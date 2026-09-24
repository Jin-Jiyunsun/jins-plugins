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
 * Raw/uncooked item IDs, used to detect what's currently being cooked by watching for a
 * one-item drop in the inventory - fish, meat, and the single-item "uncooked X" forms that
 * bread/cakes/pizzas/pies/stews/curries take right up until the Cooking animation finishes them.
 * Deliberately excludes:
 * <ul>
 *     <li>A couple of raw fish whose only gameval name is prefixed by the quest/activity that
 *     introduced them (raw slimy eel, raw rainbow fish) - kept out to avoid guessing whether
 *     that's really the same item used in normal cooking. Raw karambwan had the same
 *     {@code TBWT_}-prefixed naming but Jin confirmed (2026-09-18) it's the one and only item id
 *     used whenever karambwan is cooked, quest or not, so it's tracked despite the name.</li>
 *     <li>Macro pheasant and its good/bad variants (an anti-botting decoy item, not real food),
 *     and raw mystery meat (name suggests a joke item) - confirmed excluded with Jin, 2026-09-18.
 *     Raw impaler meat is also excluded - it isn't cookable.</li>
 * </ul>
 * Pizzas are a single tracked id regardless of topping - {@code UNCOOKED_PIZZA} is the same item
 * whether it'll finish as a plain, meat, anchovy or pineapple pizza, the topping only becoming
 * distinct item ids once cooked. Beyond the three original pies (apple/meat/redberry), all the
 * other pie fillings each have their own distinct {@code UNCOOKED_X_PIE} id - mud, garden, fish,
 * admiral, wild, summer, botanical, mushroom and dragonfruit are all tracked too.
 */
final class CookingIngredients
{
	static final Set<Integer> ITEM_IDS = Set.of(
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
		ItemID.RAW_ANGLERFISH,
		ItemID.TBWT_RAW_KARAMBWAN,
		ItemID.RAW_CHICKEN,
		ItemID.RAW_BEEF,
		ItemID.RAW_BEAR_MEAT,
		ItemID.RAW_RAT_MEAT,
		ItemID.RAW_RABBIT,
		ItemID.RAW_GIANT_CARP,
		ItemID.RAW_UGTHANKI_MEAT,
		ItemID.RAW_CHOMPY,
		ItemID.RAW_BOAR_MEAT,
		ItemID.RAW_SWORDTIP_SQUID,
		ItemID.RAW_JUMBO_SQUID,
		ItemID.RAW_RED_CRAB_MEAT,
		ItemID.RAW_BLUE_CRAB_MEAT,
		ItemID.RAW_RAINBOW_CRAB_MEAT,
		ItemID.RAW_HADDOCK,
		ItemID.RAW_YELLOWFIN,
		ItemID.RAW_HALIBUT,
		ItemID.RAW_BLUEFIN,
		ItemID.RAW_MARLIN,
		// Not RAW_OOMLIE - that has to be wrapped in a palm leaf before it's cooked, so
		// WRAPPED_OOMLIE is the item actually consumed by the Cooking animation.
		ItemID.WRAPPED_OOMLIE,
		ItemID.RAW_GIANT_KRILL,
		ItemID.RAW_TBONE_STEAK,
		ItemID.RAW_GNOMEBOWL,
		ItemID.RAW_CRUNCHIES,
		ItemID.RAW_BATTA,
		ItemID.RAW_BEEF_UNDEAD,
		ItemID.RAW_CHICKEN_UNDEAD,
		ItemID.BREAD_DOUGH,
		ItemID.UNCOOKED_PITTA_BREAD,
		ItemID.UNCOOKED_CAKE,
		ItemID.UNCOOKED_PIZZA,
		ItemID.UNCOOKED_APPLE_PIE,
		ItemID.UNCOOKED_MEAT_PIE,
		ItemID.UNCOOKED_REDBERRY_PIE,
		ItemID.UNCOOKED_MUD_PIE,
		ItemID.UNCOOKED_GARDEN_PIE,
		ItemID.UNCOOKED_FISH_PIE,
		ItemID.UNCOOKED_ADMIRAL_PIE,
		ItemID.UNCOOKED_WILD_PIE,
		ItemID.UNCOOKED_SUMMER_PIE,
		ItemID.UNCOOKED_BOTANICAL_PIE,
		ItemID.UNCOOKED_MUSHROOM_PIE,
		ItemID.UNCOOKED_DRAGONFRUIT_PIE,
		ItemID.UNCOOKED_STEW,
		ItemID.UNCOOKED_CURRY
	);

	private CookingIngredients()
	{
	}
}
