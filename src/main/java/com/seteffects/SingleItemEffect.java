package com.seteffects;

class SingleItemEffect
{
	private final EffectFamily family;
	private final String name;
	private final String effect;

	SingleItemEffect(EffectFamily family, String name, String effect)
	{
		this.family = family;
		this.name = name;
		this.effect = effect;
	}

	EffectFamily getFamily()
	{
		return family;
	}

	EffectLine describe()
	{
		return new EffectLine(name, effect);
	}
}
