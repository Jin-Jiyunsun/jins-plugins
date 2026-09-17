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
 * Bar item IDs used to detect which bar is being worked at an anvil, by watching for a one-item
 * drop in the inventory - the same technique as CookingFish. Unlike smelting, no tie-break is
 * needed here: every smithable item consumes only one bar type (just possibly more than one of
 * it), never two different bars at once.
 */
final class SmithingBar
{
	static final Set<Integer> BAR_IDS = Set.of(
		ItemID.BRONZE_BAR,
		ItemID.IRON_BAR,
		ItemID.STEEL_BAR,
		ItemID.SILVER_BAR,
		ItemID.GOLD_BAR,
		ItemID.MITHRIL_BAR,
		ItemID.ADAMANTITE_BAR,
		ItemID.RUNITE_BAR,
		ItemID.BLURITE_BAR
	);

	private SmithingBar()
	{
	}
}
