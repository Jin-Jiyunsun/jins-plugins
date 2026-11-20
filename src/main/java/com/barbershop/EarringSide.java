package com.barbershop;

import lombok.RequiredArgsConstructor;

/** Which ear the earring goes on; the left one is a mirrored copy. */
@RequiredArgsConstructor
public enum EarringSide
{
	RIGHT("Right"),
	LEFT("Left"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
