package com.trawlingplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the nearest shoal route on the minimap. Separate from the world map because the two are drawn
 * at different sizes, from different sets of routes, and the minimap leaves out the direction arrows:
 * at its size they cost more to place than they are worth.
 */
class TrawlingPlusMinimapOverlay extends Overlay
{
	// The minimap sits in a different place in each of the three screen layouts.
	private static final int[] MINIMAPS = {
		InterfaceID.Toplevel.MINIMAP,
		InterfaceID.ToplevelOsrsStretch.MINIMAP,
		InterfaceID.ToplevelPreEoc.MINIMAP
	};

	private static final int THICKNESS = 1;
	private static final int SHOAL = 3;

	// How far either side of the boat the two points the minimap's placing is measured from are, in tiles: far enough
	// that rounding them to whole pixels barely matters. And how far from the player the minimap is asked to place a
	// point, in local units, which reaches well past them.
	private static final int SCALE_TILES = 64;
	private static final int REACH = 128 * Perspective.LOCAL_TILE_SIZE;
	// How much slack to leave around what the minimap can show, in tiles: it's centred on the player rather than on
	// the boat, and a boat is ten tiles long.
	private static final double EDGE_SLACK = 8;

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;
	private final MapRoutes.MapView mapView = new MapRoutes.MapView();

	@Inject
	TrawlingPlusMinimapOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Behind Show guides, and so behind being on a boat at all, unlike the world map: the minimap is
		// for steering by, which is done from a boat.
		TrawlingPlusConfig.ShowOnMaps where = config.showOnMaps();
		if (client.getGameState() != GameState.LOGGED_IN
			|| !plugin.showGuides()
			|| where == TrawlingPlusConfig.ShowOnMaps.OFF
			|| where == TrawlingPlusConfig.ShowOnMaps.WORLD_MAP)
		{
			return null;
		}

		WorldView view = client.getTopLevelWorldView();
		Widget minimap = minimap();
		if (view == null || minimap == null)
		{
			return null;
		}

		// The map is round inside a square widget, so a line is cut off where it meets the circle, by hand rather
		// than by clipping the drawing to a circle: a clip that is not a rectangle takes Java2D off its quick path,
		// and it is charged for on everything drawn afterwards. The frame laps over the edge of the circle in places
		// and a line can still meet it there, which is better than the alternatives: drawing under the interface
		// hides the line behind the map, and pulling the circle in far enough to clear the frame leaves a ring of
		// map nothing is ever drawn on.
		Rectangle bounds = minimap.getBounds();
		double radius = Math.min(bounds.width, bounds.height) / 2.0;

		// The route the boat is nearest, the same one the water shows, rather than every route that
		// reaches the loaded map. It is drawn whether or not its shoal is in view: a shoal swims out of
		// range long before its route does, and a line that vanishes with the shoal is no use for
		// steering back to it.
		ShoalRoute route = plugin.getNearestRoute();
		double[] boat = plugin.getBoatPlace();

		// Where tiles land on the minimap, from two points the game places either side of the boat. The minimap turns
		// the world and scales it the same both ways, so the step one tile north makes is the step east turned a
		// quarter, and every other point is placed from these with a few sums.
		double middleX = boat == null ? view.getBaseX() + view.getSizeX() / 2.0 : boat[0];
		double middleY = boat == null ? view.getBaseY() + view.getSizeY() / 2.0 : boat[1];
		Point east = onMinimap(view, middleX + SCALE_TILES, middleY);
		Point west = onMinimap(view, middleX - SCALE_TILES, middleY);
		if (east == null || west == null)
		{
			return null;
		}
		double eastX = (east.getX() - west.getX()) / (2.0 * SCALE_TILES);
		double eastY = (east.getY() - west.getY()) / (2.0 * SCALE_TILES);
		mapView.place(middleX, middleY, (east.getX() + west.getX()) / 2.0, (east.getY() + west.getY()) / 2.0,
			eastX, eastY, eastY, -eastX);
		mapView.edges(bounds, true);
		double pixelsPerTile = mapView.pixelsPerTile();
		if (pixelsPerTile <= 0)
		{
			return null;
		}

		Shape clip = graphics.getClip();
		graphics.clip(bounds);

		// Only the stretch of route the minimap could show, which is a good deal less than the map that
		// is loaded: the minimap holds a couple of dozen tiles where the scene holds a hundred. Without
		// this, most of the route is placed on the minimap only to be found outside its edge and thrown
		// away. With no boat to centre it on, the whole scene is used.
		double reach = radius / pixelsPerTile + EDGE_SLACK;
		double fromX = boat == null ? view.getBaseX() : boat[0] - reach;
		double fromY = boat == null ? view.getBaseY() : boat[1] - reach;
		double toX = boat == null ? view.getBaseX() + view.getSizeX() : boat[0] + reach;
		double toY = boat == null ? view.getBaseY() + view.getSizeY() : boat[1] + reach;

		if (route != null && route.overlaps(fromX, fromY, toX, toY))
		{
			MapRoutes.MapTimer timer = MapRoutes.mapTimer;
			long started = timer == null ? 0 : System.nanoTime();
			MapRoutes.route(graphics, config, route, mapView, THICKNESS, fromX, fromY, toX, toY,
				config.showDanger().onMaps() ? MapRoutes.withAlphaOf(config.dangerColour(), config.routeColour()) : null);
			if (timer != null)
			{
				timer.mapTook(MapRoutes.MapTimer.MINIMAP, System.nanoTime() - started);
			}
		}

		// Only a shoal the client is drawing in the world may be marked on the map.
		for (Shoal shoal : plugin.getShoals())
		{
			if (plugin.isShown(shoal) && shoal.rendered(client))
			{
				MapRoutes.shoal(graphics, config, client, shoal, mapView, SHOAL);
			}
		}

		graphics.setClip(clip);
		return null;
	}

	/**
	 * World tile coordinates as a point on the minimap, placed by the game, or null when it has no minimap up.
	 */
	private Point onMinimap(WorldView view, double x, double y)
	{
		int localX = (int) Math.round((x - view.getBaseX()) * Perspective.LOCAL_TILE_SIZE)
			+ Perspective.LOCAL_HALF_TILE_SIZE;
		int localY = (int) Math.round((y - view.getBaseY()) * Perspective.LOCAL_TILE_SIZE)
			+ Perspective.LOCAL_HALF_TILE_SIZE;
		return Perspective.localToMinimap(client, new LocalPoint(localX, localY, view), REACH);
	}

	/**
	 * Whichever screen layout is up, and its minimap, or null if none of them is.
	 */
	private Widget minimap()
	{
		for (int id : MINIMAPS)
		{
			Widget found = client.getWidget(id);
			if (found != null && !found.isHidden())
			{
				return found;
			}
		}
		return null;
	}
}
