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

import net.runelite.api.Skill;

/**
 * One recognised skilling animation: which skill it belongs to, and which item (if any)
 * represents the tool used. {@link #NO_TOOL} means the action has no single hand-tool
 * (e.g. Runecraft, most of Cooking) - tool icon mode falls back to the skill icon for these.
 * {@link #EQUIPPED_WEAPON} means the tool varies by animation alone (e.g. Fishing, where the
 * same cast animation is used for a net, rod or harpoon) and must be read from the weapon
 * slot at the moment the animation plays.
 */
class SkillAction
{
	static final int NO_TOOL = -1;
	static final int EQUIPPED_WEAPON = -2;

	final Skill skill;
	final int toolItemId;

	SkillAction(Skill skill, int toolItemId)
	{
		this.skill = skill;
		this.toolItemId = toolItemId;
	}
}
