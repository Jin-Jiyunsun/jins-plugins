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

	EffectLine(String name, String effect)
	{
		this(name, null, null, effect);
	}

	EffectLine(String name, @Nullable Integer worn, @Nullable Integer total, String effect)
	{
		this.name = name;
		this.worn = worn;
		this.total = total;
		this.effect = effect;
	}

	boolean isFullSet()
	{
		return worn != null && worn.equals(total);
	}
}
