package com.livelyfishingspots;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import static com.livelyfishingspots.RiverSpotFish.*;

/**
 * Debug: draws the rivers and lakes near the player, as the debug drawing toggles say: ranges, banks, path and room,
 * spots, fish, schools and picked points; and the fish counts and map memory for the debug panel.
 */
final class RiverDrawing
{
	private final Client client;
	private final RiverSpotFish rivers;
	// Scratch for River.at.
	private final double[] point = new double[5];
	// The route being picked.
	private List<WorldPoint> picking = List.of();

	RiverDrawing(Client client, RiverSpotFish rivers)
	{
		this.client = client;
		this.rivers = rivers;
	}

	/**
	 * Debug: hatches a path's room, the shape between its room edges.
	 */
	private void shade(Graphics2D graphics, WorldView view, Shoal shoal, River way)
	{
		// The room as one shape: down the left edge, back up the right.
		int points = way.pathX.length;
		for (int n = 0; n < 2 * points; n++)
		{
			int k = n < points ? n : 2 * points - 1 - n;
			int ahead = Math.min(k + 1, points - 1);
			int behind = Math.max(k - 1, 0);
			double d = Math.max(1e-6, Math.hypot(way.pathX[ahead] - way.pathX[behind], way.pathY[ahead] - way.pathY[behind]));
			double dx = (way.pathX[ahead] - way.pathX[behind]) / d;
			double dy = (way.pathY[ahead] - way.pathY[behind]) / d;
			double out = n < points ? way.room[k] : -way.room[k];
			mark(shoal, way.pathX[k] - dy * out, way.pathY[k] + dx * out);
		}
		project(view);
		Polygon room = new Polygon();
		for (int k = 0; k < marked; k++)
		{
			if (screenX[k] != Integer.MIN_VALUE)
			{
				room.addPoint(screenX[k], screenY[k]);
			}
		}
		marked = 0;
		Paint before = graphics.getPaint();
		graphics.setPaint(HATCH);
		graphics.fill(room);
		graphics.setPaint(before);
	}

	/**
	 * Debug: the line round a river's water where it's BANK_GAP from the bank, as segments (x, y, x, y per segment),
	 * traced over the cells' bank distances by marching squares; worked out the first time it's drawn.
	 */
	private static double[] roomEdge(River river)
	{
		if (river.roomEdge != null)
		{
			return river.roomEdge;
		}
		int width = river.width;
		int height = river.height;
		// The bank distances blurred (each cell the average of it and its neighbours), so the line is smooth.
		double[] near = new double[width * height];
		for (int j = 0; j < height; j++)
		{
			for (int i = 0; i < width; i++)
			{
				double sum = 0;
				int cells = 0;
				for (int dj = -1; dj <= 1; dj++)
				{
					for (int di = -1; di <= 1; di++)
					{
						if (i + di >= 0 && j + dj >= 0 && i + di < width && j + dj < height)
						{
							sum += river.clearance[(j + dj) * width + i + di];
							cells++;
						}
					}
				}
				near[j * width + i] = sum / cells;
			}
		}
		double[] segments = new double[64];
		int count = 0;
		double[] cross = new double[8];
		for (int j = 0; j + 1 < river.height; j++)
		{
			for (int i = 0; i + 1 < width; i++)
			{
				// The square between four cells' middles: corners south-west, south-east, north-east, north-west.
				double a = near[j * width + i] - BANK_GAP;
				double b = near[j * width + i + 1] - BANK_GAP;
				double c = near[(j + 1) * width + i + 1] - BANK_GAP;
				double d = near[(j + 1) * width + i] - BANK_GAP;
				double x = river.x0 + i * CELL + CELL / 2.0;
				double y = river.y0 + j * CELL + CELL / 2.0;
				// Where the line crosses each side the corners differ on, in order round the square.
				int found = 0;
				double[][] sides = {{a, b, x, y, CELL, 0}, {b, c, x + CELL, y, 0, CELL}, {c, d, x + CELL, y + CELL, -CELL, 0},
					{d, a, x, y + CELL, 0, -CELL}};
				for (double[] side : sides)
				{
					if ((side[0] > 0) != (side[1] > 0))
					{
						double t = side[0] / (side[0] - side[1]);
						cross[found++] = side[2] + side[4] * t;
						cross[found++] = side[3] + side[5] * t;
					}
				}
				// Two crossings make one segment; four (a saddle) make two.
				for (int k = 0; k + 3 < found; k += 4)
				{
					if (count + 4 > segments.length)
					{
						segments = Arrays.copyOf(segments, segments.length * 2);
					}
					System.arraycopy(cross, k, segments, count, 4);
					count += 4;
				}
			}
		}
		river.roomEdge = Arrays.copyOf(segments, count);
		return river.roomEdge;
	}

	/**
	 * Debug: a river's bank cells' middles, x then y, worked out the first time they're drawn.
	 */
	private static int[] banks(River river)
	{
		if (river.banks == null)
		{
			int width = river.width;
			int cells = width * river.height;
			int count = 0;
			for (int c = 0; c < cells; c++)
			{
				count += river.clearance[c] > 0 && river.clearance[c] <= CELL ? 1 : 0;
			}
			int[] banks = new int[count * 2];
			int k = 0;
			for (int c = 0; c < cells; c++)
			{
				if (river.clearance[c] > 0 && river.clearance[c] <= CELL)
				{
					banks[k++] = river.x0 + (c % width) * CELL + CELL / 2;
					banks[k++] = river.y0 + (c / width) * CELL + CELL / 2;
				}
			}
			river.banks = banks;
		}
		return river.banks;
	}

	// Debug: places waiting to go on screen together (projecting one at a time works the camera's turn out for
	// each), local x, y and height, and where they land.
	private float[] drawX = new float[256];
	private float[] drawY = new float[256];
	private float[] drawZ = new float[256];
	private int[] screenX = new int[256];
	private int[] screenY = new int[256];
	private int marked;

	/**
	 * Debug: marks a place on the water to draw, at the water's own height, so it isn't lifted onto bridges.
	 */
	private void mark(Shoal shoal, double x, double y)
	{
		markAt(x, y, rivers.waterHeight((int) x, (int) y, shoal.plane));
	}

	private void markAt(double x, double y, int height)
	{
		if (marked == drawX.length)
		{
			int size = marked * 2;
			drawX = Arrays.copyOf(drawX, size);
			drawY = Arrays.copyOf(drawY, size);
			drawZ = Arrays.copyOf(drawZ, size);
			screenX = Arrays.copyOf(screenX, size);
			screenY = Arrays.copyOf(screenY, size);
		}
		drawX[marked] = (float) x;
		drawY[marked] = (float) y;
		drawZ[marked] = height;
		marked++;
	}

	/**
	 * Debug: puts the marked places on screen, all at once; those behind the camera get Integer.MIN_VALUE.
	 */
	private void project(WorldView view)
	{
		Perspective.modelToCanvas(client, view, marked, 0, 0, 0, 0, drawX, drawY, drawZ, screenX, screenY);
	}

	/**
	 * Debug: draws the marked places joined up, skipping any behind the camera, and clears them.
	 */
	private void line(Graphics2D graphics, WorldView view)
	{
		project(view);
		boolean whole = true;
		for (int k = 0; k < marked && whole; k++)
		{
			whole = screenX[k] != Integer.MIN_VALUE;
		}
		if (whole)
		{
			graphics.drawPolyline(screenX, screenY, marked);
		}
		else
		{
			for (int k = 1; k < marked; k++)
			{
				if (screenX[k - 1] != Integer.MIN_VALUE && screenX[k] != Integer.MIN_VALUE)
				{
					graphics.drawLine(screenX[k - 1], screenY[k - 1], screenX[k], screenY[k]);
				}
			}
		}
		marked = 0;
	}

	/**
	 * Debug: a line across the river at a distance along the path.
	 */
	private void acrossLine(Graphics2D graphics, WorldView view, Shoal shoal, double s, Color colour)
	{
		shoal.river.at(s, point);
		double room = point[4] + BANK_GAP;
		mark(shoal, point[0] - point[3] * room, point[1] + point[2] * room);
		mark(shoal, point[0] + point[3] * room, point[1] - point[2] * room);
		graphics.setColor(colour);
		line(graphics, view);
	}

	// which parts of the debug drawing to show (toggles).
	static boolean drawRanges = true;
	static boolean drawShade = true;
	static boolean drawBanks = true;
	static boolean drawPath = true;
	static boolean drawSpots = true;
	static boolean drawFish = true;
	static boolean drawSchools = true;
	static boolean drawPicked = true;
	// Debug: length of a school's heading arrow, local units.
	private static final double SCHOOL_ARROW = 160;
	// Debug: room drawn round each fish in a school's outline, local units.
	private static final double SCHOOL_PAD = 24;
	// Debug: forks' picked tiles and paths, light blue.
	private static final Color FORK_COLOUR = new Color(120, 200, 255);
	// Debug: the room's hatching, teal lines going up to the right.
	private static final TexturePaint HATCH = hatch();
	// Debug: cos and sin of the 8 points round each fish in a school's outline.
	private static final double[][] PAD_WAYS = ways(8);

	/**
	 * Debug: a small tile of diagonal teal lines, repeated to hatch the room.
	 */
	private static TexturePaint hatch()
	{
		BufferedImage tile = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int k = 0; k < 8; k++)
		{
			tile.setRGB(k, 7 - k, new Color(0, 160, 160, 110).getRGB());
		}
		return new TexturePaint(tile, new Rectangle(0, 0, 8, 8));
	}

	/**
	 * Debug: a route's points as tile outlines, numbered in order, the first marked start and, when finished, the
	 * last marked end.
	 */
	private void routePoints(Graphics2D graphics, WorldView view, WorldPoint[] points, Color colour, boolean finished)
	{
		routePoints(graphics, view, points, colour, finished, "", "");
	}

	/**
	 * Debug: as routePoints, the first and last labelled with a word before start and end, and a note after start.
	 */
	private void routePoints(Graphics2D graphics, WorldView view, WorldPoint[] points, Color colour, boolean finished,
		String kind, String startNote)
	{
		graphics.setColor(colour);
		for (int k = 0; k < points.length; k++)
		{
			LocalPoint at = LocalPoint.fromWorld(view, points[k]);
			Polygon tile = at == null ? null : Perspective.getCanvasTilePoly(client, at);
			if (tile == null)
			{
				continue;
			}
			graphics.draw(tile);
			String text = (k + 1) + (k == 0 ? kind + " start" + startNote : finished && k == points.length - 1 ? kind + " end" : "");
			Point label = Perspective.getCanvasTextLocation(client, graphics, at, text, 0);
			if (label != null)
			{
				graphics.drawString(text, label.getX(), label.getY());
			}
		}
	}

	/**
	 * Debug: a school's bounds, the outline round its fish with a little room.
	 */
	private void outline(Graphics2D graphics, WorldView view, Shoal shoal, Group group)
	{
		List<double[]> points = new ArrayList<>();
		for (Swimmer member : group.members)
		{
			for (double[] way : PAD_WAYS)
			{
				points.add(new double[]{member.x + SCHOOL_PAD * way[0], member.y + SCHOOL_PAD * way[1]});
			}
		}
		// Convex hull, by the monotone chain.
		points.sort((p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(p[1], q[1]));
		double[][] hull = new double[2 * points.size()][];
		int k = 0;
		for (int pass = 0; pass < 2; pass++)
		{
			int start = k;
			for (int n = 0; n < points.size(); n++)
			{
				double[] p = points.get(pass == 0 ? n : points.size() - 1 - n);
				while (k >= start + 2 && (hull[k - 1][0] - hull[k - 2][0]) * (p[1] - hull[k - 2][1])
					- (hull[k - 1][1] - hull[k - 2][1]) * (p[0] - hull[k - 2][0]) <= 0)
				{
					k--;
				}
				hull[k++] = p;
			}
			k--;
		}
		// Closed: back to the first point.
		for (int n = 0; n <= k; n++)
		{
			mark(shoal, hull[n % k][0], hull[n % k][1]);
		}
		line(graphics, view);
	}

	/**
	 * Debug: marks an arrow on the water between two places, its head this long (local units): the tail, tip and the
	 * head's two sides. Drawn by drawArrow once projected.
	 */
	private void markArrow(Shoal shoal, double fromX, double fromY, double toX, double toY, double head)
	{
		double length = Math.max(1e-6, Math.hypot(toX - fromX, toY - fromY));
		double ux = (toX - fromX) / length;
		double uy = (toY - fromY) / length;
		double back = Math.min(head, length / 2);
		double side = back * 0.6;
		mark(shoal, fromX, fromY);
		mark(shoal, toX, toY);
		mark(shoal, toX - ux * back - uy * side, toY - uy * back + ux * side);
		mark(shoal, toX - ux * back + uy * side, toY - uy * back - ux * side);
	}

	/**
	 * Debug: draws the arrow marked from this place on, once projected.
	 */
	private void drawArrow(Graphics2D graphics, int at)
	{
		for (int k = at; k < at + 4; k++)
		{
			if (screenX[k] == Integer.MIN_VALUE)
			{
				return;
			}
		}
		graphics.drawLine(screenX[at], screenY[at], screenX[at + 1], screenY[at + 1]);
		graphics.drawLine(screenX[at + 1], screenY[at + 1], screenX[at + 2], screenY[at + 2]);
		graphics.drawLine(screenX[at + 1], screenY[at + 1], screenX[at + 3], screenY[at + 3]);
	}

	/**
	 * Debug: ranges, banks, path and room, spots, fish steering, schools and picked points, each part shown by its
	 * toggle.
	 */
	void drawDebug(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		Player me = client.getLocalPlayer();
		if (view == null || me == null)
		{
			return;
		}
		LocalPoint meAt = me.getLocalLocation();
		if (drawRanges && meAt != null)
		{
			// The square rivers are mapped within round the player, as clip() cuts them: tile middles, so drawn half a
			// tile out.
			int reach = loadRange() + MAP_MORE;
			int lowX = Math.max(ROUTE_MARGIN, meAt.getSceneX() - reach);
			int lowY = Math.max(ROUTE_MARGIN, meAt.getSceneY() - reach);
			int highX = Math.min(view.getSizeX() - 1 - ROUTE_MARGIN, meAt.getSceneX() + reach);
			int highY = Math.min(view.getSizeY() - 1 - ROUTE_MARGIN, meAt.getSceneY() + reach);
			int[][] corners = {{lowX * 128, lowY * 128}, {highX * 128 + 128, lowY * 128},
				{highX * 128 + 128, highY * 128 + 128}, {lowX * 128, highY * 128 + 128}, {lowX * 128, lowY * 128}};
			int plane = me.getWorldLocation().getPlane();
			for (int side = 0; side < 4; side++)
			{
				int[] from = corners[side];
				int[] to = corners[side + 1];
				int steps = Math.max(1, (Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1])) / 128);
				for (int k = side == 0 ? 0 : 1; k <= steps; k++)
				{
					int x = from[0] + (to[0] - from[0]) * k / steps;
					int y = from[1] + (to[1] - from[1]) * k / steps;
					markAt(x, y, Perspective.getTileHeight(client, new LocalPoint(x, y, view.getId()), plane));
				}
			}
			graphics.setColor(Color.CYAN);
			line(graphics, view);
		}
		if (drawPicked)
		{
			for (WorldPoint[] route : rivers.routes)
			{
				routePoints(graphics, view, route, Color.YELLOW, true);
			}
			routePoints(graphics, view, picking.toArray(new WorldPoint[0]), Color.MAGENTA, false);
			// Each baked river's forks' picked tiles.
			for (WorldPoint[] route : rivers.routes)
			{
				List<int[]> forks = rivers.bakedBranches(route[0]);
				List<Integer> shares = Bakes.bakedBranchShares(rivers, route[0]);
				for (int f = 0; f < forks.size(); f++)
				{
					int[] fork = forks.get(f);
					WorldPoint[] tiles = new WorldPoint[fork.length / 2];
					for (int k = 0; k < tiles.length; k++)
					{
						tiles[k] = new WorldPoint(fork[2 * k], fork[2 * k + 1], route[0].getPlane());
					}
					int share = f < shares.size() ? shares.get(f) : -1;
					routePoints(graphics, view, tiles, FORK_COLOUR, true, " fork",
						share >= 0 ? " " + share + "%" : " by width");
				}
			}
		}
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		for (Shoal shoal : rivers.shoals)
		{
			River river = shoal.river;
			if (drawBanks)
			{
				// Only those on screen are drawn.
				int[] banks = banks(river);
				for (int k = 0; k + 1 < banks.length; k += 2)
				{
					mark(shoal, banks[k], banks[k + 1]);
				}
				project(view);
				graphics.setColor(new Color(255, 160, 0, 160));
				for (int k = 0; k < marked; k++)
				{
					if (screenX[k] >= 1 && screenY[k] >= 1 && screenX[k] < width - 1 && screenY[k] < height - 1)
					{
						graphics.fillRect(screenX[k] - 1, screenY[k] - 1, 3, 3);
					}
				}
				marked = 0;
			}
			if (shoal.lake && drawPicked)
			{
				// Spawn points.
				for (double[] spawn : shoal.spawns)
				{
					mark(shoal, spawn[0], spawn[1]);
				}
				project(view);
				graphics.setColor(Color.YELLOW);
				for (int k = 0; k < marked; k++)
				{
					if (screenX[k] != Integer.MIN_VALUE)
					{
						graphics.drawOval(screenX[k] - 6, screenY[k] - 6, 12, 12);
					}
				}
				marked = 0;
			}
			if (drawPath && river.pathX != null)
			{
				for (int k = 0; k < river.pathX.length; k++)
				{
					mark(shoal, river.pathX[k], river.pathY[k]);
				}
				int end = marked - 1;
				project(view);
				graphics.setColor(Color.CYAN);
				if (screenX[end] != Integer.MIN_VALUE)
				{
					graphics.fillOval(screenX[end] - 4, screenY[end] - 4, 8, 8);
				}
				line(graphics, view);
				graphics.setColor(FORK_COLOUR);
				for (Branch branch : river.branches)
				{
					for (int k = 0; k < branch.line.pathX.length; k++)
					{
						mark(shoal, branch.line.pathX[k], branch.line.pathY[k]);
					}
					line(graphics, view);
				}
			}
			// The room's edge: where the water is BANK_GAP from the bank.
			if (drawPath && river.pathX != null)
			{
				double[] edge = roomEdge(river);
				for (int k = 0; k + 1 < edge.length; k += 2)
				{
					mark(shoal, edge[k], edge[k + 1]);
				}
				project(view);
				graphics.setColor(new Color(0, 160, 160, 160));
				for (int k = 0; k + 1 < marked; k += 2)
				{
					if (screenX[k] != Integer.MIN_VALUE && screenX[k + 1] != Integer.MIN_VALUE)
					{
						graphics.drawLine(screenX[k], screenY[k], screenX[k + 1], screenY[k + 1]);
					}
				}
				marked = 0;
			}
			// The room of the river and its side channels, hatched.
			if (drawShade && river.pathX != null)
			{
				shade(graphics, view, shoal, river);
				for (Branch branch : river.branches)
				{
					shade(graphics, view, shoal, branch.line);
				}
			}
			if (drawRanges)
			{
				// The tiles mapped for it: the outline of its box.
				double right = river.x0 + river.width * CELL;
				double top = river.y0 + river.height * CELL;
				double[][] corners = {{river.x0, river.y0}, {right, river.y0}, {right, top}, {river.x0, top},
					{river.x0, river.y0}};
				for (int side = 0; side < 4; side++)
				{
					double[] from = corners[side];
					double[] to = corners[side + 1];
					int steps = (int) Math.max(1, (Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1])) / 128);
					for (int k = side == 0 ? 0 : 1; k <= steps; k++)
					{
						mark(shoal, from[0] + (to[0] - from[0]) * k / steps, from[1] + (to[1] - from[1]) * k / steps);
					}
				}
				graphics.setColor(new Color(180, 120, 255));
				line(graphics, view);
				// The stretch with fish round the player, and where they're drawn.
				if (!shoal.lake)
				{
					acrossLine(graphics, view, shoal, shoal.windowFrom, Color.ORANGE);
					acrossLine(graphics, view, shoal, shoal.windowTo, Color.ORANGE);
					acrossLine(graphics, view, shoal, Math.max(0, shoal.playerAt - FISH_RANGE * 128.0), Color.YELLOW);
					acrossLine(graphics, view, shoal, Math.min(river.length, shoal.playerAt + FISH_RANGE * 128.0),
						Color.YELLOW);
				}
			}
			for (Circle circle : shoal.circles.values())
			{
				if (!drawSpots)
				{
					break;
				}
				// Where passing fish decide whether to join, and the ring's own line.
				if (!shoal.lake)
				{
					acrossLine(graphics, view, shoal, circle.along - JOIN_BEFORE, Color.MAGENTA);
					acrossLine(graphics, view, shoal, circle.along, Color.GREEN);
				}
				graphics.setColor(Color.GREEN);
				for (int lane = 0; lane < circle.lanes.length; lane++)
				{
					for (int a = 0; a <= LANE_ANGLES; a++)
					{
						double[] way = LANE_WAYS[a % LANE_ANGLES];
						double out = circle.pulled[lane][a % LANE_ANGLES];
						mark(shoal, circle.x + out * way[0], circle.y + out * way[1]);
					}
					line(graphics, view);
				}
				// Avoided area.
				double out = circle.radius + CIRCLE_CLEARANCE;
				for (int a = 0; a <= LANE_ANGLES; a++)
				{
					double[] way = LANE_WAYS[a % LANE_ANGLES];
					mark(shoal, circle.x + out * way[0], circle.y + out * way[1]);
				}
				graphics.setColor(Color.RED);
				line(graphics, view);
				mark(shoal, circle.x, circle.y);
				project(view);
				marked = 0;
				graphics.setColor(Color.GREEN);
				if (screenX[0] != Integer.MIN_VALUE)
				{
					graphics.drawLine(screenX[0] - 5, screenY[0], screenX[0] + 5, screenY[0]);
					graphics.drawLine(screenX[0], screenY[0] - 5, screenX[0], screenY[0] + 5);
				}
				LocalPoint at = circle.npc.getLocalLocation();
				Point label = at == null ? null : Perspective.localToCanvas(client, at, shoal.plane, 150);
				if (label != null)
				{
					String text = String.valueOf(circle.npc.getId());
					graphics.setColor(Color.WHITE);
					graphics.drawString(text, label.getX() - graphics.getFontMetrics().stringWidth(text) / 2, label.getY());
				}
			}
			// Each dead body: lining up with the river, and how much, or spinning.
			for (Body body : drawFish ? shoal.bodies : List.<Body>of())
			{
				if (Double.isNaN(body.x))
				{
					continue;
				}
				LocalPoint at = new LocalPoint((int) body.x, (int) body.y, shoal.worldView);
				Point label = Perspective.localToCanvas(client, at, shoal.plane, 150);
				if (label != null)
				{
					String text = (body.narrow >= BODY_LINE_UP ? "Lining up " : "Spinning ") + Math.round(body.narrow * 100) + "%";
					graphics.setColor(Color.WHITE);
					graphics.drawString(text, label.getX() - graphics.getFontMetrics().stringWidth(text) / 2, label.getY());
				}
			}
			// Steering arrows: white swimming, green circling, yellow growing (in, or into sight), red shrinking (away,
			// or out of sight), grey not drawn.
			if (drawFish)
			{
				for (Swimmer swimmer : shoal.fish)
				{
					markArrow(shoal, swimmer.x, swimmer.y, swimmer.targetX, swimmer.targetY, 16);
				}
				project(view);
				for (int n = 0; n < shoal.fish.size(); n++)
				{
					Swimmer swimmer = shoal.fish.get(n);
					graphics.setColor(!swimmer.shown ? Color.GRAY
						: swimmer.shrinkingSince >= 0 || !inSight(shoal, swimmer) ? Color.RED
						: swimmer.growingSince >= 0 || swimmer.inSight < 1 ? Color.YELLOW
						: swimmer.circle != null ? Color.GREEN : Color.WHITE);
					drawArrow(graphics, n * 4);
				}
				marked = 0;
			}
			// Schools, blue: an outline round each, and an arrow from its middle along its fish's average heading.
			if (!drawSchools)
			{
				continue;
			}
			graphics.setColor(new Color(60, 140, 255));
			Set<Group> schools = new HashSet<>();
			for (Swimmer swimmer : shoal.fish)
			{
				Group group = swimmer.group;
				if (group == null || group.members.size() < 2 || !schools.add(group))
				{
					continue;
				}
				double x = 0;
				double y = 0;
				double hx = 0;
				double hy = 0;
				for (Swimmer member : group.members)
				{
					x += member.x;
					y += member.y;
					hx += member.headX;
					hy += member.headY;
				}
				x /= group.members.size();
				y /= group.members.size();
				double length = Math.hypot(hx, hy);
				if (length < 0.01)
				{
					continue;
				}
				outline(graphics, view, shoal, group);
				markArrow(shoal, x, y, x + hx / length * SCHOOL_ARROW, y + hy / length * SCHOOL_ARROW, 32);
				project(view);
				marked = 0;
				drawArrow(graphics, 0);
				if (screenX[0] != Integer.MIN_VALUE)
				{
					graphics.fillOval(screenX[0] - 3, screenY[0] - 3, 7, 7);
				}
			}
		}
	}

	/**
	 * The points of the route being picked so far, drawn while picking.
	 */
	void setPicking(List<WorldPoint> points)
	{
		picking = points;
	}

	/**
	 * Each river and lake's name, its fish against the most it keeps, and its fish, for the debug panel.
	 */
	static String[][] counts(RiverSpotFish rivers)
	{
		String[][] rows = new String[rivers.shoals.size()][];
		for (int k = 0; k < rows.length; k++)
		{
			Shoal shoal = rivers.shoals.get(k);
			int most = shoal.lake ? shoal.travelling : windowFish(shoal);
			rows[k] = new String[]{rivers.label(shoal.route), shoal.fish.size() + " / " + most,
				String.valueOf(shoal.fish.size())};
		}
		return rows;
	}

	/**
	 * The memory the river and lake maps hold, MB.
	 */
	static double mapsMb(RiverSpotFish rivers)
	{
		long bytes = 0;
		for (Shoal shoal : rivers.shoals)
		{
			// Bank distances (2 bytes a cell), and surfacer and diver cells (1 each) if any.
			int cells = shoal.river.width * shoal.river.height;
			bytes += 2L * cells + (shoal.river.surface != null ? cells : 0) + (shoal.river.dive != null ? cells : 0);
		}
		return bytes / 1e6;
	}
}
