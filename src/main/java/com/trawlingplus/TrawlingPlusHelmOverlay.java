package com.trawlingplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The display on the boat, drawn in a layer of its own above the game's health bars, hitsplats and overhead text,
 * which would otherwise cover it, but still under the interface. The route stays down on the water with the main
 * overlay; this only asks it to draw the display.
 */
class TrawlingPlusHelmOverlay extends Overlay
{
	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusOverlay overlay;

	TrawlingPlusHelmOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusOverlay overlay)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.overlay = overlay;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !plugin.showGuides())
		{
			return null;
		}

		Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		overlay.drawHelm(graphics);
		if (antialiasing != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
		return null;
	}
}
