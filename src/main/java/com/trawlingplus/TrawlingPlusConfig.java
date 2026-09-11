package com.trawlingplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(TrawlingPlusConfig.GROUP)
public interface TrawlingPlusConfig extends Config
{
	String GROUP = "trawling-plus";

	enum RouteDisplay
	{
		WHOLE_ROUTE("Whole route"),
		NEXT_STOP("Next stop only");

		private final String name;

		RouteDisplay(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigItem(
		keyName = "routeDisplay",
		name = "Route display",
		description = "Show each shoal's whole route, or only the stretch up to its next stop",
		position = 0
	)
	default RouteDisplay routeDisplay()
	{
		return RouteDisplay.WHOLE_ROUTE;
	}

	@ConfigItem(
		keyName = "showDirectionArrows",
		name = "Direction arrows",
		description = "Draw arrows along each route showing which way its shoal swims round it",
		position = 1
	)
	default boolean showDirectionArrows()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showShoalHeadingArrow",
		name = "Shoal heading arrow",
		description = "Mark each shoal's place on its route with an arrow pointing the way it's heading, hidden while it sits at a stop",
		position = 2
	)
	default boolean showShoalHeadingArrow()
	{
		return true;
	}
}
