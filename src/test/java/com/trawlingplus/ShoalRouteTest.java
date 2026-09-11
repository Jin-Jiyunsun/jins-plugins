package com.trawlingplus;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShoalRouteTest
{
	// A 10 by 10 square swum from the origin, 40 tiles round, with stops at 10 and 30 tiles.
	private static final ShoalRoute SQUARE = new ShoalRoute("Test", "Square",
		new double[][]{{0, 0}, {10, 0}, {10, 10}, {0, 10}},
		new double[][]{{10, 0}, {0, 10}});

	@Test
	public void projectsOntoTheNearestPointOfTheLoop()
	{
		ShoalRoute.Projection onPath = SQUARE.project(5, 0);
		assertEquals(5, onPath.distance, 1e-9);
		assertEquals(0, onPath.offset, 1e-9);

		ShoalRoute.Projection offPath = SQUARE.project(5, -2);
		assertEquals(5, offPath.distance, 1e-9);
		assertEquals(2, offPath.offset, 1e-9);

		// The closing edge, from the last point back to the first.
		assertEquals(35, SQUARE.project(0, 5).distance, 1e-9);
	}

	@Test
	public void nextStopIsTheFirstOneAhead()
	{
		assertEquals(0, SQUARE.nextStop(5));
		assertEquals(1, SQUARE.nextStop(15));
	}

	@Test
	public void nextStopWrapsRoundTheLoop()
	{
		assertEquals(0, SQUARE.nextStop(35));
	}

	@Test
	public void aShoalSittingAtAStopIsHeadingForTheOneAfter()
	{
		assertEquals(1, SQUARE.nextStop(10));
		assertEquals(1, SQUARE.nextStop(8.5));
		assertEquals(0, SQUARE.nextStop(29));
	}

	@Test
	public void samplesCoverTheLoopInOrder()
	{
		assertEquals(40, SQUARE.sampleCount());
		assertEquals(0, SQUARE.sampleAt(0));
		assertEquals(10, SQUARE.sampleAt(10));
		// Past the last sample wraps back round to the first.
		assertEquals(0, SQUARE.sampleAt(39.5));
	}

	@Test
	public void overlapsOnlyRectanglesItReachesInto()
	{
		assertTrue(SQUARE.overlaps(5, 5, 20, 20));
		// Touching a corner counts.
		assertTrue(SQUARE.overlaps(-5, -5, 0, 0));
		assertFalse(SQUARE.overlaps(11, 11, 30, 30));
	}

	@Test
	public void smoothingPassesThroughEveryPoint()
	{
		double[][] points = {{0, 0}, {10, 0}, {10, 10}, {0, 10}};
		double[][] curve = ShoalRoute.smooth(points);
		for (double[] point : points)
		{
			boolean found = false;
			for (double[] c : curve)
			{
				found |= Math.abs(c[0] - point[0]) < 1e-9 && Math.abs(c[1] - point[1]) < 1e-9;
			}
			assertTrue("curve misses " + point[0] + ", " + point[1], found);
		}
	}

	@Test
	public void smoothingKeepsStraightStretchesStraight()
	{
		// Between (10, 0) and (20, 0) the neighbouring points are in line too, so the curve can't bend.
		double[][] curve = ShoalRoute.smooth(new double[][]{{0, 0}, {10, 0}, {20, 0}, {30, 0}, {30, 10}, {0, 10}});
		for (double[] c : curve)
		{
			if (c[0] >= 10 && c[0] <= 20 && c[1] < 5)
			{
				assertEquals(0, c[1], 1e-9);
			}
		}
	}

	@Test
	public void smoothingNeverPullsARouteMoreThanATileOffItsPath() throws IOException
	{
		try (InputStream in = ShoalRoute.class.getResourceAsStream("routes.json"))
		{
			RouteData data = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RouteData.class);
			for (RouteData.Species species : data.species)
			{
				for (RouteData.Route route : species.routes)
				{
					ShoalRoute original = new ShoalRoute(species.name, route.name, route.path, route.stops);
					for (double[] point : ShoalRoute.smooth(route.path))
					{
						double offset = original.project(point[0], point[1]).offset;
						assertTrue(route.name + " curve is " + offset + " tiles off its path", offset <= 1);
					}
				}
			}
		}
	}

	@Test
	public void loadsEveryWikiRouteWithItsStopsOnThePath() throws IOException
	{
		List<ShoalRoute> routes = ShoalRoute.load(new Gson());
		assertEquals(16, routes.size());
		for (ShoalRoute route : routes)
		{
			for (int stop = 0; stop < route.stopCount(); stop++)
			{
				double offset = route.project(route.stopX(stop), route.stopY(stop)).offset;
				assertTrue(route.getName() + " stop " + stop + " is " + offset + " tiles off its path", offset <= 5);
			}
		}
	}
}
