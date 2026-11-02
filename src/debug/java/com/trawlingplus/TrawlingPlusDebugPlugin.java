package com.trawlingplus;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Dev-only timing for Trawling Plus, loaded by the dev client and never part of the released plugin: times its main
 * overlay through the package-private drawTimer hook.
 */
@PluginDescriptor(
	name = "Trawling Plus Debug",
	description = "Debug timing for Trawling Plus",
	developerPlugin = true
)
public class TrawlingPlusDebugPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TrawlingPlusDebugTimingOverlay timingOverlay;

	@Provides
	TrawlingPlusDebugConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TrawlingPlusDebugConfig.class);
	}

	@Override
	protected void startUp()
	{
		TrawlingPlusOverlay.drawTimer = timingOverlay;
		overlayManager.add(timingOverlay);
	}

	@Override
	protected void shutDown()
	{
		TrawlingPlusOverlay.drawTimer = null;
		overlayManager.remove(timingOverlay);
	}
}
