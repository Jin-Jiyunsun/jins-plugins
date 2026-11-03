package com.trawlingplus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;

/**
 * One of the game's own fonts, drawn in an overlay from its letter sprites: the game keeps each font as a group of
 * sprites, one per character in the Windows-1252 character set. A letter is placed by its sprite's offsets within its
 * cell, and moves the pen on by its offset plus its width, as the old game fonts were measured; the game's own
 * spacing tables are not reachable from a plugin, so the spacing may differ from the game's by a pixel here and
 * there. Built and drawn on the client thread only.
 */
final class QuillFont
{
	private static final int CHARACTERS = 256;
	private static final Charset GAME_CHARSET = Charset.forName("windows-1252");

	private final BufferedImage[] glyphs = new BufferedImage[CHARACTERS];
	private final int[] offsetX = new int[CHARACTERS];
	private final int[] offsetY = new int[CHARACTERS];
	private final int[] advance = new int[CHARACTERS];
	private final int height;

	private QuillFont(SpritePixels[] sprites)
	{
		int tallest = 0;
		for (int i = 0; i < CHARACTERS && i < sprites.length; i++)
		{
			SpritePixels sprite = sprites[i];
			if (sprite == null)
			{
				continue;
			}
			tallest = Math.max(tallest, sprite.getMaxHeight());
			if (sprite.getWidth() > 0 && sprite.getHeight() > 0)
			{
				glyphs[i] = sprite.toBufferedImage();
				offsetX[i] = sprite.getOffsetX();
				offsetY[i] = sprite.getOffsetY();
				advance[i] = sprite.getOffsetX() + sprite.getWidth();
			}
		}
		height = tallest;
		// A space has no pixels to measure, so it takes the width of a narrow letter's cell, or a third of the height.
		int space = advance['i'] > 0 ? advance['i'] + 1 : Math.max(2, height / 3);
		if (advance[' '] <= 0)
		{
			advance[' '] = space;
		}
	}

	/** The font in the given sprite group, or null if the game hasn't loaded it. */
	static QuillFont load(Client client, int spriteGroup)
	{
		SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteGroup, 0);
		if (sprites == null || sprites.length < 128)
		{
			return null;
		}
		QuillFont font = new QuillFont(sprites);
		return font.height > 0 ? font : null;
	}

	int height()
	{
		return height;
	}

	int width(String text)
	{
		int width = 0;
		for (int i = 0; i < text.length(); i++)
		{
			width += advance[index(text.charAt(i))];
		}
		return width;
	}

	/** Draws text with the top of its cells at y, every letter in the one colour. */
	void draw(Graphics2D graphics, String text, int x, int y, Color colour)
	{
		for (int i = 0; i < text.length(); i++)
		{
			int c = index(text.charAt(i));
			if (glyphs[c] != null)
			{
				graphics.drawImage(tint(glyphs[c], colour), x + offsetX[c], y + offsetY[c], null);
			}
			x += advance[c];
		}
	}

	/**
	 * The character's place in the font. A font with no lower case letters (the large capitals) takes the capital
	 * instead; anything the font doesn't have counts as a space.
	 */
	private int index(char character)
	{
		int c = character < 128 ? character : encode(character);
		if (c < 0 || c >= CHARACTERS)
		{
			return ' ';
		}
		if (advance[c] == 0 && Character.isLowerCase(character))
		{
			int upper = Character.toUpperCase(character);
			if (upper < CHARACTERS && advance[upper] > 0)
			{
				return upper;
			}
		}
		return advance[c] > 0 ? c : ' ';
	}

	private static int encode(char character)
	{
		byte[] bytes = String.valueOf(character).getBytes(GAME_CHARSET);
		return bytes.length == 1 && (bytes[0] != '?' || character == '?') ? bytes[0] & 0xff : -1;
	}

	/** The letter in the given colour, keeping its shape and how see-through each pixel is. */
	private static BufferedImage tint(BufferedImage glyph, Color colour)
	{
		BufferedImage tinted = new BufferedImage(glyph.getWidth(), glyph.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int rgb = colour.getRGB() & 0xffffff;
		for (int y = 0; y < glyph.getHeight(); y++)
		{
			for (int x = 0; x < glyph.getWidth(); x++)
			{
				int alpha = (glyph.getRGB(x, y) >>> 24) * colour.getAlpha() / 255;
				if (alpha > 0)
				{
					tinted.setRGB(x, y, alpha << 24 | rgb);
				}
			}
		}
		return tinted;
	}
}
