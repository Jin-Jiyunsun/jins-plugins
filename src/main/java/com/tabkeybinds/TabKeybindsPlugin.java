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
import net.runelite.client.config.Keybind;
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

		ModifierlessKeybind remapped;
		switch (label.toUpperCase())
		{
			case "F1":
				remapped = remapConfig.f1();
				break;
			case "F2":
				remapped = remapConfig.f2();
				break;
			case "F3":
				remapped = remapConfig.f3();
				break;
			case "F4":
				remapped = remapConfig.f4();
				break;
			case "F5":
				remapped = remapConfig.f5();
				break;
			case "F6":
				remapped = remapConfig.f6();
				break;
			case "F7":
				remapped = remapConfig.f7();
				break;
			case "F8":
				remapped = remapConfig.f8();
				break;
			case "F9":
				remapped = remapConfig.f9();
				break;
			case "F10":
				remapped = remapConfig.f10();
				break;
			case "F11":
				remapped = remapConfig.f11();
				break;
			case "F12":
				remapped = remapConfig.f12();
				break;
			case "ESC":
			case "ESCAPE":
				remapped = remapConfig.esc();
				break;
			default:
				return null;
		}

		return remapped == null || remapped.equals(Keybind.NOT_SET) ? null : remapped;
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
			if (remainder.length() > 1 && (remainder.charAt(0) == '-' || remainder.charAt(0) == '_'))
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
		map.put("caps lock", "Caps");
		map.put("num lock", "Num");
		map.put("scroll lock", "Scrl");
		map.put("print screen", "PrtSc");
		map.put("context menu", "Menu");
		map.put("space", "Spc");
		return Collections.unmodifiableMap(map);
	}
}
