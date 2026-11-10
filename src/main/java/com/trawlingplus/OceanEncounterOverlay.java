package com.trawlingplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines the ocean encounters picked in the settings.
 */
class OceanEncounterOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 2;

	private final OceanEncounters encounters;
	private final TrawlingPlusConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	OceanEncounterOverlay(OceanEncounters encounters, TrawlingPlusConfig config, ModelOutlineRenderer outlineRenderer)
	{
		this.encounters = encounters;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<NPC> shown = encounters.shown();
		if (shown.isEmpty())
		{
			return null;
		}
		Color colour = config.encounterColour();
		for (NPC npc : shown)
		{
			outlineRenderer.drawOutline(npc, OUTLINE_WIDTH, colour, OUTLINE_FEATHER);
		}
		return null;
	}
}
