package com.trawlingplus;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * A shoal in the scene and the route it has been matched to, if any.
 */
final class Shoal
{
	// How long the heading arrow takes to fade fully in or out, in milliseconds: about a game tick.
	private static final double FADE_MILLIS = 600;

	// Once the route to a new next stop has drawn itself out, how long the stop takes to fade in, in
	// milliseconds. How long the route takes to draw out is set in the config.
	private static final double STOP_REVEAL_MILLIS = 300;

	// How long a new section waits before drawing out, in milliseconds, so the shoal can finish gliding into its stop
	// first.
	private static final long REVEAL_DELAY_MILLIS = 500;

	private final WorldEntity entity;
	private ShoalRoute route;
	private int clickbox;
	// Where the shoal was last found along its route, so it can be followed past a crossing.
	private double routeDistance = -1;

	private double[] lastPosition;
	private double[] previousPosition;
	private double[] lastTarget;
	// The shoal's stop bar as read on the world map, and the tick it was read on, as it only changes once a tick.
	private double barNow = -1;
	private int barNowTick = -1;
	// Ticks in a row the shoal's position hasn't changed, whether it has been seen moving, and whether on the last
	// tick it set off from a stop or first sat still at one.
	private int stillRun;
	// How many ticks still count as sitting at a stop, for setting off from one: every stop lasts far longer.
	private static final int SETTLED_TICKS = 10;
	private boolean sawMove;
	private boolean left;
	private boolean arrived;
	private ShoalDepth depth = ShoalDepth.UNKNOWN;
	// The last depth read off the shoal's animation rather than guessed, and whether it changed on the last read.
	private ShoalDepth animatedDepth;
	private boolean depthChanged;

	// The bar the game draws over a shoal at a stop, emptying as the stop runs out, and how long a stop on this route
	// lasts, without which the bar can't be turned into a time.
	private int barLeft = -1;
	private int barScale;
	private int barStopTicks;
	private long stopEndsMillis = -1;
	private boolean sawNoBar;

	// A bar first seen this far below full didn't just start: a shoal's entity loads a tick or two before the fish
	// inside it, so a bar can seem to appear mid stop.
	private static final int FRESH_SLACK = 6;
	// Hidden until the shoal is seen swimming, and whenever it sits still.
	private boolean headingArrowHidden = true;
	private double headingArrowOpacity;
	private long lastFadeMillis = -1;
	// How much of the heading arrow shows near the boat: held where it was for HEADING_CLEAR_HOLD_MILLIS once the
	// boat starts to cover it, then faded down at the arrow's usual pace. Started afresh after a gap in drawing it.
	private static final long HEADING_CLEAR_HOLD_MILLIS = 1500;
	private static final long HEADING_CLEAR_GAP_MILLIS = 250;
	private double headingClearShown = 1;
	private long headingClearDropMillis = -1;
	private long lastClearMillis = -1;
	// When the shoal last moved off from a stop it was seen sitting at, for the same length of time after which the
	// boat doesn't hide the arrow at all, so it is seen setting off.
	private boolean seenStill;
	private long setOffMillis = -1;

	// The next stop the route is being drawn out to, and when that started.
	private int revealStop = -1;
	// The stop it was heading for before that, which it has just reached: kept on screen while the
	// shoal settles in there, then forgotten.
	private int arrivedStop = -1;
	private long revealStartMillis;

	Shoal(WorldEntity entity)
	{
		this.entity = entity;
	}

	ShoalRoute getRoute()
	{
		return route;
	}

	/**
	 * The clickbox object the shoal was found by, which says what species it is.
	 */
	int getClickbox()
	{
		return clickbox;
	}

	void setClickbox(int clickbox)
	{
		this.clickbox = clickbox;
	}

	void setRoute(ShoalRoute route)
	{
		if (route != this.route)
		{
			// Stop numbers belong to a route, so draw the new one out from the start.
			revealStop = -1;
			arrivedStop = -1;
			routeDistance = -1;
		}
		this.route = route;
	}

	/**
	 * Swaps in the same route with its line smoothed differently. Its stops are the same, so a next
	 * section that's drawing itself out carries on.
	 */
	void reshapeRoute(ShoalRoute route)
	{
		this.route = route;
	}

	/**
	 * Follows the shoal along its route, giving how far round it now is; keeping where it was stops it jumping across
	 * where a route crosses itself.
	 */
	double followRoute(double[] position)
	{
		routeDistance = route.project(position[0], position[1], routeDistance).distance;
		return routeDistance;
	}

	ShoalDepth getDepth()
	{
		return depth;
	}

	/**
	 * Takes the depth as read this tick, and whether it came off the shoal's animation. Only one animated depth
	 * giving way to another counts as a change, not the guessed resting depth giving way to the first one seen.
	 */
	void setDepth(ShoalDepth depth, boolean animated)
	{
		this.depth = depth;
		depthChanged = animated && animatedDepth != null && animatedDepth != depth;
		if (animated)
		{
			animatedDepth = depth;
		}
	}

	/**
	 * Whether the shoal's animation moved it to another depth on the last read.
	 */
	boolean depthChanged()
	{
		return depthChanged;
	}

	/**
	 * Takes the stop bar as it reads this tick. A bar that has gone means the shoal is on the move, and
	 * one that has gone up means it has settled at the next stop and the bar has been refilled.
	 */
	void setStopBar(int left, int scale)
	{
		if (left < 0)
		{
			// No bar, so the shoal is on the move and there is no stop to count down.
			stopEndsMillis = -1;
			sawNoBar = true;
		}
		else if (barLeft < 0 || left > barLeft)
		{
			// A stop has begun, so its clock is set running, but only if the shoal was watched moving before it and its
			// bar turned up full; a bar already part way down belongs to a stop that began unseen, which isn't timed.
			boolean fromTheStart = sawNoBar && scale > 0 && left >= scale - FRESH_SLACK;
			stopEndsMillis = fromTheStart
				? System.currentTimeMillis() + (long) barStopTicks * Constants.GAME_TICK_LENGTH
				: -1;
			sawNoBar = false;
		}

		barLeft = left;
		barScale = scale;
	}

	/**
	 * Takes how long a stop on this shoal's route lasts, in ticks, which is how long the clock is set for.
	 */
	void seedStopTicks(int ticks)
	{
		barStopTicks = ticks;
	}

	/**
	 * How full the game's bar over the shoal is at its stop, from 0 to 1 as the game has it, or -1 while it is on
	 * the move.
	 */
	double stopBarFraction()
	{
		return barLeft < 0 || barScale <= 0 ? -1 : Math.min(1, (double) barLeft / barScale);
	}

	/**
	 * How long the shoal has left at this stop, in seconds, or -1 unless the stop was watched from its first tick: a
	 * guess from how far down the bar is would be worse than nothing.
	 */
	double secondsAtStop()
	{
		if (barLeft < 0 || stopEndsMillis < 0)
		{
			return -1;
		}

		return Math.max(0, (stopEndsMillis - System.currentTimeMillis()) / 1000.0);
	}

	/**
	 * Whether the shoal is sitting at a stop, as of the last tick.
	 */
	boolean stopped()
	{
		return headingArrowHidden;
	}

	/**
	 * Whether the shoal set off from a stop on the last tick: the first tick it moved after sitting still for a while.
	 */
	boolean justLeft()
	{
		return left;
	}

	/**
	 * Whether the shoal was seen coming to a stop on the last tick: the first tick it sat still after moving.
	 */
	boolean justArrived()
	{
		return arrived;
	}

	/**
	 * Whether the shoal's position didn't change on the last tick.
	 */
	boolean sittingStill()
	{
		return stillRun > 0;
	}

	/**
	 * Where the shoal was as of the last tick, or null.
	 */
	double[] lastPosition()
	{
		return lastPosition;
	}

	/**
	 * Where the shoal was the tick before the last, or null.
	 */
	double[] previousPosition()
	{
		return previousPosition;
	}

	/**
	 * Whether the shoal has actually been seen sitting still, as of the last tick. Unlike stopped(), a shoal that
	 * has only just come into view, with nothing yet to judge it by, doesn't count.
	 */
	boolean seenStopped()
	{
		return seenStill;
	}

	/**
	 * Tracks once a tick whether the shoal is swimming or sitting still, for its heading arrow: by where it's heading
	 * when that can be read, which changes a tick before the drawn position does, else by its position.
	 */
	void update(double[] position, double[] target)
	{
		// Leaving and arriving by the drawn position itself, a tick after the destination shows it, as the route
		// timing was recorded.
		left = false;
		arrived = false;
		if (lastPosition != null)
		{
			boolean same = position[0] == lastPosition[0] && position[1] == lastPosition[1];
			// Only after a real stop: a tick's pause on the way, near a stop, would otherwise look like leaving it.
			left = !same && stillRun >= SETTLED_TICKS;
			arrived = same && stillRun == 0 && sawMove;
			stillRun = same ? stillRun + 1 : 0;
			sawMove |= !same;
		}
		previousPosition = lastPosition;

		boolean judged = false;
		if (target != null)
		{
			if (lastTarget != null)
			{
				headingArrowHidden = target[0] == lastTarget[0] && target[1] == lastTarget[1];
				judged = true;
			}
		}
		else if (lastPosition != null)
		{
			headingArrowHidden = position[0] == lastPosition[0] && position[1] == lastPosition[1];
			judged = true;
		}
		if (judged)
		{
			if (seenStill && !headingArrowHidden)
			{
				setOffMillis = System.currentTimeMillis();
			}
			seenStill = headingArrowHidden;
		}
		lastPosition = position;
		lastTarget = target;
	}

	/**
	 * How opaque the heading arrow is right now, from 0 to 1. It moves towards shown or hidden a
	 * little on each call, so the arrow fades in and out instead of popping. Call once per frame.
	 */
	double headingArrowOpacity(long nowMillis)
	{
		if (lastFadeMillis >= 0)
		{
			double step = Math.max(0, nowMillis - lastFadeMillis) / FADE_MILLIS;
			headingArrowOpacity = headingArrowHidden
				? Math.max(0, headingArrowOpacity - step)
				: Math.min(1, headingArrowOpacity + step);
		}
		lastFadeMillis = nowMillis;

		// Smoothstep, so the fade eases in and out rather than changing at a constant rate.
		return headingArrowOpacity * headingArrowOpacity * (3 - 2 * headingArrowOpacity);
	}

	/**
	 * How much of the heading arrow to show near the boat: as the boat's clear area would, but held 1.5 s before fading
	 * as the boat covers it, and not hidden for 1.5 s after the shoal leaves a stop. Call once per drawn frame.
	 */
	double heldClearShown(double shown, long nowMillis)
	{
		long since = lastClearMillis < 0 ? Long.MAX_VALUE : nowMillis - lastClearMillis;
		lastClearMillis = nowMillis;
		if (setOffMillis >= 0 && nowMillis - setOffMillis < HEADING_CLEAR_HOLD_MILLIS)
		{
			// Just set off from a stop, so shown whatever the boat is over.
			headingClearShown = 1;
			headingClearDropMillis = -1;
			return 1;
		}
		if (since > HEADING_CLEAR_GAP_MILLIS || shown >= headingClearShown)
		{
			headingClearShown = shown;
			headingClearDropMillis = -1;
		}
		else if (headingClearDropMillis < 0)
		{
			headingClearDropMillis = nowMillis;
		}
		else if (nowMillis - headingClearDropMillis >= HEADING_CLEAR_HOLD_MILLIS)
		{
			headingClearShown = Math.max(shown, headingClearShown - Math.max(0, since) / FADE_MILLIS);
		}
		return headingClearShown;
	}

	/**
	 * Notes the stop the shoal is heading for, once per frame. When it changes, the route to the new
	 * stop starts drawing itself out.
	 */
	void headFor(int nextStop, long nowMillis)
	{
		if (nextStop != revealStop)
		{
			arrivedStop = revealStop;
			revealStop = nextStop;
			revealStartMillis = nowMillis + REVEAL_DELAY_MILLIS;
		}
	}

	/**
	 * The stop the shoal has just reached, while it is still being shown, or -1.
	 */
	int arrivedStop()
	{
		return arrivedStop;
	}

	void clearArrivedStop()
	{
		arrivedStop = -1;
	}

	/**
	 * Whether the section to the next stop has started drawing out, rather than still waiting for the
	 * shoal to settle in.
	 */
	boolean revealStarted(long nowMillis)
	{
		return nowMillis >= revealStartMillis;
	}

	/**
	 * How much of the route to the next stop is drawn so far, from 0 to 1, when it takes the given
	 * time to draw out.
	 */
	double routeReveal(long nowMillis, long revealMillis)
	{
		double t = Math.min(1, Math.max(0, nowMillis - revealStartMillis) / (double) revealMillis);
		// Sets off gently and settles into the stop, but without creeping in, which read as dragging on:
		// it arrives at a third of its average speed rather than coming to a crawl.
		return t * t * (8 - 5 * t) / 3;
	}

	/**
	 * How opaque the next stop is, from 0 to 1. It fades in once the route has been drawn out to it.
	 */
	double nextStopReveal(long nowMillis, long revealMillis)
	{
		double t = Math.min(1, Math.max(0, nowMillis - revealStartMillis - revealMillis) / STOP_REVEAL_MILLIS);
		return t * t * (3 - 2 * t);
	}

	/**
	 * The world view the shoal swims around in, which is normally the top-level one.
	 */
	WorldView parentView(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		return local == null ? null : client.getWorldView(local.getWorldView());
	}

	/**
	 * Whether the shoal is being drawn in the world right now. Only a shoal the client is rendering may be marked on a
	 * map.
	 */
	boolean rendered(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		WorldView view = local == null ? null : client.getWorldView(local.getWorldView());
		if (view == null)
		{
			return false;
		}

		// Inside the scene the client is drawing, rather than the wider area it has loaded around it.
		return local.getX() >= 0 && local.getY() >= 0
			&& local.getX() < view.getSizeX() * Perspective.LOCAL_TILE_SIZE
			&& local.getY() < view.getSizeY() * Perspective.LOCAL_TILE_SIZE;
	}

	/**
	 * How full the game's bar over the shoal is on this tick, from 0 to 1, or -1 while there's none: it only shows
	 * while the shoal is at a stop. Read straight off the shoal once a tick, so it holds wherever the player is.
	 */
	double stopBarNow(int tick)
	{
		if (tick == barNowTick)
		{
			return barNow;
		}
		barNowTick = tick;
		barNow = -1;
		WorldView view = entity.getWorldView();
		if (view != null)
		{
			for (NPC npc : view.npcs())
			{
				if (npc.getHealthScale() > 0 && npc.getHealthRatio() >= 0)
				{
					barNow = Math.min(1, (double) npc.getHealthRatio() / npc.getHealthScale());
					break;
				}
			}
		}
		return barNow;
	}

	/**
	 * Where the shoal is swimming to in world tiles: the spot the server last sent, which the client glides
	 * it towards until the next tick. Null when it cannot be read.
	 */
	double[] target(Client client)
	{
		LocalPoint local = entity.getTargetLocation();
		WorldView view = local == null ? null : client.getWorldView(local.getWorldView());
		return view == null ? null : toWorld(view, local);
	}

	/**
	 * The shoal's position in world tile coordinates, including the fraction of a tile, or null.
	 */
	double[] position(Client client)
	{
		LocalPoint local = entity.getLocalLocation();
		WorldView view = local == null ? null : client.getWorldView(local.getWorldView());
		return view == null ? null : toWorld(view, local);
	}

	private static double[] toWorld(WorldView view, LocalPoint local)
	{
		return new double[]{
			view.getBaseX() + (double) (local.getX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE,
			view.getBaseY() + (double) (local.getY() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE
		};
	}
}
