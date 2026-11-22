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
 * One shoal route: a closed loop of path points with stops along it, in world tile coordinates, with distances in tiles
 * round the loop in the direction shoals swim.
 */
final class ShoalRoute
{
	private static final String RESOURCE = "routes.json";

	// Longest gap between the points the route is drawn through, in tiles: straights are split this often so the line
	// stops near the edge of the loaded map; bends keep their points.
	private static final double SAMPLE_SPACING = 3.0;

	// Spacing of the points a route's curve is laid down as, in tiles, close enough that the corners between pieces
	// don't show even on tight bends close up.
	private static final double SMOOTH_SPACING = 0.25;

	// A curve point this close to the line through the points either side of it is dropped, in tiles.
	// It makes no visible difference, and takes most of the points out of long straight stretches.
	private static final double CURVE_TOLERANCE = 0.01;

	// Before light smoothing, long straights are split into points this far apart, in tiles, so the curve only rounds
	// off corners rather than swinging wide between them.
	private static final double CATMULL_ROM_SPACING = 6.0;

	// Heavy smoothing respaces the points this far apart all round, bends included, rounding them off too; at 5 tiles
	// it stays well inside the 3 tiles a shoal may stray before it stops matching its route.
	private static final double B_SPLINE_SPACING = 5.0;

	// How far either way round the route, in tiles, the heavy curve's pull onto each stop eases off to nothing, so it
	// bends onto the stop rather than kinking; never past halfway to the next stop.
	private static final double PIN_TILES = 10;

	// A shoal this close to a stop counts as sitting at it, so its next stop is the one after.
	static final double AT_STOP_TILES = 3.0;

	// How far either way along the route a shoal is looked for once it's known roughly where it was, in tiles: far more
	// than it can move in a tick, far less than the way round to a crossing's other side.
	private static final double FOLLOW_WINDOW = 15;

	// A position further than this from the stretch it was expected on has not been followed properly,
	// so the whole route is searched instead. The same tolerance as matching a shoal to a route.
	private static final double FOLLOW_OFFSET = 3;

	// How many points go in each run that can be skipped past in one go. Small enough that a run is a
	// short stretch of route, large enough that there are few runs to look through.
	private static final int BLOCK = 64;

	private final String species;
	private final String name;
	private final int stopTicks;
	// The lap's timing, or null legs where it isn't known: ticks a shoal sits still at a stop, the share of each leg
	// swum each tick, the tick each leg starts on (counting from stop 0's), and the lap's length in ticks.
	private final int stillTicks;
	private final float[][] legs;
	private final int[] legStart;
	private final int lapTicks;
	private final double length;
	private final double[][] stops;
	private final double[] stopDistance;
	// The drawn points, also what a shoal is followed along. Floats halve their memory and still place a point to
	// within a thousandth of a tile.
	private final float[] sampleX;
	private final float[] sampleY;
	private final float[] sampleDistance;
	// For each drawn point, the largest tolerance in tiles at which simplifying the route still keeps it, so a map can
	// leave out the points that would barely bend the line at its zoom.
	private final float[] sampleDetail;
	private final double[] blockMinX;
	private final double[] blockMinY;
	private final double[] blockMaxX;
	private final double[] blockMaxY;
	private final double minX;
	private final double minY;
	private final double maxX;
	private final double maxY;
	// Which of the drawn points pass close to where sea creatures that attack boats spawn, set by markDanger.
	private boolean[] danger = new boolean[0];
	// Whether any of the route is marked dangerous, so a route with none is passed over whole.
	private boolean anyDanger;
	// Circles of tiles, as {x, y, tiles}, marked safe by hand regardless of danger; never null.
	private final double[][] safe;
	// Where sea creatures that attack boats spawn close enough to threaten this route, place by place; never null.
	private final List<RouteData.Threat> threats;
	// Which of these threaten this route once safe circles and the danger distance are taken into account, with
	// where to place a skull and crossbones for each, set by markDanger.
	private List<DangerousThreat> dangerousThreats = new ArrayList<>();

	ShoalRoute(String species, String name, int stopTicks, double[][] path, double[][] stops, double[][] safe,
		List<RouteData.Threat> threats, int stillTicks, float[][] legs)
	{
		if (path.length < 2 || stops.length == 0)
		{
			throw new IllegalArgumentException(name + " needs at least two path points and a stop");
		}

		this.species = species;
		this.name = name;
		this.stopTicks = stopTicks;
		boolean timed = legs != null && legs.length == stops.length && stillTicks > 0;
		this.stillTicks = stillTicks;
		this.legs = timed ? legs : null;
		legStart = new int[stops.length];
		int tick = 0;
		for (int stop = 0; timed && stop < stops.length; stop++)
		{
			legStart[stop] = tick;
			tick += legs[stop].length + 1 + stillTicks;
		}
		lapTicks = tick;
		this.stops = stops;
		this.safe = safe == null ? new double[0][] : safe;
		this.threats = threats == null ? new ArrayList<>() : threats;

		int points = path.length;
		double[] pathDistance = new double[points];
		double distance = 0;
		for (int i = 0; i < points; i++)
		{
			int j = (i + 1) % points;
			pathDistance[i] = distance;
			distance += span(path[j][0] - path[i][0], path[j][1] - path[i][1]);
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

		List<double[]> samples = new ArrayList<>();
		for (int i = 0; i < points; i++)
		{
			int j = (i + 1) % points;
			double segment = (j == 0 ? length : pathDistance[j]) - pathDistance[i];
			int steps = Math.max(1, (int) Math.ceil(segment / SAMPLE_SPACING));
			for (int step = 0; step < steps; step++)
			{
				double t = (double) step / steps;
				samples.add(new double[]{
					path[i][0] + t * (path[j][0] - path[i][0]),
					path[i][1] + t * (path[j][1] - path[i][1]),
					pathDistance[i] + t * segment
				});
			}
		}

		sampleX = new float[samples.size()];
		sampleY = new float[samples.size()];
		sampleDistance = new float[samples.size()];
		for (int i = 0; i < samples.size(); i++)
		{
			sampleX[i] = (float) samples.get(i)[0];
			sampleY[i] = (float) samples.get(i)[1];
			sampleDistance[i] = (float) samples.get(i)[2];
		}
		sampleDetail = detail(sampleX, sampleY);

		// The box each run of points fits inside, worked out once, so runs nowhere near the screen are dismissed with
		// four comparisons.
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

		stopDistance = new double[stops.length];
		for (int i = 0; i < stops.length; i++)
		{
			stopDistance[i] = project(stops[i][0], stops[i][1]).distance;
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
					shape(route.path, route.stops, smoothing), route.stops, route.safe, route.threats, route.stillTicks,
					route.legs));
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
			int steps = Math.max(1, (int) Math.ceil(span(p2[0] - p1[0], p2[1] - p1[1]) / SMOOTH_SPACING));
			for (int step = 0; step < steps; step++)
			{
				curve.add(catmullRomPoint(p0, p1, p2, p3, t1, t2, t3, t1 + (t2 - t1) * step / steps));
			}
		}
		return trim(curve.toArray(new double[0][]));
	}

	/**
	 * Turns a closed loop of points into a smoother curve that needn't pass through them, as a denser loop, then bends
	 * it onto each stop. It irons out wobbles and rounds bends; straights stay straight.
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

			int steps = Math.max(1, (int) Math.ceil(span(p2[0] - p1[0], p2[1] - p1[1]) / SMOOTH_SPACING));
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
	 * Moves a curve so it runs through every stop, each matched to the stretch of curve where it sits along the
	 * recorded path (a route can pass back by a stop), the move easing off either way.
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
				double offset = span(stops[stop][0] - curve[i][0], stops[stop][1] - curve[i][1]);
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
			// Squared: only ever compared.
			double awayX = position[0] - (from[0] + t * dx);
			double awayY = position[1] - (from[1] + t * dy);
			double offset = awayX * awayX + awayY * awayY;
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
			total += span(next[0] - points[i][0], next[1] - points[i][1]);
		}
		return total;
	}

	/**
	 * Drops the points of a curve within CURVE_TOLERANCE of the line through their neighbours, so straights come out as
	 * a couple of points and bends keep theirs.
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
		return span(point[0] - (from[0] + t * dx), point[1] - (from[1] + t * dy));
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
			reached[i + 1] = reached[i] + span(next[0] - points[i][0], next[1] - points[i][1]);
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
			int pieces = Math.max(1, (int) Math.ceil(span(b[0] - a[0], b[1] - a[1]) / spacing));
			for (int piece = 0; piece < pieces; piece++)
			{
				controls.add(lerp(a, b, (double) piece / pieces));
			}
		}
		return controls.toArray(new double[0][]);
	}

	private static double knot(double[] a, double[] b)
	{
		// Centripetal Catmull-Rom, which curves through every point without overshooting at sharp turns; the distance's
		// square root is taken as two square roots of its square, and knot gaps are kept above zero.
		double dx = b[0] - a[0];
		double dy = b[1] - a[1];
		return Math.max(1e-6, Math.sqrt(Math.sqrt(dx * dx + dy * dy)));
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

	String getName()
	{
		return name;
	}

	/**
	 * Whether the lap's timing is known, so where a shoal will be can be worked out.
	 */
	boolean timed()
	{
		return legs != null;
	}

	/**
	 * How many ticks a lap takes, or 0 where it hasn't been timed.
	 */
	int lapTicks()
	{
		return lapTicks;
	}

	/**
	 * The tick of the lap a shoal leaves a stop on: the last tick it sits still there.
	 */
	int leaveTick(int stop)
	{
		return legStart[stop];
	}

	/**
	 * The tick of the lap a shoal first sits still at a stop.
	 */
	int arriveTick(int stop)
	{
		int from = (stop + stops.length - 1) % stops.length;
		return (legStart[from] + legs[from].length + 1) % lapTicks;
	}

	/**
	 * How many ticks a shoal sits still at each stop after the one it arrives on.
	 */
	int stillTicks()
	{
		return stillTicks;
	}

	/**
	 * How far round the route a shoal is on a tick of its lap, any whole number of laps either way.
	 */
	double distanceAtTick(long tick)
	{
		int at = (int) Math.floorMod(tick, (long) lapTicks);
		int stop = legs.length - 1;
		while (legStart[stop] > at)
		{
			stop--;
		}
		int into = at - legStart[stop];
		float[] leg = legs[stop];
		double share = into == 0 ? 0 : into > leg.length ? 1 : leg[into - 1];
		int next = stop + 1 == stops.length ? 0 : stop + 1;
		return stopDistance[stop] + forward(stopDistance[stop], stopDistance[next]) * share;
	}

	/**
	 * How much of its stop a shoal has left on a tick of its lap, from 1 as it arrives down to a tick's worth, or -1
	 * from the last tick of the stop, as it sets off, and while it is swimming.
	 */
	double stopLeft(long tick)
	{
		int at = (int) Math.floorMod(tick, (long) lapTicks);
		int stop = legs.length - 1;
		while (legStart[stop] > at)
		{
			stop--;
		}
		int arrive = legStart[stop] + legs[stop].length + 1;
		if (at < arrive)
		{
			return -1;
		}
		int leave = stop + 1 == legs.length ? lapTicks : legStart[stop + 1];
		return (double) (leave - at) / stillTicks;
	}

	/**
	 * The tick of the lap a swimming shoal is most likely on, from how far round the route it is.
	 */
	int tickAt(double distance)
	{
		int stop = 0;
		double behind = Double.MAX_VALUE;
		for (int i = 0; i < stops.length; i++)
		{
			double gap = forward(stopDistance[i], distance);
			if (gap < behind)
			{
				behind = gap;
				stop = i;
			}
		}
		int next = stop + 1 == stops.length ? 0 : stop + 1;
		double whole = forward(stopDistance[stop], stopDistance[next]);
		double share = whole <= 0 ? 0 : behind / whole;
		float[] leg = legs[stop];
		int best = 1;
		// A leg of a single tick has no share to compare, and its one tick is the answer.
		for (int into = 2; into <= leg.length; into++)
		{
			if (Math.abs(leg[into - 1] - share) < Math.abs(leg[best - 1] - share))
			{
				best = into;
			}
		}
		return legStart[stop] + best;
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
	 * How far a position is from the route's box, or 0 inside it: never further than the route itself, so a route whose
	 * box is further than the best so far can be skipped.
	 */
	double boxDistance(double x, double y)
	{
		double awayX = Math.max(0, Math.max(minX - x, x - maxX));
		double awayY = Math.max(0, Math.max(minY - y, y - maxY));
		return span(awayX, awayY);
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
	 * Where a position sits on the route. Given where it was last found, only that stretch is searched, so following
	 * doesn't jump across a crossing; a negative distance, or a position off that stretch, searches it all.
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
		int points = sampleX.length;
		double bestOffset = Double.MAX_VALUE;
		double bestDistance = 0;
		for (int i = 0; i < points; i++)
		{
			int j = i + 1 == points ? 0 : i + 1;
			double dx = sampleX[j] - sampleX[i];
			double dy = sampleY[j] - sampleY[i];
			double lengthSquared = dx * dx + dy * dy;
			// Already measured when the route was built, rather than a square root for every piece on every call.
			double segmentLength = (j == 0 ? length : sampleDistance[j]) - sampleDistance[i];

			if (near >= 0)
			{
				// Any piece reaching into the window, not only one starting inside it, so a shoal part way along a long
				// straight isn't snapped ahead to its end; measured from the back of the window, round the loop.
				double reach = forward(near - window, sampleDistance[i]);
				if (reach > 2 * window && reach + segmentLength < length)
				{
					continue;
				}
			}

			double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((x - sampleX[i]) * dx + (y - sampleY[i]) * dy) / lengthSquared));
			// Compared squared, with one square root for the nearest at the end.
			double awayX = x - (sampleX[i] + t * dx);
			double awayY = y - (sampleY[i] + t * dy);
			double offset = awayX * awayX + awayY * awayY;
			if (offset < bestOffset)
			{
				bestOffset = offset;
				bestDistance = sampleDistance[i] + t * segmentLength;
			}
		}
		return new Projection(bestDistance % length, Math.sqrt(bestOffset));
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
	 * Whether this drawn point is kept when the route is simplified to within a tolerance in tiles. The first and last
	 * points always are.
	 */
	boolean sampleKept(int sample, double tolerance)
	{
		return sampleDetail[sample] >= tolerance;
	}

	/**
	 * Douglas-Peucker over the points as one open line: each split point gets its distance from the piece it splits,
	 * capped by its parent's, so the points at or above any tolerance are those Douglas-Peucker keeps.
	 */
	private static float[] detail(float[] xs, float[] ys)
	{
		int count = xs.length;
		float[] detail = new float[count];
		if (count == 0)
		{
			return detail;
		}
		detail[0] = Float.POSITIVE_INFINITY;
		detail[count - 1] = Float.POSITIVE_INFINITY;
		// Stretches still to split: their first and last points, and the cap from the point that made them.
		int[] firsts = new int[count];
		int[] lasts = new int[count];
		double[] caps = new double[count];
		int stretches = 1;
		firsts[0] = 0;
		lasts[0] = count - 1;
		caps[0] = Double.POSITIVE_INFINITY;
		while (stretches > 0)
		{
			stretches--;
			int first = firsts[stretches];
			int last = lasts[stretches];
			double cap = caps[stretches];
			if (last - first < 2)
			{
				continue;
			}

			double alongX = xs[last] - xs[first];
			double alongY = ys[last] - ys[first];
			double lengthSquared = alongX * alongX + alongY * alongY;
			int farthest = first + 1;
			double farthestSquared = -1;
			for (int i = first + 1; i < last; i++)
			{
				double offX = xs[i] - xs[first];
				double offY = ys[i] - ys[first];
				double share = lengthSquared == 0 ? 0
					: Math.max(0, Math.min(1, (offX * alongX + offY * alongY) / lengthSquared));
				double awayX = offX - share * alongX;
				double awayY = offY - share * alongY;
				double awaySquared = awayX * awayX + awayY * awayY;
				if (awaySquared > farthestSquared)
				{
					farthestSquared = awaySquared;
					farthest = i;
				}
			}

			double split = Math.min(Math.sqrt(farthestSquared), cap);
			// Rounded up as it is stored, so a point is never left out at a tolerance it reaches.
			float stored = (float) split;
			detail[farthest] = stored < split ? Math.nextUp(stored) : stored;
			firsts[stretches] = first;
			lasts[stretches] = farthest;
			caps[stretches++] = split;
			firsts[stretches] = farthest;
			lasts[stretches] = last;
			caps[stretches++] = split;
		}
		return detail;
	}

	/**
	 * Marks each drawn point within a distance of this route's threats, joins marked stretches up to gapTiles apart and
	 * clears points in hand-marked safe circles; also finds which threats come close, and each one's skull spot.
	 */
	void markDanger(double tiles, double gapTiles)
	{
		boolean[] marked = new boolean[sampleX.length];
		List<DangerousThreat> found = new ArrayList<>();
		for (RouteData.Threat threat : threats)
		{
			double x = 0;
			double y = 0;
			int near = 0;
			for (int[] spawn : threat.points)
			{
				boolean spawnNear = false;
				for (int i = 0; i < sampleX.length; i++)
				{
					double awayX = sampleX[i] - spawn[0];
					double awayY = sampleY[i] - spawn[1];
					if (awayX * awayX + awayY * awayY <= tiles * tiles)
					{
						marked[i] = true;
						spawnNear = true;
					}
				}
				if (spawnNear)
				{
					x += spawn[0];
					y += spawn[1];
					near++;
				}
			}
			if (near > 0)
			{
				found.add(new DangerousThreat(x / near, y / near, threat.creature + " (level-" + threat.combat + ")"));
			}
		}
		closeGaps(marked, gapTiles);
		clearSafe(marked);
		danger = marked;
		boolean any = false;
		for (boolean sample : marked)
		{
			any |= sample;
		}
		anyDanger = any;
		dangerousThreats = found;
	}

	/**
	 * Whether any stretch of the route is marked dangerous.
	 */
	boolean hasDanger()
	{
		return anyDanger;
	}

	private void clearSafe(boolean[] marked)
	{
		for (double[] circle : safe)
		{
			for (int i = 0; i < sampleX.length; i++)
			{
				double awayX = sampleX[i] - circle[0];
				double awayY = sampleY[i] - circle[1];
				if (marked[i] && awayX * awayX + awayY * awayY <= circle[2] * circle[2])
				{
					marked[i] = false;
				}
			}
		}
	}

	/**
	 * Marks every unmarked run of points round the loop that sits between two marked ones no more than
	 * gapTiles apart along the route.
	 */
	private void closeGaps(boolean[] marked, double gapTiles)
	{
		int count = marked.length;
		int start = -1;
		for (int i = 0; i < count && start < 0; i++)
		{
			if (marked[i])
			{
				start = i;
			}
		}
		if (start < 0)
		{
			return;
		}

		// Walked once round the loop from a marked point, so every gap has a marked point at each end.
		int step = 1;
		while (step < count)
		{
			if (marked[(start + step) % count])
			{
				step++;
				continue;
			}

			int end = step;
			while (!marked[(start + end) % count])
			{
				end++;
			}

			int before = (start + step - 1) % count;
			int after = (start + end) % count;
			if (forward(sampleDistance[before], sampleDistance[after]) <= gapTiles)
			{
				for (int fill = step; fill < end; fill++)
				{
					marked[(start + fill) % count] = true;
				}
			}
			step = end;
		}
	}

	/**
	 * Whether this drawn point passes close to where sea creatures that attack boats spawn.
	 */
	boolean dangerAt(int sample)
	{
		return sample < danger.length && danger[sample];
	}

	/**
	 * Whether the piece of route at this distance round it is drawn as dangerous. A piece takes the colour of the
	 * point it starts from, so this reads that point rather than the one after.
	 */
	boolean dangerAtDistance(double distance)
	{
		int count = sampleX.length;
		if (count == 0)
		{
			return false;
		}
		int index = Arrays.binarySearch(sampleDistance, (float) forward(0, distance));
		return dangerAt(index >= 0 ? index : (-index - 2 + count) % count);
	}

	/**
	 * How many of this route's threats actually come close enough to threaten it.
	 */
	int dangerousThreatCount()
	{
		return dangerousThreats.size();
	}

	double dangerousThreatX(int threat)
	{
		return dangerousThreats.get(threat).x;
	}

	double dangerousThreatY(int threat)
	{
		return dangerousThreats.get(threat).y;
	}

	/**
	 * The creature at a dangerous threat with its level, as the game names it, such as "Tiger shark (level-125)".
	 */
	String dangerousThreatName(int threat)
	{
		return dangerousThreats.get(threat).name;
	}

	private static final class DangerousThreat
	{
		final double x;
		final double y;
		final String name;

		DangerousThreat(double x, double y, String name)
		{
			this.x = x;
			this.y = y;
			this.name = name;
		}
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
		double step = span(dx, dy);
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
		int index = Arrays.binarySearch(sampleDistance, (float) forward(0, distance));
		if (index < 0)
		{
			index = -index - 1;
		}
		return index == sampleDistance.length ? 0 : index;
	}

	/**
	 * How long a line across and down is. Math.hypot guards against overflow these sizes never reach, and costs many
	 * times as much for it.
	 */
	static double span(double x, double y)
	{
		return Math.sqrt(x * x + y * y);
	}

	/**
	 * How far it is going forwards round the route from one distance to another.
	 */
	double forward(double from, double to)
	{
		// Usually both are already round the route once at most, which needs no remainder.
		double gap = to - from;
		if (gap >= 0 && gap < length)
		{
			return gap;
		}
		gap %= length;
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
