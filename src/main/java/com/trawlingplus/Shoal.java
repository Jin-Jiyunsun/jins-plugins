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

	private final WorldEntity entity;
	private ShoalRoute route;

	private double[] lastTarget;
	// Hidden until the shoal is seen swimming, and whenever it sits still.
	private boolean headingArrowHidden = true;
	private double headingArrowOpacity;
	private long lastFadeMillis = -1;

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
		this.route = route;
	}

	/**
	 * Tracks whether the shoal is swimming or sitting still, once per game tick, from where it's
	 * heading. While a shoal swims its target moves on every tick; the first tick it doesn't, the
	 * shoal is easing into a stop, about a tick before it comes to rest. The heading arrow hides then,
	 * and shows again on the tick the target moves off.
	 */
	void update(double[] target)
	{
		if (target == null)
		{
			return;
		}

		if (lastTarget != null)
		{
			headingArrowHidden = target[0] == lastTarget[0] && target[1] == lastTarget[1];
		}
		lastTarget = target;
	}

	/**
	 * Whether the heading arrow should be hidden: the shoal is sitting still, or hasn't been seen
	 * swimming yet.
	 */
	boolean isHeadingArrowHidden()
	{
		return headingArrowHidden;
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
	 * The world view the shoal swims around in, which is normally the top-level one.
	 */
	WorldView parentView(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		return local == null ? null : client.getWorldView(local.getWorldView());
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

	/**
	 * Where the shoal is heading this tick, in world tile coordinates; its position if it has no
	 * target; or null.
	 */
	double[] target(Client client)
	{
		LocalPoint target = entity.getTargetLocation();
		WorldView view = parentView(client);
		return target == null || view == null ? position(client) : toWorld(view, target);
	}

	private static double[] toWorld(WorldView view, LocalPoint local)
	{
		return new double[]{
			view.getBaseX() + (double) (local.getX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE,
			view.getBaseY() + (double) (local.getY() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE
		};
	}
}
