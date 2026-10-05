package com.trawlingplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Fish each fishing spot at sea gives, a few of them circling the spot's middle together, as a shoal does. Only
 * something to look at: they have no clickbox and no menu option, and the spot itself is left as the game draws it.
 * They are shown while their spot is in sight and taken away as the spot goes.
 */
final class SeaSpotFish
{
	// The fish each spot shows, each of its fish picked at random from these: every fish it gives.
	private static final Map<Integer, int[]> SPOT_FISH = Map.of(
		NpcID.FISHING_BOAT_SALTFISH,
		new int[]{ItemID.RAW_SHRIMP, ItemID.RAW_ANCHOVIES, ItemID.RAW_SARDINE, ItemID.RAW_HERRING},
		NpcID.FISHING_BOAT_MEMBERFISH,
		new int[]{ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS, ItemID.RAW_SHARK},
		NpcID.FISHING_BOAT_RAREFISH, new int[]{ItemID.RAW_LOBSTER, ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH},
		NpcID.FISHING_BOAT_KARAMBWANFISH, new int[]{ItemID.TBWT_RAW_KARAMBWAN},
		NpcID.FISHING_BOAT_PISCARILIUSFISH, new int[]{ItemID.RAW_ANGLERFISH},
		NpcID.FISHING_BOAT_MONKFISH, new int[]{ItemID.RAW_MONKFISH});
	// How likely each of a spot's fish is, in the same order, out of their total, for the spots that aren't evenly
	// shared out.
	private static final Map<Integer, int[]> SPOT_FISH_ODDS = Map.of(
		NpcID.FISHING_BOAT_SALTFISH, new int[]{8, 8, 42, 42},
		NpcID.FISHING_BOAT_RAREFISH, new int[]{30, 85, 85});
	// How many of each of a spot's fish, in the same order, it always has at least, for the spots that keep some.
	private static final Map<Integer, int[]> SPOT_FISH_LEAST = Map.of(
		NpcID.FISHING_BOAT_RAREFISH, new int[]{5, 5, 5});

	// How big the shoal is: how far its outermost lane is from the spot's middle, in local units (128 to a tile),
	// well inside the spot's 5 by 5 tiles; and how far apart its lanes are, which stays the same whatever its size,
	// the innermost lane coming in or going out to fit. SCALE sizes the speeds and the gaps the fish keep, against
	// the size they were first set for.
	private static final double SHOAL_RADIUS = 224;
	private static final double LANE_SPACING = 45;
	private static final double SCALE = 256.0 / 352;
	// How many lanes the fish swim round each spot in, so none from different lanes can swim into each other, and
	// how much of a lane each fish takes, in merging gaps, so a lane holds more the bigger round it is, with room
	// to spare for others to join it; how far out the outermost and innermost lanes are, the rest evenly between;
	// and how far either way a lane may stray from its even place, which keeps a clear gap to the next.
	private static final int LANES = 5;
	private static final double LANE_ROOM = 1.5;
	// How many fish each spot has, shared out among its lanes.
	private static final int FISH_PER_SPOT = 8;
	private static final double LANE_STRAY = 4 * SCALE;

	/**
	 * How crowded one spot's shoal is: how many lanes it has, how many fish, how far apart its lanes are, and how far
	 * out its outermost lane is, in local units; and, for the kinds kept out, how many lanes its outer ring has, how
	 * far outside the shoal's outermost lane its first is, and how far apart they are, in local units. The gaps its
	 * fish keep from each other, and how far they make room, are in proportion to its lane spacing against
	 * LANE_SPACING, so small fish in narrow lanes keep small gaps.
	 */
	private static final class Crowd
	{
		private final int lanes;
		private final int count;
		private final double spacing;
		private final double radius;
		private final int outerLanes;
		private final double outerGap;
		private final double outerSpacing;

		private Crowd(int[] values)
		{
			lanes = values[0];
			count = values[1];
			spacing = values[2];
			radius = values[3];
			outerLanes = values[4];
			outerGap = values[5];
			outerSpacing = values[6];
		}
	}

	// Each spot's crowd, by its NPC, the rest having CROWD.
	// In the order of its values: lanes, count, spacing, radius, outerLanes, outerGap, outerSpacing.
	private static final Crowd CROWD = new Crowd(
		new int[]{LANES, FISH_PER_SPOT, (int) LANE_SPACING, (int) SHOAL_RADIUS, 1, 64, 64});
	private static final Map<Integer, Crowd> CROWDS = Map.of(
		NpcID.FISHING_BOAT_SALTFISH, new Crowd(new int[]{10, 25, 45, 270, 1, 64, 64}),
		NpcID.FISHING_BOAT_MEMBERFISH, new Crowd(new int[]{6, 20, 45, 270, 2, -44, 190}),
		NpcID.FISHING_BOAT_PISCARILIUSFISH, new Crowd(new int[]{6, 13, 44, 275, 1, 64, 64}),
		NpcID.FISHING_BOAT_MONKFISH, new Crowd(new int[]{5, 14, 57, 308, 1, 64, 64}),
		NpcID.FISHING_BOAT_RAREFISH, new Crowd(new int[]{7, 23, 38, 294, 1, 64, 64}),
		NpcID.FISHING_BOAT_KARAMBWANFISH, new Crowd(new int[]{5, 12, 46, 287, 1, 64, 64}));
	// How far each fish sways in and out from its lane as it swims, in local units at most, from two slow swings
	// over times picked at random between these, in client ticks, so it wanders a little without any pattern.
	private static final double SWAY = 10 * SCALE;
	private static final int MIN_SWAY_CYCLES = 120;
	private static final int MAX_SWAY_CYCLES = 360;
	// How far, at most, a fish turns in or out from the way round as it moves in or out, in radians (0.35 is about 20
	// degrees), and how much of the way to that it turns each time it is placed, so it turns smoothly.
	private static final double MAX_DRIFT = 0.35;
	private static final double DRIFT_EASE = 0.1;
	// How fast the fish swim, in local units a client tick (20 ms): REFERENCE_SPEED out at the middle lane, slower
	// further in and faster further out, from INNER_SPEED to OUTER_SPEED of it; how far, in percent, each fish's own
	// speed may differ from that; and how much faster or slower each surges as it swims, as a share of its speed,
	// from INNER_SURGE in the innermost lane to OUTER_SURGE in the outermost, over a time picked at random between
	// these, in client ticks.
	private static final double REFERENCE_SPEED = 2 * Math.PI / 1.4 * SCALE;
	private static final double INNER_SPEED = 0.7;
	// The innermost lane alone swims slower than that, at this share of the middle lane's speed.
	private static final double INNERMOST_SPEED = 0.4;
	private static final double OUTER_SPEED = 1.3;
	private static final int SPEED_SPREAD = 8;
	private static final double INNER_SURGE = 0.1;
	private static final double OUTER_SURGE = 0.3;
	private static final int MIN_SURGE_CYCLES = 200;
	private static final int MAX_SURGE_CYCLES = 400;
	// How near, round its lane, a fish lets the one ahead of it get, in local units, before slowing to keep its
	// distance.
	private static final double KEEP_GAP = 192 * SCALE;
	// How many lengths of the two fish, on average, a fish keeps behind the one ahead, and needs clear to join a lane,
	// at least, so big fish keep big gaps.
	private static final double FOLLOW_LENGTHS = 1.5;
	// Changing lanes: how often a fish thinks about moving to the lane in or out from its own, picked at random
	// between these, in client ticks; how much clear water, round the lane it moves to, it needs ahead of and behind
	// where it would join, in local units; how long the move takes, in client ticks; and how long a fish whose lane
	// another has just joined keeps looking for a gap to leave by, before staying put.
	private static final int MIN_THINK_CYCLES = 300;
	private static final int MAX_THINK_CYCLES = 900;
	private static final double MERGE_GAP = 192 * SCALE;
	private static final int MERGE_CYCLES = 100;
	private static final int LEAVE_CYCLES = 500;
	// The fish in a lane start evenly round it, each up to START_STRAY, in radians, from its even place.
	private static final double START_STRAY = 0.2;
	// How many client ticks each fish takes to rise and settle again, and then holds still for before the next, each
	// picked at random between these.
	private static final int MIN_BOB_CYCLES = 70;
	private static final int MAX_BOB_CYCLES = 150;
	private static final int MIN_BOB_REST_CYCLES = 20;
	private static final int MAX_BOB_REST_CYCLES = 60;
	// How far each fish swings either side of the way it swims, as its tail wags, in the game's 2048ths of a turn
	// (20 is about 3.5 degrees), at the middle lane's speed or faster, and less by the square of how much slower it
	// is going, so slow fish barely wag; and how far it swims, in local units, for each swing there and back, so
	// faster fish wag faster.
	private static final int WAG = 20;
	private static final double WAG_DISTANCE = 100 * SCALE;
	// Which way up a fish is stood, 1 or -1: an item's model lies on its side, as it does on the floor.
	private static final int UPRIGHT = 1;
	// A fish tips nose down as it sinks and nose up as it rises: by up to DIP_PITCH degrees in a dip, at its
	// steepest, and BOB_PITCH in its bob. Its model is made at each PITCH_STEP degrees of that, as needed, since a
	// placed model can only be turned about upright, and swapped as it tips from one to the next.
	private static final double DIP_PITCH = 20;
	private static final double BOB_PITCH = 8;
	private static final double PITCH_STEP = 5;
	// How much smaller, in percent, each size of a kind of fish is than the last, up to its size spread.
	private static final int SIZE_STEP = 4;
	// How many slices a model is cut into along its length to straighten it.
	private static final int STRAIGHTEN_SLICES = 20;
	// The kinds whose models are reshaped by their look's sweep, straighten, joint, bend and stretch; the rest are
	// left as their model is.
	private static final Set<Integer> RESHAPED = Set.of(ItemID.RAW_SWORDFISH, ItemID.TBWT_RAW_KARAMBWAN);
	// How many frames of their wiggle swept arms are made at, cycled through as they swim, and how many waves run
	// along an arm at once.
	private static final int WIGGLE_FRAMES = 4;
	private static final double WIGGLE_WAVES = 1.2;
	// The kinds that keep the height they sit at untipped as they tip, rather than being sat back on the water by
	// their lowest point, which would lift a long-nosed fish's nose far out as its tail dips.
	private static final Set<Integer> KEEP_HEIGHT = Set.of(ItemID.RAW_SWORDFISH);
	// The kinds kept out of a shoal's innermost lanes, too long to circle so tight: how many of them.
	private static final Map<Integer, Integer> KEPT_FROM_MIDDLE = Map.of(ItemID.RAW_SWORDFISH, 2);
	// The kinds that give each other more room: how many, at most, share a lane, and how many lengths, rather than
	// FOLLOW_LENGTHS, they keep from any fish ahead and need clear to join a lane.
	private static final Map<Integer, Integer> MOST_IN_LANE = Map.of(ItemID.RAW_SWORDFISH, 2);
	private static final Map<Integer, Double> GAP_LENGTHS = Map.of(ItemID.RAW_SWORDFISH, 2.5);
	// The kinds swimming in a layer of their own, by its number, as lobsters do below the other fish.
	private static final Map<Integer, Integer> LAYERS = Map.of(ItemID.RAW_LOBSTER, 1);
	// The kinds with a depth range that keep this many of a spot's fish, at least, at their shallowest, no deeper
	// than their sink.
	private static final Map<Integer, Integer> SHALLOW_LEAST = Map.of(
		ItemID.TBWT_RAW_KARAMBWAN, 4, ItemID.RAW_MONKFISH, 7, ItemID.RAW_ANGLERFISH, 7);
	// And how much deeper, at least, than their sink those not at their shallowest sit, in local units.
	private static final Map<Integer, Integer> LEAST_DEEPER = Map.of(
		ItemID.TBWT_RAW_KARAMBWAN, 50, ItemID.RAW_MONKFISH, 50, ItemID.RAW_ANGLERFISH, 50);
	// How far either side of a joint, as a share of the fish's length, its bend eases in.
	private static final double JOINT_EASE = 0.08;
	// Whether fish of a kind vary in size, by its size spread, each size taking a set of models of its own. Off, to
	// keep the models few; each kind keeps its spread for when it's wanted again.
	private static final boolean SIZE_VARIATION = false;

	/**
	 * How one kind of fish is shown and moves, each kind's model being its own shape and size:
	 * <ul>
	 * <li>roll: how far it is rolled up off its side about its length, in degrees, 0 leaving it lying flat as an item
	 * does and 90 standing it upright; tilt: how far it is then tilted head up, in degrees</li>
	 * <li>size: how big it is, in percent of the item's own size; sizeSpread: how much smaller, in percent, each fish
	 * may be</li>
	 * <li>sink: how far below the water's height it sits; rise: how far above that it rises as it bobs; both in local
	 * units</li>
	 * <li>pivot: how far ahead of the model's middle, towards the head, it turns as it wags, in local units, so the
	 * head stays nearly still and the tail sweeps</li>
	 * <li>turn: how far it is turned to face the way it swims, in degrees, for a model whose head points some other
	 * way; spin: how fast it spins round as it swims, in degrees a second, 0 for not at all</li>
	 * <li>room, roomRange: how far at most it eases away from fish near it, and how near they have to be, in local
	 * units, against LANE_SPACING's lanes; roomEase: how much of the way there it eases each client tick, in
	 * percent</li>
	 * <li>dipEvery, dipDepth, dipMillis: how often on average it dips deeper than its bob takes it, never higher, in
	 * seconds; how much deeper at most, in local units; and how long a dip takes, down and back up, in
	 * milliseconds</li>
	 * <li>lightest: how light, at least, every face of it is, on the game's colour scale of 0 to 127, so none is
	 * too dark however it is lit</li>
	 * <li>straighten: how far a model bent along its length, as one drawn leaping, is straightened, in percent;
	 * stretch: how long it is made along its length, in percent of how long it is; joint and bend: where along its
	 * length, in percent, a model bent there is turned back straight, and by how many degrees, the shorter side of
	 * the joint swinging round it</li>
	 * <li>tipPivot: how far ahead of its middle, towards its head, it tips about as it bobs and dips, in local
	 * units</li>
	 * <li>pace: how fast it swims round, in percent of its lane's speed; surge: how much it speeds up and slows down
	 * as it swims, in percent of its lane's surge</li>
	 * <li>sweep and sweepBody: how far, in percent, the arms of a model reaching out all round, as the karambwan's,
	 * are swept back behind it, the tips the most, as an octopus's trail; and how much of its middle, in percent of
	 * its reach, is the body they reach from, left as it is; wiggle and wiggleRate: how far its swept arms wave, in
	 * percent of its reach, and how many of its WIGGLE_FRAMES it goes through a second</li>
	 * <li>depthRange: how much deeper, at most, each fish sits than its kind's sink, picked at random for it and kept,
	 * in local units</li>
	 * <li>wag: how far its tail wags, in percent of WAG, 0 for not at all; tip: how far it tips nose up and down as it
	 * bobs and dips, in percent of BOB_PITCH and DIP_PITCH, 0 for staying level</li>
	 * </ul>
	 */
	private static final class Look
	{
		private final int roll;
		private final int tilt;
		private final int size;
		private final int sizeSpread;
		private final int sink;
		private final int rise;
		private final int pivot;
		private final int turn;
		private final int spin;
		private final int room;
		private final int roomRange;
		private final int roomEase;
		private final int dipEvery;
		private final int dipDepth;
		private final int dipMillis;
		private final int lightest;
		private final int wag;
		private final int tip;
		private final int straighten;
		private final int stretch;
		private final int joint;
		private final int bend;
		private final int tipPivot;
		private final int pace;
		private final int surge;
		private final int sweep;
		private final int sweepBody;
		private final int wiggle;
		private final int wiggleRate;
		private final int depthRange;

		private Look(int[] values)
		{
			roll = values[0];
			tilt = values[1];
			size = values[2];
			sizeSpread = values[3];
			sink = values[4];
			rise = values[5];
			pivot = values[6];
			turn = values[7];
			spin = values[8];
			room = values[9];
			roomRange = values[10];
			roomEase = values[11];
			dipEvery = values[12];
			dipDepth = values[13];
			dipMillis = values[14];
			lightest = values[15];
			wag = values[16];
			tip = values[17];
			straighten = values[18];
			stretch = values[19];
			joint = values[20];
			bend = values[21];
			tipPivot = values[22];
			pace = values[23];
			surge = values[24];
			sweep = values[25];
			sweepBody = values[26];
			wiggle = values[27];
			wiggleRate = values[28];
			depthRange = values[29];
		}
	}

	// Each kind's look, by its item, in the order of Look's values: roll, tilt, size, sizeSpread, sink, rise, pivot,
	// turn, spin, room, roomRange, roomEase, dipEvery, dipDepth, dipMillis, lightest, wag, tip, straighten, stretch,
	// joint, bend, tipPivot, pace, surge, sweep, sweepBody, wiggle, wiggleRate, depthRange. Those not yet tuned look
	// like the anglerfish, the first tuned.
	private static final Look ANGLERFISH = new Look(new int[]{90, 46, 50, 17, 30, 8, 16, 0, 0, 40, 60, 30, 10, 10, 1500,
		0, 100, 0, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 200});
	private static final Look SHRIMP = new Look(new int[]{0, 5, 35, 17, 50, 20, 16, 126, 300, 40, 60, 30, 1, 10, 1500,
		0, 0, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0});
	private static final Look SARDINE = new Look(new int[]{90, 0, 30, 17, 5, 4, 16, -90, 0, 40, 60, 30, 10, 10, 1500,
		30, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0});
	private static final Look HERRING = new Look(new int[]{-90, 0, 45, 17, 10, 3, 16, -90, 0, 40, 60, 30, 3, 15, 1500,
		35, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0});
	private static final Look BASS = new Look(new int[]{-90, 0, 45, 17, 17, 3, 30, -90, 0, 40, 60, 30, 8, 15, 2500,
		30, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0});
	private static final Map<Integer, Look> LOOKS = Map.ofEntries(
		Map.entry(ItemID.RAW_ANGLERFISH, ANGLERFISH),
		Map.entry(ItemID.RAW_MONKFISH, new Look(new int[]{0, 0, 55, 17, 15, 6, 16, -42, 0, 40, 60, 30, 10, 10, 1500, 0,
			100, 0, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 250})),
		Map.entry(ItemID.TBWT_RAW_KARAMBWAN, new Look(new int[]{0, 12, 70, 17, 8, 0, 16, -90, 0, 40, 60, 30, 10, 0,
			1500, 0, 100, 100, 0, 110, 13, 37, -13, 50, 25, 100, 33, 17, 6, 250})),
		Map.entry(ItemID.RAW_SHRIMP, SHRIMP),
		Map.entry(ItemID.RAW_ANCHOVIES, SHRIMP),
		Map.entry(ItemID.RAW_SARDINE, SARDINE),
		Map.entry(ItemID.RAW_HERRING, HERRING),
		Map.entry(ItemID.RAW_MACKEREL, new Look(new int[]{-90, 0, 35, 17, 9, 5, 16, -90, 0, 40, 60, 30, 10, 12, 1500,
			25, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0})),
		Map.entry(ItemID.RAW_COD, new Look(new int[]{-90, 0, 35, 17, 12, 6, 16, -90, 0, 40, 60, 30, 10, 7, 1500,
			50, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0})),
		Map.entry(ItemID.RAW_LOBSTER, new Look(new int[]{0, 0, 45, 17, 30, 5, 0, -90, 0, 40, 60, 30, 3, 0, 1500,
			35, 0, 100, 0, 100, 50, 0, 0, 50, 50, 0, 35, 0, 6, 0})),
		Map.entry(ItemID.RAW_TUNA, BASS),
		Map.entry(ItemID.RAW_SWORDFISH, new Look(new int[]{-90, 19, 45, 17, 16, 2, 25, -90, 0, 40, 60, 30, 10, 10, 1500,
			35, 100, 100, 0, 120, 66, 80, -10, 100, 100, 0, 35, 0, 6, 0})),
		Map.entry(ItemID.RAW_BASS, BASS),
		Map.entry(ItemID.RAW_SHARK, new Look(new int[]{0, 0, 40, 30, 125, 0, 0, 0, 0, 40, 60, 30, 1, 5, 2500,
			0, 10, 0, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0})));
	// The kinds shown as a creature, still, rather than as their item, the raw shark's model looking wrong stood up:
	// one of these creatures, picked at random for each fish, for variety. A creature is already shaped swimming, so
	// its look leaves it unrolled.
	private static final Map<Integer, int[]> CREATURES = Map.of(
		ItemID.RAW_SHARK,
		new int[]{NpcID.SAILING_BULL_SHARK, NpcID.SAILING_TIGER_SHARK, NpcID.SAILING_GREAT_WHITE_SHARK});
	// The kinds kept few and in a ring of their own outside a shoal, never among the other fish: how many, at least
	// and at most, a spot with them has, and how many, at most, share one of the ring's lanes.
	private static final Map<Integer, int[]> KEPT_OUT = Map.of(
		ItemID.RAW_SHARK, new int[]{1, 3, 2});
	// The most of all kinds kept out a spot can have, for keeping room for them.
	private static final int MOST_KEPT_OUT = KEPT_OUT.values().stream().mapToInt(kept -> kept[1]).sum();

	private final Client client;
	private final Map<NPC, School> schools = new HashMap<>();
	// Each fish's model at each size, tip and look, by its item, its size in percent, its tip in steps and which of
	// its kind's creatures it is, loaded the first time it's needed.
	private final Map<Long, Model> models = new HashMap<>();

	/**
	 * The fish at one spot: where the spot is in the world, and its middle in the scene and the water's height there,
	 * which a map load can move; how far out each of its lanes is, the
	 * outermost's and innermost's even places, and its gaps against LANE_SPACING's; its fish, and the client tick they
	 * were last moved on at.
	 */
	private static final class School
	{
		private final WorldPoint spot;
		private int x;
		private int y;
		private int z;
		private final double[] lanes;
		// How many of its lanes are its main ones, the rest being its outer ring's, for the kinds kept out.
		private final int mainLanes;
		private final double outer;
		private final double inner;
		private final double gaps;
		// And the outer ring's, against LANE_SPACING's: its lanes' spacing, so the bigger kinds kept out keep bigger
		// gaps, more the wider apart their lanes are.
		private final double outerGaps;
		private final List<Swimmer> fish = new ArrayList<>();
		// How fast each is swimming this tick, kept so a new list isn't made every tick.
		private final double[] speeds;
		// And how far each wants to ease away from the fish near it this tick.
		private final double[] room;
		private int movedAt;

		private School(WorldPoint spot, int x, int y, int z, Crowd crowd, boolean keepsOut, int cycle)
		{
			this.spot = spot;
			this.x = x;
			this.y = y;
			this.z = z;
			mainLanes = crowd.lanes;
			lanes = new double[crowd.lanes + (keepsOut ? crowd.outerLanes : 0)];
			// Lanes too many or too wide to fit are drawn in closer, so the innermost stays clear of the middle.
			outer = crowd.radius;
			double spacing = Math.min(crowd.spacing, (outer - LANE_SPACING / 2) / (crowd.lanes - 1));
			inner = outer - spacing * (crowd.lanes - 1);
			gaps = spacing / LANE_SPACING;
			outerGaps = crowd.outerSpacing / LANE_SPACING;
			speeds = new double[crowd.count + MOST_KEPT_OUT];
			room = new double[crowd.count + MOST_KEPT_OUT];
			movedAt = cycle;
		}

		private double spacing()
		{
			return (outer - inner) / (mainLanes - 1);
		}

		/**
		 * The gaps kept in a lane against LANE_SPACING's: the outer ring's in its lanes, the main lanes' in theirs.
		 */
		private double gaps(int lane)
		{
			return lane >= mainLanes ? outerGaps : gaps;
		}
	}

	private static final class Swimmer
	{
		private final RuneLiteObject fish;
		// Which kind it is, by its item.
		private final int item;
		// Its size, in percent, and how far its model is tipped now, in PITCH_STEPs, nose up when more than 0.
		private final int size;
		private int pitch;
		// Which of its kind's creatures it looks like, for a kind shown as one, else 0.
		private int variant;
		// Which of its arms' wiggle frames it shows, and how far into its wiggle it starts, in client ticks.
		private int frame;
		private int wiggleStart;
		// How much deeper than its kind's sink it sits, picked for it and kept, in local units.
		private int deeper;
		// How long it is, nose to tail, in local units, as its model is made.
		private double length;
		// Its own share of its lane's speed; how long it takes to surge and slacken off again, and where in that it
		// starts; and the same for rising and falling. All picked at random, so fish in sight together don't move in
		// step.
		private final double speed;
		private final int surge;
		private final int surgePhase;
		private final int bob;
		private final int bobRest;
		private final int bobPhase;
		// Its two sways in and out: how long each takes, and where in it it starts.
		private final int swayA;
		private final int swayAPhase;
		private final int swayB;
		private final int swayBPhase;
		// The lane it is in, or moving to; how far round it is, in radians; how far out it is, and was as it started
		// moving lanes, at the client tick it started, or -1 while it isn't; how far through a swing its tail is, in
		// radians; when it next thinks about moving lanes; and until when it is looking for a gap to leave its lane
		// by, as another has joined it, or -1 while it isn't.
		private int lane;
		private double angle;
		private double radius;
		private double movedFrom;
		private int movingSince = -1;
		private double wag;
		// Where in its spin it starts, in 2048ths of a turn, so fish that spin don't all face the same way.
		private final int spinPhase;
		private int thinkAt;
		private int leaveBy = -1;
		// How far it has eased away from the fish near it, out from the middle, or in when less than 0.
		private double eased;
		// How far it is turned in or out from the way round, in radians, out when more than 0.
		private double drift;
		// The client tick it started dipping deeper at, or -1 while it isn't.
		private int dippingSince = -1;

		private Swimmer(RuneLiteObject fish, int item, int size, int lane, double angle, double radius, int cycle,
			ThreadLocalRandom random)
		{
			this.size = size;
			this.fish = fish;
			this.item = item;
			this.lane = lane;
			this.angle = angle;
			this.radius = radius;
			speed = (100 + random.nextInt(-SPEED_SPREAD, SPEED_SPREAD + 1)) / 100.0;
			surge = random.nextInt(MIN_SURGE_CYCLES, MAX_SURGE_CYCLES + 1);
			surgePhase = random.nextInt(surge);
			bob = random.nextInt(MIN_BOB_CYCLES, MAX_BOB_CYCLES + 1);
			bobRest = random.nextInt(MIN_BOB_REST_CYCLES, MAX_BOB_REST_CYCLES + 1);
			bobPhase = random.nextInt(bob + bobRest);
			swayA = random.nextInt(MIN_SWAY_CYCLES, MAX_SWAY_CYCLES + 1);
			swayAPhase = random.nextInt(swayA);
			swayB = random.nextInt(MIN_SWAY_CYCLES, MAX_SWAY_CYCLES + 1);
			swayBPhase = random.nextInt(swayB);
			wag = random.nextDouble(2 * Math.PI);
			spinPhase = random.nextInt(2048);
			thinkAt = cycle + random.nextInt(MIN_THINK_CYCLES, MAX_THINK_CYCLES + 1);
		}
	}

	SeaSpotFish(Client client)
	{
		this.client = client;
	}

	/**
	 * How crowded a spot's shoal is.
	 */
	private Crowd crowd(int spot)
	{
		return CROWDS.getOrDefault(spot, CROWD);
	}

	/**
	 * Starts fish swimming at a spot that has come into sight, if it is one of the spots at sea: its crowd's count of
	 * them shared out among the lanes around its middle.
	 */
	void add(NPC spot)
	{
		int[] kinds = SPOT_FISH.get(spot.getId());
		int[] odds = SPOT_FISH_ODDS.get(spot.getId());
		int[] least = SPOT_FISH_LEAST.get(spot.getId());
		LocalPoint at = spot.getLocalLocation();
		if (kinds == null || at == null || schools.containsKey(spot))
		{
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int cycle = client.getGameCycle();
		Crowd crowd = crowd(spot.getId());
		int plane = spot.getWorldLocation().getPlane();
		int[] spotKinds = kinds;
		boolean keepsOut = Arrays.stream(spotKinds).anyMatch(KEPT_OUT::containsKey);
		School school = new School(spot.getWorldLocation(), at.getX(), at.getY(),
			Perspective.getTileHeight(client, at, plane), crowd, keepsOut, cycle);
		int lanes = school.mainLanes;
		// Each lane's distance out, and how many fish start in it: one each while there are enough, then the rest
		// shared out at random among the lanes with room for another, more likely to those with more room left, so
		// the bigger lanes take more.
		int[] chosen = chooseFish(kinds, odds, least, crowd.count, random);
		int[] counts = new int[lanes];
		for (int lane = 0; lane < lanes; lane++)
		{
			school.lanes[lane] = school.inner + school.spacing() * lane + random.nextDouble(-LANE_STRAY, LANE_STRAY);
		}
		// The outer ring's lanes, outside the main ones.
		for (int lane = lanes; lane < school.lanes.length; lane++)
		{
			school.lanes[lane] = school.outer + crowd.outerGap + crowd.outerSpacing * (lane - lanes);
		}
		for (int left = crowd.count; left > 0; left--)
		{
			boolean everyHasOne = true;
			for (int count : counts)
			{
				everyHasOne &= count > 0;
			}
			int total = 0;
			int[] spare = new int[lanes];
			for (int lane = 0; lane < lanes; lane++)
			{
				spare[lane] = everyHasOne ? Math.max(0, room(school, lane) - counts[lane]) : counts[lane] == 0 ? 1 : 0;
				total += spare[lane];
			}
			if (total == 0)
			{
				break;
			}
			int pick = random.nextInt(total);
			for (int lane = 0; lane < lanes; lane++)
			{
				pick -= spare[lane];
				if (pick < 0)
				{
					counts[lane]++;
					break;
				}
			}
		}
		// From the outermost in, so the kinds kept out of the innermost lanes find room while it's still there.
		for (int lane = lanes - 1; lane >= 0; lane--)
		{
			double radius = school.lanes[lane];
			double turned = random.nextDouble(2 * Math.PI);
			// Evenly round the lane; more may join it later, while it has room.
			int count = counts[lane];
			for (int place = 0; place < count; place++)
			{
				double angle = turned + place * 2 * Math.PI / count
					+ random.nextDouble(-START_STRAY, START_STRAY);
				int item = pickInner(kinds, chosen, lane, school, random);
				if (!addFish(school, item, lane, angle, at, plane, cycle, random))
				{
					return;
				}
			}
		}
		// Then each kind kept out, between its fewest and most, spread round the outer ring's lanes at random.
		for (int item : spotKinds)
		{
			int[] kept = KEPT_OUT.get(item);
			if (kept == null || school.lanes.length == lanes)
			{
				continue;
			}
			int count = random.nextInt(kept[0], kept[1] + 1);
			double turned = random.nextDouble(2 * Math.PI);
			for (int place = 0; place < count; place++)
			{
				// One of the ring's lanes not yet as full as the kind allows, or none left to put it in.
				int[] open = new int[school.lanes.length - lanes];
				int choices = 0;
				for (int lane = lanes; lane < school.lanes.length; lane++)
				{
					int ringLane = lane;
					if (school.fish.stream().filter(swimmer -> swimmer.lane == ringLane).count() < kept[2])
					{
						open[choices++] = lane;
					}
				}
				if (choices == 0)
				{
					break;
				}
				int lane = open[random.nextInt(choices)];
				double angle = turned + place * 2 * Math.PI / count + random.nextDouble(-START_STRAY, START_STRAY);
				if (!addFish(school, item, lane, angle, at, plane, cycle, random))
				{
					return;
				}
			}
		}
		// Kinds that keep some fish at their shallowest have that many of theirs, chosen at random, brought up to it.
		for (Map.Entry<Integer, Integer> shallow : SHALLOW_LEAST.entrySet())
		{
			List<Swimmer> deep = new ArrayList<>();
			int atTop = 0;
			for (Swimmer swimmer : school.fish)
			{
				if (swimmer.item == shallow.getKey())
				{
					if (swimmer.deeper == 0)
					{
						atTop++;
					}
					else
					{
						deep.add(swimmer);
					}
				}
			}
			Collections.shuffle(deep, random);
			for (int i = 0; atTop + i < shallow.getValue() && i < deep.size(); i++)
			{
				deep.get(i).deeper = 0;
			}
		}
		for (Swimmer swimmer : school.fish)
		{
			place(school, swimmer, 0, 0, cycle);
			swimmer.fish.setActive(true);
		}
		schools.put(spot, school);
	}

	/**
	 * A kind's item's model, in its own colours: a copy, so the game's own model of the item is left as it is.
	 */
	private ModelData loadItem(int item)
	{
		ItemComposition fish = client.getItemDefinition(item);
		ModelData model = client.loadModelData(fish.getInventoryModel());
		return model == null ? null : recolor(model.cloneVertices().cloneColors(), fish.getColorToReplace(),
			fish.getColorToReplaceWith());
	}

	/**
	 * A creature's model, all its parts together in its own colours.
	 */
	private ModelData loadCreature(int npc)
	{
		NPCComposition creature = client.getNpcDefinition(npc);
		int[] parts = creature.getModels();
		if (parts == null)
		{
			return null;
		}
		ModelData[] loaded = new ModelData[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			loaded[i] = client.loadModelData(parts[i]);
			if (loaded[i] == null)
			{
				return null;
			}
		}
		return recolor(client.mergeModels(loaded).cloneVertices().cloneColors(), creature.getColorToReplace(),
			creature.getColorToReplaceWith());
	}

	/**
	 * Straightens a model bent along its length, its longest way, by a share of its bend: slices it along its length,
	 * finds the middle of each slice the other two ways, halfway between its furthest points so fins sway it little,
	 * and moves each slice that share of the way onto a straight line through the middle of the whole, spread out
	 * along it by how far round the bend it was, so it keeps its length.
	 */
	private static void straighten(ModelData model, double share)
	{
		int count = model.getVerticesCount();
		float[][] ways = {model.getVerticesX(), model.getVerticesY(), model.getVerticesZ()};
		int longest = 0;
		for (int i = 1; i < 3; i++)
		{
			longest = spread(ways[i], count) > spread(ways[longest], count) ? i : longest;
		}
		float[] along = ways[longest];
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			min = Math.min(min, along[i]);
			max = Math.max(max, along[i]);
		}
		double length = Math.max(1, max - min);
		int[] others = longest == 0 ? new int[]{1, 2} : longest == 1 ? new int[]{0, 2} : new int[]{0, 1};
		// The middle of each slice each other way, and of the whole.
		double[][] middle = new double[2][];
		double[] whole = new double[2];
		for (int o = 0; o < 2; o++)
		{
			float[] across = ways[others[o]];
			double[] low = new double[STRAIGHTEN_SLICES];
			double[] high = new double[STRAIGHTEN_SLICES];
			Arrays.fill(low, Double.MAX_VALUE);
			Arrays.fill(high, -Double.MAX_VALUE);
			for (int i = 0; i < count; i++)
			{
				int slice = slice(along[i], min, length);
				low[slice] = Math.min(low[slice], across[i]);
				high[slice] = Math.max(high[slice], across[i]);
			}
			middle[o] = new double[STRAIGHTEN_SLICES];
			for (int slice = 0; slice < STRAIGHTEN_SLICES; slice++)
			{
				middle[o][slice] = low[slice] <= high[slice] ? (low[slice] + high[slice]) / 2 : Double.NaN;
			}
			// A slice with nothing in it takes its nearest neighbour's middle.
			double[] found = middle[o].clone();
			for (int slice = 0; slice < STRAIGHTEN_SLICES; slice++)
			{
				for (int reach = 1; Double.isNaN(middle[o][slice]) && reach < STRAIGHTEN_SLICES; reach++)
				{
					if (slice - reach >= 0 && !Double.isNaN(found[slice - reach]))
					{
						middle[o][slice] = found[slice - reach];
					}
					else if (slice + reach < STRAIGHTEN_SLICES && !Double.isNaN(found[slice + reach]))
					{
						middle[o][slice] = found[slice + reach];
					}
				}
			}
			double lowest = Double.MAX_VALUE;
			double highest = -Double.MAX_VALUE;
			for (int i = 0; i < count; i++)
			{
				lowest = Math.min(lowest, across[i]);
				highest = Math.max(highest, across[i]);
			}
			whole[o] = (lowest + highest) / 2;
		}
		// How far round the bend each slice's middle is from the first's, along the line through the middles.
		double step = length / STRAIGHTEN_SLICES;
		double[] round = new double[STRAIGHTEN_SLICES];
		for (int slice = 1; slice < STRAIGHTEN_SLICES; slice++)
		{
			double a = middle[0][slice] - middle[0][slice - 1];
			double b = middle[1][slice] - middle[1][slice - 1];
			round[slice] = round[slice - 1] + Math.sqrt(step * step + a * a + b * b);
		}
		double stretch = round[STRAIGHTEN_SLICES - 1] / Math.max(1, step * (STRAIGHTEN_SLICES - 1));
		for (int i = 0; i < count; i++)
		{
			// Between the middles of the two slices nearest it, so the line through them bends smoothly.
			double at = (along[i] - min) / length * STRAIGHTEN_SLICES - 0.5;
			int below = Math.max(0, Math.min(STRAIGHTEN_SLICES - 1, (int) Math.floor(at)));
			int above = Math.min(STRAIGHTEN_SLICES - 1, below + 1);
			double through = Math.max(0, Math.min(1, at - below));
			for (int o = 0; o < 2; o++)
			{
				double bend = middle[o][below] + (middle[o][above] - middle[o][below]) * through - whole[o];
				ways[others[o]][i] = (float) (ways[others[o]][i] - bend * share);
			}
			// Along: as far round the bend as it was, about the middle of its length, by the share.
			double middleAlong = (min + max) / 2.0;
			double straightened = middleAlong + (along[i] - middleAlong) * stretch;
			along[i] = (float) (along[i] + (straightened - along[i]) * share);
		}
	}

	private static int slice(float along, float min, double length)
	{
		return Math.max(0, Math.min(STRAIGHTEN_SLICES - 1, (int) ((along - min) / length * STRAIGHTEN_SLICES)));
	}

	/**
	 * Swings the part of a model beyond a joint along its length, the shorter side of it, round the joint by so many
	 * degrees, within its flat side: the plane of its two longest ways. Points within JOINT_EASE of its length either
	 * side of the joint swing only part of the way, so it bends there smoothly rather than tearing.
	 */
	private static void bendTail(ModelData model, double joint, int degrees)
	{
		int count = model.getVerticesCount();
		float[][] ways = {model.getVerticesX(), model.getVerticesY(), model.getVerticesZ()};
		Integer[] order = {0, 1, 2};
		Arrays.sort(order, (a, b) -> Float.compare(spread(ways[b], count), spread(ways[a], count)));
		float[] along = ways[order[0]];
		float[] across = ways[order[1]];
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		float acrossMin = Float.MAX_VALUE;
		float acrossMax = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			min = Math.min(min, along[i]);
			max = Math.max(max, along[i]);
			acrossMin = Math.min(acrossMin, across[i]);
			acrossMax = Math.max(acrossMax, across[i]);
		}
		double length = Math.max(1, max - min);
		double at = min + length * joint;
		double middle = (acrossMin + acrossMax) / 2.0;
		// The shorter side swings: beyond the joint towards whichever end is nearer.
		int side = joint >= 0.5 ? 1 : -1;
		double ease = length * JOINT_EASE;
		for (int i = 0; i < count; i++)
		{
			double beyond = (along[i] - at) * side;
			if (beyond <= -ease)
			{
				continue;
			}
			double share = Math.min(1, (beyond + ease) / (2 * ease));
			double turn = Math.toRadians(degrees) * share * side;
			double a = along[i] - at;
			double b = across[i] - middle;
			along[i] = (float) (at + a * Math.cos(turn) - b * Math.sin(turn));
			across[i] = (float) (middle + a * Math.sin(turn) + b * Math.cos(turn));
		}
	}

	/**
	 * Sweeps the arms of a model reaching out all round back behind it, the way x grows, as an octopus trails its
	 * arms: each point beyond the body, the middle body of its reach, turns about the model's middle a share of the
	 * way from where it points to straight behind, more the further out it is, so each arm curves back; then waves
	 * it up and down by wiggle of its reach, as it is at a frame of its wiggle.
	 */
	private static void sweep(ModelData model, double share, double body, double wiggle, int frame)
	{
		int count = model.getVerticesCount();
		float[] x = model.getVerticesX();
		float[] y = model.getVerticesY();
		float[] z = model.getVerticesZ();
		double[] middle = {(min(x, count) + max(x, count)) / 2.0, (min(y, count) + max(y, count)) / 2.0,
			(min(z, count) + max(z, count)) / 2.0};
		double reach = 0;
		for (int i = 0; i < count; i++)
		{
			reach = Math.max(reach, Math.hypot(Math.hypot(x[i] - middle[0], y[i] - middle[1]), z[i] - middle[2]));
		}
		double inner = reach * body;
		for (int i = 0; i < count; i++)
		{
			double dx = x[i] - middle[0];
			double dy = y[i] - middle[1];
			double dz = z[i] - middle[2];
			double out = Math.hypot(Math.hypot(dx, dy), dz);
			if (out <= inner || reach <= inner)
			{
				continue;
			}
			// The angle from straight behind, (1, 0, 0), and the line it turns about, across both.
			double angle = Math.acos(Math.max(-1, Math.min(1, dx / out)));
			double ax = 0;
			double ay = dz;
			double az = -dy;
			double across = Math.hypot(ay, az);
			if (across < 1e-6)
			{
				// Pointing straight ahead or behind: turned about the up line.
				ay = 1;
				az = 0;
				across = 1;
			}
			ay /= across;
			az /= across;
			double turn = angle * share * (out - inner) / (reach - inner);
			// Turned by Rodrigues' rule about the unit line (0, ay, az).
			double cos = Math.cos(turn);
			double sin = Math.sin(turn);
			double dot = ay * dy + az * dz;
			double cx = ay * dz - az * dy;
			double cy = az * dx;
			double cz = -ay * dx;
			x[i] = (float) (middle[0] + dx * cos + cx * sin + ax * dot * (1 - cos));
			y[i] = (float) (middle[1] + dy * cos + cy * sin + ay * dot * (1 - cos));
			z[i] = (float) (middle[2] + dz * cos + cz * sin + az * dot * (1 - cos));
			// Then waved up and down, more towards the tip, by a wave running out along the arm, each arm, by which
			// way it reached, a little behind the next, so they don't wave together.
			double along = (out - inner) / (reach - inner);
			double arm = Math.atan2(dz, dy) * 2;
			y[i] += (float) (wiggle * reach * along
				* Math.sin(2 * Math.PI * (WIGGLE_WAVES * along - (double) frame / WIGGLE_FRAMES) + arm));
		}
	}

	private static float min(float[] values, int count)
	{
		float min = Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			min = Math.min(min, values[i]);
		}
		return min;
	}

	private static float max(float[] values, int count)
	{
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			max = Math.max(max, values[i]);
		}
		return max;
	}

	/**
	 * Lengthens or shortens a model along its length, its longest way, about its middle, by a share.
	 */
	private static void stretch(ModelData model, double share)
	{
		int count = model.getVerticesCount();
		float[][] ways = {model.getVerticesX(), model.getVerticesY(), model.getVerticesZ()};
		float[] along = ways[0];
		for (float[] way : ways)
		{
			along = spread(way, count) > spread(along, count) ? way : along;
		}
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			min = Math.min(min, along[i]);
			max = Math.max(max, along[i]);
		}
		double middle = (min + max) / 2.0;
		for (int i = 0; i < count; i++)
		{
			along[i] = (float) (middle + (along[i] - middle) * share);
		}
	}

	private static ModelData recolor(ModelData model, short[] from, short[] to)
	{
		for (int i = 0; from != null && to != null && i < from.length; i++)
		{
			model.recolor(from[i], to[i]);
		}
		return model;
	}

	/**
	 * Decides which fish a spot's main lanes will have, before they are placed: how many of each of its kinds, in the
	 * same order. First the fewest of each its spot keeps, then the rest by how likely each is; never a kind kept to
	 * the outer ring.
	 */
	private static int[] chooseFish(int[] kinds, int[] odds, int[] least, int count, ThreadLocalRandom random)
	{
		int[] chosen = new int[kinds.length];
		int left = count;
		for (int k = 0; least != null && k < kinds.length && k < least.length; k++)
		{
			if (!KEPT_OUT.containsKey(kinds[k]))
			{
				chosen[k] = Math.min(least[k], Math.max(0, left));
				left -= chosen[k];
			}
		}
		boolean any = Arrays.stream(kinds).anyMatch(kind -> !KEPT_OUT.containsKey(kind));
		for (; left > 0 && any; left--)
		{
			int k;
			do
			{
				k = pick(odds, kinds.length, random);
			}
			while (KEPT_OUT.containsKey(kinds[k]));
			chosen[k]++;
		}
		return chosen;
	}

	/**
	 * Takes one of the fish chosen for a spot's main lanes for one of them, by how many of each are left, but none
	 * kept out of that lane or already as many there as its kind allows; failing those, any kind that may swim there.
	 */
	private static int pickInner(int[] kinds, int[] chosen, int lane, School school, ThreadLocalRandom random)
	{
		int total = 0;
		for (int k = 0; k < kinds.length; k++)
		{
			total += mayJoin(kinds[k], lane, school) ? chosen[k] : 0;
		}
		if (total > 0)
		{
			int roll = random.nextInt(total);
			for (int k = 0; k < kinds.length; k++)
			{
				roll -= mayJoin(kinds[k], lane, school) ? chosen[k] : 0;
				if (roll < 0)
				{
					chosen[k]--;
					return kinds[k];
				}
			}
		}
		for (int item : kinds)
		{
			if (mayJoin(item, lane, school))
			{
				return item;
			}
		}
		return kinds[0];
	}

	/**
	 * Whether a kind may be put in one of the main lanes as the spot is laid out: not one kept to the outer ring, nor
	 * kept out of that lane, nor already as many there as its kind allows.
	 */
	private static boolean mayJoin(int item, int lane, School school)
	{
		return !KEPT_OUT.containsKey(item) && lane >= KEPT_FROM_MIDDLE.getOrDefault(item, 0)
			&& inLane(school, item, lane) < MOST_IN_LANE.getOrDefault(item, Integer.MAX_VALUE);
	}

	/**
	 * Whether a kind may swim in a lane: those kept out only in the outer ring's lanes, the rest only in the main
	 * lanes, and none in the innermost lanes it is kept from.
	 */
	private static boolean allowed(int item, int lane, School school)
	{
		return KEPT_OUT.containsKey(item) == lane >= school.mainLanes && lane >= KEPT_FROM_MIDDLE.getOrDefault(item, 0);
	}

	/**
	 * Picks one of a spot's fish, by how likely each is, or evenly without odds.
	 */
	private static int pick(int[] odds, int count, ThreadLocalRandom random)
	{
		if (odds == null)
		{
			return random.nextInt(count);
		}
		int roll = random.nextInt(Arrays.stream(odds).sum());
		for (int i = 0; i < odds.length; i++)
		{
			roll -= odds[i];
			if (roll < 0)
			{
				return i;
			}
		}
		return odds.length - 1;
	}

	/**
	 * Adds a fish of a kind to a shoal, in a lane at an angle round it, at one of its kind's sizes, saying whether it
	 * could; if not, its model couldn't be made, and the shoal's fish so far are taken away.
	 */
	private boolean addFish(School school, int item, int lane, double angle, LocalPoint at, int plane, int cycle,
		ThreadLocalRandom random)
	{
		// Smaller in SIZE_STEPs, so a kind has only a few sizes of model, while SIZE_VARIATION; otherwise all its size.
		int size = !SIZE_VARIATION ? look(item).size
			: look(item).size * (100 - SIZE_STEP * random.nextInt(look(item).sizeSpread / SIZE_STEP + 1)) / 100;
		int[] creatures = CREATURES.get(item);
		int variant = creatures == null ? 0 : random.nextInt(creatures.length);
		Model model = model(item, size, 0, variant, 0);
		if (model == null)
		{
			school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
			return false;
		}
		RuneLiteObject fish = client.createRuneLiteObject();
		fish.setModel(model);
		fish.setLocation(at, plane);
		Swimmer swimmer = new Swimmer(fish, item, size, lane, angle, school.lanes[lane], cycle, random);
		swimmer.variant = variant;
		swimmer.wiggleStart = random.nextInt(50 * WIGGLE_FRAMES);
		swimmer.deeper = deeper(item, random);
		int points = model.getVerticesCount();
		swimmer.length = Math.max(spread(model.getVerticesX(), points), spread(model.getVerticesZ(), points));
		school.fish.add(swimmer);
		return true;
	}

	/**
	 * How much deeper than its kind's sink a new fish sits: a random depth within its kind's range, but never only a
	 * little deeper, under the least its kind allows other than none, so none of it just pokes out of the water.
	 */
	private int deeper(int item, ThreadLocalRandom random)
	{
		int range = look(item).depthRange;
		int least = LEAST_DEEPER.getOrDefault(item, 0);
		int deeper = range > 0 ? random.nextInt(range + 1) : 0;
		if (deeper > 0 && deeper < least)
		{
			deeper = range < least ? 0 : random.nextInt(least, range + 1);
		}
		return deeper;
	}

	/**
	 * A kind of fish's model at a size and tipped nose up by a number of PITCH_STEPs, made the first time it's needed.
	 */
	private Model model(int item, int size, int pitch, int variant, int frame)
	{
		return models.computeIfAbsent((((item * 1000L + size) * 64 + pitch + 32) * 8 + variant) * 8 + frame,
			key -> load(item, size, pitch, variant, frame));
	}

	private Model load(int item, int size, int pitch, int variant, int frame)
	{
		ModelData model = CREATURES.containsKey(item) ? loadCreature(CREATURES.get(item)[variant]) : loadItem(item);
		if (model == null)
		{
			return null;
		}
		// Only the kinds that need it are reshaped.
		boolean reshaped = RESHAPED.contains(item);
		if (reshaped && look(item).sweep > 0)
		{
			sweep(model, look(item).sweep / 100.0, look(item).sweepBody / 100.0, look(item).wiggle / 100.0, frame);
		}
		if (reshaped && look(item).straighten > 0)
		{
			straighten(model, look(item).straighten / 100.0);
		}
		if (reshaped && look(item).bend != 0)
		{
			bendTail(model, look(item).joint / 100.0, look(item).bend);
		}
		if (reshaped && look(item).stretch != 100)
		{
			stretch(model, look(item).stretch / 100.0);
		}
		// Tipped about a point ahead of its middle, towards its head, by its kind's tip pivot at its size: its head
		// faces (sine, -cosine) of its turn in its model's own ways.
		double turn = Math.toRadians(look(item).turn);
		standUp(model, look(item).roll, look(item).tilt, pitch * PITCH_STEP, look(item).tipPivot * 100.0 / size,
			Math.sin(turn), -Math.cos(turn), KEEP_HEIGHT.contains(item));
		int scale = size * 128 / 100;
		model.scale(scale, scale, scale);
		// Lit as the game lights any item; only its colours are lightened after, where its kind asks.
		Model lit = model.light();
		if (look(item).lightest > 0 && lit != null)
		{
			lighten(lit, look(item).lightest);
		}
		return lit;
	}

	/**
	 * Makes every face of a lit model at least so light, keeping its hue and saturation: both its colours as the
	 * game has lit them and those it was lit from, which renderers that light models themselves, such as 117 HD,
	 * start from. The game's colours keep hue in their top 6 bits, saturation in the next 3 and lightness in the
	 * last 7.
	 */
	private static void lighten(Model model, int lightest)
	{
		for (int[] colours : new int[][]{model.getFaceColors1(), model.getFaceColors2(), model.getFaceColors3()})
		{
			for (int i = 0; colours != null && i < colours.length; i++)
			{
				// Below 0 marks a face drawn flat, from its first colour alone, or not drawn.
				if (colours[i] >= 0 && (colours[i] & 127) < lightest)
				{
					colours[i] = colours[i] & ~127 | lightest;
				}
			}
		}
		short[] unlit = model.getUnlitFaceColors();
		for (int i = 0; unlit != null && i < unlit.length; i++)
		{
			int colour = unlit[i] & 0xffff;
			if ((colour & 127) < lightest)
			{
				unlit[i] = (short) (colour & ~127 | lightest);
			}
		}
	}

	/**
	 * How a kind of fish is shown.
	 */
	private Look look(int item)
	{
		return LOOKS.getOrDefault(item, ANGLERFISH);
	}

	/**
	 * Rolls a fish lying on its side up about its length, which is the longer of its two flat sides, by roll degrees,
	 * tilts it head up by tilt degrees, tips it head up by tip degrees more about a point pivot ahead of its middle,
	 * its head being the way (headX, headZ) in its model, and sits its lowest point on the water: tipped, if it
	 * keeps its height, as it lies untipped.
	 */
	private static void standUp(ModelData model, int roll, int tilt, double tip, double pivot, double headX,
		double headZ, boolean keepHeight)
	{
		int count = model.getVerticesCount();
		float[] x = model.getVerticesX();
		float[] y = model.getVerticesY();
		float[] z = model.getVerticesZ();
		boolean longX = spread(x, count) >= spread(z, count);
		float[] across = longX ? z : x;
		float[] length = longX ? x : z;
		double rollCos = Math.cos(Math.toRadians(roll));
		double rollSin = Math.sin(Math.toRadians(roll)) * UPRIGHT;
		double cos = Math.cos(Math.toRadians(tilt));
		double sin = Math.sin(Math.toRadians(tilt));
		double tipCos = Math.cos(Math.toRadians(tip));
		double tipSin = Math.sin(Math.toRadians(tip));
		// The pivot along its length, on whichever side its head is.
		double head = longX ? headX : headZ;
		double about = pivot * (head < 0 ? -1 : 1);
		float lowest = -Float.MAX_VALUE;
		float tipped = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			float up = y[i];
			y[i] = (float) (up * rollCos + across[i] * rollSin);
			across[i] = (float) (across[i] * rollCos - up * rollSin);
			// Then tilted about the line across the fish.
			float along = length[i];
			length[i] = (float) (along * cos - y[i] * sin);
			y[i] = (float) (along * sin + y[i] * cos);
			lowest = Math.max(lowest, y[i]);
			// Then tipped about the line across it through the pivot.
			double from = length[i] - about;
			float height = y[i];
			length[i] = (float) (about + from * tipCos - height * tipSin);
			y[i] = (float) (from * tipSin + height * tipCos);
			tipped = Math.max(tipped, y[i]);
		}
		// Its lowest point is the largest, higher being lower in the game's heights. Keeping its height, it sits as it
		// lies untipped, so its tail dips as far as its nose lifts rather than lifting it all.
		lowest = keepHeight ? lowest : tipped;
		for (int i = 0; i < count; i++)
		{
			y[i] -= lowest;
		}
	}

	private static float spread(float[] values, int count)
	{
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			min = Math.min(min, values[i]);
			max = Math.max(max, values[i]);
		}
		return max - min;
	}

	/**
	 * Takes away the fish at a spot that has gone out of sight.
	 */
	void remove(NPC spot)
	{
		School school = schools.remove(spot);
		if (school == null)
		{
			return;
		}
		school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
		// The models of any kind no longer swimming anywhere in sight are let go, to be made again if it comes back.
		Set<Integer> swimming = new HashSet<>();
		for (School other : schools.values())
		{
			other.fish.forEach(swimmer -> swimming.add(swimmer.item));
		}
		models.keySet().removeIf(key -> !swimming.contains((int) (key / 8 / 8 / 64 / 1000)));
	}

	/**
	 * Moves every fish on, each client tick.
	 */
	void swim()
	{
		if (schools.isEmpty())
		{
			return;
		}
		int cycle = client.getGameCycle();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (School school : schools.values())
		{
			// However many ticks have gone by, but not so many that a fish jumps far after a stall.
			int ticks = Math.min(10, cycle - school.movedAt);
			school.movedAt = cycle;
			if (ticks <= 0)
			{
				continue;
			}
			int count = school.fish.size();
			double[] speeds = school.speeds;
			for (int i = 0; i < count; i++)
			{
				speeds[i] = speed(school, school.fish.get(i), cycle, school.inner);
			}
			follow(school);
			makeRoom(school);
			for (int i = 0; i < count; i++)
			{
				Swimmer swimmer = school.fish.get(i);
				double was = swimmer.radius + sway(swimmer, cycle - ticks) + swimmer.eased;
				swimmer.eased += (school.room[i] - swimmer.eased)
					* Math.min(1, look(swimmer.item).roomEase / 100.0 * ticks);
				if (swimmer.movingSince >= 0)
				{
					double through = Math.min(1, (cycle - swimmer.movingSince) / (double) MERGE_CYCLES);
					double eased = through * through * (3 - 2 * through);
					swimmer.radius = swimmer.movedFrom + (school.lanes[swimmer.lane] - swimmer.movedFrom) * eased;
					if (through >= 1)
					{
						swimmer.movingSince = -1;
					}
				}
				double moved = speeds[i] * ticks;
				swimmer.angle = (swimmer.angle + moved / swimmer.radius) % (2 * Math.PI);
				swimmer.wag = (swimmer.wag + 2 * Math.PI * moved / WAG_DISTANCE) % (2 * Math.PI);
				double outward = (swimmer.radius + sway(swimmer, cycle) + swimmer.eased - was) / ticks;
				place(school, swimmer, speeds[i], outward, cycle);
			}
			for (Swimmer swimmer : school.fish)
			{
				think(school, swimmer, cycle, random);
				// Seconds are 50 client ticks, each 20 ms.
				Look look = look(swimmer.item);
				if (swimmer.dippingSince >= 0 ? cycle - swimmer.dippingSince >= Math.max(1, look.dipMillis / 20)
					: random.nextDouble() < ticks / (look.dipEvery * 50.0))
				{
					swimmer.dippingSince = swimmer.dippingSince >= 0 ? -1 : cycle;
				}
			}
		}
	}

	/**
	 * How fast a fish would swim now, in local units a client tick, before minding the fish ahead: its lane's speed at
	 * how far out it is, its own share of that and its surge.
	 */
	private double speed(School school, Swimmer swimmer, int cycle, double inner)
	{
		double out = (swimmer.radius - inner) / (school.outer - inner);
		double surging = Math.sin(2 * Math.PI * ((cycle + swimmer.surgePhase) % swimmer.surge) / swimmer.surge);
		// Evenly faster from the second lane out; the innermost slower still, easing up to the second's speed.
		double second = 1.0 / (school.mainLanes - 1);
		double lane = out >= second
			? INNER_SPEED + (OUTER_SPEED - INNER_SPEED) * out
			: INNERMOST_SPEED + (INNER_SPEED + (OUTER_SPEED - INNER_SPEED) * second - INNERMOST_SPEED) * out / second;
		double speed = REFERENCE_SPEED * lane * swimmer.speed * look(swimmer.item).pace / 100.0
			* (1 + (INNER_SURGE + (OUTER_SURGE - INNER_SURGE) * out) * look(swimmer.item).surge / 100.0 * surging);
		return speed;
	}

	/**
	 * Slows each fish closing on the one ahead of it in its lane: nearer than the gap they keep, it swims no faster
	 * than that one, less the nearer it is, so it can never close the gap, only drift back.
	 */
	private static void follow(School school)
	{
		int count = school.fish.size();
		double[] own = Arrays.copyOf(school.speeds, count);
		for (int i = 0; i < count; i++)
		{
			Swimmer swimmer = school.fish.get(i);
			int nearest = -1;
			double ahead = Double.MAX_VALUE;
			for (int j = 0; j < count; j++)
			{
				Swimmer other = school.fish.get(j);
				if (j != i && other.lane == swimmer.lane && layer(other.item) == layer(swimmer.item))
				{
					double turn = other.angle - swimmer.angle;
					turn = turn < 0 ? turn + 2 * Math.PI : turn;
					if (turn * swimmer.radius < ahead)
					{
						ahead = turn * swimmer.radius;
						nearest = j;
					}
				}
			}
			if (nearest < 0)
			{
				continue;
			}
			Swimmer leader = school.fish.get(nearest);
			double keep = Math.max(KEEP_GAP * school.gaps(swimmer.lane),
				lengths(swimmer, leader) * (swimmer.length + leader.length) / 2);
			if (ahead < keep)
			{
				school.speeds[i] = Math.min(own[i], own[nearest] * ahead / keep);
			}
		}
	}

	/**
	 * Has a fish move to the lane in or out from its own, if it is time for it to think about it, or it is looking
	 * to leave its lane, and there is clear water there to join it by. A fish already in that lane then looks to
	 * leave it in turn.
	 */
	private static void think(School school, Swimmer swimmer, int cycle, ThreadLocalRandom random)
	{
		if (swimmer.movingSince >= 0)
		{
			return;
		}
		boolean leaving = swimmer.leaveBy >= cycle;
		if (!leaving && cycle < swimmer.thinkAt)
		{
			return;
		}
		swimmer.leaveBy = leaving ? swimmer.leaveBy : -1;
		swimmer.thinkAt = cycle + random.nextInt(MIN_THINK_CYCLES, MAX_THINK_CYCLES + 1);
		int first = random.nextBoolean() ? -1 : 1;
		for (int way : new int[]{first, -first})
		{
			int lane = swimmer.lane + way;
			if (lane < 0 || lane >= school.lanes.length || !allowed(swimmer.item, lane, school)
				|| !clear(school, swimmer, lane))
			{
				continue;
			}
			swimmer.movedFrom = swimmer.radius;
			swimmer.movingSince = cycle;
			swimmer.lane = lane;
			swimmer.leaveBy = -1;
			for (Swimmer other : school.fish)
			{
				if (other != swimmer && other.lane == lane && other.movingSince < 0)
				{
					other.leaveBy = cycle + LEAVE_CYCLES;
					break;
				}
			}
			return;
		}
	}

	/**
	 * Whether a fish could join a lane where it is: the lane has room for another, no more than its kind allows in
	 * one lane if it is kept out, and no fish in it, or still leaving it, is within MERGE_GAP ahead or behind.
	 */
	private static boolean clear(School school, Swimmer swimmer, int lane)
	{
		int in = 0;
		for (Swimmer other : school.fish)
		{
			boolean there = other.lane == lane;
			boolean leaving = other.movingSince >= 0 && other.movedFrom == school.lanes[lane];
			if (other == swimmer || !there && !leaving || layer(other.item) != layer(swimmer.item))
			{
				continue;
			}
			in += there ? 1 : 0;
			double turn = Math.abs(Math.IEEEremainder(other.angle - swimmer.angle, 2 * Math.PI));
			if (turn * school.lanes[lane] < Math.max(MERGE_GAP * school.gaps(lane),
				lengths(swimmer, other) * (swimmer.length + other.length) / 2))
			{
				return false;
			}
		}
		int[] kept = KEPT_OUT.get(swimmer.item);
		return in < (kept == null ? room(school, lane) : Math.min(kept[2], room(school, lane)))
			&& inLane(school, swimmer.item, lane) < MOST_IN_LANE.getOrDefault(swimmer.item, Integer.MAX_VALUE);
	}

	/**
	 * Which layer of a shoal a kind swims in, the fish of different layers paying each other no mind: none following,
	 * making room for or blocking a lane to one in another. The kinds kept out are a layer of their own, as are those
	 * in LAYERS; the rest share layer 0.
	 */
	private static int layer(int item)
	{
		return LAYERS.getOrDefault(item, KEPT_OUT.containsKey(item) ? -1 : 0);
	}

	/**
	 * How many fish of a kind are in a lane, or moving to it.
	 */
	private static int inLane(School school, int item, int lane)
	{
		int in = 0;
		for (Swimmer swimmer : school.fish)
		{
			in += swimmer.item == item && swimmer.lane == lane ? 1 : 0;
		}
		return in;
	}

	/**
	 * How many of their lengths, on average, two fish keep apart: the more of the two kinds' gaps.
	 */
	private static double lengths(Swimmer one, Swimmer other)
	{
		return Math.max(GAP_LENGTHS.getOrDefault(one.item, FOLLOW_LENGTHS),
			GAP_LENGTHS.getOrDefault(other.item, FOLLOW_LENGTHS));
	}

	/**
	 * How many fish a lane holds: as many as fit round it with LANE_ROOM merging gaps each, and always one.
	 */
	private static int room(School school, int lane)
	{
		return Math.max(1,
			(int) (2 * Math.PI * school.lanes[lane] / (LANE_ROOM * MERGE_GAP * school.gaps(lane))));
	}

	/**
	 * Works out how far each fish wants to ease away from those near it, in or out from the middle: away from each
	 * within its kind's range, more the nearer it is, and never more than its kind's room in all, both in proportion
	 * to the spot's lanes.
	 */
	private void makeRoom(School school)
	{
		int count = school.fish.size();
		for (int i = 0; i < count; i++)
		{
			Swimmer swimmer = school.fish.get(i);
			Look look = look(swimmer.item);
			double roomRange = look.roomRange * school.gaps(swimmer.lane);
			double room = look.room * school.gaps(swimmer.lane);
			double out = swimmer.radius + swimmer.eased;
			double want = 0;
			for (int j = 0; j < count; j++)
			{
				Swimmer other = school.fish.get(j);
				// Fish in different layers pay each other no mind.
				if (layer(other.item) != layer(swimmer.item))
				{
					continue;
				}
				double otherOut = other.radius + other.eased;
				double apart = Math.hypot(out * Math.sin(swimmer.angle) - otherOut * Math.sin(other.angle),
					out * Math.cos(swimmer.angle) - otherOut * Math.cos(other.angle));
				if (j == i || apart >= roomRange)
				{
					continue;
				}
				// Out if it is the further out of the two, in if not; two as far out as each other part by order.
				double way = Math.abs(out - otherOut) > 1 ? Math.signum(out - otherOut) : i < j ? -1 : 1;
				want += way * room * (1 - apart / roomRange);
			}
			school.room[i] = Math.max(-room, Math.min(room, want));
		}
	}

	/**
	 * How far a fish has swayed out from its lane at a client tick, in local units, in when less than 0.
	 */
	private static double sway(Swimmer swimmer, int cycle)
	{
		double a = Math.sin(2 * Math.PI * ((cycle + swimmer.swayAPhase) % swimmer.swayA) / swimmer.swayA);
		double b = Math.sin(2 * Math.PI * ((cycle + swimmer.swayBPhase) % swimmer.swayB) / swimmer.swayB);
		return SWAY * (0.6 * a + 0.4 * b);
	}

	/**
	 * Puts a fish where it is, facing the way it swims, given how fast it is going on round and out, in local units a
	 * client tick.
	 */
	private void place(School school, Swimmer swimmer, double speed, double outward, int cycle)
	{
		Look look = look(swimmer.item);
		double out = swimmer.radius + sway(swimmer, cycle) + swimmer.eased;
		double sine = Math.sin(swimmer.angle);
		double cosine = Math.cos(swimmer.angle);
		// Going round this way it heads a quarter turn on from where it is, turned in or out as it moves lanes: 0
		// faces south, 512 west, 1024 north, in the game's 2048ths of a turn. The turn eases towards the way it is
		// moving, and only so far, so quick nudges in or out don't swing it about.
		double toward = speed > 0 ? Math.atan2(outward, speed) : 0;
		swimmer.drift += (Math.max(-MAX_DRIFT, Math.min(MAX_DRIFT, toward)) - swimmer.drift) * DRIFT_EASE;
		double drift = swimmer.drift;
		int heading = (int) Math.round((swimmer.angle - drift) * 2048 / (2 * Math.PI)) + 1536 & 2047;
		// Its tail wags less by the square of how much slower than the middle lane's speed it is going.
		double pace = Math.min(1, speed / REFERENCE_SPEED);
		int swung = heading + (int) Math.round(WAG * look.wag / 100.0 * pace * pace * Math.sin(swimmer.wag)) & 2047;
		// Spun round from where it started, at its kind's speed: 2048ths of a turn a second over 50 client ticks. A
		// kind that doesn't spin isn't turned at all.
		int spun = look.spin == 0 ? 0 : swimmer.spinPhase - (int) ((long) look.spin * 2048 / 360 * cycle / 50);
		swimmer.fish.setOrientation(swung + look.turn * 2048 / 360 + spun & 2047);
		// Swung about a point ahead of its middle, so the head stays nearly still and the tail sweeps: the same turn
		// with the fish moved over by how far that point's turn carries its middle. It faces (-sine, -cosine).
		swimmer.fish.setX(school.x + (int) Math.round(out * sine)
			+ (look.pivot * (Perspective.SINE[swung] - Perspective.SINE[heading]) >> 16));
		swimmer.fish.setY(school.y + (int) Math.round(out * cosine)
			+ (look.pivot * (Perspective.COSINE[swung] - Perspective.COSINE[heading]) >> 16));
		// Higher is lower in the game's heights.
		// A bob rises and settles back smoothly, then holds still at rest a while; -1 while holding.
		int bobbing = (cycle + swimmer.bobPhase) % (swimmer.bob + swimmer.bobRest);
		int bobAt = bobbing < swimmer.bob ? bobbing * 2048 / swimmer.bob : -1;
		int risen = bobAt < 0 ? 0 : look.rise * (65536 - Perspective.COSINE[bobAt]) >> 17;
		// A dip goes down and back up smoothly, only ever deeper.
		double through = swimmer.dippingSince < 0 ? 0
			: Math.PI * (cycle - swimmer.dippingSince) / Math.max(1, look.dipMillis / 20.0);
		double dip = Math.sin(through);
		swimmer.fish.setZ(school.z + look.sink + swimmer.deeper - risen
			+ (int) Math.round(look.dipDepth * dip * dip));
		// Tipped nose down while sinking and up while rising, the most where it moves fastest: its bob rises as the
		// sine of its way through is above 0, its dip sinks as twice its way through's sine is. Level while holding.
		// Only a kind that bobs or dips.
		double tip = look.tip / 100.0 * ((look.rise > 0 && bobAt >= 0 ? BOB_PITCH * Perspective.SINE[bobAt] / 65536 : 0)
			- (swimmer.dippingSince >= 0 && look.dipDepth > 0 ? DIP_PITCH * Math.sin(2 * through) : 0));
		int pitch = (int) Math.round(tip / PITCH_STEP);
		// Arms that wiggle step through their frames at their kind's rate, a second being 50 client ticks.
		int frame = RESHAPED.contains(swimmer.item) && look.wiggle > 0 && look.sweep > 0
			? (int) ((long) (cycle + swimmer.wiggleStart) * look.wiggleRate / 50 % WIGGLE_FRAMES) : 0;
		if (pitch != swimmer.pitch || frame != swimmer.frame)
		{
			Model model = model(swimmer.item, swimmer.size, pitch, swimmer.variant, frame);
			if (model != null)
			{
				swimmer.fish.setModel(model);
				swimmer.pitch = pitch;
				swimmer.frame = frame;
			}
		}
	}

	/**
	 * After a map load, which drops the fish from the scene and may move where in it the spots are: puts each spot's
	 * fish back, just as they were, about where the spot now is in the scene, matching spots to their fish by where
	 * they are in the world; and takes away the fish of any spot no longer in sight. Spots with none are left for
	 * adding as usual.
	 */
	void reload(Iterable<? extends NPC> spots)
	{
		Map<NPC, School> kept = new HashMap<>();
		for (NPC spot : spots)
		{
			LocalPoint at = spot.getLocalLocation();
			if (at == null || !SPOT_FISH.containsKey(spot.getId()))
			{
				continue;
			}
			School school = null;
			for (Map.Entry<NPC, School> entry : schools.entrySet())
			{
				if (entry.getValue().spot.equals(spot.getWorldLocation()) && entry.getKey().getId() == spot.getId())
				{
					school = entry.getValue();
					break;
				}
			}
			if (school == null || kept.containsValue(school))
			{
				continue;
			}
			int plane = spot.getWorldLocation().getPlane();
			school.x = at.getX();
			school.y = at.getY();
			school.z = Perspective.getTileHeight(client, at, plane);
			for (Swimmer swimmer : school.fish)
			{
				swimmer.fish.setActive(false);
				swimmer.fish.setLocation(at, plane);
				place(school, swimmer, 0, 0, client.getGameCycle());
				swimmer.fish.setActive(true);
			}
			kept.put(spot, school);
		}
		for (School school : schools.values())
		{
			if (!kept.containsValue(school))
			{
				school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
			}
		}
		schools.clear();
		schools.putAll(kept);
	}

	/**
	 * Takes every fish away and lets the models go, as when switched off.
	 */
	void clear()
	{
		for (School school : schools.values())
		{
			school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
		}
		schools.clear();
		models.clear();
	}
}
