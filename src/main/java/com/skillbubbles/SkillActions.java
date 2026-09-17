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

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;

/**
 * Maps skilling animation IDs to the skill and tool they represent. Only covers the local
 * player's own animations (see {@link SkillBubblesPlugin}) - NPC and other-actor variants of
 * these animations are deliberately left out of {@link net.runelite.api.gameval.AnimationID}
 * lookups below.
 */
final class SkillActions
{
	private static final Map<Integer, SkillAction> ANIMATIONS = new HashMap<>();

	private SkillActions()
	{
	}

	static SkillAction get(int animationId)
	{
		return ANIMATIONS.get(animationId);
	}

	private static void add(int animationId, Skill skill, int toolItemId)
	{
		ANIMATIONS.put(animationId, new SkillAction(skill, toolItemId));
	}

	static
	{
		// Woodcutting - chopping trees
		add(AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE, Skill.WOODCUTTING, ItemID.BRONZE_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_IRON_AXE, Skill.WOODCUTTING, ItemID.IRON_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_STEEL_AXE, Skill.WOODCUTTING, ItemID.STEEL_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_BLACK_AXE, Skill.WOODCUTTING, ItemID.BLACK_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE, Skill.WOODCUTTING, ItemID.MITHRIL_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE, Skill.WOODCUTTING, ItemID.ADAMANT_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_RUNE_AXE, Skill.WOODCUTTING, ItemID.RUNE_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE, Skill.WOODCUTTING, ItemID.DRAGON_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE, Skill.WOODCUTTING, ItemID.INFERNAL_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE, Skill.WOODCUTTING, ItemID.CRYSTAL_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE, Skill.WOODCUTTING, ItemID.TRAILBLAZER_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE, Skill.WOODCUTTING, ItemID.TRAILBLAZER_RELOADED_AXE);
		add(AnimationID.HUMAN_WOODCUTTING_3A_AXE, Skill.WOODCUTTING, ItemID._3A_AXE);

		// Woodcutting - carving a canoe (same skill, same axes)
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_BRONZE, Skill.WOODCUTTING, ItemID.BRONZE_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_IRON, Skill.WOODCUTTING, ItemID.IRON_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_STEEL, Skill.WOODCUTTING, ItemID.STEEL_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_BLACK, Skill.WOODCUTTING, ItemID.BLACK_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_MITHRIL, Skill.WOODCUTTING, ItemID.MITHRIL_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_ADAMANT, Skill.WOODCUTTING, ItemID.ADAMANT_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_RUNE, Skill.WOODCUTTING, ItemID.RUNE_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_DRAGON, Skill.WOODCUTTING, ItemID.DRAGON_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_INFERNAL, Skill.WOODCUTTING, ItemID.INFERNAL_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_CRYSTAL, Skill.WOODCUTTING, ItemID.CRYSTAL_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_TRAILBLAZER, Skill.WOODCUTTING, ItemID.TRAILBLAZER_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_TRAILBLAZER_RELOADED, Skill.WOODCUTTING, ItemID.TRAILBLAZER_RELOADED_AXE);
		add(AnimationID.HUMAN_WOODCRAFTING_AXE_3A, Skill.WOODCUTTING, ItemID._3A_AXE);

		// Woodcutting - Forestry's felling axe (2h). All tiers share these same animations, but
		// unlike a plain axe a felling axe genuinely has to be wielded to use it, so the tier is
		// read from the equipped weapon slot instead - see resolveToolItemId() in the plugin.
		add(AnimationID.HUMAN_FELLING_CHARGE_SLOW01, Skill.WOODCUTTING, SkillAction.EQUIPPED_WEAPON);
		add(AnimationID.HUMAN_FELLING_CHARGE_SLOW02, Skill.WOODCUTTING, SkillAction.EQUIPPED_WEAPON);
		add(AnimationID.HUMAN_FELLING_CHARGE_FAST01, Skill.WOODCUTTING, SkillAction.EQUIPPED_WEAPON);
		add(AnimationID.HUMAN_FELLING_SWING01, Skill.WOODCUTTING, SkillAction.EQUIPPED_WEAPON);
		add(AnimationID.HUMAN_FELLING_BOTCH01, Skill.WOODCUTTING, SkillAction.EQUIPPED_WEAPON);
		add(AnimationID.HUMAN_FELLING_TAP01, Skill.WOODCUTTING, SkillAction.EQUIPPED_WEAPON);

		// Mining
		add(AnimationID.HUMAN_MINING_BRONZE_PICKAXE, Skill.MINING, ItemID.BRONZE_PICKAXE);
		add(AnimationID.HUMAN_MINING_IRON_PICKAXE, Skill.MINING, ItemID.IRON_PICKAXE);
		add(AnimationID.HUMAN_MINING_STEEL_PICKAXE, Skill.MINING, ItemID.STEEL_PICKAXE);
		add(AnimationID.HUMAN_MINING_BLACK_PICKAXE, Skill.MINING, ItemID.BLACK_PICKAXE);
		add(AnimationID.HUMAN_MINING_MITHRIL_PICKAXE, Skill.MINING, ItemID.MITHRIL_PICKAXE);
		add(AnimationID.HUMAN_MINING_ADAMANT_PICKAXE, Skill.MINING, ItemID.ADAMANT_PICKAXE);
		add(AnimationID.HUMAN_MINING_RUNE_PICKAXE, Skill.MINING, ItemID.RUNE_PICKAXE);
		add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE, Skill.MINING, ItemID.DRAGON_PICKAXE);
		add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY, Skill.MINING, ItemID.DRAGON_PICKAXE);
		add(AnimationID.HUMAN_MINING_INFERNAL_PICKAXE, Skill.MINING, ItemID.INFERNAL_PICKAXE);
		add(AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE, Skill.MINING, ItemID.CRYSTAL_PICKAXE);
		add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE, Skill.MINING, ItemID.TRAILBLAZER_PICKAXE);
		add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE, Skill.MINING, ItemID.TRAILBLAZER_RELOADED_PICKAXE);
		add(AnimationID.HUMAN_MINING_3A_PICKAXE, Skill.MINING, ItemID._3A_PICKAXE);

		// Fishing - net, harpoon and lobster pot each have their own dedicated animation, so
		// they're mapped directly to their one tool, the same as axes/pickaxes.
		add(AnimationID.HUMAN_SMALLNET, Skill.FISHING, ItemID.NET);
		add(AnimationID.HUMAN_LARGENET, Skill.FISHING, ItemID.BIG_NET);
		add(AnimationID.HUMAN_LOBSTER, Skill.FISHING, ItemID.LOBSTER_POT);
		add(AnimationID.HUMAN_HARPOON, Skill.FISHING, ItemID.HARPOON);
		add(AnimationID.HUMAN_HARPOON_DRAGON, Skill.FISHING, ItemID.DRAGON_HARPOON);
		add(AnimationID.HUMAN_HARPOON_INFERNAL, Skill.FISHING, ItemID.INFERNAL_HARPOON);
		add(AnimationID.HUMAN_HARPOON_CRYSTAL, Skill.FISHING, ItemID.CRYSTAL_HARPOON);

		// Fishing - rod casting/reeling shares one animation regardless of which rod (or its
		// tier) is actually equipped/held, and the rod isn't equipped gear (it just needs to be
		// in the inventory) so there's no clean signal to read it from - Jin's call: always show
		// the plain Fishing rod rather than trying to detect which rod is being used.
		add(AnimationID.HUMAN_FISHING_CASTING, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISH_ONSPOT, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISHING_CASTING_BRUT, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISHING_ONSPOT_BRUT, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISHING_CASTING_PEARL, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISHING_CASTING_PEARL_FLY, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISHING_CASTING_PEARL_BRUT, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISHING_CASTING_PEARL_OILY, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISH_ONSPOT_PEARL, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISH_ONSPOT_PEARL_FLY, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT, Skill.FISHING, ItemID.FISHING_ROD);
		add(AnimationID.HUMAN_FISH_ONSPOT_PEARL_OILY, Skill.FISHING, ItemID.FISHING_ROD);

		// Firemaking
		add(AnimationID.HUMAN_CREATEFIRE, Skill.FIREMAKING, ItemID.TINDERBOX);

		// Cooking - the same animation covers every range/fire and every food
		add(AnimationID.HUMAN_COOKING, Skill.COOKING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_COOKING_LOOP, Skill.COOKING, SkillAction.NO_TOOL);

		// Smithing - working a bar at the anvil (hammer, overridden with the bar being used
		// where SkillBubblesOverlay detects one - see SmithingBar)
		add(AnimationID.HUMAN_SMITHING, Skill.SMITHING, ItemID.HAMMER);
		add(AnimationID.HUMAN_SMITHING_IMCANDO_HAMMER, Skill.SMITHING, ItemID.IMCANDO_HAMMER);
		// Smithing - smelting ore into a bar at a furnace. No tool at all (no hammer involved),
		// filled in with the detected ore where known - see SmithingOre.
		add(AnimationID.HUMAN_FURNACE, Skill.SMITHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FURNACE_NOSTALL, Skill.SMITHING, SkillAction.NO_TOOL);

		// Runecraft - no hand tool, runs off talismans/tiaras at an altar
		add(AnimationID.HUMAN_RUNECRAFT, Skill.RUNECRAFT, SkillAction.NO_TOOL);

		// Crafting
		add(AnimationID.HUMAN_GLASSBLOWING, Skill.CRAFTING, ItemID.GLASSBLOWINGPIPE);
		add(AnimationID.HUMAN_LEATHER_CRAFTING, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_SHIELD_CRAFTING_LEATHER, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_SHIELD_CRAFTING_SNAKESKIN, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_SHIELD_CRAFTING_GREEN_DHIDE, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_SHIELD_CRAFTING_BLUE_DHIDE, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_SHIELD_CRAFTING_RED_DHIDE, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_SHIELD_CRAFTING_BLACK_DHIDE, Skill.CRAFTING, ItemID.NEEDLE);
		add(AnimationID.HUMAN_POTTERYWHEEL, Skill.CRAFTING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_SPINNINGWHEEL_90, Skill.CRAFTING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_SPINNINGWHEEL_60, Skill.CRAFTING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_BATTLESTAFF_CRAFTING, Skill.CRAFTING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_CRAFTING, Skill.CRAFTING, SkillAction.NO_TOOL);
		// Gem cutting - every gem has its own animation, but the tool is always a chisel
		add(AnimationID.HUMAN_OPALCUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_JADECUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_REDTOPAZCUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_SAPPHIRECUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_EMERALDCUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_RUBYCUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_DIAMONDCUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_DRAGONSTONECUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_ONYXCUTTING, Skill.CRAFTING, ItemID.CHISEL);
		add(AnimationID.HUMAN_ZENYTECUTTING, Skill.CRAFTING, ItemID.CHISEL);

		// Fletching
		add(AnimationID.HUMAN_FLETCHING, Skill.FLETCHING, ItemID.KNIFE);
		add(AnimationID.HUMAN_FLETCHING_SINGLE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_FEATHER, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_ARROW_TIPS, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_BRONZE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_IRON, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_BLURITE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_STEEL, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_MITHRIL, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_ADAMANT, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_RUNE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_DRAGON, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_BRONZE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_IRON, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_SILVER, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_BLURITE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_STEEL, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_MITHRIL, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_ADAMANT, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_RUNE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_BOLT_FEATHERS_DRAGON, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_BRONZE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_IRON, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_STEEL, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_MITHRIL, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_ADAMANT, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_RUNE, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_DRAGON, Skill.FLETCHING, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_AMETHYST, Skill.FLETCHING, SkillAction.NO_TOOL);

		// Herblore
		add(AnimationID.HUMAN_HERBING_GRIND, Skill.HERBLORE, ItemID.PESTLE_AND_MORTAR);
		add(AnimationID.HUMAN_HERBING_VIAL, Skill.HERBLORE, SkillAction.NO_TOOL);

		// Hunter - no tool shown for any trap type, butterfly net included (Jin's call,
		// 2026-09-18 - dropped the one tool-specific case since it had no RSC sprite to fall
		// back on cleanly).
		add(AnimationID.HUMAN_LAYTRAP, Skill.HUNTER, SkillAction.NO_TOOL);
		add(AnimationID.HUMAN_BUTTERFLYNET_SWING, Skill.HUNTER, SkillAction.NO_TOOL);

		// Farming - one generic animation covers raking, planting, composting and harvesting
		add(AnimationID.HUMAN_FARMING, Skill.FARMING, SkillAction.NO_TOOL);

		// Construction
		add(AnimationID.HUMAN_POH_BUILD, Skill.CONSTRUCTION, SkillAction.NO_TOOL);

		// Thieving - pickpocketing and stalls have no hand-tool
		add(AnimationID.HUMAN_PICKPOCKET, Skill.THIEVING, SkillAction.NO_TOOL);
	}
}
