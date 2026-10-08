package com.livelyfishingspots;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Debug: the river and lake drawing, and the picking menu's preview.
 */
class RiverDebugOverlay extends Overlay
{
	private final LivelyFishingSpotsDebugConfig config;
	private RiverDrawing drawing;
	private RiverBaker baker;

	@Inject
	RiverDebugOverlay(LivelyFishingSpotsDebugConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	void setRivers(RiverDrawing drawing, RiverBaker baker)
	{
		this.drawing = drawing;
		this.baker = baker;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.debugDraw() && drawing != null)
		{
			long started = TickTimes.start();
			drawing.drawDebug(graphics);
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
