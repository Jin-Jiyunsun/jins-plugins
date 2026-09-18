package com.seteffects;

/**
 * One user-toggleable group of tracked effects - a set, a single item, or a family of near-identical
 * variants that read as one thing (all four Salve amulet tiers, all six revenant weapons, ...).
 * {@link #configKey} must match the {@code keyName} of the corresponding {@code @ConfigItem} in
 * {@link SetEffectDisplayConfig}; a unit test checks the two stay in sync.
 */
enum EffectFamily
{
	// Barrows
	AHRIMS("ahrims"),
	DHAROKS("dharoks"),
	GUTHANS("guthans"),
	KARILS("karils"),
	TORAGS("torags"),
	VERACS("veracs"),
	AMULET_OF_THE_DAMNED("amuletOfTheDamned"),

	// Armour sets
	JUSTICIAR("justiciar"),
	OBSIDIAN("obsidian"),
	INQUISITORS("inquisitors"),
	VIRTUS("virtus"),
	CRYSTAL_ARMOUR("crystalArmour"),
	VOID_KNIGHT("voidKnight"),
	SHAYZIEN("shayzien"),
	BLOOD_MOON("bloodMoon"),
	BLUE_MOON("blueMoon"),
	ECLIPSE_MOON("eclipseMoon"),
	VANILLA_ONLY_SETS("vanillaOnlySets"),
	SWAMPBARK("swampbark"),
	BLOODBARK("bloodbark"),

	// Weapons
	EMBERLIGHT("emberlight"),
	SILVERLIGHT("silverlight"),
	DARKLIGHT("darklight"),
	ARCLIGHT("arclight"),
	SCORCHING_BOW("scorchingBow"),
	TWISTED_BOW("twistedBow"),
	ZARYTE_CROSSBOW("zaryteCrossbow"),
	DRAGON_HUNTER_WEAPONS("dragonHunterWeapons"),
	REVENANT_WEAPONS("revenantWeapons"),
	STAVES_OF_THE_DEAD("stavesOfTheDead"),
	KODAI_WAND("kodaiWand"),
	SANGUINESTI_STAFF("sanguinestiStaff"),
	PURGING_STAFF("purgingStaff"),
	KERIS_PARTISANS("kerisPartisans"),
	WOLFBANE("wolfbane"),
	COLOSSAL_BLADE("colossalBlade"),
	LEAF_BLADED_BATTLEAXE("leafBladedBattleaxe"),
	OSMUMTENS_FANG("osmumtensFang"),
	TUMEKENS_SHADOW("tumekensShadow"),
	HALLOWFELL("hallowfell"),
	ANCIENT_SCEPTRES("ancientSceptres"),
	BONE_WEAPONS("boneWeapons"),
	BARRONITE_MACE("barroniteMace"),
	GRANITE_HAMMER("graniteHammer"),
	BRINE_SABRE("brineSabre"),
	SCYTHE_OF_VITUR("scytheOfVitur"),
	VAMPYRE_WEAPONS("vampyreWeapons"),
	HARPOONS("harpoons"),
	INFERNAL_TOOLS("infernalTools"),
	TWINFLAME_STAFF("twinflameStaff"),
	ENCHANTED_BOLTS("enchantedBolts"),

	// Shields
	ELYSIAN_SPIRIT_SHIELD("elysianSpiritShield"),
	SPECTRAL_SPIRIT_SHIELD("spectralSpiritShield"),
	DINHS_BULWARK("dinhsBulwark"),
	ANTI_DRAGON_SHIELD("antiDragonShield"),
	ELEMENTAL_SHIELD("elementalShield"),
	MIND_SHIELD("mindShield"),
	DRAGONFIRE_SHIELD("dragonfireShield"),
	ANCIENT_WYVERN_SHIELD("ancientWyvernShield"),
	DRAGONFIRE_WARD("dragonfireWard"),
	ABYSSAL_LANTERN("abyssalLantern"),
	ELEMENTAL_TOMES("elementalTomes"),
	MIRROR_SHIELDS("mirrorShields"),
	AQUANITE_HOPPER("aquaniteHopper"),
	GHOSTSPEAK("ghostspeak"),

	// Head and hands
	SLAYER_HELMET("slayerHelmet"),
	BLACK_MASK("blackMask"),
	SERPENTINE_HELM("serpentineHelm"),
	CHAOS_GAUNTLETS("chaosGauntlets"),
	REGEN_BRACELET("regenBracelet"),

	// Jewellery
	SALVE_AMULET("salveAmulet"),
	NECKLACE_OF_FAITH("necklaceOfFaith"),
	PHOENIX_NECKLACE("phoenixNecklace"),
	AMULET_OF_GLORY("amuletOfGlory"),
	AMULET_OF_AVARICE("amuletOfAvarice"),
	AMULET_OF_BLOOD_FURY("amuletOfBloodFury"),
	BERSERKER_NECKLACE("berserkerNecklace"),
	RING_OF_LIFE("ringOfLife"),
	RING_OF_WEALTH("ringOfWealth"),
	RING_OF_RECOIL("ringOfRecoil"),
	RING_OF_SUFFERING("ringOfSuffering"),
	BRIMSTONE_RING("brimstoneRing"),
	ELEMENTAL_AMULETS("elementalAmulets"),
	BONE_NECKLACES("boneNecklaces"),
	RING_OF_THE_GODS("ringOfTheGods"),
	CELESTIAL_RING("celestialRing"),
	LIGHTBEARER("lightbearer"),
	EFARITAYS_AID("efaritaysAid"),
	DODGY_NECKLACE("dodgyNecklace"),
	MEDALLION_OF_THE_DEEP("medallionOfTheDeep"),
	TORTUGAN_SHIELD("tortuganShield"),
	ECHO_BOOTS("echoBoots"),
	ARANEA_BOOTS("araneaBoots"),
	BRACELET_OF_ETHEREUM("braceletOfEthereum"),
	COOKING_GAUNTLETS("cookingGauntlets"),
	GLOVES_OF_SILENCE("glovesOfSilence"),
	GOLDSMITH_GAUNTLETS("goldsmithGauntlets"),
	ELVEN_SIGNET("elvenSignet"),

	// Skilling outfits
	GRACEFUL("graceful"),
	ANGLER_OUTFIT("anglerOutfit"),
	LUMBERJACK_OUTFIT("lumberjackOutfit"),
	PROSPECTOR_OUTFIT("prospectorOutfit"),
	FARMERS_OUTFIT("farmersOutfit"),
	PYROMANCER_OUTFIT("pyromancerOutfit"),
	CARPENTERS_OUTFIT("carpentersOutfit"),
	SMITHS_UNIFORM("smithsUniform"),
	GUILD_HUNTER_OUTFIT("guildHunterOutfit"),
	ROGUE_EQUIPMENT("rogueEquipment"),
	ZEALOTS_ROBES("zealotsRobes"),
	RAIMENTS_OF_THE_EYE("raimentsOfTheEye"),
	HUNTER_GEAR("hunterGear"),

	// Misc
	CIRCLET_OF_WATER("circletOfWater"),
	DIZANAS_QUIVER("dizanasQuiver"),
	AVAS_DEVICES("avasDevices"),
	CAPES_OF_ACCOMPLISHMENT("capesOfAccomplishment"),
	RADAS_BLESSING("radasBlessing");

	final String configKey;

	EffectFamily(String configKey)
	{
		this.configKey = configKey;
	}
}
