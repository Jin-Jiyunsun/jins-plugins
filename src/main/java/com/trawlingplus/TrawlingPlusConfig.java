package com.trawlingplus;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
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

	// Longest warning before a shoal leaves its stop, in seconds. The shortest stop known is about 42.
	int MAX_LEAVING_SECONDS = 30;

	@ConfigSection(
		name = "Route line",
		description = "The line each shoal swims along",
		position = 5
	)
	String routeLineSection = "routeLine";

	@ConfigSection(
		name = "Direction arrows",
		description = "Arrows showing which way shoals swim",
		position = 11
	)
	String directionArrowsSection = "directionArrows";

	@ConfigSection(
		name = "Stops",
		description = "Where shoals stop along their routes",
		position = 17
	)
	String stopsSection = "stops";

	@ConfigSection(
		name = "Shoal heading arrow",
		description = "The arrow marking each shoal on its route",
		position = 22
	)
	String headingArrowSection = "headingArrow";

	@ConfigSection(
		name = "Fishable area",
		description = "The water a shoal can be fished from",
		position = 26
	)
	String areaSection = "fishableArea";

	@ConfigSection(
		name = "Heads up display",
		description = "What is shown on your own boat, at the helm",
		position = 33
	)
	String hudSection = "headsUpDisplay";

	@ConfigSection(
		name = "Depth colours",
		description = "Colours for each depth, used at the helm and on the side panel",
		position = 52
	)
	String depthSection = "shoalDepth";

	@ConfigSection(
		name = "Fish",
		description = "Which fish's routes and shoals to show",
		position = 56
	)
	String fishSection = "fish";

	@ConfigSection(
		name = "Side panel",
		description = "Marks on the trawling nets in the sailing side panel",
		position = 43
	)
	String sidePanelSection = "sidePanel";

	@ConfigSection(
		name = "Notifications",
		description = "Alerts for the nets, the hold and the shoal",
		position = 47
	)
	String notificationsSection = "notifications";

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

	enum ArrowStyle
	{
		FLAT("Flat"),
		STANDING("Standing"),
		FACING("Facing camera");

		private final String name;

		ArrowStyle(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum FishableShape
	{
		SQUARE("Square"),
		CIRCLE("Circle");

		private final String name;

		FishableShape(String name)
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
		description = "When to show routes, the heads up display<br>and side panel marks.<br><b>Always</b>: shows whether or not the boat has nets.<br><b>Nets only</b>: only shows if the boat has nets fitted.",
		position = 0
	)
	default ShowGuides showGuides()
	{
		return ShowGuides.WITH_NETS;
	}

	@ConfigItem(
		keyName = "showOnMaps",
		name = "Show on maps",
		description = "Which maps to show routes and shoals on.<br>The world map also marks dangerous water<br>near sea creatures that attack boats.",
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
		description = "Show the route as a line on the water.",
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
		keyName = "clearAroundBoat",
		name = "Clear around boat",
		description = "Hide the route line, direction arrows<br>and stops under your boat.",
		position = 10,
		section = routeLineSection
	)
	default boolean clearAroundBoat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDirectionArrows",
		name = "Show",
		description = "Show arrows along the route.<br>They point the way the shoals swim.",
		position = 12,
		section = directionArrowsSection
	)
	default boolean showDirectionArrows()
	{
		return true;
	}

	@ConfigItem(
		keyName = "arrowStyle",
		name = "Style",
		description = "How the arrows on the water are drawn.<br><b>Flat</b>: lying on the water.<br><b>Standing</b>: standing up along the route.<br><b>Facing camera</b>: standing up, turned to face you.",
		position = 13,
		section = directionArrowsSection
	)
	default ArrowStyle arrowStyle()
	{
		return ArrowStyle.FACING;
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
		position = 14,
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
		position = 15,
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
		position = 16,
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
		position = 23,
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
		position = 24,
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
		position = 25,
		section = headingArrowSection
	)
	default Color shoalHeadingArrowColour()
	{
		return new Color(255, 221, 0);
	}

	@ConfigItem(
		keyName = "showStops",
		name = "Show",
		description = "Show where the shoals stop.<br>The next stop is highlighted.",
		position = 18,
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
		position = 19,
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
		position = 20,
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
		position = 21,
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
		position = 34,
		section = hudSection
	)
	default boolean showHeadsUpDisplay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showShoalDepth",
		name = "Depth",
		description = "Show the nearest shoal's depth.<br>Coloured by the Depth colours section.",
		position = 35,
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
		position = 36,
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
		position = 37,
		section = hudSection
	)
	default boolean showTimeAtStop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "timeLeftColour",
		name = "Time left colour",
		description = "Colour of the time left at stop.",
		position = 38,
		section = hudSection
	)
	default Color timeLeftColour()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "showFishInNets",
		name = "Fish in nets",
		description = "Show how many fish are in the nets,<br>and a warning when the hold is full.<br>Hides once the nets are raised and<br>have been empty for a minute.<br>Shows ? when unsure, until the nets<br>are emptied or opened.",
		position = 39,
		section = hudSection
	)
	default boolean showFishInNets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fishInNetsColour",
		name = "Fish in nets colour",
		description = "Colour of the fish in nets count.",
		position = 40,
		section = hudSection
	)
	default Color fishInNetsColour()
	{
		return new Color(140, 200, 255);
	}

	@ConfigItem(
		keyName = "showBaited",
		name = "Baited",
		description = "Show if the shoal is baited, and how much<br>bait is left. Shows ? until the hold is<br>opened to count the bait in it.",
		position = 41,
		section = hudSection
	)
	default boolean showBaited()
	{
		return true;
	}

	@ConfigItem(
		keyName = "baitedColour",
		name = "Baited colour",
		description = "Colour of the baited status.",
		position = 42,
		section = hudSection
	)
	default Color baitedColour()
	{
		return new Color(242, 180, 210);
	}

	@ConfigItem(
		keyName = "shallowDepthColour",
		name = "Shallow",
		description = "Colour of shallow depth.",
		position = 53,
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
		position = 54,
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
		position = 55,
		section = depthSection
	)
	default Color deepDepthColour()
	{
		return new Color(255, 70, 70);
	}

	@ConfigItem(
		keyName = "showNetDepths",
		name = "Net depths",
		description = "Show each net's depth as a letter.<br><b>R</b>: raised, <b>S</b>: shallow, <b>M</b>: moderate, <b>D</b>: deep.<br>Coloured by the Depth colours section.",
		position = 44,
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
		position = 45,
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
		position = 46,
		section = sidePanelSection
	)
	default boolean showNetCorrect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyNetsFull",
		name = "Nets full",
		description = "Notify when the nets are full.",
		position = 48,
		section = notificationsSection
	)
	default Notification notifyNetsFull()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyHoldFull",
		name = "Hold full",
		description = "Notify when emptying the nets finds<br>the cargo hold full.",
		position = 49,
		section = notificationsSection
	)
	default Notification notifyHoldFull()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyShoalLeaving",
		name = "Shoal leaving",
		description = "Notify when the nearest shoal is about<br>to leave its stop.",
		position = 50,
		section = notificationsSection
	)
	default Notification notifyShoalLeaving()
	{
		return Notification.OFF;
	}

	@Range(
		min = 0,
		max = MAX_LEAVING_SECONDS
	)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "shoalLeavingSeconds",
		name = "Leaving warning",
		description = "How long before the shoal leaves to notify.<br><b>0</b>: as it sets off.<br><b>Above 0</b>: needs the stop's timer running.",
		position = 51,
		section = notificationsSection
	)
	default int shoalLeavingSeconds()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showFishableArea",
		name = "Show",
		description = "Show an estimate of the fishable area<br>around the shoal.",
		position = 27,
		section = areaSection
	)
	default boolean showFishableArea()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fishableAreaShape",
		name = "Shape",
		description = "The shape the fishable area is displayed as.<br><b>Square</b>: mostly accurate to the area.<br><b>Circle</b>: less accurate, but looks nicer.",
		position = 28,
		section = areaSection
	)
	default FishableShape fishableAreaShape()
	{
		return FishableShape.SQUARE;
	}

	@ConfigItem(
		keyName = "showFishingPoint",
		name = "Fishing point",
		description = "Show a dot near the bow of the boat. This is<br>the point that must be inside the fishable<br>area to catch fish.",
		position = 29,
		section = areaSection
	)
	default boolean showFishingPoint()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "fishableAreaColour",
		name = "Colour",
		description = "Colour of the fishable area and the<br>fishing point.",
		position = 30,
		section = areaSection
	)
	default Color fishableAreaColour()
	{
		return new Color(80, 200, 255, 180);
	}

	@Alpha
	@ConfigItem(
		keyName = "fishableAreaFillColour",
		name = "Fill colour",
		description = "Colour of the fishable area's fill.",
		position = 31,
		section = areaSection
	)
	default Color fishableAreaFillColour()
	{
		return new Color(80, 200, 255, 30);
	}

	@ConfigItem(
		keyName = "fishableAreaThickness",
		name = "Thickness",
		description = "Thickness of the fishable area outline.",
		position = 32,
		section = areaSection
	)
	default LineThickness fishableAreaThickness()
	{
		return LineThickness.MEDIUM;
	}

	@ConfigItem(
		keyName = "showBluefin",
		name = "Bluefin",
		description = "Show bluefin routes and shoals.",
		position = 57,
		section = fishSection
	)
	default boolean showBluefin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGiantKrill",
		name = "Giant krill",
		description = "Show giant krill routes and shoals.",
		position = 58,
		section = fishSection
	)
	default boolean showGiantKrill()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHaddock",
		name = "Haddock",
		description = "Show haddock routes and shoals.",
		position = 59,
		section = fishSection
	)
	default boolean showHaddock()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showYellowfin",
		name = "Yellowfin",
		description = "Show yellowfin routes and shoals.",
		position = 60,
		section = fishSection
	)
	default boolean showYellowfin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHalibut",
		name = "Halibut",
		description = "Show halibut routes and shoals.",
		position = 61,
		section = fishSection
	)
	default boolean showHalibut()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMarlin",
		name = "Marlin",
		description = "Show marlin routes and shoals.",
		position = 62,
		section = fishSection
	)
	default boolean showMarlin()
	{
		return true;
	}
}
