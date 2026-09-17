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
 * Ore item IDs used to detect which ore is being smelted, by watching for a one-item drop in
 * the inventory - the same technique as CookingFish. Deliberately excludes:
 * <ul>
 *     <li>Coal - every bar above iron uses it alongside the metal ore, but it's never the ore
 *     that should be shown (Jin, 2026-09-17: "not coal, the actual metal ore").</li>
 *     <li>Tin ore - bronze bar is the one recipe using two non-coal ores at once (copper + tin),
 *     so both would show a count drop on the same tick. Rather than a tie-break at detection
 *     time, tin is just never tracked - copper alone still fires correctly for bronze (Jin's
 *     call: copper is bronze's "primary" ingredient, tin the additive), and tin is never used
 *     in any other recipe.</li>
 * </ul>
 */
final class SmithingOre
{
	static final Set<Integer> ORE_IDS = Set.of(
		ItemID.COPPER_ORE,
		ItemID.IRON_ORE,
		ItemID.SILVER_ORE,
		ItemID.GOLD_ORE,
		ItemID.MITHRIL_ORE,
		ItemID.ADAMANTITE_ORE,
		ItemID.RUNITE_ORE,
		ItemID.BLURITE_ORE
	);

	private SmithingOre()
	{
	}
}
