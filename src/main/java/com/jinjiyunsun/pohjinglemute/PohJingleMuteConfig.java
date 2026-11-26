package com.jinjiyunsun.pohjinglemute;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PohJingleMuteConfig.GROUP)
public interface PohJingleMuteConfig extends Config
{
	String GROUP = "pohjinglemute";

	@ConfigItem(
		keyName = "mutePohJingles",
		name = "Mute house jingles",
		description = "Mutes the jingle that plays when entering<br>"
			+ "a player-owned house.",
		position = 0
	)
	default boolean mutePohJingles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mutedJingles",
		name = "Muted jingles",
		description = "Other jingle IDs to mute, separated by commas.",
		position = 1
	)
	default String mutedJingles()
	{
		return "";
	}

	@ConfigItem(
		keyName = "mutedTracks",
		name = "Muted tracks",
		description = "Music tracks to mute until the next track plays.<br>"
			+ "Track names or IDs, separated by commas.",
		position = 2
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
		position = 3
	)
	default boolean showTrackIds()
	{
		return false;
	}
}
