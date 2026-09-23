package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class TrawlingPlusOverlay extends Overlay
{
	// Stops are drawn as a square this many tiles across, roughly the size of a shoal.
	private static final int STOP_SIZE = 3;

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
	private static final int HELM_LINES = 5;

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
	private Stroke areaStroke;
	private int areaThickness;
	// The same for the stops' outline, which every stop is drawn with.
	private Stroke stopStroke;
	private int stopThickness;

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
	private ShoalRoute loadedRoute;
	private boolean loadedEnough;

	// How far faded in the fishing point dot is, and when that was last worked out.
	private double fishingPointFade;
	private long lastFishingPointMillis = -1;

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;

	// How far faded in each line of the display at the helm is, by line.
	private final double[] helmFades = new double[HELM_LINES];
	private long lastHelmFadeMillis = -1;
	private ShoalDepth fadingDepth = ShoalDepth.UNKNOWN;

	@Inject
	TrawlingPlusOverlay(Client client, TrawlingPlusPlugin plugin, TrawlingPlusConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView top = client.getTopLevelWorldView();
		frameBeyond = top == null ? 0 : tilesBeyondScene(top);
		// Routes included: there is no point being shown where the fish are by a boat that cannot
		// catch them, though how strict to be about that is the Show guides setting.
		if (client.getGameState() != GameState.LOGGED_IN || !plugin.showGuides())
		{
			return null;
		}

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
		boolean areaDrawn = config.showFishableArea() && drawFishableArea(graphics);
		// Switched off, the dot goes at once like anything else; it only fades as the area comes and goes.
		if (config.showFishableArea() && config.showFishingPoint())
		{
			drawFishingPoint(graphics, areaDrawn, now);
		}
		else
		{
			fishingPointFade = 0;
			lastFishingPointMillis = -1;
		}
		drawHelm(graphics);

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
					continue;
				}

				for (int sample = route.blockFrom(block); sample < route.blockTo(block); sample++)
				{
					line.add(toCanvas(view, route.sampleX(sample), route.sampleY(sample)));
				}
			}

			// Closing the loop, so the last run joins back onto the first.
			line.add(toCanvas(view, route.sampleX(0), route.sampleY(0)));
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
			Path2D tip = arrowhead(view, onRoute(route, placed.distance + drawn, tipLength), tipLength,
				SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (tip != null)
			{
				graphics.setColor(withOpacity(config.directionArrowColour(), opacity));
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
			Path2D tip = arrowhead(view, onRoute(route, distance + drawn, tipLength), tipLength, SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (tip != null)
			{
				graphics.setColor(withOpacity(config.directionArrowColour(), tipOpacity));
				graphics.fill(tip);
			}
		}
	}

	/**
	 * Draws the display at the helm of the player's boat: the depth, time left at the stop, fish in the
	 * nets, bait and the hold full warning, each line fading in and out on its own.
	 */
	private void drawHelm(Graphics2D graphics)
	{
		if (!config.showHeadsUpDisplay())
		{
			return;
		}

		// Each line of the display stands on its own. Only the depth waits on a depth being known; the
		// rest have nothing to do with it and are not held up by it.
		WorldEntity boat = plugin.getBoat();
		ShoalDepth depth = boat == null ? ShoalDepth.UNKNOWN : plugin.getNearestDepth();
		if (depth != ShoalDepth.UNKNOWN)
		{
			// Kept while the text fades out, so it does not change word on the way.
			fadingDepth = depth;
		}

		double seconds = boat == null ? -1 : plugin.getSecondsAtStop();
		long now = System.currentTimeMillis();
		boolean[] wanted = new boolean[HELM_LINES];
		wanted[DEPTH_LINE] = boat != null && config.showShoalDepth() && depth != ShoalDepth.UNKNOWN;
		wanted[TIME_LINE] = boat != null && config.showTimeAtStop() && seconds >= 0;
		wanted[BAITED_LINE] = boat != null && config.showBaited() && (plugin.isBaited() || plugin.isOutOfBait());
		wanted[FISH_LINE] = boat != null && config.showFishInNets() && plugin.netsFitted() && plugin.fishLineWanted(now);
		wanted[HOLD_LINE] = boat != null && config.showFishInNets() && plugin.isHoldFull();

		// A line coming or going while another holds the pill up is a change inside something already
		// on screen, so it happens at once. The fade is for the display itself arriving or leaving.
		double step = lastHelmFadeMillis < 0 ? 0
			: Math.max(0, now - lastHelmFadeMillis) / HELM_FADE_MILLIS;
		boolean[] wasUp = new boolean[HELM_LINES];
		for (int line = 0; line < HELM_LINES; line++)
		{
			wasUp[line] = helmFades[line] > 0;
		}

		double[] opacity = new double[HELM_LINES];
		for (int line = 0; line < HELM_LINES; line++)
		{
			// Another line already holding the pill up means this one is changing inside a display that
			// is already on screen, so it changes at once rather than fading.
			opacity[line] = fade(line, wanted[line], step, othersUp(wasUp, line));
		}
		lastHelmFadeMillis = now;

		WorldView deck = boat == null ? null : boat.getWorldView();
		WorldEntityConfig hull = boat == null ? null : boat.getConfig();
		if (deck == null || hull == null)
		{
			return;
		}

		// The lines that are actually showing, bottom first, so a gap in the middle closes up.
		String[] text = new String[HELM_LINES];
		Color[] colour = new Color[HELM_LINES];
		double[] showing = new double[HELM_LINES];
		int[] width = new int[HELM_LINES];
		FontMetrics letters = graphics.getFontMetrics();
		boolean ticked = opacity[DEPTH_LINE] > 0 && config.showDepthTick() && plugin.isAtDepth();
		int count = 0;
		int depthAt = -1;

		if (opacity[DEPTH_LINE] > 0 && fadingDepth != ShoalDepth.UNKNOWN)
		{
			depthAt = count;
			text[count] = fadingDepth.toString();
			colour[count] = depthColour(fadingDepth);
			showing[count] = opacity[DEPTH_LINE];
			width[count] = letters.stringWidth(text[count])
				+ (ticked ? TICK_GAP + TrawlingPlusNetOverlay.TICK_WIDTH : 0);
			count++;
		}
		if (opacity[BAITED_LINE] > 0)
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
		if (opacity[FISH_LINE] > 0)
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
		if (opacity[TIME_LINE] > 0)
		{
			text[count] = Math.max(0, Math.round(seconds)) + "s";
			colour[count] = TrawlingPlusNetOverlay.opaque(config.timeLeftColour());
			showing[count] = opacity[TIME_LINE];
			width[count] = letters.stringWidth(text[count]);
			count++;
		}
		if (opacity[HOLD_LINE] > 0)
		{
			// Eased back and forth rather than blinking, starting and ending on the first colour.
			double pulse = warningPulse(now);
			text[count] = HOLD_FULL;
			colour[count] = blend(HOLD_FULL_COLOUR, HOLD_FULL_PULSE_COLOUR, pulse);
			showing[count] = opacity[HOLD_LINE];
			width[count] = letters.stringWidth(HOLD_FULL);
			count++;
		}

		if (count == 0)
		{
			return;
		}

		// The helm sits a quarter of the hull length behind the middle of the boat, and the text a
		// tile and a half further back again. A hull an odd number of tiles wide has its middle
		// tile half a tile off the middle of the view.
		int across = deck.getSizeX() * Perspective.LOCAL_TILE_SIZE / 2;
		if ((hull.getBoundsWidth() / Perspective.LOCAL_TILE_SIZE) % 2 != 0)
		{
			across -= Perspective.LOCAL_HALF_TILE_SIZE;
		}
		LocalPoint atStern = new LocalPoint(across,
			deck.getSizeY() * Perspective.LOCAL_TILE_SIZE / 2 + hull.getBoundsHeight() / 4
				+ Perspective.LOCAL_TILE_SIZE * 3 / 2, deck);
		LocalPoint afloatAtStern = boat.transformToMainWorld(atStern);
		if (afloatAtStern == null)
		{
			return;
		}

		Point at = Perspective.getCanvasTextLocation(client, graphics, afloatAtStern, text[0],
			HELM_TEXT_HEIGHT);
		if (at == null)
		{
			return;
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
		int left = at.getX() + (letters.stringWidth(text[0]) - wide) / 2;
		int top = at.getY() - letters.getAscent() - (count - 1) * lineHeight;

		// One pill around the lot, as opaque as whichever line is showing the most, so it does not
		// flicker as one of them fades while the others stay.
		graphics.setColor(withOpacity(HELM_BACKGROUND, solid));
		graphics.fillRoundRect(left - HELM_PADDING, top - HELM_PADDING,
			wide + HELM_PADDING * 2, lineHeight * count + HELM_PADDING * 2, 6, 6);

		for (int line = 0; line < count; line++)
		{
			int from = left + (wide - width[line]) / 2;
			int baseline = at.getY() - line * lineHeight;
			line(graphics, text[line], from, baseline, colour[line], showing[line]);
			if (line == depthAt && ticked)
			{
				TrawlingPlusNetOverlay.drawTick(graphics,
					from + letters.stringWidth(text[line]) + TICK_GAP,
					baseline - letters.getAscent() / 2,
					withOpacity(TrawlingPlusNetOverlay.TICK, showing[line]));
			}
		}
	}

	/**
	 * Draws one piece of a route's line.
	 */
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
			line.add(toCanvas(view, start[0], start[1]));
			for (int step = 0; step < steps; step++)
			{
				int sample = (from + step) % route.sampleCount();
				if (route.forward(distance, route.sampleDistance(sample)) >= drawn)
				{
					break;
				}
				line.add(toCanvas(view, route.sampleX(sample), route.sampleY(sample)));
			}

			// Ends along the route too, level with the stop once it has drawn all the way there, rather than
			// at the stop itself: a smoothed route passes beside a stop, and joining the two leaves a hook.
			double[] end = route.pointAt(distance + drawn);
			line.add(toCanvas(view, end[0], end[1]));
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
		graphics.setStroke(new BasicStroke(config.routeLineThickness().pixels(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.draw(line.path);
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
		graphics.setColor(opacity >= 1 ? config.directionArrowColour() : withOpacity(config.directionArrowColour(), opacity));

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
					Path2D head = arrowhead(view, onRoute(route, at, length), length, ARROW_HALF_WIDTH, ARROW_NOTCH);
					if (head != null)
					{
						graphics.fill(head);
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

			// Snap the arrow onto the route, so it slides along the line with the shoal.
			Path2D arrow = arrowhead(placed.view, onRoute(placed.route, placed.distance, length), length, SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
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

	private void drawStop(Graphics2D graphics, WorldView view, ShoalRoute route, int stop, Color colour, double opacity)
	{
		LocalPoint local = toLocal(view, route.stopX(stop), route.stopY(stop));
		Polygon area = local == null ? null : Perspective.getCanvasTileAreaPoly(client, local, STOP_SIZE);
		if (area != null)
		{
			OverlayUtil.renderPolygon(graphics, area, withOpacity(colour, opacity), withOpacity(STOP_FILL, opacity),
				stopOutline(config.stopThickness().pixels()));
		}
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
	 * Outlines the water the nearest shoal can be fished from, as a square or circle around the shoal,
	 * and says whether it drew it. The shape is centred on the shoal itself, so it travels with the shoal
	 * and only sits still because the shoal does.
	 */
	private boolean drawFishableArea(Graphics2D graphics)
	{
		Shoal shoal = plugin.getNearestShoal();
		WorldView view = shoal == null ? null : shoal.parentView(client);
		double[] at = shoal == null ? null : shoal.position(client);
		if (view == null || at == null)
		{
			return false;
		}

		// Walked around the shape a point at a time, so one running off the loaded map still draws the
		// part that is on it. The same shape every frame, so it is drawn into the same polygon rather
		// than a new one, off points worked out once.
		boolean circle = config.fishableAreaShape() == TrawlingPlusConfig.FishableShape.CIRCLE;
		double[] shapeX = circle ? CIRCLE_X : SQUARE_X;
		double[] shapeY = circle ? CIRCLE_Y : SQUARE_Y;
		area.reset();
		for (int step = 0; step < AREA_POINTS; step++)
		{
			Point edge = toCanvas(view, at[0] + shapeX[step] * FISHABLE_REACH, at[1] + shapeY[step] * FISHABLE_REACH);
			if (edge != null)
			{
				area.addPoint(edge.getX(), edge.getY());
			}
		}

		if (area.npoints < AREA_POINTS / 4)
		{
			// Too little of it is on screen to make a shape out of.
			return false;
		}

		OverlayUtil.renderPolygon(graphics, area, config.fishableAreaColour(),
			config.fishableAreaFillColour(), areaOutline(config.fishableAreaThickness().pixels()));
		return true;
	}

	/**
	 * A dot on the boat at the point the fishable area is measured to, so the boat can fish the shoal
	 * whenever the dot is inside it. It fades in as the area appears and out as it goes.
	 */
	private void drawFishingPoint(Graphics2D graphics, boolean wanted, long now)
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

	private Stroke stopOutline(int pixels)
	{
		if (stopStroke == null || pixels != stopThickness)
		{
			stopThickness = pixels;
			stopStroke = new BasicStroke(pixels);
		}
		return stopStroke;
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
		int beyond = (view.isTopLevel() ? frameBeyond : 0) * Perspective.LOCAL_TILE_SIZE;
		if (localX < -beyond || localY < -beyond
			|| localX >= view.getSizeX() * Perspective.LOCAL_TILE_SIZE + beyond
			|| localY >= view.getSizeY() * Perspective.LOCAL_TILE_SIZE + beyond)
		{
			return null;
		}
		return new LocalPoint(localX, localY, view);
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
	}
}
