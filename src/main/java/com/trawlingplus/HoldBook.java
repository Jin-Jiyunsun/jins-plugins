package com.trawlingplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A button on the cargo hold's screen, and the book it opens: drawn from the game's own Fractured Archive book
 * sprites, with the pages the plugin gives it. It only draws and reads the mouse and keyboard; nothing is sent to the
 * game. Clicks are handled as the Hub's Gauntlet Chest Popup handles its popup (see {@link #mouse}).
 */
@Slf4j
class HoldBook extends Overlay implements KeyListener
{
	// The book frame, as the Alliance Log lays it out: 34 px pieces, corners fixed, clasps centred top and bottom,
	// edges and the page tiled between them.
	private static final int TILE = 34;
	private static final int CORNER_TOP_LEFT = SpriteID.TfaBackgroundBookTfa01._0;
	private static final int EDGE_TOP = SpriteID.TfaBackgroundBookTfa01._1;
	private static final int CLASP_TOP = SpriteID.TfaBackgroundBookTfa01._2;
	private static final int CORNER_TOP_RIGHT = SpriteID.TfaBackgroundBookTfa01._3;
	private static final int EDGE_LEFT = SpriteID.TfaBackgroundBookTfa01._4;
	private static final int PAGE = SpriteID.TfaBackgroundBookTfa01._5;
	private static final int SPINE = SpriteID.TfaBackgroundBookTfa01._6;
	private static final int EDGE_RIGHT = SpriteID.TfaBackgroundBookTfa01._7;
	private static final int CORNER_BOTTOM_LEFT = SpriteID.TfaBackgroundBookTfa01._8;
	private static final int EDGE_BOTTOM = SpriteID.TfaBackgroundBookTfa01._9;
	private static final int CLASP_BOTTOM = SpriteID.TfaBackgroundBookTfa01._10;
	private static final int CORNER_BOTTOM_RIGHT = SpriteID.TfaBackgroundBookTfa01._11;

	// Page turning and closing. The arrows are matched by their order in the set; the right one has been seen in
	// game, the left one only shows from a book's second spread.
	private static final int ARROW_LEFT = SpriteID.TfaButtonArrowTfa01._2;
	private static final int ARROW_RIGHT = SpriteID.TfaButtonArrowTfa01._3;
	private static final int CLOSE = SpriteID.CloseButtons.PARCHMENT;
	private static final int CLOSE_HOVERED = SpriteID.CloseButtons.PARCHMENT_HOVERED;

	// 13 by 9 pieces, 442 x 306 px: fits the fixed size game view, with the clasps on the middle column.
	private static final int COLUMNS = 13;
	private static final int ROWS = 9;
	private static final int BOOK_WIDTH = COLUMNS * TILE;
	private static final int BOOK_HEIGHT = ROWS * TILE;
	// Where the plain page shows inside the frame, measured from the frame sprites: the top edge's border ends 8 px
	// down, the bottom edge's starts 17 px up, the left and right edges' are 13 px wide, and the spine's dark middle
	// covers 12 px from 11 px into the middle column. That leaves two pages of 202 x 281 px.
	private static final int PAGE_TOP = 8;
	private static final int PAGE_BOTTOM = BOOK_HEIGHT - 17;
	private static final int LEFT_PAGE_LEFT = 13;
	private static final int LEFT_PAGE_RIGHT = (COLUMNS / 2) * TILE + 11;
	private static final int RIGHT_PAGE_LEFT = (COLUMNS / 2) * TILE + 23;
	private static final int RIGHT_PAGE_RIGHT = BOOK_WIDTH - 13;
	private static final int PAGE_WIDTH = LEFT_PAGE_RIGHT - LEFT_PAGE_LEFT;
	// The text keeps this far from the sides of its page, so it sits centred on the page, and the title and page
	// number this far from its top and bottom, a little closer than the sides to leave more room for lines (Jin); the
	// close button and arrows sit this far from their page's corner, by their visible pixels.
	private static final int TEXT_INSET = 18;
	private static final int TITLE_INSET = 16;
	private static final int NUMBER_INSET = 10;
	private static final int CORNER_INSET = 6;

	// The button is centred on a space the size of the hold's own close button, just left of it; where that can't be
	// found, where the close button usually is, from the frame's top right. It is the Collections Logged book
	// (25 x 25) with the Fishing skill icon's fish on its cover: the icon's rod and hook (its top 8 rows) cut off, the
	// rest cut to its visible pixels and shrunk to 15 px across, centred on the middle of the cover (measured from the
	// sprite: 10.65 px in, 10.78 px down). Hovered, the button is drawn a quarter brighter.
	private static final int BUTTON_BOOK = SpriteID.IconActivities25x25.COLLECTIONS_LOGGED;
	private static final int BUTTON_FISH = SpriteID.Staticons.FISHING;
	private static final int FISH_TOP = 8;
	private static final int FISH_SIZE = 15;
	private static final double COVER_MIDDLE_X = 10.65;
	private static final double COVER_MIDDLE_Y = 10.78;
	// Moved from the middle by eye: 1 px right and 1 px up (Jin).
	private static final int FISH_NUDGE_X = 1;
	private static final int FISH_NUDGE_Y = -1;
	private static final float HOVER_BRIGHTNESS = 1.25f;
	private static final int BUTTON_GAP = 3;
	private static final int FALLBACK_SIZE = 21;
	private static final int FALLBACK_RIGHT = 31;
	private static final int FALLBACK_TOP = 7;
	private static final String CLOSE_ACTION = "Close";

	// The game's quill fonts, kept as sprite groups: the small one for text, the medium slanted one for titles (the
	// large capitals were far too big for a page). A title too wide for its page falls back to the small one.
	private static final int BODY_FONT = SpriteID.Q8_FULL;
	private static final int TITLE_FONT = SpriteID.QUILL_OBLIQUE_LARGE;

	private static final Color INK = new Color(40, 26, 12);
	private static final Color FADED_INK = new Color(40, 26, 12, 140);

	private final Client client;
	private final TrawlingPlusConfig config;
	private final TrawlingPlusPlugin plugin;

	// The pages, two to a spread, as the plugin last gave them: read again once a game tick while the book is open,
	// so the counts on them stay current. Each page's image is redrawn only when the page has changed.
	private volatile List<BookPage> pages = Collections.emptyList();
	private final List<BookPage> pagesDrawn = new ArrayList<>();
	private int pagesReadTick = -1;

	// Built once from the sprites, then drawn whole each frame.
	private BufferedImage frame;
	private BufferedImage arrowLeft;
	private BufferedImage arrowRight;
	private BufferedImage close;
	private BufferedImage closeHovered;
	private BufferedImage icon;
	private BufferedImage iconHovered;

	// The hold's close button, looked for each tick while its screen is open until found, then kept until it closes.
	private Widget holdClose;
	// The game tick the sprites or the close button were last looked for, so a miss is retried once a tick rather
	// than every frame.
	private int spritesTriedTick = -1;
	// The resource pack replacement each sprite was built from (null for the game's own), and the tick that was last
	// checked: a pack switched on, off or changed while the client runs rebuilds the book from the new sprites.
	private final Map<Integer, SpritePixels> overridesUsed = new HashMap<>();
	private int overridesCheckedTick = -1;
	private boolean spritesMissingLogged;
	private int closeTriedTick = -1;

	// Each page drawn once, the first time it shows, then kept: the text is drawn letter by letter from the game's
	// quill font, too slow to redo every frame.
	private final List<BufferedImage> pageImages = new ArrayList<>();
	private QuillFont bodyFont;
	private QuillFont titleFont;

	// The game's menu as of the last frame drawn, saved on the client thread for the mouse to read: whether it is
	// open, and whether the option under the mouse is walking, which means the mouse is out in the world.
	private volatile boolean menuOpen;
	private volatile boolean overWorld;

	// Where things were last drawn, read by the mouse on the AWT thread. Null while not drawn.
	private volatile Rectangle buttonBounds;
	private volatile Rectangle bookBounds;
	private volatile Rectangle closeBounds;
	private volatile Rectangle leftBounds;
	private volatile Rectangle rightBounds;

	// The config key for the spread last showing, kept across sessions so the book opens where it was left (Jin).
	private static final String SPREAD_KEY = "holdBookSpread";

	private final ConfigManager configManager;

	private volatile boolean open;
	// The left page of the spread showing.
	private volatile int spread;

	@Inject
	HoldBook(Client client, TrawlingPlusConfig config, TrawlingPlusPlugin plugin, ConfigManager configManager)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.configManager = configManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	/** Closes the book and lets go of the sprites, for when the plugin is switched off. */
	void reset()
	{
		open = false;
		spread = 0;
		forgetBounds();
		frame = null;
		arrowLeft = null;
		arrowRight = null;
		close = null;
		closeHovered = null;
		icon = null;
		iconHovered = null;
		holdClose = null;
		spritesTriedTick = -1;
		spritesMissingLogged = false;
		overridesUsed.clear();
		overridesCheckedTick = -1;
		closeTriedTick = -1;
		pageImages.clear();
		pagesDrawn.clear();
		pages = Collections.emptyList();
		pagesReadTick = -1;
		bodyFont = null;
		titleFont = null;
		menuOpen = false;
		overWorld = false;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget hold = config.showHoldBook() ? client.getWidget(InterfaceID.SailingBoatCargohold.FRAME) : null;
		if (hold != null && !hold.isHidden())
		{
			forgetChangedSprites();
		}
		if (hold == null || hold.isHidden() || !loadSprites())
		{
			// The book belongs to the hold's screen and closes with it.
			open = false;
			forgetBounds();
			holdClose = null;
			closeTriedTick = -1;
			return null;
		}

		Rectangle frameBounds = hold.getBounds();
		Point mouse = client.getMouseCanvasPosition();
		int mouseX = mouse.getX();
		int mouseY = mouse.getY();
		menuOpen = client.isMenuOpen();
		overWorld = open && !menuOpen && walkingUnderMouse();
		// Looked for again each tick until found: the game may not have built it on the screen's first frame.
		int tick = client.getTickCount();
		if (holdClose == null && tick != closeTriedTick)
		{
			holdClose = findClose(hold, 0);
			closeTriedTick = tick;
		}
		Rectangle closing = holdClose == null || holdClose.isHidden() ? null : holdClose.getBounds();
		Rectangle button = closing == null
			? new Rectangle(frameBounds.x + frameBounds.width - FALLBACK_RIGHT - BUTTON_GAP - FALLBACK_SIZE,
				frameBounds.y + FALLBACK_TOP, FALLBACK_SIZE, FALLBACK_SIZE)
			: new Rectangle(closing.x - BUTTON_GAP - closing.width, closing.y, closing.width, closing.height);
		// At its own size, centred on where the button goes, never scaled: the click area is the image itself.
		button = new Rectangle(button.x + (button.width - icon.getWidth()) / 2,
			button.y + (button.height - icon.getHeight()) / 2, icon.getWidth(), icon.getHeight());
		boolean lit = open || button.contains(mouseX, mouseY);
		graphics.drawImage(lit ? iconHovered : icon, button.x, button.y, null);
		buttonBounds = button;

		if (!open)
		{
			bookBounds = null;
			closeBounds = null;
			leftBounds = null;
			rightBounds = null;
			return null;
		}

		// Over the middle of the hold's screen, which it is no bigger than, kept inside the game view: in fixed size
		// mode the view stops short of the side panels and chat, so the book never covers them.
		int viewX = client.getViewportXOffset();
		int viewY = client.getViewportYOffset();
		int x = frameBounds.x + (frameBounds.width - BOOK_WIDTH) / 2;
		int y = frameBounds.y + (frameBounds.height - BOOK_HEIGHT) / 2;
		x = Math.max(viewX, Math.min(x, viewX + client.getViewportWidth() - BOOK_WIDTH));
		y = Math.max(viewY, Math.min(y, viewY + client.getViewportHeight() - BOOK_HEIGHT));
		graphics.drawImage(frame, x, y, null);
		bookBounds = new Rectangle(x, y, BOOK_WIDTH, BOOK_HEIGHT);

		if (tick != pagesReadTick)
		{
			pages = plugin.bookPages();
			pagesReadTick = tick;
		}
		List<BookPage> showing = pages;
		int first = Math.min(spread, lastSpread(showing.size()));
		int textWidth = PAGE_WIDTH - 2 * TEXT_INSET;
		int top = y + PAGE_TOP + TITLE_INSET;
		drawPage(graphics, showing, first, x + LEFT_PAGE_LEFT + TEXT_INSET, top, textWidth);
		drawPage(graphics, showing, first + 1, x + RIGHT_PAGE_LEFT + TEXT_INSET, top, textWidth);

		closing = new Rectangle(x + RIGHT_PAGE_RIGHT - CORNER_INSET - close.getWidth(), y + PAGE_TOP + CORNER_INSET,
			close.getWidth(), close.getHeight());
		graphics.drawImage(closing.contains(mouseX, mouseY) ? closeHovered : close, closing.x, closing.y, null);
		closeBounds = closing;

		int arrowY = y + PAGE_BOTTOM - CORNER_INSET - arrowLeft.getHeight();
		leftBounds = first > 0 ? drawArrow(graphics, arrowLeft, x + LEFT_PAGE_LEFT + CORNER_INSET, arrowY) : null;
		rightBounds = first + 2 < showing.size()
			? drawArrow(graphics, arrowRight, x + RIGHT_PAGE_RIGHT - CORNER_INSET - arrowRight.getWidth(), arrowY)
			: null;
		return null;
	}

	private Rectangle drawArrow(Graphics2D graphics, BufferedImage arrow, int x, int y)
	{
		Rectangle bounds = new Rectangle(x, y, arrow.getWidth(), arrow.getHeight());
		graphics.drawImage(arrow, x, y, null);
		return bounds;
	}

	/** The left page of the last spread for this many pages. */
	private static int lastSpread(int pageCount)
	{
		return Math.max(0, (pageCount - 1) / 2 * 2);
	}

	private void drawPage(Graphics2D graphics, List<BookPage> showing, int page, int x, int y, int width)
	{
		if (page >= showing.size())
		{
			return;
		}
		while (pageImages.size() <= page)
		{
			pageImages.add(null);
			pagesDrawn.add(null);
		}
		BookPage content = showing.get(page);
		if (pageImages.get(page) == null || !content.equals(pagesDrawn.get(page)))
		{
			pageImages.set(page, buildPage(content, page + 1, width,
				PAGE_BOTTOM - PAGE_TOP - TITLE_INSET - NUMBER_INSET));
			pagesDrawn.set(page, content);
		}
		graphics.drawImage(pageImages.get(page), x, y, null);
	}

	/**
	 * One page: its title in the medium quill, then its lines in the small quill (text wrapped to the page, left
	 * aligned or centred, centred in faded ink for a heading, a row with its name on the left and its value against
	 * the right edge, or a blank line), and its number at the foot.
	 */
	private BufferedImage buildPage(BookPage content, int number, int width, int height)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		String title = content.getTitle();
		QuillFont heading = titleFont.width(title) <= width ? titleFont : bodyFont;
		heading.draw(g, title, (width - heading.width(title)) / 2, 0, INK);

		int lineHeight = bodyFont.height() + 2;
		// Half a blank line between the title and the first line, and for each blank line (Jin): a whole one left too
		// little room on a page.
		int gap = lineHeight / 2;
		int line = heading.height() + 6 + gap;
		for (BookPage.Line text : content.getLines())
		{
			if (text.getRight() == null)
			{
				if (text.getLeft().isEmpty())
				{
					line += gap;
				}
				for (String part : wrap(text.getLeft(), bodyFont, width))
				{
					int x = text.isCentred() ? (width - bodyFont.width(part)) / 2 : 0;
					bodyFont.draw(g, part, x, line, text.isFaded() ? FADED_INK : INK);
					line += lineHeight;
				}
			}
			else
			{
				bodyFont.draw(g, text.getLeft(), 0, line, INK);
				bodyFont.draw(g, text.getRight(), width - bodyFont.width(text.getRight()), line, INK);
				line += lineHeight;
			}
		}

		String page = String.valueOf(number);
		bodyFont.draw(g, page, (width - bodyFont.width(page)) / 2, height - bodyFont.height(), FADED_INK);
		g.dispose();
		return image;
	}

	private static List<String> wrap(String text, QuillFont font, int width)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String tried = line.length() == 0 ? word : line + " " + word;
			if (font.width(tried) > width && line.length() > 0)
			{
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
			else
			{
				line.setLength(0);
				line.append(tried);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}

	/** The hold's close button: the one widget in its frame with a Close action. */
	private static Widget findClose(Widget widget, int depth)
	{
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (CLOSE_ACTION.equals(action))
				{
					return widget;
				}
			}
		}
		if (depth > 4)
		{
			return null;
		}
		for (Widget[] children : new Widget[][]{widget.getDynamicChildren(), widget.getStaticChildren(),
			widget.getNestedChildren()})
		{
			for (Widget child : children == null ? new Widget[0] : children)
			{
				Widget found = child == null ? null : findClose(child, depth + 1);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Repeats an image over an area, cutting the last copies short where they overrun it. */
	private static void tile(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height)
	{
		int w = image.getWidth();
		int h = image.getHeight();
		for (int ty = 0; ty < height; ty += h)
		{
			for (int tx = 0; tx < width; tx += w)
			{
				int cw = Math.min(w, width - tx);
				int ch = Math.min(h, height - ty);
				graphics.drawImage(image, x + tx, y + ty, x + tx + cw, y + ty + ch, 0, 0, cw, ch, null);
			}
		}
	}

	/**
	 * Once a game tick while the hold is open, drops the built book if any sprite's resource pack replacement is not
	 * the one it was built from, so it is built again from what the game now draws. Client thread only.
	 */
	private void forgetChangedSprites()
	{
		int tick = client.getTickCount();
		if (frame == null || tick == overridesCheckedTick)
		{
			return;
		}
		overridesCheckedTick = tick;
		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		for (Map.Entry<Integer, SpritePixels> used : overridesUsed.entrySet())
		{
			if (overrides.get(used.getKey()) != used.getValue())
			{
				log.debug("Hold book sprite {} changed by a resource pack; rebuilding", used.getKey());
				frame = null;
				spritesTriedTick = -1;
				overridesUsed.clear();
				return;
			}
		}
	}

	/** Reads the sprites the first time they are all there and builds the book's frame. Client thread only. */
	private boolean loadSprites()
	{
		if (frame != null)
		{
			return true;
		}
		int tick = client.getTickCount();
		if (tick == spritesTriedTick)
		{
			return false;
		}
		spritesTriedTick = tick;
		BufferedImage[] book = new BufferedImage[12];
		int[] ids = {CORNER_TOP_LEFT, EDGE_TOP, CLASP_TOP, CORNER_TOP_RIGHT, EDGE_LEFT, PAGE, SPINE, EDGE_RIGHT,
			CORNER_BOTTOM_LEFT, EDGE_BOTTOM, CLASP_BOTTOM, CORNER_BOTTOM_RIGHT};
		for (int i = 0; i < ids.length; i++)
		{
			book[i] = sprite(ids[i]);
		}
		BufferedImage left = sprite(ARROW_LEFT);
		BufferedImage right = sprite(ARROW_RIGHT);
		BufferedImage cross = sprite(CLOSE);
		BufferedImage crossHovered = sprite(CLOSE_HOVERED);
		// The button is two sprites drawn as one, so it always uses the game's own: a pack replacing only one of them
		// (often the skill icon) leaves a fish that doesn't match its book (Jin: never follow packs here).
		BufferedImage buttonBook = sprite(BUTTON_BOOK, false);
		BufferedImage buttonFish = sprite(BUTTON_FISH, false);
		QuillFont body = QuillFont.load(client, BODY_FONT);
		QuillFont titles = QuillFont.load(client, TITLE_FONT);
		if (body == null || titles == null || buttonBook == null || buttonFish == null || anyMissing(book) || left == null
			|| right == null || cross == null || crossHovered == null)
		{
			if (!spritesMissingLogged)
			{
				log.debug("Hold book sprites not all loaded yet; trying again each game tick");
				spritesMissingLogged = true;
			}
			return false;
		}

		BufferedImage built = new BufferedImage(BOOK_WIDTH, BOOK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = built.createGraphics();
		int right34 = BOOK_WIDTH - TILE;
		int bottom34 = BOOK_HEIGHT - TILE;
		int middle = (COLUMNS / 2) * TILE;
		tile(g, book[5], TILE, TILE, BOOK_WIDTH - 2 * TILE, BOOK_HEIGHT - 2 * TILE);
		tile(g, book[6], middle, TILE, TILE, BOOK_HEIGHT - 2 * TILE);
		tile(g, book[1], TILE, 0, middle - TILE, TILE);
		tile(g, book[1], middle + TILE, 0, right34 - middle - TILE, TILE);
		tile(g, book[9], TILE, bottom34, middle - TILE, TILE);
		tile(g, book[9], middle + TILE, bottom34, right34 - middle - TILE, TILE);
		tile(g, book[4], 0, TILE, TILE, BOOK_HEIGHT - 2 * TILE);
		tile(g, book[7], right34, TILE, TILE, BOOK_HEIGHT - 2 * TILE);
		g.drawImage(book[0], 0, 0, null);
		g.drawImage(book[3], right34, 0, null);
		g.drawImage(book[8], 0, bottom34, null);
		g.drawImage(book[11], right34, bottom34, null);
		g.drawImage(book[2], middle, 0, null);
		g.drawImage(book[10], middle, bottom34, null);
		g.dispose();

		BufferedImage button = new BufferedImage(buttonBook.getWidth(), buttonBook.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D b = button.createGraphics();
		b.drawImage(buttonBook, 0, 0, null);
		BufferedImage fishOnly = trim(buttonFish, new Rectangle(0, FISH_TOP, buttonFish.getWidth(),
			buttonFish.getHeight() - FISH_TOP));
		Image fish = shrunk(trim(fishOnly, visible(fishOnly)), FISH_SIZE);
		b.drawImage(fish, (int) Math.round(COVER_MIDDLE_X - fish.getWidth(null) / 2.0) + FISH_NUDGE_X,
			(int) Math.round(COVER_MIDDLE_Y - fish.getHeight(null) / 2.0) + FISH_NUDGE_Y, null);
		b.dispose();
		// Cut down to their visible pixels, so they sit by what shows rather than by their transparent padding.
		arrowLeft = trim(left, visible(left));
		arrowRight = trim(right, visible(right));
		Rectangle crossShows = visible(cross).union(visible(crossHovered));
		close = trim(cross, crossShows);
		closeHovered = trim(crossHovered, crossShows);
		icon = button;
		iconHovered = brighter(button, HOVER_BRIGHTNESS);
		bodyFont = body;
		titleFont = titles;
		frame = built;
		return true;
	}

	/** Shrunk to the given size on its longer side, averaging pixels rather than dropping them. */
	private static Image shrunk(BufferedImage image, int size)
	{
		double scale = (double) size / Math.max(image.getWidth(), image.getHeight());
		if (scale >= 1)
		{
			return image;
		}
		Image scaled = image.getScaledInstance((int) Math.round(image.getWidth() * scale),
			(int) Math.round(image.getHeight() * scale), Image.SCALE_AREA_AVERAGING);
		BufferedImage done = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = done.createGraphics();
		g.drawImage(scaled, 0, 0, null);
		g.dispose();
		return done;
	}

	/** A copy with every colour scaled up by the given factor, keeping how see-through each pixel is. */
	private static BufferedImage brighter(BufferedImage image, float factor)
	{
		BufferedImage lit = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				int r = Math.min(255, Math.round(((argb >> 16) & 0xff) * factor));
				int g = Math.min(255, Math.round(((argb >> 8) & 0xff) * factor));
				int bl = Math.min(255, Math.round((argb & 0xff) * factor));
				lit.setRGB(x, y, (argb & 0xff000000) | r << 16 | g << 8 | bl);
			}
		}
		return lit;
	}

	/** The smallest area holding every pixel that isn't fully see-through; the whole image if there are none. */
	private static Rectangle visible(BufferedImage image)
	{
		int left = image.getWidth();
		int top = image.getHeight();
		int right = -1;
		int bottom = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					left = Math.min(left, x);
					top = Math.min(top, y);
					right = Math.max(right, x);
					bottom = Math.max(bottom, y);
				}
			}
		}
		return right < 0 ? new Rectangle(0, 0, image.getWidth(), image.getHeight())
			: new Rectangle(left, top, right - left + 1, bottom - top + 1);
	}

	private static BufferedImage trim(BufferedImage image, Rectangle area)
	{
		Rectangle inside = area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
		return image.getSubimage(inside.x, inside.y, inside.width, inside.height);
	}

	private static boolean anyMissing(BufferedImage[] images)
	{
		for (BufferedImage image : images)
		{
			if (image == null)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A sprite at its full size, with the transparent space around it that the cache trims off put back, so pieces
	 * line up as the game lays them out. A resource pack's replacement is used where there is one.
	 */
	private BufferedImage sprite(int id)
	{
		return sprite(id, true);
	}

	/** As {@link #sprite(int)}, but with packs left out when asked: then always the game's own sprite. */
	private BufferedImage sprite(int id, boolean followPacks)
	{
		SpritePixels pixels = null;
		if (followPacks)
		{
			pixels = client.getSpriteOverrides().get(id);
			overridesUsed.put(id, pixels);
		}
		if (pixels == null)
		{
			SpritePixels[] loaded = client.getSprites(client.getIndexSprites(), id, 0);
			pixels = loaded == null || loaded.length == 0 ? null : loaded[0];
		}
		if (pixels == null || pixels.getWidth() <= 0 || pixels.getHeight() <= 0)
		{
			return null;
		}
		int width = Math.max(pixels.getMaxWidth(), pixels.getOffsetX() + pixels.getWidth());
		int height = Math.max(pixels.getMaxHeight(), pixels.getOffsetY() + pixels.getHeight());
		BufferedImage full = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = full.createGraphics();
		g.drawImage(pixels.toBufferedImage(), pixels.getOffsetX(), pixels.getOffsetY(), null);
		g.dispose();
		return full;
	}

	private void forgetBounds()
	{
		buttonBounds = null;
		bookBounds = null;
		closeBounds = null;
		leftBounds = null;
		rightBounds = null;
	}

	private static boolean inside(Rectangle bounds, MouseEvent event)
	{
		return bounds != null && bounds.contains(event.getPoint());
	}

	/**
	 * The mouse, handled as the Hub's Gauntlet Chest Popup handles its popup. Only left presses count, and none while
	 * the game's right-click menu is open (as of the last frame drawn), so picking a menu option over the book still
	 * reaches the game. A left press on the button or anywhere on the open book is acted on and kept from the game;
	 * one out in the world (where the game offers walking) closes the book and still reaches the game. Releases,
	 * clicks and right presses always reach it, so right-clicking the book shows the game's menu for whatever is under
	 * it.
	 */
	final MouseListener mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (!SwingUtilities.isLeftMouseButton(event) || menuOpen)
			{
				return event;
			}
			if (inside(buttonBounds, event))
			{
				open = !open;
				if (open)
				{
					Integer saved = configManager.getConfiguration(TrawlingPlusConfig.GROUP, SPREAD_KEY, Integer.class);
					spread = saved != null && saved > 0 ? saved / 2 * 2 : 0;
				}
				event.consume();
			}
			else if (open && inside(bookBounds, event))
			{
				if (inside(closeBounds, event))
				{
					open = false;
				}
				else if (inside(leftBounds, event))
				{
					turnTo(Math.max(0, Math.min(spread, lastSpread(pages.size())) - 2));
				}
				else if (inside(rightBounds, event))
				{
					turnTo(Math.min(spread + 2, lastSpread(pages.size())));
				}
				event.consume();
			}
			else if (open && overWorld)
			{
				open = false;
			}
			return event;
		}
	};

	/** Shows the spread starting at the given page, and saves it for the next time the book opens. */
	private void turnTo(int page)
	{
		if (page != spread)
		{
			spread = page;
			configManager.setConfiguration(TrawlingPlusConfig.GROUP, SPREAD_KEY, page);
		}
	}

	/** Whether the game offers walking under the mouse, by its type, so other plugins renaming it don't matter. */
	private boolean walkingUnderMouse()
	{
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (entry.getType() == MenuAction.WALK)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	/** Escape closes the book and still reaches the game, as with Gauntlet Chest Popup. */
	@Override
	public void keyPressed(KeyEvent event)
	{
		if (open && event.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			open = false;
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}
}
