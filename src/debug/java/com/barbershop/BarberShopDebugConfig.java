package com.barbershop;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("barber-shop-debug")
public interface BarberShopDebugConfig extends Config
{
	@ConfigSection(
		name = "Debug: head faces",
		description = "Checks for finding the player's own head in their model",
		position = 0
	)
	String headFacesSection = "headFaces";

	@ConfigSection(
		name = "Debug: head height",
		description = "Measures how far each head should move up or down",
		position = 1
	)
	String headHeightSection = "headHeight";

	@Range(min = -40, max = 40)
	@ConfigItem(
		keyName = "heightNudge",
		name = "Height nudge",
		description = "Moves the chosen head further down (positive) or up (negative),<br>on top of the built-in shift, in model units.<br>A player is about 200 tall.",
		section = headHeightSection,
		position = 1
	)
	default int heightNudge()
	{
		return 0;
	}

	@Range(min = 0, max = 60)
	@ConfigItem(
		keyName = "neckWeldFull",
		name = "Neck weld full",
		description = "How far up from the bottom of the head<br>it follows your neck fully, in model units.",
		section = headHeightSection,
		position = 2
	)
	default int neckWeldFull()
	{
		return 8;
	}

	@Range(min = 0, max = 80)
	@ConfigItem(
		keyName = "neckWeldBand",
		name = "Neck weld fade",
		description = "Up to how far from the bottom of the head<br>the neck pull fades out, in model units.",
		section = headHeightSection,
		position = 3
	)
	default int neckWeldBand()
	{
		return 24;
	}

	@ConfigItem(
		keyName = "showColours",
		name = "Show colours",
		description = "Writes your colour settings and the skin colour picked<br>into the chat whenever they change.",
		section = headFacesSection,
		position = 1
	)
	default boolean showColours()
	{
		return false;
	}

	@ConfigItem(
		keyName = "paintHeadFaces",
		name = "Paint head faces",
		description = "Paints the faces found as your hair and jaw magenta.<br>Set Barber Shop's Head to None first.",
		section = headFacesSection,
		position = 0
	)
	default boolean paintHeadFaces()
	{
		return false;
	}
}
