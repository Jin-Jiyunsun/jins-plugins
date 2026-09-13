package com.trawlingplus;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * A shoal in the scene and the route it has been matched to, if any.
 */
final class Shoal
{
	// How long the heading arrow takes to fade fully in or out, in milliseconds: about a game tick.
	private static final double FADE_MILLIS = 600;

	// Once the route to a new next stop has drawn itself out, how long the stop takes to fade in, in
	// milliseconds. How long the route takes to draw out is set in the config.
	private static final double STOP_REVEAL_MILLIS = 300;

	private final WorldEntity entity;
	private ShoalRoute route;
	// Where the shoal was last found along its route, so it can be followed past a crossing.
	private double routeDistance = -1;

	private double[] lastPosition;
	private ShoalDepth depth = ShoalDepth.UNKNOWN;

	// The bar the game draws over a shoal while it sits at a stop, which empties as the stop runs out,
	// and how long this species is known to sit there. The bar is the same length whatever the species
	// but a stop is not, so without a known length there is nothing to turn the bar into a time.
	private int barLeft = -1;
	private int barScale;
	private int barStopTicks;
	private long stopEndsMillis = -1;
	private boolean sawNoBar;

	// A bar appearing this far below full did not just appear because a stop began. A shoal's world
	// entity turns up a tick or two before the fish inside it do, so it can look as though there was
	// no bar when really there was nothing to read it from yet.
	private static final int FRESH_SLACK = 6;
	// Hidden until the shoal is seen swimming, and whenever it sits still.
	private boolean headingArrowHidden = true;
	private double headingArrowOpacity;
	private long lastFadeMillis = -1;

	// The next stop the route is being drawn out to, and when that started.
	private int revealStop = -1;
	private long revealStartMillis;

	Shoal(WorldEntity entity)
	{
		this.entity = entity;
	}

	ShoalRoute getRoute()
	{
		return route;
	}

	void setRoute(ShoalRoute route)
	{
		if (route != this.route)
		{
			// Stop numbers belong to a route, so draw the new one out from the start.
			revealStop = -1;
			routeDistance = -1;
		}
		this.route = route;
	}

	/**
	 * Swaps in the same route with its line smoothed differently. Its stops are the same, so a next
	 * section that's drawing itself out carries on.
	 */
	void reshapeRoute(ShoalRoute route)
	{
		this.route = route;
	}

	/**
	 * Follows the shoal along its route, giving how far round it now is. Keeping hold of where it was
	 * is what stops it jumping to the other side of a crossing where a route doubles back on itself.
	 */
	double followRoute(double[] position)
	{
		routeDistance = route.project(position[0], position[1], routeDistance).distance;
		return routeDistance;
	}

	ShoalDepth getDepth()
	{
		return depth;
	}

	void setDepth(ShoalDepth depth)
	{
		this.depth = depth;
	}

	/**
	 * Takes the stop bar as it reads this tick. A bar that has gone means the shoal is on the move, and
	 * one that has gone up means it has settled at the next stop and the bar has been refilled.
	 */
	void setStopBar(int left, int scale)
	{
		if (left < 0)
		{
			// No bar, so the shoal is on the move and there is no stop to count down.
			stopEndsMillis = -1;
			sawNoBar = true;
		}
		else if (barLeft < 0 || left > barLeft)
		{
			// A stop has begun, so the clock is set running for as long as one lasts. When it began is
			// only known if the shoal was watched through the gap before it and the bar has turned up
			// full: a bar that was already part way down belongs to a stop that started before anyone
			// was looking, whether because the shoal was found mid stop or because it had not finished
			// loading, and that one is not counted at all.
			boolean fromTheStart = sawNoBar && scale > 0 && left >= scale - FRESH_SLACK;
			stopEndsMillis = fromTheStart
				? System.currentTimeMillis() + (long) barStopTicks * Constants.GAME_TICK_LENGTH
				: -1;
			sawNoBar = false;
		}

		barLeft = left;
		barScale = scale;
	}

	/**
	 * Takes how long this species sits at a stop, in ticks, which is how long the clock is set for.
	 */
	void seedStopTicks(int ticks)
	{
		barStopTicks = ticks;
	}

	/**
	 * How long the shoal has left at this stop, in seconds, or -1 when that is not known exactly. It is
	 * only known for a stop that was watched from its first tick: a shoal found part way through one
	 * could only be guessed at from how far down its bar is, and a guess is worse than nothing here.
	 */
	double secondsAtStop()
	{
		if (barLeft < 0 || stopEndsMillis < 0)
		{
			return -1;
		}

		return Math.max(0, (stopEndsMillis - System.currentTimeMillis()) / 1000.0);
	}

	/**
	 * Whether the shoal is sitting at a stop, as of the last tick.
	 */
	boolean stopped()
	{
		return headingArrowHidden;
	}

	/**
	 * Tracks whether the shoal is swimming or sitting still, once per game tick. The heading arrow
	 * hides the tick the shoal stops and shows again the tick it moves off.
	 */
	void update(double[] position)
	{
		if (lastPosition != null)
		{
			headingArrowHidden = position[0] == lastPosition[0] && position[1] == lastPosition[1];
		}
		lastPosition = position;
	}

	/**
	 * How opaque the heading arrow is right now, from 0 to 1. It moves towards shown or hidden a
	 * little on each call, so the arrow fades in and out instead of popping. Call once per frame.
	 */
	double headingArrowOpacity(long nowMillis)
	{
		if (lastFadeMillis >= 0)
		{
			double step = Math.max(0, nowMillis - lastFadeMillis) / FADE_MILLIS;
			headingArrowOpacity = headingArrowHidden
				? Math.max(0, headingArrowOpacity - step)
				: Math.min(1, headingArrowOpacity + step);
		}
		lastFadeMillis = nowMillis;

		// Smoothstep, so the fade eases in and out rather than changing at a constant rate.
		return headingArrowOpacity * headingArrowOpacity * (3 - 2 * headingArrowOpacity);
	}

	/**
	 * Notes the stop the shoal is heading for, once per frame. When it changes, the route to the new
	 * stop starts drawing itself out.
	 */
	void headFor(int nextStop, long nowMillis)
	{
		if (nextStop != revealStop)
		{
			revealStop = nextStop;
			revealStartMillis = nowMillis;
		}
	}

	/**
	 * How much of the route to the next stop is drawn so far, from 0 to 1, when it takes the given
	 * time to draw out.
	 */
	double routeReveal(long nowMillis, long revealMillis)
	{
		double t = Math.min(1, Math.max(0, nowMillis - revealStartMillis) / (double) revealMillis);
		// Sets off gently and settles into the stop, but without creeping in, which read as dragging on:
		// it arrives at a third of its average speed rather than coming to a crawl.
		return t * t * (8 - 5 * t) / 3;
	}

	/**
	 * How opaque the next stop is, from 0 to 1. It fades in once the route has been drawn out to it.
	 */
	double nextStopReveal(long nowMillis, long revealMillis)
	{
		double t = Math.min(1, Math.max(0, nowMillis - revealStartMillis - revealMillis) / STOP_REVEAL_MILLIS);
		return t * t * (3 - 2 * t);
	}

	/**
	 * The world view the shoal swims around in, which is normally the top-level one.
	 */
	WorldView parentView(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		return local == null ? null : client.getWorldView(local.getWorldView());
	}

	/**
	 * Whether the shoal is being drawn in the world right now. Only a shoal the client is rendering
	 * may be marked on a map: a remembered position is not the plugin's to show, and one that has
	 * swum out of range is no longer the plugin's to know.
	 */
	boolean rendered(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		WorldView view = local == null ? null : client.getWorldView(local.getWorldView());
		if (view == null)
		{
			return false;
		}

		// Inside the scene the client is drawing, rather than the wider area it has loaded around it.
		return local.getX() >= 0 && local.getY() >= 0
			&& local.getX() < view.getSizeX() * Perspective.LOCAL_TILE_SIZE
			&& local.getY() < view.getSizeY() * Perspective.LOCAL_TILE_SIZE;
	}

	/**
	 * The shoal's position in world tile coordinates, including the fraction of a tile, or null.
	 */
	double[] position(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		WorldView view = local == null ? null : client.getWorldView(local.getWorldView());
		return view == null ? null : toWorld(view, local);
	}

	private static double[] toWorld(WorldView view, LocalPoint local)
	{
		return new double[]{
			view.getBaseX() + (double) (local.getX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE,
			view.getBaseY() + (double) (local.getY() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE
		};
	}
}
