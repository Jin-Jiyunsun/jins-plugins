package com.trawlingplus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.SpritePixels;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class TrawlingPlusOverlay extends Overlay
{
	// Stops are drawn as a square this many tiles across, roughly the size of a shoal.
	private static final int STOP_SIZE = 3;
	// A round stop is the circle through the middle of each side of that square: a smooth curve through
	// STOP_CURVE_POINTS points of it, into the same polygon and path each time.
	private static final int STOP_CURVE_POINTS = 8;
	private final Polygon stopCircle = new Polygon();
	private final Path2D.Double stopCurve = new Path2D.Double();
	// Which of a circle's points each placed corner is, so a gap where some were off the map is known.
	private final int[] circleSteps = new int[AREA_POINTS];

	// What an empty deck tile holds, shared rather than made afresh for each one while the deck is looked through.
	private static final GameObject[] NO_OBJECTS = new GameObject[0];

	// How far a shoal can be fished from, in tiles along each axis. It is the same for every kind of
	// shoal, but it is not measured from the middle of the boat: it is measured from a point towards the
	// bow, so a boat pointing its bow at a shoal can fish it from further away than one pointing its
	// stern. Measured on a sloop against bluefin, halibut and glistening shoals, parked still. The edge
	// shifts by about half a tile with the way the boat faces, so this sits between where fishing always
	// works and where it sometimes still does.
	private static final double FISHABLE_REACH = 8.7;

	// That point on the boat, in local units ahead of its middle towards the bow: two and a quarter
	// tiles. The bow is the low end of the boat's own view, the helm the high end.
	private static final int FISHING_POINT_AHEAD = Perspective.LOCAL_TILE_SIZE * 9 / 4;

	// The dot marking that point on the boat, in pixels across.
	private static final int FISHING_POINT_SIZE = 5;

	// How long the dot takes to fade fully in or out as the fishable area comes and goes, in milliseconds.
	private static final double FISHING_POINT_FADE_MILLIS = 500;

	// The area the game itself uses is a square lined up with the map: a shoal can be fished while that
	// point on the boat is within the reach of it along both the x and the y axis. A circle of the same
	// reach sits just inside it, for anyone who would rather have the rounder guide.
	//
	// Either shape is drawn through points spaced around it rather than only a few corners, so one
	// running off the loaded map still draws the part that is on it, and it follows the height of the
	// water it crosses. Where each sits never changes, so both are worked out once here, as fractions of
	// the reach: the square's south side west to east, then east, north and west the same way round, and
	// the circle anticlockwise from the east.
	private static final int AREA_POINTS_PER_SIDE = 16;
	private static final int AREA_POINTS = AREA_POINTS_PER_SIDE * 4;
	private static final double[] SQUARE_X = new double[AREA_POINTS];
	private static final double[] SQUARE_Y = new double[AREA_POINTS];
	private static final double[] CIRCLE_X = new double[AREA_POINTS];
	private static final double[] CIRCLE_Y = new double[AREA_POINTS];

	static
	{
		for (int step = 0; step < AREA_POINTS_PER_SIDE; step++)
		{
			double along = -1 + 2.0 * step / AREA_POINTS_PER_SIDE;
			int south = step;
			int east = south + AREA_POINTS_PER_SIDE;
			int north = east + AREA_POINTS_PER_SIDE;
			int west = north + AREA_POINTS_PER_SIDE;
			SQUARE_X[south] = along;
			SQUARE_Y[south] = -1;
			SQUARE_X[east] = 1;
			SQUARE_Y[east] = along;
			SQUARE_X[north] = -along;
			SQUARE_Y[north] = 1;
			SQUARE_X[west] = -1;
			SQUARE_Y[west] = -along;
		}
		for (int step = 0; step < AREA_POINTS; step++)
		{
			double angle = 2 * Math.PI * step / AREA_POINTS;
			CIRCLE_X[step] = Math.cos(angle);
			CIRCLE_Y[step] = Math.sin(angle);
		}
	}

	// How far above the deck the display at the helm floats, in local units: about two tiles, which
	// clears the mast and the crew.
	private static final int HELM_TEXT_HEIGHT = 250;

	// Where the display goes at the bow and above the sails, read once per boat type from its deck: how far up the
	// deck the front of the hull is and how high the top of the tallest thing on it is, both in the deck's local
	// units, and which boat and deck they were read for. The display sits a little back from the tip,
	// and a little above the top.
	private static final int BOW_INSET = Perspective.LOCAL_TILE_SIZE;
	// How far towards the helm from the bow's spot the display sits over the sails, and how far from their top,
	// in tenths of a tile, by boat config, tuned in game: the skiff and the sloop. The raft has none, and puts the
	// display at its bow instead.
	private static final Map<Integer, int[]> SAILS_BY_BOAT = Map.of(2, new int[]{12, -23}, 3, new int[]{21, -27});

	private int anchorConfig = -1;
	private int anchorDeckView = -1;
	private int anchorTriedTick = -1;
	private int bowLocalY;
	private int sailTop;

	// A dark box behind the display at the helm, so its text reads against the water whatever its colour.
	private static final Color HELM_BACKGROUND = new Color(0, 0, 0, 150);
	private static final int HELM_PADDING = 3;

	// How long the display at the helm takes to fade fully in or out, in milliseconds.
	private static final double HELM_FADE_MILLIS = 500;

	// The gap between the depth and the tick that follows it once every net is set to that depth.
	private static final int TICK_GAP = 4;

	// The lines of the display at the helm. The depth holds the bottom line so the display keeps its
	// place on the boat however many of the others are showing.
	private static final int DEPTH_LINE = 0;
	private static final int TIME_LINE = 1;
	private static final int BAITED_LINE = 2;
	private static final int FISH_LINE = 3;
	private static final int HOLD_LINE = 4;
	// Not a line of text: the timer bar, under them all.
	private static final int BAR_LINE = 5;
	private static final int HELM_LINES = 6;

	// The timer bar: the game's own bar over a shoal, from the same two pictures the game draws it
	// with, so it looks the same whether they are the game's or RuneLite's high detail ones. Kept below the text,
	// this far under it.
	private static final int BAR_FRONT = SpriteID.HeadbarIce90.FRONT;
	private static final int BAR_BACK = SpriteID.HeadbarIce90.BACK;
	private static final int BAR_GAP = 2;
	// As wide as the text above it, so it matches the display whatever is showing, but never narrower than this,
	// in pixels: under a short line such as the depth alone, the display widens to it instead.
	private static final int BAR_MIN_WIDTH = 60;

	// The warning on top of the display while the hold is full, pulsing between two colours, from one to
	// the other and back in this long.
	private static final String HOLD_FULL = "Hold full";
	private static final Color HOLD_FULL_COLOUR = new Color(255, 70, 40);
	private static final Color HOLD_FULL_PULSE_COLOUR = new Color(255, 200, 60);
	private static final long HOLD_FULL_PULSE_MILLIS = 1000;

	// The stops' faint fill, as RuneLite draws a highlighted tile.
	private static final Color STOP_FILL = new Color(0, 0, 0, 50);

	// A direction arrow's length in tiles at 100% scaling, and its shape as fractions of its length:
	// half its width, and how far behind its middle the notch between its wings sits.
	private static final double ARROW_LENGTH = 1.5;
	private static final double ARROW_HALF_WIDTH = 0.4;
	private static final double ARROW_NOTCH = 0.2;

	// The same for the arrow marking where a shoal is and which way it's heading, which also leads a
	// route as it draws out to the next stop. The heading arrow is drawn last, so it sits on top of
	// everything else.
	private static final double SHOAL_ARROW_LENGTH = 2.4;
	private static final double SHOAL_ARROW_HALF_WIDTH = 1.0 / 2.4;
	private static final double SHOAL_ARROW_NOTCH = 0.45 / 2.4;

	// The fishable area, and the stroke it is drawn with, kept between frames: the shape is redrawn every
	// frame but never changes size, and the thickness only changes when a setting does.
	private final Polygon area = new Polygon();
	// A round area is a smooth curve through this many of its points, as round as the full count at a quarter of
	// the placing.
	private static final int AREA_CURVE_POINTS = 16;
	private final Path2D.Double areaCurve = new Path2D.Double();
	private Stroke areaStroke;
	private int areaThickness;
	// The same for the stops' outline, which every stop is drawn with.
	private Stroke stopStroke;
	private int stopThickness;
	// And the route line's, round ended, and flat ended for the pieces faded near the boat.
	private Stroke lineStroke;
	private Stroke fadedLineStroke;
	private int lineThickness;

	// Whole route leaves the route out when less than this much of it, in tiles, is inside the loaded map:
	// a scrap at the edge of the view says nothing and only costs drawing. Worked out once a tick, for the
	// route it was last worked out for.
	private static final double MIN_LOADED_ROUTE_TILES = 50;
	// A route passing within this many tiles of the boat is drawn however little of it is loaded: the loaded
	// map isn't always centred on the boat, so near its edge the route being sailed along can fall short.
	private static final double NEAR_ROUTE_TILES = 15;
	private int loadedTick = -1;
	// How many tiles the loaded map reaches beyond the scene, worked out once a frame rather than for every
	// point placed.
	private int frameBeyond;
	// This frame's arrow style, and for Facing camera, where the camera is in local units.
	private TrawlingPlusConfig.ArrowStyle arrowStyle = TrawlingPlusConfig.ArrowStyle.FACING;
	private double frameCameraX;
	private double frameCameraY;
	// The most and fewest pixels a tile may cover for a camera-facing arrow this frame, or -1 for no limit: no more
	// than the size where the camera is aimed, nor a tenth of the screen's height for an arrow, and no less than half
	// that size. Tuned in game.
	private static final double FACING_MIN_SCALE = 0.5;
	private static final double FACING_MAX_SCREEN = 0.1;
	private double facingLimit = -1;
	private double facingMinimum = -1;
	// The last size a tile could be measured at where the camera is aimed.
	private double facingReference = -1;
	private static final double FACING_MAX_SCALE = 1;
	// How far apart, in tiles, the places a camera-facing arrow is measured off are.
	private static final double FACING_SPAN = 2;
	// This frame's camera, unrounded, for finding which way a camera-facing arrow points without the screen's
	// whole pixels in the way: where it is in local units, its turn and tilt, and its zoom.
	private double cameraFpX;
	private double cameraFpY;
	private double cameraFpZ;
	private double yawSin;
	private double yawCos;
	private double pitchSin;
	private double pitchCos;
	private int cameraScale;
	// What screenWay and unroundedCanvas give back, kept here rather than in a new array for every arrow.
	private double wayX;
	private double wayY;
	private double canvasX;
	private double canvasY;

	// Whether the route line, direction arrows and stops are left out from under the boat this frame: Clear around
	// boat is on, the player is aboard, and the hull's outline has been read. Taken from where the boat is drawn
	// this frame rather than last tick, so the gap glides and turns with it.
	private boolean clearing;
	// The hull's own outline seen from above, read once per boat type from its model: corners in the deck's
	// local units, which boat type they are for, and this frame's corners in world tiles with the box round them.
	// How many corners it is cut down to: the fewest that fit every boat, tuned in game.
	private static final int FOOTPRINT_POINTS = 7;
	private int footprintConfig = -1;
	private int footprintTriedTick = -1;
	private int footprintDeckView = -1;
	private int footprintCount;
	private final double[] footLocalX = new double[FOOTPRINT_POINTS];
	private final double[] footLocalY = new double[FOOTPRINT_POINTS];
	private final double[] footX = new double[FOOTPRINT_POINTS];
	private final double[] footY = new double[FOOTPRINT_POINTS];
	private double footFromX;
	private double footFromY;
	private double footToX;
	private double footToY;
	// How far outside the hull's outline, in tiles, the route line, arrows and stops take to fade back in.
	private static final double CLEAR_FADE_TILES = 1.5;
	// The route line near the boat is faded in steps this long, in tiles, each drawn blending from one end's
	// opacity to the other's; a step with both ends at least LINE_FULLY_SHOWN is part of the line as usual.
	private static final double LINE_FADE_STEP_TILES = 0.25;
	private static final double LINE_FULLY_SHOWN = 0.995;
	// The outline stood up into a column CLEAR_HEIGHT_TILES tall, which keeps the hull clear without hiding the
	// line behind the sails: its outline on screen, from the hull's outline at the waterline and at the top, and
	// the boat's middle and the camera in local units for telling what is behind the boat. Rebuilt every frame
	// into the same polygon.
	private static final double CLEAR_HEIGHT_TILES = 1;
	// Boat types less tall than that, by their boat config, tuned in game: the skiff, and the raft, which is flat
	// enough that its outline on the water is all there is to it. There are only these three player boats.
	private static final Map<Integer, Double> CLEAR_HEIGHT_BY_BOAT = Map.of(1, 0.0, 2, 0.9);
	// This frame's boat's height, from the above.
	private double clearHeightTiles = CLEAR_HEIGHT_TILES;
	private boolean volume;
	private final Polygon silhouette = new Polygon();
	private final int[] ringX = new int[FOOTPRINT_POINTS * 2];
	private final int[] ringY = new int[FOOTPRINT_POINTS * 2];
	private final int[] ringOrder = new int[FOOTPRINT_POINTS * 2];
	private final int[] ringHull = new int[FOOTPRINT_POINTS * 4];
	private double centreLocalX;
	private double centreLocalY;
	private double cameraX;
	private double cameraY;
	private int volumeBaseX;
	private int volumeBaseY;
	// The fade at the outline's edge, in pixels, and the outline's box grown by it, beyond which nothing fades.
	private double silhouetteBand = 1;
	private final Rectangle fadeBounds = new Rectangle();
	// The middle of the hull's outline this frame, in world tiles.
	private double clearX;
	private double clearY;
	private ShoalRoute loadedRoute;
	private boolean loadedEnough;

	// How far faded in the fishable area and its dot are, and when that was last worked out.
	private double fishingPointFade;
	// Where the fishable area last was: the nearest shoal's world view and place, kept so it can fade out there.
	private WorldView areaView;
	private double areaX;
	private double areaY;
	private long lastFishingPointMillis = -1;

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;
	private final SpriteManager spriteManager;

	// The bar's two pictures this frame, and the replacements they were made from when a skin or resource pack has
	// swapped them, so each is only turned into an image once. How full it last was, kept while it fades out.
	private BufferedImage barFrontSprite;
	private BufferedImage barBackSprite;
	// The two pictures resized to the display's width, remade only when that width or the pictures change.
	private BufferedImage barFront;
	private BufferedImage barBack;
	private int barWidth = -1;
	private SpritePixels barFrontSource;
	private SpritePixels barBackSource;
	private double fadingBarFull;

	// How far faded in each line of the display at the helm is, by line.
	private final double[] helmFades = new double[HELM_LINES];
	// The hold's display: its title on a line of its own and its fish out of the slots they have under it, the gap
	// between it and the display at the helm below it, and between its two columns, in pixels. How many kinds of
	// fish get a line of their own, the ones that went in most recently, and what the line for the rest is called and
	// its colour. This frame's lines, top first, as the name on the left and the count on the right, in its colour,
	// in how many lines: the title, the slots, those kinds and the rest, and which of its places each is (the
	// title, the slots, one per kind, then the rest); how wide its columns are together, and its box as drawn this
	// frame; whether the hold is full; the order its fish are listed in; and how far faded in it is, and when that
	// was worked out.
	// Like the display at the helm, its box grows and shrinks rather than jumping, its lines slide to their new
	// places, and a fish arriving waits for room and then fades in: per place, how far faded in it is, how far up
	// from the bottom of the box its baseline is drawn, and whether it was laid out last frame; how wide its
	// columns and how tall its box are drawn; and the height it is growing from and to, and whether it is half way.
	private static final String HOLD_TITLE = "Fish in Hold";
	private static final String HOLD_TOTAL = "Total";
	private static final int HOLD_GAP = 4;
	private static final int HOLD_COLUMN_GAP = 12;
	private static final int HOLD_KINDS_SHOWN = 3;
	private static final String HOLD_OTHER = "Other";
	private static final Color HOLD_OTHER_COLOUR = new Color(0xc0c0c0);
	private static final int HOLD_OTHER_PLACE = CargoHold.SEA_FISH.length + 2;
	private static final int HOLD_PLACES = CargoHold.SEA_FISH.length + 3;
	private final String[] holdLeft = new String[HOLD_PLACES];
	private final String[] holdRight = new String[HOLD_PLACES];
	private final Color[] holdColour = new Color[HOLD_PLACES];
	private final int[] holdPlace = new int[HOLD_PLACES];
	private final int[] holdOrder = new int[CargoHold.SEA_FISH.length];
	private int holdLines;
	private int holdWide;
	// The lines are only written out again when what the hold holds or the font changes: how wide each count is, how
	// tall a line is and how far down its baseline sits, and the hold, its number of changes and the font they
	// were written out for.
	private final int[] holdRightWidth = new int[HOLD_PLACES];
	private int holdLineHeight;
	private int holdAscent;
	private CargoHold laidHold;
	private int laidChanges;
	private Font laidFont;
	private int holdBoxWidth;
	private int holdBoxHeight;
	private boolean holdFull;
	private double holdFade;
	private long lastHoldFadeMillis = -1;
	private final double[] holdLineFade = new double[HOLD_PLACES];
	private final double[] holdShownUp = new double[HOLD_PLACES];
	private final boolean[] holdWasLaid = new boolean[HOLD_PLACES];
	private final boolean[] holdLaid = new boolean[HOLD_PLACES];
	private double holdShownWide;
	private double holdShownHeight;
	private int holdResizeFrom;
	private int holdResizeTo;
	private boolean holdRoomReady = true;
	// Each line's working out for the frame, kept rather than made afresh every frame. Every entry read is written
	// first: the per-line ones for every line, the rest for the lines laid out.
	private final boolean[] helmWanted = new boolean[HELM_LINES];
	private final boolean[] helmWasUp = new boolean[HELM_LINES];
	private final double[] helmOpacity = new double[HELM_LINES];
	private final boolean[] helmLaid = new boolean[HELM_LINES];
	private final String[] helmText = new String[HELM_LINES];
	private final Color[] helmColour = new Color[HELM_LINES];
	private final double[] helmShowing = new double[HELM_LINES];
	private final int[] helmWidth = new int[HELM_LINES];
	// The display's background grows and shrinks to fit rather than jumping: how tall its text and bar are and how
	// wide it is drawn, catching up with the real sizes most of the way in about HELM_RESIZE_MILLIS. A line or the
	// bar arriving in a display already on screen waits until the background has grown at least half way to make
	// room for it, then fades in: the height it is growing from and to, and whether it is that far yet.
	private static final double HELM_RESIZE_MILLIS = 60;
	private double shownTextHeight;
	private double shownBarHeight;
	private double shownWide;
	private long lastResizeMillis = -1;
	private int resizeFrom;
	private int resizeTo;
	private boolean roomReady = true;
	private long lastHelmFadeMillis = -1;
	private ShoalDepth fadingDepth = ShoalDepth.UNKNOWN;

	@Inject
	TrawlingPlusOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config, SpriteManager spriteManager)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView top = client.getTopLevelWorldView();
		frameBeyond = top == null ? 0 : tilesBeyondScene(top);
		arrowStyle = config.arrowStyle();
		if (arrowStyle == TrawlingPlusConfig.ArrowStyle.FACING)
		{
			// Where the camera is, for finding the way straight across the screen at each arrow, and how big a tile
			// is where it is aimed, which no arrow may grow past or shrink far below.
			frameCameraX = client.getCameraX();
			frameCameraY = client.getCameraY();
			// Measured where the camera is aimed rather than at the boat, which the camera needn't be centred on.
			double focusX = top == null ? 0 : top.getBaseX()
				+ (client.getCameraFocalPointX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE;
			// The focal point's Y is its height; across the map it runs X and Z.
			double focusY = top == null ? 0 : top.getBaseY()
				+ (client.getCameraFocalPointZ() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE;
			double atBoat = top == null ? -1 : acrossScreen(top, focusX, focusY, toCanvas(top, focusX, focusY));
			// A frame where it can't be measured keeps the last size that could, rather than letting arrows grow
			// without any limit.
			if (atBoat > 0)
			{
				facingReference = atBoat;
			}
			facingLimit = facingReference > 0 ? facingReference * FACING_MAX_SCALE : -1;
			// And never more than a share of the screen, however close the camera: with it level with the sea and
			// near, even the size where it is aimed is big. Per tile, from the arrow's length of a tile and a half.
			double screenLimit = client.getViewportHeight() * FACING_MAX_SCREEN / ARROW_LENGTH;
			facingLimit = facingLimit > 0 ? Math.min(facingLimit, screenLimit) : screenLimit;
			facingMinimum = facingReference > 0 ? facingReference * FACING_MIN_SCALE : -1;
			cameraFpX = client.getCameraFpX();
			cameraFpY = client.getCameraFpY();
			cameraFpZ = client.getCameraFpZ();
			yawSin = Math.sin(client.getCameraFpYaw());
			yawCos = Math.cos(client.getCameraFpYaw());
			pitchSin = Math.sin(client.getCameraFpPitch());
			pitchCos = Math.cos(client.getCameraFpPitch());
			cameraScale = client.getScale();
		}
		// Routes included: there is no point being shown where the fish are by a boat that cannot
		// catch them, though how strict to be about that is the Show guides setting.
		if (client.getGameState() != GameState.LOGGED_IN || !plugin.showGuides())
		{
			return null;
		}
		// Only once something is to be drawn, so a boat with nothing showing doesn't work it out for nothing.
		findClearing(top);

		Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// With the route line, stops and direction arrows all off, only the shoals' heading arrows are drawn.
		boolean routes = config.showRouteLine() || config.showStops() || config.showDirectionArrows();

		// Where each shoal is on its route, found once per frame and shared by everything drawn below.
		// Placing a shoal searches its route, so it is skipped when nothing on screen needs it.
		long now = System.currentTimeMillis();
		List<PlacedShoal> shoals = routes || config.showShoalHeadingArrow()
			? placeShoals(now)
			: Collections.emptyList();

		if (routes)
		{
			if (config.routeDisplay() == TrawlingPlusConfig.RouteDisplay.WHOLE_ROUTE)
			{
				drawAllRoutes(graphics, shoals, now);
			}
			else
			{
				drawNextStops(graphics, shoals, now);
			}
		}
		drawShoalArrows(graphics, shoals, now);
		// The area and its dot fade in and out together as the area comes and goes. Switched off, both go at once
		// like anything else.
		if (config.showFishableArea())
		{
			fadeFishableArea(placeFishableArea(), now);
			if (fishingPointFade > 0 && areaView != null)
			{
				drawFishableArea(graphics);
			}
			if (config.showFishingPoint())
			{
				drawFishingPoint(graphics);
			}
		}
		else
		{
			fishingPointFade = 0;
			lastFishingPointMillis = -1;
		}
		// The display on the boat is drawn by TrawlingPlusHelmOverlay, in a layer above the game's health bars.

		if (antialiasing != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
		return null;
	}

	/**
	 * Every shoal that's matched to a route and can be placed on it this frame.
	 */
	private List<PlacedShoal> placeShoals(long now)
	{
		List<PlacedShoal> placed = new ArrayList<>();
		for (Shoal shoal : plugin.getShoals())
		{
			// Unmatched or switched off, so not placed: asked first, since placing one means looking it up.
			ShoalRoute route = shoal.getRoute();
			if (route == null || !plugin.isShown(shoal))
			{
				continue;
			}

			WorldView view = shoal.parentView(client);
			double[] position = shoal.position(client);
			if (view != null && position != null)
			{
				placed.add(new PlacedShoal(shoal, route, view, position, now));
			}
		}
		return placed;
	}

	private void drawAllRoutes(Graphics2D graphics, List<PlacedShoal> shoals, long now)
	{
		// Only the route the boat is nearest, whether or not its shoal is in view. Drawing every route
		// that reaches into the loaded scene means several at once in seas where they run close, which
		// says little and costs a good deal.
		WorldView view = client.getTopLevelWorldView();
		ShoalRoute route = plugin.getNearestRoute();
		if (view == null || route == null)
		{
			return;
		}

		int beyond = frameBeyond;
		if (route.overlaps(view.getBaseX() - beyond, view.getBaseY() - beyond,
			view.getBaseX() + view.getSizeX() + beyond, view.getBaseY() + view.getSizeY() + beyond)
			&& enoughLoaded(view, route, beyond))
		{
			drawWholeRoute(graphics, view, route, shoals, now);
		}
	}

	/**
	 * Whether enough of the route is inside the loaded map to be worth drawing, rather than a scrap at its
	 * edge, or it passes close by the boat. The loaded map only moves between ticks, so this is worked out once a tick, and stops counting as
	 * soon as there is enough.
	 */
	private boolean enoughLoaded(WorldView view, ShoalRoute route, int beyond)
	{
		int tick = client.getTickCount();
		if (tick == loadedTick && route == loadedRoute)
		{
			return loadedEnough;
		}
		loadedTick = tick;
		loadedRoute = route;

		double fromX = view.getBaseX() - beyond;
		double fromY = view.getBaseY() - beyond;
		double toX = view.getBaseX() + view.getSizeX() + beyond;
		double toY = view.getBaseY() + view.getSizeY() + beyond;
		double[] boat = plugin.getBoatPlace();
		double loaded = 0;
		for (int block = 0; block < route.blockCount() && loaded < MIN_LOADED_ROUTE_TILES; block++)
		{
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				continue;
			}

			for (int sample = route.blockFrom(block); sample < route.blockTo(block); sample++)
			{
				double dx = boat == null ? 0 : route.sampleX(sample) - boat[0];
				double dy = boat == null ? 0 : route.sampleY(sample) - boat[1];
				if (boat != null && dx * dx + dy * dy <= NEAR_ROUTE_TILES * NEAR_ROUTE_TILES)
				{
					// Beside the boat, so drawn whatever the length.
					loaded = MIN_LOADED_ROUTE_TILES;
					break;
				}

				// Each piece of the line from this sample to the next, counted when both ends are loaded.
				int next = (sample + 1) % route.sampleCount();
				if (within(route.sampleX(sample), route.sampleY(sample), fromX, fromY, toX, toY)
					&& within(route.sampleX(next), route.sampleY(next), fromX, fromY, toX, toY))
				{
					loaded += route.forward(route.sampleDistance(sample), route.sampleDistance(next));
				}
			}
		}
		loadedEnough = loaded >= MIN_LOADED_ROUTE_TILES;
		return loadedEnough;
	}

	private static boolean within(double x, double y, double fromX, double fromY, double toX, double toY)
	{
		return x >= fromX && x <= toX && y >= fromY && y <= toY;
	}

	private void drawNextStops(Graphics2D graphics, List<PlacedShoal> shoals, long now)
	{
		for (PlacedShoal placed : shoals)
		{
			drawToNextStop(graphics, placed, now);
		}
	}

	private void drawWholeRoute(Graphics2D graphics, WorldView view, ShoalRoute route, List<PlacedShoal> shoals,
		long now)
	{
		if (config.showRouteLine())
		{
			// Only the runs of the route that reach the loaded map. Most of a route is well outside it,
			// and a run can be dismissed in four comparisons instead of asking the same of all sixty
			// four points inside it.
			int beyond = frameBeyond;
			double fromX = view.getBaseX() - beyond;
			double fromY = view.getBaseY() - beyond;
			double toX = view.getBaseX() + view.getSizeX() + beyond;
			double toY = view.getBaseY() + view.getSizeY() + beyond;

			Line line = new Line();
			for (int block = 0; block < route.blockCount(); block++)
			{
				if (!route.blockWithin(block, fromX, fromY, toX, toY))
				{
					// Nowhere near, so the line picks up again wherever the route comes back.
					line.add(null);
					line.hasLast = false;
					continue;
				}

				for (int sample = route.blockFrom(block); sample < route.blockTo(block); sample++)
				{
					addPoint(line, view, route.sampleX(sample), route.sampleY(sample));
				}
			}

			// Closing the loop, so the last run joins back onto the first.
			addPoint(line, view, route.sampleX(0), route.sampleY(0));
			drawLine(graphics, line);
		}

		if (config.showDirectionArrows())
		{
			drawArrows(graphics, view, route, 0, route.length());
		}

		if (config.showStops())
		{
			boolean[] next = nextStops(route, shoals);
			double[] shown = stopsShown(route, shoals);
			for (int stop = 0; stop < route.stopCount(); stop++)
			{
				if (shown[stop] > 0)
				{
					drawStop(graphics, view, route, stop, next[stop] ? config.nextStopColour() : config.stopColour(),
						shown[stop]);
				}
			}
		}

		drawHeadArrows(graphics, view, route, shoals, now);
	}

	/**
	 * Which of a route's stops are the next stop of a shoal on it. A route whose shoal isn't in the
	 * loaded scene has none.
	 */
	private static boolean[] nextStops(ShoalRoute route, List<PlacedShoal> shoals)
	{
		boolean[] next = new boolean[route.stopCount()];
		for (PlacedShoal placed : shoals)
		{
			if (placed.route == route)
			{
				next[placed.next] = true;
			}
		}
		return next;
	}

	/**
	 * How much of each of a route's stops to show in Whole route, from 0 to 1. The stop a shoal is sitting
	 * at is hidden, fading out with its heading arrow as it settles in; every other stop shows in full.
	 */
	private static double[] stopsShown(ShoalRoute route, List<PlacedShoal> shoals)
	{
		double[] shown = new double[route.stopCount()];
		for (int stop = 0; stop < shown.length; stop++)
		{
			shown[stop] = 1;
		}

		for (PlacedShoal placed : shoals)
		{
			if (placed.route != route || !placed.shoal.stopped())
			{
				continue;
			}

			// Sitting at a stop moves a shoal's next stop on to the one after, so the one it is at comes just
			// before that. Only when it really is there, rather than stopped short of it.
			int at = (placed.next + shown.length - 1) % shown.length;
			double apart = Math.abs(placed.distance - route.stopDistance(at)) % route.length();
			if (Math.min(apart, route.length() - apart) <= ShoalRoute.AT_STOP_TILES)
			{
				shown[at] = Math.min(shown[at], placed.headingOpacity);
			}
		}
		return shown;
	}

	/**
	 * In Whole route the line is already drawn, so all that is left of the animation is the arrow at its
	 * head. When a shoal sets off for a new stop, the arrow runs along the route from the shoal to that
	 * stop over the animation duration, then fades as it arrives, the same as it does in Next stop only.
	 */
	private void drawHeadArrows(Graphics2D graphics, WorldView view, ShoalRoute route, List<PlacedShoal> shoals,
		long now)
	{
		if (!config.revealNextSection())
		{
			return;
		}

		long revealMillis = revealMillis();
		for (PlacedShoal placed : shoals)
		{
			if (placed.route != route)
			{
				continue;
			}

			// Kept up to date whether or not the arrow is drawn, so switching direction arrows on does
			// not replay a stretch the shoal set off along a while ago.
			placed.shoal.headFor(placed.next, now);
			double opacity = 1 - placed.shoal.nextStopReveal(now, revealMillis);
			if (!config.showDirectionArrows() || opacity <= 0 || !placed.shoal.revealStarted(now))
			{
				continue;
			}

			double stretch = route.forward(placed.distance, route.stopDistance(placed.next));
			double drawn = stretch * placed.shoal.routeReveal(now, revealMillis);
			double tipLength = shoalArrowLength();
			double[] place = onRoute(route, placed.distance + drawn, tipLength);
			double shown = opacity * clearShown(view, place);
			Path2D tip = shown <= 0 ? null : arrowhead(view, place, tipLength, SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (tip != null)
			{
				graphics.setColor(withOpacity(config.directionArrowColour(), shown));
				graphics.fill(tip);
			}
		}
	}

	/**
	 * How long a new stretch of route takes to animate, in milliseconds.
	 */
	private long revealMillis()
	{
		return 1000L * Math.max(TrawlingPlusConfig.MIN_ANIMATION_SECONDS,
			Math.min(TrawlingPlusConfig.MAX_ANIMATION_SECONDS, config.animationDuration()));
	}

	private void drawToNextStop(Graphics2D graphics, PlacedShoal placed, long now)
	{
		ShoalRoute route = placed.route;
		WorldView view = placed.view;
		double distance = placed.distance;
		int next = placed.next;
		double stretch = route.forward(distance, route.stopDistance(next));

		// When the shoal moves on to a new next stop, the route to it draws itself out from the shoal,
		// arrows appearing as it reaches them, and the stop fades in once the route gets there.
		placed.shoal.headFor(next, now);

		// The stop the shoal has just reached stays until it settles in there, fading out with the heading
		// arrow, rather than vanishing the moment the shoal counts as heading for the next one.
		int arrived = placed.shoal.arrivedStop();
		if (arrived >= 0)
		{
			if (placed.headingOpacity <= 0)
			{
				placed.shoal.clearArrivedStop();
			}
			else
			{
				// The last few tiles to it keep shrinking as the shoal swims in, rather than vanishing the moment
				// it comes close enough to count as heading for the next stop.
				double left = route.forward(distance, route.stopDistance(arrived));
				if (left <= ShoalRoute.AT_STOP_TILES)
				{
					drawStretch(graphics, view, route, distance, left, placed.headingOpacity);
				}
				if (config.showStops())
				{
					drawStop(graphics, view, route, arrived, config.nextStopColour(), placed.headingOpacity);
				}
			}
		}

		double routeReveal = 1;
		double stopReveal = 1;
		if (config.revealNextSection())
		{
			long revealMillis = revealMillis();
			routeReveal = placed.shoal.routeReveal(now, revealMillis);
			stopReveal = placed.shoal.nextStopReveal(now, revealMillis);
			if (!placed.shoal.revealStarted(now))
			{
				// Waiting for the shoal to settle in before the way to its next stop is drawn.
				return;
			}
		}
		double drawn = stretch * routeReveal;
		drawStretch(graphics, view, route, distance, drawn, 1);

		if (config.showStops() && stopReveal > 0)
		{
			drawStop(graphics, view, route, next, config.nextStopColour(), stopReveal);
		}

		// An arrow leads the route as it draws out, and fades away as the stop fades in.
		double tipOpacity = 1 - stopReveal;
		if (config.showDirectionArrows() && tipOpacity > 0)
		{
			double tipLength = shoalArrowLength();
			double[] place = onRoute(route, distance + drawn, tipLength);
			double shown = tipOpacity * clearShown(view, place);
			Path2D tip = shown <= 0 ? null : arrowhead(view, place, tipLength, SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (tip != null)
			{
				graphics.setColor(withOpacity(config.directionArrowColour(), shown));
				graphics.fill(tip);
			}
		}
	}

	/**
	 * Whether either display on the player's boat could be up this frame: the guides are showing, they are fishing a
	 * spot at sea, or the hold's display is still fading out after they stopped. Asked before anything else, so
	 * with neither, nothing about them is worked out.
	 */
	boolean displayWanted()
	{
		return plugin.showGuides() || plugin.isSeaFishing()
			|| holdFade > 0 && System.currentTimeMillis() - lastHoldFadeMillis <= HELM_FADE_MILLIS;
	}

	/**
	 * Lets go of everything kept between frames that holds on to the game or its pictures, as the plugin is switched
	 * off, so none of it stays in memory; all of it is made again when next needed.
	 */
	void forget()
	{
		loadedRoute = null;
		areaView = null;
		fishingPointFade = 0;
		barFrontSprite = null;
		barBackSprite = null;
		barFront = null;
		barBack = null;
		barFrontSource = null;
		barBackSource = null;
		barWidth = -1;
		laidHold = null;
		laidFont = null;
		holdFade = 0;
		lastHoldFadeMillis = -1;
	}

	/**
	 * Draws the displays on the player's boat: the one at the helm while the guides are showing, and the hold's
	 * above it while fishing a spot at sea, or in its place when it is the only one up.
	 */
	void drawHelm(Graphics2D graphics)
	{
		// The heads up display's own switch covers both.
		if (!config.showHeadsUpDisplay())
		{
			return;
		}

		// The hold's display is laid out first, so the one at the helm can make room for it above itself. It shows
		// in either guides mode, even on a raft, which has no nets for the guides to wait on.
		long now = System.currentTimeMillis();
		boolean hold = layOutHold(graphics, now);
		boolean helm = plugin.showGuides() && drawTrawling(graphics, now, hold);
		if (hold && !helm)
		{
			drawHoldAlone(graphics, now);
		}
	}

	/**
	 * Draws the display at the helm of the player's boat: the depth, time left at the stop, fish in the
	 * nets, bait and the hold full warning, each line fading in and out on its own, and the hold's display
	 * above it when that is up. Says whether it drew anything.
	 */
	private boolean drawTrawling(Graphics2D graphics, long now, boolean hold)
	{
		// Each line of the display stands on its own. Only the depth waits on a depth being known; the
		// rest have nothing to do with it and are not held up by it.
		WorldEntity boat = plugin.getBoat();
		ShoalDepth depth = boat == null ? ShoalDepth.UNKNOWN : plugin.getNearestDepth();
		if (depth != ShoalDepth.UNKNOWN)
		{
			// Kept while the text fades out, so it does not change word on the way.
			fadingDepth = depth;
		}

		double seconds = boat == null || !config.showTimeAtStop() ? -1 : plugin.getSecondsAtStop();
		double barFull = boat == null || !config.showTimerBar() ? -1 : plugin.getStopBarFraction();
		if (barFull >= 0)
		{
			fadingBarFull = barFull;
		}
		boolean[] wanted = helmWanted;
		wanted[DEPTH_LINE] = boat != null && config.showShoalDepth() && depth != ShoalDepth.UNKNOWN;
		wanted[TIME_LINE] = seconds >= 0;
		wanted[BAR_LINE] = barFull >= 0;
		wanted[BAITED_LINE] = boat != null && config.showBaited() && (plugin.isBaited() || plugin.isOutOfBait());
		wanted[FISH_LINE] = boat != null && config.showFishInNets() && plugin.netsFitted() && plugin.fishLineWanted(now);
		// The hold's own display says when it is full, when that is up.
		wanted[HOLD_LINE] = boat != null && config.showFishInNets() && plugin.isHoldFull() && !hold;

		// A line going while another holds the pill up goes at once, and one coming waits for room to be made for it
		// and then fades in. The display itself arriving or leaving fades as a whole.
		// With Animated off, everything shows and goes at once.
		boolean animated = config.animatedHud();
		double step = !animated ? 1 : lastHelmFadeMillis < 0 ? 0
			: Math.max(0, now - lastHelmFadeMillis) / HELM_FADE_MILLIS;
		boolean[] wasUp = helmWasUp;
		for (int line = 0; line < HELM_LINES; line++)
		{
			wasUp[line] = helmFades[line] > 0;
		}

		double[] opacity = helmOpacity;
		boolean[] laid = helmLaid;
		for (int line = 0; line < HELM_LINES; line++)
		{
			// Another line already holding the pill up means this one is changing inside a display that is already on
			// screen. The timer bar fades out whatever else is up, as it comes and goes with the stops.
			boolean inside = othersUp(wasUp, line);
			opacity[line] = wanted[line] && inside && animated && !roomReady
				? fade(line, true, 0, false)
				: fade(line, wanted[line], step, inside && !wanted[line] && line != BAR_LINE);
			// Given its place while it waits, so the background grows to make room for it.
			laid[line] = opacity[line] > 0 || wanted[line];
		}
		lastHelmFadeMillis = now;

		WorldView deck = boat == null ? null : boat.getWorldView();
		WorldEntityConfig hull = boat == null ? null : boat.getConfig();
		if (deck == null || hull == null)
		{
			return false;
		}

		// The lines that are actually showing, bottom first, so a gap in the middle closes up.
		String[] text = helmText;
		Color[] colour = helmColour;
		double[] showing = helmShowing;
		int[] width = helmWidth;
		FontMetrics letters = graphics.getFontMetrics();
		boolean ticked = laid[DEPTH_LINE] && config.showDepthTick() && plugin.isAtDepth();
		int count = 0;
		int depthAt = -1;

		if (laid[DEPTH_LINE] && fadingDepth != ShoalDepth.UNKNOWN)
		{
			depthAt = count;
			text[count] = fadingDepth.toString();
			colour[count] = depthColour(fadingDepth);
			showing[count] = opacity[DEPTH_LINE];
			width[count] = letters.stringWidth(text[count])
				+ (ticked ? TICK_GAP + TrawlingPlusNetOverlay.TICK_WIDTH : 0);
			count++;
		}
		if (laid[BAITED_LINE])
		{
			text[count] = plugin.getBaitedLabel();
			// Text, so drawn solid whatever the picked colour says.
			colour[count] = TrawlingPlusNetOverlay.opaque(config.baitedColour());
			if (plugin.isOutOfBait())
			{
				// Out of bait pulses between the picked colour and red, in time with the other warnings.
				double pulse = warningPulse(now);
				colour[count] = blend(colour[count], HOLD_FULL_COLOUR, pulse);
			}
			showing[count] = opacity[BAITED_LINE];
			width[count] = letters.stringWidth(text[count]);
			count++;
		}
		if (laid[FISH_LINE])
		{
			text[count] = plugin.getFishLabel();
			colour[count] = TrawlingPlusNetOverlay.opaque(config.fishInNetsColour());
			if (plugin.netsFull())
			{
				// Full nets pulse between the picked colour and red, in time with the hold full warning.
				double pulse = warningPulse(now);
				colour[count] = blend(colour[count], HOLD_FULL_COLOUR, pulse);
			}
			showing[count] = opacity[FISH_LINE];
			width[count] = letters.stringWidth(text[count]);
			count++;
		}
		if (laid[TIME_LINE])
		{
			text[count] = Math.max(0, Math.round(seconds)) + "s";
			colour[count] = TrawlingPlusNetOverlay.opaque(config.timeLeftColour());
			showing[count] = opacity[TIME_LINE];
			width[count] = letters.stringWidth(text[count]);
			count++;
		}
		if (laid[HOLD_LINE])
		{
			// Eased back and forth rather than blinking, starting and ending on the first colour.
			double pulse = warningPulse(now);
			text[count] = HOLD_FULL;
			colour[count] = blend(HOLD_FULL_COLOUR, HOLD_FULL_PULSE_COLOUR, pulse);
			showing[count] = opacity[HOLD_LINE];
			width[count] = letters.stringWidth(HOLD_FULL);
			count++;
		}

		double barShowing = opacity[BAR_LINE];
		boolean bar = laid[BAR_LINE] && readBar();
		if (count == 0 && !bar)
		{
			return false;
		}
		// Placed by its bottom line of text, or with only the bar, where that line would be.
		String placedBy = count > 0 ? text[0] : "";

		Point at = helmPoint(graphics, boat, deck, hull, placedBy);
		if (at == null)
		{
			return false;
		}

		// getCanvasTextLocation gives the left end of the bottom line's baseline; the stack grows up
		// from there and the pill grows with it.
		int lineHeight = letters.getHeight();
		int wide = 0;
		double solid = 0;
		for (int line = 0; line < count; line++)
		{
			wide = Math.max(wide, width[line]);
			solid = Math.max(solid, showing[line]);
		}
		// The bar hangs below the text, so the text keeps its place whether it shows or not, and is as wide as it.
		int barHeight = 0;
		if (bar)
		{
			sizeBar(Math.max(BAR_MIN_WIDTH, wide));
			wide = Math.max(wide, barBack.getWidth());
			solid = Math.max(solid, barShowing);
			barHeight = (count > 0 ? BAR_GAP : 0) + barBack.getHeight();
		}
		int left = at.getX() + (letters.stringWidth(placedBy) - wide) / 2;
		int top = at.getY() - letters.getAscent() - (count - 1) * lineHeight;

		// The background's size this frame, growing and shrinking towards what it holds. Starts at its size when it
		// hasn't been drawn for a while, since it is fading in then anyway.
		int textHeight = lineHeight * count;
		if (!animated || lastResizeMillis < 0 || now - lastResizeMillis > HELM_FADE_MILLIS)
		{
			shownTextHeight = textHeight;
			shownBarHeight = barHeight;
			shownWide = wide;
		}
		else
		{
			double behind = Math.exp(-(now - lastResizeMillis) / HELM_RESIZE_MILLIS);
			shownTextHeight = textHeight + (shownTextHeight - textHeight) * behind;
			shownBarHeight = barHeight + (shownBarHeight - barHeight) * behind;
			shownWide = wide + (shownWide - wide) * behind;
		}
		lastResizeMillis = now;
		int boxTextHeight = (int) Math.round(shownTextHeight);
		int boxWide = (int) Math.round(shownWide);
		int boxBar = (int) Math.round(shownBarHeight);

		// Whether it has grown half way to its new height yet, for what is waiting on it next frame.
		int heading = textHeight + barHeight;
		if (heading != resizeTo)
		{
			resizeFrom = boxTextHeight + boxBar;
			resizeTo = heading;
		}
		roomReady = resizeTo <= resizeFrom || (boxTextHeight + boxBar - resizeFrom) * 2 >= resizeTo - resizeFrom;

		// Kept inside the game view, so zoomed in close, where its place on the boat is off the edge of the
		// screen, it stops at the edge rather than going out of sight. With the hold's display above it, the two
		// are kept in as one, so neither is pushed over the other.
		int boxLeft = left + (wide - boxWide) / 2 - HELM_PADDING;
		int boxTop = top + textHeight - boxTextHeight - HELM_PADDING;
		int boxWidth = boxWide + HELM_PADDING * 2;
		int boxHeight = boxTextHeight + boxBar + HELM_PADDING * 2;
		int centre = left + wide / 2;
		int blockLeft = hold ? Math.min(boxLeft, centre - holdBoxWidth / 2) : boxLeft;
		int blockRight = hold ? Math.max(boxLeft + boxWidth, centre - holdBoxWidth / 2 + holdBoxWidth)
			: boxLeft + boxWidth;
		int blockTop = hold ? boxTop - HOLD_GAP - holdBoxHeight : boxTop;
		int shiftX = keepInside(blockLeft, blockRight - blockLeft, client.getViewportXOffset(), client.getViewportWidth());
		int shiftY = keepInside(blockTop, boxTop + boxHeight - blockTop, client.getViewportYOffset(),
			client.getViewportHeight());
		left += shiftX;
		top += shiftY;
		int bottom = at.getY() + shiftY;

		// One pill around the lot, as opaque as whichever line is showing the most, so it does not
		// flicker as one of them fades while the others stay.
		graphics.setColor(withOpacity(HELM_BACKGROUND, solid));
		graphics.fillRoundRect(boxLeft + shiftX, boxTop + shiftY, boxWidth, boxHeight, 6, 6);

		for (int line = 0; line < count; line++)
		{
			int from = left + (wide - width[line]) / 2;
			int baseline = bottom - line * lineHeight;
			line(graphics, text[line], from, baseline, colour[line], showing[line]);
			if (line == depthAt && ticked)
			{
				TrawlingPlusNetOverlay.drawTick(graphics,
					from + letters.stringWidth(text[line]) + TICK_GAP,
					baseline - letters.getAscent() / 2,
					withOpacity(TrawlingPlusNetOverlay.TICK, showing[line]));
			}
		}

		if (bar)
		{
			drawBar(graphics, left + (wide - barBack.getWidth()) / 2,
				top + lineHeight * count + (count > 0 ? BAR_GAP : 0), fadingBarFull, barShowing);
		}

		if (hold)
		{
			drawHold(graphics, centre + shiftX, boxTop + shiftY - HOLD_GAP, now);
		}
		return true;
	}

	/**
	 * Where the display at the helm, or the hold's in its place, sits on screen: the left end of the baseline of a
	 * bottom line of text centred there, at the helm, the bow or above the sails. Null when it can't be placed.
	 */
	private Point helmPoint(Graphics2D graphics, WorldEntity boat, WorldView deck, WorldEntityConfig hull,
		String placedBy)
	{
		// The helm sits a quarter of the hull length behind the middle of the boat, and the text a
		// tile and a half further back again. A hull an odd number of tiles wide has its middle
		// tile half a tile off the middle of the view.
		int across = deck.getSizeX() * Perspective.LOCAL_TILE_SIZE / 2;
		if ((hull.getBoundsWidth() / Perspective.LOCAL_TILE_SIZE) % 2 != 0)
		{
			across -= Perspective.LOCAL_HALF_TILE_SIZE;
		}
		LocalPoint anchor = new LocalPoint(across,
			deck.getSizeY() * Perspective.LOCAL_TILE_SIZE / 2 + hull.getBoundsHeight() / 4
				+ Perspective.LOCAL_TILE_SIZE * 3 / 2, deck);
		int lift = HELM_TEXT_HEIGHT;
		// At the bow or above the sails instead, once this boat's deck has been read for where those are; until
		// then, which is only as its models load, at the helm.
		TrawlingPlusConfig.HudPosition position = config.hudPosition();
		if (position != TrawlingPlusConfig.HudPosition.HELM && readAnchors(boat, deck, hull))
		{
			// A raft's sail is barely above its deck, so above it is the same as at its bow.
			if (position == TrawlingPlusConfig.HudPosition.BOW || !SAILS_BY_BOAT.containsKey(hull.getId()))
			{
				anchor = new LocalPoint(across, bowLocalY, deck);
			}
			else
			{
				// Over the deck a little towards the helm from the bow's spot, which from any angle reads as above the
				// sails, near the height of their top.
				// In tenths of a tile, by boat type.
				int[] sails = SAILS_BY_BOAT.get(hull.getId());
				int back = sails[0] * Perspective.LOCAL_TILE_SIZE / 10;
				int offset = sails[1] * Perspective.LOCAL_TILE_SIZE / 10;
				anchor = new LocalPoint(across, bowLocalY + back, deck);
				lift = sailTop + offset;
			}
		}
		LocalPoint afloat = boat.transformToMainWorld(anchor);
		return afloat == null ? null : Perspective.getCanvasTextLocation(client, graphics, afloat, placedBy, lift);
	}

	/**
	 * Works out the hold's display for this frame and says whether it is up. Fades in as fishing a spot starts and
	 * out as it ends, and goes at once when stepping off the boat or switching it off. A fish arriving in it grows
	 * the box and fades in once there's room, and one going goes at once and the box shrinks after it.
	 */
	private boolean layOutHold(Graphics2D graphics, long now)
	{
		// Not fishing a spot, and faded out already, so there is nothing to work out.
		boolean fishing = plugin.isSeaFishing();
		if (!fishing && holdFade <= 0)
		{
			return false;
		}
		CargoHold record = plugin.getHold();
		if (record == null)
		{
			holdFade = 0;
			lastHoldFadeMillis = -1;
			return false;
		}

		// With Animated off, it comes and goes at once. Arriving, or back after not being drawn for a while, it is
		// laid out at its size at once, as it is fading in as a whole then anyway; with Animated off, it always is.
		boolean animated = config.animatedHud();
		boolean fresh = !animated || lastHoldFadeMillis < 0 || now - lastHoldFadeMillis > HELM_FADE_MILLIS;
		double elapsed = lastHoldFadeMillis < 0 ? 0 : Math.max(0, now - lastHoldFadeMillis);
		double pillStep = animated ? elapsed / HELM_FADE_MILLIS : 1;
		holdFade = fishing ? Math.min(1, holdFade + pillStep) : Math.max(0, holdFade - pillStep);
		if (!fishing && holdFade <= 0)
		{
			lastHoldFadeMillis = -1;
			return false;
		}
		lastHoldFadeMillis = now;

		// Its lines only change with what the hold holds or the font, so they are only written out again then.
		Font font = graphics.getFont();
		if (record != laidHold || record.changes() != laidChanges || !font.equals(laidFont))
		{
			laidHold = record;
			laidChanges = record.changes();
			laidFont = font;
			writeHoldLines(record, graphics.getFontMetrics());
		}

		// The box's size this frame, growing and shrinking towards what it holds, and whether it has grown half way
		// to its new height yet, for a fish waiting on it.
		int lineHeight = holdLineHeight;
		int height = lineHeight * holdLines + HELM_PADDING * 2;
		double behind = fresh ? 0 : Math.exp(-elapsed / HELM_RESIZE_MILLIS);
		double wasHeight = fresh ? height : holdShownHeight;
		holdShownWide = holdWide + (holdShownWide - holdWide) * behind;
		holdShownHeight = height + (holdShownHeight - height) * behind;
		if (fresh || height != holdResizeTo)
		{
			holdResizeFrom = (int) Math.round(wasHeight);
			holdResizeTo = height;
		}
		holdRoomReady = holdResizeTo <= holdResizeFrom
			|| (Math.round(holdShownHeight) - holdResizeFrom) * 2 >= holdResizeTo - holdResizeFrom;

		// Each line slides to its place, counted up from the bottom of the box, or starts there when new. One new
		// waits until there's room and then fades in; one already in stays in; one gone goes at once.
		double step = fresh ? 1 : elapsed / HELM_FADE_MILLIS;
		for (int place = 0; place < HOLD_PLACES; place++)
		{
			holdLaid[place] = false;
		}
		for (int line = 0; line < holdLines; line++)
		{
			int place = holdPlace[line];
			holdLaid[place] = true;
			double up = height - HELM_PADDING - holdAscent - line * lineHeight;
			holdShownUp[place] = fresh || !holdWasLaid[place] ? up : up + (holdShownUp[place] - up) * behind;
			holdLineFade[place] = fresh ? 1
				: holdLineFade[place] > 0 || holdRoomReady ? Math.min(1, holdLineFade[place] + step) : 0;
		}
		for (int place = 0; place < HOLD_PLACES; place++)
		{
			if (!holdLaid[place])
			{
				holdLineFade[place] = 0;
			}
			holdWasLaid[place] = holdLaid[place];
		}

		holdBoxWidth = (int) Math.round(holdShownWide) + HELM_PADDING * 2;
		holdBoxHeight = (int) Math.round(holdShownHeight);
		return true;
	}

	/**
	 * Writes out the hold's display's lines: its title, how full the hold is under that, and the fish in it below,
	 * the three kinds that went in most recently, most first, then the rest together, each name with its count lined
	 * up on the right; and how wide and tall they are.
	 */
	private void writeHoldLines(CargoHold record, FontMetrics letters)
	{
		holdLines = 0;
		holdFull = record.full();
		holdPlace[holdLines] = 0;
		holdLeft[holdLines] = HOLD_TITLE;
		holdRight[holdLines] = "";
		holdColour[holdLines] = Color.WHITE;
		holdLines++;
		holdPlace[holdLines] = 1;
		holdLeft[holdLines] = holdFull ? HOLD_FULL : HOLD_TOTAL;
		holdRight[holdLines] = record.caught() + "/" + record.room();
		holdColour[holdLines] = Color.WHITE;
		holdLines++;

		// Only the kinds that went in most recently get a line each, so the list never grows to every fish there is:
		// between two that went in together, or before any deposit has been seen, the one with more. Those are listed
		// most first, and in the order of the fishing spots between two of the same, and the rest share a line below.
		int held = 0;
		for (int kind = 0; kind < CargoHold.SEA_FISH.length; kind++)
		{
			if (record.fish(kind) > 0)
			{
				holdOrder[held++] = kind;
			}
		}
		sortKinds(record, held, true);
		int shown = Math.min(HOLD_KINDS_SHOWN, held);
		sortKinds(record, shown, false);
		int rest = record.caught();
		for (int k = 0; k < shown; k++)
		{
			int kind = holdOrder[k];
			holdPlace[holdLines] = 2 + kind;
			holdLeft[holdLines] = CargoHold.SEA_FISH_NAMES[kind];
			holdRight[holdLines] = String.valueOf(record.fish(kind));
			holdColour[holdLines] = CargoHold.SEA_FISH_COLOURS[kind];
			holdLines++;
			rest -= record.fish(kind);
		}
		if (held > shown)
		{
			holdPlace[holdLines] = HOLD_OTHER_PLACE;
			holdLeft[holdLines] = HOLD_OTHER;
			holdRight[holdLines] = String.valueOf(rest);
			holdColour[holdLines] = HOLD_OTHER_COLOUR;
			holdLines++;
		}

		// The counts line up on the right of a column as wide as they would be in the widest digit, so the display
		// doesn't change width by a pixel or two whenever one changes. The fish never outnumber the room for them,
		// so the slots line is the widest, and only its number of digits counts.
		int digit = 0;
		for (char c = '0'; c <= '9'; c++)
		{
			digit = Math.max(digit, letters.charWidth(c));
		}
		int roomDigits = digits(record.room());
		int rightWide = digit * (Math.max(digits(record.caught()), roomDigits) + roomDigits) + letters.charWidth('/');

		// The title spans both columns, so it only has to fit their width, not be part of the left one.
		int leftWide = 0;
		for (int line = 0; line < holdLines; line++)
		{
			holdRightWidth[line] = letters.stringWidth(holdRight[line]);
			if (line > 0)
			{
				leftWide = Math.max(leftWide, letters.stringWidth(holdLeft[line]));
			}
		}
		holdWide = Math.max(leftWide + HOLD_COLUMN_GAP + rightWide, letters.stringWidth(HOLD_TITLE));
		holdLineHeight = letters.getHeight();
		holdAscent = letters.getAscent();
	}

	/**
	 * Sorts the first so many kinds of fish in the hold's list: when asked, by the deposit that last brought them,
	 * most recent first; then by how many there are, most first; then in the order of the fishing spots.
	 */
	private void sortKinds(CargoHold record, int count, boolean recentFirst)
	{
		for (int k = 1; k < count; k++)
		{
			int kind = holdOrder[k];
			int j = k;
			while (j > 0 && listedBefore(record, kind, holdOrder[j - 1], recentFirst))
			{
				holdOrder[j] = holdOrder[j - 1];
				j--;
			}
			holdOrder[j] = kind;
		}
	}

	private static boolean listedBefore(CargoHold record, int kind, int other, boolean recentFirst)
	{
		if (recentFirst && record.lastDeposit(kind) != record.lastDeposit(other))
		{
			return record.lastDeposit(kind) > record.lastDeposit(other);
		}
		if (record.fish(kind) != record.fish(other))
		{
			return record.fish(kind) > record.fish(other);
		}
		return kind < other;
	}

	/**
	 * How many digits a whole number is written with.
	 */
	private static int digits(int number)
	{
		int digits = 1;
		for (int rest = Math.abs(number) / 10; rest > 0; rest /= 10)
		{
			digits++;
		}
		return digits;
	}

	/**
	 * Draws the hold's display where the one at the helm would be, when it is the only one up: its bottom line
	 * where that one's would be.
	 */
	private void drawHoldAlone(Graphics2D graphics, long now)
	{
		WorldEntity boat = plugin.getOwnBoat();
		WorldView deck = boat == null ? null : boat.getWorldView();
		WorldEntityConfig hull = boat == null ? null : boat.getConfig();
		Point at = deck == null || hull == null ? null : helmPoint(graphics, boat, deck, hull, "");
		if (at == null)
		{
			return;
		}

		int bottom = at.getY() + holdLineHeight - holdAscent + HELM_PADDING;
		int shiftX = keepInside(at.getX() - holdBoxWidth / 2, holdBoxWidth, client.getViewportXOffset(),
			client.getViewportWidth());
		int shiftY = keepInside(bottom - holdBoxHeight, holdBoxHeight, client.getViewportYOffset(),
			client.getViewportHeight());
		drawHold(graphics, at.getX() + shiftX, bottom + shiftY, now);
	}

	/**
	 * Draws the hold's display as laid out this frame, centred on a point across and with its bottom edge at a
	 * point down the screen.
	 */
	private void drawHold(Graphics2D graphics, int centre, int bottom, long now)
	{
		graphics.setColor(withOpacity(HELM_BACKGROUND, holdFade));
		graphics.fillRoundRect(centre - holdBoxWidth / 2, bottom - holdBoxHeight, holdBoxWidth, holdBoxHeight, 6, 6);

		// The columns spread with the box as it widens, so the counts slide out rather than jump.
		int wide = holdBoxWidth - HELM_PADDING * 2;
		int left = centre - wide / 2;
		for (int line = 0; line < holdLines; line++)
		{
			int place = holdPlace[line];
			double opacity = holdFade * holdLineFade[place];
			if (opacity <= 0)
			{
				continue;
			}
			int baseline = bottom - (int) Math.round(holdShownUp[place]);
			// Full pulses between the two warning colours, in time with the other warnings; each fish is in its own.
			Color colour = line == 1 && holdFull
				? blend(HOLD_FULL_COLOUR, HOLD_FULL_PULSE_COLOUR, warningPulse(now)) : holdColour[line];
			line(graphics, holdLeft[line], left, baseline, colour, opacity);
			line(graphics, holdRight[line], left + wide - holdRightWidth[line], baseline, colour, opacity);
		}
	}

	/**
	 * Draws the stop bar the way the game does: the full picture up to how far along it is, and the empty one
	 * after. RuneLite's high detail bars have a pixel of border at each end that is not part of the bar, so there
	 * it runs between the two.
	 */
	private void drawBar(Graphics2D graphics, int x, int y, double full, double opacity)
	{
		int wide = barBack.getWidth();
		int border = barFrontSource != null ? 1 : 0;
		int filled = border + (int) (full * (wide - border * 2));
		Composite composite = graphics.getComposite();
		if (opacity < 1)
		{
			graphics.setComposite(AlphaComposite.SrcOver.derive((float) opacity));
		}
		int high = Math.min(barFront.getHeight(), barBack.getHeight());
		graphics.drawImage(barFront, x, y, x + filled, y + high, 0, 0, filled, high, null);
		graphics.drawImage(barBack, x + filled, y, x + wide, y + high, filled, 0, wide, high, null);
		graphics.setComposite(composite);
	}

	/**
	 * Finds the stop bar's two pictures, the replacements a skin or resource pack has put in the client first,
	 * since the sprite manager's own lookup skips them. False until both are loaded.
	 */
	private boolean readBar()
	{
		SpritePixels front = client.getSpriteOverrides().get(BAR_FRONT);
		SpritePixels back = client.getSpriteOverrides().get(BAR_BACK);
		if (front != barFrontSource || barFrontSprite == null)
		{
			barFrontSprite = front != null ? front.toBufferedImage() : spriteManager.getSprite(BAR_FRONT, 0);
			barFrontSource = front;
			barWidth = -1;
		}
		if (back != barBackSource || barBackSprite == null)
		{
			barBackSprite = back != null ? back.toBufferedImage() : spriteManager.getSprite(BAR_BACK, 0);
			barBackSource = back;
			barWidth = -1;
		}
		return barFrontSprite != null && barBackSprite != null;
	}

	/**
	 * Resizes the bar's pictures to a width, only when it differs from the last.
	 */
	private void sizeBar(int wide)
	{
		if (wide != barWidth)
		{
			barFront = resized(barFrontSprite, wide, barFrontSource != null);
			barBack = resized(barBackSprite, wide, barBackSource != null);
			barWidth = wide;
		}
	}

	/**
	 * One of the bar's pictures stretched or squeezed to a width. A high detail one keeps its pixel of border at
	 * each end as it is and only its middle changes, so the ends stay crisp.
	 */
	private static BufferedImage resized(BufferedImage image, int wide, boolean bordered)
	{
		if (image.getWidth() == wide)
		{
			return image;
		}
		int high = image.getHeight();
		int border = bordered ? 1 : 0;
		BufferedImage sized = new BufferedImage(wide, high, BufferedImage.TYPE_INT_ARGB);
		Graphics2D drawing = sized.createGraphics();
		drawing.drawImage(image, 0, 0, border, high, 0, 0, border, high, null);
		drawing.drawImage(image, border, 0, wide - border, high, border, 0, image.getWidth() - border, high, null);
		drawing.drawImage(image, wide - border, 0, wide, high, image.getWidth() - border, 0, image.getWidth(), high, null);
		drawing.dispose();
		return sized;
	}

	/**
	 * How far to move a box from a start along one axis, with a size, to keep it inside a stretch of screen
	 * starting at another, with its own size: 0 when it already fits.
	 */
	private static int keepInside(int start, int size, int from, int length)
	{
		if (start < from)
		{
			return from - start;
		}
		int over = start + size - (from + length);
		return over > 0 ? -Math.min(over, start - from) : 0;
	}

	/**
	 * Draws the route line and its arrows for a stretch ahead of a distance round the route, as far as it
	 * has been drawn out to, at the given opacity.
	 */
	private void drawStretch(Graphics2D graphics, WorldView view, ShoalRoute route, double distance, double drawn,
		double opacity)
	{
		if (config.showRouteLine())
		{
			int from = route.sampleAt(distance);
			int steps = Math.floorMod(route.sampleAt(distance + drawn) - from, route.sampleCount());

			// Starts where the shoal is along the route rather than where the shoal itself is, so the line
			// only ever gets shorter as the shoal swims along it. Joining it to the shoal instead leaves a
			// first piece that swings about, since a smoothed route never runs exactly through the shoal.
			Line line = new Line();
			double[] start = route.pointAt(distance);
			addPoint(line, view, start[0], start[1]);
			for (int step = 0; step < steps; step++)
			{
				int sample = (from + step) % route.sampleCount();
				if (route.forward(distance, route.sampleDistance(sample)) >= drawn)
				{
					break;
				}
				addPoint(line, view, route.sampleX(sample), route.sampleY(sample));
			}

			// Ends along the route too, level with the stop once it has drawn all the way there, rather than
			// at the stop itself: a smoothed route passes beside a stop, and joining the two leaves a hook.
			double[] end = route.pointAt(distance + drawn);
			addPoint(line, view, end[0], end[1]);
			drawLine(graphics, line, opacity);
		}

		if (config.showDirectionArrows())
		{
			drawArrows(graphics, view, route, distance, drawn, opacity);
		}
	}

	private void drawLine(Graphics2D graphics, Line line)
	{
		drawLine(graphics, line, 1);
	}

	private void drawLine(Graphics2D graphics, Line line, double opacity)
	{
		// Round joins and caps so the short segments the curve is drawn with blend into one smooth line.
		graphics.setColor(opacity >= 1 ? config.routeColour() : withOpacity(config.routeColour(), opacity));
		lineStrokes(config.routeLineThickness().pixels());
		graphics.setStroke(lineStroke);
		graphics.draw(line.path);
		if (line.fadedCount == 0)
		{
			return;
		}

		// Flat ended, since the round ends of see-through pieces would overlap and show as darker beads.
		graphics.setStroke(fadedLineStroke);
		Paint paint = graphics.getPaint();
		Line2D.Double piece = new Line2D.Double();
		double[] f = line.faded;
		for (int k = 0; k < line.fadedCount; k += 6)
		{
			piece.setLine(f[k], f[k + 1], f[k + 2], f[k + 3]);
			graphics.setPaint(new GradientPaint((float) f[k], (float) f[k + 1],
				withOpacity(config.routeColour(), opacity * f[k + 4]), (float) f[k + 2], (float) f[k + 3],
				withOpacity(config.routeColour(), opacity * f[k + 5])));
			graphics.draw(piece);
		}
		graphics.setPaint(paint);
	}

	/**
	 * The colour a depth is shown in.
	 */
	private Color depthColour(ShoalDepth depth)
	{
		switch (depth)
		{
			case SHALLOW:
				return TrawlingPlusNetOverlay.opaque(config.shallowDepthColour());
			case DEEP:
				return TrawlingPlusNetOverlay.opaque(config.deepDepthColour());
			default:
				return TrawlingPlusNetOverlay.opaque(config.moderateDepthColour());
		}
	}

	/**
	 * Whether any line other than the given one is showing.
	 */
	private static boolean othersUp(boolean[] up, int except)
	{
		for (int line = 0; line < up.length; line++)
		{
			if (line != except && up[line])
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Moves one line of the display towards shown or hidden, and gives back how opaque it now is. A
	 * line held up by one of the others changes at once instead of fading.
	 */
	private double fade(int line, boolean showing, double step, boolean atOnce)
	{
		if (atOnce)
		{
			helmFades[line] = showing ? 1 : 0;
		}
		else
		{
			helmFades[line] = showing ? Math.min(1, helmFades[line] + step)
				: Math.max(0, helmFades[line] - step);
		}

		// Smoothstep, so the fade eases in and out rather than changing at a constant rate.
		double faded = helmFades[line];
		return faded * faded * (3 - 2 * faded);
	}

	/**
	 * One line of the helm text, with the shadow drawn here rather than with OverlayUtil so that it
	 * fades along with the text.
	 */
	private static void line(Graphics2D graphics, String text, int left, int baseline, Color colour,
		double opacity)
	{
		graphics.setColor(withOpacity(Color.BLACK, opacity));
		graphics.drawString(text, left + 1, baseline + 1);
		graphics.setColor(withOpacity(colour, opacity));
		graphics.drawString(text, left, baseline);
	}

	/**
	 * Draws the route's arrows that fall within a stretch of it, starting at a distance round the route.
	 * Arrows sit at fixed places round the route, so they don't slide along as the shoal swims.
	 */
	private void drawArrows(Graphics2D graphics, WorldView view, ShoalRoute route, double from, double stretch)
	{
		drawArrows(graphics, view, route, from, stretch, 1);
	}

	private void drawArrows(Graphics2D graphics, WorldView view, ShoalRoute route, double from, double stretch,
		double opacity)
	{
		// Clamped as well as limited in the settings panel, since a spacing of 0 would never finish.
		int spacing = Math.max(TrawlingPlusConfig.MIN_ARROW_SPACING,
			Math.min(TrawlingPlusConfig.MAX_ARROW_SPACING, config.directionArrowSpacing()));
		double length = scaled(ARROW_LENGTH, config.directionArrowScale());

		// The first arrow sits one spacing in from the start of the route, so where the loop closes, its
		// last arrow and first arrow are never closer than the spacing.
		Color colour = opacity >= 1 ? config.directionArrowColour() : withOpacity(config.directionArrowColour(), opacity);
		graphics.setColor(colour);

		// Only the arrows on runs of the route that reach the loaded map, as the line is drawn: an arrow off it
		// can't be drawn, and a run is dismissed in four comparisons rather than placing every arrow along it.
		int beyond = view.isTopLevel() ? frameBeyond : 0;
		double fromX = view.getBaseX() - beyond;
		double fromY = view.getBaseY() - beyond;
		double toX = view.getBaseX() + view.getSizeX() + beyond;
		double toY = view.getBaseY() + view.getSizeY() + beyond;
		for (int block = 0; block < route.blockCount(); block++)
		{
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				continue;
			}

			double opens = route.sampleDistance(route.blockFrom(block));
			double closes = route.blockTo(block) < route.sampleCount()
				? route.sampleDistance(route.blockTo(block)) : route.length();
			for (int arrow = Math.max(1, (int) Math.ceil(opens / spacing));
				arrow * spacing < closes && arrow * spacing < route.length(); arrow++)
			{
				double at = arrow * spacing;
				if (route.forward(from, at) < stretch)
				{
					double[] place = onRoute(route, at, length);
					double shown = clearShown(view, place);
					Path2D head = shown <= 0 ? null : arrowhead(view, place, length, ARROW_HALF_WIDTH, ARROW_NOTCH);
					if (head != null)
					{
						// Only an arrow near the boat changes colour; the rest share the one already set.
						if (shown < 1)
						{
							graphics.setColor(withOpacity(colour, shown));
						}
						graphics.fill(head);
						if (shown < 1)
						{
							graphics.setColor(colour);
						}
					}
				}
			}
		}
	}

	/**
	 * Marks where each shoal is on its route with an arrow pointing the way it's heading.
	 */
	private void drawShoalArrows(Graphics2D graphics, List<PlacedShoal> shoals, long now)
	{
		if (!config.showShoalHeadingArrow())
		{
			return;
		}

		Color colour = config.shoalHeadingArrowColour();
		double length = shoalArrowLength();
		for (PlacedShoal placed : shoals)
		{
			// Fades out while the shoal sits at a stop, and back in as it's about to set off again.
			double opacity = placed.headingOpacity;
			if (opacity <= 0)
			{
				continue;
			}

			// Snap the arrow onto the route, so it slides along the line with the shoal. Near the boat it fades and
			// hides like the rest of the route, but only after staying a moment once the boat comes over it.
			double[] place = onRoute(placed.route, placed.distance, length);
			opacity *= placed.shoal.heldClearShown(clearShown(placed.view, place), now);
			Path2D arrow = opacity <= 0 ? null
				: arrowhead(placed.view, place, length, SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (arrow != null)
			{
				graphics.setColor(withOpacity(colour, opacity));
				graphics.fill(arrow);
			}
		}
	}

	/**
	 * Where an arrow of the given length in tiles sits on a route, as {x, y, dx, dy}: aimed from the route
	 * half its length behind to the route half its length ahead, and centred between the two. On a bend
	 * that puts both its tip and its tail on the line, where following the direction at its middle sends
	 * the tip off the outside of the bend.
	 */
	private static double[] onRoute(ShoalRoute route, double distance, double length)
	{
		double[] behind = route.pointAt(distance - length / 2);
		double[] ahead = route.pointAt(distance + length / 2);
		double dx = ahead[0] - behind[0];
		double dy = ahead[1] - behind[1];
		double across = Math.hypot(dx, dy);
		if (across < 1e-9)
		{
			return route.pointAt(distance);
		}
		return new double[]{(behind[0] + ahead[0]) / 2, (behind[1] + ahead[1]) / 2, dx / across, dy / across};
	}

	/**
	 * An arrowhead lying flat on the water, centred on a point of a route and pointing along it, or
	 * null if any of it can't be drawn. The point is {x, y, dx, dy}, as ShoalRoute.pointAt gives it.
	 * The length is in tiles; the half width and notch are fractions of it.
	 */
	private Path2D arrowhead(WorldView view, double[] at, double length, double halfWidthFraction, double notchFraction)
	{
		double halfLength = length / 2;
		double halfWidth = length * halfWidthFraction;
		double notch = length * notchFraction;
		double x = at[0];
		double y = at[1];
		double dx = at[2];
		double dy = at[3];
		if (arrowStyle == TrawlingPlusConfig.ArrowStyle.STANDING)
		{
			return standingArrowhead(view, x, y, dx, dy, halfLength, halfWidth, notch);
		}
		if (arrowStyle == TrawlingPlusConfig.ArrowStyle.FACING)
		{
			return facingArrowhead(view, x, y, dx, dy, halfLength, halfWidth, notch);
		}
		// Given up on at the first corner that can't be placed, rather than placing the rest for nothing.
		Point tip = toCanvas(view, x + dx * halfLength, y + dy * halfLength);
		Point left = tip == null ? null
			: toCanvas(view, x - dx * halfLength - dy * halfWidth, y - dy * halfLength + dx * halfWidth);
		Point back = left == null ? null : toCanvas(view, x - dx * notch, y - dy * notch);
		Point right = back == null ? null
			: toCanvas(view, x - dx * halfLength + dy * halfWidth, y - dy * halfLength - dx * halfWidth);
		if (right == null)
		{
			return null;
		}

		Path2D.Double arrow = new Path2D.Double();
		arrow.moveTo(tip.getX(), tip.getY());
		arrow.lineTo(left.getX(), left.getY());
		arrow.lineTo(back.getX(), back.getY());
		arrow.lineTo(right.getX(), right.getY());
		arrow.closePath();
		return arrow;
	}

	/**
	 * The same arrowhead stood up along the route, like a sail: its width goes up and down instead of across, its
	 * tip and notch on the route so the line runs through its middle. The same four corners, placed at their heights.
	 */
	private Path2D standingArrowhead(WorldView view, double x, double y, double dx, double dy, double halfLength,
		double halfWidth, double notch)
	{
		Point tip = toCanvas(view, x + dx * halfLength, y + dy * halfLength, 0);
		Point top = tip == null ? null : toCanvas(view, x - dx * halfLength, y - dy * halfLength, halfWidth);
		Point back = top == null ? null : toCanvas(view, x - dx * notch, y - dy * notch, 0);
		Point bottom = back == null ? null : toCanvas(view, x - dx * halfLength, y - dy * halfLength, -halfWidth);
		return bottom == null ? null : path(tip, top, back, bottom);
	}

	/**
	 * The same arrowhead stood up and turned to face the camera, pointing the way the route runs across the screen,
	 * centred on the route. Its size comes from a distance measured across the screen at the arrow, which
	 * perspective never squashes, so it keeps its size whichever way the route runs. Four points placed.
	 */
	private Path2D facingArrowhead(WorldView view, double x, double y, double dx, double dy, double halfLength,
		double halfWidth, double notch)
	{
		// Which way it points and how big it is are both read off places a couple of tiles apart rather than right
		// beside it: the screen only places whole pixels, and over a short gap a pixel's rounding swings the arrow
		// about as the camera moves.
		Point middle = toCanvas(view, x, y);
		if (middle == null)
		{
			return null;
		}
		// Looking along the route from low down, the places ahead and behind land only a few pixels apart, and
		// rounding each to a whole pixel swings the arrow about as the camera moves, so the way across the screen
		// comes from the same sums unrounded.
		if (!screenWay(view, x, y, dx, dy))
		{
			return null;
		}
		double ax = wayX;
		double ay = wayY;
		double perTile = acrossScreen(view, x, y, middle);
		if (perTile < 0)
		{
			return null;
		}
		// Right by the camera, perspective blows a place up far beyond anything else on screen, so no arrow is let
		// grow past a set size against one at the boat.
		if (facingLimit > 0)
		{
			perTile = Math.min(perTile, facingLimit);
		}
		// And far off, it would shrink past reading, so none is let shrink past a set size against one at the boat.
		if (facingMinimum > 0)
		{
			perTile = Math.max(perTile, facingMinimum);
		}
		double apart = Math.hypot(ax, ay);
		// Heading straight towards or away from the camera there is no way across the screen to point, so it
		// points up the screen, the way the route leads off into the distance.
		double ux = apart < 1e-6 ? 0 : ax / apart;
		double uy = apart < 1e-6 ? -1 : ay / apart;
		// Across the arrow on screen. Its middle is on the route, so the line runs through it.
		double nx = -uy;
		double ny = ux;
		double length = halfLength * perTile;
		double width = halfWidth * perTile;
		double cx = middle.getX();
		double cy = middle.getY();

		Path2D.Double arrow = new Path2D.Double();
		arrow.moveTo(cx + ux * length, cy + uy * length);
		arrow.lineTo(cx - ux * length + nx * width, cy - uy * length + ny * width);
		arrow.lineTo(cx - ux * notch * perTile, cy - uy * notch * perTile);
		arrow.lineTo(cx - ux * length - nx * width, cy - uy * length - ny * width);
		arrow.closePath();

		return arrow;
	}

	/**
	 * Which way along the screen a route heading along dx, dy runs at a place on the sea, from FACING_SPAN tiles
	 * behind it to as far ahead, placed the same way the game places things on screen but without rounding either
	 * end to a whole pixel, into wayX and wayY. False when either end is off the loaded map or behind the camera.
	 */
	private boolean screenWay(WorldView view, double x, double y, double dx, double dy)
	{
		double localX = (x - view.getBaseX()) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		double localY = (y - view.getBaseY()) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		double reachX = dx * FACING_SPAN * Perspective.LOCAL_TILE_SIZE;
		double reachY = dy * FACING_SPAN * Perspective.LOCAL_TILE_SIZE;
		if (!onLoadedMap(view, (int) Math.round(localX + reachX), (int) Math.round(localY + reachY))
			|| !onLoadedMap(view, (int) Math.round(localX - reachX), (int) Math.round(localY - reachY)))
		{
			return false;
		}
		double height = view.getTileHeight((int) localX, (int) localY, view.getPlane());
		if (!unroundedCanvas(localX + reachX, localY + reachY, height))
		{
			return false;
		}
		double aheadX = canvasX;
		double aheadY = canvasY;
		if (!unroundedCanvas(localX - reachX, localY - reachY, height))
		{
			return false;
		}
		wayX = aheadX - canvasX;
		wayY = aheadY - canvasY;
		return true;
	}

	/**
	 * Where a place in local units lands on screen, before rounding, measured from the middle of the view, into
	 * canvasX and canvasY. False when it is behind the camera.
	 */
	private boolean unroundedCanvas(double localX, double localY, double height)
	{
		double fx = localX - cameraFpX;
		double fy = localY - cameraFpY;
		double fz = height - cameraFpZ;
		double across = fx * yawCos + fy * yawSin;
		double along = fy * yawCos - fx * yawSin;
		double down = fz * pitchCos - along * pitchSin;
		double depth = along * pitchCos + fz * pitchSin;
		if (depth < 50)
		{
			return false;
		}
		canvasX = across * cameraScale / depth;
		canvasY = down * cameraScale / depth;
		return true;
	}

	/**
	 * How many pixels a tile covers at a place, measured along the ground lying straight across the screen there,
	 * which the camera looking down never squashes: square to the line from the camera to the place, seen from
	 * above. -1 when it can't be placed.
	 */
	private double acrossScreen(WorldView view, double x, double y, Point at)
	{
		double localX = (x - view.getBaseX()) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		double localY = (y - view.getBaseY()) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		double sightX = localX - frameCameraX;
		double sightY = localY - frameCameraY;
		double sight = Math.hypot(sightX, sightY);
		double acrossX = sight < 1e-6 ? 1 : -sightY / sight;
		double acrossY = sight < 1e-6 ? 0 : sightX / sight;
		Point side = at == null ? null : toCanvas(view, x + acrossX * FACING_SPAN, y + acrossY * FACING_SPAN);
		return side == null ? -1 : Math.hypot(side.getX() - at.getX(), side.getY() - at.getY()) / FACING_SPAN;
	}

	private static Path2D path(Point a, Point b, Point c, Point d)
	{
		Path2D.Double path = new Path2D.Double();
		path.moveTo(a.getX(), a.getY());
		path.lineTo(b.getX(), b.getY());
		path.lineTo(c.getX(), c.getY());
		path.lineTo(d.getX(), d.getY());
		path.closePath();
		return path;
	}

	private void drawStop(Graphics2D graphics, WorldView view, ShoalRoute route, int stop, Color colour, double opacity)
	{
		opacity *= clearShown(view, route.stopX(stop), route.stopY(stop));
		if (opacity <= 0)
		{
			return;
		}

		Shape area;
		if (config.stopShape() == TrawlingPlusConfig.FishableShape.CIRCLE)
		{
			area = stopCircle(view, route.stopX(stop), route.stopY(stop));
		}
		else
		{
			LocalPoint local = toLocal(view, route.stopX(stop), route.stopY(stop));
			area = local == null ? null : Perspective.getCanvasTileAreaPoly(client, local, STOP_SIZE);
		}
		if (area != null)
		{
			OverlayUtil.renderPolygon(graphics, area, withOpacity(colour, opacity), withOpacity(STOP_FILL, opacity),
				stopOutline(config.stopThickness().pixels()));
		}
	}

	/**
	 * A round stop around a point in world tiles, or null when too little of it is on the loaded map to draw.
	 */
	private Shape stopCircle(WorldView view, double x, double y)
	{
		return roundOutline(view, x, y, STOP_SIZE / 2.0, STOP_CURVE_POINTS, stopCircle, stopCurve);
	}

	/**
	 * A circle on the water around a point in world tiles, of a radius in tiles, as it lands on screen: a smooth
	 * curve through a few points of it, which is as round as many more at a fraction of the placing. Points off the
	 * loaded map are left out, and the curve goes straight across the gap they leave, so the part that is on it still
	 * draws; null when fewer than three are on it. Into the given polygon and path, reused between frames.
	 */
	private Shape roundOutline(WorldView view, double x, double y, double radius, int points, Polygon corners,
		Path2D.Double curve)
	{
		corners.reset();
		for (int step = 0; step < points; step++)
		{
			int of = step * (AREA_POINTS / points);
			Point edge = toCanvas(view, x + CIRCLE_X[of] * radius, y + CIRCLE_Y[of] * radius);
			if (edge != null)
			{
				circleSteps[corners.npoints] = step;
				corners.addPoint(edge.getX(), edge.getY());
			}
		}
		return corners.npoints < 3 ? null : smoothLoop(corners, circleSteps, points, curve);
	}

	/**
	 * A smooth closed curve through the corners of a polygon placed round a circle of a number of points, into the
	 * given path: each stretch between two corners that are next to each other round the circle is a cubic curve
	 * heading the way the corners either side of it lie (a Catmull-Rom spline), and one across a gap where points
	 * were left out is straight. Which point of the circle each corner is comes from steps.
	 */
	private static Path2D smoothLoop(Polygon corners, int[] steps, int points, Path2D.Double into)
	{
		int[] xs = corners.xpoints;
		int[] ys = corners.ypoints;
		int n = corners.npoints;
		into.reset();
		into.moveTo(xs[0], ys[0]);
		for (int k = 0; k < n; k++)
		{
			int next = (k + 1) % n;
			if (!besideOnCircle(steps[k], steps[next], points))
			{
				into.lineTo(xs[next], ys[next]);
				continue;
			}
			// A neighbour missing from the far side of either end leaves that end heading along the stretch itself.
			int before = (k + n - 1) % n;
			int after = (next + 1) % n;
			if (!besideOnCircle(steps[before], steps[k], points))
			{
				before = k;
			}
			if (!besideOnCircle(steps[next], steps[after], points))
			{
				after = next;
			}
			into.curveTo(
				xs[k] + (xs[next] - xs[before]) / 6.0, ys[k] + (ys[next] - ys[before]) / 6.0,
				xs[next] - (xs[after] - xs[k]) / 6.0, ys[next] - (ys[after] - ys[k]) / 6.0,
				xs[next], ys[next]);
		}
		into.closePath();
		return into;
	}

	private static boolean besideOnCircle(int step, int next, int points)
	{
		return (next - step + points) % points == 1;
	}

	/**
	 * A colour with its own transparency scaled by an opacity from 0 to 1.
	 */
	private static Color withOpacity(Color colour, double opacity)
	{
		return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), (int) Math.round(colour.getAlpha() * opacity));
	}

	/**
	 * How far through its pulse a warning line is, from 0 to 1 and back once a second, so every warning
	 * pulses in time.
	 */
	private static double warningPulse(long now)
	{
		return (1 - Math.cos(2 * Math.PI * (now % HOLD_FULL_PULSE_MILLIS) / HOLD_FULL_PULSE_MILLIS)) / 2;
	}

	/**
	 * A colour part way from one to another, by a fraction from 0 to 1.
	 */
	private static Color blend(Color from, Color to, double fraction)
	{
		return new Color(
			(int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * fraction),
			(int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * fraction),
			(int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * fraction));
	}

	/**
	 * The length of the shoal heading arrow, and of the arrow leading an animated section, in tiles.
	 */
	private double shoalArrowLength()
	{
		return scaled(SHOAL_ARROW_LENGTH, config.shoalHeadingArrowScale());
	}

	/**
	 * Finds where the fishable area is this frame, around the nearest shoal, and says whether there is one. The
	 * shoal's place is kept, so an area that has gone fades out where it last was.
	 */
	private boolean placeFishableArea()
	{
		Shoal shoal = plugin.getNearestShoal();
		WorldView view = shoal == null ? null : shoal.parentView(client);
		double[] at = shoal == null ? null : shoal.position(client);
		if (view == null || at == null)
		{
			return false;
		}
		areaView = view;
		areaX = at[0];
		areaY = at[1];
		return true;
	}

	/**
	 * Moves the fishable area and its dot towards shown or hidden.
	 */
	private void fadeFishableArea(boolean wanted, long now)
	{
		// Not drawn for a while, as when off the boat, so it starts from nothing rather than jumping in.
		if (lastFishingPointMillis < 0 || now - lastFishingPointMillis > FISHING_POINT_FADE_MILLIS * 2)
		{
			fishingPointFade = 0;
			lastFishingPointMillis = now;
		}
		double step = (now - lastFishingPointMillis) / FISHING_POINT_FADE_MILLIS;
		lastFishingPointMillis = now;
		fishingPointFade = wanted ? Math.min(1, fishingPointFade + step) : Math.max(0, fishingPointFade - step);
	}

	/**
	 * Outlines the water the nearest shoal can be fished from, as a square or circle around the shoal. The shape
	 * is centred on the shoal itself, so it travels with the shoal and only sits still because the shoal does.
	 */
	private void drawFishableArea(Graphics2D graphics)
	{
		WorldView view = areaView;
		double[] at = {areaX, areaY};

		// Walked around the shape a point at a time, so one running off the loaded map still draws the
		// part that is on it. The same shape every frame, so it is drawn into the same polygon rather
		// than a new one, off points worked out once. A circle is a smooth curve through a few of its
		// points while they are all on the map.
		Shape outline;
		if (config.fishableAreaShape() == TrawlingPlusConfig.FishableShape.CIRCLE)
		{
			outline = roundOutline(view, at[0], at[1], FISHABLE_REACH, AREA_CURVE_POINTS, area, areaCurve);
		}
		else
		{
			// Just its four corners, which on flat water is the whole square. Only when one can't be placed, as when
			// it is behind a low camera, is it walked round a point at a time so the rest still draws.
			area.reset();
			for (int step = 0; step < AREA_POINTS; step += AREA_POINTS_PER_SIDE)
			{
				Point corner = toCanvas(view, at[0] + SQUARE_X[step] * FISHABLE_REACH, at[1] + SQUARE_Y[step] * FISHABLE_REACH);
				if (corner != null)
				{
					area.addPoint(corner.getX(), corner.getY());
				}
			}
			boolean corners = area.npoints == 4;
			if (!corners)
			{
				area.reset();
				for (int step = 0; step < AREA_POINTS; step++)
				{
					Point edge = toCanvas(view, at[0] + SQUARE_X[step] * FISHABLE_REACH, at[1] + SQUARE_Y[step] * FISHABLE_REACH);
					if (edge != null)
					{
						area.addPoint(edge.getX(), edge.getY());
					}
				}
			}
			outline = corners || area.npoints >= AREA_POINTS / 4 ? area : null;
		}

		if (outline == null)
		{
			// Too little of it is on screen to make a shape out of.
			return;
		}

		// Faded only while fading, rather than making the same colours again every frame.
		boolean faded = fishingPointFade < 1;
		OverlayUtil.renderPolygon(graphics, outline,
			faded ? withOpacity(config.fishableAreaColour(), fishingPointFade) : config.fishableAreaColour(),
			faded ? withOpacity(config.fishableAreaFillColour(), fishingPointFade) : config.fishableAreaFillColour(),
			areaOutline(config.fishableAreaThickness().pixels()));
	}

	/**
	 * A dot on the boat at the point the fishable area is measured to, so the boat can fish the shoal
	 * whenever the dot is inside it. It fades in as the area appears and out as it goes.
	 */
	private void drawFishingPoint(Graphics2D graphics)
	{
		if (fishingPointFade <= 0)
		{
			return;
		}

		WorldEntity boat = plugin.getBoat();
		WorldView deck = boat == null ? null : boat.getWorldView();
		WorldEntityConfig hull = boat == null ? null : boat.getConfig();
		if (deck == null || hull == null)
		{
			return;
		}

		// Down the middle of the boat, which on a hull an odd number of tiles wide is half a tile off the
		// middle of its view, as at the helm.
		int across = deck.getSizeX() * Perspective.LOCAL_TILE_SIZE / 2;
		if ((hull.getBoundsWidth() / Perspective.LOCAL_TILE_SIZE) % 2 != 0)
		{
			across -= Perspective.LOCAL_HALF_TILE_SIZE;
		}
		LocalPoint afloat = boat.transformToMainWorld(new LocalPoint(across,
			deck.getSizeY() * Perspective.LOCAL_TILE_SIZE / 2 - FISHING_POINT_AHEAD, deck));
		WorldView sea = afloat == null ? null : client.getWorldView(afloat.getWorldView());
		if (sea == null)
		{
			return;
		}

		// At the water's height, the same as the area it is read against.
		int height = sea.getTileHeight(afloat.getX(), afloat.getY(), sea.getPlane());
		Point point = Perspective.localToCanvas(client, afloat.getWorldView(), afloat.getX(), afloat.getY(), height);
		if (point == null)
		{
			return;
		}

		graphics.setColor(withOpacity(TrawlingPlusNetOverlay.opaque(config.fishableAreaColour()), fishingPointFade));
		graphics.fillOval(point.getX() - FISHING_POINT_SIZE / 2, point.getY() - FISHING_POINT_SIZE / 2,
			FISHING_POINT_SIZE, FISHING_POINT_SIZE);
	}

	/**
	 * Works out this frame's area around the boat to leave clear, or that there is none: none until the hull's
	 * outline has been read, which is within a tick or so of boarding.
	 */
	private void findClearing(WorldView top)
	{
		clearing = false;
		volume = false;
		WorldEntity boat = config.clearAroundBoat() ? plugin.getBoat() : null;
		WorldView deck = boat == null ? null : boat.getWorldView();
		WorldEntityConfig hull = boat == null ? null : boat.getConfig();
		if (top == null || deck == null || hull == null)
		{
			return;
		}

		clearing = placeFootprint(top, boat, deck, hull);
		if (!clearing)
		{
			return;
		}
		clearX = (footFromX + footToX) / 2;
		clearY = (footFromY + footToY) / 2;
		clearHeightTiles = CLEAR_HEIGHT_BY_BOAT.getOrDefault(hull.getId(), CLEAR_HEIGHT_TILES);
		volume = findVolume(top);
	}

	/**
	 * Places the hull's outline on the water for this frame, reading it from the hull's model first if this boat
	 * type hasn't been read yet. False when there is no outline to be had yet.
	 */
	private boolean placeFootprint(WorldView top, WorldEntity boat, WorldView deck, WorldEntityConfig hull)
	{
		if ((hull.getId() != footprintConfig || deck.getId() != footprintDeckView)
			&& client.getTickCount() != footprintTriedTick)
		{
			// Looked for at most once a tick, so a hull whose model hasn't loaded yet isn't searched for every frame.
			footprintTriedTick = client.getTickCount();
			footprintCount = readFootprint(deck);
			if (footprintCount >= 3)
			{
				footprintConfig = hull.getId();
				footprintDeckView = deck.getId();
			}
		}
		if (footprintCount < 3 || hull.getId() != footprintConfig)
		{
			return false;
		}

		footFromX = footFromY = Double.MAX_VALUE;
		footToX = footToY = -Double.MAX_VALUE;
		for (int k = 0; k < footprintCount; k++)
		{
			LocalPoint afloat = boat.transformToMainWorld(new LocalPoint((int) footLocalX[k], (int) footLocalY[k], deck));
			if (afloat == null)
			{
				return false;
			}
			footX[k] = top.getBaseX() + (double) (afloat.getX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE;
			footY[k] = top.getBaseY() + (double) (afloat.getY() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE;
			footFromX = Math.min(footFromX, footX[k]);
			footFromY = Math.min(footFromY, footY[k]);
			footToX = Math.max(footToX, footX[k]);
			footToY = Math.max(footToY, footY[k]);
		}
		return true;
	}

	/**
	 * Reads the hull's outline from above off its model into footLocalX/Y, in the deck's local units, and says how
	 * many corners it has, or 0 if the hull can't be found yet. The outline comes from the object on the deck's
	 * lowest level covering the most tiles, the one with the most points if two tie: the deck floor on a sloop
	 * and a skiff, which spans the whole hull. Done once per boat type, not every frame.
	 */
	/**
	 * Reads, once per boat type, where the display goes at the bow and above the sails: the front of the hull's
	 * outline on the deck floor, and the height of the top of the tallest thing on the deck, the mast or its sails. True once they are known for this boat; the deck is looked through at most once a tick until then,
	 * while its models load.
	 */
	private boolean readAnchors(WorldEntity boat, WorldView deck, WorldEntityConfig hull)
	{
		if (hull.getId() == anchorConfig && deck.getId() == anchorDeckView)
		{
			return true;
		}
		if (client.getTickCount() == anchorTriedTick)
		{
			return false;
		}
		anchorTriedTick = client.getTickCount();

		Tile[][][] planes = deck.getScene().getTiles();
		GameObject floor = null;
		Model floorModel = null;
		int floorArea = 0;
		GameObject tallest = null;
		int tallestTop = Integer.MIN_VALUE;
		for (int plane = 0; plane < planes.length; plane++)
		{
			for (Tile[] row : planes[plane])
			{
				for (Tile tile : row)
				{
					for (GameObject object : tile == null ? NO_OBJECTS : tile.getGameObjects())
					{
						Renderable renderable = object == null ? null : object.getRenderable();
						Model model = renderable == null ? null
							: renderable instanceof Model ? (Model) renderable : renderable.getModel();
						if (model == null || model.getVerticesCount() == 0)
						{
							continue;
						}

						// How far above the water its highest point is: the game counts heights upwards as negative.
						float highest = Float.MAX_VALUE;
						float[] heights = model.getVerticesY();
						for (int v = 0; v < model.getVerticesCount(); v++)
						{
							highest = Math.min(highest, heights[v]);
						}
						int top = -(object.getZ() + (int) highest);
						if (top > tallestTop)
						{
							tallestTop = top;
							tallest = object;
						}

						int area = object.sizeX() * object.sizeY();
						if (plane == 0 && (area > floorArea || area == floorArea && model.getVerticesCount() > floorModel.getVerticesCount()))
						{
							floor = object;
							floorModel = model;
							floorArea = area;
						}
					}
				}
			}
		}
		if (floor == null || tallest == null)
		{
			return false;
		}

		// The front of the floor, the lowest point of its model up the deck turned the way it faces, a little way
		// back so the display sits over the boat rather than past its tip.
		int turned = floor.getOrientation() & 2047;
		int sin = Perspective.SINE[turned];
		int cos = Perspective.COSINE[turned];
		float[] xs = floorModel.getVerticesX();
		float[] zs = floorModel.getVerticesZ();
		int front = Integer.MAX_VALUE;
		for (int v = 0; v < floorModel.getVerticesCount(); v++)
		{
			front = Math.min(front, floor.getLocalLocation().getY() + (int) ((zs[v] * cos - xs[v] * sin) / 65536));
		}
		bowLocalY = front + BOW_INSET;
		sailTop = tallestTop;
		anchorConfig = hull.getId();
		anchorDeckView = deck.getId();
		return true;
	}

	private int readFootprint(WorldView deck)
	{
		Tile[][][] planes = deck.getScene().getTiles();
		if (planes.length == 0)
		{
			return 0;
		}

		GameObject best = null;
		Model bestModel = null;
		int bestArea = 0;
		for (Tile[] row : planes[0])
		{
			for (Tile tile : row)
			{
				for (GameObject object : tile == null ? NO_OBJECTS : tile.getGameObjects())
				{
					Renderable renderable = object == null ? null : object.getRenderable();
					Model model = renderable == null ? null
						: renderable instanceof Model ? (Model) renderable : renderable.getModel();
					if (model == null || model.getVerticesCount() == 0)
					{
						continue;
					}
					int area = object.sizeX() * object.sizeY();
					if (area > bestArea || area == bestArea && model.getVerticesCount() > bestModel.getVerticesCount())
					{
						best = object;
						bestModel = model;
						bestArea = area;
					}
				}
			}
		}
		if (best == null)
		{
			return 0;
		}

		// Its points flattened onto the deck, turned the way the object faces, then outlined.
		int count = bestModel.getVerticesCount();
		float[] xs = bestModel.getVerticesX();
		float[] zs = bestModel.getVerticesZ();
		int turned = best.getOrientation() & 2047;
		int sin = Perspective.SINE[turned];
		int cos = Perspective.COSINE[turned];
		int[] flatX = new int[count];
		int[] flatY = new int[count];
		for (int v = 0; v < count; v++)
		{
			flatX[v] = best.getLocalLocation().getX() + (int) ((zs[v] * sin + xs[v] * cos) / 65536);
			flatY[v] = best.getLocalLocation().getY() + (int) ((zs[v] * cos - xs[v] * sin) / 65536);
		}
		Polygon outline = new Polygon();
		outline(flatX, flatY, count, new int[count], new int[count * 2], outline);

		// Thinned to the fewest corners that keep its shape, since each is moved with the boat and placed on screen
		// every frame: again and again, the corner whose loss changes the outline least goes, which is the one
		// making the smallest triangle with its neighbours. The bow's point and the stern's corners make big ones,
		// so they stay. Done once per boat type.
		int kept = outline.npoints;
		int[] keptX = Arrays.copyOf(outline.xpoints, kept);
		int[] keptY = Arrays.copyOf(outline.ypoints, kept);
		while (kept > FOOTPRINT_POINTS)
		{
			int least = 0;
			double leastArea = Double.MAX_VALUE;
			for (int k = 0; k < kept; k++)
			{
				int before = (k + kept - 1) % kept;
				int after = (k + 1) % kept;
				double area = Math.abs((double) (keptX[k] - keptX[before]) * (keptY[after] - keptY[before])
					- (double) (keptY[k] - keptY[before]) * (keptX[after] - keptX[before]));
				if (area < leastArea)
				{
					leastArea = area;
					least = k;
				}
			}
			System.arraycopy(keptX, least + 1, keptX, least, kept - least - 1);
			System.arraycopy(keptY, least + 1, keptY, least, kept - least - 1);
			kept--;
		}
		for (int k = 0; k < kept; k++)
		{
			footLocalX[k] = keptX[k];
			footLocalY[k] = keptY[k];
		}
		return kept;
	}

	/**
	 * Outlines on screen the column the hull's outline makes when stood up to this boat's height, and notes where
	 * the boat and camera are, for telling what the boat stands in front of. False when there is no height, or too
	 * little of it can be placed.
	 */
	private boolean findVolume(WorldView top)
	{
		int height = (int) Math.round(clearHeightTiles * Perspective.LOCAL_TILE_SIZE);
		if (height <= 0)
		{
			// No height, so no volume: the outline on the water covers it.
			return false;
		}
		int count = 0;
		for (int step = 0; step < footprintCount; step++)
		{
			LocalPoint local = toLocal(top, footX[step], footY[step]);
			if (local == null)
			{
				continue;
			}
			int water = top.getTileHeight(local.getX(), local.getY(), top.getPlane());
			Point low = Perspective.localToCanvas(client, top.getId(), local.getX(), local.getY(), water);
			Point high = Perspective.localToCanvas(client, top.getId(), local.getX(), local.getY(), water - height);
			if (low != null)
			{
				ringX[count] = low.getX();
				ringY[count++] = low.getY();
			}
			if (high != null)
			{
				ringX[count] = high.getX();
				ringY[count++] = high.getY();
			}
		}
		if (count < 3)
		{
			return false;
		}

		outline(ringX, ringY, count, ringOrder, ringHull, silhouette);
		centreLocalX = (clearX - top.getBaseX()) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		centreLocalY = (clearY - top.getBaseY()) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		cameraX = client.getCameraX();
		cameraY = client.getCameraY();
		volumeBaseX = top.getBaseX();
		volumeBaseY = top.getBaseY();

		// How many pixels the fade covers at the boat right now, from a tile measured there, so the fade at the
		// outline's edge keeps about the same width as the camera zooms.
		Point from = toCanvas(top, clearX, clearY);
		Point to = toCanvas(top, clearX + 1, clearY);
		double perTile = from == null || to == null ? 0 : Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
		silhouetteBand = Math.max(1, perTile * CLEAR_FADE_TILES);
		fadeBounds.setBounds(silhouette.getBounds());
		fadeBounds.grow((int) Math.ceil(silhouetteBand), (int) Math.ceil(silhouetteBand));
		return silhouette.npoints >= 3;
	}

	/**
	 * The outline round a set of points, going round them once, into the given polygon: Andrew's monotone chain.
	 */
	private static void outline(int[] xs, int[] ys, int count, int[] order, int[] hull, Polygon into)
	{
		// Sorted left to right by insertion into arrays kept between frames: a few dozen points, most of them
		// already close to in order from going round the ring.
		for (int i = 0; i < count; i++)
		{
			int j = i;
			while (j > 0 && (xs[order[j - 1]] > xs[i] || xs[order[j - 1]] == xs[i] && ys[order[j - 1]] > ys[i]))
			{
				order[j] = order[j - 1];
				j--;
			}
			order[j] = i;
		}

		int size = 0;
		for (int pass = 0; pass < 2; pass++)
		{
			// Each pass builds its own half from where the last one stopped, and needs two of its own points
			// before it can look at a turn.
			int floor = size + 2;
			for (int k = 0; k < count; k++)
			{
				int i = order[pass == 0 ? k : count - 1 - k];
				while (size >= floor && turn(xs, ys, hull[size - 2], hull[size - 1], i) <= 0)
				{
					size--;
				}
				hull[size++] = i;
			}
			size--;
		}

		into.reset();
		for (int k = 0; k < size; k++)
		{
			into.addPoint(xs[hull[k]], ys[hull[k]]);
		}
	}

	private static long turn(int[] xs, int[] ys, int a, int b, int c)
	{
		return (long) (xs[b] - xs[a]) * (ys[c] - ys[a]) - (long) (ys[b] - ys[a]) * (xs[c] - xs[a]);
	}

	/**
	 * Whether a point in world tiles is further from the camera than the boat's middle,
	 * so the boat can stand in front of it. Cheap, so asked before placing anything on screen to test it.
	 */
	private boolean behindBoat(double x, double y)
	{
		if (!volume)
		{
			return false;
		}
		double localX = (x - volumeBaseX) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		double localY = (y - volumeBaseY) * Perspective.LOCAL_TILE_SIZE + Perspective.LOCAL_HALF_TILE_SIZE;
		return (localX - centreLocalX) * (centreLocalX - cameraX) + (localY - centreLocalY) * (centreLocalY - cameraY) > 0;
	}

	/**
	 * How much of an arrow or stop at a point to show, from 0 inside the hull's outline to 1 once CLEAR_FADE_TILES
	 * outside it. Always 1 when nothing is being left clear.
	 */
	private double clearFade(double x, double y)
	{
		return clearing ? footprintFade(x, y) : 1;
	}

	/**
	 * How much of an arrow centred at a point, {x, y, ...} in world tiles, to show near the boat: faded by the
	 * hull's outline, and not at all where the boat stands in front of it.
	 */
	private double clearShown(WorldView view, double[] place)
	{
		return clearShown(view, place[0], place[1]);
	}

	/**
	 * How much of an arrow or stop centred at a point in world tiles to show near the boat: faded by the hull's outline, and
	 * where the boat stands in front of it, faded by how far outside the boat's outline on screen it lands, over
	 * about the same width as the fade on the water.
	 */
	private double clearShown(WorldView view, double x, double y)
	{
		double shown = clearFade(x, y);
		if (shown > 0 && behindBoat(x, y))
		{
			shown = Math.min(shown, silhouetteFade(toCanvas(view, x, y)));
		}
		return shown;
	}

	/**
	 * The same for a point of the route line already placed on screen, while the boat is being left clear.
	 */
	private double clearShown(double x, double y, Point canvas)
	{
		double shown = footprintFade(x, y);
		if (shown > 0 && behindBoat(x, y))
		{
			shown = Math.min(shown, silhouetteFade(canvas));
		}
		return shown;
	}

	/**
	 * From 0 inside the boat's outline on screen to 1 once silhouetteBand pixels outside it, eased.
	 */
	private double silhouetteFade(Point canvas)
	{
		if (canvas == null || !fadeBounds.contains(canvas.getX(), canvas.getY()))
		{
			return 1;
		}
		if (silhouette.contains(canvas.getX(), canvas.getY()))
		{
			return 0;
		}

		double nearest = Double.MAX_VALUE;
		for (int k = 0; k < silhouette.npoints; k++)
		{
			int next = (k + 1) % silhouette.npoints;
			nearest = Math.min(nearest, Line2D.ptSegDistSq(silhouette.xpoints[k], silhouette.ypoints[k],
				silhouette.xpoints[next], silhouette.ypoints[next], canvas.getX(), canvas.getY()));
		}
		double fraction = Math.min(1, Math.sqrt(nearest) / silhouetteBand);
		return fraction * fraction * (3 - 2 * fraction);
	}

	/**
	 * Whether a point in world tiles is inside the hull's outline this frame: a crossing count, after the box.
	 */
	private boolean inFootprint(double x, double y)
	{
		if (x < footFromX || x > footToX || y < footFromY || y > footToY)
		{
			return false;
		}
		boolean inside = false;
		for (int k = 0, j = footprintCount - 1; k < footprintCount; j = k++)
		{
			if ((footY[k] > y) != (footY[j] > y)
				&& x < (footX[j] - footX[k]) * (y - footY[k]) / (footY[j] - footY[k]) + footX[k])
			{
				inside = !inside;
			}
		}
		return inside;
	}

	/**
	 * From 0 inside the hull's outline to 1 once CLEAR_FADE_TILES outside it, eased, by the distance to its
	 * nearest edge.
	 */
	private double footprintFade(double x, double y)
	{
		if (x < footFromX - CLEAR_FADE_TILES || x > footToX + CLEAR_FADE_TILES
			|| y < footFromY - CLEAR_FADE_TILES || y > footToY + CLEAR_FADE_TILES)
		{
			return 1;
		}
		if (inFootprint(x, y))
		{
			return 0;
		}
		double nearest = Double.MAX_VALUE;
		for (int k = 0, j = footprintCount - 1; k < footprintCount; j = k++)
		{
			nearest = Math.min(nearest, Line2D.ptSegDistSq(footX[j], footY[j], footX[k], footY[k], x, y));
		}
		double fraction = Math.min(1, Math.sqrt(nearest) / CLEAR_FADE_TILES);
		return fraction * fraction * (3 - 2 * fraction);
	}

	/**
	 * Adds the next point of a route line, in world tiles, faded out where it passes under or behind the boat when
	 * that is to be left clear, the same as arrows and stops. A piece of line that comes near the boat is split into
	 * short steps so the fade follows the outline smoothly; the rest is added as it is.
	 */
	private void addPoint(Line line, WorldView view, double x, double y)
	{
		Point here = toCanvas(view, x, y);
		if (!clearing)
		{
			line.add(here);
			return;
		}
		if (line.hasLast && nearClear(line.lastX, line.lastY, line.lastCanvas, x, y, here))
		{
			double fromX = line.lastX;
			double fromY = line.lastY;
			int steps = Math.max(1, (int) Math.ceil(Math.hypot(x - fromX, y - fromY) / LINE_FADE_STEP_TILES));
			for (int step = 1; step < steps; step++)
			{
				double stepX = fromX + (x - fromX) * step / steps;
				double stepY = fromY + (y - fromY) * step / steps;
				addFadedPoint(line, stepX, stepY, toCanvas(view, stepX, stepY));
			}
		}
		addFadedPoint(line, x, y, here);
	}

	/**
	 * Adds a point of the route line while the boat is being left clear: into the line as usual where it and the
	 * last point are fully shown, and otherwise as a piece of its own, blending from how much of one end shows to
	 * how much of the other does.
	 */
	private void addFadedPoint(Line line, double x, double y, Point here)
	{
		double shown = here == null ? 0 : clearShown(x, y, here);
		if (here == null)
		{
			line.add(null);
		}
		else if (!line.hasLast || line.lastCanvas == null)
		{
			line.add(shown >= LINE_FULLY_SHOWN ? here : null);
		}
		else if (shown >= LINE_FULLY_SHOWN && line.lastShown >= LINE_FULLY_SHOWN)
		{
			if (!line.connected)
			{
				line.add(line.lastCanvas);
			}
			line.add(here);
		}
		else
		{
			line.add(null);
			if (shown > 0 || line.lastShown > 0)
			{
				line.fade(line.lastCanvas, here, line.lastShown, shown);
			}
		}
		line.lastX = x;
		line.lastY = y;
		line.lastCanvas = here;
		line.lastShown = shown;
		line.hasLast = true;
	}

	/**
	 * Whether the piece of route line between two points could come within the fade round the boat: near the hull's
	 * outline on the water, or behind the boat and near its outline on screen.
	 */
	private boolean nearClear(double fromX, double fromY, Point fromCanvas, double toX, double toY, Point toCanvas)
	{
		if (Math.max(fromX, toX) >= footFromX - CLEAR_FADE_TILES && Math.min(fromX, toX) <= footToX + CLEAR_FADE_TILES
			&& Math.max(fromY, toY) >= footFromY - CLEAR_FADE_TILES && Math.min(fromY, toY) <= footToY + CLEAR_FADE_TILES)
		{
			return true;
		}
		if (fromCanvas == null || toCanvas == null || !behindBoat(fromX, fromY) && !behindBoat(toX, toY))
		{
			return false;
		}
		int left = Math.min(fromCanvas.getX(), toCanvas.getX());
		int top = Math.min(fromCanvas.getY(), toCanvas.getY());
		return fadeBounds.intersects(left, top, Math.abs(toCanvas.getX() - fromCanvas.getX()) + 1,
			Math.abs(toCanvas.getY() - fromCanvas.getY()) + 1);
	}

	private Stroke stopOutline(int pixels)
	{
		if (stopStroke == null || pixels != stopThickness)
		{
			stopThickness = pixels;
			stopStroke = new BasicStroke(pixels);
		}
		return stopStroke;
	}

	/**
	 * Makes the route line's strokes afresh only when its thickness setting changes.
	 */
	private void lineStrokes(int pixels)
	{
		if (lineStroke == null || pixels != lineThickness)
		{
			lineThickness = pixels;
			lineStroke = new BasicStroke(pixels, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
			fadedLineStroke = new BasicStroke(pixels, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
		}
	}

	private Stroke areaOutline(int pixels)
	{
		if (areaStroke == null || pixels != areaThickness)
		{
			areaThickness = pixels;
			areaStroke = new BasicStroke(pixels);
		}
		return areaStroke;
	}

	/**
	 * An arrow's length in tiles, from its length at 100% and an arrow scaling setting.
	 */
	private static double scaled(double length, int percent)
	{
		return length * Math.max(TrawlingPlusConfig.MIN_ARROW_SCALE, Math.min(TrawlingPlusConfig.MAX_ARROW_SCALE, percent)) / 100;
	}

	/**
	 * World tile coordinates on the canvas, raised the given number of tiles above the water, or null.
	 */
	private Point toCanvas(WorldView view, double x, double y, double raised)
	{
		LocalPoint local = toLocal(view, x, y);
		if (local == null)
		{
			return null;
		}
		int height = view.getTileHeight(local.getX(), local.getY(), view.getPlane())
			- (int) Math.round(raised * Perspective.LOCAL_TILE_SIZE);
		return Perspective.localToCanvas(client, view.getId(), local.getX(), local.getY(), height);
	}

	private Point toCanvas(WorldView view, double x, double y)
	{
		LocalPoint local = toLocal(view, x, y);
		if (local == null)
		{
			return null;
		}

		// The world view's own height lookup reaches into the extra map loaded around the scene; the one
		// Perspective.localToCanvas(client, local, plane) uses stops at the scene's edge and gives 0 beyond it.
		int height = view.getTileHeight(local.getX(), local.getY(), view.getPlane());
		return Perspective.localToCanvas(client, view.getId(), local.getX(), local.getY(), height);
	}

	/**
	 * Converts world tile coordinates to a local point, or null if they're outside the loaded map.
	 */
	private LocalPoint toLocal(WorldView view, double x, double y)
	{
		int localX = (int) Math.round((x - view.getBaseX()) * Perspective.LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE;
		int localY = (int) Math.round((y - view.getBaseY()) * Perspective.LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE;
		return onLoadedMap(view, localX, localY) ? new LocalPoint(localX, localY, view) : null;
	}

	/**
	 * Whether a place in local units is inside the loaded map, the scene and any extra map loaded around it.
	 */
	private boolean onLoadedMap(WorldView view, int localX, int localY)
	{
		int beyond = (view.isTopLevel() ? frameBeyond : 0) * Perspective.LOCAL_TILE_SIZE;
		return localX >= -beyond && localY >= -beyond
			&& localX < view.getSizeX() * Perspective.LOCAL_TILE_SIZE + beyond
			&& localY < view.getSizeY() * Perspective.LOCAL_TILE_SIZE + beyond;
	}

	/**
	 * How many tiles of map are loaded beyond each edge of a world view's scene. The GPU and 117 HD
	 * plugins' extended map loading adds whole chunks around the top-level scene, up to the extended
	 * scene's size; other world views have none.
	 */
	private int tilesBeyondScene(WorldView view)
	{
		if (!view.isTopLevel())
		{
			return 0;
		}
		int most = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
		return Math.max(0, Math.min(most, client.getExpandedMapLoading() * Constants.CHUNK_SIZE));
	}

	/**
	 * A shoal matched to a route, with where it is this frame, how far round its route that is, and the
	 * stop it's heading for. Placing a shoal searches its whole route, so it's done once per frame.
	 */
	private static final class PlacedShoal
	{
		final Shoal shoal;
		final ShoalRoute route;
		final WorldView view;
		final double distance;
		final int next;
		// How far faded in the heading arrow is this frame, which the stop just reached fades out with.
		final double headingOpacity;

		PlacedShoal(Shoal shoal, ShoalRoute route, WorldView view, double[] position, long now)
		{
			this.shoal = shoal;
			this.route = route;
			this.view = view;
			distance = shoal.followRoute(position);
			next = route.nextStop(distance);
			headingOpacity = shoal.headingArrowOpacity(now);
		}
	}

	/**
	 * A line built point by point on the canvas. A point that can't be drawn breaks the line, and
	 * the next one that can starts a new stretch.
	 */
	private static final class Line
	{
		private final Path2D.Double path = new Path2D.Double();
		private boolean connected;
		// The last point added, in world tiles, for cutting the line at the edge of the boat's clear area.
		private boolean hasLast;
		private double lastShown;
		private Point lastCanvas;
		private double lastX;
		private double lastY;
		// Pieces faded near the boat, drawn on their own: from x, y, to x, y, and how much of each end shows.
		private double[] faded = new double[0];
		private int fadedCount;

		void add(Point point)
		{
			if (point == null)
			{
				connected = false;
			}
			else if (connected)
			{
				path.lineTo(point.getX(), point.getY());
			}
			else
			{
				path.moveTo(point.getX(), point.getY());
				connected = true;
			}
		}

		void fade(Point from, Point to, double fromShown, double toShown)
		{
			if (from.getX() == to.getX() && from.getY() == to.getY())
			{
				return;
			}
			if (fadedCount + 6 > faded.length)
			{
				faded = Arrays.copyOf(faded, Math.max(48, faded.length * 2));
			}
			faded[fadedCount++] = from.getX();
			faded[fadedCount++] = from.getY();
			faded[fadedCount++] = to.getX();
			faded[fadedCount++] = to.getY();
			faded[fadedCount++] = fromShown;
			faded[fadedCount++] = toShown;
		}
	}
}
