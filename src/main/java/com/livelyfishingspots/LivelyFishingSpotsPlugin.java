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
 * Shows fish swimming at fishing spots. Visual only: no clickboxes or menu options, and the spots are untouched.
 */
@Slf4j
@PluginDescriptor(
	name = "Lively Fishing Spots",
	description = "Shows the fish each fishing spot gives swimming around it",
	tags = {"fishing", "fish", "spot", "sailing", "sea", "shoal", "swimming", "visual"}
)
public class LivelyFishingSpotsPlugin extends Plugin
{
	// 117 HD, the only renderer with see-through water.
	private static final String HD_PLUGIN = "117 HD";
	// TEMPORARY: the Look value each look spinner sets.
	private static final int[] LOOK_PLACES = {4, 5, 0, 1, 2, 7, 15, 16, 17, 23, 24, 22, 6, 12, 13, 14};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	// Created in startUp, as they need the client.
	private FishModels fishModels;
	private SeaSpotFish seaSpotFish;
	private RiverSpotFish riverSpotFish;
	// Last Fishing XP seen, or -1, to tell each catch's drop.
	private int fishingXp = -1;
	// TEMPORARY: the picked route start awaiting its end, or null.
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
		riverSpotFish.setSchooled(config.riverSwimming() == RiverSwimming.SCHOOLED);
		RiverSpotFish.tune(config);
		tuneLooks();
		riverDebugOverlay.setRivers(riverSpotFish);
		overlayManager.add(riverDebugOverlay);
		seaSpotFish.setSeeThrough(seeThroughWater());
		riverSpotFish.setSeeThrough(seeThroughWater());
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
		// A map load drops the fish and can move the spots in the scene without despawning them.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Another account may log in next.
			fishingXp = -1;
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			WorldView top = client.getTopLevelWorldView();
			if (top != null)
			{
				seaSpotFish.reload(top.npcs());
			}
			// River grids are in scene coordinates, which a map load moves.
			riverSpotFish.clear();
			addSpotFish();
		}
	}

	/**
	 * Remakes the fish when 117 HD is toggled, as it decides whether under-water fish can be seen.
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
				riverSpotFish.setSeeThrough(on);
				seaSpotFish.clear();
				riverSpotFish.clear();
				fishModels.clear();
				addSpotFish();
			});
		}
	}

	/**
	 * Whether the water is see-through, which only 117 HD draws.
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
		// Only a fishing spot going can leave a kind out of sight; then free its models.
		boolean sea = seaSpotFish.remove(event.getNpc());
		if (!riverSpotFish.remove(event.getNpc()) && !sea)
		{
			return;
		}
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
		if ("riverSwimming".equals(key))
		{
			clientThread.invoke(() ->
			{
				riverSpotFish.setSchooled(config.riverSwimming() == RiverSwimming.SCHOOLED);
				riverSpotFish.clear();
				addSpotFish();
			});
			return;
		}
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
				// Shape and look changes rebuild the river shoals.
				if (look || LivelyFishingSpotsConfig.REBUILD.contains(key))
				{
					riverSpotFish.clear();
					addSpotFish();
				}
			});
		}
	}

	/**
	 * Passes each Fishing XP drop to the river fish; the first one after login only sets the baseline.
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
	 * Notes the option clicked on a river spot. Read only; the click is untouched.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		riverSpotFish.chose(event.getMenuEntry().getNpc(), event.getMenuOption());
	}

	/**
	 * TEMPORARY: adds River start and River end to the tile menu. Local only; nothing is sent to the game.
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
	 * TEMPORARY: sets the river fish's look from the spinners.
	 */
	private void tuneLooks()
	{
		int[] values = {config.debugLookRiverSink(), config.debugLookRiverRise(), config.debugLookRiverRoll(), config.debugLookRiverTilt(), config.debugLookRiverSize(), config.debugLookRiverTurn(), config.debugLookRiverLightest(), config.debugLookRiverWag(), config.debugLookRiverTip(), config.debugLookRiverPace(), config.debugLookRiverSurge(), config.debugLookRiverTipPivot(), config.debugLookRiverPivot(), config.debugLookRiverDipEvery(), config.debugLookRiverDipDepth(), config.debugLookRiverDipMillis()};
		fishModels.tuneLook(ItemID.RAW_TROUT, LOOK_PLACES, values);
		fishModels.tuneLook(ItemID.RAW_SALMON, LOOK_PLACES, values);
		fishModels.tuneLook(ItemID.RAW_PIKE, LOOK_PLACES, values);
	}

	/**
	 * Adds fish at the spots in sight that have none.
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
