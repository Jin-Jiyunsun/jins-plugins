package com.livelyfishingspots;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug: how long each part of the plugin takes a client tick, on average and at worst over each second, and how much
 * memory it allocates (garbage for Java to collect later); and the latest mapping times; for the debug panel. Fed by
 * the plugin's Probe. Client thread only.
 */
final class TickTimes
{
	// Client ticks per report: a second.
	private static final int TICKS = 50;
	// The mapping steps' names, then a new one's last: filling it with fish.
	private static final String[] STEP_NAMES = {"box", "water", "banks", "path", "fish"};
	// The parts, in the panel's order; river fish's own parts indented under it.
	// The debug plugin's own parts, after the plugin's.
	static final String DRAWING = "Debug drawing";
	static final String BAKING = "Baking, reading water (game tick)";
	private static final List<String> ORDER = List.of(Probe.STARTING, Probe.MODELS, Probe.SEA, Probe.RIVERS,
		Probe.MAPPING, Probe.FILLING, Probe.SPAWNING, Probe.WINDOW, Probe.SCHOOLS, Probe.MOVING, Probe.SPOTS, DRAWING,
		BAKING);
	// The latest mapping times (what, how long), newest first, and the slowest so far; and the last river or lake
	// started, each step's time (ms).
	private static final int TIMINGS_SHOWN = 6;
	private static final Deque<String[]> TIMINGS = new ArrayDeque<>();
	private static double slowest;
	private static String slowestWhat;
	private static String lastLoad;
	private static double[] lastLoadSteps;
	// The plugin's timing hooks, into these.
	static final Probe PROBE = new Probe()
	{
		@Override
		public long start()
		{
			return TickTimes.start();
		}

		@Override
		public long add(String part, long started)
		{
			return TickTimes.add(part, started);
		}

		@Override
		public void endTick()
		{
			TickTimes.endTick();
		}

		@Override
		public void mapped(String what, double ms, double[] steps)
		{
			TickTimes.mapped(what, ms, steps);
		}
	};
	// Per part: nanoseconds this tick, the total and the worst tick this second, and bytes allocated this second.
	private static final Map<String, long[]> PARTS = new LinkedHashMap<>();
	// The client thread's bytes allocated so far, where it's measured (-1 if it can't be).
	private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
	private static long allocatedAt = -1;
	private static List<String[]> rows = List.of();
	private static int ticks;
	// Java's garbage collections and their time (ms) as of the last report, and over the second before it.
	private static long collections;
	private static long collectionMs;
	private static String collected = "";

	static
	{
		clear();
	}

	private TickTimes()
	{
	}

	/**
	 * Adds the time since started to a part, this tick. Returns now, to start the next part from.
	 */
	static long add(String part, long started)
	{
		long now = System.nanoTime();
		long[] times = PARTS.computeIfAbsent(part, p -> new long[4]);
		times[0] += now - started;
		// What it allocated since the last part was added; the first after a gap counts nothing.
		long allocated = allocated();
		if (allocatedAt >= 0 && allocated >= allocatedAt && now - started < 1_000_000_000L)
		{
			times[3] += allocated - allocatedAt;
		}
		allocatedAt = allocated;
		return now;
	}

	/**
	 * Marks the start of a part's allocations, before its first add.
	 */
	static long start()
	{
		allocatedAt = allocated();
		return System.nanoTime();
	}

	private static long allocated()
	{
		return THREADS instanceof com.sun.management.ThreadMXBean
			? ((com.sun.management.ThreadMXBean) THREADS).getThreadAllocatedBytes(Thread.currentThread().getId()) : -1;
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
		// River fish's own parts' allocations count towards it too.
		for (Map.Entry<String, long[]> part : PARTS.entrySet())
		{
			if (part.getKey().startsWith("  "))
			{
				PARTS.get(Probe.RIVERS)[3] += part.getValue()[3];
			}
		}
		List<String[]> report = new ArrayList<>();
		for (Map.Entry<String, long[]> part : PARTS.entrySet())
		{
			long[] times = part.getValue();
			report.add(new String[]{part.getKey(), String.format("%.2f / %.2f", times[1] / 1e6 / ticks, times[2] / 1e6),
				String.valueOf(times[3] >> 10)});
			times[1] = 0;
			times[2] = 0;
			times[3] = 0;
		}
		rows = report;
		ticks = 0;
		// Java's garbage collections, all of RuneLite's: a pause can show as a spike in any part.
		long count = 0;
		long ms = 0;
		for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans())
		{
			count += Math.max(0, collector.getCollectionCount());
			ms += Math.max(0, collector.getCollectionTime());
		}
		collected = (count - collections) + ", " + (ms - collectionMs) + " ms";
		collections = count;
		collectionMs = ms;
	}

	/**
	 * Java's garbage collections over the last second: how many, and their time.
	 */
	static String collected()
	{
		return collected;
	}

	/**
	 * The last report: each part's name, "average / worst" ms a tick, and KB allocated over the second.
	 */
	static List<String[]> rows()
	{
		return rows;
	}

	static void clear()
	{
		PARTS.clear();
		for (String part : ORDER)
		{
			PARTS.put(part, new long[4]);
		}
		rows = List.of();
		ticks = 0;
	}

	/**
	 * Notes something mapping took: a river or lake started (with each step's time), or mapped again, or a map load.
	 */
	static void mapped(String what, double ms, double[] steps)
	{
		double rounded = Math.round(ms * 10) / 10.0;
		if (steps != null)
		{
			lastLoad = what.replace(" start", "");
			lastLoadSteps = steps;
			int worst = 0;
			for (int k = 0; k < steps.length; k++)
			{
				worst = steps[k] > steps[worst] ? k : worst;
			}
			what += ", worst step: " + STEP_NAMES[worst];
			rounded = Math.round(steps[worst] * 10) / 10.0;
		}
		TIMINGS.addFirst(new String[]{what, rounded + " ms"});
		while (TIMINGS.size() > TIMINGS_SHOWN)
		{
			TIMINGS.removeLast();
		}
		if (rounded > slowest)
		{
			slowest = rounded;
			slowestWhat = what;
		}
	}

	/**
	 * The last river or lake started: a heading (its name, all steps' ms), then each step; empty if none yet.
	 */
	static List<String[]> lastLoad()
	{
		List<String[]> rows = new ArrayList<>();
		if (lastLoad == null)
		{
			return rows;
		}
		double[] steps = lastLoadSteps;
		rows.add(new String[]{"Last load: " + lastLoad, String.format("%.2f", Arrays.stream(steps).sum())});
		for (int k = 0; k < steps.length; k++)
		{
			rows.add(new String[]{"  " + STEP_NAMES[k], String.format("%.2f", steps[k])});
		}
		rows.add(new String[]{"  over client ticks", String.valueOf(steps.length)});
		return rows;
	}

	/**
	 * The latest mapping times, newest first, then the slowest so far.
	 */
	static List<String[]> timings()
	{
		List<String[]> rows = new ArrayList<>(TIMINGS);
		if (slowestWhat != null)
		{
			rows.add(new String[]{"Worst: " + slowestWhat, slowest + " ms"});
		}
		return rows;
	}
}
