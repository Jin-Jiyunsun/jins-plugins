package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Marks up the facilities tab of the sailing side panel: how deep each trawling net is set, and which
 * button moves it towards the depth of the nearest shoal.
 */
class TrawlingPlusNetOverlay extends Overlay
{
	// A row is a trawling net if its picture is one of these four, which are the net raised, then at
	// each of the three fishing depths. The panel only gives the raise and lower buttons an action
	// while the player stands at that net, so the picture is what the marks hang off. It has to be the
	// picture itself: the frames and arrows beside it are shared with every other kind of row.
	private static final int NET_PICTURE_RAISED = SpriteID.IconSailingFacilities24x24._12;
	private static final int NET_PICTURE_DEEPEST = SpriteID.IconSailingFacilities24x24._15;

	private static final String LOWER = "Lower net";
	private static final String RAISE = "Raise net";

	// How the depth varbit of a net reads: raised, then the three fishing depths.
	private static final String[] LETTERS = {"R", "S", "M", "D"};
	private static final Color RAISED_COLOUR = Color.WHITE;

	private static final Color HIGHLIGHT = new Color(255, 200, 0);
	private static final Color PILL = new Color(0, 0, 0, 170);
	private static final Color TICK = new Color(0, 220, 80);
	private static final int PILL_PADDING = 3;

	// The tick is drawn by hand, so its size is known rather than measured.
	private static final int TICK_WIDTH = 9;

	// A button counts as being on a row if their middles are within this many pixels.
	private static final int SAME_ROW = 16;

	// Pictures of facilities sit in a column down the left of the panel; the buttons start well right
	// of it. Without this the marks latch onto button frames and arrows as well.
	private static final int PICTURE_COLUMN = 40;

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;

	@Inject
	TrawlingPlusNetOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Each piece of the panel stands on its own; with all three off there is nothing to draw.
		boolean anything = config.showNetDepths() || config.showNetButton() || config.showNetCorrect();
		Widget rows = anything
			? client.getWidget(InterfaceID.SailingSidepanel.FACILITIES_ROWS)
			: null;
		if (rows == null || rows.isHidden())
		{
			return null;
		}

		// Rows scroll inside a window, and one scrolled past its edge still reports a place on screen.
		// Clipping to the window keeps the marks off whatever is drawn beyond it, and cuts a part
		// scrolled row the same way the panel cuts the row itself.
		Widget window = client.getWidget(InterfaceID.SailingSidepanel.FACILITIES_SCROLLABLE);
		Shape clip = graphics.getClip();
		if (window != null && !window.isHidden())
		{
			graphics.clip(window.getBounds());
		}

		// The buttons are only wanted for the guide, and only exist while the player is at that net.
		List<Widget> buttons = config.showNetButton() ? netButtons() : Collections.emptyList();
		int wanted = plugin.getNearestDepth().netDepth();
		for (Widget net : netPictures(rows))
		{
			// The picture of a net is its depth, so a row can never be labelled with another net.
			int depth = net.getSpriteId() - NET_PICTURE_RAISED;
			Rectangle picture = net.getBounds();
			// The two share the picture when both are switched on, and take the middle of it when
			// either has the row to itself.
			boolean withLetter = config.showNetDepths();
			boolean withTick = config.showNetCorrect();
			int middle = picture.y + picture.height / 2;

			if (withLetter)
			{
				int width = pillWidth(graphics, LETTERS[depth]);
				int left = withTick ? picture.x - 3 : picture.x + (picture.width - width) / 2;
				drawPill(graphics, LETTERS[depth], letterColour(depth), left, middle);
			}

			if (wanted < 0)
			{
				// Nothing known to aim at, so no advice either way.
				continue;
			}

			if (depth == wanted)
			{
				if (withTick)
				{
					// Nudged in from the right edge, which sits it evenly against the letter.
					int left = withLetter
						? picture.x + picture.width - TICK_WIDTH - 2
						: picture.x + (picture.width - TICK_WIDTH) / 2;
					drawTick(graphics, left, middle);
				}
			}
			else if (config.showNetButton())
			{
				// Lowering a net takes it deeper, so the button to press follows the numbers.
				Widget press = buttonOnRow(buttons, middle, depth < wanted);
				if (press != null)
				{
					highlight(graphics, press.getBounds());
				}
			}
		}

		graphics.setClip(clip);
		return null;
	}

	/**
	 * The pictures of the trawling nets on the panel, top row first, which is the order the depth
	 * varbits number them.
	 */
	private List<Widget> netPictures(Widget rows)
	{
		List<Widget> pictures = new ArrayList<>();
		for (Widget child : children(rows))
		{
			if (child == null || child.isHidden() || child.getRelativeX() >= PICTURE_COLUMN
				|| child.getSpriteId() < NET_PICTURE_RAISED || child.getSpriteId() > NET_PICTURE_DEEPEST)
			{
				continue;
			}

			// Some frames hand back a picture the panel has not laid out yet.
			Rectangle bounds = child.getBounds();
			if (bounds.y >= 0 && bounds.height > 0)
			{
				pictures.add(child);
			}
		}

		pictures.sort((one, other) -> Integer.compare(one.getBounds().y, other.getBounds().y));
		return pictures;
	}

	/**
	 * Every raise or lower button the panel is currently offering. They only carry an action while the
	 * player stands at that net, so there may be none at all.
	 */
	private List<Widget> netButtons()
	{
		List<Widget> buttons = new ArrayList<>();
		Widget clicks = client.getWidget(InterfaceID.SailingSidepanel.FACILITIES_CONTENT_CLICKLAYER);
		if (clicks == null || clicks.isHidden())
		{
			return buttons;
		}

		for (Widget child : children(clicks))
		{
			if (child != null && !child.isHidden() && buttonKind(child) >= 0)
			{
				buttons.add(child);
			}
		}
		return buttons;
	}

	private static Widget buttonOnRow(List<Widget> buttons, int middle, boolean lowering)
	{
		for (Widget button : buttons)
		{
			Rectangle bounds = button.getBounds();
			boolean lowers = buttonKind(button) == 0;
			if (lowers == lowering && Math.abs(bounds.y + bounds.height / 2 - middle) <= SAME_ROW)
			{
				return button;
			}
		}
		return null;
	}

	private static Widget[] children(Widget widget)
	{
		Widget[] children = widget.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			children = widget.getChildren();
		}
		return children == null ? new Widget[0] : children;
	}

	/**
	 * 0 for the button that lowers a net, 1 for the one that raises it, and -1 for anything else.
	 */
	private static int buttonKind(Widget widget)
	{
		String[] actions = widget.getActions();
		for (String action : actions == null ? new String[0] : actions)
		{
			if (LOWER.equals(action))
			{
				return 0;
			}
			if (RAISE.equals(action))
			{
				return 1;
			}
		}
		return -1;
	}

	private Color letterColour(int depth)
	{
		switch (depth)
		{
			case 1:
				return config.shallowDepthColour();
			case 2:
				return config.moderateDepthColour();
			case 3:
				return config.deepDepthColour();
			default:
				return RAISED_COLOUR;
		}
	}

	/**
	 * A letter on a dark pill, starting at the given edge and centred on the given line.
	 */
	private static void drawPill(Graphics2D graphics, String letter, Color colour, int left, int middle)
	{
		FontMetrics letters = graphics.getFontMetrics();
		int width = letters.stringWidth(letter) + PILL_PADDING * 2;
		int height = letters.getAscent() + PILL_PADDING * 2;
		int top = middle - height / 2;

		graphics.setColor(PILL);
		graphics.fillRoundRect(left, top, width, height, 6, 6);
		graphics.setColor(colour);
		graphics.drawString(letter, left + PILL_PADDING, top + height - PILL_PADDING);
	}

	/**
	 * A tick starting at the given edge and centred on the given line.
	 */
	private static void drawTick(Graphics2D graphics, int left, int middle)
	{
		graphics.setColor(TICK);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawLine(left, middle, left + 3, middle + 4);
		graphics.drawLine(left + 3, middle + 4, left + TICK_WIDTH, middle - 5);
	}

	private static int pillWidth(Graphics2D graphics, String text)
	{
		return graphics.getFontMetrics().stringWidth(text) + PILL_PADDING * 2;
	}

	private static void highlight(Graphics2D graphics, Rectangle button)
	{
		graphics.setColor(new Color(HIGHLIGHT.getRed(), HIGHLIGHT.getGreen(), HIGHLIGHT.getBlue(), 110));
		graphics.fillRoundRect(button.x, button.y, button.width, button.height, 6, 6);
		graphics.setColor(new Color(HIGHLIGHT.getRed(), HIGHLIGHT.getGreen(), HIGHLIGHT.getBlue(), 220));
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRoundRect(button.x, button.y, button.width - 1, button.height - 1, 6, 6);
	}
}
