package com.seteffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
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
	 * @param diaries diary state, read only when something being described depends on it (enchanted
	 *                bolts, a Slayer helmet standing in for a Shayzien helm)
	 * @param rawItemId the item id as-is, not yet run through {@link ItemVariationMapping} - some
	 *                  items (Salve amulet's tiers) need to distinguish variants that
	 *                  {@code ItemVariationMapping} itself collapses into one, so the mapping is
	 *                  done internally here rather than by the caller.
	 */
	static List<EffectLine> describeItem(ItemContainer equipment, int rawItemId, Predicate<EffectFamily> enabled, boolean verbose, DiaryChecks diaries)
	{
		List<EffectLine> lines = new ArrayList<>();
		int mappedItemId = ItemVariationMapping.map(rawItemId);
		appendItemEffects(lines, equipment, mappedItemId, new HashSet<>(), enabled);

		if (VoidKnight.isVoidItem(mappedItemId) && enabled.test(EffectFamily.VOID_KNIGHT))
		{
			lines.add(VoidKnight.describe(equipment));
		}

		if (SalveAmulet.isSalveAmulet(rawItemId) && enabled.test(EffectFamily.SALVE_AMULET))
		{
			lines.add(SalveAmulet.describe(rawItemId));
		}

		if (BlackMask.isBlackMask(rawItemId) && enabled.test(EffectFamily.BLACK_MASK))
		{
			lines.add(BlackMask.describe(rawItemId));
		}

		if (SlayerHelm.isSlayerHelm(rawItemId) && enabled.test(EffectFamily.SLAYER_HELMET))
		{
			lines.add(SlayerHelm.describe(rawItemId));
		}

		if (RadasBlessing.isRadasBlessing(rawItemId) && enabled.test(EffectFamily.RADAS_BLESSING))
		{
			lines.add(RadasBlessing.describe(rawItemId));
		}

		if (AbyssalLantern.isAbyssalLantern(rawItemId) && enabled.test(EffectFamily.ABYSSAL_LANTERN))
		{
			lines.add(AbyssalLantern.describe(rawItemId));
		}

		if (GhostspeakItems.isGhostspeakItem(rawItemId) && enabled.test(EffectFamily.GHOSTSPEAK))
		{
			lines.add(GhostspeakItems.describe(rawItemId));
		}

		if (mappedItemId == ItemID.HUNTING_SILENT_GLOVES && enabled.test(EffectFamily.GLOVES_OF_SILENCE))
		{
			lines.add(GlovesOfSilence.describe(diaries::hardArdougne));
		}

		if (RingOfTheGods.isImbuedRing(rawItemId) && enabled.test(EffectFamily.RING_OF_THE_GODS))
		{
			lines.add(RingOfTheGods.describe());
		}

		if (PerPieceSets.InquisitorArmour.isInquisitorItem(mappedItemId) && enabled.test(EffectFamily.INQUISITORS))
		{
			lines.addAll(PerPieceSets.InquisitorArmour.describe(equipment, verbose));
		}

		if (PerPieceSets.VirtusRobes.isVirtusItem(mappedItemId) && enabled.test(EffectFamily.VIRTUS))
		{
			lines.addAll(PerPieceSets.VirtusRobes.describe(equipment, verbose));
		}

		if (PerPieceSets.CrystalArmour.isCrystalItem(rawItemId) && enabled.test(EffectFamily.CRYSTAL_ARMOUR))
		{
			lines.addAll(PerPieceSets.CrystalArmour.describe(equipment, verbose));
		}

		if (PerPieceSets.KyattHunterGear.isKyattItem(mappedItemId) && enabled.test(EffectFamily.HUNTER_GEAR))
		{
			lines.addAll(PerPieceSets.KyattHunterGear.describe(equipment, verbose));
		}

		if (PerPieceSets.ShayzienArmour.isShayzienItem(mappedItemId) && enabled.test(EffectFamily.SHAYZIEN))
		{
			lines.addAll(PerPieceSets.ShayzienArmour.describe(equipment, verbose, diaries::hardKourend));
		}

		if (PerPieceSets.SwampbarkArmour.isSwampbarkItem(mappedItemId) && enabled.test(EffectFamily.SWAMPBARK))
		{
			lines.addAll(PerPieceSets.SwampbarkArmour.describe(equipment, verbose));
		}

		if (PerPieceSets.BloodbarkArmour.isBloodbarkItem(mappedItemId) && enabled.test(EffectFamily.BLOODBARK))
		{
			lines.addAll(PerPieceSets.BloodbarkArmour.describe(equipment, verbose));
		}

		if (PerPieceSets.GracefulOutfit.isGracefulItem(mappedItemId) && enabled.test(EffectFamily.GRACEFUL))
		{
			lines.addAll(PerPieceSets.GracefulOutfit.describe(equipment, verbose));
		}

		for (PerPieceSets.SkillingOutfit outfit : PerPieceSets.SkillingOutfit.ALL)
		{
			if (outfit.isItem(mappedItemId) && enabled.test(outfit.family))
			{
				lines.addAll(outfit.describe(equipment, verbose));
			}
		}

		if (EnchantedBolts.isEnchantedBolt(mappedItemId) && enabled.test(EffectFamily.ENCHANTED_BOLTS))
		{
			lines.add(EnchantedBolts.describe(mappedItemId, diaries::hardKandarin));
		}

		if (mappedItemId == ItemID.SKILLCAPE_AGILITY && enabled.test(EffectFamily.CAPES_OF_ACCOMPLISHMENT)
			&& !PerPieceSets.GracefulOutfit.isAnyPieceWorn(equipment))
		{
			lines.add(agilityCapeHint());
		}

		if (mappedItemId == ItemID.SKILLCAPE_MAX && enabled.test(EffectFamily.CAPES_OF_ACCOMPLISHMENT))
		{
			lines.add(maxCapeEffect(equipment));
		}

		if (mappedItemId == ItemID.DAMNED_AMULET && enabled.test(EffectFamily.AMULET_OF_THE_DAMNED))
		{
			lines.add(amuletOfTheDamnedEffect(equipment));
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
	static List<EffectLine> describeEquipment(ItemContainer equipment, Predicate<EffectFamily> enabled, boolean verbose, DiaryChecks diaries,
		List<EffectLine> vanillaOnlyLines, BooleanSupplier showWarmClothing)
	{
		List<EffectLine> lines = new ArrayList<>();
		Set<ItemSet> seenSets = new HashSet<>();
		boolean hasVoidItem = false;
		int salveAmuletRawId = -1;
		int blackMaskRawId = -1;
		int slayerHelmRawId = -1;
		int radasBlessingRawId = -1;
		int abyssalLanternRawId = -1;
		boolean hasGlovesOfSilence = false;
		int ghostspeakLegsRawId = -1;
		int ghostspeakAmuletRawId = -1;
		boolean hasImbuedRingOfTheGods = false;
		boolean hasInquisitorItem = false;
		boolean hasVirtusItem = false;
		boolean hasCrystalItem = false;
		boolean hasKyattItem = false;
		boolean hasSwampbarkItem = false;
		boolean hasShayzienItem = false;
		boolean hasAgilityCape = false;
		boolean hasMaxCape = false;
		int enchantedBoltId = -1;
		boolean hasBloodbarkItem = false;
		boolean hasGracefulItem = false;
		Set<PerPieceSets.SkillingOutfit> xpOutfits = new HashSet<>();
		boolean hasDamnedAmulet = false;
		int fullBarrowsLineIndex = -1;

		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			Item item = equipment.getItem(slot.getSlotIdx());
			if (item != null)
			{
				int rawItemId = item.getId();
				int mappedItemId = ItemVariationMapping.map(rawItemId);

				List<ItemSet> sets = SetEffectsData.SETS_BY_ITEM.get(mappedItemId);
				if (sets != null)
				{
					for (ItemSet set : sets)
					{
						if (enabled.test(set.getFamily()) && seenSets.add(set))
						{
							lines.add(set.describe(equipment));
							if (set.getAmuletOfTheDamnedSynergy() != null && set.isFullyWorn(equipment))
							{
								fullBarrowsLineIndex = lines.size() - 1;
							}
						}
					}
				}

				SingleItemEffect single = SetEffectsData.SINGLE_ITEM_EFFECTS.get(mappedItemId);
				if (single != null && enabled.test(single.getFamily()))
				{
					lines.add(single.describe());
				}

				hasDamnedAmulet |= mappedItemId == ItemID.DAMNED_AMULET;
				hasInquisitorItem |= PerPieceSets.InquisitorArmour.isInquisitorItem(mappedItemId);
				hasVirtusItem |= PerPieceSets.VirtusRobes.isVirtusItem(mappedItemId);
				hasCrystalItem |= PerPieceSets.CrystalArmour.isCrystalItem(rawItemId);
				hasKyattItem |= PerPieceSets.KyattHunterGear.isKyattItem(mappedItemId);
				hasSwampbarkItem |= PerPieceSets.SwampbarkArmour.isSwampbarkItem(mappedItemId);
				hasShayzienItem |= PerPieceSets.ShayzienArmour.isShayzienItem(mappedItemId);
				hasAgilityCape |= mappedItemId == ItemID.SKILLCAPE_AGILITY;
				hasMaxCape |= mappedItemId == ItemID.SKILLCAPE_MAX;
				if (EnchantedBolts.isEnchantedBolt(mappedItemId))
				{
					enchantedBoltId = mappedItemId;
				}
				hasBloodbarkItem |= PerPieceSets.BloodbarkArmour.isBloodbarkItem(mappedItemId);
				hasGracefulItem |= PerPieceSets.GracefulOutfit.isGracefulItem(mappedItemId);
				for (PerPieceSets.SkillingOutfit outfit : PerPieceSets.SkillingOutfit.ALL)
				{
					if (outfit.isItem(mappedItemId))
					{
						xpOutfits.add(outfit);
					}
				}
				hasVoidItem |= VoidKnight.isVoidItem(mappedItemId);
				if (SalveAmulet.isSalveAmulet(rawItemId))
				{
					salveAmuletRawId = rawItemId;
				}
				if (BlackMask.isBlackMask(rawItemId))
				{
					blackMaskRawId = rawItemId;
				}
				if (SlayerHelm.isSlayerHelm(rawItemId))
				{
					slayerHelmRawId = rawItemId;
				}
				if (RadasBlessing.isRadasBlessing(rawItemId))
				{
					radasBlessingRawId = rawItemId;
				}
				if (AbyssalLantern.isAbyssalLantern(rawItemId))
				{
					abyssalLanternRawId = rawItemId;
				}
				hasGlovesOfSilence |= mappedItemId == ItemID.HUNTING_SILENT_GLOVES;
				if (GhostspeakItems.isMorytaniaLegs(rawItemId))
				{
					ghostspeakLegsRawId = rawItemId;
				}
				else if (GhostspeakItems.isAmulet(rawItemId))
				{
					ghostspeakAmuletRawId = rawItemId;
				}
				hasImbuedRingOfTheGods |= RingOfTheGods.isImbuedRing(rawItemId);
			}
		}

		if (hasDamnedAmulet && enabled.test(EffectFamily.AMULET_OF_THE_DAMNED))
		{
			EffectLine amuletLine = amuletOfTheDamnedEffect(equipment);
			if (fullBarrowsLineIndex >= 0)
			{
				// Attach directly under its Barrows set's own line rather than wherever the
				// amulet slot happens to fall in EquipmentInventorySlot's iteration order relative
				// to other equipped items - continuesPrevious skips the usual blank-line gap
				lines.add(fullBarrowsLineIndex + 1, new EffectLine(amuletLine.name, amuletLine.effect, true));
			}
			else
			{
				lines.add(amuletLine);
			}
		}

		if (hasVoidItem && enabled.test(EffectFamily.VOID_KNIGHT))
		{
			lines.add(VoidKnight.describe(equipment));
		}

		if (salveAmuletRawId != -1 && enabled.test(EffectFamily.SALVE_AMULET))
		{
			lines.add(SalveAmulet.describe(salveAmuletRawId));
		}

		if (blackMaskRawId != -1 && enabled.test(EffectFamily.BLACK_MASK))
		{
			lines.add(BlackMask.describe(blackMaskRawId));
		}

		if (slayerHelmRawId != -1 && enabled.test(EffectFamily.SLAYER_HELMET))
		{
			lines.add(SlayerHelm.describe(slayerHelmRawId));
		}

		if (radasBlessingRawId != -1 && enabled.test(EffectFamily.RADAS_BLESSING))
		{
			lines.add(RadasBlessing.describe(radasBlessingRawId));
		}

		if (abyssalLanternRawId != -1 && enabled.test(EffectFamily.ABYSSAL_LANTERN))
		{
			lines.add(AbyssalLantern.describe(abyssalLanternRawId));
		}

		if (hasGlovesOfSilence && enabled.test(EffectFamily.GLOVES_OF_SILENCE))
		{
			lines.add(GlovesOfSilence.describe(diaries::hardArdougne));
		}

		// One effect from either source: the legs win when both are worn, as the less obvious source
		int ghostspeakRawId = ghostspeakLegsRawId != -1 ? ghostspeakLegsRawId : ghostspeakAmuletRawId;
		if (ghostspeakRawId != -1 && enabled.test(EffectFamily.GHOSTSPEAK))
		{
			lines.add(GhostspeakItems.describe(ghostspeakRawId));
		}

		if (hasImbuedRingOfTheGods && enabled.test(EffectFamily.RING_OF_THE_GODS))
		{
			lines.add(RingOfTheGods.describe());
		}

		if (hasInquisitorItem && enabled.test(EffectFamily.INQUISITORS))
		{
			lines.addAll(PerPieceSets.InquisitorArmour.describe(equipment, verbose));
		}

		if (hasVirtusItem && enabled.test(EffectFamily.VIRTUS))
		{
			lines.addAll(PerPieceSets.VirtusRobes.describe(equipment, verbose));
		}

		if (hasCrystalItem && enabled.test(EffectFamily.CRYSTAL_ARMOUR))
		{
			lines.addAll(PerPieceSets.CrystalArmour.describe(equipment, verbose));
		}

		if (hasKyattItem && enabled.test(EffectFamily.HUNTER_GEAR))
		{
			lines.addAll(PerPieceSets.KyattHunterGear.describe(equipment, verbose));
		}

		// Only a hint: once a graceful piece is worn the Graceful entry already counts the cape
		if (hasAgilityCape && enabled.test(EffectFamily.CAPES_OF_ACCOMPLISHMENT) && !PerPieceSets.GracefulOutfit.isAnyPieceWorn(equipment))
		{
			lines.add(agilityCapeHint());
		}

		if (hasMaxCape && enabled.test(EffectFamily.CAPES_OF_ACCOMPLISHMENT))
		{
			lines.add(maxCapeEffect(equipment));
		}

		if (hasShayzienItem && enabled.test(EffectFamily.SHAYZIEN))
		{
			lines.addAll(PerPieceSets.ShayzienArmour.describe(equipment, verbose, diaries::hardKourend));
		}

		if (enchantedBoltId != -1 && enabled.test(EffectFamily.ENCHANTED_BOLTS))
		{
			lines.add(EnchantedBolts.describe(enchantedBoltId, diaries::hardKandarin));
		}

		if (hasSwampbarkItem && enabled.test(EffectFamily.SWAMPBARK))
		{
			lines.addAll(PerPieceSets.SwampbarkArmour.describe(equipment, verbose));
		}

		if (hasBloodbarkItem && enabled.test(EffectFamily.BLOODBARK))
		{
			lines.addAll(PerPieceSets.BloodbarkArmour.describe(equipment, verbose));
		}

		if (hasGracefulItem && enabled.test(EffectFamily.GRACEFUL))
		{
			lines.addAll(PerPieceSets.GracefulOutfit.describe(equipment, verbose));
		}

		for (PerPieceSets.SkillingOutfit outfit : xpOutfits)
		{
			if (enabled.test(outfit.family))
			{
				lines.addAll(outfit.describe(equipment, verbose));
			}
		}

		if (showWarmClothing.getAsBoolean())
		{
			lines.add(WarmClothing.describe(equipment));
		}

		List<EffectLine> sorted = sortedByName(lines);
		if (enabled.test(EffectFamily.VANILLA_ONLY_SETS))
		{
			// Appended after the alphabetical list rather than sorted into it
			sorted.addAll(vanillaOnlyLines);
		}
		return sorted;
	}

	/**
	 * Alphabetical by name, so the box reads as a stable, scannable list rather than whatever
	 * order {@code EquipmentInventorySlot} happens to enumerate slots in. A line with {@code
	 * continuesPrevious} set (the Amulet of the Damned's synergy line - see the amulet handling
	 * above) is kept glued to whichever line precedes it rather than sorted independently, since
	 * it isn't a standalone effect on its own.
	 */
	private static List<EffectLine> sortedByName(List<EffectLine> lines)
	{
		List<List<EffectLine>> clusters = new ArrayList<>();
		for (EffectLine line : lines)
		{
			if (line.continuesPrevious && !clusters.isEmpty())
			{
				clusters.get(clusters.size() - 1).add(line);
			}
			else
			{
				List<EffectLine> cluster = new ArrayList<>();
				cluster.add(line);
				clusters.add(cluster);
			}
		}

		clusters.sort(Comparator.comparing(cluster -> cluster.get(0).name, String.CASE_INSENSITIVE_ORDER));

		List<EffectLine> sorted = new ArrayList<>();
		for (List<EffectLine> cluster : clusters)
		{
			sorted.addAll(cluster);
		}
		return sorted;
	}

	private static void appendItemEffects(List<EffectLine> lines, ItemContainer equipment, int itemId, Set<ItemSet> seenSets, Predicate<EffectFamily> enabled)
	{
		List<ItemSet> sets = SetEffectsData.SETS_BY_ITEM.get(itemId);
		if (sets != null)
		{
			for (ItemSet set : sets)
			{
				if (enabled.test(set.getFamily()) && seenSets.add(set))
				{
					lines.add(set.describe(equipment));
				}
			}
		}

		SingleItemEffect single = SetEffectsData.SINGLE_ITEM_EFFECTS.get(itemId);
		if (single != null && enabled.test(single.getFamily()))
		{
			lines.add(single.describe());
		}
	}

	private static EffectLine agilityCapeHint()
	{
		return new EffectLine("Agility cape", "Counts as a graceful cape for the Graceful outfit bonus.");
	}

	/** The graceful sentence is dropped once a graceful piece is worn: the Graceful entry already counts the cape. */
	private static EffectLine maxCapeEffect(ItemContainer equipment)
	{
		String effect = "Has the perks of every skill cape.";
		if (!PerPieceSets.GracefulOutfit.isAnyPieceWorn(equipment))
		{
			effect += " Counts as a graceful cape for the Graceful outfit bonus.";
		}
		return new EffectLine("Max cape", effect);
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
