package com.livelyfishingspots;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * TEMPORARY: debug panel: the fish loaded, by sea, river and lake; models made; mapping times; and the time
 * each part takes a client tick.
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
		panelComponent.setPreferredSize(new Dimension(280, 0));
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
		heading("Fish loaded", String.valueOf(total));
		line("  Sea", String.valueOf(seaFish));
		// Each river and lake: fish now / most it keeps.
		for (String[] shoal : shoals)
		{
			line("  " + shoal[0], shoal[1]);
		}
		line("Models made", String.valueOf(models.count()));
		// The last river or lake started: each step's time.
		List<String[]> last = rivers.lastLoad();
		for (int k = 0; k < last.size(); k++)
		{
			if (k == 0)
			{
				heading(last.get(k)[0], last.get(k)[1] + " ms");
			}
			else
			{
				line(last.get(k)[0], last.get(k)[1]);
			}
		}
		List<String[]> timings = rivers.timings();
		if (!timings.isEmpty())
		{
			heading("Mapping, latest first", "ms");
			for (String[] timing : timings)
			{
				line(timing[0], timing[1].replace(" ms", ""));
			}
		}
		// Each part a client tick, over the last second; and what it allocated (garbage to collect later).
		heading("Each client tick", "avg / worst ms, KB/s");
		for (String[] part : TickTimes.rows())
		{
			line(part[0], part[1] + ", " + part[2]);
		}
		// Memory: all of RuneLite's heap, Java's garbage collections (a pause shows as a spike anywhere), and what
		// the river and lake maps hold.
		heading("Memory", "");
		Runtime java = Runtime.getRuntime();
		line("Java heap used", String.format("%d / %d MB", (java.totalMemory() - java.freeMemory()) >> 20,
			java.maxMemory() >> 20));
		line("Garbage collections (last s)", TickTimes.collected());
		line("River and lake maps", String.format("%.1f MB", rivers.mapsMb()));
		line("Warm-up at plugin start", String.format("%.1f ms", rivers.warmUpMs()));
		return super.render(graphics);
	}

	private void heading(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).leftColor(Color.YELLOW).right(right)
			.rightColor(Color.YELLOW).build());
	}

	private void line(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
	}
}
