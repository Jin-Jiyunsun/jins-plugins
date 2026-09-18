package com.seteffects;

import net.runelite.api.gameval.ItemID;

/**
 * All four tiers of Rada's blessing (rewards from the Kourend & Kebos Diary) collapse to one
 * {@code ItemVariationMapping} chain - confirmed via {@code item_variations.json}
 * ("radas blessing": {@code [22803, 22941, 22943, 22945, 22947]}, the dummy placeholder plus the
 * four real tiers) - so, same as the Salve amulet, distinguishing tiers needs the raw item id.
 */
final class RadasBlessing
{
	private RadasBlessing()
	{
	}

	static boolean isRadasBlessing(int rawItemId)
	{
		return rawItemId == ItemID.ZEAH_BLESSING_EASY
			|| rawItemId == ItemID.ZEAH_BLESSING_MEDIUM
			|| rawItemId == ItemID.ZEAH_BLESSING_HARD
			|| rawItemId == ItemID.ZEAH_BLESSING_ELITE;
	}

	static EffectLine describe(int rawItemId)
	{
		if (rawItemId == ItemID.ZEAH_BLESSING_ELITE)
		{
			return new EffectLine("Rada's blessing 4", "+8% chance of catching an extra fish while Fishing.");
		}

		if (rawItemId == ItemID.ZEAH_BLESSING_HARD)
		{
			return new EffectLine("Rada's blessing 3", "+6% chance of catching an extra fish while Fishing.");
		}

		if (rawItemId == ItemID.ZEAH_BLESSING_MEDIUM)
		{
			return new EffectLine("Rada's blessing 2", "+4% chance of catching an extra fish while Fishing.");
		}

		return new EffectLine("Rada's blessing 1", "+2% chance of catching an extra fish while Fishing.");
	}
}
