package com.trawlingplus;

import net.runelite.api.Client;
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

	private double[] lastPosition;
	private ShoalDepth depth = ShoalDepth.UNKNOWN;
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

	ShoalDepth getDepth()
	{
		return depth;
	}

	void setDepth(ShoalDepth depth)
	{
		this.depth = depth;
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
		// Smoothstep, so the line sets off gently, glides along, and settles into the stop.
		return t * t * (3 - 2 * t);
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
