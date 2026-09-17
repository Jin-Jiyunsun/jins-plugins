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

import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Felling axe (Forestry's 2h axe) item IDs, used to validate that whatever's actually in the
 * weapon slot when a felling animation plays is really a felling axe - not some unrelated weapon
 * the player happens to have equipped, which {@link SkillBubblesPlugin#resolveToolItemId} would
 * otherwise show by mistake.
 */
final class FellingAxes
{
	static final Set<Integer> ITEM_IDS = Set.of(
		ItemID.BRONZE_AXE_2H,
		ItemID.IRON_AXE_2H,
		ItemID.STEEL_AXE_2H,
		ItemID.BLACK_AXE_2H,
		ItemID.MITHRIL_AXE_2H,
		ItemID.ADAMANT_AXE_2H,
		ItemID.RUNE_AXE_2H,
		ItemID.DRAGON_AXE_2H,
		ItemID.CRYSTAL_AXE_2H,
		ItemID._3A_AXE_2H
	);

	private FellingAxes()
	{
	}
}
