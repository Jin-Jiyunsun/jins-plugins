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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class SkillBubblesOverlay extends Overlay
{
	private static final int BUBBLE_SIZE = 32;
	private static final int HEIGHT_MARGIN = 28;
	private static final Color BUBBLE_FILL = new Color(0, 0, 0, 140);
	private static final Color BUBBLE_BORDER = new Color(255, 255, 255, 190);

	// Only used to give getCanvasImageLocation() the bubble's footprint for positioning -
	// never drawn itself, so it's fine to share one instance across frames.
	private final BufferedImage sizingImage = new BufferedImage(BUBBLE_SIZE, BUBBLE_SIZE, BufferedImage.TYPE_INT_ARGB);

	private final Client client;
	private final SkillBubblesPlugin plugin;
	private final SkillBubblesConfig config;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final Map<Skill, BufferedImage> skillIconCache = new EnumMap<>(Skill.class);

	@Inject
	private SkillBubblesOverlay(Client client, SkillBubblesPlugin plugin, SkillBubblesConfig config,
		ItemManager itemManager, SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		SkillAction action = plugin.getCurrentAction();
		if (action == null)
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		BufferedImage icon = resolveIcon(action);
		if (icon == null)
		{
			return null;
		}

		// getCanvasImageLocation centers sizingImage on the target point, i.e. it draws the
		// bubble half above and half below that point. Shift it up by half the bubble's height
		// so the bubble's bottom edge sits at the point instead - otherwise, when zoomed out,
		// the screen-space gap to the character shrinks while the bubble's pixel size doesn't,
		// and the bottom half ends up overlapping the character model.
		Point loc = player.getCanvasImageLocation(sizingImage, plugin.getBubbleLogicalHeight() + HEIGHT_MARGIN);
		if (loc == null)
		{
			return null;
		}

		int bubbleX = loc.getX();
		int bubbleY = loc.getY() - BUBBLE_SIZE / 2;

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(BUBBLE_FILL);
		graphics.fillOval(bubbleX, bubbleY, BUBBLE_SIZE, BUBBLE_SIZE);
		graphics.setColor(BUBBLE_BORDER);
		graphics.drawOval(bubbleX, bubbleY, BUBBLE_SIZE, BUBBLE_SIZE);

		// Always drawn at native pixel size - never stretched, shrunk or smoothed, for either
		// skill sprites or item icons. Item icons can be a bit larger than the bubble (skilling
		// tools render around 36x32); that's a minor overflow past the bubble outline, which
		// looks better than shrinking every icon to force-fit.
		int cx = bubbleX + BUBBLE_SIZE / 2;
		int cy = bubbleY + BUBBLE_SIZE / 2;
		graphics.drawImage(icon, cx - icon.getWidth() / 2, cy - icon.getHeight() / 2, null);

		return null;
	}

	private BufferedImage resolveIcon(SkillAction action)
	{
		if (config.iconMode() == SkillBubblesConfig.IconMode.TOOL)
		{
			int toolItemId = plugin.getResolvedToolItemId();
			if (toolItemId != SkillAction.NO_TOOL)
			{
				return itemManager.getImage(toolItemId);
			}
		}

		return skillIcon(action.skill);
	}

	private BufferedImage skillIcon(Skill skill)
	{
		BufferedImage cached = skillIconCache.get(skill);
		if (cached != null)
		{
			return cached;
		}

		Integer spriteId = SkillIcons.spriteId(skill);
		if (spriteId == null)
		{
			return null;
		}

		spriteManager.getSpriteAsync(spriteId, 0, sprite -> skillIconCache.put(skill, sprite));
		return skillIconCache.get(skill);
	}
}
