package com.github.corhen.poisonring;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PoisonRingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PoisonRingPlugin.class);
		RuneLite.main(args);
	}
}