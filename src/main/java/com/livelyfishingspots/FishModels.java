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
 * Fish looks and their models, built from item models, cached while a fish of the kind is in sight.
 */
final class FishModels
{
	// 1 or -1: which way up an item model, lying on its side, is stood.
	private static final int UPRIGHT = 1;
	// Tip step in degrees; a model is made per step, since placed models only turn about upright.
	static final double PITCH_STEP = 5;

	// Slices used to straighten a model.
	private static final int STRAIGHTEN_SLICES = 20;
	// Most queued models made per client tick.
	private static final int MAKE_PER_TICK = 2;

	// Kinds whose models are reshaped by their look.
	static final Set<Integer> RESHAPED = Set.of(ItemID.RAW_SWORDFISH, ItemID.TBWT_RAW_KARAMBWAN,
		ItemID.RAW_SHARK);
	// Wiggle frames made for swept arms, and waves along an arm.
	static final int WIGGLE_FRAMES = 4;
	private static final double WIGGLE_WAVES = 1.2;
	// Kinds whose item model is a relief facing one way, given a mirrored back.
	private static final Set<Integer> ONE_SIDED = Set.of(ItemID.HUNTING_RAW_FISH_SPECIAL, ItemID.BRUT_SPAWNING_TROUT,
		ItemID.BRUT_SPAWNING_SALMON, ItemID.BRUT_STURGEON);
	// Their depth from the back, percent, by kind; 50 if none (the debug plugin may tune it).
	static final Map<Integer, Integer> ONE_SIDED_DEPTH = new HashMap<>(Map.of(ItemID.BRUT_SPAWNING_TROUT, 100,
		ItemID.BRUT_SPAWNING_SALMON, 100));
	// Kinds that keep their untipped height when tipped, so a long nose doesn't lift out.
	private static final Set<Integer> KEEP_HEIGHT = Set.of(ItemID.RAW_SWORDFISH);
	// Hue range (of 64) and least saturation (of 8) counted as green, never lightened.
	private static final int[] GREEN_HUES = {14, 30};
	private static final int GREEN_SATURATION = 2;

	// Share of the length either side of a joint the bend eases over.
	private static final double JOINT_EASE = 0.08;
	// Kinds folded into a V at their joint, bending about the fold.
	private static final Set<Integer> FOLDED = Set.of(ItemID.RAW_SHARK);

	/**
	 * How one kind looks and moves. Angles in degrees, distances in local units, shares in percent.
	 * <ul>
	 * <li>roll, tilt: rolled up off its side (90 upright), then tilted head up</li>
	 * <li>size, sizeSpread: percent of the item's size, and how much smaller a fish may be</li>
	 * <li>sink, rise: depth below the water, and bob height</li>
	 * <li>pivot: how far ahead of its middle it wags about</li>
	 * <li>turn, spin: turn to face its way; spin in degrees a second (0 none)</li>
	 * <li>room, roomRange, roomEase: room kept from nearby fish, from how far, and spring strength</li>
	 * <li>dipEvery, dipDepth, dipMillis: seconds between dips on average, extra depth, duration</li>
	 * <li>lightest: least face lightness, of 127</li>
	 * <li>straighten, stretch: straightening of a bent model, and length</li>
	 * <li>joint, bend, joint2, bend2, tailSize: two joints along the length, their bends, and the size
	 * past the second</li>
	 * <li>tipPivot: how far ahead of its middle it tips about</li>
	 * <li>tipPivotUp: how far above its middle it tips about (optional, 0 if left out)</li>
	 * <li>pace, surge: share of its lane's speed and surge</li>
	 * <li>sweep, sweepBody, wiggle, wiggleRate: arms swept back, body share left alone, arm wave and
	 * frames a second</li>
	 * <li>uncurl, curlCentre, finSize: unrolling a curled model, its centre shift (less 50), loose
	 * piece size</li>
	 * <li>depthRange: most extra depth per fish</li>
	 * <li>wag, tip: percent of WAG, and of BOB_PITCH and DIP_PITCH</li>
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
		final int tipPivotUp;
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
			tipPivotUp = values.length > 36 ? values[36] : 0;
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

	// Each kind's look, values in Look's order. Untuned kinds use the anglerfish's.
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
	// Trout, salmon and pike: the cod's, smaller and shallower.
	private static final int[] RIVER_VALUES = {-90, 0, 30, 17, 8, 3, 16, -90, 0, 40, 60, 30, 20, 7, 1500,
		50, 100, 100, 0, 100, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100};
	private static final Look TROUT = riverLook(26, 8);
	private static final Look SALMON = riverLook(28, 8);
	private static final Look PIKE = riverLook(30, 9);
	// Leaping fish: the river look, tilted level (their models leap); trout and salmon face the other way.
	private static final Look LEAPING_TROUT = riverLook(45, 11, -39, 180, 100);
	private static final Look LEAPING_SALMON = riverLook(39, 15, -40, 180, 100);
	// The sturgeon slower too.
	private static final Look STURGEON = riverLook(60, 15, -52, -90, 70);
	// Rainbow fish: smaller, nearer the surface, stretched longer.
	private static final Look RAINBOW = new Look(new int[]{-90, 0, 17, 17, 6, 3, 16, -90, 0, 40, 60, 30, 3, 7, 1500,
		7, 100, 100, 0, 150, 50, 0, 0, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100});
	// Looks from the debug plugin's tuning spinners, by item, overriding LOOKS; none in the plugin itself.
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
		Map.entry(ItemID.RAW_TROUT, TROUT),
		Map.entry(ItemID.RAW_SALMON, SALMON),
		Map.entry(ItemID.RAW_PIKE, PIKE),
		Map.entry(ItemID.HUNTING_RAW_FISH_SPECIAL, RAINBOW),
		Map.entry(ItemID.BRUT_SPAWNING_TROUT, LEAPING_TROUT),
		Map.entry(ItemID.BRUT_SPAWNING_SALMON, LEAPING_SALMON),
		Map.entry(ItemID.BRUT_STURGEON, STURGEON),
		Map.entry(ItemID.RAW_LOBSTER, new Look(new int[]{0, 0, 45, 17, 30, 5, 0, -90, 0, 40, 60, 30, 3, 0, 1500,
			35, 0, 100, 0, 100, 50, 0, 0, 50, 50, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.RAW_TUNA, BASS),
		Map.entry(ItemID.RAW_SWORDFISH, new Look(new int[]{-90, 19, 45, 17, 16, 2, 25, -90, 0, 40, 60, 30, 10, 10, 1500,
			35, 100, 100, 0, 120, 66, 80, -10, 100, 100, 0, 35, 0, 6, 0, 0, 50, 100, 0, 0, 100})),
		Map.entry(ItemID.RAW_BASS, BASS),
		Map.entry(ItemID.RAW_SHARK, new Look(new int[]{-90, 37, 67, 30, 29, 3, 45, -90, 0, 40, 60, 30, 3, 17, 2500,
			10, 60, 0, 5, 85, 54, 98, 0, 40, 75, 0, 0, 0, 6, 0, 15, 81, 55, 80, 36, 200})));

	// Made models, by item, size, tip and frame.
	private final Map<Long, Model> models = new HashMap<>();

	/**
	 * How many models are made, for the debug panel.
	 */
	int count()
	{
		return models.size();
	}

	// Models waiting to be made, and their keys, so none is queued twice.
	private final Deque<int[]> toMake = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private final Client client;

	FishModels(Client client)
	{
		this.client = client;
	}

	/**
	 * A recoloured copy of a kind's item model.
	 */
	private ModelData loadItem(int item)
	{
		ItemComposition fish = client.getItemDefinition(item);
		ModelData model = client.loadModelData(fish.getInventoryModel());
		if (model != null && ONE_SIDED.contains(item))
		{
			model = bothSides(model, ONE_SIDED_DEPTH.getOrDefault(item, 50));
		}
		return model == null ? null : recolor(model.cloneVertices().cloneColors(), fish.getColorToReplace(),
			fish.getColorToReplaceWith());
	}

	/**
	 * The model with a copy mirrored across its back, so a one-way relief shows from both sides.
	 */
	private ModelData bothSides(ModelData model, int depth)
	{
		int vertices = model.getVerticesCount();
		int faces = model.getFaceCount();
		float[][] ways = {model.getVerticesX(), model.getVerticesY(), model.getVerticesZ()};
		int thin = 0;
		for (int way = 1; way < 3; way++)
		{
			if (max(ways[way], vertices) - min(ways[way], vertices) < max(ways[thin], vertices) - min(ways[thin], vertices))
			{
				thin = way;
			}
		}
		// Which way the faces look along the thin axis; the back is the other side.
		int[] first = model.getFaceIndices1();
		int[] second = model.getFaceIndices2();
		int[] third = model.getFaceIndices3();
		int next = (thin + 1) % 3;
		int last = (thin + 2) % 3;
		double facing = 0;
		for (int face = 0; face < faces; face++)
		{
			int a = first[face];
			int b = second[face];
			int c = third[face];
			facing += (ways[next][b] - ways[next][a]) * (ways[last][c] - ways[last][a])
				- (ways[last][b] - ways[last][a]) * (ways[next][c] - ways[next][a]);
		}
		double back = facing < 0 ? max(ways[thin], vertices) : min(ways[thin], vertices);
		ModelData front = model.shallowCopy().cloneVertices();
		ModelData copy = model.shallowCopy().cloneVertices();
		float[] squashed = axis(front, thin);
		float[] mirrored = axis(copy, thin);
		for (int vertex = 0; vertex < vertices; vertex++)
		{
			squashed[vertex] = (float) (back + (squashed[vertex] - back) * depth / 100.0);
			mirrored[vertex] = (float) (2 * back - squashed[vertex]);
		}
		ModelData both = client.mergeModels(front, copy);
		// Mirroring turns the copy's faces inside out; swap two corners to turn them back.
		int[] bothSecond = both.getFaceIndices2();
		int[] bothThird = both.getFaceIndices3();
		for (int face = faces; face < both.getFaceCount(); face++)
		{
			int swap = bothSecond[face];
			bothSecond[face] = bothThird[face];
			bothThird[face] = swap;
		}
		return both;
	}

	private static float[] axis(ModelData model, int way)
	{
		return way == 0 ? model.getVerticesX() : way == 1 ? model.getVerticesY() : model.getVerticesZ();
	}

	/**
	 * Straightens a model bent along its length by a share, keeping its length.
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
		float min = min(along, count);
		float max = max(along, count);
		double length = Math.max(1, max - min);
		int[] others = longest == 0 ? new int[]{1, 2} : longest == 1 ? new int[]{0, 2} : new int[]{0, 1};
		// Each slice's middle across both other axes, and the whole model's.
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
			// Empty slices take their nearest neighbour's.
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
		// Arc length from the first slice to each.
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
			// Interpolate between the two nearest slices.
			double at = (along[i] - min) / length * STRAIGHTEN_SLICES - 0.5;
			int below = Math.max(0, Math.min(STRAIGHTEN_SLICES - 1, (int) Math.floor(at)));
			int above = Math.min(STRAIGHTEN_SLICES - 1, below + 1);
			double through = Math.max(0, Math.min(1, at - below));
			for (int o = 0; o < 2; o++)
			{
				double bend = middle[o][below] + (middle[o][above] - middle[o][below]) * through - whole[o];
				ways[others[o]][i] = (float) (ways[others[o]][i] - bend * share);
			}
			// Spread along by arc length.
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
	 * Bends the shorter side past a joint by some degrees in the model's flat plane, scaling it about the
	 * joint; eased over JOINT_EASE so it doesn't tear.
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
		// The shorter side swings.
		int side = joint >= 0.5 ? 1 : -1;
		double ease = length * JOINT_EASE;
		// A V-folded model bends about the fold, not its overall middle.
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
			// Scale eased in with the turn.
			double size = 1 + (scale - 1) * share;
			double a = (along[i] - at) * size;
			double b = (across[i] - middle) * size;
			along[i] = (float) (at + a * Math.cos(turn) - b * Math.sin(turn));
			across[i] = (float) (middle + a * Math.sin(turn) + b * Math.cos(turn));
			thick[i] = (float) (thickMiddle + (thick[i] - thickMiddle) * size);
		}
	}

	/**
	 * Sweeps outreaching arms back behind the model (towards +x), more towards the tips, then waves them
	 * for a wiggle frame.
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
			// Angle from straight behind, and the axis to turn about.
			double angle = Math.acos(Math.max(-1, Math.min(1, dx / out)));
			double ax = 0;
			double ay = dz;
			double az = -dy;
			double across = Math.hypot(ay, az);
			if (across < 1e-6)
			{
				// Pointing straight along x: turn about the up axis.
				ay = 1;
				az = 0;
				across = 1;
			}
			ay /= across;
			az /= across;
			double turn = angle * share * (out - inner) / (reach - inner);
			// Rodrigues' rotation about (0, ay, az).
			double cos = Math.cos(turn);
			double sin = Math.sin(turn);
			double dot = ay * dy + az * dz;
			double cx = ay * dz - az * dy;
			double cy = az * dx;
			double cz = -ay * dx;
			x[i] = (float) (middle[0] + dx * cos + cx * sin + ax * dot * (1 - cos));
			y[i] = (float) (middle[1] + dy * cos + cy * sin + ay * dot * (1 - cos));
			z[i] = (float) (middle[2] + dz * cos + cz * sin + az * dot * (1 - cos));
			// Wave up and down, phase-shifted per arm.
			double along = (out - inner) / (reach - inner);
			double arm = Math.atan2(dz, dy) * 2;
			y[i] += (float) (wiggle * reach * along
				* Math.sin(2 * Math.PI * (WIGGLE_WAVES * along - (double) frame / WIGGLE_FRAMES) + arm));
		}
	}

	static float min(float[] values, int count)
	{
		float min = Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			min = Math.min(min, values[i]);
		}
		return min;
	}

	static float max(float[] values, int count)
	{
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			max = Math.max(max, values[i]);
		}
		return max;
	}

	/**
	 * Unrolls a model curled in an arc by a share, about the best-fit circle through its body (fins
	 * excluded), shifted by shift towards the arch.
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
		// Least-squares circle: x^2 + y^2 + D x + E y + F = 0.
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
		// Which way the arch bulges: the mean direction of the body's points from the centre.
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
		// Snap to the nearest axis, as the item is drawn arch up.
		boolean alongU = Math.abs(bulgeU) >= Math.abs(bulgeV);
		bulgeU = alongU ? Math.signum(bulgeU) : 0;
		bulgeV = alongU ? 0 : Math.signum(bulgeV);
		centreU += bulgeU * shift;
		centreV += bulgeV * shift;
		// Loose pieces (fins) move rigidly with the nearest body point instead of stretching.
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
				double du = u[i] - u[j];
				double dv = v[i] - v[j];
				double dside = side[i] - side[j];
				double far = du * du + dv * dv + dside * dside;
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
			// Move with its body point and turn by the body's turn there.
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
	 * A point's position once unrolled, relative to the curl's centre: angle becomes length along the
	 * fish, distance from the centre stays.
	 */
	private static double[] unroll(double a, double b, double bulgeU, double bulgeV, double radius)
	{
		double angle = Math.atan2(a * bulgeV - b * bulgeU, a * bulgeU + b * bulgeV);
		double distance = Math.hypot(a, b);
		double alongFish = radius * angle;
		return new double[]{bulgeU * distance + bulgeV * alongFish, bulgeV * distance - bulgeU * alongFish, angle};
	}

	/**
	 * Connected pieces of a model, labelled by each piece's lowest point.
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
	 * Solves a 3x3 linear system given as rows of three weights and a total; null if singular.
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
	 * Scales a model along its longest axis about its middle.
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
		float min = min(along, count);
		float max = max(along, count);
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
	 * A model, made and cached on first use.
	 */
	Model model(int item, int size, int pitch, int frame)
	{
		return models.computeIfAbsent(key(item, size, pitch, frame), key -> load(item, size, pitch, frame));
	}

	/**
	 * Cache key.
	 */
	private static long key(int item, int size, int pitch, int frame)
	{
		return ((item * 1000L + size) * 64 + pitch + 32) * 8 + frame;
	}

	/**
	 * The item in a cache key.
	 */
	private static int itemOf(long key)
	{
		return (int) (key / 8 / 64 / 1000);
	}

	/**
	 * Queues a model unless made or queued; at the front if a fish is waiting for it.
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
	 * Makes up to MAKE_PER_TICK queued models.
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
		// Only some kinds are reshaped.
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
		// Any kind may be stretched along its length.
		if (look.stretch != 100)
		{
			stretch(model, look.stretch / 100.0);
		}
		// Tip about tipPivot, scaled to its size; its head faces (sin, -cos) of its turn.
		double turn = Math.toRadians(look.turn);
		standUp(model, look.roll, look.tilt, pitch * PITCH_STEP, look.tipPivot * 100.0 / size,
			look.tipPivotUp * 100.0 / size, Math.sin(turn), -Math.cos(turn), KEEP_HEIGHT.contains(item));
		int scale = size * 128 / 100;
		model.scale(scale, scale, scale);
		// Lit as any item, then lightened where its look asks.
		Model lit = model.light();
		if (look.lightest > 0 && lit != null)
		{
			lighten(lit, look.lightest);
		}
		return lit;
	}

	/**
	 * Raises each face to at least a lightness, keeping hue and saturation, in both the lit and unlit
	 * colours (117 HD lights from the unlit ones). Colours pack 6 bits hue, 3 saturation, 7 lightness.
	 */
	private static void lighten(Model model, int lightest)
	{
		for (int[] colours : new int[][]{model.getFaceColors1(), model.getFaceColors2(), model.getFaceColors3()})
		{
			for (int i = 0; colours != null && i < colours.length; i++)
			{
				// Below 0: flat-shaded or hidden face.
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
	 * Whether a packed colour is a clear green, like tuna and bass gills, so it's left dark.
	 */
	private static boolean green(int colour)
	{
		int hue = colour >> 10 & 63;
		int saturation = colour >> 7 & 7;
		return saturation >= GREEN_SATURATION && hue >= GREEN_HUES[0] && hue <= GREEN_HUES[1];
	}

	/**
	 * A kind's look.
	 */
	static Look look(int item)
	{
		Look tuned = TUNED.get(item);
		return tuned != null ? tuned : LOOKS.getOrDefault(item, ANGLERFISH);
	}

	/**
	 * The river look with its own size and sink.
	 */
	private static Look riverLook(int size, int sink)
	{
		return riverLook(size, sink, 0, -90, 100);
	}

	/**
	 * The river look with its own size, sink, tilt, turn and speed (percent).
	 */
	private static Look riverLook(int size, int sink, int tilt, int turn, int pace)
	{
		int[] values = RIVER_VALUES.clone();
		values[1] = tilt;
		values[2] = size;
		values[4] = sink;
		values[7] = turn;
		values[23] = pace;
		return new Look(values);
	}

	/**
	 * For the debug plugin: sets a kind's look from the river look with some values replaced, and drops its models.
	 */
	void tuneLook(int item, int[] places, int[] values)
	{
		int[] look = Arrays.copyOf(RIVER_VALUES, 37);
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
	 * Rolls a side-lying model upright by roll, tilts it by tilt, tips it by tip about a pivot towards its
	 * head (headX, headZ) and pivotUp above its middle, then rests its lowest point on the water (its untipped one if
	 * keepHeight).
	 */
	private static void standUp(ModelData model, int roll, int tilt, double tip, double pivot, double pivotUp,
		double headX, double headZ, boolean keepHeight)
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
		// Pivot on the head's side, and tip the head's way: nose up for more than 0 whichever end it's at.
		double head = longX ? headX : headZ;
		double tipCos = Math.cos(Math.toRadians(tip));
		double tipSin = Math.sin(Math.toRadians(tip)) * (head < 0 ? 1 : -1);
		double about = pivot * (head < 0 ? -1 : 1);
		float lowest = -Float.MAX_VALUE;
		float tipped = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			float up = y[i];
			y[i] = (float) (up * rollCos + across[i] * rollSin);
			across[i] = (float) (across[i] * rollCos - up * rollSin);
			// Tilt.
			float along = length[i];
			length[i] = (float) (along * cos - y[i] * sin);
			y[i] = (float) (along * sin + y[i] * cos);
			lowest = Math.max(lowest, y[i]);
			// Tip about the pivot (y grows downwards).
			double from = length[i] - about;
			double height = y[i] + pivotUp;
			length[i] = (float) (about + from * tipCos - height * tipSin);
			y[i] = (float) (from * tipSin + height * tipCos - pivotUp);
			tipped = Math.max(tipped, y[i]);
		}
		// Lowest point is the largest y. keepHeight rests it as untipped, so the tail dips as the nose lifts.
		lowest = keepHeight ? lowest : tipped;
		for (int i = 0; i < count; i++)
		{
			y[i] -= lowest;
		}
	}

	static float spread(float[] values, int count)
	{
		return max(values, count) - min(values, count);
	}

	/**
	 * A model if already made, else null; never makes one.
	 */
	Model made(int item, int size, int pitch, int frame)
	{
		return models.get(key(item, size, pitch, frame));
	}

	/**
	 * Drops the models of every kind not in the set.
	 */
	void keepOnly(Set<Integer> swimming)
	{
		models.keySet().removeIf(key -> !swimming.contains(itemOf(key)));
		toMake.removeIf(wanted -> !swimming.contains(wanted[0]));
		queued.removeIf(key -> !swimming.contains(itemOf(key)));
	}

	/**
	 * Drops every model.
	 */
	void clear()
	{
		models.clear();
		toMake.clear();
		queued.clear();
	}
}
