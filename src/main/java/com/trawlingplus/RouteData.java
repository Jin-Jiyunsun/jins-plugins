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
		// How far from the shoal it can be fished from, in tiles along each axis, or 0 where that has not
		// been measured. It differs by species: bluefin reach 10.5 tiles, halibut only 6.5. It cannot be
		// worked out from a recording, so it is measured by hand in game and kept in tools/species.json.
		double fishableReach;
		// "any" where both fish offcuts and fine fish offcuts bait this kind of shoal, "fine" where only
		// fine fish offcuts do.
		String bait;
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
