package com.barbershop;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BarberShopConfig.GROUP)
public interface BarberShopConfig extends Config
{
	String GROUP = "barber-shop";

	@ConfigItem(
		keyName = "headBodyTypeA",
		name = "Head (A)",
		description = "The head and hair to wear while your character<br>has body type A. Only you can see it.",
		position = 0
	)
	default BodyTypeAHead headBodyTypeA()
	{
		return BodyTypeAHead.NONE;
	}

	@ConfigItem(
		keyName = "headBodyTypeB",
		name = "Head (B)",
		description = "The head and hair to wear while your character<br>has body type B. Only you can see it.",
		position = 1
	)
	default BodyTypeBHead headBodyTypeB()
	{
		return BodyTypeBHead.NONE;
	}

	@ConfigItem(
		keyName = "hairColour",
		name = "Hair colour",
		description = "The colour of the head's hair and beard:<br>your own, the NPC's, or the custom colour below.",
		position = 2
	)
	default HairColour hairColour()
	{
		return HairColour.OWN;
	}

	@ConfigItem(
		keyName = "customHairColour",
		name = "Custom hair colour",
		description = "The hair colour used when Hair colour is Custom.",
		position = 3
	)
	default Color customHairColour()
	{
		return new Color(110, 60, 30);
	}

	@ConfigItem(
		keyName = "glasses",
		name = "Glasses",
		description = "Adds a pair of glasses to any head.",
		position = 4
	)
	default Glasses glasses()
	{
		return Glasses.NONE;
	}

	@ConfigItem(
		keyName = "earring",
		name = "Earring",
		description = "Adds an earring to any head.",
		position = 5
	)
	default Earring earring()
	{
		return Earring.NONE;
	}

	@ConfigItem(
		keyName = "earringSide",
		name = "Earring side",
		description = "Which ear the earring goes on.",
		position = 6
	)
	default EarringSide earringSide()
	{
		return EarringSide.RIGHT;
	}

	@ConfigItem(
		keyName = "customEarringColour",
		name = "Custom earring colour",
		description = "Recolours the earring with the colour below,<br>keeping its own light and dark shades.",
		position = 7
	)
	default boolean customEarringColour()
	{
		return false;
	}

	@ConfigItem(
		keyName = "earringColour",
		name = "Earring colour",
		description = "The earring's colour when Custom earring colour is on.",
		position = 8
	)
	default Color earringColour()
	{
		return new Color(210, 170, 60);
	}

	@ConfigItem(
		keyName = "dialogueHead",
		name = "Dialogue head",
		description = "Shows the head's NPC chathead in place of yours<br>when you speak in dialogue. It keeps the NPC's<br>own colours, hat and jewellery.",
		position = 9
	)
	default boolean dialogueHead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepHelmets",
		name = "Keep helmets",
		description = "Leaves headgear that hides your hair on,<br>such as full helmets and hoods,<br>and shows your own head while it's worn.<br>Hats and headbands still come off.",
		position = 10
	)
	default boolean keepHelmets()
	{
		return false;
	}
}
