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
import net.runelite.api.ItemContainer;
import net.runelite.api.GameObject;
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
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Fish swimming down rivers along hand-picked routes, in small groups that keep apart, line up and stay
 * together (Couzin's zones). One fixed shoal per route; fish grow in at the start and shrink away at the
 * end. While the player fishes a spot, passing fish circle it.
 */
@Slf4j
final class RiverSpotFish
{
	// Rainbow fish: the odd group (never solo), drawn to a circle only by lure with stripy feathers.
	private static final int RAINBOW = ItemID.HUNTING_RAW_FISH_SPECIAL;
	// Share of groups that are rainbow fish, and the other kinds' weights against each other (tuning spinners).
	private static double RAINBOW_SHARE = 0.07;
	private static final Map<Integer, Integer> WEIGHTS = new HashMap<>();
	// Lure/bait spots: trout, salmon, pike and the odd rainbow fish.
	private static final int[] LURE_FISH = {ItemID.RAW_TROUT, ItemID.RAW_SALMON, ItemID.RAW_PIKE, RAINBOW};
	// River spots with fish, by NPC id.
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
	// Fish each menu option draws to the circle; other options draw all.
	private static final Map<String, int[]> OPTION_FISH = Map.of(
		"lure", new int[]{ItemID.RAW_TROUT, ItemID.RAW_SALMON},
		"bait", new int[]{ItemID.RAW_PIKE});
	private static final int[] STRIPY_FISH = {RAINBOW};
	// Fishing XP per catch, to tell catches apart; within CATCH_XP_SPREAD counts (outfit bonuses).
	private static final Map<Integer, Integer> CATCH_XP = Map.of(
		ItemID.RAW_TROUT, 50, ItemID.RAW_PIKE, 60, ItemID.RAW_SALMON, 70, RAINBOW, 80);
	private static final double CATCH_XP_SPREAD = 0.15;
	// Hand-picked routes, upstream start then downstream end. Spots near none get no fish.
	private static final List<WorldPoint[]> ROUTES = List.<WorldPoint[]>of(
		// Barbarian Village.
		new WorldPoint[]{new WorldPoint(3104, 3443, 0), new WorldPoint(3105, 3417, 0)});
	// Tiles a spot may be from a route's line to use it; tiles of water mapped past its ends.
	private static final int ROUTE_REACH = 8;
	private static final int ROUTE_MARGIN = 4;
	// Client ticks a shoal outlives its last spot, so a spot moving doesn't restart it.
	private static final int LINGER = 500;

	// Floor texture of river water, seen in game.
	private static final int WATER_TEXTURE = 1;
	// Water grid cell size, local units.
	private static final int CELL = 32;
	// Path point spacing, local units, and smoothing passes.
	private static final double PATH_STEP = 16;
	private static final int SMOOTHING = 4;
	// Gap fish keep from the bank, local units.
	private static final double BANK_GAP = 20;

	// Speeds in local units per client tick, before the look's speed; per-fish spread (percent); surge
	// (share of speed) and its period in client ticks.
	private static final double TRAVEL_SPEED = 2.8;
	private static final double CIRCLE_SPEED = 2.0;
	private static final int SPEED_SPREAD = 30;
	private static final double SURGE = 0.25;
	private static final int SURGE_CYCLES = 300;
	// How early or late each fish spawns, as a share of the gap.
	private static final double SPAWN_STRAY = 0.25;
	// Steering: look-ahead along the path (local units) and turn rate (radians per client tick).
	private static final double LOOK_AHEAD = 48;
	private static final double TURN_RATE = 0.08;
	// Average gap between fish down the river, local units.
	private static double TRAVEL_SPACING = 70;
	// Lane spread and wander, as shares of the room either side; wander glides between random points
	// over this many client ticks.
	private static double SPREAD = 0.4;
	private static double WANDER = 0.25;
	private static final int MIN_WANDER_CYCLES = 100;
	private static final int MAX_WANDER_CYCLES = 300;
	// Groups: fewest and most fish in one, the radius of the round group they hold places in (local units), and
	// how much of each member's own speed variation it keeps.
	private static final int GROUP_LEAST = 2;
	private static final int GROUP_MOST = 6;
	private static final double GROUP_RADIUS = 32;
	private static final double GROUP_LENGTH = 2 * GROUP_RADIUS;
	// Chance a group is flat, spread across the river and short along it; the rest are round, give or take.
	private static final double FLAT_CHANCE = 0.2;
	private static final int FLAT_MOST = 3;
	private static final double OWN_SPEED = 0.15;
	// Single fish on their own come on top of the groups, one per this many fish's spacing.
	private static final double SOLO_EVERY = 2;
	// Couzin's zones: steer away from any fish within REPEL_RANGE; line up with groupmates within ALIGN_RANGE;
	// steer back towards the group's middle when further than COHESION_RANGE. Weights are against the pull
	// towards the path, 1.
	private static final double REPEL_RANGE = 26;
	private static final double REPEL = 1.0;
	// Groupmates keep their spacing more firmly, from further.
	private static final double GROUP_REPEL_RANGE = 28;
	// How far along the river either side a fish looks for others to keep from: the widest repel range, plus room
	// for bends, where fish can be nearer than their distance along the path.
	private static final double REPEL_WINDOW = Math.max(REPEL_RANGE, GROUP_REPEL_RANGE) + 32;
	private static final double GROUP_REPEL = 1.2;
	// Share per client tick each repulsion eases towards what it should be: slower between groups, so they
	// steer round each other smoothly, quicker within one, so it keeps its spacing.
	private static final double REPEL_EASE = 0.08;
	private static final double GROUP_REPEL_EASE = 0.12;
	// With see-through water (117 HD), fish swimming down the river sit this much deeper, picked at random per fish
	// (local units), rising to the surface to circle a spot; share per client tick they ease up or down.
	private static final int DEEP_LEAST = 16;
	private static final int DEEP_MOST = 64;
	private static final double DEPTH_EASE = 0.03;
	// Groups at least this big may scatter: chance per client tick, the burst's speed (as a share) and length
	// (client ticks), and the push outwards from the group's middle.
	private static final int SCATTER_LEAST = 4;
	private static double SCATTER_RATE = 1.0 / 2650;
	private static final double BURST_SPEED = 1.8;
	private static final int BURST_CYCLES = 40;
	private static final double SCATTER_PUSH = 2;
	// Pushes fade at this share of the speed they build up at, so a pushed fish drifts back, not springs back.
	private static final double REPEL_FADE = 0.5;
	// Share per client tick a fish's intended heading eases towards where it wants to go, so shoves and recoveries
	// curve smoothly.
	private static final double STEER_EASE = 0.12;
	// The same, slower, for a fish swimming in to join a circle, so it peels off the river in a curve.
	private static final double JOIN_STEER_EASE = 0.02;
	// And its turn rate when furthest, as a share of TURN_RATE, tightening to the full rate as it nears its lane.
	private static final double JOIN_TURN = 0.4;
	// A fish swimming in slows from river to circling speed over this far from its lane, local units.
	private static final double APPROACH_RANGE = 256;
	private static final double ALIGN_RANGE = 96;
	private static final double ALIGN = 0.6;
	private static final double COHESION_RANGE = 40;
	private static final double COHESION = 0.5;
	// Speed-up or slow-down, as a share, to keep level with the group's middle, reached this far (local units)
	// ahead or behind it.
	private static final double CATCH_UP = 0.3;
	private static final double CATCH_UP_RANGE = 96;
	// Client ticks to grow in or shrink away, and how many sizes that takes.
	private static final int GROW_CYCLES = 50;
	private static final int GROW_STEPS = 6;

	// Circle: outer lane radius, lanes, lane spacing (local units), most fish.
	private static double CIRCLE_SIZE = 58;
	private static int CIRCLE_LANES = 2;
	private static double CIRCLE_LANE_SPACING = 16;
	private static int CIRCLE_MOST = 7;
	// Innermost lane speed as a share of the outermost's.
	private static double INNER_LANE_SPEED = 0.4;
	// Within LANE_ARRIVED of its lane a fish is in it; it eases to lane speed over LANE_ARRIVING.
	private static final double LANE_ARRIVED = 16;
	private static final double LANE_ARRIVING = 32;
	// Circle centre offset from the spot, local units.
	private static int CIRCLE_OFFSET_X = 26;
	private static int CIRCLE_OFFSET_Y = 0;
	// Gap passing fish keep outside a circle, and how much further off they start steering.
	private static double CIRCLE_CLEARANCE = 12;
	private static final double CLEAR_AHEAD = 48;
	// Circling: look-ahead round the lane (local units) and spacing adjustment limits.
	private static final double CIRCLE_LEAD = 40;
	private static final double SPACING_MOST = 0.5;
	private static final double SPACING_PULL = 0.6;
	// Join chance (percent), how far before the spot fish decide (local units), and average client
	// ticks a circling fish stays once the player stops.
	private static final int JOIN_CHANCE = 50;
	private static final double JOIN_BEFORE = 256;
	private static final double LEAVE_AFTER = 100;
	// A fished circle not full and joined by no passing fish for this many client ticks gets filler fish, grown in
	// this far upstream (local units) every so many client ticks at random, until one joins or it's full.
	private static final int REFILL_AFTER = 500;
	private static final double REFILL_BEHIND = 768;
	private static final int REFILL_GAP_LEAST = 100;
	private static final int REFILL_GAP_MOST = 300;
	// Client ticks away from a circle after which fishing it again starts its wait afresh.
	private static final int REFILL_BREAK = 50;

	// Wag, bob and tip, as at sea.
	private static final int WAG = 20;
	private static final double WAG_DISTANCE = 72;
	private static final double REFERENCE_SPEED = 3.2;
	private static int BOB_CYCLES = 60;
	private static int BOB_REST_CYCLES = 30;
	private static final double DIP_PITCH = 20;
	private static final double BOB_PITCH = 8;

	/**
	 * A route's water grid and the path down its middle.
	 */
	private static final class River
	{
		// South-west corner (local units) and size in cells.
		private final int x0;
		private final int y0;
		private final int size;
		private final boolean[] water;
		private final double[] clearance;
		// Path points, distance along, and room left and right (facing downstream).
		private double[] pathX;
		private double[] pathY;
		private double[] along;
		private double[] left;
		private double[] right;
		private double length;
		// Bank cells, for debug drawing.
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
		 * Distance to the bank, local units; 0 off the water.
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
		 * Fills x, y, direction x, direction y, room left, room right at a distance along the path.
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
		 * Distance along the path of its nearest point.
		 */
		private double nearest(double x, double y)
		{
			return nearest(x, y, 0, length);
		}

		/**
		 * As nearest, searching only between two distances along.
		 */
		private double nearest(double x, double y, double from, double to)
		{
			double best = Double.MAX_VALUE;
			double s = from;
			// Points are evenly spaced, so the range maps straight to indices.
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
	 * One route's river, circles and fish.
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
		// Its fish in order down the river, rebuilt each tick, so each looks only at those near it along it.
		private final List<Swimmer> byAlong = new ArrayList<>();
		private int nextSpawn;
		private int nextSolo;
		private int movedAt;
		// Client tick the last spot went, or -1.
		private int emptySince = -1;
		// Whether its fish are shrinking away, the shoal going once they have.
		private boolean leaving;

		private Shoal(WorldPoint[] route, int plane, int worldView, River river, int[] kinds, int cycle, double spacing)
		{
			this.route = route;
			this.plane = plane;
			this.worldView = worldView;
			this.river = river;
			this.kinds = kinds;
			travelling = Math.max(1, (int) Math.round(river.length / spacing));
			movedAt = cycle;
		}
	}

	/**
	 * A glide between random points from -1 to 1, each taking a random time.
	 */
	private static final class Glide
	{
		private double from;
		private double to;
		private int since;
		private int takes = 1;

		private Glide(ThreadLocalRandom random)
		{
			to = random.nextDouble(-1, 1);
		}

		private double at(int cycle)
		{
			double through = (cycle - since) / (double) takes;
			if (through >= 1)
			{
				ThreadLocalRandom random = ThreadLocalRandom.current();
				from = to;
				to = random.nextDouble(-1, 1);
				since = cycle;
				takes = random.nextInt(MIN_WANDER_CYCLES, MAX_WANDER_CYCLES + 1);
				through = 0;
			}
			double eased = through * through * (3 - 2 * through);
			return from + (to - from) * eased;
		}
	}

	/**
	 * Fish swimming down the river together: their shared lane, wander, speed and surge.
	 */
	private static final class Group
	{
		private final double across;
		private final double speed;
		private final int surgePhase;
		private final Glide wander;
		private final List<Swimmer> members = new ArrayList<>();
		// Its middle, along the path and in the scene, worked out once a tick.
		private double middleS;
		private double middleX;
		private double middleY;
		private int middleAt = -1;

		private Group(ThreadLocalRandom random)
		{
			across = random.nextDouble(-1, 1) * SPREAD;
			speed = (100 + random.nextInt(-SPEED_SPREAD, SPEED_SPREAD + 1)) / 100.0;
			surgePhase = random.nextInt(SURGE_CYCLES);
			wander = new Glide(random);
		}
	}

	/**
	 * The circle at one spot.
	 */
	private static final class Circle
	{
		private final NPC npc;
		private final WorldPoint spot;
		// Centre, outer radius, distance along the path, direction (1 or -1), lane radii.
		private final double x;
		private final double y;
		private final double radius;
		private final double along;
		private final int way;
		private final double[] lanes;
		// Centre's offset across the path (left positive) and room either side there.
		private final double across;
		private final double roomLeft;
		private final double roomRight;
		// Client tick it started waiting for a passing fish while fished and not full, or -1; the next filler
		// fish's client tick once it's waited too long, or -1; and the client tick it was last fished.
		private int waitingSince = -1;
		private int nextFill = -1;
		private int fishedAt = -1;

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
		// Position, heading (radians), distance along the path.
		private double x;
		private double y;
		private double facing;
		private double s;
		// Last steering target, for debug drawing.
		private double targetX;
		private double targetY;
		// Circle and lane, or null; circles already decided about.
		private Circle circle;
		private int circleLane;
		private final Set<Circle> decided = new HashSet<>();
		// A filler fish's circle, joined when it gets there, or null.
		private Circle bound;
		// Circle being passed and side (1 left, -1 right), or null and 0.
		private Circle passing;
		private int passSide;
		// Own speed share, lane across the river, surge phase.
		private double speed;
		private double across;
		// Client tick a scatter burst ends, or -1.
		private int burstUntil = -1;
		private final int surgePhase;
		// Own wander, used when not in a group.
		private final Glide wander;
		// Its group, or null, and its place across the group, local units.
		private Group group;
		private double slot;
		// And its place along the group, ahead when more than 0, local units.
		private double slotAlong;
		// Its size against a trout's, scaling the room it keeps.
		private double scale = 1;
		// Its place in the shoal's byAlong this tick.
		private int order;
		// Repulsion from nearby fish, eased: from other fish, and from groupmates.
		private double repelX;
		private double repelY;
		private double groupRepelX;
		private double groupRepelY;
		// Extra depth while swimming down the river (0 without see-through water), and how deep it is now.
		private int deep;
		private double depthNow;
		// Intended heading, eased; NaN until first set.
		private double steer = Double.NaN;
		private double swimming;
		// Current offset across the path, local units, left positive.
		private double lateral;
		// Grow/shrink start ticks (-1 if not), size steps, tip in PITCH_STEPs, dip start tick (-1 if not).
		private int growingSince = -1;
		private int shrinkingSince = -1;
		private int step;
		private int wantStep;
		private int pitch;
		private int dippingSince = -1;
		// Caught: shrinks away once in its lane.
		private boolean caught;
		private double wag;
		// Bob timer, paused during dips so they never overlap.
		private int bobClock;

		private Swimmer(RuneLiteObject fish, int item, ThreadLocalRandom random)
		{
			this.fish = fish;
			this.item = item;
			look = FishModels.look(item);
			speed = (100 + random.nextInt(-SPEED_SPREAD, SPEED_SPREAD + 1)) / 100.0;
			across = random.nextDouble(-1, 1) * SPREAD;
			wander = new Glide(random);
			surgePhase = random.nextInt(SURGE_CYCLES);
			wag = random.nextDouble(2 * Math.PI);
			bobClock = random.nextInt(BOB_CYCLES + BOB_REST_CYCLES);
		}
	}

	private final Client client;
	private final FishModels models;
	private final List<Shoal> shoals = new ArrayList<>();
	// Spot whose menu option the player last chose, and the fish that option draws (null for all).
	private NPC chosenSpot;
	private int[] chosenFish;
	// Routes picked in game while debugging, checked first.
	private final List<WorldPoint[]> picked = new ArrayList<>();
	// Whether fish swim in groups (schooled) or each on its own (random).
	private boolean schooled = true;
	// Fish spacing times this, from the player's River fish amount.
	private double spacingScale = 1;
	// While a shoal comes into sight: start each fish growing up to GROW_STAGGER client ticks late.
	private boolean stagger;
	private static final int GROW_STAGGER = 25;
	// Whether the water is see-through (117 HD), so fish can swim deep.
	private boolean seeThrough;
	// Whether, with see-through water, fish swim deeper (player setting).
	private boolean deep = true;
	// Reused each tick: spots that moved, and fish circling each circle.
	private final List<NPC> moved = new ArrayList<>();
	private final Map<Circle, Integer> circling = new HashMap<>();
	// Scratch for offLane, used on the client thread only.
	private static final double[] LANE = new double[2];
	// Scratch for River.at.
	private final double[] point = new double[6];

	RiverSpotFish(Client client, FishModels models)
	{
		this.client = client;
		this.models = models;
	}

	/**
	 * Adds a river spot to its route's shoal, starting the shoal if needed.
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
		// Make the growing sizes now; queue the tipped full-size models.
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
		Shoal shoal = new Shoal(route, plane, view.getId(), river, kinds, cycle, TRAVEL_SPACING * spacingScale);
		log.debug("River route {} to {}: path {} long, {} fish", route[0], route[1], (int) river.length,
			shoal.travelling);
		attach(shoal, spot, at);
		shoals.add(shoal);
		// Start with groups spread evenly along the river, growing in, a little out of step.
		stagger = true;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double gap = river.length / shoal.travelling;
		for (double along = gap * random.nextDouble(); along < river.length - GROUP_LENGTH; )
		{
			along += gap * spawnGroup(shoal, along, true, cycle, random) * (1 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY));
		}
		// And single fish on their own between them, when schooled.
		for (double along = gap * SOLO_EVERY * random.nextDouble(); schooled && along < river.length; )
		{
			spawn(shoal, along, true, cycle, random, kind(shoal.kinds, random, false), null, 0);
			along += gap * SOLO_EVERY * (1 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY));
		}
		stagger = false;
		shoal.nextSpawn = cycle + spawnGap(random);
		shoal.nextSolo = cycle + (int) (spawnGap(random) * SOLO_EVERY);
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
	 * The nearest route within ROUTE_REACH tiles of a spot, picked routes first; null for none.
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
	 * Gives a spot its circle.
	 */
	private void attach(Shoal shoal, NPC spot, LocalPoint at)
	{
		Circle circle = new Circle(spot, shoal.river, at.getX() + CIRCLE_OFFSET_X, at.getY() + CIRCLE_OFFSET_Y,
			ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
		shoal.circles.put(spot, circle);
		shoal.emptySince = -1;
		shoal.leaving = false;
		// Fish already past the decision point skip it.
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.s > circle.along - JOIN_BEFORE)
			{
				swimmer.decided.add(circle);
			}
		}
	}

	/**
	 * Maps a route's water and lays its path; null (logged) if it can't.
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
		// The end must be in the start's water.
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
	 * The nearest marked cell within a tile of a place, or -1.
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
	 * Which grid cells are water, reading under bridges.
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
				// Objects standing in the water, as rocks and posts, aren't water: their whole footprint.
				for (GameObject object : tile.getGameObjects())
				{
					if (object != null)
					{
						block(wet, river, object.getSceneMinLocation(), object.getSceneMaxLocation());
					}
				}
			}
		}
		return wet;
	}

	/**
	 * Marks the grid cells under tiles from one scene corner to another, inclusive, as not water.
	 */
	private static void block(boolean[] wet, River river, Point min, Point max)
	{
		if (min == null || max == null)
		{
			return;
		}
		int perTile = 128 / CELL;
		int tileX0 = Math.floorDiv(river.x0, 128);
		int tileY0 = Math.floorDiv(river.y0, 128);
		for (int sy = min.getY(); sy <= max.getY(); sy++)
		{
			for (int sx = min.getX(); sx <= max.getX(); sx++)
			{
				int tx = sx - tileX0;
				int ty = sy - tileY0;
				for (int cy = 0; cy < perTile; cy++)
				{
					for (int cx = 0; cx < perTile; cx++)
					{
						int i = tx * perTile + cx;
						int j = ty * perTile + cy;
						if (i >= 0 && j >= 0 && i < river.size && j < river.size)
						{
							wet[j * river.size + i] = false;
						}
					}
				}
			}
		}
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
	 * Floods the river's water from one cell, then works out bank distances and bank cells.
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
	 * Distance from each water cell to the bank, by a two-pass chamfer transform.
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
		// Measured to the cell edge, not its centre.
		for (int c = 0; c < d.length; c++)
		{
			d[c] = river.water[c] ? Math.min(d[c], 1e6) - CELL / 2.0 : 0;
		}
	}

	/**
	 * Lays the path down the middle of the river between two cells, with the room either side.
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
		// Smooth, keeping the ends, then space evenly.
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
	 * Respaces points evenly, PATH_STEP apart.
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
	 * Unit vector to the path's left at a point.
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
	 * How far a ray stays in the water, local units.
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
	 * Cheapest paths through the water from one cell, costing 1 / clearance squared so they keep to the
	 * middle; fills from.
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
	 * A kind's size, in percent, at a growing step.
	 */
	private static int size(int item, int step)
	{
		return Math.max(1, FishModels.look(item).size * step / GROW_STEPS);
	}

	/**
	 * Client ticks until the next spawn.
	 */
	private int spawnGap(ThreadLocalRandom random)
	{
		return (int) Math.max(1, TRAVEL_SPACING * spacingScale / TRAVEL_SPEED * (1 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY)));
	}

	/**
	 * Adds a group of fish from a distance along the path, growing in or full size. Returns how many.
	 */
	private int spawnGroup(Shoal shoal, double s, boolean grow, int cycle, ThreadLocalRandom random)
	{
		if (!schooled)
		{
			spawn(shoal, s, grow, cycle, random, kind(shoal.kinds, random, rainbow(shoal.kinds, random)), null, 0);
			return 1;
		}
		Group group = new Group(random);
		// A rainbow fish group is all rainbow fish, and tighter, being smaller.
		boolean rainbow = rainbow(shoal.kinds, random);
		double radius = GROUP_RADIUS * scale(kind(shoal.kinds, random, rainbow));
		// Mostly round, a little oval either way; sometimes flat, and then small.
		boolean flat = random.nextDouble() < FLAT_CHANCE;
		int count = random.nextInt(GROUP_LEAST, (flat ? FLAT_MOST : GROUP_MOST) + 1);
		double wide = flat ? 1.6 : random.nextDouble(0.85, 1.2);
		double deep = flat ? 0.35 : random.nextDouble(0.85, 1.2);
		for (int n = 0; n < count; n++)
		{
			// A random place in the group's oval.
			double angle = random.nextDouble(2 * Math.PI);
			double out = radius * Math.sqrt(random.nextDouble());
			double ahead = out * Math.sin(angle) * deep;
			double along = Math.max(0, Math.min(shoal.river.length, s + radius + ahead));
			spawn(shoal, along, grow, cycle, random, kind(shoal.kinds, random, rainbow), group,
				out * Math.cos(angle) * wide);
			group.members.get(group.members.size() - 1).slotAlong = ahead;
		}
		return count;
	}

	/**
	 * A kind's size against a trout's, 0.3 to 1, for how close it keeps to others.
	 */
	private static double scale(int item)
	{
		return Math.max(0.3, Math.min(1, FishModels.look(item).size / (double) FishModels.look(ItemID.RAW_TROUT).size));
	}

	/**
	 * Whether a new group (or fish, swimming randomly) is rainbow fish, now and then.
	 */
	private static boolean rainbow(int[] kinds, ThreadLocalRandom random)
	{
		return has(kinds, RAINBOW) && random.nextDouble() < RAINBOW_SHARE;
	}

	/**
	 * A rainbow fish, or a random other kind.
	 */
	private static int kind(int[] kinds, ThreadLocalRandom random, boolean rainbow)
	{
		if (rainbow)
		{
			return RAINBOW;
		}
		int total = 0;
		for (int kind : kinds)
		{
			total += weight(kind);
		}
		if (total <= 0)
		{
			// All weights 0: evenly.
			int item;
			do
			{
				item = kinds[random.nextInt(kinds.length)];
			}
			while (item == RAINBOW);
			return item;
		}
		int pick = random.nextInt(total);
		for (int kind : kinds)
		{
			pick -= weight(kind);
			if (pick < 0)
			{
				return kind;
			}
		}
		return kinds[0];
	}

	private static int weight(int kind)
	{
		return kind == RAINBOW ? 0 : WEIGHTS.getOrDefault(kind, 1);
	}

	private static boolean has(int[] kinds, int item)
	{
		for (int kind : kinds)
		{
			if (kind == item)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds a fish at a distance along the path, growing in or full size, in a group (or null) at a place across it.
	 * Returns it, or null if its model isn't made yet.
	 */
	private Swimmer spawn(Shoal shoal, double s, boolean grow, int cycle, ThreadLocalRandom random, int item,
		Group group, double slot)
	{
		int step = grow ? 1 : GROW_STEPS;
		Model model = models.model(item, size(item, step), 0, 0);
		if (model == null)
		{
			return null;
		}
		RuneLiteObject fish = client.createRuneLiteObject();
		fish.setModel(model);
		Swimmer swimmer = new Swimmer(fish, item, random);
		swimmer.step = step;
		swimmer.wantStep = step;
		swimmer.s = s;
		swimmer.growingSince = grow ? cycle + (stagger ? random.nextInt(GROW_STAGGER + 1) : 0) : -1;
		// Skip circles it's already past.
		for (Circle circle : shoal.circles.values())
		{
			if (s > circle.along - JOIN_BEFORE)
			{
				swimmer.decided.add(circle);
			}
		}
		swimmer.group = group;
		swimmer.slot = slot;
		swimmer.scale = scale(item);
		swimmer.deep = seeThrough && deep ? random.nextInt(DEEP_LEAST, DEEP_MOST + 1) : 0;
		swimmer.depthNow = swimmer.deep;
		if (group != null)
		{
			group.members.add(swimmer);
		}
		shoal.river.at(s, point);
		double offset = group != null ? share(group.across, point) + slot : share(swimmer.across, point);
		swimmer.x = point[0] - point[3] * offset;
		swimmer.y = point[1] + point[2] * offset;
		swimmer.facing = Math.atan2(point[3], point[2]);
		swimmer.swimming = TRAVEL_SPEED * swimmer.speed;
		fish.setLocation(new LocalPoint((int) swimmer.x, (int) swimmer.y, shoal.worldView), shoal.plane);
		place(shoal, swimmer, cycle);
		fish.setActive(true);
		shoal.fish.add(swimmer);
		return swimmer;
	}

	/**
	 * Converts a share of the room (-1 to 1) to an offset across the path, local units.
	 */
	private static double share(double share, double[] at)
	{
		return share >= 0 ? share * at[4] : share * at[5];
	}

	/**
	 * Removes a spot's circle; its fish swim on. The shoal lingers in case the spot only moved. Returns whether it
	 * was a spot here.
	 */
	boolean remove(NPC spot)
	{
		Shoal shoal = shoalOf(spot);
		if (shoal == null)
		{
			return false;
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
		return true;
	}

	/**
	 * Now and then breaks up a big group: each fish darts outwards from its middle at a burst of speed, then swims on
	 * alone in a lane of its own.
	 */
	private static void scatter(Shoal shoal, int ticks, int cycle, ThreadLocalRandom random)
	{
		Set<Group> groups = new HashSet<>();
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.group != null && swimmer.group.members.size() >= SCATTER_LEAST)
			{
				groups.add(swimmer.group);
			}
		}
		for (Group group : groups)
		{
			if (random.nextDouble() >= ticks * SCATTER_RATE)
			{
				continue;
			}
			double middleX = 0;
			double middleY = 0;
			for (Swimmer member : group.members)
			{
				middleX += member.x / group.members.size();
				middleY += member.y / group.members.size();
			}
			for (Swimmer member : new ArrayList<>(group.members))
			{
				ungroup(member);
				member.across = random.nextDouble(-1, 1) * SPREAD;
				member.burstUntil = cycle + BURST_CYCLES;
				double dx = member.x - middleX;
				double dy = member.y - middleY;
				double apart = Math.hypot(dx, dy);
				double angle = apart > 1 ? Math.atan2(dy, dx) : random.nextDouble(2 * Math.PI);
				member.repelX += SCATTER_PUSH * Math.cos(angle);
				member.repelY += SCATTER_PUSH * Math.sin(angle);
			}
		}
	}

	/**
	 * Takes a fish out of its group, if any.
	 */
	private static void ungroup(Swimmer swimmer)
	{
		if (swimmer.group != null)
		{
			// Keep the speed it had in the group.
			swimmer.speed = swimmer.group.speed * (1 + (swimmer.speed - 1) * OWN_SPEED);
			swimmer.group.members.remove(swimmer);
			swimmer.group = null;
		}
	}

	/**
	 * Sends a circling fish back down the river.
	 */
	private static void leave(Shoal shoal, Swimmer swimmer)
	{
		swimmer.circle = null;
		swimmer.s = Math.max(swimmer.s, shoal.river.nearest(swimmer.x, swimmer.y));
	}

	/**
	 * Adds the kinds in sight, so their models are kept.
	 */
	void addKinds(Set<Integer> swimming)
	{
		for (Shoal shoal : shoals)
		{
			shoal.fish.forEach(swimmer -> swimming.add(swimmer.item));
		}
	}

	/**
	 * Moves every fish, each client tick.
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
		List<NPC> moved = this.moved;
		moved.clear();
		for (Iterator<Shoal> all = shoals.iterator(); all.hasNext(); )
		{
			Shoal shoal = all.next();
			// Drop shoals whose spots have been gone a while.
			// Shoals whose spots have been gone a while shrink their fish away, then go.
			if (shoal.emptySince >= 0 && cycle - shoal.emptySince > LINGER && !shoal.leaving)
			{
				shoal.leaving = true;
				for (Swimmer swimmer : shoal.fish)
				{
					swimmer.shrinkingSince = swimmer.shrinkingSince >= 0 ? swimmer.shrinkingSince : cycle;
				}
			}
			if (shoal.leaving && shoal.fish.isEmpty())
			{
				all.remove();
				continue;
			}
			// Re-add spots that moved.
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
			Map<Circle, Integer> circling = this.circling;
			circling.clear();
			for (Swimmer swimmer : shoal.fish)
			{
				if (swimmer.circle != null && swimmer.shrinkingSince < 0)
				{
					circling.merge(swimmer.circle, 1, Integer::sum);
				}
				else if (swimmer.bound != null && swimmer.shrinkingSince < 0)
				{
					// Filler fish on the way hold their place.
					circling.merge(swimmer.bound, 1, Integer::sum);
				}
				else if (swimmer.shrinkingSince < 0)
				{
					travelling++;
				}
			}
			// Spawn on a steady timer so no gaps open, capped at twice the target.
			if (!shoal.leaving && cycle >= shoal.nextSpawn)
			{
				int count = travelling < 3 * shoal.travelling ? spawnGroup(shoal, 0, true, cycle, random) : 1;
				shoal.nextSpawn = Math.max(shoal.nextSpawn + spawnGap(random) * count, cycle);
			}
			if (!shoal.leaving && schooled && cycle >= shoal.nextSolo)
			{
				if (travelling < 3 * shoal.travelling)
				{
					spawn(shoal, 0, true, cycle, random, kind(shoal.kinds, random, false), null, 0);
				}
				shoal.nextSolo = Math.max(shoal.nextSolo + (int) (spawnGap(random) * SOLO_EVERY), cycle);
			}
			if (!shoal.leaving && fishing != null)
			{
				refill(shoal, fishing, cycle, random);
			}
			if (schooled)
			{
				scatter(shoal, ticks, cycle, random);
				// Order the fish down the river; they barely change order, so this is quick.
				shoal.byAlong.clear();
				shoal.byAlong.addAll(shoal.fish);
				shoal.byAlong.sort((a, b) -> Double.compare(a.s, b.s));
				for (int n = 0; n < shoal.byAlong.size(); n++)
				{
					shoal.byAlong.get(n).order = n;
				}
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
							if (swimmer.bound == circle)
							{
								swimmer.bound = null;
								if (fishing == circle.npc && drawn(circle, swimmer.item))
								{
									swimmer.circle = circle;
									swimmer.circleLane = roomiestLane(shoal, circle);
								}
							}
							else if (fishing == circle.npc && drawn(circle, swimmer.item) && fewest(shoal, circle, swimmer.item)
								&& circling.getOrDefault(circle, 0) < CIRCLE_MOST && random.nextInt(100) < JOIN_CHANCE)
							{
								swimmer.circle = circle;
								swimmer.circleLane = roomiestLane(shoal, circle);
								ungroup(swimmer);
								circling.merge(circle, 1, Integer::sum);
								circle.waitingSince = cycle;
								circle.nextFill = -1;
							}
						}
					}
				}
				else if (swimmer.shrinkingSince < 0 && (fishing != swimmer.circle.npc || !drawn(swimmer.circle, swimmer.item))
					&& random.nextDouble() < ticks / LEAVE_AFTER)
				{
					leave(shoal, swimmer);
				}
				// Dips start only between bobs.
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
					ungroup(swimmer);
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
	 * Notes the menu option chosen on a river spot (read only).
	 */
	void chose(NPC spot, String option)
	{
		if (spot != null && SPOT_FISH.containsKey(spot.getId()))
		{
			chosenSpot = spot;
			chosenFish = option == null ? null : OPTION_FISH.get(option.toLowerCase());
			if ("lure".equalsIgnoreCase(option))
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INV);
				if (inventory != null && inventory.contains(ItemID.HUNTING_STRIPY_BIRD_FEATHER))
				{
					chosenFish = STRIPY_FISH;
				}
			}
		}
	}

	/**
	 * Whether a kind is drawn to a circle, given the option chosen there.
	 */
	private boolean drawn(Circle circle, int item)
	{
		if (circle.npc != chosenSpot || chosenFish == null)
		{
			return item != RAINBOW;
		}
		return has(chosenFish, item);
	}

	/**
	 * Removes a fish of the caught kind from the circle being fished, picked from its Fishing XP.
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
		// Prefer one already in its lane; otherwise mark one still coming in.
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
	 * Whether a circling fish has reached its lane.
	 */
	private static boolean inLane(River river, Swimmer swimmer)
	{
		return offLane(river, swimmer) <= LANE_ARRIVED;
	}

	/**
	 * How far a circling fish is from its lane, allowing for the lane pulled in from the bank.
	 */
	private static double offLane(River river, Swimmer swimmer)
	{
		Circle circle = swimmer.circle;
		double angle = Math.atan2(swimmer.y - circle.y, swimmer.x - circle.x);
		double[] lane = LANE;
		circlePoint(river, circle, circle.lanes[swimmer.circleLane], angle, lane);
		double out = Math.hypot(lane[0] - circle.x, lane[1] - circle.y);
		return Math.abs(Math.hypot(swimmer.x - circle.x, swimmer.y - circle.y) - out);
	}

	/**
	 * Failsafe: sends filler fish from upstream to a fished circle that's waited too long for a passing one.
	 */
	private void refill(Shoal shoal, Actor fishing, int cycle, ThreadLocalRandom random)
	{
		for (Circle circle : shoal.circles.values())
		{
			if (fishing != circle.npc)
			{
				continue;
			}
			// Fished again after a break, or full: start waiting afresh.
			boolean back = cycle - circle.fishedAt > REFILL_BREAK;
			boolean full = circling.getOrDefault(circle, 0) >= CIRCLE_MOST;
			circle.fishedAt = cycle;
			if (back || full)
			{
				circle.waitingSince = -1;
				circle.nextFill = -1;
			}
			if (full)
			{
				continue;
			}
			if (circle.waitingSince < 0)
			{
				circle.waitingSince = cycle;
			}
			if (cycle - circle.waitingSince < REFILL_AFTER || cycle < circle.nextFill)
			{
				continue;
			}
			circle.nextFill = cycle + random.nextInt(REFILL_GAP_LEAST, REFILL_GAP_MOST + 1);
			List<Integer> wanted = new ArrayList<>();
			for (int kind : shoal.kinds)
			{
				if (drawn(circle, kind) && fewest(shoal, circle, kind))
				{
					wanted.add(kind);
				}
			}
			if (wanted.isEmpty())
			{
				continue;
			}
			double s = Math.max(0, circle.along - REFILL_BEHIND);
			Swimmer swimmer = spawn(shoal, s, true, cycle, random, wanted.get(random.nextInt(wanted.size())), null, 0);
			if (swimmer != null)
			{
				swimmer.bound = circle;
				circling.merge(circle, 1, Integer::sum);
			}
		}
	}

	/**
	 * Whether a kind is among the fewest circling, to keep the kinds even.
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
			if ((swimmer.circle == circle || swimmer.bound == circle) && swimmer.shrinkingSince < 0
				&& counts.containsKey(swimmer.item))
			{
				counts.merge(swimmer.item, 1, Integer::sum);
			}
		}
		return counts.getOrDefault(item, 0) <= counts.values().stream().min(Integer::compare).orElse(0);
	}

	/**
	 * Picks the nearest circle a fish is passing and which side to pass on, kept for the pass.
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
	 * The circle lane with the fewest fish for its length.
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
	 * Speed factor that evens out a circling fish's gaps to its neighbours in its lane.
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
	 * Moves a fish on by some client ticks. Returns false once it has shrunk away.
	 */
	private boolean move(Shoal shoal, Swimmer swimmer, int ticks, int cycle)
	{
		River river = shoal.river;
		Look look = swimmer.look;
		Group group = swimmer.circle == null ? swimmer.group : null;
		int surgePhase = group != null ? group.surgePhase : swimmer.surgePhase;
		double surging = Math.sin(2 * Math.PI * ((cycle + surgePhase) % SURGE_CYCLES) / SURGE_CYCLES);
		// In a group, mostly the group's speed, a little its own.
		double own = group != null ? group.speed * (1 + (swimmer.speed - 1) * OWN_SPEED) : swimmer.speed;
		// How far into its lane a circling fish is, 0 while still swimming in, 1 once there; and how near it has
		// got, 0 at APPROACH_RANGE or further, 1 at its lane: it slows to circling speed as it nears.
		double in = 0;
		double near = 0;
		// Worked out once a tick: how far a circling fish is from its lane.
		double off = Double.MAX_VALUE;
		if (swimmer.circle != null)
		{
			off = offLane(river, swimmer);
			in = Math.max(0, Math.min(1, 1 - (off - LANE_ARRIVED) / LANE_ARRIVING));
			near = Math.max(0, Math.min(1, 1 - (off - LANE_ARRIVED) / APPROACH_RANGE));
		}
		double base = swimmer.circle != null ? TRAVEL_SPEED + (CIRCLE_SPEED - TRAVEL_SPEED) * near : TRAVEL_SPEED;
		double want = base * own * look.pace / 100.0 * (1 + SURGE * look.surge / 100.0 * surging);
		if (swimmer.burstUntil >= 0)
		{
			want *= cycle < swimmer.burstUntil ? BURST_SPEED : 1;
			swimmer.burstUntil = cycle < swimmer.burstUntil ? swimmer.burstUntil : -1;
		}
		// Keep to its place along the group: speed up behind it, ease off ahead.
		double middleX = 0;
		double middleY = 0;
		double middleS = Double.NaN;
		if (group != null && group.members.size() > 1)
		{
			// Once a tick for the whole group.
			if (group.middleAt != cycle)
			{
				group.middleAt = cycle;
				group.middleS = 0;
				group.middleX = 0;
				group.middleY = 0;
				for (Swimmer member : group.members)
				{
					group.middleS += member.s;
					group.middleX += member.x;
					group.middleY += member.y;
				}
				group.middleS /= group.members.size();
				group.middleX /= group.members.size();
				group.middleY /= group.members.size();
			}
			middleS = group.middleS;
			middleX = group.middleX;
			middleY = group.middleY;
			want *= 1 + CATCH_UP * Math.max(-1, Math.min(1, (middleS + swimmer.slotAlong - swimmer.s) / CATCH_UP_RANGE));
		}
		if (swimmer.circle != null)
		{
			// Inner lanes are slower, and gaps are evened out, but only once the fish has reached its lane.
			Circle circle = swimmer.circle;
			int lanes = circle.lanes.length;
			double out = lanes > 1 ? swimmer.circleLane / (double) (lanes - 1) : 1;
			double lane = INNER_LANE_SPEED + (1 - INNER_LANE_SPEED) * out;
			want *= (1 - in * (1 - lane)) * (1 + in * (spacing(shoal, swimmer) - 1));
		}
		// Circling fish change speed more gently once in their lane.
		swimmer.swimming += (want - swimmer.swimming) * Math.min(1, (in >= 1 ? 0.02 : 0.05) * ticks);
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
			// Track progress from the real position, so bends don't make it turn back.
			swimmer.s = Math.max(swimmer.s, river.nearest(swimmer.x, swimmer.y, swimmer.s - LOOK_AHEAD,
				swimmer.s + moved + LOOK_AHEAD));
			double wander = WANDER * (group != null ? group.wander : swimmer.wander).at(cycle);
			// In a group, steer for its own place along it, so the group keeps its shape.
			double aim = Double.isNaN(middleS) ? swimmer.s : Math.max(swimmer.s - LOOK_AHEAD, middleS + swimmer.slotAlong);
			river.at(aim + LOOK_AHEAD, point);
			double across = group != null ? group.across : swimmer.across;
			double offset = share(Math.max(-1, Math.min(1, across + wander)), point) + (group != null ? swimmer.slot : 0);
			// Keep outside any circle being passed.
			pass(shoal, swimmer);
			if (swimmer.passing != null)
			{
				double edge = swimmer.passing.across + swimmer.passSide * (swimmer.passing.radius + CIRCLE_CLEARANCE);
				offset = swimmer.passSide > 0 ? Math.max(offset, edge) : Math.min(offset, edge);
			}
			// Stay within the room.
			offset = Math.max(-point[5], Math.min(point[4], offset));
			swimmer.lateral = offset;
			targetX = point[0] - point[3] * offset;
			targetY = point[1] + point[2] * offset;
		}
		swimmer.targetX = targetX;
		swimmer.targetY = targetY;
		double toward = Math.atan2(targetY - swimmer.y, targetX - swimmer.x);
		if (schooled && swimmer.circle == null)
		{
			toward = flock(shoal, swimmer, group, toward, middleX, middleY);
			// Ease the intended heading, so being shoved and coming back both curve smoothly.
			swimmer.steer = Double.isNaN(swimmer.steer) ? toward
				: swimmer.steer + Math.IEEEremainder(toward - swimmer.steer, 2 * Math.PI) * Math.min(1, STEER_EASE * ticks);
			toward = swimmer.steer;
		}
		else if (swimmer.circle != null)
		{
			// Swimming in to the circle: curve round to it; once in its lane, follow it closely.
			double from = Double.isNaN(swimmer.steer) ? swimmer.facing : swimmer.steer;
			swimmer.steer = off <= LANE_ARRIVED ? toward
				: from + Math.IEEEremainder(toward - from, 2 * Math.PI)
				* Math.min(1, (JOIN_STEER_EASE + (STEER_EASE - JOIN_STEER_EASE) * near) * ticks);
			toward = swimmer.steer;
		}
		double turn = Math.IEEEremainder(toward - swimmer.facing, 2 * Math.PI);
		// Swimming in, it turns gently while far and tightens as it nears.
		double most = TURN_RATE * ticks * (swimmer.circle != null ? JOIN_TURN + (1 - JOIN_TURN) * near : 1);
		swimmer.facing += Math.max(-most, Math.min(most, turn));
		double x = swimmer.x + Math.cos(swimmer.facing) * moved;
		double y = swimmer.y + Math.sin(swimmer.facing) * moved;
		if (!river.isWater(x, y))
		{
			// Heading onto land: step straight for the target instead, still turning smoothly.
			x = swimmer.x + Math.cos(toward) * moved;
			y = swimmer.y + Math.sin(toward) * moved;
		}
		swimmer.x = x;
		swimmer.y = y;
		swimmer.wag = (swimmer.wag + 2 * Math.PI * moved / WAG_DISTANCE) % (2 * Math.PI);
		// Deep while swimming down the river, up at the surface to circle.
		swimmer.depthNow += ((swimmer.circle != null ? 0 : swimmer.deep) - swimmer.depthNow) * Math.min(1, DEPTH_EASE * ticks);
		// Caught fish shrink once in their lane.
		if (swimmer.caught && swimmer.shrinkingSince < 0 && swimmer.circle != null && off <= LANE_ARRIVED)
		{
			swimmer.shrinkingSince = cycle;
		}
		// Shrink near the path's end.
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
	 * Eases a push towards what it should be: at the given share per tick while growing, slower while fading.
	 */
	private static double ease(double now, double wanted, double share)
	{
		boolean fading = Math.abs(wanted) < Math.abs(now);
		return now + (wanted - now) * (fading ? share * REPEL_FADE : share);
	}

	/**
	 * Couzin's zones on top of the pull towards the path: away from any fish too close, lined up with groupmates
	 * near enough, and back towards the group's middle when strayed. Returns the heading to steer for.
	 */
	private static double flock(Shoal shoal, Swimmer swimmer, Group group, double toward, double middleX,
		double middleY)
	{
		double x = Math.cos(toward);
		double y = Math.sin(toward);
		double repelX = 0;
		double repelY = 0;
		double groupRepelX = 0;
		double groupRepelY = 0;
		// Only fish near it along the river: walk out both ways from its place in the order.
		List<Swimmer> byAlong = shoal.byAlong;
		int at = swimmer.order < byAlong.size() && byAlong.get(swimmer.order) == swimmer ? swimmer.order : -1;
		int first = at;
		int last = at;
		while (at >= 0 && first > 0 && swimmer.s - byAlong.get(first - 1).s <= REPEL_WINDOW)
		{
			first--;
		}
		while (at >= 0 && last < byAlong.size() - 1 && byAlong.get(last + 1).s - swimmer.s <= REPEL_WINDOW)
		{
			last++;
		}
		for (int n = first; at >= 0 && n <= last; n++)
		{
			Swimmer other = byAlong.get(n);
			boolean mate = group != null && other.group == group;
			double range = (mate ? GROUP_REPEL_RANGE : REPEL_RANGE) * (swimmer.scale + other.scale) / 2;
			double dx = swimmer.x - other.x;
			double dy = swimmer.y - other.y;
			double apartSquared = dx * dx + dy * dy;
			if (other == swimmer || other.circle != null || apartSquared >= range * range || apartSquared < 0.01)
			{
				continue;
			}
			double apart = Math.sqrt(apartSquared);
			double push = 1 - apart / range;
			if (mate)
			{
				groupRepelX += dx / apart * push;
				groupRepelY += dy / apart * push;
			}
			else
			{
				repelX += dx / apart * push;
				repelY += dy / apart * push;
			}
		}
		swimmer.repelX = ease(swimmer.repelX, repelX, REPEL_EASE);
		swimmer.repelY = ease(swimmer.repelY, repelY, REPEL_EASE);
		swimmer.groupRepelX = ease(swimmer.groupRepelX, groupRepelX, GROUP_REPEL_EASE);
		swimmer.groupRepelY = ease(swimmer.groupRepelY, groupRepelY, GROUP_REPEL_EASE);
		x += REPEL * swimmer.repelX + GROUP_REPEL * swimmer.groupRepelX;
		y += REPEL * swimmer.repelY + GROUP_REPEL * swimmer.groupRepelY;
		if (group != null && group.members.size() > 1)
		{
			double alignX = 0;
			double alignY = 0;
			for (Swimmer member : group.members)
			{
				if (member != swimmer && Math.hypot(member.x - swimmer.x, member.y - swimmer.y) < ALIGN_RANGE)
				{
					alignX += Math.cos(member.facing);
					alignY += Math.sin(member.facing);
				}
			}
			double align = Math.hypot(alignX, alignY);
			if (align > 0)
			{
				x += ALIGN * alignX / align;
				y += ALIGN * alignY / align;
			}
			double apart = Math.hypot(middleX - swimmer.x, middleY - swimmer.y);
			if (apart > COHESION_RANGE * swimmer.scale)
			{
				x += COHESION * (middleX - swimmer.x) / apart;
				y += COHESION * (middleY - swimmer.y) / apart;
			}
		}
		return Math.atan2(y, x);
	}

	/**
	 * Places a fish: heading, wag, bob, dip and tip.
	 */
	private void place(Shoal shoal, Swimmer swimmer, int cycle)
	{
		Look look = swimmer.look;
		// Game orientation: 0 south, 512 west, 1024 north.
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
		// River bobs go down from rest and back.
		int bobbing = swimmer.bobClock % (BOB_CYCLES + BOB_REST_CYCLES);
		int bobAt = bobbing < BOB_CYCLES ? bobbing * 2048 / BOB_CYCLES : -1;
		int bobbed = bobAt < 0 ? 0 : look.rise * (65536 - Perspective.COSINE[bobAt]) >> 17;
		// Dips only go deeper.
		double through = swimmer.dippingSince < 0 ? 0
			: Math.PI * (cycle - swimmer.dippingSince) / Math.max(1, look.dipMillis / 20.0);
		double dip = Math.sin(through);
		swimmer.fish.setZ(waterHeight(x, y, shoal.plane) + look.sink + bobbed
			+ (int) Math.round(look.dipDepth * dip * dip + swimmer.depthNow));
		// Tip with the bob and dip; only at full size.
		double tip = look.tip / 100.0 * ((look.rise > 0 && bobAt >= 0 ? -BOB_PITCH * Perspective.SINE[bobAt] / 65536 : 0)
			- (swimmer.dippingSince >= 0 && look.dipDepth > 0 ? DIP_PITCH * Math.sin(2 * through) : 0));
		int pitch = swimmer.wantStep == GROW_STEPS ? (int) Math.round(tip / FishModels.PITCH_STEP) : 0;
		if (pitch != swimmer.pitch || swimmer.wantStep != swimmer.step)
		{
			// Use only made models; queue a missing one and keep the current model meanwhile.
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
	 * Water height at a place, ignoring bridges above it.
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
	 * A circle lane's point at an angle, pulled in from the bank.
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
	 * Sets whether the water is see-through (117 HD), for fish made from now on.
	 */
	void setSeeThrough(boolean seeThrough)
	{
		this.seeThrough = seeThrough;
	}

	/**
	 * Sets whether fish swim deeper with see-through water, for fish made from now on.
	 */
	void setDeep(boolean deep)
	{
		this.deep = deep;
	}

	/**
	 * Sets whether fish swim in groups or each on its own, for shoals made from now on.
	 */
	void setSchooled(boolean schooled)
	{
		this.schooled = schooled;
	}

	/**
	 * Sets how many river fish there are, 25-100%: 50% gives twice the spacing, so half the fish.
	 */
	void setAmount(int percent)
	{
		spacingScale = 100.0 / percent;
	}

	/**
	 * TEMPORARY: reads the tuning spinners.
	 */
	static void tune(LivelyFishingSpotsConfig config)
	{
		WEIGHTS.put(ItemID.RAW_TROUT, config.debugRiverShareTrout());
		WEIGHTS.put(ItemID.RAW_SALMON, config.debugRiverShareSalmon());
		WEIGHTS.put(ItemID.RAW_PIKE, config.debugRiverSharePike());
		RAINBOW_SHARE = config.debugRiverShareRainbow() / 100.0;
		BOB_CYCLES = config.debugRiverBobCycles();
		BOB_REST_CYCLES = config.debugRiverBobRestCycles();
		CIRCLE_LANES = config.debugRiverCircleLanes();
		CIRCLE_LANE_SPACING = config.debugRiverCircleLaneSpacing();
		CIRCLE_SIZE = config.debugRiverCircleSize();
		CIRCLE_MOST = config.debugRiverCircleMost();
		INNER_LANE_SPEED = config.debugRiverInnerLaneSpeed() / 100.0;
		CIRCLE_CLEARANCE = config.debugRiverCircleClearance();
		SCATTER_RATE = 1.0 / (config.debugRiverScatterSeconds() * 50.0);
		CIRCLE_OFFSET_X = config.debugRiverCircleOffsetX();
		CIRCLE_OFFSET_Y = config.debugRiverCircleOffsetY();
		TRAVEL_SPACING = config.debugRiverTravelSpacing();
		SPREAD = config.debugRiverSpread() / 100.0;
		WANDER = config.debugRiverWander() / 100.0;
	}

	/**
	 * Adds a route picked in game and logs it for the table.
	 */
	void pickRoute(WorldPoint start, WorldPoint end)
	{
		picked.add(0, new WorldPoint[]{start, end});
		log.debug("River route picked: new WorldPoint[]{{new WorldPoint({}, {}, {}), new WorldPoint({}, {}, {})}}",
			start.getX(), start.getY(), start.getPlane(), end.getX(), end.getY(), end.getPlane());
	}

	/**
	 * Removes every fish.
	 */
	void clear()
	{
		for (Shoal shoal : shoals)
		{
			shoal.fish.forEach(swimmer -> swimmer.fish.setActive(false));
		}
		shoals.clear();
	}

	/**
	 * Debug: a place on the water to the screen, at the water's own height, so it isn't lifted onto bridges.
	 */
	private Point canvas(Shoal shoal, double x, double y)
	{
		return Perspective.localToCanvas(client, (int) x, (int) y, waterHeight((int) x, (int) y, shoal.plane));
	}

	/**
	 * Debug: a line across the river at a distance along the path.
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
	 * Debug: banks, path, room, circles and fish steering.
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
			// Room edges.
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
				// Decision line and the circle's own line.
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
				// Avoided area.
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
			// Steering lines: white swimming, green circling, yellow growing, red shrinking.
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
