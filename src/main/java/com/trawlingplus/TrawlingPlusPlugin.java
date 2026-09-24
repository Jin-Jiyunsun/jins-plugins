package com.trawlingplus;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Perspective;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldEntityDespawned;
import net.runelite.api.events.WorldEntitySpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.MapElementConfig;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapIcon;
import net.runelite.api.worldmap.WorldMapRegion;
import net.runelite.api.worldmap.WorldMapRenderer;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Trawling Plus",
	description = "Shows the routes that deep sea trawling shoals swim",
	tags = {"sailing", "trawling", "shoal", "fishing", "route"}
)
public class TrawlingPlusPlugin extends Plugin
{
	// The config keys of the Fish section's switches.
	private static final Set<String> FISH_KEYS = Set.of("showBluefin", "showGiantKrill", "showHaddock", "showYellowfin",
		"showHalibut", "showMarlin");

	private static final Map<Integer, String> SPECIES_BY_CLICKBOX = Map.of(
		ObjectID.SAILING_SHOAL_CLICKBOX_GIANT_KRILL, "Giant krill",
		ObjectID.SAILING_SHOAL_CLICKBOX_HADDOCK, "Haddock",
		ObjectID.SAILING_SHOAL_CLICKBOX_YELLOWFIN, "Yellowfin",
		ObjectID.SAILING_SHOAL_CLICKBOX_HALIBUT, "Halibut",
		ObjectID.SAILING_SHOAL_CLICKBOX_BLUEFIN, "Bluefin",
		ObjectID.SAILING_SHOAL_CLICKBOX_MARLIN, "Marlin"
	);

	// Mixed shoals follow their original shoal's route, but don't say which species that was.
	// They still have a name of their own, which is what their entry in routes.json is filed under.
	private static final Map<Integer, String> MIXED_BY_CLICKBOX = Map.of(
		ObjectID.SAILING_SHOAL_CLICKBOX_SHIMMERING, "Shimmering",
		ObjectID.SAILING_SHOAL_CLICKBOX_GLISTENING, "Glistening",
		ObjectID.SAILING_SHOAL_CLICKBOX_VIBRANT, "Vibrant"
	);

	// Inventories that belong to a boat arrive with this bit added to their id, so the cargo hold of
	// the fourth boat comes through as 966 plus this.
	private static final int BOAT_INVENTORY = 32768;

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

	// A stretch of route this close to where a sea creature that attacks boats spawns counts as dangerous, in tiles.
	private static final double DANGER_TILES = 20;

	// A safe stretch no longer than this between two dangerous ones counts as dangerous too, in tiles. On the
	// routes recorded so far the short gaps run up to 24.5 tiles and the next shortest is 53.
	private static final double DANGER_GAP_TILES = 30;

	// The type of the icon the game puts on the world map beside each trawling shoal route, "Trawling shoal" in the
	// map key. It isn't among the named gamevals, so it was found by logging the icons near every recorded route.
	private static final int TRAWLING_SHOAL_ICON = 1048;

	// How far a trawling shoal icon can sit from the route it belongs to, in tiles. Those seen sit within 7.
	private static final double SHOAL_ICON_TILES = 15;

	// How far the invisible point over a trawling shoal icon reaches past the icon, in pixels, so the game's own
	// hover label can't show at its edges; further on the left, where it showed most.
	private static final int SHOAL_ICON_PAD = 1;
	private static final int SHOAL_ICON_PAD_LEFT = 2;

	// How long another route has to be the nearer one before it takes over, in ticks: about five
	// seconds, long enough to cross the water between two routes without the drawn one changing
	// under you, short enough not to be waiting for it once you have plainly moved on.
	private static final int ROUTE_GRACE_TICKS = 8;

	// A shoal arriving within this many ticks of being baited was baited as it settled in, so that
	// arrival does not end the bait.
	private static final int ARRIVAL_GRACE_TICKS = 2;

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

	// The nets share one catch of up to this many fish, however many are fitted.
	private static final int NET_CAPACITY = 250;

	// How many ticks after the hold's screen closes a deposit made just before can still leave the inventory.
	private static final int DEPOSIT_TICKS = 2;

	// The most bait the Baited line counts up to; more shows as this with a plus.
	private static final int MAX_BAIT_SHOWN = 999;

	// How long the fish line stays up once the nets are empty and raised, after they were last emptied,
	// found empty or raised.
	private static final long FISH_LINE_LINGER_MILLIS = 60_000;

	// "You catch four giant krill!", "You catch a haddock!", "Jolly Jim catches three giant krill!"
	private static final Pattern CATCH = Pattern.compile("^(?:You catch|.+ catches) (\\S+) ");

	// The first line of the menu a net opens: "There are 46 fish across the two nets on the boat."
	private static final Pattern NET_MENU = Pattern.compile("^There (?:is|are) (\\S+) fish");

	// Counts the game writes out as words rather than figures.
	private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
		Map.entry("no", 0), Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1), Map.entry("two", 2),
		Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
		Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10),
		Map.entry("eleven", 11), Map.entry("twelve", 12), Map.entry("thirteen", 13), Map.entry("fourteen", 14),
		Map.entry("fifteen", 15), Map.entry("sixteen", 16), Map.entry("seventeen", 17),
		Map.entry("eighteen", 18), Map.entry("nineteen", 19), Map.entry("twenty", 20)
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Notifier notifier;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldMapPointManager worldMapPointManager;

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
	// What routes.json knows about each kind of shoal, by the name it is filed under.
	private Map<String, RouteData.Species> speciesByName = Collections.emptyMap();
	private Shoal nearestShoal;

	// Where the boat of the player is in world tiles, or null while they are not aboard one. Worked out
	// once a tick and handed to everything that wants it, the minimap included: it draws many times
	// over between ticks, and the boat has not moved in between.
	private double[] boatPlace;
	// The boat itself, while the player is aboard it, found on the tick for the display at the helm to
	// be drawn on rather than searched for among every world entity each frame.
	private WorldEntity boat;

	// The route nearest the boat, and the one waiting to take over from it. A route is only swapped
	// once another has been the nearer for a while: sailing the water between two of them would
	// otherwise flick back and forth between the pair with every small movement.
	private ShoalRoute nearestRoute;
	private ShoalRoute contender;
	private int contenderTicks;
	private boolean showGuides;
	// Whether the boat has a trawling net in either slot, updated whenever the game changes either slot.
	private boolean netsFitted;
	// Whether either net is lowered into the water, updated whenever the game changes either net's depth.
	private boolean netsLowered;

	// The last value of the baited step varbit, which changes with every bait, and whether the nearest
	// shoal is baited. Null until the first reading, so starting the plugin beside an already baited
	// shoal does not read as a bait having just been laid.
	private Integer baitedStep;
	private boolean baited;
	// The tick a crewmate last found no offcuts to bait with, so a change in the baited step that comes with it
	// isn't taken for a bait laid.
	private int baitFailedTick = -1;
	// The tick a bait was last announced in chat ("Bosun Zarah has baited a nearby halibut shoal with some fine
	// fish offcuts."), which is a real bait even on the same tick as a failed one, as when offcuts go into an
	// empty hold just as a crewmate tries; and the tick offcuts were last counted into the hold from the
	// inventory, which a failed attempt that same tick came before.
	private int baitAnnouncedTick = -1;
	private int depositTick = -1;

	// How much of each bait the hold had when it was last opened, less what has been used since, or -1
	// before the hold has been opened this session. The game only sends the hold when it is opened, so
	// between openings this is an estimate. Every chum station uses one offcut per bait.
	private int plainBait = -1;
	private int fineBait = -1;
	// Whether the bait the nearest shoal takes was seen running out: the last offcut laid, or a crewmate finding
	// none. Only then does the Baited line say there is none, never while the count isn't known. Cleared once the
	// hold is opened with offcuts in it, or when a new session makes the count unknown.
	private boolean ranOutOfBait;
	private String baitedLabel = label(-1, false);
	// The shoal the last bait was laid on, until it next arrives at a stop; whether it was sitting at one
	// on the last tick; and how many ticks ago the bait was laid.
	private Shoal baitedShoal;
	private boolean baitedWasStopped;
	private int ticksSinceBait;
	private boolean netsAtDepth;

	// How many fish are in the nets, or -1 while that is not known. The game never sends what the nets
	// hold, so this adds up what it says is caught and taken, and is put right whenever a net's menu
	// gives the true number.
	private int fishInNets = -1;
	private String fishLabel = fishLabel(-1);
	// When the nets were last emptied, found empty or raised, or -1.
	private long fishActivityMillis = -1;
	// Filled inventory slots as of the last inventory update, on which tick that update came and how many
	// slots it filled, or -1 before the first; and whether a partial take is waiting on that update.
	private int inventoryFilled = -1;
	private int inventoryTick = -1;
	private int inventoryAdded = -1;
	private boolean takePending;
	// Each kind of offcut in the inventory as of the last inventory update, or -1 before the first, and the
	// tick the hold's contents last arrived. Offcuts leaving the inventory aboard went into the hold, which
	// is how a deposit is counted when the hold is shut before its own update is sent.
	private int inventoryPlain = -1;
	private int inventoryFine = -1;
	private int holdTick = -1;
	// Whether the hold's screen is open, and the tick it last closed, or -1. Only offcuts leaving the inventory
	// while it is open, or just after, went into the hold; any other time they were dropped or used up.
	private boolean holdOpen;
	private int holdClosedTick = -1;
	// Whether the hold is full: found without room when the nets were last emptied into it, or showing no
	// free slots when last opened, until it is known to have room again.
	private boolean holdFull;
	// The shoal the leaving notification is watching, whether it was sitting at a stop on the last tick,
	// and whether the stop it is at has been warned about already.
	private Shoal leavingShoal;
	private boolean leavingWasStopped;
	private boolean leavingWarned;
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
		readShownFish();
		markDanger(routes);
		Map<String, RouteData.Species> byName = new HashMap<>();
		for (RouteData.Species species : routeData.species)
		{
			byName.put(species.name, species);
		}
		speciesByName = byName;
		clientThread.invoke(() ->
		{
			// Not logged in, or not aboard, and the nets are known to be empty: logging out and stepping off
			// the boat both empty them. Aboard part way through a session, what they hold is not known until
			// they are emptied or opened, since a count kept from before the plugin was last switched off
			// would have missed everything caught in between.
			boolean aboard = client.getGameState() == GameState.LOGGED_IN
				&& client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) == 1;
			resetFish(aboard ? -1 : 0);
			inventoryFilled = -1;
			inventoryPlain = -1;
			inventoryFine = -1;
			takePending = false;
			// Started part way through a session, the slots won't change to say what they hold.
			netsFitted = readNetsFitted();
			netsLowered = readNetsLowered();
			// Whatever the hold was doing while the plugin was off is not known, so don't warn about it.
			holdFull = false;
		});
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
		worldMapPointManager.removeIf(DangerMarker.class::isInstance);
		worldMapPointManager.removeIf(ShoalMarker.class::isInstance);
		// shutDown runs on the Swing thread; clear on the client thread so it can't race a game tick.
		clientThread.invoke(() ->
		{
			clearShoals();
			shoalMarkers.clear();
			shoalIconsSeen.clear();
			mapRegionsSeen.clear();
		});
		log.debug("Trawling Plus stopped");
	}

	Collection<Shoal> getShoals()
	{
		return shoals.values();
	}

	// The skull and crossbones on the world map for each place sea creatures that attack boats spawn close
	// enough to a route to threaten it, placed among the spawns that are that close so it sits by the
	// dangerous stretch, with the creature's name and level as its tooltip. On the map whenever the world map is
	// one of the maps routes show on.
	private List<DangerMarker> dangerMarkers = Collections.emptyList();

	// The fish switched off in the Fish section, by the name their routes carry, and the routes of every other
	// fish. Read when a switch changes rather than asked of the config every frame. Replaced whole rather than
	// changed, since the config is read on another thread than the one drawing.
	private volatile Set<String> hiddenFish = Collections.emptySet();
	private volatile List<ShoalRoute> shownRoutes = Collections.emptyList();

	/**
	 * A skull on the world map, told apart from every other plugin's points so only these are ever taken off.
	 */
	private static final class DangerMarker extends WorldMapPoint
	{
		// The fish of the route this skull threatens, so it goes with that fish's switch.
		final String fish;

		DangerMarker(double x, double y, String name, String fish)
		{
			super(new WorldPoint((int) Math.round(x), (int) Math.round(y), 0), TrawlingPlusMapOverlay.DANGER_ICON);
			setTooltip(name);
			this.fish = fish;
		}
	}

	/**
	 * Reads which fish are switched off in the Fish section, and which routes that leaves shown.
	 */
	private void readShownFish()
	{
		Set<String> hidden = new HashSet<>();
		if (!config.showBluefin())
		{
			hidden.add("Bluefin");
		}
		if (!config.showGiantKrill())
		{
			hidden.add("Giant krill");
		}
		if (!config.showHaddock())
		{
			hidden.add("Haddock");
		}
		if (!config.showYellowfin())
		{
			hidden.add("Yellowfin");
		}
		if (!config.showHalibut())
		{
			hidden.add("Halibut");
		}
		if (!config.showMarlin())
		{
			hidden.add("Marlin");
		}
		hiddenFish = hidden;
		shownRoutes = shownOf(routes);
	}

	private List<ShoalRoute> shownOf(List<ShoalRoute> all)
	{
		List<ShoalRoute> shown = new ArrayList<>();
		for (ShoalRoute route : all)
		{
			if (!hiddenFish.contains(route.getSpecies()))
			{
				shown.add(route);
			}
		}
		return shown;
	}

	/**
	 * The routes of the fish switched on in the Fish section.
	 */
	List<ShoalRoute> getShownRoutes()
	{
		return shownRoutes;
	}

	/**
	 * Whether a shoal's fish is switched on: the fish of the route it swims, which a mixed shoal shares, or of
	 * the shoal itself before it has been matched to one.
	 */
	boolean isShown(Shoal shoal)
	{
		ShoalRoute route = shoal.getRoute();
		String fish = route != null ? route.getSpecies() : SPECIES_BY_CLICKBOX.get(shoal.getClickbox());
		return fish == null || !hiddenFish.contains(fish);
	}

	/**
	 * Whether routes are shown on the world map.
	 */
	private boolean worldMapShown()
	{
		TrawlingPlusConfig.ShowOnMaps where = config.showOnMaps();
		return where == TrawlingPlusConfig.ShowOnMaps.WORLD_MAP || where == TrawlingPlusConfig.ShowOnMaps.BOTH;
	}

	// A tooltip over each of the game's trawling shoal icons whose route has been recorded, naming its species,
	// and the icons already looked at, so each is matched to a route only once.
	private final List<ShoalMarker> shoalMarkers = new ArrayList<>();
	private final Set<WorldPoint> shoalIconsSeen = new HashSet<>();
	// The map's loaded areas whose icons have been looked through, so each is only looked through once rather than
	// every tick the map is open. Held weakly, so areas the map lets go of aren't kept alive here.
	private final Set<WorldMapRegion> mapRegionsSeen = Collections.newSetFromMap(new WeakHashMap<>());

	/**
	 * An invisible point over one of the game's trawling shoal icons, there for its tooltip.
	 */
	private static final class ShoalMarker extends WorldMapPoint
	{
		// The fish of the route beside the icon, so the tooltip goes with that fish's switch.
		final String fish;

		ShoalMarker(WorldPoint icon, BufferedImage blank, String name, String fish)
		{
			// A tile to the west of the icon's own coordinate, as RuneLite's World Map plugin places its quest icons over
			// the game's: at the coordinate itself a point sits a tile east of the icon it covers.
			super(icon.dx(-1), blank);
			// The padding sits round the icon rather than moving it: the icon's middle goes on the point.
			setImagePoint(new Point(SHOAL_ICON_PAD_LEFT + (blank.getWidth() - SHOAL_ICON_PAD_LEFT - SHOAL_ICON_PAD) / 2,
				blank.getHeight() / 2));
			setTooltip(name);
			this.fish = fish;
		}
	}

	/**
	 * Lays a tooltip over each trawling shoal icon the world map has loaded, once each, naming the species of the
	 * recorded route it sits beside. It only looks while the world map is open, and stops once every recorded
	 * route has one. The tooltips go on whatever maps routes are shown on, even none, since they name the
	 * game's own icons rather than anything the plugin draws. Each area of the map is looked through once. Icons beside
	 * routes not yet recorded get none.
	 */
	private void findShoalIcons()
	{
		if (shoalMarkers.size() >= routes.size())
		{
			return;
		}

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		WorldMap worldMap = map == null || map.isHidden() ? null : client.getWorldMap();
		WorldMapRenderer renderer = worldMap == null ? null : worldMap.getWorldMapRenderer();
		WorldMapRegion[][] regions = renderer == null || !renderer.isLoaded() ? null : renderer.getMapRegions();
		if (regions == null)
		{
			return;
		}

		for (WorldMapRegion[] column : regions)
		{
			for (WorldMapRegion region : column == null ? new WorldMapRegion[0] : column)
			{
				if (region == null || mapRegionsSeen.contains(region))
				{
					continue;
				}

				Collection<WorldMapIcon> icons = region.getMapIcons();
				if (icons == null || icons.isEmpty())
				{
					// Possibly not filled in yet, so looked at again next tick.
					continue;
				}
				mapRegionsSeen.add(region);

				for (WorldMapIcon icon : icons)
				{
					WorldPoint at = icon.getCoordinate();
					if (icon.getType() != TRAWLING_SHOAL_ICON || at == null || !shoalIconsSeen.add(at))
					{
						continue;
					}

					ShoalRoute route = routeBeside(at.getX(), at.getY());
					if (route != null)
					{
						ShoalMarker marker = new ShoalMarker(at, blankLike(icon.getType()), route.getSpecies() + " shoal",
							route.getSpecies());
						shoalMarkers.add(marker);
						if (!hiddenFish.contains(marker.fish))
						{
							worldMapPointManager.add(marker);
						}
					}
				}
			}
		}
	}

	/**
	 * The recorded route a map icon at this place sits beside, or null when none is close enough.
	 */
	private ShoalRoute routeBeside(int x, int y)
	{
		ShoalRoute beside = null;
		double closest = SHOAL_ICON_TILES;
		for (ShoalRoute route : routes)
		{
			double[][] box = route.corners();
			if (x < box[0][0] - SHOAL_ICON_TILES || x > box[2][0] + SHOAL_ICON_TILES
				|| y < box[0][1] - SHOAL_ICON_TILES || y > box[2][1] + SHOAL_ICON_TILES)
			{
				continue;
			}

			for (int i = 0; i < route.sampleCount(); i++)
			{
				double distance = Math.hypot(route.sampleX(i) - x, route.sampleY(i) - y);
				if (distance <= closest)
				{
					closest = distance;
					beside = route;
				}
			}
		}
		return beside;
	}

	/**
	 * A see-through image the size of the game's own icon of this type and a little more all round, so hovering
	 * anywhere over that icon, edges included, shows the tooltip laid over it rather than the game's own label.
	 */
	private BufferedImage blankLike(int type)
	{
		MapElementConfig element = client.getMapElementConfig(type);
		SpritePixels sprite = element == null ? null : element.getMapIcon(false);
		int width = sprite == null ? 15 : Math.max(1, sprite.getWidth());
		int height = sprite == null ? 15 : Math.max(1, sprite.getHeight());
		return new BufferedImage(width + SHOAL_ICON_PAD_LEFT + SHOAL_ICON_PAD, height + SHOAL_ICON_PAD * 2,
			BufferedImage.TYPE_INT_ARGB);
	}

	/**
	 * Puts the skulls on the world map, or takes them off, to match the settings.
	 */
	private void showDangerMarkers()
	{
		worldMapPointManager.removeIf(DangerMarker.class::isInstance);
		if (worldMapShown())
		{
			for (DangerMarker marker : dangerMarkers)
			{
				if (!hiddenFish.contains(marker.fish))
				{
					worldMapPointManager.add(marker);
				}
			}
		}
	}

	/**
	 * Puts the shoal icon tooltips found so far on the world map for the fish switched on, and takes the rest off.
	 */
	private void showShoalMarkers()
	{
		worldMapPointManager.removeIf(ShoalMarker.class::isInstance);
		for (ShoalMarker marker : shoalMarkers)
		{
			if (!hiddenFish.contains(marker.fish))
			{
				worldMapPointManager.add(marker);
			}
		}
	}

	/**
	 * Marks the stretches of each route that pass close to where sea creatures that attack boats spawn, and
	 * works out where to mark each place they spawn that comes close enough to any route to threaten it.
	 */
	private void markDanger(List<ShoalRoute> marking)
	{
		List<DangerMarker> markers = new ArrayList<>();
		for (ShoalRoute route : marking)
		{
			route.markDanger(DANGER_TILES, DANGER_GAP_TILES);
			for (int threat = 0; threat < route.dangerousThreatCount(); threat++)
			{
				markers.add(new DangerMarker(route.dangerousThreatX(threat), route.dangerousThreatY(threat),
					route.dangerousThreatName(threat), route.getSpecies()));
			}
		}
		dangerMarkers = markers;
		showDangerMarkers();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clearShoals();
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// A new session, so what the hold held last time is no longer known.
			plainBait = -1;
			fineBait = -1;
			ranOutOfBait = false;
			baitedLabel = label(-1, false);
			// Logging out empties the nets into the hold, throwing away whatever doesn't fit, so every
			// session starts with them empty.
			resetFish(0);
			inventoryFilled = -1;
			inventoryAdded = -1;
			inventoryPlain = -1;
			inventoryFine = -1;
			takePending = false;
		}
	}

	/**
	 * Counts the bait in the cargo hold and checks whether it is full whenever it is opened, which is the
	 * only time the game sends it, and notes how full the inventory is, which is how a partial take from
	 * the nets is measured.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV)
		{
			countInventory(event.getItemContainer());
			return;
		}

		int hold = id & ~BOAT_INVENTORY;
		if ((id & BOAT_INVENTORY) == 0 || hold < InventoryID.SAILING_BOAT_1_CARGOHOLD
			|| hold > InventoryID.SAILING_BOAT_5_CARGOHOLD)
		{
			return;
		}

		ItemContainer contents = event.getItemContainer();
		plainBait = contents == null ? 0 : contents.count(ItemID.BRUT_FISH_CUTS);
		fineBait = contents == null ? 0 : contents.count(ItemID.SAILING_FINE_FISH_OFFCUTS);
		holdTick = client.getTickCount();
		if (plainBait + fineBait > 0)
		{
			ranOutOfBait = false;
		}
		baitedLabel = baitLabel();
		// The hold is only sent while its screen is open, and that screen shows in its corner how many slots
		// are taken out of how many. Its numbers are filled in after the contents arrive.
		clientThread.invokeLater(this::readHoldSpace);
	}

	/**
	 * Whether the hold is full, from the numbers in the corner of its screen: the slots taken over its
	 * capacity, such as 240 over 240. So opening a full hold warns as well as emptying the nets into one.
	 */
	private void readHoldSpace()
	{
		int taken = widgetNumber(InterfaceID.SailingBoatCargohold.OCCUPIEDSLOTS);
		int capacity = widgetNumber(InterfaceID.SailingBoatCargohold.CAPACITY);
		if (taken >= 0 && capacity > 0)
		{
			holdFull = taken >= capacity;
		}
	}

	/**
	 * The whole number an interface component shows, or -1 if it is not showing one.
	 */
	private int widgetNumber(int component)
	{
		Widget widget = client.getWidget(component);
		String text = widget == null || widget.isHidden() || widget.getText() == null
			? "" : Text.removeTags(widget.getText()).trim();
		if (text.isEmpty() || text.length() > 5 || !text.chars().allMatch(Character::isDigit))
		{
			return -1;
		}
		return Integer.parseInt(text);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (TrawlingPlusConfig.GROUP.equals(event.getGroup())
			&& "showOnMaps".equals(event.getKey()))
		{
			clientThread.invoke(this::showDangerMarkers);
		}

		if (TrawlingPlusConfig.GROUP.equals(event.getGroup()) && FISH_KEYS.contains(event.getKey()))
		{
			clientThread.invoke(() ->
			{
				readShownFish();
				// Dropped at once rather than left drawn through the wait before another route takes over.
				if (nearestRoute != null && hiddenFish.contains(nearestRoute.getSpecies()))
				{
					nearestRoute = null;
				}
				contender = null;
				contenderTicks = 0;
				showDangerMarkers();
				showShoalMarkers();
			});
		}

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
		markDanger(reshaped);
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
		shownRoutes = shownOf(reshaped);
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
		// The world map is looked at from anywhere, so its trawling shoal icons are found whether or not aboard.
		findShoalIcons();

		WorldEntity own = boardedBoat();
		if (own == null)
		{
			stopTracking();
			showGuides = false;
			return;
		}

		// A boat with no net fitted, which a raft always is since it can't take one, has nothing to show under Nets
		// only, so nothing is worked out for it either: no shoals followed, no routes matched.
		showGuides = guidesWanted();
		if (!showGuides)
		{
			stopTracking();
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
			shoal.setClickbox(clickbox);

			double[] position = shoal.position(client);
			if (position == null)
			{
				continue;
			}
			shoal.update(position, shoal.target(client));
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
		boat = own;
		boatPlace = worldPlace(own.getLocalLocation());
		nearestShoal = nearest();
		followNearestRoute();
		baited = stillBaited();
		baitedLabel = baitLabel();
		netsAtDepth = netsSetToDepth();
		checkShoalLeaving();
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
	 * The boat of the player while they are aboard it, as found on the last tick, or null.
	 */
	WorldEntity getBoat()
	{
		return boat;
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

		// How many of them are in range at all. Most of the time it is one, and one route has nothing to
		// be nearer than, so there is nothing to work out: knowing how far away it is would only ever be
		// used to rule others out, and there are none to rule out.
		List<ShoalRoute> shown = shownRoutes;
		ShoalRoute only = null;
		int candidates = 0;
		for (ShoalRoute route : shown)
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

		// The one already being drawn is looked at first, since the boat is usually still nearest the
		// route it was nearest a tick ago. That gives a distance to beat straight away, and every route
		// whose box is further off than that is then passed over without being looked through at all.
		ShoalRoute closest = nearestRoute != null && !hiddenFish.contains(nearestRoute.getSpecies())
			&& nearestRoute.overlaps(fromX, fromY, toX, toY)
			? nearestRoute
			: null;
		double nearestOffset = closest == null ? Double.MAX_VALUE
			: closest.project(afloat[0], afloat[1]).offset;

		for (ShoalRoute route : shown)
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
	 * Notifies once per stop when the nearest shoal is about to leave it: as it sets off, or the set number
	 * of seconds before, which can only be known for a stop whose timer is running.
	 */
	private void checkShoalLeaving()
	{
		Shoal shoal = nearestShoal;
		if (shoal == null)
		{
			leavingShoal = null;
			return;
		}

		boolean stopped = shoal.stopped();
		if (shoal != leavingShoal)
		{
			// A shoal first seen on the move has no stop to warn about until it reaches one.
			leavingShoal = shoal;
			leavingWasStopped = stopped;
			leavingWarned = !stopped;
			return;
		}

		if (stopped && !leavingWasStopped)
		{
			// Arrived at a new stop, which gets a warning of its own.
			leavingWarned = false;
		}

		int seconds = Math.max(0, Math.min(TrawlingPlusConfig.MAX_LEAVING_SECONDS, config.shoalLeavingSeconds()));
		double left = shoal.secondsAtStop();
		boolean due = seconds == 0
			? !stopped && leavingWasStopped
			: stopped && left >= 0 && left <= seconds;
		if (due && !leavingWarned)
		{
			leavingWarned = true;
			notifier.notify(config.notifyShoalLeaving(), seconds == 0
				? "The shoal is leaving its stop."
				: "The shoal leaves its stop in " + seconds + " seconds.");
		}
		leavingWasStopped = stopped;
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
	 * Whether the shoal nearest the boat is baited, as worked out on the last tick.
	 */
	boolean isBaited()
	{
		return baited;
	}

	/**
	 * Whether the bait the nearest shoal takes has run out, as worked out on the last tick or when the hold
	 * was last opened. Only ever true once it has been seen running out, never while the count isn't known.
	 */
	boolean isOutOfBait()
	{
		return ranOutOfBait && baitLeft() == 0;
	}

	/**
	 * What the Baited line says, with how much bait the nearest shoal can still be given, as worked out
	 * on the last tick or when the hold was last opened.
	 */
	String getBaitedLabel()
	{
		return baitedLabel;
	}

	/**
	 * What the fish line says: how many fish are in the nets, or a question mark while that is not known.
	 */
	/**
	 * Whether the nets are known to be full.
	 */
	boolean netsFull()
	{
		return fishInNets >= NET_CAPACITY;
	}

	String getFishLabel()
	{
		return fishLabel;
	}

	/**
	 * Whether the hold is full: the nets were last emptied into it without room for them all, or it showed
	 * no free slots when last opened, and it has not been seen with room since.
	 */
	boolean isHoldFull()
	{
		return holdFull;
	}

	/**
	 * Whether the boat has a trawling net in either slot, kept up to date as the game changes the slots.
	 */
	boolean netsFitted()
	{
		return netsFitted;
	}

	private boolean readNetsFitted()
	{
		// Each slot names the hotspot its net is built on, so an empty slot names no hotspot.
		return client.getVarbitValue(NET_SLOTS[0]) > 0 || client.getVarbitValue(NET_SLOTS[1]) > 0;
	}

	private boolean readNetsLowered()
	{
		// A net's depth reads 0 while it is raised out of the water, and 1 to 3 at the fishing depths.
		return client.getVarbitValue(NET_DEPTHS[0]) > 0 || client.getVarbitValue(NET_DEPTHS[1]) > 0;
	}

	/**
	 * Keeps count of the fish in the nets from what the game says about them, since nothing else tells.
	 * Every catch, the crew's included, is announced with how many fish it brought in, and emptying the
	 * nets or taking everything from them leaves none.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.MESBOX)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		if (message.startsWith("Your crew start moving the contents of the cargo hold"))
		{
			// Asking the crew on the dock to move everything in the hold to the bank empties it. This is said
			// off the boat, so it comes before the check for being aboard; what the crewmate says once it is
			// done is in their own words, so this is the line to go by.
			holdFull = false;
			return;
		}

		if (boatPlace == null)
		{
			return;
		}

		if (message.startsWith("Your crewmate on the chum station can"))
		{
			// "...can't find any offcuts in the cargo hold." Nothing was laid, and the hold has none of what the
			// nearest shoal takes.
			noBait();
			return;
		}

		if (message.contains("baited a nearby"))
		{
			baitAnnouncedTick = client.getTickCount();
			return;
		}

		if (message.startsWith("There are no fish in"))
		{
			// What opening an empty net says, in a box of its own rather than the menu a net with fish opens.
			noteFish(0);
		}
		else if (message.startsWith("You empty the net"))
		{
			// "..., but there was not enough space in there to do so entirely." A hold without room for them
			// all keeps some back and doesn't say how many; one that took them all had room.
			boolean noRoom = message.contains("not enough");
			if (noRoom && !holdFull)
			{
				notifier.notify(config.notifyHoldFull(), "Your cargo hold is full.");
			}
			holdFull = noRoom;
			if (noRoom)
			{
				setFish(-1);
			}
			else
			{
				noteFish(0);
			}
		}
		else if (message.startsWith("You take all of the fish from the net"))
		{
			noteFish(0);
		}
		else if (message.startsWith("You take some fish from the net"))
		{
			// An inventory without room for them all takes as many as fit, and doesn't say how many. The
			// inventory does, and its update can come either side of this message on the same tick.
			if (inventoryTick == client.getTickCount())
			{
				takeFromNets(inventoryAdded);
			}
			else
			{
				takePending = true;
			}
		}
		else if (message.startsWith("Your net has no more space"))
		{
			// "Your net has no more space for any raw bluefin.", for the crew's attempts as well as the player's.
			// On its own it doesn't mean the nets are full: it turns up now and then with plenty of room left,
			// the nets catching again straight after. So it only notifies while the count says full as well,
			// and then every time it is said.
			if (netsFull())
			{
				notifier.notify(config.notifyNetsFull(), "Your trawling nets are full.");
			}
		}
		else if (netsFitted())
		{
			// Only with a net fitted: the messages above name the nets, but a catch could be ordinary fishing.
			// A catch whose count can't be read, like "You catch some shrimps" from ordinary fishing, is not a
			// trawling catch and changes nothing. "Trawler's trust: You catch an additional fish." doesn't
			// match either, which is right: that fish is already in the number of the catch line after it.
			Matcher caught = CATCH.matcher(message);
			int fish = caught.find() ? number(caught.group(1)) : -1;
			if (fish >= 0 && fishInNets >= 0)
			{
				setFish(Math.min(NET_CAPACITY, fishInNets + fish));
			}
		}
	}

	/**
	 * Opening a net asks what to do with the fish in it and says how many there are, which puts the count
	 * right whatever it had drifted to.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.CHATMENU && boatPlace != null)
		{
			// The menu's lines are filled in after it loads.
			clientThread.invokeLater(this::readNetMenu);
		}
		else if (event.getGroupId() == InterfaceID.SAILING_BOAT_CARGOHOLD)
		{
			holdOpen = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SAILING_BOAT_CARGOHOLD)
		{
			holdOpen = false;
			holdClosedTick = client.getTickCount();
		}
	}

	/**
	 * Stepping off the boat throws away whatever is in the nets, so they are empty once the player is no
	 * longer aboard: taken from the game's own flag rather than from where the player stands, which can
	 * look like being off the boat for a moment while the area loads. Also keeps track of which net slots
	 * have a net in them.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		if (varbit == VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT && event.getValue() == 0)
		{
			resetFish(0);
		}
		else if (varbit == NET_SLOTS[0] || varbit == NET_SLOTS[1])
		{
			// Nets are only fitted or taken off ashore, and these arrive a few ticks after logging in, so the
			// answer is kept here as they change rather than asked for again every tick.
			netsFitted = readNetsFitted();
		}
		else if (varbit == NET_DEPTHS[0] || varbit == NET_DEPTHS[1])
		{
			boolean lowered = readNetsLowered();
			if (netsLowered && !lowered && client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) == 1)
			{
				// Raising the nets keeps the fish line up a while after, but only while still aboard: nets
				// dropping out of the water as the player steps off or hops is not them being raised.
				fishActivityMillis = System.currentTimeMillis();
			}
			netsLowered = lowered;
		}
	}

	private void readNetMenu()
	{
		Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		Widget[] lines = options == null ? null : options.getDynamicChildren();
		if (lines == null || lines.length == 0 || lines[0].getText() == null)
		{
			return;
		}

		Matcher menu = NET_MENU.matcher(Text.removeTags(lines[0].getText()));
		int fish = menu.find() ? number(menu.group(1)) : -1;
		if (fish >= 0)
		{
			setFish(Math.min(NET_CAPACITY, fish));
		}
	}

	private void countInventory(ItemContainer inventory)
	{
		int filled = 0;
		int plain = 0;
		int fine = 0;
		for (Item item : inventory == null ? new Item[0] : inventory.getItems())
		{
			if (item.getId() >= 0)
			{
				filled++;
			}
			if (item.getId() == ItemID.BRUT_FISH_CUTS)
			{
				plain += item.getQuantity();
			}
			else if (item.getId() == ItemID.SAILING_FINE_FISH_OFFCUTS)
			{
				fine += item.getQuantity();
			}
		}

		// Fish don't stack, so the slots that filled are the fish that arrived.
		inventoryAdded = inventoryFilled < 0 ? -1 : Math.max(0, filled - inventoryFilled);
		inventoryFilled = filled;
		inventoryTick = client.getTickCount();
		countDeposits(plain, fine);
		if (takePending)
		{
			takePending = false;
			takeFromNets(inventoryAdded);
		}
	}

	/**
	 * Adds offcuts that just left the inventory aboard to the hold's count. The hold is only sent while its
	 * screen is open, so offcuts put in just as it is shut never show up there; the inventory is always
	 * sent. When the hold's own contents came the same tick they already include the deposit, and win. Only
	 * counted while the hold's screen is open or just shut, so offcuts dropped any other time aren't.
	 */
	private void countDeposits(int plain, int fine)
	{
		if (fineBait >= 0 && inventoryFine >= 0 && holdTick != client.getTickCount()
			&& (holdOpen || holdClosedTick >= 0 && client.getTickCount() - holdClosedTick <= DEPOSIT_TICKS)
			&& (plain < inventoryPlain || fine < inventoryFine)
			&& client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) == 1)
		{
			plainBait += Math.max(0, inventoryPlain - plain);
			fineBait += Math.max(0, inventoryFine - fine);
			if (plainBait + fineBait > 0)
			{
				ranOutOfBait = false;
			}
			depositTick = client.getTickCount();
			baitedLabel = baitLabel();
		}
		inventoryPlain = plain;
		inventoryFine = fine;
	}

	private void takeFromNets(int taken)
	{
		setFish(taken < 0 || fishInNets < 0 ? -1 : Math.max(0, fishInNets - taken));
	}

	private void setFish(int fish)
	{
		fishInNets = fish;
		fishLabel = fishLabel(fish);
	}

	/**
	 * Sets the count after the nets were just emptied or found empty, which keeps the fish line up for a
	 * while even though there is nothing in them. Anything that leaves fish in the nets, or an unknown count,
	 * shows the line anyway, so it goes through setFish.
	 */
	private void noteFish(int fish)
	{
		setFish(fish);
		fishActivityMillis = System.currentTimeMillis();
	}

	/**
	 * Sets the count without anything having been done with the nets, for logging in, stepping off the boat
	 * or the plugin starting, and forgets any recent activity so the fish line doesn't come up by itself.
	 */
	private void resetFish(int fish)
	{
		setFish(fish);
		fishActivityMillis = -1;
	}

	/**
	 * Whether the fish line is worth showing: whenever there are fish in the nets or the count is not known,
	 * whenever a net is lowered, since that is trawling under way, and otherwise for a minute after the nets
	 * were last emptied, found empty or raised.
	 */
	boolean fishLineWanted(long nowMillis)
	{
		return fishInNets != 0 || netsLowered
			|| (fishActivityMillis >= 0 && nowMillis - fishActivityMillis <= FISH_LINE_LINGER_MILLIS);
	}

	private static String fishLabel(int fish)
	{
		if (fish >= NET_CAPACITY)
		{
			return "Nets full";
		}
		return "Fish: " + (fish < 0 ? "?" : Integer.toString(fish));
	}

	/**
	 * A count as the game writes it, in figures or in words, or -1 for one it isn't known to write.
	 */
	private static int number(String written)
	{
		if (!written.isEmpty() && written.length() < 5 && written.chars().allMatch(Character::isDigit))
		{
			return Integer.parseInt(written);
		}
		return NUMBER_WORDS.getOrDefault(written.toLowerCase(), -1);
	}

	/**
	 * How much bait is left that the nearest shoal takes, or -1 when that is not known. A shoal that takes
	 * either kind counts both together; every other shoal counts only the fine offcuts.
	 */
	private int baitLeft()
	{
		if (fineBait < 0 || nearestShoal == null)
		{
			return -1;
		}
		return takesPlainOffcuts(nearestShoal) ? plainBait + fineBait : fineBait;
	}

	/**
	 * A crewmate on the chum station found no offcuts in the hold, so the nearest shoal isn't baited and the
	 * hold has none of the offcuts it takes. On a shoal that takes plain offcuts that is both kinds; on one
	 * that takes only fine, the fine count is known to be empty, but the plain count is left alone while it
	 * isn't known, since the two are only ever known together.
	 */
	private void noBait()
	{
		baitFailedTick = client.getTickCount();
		baitedShoal = null;
		baited = false;
		// Offcuts counted into the hold this same tick went in after the attempt that found none.
		boolean justDeposited = depositTick == client.getTickCount();
		if (!justDeposited && nearestShoal != null && takesPlainOffcuts(nearestShoal))
		{
			plainBait = 0;
			fineBait = 0;
		}
		else if (!justDeposited && plainBait >= 0)
		{
			fineBait = 0;
		}
		ranOutOfBait = baitLeft() == 0;
		baitedLabel = baitLabel();
	}

	/**
	 * Takes one offcut off the count for a bait just laid on the nearest shoal. A shoal that takes plain
	 * offcuts takes fine ones too, but uses the plain first, so fine ones only go once the plain have run out.
	 */
	private void useBait()
	{
		if (fineBait < 0 || nearestShoal == null)
		{
			return;
		}

		if (takesPlainOffcuts(nearestShoal) && plainBait > 0)
		{
			plainBait--;
		}
		else if (fineBait > 0)
		{
			fineBait--;
		}
		if (baitLeft() == 0)
		{
			ranOutOfBait = true;
		}
	}

	/**
	 * What routes.json knows about the kind of shoal this is, or null for one it has no entry for.
	 */
	private RouteData.Species speciesOf(Shoal shoal)
	{
		String name = SPECIES_BY_CLICKBOX.get(shoal.getClickbox());
		return speciesByName.get(name != null ? name : MIXED_BY_CLICKBOX.get(shoal.getClickbox()));
	}

	/**
	 * Whether plain fish offcuts bait this shoal as well as fine ones. A kind with no entry is taken to
	 * need fine ones, which every kind of shoal accepts.
	 */
	private boolean takesPlainOffcuts(Shoal shoal)
	{
		RouteData.Species species = speciesOf(shoal);
		return species != null && "any".equals(species.bait);
	}

	private String baitLabel()
	{
		return label(baitLeft(), isOutOfBait());
	}

	private static String label(int left, boolean outOfBait)
	{
		if (outOfBait)
		{
			return "No bait";
		}
		// Counted in full, but a hold stocked with thousands only needs to say it has plenty.
		return "Baited (" + (left < 0 ? "?" : left > MAX_BAIT_SHOWN ? MAX_BAIT_SHOWN + "+" : Integer.toString(left)) + " left)";
	}

	/**
	 * Works out whether the nearest shoal is baited. The game records which step of its route a shoal was
	 * last baited at rather than how long the bait lasts, and never says when it wears off: the number
	 * stays where it is long after a bait, while the shoal swims on. Tested in game, a bait lasts until the
	 * shoal next arrives at a stop. Baited at a stop, it lasts the rest of that stop and the swim to the
	 * next; baited on the way, until the stop it is heading for. So a new number is a fresh bait on the
	 * nearest shoal, which stays baited until that shoal next arrives somewhere.
	 */
	private boolean stillBaited()
	{
		int step = client.getVarbitValue(VarbitID.SAILING_PLAYER_TRAWLING_SHOAL_BAITED_STEP);
		// A change on the tick a crewmate found nothing to bait with, or the one after, is that failed attempt.
		int tick = client.getTickCount();
		boolean laid = baitedStep != null && baitedStep != step
			&& (tick - baitFailedTick > 1 || tick - baitAnnouncedTick <= 1);
		baitedStep = step;

		if (laid && nearestShoal != null)
		{
			baitedShoal = nearestShoal;
			baitedWasStopped = nearestShoal.stopped();
			ticksSinceBait = 0;
			if (holdTick != tick)
			{
				// The hold's contents sent on the same tick as the bait already have that offcut taken out.
				useBait();
			}
		}
		else if (baitedShoal != null)
		{
			ticksSinceBait++;
			boolean stopped = baitedShoal.stopped();
			// An arrival right after the bait is the shoal settling in as it was baited, which the bait
			// outlasts. Any later one is the next stop, where it runs out.
			if (stopped && !baitedWasStopped && ticksSinceBait > ARRIVAL_GRACE_TICKS)
			{
				baitedShoal = null;
			}
			baitedWasStopped = stopped;
		}
		return nearestShoal != null && nearestShoal == baitedShoal;
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
			return true;
		}

		return netsFitted;
	}

	/**
	 * The player's own boat while they are stood on it, or null. Everyone aboard stands in the boat's own
	 * world view rather than the one the sea is in, and the boat is looked up straight from that view's id
	 * rather than searched for among every world entity in the scene. What comes back is checked to be
	 * that same view and the player's own boat, so standing on someone else's counts as not aboard.
	 */
	private WorldEntity boardedBoat()
	{
		Player player = client.getLocalPlayer();
		WorldView standing = player == null ? null : player.getWorldView();
		WorldView top = client.getTopLevelWorldView();
		if (standing == null || top == null || standing.isTopLevel() || standing.getId() < 0)
		{
			return null;
		}

		WorldEntity boat = top.worldEntities().byIndex(standing.getId());
		WorldView deck = boat == null ? null : boat.getWorldView();
		return deck != null && deck.getId() == standing.getId()
			&& boat.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER ? boat : null;
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
			double[] at = isShown(shoal) ? shoal.position(client) : null;
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
		return SPECIES_BY_CLICKBOX.containsKey(id) || MIXED_BY_CLICKBOX.containsKey(id);
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
		boat = null;
		boatPlace = null;
		nearestShoal = null;
		nearestRoute = null;
		contender = null;
		contenderTicks = 0;
		baitedStep = null;
		baited = false;
		baitedShoal = null;
		netsAtDepth = false;
		leavingShoal = null;
	}

	@Provides
	TrawlingPlusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TrawlingPlusConfig.class);
	}
}
