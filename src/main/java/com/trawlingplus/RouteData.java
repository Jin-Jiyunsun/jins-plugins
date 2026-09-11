package com.trawlingplus;

import java.util.List;

/**
 * The shape of routes.json, which tools/generate_routes.py builds from the OSRS Wiki.
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
