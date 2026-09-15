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
		// Stretches marked safe by hand in routes.json despite sitting close to where a sea creature that
		// attacks boats spawns, as [x, y, tiles] circles, or null where none apply.
		double[][] safe;
		// Where sea creatures that attack boats spawn close enough to threaten this route, place by place, or
		// null where none do. From the OSRS wiki (Boat combat, each creature page), folded in by hand once
		// under the route each place is close enough to threaten; a place is never split between routes.
		List<Threat> threats;
	}

	static class Threat
	{
		String creature;
		int combat;
		int[][] points;
	}
}
