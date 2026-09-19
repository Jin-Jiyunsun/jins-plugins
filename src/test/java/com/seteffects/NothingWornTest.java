package com.seteffects;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NothingWornTest
{
	private static final DiaryChecks DIARIES = new DiaryChecks(() -> false, () -> false, () -> false);

	@Test
	public void nothingWornDescribesToAnEmptyList()
	{
		List<EffectLine> lines = EquippedEffects.describeEquipment(NothingWorn.INSTANCE, family -> true, true, DIARIES,
			Collections.emptyList(), () -> false);
		assertTrue(lines.isEmpty());
	}

	@Test
	public void nothingWornStillShowsTheWarmClothingTallyWhenWanted()
	{
		List<EffectLine> lines = EquippedEffects.describeEquipment(NothingWorn.INSTANCE, family -> true, false, DIARIES,
			Collections.emptyList(), () -> true);
		assertEquals(1, lines.size());
		assertEquals("Warm clothing", lines.get(0).name);
	}

	@Test
	public void hoveringAnItemWithNothingWornStillDescribesIt()
	{
		List<EffectLine> lines = EquippedEffects.describeItem(NothingWorn.INSTANCE, net.runelite.api.gameval.ItemID.BARROWS_AHRIM_HEAD,
			family -> true, false, DIARIES);
		assertEquals(1, lines.size());
		assertEquals("Ahrim's set", lines.get(0).name);
	}
}
