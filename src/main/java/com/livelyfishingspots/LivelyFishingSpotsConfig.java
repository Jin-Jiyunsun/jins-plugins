package com.livelyfishingspots;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(LivelyFishingSpotsConfig.GROUP)
public interface LivelyFishingSpotsConfig extends Config
{
	String GROUP = "lively-fishing-spots";

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

	@Range(min = 25, max = 125)
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

	@Range(min = 25, max = 125)
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
}
