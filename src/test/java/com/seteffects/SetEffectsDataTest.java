package com.seteffects;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SetEffectsDataTest
{
	@Test
	public void noTwoSingleItemsCollapseOntoTheSameKey()
	{
		// Entries are stored under their variation-mapped id; two whose chains merge would overwrite each other
		assertEquals(SetEffectsData.SINGLE_ITEM_COUNT, SetEffectsData.SINGLE_ITEM_EFFECTS.size());
	}

	@Test
	public void everyKeyIsItsOwnVariationChainBase()
	{
		for (int key : SetEffectsData.SINGLE_ITEM_EFFECTS.keySet())
		{
			assertEquals("key " + key, key, ItemVariationMapping.map(key));
		}
	}

	@Test
	public void chargedItemsWhoseOwnIdIsNotTheirChainBaseStillResolve()
	{
		// These were registered under their charged id, which is not the chain base the lookup uses, so they
		// never showed. Both the charged and the uncharged id must find the entry now.
		Object[][] cases = {
			{ItemID.WILD_CAVE_BOW_CHARGED, ItemID.WILD_CAVE_BOW_UNCHARGED, "Craw's bow"},
			{ItemID.WILD_CAVE_WEBWEAVER_CHARGED, ItemID.WILD_CAVE_WEBWEAVER_UNCHARGED, "Webweaver bow"},
			{ItemID.WILD_CAVE_SCEPTRE_CHARGED, ItemID.WILD_CAVE_SCEPTRE_UNCHARGED, "Thammaron's sceptre"},
			{ItemID.WILD_CAVE_CHAINMACE_CHARGED, ItemID.WILD_CAVE_CHAINMACE_UNCHARGED, "Viggora's chainmace"},
			{ItemID.TOXIC_SOTD_CHARGED, ItemID.TOXIC_SOTD, "Toxic staff of the dead"},
			{ItemID.BARRONITE_MACE, ItemID.BARRONITE_MACE_BROKEN, "Barronite mace"},
			{ItemID.ANCIENT_SCEPTRE_BLOOD, ItemID.ANCIENT_SCEPTRE_BLOOD_BROKEN, "Blood ancient sceptre"},
			{ItemID.ANCIENT_SCEPTRE_ICE, ItemID.ANCIENT_SCEPTRE_ICE_BROKEN, "Ice ancient sceptre"},
			{ItemID.ANCIENT_SCEPTRE_SMOKE, ItemID.ANCIENT_SCEPTRE_SMOKE_BROKEN, "Smoke ancient sceptre"},
			{ItemID.ANCIENT_SCEPTRE_SHADOW, ItemID.ANCIENT_SCEPTRE_SHADOW_BROKEN, "Shadow ancient sceptre"}};
		for (Object[] c : cases)
		{
			for (int i = 0; i < 2; i++)
			{
				SingleItemEffect effect = SetEffectsData.SINGLE_ITEM_EFFECTS.get(ItemVariationMapping.map((Integer) c[i]));
				assertNotNull(c[2] + " (item " + c[i] + ")", effect);
				assertEquals(c[2], effect.describe().name);
			}
		}
	}
}
