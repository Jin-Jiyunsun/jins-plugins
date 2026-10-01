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
package com.skillbubbles;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(SkillBubblesConfig.GROUP)
public interface SkillBubblesConfig extends Config
{
	String GROUP = "skill-bubbles";

	@ConfigItem(
		position = 0,
		keyName = "iconMode",
		name = "Icon",
		description = "Generic skill icon, or the specific tool being used<br>"
			+ "(falls back to the skill icon for actions with no single tool)"
	)
	default IconMode iconMode()
	{
		return IconMode.SKILL;
	}

	@Range(min = 75, max = 125)
	@Units(Units.PERCENT)
	@ConfigItem(
		position = 1,
		keyName = "scale",
		name = "Scale",
		description = "Size of the bubble and icon, as a percentage of<br>"
			+ "the default (100)"
	)
	default int scale()
	{
		return 100;
	}

	@Range(min = 0, max = 999)
	@Units(Units.SECONDS)
	@ConfigItem(
		position = 2,
		keyName = "idleSeconds",
		name = "Hide after",
		description = "How many seconds of no matching action before<br>"
			+ "the bubble disappears"
	)
	default int idleSeconds()
	{
		return 2;
	}

	@ConfigItem(
		position = 3,
		keyName = "fadeAnimation",
		name = "Fade in/out",
		description = "Fade the bubble in and out when it appears<br>"
			+ "and disappears"
	)
	default boolean fadeAnimation()
	{
		return false;
	}

	@ConfigItem(
		position = 4,
		keyName = "style",
		name = "Style",
		description = "Look of the bubble. Classic also swaps the icons<br>"
			+ "for RuneScape Classic-style art, where one exists"
	)
	default Style style()
	{
		return Style.GRADIENT;
	}

	@Alpha
	@ConfigItem(
		position = 5,
		keyName = "customFill",
		name = "Custom fill",
		description = "Fill colour of the bubble when Style is<br>"
			+ "set to Custom"
	)
	default Color customFill()
	{
		return new Color(0x15, 0x15, 0x15, 200);
	}

	@Alpha
	@ConfigItem(
		position = 6,
		keyName = "customRim",
		name = "Custom rim",
		description = "Rim colour of the bubble when Style is<br>"
			+ "set to Custom"
	)
	default Color customRim()
	{
		return new Color(0x0F, 0x0F, 0x0F);
	}

	enum IconMode
	{
		SKILL,
		TOOL
	}

	enum Style
	{
		GRADIENT("Gradient"),
		CLASSIC("Classic"),
		SPEECH_BUBBLE("Speech bubble"),
		ICON_ONLY("Icon only"),
		CUSTOM("Custom");

		private final String name;

		Style(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
