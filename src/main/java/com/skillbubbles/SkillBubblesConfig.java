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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("skill-bubbles")
public interface SkillBubblesConfig extends Config
{
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
		keyName = "classicSprites",
		name = "RuneScape Classic sprites",
		description = "Use RuneScape Classic-style icons instead of the<br>"
			+ "current game's, where a classic sprite exists"
	)
	default boolean classicSprites()
	{
		return false;
	}

	enum IconMode
	{
		SKILL,
		TOOL
	}
}
