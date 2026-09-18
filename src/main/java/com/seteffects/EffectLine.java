package com.seteffects;

import javax.annotation.Nullable;

/**
 * One recognized effect, broken into parts so each renderer (the hover tooltip's colour-tag
 * markup, and the overlay's own hand-drawn text) can style the name/tally/numbers itself rather
 * than parsing a pre-formatted string back apart.
 */
final class EffectLine
{
	final String name;
	@Nullable
	final Integer worn;
	@Nullable
	final Integer total;
	final String effect;

	// True for a line that must render immediately under the line before it, with no blank-line
	// gap - e.g. the Amulet of the Damned's synergy line under its Barrows set's own line, which
	// otherwise get the same blank-line separation as any two unrelated effects
	final boolean continuesPrevious;

	EffectLine(String name, String effect)
	{
		this(name, null, null, effect, false);
	}

	EffectLine(String name, @Nullable Integer worn, @Nullable Integer total, String effect)
	{
		this(name, worn, total, effect, false);
	}

	EffectLine(String name, String effect, boolean continuesPrevious)
	{
		this(name, null, null, effect, continuesPrevious);
	}

	private EffectLine(String name, @Nullable Integer worn, @Nullable Integer total, String effect, boolean continuesPrevious)
	{
		this.name = name;
		this.worn = worn;
		this.total = total;
		this.effect = effect;
		this.continuesPrevious = continuesPrevious;
	}

	boolean isFullSet()
	{
		return worn != null && worn.equals(total);
	}
}
