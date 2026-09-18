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

		sets.add(fourPiece(EffectFamily.AHRIMS, "Ahrim's set",
			"Successful magic attacks have a 25% chance to lower the target's Strength by 5 levels.",
			ItemID.BARROWS_AHRIM_HEAD, ItemID.BARROWS_AHRIM_BODY, ItemID.BARROWS_AHRIM_LEGS, ItemID.BARROWS_AHRIM_WEAPON,
			"Autocasts Ancient Magicks, 25% chance of 30% increased damage."));
		sets.add(fourPiece(EffectFamily.DHAROKS, "Dharok's set",
			"Damage increases as Hitpoints decrease, up to +98% extra damage at 1 HP (with 99 Hitpoints).",
			ItemID.BARROWS_DHAROK_HEAD, ItemID.BARROWS_DHAROK_BODY, ItemID.BARROWS_DHAROK_LEGS, ItemID.BARROWS_DHAROK_WEAPON,
			"25% chance to recoil 15% of damage taken back at the attacker."));
		sets.add(fourPiece(EffectFamily.GUTHANS, "Guthan's set",
			"Successful melee attacks have a 25% chance to heal Hitpoints equal to the damage dealt.",
			ItemID.BARROWS_GUTHAN_HEAD, ItemID.BARROWS_GUTHAN_BODY, ItemID.BARROWS_GUTHAN_LEGS, ItemID.BARROWS_GUTHAN_WEAPON,
			"Allows healing up to 10 Hitpoints above your base level."));
		sets.add(fourPiece(EffectFamily.KARILS, "Karil's set",
			"Successful ranged attacks have a 25% chance to lower the target's Agility level by 20%.",
			ItemID.BARROWS_KARIL_HEAD, ItemID.BARROWS_KARIL_BODY, ItemID.BARROWS_KARIL_LEGS, ItemID.BARROWS_KARIL_WEAPON,
			"25% chance to hit twice with one attack; the second hit deals half damage."));
		sets.add(fourPiece(EffectFamily.TORAGS, "Torag's set",
			"Successful melee attacks have a 25% chance to lower the target's run energy by 20%.",
			ItemID.BARROWS_TORAG_HEAD, ItemID.BARROWS_TORAG_BODY, ItemID.BARROWS_TORAG_LEGS, ItemID.BARROWS_TORAG_WEAPON,
			"Defence level increases by 1% per Hitpoint missing."));
		sets.add(fourPiece(EffectFamily.VERACS, "Verac's set",
			"Attacks have a 25% chance to be a guaranteed hit, ignoring accuracy. This can bypass prayer.",
			ItemID.BARROWS_VERAC_HEAD, ItemID.BARROWS_VERAC_BODY, ItemID.BARROWS_VERAC_LEGS, ItemID.BARROWS_VERAC_WEAPON,
			"+7 additional Prayer bonus."));

		sets.add(new ItemSet(EffectFamily.JUSTICIAR, "Justiciar armour",
			"Reduces non-typeless damage taken (outside PvP) by (your defensive bonus for that style) / 3000, min. 1 damage reduced.",
			threePiece(ItemID.JUSTICIAR_FACEGUARD, ItemID.JUSTICIAR_CHESTGUARD, ItemID.JUSTICIAR_LEG_GUARDS)));

		Map<EquipmentInventorySlot, int[]> obsidianSlots = new EnumMap<>(EquipmentInventorySlot.class);
		obsidianSlots.put(EquipmentInventorySlot.HEAD, new int[]{ItemID.OBSIDIAN_HELMET});
		obsidianSlots.put(EquipmentInventorySlot.BODY, new int[]{ItemID.OBSIDIAN_PLATEBODY});
		obsidianSlots.put(EquipmentInventorySlot.LEGS, new int[]{ItemID.OBSIDIAN_PLATELEGS});
		obsidianSlots.put(EquipmentInventorySlot.WEAPON, new int[]{ItemID.TZHAAR_SPLITSWORD, ItemID.TZHAAR_MACE, ItemID.TZHAAR_MAUL});
		sets.add(new ItemSet(EffectFamily.OBSIDIAN, "Obsidian armour",
			"+10% accuracy and damage with obsidian melee weapons. Stacks with the berserker necklace's damage bonus.",
			obsidianSlots));

		sets.add(fourPiece(EffectFamily.BLOOD_MOON, "Blood moon armour (Bloodrager)",
			"The dual macuahuitl has a 33% chance per successful hit to attack a tick earlier than usual.",
			ItemID.BLOOD_MOON_HELM, ItemID.BLOOD_MOON_CHESTPLATE, ItemID.BLOOD_MOON_TASSETS, ItemID.DUAL_MACUAHUITL, null));
		sets.add(fourPiece(EffectFamily.BLUE_MOON, "Blue moon armour (Frostweaver)",
			"After casting a bind or ice spell (20%) or a grasp spell (50%), the blue moon spear has a chance for its next melee attack to ignore attack delay.",
			ItemID.FROST_MOON_HELM, ItemID.FROST_MOON_CHESTPLATE, ItemID.FROST_MOON_TASSETS, ItemID.FROSTMOON_SPEAR, null));
		sets.add(fourPiece(EffectFamily.ECLIPSE_MOON, "Eclipse moon armour (Eclipse)",
			"The eclipse atlatl has a 20% chance to burn the target for 10 damage over time (stacks up to 5 times).",
			ItemID.ECLIPSE_MOON_HELM, ItemID.ECLIPSE_MOON_CHESTPLATE, ItemID.ECLIPSE_MOON_TASSETS, ItemID.ECLIPSE_ATLATL, null));

		sets.add(new ItemSet(EffectFamily.HUNTER_GEAR, "Kyatt hunter gear",
			"Damage taken from hunter creatures is reduced by 60%.",
			threePiece(ItemID.HUNTING_HAT_TIGER, ItemID.HUNTING_TORSO_TIGER, ItemID.HUNTING_TROUSERS_TIGER)));
		sets.add(new ItemSet(EffectFamily.HUNTER_GEAR, "Graahk hunter gear",
			"Damage taken from hunter creatures is reduced by 40%.",
			threePiece(ItemID.HUNTING_HAT_LEOPARD, ItemID.HUNTING_TORSO_LEOPARD, ItemID.HUNTING_TROUSERS_LEOPARD)));
		sets.add(new ItemSet(EffectFamily.HUNTER_GEAR, "Larupia hunter gear",
			"Damage taken from hunter creatures is reduced by 20%.",
			threePiece(ItemID.HUNTING_HAT_JAGUAR, ItemID.HUNTING_TORSO_JAGUAR, ItemID.HUNTING_TROUSERS_JAGUAR)));

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
		singles.put(ItemID.EMBERLIGHT, new SingleItemEffect(EffectFamily.EMBERLIGHT, "Emberlight", "+70% damage and accuracy against demons."));
		singles.put(ItemID.JEWL_BESERKER_NECKLACE, new SingleItemEffect(EffectFamily.BERSERKER_NECKLACE, "Berserker necklace", "+20% damage with obsidian weapons. Stacks with the obsidian armour set's bonus."));

		// vs-monster-type weapons
		singles.put(ItemID.TWISTED_BOW, new SingleItemEffect(EffectFamily.TWISTED_BOW, "Twisted bow", "Accuracy and damage scale up with the target's Magic level and Magic accuracy bonus."));
		singles.put(ItemID.ZARYTE_XBOW, new SingleItemEffect(EffectFamily.ZARYTE_CROSSBOW, "Zaryte crossbow", "Strengthens enchanted bolt effects by 10%."));
		singles.put(ItemID.DRAGONHUNTER_XBOW, new SingleItemEffect(EffectFamily.DRAGON_HUNTER_WEAPONS, "Dragon hunter crossbow", "+25% damage and +30% accuracy against draconic creatures."));
		singles.put(ItemID.DRAGONHUNTER_LANCE, new SingleItemEffect(EffectFamily.DRAGON_HUNTER_WEAPONS, "Dragon hunter lance", "+20% damage and accuracy against draconic creatures."));
		singles.put(ItemID.DRAGONHUNTER_WAND, new SingleItemEffect(EffectFamily.DRAGON_HUNTER_WEAPONS, "Dragon hunter wand", "+40% damage and +75% accuracy against draconic creatures."));
		singles.put(ItemID.WILD_CAVE_BOW_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Craw's bow", "While charged with at least 1000 revenant ether: +50% ranged damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_WEBWEAVER_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Webweaver bow", "While charged with at least 1000 revenant ether: +50% ranged damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_SCEPTRE_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Thammaron's sceptre", "While charged with at least 1000 revenant ether: +50% magic damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_ACCURSED_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Accursed sceptre", "While charged with at least 1000 revenant ether: +50% magic damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_CHAINMACE_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Viggora's chainmace", "While charged with at least 1000 revenant ether: +50% melee damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.WILD_CAVE_URSINE_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Ursine chainmace", "While charged with at least 1000 revenant ether: +50% melee damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		singles.put(ItemID.SOTD, new SingleItemEffect(EffectFamily.STAVES_OF_THE_DEAD, "Staff of the dead", "12.5% chance to negate the rune cost of a combat spell cast with it."));
		singles.put(ItemID.TOXIC_SOTD_CHARGED, new SingleItemEffect(EffectFamily.STAVES_OF_THE_DEAD, "Toxic staff of the dead", "12.5% chance to negate the rune cost of a combat spell cast with it. In addition, combat spells have a 25% chance to venom the target, increased to 100% when the serpentine helm is worn (vs NPCs)."));
		singles.put(ItemID.STAFF_OF_LIGHT, new SingleItemEffect(EffectFamily.STAVES_OF_THE_DEAD, "Staff of light", "12.5% chance to negate the rune cost of a combat spell cast with it."));
		singles.put(ItemID.KODAI_WAND, new SingleItemEffect(EffectFamily.KODAI_WAND, "Kodai wand", "15% chance to negate the rune cost of an offensive spell cast with it."));
		singles.put(ItemID.SANGUINESTI_STAFF, new SingleItemEffect(EffectFamily.SANGUINESTI_STAFF, "Sanguinesti staff", "20% chance to deal 8 extra damage and heal the wielder for half the total hit."));
		singles.put(ItemID.SILVERLIGHT, new SingleItemEffect(EffectFamily.SILVERLIGHT, "Silverlight", "+60% damage and accuracy against demons."));
		singles.put(ItemID.DARKLIGHT, new SingleItemEffect(EffectFamily.DARKLIGHT, "Darklight", "+60% damage and accuracy against demons."));
		singles.put(ItemID.ARCLIGHT, new SingleItemEffect(EffectFamily.ARCLIGHT, "Arclight", "+70% damage and accuracy against demons."));
		singles.put(ItemID.SCORCHING_BOW, new SingleItemEffect(EffectFamily.SCORCHING_BOW, "Scorching bow", "+30% damage and accuracy against demons."));
		singles.put(ItemID.PURGING_STAFF, new SingleItemEffect(EffectFamily.PURGING_STAFF, "Purging staff", "Doubles demonbane spell bonuses. Mark of Darkness lasts 5x longer when cast with this staff equipped."));
		singles.put(ItemID.KERIS_PARTISAN, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris partisan", "+33% damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		singles.put(ItemID.KERIS_PARTISAN_BREACH, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris partisan of breaching", "+33% accuracy and damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		singles.put(ItemID.KERIS_PARTISAN_SUN, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris partisan of the sun", "+33% damage against kalphites and scarab creatures. In the Tombs of Amascut, +25% accuracy against targets below 25% health, and kills heal 12 HP at the cost of 5 Prayer, overhealing up to 20% above your Hitpoints."));
		singles.put(ItemID.DAGGER_WOLFBANE, new SingleItemEffect(EffectFamily.WOLFBANE, "Wolfbane", "Prevents Canifis residents from transforming into werewolves."));
		singles.put(ItemID.GIANTS_FOUNDRY_COLOSSAL_BLADE, new SingleItemEffect(EffectFamily.COLOSSAL_BLADE, "Colossal blade", "Increases max hit against larger enemies, scaling with the target's size."));
		singles.put(ItemID.LEAFBLADED_BATTLEAXE, new SingleItemEffect(EffectFamily.LEAF_BLADED_BATTLEAXE, "Leaf-bladed battleaxe", "+17.5% damage against turoths and kurasks. Stacks with the Slayer helmet."));
		singles.put(ItemID.OSMUMTENS_FANG, new SingleItemEffect(EffectFamily.OSMUMTENS_FANG, "Osmumten's fang", "When on stab, rolls accuracy twice and uses the higher roll. Normal successful hits deal 15%-85% of max hit."));
		singles.put(ItemID.TUMEKENS_SHADOW, new SingleItemEffect(EffectFamily.TUMEKENS_SHADOW, "Tumeken's shadow", "Triples the magic attack and damage bonuses from worn equipment, capped at 100% (quadruples inside the Tombs of Amascut)."));
		singles.put(ItemID.HALLOWFELL, new SingleItemEffect(EffectFamily.HALLOWFELL, "Hallowfell", "Cleaves up to 2 additional targets in front of or beside you, for up to 50% of your max hit each."));
		singles.put(ItemID.ANCIENT_SCEPTRE, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Ancient sceptre", "+10% boost to Ancient Magicks secondary effects."));
		singles.put(ItemID.ANCIENT_SCEPTRE_BLOOD, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Blood ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Blood spells can overheal by 10%."));
		singles.put(ItemID.ANCIENT_SCEPTRE_ICE, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Ice ancient sceptre", "+10% boost to Ancient Magicks secondary effects. +10% extra accuracy for ice spells against unfrozen, freezable targets."));
		singles.put(ItemID.ANCIENT_SCEPTRE_SHADOW, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Shadow ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Shadow spells also lower the target's Attack, Strength, and Defence."));
		singles.put(ItemID.ANCIENT_SCEPTRE_SMOKE, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Smoke ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Temporarily reduces a poisoned target's healing by 20% for 6 seconds."));
		singles.put(ItemID.RAT_BONE_MACE, new SingleItemEffect(EffectFamily.BONE_WEAPONS, "Bone mace", "+10 to max hit. Only effective against rats."));
		singles.put(ItemID.RAT_BONE_BOW, new SingleItemEffect(EffectFamily.BONE_WEAPONS, "Bone shortbow", "+10 to max hit. Only effective against rats."));
		singles.put(ItemID.BARRONITE_MACE, new SingleItemEffect(EffectFamily.BARRONITE_MACE, "Barronite mace", "+15% damage and accuracy against golems."));
		singles.put(ItemID.GRANITE_HAMMER, new SingleItemEffect(EffectFamily.GRANITE_HAMMER, "Granite hammer", "+30% damage and accuracy against golems."));

		// shields
		singles.put(ItemID.ELYSIAN, new SingleItemEffect(EffectFamily.ELYSIAN_SPIRIT_SHIELD, "Elysian spirit shield", "70% chance to reduce incoming damage by 25%."));
		singles.put(ItemID.SPECTRAL, new SingleItemEffect(EffectFamily.SPECTRAL_SPIRIT_SHIELD, "Spectral spirit shield", "Reduces the effectiveness of prayer-draining attacks by 50% (outside PvP)."));
		singles.put(ItemID.DINHS_BULWARK, new SingleItemEffect(EffectFamily.DINHS_BULWARK, "Dinh's bulwark", "Block attack style reduces incoming damage by 20% (outside PvP). Pummel attack style increases max hit based on your defensive bonuses."));
		singles.put(ItemID.ANTIDRAGONBREATHSHIELD, new SingleItemEffect(EffectFamily.ANTI_DRAGON_SHIELD, "Anti-dragon shield", "Partial protection against dragonfire."));
		singles.put(ItemID.ELEMENTAL_SHIELD, new SingleItemEffect(EffectFamily.ELEMENTAL_SHIELD, "Elemental shield", "Partial protection against wyverns' icy breath."));
		singles.put(ItemID.ELEMENTAL_MIND_SHIELD, new SingleItemEffect(EffectFamily.MIND_SHIELD, "Mind shield", "Partial protection against wyverns' icy breath."));
		singles.put(ItemID.DRAGONFIRE_SHIELD, new SingleItemEffect(EffectFamily.DRAGONFIRE_SHIELD, "Dragonfire shield", "Partial protection against dragonfire and wyverns' icy breath."));
		singles.put(ItemID.WYVERN_SHIELD, new SingleItemEffect(EffectFamily.ANCIENT_WYVERN_SHIELD, "Ancient wyvern shield", "Partial protection against wyverns' icy breath and blocks the freeze effect."));
		singles.put(ItemID.DRAGONFIRE_WARD, new SingleItemEffect(EffectFamily.DRAGONFIRE_WARD, "Dragonfire ward", "Partial protection against dragonfire and wyverns' icy breath."));

		// headgear
		singles.put(ItemID.SERPENTINE_HELM, new SingleItemEffect(EffectFamily.SERPENTINE_HELM, "Serpentine helm", "While charged with scales, grants immunity to venom and poison. Chance to inflict venom: 16.7% with a non-poisoned melee weapon, 50% with poison or the noxious halberd, or 100% with toxic blowpipe, trident of the swamp, or toxic staff of the dead."));

		// handwear
		singles.put(ItemID.GAUNTLETS_OF_CHAOS, new SingleItemEffect(EffectFamily.CHAOS_GAUNTLETS, "Chaos gauntlets", "+3 max hit on Bolt spells."));
		singles.put(ItemID.JEWL_BRACELET_REGEN, new SingleItemEffect(EffectFamily.REGEN_BRACELET, "Regen bracelet", "Doubles the rate of natural Hitpoints regeneration."));

		// amulets
		singles.put(ItemID.NECKLACE_OF_FAITH, new SingleItemEffect(EffectFamily.NECKLACE_OF_FAITH, "Necklace of faith", "When hit below 20% Hitpoints, restores Prayer points equal to 25% of your Prayer level, then breaks."));
		singles.put(ItemID.JEWL_NECKLACE_OF_PHOENIX, new SingleItemEffect(EffectFamily.PHOENIX_NECKLACE, "Phoenix necklace", "When hit below 20% Hitpoints, heals 30% of max Hitpoints, then breaks."));
		singles.put(ItemID.AMULET_OF_GLORY, new SingleItemEffect(EffectFamily.AMULET_OF_GLORY, "Amulet of glory", "While charged, increases gem-finding chance while mining and increases mining speed on gem rocks."));
		singles.put(ItemID.WILD_CAVE_AMULET, new SingleItemEffect(EffectFamily.AMULET_OF_AVARICE, "Amulet of avarice", "Revenant, Wilderness Slayer Cave, and Chaos Temple drops will be noted. Revenant unique drop rates double, and you gain +20% increased damage and accuracy against revenants. Skulls the player while worn."));
		singles.put(ItemID.BLOOD_AMULET, new SingleItemEffect(EffectFamily.AMULET_OF_BLOOD_FURY, "Amulet of blood fury", "20% chance to heal for 30% of melee damage dealt."));

		// rings
		singles.put(ItemID.RING_OF_LIFE, new SingleItemEffect(EffectFamily.RING_OF_LIFE, "Ring of life", "When hit below 10% Hitpoints, teleports you to your respawn point, then breaks."));
		singles.put(ItemID.RING_OF_WEALTH, new SingleItemEffect(EffectFamily.RING_OF_WEALTH, "Ring of wealth", "Removes empty Rare Drop Table slots and can collect currency drops for you."));
		singles.put(ItemID.RING_OF_RECOIL, new SingleItemEffect(EffectFamily.RING_OF_RECOIL, "Ring of recoil", "Returns 1+10% of damage taken back at the attacker."));
		singles.put(ItemID.ZENYTE_RING_ENCHANTED, new SingleItemEffect(EffectFamily.RING_OF_SUFFERING, "Ring of suffering", "When charged with rings of recoil, returns 1+10% of damage taken back at the attacker."));
		singles.put(ItemID.BRIMSTONE_RING, new SingleItemEffect(EffectFamily.BRIMSTONE_RING, "Brimstone ring", "Combat spells have a 25% chance to ignore 10% of the target's magic defence."));
		singles.put(ItemID.AMULET_OF_AIR, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of air", "Increases the max hit of your air spells by 2."));
		singles.put(ItemID.AMULET_OF_WATER, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of water", "Increases the max hit of your water spells by 2."));
		singles.put(ItemID.AMULET_OF_EARTH, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of earth", "Increases the max hit of your earth spells by 2."));
		singles.put(ItemID.AMULET_OF_FIRE, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of fire", "Increases the max hit of your fire spells by 2."));
		singles.put(ItemID.ELEMENTAL_AMULET, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Elemental amulet", "Increases the max hit of your air, water, earth and fire spells by 2."));
		singles.put(ItemID.DRAGONBONE_NECKLACE, new SingleItemEffect(EffectFamily.BONE_NECKLACES, "Dragonbone necklace", "Restores 1-5 Prayer points per bone buried, depending on the bone. Does not stack with the Catacombs of Kourend effect."));
		singles.put(ItemID.BONECRUSHER_NECKLACE, new SingleItemEffect(EffectFamily.BONE_NECKLACES, "Bonecrusher necklace", "Automatically crushes bones and restores 1-5 Prayer points per bone. Requires ecto-token charges."));

		// misc utility
		singles.put(ItemID.WATER_CIRCLET, new SingleItemEffect(EffectFamily.CIRCLET_OF_WATER, "Circlet of water", "While charged with water runes, protects against desert heat."));
		singles.put(ItemID.DIZANAS_QUIVER_BROKEN, new SingleItemEffect(EffectFamily.DIZANAS_QUIVER, "Dizana's quiver", "Increases ranged attack by 10 and ranged strength by 1. Only applies when using arrows and bolts."));
		singles.put(ItemID.DIZANAS_QUIVER_INFINITE_BROKEN, new SingleItemEffect(EffectFamily.DIZANAS_QUIVER, "Blessed Dizana's quiver", "Increases ranged attack by 10 and ranged strength by 1. Only applies when using arrows and bolts."));
		singles.put(ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN, new SingleItemEffect(EffectFamily.DIZANAS_QUIVER, "Dizana's max cape", "Increases ranged attack by 10 and ranged strength by 1. Only applies when using arrows and bolts."));

		SINGLE_ITEM_EFFECTS = Collections.unmodifiableMap(singles);
	}

	private static ItemSet fourPiece(EffectFamily family, String name, String effect, int head, int body, int legs, int weapon, String amuletOfTheDamnedSynergy)
	{
		Map<EquipmentInventorySlot, int[]> slots = new EnumMap<>(EquipmentInventorySlot.class);
		slots.put(EquipmentInventorySlot.HEAD, new int[]{head});
		slots.put(EquipmentInventorySlot.BODY, new int[]{body});
		slots.put(EquipmentInventorySlot.LEGS, new int[]{legs});
		slots.put(EquipmentInventorySlot.WEAPON, new int[]{weapon});
		return new ItemSet(family, name, effect, slots, amuletOfTheDamnedSynergy);
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
