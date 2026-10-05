package com.livelyfishingspots;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Debug drawing for the river fish.
 */
class RiverDebugOverlay extends Overlay
{
	private final LivelyFishingSpotsConfig config;
	private RiverSpotFish rivers;

	@Inject
	RiverDebugOverlay(LivelyFishingSpotsConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	void setRivers(RiverSpotFish rivers)
	{
		this.rivers = rivers;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.debugDraw() && rivers != null)
		{
			rivers.drawDebug(graphics);
		}
		return null;
	}
}
