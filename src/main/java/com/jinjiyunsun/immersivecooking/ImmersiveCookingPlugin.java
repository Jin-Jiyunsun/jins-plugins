package com.jinjiyunsun.immersivecooking;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.AnimationController;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Immersive Cooking",
	description = "Shows the food you're cooking sitting on the range while it cooks"
)
public class ImmersiveCookingPlugin extends Plugin
{
	// When the clicked object can't be found: this far up, and east of the clicked tile.
	private static final int HOVER_HEIGHT = 120;
	private static final int EAST_OFFSET = 25;
	// Above a bare hob. One with a pan or pot uses its top instead (RANGE's pan handle puts the fish at 120).
	private static final int BARE_HOB_CLEARANCE = 4;
	// Above the top flat surface of something without a hob, like a fire's logs.
	private static final int SURFACE_CLEARANCE = 8;
	// Smaller than this, the top "surface" is only slivers (flame tips); a normal fire's logs are about 4400.
	private static final float MIN_SURFACE_AREA = 1000;
	// Used then instead: about where food sits on a normal fire (logs at 12, plus the clearance).
	private static final int NO_SURFACE_HEIGHT = 20;
	// A face counts as facing up if its normal is within about 25 degrees of straight up.
	private static final float UP_FACING = 0.9f;

	// 128 is 100%.
	private static final int MODEL_SCALE = 96;

	private static final int RESULT_DISPLAY_TICKS = 2;
	private static final int GAP_TICKS = 1;

	// How close counts as "at the range", in local units (128 per tile).
	private static final int NEAR_RANGE_DISTANCE = 3 * 128;

	private static final int SPACE_SLOT = -2;

	private static final int FADE_DURATION_MS = 150;
	// Shrinks to nearly nothing rather than a zero-size mesh.
	private static final int MIN_SCALE_PERCENT = 4;

	private static final Map<Integer, Integer> COOKED_BY_RAW = Map.ofEntries(
		Map.entry(ItemID.RAW_SHRIMP, ItemID.SHRIMP), Map.entry(ItemID.RAW_ANCHOVIES, ItemID.ANCHOVIES),
		Map.entry(ItemID.RAW_SARDINE, ItemID.SARDINE), Map.entry(ItemID.RAW_SALMON, ItemID.SALMON),
		Map.entry(ItemID.RAW_TROUT, ItemID.TROUT), Map.entry(ItemID.RAW_GIANT_CARP, ItemID.GIANT_CARP),
		Map.entry(ItemID.RAW_COD, ItemID.COD), Map.entry(ItemID.RAW_HERRING, ItemID.HERRING),
		Map.entry(ItemID.RAW_PIKE, ItemID.PIKE), Map.entry(ItemID.RAW_MACKEREL, ItemID.MACKEREL),
		Map.entry(ItemID.RAW_TUNA, ItemID.TUNA), Map.entry(ItemID.RAW_BASS, ItemID.BASS),
		Map.entry(ItemID.RAW_SWORDFISH, ItemID.SWORDFISH), Map.entry(ItemID.RAW_LOBSTER, ItemID.LOBSTER),
		Map.entry(ItemID.RAW_SHARK, ItemID.SHARK), Map.entry(ItemID.RAW_MANTARAY, ItemID.MANTARAY),
		Map.entry(ItemID.RAW_SEATURTLE, ItemID.SEATURTLE), Map.entry(ItemID.RAW_LAVA_EEL, ItemID.LAVA_EEL),
		Map.entry(ItemID.RAW_CAVE_EEL, ItemID.CAVE_EEL), Map.entry(ItemID.RAW_MONKFISH, ItemID.MONKFISH),
		Map.entry(ItemID.RAW_DARK_CRAB, ItemID.DARK_CRAB), Map.entry(ItemID.RAW_ANGLERFISH, ItemID.ANGLERFISH),
		Map.entry(ItemID.RAW_GUPPY, ItemID.GUPPY), Map.entry(ItemID.RAW_CAVEFISH, ItemID.CAVEFISH),
		Map.entry(ItemID.RAW_TETRA, ItemID.TETRA), Map.entry(ItemID.RAW_CATFISH, ItemID.CATFISH),
		Map.entry(ItemID.RAW_SWORDTIP_SQUID, ItemID.SWORDTIP_SQUID), Map.entry(ItemID.RAW_JUMBO_SQUID, ItemID.JUMBO_SQUID),
		Map.entry(ItemID.RAW_GIANT_KRILL, ItemID.GIANT_KRILL), Map.entry(ItemID.RAW_HADDOCK, ItemID.HADDOCK),
		Map.entry(ItemID.RAW_YELLOWFIN, ItemID.YELLOWFIN), Map.entry(ItemID.RAW_HALIBUT, ItemID.HALIBUT),
		Map.entry(ItemID.RAW_BLUEFIN, ItemID.BLUEFIN), Map.entry(ItemID.RAW_MARLIN, ItemID.MARLIN),
		Map.entry(ItemID.TBWT_RAW_KARAMBWAN, ItemID.TBWT_COOKED_KARAMBWAN),
		Map.entry(ItemID.HUNTING_RAW_FISH_SPECIAL, ItemID.HUNTING_FISH_SPECIAL),
		// Meats.
		Map.entry(ItemID.RAW_BEEF, ItemID.COOKED_MEAT), Map.entry(ItemID.RAW_RAT_MEAT, ItemID.COOKED_MEAT),
		Map.entry(ItemID.RAW_BEAR_MEAT, ItemID.COOKED_MEAT), Map.entry(ItemID.RAW_CHICKEN, ItemID.COOKED_CHICKEN),
		Map.entry(ItemID.RAW_RABBIT, ItemID.COOKED_RABBIT), Map.entry(ItemID.RAW_UGTHANKI_MEAT, ItemID.COOKED_UGTHANKI_MEAT),
		Map.entry(ItemID.RAW_TBONE_STEAK, ItemID.TBONE_STEAK), Map.entry(ItemID.RAW_MYSTERY_MEAT, ItemID.COOKED_MYSTERY_MEAT),
		Map.entry(ItemID.RAW_LIZARD, ItemID.COOKED_LIZARD), Map.entry(ItemID.YAK_MEAT_RAW, ItemID.COOKED_MEAT),
		Map.entry(ItemID.RAW_BOAR_MEAT, ItemID.COOKED_MEAT), Map.entry(ItemID.RAW_IMPALER_MEAT, ItemID.COOKED_MEAT),
		Map.entry(ItemID._100_JUBBLY_MEAT_RAW, ItemID._100_JUBBLY_MEAT_COOKED),
		Map.entry(ItemID.MORT_SLIMEY_EEL, ItemID.MORT_SLIMEY_EEL_COOKED), Map.entry(ItemID.BREAM_FISH_RAW, ItemID.BREAM_FISH_COOKED),
		Map.entry(ItemID.RAW_RED_CRAB_MEAT, ItemID.RED_CRAB_MEAT), Map.entry(ItemID.RAW_BLUE_CRAB_MEAT, ItemID.BLUE_CRAB_MEAT),
		Map.entry(ItemID.RAW_RAINBOW_CRAB_MEAT, ItemID.RAINBOW_CRAB_MEAT),
		// Varlamore hunter meats.
		Map.entry(ItemID.HUNTINGBEAST_WILD_MEAT, ItemID.WILDKEBBIT_COOKED), Map.entry(ItemID.HUNTINGBEAST_BARBED_MEAT, ItemID.BARBKEBBIT_COOKED),
		Map.entry(ItemID.HUNTINGBEAST_SPEEDY2_MEAT, ItemID.DASHINGKEBBIT_COOKED), Map.entry(ItemID.HUNTING_FENNECFOX_MEAT, ItemID.FENNECFOX_COOKED),
		Map.entry(ItemID.HUNTING_ANTELOPESUN_MEAT, ItemID.ANTELOPESUN_COOKED), Map.entry(ItemID.HUNTING_ANTELOPEMOON_MEAT, ItemID.ANTELOPEMOON_COOKED),
		Map.entry(ItemID.HUNTING_GRAAHK_MEAT, ItemID.GRAAHK_COOKED), Map.entry(ItemID.HUNTING_LARUPIA_MEAT, ItemID.LARUPIA_COOKED),
		Map.entry(ItemID.HUNTING_KYATT_MEAT, ItemID.KYATT_COOKED)
	);

	// Burnt models. Foods missing here (e.g. moss lizard, bream) can't burn.
	private static final Map<Integer, Integer> BURNT_BY_RAW = Map.ofEntries(
		Map.entry(ItemID.RAW_SWORDFISH, ItemID.BURNT_SWORDFISH), Map.entry(ItemID.RAW_LOBSTER, ItemID.BURNT_LOBSTER),
		Map.entry(ItemID.RAW_SHARK, ItemID.BURNT_SHARK), Map.entry(ItemID.RAW_MANTARAY, ItemID.BURNT_MANTARAY),
		Map.entry(ItemID.RAW_SEATURTLE, ItemID.BURNT_SEATURTLE), Map.entry(ItemID.RAW_CAVE_EEL, ItemID.BURNT_CAVE_EEL),
		Map.entry(ItemID.RAW_MONKFISH, ItemID.BURNT_MONKFISH), Map.entry(ItemID.RAW_SHRIMP, ItemID.BURNT_SHRIMP),
		Map.entry(ItemID.RAW_DARK_CRAB, ItemID.BURNT_DARK_CRAB), Map.entry(ItemID.RAW_ANGLERFISH, ItemID.BURNT_ANGLERFISH),
		Map.entry(ItemID.RAW_SWORDTIP_SQUID, ItemID.BURNT_SWORDTIP_SQUID), Map.entry(ItemID.RAW_JUMBO_SQUID, ItemID.BURNT_JUMBO_SQUID),
		Map.entry(ItemID.RAW_GIANT_KRILL, ItemID.BURNT_GIANT_KRILL), Map.entry(ItemID.RAW_HADDOCK, ItemID.BURNT_HADDOCK),
		Map.entry(ItemID.RAW_YELLOWFIN, ItemID.BURNT_YELLOWFIN), Map.entry(ItemID.RAW_HALIBUT, ItemID.BURNT_HALIBUT),
		Map.entry(ItemID.RAW_BLUEFIN, ItemID.BURNT_BLUEFIN), Map.entry(ItemID.RAW_MARLIN, ItemID.BURNT_MARLIN),
		Map.entry(ItemID.TBWT_RAW_KARAMBWAN, ItemID.TBWT_BURNT_KARAMBWAN),
		Map.entry(ItemID.HUNTING_RAW_FISH_SPECIAL, ItemID.HUNTING_RAW_FISH_SPECIAL_BURNT),
		Map.entry(ItemID.RAW_BEEF, ItemID.BURNT_MEAT), Map.entry(ItemID.RAW_RAT_MEAT, ItemID.BURNT_MEAT),
		Map.entry(ItemID.RAW_BEAR_MEAT, ItemID.BURNT_MEAT), Map.entry(ItemID.RAW_CHICKEN, ItemID.BURNT_CHICKEN),
		Map.entry(ItemID.RAW_TBONE_STEAK, ItemID.TBONE_STEAK_BURNED),
		// Officially the burnt skewered rabbit, but it's the right look.
		Map.entry(ItemID.RAW_RABBIT, ItemID.SPIT_BURNED_RABBIT_MEAT),
		Map.entry(ItemID.RAW_UGTHANKI_MEAT, ItemID.BURNT_MEAT), Map.entry(ItemID.YAK_MEAT_RAW, ItemID.BURNT_MEAT),
		Map.entry(ItemID.RAW_BOAR_MEAT, ItemID.BURNT_MEAT), Map.entry(ItemID.RAW_IMPALER_MEAT, ItemID.BURNT_MEAT),
		// The "Burnt fish" items, one per group of fish.
		Map.entry(ItemID.RAW_ANCHOVIES, ItemID.BURNTFISH1), Map.entry(ItemID.RAW_SARDINE, ItemID.BURNTFISH5),
		Map.entry(ItemID.RAW_HERRING, ItemID.BURNTFISH3), Map.entry(ItemID.RAW_MACKEREL, ItemID.BURNTFISH3),
		Map.entry(ItemID.RAW_TROUT, ItemID.BURNTFISH2), Map.entry(ItemID.RAW_SALMON, ItemID.BURNTFISH2),
		Map.entry(ItemID.RAW_PIKE, ItemID.BURNTFISH2), Map.entry(ItemID.RAW_COD, ItemID.BURNTFISH2),
		Map.entry(ItemID.RAW_GIANT_CARP, ItemID.BURNTFISH2), Map.entry(ItemID.RAW_TUNA, ItemID.BURNTFISH4),
		Map.entry(ItemID.RAW_BASS, ItemID.BURNTFISH4),
		Map.entry(ItemID._100_JUBBLY_MEAT_RAW, ItemID._100_JUBBLY_MEAT_BURNED), Map.entry(ItemID.MORT_SLIMEY_EEL, ItemID.BURNT_EEL),
		Map.entry(ItemID.RAW_RED_CRAB_MEAT, ItemID.BURNT_CRAB_MEAT), Map.entry(ItemID.RAW_BLUE_CRAB_MEAT, ItemID.BURNT_CRAB_MEAT),
		Map.entry(ItemID.RAW_RAINBOW_CRAB_MEAT, ItemID.BURNT_RAINBOW_CRAB_MEAT),
		Map.entry(ItemID.HUNTINGBEAST_WILD_MEAT, ItemID.BURNT_KEBBIT), Map.entry(ItemID.HUNTINGBEAST_BARBED_MEAT, ItemID.BURNT_KEBBIT),
		Map.entry(ItemID.HUNTINGBEAST_SPEEDY2_MEAT, ItemID.BURNT_KEBBIT), Map.entry(ItemID.HUNTING_FENNECFOX_MEAT, ItemID.BURNT_FOXMEAT),
		Map.entry(ItemID.HUNTING_ANTELOPESUN_MEAT, ItemID.BURNT_ANTELOPE), Map.entry(ItemID.HUNTING_ANTELOPEMOON_MEAT, ItemID.BURNT_ANTELOPE),
		Map.entry(ItemID.HUNTING_GRAAHK_MEAT, ItemID.BURNT_LARGEBEAST), Map.entry(ItemID.HUNTING_LARUPIA_MEAT, ItemID.BURNT_LARGEBEAST),
		Map.entry(ItemID.HUNTING_KYATT_MEAT, ItemID.BURNT_LARGEBEAST)
	);

	private static final Set<Integer> RAW_FOOD_ITEM_IDS = COOKED_BY_RAW.keySet();

	// Things food can be used on that have neither a "Cook" option nor "fire" in their name.
	private static final Set<Integer> OTHER_COOKING_OBJECTS = Set.of(ObjectID.TZHAAR_SULPHAR_VENT);

	private static final Set<MenuAction> GAME_OBJECT_OPTIONS = EnumSet.of(
		MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION,
		MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION
	);

	// The dialog's food buttons, in hotkey order (1 = A).
	private static final int[] DIALOG_SLOTS = {
		InterfaceID.Skillmulti.A, InterfaceID.Skillmulti.B, InterfaceID.Skillmulti.C, InterfaceID.Skillmulti.D,
		InterfaceID.Skillmulti.E, InterfaceID.Skillmulti.F, InterfaceID.Skillmulti.G, InterfaceID.Skillmulti.H,
		InterfaceID.Skillmulti.I, InterfaceID.Skillmulti.J, InterfaceID.Skillmulti.K, InterfaceID.Skillmulti.L,
		InterfaceID.Skillmulti.M, InterfaceID.Skillmulti.N, InterfaceID.Skillmulti.O, InterfaceID.Skillmulti.P,
		InterfaceID.Skillmulti.Q, InterfaceID.Skillmulti.R
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	private RuneLiteObject foodObject;

	// The raw food being cooked, and the range it's on. Location can be set before the food is known.
	private int pendingCookItemId = -1;
	private LocalPoint pendingCookLocation;
	// Kept after a batch ends, as a fallback if a cook message arrives with no session.
	private LocalPoint lastCookLocation;
	// How high the food floats at the cook location; kept with lastCookLocation.
	private int cookHoverHeight = HOVER_HEIGHT;
	// Food was used on the range from a distance; it shows once the player gets there.
	private boolean waitingForArrival = false;

	// Item shown on foodObject, or -1 if nothing (or it's shrinking away).
	private int displayedItemId = -1;

	private int hideAtTick = -1;
	private int showAtTick = -1;
	private int pendingShowItemId = -1;
	// A result reveal gets its own hide timer; a raw reveal stays until the next result.
	private boolean pendingShowIsResult = false;

	private int hoverPreviewItemId = -1;
	// Food to grow in once the current shrink-out finishes.
	private int pendingHoverSwapItemId = -1;

	// Catches the quantity dialog being closed with Escape before cooking starts.
	private boolean dialogSeenOpen = false;
	private boolean cookingAnimationSeen = false;
	private int dialogCloseCheckTick = -1;

	// Animation can blip off for a frame mid-batch, so a stop is confirmed a tick later.
	private int clearCheckTick = -1;

	// Lowercase raw, unprefixed and cooked names -> raw item id.
	private Map<String, List<Integer>> rawFoodByName;

	// The same chat line can arrive twice on one tick.
	private String lastChatMessage = "";
	private int lastChatMessageTick = -1;

	// SpotanimID.FORESTRY_CAMPFIRE_SMOKE_01's model and animation; model ids have no gamevals.
	private static final int SMOKE_MODEL = 49898;
	private static final int SMOKE_AMBIENT = 20;
	private static final int SMOKE_CONTRAST = 20;
	// The smoke's own grey; its embers (any more saturated face) are recoloured to it.
	private static final int SMOKE_GREY_HUE = 5;
	private static final int SMOKE_GREY_SATURATION = 1;

	private RuneLiteObject smokeObject;
	// Whether a deferred result reveal is a burn, for the smoke.
	private boolean pendingShowIsBurnt = false;

	private enum FadeState { NONE, IN, OUT }
	private FadeState fadeState = FadeState.NONE;
	private long fadeStartMs;
	private int fadeItemId = -1;

	// Only watches key presses; never consumes or sends input.
	private final KeyListener dialogHotkeyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			int slot = slotForKey(e.getKeyCode());
			if (slot != -1)
			{
				clientThread.invoke(() -> onDialogHotkey(slot));
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	@Override
	protected void startUp()
	{
		foodObject = client.createRuneLiteObject();
		smokeObject = client.createRuneLiteObject();
		keyManager.registerKeyListener(dialogHotkeyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(dialogHotkeyListener);
		clearCooking();
		lastCookLocation = null;
		fadeState = FadeState.NONE;
		foodObject.setActive(false);
		foodObject = null;
		smokeObject.setActive(false);
		smokeObject = null;
	}

	private void setCookLocation(int sceneX, int sceneY, int objectId)
	{
		TileObject object = findTileObject(sceneX, sceneY, objectId);
		Model model = object != null ? modelOf(object) : null;
		float[] spot = model != null ? findHob(model) : null;
		boolean hob = spot != null;
		if (model != null && spot == null)
		{
			spot = findTopSurface(model);
		}
		if (spot != null)
		{
			pendingCookLocation = object.getLocalLocation().dx(Math.round(spot[0])).dy(Math.round(spot[1]));
			cookHoverHeight = Math.round(spot[2]);
		}
		else
		{
			pendingCookLocation = LocalPoint.fromScene(sceneX, sceneY, client.getTopLevelWorldView()).dx(EAST_OFFSET);
			cookHoverHeight = HOVER_HEIGHT;
		}
		log.debug("Cook spot: object={}, found={}, offset={}, location={}, height={}", objectId,
			spot == null ? "nothing" : hob ? "hob" : "surface",
			spot == null ? "-" : Math.round(spot[0]) + "," + Math.round(spot[1]), pendingCookLocation, cookHoverHeight);
		lastCookLocation = pendingCookLocation;
	}

	// A game object, or a floor decoration (like a sulphur vent), on the clicked tile.
	private TileObject findTileObject(int sceneX, int sceneY, int objectId)
	{
		Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
		int plane = client.getTopLevelWorldView().getPlane();
		if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length || sceneY >= tiles[plane][sceneX].length)
		{
			return null;
		}
		Tile tile = tiles[plane][sceneX][sceneY];
		if (tile == null)
		{
			return null;
		}
		for (GameObject object : tile.getGameObjects())
		{
			if (object != null && object.getId() == objectId)
			{
				return object;
			}
		}
		GroundObject ground = tile.getGroundObject();
		return ground != null && ground.getId() == objectId ? ground : null;
	}

	// The model as drawn in the scene, already turned the way the object faces. Animated objects (fires)
	// give their current frame.
	private static Model modelOf(TileObject object)
	{
		Renderable renderable = object instanceof GameObject ? ((GameObject) object).getRenderable()
			: object instanceof GroundObject ? ((GroundObject) object).getRenderable() : null;
		return renderable instanceof Model ? (Model) renderable : renderable != null ? renderable.getModel() : null;
	}

	// A face's area if it faces up (y points down), otherwise 0.
	private static float upArea(Model model, int face)
	{
		float[] x = model.getVerticesX();
		float[] y = model.getVerticesY();
		float[] z = model.getVerticesZ();
		int a = model.getFaceIndices1()[face];
		int b = model.getFaceIndices2()[face];
		int c = model.getFaceIndices3()[face];
		float ux = x[b] - x[a], uy = y[b] - y[a], uz = z[b] - z[a];
		float vx = x[c] - x[a], vy = y[c] - y[a], vz = z[c] - z[a];
		float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		return length > 0 && -ny / length >= UP_FACING ? length / 2 : 0;
	}

	private static int heightBucket(Model model, int face)
	{
		float[] y = model.getVerticesY();
		return Math.round((y[model.getFaceIndices1()[face]] + y[model.getFaceIndices2()[face]] + y[model.getFaceIndices3()[face]]) / 3 / 4) * 4;
	}

	// Ranges share red, upward-facing hob faces. Returns where the food sits, relative to the object's
	// middle, and how high: {east, north, height}, or null without a hob. The food goes over anything on the
	// hob (a pan or pot), level with its top, or a little above a bare hob.
	private static float[] findHob(Model model)
	{
		short[] unlit = model.getUnlitFaceColors();
		int[] lit = model.getFaceColors1();
		float[] x = model.getVerticesX();
		float[] y = model.getVerticesY();
		float[] z = model.getVerticesZ();
		int[] a = model.getFaceIndices1();
		int[] b = model.getFaceIndices2();
		int[] c = model.getFaceIndices3();
		int faces = model.getFaceCount();

		// The hob's height: the height with the most red upward area.
		float[] redArea = new float[faces];
		Map<Integer, Float> redAreaByHeight = new HashMap<>();
		for (int f = 0; f < faces; f++)
		{
			int colour = unlit != null ? unlit[f] & 0xffff : lit[f];
			int hue = colour >> 10 & 63;
			if (colour < 0 || !(hue <= 2 || hue >= 62) || (colour >> 7 & 7) < 4)
			{
				continue;
			}
			redArea[f] = upArea(model, f);
			if (redArea[f] > 0)
			{
				redAreaByHeight.merge(heightBucket(model, f), redArea[f], Float::sum);
			}
		}
		if (redAreaByHeight.isEmpty())
		{
			return null;
		}
		int hobBucket = Collections.max(redAreaByHeight.entrySet(), Map.Entry.comparingByValue()).getKey();

		// The hob's middle, height and extent, from the red faces at that height only.
		float total = 0, sumX = 0, sumZ = 0, hobY = 0;
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int f = 0; f < faces; f++)
		{
			if (redArea[f] == 0 || Math.abs(heightBucket(model, f) - hobBucket) > 4)
			{
				continue;
			}
			total += redArea[f];
			for (int v : new int[]{a[f], b[f], c[f]})
			{
				sumX += redArea[f] * x[v] / 3;
				sumZ += redArea[f] * z[v] / 3;
				hobY += redArea[f] * y[v] / 3;
				minX = Math.min(minX, x[v]);
				maxX = Math.max(maxX, x[v]);
				minZ = Math.min(minZ, z[v]);
				maxZ = Math.max(maxZ, z[v]);
			}
		}
		hobY /= total;

		// Anything resting on it. Its top: any face centred over the hob and above it (a grate's bars reach past
		// the coals, a pan handle counts). Its middle: the upward faces wholly within the hob's extent.
		float onTotal = 0, onX = 0, onZ = 0, onTop = hobY;
		for (int f = 0; f < faces; f++)
		{
			float faceTop = Math.min(y[a[f]], Math.min(y[b[f]], y[c[f]]));
			float centreX = (x[a[f]] + x[b[f]] + x[c[f]]) / 3;
			float centreZ = (z[a[f]] + z[b[f]] + z[c[f]]) / 3;
			if (faceTop < hobY - 2 && centreX >= minX && centreX <= maxX && centreZ >= minZ && centreZ <= maxZ)
			{
				onTop = Math.min(onTop, faceTop);
			}

			boolean onHob = true;
			for (int v : new int[]{a[f], b[f], c[f]})
			{
				onHob &= x[v] >= minX - 4 && x[v] <= maxX + 4 && z[v] >= minZ - 4 && z[v] <= maxZ + 4 && y[v] < hobY - 2;
			}
			if (!onHob)
			{
				continue;
			}
			float faceArea = upArea(model, f);
			onTotal += faceArea;
			onX += faceArea * (x[a[f]] + x[b[f]] + x[c[f]]) / 3;
			onZ += faceArea * (z[a[f]] + z[b[f]] + z[c[f]]) / 3;
		}
		float height = Math.max(-hobY + BARE_HOB_CLEARANCE, -onTop);
		return onTotal > 0
			? new float[]{onX / onTotal, onZ / onTotal, height}
			: new float[]{sumX / total, sumZ / total, height};
	}

	private static float middle(float[] values, int count)
	{
		float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			low = Math.min(low, values[i]);
			high = Math.max(high, values[i]);
		}
		return (low + high) / 2;
	}

	// Without a hob (fires, plain ranges): over the highest flat surface with at least a quarter of the
	// largest one's area, so a small chimney top can't win. Returns {east, north, height}, or null.
	private static float[] findTopSurface(Model model)
	{
		int faces = model.getFaceCount();
		float[] area = new float[faces];
		Map<Integer, Float> areaByHeight = new HashMap<>();
		for (int f = 0; f < faces; f++)
		{
			area[f] = upArea(model, f);
			if (area[f] > 0)
			{
				areaByHeight.merge(heightBucket(model, f), area[f], Float::sum);
			}
		}
		float largest = areaByHeight.isEmpty() ? 0 : Collections.max(areaByHeight.values());
		if (largest < MIN_SURFACE_AREA)
		{
			// Only slivers, like flame tips on a fire with no logs: centre it, at a normal fire's height.
			return new float[]{middle(model.getVerticesX(), model.getVerticesCount()),
				middle(model.getVerticesZ(), model.getVerticesCount()), NO_SURFACE_HEIGHT};
		}
		int top = Integer.MAX_VALUE;
		for (Map.Entry<Integer, Float> entry : areaByHeight.entrySet())
		{
			if (entry.getValue() >= largest / 4 && entry.getKey() < top)
			{
				top = entry.getKey();
			}
		}

		float[] x = model.getVerticesX();
		float[] z = model.getVerticesZ();
		int[] a = model.getFaceIndices1();
		int[] b = model.getFaceIndices2();
		int[] c = model.getFaceIndices3();
		float total = 0, sumX = 0, sumZ = 0;
		for (int f = 0; f < faces; f++)
		{
			if (area[f] > 0 && heightBucket(model, f) == top)
			{
				total += area[f];
				sumX += area[f] * (x[a[f]] + x[b[f]] + x[c[f]]) / 3;
				sumZ += area[f] * (z[a[f]] + z[b[f]] + z[c[f]]) / 3;
			}
		}
		return new float[]{sumX / total, sumZ / total, -top + SURFACE_CLEARANCE};
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();

		// Raw food used on the range.
		if (action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			Widget selectedWidget = client.getSelectedWidget();
			int itemId = selectedWidget != null ? selectedWidget.getItemId() : -1;
			if (!RAW_FOOD_ITEM_IDS.contains(itemId) || !isCookingObject(event.getId()))
			{
				return;
			}
			// Mid-batch the game keeps cooking the first food; chat messages resync if not.
			if (pendingCookItemId != -1 && pendingCookItemId != itemId)
			{
				return;
			}
			pendingCookItemId = itemId;
			setCookLocation(event.getParam0(), event.getParam1(), event.getId());
			if (isPlayerNear(pendingCookLocation))
			{
				showModel(itemId);
			}
			else
			{
				waitingForArrival = true;
			}
			return;
		}

		// Walking or interacting elsewhere before reaching the range cancels the cook.
		if (waitingForArrival && event.getWidget() == null && action != MenuAction.CANCEL)
		{
			log.debug("Clearing: left before reaching the range");
			clearCooking();
		}

		// "Cook" on the range: location known, food picked in the dialog.
		if (GAME_OBJECT_OPTIONS.contains(action) && "Cook".equalsIgnoreCase(event.getMenuOption()))
		{
			// A new session: the old batch's clean-up must not wipe this location later.
			if (pendingCookItemId != -1)
			{
				clearCooking();
			}
			setCookLocation(event.getParam0(), event.getParam1(), event.getId());
			hoverPreviewItemId = -1;
			log.debug("Cook clicked on range");
			return;
		}

		if (pendingCookItemId != -1 || pendingCookLocation == null)
		{
			return;
		}

		// A click in the dialog. Read the click's own text; the hover poll may lag a fast click.
		// Quantity buttons carry no food name, so fall back to the hovered one.
		int clickedItemId = parseFoodFromMenuText(event.getMenuOption(), event.getMenuTarget());
		int confirmedItemId = clickedItemId != -1 ? clickedItemId : hoverPreviewItemId;
		if (confirmedItemId != -1)
		{
			pendingCookItemId = confirmedItemId;
			log.debug("Confirmed via dialog click, itemId={}", pendingCookItemId);
			showModel(pendingCookItemId);
		}
	}

	// Ranges have a "Cook" option; fires have none, so they're matched by name. A few others are listed.
	private boolean isCookingObject(int objectId)
	{
		if (OTHER_COOKING_OBJECTS.contains(objectId))
		{
			return true;
		}
		ObjectComposition object = client.getObjectDefinition(objectId);
		if (object.getImpostorIds() != null && object.getImpostor() != null)
		{
			object = object.getImpostor();
		}

		String[] actions = object.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if ("Cook".equalsIgnoreCase(action))
				{
					return true;
				}
			}
		}

		String name = object.getName();
		boolean isFire = name != null && name.toLowerCase().contains("fire");
		log.debug("Use-on object: id={}, name={}, isFire={}", objectId, name, isFire);
		return isFire;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		updateFade();

		if (pendingCookLocation == null)
		{
			return;
		}

		if (pendingCookItemId != -1)
		{
			if (!cookingAnimationSeen)
			{
				trackDialogClose();
			}
			return;
		}

		updateHoverPreview();
	}

	private void trackDialogClose()
	{
		if (!isSkillmultiDialogClosed())
		{
			dialogSeenOpen = true;
			dialogCloseCheckTick = -1;
		}
		else if (dialogSeenOpen && dialogCloseCheckTick == -1)
		{
			// The widget can flicker for a frame, so confirm on the next tick.
			dialogCloseCheckTick = client.getTickCount() + 1;
		}
	}

	private void updateHoverPreview()
	{
		int hoveredItemId = findHoveredFood();
		if (hoveredItemId == hoverPreviewItemId)
		{
			return;
		}
		hoverPreviewItemId = hoveredItemId;

		if (hoveredItemId == -1)
		{
			pendingHoverSwapItemId = -1;
			hideModel();
		}
		else if (foodObject.isActive())
		{
			// Something is showing or still shrinking: shrink it fully, then grow this one in.
			pendingHoverSwapItemId = hoveredItemId;
			hideModel();
		}
		else
		{
			showModel(hoveredItemId);
		}
	}

	// 1-9 on the main row or numpad pick the Nth button; space picks the only one.
	private static int slotForKey(int keyCode)
	{
		if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9)
		{
			return keyCode - KeyEvent.VK_1;
		}
		if (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9)
		{
			return keyCode - KeyEvent.VK_NUMPAD1;
		}
		return keyCode == KeyEvent.VK_SPACE ? SPACE_SLOT : -1;
	}

	private void onDialogHotkey(int slot)
	{
		if (pendingCookLocation == null || pendingCookItemId != -1 || isSkillmultiDialogClosed())
		{
			return;
		}

		List<Integer> slotItemIds = readDialogSlots();
		log.debug("Dialog hotkey: slot={}, slots={}", slot, slotItemIds);
		if (slot == SPACE_SLOT)
		{
			if (slotItemIds.size() != 1)
			{
				return;
			}
			slot = 0;
		}
		if (slot >= slotItemIds.size() || slotItemIds.get(slot) == -1)
		{
			return;
		}

		pendingCookItemId = slotItemIds.get(slot);
		showModel(pendingCookItemId);
	}

	// Each visible button's food, read from its name (the same text the "Cook <food>" menu shows).
	// Unknown buttons (e.g. poison karambwan) stay as -1 so the hotkey numbering lines up.
	private List<Integer> readDialogSlots()
	{
		List<Integer> slotItemIds = new ArrayList<>();
		for (int componentId : DIALOG_SLOTS)
		{
			Widget slot = client.getWidget(componentId);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			slotItemIds.add(slot.getName() != null ? foodForLabel(slot.getName()) : -1);
		}
		return slotItemIds;
	}

	private boolean isSkillmultiDialogClosed()
	{
		Widget universe = client.getWidget(InterfaceID.Skillmulti.UNIVERSE);
		return universe == null || universe.isHidden();
	}

	// The dialog's food widgets have no item id, but the hovered "Cook <food>" menu entry names it.
	private int findHoveredFood()
	{
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length == 0)
		{
			return -1;
		}
		MenuEntry top = entries[entries.length - 1];
		return parseFoodFromMenuText(top.getOption(), top.getTarget());
	}

	private int parseFoodFromMenuText(String option, String target)
	{
		return "Cook".equalsIgnoreCase(option) ? foodForLabel(target) : -1;
	}

	private int foodForLabel(String label)
	{
		return pickRawFood(rawFoodByName().get(Text.removeTags(label).toLowerCase()));
	}

	// Several raw foods can share a name (beef, rat and bear meat are all "Cooked meat"); the one being
	// cooked is the one in the inventory.
	private int pickRawFood(List<Integer> candidates)
	{
		if (candidates == null)
		{
			return -1;
		}
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		for (int rawItemId : candidates)
		{
			if (inventory != null && inventory.count(rawItemId) > 0)
			{
				return rawItemId;
			}
		}
		return candidates.get(0);
	}

	private Map<String, List<Integer>> rawFoodByName()
	{
		if (rawFoodByName == null)
		{
			rawFoodByName = new HashMap<>();
			for (int rawItemId : RAW_FOOD_ITEM_IDS)
			{
				// The dialog says "Shark", not "Raw shark".
				String name = client.getItemDefinition(rawItemId).getName().toLowerCase();
				addRawFoodName(name, rawItemId);
				if (name.startsWith("raw "))
				{
					addRawFoodName(name.substring(4), rawItemId);
				}
				// Some foods, like karambwan, are labelled by what they cook into.
				addRawFoodName(client.getItemDefinition(COOKED_BY_RAW.get(rawItemId)).getName().toLowerCase(), rawItemId);
			}
		}
		return rawFoodByName;
	}

	private void addRawFoodName(String name, int rawItemId)
	{
		List<Integer> ids = rawFoodByName.computeIfAbsent(name, key -> new ArrayList<>());
		if (!ids.contains(rawItemId))
		{
			ids.add(rawItemId);
		}
	}

	// Longest match wins, so a short name inside a longer one can't take over.
	private int resolveFoodFromMessage(String message)
	{
		List<Integer> best = null;
		int bestLength = -1;
		for (Map.Entry<String, List<Integer>> entry : rawFoodByName().entrySet())
		{
			String name = entry.getKey();
			if (name.length() > bestLength && Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(message).find())
			{
				best = entry.getValue();
				bestLength = name.length();
			}
		}
		return pickRawFood(best);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		if (isCookingAnimation(actor.getAnimation()))
		{
			cookingAnimationSeen = true;
			clearCheckTick = -1;
		}
		else if (cookingAnimationSeen)
		{
			clearCheckTick = client.getTickCount() + 1;
		}
	}

	private boolean isPlayerNear(LocalPoint location)
	{
		Actor localPlayer = client.getLocalPlayer();
		return location != null && localPlayer != null
			&& localPlayer.getLocalLocation().distanceTo(location) <= NEAR_RANGE_DISTANCE;
	}

	// Local coordinates shift when a new region loads, so the stored range location goes stale.
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			lastCookLocation = null;
		}
	}

	private static boolean isCookingAnimation(int animation)
	{
		// Fires use their own animation.
		return animation == AnimationID.HUMAN_COOKING || animation == AnimationID.HUMAN_COOKING_LOOP
			|| animation == AnimationID.HUMAN_FIRECOOKING || animation == AnimationID.HUMAN_COOKING_WALKMERGE_NORESTART;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Results are SPAM, out-of-food is GAMEMESSAGE or MESBOX. Player chat is ignored.
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.MESBOX)
		{
			return;
		}

		String message = event.getMessage().toLowerCase();
		int tick = client.getTickCount();
		if (message.equals(lastChatMessage) && tick == lastChatMessageTick)
		{
			return;
		}
		lastChatMessage = message;
		lastChatMessageTick = tick;

		if (message.contains("haven't got anything to cook") || message.contains("don't have any more"))
		{
			if (pendingCookLocation == null)
			{
				return;
			}
			log.debug("Clearing: out of food, message={}", message);
			clearCooking();
			return;
		}

		// Matched precisely: "You eat the shark.", "Are you sure you wish to cook..." and
		// "The fire burns out" would otherwise pass as results.
		boolean burnt = message.startsWith("you accidentally");
		boolean cooked = message.startsWith("you successfully cook") || message.startsWith("you cook ");
		if (!burnt && !cooked)
		{
			return;
		}
		log.debug("Cook result message: {}", message);

		// The message names the food the game actually cooked; trust it over our tracking.
		int resolvedItemId = resolveFoodFromMessage(message);

		// Cooking with no session (it was cleared, or the start was missed): pick the last range back up.
		if (pendingCookLocation == null)
		{
			if (resolvedItemId == -1 || !isPlayerNear(lastCookLocation))
			{
				return;
			}
			log.debug("Resuming at last range from chat");
			pendingCookLocation = lastCookLocation;
		}
		if (resolvedItemId != -1 && resolvedItemId != pendingCookItemId)
		{
			log.debug("Resyncing food from chat: was={}, now={}", pendingCookItemId, resolvedItemId);
			pendingCookItemId = resolvedItemId;
			cookingAnimationSeen = true;
		}

		if (pendingCookItemId == -1)
		{
			return;
		}

		Integer resultItemId = burnt ? BURNT_BY_RAW.get(pendingCookItemId) : COOKED_BY_RAW.get(pendingCookItemId);
		if (resultItemId == null)
		{
			// No burnt model for this food.
			playSmoke();
			hideModel();
			scheduleNextRaw();
		}
		else if (displayedItemId == pendingCookItemId)
		{
			showResult(resultItemId, burnt);
		}
		else
		{
			// The raw food isn't up yet (previous result still cycling, or we only just learned
			// the food). Show it for a beat first.
			showModel(pendingCookItemId);
			pendingShowItemId = resultItemId;
			pendingShowIsResult = true;
			pendingShowIsBurnt = burnt;
			showAtTick = tick + 1;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();

		// Large ranges can leave the player further than NEAR_RANGE_DISTANCE, so the dialog or animation also counts.
		if (waitingForArrival && (isPlayerNear(pendingCookLocation) || cookingAnimationSeen || !isSkillmultiDialogClosed()))
		{
			showModel(pendingCookItemId);
		}

		if (clearCheckTick != -1 && tick >= clearCheckTick)
		{
			clearCheckTick = -1;
			Actor localPlayer = client.getLocalPlayer();
			if (localPlayer == null || !isCookingAnimation(localPlayer.getAnimation()))
			{
				log.debug("Clearing: cook animation stopped, itemId={}", pendingCookItemId);
				clearCooking();
			}
		}

		if (dialogCloseCheckTick != -1 && tick >= dialogCloseCheckTick)
		{
			dialogCloseCheckTick = -1;
			if (!cookingAnimationSeen && isSkillmultiDialogClosed())
			{
				log.debug("Clearing: dialog closed before cooking, itemId={}", pendingCookItemId);
				clearCooking();
			}
		}

		if (showAtTick != -1 && tick >= showAtTick)
		{
			showAtTick = -1;
			if (pendingShowIsResult)
			{
				showResult(pendingShowItemId, pendingShowIsBurnt);
			}
			else if (hasRawFoodLeft())
			{
				showModel(pendingShowItemId);
			}
			else
			{
				// The animation keeps going after the last food, so the inventory decides.
				clearCooking();
			}
		}

		if (hideAtTick != -1 && tick >= hideAtTick)
		{
			hideAtTick = -1;
			hideModel();
			scheduleNextRaw();
		}
	}

	private void scheduleNextRaw()
	{
		pendingShowItemId = pendingCookItemId;
		pendingShowIsResult = false;
		showAtTick = client.getTickCount() + GAP_TICKS;
	}

	private boolean hasRawFoodLeft()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory != null && inventory.count(pendingCookItemId) > 0;
	}

	private void clearCooking()
	{
		pendingHoverSwapItemId = -1;
		waitingForArrival = false;
		hideModel();
		pendingCookItemId = -1;
		pendingCookLocation = null;
		hoverPreviewItemId = -1;
		hideAtTick = -1;
		showAtTick = -1;
		pendingShowItemId = -1;
		pendingShowIsResult = false;
		dialogSeenOpen = false;
		cookingAnimationSeen = false;
		clearCheckTick = -1;
		dialogCloseCheckTick = -1;
	}

	private void showResult(int resultItemId, boolean burnt)
	{
		showModel(resultItemId);
		hideAtTick = client.getTickCount() + RESULT_DISPLAY_TICKS;
		if (burnt)
		{
			playSmoke();
		}
	}

	// The smoke's orange embers (any face more saturated than its grey) go its grey, keeping their lightness.
	private static ModelData greyEmbers(ModelData smoke)
	{
		ModelData result = smoke.cloneColors();
		short[] colors = result.getFaceColors();
		for (int face = 0; face < colors.length; face++)
		{
			if (((colors[face] >> 7) & 7) > SMOKE_GREY_SATURATION)
			{
				colors[face] = (short) (SMOKE_GREY_HUE << 10 | SMOKE_GREY_SATURATION << 7 | colors[face] & 127);
			}
		}
		return result;
	}

	private void playSmoke()
	{
		ModelData modelData = client.loadModelData(SMOKE_MODEL);
		if (modelData == null)
		{
			return;
		}
		// Same lighting the client gives spot animations.
		smokeObject.setModel(greyEmbers(modelData).light(SMOKE_AMBIENT + 64, SMOKE_CONTRAST + 850, -30, -50, -30));
		RuneLiteObject smoke = smokeObject;
		smokeObject.setAnimationController(new AnimationController(client, AnimationID.FORESTRY_CAMPFIRE)
			.setOnFinished(controller -> smoke.setActive(false)));
		smokeObject.setLocation(pendingCookLocation, client.getTopLevelWorldView().getPlane());
		smokeObject.setZ(smokeObject.getZ() - cookHoverHeight);
		smokeObject.setActive(true);
	}

	private void showModel(int itemId)
	{
		log.debug("showModel: itemId={}", itemId);
		waitingForArrival = false;
		hideAtTick = -1;
		showAtTick = -1;
		pendingHoverSwapItemId = -1;
		boolean wasAlreadyVisible = foodObject.isActive();
		displayedItemId = itemId;

		foodObject.setLocation(pendingCookLocation, client.getTopLevelWorldView().getPlane());
		foodObject.setZ(foodObject.getZ() - cookHoverHeight);

		// Swapping what's already on screen (raw -> result) is instant; a fresh appearance pops in.
		if (wasAlreadyVisible)
		{
			fadeState = FadeState.NONE;
			foodObject.setModel(buildItemModel(itemId, MODEL_SCALE));
		}
		else
		{
			foodObject.setModel(buildItemModel(itemId, MIN_SCALE_PERCENT));
			fadeItemId = itemId;
			fadeState = FadeState.IN;
			fadeStartMs = System.currentTimeMillis();
		}
		foodObject.setActive(true);
	}

	private void hideModel()
	{
		// Nothing showing, or already shrinking - restarting would freeze the shrink mid-way.
		if (displayedItemId == -1)
		{
			return;
		}

		long now = System.currentTimeMillis();
		// Mid grow-in, shrink from the current size: skip the part of the shrink that's bigger.
		long skippedMs = fadeState == FadeState.IN ? FADE_DURATION_MS - Math.min(FADE_DURATION_MS, now - fadeStartMs) : 0;

		fadeItemId = displayedItemId;
		displayedItemId = -1;
		fadeState = FadeState.OUT;
		fadeStartMs = now - skippedMs;
	}

	private void updateFade()
	{
		if (fadeState == FadeState.NONE)
		{
			return;
		}

		float progress = Math.min(1f, (System.currentTimeMillis() - fadeStartMs) / (float) FADE_DURATION_MS);
		float t = fadeState == FadeState.IN ? progress : (1f - progress);
		int scalePercent = Math.max(MIN_SCALE_PERCENT, (int) (t * MODEL_SCALE));
		foodObject.setModel(buildItemModel(fadeItemId, scalePercent));

		if (progress < 1f)
		{
			return;
		}

		FadeState finished = fadeState;
		fadeState = FadeState.NONE;
		if (finished == FadeState.OUT)
		{
			foodObject.setActive(false);
			if (pendingHoverSwapItemId != -1 && pendingCookLocation != null)
			{
				showModel(pendingHoverSwapItemId);
			}
			pendingHoverSwapItemId = -1;
		}
	}

	private Model buildItemModel(int itemId, int scalePercent)
	{
		ItemComposition itemComposition = client.getItemDefinition(itemId);
		// loadModelData returns a shared instance, so clone before mutating. Colours/textures are only
		// cloned when replaced, since cloning a null array throws.
		ModelData modelData = client.loadModelData(itemComposition.getInventoryModel()).cloneVertices();

		short[] colorToReplace = itemComposition.getColorToReplace();
		short[] colorToReplaceWith = itemComposition.getColorToReplaceWith();
		if (colorToReplace != null)
		{
			modelData = modelData.cloneColors();
			for (int i = 0; i < colorToReplace.length; i++)
			{
				modelData = modelData.recolor(colorToReplace[i], colorToReplaceWith[i]);
			}
		}

		short[] textureToReplace = itemComposition.getTextureToReplace();
		short[] textureToReplaceWith = itemComposition.getTextureToReplaceWith();
		if (textureToReplace != null)
		{
			modelData = modelData.cloneTextures();
			for (int i = 0; i < textureToReplace.length; i++)
			{
				modelData = modelData.retexture(textureToReplace[i], textureToReplaceWith[i]);
			}
		}

		return modelData.scale(scalePercent, scalePercent, scalePercent).light();
	}
}
