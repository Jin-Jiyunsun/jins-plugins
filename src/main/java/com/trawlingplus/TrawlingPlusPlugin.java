package com.trawlingplus;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Trawling Plus",
	description = "Shows the routes that deep sea trawling shoals swim",
	tags = {"sailing", "trawling", "shoal", "fishing", "route"}
)
public class TrawlingPlusPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.debug("Trawling Plus started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Trawling Plus stopped");
	}
}
