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
		keyName = "seaFish",
		name = "Sea fish",
		description = "Fish swimming round the fishing spots at sea",
		position = 0
	)
	default boolean seaFish()
	{
		return true;
	}

	@ConfigItem(
		keyName = "riverFish",
		name = "River fish",
		description = "Fish swimming down the rivers",
		position = 1
	)
	default boolean riverFish()
	{
		return true;
	}

	@ConfigItem(
		keyName = "freeRoamFish",
		name = "Free roam fish",
		description = "Fish roaming the lakes and shores",
		position = 2
	)
	default boolean freeRoamFish()
	{
		return true;
	}

	@ConfigItem(
		keyName = "riverSwimming",
		name = "Fish behaviour",
		description = "How fish swim in rivers, lakes and shores:<br>Schooled: in groups<br>Random: each on its own<br>"
			+ "Only at my spot: just fish coming to the spot<br>you're fishing",
		position = 3
	)
	default RiverSwimming riverSwimming()
	{
		return RiverSwimming.SCHOOLED;
	}

	@Range(min = 25, max = 125)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "riverFishAmount",
		name = "River amount",
		description = "How many fish swim down the rivers",
		position = 4
	)
	default int riverFishAmount()
	{
		return 100;
	}

	@Range(min = 25, max = 125)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "lakeFishAmount",
		name = "Free roam amount",
		description = "How many fish roam the lakes and shores",
		position = 5
	)
	default int lakeFishAmount()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "riverDeep",
		name = "Deep fish (117 HD)",
		description = "With 117 HD's transparent water, river, lake<br>and shore fish swim deeper, rising to circle<br>the spot being fished",
		position = 6
	)
	default boolean riverDeep()
	{
		return true;
	}
}
