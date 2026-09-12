package com.trawlingplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
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
 * Draws shoal routes on the minimap. Separate from the world map only because the two are drawn at
 * different sizes and from different sets of routes.
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
	private static final int ARROW = 5;
	private static final int SHOAL = 3;

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;

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
		TrawlingPlusConfig.ShowOnMaps where = config.showOnMaps();
		if (client.getGameState() != GameState.LOGGED_IN
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

		MapRoutes.Projection onto = (x, y) -> onMinimap(view, x, y);
		Shape clip = graphics.getClip();
		// The map is round inside a square widget, so clipping to the widget itself would let a line
		// spill into the corners, well outside the map. The frame laps over the edge of the circle in
		// places and a line can still meet it there, which is a good deal better than the alternatives:
		// drawing under the interface hides the line behind the map, and insetting the circle far
		// enough to clear the frame leaves a dead ring of map that nothing is ever drawn on.
		Rectangle bounds = minimap.getBounds();
		graphics.clip(new Ellipse2D.Double(bounds.x, bounds.y, bounds.width, bounds.height));

		// Every route reaching into the loaded map, not only the one being fished: a shoal swims out
		// of range long before its route does, and a line that vanishes with the shoal is no use for
		// steering back to it.
		for (ShoalRoute route : plugin.getRoutes())
		{
			// Only what the minimap could show: the rest would be projected only to be thrown away.
			if (route.overlaps(view.getBaseX(), view.getBaseY(),
				view.getBaseX() + view.getSizeX(), view.getBaseY() + view.getSizeY()))
			{
				MapRoutes.route(graphics, config, route, onto, THICKNESS, 1);
				MapRoutes.arrows(graphics, config, route, onto, ARROW);
			}
		}

		// Only a shoal the client is drawing in the world may be marked on the map.
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

	/**
	 * World tile coordinates as a point on the minimap, or null when they are off the loaded map or
	 * too far out for the minimap to hold.
	 */
	private Point onMinimap(WorldView view, double x, double y)
	{
		int localX = (int) Math.round((x - view.getBaseX()) * Perspective.LOCAL_TILE_SIZE)
			+ Perspective.LOCAL_HALF_TILE_SIZE;
		int localY = (int) Math.round((y - view.getBaseY()) * Perspective.LOCAL_TILE_SIZE)
			+ Perspective.LOCAL_HALF_TILE_SIZE;
		if (localX < 0 || localY < 0 || localX >= view.getSizeX() * Perspective.LOCAL_TILE_SIZE
			|| localY >= view.getSizeY() * Perspective.LOCAL_TILE_SIZE)
		{
			return null;
		}

		return Perspective.localToMinimap(client, new LocalPoint(localX, localY, view));
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
