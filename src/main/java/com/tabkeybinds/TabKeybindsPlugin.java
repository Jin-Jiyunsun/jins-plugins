package com.tabkeybinds;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Tab Keybinds",
	description = "Shows your configured keybind as text on each interface tab",
	tags = {"keybind", "tabs", "hud", "overlay"}
)
public class TabKeybindsPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TabKeybindsOverlay overlay;

	@Provides
	TabKeybindsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TabKeybindsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}
}
