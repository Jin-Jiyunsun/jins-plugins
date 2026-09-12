package com.trawlingplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
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
 * as a picture of the seas covered so far. Drawn over the interface, since the map it belongs to is
 * part of the interface itself.
 */
class TrawlingPlusMapOverlay extends Overlay
{
	private static final int THICKNESS = 2;
	private static final int ARROW = 7;
	private static final int SHOAL = 4;

	// The world map is drawn far enough out that most of a route's points land on the same pixel, so
	// only every so many of them is worth converting.
	private static final int STRIDE = 4;

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
		setLayer(OverlayLayer.ABOVE_WIDGETS);
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
		MapRoutes.Projection onto = (x, y) ->
		{
			Point at = worldMapOverlay.mapWorldPointToGraphicsPoint(
				new WorldPoint((int) Math.round(x), (int) Math.round(y), 0));
			// Scrolled off the edge of the map, or hidden behind its own border.
			return at != null && within.contains(at.getX(), at.getY()) ? at : null;
		};

		Shape clip = graphics.getClip();
		graphics.clip(within);

		for (ShoalRoute route : plugin.getRoutes())
		{
			MapRoutes.route(graphics, config, route, onto, THICKNESS, STRIDE);
			MapRoutes.arrows(graphics, config, route, onto, ARROW);
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
