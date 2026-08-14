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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(KeybindTileHighlightConfig.GROUP)
public interface KeybindTileHighlightConfig extends Config
{
	String GROUP = "keybind-tile-highlight";

	enum RainbowMode
	{
		OFF("Off"),
		SOLID("Solid"),
		GRADIENT("Gradient");

		private final String label;

		RainbowMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(
			name = "Keybind 1",
			description = "Settings for the first highlight keybind.",
			position = 0
	)
	String keybind1Section = "keybind1Section";

	@ConfigSection(
			name = "Keybind 2",
			description = "Settings for the second highlight keybind.",
			position = 1
	)
	String keybind2Section = "keybind2Section";

	@ConfigItem(
			keyName = "highlightKeybind",
			name = "Highlight key",
			description = "Hold this key to highlight the tile under the mouse.",
			position = 0,
			section = keybind1Section
	)
	default Keybind highlightKeybind()
	{
		return Keybind.NOT_SET;
	}

	@Alpha
	@ConfigItem(
			keyName = "highlightColor",
			name = "Highlight colour",
			description = "Colour of the highlighted tile for keybind 1.",
			position = 1,
			section = keybind1Section
	)
	default Color highlightColor()
	{
		return Color.YELLOW;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
			keyName = "fillOpacity",
			name = "Fill opacity",
			description = "Opacity of the tile fill, as a percentage of keybind 1.",
			position = 2,
			section = keybind1Section
	)
	default int fillOpacity()
	{
		return 25;
	}

	@ConfigItem(
			keyName = "highlightKeybind2",
			name = "Highlight key",
			description = "Hold this key to highlight the tile under the mouse.",
			position = 0,
			section = keybind2Section
	)
	default Keybind highlightKeybind2()
	{
		return Keybind.NOT_SET;
	}

	@Alpha
	@ConfigItem(
			keyName = "highlightColor2",
			name = "Highlight colour",
			description = "Colour of the tile highlighted for keybind 2.",
			position = 1,
			section = keybind2Section
	)
	default Color highlightColor2()
	{
		return Color.CYAN;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
			keyName = "fillOpacity2",
			name = "Fill opacity",
			description = "Opacity of the tile fill, as a percentage for keybind 2.",
			position = 2,
			section = keybind2Section
	)
	default int fillOpacity2()
	{
		return 25;
	}

	@ConfigItem(
			keyName = "rainbowMode",
			name = "Rainbow mode",
			description = "<html>Off: Use each keybind's configured colour.<br>"
					+ "Solid: Cycle both highlights through a single hue.<br>"
					+ "Gradient: Scroll a rainbow gradient across the tile.</html>",
			position = 2
	)
	default RainbowMode rainbowMode()
	{
		return RainbowMode.OFF;
	}
}