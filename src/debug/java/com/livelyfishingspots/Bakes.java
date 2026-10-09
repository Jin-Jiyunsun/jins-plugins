package com.livelyfishingspots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import lombok.extern.slf4j.Slf4j;
import static com.livelyfishingspots.RiverSpotFish.*;

/**
 * Debug: editing baked rivers and lakes: their text as saved, their route and spawn points, marks and forks; and
 * reloading a bake while playing.
 */
@Slf4j
final class Bakes
{
	// Longest line in a baked file, characters, so a river takes few lines.
	private static final int BAKED_WIDTH = 150;

	private Bakes()
	{
	}

	/**
	 * A baked river or lake as text: its head line (see headLine), then "fish N" if it has N% of the fish its size would
	 * give, then "kind K" if its fish aren't lure/bait ones (barbarian: leaping fish), then "water X Y" gives the first
	 * row's world cell (y) and the cell the rows count from (x); the lines after hold the rows, going north, split by commas: each row's water as gap, length, gap,
	 * length..., each gap from the end of the run before (the first from X); "N*" in front repeats the row N times, and
	 * "-" is a row with none. "path X Y" gives the first path point (world local units), and each "step" line the steps
	 * on to the next points. Each "block X Y PLANE HALF" line is a fish blocker: a world tile, or half of it, counted
	 * as land; each "allow" line likewise a fish allower, counted as water; each "surface" line a fish surfacer, where
	 * deep fish come up; each "dive" line a fish diver, where they go down. Each "branch X Y X Y ..." line is a side
	 * channel's picked tiles, first and last on the river, followed by its laid path as "bpath" and "bstep" lines.
	 */
	static String bakedText(String head, int fishShare, String fishKind, int x0, int y0, List<int[]> rows, List<int[]> path,
		List<Map<WorldPoint, String>> marks, List<int[]> branchStops, List<int[]> branchPaths, List<Integer> branchShares)
	{
		StringBuilder text = new StringBuilder(head).append('\n');
		if (fishShare != 100)
		{
			text.append("fish ").append(fishShare).append('\n');
		}
		if (!fishKind.equals("lure"))
		{
			text.append("kind ").append(fishKind).append('\n');
		}
		text.append("water ").append(x0).append(' ').append(y0).append('\n');
		StringBuilder line = new StringBuilder();
		for (int j = 0; j < rows.size(); j++)
		{
			int[] row = rows.get(j);
			int repeats = 1;
			while (j + repeats < rows.size() && Arrays.equals(rows.get(j + repeats), row))
			{
				repeats++;
			}
			StringBuilder piece = new StringBuilder(repeats > 1 ? repeats + "*" : "");
			int last = x0;
			for (int k = 0; k + 1 < row.length; k += 2)
			{
				piece.append(piece.length() > 0 ? " " : "").append(row[k] - last).append(' ').append(row[k + 1] - row[k]);
				last = row[k + 1];
			}
			if (row.length == 0)
			{
				piece.append(piece.length() > 0 ? " -" : "-");
			}
			if (line.length() > 0 && line.length() + piece.length() + 2 > BAKED_WIDTH)
			{
				text.append(line).append('\n');
				line.setLength(0);
			}
			line.append(line.length() > 0 ? ", " : "").append(piece);
			j += repeats - 1;
		}
		text.append(line).append('\n');
		if (path.size() >= 2)
		{
			text.append("path ").append(path.get(0)[0]).append(' ').append(path.get(0)[1]).append('\n');
			line = new StringBuilder("step");
			for (int k = 1; k < path.size(); k++)
			{
				String piece = " " + (path.get(k)[0] - path.get(k - 1)[0]) + " " + (path.get(k)[1] - path.get(k - 1)[1]);
				if (line.length() + piece.length() > BAKED_WIDTH)
				{
					text.append(line).append('\n');
					line = new StringBuilder("step");
				}
				line.append(piece);
			}
			text.append(line).append('\n');
		}
		for (int b = 0; b < branchStops.size(); b++)
		{
			text.append("branch");
			for (int stop : branchStops.get(b))
			{
				text.append(' ').append(stop);
			}
			text.append('\n');
			if (b < branchShares.size() && branchShares.get(b) >= 0)
			{
				text.append("bshare ").append(branchShares.get(b)).append('\n');
			}
			int[] points = branchPaths.get(b);
			if (points.length < 4)
			{
				continue;
			}
			text.append("bpath ").append(points[0]).append(' ').append(points[1]).append('\n');
			StringBuilder steps = new StringBuilder("bstep");
			for (int k = 2; k + 1 < points.length; k += 2)
			{
				String piece = " " + (points[k] - points[k - 2]) + " " + (points[k + 1] - points[k - 1]);
				if (steps.length() + piece.length() > BAKED_WIDTH)
				{
					text.append(steps).append('\n');
					steps = new StringBuilder("bstep");
				}
				steps.append(piece);
			}
			text.append(steps).append('\n');
		}
		for (int kind = 0; kind < MARK_WORDS.length; kind++)
		{
			for (Map.Entry<WorldPoint, String> mark : marks.get(kind).entrySet())
			{
				WorldPoint at = mark.getKey();
				text.append(MARK_WORDS[kind]).append(' ').append(at.getX()).append(' ').append(at.getY()).append(' ')
					.append(at.getPlane()).append(' ').append(mark.getValue()).append('\n');
			}
		}
		return text.toString();
	}

	/**
	 * A baked river or lake's water as each world tile's cell bits (key x << 14 | y, a bit per cell, row by
	 * row), for baking it again; empty if it has no bake.
	 */
	static Map<Integer, Integer> bakedTiles(RiverSpotFish rivers, WorldPoint first)
	{
		Map<Integer, Integer> tiles = new HashMap<>();
		Baked saved = rivers.baked.get(first);
		if (saved == null)
		{
			return tiles;
		}
		for (int j = 0; j < saved.rows.length; j++)
		{
			int cellY = saved.y0 + j;
			int[] runs = saved.rows[j];
			for (int k = 0; k + 1 < runs.length; k += 2)
			{
				for (int cellX = runs[k]; cellX < runs[k + 1]; cellX++)
				{
					int key = Math.floorDiv(cellX, PER_TILE) << 14 | Math.floorDiv(cellY, PER_TILE);
					tiles.merge(key, 1 << (Math.floorMod(cellY, PER_TILE) * PER_TILE + Math.floorMod(cellX, PER_TILE)),
						(a, b) -> a | b);
				}
			}
		}
		return tiles;
	}

	/**
	 * A river or lake's fish blockers, allowers or surfacers (kind 0, 1 or 2; tile, half), empty if it has
	 * no bake.
	 */
	static Map<WorldPoint, String> bakedMarks(RiverSpotFish rivers, WorldPoint first, int kind)
	{
		Baked saved = rivers.baked.get(first);
		return saved == null ? Map.of() : saved.marks(kind);
	}

	/**
	 * A river or lake's marks of every kind (MARK_WORDS order), empty if it has no bake.
	 */
	static List<Map<WorldPoint, String>> bakedMarks(RiverSpotFish rivers, WorldPoint first)
	{
		List<Map<WorldPoint, String>> marks = new ArrayList<>();
		for (int kind = 0; kind < MARK_WORDS.length; kind++)
		{
			marks.add(bakedMarks(rivers, first, kind));
		}
		return marks;
	}

	/**
	 * Whether a baked river has a side channel whose path isn't laid yet.
	 */
	static boolean hasUnlaidBranch(RiverSpotFish rivers, WorldPoint first)
	{
		Baked saved = rivers.baked.get(first);
		return saved != null && saved.branchPaths.stream().anyMatch(points -> points.length < 4);
	}

	/**
	 * A baked river's side channel's share of fish, percent, or -1 for by its width; returns the bake's new
	 * text, or null if it has none.
	 */
	static String setBranchShare(RiverSpotFish rivers, WorldPoint first, int index, int share)
	{
		Baked saved = rivers.baked.get(first);
		if (saved == null || index >= saved.branchShares.size())
		{
			return null;
		}
		saved.branchShares.set(index, share);
		return textOf(saved);
	}

	/**
	 * A baked river's side channels' shares of fish, percent or -1, empty if it has no bake.
	 */
	static List<Integer> bakedBranchShares(RiverSpotFish rivers, WorldPoint first)
	{
		Baked saved = rivers.baked.get(first);
		return saved == null ? List.of() : saved.branchShares;
	}

	/**
	 * Removes a baked river's side channel, by its place in the list.
	 */
	static void removeBranch(RiverSpotFish rivers, WorldPoint first, int index)
	{
		Baked saved = rivers.baked.get(first);
		if (saved != null && index < saved.branchStops.size())
		{
			saved.branchStops.remove(index);
			saved.branchPaths.remove(index);
			saved.branchShares.remove(index);
		}
	}

	/**
	 * Adds a side channel's picked tiles (world x, y, x, y, ...) to a baked river; false if it has no bake.
	 */
	static boolean addBranch(RiverSpotFish rivers, WorldPoint first, int[] stops)
	{
		Baked saved = rivers.baked.get(first);
		if (saved == null)
		{
			return false;
		}
		saved.branchStops.add(stops);
		saved.branchPaths.add(new int[0]);
		saved.branchShares.add(-1);
		return true;
	}

	/**
	 * Places a fish blocker, allower or surfacer (kind 0, 1 or 2; a half, or WHOLE) on a tile of a baked
	 * river or lake, or with null clears it; a blocker and an allower replace each other, a surfacer goes with either.
	 * Returns the bake's new text to save, or null if it has no bake.
	 */
	static String setMark(RiverSpotFish rivers, WorldPoint first, WorldPoint tile, String half, int kind)
	{
		Baked saved = rivers.baked.get(first);
		if (saved == null)
		{
			return null;
		}
		// Blockers and allowers replace each other, as do surfacers and divers; hiding spots go with any.
		if (kind < 4)
		{
			saved.marks(kind / 2 * 2).remove(tile);
			saved.marks(kind / 2 * 2 + 1).remove(tile);
		}
		saved.marks(kind).remove(tile);
		if (half != null)
		{
			saved.marks(kind).put(tile, half);
		}
		return textOf(saved);
	}

	/**
	 * A baked river or lake as its file's text.
	 */
	static String textOf(Baked saved)
	{
		int x0 = Integer.MAX_VALUE;
		List<int[]> rows = new ArrayList<>();
		for (int[] row : saved.rows)
		{
			x0 = row.length > 0 ? Math.min(x0, row[0]) : x0;
			rows.add(row);
		}
		List<int[]> path = new ArrayList<>();
		for (int k = 0; saved.pathX != null && k < saved.pathX.length; k++)
		{
			path.add(new int[]{(int) Math.round(saved.pathX[k]), (int) Math.round(saved.pathY[k])});
		}
		return bakedText(headLine(saved.route, saved.lake), saved.fishShare, saved.kind, x0, saved.y0, rows, path, saved.marks,
			saved.branchStops, saved.branchPaths, saved.branchShares);
	}

	/**
	 * Takes a bake file's new text (one changed in .runelite), and maps every river and lake again with
	 * it, keeping the fish.
	 */
	static void reloadBaked(RiverSpotFish rivers, String fileName, String text)
	{
		List<WorldPoint> firsts = rivers.addBaked(fileName.replace(".txt", ""), text);
		rivers.unmappable.removeIf(route -> firsts.contains(route[0]));
		log.debug("Reloaded the bake {}", fileName);
		remapAll(rivers);
	}

	/**
	 * A bake file's text: every river and lake in it (rivers first, then lakes, each by its first point),
	 * those given by first point as their new text, the rest as they are.
	 */
	static String fileText(RiverSpotFish rivers, String fileName, Map<WorldPoint, String> texts)
	{
		String name = fileName.replace(".txt", "");
		List<WorldPoint> firsts = new ArrayList<>(texts.keySet());
		NAMES.forEach((first, of) ->
		{
			if (of.equals(name) && !firsts.contains(first) && rivers.baked.containsKey(first))
			{
				firsts.add(first);
			}
		});
		Map<WorldPoint, String> all = new HashMap<>(texts);
		firsts.forEach(first -> all.computeIfAbsent(first, f -> textOf(rivers.baked.get(f))));
		firsts.sort((a, b) -> all.get(a).startsWith("lake") != all.get(b).startsWith("lake")
			? (all.get(a).startsWith("lake") ? 1 : -1)
			: a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX()) : Integer.compare(a.getY(), b.getY()));
		StringBuilder file = new StringBuilder();
		firsts.forEach(first -> file.append(all.get(first)));
		return file.toString();
	}

	/**
	 * A lake's spawn points changed, as a pick adds one; returns its bake's new text, or null if it has none.
	 */
	static String setLakeSpawns(RiverSpotFish rivers, WorldPoint first, WorldPoint[] spawns)
	{
		Baked saved = rivers.baked.get(first);
		if (saved == null)
		{
			return null;
		}
		saved.route = spawns;
		int index = rivers.lakes.indexOf(rivers.baked.get(first) == saved ? routeOf(rivers, first) : null);
		if (index >= 0)
		{
			rivers.lakes.set(index, spawns);
		}
		return textOf(saved);
	}

	/**
	 * A river's route, or a lake's spawn points, changed: points added, moved or removed; returns its bake's
	 * new text, or null if it has none. Its name stays, though its first point may change.
	 */
	static String setRoute(RiverSpotFish rivers, WorldPoint[] route, WorldPoint[] changed)
	{
		Baked saved = rivers.baked.remove(route[0]);
		if (saved == null)
		{
			return null;
		}
		String name = NAMES.remove(route[0]);
		saved.route = changed;
		rivers.baked.put(changed[0], saved);
		NAMES.put(changed[0], name);
		List<WorldPoint[]> list = rivers.lakes.contains(route) ? rivers.lakes : rivers.routes;
		int index = list.indexOf(route);
		if (index >= 0)
		{
			list.set(index, changed);
		}
		return textOf(saved);
	}

	/**
	 * Maps every river and lake again, keeping the fish, after a bake or its blockers change.
	 */
	static void remapAll(RiverSpotFish rivers)
	{
		rivers.ringPlaces.clear();
		WorldView view = rivers.client.getTopLevelWorldView();
		for (Shoal shoal : rivers.shoals)
		{
			if (view != null)
			{
				rivers.remap(view, shoal);
			}
		}
	}

	/**
	 * A baked river or lake's file name.
	 */
	static String bakedName(WorldPoint first)
	{
		String name = NAMES.get(first);
		return (name != null ? name : "river-" + first.getX() + "-" + first.getY()) + ".txt";
	}

	/**
	 * A bake's first line: "route PLANE X Y X Y ..." for a river, its route's tiles, or "lake ..." for a lake,
	 * its spawn points.
	 */
	static String headLine(WorldPoint[] route, boolean lake)
	{
		StringBuilder line = new StringBuilder(lake ? "lake " : "route ").append(route[0].getPlane());
		for (WorldPoint point : route)
		{
			line.append(' ').append(point.getX()).append(' ').append(point.getY());
		}
		return line.toString();
	}

	/**
	 * The river or lake whose first point this is, or null.
	 */
	static WorldPoint[] routeOf(RiverSpotFish rivers, WorldPoint first)
	{
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (route[0].equals(first))
			{
				return route;
			}
		}
		return null;
	}
}
