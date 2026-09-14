package com.trawlingplus;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * One shoal route: a closed loop of path points with stops along it. Positions are world tile
 * coordinates, and distances are measured in tiles round the loop in the direction shoals swim,
 * which is the order routes.json lists the points in.
 */
final class ShoalRoute
{
	private static final String RESOURCE = "routes.json";

	// Longest gap between the points the overlay draws the route through, in tiles. Every gap being
	// split is already a straight piece of the finished curve, so this only ever adds points along
	// straights: bends are made of pieces shorter than this and keep exactly the points they have.
	// The line looks the same at any spacing; closer points only stop it nearer the edge of the
	// loaded map, and three tiles apart is close enough.
	private static final double SAMPLE_SPACING = 3.0;

	// Spacing of the points a route's curve is laid down as, in tiles. The line is drawn as straight
	// pieces between them, and at a quarter of a tile the corners between pieces don't show, even on
	// tight bends seen close up.
	private static final double SMOOTH_SPACING = 0.25;

	// A curve point this close to the line through the points either side of it is dropped, in tiles.
	// It makes no visible difference, and takes most of the points out of long straight stretches.
	private static final double CURVE_TOLERANCE = 0.01;

	// Centripetal Catmull-Rom: curves through every point without overshooting or looping at sharp turns.
	private static final double SMOOTH_ALPHA = 0.5;

	// Before light smoothing, long straight stretches are split into points this far apart, in tiles,
	// so the curve only rounds off corners instead of swinging wide between them. On Rainbow Reef it
	// stays within a quarter of a tile of where the recorded shoal actually swam.
	private static final double CATMULL_ROM_SPACING = 6.0;

	// Heavy smoothing respaces the points this far apart all the way round, bends included, so it
	// rounds the bends off too. At 5 tiles it sits about a fifth of a tile off the recorded laps on
	// average and never more than a tile and a quarter, comfortably inside the 3 tiles a shoal may
	// stray from its route before it stops being matched to it.
	private static final double B_SPLINE_SPACING = 5.0;

	// Heavy smoothing doesn't pass through the recorded points, which left stops on tight bends most of
	// a tile off the line. The curve is pulled onto each stop, the pull easing off to nothing this far
	// either way round the route, in tiles, so the line bends gently onto the stop rather than kinking.
	// Never further than halfway to the next stop, so no stretch is pulled towards two.
	private static final double PIN_TILES = 10;

	// A shoal this close to a stop counts as sitting at it, so its next stop is the one after.
	private static final double AT_STOP_TILES = 3.0;

	// How far either way along the route a shoal is looked for, once it is known roughly where it was,
	// in tiles. A shoal covers well under a tile a tick, so this is far more than it can have moved,
	// and far less than the way round to the other side of a crossing.
	private static final double FOLLOW_WINDOW = 15;

	// A position further than this from the stretch it was expected on has not been followed properly,
	// so the whole route is searched instead. The same tolerance as matching a shoal to a route.
	private static final double FOLLOW_OFFSET = 3;

	// How many points go in each run that can be skipped past in one go. Small enough that a run is a
	// short stretch of route, large enough that there are few runs to look through.
	private static final int BLOCK = 64;

	private final String species;
	private final int stopTicks;
	private final double[] pathX;
	private final double[] pathY;
	private final double[] pathDistance;
	private final double length;
	private final double[][] stops;
	private final double[] stopDistance;
	private final double[] sampleX;
	private final double[] sampleY;
	private final double[] sampleDistance;
	private final double[] blockMinX;
	private final double[] blockMinY;
	private final double[] blockMaxX;
	private final double[] blockMaxY;
	private final double minX;
	private final double minY;
	private final double maxX;
	private final double maxY;

	ShoalRoute(String species, String name, int stopTicks, double[][] path, double[][] stops)
	{
		if (path.length < 2 || stops.length == 0)
		{
			throw new IllegalArgumentException(name + " needs at least two path points and a stop");
		}

		this.species = species;
		this.stopTicks = stopTicks;
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

		// The box each run of points fits inside, worked out once here. A route is a thousand tiles
		// round and only a stretch of it is ever on screen, so most of these can be dismissed with four
		// comparisons rather than by asking the same of every point inside them.
		int blocks = (sampleX.length + BLOCK - 1) / BLOCK;
		blockMinX = new double[blocks];
		blockMinY = new double[blocks];
		blockMaxX = new double[blocks];
		blockMaxY = new double[blocks];
		for (int block = 0; block < blocks; block++)
		{
			double westward = Double.MAX_VALUE;
			double southward = Double.MAX_VALUE;
			double eastward = -Double.MAX_VALUE;
			double northward = -Double.MAX_VALUE;
			for (int i = blockFrom(block); i < blockTo(block); i++)
			{
				westward = Math.min(westward, sampleX[i]);
				southward = Math.min(southward, sampleY[i]);
				eastward = Math.max(eastward, sampleX[i]);
				northward = Math.max(northward, sampleY[i]);
			}
			blockMinX[block] = westward;
			blockMinY[block] = southward;
			blockMaxX[block] = eastward;
			blockMaxY[block] = northward;
		}
	}

	/**
	 * How many runs of points the route is divided into for skipping past the parts of it that are
	 * nowhere near the screen.
	 */
	int blockCount()
	{
		return blockMinX.length;
	}

	int blockFrom(int block)
	{
		return block * BLOCK;
	}

	int blockTo(int block)
	{
		return Math.min(sampleX.length, (block + 1) * BLOCK);
	}

	/**
	 * Whether any of a run of points could fall inside the given stretch of the world.
	 */
	boolean blockWithin(int block, double fromX, double fromY, double toX, double toY)
	{
		return blockMaxX[block] >= fromX && blockMinX[block] <= toX
			&& blockMaxY[block] >= fromY && blockMinY[block] <= toY;
	}

	/**
	 * Reads every route from routes.json.
	 */
	static RouteData read(Gson gson) throws IOException
	{
		try (InputStream in = ShoalRoute.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing " + RESOURCE);
			}
			return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RouteData.class);
		}
	}

	/**
	 * Builds every route, in the order routes.json lists them, with its path smoothed as chosen.
	 */
	static List<ShoalRoute> build(RouteData data, TrawlingPlusConfig.Smoothing smoothing)
	{
		List<ShoalRoute> routes = new ArrayList<>();
		for (RouteData.Species species : data.species)
		{
			for (RouteData.Route route : species.routes)
			{
				routes.add(new ShoalRoute(species.name, route.name, route.stopTicks,
					shape(route.path, route.stops, smoothing), route.stops));
			}
		}
		return routes;
	}

	private static double[][] shape(double[][] path, double[][] stops, TrawlingPlusConfig.Smoothing smoothing)
	{
		switch (smoothing)
		{
			case NONE:
				return path;
			case HEAVY:
				return bSpline(path, stops);
			default:
				return catmullRom(path);
		}
	}

	/**
	 * Turns a closed loop of points into a smooth curve through all of them, as a denser loop of points.
	 * Corners are rounded off; straight stretches stay straight.
	 */
	static double[][] catmullRom(double[][] points)
	{
		if (points.length < 3)
		{
			return points;
		}

		double[][] controls = subdivide(points, CATMULL_ROM_SPACING);
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
				curve.add(catmullRomPoint(p0, p1, p2, p3, t1, t2, t3, t1 + (t2 - t1) * step / steps));
			}
		}
		return trim(curve.toArray(new double[0][]));
	}

	/**
	 * Turns a closed loop of points into a smoother curve that doesn't have to pass through them, as a
	 * denser loop of points, then bends it onto each stop so every stop sits on the line. It irons out
	 * small wobbles and rounds bends off more; straight stretches stay straight.
	 */
	static double[][] bSpline(double[][] points, double[][] stops)
	{
		if (points.length < 3)
		{
			return points;
		}

		double total = loopLength(points);
		double[][] controls = respace(points, B_SPLINE_SPACING);
		int count = controls.length;
		double apart = total / count;
		List<double[]> curve = new ArrayList<>();
		// How far round the recorded path each curve point sits, near enough: the respaced points are
		// evenly spread along it, and the curve passes over them in order.
		List<Double> along = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			double[] p0 = controls[(i + count - 1) % count];
			double[] p1 = controls[i];
			double[] p2 = controls[(i + 1) % count];
			double[] p3 = controls[(i + 2) % count];

			int steps = Math.max(1, (int) Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / SMOOTH_SPACING));
			for (int step = 0; step < steps; step++)
			{
				// Uniform cubic B-spline weights, which always add up to 1.
				double t = (double) step / steps;
				double w0 = (1 - t) * (1 - t) * (1 - t) / 6;
				double w1 = (3 * t * t * t - 6 * t * t + 4) / 6;
				double w2 = (-3 * t * t * t + 3 * t * t + 3 * t + 1) / 6;
				double w3 = t * t * t / 6;
				curve.add(new double[]{
					w0 * p0[0] + w1 * p1[0] + w2 * p2[0] + w3 * p3[0],
					w0 * p0[1] + w1 * p1[1] + w2 * p2[1] + w3 * p3[1]
				});
				along.add((i + t) * apart);
			}
		}

		double[][] shaped = curve.toArray(new double[0][]);
		double[] reached = new double[shaped.length];
		for (int i = 0; i < reached.length; i++)
		{
			reached[i] = along.get(i);
		}
		pin(shaped, reached, total, points, stops);
		return trim(shaped);
	}

	/**
	 * Moves a curve so it runs through every stop. Each stop is matched to the stretch of curve over
	 * where it sits along the recorded path, not simply the nearest point, since a route can come back
	 * past one of its stops on the way somewhere else. That stretch is moved across by however far the
	 * stop is off it, the move easing off to nothing either way.
	 */
	private static void pin(double[][] curve, double[] along, double total, double[][] path, double[][] stops)
	{
		double[] stopAlong = new double[stops.length];
		for (int stop = 0; stop < stops.length; stop++)
		{
			stopAlong[stop] = alongLoop(path, stops[stop]);
		}

		for (int stop = 0; stop < stops.length; stop++)
		{
			double radius = PIN_TILES;
			for (int other = 0; other < stops.length; other++)
			{
				if (other != stop)
				{
					radius = Math.min(radius, loopGap(stopAlong[stop], stopAlong[other], total) / 2);
				}
			}

			int nearest = -1;
			double nearestOffset = Double.MAX_VALUE;
			for (int i = 0; i < curve.length; i++)
			{
				if (loopGap(along[i], stopAlong[stop], total) > radius)
				{
					continue;
				}
				double offset = Math.hypot(stops[stop][0] - curve[i][0], stops[stop][1] - curve[i][1]);
				if (offset < nearestOffset)
				{
					nearest = i;
					nearestOffset = offset;
				}
			}
			if (nearest < 0)
			{
				continue;
			}

			double dx = stops[stop][0] - curve[nearest][0];
			double dy = stops[stop][1] - curve[nearest][1];
			double centre = along[nearest];
			for (int i = 0; i < curve.length; i++)
			{
				double gap = loopGap(along[i], centre, total);
				if (gap < radius)
				{
					double weight = (1 + Math.cos(Math.PI * gap / radius)) / 2;
					curve[i][0] += dx * weight;
					curve[i][1] += dy * weight;
				}
			}
		}
	}

	/**
	 * How far round a closed loop of points the point on it nearest the given position is.
	 */
	private static double alongLoop(double[][] points, double[] position)
	{
		double bestOffset = Double.MAX_VALUE;
		double bestAlong = 0;
		double reached = 0;
		for (int i = 0; i < points.length; i++)
		{
			double[] from = points[i];
			double[] to = points[(i + 1) % points.length];
			double dx = to[0] - from[0];
			double dy = to[1] - from[1];
			double lengthSquared = dx * dx + dy * dy;
			double segmentLength = Math.sqrt(lengthSquared);
			double t = lengthSquared == 0 ? 0
				: Math.max(0, Math.min(1, ((position[0] - from[0]) * dx + (position[1] - from[1]) * dy) / lengthSquared));
			double offset = Math.hypot(position[0] - (from[0] + t * dx), position[1] - (from[1] + t * dy));
			if (offset < bestOffset)
			{
				bestOffset = offset;
				bestAlong = reached + t * segmentLength;
			}
			reached += segmentLength;
		}
		return bestAlong;
	}

	/**
	 * How far apart two distances round a loop are, whichever way round is shorter.
	 */
	private static double loopGap(double a, double b, double total)
	{
		double gap = Math.abs(a - b) % total;
		return Math.min(gap, total - gap);
	}

	private static double loopLength(double[][] points)
	{
		double total = 0;
		for (int i = 0; i < points.length; i++)
		{
			double[] next = points[(i + 1) % points.length];
			total += Math.hypot(next[0] - points[i][0], next[1] - points[i][1]);
		}
		return total;
	}

	/**
	 * Drops the points of a curve that aren't needed to draw it: those within CURVE_TOLERANCE of the
	 * line through the points either side of them, once the ones between have gone. Long straight
	 * stretches come out as a couple of points; bends keep theirs.
	 */
	private static double[][] trim(double[][] curve)
	{
		int count = curve.length;
		if (count < 3)
		{
			return curve;
		}

		// The loop is trimmed as two halves, so its first point and the one opposite always stay.
		boolean[] keep = new boolean[count];
		keep[0] = true;
		keep[count / 2] = true;
		Deque<int[]> pending = new ArrayDeque<>();
		pending.push(new int[]{0, count / 2});
		pending.push(new int[]{count / 2, count});
		while (!pending.isEmpty())
		{
			int[] span = pending.pop();
			double[] from = curve[span[0]];
			double[] to = curve[span[1] % count];
			double worst = 0;
			int furthest = -1;
			for (int i = span[0] + 1; i < span[1]; i++)
			{
				double offset = distanceToSegment(curve[i], from, to);
				if (offset > worst)
				{
					worst = offset;
					furthest = i;
				}
			}

			if (furthest >= 0 && worst > CURVE_TOLERANCE)
			{
				keep[furthest] = true;
				pending.push(new int[]{span[0], furthest});
				pending.push(new int[]{furthest, span[1]});
			}
		}

		List<double[]> trimmed = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			if (keep[i])
			{
				trimmed.add(curve[i]);
			}
		}
		return trimmed.toArray(new double[0][]);
	}

	private static double distanceToSegment(double[] point, double[] from, double[] to)
	{
		double dx = to[0] - from[0];
		double dy = to[1] - from[1];
		double lengthSquared = dx * dx + dy * dy;
		double t = lengthSquared == 0 ? 0
			: Math.max(0, Math.min(1, ((point[0] - from[0]) * dx + (point[1] - from[1]) * dy) / lengthSquared));
		return Math.hypot(point[0] - (from[0] + t * dx), point[1] - (from[1] + t * dy));
	}

	/**
	 * Points spaced evenly round a closed loop, starting at its first point, as close to the given
	 * distance apart as divides the loop evenly.
	 */
	private static double[][] respace(double[][] points, double spacing)
	{
		int count = points.length;
		double[] reached = new double[count + 1];
		for (int i = 0; i < count; i++)
		{
			double[] next = points[(i + 1) % count];
			reached[i + 1] = reached[i] + Math.hypot(next[0] - points[i][0], next[1] - points[i][1]);
		}

		int pieces = Math.max(3, (int) (reached[count] / spacing));
		double step = reached[count] / pieces;
		double[][] respaced = new double[pieces][];
		int segment = 0;
		for (int piece = 0; piece < pieces; piece++)
		{
			double at = piece * step;
			while (segment < count - 1 && reached[segment + 1] < at)
			{
				segment++;
			}
			double length = reached[segment + 1] - reached[segment];
			double fraction = length == 0 ? 0 : (at - reached[segment]) / length;
			respaced[piece] = lerp(points[segment], points[(segment + 1) % count], fraction);
		}
		return respaced;
	}

	private static double[][] subdivide(double[][] points, double spacing)
	{
		List<double[]> controls = new ArrayList<>();
		for (int i = 0; i < points.length; i++)
		{
			double[] a = points[i];
			double[] b = points[(i + 1) % points.length];
			int pieces = Math.max(1, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / spacing));
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

	private static double[] catmullRomPoint(double[] p0, double[] p1, double[] p2, double[] p3, double t1, double t2, double t3, double t)
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

	/**
	 * How long a shoal sits at each stop on this route, in ticks, or 0 where it has not been timed.
	 */
	int stopTicks()
	{
		return stopTicks;
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
	 * How far a position is from the box the whole route fits inside, or 0 while it is inside it. The
	 * route is somewhere in that box, so this is never further than the route itself: a route whose box
	 * is already further off than the nearest found so far cannot win, and need not be looked through.
	 */
	double boxDistance(double x, double y)
	{
		double awayX = Math.max(0, Math.max(minX - x, x - maxX));
		double awayY = Math.max(0, Math.max(minY - y, y - maxY));
		return Math.hypot(awayX, awayY);
	}

	/**
	 * The corners of the box the whole route fits inside, as world tiles: south west, south east,
	 * north east, north west. Enough to find out where a route sits on a map without walking it.
	 */
	double[][] corners()
	{
		return new double[][]{{minX, minY}, {maxX, minY}, {maxX, maxY}, {minX, maxY}};
	}

	/**
	 * Finds the point on the route nearest to a position.
	 */
	Projection project(double x, double y)
	{
		return project(x, y, -1);
	}

	/**
	 * Where a position sits on the route. Given where it was last found, the search is kept to that
	 * stretch of the route: a route that crosses itself has two places equally close to the crossing,
	 * and picking whichever is a hair nearer makes whatever is following it jump between the two. A
	 * negative distance, or a position that has strayed from that stretch, searches the whole route.
	 */
	Projection project(double x, double y, double near)
	{
		if (near >= 0)
		{
			Projection following = project(x, y, near, FOLLOW_WINDOW);
			if (following.offset <= FOLLOW_OFFSET)
			{
				return following;
			}
		}
		return project(x, y, -1, 0);
	}

	private Projection project(double x, double y, double near, double window)
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
			double segmentLength = Math.sqrt(lengthSquared);

			if (near >= 0)
			{
				// Any piece reaching into the window, not only one starting inside it: a long straight starts
				// well behind a shoal partway along it, and leaving it out snapped the shoal ahead to the
				// corner at its far end. Measured from the back of the window, round the loop.
				double reach = forward(near - window, pathDistance[i]);
				if (reach > 2 * window && reach + segmentLength < length)
				{
					continue;
				}
			}

			double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((x - pathX[i]) * dx + (y - pathY[i]) * dy) / lengthSquared));
			double offset = Math.hypot(x - (pathX[i] + t * dx), y - (pathY[i] + t * dy));
			if (offset < bestOffset)
			{
				bestOffset = offset;
				bestDistance = pathDistance[i] + t * segmentLength;
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
