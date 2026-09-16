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

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;

/**
 * The stats-tab sprite for each skilling skill this plugin tracks. There is no gameval
 * constant for these (gameval's sprite IDs are unnamed indices into a widget's sprite list),
 * so this uses the hand-named {@link SpriteID} constants instead.
 */
final class SkillIcons
{
	private static final Map<Skill, Integer> SPRITE_IDS = new EnumMap<>(Skill.class);

	private SkillIcons()
	{
	}

	static Integer spriteId(Skill skill)
	{
		return SPRITE_IDS.get(skill);
	}

	static
	{
		SPRITE_IDS.put(Skill.WOODCUTTING, SpriteID.SKILL_WOODCUTTING);
		SPRITE_IDS.put(Skill.MINING, SpriteID.SKILL_MINING);
		SPRITE_IDS.put(Skill.FISHING, SpriteID.SKILL_FISHING);
		SPRITE_IDS.put(Skill.FIREMAKING, SpriteID.SKILL_FIREMAKING);
		SPRITE_IDS.put(Skill.COOKING, SpriteID.SKILL_COOKING);
		SPRITE_IDS.put(Skill.SMITHING, SpriteID.SKILL_SMITHING);
		SPRITE_IDS.put(Skill.RUNECRAFT, SpriteID.SKILL_RUNECRAFT);
		SPRITE_IDS.put(Skill.CRAFTING, SpriteID.SKILL_CRAFTING);
		SPRITE_IDS.put(Skill.FLETCHING, SpriteID.SKILL_FLETCHING);
		SPRITE_IDS.put(Skill.HERBLORE, SpriteID.SKILL_HERBLORE);
		SPRITE_IDS.put(Skill.HUNTER, SpriteID.SKILL_HUNTER);
		SPRITE_IDS.put(Skill.FARMING, SpriteID.SKILL_FARMING);
		SPRITE_IDS.put(Skill.CONSTRUCTION, SpriteID.SKILL_CONSTRUCTION);
		SPRITE_IDS.put(Skill.THIEVING, SpriteID.SKILL_THIEVING);
	}
}
