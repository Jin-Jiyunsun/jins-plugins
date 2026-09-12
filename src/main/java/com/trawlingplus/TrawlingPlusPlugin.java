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
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldEntityDespawned;
import net.runelite.api.events.WorldEntitySpawned;
import net.runelite.api.gameval.ObjectID;
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

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TrawlingPlusOverlay overlay;

	@Inject
	private Gson gson;

	@Inject
	private TrawlingPlusConfig config;

	private RouteData routeData;
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
		clientThread.invoke(this::findExistingShoals);
		log.debug("Trawling Plus started with {} routes", routes.size());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
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
			int index = routes.indexOf(shoal.getRoute());
			if (index >= 0)
			{
				shoal.reshapeRoute(reshaped.get(index));
			}
		}
		routes = reshaped;
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
			shoal.setDepth(depthOf(entry.getValue(), RESTING_DEPTH_BY_CLICKBOX.getOrDefault(clickbox, ShoalDepth.UNKNOWN)));

			// Match once, and again if a mixed shoal turns back into a species that doesn't fit its route.
			String species = SPECIES_BY_CLICKBOX.get(clickbox);
			ShoalRoute route = shoal.getRoute();
			if (route == null || (species != null && !species.equals(route.getSpecies())))
			{
				route = nearestRoute(position[0], position[1], species);
				shoal.setRoute(route);
			}
		}
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
	 * How deep a shoal is swimming, from the animation its ripples and fish play, falling back to the
	 * depth its species rests at until they are close enough to be animating.
	 */
	private static ShoalDepth depthOf(WorldEntity entity, ShoalDepth resting)
	{
		WorldView view = entity.getWorldView();
		if (view == null)
		{
			return resting;
		}

		for (NPC npc : view.npcs())
		{
			ShoalDepth depth = ShoalDepth.fromAnimation(npc.getAnimation());
			if (depth != null)
			{
				return depth;
			}
		}
		return resting;
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
	}

	@Provides
	TrawlingPlusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TrawlingPlusConfig.class);
	}
}
