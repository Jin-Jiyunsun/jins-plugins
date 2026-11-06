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

	@ConfigSection(
		name = "Debug: curiosities",
		description = "Fake finds for the trawling log's Curiosities pages",
		position = 2
	)
	String curiositiesSection = "curiosities";

	String FAKE_CURIOSITIES_KEY = "fakeCuriosities";

	@ConfigItem(
		keyName = FAKE_CURIOSITIES_KEY,
		name = "Fake finds",
		description = "Turning on gives this account a random mix of found<br>and not found curiosities, some by"
			+ " crewmates and<br>some from before the log was kept. Turning off<br>clears them all.",
		position = 3,
		section = curiositiesSection
	)
	default boolean fakeCuriosities()
	{
		return false;
	}

	String FAKE_MESSAGE_KEY = "fakeCuriosityMessage";

	@ConfigItem(
		keyName = FAKE_MESSAGE_KEY,
		name = "Fake message",
		description = "Picking one puts that game message in the chatbox,<br>only on this client, as if it had"
			+ " happened. A rare<br>catch also counts as one fish caught.",
		position = 4,
		section = curiositiesSection
	)
	default FakeMessage fakeCuriosityMessage()
	{
		return FakeMessage.NONE;
	}

	enum FakeMessage
	{
		NONE(null),
		PLAYER_MARLIN("You catch a swift marlin!"),
		CREW_KRILL("Jolly Jim catches a giant blue krill!"),
		PLAYER_ORANGEFIN("You catch a orangefin!"),
		PAINT("You've received some paint!"),
		LOG_HADDOCK("New item added to your collection log: <col=ef1020>Golden haddock</col>");

		final String message;

		FakeMessage(String message)
		{
			this.message = message;
		}
	}
}
