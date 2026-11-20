package com.barbershop;

import lombok.RequiredArgsConstructor;

/** Where the head's hair colour comes from. */
@RequiredArgsConstructor
public enum HairColour
{
	OWN("Your own"),
	NPC("The NPC's"),
	CUSTOM("Custom");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
