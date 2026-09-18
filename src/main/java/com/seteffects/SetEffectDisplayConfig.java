package com.seteffects;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SetEffectDisplayConfig.GROUP)
public interface SetEffectDisplayConfig extends Config
{
	String GROUP = "set-effect-display";

	@ConfigItem(
		keyName = "showSetEffectList",
		name = "Show set effect list",
		description = "Replace the Set Effect Bonus box with the full list of your worn gear's effects",
		position = 0
	)
	default boolean showSetEffectList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTooltips",
		name = "Show tooltips",
		description = "Show an item's effect when hovering it in the equipment stats screen",
		position = 1
	)
	default boolean showTooltips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "verbose",
		name = "Verbose",
		description = "List each worn piece's bonus (Inquisitor's, Virtus) instead of just the total",
		position = 2
	)
	default boolean verbose()
	{
		return true;
	}

	@ConfigSection(
		name = "Barrows",
		description = "Barrows sets and the Amulet of the Damned",
		position = 3,
		closedByDefault = true
	)
	String barrowsSection = "barrows";

	@ConfigSection(
		name = "Armour sets",
		description = "Multi-piece armour set bonuses",
		position = 4,
		closedByDefault = true
	)
	String armourSetsSection = "armourSets";

	@ConfigSection(
		name = "Weapons",
		description = "Weapon passive effects",
		position = 5,
		closedByDefault = true
	)
	String weaponsSection = "weapons";

	@ConfigSection(
		name = "Shields",
		description = "Shield passive effects",
		position = 6,
		closedByDefault = true
	)
	String shieldsSection = "shields";

	@ConfigSection(
		name = "Head and hands",
		description = "Headwear, gloves and bracelets",
		position = 7,
		closedByDefault = true
	)
	String headAndHandsSection = "headAndHands";

	@ConfigSection(
		name = "Jewellery",
		description = "Amulets, necklaces and rings",
		position = 8,
		closedByDefault = true
	)
	String jewellerySection = "jewellery";

	@ConfigSection(
		name = "Skilling outfits",
		description = "Skilling outfit bonuses",
		position = 9,
		closedByDefault = true
	)
	String skillingSection = "skilling";

	@ConfigSection(
		name = "Miscellaneous",
		description = "Other passive effects",
		position = 10,
		closedByDefault = true
	)
	String miscSection = "misc";

	@ConfigItem(
		keyName = "ahrims",
		name = "Ahrim's set",
		description = "Show the Ahrim's set effect",
		section = barrowsSection,
		position = 0
	)
	default boolean ahrims()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dharoks",
		name = "Dharok's set",
		description = "Show the Dharok's set effect",
		section = barrowsSection,
		position = 1
	)
	default boolean dharoks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guthans",
		name = "Guthan's set",
		description = "Show the Guthan's set effect",
		section = barrowsSection,
		position = 2
	)
	default boolean guthans()
	{
		return true;
	}

	@ConfigItem(
		keyName = "karils",
		name = "Karil's set",
		description = "Show the Karil's set effect",
		section = barrowsSection,
		position = 3
	)
	default boolean karils()
	{
		return true;
	}

	@ConfigItem(
		keyName = "torags",
		name = "Torag's set",
		description = "Show the Torag's set effect",
		section = barrowsSection,
		position = 4
	)
	default boolean torags()
	{
		return true;
	}

	@ConfigItem(
		keyName = "veracs",
		name = "Verac's set",
		description = "Show the Verac's set effect",
		section = barrowsSection,
		position = 5
	)
	default boolean veracs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "amuletOfTheDamned",
		name = "Amulet of the Damned",
		description = "Show the Amulet of the Damned effect",
		section = barrowsSection,
		position = 6
	)
	default boolean amuletOfTheDamned()
	{
		return true;
	}

	@ConfigItem(
		keyName = "justiciar",
		name = "Justiciar armour",
		description = "Show the Justiciar armour effect",
		section = armourSetsSection,
		position = 0
	)
	default boolean justiciar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "obsidian",
		name = "Obsidian armour",
		description = "Show the Obsidian armour effect",
		section = armourSetsSection,
		position = 1
	)
	default boolean obsidian()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inquisitors",
		name = "Inquisitor's armour",
		description = "Show the Inquisitor's armour effect",
		section = armourSetsSection,
		position = 2
	)
	default boolean inquisitors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "virtus",
		name = "Virtus robes",
		description = "Show the Virtus robes effect",
		section = armourSetsSection,
		position = 3
	)
	default boolean virtus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "crystalArmour",
		name = "Crystal armour",
		description = "Show the Crystal armour effect",
		section = armourSetsSection,
		position = 4
	)
	default boolean crystalArmour()
	{
		return true;
	}

	@ConfigItem(
		keyName = "voidKnight",
		name = "Void Knight",
		description = "Show the Void Knight effect",
		section = armourSetsSection,
		position = 5
	)
	default boolean voidKnight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shayzien",
		name = "Shayzien armour",
		description = "Show the Shayzien armour effect",
		section = armourSetsSection,
		position = 6
	)
	default boolean shayzien()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bloodMoon",
		name = "Blood moon armour",
		description = "Show the Blood moon armour effect",
		section = armourSetsSection,
		position = 7
	)
	default boolean bloodMoon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "blueMoon",
		name = "Blue moon armour",
		description = "Show the Blue moon armour effect",
		section = armourSetsSection,
		position = 8
	)
	default boolean blueMoon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eclipseMoon",
		name = "Eclipse moon armour",
		description = "Show the Eclipse moon armour effect",
		section = armourSetsSection,
		position = 9
	)
	default boolean eclipseMoon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "swampbark",
		name = "Swampbark armour",
		description = "Show the Swampbark armour effect",
		section = armourSetsSection,
		position = 10
	)
	default boolean swampbark()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bloodbark",
		name = "Bloodbark armour",
		description = "Show the Bloodbark armour effect",
		section = armourSetsSection,
		position = 11
	)
	default boolean bloodbark()
	{
		return true;
	}

	@ConfigItem(
		keyName = "emberlight",
		name = "Emberlight",
		description = "Show the Emberlight effect",
		section = weaponsSection,
		position = 0
	)
	default boolean emberlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "silverlight",
		name = "Silverlight",
		description = "Show the Silverlight effect",
		section = weaponsSection,
		position = 1
	)
	default boolean silverlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "darklight",
		name = "Darklight",
		description = "Show the Darklight effect",
		section = weaponsSection,
		position = 2
	)
	default boolean darklight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "arclight",
		name = "Arclight",
		description = "Show the Arclight effect",
		section = weaponsSection,
		position = 3
	)
	default boolean arclight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "scorchingBow",
		name = "Scorching bow",
		description = "Show the Scorching bow effect",
		section = weaponsSection,
		position = 4
	)
	default boolean scorchingBow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "twistedBow",
		name = "Twisted bow",
		description = "Show the Twisted bow effect",
		section = weaponsSection,
		position = 5
	)
	default boolean twistedBow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "zaryteCrossbow",
		name = "Zaryte crossbow",
		description = "Show the Zaryte crossbow effect",
		section = weaponsSection,
		position = 6
	)
	default boolean zaryteCrossbow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dragonHunterWeapons",
		name = "Dragon hunter weapons",
		description = "Show the Dragon hunter weapons effect",
		section = weaponsSection,
		position = 7
	)
	default boolean dragonHunterWeapons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "revenantWeapons",
		name = "Revenant weapons",
		description = "Show the Revenant weapons effect",
		section = weaponsSection,
		position = 8
	)
	default boolean revenantWeapons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "stavesOfTheDead",
		name = "Staves of the dead",
		description = "Show the Staves of the dead effect",
		section = weaponsSection,
		position = 9
	)
	default boolean stavesOfTheDead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "kodaiWand",
		name = "Kodai wand",
		description = "Show the Kodai wand effect",
		section = weaponsSection,
		position = 10
	)
	default boolean kodaiWand()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sanguinestiStaff",
		name = "Sanguinesti staff",
		description = "Show the Sanguinesti staff effect",
		section = weaponsSection,
		position = 11
	)
	default boolean sanguinestiStaff()
	{
		return true;
	}

	@ConfigItem(
		keyName = "purgingStaff",
		name = "Purging staff",
		description = "Show the Purging staff effect",
		section = weaponsSection,
		position = 12
	)
	default boolean purgingStaff()
	{
		return true;
	}

	@ConfigItem(
		keyName = "kerisPartisans",
		name = "Keris partisans",
		description = "Show the Keris partisans effect",
		section = weaponsSection,
		position = 13
	)
	default boolean kerisPartisans()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wolfbane",
		name = "Wolfbane",
		description = "Show the Wolfbane effect",
		section = weaponsSection,
		position = 14
	)
	default boolean wolfbane()
	{
		return true;
	}

	@ConfigItem(
		keyName = "colossalBlade",
		name = "Colossal blade",
		description = "Show the Colossal blade effect",
		section = weaponsSection,
		position = 15
	)
	default boolean colossalBlade()
	{
		return true;
	}

	@ConfigItem(
		keyName = "leafBladedBattleaxe",
		name = "Leaf-bladed battleaxe",
		description = "Show the Leaf-bladed battleaxe effect",
		section = weaponsSection,
		position = 16
	)
	default boolean leafBladedBattleaxe()
	{
		return true;
	}

	@ConfigItem(
		keyName = "osmumtensFang",
		name = "Osmumten's fang",
		description = "Show the Osmumten's fang effect",
		section = weaponsSection,
		position = 17
	)
	default boolean osmumtensFang()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tumekensShadow",
		name = "Tumeken's shadow",
		description = "Show the Tumeken's shadow effect",
		section = weaponsSection,
		position = 18
	)
	default boolean tumekensShadow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hallowfell",
		name = "Hallowfell",
		description = "Show the Hallowfell effect",
		section = weaponsSection,
		position = 19
	)
	default boolean hallowfell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ancientSceptres",
		name = "Ancient sceptres",
		description = "Show the Ancient sceptres effect",
		section = weaponsSection,
		position = 20
	)
	default boolean ancientSceptres()
	{
		return true;
	}

	@ConfigItem(
		keyName = "boneWeapons",
		name = "Bone weapons",
		description = "Show the Bone weapons effect",
		section = weaponsSection,
		position = 21
	)
	default boolean boneWeapons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "barroniteMace",
		name = "Barronite mace",
		description = "Show the Barronite mace effect",
		section = weaponsSection,
		position = 22
	)
	default boolean barroniteMace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "graniteHammer",
		name = "Granite hammer",
		description = "Show the Granite hammer effect",
		section = weaponsSection,
		position = 23
	)
	default boolean graniteHammer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enchantedBolts",
		name = "Enchanted bolts",
		description = "Show the enchanted bolt effects",
		section = weaponsSection,
		position = 24
	)
	default boolean enchantedBolts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "elysianSpiritShield",
		name = "Elysian spirit shield",
		description = "Show the Elysian spirit shield effect",
		section = shieldsSection,
		position = 0
	)
	default boolean elysianSpiritShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "spectralSpiritShield",
		name = "Spectral spirit shield",
		description = "Show the Spectral spirit shield effect",
		section = shieldsSection,
		position = 1
	)
	default boolean spectralSpiritShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dinhsBulwark",
		name = "Dinh's bulwark",
		description = "Show the Dinh's bulwark effect",
		section = shieldsSection,
		position = 2
	)
	default boolean dinhsBulwark()
	{
		return true;
	}

	@ConfigItem(
		keyName = "antiDragonShield",
		name = "Anti-dragon shield",
		description = "Show the Anti-dragon shield effect",
		section = shieldsSection,
		position = 3
	)
	default boolean antiDragonShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "elementalShield",
		name = "Elemental shield",
		description = "Show the Elemental shield effect",
		section = shieldsSection,
		position = 4
	)
	default boolean elementalShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mindShield",
		name = "Mind shield",
		description = "Show the Mind shield effect",
		section = shieldsSection,
		position = 5
	)
	default boolean mindShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dragonfireShield",
		name = "Dragonfire shield",
		description = "Show the Dragonfire shield effect",
		section = shieldsSection,
		position = 6
	)
	default boolean dragonfireShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ancientWyvernShield",
		name = "Ancient wyvern shield",
		description = "Show the Ancient wyvern shield effect",
		section = shieldsSection,
		position = 7
	)
	default boolean ancientWyvernShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dragonfireWard",
		name = "Dragonfire ward",
		description = "Show the Dragonfire ward effect",
		section = shieldsSection,
		position = 8
	)
	default boolean dragonfireWard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "slayerHelmet",
		name = "Slayer helmet",
		description = "Show the Slayer helmet effect",
		section = headAndHandsSection,
		position = 0
	)
	default boolean slayerHelmet()
	{
		return true;
	}

	@ConfigItem(
		keyName = "blackMask",
		name = "Black mask",
		description = "Show the Black mask effect",
		section = headAndHandsSection,
		position = 1
	)
	default boolean blackMask()
	{
		return true;
	}

	@ConfigItem(
		keyName = "serpentineHelm",
		name = "Serpentine helm",
		description = "Show the Serpentine helm effect",
		section = headAndHandsSection,
		position = 2
	)
	default boolean serpentineHelm()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chaosGauntlets",
		name = "Chaos gauntlets",
		description = "Show the Chaos gauntlets effect",
		section = headAndHandsSection,
		position = 3
	)
	default boolean chaosGauntlets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "regenBracelet",
		name = "Regen bracelet",
		description = "Show the Regen bracelet effect",
		section = headAndHandsSection,
		position = 4
	)
	default boolean regenBracelet()
	{
		return true;
	}

	@ConfigItem(
		keyName = "salveAmulet",
		name = "Salve amulet",
		description = "Show the Salve amulet effect",
		section = jewellerySection,
		position = 0
	)
	default boolean salveAmulet()
	{
		return true;
	}

	@ConfigItem(
		keyName = "necklaceOfFaith",
		name = "Necklace of faith",
		description = "Show the Necklace of faith effect",
		section = jewellerySection,
		position = 1
	)
	default boolean necklaceOfFaith()
	{
		return true;
	}

	@ConfigItem(
		keyName = "phoenixNecklace",
		name = "Phoenix necklace",
		description = "Show the Phoenix necklace effect",
		section = jewellerySection,
		position = 2
	)
	default boolean phoenixNecklace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "amuletOfGlory",
		name = "Amulet of glory",
		description = "Show the Amulet of glory effect",
		section = jewellerySection,
		position = 3
	)
	default boolean amuletOfGlory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "amuletOfAvarice",
		name = "Amulet of avarice",
		description = "Show the Amulet of avarice effect",
		section = jewellerySection,
		position = 4
	)
	default boolean amuletOfAvarice()
	{
		return true;
	}

	@ConfigItem(
		keyName = "amuletOfBloodFury",
		name = "Amulet of blood fury",
		description = "Show the Amulet of blood fury effect",
		section = jewellerySection,
		position = 5
	)
	default boolean amuletOfBloodFury()
	{
		return true;
	}

	@ConfigItem(
		keyName = "berserkerNecklace",
		name = "Berserker necklace",
		description = "Show the Berserker necklace effect",
		section = jewellerySection,
		position = 6
	)
	default boolean berserkerNecklace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ringOfLife",
		name = "Ring of life",
		description = "Show the Ring of life effect",
		section = jewellerySection,
		position = 7
	)
	default boolean ringOfLife()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ringOfWealth",
		name = "Ring of wealth",
		description = "Show the Ring of wealth effect",
		section = jewellerySection,
		position = 8
	)
	default boolean ringOfWealth()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ringOfRecoil",
		name = "Ring of recoil",
		description = "Show the Ring of recoil effect",
		section = jewellerySection,
		position = 9
	)
	default boolean ringOfRecoil()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ringOfSuffering",
		name = "Ring of suffering",
		description = "Show the Ring of suffering effect",
		section = jewellerySection,
		position = 10
	)
	default boolean ringOfSuffering()
	{
		return true;
	}

	@ConfigItem(
		keyName = "brimstoneRing",
		name = "Brimstone ring",
		description = "Show the Brimstone ring effect",
		section = jewellerySection,
		position = 11
	)
	default boolean brimstoneRing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "elementalAmulets",
		name = "Elemental amulets",
		description = "Show the amulets of air, water, earth and fire, and the Elemental amulet",
		section = jewellerySection,
		position = 12
	)
	default boolean elementalAmulets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "boneNecklaces",
		name = "Bone necklaces",
		description = "Show the Dragonbone necklace and Bonecrusher necklace effects",
		section = jewellerySection,
		position = 13
	)
	default boolean boneNecklaces()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ringOfTheGods",
		name = "Ring of the gods",
		description = "Show the imbued Ring of the gods effect",
		section = jewellerySection,
		position = 14
	)
	default boolean ringOfTheGods()
	{
		return true;
	}

	@ConfigItem(
		keyName = "radasBlessing",
		name = "Rada's blessing",
		description = "Show the Rada's blessing effect",
		section = miscSection,
		position = 1
	)
	default boolean radasBlessing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "circletOfWater",
		name = "Circlet of water",
		description = "Show the Circlet of water effect",
		section = miscSection,
		position = 0
	)
	default boolean circletOfWater()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dizanasQuiver",
		name = "Dizana's quiver",
		description = "Show the Dizana's quiver, blessed quiver and max cape effect",
		section = miscSection,
		position = 2
	)
	default boolean dizanasQuiver()
	{
		return true;
	}

	@ConfigItem(
		keyName = "graceful",
		name = "Graceful outfit",
		description = "Show the Graceful outfit effect",
		section = skillingSection,
		position = 0
	)
	default boolean graceful()
	{
		return true;
	}

	@ConfigItem(
		keyName = "anglerOutfit",
		name = "Angler outfit",
		description = "Show the Angler outfit effect",
		section = skillingSection,
		position = 1
	)
	default boolean anglerOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lumberjackOutfit",
		name = "Lumberjack outfit",
		description = "Show the Lumberjack outfit effect",
		section = skillingSection,
		position = 3
	)
	default boolean lumberjackOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prospectorOutfit",
		name = "Prospector outfit",
		description = "Show the Prospector outfit effect",
		section = skillingSection,
		position = 4
	)
	default boolean prospectorOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "farmersOutfit",
		name = "Farmer's outfit",
		description = "Show the Farmer's outfit effect",
		section = skillingSection,
		position = 5
	)
	default boolean farmersOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pyromancerOutfit",
		name = "Pyromancer outfit",
		description = "Show the Pyromancer outfit effect",
		section = skillingSection,
		position = 6
	)
	default boolean pyromancerOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "carpentersOutfit",
		name = "Carpenter's outfit",
		description = "Show the Carpenter's outfit effect",
		section = skillingSection,
		position = 7
	)
	default boolean carpentersOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "smithsUniform",
		name = "Smith's uniform",
		description = "Show the Smith's uniform effect",
		section = skillingSection,
		position = 8
	)
	default boolean smithsUniform()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guildHunterOutfit",
		name = "Guild hunter outfit",
		description = "Show the Guild hunter outfit effect",
		section = skillingSection,
		position = 9
	)
	default boolean guildHunterOutfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rogueEquipment",
		name = "Rogue equipment",
		description = "Show the Rogue equipment effect",
		section = skillingSection,
		position = 10
	)
	default boolean rogueEquipment()
	{
		return true;
	}

	@ConfigItem(
		keyName = "zealotsRobes",
		name = "Zealot's robes",
		description = "Show the Zealot's robes effect",
		section = skillingSection,
		position = 11
	)
	default boolean zealotsRobes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "raimentsOfTheEye",
		name = "Raiments of the Eye",
		description = "Show the Raiments of the Eye effect",
		section = skillingSection,
		position = 12
	)
	default boolean raimentsOfTheEye()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hunterGear",
		name = "Hunter gear",
		description = "Show the Larupia, Graahk and Kyatt hunter gear effects",
		section = skillingSection,
		position = 13
	)
	default boolean hunterGear()
	{
		return true;
	}
}
