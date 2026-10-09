package com.livelyfishingspots;

import com.livelyfishingspots.RiverSpotFish.River;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Debug (Bake water bodies): records the water of each river and lake from the scene as the player walks, and
 * turns it into baked files: the water joined up from its first point, and a river's path down its middle. The game
 * itself only uses baked rivers and lakes; this is how they're made.
 */
@Slf4j
class RiverBaker
{
	private static final int CELL = RiverSpotFish.CELL;
	private static final int PER_TILE = RiverSpotFish.PER_TILE;
	// Tiles round a route's line for picking: a point added, a lake or fork joining it.
	static final int BOX_MARGIN = 8;
	// A diagonal step between cells.
	private static final double DIAGONAL = CELL * Math.sqrt(2);
	// Floor textures of river water, seen in game: plain water, and Tirannwn's crystal water (WATER_CRYSTAL_OPEN,
	// 170, to its deepest, 174).
	private static final Set<Integer> WATER_TEXTURES = Set.of(1, 170, 171, 172, 173, 174);
	// Floor overlays of untextured water, seen in game: plain water's (6, untextured in places along Kourend's
	// rivers), the Water Ravine Dungeon's (130) and the cave's round 1303, 9799 (41).
	private static final Set<Integer> WATER_OVERLAYS = Set.of(6, 41, 130);
	// How far inside an object's rough outline a cell's middle must be to count as blocked, local units.
	private static final double OBJECT_INSET = 16;
	// Tiles from a river's laid path (or a fork's) its water is saved within: its whole width round any bend, but not
	// side water further off (other rivers' arms, inlets, the sea).
	private static final int PATH_KEEP = 10;
	// How near water a route point or fork point must be to count as recorded, local units (two tiles).
	private static final int POINT_REACH = 256;
	// Smoothing passes over the path.
	private static final int SMOOTHING = 4;
	// Tiles walked before the water round the player is read again.
	private static final int RECORD_MOVE = 2;
	// Objects in the water that block movement but not fish: invisible blockers keeping ducks to a pond, waterfall
	// foam, and the parts of water wheels in the water.
	private static final Set<Integer> NOT_BLOCKING = Set.of(ObjectID.DUCKBLOCKER, ObjectID.WATERFALL_FOAM,
		ObjectID.WATERWHEEL_CENTRE, ObjectID.WATERWHEEL_LEFT, ObjectID.WATERWHEEL_RIGHT, ObjectID.WATERWHEEL_DRIP,
		ObjectID.KASTORI_WATERWHEEL_CENTRE, ObjectID.KASTORI_WATERWHEEL_LEFT, ObjectID.KASTORI_WATERWHEEL_RIGHT);

	/**
	 * How much of a tile a fish blocker covers.
	 */
	enum Half
	{
		WHOLE,
		NORTH,
		SOUTH,
		EAST,
		WEST
	}

	private final Client client;
	private final RiverSpotFish rivers;
	// Water recorded, per river or lake (by its first point, which stays as its points change), per world tile
	// (x << 14 | y): a bit per cell, row by row.
	private final Map<WorldPoint, Map<Integer, Integer>> recorded = new LinkedHashMap<>();
	// All water walked past while baking, per plane, per world tile as above, so a river or lake picked afterwards
	// still gets it.
	private final Map<Integer, Map<Integer, Integer>> walked = new HashMap<>();
	// The picking menu's mark entries: what each would place or remove (tile, half, kind), shown while hovered. By the
	// entry itself, as entries with the same text (each kind's "West half") count as equal.
	private final Map<MenuEntry, Object[]> previews = new IdentityHashMap<>();
	// The picking menu's entry with a submenu the mouse was last on: the game opens that one's submenu, and the
	// others keep where they were last shown, so only its submenu is looked in.
	private MenuEntry openParent;
	// TEMPORARY: each floor texture's cells in the last read, for the log.
	private final Map<Integer, Integer> textures = new TreeMap<>();
	// TEMPORARY: each floor overlay's untextured tiles in the last read, for the log.
	private final Map<Integer, Integer> overlays = new TreeMap<>();
	// Rivers and lakes (by first point) whose recorded water changed since they were last baked.
	private final Set<WorldPoint> changed = new HashSet<>();
	// Where the player was, and the scene's corner, when the water was last read.
	private WorldPoint readAt;
	private int readBaseX;
	private int readBaseY;

	// Writes bake files (name, text) to .runelite, off the client thread.
	private final Consumer<Map<String, String>> saveFiles;

	RiverBaker(Client client, RiverSpotFish rivers, Consumer<Map<String, String>> saveFiles)
	{
		this.client = client;
		this.rivers = rivers;
		this.saveFiles = saveFiles;
	}

	/**
	 * Once a game tick while baking: after a couple of tiles walked or a map load, reads the water of the tiles round
	 * the player (the square rivers are mapped within), and records it for each river or lake it's near, and as walked;
	 * a tile read again keeps its latest reading.
	 */
	void record()
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (player == null || view == null || view.getScene() == null || player.getLocalLocation() == null)
		{
			return;
		}
		WorldPoint me = player.getWorldLocation();
		if (readAt != null && me.distanceTo2D(readAt) < RECORD_MOVE && view.getBaseX() == readBaseX
			&& view.getBaseY() == readBaseY)
		{
			return;
		}
		readAt = me;
		readBaseX = view.getBaseX();
		readBaseY = view.getBaseY();
		LocalPoint at = player.getLocalLocation();
		int reach = RiverSpotFish.loadRange() + RiverSpotFish.MAP_MORE;
		int margin = RiverSpotFish.ROUTE_MARGIN;
		int lowX = Math.max(margin, at.getSceneX() - reach);
		int lowY = Math.max(margin, at.getSceneY() - reach);
		int highX = Math.min(view.getSizeX() - 1 - margin, at.getSceneX() + reach);
		int highY = Math.min(view.getSizeY() - 1 - margin, at.getSceneY() + reach);
		if (lowX > highX || lowY > highY)
		{
			return;
		}
		int tilesX = highX - lowX + 1;
		int tilesY = highY - lowY + 1;
		River box = new River(lowX * 128, lowY * 128, tilesX * PER_TILE, tilesY * PER_TILE);
		textures.clear();
		overlays.clear();
		boolean[] wet = wet(view, me.getPlane(), box);
		// TEMPORARY: what was read, to spot water with another floor texture.
		int water = 0;
		for (boolean cell : wet)
		{
			water += cell ? 1 : 0;
		}
		log.debug("Read round {}: {} water cells; floor textures (cells): {}", me, water, textures);
		// TEMPORARY: the floor overlay under each river and lake point in the scene, to find untextured water.
		Map<WorldPoint, Integer> atPoints = new LinkedHashMap<>();
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			for (WorldPoint point : route)
			{
				LocalPoint local = point.getPlane() == me.getPlane() ? LocalPoint.fromWorld(view, point) : null;
				if (local != null)
				{
					atPoints.put(point, overlayAt(view, me.getPlane(), local.getSceneX(), local.getSceneY()));
				}
			}
		}
		log.debug("Read round {}: untextured tiles by overlay: {}; overlay at points: {}", me, overlays, atPoints);
		// Every tile read, as walked.
		Map<Integer, Integer> walkedHere = walked.computeIfAbsent(me.getPlane(), plane -> new HashMap<>());
		for (int ty = 0; ty < tilesY; ty++)
		{
			for (int tx = 0; tx < tilesX; tx++)
			{
				walkedHere.put((view.getBaseX() + lowX + tx) << 14 | (view.getBaseY() + lowY + ty),
					cellBits(wet, box.width, tx, ty));
			}
		}
		// And each river or lake's own: the tiles within WATER_REACH of a river's line, or LAKE_RADIUS + 1 of a lake's
		// spawn points. What's walked past further off is taken when it's baked.
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (route[0].getPlane() != me.getPlane())
			{
				continue;
			}
			int[] bounds = boundsOf(route);
			Map<Integer, Integer> tiles = null;
			for (int ty = 0; ty < tilesY; ty++)
			{
				for (int tx = 0; tx < tilesX; tx++)
				{
					int x = view.getBaseX() + lowX + tx;
					int y = view.getBaseY() + lowY + ty;
					if (!inBounds(bounds, x, y) || !reaches(route, x, y))
					{
						continue;
					}
					tiles = tiles != null ? tiles : recorded.computeIfAbsent(route[0], first -> Bakes.bakedTiles(rivers, first));
					Integer bits = walkedHere.get(x << 14 | y);
					if (!bits.equals(tiles.put(x << 14 | y, bits)))
					{
						changed.add(route[0]);
					}
				}
			}
		}
	}

	/**
	 * Keeps a river's water to within PATH_KEEP tiles of its path and its forks' paths (world local units).
	 */
	private static void keepNear(River river, List<int[]> points, List<int[]> branchPaths)
	{
		boolean[] near = new boolean[river.width * river.height];
		int reach = PATH_KEEP * PER_TILE;
		List<int[]> all = new ArrayList<>(points);
		for (int[] branch : branchPaths)
		{
			for (int k = 0; k + 1 < branch.length; k += 2)
			{
				all.add(new int[]{branch[k], branch[k + 1]});
			}
		}
		// Each path point marks the cells within reach of it.
		for (int[] point : all)
		{
			int ci = Math.floorDiv(point[0] - river.x0, CELL);
			int cj = Math.floorDiv(point[1] - river.y0, CELL);
			for (int j = Math.max(0, cj - reach); j <= Math.min(river.height - 1, cj + reach); j++)
			{
				for (int i = Math.max(0, ci - reach); i <= Math.min(river.width - 1, ci + reach); i++)
				{
					int di = i - ci;
					int dj = j - cj;
					near[j * river.width + i] |= di * di + dj * dj <= reach * reach;
				}
			}
		}
		for (int c = 0; c < near.length; c++)
		{
			river.water[c] &= near[c];
		}
	}

	/**
	 * A tile's water cells as bits, a bit per cell, row by row.
	 */
	private static int cellBits(boolean[] wet, int width, int tx, int ty)
	{
		int bits = 0;
		for (int cy = 0; cy < PER_TILE; cy++)
		{
			for (int cx = 0; cx < PER_TILE; cx++)
			{
				if (wet[(ty * PER_TILE + cy) * width + tx * PER_TILE + cx])
				{
					bits |= 1 << (cy * PER_TILE + cx);
				}
			}
		}
		return bits;
	}

	/**
	 * A river or lake's recorded water, starting from its bake if it has one, so it can be baked again (with new fish
	 * blockers, say) without walking all of it; with any water walked past it reaches.
	 */
	private Map<Integer, Integer> tilesOf(WorldPoint[] route)
	{
		Map<Integer, Integer> tiles = recorded.computeIfAbsent(route[0], first -> Bakes.bakedTiles(rivers, first));
		takeWalked(route, tiles);
		return tiles;
	}

	/**
	 * Adds the water walked past while baking that a river or lake reaches to its tiles; true if any changed.
	 */
	private boolean takeWalked(WorldPoint[] route, Map<Integer, Integer> tiles)
	{
		boolean changed = false;
		int[] bounds = boundsOf(route);
		for (Map.Entry<Integer, Integer> tile : walked.getOrDefault(route[0].getPlane(), Map.of()).entrySet())
		{
			int x = tile.getKey() >> 14;
			int y = tile.getKey() & 0x3FFF;
			if (inBounds(bounds, x, y) && reaches(route, x, y)
				&& !tile.getValue().equals(tiles.put(tile.getKey(), tile.getValue())))
			{
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * The world tiles a river or lake could reach, as a box {west, south, east, north}: round its points and side
	 * channels, grown by its reach. A quick check before the slower reaches().
	 */
	private int[] boundsOf(WorldPoint[] route)
	{
		int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (WorldPoint point : route)
		{
			RiverSpotFish.include(bounds, point.getX(), point.getY());
		}
		for (int[] branch : rivers.bakedBranches(route[0]))
		{
			for (int k = 0; k + 1 < branch.length; k += 2)
			{
				RiverSpotFish.include(bounds, branch[k], branch[k + 1]);
			}
		}
		int grow = rivers.isLake(route) ? RiverSpotFish.LAKE_RADIUS + 1 : RiverSpotFish.WATER_REACH;
		return new int[]{bounds[0] - grow, bounds[1] - grow, bounds[2] + grow, bounds[3] + grow};
	}

	private static boolean inBounds(int[] bounds, int x, int y)
	{
		return x >= bounds[0] && y >= bounds[1] && x <= bounds[2] && y <= bounds[3];
	}

	/**
	 * Whether a world tile is near enough a river's line, or a lake's spawn points, to be recorded for it.
	 */
	private boolean reaches(WorldPoint[] route, int x, int y)
	{
		if (rivers.isLake(route))
		{
			return RiverSpotFish.toSpawns(route, x, y) <= RiverSpotFish.LAKE_RADIUS + 1;
		}
		if (RiverSpotFish.toLine(route, x, y) <= RiverSpotFish.WATER_REACH)
		{
			return true;
		}
		// Its side channels' water too.
		for (int[] branch : rivers.bakedBranches(route[0]))
		{
			WorldPoint[] line = new WorldPoint[branch.length / 2];
			for (int k = 0; k < line.length; k++)
			{
				line[k] = new WorldPoint(branch[2 * k], branch[2 * k + 1], route[0].getPlane());
			}
			if (RiverSpotFish.toLine(line, x, y) <= RiverSpotFish.WATER_REACH)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Each recorded river and lake as a baked file's name and text: its water joined up from its first point (a lake's
	 * only within LAKE_RADIUS tiles of its spawn points), and a river's path through its points that were recorded.
	 * Lines are "water y x0 x1 ..." (runs of water cells in a row, end exclusive, world cells) and "path x y ..." (world
	 * local units).
	 */
	Map<String, String> save()
	{
		long started = System.nanoTime();
		Map<WorldPoint, String> texts = new LinkedHashMap<>();
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			// Only those whose water changed.
			boolean dirty = changed.remove(route[0]);
			Map<Integer, Integer> tiles = recorded.computeIfAbsent(route[0], first -> Bakes.bakedTiles(rivers, first));
			String text = takeWalked(route, tiles) || dirty ? bakeRoute(route, tiles) : null;
			if (text != null)
			{
				texts.put(route[0], text);
			}
		}
		// The water walked past is in the bakes now, so the next baking starts with nothing walked yet. Each river and
		// lake keeps the water recorded for it, though: a bake keeps only the water joined to its start, so one cut short
		// (a waterfall not yet allowed through, say) would lose the rest.
		walked.clear();
		changed.clear();
		log.debug("Baked {} in {} ms", texts.size(), (System.nanoTime() - started) / 1_000_000);
		return filesOf(texts);
	}

	/**
	 * A river or lake's bake from its tiles' water: the water saved as read (fish blockers and allowers are applied
	 * when it's played, so they can be taken off again), and a river's path laid through the water with them applied.
	 * Null if none of its points have water.
	 */
	private String bakeRoute(WorldPoint[] route, Map<Integer, Integer> recordedTiles)
	{
		boolean lake = rivers.isLake(route);
		// Only the water it reaches: any further out, as in older bakes, is left out.
		int[] bounds = boundsOf(route);
		Map<Integer, Integer> tiles = new HashMap<>();
		recordedTiles.forEach((key, bits) ->
		{
			if (inBounds(bounds, key >> 14, key & 0x3FFF) && reaches(route, key >> 14, key & 0x3FFF))
			{
				tiles.put(key, bits);
			}
		});
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int key : tiles.keySet())
		{
			minX = Math.min(minX, key >> 14);
			maxX = Math.max(maxX, key >> 14);
			minY = Math.min(minY, key & 0x3FFF);
			maxY = Math.max(maxY, key & 0x3FFF);
		}
		// A tile of land all round, so water on the box's edge is measured to its bank, not past it.
		minX--;
		minY--;
		maxX++;
		maxY++;
		// In world local units, so its cells are world cells.
		River river = new River(minX * 128, minY * 128, (maxX - minX + 1) * PER_TILE, (maxY - minY + 1) * PER_TILE);
		boolean[] wet = new boolean[river.width * river.height];
		for (Map.Entry<Integer, Integer> tile : tiles.entrySet())
		{
			int tx = (tile.getKey() >> 14) - minX;
			int ty = (tile.getKey() & 0x3FFF) - minY;
			for (int bit = 0; bit < PER_TILE * PER_TILE; bit++)
			{
				wet[(ty * PER_TILE + bit / PER_TILE) * river.width + tx * PER_TILE + bit % PER_TILE] =
					(tile.getValue() >> bit & 1) != 0;
			}
		}
		if (lake)
		{
			for (int c = 0; c < wet.length; c++)
			{
				double x = river.x0 + (c % river.width + 0.5) * CELL;
				double y = river.y0 + (c / river.width + 0.5) * CELL;
				wet[c] &= RiverSpotFish.toSpawns(route, (int) Math.floor(x / 128), (int) Math.floor(y / 128))
					<= RiverSpotFish.LAKE_RADIUS;
			}
		}
		// The water as read, kept to save; the path is laid with fish blockers and allowers applied.
		boolean[] read = wet.clone();
		for (int kind = 0; kind < 2; kind++)
		{
			for (Map.Entry<WorldPoint, Half> mark : marks(kind).entrySet())
			{
				WorldPoint at = mark.getKey();
				int tx = at.getX() - minX;
				int ty = at.getY() - minY;
				if (at.getPlane() != route[0].getPlane() || tx < 0 || ty < 0 || tx > maxX - minX || ty > maxY - minY)
				{
					continue;
				}
				int[] cells = RiverSpotFish.blockedCells(mark.getValue().name());
				for (int cy = cells[2]; cy < cells[3]; cy++)
				{
					for (int cx = cells[0]; cx < cells[1]; cx++)
					{
						wet[(ty * PER_TILE + cy) * river.width + tx * PER_TILE + cx] = kind == 1;
					}
				}
			}
		}
		// Its points that were recorded: within two tiles of water. A river's water is what's joined to its first point,
		// a lake's what's joined to any of its spawn points.
		List<Integer> cells = new ArrayList<>();
		for (WorldPoint point : route)
		{
			int cell = nearestWater(river, point.getX(), point.getY(), cells.isEmpty() || lake ? wet : river.water);
			if (cell >= 0)
			{
				if (cells.isEmpty() || lake)
				{
					flood(river, wet, cell);
				}
				cells.add(cell);
			}
		}
		if (cells.isEmpty())
		{
			log.debug("Bake {}: none of its points were recorded", route[0]);
			return null;
		}
		// Saved: the water as read that's joined to the first point (or any spawn point), or to the water with marks
		// applied.
		River saved = new River(river.x0, river.y0, river.width, river.height);
		for (int cell : lake ? cells : cells.subList(0, 1))
		{
			if (read[cell])
			{
				flood(saved, read, cell);
			}
		}
		for (int c = 0; c < read.length; c++)
		{
			saved.water[c] |= read[c] && river.water[c];
		}
		List<int[]> points = new ArrayList<>();
		if (!lake && cells.size() >= 2)
		{
			RiverSpotFish.clearances(river);
			int[] stops = new int[cells.size()];
			for (int k = 0; k < stops.length; k++)
			{
				// Through each route point as placed.
				stops[k] = cells.get(k);
			}
			for (int[] point : thinned(layPath(river, stops)))
			{
				points.add(point);
			}
		}
		// Side channels: each laid through its picked tiles, if they're all on the water.
		List<int[]> branchStops = rivers.bakedBranches(route[0]);
		List<int[]> branchPaths = new ArrayList<>();
		for (int[] branch : branchStops)
		{
			int[] laid = new int[0];
			int[] stops = new int[branch.length / 2];
			boolean found = !lake && !points.isEmpty();
			for (int k = 0; found && k < stops.length; k++)
			{
				// Through each picked tile as it is: a fork goes where it's told.
				stops[k] = nearestWater(river, branch[2 * k], branch[2 * k + 1], river.water);
				found = stops[k] >= 0;
			}
			if (found)
			{
				List<int[]> branchPoints = thinned(layPath(river, stops));
				laid = new int[branchPoints.size() * 2];
				for (int k = 0; k < branchPoints.size(); k++)
				{
					laid[2 * k] = branchPoints.get(k)[0];
					laid[2 * k + 1] = branchPoints.get(k)[1];
				}
			}
			else
			{
				log.debug("Bake {}: a branch from {}, {} isn't all on recorded water yet; walk it while baking", route[0],
					branch[0], branch[1]);
			}
			branchPaths.add(laid);
		}
		// A river keeps only the water near its path and its forks' paths.
		if (!points.isEmpty())
		{
			keepNear(saved, points, branchPaths);
		}
		// Each row's runs of water (world cells, end exclusive), from the first row with water to the last.
		List<int[]> rows = new ArrayList<>();
		int firstRow = -1;
		for (int j = 0; j < river.height; j++)
		{
			List<Integer> row = new ArrayList<>();
			for (int i = 0; i < river.width; i++)
			{
				if (saved.water[j * river.width + i] && (i == 0 || !saved.water[j * river.width + i - 1]))
				{
					int end = i;
					while (end < river.width && saved.water[j * river.width + end])
					{
						end++;
					}
					row.add(minX * PER_TILE + i);
					row.add(minX * PER_TILE + end);
				}
			}
			if (firstRow < 0 && row.isEmpty())
			{
				continue;
			}
			firstRow = firstRow < 0 ? j : firstRow;
			rows.add(row.stream().mapToInt(Integer::intValue).toArray());
		}
		while (!rows.isEmpty() && rows.get(rows.size() - 1).length == 0)
		{
			rows.remove(rows.size() - 1);
		}
		log.debug("Bake {}: {} of {} points recorded, {} tiles", route[0], cells.size(), route.length, tiles.size());
		return Bakes.bakedText(Bakes.headLine(route, lake), rivers.bakedFishShare(route[0]),
			minX * PER_TILE, minY * PER_TILE + firstRow, rows, points,
			Bakes.bakedMarks(rivers, route[0]), branchStops, branchPaths,
			Bakes.bakedBranchShares(rivers, route[0]));
	}

	/**
	 * A laid path's every 8th point (a tile apart), and its last, rounded; spaced evenly again when used.
	 */
	private static List<int[]> thinned(double[][] path)
	{
		List<int[]> points = new ArrayList<>();
		int last = path[0].length - 1;
		for (int k = 0; k <= last; k = k + 8 > last && k < last ? last : k + 8)
		{
			points.add(new int[]{(int) Math.round(path[0][k]), (int) Math.round(path[1][k])});
		}
		return points;
	}

	/**
	 * Lays the paths of side channels not laid yet, baking their rivers again from their bakes.
	 */
	void layBranches()
	{
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (!Bakes.hasUnlaidBranch(rivers, route[0]))
			{
				continue;
			}
			relay(route);
		}
	}

	/**
	 * Saves a river or lake's new text, if any, into its bake file (with whatever else is in it), and shows it at once.
	 */
	private void saveBake(String name, WorldPoint first, String text)
	{
		if (text != null)
		{
			String file = Bakes.fileText(rivers, name, Map.of(first, text));
			saveFiles.accept(Map.of(name, file));
			Bakes.reloadBaked(rivers, name, file);
			// Read the water round the player again, so a new or changed river or lake is recorded at once.
			readAt = null;
		}
	}

	/**
	 * Rivers and lakes' new texts (by first point) as their bake files' texts, with whatever else is in each.
	 */
	private Map<String, String> filesOf(Map<WorldPoint, String> texts)
	{
		Map<String, Map<WorldPoint, String>> byFile = new LinkedHashMap<>();
		texts.forEach((first, text) -> byFile.computeIfAbsent(Bakes.bakedName(first), name -> new HashMap<>())
			.put(first, text));
		Map<String, String> files = new LinkedHashMap<>();
		byFile.forEach((name, ofFile) -> files.put(name, Bakes.fileText(rivers, name, ofFile)));
		return files;
	}

	/**
	 * Bakes a river or lake again from its bake (and any water walked while baking), saved and shown at once.
	 */
	private void relay(WorldPoint[] route)
	{
		saveBake(Bakes.bakedName(route[0]), route[0], bakeRoute(route, tilesOf(route)));
	}

	/**
	 * The river and fork (its place in the river's list) one of whose picked tiles is a tile, or null.
	 */
	private Object[] forkAt(WorldPoint tile)
	{
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			List<int[]> forks = rivers.bakedBranches(route[0]);
			for (int f = 0; route[0].getPlane() == tile.getPlane() && f < forks.size(); f++)
			{
				int[] stops = forks.get(f);
				for (int k = 0; k + 1 < stops.length; k += 2)
				{
					if (stops[k] == tile.getX() && stops[k + 1] == tile.getY())
					{
						return new Object[]{route, f};
					}
				}
			}
		}
		return null;
	}

	/**
	 * Whether a tile is one of a fork's picked tiles.
	 */
	boolean hasFork(WorldPoint tile)
	{
		return forkAt(tile) != null;
	}

	/**
	 * Sets the share of fish taking the fork one of whose picked tiles is a tile, percent, or -1 for by its width;
	 * saved and shown at once.
	 */
	void setForkShare(WorldPoint tile, int share)
	{
		Object[] found = forkAt(tile);
		if (found == null)
		{
			return;
		}
		WorldPoint[] route = (WorldPoint[]) found[0];
		saveBake(Bakes.bakedName(route[0]), route[0], Bakes.setBranchShare(rivers, route[0], (Integer) found[1], share));
	}

	/**
	 * Removes the fork one of whose picked tiles is a tile, from its river's bake, saved and shown at once.
	 */
	void removeFork(WorldPoint tile)
	{
		Object[] found = forkAt(tile);
		if (found == null)
		{
			return;
		}
		WorldPoint[] route = (WorldPoint[]) found[0];
		Bakes.removeBranch(rivers, route[0], (Integer) found[1]);
		relay(route);
		log.debug("Fork removed from {}", Bakes.bakedName(route[0]));
	}

	/**
	 * The river one of whose route points is a tile, or null.
	 */
	private WorldPoint[] routeWithPoint(WorldPoint tile, boolean lake)
	{
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (rivers.isLake(route) == lake && Arrays.asList(route).contains(tile))
			{
				return route;
			}
		}
		return null;
	}

	/**
	 * Whether a tile is one of a river's route points, or a lake's spawn points.
	 */
	boolean isRoutePoint(WorldPoint tile, boolean lake)
	{
		return routeWithPoint(tile, lake) != null;
	}

	/**
	 * Takes a route point out of its river, keeping at least its start and end; or a spawn point out of its lake,
	 * keeping at least one.
	 */
	void removeRoutePoint(WorldPoint tile, boolean lake)
	{
		WorldPoint[] route = routeWithPoint(tile, lake);
		if (route != null && route.length > (lake ? 1 : 2))
		{
			editRoute(route, Arrays.stream(route).filter(point -> !point.equals(tile)).toArray(WorldPoint[]::new));
		}
	}

	/**
	 * Moves a river's route point, or a lake's spawn point, to another tile.
	 */
	void moveRoutePoint(WorldPoint from, WorldPoint to, boolean lake)
	{
		WorldPoint[] route = routeWithPoint(from, lake);
		if (route != null)
		{
			editRoute(route, Arrays.stream(route).map(point -> point.equals(from) ? to : point).toArray(WorldPoint[]::new));
		}
	}

	/**
	 * Adds a point to the river whose route passes nearest a tile (within BOX_MARGIN), between the two points it's
	 * nearest the line between.
	 */
	void addRoutePoint(WorldPoint tile)
	{
		WorldPoint[] best = null;
		int after = 0;
		double nearest = BOX_MARGIN;
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			for (int k = 0; !rivers.isLake(route) && route[0].getPlane() == tile.getPlane() && k + 1 < route.length; k++)
			{
				double d = RiverSpotFish.toLine(new WorldPoint[]{route[k], route[k + 1]}, tile.getX(), tile.getY());
				if (d <= nearest)
				{
					nearest = d;
					best = route;
					after = k;
				}
			}
		}
		if (best == null)
		{
			log.debug("River point at {}: no river near it", tile);
			return;
		}
		WorldPoint[] changed = new WorldPoint[best.length + 1];
		System.arraycopy(best, 0, changed, 0, after + 1);
		changed[after + 1] = tile;
		System.arraycopy(best, after + 1, changed, after + 2, best.length - after - 1);
		editRoute(best, changed);
	}

	/**
	 * Gives a river its changed route, its path laid again from its bake, saved and shown at once.
	 */
	private void editRoute(WorldPoint[] route, WorldPoint[] changed)
	{
		String text = Bakes.setRoute(rivers, route, changed);
		if (text == null)
		{
			return;
		}
		Map<Integer, Integer> tiles = recorded.remove(route[0]);
		recorded.put(changed[0], tiles != null ? tiles : Bakes.bakedTiles(rivers, changed[0]));
		String laid = bakeRoute(changed, recorded.get(changed[0]));
		saveBake(Bakes.bakedName(changed[0]), changed[0], laid != null ? laid : text);
		log.debug("Route of {} changed", Bakes.bakedName(changed[0]));
	}

	/**
	 * Makes a new river from a picked route: its bake, with only its route until it's walked while baking.
	 */
	void newRiver(List<WorldPoint> picked)
	{
		WorldPoint[] route = picked.toArray(new WorldPoint[0]);
		// Not over another river that starts there (a lake there is replaced).
		WorldPoint[] there = Bakes.routeOf(rivers, route[0]);
		if (there != null && !rivers.isLake(there))
		{
			log.debug("River start at {}: another river starts there", route[0]);
			return;
		}
		String name = "river-" + route[0].getX() + "-" + route[0].getY() + ".txt";
		saveBake(name, route[0], Bakes.headLine(route, false) + "\n");
		log.debug("New river {}: walk it while baking to bake its water and path", name);
	}

	/**
	 * Adds a spawn point to the lake whose first is within LAKE_PICK_REACH tiles, or makes a new lake there: in the
	 * bake of a river whose line is within BOX_MARGIN tiles, else its own.
	 */
	void addLakeSpawn(WorldPoint spawn)
	{
		for (WorldPoint[] lake : rivers.routesAndLakes())
		{
			if (rivers.isLake(lake) && lake[0].getPlane() == spawn.getPlane()
				&& lake[0].distanceTo2D(spawn) <= RiverSpotFish.LAKE_PICK_REACH)
			{
				WorldPoint[] spawns = Arrays.copyOf(lake, lake.length + 1);
				spawns[lake.length] = spawn;
				saveBake(Bakes.bakedName(lake[0]), lake[0], Bakes.setLakeSpawns(rivers, lake[0], spawns));
				return;
			}
		}
		// Each river and lake goes by its first point, so a new one can't start on another's.
		if (Bakes.routeOf(rivers, spawn) != null)
		{
			log.debug("Lake spawn at {}: another river or lake starts there", spawn);
			return;
		}
		String name = "lake-" + spawn.getX() + "-" + spawn.getY() + ".txt";
		for (WorldPoint[] river : rivers.routesAndLakes())
		{
			if (!rivers.isLake(river) && river[0].getPlane() == spawn.getPlane()
				&& RiverSpotFish.toLine(river, spawn.getX(), spawn.getY()) <= BOX_MARGIN)
			{
				name = Bakes.bakedName(river[0]);
			}
		}
		saveBake(name, spawn, Bakes.headLine(new WorldPoint[]{spawn}, true) + "\n");
		log.debug("New lake {}: walk round it while baking to bake its water", name);
	}

	/**
	 * Adds a picked route as a side channel of the baked river both its ends are near, laid at once from its bake
	 * (once its water is recorded); false if there's no such river.
	 */
	boolean addBranch(List<WorldPoint> picked)
	{
		WorldPoint start = picked.get(0);
		WorldPoint end = picked.get(picked.size() - 1);
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (rivers.isLake(route) || route[0].getPlane() != start.getPlane()
				|| !nearRiver(route, start) || !nearRiver(route, end))
			{
				continue;
			}
			int[] stops = new int[picked.size() * 2];
			for (int k = 0; k < picked.size(); k++)
			{
				stops[2 * k] = picked.get(k).getX();
				stops[2 * k + 1] = picked.get(k).getY();
			}
			if (!Bakes.addBranch(rivers, route[0], stops))
			{
				continue;
			}
			relay(route);
			log.debug("Branch added to {}", Bakes.bakedName(route[0]));
			return true;
		}
		return false;
	}

	/**
	 * Whether a tile is within BOX_MARGIN of a river's line or its laid path (which may bend well off the line).
	 */
	private boolean nearRiver(WorldPoint[] route, WorldPoint at)
	{
		if (RiverSpotFish.toLine(route, at.getX(), at.getY()) <= BOX_MARGIN)
		{
			return true;
		}
		RiverSpotFish.Baked saved = rivers.baked.get(route[0]);
		double reach = BOX_MARGIN * 128.0;
		double x = at.getX() * 128 + 64;
		double y = at.getY() * 128 + 64;
		for (int k = 0; saved != null && saved.pathX != null && k < saved.pathX.length; k++)
		{
			double dx = saved.pathX[k] - x;
			double dy = saved.pathY[k] - y;
			if (dx * dx + dy * dy <= reach * reach)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Each kind of mark's colour, drawn and in the menu: fish blockers red, allowers green, surfacers orange, divers
	 * blue.
	 */
	static Color markColour(int kind)
	{
		return kind == 0 ? Color.RED : kind == 1 ? Color.GREEN : kind == 2 ? Color.ORANGE : new Color(70, 110, 255);
	}

	/**
	 * Whether a tile has a fish blocker, allower or surfacer (kind 0, 1 or 2) in any bake.
	 */
	boolean hasMark(WorldPoint tile, int kind)
	{
		return marks(kind).containsKey(tile);
	}

	/**
	 * Places a fish blocker, allower or surfacer (kind 0, 1 or 2) on a tile, or a half of it, or with null clears it:
	 * into the bake of each river and lake it's near, saved at once, so it shows straight away and lasts.
	 */
	void setMark(WorldPoint tile, Half half, int kind)
	{
		Map<WorldPoint, String> texts = new LinkedHashMap<>();
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (route[0].getPlane() == tile.getPlane() && reaches(route, tile.getX(), tile.getY()))
			{
				String marked = Bakes.setMark(rivers, route[0], tile, half == null ? null : half.name(), kind);
				if (marked == null)
				{
					continue;
				}
				// Surfacers and divers don't change the path.
				if (kind >= 2)
				{
					texts.put(route[0], marked);
					continue;
				}
				// Baked again from its bake (and any water walked while baking), so the path goes round it too.
				long started = System.nanoTime();
				String text = bakeRoute(route, tilesOf(route));
				log.debug("Re-laid {} in {} ms", Bakes.bakedName(route[0]), (System.nanoTime() - started) / 1_000_000);
				if (text != null)
				{
					texts.put(route[0], text);
				}
			}
		}
		if (texts.isEmpty())
		{
			log.debug("Fish {} at {}: no baked river or lake near it", RiverSpotFish.MARK_WORDS[kind], tile);
			return;
		}
		Map<String, String> files = filesOf(texts);
		saveFiles.accept(files);
		files.forEach((name, text) -> Bakes.reloadBaked(rivers, name, text));
		readAt = null;
	}

	/**
	 * Draws each fish blocker, allower and surfacer in its colour: an outline round the tile, or the half of it, it covers, lightly
	 * filled.
	 */
	void drawBlockers(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return;
		}
		for (int kind = 0; kind < RiverSpotFish.MARK_WORDS.length; kind++)
		{
			for (Map.Entry<WorldPoint, Half> mark : marks(kind).entrySet())
			{
				drawMark(graphics, view, mark.getKey(), mark.getValue(), markColour(kind), 50);
			}
		}
	}

	/**
	 * Draws a mark's area: an outline round the tile, or the half of it, filled at some alpha.
	 */
	private void drawMark(Graphics2D graphics, WorldView view, WorldPoint tile, Half half, Color colour, int alpha)
	{
		LocalPoint at = LocalPoint.fromWorld(view, tile);
		if (at == null)
		{
			return;
		}
		int south = half == Half.NORTH ? 0 : -64;
		int north = half == Half.SOUTH ? 0 : 64;
		int west = half == Half.EAST ? 0 : -64;
		int east = half == Half.WEST ? 0 : 64;
		Polygon area = new Polygon();
		for (int[] corner : new int[][]{{west, south}, {east, south}, {east, north}, {west, north}})
		{
			// At the water's own height, so it isn't lifted onto a bridge above.
			int x = at.getX() + corner[0];
			int y = at.getY() + corner[1];
			Point p = Perspective.localToCanvas(client, x, y, rivers.waterHeight(x, y, tile.getPlane()));
			if (p != null)
			{
				area.addPoint(p.getX(), p.getY());
			}
		}
		if (area.npoints == 4)
		{
			graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
			graphics.fill(area);
			graphics.setColor(colour);
			graphics.draw(area);
		}
	}

	/**
	 * Remembers what a picking menu entry would place or remove, to show while it's hovered.
	 */
	void preview(MenuEntry entry, WorldPoint tile, Half half, int kind)
	{
		previews.put(entry, new Object[]{tile, half, kind});
	}

	/**
	 * Forgets the last menu's entries, as a new one opens.
	 */
	void clearPreviews()
	{
		previews.clear();
		openParent = null;
	}

	/**
	 * The fish blocker, allower, surfacer or diver (kind 0 to 3) on a tile, or null.
	 */
	Half markAt(WorldPoint tile, int kind)
	{
		return marks(kind).get(tile);
	}

	/**
	 * While the menu is open, draws what the hovered mark entry would place or remove, strongly, with a thick outline.
	 */
	void drawPreview(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		if (view == null || previews.isEmpty() || !client.isMenuOpen())
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		MenuEntry inMenu = hoveredIn(client.getMenu(), mouse);
		if (inMenu != null && inMenu.getSubMenu() != null)
		{
			openParent = inMenu;
		}
		MenuEntry hovered = inMenu != null ? inMenu
			: openParent != null ? hoveredIn(openParent.getSubMenu(), mouse) : null;
		Object[] mark = previews.get(hovered);
		if (mark == null)
		{
			return;
		}
		Stroke stroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(3));
		drawMark(graphics, view, (WorldPoint) mark[0], (Half) mark[1], markColour((Integer) mark[2]), 120);
		graphics.setStroke(stroke);
	}

	/**
	 * The entry of a shown menu under the mouse, or null: rows 15 high under a 19 high header, the last entry at the
	 * top.
	 */
	private static MenuEntry hoveredIn(Menu menu, Point mouse)
	{
		int x = mouse.getX() - menu.getMenuX();
		int y = mouse.getY() - menu.getMenuY() - 19;
		MenuEntry[] entries = menu.getMenuEntries();
		int index = entries.length - 1 - y / 15;
		return x < 0 || x >= menu.getMenuWidth() || y < 0 || index < 0 ? null : entries[index];
	}

	/**
	 * Every fish blocker, allower, surfacer or diver (kind 0 to 3) from every bake.
	 */
	private Map<WorldPoint, Half> marks(int kind)
	{
		Map<WorldPoint, Half> all = new HashMap<>();
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			Bakes.bakedMarks(rivers, route[0], kind).forEach((tile, half) -> all.put(tile, Half.valueOf(half)));
		}
		return all;
	}

	/**
	 * A scene tile's floor overlay id (0 for none). The ids array may be the extended scene's, centred on
	 * the scene.
	 */
	private static int overlayAt(WorldView view, int plane, int sx, int sy)
	{
		short[][] ids = view.getScene().getOverlayIds()[plane];
		int offset = (ids.length - view.getSizeX()) / 2;
		return ids[sx + offset][sy + offset];
	}

	/**
	 * Which grid cells are water, reading under bridges. Cells under the model of a visible object that blocks
	 * movement, as rocks and posts, aren't; walk-through ones, as lily pads, and invisible blockers don't count.
	 */
	private boolean[] wet(WorldView view, int plane, River river)
	{
		Tile[][] tiles = view.getScene().getTiles()[plane];
		CollisionData[] maps = view.getCollisionMaps();
		int[][] flags = maps == null || maps[plane] == null ? null : maps[plane].getFlags();
		int width = river.width;
		int tilesX = width / PER_TILE;
		int tilesY = river.height / PER_TILE;
		int cells = width * river.height;
		boolean[] wet = new boolean[cells];
		// Tiles with any water, so objects are only looked at where there's water.
		boolean[] wetTile = new boolean[tilesX * tilesY];
		int tileX0 = Math.floorDiv(river.x0, 128);
		int tileY0 = Math.floorDiv(river.y0, 128);
		for (int ty = 0; ty < tilesY; ty++)
		{
			for (int tx = 0; tx < tilesX; tx++)
			{
				int sx = tileX0 + tx;
				int sy = tileY0 + ty;
				if (sx < 0 || sy < 0 || sx >= tiles.length
					|| sy >= tiles[sx].length || tiles[sx][sy] == null)
				{
					continue;
				}
				Tile tile = tiles[sx][sy].getBridge() != null ? tiles[sx][sy].getBridge() : tiles[sx][sy];
				SceneTilePaint paint = tile.getSceneTilePaint();
				SceneTileModel model = tile.getSceneTileModel();
				if (paint == null && model == null)
				{
					continue;
				}
				// Untextured water is known by its overlay, the whole tile; only on a tile with no texture at all, as a
				// shore tile's land part has none either, under the same overlay as its water.
				int overlay = overlayAt(view, plane, sx, sy);
				boolean untextured = paint != null ? paint.getTexture() == -1 : !textured(model);
				for (int cy = 0; cy < PER_TILE; cy++)
				{
					for (int cx = 0; cx < PER_TILE; cx++)
					{
						double x = sx * 128 + (cx + 0.5) * CELL;
						double y = sy * 128 + (cy + 0.5) * CELL;
						int c = (ty * PER_TILE + cy) * width + tx * PER_TILE + cx;
						int texture = paint != null ? paint.getTexture() : textureAt(model, x, y);
						textures.merge(texture, 1, Integer::sum);
						if (texture == -1 && cx == 0 && cy == 0)
						{
							overlays.merge(overlay, 1, Integer::sum);
						}
						wet[c] = WATER_TEXTURES.contains(texture) || untextured && WATER_OVERLAYS.contains(overlay);
						wetTile[ty * tilesX + tx] |= wet[c];
					}
				}
			}
		}
		// Cells under the model of each visible object in the water that blocks movement.
		// An object covering several tiles is listed on each, so the water tiles' lists find all those in the water.
		boolean[] solid = new boolean[cells];
		Set<GameObject> done = new HashSet<>();
		for (int ty = 0; ty < tilesY; ty++)
		{
			for (int tx = 0; tx < tilesX; tx++)
			{
				int sx = tileX0 + tx;
				int sy = tileY0 + ty;
				if (!wetTile[ty * tilesX + tx] || tiles[sx][sy] == null)
				{
					continue;
				}
				for (GameObject object : tiles[sx][sy].getGameObjects())
				{
					if (object != null && done.add(object) && visible(object) && blocks(flags, object)
						&& object.getLocalLocation() != null)
					{
						footprint(solid, river, object.getRenderable().getModel(), object.getLocalLocation(),
							object.getModelOrientation());
					}
				}
			}
		}
		for (int c = 0; c < cells; c++)
		{
			wet[c] &= !solid[c];
		}
		return wet;
	}

	/**
	 * Whether an object has a model with faces to see.
	 */
	private static boolean visible(GameObject object)
	{
		if (object == null || object.getRenderable() == null || object.getSceneMinLocation() == null
			|| object.getSceneMaxLocation() == null || NOT_BLOCKING.contains(object.getId()))
		{
			return false;
		}
		Model model = object.getRenderable().getModel();
		return model != null && model.getFaceCount() > 0;
	}

	/**
	 * Whether any tile under an object is flagged as blocked by an object.
	 */
	private static boolean blocks(int[][] flags, GameObject object)
	{
		Point min = object.getSceneMinLocation();
		Point max = object.getSceneMaxLocation();
		for (int x = Math.max(0, min.getX()); flags != null && x <= Math.min(flags.length - 1, max.getX()); x++)
		{
			for (int y = Math.max(0, min.getY()); y <= Math.min(flags[x].length - 1, max.getY()); y++)
			{
				if ((flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_OBJECT) != 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Marks the grid cells inside a rough outline of a model seen from above, placed at a local point and turned by an
	 * orientation: the convex hull of its corners, drawn in by OBJECT_INSET.
	 */
	private static void footprint(boolean[] solid, River river, Model model, LocalPoint at, int orientation)
	{
		int count = model.getVerticesCount();
		float[] vx = model.getVerticesX();
		float[] vz = model.getVerticesZ();
		int sin = Perspective.SINE[orientation & 2047];
		int cos = Perspective.COSINE[orientation & 2047];
		double[][] points = new double[count][];
		for (int v = 0; v < count; v++)
		{
			points[v] = new double[]{at.getX() + (vz[v] * sin + vx[v] * cos) / 65536.0,
				at.getY() + (vz[v] * cos - vx[v] * sin) / 65536.0};
		}
		double[][] hull = hull(points);
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (double[] point : hull)
		{
			minX = Math.min(minX, point[0]);
			minY = Math.min(minY, point[1]);
			maxX = Math.max(maxX, point[0]);
			maxY = Math.max(maxY, point[1]);
		}
		int i0 = Math.max(0, (int) Math.floor((minX - river.x0) / CELL));
		int i1 = Math.min(river.width - 1, (int) Math.floor((maxX - river.x0) / CELL));
		int j0 = Math.max(0, (int) Math.floor((minY - river.y0) / CELL));
		int j1 = Math.min(river.height - 1, (int) Math.floor((maxY - river.y0) / CELL));
		for (int j = j0; j <= j1; j++)
		{
			for (int i = i0; i <= i1; i++)
			{
				double x = river.x0 + (i + 0.5) * CELL;
				double y = river.y0 + (j + 0.5) * CELL;
				boolean inside = hull.length >= 3;
				for (int k = 0; inside && k < hull.length; k++)
				{
					double[] p = hull[k];
					double[] q = hull[(k + 1) % hull.length];
					double edge = Math.max(1e-6, Math.hypot(q[0] - p[0], q[1] - p[1]));
					inside = ((q[0] - p[0]) * (y - p[1]) - (q[1] - p[1]) * (x - p[0])) / edge >= OBJECT_INSET;
				}
				solid[j * river.width + i] |= inside;
			}
		}
	}

	/**
	 * The convex hull of some points, anticlockwise, by the monotone chain.
	 */
	private static double[][] hull(double[][] points)
	{
		double[][] sorted = points.clone();
		Arrays.sort(sorted, (p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(p[1], q[1]));
		double[][] hull = new double[2 * sorted.length + 1][];
		int k = 0;
		for (int pass = 0; pass < 2; pass++)
		{
			int start = k;
			for (int n = 0; n < sorted.length; n++)
			{
				double[] p = sorted[pass == 0 ? n : sorted.length - 1 - n];
				while (k >= start + 2 && (hull[k - 1][0] - hull[k - 2][0]) * (p[1] - hull[k - 2][1])
					- (hull[k - 1][1] - hull[k - 2][1]) * (p[0] - hull[k - 2][0]) <= 0)
				{
					k--;
				}
				hull[k++] = p;
			}
			k--;
		}
		return Arrays.copyOf(hull, Math.max(0, k));
	}

	/**
	 * Whether any face of a shaped tile has a texture.
	 */
	private static boolean textured(SceneTileModel model)
	{
		int[] textures = model.getTriangleTextureId();
		for (int k = 0; textures != null && k < textures.length; k++)
		{
			if (textures[k] >= 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Texture of a shaped tile's face under a place, or -1.
	 */
	private static int textureAt(SceneTileModel model, double x, double y)
	{
		int[] textures = model.getTriangleTextureId();
		int[] a = model.getFaceX();
		int[] b = model.getFaceY();
		int[] c = model.getFaceZ();
		int[] vx = model.getVertexX();
		int[] vz = model.getVertexZ();
		if (textures == null || a == null)
		{
			return -1;
		}
		for (int f = 0; f < a.length; f++)
		{
			if (inside(x, y, vx[a[f]], vz[a[f]], vx[b[f]], vz[b[f]], vx[c[f]], vz[c[f]]))
			{
				return textures[f];
			}
		}
		return -1;
	}

	private static boolean inside(double x, double y, double ax, double ay, double bx, double by, double cx, double cy)
	{
		double d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by);
		double d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy);
		double d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
		boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
		boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
		return !(negative && positive);
	}

	/**
	 * Floods the river's water from one cell; clearances() then works out bank distances.
	 */
	private static void flood(River river, boolean[] wet, int start)
	{
		int width = river.width;
		int height = river.height;
		int[] stack = new int[width * height];
		int top = 0;
		stack[top++] = start;
		river.water[start] = true;
		while (top > 0)
		{
			int c = stack[--top];
			int i = c % width;
			int j = c / width;
			for (int dj = -1; dj <= 1; dj++)
			{
				for (int di = -1; di <= 1; di++)
				{
					int ni = i + di;
					int nj = j + dj;
					int n = nj * width + ni;
					if (ni >= 0 && nj >= 0 && ni < width && nj < height && wet[n] && !river.water[n])
					{
						river.water[n] = true;
						stack[top++] = n;
					}
				}
			}
		}
	}

	/**
	 * The water cell nearest a world tile's middle, within POINT_REACH, or -1.
	 */
	private static int nearestWater(River river, int tileX, int tileY, boolean[] water)
	{
		int width = river.width;
		int height = river.height;
		int atX = tileX * 128 + 64;
		int atY = tileY * 128 + 64;
		int ci = Math.max(0, Math.min(width - 1, Math.floorDiv(atX - river.x0, CELL)));
		int cj = Math.max(0, Math.min(height - 1, Math.floorDiv(atY - river.y0, CELL)));
		int nearestCell = -1;
		// Just over POINT_REACH, so one at it counts.
		double nearest = POINT_REACH + 1;
		// Square rings outwards; a ring further out than the nearest found yet can't hold a nearer one.
		for (int ring = 0; ring * CELL <= nearest + CELL; ring++)
		{
			for (int j = cj - ring; j <= cj + ring; j++)
			{
				for (int i = ci - ring; i <= ci + ring; i++)
				{
					boolean edge = Math.abs(i - ci) == ring || Math.abs(j - cj) == ring;
					if (!edge || i < 0 || j < 0 || i >= width || j >= height || !water[j * width + i])
					{
						continue;
					}
					double d = Math.hypot(river.x0 + (i + 0.5) * CELL - atX, river.y0 + (j + 0.5) * CELL - atY);
					if (d < nearest)
					{
						nearest = d;
						nearestCell = j * width + i;
					}
				}
			}
		}
		return nearestCell;
	}

	/**
	 * The path down the middle of the river through some cells in turn (start, waypoints, end), smoothed and spaced
	 * evenly; its x and y.
	 */
	private static double[][] layPath(River river, int[] stops)
	{
		int width = river.width;
		PathSearch search = new PathSearch(river);
		List<double[]> cells = new ArrayList<>();
		cells.add(new double[]{river.x0 + (stops[0] % width + 0.5) * CELL, river.y0 + (stops[0] / width + 0.5) * CELL});
		for (int leg = 0; leg + 1 < stops.length; leg++)
		{
			if (stops[leg] == stops[leg + 1])
			{
				continue;
			}
			search.run(stops[leg], stops[leg + 1]);
			List<double[]> part = new ArrayList<>();
			for (int c = stops[leg + 1]; c != stops[leg]; c = search.from[c])
			{
				part.add(new double[]{river.x0 + (c % width + 0.5) * CELL, river.y0 + (c / width + 0.5) * CELL});
			}
			Collections.reverse(part);
			cells.addAll(part);
		}
		// Smooth, keeping the ends, then space evenly.
		int count = cells.size();
		double[] xs = new double[count];
		double[] ys = new double[count];
		for (int p = 0; p < count; p++)
		{
			xs[p] = cells.get(p)[0];
			ys[p] = cells.get(p)[1];
		}
		double[] nextX = new double[count];
		double[] nextY = new double[count];
		for (int pass = 0; pass < SMOOTHING; pass++)
		{
			nextX[0] = xs[0];
			nextY[0] = ys[0];
			nextX[count - 1] = xs[count - 1];
			nextY[count - 1] = ys[count - 1];
			for (int p = 1; p < count - 1; p++)
			{
				nextX[p] = (xs[p - 1] + 2 * xs[p] + xs[p + 1]) / 4;
				nextY[p] = (ys[p - 1] + 2 * ys[p] + ys[p + 1]) / 4;
			}
			double[] swap = xs;
			xs = nextX;
			nextX = swap;
			swap = ys;
			ys = nextY;
			nextY = swap;
		}
		return RiverSpotFish.even(xs, ys, count);
	}

	/**
	 * Cheapest paths through a river's water, costing 1 / clearance squared so they keep to the middle. Made once per
	 * route and run once per leg, stopping when it reaches the leg's end; its queue is a heap in plain arrays.
	 */
	private static final class PathSearch
	{
		private final River river;
		private final int[] from;
		private final float[] best;
		// The leg each cell's best cost was found on; cells from older legs count as unreached.
		private final int[] reachedOn;
		private int leg;
		private int[] heapCells = new int[256];
		private float[] heapCosts = new float[256];
		private int heapSize;

		private PathSearch(River river)
		{
			this.river = river;
			int cells = river.width * river.height;
			from = new int[cells];
			best = new float[cells];
			reachedOn = new int[cells];
		}

		/**
		 * Finds the cheapest way from one cell to another; follow from back from the goal to the start.
		 */
		private void run(int start, int goal)
		{
			int width = river.width;
			int height = river.height;
			leg++;
			heapSize = 0;
			reach(start, 0, start);
			while (heapSize > 0)
			{
				float cost = heapCosts[0];
				int c = pop();
				if (cost > best[c])
				{
					continue;
				}
				if (c == goal)
				{
					return;
				}
				int i = c % width;
				int j = c / width;
				for (int dj = -1; dj <= 1; dj++)
				{
					for (int di = -1; di <= 1; di++)
					{
						int ni = i + di;
						int nj = j + dj;
						int n = nj * width + ni;
						if ((di == 0 && dj == 0) || ni < 0 || nj < 0 || ni >= width || nj >= height || !river.water[n])
						{
							continue;
						}
						float clear = Math.max(1, river.clearance[n]);
						float step = (float) ((di != 0 && dj != 0 ? DIAGONAL : CELL) / (clear * clear));
						if (reachedOn[n] != leg || cost + step < best[n])
						{
							reach(n, cost + step, c);
						}
					}
				}
			}
		}

		private void reach(int cell, float cost, int previous)
		{
			reachedOn[cell] = leg;
			best[cell] = cost;
			from[cell] = previous;
			if (heapSize == heapCells.length)
			{
				heapCells = Arrays.copyOf(heapCells, heapSize * 2);
				heapCosts = Arrays.copyOf(heapCosts, heapSize * 2);
			}
			// Sift up.
			int k = heapSize++;
			while (k > 0 && heapCosts[(k - 1) / 2] > cost)
			{
				heapCells[k] = heapCells[(k - 1) / 2];
				heapCosts[k] = heapCosts[(k - 1) / 2];
				k = (k - 1) / 2;
			}
			heapCells[k] = cell;
			heapCosts[k] = cost;
		}

		private int pop()
		{
			int top = heapCells[0];
			int lastCell = heapCells[--heapSize];
			float lastCost = heapCosts[heapSize];
			// Sift down.
			int k = 0;
			while (2 * k + 1 < heapSize)
			{
				int child = 2 * k + 1;
				if (child + 1 < heapSize && heapCosts[child + 1] < heapCosts[child])
				{
					child++;
				}
				if (heapCosts[child] >= lastCost)
				{
					break;
				}
				heapCells[k] = heapCells[child];
				heapCosts[k] = heapCosts[child];
				k = child;
			}
			heapCells[k] = lastCell;
			heapCosts[k] = lastCost;
			return top;
		}
	}
}
