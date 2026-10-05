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
 * Fish circling each sea fishing spot, as a shoal. Visual only: no clickbox or menu options.
 */
final class SeaSpotFish
{
	// Each spot's fish, by NPC id.
	private static final Map<Integer, int[]> SPOT_FISH = Map.of(
		NpcID.FISHING_BOAT_SALTFISH,
		new int[]{ItemID.RAW_SHRIMP, ItemID.RAW_ANCHOVIES, ItemID.RAW_SARDINE, ItemID.RAW_HERRING},
		NpcID.FISHING_BOAT_MEMBERFISH,
		new int[]{ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS, ItemID.RAW_SHARK},
		NpcID.FISHING_BOAT_RAREFISH, new int[]{ItemID.RAW_LOBSTER, ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH},
		NpcID.FISHING_BOAT_KARAMBWANFISH, new int[]{ItemID.TBWT_RAW_KARAMBWAN},
		NpcID.FISHING_BOAT_PISCARILIUSFISH, new int[]{ItemID.RAW_ANGLERFISH},
		NpcID.FISHING_BOAT_MONKFISH, new int[]{ItemID.RAW_MONKFISH});
	// Relative odds of each fish, for spots not split evenly.
	private static final Map<Integer, int[]> SPOT_FISH_ODDS = Map.of(
		NpcID.FISHING_BOAT_SALTFISH, new int[]{8, 8, 42, 42},
		NpcID.FISHING_BOAT_RAREFISH, new int[]{30, 85, 85});
	// Fewest of each fish a spot always has.
	private static final Map<Integer, int[]> SPOT_FISH_LEAST = Map.of(
		NpcID.FISHING_BOAT_RAREFISH, new int[]{5, 5, 5});

	// Reference lane spacing, local units: gaps and room scale with a spot's spacing against it. SCALE
	// scales speeds and gaps from the size they were tuned at.
	private static final double LANE_SPACING = 45;
	private static final double SCALE = 256.0 / 352;
	// Lane capacity, in merge gaps per fish; how far a lane may stray from even spacing.
	private static final double LANE_ROOM = 1.5;
	private static final double LANE_STRAY = 4 * SCALE;

	/**
	 * A spot's shoal size: lanes, fish, lane spacing, outer radius (local units), and for kept-out kinds
	 * the outer ring's gap and spacing. Gaps and room scale with spacing against LANE_SPACING.
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

	// Each spot's crowd, by NPC id: lanes, count, spacing, radius, outerGap, outerSpacing.
	private static final Map<Integer, Crowd> CROWDS = Map.of(
		NpcID.FISHING_BOAT_SALTFISH, new Crowd(new int[]{10, 25, 45, 270, 64, 64}),
		NpcID.FISHING_BOAT_MEMBERFISH, new Crowd(new int[]{7, 27, 39, 270, -76, 87}),
		NpcID.FISHING_BOAT_PISCARILIUSFISH, new Crowd(new int[]{6, 13, 44, 275, 64, 64}),
		NpcID.FISHING_BOAT_MONKFISH, new Crowd(new int[]{5, 14, 57, 308, 64, 64}),
		NpcID.FISHING_BOAT_RAREFISH, new Crowd(new int[]{7, 23, 38, 294, 64, 64}),
		NpcID.FISHING_BOAT_KARAMBWANFISH, new Crowd(new int[]{5, 12, 46, 287, 64, 64}));
	// Sway in and out of the lane, local units, from two swings with random periods (client ticks).
	private static final double SWAY = 10 * SCALE;
	private static final int MIN_SWAY_CYCLES = 120;
	private static final int MAX_SWAY_CYCLES = 360;
	// Most a fish turns in or out while changing lanes (radians), and how quickly it eases there.
	private static final double MAX_DRIFT = 0.35;
	private static final double DRIFT_EASE = 0.1;
	// Speeds in local units per client tick: REFERENCE_SPEED at the middle lane, scaled from INNER_SPEED
	// to OUTER_SPEED; per-fish spread (percent); surge from INNER_SURGE to OUTER_SURGE over a random
	// period.
	private static final double REFERENCE_SPEED = 2 * Math.PI / 1.4 * SCALE;
	private static final double INNER_SPEED = 0.7;
	// The innermost lane's speed, as a share of the middle lane's.
	private static final double INNERMOST_SPEED = 0.4;
	private static final double OUTER_SPEED = 1.3;
	private static final int SPEED_SPREAD = 8;
	private static final double INNER_SURGE = 0.1;
	private static final double OUTER_SURGE = 0.3;
	private static final int MIN_SURGE_CYCLES = 200;
	private static final int MAX_SURGE_CYCLES = 400;
	// Distance round the lane at which a fish slows behind the one ahead, local units.
	private static final double KEEP_GAP = 192 * SCALE;
	// Fish lengths kept behind the one ahead, and needed clear to join a lane.
	private static final double FOLLOW_LENGTHS = 1.5;
	// Lane changes: think interval (client ticks), clear water needed (local units), move time, and how
	// long a displaced fish looks for a gap to leave by.
	private static final int MIN_THINK_CYCLES = 300;
	private static final int MAX_THINK_CYCLES = 900;
	private static final double MERGE_GAP = 192 * SCALE;
	private static final int MERGE_CYCLES = 100;
	private static final int LEAVE_CYCLES = 500;
	// How quickly a fish eases to its decided speed, per client tick.
	private static final double SPEED_EASE = 0.05;
	// Make-room spring strength per client tick at 100% room ease.
	private static final double ROOM_SPRING = 0.35;
	// Random start offset round the lane, radians.
	private static final double START_STRAY = 0.2;
	// Bob and rest durations, client ticks, random between these.
	private static final int MIN_BOB_CYCLES = 70;
	private static final int MAX_BOB_CYCLES = 150;
	private static final int MIN_BOB_REST_CYCLES = 20;
	private static final int MAX_BOB_REST_CYCLES = 60;
	// Tail wag in 2048ths of a turn at middle-lane speed (less when slower), and local units swum per
	// wag.
	private static final int WAG = 20;
	private static final double WAG_DISTANCE = 100 * SCALE;
	// Most tip in degrees, in a dip and in a bob.
	private static final double DIP_PITCH = 20;
	private static final double BOB_PITCH = 8;
	// Percent smaller per size step, within a kind's size spread.
	private static final int SIZE_STEP = 4;
	// Decisions per second (speed, room, lane changes, dips); fish still move every tick.
	private static final double DECISIONS_PER_SECOND = 20;
	// Kinds that swim under the water, invisible unless it's see-through.
	private static final Set<Integer> UNDER_WATER = Set.of(ItemID.RAW_LOBSTER, ItemID.RAW_SHRIMP,
		ItemID.RAW_ANCHOVIES);
	// Kinds kept out of the innermost lanes, and how many lanes.
	private static final Map<Integer, Integer> KEPT_FROM_MIDDLE = Map.of(ItemID.RAW_SWORDFISH, 2,
		ItemID.RAW_BASS, 1, ItemID.RAW_COD, 1);
	// Kinds that give more room: most per lane, and lengths kept instead of FOLLOW_LENGTHS.
	private static final Map<Integer, Integer> MOST_IN_LANE = Map.of(ItemID.RAW_SWORDFISH, 2);
	private static final Map<Integer, Double> GAP_LENGTHS = Map.of(ItemID.RAW_SWORDFISH, 2.5, ItemID.RAW_SHARK, 4.0);
	// Kinds in their own layer, like lobsters below the rest.
	private static final Map<Integer, Integer> LAYERS = Map.of(ItemID.RAW_LOBSTER, 1);
	// Kinds everyone makes room for; they make room only within their own layer.
	private static final Set<Integer> GIVEN_ROOM = Set.of(ItemID.RAW_SHARK);
	// Room given to those, and from how far, in local units at LANE_SPACING.
	private static final double SHARK_ROOM = 50;
	private static final double SHARK_ROOM_RANGE = 160;
	// Most room easing speed, local units a second: from other fish, and (slower) from given-room kinds.
	private static final double MOST_ROOM_SPEED = 300;
	private static final double SHARK_ROOM_SPEED = 60;
	// Kinds with a depth range that keep at least this many fish at their shallowest.
	private static final Map<Integer, Integer> SHALLOW_LEAST = Map.of(
		ItemID.TBWT_RAW_KARAMBWAN, 4, ItemID.RAW_MONKFISH, 7, ItemID.RAW_ANGLERFISH, 7);
	// Least extra depth for those not at their shallowest, local units.
	private static final Map<Integer, Integer> LEAST_DEEPER = Map.of(
		ItemID.TBWT_RAW_KARAMBWAN, 50, ItemID.RAW_MONKFISH, 50, ItemID.RAW_ANGLERFISH, 50);
	// Size variation per fish; off to keep the model count down.
	private static final boolean SIZE_VARIATION = false;

	// Kinds kept apart in an outer ring, never changing lanes: how many per ring lane, innermost first.
	private static final Map<Integer, int[]> KEPT_OUT = Map.of(
		ItemID.RAW_SHARK, new int[]{1, 2});
	// Most kept-out fish any spot has.
	private static final int MOST_KEPT_OUT = KEPT_OUT.values().stream().flatMapToInt(Arrays::stream).sum();

	private final Client client;
	private final FishModels models;
	private final Map<NPC, School> schools = new HashMap<>();
	// Whether the water is see-through (117 HD), so fish under it can be seen.
	private boolean seeThrough;

	/**
	 * The fish at one spot: its location, scene position and water height, lane radii, and its fish.
	 */
	private static final class School
	{
		private final WorldPoint spot;
		private int x;
		private int y;
		private int z;
		private final double[] lanes;
		// Main lanes; the rest are the outer ring's.
		private final int mainLanes;
		private final double outer;
		private final double inner;
		private final double gaps;
		private final List<Swimmer> fish = new ArrayList<>();
		// Reused each tick: decided speeds.
		private final double[] speeds;
		// Reused each tick: room wanted, and the part of it for given-room kinds.
		private final double[] room;
		private final double[] sharkRoom;
		// Reused each tick: speeds before following, and positions.
		private final double[] own;
		private final double[] placeX;
		private final double[] placeY;
		private int movedAt;
		// Last and next decision ticks.
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
			// Lanes that don't fit are squeezed in, keeping the innermost clear of the middle.
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
		private final int item;
		// Cached per kind: look, layer and gap lengths.
		private Look look;
		private int layer;
		// Whether others make room for it (GIVEN_ROOM).
		private boolean givenRoom;
		private double gapLengths;
		// Last sway, local units.
		private double swayed;
		// Size in percent; tip in PITCH_STEPs, nose up positive.
		private final int size;
		private int pitch;
		// Wiggle frame shown and start offset, client ticks.
		private int frame;
		private int wiggleStart;
		// Whether its arms wiggle.
		private boolean wiggles;
		// Extra depth below its kind's sink, local units.
		private int deeper;
		// Length nose to tail, local units.
		private double length;
		// Random speed share, surge and bob timings, so fish don't move in step.
		private final double speed;
		private final int surge;
		private final int surgePhase;
		private final int bob;
		private final int bobRest;
		private final int bobPhase;
		// The two sway periods and phases.
		private final int swayA;
		private final int swayAPhase;
		private final int swayB;
		private final int swayBPhase;
		// Lane, angle (radians), radius and lane-change start, tail wag phase, next think tick, and the
		// deadline to leave a lane another has joined (-1 if not).
		private int lane;
		private double angle;
		private double radius;
		private double movedFrom;
		private int movingSince = -1;
		private double wag;
		// Spin start, 2048ths of a turn.
		private final int spinPhase;
		private int thinkAt;
		private int leaveBy = -1;
		// Room eased so far (out positive), and the part from given-room kinds.
		private double eased;
		private double sharkEased;
		// Room easing speed, local units per client tick.
		private double roomSpeed;
		// Current speed, local units per client tick, or -1 before its first move.
		private double swimming = -1;
		// Turn in or out from the circle, radians, out positive.
		private double drift;
		// Dip start tick, or -1.
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
	 * Starts a shoal at a sea spot that came into sight, sharing its fish among its lanes.
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
		// Ring lanes for kept-out kinds, if any.
		int ringLanes = Arrays.stream(kinds).filter(KEPT_OUT::containsKey).map(item -> KEPT_OUT.get(item).length)
			.max().orElse(0);
		School school = new School(spot.getWorldLocation(), at.getX(), at.getY(),
			Perspective.getTileHeight(client, at, plane), crowd, ringLanes, cycle);
		int lanes = school.mainLanes;
		// Lane radii, then fish per lane: one each first, the rest weighted by room left.
		int[] chosen = chooseFish(kinds, odds, least, crowd.count, random);
		int[] counts = new int[lanes];
		for (int lane = 0; lane < lanes; lane++)
		{
			school.lanes[lane] = school.inner + school.spacing() * lane + random.nextDouble(-LANE_STRAY, LANE_STRAY);
		}
		// Outer ring lanes.
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
		// Outermost first, so kinds kept from the middle still find room.
		for (int lane = lanes - 1; lane >= 0; lane--)
		{
			double turned = random.nextDouble(2 * Math.PI);
			// Evenly round the lane.
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
		// Kept-out kinds in the ring, each ring lane offset by half a gap.
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
		// Bring enough of each depth-range kind up to their shallowest.
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
		// Without see-through water, drop fish that would be hidden.
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
	 * How many of each kind a spot's main lanes get: the minimums first, then by odds.
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
	 * Takes one of the chosen fish allowed in a lane; failing that, any kind allowed there.
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
	 * Whether a kind may be placed in a main lane during layout.
	 */
	private static boolean mayJoin(int item, int lane, School school)
	{
		return !KEPT_OUT.containsKey(item) && lane >= KEPT_FROM_MIDDLE.getOrDefault(item, 0)
			&& inLane(school, item, lane) < MOST_IN_LANE.getOrDefault(item, Integer.MAX_VALUE);
	}

	/**
	 * Whether a fish may move into a lane.
	 */
	private static boolean allowed(int item, int lane, School school)
	{
		return lane < school.mainLanes && lane >= KEPT_FROM_MIDDLE.getOrDefault(item, 0);
	}

	/**
	 * Picks a fish index by odds, or evenly.
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
	 * Adds a fish to a shoal. Returns false if its model couldn't be made.
	 */
	private boolean addFish(School school, int item, int lane, double angle, LocalPoint at, int plane, int cycle,
		ThreadLocalRandom random)
	{
		// Size steps only with SIZE_VARIATION.
		int size = !SIZE_VARIATION ? FishModels.look(item).size
			: FishModels.look(item).size * (100 - SIZE_STEP * random.nextInt(FishModels.look(item).sizeSpread / SIZE_STEP + 1)) / 100;
		Model model = models.model(item, size, 0, 0);
		if (model == null)
		{
			school.fish.forEach(swimmer -> swimmer.fish.setActive(false));
			return false;
		}
		// Without see-through water, skip under-water kinds.
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
	 * Random extra depth within a kind's range, never just barely below the surface.
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
	 * Queues every tip and wiggle frame a shoal's fish may need.
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
	 * Removes a spot's fish.
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
	 * Adds the kinds in sight, so their models are kept.
	 */
	void addKinds(Set<Integer> swimming)
	{
		for (School school : schools.values())
		{
			school.fish.forEach(swimmer -> swimming.add(swimmer.item));
		}
	}

	/**
	 * Moves every fish, each client tick.
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
			// Cap ticks so a stall doesn't jump fish far.
			int ticks = Math.min(10, cycle - school.movedAt);
			school.movedAt = cycle;
			if (ticks <= 0)
			{
				continue;
			}
			int count = school.fish.size();
			double[] speeds = school.speeds;
			// Decide DECISIONS_PER_SECOND times a second (the costly all-pairs part); move every tick.
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
				// Critically damped spring, capped at the most room speed.
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
				// Ease to the decided speed; new fish start at it.
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
				// 50 client ticks a second. Fish below their kind's depth never dip.
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
	 * A fish's unhindered speed: its lane's, its own share and its surge.
	 */
	private double speed(School school, Swimmer swimmer, int cycle, double inner)
	{
		double out = (swimmer.radius - inner) / (school.outer - inner);
		double surging = Math.sin(2 * Math.PI * ((cycle + swimmer.surgePhase) % swimmer.surge) / swimmer.surge);
		// Faster outward; the innermost lane slower still.
		double second = 1.0 / (school.mainLanes - 1);
		double lane = out >= second
			? INNER_SPEED + (OUTER_SPEED - INNER_SPEED) * out
			: INNERMOST_SPEED + (INNER_SPEED + (OUTER_SPEED - INNER_SPEED) * second - INNERMOST_SPEED) * out / second;
		double speed = REFERENCE_SPEED * lane * swimmer.speed * swimmer.look.pace / 100.0
			* (1 + (INNER_SURGE + (OUTER_SURGE - INNER_SURGE) * out) * swimmer.look.surge / 100.0 * surging);
		return speed;
	}

	/**
	 * Slows each fish closing on the one ahead in its lane so it never closes the gap.
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
	 * Moves a fish to a neighbouring lane when due and clear; one already there then looks to leave.
	 */
	private static void think(School school, Swimmer swimmer, int cycle, ThreadLocalRandom random, double sharkRange)
	{
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
	 * Whether a fish can join a lane where it is: room, clear gaps, and no given-room kind nearby.
	 */
	private static boolean clear(School school, Swimmer swimmer, int lane, double sharkRange)
	{
		int in = 0;
		double out = swimmer.radius + swimmer.eased;
		for (Swimmer other : school.fish)
		{
			// Not near a given-room kind, which would hold it off then shove it over.
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
	 * A kind's layer; layers ignore each other. Kept-out kinds are -1, LAYERS as listed, the rest 0.
	 */
	private static int layer(int item)
	{
		return LAYERS.getOrDefault(item, KEPT_OUT.containsKey(item) ? -1 : 0);
	}

	/**
	 * Fish of a kind in, or moving to, a lane.
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
	 * Lengths two fish keep apart: the larger of their kinds' gaps.
	 */
	private static double lengths(Swimmer one, Swimmer other)
	{
		return Math.max(one.gapLengths, other.gapLengths);
	}

	/**
	 * How many fish a lane holds, at least one.
	 */
	private static int room(School school, int lane)
	{
		return Math.max(1,
			(int) (2 * Math.PI * school.lanes[lane] / (LANE_ROOM * MERGE_GAP * school.gaps)));
	}

	/**
	 * Room each fish wants from those near it, in or out, scaled to the spot's lanes.
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
				// Different layers ignore each other, except given-room kinds.
				if (j == i || other.layer != swimmer.layer && !(other.givenRoom && !swimmer.givenRoom))
				{
					continue;
				}
				// Given-room kinds get more room from further off. Squared distances first.
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
				// Out if further out; level fish part by order.
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
	 * Sway from the lane at a tick, local units, in negative.
	 */
	private static double sway(Swimmer swimmer, int cycle)
	{
		double a = Math.sin(2 * Math.PI * ((cycle + swimmer.swayAPhase) % swimmer.swayA) / swimmer.swayA);
		double b = Math.sin(2 * Math.PI * ((cycle + swimmer.swayBPhase) % swimmer.swayB) / swimmer.swayB);
		return SWAY * (0.6 * a + 0.4 * b);
	}

	/**
	 * Places a fish given its speed round and out, local units per client tick.
	 */
	private void place(School school, Swimmer swimmer, double speed, double outward, int cycle)
	{
		Look look = swimmer.look;
		double out = swimmer.radius + swimmer.swayed + swimmer.eased;
		double sine = Math.sin(swimmer.angle);
		double cosine = Math.cos(swimmer.angle);
		// Heading: a quarter turn on from its angle, eased toward its drift. Game orientation: 0 south,
		// 512 west, 1024 north.
		double toward = speed > 0 ? Math.atan2(outward, speed) : 0;
		swimmer.drift += (Math.max(-MAX_DRIFT, Math.min(MAX_DRIFT, toward)) - swimmer.drift) * DRIFT_EASE;
		double drift = swimmer.drift;
		int heading = (int) Math.round((swimmer.angle - drift) * 2048 / (2 * Math.PI)) + 1536 & 2047;
		// Slower fish wag less.
		double pace = Math.min(1, speed / REFERENCE_SPEED);
		int swung = heading + (int) Math.round(WAG * look.wag / 100.0 * pace * pace * Math.sin(swimmer.wag)) & 2047;
		// Spin at the kind's rate, if any.
		int spun = look.spin == 0 ? 0 : swimmer.spinPhase - (int) ((long) look.spin * 2048 / 360 * cycle / 50);
		swimmer.fish.setOrientation(swung + look.turn * 2048 / 360 + spun & 2047);
		// Wag about a point ahead of its middle, so the tail sweeps.
		swimmer.fish.setX(school.x + (int) Math.round(out * sine)
			+ (look.pivot * (Perspective.SINE[swung] - Perspective.SINE[heading]) >> 16));
		swimmer.fish.setY(school.y + (int) Math.round(out * cosine)
			+ (look.pivot * (Perspective.COSINE[swung] - Perspective.COSINE[heading]) >> 16));
		// Higher is lower in game heights. Bob, then rest; fish below their kind's depth don't bob.
		int bobbing = (cycle + swimmer.bobPhase) % (swimmer.bob + swimmer.bobRest);
		int bobAt = swimmer.deeper == 0 && bobbing < swimmer.bob ? bobbing * 2048 / swimmer.bob : -1;
		int risen = bobAt < 0 ? 0 : look.rise * (65536 - Perspective.COSINE[bobAt]) >> 17;
		// Dips only go deeper.
		double through = swimmer.dippingSince < 0 ? 0
			: Math.PI * (cycle - swimmer.dippingSince) / Math.max(1, look.dipMillis / 20.0);
		double dip = Math.sin(through);
		swimmer.fish.setZ(school.z + look.sink + swimmer.deeper - risen
			+ (int) Math.round(look.dipDepth * dip * dip));
		// Tip with the bob and dip; level while resting.
		double tip = look.tip / 100.0 * ((look.rise > 0 && bobAt >= 0 ? BOB_PITCH * Perspective.SINE[bobAt] / 65536 : 0)
			- (swimmer.dippingSince >= 0 && look.dipDepth > 0 ? DIP_PITCH * Math.sin(2 * through) : 0));
		int pitch = (int) Math.round(tip / FishModels.PITCH_STEP);
		// Wiggle frames at the kind's rate.
		int frame = swimmer.wiggles
			? (int) ((long) (cycle + swimmer.wiggleStart) * look.wiggleRate / 50 % FishModels.WIGGLE_FRAMES) : 0;
		if (pitch != swimmer.pitch || frame != swimmer.frame)
		{
			// Use only made models; queue a missing one and keep the current model meanwhile.
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
	 * After a map load, puts each spot's fish back where the spot now is in the scene, matched by world
	 * location, and drops spots no longer in sight.
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
	 * Sets whether the water is see-through, for shoals made from now on.
	 */
	void setSeeThrough(boolean seeThrough)
	{
		this.seeThrough = seeThrough;
	}

	/**
	 * Removes every fish.
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
