package com.trawlingplus;

import java.util.List;

/**
 * The shape of routes.json, which tools/generate_routes.py builds from the OSRS Wiki's routes, with
 * routes recorded in game (tools/recorded_routes.json) in place of the wiki's where there are some.
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
		double[][] stops;
		double[][] path;
	}
}
