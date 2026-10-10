package com.livelyfishingspots;

import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * The debug plugin's settings: tuning spinners, debug drawing and baking. In the plugin's own config group, so they're
 * kept as they were before the debug plugin was split out.
 */
@ConfigGroup(LivelyFishingSpotsConfig.GROUP)
public interface LivelyFishingSpotsDebugConfig extends Config
{
	// The keys of the river settings whose change starts the river shoals in sight again, while tuning.
	Set<String> REBUILD = Set.of("debugRiverCircleLanes", "debugRiverCircleLaneSpacing", "debugRiverCircleSize", "debugRiverCircleMost", "debugRiverTravelSpacing", "debugRiverSpread", "debugRiverCircleOffset",
		"debugRiverBodyMinutes", "debugRiverDeepLeast", "debugRiverDeepMost", "debugRiverRingManual",
		"debugRiverRingEast", "debugRiverRingNorth");

	@ConfigSection(
		name = "Debug: drawing and baking",
		description = "Debug drawing, the picking menu<br>and baking water bodies",
		position = 99
	)
	String debugRivers = "debugRivers";

	@ConfigItem(
		keyName = "debugDraw",
		name = "Debug drawing",
		description = "Draws the parts ticked below on the<br>rivers and lakes near you, and<br>the debug panel",
		section = debugRivers,
		position = 0
	)
	default boolean debugDraw()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugRiverDrawCount",
		name = "Debug panel",
		description = "Top left: fish loaded (sea, and each<br>river and lake: now / most kept),<br>models made, mapping times, and the<br>ms each part takes a client tick",
		section = debugRivers,
		position = 1
	)
	default boolean debugRiverDrawCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawRange",
		name = "Ranges",
		description = "Cyan square round you: rivers are<br>mapped inside it. Purple: each river<br>and lake's mapped box. Rivers: fish<br>are kept between the orange lines<br>and drawn between the yellow ones",
		section = debugRivers,
		position = 2
	)
	default boolean debugRiverDrawRange()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawBanks",
		name = "Banks",
		description = "Orange dots on the water's edge,<br>as baked",
		section = debugRivers,
		position = 3
	)
	default boolean debugRiverDrawBanks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawPath",
		name = "Path and room",
		description = "Rivers: the cyan line fish follow,<br>a dot at its downstream end, and<br>teal lines at the room either side",
		section = debugRivers,
		position = 4
	)
	default boolean debugRiverDrawPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawShade",
		name = "Room shading",
		description = "Rivers: hatches the room fish<br>may swim in, between the<br>teal room lines",
		section = debugRivers,
		position = 5
	)
	default boolean debugRiverDrawShade()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawRings",
		name = "Spots",
		description = "At each spot: its ring's lanes and<br>middle (green), the red ring passing<br>fish keep outside, and its NPC id.<br>Rivers: where passing fish decide<br>to join (magenta), and the ring's<br>line across the river (green)",
		section = debugRivers,
		position = 6
	)
	default boolean debugRiverDrawRings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawFish",
		name = "Fish steering",
		description = "Arrow from each fish to where it's<br>steering: white swimming, green<br>circling, yellow growing or coming<br>into sight, red shrinking or going<br>out of sight, grey not drawn",
		section = debugRivers,
		position = 7
	)
	default boolean debugRiverDrawFish()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawSchools",
		name = "Schools",
		description = "Blue outline round each school,<br>and an arrow from its middle<br>along its heading",
		section = debugRivers,
		position = 8
	)
	default boolean debugRiverDrawSchools()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawPivots",
		name = "Wag and tip points",
		description = "Line from each fish's middle to<br>the point it wags about (orange)<br>and tips about (magenta)",
		section = debugRivers,
		position = 9
	)
	default boolean debugRiverDrawPivots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugRiverDrawPoints",
		name = "Picked points",
		description = "Yellow tiles numbered in order: each<br>route's start, waypoints and end<br>(magenta while picking one). Yellow<br>rings at lake spawns. Red outlines<br>on fish blockers",
		section = debugRivers,
		position = 10
	)
	default boolean debugRiverDrawPoints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugPick",
		name = "Picking menu",
		description = "Adds River and Fork start, waypoint<br>and end, Lake spawn, Clear lake,<br>Fish blocker and Fish allower to<br>the right-click menu on tiles. A fork<br>is a side channel of a baked river",
		section = debugRivers,
		position = 11
	)
	default boolean debugPick()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugRiverBake",
		name = "Bake water bodies",
		description = "Records the water round you near<br>each river and lake as you walk;<br>turning it off saves them to<br>.runelite/plugin-data/<br>lively-fishing-spots-debug/baked.<br>Only baked rivers and lakes have fish",
		section = debugRivers,
		position = 12
	)
	default boolean debugRiverBake()
	{
		return false;
	}

	@ConfigSection(
		name = "Debug: fish shares",
		description = "Temporary tuning of which fish swim<br>down the rivers, for fish spawned<br>from now on",
		position = 100,
		closedByDefault = true
	)
	String debugRiverShares = "debugRiverShares";

	@ConfigSection(
		name = "Debug: splashes",
		description = "Temporary tuning of the splashes<br>leaping fish make",
		position = 114,
		closedByDefault = true
	)
	String debugSplashes = "debugSplashes";

	@ConfigSection(
		name = "Debug: dead bodies",
		description = "Temporary tuning of the dead bodies<br>drifting down the rivers",
		position = 115,
		closedByDefault = true
	)
	String debugBodies = "debugBodies";

	@ConfigSection(
		name = "Debug: lakes",
		description = "Temporary tuning of the lake fish",
		position = 111,
		closedByDefault = true
	)
	String debugLakes = "debugLakes";

	@ConfigSection(
		name = "Debug: range and performance",
		description = "Temporary tuning of how far from<br>you fish are kept, and how often<br>they decide",
		position = 116,
		closedByDefault = true
	)
	String debugRange = "debugRange";

	@ConfigSection(
		name = "Debug: schools",
		description = "Temporary tuning of the schools<br>of river and lake fish",
		position = 110,
		closedByDefault = true
	)
	String debugSchools = "debugSchools";

	@ConfigSection(
		name = "Debug: trout look",
		description = "Temporary tuning of the trout's look and dips",
		position = 101,
		closedByDefault = true
	)
	String debugLookTrout = "debugLookTrout";

	@ConfigSection(
		name = "Debug: salmon look",
		description = "Temporary tuning of the salmon's look and dips",
		position = 102,
		closedByDefault = true
	)
	String debugLookSalmon = "debugLookSalmon";

	@ConfigSection(
		name = "Debug: pike look",
		description = "Temporary tuning of the pike's look and dips",
		position = 103,
		closedByDefault = true
	)
	String debugLookPike = "debugLookPike";

	@ConfigSection(
		name = "Debug: rainbow fish look",
		description = "Temporary tuning of the rainbow fish's look and dips",
		position = 104,
		closedByDefault = true
	)
	String debugLookRainbow = "debugLookRainbow";

	@ConfigSection(
		name = "Debug: leaping trout look",
		description = "Temporary tuning of the leaping trout's look and dips",
		position = 105,
		closedByDefault = true
	)
	String debugLookLeapTrout = "debugLookLeapTrout";

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookLeapTrout,
		position = 0
	)
	default int debugLookLeapTroutSink()
	{
		return 11;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookLeapTrout,
		position = 1
	)
	default int debugLookLeapTroutRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookLeapTroutRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookLeapTrout,
		position = 2
	)
	default int debugLookLeapTroutRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookLeapTroutTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookLeapTrout,
		position = 3
	)
	default int debugLookLeapTroutTilt()
	{
		return -39;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookLeapTrout,
		position = 4
	)
	default int debugLookLeapTroutSize()
	{
		return 45;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookLeapTroutTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookLeapTrout,
		position = 5
	)
	default int debugLookLeapTroutTurn()
	{
		return 180;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookLeapTroutLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookLeapTrout,
		position = 6
	)
	default int debugLookLeapTroutLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookLeapTrout,
		position = 7
	)
	default int debugLookLeapTroutWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookLeapTrout,
		position = 8
	)
	default int debugLookLeapTroutTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookLeapTrout,
		position = 9
	)
	default int debugLookLeapTroutPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookLeapTrout,
		position = 10
	)
	default int debugLookLeapTroutSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookLeapTrout,
		position = 11
	)
	default int debugLookLeapTroutTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookLeapTrout,
		position = 13
	)
	default int debugLookLeapTroutTipPivotUp()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookLeapTrout,
		position = 12
	)
	default int debugLookLeapTroutPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookLeapTroutDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookLeapTrout,
		position = 20
	)
	default int debugLookLeapTroutDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapTroutDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookLeapTrout,
		position = 21
	)
	default int debugLookLeapTroutDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookLeapTroutDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookLeapTrout,
		position = 22
	)
	default int debugLookLeapTroutDipMillis()
	{
		return 1500;
	}

	@Range(min = 5, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookLeapTroutThickness",
		name = "Fish thickness",
		description = "How thick the leaping trout is,<br>of its doubled model",
		section = debugLookLeapTrout,
		position = 30
	)
	default int debugLookLeapTroutThickness()
	{
		return 100;
	}

	@Range(min = 50, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookLeapTroutStretch",
		name = "Fish stretch",
		description = "How long the leaping trout is,<br>of its model's length",
		section = debugLookLeapTrout,
		position = 31
	)
	default int debugLookLeapTroutStretch()
	{
		return 100;
	}

	@ConfigSection(
		name = "Debug: leaping salmon look",
		description = "Temporary tuning of the leaping salmon's look and dips",
		position = 106,
		closedByDefault = true
	)
	String debugLookLeapSalmon = "debugLookLeapSalmon";

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookLeapSalmon,
		position = 0
	)
	default int debugLookLeapSalmonSink()
	{
		return 15;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookLeapSalmon,
		position = 1
	)
	default int debugLookLeapSalmonRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookLeapSalmonRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookLeapSalmon,
		position = 2
	)
	default int debugLookLeapSalmonRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookLeapSalmonTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookLeapSalmon,
		position = 3
	)
	default int debugLookLeapSalmonTilt()
	{
		return -40;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookLeapSalmon,
		position = 4
	)
	default int debugLookLeapSalmonSize()
	{
		return 39;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookLeapSalmonTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookLeapSalmon,
		position = 5
	)
	default int debugLookLeapSalmonTurn()
	{
		return 180;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookLeapSalmonLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookLeapSalmon,
		position = 6
	)
	default int debugLookLeapSalmonLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookLeapSalmon,
		position = 7
	)
	default int debugLookLeapSalmonWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookLeapSalmon,
		position = 8
	)
	default int debugLookLeapSalmonTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookLeapSalmon,
		position = 9
	)
	default int debugLookLeapSalmonPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookLeapSalmon,
		position = 10
	)
	default int debugLookLeapSalmonSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookLeapSalmon,
		position = 11
	)
	default int debugLookLeapSalmonTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookLeapSalmon,
		position = 13
	)
	default int debugLookLeapSalmonTipPivotUp()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookLeapSalmon,
		position = 12
	)
	default int debugLookLeapSalmonPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookLeapSalmonDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookLeapSalmon,
		position = 20
	)
	default int debugLookLeapSalmonDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLeapSalmonDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookLeapSalmon,
		position = 21
	)
	default int debugLookLeapSalmonDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookLeapSalmonDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookLeapSalmon,
		position = 22
	)
	default int debugLookLeapSalmonDipMillis()
	{
		return 1500;
	}

	@Range(min = 5, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookLeapSalmonThickness",
		name = "Fish thickness",
		description = "How thick the leaping salmon is,<br>of its doubled model",
		section = debugLookLeapSalmon,
		position = 30
	)
	default int debugLookLeapSalmonThickness()
	{
		return 100;
	}

	@Range(min = 50, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookLeapSalmonStretch",
		name = "Fish stretch",
		description = "How long the leaping salmon is,<br>of its model's length",
		section = debugLookLeapSalmon,
		position = 31
	)
	default int debugLookLeapSalmonStretch()
	{
		return 100;
	}

	@ConfigSection(
		name = "Debug: leaping sturgeon look",
		description = "Temporary tuning of the leaping sturgeon's look and dips",
		position = 107,
		closedByDefault = true
	)
	String debugLookSturgeon = "debugLookSturgeon";

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookSturgeon,
		position = 0
	)
	default int debugLookSturgeonSink()
	{
		return 15;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookSturgeon,
		position = 1
	)
	default int debugLookSturgeonRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookSturgeonRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookSturgeon,
		position = 2
	)
	default int debugLookSturgeonRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookSturgeonTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookSturgeon,
		position = 3
	)
	default int debugLookSturgeonTilt()
	{
		return -52;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookSturgeon,
		position = 4
	)
	default int debugLookSturgeonSize()
	{
		return 60;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookSturgeonTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookSturgeon,
		position = 5
	)
	default int debugLookSturgeonTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookSturgeonLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookSturgeon,
		position = 6
	)
	default int debugLookSturgeonLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookSturgeon,
		position = 7
	)
	default int debugLookSturgeonWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookSturgeon,
		position = 8
	)
	default int debugLookSturgeonTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookSturgeon,
		position = 9
	)
	default int debugLookSturgeonPace()
	{
		return 70;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookSturgeon,
		position = 10
	)
	default int debugLookSturgeonSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookSturgeon,
		position = 11
	)
	default int debugLookSturgeonTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookSturgeon,
		position = 13
	)
	default int debugLookSturgeonTipPivotUp()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookSturgeon,
		position = 12
	)
	default int debugLookSturgeonPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookSturgeonDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookSturgeon,
		position = 20
	)
	default int debugLookSturgeonDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookSturgeonDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookSturgeon,
		position = 21
	)
	default int debugLookSturgeonDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookSturgeonDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookSturgeon,
		position = 22
	)
	default int debugLookSturgeonDipMillis()
	{
		return 1500;
	}

	@Range(min = 5, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookSturgeonThickness",
		name = "Fish thickness",
		description = "How thick the leaping sturgeon is,<br>of its doubled model",
		section = debugLookSturgeon,
		position = 30
	)
	default int debugLookSturgeonThickness()
	{
		return 50;
	}

	@Range(min = 50, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookSturgeonStretch",
		name = "Fish stretch",
		description = "How long the leaping sturgeon is,<br>of its model's length",
		section = debugLookSturgeon,
		position = 31
	)
	default int debugLookSturgeonStretch()
	{
		return 100;
	}

	@ConfigSection(
		name = "Debug: bobs and depth",
		description = "Temporary tuning of bob timing and<br>depths, all river and lake fish",
		position = 108,
		closedByDefault = true
	)
	String debugRiverDips = "debugRiverDips";

	@ConfigSection(
		name = "Debug: waterfalls",
		description = "Temporary tuning of fish climbing<br>up waterfalls",
		position = 112,
		closedByDefault = true
	)
	String debugWaterfalls = "debugWaterfalls";

	@ConfigSection(
		name = "Debug: leaps",
		description = "Temporary tuning of leaping fish<br>jumping out of the water",
		position = 113,
		closedByDefault = true
	)
	String debugLeaps = "debugLeaps";

	@ConfigSection(
		name = "Debug: rings and spacing",
		description = "Temporary tuning of the circles<br>at spots and fish spacing",
		position = 109,
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

	@ConfigItem(
		keyName = "debugRiverEagerCircles",
		name = "Eager circles",
		description = "Every passing fish joins a fished spot,<br>and fillers come after 1 s, every 0.5-1 s",
		section = debugRiverLanes,
		position = 20
	)
	default boolean debugRiverEagerCircles()
	{
		return false;
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
		return 120;
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
		position = 0
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
		position = 1
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
		section = debugSchools,
		position = 0
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
		keyName = "debugLookRainbowTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookRainbow,
		position = 13
	)
	default int debugLookRainbowTipPivotUp()
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
		return 26;
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
		keyName = "debugLookTroutTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookTrout,
		position = 13
	)
	default int debugLookTroutTipPivotUp()
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
		return 28;
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
		keyName = "debugLookSalmonTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookSalmon,
		position = 13
	)
	default int debugLookSalmonTipPivotUp()
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
		return 9;
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
		keyName = "debugLookPikeTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookPike,
		position = 13
	)
	default int debugLookPikeTipPivotUp()
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
		section = debugBodies,
		position = 0
	)
	default int debugRiverBodyMinutes()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "debugRiverBodyTest",
		name = "Dead body every 10 s",
		description = "For testing: a dead body<br>10 seconds after the last one goes",
		section = debugBodies,
		position = 1
	)
	default boolean debugRiverBodyTest()
	{
		return false;
	}

	@Range(max = 359)
	@ConfigItem(
		keyName = "debugRiverBodyFeet",
		name = "Dead body feet turn",
		description = "Turn that lines a dead body up<br>with a narrow river",
		section = debugBodies,
		position = 2
	)
	default int debugRiverBodyFeet()
	{
		return 145;
	}

	@Range(min = 10, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLakeSpeed",
		name = "Lake speed",
		description = "How fast lake fish cruise,<br>of river fish speed",
		section = debugLakes,
		position = 0
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
		section = debugLakes,
		position = 1
	)
	default int debugRiverLakeJoinChance()
	{
		return 70;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLakeSturgeon",
		name = "Lake sturgeon share",
		description = "About how many of a barbarian<br>lake's fish are sturgeon",
		section = debugLakes,
		position = 2
	)
	default int debugRiverLakeSturgeon()
	{
		return 15;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(
		keyName = "debugRiverDeepLeast",
		name = "Least depth (117 HD)",
		description = "Shallowest a fish swims under the<br>surface with 117 HD, local units",
		section = debugRiverDips,
		position = 2
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
		position = 3
	)
	default int debugRiverDeepMost()
	{
		return 96;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(
		keyName = "debugRiverDiveDepth",
		name = "Diver depth",
		description = "How far under the surface fish go<br>on a fish diver, local units<br>(only fish not swimming deep)",
		section = debugRiverDips,
		position = 4
	)
	default int debugRiverDiveDepth()
	{
		return 25;
	}

	@Range(min = 1, max = 50)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverDiveSpeed",
		name = "Diver speed",
		description = "Share of the way down (and back up)<br>a fish goes each client tick<br>on a fish diver",
		section = debugRiverDips,
		position = 5
	)
	default int debugRiverDiveSpeed()
	{
		return 1;
	}

	@Range(min = 0, max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverDepthTilt",
		name = "Depth tilt",
		description = "How far fish point up rising and<br>down sinking, of the angle<br>they move at (most 35 degrees)",
		section = debugRiverDips,
		position = 6
	)
	default int debugRiverDepthTilt()
	{
		return 100;
	}

	@Range(min = 50, max = 400)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverJoinSpeed",
		name = "Join speed",
		description = "How fast fish come in to a circle<br>while far off, of their usual;<br>they slow to circling speed<br>as they near their lane",
		section = debugRiverLanes,
		position = 19
	)
	default int debugRiverJoinSpeed()
	{
		return 160;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "debugRiverDecideEvery",
		name = "Decide every",
		description = "Client ticks between river and<br>lake fish deciding where to steer,<br>reading the water's height and<br>so on (they move every tick)",
		section = debugRange,
		position = 2
	)
	default int debugRiverDecideEvery()
	{
		return 5;
	}

	@Range(min = 50, max = 400)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverClimbTilt",
		name = "Climb tilt",
		description = "How far fish point up steep water,<br>of its slope (at most 80 degrees)",
		section = debugWaterfalls,
		position = 0
	)
	default int debugRiverClimbTilt()
	{
		return 150;
	}

	@Range(min = 50, max = 500)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverClimbWag",
		name = "Climb wag",
		description = "How fast tails wag climbing,<br>of how fast on the flat",
		section = debugWaterfalls,
		position = 1
	)
	default int debugRiverClimbWag()
	{
		return 150;
	}

	@Range(min = 1, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "debugLeapEvery",
		name = "Leap every",
		description = "Seconds between each leaping<br>fish's leaps, on average",
		section = debugLeaps,
		position = 0
	)
	default int debugLeapEvery()
	{
		return 20;
	}

	@Range(min = 100, max = 5000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugLeapMillis",
		name = "Leap length",
		description = "How long a leap is in the air",
		section = debugLeaps,
		position = 2
	)
	default int debugLeapMillis()
	{
		return 700;
	}

	@Range(min = 0, max = 1000)
	@ConfigItem(
		keyName = "debugLeapHeight",
		name = "Leap height",
		description = "How high over the water<br>a leap goes, local units",
		section = debugLeaps,
		position = 3
	)
	default int debugLeapHeight()
	{
		return 70;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "debugLeapHeightSpread",
		name = "Leap height spread",
		description = "Each leap up to this much higher<br>or lower, at random, local units",
		section = debugLeaps,
		position = 3
	)
	default int debugLeapHeightSpread()
	{
		return 5;
	}

	@Range(min = 0, max = 89)
	@ConfigItem(
		keyName = "debugLeapPitch",
		name = "Leap tilt",
		description = "Most degrees nose up or down,<br>pointing along its way",
		section = debugLeaps,
		position = 7
	)
	default int debugLeapPitch()
	{
		return 80;
	}

	@Range(min = 0, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapLanding",
		name = "Landing dive",
		description = "How much of the speed it falls<br>in at carries it on down past<br>its depth",
		section = debugLeaps,
		position = 10
	)
	default int debugLeapLanding()
	{
		return 60;
	}

	@Range(min = 20, max = 5000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugLeapSettleMillis",
		name = "Landing settle",
		description = "How long a landed fish takes<br>to ease back to its depth and level",
		section = debugLeaps,
		position = 12
	)
	default int debugLeapSettleMillis()
	{
		return 1500;
	}

	@Range(min = 20, max = 5000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugLeapRunMillis",
		name = "Leap run-up",
		description = "How long a fish takes to curve<br>up to the surface, speeding up",
		section = debugLeaps,
		position = 1
	)
	default int debugLeapRunMillis()
	{
		return 500;
	}

	@Range(min = 100, max = 500)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapSpeed",
		name = "Leap speed",
		description = "Its speed in the air, of its<br>usual; reached in the run-up,<br>eased off after landing",
		section = debugLeaps,
		position = 4
	)
	default int debugLeapSpeed()
	{
		return 200;
	}


	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapSchoolChance",
		name = "School leap chance",
		description = "How often a school leaps together,<br>as a share of how often one fish<br>leaps on its own",
		section = debugLeaps,
		position = 13
	)
	default int debugLeapSchoolChance()
	{
		return 15;
	}

	@Range(min = 20, max = 3000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugLeapSchoolSpreadMillis",
		name = "School leap spread",
		description = "How far behind the first the<br>rest of a school may leap",
		section = debugLeaps,
		position = 14
	)
	default int debugLeapSchoolSpreadMillis()
	{
		return 400;
	}

	@Range(min = 0, max = 30000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugLeapAfterScatterMillis",
		name = "No leaps after scatter",
		description = "How long scattered fish wait<br>before they may leap",
		section = debugLeaps,
		position = 15
	)
	default int debugLeapAfterScatterMillis()
	{
		return 5000;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapLureShare",
		name = "Lure fish leaps",
		description = "How often ordinary trout and<br>salmon leap, of leaping fish",
		section = debugLeaps,
		position = 16
	)
	default int debugLeapLureShare()
	{
		return 5;
	}

	@Range(min = 1, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapTipEase",
		name = "Leap turn smoothing",
		description = "How quickly a leaping fish's<br>nose follows its way, each<br>client tick; lower is smoother",
		section = debugLeaps,
		position = 9
	)
	default int debugLeapTipEase()
	{
		return 25;
	}

	@Range(min = 50, max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapDiveTilt",
		name = "Leap dive tilt",
		description = "How steeply a fish points coming<br>down and into the water, of<br>its way (up to Leap tilt)",
		section = debugLeaps,
		position = 8
	)
	default int debugLeapDiveTilt()
	{
		return 130;
	}

	@Range(min = 0, max = 90)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapCarry",
		name = "Landing carry",
		description = "How much of the landing settle<br>a fish keeps its leaping speed<br>before easing down",
		section = debugLeaps,
		position = 11
	)
	default int debugLeapCarry()
	{
		return 90;
	}

	/**
	 * Splash models to try, with their animations.
	 */
	enum Splash
	{
		AERIAL_FISHING(2223, AnimationID.AERIAL_FISHING_SPLASH_MEDIUM),
		STONE_SKIP(2223, AnimationID.WATERSPLASH_SMALL),
		WATER(49225, AnimationID.VFX_WATER_SPLASH_01),
		WATER_SMALL(49226, AnimationID.VFX_WATER_SPLASH_01),
		MINNOW(33187, AnimationID.MINNOW_FISHING_FLYINGFISH),
		SPRAY_IMPACT(55576, AnimationID.STRIKE_IMPACT);

		final int model;
		final int animation;

		Splash(int model, int animation)
		{
			this.model = model;
			this.animation = animation;
		}
	}

	@ConfigItem(
		keyName = "debugLeapSplashOut",
		name = "Leaving splash",
		description = "Which splash leaping fish make<br>leaving the water (as for the<br>landing splash)",
		section = debugSplashes,
		position = 0
	)
	default Splash debugLeapSplashOut()
	{
		return Splash.SPRAY_IMPACT;
	}

	@Range(min = 4, max = 256)
	@ConfigItem(
		keyName = "debugLeapSplashOutSize",
		name = "Leaving splash size",
		description = "The leaving splash's size, 128<br>as the model is",
		section = debugSplashes,
		position = 1
	)
	default int debugLeapSplashOutSize()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "debugLeapSplash",
		name = "Landing splash",
		description = "Which splash leaping fish make<br>landing: aerial fishing's, a<br>skipped stone's, the newer water<br>splash (or its spray), the minnow<br>spot's, or a water spray impact",
		section = debugSplashes,
		position = 2
	)
	default Splash debugLeapSplash()
	{
		return Splash.WATER_SMALL;
	}

	@Range(min = 4, max = 256)
	@ConfigItem(
		keyName = "debugLeapSplashSize",
		name = "Landing splash size",
		description = "The landing splash's size, 128<br>as the model is",
		section = debugSplashes,
		position = 3
	)
	default int debugLeapSplashSize()
	{
		return 70;
	}

	@Range(min = -1, max = 63)
	@ConfigItem(
		keyName = "debugLeapSplashHue",
		name = "Splash hue (GPU)",
		description = "The splash's colour, 0 to 63<br>(about 32 cyan, 40 blue);<br>-1 leaves it as it is",
		section = debugSplashes,
		position = 4
	)
	default int debugLeapSplashHue()
	{
		return 39;
	}

	@Range(min = 0, max = 7)
	@ConfigItem(
		keyName = "debugLeapSplashSaturation",
		name = "Splash saturation (GPU)",
		description = "How strong its colour is, 0 grey<br>to 7 full",
		section = debugSplashes,
		position = 5
	)
	default int debugLeapSplashSaturation()
	{
		return 1;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "debugLeapSplashLighter",
		name = "Splash lightness (GPU)",
		description = "Lightness added to every face<br>(of 127)",
		section = debugSplashes,
		position = 6
	)
	default int debugLeapSplashLighter()
	{
		return 36;
	}

	@Range(min = -1, max = 63)
	@ConfigItem(
		keyName = "debugLeapSplashHueHd",
		name = "Splash hue (117 HD)",
		description = "The splash's colour, 0 to 63<br>(about 32 cyan, 40 blue);<br>-1 leaves it as it is",
		section = debugSplashes,
		position = 7
	)
	default int debugLeapSplashHueHd()
	{
		return 39;
	}

	@Range(min = 0, max = 7)
	@ConfigItem(
		keyName = "debugLeapSplashSaturationHd",
		name = "Splash saturation (117 HD)",
		description = "How strong its colour is, 0 grey<br>to 7 full",
		section = debugSplashes,
		position = 8
	)
	default int debugLeapSplashSaturationHd()
	{
		return 1;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "debugLeapSplashLighterHd",
		name = "Splash lightness (117 HD)",
		description = "Lightness added to every face<br>(of 127)",
		section = debugSplashes,
		position = 9
	)
	default int debugLeapSplashLighterHd()
	{
		return 17;
	}

	@Range(min = 10, max = 90)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLeapFallShare",
		name = "Leap fall time",
		description = "How much of the time in the air<br>is spent falling from the top",
		section = debugLeaps,
		position = 5
	)
	default int debugLeapFallShare()
	{
		return 45;
	}

	@Range(min = 100, max = 600)
	@ConfigItem(
		keyName = "debugLeapFallPower",
		name = "Leap fall speed-up",
		description = "How hard the fall picks up speed<br>after the top: 200 as if thrown,<br>higher speeds up more at the end",
		section = debugLeaps,
		position = 6
	)
	default int debugLeapFallPower()
	{
		return 175;
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
		description = "Tiles along a river either side of you<br>where fish are drawn; lakes start<br>4 tiles further out, and go 16 further",
		section = debugRange,
		position = 0
	)
	default int debugRiverFishRange()
	{
		return 35;
	}

	@Range(max = 40)
	@ConfigItem(
		keyName = "debugRiverLoadMore",
		name = "Fish load beyond",
		description = "Tiles past the fish range that rivers<br>still keep fish in, unseen, so fish<br>come into sight already swimming;<br>rivers start 4 tiles further out still",
		section = debugRange,
		position = 1
	)
	default int debugRiverLoadMore()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "debugPanelCollapsed",
		name = "",
		description = "",
		hidden = true
	)
	default String debugPanelCollapsed()
	{
		return "";
	}
}
