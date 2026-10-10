package com.livelyfishingspots;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Debug panel: a line with a name on the left and cells in fixed-width columns on the right, each right-aligned, so
 * numbers line up under their headers.
 */
final class ColumnLine implements LayoutableRenderableEntity
{
	// Each column's width, pixels, left to right.
	static final int[] WIDTHS = {32, 34, 34};

	private final String left;
	private final String[] cells;
	private final Color color;
	private final Rectangle bounds = new Rectangle();
	private Point location = new Point();
	private Dimension size = new Dimension();

	ColumnLine(String left, Color color, String... cells)
	{
		this.left = left;
		this.color = color;
		this.cells = cells;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		int y = location.y + metrics.getHeight();
		text(graphics, left, location.x, y);
		int right = location.x + size.width;
		for (int k = cells.length - 1; k >= 0; k--)
		{
			text(graphics, cells[k], right - metrics.stringWidth(cells[k]), y);
			right -= WIDTHS[k];
		}
		Dimension drawn = new Dimension(size.width, metrics.getHeight());
		bounds.setLocation(location);
		bounds.setSize(drawn);
		return drawn;
	}

	private void text(Graphics2D graphics, String text, int x, int y)
	{
		TextComponent component = new TextComponent();
		component.setText(text);
		component.setColor(color);
		component.setPosition(new Point(x, y));
		component.render(graphics);
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		location = position;
	}

	@Override
	public void setPreferredSize(Dimension preferred)
	{
		size = preferred;
	}
}
