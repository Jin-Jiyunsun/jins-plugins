package com.barbershop;

import java.util.Arrays;

/**
 * Follows how the player's own head moves: from where its vertices sit unanimated to where
 * they are this frame, fits the best matching affine transform (least squares), so another
 * head can be moved the same way.
 */
final class HeadFit
{
	private final int[] vertices;
	private final float[] restX;
	private final float[] restY;
	private final float[] restZ;
	// Inverse of the sum of p p^T over the rest points p = (x, y, z, 1); fixed once taken.
	private final double[][] inverse;

	// This frame's transform, rows x, y, z, each (a, b, c, d) for a*x + b*y + c*z + d.
	private final double[][] transform = new double[3][4];

	// This frame's positions of the player's vertices, kept for the neck weld.
	private float[] currentX;
	private float[] currentY;
	private float[] currentZ;

	// The rest of the player (body, gear), unanimated, for the neck weld to hold onto.
	private int[] bodyVertices = new int[0];
	private float[] bodyRestX = new float[0];
	private float[] bodyRestY = new float[0];
	private float[] bodyRestZ = new float[0];

	// Neck weld, per vertex of the head being moved: how much it follows its nearest player
	// body vertices rather than the whole-head transform (0 to 1), which ones (indexes into bodyVertices), and how much each.
	private float[] weldAmount;
	private int[][] weldNearest;
	private float[][] weldShare;

	private HeadFit(int[] vertices, float[] restX, float[] restY, float[] restZ, double[][] inverse)
	{
		this.vertices = vertices;
		this.restX = restX;
		this.restY = restY;
		this.restZ = restZ;
		this.inverse = inverse;
	}

	/** Takes the rest positions of the given vertices. Returns null if they don't span 3D. */
	static HeadFit of(int[] vertices, float[] x, float[] y, float[] z)
	{
		int n = vertices.length;
		float[] rx = new float[n];
		float[] ry = new float[n];
		float[] rz = new float[n];
		double[][] sum = new double[4][4];
		for (int i = 0; i < n; i++)
		{
			int v = vertices[i];
			rx[i] = x[v];
			ry[i] = y[v];
			rz[i] = z[v];
			double[] p = {rx[i], ry[i], rz[i], 1};
			for (int r = 0; r < 4; r++)
			{
				for (int c = 0; c < 4; c++)
				{
					sum[r][c] += p[r] * p[c];
				}
			}
		}
		double[][] inverse = invert(sum);
		return inverse == null ? null : new HeadFit(vertices, rx, ry, rz, inverse);
	}

	/** Takes the unanimated positions of the player's body vertices, which the neck weld holds onto. */
	void setBody(int[] vertices, float[] x, float[] y, float[] z)
	{
		bodyVertices = vertices;
		bodyRestX = new float[vertices.length];
		bodyRestY = new float[vertices.length];
		bodyRestZ = new float[vertices.length];
		for (int i = 0; i < vertices.length; i++)
		{
			bodyRestX[i] = x[vertices[i]];
			bodyRestY[i] = y[vertices[i]];
			bodyRestZ[i] = z[vertices[i]];
		}
	}

	/**
	 * Ties the bottom of the head being moved to the player's body: within {@code full} of its
	 * lowest point a vertex moves fully with its nearest body vertices, fading out from there to
	 * {@code band}. The nearest body vertices are the top of the player's neck and collar, so the
	 * new head's neck stays on them when the body bends.
	 */
	void weld(float[] x, float[] y, float[] z, int count, float full, float band, int nearest)
	{
		float bottom = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			bottom = Math.max(bottom, y[i]);
		}
		weldAmount = new float[count];
		weldNearest = new int[count][];
		weldShare = new float[count][];
		int k = Math.min(nearest, bodyVertices.length);
		for (int i = 0; i < count; i++)
		{
			float above = bottom - y[i];
			if (above >= band || k == 0)
			{
				continue;
			}
			float t = above <= full ? 1 : 1 - (above - full) / Math.max(1, band - full);
			weldAmount[i] = t * t * (3 - 2 * t);

			int[] best = new int[k];
			float[] bestDistance = new float[k];
			Arrays.fill(bestDistance, Float.MAX_VALUE);
			for (int j = 0; j < bodyVertices.length; j++)
			{
				float dx = bodyRestX[j] - x[i];
				float dy = bodyRestY[j] - y[i];
				float dz = bodyRestZ[j] - z[i];
				float d = dx * dx + dy * dy + dz * dz;
				for (int slot = 0; slot < k; slot++)
				{
					if (d < bestDistance[slot])
					{
						System.arraycopy(bestDistance, slot, bestDistance, slot + 1, k - slot - 1);
						System.arraycopy(best, slot, best, slot + 1, k - slot - 1);
						bestDistance[slot] = d;
						best[slot] = j;
						break;
					}
				}
			}
			float[] share = new float[k];
			float total = 0;
			for (int slot = 0; slot < k; slot++)
			{
				share[slot] = 1f / (1f + (float) Math.sqrt(bestDistance[slot]));
				total += share[slot];
			}
			for (int slot = 0; slot < k; slot++)
			{
				share[slot] /= total;
			}
			weldNearest[i] = best;
			weldShare[i] = share;
		}
	}

	/** Fits this frame's transform from the player's animated vertex positions. */
	void fit(float[] x, float[] y, float[] z)
	{
		currentX = x;
		currentY = y;
		currentZ = z;
		// sum of p q^T, 4 x 3
		double[][] b = new double[4][3];
		for (int i = 0; i < vertices.length; i++)
		{
			int v = vertices[i];
			double[] p = {restX[i], restY[i], restZ[i], 1};
			double qx = x[v];
			double qy = y[v];
			double qz = z[v];
			for (int r = 0; r < 4; r++)
			{
				b[r][0] += p[r] * qx;
				b[r][1] += p[r] * qy;
				b[r][2] += p[r] * qz;
			}
		}
		for (int axis = 0; axis < 3; axis++)
		{
			for (int r = 0; r < 4; r++)
			{
				double s = 0;
				for (int k = 0; k < 4; k++)
				{
					s += inverse[r][k] * b[k][axis];
				}
				transform[axis][r] = s;
			}
		}
	}

	/** Moves rest positions by this frame's transform into the output arrays. */
	void apply(float[] fromX, float[] fromY, float[] fromZ, float[] toX, float[] toY, float[] toZ, int count)
	{
		double[] tx = transform[0];
		double[] ty = transform[1];
		double[] tz = transform[2];
		for (int i = 0; i < count; i++)
		{
			double px = fromX[i];
			double py = fromY[i];
			double pz = fromZ[i];
			float ax = (float) (tx[0] * px + tx[1] * py + tx[2] * pz + tx[3]);
			float ay = (float) (ty[0] * px + ty[1] * py + ty[2] * pz + ty[3]);
			float az = (float) (tz[0] * px + tz[1] * py + tz[2] * pz + tz[3]);
			float amount = weldAmount != null && i < weldAmount.length ? weldAmount[i] : 0;
			if (amount > 0)
			{
				// Moved as far as its nearest body vertices moved from rest.
				float lx = (float) px;
				float ly = (float) py;
				float lz = (float) pz;
				int[] near = weldNearest[i];
				float[] share = weldShare[i];
				for (int slot = 0; slot < near.length; slot++)
				{
					int j = near[slot];
					int v = bodyVertices[j];
					lx += share[slot] * (currentX[v] - bodyRestX[j]);
					ly += share[slot] * (currentY[v] - bodyRestY[j]);
					lz += share[slot] * (currentZ[v] - bodyRestZ[j]);
				}
				ax += (lx - ax) * amount;
				ay += (ly - ay) * amount;
				az += (lz - az) * amount;
			}
			toX[i] = ax;
			toY[i] = ay;
			toZ[i] = az;
		}
	}

	/** The lowest point of the player's own head at rest (largest y, as y points down). */
	float restBottom()
	{
		float bottom = -Float.MAX_VALUE;
		for (float y : restY)
		{
			bottom = Math.max(bottom, y);
		}
		return bottom;
	}

	int[] getVertices()
	{
		return vertices;
	}

	private static double[][] invert(double[][] m)
	{
		int n = m.length;
		double[][] a = new double[n][2 * n];
		for (int r = 0; r < n; r++)
		{
			System.arraycopy(m[r], 0, a[r], 0, n);
			a[r][n + r] = 1;
		}
		for (int c = 0; c < n; c++)
		{
			int pivot = c;
			for (int r = c + 1; r < n; r++)
			{
				if (Math.abs(a[r][c]) > Math.abs(a[pivot][c]))
				{
					pivot = r;
				}
			}
			if (Math.abs(a[pivot][c]) < 1e-9)
			{
				return null;
			}
			double[] t = a[c];
			a[c] = a[pivot];
			a[pivot] = t;
			double d = a[c][c];
			for (int k = 0; k < 2 * n; k++)
			{
				a[c][k] /= d;
			}
			for (int r = 0; r < n; r++)
			{
				if (r != c && a[r][c] != 0)
				{
					double f = a[r][c];
					for (int k = 0; k < 2 * n; k++)
					{
						a[r][k] -= f * a[c][k];
					}
				}
			}
		}
		double[][] out = new double[n][n];
		for (int r = 0; r < n; r++)
		{
			System.arraycopy(a[r], n, out[r], 0, n);
		}
		return out;
	}
}
