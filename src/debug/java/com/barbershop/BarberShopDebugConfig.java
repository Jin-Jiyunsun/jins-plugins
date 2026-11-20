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
		name = "Debug: glasses",
		description = "Measures how far each pair of glasses should move on each body type",
		position = 2
	)
	String glassesSection = "glasses";

	@Range(min = -30, max = 30)
	@ConfigItem(
		keyName = "glassesNudgeDown",
		name = "Glasses down",
		description = "Moves the glasses down (positive) or up (negative),<br>on top of their built-in offset, in model units.",
		section = glassesSection,
		position = 0
	)
	default int glassesNudgeDown()
	{
		return 0;
	}

	@Range(min = -30, max = 30)
	@ConfigItem(
		keyName = "glassesNudgeBack",
		name = "Glasses back",
		description = "Moves the glasses back towards the ears (positive)<br>or forward (negative), on top of their built-in offset,<br>in model units.",
		section = glassesSection,
		position = 1
	)
	default int glassesNudgeBack()
	{
		return 0;
	}

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

	@Range(min = 0, max = 30)
	@ConfigItem(
		keyName = "neckCut",
		name = "Neck cut",
		description = "How far above your chin the head's skin<br>starts being hidden, in model units,<br>letting your own neck show.",
		section = headHeightSection,
		position = 2
	)
	default int neckCut()
	{
		return 10;
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
		description = "Paints the faces found as your hair and jaw magenta.<br>Set Barber Shop's head to None first.",
		section = headFacesSection,
		position = 0
	)
	default boolean paintHeadFaces()
	{
		return false;
	}
}
