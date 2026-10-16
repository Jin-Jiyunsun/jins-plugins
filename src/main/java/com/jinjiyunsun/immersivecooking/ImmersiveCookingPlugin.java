package com.jinjiyunsun.immersivecooking;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Immersive Cooking",
	description = "Shows the fish you're cooking sitting on the range while it cooks"
)
public class ImmersiveCookingPlugin extends Plugin
{
	// How far above the range the fish model hovers, in local-coordinate height units.
	private static final int HOVER_HEIGHT = 120;

	// Nudges the fish east of the range's clicked tile, to sit over the cooking surface.
	private static final int EAST_OFFSET = 25;

	// Model scale is fixed-point with 128 as 100%; 96 is 75%.
	private static final int MODEL_SCALE = 96;

	// How long the cooked/burnt result shows before the range clears, in game ticks.
	private static final int RESULT_DISPLAY_TICKS = 2;

	// How long the range sits empty between raw -> result and result -> next raw, in game ticks.
	private static final int GAP_TICKS = 1;

	// Raw fish -> cooked fish. Covers every fish in RAW_FISH_ITEM_IDS.
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
		Map.entry(ItemID.TBWT_RAW_KARAMBWAN, ItemID.TBWT_COOKED_KARAMBWAN)
	);

	// Raw fish -> burnt fish. Only the fish with their own distinct burnt item are covered;
	// the rest share a handful of generic "Burnt fish" items that can't be told apart by name,
	// so those just clear the range without a burnt model (see onChatMessage).
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
		Map.entry(ItemID.TBWT_RAW_KARAMBWAN, ItemID.TBWT_BURNT_KARAMBWAN)
	);

	private static final Set<Integer> RAW_FISH_ITEM_IDS = COOKED_BY_RAW.keySet();

	@Inject
	private Client client;

	private RuneLiteObject fishObject;

	// Set when a raw fish is used on a range; stays set for the whole batch.
	private int pendingCookItemId = -1;
	private LocalPoint pendingCookLocation;

	// Item id of whichever model is currently loaded on fishObject, or -1 if none.
	private int displayedItemId = -1;

	// Game tick at which the cooked/burnt result should be cleared, or -1 if nothing pending.
	private int hideAtTick = -1;

	// Game tick at which pendingShowItemId should appear, or -1 if nothing scheduled. When it's
	// a result (not the raw fish for the next item), pendingShowIsResult says so, since a result
	// reveal needs its own hide timer afterward and a raw reveal doesn't.
	private int showAtTick = -1;
	private int pendingShowItemId = -1;
	private boolean pendingShowIsResult = false;

	// Item id currently previewed from hovering the "how many" dialog, or -1 if none.
	private int hoverPreviewItemId = -1;

	// Lazily-built, cached: lowercase raw fish name -> raw fish item id.
	private Map<String, Integer> rawFishByName;

	// Last cook-result chat message processed, and the tick it arrived on - for de-duplication.
	private String lastChatMessage = "";
	private int lastChatMessageTick = -1;

	@Override
	protected void startUp()
	{
		fishObject = client.createRuneLiteObject();
	}

	@Override
	protected void shutDown()
	{
		fishObject.setActive(false);
		fishObject = null;
		pendingCookLocation = null;
		pendingCookItemId = -1;
		displayedItemId = -1;
		hideAtTick = -1;
		showAtTick = -1;
		pendingShowItemId = -1;
		pendingShowIsResult = false;
		hoverPreviewItemId = -1;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		Widget clickedWidget = event.getWidget();
		log.debug("Menu option clicked: action={}, option={}, widgetId={}, widgetItemId={}, selectedWidgetItemId={}",
			action, event.getMenuOption(), event.getWidgetId(),
			clickedWidget != null ? clickedWidget.getItemId() : null,
			client.getSelectedWidget() != null ? client.getSelectedWidget().getItemId() : null);

		// Raw fish used directly on the range.
		if (action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			Widget selectedWidget = client.getSelectedWidget();
			int itemId = selectedWidget != null ? selectedWidget.getItemId() : -1;
			if (RAW_FISH_ITEM_IDS.contains(itemId))
			{
				pendingCookItemId = itemId;
				pendingCookLocation = LocalPoint.fromScene(event.getParam0(), event.getParam1());
				showModel(itemId, pendingCookLocation);
			}
			return;
		}

		// "Cook" clicked directly on the range: we know where, but not what yet - that's
		// resolved once the player hovers and clicks a food choice in the dialog below.
		boolean isGameObjectOption = action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
		if (isGameObjectOption && "Cook".equalsIgnoreCase(event.getMenuOption()))
		{
			pendingCookLocation = LocalPoint.fromScene(event.getParam0(), event.getParam1());
			log.debug("Cook clicked directly on range at scene {},{}", event.getParam0(), event.getParam1());
			return;
		}

		// Any other click in the "how many would you like to make" dialog - whether on a food
		// icon or a quantity button - commits to whatever was just hovered and previewed, since
		// the widgets involved don't carry a usable item id of their own to read directly.
		if (pendingCookItemId != -1 || pendingCookLocation == null)
		{
			return;
		}

		if ("Cancel".equalsIgnoreCase(event.getMenuOption()))
		{
			// Dialog cancelled before anything was confirmed - nothing left to show on the range.
			clearCooking();
			return;
		}

		if (hoverPreviewItemId != -1)
		{
			pendingCookItemId = hoverPreviewItemId;
			log.debug("Confirmed via click while dialog open, itemId={}", pendingCookItemId);
			showModel(pendingCookItemId, pendingCookLocation);
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (pendingCookLocation == null || pendingCookItemId != -1)
		{
			// Hover preview only matters before a choice is confirmed. Once cooking has started,
			// stray mouse movement over unrelated "Cook <fish>" text shouldn't reinterrupt it.
			return;
		}

		int hoveredItemId = findHoveredFishSlot();
		if (hoveredItemId == hoverPreviewItemId)
		{
			return;
		}

		hoverPreviewItemId = hoveredItemId;
		if (hoveredItemId != -1)
		{
			showModel(hoveredItemId, pendingCookLocation);
		}
		else
		{
			// Nothing hovered and nothing confirmed yet - no preview to show.
			fishObject.setActive(false);
			displayedItemId = -1;
		}
	}

	// The dialog's food icons don't carry a usable item id on their widgets. But RuneLite's own
	// "Cook <name>" action-text readout (top-left of the game view) is built from the same menu
	// entry that a left-click would fire, kept live as the mouse moves - so read that instead.
	private String lastLoggedMenuState = "";

	private int findHoveredFishSlot()
	{
		MenuEntry[] entries = client.getMenuEntries();
		String state = entries.length == 0 ? "empty"
			: "count=" + entries.length + " top=[" + entries[entries.length - 1].getOption()
				+ " | " + entries[entries.length - 1].getTarget() + "]";
		if (!state.equals(lastLoggedMenuState))
		{
			lastLoggedMenuState = state;
			log.debug("Menu entries while dialog open: {}", state);
		}

		if (entries.length == 0)
		{
			return -1;
		}

		MenuEntry top = entries[entries.length - 1];
		if (!"Cook".equalsIgnoreCase(top.getOption()))
		{
			return -1;
		}

		String target = Text.removeTags(top.getTarget()).toLowerCase();
		return rawFishByName().getOrDefault(target, -1);
	}

	private Map<String, Integer> rawFishByName()
	{
		if (rawFishByName == null)
		{
			rawFishByName = new HashMap<>();
			for (int rawItemId : RAW_FISH_ITEM_IDS)
			{
				// The cooking dialog shows "Shark", not the item's full name "Raw shark".
				String name = client.getItemDefinition(rawItemId).getName().toLowerCase();
				rawFishByName.put(name, rawItemId);
				if (name.startsWith("raw "))
				{
					rawFishByName.put(name.substring(4), rawItemId);
				}

				// Karambwan is labelled by its result name ("Cooked karambwan") instead of its
				// raw name - index that too, in case other fish turn out to do the same.
				Integer cookedId = COOKED_BY_RAW.get(rawItemId);
				if (cookedId != null)
				{
					rawFishByName.put(client.getItemDefinition(cookedId).getName().toLowerCase(), rawItemId);
				}
			}
		}
		return rawFishByName;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		int animation = actor.getAnimation();
		if (animation != AnimationID.HUMAN_COOKING && animation != AnimationID.HUMAN_COOKING_LOOP)
		{
			clearCooking();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (pendingCookItemId == -1)
		{
			return;
		}

		String message = event.getMessage().toLowerCase();
		log.debug("Chat message during cook: type={}, message={}", event.getType(), message);

		// RuneLite can occasionally deliver the same chat line to this handler twice on the
		// same tick (observed right at the start of a batch) - without this it eats the next
		// raw-fish slot by replaying the same result a second time.
		int tick = client.getTickCount();
		if (message.equals(lastChatMessage) && tick == lastChatMessageTick)
		{
			return;
		}
		lastChatMessage = message;
		lastChatMessageTick = tick;

		if (message.contains("haven't got anything to cook"))
		{
			// Out of raw fish: "You haven't got anything to cook." This is a MESBOX popup, not
			// a chatbox line, but it still comes through as a ChatMessage event. It contains
			// "cook" too, so it must be checked before the result parsing below or it gets
			// misread as a successful cook.
			clearCooking();
			return;
		}

		Integer resultItemId;
		if (message.contains("burn"))
		{
			resultItemId = BURNT_BY_RAW.get(pendingCookItemId);
		}
		else if (message.contains("cook"))
		{
			resultItemId = COOKED_BY_RAW.get(pendingCookItemId);
		}
		else
		{
			return;
		}

		if (resultItemId != null)
		{
			if (displayedItemId == pendingCookItemId)
			{
				// Normal case: the raw fish is currently shown, swap to the result immediately.
				showModel(resultItemId, pendingCookLocation);
				hideAtTick = client.getTickCount() + RESULT_DISPLAY_TICKS;
			}
			else
			{
				// This result arrived before the raw fish ever got shown for it (a previous
				// result's display/gap cycle is still playing out) - show the raw fish now for
				// a beat first, so the rhythm doesn't skip straight from one result to the next.
				showModel(pendingCookItemId, pendingCookLocation);
				pendingShowItemId = resultItemId;
				pendingShowIsResult = true;
				showAtTick = client.getTickCount() + 1;
			}
		}
		else
		{
			// No distinct burnt model for this fish - clear the range and gap straight to the next raw fish.
			fishObject.setActive(false);
			displayedItemId = -1;
			scheduleNextRaw();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();

		if (showAtTick != -1 && tick >= showAtTick)
		{
			showAtTick = -1;
			if (pendingShowIsResult)
			{
				// A result that was deferred by a tick in onChatMessage to let the raw fish get
				// a beat on screen first.
				showModel(pendingShowItemId, pendingCookLocation);
				hideAtTick = tick + RESULT_DISPLAY_TICKS;
			}
			else if (hasRawFishLeft())
			{
				// Reveals the raw fish for the next item - it stays shown with no auto-hide
				// timer until the next chat-message result comes in.
				showModel(pendingShowItemId, pendingCookLocation);
			}
			else
			{
				// No more of this raw fish in the inventory - nothing left to cook. The cook
				// animation itself is unreliable for this: it keeps playing well after the
				// last real attempt has already resolved.
				clearCooking();
			}
		}

		if (hideAtTick != -1 && tick >= hideAtTick)
		{
			hideAtTick = -1;
			fishObject.setActive(false);
			displayedItemId = -1;
			scheduleNextRaw();
		}
	}

	// Called once a cooked/burnt result has been cleared from the range. Schedules the raw fish
	// to reappear after a gap; whether there's actually another one to cook is checked later,
	// when that reveal fires (see hasRawFishLeft()).
	private void scheduleNextRaw()
	{
		pendingShowItemId = pendingCookItemId;
		pendingShowIsResult = false;
		showAtTick = client.getTickCount() + GAP_TICKS;
	}

	// The cook animation keeps playing well after the last real attempt has resolved, so it
	// can't tell us whether the batch is over. The inventory can: if there's none of this raw
	// fish left, there's nothing more to cook, no matter what the animation is still doing.
	private boolean hasRawFishLeft()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		return inventory != null && inventory.count(pendingCookItemId) > 0;
	}

	private void clearCooking()
	{
		fishObject.setActive(false);
		displayedItemId = -1;
		pendingCookItemId = -1;
		pendingCookLocation = null;
		hideAtTick = -1;
		showAtTick = -1;
		pendingShowItemId = -1;
		pendingShowIsResult = false;
	}

	private void showModel(int itemId, LocalPoint location)
	{
		log.debug("showModel: itemId={}, location={}", itemId, location);
		hideAtTick = -1;
		showAtTick = -1;
		if (displayedItemId != itemId)
		{
			fishObject.setModel(buildItemModel(itemId));
			displayedItemId = itemId;
		}

		fishObject.setLocation(location.dx(EAST_OFFSET), client.getPlane());
		fishObject.setZ(fishObject.getZ() - HOVER_HEIGHT);
		fishObject.setActive(true);
	}

	private Model buildItemModel(int itemId)
	{
		ItemComposition itemComposition = client.getItemDefinition(itemId);
		// loadModelData can return a cached, shared instance - clone before mutating it, or
		// recolor/retexture/scale would compound on every subsequent build of the same model.
		// Only clone colors/textures when we're actually about to replace them: models with no
		// texture data (most fish) have a null texture array, and cloning a null array throws.
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

		return modelData.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE).light();
	}
}
