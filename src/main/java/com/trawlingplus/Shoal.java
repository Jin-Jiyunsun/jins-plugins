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
	private final WorldEntity entity;
	private ShoalRoute route;

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
		if (view == null)
		{
			return null;
		}

		return new double[]{
			view.getBaseX() + (double) (local.getX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE,
			view.getBaseY() + (double) (local.getY() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE
		};
	}
}
