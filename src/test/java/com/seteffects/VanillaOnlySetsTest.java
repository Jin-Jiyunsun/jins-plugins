package com.seteffects;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VanillaOnlySetsTest
{
	@Test
	public void picksOutOnlyTheSetsWeDontTrack()
	{
		String vanilla = "<col=ffffff>Dharok the Wretched:<br><br>Attacks do more damage as your hitpoints decrease.<br><br>"
			+ "<col=ffffff>Statius's Equipment:<br><br>Melee special attacks have 15% increased accuracy.<br><br>"
			+ "<col=ffffff>Zuriel's Corrupt Equipment:<br><br>Magic special attacks have 25% increased accuracy.<br><br>";

		List<EffectLine> lines = VanillaOnlySets.parse(vanilla);

		assertEquals(2, lines.size());
		assertEquals("Statius's Equipment", lines.get(0).name);
		assertEquals("Melee special attacks have 15% increased accuracy.", lines.get(0).effect);
		assertEquals("Zuriel's Corrupt Equipment", lines.get(1).name);
		assertEquals("Magic special attacks have 25% increased accuracy.", lines.get(1).effect);
	}

	@Test
	public void ignoresTextWithNoUntrackedSets()
	{
		assertTrue(VanillaOnlySets.parse("<col=ffffff>Ahrim The Blighted:<br><br>Magic Attacks have a 25% chance.<br><br>").isEmpty());
		assertTrue(VanillaOnlySets.parse("").isEmpty());
		assertTrue(VanillaOnlySets.parse(null).isEmpty());
	}

	@Test
	public void handlesTheScriptStyleTitleAndAMissingTrailingBreak()
	{
		List<EffectLine> lines = VanillaOnlySets.parse("<col=ffffff>Vesta's Equipment:</col><br><br>Melee special attacks have 15% increased accuracy.");

		assertEquals(1, lines.size());
		assertEquals("Vesta's Equipment", lines.get(0).name);
		assertEquals("Melee special attacks have 15% increased accuracy.", lines.get(0).effect);
	}
}
