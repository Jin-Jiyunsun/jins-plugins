package com.trawlingplus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Point;

/**
 * Draws routes onto a map, wherever that map happens to be and at whatever scale it is drawn. Shared
 * by the minimap and the world map.
 */
final class MapRoutes
{
	// An arrowhead's half width and back-edge notch, as fractions of its length, which is in pixels so it stays
	// readable at any zoom.
	private static final double ARROW_HALF_WIDTH = 0.4;
	private static final double ARROW_NOTCH = 0.25;

	// No two arrows closer than this on screen. How many to leave out depends only on the zoom, never on which are
	// visible, so they don't rearrange as the map is panned.
	private static final int ARROW_GAP = 24;
	// The arrows left are then spread out, which keeps a map from filling up with them: twice as far apart, or a
	// third further once it's zoomed out below this many pixels a tile, which is all the way out.
	private static final double ARROW_SPREAD = 2;
	private static final double ZOOMED_OUT_SPREAD = 4.0 / 3;
	private static final double ZOOMED_OUT = 1.5;

	// How far the drawn line may stray from the route, in pixels. The points that would bend it by less at the map's
	// zoom are left out, which is most of them once a map is zoomed out.
	private static final double LINE_TOLERANCE = 0.5;
	// How far each straight piece runs past its ends, in pixels, so pieces meeting at an angle leave no seam.
	private static final double PIECE_OVERLAP = 0.125;
	// Flat-ended strokes by thickness and for the direction arrows' two strokes, and the shoal dot's edge, made once
	// rather than every frame.
	private static final BasicStroke[] STROKES = {null, flat(1), flat(2), flat(3)};
	private static final BasicStroke ARROW_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
	// The thin dark edge round a shoal's mark. The arrow's goes under its colour, a little wider, so its thin barbs
	// keep their colour.
	private static final Color MARK_EDGE = Color.BLACK;
	// Room around a mark's size for its edge, in pixels, and the picture a mark crossing a masked map's edge is drawn
	// into first, so the hidden part can be taken off it. Client thread only.
	private static final int MARK_MARGIN = 6;
	private static BufferedImage markPicture;
	private static final BasicStroke DOT_EDGE = new BasicStroke(1);
	private static final BasicStroke MARK_ARROW_EDGE =
		new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	// A stop's outline in its colour, over a dark one twice as wide so it shows on any water.
	private static final BasicStroke STOP_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
	private static final BasicStroke STOP_EDGE = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
	// Room around a stop's outline for its edge, in pixels, and its pictures on an upright map, plain and next.
	private static final int STOP_PICTURE_MARGIN = 4;
	// A stop as stopStates gives it: a shoal's next stop, or the one a shoal is sitting at, which isn't drawn.
	static final byte STOP_NEXT = 1;
	static final byte STOP_AT = 2;
	private static final StopPicture[] STOP_PICTURES = {new StopPicture(), new StopPicture()};

	// Told how long the maps' route drawing takes, by the debug plugin; null otherwise.
	static MapTimer mapTimer;

	/**
	 * Takes how long one part of the maps' route drawing took, in nanoseconds.
	 */
	interface MapTimer
	{
		int MINIMAP = 0;
		int WORLD_LINES = 1;
		int WORLD_ARROWS = 2;
		int MINIMAP_STOPS = 3;
		int WORLD_STOPS = 4;

		void mapTook(int part, long nanos);
	}

	private MapRoutes()
	{
	}

	private static BasicStroke flat(int thickness)
	{
		return new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
	}

	/**
	 * Where world tiles land on a map, and where the map ends. Both maps scale, turn and shift the world evenly, so a
	 * point is placed from one tile's place and the steps a tile east and north make.
	 */
	static final class MapView
	{
		private double xByX;
		private double xByY;
		private double xFrom;
		private double yByX;
		private double yByY;
		private double yFrom;
		// The map's box on screen, and for a round map its middle and radius; a radius of 0 for a square map.
		private final Rectangle box = new Rectangle();
		private double middleX;
		private double middleY;
		private double radius;
		// For a map shaped by the game's mask instead: whether each of its pixels shows the map, and where it sits.
		private boolean[] shows;
		private int showsX;
		private int showsY;
		private int showsWidth;
		private int showsHeight;

		void place(double tileX, double tileY, double screenX, double screenY, double eastX, double eastY,
			double northX, double northY)
		{
			xByX = eastX;
			yByX = eastY;
			xByY = northX;
			yByY = northY;
			xFrom = screenX - eastX * tileX - northX * tileY;
			yFrom = screenY - eastY * tileX - northY * tileY;
		}

		void edges(Rectangle box, boolean round)
		{
			this.box.setBounds(box);
			middleX = box.x + box.width / 2.0;
			middleY = box.y + box.height / 2.0;
			radius = round ? Math.min(box.width, box.height) / 2.0 : 0;
			shows = null;
		}

		/**
		 * Shapes the map by a mask, centred in its box, in place of its round edge.
		 */
		void shape(boolean[] shows, int width, int height)
		{
			this.shows = shows;
			showsWidth = width;
			showsHeight = height;
			showsX = box.x + (box.width - width) / 2;
			showsY = box.y + (box.height - height) / 2;
			radius = 0;
		}

		private boolean showsAt(double x, double y)
		{
			int pixelX = (int) Math.floor(x) - showsX;
			int pixelY = (int) Math.floor(y) - showsY;
			return pixelX >= 0 && pixelY >= 0 && pixelX < showsWidth && pixelY < showsHeight
				&& shows[pixelY * showsWidth + pixelX];
		}

		/**
		 * How far from the map's middle its edge is, going one way (a unit step across and down).
		 */
		double edgeFromMiddle(double wayX, double wayY)
		{
			if (shows == null)
			{
				return radius;
			}
			int most = Math.max(showsWidth, showsHeight);
			for (int out = 0; out < most; out++)
			{
				if (!showsAt(middleX + wayX * out, middleY + wayY * out))
				{
					return Math.max(0, out - 0.5);
				}
			}
			return most;
		}

		double x(double tileX, double tileY)
		{
			return xByX * tileX + xByY * tileY + xFrom;
		}

		double y(double tileX, double tileY)
		{
			return yByX * tileX + yByY * tileY + yFrom;
		}

		double pixelsPerTile()
		{
			return Math.sqrt(xByX * xByX + yByX * yByX);
		}

		boolean contains(double x, double y)
		{
			if (shows != null)
			{
				return showsAt(x, y);
			}
			if (radius > 0)
			{
				double offX = x - middleX;
				double offY = y - middleY;
				return offX * offX + offY * offY <= radius * radius;
			}
			return box.contains(x, y);
		}

		/**
		 * The step on screen that a step across the world makes, across and down, turned and scaled as the map draws
		 * it.
		 */
		double stepX(double tilesX, double tilesY)
		{
			return xByX * tilesX + xByY * tilesY;
		}

		double stepY(double tilesX, double tilesY)
		{
			return yByX * tilesX + yByY * tilesY;
		}

		/**
		 * Where a tile lands, to the nearest pixel, or null when that's off the map.
		 */
		Point at(double tileX, double tileY)
		{
			double x = x(tileX, tileY);
			double y = y(tileX, tileY);
			return contains(x, y) ? new Point((int) Math.round(x), (int) Math.round(y)) : null;
		}
	}

	/**
	 * A colour made as see-through as another.
	 */
	static Color withAlphaOf(Color colour, Color like)
	{
		return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), like.getAlpha());
	}

	/**
	 * One route as a line, simplified to the map's zoom and drawn as smooth straight pieces (far faster than one long
	 * path), danger stretches in the danger colour if given; only runs reaching the given box of tiles.
	 */
	static void route(Graphics2D graphics, TrawlingPlusConfig config, ShoalRoute route, MapView onto, int thickness,
		double fromX, double fromY, double toX, double toY, Color dangerColour)
	{
		int count = route.sampleCount();
		double pixelsPerTile = onto.pixelsPerTile();
		if (count == 0 || pixelsPerTile <= 0)
		{
			return;
		}
		double tolerance = LINE_TOLERANCE / pixelsPerTile;
		boolean withDanger = dangerColour != null && route.hasDanger();
		Color colour = config.routeColour();

		Object[] hints = smooth(graphics);
		graphics.setStroke(thickness > 0 && thickness < STROKES.length ? STROKES[thickness] : flat(thickness));
		graphics.setColor(colour);
		boolean inDanger = false;

		Line2D.Double piece = new Line2D.Double();
		// The point the line has been drawn to so far, and where it landed.
		int at = -1;
		double atX = 0;
		double atY = 0;
		for (int block = 0; block < route.blockCount(); block++)
		{
			// Only the runs of the route that reach what is being shown. Most of a route is somewhere
			// else entirely, and a run is dismissed in four comparisons.
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				continue;
			}

			// Picked up from the last point kept before this run, so the piece running into it is drawn, unless the
			// line already reaches into it. The loop is closed by the piece after the last point.
			int first = route.blockFrom(block);
			if (at < first)
			{
				at = Math.max(0, first - 1);
				while (at > 0 && !kept(route, at, tolerance, withDanger))
				{
					at--;
				}
				atX = onto.x(route.sampleX(at), route.sampleY(at));
				atY = onto.y(route.sampleX(at), route.sampleY(at));
			}

			// Each point kept in the run, and then the first one kept beyond it, which past the last point is the
			// first.
			int end = route.blockTo(block);
			for (int sample = at + 1; sample <= count; sample++)
			{
				if (sample < count && !kept(route, sample, tolerance, withDanger))
				{
					continue;
				}

				int to = sample < count ? sample : 0;
				double x = onto.x(route.sampleX(to), route.sampleY(to));
				double y = onto.y(route.sampleX(to), route.sampleY(to));
				// A piece takes the colour of the point it starts from.
				boolean dangerous = withDanger && route.dangerAt(at);
				if (dangerous != inDanger)
				{
					graphics.setColor(dangerous ? dangerColour : colour);
					inDanger = dangerous;
				}
				drawPiece(graphics, onto, piece, atX, atY, x, y);
				at = sample;
				atX = x;
				atY = y;
				if (sample >= end)
				{
					break;
				}
			}
		}

		restore(graphics, hints);
	}

	/**
	 * Turns smoothing on, with each piece landing exactly where it's placed rather than having its ends nudged to the
	 * pixel grid on its own, and gives back the hints it replaced.
	 */
	static Object[] smooth(Graphics2D graphics)
	{
		Object[] was = {graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING),
			graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)};
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return was;
	}

	static void restore(Graphics2D graphics, Object[] hints)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			hints[0] != null ? hints[0] : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
			hints[1] != null ? hints[1] : RenderingHints.VALUE_STROKE_DEFAULT);
	}

	/**
	 * Whether a point is drawn at a tolerance: kept by simplifying, or where the line changes colour.
	 */
	private static boolean kept(ShoalRoute route, int sample, double tolerance, boolean withDanger)
	{
		return route.sampleKept(sample, tolerance)
			|| withDanger && sample > 0 && route.dangerAt(sample) != route.dangerAt(sample - 1);
	}

	/**
	 * One straight piece, running a fraction of a pixel past each end so pieces meeting leave no seam, and on a round
	 * or masked map (null for none) only the parts inside it. Nothing for a piece whose ends landed on the same spot.
	 */
	static void drawPiece(Graphics2D graphics, MapView onto, Line2D.Double piece, double fromX, double fromY,
		double toX, double toY)
	{
		double alongX = toX - fromX;
		double alongY = toY - fromY;
		double lengthSquared = alongX * alongX + alongY * alongY;
		if (lengthSquared == 0)
		{
			return;
		}
		// Where it starts and ends, as shares of the way along it.
		double over = PIECE_OVERLAP / Math.sqrt(lengthSquared);
		double start = -over;
		double end = 1 + over;
		if (onto != null && onto.radius > 0)
		{
			// Where the piece crosses the map's edge, from where it meets the circle.
			double offX = fromX - onto.middleX;
			double offY = fromY - onto.middleY;
			double half = offX * alongX + offY * alongY;
			double crossing = half * half - lengthSquared * (offX * offX + offY * offY - onto.radius * onto.radius);
			if (crossing <= 0)
			{
				return;
			}
			double root = Math.sqrt(crossing);
			start = Math.max(start, (-half - root) / lengthSquared);
			end = Math.min(end, (-half + root) / lengthSquared);
			if (start >= end)
			{
				return;
			}
		}
		if (onto != null && onto.shows != null)
		{
			drawShown(graphics, onto, piece, fromX, fromY, alongX, alongY, Math.sqrt(lengthSquared), start, end);
			return;
		}
		drawShare(graphics, piece, fromX, fromY, alongX, alongY, start, end);
	}

	/**
	 * A piece on a masked map, walked a pixel at a time, each stretch that shows drawn to the pixel edge it meets.
	 */
	private static void drawShown(Graphics2D graphics, MapView onto, Line2D.Double piece, double fromX, double fromY,
		double alongX, double alongY, double length, double start, double end)
	{
		double left = Math.min(fromX, fromX + alongX);
		double top = Math.min(fromY, fromY + alongY);
		if (left + Math.abs(alongX) < onto.showsX || top + Math.abs(alongY) < onto.showsY
			|| left > onto.showsX + onto.showsWidth || top > onto.showsY + onto.showsHeight)
		{
			return;
		}
		int steps = Math.max(1, (int) Math.ceil(length));
		double half = 0.5 / steps;
		double runFrom = Double.NaN;
		double runTo = 0;
		for (int step = 0; step <= steps; step++)
		{
			double share = (double) step / steps;
			if (onto.showsAt(fromX + alongX * share, fromY + alongY * share))
			{
				if (Double.isNaN(runFrom))
				{
					runFrom = step == 0 ? start : share - half;
				}
				runTo = step == steps ? end : share + half;
			}
			else if (!Double.isNaN(runFrom))
			{
				drawShare(graphics, piece, fromX, fromY, alongX, alongY, runFrom, runTo);
				runFrom = Double.NaN;
			}
		}
		if (!Double.isNaN(runFrom))
		{
			drawShare(graphics, piece, fromX, fromY, alongX, alongY, runFrom, runTo);
		}
	}

	private static void drawShare(Graphics2D graphics, Line2D.Double piece, double fromX, double fromY,
		double alongX, double alongY, double start, double end)
	{
		piece.setLine(fromX + alongX * start, fromY + alongY * start, fromX + alongX * end, fromY + alongY * end);
		graphics.draw(piece);
	}

	/**
	 * The arrows showing which way a route is swum, at the water's spacing thinned so they never crowd, in the danger
	 * colour on dangerous stretches. Each is two smooth strokes meeting at its tip; the line goes over them.
	 */
	static void arrows(Graphics2D graphics, TrawlingPlusConfig config, ShoalRoute route, MapView onto, int size,
		double fromX, double fromY, double toX, double toY, Color dangerColour)
	{
		double pixelsPerTile = onto.pixelsPerTile();
		if (!config.showDirectionArrows() || pixelsPerTile <= 0)
		{
			return;
		}

		int spacing = Math.max(TrawlingPlusConfig.MIN_ARROW_SPACING,
			Math.min(TrawlingPlusConfig.MAX_ARROW_SPACING, config.directionArrowSpacing()));

		// Every so many of the arrows, chosen so that what is left of them is far enough apart on screen, then spread.
		// They then sit at fixed places round the route and stay put however the map is moved about.
		int every = Math.max(1, (int) Math.ceil(ARROW_GAP / pixelsPerTile / spacing));
		double apart = every * spacing * (pixelsPerTile < ZOOMED_OUT ? ZOOMED_OUT_SPREAD : ARROW_SPREAD);
		Color arrowColour = config.directionArrowColour();
		Object[] hints = smooth(graphics);
		graphics.setStroke(ARROW_STROKE);
		Line2D.Double barb = new Line2D.Double();
		// Half an arrow's length along the route, in tiles, and how far each barb sits to the side, in pixels.
		double half = size / 2.0 / pixelsPerTile;
		double aside = size * ARROW_HALF_WIDTH;
		for (int block = 0; block < route.blockCount(); block++)
		{
			// Skipped as the line is: the arrows on a run are those whose distances fall inside its stretch of the
			// route.
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				continue;
			}

			double opens = route.sampleDistance(route.blockFrom(block));
			double closes = route.blockTo(block) < route.sampleCount()
				? route.sampleDistance(route.blockTo(block)) : route.length();

			for (int arrow = Math.max(1, (int) Math.ceil(opens / apart)); arrow * apart < closes; arrow++)
			{
				// The tip sits on the route half an arrow ahead, and the arrow points along the route from half an
				// arrow behind, so on a bend it stays on the line rather than running off it.
				double[] ahead = route.pointAt(arrow * apart + half);
				double[] behind = route.pointAt(arrow * apart - half);
				double tipX = onto.x(ahead[0], ahead[1]);
				double tipY = onto.y(ahead[0], ahead[1]);
				double towardX = tipX - onto.x(behind[0], behind[1]);
				double towardY = tipY - onto.y(behind[0], behind[1]);
				double step = Math.sqrt(towardX * towardX + towardY * towardY);
				// On a masked map an arrow is cut at the edge like the line, so it slides under the frame rather than
				// popping in and out whole.
				boolean masked = onto.shows != null;
				if (!masked && !onto.contains(tipX, tipY) || step < 1e-6)
				{
					continue;
				}

				// An arrow on a stretch drawn red is red too, so it doesn't break the stretch up.
				boolean red = dangerColour != null && route.dangerAtDistance(arrow * apart);
				graphics.setColor(red ? dangerColour : arrowColour);
				double alongX = towardX / step;
				double alongY = towardY / step;
				double backX = tipX - alongX * size;
				double backY = tipY - alongY * size;
				if (masked)
				{
					drawPiece(graphics, onto, barb, backX - alongY * aside, backY + alongX * aside, tipX, tipY);
					drawPiece(graphics, onto, barb, backX + alongY * aside, backY - alongX * aside, tipX, tipY);
					continue;
				}
				// Whole arrows only, so none pokes past a round map's edge.
				if (!onto.contains(backX, backY))
				{
					continue;
				}
				barb.setLine(backX - alongY * aside, backY + alongX * aside, tipX, tipY);
				graphics.draw(barb);
				barb.setLine(backX + alongY * aside, backY - alongX * aside, tipX, tipY);
				graphics.draw(barb);
			}
		}
		restore(graphics, hints);
	}

	/**
	 * Where a shoal is right now, as its mark in the colour of its heading arrow on the water: a dot this many pixels
	 * across, and while it's moving an arrow this long pointing the way its route runs, turned as the map turns it.
	 */
	static void shoal(Graphics2D graphics, TrawlingPlusConfig config, Client client, Shoal shoal,
		MapView onto, int dot, int arrow)
	{
		double[] at = alongRoute(shoal, client);
		if (at == null)
		{
			return;
		}
		boolean heading = at.length > 2 && shoal.stopBarNow(client.getTickCount()) < 0;
		double towardX = heading ? onto.stepX(at[2], at[3]) : 0;
		double towardY = heading ? onto.stepY(at[2], at[3]) : 0;
		Color colour = config.shoalHeadingArrowColour();
		if (onto.shows != null)
		{
			masked(graphics, onto, (int) Math.round(onto.x(at[0], at[1])), (int) Math.round(onto.y(at[0], at[1])),
				Math.max(dot, arrow) + MARK_MARGIN,
				(drawing, x, y) -> mark(drawing, x, y, colour, dot, arrow, towardX, towardY));
			return;
		}
		Point placed = onto.at(at[0], at[1]);
		if (placed != null)
		{
			mark(graphics, placed.getX(), placed.getY(), colour, dot, arrow, towardX, towardY);
		}
	}

	/**
	 * Draws a mark centred on a point.
	 */
	private interface MarkDrawing
	{
		void draw(Graphics2D graphics, double x, double y);
	}

	/**
	 * A mark this many pixels across, edge included, on a masked map, cut at the edge like the line, so it slides
	 * under the frame rather than popping in and out whole.
	 */
	private static void masked(Graphics2D graphics, MapView onto, int x, int y, int size, MarkDrawing mark)
	{
		int left = x - size / 2;
		int top = y - size / 2;
		int shown = 0;
		for (int down = 0; down < size; down++)
		{
			for (int across = 0; across < size; across++)
			{
				if (onto.showsAt(left + across, top + down))
				{
					shown++;
				}
			}
		}
		if (shown == size * size)
		{
			mark.draw(graphics, x, y);
			return;
		}
		if (shown == 0)
		{
			return;
		}

		if (markPicture == null || markPicture.getWidth() < size)
		{
			markPicture = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		}
		Graphics2D drawing = markPicture.createGraphics();
		drawing.setComposite(AlphaComposite.Clear);
		drawing.fillRect(0, 0, markPicture.getWidth(), markPicture.getHeight());
		drawing.setComposite(AlphaComposite.SrcOver);
		mark.draw(drawing, x - left, y - top);
		drawing.dispose();
		for (int down = 0; down < size; down++)
		{
			for (int across = 0; across < size; across++)
			{
				if (!onto.showsAt(left + across, top + down))
				{
					markPicture.setRGB(across, down, 0);
				}
			}
		}
		graphics.drawImage(markPicture, left, top, left + size, top + size, 0, 0, size, size, null);
	}

	/**
	 * How far a point on screen is from a route's line, in pixels, looking only at the runs reaching a box of tiles;
	 * the largest double where none does.
	 */
	static double pixelsFrom(ShoalRoute route, MapView onto, double x, double y, double fromX, double fromY,
		double toX, double toY)
	{
		int count = route.sampleCount();
		double nearest = Double.MAX_VALUE;
		for (int block = 0; block < route.blockCount(); block++)
		{
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				continue;
			}
			int first = route.blockFrom(block);
			int last = Math.min(route.blockTo(block), count - 1);
			double atX = onto.x(route.sampleX(first), route.sampleY(first));
			double atY = onto.y(route.sampleX(first), route.sampleY(first));
			// Each piece from a point to the next, the one after the last point closing the loop.
			for (int sample = first; sample <= last; sample++)
			{
				int next = (sample + 1) % count;
				double nextX = onto.x(route.sampleX(next), route.sampleY(next));
				double nextY = onto.y(route.sampleX(next), route.sampleY(next));
				nearest = Math.min(nearest, Line2D.ptSegDistSq(atX, atY, nextX, nextY, x, y));
				atX = nextX;
				atY = nextY;
			}
		}
		return nearest == Double.MAX_VALUE ? nearest : Math.sqrt(nearest);
	}

	/**
	 * Each of a route's stops as plain, the next stop of a shoal on it the game is drawing, or the one such a shoal is
	 * sitting at; with none drawn, for the shoal likely this far round it (below 0 for none) and likely stopped or not.
	 */
	static byte[] stopStates(TrawlingPlusPlugin plugin, Client client, ShoalRoute route, double likely,
		boolean likelyStopped)
	{
		byte[] states = new byte[route.stopCount()];
		int tick = client.getTickCount();
		boolean seen = false;
		for (Shoal shoal : plugin.getShoals())
		{
			if (shoal.getRoute() != route || !plugin.isShown(shoal) || !shoal.rendered(client))
			{
				continue;
			}
			seen = true;
			double[] position = shoal.position(client);
			if (position != null)
			{
				markStops(states, route, shoal.followRoute(position), shoal.stopBarNow(tick) >= 0);
			}
		}
		if (!seen && likely >= 0)
		{
			markStops(states, route, likely, likelyStopped);
		}
		return states;
	}

	private static void markStops(byte[] states, ShoalRoute route, double distance, boolean stopped)
	{
		int next = route.nextStop(distance);
		if (states[next] != STOP_AT)
		{
			states[next] = STOP_NEXT;
		}
		if (!stopped)
		{
			return;
		}
		// Sitting at a stop moves the next stop on to the one after, so the one it's at comes just before; only when
		// it really is there, rather than stopped short of it.
		int at = (next + states.length - 1) % states.length;
		double apart = Math.abs(distance - route.stopDistance(at)) % route.length();
		if (Math.min(apart, route.length() - apart) <= ShoalRoute.AT_STOP_TILES)
		{
			states[at] = STOP_AT;
		}
	}

	/**
	 * A route's stops within a box of tiles, as outlines this many pixels across in the stop shape, a square turned
	 * as the map turns, each a shoal is heading for in the next stop colour and none where a shoal is sitting.
	 */
	static void stops(Graphics2D graphics, TrawlingPlusConfig config, ShoalRoute route, MapView onto, int size,
		double fromX, double fromY, double toX, double toY, byte[] states)
	{
		double pixelsPerTile = onto.pixelsPerTile();
		if (pixelsPerTile <= 0)
		{
			return;
		}
		boolean circle = config.stopShape() == TrawlingPlusConfig.FishableShape.CIRCLE;
		// A step east and north on screen, a pixel long, for a square's sides.
		double eastX = onto.stepX(1, 0) / pixelsPerTile;
		double eastY = onto.stepY(1, 0) / pixelsPerTile;
		double northX = onto.stepX(0, 1) / pixelsPerTile;
		double northY = onto.stepY(0, 1) / pixelsPerTile;
		Color stopColour = config.stopColour();
		Color nextColour = config.nextStopColour();
		// A map that doesn't turn copies in a picture of each stop, far quicker than stroking its outline each time.
		boolean upright = onto.shows == null && Math.abs(eastY) < 1e-9 && Math.abs(northX) < 1e-9;
		int across = size + STOP_PICTURE_MARGIN;
		for (int stop = 0; stop < route.stopCount(); stop++)
		{
			double tileX = route.stopX(stop);
			double tileY = route.stopY(stop);
			if (states[stop] == STOP_AT || tileX < fromX || tileX > toX || tileY < fromY || tileY > toY)
			{
				continue;
			}
			boolean isNext = states[stop] == STOP_NEXT;
			Color colour = isNext ? nextColour : stopColour;
			double x = onto.x(tileX, tileY);
			double y = onto.y(tileX, tileY);
			if (upright)
			{
				if (onto.contains(x, y))
				{
					BufferedImage picture = STOP_PICTURES[isNext ? 1 : 0].of(colour, circle, size);
					graphics.drawImage(picture, (int) Math.round(x - across / 2.0), (int) Math.round(y - across / 2.0),
						null);
				}
			}
			else if (onto.shows != null)
			{
				masked(graphics, onto, (int) Math.round(x), (int) Math.round(y), size + MARK_MARGIN,
					(drawing, atX, atY) -> stop(drawing, atX, atY, colour, size, circle, eastX, eastY, northX, northY));
			}
			else if (onto.contains(x, y))
			{
				stop(graphics, x, y, colour, size, circle, eastX, eastY, northX, northY);
			}
		}
	}

	/**
	 * A stop drawn once into a picture, for a map that doesn't turn, kept until its colour, shape or size changes.
	 * Client thread only.
	 */
	private static final class StopPicture
	{
		private Color colour;
		private boolean circle;
		private int size;
		private BufferedImage picture;

		BufferedImage of(Color colour, boolean circle, int size)
		{
			if (picture == null || !colour.equals(this.colour) || circle != this.circle || size != this.size)
			{
				this.colour = colour;
				this.circle = circle;
				this.size = size;
				int across = size + STOP_PICTURE_MARGIN;
				picture = new BufferedImage(across, across, BufferedImage.TYPE_INT_ARGB);
				Graphics2D drawing = picture.createGraphics();
				stop(drawing, across / 2.0, across / 2.0, colour, size, circle, 1, 0, 0, 1);
				drawing.dispose();
			}
			return picture;
		}
	}

	private static void stop(Graphics2D graphics, double x, double y, Color colour, int size, boolean circle,
		double eastX, double eastY, double northX, double northY)
	{
		double half = size / 2.0;
		Shape outline;
		if (circle)
		{
			outline = new Ellipse2D.Double(x - half, y - half, size, size);
		}
		else
		{
			Path2D square = new Path2D.Double();
			square.moveTo(x + (eastX + northX) * half, y + (eastY + northY) * half);
			square.lineTo(x + (eastX - northX) * half, y + (eastY - northY) * half);
			square.lineTo(x - (eastX + northX) * half, y - (eastY + northY) * half);
			square.lineTo(x - (eastX - northX) * half, y - (eastY - northY) * half);
			square.closePath();
			outline = square;
		}
		Object[] hints = smooth(graphics);
		graphics.setColor(withAlphaOf(MARK_EDGE, colour));
		graphics.setStroke(STOP_EDGE);
		graphics.draw(outline);
		graphics.setColor(colour);
		graphics.setStroke(STOP_STROKE);
		graphics.draw(outline);
		restore(graphics, hints);
	}

	/**
	 * A shoal's mark, smooth with a thin dark edge: while moving, an arrow this long centred on it pointing along a
	 * step on screen; with no step, a dot this many pixels across.
	 */
	static void mark(Graphics2D graphics, double x, double y, Color colour, int dot, int arrow, double towardX,
		double towardY)
	{
		Object[] hints = smooth(graphics);
		if (towardX * towardX + towardY * towardY >= 1e-12)
		{
			edgedArrow(graphics, x, y, colour, arrow, towardX, towardY);
			restore(graphics, hints);
			return;
		}
		Ellipse2D.Double circle = new Ellipse2D.Double(x - dot / 2.0, y - dot / 2.0, dot, dot);
		graphics.setColor(colour);
		graphics.fill(circle);
		graphics.setColor(MARK_EDGE);
		graphics.setStroke(DOT_EDGE);
		graphics.draw(circle);
		restore(graphics, hints);
	}

	/**
	 * A mark drawn this frame, for its right-click menu: its route (null for none) and middle. Client thread only.
	 */
	static final class Clickable
	{
		private ShoalRoute route;
		private double x;
		private double y;
		private double reach;

		void clear()
		{
			route = null;
		}

		void drawn(ShoalRoute route, double x, double y, int size)
		{
			this.route = route;
			this.x = x;
			this.y = y;
			reach = size / 2.0 + 2;
		}

		/**
		 * Its route if a point on the canvas is on it, or null.
		 */
		ShoalRoute at(int atX, int atY)
		{
			double dx = atX - x;
			double dy = atY - y;
			return route != null && dx * dx + dy * dy <= reach * reach ? route : null;
		}
	}

	/**
	 * A shoal mark's arrow on its own, centred on a point and pointing along a step on screen.
	 */
	static void pointer(Graphics2D graphics, double x, double y, Color colour, int arrow, double towardX,
		double towardY)
	{
		Object[] hints = smooth(graphics);
		edgedArrow(graphics, x, y, colour, arrow, towardX, towardY);
		restore(graphics, hints);
	}

	private static void edgedArrow(Graphics2D graphics, double x, double y, Color colour, int arrow, double towardX,
		double towardY)
	{
		Path2D head = arrowhead(x, y, towardX, towardY, arrow);
		graphics.setColor(MARK_EDGE);
		graphics.setStroke(MARK_ARROW_EDGE);
		graphics.draw(head);
		graphics.setColor(colour);
		graphics.fill(head);
	}

	/**
	 * Where a shoal is along its route, as the route's point and direction there, so its mark sits on the line however
	 * the route is smoothed; where the game has it (x, y only) with no route; null if it can't be placed.
	 */
	static double[] alongRoute(Shoal shoal, Client client)
	{
		double[] position = shoal.position(client);
		ShoalRoute route = shoal.getRoute();
		return position == null || route == null ? position : route.pointAt(shoal.followRoute(position));
	}

	/**
	 * An arrowhead of the given length in pixels, centred on a point and pointing along a direction of any non-zero
	 * length.
	 */
	static Path2D arrowhead(double atX, double atY, double towardX, double towardY, int length)
	{
		double size = Math.sqrt(towardX * towardX + towardY * towardY);
		double sin = towardY / size;
		double cos = towardX / size;
		double half = length * ARROW_HALF_WIDTH;
		double notch = length * ARROW_NOTCH;

		// Along the arrow, then across it, for the tip, both barbs and the notch between them.
		double[][] corners = {{length / 2.0, 0}, {-length / 2.0, half}, {-length / 2.0 + notch, 0},
			{-length / 2.0, -half}};
		Path2D head = new Path2D.Double();
		for (int corner = 0; corner < corners.length; corner++)
		{
			double x = atX + corners[corner][0] * cos - corners[corner][1] * sin;
			double y = atY + corners[corner][0] * sin + corners[corner][1] * cos;
			if (corner == 0)
			{
				head.moveTo(x, y);
			}
			else
			{
				head.lineTo(x, y);
			}
		}
		head.closePath();
		return head;
	}
}
