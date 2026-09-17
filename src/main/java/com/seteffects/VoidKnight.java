package com.seteffects;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Void Knight's top/robe/gloves are shared across all three combat-style configurations, unlike
 * every other set here - looking up "which set(s) contain this item" for a shared piece would
 * list all three possible bonuses regardless of which helm (if any) is actually equipped. This
 * resolves the one style that's actually active from the currently equipped helm instead, and
 * describes only that one; not modelled as an {@link ItemSet} at all.
 */
final class VoidKnight
{
	private static final int[] TOPS = {ItemID.PEST_VOID_KNIGHT_TOP, ItemID.ELITE_VOID_KNIGHT_TOP};
	private static final int[] ROBES = {ItemID.PEST_VOID_KNIGHT_ROBES, ItemID.ELITE_VOID_KNIGHT_ROBES};

	private VoidKnight()
	{
	}

	static boolean isVoidItem(int itemId)
	{
		return contains(TOPS, itemId)
			|| contains(ROBES, itemId)
			|| itemId == ItemID.PEST_VOID_KNIGHT_GLOVES
			|| itemId == ItemID.GAME_PEST_MELEE_HELM
			|| itemId == ItemID.GAME_PEST_ARCHER_HELM
			|| itemId == ItemID.GAME_PEST_MAGE_HELM;
	}

	/**
	 * Describes whichever Void configuration is currently active, based on the equipped helm and
	 * whether the top/robe are both the Elite variant (the Elite ranged/magic bonus needs both
	 * pieces upgraded together, not just one) - always returns something, since {@link #isVoidItem}
	 * having matched something means there's always at least something worth telling the player,
	 * even if it's just "you need a helm for this to do anything".
	 */
	static EffectLine describe(ItemContainer equipment)
	{
		int helm = mappedItem(equipment, EquipmentInventorySlot.HEAD);
		if (helm != ItemID.GAME_PEST_MELEE_HELM && helm != ItemID.GAME_PEST_ARCHER_HELM && helm != ItemID.GAME_PEST_MAGE_HELM)
		{
			return new EffectLine("Void Knight (No Helm)", "Equip a void knight helm to see its effect.");
		}

		boolean elite = mappedItem(equipment, EquipmentInventorySlot.BODY) == ItemID.ELITE_VOID_KNIGHT_TOP
			&& mappedItem(equipment, EquipmentInventorySlot.LEGS) == ItemID.ELITE_VOID_KNIGHT_ROBES;

		String name;
		String effect;
		if (helm == ItemID.GAME_PEST_MELEE_HELM)
		{
			name = elite ? "Elite Void Knight (Melee)" : "Void Knight (Melee)";
			effect = elite
				? "+10% melee accuracy and damage. Elite pieces give no extra melee bonus."
				: "+10% melee accuracy and damage.";
		}
		else if (helm == ItemID.GAME_PEST_ARCHER_HELM)
		{
			name = elite ? "Elite Void Knight (Ranged)" : "Void Knight (Ranged)";
			effect = elite
				? "+10% ranged accuracy and damage, plus +2.5% extra ranged damage (12.5% total)."
				: "+10% ranged accuracy and damage.";
		}
		else
		{
			name = elite ? "Elite Void Knight (Magic)" : "Void Knight (Magic)";
			effect = elite
				? "+45% magic accuracy and +5% magic damage."
				: "+45% magic accuracy.";
		}

		int worn = 1; // the helm itself, already matched above
		worn += contains(TOPS, mappedItem(equipment, EquipmentInventorySlot.BODY)) ? 1 : 0;
		worn += contains(ROBES, mappedItem(equipment, EquipmentInventorySlot.LEGS)) ? 1 : 0;
		worn += mappedItem(equipment, EquipmentInventorySlot.GLOVES) == ItemID.PEST_VOID_KNIGHT_GLOVES ? 1 : 0;

		return new EffectLine(name, worn, 4, effect);
	}

	private static int mappedItem(ItemContainer equipment, EquipmentInventorySlot slot)
	{
		Item item = equipment.getItem(slot.getSlotIdx());
		return item != null ? ItemVariationMapping.map(item.getId()) : -1;
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
