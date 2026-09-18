package com.seteffects;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@PluginDescriptor(
	name = "Set Effect Display",
	description = "Shows the passive set and gear effects of worn items, on hover and in a scrollable list, in the equipment stats screen",
	tags = {"equipment", "tooltip", "set", "barrows", "void", "moons"}
)
public class SetEffectsPlugin extends Plugin
{
	private static final int WRAP_WIDTH = 50;
	// Wintertodt's bank camp and its arena (no gameval for region ids)
	private static final int WINTERTODT_CAMP_REGION = 6461;
	private static final int WINTERTODT_ARENA_REGION = 6462;
	private static final String TOOLTIP_LINE_BREAK = "</br>";

	// A single space, not empty - keeps the native text widget's own layout/sizing stable
	private static final String BLANK_TEXT = " ";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private SetEffectsOverlay overlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SetEffectDisplayConfig config;

	// Cached from config (rebuilt on startUp and whenever our config group changes) so the
	// per-frame paths never touch ConfigManager; volatile because ConfigChanged isn't guaranteed
	// to arrive on the client thread that reads these every frame
	private volatile Set<EffectFamily> disabledFamilies = Collections.emptySet();
	private volatile boolean showSetEffectList = true;
	private volatile boolean showTooltips = true;
	private volatile boolean verbose = false;
	// Bumped whenever the config is re-read, so cached results built from it can tell they are stale
	private volatile int configVersion;
	private volatile WarmClothingDisplay warmClothingDisplay = WarmClothingDisplay.AT_WINTERTODT;

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
	// Blocks of the game's text for sets we don't track ourselves - see VanillaOnlySets; rebuilt
	// only when the game rewrites its text, read every frame by the overlay
	private volatile List<EffectLine> vanillaOnlyLines = Collections.emptyList();
	private RenderKey tooltipKey;
	private String tooltipText;
	private final DiaryChecks diaryChecks = new DiaryChecks(this::isHardKandarinDiaryComplete, this::isHardKourendDiaryComplete);

	@Override
	protected void startUp()
	{
		refreshConfig();
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(overlay);
		mouseManager.registerMouseWheelListener(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(overlay);
		mouseManager.unregisterMouseWheelListener(overlay);
		// Put the game's own text back - it only rewrites it on equipment changes, so our blank would
		// otherwise stay until then
		clientThread.invoke(this::restoreNativeText);
	}

	@Provides
	SetEffectDisplayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SetEffectDisplayConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SetEffectDisplayConfig.GROUP.equals(event.getGroup()))
		{
			refreshConfig();
		}
	}

	private void refreshConfig()
	{
		showSetEffectList = config.showSetEffectList();
		showTooltips = config.showTooltips();
		verbose = config.verbose();
		warmClothingDisplay = config.warmClothing();

		Set<EffectFamily> disabled = EnumSet.noneOf(EffectFamily.class);
		for (EffectFamily family : EffectFamily.values())
		{
			// An unset key means the default (enabled) - ConfigManager drops keys set back to default
			Boolean value = configManager.getConfiguration(SetEffectDisplayConfig.GROUP, family.configKey, Boolean.class);
			if (value != null && !value)
			{
				disabled.add(family);
			}
		}
		disabledFamilies = disabled;
		configVersion++;
	}

	boolean isFamilyEnabled(EffectFamily family)
	{
		return !disabledFamilies.contains(family);
	}

	/** Read on the client thread, and only when the list is actually being built. */
	boolean isWarmClothingShown()
	{
		switch (warmClothingDisplay)
		{
			case ALWAYS:
				return true;
			case AT_WINTERTODT:
				return isAtWintertodt();
			default:
				return false;
		}
	}

	private boolean isAtWintertodt()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}

		int region = player.getWorldLocation().getRegionID();
		return region == WINTERTODT_CAMP_REGION || region == WINTERTODT_ARENA_REGION;
	}

	int getConfigVersion()
	{
		return configVersion;
	}

	boolean isVerbose()
	{
		return verbose;
	}

	boolean isSetEffectListEnabled()
	{
		return showSetEffectList;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		// Everything here is for the equipment stats popup, so do nothing at all while it's closed
		Widget popup = client.getWidget(InterfaceID.Equipment.UNIVERSE);
		if (popup == null || popup.isHidden())
		{
			overlay.popupClosed();
			return;
		}

		// Runs every frame, right before it's drawn - blanking here (rather than once per game
		// tick) keeps the window where the native script's own text could flash through under
		// our overlay as small as possible
		blankSetEffectWidget();

		if (!showTooltips || client.isMenuOpen())
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
			vanillaOnlyLines = VanillaOnlySets.parse(currentText);
		}

		if (!showSetEffectList)
		{
			// List turned off: stop blanking, and put the game's own text back if our blank is
			// still what's showing (the game only rewrites it on equipment changes)
			restoreNativeText();
			return;
		}

		setEffectText.setText(BLANK_TEXT);
		lastWrittenText = BLANK_TEXT;
	}

	/** Puts the game's own text back in the box, but only if what's showing is still our blank. */
	private void restoreNativeText()
	{
		Widget setEffectContainer = client.getWidget(InterfaceID.Equipment.SET_EFFECT);
		Widget setEffectText = setEffectContainer != null ? setEffectContainer.getChild(0) : null;
		if (setEffectText != null && lastWrittenText != null && lastWrittenText.equals(setEffectText.getText()))
		{
			setEffectText.setText(lastNativeBaseText);
		}
		lastWrittenText = null;
	}

	List<EffectLine> getVanillaOnlyLines()
	{
		return vanillaOnlyLines;
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

	/**
	 * Read on the client thread (overlay render / BeforeRender), and only when an enchanted bolt is
	 * actually being described - see {@link EquippedEffects}.
	 */
	private boolean isHardKandarinDiaryComplete()
	{
		return client.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1;
	}

	private boolean isHardKourendDiaryComplete()
	{
		return client.getVarbitValue(VarbitID.KOUREND_DIARY_HARD_COMPLETE) == 1;
	}

	DiaryChecks getDiaryChecks()
	{
		return diaryChecks;
	}

	private String buildTooltip(int rawItemId)
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return null;
		}

		// Same item, same gear, same config: reuse the last text rather than rebuilding it every frame
		RenderKey key = RenderKey.of(equipment, diaryChecks, configVersion, false, 0, Collections.emptyList(), "", null, rawItemId);
		if (key.equals(tooltipKey))
		{
			return tooltipText;
		}
		tooltipKey = key;
		tooltipText = buildTooltipText(equipment, rawItemId);
		return tooltipText;
	}

	private String buildTooltipText(ItemContainer equipment, int rawItemId)
	{
		List<EffectLine> lines = EquippedEffects.describeItem(equipment, rawItemId, this::isFamilyEnabled, verbose, diaryChecks);
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
