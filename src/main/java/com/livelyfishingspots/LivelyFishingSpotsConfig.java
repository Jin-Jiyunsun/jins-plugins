package com.livelyfishingspots;

import java.util.Set;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(LivelyFishingSpotsConfig.GROUP)
public interface LivelyFishingSpotsConfig extends Config
{
	String GROUP = "lively-fishing-spots";
	// The keys of the river settings whose change starts the river shoals in sight again, while tuning.
	Set<String> REBUILD = Set.of("debugRiverCircleLanes", "debugRiverCircleLaneSpacing", "debugRiverCircleSize", "debugRiverCircleMost", "debugRiverTravelSpacing", "debugRiverSpread", "debugRiverCircleOffset",
		"debugRiverBodyMinutes", "debugRiverDeepLeast", "debugRiverDeepMost", "debugRiverRingManual",
		"debugRiverRingEast", "debugRiverRingNorth");

	@ConfigItem(
		keyName = "riverSwimming",
		name = "River fish",
		description = "How river fish swim down the river:<br>Schooled in groups, or Random,<br>each on its own",
		position = 0
	)
	default RiverSwimming riverSwimming()
	{
		return RiverSwimming.SCHOOLED;
	}

	@Range(min = 25, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "riverFishAmount",
		name = "River fish amount",
		description = "How many fish swim down the rivers:<br>lower spreads them further apart",
		position = 1
	)
	default int riverFishAmount()
	{
		return 100;
	}

	@Range(min = 25, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "lakeFishAmount",
		name = "Lake fish amount",
		description = "How many fish swim in the lakes",
		position = 2
	)
	default int lakeFishAmount()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "riverDeep",
		name = "Deep river fish (117 HD)",
		description = "With 117 HD's see-through water,<br>river fish swim deeper and rise<br>to circle the spot being fished",
		position = 3
	)
	default boolean riverDeep()
	{
		return true;
	}

	@ConfigSection(
		name = "Debug: rivers",
		description = "Temporary settings for setting up the river spots",
		position = 99,
		closedByDefault = true
	)
	String debugRivers = "debugRivers";

	@ConfigItem(
		keyName = "debugDraw",
		name = "Show debug drawing",
		description = "Draws the parts of the river and<br>lake debug ticked below",
		section = debugRivers,
		position = 0
	)
	default boolean debugDraw()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugRiverDrawBanks",
		name = "Draw banks",
		description = "Orange dots along the banks",
		section = debugRivers,
		position = 1
	)
	default boolean debugRiverDrawBanks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawPath",
		name = "Draw path",
		description = "Cyan line the river fish follow,<br>with a dot at its end",
		section = debugRivers,
		position = 2
	)
	default boolean debugRiverDrawPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawRoom",
		name = "Draw room",
		description = "Teal lines: the room either side<br>of a river's path",
		section = debugRivers,
		position = 3
	)
	default boolean debugRiverDrawRoom()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawSpawns",
		name = "Draw lake spawns",
		description = "Yellow rings at a lake's<br>spawn points",
		section = debugRivers,
		position = 4
	)
	default boolean debugRiverDrawSpawns()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawRings",
		name = "Draw rings",
		description = "Green lanes and middle of<br>each spot's ring",
		section = debugRivers,
		position = 5
	)
	default boolean debugRiverDrawRings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawDecisions",
		name = "Draw join lines",
		description = "Rivers: magenta line where passing<br>fish decide to join a ring, and a<br>green line through the ring",
		section = debugRivers,
		position = 6
	)
	default boolean debugRiverDrawDecisions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawKeepOut",
		name = "Draw keep-out",
		description = "Red ring that fish not joining<br>stay outside",
		section = debugRivers,
		position = 7
	)
	default boolean debugRiverDrawKeepOut()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawSpotIds",
		name = "Draw spot ids",
		description = "Each fishing spot's NPC id",
		section = debugRivers,
		position = 8
	)
	default boolean debugRiverDrawSpotIds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawFish",
		name = "Draw fish steering",
		description = "Arrow from each fish to where it's<br>steering: white swimming, green<br>circling, yellow growing, red shrinking",
		section = debugRivers,
		position = 9
	)
	default boolean debugRiverDrawFish()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawSchools",
		name = "Draw school headings",
		description = "Blue arrow from each school's<br>middle along its heading",
		section = debugRivers,
		position = 10
	)
	default boolean debugRiverDrawSchools()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawBounds",
		name = "Draw school bounds",
		description = "Blue outline round each school",
		section = debugRivers,
		position = 11
	)
	default boolean debugRiverDrawBounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawPoints",
		name = "Draw route points",
		description = "Yellow tiles numbered in order: each<br>route's start, waypoints and end;<br>magenta while picking one",
		section = debugRivers,
		position = 12
	)
	default boolean debugRiverDrawPoints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawCount",
		name = "Draw fish count",
		description = "A panel at the top left counting<br>the fish loaded: sea, each river<br>and lake, and models made",
		section = debugRivers,
		position = 13
	)
	default boolean debugRiverDrawCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawBlockers",
		name = "Draw fish blockers",
		description = "Red outlines on the tiles fish<br>treat as land by hand",
		section = debugRivers,
		position = 14
	)
	default boolean debugRiverDrawBlockers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawBoxes",
		name = "Draw map boxes",
		description = "Purple outline round the tiles mapped<br>for each river and lake: a band along<br>the route, or round the spawn points",
		section = debugRivers,
		position = 15
	)
	default boolean debugRiverDrawBoxes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugPick",
		name = "Pick routes",
		description = "Adds River start, waypoints and end,<br>Lake spawn, Clear lake and Fish blocker<br>to the right-click menu on tiles;<br>each pick is logged",
		section = debugRivers,
		position = 16
	)
	default boolean debugPick()
	{
		return false;
	}

	@ConfigSection(
		name = "Debug: river fish shares",
		description = "Temporary tuning of which fish swim<br>down the rivers, for fish spawned<br>from now on",
		position = 106,
		closedByDefault = true
	)
	String debugRiverShares = "debugRiverShares";

	@ConfigSection(
		name = "Debug: trout look",
		description = "Temporary tuning of the trout's look and dips",
		position = 100,
		closedByDefault = true
	)
	String debugLookTrout = "debugLookTrout";

	@ConfigSection(
		name = "Debug: salmon look",
		description = "Temporary tuning of the salmon's look and dips",
		position = 101,
		closedByDefault = true
	)
	String debugLookSalmon = "debugLookSalmon";

	@ConfigSection(
		name = "Debug: pike look",
		description = "Temporary tuning of the pike's look and dips",
		position = 102,
		closedByDefault = true
	)
	String debugLookPike = "debugLookPike";

	@ConfigSection(
		name = "Debug: rainbow fish look",
		description = "Temporary tuning of the rainbow fish's look and dips",
		position = 103,
		closedByDefault = true
	)
	String debugLookRainbow = "debugLookRainbow";

	@ConfigSection(
		name = "Debug: river bobs and depth",
		description = "Temporary tuning of bob timing, all river fish",
		position = 104,
		closedByDefault = true
	)
	String debugRiverDips = "debugRiverDips";

	@ConfigSection(
		name = "Debug: river lanes",
		description = "Temporary tuning of the circles and spacing",
		position = 105,
		closedByDefault = true
	)
	String debugRiverLanes = "debugRiverLanes";

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "debugRiverCircleLanes",
		name = "Lanes",
		description = "Lanes round each circle",
		section = debugRiverLanes,
		position = 0
	)
	default int debugRiverCircleLanes()
	{
		return 2;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugRiverCircleLaneSpacing",
		name = "Lane width",
		description = "Local units between<br>a circle's lanes",
		section = debugRiverLanes,
		position = 1
	)
	default int debugRiverCircleLaneSpacing()
	{
		return 16;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugRiverCircleSize",
		name = "Circle size",
		description = "Outer lane radius at most,<br>local units",
		section = debugRiverLanes,
		position = 2
	)
	default int debugRiverCircleSize()
	{
		return 58;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "debugRiverCircleMost",
		name = "Fish count",
		description = "The most fish circling<br>one spot",
		section = debugRiverLanes,
		position = 3
	)
	default int debugRiverCircleMost()
	{
		return 7;
	}

	@Range(min = 1, max = 5000)
	@ConfigItem(
		keyName = "debugRiverTravelSpacing",
		name = "Fish spacing",
		description = "Local units between fish<br>down the river, on average",
		section = debugRiverLanes,
		position = 4
	)
	default int debugRiverTravelSpacing()
	{
		return 100;
	}

	@Range(min = -512, max = 512)
	@ConfigItem(
		keyName = "debugRiverCircleOffset",
		name = "Circle offset",
		description = "How far each circle's middle is<br>from its spot, away from the<br>nearest bank, local units",
		section = debugRiverLanes,
		position = 5
	)
	default int debugRiverCircleOffset()
	{
		return 28;
	}

	@Range(min = 1, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverInnerLaneSpeed",
		name = "Inner lane speed",
		description = "How fast a circle's innermost lane<br>swims, of the outermost's",
		section = debugRiverLanes,
		position = 7
	)
	default int debugRiverInnerLaneSpeed()
	{
		return 40;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverSpread",
		name = "Fish spread",
		description = "How far from the line each fish's<br>own line is, at most, of the room",
		section = debugRiverLanes,
		position = 8
	)
	default int debugRiverSpread()
	{
		return 40;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverWander",
		name = "Fish wander",
		description = "How far each fish drifts either<br>side of its own line, of the room",
		section = debugRiverLanes,
		position = 9
	)
	default int debugRiverWander()
	{
		return 25;
	}

	@Range(min = 1, max = 1000)
	@ConfigItem(
		keyName = "debugRiverBobCycles",
		name = "Fish bob time",
		description = "Client ticks a bob takes,<br>down and back up (50 a second)",
		section = debugRiverDips,
		position = 3
	)
	default int debugRiverBobCycles()
	{
		return 60;
	}

	@Range(min = 0, max = 1000)
	@ConfigItem(
		keyName = "debugRiverBobRestCycles",
		name = "Fish bob rest",
		description = "Client ticks it holds still<br>between bobs (50 a second)",
		section = debugRiverDips,
		position = 4
	)
	default int debugRiverBobRestCycles()
	{
		return 30;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugRiverCircleClearance",
		name = "Spot avoid gap",
		description = "How far outside a circle passing<br>fish keep, local units; drawn red",
		section = debugRiverLanes,
		position = 10
	)
	default int debugRiverCircleClearance()
	{
		return 11;
	}

	@Range(min = 1, max = 3600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "debugRiverScatterSeconds",
		name = "Scatter every",
		description = "How long, on average, a big group<br>swims before it scatters",
		section = debugRiverLanes,
		position = 12
	)
	default int debugRiverScatterSeconds()
	{
		return 66;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookRainbow,
		position = 0
	)
	default int debugLookRainbowSink()
	{
		return 6;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookRainbow,
		position = 1
	)
	default int debugLookRainbowRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookRainbowRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookRainbow,
		position = 2
	)
	default int debugLookRainbowRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookRainbowTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookRainbow,
		position = 3
	)
	default int debugLookRainbowTilt()
	{
		return 0;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookRainbow,
		position = 4
	)
	default int debugLookRainbowSize()
	{
		return 17;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookRainbowTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookRainbow,
		position = 5
	)
	default int debugLookRainbowTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookRainbowLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookRainbow,
		position = 6
	)
	default int debugLookRainbowLightest()
	{
		return 7;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookRainbow,
		position = 7
	)
	default int debugLookRainbowWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookRainbow,
		position = 8
	)
	default int debugLookRainbowTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookRainbow,
		position = 9
	)
	default int debugLookRainbowPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookRainbow,
		position = 10
	)
	default int debugLookRainbowSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookRainbow,
		position = 11
	)
	default int debugLookRainbowTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookRainbow,
		position = 12
	)
	default int debugLookRainbowPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookRainbowDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookRainbow,
		position = 20
	)
	default int debugLookRainbowDipEvery()
	{
		return 3;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRainbowDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookRainbow,
		position = 21
	)
	default int debugLookRainbowDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookRainbowDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookRainbow,
		position = 22
	)
	default int debugLookRainbowDipMillis()
	{
		return 1500;
	}

	@Range(min = 5, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookRainbowThickness",
		name = "Fish thickness",
		description = "How thick the rainbow fish is,<br>of its doubled model",
		section = debugLookRainbow,
		position = 30
	)
	default int debugLookRainbowThickness()
	{
		return 50;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookTrout,
		position = 0
	)
	default int debugLookTroutSink()
	{
		return 8;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookTrout,
		position = 1
	)
	default int debugLookTroutRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookTroutRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookTrout,
		position = 2
	)
	default int debugLookTroutRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookTroutTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookTrout,
		position = 3
	)
	default int debugLookTroutTilt()
	{
		return 0;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookTrout,
		position = 4
	)
	default int debugLookTroutSize()
	{
		return 30;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookTroutTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookTrout,
		position = 5
	)
	default int debugLookTroutTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookTroutLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookTrout,
		position = 6
	)
	default int debugLookTroutLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookTrout,
		position = 7
	)
	default int debugLookTroutWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookTrout,
		position = 8
	)
	default int debugLookTroutTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookTrout,
		position = 9
	)
	default int debugLookTroutPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookTrout,
		position = 10
	)
	default int debugLookTroutSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookTrout,
		position = 11
	)
	default int debugLookTroutTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookTrout,
		position = 12
	)
	default int debugLookTroutPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookTroutDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookTrout,
		position = 20
	)
	default int debugLookTroutDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookTroutDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookTrout,
		position = 21
	)
	default int debugLookTroutDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookTroutDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookTrout,
		position = 22
	)
	default int debugLookTroutDipMillis()
	{
		return 1500;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookSalmon,
		position = 0
	)
	default int debugLookSalmonSink()
	{
		return 8;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookSalmon,
		position = 1
	)
	default int debugLookSalmonRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookSalmonRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookSalmon,
		position = 2
	)
	default int debugLookSalmonRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookSalmonTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookSalmon,
		position = 3
	)
	default int debugLookSalmonTilt()
	{
		return 0;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookSalmon,
		position = 4
	)
	default int debugLookSalmonSize()
	{
		return 30;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookSalmonTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookSalmon,
		position = 5
	)
	default int debugLookSalmonTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookSalmonLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookSalmon,
		position = 6
	)
	default int debugLookSalmonLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookSalmon,
		position = 7
	)
	default int debugLookSalmonWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookSalmon,
		position = 8
	)
	default int debugLookSalmonTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookSalmon,
		position = 9
	)
	default int debugLookSalmonPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookSalmon,
		position = 10
	)
	default int debugLookSalmonSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookSalmon,
		position = 11
	)
	default int debugLookSalmonTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookSalmon,
		position = 12
	)
	default int debugLookSalmonPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookSalmonDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookSalmon,
		position = 20
	)
	default int debugLookSalmonDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSalmonDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookSalmon,
		position = 21
	)
	default int debugLookSalmonDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookSalmonDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookSalmon,
		position = 22
	)
	default int debugLookSalmonDipMillis()
	{
		return 1500;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookPike,
		position = 0
	)
	default int debugLookPikeSink()
	{
		return 8;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookPike,
		position = 1
	)
	default int debugLookPikeRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookPikeRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookPike,
		position = 2
	)
	default int debugLookPikeRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookPikeTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookPike,
		position = 3
	)
	default int debugLookPikeTilt()
	{
		return 0;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookPike,
		position = 4
	)
	default int debugLookPikeSize()
	{
		return 30;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookPikeTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookPike,
		position = 5
	)
	default int debugLookPikeTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookPikeLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookPike,
		position = 6
	)
	default int debugLookPikeLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookPike,
		position = 7
	)
	default int debugLookPikeWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookPike,
		position = 8
	)
	default int debugLookPikeTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookPikePace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookPike,
		position = 9
	)
	default int debugLookPikePace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookPike,
		position = 10
	)
	default int debugLookPikeSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookPike,
		position = 11
	)
	default int debugLookPikeTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookPikePivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookPike,
		position = 12
	)
	default int debugLookPikePivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookPikeDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookPike,
		position = 20
	)
	default int debugLookPikeDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookPikeDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookPike,
		position = 21
	)
	default int debugLookPikeDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookPikeDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookPike,
		position = 22
	)
	default int debugLookPikeDipMillis()
	{
		return 1500;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "debugRiverShareTrout",
		name = "Trout",
		description = "How many trout, against<br>salmon and pike",
		section = debugRiverShares,
		position = 0
	)
	default int debugRiverShareTrout()
	{
		return 34;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "debugRiverShareSalmon",
		name = "Salmon",
		description = "How many salmon, against<br>trout and pike",
		section = debugRiverShares,
		position = 1
	)
	default int debugRiverShareSalmon()
	{
		return 33;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "debugRiverSharePike",
		name = "Pike",
		description = "How many pike, against<br>trout and salmon",
		section = debugRiverShares,
		position = 2
	)
	default int debugRiverSharePike()
	{
		return 33;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverShareRainbow",
		name = "Rainbow fish groups",
		description = "Share of groups that are<br>rainbow fish; never solo",
		section = debugRiverShares,
		position = 3
	)
	default int debugRiverShareRainbow()
	{
		return 7;
	}

	@Range(min = 1, max = 600)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "debugRiverBodyMinutes",
		name = "Dead body every",
		description = "How long, on average, between<br>dead bodies drifting down a river",
		section = debugRiverShares,
		position = 4
	)
	default int debugRiverBodyMinutes()
	{
		return 180;
	}

	@Range(min = 10, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLakeSpeed",
		name = "Lake speed",
		description = "How fast lake fish cruise,<br>of river fish speed",
		section = debugRiverLanes,
		position = 13
	)
	default int debugRiverLakeSpeed()
	{
		return 65;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLakeJoinChance",
		name = "Lake join chance",
		description = "Chance a lake fish passing a spot<br>being fished joins its ring;<br>rivers stay at 50%",
		section = debugRiverLanes,
		position = 14
	)
	default int debugRiverLakeJoinChance()
	{
		return 70;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(
		keyName = "debugRiverDeepLeast",
		name = "Least depth (117 HD)",
		description = "Shallowest a fish swims under the<br>surface with 117 HD, local units",
		section = debugRiverDips,
		position = 5
	)
	default int debugRiverDeepLeast()
	{
		return 12;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(
		keyName = "debugRiverDeepMost",
		name = "Most depth (117 HD)",
		description = "Deepest a river fish swims under the<br>surface with 117 HD, local units;<br>each fish picks a depth between",
		section = debugRiverDips,
		position = 6
	)
	default int debugRiverDeepMost()
	{
		return 96;
	}

	@Range(min = 50, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookRainbowStretch",
		name = "Fish stretch",
		description = "How long the rainbow fish is,<br>of its model's length",
		section = debugLookRainbow,
		position = 31
	)
	default int debugLookRainbowStretch()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "debugRiverRingManual",
		name = "Manual ring offset",
		description = "Every ring uses the offset below<br>instead of moving away from the bank;<br>each is logged to lock in per place",
		section = debugRiverLanes,
		position = 15
	)
	default boolean debugRiverRingManual()
	{
		return false;
	}

	@Range(min = -512, max = 512)
	@ConfigItem(
		keyName = "debugRiverRingEast",
		name = "Ring offset east",
		description = "How far each ring's middle is east<br>of its spot, local units;<br>west when less than 0",
		section = debugRiverLanes,
		position = 16
	)
	default int debugRiverRingEast()
	{
		return 0;
	}

	@Range(min = -512, max = 512)
	@ConfigItem(
		keyName = "debugRiverRingNorth",
		name = "Ring offset north",
		description = "How far each ring's middle is north<br>of its spot, local units;<br>south when less than 0",
		section = debugRiverLanes,
		position = 17
	)
	default int debugRiverRingNorth()
	{
		return 0;
	}

	@Range(min = 12, max = 60)
	@ConfigItem(
		keyName = "debugRiverFishRange",
		name = "Fish range",
		description = "Tiles along a river either side of you<br>that keep fish; rivers and lakes start<br>4 tiles further out, and go 16 further",
		section = debugRiverLanes,
		position = 18
	)
	default int debugRiverFishRange()
	{
		return 28;
	}
}
