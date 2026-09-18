package com.seteffects;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;
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
 * The scrollbar itself is drawn with the game's own scrollbar sprites ({@code SpriteID.
 * ScrollbarV2}/{@code ScrollbarDraggerV2}), the same ones native interfaces use, rather than
 * hand-drawn shapes - so it looks and behaves like a normal OSRS scrollbar, complete with a
 * draggable thumb. Scrolling is driven by three independent inputs (arrow clicks, thumb drag,
 * mouse wheel) that all just move the same {@link #scrollY}, since this is our own drawn content
 * rather than the native widget's, so there's no native scroll mechanism to depend on either way.
 */
class SetEffectsOverlay extends Overlay implements MouseListener, MouseWheelListener
{
	private static final Color TEXT_COLOR = new Color(0xff981f);
	private static final int LINE_HEIGHT = 13;
	private static final int SCROLL_STEP = LINE_HEIGHT * 3;

	// Used only until the sprites below have loaded for the first time, so layout doesn't wait
	private static final int FALLBACK_SCROLLBAR_WIDTH = 16;
	private static final int MIN_THUMB_HEIGHT = 20;

	// The popup has a little unused space to the right of this widget's own reported bounds -
	// we're drawing our own content, not the native widget's, so nothing stops us using it
	private static final int EXTRA_WIDTH = 5;

	private final Client client;
	private final SetEffectsPlugin plugin;
	private final SpriteManager spriteManager;

	private int scrollY;

	// Drag state - only ever touched from mouse listener callbacks (a different thread than
	// render()), and only ever plain fields, never Client/Widget
	private boolean dragging;
	private int dragStartMouseY;
	private int dragStartScrollY;

	// Set by render() each frame, read by the mouse listener callbacks below - never touch live
	// Client state from those callbacks, which run on a different thread than rendering; null (or
	// zero) when the box isn't currently shown or there's nothing to scroll, so stale geometry
	// can't be clicked/dragged/scrolled
	private Rectangle upArrowHitbox;
	private Rectangle downArrowHitbox;
	private Rectangle thumbHitbox;
	private Rectangle scrollableBounds;
	private float dragScrollPerPixel;

	@Inject
	private SetEffectsOverlay(Client client, SetEffectsPlugin plugin, SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.spriteManager = spriteManager;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget setEffectWidget = getSetEffectWidget();
		if (setEffectWidget == null)
		{
			return clearInputState();
		}

		Rectangle bounds = setEffectWidget.getBounds();
		if (bounds.width <= 0 || bounds.height <= 0)
		{
			return clearInputState();
		}

		// While this screen is mid-transition (e.g. the single frame right after clicking "Stat
		// Bonus"), this widget can briefly report stale bounds from outside the popup entirely -
		// skip drawing that frame rather than show our text in the wrong place on screen
		Widget popup = client.getWidget(InterfaceID.Equipment.UNIVERSE);
		if (popup == null || popup.isHidden() || !popup.getBounds().contains(bounds))
		{
			return clearInputState();
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return clearInputState();
		}

		// We're drawing our own content over this widget, not the widget's own text, so we're not
		// bound by its reported width - widen the area we draw/wrap/hit-test against a little
		Rectangle drawBounds = new Rectangle(bounds.x, bounds.y, bounds.width + EXTRA_WIDTH, bounds.height);

		BufferedImage arrowUp = spriteManager.getSprite(SpriteID.ScrollbarV2.ARROW_UP, 0);
		BufferedImage arrowDown = spriteManager.getSprite(SpriteID.ScrollbarV2.ARROW_DOWN, 0);
		BufferedImage thumbTop = spriteManager.getSprite(SpriteID.ScrollbarDraggerV2.TOP, 0);
		BufferedImage thumbMiddle = spriteManager.getSprite(SpriteID.ScrollbarDraggerV2.MIDDLE, 0);
		BufferedImage thumbBottom = spriteManager.getSprite(SpriteID.ScrollbarDraggerV2.BOTTOM, 0);
		BufferedImage track = spriteManager.getSprite(SpriteID.ScrollbarDraggerV2.TRACK, 0);

		int scrollbarWidth = arrowUp != null ? arrowUp.getWidth() : FALLBACK_SCROLLBAR_WIDTH;

		List<EffectLine> effectLines = EquippedEffects.describeEquipment(equipment);

		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();

		int textWidth = drawBounds.width - scrollbarWidth - 2;
		List<List<EffectLineFormat.Word>> rows = new ArrayList<>();
		if (effectLines.isEmpty())
		{
			// The native widget's own raw text uses literal <br> tags for its line breaks (its own
			// rendering convention, not ours) - split on them rather than drawing "<br>" as if it
			// were a word, which plainWords()/wordWrap() would otherwise do with no interpretation
			for (String line : plugin.getFallbackText().split("<br>"))
			{
				String trimmed = line.trim();
				if (!trimmed.isEmpty())
				{
					rows.addAll(wrapWords(plainWords(trimmed), metrics, textWidth));
				}
			}
		}
		else
		{
			for (int i = 0; i < effectLines.size(); i++)
			{
				if (i > 0 && !effectLines.get(i).continuesPrevious)
				{
					rows.add(Collections.emptyList());
				}
				rows.addAll(wrapWords(EffectLineFormat.words(effectLines.get(i), TEXT_COLOR), metrics, textWidth));
			}
		}

		// LINE_HEIGHT is baseline-to-baseline spacing - the very last row's descenders extend past
		// that sum with nothing below them, so without adding descent back in, maxScroll fell just
		// short of enough to ever fully scroll them into view
		int contentHeight = rows.size() * LINE_HEIGHT + metrics.getDescent();
		int maxScroll = Math.max(0, contentHeight - drawBounds.height);
		// Snapshotted once and threaded through explicitly rather than re-reading the mutable
		// scrollY field later in this method - mouseDragged() runs on a different thread and can
		// write a fresh, unclamped value into that field between this clamp and a later read,
		// which was making the thumb briefly jump outside the track while actively dragging
		int clampedScrollY = Math.max(0, Math.min(scrollY, maxScroll));
		scrollY = clampedScrollY;

		Shape originalClip = graphics.getClip();
		graphics.setClip(drawBounds);

		int y = drawBounds.y + metrics.getAscent() - clampedScrollY;
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

		if (maxScroll <= 0)
		{
			clearInputState();
			return null;
		}

		scrollableBounds = drawBounds;
		drawScrollbar(graphics, drawBounds, scrollbarWidth, maxScroll, contentHeight, clampedScrollY,
			arrowUp, arrowDown, thumbTop, thumbMiddle, thumbBottom, track);

		return null;
	}

	private void drawScrollbar(Graphics2D graphics, Rectangle drawBounds, int scrollbarWidth, int maxScroll, int contentHeight,
		int scrollY, BufferedImage arrowUp, BufferedImage arrowDown, BufferedImage thumbTop, BufferedImage thumbMiddle,
		BufferedImage thumbBottom, BufferedImage track)
	{
		int scrollbarX = drawBounds.x + drawBounds.width - scrollbarWidth - 1;
		int arrowUpHeight = arrowUp != null ? arrowUp.getHeight() : scrollbarWidth;
		int arrowDownHeight = arrowDown != null ? arrowDown.getHeight() : scrollbarWidth;

		upArrowHitbox = new Rectangle(scrollbarX, drawBounds.y, scrollbarWidth, arrowUpHeight);
		if (arrowUp != null)
		{
			graphics.drawImage(arrowUp, scrollbarX, drawBounds.y, null);
		}

		int arrowDownY = drawBounds.y + drawBounds.height - arrowDownHeight;
		downArrowHitbox = new Rectangle(scrollbarX, arrowDownY, scrollbarWidth, arrowDownHeight);
		if (arrowDown != null)
		{
			graphics.drawImage(arrowDown, scrollbarX, arrowDownY, null);
		}

		int trackY = drawBounds.y + arrowUpHeight;
		int trackHeight = Math.max(0, drawBounds.height - arrowUpHeight - arrowDownHeight);
		drawTiled(graphics, track, scrollbarX, trackY, scrollbarWidth, trackHeight);

		int minThumbHeight = thumbTop != null && thumbBottom != null
			? Math.max(MIN_THUMB_HEIGHT, thumbTop.getHeight() + thumbBottom.getHeight())
			: MIN_THUMB_HEIGHT;
		double visibleRatio = Math.min(1.0, (double) drawBounds.height / contentHeight);
		int thumbHeight = Math.min(trackHeight, Math.max(minThumbHeight, (int) Math.round(trackHeight * visibleRatio)));
		int thumbTravel = trackHeight - thumbHeight;
		int thumbY = trackY + (int) Math.round(thumbTravel * ((double) scrollY / maxScroll));

		thumbHitbox = new Rectangle(scrollbarX, thumbY, scrollbarWidth, thumbHeight);
		dragScrollPerPixel = thumbTravel > 0 ? (float) maxScroll / thumbTravel : 0f;

		if (thumbTop != null && thumbBottom != null && thumbMiddle != null)
		{
			graphics.drawImage(thumbTop, scrollbarX, thumbY, null);
			graphics.drawImage(thumbBottom, scrollbarX, thumbY + thumbHeight - thumbBottom.getHeight(), null);
			drawTiled(graphics, thumbMiddle, scrollbarX, thumbY + thumbTop.getHeight(),
				scrollbarWidth, thumbHeight - thumbTop.getHeight() - thumbBottom.getHeight());
		}
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		Point point = event.getPoint();

		if (thumbHitbox != null && thumbHitbox.contains(point))
		{
			dragging = true;
			dragStartMouseY = point.y;
			dragStartScrollY = scrollY;
			event.consume();
		}
		else if (upArrowHitbox != null && upArrowHitbox.contains(point))
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
	public MouseEvent mouseDragged(MouseEvent event)
	{
		if (dragging)
		{
			int deltaY = event.getPoint().y - dragStartMouseY;
			scrollY = dragStartScrollY + Math.round(deltaY * dragScrollPerPixel);
			event.consume();
		}

		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		dragging = false;
		return event;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		Rectangle bounds = scrollableBounds;
		if (bounds != null && bounds.contains(event.getPoint()))
		{
			scrollY = Math.max(0, scrollY + event.getWheelRotation() * SCROLL_STEP);
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
	public MouseEvent mouseMoved(MouseEvent event)
	{
		return event;
	}

	private Widget getSetEffectWidget()
	{
		Widget container = client.getWidget(InterfaceID.Equipment.SET_EFFECT);
		return container != null && !container.isHidden() ? container : null;
	}

	private Dimension clearInputState()
	{
		upArrowHitbox = null;
		downArrowHitbox = null;
		thumbHitbox = null;
		scrollableBounds = null;
		dragging = false;
		return null;
	}

	private static void drawTiled(Graphics2D graphics, BufferedImage tile, int x, int y, int width, int height)
	{
		if (tile == null || height <= 0)
		{
			return;
		}

		Shape originalClip = graphics.getClip();
		graphics.clipRect(x, y, width, height);
		for (int drawY = y; drawY < y + height; drawY += tile.getHeight())
		{
			graphics.drawImage(tile, x, drawY, null);
		}
		graphics.setClip(originalClip);
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
