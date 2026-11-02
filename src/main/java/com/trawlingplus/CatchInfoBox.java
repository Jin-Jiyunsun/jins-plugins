package com.trawlingplus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.QuantityFormatter;

/**
 * A count of fish: of one kind caught, of every kind caught, or in the nets. A count below 0 is not known. The
 * caught ones also give the account's lifetime count in their tooltip.
 */
class CatchInfoBox extends InfoBox
{
	private final String label;
	private final IntSupplier count;
	// How many have been caught in the account's lifetime, or null for a box with no lifetime count.
	private final IntSupplier lifetime;
	private final BooleanSupplier shown;

	CatchInfoBox(BufferedImage image, Plugin plugin, String label, IntSupplier count, IntSupplier lifetime,
		BooleanSupplier shown)
	{
		super(image, plugin);
		this.label = label;
		this.count = count;
		this.lifetime = lifetime;
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
		String tooltip = label + ": " + (fish < 0 ? "unknown" : QuantityFormatter.formatNumber(fish));
		return lifetime == null ? tooltip
			: tooltip + "</br>Lifetime: " + QuantityFormatter.formatNumber(lifetime.getAsInt());
	}

	@Override
	public boolean render()
	{
		return shown.getAsBoolean();
	}
}
