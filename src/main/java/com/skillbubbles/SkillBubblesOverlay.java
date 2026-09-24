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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

class SkillBubblesOverlay extends Overlay
{
	private static final int HEIGHT_MARGIN = 28;
	private static final int BUBBLE_SIZE = 35;
	// Matches the prayer tab's golden glow colouring - bright warm gold at the centre fading to
	// a darker amber at the rim - but drawn as our own gradient rather than the actual sprite,
	// no outline. A gradient is computed at render resolution, so it doesn't have the scaling
	// artifacts a bitmap would.
	private static final Color GLOW_CENTER = new Color(0x83, 0x86, 0x6D);
	private static final float GLOW_CENTER_FRACTION = 0.15f;
	private static final Color GLOW_EDGE = new Color(0xE5, 0xC6, 0x7E);
	private static final float GLOW_OUTER_EDGE_FRACTION = 0.95f;
	private static final Color GLOW_OUTER_EDGE = new Color(0xFF, 0xE8, 0xAA);
	// RSC's overhead icon backdrop - flat gray, semi-transparent (unlike the fully opaque
	// gradient bubble). Tuned live in-game via a temporary debug config item, then hard-coded.
	// Tints classicBubbleMask() below - that image is plain white-on-transparent, not this
	// colour, since it's just a shape mask.
	private static final Color CLASSIC_BUBBLE_COLOR = new Color(0x9E, 0x9E, 0x9E, 0x6E);
	// The half-width (in destination pixels) of the zone around a source pixel boundary that
	// hybridResize() smooths - 0.5 is the textbook Hybrid algorithm's own value; tried smaller
	// values for a sharper look, but 0.5 won a side-by-side comparison in-game.
	private static final float BLEND_RADIUS = 0.5f;

	// Only used to give getCanvasImageLocation() the bubble's footprint for positioning -
	// never drawn itself, so it's fine to share one instance across frames. Rebuilt only when
	// the configured scale changes, not every frame.
	private BufferedImage sizingImage;
	private int sizingImageScale = -1;

	private final Client client;
	private final SkillBubblesPlugin plugin;
	private final SkillBubblesConfig config;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final Map<Skill, BufferedImage> skillIconCache = new EnumMap<>(Skill.class);
	// Item icons sit on a fixed-size canvas where the actual artwork isn't necessarily centered
	// in it (e.g. an axe held diagonally has more padding on one side than the other), so
	// centering on the full image bounds leaves it looking off-centre in the bubble. This caches,
	// per item, the centre of its actual opaque pixels instead. Skill icons don't need this -
	// they're already tidy squares that fill their canvas evenly. Keyed by item id + scale
	// percent packed together, since a resized image's opaque bounds aren't just a linear scale
	// of the original's (resampling can shift them slightly).
	private final Map<Long, int[]> toolIconCenterCache = new HashMap<>();
	// Icons scaled away from 100% are resampled once per (icon, scale percent) and cached here,
	// rather than every frame - keyed the same way as toolIconCenterCache, using a negative
	// surrogate id for skill icons (item ids are always non-negative) so both share one key
	// space without colliding.
	private final Map<Long, BufferedImage> resizedIconCache = new HashMap<>();
	// The classic bubble backdrop mask, loaded once on first use and reused.
	private BufferedImage classicBubbleMask;
	// Recoloured + resized per bubble height, so the (cheap, but not free) recolour pass doesn't
	// run every frame.
	private final Map<Integer, BufferedImage> classicBubbleCache = new HashMap<>();

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
		SkillAction action = plugin.getLastAction();
		if (action == null)
		{
			return null;
		}

		// Without the fade toggle, this behaves exactly as before - disappears the instant the
		// action stops. With it, the bubble keeps rendering (using the last known action/icon,
		// since currentAction has already gone null by then) for up to a second while its alpha
		// ramps down, computed from wall-clock time so it's smooth across frames rather than
		// stepped at tick granularity.
		float fadeAlpha = 1f;
		if (config.fadeAnimation())
		{
			long elapsed = System.currentTimeMillis() - plugin.getActionStateChangedMillis();
			float t = Math.min(1f, elapsed / (float) SkillBubblesPlugin.FADE_DURATION_MILLIS);
			fadeAlpha = plugin.isActionActive() ? t : 1f - t;
			if (fadeAlpha <= 0f)
			{
				return null;
			}
		}
		else if (!plugin.isActionActive())
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		int toolItemId = plugin.getResolvedToolItemId();
		if (action.skill == Skill.COOKING)
		{
			// No single tool (the animation is the same regardless of food) - the plugin
			// separately tracks what's being cooked by watching inventory counts, standing in
			// for a tool item here when known.
			if (toolItemId == SkillAction.NO_TOOL)
			{
				toolItemId = plugin.getCurrentCookingItemId();
			}
		}
		else if (action.skill == Skill.SMITHING)
		{
			int matchedAnimationId = plugin.getMatchedAnimationId();
			if (matchedAnimationId == AnimationID.HUMAN_FURNACE || matchedAnimationId == AnimationID.HUMAN_FURNACE_NOSTALL)
			{
				// Smelting - no tool at all, filled in with the detected ore where known.
				toolItemId = plugin.getCurrentSmithingOreId();
			}
			else
			{
				// Working at the anvil - the hammer is the table's default, but the bar being
				// worked is more informative, so it replaces the hammer where known rather than
				// only filling a gap.
				int bar = plugin.getCurrentSmithingBarId();
				if (bar != SkillAction.NO_TOOL)
				{
					toolItemId = bar;
				}
			}
		}

		boolean toolMode = config.iconMode() == SkillBubblesConfig.IconMode.TOOL && toolItemId != SkillAction.NO_TOOL;
		boolean classicSprites = config.classicSprites();

		// Distinct negative surrogate ranges (well clear of real item ids, and of the plain
		// skill-ordinal surrogates below) so a classic sprite never shares a resize/centering
		// cache entry with its modern counterpart - the two are different images at the same
		// item id/skill.
		BufferedImage icon;
		long identity;
		// True for any icon drawn from item-style art sitting on a padded canvas (an OSRS item
		// icon, or any RSC sprite - both classic skill stand-ins and classic tool icons use the
		// same kind of art as the dump's item sprites) - these need the opaque-bounds centering
		// below. False only for the plain OSRS skill-tab icon, which is already a tidy, evenly
		// filled square.
		boolean itemStyleIcon;
		if (toolMode)
		{
			BufferedImage classicIcon = classicSprites ? RscSprites.forItem(toolItemId) : null;
			if (classicIcon != null)
			{
				icon = classicIcon;
				identity = -(2_000_000_000L + toolItemId);
			}
			else
			{
				icon = itemManager.getImage(toolItemId);
				identity = toolItemId;
			}
			itemStyleIcon = true;
		}
		else
		{
			BufferedImage classicIcon = classicSprites ? RscSprites.forSkill(action.skill) : null;
			if (classicIcon != null)
			{
				icon = classicIcon;
				identity = -(1000L + action.skill.ordinal());
				itemStyleIcon = true;
			}
			else
			{
				icon = skillIcon(action.skill);
				identity = -(action.skill.ordinal() + 1);
				itemStyleIcon = false;
			}
		}

		if (icon == null)
		{
			return null;
		}

		int scalePercent = config.scale();
		int bubbleSize = Math.round(BUBBLE_SIZE * scalePercent / 100f);

		// getCanvasImageLocation centers sizingImage on the target point, i.e. it draws the
		// bubble half above and half below that point. Shift it up by half the bubble's height
		// so the bubble's bottom edge sits at the point instead - otherwise, when zoomed out,
		// the screen-space gap to the character shrinks while the bubble's pixel size doesn't,
		// and the bottom half ends up overlapping the character model.
		Point loc = player.getCanvasImageLocation(sizingImage(bubbleSize), plugin.getBubbleLogicalHeight() + HEIGHT_MARGIN);
		if (loc == null)
		{
			return null;
		}

		int bubbleX = loc.getX();
		int bubbleY = loc.getY() - bubbleSize / 2;

		Composite originalComposite = graphics.getComposite();
		if (fadeAlpha < 1f)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
		}

		if (classicSprites)
		{
			// RSC's own overhead icon backdrop, from a real sprite (a plain white-on-transparent
			// shape mask Jin supplied) rather than hand-drawn geometry - recoloured to
			// CLASSIC_BUBBLE_COLOR and scaled with nearest-neighbour, so it stays crisp and
			// blocky instead of picking up soft antialiased edges.
			BufferedImage classicBubble = classicBubbleImage(bubbleSize);
			int bubbleDrawX = bubbleX - (classicBubble.getWidth() - bubbleSize) / 2;
			graphics.drawImage(classicBubble, bubbleDrawX, bubbleY, null);
		}
		else
		{
			// The gradient is computed at render resolution, so scaling it never loses quality.
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Point2D.Float center = new Point2D.Float(bubbleX + bubbleSize / 2f, bubbleY + bubbleSize / 2f);
			graphics.setPaint(new RadialGradientPaint(center, bubbleSize / 2f,
				new float[] {0f, GLOW_CENTER_FRACTION, GLOW_OUTER_EDGE_FRACTION, 1f},
				new Color[] {GLOW_CENTER, GLOW_CENTER, GLOW_EDGE, GLOW_OUTER_EDGE}));
			graphics.fillOval(bubbleX, bubbleY, bubbleSize, bubbleSize);
		}

		int cx = bubbleX + bubbleSize / 2;
		int cy = bubbleY + bubbleSize / 2;

		long cacheKey = (identity << 32) | (scalePercent & 0xFFFFFFFFL);

		BufferedImage drawIcon = icon;
		if (scalePercent != 100)
		{
			int drawWidth = Math.round(icon.getWidth() * scalePercent / 100f);
			int drawHeight = Math.round(icon.getHeight() * scalePercent / 100f);
			drawIcon = resizedIconCache.computeIfAbsent(cacheKey, k -> hybridResize(icon, drawWidth, drawHeight));
		}

		int offsetX = drawIcon.getWidth() / 2;
		int offsetY = drawIcon.getHeight() / 2;
		if (itemStyleIcon)
		{
			BufferedImage finalDrawIcon = drawIcon;
			int[] opaqueCenter = toolIconCenterCache.computeIfAbsent(cacheKey, k -> computeOpaqueCenter(finalDrawIcon));
			offsetX = opaqueCenter[0];
			offsetY = opaqueCenter[1];
		}

		// Icons are never stretched/shrunk with Java2D's own bilinear smoothing - at 100% it's
		// a plain unscaled blit; away from that, drawIcon is already our own hybrid resize,
		// drawn here at its own (already-correct) size with no further resampling.
		graphics.drawImage(drawIcon, cx - offsetX, cy - offsetY, null);

		graphics.setComposite(originalComposite);
		return null;
	}

	/**
	 * Resamples an image the way the GPU/117 HD plugins' "Hybrid" UI scaling mode does
	 * ({@code net.runelite.client.plugins.gpu.scale.hybrid.glsl}, credited there to
	 * <a href="https://colececil.dev/blog/2017/scaling-pixel-art-without-destroying-it/">
	 * Cole Cecil's "Scaling pixel art without destroying it"</a>): deep inside a source pixel
	 * the sample position snaps exactly to that pixel's centre (perfectly sharp, no blending),
	 * and only within half a destination pixel of a source pixel boundary does it smoothly
	 * blend toward the neighbour - sharper in flat regions than plain bilinear/bicubic (which
	 * soften everywhere), while still anti-aliasing edges rather than nearest-neighbour's
	 * blockiness. Confirmed against Catmull-Rom and Java2D's built-in algorithms side by side
	 * in-game (2026-09-17); Jin picked this one. Also tried tuning the blend-zone width smaller
	 * than the textbook 0.5 for a sharper look, but 0.5 (the default) won that comparison too.
	 */
	private static BufferedImage hybridResize(BufferedImage src, int destWidth, int destHeight)
	{
		int srcWidth = src.getWidth();
		int srcHeight = src.getHeight();
		int[] srcPixels = src.getRGB(0, 0, srcWidth, srcHeight, null, 0, srcWidth);

		float scaleX = (float) destWidth / srcWidth;
		float scaleY = (float) destHeight / srcHeight;
		int[] destPixels = new int[destWidth * destHeight];

		for (int dy = 0; dy < destHeight; dy++)
		{
			float sampleY = hybridSamplePosition(dy, scaleY);
			for (int dx = 0; dx < destWidth; dx++)
			{
				float sampleX = hybridSamplePosition(dx, scaleX);
				destPixels[dy * destWidth + dx] = bilinearSample(srcPixels, srcWidth, srcHeight, sampleX, sampleY);
			}
		}

		BufferedImage dest = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_ARGB);
		dest.setRGB(0, 0, destWidth, destHeight, destPixels, 0, destWidth);
		return dest;
	}

	private static float hybridSamplePosition(int destIndex, float pixelsPerTexel)
	{
		float srcPos = (destIndex + 0.5f) / pixelsPerTexel;
		float texelIndex = (float) Math.floor(srcPos);
		float texelFrac = srcPos - texelIndex;
		float interpolation = Math.min(texelFrac * pixelsPerTexel, BLEND_RADIUS)
			- Math.min((1f - texelFrac) * pixelsPerTexel, BLEND_RADIUS);
		return texelIndex + 0.5f + interpolation;
	}

	// Alpha is premultiplied before blending and un-premultiplied after, so transparent
	// neighbours don't bleed colour into opaque edges.
	private static int bilinearSample(int[] pixels, int width, int height, float x, float y)
	{
		int x0 = clamp((int) Math.floor(x - 0.5f), 0, width - 1);
		int y0 = clamp((int) Math.floor(y - 0.5f), 0, height - 1);
		int x1 = clamp(x0 + 1, 0, width - 1);
		int y1 = clamp(y0 + 1, 0, height - 1);
		float tx = clamp01(x - 0.5f - x0);
		float ty = clamp01(y - 0.5f - y0);

		float[] c00 = premultiplied(pixels[y0 * width + x0]);
		float[] c10 = premultiplied(pixels[y0 * width + x1]);
		float[] c01 = premultiplied(pixels[y1 * width + x0]);
		float[] c11 = premultiplied(pixels[y1 * width + x1]);

		float a = lerp(lerp(c00[0], c10[0], tx), lerp(c01[0], c11[0], tx), ty);
		float r = lerp(lerp(c00[1], c10[1], tx), lerp(c01[1], c11[1], tx), ty);
		float g = lerp(lerp(c00[2], c10[2], tx), lerp(c01[2], c11[2], tx), ty);
		float b = lerp(lerp(c00[3], c10[3], tx), lerp(c01[3], c11[3], tx), ty);

		float outA = clamp01(a);
		float outR = outA > 0.0001f ? clamp01(r / outA) : 0f;
		float outG = outA > 0.0001f ? clamp01(g / outA) : 0f;
		float outB = outA > 0.0001f ? clamp01(b / outA) : 0f;

		return (Math.round(outA * 255) << 24)
			| (Math.round(outR * 255) << 16)
			| (Math.round(outG * 255) << 8)
			| Math.round(outB * 255);
	}

	private static float[] premultiplied(int argb)
	{
		float alpha = ((argb >>> 24) & 0xFF) / 255f;
		return new float[] {
			alpha,
			((argb >> 16) & 0xFF) / 255f * alpha,
			((argb >> 8) & 0xFF) / 255f * alpha,
			(argb & 0xFF) / 255f * alpha
		};
	}

	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}

	private static float clamp01(float v)
	{
		return Math.max(0f, Math.min(1f, v));
	}

	/**
	 * Loads the classic bubble backdrop's shape mask (a plain white-on-transparent PNG Jin
	 * supplied) once, recolours it to {@link #CLASSIC_BUBBLE_COLOR} and scales it to the given
	 * bubble height with nearest-neighbour interpolation - blocky/crisp rather than blurred,
	 * matching the rest of the classic look. Cached per height so this only runs once per
	 * distinct size, not every frame.
	 */
	private BufferedImage classicBubbleImage(int height)
	{
		return classicBubbleCache.computeIfAbsent(height, this::buildClassicBubbleImage);
	}

	private BufferedImage buildClassicBubbleImage(int height)
	{
		BufferedImage mask = classicBubbleMask();
		int width = Math.round(height * mask.getWidth() / (float) mask.getHeight());
		BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = resized.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(mask, 0, 0, width, height, null);
		g.dispose();

		int tint = CLASSIC_BUBBLE_COLOR.getRGB();
		int[] pixels = resized.getRGB(0, 0, width, height, null, 0, width);
		for (int i = 0; i < pixels.length; i++)
		{
			if ((pixels[i] >>> 24) != 0)
			{
				pixels[i] = tint;
			}
		}
		resized.setRGB(0, 0, width, height, pixels, 0, width);
		return resized;
	}

	private BufferedImage classicBubbleMask()
	{
		if (classicBubbleMask == null)
		{
			classicBubbleMask = ImageUtil.loadImageResource(SkillBubblesOverlay.class, "rsc/bubble.png");
		}

		return classicBubbleMask;
	}

	/**
	 * Drops every cached image, so a disabled plugin doesn't keep them in memory. Client thread
	 * only, like render(), since the caches are plain HashMaps.
	 */
	void clearCaches()
	{
		skillIconCache.clear();
		toolIconCenterCache.clear();
		resizedIconCache.clear();
		classicBubbleCache.clear();
		classicBubbleMask = null;
		sizingImage = null;
		sizingImageScale = -1;
		RscSprites.clearCache();
	}

	private BufferedImage sizingImage(int size)
	{
		if (sizingImage == null || sizingImageScale != size)
		{
			sizingImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			sizingImageScale = size;
		}

		return sizingImage;
	}

	private static int[] computeOpaqueCenter(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

		int minX = width, minY = height, maxX = -1, maxY = -1;
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if ((pixels[y * width + x] >>> 24) != 0)
				{
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}

		if (maxX < minX)
		{
			// Fully transparent (e.g. still loading) - fall back to the full canvas centre.
			return new int[] {width / 2, height / 2};
		}

		return new int[] {(minX + maxX + 1) / 2, (minY + maxY + 1) / 2};
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
