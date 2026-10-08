package com.livelyfishingspots;

import com.google.inject.Provides;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

/**
 * Dev only, never shipped: tools for building Lively Fishing Spots. Baking rivers and lakes (picking routes, lake
 * spawns, forks and fish marks from the tile menu, recording the water walked), tuning spinners, debug drawing and the
 * debug panel. Built with the dev client from src/debug; the plugin itself is built from src/main alone. It finds
 * the plugin in RuneLite's plugin list (a plugin dependency needs the other plugin to offer a service). Its menu
 * entries are RuneLite's own: nothing is sent to the game.
 */
@Slf4j
@PluginDescriptor(
	name = "116-lively-fishing-spots debug",
	description = "Dev tools for Lively Fishing Spots: baking, tuning and debug drawing",
	developerPlugin = true
)
public class LivelyFishingSpotsDebugPlugin extends Plugin
{
	// The Look value each look spinner sets.
	private static final int[] LOOK_PLACES = {4, 5, 0, 1, 2, 7, 15, 16, 17, 23, 24, 22, 6, 12, 13, 14};
	// Where the bakes made while playing are saved and read from, in .runelite.
	static final String BAKED_FOLDER = "lively-fishing-spots/baked";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LivelyFishingSpotsDebugConfig config;

	@Inject
	private RiverDebugOverlay riverDebugOverlay;

	@Inject
	private FishCountOverlay fishCountOverlay;

	// The plugin, and the rivers it has now (a new one each time it starts), with the baker and drawing for them.
	private LivelyFishingSpotsPlugin main;
	private RiverSpotFish rivers;
	private RiverBaker baker;
	private RiverDrawing drawing;
	// Whether Pick routes is on, kept so menu building doesn't look it up each entry; and whether Bake water bodies
	// is on.
	private boolean debugPick;
	private boolean debugBake;
	// Checks for changed bake files, and when each was last changed, by name.
	private ScheduledFuture<?> bakeWatch;
	private final Map<String, Long> bakeTimes = new ConcurrentHashMap<>();
	// The picked route start awaiting its end, or null; whether it's a fork of a baked river; its waypoints.
	private WorldPoint pickedStart;
	private boolean pickingFork;
	private final List<WorldPoint> pickedWays = new ArrayList<>();
	// A river's route point, or a lake's spawn point, picked up to move, or null; and which.
	private WorldPoint movingPoint;
	private boolean movingSpawn;

	@Provides
	LivelyFishingSpotsDebugConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LivelyFishingSpotsDebugConfig.class);
	}

	@Override
	protected void startUp()
	{
		main = pluginManager.getPlugins().stream().filter(LivelyFishingSpotsPlugin.class::isInstance)
			.map(LivelyFishingSpotsPlugin.class::cast).findFirst().orElse(null);
		debugPick = config.debugPick();
		debugBake = config.debugRiverBake();
		TickTimes.clear();
		RiverSpotFish.probe = TickTimes.PROBE;
		overlayManager.add(riverDebugOverlay);
		overlayManager.add(fishCountOverlay);
		// The bakes in .runelite, read now and whenever they change; they take over the plugin's own of the same name.
		bakeTimes.clear();
		bakeWatch = executor.scheduleWithFixedDelay(() -> checkBakes(true), 0, 2, TimeUnit.SECONDS);
		clientThread.invoke(this::attached);
	}

	@Override
	protected void shutDown()
	{
		RiverSpotFish.probe = Probe.NONE;
		if (bakeWatch != null)
		{
			bakeWatch.cancel(false);
			bakeWatch = null;
		}
		overlayManager.remove(riverDebugOverlay);
		overlayManager.remove(fishCountOverlay);
		rivers = null;
	}

	/**
	 * Follows the plugin's rivers, which are new each time it starts (in either order with this one): a baker and
	 * drawing for them, every tuning spinner's value and each fish's look set, the fish started again with them, side
	 * channels picked but not laid yet given their paths, and the bakes in .runelite read in. False if the plugin isn't
	 * running. Client thread.
	 */
	private boolean attached()
	{
		RiverSpotFish now = main == null || !pluginManager.isPluginActive(main) ? null : main.riverSpotFish();
		if (now != rivers)
		{
			rivers = now;
			baker = now == null ? null : new RiverBaker(client, now, this::saveBaked);
			drawing = now == null ? null : new RiverDrawing(client, now);
			riverDebugOverlay.setRivers(drawing, baker);
			fishCountOverlay.setSources(main == null ? null : main.seaSpotFish(), now,
				main == null ? null : main.fishModels());
			if (now != null)
			{
				tune();
				tuneLooks();
				main.seaSpotFish().clear();
				main.fishModels().clear();
				main.refresh();
				baker.layBranches();
				// The bakes in .runelite are read into the new rivers on the next check.
				bakeTimes.clear();
			}
		}
		return rivers != null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		long started = TickTimes.start();
		// Kept to the plugin's rivers, should it have started again.
		if (attached() && debugBake)
		{
			baker.record();
		}
		TickTimes.add(TickTimes.BAKING, started);
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
		boolean look = key.startsWith("debugLook");
		if (look || key.startsWith("debugRiver"))
		{
			clientThread.invoke(() ->
			{
				if (!attached())
				{
					return;
				}
				if ("debugRiverBake".equals(key) && !config.debugRiverBake())
				{
					// Saved, and shown at once.
					Map<String, String> files = baker.save();
					saveBaked(files);
					files.forEach((name, text) -> Bakes.reloadBaked(rivers, name, text));
				}
				debugBake = config.debugRiverBake();
				tune();
				if (look)
				{
					tuneLooks();
				}
				// Shape and look changes rebuild the river shoals.
				if (look || LivelyFishingSpotsDebugConfig.REBUILD.contains(key))
				{
					main.refresh();
				}
			});
		}
	}

	/**
	 * Adds picking to the tile menu: rivers, forks, route points, lake spawns, fish blockers and allowers.
	 * Local only; nothing is sent to the game.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!debugPick || event.getType() != MenuAction.WALK.getId() || !attached())
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
		baker.clearPreviews();
		// The rows in reading order, top to bottom; each new menu entry goes above the last, so they're made from the
		// bottom up.
		List<Runnable> rows = new ArrayList<>();
		// A river being picked, or a fork (a side channel of a baked river): its end and waypoints.
		if (pickedStart != null)
		{
			String kind = pickingFork ? "Fork" : "River";
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint(kind + " end", pickingFork)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					List<WorldPoint> points = new ArrayList<>();
					points.add(pickedStart);
					points.addAll(pickedWays);
					points.add(at);
					if (!pickingFork)
					{
						baker.newRiver(points);
						main.refresh();
					}
					else if (!baker.addBranch(points))
					{
						log.debug("Fork from {} to {}: no baked river near both ends", points.get(0), at);
					}
					pickedStart = null;
					pickedWays.clear();
					drawing.setPicking(List.of());
				}));
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint(kind + " waypoint", pickingFork)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					pickedWays.add(at);
					List<WorldPoint> points = new ArrayList<>();
					points.add(pickedStart);
					points.addAll(pickedWays);
					drawing.setPicking(points);
				}));
		}
		// A river's route point, or a lake's spawn point, picked up to move: put down here.
		if (movingPoint != null)
		{
			WorldPoint from = movingPoint;
			boolean lake = movingSpawn;
			rows.add(() -> client.getMenu().createMenuEntry(-1)
				.setOption(tint(lake ? "Put lake spawn here" : "Put river point here", false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					baker.moveRoutePoint(from, at, lake);
					movingPoint = null;
					main.refresh();
				}));
		}
		for (int kind = 0; kind < 2; kind++)
		{
			boolean fork = kind == 1;
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint(fork ? "Fork start" : "River start", fork))
				.setTarget("").setType(MenuAction.RUNELITE).onClick(entry ->
				{
					pickedStart = at;
					pickingFork = fork;
					pickedWays.clear();
					drawing.setPicking(List.of(at));
				}));
		}
		rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint("Lake spawn", false)).setTarget("")
			.setType(MenuAction.RUNELITE).onClick(entry ->
			{
				baker.addLakeSpawn(at);
				main.refresh();
			}));
		if (!baker.isRoutePoint(at, false))
		{
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint("Add river point", false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					baker.addRoutePoint(at);
					main.refresh();
				}));
		}
		// A river's route points, or a lake's spawn points: moved (picked up, then put down) or removed.
		for (boolean lake : new boolean[]{false, true})
		{
			if (!baker.isRoutePoint(at, lake))
			{
				continue;
			}
			String what = lake ? "lake spawn" : "river point";
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint("Move " + what, false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					movingPoint = at;
					movingSpawn = lake;
				}));
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint("Remove " + what, false)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					baker.removeRoutePoint(at, lake);
					main.refresh();
				}));
		}
		// Fish blockers, allowers, surfacers and divers, the whole tile or a half, each in a submenu; or clearing the one
		// there. Each shows what it would place or remove while hovered.
		String[] markNames = {"Fish blocker", "Fish allower", "Fish surfacer", "Fish diver"};
		for (int k = 0; k < markNames.length; k++)
		{
			int kind = k;
			// Coloured as they're drawn.
			String name = ColorUtil.wrapWithColorTag(markNames[kind], RiverBaker.markColour(kind));
			if (baker.hasMark(at, kind))
			{
				rows.add(() ->
				{
					MenuEntry remove = client.getMenu().createMenuEntry(-1).setOption("Remove " + name).setTarget("")
						.setType(MenuAction.RUNELITE).onClick(entry -> baker.setMark(at, null, kind));
					baker.preview(remove, at, baker.markAt(at, kind), kind);
				});
				continue;
			}
			rows.add(() ->
			{
				// Clicked itself, the whole tile.
				MenuEntry parent = client.getMenu().createMenuEntry(-1).setOption(name).setTarget("")
					.setType(MenuAction.RUNELITE).onClick(entry -> baker.setMark(at, RiverBaker.Half.WHOLE, kind));
				baker.preview(parent, at, RiverBaker.Half.WHOLE, kind);
				// Whole tile, north, south, east, west, top to bottom: made from the bottom up.
				Menu halves = parent.createSubMenu();
				RiverBaker.Half[] all = RiverBaker.Half.values();
				for (int h = all.length - 1; h >= 0; h--)
				{
					RiverBaker.Half half = all[h];
					String label = half == RiverBaker.Half.WHOLE ? "Whole tile"
						: half.name().charAt(0) + half.name().substring(1).toLowerCase() + " half";
					// Coloured as their parent.
					MenuEntry option = halves.createMenuEntry(-1)
						.setOption(ColorUtil.wrapWithColorTag(label, RiverBaker.markColour(kind))).setTarget("")
						.setType(MenuAction.RUNELITE).onClick(entry -> baker.setMark(at, half, kind));
					baker.preview(option, at, half, kind);
				}
			});
		}
		if (baker.hasFork(at))
		{
			// The share of fish taking it, by width first, then most to least: made from the bottom up.
			rows.add(() ->
			{
				Menu shares = client.getMenu().createMenuEntry(-1).setOption(tint("Fork share", true)).setTarget("")
					.setType(MenuAction.RUNELITE).createSubMenu();
				int[] all = {-1, 90, 75, 50, 25, 10};
				for (int k = all.length - 1; k >= 0; k--)
				{
					int share = all[k];
					shares.createMenuEntry(-1).setOption(tint(share < 0 ? "By width" : share + "%", true)).setTarget("")
						.setType(MenuAction.RUNELITE).onClick(entry -> baker.setForkShare(at, share));
				}
			});
			rows.add(() -> client.getMenu().createMenuEntry(-1).setOption(tint("Remove fork", true)).setTarget("")
				.setType(MenuAction.RUNELITE).onClick(entry -> baker.removeFork(at)));
		}
		for (int k = rows.size() - 1; k >= 0; k--)
		{
			rows.get(k).run();
		}
	}

	/**
	 * A picking menu option coloured as what it picks is drawn: forks light blue, rivers and lakes yellow.
	 */
	private static String tint(String option, boolean fork)
	{
		return ColorUtil.wrapWithColorTag(option, fork ? new Color(120, 200, 255) : Color.YELLOW);
	}

	/**
	 * Looks for bake files in .runelite that are new or changed since last looked, and when reloading,
	 * hands each one's text to the rivers on the client thread. Off the client thread.
	 */
	private void checkBakes(boolean reload)
	{
		try
		{
			File[] files = new File(RuneLite.RUNELITE_DIR, LivelyFishingSpotsDebugPlugin.BAKED_FOLDER).listFiles(
				(folder, name) -> name.endsWith(".txt"));
			for (File file : files == null ? new File[0] : files)
			{
				Long seen = bakeTimes.put(file.getName(), file.lastModified());
				if (reload && (seen == null || seen != file.lastModified()))
				{
					String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
					clientThread.invoke(() ->
					{
						if (attached())
						{
							Bakes.reloadBaked(rivers, file.getName(), text);
						}
					});
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't check the bakes", e);
		}
	}

	/**
	 * Writes baked rivers and lakes to .runelite/lively-fishing-spots/baked, off the client thread.
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
	 * The debug panel's right-click menu, collapsing or expanding a section; kept in the config.
	 */
	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() != fishCountOverlay)
		{
			return;
		}
		String name = event.getEntry().getTarget();
		Set<String> collapsed = new LinkedHashSet<>(Arrays.asList(config.debugPanelCollapsed().split(",")));
		collapsed.remove("");
		if (!collapsed.remove(name))
		{
			collapsed.add(name);
		}
		String list = String.join(",", collapsed);
		configManager.setConfiguration(LivelyFishingSpotsConfig.GROUP, "debugPanelCollapsed", list);
		fishCountOverlay.menu(list);
	}

	/**
	 * Sets each river fish's look from the spinners.
	 */
	private void tuneLooks()
	{
		main.fishModels().tuneLook(ItemID.RAW_TROUT, LOOK_PLACES, new int[]{config.debugLookTroutSink(), config.debugLookTroutRise(), config.debugLookTroutRoll(), config.debugLookTroutTilt(), config.debugLookTroutSize(), config.debugLookTroutTurn(), config.debugLookTroutLightest(), config.debugLookTroutWag(), config.debugLookTroutTip(), config.debugLookTroutPace(), config.debugLookTroutSurge(), config.debugLookTroutTipPivot(), config.debugLookTroutPivot(), config.debugLookTroutDipEvery(), config.debugLookTroutDipDepth(), config.debugLookTroutDipMillis()});
		main.fishModels().tuneLook(ItemID.RAW_SALMON, LOOK_PLACES, new int[]{config.debugLookSalmonSink(), config.debugLookSalmonRise(), config.debugLookSalmonRoll(), config.debugLookSalmonTilt(), config.debugLookSalmonSize(), config.debugLookSalmonTurn(), config.debugLookSalmonLightest(), config.debugLookSalmonWag(), config.debugLookSalmonTip(), config.debugLookSalmonPace(), config.debugLookSalmonSurge(), config.debugLookSalmonTipPivot(), config.debugLookSalmonPivot(), config.debugLookSalmonDipEvery(), config.debugLookSalmonDipDepth(), config.debugLookSalmonDipMillis()});
		main.fishModels().tuneLook(ItemID.RAW_PIKE, LOOK_PLACES, new int[]{config.debugLookPikeSink(), config.debugLookPikeRise(), config.debugLookPikeRoll(), config.debugLookPikeTilt(), config.debugLookPikeSize(), config.debugLookPikeTurn(), config.debugLookPikeLightest(), config.debugLookPikeWag(), config.debugLookPikeTip(), config.debugLookPikePace(), config.debugLookPikeSurge(), config.debugLookPikeTipPivot(), config.debugLookPikePivot(), config.debugLookPikeDipEvery(), config.debugLookPikeDipDepth(), config.debugLookPikeDipMillis()});
		int[] rainbow = {config.debugLookRainbowSink(), config.debugLookRainbowRise(), config.debugLookRainbowRoll(), config.debugLookRainbowTilt(), config.debugLookRainbowSize(), config.debugLookRainbowTurn(), config.debugLookRainbowLightest(), config.debugLookRainbowWag(), config.debugLookRainbowTip(), config.debugLookRainbowPace(), config.debugLookRainbowSurge(), config.debugLookRainbowTipPivot(), config.debugLookRainbowPivot(), config.debugLookRainbowDipEvery(), config.debugLookRainbowDipDepth(), config.debugLookRainbowDipMillis()};
		FishModels.oneSidedDepth = config.debugLookRainbowThickness();
		// Rainbow fish are stretched too, look place 19.
		int[] places = Arrays.copyOf(LOOK_PLACES, LOOK_PLACES.length + 1);
		places[LOOK_PLACES.length] = 19;
		int[] values = Arrays.copyOf(rainbow, rainbow.length + 1);
		values[rainbow.length] = config.debugLookRainbowStretch();
		main.fishModels().tuneLook(ItemID.HUNTING_RAW_FISH_SPECIAL, places, values);
	}

	/**
	 * Reads the tuning spinners.
	 */
	private void tune()
	{
		RiverDrawing.drawRanges = config.debugRiverDrawRange();
		RiverDrawing.drawBanks = config.debugRiverDrawBanks();
		RiverDrawing.drawPath = config.debugRiverDrawPath();
		RiverDrawing.drawShade = config.debugRiverDrawShade();
		RiverDrawing.drawSpots = config.debugRiverDrawRings();
		RiverDrawing.drawFish = config.debugRiverDrawFish();
		RiverDrawing.drawSchools = config.debugRiverDrawSchools();
		RiverDrawing.drawPicked = config.debugRiverDrawPoints();
		RiverSpotFish.WEIGHTS.put(ItemID.RAW_TROUT, config.debugRiverShareTrout());
		RiverSpotFish.WEIGHTS.put(ItemID.RAW_SALMON, config.debugRiverShareSalmon());
		RiverSpotFish.WEIGHTS.put(ItemID.RAW_PIKE, config.debugRiverSharePike());
		RiverSpotFish.RAINBOW_SHARE = config.debugRiverShareRainbow() / 100.0;
		RiverSpotFish.BODY_EVERY = config.debugRiverBodyMinutes() * 60 * 50.0;
		RiverSpotFish.BODY_TEST = config.debugRiverBodyTest();
		RiverSpotFish.BODY_FEET = config.debugRiverBodyFeet() * 2048 / 360;
		RiverSpotFish.BOB_CYCLES = config.debugRiverBobCycles();
		RiverSpotFish.BOB_REST_CYCLES = config.debugRiverBobRestCycles();
		RiverSpotFish.DEEP_LEAST = config.debugRiverDeepLeast();
		RiverSpotFish.DEEP_MOST = config.debugRiverDeepMost();
		RiverSpotFish.DIVE_DEPTH = config.debugRiverDiveDepth();
		RiverSpotFish.DIVE_EASE = config.debugRiverDiveSpeed() / 100.0;
		RiverSpotFish.CIRCLE_LANES = config.debugRiverCircleLanes();
		RiverSpotFish.CIRCLE_LANE_SPACING = config.debugRiverCircleLaneSpacing();
		RiverSpotFish.CIRCLE_SIZE = config.debugRiverCircleSize();
		RiverSpotFish.CIRCLE_MOST = config.debugRiverCircleMost();
		RiverSpotFish.INNER_LANE_SPEED = config.debugRiverInnerLaneSpeed() / 100.0;
		RiverSpotFish.CIRCLE_CLEARANCE = config.debugRiverCircleClearance();
		RiverSpotFish.SCATTER_RATE = 1.0 / (config.debugRiverScatterSeconds() * 50.0);
		RiverSpotFish.LAKE_JOIN_CHANCE = config.debugRiverLakeJoinChance();
		RiverSpotFish.FISH_RANGE = config.debugRiverFishRange();
		RiverSpotFish.LOAD_MORE = config.debugRiverLoadMore();
		RiverSpotFish.LAKE_SPEED = config.debugRiverLakeSpeed() / 100.0;
		RiverSpotFish.CIRCLE_OFFSET = config.debugRiverCircleOffset();
		RiverSpotFish.RING_MANUAL = config.debugRiverRingManual();
		RiverSpotFish.RING_EAST = config.debugRiverRingEast();
		RiverSpotFish.RING_NORTH = config.debugRiverRingNorth();
		RiverSpotFish.TRAVEL_SPACING = config.debugRiverTravelSpacing();
		RiverSpotFish.SPREAD = config.debugRiverSpread() / 100.0;
		RiverSpotFish.WANDER = config.debugRiverWander() / 100.0;
	}
}
