package com.trawlingplus;

import net.runelite.api.gameval.AnimationID;

/**
 * How deep a shoal is swimming, which trawling nets have to be set to match.
 */
enum ShoalDepth
{
	SHALLOW("Shallow"),
	MODERATE("Moderate"),
	DEEP("Deep"),
	UNKNOWN("Unknown");

	private final String name;

	ShoalDepth(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * The depth an animation means, or null if it means none of them. The ripples of a shoal play the
	 * first three; the fish swimming around it play the silent versions of the same three.
	 */
	static ShoalDepth fromAnimation(int animation)
	{
		switch (animation)
		{
			case AnimationID.DEEP_SEA_TRAWLING_SHOAL_SHALLOW:
			case AnimationID.DEEP_SEA_TRAWLING_SHOAL_SHALLOW_NOSOUND:
				return SHALLOW;
			case AnimationID.DEEP_SEA_TRAWLING_SHOAL_MID:
			case AnimationID.DEEP_SEA_TRAWLING_SHOAL_MID_NOSOUND:
				return MODERATE;
			case AnimationID.DEEP_SEA_TRAWLING_SHOAL_DEEP:
			case AnimationID.DEEP_SEA_TRAWLING_SHOAL_DEEP_NOSOUND:
				return DEEP;
			default:
				return null;
		}
	}
}
