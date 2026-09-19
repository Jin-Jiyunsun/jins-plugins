package com.seteffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

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
	// How many single items are registered, so a test can tell if two collapsed onto the same key
	static final int SINGLE_ITEM_COUNT;

	private static int registered;

	/**
	 * Lookups go through {@link ItemVariationMapping#map}, which collapses a variant chain to one id, so
	 * an entry has to be stored under that same mapped id or it can never be found - a charged item's own
	 * id is usually not its chain's base. Mapping here, at load, keeps entries findable however the
	 * client's variation data is ordered.
	 */
	private static void register(Map<Integer, SingleItemEffect> singles, int itemId, SingleItemEffect effect)
	{
		registered++;
		singles.put(ItemVariationMapping.map(itemId), effect);
	}

	static
	{
		List<ItemSet> sets = new ArrayList<>();

		sets.add(fourPiece(EffectFamily.AHRIMS, "Ahrim's set",
			"Successful magic attacks have a 25% chance to lower the target's Strength by 5 levels.",
			ItemID.BARROWS_AHRIM_HEAD, ItemID.BARROWS_AHRIM_BODY, ItemID.BARROWS_AHRIM_LEGS, ItemID.BARROWS_AHRIM_WEAPON,
			"Autocasts Ancient Magicks, 25% chance of 30% increased damage."));
		sets.add(fourPiece(EffectFamily.DHAROKS, "Dharok's set",
			"Damage increases as HP decrease, up to +98% extra damage at 1 HP (with 99 HP).",
			ItemID.BARROWS_DHAROK_HEAD, ItemID.BARROWS_DHAROK_BODY, ItemID.BARROWS_DHAROK_LEGS, ItemID.BARROWS_DHAROK_WEAPON,
			"25% chance to recoil 15% of damage taken back at the attacker."));
		sets.add(fourPiece(EffectFamily.GUTHANS, "Guthan's set",
			"Successful melee attacks have a 25% chance to heal HP equal to the damage dealt.",
			ItemID.BARROWS_GUTHAN_HEAD, ItemID.BARROWS_GUTHAN_BODY, ItemID.BARROWS_GUTHAN_LEGS, ItemID.BARROWS_GUTHAN_WEAPON,
			"Allows healing up to 10 HP above your base level."));
		sets.add(fourPiece(EffectFamily.KARILS, "Karil's set",
			"Successful ranged attacks have a 25% chance to lower the target's Agility level by 20%.",
			ItemID.BARROWS_KARIL_HEAD, ItemID.BARROWS_KARIL_BODY, ItemID.BARROWS_KARIL_LEGS, ItemID.BARROWS_KARIL_WEAPON,
			"25% chance to hit twice with one attack; the second hit deals half damage."));
		sets.add(fourPiece(EffectFamily.TORAGS, "Torag's set",
			"Successful melee attacks have a 25% chance to lower the target's run energy by 20%.",
			ItemID.BARROWS_TORAG_HEAD, ItemID.BARROWS_TORAG_BODY, ItemID.BARROWS_TORAG_LEGS, ItemID.BARROWS_TORAG_WEAPON,
			"Defence level increases by 1% per HP missing."));
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
		register(singles, ItemID.EMBERLIGHT, new SingleItemEffect(EffectFamily.EMBERLIGHT, "Emberlight", "+70% damage and accuracy against demons."));
		register(singles, ItemID.JEWL_BESERKER_NECKLACE, new SingleItemEffect(EffectFamily.BERSERKER_NECKLACE, "Berserker necklace", "+20% damage with obsidian weapons. Stacks with the obsidian armour set's bonus."));

		// vs-monster-type weapons
		register(singles, ItemID.TWISTED_BOW, new SingleItemEffect(EffectFamily.TWISTED_BOW, "Twisted bow", "Accuracy and damage scale up with the target's Magic level and Magic accuracy bonus."));
		register(singles, ItemID.ZARYTE_XBOW, new SingleItemEffect(EffectFamily.ZARYTE_CROSSBOW, "Zaryte crossbow", "Strengthens enchanted bolt effects by 10%."));
		register(singles, ItemID.DRAGONHUNTER_XBOW, new SingleItemEffect(EffectFamily.DRAGON_HUNTER_WEAPONS, "Dragon hunter crossbow", "+25% damage and +30% accuracy against draconic creatures."));
		register(singles, ItemID.DRAGONHUNTER_LANCE, new SingleItemEffect(EffectFamily.DRAGON_HUNTER_WEAPONS, "Dragon hunter lance", "+20% damage and accuracy against draconic creatures."));
		register(singles, ItemID.DRAGONHUNTER_WAND, new SingleItemEffect(EffectFamily.DRAGON_HUNTER_WEAPONS, "Dragon hunter wand", "+40% damage and +75% accuracy against draconic creatures."));
		register(singles, ItemID.WILD_CAVE_BOW_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Craw's bow", "While charged with at least 1000 revenant ether: +50% ranged damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		register(singles, ItemID.WILD_CAVE_WEBWEAVER_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Webweaver bow", "While charged with at least 1000 revenant ether: +50% ranged damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		register(singles, ItemID.WILD_CAVE_SCEPTRE_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Thammaron's sceptre", "While charged with at least 1000 revenant ether: +50% magic damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		register(singles, ItemID.WILD_CAVE_ACCURSED_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Accursed sceptre", "While charged with at least 1000 revenant ether: +50% magic damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		register(singles, ItemID.WILD_CAVE_CHAINMACE_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Viggora's chainmace", "While charged with at least 1000 revenant ether: +50% melee damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		register(singles, ItemID.WILD_CAVE_URSINE_CHARGED, new SingleItemEffect(EffectFamily.REVENANT_WEAPONS, "Ursine chainmace", "While charged with at least 1000 revenant ether: +50% melee damage and accuracy against any NPC in the Wilderness (consumes ether per attack)."));
		register(singles, ItemID.SOTD, new SingleItemEffect(EffectFamily.STAVES_OF_THE_DEAD, "Staff of the dead", "14.2% chance to negate the rune cost of a combat spell cast with it."));
		register(singles, ItemID.TOXIC_SOTD_CHARGED, new SingleItemEffect(EffectFamily.STAVES_OF_THE_DEAD, "Toxic staff of the dead", "14.2% chance to negate the rune cost of a combat spell cast with it. In addition, combat spells have a 25% chance to venom the target, increased to 100% when the serpentine helm is worn (vs NPCs)."));
		register(singles, ItemID.STAFF_OF_LIGHT, new SingleItemEffect(EffectFamily.STAVES_OF_THE_DEAD, "Staff of light", "14.2% chance to negate the rune cost of a combat spell cast with it."));
		register(singles, ItemID.KODAI_WAND, new SingleItemEffect(EffectFamily.KODAI_WAND, "Kodai wand", "15% chance to negate the rune cost of an offensive spell cast with it."));
		register(singles, ItemID.SANGUINESTI_STAFF, new SingleItemEffect(EffectFamily.SANGUINESTI_STAFF, "Sanguinesti staff", "20% chance to deal 8 extra damage and heal the wielder for half the total hit."));
		register(singles, ItemID.SILVERLIGHT, new SingleItemEffect(EffectFamily.SILVERLIGHT, "Silverlight", "+60% damage and accuracy against demons."));
		register(singles, ItemID.DARKLIGHT, new SingleItemEffect(EffectFamily.DARKLIGHT, "Darklight", "+60% damage and accuracy against demons."));
		register(singles, ItemID.ARCLIGHT, new SingleItemEffect(EffectFamily.ARCLIGHT, "Arclight", "+70% damage and accuracy against demons."));
		register(singles, ItemID.SCORCHING_BOW, new SingleItemEffect(EffectFamily.SCORCHING_BOW, "Scorching bow", "+30% damage and accuracy against demons."));
		register(singles, ItemID.PURGING_STAFF, new SingleItemEffect(EffectFamily.PURGING_STAFF, "Purging staff", "Doubles demonbane spell bonuses. Mark of Darkness lasts 5x longer when cast with this staff equipped."));
		register(singles, ItemID.CONTACT_KERIS, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris", "+33% damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		register(singles, ItemID.KERIS_PARTISAN, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris partisan", "+33% damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		register(singles, ItemID.KERIS_PARTISAN_BREACH, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris partisan of breaching", "+33% accuracy and damage against kalphites and scarab creatures. 1.96% chance to deal triple damage."));
		register(singles, ItemID.KERIS_PARTISAN_SUN, new SingleItemEffect(EffectFamily.KERIS_PARTISANS, "Keris partisan of the sun", "+33% damage against kalphites and scarab creatures. In the Tombs of Amascut, +25% accuracy against targets below 25% health, and kills heal 12 HP at the cost of 5 Prayer, overhealing up to 20% above your HP."));
		register(singles, ItemID.DAGGER_WOLFBANE, new SingleItemEffect(EffectFamily.WOLFBANE, "Wolfbane", "Prevents Canifis residents from transforming into werewolves."));
		register(singles, ItemID.GIANTS_FOUNDRY_COLOSSAL_BLADE, new SingleItemEffect(EffectFamily.COLOSSAL_BLADE, "Colossal blade", "Increases max hit against larger enemies, scaling with the target's size."));
		register(singles, ItemID.LEAFBLADED_BATTLEAXE, new SingleItemEffect(EffectFamily.LEAF_BLADED_BATTLEAXE, "Leaf-bladed battleaxe", "+17.5% damage against turoths and kurasks. Stacks with the Slayer helmet."));
		register(singles, ItemID.OSMUMTENS_FANG, new SingleItemEffect(EffectFamily.OSMUMTENS_FANG, "Osmumten's fang", "When on stab, rolls accuracy twice and uses the higher roll. Normal successful hits deal 15%-85% of max hit."));
		register(singles, ItemID.TUMEKENS_SHADOW, new SingleItemEffect(EffectFamily.TUMEKENS_SHADOW, "Tumeken's shadow", "Triples the magic attack and damage bonuses from worn equipment, capped at 100% (quadruples inside the Tombs of Amascut)."));
		register(singles, ItemID.HALLOWFELL, new SingleItemEffect(EffectFamily.HALLOWFELL, "Hallowfell", "Cleaves up to 2 additional targets in front of or beside you, for up to 50% of your max hit each."));
		register(singles, ItemID.ANCIENT_SCEPTRE, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Ancient sceptre", "+10% boost to Ancient Magicks secondary effects."));
		register(singles, ItemID.ANCIENT_SCEPTRE_BLOOD, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Blood ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Blood spells can overheal by 10%."));
		register(singles, ItemID.ANCIENT_SCEPTRE_ICE, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Ice ancient sceptre", "+10% boost to Ancient Magicks secondary effects. +10% extra accuracy for ice spells against unfrozen, freezable targets."));
		register(singles, ItemID.ANCIENT_SCEPTRE_SHADOW, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Shadow ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Shadow spells also lower the target's Attack, Strength, and Defence."));
		register(singles, ItemID.ANCIENT_SCEPTRE_SMOKE, new SingleItemEffect(EffectFamily.ANCIENT_SCEPTRES, "Smoke ancient sceptre", "+10% boost to Ancient Magicks secondary effects. Temporarily reduces a poisoned target's healing by 20% for 6 seconds."));
		register(singles, ItemID.RAT_BONE_MACE, new SingleItemEffect(EffectFamily.BONE_WEAPONS, "Bone mace", "+10 to max hit. Only effective against rats."));
		register(singles, ItemID.RAT_BONE_BOW, new SingleItemEffect(EffectFamily.BONE_WEAPONS, "Bone shortbow", "+10 to max hit. Only effective against rats."));
		register(singles, ItemID.BARRONITE_MACE, new SingleItemEffect(EffectFamily.BARRONITE_MACE, "Barronite mace", "+15% damage and accuracy against golems."));
		register(singles, ItemID.TWINFLAME_STAFF, new SingleItemEffect(EffectFamily.TWINFLAME_STAFF, "Twinflame staff", "Provides infinite fire and water runes and +10% accuracy and damage with standard spells. Elemental spells (except Strike and Surge) fire a second hit for 40% damage."));
		register(singles, ItemID.INFERNAL_AXE, new SingleItemEffect(EffectFamily.INFERNAL_TOOLS, "Infernal axe", "While charged, 1/3 chance to burn chopped logs for half Firemaking experience."));
		register(singles, ItemID.INFERNAL_HARPOON, new SingleItemEffect(EffectFamily.INFERNAL_TOOLS, "Infernal harpoon", "While charged, 1/3 chance to cook caught fish instantly for half Cooking experience, destroying them."));
		register(singles, ItemID.INFERNAL_PICKAXE, new SingleItemEffect(EffectFamily.INFERNAL_TOOLS, "Infernal pickaxe", "While charged, 1/3 chance for mined ore to combust for half Smithing experience, destroying it."));
		register(singles, ItemID.DRAGON_HARPOON, new SingleItemEffect(EffectFamily.HARPOONS, "Dragon harpoon", "+20% catch rate when harpoon fishing (+5% at Tempoross)."));
		register(singles, ItemID.CRYSTAL_HARPOON, new SingleItemEffect(EffectFamily.HARPOONS, "Crystal harpoon", "While charged, +35% catch rate when harpoon fishing (+10% at Tempoross). 1/3 of fish at Tempoross become crystallised harpoonfish."));
		register(singles, ItemID.SUNSPEAR, new SingleItemEffect(EffectFamily.VAMPYRE_WEAPONS, "Sunspear", "+50% damage and +25% accuracy against vampyres."));
		register(singles, ItemID.IVANDIS_FLAIL, new SingleItemEffect(EffectFamily.VAMPYRE_WEAPONS, "Ivandis flail", "+20% damage against vampyres."));
		register(singles, ItemID.BLISTERWOOD_FLAIL, new SingleItemEffect(EffectFamily.VAMPYRE_WEAPONS, "Blisterwood flail", "+25% damage and +5% accuracy against vampyres."));
		register(singles, ItemID.BLISTERWOOD_STAKE, new SingleItemEffect(EffectFamily.VAMPYRE_WEAPONS, "Blisterwood stake", "+25% damage and accuracy against vampyres."));
		register(singles, ItemID.HALLOWED_FLAIL, new SingleItemEffect(EffectFamily.VAMPYRE_WEAPONS, "Hallowed flail", "+25% damage and accuracy against vampyres."));
		register(singles, ItemID.SCYTHE_OF_VITUR, new SingleItemEffect(EffectFamily.SCYTHE_OF_VITUR, "Scythe of Vitur", "Hits up to three targets in front of you, or a large monster up to three times. Each hit deals 50% less damage than the last."));
		register(singles, ItemID.OLAF2_BRINE_SABRE, new SingleItemEffect(EffectFamily.BRINE_SABRE, "Brine sabre", "Finishes off rockslugs without a bag of salt."));
		register(singles, ItemID.GRANITE_HAMMER, new SingleItemEffect(EffectFamily.GRANITE_HAMMER, "Granite hammer", "+30% damage and accuracy against golems."));

		// shields
		register(singles, ItemID.ELYSIAN, new SingleItemEffect(EffectFamily.ELYSIAN_SPIRIT_SHIELD, "Elysian spirit shield", "70% chance to reduce incoming damage by 25%."));
		register(singles, ItemID.SPECTRAL, new SingleItemEffect(EffectFamily.SPECTRAL_SPIRIT_SHIELD, "Spectral spirit shield", "Reduces the effectiveness of prayer-draining attacks by 50% (outside PvP)."));
		register(singles, ItemID.DINHS_BULWARK, new SingleItemEffect(EffectFamily.DINHS_BULWARK, "Dinh's bulwark", "Block attack style reduces incoming damage by 20% (outside PvP). Pummel attack style increases max hit based on your defensive bonuses."));
		register(singles, ItemID.ANTIDRAGONBREATHSHIELD, new SingleItemEffect(EffectFamily.ANTI_DRAGON_SHIELD, "Anti-dragon shield", "Partial protection against dragonfire."));
		register(singles, ItemID.ELEMENTAL_SHIELD, new SingleItemEffect(EffectFamily.ELEMENTAL_SHIELD, "Elemental shield", "Partial protection against wyverns' icy breath."));
		register(singles, ItemID.ELEMENTAL_MIND_SHIELD, new SingleItemEffect(EffectFamily.MIND_SHIELD, "Mind shield", "Partial protection against wyverns' icy breath."));
		register(singles, ItemID.DRAGONFIRE_SHIELD, new SingleItemEffect(EffectFamily.DRAGONFIRE_SHIELD, "Dragonfire shield", "Partial protection against dragonfire and wyverns' icy breath."));
		register(singles, ItemID.WYVERN_SHIELD, new SingleItemEffect(EffectFamily.ANCIENT_WYVERN_SHIELD, "Ancient wyvern shield", "Partial protection against wyverns' icy breath and blocks the freeze effect."));
		register(singles, ItemID.DRAGONFIRE_WARD, new SingleItemEffect(EffectFamily.DRAGONFIRE_WARD, "Dragonfire ward", "Partial protection against dragonfire and wyverns' icy breath."));

		// headgear
		register(singles, ItemID.SERPENTINE_HELM, new SingleItemEffect(EffectFamily.SERPENTINE_HELM, "Serpentine helm", "While charged with scales, grants immunity to venom and poison. Chance to inflict venom: 16.7% with a non-poisoned melee weapon, 50% with poison or the noxious halberd, or 100% with toxic blowpipe, trident of the swamp, or toxic staff of the dead."));

		// handwear
		register(singles, ItemID.GAUNTLETS_OF_CHAOS, new SingleItemEffect(EffectFamily.CHAOS_GAUNTLETS, "Chaos gauntlets", "Increases the max hit of your bolt spells by 3, before magic damage and weakness bonuses are applied."));
		register(singles, ItemID.JEWL_BRACELET_REGEN, new SingleItemEffect(EffectFamily.REGEN_BRACELET, "Regen bracelet", "Doubles the rate of natural HP regeneration. Stacks with Rapid Heal or the Hitpoints cape, not both."));

		// amulets
		register(singles, ItemID.NECKLACE_OF_FAITH, new SingleItemEffect(EffectFamily.NECKLACE_OF_FAITH, "Necklace of faith", "When hit below 20% HP, restores Prayer points equal to 25% of your Prayer level, then breaks."));
		register(singles, ItemID.JEWL_NECKLACE_OF_PHOENIX, new SingleItemEffect(EffectFamily.PHOENIX_NECKLACE, "Phoenix necklace", "When hit below 20% HP, heals 30% of max HP, then breaks."));
		register(singles, ItemID.AMULET_OF_GLORY, new SingleItemEffect(EffectFamily.AMULET_OF_GLORY, "Amulet of glory", "While charged, increases gem-finding chance while mining and increases mining speed on gem rocks."));
		register(singles, ItemID.WILD_CAVE_AMULET, new SingleItemEffect(EffectFamily.AMULET_OF_AVARICE, "Amulet of avarice", "Most Revenant, Wilderness Slayer Cave, and Chaos Temple drops will be noted. Revenant unique drop rates double, and you gain +20% damage and accuracy against revenants. Skulls the player while worn."));
		register(singles, ItemID.BLOOD_AMULET, new SingleItemEffect(EffectFamily.AMULET_OF_BLOOD_FURY, "Amulet of blood fury", "20% chance to heal for 30% of melee damage dealt."));

		// rings
		register(singles, ItemID.RING_OF_LIFE, new SingleItemEffect(EffectFamily.RING_OF_LIFE, "Ring of life", "When hit below 10% HP, teleports you to your respawn point, then breaks."));
		register(singles, ItemID.RING_OF_WEALTH, new SingleItemEffect(EffectFamily.RING_OF_WEALTH, "Ring of wealth", "Removes empty Rare Drop Table slots and can collect currency drops for you."));
		register(singles, ItemID.RING_OF_RECOIL, new SingleItemEffect(EffectFamily.RING_OF_RECOIL, "Ring of recoil", "Returns 1+10% of damage taken back at the attacker."));
		register(singles, ItemID.ZENYTE_RING_ENCHANTED, new SingleItemEffect(EffectFamily.RING_OF_SUFFERING, "Ring of suffering", "When charged with rings of recoil, returns 1+10% of damage taken back at the attacker."));
		register(singles, ItemID.BRIMSTONE_RING, new SingleItemEffect(EffectFamily.BRIMSTONE_RING, "Brimstone ring", "Combat spells have a 25% chance to ignore 10% of the target's magic defence."));
		register(singles, ItemID.AMULET_OF_AIR, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of air", "Increases the max hit of your air spells by 2, before magic damage and weakness bonuses are applied."));
		register(singles, ItemID.AMULET_OF_WATER, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of water", "Increases the max hit of your water spells by 2, before magic damage and weakness bonuses are applied."));
		register(singles, ItemID.AMULET_OF_EARTH, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of earth", "Increases the max hit of your earth spells by 2, before magic damage and weakness bonuses are applied."));
		register(singles, ItemID.AMULET_OF_FIRE, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Amulet of fire", "Increases the max hit of your fire spells by 2, before magic damage and weakness bonuses are applied."));
		register(singles, ItemID.ELEMENTAL_AMULET, new SingleItemEffect(EffectFamily.ELEMENTAL_AMULETS, "Elemental amulet", "Increases the max hit of your air, water, earth and fire spells by 2, before magic damage and weakness bonuses are applied."));
		register(singles, ItemID.DRAGONBONE_NECKLACE, new SingleItemEffect(EffectFamily.BONE_NECKLACES, "Dragonbone necklace", "Restores 1-5 Prayer points per bone buried, depending on the bone. Does not stack with the Catacombs of Kourend effect."));
		register(singles, ItemID.BONECRUSHER_NECKLACE, new SingleItemEffect(EffectFamily.BONE_NECKLACES, "Bonecrusher necklace", "Automatically crushes bones and restores 1-5 Prayer points per bone. Requires ecto-token charges."));

		// misc utility
		register(singles, ItemID.WATER_CIRCLET, new SingleItemEffect(EffectFamily.CIRCLET_OF_WATER, "Circlet of water", "While charged with water runes, protects against desert heat."));
		// Capes of accomplishment (keyed by the untrimmed cape - the trimmed one maps to it)
		register(singles, ItemID.SKILLCAPE_COOKING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Cooking cape", "Food never burns while cooking."));
		register(singles, ItemID.SKILLCAPE_DEFENCE, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Defence cape", "Acts as a ring of life, teleporting you to your respawn point when hit below 10% HP."));
		register(singles, ItemID.SKILLCAPE_FARMING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Farming cape", "+5% to the base chance to save harvest lives and gain extra herbs from herb patches. Stacks with magic secateurs."));
		register(singles, ItemID.SKILLCAPE_HERBLORE, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Herblore cape", "Grimy herbs can be used to make unfinished potions, giving the experience of cleaning the herb."));
		register(singles, ItemID.SKILLCAPE_HITPOINTS, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Hitpoints cape", "Doubles your natural HP restoration rate. Does not stack with Rapid Heal, but stacks with the regen bracelet."));
		register(singles, ItemID.SKILLCAPE_MINING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Mining cape", "5% chance of an extra ore from rocks up to adamantite. Stacks with Varrock armour."));
		register(singles, ItemID.SKILLCAPE_PRAYER, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Prayer cape", "Increases the Prayer points restored by prayer potions, like the holy wrench. Works worn or in your inventory. Does not stack with the wrench or ring of the gods (i)."));
		register(singles, ItemID.SKILLCAPE_RANGING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Ranging cape", "Acts as Ava's accumulator, recovering 72% of your fired ammunition (80% once upgraded with Vorkath's head)."));
		register(singles, ItemID.AQUANITE_HOPPER, new SingleItemEffect(EffectFamily.AQUANITE_HOPPER, "Aquanite hopper", "11% chance for crossbows to fire a second, weaker shot."));
		register(singles, ItemID.SLAYER_MIRROR_SHIELD, new SingleItemEffect(EffectFamily.MIRROR_SHIELDS, "Mirror shield", "Protects against the stat reduction from cockatrices and basilisks."));
		register(singles, ItemID.VIKINGEXILE_V_SHIELD, new SingleItemEffect(EffectFamily.MIRROR_SHIELDS, "V's shield", "Acts as a mirror shield against basilisks, basilisk knights and cockatrices."));
		register(singles, ItemID.TOME_OF_FIRE, new SingleItemEffect(EffectFamily.ELEMENTAL_TOMES, "Tome of fire", "While charged, provides infinite fire runes and +10% fire spell damage (+50% in PvP)."));
		register(singles, ItemID.TOME_OF_WATER, new SingleItemEffect(EffectFamily.ELEMENTAL_TOMES, "Tome of water", "While charged, provides infinite water runes and +10% water spell accuracy and damage (+20% in PvP)."));
		register(singles, ItemID.TOME_OF_EARTH, new SingleItemEffect(EffectFamily.ELEMENTAL_TOMES, "Tome of earth", "While charged, provides infinite earth runes and +10% earth spell damage."));
		register(singles, ItemID.GAUNTLETS_OF_GOLDSMITHING, new SingleItemEffect(EffectFamily.GOLDSMITH_GAUNTLETS, "Goldsmith gauntlets", "Increases the experience from smelting gold bars by 2.5x. Works at the Blast Furnace."));
		register(singles, ItemID.GAUNTLETS_OF_COOKING, new SingleItemEffect(EffectFamily.COOKING_GAUNTLETS, "Cooking gauntlets", "Reduces the chance of burning lobsters, swordfish, monkfish, sharks, anglerfish and kyatt when cooking."));
		register(singles, ItemID.WILD_CAVE_BRACELET_CHARGED, new SingleItemEffect(EffectFamily.BRACELET_OF_ETHEREUM, "Bracelet of ethereum", "While charged, reduces damage taken from revenants by 75% and makes them tolerant."));
		register(singles, ItemID.ARANEA_BOOTS, new SingleItemEffect(EffectFamily.ARANEA_BOOTS, "Aranea boots", "Lets you pass through webs without a slash weapon, and negates some spider-based boss attacks once every 8 ticks."));
		register(singles, ItemID.ECHO_BOOTS, new SingleItemEffect(EffectFamily.ECHO_BOOTS, "Echo boots", "When charged, returns 1 damage back at attackers within a 3x3 square."));
		register(singles, ItemID.TORTUGAN_SHIELD, new SingleItemEffect(EffectFamily.TORTUGAN_SHIELD, "Tortugan shield", "Negates the devastating attacks of dire and shellbane gryphons, letting Protect from Melee fully block their melee attacks."));
		register(singles, ItemID.MEDALLION_OF_THE_DEEP, new SingleItemEffect(EffectFamily.MEDALLION_OF_THE_DEEP, "Medallion of the Deep", "Lets you breathe underwater without diving apparatus or a fishbowl helmet, and reduces oxygen depletion by 80% in the underwater Agility and Thieving area."));
		register(singles, ItemID.DODGY_NECKLACE, new SingleItemEffect(EffectFamily.DODGY_NECKLACE, "Dodgy necklace", "25% chance to prevent being stunned and damaged when pickpocketing. Stacks with Shadow Veil."));
		register(singles, ItemID.VAMPYRE_RING, new SingleItemEffect(EffectFamily.EFARITAYS_AID, "Efaritay's aid", "+10% damage and +15% accuracy against vampyres. Stacks with the black mask or Slayer helmet."));
		register(singles, ItemID.LIGHTBEARER, new SingleItemEffect(EffectFamily.LIGHTBEARER, "Lightbearer", "Regenerates special attack energy twice as fast."));
		register(singles, ItemID.CELESTIAL_RING, new SingleItemEffect(EffectFamily.CELESTIAL_RING, "Celestial ring", "Grants an invisible +4 Mining boost. While charged, 10% chance of an extra ore from rocks up to adamantite."));
		register(singles, ItemID.CELESTIAL_SIGNET, new SingleItemEffect(EffectFamily.CELESTIAL_RING, "Celestial signet", "Grants an invisible +4 Mining boost. While charged, 10% chance of an extra ore from rocks up to adamantite, granting experience for both. 10% chance to not use a charge on crystal equipment, except the blade of Saeldor and bow of Faerdhinen."));
		register(singles, ItemID.ELVEN_SIGNET, new SingleItemEffect(EffectFamily.ELVEN_SIGNET, "Elven signet", "10% chance to not use a charge on crystal equipment, except the blade of Saeldor and bow of Faerdhinen."));
		register(singles, ItemID.SKILLCAPE_RUNECRAFTING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Runecrafting cape", "Allows access to any runic altar without a talisman or tiara. Essence pouches do not degrade when filled."));
		register(singles, ItemID.SKILLCAPE_SLAYER, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Slayer cape", "10% chance to be offered your previous task when getting a new one from a Slayer master. Bypasses combat level requirements when receiving tasks."));
		register(singles, ItemID.SKILLCAPE_SMITHING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Smithing cape", "Increases the coal bag's capacity to 36 coal. Acts as goldsmith gauntlets."));
		register(singles, ItemID.SKILLCAPE_THIEVING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Thieving cape", "+10% chance of success when pickpocketing. Stacks with the Ardougne diary bonuses."));
		register(singles, ItemID.SKILLCAPE_WOODCUTTING, new SingleItemEffect(EffectFamily.CAPES_OF_ACCOMPLISHMENT, "Woodcutting cape", "+10% chance of a bird nest falling while chopping trees."));
		register(singles, ItemID.ANMA_30_REWARD, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Ava's attractor", "Recovers 60% of your fired ammunition."));
		register(singles, ItemID.ANMA_50_REWARD, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Ava's accumulator", "Recovers 72% of your fired ammunition."));
		register(singles, ItemID.SKILLCAPE_MAX_ANMA, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Accumulator max cape", "Recovers 72% of your fired ammunition."));
		register(singles, ItemID.AVAS_ASSEMBLER_BROKEN, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Ava's assembler", "Recovers 80% of your fired ammunition."));
		register(singles, ItemID.SKILLCAPE_MAX_ASSEMBLER, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Assembler max cape", "Recovers 80% of your fired ammunition."));
		register(singles, ItemID.AVAS_ASSEMBLER_MASORI_BROKEN, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Masori assembler", "Recovers 80% of your fired ammunition."));
		register(singles, ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI_BROKEN, new SingleItemEffect(EffectFamily.AVAS_DEVICES, "Masori assembler max cape", "Recovers 80% of your fired ammunition."));
		register(singles, ItemID.DIZANAS_QUIVER_BROKEN, new SingleItemEffect(EffectFamily.DIZANAS_QUIVER, "Dizana's quiver", "Increases ranged attack by 10 and ranged strength by 1. Only applies when using arrows and bolts."));
		register(singles, ItemID.DIZANAS_QUIVER_INFINITE_BROKEN, new SingleItemEffect(EffectFamily.DIZANAS_QUIVER, "Blessed Dizana's quiver", "Increases ranged attack by 10 and ranged strength by 1. Only applies when using arrows and bolts."));
		register(singles, ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN, new SingleItemEffect(EffectFamily.DIZANAS_QUIVER, "Dizana's max cape", "Increases ranged attack by 10 and ranged strength by 1. Only applies when using arrows and bolts."));

		SINGLE_ITEM_EFFECTS = Collections.unmodifiableMap(singles);
		SINGLE_ITEM_COUNT = registered;
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
