package com.seteffects;

import net.runelite.api.gameval.ItemID;

/**
 * Only the imbued Ring of the gods has an effect (the holy wrench's extra Prayer restoration); the
 * plain ring is just stats. All versions share one {@code ItemVariationMapping} chain, so telling
 * them apart needs the raw, unmapped item id, the same way as {@link SalveAmulet}. The three imbued
 * ids are the Nightmare Zone, Soul Wars and PvP Arena imbuing sources.
 */
final class RingOfTheGods
{
	private static final int[] IMBUED = {ItemID.NZONE_ROTG, ItemID.SW_ROTG, ItemID.PVPA_ROTG};

	private RingOfTheGods()
	{
	}

	static boolean isImbuedRing(int rawItemId)
	{
		for (int id : IMBUED)
		{
			if (id == rawItemId)
			{
				return true;
			}
		}
		return false;
	}

	static EffectLine describe()
	{
		return new EffectLine("Ring of the gods (i)", "Increases the Prayer points restored by prayer potions, like the holy wrench. Does not stack with the wrench or Prayer cape.");
	}
}
