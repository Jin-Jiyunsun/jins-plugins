package com.trawlingplus;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the sea creatures that attack boats spawn, taken from the OSRS wiki: every spawn point, and the
 * spawn points grouped by the place they spawn. Used to mark dangerous water on the world map.
 */
final class SeaMonsters
{
	private static final String RESOURCE = "sea_monsters.json";

	// Before anything has been read.
	static final SeaMonsters NONE = new SeaMonsters(new int[0][], new int[0][][], new String[0]);

	private final int[][] spawns;
	private final int[][][] areaSpawns;
	private final String[] areaNames;

	private SeaMonsters(int[][] spawns, int[][][] areaSpawns, String[] areaNames)
	{
		this.spawns = spawns;
		this.areaSpawns = areaSpawns;
		this.areaNames = areaNames;
	}

	static SeaMonsters read(Gson gson) throws IOException
	{
		try (InputStream in = SeaMonsters.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing " + RESOURCE);
			}

			Data data = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class);
			List<int[]> points = new ArrayList<>();
			List<int[][]> areas = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (Creature creature : data.creatures)
			{
				for (Location location : creature.locations)
				{
					if (location.points.length == 0)
					{
						continue;
					}
					areas.add(location.points);
					names.add(creature.name + " (level-" + creature.combat + ")");
					for (int[] point : location.points)
					{
						points.add(point);
					}
				}
			}
			return new SeaMonsters(points.toArray(new int[0][]), areas.toArray(new int[0][][]),
				names.toArray(new String[0]));
		}
	}

	/**
	 * Every spawn point as [x, y] world tiles on plane 0, whichever creature and place it belongs to.
	 */
	int[][] spawns()
	{
		return spawns;
	}

	/**
	 * The spawn points of each place they spawn, one group for each creature at each place.
	 */
	int[][][] areaSpawns()
	{
		return areaSpawns;
	}

	/**
	 * The creature at each place they spawn, as the game names it with its level, such as
	 * "Tiger shark (level-125)", in the same order as areaSpawns.
	 */
	String[] areaNames()
	{
		return areaNames;
	}

	/**
	 * The shape of sea_monsters.json: each creature's name and level, and its spawn points place by place.
	 */
	static class Data
	{
		List<Creature> creatures;
	}

	static class Creature
	{
		String name;
		int combat;
		List<Location> locations;
	}

	static class Location
	{
		int[][] points;
	}
}
