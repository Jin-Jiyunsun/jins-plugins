package com.livelyfishingspots;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * Shows fish swimming at fishing spots. Visual only: no clickboxes or menu options, and the spots are untouched.
 */
@Slf4j
@PluginDescriptor(
	name = "Lively Fishing Spots",
	description = "Shows the fish each fishing spot gives swimming around it",
	tags = {"fishing", "fish", "spot", "sailing", "sea", "river", "lake", "shoal", "swimming", "visual"}
)
public class LivelyFishingSpotsPlugin extends Plugin
{
	// 117 HD, the only renderer with see-through water.
	private static final String HD_PLUGIN = "117 HD";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private LivelyFishingSpotsConfig config;

	// Created in startUp, as they need the client.
	private FishModels fishModels;
	private SeaSpotFish seaSpotFish;
	private RiverSpotFish riverSpotFish;
	// Last Fishing XP seen, or -1, to tell each catch's drop.
	private int fishingXp = -1;

	@Provides
	LivelyFishingSpotsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LivelyFishingSpotsConfig.class);
	}

	/**
	 * Hands the river fish their player settings.
	 */
	private void riverSettings()
	{
		riverSpotFish.setSwimming(config.riverSwimming());
		riverSpotFish.setAmount(config.riverFishAmount());
		riverSpotFish.setLakeAmount(config.lakeFishAmount());
		riverSpotFish.setDeep(config.riverDeep());
	}

	@Override
	protected void startUp()
	{
		fishModels = new FishModels(client);
		seaSpotFish = new SeaSpotFish(client, fishModels);
		riverSpotFish = new RiverSpotFish(client, fishModels);
		riverSettings();
		riverSpotFish.warmUp();
		seaSpotFish.setSeeThrough(seeThroughWater());
		riverSpotFish.setSeeThrough(seeThroughWater());
		clientThread.invoke(this::addSpotFish);
		log.debug("Lively Fishing Spots started");
	}

	@Override
	protected void shutDown()
	{
		SeaSpotFish fish = seaSpotFish;
		RiverSpotFish riverFish = riverSpotFish;
		FishModels models = fishModels;
		// Nothing to clear if starting up failed partway.
		if (fish == null || riverFish == null || models == null)
		{
			return;
		}
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
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// The spots will be new ones.
			riverSpotFish.clear();
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			WorldView top = client.getTopLevelWorldView();
			if (top != null)
			{
				seaSpotFish.reload(top.npcs());
				// Rivers and lakes are kept, moved to the new scene coordinates.
				riverSpotFish.reload(top);
			}
			else
			{
				riverSpotFish.clear();
			}
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
		long started = RiverSpotFish.probe.start();
		seaSpotFish.add(event.getNpc());
		riverSpotFish.add(event.getNpc());
		RiverSpotFish.probe.add(Probe.SPOTS, started);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		// Only a fishing spot going can leave a kind out of sight; then free its models.
		long started = RiverSpotFish.probe.start();
		boolean sea = seaSpotFish.remove(event.getNpc());
		if (riverSpotFish.remove(event.getNpc()) || sea)
		{
			Set<Integer> swimming = new HashSet<>();
			seaSpotFish.addKinds(swimming);
			riverSpotFish.addKinds(swimming);
			fishModels.keepOnly(swimming);
		}
		RiverSpotFish.probe.add(Probe.SPOTS, started);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		long started = RiverSpotFish.probe.start();
		riverSpotFish.activate();
		RiverSpotFish.probe.add(Probe.STARTING, started);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		Probe probe = RiverSpotFish.probe;
		long started = probe.start();
		fishModels.makeQueued();
		started = probe.add(Probe.MODELS, started);
		seaSpotFish.swim();
		started = probe.add(Probe.SEA, started);
		riverSpotFish.swim();
		probe.add(Probe.RIVERS, started);
		probe.endTick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		String key = event.getKey();
		if (LivelyFishingSpotsConfig.GROUP.equals(event.getGroup()) && ("riverSwimming".equals(key)
			|| "riverFishAmount".equals(key) || "lakeFishAmount".equals(key) || "riverDeep".equals(key)))
		{
			clientThread.invoke(() ->
			{
				riverSettings();
				refresh();
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
	 * Adds fish at the spots in sight that have none.
	 */
	void addSpotFish()
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null || riverSpotFish == null)
		{
			return;
		}
		for (NPC npc : top.npcs())
		{
			seaSpotFish.add(npc);
			riverSpotFish.add(npc);
		}
	}

	/**
	 * Starts the river fish again from the spots in sight. Client thread.
	 */
	void refresh()
	{
		if (riverSpotFish != null)
		{
			riverSpotFish.clear();
			addSpotFish();
		}
	}

	// For the dev-only debug plugin.
	FishModels fishModels()
	{
		return fishModels;
	}

	SeaSpotFish seaSpotFish()
	{
		return seaSpotFish;
	}

	RiverSpotFish riverSpotFish()
	{
		return riverSpotFish;
	}
}
