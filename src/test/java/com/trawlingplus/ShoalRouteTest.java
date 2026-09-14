package com.trawlingplus;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShoalRouteTest
{
	// A 10 by 10 square swum from the origin, 40 tiles round, with stops at 10 and 30 tiles.
	private static final ShoalRoute SQUARE = new ShoalRoute("Test", "Square", 0,
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
	public void followingAlongALongStraightNeverJumpsAhead()
	{
		// Sides far longer than the stretch of route a shoal is followed within.
		ShoalRoute big = new ShoalRoute("Test", "Big", 0,
			new double[][]{{0, 0}, {40, 0}, {40, 40}, {0, 40}},
			new double[][]{{40, 0}});
		double distance = big.project(0, 0).distance;
		for (double x = 0.5; x < 40; x += 0.5)
		{
			distance = big.project(x, 0, distance).distance;
			assertEquals("following at x = " + x, x, distance, 1e-9);
		}
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
	public void piecesShorterThanTheSpacingAreNotSplit()
	{
		ShoalRoute small = new ShoalRoute("Test", "Small", 0,
			new double[][]{{0, 0}, {2, 0}, {2, 2}, {0, 2}},
			new double[][]{{2, 0}});
		assertEquals(4, small.sampleCount());
	}

	@Test
	public void samplesCoverTheLoopInOrder()
	{
		// Each 10 tile side is a straight, split into four pieces no longer than 3 tiles.
		assertEquals(16, SQUARE.sampleCount());
		assertEquals(0, SQUARE.sampleAt(0));
		assertEquals(4, SQUARE.sampleAt(10));
		// Past the last sample wraps back round to the first.
		assertEquals(0, SQUARE.sampleAt(39.5));
	}

	@Test
	public void pointAtFollowsTheRouteAndTheWayItRuns()
	{
		assertPoint(SQUARE.pointAt(5), 5, 0, 1, 0);
		assertPoint(SQUARE.pointAt(12.5), 10, 2.5, 0, 1);
		// On the closing edge, heading back to the start.
		assertPoint(SQUARE.pointAt(39.5), 0, 0.5, 0, -1);
		// Distances past the end wrap round.
		assertPoint(SQUARE.pointAt(45), 5, 0, 1, 0);
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
	public void lightSmoothingPassesThroughEveryPoint()
	{
		double[][] points = {{0, 0}, {10, 0}, {10, 10}, {0, 10}};
		double[][] curve = ShoalRoute.catmullRom(points);
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
	public void heavySmoothingRunsThroughEveryStop()
	{
		// Heavy smoothing would cut these corners by over a tile, so stops on two of them have to be pulled onto.
		double[][] points = {{0, 0}, {20, 0}, {20, 20}, {0, 20}};
		double[][] stops = {{20, 0}, {0, 20}};
		ShoalRoute route = new ShoalRoute("Test", "Corners", 0, ShoalRoute.bSpline(points, stops), stops);
		for (int stop = 0; stop < route.stopCount(); stop++)
		{
			assertEquals(0, route.project(route.stopX(stop), route.stopY(stop)).offset, 0.01);
		}
	}

	@Test
	public void smoothingKeepsStraightStretchesStraight()
	{
		// Between (10, 0) and (20, 0) the neighbouring points are in line too, so neither curve can bend.
		double[][] points = {{0, 0}, {10, 0}, {20, 0}, {30, 0}, {30, 10}, {0, 10}};
		for (double[][] curve : new double[][][]{ShoalRoute.catmullRom(points), ShoalRoute.bSpline(points, new double[0][])})
		{
			for (double[] c : curve)
			{
				if (c[0] >= 10 && c[0] <= 20 && c[1] < 5)
				{
					assertEquals(0, c[1], 1e-9);
				}
			}
		}
	}

	@Test
	// Two tiles leaves room under the 3 a shoal may stray from its route before it stops being matched.
	public void smoothingNeverPullsARouteMoreThanTwoTilesOffItsPath() throws IOException
	{
		RouteData data = ShoalRoute.read(new Gson());
		for (RouteData.Species species : data.species)
		{
			for (RouteData.Route route : species.routes)
			{
				ShoalRoute original = new ShoalRoute(species.name, route.name, route.stopTicks,
					route.path, route.stops);
				for (double[][] curve : new double[][][]{ShoalRoute.catmullRom(route.path), ShoalRoute.bSpline(route.path, route.stops)})
				{
					for (double[] point : curve)
					{
						double offset = original.project(point[0], point[1]).offset;
						assertTrue(route.name + " curve is " + offset + " tiles off its path", offset <= 2);
					}
				}
			}
		}
	}

	@Test
	public void buildsEveryRouteWithItsStopsOnThePath() throws IOException
	{
		RouteData data = ShoalRoute.read(new Gson());
		for (TrawlingPlusConfig.Smoothing smoothing : TrawlingPlusConfig.Smoothing.values())
		{
			List<ShoalRoute> routes = ShoalRoute.build(data, smoothing);
			assertFalse(routes.isEmpty());
			for (int index = 0; index < routes.size(); index++)
			{
				ShoalRoute route = routes.get(index);
				for (int stop = 0; stop < route.stopCount(); stop++)
				{
					double offset = route.project(route.stopX(stop), route.stopY(stop)).offset;
					assertTrue(smoothing + " " + route.getSpecies() + " route " + index + " stop " + stop + " is " + offset + " tiles off its path", offset <= 1);
				}
			}
		}
	}

	private static void assertPoint(double[] point, double x, double y, double dx, double dy)
	{
		assertEquals(x, point[0], 1e-9);
		assertEquals(y, point[1], 1e-9);
		assertEquals(dx, point[2], 1e-9);
		assertEquals(dy, point[3], 1e-9);
	}
}
