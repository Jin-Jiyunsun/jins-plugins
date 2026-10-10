package com.livelyfishingspots;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.api.Model;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Debug panel: the fish loaded, by sea, river and lake; models made; mapping times; the time each part
 * takes a client tick; and memory. Each section can be collapsed to its heading from the panel's right-click menu
 * (RuneLite's own overlay menu, nothing sent to the game).
 */
class FishCountOverlay extends OverlayPanel
{
	private final LivelyFishingSpotsDebugConfig config;
	private SeaSpotFish sea;
	private RiverSpotFish rivers;
	private FishModels models;
	// The sections, by their headings' names; the one being drawn, and those collapsed (a comma list, kept in the
	// config so they stay collapsed).
	static final List<String> SECTIONS = List.of("Fish loaded", "Last load", "Mapping", "Each client tick", "Memory");
	private String section;
	private List<String> collapsed = List.of();

	@Inject
	FishCountOverlay(LivelyFishingSpotsDebugConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		menu(config.debugPanelCollapsed());
		panelComponent.setPreferredSize(new Dimension(250, 0));
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
		// The small font, so the long panel takes less of the screen.
		graphics.setFont(FontManager.getRunescapeSmallFont());
		collapsed = Arrays.asList(config.debugPanelCollapsed().split(","));
		int seaFish = sea.fishCount();
		String[][] shoals = RiverDrawing.counts(rivers);
		int total = seaFish;
		for (String[] shoal : shoals)
		{
			total += Integer.parseInt(shoal[2]);
		}
		heading("Fish loaded", "Fish loaded", String.valueOf(total));
		line("  Sea", String.valueOf(seaFish));
		// All rivers' fish against the target, and how far their spacing's widened.
		line("  All rivers / target", rivers.riverFish + " / " + RiverSpotFish.RIVER_FISH_TARGET);
		line("  Dynamic spacing", String.format("%.0f%%", rivers.crowding * 100));
		// Each river and lake: fish now / most it keeps.
		for (String[] shoal : shoals)
		{
			line("  " + shoal[0], shoal[1]);
		}
		line("Models made", String.valueOf(models.count()));
		// The last river or lake started: each step's time.
		List<String[]> last = TickTimes.lastLoad();
		for (int k = 0; k < last.size(); k++)
		{
			if (k == 0)
			{
				heading("Last load", last.get(k)[0], last.get(k)[1] + " ms");
			}
			else
			{
				line(last.get(k)[0], last.get(k)[1]);
			}
		}
		List<String[]> timings = TickTimes.timings();
		if (!timings.isEmpty())
		{
			heading("Mapping", "Mapping, latest first", "ms");
			for (String[] timing : timings)
			{
				line(timing[0], timing[1].replace(" ms", ""));
			}
		}
		// Each part a client tick, over the last second; and what it allocated (garbage to collect later).
		// Columns, so each number sits under its header.
		section = "Each client tick";
		boolean folded = collapsed.contains(section);
		panelComponent.getChildren().add(new ColumnLine((folded ? "+ " : "") + "Each client tick (ms)", Color.YELLOW,
			"avg", "worst", "KB/s"));
		for (String[] part : folded ? List.<String[]>of() : TickTimes.rows())
		{
			panelComponent.getChildren().add(new ColumnLine(part[0], Color.WHITE, part[1], part[2], part[3]));
		}
		// Memory: all of RuneLite's heap, Java's garbage collections (a pause shows as a spike anywhere), and what
		// the river and lake maps hold.
		heading("Memory", "Memory", "");
		MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		line("Java heap used", String.format("%d / %d MB", heap.getUsed() >> 20, heap.getMax() >> 20));
		line("Garbage collections (last s)", TickTimes.collected());
		line("River and lake maps", String.format("%.1f MB", RiverDrawing.mapsMb(rivers)));
		line("Fish models", String.format("%.1f MB", modelsMb(models)));
		line("Warm-up at plugin start", String.format("%.1f ms", rivers.warmUpMs));
		return super.render(graphics);
	}

	/**
	 * Starts a section: its heading, with a + in front if collapsed (its lines then left out).
	 */
	private void heading(String name, String left, String right)
	{
		section = name;
		panelComponent.getChildren().add(LineComponent.builder().left((collapsed.contains(name) ? "+ " : "") + left)
			.leftColor(Color.YELLOW).right(right).rightColor(Color.YELLOW).build());
	}

	/**
	 * The right-click menu: collapse or expand each section, from the collapsed ones (a comma list).
	 */
	void menu(String collapsedList)
	{
		List<String> folded = Arrays.asList(collapsedList.split(","));
		getMenuEntries().clear();
		for (String name : SECTIONS)
		{
			getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,
				folded.contains(name) ? "Expand" : "Collapse", name));
		}
	}

	/**
	 * The memory the made fish models' arrays hold, MB.
	 */
	private static double modelsMb(FishModels models)
	{
		long bytes = 0;
		for (Model model : models.made())
		{
			if (model == null)
			{
				continue;
			}
			bytes += 4L * (length(model.getVerticesX()) + length(model.getVerticesY()) + length(model.getVerticesZ())
				+ length(model.getVertexNormalsX()) + length(model.getVertexNormalsY())
				+ length(model.getVertexNormalsZ()) + length(model.getFaceIndices1())
				+ length(model.getFaceIndices2()) + length(model.getFaceIndices3()) + length(model.getFaceColors1())
				+ length(model.getFaceColors2()) + length(model.getFaceColors3()) + length(model.getTexIndices1())
				+ length(model.getTexIndices2()) + length(model.getTexIndices3()));
			bytes += 2L * (length(model.getFaceTextures()) + length(model.getUnlitFaceColors()));
			bytes += length(model.getFaceTransparencies()) + length(model.getFaceRenderPriorities())
				+ length(model.getFaceBias()) + length(model.getTextureFaces());
		}
		return bytes / 1e6;
	}

	private static int length(int[] array)
	{
		return array == null ? 0 : array.length;
	}

	private static int length(float[] array)
	{
		return array == null ? 0 : array.length;
	}

	private static int length(short[] array)
	{
		return array == null ? 0 : array.length;
	}

	private static int length(byte[] array)
	{
		return array == null ? 0 : array.length;
	}

	private void line(String left, String right)
	{
		if (collapsed.contains(section))
		{
			return;
		}
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
	}
}
