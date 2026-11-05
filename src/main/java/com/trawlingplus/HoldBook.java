package com.trawlingplus;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
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
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

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

	// Page turning and closing. The arrows are matched by their order in the set; the left one only shows from a
	// book's second spread.
	private static final int ARROW_LEFT = SpriteID.TfaButtonArrowTfa01._2;
	private static final int ARROW_RIGHT = SpriteID.TfaButtonArrowTfa01._3;
	private static final int CLOSE = SpriteID.CloseButtons.PARCHMENT;
	private static final int CLOSE_HOVERED = SpriteID.CloseButtons.PARCHMENT_HOVERED;
	// Sailing's large red wax seal, pressed beside a name signed at the foot of a page.
	private static final int SEAL = SpriteID.IconSailingWaxseal37x37._1;
	// How near the page's own edge the seal may go.
	private static final int SEAL_PAGE_EDGE = 3;
	// The seal sits off to the lower left of the name, so it looks pressed on by hand: its left edge this far left of
	// the name's, and its top this far below the name's.
	private static final int SEAL_LEFT_OF_NAME = 61;
	private static final int SEAL_BELOW_NAME = 19;
	// Two ribbon tails hanging from behind the seal, splayed apart, each with a notch cut into its end like the
	// bookmark's: in px, their width, how far below the seal's middle they reach, and how far out each leans.
	private static final int SEAL_TAIL_WIDTH = 7;
	private static final int SEAL_TAIL_LENGTH = 26;
	private static final int SEAL_TAIL_LEAN = 6;
	private static final int SEAL_TAIL_NOTCH = 4;
	// Blue, for contrast with the red wax, close to the game's blue seal.
	private static final Color SEAL_TAIL = new Color(48, 86, 122);
	private static final Color SEAL_TAIL_EDGE = new Color(26, 48, 74);
	// The Fishing skill's fish pressed into the middle of the seal, as the stamp left it: shrunk to this many px
	// across, shaded in the seal's own red, a shade under the wax, darker where the icon is dark.
	private static final int SEAL_FISH_SIZE = 16;
	private static final float SEAL_FISH_SHADE_DARK = 0.85f;
	private static final float SEAL_FISH_SHADE_LIGHT = 1f;
	// A 1 px lit edge just past the fish's lower right outline, as the wax catches the light along a pressed-in rim,
	// and a 1 px shadowed edge just inside its upper left outline, in the seal's own brightest and darkest colours (the
	// game's has just three: its highlight, wax and ring), so a resource pack's seal gets its own. Only colours making up
	// at least this share of the seal count, so a stray pixel isn't picked. Icon pixels at least this opaque count as
	// the fish.
	private static final double SEAL_COLOUR_SHARE = 0.02;
	private static final int SEAL_FISH_SOLID = 60;
	// The middle of the seal's inner ring, measured on the trimmed sprite: a pixel left of the sprite's own middle.
	private static final double SEAL_RING_X = 17;
	private static final double SEAL_RING_Y = 16.5;
	// How much more saturated the seal is drawn than the game's own, whose wax is quite dull on the page.
	private static final float SEAL_SATURATION = 1.8f;

	// A row's stars: three, each bronze, then silver, then gold as more milestones are reached, drawn from this pixel
	// mask with a darker edge, in a column a little after the longest name on the page.
	private static final String[] STAR = {
		"....#....",
		"....#....",
		"...###...",
		"#########",
		".#######.",
		"..#####..",
		"..##.##..",
		".##...##.",
		".#.....#.",
	};
	private static final int STARS = 3;
	private static final int STAR_SPACING = 10;
	private static final int STAR_GAP = 5;
	// Bronze, silver and gold: the fill, then the edge.
	private static final Color[][] STAR_METALS = {
		{new Color(184, 115, 60), new Color(112, 62, 28)},
		{new Color(200, 200, 208), new Color(104, 104, 116)},
		{new Color(236, 188, 52), new Color(146, 100, 14)},
	};

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
	private static final int PAGE_WIDTH = LEFT_PAGE_RIGHT - LEFT_PAGE_LEFT;
	private static final int PAGE_HEIGHT = PAGE_BOTTOM - PAGE_TOP;
	// The text keeps this far from the sides of its page, so it sits centred on the page, and the title and page
	// number this far from its top and bottom; the close button and arrows sit this far from their page's corner,
	// by their visible pixels.
	private static final int TEXT_INSET = 18;
	private static final int TITLE_INSET = 16;
	private static final int NUMBER_INSET = 10;
	private static final int CORNER_INSET = 6;

	// A page turn: the leaf swings over the spine in this long, easing in and out, drawn in one-pixel strips, each
	// taller the further it stands off the page, up to this share taller at the leaf's outer edge when upright.
	private static final long TURN_MILLIS = 350;
	private static final double TURN_LIFT = 0.15;
	// The leaf darkens as it stands up, more towards the fold, and casts a shadow this wide on the page it passes over.
	private static final float LEAF_SHADE = 0.3f;
	private static final float FOLD_SHADE = 0.15f;
	private static final float LEAF_SHADOW = 0.25f;
	private static final int LEAF_SHADOW_WIDTH = 24;
	// A leaf side is a row taller than its page, for the bookmark ribbon hanging a pixel above the page's top.
	private static final int LEAF_HEIGHT = PAGE_HEIGHT + 1;
	// Leaves and the cover swing about the middle of the spine; a leaf's inner edge sits this far from it.
	private static final double SPINE_MIDDLE = (LEFT_PAGE_RIGHT + RIGHT_PAGE_LEFT) / 2.0;
	private static final double LEAF_GAP = RIGHT_PAGE_LEFT - SPINE_MIDDLE;
	// A mouse x no button contains, for buttons drawn without hover.
	private static final int NO_HOVER = Integer.MIN_VALUE;

	// Opening and closing: the front cover swings over the spine like a leaf, this long, its inside the left half of
	// the open book. Closed, the book is just its cover, centred where the open book goes.
	private static final long COVER_MILLIS = 450;
	// The cover lifts further towards the viewer than a page, and casts a wider shadow on the right page.
	private static final double COVER_LIFT = 0.3;
	private static final int COVER_SHADOW_WIDTH = 40;
	private static final int COVER_WIDTH = BOOK_WIDTH - (int) SPINE_MIDDLE;
	// The cover, made in the frame's own leather colours: a darker hinge along the spine, a tooled panel set in from
	// the edges, the frame's corner pieces at the fore edge and the title in gold leaf.
	private static final int COVER_HINGE = 6;
	// The hinge and the groove's dark line are the leather this much darker.
	private static final float COVER_DARKER = 0.75f;
	private static final int COVER_PANEL_INSET = 12;
	private static final String COVER_TITLE = "Trawling Log";
	private static final int COVER_TITLE_Y = 70;
	private static final Color GOLD_LEAF = new Color(214, 172, 72);

	// The button is centred on a space the size of the hold's own close button, just left of it; where that can't be
	// found, where the close button usually is, from the frame's top right. It is the Collections Logged book
	// (25 x 25) with the Fishing skill icon's fish on its cover: the icon's rod and hook (its top 8 rows) cut off, the
	// rest cut to its visible pixels and shrunk to 15 px across, centred on the middle of the cover (measured from the
	// sprite: 10.65 px in, 10.78 px down). Hovered, the button is drawn a quarter brighter.
	private static final int BUTTON_BOOK = SpriteID.IconActivities25x25.COLLECTIONS_LOGGED;
	private static final int BUTTON_FISH = SpriteID.Staticons.FISHING;
	private static final int FISH_TOP = 8;
	private static final int FISH_SIZE = 15;
	private static final double BUTTON_COVER_X = 10.65;
	private static final double BUTTON_COVER_Y = 10.78;
	// How far the fish sits off the cover's middle, in px: 1 right and 1 up.
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

	// The bookmark: a ribbon drawn hanging from the top of the left page, faded until the spread showing is the one
	// marked, then red. Pixel sizes, with a notch cut into its foot.
	private static final int RIBBON_WIDTH = 11;
	private static final int RIBBON_HEIGHT = 24;
	private static final int RIBBON_NOTCH = 5;
	private static final int RIBBON_LEFT = 14;
	private static final Color RIBBON_UNSET = new Color(40, 26, 12, 70);
	// Fainter still while another spread holds the bookmark.
	private static final Color RIBBON_ELSEWHERE = new Color(40, 26, 12, 40);
	private static final Color RIBBON_HOVERED = new Color(40, 26, 12, 130);
	private static final Color RIBBON_SET = new Color(150, 32, 28);
	private static final Color RIBBON_SET_HOVERED = new Color(118, 24, 21);
	private static final String BOOKMARK_TIP = "Bookmark these pages: the book opens here"
		+ "</br>the first time it is opened each session.";
	private static final String UNBOOKMARK_TIP = "Remove the bookmark.";

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
	// Optional: a page signed without it just has the name.
	private BufferedImage seal;
	private BufferedImage cover;

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
	// Each drawn page's lines that have a tooltip, by where they sit on the page's image.
	private final List<Map<Rectangle, String>> pageTips = new ArrayList<>();
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
	private volatile Rectangle bookmarkBounds;

	// Set once the book has first been opened in this RuneLite profile, after which its button stops pulsing.
	private static final String SEEN_KEY = "holdBookSeen";
	private static final long BUTTON_PULSE_MILLIS = 1200;

	// The config key for the bookmarked spread's left page, kept across sessions. The book opens there the first
	// time each session, then where it was last left.
	private static final String BOOKMARK_KEY = "holdBookBookmark";
	// Where the book opened before bookmarks, no longer used: taken out of the config when the plugin starts.
	static final String OLD_SPREAD_KEY = "holdBookSpread";

	private final ConfigManager configManager;
	private final TooltipManager tooltipManager;

	private volatile boolean open;
	// The left page of the spread showing.
	private volatile int spread;
	// The left page of the spread last turned from, -1 for none, and when that turn started.
	private volatile int turnFrom = -1;
	private volatile long turnStart;
	// The two sides of the turning leaf, parchment and page, drawn again each frame of a turn.
	private BufferedImage leafRight;
	private BufferedImage leafLeft;
	// When the cover last started swinging, 0 for never, and whether it was opening; the inside of the cover, the
	// left half of the open book, drawn again each frame of the swing.
	private volatile long coverStart;
	private volatile boolean coverOpening;
	private BufferedImage coverInside;
	// Whether the book has been opened since the plugin started, after which it opens where it was left.
	private volatile boolean openedThisSession;
	// The bookmarked spread's left page, -1 for none, or null until read from the config.
	private volatile Integer bookmark;
	// Whether the book has ever been opened in this RuneLite profile, or null until read from the config.
	private volatile Boolean seen;

	@Inject
	HoldBook(Client client, TrawlingPlusConfig config, TrawlingPlusPlugin plugin, ConfigManager configManager,
		TooltipManager tooltipManager)
	{
		this.tooltipManager = tooltipManager;
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
		turnFrom = -1;
		leafRight = null;
		leafLeft = null;
		coverStart = 0;
		coverInside = null;
		openedThisSession = false;
		bookmark = null;
		seen = null;
		forgetBounds();
		frame = null;
		arrowLeft = null;
		arrowRight = null;
		close = null;
		closeHovered = null;
		icon = null;
		iconHovered = null;
		seal = null;
		cover = null;
		holdClose = null;
		spritesTriedTick = -1;
		spritesMissingLogged = false;
		overridesUsed.clear();
		overridesCheckedTick = -1;
		closeTriedTick = -1;
		pageImages.clear();
		pageTips.clear();
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
			// The book belongs to the hold's screen and closes with it, at once, letting go of the images only a turn
			// or swing draws into.
			open = false;
			coverStart = 0;
			leafRight = null;
			leafLeft = null;
			coverInside = null;
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
		if (!lit && !bookSeen())
		{
			// Until the book is first opened, the button slowly brightens to its hovered look and back.
			double phase = (System.currentTimeMillis() % BUTTON_PULSE_MILLIS) / (double) BUTTON_PULSE_MILLIS;
			float glow = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * phase));
			Composite before = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glow));
			graphics.drawImage(iconHovered, button.x, button.y, null);
			graphics.setComposite(before);
		}
		buttonBounds = button;

		long now = System.currentTimeMillis();
		long swung = now - coverStart;
		boolean swinging = coverStart > 0 && swung < COVER_MILLIS;
		if (!open && !swinging)
		{
			forgetBookBounds();
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

		if (tick != pagesReadTick)
		{
			pages = plugin.bookPages();
			pagesReadTick = tick;
		}
		List<BookPage> showing = pages;
		int first = Math.min(spread, lastSpread(showing.size()));

		if (swinging)
		{
			// Clicks on the book while it opens do nothing, and none are kept while it closes.
			forgetBookBounds();
			bookBounds = open ? new Rectangle(x, y, BOOK_WIDTH, BOOK_HEIGHT) : null;
			double eased = eased(swung, COVER_MILLIS);
			double angle = Math.PI * (coverOpening ? eased : 1 - eased);
			drawCover(graphics, showing, first, angle, x, y);
			return null;
		}

		graphics.drawImage(frame, x, y, null);
		bookBounds = new Rectangle(x, y, BOOK_WIDTH, BOOK_HEIGHT);
		int from = turnFrom;
		long turned = now - turnStart;
		boolean turning = from >= 0 && from != first && from < showing.size() && turned < TURN_MILLIS;
		// While turning, the pages either side of the leaf: the lower spread's left page and the higher one's right.
		int lower = turning ? Math.min(from, first) : first;
		int higher = turning ? Math.max(from, first) : first;
		int top = y + PAGE_TOP + TITLE_INSET;
		drawPage(graphics, showing, lower, x + LEFT_PAGE_LEFT, y + PAGE_TOP);
		drawPage(graphics, showing, higher + 1, x + RIGHT_PAGE_LEFT, y + PAGE_TOP);
		if (!menuOpen && !turning)
		{
			showTip(first, mouseX - (x + LEFT_PAGE_LEFT + TEXT_INSET), mouseY - top);
			showTip(first + 1, mouseX - (x + RIGHT_PAGE_LEFT + TEXT_INSET), mouseY - top);
		}

		// The buttons belong to their pages and turn with them, uncovering the next page's as the leaf lifts. Hovering
		// shows only while no page is turning; clicks go to the spread being turned to.
		int hoverX = turning || menuOpen ? NO_HOVER : mouseX;
		drawLeftButtons(graphics, lower, x + LEFT_PAGE_LEFT, y + PAGE_TOP, hoverX, mouseY);
		drawRightButtons(graphics, higher, showing.size(), x + RIGHT_PAGE_LEFT, y + PAGE_TOP, hoverX, mouseY);
		closeBounds = closeAt(x + RIGHT_PAGE_LEFT, y + PAGE_TOP);
		// The book opens on the cover anyway, so the first spread has no bookmark.
		bookmarkBounds = first == 0 ? null : ribbonAt(x + LEFT_PAGE_LEFT, y + PAGE_TOP);
		leftBounds = first == 0 ? null : leftArrowAt(x + LEFT_PAGE_LEFT, y + PAGE_TOP);
		rightBounds = first + 2 < showing.size() ? rightArrowAt(x + RIGHT_PAGE_LEFT, y + PAGE_TOP) : null;
		if (bookmarkBounds != null && bookmarkBounds.contains(hoverX, mouseY))
		{
			tooltipManager.add(new Tooltip(bookmarkedSpread() == first ? UNBOOKMARK_TIP : BOOKMARK_TIP));
		}

		if (turning)
		{
			double eased = eased(turned, TURN_MILLIS);
			// Lying on the right page at 0, on the left at pi.
			double angle = Math.PI * (first > from ? eased : 1 - eased);
			drawLeaf(graphics, showing, lower + 1, higher, angle, x, y);
		}
		return null;
	}

	/** How far through a swing this long it is, from 0 to 1, easing in and out. */
	private static double eased(long elapsed, long millis)
	{
		return (1 - Math.cos(Math.PI * elapsed / millis)) / 2;
	}

	/** A spread's left page buttons, the page's top left given: the back arrow and the bookmark, past the cover. */
	private void drawLeftButtons(Graphics2D graphics, int first, int pageX, int pageY, int mouseX, int mouseY)
	{
		if (first == 0)
		{
			return;
		}
		Rectangle arrow = leftArrowAt(pageX, pageY);
		graphics.drawImage(arrowLeft, arrow.x, arrow.y, null);
		Rectangle ribbon = ribbonAt(pageX, pageY);
		drawBookmark(graphics, ribbon.x, ribbon.y, first, ribbon.contains(mouseX, mouseY));
	}

	/** A spread's right page buttons, the page's top left given: the close button, and the next arrow if any. */
	private void drawRightButtons(Graphics2D graphics, int first, int pageCount, int pageX, int pageY, int mouseX,
		int mouseY)
	{
		Rectangle cross = closeAt(pageX, pageY);
		graphics.drawImage(cross.contains(mouseX, mouseY) ? closeHovered : close, cross.x, cross.y, null);
		if (first + 2 < pageCount)
		{
			Rectangle arrow = rightArrowAt(pageX, pageY);
			graphics.drawImage(arrowRight, arrow.x, arrow.y, null);
		}
	}

	private Rectangle closeAt(int pageX, int pageY)
	{
		return new Rectangle(pageX + PAGE_WIDTH - CORNER_INSET - close.getWidth(), pageY + CORNER_INSET,
			close.getWidth(), close.getHeight());
	}

	private static Rectangle ribbonAt(int pageX, int pageY)
	{
		return new Rectangle(pageX + RIBBON_LEFT, pageY - 1, RIBBON_WIDTH, RIBBON_HEIGHT);
	}

	private Rectangle leftArrowAt(int pageX, int pageY)
	{
		return new Rectangle(pageX + CORNER_INSET, pageY + PAGE_HEIGHT - CORNER_INSET - arrowLeft.getHeight(),
			arrowLeft.getWidth(), arrowLeft.getHeight());
	}

	private Rectangle rightArrowAt(int pageX, int pageY)
	{
		return new Rectangle(pageX + PAGE_WIDTH - CORNER_INSET - arrowRight.getWidth(),
			pageY + PAGE_HEIGHT - CORNER_INSET - arrowLeft.getHeight(), arrowRight.getWidth(), arrowRight.getHeight());
	}

	/** The turning leaf: its right side the page given lying on the right, its left side the one lying on the left. */
	private void drawLeaf(Graphics2D graphics, List<BookPage> showing, int rightPage, int leftPage, double angle, int x,
		int y)
	{
		boolean facingRight = Math.cos(angle) >= 0;
		BufferedImage face = leafSide(showing, facingRight ? rightPage : leftPage, facingRight);
		Rectangle under = new Rectangle(x + (facingRight ? RIGHT_PAGE_LEFT : LEFT_PAGE_LEFT), y + PAGE_TOP, PAGE_WIDTH,
			PAGE_HEIGHT);
		drawSwing(graphics, face, angle, x + SPINE_MIDDLE, LEAF_GAP, y + PAGE_TOP - 1, 1, TURN_LIFT, under,
			LEAF_SHADOW_WIDTH);
	}

	/**
	 * Opening or closing: the right half of the open book, slid towards the middle the more closed it is, with the
	 * cover swinging over the spine (0 closed, pi open), its inside the open book's left half.
	 */
	private void drawCover(Graphics2D graphics, List<BookPage> showing, int first, double angle, int x, int y)
	{
		x -= (int) Math.round(COVER_WIDTH / 2.0 * (1 + Math.cos(angle)) / 2);
		int half = (int) SPINE_MIDDLE;
		graphics.drawImage(frame, x + half, y, x + BOOK_WIDTH, y + BOOK_HEIGHT, half, 0, BOOK_WIDTH, BOOK_HEIGHT, null);
		drawPage(graphics, showing, first + 1, x + RIGHT_PAGE_LEFT, y + PAGE_TOP);
		drawRightButtons(graphics, first, showing.size(), x + RIGHT_PAGE_LEFT, y + PAGE_TOP, NO_HOVER, 0);
		boolean facingRight = Math.cos(angle) >= 0;
		Rectangle under = new Rectangle(x + half, y, COVER_WIDTH, BOOK_HEIGHT);
		drawSwing(graphics, facingRight ? cover : coverInside(showing, first), angle, x + SPINE_MIDDLE, 0, y, 0,
			COVER_LIFT, under, COVER_SHADOW_WIDTH);
	}

	/** The inside of the cover: the open book's left half, with the left page and its buttons. */
	private BufferedImage coverInside(List<BookPage> showing, int first)
	{
		if (coverInside == null)
		{
			coverInside = new BufferedImage(COVER_WIDTH, BOOK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		}
		Graphics2D g = coverInside.createGraphics();
		g.setComposite(AlphaComposite.Src);
		g.drawImage(frame, 0, 0, COVER_WIDTH, BOOK_HEIGHT, 0, 0, COVER_WIDTH, BOOK_HEIGHT, null);
		g.setComposite(AlphaComposite.SrcOver);
		drawPage(g, showing, first, LEFT_PAGE_LEFT, PAGE_TOP);
		drawLeftButtons(g, first, LEFT_PAGE_LEFT, PAGE_TOP, NO_HOVER, 0);
		g.dispose();
		return coverInside;
	}

	/**
	 * Something swinging over the spine, a leaf or the cover: the side facing up, whose inner edge sits gap px from the
	 * spine, at the given angle (0 lying right, pi lying left), its top at top. Drawn a pixel column at a time, each
	 * column taller the further it stands off the book, up to the given share taller at its edge when upright, as if
	 * lifting towards the viewer, then shaded below its first skipped rows. It casts a shadow this wide beyond its
	 * edge, kept to the area under it.
	 */
	private static void drawSwing(Graphics2D graphics, BufferedImage face, double angle, double spine, double gap,
		double top, int skipped, double turnLift, Rectangle under, int shadowWidth)
	{
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		boolean facingRight = cos >= 0;
		int width = face.getWidth();
		int faceHeight = face.getHeight();
		double middleY = top + faceHeight / 2.0;
		double reach = gap + width;
		double lift = turnLift * sin / reach;
		double hingeScale = 1 / (1 - lift * gap);
		double edgeScale = 1 / (1 - lift * reach);
		double hinge = spine + gap * cos * hingeScale;
		double edge = spine + reach * cos * edgeScale;
		float standing = (float) (1 - Math.abs(cos));

		Paint paint = graphics.getPaint();
		Shape clip = graphics.getClip();
		Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);

		// The shadow, beyond the edge, fading away from it.
		double shadowEnd = edge + (facingRight ? 1 : -1) * shadowWidth * sin;
		graphics.clip(under);
		graphics.setPaint(new GradientPaint((float) edge, 0, new Color(0, 0, 0, LEAF_SHADOW * (float) sin),
			(float) shadowEnd, 0, new Color(0, 0, 0, 0)));
		int shadowLeft = (int) Math.floor(Math.min(edge, shadowEnd));
		graphics.fillRect(shadowLeft, under.y, (int) Math.ceil(Math.max(edge, shadowEnd)) - shadowLeft, under.height);
		graphics.setClip(clip);

		// Each pixel column: how far along the leaf it shows, from where it sits off the spine.
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int start = (int) Math.floor(Math.min(hinge, edge));
		int end = (int) Math.ceil(Math.max(hinge, edge));
		for (int column = start; column < end; column++)
		{
			double offset = column + 0.5 - spine;
			double along = offset / (cos + lift * offset);
			if (!(along >= gap && along < reach))
			{
				continue;
			}
			int source = Math.min(width - 1, (int) (along - gap));
			if (!facingRight)
			{
				source = width - 1 - source;
			}
			double height = faceHeight / (1 - lift * along);
			int columnTop = (int) Math.round(middleY - height / 2);
			int columnBottom = (int) Math.round(middleY + height / 2);
			graphics.drawImage(face, column, columnTop, column + 1, columnBottom, source, 0, source + 1, faceHeight,
				null);
		}
		if (interpolation != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		}

		// Darker the more upright it stands, and darker still by the fold.
		Path2D.Double outline = new Path2D.Double();
		outline.moveTo(hinge, middleY - (faceHeight / 2.0 - skipped) * hingeScale);
		outline.lineTo(edge, middleY - (faceHeight / 2.0 - skipped) * edgeScale);
		outline.lineTo(edge, middleY + faceHeight / 2.0 * edgeScale);
		outline.lineTo(hinge, middleY + faceHeight / 2.0 * hingeScale);
		outline.closePath();
		float shade = LEAF_SHADE * standing;
		graphics.setPaint(new GradientPaint((float) hinge, 0, new Color(0, 0, 0, shade + FOLD_SHADE * (float) sin),
			(float) edge, 0, new Color(0, 0, 0, shade)));
		graphics.fill(outline);
		graphics.setPaint(paint);
	}

	/**
	 * One side of the turning leaf: the page's parchment from the frame, a row down, with the page and its buttons
	 * drawn on it.
	 */
	private BufferedImage leafSide(List<BookPage> showing, int page, boolean right)
	{
		BufferedImage side = right ? leafRight : leafLeft;
		if (side == null)
		{
			side = new BufferedImage(PAGE_WIDTH, LEAF_HEIGHT, BufferedImage.TYPE_INT_ARGB);
			if (right)
			{
				leafRight = side;
			}
			else
			{
				leafLeft = side;
			}
		}
		Graphics2D g = side.createGraphics();
		g.setComposite(AlphaComposite.Clear);
		g.fillRect(0, 0, PAGE_WIDTH, LEAF_HEIGHT);
		g.setComposite(AlphaComposite.SrcOver);
		int pageLeft = right ? RIGHT_PAGE_LEFT : LEFT_PAGE_LEFT;
		g.drawImage(frame, 0, 1, PAGE_WIDTH, LEAF_HEIGHT, pageLeft, PAGE_TOP, pageLeft + PAGE_WIDTH, PAGE_BOTTOM, null);
		BufferedImage content = pageImage(showing, page);
		if (content != null)
		{
			g.drawImage(content, 0, 1 + TITLE_INSET, null);
		}
		if (right)
		{
			drawRightButtons(g, page - 1, showing.size(), 0, 1, NO_HOVER, 0);
		}
		else
		{
			drawLeftButtons(g, page, 0, 1, NO_HOVER, 0);
		}
		g.dispose();
		return side;
	}

	/**
	 * Opens or closes the book, swinging the cover if animated: from where it is if it was already swinging the other
	 * way.
	 */
	private void setOpen(boolean value)
	{
		if (open == value)
		{
			return;
		}
		open = value;
		turnFrom = -1;
		if (!config.animatedBook())
		{
			coverStart = 0;
			return;
		}
		long now = System.currentTimeMillis();
		long swung = now - coverStart;
		coverStart = swung < COVER_MILLIS ? now - (COVER_MILLIS - swung) : now;
		coverOpening = value;
	}

	/** Starts turning from one spread to another, which then shows. */
	private void turn(int from, int to)
	{
		if (from == to)
		{
			return;
		}
		turnStart = System.currentTimeMillis();
		turnFrom = config.animatedBook() ? from : -1;
		spread = to;
	}

	/** The bookmark ribbon hanging from the top of a spread's left page, red when that spread is the one marked. */
	private void drawBookmark(Graphics2D graphics, int x, int y, int first, boolean hovered)
	{
		int marked = bookmarkedSpread();
		boolean set = marked == first;
		graphics.setColor(set ? (hovered ? RIBBON_SET_HOVERED : RIBBON_SET)
			: hovered ? RIBBON_HOVERED : marked >= 0 ? RIBBON_ELSEWHERE : RIBBON_UNSET);
		int middle = x + RIBBON_WIDTH / 2;
		graphics.fillPolygon(new int[]{x, x + RIBBON_WIDTH, x + RIBBON_WIDTH, middle + 1, middle, x},
			new int[]{y, y, y + RIBBON_HEIGHT, y + RIBBON_HEIGHT - RIBBON_NOTCH, y + RIBBON_HEIGHT - RIBBON_NOTCH,
				y + RIBBON_HEIGHT}, 6);
	}

	/** Forgets what was read from the old RuneLite profile's config, for when the user switches profiles. */
	void profileChanged()
	{
		seen = null;
		bookmark = null;
	}

	/** Whether the book has ever been opened in this RuneLite profile, read from the config the first time. */
	private boolean bookSeen()
	{
		Boolean known = seen;
		if (known == null)
		{
			known = Boolean.TRUE.equals(configManager.getConfiguration(TrawlingPlusConfig.GROUP, SEEN_KEY, Boolean.class));
			seen = known;
		}
		return known;
	}

	/** The bookmarked spread's left page, or -1 for none, read from the config the first time it is needed. */
	private int bookmarkedSpread()
	{
		Integer marked = bookmark;
		if (marked == null)
		{
			Integer saved = configManager.getConfiguration(TrawlingPlusConfig.GROUP, BOOKMARK_KEY, Integer.class);
			marked = saved != null && saved >= 0 ? saved / 2 * 2 : -1;
			bookmark = marked;
		}
		return marked;
	}

	/** The left page of the last spread for this many pages. */
	private static int lastSpread(int pageCount)
	{
		return Math.max(0, (pageCount - 1) / 2 * 2);
	}

	/** Shows the tooltip of the line on a page under the mouse, given relative to the page's top left. */
	private void showTip(int page, int x, int y)
	{
		if (page >= pageTips.size() || pageTips.get(page) == null)
		{
			return;
		}
		for (Map.Entry<Rectangle, String> tip : pageTips.get(page).entrySet())
		{
			if (tip.getKey().contains(x, y))
			{
				tooltipManager.add(new Tooltip(tip.getValue()));
				return;
			}
		}
	}

	/** A page, given its top left, its image starting under the title inset. */
	private void drawPage(Graphics2D graphics, List<BookPage> showing, int page, int pageX, int pageY)
	{
		BufferedImage image = pageImage(showing, page);
		if (image != null)
		{
			graphics.drawImage(image, pageX, pageY + TITLE_INSET, null);
		}
	}

	/** A page's image, drawn again only when the page has changed; null past the last page. */
	private BufferedImage pageImage(List<BookPage> showing, int page)
	{
		if (page >= showing.size())
		{
			return null;
		}
		while (pageImages.size() <= page)
		{
			pageImages.add(null);
			pageTips.add(null);
			pagesDrawn.add(null);
		}
		BookPage content = showing.get(page);
		if (pageImages.get(page) == null || !content.equals(pagesDrawn.get(page)))
		{
			Map<Rectangle, String> tips = new HashMap<>();
			pageImages.set(page, buildPage(content, page + 1, tips));
			pageTips.set(page, tips);
			pagesDrawn.set(page, content);
		}
		return pageImages.get(page);
	}

	/**
	 * One page: its title in the medium quill, then its lines in the small quill (text wrapped to the page, left
	 * aligned or centred, centred in faded ink for a heading, a row with its name on the left and its value against
	 * the right edge, or a blank line), and its number at the foot. Each line with a tooltip is put in tips, by where
	 * it was drawn.
	 */
	private BufferedImage buildPage(BookPage content, int number, Map<Rectangle, String> tips)
	{
		// The page's full width, side margins included, which a seal may hang over, and from under the title inset to
		// the foot; drawn to with the text's left edge at 0.
		int width = PAGE_WIDTH - 2 * TEXT_INSET;
		int height = PAGE_HEIGHT - TITLE_INSET;
		BufferedImage image = new BufferedImage(PAGE_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.translate(TEXT_INSET, 0);
		String title = content.getTitle();
		QuillFont heading = titleFont.width(title) <= width ? titleFont : bodyFont;
		heading.draw(g, title, (width - heading.width(title)) / 2, 0, INK);

		int lineHeight = bodyFont.height() + 2;
		// Half a blank line between the title and the first line, and for each blank line.
		int gap = lineHeight / 2;
		int line = heading.height() + 6 + gap;
		int starX = 0;
		for (BookPage.Line text : content.getLines())
		{
			if (text.getStars() >= 0)
			{
				starX = Math.max(starX, bodyFont.width(text.getLeft()) + STAR_GAP);
			}
		}
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
					if (text.getTooltip() != null)
					{
						tips.put(new Rectangle(x, line, bodyFont.width(part), lineHeight), text.getTooltip());
					}
					line += lineHeight;
				}
			}
			else
			{
				bodyFont.draw(g, text.getLeft(), 0, line, INK);
				bodyFont.draw(g, text.getRight(), width - bodyFont.width(text.getRight()), line, INK);
				drawStars(g, text.getStars(), starX, line + (bodyFont.height() - STAR.length) / 2);
				if (text.getTooltip() != null)
				{
					// The whole row, name to value.
					tips.put(new Rectangle(0, line, width, lineHeight), text.getTooltip());
				}
				line += lineHeight;
			}
		}

		String page = String.valueOf(number);
		// The image runs to the foot of the page so a seal can hang below the number; the number keeps its inset.
		int numberTop = height - NUMBER_INSET - bodyFont.height();
		bodyFont.draw(g, page, (width - bodyFont.width(page)) / 2, numberTop, FADED_INK);

		String signature = content.getSignature();
		if (signature != null)
		{
			// The name centred above the page number, with the seal off to its lower left.
			int nameX = (width - bodyFont.width(signature)) / 2;
			// As high as the middle of a seal standing on the line above the page number.
			int sealHeight = seal == null ? bodyFont.height() : seal.getHeight();
			int nameY = numberTop - lineHeight - sealHeight + (sealHeight - bodyFont.height()) / 2;
			if (seal != null)
			{
				// Kept on the page for long names, and never so low its ribbon tails run off the foot of the page.
				int sealX = Math.max(SEAL_PAGE_EDGE - TEXT_INSET, nameX - SEAL_LEFT_OF_NAME);
				int sealY = Math.min(nameY + SEAL_BELOW_NAME, height - 1 - SEAL_TAIL_LENGTH - seal.getHeight() / 2);
				int middleX = sealX + seal.getWidth() / 2;
				int middleY = sealY + seal.getHeight() / 2;
				drawSealTail(g, middleX - SEAL_TAIL_WIDTH - 2, middleY, -SEAL_TAIL_LEAN);
				drawSealTail(g, middleX, middleY, SEAL_TAIL_LEAN);
				g.drawImage(seal, sealX, sealY, null);
			}
			bodyFont.draw(g, signature, nameX, nameY, INK);
		}
		g.dispose();
		return image;
	}

	/**
	 * A row's stars for this many milestones: each star in turn goes bronze, silver then gold, and a star not yet
	 * reached isn't drawn.
	 */
	private static void drawStars(Graphics2D g, int reached, int x, int y)
	{
		for (int star = 0; star < STARS; star++)
		{
			int metal = Math.min(reached - star * STAR_METALS.length, STAR_METALS.length) - 1;
			if (metal < 0)
			{
				return;
			}
			int left = x + star * STAR_SPACING;
			for (int row = 0; row < STAR.length; row++)
			{
				for (int column = 0; column < STAR[row].length(); column++)
				{
					if (STAR[row].charAt(column) == '#')
					{
						g.setColor(STAR_METALS[metal][starEdge(row, column) ? 1 : 0]);
						g.fillRect(left + column, y + row, 1, 1);
					}
				}
			}
		}
	}

	/** Whether a star pixel is on its edge, beside a blank pixel or the mask's border. */
	private static boolean starEdge(int row, int column)
	{
		return !starPixel(row - 1, column) || !starPixel(row + 1, column) || !starPixel(row, column - 1)
			|| !starPixel(row, column + 1);
	}

	private static boolean starPixel(int row, int column)
	{
		return row >= 0 && row < STAR.length && column >= 0 && column < STAR[row].length()
			&& STAR[row].charAt(column) == '#';
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
		// (often the skill icon) leaves a fish that doesn't match its book.
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
		BufferedImage fish = shrunk(trimmed(fishOnly), FISH_SIZE);
		b.drawImage(fish, (int) Math.round(BUTTON_COVER_X - fish.getWidth() / 2.0) + FISH_NUDGE_X,
			(int) Math.round(BUTTON_COVER_Y - fish.getHeight() / 2.0) + FISH_NUDGE_Y, null);
		b.dispose();
		// Cut down to their visible pixels, so they sit by what shows rather than by their transparent padding.
		arrowLeft = trimmed(left);
		arrowRight = trimmed(right);
		Rectangle crossShows = visible(cross).union(visible(crossHovered));
		close = trim(cross, crossShows);
		closeHovered = trim(crossHovered, crossShows);
		icon = button;
		iconHovered = brighter(button, HOVER_BRIGHTNESS);
		BufferedImage waxSeal = sprite(SEAL);
		// The Fishing icon pressed into it is the game's own, hook and all, whatever a resource pack does.
		seal = waxSeal == null ? null
			: imprinted(saturated(trimmed(waxSeal), SEAL_SATURATION), trimmed(buttonFish));
		cover = buildCover(built, titles);
		// Pages already drawn are drawn again, with the new sprites.
		pageImages.clear();
		pageTips.clear();
		pagesDrawn.clear();
		bodyFont = body;
		titleFont = titles;
		frame = built;
		return true;
	}

	/**
	 * The front cover, in the leather colours of the frame's right edge: a darker hinge along the spine, a beveled
	 * edge, a tooled panel, the frame's own corner pieces at the fore edge and the title in gold leaf.
	 */
	private BufferedImage buildCover(BufferedImage book, QuillFont titles)
	{
		int w = COVER_WIDTH;
		int h = BOOK_HEIGHT;
		// Read across the frame's right edge at its middle: its leather, the leather's highlight, then its outline.
		int middle = BOOK_HEIGHT / 2;
		Color leather = new Color(book.getRGB(BOOK_WIDTH - 5, middle));
		Color light = new Color(book.getRGB(BOOK_WIDTH - 3, middle));
		Color outline = new Color(book.getRGB(BOOK_WIDTH - 2, middle));
		Color dark = new Color((int) (leather.getRed() * COVER_DARKER), (int) (leather.getGreen() * COVER_DARKER),
			(int) (leather.getBlue() * COVER_DARKER));
		BufferedImage built = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = built.createGraphics();
		g.setColor(leather);
		g.fillRect(0, 0, w, h);
		g.setColor(dark);
		g.fillRect(0, 0, COVER_HINGE, h);
		g.drawLine(0, h - 2, w - 2, h - 2);
		g.setColor(light);
		g.drawLine(COVER_HINGE, 1, COVER_HINGE, h - 2);
		g.drawLine(0, 1, w - 2, 1);
		g.drawLine(w - 2, 1, w - 2, h - 2);
		g.setColor(outline);
		g.drawRect(0, 0, w - 1, h - 1);

		// A groove: a dark line with a light one just below and right of it.
		int panelLeft = COVER_HINGE + COVER_PANEL_INSET;
		int panelWidth = w - panelLeft - COVER_PANEL_INSET - 1;
		int panelHeight = h - 2 * COVER_PANEL_INSET - 1;
		g.setColor(dark);
		g.drawRect(panelLeft, COVER_PANEL_INSET, panelWidth, panelHeight);
		g.setColor(light);
		g.drawRect(panelLeft + 1, COVER_PANEL_INSET + 1, panelWidth, panelHeight);

		// The frame's green corner pieces, where the cover's corners lie on the open book.
		for (int corner : new int[]{0, h - TILE})
		{
			for (int fy = corner; fy < corner + TILE; fy++)
			{
				for (int fx = BOOK_WIDTH - TILE; fx < BOOK_WIDTH; fx++)
				{
					int pixel = book.getRGB(fx, fy);
					int r = pixel >> 16 & 0xff;
					int gr = pixel >> 8 & 0xff;
					int b = pixel & 0xff;
					if ((pixel >>> 24) != 0 && gr > r + 8 && gr > b)
					{
						built.setRGB(fx - (BOOK_WIDTH - w), fy, pixel);
					}
				}
			}
		}

		int middleX = (w + COVER_HINGE) / 2;
		int titleX = middleX - titles.width(COVER_TITLE) / 2;
		titles.draw(g, COVER_TITLE, titleX + 1, COVER_TITLE_Y + 1, outline);
		titles.draw(g, COVER_TITLE, titleX, COVER_TITLE_Y, GOLD_LEAF);
		g.dispose();
		return built;
	}

	/** One ribbon tail from behind the seal: its top left corner, leaning this far sideways by its notched end. */
	private static void drawSealTail(Graphics2D g, int x, int y, int lean)
	{
		int bottom = y + SEAL_TAIL_LENGTH;
		int[] xs = {x, x + SEAL_TAIL_WIDTH, x + SEAL_TAIL_WIDTH + lean, x + lean + SEAL_TAIL_WIDTH / 2, x + lean};
		int[] ys = {y, y, bottom, bottom - SEAL_TAIL_NOTCH, bottom};
		g.setColor(SEAL_TAIL);
		g.fillPolygon(xs, ys, xs.length);
		g.setColor(SEAL_TAIL_EDGE);
		g.drawPolygon(xs, ys, xs.length);
	}

	/**
	 * The seal with the fish pressed into its middle, drawn into it: shrunk, then each of its pixels drawn in the colour
	 * of the seal beneath, darker or lighter by how dark or light the icon is there, so it reads as a stamped impression.
	 */
	private static BufferedImage imprinted(BufferedImage seal, BufferedImage fish)
	{
		BufferedImage icon = shrunk(fish, SEAL_FISH_SIZE);
		int left = (int) Math.round(SEAL_RING_X - icon.getWidth() / 2.0);
		int top = (int) Math.round(SEAL_RING_Y - icon.getHeight() / 2.0);
		Color rim = sealColour(seal, true);
		Color shadow = sealColour(seal, false);
		float[] hsb = new float[3];
		for (int y = 0; y < icon.getHeight(); y++)
		{
			for (int x = 0; x < icon.getWidth(); x++)
			{
				int pixel = icon.getRGB(x, y);
				float alpha = (pixel >>> 24) / 255f;
				if (alpha == 0 || !inside(seal, left + x, top + y))
				{
					continue;
				}
				Color.RGBtoHSB(pixel >> 16 & 0xff, pixel >> 8 & 0xff, pixel & 0xff, hsb);
				float shade = SEAL_FISH_SHADE_DARK + (SEAL_FISH_SHADE_LIGHT - SEAL_FISH_SHADE_DARK) * hsb[2];
				int under = seal.getRGB(left + x, top + y);
				int r = blend(under >> 16 & 0xff, shade, alpha);
				int gr = blend(under >> 8 & 0xff, shade, alpha);
				int b = blend(under & 0xff, shade, alpha);
				seal.setRGB(left + x, top + y, under & 0xff000000 | r << 16 | gr << 8 | b);
			}
		}
		for (int y = 0; y < icon.getHeight(); y++)
		{
			for (int x = 0; x < icon.getWidth(); x++)
			{
				if (!fishAt(icon, x, y))
				{
					continue;
				}
				if (!fishAt(icon, x + 1, y + 1))
				{
					edge(seal, left + x + 1, top + y + 1, rim);
				}
				if (!fishAt(icon, x - 1, y - 1))
				{
					edge(seal, left + x, top + y, shadow);
				}
			}
		}
		return seal;
	}

	private static boolean inside(BufferedImage image, int x, int y)
	{
		return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight();
	}

	/** The seal's brightest or darkest colour, among those making up a fair share of it. */
	private static Color sealColour(BufferedImage seal, boolean brightest)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		int total = 0;
		for (int y = 0; y < seal.getHeight(); y++)
		{
			for (int x = 0; x < seal.getWidth(); x++)
			{
				int pixel = seal.getRGB(x, y);
				if ((pixel >>> 24) == 0xff)
				{
					counts.merge(pixel & 0xffffff, 1, Integer::sum);
					total++;
				}
			}
		}
		int best = -1;
		float bestBrightness = brightest ? -1 : 2;
		float[] hsb = new float[3];
		for (Map.Entry<Integer, Integer> colour : counts.entrySet())
		{
			if (colour.getValue() < total * SEAL_COLOUR_SHARE)
			{
				continue;
			}
			int rgb = colour.getKey();
			Color.RGBtoHSB(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, hsb);
			if (brightest ? hsb[2] > bestBrightness : hsb[2] < bestBrightness)
			{
				bestBrightness = hsb[2];
				best = rgb;
			}
		}
		return new Color(best < 0 ? 0 : best);
	}

	/** Whether the shrunk icon is solid enough at a pixel to count as the fish; outside it never is. */
	private static boolean fishAt(BufferedImage icon, int x, int y)
	{
		return inside(icon, x, y) && (icon.getRGB(x, y) >>> 24) >= SEAL_FISH_SOLID;
	}

	/** Sets a pixel of the seal to an emboss edge's colour, if there is wax there. */
	private static void edge(BufferedImage seal, int x, int y, Color colour)
	{
		if (inside(seal, x, y) && (seal.getRGB(x, y) >>> 24) != 0)
		{
			seal.setRGB(x, y, seal.getRGB(x, y) & 0xff000000 | colour.getRGB() & 0xffffff);
		}
	}

	/** A colour channel shaded by a factor, mixed over itself by alpha. */
	private static int blend(int channel, float shade, float alpha)
	{
		float shaded = Math.min(255, channel * shade);
		return Math.round(channel + (shaded - channel) * alpha);
	}

	/** A copy with every pixel's saturation scaled, capped at full, keeping its hue, brightness and alpha. */
	private static BufferedImage saturated(BufferedImage image, float scale)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		float[] hsb = new float[3];
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				Color.RGBtoHSB(argb >> 16 & 0xff, argb >> 8 & 0xff, argb & 0xff, hsb);
				int rgb = Color.HSBtoRGB(hsb[0], Math.min(1f, hsb[1] * scale), hsb[2]);
				out.setRGB(x, y, argb & 0xff000000 | rgb & 0xffffff);
			}
		}
		return out;
	}

	/** Shrunk to the given size on its longer side, averaging pixels rather than dropping them. */
	private static BufferedImage shrunk(BufferedImage image, int size)
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

	/** Cut down to its visible pixels. */
	private static BufferedImage trimmed(BufferedImage image)
	{
		return trim(image, visible(image));
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
	 * line up as the game lays them out. A resource pack's replacement is used where there is one, if the book is set to
	 * follow resource packs.
	 */
	private BufferedImage sprite(int id)
	{
		return sprite(id, config.bookResourcePacks());
	}

	/** Builds the book again from its sprites, for when resource packs are switched on or off for it. */
	void rebuildSprites()
	{
		frame = null;
		spritesTriedTick = -1;
		overridesUsed.clear();
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
		forgetBookBounds();
	}

	/** Forgets where the open book's parts were drawn, leaving the button's. */
	private void forgetBookBounds()
	{
		bookBounds = null;
		closeBounds = null;
		leftBounds = null;
		rightBounds = null;
		bookmarkBounds = null;
	}

	private static boolean inside(Rectangle bounds, MouseEvent event)
	{
		return bounds != null && bounds.contains(event.getPoint());
	}

	/** Whether the book's button shows under a point on the canvas, not hidden behind the open book. */
	boolean overButton(int x, int y)
	{
		Rectangle button = buttonBounds;
		Rectangle book = bookBounds;
		return button != null && button.contains(x, y) && !(open && book != null && book.contains(x, y));
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
			// The open book covers the button wherever they overlap.
			if (inside(buttonBounds, event) && !(open && inside(bookBounds, event)))
			{
				setOpen(!open);
				if (open && !bookSeen())
				{
					seen = true;
					configManager.setConfiguration(TrawlingPlusConfig.GROUP, SEEN_KEY, true);
				}
				if (open && !openedThisSession)
				{
					openedThisSession = true;
					spread = Math.max(0, bookmarkedSpread());
				}
				event.consume();
			}
			else if (open && inside(bookBounds, event))
			{
				if (inside(closeBounds, event))
				{
					setOpen(false);
				}
				else if (inside(leftBounds, event))
				{
					int from = Math.min(spread, lastSpread(pages.size()));
					turn(from, Math.max(0, from - 2));
				}
				else if (inside(rightBounds, event))
				{
					int from = Math.min(spread, lastSpread(pages.size()));
					turn(from, Math.min(from + 2, lastSpread(pages.size())));
				}
				else if (inside(bookmarkBounds, event))
				{
					toggleBookmark();
				}
				event.consume();
			}
			else if (open && overWorld)
			{
				setOpen(false);
			}
			return event;
		}
	};

	/** Bookmarks the spread showing, or takes the bookmark off it if it is the one marked. */
	private void toggleBookmark()
	{
		int first = Math.min(spread, lastSpread(pages.size()));
		int marked = bookmarkedSpread() == first ? -1 : first;
		bookmark = marked;
		if (marked < 0)
		{
			configManager.unsetConfiguration(TrawlingPlusConfig.GROUP, BOOKMARK_KEY);
		}
		else
		{
			configManager.setConfiguration(TrawlingPlusConfig.GROUP, BOOKMARK_KEY, marked);
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
			setOpen(false);
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}
}
