package com.seteffects;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SetEffectsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SetEffectsPlugin.class);
		RuneLite.main(args);
	}
}
