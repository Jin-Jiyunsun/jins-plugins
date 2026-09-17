package com.seteffects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Raw (unwrapped) effect description lines for currently equipped gear - shared by the hover
 * tooltip and the overlay panel, which each wrap/join these differently for their own renderer.
 */
final class EquippedEffects
{
	private EquippedEffects()
	{
	}

	/**
	 * @param rawItemId the item id as-is, not yet run through {@link ItemVariationMapping} - some
	 *                  items (Salve amulet's tiers) need to distinguish variants that
	 *                  {@code ItemVariationMapping} itself collapses into one, so the mapping is
	 *                  done internally here rather than by the caller.
	 */
	static List<EffectLine> describeItem(ItemContainer equipment, int rawItemId)
	{
		List<EffectLine> lines = new ArrayList<>();
		int mappedItemId = ItemVariationMapping.map(rawItemId);
		appendItemEffects(lines, equipment, mappedItemId, new HashSet<>());

		if (VoidKnight.isVoidItem(mappedItemId))
		{
			lines.add(VoidKnight.describe(equipment));
		}

		if (SalveAmulet.isSalveAmulet(rawItemId))
		{
			lines.add(SalveAmulet.describe(rawItemId));
		}

		return lines;
	}

	/**
	 * Every recognized effect across all currently equipped items, deduped so a multi-piece set
	 * worn several times over (e.g. all four Ahrim's pieces) only describes itself once. Void
	 * Knight and the Salve amulet are each resolved separately and once, not per-piece, since
	 * Void's pieces span up to four equipped slots and would otherwise repeat, and the Salve
	 * amulet's tiers only differ by raw id, which the generic per-item pass doesn't see.
	 */
	static List<EffectLine> describeEquipment(ItemContainer equipment)
	{
		List<EffectLine> lines = new ArrayList<>();
		Set<ItemSet> seenSets = new HashSet<>();
		boolean hasVoidItem = false;
		int salveAmuletRawId = -1;

		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			Item item = equipment.getItem(slot.getSlotIdx());
			if (item != null)
			{
				int rawItemId = item.getId();
				int mappedItemId = ItemVariationMapping.map(rawItemId);
				appendItemEffects(lines, equipment, mappedItemId, seenSets);
				hasVoidItem |= VoidKnight.isVoidItem(mappedItemId);
				if (SalveAmulet.isSalveAmulet(rawItemId))
				{
					salveAmuletRawId = rawItemId;
				}
			}
		}

		if (hasVoidItem)
		{
			lines.add(VoidKnight.describe(equipment));
		}

		if (salveAmuletRawId != -1)
		{
			lines.add(SalveAmulet.describe(salveAmuletRawId));
		}

		return lines;
	}

	private static void appendItemEffects(List<EffectLine> lines, ItemContainer equipment, int itemId, Set<ItemSet> seenSets)
	{
		List<ItemSet> sets = SetEffectsData.SETS_BY_ITEM.get(itemId);
		if (sets != null)
		{
			for (ItemSet set : sets)
			{
				if (seenSets.add(set))
				{
					lines.add(set.describe(equipment));
				}
			}
		}

		SingleItemEffect single = SetEffectsData.SINGLE_ITEM_EFFECTS.get(itemId);
		if (single != null)
		{
			lines.add(single.describe());
		}

		if (itemId == ItemID.DAMNED_AMULET)
		{
			lines.add(amuletOfTheDamnedEffect(equipment));
		}
	}

	private static EffectLine amuletOfTheDamnedEffect(ItemContainer equipment)
	{
		for (ItemSet set : SetEffectsData.ITEM_SETS)
		{
			String synergy = set.getAmuletOfTheDamnedSynergy();
			if (synergy != null && set.isFullyWorn(equipment))
			{
				return new EffectLine("Amulet of the damned", synergy);
			}
		}

		return new EffectLine("Amulet of the damned", "Grants a bonus specific to whichever full Barrows set you're wearing.");
	}
}
