package com.seteffects;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * The abyssal lantern: what it does depends on the logs it was lit with, but every version (unlit,
 * each log type and the coloured logs) collapses to one {@code ItemVariationMapping} chain, so -
 * same as the Salve amulet - telling them apart needs the raw item id. Membership is checked
 * through the mapping so a version added to that chain later is still recognised (as unlit-looking
 * text, at worst).
 */
final class AbyssalLantern
{
	private static final int CHAIN = ItemVariationMapping.map(ItemID.ABYSSAL_LANTERN);

	private AbyssalLantern()
	{
	}

	static boolean isAbyssalLantern(int rawItemId)
	{
		return ItemVariationMapping.map(rawItemId) == CHAIN;
	}

	static EffectLine describe(int rawItemId)
	{
		switch (rawItemId)
		{
			case ItemID.ABYSSAL_LANTERN_NORMAL:
			case ItemID.ABYSSAL_LANTERN_NORMAL_BLUE:
			case ItemID.ABYSSAL_LANTERN_NORMAL_RED:
			case ItemID.ABYSSAL_LANTERN_NORMAL_WHITE:
			case ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE:
			case ItemID.ABYSSAL_LANTERN_NORMAL_GREEN:
				return new EffectLine("Abyssal lantern (normal)",
					"Increases the chance of finding portal talismans by 10% in Guardians of the Rift.");
			case ItemID.ABYSSAL_LANTERN_OAK:
				return new EffectLine("Abyssal lantern (oak)",
					"5% chance to double your contribution points for an action, and increases the point contribution cap by 5% in Guardians of the Rift.");
			case ItemID.ABYSSAL_LANTERN_WILLOW:
				return new EffectLine("Abyssal lantern (willow)", "Increases the amount of runes crafted by 5%.");
			case ItemID.ABYSSAL_LANTERN_MAPLE:
				return new EffectLine("Abyssal lantern (maple)",
					"Increases the chance of finding portal talismans by 20% in Guardians of the Rift.");
			case ItemID.ABYSSAL_LANTERN_YEW:
				return new EffectLine("Abyssal lantern (yew)",
					"10% chance to double your contribution points for an action, and increases the point contribution cap by 10% in Guardians of the Rift.");
			case ItemID.ABYSSAL_LANTERN_BLISTERWOOD:
				return new EffectLine("Abyssal lantern (blisterwood)", "Increases the amount of blood runes crafted by 20%.");
			case ItemID.ABYSSAL_LANTERN_MAGIC:
				return new EffectLine("Abyssal lantern (magic)", "Increases the amount of runes crafted by 10%.");
			case ItemID.ABYSSAL_LANTERN_REDWOOD:
				return new EffectLine("Abyssal lantern (redwood)",
					"Essence pouches do not degrade. Also has the oak and willow effects: 5% chance to double contribution points and +5% cap in Guardians of the Rift, and +5% runes crafted.");
			default:
				return new EffectLine("Abyssal lantern", "Light with six logs to gain a Runecrafting effect based on the log type.");
		}
	}
}
