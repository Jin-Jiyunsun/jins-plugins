package com.trawlingplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShoalTest
{
	@Test
	public void headingArrowShowsWhileTheShoalSwims()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0});
		shoal.update(new double[]{2, 0});
		assertFalse(shoal.isHeadingArrowHidden());
	}

	@Test
	public void headingArrowHidesTheTickTheShoalStops()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0});
		shoal.update(new double[]{2, 0});
		shoal.update(new double[]{2, 0});
		assertTrue(shoal.isHeadingArrowHidden());
	}

	@Test
	public void headingArrowShowsTheTickTheShoalMovesOff()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0});
		shoal.update(new double[]{2, 0});
		for (int tick = 0; tick < 70; tick++)
		{
			shoal.update(new double[]{2, 0});
		}
		shoal.update(new double[]{4, 0});
		assertFalse(shoal.isHeadingArrowHidden());
	}

	@Test
	public void headingArrowStaysHiddenForAShoalFirstSeenSittingStill()
	{
		Shoal shoal = new Shoal(null);
		for (int tick = 0; tick < 200; tick++)
		{
			shoal.update(new double[]{5, 5});
			assertTrue("hidden on tick " + tick, shoal.isHeadingArrowHidden());
		}

		// Until it swims off.
		shoal.update(new double[]{7, 5});
		assertFalse(shoal.isHeadingArrowHidden());
	}

	@Test
	public void headingArrowFadesInOverATick()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0});
		shoal.update(new double[]{2, 0});

		// It starts invisible; the first frame just starts the clock.
		assertEquals(0, shoal.headingArrowOpacity(1000), 1e-9);
		assertEquals(0.5, shoal.headingArrowOpacity(1300), 1e-9);
		assertEquals(1, shoal.headingArrowOpacity(1600), 1e-9);
		assertEquals(1, shoal.headingArrowOpacity(5000), 1e-9);
	}

	@Test
	public void headingArrowFadesOutWhenTheShoalStops()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0});
		shoal.update(new double[]{2, 0});
		shoal.headingArrowOpacity(0);
		assertEquals(1, shoal.headingArrowOpacity(600), 1e-9);

		// Sits still on the next tick, so the arrow fades away.
		shoal.update(new double[]{2, 0});
		assertEquals(0.5, shoal.headingArrowOpacity(900), 1e-9);
		assertEquals(0, shoal.headingArrowOpacity(1200), 1e-9);
	}
}
