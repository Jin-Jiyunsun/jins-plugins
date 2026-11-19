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
 * The display on the boat, in its own layer above the game's health bars, hitsplats and overhead text but under the
 * interface. The route stays with the main overlay.
 */
class TrawlingPlusHelmOverlay extends Overlay
{
	private final Client client;
	private final TrawlingPlusOverlay overlay;

	TrawlingPlusHelmOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusOverlay overlay)
	{
		super(plugin);
		this.client = client;
		this.overlay = overlay;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Not just while the guides are showing: the hold's display shows in either guides mode, even on a raft.
		if (client.getGameState() != GameState.LOGGED_IN || !overlay.displayWanted())
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
