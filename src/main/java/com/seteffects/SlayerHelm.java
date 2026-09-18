package com.seteffects;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Slayer helmet and its imbued form collapse into one enormous {@link ItemVariationMapping} chain
 * along with every cosmetic recolour of both (black/green/red/purple/turquoise/hydra/twisted/jad/
 * verzik/zuk/araxyte/hooded/league, ~60 ids total, confirmed via {@code item_variations.json}) -
 * same raw-id-collapse problem as the Salve amulet and Black mask, just at a much larger scale
 * because of how many recolours exist. Rather than hand-listing all ~60 ids, membership in the
 * whole chain is detected dynamically by comparing against the known base id's own mapped value
 * (self-updating if Jagex adds another recolour to the same chain later) - only the smaller set of
 * *regular* (non-imbued) raw ids needs to be hand-listed, since anything in the chain that isn't
 * one of those is, by elimination, some imbued variant.
 */
final class SlayerHelm
{
	private static final int CHAIN_ID = ItemVariationMapping.map(ItemID.SLAYER_HELM);

	private static final Set<Integer> REGULAR_IDS = idSet(
		ItemID.SLAYER_HELM, ItemID.SLAYER_HELM_BLACK, ItemID.SLAYER_HELM_GREEN, ItemID.SLAYER_HELM_RED,
		ItemID.SLAYER_HELM_PURPLE, ItemID.SLAYER_HELM_TURQUOISE, ItemID.SLAYER_HELM_HYDRA,
		ItemID.SLAYER_HELM_TWISTED, ItemID.SLAYER_HELM_JAD, ItemID.SLAYER_HELM_VERZIK,
		ItemID.SLAYER_HELM_ZUK, ItemID.SLAYER_HELM_ARAXYTE, ItemID.SLAYER_HELM_HOODED,
		ItemID.LEAGUE_6_SLAYER_HELM1, ItemID.LEAGUE_6_SLAYER_HELM2);

	private SlayerHelm()
	{
	}

	static boolean isSlayerHelm(int rawItemId)
	{
		return ItemVariationMapping.map(rawItemId) == CHAIN_ID;
	}

	static EffectLine describe(int rawItemId)
	{
		if (REGULAR_IDS.contains(rawItemId))
		{
			return new EffectLine("Slayer helmet", "+16.67% increased melee accuracy and damage against your assigned Slayer task.");
		}

		return new EffectLine("Slayer helmet (i)",
			"+16.67% increased melee accuracy and damage, +15% for ranged and magic, against your assigned "
				+ "Slayer task. Does not stack with the salve amulet.");
	}

	private static Set<Integer> idSet(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}
}
