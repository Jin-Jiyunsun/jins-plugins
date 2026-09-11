package com.trawlingplus;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One shoal route: a closed loop of path points with stops along it. Positions are world tile
 * coordinates, and distances are measured in tiles round the loop in the direction shoals swim,
 * which is the order routes.json lists the points in.
 */
final class ShoalRoute
{
	private static final String RESOURCE = "routes.json";

	// Longest gap between the points the overlay draws the route through, in tiles.
	private static final double SAMPLE_SPACING = 1.0;

	// Spacing of the points a route's curve is built from, in tiles.
	private static final double SMOOTH_SPACING = 0.5;

	// Centripetal Catmull-Rom: curves through every point without overshooting or looping at sharp turns.
	private static final double SMOOTH_ALPHA = 0.5;

	// Before smoothing, long straight stretches are split into points this far apart, in tiles, so the
	// curve only rounds off corners instead of swinging wide between them. At 6 tiles, Rainbow Reef's
	// curve stays within a quarter of a tile of where the recorded shoal actually swam.
	private static final double CONTROL_SPACING = 6.0;

	// A shoal this close to a stop counts as sitting at it, so its next stop is the one after.
	private static final double AT_STOP_TILES = 3.0;

	private final String species;
	private final double[] pathX;
	private final double[] pathY;
	private final double[] pathDistance;
	private final double length;
	private final double[][] stops;
	private final double[] stopDistance;
	private final double[] sampleX;
	private final double[] sampleY;
	private final double[] sampleDistance;
	private final double minX;
	private final double minY;
	private final double maxX;
	private final double maxY;

	ShoalRoute(String species, String name, double[][] path, double[][] stops)
	{
		if (path.length < 2 || stops.length == 0)
		{
			throw new IllegalArgumentException(name + " needs at least two path points and a stop");
		}

		this.species = species;
		this.stops = stops;

		int points = path.length;
		pathX = new double[points];
		pathY = new double[points];
		pathDistance = new double[points];
		double distance = 0;
		for (int i = 0; i < points; i++)
		{
			int j = (i + 1) % points;
			pathX[i] = path[i][0];
			pathY[i] = path[i][1];
			pathDistance[i] = distance;
			distance += Math.hypot(path[j][0] - path[i][0], path[j][1] - path[i][1]);
		}
		length = distance;

		double lowX = Double.MAX_VALUE;
		double lowY = Double.MAX_VALUE;
		double highX = -Double.MAX_VALUE;
		double highY = -Double.MAX_VALUE;
		for (double[][] group : new double[][][]{path, stops})
		{
			for (double[] point : group)
			{
				lowX = Math.min(lowX, point[0]);
				lowY = Math.min(lowY, point[1]);
				highX = Math.max(highX, point[0]);
				highY = Math.max(highY, point[1]);
			}
		}
		minX = lowX;
		minY = lowY;
		maxX = highX;
		maxY = highY;

		stopDistance = new double[stops.length];
		for (int i = 0; i < stops.length; i++)
		{
			stopDistance[i] = project(stops[i][0], stops[i][1]).distance;
		}

		List<double[]> samples = new ArrayList<>();
		for (int i = 0; i < points; i++)
		{
			int j = (i + 1) % points;
			double segment = pathDistance(j) - pathDistance[i];
			int steps = Math.max(1, (int) Math.ceil(segment / SAMPLE_SPACING));
			for (int step = 0; step < steps; step++)
			{
				double t = (double) step / steps;
				samples.add(new double[]{
					pathX[i] + t * (pathX[j] - pathX[i]),
					pathY[i] + t * (pathY[j] - pathY[i]),
					pathDistance[i] + t * segment
				});
			}
		}

		sampleX = new double[samples.size()];
		sampleY = new double[samples.size()];
		sampleDistance = new double[samples.size()];
		for (int i = 0; i < samples.size(); i++)
		{
			sampleX[i] = samples.get(i)[0];
			sampleY[i] = samples.get(i)[1];
			sampleDistance[i] = samples.get(i)[2];
		}
	}

	/**
	 * Loads every route from routes.json, each path smoothed into a curve through its points.
	 */
	static List<ShoalRoute> load(Gson gson) throws IOException
	{
		try (InputStream in = ShoalRoute.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing " + RESOURCE);
			}

			RouteData data = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RouteData.class);
			List<ShoalRoute> routes = new ArrayList<>();
			for (RouteData.Species species : data.species)
			{
				for (RouteData.Route route : species.routes)
				{
					routes.add(new ShoalRoute(species.name, route.name, smooth(route.path), route.stops));
				}
			}
			return routes;
		}
	}

	/**
	 * Turns a closed loop of points into a smooth curve through all of them, as a denser loop of points.
	 * Corners are rounded off; straight stretches stay straight.
	 */
	static double[][] smooth(double[][] points)
	{
		if (points.length < 3)
		{
			return points;
		}

		double[][] controls = subdivide(points);
		int count = controls.length;
		List<double[]> curve = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			double[] p0 = controls[(i + count - 1) % count];
			double[] p1 = controls[i];
			double[] p2 = controls[(i + 1) % count];
			double[] p3 = controls[(i + 2) % count];

			double t1 = knot(p0, p1);
			double t2 = t1 + knot(p1, p2);
			double t3 = t2 + knot(p2, p3);
			int steps = Math.max(1, (int) Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / SMOOTH_SPACING));
			for (int step = 0; step < steps; step++)
			{
				curve.add(catmullRom(p0, p1, p2, p3, t1, t2, t3, t1 + (t2 - t1) * step / steps));
			}
		}
		return curve.toArray(new double[0][]);
	}

	private static double[][] subdivide(double[][] points)
	{
		List<double[]> controls = new ArrayList<>();
		for (int i = 0; i < points.length; i++)
		{
			double[] a = points[i];
			double[] b = points[(i + 1) % points.length];
			int pieces = Math.max(1, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / CONTROL_SPACING));
			for (int piece = 0; piece < pieces; piece++)
			{
				controls.add(lerp(a, b, (double) piece / pieces));
			}
		}
		return controls.toArray(new double[0][]);
	}

	private static double knot(double[] a, double[] b)
	{
		// Coincident points would divide by zero, so keep every knot interval above zero.
		return Math.max(1e-6, Math.pow(Math.hypot(b[0] - a[0], b[1] - a[1]), SMOOTH_ALPHA));
	}

	private static double[] catmullRom(double[] p0, double[] p1, double[] p2, double[] p3, double t1, double t2, double t3, double t)
	{
		// Barry and Goldman's pyramidal form, with the first knot at zero.
		double[] a1 = lerp(p0, p1, t / t1);
		double[] a2 = lerp(p1, p2, (t - t1) / (t2 - t1));
		double[] a3 = lerp(p2, p3, (t - t2) / (t3 - t2));
		double[] b1 = lerp(a1, a2, t / t2);
		double[] b2 = lerp(a2, a3, (t - t1) / (t3 - t1));
		return lerp(b1, b2, (t - t1) / (t2 - t1));
	}

	private static double[] lerp(double[] a, double[] b, double fraction)
	{
		return new double[]{a[0] + (b[0] - a[0]) * fraction, a[1] + (b[1] - a[1]) * fraction};
	}

	String getSpecies()
	{
		return species;
	}

	double length()
	{
		return length;
	}

	/**
	 * Whether the route's bounding box overlaps the given rectangle of world tiles.
	 */
	boolean overlaps(double fromX, double fromY, double toX, double toY)
	{
		return maxX >= fromX && minX <= toX && maxY >= fromY && minY <= toY;
	}

	/**
	 * Finds the point on the route nearest to a position.
	 */
	Projection project(double x, double y)
	{
		int points = pathX.length;
		double bestOffset = Double.MAX_VALUE;
		double bestDistance = 0;
		for (int i = 0; i < points; i++)
		{
			int j = (i + 1) % points;
			double dx = pathX[j] - pathX[i];
			double dy = pathY[j] - pathY[i];
			double lengthSquared = dx * dx + dy * dy;
			double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((x - pathX[i]) * dx + (y - pathY[i]) * dy) / lengthSquared));
			double offset = Math.hypot(x - (pathX[i] + t * dx), y - (pathY[i] + t * dy));
			if (offset < bestOffset)
			{
				bestOffset = offset;
				bestDistance = pathDistance[i] + t * Math.sqrt(lengthSquared);
			}
		}
		return new Projection(bestDistance % length, bestOffset);
	}

	/**
	 * The index of the stop a shoal at the given distance round the route is heading for.
	 */
	int nextStop(double distance)
	{
		int best = 0;
		double bestGap = Double.MAX_VALUE;
		for (int i = 0; i < stopDistance.length; i++)
		{
			double gap = forward(distance, stopDistance[i]);
			if (gap <= AT_STOP_TILES)
			{
				// Sitting at this stop, or about to reach it: it comes round again last.
				gap += length;
			}
			if (gap < bestGap)
			{
				best = i;
				bestGap = gap;
			}
		}
		return best;
	}

	int stopCount()
	{
		return stops.length;
	}

	double stopX(int stop)
	{
		return stops[stop][0];
	}

	double stopY(int stop)
	{
		return stops[stop][1];
	}

	double stopDistance(int stop)
	{
		return stopDistance[stop];
	}

	int sampleCount()
	{
		return sampleX.length;
	}

	double sampleX(int sample)
	{
		return sampleX[sample];
	}

	double sampleY(int sample)
	{
		return sampleY[sample];
	}

	double sampleDistance(int sample)
	{
		return sampleDistance[sample];
	}

	/**
	 * The point at a distance round the route and the direction the route runs there, as
	 * {x, y, dx, dy} with the direction a unit vector.
	 */
	double[] pointAt(double distance)
	{
		int count = sampleX.length;
		double at = forward(0, distance);
		int next = sampleAt(at);
		int previous = (next + count - 1) % count;
		double start = sampleDistance[previous];
		double end = sampleDistance[next];
		if (end <= start)
		{
			// The stretch between the last sample and the first, closing the loop.
			end += length;
		}
		if (at < start)
		{
			at += length;
		}

		double fraction = end == start ? 0 : (at - start) / (end - start);
		double dx = sampleX[next] - sampleX[previous];
		double dy = sampleY[next] - sampleY[previous];
		double step = Math.hypot(dx, dy);
		return new double[]{
			sampleX[previous] + dx * fraction,
			sampleY[previous] + dy * fraction,
			step == 0 ? 0 : dx / step,
			step == 0 ? 0 : dy / step
		};
	}

	/**
	 * The index of the first drawing sample at or after the given distance round the route.
	 */
	int sampleAt(double distance)
	{
		int index = Arrays.binarySearch(sampleDistance, forward(0, distance));
		if (index < 0)
		{
			index = -index - 1;
		}
		return index == sampleDistance.length ? 0 : index;
	}

	private double pathDistance(int point)
	{
		return point == 0 ? length : pathDistance[point];
	}

	/**
	 * How far it is going forwards round the route from one distance to another.
	 */
	double forward(double from, double to)
	{
		double gap = (to - from) % length;
		return gap < 0 ? gap + length : gap;
	}

	static final class Projection
	{
		// Distance round the route to the nearest point on it.
		final double distance;
		// How far the position is from that point.
		final double offset;

		private Projection(double distance, double offset)
		{
			this.distance = distance;
			this.offset = offset;
		}
	}
}
