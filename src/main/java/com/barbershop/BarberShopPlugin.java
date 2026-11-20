package com.barbershop;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.JagexColor;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.gameval.NpcID;
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

	// The colour skin is modelled in; the game swaps it for a skin tone from SKIN_TONES.
	private static final short BASE_SKIN = 4550;
	// The game's skin tones, in the order of the player's skin colour setting (from the client).
	private static final short[] SKIN_TONES = {
		4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574, 17050, 0, 127, -31821, -17991, 918
	};
	// Which of the player's colour settings is skin.
	private static final int SKIN_SLOT = 4;

	// The two colours hair (and beards) are modelled in, and the game's colours for each, in the order
	// of the player's hair colour setting (from the client's two player colour tables).
	private static final short BASE_HAIR = 6798;
	private static final short BASE_HAIR_SHADE = -10304;
	private static final short[] HAIR_TONES = {
		6798, 107, 10283, 16, 4797, 7744, 5799, 4634, -31839, 22433, 2983, -11343, 8, 5281, 10438, 3650,
		-27322, -21845, 200, 571, 908, 21830, 28946, -15701, -14010, -22122, 937, 8130, -13422, 30385
	};
	private static final short[] HAIR_SHADES = {
		6554, 115, 10304, 28, 5702, 7756, 5681, 4510, -31835, 22437, 2859, -11339, 16, 5157, 10446, 3658,
		-27314, -21965, 472, 580, 784, 21966, 28950, -15697, -14002, -22116, 945, 8144, -13414, 30389
	};
	// Which of the player's colour settings is hair.
	private static final int HAIR_SLOT = 0;
	// How the second hair shade differs from the main colour, as in nearly all of the game's own pairs.
	private static final int SHADE_SATURATION = -1;
	private static final int SHADE_LUMINANCE = 10;

	// How far above the player's chin the head's skin starts being hidden; the debug plugin may change it.
	static int neckCut = 10;
	private int cutAt;
	// Heads whose own neck looks right, so they keep it and the player's stays hidden: by NPC id.
	private static final Set<Integer> NO_NECK_CUT = new HashSet<>(Arrays.asList(
		NpcID.SAILING_BT_TRIAL_MASTER_2_ASSISTANT_1OP,
		NpcID.PORT_TASK_MASTER_PRIFDDINAS
	));

	// Heads with hair modelled under a hat that is its own piece: NPC id to the hat's piece, always left out,
	// as hats don't belong in a hairstyle.
	private static final Map<Integer, Integer> HAT_PIECES = new HashMap<>();
	static
	{
		HAT_PIECES.put(NpcID.SAILING_CREW_GENERIC_2_WORLD, 6);
		HAT_PIECES.put(NpcID.RED_ROCK_CAPTAIN, 1);
	}

	// Earrings built into heads that aren't offered in the Earring setting (it has a look-alike), still hidden so
	// heads come without earrings: NPC id, piece, the earring's colours. Teicuh's pair is the same as Gem.
	private static final Object[][] OTHER_EARRINGS = {
		{NpcID.TAL_TEKLAN_RUNE_TRADER, 0, new short[]{2733}},
	};

	// Heads that always keep their own glasses, in the world and in dialogue; the Glasses setting doesn't
	// change them, as their chathead has the glasses built in.
	private static final Set<Integer> OWN_GLASSES = new HashSet<>(Arrays.asList(
		NpcID.SAILING_BT_TRIAL_MASTER_2_ASSISTANT_1OP
	));

	// Chathead models of the glasses and hats the heads are worn without, left out of the dialogue head too.
	private static final Set<Integer> CHATHEAD_EXTRAS = new HashSet<>(Arrays.asList(
		78, // Reldo's glasses, also Fortunato's
		59025, // Crew Registrar's monocle
		59023, // Eleanor's glasses
		59990, // Receptionist (Red Rock)'s glasses
		59009, // Adventurer Ada's hat
		59984, // Captain Maclean's hat
		59822 // Mercenary (Port Roberts)'s hat
	));

	// Earrings built into chathead models, by model id: their colours. A chathead can't hide faces, so the
	// earring takes the head's hair colour instead, through the NPC's colour swaps.
	private static final Map<Integer, short[]> CHATHEAD_EARRINGS = new HashMap<>();
	static
	{
		// Gwyna's fish
		CHATHEAD_EARRINGS.put(59014, new short[]{-29632, -29624, -28476, -28460, -28452, -28018, -28010, -27766});
		// Eleanor's gems
		CHATHEAD_EARRINGS.put(56369, new short[]{23853});
		// Auburn citizen (rich woman)
		CHATHEAD_EARRINGS.put(56373, new short[]{2993});
		// Auburn, Kastori and Tal Teklan rich men
		CHATHEAD_EARRINGS.put(56381, new short[]{2993});
	}

	// An NPC whose definition lends its long colour swap lists (13) to a chathead whose own are too short;
	// rarely seen, as its own look changes while the player's dialogue is open.
	private static final int CHATHEAD_CARRIER = NpcID.BAS_REBECCA;

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

	// The worn head's NPC shown in the player's dialogue, and the NPC id it's shown under: its own, or
	// CHATHEAD_CARRIER's when its own colour swaps are too few.
	private int chatheadWorn = -1;
	private int chatheadShown = -1;
	// Puts back the NPC definition arrays changed for the dialogue head.
	private final List<Runnable> chatheadRestores = new ArrayList<>();
	// NPCs' own chathead model lists, by NPC id, while changed.
	private final Map<Integer, int[]> chatheadModelsOriginal = new HashMap<>();
	// The colours of the worn heads' chathead models, by model id, once loaded.
	private final Map<Integer, Set<Short>> chatheadColours = new HashMap<>();
	// The glasses drawn over the dialogue chathead.
	private Widget chatGlasses;

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
	// Moves the glasses down and back on top of their own offsets; set by the debug plugin while measuring.
	static int glassesNudgeDown;
	static int glassesNudgeBack;
	private int builtGlassesNudge;
	private int builtDrop;
	private int builtSkin;
	private int builtHair;
	private Glasses builtGlasses;
	private Earring builtEarring;
	private EarringSide builtSide;
	private int builtEarringColour = -1;
	// The head's baked colours before any brightness match, and whether the match is done.
	private int[] headBase1;
	private int[] headBase2;
	private int[] headBase3;
	private boolean skinMatched;
	// The head's skin colour (hue and saturation part), to tell its skin faces apart.
	private int headSkinHueSat;
	private RuneLiteObject headObject;
	private Model headModel;
	// For the debug plugin: the NPC head as last built, and the player's own head faces.
	static Model debugHeadModel;
	static HeadFaces.Ranges debugRanges;
	private float[] headRestX;
	private float[] headRestY;
	private float[] headRestZ;
	// The head's own see-through values, and the fade last copied from the player's head on top.
	private byte[] headAlpha;
	private int headFade;

	// The player's own head, hidden, and how it is followed.
	private int appearance;
	// The game tick a search for the player's own head last failed on, to try again on the next one.
	private int failedTick = -1;
	private HeadFaces.Ranges ranges;
	private HeadFit fit;
	private int[] hiddenColours;
	private int[] savedColours;
	// The player's whole model's unanimated heights, for the neck line.
	private float[] ownRestY;

	// The choices whose models have all been loaded ahead of time, see preload.
	private String preloaded;

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
		clientThread.invoke(() ->
		{
			restoreChathead();
			clear();
		});
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
		// A scene load drops the drawn head, so it's added again afterwards; the player's own head stays
		// hidden meanwhile, as the scene is still drawn while loading. Hopping and logging out start over.
		GameState state = event.getGameState();
		if (state == GameState.LOADING)
		{
			hideObject();
		}
		else if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			clear();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.CHAT_RIGHT)
		{
			// The dialogue fills in its head after loading.
			clientThread.invokeLater(this::swapChathead);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		swapChathead();
	}

	/**
	 * Shows the worn head's NPC chathead in place of the player's own in their dialogue, with the chosen glasses
	 * drawn over it.
	 */
	private void swapChathead()
	{
		if (built != null && config.dialogueHead())
		{
			loadChatheadColours(built.getNpcId());
		}
		Widget chathead = client.getWidget(InterfaceID.ChatRight.HEAD);
		if (chathead == null)
		{
			restoreChathead();
			return;
		}
		if (chathead.getModelType() == WidgetModelType.LOCAL_PLAYER_CHATHEAD && config.dialogueHead() && built != null && fit != null)
		{
			int shown = dressChathead(built.getNpcId());
			chathead.setModelType(WidgetModelType.NPC_CHATHEAD);
			chathead.setModelId(shown);
		}
		boolean swapped = chatheadShown != -1 && chathead.getModelType() == WidgetModelType.NPC_CHATHEAD
			&& chathead.getModelId() == chatheadShown;
		showChatheadGlasses(chathead, swapped && !OWN_GLASSES.contains(chatheadWorn) ? config.glasses() : Glasses.NONE);
	}

	/**
	 * The game draws an NPC chathead from the NPC definition's chathead models and colour swaps, which the API
	 * hands out as the definition's own arrays, changed while the dialogue is open and put back after. Glasses
	 * and hats in CHATHEAD_EXTRAS become a copy of the first model; the base skin and hair colours take the
	 * player's (or the custom hair colour); built-in earrings take the hair colour. Swaps of colours the
	 * chathead doesn't use are dropped to make room; if there's still too little, the chathead is shown under
	 * CHATHEAD_CARRIER's definition, whose lists are longer.
	 *
	 * @return the NPC id to show the chathead as
	 */
	private int dressChathead(int npcId)
	{
		if (chatheadWorn == npcId)
		{
			return chatheadShown;
		}
		restoreChathead();
		chatheadWorn = npcId;
		chatheadShown = npcId;
		NPCComposition npc = client.getNpcDefinition(npcId);
		int[] own = npc == null ? null : npc.getChatheadModels();
		if (own == null || own.length == 0)
		{
			return npcId;
		}
		int[] models = own.clone();
		for (int i = 1; i < models.length; i++)
		{
			if (CHATHEAD_EXTRAS.contains(models[i]))
			{
				models[i] = models[0];
			}
		}

		Player player = client.getLocalPlayer();
		PlayerComposition composition = player == null ? null : player.getPlayerComposition();
		int skin = composition == null ? -1 : playerSkin(composition);
		int hair = composition == null ? -1 : hairColours(composition);
		short[] find = npc.getColorToReplace();
		short[] replace = npc.getColorToReplaceWith();
		int size = find == null || replace == null ? 0 : Math.min(find.length, replace.length);
		List<short[]> swaps = new ArrayList<>();
		for (int i = 0; i < size; i++)
		{
			short to = replace[i];
			if (find[i] == BASE_SKIN && skin != -1)
			{
				to = (short) skin;
			}
			else if (find[i] == BASE_HAIR && hair != -1)
			{
				to = (short) (hair >>> 16);
			}
			else if (find[i] == BASE_HAIR_SHADE && hair != -1)
			{
				to = (short) hair;
			}
			swaps.add(new short[]{find[i], to});
		}
		short earringColour = hair != -1 ? (short) (hair >>> 16) : BASE_HAIR;
		for (short[] swap : swaps)
		{
			if (swap[0] == BASE_HAIR)
			{
				earringColour = swap[1];
			}
		}
		int ownSwaps = swaps.size();
		for (int model : models)
		{
			short[] colours = CHATHEAD_EARRINGS.get(model);
			for (int i = 0; colours != null && i < colours.length; i++)
			{
				short[] swap = null;
				for (short[] existing : swaps)
				{
					swap = existing[0] == colours[i] ? existing : swap;
				}
				if (swap != null)
				{
					swap[1] = earringColour;
				}
				else
				{
					swaps.add(new short[]{colours[i], earringColour});
				}
			}
		}

		// Swaps of colours the chathead doesn't have (and no earlier swap makes) do nothing.
		Set<Short> used = new HashSet<>();
		boolean known = true;
		for (int model : models)
		{
			Set<Short> colours = chatheadColours.get(model);
			known &= colours != null;
			if (colours != null)
			{
				used.addAll(colours);
			}
		}
		if (known)
		{
			Set<Short> made = new HashSet<>(used);
			List<short[]> kept = new ArrayList<>();
			for (int i = 0; i < swaps.size(); i++)
			{
				short[] swap = swaps.get(i);
				if (made.contains(swap[0]))
				{
					kept.add(swap);
					made.add(swap[1]);
				}
				else if (i < ownSwaps)
				{
					ownSwaps--;
				}
			}
			swaps = kept;
		}

		NPCComposition target = npc;
		if (swaps.size() > size)
		{
			NPCComposition carrier = npcId == CHATHEAD_CARRIER ? null : client.getNpcDefinition(CHATHEAD_CARRIER);
			short[] carrierFind = carrier == null ? null : carrier.getColorToReplace();
			short[] carrierReplace = carrier == null ? null : carrier.getColorToReplaceWith();
			int[] carrierModels = carrier == null ? null : carrier.getChatheadModels();
			if (carrierFind != null && carrierReplace != null && carrierModels != null
				&& Math.min(carrierFind.length, carrierReplace.length) >= swaps.size() && carrierModels.length >= models.length)
			{
				target = carrier;
				chatheadShown = CHATHEAD_CARRIER;
			}
			else
			{
				// Not enough room for the earring: it keeps its own colours.
				log.debug("Barber Shop: no room to recolour the chathead earring of {}", npcId);
				swaps = swaps.subList(0, Math.min(ownSwaps, size));
			}
		}

		int[] targetModels = target.getChatheadModels();
		chatheadModelsOriginal.put(chatheadShown, targetModels.clone());
		save(targetModels);
		for (int i = 0; i < targetModels.length; i++)
		{
			targetModels[i] = i < models.length ? models[i] : models[0];
		}
		short[] targetFind = target.getColorToReplace();
		short[] targetReplace = target.getColorToReplaceWith();
		if (targetFind != null && targetReplace != null)
		{
			save(targetFind);
			save(targetReplace);
			for (int i = 0; i < Math.min(targetFind.length, targetReplace.length); i++)
			{
				// Unneeded swaps change a colour into itself.
				short[] swap = i < swaps.size() ? swaps.get(i) : new short[]{0, 0};
				targetFind[i] = swap[0];
				targetReplace[i] = swap[1];
			}
		}
		return chatheadShown;
	}

	private void save(short[] array)
	{
		short[] copy = array.clone();
		chatheadRestores.add(() -> System.arraycopy(copy, 0, array, 0, copy.length));
	}

	private void save(int[] array)
	{
		int[] copy = array.clone();
		chatheadRestores.add(() -> System.arraycopy(copy, 0, array, 0, copy.length));
	}

	/** Loads the colours of an NPC's chathead models, if not loaded yet. */
	private void loadChatheadColours(int npcId)
	{
		int[] models = chatheadModelsOriginal.get(npcId);
		if (models == null)
		{
			NPCComposition npc = client.getNpcDefinition(npcId);
			models = npc == null ? null : npc.getChatheadModels();
		}
		for (int i = 0; models != null && i < models.length; i++)
		{
			if (!chatheadColours.containsKey(models[i]))
			{
				ModelData data = client.loadModelData(models[i]);
				if (data != null)
				{
					Set<Short> colours = new HashSet<>();
					for (short colour : data.getFaceColors())
					{
						colours.add(colour);
					}
					chatheadColours.put(models[i], colours);
				}
			}
		}
	}

	/**
	 * Draws the glasses' own chathead model in a widget over the dialogue chathead, placed, turned and animated
	 * as it is. The widget goes with the dialogue when it closes.
	 */
	private void showChatheadGlasses(Widget chathead, Glasses glasses)
	{
		Widget parent = chathead.getParent();
		Widget[] children = parent == null ? null : parent.getDynamicChildren();
		if (chatGlasses != null && (children == null || !Arrays.asList(children).contains(chatGlasses)))
		{
			chatGlasses = null;
		}
		int model = glasses == Glasses.NONE ? -1 : chatheadModel(glasses.getNpcId(), glasses.getChatPiece());
		if (model == -1 || parent == null)
		{
			hideChatheadGlasses();
			return;
		}
		boolean created = chatGlasses == null;
		if (created)
		{
			chatGlasses = parent.createChild(WidgetType.MODEL);
		}
		chatGlasses.setModelType(WidgetModelType.MODEL);
		chatGlasses.setModelId(model);
		chatGlasses.setOriginalX(chathead.getOriginalX());
		chatGlasses.setOriginalY(chathead.getOriginalY());
		chatGlasses.setOriginalWidth(chathead.getOriginalWidth());
		chatGlasses.setOriginalHeight(chathead.getOriginalHeight());
		chatGlasses.setXPositionMode(chathead.getXPositionMode());
		chatGlasses.setYPositionMode(chathead.getYPositionMode());
		chatGlasses.setWidthMode(chathead.getWidthMode());
		chatGlasses.setHeightMode(chathead.getHeightMode());
		chatGlasses.setRotationX(chathead.getRotationX());
		chatGlasses.setRotationY(chathead.getRotationY());
		chatGlasses.setRotationZ(chathead.getRotationZ());
		chatGlasses.setModelZoom(chathead.getModelZoom());
		chatGlasses.setHidden(chathead.isSelfHidden());
		if (created || chatGlasses.getAnimationId() != chathead.getAnimationId())
		{
			// Started together so they stay in step.
			chathead.setAnimationId(chathead.getAnimationId());
			chatGlasses.setAnimationId(chathead.getAnimationId());
		}
		chatGlasses.revalidate();
	}

	private void hideChatheadGlasses()
	{
		if (chatGlasses != null)
		{
			chatGlasses.setHidden(true);
			chatGlasses = null;
		}
	}

	/** An NPC's chathead model, from its definition's own list even while that is changed for the dialogue. */
	private int chatheadModel(int npcId, int piece)
	{
		int[] models = chatheadModelsOriginal.get(npcId);
		if (models == null)
		{
			NPCComposition npc = client.getNpcDefinition(npcId);
			models = npc == null ? null : npc.getChatheadModels();
		}
		return models == null || piece < 0 || piece >= models.length ? -1 : models[piece];
	}

	/** Puts back the NPC definitions' own chathead models and colours after the player's dialogue. */
	private void restoreChathead()
	{
		hideChatheadGlasses();
		for (int i = chatheadRestores.size() - 1; i >= 0; i--)
		{
			chatheadRestores.get(i).run();
		}
		chatheadRestores.clear();
		chatheadModelsOriginal.clear();
		chatheadWorn = -1;
		chatheadShown = -1;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		tracking = false;

		Player player = client.getLocalPlayer();
		PlayerComposition composition = player == null ? null : player.getPlayerComposition();
		if (composition == null)
		{
			clear();
			preload();
			return;
		}
		// Each body type has its own list, as heads made for the other one don't fit.
		Head head = composition.getGender() == 0 ? config.headBodyTypeA() : config.headBodyTypeB();
		if (head.isNone())
		{
			clear();
			return;
		}

		if (config.keepHelmets() && hidesHair(composition))
		{
			clear();
			return;
		}
		int drop = HEAD_DROP + heightNudge;
		int ownSkin = playerSkin(composition);
		int ownSkinHueSat = ownSkin < 0 ? -1 : ownSkin >> 7;
		// The head always takes the player's skin colour, or the NPC's own if the player's isn't a known tone.
		int skin = ownSkin;
		int hair = hairColours(composition);
		Glasses glasses = OWN_GLASSES.contains(head.getNpcId()) ? Glasses.NONE : config.glasses();
		Earring earring = config.earring();
		EarringSide side = config.earringSide();
		int earringColour = config.customEarringColour() ? JagexColor.rgbToHSL(config.earringColour().getRGB(), 1.0) & 0xFFFF : -1;
		int glassesNudge = glassesNudgeDown * 1000 + glassesNudgeBack;
		if ((built != head || builtDrop != drop || builtSkin != skin || builtHair != hair || builtGlasses != glasses
			|| builtEarring != earring || builtSide != side || builtEarringColour != earringColour || builtGlassesNudge != glassesNudge)
			&& !build(head, drop, skin, hair, glasses, earring, side, earringColour))
		{
			return;
		}

		dressHead(composition);
		int now = Arrays.hashCode(composition.getEquipmentIds()) * 31 + Arrays.hashCode(composition.getColors());
		Model model = player.getModel();
		if (model == null)
		{
			return;
		}
		boolean retry = fit == null && failedTick != client.getTickCount();
		if (now != appearance || retry || hiddenColours != null && model.getFaceColors3() != hiddenColours)
		{
			// New look, the game rebuilt the model, or the last try failed: find and hide the head again.
			showOwnHead();
			appearance = now;
			if (!follow(player))
			{
				failedTick = client.getTickCount();
				hideObject();
				return;
			}
			model = player.getModel();
			if (model == null || model.getFaceCount() != ranges.getFaceCount())
			{
				fit = null;
				failedTick = client.getTickCount();
				hideObject();
				return;
			}
			hideOwnHead(model, ownSkinHueSat);
			skinMatched = false;
		}
		if (fit == null)
		{
			hideObject();
			return;
		}

		int cut = NO_NECK_CUT.contains(head.getNpcId()) ? 0 : neckCut;
		if (!skinMatched || cutAt != cut)
		{
			if (cutAt != cut)
			{
				cutAt = cut;
				showOwnHead();
				hideOwnHead(model, ownSkinHueSat);
			}
			matchSkinBrightness(model);
			hideNeckStub();
			skinMatched = true;
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
		int own = averageLightness(player.getFaceColors1(), player.getFaceColors2(), null, hueSat, ranges, false);
		int head = averageLightness(c1, c2, c3, hueSat, null, true);
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
	 * With ranges, only faces inside them (inside true) or outside them (inside false); with c3, only
	 * faces that are drawn.
	 */
	private static int averageLightness(int[] c1, int[] c2, int[] c3, int hueSat, HeadFaces.Ranges ranges, boolean inside)
	{
		long total = 0;
		int count = 0;
		for (int face = 0; face < c1.length; face++)
		{
			if (ranges != null && ranges.contains(face) != inside || c3 != null && c3[face] == -2)
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
	 * Asks for the chosen heads' and add-ons' models before the player appears, such as on the login
	 * screen. loadModelData gives nothing while a model is still loading, so a head first built at login
	 * would otherwise leave the player's own head showing for a moment.
	 */
	private void preload()
	{
		String wanted = config.headBodyTypeA() + "/" + config.headBodyTypeB() + "/" + config.glasses() + "/" + config.earring();
		if (wanted.equals(preloaded))
		{
			return;
		}
		boolean ready = true;
		for (Head head : new Head[]{config.headBodyTypeA(), config.headBodyTypeB()})
		{
			for (int piece : head.isNone() ? new int[0] : head.getPieces())
			{
				ready &= loadPiece(head.getNpcId(), piece) != null;
			}
		}
		Glasses glasses = config.glasses();
		if (glasses != Glasses.NONE)
		{
			ready &= loadPiece(glasses.getNpcId(), glasses.getPiece()) != null;
		}
		Earring earring = config.earring();
		if (earring != Earring.NONE)
		{
			ready &= loadPiece(earring.getNpcId(), earring.getPiece()) != null;
		}
		if (ready)
		{
			preloaded = wanted;
		}
	}

	/** Which faces of a model piece are the earring's, by its colours. */
	private static boolean[] earringFaces(ModelData piece, Earring earring)
	{
		return earringFaces(piece, earring.getColours());
	}

	/** Which faces of a model piece are in the given colours. */
	private static boolean[] earringFaces(ModelData piece, short[] wanted)
	{
		short[] colours = piece.getFaceColors();
		boolean[] faces = new boolean[piece.getFaceCount()];
		for (int face = 0; face < faces.length; face++)
		{
			for (short colour : wanted)
			{
				faces[face] |= colours[face] == colour;
			}
		}
		return faces;
	}

	/** Of the given faces, only those on the right (positive x) or left side of the face. */
	private static boolean[] onSide(ModelData piece, boolean[] faces, boolean right)
	{
		float[] x = piece.getVerticesX();
		int[] a = piece.getFaceIndices1();
		int[] b = piece.getFaceIndices2();
		int[] c = piece.getFaceIndices3();
		boolean[] kept = faces.clone();
		for (int face = 0; face < kept.length; face++)
		{
			kept[face] &= (x[a[face]] + x[b[face]] + x[c[face]] > 0) == right;
		}
		return kept;
	}

	/**
	 * A copy of the earring's piece, in its NPC's colours or recoloured to the custom colour (each shade
	 * keeping its lightness relative to the others), mirrored across the face if asked.
	 *
	 * @param custom the custom colour, or -1 for the NPC's own
	 */
	private ModelData earringCopy(ModelData source, Earring earring, int custom, boolean mirror)
	{
		ModelData copy = source.shallowCopy().cloneColors().cloneVertices();
		float[] x = copy.getVerticesX();
		NPCComposition npc = client.getNpcDefinition(earring.getNpcId());
		short[] find = npc == null ? null : npc.getColorToReplace();
		short[] replace = npc == null ? null : npc.getColorToReplaceWith();
		short[] worn = earring.getColours().clone();
		for (int i = 0; i < worn.length; i++)
		{
			for (int k = 0; find != null && replace != null && k < find.length && k < replace.length; k++)
			{
				if (find[k] == worn[i])
				{
					worn[i] = replace[k];
				}
			}
			copy.recolor(earring.getColours()[i], worn[i]);
		}
		if (custom != -1)
		{
			short pick = (short) custom;
			int total = 0;
			for (short colour : worn)
			{
				total += JagexColor.unpackLuminance(colour);
			}
			int shift = JagexColor.unpackLuminance(pick) - total / Math.max(1, worn.length);
			for (short colour : worn)
			{
				int luminance = Math.max(0, Math.min(JagexColor.LUMINANCE_MAX, JagexColor.unpackLuminance(colour) + shift));
				copy.recolor(colour, JagexColor.packHSL(JagexColor.unpackHue(pick), JagexColor.unpackSaturation(pick), luminance));
			}
		}
		if (mirror)
		{
			for (int i = 0; i < copy.getVerticesCount(); i++)
			{
				x[i] = -x[i];
			}
		}
		return copy;
	}

	/**
	 * The faces of any earring built into the head's pieces, found by model, across its merged pieces;
	 * null if it has none.
	 */
	private boolean[] ownEarringFaces(int[] partModels, ModelData[] parts)
	{
		boolean[] hide = null;
		int start = 0;
		for (int i = 0; i < parts.length; i++)
		{
			int count = parts[i].getFaceCount();
			List<Object[]> earrings = new ArrayList<>(Arrays.asList(OTHER_EARRINGS));
			for (Earring earring : Earring.values())
			{
				if (earring != Earring.NONE)
				{
					earrings.add(new Object[]{earring.getNpcId(), earring.getPiece(), earring.getColours()});
				}
			}
			for (Object[] earring : earrings)
			{
				if (pieceModel((int) earring[0], (int) earring[1]) != partModels[i])
				{
					continue;
				}
				if (hide == null || hide.length < start + count)
				{
					hide = hide == null ? new boolean[start + count] : Arrays.copyOf(hide, start + count);
				}
				boolean[] faces = earringFaces(parts[i], (short[]) earring[2]);
				for (int face = 0; face < count; face++)
				{
					hide[start + face] |= faces[face];
				}
			}
			start += count;
		}
		return hide;
	}

	/**
	 * Shrinks the marked faces to a single point. The point is a vertex no shown face uses: each face still
	 * counts towards the shading of its corners, even with no size, which flattened shown faces sharing them.
	 */
	private static void shrinkFaces(ModelData data, boolean[] hidden)
	{
		int[] a = data.getFaceIndices1();
		int[] b = data.getFaceIndices2();
		int[] c = data.getFaceIndices3();
		int faces = Math.min(hidden.length, Math.min(a.length, data.getFaceCount()));
		boolean[] used = new boolean[data.getVerticesCount()];
		for (int face = 0; face < faces; face++)
		{
			if (!hidden[face])
			{
				used[a[face]] = true;
				used[b[face]] = true;
				used[c[face]] = true;
			}
		}
		int spare = 0;
		while (spare < used.length && used[spare])
		{
			spare++;
		}
		for (int face = 0; face < faces; face++)
		{
			if (hidden[face])
			{
				int point = spare < used.length ? spare : a[face];
				a[face] = point;
				b[face] = point;
				c[face] = point;
			}
		}
	}

	/** The model id of one of an NPC's pieces, or -1 if it isn't available. */
	private int pieceModel(int npcId, int piece)
	{
		NPCComposition npc = client.getNpcDefinition(npcId);
		int[] models = npc == null ? null : npc.getModels();
		return models == null || models.length <= piece ? -1 : models[piece];
	}

	/** One model piece of an NPC, as the NPC wears it, or null if it isn't available. */
	private ModelData loadPiece(int npcId, int piece)
	{
		NPCComposition npc = client.getNpcDefinition(npcId);
		int[] models = npc == null ? null : npc.getModels();
		return models == null || models.length <= piece ? null : client.loadModelData(models[piece]);
	}

	/**
	 * The hair colours to give the head, main colour and shade packed into one int, or -1 to keep the
	 * NPC's own.
	 */
	private int hairColours(PlayerComposition composition)
	{
		switch (config.hairColour())
		{
			case NPC:
				return -1;
			case CUSTOM:
				short main = JagexColor.rgbToHSL(config.customHairColour().getRGB(), 1.0);
				short shade = JagexColor.packHSL(JagexColor.unpackHue(main),
					Math.max(0, JagexColor.unpackSaturation(main) + SHADE_SATURATION),
					Math.min(JagexColor.LUMINANCE_MAX, JagexColor.unpackLuminance(main) + SHADE_LUMINANCE));
				return packHair(main, shade);
			default:
				int setting = playerHair(composition);
				return setting == -1 ? -1 : packHair(HAIR_TONES[setting], HAIR_SHADES[setting]);
		}
	}

	private static int packHair(short main, short shade)
	{
		return (main & 0xFFFF) << 16 | shade & 0xFFFF;
	}

	/** The player's hair colour setting, or -1 for one outside the game's hair colour lists. */
	private static int playerHair(PlayerComposition composition)
	{
		int[] colours = composition.getColors();
		int setting = colours == null || colours.length <= HAIR_SLOT ? -1 : colours[HAIR_SLOT];
		return setting >= 0 && setting < HAIR_TONES.length && setting < HAIR_SHADES.length ? setting : -1;
	}

	/** The player's skin colour, or -1 for one outside the game's skin tone list. */
	static int playerSkin(PlayerComposition composition)
	{
		int[] colours = composition.getColors();
		int tone = colours == null || colours.length <= SKIN_SLOT ? -1 : colours[SKIN_SLOT];
		return tone >= 0 && tone < SKIN_TONES.length ? SKIN_TONES[tone] & 0xFFFF : -1;
	}

	/**
	 * Loads the NPC's head pieces, recoloured as the NPC wears them.
	 *
	 * @param skin the player's skin colour to give the head, or -1 to keep the NPC's own
	 * @param hair the hair colours to give the head (see hairColours), or -1 to keep the NPC's own
	 * @param glasses the glasses to add
	 * @param earring the earring to add
	 * @param side which ear or ears it goes on
	 * @param earringColour the earring's custom colour, or -1 for its own
	 */
	private boolean build(Head head, int drop, int skin, int hair, Glasses glasses, Earring earring,
		EarringSide side, int earringColour)
	{
		NPCComposition npc = client.getNpcDefinition(head.getNpcId());
		int[] models = npc == null ? null : npc.getModels();
		if (models == null)
		{
			return false;
		}
		// A head's own glasses and hat are left out and its own earring hidden; only the Glasses and
		// Earring settings add glasses and earrings.
		Set<Integer> glassesModels = new HashSet<>();
		for (Glasses pair : Glasses.values())
		{
			if (pair != Glasses.NONE)
			{
				glassesModels.add(pieceModel(pair.getNpcId(), pair.getPiece()));
			}
		}
		List<ModelData> loaded = new ArrayList<>();
		List<Integer> loadedModels = new ArrayList<>();
		for (int piece : head.getPieces())
		{
			if (piece >= models.length)
			{
				return false;
			}
			Integer hat = HAT_PIECES.get(head.getNpcId());
			if (glassesModels.contains(models[piece]) && !OWN_GLASSES.contains(head.getNpcId()) || hat != null && hat == piece)
			{
				continue;
			}
			ModelData part = client.loadModelData(models[piece]);
			if (part == null)
			{
				log.debug("Barber Shop: model piece {} of {} not loaded", piece, head);
				return false;
			}
			loaded.add(part);
			loadedModels.add(models[piece]);
		}
		ModelData[] parts = loaded.toArray(new ModelData[0]);
		int[] partModels = loadedModels.stream().mapToInt(Integer::intValue).toArray();
		// hideFaces marks faces to hide, which keep their place at the start of the merged model.
		boolean[] hideFaces = ownEarringFaces(partModels, parts);
		// With a see-through value per face, made if the pieces have none, so animations can fade it.
		ModelData data = client.mergeModels(parts).cloneColors().cloneTransparencies(true);
		short[] find = npc.getColorToReplace();
		short[] replace = npc.getColorToReplaceWith();
		if (find != null && replace != null)
		{
			for (int i = 0; i < find.length && i < replace.length; i++)
			{
				// The NPC's own skin and hair colours are left out when others go on instead.
				boolean playerSkin = skin != -1 && find[i] == BASE_SKIN;
				boolean playerHair = hair != -1 && (find[i] == BASE_HAIR || find[i] == BASE_HAIR_SHADE);
				if (!playerSkin && !playerHair)
				{
					data.recolor(find[i], replace[i]);
				}
			}
		}
		if (hair != -1)
		{
			// Faces still on the base hair colours are hair or beard.
			data.recolor(BASE_HAIR, (short) (hair >>> 16));
			data.recolor(BASE_HAIR_SHADE, (short) hair);
		}
		if (skin != -1)
		{
			// Faces still on the base skin colour are skin, including any an NPC swaps onto it.
			data.recolor(BASE_SKIN, (short) skin);
		}
		headSkinHueSat = (skin != -1 ? skin : npcSkin(find, replace)) >> 7;
		// Added after the head's colour swaps so they can't change the glasses.
		if (glasses != Glasses.NONE)
		{
			ModelData worn = loadPiece(glasses.getNpcId(), glasses.getPiece());
			if (worn != null)
			{
				// Moved on a copy, leaving the game's cached model alone.
				boolean bodyB = head instanceof BodyTypeBHead;
				int down = (bodyB ? glasses.getDownB() : glasses.getDownA()) + glassesNudgeDown;
				int back = (bodyB ? glasses.getBackB() : glasses.getBackA()) + glassesNudgeBack;
				if (down != 0 || back != 0)
				{
					worn = worn.shallowCopy().cloneVertices().translate(0, down, back);
				}
				data = client.mergeModels(data, worn).cloneTransparencies(true);
			}
		}
		// The earring comes with the rest of its NPC's head, so it goes on last and all but its own faces are
		// hidden once lit; keepEarring marks them, in the order they end the merged model. A left single
		// earring is a copy mirrored across the face; mirrorFaces marks its faces, whose corners are put
		// back in drawing order before lighting, as mirroring turns faces inside out.
		boolean[] keepEarring = null;
		boolean[] mirrorFaces = null;
		ModelData source = earring != Earring.NONE ? loadPiece(earring.getNpcId(), earring.getPiece()) : null;
		if (source != null)
		{
			boolean[] own = earringFaces(source, earring);
			List<ModelData> copies = new ArrayList<>();
			List<boolean[]> keeps = new ArrayList<>();
			List<Boolean> mirrored = new ArrayList<>();
			if (earring.isPair())
			{
				// One of the pair per side, by which side of the face each face is on.
				if (side != EarringSide.BOTH)
				{
					own = onSide(source, own, side == EarringSide.RIGHT);
				}
				copies.add(earringCopy(source, earring, earringColour, false));
				keeps.add(own);
				mirrored.add(false);
			}
			else
			{
				if (side != EarringSide.LEFT)
				{
					copies.add(earringCopy(source, earring, earringColour, false));
					keeps.add(own);
					mirrored.add(false);
				}
				if (side != EarringSide.RIGHT)
				{
					copies.add(earringCopy(source, earring, earringColour, true));
					keeps.add(own);
					mirrored.add(true);
				}
			}
			int count = 0;
			for (boolean[] keep : keeps)
			{
				count += keep.length;
			}
			keepEarring = new boolean[count];
			mirrorFaces = new boolean[count];
			int at = 0;
			for (int i = 0; i < copies.size(); i++)
			{
				boolean[] keep = keeps.get(i);
				System.arraycopy(keep, 0, keepEarring, at, keep.length);
				if (mirrored.get(i))
				{
					Arrays.fill(mirrorFaces, at, at + keep.length, true);
				}
				at += keep.length;
				data = client.mergeModels(data, copies.get(i)).cloneTransparencies(true);
			}
			// Mirrored faces are turned the right way out before lighting, in the merged model's own face
			// lists (the copy's are shared with the game's cached model), or they'd be shaded inside out.
			int[] b = data.getFaceIndices2();
			int[] c = data.getFaceIndices3();
			int start = data.getFaceCount() - mirrorFaces.length;
			for (int face = 0; face < mirrorFaces.length && start >= 0; face++)
			{
				if (mirrorFaces[face])
				{
					int corner = b[start + face];
					b[start + face] = c[start + face];
					c[start + face] = corner;
				}
			}
		}
		// Faces to be hidden are first shrunk to a point: a hidden face still counts towards the shading of
		// the vertices it shares, which darkened or flattened the visible faces around it.
		boolean[] hidden = new boolean[data.getFaceCount()];
		for (int face = 0; hideFaces != null && face < hideFaces.length && face < hidden.length; face++)
		{
			hidden[face] = hideFaces[face];
		}
		if (keepEarring != null)
		{
			int start = hidden.length - keepEarring.length;
			for (int face = 0; face < keepEarring.length && start >= 0; face++)
			{
				hidden[start + face] |= !keepEarring[face];
			}
		}
		shrinkFaces(data, hidden);
		// Lit as the game lights characters, not as items on the ground or icons.
		Model model = data.light(AMBIENT, CONTRAST, LIGHT_X, LIGHT_Y, LIGHT_Z);
		if (model == null)
		{
			return false;
		}
		if (hideFaces != null)
		{
			int[] c3 = model.getFaceColors3();
			for (int face = 0; face < hideFaces.length && face < c3.length; face++)
			{
				if (hideFaces[face])
				{
					c3[face] = -2;
				}
			}
		}
		if (keepEarring != null)
		{
			int[] c3 = model.getFaceColors3();
			int start = model.getFaceCount() - keepEarring.length;
			for (int face = 0; face < keepEarring.length && start >= 0; face++)
			{
				if (!keepEarring[face])
				{
					c3[start + face] = -2;
				}
			}
		}
		int count = model.getVerticesCount();
		headRestX = Arrays.copyOf(model.getVerticesX(), count);
		headRestY = Arrays.copyOf(model.getVerticesY(), count);
		builtDrop = drop;
		builtSkin = skin;
		builtHair = hair;
		builtGlasses = glasses;
		builtEarring = earring;
		builtSide = side;
		builtEarringColour = earringColour;
		builtGlassesNudge = glassesNudgeDown * 1000 + glassesNudgeBack;
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
		ownRestY = rest.getVerticesY().clone();
		int[] list = vertices.stream().mapToInt(Integer::intValue).toArray();
		fit = HeadFit.of(list, rest.getVerticesX(), rest.getVerticesY(), rest.getVerticesZ());
		return fit != null;
	}

	/**
	 * Whether the player wears headgear that hides their hair: the game then sends their look with the
	 * hair slot empty. Also true once dressHead has swapped such headgear for a stand-in hairstyle.
	 */
	private boolean hidesHair(PlayerComposition composition)
	{
		int[] ids = composition.getEquipmentIds();
		return addedHair != 0
			|| ids[KitType.HEAD.getIndex()] >= PlayerComposition.ITEM_OFFSET && ids[KitType.HAIR.getIndex()] == 0;
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
			// Taken out of the scene at the start of the next frame; not here, as this may run while it's drawn.
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
		tracking = false;
		headObject = null;
		headModel = null;
		built = null;
		fit = null;
		ranges = null;
		appearance = 0;
		failedTick = -1;
	}

	@Provides
	BarberShopConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BarberShopConfig.class);
	}
}
