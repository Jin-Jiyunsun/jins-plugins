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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Maps items and skills to a RuneScape Classic-style sprite bundled under
 * {@code resources/com/skillbubbles/rsc/}, for the "RuneScape Classic sprites" config toggle.
 * Most entries are genuine RSC-era sprites, but plenty of post-RSC items reuse the closest
 * real RSC sprite as a stand-in instead of being left unmapped (each such case is commented
 * where it's assigned below) - skills that didn't exist in RSC at all (Farming, Construction,
 * Hunter, Runecraft) are the only ones with no entry at all, falling back to the normal OSRS
 * icon regardless of the toggle.
 */
final class RscSprites
{
	private static final Map<Integer, String> ITEM_FILES = new HashMap<>();
	private static final Map<Skill, String> SKILL_FILES = new EnumMap<>(Skill.class);
	private static final Map<String, BufferedImage> CACHE = new HashMap<>();

	private RscSprites()
	{
	}

	static BufferedImage forItem(int itemId)
	{
		return load(ITEM_FILES.get(itemId));
	}

	static BufferedImage forSkill(Skill skill)
	{
		return load(SKILL_FILES.get(skill));
	}

	private static BufferedImage load(String fileName)
	{
		return fileName == null ? null : CACHE.computeIfAbsent(fileName, RscSprites::read);
	}

	private static BufferedImage read(String fileName)
	{
		try (InputStream in = RscSprites.class.getResourceAsStream("rsc/" + fileName))
		{
			return in == null ? null : ImageIO.read(in);
		}
		catch (IOException e)
		{
			return null;
		}
	}

	static
	{
		// One representative tool per skill, for Icon mode's skill-icon display.
		SKILL_FILES.put(Skill.WOODCUTTING, "bronze_axe.png");
		SKILL_FILES.put(Skill.MINING, "bronze_pickaxe.png");
		SKILL_FILES.put(Skill.FISHING, "fishing_rod.png");
		SKILL_FILES.put(Skill.FIREMAKING, "tinderbox.png");
		SKILL_FILES.put(Skill.COOKING, "raw_shrimp.png");
		SKILL_FILES.put(Skill.SMITHING, "hammer.png");
		SKILL_FILES.put(Skill.CRAFTING, "needle.png");
		SKILL_FILES.put(Skill.FLETCHING, "knife.png");
		SKILL_FILES.put(Skill.HERBLORE, "pestle_and_mortar.png");
		SKILL_FILES.put(Skill.THIEVING, "coins.png");

		// Woodcutting axes
		ITEM_FILES.put(ItemID.BRONZE_AXE, "bronze_axe.png");
		ITEM_FILES.put(ItemID.IRON_AXE, "iron_axe.png");
		ITEM_FILES.put(ItemID.STEEL_AXE, "steel_axe.png");
		ITEM_FILES.put(ItemID.BLACK_AXE, "black_axe.png");
		ITEM_FILES.put(ItemID.MITHRIL_AXE, "mithril_axe.png");
		ITEM_FILES.put(ItemID.ADAMANT_AXE, "adamantite_axe.png");
		ITEM_FILES.put(ItemID.RUNE_AXE, "rune_axe.png");
		ITEM_FILES.put(ItemID.CRYSTAL_AXE, "crystal_axe.png");
		ITEM_FILES.put(ItemID.DRAGON_AXE, "dragon_axe.png");
		// No sprite of their own - reusing dragon_axe.png as the closest visual stand-in.
		ITEM_FILES.put(ItemID.INFERNAL_AXE, "dragon_axe.png");
		ITEM_FILES.put(ItemID.TRAILBLAZER_AXE, "dragon_axe.png");
		ITEM_FILES.put(ItemID.TRAILBLAZER_RELOADED_AXE, "dragon_axe.png");
		// No equivalent to reuse - Jin's call: use Steel as the stand-in.
		ITEM_FILES.put(ItemID._3A_AXE, "steel_axe.png");

		// Felling axe (Forestry's 2h axe) - reuses the matching material tier's normal axe
		// sprite, since it's the same metal, just a different (post-RSC) axe design.
		ITEM_FILES.put(ItemID.BRONZE_AXE_2H, "bronze_axe.png");
		ITEM_FILES.put(ItemID.IRON_AXE_2H, "iron_axe.png");
		ITEM_FILES.put(ItemID.STEEL_AXE_2H, "steel_axe.png");
		ITEM_FILES.put(ItemID.BLACK_AXE_2H, "black_axe.png");
		ITEM_FILES.put(ItemID.MITHRIL_AXE_2H, "mithril_axe.png");
		ITEM_FILES.put(ItemID.ADAMANT_AXE_2H, "adamantite_axe.png");
		ITEM_FILES.put(ItemID.RUNE_AXE_2H, "rune_axe.png");
		ITEM_FILES.put(ItemID.CRYSTAL_AXE_2H, "crystal_axe.png");
		ITEM_FILES.put(ItemID.DRAGON_AXE_2H, "dragon_axe.png");
		// No equivalent to reuse - Jin's call: use Steel as the stand-in.
		ITEM_FILES.put(ItemID._3A_AXE_2H, "steel_axe.png");

		// Mining pickaxes
		ITEM_FILES.put(ItemID.BRONZE_PICKAXE, "bronze_pickaxe.png");
		ITEM_FILES.put(ItemID.IRON_PICKAXE, "iron_pickaxe.png");
		ITEM_FILES.put(ItemID.STEEL_PICKAXE, "steel_pickaxe.png");
		ITEM_FILES.put(ItemID.MITHRIL_PICKAXE, "mithril_pickaxe.png");
		ITEM_FILES.put(ItemID.ADAMANT_PICKAXE, "adamantite_pickaxe.png");
		ITEM_FILES.put(ItemID.RUNE_PICKAXE, "rune_pickaxe.png");
		ITEM_FILES.put(ItemID.BLACK_PICKAXE, "black_pickaxe.png");
		ITEM_FILES.put(ItemID.CRYSTAL_PICKAXE, "crystal_pickaxe.png");
		ITEM_FILES.put(ItemID.DRAGON_PICKAXE, "dragon_pickaxe.png");
		// No sprite of their own - reusing dragon_pickaxe.png as the closest visual stand-in.
		ITEM_FILES.put(ItemID.INFERNAL_PICKAXE, "dragon_pickaxe.png");
		ITEM_FILES.put(ItemID.TRAILBLAZER_PICKAXE, "dragon_pickaxe.png");
		ITEM_FILES.put(ItemID.TRAILBLAZER_RELOADED_PICKAXE, "dragon_pickaxe.png");
		// No equivalent to reuse - Jin's call: use Steel as the stand-in.
		ITEM_FILES.put(ItemID._3A_PICKAXE, "steel_pickaxe.png");

		// Firemaking, Smithing, Crafting, Fletching, Herblore hand tools
		ITEM_FILES.put(ItemID.TINDERBOX, "tinderbox.png");
		ITEM_FILES.put(ItemID.HAMMER, "hammer.png");
		// No sprite of its own - reusing the plain hammer as the closest visual stand-in.
		ITEM_FILES.put(ItemID.IMCANDO_HAMMER, "hammer.png");
		ITEM_FILES.put(ItemID.GLASSBLOWINGPIPE, "glassblowing_pipe.png");
		ITEM_FILES.put(ItemID.NEEDLE, "needle.png");
		ITEM_FILES.put(ItemID.CHISEL, "chisel.png");
		ITEM_FILES.put(ItemID.KNIFE, "knife.png");
		ITEM_FILES.put(ItemID.PESTLE_AND_MORTAR, "pestle_and_mortar.png");

		// Fishing tools
		ITEM_FILES.put(ItemID.FISHING_ROD, "fishing_rod.png");
		ITEM_FILES.put(ItemID.FLY_FISHING_ROD, "fly_fishing_rod.png");
		ITEM_FILES.put(ItemID.OILY_FISHING_ROD, "oily_fishing_rod.png");
		ITEM_FILES.put(ItemID.HARPOON, "harpoon.png");
		ITEM_FILES.put(ItemID.DRAGON_HARPOON, "dragon_harpoon.png");
		ITEM_FILES.put(ItemID.CRYSTAL_HARPOON, "crystal_harpoon.png");
		// No sprite of its own - reusing dragon_harpoon.png as the closest visual stand-in.
		ITEM_FILES.put(ItemID.INFERNAL_HARPOON, "dragon_harpoon.png");
		ITEM_FILES.put(ItemID.NET, "net.png");
		ITEM_FILES.put(ItemID.BIG_NET, "big_net.png");
		ITEM_FILES.put(ItemID.LOBSTER_POT, "lobster_pot.png");

		// Cooking - raw/uncooked ingredients detected via CookingIngredients.
		ITEM_FILES.put(ItemID.RAW_SHRIMP, "raw_shrimp.png");
		ITEM_FILES.put(ItemID.RAW_ANCHOVIES, "raw_anchovies.png");
		ITEM_FILES.put(ItemID.RAW_SARDINE, "raw_sardine.png");
		ITEM_FILES.put(ItemID.RAW_SALMON, "raw_salmon.png");
		ITEM_FILES.put(ItemID.RAW_TROUT, "raw_trout.png");
		ITEM_FILES.put(ItemID.RAW_COD, "raw_cod.png");
		ITEM_FILES.put(ItemID.RAW_HERRING, "raw_herring.png");
		ITEM_FILES.put(ItemID.RAW_PIKE, "raw_pike.png");
		ITEM_FILES.put(ItemID.RAW_MACKEREL, "raw_mackerel.png");
		ITEM_FILES.put(ItemID.RAW_BASS, "raw_bass.png");
		ITEM_FILES.put(ItemID.RAW_TUNA, "raw_tuna.png");
		ITEM_FILES.put(ItemID.RAW_SWORDFISH, "raw_swordfish.png");
		ITEM_FILES.put(ItemID.RAW_LOBSTER, "raw_lobster.png");
		ITEM_FILES.put(ItemID.RAW_SHARK, "raw_shark.png");
		ITEM_FILES.put(ItemID.RAW_MANTARAY, "raw_manta_ray.png");
		ITEM_FILES.put(ItemID.RAW_SEATURTLE, "raw_sea_turtle.png");
		ITEM_FILES.put(ItemID.RAW_LAVA_EEL, "raw_lava_eel.png");
		ITEM_FILES.put(ItemID.RAW_ANGLERFISH, "raw_anglerfish.png");
		ITEM_FILES.put(ItemID.RAW_MONKFISH, "raw_monkfish.png");
		ITEM_FILES.put(ItemID.TBWT_RAW_KARAMBWAN, "raw_karambwan.png");
		ITEM_FILES.put(ItemID.RAW_CHICKEN, "raw_chicken.png");
		ITEM_FILES.put(ItemID.RAW_BEEF, "raw_beef.png");
		ITEM_FILES.put(ItemID.RAW_BEAR_MEAT, "raw_bear_meat.png");
		ITEM_FILES.put(ItemID.RAW_RAT_MEAT, "raw_rat_meat.png");
		ITEM_FILES.put(ItemID.BREAD_DOUGH, "bread_dough.png");
		ITEM_FILES.put(ItemID.UNCOOKED_CAKE, "uncooked_cake.png");
		ITEM_FILES.put(ItemID.UNCOOKED_PIZZA, "uncooked_pizza.png");
		ITEM_FILES.put(ItemID.UNCOOKED_APPLE_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_MEAT_PIE, "uncooked_meat_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_REDBERRY_PIE, "uncooked_redberry_pie.png");
		// No RSC sprite exists for these fillings - Jin's call: reuse uncooked_apple_pie.png for
		// all of them rather than leaving them on the modern icon.
		ITEM_FILES.put(ItemID.UNCOOKED_MUD_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_GARDEN_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_FISH_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_ADMIRAL_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_WILD_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_SUMMER_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_BOTANICAL_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_MUSHROOM_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_DRAGONFRUIT_PIE, "uncooked_apple_pie.png");
		ITEM_FILES.put(ItemID.UNCOOKED_STEW, "uncooked_stew.png");
		ITEM_FILES.put(ItemID.UNCOOKED_CURRY, "uncooked_curry.png");
		ITEM_FILES.put(ItemID.RAW_GIANT_CARP, "raw_giant_carp.png");
		ITEM_FILES.put(ItemID.RAW_UGTHANKI_MEAT, "raw_ugthanki_meat.png");
		ITEM_FILES.put(ItemID.UNCOOKED_PITTA_BREAD, "uncooked_pitta_bread.png");
		ITEM_FILES.put(ItemID.WRAPPED_OOMLIE, "raw_oomlie_meat_parcel.png");
		ITEM_FILES.put(ItemID.RAW_GNOMEBOWL, "gnomebowl_dough.png");
		ITEM_FILES.put(ItemID.RAW_CRUNCHIES, "gnomecrunchie_dough.png");
		ITEM_FILES.put(ItemID.RAW_BATTA, "gnomebatta_dough.png");

		// The rest of these post-RSC additions have no sprite of their own in the dump, so Jin
		// picked a close visual stand-in for each (2026-09-18) rather than leaving them unmapped.
		ITEM_FILES.put(ItemID.RAW_CHICKEN_UNDEAD, "enchanted_beef.png");
		ITEM_FILES.put(ItemID.RAW_BEEF_UNDEAD, "enchanted_beef.png");
		ITEM_FILES.put(ItemID.RAW_RABBIT, "raw_beef.png");
		ITEM_FILES.put(ItemID.RAW_BOAR_MEAT, "raw_beef.png");
		ITEM_FILES.put(ItemID.RAW_CHOMPY, "raw_beef.png");
		ITEM_FILES.put(ItemID.RAW_TBONE_STEAK, "raw_beef.png");
		ITEM_FILES.put(ItemID.RAW_RED_CRAB_MEAT, "raw_lobster.png");
		ITEM_FILES.put(ItemID.RAW_RAINBOW_CRAB_MEAT, "raw_lobster.png");
		ITEM_FILES.put(ItemID.RAW_GIANT_KRILL, "raw_shrimp.png");
		// These three do have their own sprite - Jin added them to the dump directly.
		ITEM_FILES.put(ItemID.RAW_BLUE_CRAB_MEAT, "raw_blue_crab.png");
		ITEM_FILES.put(ItemID.RAW_HADDOCK, "raw_haddock.png");
		ITEM_FILES.put(ItemID.RAW_YELLOWFIN, "raw_yellowfin.png");
		ITEM_FILES.put(ItemID.RAW_HALIBUT, "raw_halibut.png");
		ITEM_FILES.put(ItemID.RAW_MARLIN, "raw_marlin.png");
		ITEM_FILES.put(ItemID.RAW_SWORDTIP_SQUID, "raw_swordsquid.png");
		ITEM_FILES.put(ItemID.RAW_JUMBO_SQUID, "raw_jumbosquid.png");
		ITEM_FILES.put(ItemID.RAW_BLUEFIN, "raw_bluefin.png");
		ITEM_FILES.put(ItemID.RAW_CAVE_EEL, "raw_cave_eel.png");
		// No sprite of its own - burnt lobster is a close enough visual stand-in.
		ITEM_FILES.put(ItemID.RAW_DARK_CRAB, "burnt_lobster.png");

		// Smithing - ores detected via SmithingOre, bars via SmithingBar. RSC named plain gold
		// and silver ore just "Gold"/"Silver", not "Gold ore"/"Silver ore" - hence the different
		// file names for those two. Blurite bar has no classic sprite in the dump.
		ITEM_FILES.put(ItemID.COPPER_ORE, "copper_ore.png");
		ITEM_FILES.put(ItemID.IRON_ORE, "iron_ore.png");
		ITEM_FILES.put(ItemID.SILVER_ORE, "silver.png");
		ITEM_FILES.put(ItemID.GOLD_ORE, "gold.png");
		ITEM_FILES.put(ItemID.MITHRIL_ORE, "mithril_ore.png");
		ITEM_FILES.put(ItemID.ADAMANTITE_ORE, "adamantite_ore.png");
		ITEM_FILES.put(ItemID.RUNITE_ORE, "runite_ore.png");
		ITEM_FILES.put(ItemID.BLURITE_ORE, "blurite_ore.png");
		ITEM_FILES.put(ItemID.BLURITE_BAR, "blurite_bar.png");
		ITEM_FILES.put(ItemID.BRONZE_BAR, "bronze_bar.png");
		ITEM_FILES.put(ItemID.IRON_BAR, "iron_bar.png");
		ITEM_FILES.put(ItemID.STEEL_BAR, "steel_bar.png");
		ITEM_FILES.put(ItemID.SILVER_BAR, "silver_bar.png");
		ITEM_FILES.put(ItemID.GOLD_BAR, "gold_bar.png");
		ITEM_FILES.put(ItemID.MITHRIL_BAR, "mithril_bar.png");
		ITEM_FILES.put(ItemID.ADAMANTITE_BAR, "adamantite_bar.png");
		ITEM_FILES.put(ItemID.RUNITE_BAR, "runite_bar.png");
	}
}
