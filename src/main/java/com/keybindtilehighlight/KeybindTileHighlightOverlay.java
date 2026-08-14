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
import java.awt.LinearGradientPaint;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.Point2D;
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
	private static final long RAINBOW_CYCLE_MS = 3000;
	private static final int RAINBOW_STOPS = 36;
	private static final float RAINBOW_HUE_SPAN = 0.75f;

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
		if (client.isMenuOpen())
		{
			return null;
		}

		boolean held1 = plugin.isKeybind1Held();
		boolean held2 = plugin.isKeybind2Held();

		if (!held1 && !held2)
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

		if (held1 && held2)
		{
			if (plugin.getMostRecentKeybind() == 1)
			{
				renderHighlight(graphics, poly, config.highlightColor(), config.fillOpacity());
			}
			else
			{
				renderHighlight(graphics, poly, config.highlightColor2(), config.fillOpacity2());
			}
		}
		else if (held1)
		{
			renderHighlight(graphics, poly, config.highlightColor(), config.fillOpacity());
		}
		else
		{
			renderHighlight(graphics, poly, config.highlightColor2(), config.fillOpacity2());
		}

		return null;
	}

	private void renderHighlight(Graphics2D graphics, Polygon poly, Color configuredColor, int fillOpacityPercent)
	{
		switch (config.rainbowMode())
		{
			case GRADIENT:
				renderGradientHighlight(graphics, poly, fillOpacityPercent);
				break;
			case SOLID:
				renderSolidHighlight(graphics, poly, rainbowColor(), fillOpacityPercent);
				break;
			case OFF:
			default:
				renderSolidHighlight(graphics, poly, configuredColor, fillOpacityPercent);
				break;
		}
	}

	private void renderSolidHighlight(Graphics2D graphics, Polygon poly, Color color, int fillOpacityPercent)
	{
		int fillAlpha = (int) Math.round(fillOpacityPercent / 100.0 * 255);
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), fillAlpha);
		OverlayUtil.renderPolygon(graphics, poly, color, fill, BORDER_STROKE);
	}

	private void renderGradientHighlight(Graphics2D graphics, Polygon poly, int fillOpacityPercent)
	{
		Point2D start = new Point2D.Float(poly.xpoints[0], poly.ypoints[0]);
		Point2D end = new Point2D.Float(poly.xpoints[2], poly.ypoints[2]);

		if (start.equals(end))
		{
			renderSolidHighlight(graphics, poly, rainbowColor(), fillOpacityPercent);
			return;
		}

		float offset = (System.currentTimeMillis() % RAINBOW_CYCLE_MS) / (float) RAINBOW_CYCLE_MS;
		int fillAlpha = (int) Math.round(fillOpacityPercent / 100.0 * 255);

		float[] fractions = new float[RAINBOW_STOPS];
		Color[] fillColors = new Color[RAINBOW_STOPS];
		Color[] borderColors = new Color[RAINBOW_STOPS];

		for (int i = 0; i < RAINBOW_STOPS; i++)
		{
			fractions[i] = i / (float) (RAINBOW_STOPS - 1);
			float hue = (fractions[i] * RAINBOW_HUE_SPAN + offset) % 1f;
			Color base = Color.getHSBColor(hue, 1f, 1f);
			fillColors[i] = new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha);
			borderColors[i] = base;
		}

		graphics.setPaint(new LinearGradientPaint(start, end, fractions, fillColors));
		graphics.fill(poly);

		graphics.setStroke(BORDER_STROKE);
		graphics.setPaint(new LinearGradientPaint(start, end, fractions, borderColors));
		graphics.draw(poly);
	}

	private static Color rainbowColor()
	{
		float hue = (System.currentTimeMillis() % RAINBOW_CYCLE_MS) / (float) RAINBOW_CYCLE_MS;
		return Color.getHSBColor(hue, 1f, 1f);
	}

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