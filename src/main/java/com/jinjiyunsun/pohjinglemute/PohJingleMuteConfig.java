package com.jinjiyunsun.pohjinglemute;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PohJingleMuteConfig.GROUP)
public interface PohJingleMuteConfig extends Config
{
	String GROUP = "pohjinglemute";

	@ConfigItem(
		keyName = "mutedJingles",
		name = "Muted jingles",
		description = "Jingle IDs to mute, separated by commas,<br>"
			+ "e.g. 20, 112, 299<br>"
			+ "Starts with the house entrance jingles.<br>"
			+ "With Show track IDs on, a jingle's ID is<br>"
			+ "the orange number in chat.",
		position = 0
	)
	// The player-owned house entrance jingles, one per house style
	default String mutedJingles()
	{
		return "16, 18, 19, 20, 21, 22, 112, 279, 286, 307, 308";
	}

	@ConfigItem(
		keyName = "mutedTracks",
		name = "Muted tracks",
		description = "Music tracks to mute until the next track plays.<br>"
			+ "Track names or IDs, separated by commas,<br>"
			+ "e.g. 138, The Trade Parade<br>"
			+ "With Show track IDs on, the name is blue<br>"
			+ "and the ID is the orange number in chat.",
		position = 1
	)
	default String mutedTracks()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showTrackIds",
		name = "Show track IDs",
		description = "Posts each track and jingle in chat as it starts,<br>"
			+ "to find the ones to mute.",
		position = 2
	)
	default boolean showTrackIds()
	{
		return false;
	}
}
