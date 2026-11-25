package com.jinjiyunsun.pohjinglemute;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PohJingleMutePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PohJingleMutePlugin.class, PohJingleMuteProbePlugin.class);
		RuneLite.main(args);
	}
}
