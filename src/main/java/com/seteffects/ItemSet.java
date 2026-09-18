package com.seteffects;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemVariationMapping;

class ItemSet
{
	private final EffectFamily family;
	private final String name;
	private final String effect;
	private final Map<EquipmentInventorySlot, int[]> slots;
	private final String amuletOfTheDamnedSynergy;

	ItemSet(EffectFamily family, String name, String effect, Map<EquipmentInventorySlot, int[]> slots)
	{
		this(family, name, effect, slots, null);
	}

	ItemSet(EffectFamily family, String name, String effect, Map<EquipmentInventorySlot, int[]> slots, @Nullable String amuletOfTheDamnedSynergy)
	{
		this.family = family;
		this.name = name;
		this.effect = effect;
		this.slots = slots;
		this.amuletOfTheDamnedSynergy = amuletOfTheDamnedSynergy;
	}

	EffectFamily getFamily()
	{
		return family;
	}

	@Nullable
	String getAmuletOfTheDamnedSynergy()
	{
		return amuletOfTheDamnedSynergy;
	}

	Set<Integer> allItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		for (int[] slotItemIds : slots.values())
		{
			for (int id : slotItemIds)
			{
				ids.add(id);
			}
		}
		return ids;
	}

	int totalSlots()
	{
		return slots.size();
	}

	int wornSlots(ItemContainer equipment)
	{
		int worn = 0;
		for (Map.Entry<EquipmentInventorySlot, int[]> entry : slots.entrySet())
		{
			Item item = equipment.getItem(entry.getKey().getSlotIdx());
			if (item == null)
			{
				continue;
			}

			int mappedId = ItemVariationMapping.map(item.getId());
			for (int acceptableId : entry.getValue())
			{
				if (acceptableId == mappedId)
				{
					worn++;
					break;
				}
			}
		}
		return worn;
	}

	boolean isFullyWorn(ItemContainer equipment)
	{
		return wornSlots(equipment) == totalSlots();
	}

	EffectLine describe(ItemContainer equipment)
	{
		return new EffectLine(name, wornSlots(equipment), totalSlots(), effect);
	}
}
