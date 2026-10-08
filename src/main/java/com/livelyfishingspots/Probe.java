package com.livelyfishingspots;

/**
 * Where the dev-only debug plugin, when it's loaded, times the parts of each client tick and each river or lake
 * mapped. In the plugin itself it's NONE, which does nothing.
 */
interface Probe
{
	Probe NONE = new Probe()
	{
	};

	// The parts timed, in the debug panel's order; river fish's own parts indented under it.
	String STARTING = "Starting rivers (game tick)";
	String MODELS = "Making models";
	String SEA = "Sea fish";
	String RIVERS = "River fish, all";
	String MAPPING = "  mapping, rings";
	String FILLING = "  filling gaps";
	String SPAWNING = "  spawning";
	String WINDOW = "  loaded stretch, remaps";
	String SCHOOLS = "  schools";
	String MOVING = "  moving fish";
	String SPOTS = "Spots appearing, going";

	/**
	 * Starts timing a part. Returns the time to time it from.
	 */
	default long start()
	{
		return 0;
	}

	/**
	 * Adds the time since started to a part. Returns the time to time the next part from.
	 */
	default long add(String part, long started)
	{
		return started;
	}

	/**
	 * A client tick is over.
	 */
	default void endTick()
	{
	}

	/**
	 * Something mapping took, ms: a river or lake started (with each step's time), mapped again, or a map load.
	 */
	default void mapped(String what, double ms, double[] steps)
	{
	}
}
