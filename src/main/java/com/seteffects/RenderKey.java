package com.seteffects;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
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
	private final boolean warmShown;
	private final int textWidth;
	private final List<EffectLine> vanillaOnly;
	private final String fallback;
	private final Object font;
	private final int hoveredItemId;

	private RenderKey(int[] itemIds, int configVersion, boolean diaryKandarin, boolean diaryKourend, boolean warmShown,
		int textWidth, List<EffectLine> vanillaOnly, String fallback, Object font, int hoveredItemId)
	{
		this.itemIds = itemIds;
		this.configVersion = configVersion;
		this.diaryKandarin = diaryKandarin;
		this.diaryKourend = diaryKourend;
		this.warmShown = warmShown;
		this.textWidth = textWidth;
		this.vanillaOnly = vanillaOnly;
		this.fallback = fallback;
		this.font = font;
		this.hoveredItemId = hoveredItemId;
	}

	static RenderKey of(ItemContainer equipment, DiaryChecks diaries, int configVersion, boolean warmShown, int textWidth,
		List<EffectLine> vanillaOnly, String fallback, Object font, int hoveredItemId)
	{
		Item[] items = equipment.getItems();
		int[] ids = new int[items.length];
		boolean diaryRelevant = false;
		for (int i = 0; i < items.length; i++)
		{
			int id = items[i] != null ? items[i].getId() : -1;
			ids[i] = id;
			if (id > 0)
			{
				diaryRelevant |= EnchantedBolts.isEnchantedBolt(ItemVariationMapping.map(id)) || SlayerHelm.isSlayerHelm(id);
			}
		}

		return new RenderKey(ids, configVersion,
			diaryRelevant && diaries.hardKandarin(), diaryRelevant && diaries.hardKourend(),
			warmShown, textWidth, vanillaOnly, fallback, font, hoveredItemId);
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
			&& warmShown == other.warmShown
			&& textWidth == other.textWidth
			&& hoveredItemId == other.hoveredItemId
			&& Arrays.equals(itemIds, other.itemIds)
			&& Objects.equals(vanillaOnly, other.vanillaOnly)
			&& Objects.equals(fallback, other.fallback)
			&& Objects.equals(font, other.font);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(Arrays.hashCode(itemIds), configVersion, diaryKandarin, diaryKourend, warmShown, textWidth,
			vanillaOnly, fallback, font, hoveredItemId);
	}
}
