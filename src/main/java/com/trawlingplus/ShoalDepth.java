package com.trawlingplus;

import net.runelite.api.gameval.AnimationID;

/**
 * How deep a shoal is swimming, which trawling nets have to be set to match.
 */
enum ShoalDepth
{
	SHALLOW("Shallow", 1),
	MODERATE("Moderate", 2),
	DEEP("Deep", 3),
	UNKNOWN("Unknown", -1);

	private final String name;
	private final int netDepth;

	ShoalDepth(String name, int netDepth)
	{
		this.name = name;
		this.netDepth = netDepth;
	}

	/**
	 * The value the depth varbit of a net holds while it is set to this depth, or -1 when unknown.
	 * A raised net reads 0, which is no depth at all.
	 */
	int netDepth()
	{
		return netDepth;
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
