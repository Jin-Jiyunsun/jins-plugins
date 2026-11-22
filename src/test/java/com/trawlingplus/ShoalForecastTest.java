package com.trawlingplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShoalForecastTest
{
	// A 10 by 10 square, stops 10 and 30 tiles round, swum 10 -> 30 in four ticks and 30 -> 10 in two, sitting still
	// five more ticks at each stop: a 16 tick lap.
	private static final ShoalRoute ROUTE = new ShoalRoute("Test", "Timed", 0,
		new double[][]{{0, 0}, {10, 0}, {10, 10}, {0, 10}},
		new double[][]{{10, 0}, {0, 10}}, null, null, 5, new float[][]{{0.25f, 0.5f, 0.75f}, {0.5f}});

	@Test
	public void followsFromLeavingAStop()
	{
		ShoalForecast forecast = new ShoalForecast();
		Shoal shoal = new Shoal(null);
		long now = 0;
		assertEquals(-1, forecast.distance(ROUTE), 1e-9);

		// Sat at the first stop long enough to count as a stop, then sets off.
		for (int tick = 0; tick < 12; tick++)
		{
			forecast.tick(now += 600);
			shoal.update(new double[]{10, 0}, null);
			forecast.see(shoal, ROUTE);
		}
		forecast.tick(now += 600);
		shoal.update(new double[]{10, 5}, null);
		assertTrue(shoal.justLeft());
		forecast.see(shoal, ROUTE);
		assertTrue(forecast.inView(ROUTE));
		assertEquals(15, forecast.distance(ROUTE), 1e-9);
		// Halfway to the next tick, halfway to where it will be then; swimming, so no time left at a stop.
		assertEquals(17.5, forecast.distance(ROUTE, now + 300), 1e-9);
		assertEquals(-1, forecast.stopLeft(ROUTE, now + 300), 1e-9);

		// Out of sight for three ticks: at the second stop.
		for (int tick = 0; tick < 3; tick++)
		{
			forecast.tick(now += 600);
		}
		assertFalse(forecast.inView(ROUTE));
		assertEquals(30, forecast.distance(ROUTE), 1e-9);
		// Just arrived: the whole stop left, running down smoothly between ticks.
		assertEquals(1, forecast.stopLeft(ROUTE, now), 1e-9);
		assertEquals(0.9, forecast.stopLeft(ROUTE, now + 300), 1e-9);
		assertEquals(30, forecast.distance(ROUTE, now + 300), 1e-9);

		// Logged out for ten ticks: the clock counts them, which takes it round to the first stop again.
		forecast.away();
		forecast.tick(now += 6000);
		assertEquals(10, forecast.distance(ROUTE), 1e-9);
	}

	@Test
	public void estimatesFromWhereItWasLastSeen()
	{
		ShoalForecast forecast = new ShoalForecast();
		Shoal shoal = new Shoal(null);
		long now = 0;
		// Only ever seen swimming, a quarter of the way along the first leg on its last tick in view.
		forecast.tick(now += 600);
		shoal.update(new double[]{10, 1}, null);
		forecast.see(shoal, ROUTE);
		forecast.tick(now += 600);
		shoal.update(new double[]{10, 5}, null);
		forecast.see(shoal, ROUTE);
		forecast.tick(now += 600);
		assertEquals(20, forecast.distance(ROUTE), 1e-9);
	}

	@Test
	public void freezesCountEachTick()
	{
		ShoalForecast forecast = new ShoalForecast();
		Shoal shoal = new Shoal(null);
		long now = 0;
		for (int tick = 0; tick < 12; tick++)
		{
			forecast.tick(now += 600);
			shoal.update(new double[]{10, 0}, null);
			forecast.see(shoal, ROUTE);
		}
		forecast.tick(now += 600);
		shoal.update(new double[]{10, 5}, null);
		forecast.see(shoal, ROUTE);

		// A five second freeze while connected, then the three ticks held up by it: three ticks on, not eleven.
		forecast.tick(now += 5000);
		forecast.tick(now += 10);
		forecast.tick(now += 10);
		assertEquals(30, forecast.distance(ROUTE), 1e-9);
	}
}
