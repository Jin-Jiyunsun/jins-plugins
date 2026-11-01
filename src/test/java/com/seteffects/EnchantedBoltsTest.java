package com.seteffects;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnchantedBoltsTest
{
	@Test
	public void unenchantedAscensionBoltsAreNotMatched()
	{
		// They head the same variation chain as the enchanted ones
		assertTrue(EnchantedBolts.isEnchantedBolt(ItemID.ASCENSION_BOLTS_DIAMOND_ENCHANTED));
		assertTrue(EnchantedBolts.isEnchantedBolt(ItemID.ASCENSION_BOLTS_ONYX_ENCHANTED));
		assertFalse(EnchantedBolts.isEnchantedBolt(ItemID.ASCENSION_BOLTS_DIAMOND_UNENCHANTED));
		assertFalse(EnchantedBolts.isEnchantedBolt(ItemID.ASCENSION_BOLTS_ONYX_UNENCHANTED));
		assertFalse(EnchantedBolts.isEnchantedBolt(ItemID.ASCENSION_BOLTS));
	}

	@Test
	public void diaryChancesKeepTheirHundredths()
	{
		assertEquals("5.5% chance (2.75% in PvP) to ignore the target's Defence and raise max hit by 15%.",
			EnchantedBolts.describe(ItemID.ASCENSION_BOLTS_DIAMOND_ENCHANTED, () -> true).effect);
		assertEquals("6.05% chance (5.5% in PvP) to deal 20% extra damage that heals you for 25% of the damage dealt. Not effective against the undead.",
			EnchantedBolts.describe(ItemID.ASCENSION_BOLTS_ONYX_ENCHANTED, () -> true).effect);
		assertEquals("5% chance (2.5% in PvP) to ignore the target's Defence and raise max hit by 15%.",
			EnchantedBolts.describe(ItemID.ASCENSION_BOLTS_DIAMOND_ENCHANTED, () -> false).effect);
	}

	@Test
	public void regularBoltTextIsUnchanged()
	{
		assertEquals("11% chance (10% in PvP) to deal 20% extra damage that heals you for 25% of the damage dealt. Not effective against the undead.",
			EnchantedBolts.describe(ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, () -> false).effect);
		assertEquals("12.1% chance (11% in PvP) to deal 20% extra damage that heals you for 25% of the damage dealt. Not effective against the undead.",
			EnchantedBolts.describe(ItemID.DRAGON_BOLTS_ENCHANTED_ONYX, () -> true).effect);
		assertEquals("60.5% chance (59.4% in PvP) to poison the target for 5 damage.",
			EnchantedBolts.describe(ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD_ENCHANTED, () -> true).effect);
	}
}
