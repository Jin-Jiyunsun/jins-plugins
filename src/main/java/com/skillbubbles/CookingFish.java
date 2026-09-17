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
 * Raw fish item IDs, used to detect which fish is currently being cooked by watching for a
 * one-item drop in the inventory. Deliberately excludes a few raw fish whose only gameval name
 * is prefixed by the quest/activity that introduced them (raw karambwan, raw slimy eel, raw
 * rainbow fish) - kept out to avoid guessing whether that's really the same item used in normal
 * cooking, not because they're unsupported in principle.
 */
final class CookingFish
{
	static final Set<Integer> RAW_FISH_IDS = Set.of(
		ItemID.RAW_SHRIMP,
		ItemID.RAW_ANCHOVIES,
		ItemID.RAW_SARDINE,
		ItemID.RAW_SALMON,
		ItemID.RAW_TROUT,
		ItemID.RAW_COD,
		ItemID.RAW_HERRING,
		ItemID.RAW_PIKE,
		ItemID.RAW_MACKEREL,
		ItemID.RAW_BASS,
		ItemID.RAW_TUNA,
		ItemID.RAW_SWORDFISH,
		ItemID.RAW_LOBSTER,
		ItemID.RAW_SHARK,
		ItemID.RAW_MANTARAY,
		ItemID.RAW_SEATURTLE,
		ItemID.RAW_MONKFISH,
		ItemID.RAW_CAVE_EEL,
		ItemID.RAW_LAVA_EEL,
		ItemID.RAW_DARK_CRAB,
		ItemID.RAW_ANGLERFISH
	);

	private CookingFish()
	{
	}
}
