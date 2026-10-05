package com.livelyfishingspots;

import com.livelyfishingspots.FishModels.Look;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
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

	// The lane spacing, in local units (128 to a tile), that each spot's own is measured against: its fish keep gaps
	// and make room in proportion to its spacing against this. SCALE sizes the speeds and the gaps the fish keep,
	// against the size they were first set for.
	private static final double LANE_SPACING = 45;
	private static final double SCALE = 256.0 / 352;
	// How much of a lane each fish takes, in merging gaps, so a lane holds more the bigger round it is, with room to
	// spare for others to join it; and how far either way a lane may stray from its even place, which keeps a clear
	// gap to the next.
	private static final double LANE_ROOM = 1.5;
	private static final double LANE_STRAY = 4 * SCALE;

	/**
	 * How crowded one spot's shoal is: how many lanes it has, how many fish, how far apart its lanes are, and how far
	 * out its outermost lane is, in local units; and, for the kinds kept out, how far outside the shoal's outermost
	 * lane its outer ring's first lane is, and how far apart the ring's lanes are, in local units. The gaps its
	 * fish keep from each other, and how far they make room, are in proportion to its lane spacing against
	 * LANE_SPACING, so small fish in narrow lanes keep small gaps.
	 */
	private static final class Crowd
	{
		private final int lanes;
		private final int count;
		private final double spacing;
		private final double radius;
		private final double outerGap;
		private final double outerSpacing;

		private Crowd(int[] values)
		{
			lanes = values[0];
			count = values[1];
			spacing = values[2];
			radius = values[3];
			outerGap = values[4];
			outerSpacing = values[5];
		}
	}

	// Each spot's crowd, by its NPC, in the order of its values: lanes, count, spacing, radius, outerGap,
	// outerSpacing. Every spot with fish has one.
	private static final Map<Integer, Crowd> CROWDS = Map.of(
		NpcID.FISHING_BOAT_SALTFISH, new Crowd(new int[]{10, 25, 45, 270, 64, 64}),
		NpcID.FISHING_BOAT_MEMBERFISH, new Crowd(new int[]{7, 27, 39, 270, -76, 87}),
		NpcID.FISHING_BOAT_PISCARILIUSFISH, new Crowd(new int[]{6, 13, 44, 275, 64, 64}),
		NpcID.FISHING_BOAT_MONKFISH, new Crowd(new int[]{5, 14, 57, 308, 64, 64}),
		NpcID.FISHING_BOAT_RAREFISH, new Crowd(new int[]{7, 23, 38, 294, 64, 64}),
		NpcID.FISHING_BOAT_KARAMBWANFISH, new Crowd(new int[]{5, 12, 46, 287, 64, 64}));
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
	// How much of the way from how fast a fish is swimming to how fast it has decided to it eases a client tick.
	private static final double SPEED_EASE = 0.05;
	// How quick, a client tick, the spring a fish eases aside from others by is, at a room ease of 100%.
	private static final double ROOM_SPRING = 0.35;
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
	// A fish tips nose down as it sinks and nose up as it rises: by up to DIP_PITCH degrees in a dip, at its
	// steepest, and BOB_PITCH in its bob, its model made at each PITCH_STEP degrees of that, as needed.
	private static final double DIP_PITCH = 20;
	private static final double BOB_PITCH = 8;
	// How much smaller, in percent, each size of a kind of fish is than the last, up to its size spread.
	private static final int SIZE_STEP = 4;
	// How many times a second the fish decide how fast to swim, how far to ease aside and whether to change lanes or
	// dip, up to 50, one for each client tick; between, they carry on as last decided, still moved every tick.
	private static final double DECISIONS_PER_SECOND = 20;
	// The kinds that swim under the water, unseen where it isn't drawn see-through.
	private static final Set<Integer> UNDER_WATER = Set.of(ItemID.RAW_LOBSTER, ItemID.RAW_SHRIMP,
		ItemID.RAW_ANCHOVIES);
	// The kinds kept out of a shoal's innermost lanes, too long to circle so tight: how many of them.
	private static final Map<Integer, Integer> KEPT_FROM_MIDDLE = Map.of(ItemID.RAW_SWORDFISH, 2,
		ItemID.RAW_BASS, 1, ItemID.RAW_COD, 1);
	// The kinds that give each other more room: how many, at most, share a lane, and how many lengths, rather than
	// FOLLOW_LENGTHS, they keep from any fish ahead and need clear to join a lane.
	private static final Map<Integer, Integer> MOST_IN_LANE = Map.of(ItemID.RAW_SWORDFISH, 2);
	private static final Map<Integer, Double> GAP_LENGTHS = Map.of(ItemID.RAW_SWORDFISH, 2.5, ItemID.RAW_SHARK, 4.0);
	// The kinds swimming in a layer of their own, by its number, as lobsters do below the other fish.
	private static final Map<Integer, Integer> LAYERS = Map.of(ItemID.RAW_LOBSTER, 1);
	// The kinds every other fish makes room for, whatever its layer, while they make room only for their own layer.
	private static final Set<Integer> GIVEN_ROOM = Set.of(ItemID.RAW_SHARK);
	// How far, at most, a fish eases away from one of those, and from how far off, as a kind's room and room range
	// are, in local units at LANE_SPACING.
	private static final double SHARK_ROOM = 50;
	private static final double SHARK_ROOM_RANGE = 160;
	// How fast, at most, a fish eases aside, in local units a second: from other fish, and apart from that, from one
	// of those, slower, so it drifts aside.
	private static final double MOST_ROOM_SPEED = 300;
	private static final double SHARK_ROOM_SPEED = 60;
	// The kinds with a depth range that keep this many of a spot's fish, at least, at their shallowest, no deeper
	// than their sink.
	private static final Map<Integer, Integer> SHALLOW_LEAST = Map.of(
		ItemID.TBWT_RAW_KARAMBWAN, 4, ItemID.RAW_MONKFISH, 7, ItemID.RAW_ANGLERFISH, 7);
	// And how much deeper, at least, than their sink those not at their shallowest sit, in local units.
	private static final Map<Integer, Integer> LEAST_DEEPER = Map.of(
		ItemID.TBWT_RAW_KARAMBWAN, 50, ItemID.RAW_MONKFISH, 50, ItemID.RAW_ANGLERFISH, 50);
	// Whether fish of a kind vary in size, by its size spread, each size taking a set of models of its own. Off, to
	// keep the models few; each kind keeps its spread for when it's wanted again.
	private static final boolean SIZE_VARIATION = false;

	// The kinds kept few and in a ring of their own outside a shoal, never among the other fish, each always in the
	// lane it starts in: how many of them in each of the ring's lanes, innermost first, the ring having as many lanes.
	private static final Map<Integer, int[]> KEPT_OUT = Map.of(
		ItemID.RAW_SHARK, new int[]{1, 2});
	// The most of all kinds kept out a spot can have, for keeping room for them.
	private static final int MOST_KEPT_OUT = KEPT_OUT.values().stream().flatMapToInt(Arrays::stream).sum();

	private final Client client;
	private final FishModels models;
	private final Map<NPC, School> schools = new HashMap<>();
	// Whether the water is drawn see-through, as 117 HD draws it, so fish under it can be seen; the game's own
	// renderers draw it solid.
	private boolean seeThrough;

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
		private final List<Swimmer> fish = new ArrayList<>();
		// How fast each is swimming this tick, kept so a new list isn't made every tick.
		private final double[] speeds;
		// And how far each wants to ease away from the fish near it this tick, and of that, from those every fish
		// makes room for.
		private final double[] room;
		private final double[] sharkRoom;
		// And, reused each tick, each fish's speed before minding the fish ahead, and its place across the shoal.
		private final double[] own;
		private final double[] placeX;
		private final double[] placeY;
		private int movedAt;
		// The client tick its fish last decided at, and the one, or part of one, they next do at.
		private int decidedAt;
		private double decideDue;

		private School(WorldPoint spot, int x, int y, int z, Crowd crowd, int ringLanes, int cycle)
		{
			this.spot = spot;
			this.x = x;
			this.y = y;
			this.z = z;
			mainLanes = crowd.lanes;
			lanes = new double[crowd.lanes + ringLanes];
			// Lanes too many or too wide to fit are drawn in closer, so the innermost stays clear of the middle.
			outer = crowd.radius;
			double spacing = Math.min(crowd.spacing, (outer - LANE_SPACING / 2) / (crowd.lanes - 1));
			inner = outer - spacing * (crowd.lanes - 1);
			gaps = spacing / LANE_SPACING;
			speeds = new double[crowd.count + MOST_KEPT_OUT];
			room = new double[crowd.count + MOST_KEPT_OUT];
			sharkRoom = new double[room.length];
			own = new double[room.length];
			placeX = new double[room.length];
			placeY = new double[room.length];
			movedAt = cycle;
			decidedAt = cycle;
		}

		private double spacing()
		{
			return (outer - inner) / (mainLanes - 1);
		}
	}

	private static final class Swimmer
	{
		private final RuneLiteObject fish;
		// Which kind it is, by its item.
		private final int item;
		// Its kind's look, its kind's layer, and how many lengths it keeps from others, kept as they never change, so
		// they aren't looked up every tick.
		private Look look;
		private int layer;
		// Whether every other fish makes room for it, its kind being one of GIVEN_ROOM.
		private boolean givenRoom;
		private double gapLengths;
		// How far it swayed out from its lane at the last client tick it was moved, in local units.
		private double swayed;
		// Its size, in percent, and how far its model is tipped now, in PITCH_STEPs, nose up when more than 0.
		private final int size;
		private int pitch;
		// Which of its arms' wiggle frames it shows, and how far into its wiggle it starts, in client ticks.
		private int frame;
		private int wiggleStart;
		// Whether its arms wiggle at all, kept so it isn't worked out every tick.
		private boolean wiggles;
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
		// How far it has eased away from the fish near it, out from the middle, or in when less than 0, and of that,
		// from those every fish makes room for, eased at its own speed.
		private double eased;
		private double sharkEased;
		// How fast it is easing aside from other fish, in local units a client tick.
		private double roomSpeed;
		// How fast it is swimming, in local units a client tick, eased towards how fast it decided to, or -1 before
		// its first move.
		private double swimming = -1;
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

	SeaSpotFish(Client client, FishModels models)
	{
		this.client = client;
		this.models = models;
	}

	/**
	 * Starts fish swimming at a spot that has come into sight, if it is one of the spots at sea: its crowd's count of
	 * them shared out among the lanes around its middle.
	 */
	void add(NPC spot)
	{
		int[] kinds = SPOT_FISH.get(spot.getId());
		Crowd crowd = CROWDS.get(spot.getId());
		if (kinds == null || crowd == null || schools.containsKey(spot))
		{
			return;
		}
		LocalPoint at = spot.getLocalLocation();
		if (at == null)
		{
			return;
		}
		int[] odds = SPOT_FISH_ODDS.get(spot.getId());
		int[] least = SPOT_FISH_LEAST.get(spot.getId());
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int cycle = client.getGameCycle();
		int plane = spot.getWorldLocation().getPlane();
		// The outer ring has as many lanes as the kinds kept out at the spot fill, none without them.
		int ringLanes = Arrays.stream(kinds).filter(KEPT_OUT::containsKey).map(item -> KEPT_OUT.get(item).length)
			.max().orElse(0);
		School school = new School(spot.getWorldLocation(), at.getX(), at.getY(),
			Perspective.getTileHeight(client, at, plane), crowd, ringLanes, cycle);
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
		// Then each kind kept out, as many in each of the outer ring's lanes as it says, evenly round the lane, each
		// lane further out turned on by half the gap between its fish, so those in lanes side by side don't start
		// side by side.
		for (int item : kinds)
		{
			int[] perLane = KEPT_OUT.get(item);
			if (perLane == null)
			{
				continue;
			}
			double turned = random.nextDouble(2 * Math.PI);
			for (int ring = 0; ring < perLane.length; ring++)
			{
				for (int place = 0; place < perLane[ring]; place++)
				{
					double angle = turned + (place + ring / 2.0) * 2 * Math.PI / perLane[ring]
						+ random.nextDouble(-START_STRAY, START_STRAY);
					if (!addFish(school, item, lanes + ring, angle, at, plane, cycle, random))
					{
						return;
					}
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
		// Where the water hides what is under it, fish still deeper than their kind, once those kept at their
		// shallowest are, are taken out, there being nothing of them to see.
		if (!seeThrough)
		{
			school.fish.removeIf(swimmer -> swimmer.deeper > 0);
		}
		for (Swimmer swimmer : school.fish)
		{
			place(school, swimmer, 0, 0, cycle);
			swimmer.fish.setActive(true);
		}
		schools.put(spot, school);
		queueAll(school);
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
	 * Whether a fish may move into a lane: one of the main lanes, never the outer ring's, and none of the innermost
	 * its kind is kept from. The kinds kept out never move, so never ask.
	 */
	private static boolean allowed(int item, int lane, School school)
	{
		return lane < school.mainLanes && lane >= KEPT_FROM_MIDDLE.getOrDefault(item, 0);
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
		int size = !SIZE_VARIATION ? FishModels.look(item).size
			: FishModels.look(item).size * (100 - SIZE_STEP * random.nextInt(FishModels.look(item).sizeSpread / SIZE_STEP + 1)) / 100;
		Model model = models.model(item, size, 0, 0);
		if (model == null)
		{
			school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
			return false;
		}
		// Where the water hides what is under it, the kinds that swim under it aren't put in at all, there being
		// nothing of them to see; fish put deeper than their kind are taken out once the shoal is laid out.
		if (!seeThrough && UNDER_WATER.contains(item))
		{
			return true;
		}
		int deeper = deeper(item, random);
		RuneLiteObject fish = client.createRuneLiteObject();
		fish.setModel(model);
		fish.setLocation(at, plane);
		Swimmer swimmer = new Swimmer(fish, item, size, lane, angle, school.lanes[lane], cycle, random);
		swimmer.look = FishModels.look(item);
		swimmer.layer = layer(item);
		swimmer.givenRoom = GIVEN_ROOM.contains(item);
		swimmer.gapLengths = GAP_LENGTHS.getOrDefault(item, FOLLOW_LENGTHS);
		swimmer.swayed = sway(swimmer, cycle);
		swimmer.wiggleStart = random.nextInt(50 * FishModels.WIGGLE_FRAMES);
		swimmer.wiggles = FishModels.RESHAPED.contains(item) && swimmer.look.wiggle > 0 && swimmer.look.sweep > 0;
		swimmer.deeper = deeper;
		int points = model.getVerticesCount();
		swimmer.length = Math.max(FishModels.spread(model.getVerticesX(), points), FishModels.spread(model.getVerticesZ(), points));
		school.fish.add(swimmer);
		return true;
	}

	/**
	 * How much deeper than its kind's sink a new fish sits: a random depth within its kind's range, but never only a
	 * little deeper, under the least its kind allows other than none, so none of it just pokes out of the water.
	 */
	private int deeper(int item, ThreadLocalRandom random)
	{
		int range = FishModels.look(item).depthRange;
		int least = LEAST_DEEPER.getOrDefault(item, 0);
		int deeper = range > 0 ? random.nextInt(range + 1) : 0;
		if (deeper > 0 && deeper < least)
		{
			deeper = range < least ? 0 : random.nextInt(least, range + 1);
		}
		return deeper;
	}

	/**
	 * Queues every tip and frame a shoal's fish may come to need, beyond the upright ones they start with, to be made
	 * a few at a time: as far as each kind tips at most, both ways, and each of its wiggle frames.
	 */
	private void queueAll(School school)
	{
		for (Swimmer swimmer : school.fish)
		{
			Look look = swimmer.look;
			double most = look.tip / 100.0 * ((look.rise > 0 ? BOB_PITCH : 0) + (look.dipDepth > 0 ? DIP_PITCH : 0));
			int steps = (int) Math.ceil(most / FishModels.PITCH_STEP);
			int frames = swimmer.wiggles ? FishModels.WIGGLE_FRAMES : 1;
			for (int pitch = -steps; pitch <= steps; pitch++)
			{
				for (int frame = 0; frame < frames; frame++)
				{
					models.queue(swimmer.item, swimmer.size, pitch, frame, false);
				}
			}
		}
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
	}

	/**
	 * Adds the kinds swimming at the spots in sight.
	 */
	void addKinds(Set<Integer> swimming)
	{
		for (School school : schools.values())
		{
			school.fish.forEach(swimmer -> swimming.add(swimmer.item));
		}
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
			// DECISIONS_PER_SECOND times a second, at the client tick nearest each, the fish decide how fast to swim
			// and how far to ease aside, the costly part, comparing every pair; every tick they move on as decided.
			boolean deciding = cycle >= school.decideDue;
			int decided = Math.min(10, cycle - school.decidedAt);
			if (deciding)
			{
				school.decideDue = Math.max(school.decideDue + 50.0 / DECISIONS_PER_SECOND, cycle);
				school.decidedAt = cycle;
				for (int i = 0; i < count; i++)
				{
					speeds[i] = speed(school, school.fish.get(i), cycle, school.inner);
				}
				follow(school);
				makeRoom(school);
			}
			for (int i = 0; i < count; i++)
			{
				Swimmer swimmer = school.fish.get(i);
				double was = swimmer.radius + swimmer.swayed + swimmer.eased;
				double share = Math.min(1, swimmer.look.roomEase / 100.0 * ticks);
				double fishEased = swimmer.eased - swimmer.sharkEased;
				// Eased like a spring settling without overshooting, its kind's room ease setting how quick, so it
				// gathers speed and slows into place rather than starting and stopping dead; never faster than the
				// most room speed.
				double spring = swimmer.look.roomEase / 100.0 * ROOM_SPRING;
				double most = MOST_ROOM_SPEED / 50;
				for (int t = 0; t < ticks; t++)
				{
					swimmer.roomSpeed += spring * spring * (school.room[i] - fishEased)
						- 2 * spring * swimmer.roomSpeed;
					swimmer.roomSpeed = Math.max(-most, Math.min(most, swimmer.roomSpeed));
					fishEased += swimmer.roomSpeed;
				}
				double mostShark = SHARK_ROOM_SPEED / 50 * ticks;
				swimmer.sharkEased += Math.max(-mostShark,
					Math.min(mostShark, (school.sharkRoom[i] - swimmer.sharkEased) * share));
				swimmer.eased = fishEased + swimmer.sharkEased;
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
				// Its speed eases towards the one decided, so it speeds up and slows down smoothly rather than in
				// steps; a fish just made starts at it.
				swimmer.swimming = swimmer.swimming < 0 ? speeds[i]
					: swimmer.swimming + (speeds[i] - swimmer.swimming) * Math.min(1, SPEED_EASE * ticks);
				double moved = swimmer.swimming * ticks;
				swimmer.angle = (swimmer.angle + moved / swimmer.radius) % (2 * Math.PI);
				swimmer.wag = (swimmer.wag + 2 * Math.PI * moved / WAG_DISTANCE) % (2 * Math.PI);
				swimmer.swayed = sway(swimmer, cycle);
				double outward = (swimmer.radius + swimmer.swayed + swimmer.eased - was) / ticks;
				place(school, swimmer, swimmer.swimming, outward, cycle);
			}
			if (!deciding)
			{
				continue;
			}
			for (Swimmer swimmer : school.fish)
			{
				think(school, swimmer, cycle, random, SHARK_ROOM_RANGE * school.gaps);
				// Seconds are 50 client ticks, each 20 ms. A fish put deeper than its kind never dips, being hard to
				// see moving down there.
				Look look = swimmer.look;
				if (swimmer.dippingSince >= 0 ? cycle - swimmer.dippingSince >= Math.max(1, look.dipMillis / 20)
					: swimmer.deeper == 0 && random.nextDouble() < Math.max(1, decided) / (look.dipEvery * 50.0))
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
		double speed = REFERENCE_SPEED * lane * swimmer.speed * swimmer.look.pace / 100.0
			* (1 + (INNER_SURGE + (OUTER_SURGE - INNER_SURGE) * out) * swimmer.look.surge / 100.0 * surging);
		return speed;
	}

	/**
	 * Slows each fish closing on the one ahead of it in its lane: nearer than the gap they keep, it swims no faster
	 * than that one, less the nearer it is, so it can never close the gap, only drift back.
	 */
	private static void follow(School school)
	{
		int count = school.fish.size();
		double[] own = school.own;
		System.arraycopy(school.speeds, 0, own, 0, count);
		for (int i = 0; i < count; i++)
		{
			Swimmer swimmer = school.fish.get(i);
			int nearest = -1;
			double ahead = Double.MAX_VALUE;
			for (int j = 0; j < count; j++)
			{
				Swimmer other = school.fish.get(j);
				if (j != i && other.lane == swimmer.lane && other.layer == swimmer.layer)
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
			double keep = Math.max(KEEP_GAP * school.gaps,
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
	 * leave it in turn. The kinds kept out never move.
	 */
	private static void think(School school, Swimmer swimmer, int cycle, ThreadLocalRandom random, double sharkRange)
	{
		// The kinds kept out keep the lane they start in.
		if (swimmer.movingSince >= 0 || KEPT_OUT.containsKey(swimmer.item))
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
				|| !clear(school, swimmer, lane, sharkRange))
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
	 * Whether a fish could join a lane where it is: the lane has room for another, no fish in it, or still leaving
	 * it, is within MERGE_GAP ahead or behind, and none every fish makes room for is within the range they make it
	 * from. The kinds kept out never change lanes, so never ask.
	 */
	private static boolean clear(School school, Swimmer swimmer, int lane, double sharkRange)
	{
		int in = 0;
		double out = swimmer.radius + swimmer.eased;
		for (Swimmer other : school.fish)
		{
			// Never while one every fish makes room for is near enough to push it aside, which would hold it off its
			// new lane until that one had passed, then slide it over all at once.
			if (other.givenRoom)
			{
				double otherOut = other.radius + other.eased;
				if (Math.hypot(out * Math.sin(swimmer.angle) - otherOut * Math.sin(other.angle),
					out * Math.cos(swimmer.angle) - otherOut * Math.cos(other.angle)) < sharkRange)
				{
					return false;
				}
			}
			boolean there = other.lane == lane;
			boolean leaving = other.movingSince >= 0 && other.movedFrom == school.lanes[lane];
			if (other == swimmer || !there && !leaving || other.layer != swimmer.layer)
			{
				continue;
			}
			in += there ? 1 : 0;
			double turn = Math.abs(Math.IEEEremainder(other.angle - swimmer.angle, 2 * Math.PI));
			if (turn * school.lanes[lane] < Math.max(MERGE_GAP * school.gaps,
				lengths(swimmer, other) * (swimmer.length + other.length) / 2))
			{
				return false;
			}
		}
		return in < room(school, lane)
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
		return Math.max(one.gapLengths, other.gapLengths);
	}

	/**
	 * How many fish a lane holds: as many as fit round it with LANE_ROOM merging gaps each, and always one.
	 */
	private static int room(School school, int lane)
	{
		return Math.max(1,
			(int) (2 * Math.PI * school.lanes[lane] / (LANE_ROOM * MERGE_GAP * school.gaps)));
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
			double out = swimmer.radius + swimmer.eased;
			school.placeX[i] = out * Math.sin(swimmer.angle);
			school.placeY[i] = out * Math.cos(swimmer.angle);
		}
		for (int i = 0; i < count; i++)
		{
			Swimmer swimmer = school.fish.get(i);
			Look look = swimmer.look;
			double roomRange = look.roomRange * school.gaps;
			double room = look.room * school.gaps;
			double out = swimmer.radius + swimmer.eased;
			double want = 0;
			double wantShark = 0;
			double mostShark = 0;
			for (int j = 0; j < count; j++)
			{
				Swimmer other = school.fish.get(j);
				// Fish in different layers pay each other no mind, but for those every fish makes room for, as the
				// sharks, which in turn make room only for their own layer.
				if (j == i || other.layer != swimmer.layer && !(other.givenRoom && !swimmer.givenRoom))
				{
					continue;
				}
				// One every fish makes room for is given more of it, from further off. Squared distances first, so
				// the square root is only taken for those near enough.
				boolean givingWay = other.givenRoom && !swimmer.givenRoom;
				double range = givingWay ? SHARK_ROOM_RANGE * school.gaps : roomRange;
				double dx = school.placeX[i] - school.placeX[j];
				double dy = school.placeY[i] - school.placeY[j];
				double apartSquared = dx * dx + dy * dy;
				if (apartSquared >= range * range)
				{
					continue;
				}
				double apart = Math.sqrt(apartSquared);
				double otherOut = other.radius + other.eased;
				double give = givingWay ? SHARK_ROOM * school.gaps : room;
				// Out if it is the further out of the two, in if not; two as far out as each other part by order.
				double way = Math.abs(out - otherOut) > 1 ? Math.signum(out - otherOut) : i < j ? -1 : 1;
				if (givingWay)
				{
					wantShark += way * give * (1 - apart / range);
					mostShark = give;
				}
				else
				{
					want += way * give * (1 - apart / range);
				}
			}
			school.room[i] = Math.max(-room, Math.min(room, want));
			school.sharkRoom[i] = Math.max(-mostShark, Math.min(mostShark, wantShark));
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
		Look look = swimmer.look;
		double out = swimmer.radius + swimmer.swayed + swimmer.eased;
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
		// A bob rises and settles back smoothly, then holds still at rest a while; -1 while holding, as a fish put
		// deeper than its kind always is, being hard to see moving down there.
		int bobbing = (cycle + swimmer.bobPhase) % (swimmer.bob + swimmer.bobRest);
		int bobAt = swimmer.deeper == 0 && bobbing < swimmer.bob ? bobbing * 2048 / swimmer.bob : -1;
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
		int pitch = (int) Math.round(tip / FishModels.PITCH_STEP);
		// Arms that wiggle step through their frames at their kind's rate, a second being 50 client ticks.
		int frame = swimmer.wiggles
			? (int) ((long) (cycle + swimmer.wiggleStart) * look.wiggleRate / 50 % FishModels.WIGGLE_FRAMES) : 0;
		if (pitch != swimmer.pitch || frame != swimmer.frame)
		{
			// Only a model already made: one not made yet is put first in line, and the fish keeps the one it has
			// until it is, a tick or two at most.
			Model model = models.made(swimmer.item, swimmer.size, pitch, frame);
			if (model != null)
			{
				swimmer.fish.setModel(model);
				swimmer.pitch = pitch;
				swimmer.frame = frame;
			}
			else
			{
				models.queue(swimmer.item, swimmer.size, pitch, frame, true);
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
	 * Says whether the water is drawn see-through, for the shoals made from now on.
	 */
	void setSeeThrough(boolean seeThrough)
	{
		this.seeThrough = seeThrough;
	}

	/**
	 * Takes every fish away, as when switched off.
	 */
	void clear()
	{
		for (School school : schools.values())
		{
			school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
		}
		schools.clear();
	}
}
