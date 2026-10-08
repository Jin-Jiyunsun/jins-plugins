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
	private RiverBaker baker;

	@Inject
	RiverDebugOverlay(LivelyFishingSpotsConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	void setRivers(RiverSpotFish rivers, RiverBaker baker)
	{
		this.rivers = rivers;
		this.baker = baker;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.debugDraw() && rivers != null)
		{
			long started = System.nanoTime();
			rivers.drawDebug(graphics);
			if (baker != null && (config.debugRiverDrawPoints() || config.debugPick()))
			{
				baker.drawBlockers(graphics);
			}
			TickTimes.add(TickTimes.DRAWING, started);
		}
		// The picking menu's hovered mark entry, whatever else is drawn.
		if (baker != null && config.debugPick())
		{
			baker.drawPreview(graphics);
		}
		return null;
	}
}
