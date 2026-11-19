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
		// How many ticks a shoal sits still at each stop, and for each stop to the next, the share of that leg swum by
		// each tick between leaving one stop and sitting still at the next. Null where the route hasn't been timed.
		int stillTicks;
		double[][] legs;
		double[][] stops;
		double[][] path;
		// Stretches marked safe by hand in routes.json despite sitting close to where a sea creature that
		// attacks boats spawns, as [x, y, tiles] circles, or null where none apply.
		double[][] safe;
		// Where sea creatures that attack boats spawn close enough to threaten this route, or null for none. Taken by
		// hand from the OSRS wiki; each place belongs to one route.
		List<Threat> threats;
	}

	static class Threat
	{
		String creature;
		int combat;
		int[][] points;
	}
}
