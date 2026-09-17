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

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Skill Bubbles",
	description = "Shows a RuneScape Classic-style bubble above your head with the skill or tool you're currently using",
	tags = {"skilling", "overlay", "classic", "bubble", "icon"}
)
public class SkillBubblesPlugin extends Plugin
{
	// How long the fade-in/fade-out animation takes, shared with SkillBubblesOverlay - lives here
	// since the ingredient-forgetting logic below needs to know when a fade-out has actually
	// finished, not just when the underlying action stopped.
	static final long FADE_DURATION_MILLIS = 250;

	@Inject
	private Client client;

	@Inject
	private SkillBubblesConfig config;

	@Inject
	private SkillBubblesOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	private SkillAction currentAction;
	// Never cleared when currentAction goes null - holds whatever was last shown, so the overlay
	// still has something to render while fading out after the action itself has stopped.
	private SkillAction lastAction;
	// Wall-clock time of the most recent transition between "an action is active" and "not" -
	// used to compute the fade-in/fade-out progress smoothly across frames (tick granularity,
	// ~600ms, would look stepped for a 1-second fade).
	private long actionStateChangedMillis;
	private int matchedAnimationId = -1;
	private int resolvedToolItemId = SkillAction.NO_TOOL;
	private int lastActionTick = -1;
	private int bubbleLogicalHeight;
	// Cooking, and Smithing's furnace/anvil actions, share the same animation regardless of the
	// specific food/ore/bar involved, so which one is being used is inferred separately by
	// watching for a one-item drop in a known set of items' inventory counts - ground truth from
	// the actual inventory, not text-scraping chat messages. See CookingFish, SmithingOre and
	// SmithingBar for what's tracked (and deliberately not).
	private final Map<Integer, Integer> lastFishCounts = new HashMap<>();
	private int currentCookingFishId = SkillAction.NO_TOOL;
	private final Map<Integer, Integer> lastOreCounts = new HashMap<>();
	private int currentSmithingOreId = SkillAction.NO_TOOL;
	private final Map<Integer, Integer> lastBarCounts = new HashMap<>();
	private int currentSmithingBarId = SkillAction.NO_TOOL;

	@Override
	protected void startUp()
	{
		log.debug("Skill Bubbles started");
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Skill Bubbles stopped");
		overlayManager.remove(overlay);
		currentAction = null;
	}

	SkillAction getCurrentAction()
	{
		return currentAction;
	}

	SkillAction getLastAction()
	{
		return lastAction;
	}

	boolean isActionActive()
	{
		return currentAction != null;
	}

	long getActionStateChangedMillis()
	{
		return actionStateChangedMillis;
	}

	int getResolvedToolItemId()
	{
		return resolvedToolItemId;
	}

	int getBubbleLogicalHeight()
	{
		return bubbleLogicalHeight;
	}

	int getCurrentCookingFishId()
	{
		return currentCookingFishId;
	}

	int getCurrentSmithingOreId()
	{
		return currentSmithingOreId;
	}

	int getCurrentSmithingBarId()
	{
		return currentSmithingBarId;
	}

	int getMatchedAnimationId()
	{
		return matchedAnimationId;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		SkillAction action = SkillActions.get(event.getActor().getAnimation());
		if (action == null)
		{
			return;
		}

		if (currentAction == null)
		{
			// Lock the model height in for this bubble's whole lifetime, sampled only when it
			// first appears - the same action's later animation frames can report a different
			// logical height (e.g. a crouch mid-swing), which would otherwise make the bubble
			// hop around every time it's re-read.
			bubbleLogicalHeight = event.getActor().getLogicalHeight();
			actionStateChangedMillis = System.currentTimeMillis();
		}

		currentAction = action;
		lastAction = action;
		matchedAnimationId = event.getActor().getAnimation();
		resolvedToolItemId = resolveToolItemId(action);
		lastActionTick = client.getTickCount();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (currentAction != null)
		{
			// Still mid-animation - the idle countdown only starts once it actually stops
			// matching, not merely from when AnimationChanged last fired (a held animation may
			// not re-fire it).
			Player player = client.getLocalPlayer();
			if (player != null && player.getAnimation() == matchedAnimationId)
			{
				lastActionTick = client.getTickCount();
				return;
			}

			if (client.getTickCount() - lastActionTick >= idleTicksThreshold())
			{
				currentAction = null;
				actionStateChangedMillis = System.currentTimeMillis();
			}

			return;
		}

		// Forget which ore/bar/fish was last detected, so a new bubble for a different item
		// doesn't briefly show the previous one before the next consumption is detected - it
		// falls back to the generic skill/tool icon in the meantime instead. The underlying count
		// baselines are left alone - only what's currently displayed resets, not the tracking
		// that makes the very next consumption detectable immediately. Held off until the
		// fade-out (if enabled) has actually finished, so the fading bubble keeps showing the
		// resource that was in use instead of dropping to the generic icon partway through.
		long fadeMillis = config.fadeAnimation() ? FADE_DURATION_MILLIS : 0;
		if (System.currentTimeMillis() - actionStateChangedMillis >= fadeMillis)
		{
			forgetDetectedIngredients();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		ItemContainer inventory = event.getItemContainer();
		Integer fish = detectConsumed(inventory, CookingFish.RAW_FISH_IDS, lastFishCounts);
		if (fish != null)
		{
			currentCookingFishId = fish;
		}

		Integer ore = detectConsumed(inventory, SmithingOre.ORE_IDS, lastOreCounts);
		if (ore != null)
		{
			currentSmithingOreId = ore;
		}

		Integer bar = detectConsumed(inventory, SmithingBar.BAR_IDS, lastBarCounts);
		if (bar != null)
		{
			currentSmithingBarId = bar;
		}
	}

	private int idleTicksThreshold()
	{
		// Rounded to the nearest tick, not truncated - the countdown only advances once per
		// GAME_TICK_LENGTH (600ms), so this is as close to the configured seconds as it can get.
		return Math.round(config.idleSeconds() * 1000f / Constants.GAME_TICK_LENGTH);
	}

	private void forgetDetectedIngredients()
	{
		currentCookingFishId = SkillAction.NO_TOOL;
		currentSmithingOreId = SkillAction.NO_TOOL;
		currentSmithingBarId = SkillAction.NO_TOOL;
	}

	/**
	 * Checks a set of item IDs against the inventory's current counts, updates the running
	 * counts, and returns whichever one just dropped - or null if none did. If more than one
	 * item in the set drops on the same event, the last one checked (Set iteration order, not
	 * meaningfully controllable) wins - fine for the sets this is used with, since each is
	 * designed so at most one relevant item drops per real in-game action.
	 */
	private static Integer detectConsumed(ItemContainer inventory, Iterable<Integer> itemIds, Map<Integer, Integer> lastCounts)
	{
		Integer consumed = null;
		for (int itemId : itemIds)
		{
			int count = inventory.count(itemId);
			Integer previous = lastCounts.put(itemId, count);
			if (previous != null && count < previous)
			{
				consumed = itemId;
			}
		}

		return consumed;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.LOADING)
		{
			currentAction = null;
			matchedAnimationId = -1;
			forgetDetectedIngredients();
			lastFishCounts.clear();
			lastOreCounts.clear();
			lastBarCounts.clear();
		}
		else if (state == GameState.LOGGED_IN)
		{
			// Seed the baselines from the actual inventory now, rather than only learning them
			// reactively from the first ItemContainerChanged we happen to see - otherwise the
			// very first consumption of a given item is unobservable (nothing to compare against
			// yet), and only the second one onward gets detected.
			seedInventoryBaselines();
		}
	}

	private void seedInventoryBaselines()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return;
		}

		seedCounts(inventory, CookingFish.RAW_FISH_IDS, lastFishCounts);
		seedCounts(inventory, SmithingOre.ORE_IDS, lastOreCounts);
		seedCounts(inventory, SmithingBar.BAR_IDS, lastBarCounts);
	}

	private static void seedCounts(ItemContainer inventory, Iterable<Integer> itemIds, Map<Integer, Integer> counts)
	{
		for (int itemId : itemIds)
		{
			counts.put(itemId, inventory.count(itemId));
		}
	}

	private int resolveToolItemId(SkillAction action)
	{
		if (action.toolItemId != SkillAction.EQUIPPED_WEAPON)
		{
			return action.toolItemId;
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return SkillAction.NO_TOOL;
		}

		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? SkillAction.NO_TOOL : weapon.getId();
	}

	@Provides
	SkillBubblesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillBubblesConfig.class);
	}
}
