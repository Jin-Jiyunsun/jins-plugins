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

	@ConfigSection(
		name = "Route line",
		description = "The line each shoal swims along",
		position = 5
	)
	String routeLineSection = "routeLine";

	@ConfigSection(
		name = "Direction arrows",
		description = "Arrows showing which way shoals swim",
		position = 10
	)
	String directionArrowsSection = "directionArrows";

	@ConfigSection(
		name = "Stops",
		description = "Where shoals stop along their routes",
		position = 15
	)
	String stopsSection = "stops";

	@ConfigSection(
		name = "Shoal heading arrow",
		description = "The arrow marking each shoal on its route",
		position = 20
	)
	String headingArrowSection = "headingArrow";

	@ConfigSection(
		name = "Fishable area",
		description = "The water a shoal can be fished from",
		position = 24
	)
	String areaSection = "fishableArea";

	@ConfigSection(
		name = "Heads up display",
		description = "What is shown on your own boat, at the helm",
		position = 28
	)
	String hudSection = "headsUpDisplay";

	@ConfigSection(
		name = "Depth colours",
		description = "Colours for each depth, used at the helm and on the side panel",
		position = 38
	)
	String depthSection = "shoalDepth";

	// DEBUG: remove before release, with both settings in it and what they draw in TrawlingPlusOverlay.
	@ConfigSection(
		name = "Debug",
		description = "Aids for working on the plugin, to be removed before release",
		position = 42,
		closedByDefault = true
	)
	String debugSection = "debug";

	@ConfigSection(
		name = "Side panel",
		description = "Marks on the trawling nets in the sailing side panel",
		position = 34
	)
	String sidePanelSection = "sidePanel";

	enum LineThickness
	{
		THIN("Thin", 1),
		MEDIUM("Medium", 2),
		THICK("Thick", 3);

		private final String name;
		private final int pixels;

		LineThickness(String name, int pixels)
		{
			this.name = name;
			this.pixels = pixels;
		}

		/**
		 * How wide a line this is drawn, in pixels.
		 */
		int pixels()
		{
			return pixels;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

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

	enum ShowOnMaps
	{
		OFF("Off"),
		MINIMAP("Minimap"),
		WORLD_MAP("World"),
		BOTH("Both");

		private final String name;

		ShowOnMaps(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum ShowGuides
	{
		ALWAYS("Always"),
		WITH_NETS("Nets only");

		private final String name;

		ShowGuides(String name)
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
		keyName = "showGuides",
		name = "Show guides",
		description = "When to show routes, the helm display and side panel info.<br><b>Always</b>: shows regardless of if the boat has nets.<br><b>Nets only</b>: only shows if the boat has nets fitted.",
		position = 0
	)
	default ShowGuides showGuides()
	{
		return ShowGuides.WITH_NETS;
	}

	@ConfigItem(
		keyName = "showOnMaps",
		name = "Show on maps",
		description = "Which maps to show routes and shoals on.",
		position = 1
	)
	default ShowOnMaps showOnMaps()
	{
		return ShowOnMaps.WORLD_MAP;
	}

	@ConfigItem(
		keyName = "routeDisplay",
		name = "Route display",
		description = "How much of the route to show.<br><b>Whole route</b>: the entire route nearest your boat.<br><b>Next stop only</b>: from the shoal to its next stop.",
		position = 2
	)
	default RouteDisplay routeDisplay()
	{
		return RouteDisplay.WHOLE_ROUTE;
	}

	@ConfigItem(
		keyName = "revealNextSection",
		name = "Animate next section",
		description = "Animate the shoal's route to its next stop.",
		position = 3
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
		description = "How long the animation takes.",
		position = 4
	)
	default int animationDuration()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "showRouteLine",
		name = "Show",
		description = "Draw the route as a line on the water.",
		position = 6,
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
		description = "Colour of the route line.",
		position = 7,
		section = routeLineSection
	)
	default Color routeColour()
	{
		return new Color(0, 200, 255, 200);
	}

	@ConfigItem(
		keyName = "routeLineThickness",
		name = "Thickness",
		description = "Thickness of the route line.",
		position = 8,
		section = routeLineSection
	)
	default LineThickness routeLineThickness()
	{
		return LineThickness.MEDIUM;
	}

	@ConfigItem(
		keyName = SMOOTHING_KEY,
		name = "Smoothing",
		description = "How much to round off the route's corners.<br><b>None</b>: straight lines between points.<br><b>Light</b>: gently rounded corners.<br><b>Heavy</b>: the smoothest curves.",
		position = 9,
		section = routeLineSection
	)
	default Smoothing routeSmoothing()
	{
		return Smoothing.LIGHT;
	}

	@ConfigItem(
		keyName = "showDirectionArrows",
		name = "Show",
		description = "Show arrows along the route.<br>They point the way the shoals swim.",
		position = 11,
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
		description = "Distance between direction arrows.",
		position = 12,
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
		description = "Size of the direction arrows.",
		position = 13,
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
		description = "Colour of the direction arrows.",
		position = 14,
		section = directionArrowsSection
	)
	default Color directionArrowColour()
	{
		return new Color(0, 200, 255);
	}

	@ConfigItem(
		keyName = "showShoalHeadingArrow",
		name = "Show",
		description = "Show which way the shoal is heading, as an arrow.",
		position = 21,
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
		description = "Size of the shoal heading arrow.",
		position = 22,
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
		description = "Colour of the shoal heading arrow.",
		position = 23,
		section = headingArrowSection
	)
	default Color shoalHeadingArrowColour()
	{
		return new Color(255, 221, 0);
	}

	@ConfigItem(
		keyName = "showStops",
		name = "Show",
		description = "Mark where the shoals stop.<br>The next stop is highlighted.",
		position = 16,
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
		description = "Colour of the stops.",
		position = 17,
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
		description = "Colour of the next stop.",
		position = 18,
		section = stopsSection
	)
	default Color nextStopColour()
	{
		return new Color(255, 221, 0, 225);
	}

	@ConfigItem(
		keyName = "stopThickness",
		name = "Thickness",
		description = "Thickness of the stop outlines.",
		position = 19,
		section = stopsSection
	)
	default LineThickness stopThickness()
	{
		return LineThickness.MEDIUM;
	}

	@ConfigItem(
		keyName = "showHeadsUpDisplay",
		name = "Show",
		description = "Show the heads up display at the helm.",
		position = 29,
		section = hudSection
	)
	default boolean showHeadsUpDisplay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showShoalDepth",
		name = "Depth",
		description = "Show the nearest shoal's depth.<br>Coloured by Depth colours section.",
		position = 30,
		section = hudSection
	)
	default boolean showShoalDepth()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDepthTick",
		name = "Tick on correct depth",
		description = "Show a tick beside the HUD depth when it's correct.",
		position = 31,
		section = hudSection
	)
	default boolean showDepthTick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTimeAtStop",
		name = "Time left at stop",
		description = "Show how long until the shoal swims on.",
		position = 32,
		section = hudSection
	)
	default boolean showTimeAtStop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBaited",
		name = "Baited",
		description = "Show if the shoal is baited, and how much<br>bait is left.<br>Must be synced by opening the hold<br>to view how much bait is in it.",
		position = 33,
		section = hudSection
	)
	default boolean showBaited()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shallowDepthColour",
		name = "Shallow",
		description = "Colour of shallow depth.",
		position = 39,
		section = depthSection
	)
	default Color shallowDepthColour()
	{
		return new Color(0, 220, 80);
	}

	@ConfigItem(
		keyName = "moderateDepthColour",
		name = "Moderate",
		description = "Colour of moderate depth.",
		position = 40,
		section = depthSection
	)
	default Color moderateDepthColour()
	{
		return new Color(255, 165, 0);
	}

	@ConfigItem(
		keyName = "deepDepthColour",
		name = "Deep",
		description = "Colour of deep depth.",
		position = 41,
		section = depthSection
	)
	default Color deepDepthColour()
	{
		return new Color(255, 70, 70);
	}

	@ConfigItem(
		keyName = "showNetDepths",
		name = "Net depths",
		description = "Show each net's depth as a letter.<br><b>R</b>: raised, <b>S</b>: shallow, <b>M</b>: moderate, <b>D</b>: deep.<br>Coloured by Depth colours section.",
		position = 35,
		section = sidePanelSection
	)
	default boolean showNetDepths()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNetButton",
		name = "Depth guide",
		description = "Highlight the raise or lower button needed<br>to reach the target depth.",
		position = 36,
		section = sidePanelSection
	)
	default boolean showNetButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNetCorrect",
		name = "Tick on correct depth",
		description = "Show a tick on the side panel's net<br>when its depth is correct.",
		position = 37,
		section = sidePanelSection
	)
	default boolean showNetCorrect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFishableArea",
		name = "Show",
		description = "Show an estimate of the fishable area,<br>as a ring centred on the shoal.",
		position = 25,
		section = areaSection
	)
	default boolean showFishableArea()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "fishableAreaColour",
		name = "Colour",
		description = "Colour of the fishable area.",
		position = 26,
		section = areaSection
	)
	default Color fishableAreaColour()
	{
		return new Color(80, 200, 255, 180);
	}

	@ConfigItem(
		keyName = "fishableAreaThickness",
		name = "Thickness",
		description = "Thickness of the fishable area outline.",
		position = 27,
		section = areaSection
	)
	default LineThickness fishableAreaThickness()
	{
		return LineThickness.MEDIUM;
	}

	@ConfigItem(
		keyName = "debugRoutePoints",
		name = "Show line points",
		description = "Mark each point the route line is drawn through.",
		position = 43,
		section = debugSection
	)
	default boolean debugRoutePoints()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugStopNumbers",
		name = "Show stop numbers",
		description = "Number each stop, from 1, in the order<br>the shoal swims to them.",
		position = 44,
		section = debugSection
	)
	default boolean debugStopNumbers()
	{
		return false;
	}
}
