package com.trawlingplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
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

	// The game's pictures of which pixels of the minimap show the map, one per size of minimap; and how far a
	// picture's size may be from the minimap's to be taken as its own, in pixels.
	private static final int[] MASKS = {SpriteID.RESIZE_MAP_MASK, SpriteID.FIXED_MAP_MASK};
	private static final int MASK_SLACK = 4;
	// How many game ticks after a setting or plugin change to look for a resource pack's new pictures, as a pack
	// puts them in a little after its setting changes.
	private static final int PACK_CHECK_TICKS = 10;

	private static final int THICKNESS = 1;
	private static final int ARROW = 6;
	// A shoal's mark: its dot while still, this many pixels across, or its arrow while moving; and the arrow at the
	// minimap's edge pointing to the last trawled one off it.
	private static final int SHOAL_DOT = 6;
	private static final int HEADING_ARROW = 9;
	// A stop's outline, across.
	private static final int STOP = 5;
	private static final int EDGE_ARROW = 11;
	// How far the edge arrow's middle sits inside the minimap's edge.
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
	private final SpriteManager spriteManager;
	private final MapRoutes.MapView mapView = new MapRoutes.MapView();

	// The minimap's mask as which pixels show the map, and the minimap size it was picked for.
	private boolean[] mask;
	private int maskWidth;
	private int maskHeight;
	private int maskForWidth = -1;
	private int maskForHeight = -1;
	// The pictures the mask was made from, by sprite id, and the resource pack picture each had then (null for the
	// game's own), so a pack change makes it again.
	private final Map<Integer, SpritePixels> maskSources = new HashMap<>();
	// The game tick to stop looking for a resource pack's new pictures on.
	private int packCheckUntil = -1;

	@Inject
	TrawlingPlusMinimapOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config,
		SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
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

		// Lines are cut where the game's mask of the minimap ends (a circle without one) by hand, as a clip that isn't
		// a rectangle takes Java2D off its quick path.
		Rectangle bounds = minimap.getBounds();
		double radius = Math.max(bounds.width, bounds.height) / 2.0;

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
		shapeByMask(minimap, bounds);
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
			long drawn = timer == null ? 0 : System.nanoTime();
			if (config.showStops())
			{
				MapRoutes.stops(graphics, config, route, mapView, STOP, fromX, fromY, toX, toY,
					MapRoutes.stopStates(plugin, client, route, -1, false));
			}
			if (timer != null)
			{
				timer.mapTook(MapRoutes.MapTimer.MINIMAP, drawn - started);
				if (config.showStops())
				{
					timer.mapTook(MapRoutes.MapTimer.MINIMAP_STOPS, System.nanoTime() - drawn);
				}
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

		drawEdgeArrow(graphics, bounds, trawled, trawledAt);

		graphics.setClip(clip);
		return null;
	}

	/**
	 * While aboard, an arrow just inside the minimap's edge pointing to the last trawled shoal while it's off
	 * the minimap: where the game has it if in view, or else where it likely is.
	 */
	private void drawEdgeArrow(Graphics2D graphics, Rectangle bounds, ShoalRoute trawled, double[] at)
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
		if (away < 1e-6)
		{
			return;
		}
		double room = mapView.edgeFromMiddle(towardX / away, towardY / away) - EDGE_ARROW_INSET;
		if (room <= 0)
		{
			return;
		}
		double arrowX = bounds.getCenterX() + towardX / away * room;
		double arrowY = bounds.getCenterY() + towardY / away * room;
		MapRoutes.pointer(graphics, arrowX, arrowY, config.shoalHeadingArrowColour(), EDGE_ARROW, towardX, towardY);
	}

	/**
	 * Shapes the minimap by the game's mask the size of it, less what its frame covers, made again only when its size
	 * or a resource pack changes; left round if no mask fits.
	 */
	private void shapeByMask(Widget minimap, Rectangle bounds)
	{
		if (bounds.width != maskForWidth || bounds.height != maskForHeight || packChanged())
		{
			mask = null;
			maskSources.clear();
			BufferedImage best = null;
			int bestOff = MASK_SLACK + 1;
			boolean allLoaded = true;
			for (int id : MASKS)
			{
				SpritePixels replaced = client.getSpriteOverrides().get(id);
				maskSources.put(id, replaced);
				BufferedImage image = replaced != null ? replaced.toBufferedImage() : spriteManager.getSprite(id, 0);
				if (image == null)
				{
					allLoaded = false;
					continue;
				}
				int off = Math.max(Math.abs(image.getWidth() - bounds.width),
					Math.abs(image.getHeight() - bounds.height));
				if (off < bestOff)
				{
					best = image;
					bestOff = off;
				}
			}
			if (best != null)
			{
				maskWidth = best.getWidth();
				maskHeight = best.getHeight();
				int[] pixels = best.getRGB(0, 0, maskWidth, maskHeight, null, 0, maskWidth);
				// The map shows where the mask is as see-through as at its middle.
				boolean clearShows = pixels[maskHeight / 2 * maskWidth + maskWidth / 2] >>> 24 == 0;
				mask = new boolean[pixels.length];
				for (int i = 0; i < pixels.length; i++)
				{
					mask[i] = (pixels[i] >>> 24 == 0) == clearShows;
				}
				coverByFrame(minimap, bounds);
			}
			if (best != null || allLoaded)
			{
				maskForWidth = bounds.width;
				maskForHeight = bounds.height;
			}
		}
		if (mask != null)
		{
			mapView.shape(mask, maskWidth, maskHeight);
		}
	}

	/**
	 * Looks for a resource pack's new pictures over the next few game ticks. Client thread only.
	 */
	void packMayChange()
	{
		packCheckUntil = client.getTickCount() + PACK_CHECK_TICKS;
	}

	/**
	 * Whether a resource pack has changed any picture the mask was made from, checked only for a few game ticks after
	 * {@link #packMayChange}.
	 */
	private boolean packChanged()
	{
		if (client.getTickCount() >= packCheckUntil)
		{
			return false;
		}
		Map<Integer, SpritePixels> replaced = client.getSpriteOverrides();
		for (Map.Entry<Integer, SpritePixels> source : maskSources.entrySet())
		{
			if (replaced.get(source.getKey()) != source.getValue())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes out of the mask every pixel the pictures drawn after the minimap, such as its frame, draw over: the
	 * mask is where the map is drawn, and in some layouts the frame laps over it.
	 */
	private void coverByFrame(Widget minimap, Rectangle bounds)
	{
		Widget parent = minimap.getParent();
		Widget[] siblings = parent == null ? null : parent.getStaticChildren();
		if (siblings == null)
		{
			return;
		}
		int maskX = bounds.x + (bounds.width - maskWidth) / 2;
		int maskY = bounds.y + (bounds.height - maskHeight) / 2;
		boolean after = false;
		for (Widget sibling : siblings)
		{
			if (sibling == minimap)
			{
				after = true;
			}
			else if (after && sibling != null && !sibling.isHidden() && sibling.getSpriteId() >= 0
				&& !sibling.getSpriteTiling() && sibling.getBounds().intersects(bounds))
			{
				cover(sibling, maskX, maskY);
			}
		}
	}

	/**
	 * Takes out of the mask every pixel a picture widget draws over, its resource pack picture if it has one.
	 */
	private void cover(Widget widget, int maskX, int maskY)
	{
		SpritePixels picture = client.getSpriteOverrides().get(widget.getSpriteId());
		maskSources.put(widget.getSpriteId(), picture);
		if (picture == null)
		{
			SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), widget.getSpriteId(), 0);
			picture = sprites == null || sprites.length == 0 ? null : sprites[0];
		}
		if (picture == null)
		{
			return;
		}
		Rectangle at = widget.getBounds();
		int[] pixels = picture.getPixels();
		int width = picture.getWidth();
		int height = picture.getHeight();
		// From a mask pixel to the same spot in the picture.
		int shiftX = maskX - at.x - picture.getOffsetX();
		int shiftY = maskY - at.y - picture.getOffsetY();
		for (int y = 0; y < maskHeight; y++)
		{
			int pictureY = y + shiftY;
			if (pictureY < 0 || pictureY >= height)
			{
				continue;
			}
			for (int x = 0; x < maskWidth; x++)
			{
				int pictureX = x + shiftX;
				if (pictureX >= 0 && pictureX < width && pixels[pictureY * width + pictureX] != 0)
				{
					mask[y * maskWidth + x] = false;
				}
			}
		}
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
