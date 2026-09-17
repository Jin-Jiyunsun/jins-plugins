package com.seteffects;

class SingleItemEffect
{
	private final String name;
	private final String effect;

	SingleItemEffect(String name, String effect)
	{
		this.name = name;
		this.effect = effect;
	}

	EffectLine describe()
	{
		return new EffectLine(name, effect);
	}
}
