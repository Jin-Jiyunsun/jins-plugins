package com.seteffects;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WarmClothingTest
{
	// Items the wiki's Warmth page says do NOT count as warm, despite looking like they should
	private static final int[] NOT_WARM = {
		ItemID.ABYSSAL_LANTERN,
		ItemID.ABYSSAL_LANTERN_BLISTERWOOD,
		ItemID.ABYSSAL_LANTERN_MAGIC,
		ItemID.ABYSSAL_LANTERN_MAPLE,
		ItemID.ABYSSAL_LANTERN_NORMAL,
		ItemID.ABYSSAL_LANTERN_NORMAL_BLUE,
		ItemID.ABYSSAL_LANTERN_NORMAL_GREEN,
		ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE,
		ItemID.ABYSSAL_LANTERN_NORMAL_RED,
		ItemID.ABYSSAL_LANTERN_NORMAL_WHITE,
		ItemID.ABYSSAL_LANTERN_OAK,
		ItemID.ABYSSAL_LANTERN_REDWOOD,
		ItemID.ABYSSAL_LANTERN_WILLOW,
		ItemID.ABYSSAL_LANTERN_YEW,
		ItemID.BONE_CLAWS,
		ItemID.BUNNYEARS,
		ItemID.BURNING_AMULET_1,
		ItemID.BURNING_AMULET_2,
		ItemID.BURNING_AMULET_3,
		ItemID.BURNING_AMULET_4,
		ItemID.BURNING_AMULET_5,
		ItemID.DEVILS_ELEMENT,
		ItemID.EMBERLIGHT,
		ItemID.HAT_OF_THE_EYE,
		ItemID.HAT_OF_THE_EYE_BLUE,
		ItemID.HAT_OF_THE_EYE_GREEN,
		ItemID.HAT_OF_THE_EYE_RED,
		ItemID.HUNTING_LIGHTER_CAPE,
		ItemID.HUNTING_LIGHTER_CAPE_WORN,
		ItemID.HUNTING_LIGHT_CAPE,
		ItemID.HUNTING_LIGHT_CAPE_WORN,
		ItemID.HUNTING_STRUNG_RABBIT_FOOT,
		ItemID.LEAGUE_4_TORCH,
		ItemID.LEAGUE_RELIC_AGILITY_BOOTS,
		ItemID.RAEDWALD_HELM,
		ItemID.RAMBLE_LUMBERJACK_BOOTS,
		ItemID.RAMBLE_LUMBERJACK_LEGS,
		ItemID.RAMBLE_LUMBERJACK_TOP,
		ItemID.SCORCHING_BOW,
		ItemID.SERPENTINE_HELM_CHARGED_RED,
		ItemID.SERPENTINE_HELM_RED,
		ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT,
		ItemID.TOXIC_BLOWPIPE_ORNAMENT,
		ItemID.XMAS17_WOM_HAT,
		ItemID.XMAS25_BEER_BELLY_SWEATER,
		ItemID.XMAS25_CONTEST_JUMPER,
		ItemID.OBSIDIAN_HELMET,
		ItemID.OBSIDIAN_PLATEBODY,
		ItemID.OBSIDIAN_PLATELEGS,
		ItemID.LEAGUE_4_RELIC_HUNTER_HAT_T1,
		ItemID.LEAGUE_4_RELIC_HUNTER_TOP_T1,
		ItemID.LEAGUE_4_RELIC_HUNTER_LEGS_T1,
		ItemID.LEAGUE_4_RELIC_HUNTER_BOOTS_T1,
		ItemID.LEAGUE_4_RELIC_HUNTER_HAT_T2,
		ItemID.LEAGUE_4_RELIC_HUNTER_TOP_T2,
		ItemID.LEAGUE_4_RELIC_HUNTER_LEGS_T2,
		ItemID.LEAGUE_4_RELIC_HUNTER_BOOTS_T2,
		ItemID.LEAGUE_4_RELIC_HUNTER_HAT_T3,
		ItemID.LEAGUE_4_RELIC_HUNTER_TOP_T3,
		ItemID.LEAGUE_4_RELIC_HUNTER_LEGS_T3,
		ItemID.LEAGUE_4_RELIC_HUNTER_BOOTS_T3};

	@Test
	public void itemsTheWikiSaysAreNotWarmAreNotCounted()
	{
		for (int id : NOT_WARM)
		{
			assertFalse("item " + id + " is on the wiki's non-warm list", WarmClothing.isWarm(id));
		}
	}

	@Test
	public void listedItemsAreCounted()
	{
		assertTrue(WarmClothing.isWarm(ItemID.SANTA_HAT));
		assertTrue(WarmClothing.isWarm(ItemID.PYROMANCER_BOOTS));
		assertTrue(WarmClothing.isWarm(ItemID.TZHAAR_CAPE_FIRE));
		assertTrue(WarmClothing.isWarm(ItemID.SKILLCAPE_MAX));
		assertTrue(WarmClothing.isWarm(ItemID.RAMBLE_LUMBERJACK_HAT));
	}

	@Test
	public void slayerHelmetsAreCountedButThePlainAbyssalWhipIsNot()
	{
		assertTrue(WarmClothing.isWarm(ItemID.SLAYER_HELM));
		assertTrue(WarmClothing.isWarm(ItemID.SLAYER_HELM_I));
		assertTrue(WarmClothing.isWarm(ItemID.ABYSSAL_WHIP_LAVA));
		assertFalse(WarmClothing.isWarm(ItemID.ABYSSAL_WHIP));
	}
}
