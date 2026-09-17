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

	private static final Pattern COLOR_TAG = Pattern.compile("</?col(=[0-9a-fA-F]+)?>");
	// A whitespace-delimited word containing at least one digit is treated as a number/percentage
	private static final Pattern NUMBER_WORD = Pattern.compile("\\S*\\d\\S*");

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

		sb.append(": ").append(highlightNumbers(line.effect));
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

		for (String word : line.effect.split(" "))
		{
			words.add(new Word(word, NUMBER_WORD.matcher(word).matches() ? NUMBER_COLOR : defaultColor));
		}

		return words;
	}

	private static String highlightNumbers(String text)
	{
		StringBuilder result = new StringBuilder();
		Matcher matcher = NUMBER_WORD.matcher(text);
		int last = 0;
		while (matcher.find())
		{
			result.append(text, last, matcher.start());
			result.append(ColorUtil.wrapWithColorTag(matcher.group(), NUMBER_COLOR));
			last = matcher.end();
		}
		result.append(text.substring(last));
		return result.toString();
	}

	static final class Word
	{
		final String text;
		final Color color;

		Word(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}
}
