package com.seteffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;

/**
 * Curated set/gear effects: multi-piece sets with a genuine passive combo effect (not a
 * manually-triggered special attack), plus notable single items with their own passive effect.
 * Plain top-tier stat items (e.g. Bandos/Armadyl, Barrows gloves, Berserker ring (i)) are
 * deliberately excluded - they have no distinct effect to describe.
 */
final class SetEffectsData
{
	static final List<ItemSet> ITEM_SETS;
	static final Map<Integer, List<ItemSet>> SETS_BY_ITEM;
	static final Map<Integer, SingleItemEffect> SINGLE_ITEM_EFFECTS;

	static
	{
		List<ItemSet> sets = new ArrayList<>();

		sets.add(fourPiece("Ahrim's set",
			"Successful magic attacks have a 25% chance to lower the target's Strength by 5 levels.",
			ItemID.BARROWS_AHRIM_HEAD, ItemID.BARROWS_AHRIM_BODY, ItemID.BARROWS_AHRIM_LEGS, ItemID.BARROWS_AHRIM_WEAPON,
			"Autocasts Ancient Magicks, 25% chance of 30% increased damage."));
		sets.add(fourPiece("Dharok's set",
			"Damage increases as Hitpoints decrease, up to +98% extra damage at 1 HP (with 99 Hitpoints).",
			ItemID.BARROWS_DHAROK_HEAD, ItemID.BARROWS_DHAROK_BODY, ItemID.BARROWS_DHAROK_LEGS, ItemID.BARROWS_DHAROK_WEAPON,
			"25% chance to recoil 15% of damage taken back at the attacker."));
		sets.add(fourPiece("Guthan's set",
			"Successful melee attacks have a 25% chance to heal Hitpoints equal to the damage dealt.",
			ItemID.BARROWS_GUTHAN_HEAD, ItemID.BARROWS_GUTHAN_BODY, ItemID.BARROWS_GUTHAN_LEGS, ItemID.BARROWS_GUTHAN_WEAPON,
			"Allows healing up to 10 Hitpoints above your base level."));
		sets.add(fourPiece("Karil's set",
			"Successful ranged attacks have a 25% chance to lower the target's Agility level by 20%.",
			ItemID.BARROWS_KARIL_HEAD, ItemID.BARROWS_KARIL_BODY, ItemID.BARROWS_KARIL_LEGS, ItemID.BARROWS_KARIL_WEAPON,
			"25% chance to hit twice with one attack; the second hit deals half damage."));
		sets.add(fourPiece("Torag's set",
			"Successful melee attacks have a 25% chance to lower the target's run energy by 20%.",
			ItemID.BARROWS_TORAG_HEAD, ItemID.BARROWS_TORAG_BODY, ItemID.BARROWS_TORAG_LEGS, ItemID.BARROWS_TORAG_WEAPON,
			"Defence level increases by 1% per Hitpoint missing."));
		sets.add(fourPiece("Verac's set",
			"Attacks have a 25% chance to be a guaranteed hit, ignoring accuracy. This can bypass prayer.",
			ItemID.BARROWS_VERAC_HEAD, ItemID.BARROWS_VERAC_BODY, ItemID.BARROWS_VERAC_LEGS, ItemID.BARROWS_VERAC_WEAPON,
			"+7 additional Prayer bonus."));

		sets.add(new ItemSet("Justiciar armour",
			"Reduces non-typeless damage taken (outside PvP) by (your defensive bonus for that style) / 3000, min. 1 damage reduced.",
			threePiece(ItemID.JUSTICIAR_FACEGUARD, ItemID.JUSTICIAR_CHESTGUARD, ItemID.JUSTICIAR_LEG_GUARDS)));

		Map<EquipmentInventorySlot, int[]> obsidianSlots = new EnumMap<>(EquipmentInventorySlot.class);
		obsidianSlots.put(EquipmentInventorySlot.HEAD, new int[]{ItemID.OBSIDIAN_HELMET});
		obsidianSlots.put(EquipmentInventorySlot.BODY, new int[]{ItemID.OBSIDIAN_PLATEBODY});
		obsidianSlots.put(EquipmentInventorySlot.LEGS, new int[]{ItemID.OBSIDIAN_PLATELEGS});
		obsidianSlots.put(EquipmentInventorySlot.WEAPON, new int[]{ItemID.TZHAAR_SPLITSWORD, ItemID.TZHAAR_MACE, ItemID.TZHAAR_MAUL});
		sets.add(new ItemSet("Obsidian armour",
			"+10% accuracy and damage with obsidian melee weapons. Stacks with the berserker necklace's damage bonus.",
			obsidianSlots));

		sets.add(fourPiece("Blood moon armour (Bloodrager)",
			"The dual macuahuitl has a 33% chance per successful hit to attack a tick earlier than usual.",
			ItemID.BLOOD_MOON_HELM, ItemID.BLOOD_MOON_CHESTPLATE, ItemID.BLOOD_MOON_TASSETS, ItemID.DUAL_MACUAHUITL, null));
		sets.add(fourPiece("Blue moon armour (Frostweaver)",
			"After casting a bind/grasp spell, the blue moon spear has a 20%/50% chance for its next melee attack to ignore attack delay.",
			ItemID.FROST_MOON_HELM, ItemID.FROST_MOON_CHESTPLATE, ItemID.FROST_MOON_TASSETS, ItemID.FROSTMOON_SPEAR, null));
		sets.add(fourPiece("Eclipse moon armour (Eclipse)",
			"The eclipse atlatl has a 20% chance to burn the target for 10 damage over time (stacks up to 5 times).",
			ItemID.ECLIPSE_MOON_HELM, ItemID.ECLIPSE_MOON_CHESTPLATE, ItemID.ECLIPSE_MOON_TASSETS, ItemID.ECLIPSE_ATLATL, null));

		sets.add(shayzienTier(1, 20, ItemID.SHAYZIEN_HELM_1, ItemID.SHAYZIEN_BODY_1, ItemID.SHAYZIEN_LEGS_1, ItemID.SHAYZIEN_GLOVES_1, ItemID.SHAYZIEN_BOOTS_1));
		sets.add(shayzienTier(2, 40, ItemID.SHAYZIEN_HELM_2, ItemID.SHAYZIEN_BODY_2, ItemID.SHAYZIEN_LEGS_2, ItemID.SHAYZIEN_GLOVES_2, ItemID.SHAYZIEN_BOOTS_2));
		sets.add(shayzienTier(3, 60, ItemID.SHAYZIEN_HELM_3, ItemID.SHAYZIEN_BODY_3, ItemID.SHAYZIEN_LEGS_3, ItemID.SHAYZIEN_GLOVES_3, ItemID.SHAYZIEN_BOOTS_3));
		sets.add(shayzienTier(4, 80, ItemID.SHAYZIEN_HELM_4, ItemID.SHAYZIEN_BODY_4, ItemID.SHAYZIEN_LEGS_4, ItemID.SHAYZIEN_GLOVES_4, ItemID.SHAYZIEN_BOOTS_4));
		sets.add(shayzienTier(5, 100, ItemID.SHAYZIEN_HELM_5, ItemID.SHAYZIEN_BODY_5, ItemID.SHAYZIEN_LEGS_5, ItemID.SHAYZIEN_GLOVES_5, ItemID.SHAYZIEN_BOOTS_5));

		ITEM_SETS = Collections.unmodifiableList(sets);

		Map<Integer, List<ItemSet>> byItem = new HashMap<>();
		for (ItemSet set : ITEM_SETS)
		{
			for (int id : set.allItemIds())
			{
				byItem.computeIfAbsent(id, k -> new ArrayList<>()).add(set);
			}
		}
		SETS_BY_ITEM = Collections.unmodifiableMap(byItem);

		Map<Integer, SingleItemEffect> singles = new HashMap<>();
		singles.put(ItemID.EMBERLIGHT, new SingleItemEffect("Emberlight", "+70% damage and accuracy against demons."));
		singles.put(ItemID.JEWL_BESERKER_NECKLACE, new SingleItemEffect("Berserker necklace", "+20% damage with obsidian weapons. Stacks with the obsidian armour set's bonus."));

		// vs-monster-type weapons
		singles.put(ItemID.TWISTED_BOW, new SingleItemEffect("Twisted bow", "Accuracy and damage scale up with the target's Magic level and Magic accuracy bonus."));
		singles.put(ItemID.ZARYTE_XBOW, new SingleItemEffect("Zaryte crossbow", "Strengthens enchanted bolt effects by 10%."));
		singles.put(ItemID.DRAGONHUNTER_XBOW, new SingleItemEffect("Dragon hunter crossbow", "+25% damage and +30% accuracy against draconic creatures."));
		singles.put(ItemID.DRAGONHUNTER_LANCE, new SingleItemEffect("Dragon hunter lance", "+20% damage and accuracy against draconic creatures."));
		singles.put(ItemID.DRAGONHUNTER_WAND, new SingleItemEffect("Dragon hunter wand", "+40% damage and +75% accuracy against draconic creatures."));
		singles.put(ItemID.WILD_CAVE_BOW_CHARGED, new SingleItemEffect("Craw's bow", "While charged with at least 1000 revenant ether: +50% ranged damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_WEBWEAVER_CHARGED, new SingleItemEffect("Webweaver bow", "While charged with at least 1000 revenant ether: +50% ranged damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_SCEPTRE_CHARGED, new SingleItemEffect("Thammaron's sceptre", "While charged with at least 1000 revenant ether: +50% magic damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_ACCURSED_CHARGED, new SingleItemEffect("Accursed sceptre", "While charged with at least 1000 revenant ether: +50% magic damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_CHAINMACE_CHARGED, new SingleItemEffect("Viggora's chainmace", "While charged with at least 1000 revenant ether: +50% melee damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_URSINE_CHARGED, new SingleItemEffect("Ursine chainmace", "While charged with at least 1000 revenant ether: +50% melee damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.SOTD, new SingleItemEffect("Staff of the dead", "12.5% chance to negate the rune cost of a combat spell cast with it."));
		singles.put(ItemID.TOXIC_SOTD_CHARGED, new SingleItemEffect("Toxic staff of the dead", "12.5% chance to negate the rune cost of a combat spell cast with it. In addition, combat spells have a 25% chance to venom the target, increased to 100% when the serpentine helm is worn (vs NPCs)."));
		singles.put(ItemID.STAFF_OF_LIGHT, new SingleItemEffect("Staff of light", "12.5% chance to negate the rune cost of a combat spell cast with it."));
		singles.put(ItemID.KODAI_WAND, new SingleItemEffect("Kodai wand", "15% chance to negate the rune cost of an offensive spell cast with it."));
		singles.put(ItemID.SANGUINESTI_STAFF, new SingleItemEffect("Sanguinesti staff", "20% chance to deal 8 extra damage and heal the wielder for half the total hit."));
		singles.put(ItemID.SILVERLIGHT, new SingleItemEffect("Silverlight", "+60% damage and accuracy against demons."));
		singles.put(ItemID.DARKLIGHT, new SingleItemEffect("Darklight", "+60% damage and accuracy against demons."));
		singles.put(ItemID.ARCLIGHT, new SingleItemEffect("Arclight", "+70% damage and accuracy against demons."));
		singles.put(ItemID.SCORCHING_BOW, new SingleItemEffect("Scorching bow", "+30% damage and accuracy against demons."));
		singles.put(ItemID.PURGING_STAFF, new SingleItemEffect("Purging staff", "Doubles demonbane spell bonuses. Mark of Darkness lasts 5x longer when cast with this staff equipped."));
		singles.put(ItemID.KERIS_PARTISAN, new SingleItemEffect("Keris partisan", "+33% damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		singles.put(ItemID.KERIS_PARTISAN_BREACH, new SingleItemEffect("Keris partisan of breaching", "+33% accuracy and damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		singles.put(ItemID.KERIS_PARTISAN_SUN, new SingleItemEffect("Keris partisan of the sun", "+33% damage against kalphites and scarab creatures. In the Tombs of Amascut, +25% accuracy against targets below 25% health, and kills heal 12 HP at the cost of 5 Prayer."));
		singles.put(ItemID.DAGGER_WOLFBANE, new SingleItemEffect("Wolfbane", "Prevents Canifis residents from transforming into werewolves."));
		singles.put(ItemID.GIANTS_FOUNDRY_COLOSSAL_BLADE, new SingleItemEffect("Colossal blade", "Increases max hit against larger enemies, scaling with the target's size."));
		singles.put(ItemID.LEAFBLADED_BATTLEAXE, new SingleItemEffect("Leaf-bladed battleaxe", "+17.5% damage against turoths and kurasks. Stacks with the Slayer helmet."));
		singles.put(ItemID.OSMUMTENS_FANG, new SingleItemEffect("Osmumten's fang", "When on stab, rolls accuracy twice and uses the higher roll. Normal successful hits deal 15%-85% of max hit."));
		singles.put(ItemID.TUMEKENS_SHADOW, new SingleItemEffect("Tumeken's shadow", "Triples the magic attack and damage bonuses from worn equipment, capped at 100% (quadruples inside the Tombs of Amascut)."));
		singles.put(ItemID.HALLOWFELL, new SingleItemEffect("Hallowfell", "Cleaves up to 2 additional targets in front of or beside you, for up to 50% of your max hit each."));
		singles.put(ItemID.ANCIENT_SCEPTRE, new SingleItemEffect("Ancient sceptre", "+10% boost to Ancient Magicks secondary effects."));
		singles.put(ItemID.ANCIENT_SCEPTRE_BLOOD, new SingleItemEffect("Blood ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Blood spells can overheal by 10%."));
		singles.put(ItemID.ANCIENT_SCEPTRE_ICE, new SingleItemEffect("Ice ancient sceptre", "+10% boost to Ancient Magicks secondary effects. +10% extra accuracy for ice spells against unfrozen, freezable targets."));
		singles.put(ItemID.ANCIENT_SCEPTRE_SHADOW, new SingleItemEffect("Shadow ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Shadow spells also lower the target's Attack, Strength, and Defence."));
		singles.put(ItemID.ANCIENT_SCEPTRE_SMOKE, new SingleItemEffect("Smoke ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Temporarily reduces a poisoned target's healing by 20% for 6 seconds."));
		singles.put(ItemID.RAT_BONE_MACE, new SingleItemEffect("Bone mace", "+10 to max hit. Only effective against rats."));
		singles.put(ItemID.RAT_BONE_BOW, new SingleItemEffect("Bone shortbow", "+10 to max hit. Only effective against rats."));
		singles.put(ItemID.BARRONITE_MACE, new SingleItemEffect("Barronite mace", "+15% damage and accuracy against golems."));
		singles.put(ItemID.GRANITE_HAMMER, new SingleItemEffect("Granite hammer", "+30% damage and accuracy against golems."));

		// shields
		singles.put(ItemID.ELYSIAN, new SingleItemEffect("Elysian spirit shield", "70% chance to reduce incoming damage by 25%."));
		singles.put(ItemID.SPECTRAL, new SingleItemEffect("Spectral spirit shield", "Reduces the effectiveness of prayer-draining attacks by 50% (outside PvP)."));
		singles.put(ItemID.DINHS_BULWARK, new SingleItemEffect("Dinh's bulwark", "Block attack style reduces incoming damage by 20% (outside PvP). Pummel attack style increases max hit based on your defensive bonuses."));
		singles.put(ItemID.ANTIDRAGONBREATHSHIELD, new SingleItemEffect("Anti-dragon shield", "Partial protection against dragonfire."));
		singles.put(ItemID.ELEMENTAL_SHIELD, new SingleItemEffect("Elemental shield", "Partial protection against wyverns' icy breath."));
		singles.put(ItemID.ELEMENTAL_MIND_SHIELD, new SingleItemEffect("Mind shield", "Partial protection against wyverns' icy breath."));
		singles.put(ItemID.DRAGONFIRE_SHIELD, new SingleItemEffect("Dragonfire shield", "Partial protection against dragonfire and wyverns' icy breath."));
		singles.put(ItemID.WYVERN_SHIELD, new SingleItemEffect("Ancient wyvern shield", "Partial protection against wyverns' icy breath and blocks the freeze effect."));
		singles.put(ItemID.DRAGONFIRE_WARD, new SingleItemEffect("Dragonfire ward", "Partial protection against dragonfire and wyverns' icy breath."));

		// headgear
		singles.put(ItemID.SERPENTINE_HELM, new SingleItemEffect("Serpentine helm", "While charged with scales, grants immunity to venom and poison. Chance to inflict venom: 16.7% with a non-poisoned melee weapon, 50% with poison or the noxious halberd, or 100% with toxic blowpipe, trident of the swamp, or toxic staff of the dead."));

		// handwear
		singles.put(ItemID.GAUNTLETS_OF_CHAOS, new SingleItemEffect("Chaos gauntlets", "+3 max hit on Bolt spells."));
		singles.put(ItemID.JEWL_BRACELET_REGEN, new SingleItemEffect("Regen bracelet", "Doubles the rate of natural Hitpoints regeneration."));

		// amulets
		singles.put(ItemID.NECKLACE_OF_FAITH, new SingleItemEffect("Necklace of faith", "When hit below 20% Hitpoints, restores Prayer points equal to 25% of your Prayer level, then breaks."));
		singles.put(ItemID.JEWL_NECKLACE_OF_PHOENIX, new SingleItemEffect("Phoenix necklace", "When hit below 20% Hitpoints, heals 30% of max Hitpoints, then breaks."));
		singles.put(ItemID.AMULET_OF_GLORY, new SingleItemEffect("Amulet of glory", "While charged, increases gem-finding chance while mining and increases mining speed on gem rocks."));
		singles.put(ItemID.WILD_CAVE_AMULET, new SingleItemEffect("Amulet of avarice", "Revenant, Wilderness Slayer Cave, and Chaos Temple drops will be noted. Revenant unique drop rates double, and you gain +20% increased damage and accuracy against revenants. Skulls the player while worn."));
		singles.put(ItemID.BLOOD_AMULET, new SingleItemEffect("Amulet of blood fury", "20% chance to heal for 30% of melee damage dealt."));

		// rings
		singles.put(ItemID.RING_OF_LIFE, new SingleItemEffect("Ring of life", "When hit below 10% Hitpoints, teleports you to your respawn point, then breaks."));
		singles.put(ItemID.RING_OF_WEALTH, new SingleItemEffect("Ring of wealth", "Removes empty Rare Drop Table slots and can collect currency drops for you."));
		singles.put(ItemID.RING_OF_RECOIL, new SingleItemEffect("Ring of recoil", "Returns 1+10% of damage taken back at the attacker."));
		singles.put(ItemID.ZENYTE_RING_ENCHANTED, new SingleItemEffect("Ring of suffering", "When charged with rings of recoil, returns 1+10% of damage taken back at the attacker."));
		singles.put(ItemID.BRIMSTONE_RING, new SingleItemEffect("Brimstone ring", "Combat spells have a 25% chance to ignore 10% of the target's magic defence."));

		// misc utility
		singles.put(ItemID.WATER_CIRCLET, new SingleItemEffect("Circlet of water", "While charged with water runes, protects against desert heat."));

		SINGLE_ITEM_EFFECTS = Collections.unmodifiableMap(singles);
	}

	private static ItemSet fourPiece(String name, String effect, int head, int body, int legs, int weapon, String amuletOfTheDamnedSynergy)
	{
		Map<EquipmentInventorySlot, int[]> slots = new EnumMap<>(EquipmentInventorySlot.class);
		slots.put(EquipmentInventorySlot.HEAD, new int[]{head});
		slots.put(EquipmentInventorySlot.BODY, new int[]{body});
		slots.put(EquipmentInventorySlot.LEGS, new int[]{legs});
		slots.put(EquipmentInventorySlot.WEAPON, new int[]{weapon});
		return new ItemSet(name, effect, slots, amuletOfTheDamnedSynergy);
	}

	private static ItemSet shayzienTier(int tier, int percent, int helm, int body, int legs, int gloves, int boots)
	{
		Map<EquipmentInventorySlot, int[]> slots = new EnumMap<>(EquipmentInventorySlot.class);
		slots.put(EquipmentInventorySlot.HEAD, new int[]{helm});
		slots.put(EquipmentInventorySlot.BODY, new int[]{body});
		slots.put(EquipmentInventorySlot.LEGS, new int[]{legs});
		slots.put(EquipmentInventorySlot.GLOVES, new int[]{gloves});
		slots.put(EquipmentInventorySlot.BOOTS, new int[]{boots});
		String effect = tier == 5
			? "Full set completely negates a lizardman shaman's acid attack damage."
			: "Full set reduces a lizardman shaman's acid attack damage by " + percent + "%.";
		return new ItemSet("Shayzien armour (Tier " + tier + ")", effect, slots);
	}

	private static Map<EquipmentInventorySlot, int[]> threePiece(int head, int body, int legs)
	{
		Map<EquipmentInventorySlot, int[]> slots = new EnumMap<>(EquipmentInventorySlot.class);
		slots.put(EquipmentInventorySlot.HEAD, new int[]{head});
		slots.put(EquipmentInventorySlot.BODY, new int[]{body});
		slots.put(EquipmentInventorySlot.LEGS, new int[]{legs});
		return slots;
	}

	private SetEffectsData()
	{
	}
}
