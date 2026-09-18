package com.seteffects;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.ColorUtil;

/**
 * Shared colour scheme + text-building helpers for displaying an {@link EffectLine}: the name in
 * white, the worn/total tally in green (complete) or red (incomplete), and any damage/accuracy
 * numbers within the effect text in yellow. Used by both the hover tooltip (colour-tag markup
 * baked into the string, since {@code Tooltip} text understands {@code <col=RRGGBB>} tags the
 * same way the game's own widget text does) and the overlay (a list of (text, colour) words,
 * since {@code Graphics2D} can't interpret that markup itself and has to draw each run directly).
 */
final class EffectLineFormat
{
	static final Color NAME_COLOR = Color.WHITE;
	static final Color COMPLETE_COLOR = Color.GREEN;
	static final Color INCOMPLETE_COLOR = Color.RED;
	static final Color NUMBER_COLOR = Color.YELLOW;
	// The colour of the game's own text in the equipment stats popup
	static final Color EFFECT_COLOR = new Color(0xff981f);

	private static final Pattern COLOR_TAG = Pattern.compile("</?col(=[0-9a-fA-F]+)?>");
	// A whitespace-delimited word containing a digit holds a number/percentage; only that part is
	// coloured - from the first digit (or a sign right before it) to the last non-punctuation
	// character, so a bracket or the comma/period ending a clause stays the text colour
	private static final Pattern NUMBER_PARTS = Pattern.compile("^([^\\d+\\-]*)([+\\-]?\\d.*?)([.,;:)\\]!?]*)$");

	private EffectLineFormat()
	{
	}

	/**
	 * Builds colour-tag markup for the tooltip renderer. The name's internal spaces are replaced
	 * with a non-breaking space so word-wrapping (which splits on plain spaces) never slices its
	 * colour tag in half; every other tagged span here is already a single whitespace-free token,
	 * so it can't be split by wrapping regardless.
	 */
	static String tooltipMarkup(EffectLine line)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(ColorUtil.wrapWithColorTag(line.name.replace(' ', ' '), NAME_COLOR));

		if (line.total != null)
		{
			Color tallyColor = line.isFullSet() ? COMPLETE_COLOR : INCOMPLETE_COLOR;
			sb.append(' ').append(ColorUtil.wrapWithColorTag("(" + line.worn + "/" + line.total + ")", tallyColor));
		}

		sb.append(": ").append(colourEffect(line.effect));
		return sb.toString();
	}

	/**
	 * Length of a tooltip-markup word with colour tags (and the name's non-breaking spaces)
	 * stripped back out, for wrap-width comparisons against plain, untagged text.
	 */
	static int visibleLength(String taggedWord)
	{
		return COLOR_TAG.matcher(taggedWord).replaceAll("").replace(' ', ' ').length();
	}

	/**
	 * The same line broken into (text, colour) words for the overlay, which draws each word
	 * itself via {@code Graphics2D} and so needs real colours rather than markup.
	 */
	static List<Word> words(EffectLine line, Color defaultColor)
	{
		List<Word> words = titleWords(line);
		words.addAll(effectWords(line, defaultColor));
		return words;
	}

	/** Just the "Name (x/y):" part of {@link #words}, so the overlay can put it on its own row. */
	static List<Word> titleWords(EffectLine line)
	{
		List<Word> words = new ArrayList<>();
		String[] nameWords = line.name.split(" ");
		for (int i = 0; i < nameWords.length; i++)
		{
			boolean lastNameWord = i == nameWords.length - 1;
			String text = lastNameWord && line.total == null ? nameWords[i] + ":" : nameWords[i];
			words.add(new Word(text, NAME_COLOR));
		}

		if (line.total != null)
		{
			Color tallyColor = line.isFullSet() ? COMPLETE_COLOR : INCOMPLETE_COLOR;
			words.add(new Word("(" + line.worn + "/" + line.total + "):", tallyColor));
		}

		return words;
	}

	/** Just the effect text of {@link #words}. */
	static List<Word> effectWords(EffectLine line, Color defaultColor)
	{
		List<Word> words = new ArrayList<>();
		String previous = null;
		for (String word : line.effect.split(" "))
		{
			int[] range = numberRange(word, previous);
			words.add(range == null ? new Word(word, defaultColor) : new Word(word, defaultColor, range[0], range[1]));
			previous = word;
		}
		return words;
	}

	/**
	 * Formats a percentage stored as tenths (e.g. `5` -> "0.5", `25` -> "2.5", `50` -> "5") -
	 * shared by the dynamic per-piece resolvers in {@link PerPieceSets} so their percentages are
	 * computed as integers rather than accumulating floating-point rounding artifacts like
	 * `2.5000000000000004` from repeated `double` addition.
	 */
	static String formatTenths(int tenths)
	{
		return tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
	}

	/** Like {@link #formatTenths} but for hundredths (e.g. `125` -> "1.25", `40` -> "0.4", `500` -> "5"). */
	static String formatHundredths(int hundredths)
	{
		int whole = hundredths / 100;
		int fraction = hundredths % 100;
		if (fraction == 0)
		{
			return String.valueOf(whole);
		}
		if (fraction % 10 == 0)
		{
			return whole + "." + (fraction / 10);
		}
		return whole + "." + (fraction < 10 ? "0" + fraction : String.valueOf(fraction));
	}

	/**
	 * The {start, end} of the part of a word to colour as a number, or null if it has none. A number
	 * right after the word "tier" is a tier, not a value, so it isn't coloured.
	 */
	private static int[] numberRange(String word, String previousWord)
	{
		if (previousWord != null && previousWord.equalsIgnoreCase("tier"))
		{
			return null;
		}

		Matcher matcher = NUMBER_PARTS.matcher(word);
		return matcher.matches() ? new int[]{matcher.start(2), matcher.end(2)} : null;
	}

	/**
	 * The effect text for the tooltip: the same orange as the equipment popup's own text with just the
	 * numbers highlighted. Every piece is tagged on its own (never across a space) because the game's
	 * closing tag resets to the default colour rather than back to the enclosing one, and because
	 * word-wrapping splits on spaces.
	 */
	private static String colourEffect(String text)
	{
		StringBuilder result = new StringBuilder();
		String previous = null;
		for (String word : text.split(" ", -1))
		{
			if (previous != null)
			{
				result.append(' ');
			}

			int[] range = numberRange(word, previous);
			if (range == null)
			{
				result.append(orange(word));
			}
			else
			{
				result.append(orange(word.substring(0, range[0])))
					.append(ColorUtil.wrapWithColorTag(word.substring(range[0], range[1]), NUMBER_COLOR))
					.append(orange(word.substring(range[1])));
			}
			previous = word;
		}
		return result.toString();
	}

	private static String orange(String text)
	{
		return text.isEmpty() ? "" : ColorUtil.wrapWithColorTag(text, EFFECT_COLOR);
	}

	static final class Word
	{
		final String text;
		final Color color;
		// The part of the text to draw in NUMBER_COLOR instead ({@code -1} for none)
		final int numberStart;
		final int numberEnd;

		Word(String text, Color color)
		{
			this(text, color, -1, -1);
		}

		Word(String text, Color color, int numberStart, int numberEnd)
		{
			this.text = text;
			this.color = color;
			this.numberStart = numberStart;
			this.numberEnd = numberEnd;
		}
	}
}
