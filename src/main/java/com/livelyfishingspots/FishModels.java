package com.livelyfishingspots;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.gameval.ItemID;

/**
 * How each kind of fish looks, and its models: made from its item's model, reshaped, stood up, sized and lit as its
 * look says, each made once and kept while a fish of its kind is in sight.
 */
final class FishModels
{
	// Which way up a fish is stood, 1 or -1: an item's model lies on its side, as it does on the floor.
	private static final int UPRIGHT = 1;
	// A fish's model is made at each PITCH_STEP degrees it tips nose up or down, as needed, since a placed model can
	// only be turned about upright, and swapped as it tips from one to the next.
	static final double PITCH_STEP = 5;

	// How many slices a model is cut into along its length to straighten it.
	private static final int STRAIGHTEN_SLICES = 20;
	// How many queued models are made each client tick, at most.
	private static final int MAKE_PER_TICK = 2;

	// The kinds whose models are reshaped by their look's sweep, uncurl, straighten, joints, bends, tail size and
	// stretch; the rest are left as their model is.
	static final Set<Integer> RESHAPED = Set.of(ItemID.RAW_SWORDFISH, ItemID.TBWT_RAW_KARAMBWAN,
		ItemID.RAW_SHARK);
	// How many frames of their wiggle swept arms are made at, cycled through as they swim, and how many waves run
	// along an arm at once.
	static final int WIGGLE_FRAMES = 4;
	private static final double WIGGLE_WAVES = 1.2;
	// The kinds that keep the height they sit at untipped as they tip, rather than being sat back on the water by
	// their lowest point, which would lift a long-nosed fish's nose far out as its tail dips.
	private static final Set<Integer> KEEP_HEIGHT = Set.of(ItemID.RAW_SWORDFISH);
	// The hues, of the game's 64 round from red, that count as green, and how saturated, of 8, a colour has to be, so
	// none of them are lightened.
	private static final int[] GREEN_HUES = {14, 30};
	private static final int GREEN_SATURATION = 2;

	// How far either side of a joint, as a share of the fish's length, its bend eases in.
	private static final double JOINT_EASE = 0.08;
	// The kinds whose models are folded at their joint, as a V, and so bend about the fold itself.
	private static final Set<Integer> FOLDED = Set.of(ItemID.RAW_SHARK);

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
	 * units, against LANE_SPACING's lanes; roomEase: how quickly it eases there, in percent, as the strength of the
	 * spring it eases by</li>
	 * <li>dipEvery, dipDepth, dipMillis: how often on average it dips deeper than its bob takes it, never higher, in
	 * seconds; how much deeper at most, in local units; and how long a dip takes, down and back up, in
	 * milliseconds</li>
	 * <li>lightest: how light, at least, every face of it is, on the game's colour scale of 0 to 127, so none is
	 * too dark however it is lit</li>
	 * <li>straighten: how far a model bent along its length, as one drawn leaping, is straightened, in percent;
	 * stretch: how long it is made along its length, in percent of how long it is; joint and bend: where along its
	 * length, in percent, a model bent there is turned back straight, and by how many degrees, the shorter side of
	 * the joint swinging round it; joint2 and bend2: a second such joint, turned after the first, as for a tail
	 * still bent once its body is straight; tailSize: how big, in percent, the part beyond that second joint is
	 * made, round the joint</li>
	 * <li>tipPivot: how far ahead of its middle, towards its head, it tips about as it bobs and dips, in local
	 * units</li>
	 * <li>pace: how fast it swims round, in percent of its lane's speed; surge: how much it speeds up and slows down
	 * as it swims, in percent of its lane's surge</li>
	 * <li>sweep and sweepBody: how far, in percent, the arms of a model reaching out all round, as the karambwan's,
	 * are swept back behind it, the tips the most, as an octopus's trail; and how much of its middle, in percent of
	 * its reach, is the body they reach from, left as it is; wiggle and wiggleRate: how far its swept arms wave, in
	 * percent of its reach, and how many of its WIGGLE_FRAMES it goes through a second</li>
	 * <li>uncurl and curlCentre: how far, in percent, a model curled round in an arc, as the raw shark's, is unrolled
	 * about the middle of its curl, and how far that middle is moved the way its arch bulges, in local units less
	 * 50; finSize: how big, in percent, the pieces not joined to its body, as the side fins, are made as it is
	 * unrolled, round the body point each is held to</li>
	 * <li>depthRange: how much deeper, at most, each fish sits than its kind's sink, picked at random for it and kept,
	 * in local units</li>
	 * <li>wag: how far its tail wags, in percent of WAG, 0 for not at all; tip: how far it tips nose up and down as it
	 * bobs and dips, in percent of BOB_PITCH and DIP_PITCH, 0 for staying level</li>
	 * </ul>
	 */
	static final class Look
	{
		final int roll;
		final int tilt;
		final int size;
		final int sizeSpread;
		final int sink;
		final int rise;
		final int pivot;
		final int turn;
		final int spin;
		final int room;
		final int roomRange;
		final int roomEase;
		final int dipEvery;
		final int dipDepth;
		final int dipMillis;
		final int lightest;
		final int wag;
		final int tip;
		final int straighten;
		final int stretch;
		final int joint;
		final int bend;
		final int tipPivot;
		final int pace;
		final int surge;
		final int sweep;
		final int sweepBody;
		final int wiggle;
		final int wiggleRate;
		final int depthRange;
		final int uncurl;
		final int curlCentre;
		final int finSize;
		final int joint2;
		final int bend2;
		final int tailSize;

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
			uncurl = values[30];
			curlCentre = values[31];
			finSize = values[32];
			joint2 = values[33];
			bend2 = values[34];
			tailSize = values[35];
		}
	}

	// Each kind's look, by its item, in the order of Look's values: roll, tilt, size, sizeSpread, sink, rise, pivot,
	// turn, spin, room, roomRange, roomEase, dipEvery, dipDepth, dipMillis, lightest, wag, tip, straighten, stretch,
	// joint, bend, tipPivot, pace, surge, sweep, sweepBody, wiggle, wiggleRate, depthRange, uncurl, curlCentre,
	// finSize, joint2, bend2, tailSize. Those not yet tuned look like the anglerfish, the first tuned.
	private static final Look ANGLERFISH = new Look(new int[]{90, 46, 50, 17, 30, 8, 16, 0, 0, 40, 60, 30, 10, 10, 1500,
		0, 100, 0, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 200, 0, 50, 100, 0, 0, 100});
	private static final Look SHRIMP = new Look(new int[]{0, 5, 35, 17, 50, 20, 16, 126, 300, 40, 60, 30, 1, 10, 1500,
		0, 0, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100});
	private static final Look SARDINE = new Look(new int[]{90, 0, 30, 17, 5, 4, 16, -90, 0, 40, 60, 30, 10, 10, 1500,
		30, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100});
	private static final Look HERRING = new Look(new int[]{-90, 0, 45, 17, 10, 3, 16, -90, 0, 40, 60, 30, 3, 15, 1500,
		35, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100});
	private static final Look BASS = new Look(new int[]{-90, 0, 45, 17, 17, 3, 30, -90, 0, 40, 60, 30, 8, 15, 2500,
		30, 100, 100, 0, 100, 50, 0, 0, 80, 80, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100});
	private static final int[] COD_VALUES = {-90, 0, 35, 17, 12, 6, 16, -90, 0, 40, 60, 30, 10, 7, 1500,
		50, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100};
	private static final Look COD = new Look(COD_VALUES);
	// The river fish's, all three: the cod's, smaller and nearer the surface.
	private static final int[] RIVER_VALUES = {-90, 0, 30, 17, 8, 3, 16, -90, 0, 40, 60, 30, 20, 7, 1500,
		50, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100};
	private static final Look RIVER = new Look(RIVER_VALUES);
	// TEMPORARY, while tuning: looks set from the debug spinners, by item, used before LOOKS.
	private static final Map<Integer, Look> TUNED = new HashMap<>();
	private static final Map<Integer, Look> LOOKS = Map.ofEntries(
		Map.entry(ItemID.RAW_ANGLERFISH, ANGLERFISH),
		Map.entry(ItemID.RAW_MONKFISH, new Look(new int[]{0, 0, 55, 17, 15, 6, 16, -42, 0, 40, 60, 30, 10, 10, 1500,
			0, 100, 0, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 250, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.TBWT_RAW_KARAMBWAN, new Look(new int[]{0, 12, 70, 17, 8, 0, 16, -90, 0, 40, 60, 30, 10, 0,
			1500, 0, 100, 100, 0, 110, 13, 37, -13, 50, 25, 100, 33, 17, 6, 250, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.RAW_SHRIMP, SHRIMP),
		Map.entry(ItemID.RAW_ANCHOVIES, SHRIMP),
		Map.entry(ItemID.RAW_SARDINE, SARDINE),
		Map.entry(ItemID.RAW_HERRING, HERRING),
		Map.entry(ItemID.RAW_MACKEREL, new Look(new int[]{-90, 0, 30, 17, 10, 6, 16, -90, 0, 40, 60, 30, 10, 12, 1500,
			25, 100, 100, 0, 100, 50, 0, 0, 90, 105, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.RAW_COD, COD),
		Map.entry(ItemID.RAW_TROUT, RIVER),
		Map.entry(ItemID.RAW_SALMON, RIVER),
		Map.entry(ItemID.RAW_PIKE, RIVER),
		Map.entry(ItemID.RAW_LOBSTER, new Look(new int[]{0, 0, 45, 17, 30, 5, 0, -90, 0, 40, 60, 30, 3, 0, 1500,
			35, 0, 100, 0, 100, 50, 0, 0, 50, 50, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.RAW_TUNA, BASS),
		Map.entry(ItemID.RAW_SWORDFISH, new Look(new int[]{-90, 19, 45, 17, 16, 2, 25, -90, 0, 40, 60, 30, 10, 10, 1500,
			35, 100, 100, 0, 120, 66, 80, -10, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.RAW_BASS, BASS),
		Map.entry(ItemID.RAW_SHARK, new Look(new int[]{-90, 37, 67, 30, 29, 3, 45, -90, 0, 40, 60, 30, 3, 17, 2500,
			10, 60, 0, 5, 85, 54, 98, 0, 40, 75, 0, 0, 0, 6, 0, 15, 81, 55, 80, 36, 200})));

	// Each fish's model at each size, tip and frame, by its item, its size in percent, its tip in steps and its
	// frame, loaded the first time it's needed.
	private final Map<Long, Model> models = new HashMap<>();

	// Models still to make, a few each client tick, so a spot coming into sight doesn't make them all at once: each as
	// its item, size, tip and frame, in the order wanted, and their keys in models, so none is queued twice.
	private final Deque<int[]> toMake = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private final Client client;

	FishModels(Client client)
	{
		this.client = client;
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
	 * degrees, within its flat side: the plane of its two longest ways, and sizes it by scale round the joint. Points
	 * within JOINT_EASE of its length either side of the joint swing and size only part of the way, so it bends there
	 * smoothly rather than tearing.
	 */
	private static void bendTail(ModelData model, double joint, int degrees, boolean atFold, double scale)
	{
		int count = model.getVerticesCount();
		float[][] ways = {model.getVerticesX(), model.getVerticesY(), model.getVerticesZ()};
		Integer[] order = {0, 1, 2};
		Arrays.sort(order, (a, b) -> Float.compare(spread(ways[b], count), spread(ways[a], count)));
		float[] along = ways[order[0]];
		float[] across = ways[order[1]];
		float[] thick = ways[order[2]];
		double thickMiddle = (min(thick, count) + max(thick, count)) / 2.0;
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
		// A model folded at the joint, as a V, swings about the fold itself: the middle of its body there, halfway
		// between its furthest points across near the joint, rather than the middle of all of it, which a deep fold
		// leaves out in the empty space inside the V.
		if (atFold)
		{
			float low = Float.MAX_VALUE;
			float high = -Float.MAX_VALUE;
			for (int i = 0; i < count; i++)
			{
				if (Math.abs(along[i] - at) <= ease)
				{
					low = Math.min(low, across[i]);
					high = Math.max(high, across[i]);
				}
			}
			if (low <= high)
			{
				middle = (low + high) / 2.0;
			}
		}
		for (int i = 0; i < count; i++)
		{
			double beyond = (along[i] - at) * side;
			if (beyond <= -ease)
			{
				continue;
			}
			double share = Math.min(1, (beyond + ease) / (2 * ease));
			double turn = Math.toRadians(degrees) * share * side;
			// Sized round the joint too, eased in the same as the turn so it stays joined on.
			double size = 1 + (scale - 1) * share;
			double a = (along[i] - at) * size;
			double b = (across[i] - middle) * size;
			along[i] = (float) (at + a * Math.cos(turn) - b * Math.sin(turn));
			across[i] = (float) (middle + a * Math.sin(turn) + b * Math.cos(turn));
			thick[i] = (float) (thickMiddle + (thick[i] - thickMiddle) * size);
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
	 * Unrolls a model curled round in an arc, as the raw shark's, by a share. Its curl lies across its two broadest
	 * ways, its thinnest being its thickness side to side. The curl's middle is the middle of the circle best fitting
	 * its body, the points at least half as far out to its sides as its sides are, which leaves its fins out; moved
	 * by shift, in local units, the way its arch bulges. Each point is unrolled round it, as {@link #unroll} says.
	 */
	private static void uncurl(ModelData model, double share, double shift, double finSize)
	{
		int count = model.getVerticesCount();
		float[][] ways = {model.getVerticesX(), model.getVerticesY(), model.getVerticesZ()};
		Integer[] order = {0, 1, 2};
		Arrays.sort(order, (a, b) -> Float.compare(spread(ways[b], count), spread(ways[a], count)));
		float[] u = ways[order[0]];
		float[] v = ways[order[1]];
		float[] side = ways[order[2]];
		double sideMiddle = (min(side, count) + max(side, count)) / 2.0;
		double sideHalf = (max(side, count) - min(side, count)) / 2.0;
		// The circle through the body, by least squares: x^2 + y^2 + D x + E y + F = 0.
		double[][] m = new double[3][4];
		for (int i = 0; i < count; i++)
		{
			if (Math.abs(side[i] - sideMiddle) < sideHalf / 2)
			{
				continue;
			}
			double[] row = {u[i], v[i], 1};
			double rhs = -(u[i] * (double) u[i] + v[i] * (double) v[i]);
			for (int r = 0; r < 3; r++)
			{
				for (int c = 0; c < 3; c++)
				{
					m[r][c] += row[r] * row[c];
				}
				m[r][3] += row[r] * rhs;
			}
		}
		double[] solved = solve(m);
		if (solved == null)
		{
			return;
		}
		double centreU = -solved[0] / 2;
		double centreV = -solved[1] / 2;
		double radius = Math.sqrt(Math.max(1, centreU * centreU + centreV * centreV - solved[2]));
		// The way the arch bulges: the way, on average, its body's points lie from the middle, each counted the same
		// however far out, so the two sides hanging down cancel and its top is left.
		double bulgeU = 0;
		double bulgeV = 0;
		for (int i = 0; i < count; i++)
		{
			double out = Math.hypot(u[i] - centreU, v[i] - centreV);
			if (Math.abs(side[i] - sideMiddle) >= sideHalf / 2 && out > 0)
			{
				bulgeU += (u[i] - centreU) / out;
				bulgeV += (v[i] - centreV) / out;
			}
		}
		// Squared up to whichever of its two ways that is nearest, the item being drawn with its arch upright, as
		// uneven numbers of points on the two sides would otherwise lean it.
		boolean alongU = Math.abs(bulgeU) >= Math.abs(bulgeV);
		bulgeU = alongU ? Math.signum(bulgeU) : 0;
		bulgeV = alongU ? 0 : Math.signum(bulgeV);
		centreU += bulgeU * shift;
		centreV += bulgeV * shift;
		// Pieces not joined to the body by any face, as the side fins, are moved whole, held to the body's point
		// nearest them and turned with the body there, rather than point by point, which would stretch them along it.
		int[] piece = pieces(model);
		int body = largest(piece);
		Map<Integer, Integer> nearest = new HashMap<>();
		Map<Integer, Double> nearestFar = new HashMap<>();
		for (int i = 0; i < count; i++)
		{
			if (piece[i] == body)
			{
				continue;
			}
			for (int j = 0; j < count; j++)
			{
				if (piece[j] != body)
				{
					continue;
				}
				double far = Math.pow(u[i] - u[j], 2) + Math.pow(v[i] - v[j], 2) + Math.pow(side[i] - side[j], 2);
				if (far < nearestFar.getOrDefault(piece[i], Double.MAX_VALUE))
				{
					nearestFar.put(piece[i], far);
					nearest.put(piece[i], j);
				}
			}
		}
		Map<Integer, double[]> moves = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : nearest.entrySet())
		{
			int j = entry.getValue();
			double[] unrolled = unroll(u[j] - centreU, v[j] - centreV, bulgeU, bulgeV, radius);
			moves.put(entry.getKey(), new double[]{u[j], v[j], unrolled[0], unrolled[1], unrolled[2], side[j]});
		}
		for (int i = 0; i < count; i++)
		{
			double a = u[i] - centreU;
			double b = v[i] - centreV;
			double[] move = moves.get(piece[i]);
			if (move == null)
			{
				double[] unrolled = unroll(a, b, bulgeU, bulgeV, radius);
				u[i] = (float) (u[i] + (centreU + unrolled[0] - u[i]) * share);
				v[i] = (float) (v[i] + (centreV + unrolled[1] - v[i]) * share);
				continue;
			}
			// The body point it is held to moved its share of the way, and the point turned round it by its share of
			// the body's turn there, across and along the bulge.
			double offU = (u[i] - move[0]) * finSize;
			double offV = (v[i] - move[1]) * finSize;
			double out = offU * bulgeU + offV * bulgeV;
			double across = offU * bulgeV - offV * bulgeU;
			double turn = -move[4] * share;
			double turnedOut = out * Math.cos(turn) - across * Math.sin(turn);
			double turnedAcross = out * Math.sin(turn) + across * Math.cos(turn);
			double middleU = move[0] + (centreU + move[2] - move[0]) * share;
			double middleV = move[1] + (centreV + move[3] - move[1]) * share;
			u[i] = (float) (middleU + bulgeU * turnedOut + bulgeV * turnedAcross);
			v[i] = (float) (middleV + bulgeV * turnedOut - bulgeU * turnedAcross);
			side[i] = (float) (move[5] + (side[i] - move[5]) * finSize);
		}
	}

	/**
	 * Where a point, given from the middle of a curl, lies once unrolled, from that middle, and its angle round it
	 * from the way the arch bulges: the angle becomes how far along the straightened fish it is, at the curl's radius
	 * out, and its distance from the middle how far it is from the spine, its outside, the arch's top, staying on top.
	 */
	private static double[] unroll(double a, double b, double bulgeU, double bulgeV, double radius)
	{
		double angle = Math.atan2(a * bulgeV - b * bulgeU, a * bulgeU + b * bulgeV);
		double distance = Math.hypot(a, b);
		double alongFish = radius * angle;
		return new double[]{bulgeU * distance + bulgeV * alongFish, bulgeV * distance - bulgeU * alongFish, angle};
	}

	/**
	 * Which piece each of a model's points belongs to, points sharing a face being in the same piece, by the piece's
	 * lowest point.
	 */
	private static int[] pieces(ModelData model)
	{
		int[] piece = new int[model.getVerticesCount()];
		for (int i = 0; i < piece.length; i++)
		{
			piece[i] = i;
		}
		int[][] corners = {model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3()};
		for (int f = 0; f < model.getFaceCount(); f++)
		{
			join(piece, corners[0][f], corners[1][f]);
			join(piece, corners[1][f], corners[2][f]);
		}
		for (int i = 0; i < piece.length; i++)
		{
			piece[i] = root(piece, i);
		}
		return piece;
	}

	private static void join(int[] piece, int a, int b)
	{
		int rootA = root(piece, a);
		int rootB = root(piece, b);
		piece[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
	}

	private static int root(int[] piece, int i)
	{
		while (piece[i] != i)
		{
			piece[i] = piece[piece[i]];
			i = piece[i];
		}
		return i;
	}

	/**
	 * The piece with the most points.
	 */
	private static int largest(int[] piece)
	{
		Map<Integer, Integer> sizes = new HashMap<>();
		for (int p : piece)
		{
			sizes.merge(p, 1, Integer::sum);
		}
		return Collections.max(sizes.entrySet(), Map.Entry.comparingByValue()).getKey();
	}

	/**
	 * Solves three equations in three unknowns, each row its three weights then its total; null if they can't be.
	 */
	private static double[] solve(double[][] m)
	{
		for (int i = 0; i < 3; i++)
		{
			int pivot = i;
			for (int r = i + 1; r < 3; r++)
			{
				pivot = Math.abs(m[r][i]) > Math.abs(m[pivot][i]) ? r : pivot;
			}
			double[] swap = m[i];
			m[i] = m[pivot];
			m[pivot] = swap;
			if (Math.abs(m[i][i]) < 1e-9)
			{
				return null;
			}
			for (int r = 0; r < 3; r++)
			{
				if (r != i)
				{
					double f = m[r][i] / m[i][i];
					for (int c = i; c < 4; c++)
					{
						m[r][c] -= f * m[i][c];
					}
				}
			}
		}
		return new double[]{m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2]};
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
	 * A kind of fish's model at a size and tipped nose up by a number of PITCH_STEPs, made the first time it's needed.
	 */
	Model model(int item, int size, int pitch, int frame)
	{
		return models.computeIfAbsent(key(item, size, pitch, frame), key -> load(item, size, pitch, frame));
	}

	/**
	 * Where a kind's model at a size, tip and frame is kept in models.
	 */
	private static long key(int item, int size, int pitch, int frame)
	{
		return ((item * 1000L + size) * 64 + pitch + 32) * 8 + frame;
	}

	/**
	 * Which kind's model a key in models is, by its item.
	 */
	private static int itemOf(long key)
	{
		return (int) (key / 8 / 64 / 1000);
	}

	/**
	 * Queues a model to be made, unless it is made or queued already: first in line if a fish is waiting for it, else
	 * last.
	 */
	void queue(int item, int size, int pitch, int frame, boolean waiting)
	{
		long key = key(item, size, pitch, frame);
		if (models.containsKey(key) || !queued.add(key))
		{
			return;
		}
		int[] wanted = {item, size, pitch, frame};
		if (waiting)
		{
			toMake.addFirst(wanted);
		}
		else
		{
			toMake.addLast(wanted);
		}
	}

	/**
	 * Makes up to MAKE_PER_TICK of the queued models.
	 */
	void makeQueued()
	{
		for (int made = 0; made < MAKE_PER_TICK && !toMake.isEmpty(); )
		{
			int[] wanted = toMake.pollFirst();
			long key = key(wanted[0], wanted[1], wanted[2], wanted[3]);
			queued.remove(key);
			if (!models.containsKey(key))
			{
				model(wanted[0], wanted[1], wanted[2], wanted[3]);
				made++;
			}
		}
	}

	private Model load(int item, int size, int pitch, int frame)
	{
		ModelData model = loadItem(item);
		Look look = look(item);
		if (model == null)
		{
			return null;
		}
		// Only the kinds that need it are reshaped.
		boolean reshaped = RESHAPED.contains(item);
		if (reshaped && look.sweep > 0)
		{
			sweep(model, look.sweep / 100.0, look.sweepBody / 100.0, look.wiggle / 100.0, frame);
		}
		if (reshaped && look.uncurl > 0)
		{
			uncurl(model, look.uncurl / 100.0, look.curlCentre - 50, look.finSize / 100.0);
		}
		if (reshaped && look.straighten > 0)
		{
			straighten(model, look.straighten / 100.0);
		}
		if (reshaped && look.bend != 0)
		{
			bendTail(model, look.joint / 100.0, look.bend, FOLDED.contains(item), 1);
		}
		if (reshaped && (look.bend2 != 0 || look.tailSize != 100))
		{
			bendTail(model, look.joint2 / 100.0, look.bend2, FOLDED.contains(item),
				look.tailSize / 100.0);
		}
		if (reshaped && look.stretch != 100)
		{
			stretch(model, look.stretch / 100.0);
		}
		// Tipped about a point ahead of its middle, towards its head, by its kind's tip pivot at its size: its head
		// faces (sine, -cosine) of its turn in its model's own ways.
		double turn = Math.toRadians(look.turn);
		standUp(model, look.roll, look.tilt, pitch * PITCH_STEP, look.tipPivot * 100.0 / size,
			Math.sin(turn), -Math.cos(turn), KEEP_HEIGHT.contains(item));
		int scale = size * 128 / 100;
		model.scale(scale, scale, scale);
		// Lit as the game lights any item; only its colours are lightened after, where its kind asks.
		Model lit = model.light();
		if (look.lightest > 0 && lit != null)
		{
			lighten(lit, look.lightest);
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
				if (colours[i] >= 0 && (colours[i] & 127) < lightest && !green(colours[i]))
				{
					colours[i] = colours[i] & ~127 | lightest;
				}
			}
		}
		short[] unlit = model.getUnlitFaceColors();
		for (int i = 0; unlit != null && i < unlit.length; i++)
		{
			int colour = unlit[i] & 0xffff;
			if ((colour & 127) < lightest && !green(colour))
			{
				unlit[i] = (short) (colour & ~127 | lightest);
			}
		}
	}

	/**
	 * Whether a colour, in the game's packed hue, saturation and lightness, is a clear green, as the dark gills of a
	 * tuna or bass, which lightening would turn a bright, glaring green, so are left dark.
	 */
	private static boolean green(int colour)
	{
		int hue = colour >> 10 & 63;
		int saturation = colour >> 7 & 7;
		return saturation >= GREEN_SATURATION && hue >= GREEN_HUES[0] && hue <= GREEN_HUES[1];
	}

	/**
	 * How a kind of fish is shown.
	 */
	static Look look(int item)
	{
		Look tuned = TUNED.get(item);
		return tuned != null ? tuned : LOOKS.getOrDefault(item, ANGLERFISH);
	}

	/**
	 * TEMPORARY, while tuning: sets a kind's look from the river fish's, with these of its values, by their place in Look's
	 * values, and lets its models go, to be made again.
	 */
	void tuneLook(int item, int[] places, int[] values)
	{
		int[] look = RIVER_VALUES.clone();
		for (int i = 0; i < places.length; i++)
		{
			look[places[i]] = values[i];
		}
		TUNED.put(item, new Look(look));
		models.keySet().removeIf(key -> itemOf(key) == item);
		toMake.removeIf(wanted -> wanted[0] == item);
		queued.removeIf(key -> itemOf(key) == item);
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

	static float spread(float[] values, int count)
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
	 * A kind's model at a size, tip and frame, if it has been made yet; null if not.
	 */
	Model made(int item, int size, int pitch, int frame)
	{
		return models.get(key(item, size, pitch, frame));
	}

	/**
	 * Lets go of the models of every kind but these, the kinds still swimming in sight, to be made again if they come
	 * back.
	 */
	void keepOnly(Set<Integer> swimming)
	{
		models.keySet().removeIf(key -> !swimming.contains(itemOf(key)));
		toMake.removeIf(wanted -> !swimming.contains(wanted[0]));
		queued.removeIf(key -> !swimming.contains(itemOf(key)));
	}

	/**
	 * Lets every model go, as when switched off.
	 */
	void clear()
	{
		models.clear();
		toMake.clear();
		queued.clear();
	}
}
