package com.trawlingplus;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldEntityDespawned;
import net.runelite.api.events.WorldEntitySpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Trawling Plus",
	description = "Shows the routes that deep sea trawling shoals swim",
	tags = {"sailing", "trawling", "shoal", "fishing", "route"}
)
public class TrawlingPlusPlugin extends Plugin
{
	private static final Map<Integer, String> SPECIES_BY_CLICKBOX = Map.of(
		ObjectID.SAILING_SHOAL_CLICKBOX_GIANT_KRILL, "Giant krill",
		ObjectID.SAILING_SHOAL_CLICKBOX_HADDOCK, "Haddock",
		ObjectID.SAILING_SHOAL_CLICKBOX_YELLOWFIN, "Yellowfin",
		ObjectID.SAILING_SHOAL_CLICKBOX_HALIBUT, "Halibut",
		ObjectID.SAILING_SHOAL_CLICKBOX_BLUEFIN, "Bluefin",
		ObjectID.SAILING_SHOAL_CLICKBOX_MARLIN, "Marlin"
	);

	// Mixed shoals follow their original shoal's route, but don't say which species that was.
	private static final Set<Integer> MIXED_CLICKBOXES = Set.of(
		ObjectID.SAILING_SHOAL_CLICKBOX_SHIMMERING,
		ObjectID.SAILING_SHOAL_CLICKBOX_GLISTENING,
		ObjectID.SAILING_SHOAL_CLICKBOX_VIBRANT
	);

	// The depth each shoal swims at when nothing says otherwise, used until its ripples or fish are
	// seen animating. Most sit at moderate; krill, haddock and the shimmering ones stay shallow.
	private static final Map<Integer, ShoalDepth> RESTING_DEPTH_BY_CLICKBOX = Map.of(
		ObjectID.SAILING_SHOAL_CLICKBOX_GIANT_KRILL, ShoalDepth.SHALLOW,
		ObjectID.SAILING_SHOAL_CLICKBOX_HADDOCK, ShoalDepth.SHALLOW,
		ObjectID.SAILING_SHOAL_CLICKBOX_SHIMMERING, ShoalDepth.SHALLOW,
		ObjectID.SAILING_SHOAL_CLICKBOX_YELLOWFIN, ShoalDepth.MODERATE,
		ObjectID.SAILING_SHOAL_CLICKBOX_HALIBUT, ShoalDepth.MODERATE,
		ObjectID.SAILING_SHOAL_CLICKBOX_BLUEFIN, ShoalDepth.MODERATE,
		ObjectID.SAILING_SHOAL_CLICKBOX_MARLIN, ShoalDepth.MODERATE,
		ObjectID.SAILING_SHOAL_CLICKBOX_GLISTENING, ShoalDepth.MODERATE,
		ObjectID.SAILING_SHOAL_CLICKBOX_VIBRANT, ShoalDepth.MODERATE
	);

	// A shoal further than this from every candidate route isn't matched to one, in tiles. Recorded
	// routes are accurate to a fraction of a tile, so a shoal this far off one isn't swimming it.
	private static final double MAX_ROUTE_OFFSET = 3;

	// How long another route has to be the nearer one before it takes over, in ticks: about five
	// seconds, long enough to cross the water between two routes without the drawn one changing
	// under you, short enough not to be waiting for it once you have plainly moved on.
	private static final int ROUTE_GRACE_TICKS = 8;

	// The two trawling net slots a boat can have: the hotspot each net is built on, and how deep it is
	// set. A slot with no net in it names no hotspot.
	private static final int[] NET_SLOTS = {
		VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_0_HOTSPOT_ID,
		VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_1_HOTSPOT_ID
	};
	private static final int[] NET_DEPTHS = {
		VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_0_DEPTH,
		VarbitID.SAILING_SIDEPANEL_BOAT_TRAWLING_NET_1_DEPTH
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TrawlingPlusOverlay overlay;

	@Inject
	private TrawlingPlusNetOverlay netOverlay;

	@Inject
	private TrawlingPlusMapOverlay mapOverlay;

	@Inject
	private TrawlingPlusMinimapOverlay minimapOverlay;

	@Inject
	private Gson gson;

	@Inject
	private TrawlingPlusConfig config;

	private RouteData routeData;
	private Shoal nearestShoal;

	// Where the boat of the player is in world tiles, or null while they are not aboard one. Worked out
	// once a tick and handed to everything that wants it, the minimap included: it draws many times
	// over between ticks, and the boat has not moved in between.
	private double[] boatPlace;

	// The route nearest the boat, and the one waiting to take over from it. A route is only swapped
	// once another has been the nearer for a while: sailing the water between two of them would
	// otherwise flick back and forth between the pair with every small movement.
	private ShoalRoute nearestRoute;
	private ShoalRoute contender;
	private int contenderTicks;
	private boolean showGuides;

	// Which step of its route the shoal was last baited at, and whether that bait belongs to the stop
	// it is sitting at now. Null until the first reading, so starting the plugin beside an already
	// baited shoal does not read as a bait having just been laid.
	private Integer baitedStep;
	private boolean baited;
	private boolean wasStopped;
	private boolean netsAtDepth;
	private List<ShoalRoute> routes = Collections.emptyList();

	// All keyed by the id of each world entity's own world view, which is what ties a shoal's
	// entity to its clickbox object.
	private final Map<Integer, WorldEntity> entities = new HashMap<>();
	private final Map<Integer, Integer> clickboxByView = new HashMap<>();
	private final Map<Integer, Shoal> shoals = new HashMap<>();

	@Override
	protected void startUp() throws IOException
	{
		routeData = ShoalRoute.read(gson);
		routes = ShoalRoute.build(routeData, config.routeSmoothing());
		overlayManager.add(overlay);
		overlayManager.add(netOverlay);
		overlayManager.add(mapOverlay);
		overlayManager.add(minimapOverlay);
		clientThread.invoke(this::findExistingShoals);
		log.debug("Trawling Plus started with {} routes, {} points to draw them from", routes.size(),
			routes.stream().mapToInt(ShoalRoute::sampleCount).sum());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(netOverlay);
		overlayManager.remove(mapOverlay);
		overlayManager.remove(minimapOverlay);
		// shutDown runs on the Swing thread; clear on the client thread so it can't race a game tick.
		clientThread.invoke(this::clearShoals);
		log.debug("Trawling Plus stopped");
	}

	Collection<Shoal> getShoals()
	{
		return shoals.values();
	}

	List<ShoalRoute> getRoutes()
	{
		return routes;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clearShoals();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (TrawlingPlusConfig.GROUP.equals(event.getGroup()) && TrawlingPlusConfig.SMOOTHING_KEY.equals(event.getKey()))
		{
			// Config changes arrive on the Swing thread; reshape the routes on the client thread, where
			// shoals are matched to them and they're drawn.
			clientThread.invoke(this::reshapeRoutes);
		}
	}

	private void reshapeRoutes()
	{
		List<ShoalRoute> reshaped = ShoalRoute.build(routeData, config.routeSmoothing());
		for (Shoal shoal : shoals.values())
		{
			ShoalRoute route = counterpart(shoal.getRoute(), reshaped);
			if (route != null)
			{
				shoal.reshapeRoute(route);
			}
		}

		// The route being drawn and the one waiting to take over from it are swapped for their new shapes
		// too. Left as they were, Whole route kept drawing the old shape for as long as that route stayed
		// nearest, since its new shape is never meaningfully nearer than its old one.
		nearestRoute = counterpart(nearestRoute, reshaped);
		contender = counterpart(contender, reshaped);
		routes = reshaped;
	}

	/**
	 * The same route out of a freshly built list, or null if it is not one of the current routes.
	 */
	private ShoalRoute counterpart(ShoalRoute route, List<ShoalRoute> reshaped)
	{
		int index = route == null ? -1 : routes.indexOf(route);
		return index < 0 ? null : reshaped.get(index);
	}

	@Subscribe
	public void onWorldEntitySpawned(WorldEntitySpawned event)
	{
		WorldView view = event.getWorldEntity().getWorldView();
		if (view != null)
		{
			entities.put(view.getId(), event.getWorldEntity());
			// A respawned entity is a new object, so rebuild its shoal around it on the next tick.
			shoals.remove(view.getId());
		}
	}

	@Subscribe
	public void onWorldEntityDespawned(WorldEntityDespawned event)
	{
		WorldView view = event.getWorldEntity().getWorldView();
		if (view != null)
		{
			entities.remove(view.getId());
			clickboxByView.remove(view.getId());
			shoals.remove(view.getId());
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		if (isShoalClickbox(object.getId()) && object.getWorldView() != null)
		{
			clickboxByView.put(object.getWorldView().getId(), object.getId());
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		if (object.getWorldView() != null)
		{
			// Only clear it if it's still the current clickbox; mixed shoals swap objects in place.
			clickboxByView.remove(object.getWorldView().getId(), object.getId());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// None of this means anything off a boat: the routes are out at sea and out of sight from land,
		// and someone stood on land is not fishing. So none of it is worked out off one, and everything
		// drawn on the water, the minimap and the side panel rests on the same answer. The world map is
		// the exception, and needs none of this: it draws every route wherever the player happens to be.
		WorldEntity own = ownBoat();
		if (!aboard(own))
		{
			stopTracking();
			showGuides = false;
			return;
		}

		for (Map.Entry<Integer, WorldEntity> entry : entities.entrySet())
		{
			Integer clickbox = clickboxByView.get(entry.getKey());
			if (clickbox == null)
			{
				continue;
			}

			Shoal shoal = shoals.get(entry.getKey());
			if (shoal == null)
			{
				shoal = new Shoal(entry.getValue());
				shoals.put(entry.getKey(), shoal);
			}

			double[] position = shoal.position(client);
			if (position == null)
			{
				continue;
			}
			shoal.update(position);
			read(entry.getValue(), RESTING_DEPTH_BY_CLICKBOX.getOrDefault(clickbox, ShoalDepth.UNKNOWN), shoal);

			// Match once, and again if a mixed shoal turns back into a species that doesn't fit its route.
			String species = SPECIES_BY_CLICKBOX.get(clickbox);
			ShoalRoute route = shoal.getRoute();
			if (route == null || (species != null && !species.equals(route.getSpecies())))
			{
				route = nearestRoute(position[0], position[1], species);
				shoal.setRoute(route);
			}

			// How long a stop lasts is recorded with the route it belongs to, so it arrives with the
			// match rather than being measured or held in a table of its own.
			if (route != null)
			{
				shoal.seedStopTicks(route.stopTicks());
			}
		}

		// Worked out here rather than in each overlay, which would repeat it every frame.
		boatPlace = worldPlace(own.getLocalLocation());
		nearestShoal = nearest();
		showGuides = guidesWanted();
		followNearestRoute();
		baited = stillBaited();
		netsAtDepth = netsSetToDepth();
	}

	private void findExistingShoals()
	{
		WorldView top = client.getTopLevelWorldView();
		if (client.getGameState() != GameState.LOGGED_IN || top == null)
		{
			return;
		}

		// Shoals already in the scene when the plugin starts won't fire spawn events, so look for
		// them once. Each world entity's own scene is only a few tiles across, so this is cheap.
		for (WorldEntity entity : top.worldEntities())
		{
			WorldView view = entity.getWorldView();
			if (view == null)
			{
				continue;
			}

			entities.put(view.getId(), entity);
			for (Tile[][] plane : view.getScene().getTiles())
			{
				for (Tile[] row : plane)
				{
					for (Tile tile : row)
					{
						if (tile == null)
						{
							continue;
						}

						for (GameObject object : tile.getGameObjects())
						{
							if (object != null && isShoalClickbox(object.getId()))
							{
								clickboxByView.put(view.getId(), object.getId());
							}
						}
					}
				}
			}
		}
	}

	/**
	 * How deep the shoal nearest the boat of the player is swimming, as worked out on the last tick.
	 */
	ShoalDepth getNearestDepth()
	{
		return nearestShoal == null ? ShoalDepth.UNKNOWN : nearestShoal.getDepth();
	}

	/**
	 * The shoal nearest the boat of the player, as worked out on the last tick, or null.
	 */
	Shoal getNearestShoal()
	{
		return nearestShoal;
	}

	/**
	 * Where the boat of the player is, in world tiles, as worked out on the last tick, or null while they
	 * are not aboard one.
	 */
	double[] getBoatPlace()
	{
		return boatPlace;
	}

	/**
	 * The route nearest the boat of the player, as worked out on the last tick, or null. Only one route
	 * is drawn at a time, so which one that is has to be decided somewhere.
	 */
	ShoalRoute getNearestRoute()
	{
		return nearestRoute;
	}

	/**
	 * Keeps track of which route the boat is nearest, swapping only once another has been the nearer
	 * one for a while. Distance is to the route itself rather than to a shoal on it, so a route is
	 * still found while sailing towards a sea with nothing in view yet.
	 */
	private void followNearestRoute()
	{
		WorldView view = client.getTopLevelWorldView();
		double[] afloat = boatPlace;
		if (afloat == null || view == null)
		{
			return;
		}

		// Only routes reaching the map that is loaded, since a route is only ever wanted to be drawn on
		// it. Working out which of the ones an ocean away is nearest would cost more than the answer,
		// and the answer would change nothing: none of them can be drawn.
		int beyond = Math.max(0, Math.min((Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2,
			client.getExpandedMapLoading() * Constants.CHUNK_SIZE));
		double fromX = view.getBaseX() - beyond;
		double fromY = view.getBaseY() - beyond;
		double toX = view.getBaseX() + view.getSizeX() + beyond;
		double toY = view.getBaseY() + view.getSizeY() + beyond;

		// The one already being drawn is looked at first, since the boat is usually still nearest the
		// route it was nearest a tick ago. That gives a distance to beat straight away, and every route
		// whose box is further off than that is then passed over without being looked through at all.
		// How many of them are in range at all. Most of the time it is one, and one route has nothing to
		// be nearer than, so there is nothing to work out: knowing how far away it is would only ever be
		// used to rule others out, and there are none to rule out.
		ShoalRoute only = null;
		int candidates = 0;
		for (ShoalRoute route : routes)
		{
			if (route.overlaps(fromX, fromY, toX, toY))
			{
				candidates++;
				only = route;
			}
		}

		if (candidates <= 1)
		{
			// Nothing in range leaves nothing to draw. One in range is the answer, taken straight away:
			// waiting is for choosing between routes, and there is no choice to make.
			if (only != nearestRoute)
			{
				nearestRoute = only;
				contender = null;
				contenderTicks = 0;
			}
			return;
		}

		ShoalRoute closest = nearestRoute != null && nearestRoute.overlaps(fromX, fromY, toX, toY)
			? nearestRoute
			: null;
		double nearestOffset = closest == null ? Double.MAX_VALUE
			: closest.project(afloat[0], afloat[1]).offset;

		for (ShoalRoute route : routes)
		{
			if (route == closest || !route.overlaps(fromX, fromY, toX, toY)
				|| route.boxDistance(afloat[0], afloat[1]) >= nearestOffset)
			{
				continue;
			}

			double offset = route.project(afloat[0], afloat[1]).offset;
			if (offset < nearestOffset)
			{
				nearestOffset = offset;
				closest = route;
			}
		}

		if (closest == nearestRoute)
		{
			contender = null;
			contenderTicks = 0;
			return;
		}

		if (closest != contender)
		{
			contender = closest;
			contenderTicks = 0;
		}

		// Nothing drawn yet, so there is no reason to make anyone wait for the first one.
		if (++contenderTicks >= ROUTE_GRACE_TICKS || nearestRoute == null)
		{
			nearestRoute = closest;
			contender = null;
			contenderTicks = 0;
		}
	}

	/**
	 * Whether every trawling net on the boat is set to the depth the nearest shoal is swimming at, as
	 * worked out on the last tick.
	 */
	boolean isAtDepth()
	{
		return netsAtDepth;
	}

	/**
	 * Whether every net fitted is already at the depth worth fishing. A boat with no nets, or a shoal
	 * whose depth is not known, has nothing to be right about.
	 */
	private boolean netsSetToDepth()
	{
		int wanted = getNearestDepth().netDepth();
		if (wanted < 0)
		{
			return false;
		}

		boolean fitted = false;
		for (int slot = 0; slot < NET_SLOTS.length; slot++)
		{
			// A slot names the hotspot its net is built on, so an empty slot names no hotspot.
			if (client.getVarbitValue(NET_SLOTS[slot]) <= 0)
			{
				continue;
			}

			fitted = true;
			if (client.getVarbitValue(NET_DEPTHS[slot]) != wanted)
			{
				return false;
			}
		}
		return fitted;
	}

	/**
	 * Whether the shoal nearest the boat has been baited at the stop it is sitting at, as worked out on
	 * the last tick.
	 */
	boolean isBaited()
	{
		return baited;
	}

	/**
	 * Works out whether the bait on the nearest shoal is still the one for the stop it is at. The game
	 * records which step of its route a shoal was baited at rather than how long the bait has left, and
	 * the number means nothing to us, so the change is what is watched: a new number while the shoal
	 * sits still is a fresh bait, and a shoal swimming off leaves its bait behind with the stop.
	 */
	private boolean stillBaited()
	{
		int step = client.getVarbitValue(VarbitID.SAILING_PLAYER_TRAWLING_SHOAL_BAITED_STEP);
		boolean laid = baitedStep != null && baitedStep != step;
		baitedStep = step;

		if (nearestShoal == null)
		{
			wasStopped = false;
			return false;
		}

		boolean stopped = nearestShoal.stopped();
		boolean held = baited;
		if (laid)
		{
			// Taken wherever the shoal is rather than only once it counts as stopped. Bait is laid as
			// the shoal arrives, and a stop is not recognised until it has held still for a tick, so
			// insisting on both at once threw the bait away on the tick it was laid.
			held = true;
		}
		else if (wasStopped && !stopped)
		{
			// It has set off again, leaving the bait behind with the stop.
			held = false;
		}

		wasStopped = stopped;
		return held;
	}

	/**
	 * Whether anything should be drawn at all, as worked out on the last tick, which is as much of the
	 * Show guides setting as the overlays need to know.
	 */
	boolean showGuides()
	{
		return showGuides;
	}

	/**
	 * Whether the setting is satisfied, on a boat that is already known to be underfoot: either that is
	 * enough on its own, or the boat has to be fitted with a trawling net as well.
	 */
	private boolean guidesWanted()
	{
		// Only ever asked while aboard, so what is left to settle is whether a net has to be fitted too.
		if (config.showGuides() == TrawlingPlusConfig.ShowGuides.ALWAYS)
		{
			// Being on the boat at all, which by the time this is asked is already known.
			return true;
		}

		// Each slot names the hotspot its net is built on, so an empty slot names no hotspot.
		return client.getVarbitValue(NET_SLOTS[0]) > 0 || client.getVarbitValue(NET_SLOTS[1]) > 0;
	}

	/**
	 * Whether the player is stood on their own boat. Everyone aboard stands in the boat's own world
	 * view rather than the one the sea is in.
	 */
	private boolean aboard(WorldEntity boat)
	{
		WorldView deck = boat == null ? null : boat.getWorldView();
		Player player = client.getLocalPlayer();
		WorldView standing = player == null ? null : player.getWorldView();
		return deck != null && standing != null && deck.getId() == standing.getId();
	}

	/**
	 * The boat of the player, or null while they are not aboard one.
	 */
	WorldEntity ownBoat()
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return null;
		}

		for (WorldEntity boat : top.worldEntities())
		{
			if (boat.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER)
			{
				return boat;
			}
		}
		return null;
	}

	/**
	 * The shoal nearest the boat of the player, or null if there is no boat or no shoal to measure to.
	 */
	private Shoal nearest()
	{
		double[] afloat = boatPlace;
		if (afloat == null)
		{
			return null;
		}

		Shoal nearest = null;
		double nearestGap = Double.MAX_VALUE;
		for (Shoal shoal : shoals.values())
		{
			double[] at = shoal.position(client);
			if (at == null)
			{
				continue;
			}

			double gap = Math.hypot(at[0] - afloat[0], at[1] - afloat[1]);
			if (gap < nearestGap)
			{
				nearestGap = gap;
				nearest = shoal;
			}
		}
		return nearest;
	}

	/**
	 * A local point in world tile coordinates, including the fraction of a tile, or null.
	 */
	private double[] worldPlace(LocalPoint local)
	{
		WorldView view = local == null ? null : client.getWorldView(local.getWorldView());
		if (view == null)
		{
			return null;
		}

		return new double[]{
			view.getBaseX() + (double) (local.getX() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE,
			view.getBaseY() + (double) (local.getY() - Perspective.LOCAL_HALF_TILE_SIZE) / Perspective.LOCAL_TILE_SIZE
		};
	}

	/**
	 * How long the shoal nearest the boat has left at its stop, in seconds, or -1 if there is no such
	 * shoal or it is on the move.
	 */
	double getSecondsAtStop()
	{
		return nearestShoal == null ? -1 : nearestShoal.secondsAtStop();
	}

	/**
	 * Reads what the fish inside a shoal say about it, in one walk of them: how deep it is swimming,
	 * from the animation they play, falling back to the depth its species rests at until they are
	 * close enough to be animating; and how much is left of the bar drawn over it while it sits at a
	 * stop, which the game draws the way it draws a health bar. Both come off the same handful of
	 * NPCs, so they are taken together rather than walking them again for each.
	 */
	private static void read(WorldEntity entity, ShoalDepth resting, Shoal shoal)
	{
		WorldView view = entity.getWorldView();
		ShoalDepth depth = resting;
		boolean swimming = false;
		int bar = -1;
		int scale = -1;

		for (NPC npc : view == null ? Collections.<NPC>emptyList() : view.npcs())
		{
			if (!swimming)
			{
				ShoalDepth animating = ShoalDepth.fromAnimation(npc.getAnimation());
				if (animating != null)
				{
					depth = animating;
					swimming = true;
				}
			}

			if (scale < 0 && npc.getHealthScale() > 0)
			{
				bar = npc.getHealthRatio();
				scale = npc.getHealthScale();
			}

			if (swimming && scale >= 0)
			{
				// Both found, so the rest of the shoal has nothing left to say.
				break;
			}
		}

		shoal.setDepth(depth);
		shoal.setStopBar(bar, scale);
	}

	private ShoalRoute nearestRoute(double x, double y, String species)
	{
		ShoalRoute nearest = null;
		double nearestOffset = MAX_ROUTE_OFFSET;
		for (ShoalRoute route : routes)
		{
			if (species != null && !species.equals(route.getSpecies()))
			{
				continue;
			}

			double offset = route.project(x, y).offset;
			if (offset <= nearestOffset)
			{
				nearest = route;
				nearestOffset = offset;
			}
		}
		return nearest;
	}

	private static boolean isShoalClickbox(int id)
	{
		return SPECIES_BY_CLICKBOX.containsKey(id) || MIXED_CLICKBOXES.contains(id);
	}

	private void clearShoals()
	{
		entities.clear();
		clickboxByView.clear();
		shoals.clear();
		showGuides = false;
		stopTracking();
	}

	/**
	 * Forgets everything read off the boat and the shoals around it, leaving the shoals themselves
	 * alone: they come and go with their own events, and the world map still marks the ones in view.
	 */
	private void stopTracking()
	{
		boatPlace = null;
		nearestShoal = null;
		nearestRoute = null;
		contender = null;
		contenderTicks = 0;
		baitedStep = null;
		baited = false;
		wasStopped = false;
		netsAtDepth = false;
	}

	@Provides
	TrawlingPlusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TrawlingPlusConfig.class);
	}
}
