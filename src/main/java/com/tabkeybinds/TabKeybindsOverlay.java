package com.tabkeybinds;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class TabKeybindsOverlay extends Overlay
{
	private static final int EDGE_INSET = 2;

	private static final class TabDef
	{
		final int fixed;
		final int resizableModern;
		final int resizableClassic;
		final int varbit;
		final Function<TabKeybindsConfig, Color> color;
		final Function<TabKeybindsConfig, TabKeybindsConfig.TabTextPosition> position;
		final Function<TabKeybindsConfig, Integer> xOffset;
		final Function<TabKeybindsConfig, Integer> yOffset;

		int fallbackVarbit;
		int hardcodedXOffset;
		int hardcodedRightOffset;

		TabDef(int fixed, int resizableModern, int resizableClassic, int varbit,
				Function<TabKeybindsConfig, Color> color,
				Function<TabKeybindsConfig, TabKeybindsConfig.TabTextPosition> position,
				Function<TabKeybindsConfig, Integer> xOffset,
				Function<TabKeybindsConfig, Integer> yOffset)
		{
			this.fixed = fixed;
			this.resizableModern = resizableModern;
			this.resizableClassic = resizableClassic;
			this.varbit = varbit;
			this.color = color;
			this.position = position;
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}

		TabDef fallbackVarbit(int varbit)
		{
			this.fallbackVarbit = varbit;
			return this;
		}

		TabDef centreOffset(int offset)
		{
			this.hardcodedXOffset = offset;
			return this;
		}

		TabDef rightOffset(int offset)
		{
			this.hardcodedRightOffset = offset;
			return this;
		}
	}

	private static final List<TabDef> TABS = Arrays.asList(
		new TabDef(InterfaceID.Toplevel.STONE0, InterfaceID.ToplevelOsrsStretch.STONE0, InterfaceID.ToplevelPreEoc.STONE0,
			VarbitID.STONE_COMBAT_KEY,
			TabKeybindsConfig::combatColor, TabKeybindsConfig::combatPosition,
			TabKeybindsConfig::combatXOffset, TabKeybindsConfig::combatYOffset).centreOffset(1),
		new TabDef(InterfaceID.Toplevel.STONE1, InterfaceID.ToplevelOsrsStretch.STONE1, InterfaceID.ToplevelPreEoc.STONE1,
			VarbitID.STONE_STATS_KEY,
			TabKeybindsConfig::statsColor, TabKeybindsConfig::statsPosition,
			TabKeybindsConfig::statsXOffset, TabKeybindsConfig::statsYOffset),
		new TabDef(InterfaceID.Toplevel.STONE2, InterfaceID.ToplevelOsrsStretch.STONE2, InterfaceID.ToplevelPreEoc.STONE2,
			VarbitID.STONE_JOURNAL_KEY,
			TabKeybindsConfig::questsColor, TabKeybindsConfig::questsPosition,
			TabKeybindsConfig::questsXOffset, TabKeybindsConfig::questsYOffset).centreOffset(-2).rightOffset(-5),
		new TabDef(InterfaceID.Toplevel.STONE3, InterfaceID.ToplevelOsrsStretch.STONE3, InterfaceID.ToplevelPreEoc.STONE3,
			VarbitID.STONE_INV_KEY,
			TabKeybindsConfig::inventoryColor, TabKeybindsConfig::inventoryPosition,
			TabKeybindsConfig::inventoryXOffset, TabKeybindsConfig::inventoryYOffset),
		new TabDef(InterfaceID.Toplevel.STONE4, InterfaceID.ToplevelOsrsStretch.STONE4, InterfaceID.ToplevelPreEoc.STONE4,
			VarbitID.STONE_WORN_KEY,
			TabKeybindsConfig::equipmentColor, TabKeybindsConfig::equipmentPosition,
			TabKeybindsConfig::equipmentXOffset, TabKeybindsConfig::equipmentYOffset),
		new TabDef(InterfaceID.Toplevel.STONE5, InterfaceID.ToplevelOsrsStretch.STONE5, InterfaceID.ToplevelPreEoc.STONE5,
			VarbitID.STONE_PRAYER_KEY,
			TabKeybindsConfig::prayerColor, TabKeybindsConfig::prayerPosition,
			TabKeybindsConfig::prayerXOffset, TabKeybindsConfig::prayerYOffset),
		new TabDef(InterfaceID.Toplevel.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6, InterfaceID.ToplevelPreEoc.STONE6,
			VarbitID.STONE_MAGIC_KEY,
			TabKeybindsConfig::spellbookColor, TabKeybindsConfig::spellbookPosition,
			TabKeybindsConfig::spellbookXOffset, TabKeybindsConfig::spellbookYOffset).centreOffset(-1),
		new TabDef(InterfaceID.Toplevel.STONE7, InterfaceID.ToplevelOsrsStretch.STONE7, InterfaceID.ToplevelPreEoc.STONE7,
			VarbitID.STONE_CLANCHAT_KEY,
			TabKeybindsConfig::clanColor, TabKeybindsConfig::clanPosition,
			TabKeybindsConfig::clanXOffset, TabKeybindsConfig::clanYOffset).centreOffset(1),
		new TabDef(InterfaceID.Toplevel.STONE9, InterfaceID.ToplevelOsrsStretch.STONE9, InterfaceID.ToplevelPreEoc.STONE9,
			VarbitID.STONE_FRIENDS_KEY,
			TabKeybindsConfig::friendsColor, TabKeybindsConfig::friendsPosition,
			TabKeybindsConfig::friendsXOffset, TabKeybindsConfig::friendsYOffset),
		new TabDef(InterfaceID.Toplevel.STONE8, InterfaceID.ToplevelOsrsStretch.STONE8, InterfaceID.ToplevelPreEoc.STONE8,
			VarbitID.STONE_ACCOUNT_KEY,
			TabKeybindsConfig::accountManagementColor, TabKeybindsConfig::accountManagementPosition,
			TabKeybindsConfig::accountManagementXOffset, TabKeybindsConfig::accountManagementYOffset),
		new TabDef(InterfaceID.Toplevel.STONE10, InterfaceID.ToplevelOsrsStretch.STONE10, InterfaceID.ToplevelPreEoc.STONE10,
			VarbitID.STONE_LOGOUT_KEY,
			TabKeybindsConfig::logoutColor, TabKeybindsConfig::logoutPosition,
			TabKeybindsConfig::logoutXOffset, TabKeybindsConfig::logoutYOffset).fallbackVarbit(VarbitID.STONE_LOGOUT_KEY_DESKTOP),
		new TabDef(InterfaceID.Toplevel.STONE11, InterfaceID.ToplevelOsrsStretch.STONE11, InterfaceID.ToplevelPreEoc.STONE11,
			VarbitID.STONE_OPTIONS1_KEY,
			TabKeybindsConfig::optionsColor, TabKeybindsConfig::optionsPosition,
			TabKeybindsConfig::optionsXOffset, TabKeybindsConfig::optionsYOffset),
		new TabDef(InterfaceID.Toplevel.STONE12, InterfaceID.ToplevelOsrsStretch.STONE12, InterfaceID.ToplevelPreEoc.STONE12,
			VarbitID.STONE_OPTIONS2_KEY,
			TabKeybindsConfig::emotesColor, TabKeybindsConfig::emotesPosition,
			TabKeybindsConfig::emotesXOffset, TabKeybindsConfig::emotesYOffset),
		new TabDef(InterfaceID.Toplevel.STONE13, InterfaceID.ToplevelOsrsStretch.STONE13, InterfaceID.ToplevelPreEoc.STONE13,
			VarbitID.STONE_MUSIC_KEY,
			TabKeybindsConfig::musicColor, TabKeybindsConfig::musicPosition,
			TabKeybindsConfig::musicXOffset, TabKeybindsConfig::musicYOffset).centreOffset(-1)
	);

	static final Set<Integer> KEY_VARBITS = keyVarbits();

	private final Client client;
	private final TabKeybindsConfig config;
	private final TabKeybindsPlugin plugin;

	@Inject
	private TabKeybindsOverlay(Client client, TabKeybindsConfig config, TabKeybindsPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	private static Set<Integer> keyVarbits()
	{
		Set<Integer> varbits = new HashSet<>();
		for (TabDef tab : TABS)
		{
			varbits.add(tab.varbit);
			if (tab.fallbackVarbit != 0)
			{
				varbits.add(tab.fallbackVarbit);
			}
		}
		return Collections.unmodifiableSet(varbits);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();

		for (TabDef tab : TABS)
		{
			String text = plugin.getKeyText(tab.varbit);
			if (text == null && tab.fallbackVarbit != 0)
			{
				text = plugin.getKeyText(tab.fallbackVarbit);
			}

			if (text == null)
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

			int textWidth = metrics.stringWidth(text);

			TabKeybindsConfig.TabTextPosition position = config.useGlobalPosition()
				? config.globalPosition()
				: tab.position.apply(config);
			int[] xy = textXY(bounds, textWidth, metrics, position);
			int x = xy[0];
			int y = xy[1];

			switch (position)
			{
				case CUSTOM:
					x += tab.xOffset.apply(config);
					y += tab.yOffset.apply(config);
					x += tab.hardcodedXOffset;
					break;
				case CENTER:
					x += tab.hardcodedXOffset;
					break;
				case TOP_RIGHT:
				case BOTTOM_RIGHT:
					x += tab.hardcodedRightOffset;
					break;
				default:
					break;
			}

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
