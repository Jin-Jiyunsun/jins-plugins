package com.barbershop;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Hides the player's own head and draws an NPC's head that copies its every move: the game
 * still animates the real head with the real body and gear, and each frame the NPC head is
 * moved the way the hidden head moved.
 */
@Slf4j
@PluginDescriptor(
	name = "Barber Shop",
	description = "Wear the head and hair of a non-player character",
	tags = {"hair", "hairstyle", "head", "cosmetic", "transmog", "npc", "fashion"}
)
public class BarberShopPlugin extends Plugin
{
	// The game's lighting for players and NPCs.
	private static final int AMBIENT = 64;
	private static final int CONTRAST = 850;
	private static final int LIGHT_X = -30;
	private static final int LIGHT_Y = -50;
	private static final int LIGHT_Z = -30;

	// NPC heads sit this much too high on a player of their own body type (measured in game).
	private static final int HEAD_DROP = 2;
	// Body type A's neck stands this much taller than body type B's.
	private static final int NECK_DIFFERENCE = 3;

	// The colour skin is modelled in; the game swaps it for a skin tone from SKIN_TONES.
	private static final short BASE_SKIN = 4550;
	// The game's skin tones, in the order of the player's skin colour setting (from the client).
	private static final short[] SKIN_TONES = {
		4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574, 17050, 0, 127, -31821, -17991, 918
	};
	// Which of the player's colour settings is skin.
	private static final int SKIN_SLOT = 4;

	// How far up from its lowest point the head follows the player's neck fully, and up to where
	// that fades out, in model units; the debug plugin may change them while tuning.
	static int neckWeldFull = 3;
	static int neckWeldBand = 3;
	// How far above the player's chin the head's skin starts being hidden; the debug plugin may change it.
	static int neckCut = 10;
	private int cutAt;
	// How many of the player's nearest head vertices each neck vertex follows.
	private static final int NECK_WELD_NEAREST = 3;

	// Stand-in hairstyles (kit ids) per body type, used when headgear has emptied the hair slot.
	private static final int STAND_IN_HAIR_A = 0;
	private static final int STAND_IN_HAIR_B = 45;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BarberShopConfig config;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	// Whether the game put the player in the scene during the frame being drawn; it doesn't while
	// they're hidden, such as at the end of a teleport, even though they still have a model.
	private volatile boolean playerAdded;
	// Whether the head is set up and shown this frame, so the draw hook may move it.
	private boolean tracking;
	private final RenderCallback drawCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			Player player = client.getLocalPlayer();
			if (!ui && renderable == player)
			{
				playerAdded = true;
				if (tracking)
				{
					Model model = player.getModel();
					if (model != null && model.getFaceCount() == ranges.getFaceCount())
					{
						track(player, model);
					}
				}
			}
			return true;
		}
	};

	// The NPC head: a still copy moved every frame, its unmoved positions kept apart.
	private Head built;
	// Raises (negative) or lowers the head in its own frame; set by the debug plugin while measuring.
	static int heightNudge;
	private int builtDrop;
	private int builtSkin;
	// The head's baked colours before any brightness match, and whether the match is done.
	private int[] headBase1;
	private int[] headBase2;
	private int[] headBase3;
	private boolean skinMatched;
	// The head's skin colour (hue and saturation part), to tell its skin faces apart.
	private int headSkinHueSat;
	private boolean welded;
	private int weldedFull;
	private int weldedBand;
	private RuneLiteObject headObject;
	private Model headModel;
	// For the debug plugin: the NPC head as last built, and the player's own head faces.
	static volatile Model debugHeadModel;
	static volatile HeadFaces.Ranges debugRanges;
	private float[] headRestX;
	private float[] headRestY;
	private float[] headRestZ;
	// The head's own see-through values, and the fade last copied from the player's head on top.
	private byte[] headAlpha;
	private int headFade;

	// The player's own head, hidden, and how it is followed.
	private int appearance;
	private HeadFaces.Ranges ranges;
	private HeadFit fit;
	private int[] hiddenColours;
	private int[] savedColours;
	// The player's whole model's unanimated heights, for the neck line.
	private float[] ownRestY;

	// Headgear taken off, and a stand-in hairstyle put on when headgear had hidden the hair.
	private int removedHeadgear;
	private int addedHair;

	@Override
	protected void startUp()
	{
		renderCallbackManager.register(drawCallback);
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(drawCallback);
		clientThread.invoke(this::clear);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BarberShopConfig.GROUP.equals(event.getGroup()))
		{
			clientThread.invoke(this::clear);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// A scene load drops the drawn head; start over once it's done.
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			clear();
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		boolean playerDrawn = playerAdded;
		playerAdded = false;
		tracking = false;

		Player player = client.getLocalPlayer();
		Head head = config.head();
		PlayerComposition composition = player == null ? null : player.getPlayerComposition();
		if (head == Head.NONE || composition == null)
		{
			clear();
			return;
		}

		int drop = headDrop(head, composition);
		int skin = config.matchSkin() ? playerSkin(composition) : -1;
		if ((built != head || builtDrop != drop || builtSkin != skin) && !build(head, drop, skin))
		{
			return;
		}

		dressHead(composition);
		int ownSkin = playerSkin(composition);
		int ownSkinHueSat = ownSkin < 0 ? -1 : ownSkin >> 7;
		int now = Arrays.hashCode(composition.getEquipmentIds()) * 31 + Arrays.hashCode(composition.getColors());
		Model model = player.getModel();
		if (model == null)
		{
			return;
		}
		if (now != appearance || hiddenColours != null && model.getFaceColors3() != hiddenColours)
		{
			// New look, or the game rebuilt the model: find and hide the head again.
			showOwnHead();
			appearance = now;
			if (!follow(player))
			{
				hideObject();
				return;
			}
			model = player.getModel();
			if (model == null || model.getFaceCount() != ranges.getFaceCount())
			{
				fit = null;
				hideObject();
				return;
			}
			hideOwnHead(model, ownSkinHueSat);
			skinMatched = false;
			welded = false;
		}
		// Going by the last frame: hide the head while the game isn't drawing the player.
		if (fit == null || !playerDrawn)
		{
			hideObject();
			return;
		}

		if (!skinMatched || cutAt != neckCut)
		{
			if (cutAt != neckCut)
			{
				cutAt = neckCut;
				showOwnHead();
				hideOwnHead(model, ownSkinHueSat);
			}
			matchSkinBrightness(model);
			hideNeckStub();
			skinMatched = true;
		}
		if (!welded || weldedFull != neckWeldFull || weldedBand != neckWeldBand)
		{
			weldedFull = neckWeldFull;
			weldedBand = neckWeldBand;
			fit.weld(headRestX, headRestY, headRestZ, headRestX.length, weldedFull, weldedBand, NECK_WELD_NEAREST);
			welded = true;
		}
		if (player.getWorldView() == null || player.getLocalLocation() == null)
		{
			hideObject();
			return;
		}
		track(player, model);
		if (!client.isRuneLiteObjectRegistered(headObject))
		{
			client.registerRuneLiteObject(headObject);
		}
		tracking = true;
	}

	/**
	 * Moves the head to match the player's hidden head and puts it where the player stands. Done at
	 * the start of the frame, then again as the game adds the player to the scene, as their position
	 * can still move in between (smoothed movement while walking).
	 */
	private void track(Player player, Model model)
	{
		fit.fit(model.getVerticesX(), model.getVerticesY(), model.getVerticesZ());
		fit.apply(headRestX, headRestY, headRestZ,
			headModel.getVerticesX(), headModel.getVerticesY(), headModel.getVerticesZ(), headRestX.length);
		headModel.calculateBoundsCylinder();
		copyFade(model);
		place(player);
	}

	/** Loads the NPC's head pieces, recoloured as the NPC wears them. */
	/**
	 * Shifts the brightness of the head's skin faces so their average matches the skin on the
	 * player's body (neck, arms, hands) as the game baked it, which is what the head is seen against.
	 */
	private void matchSkinBrightness(Model player)
	{
		int[] c1 = headModel.getFaceColors1();
		int[] c2 = headModel.getFaceColors2();
		int[] c3 = headModel.getFaceColors3();
		System.arraycopy(headBase1, 0, c1, 0, c1.length);
		System.arraycopy(headBase2, 0, c2, 0, c2.length);
		System.arraycopy(headBase3, 0, c3, 0, c3.length);
		if (builtSkin == -1)
		{
			return;
		}
		int hueSat = builtSkin >> 7;
		int own = averageLightness(player.getFaceColors1(), player.getFaceColors2(), hueSat, ranges, false);
		int head = averageLightness(c1, c2, hueSat, null, true);
		if (own < 0 || head < 0 || own == head)
		{
			return;
		}
		int shift = own - head;
		for (int face = 0; face < c1.length; face++)
		{
			if (c3[face] == -2 || (c1[face] >> 7) != hueSat)
			{
				continue;
			}
			c1[face] = brighten(c1[face], shift);
			if (c3[face] != -1)
			{
				c2[face] = brighten(c2[face], shift);
				c3[face] = brighten(c3[face], shift);
			}
		}
	}

	/** The colour an NPC gives the base skin colour, or the base colour if it keeps it. */
	private static int npcSkin(short[] find, short[] replace)
	{
		if (find != null && replace != null)
		{
			for (int i = 0; i < find.length && i < replace.length; i++)
			{
				if (find[i] == BASE_SKIN)
				{
					return replace[i] & 0xFFFF;
				}
			}
		}
		return BASE_SKIN;
	}

	/**
	 * Hides the head's skin faces that sit wholly below the neck line, where the player's own neck shows
	 * instead. NPC necks face mostly down, away from the light, and come out a flat dark colour.
	 */
	private void hideNeckStub()
	{
		if (cutAt <= 0)
		{
			return;
		}
		float chin = neckLine();
		int[] a = headModel.getFaceIndices1();
		int[] b = headModel.getFaceIndices2();
		int[] c = headModel.getFaceIndices3();
		int[] c1 = headModel.getFaceColors1();
		int[] c3 = headModel.getFaceColors3();
		for (int face = 0; face < c1.length; face++)
		{
			if (c3[face] != -2 && (c1[face] >> 7) == headSkinHueSat
				&& headRestY[a[face]] > chin && headRestY[b[face]] > chin && headRestY[c[face]] > chin)
			{
				c3[face] = -2;
			}
		}
	}

	/**
	 * Average lightness of faces in the given hue and saturation, from their first two corners; -1 if none.
	 * With ranges, only faces inside them (inside true) or outside them (inside false).
	 */
	private static int averageLightness(int[] c1, int[] c2, int hueSat, HeadFaces.Ranges ranges, boolean inside)
	{
		long total = 0;
		int count = 0;
		for (int face = 0; face < c1.length; face++)
		{
			if (ranges != null && ranges.contains(face) != inside)
			{
				continue;
			}
			for (int c : new int[]{c1[face], c2[face]})
			{
				if (c >= 0 && (c >> 7) == hueSat)
				{
					total += c & 127;
					count++;
				}
			}
		}
		return count == 0 ? -1 : (int) Math.round((double) total / count);
	}

	private static int brighten(int colour, int shift)
	{
		return colour & ~127 | Math.max(0, Math.min(127, (colour & 127) + shift));
	}

	/**
	 * Fades the NPC head as the player's hidden head is faded: some animations, such as teleports,
	 * fade the player out by making their faces see-through rather than by moving them.
	 */
	private void copyFade(Model player)
	{
		byte[] target = headModel.getFaceTransparencies();
		if (headAlpha == null || target == null)
		{
			return;
		}
		int fade = 0;
		byte[] playerAlpha = player.getFaceTransparencies();
		if (playerAlpha != null)
		{
			int total = 0;
			int count = 0;
			for (int face = 0; face < ranges.getFaceCount() && face < playerAlpha.length; face++)
			{
				if (ranges.contains(face))
				{
					total += playerAlpha[face] & 0xFF;
					count++;
				}
			}
			fade = count == 0 ? 0 : total / count;
		}
		if (fade == headFade)
		{
			return;
		}
		headFade = fade;
		for (int face = 0; face < target.length && face < headAlpha.length; face++)
		{
			// 255 is fully see-through; stack the fade on the face's own see-through value.
			int own = headAlpha[face] & 0xFF;
			target[face] = (byte) (255 - (255 - own) * (255 - fade) / 255);
		}
	}

	/**
	 * How far to lower the head from where the NPC wore it: a fixed amount measured in game, plus the
	 * difference in neck height when the head was made for the other body type.
	 */
	private static int headDrop(Head head, PlayerComposition composition)
	{
		Head.BodyType player = composition.getGender() == 0 ? Head.BodyType.A : Head.BodyType.B;
		int neck = 0;
		if (head.getBody() != player)
		{
			neck = head.getBody() == Head.BodyType.A ? NECK_DIFFERENCE : -NECK_DIFFERENCE;
		}
		return HEAD_DROP + neck + heightNudge;
	}

	/** The player's skin colour, or -1 for one outside the game's skin tone list. */
	static int playerSkin(PlayerComposition composition)
	{
		int[] colours = composition.getColors();
		int tone = colours == null || colours.length <= SKIN_SLOT ? -1 : colours[SKIN_SLOT];
		return tone >= 0 && tone < SKIN_TONES.length ? SKIN_TONES[tone] & 0xFFFF : -1;
	}

	/**
	 * @param skin the player's skin colour to give the head, or -1 to keep the NPC's own
	 */
	private boolean build(Head head, int drop, int skin)
	{
		NPCComposition npc = client.getNpcDefinition(head.getNpcId());
		int[] models = npc == null ? null : npc.getModels();
		if (models == null)
		{
			return false;
		}
		ModelData[] parts = new ModelData[head.getPieces().length];
		for (int i = 0; i < parts.length; i++)
		{
			int piece = head.getPieces()[i];
			parts[i] = piece < models.length ? client.loadModelData(models[piece]) : null;
			if (parts[i] == null)
			{
				log.debug("Barber Shop: model piece {} of {} not loaded", piece, head);
				return false;
			}
		}
		// With a see-through value per face, made if the pieces have none, so animations can fade it.
		ModelData data = client.mergeModels(parts).cloneColors().cloneTransparencies(true);
		short[] find = npc.getColorToReplace();
		short[] replace = npc.getColorToReplaceWith();
		if (find != null && replace != null)
		{
			for (int i = 0; i < find.length && i < replace.length; i++)
			{
				// The NPC's own skin tone is left out when the player's goes on instead.
				if (skin == -1 || find[i] != BASE_SKIN)
				{
					data.recolor(find[i], replace[i]);
				}
			}
		}
		if (skin != -1)
		{
			// Faces still on the base skin colour are skin, including any an NPC swaps onto it.
			data.recolor(BASE_SKIN, (short) skin);
		}
		headSkinHueSat = (skin != -1 ? skin : npcSkin(find, replace)) >> 7;
		// Lit as the game lights characters, not as items on the ground or icons.
		Model model = data.light(AMBIENT, CONTRAST, LIGHT_X, LIGHT_Y, LIGHT_Z);
		if (model == null)
		{
			return false;
		}
		int count = model.getVerticesCount();
		headRestX = Arrays.copyOf(model.getVerticesX(), count);
		headRestY = Arrays.copyOf(model.getVerticesY(), count);
		builtDrop = drop;
		builtSkin = skin;
		for (int i = 0; i < count; i++)
		{
			headRestY[i] += drop;
		}
		headRestZ = Arrays.copyOf(model.getVerticesZ(), count);
		byte[] alpha = model.getFaceTransparencies();
		headAlpha = alpha == null ? null : alpha.clone();
		headFade = 0;
		headModel = model;
		debugHeadModel = model;
		headBase1 = model.getFaceColors1().clone();
		headBase2 = model.getFaceColors2().clone();
		headBase3 = model.getFaceColors3().clone();
		skinMatched = false;
		welded = false;
		if (headObject == null)
		{
			headObject = client.createRuneLiteObject();
		}
		headObject.setModel(model);
		built = head;
		return true;
	}

	/**
	 * Finds the player's own head faces and takes their unanimated positions, by briefly
	 * clearing the player's animations. Returns false if there's no head to follow, such as
	 * under a helmet that hides it.
	 */
	private boolean follow(Player player)
	{
		fit = null;
		ranges = HeadFaces.find(player);
		// Without hair there's no head to follow; dressHead normally makes sure there is some.
		if (ranges == null || ranges.getHairEnd() == ranges.getHairStart())
		{
			return false;
		}

		int animation = player.getAnimation();
		int animationFrame = player.getAnimationFrame();
		int pose = player.getPoseAnimation();
		int poseFrame = player.getPoseAnimationFrame();
		player.setAnimation(-1);
		player.setPoseAnimation(-1);
		Model rest = player.getModel();
		player.setAnimation(animation);
		player.setAnimationFrame(animationFrame);
		player.setPoseAnimation(pose);
		player.setPoseAnimationFrame(poseFrame);
		if (rest == null || rest.getFaceCount() != ranges.getFaceCount())
		{
			return false;
		}

		Set<Integer> vertices = new LinkedHashSet<>();
		int[] a = rest.getFaceIndices1();
		int[] b = rest.getFaceIndices2();
		int[] c = rest.getFaceIndices3();
		for (int face = 0; face < ranges.getFaceCount(); face++)
		{
			if (ranges.contains(face))
			{
				vertices.add(a[face]);
				vertices.add(b[face]);
				vertices.add(c[face]);
			}
		}
		Set<Integer> body = new LinkedHashSet<>();
		for (int face = 0; face < ranges.getFaceCount(); face++)
		{
			if (!ranges.contains(face))
			{
				body.add(a[face]);
				body.add(b[face]);
				body.add(c[face]);
			}
		}
		body.removeAll(vertices);

		ownRestY = rest.getVerticesY().clone();
		int[] list = vertices.stream().mapToInt(Integer::intValue).toArray();
		fit = HeadFit.of(list, rest.getVerticesX(), rest.getVerticesY(), rest.getVerticesZ());
		if (fit != null)
		{
			fit.setBody(body.stream().mapToInt(Integer::intValue).toArray(),
				rest.getVerticesX(), rest.getVerticesY(), rest.getVerticesZ());
		}
		return fit != null;
	}

	/**
	 * Takes headgear off, as it clips through the new head. Headgear that hid the hair leaves the
	 * hair slot empty, so a stand-in hairstyle goes on to give the head something to follow;
	 * it's hidden with the rest of the player's own head.
	 */
	private void dressHead(PlayerComposition composition)
	{
		int[] ids = composition.getEquipmentIds();
		int headSlot = KitType.HEAD.getIndex();
		int hairSlot = KitType.HAIR.getIndex();
		boolean changed = false;
		if (ids[headSlot] != 0)
		{
			removedHeadgear = ids[headSlot];
			ids[headSlot] = 0;
			changed = true;
		}
		if (ids[hairSlot] == 0)
		{
			addedHair = PlayerComposition.KIT_OFFSET + (composition.getGender() == 0 ? STAND_IN_HAIR_A : STAND_IN_HAIR_B);
			ids[hairSlot] = addedHair;
			changed = true;
		}
		if (changed)
		{
			composition.setHash();
		}
	}

	/** Puts back what dressHead changed, unless the game has already sent a fresh look. */
	private void undressHead()
	{
		Player player = client.getLocalPlayer();
		PlayerComposition composition = player == null ? null : player.getPlayerComposition();
		if (composition != null && (removedHeadgear != 0 || addedHair != 0))
		{
			int[] ids = composition.getEquipmentIds();
			boolean changed = false;
			if (removedHeadgear != 0 && ids[KitType.HEAD.getIndex()] == 0)
			{
				ids[KitType.HEAD.getIndex()] = removedHeadgear;
				changed = true;
			}
			if (addedHair != 0 && ids[KitType.HAIR.getIndex()] == addedHair)
			{
				ids[KitType.HAIR.getIndex()] = 0;
				changed = true;
			}
			if (changed)
			{
				composition.setHash();
			}
		}
		removedHeadgear = 0;
		addedHair = 0;
	}

	/**
	 * Hides the player's own head, except their neck: skin faces wholly below the neck line stay, as the
	 * game shades them and keeps them on the body; the new head's skin is hidden there instead.
	 */
	private void hideOwnHead(Model model, int skinHueSat)
	{
		debugRanges = ranges;
		float line = neckLine();
		int[] a = model.getFaceIndices1();
		int[] b = model.getFaceIndices2();
		int[] c = model.getFaceIndices3();
		int[] c1 = model.getFaceColors1();
		// The face colours are shared with the game's stored model, so this holds until it's rebuilt.
		int[] colours = model.getFaceColors3();
		savedColours = colours.clone();
		hiddenColours = colours;
		for (int face = 0; face < ranges.getFaceCount(); face++)
		{
			if (!ranges.contains(face))
			{
				continue;
			}
			boolean neck = skinHueSat >= 0 && cutAt > 0 && (c1[face] >> 7) == skinHueSat
				&& ownRestY[a[face]] > line && ownRestY[b[face]] > line && ownRestY[c[face]] > line;
			if (!neck)
			{
				// The game's mark for a face that isn't drawn.
				colours[face] = -2;
			}
		}
	}

	/** Below this height (y points down) the player's own neck shows instead of the new head's. */
	private float neckLine()
	{
		return fit.restBottom() - cutAt;
	}

	private void showOwnHead()
	{
		if (hiddenColours != null && savedColours != null && hiddenColours.length == savedColours.length)
		{
			System.arraycopy(savedColours, 0, hiddenColours, 0, savedColours.length);
		}
		hiddenColours = null;
		savedColours = null;
	}

	private void place(Player player)
	{
		WorldView view = player.getWorldView();
		LocalPoint location = player.getLocalLocation();
		if (view == null || location == null)
		{
			hideObject();
			return;
		}
		headObject.setLocation(location, view.getPlane());
		// setLocation leaves the height alone. Actors stand at their footprint's height, lifted by any
		// animation height offset, as RuneLite's own model outlines place them.
		headObject.setZ(footprintHeight(view, location, player.getFootprintSize()) - player.getAnimationHeightOffset());
		headObject.setOrientation(player.getCurrentOrientation());
	}

	/**
	 * The highest ground under an actor's footprint, as Perspective.getFootprintTileHeight works it out,
	 * but without moving up a level for bridge tiles first: the view's own height lookup already does,
	 * so doing both lifts the head two levels on bridge tiles.
	 */
	private static int footprintHeight(WorldView view, LocalPoint location, int size)
	{
		int x = location.getX();
		int y = location.getY();
		int plane = view.getPlane();
		int height = view.getTileHeight(x, y, plane);
		if (size == 0)
		{
			return height;
		}
		int half = size / 2;
		int startX = (x - half >> Perspective.LOCAL_COORD_BITS) + 1;
		int startY = (y - half >> Perspective.LOCAL_COORD_BITS) + 1;
		int endX = x + half >> Perspective.LOCAL_COORD_BITS;
		int endY = y + half >> Perspective.LOCAL_COORD_BITS;
		for (int tileX = startX; tileX <= endX; tileX++)
		{
			for (int tileY = startY; tileY <= endY; tileY++)
			{
				height = Math.min(height, view.getTileHeight(tileX << Perspective.LOCAL_COORD_BITS, tileY << Perspective.LOCAL_COORD_BITS, plane));
			}
		}
		height = Math.min(height, view.getTileHeight(x - half, y - half, plane));
		height = Math.min(height, view.getTileHeight(x - half, y + half, plane));
		height = Math.min(height, view.getTileHeight(x + half, y - half, plane));
		height = Math.min(height, view.getTileHeight(x + half, y + half, plane));
		return height;
	}

	private void hideObject()
	{
		if (headObject != null && client.isRuneLiteObjectRegistered(headObject))
		{
			client.removeRuneLiteObject(headObject);
		}
	}

	private void clear()
	{
		if (headObject == null && hiddenColours == null && removedHeadgear == 0 && addedHair == 0)
		{
			return;
		}
		hideObject();
		showOwnHead();
		undressHead();
		headObject = null;
		headModel = null;
		built = null;
		fit = null;
		ranges = null;
		appearance = 0;
	}

	@Provides
	BarberShopConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BarberShopConfig.class);
	}
}
