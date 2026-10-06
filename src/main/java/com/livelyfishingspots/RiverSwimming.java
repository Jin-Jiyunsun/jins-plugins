package com.livelyfishingspots;

/**
 * How river fish swim down the river.
 */
public enum RiverSwimming
{
	SCHOOLED("Schooled"),
	RANDOM("Random");

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
