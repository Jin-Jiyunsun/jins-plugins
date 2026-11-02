package com.trawlingplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(TrawlingPlusDebugConfig.GROUP)
public interface TrawlingPlusDebugConfig extends Config
{
	String GROUP = "trawling-plus-debug";

	@ConfigSection(
		name = "Debug: timing",
		description = "Time Trawling Plus's main overlay",
		position = 0
	)
	String timingSection = "timing";

	@ConfigItem(
		keyName = "showRenderTiming",
		name = "Show draw time",
		description = "Show how long the main overlay takes to draw<br>each frame, over the last few seconds.",
		position = 1,
		section = timingSection
	)
	default boolean showRenderTiming()
	{
		return true;
	}
}
