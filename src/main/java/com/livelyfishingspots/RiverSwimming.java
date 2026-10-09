package com.livelyfishingspots;

/**
 * How river fish swim down the river, or only at the spot being fished.
 */
public enum RiverSwimming
{
	SCHOOLED("Schooled"),
	RANDOM("Random"),
	ONLY_AT_SPOT("Only at my spot");

	private final String name;

	RiverSwimming(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
