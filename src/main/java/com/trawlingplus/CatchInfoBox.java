package com.trawlingplus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.QuantityFormatter;

/**
 * A count of fish: of one kind caught, of every kind caught, or in the nets. A count below 0 is not known.
 */
class CatchInfoBox extends InfoBox
{
	private final String label;
	private final IntSupplier count;
	private final BooleanSupplier shown;

	CatchInfoBox(BufferedImage image, Plugin plugin, String label, IntSupplier count, BooleanSupplier shown)
	{
		super(image, plugin);
		this.label = label;
		this.count = count;
		this.shown = shown;
	}

	@Override
	public String getText()
	{
		int fish = count.getAsInt();
		return fish < 0 ? "?" : QuantityFormatter.quantityToStackSize(fish);
	}

	@Override
	public Color getTextColor()
	{
		return Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		int fish = count.getAsInt();
		return label + ": " + (fish < 0 ? "unknown" : QuantityFormatter.formatNumber(fish));
	}

	@Override
	public boolean render()
	{
		return shown.getAsBoolean();
	}
}
