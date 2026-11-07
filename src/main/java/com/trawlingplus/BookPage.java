package com.trawlingplus;

import java.util.List;
import lombok.Value;

/**
 * One page of the hold book: a title and its lines, and maybe a name signed at its foot, an item's picture above its
 * number, or a tab for the section it starts. A line is text, wrapped to the page and either left aligned or centred,
 * a centred heading in faded ink, a row with a name on the left and a value on the right, or a blank line. Pages are
 * compared by what they hold, so the book redraws one only when it changes.
 */
@Value
class BookPage
{
	String title;
	List<Line> lines;
	// Signed at the foot of the page beside a wax seal, or null for none.
	String signature;
	// An item drawn just above the page number, or 0 for none.
	int picture;
	// The picture as a faint shape only, for an item not yet found.
	boolean silhouette;
	// For the first page of a section, the section's name, shown on a tab under the page; null for none.
	String tab;

	BookPage(String title, List<Line> lines)
	{
		this(title, lines, null);
	}

	BookPage(String title, List<Line> lines, String signature)
	{
		this(title, lines, signature, 0, false);
	}

	BookPage(String title, List<Line> lines, String signature, int picture, boolean silhouette)
	{
		this(title, lines, signature, picture, silhouette, null);
	}

	private BookPage(String title, List<Line> lines, String signature, int picture, boolean silhouette, String tab)
	{
		this.title = title;
		this.lines = lines;
		this.signature = signature;
		this.picture = picture;
		this.silhouette = silhouette;
		this.tab = tab;
	}

	/** The same page, starting a section with this name on its tab. */
	BookPage withTab(String name)
	{
		return new BookPage(title, lines, signature, picture, silhouette, name);
	}

	@Value
	static class Line
	{
		String left;
		// Null for a line of text.
		String right;
		boolean centred;
		// Drawn in the faded ink of the page numbers, for a heading over the lines below it.
		boolean faded;
		// Shown while the mouse is over the line, or null for none.
		String tooltip;
		// For a row, how many star milestones it has reached (three stars, each bronze, silver then gold), drawn in a
		// column after the name; -1 for a row without stars.
		int stars;
		// Wrapped so its lines come out about even, a line break starting a new paragraph: left aligned in a block
		// centred on the page, or each line centred.
		boolean balanced;

		/** The same line, with a tooltip shown while the mouse is over it. */
		Line tip(String text)
		{
			return new Line(left, right, centred, faded, text, stars, balanced);
		}

		/** The same row, with stars for this many milestones reached. */
		Line withStars(int reached)
		{
			return new Line(left, right, centred, faded, tooltip, reached, balanced);
		}
	}

	static Line text(String text)
	{
		return new Line(text, null, false, false, null, -1, false);
	}

	static Line centred(String text)
	{
		return new Line(text, null, true, false, null, -1, false);
	}

	static Line heading(String text)
	{
		return new Line(text, null, true, true, null, -1, false);
	}

	static Line blank()
	{
		return new Line("", null, false, false, null, -1, false);
	}

	static Line row(String name, String value)
	{
		return new Line(name, value, false, false, null, -1, false);
	}

	/** A journal entry: balanced lines, left aligned in a block centred on the page. */
	static Line entry(String text)
	{
		return new Line(text, null, false, false, null, -1, true);
	}

	/** A hint in faded ink: balanced lines, each centred. */
	static Line hint(String text)
	{
		return new Line(text, null, true, true, null, -1, true);
	}
}
