package com.keybindtilehighlight;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KeybindTileHighlightPluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KeybindTileHighlightPlugin.class);
		RuneLite.main(args);
	}
}
