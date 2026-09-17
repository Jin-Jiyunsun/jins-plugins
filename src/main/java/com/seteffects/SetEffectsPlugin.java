package com.seteffects;

import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Slf4j
@PluginDescriptor(
	name = "Set Effects",
	description = "Shows the set/gear effect on hover over items in the equipment window",
	tags = {"equipment", "tooltip", "set", "barrows", "void", "moons"}
)
public class SetEffectsPlugin extends Plugin
{
	private static final int WRAP_WIDTH = 50;
	private static final String TOOLTIP_LINE_BREAK = "</br>";

	// A single space, not empty - keeps the native text widget's own layout/sizing stable
	private static final String BLANK_TEXT = " ";

	@Inject
	private Client client;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private SetEffectsOverlay overlay;

	/**
	 * The vanilla "Set Effect Bonus" box's own text, rewritten by the game's own script whenever
	 * equipment changes (it already natively describes some sets, e.g. full Barrows brothers).
	 * We blank the native widget every frame and draw our own scrollable text over it instead
	 * (see SetEffectsOverlay); this is what that overlay falls back to showing when nothing we
	 * track is equipped. These two track what we last saw/wrote so a later update can tell
	 * whether the game refreshed the text since our last blank (in which case that's the real
	 * fallback) or whether it's still our own blank (in which case reuse the stored fallback).
	 */
	private String lastNativeBaseText = "";
	private String lastWrittenText;

	@Override
	protected void startUp()
	{
		log.debug("Set Effects started!");
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(overlay);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Set Effects stopped!");
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(overlay);
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		// Runs every frame, right before it's drawn - blanking here (rather than once per game
		// tick) keeps the window where the native script's own text could flash through under
		// our overlay as small as possible
		blankSetEffectWidget();

		if (client.isMenuOpen())
		{
			return;
		}

		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length == 0)
		{
			return;
		}

		MenuEntry entry = entries[entries.length - 1];
		Widget widget = entry.getWidget();
		if (widget == null)
		{
			return;
		}

		if (WidgetUtil.componentToInterface(widget.getId()) != InterfaceID.EQUIPMENT)
		{
			return;
		}

		int itemId = resolveItemId(widget);
		if (itemId <= 0)
		{
			return;
		}

		String text = buildTooltip(itemId);
		if (text != null)
		{
			tooltipManager.add(new Tooltip(text));
		}
	}

	private void blankSetEffectWidget()
	{
		Widget setEffectContainer = client.getWidget(InterfaceID.Equipment.SET_EFFECT);
		// The container itself never renders text - the actual visible text is its one dynamic child
		Widget setEffectText = setEffectContainer != null ? setEffectContainer.getChild(0) : null;
		if (setEffectText == null)
		{
			return;
		}

		String currentText = setEffectText.getText();
		if (!currentText.equals(lastWrittenText))
		{
			lastNativeBaseText = currentText;
		}

		setEffectText.setText(BLANK_TEXT);
		lastWrittenText = BLANK_TEXT;
	}

	/**
	 * What the overlay should show in place of a real effect summary, when nothing currently
	 * equipped is one we recognize - whatever the game's own box would otherwise be saying.
	 */
	String getFallbackText()
	{
		return lastNativeBaseText;
	}

	/**
	 * The equipment bonuses screen's slot widgets sometimes carry the item id directly and
	 * sometimes carry it on a child (the same generic item-slot widget is reused in multiple
	 * interfaces, and only some of those instances have the item as a direct child) - try both.
	 */
	private static int resolveItemId(Widget widget)
	{
		int itemId = widget.getItemId();
		if (itemId > 0)
		{
			return itemId;
		}

		Widget child = widget.getChild(1);
		return child != null ? child.getItemId() : -1;
	}

	private String buildTooltip(int rawItemId)
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return null;
		}

		List<EffectLine> lines = EquippedEffects.describeItem(equipment, rawItemId);
		if (lines.isEmpty())
		{
			return null;
		}

		StringBuilder text = new StringBuilder();
		for (EffectLine line : lines)
		{
			appendLine(text, EffectLineFormat.tooltipMarkup(line));
		}
		return text.toString();
	}

	private static void appendLine(StringBuilder text, String line)
	{
		if (text.length() > 0)
		{
			text.append(TOOLTIP_LINE_BREAK);
		}
		text.append(wordWrap(line));
	}

	private static String wordWrap(String text)
	{
		StringBuilder wrapped = new StringBuilder();
		StringBuilder line = new StringBuilder();
		int lineVisibleLength = 0;

		for (String word : text.split(" "))
		{
			int wordVisibleLength = EffectLineFormat.visibleLength(word);
			if (lineVisibleLength > 0 && lineVisibleLength + 1 + wordVisibleLength > WRAP_WIDTH)
			{
				appendWrappedLine(wrapped, line);
				line.setLength(0);
				lineVisibleLength = 0;
			}

			if (line.length() > 0)
			{
				line.append(' ');
				lineVisibleLength++;
			}
			line.append(word);
			lineVisibleLength += wordVisibleLength;
		}

		appendWrappedLine(wrapped, line);
		return wrapped.toString();
	}

	private static void appendWrappedLine(StringBuilder wrapped, CharSequence line)
	{
		if (line.length() == 0)
		{
			return;
		}

		if (wrapped.length() > 0)
		{
			wrapped.append(TOOLTIP_LINE_BREAK);
		}
		wrapped.append(line);
	}
}
