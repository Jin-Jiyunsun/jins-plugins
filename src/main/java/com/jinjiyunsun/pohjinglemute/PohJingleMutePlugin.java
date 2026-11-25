package com.jinjiyunsun.pohjinglemute;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "POH Jingle Mute"
)
public class PohJingleMutePlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.debug("POH Jingle Mute started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("POH Jingle Mute stopped");
	}
}
