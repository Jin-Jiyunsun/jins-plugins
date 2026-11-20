package com.barbershop;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BarberShopPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BarberShopPlugin.class, BarberShopDebugPlugin.class);
		RuneLite.main(args);
	}
}
