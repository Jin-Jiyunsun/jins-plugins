package com.seteffects;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrystalArmourTest
{
	@Test
	public void activeCrystalPiecesAndTheirColoursCount()
	{
		// The ids on the wiki's crystal helm, crystal body and crystal legs pages
		assertEquals(23971, ItemID.CRYSTAL_HELMET);
		assertEquals(23975, ItemID.CRYSTAL_CHESTPLATE);
		assertEquals(23979, ItemID.CRYSTAL_PLATELEGS);

		assertTrue(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_HELMET));
		assertTrue(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_CHESTPLATE));
		assertTrue(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_PLATELEGS));
		assertTrue(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_HELMET_HEFIN));
		assertTrue(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_CHESTPLATE_AMLODD));
		assertTrue(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_PLATELEGS_DEADMAN));
	}

	@Test
	public void inactivePiecesDoNotCount()
	{
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_HELMET_INACTIVE));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_CHESTPLATE_INACTIVE));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_PLATELEGS_INACTIVE));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.CRYSTAL_HELMET_INACTIVE_HEFIN));
	}

	@Test
	public void gauntletMinigamePiecesDoNotCount()
	{
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.GAUNTLET_HELMET_T1));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.GAUNTLET_HELMET_T3));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.GAUNTLET_CHESTPLATE_T2));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.GAUNTLET_PLATELEGS_T1));
		assertFalse(PerPieceSets.CrystalArmour.isCrystalItem(ItemID.GAUNTLET_HELMET_T1_HM));
	}

	@Test
	public void everyCountedIdIsInTheCrystalArmourVariationChain()
	{
		// Guards against a wrong constant slipping in: each counted id must map to the same chain as
		// its slot's real crystal piece
		int[][] pieces = {
			{ItemID.CRYSTAL_HELMET, ItemID.CRYSTAL_HELMET_HEFIN, ItemID.CRYSTAL_HELMET_ITHELL, ItemID.CRYSTAL_HELMET_IORWERTH,
				ItemID.CRYSTAL_HELMET_TRAHAEARN, ItemID.CRYSTAL_HELMET_CADARN, ItemID.CRYSTAL_HELMET_CRWYS, ItemID.CRYSTAL_HELMET_AMLODD,
				ItemID.CRYSTAL_HELMET_DEADMAN},
			{ItemID.CRYSTAL_CHESTPLATE, ItemID.CRYSTAL_CHESTPLATE_HEFIN, ItemID.CRYSTAL_CHESTPLATE_ITHELL, ItemID.CRYSTAL_CHESTPLATE_IORWERTH,
				ItemID.CRYSTAL_CHESTPLATE_TRAHAEARN, ItemID.CRYSTAL_CHESTPLATE_CADARN, ItemID.CRYSTAL_CHESTPLATE_CRWYS,
				ItemID.CRYSTAL_CHESTPLATE_AMLODD, ItemID.CRYSTAL_CHESTPLATE_DEADMAN},
			{ItemID.CRYSTAL_PLATELEGS, ItemID.CRYSTAL_PLATELEGS_HEFIN, ItemID.CRYSTAL_PLATELEGS_ITHELL, ItemID.CRYSTAL_PLATELEGS_IORWERTH,
				ItemID.CRYSTAL_PLATELEGS_TRAHAEARN, ItemID.CRYSTAL_PLATELEGS_CADARN, ItemID.CRYSTAL_PLATELEGS_CRWYS,
				ItemID.CRYSTAL_PLATELEGS_AMLODD, ItemID.CRYSTAL_PLATELEGS_DEADMAN}};
		for (int[] slot : pieces)
		{
			int chain = ItemVariationMapping.map(slot[0]);
			for (int id : slot)
			{
				assertEquals("item " + id, chain, ItemVariationMapping.map(id));
				assertTrue("item " + id, PerPieceSets.CrystalArmour.isCrystalItem(id));
			}
		}
	}
}
