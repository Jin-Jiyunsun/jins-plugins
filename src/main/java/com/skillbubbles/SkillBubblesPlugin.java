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
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
	@Inject
	private Client client;

	@Inject
	private SkillBubblesConfig config;

	@Inject
	private SkillBubblesOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	private SkillAction currentAction;
	private int matchedAnimationId = -1;
	private int resolvedToolItemId = SkillAction.NO_TOOL;
	private int lastActionTick = -1;
	private int bubbleLogicalHeight;

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

	int getResolvedToolItemId()
	{
		return resolvedToolItemId;
	}

	int getBubbleLogicalHeight()
	{
		return bubbleLogicalHeight;
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
		}

		currentAction = action;
		matchedAnimationId = event.getActor().getAnimation();
		resolvedToolItemId = resolveToolItemId(action);
		lastActionTick = client.getTickCount();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (currentAction == null)
		{
			return;
		}

		// Still mid-animation - the idle countdown only starts once it actually stops matching,
		// not merely from when AnimationChanged last fired (a held animation may not re-fire it).
		Player player = client.getLocalPlayer();
		if (player != null && player.getAnimation() == matchedAnimationId)
		{
			lastActionTick = client.getTickCount();
			return;
		}

		if (client.getTickCount() - lastActionTick > config.idleTicks())
		{
			currentAction = null;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.LOADING)
		{
			currentAction = null;
			matchedAnimationId = -1;
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
