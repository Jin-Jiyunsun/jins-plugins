package com.livelyfishingspots;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LivelyFishingSpotsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LivelyFishingSpotsPlugin.class, LivelyFishingSpotsDebugPlugin.class);
		RuneLite.main(args);
	}
}
