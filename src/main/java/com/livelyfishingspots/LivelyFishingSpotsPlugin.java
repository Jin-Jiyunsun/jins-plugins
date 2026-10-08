package com.livelyfishingspots;

import com.google.inject.Provides;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

/**
 * Shows fish swimming at fishing spots. Visual only: no clickboxes or menu options, and the spots are untouched.
 */
@Slf4j
@PluginDescriptor(
	// TEMPORARY: dev name, back to "Lively Fishing Spots" before a Hub release.
	name = "116-lively-fishing-spots",
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

	@Inject
	private ScheduledExecutorService executor;

	// Created in startUp, as they need the client.
	private FishModels fishModels;
	private SeaSpotFish seaSpotFish;
	private RiverSpotFish riverSpotFish;
	// Last Fishing XP seen, or -1, to tell each catch's drop.
	private int fishingXp = -1;
	// TEMPORARY: whether Pick routes is on, kept so menu building doesn't look it up each entry; the picked route
	// start awaiting its end, or null.
	private boolean debugPick;
	// TEMPORARY: whether Bake rivers is on, and the baker recording while it is.
	private boolean debugBake;
	private RiverBaker riverBaker;
	// TEMPORARY: checks for changed bake files, and when each was last changed, by name.
	private ScheduledFuture<?> bakeWatch;
	private final Map<String, Long> bakeTimes = new ConcurrentHashMap<>();
	private WorldPoint pickedStart;
	// Whether what's being picked is a fork of a baked river, not a river of its own.
	private boolean pickingFork;
	// A river's route point picked up to move, or null.
	private WorldPoint movingPoint;
	// TEMPORARY: whether the point picked up is a lake's spawn point, not a river's.
	private boolean movingSpawn;
	private final List<WorldPoint> pickedWays = new ArrayList<>();

	@Inject
	private LivelyFishingSpotsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RiverDebugOverlay riverDebugOverlay;

	@Inject
	private FishCountOverlay fishCountOverlay;

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
		riverSpotFish.setSchooled(config.riverSwimming() == RiverSwimming.SCHOOLED);
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
		debugPick = config.debugPick();
		debugBake = config.debugRiverBake();
		riverBaker = new RiverBaker(client, riverSpotFish, this::saveBaked);
		// Side channels picked but not laid yet get their paths.
		clientThread.invoke(riverBaker::layBranches);
		// Bakes changed in .runelite are reloaded while playing.
		bakeTimes.clear();
		checkBakes(false);
		bakeWatch = executor.scheduleWithFixedDelay(() -> checkBakes(true), 2, 2, TimeUnit.SECONDS);
		RiverSpotFish.tune(config);
		tuneLooks();
		riverDebugOverlay.setRivers(riverSpotFish, riverBaker);
		overlayManager.add(riverDebugOverlay);
		fishCountOverlay.setSources(seaSpotFish, riverSpotFish, fishModels);
		overlayManager.add(fishCountOverlay);
		seaSpotFish.setSeeThrough(seeThroughWater());
		riverSpotFish.setSeeThrough(seeThroughWater());
		clientThread.invoke(this::addSpotFish);
		log.debug("Lively Fishing Spots started");
	}

	@Override
	protected void shutDown()
	{
		if (bakeWatch != null)
		{
			bakeWatch.cancel(false);
			bakeWatch = null;
		}
		overlayManager.remove(riverDebugOverlay);
		overlayManager.remove(fishCountOverlay);
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
			TickTimes.clear();
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
	public void onGameTick(GameTick event)
	{
		long started = System.nanoTime();
		riverSpotFish.activate();
		if (debugBake)
		{
			riverBaker.record();
		}
		TickTimes.add(TickTimes.STARTING, started);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		long started = System.nanoTime();
		fishModels.makeQueued();
		started = TickTimes.add(TickTimes.MODELS, started);
		seaSpotFish.swim();
		started = TickTimes.add(TickTimes.SEA, started);
		riverSpotFish.swim();
		TickTimes.add(TickTimes.RIVERS, started);
		TickTimes.endTick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LivelyFishingSpotsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if ("debugPick".equals(key))
		{
			debugPick = config.debugPick();
			return;
		}
		if ("riverSwimming".equals(key) || "riverFishAmount".equals(key) || "lakeFishAmount".equals(key)
			|| "riverDeep".equals(key))
		{
			clientThread.invoke(() ->
			{
				riverSettings();
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
				if ("debugRiverBake".equals(key) && !config.debugRiverBake())
				{
					// Saved, and shown at once.
					Map<String, String> files = riverBaker.save();
					saveBaked(files);
					files.forEach(riverSpotFish::reloadBaked);
				}
				debugBake = config.debugRiverBake();
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
	 * TEMPORARY: looks for bake files in .runelite that are new or changed since last looked, and when reloading,
	 * hands each one's text to the rivers on the client thread. Off the client thread.
	 */
	private void checkBakes(boolean reload)
	{
		try
		{
			File[] files = new File(RuneLite.RUNELITE_DIR, RiverSpotFish.BAKED_FOLDER).listFiles(
				(folder, name) -> name.endsWith(".txt"));
			for (File file : files == null ? new File[0] : files)
			{
				Long seen = bakeTimes.put(file.getName(), file.lastModified());
				if (reload && (seen == null || seen != file.lastModified()))
				{
					String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
					clientThread.invoke(() -> riverSpotFish.reloadBaked(file.getName(), text));
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't check the bakes", e);
		}
	}

	/**
	 * TEMPORARY: writes baked rivers and lakes to .runelite/lively-fishing-spots/baked, off the client thread.
	 */
	private void saveBaked(Map<String, String> files)
	{
		executor.execute(() ->
		{
			File folder = new File(RuneLite.RUNELITE_DIR, "lively-fishing-spots/baked");
			if (!folder.mkdirs() && !folder.isDirectory())
			{
				log.warn("Couldn't make {}", folder);
				return;
			}
			for (Map.Entry<String, String> file : files.entrySet())
			{
				try
				{
					Files.write(new File(folder, file.getKey()).toPath(), file.getValue().getBytes(StandardCharsets.UTF_8));
					// Already in use, so the watch for changed bakes skips it.
					bakeTimes.put(file.getKey(), new File(folder, file.getKey()).lastModified());
					log.debug("Baked {}", new File(folder, file.getKey()));
				}
				catch (IOException e)
				{
					log.warn("Couldn't save {}", file.getKey(), e);
				}
			}
		});
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
	 * TEMPORARY: adds picking to the tile menu: rivers, forks, route points, lake spawns, fish blockers and allowers.
	 * Local only; nothing is sent to the game.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!debugPick || event.getType() != MenuAction.WALK.getId())
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
		riverBaker.clearPreviews();
		// A river being picked, or a fork (a side channel of a baked river): its end and waypoints.
		if (pickedStart != null)
		{
			String kind = pickingFork ? "Fork" : "River";
			client.getMenu().createMenuEntry(-1).setOption(tint(kind + " end", pickingFork)).setTarget("").setType(MenuAction.RUNELITE)
				.onClick(entry ->
				{
					List<WorldPoint> points = new ArrayList<>();
					points.add(pickedStart);
					points.addAll(pickedWays);
					points.add(at);
					if (!pickingFork)
					{
						riverBaker.newRiver(points);
						riverSpotFish.clear();
						addSpotFish();
					}
					else if (!riverBaker.addBranch(points))
					{
						log.debug("Fork from {} to {}: no baked river near both ends", points.get(0), at);
					}
					pickedStart = null;
					pickedWays.clear();
					riverSpotFish.setPicking(List.of());
				});
			client.getMenu().createMenuEntry(-1).setOption(tint(kind + " waypoint", pickingFork)).setTarget("").setType(MenuAction.RUNELITE)
				.onClick(entry ->
				{
					pickedWays.add(at);
					List<WorldPoint> points = new ArrayList<>();
					points.add(pickedStart);
					points.addAll(pickedWays);
					riverSpotFish.setPicking(points);
				});
		}
		if (riverBaker.hasFork(at))
		{
			client.getMenu().createMenuEntry(-1).setOption(tint("Remove fork", true)).setTarget("").setType(MenuAction.RUNELITE)
				.onClick(entry -> riverBaker.removeFork(at));
			// The share of fish taking it.
			Menu shares = client.getMenu().createMenuEntry(-1).setOption(tint("Fork share", true)).setTarget("")
				.setType(MenuAction.RUNELITE).createSubMenu();
			for (int share : new int[]{-1, 90, 75, 50, 25, 10})
			{
				shares.createMenuEntry(-1).setOption(share < 0 ? "By width" : share + "%").setTarget("")
					.setType(MenuAction.RUNELITE).onClick(entry -> riverBaker.setForkShare(at, share));
			}
		}
		for (int kind = 0; kind < 2; kind++)
		{
			boolean fork = kind == 1;
			client.getMenu().createMenuEntry(-1).setOption(tint(fork ? "Fork start" : "River start", fork)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					pickedStart = at;
					pickingFork = fork;
					pickedWays.clear();
					riverSpotFish.setPicking(List.of(at));
				});
		}
		// Fish blockers, allowers, surfacers and divers, the whole tile or a half, each in a submenu; or clearing the one
		// there. Each shows what it would place or remove while hovered.
		String[] markNames = {"Fish blocker", "Fish allower", "Fish surfacer", "Fish diver"};
		for (int k = 0; k < markNames.length; k++)
		{
			int kind = k;
			// Coloured as they're drawn.
			String name = ColorUtil.wrapWithColorTag(markNames[kind], RiverBaker.markColour(kind));
			if (riverBaker.hasMark(at, kind))
			{
				MenuEntry remove = client.getMenu().createMenuEntry(-1).setOption("Remove " + name).setTarget("")
					.setType(MenuAction.RUNELITE).onClick(entry -> riverBaker.setMark(at, null, kind));
				riverBaker.preview(remove, at, riverBaker.markAt(at, kind), kind);
				continue;
			}
			// Clicked itself, the whole tile.
			MenuEntry parent = client.getMenu().createMenuEntry(-1).setOption(name).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry -> riverBaker.setMark(at, RiverBaker.Half.WHOLE, kind));
			riverBaker.preview(parent, at, RiverBaker.Half.WHOLE, kind);
			Menu halves = parent.createSubMenu();
			for (RiverBaker.Half half : RiverBaker.Half.values())
			{
				MenuEntry option = halves.createMenuEntry(-1).setOption(half == RiverBaker.Half.WHOLE ? "Whole tile"
						: half.name().charAt(0) + half.name().substring(1).toLowerCase() + " half").setTarget("")
					.setType(MenuAction.RUNELITE).onClick(entry -> riverBaker.setMark(at, half, kind));
				riverBaker.preview(option, at, half, kind);
			}
		}
		// A river's route points, or a lake's spawn points: added, moved (picked up, then put down) or removed.
		if (movingPoint != null)
		{
			WorldPoint from = movingPoint;
			boolean lake = movingSpawn;
			client.getMenu().createMenuEntry(-1).setOption(tint(lake ? "Put lake spawn here" : "Put river point here", false))
				.setTarget("").setType(MenuAction.RUNELITE).onClick(entry ->
				{
					riverBaker.moveRoutePoint(from, at, lake);
					movingPoint = null;
					riverSpotFish.clear();
					addSpotFish();
				});
		}
		for (boolean lake : new boolean[]{false, true})
		{
			if (!riverBaker.isRoutePoint(at, lake))
			{
				continue;
			}
			String what = lake ? "lake spawn" : "river point";
			client.getMenu().createMenuEntry(-1).setOption(tint("Remove " + what, false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					riverBaker.removeRoutePoint(at, lake);
					riverSpotFish.clear();
					addSpotFish();
				});
			client.getMenu().createMenuEntry(-1).setOption(tint("Move " + what, false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					movingPoint = at;
					movingSpawn = lake;
				});
		}
		if (!riverBaker.isRoutePoint(at, false))
		{
			client.getMenu().createMenuEntry(-1).setOption(tint("Add river point", false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					riverBaker.addRoutePoint(at);
					riverSpotFish.clear();
					addSpotFish();
				});
		}
		client.getMenu().createMenuEntry(-1).setOption(tint("Lake spawn", false)).setTarget("").setType(MenuAction.RUNELITE)
			.onClick(entry ->
			{
				riverBaker.addLakeSpawn(at);
				riverSpotFish.clear();
				addSpotFish();
			});
	}

	/**
	 * TEMPORARY: a picking menu option coloured as what it picks is drawn: forks light blue, rivers and lakes yellow.
	 */
	private static String tint(String option, boolean fork)
	{
		return ColorUtil.wrapWithColorTag(option, fork ? new Color(120, 200, 255) : Color.YELLOW);
	}

	/**
	 * TEMPORARY: sets each river fish's look from the spinners.
	 */
	private void tuneLooks()
	{
		fishModels.tuneLook(ItemID.RAW_TROUT, LOOK_PLACES, new int[]{config.debugLookTroutSink(), config.debugLookTroutRise(), config.debugLookTroutRoll(), config.debugLookTroutTilt(), config.debugLookTroutSize(), config.debugLookTroutTurn(), config.debugLookTroutLightest(), config.debugLookTroutWag(), config.debugLookTroutTip(), config.debugLookTroutPace(), config.debugLookTroutSurge(), config.debugLookTroutTipPivot(), config.debugLookTroutPivot(), config.debugLookTroutDipEvery(), config.debugLookTroutDipDepth(), config.debugLookTroutDipMillis()});
		fishModels.tuneLook(ItemID.RAW_SALMON, LOOK_PLACES, new int[]{config.debugLookSalmonSink(), config.debugLookSalmonRise(), config.debugLookSalmonRoll(), config.debugLookSalmonTilt(), config.debugLookSalmonSize(), config.debugLookSalmonTurn(), config.debugLookSalmonLightest(), config.debugLookSalmonWag(), config.debugLookSalmonTip(), config.debugLookSalmonPace(), config.debugLookSalmonSurge(), config.debugLookSalmonTipPivot(), config.debugLookSalmonPivot(), config.debugLookSalmonDipEvery(), config.debugLookSalmonDipDepth(), config.debugLookSalmonDipMillis()});
		fishModels.tuneLook(ItemID.RAW_PIKE, LOOK_PLACES, new int[]{config.debugLookPikeSink(), config.debugLookPikeRise(), config.debugLookPikeRoll(), config.debugLookPikeTilt(), config.debugLookPikeSize(), config.debugLookPikeTurn(), config.debugLookPikeLightest(), config.debugLookPikeWag(), config.debugLookPikeTip(), config.debugLookPikePace(), config.debugLookPikeSurge(), config.debugLookPikeTipPivot(), config.debugLookPikePivot(), config.debugLookPikeDipEvery(), config.debugLookPikeDipDepth(), config.debugLookPikeDipMillis()});
		int[] rainbow = {config.debugLookRainbowSink(), config.debugLookRainbowRise(), config.debugLookRainbowRoll(), config.debugLookRainbowTilt(), config.debugLookRainbowSize(), config.debugLookRainbowTurn(), config.debugLookRainbowLightest(), config.debugLookRainbowWag(), config.debugLookRainbowTip(), config.debugLookRainbowPace(), config.debugLookRainbowSurge(), config.debugLookRainbowTipPivot(), config.debugLookRainbowPivot(), config.debugLookRainbowDipEvery(), config.debugLookRainbowDipDepth(), config.debugLookRainbowDipMillis()};
		FishModels.oneSidedDepth = config.debugLookRainbowThickness();
		// Rainbow fish are stretched too, look place 19.
		int[] places = Arrays.copyOf(LOOK_PLACES, LOOK_PLACES.length + 1);
		places[LOOK_PLACES.length] = 19;
		int[] values = Arrays.copyOf(rainbow, rainbow.length + 1);
		values[rainbow.length] = config.debugLookRainbowStretch();
		fishModels.tuneLook(ItemID.HUNTING_RAW_FISH_SPECIAL, places, values);
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
