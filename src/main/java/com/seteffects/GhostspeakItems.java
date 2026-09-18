package com.seteffects;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Things that let you speak to ghosts: the ghostspeak amulet (its enchanted version shares the
 * same variation chain) and the Morytania legs 2, 3 and 4 (medium, hard and elite diary), which
 * act as one when worn. The legs' chain also holds the easy legs, which don't, so they are matched
 * on the raw id. Both are one effect, so when both are worn only the legs are listed (the less
 * obvious source); see {@link EquippedEffects}.
 */
final class GhostspeakItems
{
	private static final int AMULET_CHAIN = ItemVariationMapping.map(ItemID.AMULET_OF_GHOSTSPEAK);
	private static final String TEXT = "Woooo wooo woooooo. (You can speak to ghosts!)";

	private GhostspeakItems()
	{
	}

	static boolean isAmulet(int rawItemId)
	{
		return ItemVariationMapping.map(rawItemId) == AMULET_CHAIN;
	}

	static boolean isMorytaniaLegs(int rawItemId)
	{
		return rawItemId == ItemID.MORYTANIA_LEGS_MEDIUM
			|| rawItemId == ItemID.MORYTANIA_LEGS_HARD
			|| rawItemId == ItemID.MORYTANIA_LEGS_ELITE;
	}

	static boolean isGhostspeakItem(int rawItemId)
	{
		return isAmulet(rawItemId) || isMorytaniaLegs(rawItemId);
	}

	static EffectLine describe(int rawItemId)
	{
		return new EffectLine(isMorytaniaLegs(rawItemId) ? "Morytania legs" : "Ghostspeak amulet", TEXT);
	}
}
