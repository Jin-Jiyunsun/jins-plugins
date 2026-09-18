package com.seteffects;

/** When the list shows the warm clothing tally (see {@link WarmClothing}). */
public enum WarmClothingDisplay
{
	OFF("Off"),
	AT_WINTERTODT("At Wintertodt"),
	ALWAYS("Always");

	private final String label;

	WarmClothingDisplay(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
