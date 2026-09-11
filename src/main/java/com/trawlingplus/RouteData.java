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
		double[][] stops;
		double[][] path;
	}
}
