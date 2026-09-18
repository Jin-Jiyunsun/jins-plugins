package com.seteffects;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
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

		static List<EffectLine> describe(ItemContainer equipment, boolean verbose)
		{
			boolean helm = isWorn(equipment, EquipmentInventorySlot.HEAD, HELM_ID);
			boolean body = isWorn(equipment, EquipmentInventorySlot.BODY, BODY_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			int worn = (helm ? 1 : 0) + (body ? 1 : 0) + (legs ? 1 : 0);

			int totalTenths = (helm ? HELM_BONUS_TENTHS : 0) + (body ? BODY_BONUS_TENTHS : 0) + (legs ? LEGS_BONUS_TENTHS : 0);

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Inquisitor's armour", worn, 3,
				"Increases crush accuracy and damage while using the crush attack style."));

			if (verbose && helm)
			{
				lines.add(new EffectLine("Great helm", "+" + EffectLineFormat.formatTenths(HELM_BONUS_TENTHS) + "%.", true));
			}
			if (verbose && body)
			{
				lines.add(new EffectLine("Hauberk", "+" + EffectLineFormat.formatTenths(BODY_BONUS_TENTHS) + "%.", true));
			}
			if (verbose && legs)
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

		static List<EffectLine> describe(ItemContainer equipment, boolean verbose)
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

			if (verbose && mask)
			{
				lines.add(new EffectLine("Virtus mask", "+" + EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "%.", true));
			}
			if (verbose && top)
			{
				lines.add(new EffectLine("Virtus robe top", "+" + EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "%.", true));
			}
			if (verbose && legs)
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

	/**
	 * Swampbark armour: the helm, body and legs each add 1.2 seconds (2 ticks) to standard
	 * spellbook Bind duration - the gauntlets and greaves give nothing, so they aren't tracked.
	 * Same layout as Inquisitor's/Virtus: header, per-piece lines (verbose only), Current Bonus.
	 */
	static final class SwampbarkArmour
	{
		private static final int HELM_ID = ItemID.SWAMPBARK_HELM;
		private static final int BODY_ID = ItemID.SWAMPBARK_BODY;
		private static final int LEGS_ID = ItemID.SWAMPBARK_LEGS;

		private static final int PER_PIECE_SECONDS_TENTHS = 12;

		private SwampbarkArmour()
		{
		}

		static boolean isSwampbarkItem(int mappedItemId)
		{
			return mappedItemId == HELM_ID
				|| mappedItemId == BODY_ID
				|| mappedItemId == LEGS_ID;
		}

		static List<EffectLine> describe(ItemContainer equipment, boolean verbose)
		{
			boolean helm = isWorn(equipment, EquipmentInventorySlot.HEAD, HELM_ID);
			boolean body = isWorn(equipment, EquipmentInventorySlot.BODY, BODY_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			int worn = (helm ? 1 : 0) + (body ? 1 : 0) + (legs ? 1 : 0);
			String perPiece = "+" + EffectLineFormat.formatTenths(PER_PIECE_SECONDS_TENTHS) + " seconds.";

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Swampbark armour", worn, 3,
				"Each piece increases the duration of standard spell book Binds by "
					+ EffectLineFormat.formatTenths(PER_PIECE_SECONDS_TENTHS) + " seconds."));

			if (verbose && helm)
			{
				lines.add(new EffectLine("Swampbark helm", perPiece, true));
			}
			if (verbose && body)
			{
				lines.add(new EffectLine("Swampbark body", perPiece, true));
			}
			if (verbose && legs)
			{
				lines.add(new EffectLine("Swampbark legs", perPiece, true));
			}

			lines.add(new EffectLine("Current Bonus",
				"+" + EffectLineFormat.formatTenths(worn * PER_PIECE_SECONDS_TENTHS) + " seconds.", true));
			return lines;
		}
	}

	/**
	 * Bloodbark armour: each of the five pieces adds 2% of the damage dealt to what blood spells
	 * heal (base 25%, so 35% with the full set). Same layout as Swampbark.
	 */
	static final class BloodbarkArmour
	{
		private static final int HELM_ID = ItemID.BLOODBARK_HELM;
		private static final int BODY_ID = ItemID.BLOODBARK_BODY;
		private static final int LEGS_ID = ItemID.BLOODBARK_LEGS;
		private static final int GAUNTLETS_ID = ItemID.BLOODBARK_GAUNTLETS;
		private static final int BOOTS_ID = ItemID.BLOODBARK_GREAVES;

		private static final int PER_PIECE_TENTHS = 20;

		private BloodbarkArmour()
		{
		}

		static boolean isBloodbarkItem(int mappedItemId)
		{
			return mappedItemId == HELM_ID
				|| mappedItemId == BODY_ID
				|| mappedItemId == LEGS_ID
				|| mappedItemId == GAUNTLETS_ID
				|| mappedItemId == BOOTS_ID;
		}

		static List<EffectLine> describe(ItemContainer equipment, boolean verbose)
		{
			boolean helm = isWorn(equipment, EquipmentInventorySlot.HEAD, HELM_ID);
			boolean body = isWorn(equipment, EquipmentInventorySlot.BODY, BODY_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			boolean gauntlets = isWorn(equipment, EquipmentInventorySlot.GLOVES, GAUNTLETS_ID);
			boolean boots = isWorn(equipment, EquipmentInventorySlot.BOOTS, BOOTS_ID);
			int worn = (helm ? 1 : 0) + (body ? 1 : 0) + (legs ? 1 : 0) + (gauntlets ? 1 : 0) + (boots ? 1 : 0);
			String perPiece = "+" + EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "%.";

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Bloodbark armour", worn, 5,
				"Each piece increases the amount healed by blood spells by "
					+ EffectLineFormat.formatTenths(PER_PIECE_TENTHS) + "% of the damage dealt."));

			if (verbose && helm)
			{
				lines.add(new EffectLine("Bloodbark helm", perPiece, true));
			}
			if (verbose && body)
			{
				lines.add(new EffectLine("Bloodbark body", perPiece, true));
			}
			if (verbose && legs)
			{
				lines.add(new EffectLine("Bloodbark legs", perPiece, true));
			}
			if (verbose && gauntlets)
			{
				lines.add(new EffectLine("Bloodbark gauntlets", perPiece, true));
			}
			if (verbose && boots)
			{
				lines.add(new EffectLine("Bloodbark boots", perPiece, true));
			}

			lines.add(new EffectLine("Current Bonus",
				"+" + EffectLineFormat.formatTenths(worn * PER_PIECE_TENTHS) + "%.", true));
			return lines;
		}
	}

	/**
	 * Graceful outfit: each piece adds to natural run energy restoration (hood/gloves/boots/cape
	 * +3%, top/legs +4%, 20% total) and wearing all six adds a further +10%, for 30%. An Agility
	 * cape (trimmed or not) counts in place of the graceful cape; it only shows up here once a
	 * graceful piece is worn, never on its own.
	 */
	static final class GracefulOutfit
	{
		private static final int HOOD_ID = ItemID.GRACEFUL_HOOD;
		private static final int CAPE_ID = ItemID.GRACEFUL_CAPE;
		private static final int AGILITY_CAPE_ID = ItemID.SKILLCAPE_AGILITY;
		private static final int TOP_ID = ItemID.GRACEFUL_TOP;
		private static final int LEGS_ID = ItemID.GRACEFUL_LEGS;
		private static final int GLOVES_ID = ItemID.GRACEFUL_GLOVES;
		private static final int BOOTS_ID = ItemID.GRACEFUL_BOOTS;

		private static final int HOOD_TENTHS = 30;
		private static final int CAPE_TENTHS = 30;
		private static final int TOP_TENTHS = 40;
		private static final int LEGS_TENTHS = 40;
		private static final int GLOVES_TENTHS = 30;
		private static final int BOOTS_TENTHS = 30;
		private static final int FULL_SET_TENTHS = 100;

		private GracefulOutfit()
		{
		}

		static boolean isGracefulItem(int mappedItemId)
		{
			return mappedItemId == HOOD_ID
				|| mappedItemId == CAPE_ID
				|| mappedItemId == TOP_ID
				|| mappedItemId == LEGS_ID
				|| mappedItemId == GLOVES_ID
				|| mappedItemId == BOOTS_ID;
		}

		static List<EffectLine> describe(ItemContainer equipment, boolean verbose)
		{
			boolean hood = isWorn(equipment, EquipmentInventorySlot.HEAD, HOOD_ID);
			boolean gracefulCape = isWorn(equipment, EquipmentInventorySlot.CAPE, CAPE_ID);
			boolean agilityCape = isWorn(equipment, EquipmentInventorySlot.CAPE, AGILITY_CAPE_ID);
			boolean cape = gracefulCape || agilityCape;
			boolean top = isWorn(equipment, EquipmentInventorySlot.BODY, TOP_ID);
			boolean legs = isWorn(equipment, EquipmentInventorySlot.LEGS, LEGS_ID);
			boolean gloves = isWorn(equipment, EquipmentInventorySlot.GLOVES, GLOVES_ID);
			boolean boots = isWorn(equipment, EquipmentInventorySlot.BOOTS, BOOTS_ID);
			int worn = (hood ? 1 : 0) + (cape ? 1 : 0) + (top ? 1 : 0) + (legs ? 1 : 0) + (gloves ? 1 : 0) + (boots ? 1 : 0);

			int totalTenths = (hood ? HOOD_TENTHS : 0) + (cape ? CAPE_TENTHS : 0) + (top ? TOP_TENTHS : 0)
				+ (legs ? LEGS_TENTHS : 0) + (gloves ? GLOVES_TENTHS : 0) + (boots ? BOOTS_TENTHS : 0)
				+ (worn == 6 ? FULL_SET_TENTHS : 0);

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Graceful outfit", worn, 6,
				"Each piece increases your run energy restoration rate, and wearing the full outfit gives an extra "
					+ EffectLineFormat.formatTenths(FULL_SET_TENTHS) + "%."));

			if (verbose && hood)
			{
				lines.add(pieceLine("Graceful hood", HOOD_TENTHS));
			}
			if (verbose && top)
			{
				lines.add(pieceLine("Graceful top", TOP_TENTHS));
			}
			if (verbose && legs)
			{
				lines.add(pieceLine("Graceful legs", LEGS_TENTHS));
			}
			if (verbose && gloves)
			{
				lines.add(pieceLine("Graceful gloves", GLOVES_TENTHS));
			}
			if (verbose && boots)
			{
				lines.add(pieceLine("Graceful boots", BOOTS_TENTHS));
			}
			if (verbose && cape)
			{
				lines.add(pieceLine(agilityCape ? "Agility cape" : "Graceful cape", CAPE_TENTHS));
			}

			lines.add(new EffectLine("Current Bonus", "+" + EffectLineFormat.formatTenths(totalTenths) + "%.", true));
			return lines;
		}

		private static EffectLine pieceLine(String name, int tenths)
		{
			return new EffectLine(name, "+" + EffectLineFormat.formatTenths(tenths) + "%.", true);
		}
	}

	/**
	 * Skilling outfits, data-driven: each worn piece gives its own share of a bonus (or, for the
	 * Guild hunter outfit, of two), and wearing the full outfit can add a further set bonus. Same
	 * layout as the other per-piece resolvers - header, per-piece lines (verbose only), Current
	 * Bonus. Values are in hundredths of a percent so amounts like 1.25% stay integers. A slot can
	 * list several interchangeable variants (spirit angler with angler, golden with regular
	 * prospector, male with female farmer pieces); a piece's line names whichever variant is worn.
	 * Rogue equipment isn't linear (its bonus is looked up by how many pieces are worn), hence
	 * {@code totalsByCount}.
	 */
	static final class SkillingOutfit
	{
		private static final EquipmentInventorySlot[] SLOTS = {
			EquipmentInventorySlot.HEAD, EquipmentInventorySlot.BODY, EquipmentInventorySlot.LEGS,
			EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS};

		// Hat 0.4%, top 0.8%, legs 0.6%, boots 0.2% (no gloves), then +0.5% for the full set = 2.5%
		private static final int[][] XP_PIECES = {{40}, {80}, {60}, null, {20}};
		private static final int[] XP_SET_BONUS = {50};

		static final SkillingOutfit ANGLER = new SkillingOutfit(EffectFamily.ANGLER_OUTFIT, "Angler outfit",
			xpHeader("Angler outfit", "Fishing") + " The full spirit angler outfit also acts as a tether rope when fighting Tempoross.",
			slotIds(new int[]{ItemID.TRAWLER_REWARD_HAT, ItemID.SPIRIT_ANGLER_HAT}, new int[]{ItemID.TRAWLER_REWARD_TOP, ItemID.SPIRIT_ANGLER_TOP},
				new int[]{ItemID.TRAWLER_REWARD_LEGS, ItemID.SPIRIT_ANGLER_LEGS}, null, new int[]{ItemID.TRAWLER_REWARD_BOOTS, ItemID.SPIRIT_ANGLER_BOOTS}),
			slotNames(new String[]{"Angler hat", "Spirit angler headband"}, new String[]{"Angler top", "Spirit angler top"},
				new String[]{"Angler waders", "Spirit angler waders"}, null, new String[]{"Angler boots", "Spirit angler boots"}),
			XP_PIECES, XP_SET_BONUS, null, null);
		static final SkillingOutfit LUMBERJACK = new SkillingOutfit(EffectFamily.LUMBERJACK_OUTFIT, "Lumberjack outfit",
			xpHeader("Lumberjack outfit", "Woodcutting"),
			slotIds(new int[]{ItemID.RAMBLE_LUMBERJACK_HAT}, new int[]{ItemID.RAMBLE_LUMBERJACK_TOP}, new int[]{ItemID.RAMBLE_LUMBERJACK_LEGS},
				null, new int[]{ItemID.RAMBLE_LUMBERJACK_BOOTS}),
			slotNames(new String[]{"Lumberjack hat"}, new String[]{"Lumberjack top"}, new String[]{"Lumberjack legs"}, null, new String[]{"Lumberjack boots"}),
			XP_PIECES, XP_SET_BONUS, null, null);
		static final SkillingOutfit PROSPECTOR = new SkillingOutfit(EffectFamily.PROSPECTOR_OUTFIT, "Prospector outfit",
			xpHeader("Prospector outfit", "Mining"),
			slotIds(new int[]{ItemID.MOTHERLODE_REWARD_HAT, ItemID.MOTHERLODE_REWARD_HAT_GOLD}, new int[]{ItemID.MOTHERLODE_REWARD_TOP, ItemID.MOTHERLODE_REWARD_TOP_GOLD},
				new int[]{ItemID.MOTHERLODE_REWARD_LEGS, ItemID.MOTHERLODE_REWARD_LEGS_GOLD}, null,
				new int[]{ItemID.MOTHERLODE_REWARD_BOOTS, ItemID.MOTHERLODE_REWARD_BOOTS_GOLD}),
			slotNames(new String[]{"Prospector helmet", "Golden prospector helmet"}, new String[]{"Prospector jacket", "Golden prospector jacket"},
				new String[]{"Prospector legs", "Golden prospector legs"}, null, new String[]{"Prospector boots", "Golden prospector boots"}),
			XP_PIECES, XP_SET_BONUS, null, null);
		static final SkillingOutfit FARMERS = new SkillingOutfit(EffectFamily.FARMERS_OUTFIT, "Farmer's outfit",
			xpHeader("Farmer's outfit", "Farming"),
			slotIds(new int[]{ItemID.TITHE_REWARD_HAT_MALE, ItemID.TITHE_REWARD_HAT_FEMALE}, new int[]{ItemID.TITHE_REWARD_TORSO_MALE, ItemID.TITHE_REWARD_TORSO_FEMALE},
				new int[]{ItemID.TITHE_REWARD_LEGS_MALE, ItemID.TITHE_REWARD_LEGS_FEMALE}, null,
				new int[]{ItemID.TITHE_REWARD_FEET_MALE, ItemID.TITHE_REWARD_FEET_FEMALE}),
			slotNames(new String[]{"Farmer's strawhat", "Farmer's strawhat"}, new String[]{"Farmer's jacket", "Farmer's shirt"},
				new String[]{"Farmer's boro trousers", "Farmer's boro trousers"}, null, new String[]{"Farmer's boots", "Farmer's boots"}),
			XP_PIECES, XP_SET_BONUS, null, null);
		static final SkillingOutfit PYROMANCER = new SkillingOutfit(EffectFamily.PYROMANCER_OUTFIT, "Pyromancer outfit",
			xpHeader("Pyromancer outfit", "Firemaking"),
			slotIds(new int[]{ItemID.PYROMANCER_HOOD}, new int[]{ItemID.PYROMANCER_TOP}, new int[]{ItemID.PYROMANCER_BOTTOM}, null, new int[]{ItemID.PYROMANCER_BOOTS}),
			slotNames(new String[]{"Pyromancer hood"}, new String[]{"Pyromancer garb"}, new String[]{"Pyromancer robe"}, null, new String[]{"Pyromancer boots"}),
			XP_PIECES, XP_SET_BONUS, null, null);
		static final SkillingOutfit CARPENTERS = new SkillingOutfit(EffectFamily.CARPENTERS_OUTFIT, "Carpenter's outfit",
			xpHeader("Carpenter's outfit", "Construction"),
			slotIds(new int[]{ItemID.CONSTRUCTION_HAT}, new int[]{ItemID.CONSTRUCTION_SHIRT}, new int[]{ItemID.CONSTRUCTION_TROUSERS}, null, new int[]{ItemID.CONSTRUCTION_BOOTS}),
			slotNames(new String[]{"Carpenter's helmet"}, new String[]{"Carpenter's shirt"}, new String[]{"Carpenter's trousers"}, null, new String[]{"Carpenter's boots"}),
			XP_PIECES, XP_SET_BONUS, null, null);

		static final SkillingOutfit SMITHS_UNIFORM = new SkillingOutfit(EffectFamily.SMITHS_UNIFORM, "Smith's uniform",
			"Each piece of the Smith's uniform gives a 20% chance for smithing at an anvil to take 1 tick less, and wearing the full uniform gives an extra 20%.",
			slotIds(null, new int[]{ItemID.SMITHING_UNIFORM_TORSO}, new int[]{ItemID.SMITHING_UNIFORM_LEGS},
				new int[]{ItemID.SMITHING_UNIFORM_GLOVES}, new int[]{ItemID.SMITHING_UNIFORM_BOOTS}),
			slotNames(null, new String[]{"Smiths tunic"}, new String[]{"Smiths trousers"},
				new String[]{"Smiths gloves"}, new String[]{"Smiths boots"}),
			new int[][]{null, {2000}, {2000}, {2000}, {2000}}, new int[]{2000}, null, null);
		static final SkillingOutfit ROGUE_EQUIPMENT = new SkillingOutfit(EffectFamily.ROGUE_EQUIPMENT, "Rogue equipment",
			"Each piece of the Rogue equipment gives a 15% chance of double loot from standard pickpocketing, and the full set guarantees it.",
			slotIds(new int[]{ItemID.ROGUESDEN_HELM}, new int[]{ItemID.ROGUESDEN_BODY}, new int[]{ItemID.ROGUESDEN_LEGS},
				new int[]{ItemID.ROGUESDEN_GLOVES}, new int[]{ItemID.ROGUESDEN_BOOTS}),
			slotNames(new String[]{"Rogue mask"}, new String[]{"Rogue top"}, new String[]{"Rogue trousers"},
				new String[]{"Rogue gloves"}, new String[]{"Rogue boots"}),
			new int[][]{{1500}, {1500}, {1500}, {1500}, {1500}}, new int[]{0}, null,
			new int[][]{{0}, {1500}, {3000}, {4500}, {6000}, {10000}});
		static final SkillingOutfit ZEALOTS_ROBES = new SkillingOutfit(EffectFamily.ZEALOTS_ROBES, "Zealot's robes",
			"Each piece of the Zealot's robes gives a 1.25% chance to save bones, ensouled heads, and bonemeal and slime while training Prayer. Stacks with prayer altars.",
			slotIds(new int[]{ItemID.SHADES_PRAYER_HELM}, new int[]{ItemID.SHADES_PRAYER_TOP}, new int[]{ItemID.SHADES_PRAYER_BOTTOM}, null, new int[]{ItemID.SHADES_PRAYER_BOOTS}),
			slotNames(new String[]{"Zealot's helm"}, new String[]{"Zealot's robe top"}, new String[]{"Zealot's robe bottom"}, null, new String[]{"Zealot's boots"}),
			new int[][]{{125}, {125}, {125}, null, {125}}, new int[]{0}, null, null);
		static final SkillingOutfit RAIMENTS_OF_THE_EYE = new SkillingOutfit(EffectFamily.RAIMENTS_OF_THE_EYE, "Raiments of the Eye",
			"Each piece of the Raiments of the Eye grants 10% bonus runes when Runecrafting, and wearing the full outfit gives an extra 20%. Stacks additively with other bonus rune effects.",
			slotIds(new int[]{ItemID.HAT_OF_THE_EYE}, new int[]{ItemID.ROBE_TOP_OF_THE_EYE}, new int[]{ItemID.ROBE_BOTTOM_OF_THE_EYE}, null, new int[]{ItemID.BOOTS_OF_THE_EYE}),
			slotNames(new String[]{"Hat of the Eye"}, new String[]{"Robe top of the Eye"}, new String[]{"Robe bottoms of the Eye"}, null, new String[]{"Boots of the Eye"}),
			new int[][]{{1000}, {1000}, {1000}, null, {1000}}, new int[]{2000}, null, null);
		static final SkillingOutfit GUILD_HUNTER = new SkillingOutfit(EffectFamily.GUILD_HUNTER_OUTFIT, "Guild hunter outfit",
			"Each piece of the Guild hunter outfit increases hunter creature catch chance and the rare creature part drop rate from Hunters' Rumours, and wearing the full outfit gives an extra 0.5% and 1%.",
			slotIds(new int[]{ItemID.HG_HUNTER_HOOD}, new int[]{ItemID.HG_HUNTER_TOP}, new int[]{ItemID.HG_HUNTER_LEGS}, null, new int[]{ItemID.HG_HUNTER_BOOTS}),
			slotNames(new String[]{"Headwear"}, new String[]{"Top"}, new String[]{"Legs"}, null, new String[]{"Boots"}),
			new int[][]{{20, 40}, {80, 160}, {60, 120}, null, {40, 80}}, new int[]{50, 100},
			new String[]{"catch chance", "rare part drop rate"}, null);

		static final List<SkillingOutfit> ALL = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
			ANGLER, LUMBERJACK, PROSPECTOR, FARMERS, PYROMANCER, CARPENTERS,
			SMITHS_UNIFORM, ROGUE_EQUIPMENT, ZEALOTS_ROBES, RAIMENTS_OF_THE_EYE, GUILD_HUNTER));

		final EffectFamily family;
		private final String setName;
		private final String header;
		private final int[][] ids;
		private final String[][] names;
		private final int[][] pieceValues;
		private final int[] setBonus;
		private final String[] valueLabels;
		private final int[][] totalsByCount;

		private SkillingOutfit(EffectFamily family, String setName, String header, int[][] ids, String[][] names,
			int[][] pieceValues, int[] setBonus, String[] valueLabels, int[][] totalsByCount)
		{
			this.family = family;
			this.setName = setName;
			this.header = header;
			this.ids = ids;
			this.names = names;
			this.pieceValues = pieceValues;
			this.setBonus = setBonus;
			this.valueLabels = valueLabels;
			this.totalsByCount = totalsByCount;
		}

		private static String xpHeader(String setName, String skill)
		{
			return "Each piece of the " + setName + " increases " + skill + " XP, and wearing the full outfit gives an extra 0.5%.";
		}

		private static int[][] slotIds(int[]... perSlot)
		{
			return perSlot;
		}

		private static String[][] slotNames(String[]... perSlot)
		{
			return perSlot;
		}

		boolean isItem(int mappedItemId)
		{
			for (int[] slotIds : ids)
			{
				if (slotIds == null)
				{
					continue;
				}
				for (int id : slotIds)
				{
					if (id == mappedItemId)
					{
						return true;
					}
				}
			}
			return false;
		}

		List<EffectLine> describe(ItemContainer equipment, boolean verbose)
		{
			int valueCount = setBonus.length;
			String[] wornNames = new String[SLOTS.length];
			int[] totals = new int[valueCount];
			int worn = 0;
			int total = 0;
			for (int i = 0; i < SLOTS.length; i++)
			{
				if (ids[i] == null)
				{
					continue;
				}
				total++;
				for (int v = 0; v < ids[i].length; v++)
				{
					if (isWorn(equipment, SLOTS[i], ids[i][v]))
					{
						wornNames[i] = names[i][v];
						worn++;
						for (int k = 0; k < valueCount; k++)
						{
							totals[k] += pieceValues[i][k];
						}
						break;
					}
				}
			}
			if (worn == total)
			{
				for (int k = 0; k < valueCount; k++)
				{
					totals[k] += setBonus[k];
				}
			}
			if (totalsByCount != null)
			{
				totals = totalsByCount[worn];
			}

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine(setName, worn, total, header));
			for (int i = 0; verbose && i < SLOTS.length; i++)
			{
				if (wornNames[i] != null)
				{
					lines.add(new EffectLine(wornNames[i], formatValues(pieceValues[i]), true));
				}
			}
			lines.add(new EffectLine("Current Bonus", formatValues(totals), true));
			return lines;
		}

		private String formatValues(int[] hundredths)
		{
			StringBuilder sb = new StringBuilder();
			for (int k = 0; k < hundredths.length; k++)
			{
				if (k > 0)
				{
					sb.append(", ");
				}
				sb.append('+').append(EffectLineFormat.formatHundredths(hundredths[k])).append('%');
				if (valueLabels != null)
				{
					sb.append(' ').append(valueLabels[k]);
				}
			}
			return sb.append('.').toString();
		}
	}

	/**
	 * Shayzien armour: every piece reduces a lizardman shaman's acid attack by 4% per armour tier
	 * (tier 5 = 20%, so five tier 5 pieces negate it), and pieces of different tiers add up. With the
	 * Hard Kourend and Kebos Diary a Slayer helmet counts as a tier 5 helm (only checked if no
	 * Shayzien helm is worn). Same layout as the other per-piece resolvers.
	 */
	static final class ShayzienArmour
	{
		private static final EquipmentInventorySlot[] SLOTS = {
			EquipmentInventorySlot.HEAD, EquipmentInventorySlot.BODY, EquipmentInventorySlot.LEGS,
			EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS};
		private static final String[] NAMES = {"Shayzien helm", "Shayzien body", "Shayzien greaves", "Shayzien gloves", "Shayzien boots"};
		// [slot][tier - 1]
		private static final int[][] IDS = {
			{ItemID.SHAYZIEN_HELM_1, ItemID.SHAYZIEN_HELM_2, ItemID.SHAYZIEN_HELM_3, ItemID.SHAYZIEN_HELM_4, ItemID.SHAYZIEN_HELM_5},
			{ItemID.SHAYZIEN_BODY_1, ItemID.SHAYZIEN_BODY_2, ItemID.SHAYZIEN_BODY_3, ItemID.SHAYZIEN_BODY_4, ItemID.SHAYZIEN_BODY_5},
			{ItemID.SHAYZIEN_LEGS_1, ItemID.SHAYZIEN_LEGS_2, ItemID.SHAYZIEN_LEGS_3, ItemID.SHAYZIEN_LEGS_4, ItemID.SHAYZIEN_LEGS_5},
			{ItemID.SHAYZIEN_GLOVES_1, ItemID.SHAYZIEN_GLOVES_2, ItemID.SHAYZIEN_GLOVES_3, ItemID.SHAYZIEN_GLOVES_4, ItemID.SHAYZIEN_GLOVES_5},
			{ItemID.SHAYZIEN_BOOTS_1, ItemID.SHAYZIEN_BOOTS_2, ItemID.SHAYZIEN_BOOTS_3, ItemID.SHAYZIEN_BOOTS_4, ItemID.SHAYZIEN_BOOTS_5}};
		private static final int PERCENT_PER_TIER = 4;
		private static final int SLAYER_HELMET_TIER = 5;

		private ShayzienArmour()
		{
		}

		static boolean isShayzienItem(int mappedItemId)
		{
			for (int[] slotIds : IDS)
			{
				for (int id : slotIds)
				{
					if (id == mappedItemId)
					{
						return true;
					}
				}
			}
			return false;
		}

		static List<EffectLine> describe(ItemContainer equipment, boolean verbose, BooleanSupplier hardKourendDiary)
		{
			String[] pieceNames = new String[SLOTS.length];
			int[] tiers = new int[SLOTS.length];
			int worn = 0;
			int total = 0;
			for (int i = 0; i < SLOTS.length; i++)
			{
				for (int t = 0; t < IDS[i].length; t++)
				{
					if (isWorn(equipment, SLOTS[i], IDS[i][t]))
					{
						tiers[i] = t + 1;
						pieceNames[i] = NAMES[i] + " (" + (t + 1) + ")";
						break;
					}
				}
			}

			if (tiers[0] == 0)
			{
				Item head = equipment.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
				if (head != null && SlayerHelm.isSlayerHelm(head.getId()) && hardKourendDiary.getAsBoolean())
				{
					tiers[0] = SLAYER_HELMET_TIER;
					pieceNames[0] = "Slayer helmet";
				}
			}

			for (int i = 0; i < SLOTS.length; i++)
			{
				if (tiers[i] > 0)
				{
					worn++;
					total += tiers[i] * PERCENT_PER_TIER;
				}
			}

			List<EffectLine> lines = new ArrayList<>();
			lines.add(new EffectLine("Shayzien armour", worn, SLOTS.length,
				"Reduces the damage of the acid splash attack used by Lizardman Shamans."));
			for (int i = 0; verbose && i < SLOTS.length; i++)
			{
				if (tiers[i] > 0)
				{
					lines.add(new EffectLine(pieceNames[i], tiers[i] * PERCENT_PER_TIER + "%.", true));
				}
			}
			lines.add(new EffectLine("Current Bonus", total + "%.", true));
			return lines;
		}
	}
}
