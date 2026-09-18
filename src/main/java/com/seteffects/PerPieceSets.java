package com.seteffects;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Multi-piece sets whose bonus is per-piece (not a flat full-set-only bonus), so their
 * description has to be built dynamically from which pieces are actually equipped right now,
 * rather than a static per-{@link ItemSet} sentence that always lists every piece regardless of
 * what's worn. Kept together in one file since each resolver is small and they all share the same
 * shape: a header line, one {@code continuesPrevious} white-labelled line per worn piece, and a
 * final "Current Bonus" line - rather than being scattered across one file per set.
 */
final class PerPieceSets
{
	private PerPieceSets()
	{
	}

	private static boolean isWorn(ItemContainer equipment, EquipmentInventorySlot slot, int expectedId)
	{
		Item item = equipment.getItem(slot.getSlotIdx());
		return item != null && ItemVariationMapping.map(item.getId()) == expectedId;
	}

	/**
	 * Inquisitor's armour: crush accuracy/damage bonus is per-piece only (great helm +0.5%,
	 * hauberk +1%, plateskirt +1%) - no separate full-set bonus on top, per the current
	 * post-rework mechanic (an earlier version of this text claimed an extra +2.5% for completing
	 * the set, which the wiki doesn't support - confirmed and corrected, 2026-09-18).
	 */
	static final class InquisitorArmour
	{
		private static final int HELM_ID = ItemID.INQUISITORS_HELM;
		private static final int BODY_ID = ItemID.INQUISITORS_BODY;
		private static final int LEGS_ID = ItemID.INQUISITORS_SKIRT;

		private static final int HELM_BONUS_TENTHS = 5;
		private static final int BODY_BONUS_TENTHS = 10;
		private static final int LEGS_BONUS_TENTHS = 10;

		private InquisitorArmour()
		{
		}

		static boolean isInquisitorItem(int mappedItemId)
		{
			return mappedItemId == HELM_ID
				|| mappedItemId == BODY_ID
				|| mappedItemId == LEGS_ID;
		}

		static List<EffectLine> describe(ItemContainer equipment)
		{
			boolean helm = isWorn(equipment, EquipmentInventorySlot.HEAD, HELM_ID);
			boolean body = isWorn(equipment, EquipmentInventorySlot.BODY, BODY_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			int worn = (helm ? 1 : 0) + (body ? 1 : 0) + (legs ? 1 : 0);

			int totalTenths = (helm ? HELM_BONUS_TENTHS : 0) + (body ? BODY_BONUS_TENTHS : 0) + (legs ? LEGS_BONUS_TENTHS : 0);

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Inquisitor's armour", worn, 3,
				"Increases crush accuracy and damage while using the crush attack style."));

			if (helm)
			{
				lines.add(new EffectLine("Great helm", "+" + EffectLineFormat.formatTenths(HELM_BONUS_TENTHS) + "%.", true));
			}
			if (body)
			{
				lines.add(new EffectLine("Hauberk", "+" + EffectLineFormat.formatTenths(BODY_BONUS_TENTHS) + "%.", true));
			}
			if (legs)
			{
				lines.add(new EffectLine("Plateskirt", "+" + EffectLineFormat.formatTenths(LEGS_BONUS_TENTHS) + "%.", true));
			}

			lines.add(new EffectLine("Current Bonus", "+" + EffectLineFormat.formatTenths(totalTenths) + "%.", true));

			return lines;
		}
	}

	/**
	 * Virtus robes: magic damage bonus is per-piece (+2%), plus a further +3% per piece while
	 * using Ancient Magicks (5% total) - a separate additive bonus, not a doubling of the base.
	 */
	static final class VirtusRobes
	{
		private static final int MASK_ID = ItemID.VIRTUS_MASK;
		private static final int TOP_ID = ItemID.VIRTUS_TOP;
		private static final int LEGS_ID = ItemID.VIRTUS_LEGS;

		private static final int PER_PIECE_TENTHS = 20;
		private static final int PER_PIECE_ANCIENT_TENTHS = 50;

		private VirtusRobes()
		{
		}

		static boolean isVirtusItem(int mappedItemId)
		{
			return mappedItemId == MASK_ID
				|| mappedItemId == TOP_ID
				|| mappedItemId == LEGS_ID;
		}

		static List<EffectLine> describe(ItemContainer equipment)
		{
			boolean mask = isWorn(equipment, EquipmentInventorySlot.HEAD, MASK_ID);
			boolean top = isWorn(equipment, EquipmentInventorySlot.BODY, TOP_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			int worn = (mask ? 1 : 0) + (top ? 1 : 0) + (legs ? 1 : 0);

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Virtus robes", worn, 3,
				"Increases magic damage by " + EffectLineFormat.formatTenths(PER_PIECE_TENTHS)
					+ "% per piece, with an extra +" + EffectLineFormat.formatTenths(PER_PIECE_ANCIENT_TENTHS - PER_PIECE_TENTHS)
					+ "% per piece while using Ancient Magicks."));

			if (mask)
			{
				lines.add(new EffectLine("Virtus mask", "+" + EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "%.", true));
			}
			if (top)
			{
				lines.add(new EffectLine("Virtus robe top", "+" + EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "%.", true));
			}
			if (legs)
			{
				lines.add(new EffectLine("Virtus robe legs", "+" + EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "%.", true));
			}

			int totalTenths = worn * PER_PIECE_TENTHS;
			int totalAncientTenths = worn * PER_PIECE_ANCIENT_TENTHS;
			lines.add(new EffectLine("Current Bonus", "+" + EffectLineFormat.formatTenths(totalTenths)
				+ "% (+" + EffectLineFormat.formatTenths(totalAncientTenths) + "% on Ancient Magicks).", true));

			return lines;
		}
	}

	/**
	 * Crystal armour: accuracy/damage bonus with the crystal bow/bow of Faerdhinen is per-piece
	 * (helm +5%/+2.5%, body +15%/+7.5%, legs +10%/+5%, accuracy/damage respectively) - tallied out
	 * of 3 armour pieces only, not the weapon slot, matching how Inquisitor's/Virtus only tally
	 * their own armour pieces; the weapon requirement is mentioned in the header as context rather
	 * than tracked as a slot, the same way Inquisitor's crush-style and Virtus's Ancient Magicks
	 * requirements are mentioned without being verified. Unlike those two, this condenses down to
	 * a single line (current totals inline, no per-piece breakdown) - Jin found the multi-line
	 * per-piece layout too wordy/space-hungry for what's ultimately just two numbers.
	 */
	static final class CrystalArmour
	{
		private static final int HELM_ID = ItemID.GAUNTLET_HELMET_T1;
		private static final int BODY_ID = ItemID.GAUNTLET_CHESTPLATE_T1;
		private static final int LEGS_ID = ItemID.GAUNTLET_PLATELEGS_T1;

		private static final int HELM_ACCURACY_TENTHS = 50;
		private static final int HELM_DAMAGE_TENTHS = 25;
		private static final int BODY_ACCURACY_TENTHS = 150;
		private static final int BODY_DAMAGE_TENTHS = 75;
		private static final int LEGS_ACCURACY_TENTHS = 100;
		private static final int LEGS_DAMAGE_TENTHS = 50;

		private CrystalArmour()
		{
		}

		static boolean isCrystalItem(int mappedItemId)
		{
			return mappedItemId == HELM_ID
				|| mappedItemId == BODY_ID
				|| mappedItemId == LEGS_ID;
		}

		static EffectLine describe(ItemContainer equipment)
		{
			boolean helm = isWorn(equipment, EquipmentInventorySlot.HEAD, HELM_ID);
			boolean body = isWorn(equipment, EquipmentInventorySlot.BODY, BODY_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			int worn = (helm ? 1 : 0) + (body ? 1 : 0) + (legs ? 1 : 0);

			int accuracyTenths = (helm ? HELM_ACCURACY_TENTHS : 0) + (body ? BODY_ACCURACY_TENTHS : 0) + (legs ? LEGS_ACCURACY_TENTHS : 0);
			int damageTenths = (helm ? HELM_DAMAGE_TENTHS : 0) + (body ? BODY_DAMAGE_TENTHS : 0) + (legs ? LEGS_DAMAGE_TENTHS : 0);

			String effect = "Increases the damage (+" + EffectLineFormat.formatTenths(damageTenths)
				+ "%) and accuracy (+" + EffectLineFormat.formatTenths(accuracyTenths)
				+ "%) of the crystal bow or bow of Faerdhinen.";

			return new EffectLine("Crystal armour", worn, 3, effect);
		}
	}
}
