package com.trawlingplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class TrawlingPlusOverlay extends Overlay
{
	private static final Color ROUTE_COLOUR = new Color(0, 200, 255, 200);
	private static final Color STOP_COLOUR = new Color(255, 255, 255, 150);
	private static final Color NEXT_STOP_COLOUR = new Color(255, 200, 0, 230);

	// Round joins and caps so the short segments the curve is drawn with blend into one smooth line.
	private static final Stroke ROUTE_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	// Stops are drawn as a square this many tiles across, roughly the size of a shoal.
	private static final int STOP_SIZE = 3;

	// Direction arrows are drawn this far apart along a route, in tiles.
	private static final double ARROW_SPACING = 15;

	// An arrowhead's size in tiles: half its length, half its width, and how far behind its middle
	// the notch between its wings sits.
	private static final double ARROW_HALF_LENGTH = 0.75;
	private static final double ARROW_HALF_WIDTH = 0.6;
	private static final double ARROW_NOTCH = 0.3;

	private final Client client;
	private final TrawlingPlusPlugin plugin;
	private final TrawlingPlusConfig config;

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
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (config.routeDisplay() == TrawlingPlusConfig.RouteDisplay.WHOLE_ROUTE)
		{
			drawAllRoutes(graphics);
		}
		else
		{
			drawNextStops(graphics);
		}

		if (antialiasing != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
		return null;
	}

	private void drawAllRoutes(Graphics2D graphics)
	{
		// Every route that reaches into the loaded scene, whether or not its shoal is in view.
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return;
		}

		for (ShoalRoute route : plugin.getRoutes())
		{
			if (route.overlaps(view.getBaseX(), view.getBaseY(), view.getBaseX() + view.getSizeX(), view.getBaseY() + view.getSizeY()))
			{
				drawWholeRoute(graphics, view, route);
			}
		}
	}

	private void drawNextStops(Graphics2D graphics)
	{
		for (Shoal shoal : plugin.getShoals())
		{
			ShoalRoute route = shoal.getRoute();
			WorldView view = shoal.parentView(client);
			double[] position = shoal.position(client);
			if (route != null && view != null && position != null)
			{
				drawToNextStop(graphics, view, route, position);
			}
		}
	}

	private void drawWholeRoute(Graphics2D graphics, WorldView view, ShoalRoute route)
	{
		Line line = new Line();
		int samples = route.sampleCount();
		for (int step = 0; step <= samples; step++)
		{
			int sample = step % samples;
			line.add(toCanvas(view, route.sampleX(sample), route.sampleY(sample)));
		}
		drawLine(graphics, line);
		if (config.showDirectionArrows())
		{
			drawArrows(graphics, view, route, 0, route.length());
		}

		for (int stop = 0; stop < route.stopCount(); stop++)
		{
			drawStop(graphics, view, route, stop, STOP_COLOUR);
		}
	}

	private void drawToNextStop(Graphics2D graphics, WorldView view, ShoalRoute route, double[] position)
	{
		double distance = route.project(position[0], position[1]).distance;
		int next = route.nextStop(distance);
		int from = route.sampleAt(distance);
		int steps = Math.floorMod(route.sampleAt(route.stopDistance(next)) - from, route.sampleCount());

		Line line = new Line();
		line.add(toCanvas(view, position[0], position[1]));
		for (int step = 0; step < steps; step++)
		{
			int sample = (from + step) % route.sampleCount();
			line.add(toCanvas(view, route.sampleX(sample), route.sampleY(sample)));
		}
		line.add(toCanvas(view, route.stopX(next), route.stopY(next)));
		drawLine(graphics, line);
		if (config.showDirectionArrows())
		{
			drawArrows(graphics, view, route, distance, route.forward(distance, route.stopDistance(next)));
		}

		drawStop(graphics, view, route, next, NEXT_STOP_COLOUR);
	}

	private static void drawLine(Graphics2D graphics, Line line)
	{
		graphics.setColor(ROUTE_COLOUR);
		graphics.setStroke(ROUTE_STROKE);
		graphics.draw(line.path);
	}

	/**
	 * Draws the route's arrows that fall within a stretch of it, starting at a distance round the route.
	 * Arrows sit at fixed places round the route, so they don't slide along as the shoal swims.
	 */
	private void drawArrows(Graphics2D graphics, WorldView view, ShoalRoute route, double from, double stretch)
	{
		graphics.setColor(ROUTE_COLOUR);
		for (int arrow = 0; arrow * ARROW_SPACING < route.length(); arrow++)
		{
			double at = arrow * ARROW_SPACING;
			if (route.forward(from, at) < stretch)
			{
				drawArrow(graphics, view, route, route.sampleAt(at));
			}
		}
	}

	/**
	 * Draws an arrowhead lying flat on the water at a drawing point, pointing along the route.
	 */
	private void drawArrow(Graphics2D graphics, WorldView view, ShoalRoute route, int sample)
	{
		int next = (sample + 1) % route.sampleCount();
		double x = route.sampleX(sample);
		double y = route.sampleY(sample);
		double dx = route.sampleX(next) - x;
		double dy = route.sampleY(next) - y;
		double length = Math.hypot(dx, dy);
		if (length == 0)
		{
			return;
		}
		dx /= length;
		dy /= length;

		Point tip = toCanvas(view, x + dx * ARROW_HALF_LENGTH, y + dy * ARROW_HALF_LENGTH);
		Point left = toCanvas(view, x - dx * ARROW_HALF_LENGTH - dy * ARROW_HALF_WIDTH, y - dy * ARROW_HALF_LENGTH + dx * ARROW_HALF_WIDTH);
		Point notch = toCanvas(view, x - dx * ARROW_NOTCH, y - dy * ARROW_NOTCH);
		Point right = toCanvas(view, x - dx * ARROW_HALF_LENGTH + dy * ARROW_HALF_WIDTH, y - dy * ARROW_HALF_LENGTH - dx * ARROW_HALF_WIDTH);
		if (tip == null || left == null || notch == null || right == null)
		{
			return;
		}

		Path2D.Double arrow = new Path2D.Double();
		arrow.moveTo(tip.getX(), tip.getY());
		arrow.lineTo(left.getX(), left.getY());
		arrow.lineTo(notch.getX(), notch.getY());
		arrow.lineTo(right.getX(), right.getY());
		arrow.closePath();
		graphics.fill(arrow);
	}

	private void drawStop(Graphics2D graphics, WorldView view, ShoalRoute route, int stop, Color colour)
	{
		LocalPoint local = toLocal(view, route.stopX(stop), route.stopY(stop));
		Polygon area = local == null ? null : Perspective.getCanvasTileAreaPoly(client, local, STOP_SIZE);
		if (area != null)
		{
			OverlayUtil.renderPolygon(graphics, area, colour);
		}
	}

	private Point toCanvas(WorldView view, double x, double y)
	{
		LocalPoint local = toLocal(view, x, y);
		return local == null ? null : Perspective.localToCanvas(client, local, view.getPlane());
	}

	/**
	 * Converts world tile coordinates to a local point, or null if they're outside the loaded scene.
	 */
	private static LocalPoint toLocal(WorldView view, double x, double y)
	{
		int localX = (int) Math.round((x - view.getBaseX()) * Perspective.LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE;
		int localY = (int) Math.round((y - view.getBaseY()) * Perspective.LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE;
		if (localX < 0 || localY < 0
			|| localX >= view.getSizeX() * Perspective.LOCAL_TILE_SIZE
			|| localY >= view.getSizeY() * Perspective.LOCAL_TILE_SIZE)
		{
			return null;
		}
		return new LocalPoint(localX, localY, view);
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
