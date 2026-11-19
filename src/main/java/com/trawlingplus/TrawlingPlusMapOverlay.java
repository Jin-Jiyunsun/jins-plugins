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
import net.runelite.api.SpritePixels;
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
 * Draws every shown shoal route on the world map, straight after the map itself, so RuneLite's and other plugins' map
 * markers sit on top.
 */
class TrawlingPlusMapOverlay extends Overlay
{
	private static final int THICKNESS = 2;
	private static final int ARROW = 6;
	// A shoal's mark: its dot while still, this many pixels across, or its arrow while moving; the game's stop bar
	// under it at a stop, this wide; and the arrow at the map's edge pointing to the last trawled one off the map.
	private static final int SHOAL_DOT = 8;
	private static final int HEADING_ARROW = 12;
	private static final int EDGE_ARROW = 17;
	// How far the edge arrow's middle sits inside the map's edge.
	private static final int EDGE_ARROW_INSET = 12;
	private static final int BAR_WIDTH = 26;
	// How many pixels at the bar picture's right end are its end, taken as they are.
	private static final int BAR_END = 2;

	// A skull and crossbones, put on the world map as RuneLite world map points (for the game's style of tooltip) where
	// sea creatures that attack boats spawn close enough to threaten a route.
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
	private final MapRoutes.Clickable edgeArrow = new MapRoutes.Clickable();
	private final MapRoutes.Clickable trawledMark = new MapRoutes.Clickable();
	// The stop bar's two pictures as a skin or resource pack replaces them, and what each was read from.
	private final SpritePixels[] barSources = new SpritePixels[2];
	private final BufferedImage[] barPictures = new BufferedImage[2];

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
	 * The part of the map showing the world, less its overview and map list panels, as RuneLite's own markers leave
	 * out. A plain rectangle while neither is open, which keeps Java2D on its quick path.
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
		edgeArrow.clear();
		trawledMark.clear();
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

		// One tile placed by the map, and its zoom in pixels a tile. The map only scales and shifts the world, north
		// up, so every other point is placed from these with a few sums.
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

		drawShoals(graphics, mapView, within);

		graphics.setClip(clip);
		return null;
	}

	/**
	 * Every shoal as one mark on its route's line: one in view where the game has it along its route, and the last one
	 * trawled, while out of view, where it likely is.
	 */
	private void drawShoals(Graphics2D graphics, MapRoutes.MapView onto, Rectangle within)
	{
		Color colour = config.shoalHeadingArrowColour();
		int tick = client.getTickCount();
		ShoalRoute trawled = plugin.getTrawledRoute();
		// By what the game is drawing this frame, so the last trawled shoal's dot hands over the moment it leaves or
		// comes into view, with neither a gap nor two at once.
		boolean trawledInView = false;
		// Where the last trawled shoal is, in view or likely, while it's off the part of the map showing.
		double[] offMap = null;
		// Only a shoal the client is drawing in the world is taken from the game. Shoals are dropped as they despawn
		// anyway, so this is belt and braces.
		for (Shoal shoal : plugin.getShoals())
		{
			if (!plugin.isShown(shoal) || !shoal.rendered(client))
			{
				continue;
			}
			boolean isTrawled = trawled != null && shoal.getRoute() == trawled;
			trawledInView |= isTrawled;
			double[] at = MapRoutes.alongRoute(shoal, client);
			Point placed = at == null ? null : onto.at(at[0], at[1]);
			if (placed == null)
			{
				offMap = isTrawled && at != null ? at : offMap;
				continue;
			}

			// The stop bar over it, read off the game; or else, as for a shoal out of view, the way its route runs
			// there.
			double left = shoal.stopBarNow(tick);
			boolean heading = left < 0 && at.length > 2;
			drawMark(graphics, placed, colour, left, heading ? onto.stepX(at[2], at[3]) : 0,
				heading ? onto.stepY(at[2], at[3]) : 0);
			if (isTrawled)
			{
				noteTrawledMark(graphics, trawled, placed);
			}
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
				drawMark(graphics, placed, colour, left, onto.stepX(at[2], at[3]), onto.stepY(at[2], at[3]));
				noteTrawledMark(graphics, trawled, placed);
			}
			else
			{
				offMap = at;
			}
		}

		if (offMap != null && config.predictedMapEdgeArrow() && plugin.getAboardBoat() != null
			&& !plugin.isEdgeArrowHidden())
		{
			drawEdgeArrow(graphics, within, trawled, onto.x(offMap[0], offMap[1]), onto.y(offMap[0], offMap[1]), colour);
		}
	}

	/**
	 * An arrow just inside the map's edge, on the line from the map's middle to a point off it, pointing at it.
	 */
	private void drawEdgeArrow(Graphics2D graphics, Rectangle within, ShoalRoute route, double x, double y,
		Color colour)
	{
		double towardX = x - within.getCenterX();
		double towardY = y - within.getCenterY();
		double roomX = within.width / 2.0 - EDGE_ARROW_INSET;
		double roomY = within.height / 2.0 - EDGE_ARROW_INSET;
		double reach = Math.min(Math.abs(towardX) < 1e-6 ? Double.MAX_VALUE : roomX / Math.abs(towardX),
			Math.abs(towardY) < 1e-6 ? Double.MAX_VALUE : roomY / Math.abs(towardY));
		if (roomX <= 0 || roomY <= 0 || reach == Double.MAX_VALUE)
		{
			return;
		}
		double arrowX = within.getCenterX() + towardX * reach;
		double arrowY = within.getCenterY() + towardY * reach;
		MapRoutes.pointer(graphics, arrowX, arrowY, colour, EDGE_ARROW, towardX, towardY);
		// Only one showing can be right-clicked, not one under the map's panels.
		Shape showing = graphics.getClip();
		if (showing == null || showing.contains(arrowX, arrowY))
		{
			edgeArrow.drawn(route, arrowX, arrowY, EDGE_ARROW);
		}
	}

	/**
	 * Keeps where the last trawled shoal's mark was drawn, for its right-click menu, unless under the map's panels.
	 */
	private void noteTrawledMark(Graphics2D graphics, ShoalRoute route, Point placed)
	{
		Shape showing = graphics.getClip();
		if (showing == null || showing.contains(placed.getX(), placed.getY()))
		{
			trawledMark.drawn(route, placed.getX(), placed.getY(), Math.max(SHOAL_DOT, HEADING_ARROW));
		}
	}

	/**
	 * The route of the last trawled shoal's mark under a point on the canvas, or null. Client thread only.
	 */
	ShoalRoute trawledMarkAt(int x, int y)
	{
		return trawledMark.at(x, y);
	}

	/**
	 * The route of the edge arrow under a point on the canvas, or null. Client thread only.
	 */
	ShoalRoute edgeArrowAt(int x, int y)
	{
		return edgeArrow.at(x, y);
	}

	/**
	 * A shoal's mark: while moving, an arrow pointing along a step on screen; else its dot, and at a stop the game's
	 * stop bar under it with this much left (-1 for none).
	 */
	private void drawMark(Graphics2D graphics, Point at, Color colour, double left, double towardX, double towardY)
	{
		boolean stopped = left >= 0;
		MapRoutes.mark(graphics, at.getX(), at.getY(), colour, SHOAL_DOT, HEADING_ARROW, stopped ? 0 : towardX,
			stopped ? 0 : towardY);
		if (stopped)
		{
			drawStopBar(graphics, at.getX(), at.getY() + SHOAL_DOT / 2 + 4, left);
		}
	}

	/**
	 * The stop bar as the helm display shows it, a skin's or resource pack's if one replaces it, centred under a point
	 * and filled to a share. Never resized: the picture's left part, then its own right end.
	 */
	private void drawStopBar(Graphics2D graphics, int x, int top, double full)
	{
		BufferedImage front = barPicture(0, SpriteID.HeadbarIce90.FRONT);
		BufferedImage back = barPicture(1, SpriteID.HeadbarIce90.BACK);
		if (front == null || back == null)
		{
			return;
		}
		int wide = Math.min(BAR_WIDTH, Math.min(front.getWidth(), back.getWidth()));
		int high = Math.min(front.getHeight(), back.getHeight());
		int left = x - wide / 2;
		int filled = (int) Math.round(full * wide);
		drawBarPart(graphics, front, left, top, 0, filled, wide, high);
		drawBarPart(graphics, back, left, top, filled, wide, wide, high);
	}

	/**
	 * Columns from one up to another of a bar this wide, from one of its pictures, pixel for pixel: all but the last
	 * few from the picture's left part, and those from its right end.
	 */
	private static void drawBarPart(Graphics2D graphics, BufferedImage picture, int left, int top, int from, int to,
		int wide, int high)
	{
		int split = wide - BAR_END;
		int shift = picture.getWidth() - wide;
		if (from < split)
		{
			int end = Math.min(to, split);
			graphics.drawImage(picture, left + from, top, left + end, top + high, from, 0, end, high, null);
		}
		if (to > split)
		{
			int start = Math.max(from, split);
			graphics.drawImage(picture, left + start, top, left + to, top + high, start + shift, 0, to + shift, high,
				null);
		}
	}

	/**
	 * One of the stop bar's two pictures: the one a skin or resource pack has put in the client if there is one, since
	 * the sprite manager's own lookup skips them, read again only when it changes; the game's own otherwise.
	 */
	private BufferedImage barPicture(int which, int id)
	{
		SpritePixels replaced = client.getSpriteOverrides().get(id);
		if (replaced == null)
		{
			barSources[which] = null;
			barPictures[which] = null;
			return spriteManager.getSprite(id, 0);
		}
		if (replaced != barSources[which])
		{
			barSources[which] = replaced;
			barPictures[which] = replaced.toBufferedImage();
		}
		return barPictures[which];
	}
}
