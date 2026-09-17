package com.seteffects;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws our own scrollable effect text directly over the native "Set Effect Bonus" box, whose
 * text is blanked by {@link SetEffectsPlugin} - the native box has no working scroll/auto-resize
 * for content this long (confirmed via the Widget Inspector: its scroll fields are unconfigured
 * and its height is a fixed fraction of its parent, not content-driven), so we render and scroll
 * it ourselves instead.
 *
 * Scrolling is click-driven (up/down arrow buttons), not mouse wheel - the game suppresses camera
 * zoom (and, it turns out, all wheel input) while this modal is open, so a
 * {@code MouseWheelListener} here would simply never fire. Plain clicks aren't suppressed (the
 * equipment slot icons in this same modal are still clickable), so arrow buttons are the
 * mechanism that's actually reachable.
 */
class SetEffectsOverlay extends Overlay implements MouseListener
{
	private static final Color TEXT_COLOR = new Color(0xff981f);
	private static final int LINE_HEIGHT = 13;
	private static final int SCROLL_STEP = LINE_HEIGHT * 3;
	private static final int ARROW_SIZE = 12;

	// The popup has a little unused space to the right of this widget's own reported bounds -
	// we're drawing our own content, not the native widget's, so nothing stops us using it
	private static final int EXTRA_WIDTH = 5;

	private final Client client;
	private final SetEffectsPlugin plugin;

	private int scrollY;

	// Set by render() each frame, read by mousePressed() - never touch live Client state from a
	// mouse listener callback, which runs on a different thread than rendering; null when the
	// box isn't currently shown or there's nothing to scroll, so stale geometry can't be clicked
	private Polygon upArrowHitbox;
	private Polygon downArrowHitbox;

	@Inject
	private SetEffectsOverlay(Client client, SetEffectsPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget setEffectWidget = getSetEffectWidget();
		if (setEffectWidget == null)
		{
			return clearHitboxes();
		}

		Rectangle bounds = setEffectWidget.getBounds();
		if (bounds.width <= 0 || bounds.height <= 0)
		{
			return clearHitboxes();
		}

		// While this screen is mid-transition (e.g. the single frame right after clicking "Stat
		// Bonus"), this widget can briefly report stale bounds from outside the popup entirely -
		// skip drawing that frame rather than show our text in the wrong place on screen
		Widget popup = client.getWidget(InterfaceID.Equipment.UNIVERSE);
		if (popup == null || popup.isHidden() || !popup.getBounds().contains(bounds))
		{
			return clearHitboxes();
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return clearHitboxes();
		}

		// We're drawing our own content over this widget, not the widget's own text, so we're not
		// bound by its reported width - widen the area we draw/wrap/hit-test against a little
		Rectangle drawBounds = new Rectangle(bounds.x, bounds.y, bounds.width + EXTRA_WIDTH, bounds.height);

		List<EffectLine> effectLines = EquippedEffects.describeEquipment(equipment);

		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();

		int textWidth = drawBounds.width - ARROW_SIZE - 2;
		List<List<EffectLineFormat.Word>> rows = new ArrayList<>();
		if (effectLines.isEmpty())
		{
			rows.addAll(wrapWords(plainWords(plugin.getFallbackText()), metrics, textWidth));
		}
		else
		{
			for (int i = 0; i < effectLines.size(); i++)
			{
				if (i > 0)
				{
					rows.add(Collections.emptyList());
				}
				rows.addAll(wrapWords(EffectLineFormat.words(effectLines.get(i), TEXT_COLOR), metrics, textWidth));
			}
		}

		int contentHeight = rows.size() * LINE_HEIGHT;
		int maxScroll = Math.max(0, contentHeight - drawBounds.height);
		scrollY = Math.max(0, Math.min(scrollY, maxScroll));

		Shape originalClip = graphics.getClip();
		graphics.setClip(drawBounds);

		int y = drawBounds.y + metrics.getAscent() - scrollY;
		for (List<EffectLineFormat.Word> row : rows)
		{
			if (y >= drawBounds.y - LINE_HEIGHT && y <= drawBounds.y + drawBounds.height)
			{
				int x = drawBounds.x;
				for (EffectLineFormat.Word word : row)
				{
					OverlayUtil.renderTextLocation(graphics, new net.runelite.api.Point(x, y), word.text, word.color);
					x += metrics.stringWidth(word.text + " ");
				}
			}
			y += LINE_HEIGHT;
		}
		graphics.setClip(originalClip);

		graphics.setColor(TEXT_COLOR);

		upArrowHitbox = maxScroll > 0 && scrollY > 0 ? upArrow(drawBounds) : null;
		if (upArrowHitbox != null)
		{
			graphics.fillPolygon(upArrowHitbox);
		}

		downArrowHitbox = maxScroll > 0 && scrollY < maxScroll ? downArrow(drawBounds) : null;
		if (downArrowHitbox != null)
		{
			graphics.fillPolygon(downArrowHitbox);
		}

		return null;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		Point point = event.getPoint();

		if (upArrowHitbox != null && upArrowHitbox.contains(point))
		{
			scrollY = Math.max(0, scrollY - SCROLL_STEP);
			event.consume();
		}
		else if (downArrowHitbox != null && downArrowHitbox.contains(point))
		{
			scrollY += SCROLL_STEP;
			event.consume();
		}

		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		return event;
	}

	private Widget getSetEffectWidget()
	{
		Widget container = client.getWidget(InterfaceID.Equipment.SET_EFFECT);
		return container != null && !container.isHidden() ? container : null;
	}

	private Dimension clearHitboxes()
	{
		upArrowHitbox = null;
		downArrowHitbox = null;
		return null;
	}

	private static Polygon upArrow(Rectangle bounds)
	{
		int x = bounds.x + bounds.width - ARROW_SIZE;
		int y = bounds.y;
		return new Polygon(
			new int[]{x, x + ARROW_SIZE, x + ARROW_SIZE / 2},
			new int[]{y + ARROW_SIZE, y + ARROW_SIZE, y},
			3);
	}

	private static Polygon downArrow(Rectangle bounds)
	{
		int x = bounds.x + bounds.width - ARROW_SIZE;
		int y = bounds.y + bounds.height - ARROW_SIZE;
		return new Polygon(
			new int[]{x, x + ARROW_SIZE, x + ARROW_SIZE / 2},
			new int[]{y, y, y + ARROW_SIZE},
			3);
	}

	private static List<EffectLineFormat.Word> plainWords(String text)
	{
		List<EffectLineFormat.Word> words = new ArrayList<>();
		for (String word : text.split(" "))
		{
			words.add(new EffectLineFormat.Word(word, TEXT_COLOR));
		}
		return words;
	}

	private static List<List<EffectLineFormat.Word>> wrapWords(List<EffectLineFormat.Word> words, FontMetrics metrics, int maxWidth)
	{
		List<List<EffectLineFormat.Word>> rows = new ArrayList<>();
		List<EffectLineFormat.Word> row = new ArrayList<>();
		int rowWidth = 0;
		int spaceWidth = metrics.stringWidth(" ");

		for (EffectLineFormat.Word word : words)
		{
			int wordWidth = metrics.stringWidth(word.text);
			int extra = row.isEmpty() ? wordWidth : spaceWidth + wordWidth;
			if (!row.isEmpty() && rowWidth + extra > maxWidth)
			{
				rows.add(row);
				row = new ArrayList<>();
				extra = wordWidth;
				rowWidth = 0;
			}

			row.add(word);
			rowWidth += extra;
		}

		if (!row.isEmpty())
		{
			rows.add(row);
		}

		return rows;
	}
}
