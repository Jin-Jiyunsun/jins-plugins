package com.livelyfishingspots;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Shows the fish each fishing spot gives swimming around it. Only something to look at: the fish have no clickbox
 * and no menu option, and the spots themselves are left as the game draws them.
 */
@Slf4j
@PluginDescriptor(
	name = "Lively Fishing Spots",
	description = "Shows the fish each fishing spot gives swimming around it",
	tags = {"fishing", "fish", "spot", "sailing", "sea", "shoal", "swimming", "visual"}
)
public class LivelyFishingSpotsPlugin extends Plugin
{
	// The name of the 117 HD plugin, the one renderer that draws the water see-through.
	private static final String HD_PLUGIN = "117 HD";
	// TEMPORARY, while tuning: where in Look's values each look spinner's value goes.
	private static final int[] LOOK_PLACES = {4, 5, 0, 1, 2, 7, 15, 16, 17, 23, 24, 22, 6, 12, 13, 14};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	// The fish's models, and the fish swimming at the fishing spots, which need the client to be made.
	private FishModels fishModels;
	private SeaSpotFish seaSpotFish;
	private RiverSpotFish riverSpotFish;
	// The player's Fishing experience as last seen, or -1 before it has been, so each catch's drop can be told.
	private int fishingXp = -1;
	// TEMPORARY, while picking routes: the start picked, waiting for its end, or null.
	private WorldPoint pickedStart;

	@Inject
	private LivelyFishingSpotsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RiverDebugOverlay riverDebugOverlay;

	@Provides
	LivelyFishingSpotsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LivelyFishingSpotsConfig.class);
	}

	@Override
	protected void startUp()
	{
		fishModels = new FishModels(client);
		seaSpotFish = new SeaSpotFish(client, fishModels);
		riverSpotFish = new RiverSpotFish(client, fishModels);
		RiverSpotFish.tune(config);
		tuneLooks();
		riverDebugOverlay.setRivers(riverSpotFish);
		overlayManager.add(riverDebugOverlay);
		seaSpotFish.setSeeThrough(seeThroughWater());
		clientThread.invoke(this::addSpotFish);
		log.debug("Lively Fishing Spots started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(riverDebugOverlay);
		SeaSpotFish fish = seaSpotFish;
		RiverSpotFish riverFish = riverSpotFish;
		FishModels models = fishModels;
		clientThread.invoke(() ->
		{
			fish.clear();
			riverFish.clear();
			models.clear();
		});
		log.debug("Lively Fishing Spots stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// A map load drops the fish from the scene and may move where in it the spots are, without the spots
		// themselves being seen to go and come again, so their fish are put back in once it's loaded, as they were.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Another account may log in next, so its experience starts afresh.
			fishingXp = -1;
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			WorldView top = client.getTopLevelWorldView();
			if (top != null)
			{
				seaSpotFish.reload(top.npcs());
			}
			// The rivers are mapped in the scene, which a map load moves, so theirs are started again.
			riverSpotFish.clear();
			addSpotFish();
		}
	}

	/**
	 * Remakes the fish at the spots as 117 HD is switched on or off, it alone drawing the water see-through, so fish
	 * wholly under it are put in only where they can be seen.
	 */
	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (HD_PLUGIN.equals(event.getPlugin().getName()))
		{
			boolean on = event.isLoaded();
			clientThread.invoke(() ->
			{
				seaSpotFish.setSeeThrough(on);
				seaSpotFish.clear();
				riverSpotFish.clear();
				fishModels.clear();
				addSpotFish();
			});
		}
	}

	/**
	 * Whether the water is drawn see-through, which only 117 HD does: the game's own renderer and the GPU plugin draw
	 * it solid.
	 */
	private boolean seeThroughWater()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (HD_PLUGIN.equals(plugin.getName()) && pluginManager.isPluginActive(plugin))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		seaSpotFish.add(event.getNpc());
		riverSpotFish.add(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		seaSpotFish.remove(event.getNpc());
		riverSpotFish.remove(event.getNpc());
		// The models of any kind no longer swimming anywhere in sight are let go, to be made again if it comes back.
		Set<Integer> swimming = new HashSet<>();
		seaSpotFish.addKinds(swimming);
		riverSpotFish.addKinds(swimming);
		fishModels.keepOnly(swimming);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		fishModels.makeQueued();
		seaSpotFish.swim();
		riverSpotFish.swim();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LivelyFishingSpotsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		boolean look = key.startsWith("debugLook");
		if (look || key.startsWith("debugRiver"))
		{
			clientThread.invoke(() ->
			{
				RiverSpotFish.tune(config);
				if (look)
				{
					tuneLooks();
				}
				// Settings that shape a shoal, or its fish's looks, start the river shoals in sight again.
				if (look || LivelyFishingSpotsConfig.REBUILD.contains(key))
				{
					riverSpotFish.clear();
					addSpotFish();
				}
			});
		}
	}

	/**
	 * Tells the river fish of each catch, by the Fishing experience it gave. The first seen, as the player logs in,
	 * only sets where it starts from.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.FISHING)
		{
			return;
		}
		int xp = event.getXp();
		if (fishingXp >= 0 && xp > fishingXp)
		{
			riverSpotFish.caught(xp - fishingXp);
		}
		fishingXp = xp;
	}

	/**
	 * Notes which way of fishing the player chose on a river spot, so its circle draws the fish that way catches. Only
	 * read: the click goes on to the game as it is.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		riverSpotFish.chose(event.getMenuEntry().getNpc(), event.getMenuOption());
	}

	/**
	 * TEMPORARY, while picking routes: adds River start and, once a start is picked, River end to the menu on a tile.
	 * They only note the tile here; nothing is sent to the game.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.debugPick() || event.getType() != MenuAction.WALK.getId())
		{
			return;
		}
		WorldView top = client.getTopLevelWorldView();
		Tile tile = top == null ? null : top.getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}
		WorldPoint at = tile.getWorldLocation();
		if (pickedStart != null)
		{
			client.getMenu().createMenuEntry(-1).setOption("River end").setTarget("").setType(MenuAction.RUNELITE)
				.onClick(entry ->
				{
					riverSpotFish.pickRoute(pickedStart, at);
					pickedStart = null;
					riverSpotFish.clear();
					addSpotFish();
				});
		}
		client.getMenu().createMenuEntry(-1).setOption("River start").setTarget("").setType(MenuAction.RUNELITE)
			.onClick(entry -> pickedStart = at);
	}

	/**
	 * TEMPORARY, while tuning: sets the river fish's looks from the debug spinners.
	 */
	private void tuneLooks()
	{
		int[] values = {config.debugLookRiverSink(), config.debugLookRiverRise(), config.debugLookRiverRoll(), config.debugLookRiverTilt(), config.debugLookRiverSize(), config.debugLookRiverTurn(), config.debugLookRiverLightest(), config.debugLookRiverWag(), config.debugLookRiverTip(), config.debugLookRiverPace(), config.debugLookRiverSurge(), config.debugLookRiverTipPivot(), config.debugLookRiverPivot(), config.debugLookRiverDipEvery(), config.debugLookRiverDipDepth(), config.debugLookRiverDipMillis()};
		fishModels.tuneLook(ItemID.RAW_TROUT, LOOK_PLACES, values);
		fishModels.tuneLook(ItemID.RAW_SALMON, LOOK_PLACES, values);
		fishModels.tuneLook(ItemID.RAW_PIKE, LOOK_PLACES, values);
	}

	/**
	 * Puts fish at the fishing spots in sight that have none.
	 */
	private void addSpotFish()
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return;
		}
		for (NPC npc : top.npcs())
		{
			seaSpotFish.add(npc);
			riverSpotFish.add(npc);
		}
	}
}
