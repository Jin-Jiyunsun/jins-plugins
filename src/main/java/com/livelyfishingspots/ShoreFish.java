package com.livelyfishingspots;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Shore spot fish: which spots have which fish and how often, what each option draws, and how each kind behaves
 * (schools, lanes, depths, homing), as rules RiverSpotFish's shores follow. A shore is baked with a "shore" line
 * and swims as a lake does.
 */
final class ShoreFish
{
	private ShoreFish()
	{
	}

	// Shore shrimps and anchovies (giant krill ids, kept apart from the sea's).
	static final int SHRIMP = FishModels.SHORE_SHRIMP;
	static final int ANCHOVIES = FishModels.SHORE_ANCHOVIES;
	// Each spot type's fish and odds (each type's odds sum to 100, so spots count evenly in a mix): net/bait,
	// cage/harpoon and big net/harpoon.
	private static final int[][] SALT = {{SHRIMP, ANCHOVIES, ItemID.RAW_SARDINE, ItemID.RAW_HERRING}, {12, 4, 42, 42}};
	private static final int[][] RARE = {{ItemID.RAW_LOBSTER, ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH}, {25, 60, 15}};
	private static final int[][] MEMBER = {{ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS, ItemID.RAW_SHARK},
		{32, 32, 32, 4}};
	// The fish each menu option draws, by spot type (Harpoon differs between them).
	private static final Map<String, int[]> SALT_OPTIONS = Map.of("net", new int[]{SHRIMP, ANCHOVIES},
		"bait", new int[]{ItemID.RAW_SARDINE, ItemID.RAW_HERRING});
	private static final Map<String, int[]> RARE_OPTIONS = Map.of("cage", new int[]{ItemID.RAW_LOBSTER},
		"harpoon", new int[]{ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH});
	private static final Map<String, int[]> MEMBER_OPTIONS = Map.of(
		"big net", new int[]{ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS}, "harpoon", new int[]{ItemID.RAW_SHARK});
	// Shore spots, by NPC id: Catherby and Karamja (Musa Point).
	static final Map<Integer, int[][]> SPOTS = Map.of(NpcID._0_44_53_SALTFISH, SALT, NpcID._0_44_53_RAREFISH, RARE,
		NpcID._0_44_53_MEMBERFISH, MEMBER, NpcID._0_45_49_SALTFISH, SALT, NpcID._0_45_49_RAREFISH, RARE);
	// Every shore fish (any spot's), for the "sea" kind.
	static final int[] FISH = SPOTS.values().stream().flatMapToInt(spot -> Arrays.stream(spot[0]))
		.distinct().toArray();
	// Shore fish that only come with see-through, deep water (117 HD), as at sea.
	static final Set<Integer> UNDER_WATER = Set.of(ItemID.RAW_LOBSTER, SHRIMP, ANCHOVIES);
	// Fishing XP per catch, to tell catches apart.
	static final Map<Integer, Integer> CATCH_XP = Map.ofEntries(Map.entry(SHRIMP, 10), Map.entry(ANCHOVIES, 40),
		Map.entry(ItemID.RAW_SARDINE, 20), Map.entry(ItemID.RAW_HERRING, 30), Map.entry(ItemID.RAW_MACKEREL, 20),
		Map.entry(ItemID.RAW_COD, 45), Map.entry(ItemID.RAW_BASS, 100), Map.entry(ItemID.RAW_SHARK, 110),
		Map.entry(ItemID.RAW_LOBSTER, 90), Map.entry(ItemID.RAW_TUNA, 80), Map.entry(ItemID.RAW_SWORDFISH, 100));
	// The fish each menu option draws at shore spots, by spot.
	static final Map<Integer, Map<String, int[]>> OPTIONS = Map.of(NpcID._0_44_53_SALTFISH, SALT_OPTIONS,
		NpcID._0_44_53_RAREFISH, RARE_OPTIONS, NpcID._0_44_53_MEMBERFISH, MEMBER_OPTIONS,
		NpcID._0_45_49_SALTFISH, SALT_OPTIONS, NpcID._0_45_49_RAREFISH, RARE_OPTIONS);
	// Lakes baked with "rings grow" (places too tight for fish to swim in to a spot): there none join or head for
	// one; while a spot is fished, fish of the kinds it draws grow in on its lanes, and shrink away once it isn't,
	// apart from the lake's own fish. At most so many shrimps and anchovies grown in.
	static final int RING_SPAWN_KRILL_MOST = 40;
	// Client ticks between fish of a kind grown in on a ring, each kind on its own: shrimps and anchovies, sharks and
	// swordfish, and the rest (lobsters too).
	static final int RING_SPAWN_KRILL_GAP_LEAST = 15;
	static final int RING_SPAWN_KRILL_GAP_MOST = 35;
	static final int RING_SPAWN_BIG_GAP_LEAST = 125;
	static final int RING_SPAWN_BIG_GAP_MOST = 250;
	static final int RING_SPAWN_OTHER_GAP_LEAST = 80;
	static final int RING_SPAWN_OTHER_GAP_MOST = 180;
	// Where lobsters roam: this share of their goals are near the nearest cage spot, within this far of it (local
	// units; tuning spinners).
	static double LOBSTER_HOME_CHANCE = 1;
	static double LOBSTER_HOME_RADIUS = 256;
	// Lobsters swim in to a ring this share as fast as other fish.
	static final double LOBSTER_COME_IN = 0.4;
	// Water cells a shore needs for sharks to roam it (they still grow in on its rings).
	static final int SHARK_ROOM = 1000;
	// Swordfish and sharks turn this share as fast, so they take wider turns.
	static final double BIG_TURN = 0.7;
	// Lobsters circle this much higher than their sink, shrimps and anchovies this much (local units).
	static final double CIRCLE_RISE = 12;
	static final double KRILL_CIRCLE_RISE = 30;
	// Shrimps and anchovies come no nearer the surface than this (local units).
	static final int KRILL_TOP = 15;
	// Sharks and swordfish: never school (one picked to lead a school comes alone, like sturgeon), always take a
	// circle's outer lane, and grow in on a ring one at a time.
	private static final Set<Integer> BIG_FISH = Set.of(ItemID.RAW_SHARK, ItemID.RAW_SWORDFISH);
	// Places a ring drawing swordfish keeps free for them; rings that grow their own fish grow at least this many.
	static final int SWORDFISH_ROOM = 2;
	// Fewest of a kind a shore keeps while its spots bring it (others 1).
	private static final Map<Integer, Integer> LEAST = Map.of(ItemID.RAW_SWORDFISH, 2);
	// Shrimps and anchovies (giant krill): only while their spot is fished, growing in on its ring (any shore), and
	// there take any lane at random, without spacing out (spots with them have a third, innermost lane for them only).
	private static final Set<Integer> KRILL_FISH = Set.of(SHRIMP, ANCHOVIES);
	// Kinds that never roam a shore: they only grow in on a fished spot's ring, on any shore (lobsters roam a shore
	// baked "lobsters roam").
	static final Set<Integer> RING_ONLY = Set.of(SHRIMP, ANCHOVIES, ItemID.RAW_LOBSTER);
	// Share of shrimps and anchovies growing in on a ring that circle it the other way (tuning spinner).
	static double KRILL_BACKWARDS = 0.1;

	/**
	 * Whether a kind is a shrimp or anchovy (giant krill).
	 */
	static boolean isKrill(int item)
	{
		return KRILL_FISH.contains(item);
	}

	/**
	 * Fewest of a kind a shore keeps while its spots bring it.
	 */
	static int least(int item)
	{
		return LEAST.getOrDefault(item, 1);
	}

	/**
	 * Client ticks until the next fish of a kind grows in on a ring, at random within its sort's gap.
	 */
	static int ringGap(int item, ThreadLocalRandom random)
	{
		return isKrill(item) ? random.nextInt(RING_SPAWN_KRILL_GAP_LEAST, RING_SPAWN_KRILL_GAP_MOST + 1)
			: isBig(item) ? random.nextInt(RING_SPAWN_BIG_GAP_LEAST, RING_SPAWN_BIG_GAP_MOST + 1)
			: random.nextInt(RING_SPAWN_OTHER_GAP_LEAST, RING_SPAWN_OTHER_GAP_MOST + 1);
	}

	/**
	 * Which kinds a kind schools with: sardines and herring together (1), tuna only with tuna (2), the rest (0).
	 */
	static int schoolSort(int item)
	{
		return item == ItemID.RAW_SARDINE || item == ItemID.RAW_HERRING ? 1 : item == ItemID.RAW_TUNA ? 2 : 0;
	}

	/**
	 * Whether a kind never schools: sharks, swordfish and (where they roam) lobsters.
	 */
	static boolean alone(int item)
	{
		return isBig(item) || item == ItemID.RAW_LOBSTER;
	}

	/**
	 * Whether a kind is a shark or swordfish.
	 */
	static boolean isBig(int item)
	{
		return BIG_FISH.contains(item);
	}

	/**
	 * How many of each kind a ring growing its own fish holds (drawn: the kinds the ring draws): each drawn kind's
	 * share of its spot's odds, of CIRCLE_MOST for lane fish (at most its RING_MOST) and of RING_SPAWN_KRILL_MOST
	 * for shrimps and anchovies; at least one each, and SWORDFISH_ROOM swordfish.
	 */
	static Map<Integer, Integer> ringTargets(int spot, IntPredicate drawn)
	{
		Map<Integer, Integer> targets = new HashMap<>();
		int[][] shore = SPOTS.get(spot);
		if (shore == null)
		{
			return targets;
		}
		int[] odds = new int[2];
		for (int k = 0; k < shore[0].length; k++)
		{
			odds[isKrill(shore[0][k]) ? 1 : 0] += drawn.test(shore[0][k]) ? shore[1][k] : 0;
		}
		for (int k = 0; k < shore[0].length; k++)
		{
			int item = shore[0][k];
			if (drawn.test(item))
			{
				boolean krill = isKrill(item);
				double share = shore[1][k] / (double) odds[krill ? 1 : 0];
				int most = krill ? RING_SPAWN_KRILL_MOST : RiverSpotFish.CIRCLE_MOST;
				int target = Math.max(item == ItemID.RAW_SWORDFISH ? SWORDFISH_ROOM : 1, (int) Math.round(share * most));
				targets.put(item, Math.min(target, RiverSpotFish.RING_MOST.getOrDefault(item, target)));
			}
		}
		return targets;
	}
}
