package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import net.runelite.api.Client;
import net.runelite.api.Point;

/**
 * Draws routes onto a map, wherever that map happens to be and at whatever scale it is drawn. Shared
 * by the minimap and the world map, which work out where a tile lands in entirely different ways.
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

	// How far along the route to look to see which way an arrow points, in tiles.
	private static final double ARROW_AIM = 2;

	// About how far apart the points of the drawn line should be on screen, in pixels. Closer than this
	// and they land on top of each other; further apart and a bend starts to show as a cut corner.
	private static final double LINE_STEP = 2;

	private MapRoutes()
	{
	}

	/**
	 * Turns world tile coordinates into a place on whichever map is being drawn, or null when they
	 * fall outside it.
	 */
	interface Projection
	{
		Point at(double x, double y);
	}

	/**
	 * One route as a line, broken wherever it leaves the map.
	 */
	static void route(Graphics2D graphics, TrawlingPlusConfig config, ShoalRoute route, Projection onto,
		int thickness, double pixelsPerTile, double fromX, double fromY, double toX, double toY)
	{
		// Every so many of the route's points, chosen from how far the map is zoomed. A fixed number of
		// them cuts the corners once the map is zoomed in far enough to see it, and wastes the work of
		// placing points on top of each other once it is zoomed out.
		int stride = 1;
		if (pixelsPerTile > 0 && route.sampleCount() > 0)
		{
			double apart = route.length() / route.sampleCount();
			stride = Math.max(1, (int) (LINE_STEP / (pixelsPerTile * apart)));
		}

		Path2D line = new Path2D.Double();
		boolean drawing = false;
		for (int block = 0; block < route.blockCount(); block++)
		{
			// Only the runs of the route that reach what is being shown. Most of a route is somewhere
			// else entirely, and a run is dismissed in four comparisons rather than by placing all the
			// points inside it and throwing them away one at a time.
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				drawing = false;
				continue;
			}

			int start = route.blockFrom(block);
			for (int sample = start + (stride - start % stride) % stride; sample < route.blockTo(block);
				sample += stride)
			{
				Point at = onto.at(route.sampleX(sample), route.sampleY(sample));
				if (at == null)
				{
					// Off the map, so the line picks up again where it comes back.
					drawing = false;
					continue;
				}

				if (drawing)
				{
					line.lineTo(at.getX(), at.getY());
				}
				else
				{
					line.moveTo(at.getX(), at.getY());
					drawing = true;
				}
			}
		}

		graphics.setColor(config.routeColour());
		graphics.setStroke(new BasicStroke(thickness));
		graphics.draw(line);
	}

	/**
	 * The stretches of a route that pass close to where sea creatures that attack boats spawn, drawn over its
	 * line in the given colour and broken wherever the route is safe or leaves the map.
	 */
	static void dangerStretches(Graphics2D graphics, ShoalRoute route, Projection onto, Color colour, int thickness,
		double pixelsPerTile, double fromX, double fromY, double toX, double toY)
	{
		// The same thinning of points as the route line itself, so the two lie on top of each other.
		int stride = 1;
		if (pixelsPerTile > 0 && route.sampleCount() > 0)
		{
			double apart = route.length() / route.sampleCount();
			stride = Math.max(1, (int) (LINE_STEP / (pixelsPerTile * apart)));
		}

		Path2D line = new Path2D.Double();
		boolean drawing = false;
		for (int block = 0; block < route.blockCount(); block++)
		{
			if (!route.blockWithin(block, fromX, fromY, toX, toY))
			{
				drawing = false;
				continue;
			}

			int start = route.blockFrom(block);
			for (int sample = start + (stride - start % stride) % stride; sample < route.blockTo(block);
				sample += stride)
			{
				Point at = route.dangerAt(sample) ? onto.at(route.sampleX(sample), route.sampleY(sample)) : null;
				if (at == null)
				{
					drawing = false;
					continue;
				}

				if (drawing)
				{
					line.lineTo(at.getX(), at.getY());
				}
				else
				{
					line.moveTo(at.getX(), at.getY());
					drawing = true;
				}
			}
		}

		graphics.setColor(colour);
		graphics.setStroke(new BasicStroke(thickness));
		graphics.draw(line);
	}

	/**
	 * The arrows showing which way round a route is swum, at the spacing set for the ones on the water
	 * but thinned out so they never crowd each other on a map drawn small. Those on a stretch marked
	 * dangerous take the danger colour instead, when one is given.
	 */
	static void arrows(Graphics2D graphics, TrawlingPlusConfig config, ShoalRoute route, Projection onto,
		int size, double pixelsPerTile, double fromX, double fromY, double toX, double toY, Color dangerColour)
	{
		if (!config.showDirectionArrows())
		{
			return;
		}

		int spacing = Math.max(TrawlingPlusConfig.MIN_ARROW_SPACING,
			Math.min(TrawlingPlusConfig.MAX_ARROW_SPACING, config.directionArrowSpacing()));

		// Every so many of the arrows, chosen so that what is left of them is far enough apart on screen.
		// They then sit at fixed places round the route and stay put however the map is moved about.
		int every = 1;
		if (pixelsPerTile > 0)
		{
			every = Math.max(1, (int) Math.ceil(ARROW_GAP / pixelsPerTile / spacing));
		}

		double apart = (double) every * spacing;
		Color arrowColour = config.directionArrowColour();
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
				double[] point = route.pointAt(arrow * apart);
				if (point[0] < fromX || point[0] > toX || point[1] < fromY || point[1] > toY)
				{
					// Inside a run that reaches the map, but not itself on the part of it being shown.
					continue;
				}

				Point at = onto.at(point[0], point[1]);
				if (at == null)
				{
					continue;
				}

				// Which way the arrow points is read from a second place further along the route. That one
				// can be off the map while the arrow itself is on it, which happens along the edge once the
				// map is zoomed in far enough for a couple of tiles to be a long way across the screen, so
				// the place behind the arrow is taken instead and the direction turned around.
				Point ahead = onto.at(point[0] + point[2] * ARROW_AIM, point[1] + point[3] * ARROW_AIM);
				Point from = ahead != null ? at
					: onto.at(point[0] - point[2] * ARROW_AIM, point[1] - point[3] * ARROW_AIM);
				Point to = ahead != null ? ahead : at;
				if (from == null || to == null || (to.getX() == from.getX() && to.getY() == from.getY()))
				{
					// Neither way along the route could be placed, or both ends landed on the same pixel and
					// there is no direction to read from them.
					continue;
				}

				// An arrow on a stretch drawn red is red too, so it doesn't break the stretch up.
				graphics.setColor(dangerColour != null && route.dangerAtDistance(arrow * apart) ? dangerColour : arrowColour);
				graphics.fill(
					arrowhead(at, Math.atan2(to.getY() - from.getY(), to.getX() - from.getX()), size));
			}
		}
	}

	/**
	 * Where a shoal is right now, as a dot in the colour that marks it on the water.
	 */
	static void shoal(Graphics2D graphics, TrawlingPlusConfig config, Client client, Shoal shoal,
		Projection onto, int size)
	{
		double[] swimming = shoal.position(client);
		Point at = swimming == null ? null : onto.at(swimming[0], swimming[1]);
		if (at == null)
		{
			return;
		}

		graphics.setColor(config.shoalHeadingArrowColour());
		graphics.fillOval(at.getX() - size, at.getY() - size, size * 2, size * 2);
		// Outlined, so it stays visible whatever the map underneath it is coloured.
		graphics.setColor(Color.BLACK);
		graphics.setStroke(new BasicStroke(1));
		graphics.drawOval(at.getX() - size, at.getY() - size, size * 2, size * 2);
	}

	/**
	 * An arrowhead of the given length in pixels, centred on a point and pointing along an angle.
	 */
	private static Path2D arrowhead(Point at, double angle, int length)
	{
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double half = length * ARROW_HALF_WIDTH;
		double notch = length * ARROW_NOTCH;

		// Along the arrow, then across it, for the tip, both barbs and the notch between them.
		double[][] corners = {{length / 2.0, 0}, {-length / 2.0, half}, {-length / 2.0 + notch, 0},
			{-length / 2.0, -half}};
		Path2D head = new Path2D.Double();
		for (int corner = 0; corner < corners.length; corner++)
		{
			double x = at.getX() + corners[corner][0] * cos - corners[corner][1] * sin;
			double y = at.getY() + corners[corner][0] * sin + corners[corner][1] * cos;
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
