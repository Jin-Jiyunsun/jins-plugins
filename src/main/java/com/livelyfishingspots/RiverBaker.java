package com.livelyfishingspots;

import com.livelyfishingspots.RiverSpotFish.River;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
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
 * TEMPORARY (debug, Bake rivers): records the water of each river and lake from the scene as the player walks, and
 * turns it into baked files: the water joined up from its first point, and a river's path down its middle. The game
 * itself only uses baked rivers and lakes; this is how they're made.
 */
@Slf4j
class RiverBaker
{
	private static final int CELL = RiverSpotFish.CELL;
	// Floor texture of river water, seen in game.
	private static final int WATER_TEXTURE = 1;
	// How far inside an object's rough outline a cell's middle must be to count as blocked, local units.
	private static final double OBJECT_INSET = 16;
	// How far a waypoint may move to the most open water near it, local units.
	private static final int WAYPOINT_SNAP = 256;
	// Smoothing passes over the path.
	private static final int SMOOTHING = 4;
	// Tiles walked before the water round the player is read again.
	private static final int RECORD_MOVE = 2;
	// Objects in the water that block movement but not fish: invisible blockers keeping ducks to a pond, waterfall
	// foam, and the parts of water wheels in the water.
	private static final Set<Integer> NOT_BLOCKING = Set.of(ObjectID.DUCKBLOCKER, ObjectID.WATERFALL_FOAM,
		ObjectID.WATERWHEEL_CENTRE, ObjectID.WATERWHEEL_LEFT, ObjectID.WATERWHEEL_RIGHT, ObjectID.WATERWHEEL_DRIP,
		ObjectID.KASTORI_WATERWHEEL_CENTRE, ObjectID.KASTORI_WATERWHEEL_LEFT, ObjectID.KASTORI_WATERWHEEL_RIGHT);
	// Hand-placed tiles, or halves of them, fish treat as land, where the water map gets it wrong.
	private static final Map<WorldPoint, Half> BLOCKERS = Map.ofEntries();

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
	// Water recorded, per river or lake, per world tile (x << 14 | y): a bit per cell, row by row.
	private final Map<WorldPoint[], Map<Integer, Integer>> recorded = new LinkedHashMap<>();
	// Fish blockers placed this session, on top of BLOCKERS.
	private final Map<WorldPoint, Half> placedBlockers = new HashMap<>();
	// Where the player was, and the scene's corner, when the water was last read.
	private WorldPoint readAt;
	private int readBaseX;
	private int readBaseY;

	RiverBaker(Client client, RiverSpotFish rivers)
	{
		this.client = client;
		this.rivers = rivers;
	}

	/**
	 * Once a game tick while baking: after a couple of tiles walked or a map load, reads the water of the tiles round
	 * the player (the square rivers are mapped within) near each river or lake, and records it; a tile read again
	 * keeps its latest reading.
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
		int reach = RiverSpotFish.FISH_RANGE + RiverSpotFish.MAP_MORE;
		int margin = RiverSpotFish.ROUTE_MARGIN;
		int lowX = Math.max(margin, at.getSceneX() - reach);
		int lowY = Math.max(margin, at.getSceneY() - reach);
		int highX = Math.min(view.getSizeX() - 1 - margin, at.getSceneX() + reach);
		int highY = Math.min(view.getSizeY() - 1 - margin, at.getSceneY() + reach);
		if (lowX > highX || lowY > highY)
		{
			return;
		}
		int perTile = 128 / CELL;
		int tilesX = highX - lowX + 1;
		int tilesY = highY - lowY + 1;
		River box = new River(lowX * 128, lowY * 128, tilesX * perTile, tilesY * perTile);
		// Which of its tiles each river or lake reaches: within BOX_MARGIN tiles of a river's line, or LAKE_RADIUS + 1
		// of a lake's spawn points.
		Map<WorldPoint[], boolean[]> reaches = new LinkedHashMap<>();
		boolean[] near = new boolean[tilesX * tilesY];
		for (WorldPoint[] route : rivers.routesAndLakes())
		{
			if (route[0].getPlane() != me.getPlane())
			{
				continue;
			}
			boolean lake = rivers.isLake(route);
			boolean[] mask = new boolean[tilesX * tilesY];
			boolean any = false;
			for (int ty = 0; ty < tilesY; ty++)
			{
				for (int tx = 0; tx < tilesX; tx++)
				{
					int x = view.getBaseX() + lowX + tx;
					int y = view.getBaseY() + lowY + ty;
					if (lake ? toSpawns(route, x, y) <= RiverSpotFish.LAKE_RADIUS + 1
						: toLine(route, x, y) <= RiverSpotFish.BOX_MARGIN)
					{
						mask[ty * tilesX + tx] = true;
						near[ty * tilesX + tx] = true;
						any = true;
					}
				}
			}
			if (any)
			{
				reaches.put(route, mask);
			}
		}
		if (reaches.isEmpty())
		{
			return;
		}
		boolean[] wet = wet(view, me.getPlane(), box, near);
		for (Map.Entry<WorldPoint[], boolean[]> entry : reaches.entrySet())
		{
			boolean[] mask = entry.getValue();
			Map<Integer, Integer> tiles = recorded.computeIfAbsent(entry.getKey(), r -> new HashMap<>());
			for (int ty = 0; ty < tilesY; ty++)
			{
				for (int tx = 0; tx < tilesX; tx++)
				{
					if (!mask[ty * tilesX + tx])
					{
						continue;
					}
					int bits = 0;
					for (int cy = 0; cy < perTile; cy++)
					{
						for (int cx = 0; cx < perTile; cx++)
						{
							if (wet[(ty * perTile + cy) * box.width + tx * perTile + cx])
							{
								bits |= 1 << (cy * perTile + cx);
							}
						}
					}
					tiles.put((view.getBaseX() + lowX + tx) << 14 | (view.getBaseY() + lowY + ty), bits);
				}
			}
		}
	}

	/**
	 * Tiles from a tile to a route's line through its points.
	 */
	private static double toLine(WorldPoint[] route, int x, int y)
	{
		double nearest = Double.MAX_VALUE;
		for (int k = 0; k + 1 < route.length; k++)
		{
			double ax = route[k].getX();
			double ay = route[k].getY();
			double bx = route[k + 1].getX() - ax;
			double by = route[k + 1].getY() - ay;
			double t = Math.max(0, Math.min(1, ((x - ax) * bx + (y - ay) * by) / Math.max(1, bx * bx + by * by)));
			nearest = Math.min(nearest, Math.hypot(x - ax - bx * t, y - ay - by * t));
		}
		return nearest;
	}

	/**
	 * Tiles from a tile to a lake's nearest spawn point.
	 */
	private static double toSpawns(WorldPoint[] lake, int x, int y)
	{
		double nearest = Double.MAX_VALUE;
		for (WorldPoint spawn : lake)
		{
			nearest = Math.min(nearest, Math.hypot(spawn.getX() - x, spawn.getY() - y));
		}
		return nearest;
	}

	/**
	 * Each recorded river and lake as a baked file's name and text: its water joined up from its first point (a lake's
	 * only within LAKE_RADIUS tiles of its spawn points), and a river's path through its points that were recorded.
	 * Lines are "water y x0 x1 ..." (runs of water cells in a row, end exclusive, world cells) and "path x y ..." (world
	 * local units).
	 */
	Map<String, String> save()
	{
		Map<String, String> files = new LinkedHashMap<>();
		int perTile = 128 / CELL;
		for (Map.Entry<WorldPoint[], Map<Integer, Integer>> entry : recorded.entrySet())
		{
			WorldPoint[] route = entry.getKey();
			boolean lake = rivers.isLake(route);
			Map<Integer, Integer> tiles = entry.getValue();
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
			// In world local units, so its cells are world cells.
			River river = new River(minX * 128, minY * 128, (maxX - minX + 1) * perTile, (maxY - minY + 1) * perTile);
			boolean[] wet = new boolean[river.width * river.height];
			for (Map.Entry<Integer, Integer> tile : tiles.entrySet())
			{
				int tx = (tile.getKey() >> 14) - minX;
				int ty = (tile.getKey() & 0x3FFF) - minY;
				for (int bit = 0; bit < perTile * perTile; bit++)
				{
					wet[(ty * perTile + bit / perTile) * river.width + tx * perTile + bit % perTile] =
						(tile.getValue() >> bit & 1) != 0;
				}
			}
			if (lake)
			{
				for (int c = 0; c < wet.length; c++)
				{
					double x = river.x0 + (c % river.width + 0.5) * CELL;
					double y = river.y0 + (c / river.width + 0.5) * CELL;
					wet[c] &= toSpawns(route, (int) Math.floor(x / 128), (int) Math.floor(y / 128))
						<= RiverSpotFish.LAKE_RADIUS;
				}
			}
			// Its points that were recorded: within two tiles of water.
			List<Integer> cells = new ArrayList<>();
			for (WorldPoint point : route)
			{
				LocalPoint at = new LocalPoint(point.getX() * 128 + 64, point.getY() * 128 + 64, -1);
				int cell = nearestAny(river, at, cells.isEmpty() ? wet : river.water);
				double x = river.x0 + (cell % river.width + 0.5) * CELL;
				double y = river.y0 + (cell / river.width + 0.5) * CELL;
				if (cell >= 0 && Math.hypot(x - at.getX(), y - at.getY()) <= 256)
				{
					if (cells.isEmpty())
					{
						flood(river, wet, cell);
					}
					cells.add(cell);
				}
			}
			if (cells.isEmpty())
			{
				log.debug("Bake {}: none of its points were recorded", route[0]);
				continue;
			}
			StringBuilder text = new StringBuilder();
			for (int j = 0; j < river.height; j++)
			{
				StringBuilder row = new StringBuilder();
				for (int i = 0; i < river.width; i++)
				{
					if (river.water[j * river.width + i] && (i == 0 || !river.water[j * river.width + i - 1]))
					{
						int end = i;
						while (end < river.width && river.water[j * river.width + end])
						{
							end++;
						}
						row.append(' ').append(minX * perTile + i).append(' ').append(minX * perTile + end);
					}
				}
				if (row.length() > 0)
				{
					text.append("water ").append(minY * perTile + j).append(row).append('\n');
				}
			}
			if (!lake && cells.size() >= 2)
			{
				RiverSpotFish.clearances(river);
				int[] stops = new int[cells.size()];
				for (int k = 0; k < stops.length; k++)
				{
					stops[k] = k == 0 || k == stops.length - 1 ? cells.get(k)
						: roomiest(river, cells.get(k), WAYPOINT_SNAP / CELL);
				}
				double[][] path = layPath(river, stops);
				// Every other point; spaced evenly again when used.
				StringBuilder line = new StringBuilder("path");
				int last = path[0].length - 1;
				for (int k = 0; k <= last; k += 2)
				{
					line.append(' ').append(Math.round(path[0][k])).append(' ').append(Math.round(path[1][k]));
					if (k % 40 == 38)
					{
						text.append(line).append('\n');
						line = new StringBuilder("path");
					}
				}
				if (last % 2 != 0)
				{
					line.append(' ').append(Math.round(path[0][last])).append(' ').append(Math.round(path[1][last]));
				}
				text.append(line).append('\n');
			}
			log.debug("Bake {}: {} of {} points recorded, {} tiles", route[0], cells.size(), route.length, tiles.size());
			files.put(RiverSpotFish.bakedName(route[0]), text.toString());
		}
		return files;
	}

	boolean isBlocker(WorldPoint tile)
	{
		return blockers().containsKey(tile);
	}

	/**
	 * Places a fish blocker on a tile, or a half of it, or removes the one there; logs them all, and reads the water
	 * round the player again, so the recording has it.
	 */
	void toggleBlocker(WorldPoint tile, Half half)
	{
		if (placedBlockers.remove(tile) == null)
		{
			placedBlockers.put(tile, half);
		}
		StringBuilder line = new StringBuilder();
		for (Map.Entry<WorldPoint, Half> blocker : blockers().entrySet())
		{
			WorldPoint at = blocker.getKey();
			line.append(line.length() > 0 ? ",\n\t\t" : "\n\t\t").append("Map.entry(new WorldPoint(").append(at.getX())
				.append(", ").append(at.getY()).append(", ").append(at.getPlane()).append("), Half.")
				.append(blocker.getValue()).append(")");
		}
		log.debug("Fish blockers: Map.ofEntries({})", line);
		readAt = null;
	}

	/**
	 * Draws each fish blocker, or the half of the tile it covers.
	 */
	void drawBlockers(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return;
		}
		graphics.setColor(Color.RED);
		for (Map.Entry<WorldPoint, Half> blocker : blockers().entrySet())
		{
			LocalPoint at = LocalPoint.fromWorld(view, blocker.getKey());
			if (at == null)
			{
				continue;
			}
			// The tile, or one half of it.
			Half half = blocker.getValue();
			int south = half == Half.NORTH ? 0 : -64;
			int north = half == Half.SOUTH ? 0 : 64;
			int west = half == Half.EAST ? 0 : -64;
			int east = half == Half.WEST ? 0 : 64;
			int plane = blocker.getKey().getPlane();
			Polygon area = new Polygon();
			for (int[] corner : new int[][]{{west, south}, {east, south}, {east, north}, {west, north}})
			{
				Point p = Perspective.localToCanvas(client,
					new LocalPoint(at.getX() + corner[0], at.getY() + corner[1], view.getId()), plane);
				if (p != null)
				{
					area.addPoint(p.getX(), p.getY());
				}
			}
			if (area.npoints == 4)
			{
				graphics.draw(area);
			}
		}
	}

	/**
	 * Every fish blocker: the locked-in ones and those placed this session.
	 */
	private Map<WorldPoint, Half> blockers()
	{
		if (placedBlockers.isEmpty())
		{
			return BLOCKERS;
		}
		Map<WorldPoint, Half> all = new HashMap<>(BLOCKERS);
		all.putAll(placedBlockers);
		return all;
	}

	/**
	 * Which grid cells are water, reading under bridges. Cells under the model of a visible object that blocks
	 * movement, as rocks and posts, aren't; walk-through ones, as lily pads, and invisible blockers don't count.
	 */
	private boolean[] wet(WorldView view, int plane, River river, boolean[] near)
	{
		Tile[][] tiles = view.getScene().getTiles()[plane];
		CollisionData[] maps = view.getCollisionMaps();
		int[][] flags = maps == null || maps[plane] == null ? null : maps[plane].getFlags();
		int perTile = 128 / CELL;
		int width = river.width;
		int tilesX = width / perTile;
		int tilesY = river.height / perTile;
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
				if (!near[ty * tilesX + tx] || sx < 0 || sy < 0 || sx >= tiles.length
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
				for (int cy = 0; cy < perTile; cy++)
				{
					for (int cx = 0; cx < perTile; cx++)
					{
						double x = sx * 128 + (cx + 0.5) * CELL;
						double y = sy * 128 + (cy + 0.5) * CELL;
						int c = (ty * perTile + cy) * width + tx * perTile + cx;
						wet[c] = paint != null ? paint.getTexture() == WATER_TEXTURE
							: textureAt(model, x, y) == WATER_TEXTURE;
						wetTile[ty * tilesX + tx] |= wet[c];
					}
				}
			}
		}
		// Cells under the model of each visible object in the water that blocks movement, and under fish blockers.
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
		for (Map.Entry<WorldPoint, Half> blocker : blockers().entrySet())
		{
			WorldPoint tile = blocker.getKey();
			int tx = tile.getX() - view.getBaseX() - tileX0;
			int ty = tile.getY() - view.getBaseY() - tileY0;
			if (tile.getPlane() != plane || tx < 0 || ty < 0 || tx >= tilesX || ty >= tilesY)
			{
				continue;
			}
			// North is the upper rows of cells, east the right columns.
			Half half = blocker.getValue();
			int fromRow = half == Half.NORTH ? perTile / 2 : 0;
			int toRow = half == Half.SOUTH ? perTile / 2 : perTile;
			int fromColumn = half == Half.EAST ? perTile / 2 : 0;
			int toColumn = half == Half.WEST ? perTile / 2 : perTile;
			for (int cy = fromRow; cy < toRow; cy++)
			{
				for (int cx = fromColumn; cx < toColumn; cx++)
				{
					solid[(ty * perTile + cy) * width + tx * perTile + cx] = true;
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
	 * The nearest marked cell to a place at any distance, or -1.
	 */
	private static int nearestAny(River river, LocalPoint at, boolean[] marked)
	{
		int width = river.width;
		int height = river.height;
		int ci = Math.max(0, Math.min(width - 1, (int) Math.floor((at.getX() - river.x0) / (double) CELL)));
		int cj = Math.max(0, Math.min(height - 1, (int) Math.floor((at.getY() - river.y0) / (double) CELL)));
		int nearestCell = -1;
		double nearest = Double.MAX_VALUE;
		// Square rings outwards; once one is found, a ring further out than it can't hold a nearer one.
		for (int ring = 0; ring < Math.max(width, height) && ring * CELL <= nearest + CELL; ring++)
		{
			for (int j = cj - ring; j <= cj + ring; j++)
			{
				for (int i = ci - ring; i <= ci + ring; i++)
				{
					boolean edge = Math.abs(i - ci) == ring || Math.abs(j - cj) == ring;
					if (!edge || i < 0 || j < 0 || i >= width || j >= height || !marked[j * width + i])
					{
						continue;
					}
					double d = Math.hypot(river.x0 + (i + 0.5) * CELL - at.getX(), river.y0 + (j + 0.5) * CELL - at.getY());
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
	 * The water cell with the most room within some cells of one.
	 */
	private static int roomiest(River river, int cell, int reach)
	{
		int width = river.width;
		int height = river.height;
		int best = cell;
		for (int j = Math.max(0, cell / width - reach); j <= Math.min(height - 1, cell / width + reach); j++)
		{
			for (int i = Math.max(0, cell % width - reach); i <= Math.min(width - 1, cell % width + reach); i++)
			{
				int c = j * width + i;
				if (river.water[c] && river.clearance[c] > river.clearance[best])
				{
					best = c;
				}
			}
		}
		return best;
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
						float step = (float) (CELL * (di != 0 && dj != 0 ? Math.sqrt(2) : 1) / (clear * clear));
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
