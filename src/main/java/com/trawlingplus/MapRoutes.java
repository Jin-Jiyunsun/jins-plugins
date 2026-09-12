package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import net.runelite.api.Client;
import net.runelite.api.Point;

/**
 * Draws routes onto a map, wherever that map happens to be and at whatever scale it is drawn. Shared
 * by the minimap and the world map, which are otherwise separate overlays: the minimap has to be
 * drawn underneath the interface so the frame around it stays on top, and the world map on top of
 * the interface so it is not hidden by the map it belongs to.
 */
final class MapRoutes
{
	// Arrowheads, in pixels rather than tiles: a map is drawn at whatever scale it likes, and an arrow
	// has to stay big enough to make out and small enough not to swamp the line.
	// Half the width of an arrowhead, and how far its back edge is notched in, as fractions of its
	// length. The same shape the arrows on the water are drawn to.
	private static final double ARROW_HALF_WIDTH = 0.4;
	private static final double ARROW_NOTCH = 0.25;

	// No two arrows closer together than this on screen. Arrows sit a fixed number of tiles apart, so
	// without this they would pile into each other as a map is zoomed out.
	private static final int ARROW_GAP = 14;

	// How far along the route to look to see which way an arrow points, in tiles.
	private static final double ARROW_AIM = 2;

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
		int thickness, int stride)
	{
		Path2D line = new Path2D.Double();
		boolean drawing = false;
		for (int sample = 0; sample < route.sampleCount(); sample += stride)
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

		graphics.setColor(config.routeColour());
		graphics.setStroke(new BasicStroke(thickness));
		graphics.draw(line);
	}

	/**
	 * The arrows showing which way round a route is swum, at the spacing set for the ones on the water
	 * but thinned out so they never crowd each other on a map drawn small.
	 */
	static void arrows(Graphics2D graphics, TrawlingPlusConfig config, ShoalRoute route, Projection onto,
		int size)
	{
		if (!config.showDirectionArrows())
		{
			return;
		}

		int spacing = Math.max(TrawlingPlusConfig.MIN_ARROW_SPACING,
			Math.min(TrawlingPlusConfig.MAX_ARROW_SPACING, config.directionArrowSpacing()));
		graphics.setColor(config.directionArrowColour());

		Point last = null;
		for (int arrow = 1; arrow * spacing < route.length(); arrow++)
		{
			double[] point = route.pointAt(arrow * spacing);
			Point at = onto.at(point[0], point[1]);
			Point ahead = onto.at(point[0] + point[2] * ARROW_AIM, point[1] + point[3] * ARROW_AIM);
			if (at == null || ahead == null || (ahead.getX() == at.getX() && ahead.getY() == at.getY()))
			{
				// Off the map, or both ends of the aim landed on the same pixel and there is no
				// direction to read from them.
				continue;
			}

			if (last != null && Math.hypot(at.getX() - last.getX(), at.getY() - last.getY()) < ARROW_GAP)
			{
				// The map is drawn small enough that this arrow would sit on top of the last one.
				continue;
			}

			graphics.fill(arrowhead(at, Math.atan2(ahead.getY() - at.getY(), ahead.getX() - at.getX()), size));
			last = at;
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
