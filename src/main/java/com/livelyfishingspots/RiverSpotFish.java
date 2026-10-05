package com.livelyfishingspots;

import com.livelyfishingspots.FishModels.Look;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Fish swimming down the rivers the river fishing spots are on, along routes picked by hand. Each route has one
 * shoal, fixed in place while the spots move about it: its fish grow in at the route's start, swim down the river,
 * keeping to the water, and shrink away at its end. While the player fishes a spot, some of those passing it break off
 * to circle it, and drift back downstream one by one once the player stops. Only something to look at, as the fish at
 * sea are.
 */
@Slf4j
final class RiverSpotFish
{
	// The fish the lure and bait spots give: trout, salmon and pike, evenly.
	private static final int[] LURE_FISH = {ItemID.RAW_TROUT, ItemID.RAW_SALMON, ItemID.RAW_PIKE};
	// The river spots with fish, by their NPC: each spot's id is named for the map square it is in.
	private static final Map<Integer, int[]> SPOT_FISH = Map.ofEntries(
		Map.entry(NpcID._0_26_57_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_37_53_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_38_49_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_39_53_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_40_52_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_41_73_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_42_55_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_44_46_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_44_52_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_48_53_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_50_50_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_52_149_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_34_50_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_35_50_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_24_55_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_25_55_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_26_56_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_19_57_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_24_49_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_25_50_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_19_48_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_20_152_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_19_53_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_20_52_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_21_51_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_22_52_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_50_37_FRESHFISH, LURE_FISH),
		Map.entry(NpcID._0_49_38_FRESHFISH, LURE_FISH));
	// The fish each way of fishing a river spot catches, by its menu option, so only those are drawn to the circle; a
	// spot fished some other way draws all its fish.
	private static final Map<String, int[]> OPTION_FISH = Map.of(
		"lure", new int[]{ItemID.RAW_TROUT, ItemID.RAW_SALMON},
		"bait", new int[]{ItemID.RAW_PIKE});
	// The Fishing experience each river fish gives as it is caught, so a catch can be told from its experience drop;
	// one within CATCH_XP_SPREAD of it, as with the angler outfit's bonus, counts as it.
	private static final Map<Integer, Integer> CATCH_XP = Map.of(
		ItemID.RAW_TROUT, 50, ItemID.RAW_PIKE, 60, ItemID.RAW_SALMON, 70);
	private static final double CATCH_XP_SPREAD = 0.15;
	// Each river's route, picked by hand in game: where its fish grow in, upstream, then where they shrink away,
	// downstream. A spot near no route has no fish.
	private static final List<WorldPoint[]> ROUTES = List.<WorldPoint[]>of(
		// Barbarian Village.
		new WorldPoint[]{new WorldPoint(3104, 3443, 0), new WorldPoint(3105, 3417, 0)});
	// How far, in tiles, a spot may be from a route's line, start to end, to take it; and how many tiles of water are
	// mapped beyond the route's ends.
	private static final int ROUTE_REACH = 8;
	private static final int ROUTE_MARGIN = 4;
	// How long, in client ticks, a shoal stays with none of its spots in sight, so a spot moving, which takes it
	// away and puts it back elsewhere, doesn't start its shoal again.
	private static final int LINGER = 500;

	// The floor texture rivers are drawn with, as seen in game (RuneLite has no named ids for textures).
	private static final int WATER_TEXTURE = 1;
	// The grid a route's water is mapped on, CELL local units (128 to a tile) to a cell.
	private static final int CELL = 32;
	// The path: how far apart its points are, in local units, and how many times it is smoothed.
	private static final double PATH_STEP = 16;
	private static final int SMOOTHING = 4;
	// How far from the bank, in local units, a fish's middle keeps, for its width.
	private static final double BANK_GAP = 20;

	// How fast fish swim down the river and round a circle, in local units a client tick (20 ms), before their look's
	// speed; how far, in percent, each fish's own speed may differ from that; and how much faster and slower, as a
	// share of its speed, it surges as it swims, before its look's surge, over SURGE_CYCLES client ticks.
	private static final double TRAVEL_SPEED = 2.8;
	private static final double CIRCLE_SPEED = 2.0;
	private static final int SPEED_SPREAD = 30;
	private static final double SURGE = 0.25;
	private static final int SURGE_CYCLES = 300;
	// How far, as a share of the gap between them, each fish grows in early or late, so they don't come evenly.
	private static final double SPAWN_STRAY = 0.25;
	// How far ahead along its path, in local units, a fish steers for, and how fast it can turn, in radians a client
	// tick.
	private static final double LOOK_AHEAD = 48;
	private static final double TURN_RATE = 0.08;
	// How far apart, on average, in local units, the fish swimming down the river are.
	private static double TRAVEL_SPACING = 70;
	// How far from the path, at most, each fish's own line across the river is, and how far it wanders either side
	// of its own line, both as shares of the room there, together never past it: gliding to a new place picked at
	// random, then another, each glide taking between these, in client ticks, so no two wander alike.
	private static double SPREAD = 0.4;
	private static double WANDER = 0.25;
	private static final int MIN_WANDER_CYCLES = 100;
	private static final int MAX_WANDER_CYCLES = 300;
	// How long, in client ticks, a fish takes to grow in and to shrink away, and in how many sizes, each a model.
	private static final int GROW_CYCLES = 50;
	private static final int GROW_STEPS = 6;

	// The circle at a spot: its outer lane's radius, how many lanes it has and how far apart, all in local units, and
	// the most fish circling it.
	private static double CIRCLE_SIZE = 58;
	private static int CIRCLE_LANES = 2;
	private static double CIRCLE_LANE_SPACING = 16;
	private static int CIRCLE_MOST = 7;
	// How fast a circle's innermost lane swims, as a share of the outermost's, the lanes between evenly between.
	private static double INNER_LANE_SPEED = 0.4;
	// A circling fish counts as in its lane within LANE_ARRIVED of it, in local units, and eases to its lane's speed
	// over LANE_ARRIVING further out.
	private static final double LANE_ARRIVED = 16;
	private static final double LANE_ARRIVING = 32;
	// How far each circle's middle is from its spot, east and north, in local units.
	private static int CIRCLE_OFFSET_X = 26;
	private static int CIRCLE_OFFSET_Y = 0;
	// Fish not circling a spot pass it this far outside its circle, at least, in local units, as far as the river
	// allows, steering for that from CLEAR_AHEAD further off.
	private static double CIRCLE_CLEARANCE = 12;
	private static final double CLEAR_AHEAD = 48;
	// How far round the circle ahead, in local units, a circling fish steers for; and how much faster or slower, at
	// most, it swims to even out its gaps to the fish ahead and behind it, and how strongly.
	private static final double CIRCLE_LEAD = 40;
	private static final double SPACING_MOST = 0.5;
	private static final double SPACING_PULL = 0.6;
	// The chance, in percent, a fish passing a spot the player is fishing breaks off to circle it, and how far before
	// the spot, along the path, in local units, it decides; and how long, on average, in client ticks, each circling
	// fish stays once the player has stopped fishing.
	private static final int JOIN_CHANCE = 50;
	private static final double JOIN_BEFORE = 256;
	private static final double LEAVE_AFTER = 100;

	// As at sea: how far each fish's tail wags either side, in the game's 2048ths of a turn, at REFERENCE_SPEED or
	// faster, and how far it swims, in local units, for each wag there and back; how long each bob takes and holds
	// still for after, in client ticks; and how far it tips, at most, in degrees, in a dip and in a bob.
	private static final int WAG = 20;
	private static final double WAG_DISTANCE = 72;
	private static final double REFERENCE_SPEED = 3.2;
	private static int BOB_CYCLES = 60;
	private static int BOB_REST_CYCLES = 30;
	private static final double DIP_PITCH = 20;
	private static final double BOB_PITCH = 8;

	/**
	 * A route's water, on a grid of CELLs: which cells are the river's, how far each is from the bank, and the path
	 * down the middle of the river that the fish swim, upstream first, with the room either side of it.
	 */
	private static final class River
	{
		// The grid's south-west corner, in local units, and how many cells it is across.
		private final int x0;
		private final int y0;
		private final int size;
		private final boolean[] water;
		private final double[] clearance;
		// The path's points, in local units, how far along it each is, and how far a fish may go either side of it,
		// left and right as it faces downstream, to BANK_GAP from that bank.
		private double[] pathX;
		private double[] pathY;
		private double[] along;
		private double[] left;
		private double[] right;
		private double length;
		// The cells along the banks, kept for drawing them while debugging.
		private final List<int[]> banks = new ArrayList<>();

		private River(int x0, int y0, int size)
		{
			this.x0 = x0;
			this.y0 = y0;
			this.size = size;
			water = new boolean[size * size];
			clearance = new double[size * size];
		}

		/**
		 * How far a place is from the bank, in local units; 0 off the water or the grid.
		 */
		private double clearanceAt(double x, double y)
		{
			int i = (int) Math.floor((x - x0) / CELL);
			int j = (int) Math.floor((y - y0) / CELL);
			return i >= 0 && j >= 0 && i < size && j < size ? clearance[j * size + i] : 0;
		}

		private boolean isWater(double x, double y)
		{
			int i = (int) Math.floor((x - x0) / CELL);
			int j = (int) Math.floor((y - y0) / CELL);
			return i >= 0 && j >= 0 && i < size && j < size && water[j * size + i];
		}

		/**
		 * The path's point at a distance along it, held at its ends, and its way along there: x, y, the way's x and
		 * y, and the room to its left and right.
		 */
		private void at(double s, double[] into)
		{
			s = Math.max(0, Math.min(length, s));
			int k = Arrays.binarySearch(along, s);
			k = k >= 0 ? k : Math.max(0, -k - 2);
			k = Math.min(k, along.length - 2);
			double t = (s - along[k]) / Math.max(1e-6, along[k + 1] - along[k]);
			double dx = pathX[k + 1] - pathX[k];
			double dy = pathY[k + 1] - pathY[k];
			double d = Math.max(1e-6, Math.hypot(dx, dy));
			into[0] = pathX[k] + dx * t;
			into[1] = pathY[k] + dy * t;
			into[2] = dx / d;
			into[3] = dy / d;
			into[4] = left[k] + (left[k + 1] - left[k]) * t;
			into[5] = right[k] + (right[k + 1] - right[k]) * t;
		}

		/**
		 * How far along the path its point nearest a place is.
		 */
		private double nearest(double x, double y)
		{
			return nearest(x, y, 0, length);
		}

		/**
		 * How far along the path its point nearest a place is, of those between two distances along it.
		 */
		private double nearest(double x, double y, double from, double to)
		{
			double best = Double.MAX_VALUE;
			double s = from;
			// The path's points are evenly spaced, so where to look is known without searching.
			double step = length / Math.max(1, pathX.length - 1);
			int first = Math.max(0, (int) (from / step) - 1);
			int last = Math.min(pathX.length - 1, (int) Math.ceil(to / step) + 1);
			for (int k = first; k <= last; k++)
			{
				double d = (pathX[k] - x) * (pathX[k] - x) + (pathY[k] - y) * (pathY[k] - y);
				if (d < best)
				{
					best = d;
					s = along[k];
				}
			}
			return s;
		}
	}

	/**
	 * One route's fish: its river, the circle at each of its spots in sight, and its fish.
	 */
	private static final class Shoal
	{
		private final WorldPoint[] route;
		private final int plane;
		private final int worldView;
		private final River river;
		private final int[] kinds;
		private final int travelling;
		private final Map<NPC, Circle> circles = new HashMap<>();
		private final List<Swimmer> fish = new ArrayList<>();
		private int nextSpawn;
		private int movedAt;
		// The client tick its last spot went out of sight at, or -1 while it has some.
		private int emptySince = -1;

		private Shoal(WorldPoint[] route, int plane, int worldView, River river, int[] kinds, int cycle)
		{
			this.route = route;
			this.plane = plane;
			this.worldView = worldView;
			this.river = river;
			this.kinds = kinds;
			travelling = Math.max(1, (int) Math.round(river.length / TRAVEL_SPACING));
			movedAt = cycle;
		}
	}

	/**
	 * The circle at one spot, where its fish gather while the player fishes it.
	 */
	private static final class Circle
	{
		private final NPC npc;
		private final WorldPoint spot;
		// Its middle and outer lane's radius, in local units, how far along the path it is, and which way round its
		// fish swim, 1 or -1; how far out each of its lanes is, innermost first.
		private final double x;
		private final double y;
		private final double radius;
		private final double along;
		private final int way;
		private final double[] lanes;
		// How far across the river from the path its middle is, as fish are, in local units, left when more than 0,
		// and the room to the path's left and right there.
		private final double across;
		private final double roomLeft;
		private final double roomRight;

		private Circle(NPC npc, River river, double x, double y, int way)
		{
			this.npc = npc;
			spot = npc.getWorldLocation();
			this.x = x;
			this.y = y;
			this.way = way;
			radius = CIRCLE_SIZE;
			lanes = new double[CIRCLE_LANES];
			for (int lane = 0; lane < CIRCLE_LANES; lane++)
			{
				lanes[lane] = Math.max(8, radius - (CIRCLE_LANES - 1 - lane) * CIRCLE_LANE_SPACING);
			}
			along = river.nearest(x, y);
			double[] at = new double[6];
			river.at(along, at);
			across = (x - at[0]) * -at[3] + (y - at[1]) * at[2];
			roomLeft = at[4];
			roomRight = at[5];
		}
	}

	private static final class Swimmer
	{
		private final RuneLiteObject fish;
		private final int item;
		private final Look look;
		// Where it is, in local units, the way it faces, in radians from east towards north, and how far along the
		// path it has got.
		private double x;
		private double y;
		private double facing;
		private double s;
		// The point it last steered for, kept for drawing while debugging.
		private double targetX;
		private double targetY;
		// The circle it is swimming round, and in which of its lanes, or null while it swims down the river; and the
		// circles it has decided about joining, passing each.
		private Circle circle;
		private int circleLane;
		private final Set<Circle> decided = new HashSet<>();
		// The circle it is passing, while near one and not circling it, and which side of it, across the river, 1 for
		// left or -1; null and 0 while far from any.
		private Circle passing;
		private int passSide;
		// Its own share of the speed, its place across the river, as a share of the room on that side, when its surge
		// is, and how fast it is going now.
		private final double speed;
		private final double across;
		private final int surgePhase;
		// Its wander: where it glides from and to, from -1 to 1, the client tick it set off at and how long it takes.
		private double wanderFrom;
		private double wanderTo;
		private int wanderSince;
		private int wanderTakes = 1;
		private double swimming;
		// How far across the river from the path it steers now, in local units, left when more than 0.
		private double lateral;
		// The client tick it started growing in at, and shrinking away at, or -1 while it isn't; the size step it
		// shows and should show; how far its model is tipped, in PITCH_STEPs, nose up when more than 0; and the
		// client tick it started dipping at, or -1 while it isn't.
		private int growingSince = -1;
		private int shrinkingSince = -1;
		private int step;
		private int wantStep;
		private int pitch;
		private int dippingSince = -1;
		// Whether it has been caught, and shrinks away once in its lane of its circle.
		private boolean caught;
		private double wag;
		// How far into its bobbing it is, in client ticks, which stands still while it dips, so the two never overlap.
		private int bobClock;

		private Swimmer(RuneLiteObject fish, int item, ThreadLocalRandom random)
		{
			this.fish = fish;
			this.item = item;
			look = FishModels.look(item);
			speed = (100 + random.nextInt(-SPEED_SPREAD, SPEED_SPREAD + 1)) / 100.0;
			across = random.nextDouble(-1, 1) * SPREAD;
			wanderTo = random.nextDouble(-1, 1);
			surgePhase = random.nextInt(SURGE_CYCLES);
			wag = random.nextDouble(2 * Math.PI);
			bobClock = random.nextInt(BOB_CYCLES + BOB_REST_CYCLES);
		}
	}

	private final Client client;
	private final FishModels models;
	private final List<Shoal> shoals = new ArrayList<>();
	// The spot the player last chose a way of fishing on, from the menu, and the fish that way catches, or null for
	// all its fish.
	private NPC chosenSpot;
	private int[] chosenFish;
	// Routes picked in game while debugging, tried before the table's.
	private final List<WorldPoint[]> picked = new ArrayList<>();
	// Reused for the path's point at a place along it.
	private final double[] point = new double[6];

	RiverSpotFish(Client client, FishModels models)
	{
		this.client = client;
		this.models = models;
	}

	/**
	 * Puts a river spot that has come into sight, or moved, on its route's shoal, starting the shoal if it has none
	 * yet.
	 */
	void add(NPC spot)
	{
		int[] kinds = SPOT_FISH.get(spot.getId());
		if (kinds == null || shoalOf(spot) != null)
		{
			return;
		}
		LocalPoint at = spot.getLocalLocation();
		WorldView view = client.getTopLevelWorldView();
		WorldPoint[] route = routeFor(spot.getWorldLocation());
		if (at == null || view == null || route == null)
		{
			return;
		}
		for (Shoal shoal : shoals)
		{
			if (shoal.route == route)
			{
				attach(shoal, spot, at);
				return;
			}
		}
		int plane = spot.getWorldLocation().getPlane();
		River river = mapRoute(view, route, plane);
		if (river == null)
		{
			return;
		}
		// Every size each kind grows through, made now, so none waits; and, full grown, every tip it may come to, to be
		// made a few at a time.
		for (int item : kinds)
		{
			for (int step = 1; step <= GROW_STEPS; step++)
			{
				if (models.model(item, size(item, step), 0, 0) == null)
				{
					return;
				}
			}
			Look look = FishModels.look(item);
			double most = look.tip / 100.0 * ((look.rise > 0 ? BOB_PITCH : 0) + (look.dipDepth > 0 ? DIP_PITCH : 0));
			int steps = (int) Math.ceil(most / FishModels.PITCH_STEP);
			for (int pitch = -steps; pitch <= steps; pitch++)
			{
				models.queue(item, size(item, GROW_STEPS), pitch, 0, false);
			}
		}
		int cycle = client.getGameCycle();
		Shoal shoal = new Shoal(route, plane, view.getId(), river, kinds, cycle);
		log.debug("River route {} to {}: path {} long, {} fish", route[0], route[1], (int) river.length,
			shoal.travelling);
		attach(shoal, spot, at);
		shoals.add(shoal);
		// Fish already all along the river, evenly, each a little either way, so it isn't empty as it comes into sight.
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int n = 0; n < shoal.travelling; n++)
		{
			double gap = river.length / shoal.travelling;
			spawn(shoal, (n + 0.5 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY)) * gap, false, cycle, random);
		}
		shoal.nextSpawn = cycle + spawnGap(random);
	}

	/**
	 * The shoal a spot is on, or null.
	 */
	private Shoal shoalOf(NPC spot)
	{
		for (Shoal shoal : shoals)
		{
			if (shoal.circles.containsKey(spot))
			{
				return shoal;
			}
		}
		return null;
	}

	/**
	 * The route a spot takes, the picked ones first: the nearest whose line, start to end, it is within ROUTE_REACH
	 * tiles of; null for none.
	 */
	private WorldPoint[] routeFor(WorldPoint spot)
	{
		WorldPoint[] best = null;
		double nearest = ROUTE_REACH;
		List<WorldPoint[]> all = new ArrayList<>(picked);
		all.addAll(ROUTES);
		for (WorldPoint[] route : all)
		{
			if (route[0].getPlane() != spot.getPlane())
			{
				continue;
			}
			double ax = route[0].getX();
			double ay = route[0].getY();
			double bx = route[1].getX() - ax;
			double by = route[1].getY() - ay;
			double t = Math.max(0, Math.min(1, ((spot.getX() - ax) * bx + (spot.getY() - ay) * by)
				/ Math.max(1, bx * bx + by * by)));
			double d = Math.hypot(spot.getX() - ax - bx * t, spot.getY() - ay - by * t);
			if (d <= nearest)
			{
				nearest = d;
				best = route;
			}
		}
		return best;
	}

	/**
	 * Gives a spot on a shoal its circle, on the spot, moved by the circle offset.
	 */
	private void attach(Shoal shoal, NPC spot, LocalPoint at)
	{
		Circle circle = new Circle(spot, shoal.river, at.getX() + CIRCLE_OFFSET_X, at.getY() + CIRCLE_OFFSET_Y,
			ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
		shoal.circles.put(spot, circle);
		shoal.emptySince = -1;
		// Fish already past where they would have decided about it have missed their chance.
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.s > circle.along - JOIN_BEFORE)
			{
				swimmer.decided.add(circle);
			}
		}
	}

	/**
	 * Maps the water along a route, from its start to its end and ROUTE_MARGIN tiles beyond, and the path down the
	 * middle of the river between them; null, logged, if either end is out of sight, or they aren't in the same water.
	 */
	private River mapRoute(WorldView view, WorldPoint[] route, int plane)
	{
		Scene scene = view.getScene();
		LocalPoint start = LocalPoint.fromWorld(view, route[0]);
		LocalPoint end = LocalPoint.fromWorld(view, route[1]);
		if (scene == null || start == null || end == null)
		{
			log.debug("River route {} to {}: not all in sight", route[0], route[1]);
			return null;
		}
		int reach = Math.max(Math.abs(start.getSceneX() - end.getSceneX()),
			Math.abs(start.getSceneY() - end.getSceneY())) / 2 + ROUTE_MARGIN;
		int middleX = (start.getSceneX() + end.getSceneX()) / 2;
		int middleY = (start.getSceneY() + end.getSceneY()) / 2;
		River river = new River((middleX - reach) * 128, (middleY - reach) * 128, (2 * reach + 1) * (128 / CELL));
		boolean[] wet = wet(scene, plane, river);
		int from = nearestWater(river, start, wet);
		if (from < 0)
		{
			log.debug("River route {} to {}: no water within a tile of the start", route[0], route[1]);
			return null;
		}
		flood(river, wet, from);
		// The end is taken to the nearest water joined to the start's, not just any water.
		int to = nearestWater(river, end, river.water);
		if (to < 0 || to == from)
		{
			log.debug("River route {} to {}: the end isn't in the start's water", route[0], route[1]);
			return null;
		}
		layPath(river, from, to);
		return river;
	}

	/**
	 * The cell nearest a place, within a tile of it, of those marked; -1 for none.
	 */
	private static int nearestWater(River river, LocalPoint at, boolean[] marked)
	{
		int nearestCell = -1;
		double nearest = Double.MAX_VALUE;
		for (int j = 0; j < river.size; j++)
		{
			for (int i = 0; i < river.size; i++)
			{
				double d = Math.hypot(river.x0 + (i + 0.5) * CELL - at.getX(), river.y0 + (j + 0.5) * CELL - at.getY());
				if (marked[j * river.size + i] && d <= 128 && d < nearest)
				{
					nearest = d;
					nearestCell = j * river.size + i;
				}
			}
		}
		return nearestCell;
	}

	/**
	 * Which of a river grid's cells are water, from the floor's textures in the scene: under a bridge, from the
	 * floor under it, which the scene keeps apart from the bridge.
	 */
	private static boolean[] wet(Scene scene, int plane, River river)
	{
		Tile[][] tiles = scene.getTiles()[plane];
		int perTile = 128 / CELL;
		int size = river.size;
		boolean[] wet = new boolean[size * size];
		int tileX0 = Math.floorDiv(river.x0, 128);
		int tileY0 = Math.floorDiv(river.y0, 128);
		for (int ty = 0; ty < size / perTile; ty++)
		{
			for (int tx = 0; tx < size / perTile; tx++)
			{
				int sx = tileX0 + tx;
				int sy = tileY0 + ty;
				if (sx < 0 || sy < 0 || sx >= tiles.length || sy >= tiles[sx].length || tiles[sx][sy] == null)
				{
					continue;
				}
				Tile tile = tiles[sx][sy].getBridge() != null ? tiles[sx][sy].getBridge() : tiles[sx][sy];
				SceneTilePaint paint = tile.getSceneTilePaint();
				SceneTileModel model = tile.getSceneTileModel();
				for (int cy = 0; cy < perTile; cy++)
				{
					for (int cx = 0; cx < perTile; cx++)
					{
						double x = sx * 128 + (cx + 0.5) * CELL;
						double y = sy * 128 + (cy + 0.5) * CELL;
						wet[(ty * perTile + cy) * size + tx * perTile + cx] = paint != null
							? paint.getTexture() == WATER_TEXTURE
							: model != null && textureAt(model, x, y) == WATER_TEXTURE;
					}
				}
			}
		}
		return wet;
	}

	/**
	 * The texture of a shaped tile's face under a place, in local units; -1 for none.
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
	 * Marks as the river's water every wet cell joined to one, flooding out from it, then works out how far each is
	 * from the bank, and where the banks are.
	 */
	private static void flood(River river, boolean[] wet, int start)
	{
		int size = river.size;
		int[] stack = new int[size * size];
		int top = 0;
		stack[top++] = start;
		river.water[start] = true;
		while (top > 0)
		{
			int c = stack[--top];
			int i = c % size;
			int j = c / size;
			for (int dj = -1; dj <= 1; dj++)
			{
				for (int di = -1; di <= 1; di++)
				{
					int ni = i + di;
					int nj = j + dj;
					int n = nj * size + ni;
					if (ni >= 0 && nj >= 0 && ni < size && nj < size && wet[n] && !river.water[n])
					{
						river.water[n] = true;
						stack[top++] = n;
					}
				}
			}
		}
		clearances(river);
		for (int c = 0; c < size * size; c++)
		{
			if (river.water[c] && river.clearance[c] <= CELL)
			{
				river.banks.add(new int[]{river.x0 + (c % size) * CELL + CELL / 2, river.y0 + (c / size) * CELL + CELL / 2});
			}
		}
	}

	/**
	 * How far each water cell is from the nearest cell that isn't, in local units, by two sweeps over the grid; the
	 * grid's edges count as more water.
	 */
	private static void clearances(River river)
	{
		int size = river.size;
		double[] d = river.clearance;
		double diagonal = CELL * Math.sqrt(2);
		for (int c = 0; c < d.length; c++)
		{
			d[c] = river.water[c] ? Double.MAX_VALUE / 4 : 0;
		}
		for (int j = 0; j < size; j++)
		{
			for (int i = 0; i < size; i++)
			{
				int c = j * size + i;
				if (i > 0)
				{
					d[c] = Math.min(d[c], d[c - 1] + CELL);
				}
				if (j > 0)
				{
					d[c] = Math.min(d[c], d[c - size] + CELL);
					if (i > 0)
					{
						d[c] = Math.min(d[c], d[c - size - 1] + diagonal);
					}
					if (i < size - 1)
					{
						d[c] = Math.min(d[c], d[c - size + 1] + diagonal);
					}
				}
			}
		}
		for (int j = size - 1; j >= 0; j--)
		{
			for (int i = size - 1; i >= 0; i--)
			{
				int c = j * size + i;
				if (i < size - 1)
				{
					d[c] = Math.min(d[c], d[c + 1] + CELL);
				}
				if (j < size - 1)
				{
					d[c] = Math.min(d[c], d[c + size] + CELL);
					if (i < size - 1)
					{
						d[c] = Math.min(d[c], d[c + size + 1] + diagonal);
					}
					if (i > 0)
					{
						d[c] = Math.min(d[c], d[c + size - 1] + diagonal);
					}
				}
			}
		}
		// Measured to the edge of the cell beside, not its middle.
		for (int c = 0; c < d.length; c++)
		{
			d[c] = river.water[c] ? Math.min(d[c], 1e6) - CELL / 2.0 : 0;
		}
	}

	/**
	 * Lays the path from one water cell, upstream, to another, down the middle of the river: the way through the
	 * water keeping furthest from both banks, smoothed and laid out evenly; and, at each of its points, the room either
	 * side, out to each bank, less BANK_GAP.
	 */
	private static void layPath(River river, int upstream, int downstream)
	{
		int size = river.size;
		int[] from = new int[size * size];
		search(river, upstream, from);
		List<double[]> cells = new ArrayList<>();
		for (int c = downstream; c != upstream; c = from[c])
		{
			cells.add(0, new double[]{river.x0 + (c % size + 0.5) * CELL, river.y0 + (c / size + 0.5) * CELL});
		}
		cells.add(0, new double[]{river.x0 + (upstream % size + 0.5) * CELL, river.y0 + (upstream / size + 0.5) * CELL});
		// Smoothed, the ends held, then laid out evenly.
		for (int pass = 0; pass < SMOOTHING; pass++)
		{
			List<double[]> smoothed = new ArrayList<>();
			smoothed.add(cells.get(0));
			for (int p = 1; p < cells.size() - 1; p++)
			{
				double[] a = cells.get(p - 1);
				double[] b = cells.get(p);
				double[] c = cells.get(p + 1);
				smoothed.add(new double[]{(a[0] + 2 * b[0] + c[0]) / 4, (a[1] + 2 * b[1] + c[1]) / 4});
			}
			smoothed.add(cells.get(cells.size() - 1));
			cells = smoothed;
		}
		double[][] path = even(cells);
		int points = path.length;
		river.pathX = new double[points];
		river.pathY = new double[points];
		river.along = new double[points];
		river.left = new double[points];
		river.right = new double[points];
		double walked = 0;
		for (int p = 0; p < points; p++)
		{
			walked += p == 0 ? 0 : Math.hypot(path[p][0] - path[p - 1][0], path[p][1] - path[p - 1][1]);
			river.pathX[p] = path[p][0];
			river.pathY[p] = path[p][1];
			river.along[p] = walked;
			double[] across = across(path, p);
			river.left[p] = Math.max(0, outTo(river, path[p][0], path[p][1], across[0], across[1]) - BANK_GAP);
			river.right[p] = Math.max(0, outTo(river, path[p][0], path[p][1], -across[0], -across[1]) - BANK_GAP);
		}
		river.length = walked;
	}

	/**
	 * Lays points out evenly along the line through them, PATH_STEP apart.
	 */
	private static double[][] even(List<double[]> line)
	{
		double total = 0;
		for (int k = 1; k < line.size(); k++)
		{
			total += Math.hypot(line.get(k)[0] - line.get(k - 1)[0], line.get(k)[1] - line.get(k - 1)[1]);
		}
		int points = Math.max(2, (int) Math.ceil(total / PATH_STEP) + 1);
		double[][] even = new double[points][];
		int k = 0;
		double walked = 0;
		for (int p = 0; p < points; p++)
		{
			double want = p * total / (points - 1);
			while (k < line.size() - 2 && walked + Math.hypot(line.get(k + 1)[0] - line.get(k)[0],
				line.get(k + 1)[1] - line.get(k)[1]) < want)
			{
				walked += Math.hypot(line.get(k + 1)[0] - line.get(k)[0], line.get(k + 1)[1] - line.get(k)[1]);
				k++;
			}
			double[] a = line.get(k);
			double[] b = line.get(Math.min(k + 1, line.size() - 1));
			double step = Math.max(1e-6, Math.hypot(b[0] - a[0], b[1] - a[1]));
			double t = Math.max(0, Math.min(1, (want - walked) / step));
			even[p] = new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
		}
		return even;
	}

	/**
	 * The way straight across a path at one of its points, to its left as it runs downstream.
	 */
	private static double[] across(double[][] path, int p)
	{
		int q = Math.min(p + 1, path.length - 1);
		int o = Math.max(p - 1, 0);
		double dx = path[q][0] - path[o][0];
		double dy = path[q][1] - path[o][1];
		double d = Math.max(1e-6, Math.hypot(dx, dy));
		return new double[]{-dy / d, dx / d};
	}

	/**
	 * How far a line out from a place, a way, stays in the river's water, in local units.
	 */
	private static double outTo(River river, double x, double y, double wayX, double wayY)
	{
		double out = 0;
		while (out < 2048 && river.isWater(x + wayX * (out + 4), y + wayY * (out + 4)))
		{
			out += 4;
		}
		return out;
	}

	/**
	 * The cheapest way through the water to each cell from one, each step costing more the nearer the bank it is, by
	 * the square, so the cheapest way runs along the middle, as far from both banks as it can; noting in from the cell
	 * each is reached from.
	 */
	private static void search(River river, int start, int[] from)
	{
		int size = river.size;
		double[] best = new double[size * size];
		Arrays.fill(best, Double.MAX_VALUE);
		best[start] = 0;
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		open.add(new double[]{0, start});
		while (!open.isEmpty())
		{
			double[] next = open.poll();
			int c = (int) next[1];
			if (next[0] > best[c])
			{
				continue;
			}
			int i = c % size;
			int j = c / size;
			for (int dj = -1; dj <= 1; dj++)
			{
				for (int di = -1; di <= 1; di++)
				{
					int ni = i + di;
					int nj = j + dj;
					int n = nj * size + ni;
					if ((di == 0 && dj == 0) || ni < 0 || nj < 0 || ni >= size || nj >= size || !river.water[n])
					{
						continue;
					}
					double clear = Math.max(1, river.clearance[n]);
					double step = CELL * (di != 0 && dj != 0 ? Math.sqrt(2) : 1) / (clear * clear);
					if (best[c] + step < best[n])
					{
						best[n] = best[c] + step;
						from[n] = c;
						open.add(new double[]{best[n], n});
					}
				}
			}
		}
	}

	/**
	 * A kind's size at a step of growing in, in percent.
	 */
	private static int size(int item, int step)
	{
		return Math.max(1, FishModels.look(item).size * step / GROW_STEPS);
	}

	/**
	 * How long, in client ticks, until the next fish grows in at a shoal's start: the time a fish takes to swim
	 * TRAVEL_SPACING, give or take SPAWN_STRAY of it.
	 */
	private static int spawnGap(ThreadLocalRandom random)
	{
		return (int) Math.max(1, TRAVEL_SPACING / TRAVEL_SPEED * (1 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY)));
	}

	/**
	 * Puts a new fish on a shoal's path, a distance along it, growing in or already grown.
	 */
	private void spawn(Shoal shoal, double s, boolean grow, int cycle, ThreadLocalRandom random)
	{
		int item = shoal.kinds[random.nextInt(shoal.kinds.length)];
		int step = grow ? 1 : GROW_STEPS;
		Model model = models.model(item, size(item, step), 0, 0);
		if (model == null)
		{
			return;
		}
		RuneLiteObject fish = client.createRuneLiteObject();
		fish.setModel(model);
		Swimmer swimmer = new Swimmer(fish, item, random);
		swimmer.step = step;
		swimmer.wantStep = step;
		swimmer.s = s;
		swimmer.growingSince = grow ? cycle : -1;
		// Past a spot already, it has missed its chance to join it.
		for (Circle circle : shoal.circles.values())
		{
			if (s > circle.along - JOIN_BEFORE)
			{
				swimmer.decided.add(circle);
			}
		}
		shoal.river.at(s, point);
		double offset = share(swimmer.across, point);
		swimmer.x = point[0] - point[3] * offset;
		swimmer.y = point[1] + point[2] * offset;
		swimmer.facing = Math.atan2(point[3], point[2]);
		swimmer.swimming = TRAVEL_SPEED * swimmer.speed;
		fish.setLocation(new LocalPoint((int) swimmer.x, (int) swimmer.y, shoal.worldView), shoal.plane);
		place(shoal, swimmer, cycle);
		fish.setActive(true);
		shoal.fish.add(swimmer);
	}

	/**
	 * How far across the river from the path a share of the room is, in local units, left when more than 0: of the
	 * room to the left for a share above 0, of the room to the right below.
	 */
	private static double share(double share, double[] at)
	{
		return share >= 0 ? share * at[4] : share * at[5];
	}

	/**
	 * Takes a spot that has gone out of sight off its shoal: the fish circling it carry on downstream, and the shoal
	 * stays a while, in case the spot has only moved.
	 */
	void remove(NPC spot)
	{
		Shoal shoal = shoalOf(spot);
		if (shoal == null)
		{
			return;
		}
		Circle circle = shoal.circles.remove(spot);
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.circle == circle)
			{
				leave(shoal, swimmer);
			}
		}
		if (shoal.circles.isEmpty())
		{
			shoal.emptySince = client.getGameCycle();
		}
	}

	/**
	 * Sends a circling fish back to the river where it leaves its circle, carrying on downstream.
	 */
	private static void leave(Shoal shoal, Swimmer swimmer)
	{
		swimmer.circle = null;
		swimmer.s = Math.max(swimmer.s, shoal.river.nearest(swimmer.x, swimmer.y));
	}

	/**
	 * Adds the kinds swimming at the spots in sight.
	 */
	void addKinds(Set<Integer> swimming)
	{
		for (Shoal shoal : shoals)
		{
			shoal.fish.forEach(swimmer -> swimming.add(swimmer.item));
		}
	}

	/**
	 * Moves every fish on, each client tick.
	 */
	void swim()
	{
		if (shoals.isEmpty())
		{
			return;
		}
		int cycle = client.getGameCycle();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Player player = client.getLocalPlayer();
		Actor fishing = player == null ? null : player.getInteracting();
		List<NPC> moved = new ArrayList<>();
		for (Iterator<Shoal> all = shoals.iterator(); all.hasNext(); )
		{
			Shoal shoal = all.next();
			// Gone a while with none of its spots in sight.
			if (shoal.emptySince >= 0 && cycle - shoal.emptySince > LINGER)
			{
				shoal.fish.forEach(swimmer -> swimmer.fish.setActive(false));
				all.remove();
				continue;
			}
			// A spot that has moved is taken off and put back on where it now is.
			for (Circle circle : shoal.circles.values())
			{
				if (!circle.npc.getWorldLocation().equals(circle.spot))
				{
					moved.add(circle.npc);
				}
			}
			int ticks = Math.min(10, cycle - shoal.movedAt);
			shoal.movedAt = cycle;
			if (ticks <= 0)
			{
				continue;
			}
			int travelling = 0;
			Map<Circle, Integer> circling = new HashMap<>();
			for (Swimmer swimmer : shoal.fish)
			{
				if (swimmer.circle != null && swimmer.shrinkingSince < 0)
				{
					circling.merge(swimmer.circle, 1, Integer::sum);
				}
				else if (swimmer.shrinkingSince < 0)
				{
					travelling++;
				}
			}
			// A steady flow, however many are swimming, so no gaps open; held back only if far too many have gathered.
			if (cycle >= shoal.nextSpawn)
			{
				if (travelling < 2 * shoal.travelling)
				{
					spawn(shoal, 0, true, cycle, random);
				}
				shoal.nextSpawn = Math.max(shoal.nextSpawn + spawnGap(random), cycle);
			}
			for (Iterator<Swimmer> it = shoal.fish.iterator(); it.hasNext(); )
			{
				Swimmer swimmer = it.next();
				if (swimmer.circle == null)
				{
					for (Circle circle : shoal.circles.values())
					{
						if (swimmer.circle == null && !swimmer.decided.contains(circle)
							&& swimmer.s >= circle.along - JOIN_BEFORE)
						{
							swimmer.decided.add(circle);
							if (fishing == circle.npc && drawn(circle, swimmer.item) && fewest(shoal, circle, swimmer.item)
								&& circling.getOrDefault(circle, 0) < CIRCLE_MOST && random.nextInt(100) < JOIN_CHANCE)
							{
								swimmer.circle = circle;
								swimmer.circleLane = roomiestLane(shoal, circle);
								circling.merge(circle, 1, Integer::sum);
							}
						}
					}
				}
				else if (swimmer.shrinkingSince < 0 && (fishing != swimmer.circle.npc || !drawn(swimmer.circle, swimmer.item))
					&& random.nextDouble() < ticks / LEAVE_AFTER)
				{
					leave(shoal, swimmer);
				}
				// Now and then it dips deeper, down and back up, as its kind does: only while resting between bobs, its
				// bobbing standing still until the dip is over, so the two never overlap.
				Look look = swimmer.look;
				boolean resting = swimmer.bobClock % (BOB_CYCLES + BOB_REST_CYCLES) >= BOB_CYCLES;
				if (swimmer.dippingSince >= 0 ? cycle - swimmer.dippingSince >= Math.max(1, look.dipMillis / 20)
					: resting && look.dipDepth > 0 && random.nextDouble() < ticks / (look.dipEvery * 50.0))
				{
					swimmer.dippingSince = swimmer.dippingSince >= 0 ? -1 : cycle;
				}
				if (swimmer.dippingSince < 0)
				{
					swimmer.bobClock += ticks;
				}
				if (!move(shoal, swimmer, ticks, cycle))
				{
					swimmer.fish.setActive(false);
					it.remove();
				}
			}
		}
		for (NPC npc : moved)
		{
			remove(npc);
			add(npc);
		}
	}

	/**
	 * Notes the way of fishing the player chose on a spot's menu, read only, so the circle there draws only the fish
	 * that way catches.
	 */
	void chose(NPC spot, String option)
	{
		if (spot != null && SPOT_FISH.containsKey(spot.getId()))
		{
			chosenSpot = spot;
			chosenFish = option == null ? null : OPTION_FISH.get(option.toLowerCase());
		}
	}

	/**
	 * Whether a kind of fish is drawn to a circle: any, unless the player chose a way of fishing its spot that catches
	 * only some.
	 */
	private boolean drawn(Circle circle, int item)
	{
		if (circle.npc != chosenSpot || chosenFish == null)
		{
			return true;
		}
		for (int kind : chosenFish)
		{
			if (kind == item)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes a fish the player has just caught, by the Fishing experience it gave, out of the circle at the spot they
	 * are fishing: one of that kind circling it, at random, shrinks away there, and its place is left for another. An
	 * experience drop matching no river fish, or a kind not circling, takes none.
	 */
	void caught(int xp)
	{
		Player player = client.getLocalPlayer();
		Actor fishing = player == null ? null : player.getInteracting();
		Integer item = null;
		for (Map.Entry<Integer, Integer> kind : CATCH_XP.entrySet())
		{
			if (Math.abs(xp - kind.getValue()) <= kind.getValue() * CATCH_XP_SPREAD
				&& (item == null || Math.abs(xp - kind.getValue()) < Math.abs(xp - CATCH_XP.get(item))))
			{
				item = kind.getKey();
			}
		}
		log.debug("River catch: {} xp, item {}, fishing {}", xp, item, fishing == null ? null : fishing.getName());
		if (fishing == null || item == null)
		{
			return;
		}
		// One already in its lane, if any, else one still on its way in, which shrinks away once there.
		for (Shoal shoal : shoals)
		{
			List<Swimmer> inLane = new ArrayList<>();
			List<Swimmer> comingIn = new ArrayList<>();
			for (Swimmer swimmer : shoal.fish)
			{
				if (swimmer.circle != null && swimmer.circle.npc == fishing && swimmer.item == item && !swimmer.caught
					&& swimmer.shrinkingSince < 0)
				{
					(inLane(shoal.river, swimmer) ? inLane : comingIn).add(swimmer);
				}
			}
			List<Swimmer> from = inLane.isEmpty() ? comingIn : inLane;
			log.debug("River catch: {} in their lane, {} coming in", inLane.size(), comingIn.size());
			if (!from.isEmpty())
			{
				from.get(ThreadLocalRandom.current().nextInt(from.size())).caught = true;
				return;
			}
		}
	}

	/**
	 * Whether a circling fish has reached its lane of its circle, within LANE_ARRIVED of it.
	 */
	private static boolean inLane(River river, Swimmer swimmer)
	{
		return offLane(river, swimmer) <= LANE_ARRIVED;
	}

	/**
	 * How far a circling fish is from its lane of its circle, in local units: from the lane where it is, pulled in
	 * from the bank there, at the fish's angle round the circle.
	 */
	private static double offLane(River river, Swimmer swimmer)
	{
		Circle circle = swimmer.circle;
		double angle = Math.atan2(swimmer.y - circle.y, swimmer.x - circle.x);
		double[] lane = new double[2];
		circlePoint(river, circle, circle.lanes[swimmer.circleLane], angle, lane);
		double out = Math.hypot(lane[0] - circle.x, lane[1] - circle.y);
		return Math.abs(Math.hypot(swimmer.x - circle.x, swimmer.y - circle.y) - out);
	}

	/**
	 * Whether a kind is one of the fewest circling a circle, of the kinds drawn to it, so the circle's kinds stay as
	 * even as they can.
	 */
	private boolean fewest(Shoal shoal, Circle circle, int item)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		for (int kind : shoal.kinds)
		{
			if (drawn(circle, kind))
			{
				counts.put(kind, 0);
			}
		}
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.circle == circle && swimmer.shrinkingSince < 0 && counts.containsKey(swimmer.item))
			{
				counts.merge(swimmer.item, 1, Integer::sum);
			}
		}
		return counts.getOrDefault(item, 0) <= counts.values().stream().min(Integer::compare).orElse(0);
	}

	/**
	 * Which circle a fish not circling is passing, the nearest it is near, and on which side, kept for the whole
	 * pass: the side its own line is on, if the river has room to pass outside the circle there; otherwise the other,
	 * if that has; and failing both, whichever has more. Sets none while it is far from every circle.
	 */
	private static void pass(Shoal shoal, Swimmer swimmer)
	{
		Circle nearest = null;
		double nearestApart = Double.MAX_VALUE;
		if (swimmer.circle == null)
		{
			for (Circle circle : shoal.circles.values())
			{
				double apart = Math.hypot(swimmer.x - circle.x, swimmer.y - circle.y);
				if (apart < circle.radius + CIRCLE_CLEARANCE + CLEAR_AHEAD && apart < nearestApart)
				{
					nearest = circle;
					nearestApart = apart;
				}
			}
		}
		if (nearest != swimmer.passing)
		{
			swimmer.passing = nearest;
			swimmer.passSide = 0;
		}
		if (nearest == null || swimmer.passSide != 0)
		{
			return;
		}
		double clear = nearest.radius + CIRCLE_CLEARANCE;
		double own = swimmer.lateral - nearest.across;
		int roomier = nearest.roomLeft - nearest.across >= nearest.roomRight + nearest.across ? 1 : -1;
		int first = Math.abs(own) > 1 ? (int) Math.signum(own) : roomier;
		for (int way : new int[]{first, -first})
		{
			if (way > 0 ? nearest.across + clear <= nearest.roomLeft : nearest.across - clear >= -nearest.roomRight)
			{
				swimmer.passSide = way;
				return;
			}
		}
		swimmer.passSide = roomier;
	}

	/**
	 * The lane of a circle with the fewest fish in it for its length, so the circle fills evenly; the outer of two as
	 * full.
	 */
	private static int roomiestLane(Shoal shoal, Circle circle)
	{
		int[] in = new int[circle.lanes.length];
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.circle == circle)
			{
				in[swimmer.circleLane]++;
			}
		}
		int best = circle.lanes.length - 1;
		for (int lane = best - 1; lane >= 0; lane--)
		{
			if (in[lane] / circle.lanes[lane] < in[best] / circle.lanes[best])
			{
				best = lane;
			}
		}
		return best;
	}

	/**
	 * How much faster or slower a circling fish swims to even out its gaps round its lane of its circle: slower while
	 * the fish ahead of it is nearer than the one behind, faster while the one behind is nearer.
	 */
	private static double spacing(Shoal shoal, Swimmer swimmer)
	{
		Circle circle = swimmer.circle;
		double own = Math.atan2(swimmer.y - circle.y, swimmer.x - circle.x);
		double ahead = 2 * Math.PI;
		double behind = 2 * Math.PI;
		int count = 1;
		for (Swimmer other : shoal.fish)
		{
			if (other == swimmer || other.circle != circle || other.circleLane != swimmer.circleLane)
			{
				continue;
			}
			count++;
			double turn = (Math.atan2(other.y - circle.y, other.x - circle.x) - own) * circle.way;
			turn = (turn % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
			ahead = Math.min(ahead, turn);
			behind = Math.min(behind, 2 * Math.PI - turn);
		}
		if (count < 2)
		{
			return 1;
		}
		double even = 2 * Math.PI / count;
		return 1 + Math.max(-SPACING_MOST, Math.min(SPACING_MOST, SPACING_PULL * (ahead - behind) / even));
	}

	/**
	 * Moves a fish on by some client ticks: down the path, or round its circle, steering for a point a little ahead
	 * and turning towards it no faster than it can; never onto land. Says whether it is still there, not yet having
	 * shrunk away at the path's end.
	 */
	private boolean move(Shoal shoal, Swimmer swimmer, int ticks, int cycle)
	{
		River river = shoal.river;
		Look look = swimmer.look;
		double surging = Math.sin(2 * Math.PI * ((cycle + swimmer.surgePhase) % SURGE_CYCLES) / SURGE_CYCLES);
		double want = (swimmer.circle != null ? CIRCLE_SPEED : TRAVEL_SPEED) * swimmer.speed * look.pace / 100.0
			* (1 + SURGE * look.surge / 100.0 * surging);
		if (swimmer.circle != null)
		{
			// Slower the further in its lane is, once it is in it: on its way in it keeps the outermost's speed, easing
			// to its lane's over the last LANE_ARRIVING local units.
			Circle circle = swimmer.circle;
			int lanes = circle.lanes.length;
			double out = lanes > 1 ? swimmer.circleLane / (double) (lanes - 1) : 1;
			double off = offLane(river, swimmer);
			double in = Math.max(0, Math.min(1, 1 - (off - LANE_ARRIVED) / LANE_ARRIVING));
			double lane = INNER_LANE_SPEED + (1 - INNER_LANE_SPEED) * out;
			want *= (1 - in * (1 - lane)) * spacing(shoal, swimmer);
		}
		swimmer.swimming += (want - swimmer.swimming) * Math.min(1, 0.05 * ticks);
		double moved = swimmer.swimming * ticks;
		double targetX;
		double targetY;
		if (swimmer.circle != null)
		{
			Circle circle = swimmer.circle;
			double out = circle.lanes[swimmer.circleLane];
			double angle = Math.atan2(swimmer.y - circle.y, swimmer.x - circle.x) + circle.way * CIRCLE_LEAD / out;
			circlePoint(river, circle, out, angle, point);
			targetX = point[0];
			targetY = point[1];
		}
		else
		{
			// How far down the river it is, from where it really is, so on the inside of a bend, where it gets down
			// the river quicker than it swims, it doesn't fall behind itself and turn back; looked for only near where
			// it was, never back up the river.
			swimmer.s = Math.max(swimmer.s, river.nearest(swimmer.x, swimmer.y, swimmer.s - LOOK_AHEAD,
				swimmer.s + moved + LOOK_AHEAD));
			double wander = WANDER * wander(swimmer, cycle);
			river.at(swimmer.s + LOOK_AHEAD, point);
			double offset = share(Math.max(-1, Math.min(1, swimmer.across + wander)), point);
			// Near a circle, kept outside it on its side.
			pass(shoal, swimmer);
			if (swimmer.passing != null)
			{
				double edge = swimmer.passing.across + swimmer.passSide * (swimmer.passing.radius + CIRCLE_CLEARANCE);
				offset = swimmer.passSide > 0 ? Math.max(offset, edge) : Math.min(offset, edge);
			}
			// Never past the room either side.
			offset = Math.max(-point[5], Math.min(point[4], offset));
			swimmer.lateral = offset;
			targetX = point[0] - point[3] * offset;
			targetY = point[1] + point[2] * offset;
		}
		swimmer.targetX = targetX;
		swimmer.targetY = targetY;
		double toward = Math.atan2(targetY - swimmer.y, targetX - swimmer.x);
		double turn = Math.IEEEremainder(toward - swimmer.facing, 2 * Math.PI);
		double most = TURN_RATE * ticks;
		swimmer.facing += Math.max(-most, Math.min(most, turn));
		double x = swimmer.x + Math.cos(swimmer.facing) * moved;
		double y = swimmer.y + Math.sin(swimmer.facing) * moved;
		if (!river.isWater(x, y))
		{
			// About to touch the bank: straight for where it is going instead.
			swimmer.facing = toward;
			x = swimmer.x + Math.cos(toward) * moved;
			y = swimmer.y + Math.sin(toward) * moved;
		}
		swimmer.x = x;
		swimmer.y = y;
		swimmer.wag = (swimmer.wag + 2 * Math.PI * moved / WAG_DISTANCE) % (2 * Math.PI);
		// Caught, it shrinks away once in its lane.
		if (swimmer.caught && swimmer.shrinkingSince < 0 && swimmer.circle != null && inLane(river, swimmer))
		{
			swimmer.shrinkingSince = cycle;
		}
		// Shrinks away as it nears the path's end.
		if (swimmer.circle == null && swimmer.shrinkingSince < 0
			&& swimmer.s >= river.length - swimmer.swimming * GROW_CYCLES)
		{
			swimmer.shrinkingSince = cycle;
		}
		int step = GROW_STEPS;
		if (swimmer.shrinkingSince >= 0)
		{
			double through = (cycle - swimmer.shrinkingSince) / (double) GROW_CYCLES;
			if (through >= 1)
			{
				return false;
			}
			step = Math.max(1, (int) Math.ceil(GROW_STEPS * (1 - through)));
		}
		else if (swimmer.growingSince >= 0)
		{
			double through = (cycle - swimmer.growingSince) / (double) GROW_CYCLES;
			step = Math.max(1, Math.min(GROW_STEPS, (int) Math.ceil(GROW_STEPS * through)));
			swimmer.growingSince = through >= 1 ? -1 : swimmer.growingSince;
		}
		swimmer.wantStep = step;
		place(shoal, swimmer, cycle);
		return true;
	}

	/**
	 * Where a fish is in its wander, from -1 to 1: gliding smoothly from one place picked at random to the next, a
	 * new one picked each time it gets there.
	 */
	private static double wander(Swimmer swimmer, int cycle)
	{
		double through = (cycle - swimmer.wanderSince) / (double) swimmer.wanderTakes;
		if (through >= 1)
		{
			ThreadLocalRandom random = ThreadLocalRandom.current();
			swimmer.wanderFrom = swimmer.wanderTo;
			swimmer.wanderTo = random.nextDouble(-1, 1);
			swimmer.wanderSince = cycle;
			swimmer.wanderTakes = random.nextInt(MIN_WANDER_CYCLES, MAX_WANDER_CYCLES + 1);
			through = 0;
		}
		double eased = through * through * (3 - 2 * through);
		return swimmer.wanderFrom + (swimmer.wanderTo - swimmer.wanderFrom) * eased;
	}

	/**
	 * Puts a fish where it is, facing the way it swims, its tail wagging, bobbing down and back up and tipping as its
	 * kind does.
	 */
	private void place(Shoal shoal, Swimmer swimmer, int cycle)
	{
		Look look = swimmer.look;
		// 0 faces south, 512 west, 1024 north, in the game's 2048ths of a turn; it faces (cos, sin) of its facing.
		int heading = (int) Math.round(Math.atan2(-Math.cos(swimmer.facing), -Math.sin(swimmer.facing))
			* 2048 / (2 * Math.PI)) & 2047;
		double pace = Math.min(1, swimmer.swimming / REFERENCE_SPEED);
		int swung = heading + (int) Math.round(WAG * look.wag / 100.0 * pace * pace * Math.sin(swimmer.wag)) & 2047;
		swimmer.fish.setOrientation(swung + look.turn * 2048 / 360 & 2047);
		int x = (int) Math.round(swimmer.x) + (look.pivot * (Perspective.SINE[swung] - Perspective.SINE[heading]) >> 16);
		int y = (int) Math.round(swimmer.y)
			+ (look.pivot * (Perspective.COSINE[swung] - Perspective.COSINE[heading]) >> 16);
		swimmer.fish.setX(x);
		swimmer.fish.setY(y);
		// A river fish's bob goes down from where it rests and back up, by its look's rise, then holds still a while.
		int bobbing = swimmer.bobClock % (BOB_CYCLES + BOB_REST_CYCLES);
		int bobAt = bobbing < BOB_CYCLES ? bobbing * 2048 / BOB_CYCLES : -1;
		int bobbed = bobAt < 0 ? 0 : look.rise * (65536 - Perspective.COSINE[bobAt]) >> 17;
		// A dip goes down and back up smoothly, only ever deeper.
		double through = swimmer.dippingSince < 0 ? 0
			: Math.PI * (cycle - swimmer.dippingSince) / Math.max(1, look.dipMillis / 20.0);
		double dip = Math.sin(through);
		swimmer.fish.setZ(waterHeight(x, y, shoal.plane) + look.sink + bobbed
			+ (int) Math.round(look.dipDepth * dip * dip));
		// Tipped nose down while sinking and up while rising, the most where it moves fastest; only full grown, the
		// sizes it grows through being made untipped.
		double tip = look.tip / 100.0 * ((look.rise > 0 && bobAt >= 0 ? -BOB_PITCH * Perspective.SINE[bobAt] / 65536 : 0)
			- (swimmer.dippingSince >= 0 && look.dipDepth > 0 ? DIP_PITCH * Math.sin(2 * through) : 0));
		int pitch = swimmer.wantStep == GROW_STEPS ? (int) Math.round(tip / FishModels.PITCH_STEP) : 0;
		if (pitch != swimmer.pitch || swimmer.wantStep != swimmer.step)
		{
			// Only a model already made: one not made yet is put first in line, and the fish keeps the one it has
			// until it is, a tick or two at most.
			int size = size(swimmer.item, swimmer.wantStep);
			Model model = models.made(swimmer.item, size, pitch, 0);
			if (model != null)
			{
				swimmer.fish.setModel(model);
				swimmer.pitch = pitch;
				swimmer.step = swimmer.wantStep;
			}
			else
			{
				models.queue(swimmer.item, size, pitch, 0, true);
			}
		}
	}

	/**
	 * The water's height at a place, in local units, between its tile's corners: as Perspective.getTileHeight works
	 * it out, but always on the river's own plane, never lifted onto a bridge over it.
	 */
	private int waterHeight(int x, int y, int plane)
	{
		WorldView view = client.getTopLevelWorldView();
		int[][][] heights = view == null ? null : view.getTileHeights();
		int tileX = x >> 7;
		int tileY = y >> 7;
		if (heights == null || tileX < 0 || tileY < 0 || tileX + 1 >= heights[plane].length
			|| tileY + 1 >= heights[plane][tileX].length)
		{
			return 0;
		}
		int[][] h = heights[plane];
		int inX = x & 127;
		int inY = y & 127;
		int south = inX * h[tileX + 1][tileY] + (128 - inX) * h[tileX][tileY] >> 7;
		int north = inX * h[tileX + 1][tileY + 1] + (128 - inX) * h[tileX][tileY + 1] >> 7;
		return (128 - inY) * south + inY * north >> 7;
	}

	/**
	 * A circle's point at an angle, so far out, pulled in towards its middle, where the bank is nearer than BANK_GAP
	 * there, until it is that far, or at the middle.
	 */
	private static void circlePoint(River river, Circle circle, double out, double angle, double[] into)
	{
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		while (out > 0 && river.clearanceAt(circle.x + out * cos, circle.y + out * sin) < BANK_GAP)
		{
			out -= 8;
		}
		out = Math.max(0, out);
		into[0] = circle.x + out * cos;
		into[1] = circle.y + out * sin;
	}

	/**
	 * TEMPORARY, while tuning: takes the tunable river settings from the debug spinners. The settings it sets aren't
	 * final for this.
	 */
	static void tune(LivelyFishingSpotsConfig config)
	{
		BOB_CYCLES = config.debugRiverBobCycles();
		BOB_REST_CYCLES = config.debugRiverBobRestCycles();
		CIRCLE_LANES = config.debugRiverCircleLanes();
		CIRCLE_LANE_SPACING = config.debugRiverCircleLaneSpacing();
		CIRCLE_SIZE = config.debugRiverCircleSize();
		CIRCLE_MOST = config.debugRiverCircleMost();
		INNER_LANE_SPEED = config.debugRiverInnerLaneSpeed() / 100.0;
		CIRCLE_CLEARANCE = config.debugRiverCircleClearance();
		CIRCLE_OFFSET_X = config.debugRiverCircleOffsetX();
		CIRCLE_OFFSET_Y = config.debugRiverCircleOffsetY();
		TRAVEL_SPACING = config.debugRiverTravelSpacing();
		SPREAD = config.debugRiverSpread() / 100.0;
		WANDER = config.debugRiverWander() / 100.0;
	}

	/**
	 * Adds a route picked in game while debugging, start then end, to try before the table's, and logs it for the
	 * table.
	 */
	void pickRoute(WorldPoint start, WorldPoint end)
	{
		picked.add(0, new WorldPoint[]{start, end});
		log.debug("River route picked: new WorldPoint[]{{new WorldPoint({}, {}, {}), new WorldPoint({}, {}, {})}}",
			start.getX(), start.getY(), start.getPlane(), end.getX(), end.getY(), end.getPlane());
	}

	/**
	 * Takes every fish away, as when switched off or after a map load, which moves the scene the rivers are mapped
	 * in.
	 */
	void clear()
	{
		for (Shoal shoal : shoals)
		{
			shoal.fish.forEach(swimmer -> swimmer.fish.setActive(false));
		}
		shoals.clear();
	}

	private Point canvas(Shoal shoal, double x, double y)
	{
		return Perspective.localToCanvas(client, new LocalPoint((int) x, (int) y, shoal.worldView), shoal.plane);
	}

	/**
	 * Draws, while debugging, a line across the river's room at a distance along the path.
	 */
	private void acrossLine(Graphics2D graphics, Shoal shoal, double s, Color colour)
	{
		shoal.river.at(s, point);
		double left = point[4] + BANK_GAP;
		double right = point[5] + BANK_GAP;
		Point a = canvas(shoal, point[0] - point[3] * left, point[1] + point[2] * left);
		Point b = canvas(shoal, point[0] + point[3] * right, point[1] - point[2] * right);
		if (a != null && b != null)
		{
			graphics.setColor(colour);
			graphics.drawLine(a.getX(), a.getY(), b.getX(), b.getY());
		}
	}

	/**
	 * Draws, while debugging, each shoal's banks, path and room, each spot's circle, and each fish's steering.
	 */
	void drawDebug(Graphics2D graphics)
	{
		for (Shoal shoal : shoals)
		{
			River river = shoal.river;
			graphics.setColor(new Color(255, 160, 0, 160));
			for (int[] bank : river.banks)
			{
				Point p = canvas(shoal, bank[0], bank[1]);
				if (p != null)
				{
					graphics.fillRect(p.getX() - 1, p.getY() - 1, 3, 3);
				}
			}
			graphics.setColor(Color.CYAN);
			Point last = null;
			for (int k = 0; k < river.pathX.length; k++)
			{
				Point p = canvas(shoal, river.pathX[k], river.pathY[k]);
				if (p != null && last != null)
				{
					graphics.drawLine(last.getX(), last.getY(), p.getX(), p.getY());
				}
				last = p;
			}
			if (last != null)
			{
				graphics.fillOval(last.getX() - 4, last.getY() - 4, 8, 8);
			}
			// The edges of the room the fish keep within, either side of the path.
			graphics.setColor(new Color(0, 160, 160, 140));
			for (int side = -1; side <= 1; side += 2)
			{
				Point edge = null;
				for (int k = 0; k < river.pathX.length; k++)
				{
					river.at(river.along[k], point);
					double out = side > 0 ? point[4] : -point[5];
					Point p = canvas(shoal, point[0] - point[3] * out, point[1] + point[2] * out);
					if (p != null && edge != null)
					{
						graphics.drawLine(edge.getX(), edge.getY(), p.getX(), p.getY());
					}
					edge = p;
				}
			}
			for (Circle circle : shoal.circles.values())
			{
				// Across the river: where passing fish decide whether to join the circle, and level with it.
				acrossLine(graphics, shoal, circle.along - JOIN_BEFORE, Color.MAGENTA);
				acrossLine(graphics, shoal, circle.along, Color.GREEN);
				graphics.setColor(Color.GREEN);
				for (double out : circle.lanes)
				{
					Point before = null;
					for (int a = 0; a <= 32; a++)
					{
						circlePoint(river, circle, out, 2 * Math.PI * a / 32, point);
						Point p = canvas(shoal, point[0], point[1]);
						if (p != null && before != null)
						{
							graphics.drawLine(before.getX(), before.getY(), p.getX(), p.getY());
						}
						before = p;
					}
				}
				// What passing fish keep outside of.
				graphics.setColor(Color.RED);
				Point ring = null;
				for (int a = 0; a <= 32; a++)
				{
					double angle = 2 * Math.PI * a / 32;
					double out = circle.radius + CIRCLE_CLEARANCE;
					Point p = canvas(shoal, circle.x + out * Math.cos(angle), circle.y + out * Math.sin(angle));
					if (p != null && ring != null)
					{
						graphics.drawLine(ring.getX(), ring.getY(), p.getX(), p.getY());
					}
					ring = p;
				}
				graphics.setColor(Color.GREEN);
				Point middle = canvas(shoal, circle.x, circle.y);
				if (middle != null)
				{
					graphics.drawLine(middle.getX() - 5, middle.getY(), middle.getX() + 5, middle.getY());
					graphics.drawLine(middle.getX(), middle.getY() - 5, middle.getX(), middle.getY() + 5);
				}
				LocalPoint at = circle.npc.getLocalLocation();
				Point label = at == null ? null : Perspective.localToCanvas(client, at, shoal.plane, 150);
				if (label != null)
				{
					String text = String.valueOf(circle.npc.getId());
					graphics.setColor(Color.WHITE);
					graphics.drawString(text, label.getX() - graphics.getFontMetrics().stringWidth(text) / 2, label.getY());
				}
			}
			// Each fish, to the point it steers for: white swimming down, green circling, yellow growing in, red
			// shrinking away.
			for (Swimmer swimmer : shoal.fish)
			{
				Point from = canvas(shoal, swimmer.x, swimmer.y);
				Point to = canvas(shoal, swimmer.targetX, swimmer.targetY);
				if (from == null || to == null)
				{
					continue;
				}
				graphics.setColor(swimmer.shrinkingSince >= 0 ? Color.RED : swimmer.growingSince >= 0 ? Color.YELLOW
					: swimmer.circle != null ? Color.GREEN : Color.WHITE);
				graphics.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
				graphics.fillOval(to.getX() - 2, to.getY() - 2, 5, 5);
			}
		}
	}
}
