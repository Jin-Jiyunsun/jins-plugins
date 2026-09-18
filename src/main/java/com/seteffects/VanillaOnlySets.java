package com.seteffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sets the game's own "Set Effect Bonus" box describes that we deliberately don't track (Statius's,
 * Vesta's, Morrigan's and Zuriel's equipment, and their corrupted versions). We blank the vanilla
 * text to draw our own list, which would hide these - so their blocks are lifted back out of the
 * vanilla text and added to our list in our own formatting instead. Sets we do track are left
 * alone, so nothing is shown twice.
 */
final class VanillaOnlySets
{
	// "<col=ffffff>Statius's Equipment:<br><br>Melee special attacks have 15% increased accuracy.<br><br>"
	private static final Pattern BLOCK = Pattern.compile(
		"<col=ffffff>((?:Statius|Vesta|Morrigan|Zuriel)'s(?: Corrupt)? Equipment):(?:</col>)?<br><br>(.*?)(?:<br><br>|$)",
		Pattern.DOTALL);
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");

	private VanillaOnlySets()
	{
	}

	static List<EffectLine> parse(String vanillaText)
	{
		if (vanillaText == null || vanillaText.isEmpty())
		{
			return Collections.emptyList();
		}

		List<EffectLine> lines = new ArrayList<>();
		Matcher matcher = BLOCK.matcher(vanillaText);
		while (matcher.find())
		{
			String effect = TAGS.matcher(matcher.group(2)).replaceAll(" ").replaceAll("\\s+", " ").trim();
			lines.add(new EffectLine(matcher.group(1), effect));
		}
		return lines;
	}
}
