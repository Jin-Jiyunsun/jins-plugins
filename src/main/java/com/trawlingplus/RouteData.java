package com.trawlingplus;

import java.util.List;

/**
 * The shape of routes.json, which tools/generate_routes.py builds from the routes recorded in game
 * in tools/recorded_routes.json.
 */
class RouteData
{
	List<Species> species;

	static class Species
	{
		String name;
		List<Route> routes;
	}

	static class Route
	{
		String name;
		// How long a shoal sits at each stop on this route, in ticks, or 0 where it has not been timed
		// yet. Different species sit for very different lengths, so there is no sensible default.
		int stopTicks;
		double[][] stops;
		double[][] path;
	}
}
