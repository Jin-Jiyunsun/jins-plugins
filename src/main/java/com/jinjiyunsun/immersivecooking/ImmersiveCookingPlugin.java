package com.jinjiyunsun.immersivecooking;

import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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

	// Fishing-skill raw fish cooked at a range; meats/other food are out of scope for now.
	private static final Set<Integer> RAW_FISH_ITEM_IDS = Set.of(
		ItemID.RAW_SHRIMP, ItemID.RAW_ANCHOVIES, ItemID.RAW_SARDINE, ItemID.RAW_SALMON,
		ItemID.RAW_TROUT, ItemID.RAW_GIANT_CARP, ItemID.RAW_COD, ItemID.RAW_HERRING,
		ItemID.RAW_PIKE, ItemID.RAW_MACKEREL, ItemID.RAW_TUNA, ItemID.RAW_BASS,
		ItemID.RAW_SWORDFISH, ItemID.RAW_LOBSTER, ItemID.RAW_SHARK, ItemID.RAW_MANTARAY,
		ItemID.RAW_SEATURTLE, ItemID.RAW_LAVA_EEL, ItemID.RAW_CAVE_EEL, ItemID.RAW_MONKFISH,
		ItemID.RAW_DARK_CRAB, ItemID.RAW_ANGLERFISH, ItemID.RAW_GUPPY, ItemID.RAW_CAVEFISH,
		ItemID.RAW_TETRA, ItemID.RAW_CATFISH, ItemID.RAW_SWORDTIP_SQUID, ItemID.RAW_JUMBO_SQUID,
		ItemID.RAW_GIANT_KRILL, ItemID.RAW_HADDOCK, ItemID.RAW_YELLOWFIN, ItemID.RAW_HALIBUT,
		ItemID.RAW_BLUEFIN, ItemID.RAW_MARLIN, ItemID.TBWT_RAW_KARAMBWAN
	);

	@Inject
	private Client client;

	private RuneLiteObject fishObject;

	// Set when a raw fish is used on a range; consumed once the cooking animation starts.
	private int pendingCookItemId = -1;
	private LocalPoint pendingCookLocation;

	private int cachedModelItemId = -1;

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
		cachedModelItemId = -1;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			return;
		}

		Widget selectedWidget = client.getSelectedWidget();
		int itemId = selectedWidget != null ? selectedWidget.getItemId() : -1;
		log.debug("Widget target on game object: selectedWidgetItemId={}, param0={}, param1={}",
			itemId, event.getParam0(), event.getParam1());

		if (RAW_FISH_ITEM_IDS.contains(itemId))
		{
			pendingCookItemId = itemId;
			pendingCookLocation = LocalPoint.fromScene(event.getParam0(), event.getParam1());
		}
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
			fishObject.setActive(false);
			return;
		}

		if (pendingCookItemId == -1 || pendingCookLocation == null)
		{
			return;
		}

		if (cachedModelItemId != pendingCookItemId)
		{
			fishObject.setModel(buildItemModel(pendingCookItemId));
			cachedModelItemId = pendingCookItemId;
		}

		fishObject.setLocation(pendingCookLocation.dx(EAST_OFFSET), client.getPlane());
		fishObject.setZ(fishObject.getZ() - HOVER_HEIGHT);
		fishObject.setActive(true);
	}

	private Model buildItemModel(int itemId)
	{
		ItemComposition itemComposition = client.getItemDefinition(itemId);
		ModelData modelData = client.loadModelData(itemComposition.getInventoryModel());

		short[] colorToReplace = itemComposition.getColorToReplace();
		short[] colorToReplaceWith = itemComposition.getColorToReplaceWith();
		if (colorToReplace != null)
		{
			for (int i = 0; i < colorToReplace.length; i++)
			{
				modelData = modelData.recolor(colorToReplace[i], colorToReplaceWith[i]);
			}
		}

		short[] textureToReplace = itemComposition.getTextureToReplace();
		short[] textureToReplaceWith = itemComposition.getTextureToReplaceWith();
		if (textureToReplace != null)
		{
			for (int i = 0; i < textureToReplace.length; i++)
			{
				modelData = modelData.retexture(textureToReplace[i], textureToReplaceWith[i]);
			}
		}

		return modelData.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE).light();
	}
}
