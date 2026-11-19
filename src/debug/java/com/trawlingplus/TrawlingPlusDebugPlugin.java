package com.trawlingplus;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Dev-only tools for Trawling Plus, never part of the released plugin: times its drawing through the drawTimer and
 * mapTimer hooks.
 */
@PluginDescriptor(
	name = "Trawling Plus Debug",
	description = "Debug tools for Trawling Plus",
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
		MapRoutes.mapTimer = timingOverlay;
		overlayManager.add(timingOverlay);
	}

	@Override
	protected void shutDown()
	{
		TrawlingPlusOverlay.drawTimer = null;
		MapRoutes.mapTimer = null;
		overlayManager.remove(timingOverlay);
	}
}
