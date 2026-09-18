package com.seteffects;

import net.runelite.api.gameval.ItemID;

/**
 * Every Salve amulet tier - base, enchanted (e), imbued (i), and enchanted+imbued (ei), including
 * all three "training room" sources for the imbued forms (Nightmare Zone, Soul Wars, PvP Arena -
 * all three are genuinely obtainable, not decoys, since imbuing requires one of those reward
 * systems or a scroll of imbuing) - share one {@code ItemVariationMapping} chain with the plain
 * base amulet as its base. That means {@code ItemVariationMapping.map()} can't distinguish tiers
 * at all (unlike Void Knight's elite/regular split, which survives mapping); this checks the raw,
 * unmapped item id instead. Not modelled as a {@link SingleItemEffect} for the same reason.
 */
final class SalveAmulet
{
	// Misleadingly named in gameval (a decompiled-name artifact) - this constant is the Salve amulet
	private static final int BASE = ItemID.CRYSTALSHARD_NECKLACE;
	private static final int ENCHANTED = ItemID.LOTR_CRYSTALSHARD_NECKLACE_UPGRADE;
	private static final int[] IMBUED = {ItemID.NZONE_SALVE_AMULET, ItemID.SW_SALVE_AMULET, ItemID.PVPA_SALVE_AMULET};
	private static final int[] ENCHANTED_IMBUED = {ItemID.NZONE_SALVE_AMULET_E, ItemID.SW_SALVE_AMULET_E, ItemID.PVPA_SALVE_AMULET_E};

	private SalveAmulet()
	{
	}

	static boolean isSalveAmulet(int rawItemId)
	{
		return rawItemId == BASE
			|| rawItemId == ENCHANTED
			|| contains(IMBUED, rawItemId)
			|| contains(ENCHANTED_IMBUED, rawItemId);
	}

	static EffectLine describe(int rawItemId)
	{
		if (contains(ENCHANTED_IMBUED, rawItemId))
		{
			return new EffectLine("Salve amulet (ei)", "+20% accuracy and damage in every combat style against the undead. Does not stack with the black mask or Slayer helmet.");
		}

		if (contains(IMBUED, rawItemId))
		{
			return new EffectLine("Salve amulet (i)", "+16.67% melee/ranged accuracy and damage, +15% magic accuracy and damage against the undead. Does not stack with the black mask or Slayer helmet.");
		}

		if (rawItemId == ENCHANTED)
		{
			return new EffectLine("Salve amulet (e)", "+20% melee accuracy and damage against the undead. Does not stack with the black mask or Slayer helmet.");
		}

		return new EffectLine("Salve amulet", "+16.67% melee accuracy and damage against the undead. Does not stack with the black mask or Slayer helmet.");
	}

	private static boolean contains(int[] ids, int itemId)
	{
		for (int id : ids)
		{
			if (id == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
