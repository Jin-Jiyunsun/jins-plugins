package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import net.runelite.api.Client;
import net.runelite.api.Point;

/**
 * Draws routes onto a map, wherever that map happens to be and at whatever scale it is drawn. Shared
 * by the minimap and the world map.
 */
final class MapRoutes
{
	// The shape of an arrowhead on a map: half its width, and how far its back edge is notched in, as
	// fractions of its length. The length is given in pixels rather than tiles, since a map is drawn at
	// whatever scale it likes and an arrow has to stay big enough to make out without swamping the line.
	private static final double ARROW_HALF_WIDTH = 0.4;
	private static final double ARROW_NOTCH = 0.25;

	// No two arrows closer together than this on screen. Arrows sit a fixed number of tiles apart, so
	// without this they would pile into each other as a map is zoomed out. How many to leave out is
	// worked out from how far the map is zoomed, never from which of them happen to be on screen: a
	// thinning that depends on what is visible rearranges itself every time the map is panned.
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
	private static final BasicStroke DOT_EDGE = new BasicStroke(1);

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
	 * Where world tiles land on a map, and where the map ends. Both maps scale, turn and shift the world the same way
	 * everywhere, so each point is placed with a few sums from where one tile lands and the steps one tile east and one
	 * tile north make on screen.
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
			if (radius > 0)
			{
				double offX = x - middleX;
				double offY = y - middleY;
				return offX * offX + offY * offY <= radius * radius;
			}
			return box.contains(x, y);
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
	 * One route as a line, simplified to the map's zoom and drawn smoothly as straight pieces, each on its own, which
	 * Java2D draws far faster than one long path. With a danger colour, the stretches that pass close to where sea
	 * creatures that attack boats spawn are drawn in it instead. Only the runs of the route that reach the stretch of
	 * the world given, in tiles, are drawn.
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
			// line already reaches into it. The first run starts at the route's start, as the loop is closed by the
			// piece after its last point.
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
	private static Object[] smooth(Graphics2D graphics)
	{
		Object[] was = {graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING),
			graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)};
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return was;
	}

	private static void restore(Graphics2D graphics, Object[] hints)
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
	 * One straight piece, running a fraction of a pixel past each end, and on a round map only the part inside it.
	 * Nothing for a piece whose ends landed on the same spot.
	 */
	private static void drawPiece(Graphics2D graphics, MapView onto, Line2D.Double piece, double fromX, double fromY,
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
		if (onto.radius > 0)
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
		piece.setLine(fromX + alongX * start, fromY + alongY * start, fromX + alongX * end, fromY + alongY * end);
		graphics.draw(piece);
	}

	/**
	 * The arrows showing which way round a route is swum, at the spacing set for the ones on the water
	 * but thinned out so they never crowd each other on a map drawn small. Those on a stretch marked
	 * dangerous take the danger colour instead, when one is given. Each is two smooth strokes meeting at its tip, which
	 * Java2D draws faster than a filled arrowhead; the line is drawn over them.
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
			// The same skipping the line is drawn with. A run of the route covers one stretch of the way
			// round it, so the arrows on it are the ones whose distances fall inside that stretch, and the
			// rest are passed over without ever being worked out.
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
				if (!onto.contains(tipX, tipY) || step < 1e-6)
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
				barb.setLine(backX - alongY * aside, backY + alongX * aside, tipX, tipY);
				graphics.draw(barb);
				barb.setLine(backX + alongY * aside, backY - alongX * aside, tipX, tipY);
				graphics.draw(barb);
			}
		}
		restore(graphics, hints);
	}

	/**
	 * Where a shoal is right now, as a dot in the colour that marks it on the water.
	 */
	static void shoal(Graphics2D graphics, TrawlingPlusConfig config, Client client, Shoal shoal,
		MapView onto, int size)
	{
		double[] swimming = alongRoute(shoal, client);
		Point at = swimming == null ? null : onto.at(swimming[0], swimming[1]);
		if (at == null)
		{
			return;
		}

		graphics.setColor(config.shoalHeadingArrowColour());
		graphics.fillOval(at.getX() - size, at.getY() - size, size * 2, size * 2);
		// Outlined, so it stays visible whatever the map underneath it is coloured.
		graphics.setColor(Color.BLACK);
		graphics.setStroke(DOT_EDGE);
		graphics.drawOval(at.getX() - size, at.getY() - size, size * 2, size * 2);
	}

	/**
	 * Where a shoal is along its route, as the route's point there with the way it runs (x, y, and a step along it),
	 * so its mark sits on the line however the route is smoothed: smoothing rounds off bends, where the shoal itself
	 * swims wider. Where the game has it if it has no route, as x and y only; null if it can't be placed.
	 */
	static double[] alongRoute(Shoal shoal, Client client)
	{
		double[] position = shoal.position(client);
		ShoalRoute route = shoal.getRoute();
		return position == null || route == null ? position : route.pointAt(shoal.followRoute(position));
	}

	/**
	 * An arrowhead of the given length in pixels, centred on a point and pointing along a direction, which needn't be
	 * of any particular length but mustn't be nothing. Turned by the direction itself rather than by an angle worked
	 * out from it and back.
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
