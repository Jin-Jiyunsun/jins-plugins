package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/**
 * Draws every shoal route on the world map, whether or not its sea is anywhere near, which doubles
 * as a picture of the seas covered so far. Drawn straight after the world map itself, underneath
 * RuneLite's own map markers and anything else drawn over the map afterwards.
 */
class TrawlingPlusMapOverlay extends Overlay
{
	private static final int THICKNESS = 2;
	private static final int ARROW = 6;
	// A shoal's mark: its dot, this many pixels across; the arrow just ahead of it while it's moving, bigger than the
	// route's direction arrows and this far clear of the dot; and the game's stop bar under it while it's at a stop,
	// this wide.
	private static final int SHOAL_DOT = 8;
	private static final int HEADING_ARROW = 11;
	private static final int HEADING_GAP = 2;
	private static final int BAR_WIDTH = 26;

	// Stretches of route that pass close to where sea creatures that attack boats spawn, and the skull and
	// crossbones marking each place they spawn close enough to a route to threaten it, placed among the spawns
	// that are that close. The plugin puts the skulls on the map as RuneLite world map points, which gives them
	// the game's style of tooltip. With Show danger on the maps, the stretches are drawn whenever routes are, on the
	// minimap too, in the Danger colour; the skulls only on the world map. Where each creature spawns is never drawn
	// itself.
	static final BufferedImage DANGER_ICON = dangerIcon();

	// The tile the map is asked to place, which every other point is placed from.
	private static final int PLACED_TILE = 3200;

	/**
	 * A small skull and crossbones, drawn once. White with a dark edge, so it reads on sea and land alike.
	 */
	private static BufferedImage dangerIcon()
	{
		BufferedImage icon = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// The crossed bones, poking out from behind the skull at each corner.
		g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.BLACK);
		g.drawLine(2, 2, 10, 10);
		g.drawLine(10, 2, 2, 10);
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.WHITE);
		g.drawLine(2, 2, 10, 10);
		g.drawLine(10, 2, 2, 10);

		// The skull: a round head over a narrower jaw, with two dark eyes.
		g.setColor(Color.BLACK);
		g.fillOval(2, 1, 9, 8);
		g.fillRect(4, 6, 5, 4);
		g.setColor(Color.WHITE);
		g.fillOval(3, 2, 7, 6);
		g.fillRect(5, 6, 3, 3);
		g.setColor(Color.BLACK);
		g.fillRect(4, 4, 2, 2);
		g.fillRect(7, 4, 2, 2);
		g.dispose();
		return icon;
	}

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;
	private final WorldMapOverlay worldMapOverlay;
	private final SpriteManager spriteManager;
	private final MapRoutes.MapView mapView = new MapRoutes.MapView();

	@Inject
	TrawlingPlusMapOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config,
		WorldMapOverlay worldMapOverlay, SpriteManager spriteManager)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.worldMapOverlay = worldMapOverlay;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		// Right after the world map and at the lowest priority, so markers that RuneLite and other plugins draw
		// after the same interface at a higher priority sit on top of the routes rather than under them.
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(InterfaceID.WORLDMAP);
		setPriority(PRIORITY_LOW);
	}

	/**
	 * The part of the map showing the world, less the panels the world map puts over it (the overview and the
	 * list of maps), the same as RuneLite's own map markers leave out. A plain rectangle while neither is
	 * open, which keeps drawing on Java2D's quick path.
	 */
	private Shape mapArea(Rectangle within)
	{
		Area area = null;
		for (int panel : new int[]{InterfaceID.Worldmap.OVERVIEW_CONTAINER, InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0})
		{
			Widget widget = client.getWidget(panel);
			if (widget != null && !widget.isHidden())
			{
				if (area == null)
				{
					area = new Area(within);
				}
				area.subtract(new Area(widget.getBounds()));
			}
		}
		return area != null ? area : within;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TrawlingPlusConfig.ShowOnMaps where = config.showOnMaps();
		if (client.getGameState() != GameState.LOGGED_IN
			|| where == TrawlingPlusConfig.ShowOnMaps.OFF
			|| where == TrawlingPlusConfig.ShowOnMaps.MINIMAP)
		{
			return null;
		}

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map == null || map.isHidden())
		{
			return null;
		}

		// Deliberately not behind the Show guides setting: a map is for working out where to go, which
		// is something done before boarding a boat and fitting a net to it.
		Rectangle within = map.getBounds();

		// One tile placed by the map, and its zoom as the pixels a tile takes up. The map scales and shifts the world
		// without turning it, north up, so every other point is placed from these with a few sums rather than by asking
		// the map, which costs far more.
		Point placed = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(PLACED_TILE, PLACED_TILE, 0));
		double zoom = client.getWorldMap().getWorldMapZoom();
		if (placed == null || zoom <= 0)
		{
			return null;
		}
		mapView.place(PLACED_TILE, PLACED_TILE, placed.getX(), placed.getY(), zoom, 0, 0, -zoom);
		mapView.edges(within, false);

		// Which stretch of the world the map is showing, worked out by running the placing backwards from the corners
		// of the map, with a tile of slack. A run of route outside it is passed over without placing any of it.
		double fromX = PLACED_TILE + (within.x - placed.getX()) / zoom - 1;
		double toX = PLACED_TILE + (within.x + within.width - placed.getX()) / zoom + 1;
		double fromY = PLACED_TILE - (within.y + within.height - placed.getY()) / zoom - 1;
		double toY = PLACED_TILE - (within.y - placed.getY()) / zoom + 1;

		Shape clip = graphics.getClip();
		graphics.clip(mapArea(within));

		boolean danger = config.showDanger().onMaps();
		Color lineDanger = danger ? MapRoutes.withAlphaOf(config.dangerColour(), config.routeColour()) : null;
		Color arrowDanger = danger ? MapRoutes.withAlphaOf(config.dangerColour(), config.directionArrowColour()) : null;
		MapRoutes.MapTimer timer = MapRoutes.mapTimer;
		long lineNanos = 0;
		long arrowNanos = 0;
		for (ShoalRoute route : plugin.getShownRoutes())
		{
			// Whether any of the route could be on screen, from the box it fits inside. The map is panned and zoomed
			// freely, so most of the time most routes are nowhere near it.
			if (!route.overlaps(fromX, fromY, toX, toY))
			{
				continue;
			}

			// The arrows first, so the line runs over them.
			long started = timer == null ? 0 : System.nanoTime();
			MapRoutes.arrows(graphics, config, route, mapView, ARROW, fromX, fromY, toX, toY, arrowDanger);
			long drawn = timer == null ? 0 : System.nanoTime();
			MapRoutes.route(graphics, config, route, mapView, THICKNESS, fromX, fromY, toX, toY, lineDanger);
			if (timer != null)
			{
				arrowNanos += drawn - started;
				lineNanos += System.nanoTime() - drawn;
			}
		}
		if (timer != null)
		{
			timer.mapTook(MapRoutes.MapTimer.WORLD_LINES, lineNanos);
			timer.mapTook(MapRoutes.MapTimer.WORLD_ARROWS, arrowNanos);
		}

		drawShoals(graphics, mapView, zoom, -zoom);

		graphics.setClip(clip);
		return null;
	}

	/**
	 * Every shoal as one mark on its route's line, the same whether it's in view or not: a shoal in view where the
	 * game has it along its route, and the last one trawled, while out of view, where it likely is. Only for that one,
	 * out of view, is anything worked out.
	 */
	private void drawShoals(Graphics2D graphics, MapRoutes.MapView onto, double perTileX, double perTileY)
	{
		Color colour = config.shoalHeadingArrowColour();
		int tick = client.getTickCount();
		ShoalRoute trawled = plugin.getTrawledRoute();
		// By what the game is drawing this frame, so the last trawled shoal's dot hands over the moment it leaves or
		// comes into view, with neither a gap nor two at once.
		boolean trawledInView = false;
		// Only a shoal the client is drawing in the world is taken from the game. Shoals are dropped as they despawn
		// anyway, so this is belt and braces.
		for (Shoal shoal : plugin.getShoals())
		{
			if (!plugin.isShown(shoal) || !shoal.rendered(client))
			{
				continue;
			}
			trawledInView |= trawled != null && shoal.getRoute() == trawled;
			double[] at = MapRoutes.alongRoute(shoal, client);
			Point placed = at == null ? null : onto.at(at[0], at[1]);
			if (placed == null)
			{
				continue;
			}

			// The stop bar over it, read off the game; or else, as for a shoal out of view, the way its route runs
			// there.
			double left = shoal.stopBarNow(tick);
			boolean heading = left < 0 && at.length > 2;
			drawMark(graphics, placed, colour, left, heading ? at[2] * perTileX : 0, heading ? at[3] * perTileY : 0);
		}

		// The last trawled shoal, while out of view, where it likely is.
		if (config.showPredictedOnMap() && trawled != null && !trawledInView && plugin.getShownRoutes().contains(trawled))
		{
			ShoalForecast forecast = plugin.getForecast();
			long now = System.currentTimeMillis();
			double along = forecast.distance(trawled, now);
			double[] at = along < 0 ? null : trawled.pointAt(along);
			Point placed = at == null ? null : onto.at(at[0], at[1]);
			if (placed != null)
			{
				// At a stop, how much of it is likely left; otherwise the way it's heading, along the route.
				double left = forecast.stopLeft(trawled, now);
				drawMark(graphics, placed, colour, left, left < 0 ? at[2] * perTileX : 0, left < 0 ? at[3] * perTileY : 0);
			}
		}
	}

	/**
	 * A shoal's dot, and while at a stop the game's stop bar under it with this much left (-1 for none), or while
	 * moving an arrow just ahead of it pointing along a step on screen (none for no step).
	 */
	private void drawMark(Graphics2D graphics, Point at, Color colour, double left, double towardX, double towardY)
	{
		graphics.setColor(colour);
		graphics.fillOval(at.getX() - SHOAL_DOT / 2, at.getY() - SHOAL_DOT / 2, SHOAL_DOT, SHOAL_DOT);
		if (left >= 0)
		{
			drawStopBar(graphics, at.getX(), at.getY() + SHOAL_DOT / 2 + 4, left);
			return;
		}

		double step = Math.sqrt(towardX * towardX + towardY * towardY);
		if (step < 1e-6)
		{
			return;
		}
		double away = SHOAL_DOT / 2.0 + HEADING_GAP + HEADING_ARROW / 2.0;
		Point middle = new Point((int) Math.round(at.getX() + towardX / step * away),
			(int) Math.round(at.getY() + towardY / step * away));
		Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.fill(MapRoutes.arrowhead(middle.getX(), middle.getY(), towardX, towardY, HEADING_ARROW));
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			antialiasing != null ? antialiasing : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
	}

	/**
	 * The game's own stop bar, never a resource pack's, centred under a point, squeezed smoothly to the mark's width
	 * as it's drawn and filled to a share of it.
	 */
	private void drawStopBar(Graphics2D graphics, int x, int top, double full)
	{
		BufferedImage front = spriteManager.getSprite(SpriteID.HeadbarIce90.FRONT, 0);
		BufferedImage back = spriteManager.getSprite(SpriteID.HeadbarIce90.BACK, 0);
		if (front == null || back == null)
		{
			return;
		}
		int high = Math.min(front.getHeight(), back.getHeight());
		int left = x - BAR_WIDTH / 2;
		int filled = (int) Math.round(full * BAR_WIDTH);
		int frontTo = (int) Math.round(full * front.getWidth());
		int backFrom = (int) Math.round(full * back.getWidth());
		Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(front, left, top, left + filled, top + high, 0, 0, frontTo, high, null);
		graphics.drawImage(back, left + filled, top, left + BAR_WIDTH, top + high, backFrom, 0, back.getWidth(), high,
			null);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			interpolation != null ? interpolation : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
	}
}
