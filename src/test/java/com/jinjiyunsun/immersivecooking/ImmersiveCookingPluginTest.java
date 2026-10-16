package com.jinjiyunsun.immersivecooking;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ImmersiveCookingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ImmersiveCookingPlugin.class);
		RuneLite.main(args);
	}
}
