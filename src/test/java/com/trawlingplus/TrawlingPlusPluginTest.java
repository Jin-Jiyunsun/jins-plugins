package com.trawlingplus;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TrawlingPlusPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TrawlingPlusPlugin.class, TrawlingPlusDebugPlugin.class);
		RuneLite.main(args);
	}
}
