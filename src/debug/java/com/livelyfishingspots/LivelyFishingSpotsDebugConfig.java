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
		position = 108,
		closedByDefault = true
	)
	String debugSplashes = "debugSplashes";

	@ConfigSection(
		name = "Debug: dead bodies",
		description = "Temporary tuning of the dead bodies<br>drifting down the rivers",
		position = 109,
		closedByDefault = true
	)
	String debugBodies = "debugBodies";

	@ConfigSection(
		name = "Debug: range and performance",
		description = "Temporary tuning of how far from<br>you fish are kept, and how often<br>they decide",
		position = 110,
		closedByDefault = true
	)
	String debugRange = "debugRange";

	@ConfigSection(
		name = "Debug: krill",
		description = "Temporary tuning of the shore<br>shrimps and anchovies (giant krill)",
		position = 104,
		closedByDefault = true
	)
	String debugLookKrill = "debugLookKrill";

	@ConfigSection(
		name = "Debug: lobsters",
		description = "Temporary tuning of the lobster<br>model and how lobsters move",
		position = 105,
		closedByDefault = true
	)
	String debugLookLobster = "debugLookLobster";

	@ConfigSection(
		name = "Debug: shark look",
		description = "Temporary tuning of the shark model",
		position = 106,
		closedByDefault = true
	)
	String debugLookShark = "debugLookShark";

	@ConfigSection(
		name = "Debug: depth and 117 HD bed",
		description = "Temporary tuning of bobs, depths<br>and the 117 HD bed",
		position = 103,
		closedByDefault = true
	)
	String debugRiverDips = "debugRiverDips";

	@ConfigSection(
		name = "Debug: leaps",
		description = "Temporary tuning of leaping fish<br>jumping out of the water",
		position = 107,
		closedByDefault = true
	)
	String debugLeaps = "debugLeaps";

	@ConfigSection(
		name = "Debug: rings",
		description = "Temporary tuning of the rings<br>fish circle at fished spots",
		position = 101,
		closedByDefault = true
	)
	String debugRiverLanes = "debugRiverLanes";

	@ConfigSection(
		name = "Debug: swimming",
		description = "Temporary tuning of how river<br>and lake fish swim",
		position = 102,
		closedByDefault = true
	)
	String debugSwimming = "debugSwimming";

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
		position = 11
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
		section = debugSwimming,
		position = 0
	)
	default int debugRiverTravelSpacing()
	{
		return 150;
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
		position = 4
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
		section = debugSwimming,
		position = 1
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
		section = debugSwimming,
		position = 2
	)
	default int debugRiverWander()
	{
		return 25;
	}

	@Range(min = 1, max = 1000)
	@ConfigItem(
		keyName = "debugRiverBobCycles",
		name = "Bob time",
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
		name = "Bob rest",
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
		position = 6
	)
	default int debugRiverCircleClearance()
	{
		return 11;
	}

	@Range(min = 1, max = 3600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "debugRiverScatterSeconds",
		name = "School scatter every",
		description = "How long, on average, a big group<br>swims before it scatters",
		section = debugSwimming,
		position = 3
	)
	default int debugRiverScatterSeconds()
	{
		return 66;
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
		section = debugSwimming,
		position = 4
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
		section = debugSwimming,
		position = 5
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
		section = debugSwimming,
		position = 6
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
		return 150;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(
		keyName = "debugRiverDiveDepth",
		name = "Diver depth",
		description = "How far under the surface fish go<br>on a fish diver, local units<br>(only fish not swimming deep)",
		section = debugRiverDips,
		position = 6
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
		position = 7
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
		position = 5
	)
	default int debugRiverDepthTilt()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "debugRiverFollowBed",
		name = "Follow 117 HD bed",
		description = "Deep fish keep above 117 HD's<br>sloping bed and lobsters sit on it;<br>off, fish rise within 2 tiles<br>of a bank",
		section = debugRiverDips,
		position = 8
	)
	default boolean debugRiverFollowBed()
	{
		return true;
	}

	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverBedShare",
		name = "Bed share",
		description = "Deepest a fish goes, of 117 HD's<br>bed depth where it is",
		section = debugRiverDips,
		position = 9
	)
	default int debugRiverBedShare()
	{
		return 60;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverSwordfishBedShare",
		name = "Swordfish bed share",
		description = "Deepest a swordfish goes, of<br>117 HD's bed depth where it is",
		section = debugRiverDips,
		position = 10
	)
	default int debugRiverSwordfishBedShare()
	{
		return 40;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverSchoolDepth",
		name = "School depth spread",
		description = "How much of its own depth a fish<br>keeps in a school; the rest is<br>the school's average depth",
		section = debugRiverDips,
		position = 4
	)
	default int debugRiverSchoolDepth()
	{
		return 50;
	}

	@Range(max = 50)
	@ConfigItem(
		keyName = "debugRiverLobsterBedSink",
		name = "Bed sink",
		description = "How far lobsters sit into<br>117 HD's bed, local units",
		section = debugLookLobster,
		position = 18
	)
	default int debugRiverLobsterBedSink()
	{
		return 2;
	}

	@Range(min = -200, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLobsterRoll",
		name = "Bed roll",
		description = "How much lobsters roll to 117 HD's<br>bed sloping across their way;<br>negative flips it",
		section = debugLookLobster,
		position = 19
	)
	default int debugRiverLobsterRoll()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugRiverLobsterDiveSpeed",
		name = "Dive speed",
		description = "How fast lobsters swim back down<br>to their usual depth off a bed<br>falling away, local units a second",
		section = debugLookLobster,
		position = 22
	)
	default int debugRiverLobsterDiveSpeed()
	{
		return 50;
	}

	@Range(max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLobsterDiveTilt",
		name = "Dive tilt",
		description = "How far lobsters point up or down<br>swimming up or down, of the angle<br>they move at (most 35 degrees)",
		section = debugLookLobster,
		position = 23
	)
	default int debugRiverLobsterDiveTilt()
	{
		return 50;
	}

	@Range(min = 1, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLobsterTipEase",
		name = "Tilt ease",
		description = "Share of the way a lobster's tilt<br>moves to its new angle each<br>client tick (other fish 15%)",
		section = debugLookLobster,
		position = 24
	)
	default int debugRiverLobsterTipEase()
	{
		return 4;
	}

	@Range(max = 300)
	@ConfigItem(
		keyName = "debugRiverLobsterCircleSpread",
		name = "Circle depth spread",
		description = "Circling lobsters go up to this<br>much deeper, picked per lobster,<br>local units",
		section = debugLookLobster,
		position = 27
	)
	default int debugRiverLobsterCircleSpread()
	{
		return 40;
	}

	@Range(min = 50, max = 400)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverJoinSpeed",
		name = "Join speed",
		description = "How fast fish come in to a circle<br>while far off, of their usual;<br>they slow to circling speed<br>as they near their lane",
		section = debugRiverLanes,
		position = 10
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

	@Range(min = 20, max = 500)
	@ConfigItem(
		keyName = "debugRiverFishTarget",
		name = "River fish target",
		description = "Fish all rivers together aim to keep;<br>over it, river spacing widens a little<br>each game tick (up to 3x), so fewer<br>spawn than leave; under it, it eases back",
		section = debugRange,
		position = 3
	)
	default int debugRiverFishTarget()
	{
		return 150;
	}

	@Range(min = 50, max = 400)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverClimbTilt",
		name = "Climb tilt",
		description = "How far fish point up steep water,<br>of its slope (at most 80 degrees)",
		section = debugSwimming,
		position = 7
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
		section = debugSwimming,
		position = 8
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
		position = 4
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
		position = 8
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
		position = 11
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
		position = 13
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
		position = 5
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
		position = 14
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
		position = 15
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
		position = 16
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
		position = 17
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
		position = 10
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
		position = 9
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
		position = 12
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
		position = 6
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
		position = 7
	)
	default int debugLeapFallPower()
	{
		return 175;
	}

	@ConfigItem(
		keyName = "debugRiverRingManual",
		name = "Manual ring offset",
		description = "Every ring uses the offset below<br>instead of moving away from the bank;<br>each is logged to lock in per place",
		section = debugRiverLanes,
		position = 7
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
		position = 8
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
		position = 9
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

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookKrill,
		position = 0
	)
	default int debugLookKrillSink()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookKrill,
		position = 1
	)
	default int debugLookKrillRise()
	{
		return 0;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookKrillRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookKrill,
		position = 2
	)
	default int debugLookKrillRoll()
	{
		return 90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookKrillTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookKrill,
		position = 3
	)
	default int debugLookKrillTilt()
	{
		return -75;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookKrill,
		position = 4
	)
	default int debugLookKrillSize()
	{
		return 25;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookKrillTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookKrill,
		position = 5
	)
	default int debugLookKrillTurn()
	{
		return 90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookKrillLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookKrill,
		position = 6
	)
	default int debugLookKrillLightest()
	{
		return 0;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookKrill,
		position = 7
	)
	default int debugLookKrillWag()
	{
		return 0;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookKrill,
		position = 8
	)
	default int debugLookKrillTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookKrill,
		position = 9
	)
	default int debugLookKrillPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookKrill,
		position = 10
	)
	default int debugLookKrillSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookKrill,
		position = 11
	)
	default int debugLookKrillTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookKrill,
		position = 13
	)
	default int debugLookKrillTipPivotUp()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookKrill,
		position = 12
	)
	default int debugLookKrillPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookKrillDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookKrill,
		position = 14
	)
	default int debugLookKrillDipEvery()
	{
		return 7;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookKrillDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookKrill,
		position = 15
	)
	default int debugLookKrillDipDepth()
	{
		return 0;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookKrillDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookKrill,
		position = 16
	)
	default int debugLookKrillDipMillis()
	{
		return 1500;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookKrillBackwards",
		name = "Ring backwards",
		description = "Share of krill growing in on a<br>ring that circle it the other way",
		section = debugLookKrill,
		position = 18
	)
	default int debugLookKrillBackwards()
	{
		return 10;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "debugLookKrillRingMost",
		name = "Ring most",
		description = "Most shrimps and anchovies<br>grown in on a ring",
		section = debugLookKrill,
		position = 17
	)
	default int debugLookKrillRingMost()
	{
		return 60;
	}

	@Range(min = 1, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "debugLookKrillLaneEvery",
		name = "Lane change every",
		description = "Seconds on average between a<br>ring shrimp moving to a lane<br>next to its own",
		section = debugLookKrill,
		position = 19
	)
	default int debugLookKrillLaneEvery()
	{
		return 1;
	}

	@Range(max = 180)
	@ConfigItem(
		keyName = "debugLookKrillComeSpread",
		name = "Come in spread",
		description = "Degrees either side of one way<br>that ring shrimps grow in at<br>when fishing starts",
		section = debugLookKrill,
		position = 20
	)
	default int debugLookKrillComeSpread()
	{
		return 60;
	}

	@Range(min = 20, max = 20000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugLookKrillStream",
		name = "Come in over",
		description = "When fishing starts, krill grow<br>in over this long, a steady stream",
		section = debugLookKrill,
		position = 21
	)
	default int debugLookKrillStream()
	{
		return 3000;
	}

	@Range(min = -200, max = 200)
	@ConfigItem(
		keyName = "debugRiverKrillInnerDeeper",
		name = "Inner lane deeper",
		description = "How much deeper circling krill<br>swim on the innermost lane than<br>the outermost, local units",
		section = debugLookKrill,
		position = 22
	)
	default int debugRiverKrillInnerDeeper()
	{
		return 24;
	}

	@Range(max = 200)
	@ConfigItem(
		keyName = "debugRiverKrillDepthSpread",
		name = "Circle depth spread",
		description = "Circling krill go up to this much<br>deeper again, picked per fish,<br>local units",
		section = debugLookKrill,
		position = 23
	)
	default int debugRiverKrillDepthSpread()
	{
		return 8;
	}

	@Range(min = 5, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugLookSharkThickness",
		name = "Shark thickness",
		description = "How wide sharks are side to<br>side, of their model's width",
		section = debugLookShark,
		position = 1
	)
	default int debugLookSharkThickness()
	{
		return 60;
	}

	@Range(min = 1, max = 4)
	@ConfigItem(
		keyName = "debugRiverRingComeIn",
		name = "Ring fish come from",
		description = "Tiles outside a ring that filler<br>fish grow in at, then swim in to<br>their lane",
		section = debugRiverLanes,
		position = 12
	)
	default int debugRiverRingComeIn()
	{
		return 2;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLobsterHome",
		name = "Roamers head home",
		description = "Share of a roaming lobster's goals<br>near its cage spot",
		section = debugLookLobster,
		position = 34
	)
	default int debugRiverLobsterHome()
	{
		return 100;
	}

	@Range(min = 1, max = 12)
	@ConfigItem(
		keyName = "debugRiverLobsterHomeRadius",
		name = "Roamers home radius",
		description = "Tiles from its cage spot a<br>lobster's home goals are within",
		section = debugLookLobster,
		position = 35
	)
	default int debugRiverLobsterHomeRadius()
	{
		return 2;
	}

	@Range(max = 12)
	@ConfigItem(
		keyName = "debugRiverSpotLobsters",
		name = "Home lobsters per spot",
		description = "Lobsters each cage spot keeps<br>of its own, wandering near it",
		section = debugLookLobster,
		position = 30
	)
	default int debugRiverSpotLobsters()
	{
		return 3;
	}

	@Range(min = 1, max = 6)
	@ConfigItem(
		keyName = "debugRiverSpotLobsterRange",
		name = "Home range",
		description = "Tiles from their cage spot<br>its own lobsters wander within",
		section = debugLookLobster,
		position = 31
	)
	default int debugRiverSpotLobsterRange()
	{
		return 1;
	}

	@Range(max = 10000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugRiverLobsterSwimOff",
		name = "Swim off",
		description = "How long a lobster grown in on<br>a ring swims away from it<br>before shrinking away",
		section = debugLookLobster,
		position = 29
	)
	default int debugRiverLobsterSwimOff()
	{
		return 2000;
	}

	@Range(max = 20)
	@ConfigItem(
		keyName = "debugRiverSpotLobsterJoin",
		name = "Home join range",
		description = "Tiles away a spot's own lobsters<br>join a ring fished for lobsters",
		section = debugLookLobster,
		position = 32
	)
	default int debugRiverSpotLobsterJoin()
	{
		return 5;
	}

	@Range(max = 20000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "debugRiverSpotLobsterJoinAfter",
		name = "Home join wait",
		description = "Average wait before each of a<br>spot's own lobsters joins a ring<br>fished for lobsters",
		section = debugLookLobster,
		position = 33
	)
	default int debugRiverSpotLobsterJoinAfter()
	{
		return 2000;
	}

	@Range(min = 1, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "debugRiverStuckAfter",
		name = "Stuck after",
		description = "A river, lake or shore fish that<br>hasn't moved this long (not<br>counting pauses) shrinks away",
		section = debugRange,
		position = 4
	)
	default int debugRiverStuckAfter()
	{
		return 30;
	}

	@Range(max = 400)
	@ConfigItem(
		keyName = "debugRiverBedBankRoom",
		name = "Bank room on HD bed",
		description = "How far off banks lake and shore<br>fish keep while following 117 HD's<br>bed, local units (else 160)",
		section = debugRiverDips,
		position = 11
	)
	default int debugRiverBedBankRoom()
	{
		return 96;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookLobster,
		position = 0
	)
	default int debugLookLobsterSink()
	{
		return 30;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookLobster,
		position = 1
	)
	default int debugLookLobsterRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookLobsterRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookLobster,
		position = 2
	)
	default int debugLookLobsterRoll()
	{
		return 0;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookLobsterTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookLobster,
		position = 3
	)
	default int debugLookLobsterTilt()
	{
		return 0;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookLobster,
		position = 4
	)
	default int debugLookLobsterSize()
	{
		return 45;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookLobsterTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookLobster,
		position = 5
	)
	default int debugLookLobsterTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookLobsterLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookLobster,
		position = 6
	)
	default int debugLookLobsterLightest()
	{
		return 35;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookLobster,
		position = 7
	)
	default int debugLookLobsterWag()
	{
		return 0;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookLobster,
		position = 8
	)
	default int debugLookLobsterTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookLobster,
		position = 9
	)
	default int debugLookLobsterPace()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookLobster,
		position = 10
	)
	default int debugLookLobsterSurge()
	{
		return 50;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookLobster,
		position = 11
	)
	default int debugLookLobsterTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterTipPivotUp",
		name = "Fish tip pivot height",
		description = "How far above its middle<br>it tips about, local units",
		section = debugLookLobster,
		position = 13
	)
	default int debugLookLobsterTipPivotUp()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookLobster,
		position = 12
	)
	default int debugLookLobsterPivot()
	{
		return 0;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookLobsterDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugLookLobster,
		position = 14
	)
	default int debugLookLobsterDipEvery()
	{
		return 30;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookLobsterDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugLookLobster,
		position = 15
	)
	default int debugLookLobsterDipDepth()
	{
		return 0;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookLobsterDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugLookLobster,
		position = 16
	)
	default int debugLookLobsterDipMillis()
	{
		return 1500;
	}

	@Range(min = 1, max = 300)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "debugRiverLobsterComeIn",
		name = "Come in speed",
		description = "How fast lobsters come in to a<br>ring, of other fish",
		section = debugLookLobster,
		position = 25
	)
	default int debugRiverLobsterComeIn()
	{
		return 40;
	}

	@Range(min = -200, max = 200)
	@ConfigItem(
		keyName = "debugRiverLobsterCircleRise",
		name = "Circle rise",
		description = "How far above their sink lobsters<br>circle a ring, local units<br>(off 117 HD's bed)",
		section = debugLookLobster,
		position = 26
	)
	default int debugRiverLobsterCircleRise()
	{
		return 12;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "debugRiverLobsterBedClose",
		name = "Bed tilt range",
		description = "How near 117 HD's bed lobsters tilt<br>and roll to it, local units<br>(fully on it)",
		section = debugLookLobster,
		position = 20
	)
	default int debugRiverLobsterBedClose()
	{
		return 16;
	}

	@Range(min = 4, max = 256)
	@ConfigItem(
		keyName = "debugRiverLobsterBedLook",
		name = "Bed slope reach",
		description = "How far ahead, behind and to each<br>side lobsters read the bed's slope,<br>local units",
		section = debugLookLobster,
		position = 21
	)
	default int debugRiverLobsterBedLook()
	{
		return 32;
	}

	@Range(max = 300)
	@ConfigItem(
		keyName = "debugRiverLobsterBankRoom",
		name = "Bank room",
		description = "How far off banks lobsters keep,<br>local units (other lake fish 160)",
		section = debugLookLobster,
		position = 28
	)
	default int debugRiverLobsterBankRoom()
	{
		return 48;
	}

	@Range(min = 1, max = 300)
	@ConfigItem(
		keyName = "debugLookLobsterThickness",
		name = "Fish thickness",
		description = "Depth of its mirrored back,<br>percent",
		section = debugLookLobster,
		position = 17
	)
	default int debugLookLobsterThickness()
	{
		return 40;
	}
}
