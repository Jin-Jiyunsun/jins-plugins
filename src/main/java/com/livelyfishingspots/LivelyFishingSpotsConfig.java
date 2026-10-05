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
	Set<String> REBUILD = Set.of("debugRiverCircleLanes", "debugRiverCircleLaneSpacing", "debugRiverCircleSize", "debugRiverCircleMost", "debugRiverTravelSpacing", "debugRiverSpread", "debugRiverCircleOffsetX",
		"debugRiverCircleOffsetY");

	@ConfigSection(
		name = "Debug: rivers",
		description = "Temporary settings for setting up the river spots",
		position = 99,
		closedByDefault = true
	)
	String debugRivers = "debugRivers";

	@ConfigItem(
		keyName = "debugDraw",
		name = "Show water and path",
		description = "Draws the river's banks, the fish's path,<br>the circle and each spot's id",
		section = debugRivers,
		position = 0
	)
	default boolean debugDraw()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugPick",
		name = "Pick routes",
		description = "Adds River start and River end<br>to the right-click menu on tiles;<br>each route picked is logged",
		section = debugRivers,
		position = 1
	)
	default boolean debugPick()
	{
		return false;
	}

	@ConfigSection(
		name = "Debug: river fish look",
		description = "Temporary tuning of the river fish's look, all three",
		position = 100,
		closedByDefault = true
	)
	String debugLookRiver = "debugLookRiver";

	@ConfigSection(
		name = "Debug: river dips",
		description = "Temporary tuning of river fish dips",
		position = 102,
		closedByDefault = true
	)
	String debugRiverDips = "debugRiverDips";

	@ConfigSection(
		name = "Debug: river lanes",
		description = "Temporary tuning of the circles and spacing",
		position = 103,
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
		return 70;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverSink",
		name = "Fish sink",
		description = "How far under the water<br>it rests, local units",
		section = debugLookRiver,
		position = 0
	)
	default int debugLookRiverSink()
	{
		return 8;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverRise",
		name = "Fish bob",
		description = "How far down from where<br>it rests it bobs, local units",
		section = debugLookRiver,
		position = 1
	)
	default int debugLookRiverRise()
	{
		return 3;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookRiverRoll",
		name = "Fish roll",
		description = "Degrees rolled up off its side",
		section = debugLookRiver,
		position = 2
	)
	default int debugLookRiverRoll()
	{
		return -90;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "debugLookRiverTilt",
		name = "Fish tilt",
		description = "Degrees tilted head up",
		section = debugLookRiver,
		position = 3
	)
	default int debugLookRiverTilt()
	{
		return 0;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverSize",
		name = "Fish size",
		description = "Percent of the item's size",
		section = debugLookRiver,
		position = 4
	)
	default int debugLookRiverSize()
	{
		return 30;
	}

	@Range(min = -360, max = 360)
	@ConfigItem(
		keyName = "debugLookRiverTurn",
		name = "Fish turn",
		description = "Degrees turned to face<br>the way it swims",
		section = debugLookRiver,
		position = 5
	)
	default int debugLookRiverTurn()
	{
		return -90;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "debugLookRiverLightest",
		name = "Fish min lightness",
		description = "Lightest every face is,<br>of 127",
		section = debugLookRiver,
		position = 6
	)
	default int debugLookRiverLightest()
	{
		return 50;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverWag",
		name = "Fish wag",
		description = "Percent of the tail wag",
		section = debugLookRiver,
		position = 7
	)
	default int debugLookRiverWag()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverTip",
		name = "Fish tipping",
		description = "Percent of the tip<br>as it bobs and dips",
		section = debugLookRiver,
		position = 8
	)
	default int debugLookRiverTip()
	{
		return 100;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverPace",
		name = "Fish speed",
		description = "Percent of the river speed",
		section = debugLookRiver,
		position = 9
	)
	default int debugLookRiverPace()
	{
		return 100;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverSurge",
		name = "Fish surge",
		description = "Percent of the surge",
		section = debugLookRiver,
		position = 10
	)
	default int debugLookRiverSurge()
	{
		return 100;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverTipPivot",
		name = "Fish tip pivot",
		description = "How far ahead of its middle<br>it tips about, local units",
		section = debugLookRiver,
		position = 11
	)
	default int debugLookRiverTipPivot()
	{
		return 0;
	}

	@Range(min = -500, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverPivot",
		name = "Fish wag pivot",
		description = "How far ahead of its middle<br>it wags about, local units",
		section = debugLookRiver,
		position = 12
	)
	default int debugLookRiverPivot()
	{
		return 16;
	}

	@Range(min = 1, max = 600)
	@ConfigItem(
		keyName = "debugLookRiverDipEvery",
		name = "Fish dip every",
		description = "Seconds between dips,<br>on average",
		section = debugRiverDips,
		position = 0
	)
	default int debugLookRiverDipEvery()
	{
		return 20;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
		keyName = "debugLookRiverDipDepth",
		name = "Fish dip depth",
		description = "How much deeper a dip goes,<br>local units",
		section = debugRiverDips,
		position = 1
	)
	default int debugLookRiverDipDepth()
	{
		return 7;
	}

	@Range(min = 1, max = 10000)
	@ConfigItem(
		keyName = "debugLookRiverDipMillis",
		name = "Fish dip length",
		description = "Milliseconds a dip takes,<br>down and back up",
		section = debugRiverDips,
		position = 2
	)
	default int debugLookRiverDipMillis()
	{
		return 1500;
	}

	@Range(min = -512, max = 512)
	@ConfigItem(
		keyName = "debugRiverCircleOffsetX",
		name = "Circle offset east",
		description = "How far each circle's middle is<br>east of its spot, local units;<br>west when less than 0",
		section = debugRiverLanes,
		position = 5
	)
	default int debugRiverCircleOffsetX()
	{
		return 26;
	}

	@Range(min = -512, max = 512)
	@ConfigItem(
		keyName = "debugRiverCircleOffsetY",
		name = "Circle offset north",
		description = "How far each circle's middle is<br>north of its spot, local units;<br>south when less than 0",
		section = debugRiverLanes,
		position = 6
	)
	default int debugRiverCircleOffsetY()
	{
		return 0;
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
		return 12;
	}
}
