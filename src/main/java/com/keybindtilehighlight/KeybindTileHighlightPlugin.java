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
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
		name = "Keybind Tile Highlight",
		description = "Highlight the tile under the mouse while a keybind is held",
		tags = {"tiles", "overlay", "highlight", "keybind"}
)
public class KeybindTileHighlightPlugin extends Plugin implements KeyListener
{
	@Inject
	private KeybindTileHighlightConfig config;

	@Inject
	private KeybindTileHighlightOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Getter
	private volatile boolean keybindHeld;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(overlay);
		keybindHeld = false;
	}

	@Provides
	KeybindTileHighlightConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KeybindTileHighlightConfig.class);
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (matches(e))
		{
			keybindHeld = true;
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (matches(e))
		{
			keybindHeld = false;
		}
	}

	@Override
	public void focusLost()
	{
		keybindHeld = false;
	}

	private boolean matches(KeyEvent e)
	{
		Keybind keybind = config.highlightKeybind();
		if (Keybind.NOT_SET.equals(keybind))
		{
			return false;
		}

		int keyCode = e.getExtendedKeyCode();
		Integer eventModifier = Keybind.getModifierForKeyCode(keyCode);

		if (eventModifier != null && keybind.getKeyCode() == KeyEvent.VK_UNDEFINED)
		{
			return (keybind.getModifiers() & eventModifier) == eventModifier;
		}

		return keybind.matches(e);
	}
}
