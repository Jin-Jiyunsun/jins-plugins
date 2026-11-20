package com.barbershop;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BarberShopConfig.GROUP)
public interface BarberShopConfig extends Config
{
	String GROUP = "barber-shop";

	@ConfigItem(
		keyName = "head",
		name = "Head",
		description = "The head and hair to wear in place of your own.<br>Only you can see it.",
		position = 0
	)
	default Head head()
	{
		return Head.NONE;
	}

	@ConfigItem(
		keyName = "matchSkin",
		name = "Match skin colour",
		description = "Gives the head your own skin colour<br>instead of the NPC's.",
		position = 1
	)
	default boolean matchSkin()
	{
		return true;
	}
}
