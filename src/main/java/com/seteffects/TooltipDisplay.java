package com.seteffects;

/** Where hovering an item shows its effect as a tooltip. */
public enum TooltipDisplay
{
	OFF("Off"),
	EQUIPMENT_WINDOW("Equipment window"),
	EVERYWHERE("Everywhere");

	private final String label;

	TooltipDisplay(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
