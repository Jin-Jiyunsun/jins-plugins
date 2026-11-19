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
 * Draws the nearest shoal route on the minimap, with sparse direction arrows as on the world map.
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
	private static final int ARROW = 6;
	// A shoal's mark: its dot while still, this many pixels across, or its arrow while moving; and the arrow at the
	// minimap's edge pointing to the last trawled one off it.
	private static final int SHOAL_DOT = 6;
	private static final int HEADING_ARROW = 9;
	private static final int EDGE_ARROW = 11;
	// How far the edge arrow's middle sits inside the minimap's round edge.
	private static final int EDGE_ARROW_INSET = 8;

	// How far either side of the boat the minimap's placing is measured from, in tiles, so whole-pixel rounding barely
	// matters; and how far from the player the minimap is asked to place a point, in local units.
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

		// The map is round, so a line is cut off where it meets the circle by hand, as a clip that isn't a rectangle
		// takes Java2D off its quick path. The frame laps over the circle in places, so a line can meet it there.
		Rectangle bounds = minimap.getBounds();
		double radius = Math.min(bounds.width, bounds.height) / 2.0;

		// The route the boat is nearest, as on the water, drawn whether or not its shoal is in view so it can be
		// steered back to.
		ShoalRoute route = plugin.getNearestRoute();
		double[] boat = plugin.getBoatPlace();

		// Where tiles land, from two points the game places either side of the boat. The minimap turns and scales the
		// world evenly, so a step north is a step east turned a quarter.
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

		// Only the stretch of route the minimap could show, a couple of dozen tiles of the hundred loaded; the whole
		// scene with no boat to centre on.
		double reach = radius / pixelsPerTile + EDGE_SLACK;
		double fromX = boat == null ? view.getBaseX() : boat[0] - reach;
		double fromY = boat == null ? view.getBaseY() : boat[1] - reach;
		double toX = boat == null ? view.getBaseX() + view.getSizeX() : boat[0] + reach;
		double toY = boat == null ? view.getBaseY() + view.getSizeY() : boat[1] + reach;

		if (route != null && route.overlaps(fromX, fromY, toX, toY))
		{
			MapRoutes.MapTimer timer = MapRoutes.mapTimer;
			long started = timer == null ? 0 : System.nanoTime();
			boolean danger = config.showDanger().onMaps();
			// The arrows first, so the line runs over them.
			MapRoutes.arrows(graphics, config, route, mapView, ARROW, fromX, fromY, toX, toY,
				danger ? MapRoutes.withAlphaOf(config.dangerColour(), config.directionArrowColour()) : null);
			MapRoutes.route(graphics, config, route, mapView, THICKNESS, fromX, fromY, toX, toY,
				danger ? MapRoutes.withAlphaOf(config.dangerColour(), config.routeColour()) : null);
			if (timer != null)
			{
				timer.mapTook(MapRoutes.MapTimer.MINIMAP, System.nanoTime() - started);
			}
		}

		// Only a shoal the client is drawing in the world may be marked on the map.
		ShoalRoute trawled = plugin.getTrawledRoute();
		double[] trawledAt = null;
		for (Shoal shoal : plugin.getShoals())
		{
			if (plugin.isShown(shoal) && shoal.rendered(client))
			{
				MapRoutes.shoal(graphics, config, client, shoal, mapView, SHOAL_DOT, HEADING_ARROW);
				if (trawled != null && shoal.getRoute() == trawled)
				{
					trawledAt = MapRoutes.alongRoute(shoal, client);
				}
			}
		}

		drawEdgeArrow(graphics, bounds, radius, trawled, trawledAt);

		graphics.setClip(clip);
		return null;
	}

	/**
	 * While aboard, an arrow just inside the minimap's round edge pointing to the last trawled shoal while it's off
	 * the minimap: where the game has it if in view, or else where it likely is.
	 */
	private void drawEdgeArrow(Graphics2D graphics, Rectangle bounds, double radius, ShoalRoute trawled,
		double[] at)
	{
		if (trawled == null || !config.predictedMinimapEdgeArrow() || plugin.getAboardBoat() == null
			|| plugin.isEdgeArrowHidden() || !plugin.getShownRoutes().contains(trawled))
		{
			return;
		}
		if (at == null)
		{
			double along = plugin.getForecast().distance(trawled, System.currentTimeMillis());
			at = along < 0 ? null : trawled.pointAt(along);
		}
		if (at == null)
		{
			return;
		}
		double x = mapView.x(at[0], at[1]);
		double y = mapView.y(at[0], at[1]);
		if (mapView.contains(x, y))
		{
			return;
		}
		double towardX = x - bounds.getCenterX();
		double towardY = y - bounds.getCenterY();
		double away = Math.sqrt(towardX * towardX + towardY * towardY);
		double room = radius - EDGE_ARROW_INSET;
		if (away < 1e-6 || room <= 0)
		{
			return;
		}
		double arrowX = bounds.getCenterX() + towardX / away * room;
		double arrowY = bounds.getCenterY() + towardY / away * room;
		MapRoutes.pointer(graphics, arrowX, arrowY, config.shoalHeadingArrowColour(), EDGE_ARROW, towardX, towardY);
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
