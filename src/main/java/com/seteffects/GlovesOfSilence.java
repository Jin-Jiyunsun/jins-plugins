package com.seteffects;

import java.util.function.BooleanSupplier;

/**
 * Gloves of silence: +5% pickpocketing success, but the Hard Ardougne Diary's bonus is larger and
 * doesn't stack, which makes them obsolete everywhere. So the text depends on the diary, read
 * only (via the supplier) when the gloves are actually being described.
 */
final class GlovesOfSilence
{
	private GlovesOfSilence()
	{
	}

	static EffectLine describe(BooleanSupplier hardArdougneDiary)
	{
		if (hardArdougneDiary.getAsBoolean())
		{
			return new EffectLine("Gloves of silence", "This item no longer has any benefits with the completion of the hard Ardougne diary.");
		}

		return new EffectLine("Gloves of silence", "+5% chance of success when pickpocketing, made redundant by the hard Ardougne diary bonus.");
	}
}
