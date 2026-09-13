package com.trawlingplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShoalTest
{
	@Test
	public void headingArrowShowsTheTickTheShoalMovesOff()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0}, null);
		shoal.update(new double[]{2, 0}, null);
		for (int tick = 0; tick < 70; tick++)
		{
			shoal.update(new double[]{2, 0}, null);
		}
		assertEquals(0, shoal.headingArrowOpacity(0), 1e-9);
		assertEquals(0, shoal.headingArrowOpacity(600), 1e-9);

		shoal.update(new double[]{4, 0}, null);
		assertEquals(1, shoal.headingArrowOpacity(1200), 1e-9);
	}

	@Test
	public void headingArrowStaysHiddenForAShoalFirstSeenSittingStill()
	{
		Shoal shoal = new Shoal(null);
		for (int tick = 0; tick < 200; tick++)
		{
			shoal.update(new double[]{5, 5}, null);
			assertEquals("opacity on tick " + tick, 0, shoal.headingArrowOpacity(tick * 600L), 1e-9);
		}

		// Until it swims off.
		shoal.update(new double[]{7, 5}, null);
		assertEquals(1, shoal.headingArrowOpacity(200 * 600L), 1e-9);
	}

	@Test
	public void headingArrowFadesInOverATick()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0}, null);
		shoal.update(new double[]{2, 0}, null);

		// It starts invisible; the first frame just starts the clock.
		assertEquals(0, shoal.headingArrowOpacity(1000), 1e-9);
		assertEquals(0.5, shoal.headingArrowOpacity(1300), 1e-9);
		assertEquals(1, shoal.headingArrowOpacity(1600), 1e-9);
		assertEquals(1, shoal.headingArrowOpacity(5000), 1e-9);
	}

	@Test
	public void stoppedTheTickTheDestinationStopsChanging()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0}, new double[]{1, 0});
		shoal.update(new double[]{0.5, 0}, new double[]{2, 0});
		assertFalse(shoal.stopped());

		// Still gliding towards its last destination, but no new one was sent, so it has stopped.
		shoal.update(new double[]{1.5, 0}, new double[]{2, 0});
		assertTrue(shoal.stopped());

		// A new destination means it has set off, before it has visibly moved.
		shoal.update(new double[]{2, 0}, new double[]{3, 0});
		assertFalse(shoal.stopped());
	}

	@Test
	public void headingArrowFadesOutWhenTheShoalStops()
	{
		Shoal shoal = new Shoal(null);
		shoal.update(new double[]{0, 0}, null);
		shoal.update(new double[]{2, 0}, null);
		shoal.headingArrowOpacity(0);
		assertEquals(1, shoal.headingArrowOpacity(600), 1e-9);

		// Sits still on the next tick, so the arrow fades away.
		shoal.update(new double[]{2, 0}, null);
		assertEquals(0.5, shoal.headingArrowOpacity(900), 1e-9);
		assertEquals(0, shoal.headingArrowOpacity(1200), 1e-9);
	}

	@Test
	public void routeDrawsOutToANewNextStopOverTheAnimationDuration()
	{
		Shoal shoal = new Shoal(null);
		shoal.headFor(2, 1000);
		assertEquals(0, shoal.routeReveal(1000, 7000), 1e-9);
		// Waits half a second for the shoal to settle in first.
		assertFalse(shoal.revealStarted(1400));
		assertTrue(shoal.revealStarted(1500));
		assertEquals(0, shoal.routeReveal(1500, 7000), 1e-9);
		assertEquals(0.28 / 3, shoal.routeReveal(2900, 7000), 1e-9);
		assertEquals(11.0 / 24, shoal.routeReveal(5000, 7000), 1e-9);
		assertEquals(1, shoal.routeReveal(8500, 7000), 1e-9);
		assertEquals(1, shoal.routeReveal(11000, 7000), 1e-9);

		// A shorter animation gets there sooner.
		assertEquals(11.0 / 24, shoal.routeReveal(3000, 3000), 1e-9);
		assertEquals(1, shoal.routeReveal(4500, 3000), 1e-9);
	}

	@Test
	public void nextStopFadesInOnceTheRouteReachesIt()
	{
		Shoal shoal = new Shoal(null);
		shoal.headFor(2, 1000);
		assertEquals(0, shoal.nextStopReveal(4500, 7000), 1e-9);
		assertEquals(0, shoal.nextStopReveal(8500, 7000), 1e-9);
		assertEquals(0.5, shoal.nextStopReveal(8650, 7000), 1e-9);
		assertEquals(1, shoal.nextStopReveal(8800, 7000), 1e-9);
	}

	@Test
	public void remembersTheStopJustReachedUntilCleared()
	{
		Shoal shoal = new Shoal(null);
		shoal.headFor(2, 1000);
		assertEquals(-1, shoal.arrivedStop());

		shoal.headFor(3, 2000);
		assertEquals(2, shoal.arrivedStop());

		shoal.clearArrivedStop();
		assertEquals(-1, shoal.arrivedStop());
	}

	@Test
	public void revealOnlyRestartsWhenTheNextStopChanges()
	{
		Shoal shoal = new Shoal(null);
		shoal.headFor(2, 1000);
		shoal.headFor(2, 7800);
		assertEquals(1, shoal.routeReveal(8500, 7000), 1e-9);

		shoal.headFor(3, 9000);
		assertEquals(0, shoal.routeReveal(9000, 7000), 1e-9);
		assertEquals(0, shoal.nextStopReveal(9000, 7000), 1e-9);
	}
}
