package com.trawlingplus;

import java.util.List;
import lombok.Value;

/**
 * One page of the hold book: a title and its lines. A line is text, wrapped to the page and either left aligned or
 * centred, a centred heading in faded ink, a row with a name on the left and a value on the right, or a blank line.
 * Pages are compared by what they hold, so the book redraws one only when it changes.
 */
@Value
class BookPage
{
	String title;
	List<Line> lines;

	@Value
	static class Line
	{
		String left;
		// Null for a line of text.
		String right;
		boolean centred;
		// Drawn in the faded ink of the page numbers, for a heading over the lines below it.
		boolean faded;
	}

	static Line text(String text)
	{
		return new Line(text, null, false, false);
	}

	static Line centred(String text)
	{
		return new Line(text, null, true, false);
	}

	static Line heading(String text)
	{
		return new Line(text, null, true, true);
	}

	static Line blank()
	{
		return new Line("", null, false, false);
	}

	static Line row(String name, String value)
	{
		return new Line(name, value, false, false);
	}
}
