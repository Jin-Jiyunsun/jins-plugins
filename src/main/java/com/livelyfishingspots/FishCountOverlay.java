package com.livelyfishingspots;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * TEMPORARY: debug panel counting the fish loaded, by sea, river and lake.
 */
class FishCountOverlay extends OverlayPanel
{
	private final LivelyFishingSpotsConfig config;
	private SeaSpotFish sea;
	private RiverSpotFish rivers;
	private FishModels models;

	@Inject
	FishCountOverlay(LivelyFishingSpotsConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(170, 0));
	}

	void setSources(SeaSpotFish sea, RiverSpotFish rivers, FishModels models)
	{
		this.sea = sea;
		this.rivers = rivers;
		this.models = models;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.debugDraw() || !config.debugRiverDrawCount() || sea == null || rivers == null || models == null)
		{
			return null;
		}
		int seaFish = sea.fishCount();
		String[][] shoals = rivers.counts();
		int total = seaFish;
		for (String[] shoal : shoals)
		{
			total += Integer.parseInt(shoal[2]);
		}
		panelComponent.getChildren().add(LineComponent.builder().left("Fish loaded").right(String.valueOf(total)).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Sea").right(String.valueOf(seaFish)).build());
		for (String[] shoal : shoals)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(shoal[0]).right(shoal[1]).build());
		}
		panelComponent.getChildren().add(LineComponent.builder().left("Models").right(String.valueOf(models.count()))
			.build());
		return super.render(graphics);
	}
}
