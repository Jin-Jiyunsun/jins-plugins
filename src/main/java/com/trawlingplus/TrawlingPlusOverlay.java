package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
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

	// How far above the deck the depth text floats, in local units: about two tiles, which clears the
	// mast and the crew.
	// How far the drawn fishable area reaches from the shoal, in tiles. The area the game itself uses
	// is not round: it caps each axis at about 10.5 tiles and the distance at about 13.5, so it is a
	// square with its corners cut off, measured in game sat still with the net running. A circle of the
	// axis reach sits just inside all of that, so water inside the ring can always be fished from, and
	// the corners it leaves out are water that can be.
	private static final double FISHABLE_REACH = 10.5;

	// How many points the ring is drawn from, spread evenly around it. Each one is a projection, done
	// every frame, so it is only as many as it takes to read as a circle rather than a polygon.
	private static final int AREA_POINTS = 64;
	private static final int AREA_FILL_ALPHA = 30;

	private static final int DEPTH_TEXT_HEIGHT = 250;

	// A dark box behind the depth text, so it reads against the water whatever colour the text is.
	private static final Color DEPTH_TEXT_BACKGROUND = new Color(0, 0, 0, 150);
	private static final int DEPTH_TEXT_PADDING = 3;

	// How long the display at the helm takes to fade fully in or out, in milliseconds.
	private static final double DEPTH_FADE_MILLIS = 500;

	// The word above the depth when the shoal has been baited.
	private static final String BAITED = "Baited";
	private static final Color BAITED_COLOUR = new Color(0, 220, 80);

	// The gap between the depth and the tick that follows it once every net is set to that depth.
	private static final int TICK_GAP = 4;

	// The lines of the display at the helm, from the bottom up. The depth holds the bottom line so the
	// display keeps its place on the boat however many of the others are showing.
	private static final int DEPTH_LINE = 0;
	private static final int TIME_LINE = 1;
	private static final int BAITED_LINE = 2;
	private static final int HELM_LINES = 3;
	private static final Color TIME_COLOUR = Color.WHITE;

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

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;

	// How far faded in each line of the display at the helm is, bottom line first.
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
		List<PlacedShoal> shoals = routes || config.showShoalHeadingArrow()
			? placeShoals()
			: Collections.emptyList();
		long now = System.currentTimeMillis();

		if (routes)
		{
			if (config.routeDisplay() == TrawlingPlusConfig.RouteDisplay.WHOLE_ROUTE)
			{
				drawAllRoutes(graphics, shoals);
			}
			else
			{
				drawNextStops(graphics, shoals, now);
			}
		}
		drawShoalArrows(graphics, shoals, now);
		if (config.showFishableArea())
		{
			drawFishableArea(graphics);
		}
		drawDepth(graphics);

		if (antialiasing != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
		return null;
	}

	/**
	 * Every shoal that's matched to a route and can be placed on it this frame.
	 */
	private List<PlacedShoal> placeShoals()
	{
		List<PlacedShoal> placed = new ArrayList<>();
		for (Shoal shoal : plugin.getShoals())
		{
			ShoalRoute route = shoal.getRoute();
			WorldView view = shoal.parentView(client);
			double[] position = shoal.position(client);
			if (route != null && view != null && position != null)
			{
				placed.add(new PlacedShoal(shoal, route, view, position));
			}
		}
		return placed;
	}

	private void drawAllRoutes(Graphics2D graphics, List<PlacedShoal> shoals)
	{
		// Every route that reaches into the loaded scene, whether or not its shoal is in view.
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return;
		}

		int beyond = tilesBeyondScene(view);
		for (ShoalRoute route : plugin.getRoutes())
		{
			if (route.overlaps(view.getBaseX() - beyond, view.getBaseY() - beyond,
				view.getBaseX() + view.getSizeX() + beyond, view.getBaseY() + view.getSizeY() + beyond))
			{
				drawWholeRoute(graphics, view, route, shoals);
			}
		}
	}

	private void drawNextStops(Graphics2D graphics, List<PlacedShoal> shoals, long now)
	{
		for (PlacedShoal placed : shoals)
		{
			drawToNextStop(graphics, placed, now);
		}
	}

	private void drawWholeRoute(Graphics2D graphics, WorldView view, ShoalRoute route, List<PlacedShoal> shoals)
	{
		if (config.showRouteLine())
		{
			Line line = new Line();
			int samples = route.sampleCount();
			for (int step = 0; step <= samples; step++)
			{
				int sample = step % samples;
				line.add(toCanvas(view, route.sampleX(sample), route.sampleY(sample)));
			}
			drawLine(graphics, line);
		}

		if (config.showDirectionArrows())
		{
			drawArrows(graphics, view, route, 0, route.length());
		}

		if (config.showStops())
		{
			boolean[] next = nextStops(route, shoals);
			for (int stop = 0; stop < route.stopCount(); stop++)
			{
				drawStop(graphics, view, route, stop, next[stop] ? config.nextStopColour() : config.stopColour(), 1);
			}
		}
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

	private void drawToNextStop(Graphics2D graphics, PlacedShoal placed, long now)
	{
		ShoalRoute route = placed.route;
		WorldView view = placed.view;
		double distance = placed.distance;
		int next = placed.next;
		double stretch = route.forward(distance, route.stopDistance(next));

		// When the shoal moves on to a new next stop, the route to it draws itself out from the shoal,
		// arrows appearing as it reaches them, and the stop fades in once the route gets there.
		double routeReveal = 1;
		double stopReveal = 1;
		if (config.revealNextSection())
		{
			long revealMillis = 1000L * Math.max(TrawlingPlusConfig.MIN_ANIMATION_SECONDS,
				Math.min(TrawlingPlusConfig.MAX_ANIMATION_SECONDS, config.animationDuration()));
			placed.shoal.headFor(next, now);
			routeReveal = placed.shoal.routeReveal(now, revealMillis);
			stopReveal = placed.shoal.nextStopReveal(now, revealMillis);
		}
		double drawn = stretch * routeReveal;

		if (config.showRouteLine())
		{
			int from = route.sampleAt(distance);
			int steps = Math.floorMod(route.sampleAt(route.stopDistance(next)) - from, route.sampleCount());

			Line line = new Line();
			line.add(toCanvas(view, placed.position[0], placed.position[1]));
			for (int step = 0; step < steps; step++)
			{
				int sample = (from + step) % route.sampleCount();
				if (route.forward(distance, route.sampleDistance(sample)) >= drawn)
				{
					break;
				}
				line.add(toCanvas(view, route.sampleX(sample), route.sampleY(sample)));
			}

			if (routeReveal < 1)
			{
				double[] end = route.pointAt(distance + drawn);
				line.add(toCanvas(view, end[0], end[1]));
			}
			else
			{
				line.add(toCanvas(view, route.stopX(next), route.stopY(next)));
			}
			drawLine(graphics, line);
		}

		if (config.showDirectionArrows())
		{
			drawArrows(graphics, view, route, distance, drawn);
		}

		if (config.showStops() && stopReveal > 0)
		{
			drawStop(graphics, view, route, next, config.nextStopColour(), stopReveal);
		}

		// An arrow leads the route as it draws out, and fades away as the stop fades in.
		double tipOpacity = 1 - stopReveal;
		if (config.showDirectionArrows() && tipOpacity > 0)
		{
			Path2D tip = arrowhead(view, route.pointAt(distance + drawn), shoalArrowLength(), SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (tip != null)
			{
				graphics.setColor(withOpacity(config.directionArrowColour(), tipOpacity));
				graphics.fill(tip);
			}
		}
	}

	/**
	 * Writes how deep the nearest shoal is swimming at the helm of the boat of the player, fading in
	 * as a shoal comes into range and out again as it leaves.
	 */
	private void drawDepth(Graphics2D graphics)
	{
		// Each line of the display stands on its own. Only the depth waits on a depth being known; the
		// rest have nothing to do with it and are not held up by it.
		WorldEntity boat = plugin.ownBoat();
		ShoalDepth depth = boat == null ? ShoalDepth.UNKNOWN : plugin.getNearestDepth();
		if (depth != ShoalDepth.UNKNOWN)
		{
			// Kept while the text fades out, so it does not change word on the way.
			fadingDepth = depth;
		}

		double seconds = boat == null ? -1 : plugin.getSecondsAtStop();
		boolean[] wanted = new boolean[HELM_LINES];
		wanted[DEPTH_LINE] = boat != null && config.showShoalDepth() && depth != ShoalDepth.UNKNOWN;
		wanted[TIME_LINE] = boat != null && config.showTimeAtStop() && seconds >= 0;
		wanted[BAITED_LINE] = boat != null && config.showBaited() && plugin.isBaited();

		// A line coming or going while another holds the pill up is a change inside something already
		// on screen, so it happens at once. The fade is for the display itself arriving or leaving.
		long now = System.currentTimeMillis();
		double step = lastHelmFadeMillis < 0 ? 0
			: Math.max(0, now - lastHelmFadeMillis) / DEPTH_FADE_MILLIS;
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
		if (opacity[TIME_LINE] > 0)
		{
			text[count] = Math.max(0, Math.round(seconds)) + "s";
			colour[count] = TIME_COLOUR;
			showing[count] = opacity[TIME_LINE];
			width[count] = letters.stringWidth(text[count]);
			count++;
		}
		if (opacity[BAITED_LINE] > 0)
		{
			text[count] = BAITED;
			colour[count] = BAITED_COLOUR;
			showing[count] = opacity[BAITED_LINE];
			width[count] = letters.stringWidth(BAITED);
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
			DEPTH_TEXT_HEIGHT);
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
		graphics.setColor(withOpacity(DEPTH_TEXT_BACKGROUND, solid));
		graphics.fillRoundRect(left - DEPTH_TEXT_PADDING, top - DEPTH_TEXT_PADDING,
			wide + DEPTH_TEXT_PADDING * 2, lineHeight * count + DEPTH_TEXT_PADDING * 2, 6, 6);

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
	private void drawLine(Graphics2D graphics, Line line)
	{
		// Round joins and caps so the short segments the curve is drawn with blend into one smooth line.
		graphics.setColor(config.routeColour());
		graphics.setStroke(new BasicStroke(thickness(config.routeLineThickness()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
				return config.shallowDepthColour();
			case DEEP:
				return config.deepDepthColour();
			default:
				return config.moderateDepthColour();
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
	 * How opaque the depth text is right now, from 0 to 1. It moves a little towards shown or hidden
	 * on each call, so the text fades instead of popping. Call once per frame.
	 */
	/**
	 * Draws the route's arrows that fall within a stretch of it, starting at a distance round the route.
	 * Arrows sit at fixed places round the route, so they don't slide along as the shoal swims.
	 */
	private void drawArrows(Graphics2D graphics, WorldView view, ShoalRoute route, double from, double stretch)
	{
		// Clamped as well as limited in the settings panel, since a spacing of 0 would never finish.
		int spacing = Math.max(TrawlingPlusConfig.MIN_ARROW_SPACING,
			Math.min(TrawlingPlusConfig.MAX_ARROW_SPACING, config.directionArrowSpacing()));
		double length = scaled(ARROW_LENGTH, config.directionArrowScale());

		// The first arrow sits one spacing in from the start of the route, so where the loop closes, its
		// last arrow and first arrow are never closer than the spacing.
		graphics.setColor(config.directionArrowColour());
		for (int arrow = 1; arrow * spacing < route.length(); arrow++)
		{
			double at = arrow * spacing;
			if (route.forward(from, at) < stretch)
			{
				Path2D head = arrowhead(view, route.pointAt(at), length, ARROW_HALF_WIDTH, ARROW_NOTCH);
				if (head != null)
				{
					graphics.fill(head);
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
			double opacity = placed.shoal.headingArrowOpacity(now);
			if (opacity <= 0)
			{
				continue;
			}

			// Snap the arrow onto the route, so it slides along the line with the shoal.
			Path2D arrow = arrowhead(placed.view, placed.route.pointAt(placed.distance), length, SHOAL_ARROW_HALF_WIDTH, SHOAL_ARROW_NOTCH);
			if (arrow != null)
			{
				graphics.setColor(withOpacity(colour, opacity));
				graphics.fill(arrow);
			}
		}
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
		Point tip = toCanvas(view, x + dx * halfLength, y + dy * halfLength);
		Point left = toCanvas(view, x - dx * halfLength - dy * halfWidth, y - dy * halfLength + dx * halfWidth);
		Point back = toCanvas(view, x - dx * notch, y - dy * notch);
		Point right = toCanvas(view, x - dx * halfLength + dy * halfWidth, y - dy * halfLength - dx * halfWidth);
		if (tip == null || left == null || back == null || right == null)
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
				new BasicStroke(thickness(config.stopThickness())));
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
	 * The length of the shoal heading arrow, and of the arrow leading an animated section, in tiles.
	 */
	private double shoalArrowLength()
	{
		return scaled(SHOAL_ARROW_LENGTH, config.shoalHeadingArrowScale());
	}

	/**
	 * Outlines the water the nearest shoal can be fished from, as a ring around the shoal. It is
	 * centred on the shoal itself, so it travels with the shoal and only sits still because the shoal
	 * does.
	 */
	private void drawFishableArea(Graphics2D graphics)
	{
		Shoal shoal = plugin.getNearestShoal();
		WorldView view = shoal == null ? null : shoal.parentView(client);
		double[] at = shoal == null ? null : shoal.position(client);
		if (view == null || at == null)
		{
			return;
		}

		// Walked around the ring a point at a time, so one running off the loaded map still draws the
		// part that is on it.
		Polygon area = new Polygon();
		for (int step = 0; step < AREA_POINTS; step++)
		{
			double angle = 2 * Math.PI * step / AREA_POINTS;
			Point edge = toCanvas(view, at[0] + Math.cos(angle) * FISHABLE_REACH,
				at[1] + Math.sin(angle) * FISHABLE_REACH);
			if (edge != null)
			{
				area.addPoint(edge.getX(), edge.getY());
			}
		}

		if (area.npoints < AREA_POINTS / 4)
		{
			// Too little of it is on screen to make a shape out of.
			return;
		}

		Color colour = config.fishableAreaColour();
		OverlayUtil.renderPolygon(graphics, area, colour,
			new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), AREA_FILL_ALPHA),
			new BasicStroke(thickness(config.fishableAreaThickness())));
	}

	/**
	 * An arrow's length in tiles, from its length at 100% and an arrow scaling setting.
	 */
	private static double scaled(double length, int percent)
	{
		return length * Math.max(TrawlingPlusConfig.MIN_ARROW_SCALE, Math.min(TrawlingPlusConfig.MAX_ARROW_SCALE, percent)) / 100;
	}

	/**
	 * A thickness setting in pixels, kept within its limits.
	 */
	private static int thickness(int pixels)
	{
		return Math.max(TrawlingPlusConfig.MIN_LINE_THICKNESS, Math.min(TrawlingPlusConfig.MAX_LINE_THICKNESS, pixels));
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
		int beyond = tilesBeyondScene(view) * Perspective.LOCAL_TILE_SIZE;
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
		final double[] position;
		final double distance;
		final int next;

		PlacedShoal(Shoal shoal, ShoalRoute route, WorldView view, double[] position)
		{
			this.shoal = shoal;
			this.route = route;
			this.view = view;
			this.position = position;
			distance = route.project(position[0], position[1]).distance;
			next = route.nextStop(distance);
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
