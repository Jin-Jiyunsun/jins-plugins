package com.tabkeybinds;

import com.google.inject.Provides;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ModifierlessKeybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.keyremapping.KeyRemappingConfig;
import net.runelite.client.plugins.keyremapping.KeyRemappingPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Tab Keybind Display",
	description = "Shows your in-game keybind as text on each interface tab",
	tags = {"keybind", "tabs", "hud", "overlay"}
)
public class TabKeybindsPlugin extends Plugin
{
	private static final int KEY_LABEL_ENUM = 1159;
	private static final String KEY_REMAPPING_CONFIG_GROUP = "keyremapping";

	private static final Map<String, String> SYMBOL_NAMES = createSymbolNames();
	private static final Map<String, String> ABBREVIATIONS = createAbbreviations();

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TabKeybindsOverlay overlay;

	private final Map<Integer, String> keyText = new HashMap<>();

	@Provides
	TabKeybindsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TabKeybindsConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyText.clear();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		keyText.clear();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (TabKeybindsOverlay.KEY_VARBITS.contains(event.getVarbitId()))
		{
			keyText.clear();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		keyText.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (KEY_REMAPPING_CONFIG_GROUP.equals(event.getGroup()))
		{
			keyText.clear();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() instanceof KeyRemappingPlugin)
		{
			keyText.clear();
		}
	}

	String getKeyText(int varbitId)
	{
		if (keyText.containsKey(varbitId))
		{
			return keyText.get(varbitId);
		}

		String text = resolveKeyText(varbitId);
		keyText.put(varbitId, text);
		return text;
	}

	private String resolveKeyText(int varbitId)
	{
		int keyIndex = client.getVarbitValue(varbitId);
		if (keyIndex <= 0)
		{
			return null;
		}

		EnumComposition keyLabels = client.getEnum(KEY_LABEL_ENUM);
		if (keyLabels == null)
		{
			return null;
		}

		String label = keyLabels.getStringValue(keyIndex);
		if (label == null || label.isEmpty())
		{
			return null;
		}

		label = label.trim();

		ModifierlessKeybind remapped = remappedKeybind(label);
		return remapped != null ? keybindText(remapped) : shortKeyName(label);
	}

	private ModifierlessKeybind remappedKeybind(String label)
	{
		if (!isKeyRemappingActive())
		{
			return null;
		}

		KeyRemappingConfig remapConfig = configManager.getConfig(KeyRemappingConfig.class);
		if (!remapConfig.fkeyRemap())
		{
			return null;
		}

		switch (label.toUpperCase())
		{
			case "F1":
				return remapConfig.f1();
			case "F2":
				return remapConfig.f2();
			case "F3":
				return remapConfig.f3();
			case "F4":
				return remapConfig.f4();
			case "F5":
				return remapConfig.f5();
			case "F6":
				return remapConfig.f6();
			case "F7":
				return remapConfig.f7();
			case "F8":
				return remapConfig.f8();
			case "F9":
				return remapConfig.f9();
			case "F10":
				return remapConfig.f10();
			case "F11":
				return remapConfig.f11();
			case "F12":
				return remapConfig.f12();
			case "ESC":
			case "ESCAPE":
				return remapConfig.esc();
			default:
				return null;
		}
	}

	private boolean isKeyRemappingActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof KeyRemappingPlugin)
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	private static String keybindText(ModifierlessKeybind keybind)
	{
		if (keybind.getKeyCode() == KeyEvent.VK_UNDEFINED)
		{
			return modifierName(keybind.getModifiers());
		}
		return shortKeyName(KeyEvent.getKeyText(keybind.getKeyCode()));
	}

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

	private static String shortKeyName(String name)
	{
		if (name == null)
		{
			return null;
		}

		if ("Escape".equalsIgnoreCase(name) || "ESC".equalsIgnoreCase(name))
		{
			return "Esc";
		}

		String lower = name.toLowerCase();

		if (lower.startsWith("numpad"))
		{
			String remainder = name.substring(6).trim();
			while (!remainder.isEmpty() && (remainder.charAt(0) == '-' || remainder.charAt(0) == '_'))
			{
				remainder = remainder.substring(1).trim();
			}
			if (!remainder.isEmpty())
			{
				return "#" + shortKeyName(remainder);
			}
		}

		String symbol = SYMBOL_NAMES.get(lower);
		if (symbol != null)
		{
			return symbol;
		}

		String abbreviation = ABBREVIATIONS.get(lower);
		return abbreviation != null ? abbreviation : name;
	}

	private static Map<String, String> createSymbolNames()
	{
		Map<String, String> map = new HashMap<>();
		map.put("semicolon", ";");
		map.put("comma", ",");
		map.put("period", ".");
		map.put("slash", "/");
		map.put("backslash", "\\");
		map.put("back slash", "\\");
		map.put("quote", "'");
		map.put("minus", "-");
		map.put("equals", "=");
		map.put("openbracket", "[");
		map.put("open bracket", "[");
		map.put("closebracket", "]");
		map.put("close bracket", "]");
		map.put("backquote", "`");
		map.put("back quote", "`");
		map.put("grave", "`");
		map.put("grave accent", "`");
		map.put("numbersign", "#");
		map.put("number sign", "#");
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> createAbbreviations()
	{
		Map<String, String> map = new HashMap<>();
		map.put("backspace", "Bksp");
		map.put("page up", "Pg Up");
		map.put("pageup", "Pg Up");
		map.put("page down", "Pg Dn");
		map.put("pagedown", "Pg Dn");
		map.put("delete", "Del");
		map.put("insert", "Ins");
		map.put("windows", "Win");
		return Collections.unmodifiableMap(map);
	}
}
