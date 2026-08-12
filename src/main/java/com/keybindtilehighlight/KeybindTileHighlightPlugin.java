/*
 * Copyright (c) 2026, Jin-Jiyunsun
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.keybindtilehighlight;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = "Keybind Tile Highlight",
	description = "Highlight the tile under the mouse while a hotkey is held",
	tags = {"tiles", "overlay", "highlight", "hotkey"}
)
public class KeybindTileHighlightPlugin extends Plugin
{
	@Inject
	private KeybindTileHighlightConfig config;

	@Inject
	private KeybindTileHighlightOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	/**
	 * Written on the AWT thread by the hotkey listener, read on the client thread by the
	 * overlay, so it must be volatile.
	 */
	@Getter
	private volatile boolean hotkeyHeld;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		keyManager.registerKeyListener(hotkeyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		overlayManager.remove(overlay);
		// Don't leave the highlight latched on if the plugin is disabled mid-press
		hotkeyHeld = false;
	}

	@Provides
	KeybindTileHighlightConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KeybindTileHighlightConfig.class);
	}

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.highlightKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			hotkeyHeld = true;
		}

		@Override
		public void hotkeyReleased()
		{
			hotkeyHeld = false;
		}
	};
}
