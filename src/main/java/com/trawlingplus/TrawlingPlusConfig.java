package com.trawlingplus;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(TrawlingPlusConfig.GROUP)
public interface TrawlingPlusConfig extends Config
{
	String GROUP = "trawling-plus";
	String SMOOTHING_KEY = "routeSmoothing";

	int MIN_ANIMATION_SECONDS = 3;
	int MAX_ANIMATION_SECONDS = 10;

	int MIN_ARROW_SPACING = 3;
	int MAX_ARROW_SPACING = 15;

	int MIN_ARROW_SCALE = 50;
	int MAX_ARROW_SCALE = 150;

	int MIN_LINE_THICKNESS = 1;
	int MAX_LINE_THICKNESS = 3;

	@ConfigSection(
		name = "Route line",
		description = "The line each shoal swims along",
		position = 3
	)
	String routeLineSection = "routeLine";

	@ConfigSection(
		name = "Direction arrows",
		description = "Arrows showing which way shoals swim",
		position = 4
	)
	String directionArrowsSection = "directionArrows";

	@ConfigSection(
		name = "Shoal heading arrow",
		description = "The arrow marking each shoal on its route",
		position = 5
	)
	String headingArrowSection = "headingArrow";

	@ConfigSection(
		name = "Shoal depth",
		description = "How deep the nearest shoal is swimming, shown at the helm",
		position = 7
	)
	String depthSection = "shoalDepth";

	@ConfigSection(
		name = "Stops",
		description = "Where shoals stop along their routes",
		position = 6
	)
	String stopsSection = "stops";

	enum Smoothing
	{
		NONE("None"),
		LIGHT("Light"),
		HEAVY("Heavy");

		private final String name;

		Smoothing(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

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
		description = "Show whole routes, or only each shoal's route to its next stop",
		position = 0
	)
	default RouteDisplay routeDisplay()
	{
		return RouteDisplay.WHOLE_ROUTE;
	}

	@ConfigItem(
		keyName = "revealNextSection",
		name = "Animate next section",
		description = "In Next stop only, draw each new section out from the shoal instead of showing it at once",
		position = 1
	)
	default boolean revealNextSection()
	{
		return true;
	}

	@Range(
		min = MIN_ANIMATION_SECONDS,
		max = MAX_ANIMATION_SECONDS
	)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "animationDuration",
		name = "Animation duration",
		description = "How long each new section takes to draw out",
		position = 2
	)
	default int animationDuration()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "showRouteLine",
		name = "Show",
		description = "Show the route line",
		position = 3,
		section = routeLineSection
	)
	default boolean showRouteLine()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "routeColour",
		name = "Colour",
		description = "Colour of the route line",
		position = 4,
		section = routeLineSection
	)
	default Color routeColour()
	{
		return new Color(0, 200, 255, 200);
	}

	@Range(
		min = MIN_LINE_THICKNESS,
		max = MAX_LINE_THICKNESS
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "routeLineThickness",
		name = "Thickness",
		description = "Thickness of the route line",
		position = 5,
		section = routeLineSection
	)
	default int routeLineThickness()
	{
		return 2;
	}

	@ConfigItem(
		keyName = SMOOTHING_KEY,
		name = "Smoothing",
		description = "Straight lines between the route's points (None), gently rounded corners (Light), or the smoothest curves (Heavy)",
		position = 6,
		section = routeLineSection
	)
	default Smoothing routeSmoothing()
	{
		return Smoothing.LIGHT;
	}

	@ConfigItem(
		keyName = "showDirectionArrows",
		name = "Show",
		description = "Show arrows along each route pointing the way shoals swim",
		position = 7,
		section = directionArrowsSection
	)
	default boolean showDirectionArrows()
	{
		return true;
	}

	@Range(
		min = MIN_ARROW_SPACING,
		max = MAX_ARROW_SPACING
	)
	@Units(" tiles")
	@ConfigItem(
		keyName = "directionArrowSpacing",
		name = "Spacing",
		description = "Distance between direction arrows",
		position = 8,
		section = directionArrowsSection
	)
	default int directionArrowSpacing()
	{
		return 7;
	}

	@Range(
		min = MIN_ARROW_SCALE,
		max = MAX_ARROW_SCALE
	)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "directionArrowScale",
		name = "Scaling",
		description = "Size of the direction arrows",
		position = 9,
		section = directionArrowsSection
	)
	default int directionArrowScale()
	{
		return 100;
	}

	@Alpha
	@ConfigItem(
		keyName = "directionArrowColour",
		name = "Colour",
		description = "Colour of the direction arrows",
		position = 10,
		section = directionArrowsSection
	)
	default Color directionArrowColour()
	{
		return new Color(0, 200, 255);
	}

	@ConfigItem(
		keyName = "showShoalHeadingArrow",
		name = "Show",
		description = "Show an arrow on each shoal pointing the way it's heading, hidden while it's at a stop",
		position = 11,
		section = headingArrowSection
	)
	default boolean showShoalHeadingArrow()
	{
		return true;
	}

	@Range(
		min = MIN_ARROW_SCALE,
		max = MAX_ARROW_SCALE
	)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "shoalHeadingArrowScale",
		name = "Scaling",
		description = "Size of the shoal heading arrow, and of the arrow leading an animated section",
		position = 12,
		section = headingArrowSection
	)
	default int shoalHeadingArrowScale()
	{
		return 100;
	}

	@Alpha
	@ConfigItem(
		keyName = "shoalHeadingArrowColour",
		name = "Colour",
		description = "Colour of the shoal heading arrow",
		position = 13,
		section = headingArrowSection
	)
	default Color shoalHeadingArrowColour()
	{
		return new Color(255, 221, 0);
	}

	@ConfigItem(
		keyName = "showStops",
		name = "Show",
		description = "Mark where shoals stop, with each shoal's next stop highlighted",
		position = 14,
		section = stopsSection
	)
	default boolean showStops()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "stopColour",
		name = "Colour",
		description = "Colour of the stops",
		position = 15,
		section = stopsSection
	)
	default Color stopColour()
	{
		return new Color(255, 255, 255, 225);
	}

	@Alpha
	@ConfigItem(
		keyName = "nextStopColour",
		name = "Next stop colour",
		description = "Colour of each shoal's next stop",
		position = 16,
		section = stopsSection
	)
	default Color nextStopColour()
	{
		return new Color(255, 221, 0, 225);
	}

	@Range(
		min = MIN_LINE_THICKNESS,
		max = MAX_LINE_THICKNESS
	)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "stopThickness",
		name = "Thickness",
		description = "Thickness of the stops' outlines",
		position = 17,
		section = stopsSection
	)
	default int stopThickness()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showShoalDepth",
		name = "Show",
		description = "Show how deep the nearest shoal is swimming, at the helm of your boat",
		position = 18,
		section = depthSection
	)
	default boolean showShoalDepth()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "shallowDepthColour",
		name = "Shallow",
		description = "Colour of the text while the nearest shoal is shallow",
		position = 19,
		section = depthSection
	)
	default Color shallowDepthColour()
	{
		return new Color(0, 220, 80);
	}

	@Alpha
	@ConfigItem(
		keyName = "moderateDepthColour",
		name = "Moderate",
		description = "Colour of the text while the nearest shoal is at moderate depth",
		position = 20,
		section = depthSection
	)
	default Color moderateDepthColour()
	{
		return new Color(255, 165, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "deepDepthColour",
		name = "Deep",
		description = "Colour of the text while the nearest shoal is deep",
		position = 21,
		section = depthSection
	)
	default Color deepDepthColour()
	{
		return new Color(255, 70, 70);
	}
}
