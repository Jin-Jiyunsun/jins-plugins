package com.trawlingplus;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Constants;

/**
 * Where each route's shoal is in its lap, from when it was last seen, so it can be followed out of sight: shoals keep
 * exact time on every world. Kept in memory only.
 */
final class ShoalForecast
{
	// A shoal this close to a stop is taken to be at it, in tiles: it moves about a tile and a half a tick.
	private static final double STOP_TILES = 3;

	// Game ticks counted this session, logged out included, when the last was counted, and whether the client has been
	// away since (logged out, hopping or disconnected), when the next is counted by the clock.
	private long ticks;
	private long lastTickMillis = -1;
	private boolean away;
	private final Map<String, Sighting> sightings = new HashMap<>();

	private static final class Sighting
	{
		// The tick of the session the lap's first tick fell on.
		long lapStart;
		// Whether that comes from seeing the shoal set off from or come to a stop, rather than from where it was.
		boolean exact;
		long seenTick;
		// Until then, where it was last seen, turned into a tick of the lap only when a prediction is asked for:
		// whether it was sitting still, and how far down its stop's bar was.
		double[] estimateAt;
		boolean estimateStill;
		double estimateBar;
		long estimateTick;
	}

	/**
	 * Counts a game tick. Call at the start of every one.
	 */
	void tick(long nowMillis)
	{
		if (lastTickMillis >= 0)
		{
			ticks += away ? Math.max(1, Math.round((nowMillis - lastTickMillis) / (double) Constants.GAME_TICK_LENGTH)) : 1;
		}
		lastTickMillis = nowMillis;
		away = false;
	}

	/**
	 * Notes that the client has stopped receiving ticks, by logging out, hopping or losing its connection.
	 */
	void away()
	{
		away = true;
	}

	void clear()
	{
		sightings.clear();
		lastTickMillis = -1;
		away = false;
	}

	/**
	 * Takes where a shoal on a timed route is this tick. Seeing it set off from or come to a stop gives its tick of the
	 * lap exactly, which is kept while it stays in view; until then, where it is on the route gives a near one.
	 */
	void see(Shoal shoal, ShoalRoute route)
	{
		double[] at = shoal.lastPosition();
		if (!route.timed() || at == null)
		{
			return;
		}

		Sighting sighting = sightings.computeIfAbsent(route.getName(), name -> new Sighting());
		// Out of view since it was last seen: an exact tick from before then may have drifted a little.
		if (sighting.seenTick < ticks - 1)
		{
			sighting.exact = false;
		}
		sighting.seenTick = ticks;

		// Only on the tick it sets off or arrives is the route looked at, to find the stop; setting off, it is where
		// it sat the tick before, as two stops can lie a couple of tiles apart.
		if (shoal.justLeft() || shoal.justArrived())
		{
			double[] before = shoal.previousPosition();
			int stop = shoal.justLeft() && before != null ? stopAt(route, before) : stopAt(route, at);
			if (stop >= 0)
			{
				int tick = shoal.justLeft() ? route.leaveTick(stop) : route.arriveTick(stop);
				sighting.lapStart = ticks - (tick + 1);
				sighting.exact = true;
				sighting.estimateAt = null;
				return;
			}
		}
		if (!sighting.exact)
		{
			// Only noted: searching the route for where it is would be wasted every tick it stays in view.
			sighting.estimateAt = at;
			sighting.estimateStill = shoal.sittingStill();
			sighting.estimateBar = shoal.stopBarFraction();
			sighting.estimateTick = ticks;
		}
	}

	/**
	 * The route's sighting with its tick of the lap worked out, from where the shoal was last seen if it hasn't been
	 * already, or null when it hasn't been seen this session or the route isn't timed.
	 */
	private Sighting settled(ShoalRoute route)
	{
		Sighting sighting = route == null ? null : sightings.get(route.getName());
		if (sighting == null || !route.timed())
		{
			return null;
		}
		if (sighting.estimateAt != null)
		{
			sighting.lapStart = sighting.estimateTick - estimate(route, sighting);
			sighting.estimateAt = null;
		}
		return sighting;
	}

	/**
	 * The tick of the lap a shoal was most likely on where it was seen, from where it was on the route, or at a stop,
	 * how far down its bar was.
	 */
	private static int estimate(ShoalRoute route, Sighting sighting)
	{
		double[] at = sighting.estimateAt;
		int stop = sighting.estimateStill ? stopAt(route, at) : -1;
		if (stop >= 0)
		{
			double bar = sighting.estimateBar;
			double sat = bar < 0 ? 0.5 : 1 - bar;
			return route.arriveTick(stop) + (int) Math.round(sat * route.stillTicks());
		}
		return route.tickAt(route.project(at[0], at[1]).distance);
	}

	/**
	 * The nearest stop within reach of a place, or -1.
	 */
	private static int stopAt(ShoalRoute route, double[] at)
	{
		int nearest = -1;
		double best = STOP_TILES * STOP_TILES;
		for (int stop = 0; stop < route.stopCount(); stop++)
		{
			double dx = route.stopX(stop) - at[0];
			double dy = route.stopY(stop) - at[1];
			if (dx * dx + dy * dy <= best)
			{
				best = dx * dx + dy * dy;
				nearest = stop;
			}
		}
		return nearest;
	}

	/**
	 * How far round its route the route's shoal should be now, or -1 when it hasn't been seen this session.
	 */
	double distance(ShoalRoute route)
	{
		Sighting sighting = settled(route);
		if (sighting == null)
		{
			return -1;
		}
		return route.forward(0, route.distanceAtTick(ticks - sighting.lapStart));
	}

	/**
	 * How far round its route the route's shoal should be at a moment between ticks, gliding from where it is on the
	 * last tick to where it will be on the next, or -1 when it hasn't been seen this session.
	 */
	double distance(ShoalRoute route, long nowMillis)
	{
		Sighting sighting = settled(route);
		if (sighting == null)
		{
			return -1;
		}
		long tick = ticks - sighting.lapStart;
		double from = route.distanceAtTick(tick);
		double to = route.distanceAtTick(tick + 1);
		double between = Math.max(0, Math.min(1, (nowMillis - lastTickMillis) / (double) Constants.GAME_TICK_LENGTH));
		return route.forward(0, from + route.forward(from, to) * between);
	}

	/**
	 * How much of its stop the route's shoal likely has left at a moment between ticks, running down smoothly, or -1
	 * from the tick it likely sets off, while it is likely swimming, and when it hasn't been seen this session.
	 */
	double stopLeft(ShoalRoute route, long nowMillis)
	{
		Sighting sighting = settled(route);
		if (sighting == null)
		{
			return -1;
		}
		double left = route.stopLeft(ticks - sighting.lapStart);
		if (left < 0)
		{
			return -1;
		}
		double between = Math.max(0, Math.min(1, (nowMillis - lastTickMillis) / (double) Constants.GAME_TICK_LENGTH));
		return Math.max(0, left - between / route.stillTicks());
	}

	/**
	 * Whether the route's shoal was seen on this tick.
	 */
	boolean inView(ShoalRoute route)
	{
		Sighting sighting = route == null ? null : sightings.get(route.getName());
		return sighting != null && sighting.seenTick == ticks;
	}
}
