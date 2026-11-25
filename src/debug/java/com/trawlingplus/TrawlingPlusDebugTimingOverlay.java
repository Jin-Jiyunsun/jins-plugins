package com.trawlingplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * How long Trawling Plus draws over the last WINDOW frames, as average / median milliseconds: the main overlay, its
 * route line split into building and stroking, the route lines and stops on the minimap and world map, and the trawling log.
 */
class TrawlingPlusDebugTimingOverlay extends OverlayPanel
	implements TrawlingPlusOverlay.DrawTimer, MapRoutes.MapTimer, HoldBook.BookTimer
{
	// About five seconds at 120 frames a second.
	private static final int WINDOW = 600;

	private final TrawlingPlusDebugConfig config;
	private final long[] total = new long[WINDOW];
	private final long[] line = new long[WINDOW];
	private final long[] stroke = new long[WINDOW];
	private int count;
	private int next;
	// The maps' route drawing by part, as MapTimer numbers them.
	private static final String[] MAP_PARTS = {"Minimap lines", "World map lines", "World map arrows",
		"Minimap stops", "World map stops"};
	private final long[][] maps = new long[MAP_PARTS.length][WINDOW];
	private final int[] mapCounts = new int[MAP_PARTS.length];
	private final int[] mapNext = new int[MAP_PARTS.length];
	// The trawling log's work by part, as BookTimer numbers them; builds are rare, so they also keep a running count.
	private final long[][] book = new long[3][WINDOW];
	private final int[] bookCounts = new int[3];
	private final int[] bookNext = new int[3];
	private final int[] bookTotals = new int[3];

	@Inject
	TrawlingPlusDebugTimingOverlay(TrawlingPlusDebugConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/**
	 * Takes one frame's timings. Called on the client thread, as the overlay draws.
	 */
	@Override
	public void took(long totalNanos, long lineNanos, long strokeNanos)
	{
		total[next] = totalNanos;
		line[next] = lineNanos;
		stroke[next] = strokeNanos;
		next = (next + 1) % WINDOW;
		count = Math.min(count + 1, WINDOW);
	}

	/**
	 * Takes how long one part of the maps' route drawing took. Called on the client thread, as the map draws.
	 */
	@Override
	public void mapTook(int part, long nanos)
	{
		maps[part][mapNext[part]] = nanos;
		mapNext[part] = (mapNext[part] + 1) % WINDOW;
		mapCounts[part] = Math.min(mapCounts[part] + 1, WINDOW);
	}

	/**
	 * Takes how long one part of the trawling log's work took. Called on the client thread, as the log draws.
	 */
	@Override
	public void bookTook(int part, long nanos)
	{
		book[part][bookNext[part]] = nanos;
		bookNext[part] = (bookNext[part] + 1) % WINDOW;
		bookCounts[part] = Math.min(bookCounts[part] + 1, WINDOW);
		bookTotals[part]++;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean anyMap = bookCounts[HoldBook.BookTimer.DRAW] > 0;
		for (int mapCount : mapCounts)
		{
			anyMap |= mapCount > 0;
		}
		if (!config.showRenderTiming() || count == 0 && !anyMap)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text("Trawling Plus draw").build());
		row("Frames", Integer.toString(count));
		row("", "avg / median");
		if (count > 0)
		{
			long[] build = new long[count];
			for (int i = 0; i < count; i++)
			{
				build[i] = line[i] - stroke[i];
			}
			row("Overlay", times(total, count));
			row("Route line", times(line, count));
			row("- build", times(build, count));
			row("- stroke", times(stroke, count));
			long[] sorted = sorted(total, count);
			row("Overlay 1%", millis(sorted[(int) (count * 0.99)]));
		}
		for (int part = 0; part < MAP_PARTS.length; part++)
		{
			if (mapCounts[part] > 0)
			{
				row(MAP_PARTS[part], times(maps[part], mapCounts[part]));
			}
		}
		int drawn = bookCounts[HoldBook.BookTimer.DRAW];
		if (drawn > 0)
		{
			row("Log draw", times(book[HoldBook.BookTimer.DRAW], drawn));
			row("Log draw max", millis(sorted(book[HoldBook.BookTimer.DRAW], drawn)[drawn - 1]));
		}
		buildRow("Log page build", HoldBook.BookTimer.PAGE);
		buildRow("Log sprites build", HoldBook.BookTimer.SPRITES);
		return super.render(graphics);
	}

	/** A build's average / slowest over its last builds, and how many there have been. */
	private void buildRow(String name, int part)
	{
		int built = bookCounts[part];
		if (built == 0)
		{
			return;
		}
		long[] sorted = sorted(book[part], built);
		long sum = 0;
		for (long sample : sorted)
		{
			sum += sample;
		}
		row(name + " (" + bookTotals[part] + ")", millis((double) sum / built) + " / " + millis(sorted[built - 1]) + " max");
	}

	private static String times(long[] samples, int count)
	{
		long[] sorted = sorted(samples, count);
		long sum = 0;
		for (long sample : sorted)
		{
			sum += sample;
		}
		return millis((double) sum / count) + " / " + millis(sorted[count / 2]);
	}

	private static long[] sorted(long[] samples, int count)
	{
		long[] sorted = Arrays.copyOf(samples, count);
		Arrays.sort(sorted);
		return sorted;
	}

	private void row(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
	}

	private static String millis(double nanos)
	{
		return String.format("%.3f", nanos / 1e6);
	}
}
