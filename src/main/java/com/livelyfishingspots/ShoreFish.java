package com.livelyfishingspots;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

	// Shore shrimps and anchovies (giant krill models, kept apart from the sea's).
	static final int SHRIMP = FishModels.SHORE_SHRIMP;
	static final int ANCHOVIES = FishModels.SHORE_ANCHOVIES;
	// Shore spots, by NPC id: their fish and odds (each spot's odds sum to 100, so spots count evenly in a mix).
	static final Map<Integer, int[][]> SPOTS = Map.of(
		NpcID._0_44_53_SALTFISH, new int[][]{
			{SHRIMP, ANCHOVIES, ItemID.RAW_SARDINE, ItemID.RAW_HERRING}, {12, 4, 42, 42}},
		NpcID._0_44_53_RAREFISH, new int[][]{
			{ItemID.RAW_LOBSTER, ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH}, {15, 70, 15}},
		NpcID._0_44_53_MEMBERFISH, new int[][]{
			{ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS, ItemID.RAW_SHARK}, {32, 32, 32, 4}});
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
	// The fish each menu option draws at shore spots, by spot (Harpoon differs between them).
	static final Map<Integer, Map<String, int[]>> OPTIONS = Map.of(
		NpcID._0_44_53_SALTFISH, Map.of("net", new int[]{SHRIMP, ANCHOVIES},
			"bait", new int[]{ItemID.RAW_SARDINE, ItemID.RAW_HERRING}),
		NpcID._0_44_53_RAREFISH, Map.of("cage", new int[]{ItemID.RAW_LOBSTER},
			"harpoon", new int[]{ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH}),
		NpcID._0_44_53_MEMBERFISH, Map.of("big net", new int[]{ItemID.RAW_MACKEREL, ItemID.RAW_COD, ItemID.RAW_BASS},
			"harpoon", new int[]{ItemID.RAW_SHARK}));
	// Lakes baked with "rings grow" (places too tight for fish to swim in to a spot): there none join or head for
	// one; while a spot is fished, fish of the kinds it draws grow in on its lanes, and shrink away once it isn't,
	// apart from the lake's own fish. At most so many shrimps and anchovies grown in, and client ticks between fish
	// grown in (shrimps and anchovies quicker; after a shark or swordfish, no other for a while).
	static final int RING_SPAWN_KRILL_MOST = 30;
	static final int RING_SPAWN_GAP_LEAST = 25;
	static final int RING_SPAWN_GAP_MOST = 75;
	static final int RING_SPAWN_KRILL_GAP_LEAST = 8;
	static final int RING_SPAWN_KRILL_GAP_MOST = 20;
	static final int RING_SPAWN_BIG_GAP_LEAST = 125;
	static final int RING_SPAWN_BIG_GAP_MOST = 250;
	// Swordfish turn this share as fast, so they take wider turns.
	static final double SWORDFISH_TURN = 0.7;
	// Lobsters circle this much higher than their sink, shrimps and anchovies this much (local units).
	static final double CIRCLE_RISE = 12;
	static final double KRILL_CIRCLE_RISE = 30;
	// Shrimps and anchovies come no nearer the surface than this (local units).
	static final int KRILL_TOP = 15;
	// Sharks and swordfish: never school (one picked to lead a school comes alone, like sturgeon), always take a
	// circle's outer lane, and grow in on a ring one at a time.
	static final Set<Integer> BIG_FISH = Set.of(ItemID.RAW_SHARK, ItemID.RAW_SWORDFISH);
	// Fewest of a kind a shore keeps while its spots bring it (others 1).
	private static final Map<Integer, Integer> LEAST = Map.of(ItemID.RAW_SWORDFISH, 2);
	// Shrimps and anchovies (giant krill): only while their spot is fished, growing in on its ring (any shore), and
	// there take any lane at random, without spacing out (spots with them have a third, innermost lane for them only).
	static final Set<Integer> KRILL_FISH = Set.of(SHRIMP, ANCHOVIES);
	// Kinds that never roam a shore: they only grow in on a fished spot's ring, on any shore.
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
	 * Whether a kind is a shark or swordfish.
	 */
	static boolean isBig(int item)
	{
		return BIG_FISH.contains(item);
	}

	/**
	 * Of the kinds wanted in a ring, the one furthest below its target (ties by chance), so catches can't leave it
	 * all one kind.
	 */
	static int neediestInRing(List<Integer> wanted, Map<Integer, Integer> targets, Map<Integer, Integer> there,
		ThreadLocalRandom random)
	{
		int best = wanted.get(0);
		double most = Double.NEGATIVE_INFINITY;
		for (int item : wanted)
		{
			double need = 1 - there.getOrDefault(item, 0) / (double) targets.getOrDefault(item, 1)
				+ random.nextDouble() * 0.01;
			if (need > most)
			{
				most = need;
				best = item;
			}
		}
		return best;
	}

	/**
	 * How many of each kind a ring growing its own fish holds (drawn: the kinds the ring draws): each drawn kind's
	 * share of its spot's odds, of CIRCLE_MOST for lane fish (at most its RING_MOST) and of RING_SPAWN_KRILL_MOST
	 * for shrimps and anchovies; at least one each.
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
				int target = Math.max(1, (int) Math.round(share * most));
				targets.put(item, Math.min(target, RiverSpotFish.RING_MOST.getOrDefault(item, target)));
			}
		}
		return targets;
	}
}
