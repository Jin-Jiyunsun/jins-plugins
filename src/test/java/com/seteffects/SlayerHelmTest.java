package com.seteffects;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every Slayer helmet id in the game cache (plain and imbued, all recolours including the vampyric,
 * Oathplate and Radiant ones) must be recognised, and told apart correctly - a missing id means the
 * helmet silently shows no effect.
 */
public class SlayerHelmTest
{
	private static final int[] REGULAR = {
		11864, 19639, 19643, 19647, 21264, 21888, 23073, 24370, 25898, 25904, 25910, 29816, 33066, 33338, 33340};

	private static final int[] IMBUED = {
		11865, 19641, 19645, 19649, 21266, 21890, 23075, 24444,
		25177, 25179, 25181, 25183, 25185, 25187, 25189, 25191,
		25900, 25902, 25906, 25908, 25912, 25914,
		26674, 26675, 26676, 26677, 26678, 26679, 26680, 26681, 26682, 26683, 26684,
		29818, 29820, 29822, 33068, 33070, 33072,
		33439, 33441, 33443, 33445, 33447, 33449};

	@Test
	public void everyRegularHelmetIsRecognisedAsRegular()
	{
		for (int id : REGULAR)
		{
			assertTrue("not recognised: " + id, SlayerHelm.isSlayerHelm(id));
			assertEquals("wrong tier for " + id, "Slayer helmet", SlayerHelm.describe(id).name);
		}
	}

	@Test
	public void everyImbuedHelmetIsRecognisedAsImbued()
	{
		for (int id : IMBUED)
		{
			assertTrue("not recognised: " + id, SlayerHelm.isSlayerHelm(id));
			assertEquals("wrong tier for " + id, "Slayer helmet (i)", SlayerHelm.describe(id).name);
		}
	}
}
