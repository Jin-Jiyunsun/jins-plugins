package com.seteffects;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Black mask and its imbued form ("Black mask (i)") collapse to the same base id under
 * {@link net.runelite.client.game.ItemVariationMapping} - confirmed via {@code
 * item_variations.json}: every {@code HARMLESS_}/{@code NZONE_}/{@code SW_}/{@code PVPA_}-prefixed
 * variant shares one chain, the same situation as the Salve amulet - so distinguishing imbued from
 * regular needs the raw, unmapped item id, same architectural shape as {@link SalveAmulet}. The
 * imbued form (obtained via Nightmare Zone points, Soul Wars zeal tokens, or a scroll of imbuing
 * from the Emir's Arena - the same three sources as the Salve amulet's imbued tiers) grants ranged
 * and magic bonuses too, not just melee, and removes the regular mask's attack penalties -
 * confirmed via the wiki, not assumed from the {@code NZONE_}/{@code SW_}/{@code PVPA_} naming
 * pattern alone.
 */
final class BlackMask
{
	private static final Set<Integer> REGULAR_IDS = idSet(
		ItemID.HARMLESS_BLACK_MASK_10, ItemID.HARMLESS_BLACK_MASK_9, ItemID.HARMLESS_BLACK_MASK_8,
		ItemID.HARMLESS_BLACK_MASK_7, ItemID.HARMLESS_BLACK_MASK_6, ItemID.HARMLESS_BLACK_MASK_5,
		ItemID.HARMLESS_BLACK_MASK_4, ItemID.HARMLESS_BLACK_MASK_3, ItemID.HARMLESS_BLACK_MASK_2,
		ItemID.HARMLESS_BLACK_MASK_1, ItemID.HARMLESS_BLACK_MASK);

	private static final Set<Integer> IMBUED_IDS = idSet(
		ItemID.NZONE_BLACK_MASK_10, ItemID.NZONE_BLACK_MASK_9, ItemID.NZONE_BLACK_MASK_8,
		ItemID.NZONE_BLACK_MASK_7, ItemID.NZONE_BLACK_MASK_6, ItemID.NZONE_BLACK_MASK_5,
		ItemID.NZONE_BLACK_MASK_4, ItemID.NZONE_BLACK_MASK_3, ItemID.NZONE_BLACK_MASK_2,
		ItemID.NZONE_BLACK_MASK_1, ItemID.NZONE_BLACK_MASK,
		ItemID.SW_BLACK_MASK_10, ItemID.SW_BLACK_MASK_9, ItemID.SW_BLACK_MASK_8,
		ItemID.SW_BLACK_MASK_7, ItemID.SW_BLACK_MASK_6, ItemID.SW_BLACK_MASK_5,
		ItemID.SW_BLACK_MASK_4, ItemID.SW_BLACK_MASK_3, ItemID.SW_BLACK_MASK_2,
		ItemID.SW_BLACK_MASK_1, ItemID.SW_BLACK_MASK,
		ItemID.PVPA_BLACK_MASK_10, ItemID.PVPA_BLACK_MASK_9, ItemID.PVPA_BLACK_MASK_8,
		ItemID.PVPA_BLACK_MASK_7, ItemID.PVPA_BLACK_MASK_6, ItemID.PVPA_BLACK_MASK_5,
		ItemID.PVPA_BLACK_MASK_4, ItemID.PVPA_BLACK_MASK_3, ItemID.PVPA_BLACK_MASK_2,
		ItemID.PVPA_BLACK_MASK_1, ItemID.PVPA_BLACK_MASK);

	private BlackMask()
	{
	}

	static boolean isBlackMask(int rawItemId)
	{
		return REGULAR_IDS.contains(rawItemId) || IMBUED_IDS.contains(rawItemId);
	}

	static EffectLine describe(int rawItemId)
	{
		if (IMBUED_IDS.contains(rawItemId))
		{
			return new EffectLine("Black mask (i)",
				"+16.67% increased melee accuracy and damage, +15% for ranged and magic, against your assigned "
					+ "Slayer task. Does not stack with the salve amulet.");
		}

		return new EffectLine("Black mask", "+16.67% increased melee accuracy and damage against your assigned Slayer task.");
	}

	private static Set<Integer> idSet(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}
}
