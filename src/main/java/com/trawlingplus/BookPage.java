package com.trawlingplus;

import java.util.List;
import lombok.Value;

/**
 * One page of the hold book: a title and its lines. A line is either text, wrapped to the page, or a row with a name
 * on the left and a value on the right. Pages are compared by what they hold, so the book redraws one only when it
 * changes.
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
	}

	static Line text(String text)
	{
		return new Line(text, null);
	}

	static Line row(String name, String value)
	{
		return new Line(name, value);
	}
}
