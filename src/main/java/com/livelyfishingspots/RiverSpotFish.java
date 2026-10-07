package com.livelyfishingspots;

import com.livelyfishingspots.FishModels.Look;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
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
	// Fish in every river and lake, for now all lure/bait.
	private static final int[] ROUTE_FISH = LURE_FISH;
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
	// Hand-picked routes: upstream start, any waypoints the river bends round, then the downstream end. Spots near
	// none get no fish.
	private static final List<WorldPoint[]> ROUTES = List.<WorldPoint[]>of(
		// The River Lum, from north of Edgeville past Barbarian Village and Lumbridge.
		new WorldPoint[]{
			new WorldPoint(3109, 3551, 0), new WorldPoint(3122, 3534, 0), new WorldPoint(3127, 3523, 0),
			new WorldPoint(3131, 3520, 0), new WorldPoint(3131, 3513, 0), new WorldPoint(3126, 3502, 0),
			new WorldPoint(3119, 3496, 0), new WorldPoint(3110, 3489, 0), new WorldPoint(3106, 3481, 0),
			new WorldPoint(3102, 3471, 0), new WorldPoint(3102, 3458, 0), new WorldPoint(3103, 3449, 0),
			new WorldPoint(3105, 3441, 0), new WorldPoint(3112, 3433, 0), new WorldPoint(3105, 3424, 0),
			new WorldPoint(3105, 3417, 0), new WorldPoint(3110, 3405, 0), new WorldPoint(3115, 3394, 0),
			new WorldPoint(3124, 3389, 0), new WorldPoint(3138, 3387, 0), new WorldPoint(3146, 3381, 0),
			new WorldPoint(3151, 3366, 0), new WorldPoint(3161, 3350, 0), new WorldPoint(3171, 3345, 0),
			new WorldPoint(3182, 3347, 0), new WorldPoint(3196, 3341, 0), new WorldPoint(3213, 3319, 0),
			new WorldPoint(3216, 3307, 0), new WorldPoint(3218, 3292, 0), new WorldPoint(3223, 3280, 0),
			new WorldPoint(3234, 3265, 0), new WorldPoint(3237, 3249, 0), new WorldPoint(3240, 3233, 0),
			new WorldPoint(3246, 3221, 0), new WorldPoint(3259, 3213, 0), new WorldPoint(3260, 3199, 0),
			new WorldPoint(3256, 3185, 0), new WorldPoint(3251, 3170, 0), new WorldPoint(3252, 3154, 0)},
		// Shilo Village.
		new WorldPoint[]{new WorldPoint(2832, 2973, 0), new WorldPoint(2880, 2974, 0)});
	// Hand-picked lakes: spawn points spread over each, where new fish come in.
	private static final List<WorldPoint[]> LAKES = List.<WorldPoint[]>of(
		// Between Seers' Village and Sinclair Mansion.
		new WorldPoint[]{new WorldPoint(2713, 3531, 0), new WorldPoint(2727, 3522, 0), new WorldPoint(2718, 3526, 0),
			new WorldPoint(2721, 3524, 0)},
		// South-western Tree Gnome Stronghold.
		new WorldPoint[]{new WorldPoint(2388, 3420, 0), new WorldPoint(2388, 3410, 0), new WorldPoint(2385, 3415, 0),
			new WorldPoint(2393, 3413, 0)});
	// Baked file names of the rivers and lakes, by the route's start or the lake's first spawn point.
	private static final Map<WorldPoint, String> BAKED_NAMES = Map.of(
		new WorldPoint(3109, 3551, 0), "river-lum",
		new WorldPoint(2832, 2973, 0), "shilo-village-river",
		new WorldPoint(2713, 3531, 0), "seers-village-lake",
		new WorldPoint(2388, 3420, 0), "tree-gnome-stronghold-lake");
	// Fixed ring offsets (east, north, local units) for rivers and lakes where the automatic one, away from the
	// nearest bank, mangles the rings; by the route's start or the lake's first spawn point.
	private static final Map<WorldPoint, int[]> RING_OFFSETS = Map.of();
	// Lakes with fewer fish than their water would give, as a share, by the lake's first spawn point.
	private static final Map<WorldPoint, Double> LAKE_FISH_SHARE = Map.of(
		// Tree Gnome Stronghold: a small lake.
		new WorldPoint(2388, 3420, 0), 0.5);
	// A lake is the connected water within LAKE_RADIUS tiles of its spawn points, mapped LAKE_MARGIN tiles past
	// them; a spot uses it within LAKE_REACH tiles of one, and a pick adds to it within LAKE_PICK_REACH of the first.
	static final int LAKE_RADIUS = 6;
	private static final int LAKE_MARGIN = 7;
	private static final int LAKE_REACH = 36;
	private static final int LAKE_PICK_REACH = 48;
	// Lake water per fish at full amount, square local units (about 2.2 tiles).
	private static final double LAKE_ROOM = 512 * 70;
	// Lake goals: least room round one and on the way there, how near counts as there, and least distance to the
	// next (local units); of a few such places anywhere on the lake, the one with the fewest fish per water cell
	// within LAKE_CROWD_RANGE is picked, as is the emptiest spawn point for new fish, so the fish stay spread out.
	// Fish count where they're heading, so schools deciding together don't pile into the same place.
	private static final double LAKE_GOAL_ROOM = 96;
	private static final double LAKE_SIGHT_ROOM = 48;
	private static final double LAKE_GOAL_REACHED = 128;
	private static final double LAKE_GOAL_LEAST = 384;
	private static final int LAKE_GOAL_CHOICES = 6;
	private static final double LAKE_CROWD_RANGE = 384;
	// How near a lake fish comes to a circle before deciding whether to join (local units), how far a solo
	// fish wanders either side of its way, and the share of new arrivals that are solo when schooled.
	private static final double LAKE_JOIN_RANGE = 512;
	private static final double LAKE_WANDER = 128;
	private static final double LAKE_SOLO_SHARE = 1 / 3.0;
	// While a lake circle being fished has room, the chance a school or solo fish picking its next goal heads for
	// the circle's edge instead, so it passes close enough to decide whether to join.
	private static final double LAKE_LURE_CHANCE = 0.3;
	// Lake join chance, percent (tuning spinner); fewer lake fish pass a spot than river fish.
	private static int LAKE_JOIN_CHANCE = 70;
	// Lake fish swim a leg, then slow to a hover and pause before setting off a new way (pause-travel search):
	// pause length in client ticks at random, longer by up to the share of pike in a school, or this many times
	// for a pike alone; the chance per client tick of stopping partway; hover speed (a share of travelling
	// speed); and the set-off, front fish first, the rest this many client ticks later per 16 local units
	// behind, each with a burst this many client ticks long.
	private static final int LAKE_PAUSE_LEAST = 100;
	private static final int LAKE_PAUSE_MOST = 400;
	private static final double LAKE_PIKE_PAUSE = 2.5;
	private static final double LAKE_STOP_CHANCE = 1 / 1500.0;
	private static final double LAKE_HOVER = 0.08;
	private static final int LAKE_DOMINO_TICKS = 6;
	private static final int LAKE_SET_OFF_BURST = 25;
	// Share per client tick a lake fish eases its speed while slowing to a pause; gentler than elsewhere.
	private static final double LAKE_STOP_EASE = 0.012;
	// Lake cruising speed, a share of river travelling speed (tuning spinner).
	private static double LAKE_SPEED = 0.65;
	// Lake fish turn away from banks: nearer than this to one (local units), their aim is pushed out towards open
	// water, this much per unit nearer; the push builds and fades at this share per client tick.
	private static final double LAKE_BANK_ROOM = 160;
	private static final double LAKE_BANK_PUSH = 3;
	private static final double LAKE_BANK_EASE = 0.05;
	// Scattered lake fish regroup: after this many client ticks alone (at random), each joins a school with room, or
	// another scattered fish, within this range (local units).
	private static final int REGROUP_LEAST = 250;
	private static final int REGROUP_MOST = 500;
	private static final double REGROUP_RANGE = 192;
	// Every LAKE_TRIM_EVERY client ticks (100 game ticks, a minute), a lake over its count lets up to LAKE_TRIM_MOST
	// free fish shrink away: of the kind most over its share, the one furthest from the player.
	private static final int LAKE_TRIM_EVERY = 3000;
	private static final int LAKE_TRIM_MOST = 2;
	// Client ticks between new arrivals while a lake is short of fish, at random.
	private static final int LAKE_SPAWN_LEAST = 100;
	private static final int LAKE_SPAWN_MOST = 400;
	// Tiles a spot may be from a route's line to use it; tiles of water mapped past its ends.
	private static final int ROUTE_REACH = 8;
	static final int ROUTE_MARGIN = 4;
	// Tiles of map round a route's points, for its banks; wide enough for its widest water, round islands too.
	static final int BOX_MARGIN = 8;
	// Fish range, tiles (tuning spinner): rivers keep fish only within this far along the path either side of the
	// player's nearest point; a river or lake starts within this plus ACTIVE_MORE tiles of the player, and goes LINGER
	// client ticks after they're more than this plus LEAVE_MORE away.
	static int FISH_RANGE = 28;
	private static final int ACTIVE_MORE = 4;
	private static final int LEAVE_MORE = 16;
	private static final int LINGER = 500;
	// The window is worked out every WINDOW_EVERY client ticks (a game tick): fish grow in at its upstream edge and
	// shrink at its downstream one, those left more than WINDOW_SLACK upstream of it shrink away, and stretches it
	// newly takes in are filled only within HIDDEN_EDGE tiles of its edges, out of sight.
	private static final int WINDOW_EVERY = 30;
	// Fish decide (where to aim, steering round each other, which circles to join, the water's height) every this many
	// client ticks, all a shoal's on the same tick; they move every tick.
	private static final int DECIDE_EVERY = 2;
	// A new river or lake fills one school (or solo fish) a client tick, nearest the player first, not all in one frame.
	// Mapping steps: box, baked water, bank distances, baked path (lakes: roomy water and spawn points).
	private static final int MAP_STEPS = 4;
	private static final double WINDOW_SLACK = 256;
	private static final int HIDDEN_EDGE = 8;
	// Rivers are mapped only within the fish range plus MAP_MORE tiles of the player, and mapped again round them,
	// keeping the fish, once the window comes within REMAP_EDGE tiles of a cut end and they've moved REMAP_MOVE tiles.
	static final int MAP_MORE = 4;
	private static final int REMAP_EDGE = 2;
	private static final int REMAP_MOVE = 3;
	// Spacings between fish along a river that count as an empty stretch to fill: more than a school and its room.
	private static final double EMPTY_GAP = 8;

	// Water grid cell size, local units.
	static final int CELL = 32;
	// Path point spacing, local units.
	private static final double PATH_STEP = 16;
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
	private static double TRAVEL_SPACING = 100;
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
	// (local units, tuning spinners), rising to the surface to circle a spot; share per client tick they ease up or
	// down.
	private static int DEEP_LEAST = 12;
	private static int DEEP_MOST = 96;
	private static final double DEPTH_EASE = 0.03;
	// Nearer a bank than this (local units), deep fish rise towards the surface, all the way at the bank, so they
	// stay clear of a sloping bed.
	private static final double SHALLOW_ROOM = 256;
	// Lake fish may swim this many times deeper than DEEP_MOST.
	private static final double LAKE_DEEPER = 1.15;
	// Groups at least this big may scatter: chance per client tick, the burst's speed (as a share) and length
	// (client ticks), and the push outwards from the group's middle.
	private static final int SCATTER_LEAST = 4;
	private static double SCATTER_RATE = 1.0 / 3300;
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
	// Circle centre offset from the spot away from the nearest bank, and how far round the spot the water is
	// sampled to find which way that is (local units).
	private static int CIRCLE_OFFSET = 28;
	// TEMPORARY: a fixed ring offset for every ring, from the tuning spinners, east and north (local units).
	private static boolean RING_MANUAL;
	private static int RING_EAST;
	private static int RING_NORTH;
	private static final double OFFSET_LOOK = 192;
	// Making rings round: points checked round the outer lane, step and most steps (local units), and how far from
	// where it started a ring may move.
	private static final int ROUND_POINTS = 32;
	private static final double ROUND_STEP = 8;
	private static final int ROUND_STEPS = 48;
	private static final double ROUND_MOST = 384;
	// Gap passing fish keep outside a circle, and how much further off they start steering.
	private static double CIRCLE_CLEARANCE = 11;
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

	// Dead bodies: now and then one drifts down a river, on average every BODY_EVERY client ticks (tuning
	// spinner), at a share of fish speed, sunk and bobbing (local units, client ticks), turning slowly (radians
	// per client tick at most). Fish and bodies pass through each other.
	// The model of object SAILING_CHARTING_GENERIC_CORPSE_LUMBRIDGE_BASIN; there are no gamevals for models.
	private static final int BODY_MODEL = 57609;
	private static double BODY_EVERY = 180 * 60 * 50;
	private static final double BODY_SPEED = 0.45;
	private static final int BODY_SINK = 22;
	private static final int BODY_BOB = 4;
	private static final int BODY_BOB_CYCLES = 200;
	private static final double BODY_TURN = 0.002;
	private static final double BODY_WANDER = 0.3;

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
	/**
	 * A river or lake's water, saved from the scene once, in world cells; and a river's path, in world local units.
	 */
	private static final class Baked
	{
		private final int x0;
		private final int y0;
		private final int width;
		private final BitSet water;
		private final double[] pathX;
		private final double[] pathY;

		private Baked(int x0, int y0, int width, BitSet water, double[] pathX, double[] pathY)
		{
			this.x0 = x0;
			this.y0 = y0;
			this.width = width;
			this.water = water;
			this.pathX = pathX;
			this.pathY = pathY;
		}

		private boolean isWater(int cellX, int cellY)
		{
			int i = cellX - x0;
			int j = cellY - y0;
			return i >= 0 && j >= 0 && i < width && water.get(j * width + i);
		}
	}

	static final class River
	{
		// South-west corner (local units, moved by a map load) and size in cells, across and up.
		int x0;
		int y0;
		final int width;
		final int height;
		// Which cells are water, while mapping; dropped after, as a water cell is any with room.
		boolean[] water;
		// Distance from each cell to the bank, local units; 0 off the water.
		final short[] clearance;
		// Path points, distance along, and room left and right (facing downstream).
		private double[] pathX;
		private double[] pathY;
		private double[] along;
		private double[] left;
		private double[] right;
		private double length;
		// Bank cells' middles, x then y, for debug drawing; worked out when first drawn.
		private int[] banks;
		// Whether its route's start and end were cut, to the loaded scene or the player's reach, so more may be mapped.
		private boolean cutStart;
		private boolean cutEnd;
		// Lakes only (no path): water cells, those roomy enough to head for, and running totals of water cells per
		// tile (a summed-area table) for counting the water round a place.
		private int waterCells;
		private int[] open;
		private int[] tileWater;

		River(int x0, int y0, int width, int height)
		{
			this.x0 = x0;
			this.y0 = y0;
			this.width = width;
			this.height = height;
			water = new boolean[width * height];
			clearance = new short[width * height];
		}

		/**
		 * Distance to the bank, local units; 0 off the water.
		 */
		private double clearanceAt(double x, double y)
		{
			int i = (int) Math.floor((x - x0) / CELL);
			int j = (int) Math.floor((y - y0) / CELL);
			return i >= 0 && j >= 0 && i < width && j < height ? clearance[j * width + i] : 0;
		}

		/**
		 * Lakes: water cells in the tiles reaching a range either side of a place.
		 */
		private int waterNear(double x, double y, double range)
		{
			int tilesX = width / (128 / CELL);
			int tilesY = height / (128 / CELL);
			int i0 = Math.max(0, (int) Math.floor((x - range - x0) / 128));
			int j0 = Math.max(0, (int) Math.floor((y - range - y0) / 128));
			int i1 = Math.min(tilesX, (int) Math.floor((x + range - x0) / 128) + 1);
			int j1 = Math.min(tilesY, (int) Math.floor((y + range - y0) / 128) + 1);
			if (i0 >= i1 || j0 >= j1)
			{
				return 0;
			}
			int row = tilesX + 1;
			return tileWater[j1 * row + i1] - tileWater[j0 * row + i1] - tileWater[j1 * row + i0]
				+ tileWater[j0 * row + i0];
		}

		/**
		 * Distance to the bank, blended between cells so it changes smoothly.
		 */
		private double smoothClearance(double x, double y)
		{
			double fx = (x - x0) / CELL - 0.5;
			double fy = (y - y0) / CELL - 0.5;
			int i = (int) Math.floor(fx);
			int j = (int) Math.floor(fy);
			double tx = fx - i;
			double ty = fy - j;
			return (cell(i, j) * (1 - tx) + cell(i + 1, j) * tx) * (1 - ty)
				+ (cell(i, j + 1) * (1 - tx) + cell(i + 1, j + 1) * tx) * ty;
		}

		private double cell(int i, int j)
		{
			return i >= 0 && j >= 0 && i < width && j < height ? clearance[j * width + i] : 0;
		}

		private boolean isWater(double x, double y)
		{
			int i = (int) Math.floor((x - x0) / CELL);
			int j = (int) Math.floor((y - y0) / CELL);
			return i >= 0 && j >= 0 && i < width && j < height && clearance[j * width + i] > 0;
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
		// Its map, and how many fish it keeps; both change when a map load maps it again.
		private River river;
		private final int[] kinds;
		private int travelling;
		private final Map<NPC, Circle> circles = new HashMap<>();
		private final List<Swimmer> fish = new ArrayList<>();
		// Its fish in order down the river, rebuilt each tick, so each looks only at those near it along it.
		private final List<Swimmer> byAlong = new ArrayList<>();
		private int nextSpawn;
		private int nextSolo;
		private int movedAt;
		// Client tick the player went out of reach, or -1.
		private int farSince = -1;
		// Rivers: where the player was when it was last mapped.
		private WorldPoint mappedAround;
		// River schools and single fish still to fill (place along, how many), nearest the player first; and whether a
		// lake is still filling.
		private final Deque<double[]> toFill = new ArrayDeque<>();
		private boolean lakeFilling;
		// Whether its fish decide this tick, and the client tick they next do.
		private boolean deciding = true;
		private int nextDecision;
		// Rivers: the gap between fish along the path; the stretch with fish (distances along) round the player's
		// place along it; and the client tick it's next worked out.
		private double spacing;
		private double windowFrom;
		private double windowTo;
		private double playerAt;
		private int nextWindow;
		// The body drifting down, or null, and when the next may come.
		private Body body;
		private int nextBody;
		// Lakes: the client tick of the next check for too many fish.
		private int nextTrim;
		// The loaded scene's south-west corner (world tiles) its coordinates are for.
		private int baseX;
		private int baseY;
		// Whether its fish are shrinking away, the shoal going once they have.
		private boolean leaving;
		// Whether every kind's smallest model is made, so fish may come; none come before.
		private boolean ready;

		// A lake (no path), and its spawn points.
		private final boolean lake;
		private double[][] spawns;

		private Shoal(WorldPoint[] route, int plane, int worldView, River river, int[] kinds, int cycle, int travelling,
			double[][] spawns)
		{
			this.route = route;
			this.plane = plane;
			this.worldView = worldView;
			this.river = river;
			this.kinds = kinds;
			this.travelling = travelling;
			lake = spawns != null;
			this.spawns = spawns;
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
		// Lakes: where it's heading, and the client tick it last decided.
		private double goalX = Double.NaN;
		private double goalY;
		private int decidedAt = -1;
		// The client tick it last rolled to scatter.
		private int rolledAt = -1;

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
		// Centre (moved by a map load), outer radius, distance along the path, direction (1 or -1), lane radii.
		private double x;
		private double y;
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
			if (river.pathX == null)
			{
				along = 0;
				across = 0;
				roomLeft = Double.MAX_VALUE;
				roomRight = Double.MAX_VALUE;
				return;
			}
			along = river.nearest(x, y);
			double[] at = new double[6];
			river.at(along, at);
			across = (x - at[0]) * -at[3] + (y - at[1]) * at[2];
			roomLeft = at[4];
			roomRight = at[5];
		}
	}

	private static final class Body
	{
		private final RuneLiteObject object;
		// Distance along the path, place across it (a share of the room), heading and its turn per client tick.
		private double s;
		private final double across;
		private double facing;
		private final double turn;
		private final int bobPhase;
		private int growingSince;
		private int shrinkingSince = -1;
		private int step;

		private Body(RuneLiteObject object, int cycle, ThreadLocalRandom random)
		{
			this.object = object;
			across = random.nextDouble(-BODY_WANDER, BODY_WANDER);
			facing = random.nextDouble(2 * Math.PI);
			turn = random.nextDouble(-BODY_TURN, BODY_TURN);
			bobPhase = random.nextInt(BODY_BOB_CYCLES);
			growingSince = cycle;
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
		// Lakes, on its own: where it's heading; the client tick it sets off from a pause, or -1; and the client
		// tick a scattered fish starts looking for a school, or -1.
		private double goalX = Double.NaN;
		private double goalY;
		private int setOffAt = -1;
		private int regroupAt = -1;
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
		// Lakes: the push away from banks, eased.
		private double bankX;
		private double bankY;
		// Extra depth while swimming down the river (0 without see-through water), and how deep it is now.
		private int deep;
		private double depthNow;
		// Intended heading, eased; NaN until first set.
		private double steer = Double.NaN;
		// The heading it last decided to aim for, and the water's height under it then (or MIN_VALUE to read again);
		// circling, how much faster or slower it goes to even out the gaps in its lane, decided then too.
		private double aim = Double.NaN;
		private double evening = 1;
		private int surface = Integer.MIN_VALUE;
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
	// TEMPORARY: the spawn points of the lake being picked, or null; and the route being picked, for drawing.
	private WorldPoint[] pickedLake;
	private List<WorldPoint> picking = List.of();
	// Stretches of each baked river mapped while the plugin starts, to warm the mapping code up.
	private static final int WARM_ROUNDS = 12;
	// Baked water and paths, by each river or lake's first point.
	private final Map<WorldPoint, Baked> baked = new HashMap<>();
	// TEMPORARY: the latest mapping timings (what, how long), newest first, and the slowest so far.
	private static final int TIMINGS_SHOWN = 6;
	private final Deque<String[]> timings = new ArrayDeque<>();
	private double slowest;
	private String slowestWhat;
	// Kept between gap checks, for the fish's places along the river.
	private double[] gapsAlong = new double[0];
	// Kept between game ticks, for every river and lake to check.
	private final List<WorldPoint[]> everyRoute = new ArrayList<>();
	// Rivers and lakes being mapped, a step a client tick.
	private final List<Opening> openings = new ArrayList<>();
	// Rivers and lakes that couldn't be mapped since the last map load.
	private final Set<WorldPoint[]> unmappable = new HashSet<>();
	// Whether fish swim in groups (schooled) or each on its own (random).
	private boolean schooled = true;
	// Fish spacing times this, from the player's River fish amount.
	private double spacingScale = 1;
	// The same for lakes, from the player's Lake fish amount.
	private double lakeSpacingScale = 1;
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
	// What the local player is interacting with this tick.
	private Actor fishingNow;
	// The body model at each grow step, made when first needed.
	private final Model[] bodyModels = new Model[GROW_STEPS + 1];
	// Scratch for offLane, used on the client thread only.
	private static final double[] LANE = new double[2];
	// Scratch for River.at.
	private final double[] point = new double[6];

	RiverSpotFish(Client client, FishModels models)
	{
		this.client = client;
		this.models = models;
		List<WorldPoint[]> all = new ArrayList<>(ROUTES);
		all.addAll(LAKES);
		for (WorldPoint[] route : all)
		{
			Baked river = loadBaked(route[0]);
			if (river != null)
			{
				baked.put(route[0], river);
			}
		}
		long started = System.nanoTime();
		warmUp();
		log.debug("River mapping warmed up in {} ms", (System.nanoTime() - started) / 100_000 / 10.0);
	}

	/**
	 * Maps stretches of each baked river a few times, keeping nothing, so Java has sped the mapping code up before
	 * the first real map; else that one takes several times as long. Runs while the plugin starts, before any game
	 * events reach it, so it can't clash with real mapping.
	 */
	private void warmUp()
	{
		int reach = FISH_RANGE + MAP_MORE;
		for (Baked saved : baked.values())
		{
			if (saved.pathX == null)
			{
				continue;
			}
			int points = saved.pathX.length;
			for (int round = 0; round < WARM_ROUNDS; round++)
			{
				// A stretch round a place along the river, in world coordinates (a scene based at 0).
				int middle = points * (round + 1) / (WARM_ROUNDS + 1);
				int from = Math.max(0, middle - 2 * reach);
				int to = Math.min(points - 1, middle + 2 * reach);
				int tileX = (int) saved.pathX[middle] / 128;
				int tileY = (int) saved.pathY[middle] / 128;
				River river = new River((tileX - reach) * 128, (tileY - reach) * 128, (2 * reach + 1) * (128 / CELL),
					(2 * reach + 1) * (128 / CELL));
				river.water = bakedWet(saved, 0, 0, river);
				clearances(river);
				int[] ends = {(int) saved.pathX[from], (int) saved.pathY[from], (int) saved.pathX[to], (int) saved.pathY[to]};
				if (bakedPath(river, saved, 0, 0, ends))
				{
					river.at(river.nearest(river.pathX[0], river.pathY[0]), point);
				}
			}
		}
	}

	/**
	 * A baked river or lake's file name: its name, or its first point if it has none.
	 */
	static String bakedName(WorldPoint first)
	{
		String name = BAKED_NAMES.get(first);
		return (name != null ? name : first.getX() + "_" + first.getY() + "_" + first.getPlane()) + ".txt";
	}

	/**
	 * Reads a baked river or lake from the plugin's resources, or null if there's none. Lines are "water y x0 x1 ..."
	 * (runs of water cells in a row, end exclusive) and "path x y x y ...".
	 */
	private static Baked loadBaked(WorldPoint first)
	{
		InputStream in = RiverSpotFish.class.getResourceAsStream("baked/" + bakedName(first));
		if (in == null)
		{
			return null;
		}
		List<int[]> runs = new ArrayList<>();
		double[] path = new double[0];
		int pathCount = 0;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				String[] parts = line.trim().split(" ");
				if (parts[0].equals("water"))
				{
					int y = Integer.parseInt(parts[1]);
					for (int k = 2; k + 1 < parts.length; k += 2)
					{
						runs.add(new int[]{y, Integer.parseInt(parts[k]), Integer.parseInt(parts[k + 1])});
					}
				}
				else if (parts[0].equals("path"))
				{
					path = Arrays.copyOf(path, pathCount + parts.length - 1);
					for (int k = 1; k < parts.length; k++)
					{
						path[pathCount++] = Integer.parseInt(parts[k]);
					}
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't read the baked river {}", first, e);
			return null;
		}
		if (runs.isEmpty())
		{
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int[] run : runs)
		{
			minX = Math.min(minX, run[1]);
			maxX = Math.max(maxX, run[2]);
			minY = Math.min(minY, run[0]);
			maxY = Math.max(maxY, run[0]);
		}
		int width = maxX - minX;
		BitSet water = new BitSet(width * (maxY - minY + 1));
		for (int[] run : runs)
		{
			water.set((run[0] - minY) * width + run[1] - minX, (run[0] - minY) * width + run[2] - minX);
		}
		double[] pathX = null;
		double[] pathY = null;
		if (pathCount >= 4)
		{
			pathX = new double[pathCount / 2];
			pathY = new double[pathCount / 2];
			for (int k = 0; k < pathX.length; k++)
			{
				pathX[k] = path[2 * k];
				pathY[k] = path[2 * k + 1];
			}
		}
		return new Baked(minX, minY, width, water, pathX, pathY);
	}

	/**
	 * Adds a river spot's circle to its route's shoal, starting the shoal if needed.
	 */
	void add(NPC spot)
	{
		if (!SPOT_FISH.containsKey(spot.getId()) || shoalOf(spot) != null)
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
		Shoal shoal = shoalFor(route);
		if (shoal == null)
		{
			// Waits for it to be mapped.
			open(view, route, spot.getWorldLocation().getPlane(), spot);
			return;
		}
		// A lake spot must be on its water.
		if (!shoal.lake || onWater(shoal.river, at))
		{
			attach(shoal, spot, at);
		}
	}

	private Opening openingFor(WorldPoint[] route)
	{
		for (Opening opening : openings)
		{
			if (opening.mapping.route == route)
			{
				return opening;
			}
		}
		return null;
	}

	private Shoal shoalFor(WorldPoint[] route)
	{
		for (Shoal shoal : shoals)
		{
			if (shoal.route == route)
			{
				return shoal;
			}
		}
		return null;
	}

	/**
	 * Whether a place is on the water or within a tile of it.
	 */
	private static boolean onWater(River river, LocalPoint at)
	{
		for (int dx = -128; dx <= 128; dx += 128)
		{
			for (int dy = -128; dy <= 128; dy += 128)
			{
				if (river.isWater(at.getX() + dx, at.getY() + dy))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Starts the river or lake near the player: rivers and lakes within FISH_RANGE + ACTIVE_MORE tiles open, and those
	 * beyond FISH_RANGE + LEAVE_MORE start going. Once a game tick; a route that can't be mapped isn't tried again until the next map
	 * load.
	 */
	void activate()
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (player == null || view == null)
		{
			return;
		}
		WorldPoint me = player.getWorldLocation();
		int cycle = client.getGameCycle();
		List<WorldPoint[]> all = everyRoute;
		all.clear();
		all.addAll(picked);
		all.addAll(ROUTES);
		if (pickedLake != null)
		{
			all.add(pickedLake);
		}
		all.addAll(LAKES);
		for (WorldPoint[] route : all)
		{
			double apart = reachTo(route, me);
			Shoal shoal = shoalFor(route);
			if (shoal == null)
			{
				if (apart <= FISH_RANGE + ACTIVE_MORE && !unmappable.contains(route))
				{
					open(view, route, me.getPlane(), null);
				}
			}
			else if (apart > FISH_RANGE + LEAVE_MORE)
			{
				shoal.farSince = shoal.farSince >= 0 ? shoal.farSince : cycle;
			}
			else
			{
				shoal.farSince = -1;
			}
		}
	}

	/**
	 * Tiles from a place to a route's line through its waypoints, or to a lake's water round its spawn points;
	 * endless on another plane.
	 */
	private double reachTo(WorldPoint[] route, WorldPoint me)
	{
		if (route[0].getPlane() != me.getPlane())
		{
			return Double.MAX_VALUE;
		}
		double nearest = Double.MAX_VALUE;
		if (isLake(route))
		{
			for (WorldPoint spawn : route)
			{
				nearest = Math.min(nearest, Math.max(0, Math.hypot(spawn.getX() - me.getX(), spawn.getY() - me.getY())
					- LAKE_RADIUS));
			}
			return nearest;
		}
		for (int k = 0; k + 1 < route.length; k++)
		{
			double ax = route[k].getX();
			double ay = route[k].getY();
			double bx = route[k + 1].getX() - ax;
			double by = route[k + 1].getY() - ay;
			double t = Math.max(0, Math.min(1, ((me.getX() - ax) * bx + (me.getY() - ay) * by)
				/ Math.max(1, bx * bx + by * by)));
			nearest = Math.min(nearest, Math.hypot(me.getX() - ax - bx * t, me.getY() - ay - by * t));
		}
		return nearest;
	}

	/**
	 * Starts mapping a river or lake, unless it's already being mapped; a spot, if any, waits for it.
	 */
	private void open(WorldView view, WorldPoint[] route, int plane, NPC spot)
	{
		// Only baked rivers and lakes have fish.
		if (!baked.containsKey(route[0]))
		{
			return;
		}
		Opening opening = openingFor(route);
		if (opening == null)
		{
			opening = new Opening(new Mapping(view, route, plane));
			openings.add(opening);
		}
		if (spot != null && !opening.spots.contains(spot))
		{
			opening.spots.add(spot);
		}
	}

	/**
	 * Takes each river and lake being mapped a step on, once a client tick; one done is filled with fish and given its
	 * waiting spots' circles.
	 */
	private void advanceOpenings()
	{
		for (Iterator<Opening> all = openings.iterator(); all.hasNext(); )
		{
			Opening opening = all.next();
			Mapping mapping = opening.mapping;
			long started = System.nanoTime();
			if (!mapping.step())
			{
				note(opening, started);
				continue;
			}
			all.remove();
			if (mapping.river == null)
			{
				unmappable.add(mapping.route);
				continue;
			}
			Shoal shoal = startShoal(mapping);
			for (NPC spot : opening.spots)
			{
				LocalPoint at = spot.getLocalLocation();
				// A lake spot must be on its water.
				if (at != null && shoalOf(spot) == null && (!shoal.lake || onWater(shoal.river, at)))
				{
					attach(shoal, spot, at);
				}
			}
			note(opening, started);
			timeMs(label(mapping.route) + " start: slowest step " + opening.slowestStep, opening.slowest);
			log.debug("{} started over {} client ticks, {} ms in all", label(mapping.route), MAP_STEPS,
				Math.round(opening.total * 10) / 10.0);
		}
	}

	/**
	 * TEMPORARY: notes how long a mapping step took, for the debug panel.
	 */
	private static void note(Opening opening, long started)
	{
		double ms = (System.nanoTime() - started) / 100_000 / 10.0;
		opening.total += ms;
		if (ms > opening.slowest)
		{
			opening.slowest = ms;
			opening.slowestStep = opening.mapping.step;
		}
	}

	/**
	 * Fills a newly mapped river or lake with fish.
	 */
	private Shoal startShoal(Mapping mapping)
	{
		WorldView view = mapping.view;
		WorldPoint[] route = mapping.route;
		int plane = mapping.plane;
		boolean lake = mapping.lake;
		River river = mapping.river;
		List<double[]> spawns = mapping.spawns;
		int[] kinds = ROUTE_FISH;
		// Every size queued, to be made a couple a tick rather than all in one frame: the growing ones first, the
		// smallest at the very front, as new fish start at it and none come until it's made; then the tipped
		// full-size ones.
		for (int item : kinds)
		{
			for (int step = GROW_STEPS; step >= 1; step--)
			{
				models.queue(item, size(item, step), 0, 0, true);
			}
			Look look = FishModels.look(item);
			double most = look.tip / 100.0 * ((look.rise > 0 ? BOB_PITCH : 0) + (look.dipDepth > 0 ? DIP_PITCH : 0));
			int steps = (int) Math.ceil(most / FishModels.PITCH_STEP);
			for (int pitch = -steps; pitch <= steps; pitch++)
			{
				models.queue(item, size(item, GROW_STEPS), pitch, 0, false);
			}
		}
		for (int item : kinds)
		{
			models.queue(item, size(item, 1), 0, 0, true);
		}
		int cycle = client.getGameCycle();
		Shoal shoal = new Shoal(route, plane, view.getId(), river, kinds, cycle, fishCount(route, river, lake),
			lake ? spawns.toArray(new double[0][]) : null);
		shoal.mappedAround = playerTile();
		shoal.baseX = view.getBaseX();
		shoal.baseY = view.getBaseY();
		if (lake)
		{
			log.debug("Lake {}: {} water cells, {} fish", route[0], river.waterCells, shoal.travelling);
		}
		else
		{
			log.debug("River route {} to {}: path {} long, {} fish", route[0], route[route.length - 1], (int) river.length,
				shoal.travelling);
		}
		shoals.add(shoal);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (lake)
		{
			// Filled with schools spread across it over the next few ticks.
			shoal.lakeFilling = true;
			shoal.nextSpawn = cycle;
			return shoal;
		}
		// Fish spread evenly along the stretch round the player, a school a tick, nearest first.
		shoal.spacing = TRAVEL_SPACING * spacingScale;
		updateWindow(shoal);
		fillStretch(shoal, shoal.windowFrom, shoal.windowTo, random);
		shoal.nextSpawn = cycle + spawnGap(random);
		shoal.nextSolo = cycle + (int) (spawnGap(random) * SOLO_EVERY);
		shoal.nextBody = cycle + bodyGap(random);
		return shoal;
	}

	/**
	 * Queues a stretch of river's fill: groups spread evenly along it, and single fish between them when schooled,
	 * nearest the player first, each its place along and how many (0 for a single fish).
	 */
	private void fillStretch(Shoal shoal, double from, double to, ThreadLocalRandom random)
	{
		River river = shoal.river;
		double gap = shoal.spacing;
		List<double[]> arrivals = new ArrayList<>();
		for (double along = from + gap * random.nextDouble(); along < Math.min(to, river.length - GROUP_LENGTH); )
		{
			int count = schooled ? random.nextInt(GROUP_LEAST, GROUP_MOST + 1) : 1;
			arrivals.add(new double[]{along, count});
			along += gap * count * (1 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY));
		}
		for (double along = from + gap * SOLO_EVERY * random.nextDouble(); schooled && along < to; )
		{
			arrivals.add(new double[]{along, 0});
			along += gap * SOLO_EVERY * (1 + random.nextDouble(-SPAWN_STRAY, SPAWN_STRAY));
		}
		double playerAt = shoal.playerAt;
		arrivals.sort((a, b) -> Double.compare(Math.abs(a[0] - playerAt), Math.abs(b[0] - playerAt)));
		shoal.toFill.addAll(arrivals);
	}

	/**
	 * Whether every kind a shoal shows has its smallest model made.
	 */
	private boolean smallestMade(Shoal shoal)
	{
		for (int item : shoal.kinds)
		{
			if (models.made(item, size(item, 1), 0, 0) == null)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Fills a little more of a new river or lake: one school or single fish a client tick, growing in a little out of
	 * step.
	 */
	private void fillSome(Shoal shoal, int cycle, ThreadLocalRandom random)
	{
		if (shoal.leaving)
		{
			shoal.toFill.clear();
			shoal.lakeFilling = false;
			return;
		}
		double[] arrival = shoal.toFill.poll();
		stagger = true;
		if (arrival != null && arrival[1] == 0)
		{
			spawn(shoal, arrival[0], null, true, cycle, random, kind(shoal.kinds, random, false), null, 0);
		}
		else if (arrival != null)
		{
			spawnGroup(shoal, arrival[0], null, true, cycle, random, (int) arrival[1]);
		}
		if (shoal.lakeFilling)
		{
			int all = 0;
			for (Swimmer swimmer : shoal.fish)
			{
				all += swimmer.shrinkingSince < 0 ? 1 : 0;
			}
			if (all < shoal.travelling)
			{
				all += lakeArrival(shoal, openPlace(shoal.river, random), shoal.travelling - all, cycle, random);
			}
			shoal.lakeFilling = all < shoal.travelling;
		}
		stagger = false;
	}

	/**
	 * Fills the empty stretches of a river's window, as newly loaded or newly taken in: wherever fish are more than
	 * EMPTY_GAP spacings apart (more than a school and the room round it), but only within HIDDEN_EDGE tiles of its
	 * edges, out of the player's sight, and not once it has its fish.
	 */
	private void fillGaps(Shoal shoal, ThreadLocalRandom random)
	{
		double gap = shoal.spacing;
		if (gapsAlong.length < shoal.fish.size() + 1)
		{
			gapsAlong = new double[shoal.fish.size() * 2 + 1];
		}
		double[] along = gapsAlong;
		int count = 0;
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.shrinkingSince < 0 && swimmer.circle == null && swimmer.s >= shoal.windowFrom
				&& swimmer.s <= shoal.windowTo)
			{
				along[count++] = swimmer.s;
			}
		}
		// Groups, and single fish half as many again.
		if (count >= windowFish(shoal) * (1 + 1 / SOLO_EVERY))
		{
			return;
		}
		along[count++] = shoal.windowTo;
		Arrays.sort(along, 0, count);
		double hidden = Math.max(0, FISH_RANGE - HIDDEN_EDGE) * 128.0;
		double hiddenFrom = shoal.playerAt - hidden;
		double hiddenTo = shoal.playerAt + hidden;
		double last = shoal.windowFrom;
		for (int k = 0; k < count; k++)
		{
			double s = along[k];
			if (s - last > EMPTY_GAP * gap)
			{
				double from = last + gap * 2;
				double to = s - gap * 2;
				// Only the parts out of the player's sight.
				if (Math.min(to, hiddenFrom) - from > gap)
				{
					fillStretch(shoal, from, Math.min(to, hiddenFrom), random);
				}
				if (to - Math.max(from, hiddenTo) > gap)
				{
					fillStretch(shoal, Math.max(from, hiddenTo), to, random);
				}
			}
			last = s;
		}
	}

	private WorldPoint playerTile()
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : player.getWorldLocation();
	}

	/**
	 * Rivers: whether the window's come near a cut end of what's mapped and the player has moved on since, so it
	 * should be mapped again round them.
	 */
	private boolean outgrown(Shoal shoal)
	{
		River river = shoal.river;
		WorldPoint me = playerTile();
		boolean nearEnd = river.cutStart && shoal.playerAt - (FISH_RANGE + REMAP_EDGE) * 128.0 < 0
			|| river.cutEnd && shoal.playerAt + (FISH_RANGE + REMAP_EDGE) * 128.0 > river.length;
		return nearEnd && me != null && (shoal.mappedAround == null || me.distanceTo2D(shoal.mappedAround) >= REMAP_MOVE);
	}

	/**
	 * Rivers: centres the stretch with fish on the player's nearest place along the path.
	 */
	private void updateWindow(Shoal shoal)
	{
		Player player = client.getLocalPlayer();
		LocalPoint me = player == null ? null : player.getLocalLocation();
		River river = shoal.river;
		if (me != null)
		{
			shoal.playerAt = river.nearest(me.getX(), me.getY());
		}
		shoal.windowFrom = Math.max(0, shoal.playerAt - FISH_RANGE * 128.0);
		shoal.windowTo = Math.min(river.length, shoal.playerAt + FISH_RANGE * 128.0);
	}

	/**
	 * Rivers: fish kept along the window at their spacing.
	 */
	private static int windowFish(Shoal shoal)
	{
		return Math.max(1, (int) Math.round((shoal.windowTo - shoal.windowFrom) / shoal.spacing));
	}

	/**
	 * How many fish a river (from its path's length) or lake (from its water) keeps.
	 */
	private int fishCount(WorldPoint[] route, River river, boolean lake)
	{
		return Math.max(1, (int) Math.round(lake
			? river.waterCells * CELL * CELL / (LAKE_ROOM * lakeSpacingScale) * LAKE_FISH_SHARE.getOrDefault(route[0], 1.0)
			: river.length / (TRAVEL_SPACING * spacingScale)));
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
			for (int k = 0; k + 1 < route.length; k++)
			{
				double ax = route[k].getX();
				double ay = route[k].getY();
				double bx = route[k + 1].getX() - ax;
				double by = route[k + 1].getY() - ay;
				double t = Math.max(0, Math.min(1, ((spot.getX() - ax) * bx + (spot.getY() - ay) * by)
					/ Math.max(1, bx * bx + by * by)));
				double d = Math.hypot(spot.getX() - ax - bx * t, spot.getY() - ay - by * t);
				if (d <= nearest)
				{
					nearest = d;
					best = route;
				}
			}
		}
		if (best != null)
		{
			return best;
		}
		// Or a lake whose spawn points it's among.
		for (WorldPoint[] lake : lakes())
		{
			for (WorldPoint spawn : lake)
			{
				if (spawn.getPlane() == spot.getPlane() && spot.distanceTo2D(spawn) <= LAKE_REACH)
				{
					return lake;
				}
			}
		}
		return null;
	}

	/**
	 * TEMPORARY: every river and lake, picked ones too, for baking.
	 */
	List<WorldPoint[]> routesAndLakes()
	{
		List<WorldPoint[]> all = new ArrayList<>(picked);
		all.addAll(ROUTES);
		all.addAll(lakes());
		return all;
	}

	private List<WorldPoint[]> lakes()
	{
		List<WorldPoint[]> all = new ArrayList<>(LAKES);
		if (pickedLake != null)
		{
			all.add(0, pickedLake);
		}
		return all;
	}

	boolean isLake(WorldPoint[] route)
	{
		return route == pickedLake || LAKES.contains(route);
	}

	/**
	 * Gives a spot its circle.
	 */
	private void attach(Shoal shoal, NPC spot, LocalPoint at)
	{
		// A fixed offset for this river or lake (or the tuning spinners'), else away from the nearest bank.
		int[] fixed = RING_MANUAL ? new int[]{RING_EAST, RING_NORTH} : RING_OFFSETS.get(shoal.route[0]);
		double x;
		double y;
		if (fixed != null)
		{
			x = at.getX() + fixed[0];
			y = at.getY() + fixed[1];
		}
		else
		{
			double[] away = awayFromBank(shoal.river, at.getX(), at.getY());
			double[] round = roundRing(shoal.river, at.getX() + away[0] * CIRCLE_OFFSET,
				at.getY() + away[1] * CIRCLE_OFFSET);
			x = round[0];
			y = round[1];
		}
		if (RING_MANUAL)
		{
			WorldPoint key = shoal.route[0];
			log.debug("Ring offset: Map.entry(new WorldPoint({}, {}, {}), new int[]{{}, {}})", key.getX(), key.getY(),
				key.getPlane(), RING_EAST, RING_NORTH);
		}
		Circle circle = new Circle(spot, shoal.river, x, y, ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
		shoal.circles.put(spot, circle);
		shoal.leaving = false;
		// Fish already past the decision point skip it.
		for (Swimmer swimmer : shoal.fish)
		{
			if (!shoal.lake && swimmer.s > circle.along - JOIN_BEFORE)
			{
				swimmer.decided.add(circle);
			}
		}
	}

	/**
	 * Moves a ring's middle away from banks a little at a time until its outer lane is clear of them all round, so
	 * it isn't pulled out of shape; within ROUND_MOST of where it started, else the place with the fewest points too
	 * near a bank.
	 */
	private static double[] roundRing(River river, double startX, double startY)
	{
		double x = startX;
		double y = startY;
		double bestX = x;
		double bestY = y;
		int fewest = Integer.MAX_VALUE;
		for (int step = 0; step <= ROUND_STEPS; step++)
		{
			int near = 0;
			double pushX = 0;
			double pushY = 0;
			for (int a = 0; a < ROUND_POINTS; a++)
			{
				double angle = 2 * Math.PI * a / ROUND_POINTS;
				double cos = Math.cos(angle);
				double sin = Math.sin(angle);
				if (river.clearanceAt(x + CIRCLE_SIZE * cos, y + CIRCLE_SIZE * sin) < BANK_GAP)
				{
					near++;
					pushX -= cos;
					pushY -= sin;
				}
			}
			if (near < fewest)
			{
				fewest = near;
				bestX = x;
				bestY = y;
			}
			double push = Math.hypot(pushX, pushY);
			if (near == 0 || push < 0.01)
			{
				break;
			}
			x += pushX / push * ROUND_STEP;
			y += pushY / push * ROUND_STEP;
			if (Math.hypot(x - startX, y - startY) > ROUND_MOST)
			{
				break;
			}
		}
		return new double[]{bestX, bestY};
	}

	/**
	 * Which way open water lies from a place, as a unit direction: towards the roomiest water round it.
	 */
	private static double[] awayFromBank(River river, double x, double y)
	{
		double dx = 0;
		double dy = 0;
		for (int a = 0; a < 16; a++)
		{
			double angle = 2 * Math.PI * a / 16;
			double room = river.clearanceAt(x + OFFSET_LOOK * Math.cos(angle), y + OFFSET_LOOK * Math.sin(angle));
			dx += room * Math.cos(angle);
			dy += room * Math.sin(angle);
		}
		double length = Math.hypot(dx, dy);
		return length > 0 ? new double[]{dx / length, dy / length} : new double[]{0, 0};
	}

	/**
	 * Maps a baked river or lake a step at a time: a new one a step a client tick, so no one frame takes it all; a
	 * remap all at once. It maps the scene it was made for; a map load starts it again.
	 */
	private final class Mapping
	{
		private final WorldView view;
		private final WorldPoint[] route;
		private final int plane;
		private final boolean lake;
		private final Baked saved;
		// Lakes: where new fish come in, on the water; filled on the last step.
		private final List<double[]> spawns = new ArrayList<>();
		// Steps done, and the map, null if it can't be mapped.
		private int step;
		private River river;
		// Between steps: lakes' spawn points in the scene; rivers' ends in the scene (local units) and whether they
		// were cut.
		private LocalPoint[] points;
		private int[] ends;
		private boolean[] cut;

		private Mapping(WorldView view, WorldPoint[] route, int plane)
		{
			this.view = view;
			this.route = route;
			this.plane = plane;
			lake = isLake(route);
			saved = baked.get(route[0]);
		}

		/**
		 * Does the next step. Returns true once done, with the map, or null if it can't be mapped.
		 */
		private boolean step()
		{
			boolean ok = saved != null && (lake ? lakeStep(step) : routeStep(step));
			step++;
			if (!ok)
			{
				river = null;
				return true;
			}
			if (step == MAP_STEPS)
			{
				river.water = null;
				return true;
			}
			return false;
		}

		/**
		 * Does every step at once. Returns the map, or null if it can't be mapped.
		 */
		private River all()
		{
			while (!step())
			{
				// Next step.
			}
			return river;
		}

		/**
		 * A river's steps: its box, its water, its bank distances, its path. Returns false (logged) if it can't be
		 * mapped. A long route is cut off at the edge of what's loaded, and mapped again when more loads.
		 */
		private boolean routeStep(int n)
		{
			WorldPoint last = route[route.length - 1];
			switch (n)
			{
				case 0:
				{
					// The route cut to the scene round the player, and the box round what's left.
					List<int[]> stops = new ArrayList<>();
					cut = new boolean[2];
					Player player = client.getLocalPlayer();
					LocalPoint me = player == null ? null : player.getLocalLocation();
					if (view.getScene() == null || !clip(view, route, me, stops, cut) || stops.size() < 2)
					{
						log.debug("River route {} to {}: not in sight", route[0], last);
						return false;
					}
					int minX = Integer.MAX_VALUE;
					int minY = Integer.MAX_VALUE;
					int maxX = Integer.MIN_VALUE;
					int maxY = Integer.MIN_VALUE;
					for (int[] stop : stops)
					{
						minX = Math.min(minX, stop[0]);
						minY = Math.min(minY, stop[1]);
						maxX = Math.max(maxX, stop[0]);
						maxY = Math.max(maxY, stop[1]);
					}
					int[] first = stops.get(0);
					int[] end = stops.get(stops.size() - 1);
					ends = new int[]{first[0] * 128 + 64, first[1] * 128 + 64, end[0] * 128 + 64, end[1] * 128 + 64};
					river = new River((minX - BOX_MARGIN) * 128, (minY - BOX_MARGIN) * 128,
						(maxX - minX + 2 * BOX_MARGIN + 1) * (128 / CELL), (maxY - minY + 2 * BOX_MARGIN + 1) * (128 / CELL));
					return true;
				}
				case 1:
					river.water = bakedWet(saved, view.getBaseX(), view.getBaseY(), river);
					return true;
				case 2:
					clearances(river);
					return true;
				default:
					if (saved.pathX == null || !bakedPath(river, saved, view.getBaseX(), view.getBaseY(), ends))
					{
						log.debug("River route {} to {}: no baked path here", route[0], last);
						return false;
					}
					river.cutStart = cut[0];
					river.cutEnd = cut[1];
					return true;
			}
		}

		/**
		 * A lake's steps: its box, its water, its bank distances, its roomy water and spawn points snapped onto the
		 * water. Returns false (logged) if it can't be mapped.
		 */
		private boolean lakeStep(int n)
		{
			switch (n)
			{
				case 0:
				{
					points = new LocalPoint[route.length];
					int minX = Integer.MAX_VALUE;
					int minY = Integer.MAX_VALUE;
					int maxX = Integer.MIN_VALUE;
					int maxY = Integer.MIN_VALUE;
					for (int k = 0; k < route.length; k++)
					{
						points[k] = LocalPoint.fromWorld(view, route[k]);
						if (view.getScene() == null || points[k] == null)
						{
							log.debug("Lake {}: not all in sight", route[0]);
							return false;
						}
						minX = Math.min(minX, points[k].getSceneX());
						minY = Math.min(minY, points[k].getSceneY());
						maxX = Math.max(maxX, points[k].getSceneX());
						maxY = Math.max(maxY, points[k].getSceneY());
					}
					river = new River((minX - LAKE_MARGIN) * 128, (minY - LAKE_MARGIN) * 128,
						(maxX - minX + 2 * LAKE_MARGIN + 1) * (128 / CELL), (maxY - minY + 2 * LAKE_MARGIN + 1) * (128 / CELL));
					return true;
				}
				case 1:
					river.water = bakedWet(saved, view.getBaseX(), view.getBaseY(), river);
					return true;
				case 2:
					clearances(river);
					return true;
				default:
				{
					List<Integer> open = new ArrayList<>();
					for (int c = 0; c < river.width * river.height; c++)
					{
						if (river.water[c])
						{
							river.waterCells++;
							if (river.clearance[c] >= LAKE_GOAL_ROOM)
							{
								open.add(c);
							}
						}
					}
					river.open = open.stream().mapToInt(Integer::intValue).toArray();
					int perTile = 128 / CELL;
					int tilesX = river.width / perTile;
					int tilesY = river.height / perTile;
					int row = tilesX + 1;
					river.tileWater = new int[row * (tilesY + 1)];
					for (int tj = 0; tj < tilesY; tj++)
					{
						for (int ti = 0; ti < tilesX; ti++)
						{
							int count = 0;
							for (int j = tj * perTile; j < (tj + 1) * perTile; j++)
							{
								for (int i = ti * perTile; i < (ti + 1) * perTile; i++)
								{
									count += river.water[j * river.width + i] ? 1 : 0;
								}
							}
							river.tileWater[(tj + 1) * row + ti + 1] = count + river.tileWater[tj * row + ti + 1]
								+ river.tileWater[(tj + 1) * row + ti] - river.tileWater[tj * row + ti];
						}
					}
					for (LocalPoint point : points)
					{
						int cell = nearestWater(river, point, river.water);
						if (cell < 0)
						{
							log.debug("Lake {}: spawn point {} isn't on its water", route[0], point);
							continue;
						}
						spawns.add(new double[]{cellX(river, cell), cellY(river, cell)});
					}
					if (river.open.length == 0 || spawns.isEmpty())
					{
						log.debug("Lake {}: no room to swim", route[0]);
						return false;
					}
					return true;
				}
			}
		}
	}

	/**
	 * A river or lake being mapped a step a client tick, and the spots waiting for it.
	 */
	private static final class Opening
	{
		private Mapping mapping;
		private final List<NPC> spots = new ArrayList<>();
		// TEMPORARY: its slowest step (ms) and which, and all its steps together, for the debug panel.
		private double slowest;
		private int slowestStep;
		private double total;

		private Opening(Mapping mapping)
		{
			this.mapping = mapping;
		}
	}

	private static double cellX(River river, int cell)
	{
		return river.x0 + (cell % river.width + 0.5) * CELL;
	}

	private static double cellY(River river, int cell)
	{
		return river.y0 + (cell / river.width + 0.5) * CELL;
	}

	/**
	 * A random roomy place on a lake.
	 */
	private static double[] openPlace(River river, ThreadLocalRandom random)
	{
		int cell = river.open[random.nextInt(river.open.length)];
		return new double[]{cellX(river, cell), cellY(river, cell)};
	}

	/**
	 * Whether a straight swim between two places stays in roomy water, past the first bit, which may be by a bank.
	 */
	private static boolean inSight(River river, double fromX, double fromY, double toX, double toY)
	{
		double apart = Math.hypot(toX - fromX, toY - fromY);
		for (double d = CELL * 2; d < apart; d += CELL)
		{
			double t = d / apart;
			if (river.clearanceAt(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t) < LAKE_SIGHT_ROOM)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A new place to head for on a lake, not too near: the emptiest of a few anywhere on it, by way of a waypoint
	 * when it's out of sight; or null.
	 */
	private static double[] lakeGoal(Shoal shoal, double fromX, double fromY, ThreadLocalRandom random)
	{
		double[] best = null;
		double fewest = Double.MAX_VALUE;
		int found = 0;
		for (int tries = 0; tries < 24 && found < LAKE_GOAL_CHOICES; tries++)
		{
			double[] goal = openPlace(shoal.river, random);
			if (Math.hypot(goal[0] - fromX, goal[1] - fromY) < LAKE_GOAL_LEAST)
			{
				continue;
			}
			found++;
			double near = crowd(shoal, goal[0], goal[1]);
			if (near < fewest)
			{
				fewest = near;
				best = goal;
			}
		}
		return best == null ? null : waypoint(shoal.river, fromX, fromY, best[0], best[1], random);
	}

	/**
	 * Fish per water cell round a place, each fish counted where it's heading: its school's goal, its own, or the
	 * circle it's in.
	 */
	private static double crowd(Shoal shoal, double x, double y)
	{
		int near = 0;
		for (Swimmer swimmer : shoal.fish)
		{
			double toX = swimmer.x;
			double toY = swimmer.y;
			Group group = swimmer.group;
			if (swimmer.circle != null)
			{
				toX = swimmer.circle.x;
				toY = swimmer.circle.y;
			}
			else if (group != null && group.members.size() > 1 && !Double.isNaN(group.goalX))
			{
				toX = group.goalX;
				toY = group.goalY;
			}
			else if (group == null && !Double.isNaN(swimmer.goalX))
			{
				toX = swimmer.goalX;
				toY = swimmer.goalY;
			}
			near += Math.abs(toX - x) < LAKE_CROWD_RANGE && Math.abs(toY - y) < LAKE_CROWD_RANGE ? 1 : 0;
		}
		return near / (double) Math.max(1, shoal.river.waterNear(x, y, LAKE_CROWD_RANGE));
	}

	/**
	 * The lake spawn point with the fewest fish round it.
	 */
	private static double[] emptiestSpawn(Shoal shoal)
	{
		double[] best = shoal.spawns[0];
		double fewest = Double.MAX_VALUE;
		for (double[] spawn : shoal.spawns)
		{
			double near = crowd(shoal, spawn[0], spawn[1]);
			if (near < fewest)
			{
				fewest = near;
				best = spawn;
			}
		}
		return best;
	}

	/**
	 * The way towards a place on a lake: the place itself when in sight, else a roomy place in sight of both.
	 */
	private static double[] waypoint(River river, double fromX, double fromY, double toX, double toY,
		ThreadLocalRandom random)
	{
		if (inSight(river, fromX, fromY, toX, toY))
		{
			return new double[]{toX, toY};
		}
		double[] best = null;
		double bestLeft = Double.MAX_VALUE;
		for (int tries = 0; tries < 20; tries++)
		{
			double[] via = openPlace(river, random);
			if (!inSight(river, fromX, fromY, via[0], via[1]))
			{
				continue;
			}
			if (inSight(river, via[0], via[1], toX, toY))
			{
				return via;
			}
			double left = Math.hypot(toX - via[0], toY - via[1]);
			if (left < bestLeft)
			{
				bestLeft = left;
				best = via;
			}
		}
		return best != null ? best : new double[]{toX, toY};
	}

	/**
	 * Where a lake fish not circling steers for: its school's way (or its own) to the goal, keeping its place in
	 * the school, and outside circles it isn't joining. Picks a new goal on arriving, or leaves at a spawn point.
	 */
	private void lakeTarget(Shoal shoal, Swimmer swimmer, Group group, double middleX, double middleY, int cycle,
		double[] into)
	{
		River river = shoal.river;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		boolean grouped = group != null && group.members.size() > 1;
		// Paused: hover on its heading; set off with a burst when its turn comes.
		if (swimmer.setOffAt >= 0)
		{
			if (cycle < swimmer.setOffAt)
			{
				into[0] = swimmer.x + Math.cos(swimmer.facing) * LOOK_AHEAD;
				into[1] = swimmer.y + Math.sin(swimmer.facing) * LOOK_AHEAD;
				offBanks(river, swimmer, into);
				return;
			}
			swimmer.setOffAt = -1;
			swimmer.burstUntil = cycle + LAKE_SET_OFF_BURST;
		}
		double fromX = grouped ? middleX : swimmer.x;
		double fromY = grouped ? middleY : swimmer.y;
		double goalX = grouped ? group.goalX : swimmer.goalX;
		double goalY = grouped ? group.goalY : swimmer.goalY;
		boolean reached = !Double.isNaN(goalX) && Math.hypot(goalX - fromX, goalY - fromY) < LAKE_GOAL_REACHED;
		// A school decides once a tick, whichever member comes first.
		boolean deciding = !grouped || group.decidedAt != cycle;
		if (grouped)
		{
			group.decidedAt = cycle;
		}
		boolean stopping = !reached && !Double.isNaN(goalX) && swimmer.bound == null
			&& random.nextDouble() < LAKE_STOP_CHANCE;
		if (deciding && (Double.isNaN(goalX) || reached || stopping))
		{
			boolean hadGoal = !Double.isNaN(goalX);
			Circle lure = luring(shoal);
			double[] goal;
			if (swimmer.bound != null && !grouped)
			{
				goal = waypoint(river, fromX, fromY, swimmer.bound.x, swimmer.bound.y, random);
			}
			else if (lure != null && random.nextDouble() < LAKE_LURE_CHANCE)
			{
				// Towards the edge of the circle being fished, on this side.
				double apart = Math.max(1, Math.hypot(fromX - lure.x, fromY - lure.y));
				double out = lure.radius + CIRCLE_CLEARANCE + LAKE_GOAL_REACHED;
				goal = waypoint(river, fromX, fromY, lure.x + (fromX - lure.x) / apart * out,
					lure.y + (fromY - lure.y) / apart * out, random);
			}
			else
			{
				goal = lakeGoal(shoal, fromX, fromY, random);
			}
			if (goal != null)
			{
				goalX = goal[0];
				goalY = goal[1];
				if (grouped)
				{
					group.goalX = goalX;
					group.goalY = goalY;
				}
				else
				{
					swimmer.goalX = goalX;
					swimmer.goalY = goalY;
				}
			}
			// Pause before the new leg, unless heading for a circle or just arrived.
			if (hadGoal && swimmer.bound == null)
			{
				pause(swimmer, grouped ? group : null, fromX, fromY, goalX, goalY, cycle, random);
				into[0] = swimmer.x + Math.cos(swimmer.facing) * LOOK_AHEAD;
				into[1] = swimmer.y + Math.sin(swimmer.facing) * LOOK_AHEAD;
				offBanks(river, swimmer, into);
				return;
			}
		}
		double ux = Math.cos(swimmer.facing);
		double uy = Math.sin(swimmer.facing);
		double apart = Double.isNaN(goalX) ? 0 : Math.hypot(goalX - fromX, goalY - fromY);
		if (apart > 1)
		{
			ux = (goalX - fromX) / apart;
			uy = (goalY - fromY) / apart;
		}
		double ahead;
		double side;
		if (grouped)
		{
			ahead = LOOK_AHEAD + swimmer.slotAlong;
			side = swimmer.slot;
		}
		else
		{
			ahead = LOOK_AHEAD;
			side = LAKE_WANDER * WANDER * swimmer.wander.at(cycle);
		}
		double x = fromX + ux * ahead - uy * side;
		double y = fromY + uy * ahead + ux * side;
		// Keep outside circles it isn't joining.
		for (Circle circle : shoal.circles.values())
		{
			if (circle == swimmer.bound)
			{
				continue;
			}
			double clear = circle.radius + CIRCLE_CLEARANCE;
			double out = Math.hypot(x - circle.x, y - circle.y);
			if (out < clear && out > 1)
			{
				x = circle.x + (x - circle.x) * clear / out;
				y = circle.y + (y - circle.y) * clear / out;
			}
		}
		into[0] = x;
		into[1] = y;
		offBanks(river, swimmer, into);
	}

	/**
	 * Pushes a lake fish's aim away from banks, harder the nearer, easing the push in and out so it turns smoothly.
	 */
	private static void offBanks(River river, Swimmer swimmer, double[] into)
	{
		double room = river.smoothClearance(swimmer.x, swimmer.y);
		double wantX = 0;
		double wantY = 0;
		if (room < LAKE_BANK_ROOM)
		{
			double[] away = awayFromBank(river, swimmer.x, swimmer.y);
			double push = (LAKE_BANK_ROOM - room) * LAKE_BANK_PUSH;
			wantX = away[0] * push;
			wantY = away[1] * push;
		}
		swimmer.bankX = ease(swimmer.bankX, wantX, LAKE_BANK_EASE * DECIDE_EVERY);
		swimmer.bankY = ease(swimmer.bankY, wantY, LAKE_BANK_EASE * DECIDE_EVERY);
		into[0] += swimmer.bankX;
		into[1] += swimmer.bankY;
	}

	/**
	 * The lake circle being fished, while it has room, or null.
	 */
	private Circle luring(Shoal shoal)
	{
		for (Circle circle : shoal.circles.values())
		{
			if (circle.npc == fishingNow && circling.getOrDefault(circle, 0) < CIRCLE_MOST)
			{
				return circle;
			}
		}
		return null;
	}

	/**
	 * Starts a lake fish, or its school, pausing: longer with pike, setting off front first towards the new goal.
	 */
	private static void pause(Swimmer swimmer, Group group, double fromX, double fromY, double goalX, double goalY,
		int cycle, ThreadLocalRandom random)
	{
		List<Swimmer> members = group != null ? group.members : List.of(swimmer);
		int pike = 0;
		for (Swimmer member : members)
		{
			pike += member.item == ItemID.RAW_PIKE ? 1 : 0;
		}
		double longer = group != null ? 1 + pike / (double) members.size() : pike > 0 ? LAKE_PIKE_PAUSE : 1;
		int until = cycle + (int) (random.nextInt(LAKE_PAUSE_LEAST, LAKE_PAUSE_MOST + 1) * longer);
		double ux = Math.cos(swimmer.facing);
		double uy = Math.sin(swimmer.facing);
		double apart = Double.isNaN(goalX) ? 0 : Math.hypot(goalX - fromX, goalY - fromY);
		if (apart > 1)
		{
			ux = (goalX - fromX) / apart;
			uy = (goalY - fromY) / apart;
		}
		double front = Double.NEGATIVE_INFINITY;
		for (Swimmer member : members)
		{
			front = Math.max(front, (member.x - fromX) * ux + (member.y - fromY) * uy);
		}
		for (Swimmer member : members)
		{
			double behind = front - ((member.x - fromX) * ux + (member.y - fromY) * uy);
			member.setOffAt = until + (int) (behind / 16 * LAKE_DOMINO_TICKS);
		}
	}

	/**
	 * A route's points in scene tiles, cut back along it to the loaded scene (less ROUTE_MARGIN) and to the fish range
	 * plus MAP_MORE round the player: its start (or where it comes in), the waypoints inside, and its end (or where it
	 * goes out). Fills stops, and cut with whether the start and the end were cut; false if no part of it is inside.
	 */
	private static boolean clip(WorldView view, WorldPoint[] route, LocalPoint around, List<int[]> stops, boolean[] cut)
	{
		double[] low = {ROUTE_MARGIN, ROUTE_MARGIN};
		double[] high = {view.getSizeX() - 1 - ROUTE_MARGIN, view.getSizeY() - 1 - ROUTE_MARGIN};
		if (around != null)
		{
			int reach = FISH_RANGE + MAP_MORE;
			low = new double[]{Math.max(low[0], around.getSceneX() - reach), Math.max(low[1], around.getSceneY() - reach)};
			high = new double[]{Math.min(high[0], around.getSceneX() + reach), Math.min(high[1], around.getSceneY() + reach)};
		}
		int lastLeg = -1;
		double lastLeave = 0;
		for (int k = 0; k + 1 < route.length; k++)
		{
			double[] from = {route[k].getX() - view.getBaseX(), route[k].getY() - view.getBaseY()};
			double[] way = {route[k + 1].getX() - route[k].getX(), route[k + 1].getY() - route[k].getY()};
			// Liang-Barsky: the share of the way along this leg it enters and leaves the scene.
			double enter = 0;
			double leave = 1;
			boolean outside = false;
			for (int axis = 0; axis < 2 && !outside; axis++)
			{
				for (int side = 0; side < 2; side++)
				{
					double p = side == 0 ? -way[axis] : way[axis];
					double q = side == 0 ? from[axis] - low[axis] : high[axis] - from[axis];
					if (p == 0)
					{
						outside |= q < 0;
						continue;
					}
					double t = q / p;
					if (p < 0)
					{
						enter = Math.max(enter, t);
					}
					else
					{
						leave = Math.min(leave, t);
					}
				}
			}
			if (outside || enter > leave)
			{
				continue;
			}
			if (stops.isEmpty())
			{
				cut[0] = k > 0 || enter > 0;
			}
			// The leg's start: the route's, or where it comes back into the scene.
			if (stops.isEmpty() || enter > 0)
			{
				stops.add(new int[]{(int) Math.round(from[0] + way[0] * enter),
					(int) Math.round(from[1] + way[1] * enter)});
			}
			stops.add(new int[]{(int) Math.round(from[0] + way[0] * leave),
				(int) Math.round(from[1] + way[1] * leave)});
			lastLeg = k;
			lastLeave = leave;
		}
		cut[1] = lastLeg < route.length - 2 || lastLeave < 1;
		return lastLeg >= 0;
	}

	/**
	 * The nearest marked cell within a tile of a place, or -1.
	 */
	private static int nearestWater(River river, LocalPoint at, boolean[] marked)
	{
		int nearestCell = -1;
		double nearest = Double.MAX_VALUE;
		int reach = 128 / CELL + 1;
		int ci = (int) Math.floor((at.getX() - river.x0) / (double) CELL);
		int cj = (int) Math.floor((at.getY() - river.y0) / (double) CELL);
		for (int j = Math.max(0, cj - reach); j <= Math.min(river.height - 1, cj + reach); j++)
		{
			for (int i = Math.max(0, ci - reach); i <= Math.min(river.width - 1, ci + reach); i++)
			{
				if (!marked[j * river.width + i])
				{
					continue;
				}
				double d = Math.hypot(river.x0 + (i + 0.5) * CELL - at.getX(), river.y0 + (j + 0.5) * CELL - at.getY());
				if (d <= 128 && d < nearest)
				{
					nearest = d;
					nearestCell = j * river.width + i;
				}
			}
		}
		return nearestCell;
	}

	/**
	 * Which grid cells are water, from a baked river or lake.
	 */
	private static boolean[] bakedWet(Baked saved, int baseX, int baseY, River river)
	{
		int perTile = 128 / CELL;
		int width = river.width;
		boolean[] wet = new boolean[width * river.height];
		int cellX0 = baseX * perTile + Math.floorDiv(river.x0, CELL);
		int cellY0 = baseY * perTile + Math.floorDiv(river.y0, CELL);
		for (int j = 0; j < river.height; j++)
		{
			for (int i = 0; i < width; i++)
			{
				wet[j * width + i] = saved.isWater(cellX0 + i, cellY0 + j);
			}
		}
		return wet;
	}

	/**
	 * Lays a river's path along its baked one, between the points in its box nearest its two ends (scene local units:
	 * start x, y, end x, y); false if they're the wrong way round or too close.
	 */
	private static boolean bakedPath(River river, Baked saved, int baseX, int baseY, int[] ends)
	{
		double offsetX = baseX * 128.0;
		double offsetY = baseY * 128.0;
		int from = nearestPoint(saved, river, ends[0] + offsetX, ends[1] + offsetY, offsetX, offsetY);
		int to = nearestPoint(saved, river, ends[2] + offsetX, ends[3] + offsetY, offsetX, offsetY);
		if (from < 0 || to - from < 2)
		{
			return false;
		}
		int count = to - from + 1;
		double[] xs = new double[count];
		double[] ys = new double[count];
		for (int k = 0; k < count; k++)
		{
			xs[k] = saved.pathX[from + k] - offsetX;
			ys[k] = saved.pathY[from + k] - offsetY;
		}
		finishPath(river, xs, ys, count);
		return true;
	}

	/**
	 * The baked path point inside a river's box nearest a place (world local units), or -1.
	 */
	private static int nearestPoint(Baked saved, River river, double x, double y, double offsetX, double offsetY)
	{
		double west = river.x0 + offsetX;
		double south = river.y0 + offsetY;
		double east = west + river.width * CELL;
		double north = south + river.height * CELL;
		int best = -1;
		double nearest = Double.MAX_VALUE;
		for (int k = 0; k < saved.pathX.length; k++)
		{
			double px = saved.pathX[k];
			double py = saved.pathY[k];
			if (px < west || px >= east || py < south || py >= north)
			{
				continue;
			}
			double d = (px - x) * (px - x) + (py - y) * (py - y);
			if (d < nearest)
			{
				nearest = d;
				best = k;
			}
		}
		return best;
	}

	/**
	 * Debug: a river's bank cells' middles, x then y, worked out the first time they're drawn.
	 */
	private static int[] banks(River river)
	{
		if (river.banks == null)
		{
			int width = river.width;
			int cells = width * river.height;
			int count = 0;
			for (int c = 0; c < cells; c++)
			{
				count += river.clearance[c] > 0 && river.clearance[c] <= CELL ? 1 : 0;
			}
			int[] banks = new int[count * 2];
			int k = 0;
			for (int c = 0; c < cells; c++)
			{
				if (river.clearance[c] > 0 && river.clearance[c] <= CELL)
				{
					banks[k++] = river.x0 + (c % width) * CELL + CELL / 2;
					banks[k++] = river.y0 + (c / width) * CELL + CELL / 2;
				}
			}
			river.banks = banks;
		}
		return river.banks;
	}

	// Working array for clearances, kept from one map to the next.
	private static int[] distances = new int[0];

	/**
	 * Distance from each water cell to the bank, by a two-pass chamfer transform.
	 */
	static void clearances(River river)
	{
		int width = river.width;
		int height = river.height;
		int cells = width * height;
		distances = distances.length < cells ? new int[cells] : distances;
		int[] d = distances;
		int diagonal = (int) Math.round(CELL * Math.sqrt(2));
		for (int c = 0; c < cells; c++)
		{
			d[c] = river.water[c] ? Integer.MAX_VALUE / 4 : 0;
		}
		for (int j = 0; j < height; j++)
		{
			for (int i = 0; i < width; i++)
			{
				int c = j * width + i;
				if (i > 0)
				{
					d[c] = Math.min(d[c], d[c - 1] + CELL);
				}
				if (j > 0)
				{
					d[c] = Math.min(d[c], d[c - width] + CELL);
					if (i > 0)
					{
						d[c] = Math.min(d[c], d[c - width - 1] + diagonal);
					}
					if (i < width - 1)
					{
						d[c] = Math.min(d[c], d[c - width + 1] + diagonal);
					}
				}
			}
		}
		for (int j = height - 1; j >= 0; j--)
		{
			for (int i = width - 1; i >= 0; i--)
			{
				int c = j * width + i;
				if (i < width - 1)
				{
					d[c] = Math.min(d[c], d[c + 1] + CELL);
				}
				if (j < height - 1)
				{
					d[c] = Math.min(d[c], d[c + width] + CELL);
					if (i < width - 1)
					{
						d[c] = Math.min(d[c], d[c + width + 1] + diagonal);
					}
					if (i > 0)
					{
						d[c] = Math.min(d[c], d[c + width - 1] + diagonal);
					}
				}
			}
		}
		// Measured to the cell edge, not its centre; kept in whole local units.
		for (int c = 0; c < cells; c++)
		{
			river.clearance[c] = (short) (river.water[c] ? Math.min(Short.MAX_VALUE, d[c] - CELL / 2) : 0);
		}
	}

	/**
	 * Sets a river's path from points along it, spaced evenly, with the room either side.
	 */
	private static void finishPath(River river, double[] xs, double[] ys, int count)
	{
		double[][] path = even(xs, ys, count);
		double[] pathX = path[0];
		double[] pathY = path[1];
		int points = pathX.length;
		river.pathX = pathX;
		river.pathY = pathY;
		river.along = new double[points];
		river.left = new double[points];
		river.right = new double[points];
		double walked = 0;
		for (int p = 0; p < points; p++)
		{
			walked += p == 0 ? 0 : Math.hypot(pathX[p] - pathX[p - 1], pathY[p] - pathY[p - 1]);
			river.along[p] = walked;
			// To the path's left, across its way here.
			int ahead = Math.min(p + 1, points - 1);
			int behind = Math.max(p - 1, 0);
			double dx = pathX[ahead] - pathX[behind];
			double dy = pathY[ahead] - pathY[behind];
			double d = Math.max(1e-6, Math.hypot(dx, dy));
			river.left[p] = Math.max(0, outTo(river, pathX[p], pathY[p], -dy / d, dx / d) - BANK_GAP);
			river.right[p] = Math.max(0, outTo(river, pathX[p], pathY[p], dy / d, -dx / d) - BANK_GAP);
		}
		river.length = walked;
	}

	/**
	 * Respaces points evenly, PATH_STEP apart; returns their x and y.
	 */
	static double[][] even(double[] xs, double[] ys, int count)
	{
		double[] lengths = new double[count];
		double total = 0;
		for (int k = 0; k + 1 < count; k++)
		{
			lengths[k] = Math.hypot(xs[k + 1] - xs[k], ys[k + 1] - ys[k]);
			total += lengths[k];
		}
		int points = Math.max(2, (int) Math.ceil(total / PATH_STEP) + 1);
		double[] evenX = new double[points];
		double[] evenY = new double[points];
		int k = 0;
		double walked = 0;
		for (int p = 0; p < points; p++)
		{
			double want = p * total / (points - 1);
			while (k < count - 2 && walked + lengths[k] < want)
			{
				walked += lengths[k];
				k++;
			}
			int next = Math.min(k + 1, count - 1);
			double t = Math.max(0, Math.min(1, (want - walked) / Math.max(1e-6, lengths[k])));
			evenX[p] = xs[k] + (xs[next] - xs[k]) * t;
			evenY[p] = ys[k] + (ys[next] - ys[k]) * t;
		}
		return new double[][]{evenX, evenY};
	}

	/**
	 * How far a ray stays in the water, local units.
	 */
	private static double outTo(River river, double x, double y, double wayX, double wayY)
	{
		double out = 0;
		while (out < 2048 && river.isWater(x + wayX * (out + 4), y + wayY * (out + 4)))
		{
			// The bank is at least this far from here in any direction, so jump that far, in 4s near it.
			out += Math.max(4, river.clearanceAt(x + wayX * out, y + wayY * out) - CELL);
		}
		return out;
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
	 * Adds a group of fish (at most so many) from a distance along the path, or round a place on a lake, growing in or
	 * full size. Returns how many.
	 */
	private int spawnGroup(Shoal shoal, double s, double[] place, boolean grow, int cycle, ThreadLocalRandom random,
		int most)
	{
		if (!schooled)
		{
			spawn(shoal, s, place, grow, cycle, random, kind(shoal.kinds, random, rainbow(shoal.kinds, random)), null, 0);
			return 1;
		}
		Group group = new Group(random);
		// A rainbow fish group is all rainbow fish, and tighter, being smaller.
		boolean rainbow = rainbow(shoal.kinds, random);
		double radius = GROUP_RADIUS * scale(kind(shoal.kinds, random, rainbow));
		// Mostly round, a little oval either way; sometimes flat, and then small.
		boolean flat = random.nextDouble() < FLAT_CHANCE;
		int count = Math.min(most, random.nextInt(GROUP_LEAST, (flat ? FLAT_MOST : GROUP_MOST) + 1));
		double wide = flat ? 1.6 : random.nextDouble(0.85, 1.2);
		double deep = flat ? 0.35 : random.nextDouble(0.85, 1.2);
		// On a lake, the school starts heading for a goal, its fish placed round the place facing it.
		double ux = 1;
		double uy = 0;
		if (shoal.lake)
		{
			double[] goal = lakeGoal(shoal, place[0], place[1], random);
			if (goal != null)
			{
				group.goalX = goal[0];
				group.goalY = goal[1];
				double apart = Math.max(1, Math.hypot(goal[0] - place[0], goal[1] - place[1]));
				ux = (goal[0] - place[0]) / apart;
				uy = (goal[1] - place[1]) / apart;
			}
		}
		for (int n = 0; n < count; n++)
		{
			// A random place in the group's oval.
			double angle = random.nextDouble(2 * Math.PI);
			double out = radius * Math.sqrt(random.nextDouble());
			double ahead = out * Math.sin(angle) * deep;
			double across = out * Math.cos(angle) * wide;
			double along = shoal.lake ? 0 : Math.max(0, Math.min(shoal.river.length, s + radius + ahead));
			double[] at = null;
			if (shoal.lake)
			{
				at = new double[]{place[0] + ux * ahead - uy * across, place[1] + uy * ahead + ux * across};
				at = shoal.river.isWater(at[0], at[1]) ? at : place;
			}
			Swimmer added = spawn(shoal, along, at, grow, cycle, random, kind(shoal.kinds, random, rainbow), group, across);
			if (added != null)
			{
				added.slotAlong = ahead;
			}
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
	 * Adds a lake school, or a solo fish, at a place, with at most so many fish. Returns how many.
	 */
	private int lakeArrival(Shoal shoal, double[] place, int most, int cycle, ThreadLocalRandom random)
	{
		if (most < GROUP_LEAST || schooled && random.nextDouble() < LAKE_SOLO_SHARE)
		{
			spawn(shoal, 0, place, true, cycle, random, kind(shoal.kinds, random, false), null, 0);
			return 1;
		}
		return spawnGroup(shoal, 0, place, true, cycle, random, most);
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
	private Swimmer spawn(Shoal shoal, double s, double[] place, boolean grow, int cycle, ThreadLocalRandom random,
		int item, Group group, double slot)
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
			if (!shoal.lake && s > circle.along - JOIN_BEFORE)
			{
				swimmer.decided.add(circle);
			}
		}
		swimmer.group = group;
		swimmer.slot = slot;
		swimmer.scale = scale(item);
		int deepest = Math.max(DEEP_LEAST, shoal.lake ? (int) (DEEP_MOST * LAKE_DEEPER) : DEEP_MOST);
		swimmer.deep = seeThrough && deep ? random.nextInt(DEEP_LEAST, deepest + 1) : 0;
		swimmer.depthNow = swimmer.deep;
		if (group != null)
		{
			group.members.add(swimmer);
		}
		if (shoal.lake)
		{
			swimmer.x = place[0];
			swimmer.y = place[1];
			swimmer.s = swimmer.x;
			swimmer.facing = group != null && !Double.isNaN(group.goalX)
				? Math.atan2(group.goalY - place[1], group.goalX - place[0]) : random.nextDouble(2 * Math.PI);
		}
		else
		{
			shoal.river.at(s, point);
			double offset = group != null ? share(group.across, point) + slot : share(swimmer.across, point);
			swimmer.x = point[0] - point[3] * offset;
			swimmer.y = point[1] + point[2] * offset;
			swimmer.facing = Math.atan2(point[3], point[2]);
		}
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
	 * Removes a spot's circle; its fish swim on. Returns whether it was a spot here.
	 */
	boolean remove(NPC spot)
	{
		for (Opening opening : openings)
		{
			if (opening.spots.remove(spot))
			{
				return true;
			}
		}
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
			// Filler fish on their way swim on as ordinary fish; nobody keeps the gone circle.
			if (swimmer.bound == circle)
			{
				swimmer.bound = null;
				swimmer.goalX = Double.NaN;
			}
			swimmer.decided.remove(circle);
		}
		return true;
	}

	/**
	 * Now and then breaks up a big group: each fish darts outwards from its middle at a burst of speed, then swims on
	 * alone in a lane of its own.
	 */
	private static void scatter(Shoal shoal, int ticks, int cycle, ThreadLocalRandom random)
	{
		// Each big group rolls once, the first time one of its fish comes up.
		for (int n = 0; n < shoal.fish.size(); n++)
		{
			Group group = shoal.fish.get(n).group;
			if (group == null || group.members.size() < SCATTER_LEAST || group.rolledAt == cycle)
			{
				continue;
			}
			group.rolledAt = cycle;
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
				if (shoal.lake)
				{
					member.goalX = Double.NaN;
					member.regroupAt = cycle + random.nextInt(REGROUP_LEAST, REGROUP_MOST + 1);
				}
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
	 * Lakes: scattered fish, once alone a while, join a nearby school with room, or pair up with another scattered
	 * fish into a new one. Rainbow fish school only with rainbow fish.
	 */
	private static void regroup(Shoal shoal, int cycle, ThreadLocalRandom random)
	{
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.regroupAt < 0 || cycle < swimmer.regroupAt || !alone(swimmer))
			{
				continue;
			}
			Swimmer nearest = null;
			double nearestApart = REGROUP_RANGE;
			for (Swimmer other : shoal.fish)
			{
				if (other == swimmer || (other.item == RAINBOW) != (swimmer.item == RAINBOW))
				{
					continue;
				}
				boolean school = other.group != null && other.group.members.size() < GROUP_MOST && other.circle == null
					&& other.shrinkingSince < 0;
				boolean ready = other.regroupAt >= 0 && cycle >= other.regroupAt && alone(other);
				double apart = Math.hypot(other.x - swimmer.x, other.y - swimmer.y);
				if ((school || ready) && apart < nearestApart)
				{
					nearest = other;
					nearestApart = apart;
				}
			}
			if (nearest == null)
			{
				continue;
			}
			Group group = nearest.group;
			if (group == null)
			{
				group = new Group(random);
				group.goalX = nearest.goalX;
				group.goalY = nearest.goalY;
				join(group, nearest, random);
			}
			join(group, swimmer, random);
		}
	}

	private static boolean alone(Swimmer swimmer)
	{
		return swimmer.group == null && swimmer.circle == null && swimmer.bound == null && swimmer.shrinkingSince < 0;
	}

	/**
	 * Puts a fish into a school at a random place in it.
	 */
	private static void join(Group group, Swimmer swimmer, ThreadLocalRandom random)
	{
		double angle = random.nextDouble(2 * Math.PI);
		double out = GROUP_RADIUS * swimmer.scale * Math.sqrt(random.nextDouble());
		swimmer.slot = out * Math.cos(angle);
		swimmer.slotAlong = out * Math.sin(angle);
		swimmer.regroupAt = -1;
		swimmer.setOffAt = -1;
		swimmer.group = group;
		group.members.add(swimmer);
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
	 * Sends a circling fish back down the river, or off round the lake.
	 */
	private static void leave(Shoal shoal, Swimmer swimmer)
	{
		swimmer.circle = null;
		if (shoal.lake)
		{
			swimmer.goalX = Double.NaN;
			return;
		}
		swimmer.s = Math.max(swimmer.s, shoal.river.nearest(swimmer.x, swimmer.y));
	}

	/**
	 * Adds the kinds of every river and lake in sight, so their models are kept.
	 */
	void addKinds(Set<Integer> swimming)
	{
		// Every kind a river or lake may show, not only those swimming now, so a rare kind's models aren't dropped
		// and made again.
		for (Shoal shoal : shoals)
		{
			for (int kind : shoal.kinds)
			{
				swimming.add(kind);
			}
		}
	}

	/**
	 * Moves every fish, each client tick.
	 */
	void swim()
	{
		long opened = System.nanoTime();
		advanceOpenings();
		TickTimes.add("River: opening", opened);
		if (shoals.isEmpty())
		{
			return;
		}
		int cycle = client.getGameCycle();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Player player = client.getLocalPlayer();
		Actor fishing = player == null ? null : player.getInteracting();
		fishingNow = fishing;
		List<NPC> moved = this.moved;
		moved.clear();
		for (Iterator<Shoal> all = shoals.iterator(); all.hasNext(); )
		{
			Shoal shoal = all.next();
			// Rivers and lakes the player's been far from a while shrink their fish away, then go.
			if (shoal.farSince >= 0 && cycle - shoal.farSince > LINGER && !shoal.leaving)
			{
				shoal.leaving = true;
				for (Swimmer swimmer : shoal.fish)
				{
					swimmer.shrinkingSince = swimmer.shrinkingSince >= 0 ? swimmer.shrinkingSince : cycle;
				}
			}
			if (shoal.leaving && shoal.fish.isEmpty())
			{
				if (shoal.body != null)
				{
					shoal.body.object.setActive(false);
				}
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
			long started = System.nanoTime();
			shoal.ready = shoal.ready || smallestMade(shoal);
			if (shoal.ready)
			{
				fillSome(shoal, cycle, random);
			}
			started = TickTimes.add("River: filling", started);
			shoal.deciding = cycle >= shoal.nextDecision;
			if (shoal.deciding)
			{
				shoal.nextDecision = cycle + DECIDE_EVERY;
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
			// Lakes keep their fish for good, topping up to the cap from the emptiest spawn point.
			int everyFish = travelling;
			for (int count : circling.values())
			{
				everyFish += count;
			}
			if (shoal.lake && shoal.ready && !shoal.leaving && cycle >= shoal.nextSpawn && everyFish < shoal.travelling)
			{
				lakeArrival(shoal, emptiestSpawn(shoal), shoal.travelling - everyFish, cycle, random);
				shoal.nextSpawn = cycle + random.nextInt(LAKE_SPAWN_LEAST, LAKE_SPAWN_MOST + 1);
			}
			if (shoal.lake && cycle >= shoal.nextTrim)
			{
				trimLake(shoal, cycle);
				shoal.nextTrim = cycle + LAKE_TRIM_EVERY;
			}
			started = TickTimes.add("River: spawning", started);
			// Rivers keep fish round the player, filling stretches newly taken in out of sight.
			if (!shoal.lake && !shoal.leaving && cycle >= shoal.nextWindow)
			{
				updateWindow(shoal);
				// Walked towards the end of what's mapped: map again round the player, keeping the fish.
				WorldView view = client.getTopLevelWorldView();
				if (outgrown(shoal) && view != null)
				{
					remap(view, shoal);
				}
				if (shoal.toFill.isEmpty())
				{
					fillGaps(shoal, random);
				}
				shoal.nextWindow = cycle + WINDOW_EVERY;
			}
			started = TickTimes.add("River: window, remap", started);
			// Rivers spawn at the window's upstream edge on a steady timer so no gaps open, capped at twice the target.
			if (!shoal.lake && shoal.ready && !shoal.leaving && cycle >= shoal.nextSpawn)
			{
				int count = travelling < 2 * windowFish(shoal)
					? spawnGroup(shoal, shoal.windowFrom, null, true, cycle, random, Integer.MAX_VALUE) : 1;
				shoal.nextSpawn = Math.max(shoal.nextSpawn + spawnGap(random) * count, cycle);
			}
			if (!shoal.lake && shoal.ready && !shoal.leaving && schooled && cycle >= shoal.nextSolo)
			{
				if (travelling < 2 * windowFish(shoal))
				{
					spawn(shoal, shoal.windowFrom, null, true, cycle, random, kind(shoal.kinds, random, false), null, 0);
				}
				shoal.nextSolo = Math.max(shoal.nextSolo + (int) (spawnGap(random) * SOLO_EVERY), cycle);
			}
			if (!shoal.lake)
			{
				drift(shoal, ticks, cycle, random);
			}
			if (!shoal.leaving && fishing != null)
			{
				refill(shoal, fishing, cycle, random);
			}
			started = TickTimes.add("River: spawning", started);
			if (schooled)
			{
				if (shoal.deciding)
				{
					scatter(shoal, ticks * DECIDE_EVERY, cycle, random);
					if (shoal.lake)
					{
						regroup(shoal, cycle, random);
					}
				}
				// Order the fish down the river for deciding; they barely change order, so this is quick.
				if (shoal.deciding)
				{
					shoal.byAlong.clear();
					shoal.byAlong.addAll(shoal.fish);
					shoal.byAlong.sort((a, b) -> Double.compare(a.s, b.s));
					for (int n = 0; n < shoal.byAlong.size(); n++)
					{
						shoal.byAlong.get(n).order = n;
					}
				}
			}
			started = TickTimes.add("River: groups", started);
			for (Iterator<Swimmer> it = shoal.fish.iterator(); it.hasNext(); )
			{
				Swimmer swimmer = it.next();
				if (swimmer.circle == null)
				{
					for (Circle circle : shoal.deciding ? shoal.circles.values() : List.<Circle>of())
					{
						// Lake fish may decide again once they've swum well away.
						double apart = Math.hypot(swimmer.x - circle.x, swimmer.y - circle.y);
						if (shoal.lake && apart > 2 * LAKE_JOIN_RANGE)
						{
							swimmer.decided.remove(circle);
						}
						if (swimmer.circle == null && !swimmer.decided.contains(circle)
							&& (shoal.lake ? apart <= LAKE_JOIN_RANGE : swimmer.s >= circle.along - JOIN_BEFORE))
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
								&& circling.getOrDefault(circle, 0) < CIRCLE_MOST
								&& random.nextInt(100) < (shoal.lake ? LAKE_JOIN_CHANCE : JOIN_CHANCE))
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
			TickTimes.add("River: fish", started);
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
	 * Client ticks until the next body, at random around BODY_EVERY.
	 */
	private static int bodyGap(ThreadLocalRandom random)
	{
		return (int) Math.min(Integer.MAX_VALUE / 2, -BODY_EVERY * Math.log(1 - random.nextDouble()));
	}

	/**
	 * Starts a body drifting down now and then, and moves it.
	 */
	private void drift(Shoal shoal, int ticks, int cycle, ThreadLocalRandom random)
	{
		Model model = shoal.body == null && !shoal.leaving && cycle >= shoal.nextBody ? bodyModel(1) : null;
		if (model != null)
		{
			RuneLiteObject object = client.createRuneLiteObject();
			object.setModel(model);
			shoal.body = new Body(object, cycle, random);
			shoal.body.step = 1;
			shoal.body.s = shoal.windowFrom;
		}
		if (shoal.body != null && !moveBody(shoal, shoal.body, ticks, cycle))
		{
			shoal.body.object.setActive(false);
			shoal.body = null;
			shoal.nextBody = cycle + bodyGap(random);
		}
	}

	/**
	 * Moves a body: growing in at the start, shrinking away at the end. Returns false once it has gone.
	 */
	private boolean moveBody(Shoal shoal, Body body, int ticks, int cycle)
	{
		River river = shoal.river;
		body.s += TRAVEL_SPEED * BODY_SPEED * ticks;
		body.facing += body.turn * ticks;
		if (body.shrinkingSince < 0 && (shoal.leaving || body.s >= shoal.windowTo - TRAVEL_SPEED * BODY_SPEED * GROW_CYCLES
			|| body.s < shoal.windowFrom - WINDOW_SLACK))
		{
			body.shrinkingSince = cycle;
		}
		int step = GROW_STEPS;
		if (body.shrinkingSince >= 0)
		{
			double through = (cycle - body.shrinkingSince) / (double) GROW_CYCLES;
			if (through >= 1)
			{
				return false;
			}
			step = Math.max(1, (int) Math.ceil(GROW_STEPS * (1 - through)));
		}
		else if (body.growingSince >= 0)
		{
			double through = (cycle - body.growingSince) / (double) GROW_CYCLES;
			step = Math.max(1, Math.min(GROW_STEPS, (int) Math.ceil(GROW_STEPS * through)));
			body.growingSince = through >= 1 ? -1 : body.growingSince;
		}
		if (step != body.step)
		{
			Model model = bodyModel(step);
			if (model != null)
			{
				body.object.setModel(model);
				body.step = step;
			}
		}
		river.at(Math.min(body.s, river.length), point);
		double offset = share(body.across, point);
		int x = (int) Math.round(point[0] - point[3] * offset);
		int y = (int) Math.round(point[1] + point[2] * offset);
		body.object.setLocation(new LocalPoint(x, y, shoal.worldView), shoal.plane);
		int bob = BODY_BOB * Perspective.SINE[(cycle + body.bobPhase) % BODY_BOB_CYCLES * 2048 / BODY_BOB_CYCLES] >> 16;
		body.object.setZ(waterHeight(x, y, shoal.plane) + BODY_SINK + bob);
		body.object.setOrientation((int) Math.round(body.facing * 2048 / (2 * Math.PI)) & 2047);
		if (!body.object.isActive())
		{
			body.object.setActive(true);
		}
		return true;
	}

	/**
	 * The body model at a grow step, lit as its object is.
	 */
	private Model bodyModel(int step)
	{
		if (bodyModels[step] == null)
		{
			ModelData data = client.loadModelData(BODY_MODEL);
			if (data == null)
			{
				return null;
			}
			int scale = 128 * step / GROW_STEPS;
			bodyModels[step] = data.shallowCopy().cloneVertices().scale(scale, scale, scale)
				.light(ModelData.DEFAULT_AMBIENT + 15, ModelData.DEFAULT_CONTRAST + 625, ModelData.DEFAULT_X,
					ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
		}
		return bodyModels[step];
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
			// New fish even on a full lake; trimLake() evens the count out later.
			double s = shoal.lake ? 0 : Math.max(0, circle.along - REFILL_BEHIND);
			double[] place = shoal.lake ? emptiestSpawn(shoal) : null;
			Swimmer swimmer = spawn(shoal, s, place, true, cycle, random, wanted.get(random.nextInt(wanted.size())), null,
				0);
			if (swimmer != null)
			{
				swimmer.bound = circle;
				circling.merge(circle, 1, Integer::sum);
			}
		}
	}

	/**
	 * Lets a few free fish of a lake over its count shrink away: each of the kind most over its share (from the share
	 * spinners), the one furthest from the player. Fish in or bound for a circle stay.
	 */
	private void trimLake(Shoal shoal, int cycle)
	{
		int all = 0;
		Map<Integer, Integer> free = new HashMap<>();
		for (Swimmer swimmer : shoal.fish)
		{
			if (swimmer.shrinkingSince >= 0)
			{
				continue;
			}
			all++;
			if (swimmer.circle == null && swimmer.bound == null)
			{
				free.merge(swimmer.item, 1, Integer::sum);
			}
		}
		int excess = Math.min(LAKE_TRIM_MOST, all - shoal.travelling);
		if (excess <= 0)
		{
			return;
		}
		boolean rainbows = has(shoal.kinds, RAINBOW);
		int weights = 0;
		int others = 0;
		for (int kind : shoal.kinds)
		{
			weights += weight(kind);
			others += kind == RAINBOW ? 0 : 1;
		}
		Player player = client.getLocalPlayer();
		LocalPoint me = player == null ? null : player.getLocalLocation();
		for (int n = 0; n < excess; n++)
		{
			int worst = -1;
			double most = Double.NEGATIVE_INFINITY;
			for (int kind : shoal.kinds)
			{
				int count = free.getOrDefault(kind, 0);
				if (count == 0)
				{
					continue;
				}
				double rest = 1 - (rainbows ? RAINBOW_SHARE : 0);
				double share = kind == RAINBOW ? RAINBOW_SHARE
					: rest * (weights > 0 ? weight(kind) / (double) weights : 1.0 / Math.max(1, others));
				double over = count - share * all;
				if (over > most)
				{
					most = over;
					worst = kind;
				}
			}
			Swimmer furthest = null;
			double furthestApart = -1;
			for (Swimmer swimmer : shoal.fish)
			{
				if (swimmer.item != worst || swimmer.circle != null || swimmer.bound != null || swimmer.shrinkingSince >= 0)
				{
					continue;
				}
				double apart = me == null ? 0 : Math.hypot(swimmer.x - me.getX(), swimmer.y - me.getY());
				if (apart > furthestApart)
				{
					furthestApart = apart;
					furthest = swimmer;
				}
			}
			if (furthest == null)
			{
				return;
			}
			ungroup(furthest);
			furthest.shrinkingSince = cycle;
			free.merge(worst, -1, Integer::sum);
			all--;
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
		// Lake fish cruise slower than river fish.
		double travel = shoal.lake ? TRAVEL_SPEED * LAKE_SPEED : TRAVEL_SPEED;
		double base = swimmer.circle != null ? travel + (CIRCLE_SPEED - travel) * near : travel;
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
			double behind = middleS + swimmer.slotAlong - swimmer.s;
			if (shoal.lake)
			{
				// Along the school's way to its goal.
				double apart = Double.isNaN(group.goalX) ? 0 : Math.hypot(group.goalX - middleX, group.goalY - middleY);
				behind = apart > 1 ? ((middleX - swimmer.x) * (group.goalX - middleX) + (middleY - swimmer.y)
					* (group.goalY - middleY)) / apart + swimmer.slotAlong : 0;
			}
			want *= 1 + CATCH_UP * Math.max(-1, Math.min(1, behind / CATCH_UP_RANGE));
		}
		if (swimmer.circle != null)
		{
			// Inner lanes are slower, and gaps are evened out, but only once the fish has reached its lane.
			Circle circle = swimmer.circle;
			int lanes = circle.lanes.length;
			double out = lanes > 1 ? swimmer.circleLane / (double) (lanes - 1) : 1;
			double lane = INNER_LANE_SPEED + (1 - INNER_LANE_SPEED) * out;
			if (shoal.deciding)
			{
				swimmer.evening = spacing(shoal, swimmer);
			}
			want *= (1 - in * (1 - lane)) * (1 + in * (swimmer.evening - 1));
		}
		// Paused lake fish hover.
		if (shoal.lake && swimmer.circle == null && swimmer.setOffAt >= 0 && cycle < swimmer.setOffAt)
		{
			want = travel * LAKE_HOVER * own;
		}
		// Circling fish change speed more gently once in their lane.
		boolean pausing = shoal.lake && swimmer.circle == null && swimmer.setOffAt >= 0 && cycle < swimmer.setOffAt;
		double ease = pausing ? LAKE_STOP_EASE : in >= 1 ? 0.02 : 0.05;
		swimmer.swimming += (want - swimmer.swimming) * Math.min(1, ease * ticks);
		double moved = swimmer.swimming * ticks;
		double toward = swimmer.aim;
		if (shoal.deciding || Double.isNaN(toward))
		{
			toward = aim(shoal, swimmer, group, middleX, middleY, middleS, moved * DECIDE_EVERY, cycle);
			swimmer.aim = toward;
		}
		if (schooled && swimmer.circle == null)
		{
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
		if (shoal.lake && !river.isWater(x, y))
		{
			// Never onto a lake's bank; turn on the spot instead.
			x = swimmer.x;
			y = swimmer.y;
		}
		swimmer.x = x;
		swimmer.y = y;
		if (shoal.lake)
		{
			// Lakes have no path; ordering by x still finds near fish.
			swimmer.s = x;
		}
		swimmer.wag = (swimmer.wag + 2 * Math.PI * moved / WAG_DISTANCE) % (2 * Math.PI);
		// Deep while swimming down the river, shallower near banks, up at the surface to circle.
		double shallow = swimmer.deep > 0 ? Math.min(1, river.clearanceAt(x, y) / SHALLOW_ROOM) : 0;
		swimmer.depthNow += ((swimmer.circle != null ? 0 : swimmer.deep * shallow) - swimmer.depthNow)
			* Math.min(1, DEPTH_EASE * ticks);
		// Caught fish shrink once in their lane.
		if (swimmer.caught && swimmer.shrinkingSince < 0 && swimmer.circle != null && off <= LANE_ARRIVED)
		{
			swimmer.shrinkingSince = cycle;
		}
		// Shrink near the window's downstream end, or when left behind upstream of it.
		if (!shoal.lake && swimmer.circle == null && swimmer.bound == null && swimmer.shrinkingSince < 0
			&& (swimmer.s >= shoal.windowTo - swimmer.swimming * GROW_CYCLES
			|| swimmer.s < shoal.windowFrom - WINDOW_SLACK))
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
	 * Where a fish aims, as a heading: round its circle, to its place in its school or along its way down the river or
	 * round the lake, steered round others when schooled. Worked out when its shoal decides; moved is how far it goes
	 * before the next time.
	 */
	private double aim(Shoal shoal, Swimmer swimmer, Group group, double middleX, double middleY, double middleS,
		double moved, int cycle)
	{
		River river = shoal.river;
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
		else if (shoal.lake)
		{
			lakeTarget(shoal, swimmer, group, middleX, middleY, cycle, point);
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
		return schooled && swimmer.circle == null ? flock(shoal, swimmer, group, toward, middleX, middleY) : toward;
	}

	/**
	 * Eases a push towards what it should be: at the given share per call while growing, slower while fading.
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
		// Eased per decision, every DECIDE_EVERY ticks.
		swimmer.repelX = ease(swimmer.repelX, repelX, REPEL_EASE * DECIDE_EVERY);
		swimmer.repelY = ease(swimmer.repelY, repelY, REPEL_EASE * DECIDE_EVERY);
		swimmer.groupRepelX = ease(swimmer.groupRepelX, groupRepelX, GROUP_REPEL_EASE * DECIDE_EVERY);
		swimmer.groupRepelY = ease(swimmer.groupRepelY, groupRepelY, GROUP_REPEL_EASE * DECIDE_EVERY);
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
		if (shoal.deciding || swimmer.surface == Integer.MIN_VALUE)
		{
			swimmer.surface = waterHeight(x, y, shoal.plane);
		}
		swimmer.fish.setZ(swimmer.surface + look.sink + bobbed
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
	 * Sets how many lake fish there are, 25-100%: 50% gives half the fish.
	 */
	void setLakeAmount(int percent)
	{
		lakeSpacingScale = 100.0 / percent;
	}

	/**
	 * TEMPORARY: reads the tuning spinners.
	 */
	static void tune(LivelyFishingSpotsConfig config)
	{
		drawBanks = config.debugRiverDrawBanks();
		drawPath = config.debugRiverDrawPath();
		drawRoom = config.debugRiverDrawRoom();
		drawSpawns = config.debugRiverDrawSpawns();
		drawRings = config.debugRiverDrawRings();
		drawDecisions = config.debugRiverDrawDecisions();
		drawKeepOut = config.debugRiverDrawKeepOut();
		drawSpotIds = config.debugRiverDrawSpotIds();
		drawFish = config.debugRiverDrawFish();
		drawSchools = config.debugRiverDrawSchools();
		drawBounds = config.debugRiverDrawBounds();
		drawPoints = config.debugRiverDrawPoints();
		drawBoxes = config.debugRiverDrawBoxes();
		drawRange = config.debugRiverDrawRange();
		WEIGHTS.put(ItemID.RAW_TROUT, config.debugRiverShareTrout());
		WEIGHTS.put(ItemID.RAW_SALMON, config.debugRiverShareSalmon());
		WEIGHTS.put(ItemID.RAW_PIKE, config.debugRiverSharePike());
		RAINBOW_SHARE = config.debugRiverShareRainbow() / 100.0;
		BODY_EVERY = config.debugRiverBodyMinutes() * 60 * 50.0;
		BOB_CYCLES = config.debugRiverBobCycles();
		BOB_REST_CYCLES = config.debugRiverBobRestCycles();
		DEEP_LEAST = config.debugRiverDeepLeast();
		DEEP_MOST = config.debugRiverDeepMost();
		CIRCLE_LANES = config.debugRiverCircleLanes();
		CIRCLE_LANE_SPACING = config.debugRiverCircleLaneSpacing();
		CIRCLE_SIZE = config.debugRiverCircleSize();
		CIRCLE_MOST = config.debugRiverCircleMost();
		INNER_LANE_SPEED = config.debugRiverInnerLaneSpeed() / 100.0;
		CIRCLE_CLEARANCE = config.debugRiverCircleClearance();
		SCATTER_RATE = 1.0 / (config.debugRiverScatterSeconds() * 50.0);
		LAKE_JOIN_CHANCE = config.debugRiverLakeJoinChance();
		FISH_RANGE = config.debugRiverFishRange();
		LAKE_SPEED = config.debugRiverLakeSpeed() / 100.0;
		CIRCLE_OFFSET = config.debugRiverCircleOffset();
		RING_MANUAL = config.debugRiverRingManual();
		RING_EAST = config.debugRiverRingEast();
		RING_NORTH = config.debugRiverRingNorth();
		TRAVEL_SPACING = config.debugRiverTravelSpacing();
		SPREAD = config.debugRiverSpread() / 100.0;
		WANDER = config.debugRiverWander() / 100.0;
	}

	/**
	 * Adds a route picked in game and logs it for the table.
	 */
	/**
	 * TEMPORARY: adds a spawn point to the lake being picked, starting a new lake when far from it, and logs it.
	 */
	void pickLake(WorldPoint spawn)
	{
		if (pickedLake != null && pickedLake[0].distanceTo2D(spawn) > LAKE_PICK_REACH)
		{
			pickedLake = null;
		}
		List<WorldPoint> points = new ArrayList<>(pickedLake == null ? List.of() : Arrays.asList(pickedLake));
		points.add(spawn);
		pickedLake = points.toArray(new WorldPoint[0]);
		StringBuilder line = new StringBuilder("new WorldPoint[]{");
		for (int k = 0; k < pickedLake.length; k++)
		{
			WorldPoint point = pickedLake[k];
			line.append(k > 0 ? ", " : "").append("new WorldPoint(").append(point.getX()).append(", ")
				.append(point.getY()).append(", ").append(point.getPlane()).append(")");
		}
		log.debug("Lake picked: {}", line.append("}"));
	}

	/**
	 * TEMPORARY: forgets the lake being picked. Returns whether there was one.
	 */
	boolean clearPickedLake()
	{
		boolean had = pickedLake != null;
		pickedLake = null;
		return had;
	}

	/**
	 * TEMPORARY: the points of the route being picked so far, drawn while picking.
	 */
	void setPicking(List<WorldPoint> points)
	{
		picking = points;
	}

	void pickRoute(List<WorldPoint> points)
	{
		picked.add(0, points.toArray(new WorldPoint[0]));
		StringBuilder line = new StringBuilder("new WorldPoint[]{");
		for (int k = 0; k < points.size(); k++)
		{
			WorldPoint point = points.get(k);
			line.append(k > 0 ? ", " : "").append("new WorldPoint(").append(point.getX()).append(", ")
				.append(point.getY()).append(", ").append(point.getPlane()).append(")");
		}
		log.debug("River route picked: {}", line.append("}"));
	}

	/**
	 * After a map load: keeps each river and lake, and its fish, moving them to the new scene coordinates; one whose
	 * map no longer fits in the loaded scene is mapped again for what's loaded, keeping the fish still on it.
	 */
	void reload(WorldView view)
	{
		long started = System.nanoTime();
		reloadTimed(view);
		time("Map load", started);
	}

	private void reloadTimed(WorldView view)
	{
		int cycle = client.getGameCycle();
		unmappable.clear();
		// Rivers and lakes being mapped start again, in the new scene's coordinates.
		for (Opening opening : openings)
		{
			opening.mapping = new Mapping(view, opening.mapping.route, opening.mapping.plane);
		}
		for (Iterator<Shoal> all = shoals.iterator(); all.hasNext(); )
		{
			Shoal shoal = all.next();
			River river = shoal.river;
			int dx = (shoal.baseX - view.getBaseX()) * 128;
			int dy = (shoal.baseY - view.getBaseY()) * 128;
			int across = river.width * CELL;
			int up = river.height * CELL;
			shift(shoal, dx, dy);
			shoal.baseX = view.getBaseX();
			shoal.baseY = view.getBaseY();
			// A map that no longer fits the loaded scene is mapped again for what's loaded now; one that fits is only
			// moved, and walking maps more of it as needed.
			if ((river.x0 < 0 || river.y0 < 0 || river.x0 + across > view.getSizeX() * 128
				|| river.y0 + up > view.getSizeY() * 128) && !remap(view, shoal))
			{
				shoal.fish.forEach(swimmer -> swimmer.fish.setActive(false));
				if (shoal.body != null)
				{
					shoal.body.object.setActive(false);
				}
				all.remove();
				continue;
			}
			for (Swimmer swimmer : shoal.fish)
			{
				swimmer.fish.setActive(false);
				swimmer.fish.setLocation(new LocalPoint((int) swimmer.x, (int) swimmer.y, shoal.worldView), shoal.plane);
				place(shoal, swimmer, cycle);
				swimmer.fish.setActive(true);
			}
			// Placed again on its next move.
			if (shoal.body != null)
			{
				shoal.body.object.setActive(false);
			}
		}
	}

	/**
	 * Maps a shoal again for the loaded scene, its fish carrying on where they are: those off the new map go, the
	 * rest pick up their place along the new path, and the circles are placed again. Returns false if it can't.
	 */
	private boolean remap(WorldView view, Shoal shoal)
	{
		long started = System.nanoTime();
		boolean mapped = remapTimed(view, shoal);
		time(label(shoal.route) + " remap", started);
		return mapped;
	}

	private boolean remapTimed(WorldView view, Shoal shoal)
	{
		Mapping mapping = new Mapping(view, shoal.route, shoal.plane);
		River river = mapping.all();
		List<double[]> spawns = mapping.spawns;
		if (river == null)
		{
			return false;
		}
		// Where the body is now, on the old path.
		double[] bodyAt = null;
		if (shoal.body != null && !shoal.lake)
		{
			shoal.river.at(shoal.body.s, point);
			bodyAt = new double[]{point[0], point[1]};
		}
		shoal.river = river;
		shoal.travelling = fishCount(shoal.route, river, shoal.lake);
		shoal.mappedAround = playerTile();
		if (shoal.lake)
		{
			shoal.spawns = spawns.toArray(new double[0][]);
		}
		if (bodyAt != null && river.isWater(bodyAt[0], bodyAt[1]))
		{
			shoal.body.s = river.nearest(bodyAt[0], bodyAt[1]);
		}
		else if (shoal.body != null)
		{
			shoal.body.object.setActive(false);
			shoal.body = null;
		}
		// Circles placed again on the new map, by their spots.
		Map<Circle, Circle> moved = new HashMap<>();
		List<NPC> spots = new ArrayList<>(shoal.circles.keySet());
		Map<NPC, Circle> old = new HashMap<>(shoal.circles);
		shoal.circles.clear();
		for (NPC spot : spots)
		{
			LocalPoint at = spot.getLocalLocation();
			if (at != null)
			{
				attach(shoal, spot, at);
				moved.put(old.get(spot), shoal.circles.get(spot));
			}
		}
		for (Iterator<Swimmer> it = shoal.fish.iterator(); it.hasNext(); )
		{
			Swimmer swimmer = it.next();
			if (!river.isWater(swimmer.x, swimmer.y))
			{
				ungroup(swimmer);
				swimmer.fish.setActive(false);
				it.remove();
				continue;
			}
			if (!shoal.lake)
			{
				swimmer.s = river.nearest(swimmer.x, swimmer.y);
			}
			swimmer.passing = null;
			swimmer.circle = swimmer.circle == null ? null : moved.get(swimmer.circle);
			swimmer.bound = swimmer.bound == null ? null : moved.get(swimmer.bound);
			swimmer.decided.clear();
			for (Circle circle : shoal.circles.values())
			{
				if (!shoal.lake && swimmer.s > circle.along - JOIN_BEFORE && swimmer.circle != circle)
				{
					swimmer.decided.add(circle);
				}
			}
		}
		// Newly loaded stretches get their fish now, not only as fish swim down to them.
		if (!shoal.lake)
		{
			shoal.spacing = TRAVEL_SPACING * spacingScale;
			updateWindow(shoal);
			// Queued places are along the old path.
			shoal.toFill.clear();
			fillGaps(shoal, ThreadLocalRandom.current());
		}
		return true;
	}

	/**
	 * Moves everything a shoal keeps in scene coordinates by some local units.
	 */
	private static void shift(Shoal shoal, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return;
		}
		River river = shoal.river;
		river.x0 += dx;
		river.y0 += dy;
		for (int k = 0; river.pathX != null && k < river.pathX.length; k++)
		{
			river.pathX[k] += dx;
			river.pathY[k] += dy;
		}
		// Worked out again when next drawn.
		river.banks = null;
		for (int k = 0; shoal.spawns != null && k < shoal.spawns.length; k++)
		{
			shoal.spawns[k][0] += dx;
			shoal.spawns[k][1] += dy;
		}
		for (Circle circle : shoal.circles.values())
		{
			circle.x += dx;
			circle.y += dy;
		}
		Set<Group> groups = new HashSet<>();
		for (Swimmer swimmer : shoal.fish)
		{
			swimmer.surface = Integer.MIN_VALUE;
			swimmer.x += dx;
			swimmer.y += dy;
			swimmer.targetX += dx;
			swimmer.targetY += dy;
			swimmer.goalX += dx;
			swimmer.goalY += dy;
			if (shoal.lake)
			{
				swimmer.s += dx;
			}
			if (swimmer.group != null && groups.add(swimmer.group))
			{
				swimmer.group.goalX += dx;
				swimmer.group.goalY += dy;
				swimmer.group.middleAt = -1;
			}
		}
	}

	/**
	 * TEMPORARY: each river and lake's name, its fish against the most it keeps, and its fish, for the debug counter.
	 */
	String[][] counts()
	{
		String[][] rows = new String[shoals.size()][];
		for (int k = 0; k < rows.length; k++)
		{
			Shoal shoal = shoals.get(k);
			WorldPoint first = shoal.route[0];
			int most = shoal.lake ? shoal.travelling : windowFish(shoal);
			rows[k] = new String[]{(shoal.lake ? "Lake " : "River ") + first.getX() + "," + first.getY(),
				shoal.fish.size() + " / " + most, String.valueOf(shoal.fish.size())};
		}
		return rows;
	}

	/**
	 * TEMPORARY: a river or lake's name in the debug panel.
	 */
	private String label(WorldPoint[] route)
	{
		return (isLake(route) ? "Lake " : "River ") + route[0].getX() + "," + route[0].getY();
	}

	/**
	 * TEMPORARY: notes how long something took, for the debug panel.
	 */
	private void time(String what, long started)
	{
		timeMs(what, (System.nanoTime() - started) / 100_000 / 10.0);
	}

	private void timeMs(String what, double ms)
	{
		timings.addFirst(new String[]{what, ms + " ms"});
		while (timings.size() > TIMINGS_SHOWN)
		{
			timings.removeLast();
		}
		if (ms > slowest)
		{
			slowest = ms;
			slowestWhat = what;
		}
	}

	/**
	 * TEMPORARY: the latest timings, newest first, then the slowest so far, for the debug panel.
	 */
	List<String[]> timings()
	{
		List<String[]> rows = new ArrayList<>(timings);
		if (slowestWhat != null)
		{
			rows.add(new String[]{"Slowest: " + slowestWhat, slowest + " ms"});
		}
		return rows;
	}

	/**
	 * Removes every fish.
	 */
	void clear()
	{
		unmappable.clear();
		openings.clear();
		for (Shoal shoal : shoals)
		{
			shoal.fish.forEach(swimmer -> swimmer.fish.setActive(false));
			if (shoal.body != null)
			{
				shoal.body.object.setActive(false);
			}
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

	// TEMPORARY: which parts of the debug drawing to show (toggles).
	private static boolean drawBanks = true;
	private static boolean drawPath = true;
	private static boolean drawRoom = true;
	private static boolean drawSpawns = true;
	private static boolean drawRings = true;
	private static boolean drawDecisions = true;
	private static boolean drawKeepOut = true;
	private static boolean drawSpotIds = true;
	private static boolean drawFish = true;
	private static boolean drawSchools = true;
	private static boolean drawBounds = true;
	private static boolean drawPoints = true;
	private static boolean drawBoxes = true;
	private static boolean drawRange = true;
	// Debug: length of a school's heading arrow, local units.
	private static final double SCHOOL_ARROW = 160;
	// Debug: room drawn round each fish in a school's outline, local units.
	private static final double SCHOOL_PAD = 24;

	/**
	 * Debug: a route's points as tile outlines, numbered in order, the first marked start and, when finished, the
	 * last marked end.
	 */
	private void routePoints(Graphics2D graphics, WorldView view, WorldPoint[] points, Color colour, boolean finished)
	{
		graphics.setColor(colour);
		for (int k = 0; k < points.length; k++)
		{
			LocalPoint at = LocalPoint.fromWorld(view, points[k]);
			Polygon tile = at == null ? null : Perspective.getCanvasTilePoly(client, at);
			if (tile == null)
			{
				continue;
			}
			graphics.draw(tile);
			String text = (k + 1) + (k == 0 ? " start" : finished && k == points.length - 1 ? " end" : "");
			Point label = Perspective.getCanvasTextLocation(client, graphics, at, text, 0);
			if (label != null)
			{
				graphics.drawString(text, label.getX(), label.getY());
			}
		}
	}

	/**
	 * Debug: a line on the water between two places.
	 */
	private void edge(Graphics2D graphics, Shoal shoal, double fromX, double fromY, double toX, double toY)
	{
		Point a = canvas(shoal, fromX, fromY);
		Point b = canvas(shoal, toX, toY);
		if (a != null && b != null)
		{
			graphics.drawLine(a.getX(), a.getY(), b.getX(), b.getY());
		}
	}

	/**
	 * Debug: a school's bounds, the outline round its fish with a little room.
	 */
	private void outline(Graphics2D graphics, Shoal shoal, Group group)
	{
		List<double[]> points = new ArrayList<>();
		for (Swimmer member : group.members)
		{
			for (int a = 0; a < 8; a++)
			{
				double angle = Math.PI * a / 4;
				points.add(new double[]{member.x + SCHOOL_PAD * Math.cos(angle), member.y + SCHOOL_PAD * Math.sin(angle)});
			}
		}
		// Convex hull, by the monotone chain.
		points.sort((p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(p[1], q[1]));
		double[][] hull = new double[2 * points.size()][];
		int k = 0;
		for (int pass = 0; pass < 2; pass++)
		{
			int start = k;
			for (int n = 0; n < points.size(); n++)
			{
				double[] p = points.get(pass == 0 ? n : points.size() - 1 - n);
				while (k >= start + 2 && (hull[k - 1][0] - hull[k - 2][0]) * (p[1] - hull[k - 2][1])
					- (hull[k - 1][1] - hull[k - 2][1]) * (p[0] - hull[k - 2][0]) <= 0)
				{
					k--;
				}
				hull[k++] = p;
			}
			k--;
		}
		Point last = canvas(shoal, hull[k - 1][0], hull[k - 1][1]);
		for (int n = 0; n < k; n++)
		{
			Point p = canvas(shoal, hull[n][0], hull[n][1]);
			if (p != null && last != null)
			{
				graphics.drawLine(last.getX(), last.getY(), p.getX(), p.getY());
			}
			last = p;
		}
	}

	/**
	 * Debug: an arrow on the water between two places, its head this long (local units).
	 */
	private void arrow(Graphics2D graphics, Shoal shoal, double fromX, double fromY, double toX, double toY, double head)
	{
		double length = Math.hypot(toX - fromX, toY - fromY);
		if (length < 1)
		{
			return;
		}
		double ux = (toX - fromX) / length;
		double uy = (toY - fromY) / length;
		double back = Math.min(head, length / 2);
		double side = back * 0.6;
		Point from = canvas(shoal, fromX, fromY);
		Point tip = canvas(shoal, toX, toY);
		Point left = canvas(shoal, toX - ux * back - uy * side, toY - uy * back + ux * side);
		Point right = canvas(shoal, toX - ux * back + uy * side, toY - uy * back - ux * side);
		if (from == null || tip == null || left == null || right == null)
		{
			return;
		}
		graphics.drawLine(from.getX(), from.getY(), tip.getX(), tip.getY());
		graphics.drawLine(tip.getX(), tip.getY(), left.getX(), left.getY());
		graphics.drawLine(tip.getX(), tip.getY(), right.getX(), right.getY());
	}

	/**
	 * Debug: banks, path, room, spawns, circles, fish steering and schools, each part shown by its toggle.
	 */
	void drawDebug(Graphics2D graphics)
	{
		// Each route's start, waypoints and end, numbered; the one being picked in magenta.
		WorldView view = client.getTopLevelWorldView();
		Player me = client.getLocalPlayer();
		LocalPoint meAt = me == null ? null : me.getLocalLocation();
		if (drawRange && view != null && meAt != null)
		{
			// The square rivers are mapped within round the player, as clip() cuts them: tile middles, so drawn half a
			// tile out.
			int reach = FISH_RANGE + MAP_MORE;
			int lowX = Math.max(ROUTE_MARGIN, meAt.getSceneX() - reach);
			int lowY = Math.max(ROUTE_MARGIN, meAt.getSceneY() - reach);
			int highX = Math.min(view.getSizeX() - 1 - ROUTE_MARGIN, meAt.getSceneX() + reach);
			int highY = Math.min(view.getSizeY() - 1 - ROUTE_MARGIN, meAt.getSceneY() + reach);
			int[][] corners = {{lowX * 128, lowY * 128}, {highX * 128 + 128, lowY * 128},
				{highX * 128 + 128, highY * 128 + 128}, {lowX * 128, highY * 128 + 128}};
			graphics.setColor(Color.CYAN);
			int plane = me.getWorldLocation().getPlane();
			for (int side = 0; side < 4; side++)
			{
				int[] from = corners[side];
				int[] to = corners[(side + 1) % 4];
				int steps = Math.max(1, (Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1])) / 128);
				Point before = null;
				for (int k = 0; k <= steps; k++)
				{
					Point p = Perspective.localToCanvas(client, new LocalPoint(from[0] + (to[0] - from[0]) * k / steps,
						from[1] + (to[1] - from[1]) * k / steps, view.getId()), plane);
					if (p != null && before != null)
					{
						graphics.drawLine(before.getX(), before.getY(), p.getX(), p.getY());
					}
					before = p;
				}
			}
		}
		if (drawPoints && view != null)
		{
			List<WorldPoint[]> all = new ArrayList<>(picked);
			all.addAll(ROUTES);
			for (WorldPoint[] route : all)
			{
				routePoints(graphics, view, route, Color.YELLOW, true);
			}
			routePoints(graphics, view, picking.toArray(new WorldPoint[0]), Color.MAGENTA, false);
		}
		for (Shoal shoal : shoals)
		{
			River river = shoal.river;
			graphics.setColor(new Color(255, 160, 0, 160));
			int[] banks = drawBanks ? banks(river) : new int[0];
			for (int k = 0; k + 1 < banks.length; k += 2)
			{
				Point p = canvas(shoal, banks[k], banks[k + 1]);
				if (p != null)
				{
					graphics.fillRect(p.getX() - 1, p.getY() - 1, 3, 3);
				}
			}
			if (shoal.lake && drawSpawns)
			{
				// Spawn points.
				graphics.setColor(Color.YELLOW);
				for (double[] spawn : shoal.spawns)
				{
					Point p = canvas(shoal, spawn[0], spawn[1]);
					if (p != null)
					{
						graphics.drawOval(p.getX() - 6, p.getY() - 6, 12, 12);
					}
				}
			}
			graphics.setColor(Color.CYAN);
			Point last = null;
			for (int k = 0; drawPath && river.pathX != null && k < river.pathX.length; k++)
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
			// The tiles mapped for it: the outline of its box.
			if (drawBoxes)
			{
				graphics.setColor(new Color(180, 120, 255));
				double right = river.x0 + river.width * CELL;
				double top = river.y0 + river.height * CELL;
				double[][] corners = {{river.x0, river.y0}, {right, river.y0}, {right, top}, {river.x0, top}};
				for (int side = 0; side < 4; side++)
				{
					double[] from = corners[side];
					double[] to = corners[(side + 1) % 4];
					int steps = (int) Math.max(1, Math.hypot(to[0] - from[0], to[1] - from[1]) / 128);
					Point before = null;
					for (int k = 0; k <= steps; k++)
					{
						Point p = canvas(shoal, from[0] + (to[0] - from[0]) * k / steps, from[1] + (to[1] - from[1]) * k / steps);
						if (p != null && before != null)
						{
							graphics.drawLine(before.getX(), before.getY(), p.getX(), p.getY());
						}
						before = p;
					}
				}
			}
			// The stretch with fish round the player.
			if (!shoal.lake && drawPath)
			{
				acrossLine(graphics, shoal, shoal.windowFrom, Color.ORANGE);
				acrossLine(graphics, shoal, shoal.windowTo, Color.ORANGE);
			}
			// Room edges.
			graphics.setColor(new Color(0, 160, 160, 140));
			for (int side = -1; drawRoom && side <= 1; side += 2)
			{
				Point edge = null;
				for (int k = 0; river.pathX != null && k < river.pathX.length; k++)
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
				if (!shoal.lake && drawDecisions)
				{
					acrossLine(graphics, shoal, circle.along - JOIN_BEFORE, Color.MAGENTA);
					acrossLine(graphics, shoal, circle.along, Color.GREEN);
				}
				graphics.setColor(Color.GREEN);
				for (int lane = 0; drawRings && lane < circle.lanes.length; lane++)
				{
					double out = circle.lanes[lane];
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
				for (int a = 0; drawKeepOut && a <= 32; a++)
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
				Point middle = drawRings ? canvas(shoal, circle.x, circle.y) : null;
				if (middle != null)
				{
					graphics.drawLine(middle.getX() - 5, middle.getY(), middle.getX() + 5, middle.getY());
					graphics.drawLine(middle.getX(), middle.getY() - 5, middle.getX(), middle.getY() + 5);
				}
				LocalPoint at = circle.npc.getLocalLocation();
				Point label = at == null || !drawSpotIds ? null : Perspective.localToCanvas(client, at, shoal.plane, 150);
				if (label != null)
				{
					String text = String.valueOf(circle.npc.getId());
					graphics.setColor(Color.WHITE);
					graphics.drawString(text, label.getX() - graphics.getFontMetrics().stringWidth(text) / 2, label.getY());
				}
			}
			// Steering arrows: white swimming, green circling, yellow growing, red shrinking.
			for (int n = 0; drawFish && n < shoal.fish.size(); n++)
			{
				Swimmer swimmer = shoal.fish.get(n);
				graphics.setColor(swimmer.shrinkingSince >= 0 ? Color.RED : swimmer.growingSince >= 0 ? Color.YELLOW
					: swimmer.circle != null ? Color.GREEN : Color.WHITE);
				arrow(graphics, shoal, swimmer.x, swimmer.y, swimmer.targetX, swimmer.targetY, 16);
			}
			// School headings, blue: from each school's middle along its fish's average heading.
			graphics.setColor(new Color(60, 140, 255));
			Set<Group> schools = new HashSet<>();
			for (int n = 0; (drawSchools || drawBounds) && n < shoal.fish.size(); n++)
			{
				Group group = shoal.fish.get(n).group;
				if (group == null || group.members.size() < 2 || !schools.add(group))
				{
					continue;
				}
				double x = 0;
				double y = 0;
				double hx = 0;
				double hy = 0;
				for (Swimmer member : group.members)
				{
					x += member.x / group.members.size();
					y += member.y / group.members.size();
					hx += Math.cos(member.facing);
					hy += Math.sin(member.facing);
				}
				double length = Math.hypot(hx, hy);
				if (length < 0.01)
				{
					continue;
				}
				if (drawBounds)
				{
					outline(graphics, shoal, group);
				}
				if (drawSchools)
				{
					arrow(graphics, shoal, x, y, x + hx / length * SCHOOL_ARROW, y + hy / length * SCHOOL_ARROW, 32);
					Point from = canvas(shoal, x, y);
					if (from != null)
					{
						graphics.fillOval(from.getX() - 3, from.getY() - 3, 7, 7);
					}
				}
			}
		}
	}
}
