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
	String TIMER_BAR_KEY = "showTimerBar";
	String SPOT_FISH_KEY = "showSpotFish";

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
		position = 7
	)
	String routeLineSection = "routeLine";

	@ConfigSection(
		name = "Direction arrows",
		description = "Arrows showing which way shoals swim",
		position = 12
	)
	String directionArrowsSection = "directionArrows";

	@ConfigSection(
		name = "Stops",
		description = "Where shoals stop along their routes",
		position = 18
	)
	String stopsSection = "stops";

	@ConfigSection(
		name = "Shoal heading arrow",
		description = "The arrow marking each shoal on its route",
		position = 24
	)
	String headingArrowSection = "headingArrow";

	@ConfigSection(
		name = "Fishable area",
		description = "The water a shoal can be fished from",
		position = 28
	)
	String areaSection = "fishableArea";

	@ConfigSection(
		name = "Heads up display",
		description = "What is shown on your own boat, at the helm",
		position = 35
	)
	String hudSection = "headsUpDisplay";

	@ConfigSection(
		name = "Depth colours",
		description = "Colours for each depth, used at the helm and on<br>the side panel",
		position = 61
	)
	String depthSection = "shoalDepth";

	@ConfigSection(
		name = "Fish",
		description = "Which fish's routes and shoals to show",
		position = 65
	)
	String fishSection = "fish";

	@ConfigSection(
		name = "Side panel",
		description = "Marks on the trawling nets in the sailing side panel",
		position = 52
	)
	String sidePanelSection = "sidePanel";

	@ConfigSection(
		name = "Notifications",
		description = "Alerts for the nets, the hold and the shoal",
		position = 56
	)
	String notificationsSection = "notifications";

	@ConfigSection(
		name = "Debug: drawing",
		description = "What the debug marks show",
		position = 71
	)
	String debugDrawingSection = "debugDrawingGroup";

	@ConfigSection(
		name = "Debug: fish look",
		description = "How the one kind of fish being tuned looks",
		position = 72
	)
	String debugShapeSection = "debugFishShape";

	@ConfigSection(
		name = "Debug: fish reshaping",
		description = "Reshaping the model of the one kind of fish being tuned",
		position = 73
	)
	String debugReshapeSection = "debugFishReshape";

	@ConfigSection(
		name = "Debug: making room",
		description = "How the one kind of fish being tuned eases away from others",
		position = 75
	)
	String debugRoomSection = "debugFishRoom";

	@ConfigSection(
		name = "Debug: fish lanes",
		description = "The lanes and fish of the one spot being tuned",
		position = 74
	)
	String debugCrowdSection = "debugFishCrowd";

	@ConfigSection(
		name = "Debug: dips",
		description = "How the one kind of fish being tuned now and then dips deeper",
		position = 76
	)
	String debugDipSection = "debugFishDips";

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

	enum HudPosition
	{
		HELM("Helm"),
		BOW("Bow"),
		SAILS("Sails");

		private final String name;

		HudPosition(String name)
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
		description = "When to show routes, the heads up display and<br>side panel marks.<br><b>Always</b>: shows whether or not the boat has nets.<br><b>Nets only</b>: only shows if the boat has nets fitted.",
		position = 0
	)
	default ShowGuides showGuides()
	{
		return ShowGuides.WITH_NETS;
	}

	@ConfigItem(
		keyName = "showOnMaps",
		name = "Show on maps",
		description = "Which maps to show routes and shoals on. The<br>world map also marks dangerous water near sea<br>creatures that attack boats.",
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
		keyName = "clearAroundBoat",
		name = "Clear around boat",
		description = "Hide the route line, direction arrows and stops<br>under your boat.",
		position = 5
	)
	default boolean clearAroundBoat()
	{
		return false;
	}

	@ConfigItem(
		keyName = SPOT_FISH_KEY,
		name = "Fishing spot fish",
		description = "Show a small shoal of the fish each fishing<br>spot at sea gives, swimming around the spot.",
		position = 6
	)
	default boolean showSpotFish()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRouteLine",
		name = "Show",
		description = "Show the route as a line on the water.",
		position = 8,
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
		position = 9,
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
		position = 10,
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
		position = 11,
		section = routeLineSection
	)
	default Smoothing routeSmoothing()
	{
		return Smoothing.LIGHT;
	}

	@ConfigItem(
		keyName = "showDirectionArrows",
		name = "Show",
		description = "Show arrows along the route. They point the way<br>the shoals swim.",
		position = 13,
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
		position = 14,
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
		position = 15,
		section = directionArrowsSection
	)
	default int directionArrowSpacing()
	{
		return 15;
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
		position = 16,
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
		position = 17,
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
		position = 25,
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
		position = 26,
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
		position = 27,
		section = headingArrowSection
	)
	default Color shoalHeadingArrowColour()
	{
		return new Color(255, 221, 0);
	}

	@ConfigItem(
		keyName = "showStops",
		name = "Show",
		description = "Show where the shoals stop. The next stop<br>is highlighted.",
		position = 19,
		section = stopsSection
	)
	default boolean showStops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "stopShape",
		name = "Shape",
		description = "The shape the stops are displayed as.",
		position = 20,
		section = stopsSection
	)
	default FishableShape stopShape()
	{
		return FishableShape.SQUARE;
	}

	@Alpha
	@ConfigItem(
		keyName = "stopColour",
		name = "Colour",
		description = "Colour of the stops.",
		position = 21,
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
		position = 22,
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
		position = 23,
		section = stopsSection
	)
	default LineThickness stopThickness()
	{
		return LineThickness.MEDIUM;
	}

	@ConfigItem(
		keyName = "showHeadsUpDisplay",
		name = "Show",
		description = "Show the heads up display on the boat.",
		position = 36,
		section = hudSection
	)
	default boolean showHeadsUpDisplay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "animatedHud",
		name = "Animated",
		description = "Fade the display's lines in and out, and grow<br>and shrink it to fit. Off to show every<br>change at once.",
		position = 37,
		section = hudSection
	)
	default boolean animatedHud()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudPosition",
		name = "Position",
		description = "Where on the boat the display sits.<br><b>Helm</b>: above the helm.<br><b>Bow</b>: above the front of the boat.<br><b>Sails</b>: above the sails.",
		position = 38,
		section = hudSection
	)
	default HudPosition hudPosition()
	{
		return HudPosition.HELM;
	}

	@ConfigItem(
		keyName = "showShoalDepth",
		name = "Depth",
		description = "Show the nearest shoal's depth. Coloured by the<br>Depth colours section.",
		position = 39,
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
		position = 40,
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
		position = 41,
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
		position = 42,
		section = hudSection
	)
	default Color timeLeftColour()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = TIMER_BAR_KEY,
		name = "Timer bar",
		description = "Show the game's bar for the time left at a stop<br>at the bottom of the display, in place of the<br>one over the shoal.",
		position = 43,
		section = hudSection
	)
	default boolean showTimerBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFishInNets",
		name = "Fish in nets",
		description = "Show how many fish are in the nets, and a<br>warning when the hold is full. Hides once the<br>nets are raised and have been empty for a<br>minute. Shows ? when unsure, until the nets are<br>emptied or opened.",
		position = 44,
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
		position = 45,
		section = hudSection
	)
	default Color fishInNetsColour()
	{
		return new Color(140, 200, 255);
	}

	@ConfigItem(
		keyName = "showBaited",
		name = "Baited",
		description = "Show if the shoal is baited, and how much bait<br>is left. Shows ? until the hold is opened to<br>count the bait in it.",
		position = 46,
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
		position = 47,
		section = hudSection
	)
	default Color baitedColour()
	{
		return new Color(242, 180, 210);
	}

	@ConfigItem(
		keyName = "showCargoHold",
		name = "Cargo hold",
		description = "Show how full the hold is and the fish in it<br>while fishing the spots at sea.",
		position = 48,
		section = hudSection
	)
	default boolean showCargoHold()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSpotArrow",
		name = "Fishing spot arrow",
		description = "While sea spot fishing, point an arrow from<br>the boat towards the spot you're fishing.",
		position = 49,
		section = hudSection
	)
	default boolean showSpotArrow()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "spotArrowColour",
		name = "Fishing spot arrow colour",
		description = "Colour of the fishing spot arrow.",
		position = 50,
		section = hudSection
	)
	default Color spotArrowColour()
	{
		return new Color(255, 221, 0);
	}

	@ConfigItem(
		keyName = "shallowDepthColour",
		name = "Shallow",
		description = "Colour of shallow depth.",
		position = 62,
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
		position = 63,
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
		position = 64,
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
		position = 53,
		section = sidePanelSection
	)
	default boolean showNetDepths()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNetButton",
		name = "Depth guide",
		description = "Highlight the raise or lower button needed to<br>reach the target depth.",
		position = 54,
		section = sidePanelSection
	)
	default boolean showNetButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNetCorrect",
		name = "Tick on correct depth",
		description = "Show a tick on the side panel's net when its<br>depth is correct.",
		position = 55,
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
		position = 57,
		section = notificationsSection
	)
	default Notification notifyNetsFull()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyHoldFull",
		name = "Hold full",
		description = "Notify when emptying the nets finds the<br>cargo hold full.",
		position = 58,
		section = notificationsSection
	)
	default Notification notifyHoldFull()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyShoalLeaving",
		name = "Shoal leaving",
		description = "Notify when the nearest shoal is about to<br>leave its stop.",
		position = 59,
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
		position = 60,
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
		position = 29,
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
		position = 30,
		section = areaSection
	)
	default FishableShape fishableAreaShape()
	{
		return FishableShape.SQUARE;
	}

	@ConfigItem(
		keyName = "showFishingPoint",
		name = "Fishing point",
		description = "Show a dot near the bow of the boat. This is<br>the point that must be inside the fishable area<br>to catch fish.",
		position = 31,
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
		description = "Colour of the fishable area and the fishing point.",
		position = 32,
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
		position = 33,
		section = areaSection
	)
	default Color fishableAreaFillColour()
	{
		return new Color(80, 200, 255, 0);
	}

	@ConfigItem(
		keyName = "fishableAreaThickness",
		name = "Thickness",
		description = "Thickness of the fishable area outline.",
		position = 34,
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
		position = 66,
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
		position = 67,
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
		position = 68,
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
		position = 69,
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
		position = 70,
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
		position = 71,
		section = fishSection
	)
	default boolean showMarlin()
	{
		return true;
	}

	@Range(max = 256)
	@ConfigItem(
		keyName = "debugSpotFishDepthRange",
		name = "Fish depth range",
		description = "How much deeper, at most, each fish of the kind<br>being tuned sits than its sink, picked at<br>random for each and kept, 128 to a tile.",
		position = 92,
		section = debugShapeSection
	)
	default int debugSpotFishDepthRange()
	{
		return 0;
	}

	@Range(min = -256, max = 256)
	@ConfigItem(
		keyName = "debugSpotFishSink",
		name = "Fish sink",
		description = "How far the fish at the spots sit below the<br>water, 128 to a tile. Higher is deeper.",
		position = 91,
		section = debugShapeSection
	)
	default int debugSpotFishSink()
	{
		return 6;
	}

	@Range(min = 0, max = 128)
	@ConfigItem(
		keyName = "debugSpotFishBob",
		name = "Fish bob",
		description = "How far the fish being tuned rise as they bob,<br>128 to a tile.",
		position = 93,
		section = debugShapeSection
	)
	default int debugSpotFishBob()
	{
		return 4;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugSpotFishRoll",
		name = "Fish roll",
		description = "How far the fish being tuned is rolled up off<br>its side, in degrees: 0 lies flat, 90 stands up.",
		position = 78,
		section = debugShapeSection
	)
	default int debugSpotFishRoll()
	{
		return 0;
	}

	@Range(min = -90, max = 90)
	@ConfigItem(
		keyName = "debugSpotFishTilt",
		name = "Fish tilt",
		description = "How far the fish at the spots tilt head up,<br>in degrees.",
		position = 79,
		section = debugShapeSection
	)
	default int debugSpotFishTilt()
	{
		return 0;
	}

	@Range(min = 10, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishSize",
		name = "Fish size",
		description = "How big the fish at the spots are, out of<br>the item's own size.",
		position = 88,
		section = debugShapeSection
	)
	default int debugSpotFishSize()
	{
		return 55;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugSpotFishTurn",
		name = "Fish turn",
		description = "How far the fish being tuned is turned to face<br>the way it swims, in degrees.",
		position = 98,
		section = debugShapeSection
	)
	default int debugSpotFishTurn()
	{
		return 126;
	}

	@Range(min = -720, max = 720)
	@ConfigItem(
		keyName = "debugSpotFishSpin",
		name = "Fish spin",
		description = "How fast the fish being tuned spins round as it<br>swims, in degrees a second. 0 doesn't spin.",
		position = 99,
		section = debugShapeSection
	)
	default int debugSpotFishSpin()
	{
		return 200;
	}

	@Range(max = 127)
	@ConfigItem(
		keyName = "debugSpotFishLightest",
		name = "Fish min lightness",
		description = "How light, at least, every face of the fish<br>being tuned is, from 0 to 127 on the game's<br>colour scale. 0 leaves it as it is.",
		position = 100,
		section = debugShapeSection
	)
	default int debugSpotFishLightest()
	{
		return 0;
	}

	@Range(max = 50)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishSizeSpread",
		name = "Fish size variation",
		description = "How much smaller than the size above each<br>fish may be, picked at random. Not used while<br>size variation is off in the code.",
		position = 101,
		section = debugShapeSection
	)
	default int debugSpotFishSizeSpread()
	{
		return 17;
	}

	@Range(max = 50)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishWiggle",
		name = "Fish arm wiggle",
		description = "How far the swept arms of the fish being tuned<br>wave, against its reach.",
		position = 82,
		section = debugReshapeSection
	)
	default int debugSpotFishWiggle()
	{
		return 0;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "debugSpotFishWiggleRate",
		name = "Fish arm wiggle speed",
		description = "How many frames of its arms' wiggle the fish<br>being tuned goes through a second.",
		position = 83,
		section = debugReshapeSection
	)
	default int debugSpotFishWiggleRate()
	{
		return 6;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishUncurl",
		name = "Fish uncurl",
		description = "How far the fish being tuned, if curled round<br>in an arc, is unrolled about the middle of its<br>curl.",
		position = 0,
		section = debugReshapeSection
	)
	default int debugSpotFishUncurl()
	{
		return 0;
	}

	@Range(max = 100)
	@ConfigItem(
		keyName = "debugSpotFishCurlCentre",
		name = "Fish curl centre",
		description = "Moves the middle of the curl of the fish being<br>tuned, found from its shape, towards its arch<br>above 50 or away below, 128 to a tile.",
		position = 0,
		section = debugReshapeSection
	)
	default int debugSpotFishCurlCentre()
	{
		return 50;
	}

	@Range(max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishFinSize",
		name = "Fish fin size",
		description = "How big the loose fins of the fish being tuned<br>are made as it is unrolled, round where each<br>meets the body.",
		position = 0,
		section = debugReshapeSection
	)
	default int debugSpotFishFinSize()
	{
		return 100;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishStraighten",
		name = "Fish straighten",
		description = "How far the fish being tuned has a bend along<br>its length, as a leaping fish, straightened out.",
		position = 84,
		section = debugReshapeSection
	)
	default int debugSpotFishStraighten()
	{
		return 0;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishJoint",
		name = "Fish joint",
		description = "Where along the fish being tuned it bends, its<br>shorter side swinging round by the tail bend.",
		position = 85,
		section = debugReshapeSection
	)
	default int debugSpotFishJoint()
	{
		return 50;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugSpotFishBend",
		name = "Fish tail bend",
		description = "How many degrees the fish being tuned has the<br>part beyond its joint swung round, to<br>straighten a bent tail.",
		position = 86,
		section = debugReshapeSection
	)
	default int debugSpotFishBend()
	{
		return 0;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishJoint2",
		name = "Fish second joint",
		description = "Where along the fish being tuned it bends a<br>second time, after the first, its shorter side<br>swinging round by the second bend.",
		position = 86,
		section = debugReshapeSection
	)
	default int debugSpotFishJoint2()
	{
		return 80;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugSpotFishBend2",
		name = "Fish second bend",
		description = "How many degrees the fish being tuned has the<br>part beyond its second joint swung round, as<br>for a tail still bent.",
		position = 86,
		section = debugReshapeSection
	)
	default int debugSpotFishBend2()
	{
		return 0;
	}

	@Range(max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishTailSize",
		name = "Fish tail size",
		description = "How big the part of the fish being tuned<br>beyond its second joint is made, round the<br>joint.",
		position = 86,
		section = debugReshapeSection
	)
	default int debugSpotFishTailSize()
	{
		return 100;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishSweep",
		name = "Fish sweep back",
		description = "How far the arms of the fish being tuned, if<br>they reach out all round, are swept back<br>behind it, as an octopus trails its arms.",
		position = 80,
		section = debugReshapeSection
	)
	default int debugSpotFishSweep()
	{
		return 0;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishSweepBody",
		name = "Fish sweep body",
		description = "How much of the middle of the fish being tuned,<br>against its reach, is the body its arms reach<br>from, left as it is.",
		position = 81,
		section = debugReshapeSection
	)
	default int debugSpotFishSweepBody()
	{
		return 35;
	}

	@Range(min = 25, max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishStretch",
		name = "Fish stretch",
		description = "How long the fish being tuned is made along its<br>length, against how long it is.",
		position = 87,
		section = debugReshapeSection
	)
	default int debugSpotFishStretch()
	{
		return 100;
	}

	@Range(max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishWag",
		name = "Fish wag",
		description = "How far the fish being tuned wags its tail,<br>against the usual. 0 doesn't wag.",
		position = 96,
		section = debugShapeSection
	)
	default int debugSpotFishWag()
	{
		return 100;
	}

	@Range(max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishTip",
		name = "Fish tipping",
		description = "How far the fish being tuned tips nose up and<br>down as it bobs and dips. 0 stays level.",
		position = 94,
		section = debugShapeSection
	)
	default int debugSpotFishTip()
	{
		return 100;
	}

	@Range(min = 10, max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishPace",
		name = "Fish speed",
		description = "How fast the fish being tuned swims round,<br>against its lane's speed.",
		position = 89,
		section = debugShapeSection
	)
	default int debugSpotFishPace()
	{
		return 100;
	}

	@Range(max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishSurge",
		name = "Fish surge",
		description = "How much the fish being tuned speeds up and<br>slows down as it swims, against its lane's.",
		position = 90,
		section = debugShapeSection
	)
	default int debugSpotFishSurge()
	{
		return 100;
	}

	@Range(min = -256, max = 256)
	@ConfigItem(
		keyName = "debugSpotFishTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle, towards its head,<br>the fish being tuned tips about as it bobs<br>and dips, 128 to a tile.",
		position = 95,
		section = debugShapeSection
	)
	default int debugSpotFishTipPivot()
	{
		return 0;
	}

	@Range(min = -128, max = 128)
	@ConfigItem(
		keyName = "debugSpotFishPivot",
		name = "Fish wag pivot",
		description = "How far ahead of the middle of the fish at the<br>spots they turn as they wag, 128 to a tile.",
		position = 97,
		section = debugShapeSection
	)
	default int debugSpotFishPivot()
	{
		return 16;
	}

	@Range(max = 40)
	@ConfigItem(
		keyName = "debugSpotFishRoom",
		name = "Fish make room",
		description = "How far, at most, the fish at the spots ease<br>away from each other when near, 128 to a tile.",
		position = 109,
		section = debugRoomSection
	)
	default int debugSpotFishRoom()
	{
		return 40;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(
		keyName = "debugSpotFishRoomRange",
		name = "Fish make room range",
		description = "How near the fish at the spots have to be to<br>ease away from each other, 128 to a tile.",
		position = 110,
		section = debugRoomSection
	)
	default int debugSpotFishRoomRange()
	{
		return 60;
	}

	@Range(min = 1, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugSpotFishRoomEase",
		name = "Fish make room ease",
		description = "How much of the way to where they want to be<br>the fish at the spots ease each client tick<br>(20 ms) while making room. Higher is quicker.",
		position = 111,
		section = debugRoomSection
	)
	default int debugSpotFishRoomEase()
	{
		return 30;
	}

	@Range(min = 1, max = 300)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "debugSpotFishDipEvery",
		name = "Fish dip every",
		description = "How often, on average, each fish at the spots<br>dips deeper.",
		position = 112,
		section = debugDipSection
	)
	default int debugSpotFishDipEvery()
	{
		return 10;
	}

	@Range(max = 256)
	@ConfigItem(
		keyName = "debugSpotFishDipDepth",
		name = "Fish dip depth",
		description = "How much deeper the fish at the spots dip, at<br>most, 128 to a tile.",
		position = 113,
		section = debugDipSection
	)
	default int debugSpotFishDipDepth()
	{
		return 10;
	}

	@Range(min = 100, max = 10000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugSpotFishDipLength",
		name = "Fish dip length",
		description = "How long a dip takes the fish at the spots,<br>down and back up.",
		position = 114,
		section = debugDipSection
	)
	default int debugSpotFishDipLength()
	{
		return 1500;
	}



	@ConfigItem(
		keyName = "debugMiddles",
		name = "Spot middles",
		description = "Mark the middle each spot's fish swim round.",
		position = 72,
		section = debugDrawingSection
	)
	default boolean debugMiddles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugLanes",
		name = "Lanes",
		description = "Draw each spot's lanes as rings.",
		position = 73,
		section = debugDrawingSection
	)
	default boolean debugLanes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugDots",
		name = "Fish dots",
		description = "Dot each fish: green, red changing lanes,<br>ringed yellow making room, blue dipping.",
		position = 74,
		section = debugDrawingSection
	)
	default boolean debugDots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugPivots",
		name = "Wag points",
		description = "Draw a line from each fish's middle to the<br>point it wags about.",
		position = 75,
		section = debugDrawingSection
	)
	default boolean debugPivots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugTipPivots",
		name = "Tip points",
		description = "Draw a line from each fish's middle to the<br>point it tips about as it bobs and dips.",
		position = 76,
		section = debugDrawingSection
	)
	default boolean debugTipPivots()
	{
		return true;
	}

	@Range(max = 512)
	@ConfigItem(
		keyName = "debugSpotFishSharkRoom",
		name = "Room for sharks",
		description = "How far, at most, every fish eases away from a<br>shark near it, 128 to a tile.",
		position = 112,
		section = debugRoomSection
	)
	default int debugSpotFishSharkRoom()
	{
		return 50;
	}

	@Range(max = 1024)
	@ConfigItem(
		keyName = "debugSpotFishSharkRoomRange",
		name = "Room for sharks range",
		description = "How near a shark comes, 128 to a tile, before<br>every fish starts easing away from it, more the<br>nearer it is.",
		position = 113,
		section = debugRoomSection
	)
	default int debugSpotFishSharkRoomRange()
	{
		return 160;
	}

	@Range(min = 2, max = 16)
	@ConfigItem(
		keyName = "debugSpotFishLanes",
		name = "Fish lanes",
		description = "How many lanes the fish at the spot being<br>tuned swim in.",
		position = 102,
		section = debugCrowdSection
	)
	default int debugSpotFishLanes()
	{
		return 10;
	}

	@Range(min = 1, max = 48)
	@ConfigItem(
		keyName = "debugSpotFishCount",
		name = "Fish count",
		description = "How many fish swim at the spot being tuned.",
		position = 103,
		section = debugCrowdSection
	)
	default int debugSpotFishCount()
	{
		return 20;
	}

	@Range(min = 4, max = 64)
	@ConfigItem(
		keyName = "debugSpotFishLaneWidth",
		name = "Fish lane width",
		description = "How far apart the lanes at the spot being tuned<br>are, 128 to a tile. The innermost lane moves to<br>fit, and the gaps the fish keep follow it.",
		position = 104,
		section = debugCrowdSection
	)
	default int debugSpotFishLaneWidth()
	{
		return 45;
	}

	@Range(min = 64, max = 320)
	@ConfigItem(
		keyName = "debugSpotFishShoalSize",
		name = "Fish shoal size",
		description = "How far out the outermost lane at the spot<br>being tuned is, 128 to a tile. The spot is 320<br>out to its edge.",
		position = 105,
		section = debugCrowdSection
	)
	default int debugSpotFishShoalSize()
	{
		return 224;
	}

	@Range(min = 1, max = 6)
	@ConfigItem(
		keyName = "debugSpotFishSharkLanes",
		name = "Shark lanes",
		description = "How many lanes the ring of sharks outside the<br>shoal at the spot being tuned has.",
		position = 106,
		section = debugCrowdSection
	)
	default int debugSpotFishSharkLanes()
	{
		return 1;
	}

	@Range(min = -256, max = 256)
	@ConfigItem(
		keyName = "debugSpotFishSharkGap",
		name = "Shark ring gap",
		description = "How far outside the shoal's outermost lane the<br>sharks' first lane is, 128 to a tile. Below 0 it<br>is inside, overlapping the shoal.",
		position = 107,
		section = debugCrowdSection
	)
	default int debugSpotFishSharkGap()
	{
		return 64;
	}

	@Range(min = 8, max = 256)
	@ConfigItem(
		keyName = "debugSpotFishSharkWidth",
		name = "Shark lane width",
		description = "How far apart the sharks' lanes are, 128 to a<br>tile.",
		position = 108,
		section = debugCrowdSection
	)
	default int debugSpotFishSharkWidth()
	{
		return 64;
	}

	@Range(min = 10, max = 2000)
	@ConfigItem(
		keyName = "debugSpotFishRoomSpeed",
		name = "Fish most room speed",
		description = "How fast, at most, every fish eases aside as it<br>makes room, 128 to a tile a second.",
		position = 114,
		section = debugRoomSection
	)
	default int debugSpotFishRoomSpeed()
	{
		return 300;
	}

	@Range(min = 10, max = 2000)
	@ConfigItem(
		keyName = "debugSpotFishSharkRoomSpeed",
		name = "Room for sharks speed",
		description = "How fast, at most, every fish eases aside for a<br>shark, 128 to a tile a second.",
		position = 115,
		section = debugRoomSection
	)
	default int debugSpotFishSharkRoomSpeed()
	{
		return 60;
	}

}
