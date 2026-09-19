package com.seteffects;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Everything a built effect list (or tooltip) depends on, so the overlay and the tooltip can reuse
 * their last result while none of it has changed instead of rebuilding it every frame: what is
 * worn, the config, the warm clothing state, the box layout and the hovered item. Diary state is
 * only read (and only counts) when something worn actually depends on it, same as when describing.
 */
final class RenderKey
{
	private final int[] itemIds;
	private final int configVersion;
	private final boolean diaryKandarin;
	private final boolean diaryKourend;
	private final boolean diaryArdougne;
	private final boolean warmShown;
	private final int textWidth;
	private final List<EffectLine> vanillaOnly;
	private final Object font;
	private final int hoveredItemId;

	private RenderKey(int[] itemIds, int configVersion, boolean diaryKandarin, boolean diaryKourend, boolean diaryArdougne,
		boolean warmShown, int textWidth, List<EffectLine> vanillaOnly, Object font, int hoveredItemId)
	{
		this.itemIds = itemIds;
		this.configVersion = configVersion;
		this.diaryKandarin = diaryKandarin;
		this.diaryKourend = diaryKourend;
		this.diaryArdougne = diaryArdougne;
		this.warmShown = warmShown;
		this.textWidth = textWidth;
		this.vanillaOnly = vanillaOnly;
		this.font = font;
		this.hoveredItemId = hoveredItemId;
	}

	static RenderKey of(ItemContainer equipment, DiaryChecks diaries, int configVersion, boolean warmShown, int textWidth,
		List<EffectLine> vanillaOnly, Object font, int hoveredItemId)
	{
		Item[] items = equipment.getItems();
		int[] ids = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i] != null ? items[i].getId() : -1;
		}

		// Only bolts (ammo slot), a Slayer helmet (head slot) and the gloves of silence (hands slot)
		// depend on the diaries, and only the diary that item needs is read
		int ammo = idAt(ids, EquipmentInventorySlot.AMMO.getSlotIdx());
		int head = idAt(ids, EquipmentInventorySlot.HEAD.getSlotIdx());
		int gloves = idAt(ids, EquipmentInventorySlot.GLOVES.getSlotIdx());
		boolean bolts = ammo > 0 && EnchantedBolts.isEnchantedBolt(ItemVariationMapping.map(ammo));
		boolean slayerHelm = head > 0 && SlayerHelm.isSlayerHelm(head);
		boolean silence = gloves > 0 && ItemVariationMapping.map(gloves) == ItemID.HUNTING_SILENT_GLOVES;

		return new RenderKey(ids, configVersion,
			(bolts || hoveredIsBolt(hoveredItemId)) && diaries.hardKandarin(), slayerHelm && diaries.hardKourend(),
			(silence || hoveredIsSilence(hoveredItemId)) && diaries.hardArdougne(),
			warmShown, textWidth, vanillaOnly, font, hoveredItemId);
	}

	private static int idAt(int[] ids, int slot)
	{
		return slot < ids.length ? ids[slot] : -1;
	}

	// A hovered item is described even when it isn't the one worn in that slot's usual place
	private static boolean hoveredIsBolt(int hoveredItemId)
	{
		return hoveredItemId > 0 && EnchantedBolts.isEnchantedBolt(ItemVariationMapping.map(hoveredItemId));
	}

	private static boolean hoveredIsSilence(int hoveredItemId)
	{
		return hoveredItemId > 0 && ItemVariationMapping.map(hoveredItemId) == ItemID.HUNTING_SILENT_GLOVES;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof RenderKey))
		{
			return false;
		}
		RenderKey other = (RenderKey) o;
		return configVersion == other.configVersion
			&& diaryKandarin == other.diaryKandarin
			&& diaryKourend == other.diaryKourend
			&& diaryArdougne == other.diaryArdougne
			&& warmShown == other.warmShown
			&& textWidth == other.textWidth
			&& hoveredItemId == other.hoveredItemId
			&& Arrays.equals(itemIds, other.itemIds)
			&& Objects.equals(vanillaOnly, other.vanillaOnly)
			&& Objects.equals(font, other.font);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(Arrays.hashCode(itemIds), configVersion, diaryKandarin, diaryKourend, diaryArdougne, warmShown, textWidth,
			vanillaOnly, font, hoveredItemId);
	}
}
