package com.jinjiyunsun.pohjinglemute;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(PohJingleMuteProbeConfig.GROUP)
public interface PohJingleMuteProbeConfig extends Config
{
	String GROUP = "pohjinglemute-probe";

	enum TestMode
	{
		OFF("Off"),
		ZERO_THEN_TAIL("Mute, then tail volume"),
		ZERO_ONLY("Mute until end");

		private final String name;

		TestMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigSection(
		name = "Debug: timing test",
		description = "Mutes one jingle with setMusicVolume",
		position = 0
	)
	String timingSection = "timingSection";

	@ConfigItem(
		keyName = "testMode",
		name = "Mode",
		description = "Off: probe only logs.<br>"
			+ "Mute, then tail volume: mute volume<br>"
			+ "at jingle start, tail volume shortly<br>"
			+ "before its end, restore once it's gone.<br>"
			+ "Mute until end: mute volume until<br>"
			+ "it's gone.",
		section = timingSection,
		position = 0
	)
	default TestMode testMode()
	{
		return TestMode.OFF;
	}

	@ConfigItem(
		keyName = "jingleId",
		name = "Jingle ID",
		description = "MIDI archive ID of the jingle to mute.",
		section = timingSection,
		position = 1
	)
	default int jingleId()
	{
		return 112;
	}

	@Range(max = 100)
	@ConfigItem(
		keyName = "muteVolume",
		name = "Mute volume",
		description = "Volume while the jingle plays.<br>"
			+ "0 stops the jingle and the house track<br>"
			+ "never starts.",
		section = timingSection,
		position = 2
	)
	default int muteVolume()
	{
		return 1;
	}

	@Range(max = 60000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "jingleLength",
		name = "Jingle length",
		description = "Length from the cache.<br>"
			+ "Jingle 112 is 8000 ms.",
		section = timingSection,
		position = 3
	)
	default int jingleLength()
	{
		return 8000;
	}

	@Range(max = 60000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "tailLead",
		name = "Tail lead",
		description = "How long before the jingle length<br>"
			+ "to switch to the tail volume.",
		section = timingSection,
		position = 4
	)
	default int tailLead()
	{
		return 500;
	}

	@Range(max = 100)
	@ConfigItem(
		keyName = "tailVolume",
		name = "Tail volume",
		description = "Volume for the end of the jingle,<br>"
			+ "so the house track starts above 0.",
		section = timingSection,
		position = 5
	)
	default int tailVolume()
	{
		return 1;
	}
}
