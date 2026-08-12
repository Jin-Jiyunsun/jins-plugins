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
package com.keybindtilehighlight;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class KeybindTileHighlightOverlay extends Overlay
{
	private static final Stroke BORDER_STROKE = new BasicStroke(2f);
	private static final int FILL_ALPHA = 50;

	private final Client client;
	private final KeybindTileHighlightConfig config;
	private final KeybindTileHighlightPlugin plugin;

	@Inject
	private KeybindTileHighlightOverlay(Client client, KeybindTileHighlightConfig config, KeybindTileHighlightPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// getSelectedSceneTile() goes stale rather than returning null while a right-click
		// menu is open, which would leave the highlight stuck on the tile the menu opened on
		if (!plugin.isHotkeyHeld() || client.isMenuOpen())
		{
			return null;
		}

		if (!isMouseInViewport())
		{
			return null;
		}

		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return null;
		}

		// Re-read every frame so the highlight follows the cursor with no tracking of its own
		Tile tile = wv.getSelectedSceneTile();
		if (tile == null)
		{
			return null;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
		if (poly == null)
		{
			return null;
		}

		Color color = config.highlightColor();
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), FILL_ALPHA);
		OverlayUtil.renderPolygon(graphics, poly, color, fill, BORDER_STROKE);

		return null;
	}

	/**
	 * In resizable mode the viewport spans the whole canvas, so interfaces drawn over the
	 * scene still count as inside it and the tile beneath them stays highlighted. In fixed
	 * mode the surrounding panels sit outside the viewport, so the highlight drops away as
	 * the cursor leaves the scene.
	 */
	private boolean isMouseInViewport()
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return false;
		}

		int x = mouse.getX() - client.getViewportXOffset();
		int y = mouse.getY() - client.getViewportYOffset();

		return x >= 0 && x < client.getViewportWidth()
			&& y >= 0 && y < client.getViewportHeight();
	}
}
