package com.livelyfishingspots;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY: how long each part of the plugin takes a client tick, on average and at worst over each second, for the
 * debug panel. Client thread only.
 */
final class TickTimes
{
	// Client ticks per report: a second.
	private static final int TICKS = 50;
	// Per part: nanoseconds this tick, the total and the worst tick this second.
	private static final Map<String, long[]> PARTS = new LinkedHashMap<>();
	private static List<String[]> rows = List.of();
	private static int ticks;

	private TickTimes()
	{
	}

	/**
	 * Adds the time since started to a part, this tick. Returns now, to start the next part from.
	 */
	static long add(String part, long started)
	{
		long now = System.nanoTime();
		PARTS.computeIfAbsent(part, p -> new long[3])[0] += now - started;
		return now;
	}

	/**
	 * Closes the tick, and once a second makes the report: each part's average and worst tick, ms.
	 */
	static void endTick()
	{
		for (long[] times : PARTS.values())
		{
			times[1] += times[0];
			times[2] = Math.max(times[2], times[0]);
			times[0] = 0;
		}
		if (++ticks < TICKS)
		{
			return;
		}
		List<String[]> report = new ArrayList<>();
		for (Map.Entry<String, long[]> part : PARTS.entrySet())
		{
			long[] times = part.getValue();
			report.add(new String[]{part.getKey(), String.format("%.2f / %.2f", times[1] / 1e6 / ticks, times[2] / 1e6)});
			times[1] = 0;
			times[2] = 0;
		}
		rows = report;
		ticks = 0;
	}

	/**
	 * The last report: each part's name and "average / worst" ms a tick.
	 */
	static List<String[]> rows()
	{
		return rows;
	}

	static void clear()
	{
		PARTS.clear();
		rows = List.of();
		ticks = 0;
	}
}
