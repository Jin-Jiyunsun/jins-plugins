package com.tabkeybinds;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TabKeybindsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TabKeybindsPlugin.class);
		RuneLite.main(args);
	}
}
