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
 * How long Trawling Plus's main overlay takes to draw, over the last WINDOW frames, as average / median in
 * milliseconds: the whole overlay, the route line within it, and the line split into building it (placing its points
 * and working out the fade near the boat) and stroking it (Java2D drawing the path).
 */
class TrawlingPlusDebugTimingOverlay extends OverlayPanel implements TrawlingPlusOverlay.DrawTimer
{
	// About five seconds at 120 frames a second.
	private static final int WINDOW = 600;

	private final TrawlingPlusDebugConfig config;
	private final long[] total = new long[WINDOW];
	private final long[] line = new long[WINDOW];
	private final long[] stroke = new long[WINDOW];
	private int count;
	private int next;

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

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showRenderTiming() || count == 0)
		{
			return null;
		}

		long[] build = new long[count];
		for (int i = 0; i < count; i++)
		{
			build[i] = line[i] - stroke[i];
		}

		panelComponent.getChildren().add(TitleComponent.builder().text("Trawling Plus draw").build());
		row("Frames", Integer.toString(count));
		row("", "avg / median");
		row("Overlay", times(total));
		row("Route line", times(line));
		row("- build", times(build));
		row("- stroke", times(stroke));
		long[] sorted = sorted(total);
		row("Overlay 1%", millis(sorted[(int) (count * 0.99)]));
		return super.render(graphics);
	}

	private String times(long[] samples)
	{
		long[] sorted = sorted(samples);
		long sum = 0;
		for (long sample : sorted)
		{
			sum += sample;
		}
		return millis((double) sum / count) + " / " + millis(sorted[count / 2]);
	}

	private long[] sorted(long[] samples)
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
