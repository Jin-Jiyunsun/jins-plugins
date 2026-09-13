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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	static final Color TICK = new Color(0, 220, 80);
	private static final int PILL_PADDING = 3;

	// The tick is drawn by hand, so its size is known rather than measured.
	static final int TICK_WIDTH = 9;

	// A button counts as being on a row if their middles are within this many pixels.
	private static final int SAME_ROW = 16;

	// Pictures of facilities sit in a column down the left of the panel; the buttons start well right
	// of it. Without this the marks latch onto button frames and arrows as well.
	private static final int PICTURE_COLUMN = 40;

	// The last depth and place each net was seen with, by its position among the rows. While a net is
	// being raised or lowered its picture briefly is not one of the four depth pictures, and drawing from
	// what was last seen keeps the marks from flickering off for those frames.
	private final Map<Integer, NetRow> lastSeen = new HashMap<>();

	// The last button highlighted on each net, by the same position, and when it was last found. Between
	// the steps of a net being raised or lowered its button briefly loses its action, which looks the same
	// as nobody operating the net, so the highlight is held for up to a game tick rather than dropped.
	private final Map<Integer, SeenButton> lastButtons = new HashMap<>();
	private static final long BUTTON_GRACE_MILLIS = 600;

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
		// Each piece of the panel stands on its own; with all three off there is nothing to draw, and
		// neither is there on a boat with no trawling net to mark up.
		boolean anything = plugin.showGuides()
			&& (config.showNetDepths() || config.showNetButton() || config.showNetCorrect());
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
		long now = System.currentTimeMillis();
		int wanted = plugin.getNearestDepth().netDepth();
		for (NetRow net : netRows(rows))
		{
			// The picture of a net is its depth, so a row can never be labelled with another net.
			int depth = net.depth;
			Rectangle picture = net.picture;
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
					drawTick(graphics, left, middle, TICK);
				}
			}
			else if (config.showNetButton())
			{
				// Lowering a net takes it deeper, so the button to press follows the numbers.
				Rectangle press = guideButton(buttons, net, middle, depth < wanted, now);
				if (press != null)
				{
					highlight(graphics, press);
				}
			}
		}

		graphics.setClip(clip);
		return null;
	}

	/**
	 * The trawling nets on the panel with their depths, top row first. A depth is only ever read off one
	 * of the four depth pictures; for the moments a net's picture is anything else, it is drawn as it was
	 * last seen.
	 */
	private List<NetRow> netRows(Widget rows)
	{
		List<NetRow> nets = new ArrayList<>();
		for (Widget child : children(rows))
		{
			if (child == null || child.getRelativeX() >= PICTURE_COLUMN)
			{
				continue;
			}

			int sprite = child.getSpriteId();
			boolean net = sprite >= NET_PICTURE_RAISED && sprite <= NET_PICTURE_DEEPEST;
			Rectangle bounds = child.getBounds();
			// Some frames hand back a picture the panel has not laid out yet.
			if (net && !child.isHidden() && bounds.y >= 0 && bounds.height > 0)
			{
				NetRow row = new NetRow(child.getIndex(), sprite - NET_PICTURE_RAISED, bounds);
				lastSeen.put(child.getIndex(), row);
				nets.add(row);
			}
			else if (sprite > 0 && !net)
			{
				// The picture of something else entirely, so whatever net was in this place has gone.
				lastSeen.remove(child.getIndex());
			}
			else
			{
				NetRow seen = lastSeen.get(child.getIndex());
				if (seen != null)
				{
					nets.add(seen);
				}
			}
		}

		nets.sort((one, other) -> Integer.compare(one.picture.y, other.picture.y));
		return nets;
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

	/**
	 * Where the button that moves a net the given way is, or where it last was if it went missing less
	 * than a game tick ago, or null.
	 */
	private Rectangle guideButton(List<Widget> buttons, NetRow net, int middle, boolean lowering, long now)
	{
		Widget press = buttonOnRow(buttons, middle, lowering);
		if (press != null)
		{
			Rectangle bounds = press.getBounds();
			lastButtons.put(net.index, new SeenButton(bounds, lowering, now));
			return bounds;
		}

		SeenButton seen = lastButtons.get(net.index);
		return seen != null && seen.lowering == lowering && now - seen.millis <= BUTTON_GRACE_MILLIS
			? seen.bounds
			: null;
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

	/**
	 * A colour with any transparency taken out. Text is always drawn solid, whatever a saved colour
	 * says, since see-through lettering is only ever harder to read.
	 */
	static Color opaque(Color colour)
	{
		return new Color(colour.getRed(), colour.getGreen(), colour.getBlue());
	}

	private Color letterColour(int depth)
	{
		switch (depth)
		{
			case 1:
				return opaque(config.shallowDepthColour());
			case 2:
				return opaque(config.moderateDepthColour());
			case 3:
				return opaque(config.deepDepthColour());
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
	 * A tick starting at the given edge and centred on the given line. Shared with the display at the
	 * helm, which ticks the depth once every net is set to it.
	 */
	static void drawTick(Graphics2D graphics, int left, int middle, Color colour)
	{
		graphics.setColor(colour);
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

	/**
	 * A trawling net on the panel: how deep it is set, and where its picture is.
	 */
	private static final class NetRow
	{
		final int index;
		final int depth;
		final Rectangle picture;

		NetRow(int index, int depth, Rectangle picture)
		{
			this.index = index;
			this.depth = depth;
			this.picture = picture;
		}
	}

	/**
	 * A button highlighted on a net: where it was, which way it moves the net, and when it was last found.
	 */
	private static final class SeenButton
	{
		final Rectangle bounds;
		final boolean lowering;
		final long millis;

		SeenButton(Rectangle bounds, boolean lowering, long millis)
		{
			this.bounds = bounds;
			this.lowering = lowering;
			this.millis = millis;
		}
	}
}
