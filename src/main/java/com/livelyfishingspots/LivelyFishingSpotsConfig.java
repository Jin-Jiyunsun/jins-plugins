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
		keyName = "riverSwimming",
		name = "River fish",
		description = "How fish swim in rivers and lakes:<br>Schooled: in groups<br>Random: each on its own<br>"
			+ "Only at my spot: just fish coming to the spot<br>you're fishing",
		position = 1
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
		description = "How many fish swim down the rivers",
		position = 2
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
		position = 3
	)
	default int lakeFishAmount()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "riverDeep",
		name = "Deep river fish (117 HD)",
		description = "With 117 HD's transparent water, river and<br>lake fish swim deeper, rising to circle the<br>spot being fished",
		position = 4
	)
	default boolean riverDeep()
	{
		return true;
	}
}
