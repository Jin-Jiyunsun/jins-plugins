package com.livelyfishingspots;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

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

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	// The fish swimming at the fishing spots, which need the client to be made.
	private SeaSpotFish seaSpotFish;

	@Override
	protected void startUp()
	{
		seaSpotFish = new SeaSpotFish(client);
		seaSpotFish.setSeeThrough(seeThroughWater());
		clientThread.invoke(this::addSpotFish);
		log.debug("Lively Fishing Spots started");
	}

	@Override
	protected void shutDown()
	{
		SeaSpotFish fish = seaSpotFish;
		clientThread.invoke(fish::clear);
		log.debug("Lively Fishing Spots stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// A map load drops the fish from the scene and may move where in it the spots are, without the spots
		// themselves being seen to go and come again, so their fish are put back in once it's loaded, as they were.
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			WorldView top = client.getTopLevelWorldView();
			if (top != null)
			{
				seaSpotFish.reload(top.npcs());
			}
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
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		seaSpotFish.remove(event.getNpc());
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		seaSpotFish.swim();
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
		}
	}
}
