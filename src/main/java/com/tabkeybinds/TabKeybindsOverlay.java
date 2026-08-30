package com.tabkeybinds;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ModifierlessKeybind;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the user-configured keybind text on top of each interface tab.
 * Handles fixed classic, resizable classic, and resizable modern layouts,
 * since each one uses its own tab widget for the same logical tab.
 *
 * Component IDs come straight from the gameval InterfaceID constants for
 * each toplevel variant's tab "stone" (the clickable tab background), so
 * no manual group/child ID packing is done here.
 *
 * Every tab has its own color, position mode, and (for Custom position)
 * X/Y offset, all driven from config. A handful of tabs (Combat, Quests,
 * Spellbook, Clan, Music) also get a small hardcoded X correction, since
 * their icon art isn't centered within its widget bounds -- this applies
 * only when Position is plain Centre, not Custom (Custom is fully
 * user-driven via that tab's own offset fields).
 */
public class TabKeybindsOverlay extends Overlay
{
	private static final int EDGE_INSET = 2;

	private static final class TabDef
	{
		final int fixed;
		final int resizableModern;
		final int resizableClassic;
		final Function<TabKeybindsConfig, ModifierlessKeybind> keybind;
		final Function<TabKeybindsConfig, Color> color;
		final Function<TabKeybindsConfig, TabKeybindsConfig.TabTextPosition> position;
		final Function<TabKeybindsConfig, Integer> xOffset;
		final Function<TabKeybindsConfig, Integer> yOffset;

		// Hardcoded, not user-configurable: a few tabs' icon art isn't
		// centered within its widget bounds, so this nudges the text to
		// visually line up. Applies only when Position is plain Centre.
		// Not applied for Custom (fully user-driven) or the corner-anchored
		// positions.
		final int hardcodedXOffset;

		// Same idea as hardcodedXOffset, but for the right-anchored corner
		// positions (Top Right / Bottom Right) instead of Centre.
		final int hardcodedRightOffset;

		TabDef(int fixed, int resizableModern, int resizableClassic,
				Function<TabKeybindsConfig, ModifierlessKeybind> keybind,
				Function<TabKeybindsConfig, Color> color,
				Function<TabKeybindsConfig, TabKeybindsConfig.TabTextPosition> position,
				Function<TabKeybindsConfig, Integer> xOffset,
				Function<TabKeybindsConfig, Integer> yOffset)
		{
			this(fixed, resizableModern, resizableClassic, keybind, color, position, xOffset, yOffset, 0, 0);
		}

		TabDef(int fixed, int resizableModern, int resizableClassic,
				Function<TabKeybindsConfig, ModifierlessKeybind> keybind,
				Function<TabKeybindsConfig, Color> color,
				Function<TabKeybindsConfig, TabKeybindsConfig.TabTextPosition> position,
				Function<TabKeybindsConfig, Integer> xOffset,
				Function<TabKeybindsConfig, Integer> yOffset,
				int hardcodedXOffset)
		{
			this(fixed, resizableModern, resizableClassic, keybind, color, position, xOffset, yOffset, hardcodedXOffset, 0);
		}

		TabDef(int fixed, int resizableModern, int resizableClassic,
				Function<TabKeybindsConfig, ModifierlessKeybind> keybind,
				Function<TabKeybindsConfig, Color> color,
				Function<TabKeybindsConfig, TabKeybindsConfig.TabTextPosition> position,
				Function<TabKeybindsConfig, Integer> xOffset,
				Function<TabKeybindsConfig, Integer> yOffset,
				int hardcodedXOffset,
				int hardcodedRightOffset)
		{
			this.fixed = fixed;
			this.resizableModern = resizableModern;
			this.resizableClassic = resizableClassic;
			this.keybind = keybind;
			this.color = color;
			this.position = position;
			this.xOffset = xOffset;
			this.yOffset = yOffset;
			this.hardcodedXOffset = hardcodedXOffset;
			this.hardcodedRightOffset = hardcodedRightOffset;
		}
	}

	private final List<TabDef> tabs = Arrays.asList(
		new TabDef(InterfaceID.Toplevel.STONE0, InterfaceID.ToplevelOsrsStretch.STONE0, InterfaceID.ToplevelPreEoc.STONE0,
			TabKeybindsConfig::combatKeybind, TabKeybindsConfig::combatColor, TabKeybindsConfig::combatPosition,
			TabKeybindsConfig::combatXOffset, TabKeybindsConfig::combatYOffset, 1),
		new TabDef(InterfaceID.Toplevel.STONE1, InterfaceID.ToplevelOsrsStretch.STONE1, InterfaceID.ToplevelPreEoc.STONE1,
			TabKeybindsConfig::statsKeybind, TabKeybindsConfig::statsColor, TabKeybindsConfig::statsPosition,
			TabKeybindsConfig::statsXOffset, TabKeybindsConfig::statsYOffset),
		new TabDef(InterfaceID.Toplevel.STONE2, InterfaceID.ToplevelOsrsStretch.STONE2, InterfaceID.ToplevelPreEoc.STONE2,
			TabKeybindsConfig::questsKeybind, TabKeybindsConfig::questsColor, TabKeybindsConfig::questsPosition,
			TabKeybindsConfig::questsXOffset, TabKeybindsConfig::questsYOffset, -2, -5),
		new TabDef(InterfaceID.Toplevel.STONE3, InterfaceID.ToplevelOsrsStretch.STONE3, InterfaceID.ToplevelPreEoc.STONE3,
			TabKeybindsConfig::inventoryKeybind, TabKeybindsConfig::inventoryColor, TabKeybindsConfig::inventoryPosition,
			TabKeybindsConfig::inventoryXOffset, TabKeybindsConfig::inventoryYOffset),
		new TabDef(InterfaceID.Toplevel.STONE4, InterfaceID.ToplevelOsrsStretch.STONE4, InterfaceID.ToplevelPreEoc.STONE4,
			TabKeybindsConfig::equipmentKeybind, TabKeybindsConfig::equipmentColor, TabKeybindsConfig::equipmentPosition,
			TabKeybindsConfig::equipmentXOffset, TabKeybindsConfig::equipmentYOffset),
		new TabDef(InterfaceID.Toplevel.STONE5, InterfaceID.ToplevelOsrsStretch.STONE5, InterfaceID.ToplevelPreEoc.STONE5,
			TabKeybindsConfig::prayerKeybind, TabKeybindsConfig::prayerColor, TabKeybindsConfig::prayerPosition,
			TabKeybindsConfig::prayerXOffset, TabKeybindsConfig::prayerYOffset),
		new TabDef(InterfaceID.Toplevel.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6, InterfaceID.ToplevelPreEoc.STONE6,
			TabKeybindsConfig::spellbookKeybind, TabKeybindsConfig::spellbookColor, TabKeybindsConfig::spellbookPosition,
			TabKeybindsConfig::spellbookXOffset, TabKeybindsConfig::spellbookYOffset, -1),
		new TabDef(InterfaceID.Toplevel.STONE7, InterfaceID.ToplevelOsrsStretch.STONE7, InterfaceID.ToplevelPreEoc.STONE7,
			TabKeybindsConfig::clanKeybind, TabKeybindsConfig::clanColor, TabKeybindsConfig::clanPosition,
			TabKeybindsConfig::clanXOffset, TabKeybindsConfig::clanYOffset, 1),
		new TabDef(InterfaceID.Toplevel.STONE9, InterfaceID.ToplevelOsrsStretch.STONE9, InterfaceID.ToplevelPreEoc.STONE9,
			TabKeybindsConfig::friendsKeybind, TabKeybindsConfig::friendsColor, TabKeybindsConfig::friendsPosition,
			TabKeybindsConfig::friendsXOffset, TabKeybindsConfig::friendsYOffset),
		new TabDef(InterfaceID.Toplevel.STONE8, InterfaceID.ToplevelOsrsStretch.STONE8, InterfaceID.ToplevelPreEoc.STONE8,
			TabKeybindsConfig::accountManagementKeybind, TabKeybindsConfig::accountManagementColor, TabKeybindsConfig::accountManagementPosition,
			TabKeybindsConfig::accountManagementXOffset, TabKeybindsConfig::accountManagementYOffset),
		new TabDef(InterfaceID.Toplevel.STONE10, InterfaceID.ToplevelOsrsStretch.STONE10, InterfaceID.ToplevelPreEoc.STONE10,
			TabKeybindsConfig::logoutKeybind, TabKeybindsConfig::logoutColor, TabKeybindsConfig::logoutPosition,
			TabKeybindsConfig::logoutXOffset, TabKeybindsConfig::logoutYOffset),
		new TabDef(InterfaceID.Toplevel.STONE11, InterfaceID.ToplevelOsrsStretch.STONE11, InterfaceID.ToplevelPreEoc.STONE11,
			TabKeybindsConfig::optionsKeybind, TabKeybindsConfig::optionsColor, TabKeybindsConfig::optionsPosition,
			TabKeybindsConfig::optionsXOffset, TabKeybindsConfig::optionsYOffset),
		new TabDef(InterfaceID.Toplevel.STONE12, InterfaceID.ToplevelOsrsStretch.STONE12, InterfaceID.ToplevelPreEoc.STONE12,
			TabKeybindsConfig::emotesKeybind, TabKeybindsConfig::emotesColor, TabKeybindsConfig::emotesPosition,
			TabKeybindsConfig::emotesXOffset, TabKeybindsConfig::emotesYOffset),
		new TabDef(InterfaceID.Toplevel.STONE13, InterfaceID.ToplevelOsrsStretch.STONE13, InterfaceID.ToplevelPreEoc.STONE13,
			TabKeybindsConfig::musicKeybind, TabKeybindsConfig::musicColor, TabKeybindsConfig::musicPosition,
			TabKeybindsConfig::musicXOffset, TabKeybindsConfig::musicYOffset, -1)
	);

	private final Client client;
	private final TabKeybindsConfig config;

	@Inject
	private TabKeybindsOverlay(Client client, TabKeybindsConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());

		for (TabDef tab : tabs)
		{
			ModifierlessKeybind keybind = tab.keybind.apply(config);
			if (keybind == null || keybind.equals(TabKeybindsConfig.NO_KEYBIND))
			{
				continue;
			}

			Widget widget = client.getWidget(tab.fixed);
			if (widget == null || widget.isHidden())
			{
				widget = client.getWidget(tab.resizableModern);
			}
			if (widget == null || widget.isHidden())
			{
				widget = client.getWidget(tab.resizableClassic);
			}

			if (widget == null || widget.isHidden())
			{
				continue;
			}

			Rectangle bounds = widget.getBounds();
			if (bounds.width <= 0 || bounds.height <= 0)
			{
				continue;
			}

			// Only ever shown as a single key. A recorded key+modifier combo
			// (e.g. Ctrl+A) shows just the base key, ignoring the modifier.
			// A modifier held alone (e.g. just Ctrl) is its own valid single
			// key and is shown by name, since it has no base keyCode.
			String text;
			if (keybind.getKeyCode() == KeyEvent.VK_UNDEFINED)
			{
				text = modifierName(keybind.getModifiers());
			}
			else
			{
				text = KeyEvent.getKeyText(keybind.getKeyCode());
				if (text.equals("Escape"))
				{
					text = "Esc";
				}
			}

			if (text == null)
			{
				continue;
			}

			switch (config.textCase())
			{
				case UPPERCASE:
					text = text.toUpperCase();
					break;
				case LOWERCASE:
					text = text.toLowerCase();
					break;
				case DEFAULT:
				default:
					break;
			}

			FontMetrics metrics = graphics.getFontMetrics();
			int textWidth = metrics.stringWidth(text);

			TabKeybindsConfig.TabTextPosition position = tab.position.apply(config);
			int[] xy = textXY(bounds, textWidth, metrics, position);
			int x = xy[0];
			int y = xy[1];

			// The user's own X/Y offset only applies when Position is
			// explicitly Custom. The hardcoded per-tab correction (for the
			// few tabs whose icon art isn't centered in its widget) applies
			// in both Centre and Custom, stacking invisibly on top of the
			// user's own offset -- never shown or reflected in the config.
			if (position == TabKeybindsConfig.TabTextPosition.CUSTOM)
			{
				x += tab.xOffset.apply(config);
				y += tab.yOffset.apply(config);
			}
			if (position == TabKeybindsConfig.TabTextPosition.CENTER
				|| position == TabKeybindsConfig.TabTextPosition.CUSTOM)
			{
				x += tab.hardcodedXOffset;
			}
			if (position == TabKeybindsConfig.TabTextPosition.TOP_RIGHT
				|| position == TabKeybindsConfig.TabTextPosition.BOTTOM_RIGHT)
			{
				x += tab.hardcodedRightOffset;
			}

			// Keep the text within the tab's own widget bounds no matter
			// how large an offset is configured, instead of relying on a
			// fixed min/max on the offset values themselves.
			x = Math.max(bounds.x, Math.min(x, bounds.x + bounds.width - textWidth));
			y = Math.max(bounds.y + metrics.getAscent(), Math.min(y, bounds.y + bounds.height - metrics.getDescent()));

			Color drawColor = config.useGlobalColor() ? config.textColor() : tab.color.apply(config);

			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x - 1, y - 1);
			graphics.drawString(text, x, y - 1);
			graphics.drawString(text, x + 1, y - 1);
			graphics.drawString(text, x - 1, y);
			graphics.drawString(text, x + 1, y);
			graphics.drawString(text, x - 1, y + 1);
			graphics.drawString(text, x, y + 1);
			graphics.drawString(text, x + 1, y + 1);

			graphics.setColor(drawColor);
			graphics.drawString(text, x, y);
		}

		return null;
	}

	/**
	 * Name for a keybind that's a bare modifier key with no companion key
	 * (e.g. just Ctrl on its own), since {@link KeyEvent#getKeyText} can't
	 * be used for those (their keyCode is VK_UNDEFINED).
	 */
	private static String modifierName(int modifiers)
	{
		if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0)
		{
			return "Ctrl";
		}
		if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0)
		{
			return "Alt";
		}
		if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
		{
			return "Shift";
		}
		if ((modifiers & InputEvent.META_DOWN_MASK) != 0)
		{
			return "Meta";
		}
		return null;
	}

	/**
	 * Computes the draw-string origin (baseline) for the given text within
	 * a tab's bounds, anchored per the configured position. Custom shares
	 * Centre's base anchor -- its offset is applied separately by the caller.
	 */
	private static int[] textXY(Rectangle bounds, int textWidth, FontMetrics metrics, TabKeybindsConfig.TabTextPosition position)
	{
		int ascent = metrics.getAscent();
		int descent = metrics.getDescent();
		int x;
		int y;

		switch (position)
		{
			case TOP_LEFT:
				x = bounds.x + EDGE_INSET;
				y = bounds.y + EDGE_INSET + ascent;
				break;
			case TOP_RIGHT:
				x = bounds.x + bounds.width - textWidth - EDGE_INSET;
				y = bounds.y + EDGE_INSET + ascent;
				break;
			case BOTTOM_LEFT:
				x = bounds.x + EDGE_INSET;
				y = bounds.y + bounds.height - EDGE_INSET - descent;
				break;
			case BOTTOM_RIGHT:
				x = bounds.x + bounds.width - textWidth - EDGE_INSET;
				y = bounds.y + bounds.height - EDGE_INSET - descent;
				break;
			case CENTER:
			case CUSTOM:
			default:
				x = bounds.x + (bounds.width - textWidth) / 2;
				y = bounds.y + bounds.height / 2 + ascent / 2;
				break;
		}

		return new int[] { x, y };
	}
}
