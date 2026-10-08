package com.livelyfishingspots;

import com.livelyfishingspots.FishModels.Look;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
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
import net.runelite.client.RuneLite;

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
	// TEMPORARY: the folder in .runelite bakes are saved to, worked on and reloaded from.
	static final String BAKED_FOLDER = "lively-fishing-spots/baked";
	// Rivers and lakes, from their bake files: each river's route (upstream start, waypoints, downstream end; spots
	// near none get no fish) and each lake's spawn points; and each one's name, its file's, by its first point.
	private final List<WorldPoint[]> routes = new ArrayList<>();
	private final List<WorldPoint[]> lakes = new ArrayList<>();
	private static final Map<WorldPoint, String> NAMES = new HashMap<>();
	// A lake is the connected water within LAKE_RADIUS tiles of its spawn points, mapped LAKE_MARGIN tiles past
	// them; a spot uses it within LAKE_REACH tiles of one, and a pick adds to it within LAKE_PICK_REACH of the first.
	static final int LAKE_RADIUS = 6;
	private static final int LAKE_MARGIN = 7;
	private static final int LAKE_REACH = 36;
	static final int LAKE_PICK_REACH = 48;
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
	// Tiles round a route's line for picking: a point added, a lake or fork joining it.
	static final int BOX_MARGIN = 8;
	// Tiles of water round a route: looked for round its points when mapped (the box then shrunk to the baked water
	// found), and baked round its line; wide enough for its widest water, round islands too.
	static final int WATER_REACH = 16;
	// Fish range, tiles (tuning spinner): rivers keep fish only within this far along the path either side of the
	// player's nearest point; a river or lake starts within this plus ACTIVE_MORE tiles of the player, and goes LINGER
	// client ticks after they're more than this plus LEAVE_MORE away.
	static int FISH_RANGE = 28;
	// Tiles past the fish range rivers keep fish (tuning spinner): those swim on unseen and undrawn, so fish come into
	// sight already swimming, and spawn, fill in and shrink away out of sight.
	static int LOAD_MORE = 16;
	private static final int ACTIVE_MORE = 4;
	private static final int LEAVE_MORE = 16;
	private static final int LINGER = 500;
	// The window is worked out every WINDOW_EVERY client ticks (a game tick): fish grow in at its upstream edge and
	// shrink at its downstream one, those left more than WINDOW_SLACK upstream of it shrink away, and empty stretches
	// in it are filled.
	private static final int WINDOW_EVERY = 30;
	// Fish decide (where to aim, steering round each other, which circles to join, the water's height) every this many
	// client ticks, all a shoal's on the same tick; they move every tick.
	private static final int DECIDE_EVERY = 2;
	// A new river or lake fills one school (or solo fish) a client tick, nearest the player first, not all in one frame.
	// Mapping steps: box, baked water, bank distances, baked path (lakes: roomy water and spawn points); and for the
	// debug panel, their names, then a new one's last: filling it with fish.
	private static final int MAP_STEPS = 4;
	private static final String[] STEP_NAMES = {"box", "water", "banks", "path", "fish"};
	private static final double WINDOW_SLACK = 256;
	// Rivers are mapped only within the fish range plus MAP_MORE tiles of the player, and mapped again round them,
	// keeping the fish, once the window comes within REMAP_EDGE tiles of a cut end and they've moved REMAP_MOVE tiles.
	static final int MAP_MORE = 4;
	private static final int REMAP_EDGE = 2;
	private static final int REMAP_MOVE = 3;
	// Spacings between fish along a river that count as an empty stretch to fill: more than a school and its room.
	private static final double EMPTY_GAP = 8;

	// Water grid cell size, local units.
	static final int CELL = 32;
	// Grid cells along a tile.
	static final int PER_TILE = 128 / CELL;
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
	// Fish divers: how deep fish not swimming deep go (local units), and how fast, share a client tick (tuning spinners).
	private static int DIVE_DEPTH = 25;
	private static double DIVE_EASE = 0.01;
	private static final double DEPTH_EASE = 0.03;
	// Fish surfacers: how fast a deep fish on one comes up, share a client tick.
	private static final double SURFACE_EASE = 0.12;
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
	// Making rings round: step and most steps (local units), and how far from where it started a ring may move.
	private static final double ROUND_STEP = 8;
	private static final int ROUND_STEPS = 48;
	private static final double ROUND_MOST = 384;
	// Cos and sin of the 16 directions looked in for open water.
	private static final double[][] LOOK_WAYS = ways(16);
	// Angles round a circle its lanes' shapes are kept at, and their cos and sin.
	private static final int LANE_ANGLES = 64;
	private static final double[][] LANE_WAYS = ways(LANE_ANGLES);
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
	// spinner), at a share of fish speed, sunk and bobbing (local units, client ticks), turning slowly (game angle
	// units, 2048 a turn, per client tick at most). Fish and bodies pass through each other.
	// The model of object SAILING_CHARTING_GENERIC_CORPSE_LUMBRIDGE_BASIN; there are no gamevals for models.
	private static final int BODY_MODEL = 57609;
	private static double BODY_EVERY = 180 * 60 * 50;
	// TEMPORARY: testing, a body 10 s (in client ticks) after the last one goes.
	private static boolean BODY_TEST;
	private static final int BODY_TEST_GAP = 10 * 50;
	private static final double BODY_SPEED = 0.45;
	private static final int BODY_SINK = 22;
	private static final int BODY_BOB = 4;
	private static final int BODY_BOB_CYCLES = 200;
	private static final double BODY_TURN = 0.8;
	// Room (both sides, local units) at or under which a body lines up feet first and goes faster, and at or over
	// which it spins freely; in between, a mix. Its extra speed where narrow, share of its speed; how fast it lines
	// up, share a client tick; and its model's turn to lie along the river (game angle units, tuning spinner).
	private static final double BODY_NARROW = 128;
	private static final double BODY_WIDE = 640;
	private static final double BODY_NARROW_SPEED = 0.4;
	private static final double BODY_ALIGN = 0.01;
	// How narrow (0 to 1, as above) a river must be before a body lines up at all.
	private static final double BODY_LINE_UP = 0.5;
	private static int BODY_FEET = 145 * 2048 / 360;
	private static final double BODY_WANDER = 0.3;
	// Share of the way to its place on the path a body moves each client tick, smoothing the path's corners.
	private static final double BODY_EASE = 0.05;

	// Wag, bob and tip, as at sea.
	private static final int WAG = 20;
	private static final double WAG_DISTANCE = 72;
	private static final double REFERENCE_SPEED = 3.2;
	private static int BOB_CYCLES = 60;
	private static int BOB_REST_CYCLES = 30;
	private static final double DIP_PITCH = 20;
	private static final double BOB_PITCH = 8;
	// Waterfalls: fish point down (or up) the water's slope along their heading, read this far ahead and behind (local
	// units); slopes under SLOPE_MIN degrees are left flat (banks' heights), and at most SLOPE_MOST; eased each tick.
	private static final int SLOPE_LOOK = 32;
	private static final double SLOPE_MIN = 8;
	private static final double SLOPE_MOST = 50;
	private static final double SLOPE_EASE = 0.2;

	/**
	 * A river or lake's water, saved from the scene once, in world cells; and a river's path, in world local units.
	 */
	private static final class Baked
	{
		// Its water as runs along each row of cells from y0: start, end (exclusive), start, end, ...
		private final int y0;
		private final int[][] rows;
		private final double[] pathX;
		private final double[] pathY;
		// Its river's route, or lake's spawn points, and which it is.
		private WorldPoint[] route;
		private boolean lake;
		// Its share of the fish its size would give, percent ("fish N" line; 100 if none).
		private int fishShare = 100;
		// Its marks, each kind's (MARK_WORDS order) tiles or halves of them (WHOLE, WEST, EAST, SOUTH, NORTH): fish
		// blockers count as land, allowers as water; on surfacers deep fish (117 HD) come up to their usual depth; on
		// divers other fish go down to the deepest, under logs across the water.
		private final List<Map<WorldPoint, String>> marks;
		// Side channels: each one's picked tiles (world x, y, x, y, ...; first and last on the river), and its laid
		// path's points (world local x, y, x, y, ...), empty until laid.
		private final List<int[]> branchStops = new ArrayList<>();
		private final List<int[]> branchPaths = new ArrayList<>();
		// Each side channel's share of the fish reaching it, percent, or -1 to go by its width.
		private final List<Integer> branchShares = new ArrayList<>();

		private Baked(int y0, int[][] rows, double[] pathX, double[] pathY, List<Map<WorldPoint, String>> marks)
		{
			this.y0 = y0;
			this.rows = rows;
			this.pathX = pathX;
			this.pathY = pathY;
			this.marks = marks;
		}

		/**
		 * Its marks of a kind (MARK_WORDS order).
		 */
		private Map<WorldPoint, String> marks(int kind)
		{
			return marks.get(kind);
		}
	}

	/**
	 * A river or lake's water grid, and a river's path down its middle.
	 */
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
		// Cells where deep fish come up (fish surfacers), or go down (fish divers), or null if none.
		private boolean[] surface;
		private boolean[] dive;
		// Path points, distance along, and room left and right (facing downstream).
		private double[] pathX;
		private double[] pathY;
		private double[] along;
		private double[] left;
		private double[] right;
		private double length;
		// Bank cells' middles, and the room edge's (BANK_GAP in), x then y, for debug drawing; worked out when first
		// drawn.
		private int[] banks;
		private double[] roomEdge;
		// Whether its route's start and end were cut, to the loaded scene or the player's reach, so more may be mapped.
		private boolean cutStart;
		private boolean cutEnd;
		// Lakes only (no path): water cells, those roomy enough to head for, and running totals of water cells per
		// tile (a summed-area table) for counting the water round a place.
		private int waterCells;
		private int[] open;
		private int[] tileWater;

		// Side channels mapped with it.
		private final List<Branch> branches = new ArrayList<>();

		// A side channel's path over another river's grid.
		private River(River grid)
		{
			x0 = grid.x0;
			y0 = grid.y0;
			width = grid.width;
			height = grid.height;
			clearance = grid.clearance;
		}

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
		 * Whether a place is on one of some marked cells (surface or dive).
		 */
		private boolean marked(boolean[] cells, double x, double y)
		{
			int i = (int) Math.floor((x - x0) / CELL);
			int j = (int) Math.floor((y - y0) / CELL);
			return cells != null && i >= 0 && j >= 0 && i < width && j < height && cells[j * width + i];
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
			int tilesX = width / PER_TILE;
			int tilesY = height / PER_TILE;
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
			// The segment's length, from the distances along.
			double d = Math.max(1e-6, along[k + 1] - along[k]);
			double t = (s - along[k]) / d;
			double dx = pathX[k + 1] - pathX[k];
			double dy = pathY[k + 1] - pathY[k];
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
	 * A side channel round an island: its own path, leaving the river's at fromS and joining it again at toS, and the
	 * share of fish reaching the fork that take it.
	 */
	private static final class Branch
	{
		private final River line;
		private final double fromS;
		private final double toS;
		private final double share;

		private Branch(River line, double fromS, double toS, double share)
		{
			this.line = line;
			this.fromS = fromS;
			this.toS = toS;
			this.share = share;
		}

		// A place along the branch as the matching place along the river, and back.
		private double toMain(double along)
		{
			return fromS + along / Math.max(1, line.length) * (toS - fromS);
		}

		private double toBranch(double s)
		{
			return (s - fromS) / Math.max(1, toS - fromS) * line.length;
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
		// Rivers: where the player was when it was last mapped; and a map being made round them as they walk, a step a
		// client tick, swapped in when done (null if none).
		private WorldPoint mappedAround;
		private Mapping remapping;
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
		// The bodies drifting down (one at most, more while testing), and when the next may come.
		private final List<Body> bodies = new ArrayList<>();
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
		// The last fork it came to, and whether it took the side channel there.
		private Branch fork;
		private boolean forkTaken;
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
		// Each lane's radius at LANE_ANGLES angles round, pulled in where it would come within BANK_GAP of a bank.
		private final double[][] pulled;
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
			pulled = new double[CIRCLE_LANES][LANE_ANGLES];
			for (int lane = 0; lane < CIRCLE_LANES; lane++)
			{
				lanes[lane] = Math.max(8, radius - (CIRCLE_LANES - 1 - lane) * CIRCLE_LANE_SPACING);
				for (int a = 0; a < LANE_ANGLES; a++)
				{
					double cos = LANE_WAYS[a][0];
					double sin = LANE_WAYS[a][1];
					double out = lanes[lane];
					while (out > 0 && river.clearanceAt(x + out * cos, y + out * sin) < BANK_GAP)
					{
						out -= 8;
					}
					pulled[lane][a] = Math.max(0, out);
				}
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
		// Distance along the path, place across it (a share of the room), heading (game angle units) and its turn per
		// client tick.
		private double s;
		private final double across;
		private double facing;
		private final double turn;
		private final int bobPhase;
		// Where it is, easing towards its place on the path; NaN until placed.
		private double x = Double.NaN;
		private double y;
		// How narrow the river is where it is: 0 wide (spins), 1 narrow (lines up).
		private double narrow;
		// How far down it is on fish divers, local units.
		private double depth;
		private int growingSince;
		private int shrinkingSince = -1;
		private int step = 1;

		private Body(RuneLiteObject object, double s, int cycle, ThreadLocalRandom random)
		{
			this.object = object;
			this.s = s;
			across = random.nextDouble(-BODY_WANDER, BODY_WANDER);
			facing = random.nextDouble(2048);
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
		// Position, heading (radians), distance along the path; and the heading's cos and sin, kept with it.
		private double x;
		private double y;
		private double facing;
		private double s;
		private double headX = 1;
		private double headY;
		// The side channel it's swimming down, or null, and how far along it; and the last fork it came to.
		private Branch branch;
		private double branchS;
		private Branch fork;
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
		// On a fish surfacer: up quickly, and no new bobs or dips.
		private boolean surfacing;
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
		// Nose up along the water's slope, degrees: as last read, and eased towards it.
		private double slopeWant;
		private double slope;
		private int dippingSince = -1;
		// Caught: shrinks away once in its lane.
		private boolean caught;
		// Whether it's drawn, and how far it's grown into sight (0 to 1): fish grow in coming within the fish range
		// along the river and shrink going out of it, then swim on unseen; apart from growing in and shrinking away.
		private boolean shown;
		private double inSight;
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
	// TEMPORARY: the route being picked, for drawing.
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
	// TEMPORARY: the last river or lake started, each step's time (ms), and the warm-up's at plugin start.
	private String lastLoad;
	private double[] lastLoadSteps;
	private double warmUpMs;
	// Kept between gap checks, for the fish's places along the river.
	private double[] gapsAlong = new double[0];
	// Kept between game ticks, for every river and lake to check.
	private final List<WorldPoint[]> everyRoute = new ArrayList<>();
	// Rivers and lakes being mapped, a step a client tick; and spots waiting on one now open, given rings a tick apart.
	private final List<Opening> openings = new ArrayList<>();
	private final Deque<NPC> toAttach = new ArrayDeque<>();
	// Where each spot tile's ring goes, from the spot (east, north, local units), worked out the first time.
	private final Map<WorldPoint, double[]> ringPlaces = new HashMap<>();
	// Rivers and lakes that couldn't be mapped since the last map load.
	private final Set<WorldPoint[]> unmappable = new HashSet<>();
	// Rivers and lakes (by first point) logged as out of sight, so it's logged once until they're next mapped.
	private final Set<WorldPoint> saidOutOfSight = new HashSet<>();
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
	// Scratch for River.at.
	private final double[] point = new double[6];

	RiverSpotFish(Client client, FishModels models)
	{
		this.client = client;
		this.models = models;
		// The plugin's own bakes (listed in baked/index.txt), then TEMPORARY those in .runelite, which take over any of
		// the same name.
		Map<String, String> texts = new LinkedHashMap<>();
		for (String name : readLines(RiverSpotFish.class.getResourceAsStream("baked/index.txt")))
		{
			if (!name.isBlank())
			{
				texts.put(name.trim(), String.join("\n",
					readLines(RiverSpotFish.class.getResourceAsStream("baked/" + name.trim() + ".txt"))));
			}
		}
		File[] files = new File(RuneLite.RUNELITE_DIR, BAKED_FOLDER).listFiles((folder, name) -> name.endsWith(".txt"));
		for (File file : files == null ? new File[0] : files)
		{
			try
			{
				texts.put(file.getName().replace(".txt", ""), new String(Files.readAllBytes(file.toPath()),
					StandardCharsets.UTF_8));
			}
			catch (IOException e)
			{
				log.warn("Couldn't read {}", file, e);
			}
		}
		texts.forEach(this::addBaked);
		long started = System.nanoTime();
		warmUp();
		warmUpMs = (System.nanoTime() - started) / 1e6;
		log.debug("River mapping warmed up in {} ms", Math.round(warmUpMs * 10) / 10.0);
	}

	/**
	 * Maps stretches of each baked river a few times, keeping nothing, so Java has sped the mapping code up before
	 * the first real map; else that one takes several times as long. Runs while the plugin starts, before any game
	 * events reach it, so it can't clash with real mapping.
	 */
	private void warmUp()
	{
		int reach = loadRange() + MAP_MORE;
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
				River river = new River((tileX - reach) * 128, (tileY - reach) * 128, (2 * reach + 1) * PER_TILE,
					(2 * reach + 1) * PER_TILE);
				bakedWet(saved, 0, 0, river);
				clearances(river);
				int[] ends = {(int) saved.pathX[from], (int) saved.pathY[from], (int) saved.pathX[to], (int) saved.pathY[to]};
				if (bakedPath(river, saved, 0, 0, ends))
				{
					bakedBranches(river, saved, 0, 0);
					river.at(river.nearest(river.pathX[0], river.pathY[0]), point);
					double[] away = awayFromBank(river, point[0], point[1]);
					roundRing(river, point[0] + away[0] * CIRCLE_OFFSET, point[1] + away[1] * CIRCLE_OFFSET);
				}
			}
		}
	}

	/**
	 * The lines of a text resource, none if it isn't there.
	 */
	private static List<String> readLines(InputStream in)
	{
		if (in == null)
		{
			return List.of();
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			List<String> lines = new ArrayList<>();
			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				lines.add(line);
			}
			return lines;
		}
		catch (IOException e)
		{
			log.warn("Couldn't read a bake", e);
			return List.of();
		}
	}

	/**
	 * Takes each river and lake from a bake file's text (each starts at its "route" or "lake" line), under the file's
	 * name. Returns their first points.
	 */
	private List<WorldPoint> addBaked(String name, String text)
	{
		List<WorldPoint> firsts = new ArrayList<>();
		StringBuilder part = new StringBuilder();
		for (String line : (text + "\nroute").split("\n"))
		{
			if ((line.startsWith("route") || line.startsWith("lake")) && part.length() > 0)
			{
				WorldPoint first = addOneBaked(name, part.toString());
				if (first != null)
				{
					firsts.add(first);
				}
				part.setLength(0);
			}
			part.append(line).append('\n');
		}
		return firsts;
	}

	/**
	 * Takes one river or lake from its text: its route or spawn points, water, path and the rest, under its file's
	 * name; replacing the one of the same first point. Returns its first point, or null if it has no route.
	 */
	private WorldPoint addOneBaked(String name, String text)
	{
		Baked saved = readBaked(name, new BufferedReader(new StringReader(text)));
		if (saved == null || saved.route == null)
		{
			log.debug("Bake {} has no route or lake line", name);
			return null;
		}
		WorldPoint first = saved.route[0];
		List<WorldPoint[]> list = saved.lake ? lakes : routes;
		WorldPoint[] old = null;
		for (WorldPoint[] route : routesAndLakes())
		{
			old = route[0].equals(first) ? route : old;
		}
		if (old != null && Arrays.equals(old, saved.route) && list.contains(old))
		{
			// The same route: keep it, as the rivers in sight go by it.
			saved.route = old;
		}
		else
		{
			routes.remove(old);
			lakes.remove(old);
			list.add(saved.route);
		}
		baked.put(first, saved);
		NAMES.put(first, name);
		return first;
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
	 * TEMPORARY: a bake's first line: "route PLANE X Y X Y ..." for a river, its route's tiles, or "lake ..." for a lake,
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

	// Each kind of mark's word in a baked file: fish blockers, allowers, surfacers and divers. Kinds 0 and 1 replace
	// each other on a tile, as do 2 and 3.
	static final String[] MARK_WORDS = {"block", "allow", "surface", "dive"};
	// Longest line in a baked file, characters, so a river takes few lines.
	private static final int BAKED_WIDTH = 150;

	/**
	 * A baked river or lake as text: its head line (see headLine), then "fish N" if it has N% of the fish its size would
	 * give, then "water X Y" gives the first row's world cell (y) and the cell the rows count from
	 * (x); the lines after hold the rows, going north, split by commas: each row's water as gap, length, gap,
	 * length..., each gap from the end of the run before (the first from X); "N*" in front repeats the row N times, and
	 * "-" is a row with none. "path X Y" gives the first path point (world local units), and each "step" line the steps
	 * on to the next points. Each "block X Y PLANE HALF" line is a fish blocker: a world tile, or half of it, counted
	 * as land; each "allow" line likewise a fish allower, counted as water; each "surface" line a fish surfacer, where
	 * deep fish come up; each "dive" line a fish diver, where they go down. Each "branch X Y X Y ..." line is a side
	 * channel's picked tiles, first and last on the river, followed by its laid path as "bpath" and "bstep" lines.
	 */
	static String bakedText(String head, int fishShare, int x0, int y0, List<int[]> rows, List<int[]> path,
		List<Map<WorldPoint, String>> marks, List<int[]> branchStops, List<int[]> branchPaths, List<Integer> branchShares)
	{
		StringBuilder text = new StringBuilder(head).append('\n');
		if (fishShare != 100)
		{
			text.append("fish ").append(fishShare).append('\n');
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

	private static Baked readBaked(String name, BufferedReader reader)
	{
		List<int[]> runs = new ArrayList<>();
		double[] path = new double[0];
		int pathCount = 0;
		List<Map<WorldPoint, String>> marks = new ArrayList<>();
		for (String word : MARK_WORDS)
		{
			marks.add(new HashMap<>());
		}
		List<int[]> branchStops = new ArrayList<>();
		List<int[]> branchPaths = new ArrayList<>();
		List<Integer> branchShares = new ArrayList<>();
		WorldPoint[] route = null;
		boolean lake = false;
		int fishShare = 100;
		try
		{
			int x0 = 0;
			int y = 0;
			int pathX = 0;
			int pathY = 0;
			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				String[] parts = line.trim().split(" ");
				if (parts[0].isEmpty())
				{
					continue;
				}
				if (parts[0].equals("water"))
				{
					x0 = Integer.parseInt(parts[1]);
					y = Integer.parseInt(parts[2]);
				}
				else if (parts[0].equals("fish"))
				{
					fishShare = Integer.parseInt(parts[1]);
				}
				else if (parts[0].equals("route") || parts[0].equals("lake"))
				{
					lake = parts[0].equals("lake");
					int plane = Integer.parseInt(parts[1]);
					route = new WorldPoint[(parts.length - 2) / 2];
					for (int k = 0; k < route.length; k++)
					{
						route[k] = new WorldPoint(Integer.parseInt(parts[2 + 2 * k]), Integer.parseInt(parts[3 + 2 * k]), plane);
					}
				}
				else if (parts[0].equals("branch"))
				{
					int[] stops = new int[parts.length - 1];
					for (int k = 1; k < parts.length; k++)
					{
						stops[k - 1] = Integer.parseInt(parts[k]);
					}
					branchStops.add(stops);
					branchPaths.add(new int[0]);
					branchShares.add(-1);
				}
				else if (parts[0].equals("bshare") && !branchShares.isEmpty())
				{
					branchShares.set(branchShares.size() - 1, Integer.parseInt(parts[1]));
				}
				else if ((parts[0].equals("bpath") || parts[0].equals("bstep")) && !branchPaths.isEmpty())
				{
					// Points on from the branch's last, or from nothing for its first.
					int[] points = branchPaths.get(branchPaths.size() - 1);
					int at = points.length;
					points = Arrays.copyOf(points, at + parts.length - 1);
					for (int k = 1; k + 1 < parts.length; k += 2)
					{
						points[at + k - 1] = (at + k - 1 >= 2 ? points[at + k - 3] : 0) + Integer.parseInt(parts[k]);
						points[at + k] = (at + k - 1 >= 2 ? points[at + k - 2] : 0) + Integer.parseInt(parts[k + 1]);
					}
					branchPaths.set(branchPaths.size() - 1, points);
				}
				else if (Arrays.asList(MARK_WORDS).contains(parts[0]))
				{
					marks.get(Arrays.asList(MARK_WORDS).indexOf(parts[0])).put(new WorldPoint(Integer.parseInt(parts[1]),
						Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), parts[4]);
				}
				else if (parts[0].equals("path") || parts[0].equals("step"))
				{
					boolean start = parts[0].equals("path");
					path = Arrays.copyOf(path, pathCount + parts.length - 1);
					for (int k = 1; k + 1 < parts.length; k += 2)
					{
						pathX = (start ? 0 : pathX) + Integer.parseInt(parts[k]);
						pathY = (start ? 0 : pathY) + Integer.parseInt(parts[k + 1]);
						path[pathCount++] = pathX;
						path[pathCount++] = pathY;
					}
				}
				else
				{
					// Rows of water, each maybe repeated.
					for (String piece : line.split(","))
					{
						String[] row = piece.trim().split(" ");
						int skip = 0;
						int repeats = 1;
						if (row[0].endsWith("*"))
						{
							repeats = Integer.parseInt(row[0].substring(0, row[0].length() - 1));
							skip = 1;
						}
						for (int r = 0; r < repeats; r++, y++)
						{
							int x = x0;
							for (int k = skip; k + 1 < row.length; k += 2)
							{
								int from = x + Integer.parseInt(row[k]);
								x = from + Integer.parseInt(row[k + 1]);
								runs.add(new int[]{y, from, x});
							}
						}
					}
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't read the bake {}", name, e);
			return null;
		}
		if (runs.isEmpty())
		{
			// Not baked yet: only its route.
			runs.add(new int[]{0, 0, 0});
		}
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int[] run : runs)
		{
			minY = Math.min(minY, run[0]);
			maxY = Math.max(maxY, run[0]);
		}
		int[] perRow = new int[maxY - minY + 1];
		for (int[] run : runs)
		{
			perRow[run[0] - minY]++;
		}
		int[][] rows = new int[perRow.length][];
		for (int j = 0; j < rows.length; j++)
		{
			rows[j] = new int[perRow[j] * 2];
			perRow[j] = 0;
		}
		for (int[] run : runs)
		{
			int[] row = rows[run[0] - minY];
			row[perRow[run[0] - minY]++ * 2] = run[1];
			row[perRow[run[0] - minY] * 2 - 1] = run[2];
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
		Baked saved = new Baked(minY, rows, pathX, pathY, marks);
		saved.route = route;
		saved.lake = lake;
		saved.fishShare = fishShare;
		saved.branchStops.addAll(branchStops);
		saved.branchPaths.addAll(branchPaths);
		saved.branchShares.addAll(branchShares);
		return saved;
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
	 * Starts the river or lake near the player: rivers within their load range, and lakes within FISH_RANGE, plus
	 * ACTIVE_MORE tiles open, and those beyond it plus LEAVE_MORE start going. Once a game tick; a route that can't be mapped isn't tried again until the next map
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
		all.addAll(routes);
		all.addAll(lakes);
		for (WorldPoint[] route : all)
		{
			double apart = reachTo(route, me);
			int range = isLake(route) ? FISH_RANGE : loadRange();
			Shoal shoal = shoalFor(route);
			if (shoal == null)
			{
				if (apart <= range + ACTIVE_MORE && !unmappable.contains(route))
				{
					open(view, route, me.getPlane(), null);
				}
			}
			else if (apart > range + LEAVE_MORE)
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
		return isLake(route) ? Math.max(0, toSpawns(route, me.getX(), me.getY()) - LAKE_RADIUS)
			: toLine(route, me.getX(), me.getY());
	}

	/**
	 * Tiles from a tile to a route's line through its points.
	 */
	static double toLine(WorldPoint[] route, int x, int y)
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
	static double toSpawns(WorldPoint[] lake, int x, int y)
	{
		double nearest = Double.MAX_VALUE;
		for (WorldPoint spawn : lake)
		{
			nearest = Math.min(nearest, Math.hypot(spawn.getX() - x, spawn.getY() - y));
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
		// One waiting spot a tick gets its ring.
		NPC waiting = toAttach.poll();
		if (waiting != null)
		{
			add(waiting);
		}
		for (Iterator<Opening> all = openings.iterator(); all.hasNext(); )
		{
			Opening opening = all.next();
			Mapping mapping = opening.mapping;
			long started = System.nanoTime();
			if (!opening.mapped)
			{
				if (!mapping.step())
				{
					note(opening, started, mapping.step - 1);
					continue;
				}
				if (mapping.river == null)
				{
					all.remove();
					unmappable.add(mapping.route);
					continue;
				}
				// Filled with fish next tick.
				opening.mapped = true;
				note(opening, started, mapping.step - 1);
				continue;
			}
			all.remove();
			startShoal(mapping);
			toAttach.addAll(opening.spots);
			note(opening, started, MAP_STEPS);
			int worst = 0;
			double total = 0;
			for (int k = 0; k < opening.steps.length; k++)
			{
				worst = opening.steps[k] > opening.steps[worst] ? k : worst;
				total += opening.steps[k];
			}
			timeMs(label(mapping.route) + " start, worst step: " + STEP_NAMES[worst],
				Math.round(opening.steps[worst] * 10) / 10.0);
			lastLoad = label(mapping.route);
			lastLoadSteps = opening.steps;
			log.debug("{} started over {} client ticks, {} ms in all", label(mapping.route), MAP_STEPS + 1,
				Math.round(total * 10) / 10.0);
		}
		// Rivers mapped again as they're walked, a step a tick; the fish swim on the old map until the new one's done.
		for (Shoal shoal : shoals)
		{
			Mapping mapping = shoal.remapping;
			if (mapping == null)
			{
				continue;
			}
			long started = System.nanoTime();
			if (mapping.step())
			{
				shoal.remapping = null;
				if (mapping.river != null)
				{
					swapIn(shoal, mapping);
				}
			}
			time(label(shoal.route) + " remap step " + mapping.step, started);
		}
	}

	/**
	 * TEMPORARY: notes how long a mapping step took, for the debug panel.
	 */
	private static void note(Opening opening, long started, int step)
	{
		opening.steps[step] += (System.nanoTime() - started) / 1e6;
	}

	/**
	 * Fills a newly mapped river or lake with fish.
	 */
	private Shoal startShoal(Mapping mapping)
	{
		WorldView view = mapping.view;
		WorldPoint[] route = mapping.route;
		saidOutOfSight.remove(route[0]);
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
		shoal.spacing = riverSpacing(shoal.route);
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
	 * EMPTY_GAP spacings apart (more than a school and the room round it), growing in; not once it has its fish.
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
		// Not when well over what it keeps (groups, and single fish half as many again): the cap the upstream stream
		// has, so bunched fish elsewhere in it don't stop an empty stretch filling.
		if (count >= 2 * windowFish(shoal))
		{
			return;
		}
		along[count++] = shoal.windowTo;
		Arrays.sort(along, 0, count);
		double last = shoal.windowFrom;
		for (int k = 0; k < count; k++)
		{
			double s = along[k];
			if (s - last > EMPTY_GAP * gap)
			{
				fillStretch(shoal, last + gap * 2, s - gap * 2, random);
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
		boolean nearEnd = river.cutStart && shoal.playerAt - (loadRange() + REMAP_EDGE) * 128.0 < 0
			|| river.cutEnd && shoal.playerAt + (loadRange() + REMAP_EDGE) * 128.0 > river.length;
		return nearEnd && me != null && (shoal.mappedAround == null || me.distanceTo2D(shoal.mappedAround) >= REMAP_MOVE);
	}

	/**
	 * Tiles along a river either side of the player that keep fish, seen or not.
	 */
	static int loadRange()
	{
		return FISH_RANGE + LOAD_MORE;
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
		shoal.windowFrom = Math.max(0, shoal.playerAt - loadRange() * 128.0);
		shoal.windowTo = Math.min(river.length, shoal.playerAt + loadRange() * 128.0);
	}

	/**
	 * Rivers: fish kept along the window at their spacing.
	 */
	private static int windowFish(Shoal shoal)
	{
		return Math.max(1, (int) Math.round((shoal.windowTo - shoal.windowFrom) / shoal.spacing));
	}

	/**
	 * Rivers: the gap between fish along the path, wider for a river with a smaller share of fish.
	 */
	private double riverSpacing(WorldPoint[] route)
	{
		return TRAVEL_SPACING * spacingScale * 100 / Math.max(1, bakedFishShare(route[0]));
	}

	/**
	 * How many fish a river (from its path's length) or lake (from its water) keeps.
	 */
	private int fishCount(WorldPoint[] route, River river, boolean lake)
	{
		Baked saved = baked.get(route[0]);
		double share = saved == null ? 1 : saved.fishShare / 100.0;
		return Math.max(1, (int) Math.round(share * (lake
			? river.waterCells * CELL * CELL / (LAKE_ROOM * lakeSpacingScale)
			: river.length / (TRAVEL_SPACING * spacingScale))));
	}

	/**
	 * TEMPORARY: a river or lake's share of fish, percent, 100 if it has no bake.
	 */
	int bakedFishShare(WorldPoint first)
	{
		Baked saved = baked.get(first);
		return saved == null ? 100 : saved.fishShare;
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
	 * The nearest route within ROUTE_REACH tiles of a spot; null for none.
	 */
	private WorldPoint[] routeFor(WorldPoint spot)
	{
		WorldPoint[] best = null;
		double nearest = ROUTE_REACH;
		for (WorldPoint[] route : routes)
		{
			if (route[0].getPlane() != spot.getPlane())
			{
				continue;
			}
			double d = toLine(route, spot.getX(), spot.getY());
			if (d <= nearest)
			{
				nearest = d;
				best = route;
			}
		}
		if (best != null)
		{
			return best;
		}
		// Or a lake whose spawn points it's among.
		for (WorldPoint[] lake : lakes)
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
	 * TEMPORARY: a baked river or lake's water as each world tile's cell bits (key x << 14 | y, a bit per cell, row by
	 * row), for baking it again; empty if it has no bake.
	 */
	Map<Integer, Integer> bakedTiles(WorldPoint first)
	{
		Map<Integer, Integer> tiles = new HashMap<>();
		Baked saved = baked.get(first);
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
	 * TEMPORARY: a river or lake's fish blockers, allowers or surfacers (kind 0, 1 or 2; tile, half), empty if it has
	 * no bake.
	 */
	Map<WorldPoint, String> bakedMarks(WorldPoint first, int kind)
	{
		Baked saved = baked.get(first);
		return saved == null ? Map.of() : saved.marks(kind);
	}

	/**
	 * TEMPORARY: a river or lake's marks of every kind (MARK_WORDS order), empty if it has no bake.
	 */
	List<Map<WorldPoint, String>> bakedMarks(WorldPoint first)
	{
		List<Map<WorldPoint, String>> marks = new ArrayList<>();
		for (int kind = 0; kind < MARK_WORDS.length; kind++)
		{
			marks.add(bakedMarks(first, kind));
		}
		return marks;
	}

	/**
	 * TEMPORARY: a river's side channels' picked tiles (world x, y, x, y, ...), empty if it has no bake.
	 */
	List<int[]> bakedBranches(WorldPoint first)
	{
		Baked saved = baked.get(first);
		return saved == null ? List.of() : saved.branchStops;
	}

	/**
	 * TEMPORARY: whether a baked river has a side channel whose path isn't laid yet.
	 */
	boolean hasUnlaidBranch(WorldPoint first)
	{
		Baked saved = baked.get(first);
		return saved != null && saved.branchPaths.stream().anyMatch(points -> points.length < 4);
	}

	/**
	 * TEMPORARY: a baked river's side channel's share of fish, percent, or -1 for by its width; returns the bake's new
	 * text, or null if it has none.
	 */
	String setBranchShare(WorldPoint first, int index, int share)
	{
		Baked saved = baked.get(first);
		if (saved == null || index >= saved.branchShares.size())
		{
			return null;
		}
		saved.branchShares.set(index, share);
		return textOf(saved);
	}

	/**
	 * TEMPORARY: a baked river's side channels' shares of fish, percent or -1, empty if it has no bake.
	 */
	List<Integer> bakedBranchShares(WorldPoint first)
	{
		Baked saved = baked.get(first);
		return saved == null ? List.of() : saved.branchShares;
	}

	/**
	 * TEMPORARY: removes a baked river's side channel, by its place in the list.
	 */
	void removeBranch(WorldPoint first, int index)
	{
		Baked saved = baked.get(first);
		if (saved != null && index < saved.branchStops.size())
		{
			saved.branchStops.remove(index);
			saved.branchPaths.remove(index);
			saved.branchShares.remove(index);
		}
	}

	/**
	 * TEMPORARY: adds a side channel's picked tiles (world x, y, x, y, ...) to a baked river; false if it has no bake.
	 */
	boolean addBranch(WorldPoint first, int[] stops)
	{
		Baked saved = baked.get(first);
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
	 * TEMPORARY: places a fish blocker, allower or surfacer (kind 0, 1 or 2; a half, or WHOLE) on a tile of a baked
	 * river or lake, or with null clears it; a blocker and an allower replace each other, a surfacer goes with either.
	 * Returns the bake's new text to save, or null if it has no bake.
	 */
	String setMark(WorldPoint first, WorldPoint tile, String half, int kind)
	{
		Baked saved = baked.get(first);
		if (saved == null)
		{
			return null;
		}
		// Blockers and allowers replace each other, as do surfacers and divers.
		saved.marks(kind / 2 * 2).remove(tile);
		saved.marks(kind / 2 * 2 + 1).remove(tile);
		if (half != null)
		{
			saved.marks(kind).put(tile, half);
		}
		return textOf(saved);
	}

	/**
	 * TEMPORARY: a baked river or lake as its file's text.
	 */
	private static String textOf(Baked saved)
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
		return bakedText(headLine(saved.route, saved.lake), saved.fishShare, x0, saved.y0, rows, path, saved.marks,
			saved.branchStops, saved.branchPaths, saved.branchShares);
	}

	/**
	 * TEMPORARY: takes a bake file's new text (one changed in .runelite), and maps every river and lake again with
	 * it, keeping the fish.
	 */
	void reloadBaked(String fileName, String text)
	{
		List<WorldPoint> firsts = addBaked(fileName.replace(".txt", ""), text);
		unmappable.removeIf(route -> firsts.contains(route[0]));
		log.debug("Reloaded the bake {}", fileName);
		remapAll();
	}

	/**
	 * TEMPORARY: a bake file's text: every river and lake in it (rivers first, then lakes, each by its first point),
	 * those given by first point as their new text, the rest as they are.
	 */
	String fileText(String fileName, Map<WorldPoint, String> texts)
	{
		String name = fileName.replace(".txt", "");
		List<WorldPoint> firsts = new ArrayList<>(texts.keySet());
		NAMES.forEach((first, of) ->
		{
			if (of.equals(name) && !firsts.contains(first) && baked.containsKey(first))
			{
				firsts.add(first);
			}
		});
		Map<WorldPoint, String> all = new HashMap<>(texts);
		firsts.forEach(first -> all.computeIfAbsent(first, f -> textOf(baked.get(f))));
		firsts.sort((a, b) -> all.get(a).startsWith("lake") != all.get(b).startsWith("lake")
			? (all.get(a).startsWith("lake") ? 1 : -1)
			: a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX()) : Integer.compare(a.getY(), b.getY()));
		StringBuilder file = new StringBuilder();
		firsts.forEach(first -> file.append(all.get(first)));
		return file.toString();
	}

	/**
	 * TEMPORARY: a lake's spawn points changed, as a pick adds one; returns its bake's new text, or null if it has none.
	 */
	String setLakeSpawns(WorldPoint first, WorldPoint[] spawns)
	{
		Baked saved = baked.get(first);
		if (saved == null)
		{
			return null;
		}
		saved.route = spawns;
		int index = lakes.indexOf(baked.get(first) == saved ? routeOf(first) : null);
		if (index >= 0)
		{
			lakes.set(index, spawns);
		}
		return textOf(saved);
	}

	/**
	 * TEMPORARY: a river's route, or a lake's spawn points, changed: points added, moved or removed; returns its bake's
	 * new text, or null if it has none. Its name stays, though its first point may change.
	 */
	String setRoute(WorldPoint[] route, WorldPoint[] changed)
	{
		Baked saved = baked.remove(route[0]);
		if (saved == null)
		{
			return null;
		}
		String name = NAMES.remove(route[0]);
		saved.route = changed;
		baked.put(changed[0], saved);
		NAMES.put(changed[0], name);
		List<WorldPoint[]> list = lakes.contains(route) ? lakes : routes;
		int index = list.indexOf(route);
		if (index >= 0)
		{
			list.set(index, changed);
		}
		return textOf(saved);
	}

	/**
	 * The river or lake whose first point this is, or null.
	 */
	WorldPoint[] routeOf(WorldPoint first)
	{
		for (WorldPoint[] route : routesAndLakes())
		{
			if (route[0].equals(first))
			{
				return route;
			}
		}
		return null;
	}

	/**
	 * TEMPORARY: maps every river and lake again, keeping the fish, after a bake or its blockers change.
	 */
	void remapAll()
	{
		ringPlaces.clear();
		WorldView view = client.getTopLevelWorldView();
		for (Shoal shoal : shoals)
		{
			if (view != null)
			{
				remap(view, shoal);
			}
		}
	}

	/**
	 * Every river and lake.
	 */
	List<WorldPoint[]> routesAndLakes()
	{
		List<WorldPoint[]> all = new ArrayList<>(routes);
		all.addAll(lakes);
		return all;
	}

	boolean isLake(WorldPoint[] route)
	{
		return lakes.contains(route);
	}

	/**
	 * Gives a spot its circle.
	 */
	private void attach(Shoal shoal, NPC spot, LocalPoint at)
	{
		// The tuning spinners' fixed offset, else away from the nearest bank.
		int[] fixed = RING_MANUAL ? new int[]{RING_EAST, RING_NORTH} : null;
		double x;
		double y;
		if (fixed != null)
		{
			x = at.getX() + fixed[0];
			y = at.getY() + fixed[1];
		}
		else
		{
			// Worked out once per spot tile, as the water doesn't change: where the ring's middle is from the spot.
			double[] offset = ringPlaces.computeIfAbsent(spot.getWorldLocation(), tile ->
			{
				double[] away = awayFromBank(shoal.river, at.getX(), at.getY());
				double[] round = roundRing(shoal.river, at.getX() + away[0] * CIRCLE_OFFSET,
					at.getY() + away[1] * CIRCLE_OFFSET);
				return new double[]{round[0] - at.getX(), round[1] - at.getY()};
			});
			x = at.getX() + offset[0];
			y = at.getY() + offset[1];
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
	 * Moves a ring's middle away from the banks a step at a time, up the slope of the bank distances, until it's far
	 * enough from them all for its outer lane to keep clear of them (so it isn't pulled out of shape), or it's as far
	 * from them as it gets there, the middle of the water; within ROUND_MOST of where it started.
	 */
	private static double[] roundRing(River river, double startX, double startY)
	{
		double need = CIRCLE_SIZE + BANK_GAP;
		double x = startX;
		double y = startY;
		double room = river.clearanceAt(x, y);
		for (int step = 0; step < ROUND_STEPS && room < need; step++)
		{
			double slopeX = river.clearanceAt(x + CELL, y) - river.clearanceAt(x - CELL, y);
			double slopeY = river.clearanceAt(x, y + CELL) - river.clearanceAt(x, y - CELL);
			double slope = Math.hypot(slopeX, slopeY);
			if (slope < 1)
			{
				break;
			}
			double nextX = x + slopeX / slope * ROUND_STEP;
			double nextY = y + slopeY / slope * ROUND_STEP;
			double nextRoom = river.clearanceAt(nextX, nextY);
			// Never back down the slope, past the middle.
			if (nextRoom < room || Math.hypot(nextX - startX, nextY - startY) > ROUND_MOST)
			{
				break;
			}
			x = nextX;
			y = nextY;
			room = nextRoom;
		}
		return new double[]{x, y};
	}

	/**
	 * Cos and sin of some directions evenly round a circle.
	 */
	private static double[][] ways(int count)
	{
		double[][] ways = new double[count][];
		for (int a = 0; a < count; a++)
		{
			double angle = 2 * Math.PI * a / count;
			ways[a] = new double[]{Math.cos(angle), Math.sin(angle)};
		}
		return ways;
	}

	/**
	 * Which way open water lies from a place, as a unit direction: towards the roomiest water round it.
	 */
	private static double[] awayFromBank(River river, double x, double y)
	{
		double dx = 0;
		double dy = 0;
		for (double[] way : LOOK_WAYS)
		{
			double room = river.clearanceAt(x + OFFSET_LOOK * way[0], y + OFFSET_LOOK * way[1]);
			dx += room * way[0];
			dy += room * way[1];
		}
		double length = Math.hypot(dx, dy);
		return length > 0 ? new double[]{dx / length, dy / length} : new double[]{0, 0};
	}

	/**
	 * Maps a baked river or lake a step at a time: a new one, or one walked along, a step a client tick, so no one
	 * frame takes it all; after a map load or a bake, all at once. It maps the scene it was made for; a map load starts
	 * it again.
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
						if (saidOutOfSight.add(route[0]))
						{
							log.debug("River route {} to {}: not in sight", route[0], last);
						}
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
					// Side channels with either end in the box (and all of them in the scene), so it takes them in too.
					int boxMinX = minX;
					int boxMinY = minY;
					int boxMaxX = maxX;
					int boxMaxY = maxY;
					for (int[] branch : saved.branchStops)
					{
						boolean anEnd = false;
						boolean inScene = true;
						for (int k = 0; k + 1 < branch.length; k += 2)
						{
							int x = branch[k] - view.getBaseX();
							int y = branch[k + 1] - view.getBaseY();
							boolean end = k == 0 || k == branch.length - 2;
							anEnd |= end && x >= boxMinX && x <= boxMaxX && y >= boxMinY && y <= boxMaxY;
							inScene &= x >= ROUTE_MARGIN + WATER_REACH && y >= ROUTE_MARGIN + WATER_REACH
								&& x < view.getSizeX() - ROUTE_MARGIN - WATER_REACH && y < view.getSizeY() - ROUTE_MARGIN - WATER_REACH;
						}
						if (!anEnd || !inScene)
						{
							continue;
						}
						for (int k = 0; k + 1 < branch.length; k += 2)
						{
							minX = Math.min(minX, branch[k] - view.getBaseX());
							minY = Math.min(minY, branch[k + 1] - view.getBaseY());
							maxX = Math.max(maxX, branch[k] - view.getBaseX());
							maxY = Math.max(maxY, branch[k + 1] - view.getBaseY());
						}
					}
					int[] first = stops.get(0);
					int[] end = stops.get(stops.size() - 1);
					ends = new int[]{first[0] * 128 + 64, first[1] * 128 + 64, end[0] * 128 + 64, end[1] * 128 + 64};
					// Only as far as the baked water within WATER_REACH goes, with a tile of land round it for the banks.
					int[] wet = {minX, minY, maxX, maxY};
					waterBounds(saved, view.getBaseX(), view.getBaseY(), new int[]{minX - WATER_REACH, minY - WATER_REACH,
						maxX + WATER_REACH, maxY + WATER_REACH}, wet);
					river = new River((wet[0] - 1) * 128, (wet[1] - 1) * 128,
						(wet[2] - wet[0] + 3) * PER_TILE, (wet[3] - wet[1] + 3) * PER_TILE);
					return true;
				}
				case 1:
					bakedWet(saved, view.getBaseX(), view.getBaseY(), river);
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
					bakedBranches(river, saved, view.getBaseX(), view.getBaseY());
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
							if (saidOutOfSight.add(route[0]))
							{
								log.debug("Lake {}: not all in sight", route[0]);
							}
							return false;
						}
						minX = Math.min(minX, points[k].getSceneX());
						minY = Math.min(minY, points[k].getSceneY());
						maxX = Math.max(maxX, points[k].getSceneX());
						maxY = Math.max(maxY, points[k].getSceneY());
					}
					river = new River((minX - LAKE_MARGIN) * 128, (minY - LAKE_MARGIN) * 128,
						(maxX - minX + 2 * LAKE_MARGIN + 1) * PER_TILE, (maxY - minY + 2 * LAKE_MARGIN + 1) * PER_TILE);
					return true;
				}
				case 1:
					bakedWet(saved, view.getBaseX(), view.getBaseY(), river);
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
					int tilesX = river.width / PER_TILE;
					int tilesY = river.height / PER_TILE;
					int row = tilesX + 1;
					river.tileWater = new int[row * (tilesY + 1)];
					for (int tj = 0; tj < tilesY; tj++)
					{
						for (int ti = 0; ti < tilesX; ti++)
						{
							int count = 0;
							for (int j = tj * PER_TILE; j < (tj + 1) * PER_TILE; j++)
							{
								for (int i = ti * PER_TILE; i < (ti + 1) * PER_TILE; i++)
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
		// Whether it's mapped, to be filled with fish next tick.
		private boolean mapped;
		// TEMPORARY: each step's time (ms, the last its filling with fish), for the debug panel.
		private final double[] steps = new double[MAP_STEPS + 1];

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
				hover(river, swimmer, into);
				return;
			}
			swimmer.setOffAt = -1;
			swimmer.burstUntil = cycle + LAKE_SET_OFF_BURST;
		}
		double fromX = grouped ? middleX : swimmer.x;
		double fromY = grouped ? middleY : swimmer.y;
		double goalX = grouped ? group.goalX : swimmer.goalX;
		double goalY = grouped ? group.goalY : swimmer.goalY;
		boolean reached = !Double.isNaN(goalX)
			&& (goalX - fromX) * (goalX - fromX) + (goalY - fromY) * (goalY - fromY) < LAKE_GOAL_REACHED * LAKE_GOAL_REACHED;
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
				hover(river, swimmer, into);
				return;
			}
		}
		double ux = swimmer.headX;
		double uy = swimmer.headY;
		double apart = Double.isNaN(goalX) ? 0
			: Math.sqrt((goalX - fromX) * (goalX - fromX) + (goalY - fromY) * (goalY - fromY));
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
			double dx = x - circle.x;
			double dy = y - circle.y;
			double outSquared = dx * dx + dy * dy;
			if (outSquared < clear * clear && outSquared > 1)
			{
				double out = Math.sqrt(outSquared);
				x = circle.x + dx * clear / out;
				y = circle.y + dy * clear / out;
			}
		}
		into[0] = x;
		into[1] = y;
		offBanks(river, swimmer, into);
	}

	/**
	 * Aims a paused lake fish just ahead on its heading, kept off banks.
	 */
	private static void hover(River river, Swimmer swimmer, double[] into)
	{
		into[0] = swimmer.x + swimmer.headX * LOOK_AHEAD;
		into[1] = swimmer.y + swimmer.headY * LOOK_AHEAD;
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
			int reach = loadRange() + MAP_MORE;
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
		int reach = PER_TILE + 1;
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
	 * Grows bounds (scene tiles: west, south, east, north) to take in a bake's water inside a box of scene tiles.
	 */
	private static void waterBounds(Baked saved, int baseX, int baseY, int[] within, int[] bounds)
	{
		for (int j = 0; j < saved.rows.length; j++)
		{
			int y = Math.floorDiv(saved.y0 + j, PER_TILE) - baseY;
			if (y < within[1] || y > within[3])
			{
				continue;
			}
			int[] runs = saved.rows[j];
			for (int k = 0; k + 1 < runs.length; k += 2)
			{
				int from = Math.max(within[0], Math.floorDiv(runs[k], PER_TILE) - baseX);
				int to = Math.min(within[2], Math.floorDiv(runs[k + 1] - 1, PER_TILE) - baseX);
				if (from <= to)
				{
					include(bounds, from, y);
					include(bounds, to, y);
				}
			}
		}
	}

	/**
	 * Grows bounds (west, south, east, north) to take in a place.
	 */
	static void include(int[] bounds, int x, int y)
	{
		bounds[0] = Math.min(bounds[0], x);
		bounds[1] = Math.min(bounds[1], y);
		bounds[2] = Math.max(bounds[2], x);
		bounds[3] = Math.max(bounds[3], y);
	}

	/**
	 * Marks a map's water from a baked river or lake, a run of water at a time.
	 */
	private static void bakedWet(Baked saved, int baseX, int baseY, River river)
	{
		int width = river.width;
		boolean[] water = river.water;
		int cellX0 = baseX * PER_TILE + Math.floorDiv(river.x0, CELL);
		int cellY0 = baseY * PER_TILE + Math.floorDiv(river.y0, CELL);
		for (int j = 0; j < river.height; j++)
		{
			int row = cellY0 + j - saved.y0;
			if (row < 0 || row >= saved.rows.length)
			{
				continue;
			}
			int[] runs = saved.rows[row];
			for (int k = 0; k + 1 < runs.length; k += 2)
			{
				int from = Math.max(0, runs[k] - cellX0);
				int to = Math.min(width, runs[k + 1] - cellX0);
				if (from < to)
				{
					Arrays.fill(water, j * width + from, j * width + to, true);
				}
			}
		}
		// Fish blockers: the cells of their tiles they cover are land; then fish allowers: theirs are water; then fish
		// surfacers' and divers': theirs bring deep fish up, or take them down.
		river.surface = saved.marks(2).isEmpty() ? null : new boolean[width * river.height];
		river.dive = saved.marks(3).isEmpty() ? null : new boolean[width * river.height];
		for (int kind = 0; kind < MARK_WORDS.length; kind++)
		{
			boolean[] cellsOf = kind == 2 ? river.surface : kind == 3 ? river.dive : water;
			for (Map.Entry<WorldPoint, String> mark : saved.marks(kind).entrySet())
			{
				int[] cells = blockedCells(mark.getValue());
				int i0 = mark.getKey().getX() * PER_TILE - cellX0;
				int j0 = mark.getKey().getY() * PER_TILE - cellY0;
				for (int j = Math.max(0, j0 + cells[2]); j < Math.min(river.height, j0 + cells[3]); j++)
				{
					for (int i = Math.max(0, i0 + cells[0]); i < Math.min(width, i0 + cells[1]); i++)
					{
						cellsOf[j * width + i] = kind != 0;
					}
				}
			}
		}
	}

	/**
	 * The cells of its tile a fish blocker or allower covers (WHOLE, WEST, EAST, SOUTH or NORTH half): from and to column, west
	 * to east, and from and to row, south to north.
	 */
	static int[] blockedCells(String half)
	{
		return new int[]{half.equals("EAST") ? PER_TILE / 2 : 0, half.equals("WEST") ? PER_TILE / 2 : PER_TILE,
			half.equals("NORTH") ? PER_TILE / 2 : 0, half.equals("SOUTH") ? PER_TILE / 2 : PER_TILE};
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
		double[][] curved = curveThrough(saved.pathX, saved.pathY, from, to, offsetX, offsetY);
		finishPath(river, curved[0], curved[1], curved[0].length);
		return true;
	}

	/**
	 * Baked points are a tile apart: a curve through them (Catmull-Rom) from one to another, CURVE_STEPS points a
	 * tile, so a path turns smoothly instead of at each point; moved into the scene by the offset.
	 */
	private static double[][] curveThrough(double[] px, double[] py, int from, int to, double offsetX, double offsetY)
	{
		int segments = to - from;
		int count = segments * CURVE_STEPS + 1;
		double[] xs = new double[count];
		double[] ys = new double[count];
		for (int k = 0; k < count; k++)
		{
			int at = Math.min(k / CURVE_STEPS, segments - 1);
			double t = (k - at * CURVE_STEPS) / (double) CURVE_STEPS;
			int a = from + Math.max(at - 1, 0);
			int b = from + at;
			int c = from + at + 1;
			int d = from + Math.min(at + 2, segments);
			xs[k] = curve(px[a], px[b], px[c], px[d], t) - offsetX;
			ys[k] = curve(py[a], py[b], py[c], py[d], t) - offsetY;
		}
		return new double[][]{xs, ys};
	}

	/**
	 * Lays a river's side channels from their baked paths, those wholly in its box, each leaving and joining its path
	 * a way apart; fish take one by its share of the width at the fork.
	 */
	private static void bakedBranches(River river, Baked saved, int baseX, int baseY)
	{
		double offsetX = baseX * 128.0;
		double offsetY = baseY * 128.0;
		for (int b = 0; b < saved.branchPaths.size(); b++)
		{
			int[] points = saved.branchPaths.get(b);
			int set = b < saved.branchShares.size() ? saved.branchShares.get(b) : -1;
			int count = points.length / 2;
			if (count < 3)
			{
				continue;
			}
			double[] px = new double[count];
			double[] py = new double[count];
			boolean inside = true;
			for (int k = 0; k < count; k++)
			{
				px[k] = points[2 * k];
				py[k] = points[2 * k + 1];
				double x = px[k] - offsetX;
				double y = py[k] - offsetY;
				inside &= x >= river.x0 && y >= river.y0 && x < river.x0 + river.width * CELL
					&& y < river.y0 + river.height * CELL;
			}
			if (!inside)
			{
				continue;
			}
			River line = new River(river);
			double[][] curved = curveThrough(px, py, 0, count - 1, offsetX, offsetY);
			finishPath(line, curved[0], curved[1], curved[0].length);
			double fromS = river.nearest(line.pathX[0], line.pathY[0]);
			double toS = river.nearest(line.pathX[line.pathX.length - 1], line.pathY[line.pathY.length - 1]);
			if (toS - fromS < 256)
			{
				continue;
			}
			// Its share: its room against the river's own over the same stretch.
			double branchRoom = 0;
			for (double room : line.left)
			{
				branchRoom += room / line.left.length;
			}
			double mainRoom = 0;
			int mainPoints = 0;
			for (int k = 0; k < river.along.length; k++)
			{
				if (river.along[k] >= fromS && river.along[k] <= toS)
				{
					mainRoom += river.left[k];
					mainPoints++;
				}
			}
			mainRoom /= Math.max(1, mainPoints);
			double share = set >= 0 ? set / 100.0 : branchRoom / Math.max(1, branchRoom + mainRoom);
			river.branches.add(new Branch(line, fromS, toS, share));
		}
	}

	/**
	 * Debug: hatches a path's room, the shape between its room edges.
	 */
	private void shade(Graphics2D graphics, WorldView view, Shoal shoal, River way)
	{
		// The room as one shape: down the left edge, back up the right.
		int points = way.pathX.length;
		for (int n = 0; n < 2 * points; n++)
		{
			int k = n < points ? n : 2 * points - 1 - n;
			int ahead = Math.min(k + 1, points - 1);
			int behind = Math.max(k - 1, 0);
			double d = Math.max(1e-6, Math.hypot(way.pathX[ahead] - way.pathX[behind], way.pathY[ahead] - way.pathY[behind]));
			double dx = (way.pathX[ahead] - way.pathX[behind]) / d;
			double dy = (way.pathY[ahead] - way.pathY[behind]) / d;
			double out = n < points ? way.left[k] : -way.right[k];
			mark(shoal, way.pathX[k] - dy * out, way.pathY[k] + dx * out);
		}
		project(view);
		Polygon room = new Polygon();
		for (int k = 0; k < marked; k++)
		{
			if (screenX[k] != Integer.MIN_VALUE)
			{
				room.addPoint(screenX[k], screenY[k]);
			}
		}
		marked = 0;
		Paint before = graphics.getPaint();
		graphics.setPaint(HATCH);
		graphics.fill(room);
		graphics.setPaint(before);
	}

	// Points a tile along the curve through the baked path's points.
	private static final int CURVE_STEPS = 8;

	/**
	 * A Catmull-Rom curve between b and c, t of the way, steered by the points either side.
	 */
	private static double curve(double a, double b, double c, double d, double t)
	{
		return b + 0.5 * t * (c - a + t * (2 * a - 5 * b + 4 * c - d + t * (3 * (b - c) + d - a)));
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
	 * Debug: the line round a river's water where it's BANK_GAP from the bank, as segments (x, y, x, y per segment),
	 * traced over the cells' bank distances by marching squares; worked out the first time it's drawn.
	 */
	private static double[] roomEdge(River river)
	{
		if (river.roomEdge != null)
		{
			return river.roomEdge;
		}
		int width = river.width;
		int height = river.height;
		// The bank distances blurred (each cell the average of it and its neighbours), so the line is smooth.
		double[] near = new double[width * height];
		for (int j = 0; j < height; j++)
		{
			for (int i = 0; i < width; i++)
			{
				double sum = 0;
				int cells = 0;
				for (int dj = -1; dj <= 1; dj++)
				{
					for (int di = -1; di <= 1; di++)
					{
						if (i + di >= 0 && j + dj >= 0 && i + di < width && j + dj < height)
						{
							sum += river.clearance[(j + dj) * width + i + di];
							cells++;
						}
					}
				}
				near[j * width + i] = sum / cells;
			}
		}
		double[] segments = new double[64];
		int count = 0;
		double[] cross = new double[8];
		for (int j = 0; j + 1 < river.height; j++)
		{
			for (int i = 0; i + 1 < width; i++)
			{
				// The square between four cells' middles: corners south-west, south-east, north-east, north-west.
				double a = near[j * width + i] - BANK_GAP;
				double b = near[j * width + i + 1] - BANK_GAP;
				double c = near[(j + 1) * width + i + 1] - BANK_GAP;
				double d = near[(j + 1) * width + i] - BANK_GAP;
				double x = river.x0 + i * CELL + CELL / 2.0;
				double y = river.y0 + j * CELL + CELL / 2.0;
				// Where the line crosses each side the corners differ on, in order round the square.
				int found = 0;
				double[][] sides = {{a, b, x, y, CELL, 0}, {b, c, x + CELL, y, 0, CELL}, {c, d, x + CELL, y + CELL, -CELL, 0},
					{d, a, x, y + CELL, 0, -CELL}};
				for (double[] side : sides)
				{
					if ((side[0] > 0) != (side[1] > 0))
					{
						double t = side[0] / (side[0] - side[1]);
						cross[found++] = side[2] + side[4] * t;
						cross[found++] = side[3] + side[5] * t;
					}
				}
				// Two crossings make one segment; four (a saddle) make two.
				for (int k = 0; k + 3 < found; k += 4)
				{
					if (count + 4 > segments.length)
					{
						segments = Arrays.copyOf(segments, segments.length * 2);
					}
					System.arraycopy(cross, k, segments, count, 4);
					count += 4;
				}
			}
		}
		river.roomEdge = Arrays.copyOf(segments, count);
		return river.roomEdge;
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
				// Land stays 0.
				if (d[c] == 0)
				{
					continue;
				}
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
				if (d[c] == 0)
				{
					continue;
				}
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
			if (river.water[c])
			{
				river.clearance[c] = (short) Math.min(Short.MAX_VALUE, d[c] - CELL / 2);
			}
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
			// The room either side: the distance to the nearest bank, which changes smoothly, can't run on through a
			// gap, and can't fold back on a bend, as the path keeps to the middle.
			double room = Math.max(0, river.smoothClearance(pathX[p], pathY[p]) - BANK_GAP);
			river.left[p] = room;
		}
		river.length = walked;
		// Smoothed along the river, over ROOM_SMOOTH points either side, so it doesn't step from cell to cell; rising at
		// most ROOM_SLACK past the bank distance, so it can't reach a rock.
		double sum = 0;
		int in = 0;
		for (int p = 0; p < Math.min(ROOM_SMOOTH, points); p++)
		{
			sum += river.left[p];
			in++;
		}
		for (int p = 0; p < points; p++)
		{
			if (p + ROOM_SMOOTH < points)
			{
				sum += river.left[p + ROOM_SMOOTH];
				in++;
			}
			if (p - ROOM_SMOOTH - 1 >= 0)
			{
				sum -= river.left[p - ROOM_SMOOTH - 1];
				in--;
			}
			river.right[p] = Math.min(sum / in, river.left[p] + ROOM_SLACK);
		}
		System.arraycopy(river.right, 0, river.left, 0, points);
	}

	// Room smoothing: path points averaged either side (16 local units apart), and the most it may rise past the bank
	// distance, local units.
	private static final int ROOM_SMOOTH = 4;
	private static final double ROOM_SLACK = 32;

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
		swimmer.headX = Math.cos(swimmer.facing);
		swimmer.headY = Math.sin(swimmer.facing);
		swimmer.swimming = TRAVEL_SPEED * swimmer.speed;
		fish.setLocation(new LocalPoint((int) swimmer.x, (int) swimmer.y, shoal.worldView), shoal.plane);
		// One spawned in sight grows in as new fish do; one out of sight grows in once it comes into sight.
		swimmer.inSight = inSight(shoal, swimmer) ? 1 : 0;
		show(shoal, swimmer, 0, cycle);
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
		if (toAttach.remove(spot))
		{
			return true;
		}
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
			double nearestApart = REGROUP_RANGE * REGROUP_RANGE;
			for (Swimmer other : shoal.fish)
			{
				if (other == swimmer || (other.item == RAINBOW) != (swimmer.item == RAINBOW))
				{
					continue;
				}
				boolean school = other.group != null && other.group.members.size() < GROUP_MOST && other.circle == null
					&& other.shrinkingSince < 0;
				boolean ready = other.regroupAt >= 0 && cycle >= other.regroupAt && alone(other);
				double dx = other.x - swimmer.x;
				double dy = other.y - swimmer.y;
				double apart = dx * dx + dy * dy;
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
		TickTimes.add(TickTimes.MAPPING, opened);
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
				hide(shoal);
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
			started = TickTimes.add(TickTimes.FILLING, started);
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
			started = TickTimes.add(TickTimes.SPAWNING, started);
			// Rivers keep fish round the player, filling empty stretches.
			if (!shoal.lake && !shoal.leaving && cycle >= shoal.nextWindow)
			{
				updateWindow(shoal);
				// Walked towards the end of what's mapped: map again round the player, a step a tick, keeping the fish.
				WorldView view = client.getTopLevelWorldView();
				if (shoal.remapping == null && outgrown(shoal) && view != null)
				{
					shoal.remapping = new Mapping(view, shoal.route, shoal.plane);
				}
				if (shoal.toFill.isEmpty())
				{
					fillGaps(shoal, random);
				}
				shoal.nextWindow = cycle + WINDOW_EVERY;
			}
			started = TickTimes.add(TickTimes.WINDOW, started);
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
			started = TickTimes.add(TickTimes.SPAWNING, started);
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
			started = TickTimes.add(TickTimes.SCHOOLS, started);
			for (Iterator<Swimmer> it = shoal.fish.iterator(); it.hasNext(); )
			{
				Swimmer swimmer = it.next();
				if (swimmer.circle == null)
				{
					for (Circle circle : shoal.deciding ? shoal.circles.values() : List.<Circle>of())
					{
						// Lake fish may decide again once they've swum well away.
						double dx = swimmer.x - circle.x;
						double dy = swimmer.y - circle.y;
						double apartSquared = dx * dx + dy * dy;
						if (shoal.lake && apartSquared > 4 * LAKE_JOIN_RANGE * LAKE_JOIN_RANGE)
						{
							swimmer.decided.remove(circle);
						}
						if (swimmer.circle == null && !swimmer.decided.contains(circle)
							&& (shoal.lake ? apartSquared <= LAKE_JOIN_RANGE * LAKE_JOIN_RANGE
							: swimmer.s >= circle.along - JOIN_BEFORE))
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
				// Dips start only between bobs; on a fish surfacer, none start and bobs wait at rest.
				Look look = swimmer.look;
				boolean resting = swimmer.bobClock % (BOB_CYCLES + BOB_REST_CYCLES) >= BOB_CYCLES;
				if (swimmer.dippingSince >= 0 ? cycle - swimmer.dippingSince >= Math.max(1, look.dipMillis / 20)
					: resting && !swimmer.surfacing && look.dipDepth > 0
						&& random.nextDouble() < ticks / (look.dipEvery * 50.0))
				{
					swimmer.dippingSince = swimmer.dippingSince >= 0 ? -1 : cycle;
				}
				if (swimmer.dippingSince < 0 && !(swimmer.surfacing && resting))
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
			TickTimes.add(TickTimes.MOVING, started);
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
					(inLane(swimmer) ? inLane : comingIn).add(swimmer);
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
	private static boolean inLane(Swimmer swimmer)
	{
		return offLane(swimmer) <= LANE_ARRIVED;
	}

	/**
	 * How far a circling fish is from its lane, allowing for the lane pulled in from the bank.
	 */
	private static double offLane(Swimmer swimmer)
	{
		Circle circle = swimmer.circle;
		double dx = swimmer.x - circle.x;
		double dy = swimmer.y - circle.y;
		return Math.abs(Math.sqrt(dx * dx + dy * dy) - laneRadius(circle, swimmer.circleLane, Math.atan2(dy, dx)));
	}

	/**
	 * Client ticks until the next body, at random around BODY_EVERY.
	 */
	private static int bodyGap(ThreadLocalRandom random)
	{
		return BODY_TEST ? BODY_TEST_GAP : (int) Math.min(Integer.MAX_VALUE / 2, -BODY_EVERY * Math.log(1 - random.nextDouble()));
	}

	/**
	 * Starts a body drifting down now and then, and moves it.
	 */
	private void drift(Shoal shoal, int ticks, int cycle, ThreadLocalRandom random)
	{
		if (BODY_TEST)
		{
			shoal.nextBody = Math.min(shoal.nextBody, cycle + BODY_TEST_GAP);
		}
		Model model = (shoal.bodies.isEmpty() || BODY_TEST) && !shoal.leaving && cycle >= shoal.nextBody ? bodyModel(1)
			: null;
		if (model != null)
		{
			RuneLiteObject object = client.createRuneLiteObject();
			object.setModel(model);
			shoal.bodies.add(new Body(object, shoal.windowFrom, cycle, random));
			shoal.nextBody = cycle + bodyGap(random);
		}
		for (Iterator<Body> each = shoal.bodies.iterator(); each.hasNext(); )
		{
			Body body = each.next();
			if (!moveBody(shoal, body, ticks, cycle))
			{
				body.object.setActive(false);
				each.remove();
				shoal.nextBody = cycle + bodyGap(random);
			}
		}
	}

	/**
	 * Moves a body: growing in at the start, shrinking away at the end. Returns false once it has gone.
	 */
	private boolean moveBody(Shoal shoal, Body body, int ticks, int cycle)
	{
		double speed = TRAVEL_SPEED * BODY_SPEED;
		// Its place on the path, and how narrow the river is there: 0 wide, 1 narrow.
		shoal.river.at(body.s, point);
		double narrow = Math.max(0, Math.min(1, (BODY_WIDE - point[4] - point[5]) / (BODY_WIDE - BODY_NARROW)));
		body.narrow = narrow;
		body.s += speed * (1 + BODY_NARROW_SPEED * narrow) * ticks;
		// Spins freely unless at least half narrow; then lines up with the river, whichever end is already more
		// downstream first (the turn wrapped to half a circle), easing in from nothing there to fully at 1.
		double align = Math.max(0, (narrow - BODY_LINE_UP) / (1 - BODY_LINE_UP));
		double off = -Math.atan2(point[3], point[2]) * 1024 / Math.PI - 512 + BODY_FEET - body.facing;
		off -= 1024 * Math.round(off / 1024);
		body.facing += (body.turn * (1 - align) + off * BODY_ALIGN * align) * ticks;
		if (body.shrinkingSince < 0 && (shoal.leaving || body.s >= shoal.windowTo - speed * GROW_CYCLES
			|| body.s < shoal.windowFrom - WINDOW_SLACK))
		{
			body.shrinkingSince = cycle;
		}
		int step = growStep(body.growingSince, body.shrinkingSince, cycle);
		if (step == 0)
		{
			return false;
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
		double offset = share(body.across, point);
		double toX = point[0] - point[3] * offset;
		double toY = point[1] + point[2] * offset;
		if (Double.isNaN(body.x))
		{
			body.x = toX;
			body.y = toY;
		}
		else
		{
			double ease = 1 - Math.pow(1 - BODY_EASE, ticks);
			body.x += (toX - body.x) * ease;
			body.y += (toY - body.y) * ease;
		}
		int x = (int) Math.round(body.x);
		int y = (int) Math.round(body.y);
		body.object.setLocation(new LocalPoint(x, y, shoal.worldView), shoal.plane);
		// Down on fish divers like fish not swimming deep, under logs across the water.
		double depth = shoal.river.marked(shoal.river.dive, body.x, body.y) ? DIVE_DEPTH : 0;
		body.depth += (depth - body.depth) * Math.min(1, DIVE_EASE * ticks);
		int bob = BODY_BOB * Perspective.SINE[(cycle + body.bobPhase) % BODY_BOB_CYCLES * 2048 / BODY_BOB_CYCLES] >> 16;
		body.object.setZ(waterHeight(x, y, shoal.plane) + BODY_SINK + (int) Math.round(body.depth) + bob);
		body.object.setOrientation((int) Math.round(body.facing) & 2047);
		if (!body.object.isActive())
		{
			body.object.setActive(true);
		}
		return true;
	}

	/**
	 * A fish or body's grow step: growing in since a client tick, or shrinking away since another (0 once gone), else
	 * full size.
	 */
	private static int growStep(int growingSince, int shrinkingSince, int cycle)
	{
		if (shrinkingSince >= 0)
		{
			double through = (cycle - shrinkingSince) / (double) GROW_CYCLES;
			return through >= 1 ? 0 : Math.max(1, (int) Math.ceil(GROW_STEPS * (1 - through)));
		}
		if (growingSince >= 0)
		{
			double through = (cycle - growingSince) / (double) GROW_CYCLES;
			return Math.max(1, Math.min(GROW_STEPS, (int) Math.ceil(GROW_STEPS * through)));
		}
		return GROW_STEPS;
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
				double dx = swimmer.x - circle.x;
				double dy = swimmer.y - circle.y;
				double apartSquared = dx * dx + dy * dy;
				double reach = circle.radius + CIRCLE_CLEARANCE + CLEAR_AHEAD;
				if (apartSquared < reach * reach && apartSquared < nearestApart)
				{
					nearest = circle;
					nearestApart = apartSquared;
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
			turn -= 2 * Math.PI * Math.floor(turn / (2 * Math.PI));
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
		double surging = Perspective.SINE[(cycle + surgePhase) % SURGE_CYCLES * 2048 / SURGE_CYCLES] / 65536.0;
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
			off = offLane(swimmer);
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
				: wrap(swimmer.steer + wrap(toward - swimmer.steer) * Math.min(1, STEER_EASE * ticks));
			toward = swimmer.steer;
		}
		else if (swimmer.circle != null)
		{
			// Swimming in to the circle: curve round to it; once in its lane, follow it closely.
			double from = Double.isNaN(swimmer.steer) ? swimmer.facing : swimmer.steer;
			swimmer.steer = off <= LANE_ARRIVED ? toward
				: wrap(from + wrap(toward - from) * Math.min(1, (JOIN_STEER_EASE + (STEER_EASE - JOIN_STEER_EASE) * near) * ticks));
			toward = swimmer.steer;
		}
		double turn = wrap(toward - swimmer.facing);
		// Swimming in, it turns gently while far and tightens as it nears.
		double most = TURN_RATE * ticks * (swimmer.circle != null ? JOIN_TURN + (1 - JOIN_TURN) * near : 1);
		swimmer.facing = wrap(swimmer.facing + Math.max(-most, Math.min(most, turn)));
		swimmer.headX = Math.cos(swimmer.facing);
		swimmer.headY = Math.sin(swimmer.facing);
		double x = swimmer.x + swimmer.headX * moved;
		double y = swimmer.y + swimmer.headY * moved;
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
		swimmer.wag += 2 * Math.PI * moved / WAG_DISTANCE;
		while (swimmer.wag >= 2 * Math.PI)
		{
			swimmer.wag -= 2 * Math.PI;
		}
		// Deep fish (117 HD) swim deep, shallower near banks, quickly up on fish surfacers (no bobs or dips there). Other
		// fish stay at the surface, but go down DIVE_DEPTH on fish divers, under logs across the water. All come up to
		// circle.
		swimmer.surfacing = river.marked(river.surface, x, y);
		boolean diving = swimmer.deep == 0 && river.marked(river.dive, x, y);
		double depth = swimmer.circle != null ? 0 : diving ? DIVE_DEPTH
			: swimmer.deep == 0 || swimmer.surfacing ? 0 : swimmer.deep * Math.min(1, river.clearanceAt(x, y) / SHALLOW_ROOM);
		// Divers' own pace both ways, for fish not swimming deep.
		double depthEase = swimmer.deep == 0 ? DIVE_EASE : swimmer.surfacing ? SURFACE_EASE : DEPTH_EASE;
		swimmer.depthNow += (depth - swimmer.depthNow) * Math.min(1, depthEase * ticks);
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
		int step = growStep(swimmer.growingSince, swimmer.shrinkingSince, cycle);
		if (step == 0)
		{
			return false;
		}
		swimmer.growingSince = swimmer.growingSince >= 0 && cycle - swimmer.growingSince >= GROW_CYCLES ? -1
			: swimmer.growingSince;
		swimmer.wantStep = step;
		show(shoal, swimmer, ticks, cycle);
		return true;
	}

	/**
	 * An angle brought into -PI to PI.
	 */
	private static double wrap(double angle)
	{
		return angle - 2 * Math.PI * Math.floor((angle + Math.PI) / (2 * Math.PI));
	}

	/**
	 * Whether a fish is within sight: within the fish range along the river, or on a lake, or circling.
	 */
	private static boolean inSight(Shoal shoal, Swimmer swimmer)
	{
		return shoal.lake || swimmer.circle != null || Math.abs(swimmer.s - shoal.playerAt) <= FISH_RANGE * 128.0;
	}

	/**
	 * Draws a fish while it's in sight, growing it in as it comes into sight and shrinking it as it goes, placing it;
	 * one out of sight isn't drawn, and skips the placing (heading, wag, bob, dip, height, model).
	 */
	private void show(Shoal shoal, Swimmer swimmer, int ticks, int cycle)
	{
		double change = ticks / (double) GROW_CYCLES;
		swimmer.inSight = Math.max(0, Math.min(1, swimmer.inSight + (inSight(shoal, swimmer) ? change : -change)));
		boolean drawn = swimmer.inSight > 0;
		if (drawn && !swimmer.shown)
		{
			// Its water height worked out afresh.
			swimmer.surface = Integer.MIN_VALUE;
		}
		if (drawn)
		{
			// No bigger than it's grown into sight.
			swimmer.wantStep = Math.min(swimmer.wantStep, Math.max(1, (int) Math.ceil(GROW_STEPS * swimmer.inSight)));
			place(shoal, swimmer, cycle);
		}
		if (drawn != swimmer.shown)
		{
			swimmer.shown = drawn;
			swimmer.fish.setActive(drawn);
		}
	}

	/**
	 * Whether a fish is swimming down a side channel, for aiming along it. Coming up to a fork, it takes the channel
	 * by its share (a group all the same way); along it, its place on the river is kept matching, and at its end it
	 * carries on down the river.
	 */
	private static boolean onBranch(River river, Swimmer swimmer, Group group, double moved)
	{
		if (swimmer.branch != null && !river.branches.contains(swimmer.branch))
		{
			swimmer.branch = null;
		}
		if (swimmer.branch == null)
		{
			for (Branch branch : river.branches)
			{
				if (swimmer.fork == branch || swimmer.s < branch.fromS - FORK_AHEAD || swimmer.s >= branch.fromS)
				{
					continue;
				}
				swimmer.fork = branch;
				boolean take;
				if (group != null)
				{
					if (group.fork != branch)
					{
						group.fork = branch;
						group.forkTaken = ThreadLocalRandom.current().nextDouble() < branch.share;
					}
					take = group.forkTaken;
				}
				else
				{
					take = ThreadLocalRandom.current().nextDouble() < branch.share;
				}
				if (take)
				{
					swimmer.branch = branch;
					swimmer.branchS = 0;
				}
			}
			if (swimmer.branch == null)
			{
				return false;
			}
		}
		Branch branch = swimmer.branch;
		swimmer.branchS = Math.max(swimmer.branchS, branch.line.nearest(swimmer.x, swimmer.y,
			swimmer.branchS - LOOK_AHEAD, swimmer.branchS + moved + LOOK_AHEAD));
		swimmer.s = Math.max(swimmer.s, branch.toMain(swimmer.branchS));
		if (swimmer.branchS >= branch.line.length - LOOK_AHEAD)
		{
			swimmer.branch = null;
			swimmer.s = Math.max(swimmer.s, branch.toS);
			return false;
		}
		return true;
	}

	// How far before a fork, along the river, a fish decides which way to go.
	private static final double FORK_AHEAD = 128;

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
			lanePoint(circle, swimmer.circleLane, angle, point);
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
			// In a group, steer for its own place along it, so the group keeps its shape.
			River way = river;
			double aim;
			if (onBranch(river, swimmer, group, moved))
			{
				// Down a side channel: along it, keeping its place in its group by the matching place on the river.
				way = swimmer.branch.line;
				double main = Double.isNaN(middleS) ? swimmer.s : Math.max(swimmer.s - LOOK_AHEAD, middleS + swimmer.slotAlong);
				aim = Math.max(swimmer.branchS - LOOK_AHEAD, swimmer.branch.toBranch(main));
			}
			else
			{
				// Track progress from the real position, so bends don't make it turn back.
				swimmer.s = Math.max(swimmer.s, river.nearest(swimmer.x, swimmer.y, swimmer.s - LOOK_AHEAD,
					swimmer.s + moved + LOOK_AHEAD));
				aim = Double.isNaN(middleS) ? swimmer.s : Math.max(swimmer.s - LOOK_AHEAD, middleS + swimmer.slotAlong);
			}
			double wander = WANDER * (group != null ? group.wander : swimmer.wander).at(cycle);
			way.at(aim + LOOK_AHEAD, point);
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
		double dx = targetX - swimmer.x;
		double dy = targetY - swimmer.y;
		if (!schooled || swimmer.circle != null)
		{
			return Math.atan2(dy, dx);
		}
		double apart = Math.sqrt(dx * dx + dy * dy);
		return apart > 1e-9 ? flock(shoal, swimmer, group, dx / apart, dy / apart, middleX, middleY)
			: flock(shoal, swimmer, group, 1, 0, middleX, middleY);
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
	 * near enough, and back towards the group's middle when strayed; from the way to its target (a unit direction).
	 * Returns the heading to steer for.
	 */
	private static double flock(Shoal shoal, Swimmer swimmer, Group group, double towardX, double towardY,
		double middleX, double middleY)
	{
		double x = towardX;
		double y = towardY;
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
				double dx = member.x - swimmer.x;
				double dy = member.y - swimmer.y;
				if (member != swimmer && dx * dx + dy * dy < ALIGN_RANGE * ALIGN_RANGE)
				{
					alignX += member.headX;
					alignY += member.headY;
				}
			}
			double align = Math.sqrt(alignX * alignX + alignY * alignY);
			if (align > 0)
			{
				x += ALIGN * alignX / align;
				y += ALIGN * alignY / align;
			}
			double dx = middleX - swimmer.x;
			double dy = middleY - swimmer.y;
			double range = COHESION_RANGE * swimmer.scale;
			double apartSquared = dx * dx + dy * dy;
			if (apartSquared > range * range)
			{
				double apart = Math.sqrt(apartSquared);
				x += COHESION * dx / apart;
				y += COHESION * dy / apart;
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
		// Game orientation (0 south, 512 west, 1024 north) turns the other way from facing, a quarter turn behind.
		int heading = (int) Math.round(-swimmer.facing * 1024 / Math.PI) - 512 & 2047;
		double pace = Math.min(1, swimmer.swimming / REFERENCE_SPEED);
		double wag = Perspective.SINE[(int) (swimmer.wag * 1024 / Math.PI) & 2047] / 65536.0;
		int swung = heading + (int) Math.round(WAG * look.wag / 100.0 * pace * pace * wag) & 2047;
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
			// The drop along its heading (heights grow downwards): nose down going down.
			int dx = (int) Math.round(SLOPE_LOOK * Math.cos(swimmer.facing));
			int dy = (int) Math.round(SLOPE_LOOK * Math.sin(swimmer.facing));
			int drop = waterHeight(x + dx, y + dy, shoal.plane) - waterHeight(x - dx, y - dy, shoal.plane);
			double angle = Math.toDegrees(Math.atan2(drop, 2 * SLOPE_LOOK));
			swimmer.slopeWant = -Math.signum(angle) * Math.min(SLOPE_MOST, Math.max(0, Math.abs(angle) - SLOPE_MIN));
		}
		swimmer.slope += (swimmer.slopeWant - swimmer.slope) * SLOPE_EASE;
		swimmer.fish.setZ(swimmer.surface + look.sink + bobbed
			+ (int) Math.round(look.dipDepth * dip * dip + swimmer.depthNow));
		// Tip with the water's slope, bob and dip; only at full size.
		double tip = swimmer.slope + look.tip / 100.0 * ((look.rise > 0 && bobAt >= 0 ? -BOB_PITCH * Perspective.SINE[bobAt] / 65536 : 0)
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
	 * Takes a shoal's fish and dead body out of the world.
	 */
	private static void hide(Shoal shoal)
	{
		shoal.fish.forEach(swimmer -> swimmer.fish.setActive(false));
		shoal.bodies.forEach(body -> body.object.setActive(false));
	}

	/**
	 * Water height at a place, ignoring bridges above it.
	 */
	int waterHeight(int x, int y, int plane)
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
	 * A circle lane's point at an angle, from the lane's shape: its radius there, blended between the angles kept.
	 */
	private static void lanePoint(Circle circle, int lane, double angle, double[] into)
	{
		double out = laneRadius(circle, lane, angle);
		into[0] = circle.x + out * Math.cos(angle);
		into[1] = circle.y + out * Math.sin(angle);
	}

	/**
	 * A circle lane's radius at an angle, blended between the angles its shape is kept at.
	 */
	private static double laneRadius(Circle circle, int lane, double angle)
	{
		double at = angle / (2 * Math.PI) * LANE_ANGLES;
		int below = (int) Math.floor(at);
		double t = at - below;
		double[] radii = circle.pulled[lane];
		int from = Math.floorMod(below, LANE_ANGLES);
		int to = (from + 1) % LANE_ANGLES;
		return radii[from] + (radii[to] - radii[from]) * t;
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
		drawRanges = config.debugRiverDrawRange();
		drawBanks = config.debugRiverDrawBanks();
		drawPath = config.debugRiverDrawPath();
		drawShade = config.debugRiverDrawShade();
		drawSpots = config.debugRiverDrawRings();
		drawFish = config.debugRiverDrawFish();
		drawSchools = config.debugRiverDrawSchools();
		drawPicked = config.debugRiverDrawPoints();
		WEIGHTS.put(ItemID.RAW_TROUT, config.debugRiverShareTrout());
		WEIGHTS.put(ItemID.RAW_SALMON, config.debugRiverShareSalmon());
		WEIGHTS.put(ItemID.RAW_PIKE, config.debugRiverSharePike());
		RAINBOW_SHARE = config.debugRiverShareRainbow() / 100.0;
		BODY_EVERY = config.debugRiverBodyMinutes() * 60 * 50.0;
		BODY_TEST = config.debugRiverBodyTest();
		BODY_FEET = config.debugRiverBodyFeet() * 2048 / 360;
		BOB_CYCLES = config.debugRiverBobCycles();
		BOB_REST_CYCLES = config.debugRiverBobRestCycles();
		DEEP_LEAST = config.debugRiverDeepLeast();
		DEEP_MOST = config.debugRiverDeepMost();
		DIVE_DEPTH = config.debugRiverDiveDepth();
		DIVE_EASE = config.debugRiverDiveSpeed() / 100.0;
		CIRCLE_LANES = config.debugRiverCircleLanes();
		CIRCLE_LANE_SPACING = config.debugRiverCircleLaneSpacing();
		CIRCLE_SIZE = config.debugRiverCircleSize();
		CIRCLE_MOST = config.debugRiverCircleMost();
		INNER_LANE_SPEED = config.debugRiverInnerLaneSpeed() / 100.0;
		CIRCLE_CLEARANCE = config.debugRiverCircleClearance();
		SCATTER_RATE = 1.0 / (config.debugRiverScatterSeconds() * 50.0);
		LAKE_JOIN_CHANCE = config.debugRiverLakeJoinChance();
		FISH_RANGE = config.debugRiverFishRange();
		LOAD_MORE = config.debugRiverLoadMore();
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
	 * TEMPORARY: the points of the route being picked so far, drawn while picking.
	 */
	void setPicking(List<WorldPoint> points)
	{
		picking = points;
	}

	/**
	 * After a map load: keeps each river and lake, and its fish, moving them to the new scene coordinates; one whose
	 * map no longer fits in the loaded scene is mapped again for what's loaded, keeping the fish still on it.
	 */
	void reload(WorldView view)
	{
		long started = System.nanoTime();
		reloadTimed(view);
		time("Map load, moving rivers", started);
	}

	private void reloadTimed(WorldView view)
	{
		int cycle = client.getGameCycle();
		unmappable.clear();
		// Rivers and lakes being mapped start again, in the new scene's coordinates.
		for (Opening opening : openings)
		{
			opening.mapping = new Mapping(view, opening.mapping.route, opening.mapping.plane);
			opening.mapped = false;
		}
		for (Iterator<Shoal> all = shoals.iterator(); all.hasNext(); )
		{
			Shoal shoal = all.next();
			River river = shoal.river;
			shoal.remapping = null;
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
				hide(shoal);
				all.remove();
				continue;
			}
			for (Swimmer swimmer : shoal.fish)
			{
				swimmer.fish.setActive(false);
				swimmer.fish.setLocation(new LocalPoint((int) swimmer.x, (int) swimmer.y, shoal.worldView), shoal.plane);
				if (swimmer.shown)
				{
					place(shoal, swimmer, cycle);
					swimmer.fish.setActive(true);
				}
			}
			// Placed again on their next move.
			shoal.bodies.forEach(body -> body.object.setActive(false));
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
		// Replaces any being made a step a tick.
		shoal.remapping = null;
		Mapping mapping = new Mapping(view, shoal.route, shoal.plane);
		if (mapping.all() == null)
		{
			return false;
		}
		swapIn(shoal, mapping);
		return true;
	}

	/**
	 * Puts a shoal on its new map, its fish carrying on where they are: those off it go, the rest pick up their place
	 * along the new path, and the circles are placed again.
	 */
	private void swapIn(Shoal shoal, Mapping mapping)
	{
		River river = mapping.river;
		List<double[]> spawns = mapping.spawns;
		// Where each body is now, on the old path.
		List<double[]> bodiesAt = new ArrayList<>();
		for (Body body : shoal.bodies)
		{
			shoal.river.at(body.s, point);
			bodiesAt.add(new double[]{point[0], point[1]});
		}
		List<Branch> oldBranches = shoal.river.branches;
		shoal.river = river;
		shoal.travelling = fishCount(shoal.route, river, shoal.lake);
		shoal.mappedAround = playerTile();
		if (shoal.lake)
		{
			shoal.spawns = spawns.toArray(new double[0][]);
		}
		// Bodies still on the water keep their place on the new path; the rest go.
		for (int k = shoal.bodies.size() - 1; k >= 0; k--)
		{
			Body body = shoal.bodies.get(k);
			double[] at = bodiesAt.get(k);
			if (!shoal.lake && river.isWater(at[0], at[1]))
			{
				body.s = river.nearest(at[0], at[1]);
			}
			else
			{
				body.object.setActive(false);
				shoal.bodies.remove(k);
			}
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
				// On a side channel: the same one, mapped again, if it's still in.
				int index = swimmer.branch == null ? -1 : oldBranches.indexOf(swimmer.branch);
				swimmer.branch = index >= 0 && index < river.branches.size() ? river.branches.get(index) : null;
				if (swimmer.branch != null)
				{
					swimmer.branchS = swimmer.branch.line.nearest(swimmer.x, swimmer.y);
					swimmer.s = swimmer.branch.toMain(swimmer.branchS);
				}
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
			shoal.spacing = riverSpacing(shoal.route);
			updateWindow(shoal);
			// Queued places are along the old path.
			shoal.toFill.clear();
			fillGaps(shoal, ThreadLocalRandom.current());
		}
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
		List<River> ways = new ArrayList<>();
		ways.add(river);
		river.branches.forEach(branch -> ways.add(branch.line));
		for (River way : ways)
		{
			way.x0 += dx;
			way.y0 += dy;
			for (int k = 0; way.pathX != null && k < way.pathX.length; k++)
			{
				way.pathX[k] += dx;
				way.pathY[k] += dy;
			}
		}
		// Worked out again when next drawn.
		river.banks = null;
		river.roomEdge = null;
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
		for (Body body : shoal.bodies)
		{
			body.x += dx;
			body.y += dy;
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
			int most = shoal.lake ? shoal.travelling : windowFish(shoal);
			rows[k] = new String[]{label(shoal.route), shoal.fish.size() + " / " + most,
				String.valueOf(shoal.fish.size())};
		}
		return rows;
	}

	/**
	 * TEMPORARY: a river or lake's name in the debug panel, from its baked file's name if it has one.
	 */
	private String label(WorldPoint[] route)
	{
		String name = NAMES.get(route[0]);
		if (name == null)
		{
			return (isLake(route) ? "Lake " : "River ") + route[0].getX() + "," + route[0].getY();
		}
		name = name.replace('-', ' ') + (isLake(route) && !name.startsWith("lake") ? " lake" : "");
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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
	 * TEMPORARY: the last river or lake started, for the debug panel: a heading (its name, all steps' ms), then each
	 * step; empty if none yet.
	 */
	List<String[]> lastLoad()
	{
		List<String[]> rows = new ArrayList<>();
		if (lastLoad == null)
		{
			return rows;
		}
		double[] steps = lastLoadSteps;
		rows.add(new String[]{"Last load: " + lastLoad, String.format("%.2f", Arrays.stream(steps).sum())});
		for (int k = 0; k < steps.length; k++)
		{
			rows.add(new String[]{"  " + STEP_NAMES[k], String.format("%.2f", steps[k])});
		}
		rows.add(new String[]{"  over client ticks", String.valueOf(MAP_STEPS + 1)});
		return rows;
	}

	/**
	 * TEMPORARY: the mapping warm-up's time at plugin start, ms; and the memory the river and lake maps hold, MB.
	 */
	double warmUpMs()
	{
		return warmUpMs;
	}

	double mapsMb()
	{
		long bytes = 0;
		for (Shoal shoal : shoals)
		{
			// Bank distances (2 bytes a cell), and surfacer and diver cells (1 each) if any.
			int cells = shoal.river.width * shoal.river.height;
			bytes += 2L * cells + (shoal.river.surface != null ? cells : 0) + (shoal.river.dive != null ? cells : 0);
		}
		return bytes / 1e6;
	}

	/**
	 * TEMPORARY: the latest timings, newest first, then the slowest so far, for the debug panel.
	 */
	List<String[]> timings()
	{
		List<String[]> rows = new ArrayList<>(timings);
		if (slowestWhat != null)
		{
			rows.add(new String[]{"Worst: " + slowestWhat, slowest + " ms"});
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
		toAttach.clear();
		// Ring settings may have changed.
		ringPlaces.clear();
		shoals.forEach(RiverSpotFish::hide);
		shoals.clear();
	}

	// Debug: places waiting to go on screen together (projecting one at a time works the camera's turn out for
	// each), local x, y and height, and where they land.
	private float[] drawX = new float[256];
	private float[] drawY = new float[256];
	private float[] drawZ = new float[256];
	private int[] screenX = new int[256];
	private int[] screenY = new int[256];
	private int marked;

	/**
	 * Debug: marks a place on the water to draw, at the water's own height, so it isn't lifted onto bridges.
	 */
	private void mark(Shoal shoal, double x, double y)
	{
		markAt(x, y, waterHeight((int) x, (int) y, shoal.plane));
	}

	private void markAt(double x, double y, int height)
	{
		if (marked == drawX.length)
		{
			int size = marked * 2;
			drawX = Arrays.copyOf(drawX, size);
			drawY = Arrays.copyOf(drawY, size);
			drawZ = Arrays.copyOf(drawZ, size);
			screenX = Arrays.copyOf(screenX, size);
			screenY = Arrays.copyOf(screenY, size);
		}
		drawX[marked] = (float) x;
		drawY[marked] = (float) y;
		drawZ[marked] = height;
		marked++;
	}

	/**
	 * Debug: puts the marked places on screen, all at once; those behind the camera get Integer.MIN_VALUE.
	 */
	private void project(WorldView view)
	{
		Perspective.modelToCanvas(client, view, marked, 0, 0, 0, 0, drawX, drawY, drawZ, screenX, screenY);
	}

	/**
	 * Debug: draws the marked places joined up, skipping any behind the camera, and clears them.
	 */
	private void line(Graphics2D graphics, WorldView view)
	{
		project(view);
		boolean whole = true;
		for (int k = 0; k < marked && whole; k++)
		{
			whole = screenX[k] != Integer.MIN_VALUE;
		}
		if (whole)
		{
			graphics.drawPolyline(screenX, screenY, marked);
		}
		else
		{
			for (int k = 1; k < marked; k++)
			{
				if (screenX[k - 1] != Integer.MIN_VALUE && screenX[k] != Integer.MIN_VALUE)
				{
					graphics.drawLine(screenX[k - 1], screenY[k - 1], screenX[k], screenY[k]);
				}
			}
		}
		marked = 0;
	}

	/**
	 * Debug: a line across the river at a distance along the path.
	 */
	private void acrossLine(Graphics2D graphics, WorldView view, Shoal shoal, double s, Color colour)
	{
		shoal.river.at(s, point);
		double left = point[4] + BANK_GAP;
		double right = point[5] + BANK_GAP;
		mark(shoal, point[0] - point[3] * left, point[1] + point[2] * left);
		mark(shoal, point[0] + point[3] * right, point[1] - point[2] * right);
		graphics.setColor(colour);
		line(graphics, view);
	}

	// TEMPORARY: which parts of the debug drawing to show (toggles).
	private static boolean drawRanges = true;
	private static boolean drawShade = true;
	private static boolean drawBanks = true;
	private static boolean drawPath = true;
	private static boolean drawSpots = true;
	private static boolean drawFish = true;
	private static boolean drawSchools = true;
	private static boolean drawPicked = true;
	// Debug: length of a school's heading arrow, local units.
	private static final double SCHOOL_ARROW = 160;
	// Debug: room drawn round each fish in a school's outline, local units.
	private static final double SCHOOL_PAD = 24;
	// Debug: forks' picked tiles and paths, light blue.
	private static final Color FORK_COLOUR = new Color(120, 200, 255);
	// Debug: the room's hatching, teal lines going up to the right.
	private static final TexturePaint HATCH = hatch();
	// Debug: cos and sin of the 8 points round each fish in a school's outline.
	private static final double[][] PAD_WAYS = ways(8);

	/**
	 * Debug: a small tile of diagonal teal lines, repeated to hatch the room.
	 */
	private static TexturePaint hatch()
	{
		BufferedImage tile = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int k = 0; k < 8; k++)
		{
			tile.setRGB(k, 7 - k, new Color(0, 160, 160, 110).getRGB());
		}
		return new TexturePaint(tile, new Rectangle(0, 0, 8, 8));
	}

	/**
	 * Debug: a route's points as tile outlines, numbered in order, the first marked start and, when finished, the
	 * last marked end.
	 */
	private void routePoints(Graphics2D graphics, WorldView view, WorldPoint[] points, Color colour, boolean finished)
	{
		routePoints(graphics, view, points, colour, finished, "", "");
	}

	/**
	 * Debug: as routePoints, the first and last labelled with a word before start and end, and a note after start.
	 */
	private void routePoints(Graphics2D graphics, WorldView view, WorldPoint[] points, Color colour, boolean finished,
		String kind, String startNote)
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
			String text = (k + 1) + (k == 0 ? kind + " start" + startNote : finished && k == points.length - 1 ? kind + " end" : "");
			Point label = Perspective.getCanvasTextLocation(client, graphics, at, text, 0);
			if (label != null)
			{
				graphics.drawString(text, label.getX(), label.getY());
			}
		}
	}

	/**
	 * Debug: a school's bounds, the outline round its fish with a little room.
	 */
	private void outline(Graphics2D graphics, WorldView view, Shoal shoal, Group group)
	{
		List<double[]> points = new ArrayList<>();
		for (Swimmer member : group.members)
		{
			for (double[] way : PAD_WAYS)
			{
				points.add(new double[]{member.x + SCHOOL_PAD * way[0], member.y + SCHOOL_PAD * way[1]});
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
		// Closed: back to the first point.
		for (int n = 0; n <= k; n++)
		{
			mark(shoal, hull[n % k][0], hull[n % k][1]);
		}
		line(graphics, view);
	}

	/**
	 * Debug: marks an arrow on the water between two places, its head this long (local units): the tail, tip and the
	 * head's two sides. Drawn by drawArrow once projected.
	 */
	private void markArrow(Shoal shoal, double fromX, double fromY, double toX, double toY, double head)
	{
		double length = Math.max(1e-6, Math.hypot(toX - fromX, toY - fromY));
		double ux = (toX - fromX) / length;
		double uy = (toY - fromY) / length;
		double back = Math.min(head, length / 2);
		double side = back * 0.6;
		mark(shoal, fromX, fromY);
		mark(shoal, toX, toY);
		mark(shoal, toX - ux * back - uy * side, toY - uy * back + ux * side);
		mark(shoal, toX - ux * back + uy * side, toY - uy * back - ux * side);
	}

	/**
	 * Debug: draws the arrow marked from this place on, once projected.
	 */
	private void drawArrow(Graphics2D graphics, int at)
	{
		for (int k = at; k < at + 4; k++)
		{
			if (screenX[k] == Integer.MIN_VALUE)
			{
				return;
			}
		}
		graphics.drawLine(screenX[at], screenY[at], screenX[at + 1], screenY[at + 1]);
		graphics.drawLine(screenX[at + 1], screenY[at + 1], screenX[at + 2], screenY[at + 2]);
		graphics.drawLine(screenX[at + 1], screenY[at + 1], screenX[at + 3], screenY[at + 3]);
	}

	/**
	 * Debug: ranges, banks, path and room, spots, fish steering, schools and picked points, each part shown by its
	 * toggle.
	 */
	void drawDebug(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		Player me = client.getLocalPlayer();
		if (view == null || me == null)
		{
			return;
		}
		LocalPoint meAt = me.getLocalLocation();
		if (drawRanges && meAt != null)
		{
			// The square rivers are mapped within round the player, as clip() cuts them: tile middles, so drawn half a
			// tile out.
			int reach = loadRange() + MAP_MORE;
			int lowX = Math.max(ROUTE_MARGIN, meAt.getSceneX() - reach);
			int lowY = Math.max(ROUTE_MARGIN, meAt.getSceneY() - reach);
			int highX = Math.min(view.getSizeX() - 1 - ROUTE_MARGIN, meAt.getSceneX() + reach);
			int highY = Math.min(view.getSizeY() - 1 - ROUTE_MARGIN, meAt.getSceneY() + reach);
			int[][] corners = {{lowX * 128, lowY * 128}, {highX * 128 + 128, lowY * 128},
				{highX * 128 + 128, highY * 128 + 128}, {lowX * 128, highY * 128 + 128}, {lowX * 128, lowY * 128}};
			int plane = me.getWorldLocation().getPlane();
			for (int side = 0; side < 4; side++)
			{
				int[] from = corners[side];
				int[] to = corners[side + 1];
				int steps = Math.max(1, (Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1])) / 128);
				for (int k = side == 0 ? 0 : 1; k <= steps; k++)
				{
					int x = from[0] + (to[0] - from[0]) * k / steps;
					int y = from[1] + (to[1] - from[1]) * k / steps;
					markAt(x, y, Perspective.getTileHeight(client, new LocalPoint(x, y, view.getId()), plane));
				}
			}
			graphics.setColor(Color.CYAN);
			line(graphics, view);
		}
		if (drawPicked)
		{
			for (WorldPoint[] route : routes)
			{
				routePoints(graphics, view, route, Color.YELLOW, true);
			}
			routePoints(graphics, view, picking.toArray(new WorldPoint[0]), Color.MAGENTA, false);
			// Each baked river's forks' picked tiles.
			for (WorldPoint[] route : routes)
			{
				List<int[]> forks = bakedBranches(route[0]);
				List<Integer> shares = bakedBranchShares(route[0]);
				for (int f = 0; f < forks.size(); f++)
				{
					int[] fork = forks.get(f);
					WorldPoint[] tiles = new WorldPoint[fork.length / 2];
					for (int k = 0; k < tiles.length; k++)
					{
						tiles[k] = new WorldPoint(fork[2 * k], fork[2 * k + 1], route[0].getPlane());
					}
					int share = f < shares.size() ? shares.get(f) : -1;
					routePoints(graphics, view, tiles, FORK_COLOUR, true, " fork",
						share >= 0 ? " " + share + "%" : " by width");
				}
			}
		}
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		for (Shoal shoal : shoals)
		{
			River river = shoal.river;
			if (drawBanks)
			{
				// Only those on screen are drawn.
				int[] banks = banks(river);
				for (int k = 0; k + 1 < banks.length; k += 2)
				{
					mark(shoal, banks[k], banks[k + 1]);
				}
				project(view);
				graphics.setColor(new Color(255, 160, 0, 160));
				for (int k = 0; k < marked; k++)
				{
					if (screenX[k] >= 1 && screenY[k] >= 1 && screenX[k] < width - 1 && screenY[k] < height - 1)
					{
						graphics.fillRect(screenX[k] - 1, screenY[k] - 1, 3, 3);
					}
				}
				marked = 0;
			}
			if (shoal.lake && drawPicked)
			{
				// Spawn points.
				for (double[] spawn : shoal.spawns)
				{
					mark(shoal, spawn[0], spawn[1]);
				}
				project(view);
				graphics.setColor(Color.YELLOW);
				for (int k = 0; k < marked; k++)
				{
					if (screenX[k] != Integer.MIN_VALUE)
					{
						graphics.drawOval(screenX[k] - 6, screenY[k] - 6, 12, 12);
					}
				}
				marked = 0;
			}
			if (drawPath && river.pathX != null)
			{
				for (int k = 0; k < river.pathX.length; k++)
				{
					mark(shoal, river.pathX[k], river.pathY[k]);
				}
				int end = marked - 1;
				project(view);
				graphics.setColor(Color.CYAN);
				if (screenX[end] != Integer.MIN_VALUE)
				{
					graphics.fillOval(screenX[end] - 4, screenY[end] - 4, 8, 8);
				}
				line(graphics, view);
				graphics.setColor(FORK_COLOUR);
				for (Branch branch : river.branches)
				{
					for (int k = 0; k < branch.line.pathX.length; k++)
					{
						mark(shoal, branch.line.pathX[k], branch.line.pathY[k]);
					}
					line(graphics, view);
				}
			}
			// The room's edge: where the water is BANK_GAP from the bank.
			if (drawPath && river.pathX != null)
			{
				double[] edge = roomEdge(river);
				for (int k = 0; k + 1 < edge.length; k += 2)
				{
					mark(shoal, edge[k], edge[k + 1]);
				}
				project(view);
				graphics.setColor(new Color(0, 160, 160, 160));
				for (int k = 0; k + 1 < marked; k += 2)
				{
					if (screenX[k] != Integer.MIN_VALUE && screenX[k + 1] != Integer.MIN_VALUE)
					{
						graphics.drawLine(screenX[k], screenY[k], screenX[k + 1], screenY[k + 1]);
					}
				}
				marked = 0;
			}
			// The room of the river and its side channels, hatched.
			if (drawShade && river.pathX != null)
			{
				shade(graphics, view, shoal, river);
				for (Branch branch : river.branches)
				{
					shade(graphics, view, shoal, branch.line);
				}
			}
			if (drawRanges)
			{
				// The tiles mapped for it: the outline of its box.
				double right = river.x0 + river.width * CELL;
				double top = river.y0 + river.height * CELL;
				double[][] corners = {{river.x0, river.y0}, {right, river.y0}, {right, top}, {river.x0, top},
					{river.x0, river.y0}};
				for (int side = 0; side < 4; side++)
				{
					double[] from = corners[side];
					double[] to = corners[side + 1];
					int steps = (int) Math.max(1, (Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1])) / 128);
					for (int k = side == 0 ? 0 : 1; k <= steps; k++)
					{
						mark(shoal, from[0] + (to[0] - from[0]) * k / steps, from[1] + (to[1] - from[1]) * k / steps);
					}
				}
				graphics.setColor(new Color(180, 120, 255));
				line(graphics, view);
				// The stretch with fish round the player, and where they're drawn.
				if (!shoal.lake)
				{
					acrossLine(graphics, view, shoal, shoal.windowFrom, Color.ORANGE);
					acrossLine(graphics, view, shoal, shoal.windowTo, Color.ORANGE);
					acrossLine(graphics, view, shoal, Math.max(0, shoal.playerAt - FISH_RANGE * 128.0), Color.YELLOW);
					acrossLine(graphics, view, shoal, Math.min(river.length, shoal.playerAt + FISH_RANGE * 128.0),
						Color.YELLOW);
				}
			}
			for (Circle circle : shoal.circles.values())
			{
				if (!drawSpots)
				{
					break;
				}
				// Where passing fish decide whether to join, and the ring's own line.
				if (!shoal.lake)
				{
					acrossLine(graphics, view, shoal, circle.along - JOIN_BEFORE, Color.MAGENTA);
					acrossLine(graphics, view, shoal, circle.along, Color.GREEN);
				}
				graphics.setColor(Color.GREEN);
				for (int lane = 0; lane < circle.lanes.length; lane++)
				{
					for (int a = 0; a <= LANE_ANGLES; a++)
					{
						double[] way = LANE_WAYS[a % LANE_ANGLES];
						double out = circle.pulled[lane][a % LANE_ANGLES];
						mark(shoal, circle.x + out * way[0], circle.y + out * way[1]);
					}
					line(graphics, view);
				}
				// Avoided area.
				double out = circle.radius + CIRCLE_CLEARANCE;
				for (int a = 0; a <= LANE_ANGLES; a++)
				{
					double[] way = LANE_WAYS[a % LANE_ANGLES];
					mark(shoal, circle.x + out * way[0], circle.y + out * way[1]);
				}
				graphics.setColor(Color.RED);
				line(graphics, view);
				mark(shoal, circle.x, circle.y);
				project(view);
				marked = 0;
				graphics.setColor(Color.GREEN);
				if (screenX[0] != Integer.MIN_VALUE)
				{
					graphics.drawLine(screenX[0] - 5, screenY[0], screenX[0] + 5, screenY[0]);
					graphics.drawLine(screenX[0], screenY[0] - 5, screenX[0], screenY[0] + 5);
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
			// Each dead body: lining up with the river, and how much, or spinning.
			for (Body body : drawFish ? shoal.bodies : List.<Body>of())
			{
				if (Double.isNaN(body.x))
				{
					continue;
				}
				LocalPoint at = new LocalPoint((int) body.x, (int) body.y, shoal.worldView);
				Point label = Perspective.localToCanvas(client, at, shoal.plane, 150);
				if (label != null)
				{
					String text = (body.narrow >= BODY_LINE_UP ? "Lining up " : "Spinning ") + Math.round(body.narrow * 100) + "%";
					graphics.setColor(Color.WHITE);
					graphics.drawString(text, label.getX() - graphics.getFontMetrics().stringWidth(text) / 2, label.getY());
				}
			}
			// Steering arrows: white swimming, green circling, yellow growing (in, or into sight), red shrinking (away,
			// or out of sight), grey not drawn.
			if (drawFish)
			{
				for (Swimmer swimmer : shoal.fish)
				{
					markArrow(shoal, swimmer.x, swimmer.y, swimmer.targetX, swimmer.targetY, 16);
				}
				project(view);
				for (int n = 0; n < shoal.fish.size(); n++)
				{
					Swimmer swimmer = shoal.fish.get(n);
					graphics.setColor(!swimmer.shown ? Color.GRAY
						: swimmer.shrinkingSince >= 0 || !inSight(shoal, swimmer) ? Color.RED
						: swimmer.growingSince >= 0 || swimmer.inSight < 1 ? Color.YELLOW
						: swimmer.circle != null ? Color.GREEN : Color.WHITE);
					drawArrow(graphics, n * 4);
				}
				marked = 0;
			}
			// Schools, blue: an outline round each, and an arrow from its middle along its fish's average heading.
			if (!drawSchools)
			{
				continue;
			}
			graphics.setColor(new Color(60, 140, 255));
			Set<Group> schools = new HashSet<>();
			for (Swimmer swimmer : shoal.fish)
			{
				Group group = swimmer.group;
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
					x += member.x;
					y += member.y;
					hx += member.headX;
					hy += member.headY;
				}
				x /= group.members.size();
				y /= group.members.size();
				double length = Math.hypot(hx, hy);
				if (length < 0.01)
				{
					continue;
				}
				outline(graphics, view, shoal, group);
				markArrow(shoal, x, y, x + hx / length * SCHOOL_ARROW, y + hy / length * SCHOOL_ARROW, 32);
				project(view);
				marked = 0;
				drawArrow(graphics, 0);
				if (screenX[0] != Integer.MIN_VALUE)
				{
					graphics.fillOval(screenX[0] - 3, screenY[0] - 3, 7, 7);
				}
			}
		}
	}
}
