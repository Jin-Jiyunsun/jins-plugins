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
import net.runelite.api.widgets.Widget;
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
	private static final int ARROW = 8;
	private static final int SHOAL = 4;

	// DEBUG: remove before release. The dot marking where a sea creature that attacks boats spawns.
	private static final int SEA_MONSTER = 4;
	private static final Color SEA_MONSTER_COLOUR = Color.RED;

	// Stretches of route that pass close to where sea creatures that attack boats spawn, and the skull and
	// crossbones marking each place they spawn close enough to a route to threaten it, placed among the spawns
	// that are that close. The plugin puts the skulls on the map as RuneLite world map points, which gives them
	// the game's style of tooltip. Behind a debug setting while being tried out.
	private static final Color DANGER_COLOUR = new Color(230, 40, 40);
	static final BufferedImage DANGER_ICON = dangerIcon();

	// How far apart the two points used to measure the map's zoom are, in tiles. Far enough that
	// rounding them to whole pixels barely matters.
	private static final int SCALE_TILES = 64;

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

	@Inject
	TrawlingPlusMapOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config,
		WorldMapOverlay worldMapOverlay)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.worldMapOverlay = worldMapOverlay;
		setPosition(OverlayPosition.DYNAMIC);
		// Right after the world map and at the lowest priority, so markers that RuneLite and other plugins draw
		// after the same interface at a higher priority sit on top of the routes rather than under them.
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(InterfaceID.WORLDMAP);
		setPriority(PRIORITY_LOW);
	}

	/**
	 * Whether the box a route fits inside reaches the part of the world the map is showing. The map
	 * scales and shifts the world without turning it, so the box stays a box on screen.
	 */
	private boolean onScreen(ShoalRoute route, Rectangle within)
	{
		int left = Integer.MAX_VALUE;
		int right = Integer.MIN_VALUE;
		int top = Integer.MAX_VALUE;
		int bottom = Integer.MIN_VALUE;
		for (double[] corner : route.corners())
		{
			Point at = worldMapOverlay.mapWorldPointToGraphicsPoint(
				new WorldPoint((int) Math.round(corner[0]), (int) Math.round(corner[1]), 0));
			if (at == null)
			{
				// Nothing to go on, so let it draw rather than hide a route that may well be there.
				return true;
			}

			left = Math.min(left, at.getX());
			right = Math.max(right, at.getX());
			top = Math.min(top, at.getY());
			bottom = Math.max(bottom, at.getY());
		}
		return within.intersects(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
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

		// How far the map is zoomed, as the pixels a tile takes up each way. Measured off points a known
		// distance apart rather than read from a setting, and taken from the map itself so it holds
		// wherever the map happens to be scrolled to. North is up, so the second of these comes out
		// negative.
		Point origin = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(3200, 3200, 0));
		Point across = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(3200 + SCALE_TILES, 3200, 0));
		Point up = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(3200, 3200 + SCALE_TILES, 0));
		double perTileX = origin == null || across == null ? 0
			: (across.getX() - origin.getX()) / (double) SCALE_TILES;
		double perTileY = origin == null || up == null ? 0
			: (up.getY() - origin.getY()) / (double) SCALE_TILES;
		double pixelsPerTile = Math.abs(perTileX);

		// Which stretch of the world the map is showing, worked out by running the scale backwards from
		// the corners of the map. A point outside it cannot be drawn, and saying so in tiles costs two
		// comparisons, where finding out by placing it on the map costs an object and a lookup. Zoomed
		// in, that is most of a route: the map holds only a few dozen tiles at a time.
		double fromX = Double.NEGATIVE_INFINITY;
		double toX = Double.POSITIVE_INFINITY;
		double fromY = Double.NEGATIVE_INFINITY;
		double toY = Double.POSITIVE_INFINITY;
		if (origin != null && perTileX != 0 && perTileY != 0)
		{
			double[] edgeX = {3200 + (within.x - origin.getX()) / perTileX,
				3200 + (within.x + within.width - origin.getX()) / perTileX};
			double[] edgeY = {3200 + (within.y - origin.getY()) / perTileY,
				3200 + (within.y + within.height - origin.getY()) / perTileY};
			// A tile of slack, so a point just outside still draws the line running off the edge.
			fromX = Math.min(edgeX[0], edgeX[1]) - 1;
			toX = Math.max(edgeX[0], edgeX[1]) + 1;
			fromY = Math.min(edgeY[0], edgeY[1]) - 1;
			toY = Math.max(edgeY[0], edgeY[1]) + 1;
		}

		double leftX = fromX;
		double rightX = toX;
		double bottomY = fromY;
		double topY = toY;
		MapRoutes.Projection onto = (x, y) ->
		{
			if (x < leftX || x > rightX || y < bottomY || y > topY)
			{
				return null;
			}

			// The map only places whole tiles, which is a staircase once a tile is worth more than a
			// pixel or two, so the part of a tile is stepped out by hand from the scale measured above.
			int tileX = (int) Math.floor(x);
			int tileY = (int) Math.floor(y);
			Point at = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(tileX, tileY, 0));
			if (at == null)
			{
				return null;
			}

			int px = (int) Math.round(at.getX() + (x - tileX) * perTileX);
			int py = (int) Math.round(at.getY() + (y - tileY) * perTileY);
			// Scrolled off the edge of the map, or hidden behind its own border. The line ends at the
			// last point still on the map rather than being cut off exactly at its edge, which is near
			// enough and keeps the points beyond it from being drawn at all.
			return within.contains(px, py) ? new Point(px, py) : null;
		};

		Shape clip = graphics.getClip();
		graphics.clip(mapArea(within));

		for (ShoalRoute route : plugin.getRoutes())
		{
			// Whether any of the route could be on screen, from the box it fits inside, rather than by
			// working out where every point of it lands only to throw the lot away. The map is panned
			// and zoomed freely, so most of the time most routes are nowhere near it.
			if (!onScreen(route, within))
			{
				continue;
			}

			MapRoutes.route(graphics, config, route, onto, THICKNESS, pixelsPerTile,
				leftX, bottomY, rightX, topY);
			if (config.debugDangerAreas())
			{
				MapRoutes.dangerStretches(graphics, route, onto, DANGER_COLOUR, THICKNESS, pixelsPerTile,
					leftX, bottomY, rightX, topY);
			}
			MapRoutes.arrows(graphics, config, route, onto, ARROW, pixelsPerTile,
				leftX, bottomY, rightX, topY, config.debugDangerAreas() ? DANGER_COLOUR : null);
		}

		// DEBUG: remove before release. Every spawn point of the sea creatures that attack boats.
		if (config.debugSeaMonsters())
		{
			graphics.setColor(SEA_MONSTER_COLOUR);
			for (int[] spawn : plugin.getSeaMonsters().spawns())
			{
				Point at = onto.at(spawn[0], spawn[1]);
				if (at != null)
				{
					graphics.fillOval(at.getX() - SEA_MONSTER / 2, at.getY() - SEA_MONSTER / 2, SEA_MONSTER, SEA_MONSTER);
				}
			}
		}

		// Only a shoal the client is drawing in the world may be marked on the map. Shoals are dropped
		// as they despawn anyway, so this is belt and braces, but it says so in the code rather than
		// leaving it to be worked out from the order events arrive in.
		for (Shoal shoal : plugin.getShoals())
		{
			if (shoal.rendered(client))
			{
				MapRoutes.shoal(graphics, config, client, shoal, onto, SHOAL);
			}
		}

		graphics.setClip(clip);
		return null;
	}
}
