package com.trawlingplus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Notes when each curiosity is first found, for the trawling log's Curiosities pages, per account: from the catch
 * lines and the paint and pet messages as they happen, from the "New item added to your collection log" line, and,
 * for anything found before, from the collection log's own screen whenever the player opens a page of it showing
 * them, checked once a tick while it is open. It only reads; nothing is sent to the game.
 */
@Slf4j
@Singleton
class CuriosityTracker
{
	// "You catch a giant blue krill!", "Jolly Jim catches an orangefin!" (the game says "a orangefin" for the player).
	private static final Pattern RARE_CATCH = Pattern.compile("^(?:You catch|(.+) catches) an? ([a-z ]+)!$");
	private static final String PAINT = "You've received some paint!";
	private static final String COLLECTION_LOG = "New item added to your collection log: ";
	// A pet arriving as a follower, or into the backpack when a follower is already out. Every pet says these, so
	// the pet itself is checked for.
	private static final String PET_FOLLOWING = "You have a funny feeling like you're being followed.";
	private static final String PET_BACKPACK = "You feel something weird sneaking into your backpack.";
	// How long after a pet message the pet is looked for.
	private static final int PET_TICKS = 3;
	// Paint only comes from the player's own net catching, so it only counts this soon after one of their catches.
	private static final int PAINT_AFTER_CATCH_TICKS = 30;
	private static final Pattern CHAT_COLOUR = Pattern.compile("^(?:@[a-z_]+@)+");

	private final Client client;
	private final ConfigManager configManager;
	private final TrawlingPlusPlugin plugin;
	private final Map<String, Curiosity> byName = new HashMap<>();
	private final Map<Integer, Curiosity> byItem = new HashMap<>();

	// The tick the player last caught a fish, or -1.
	private int playerCatchTick = -1;
	// The tick of the last pet message, or -1, and whether it said the backpack.
	private int petTick = -1;
	private boolean petToBackpack;
	// The open collection log page's items and those obtained, as last read, or null while it is closed.
	private String lastLogPage;
	// How many Soup items were in the inventory when the pet message came.
	private int soupInInventory;

	@Inject
	CuriosityTracker(Client client, ConfigManager configManager, TrawlingPlusPlugin plugin)
	{
		this.client = client;
		this.configManager = configManager;
		this.plugin = plugin;
		for (Curiosity curiosity : Curiosity.values())
		{
			byName.put(curiosity.getName().toLowerCase(), curiosity);
			byItem.put(curiosity.getItemId(), curiosity);
		}
	}

	void reset()
	{
		playerCatchTick = -1;
		petTick = -1;
		lastLogPage = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.MESBOX)
		{
			return;
		}
		String message = CHAT_COLOUR.matcher(Text.removeTags(event.getMessage())).replaceFirst("");
		int tick = client.getTickCount();
		if (message.startsWith("You catch "))
		{
			playerCatchTick = tick;
		}

		Matcher rare = RARE_CATCH.matcher(message);
		if (rare.matches())
		{
			Curiosity curiosity = byName.get(rare.group(2));
			String crewmate = rare.group(1);
			// A crewmate's catch only counts on the player's own boat, as the crew's catches do.
			if (curiosity != null && (crewmate == null || !plugin.onOtherBoat()))
			{
				found(curiosity, crewmate);
			}
		}
		else if (message.equals(PAINT) && playerCatchTick >= 0 && tick - playerCatchTick <= PAINT_AFTER_CATCH_TICKS)
		{
			found(Curiosity.ANGLERS_PAINT, null);
		}
		else if (message.startsWith(COLLECTION_LOG))
		{
			Curiosity curiosity = byName.get(message.substring(COLLECTION_LOG.length()).trim().toLowerCase());
			if (curiosity != null)
			{
				found(curiosity, null);
			}
		}
		else if (message.equals(PET_FOLLOWING) || message.equals(PET_BACKPACK))
		{
			petTick = tick;
			petToBackpack = message.equals(PET_BACKPACK);
			soupInInventory = soupInInventory();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		readCollectionLog();
		if (petTick < 0)
		{
			return;
		}
		if (client.getTickCount() - petTick > PET_TICKS)
		{
			petTick = -1;
			return;
		}
		NPC follower = client.getFollower();
		boolean soup = petToBackpack ? soupInInventory() > soupInInventory
			: follower != null && follower.getId() == NpcID.SKILLPET_SAILING;
		if (soup)
		{
			petTick = -1;
			found(Curiosity.SOUP, null);
		}
	}

	/**
	 * While a collection log page is open, any curiosity it shows as obtained, but not yet noted, was found before.
	 * Checked once a tick while it is open, as the page's items are laid out after the page itself is drawn.
	 */
	private void readCollectionLog()
	{
		Widget items = client.getWidget(InterfaceID.Collection.ITEMS);
		if (items == null || items.isHidden())
		{
			lastLogPage = null;
			return;
		}
		List<Widget> shown = new ArrayList<>();
		collectItems(items, shown);
		StringBuilder page = new StringBuilder();
		for (Widget item : shown)
		{
			// The log shows items not yet obtained faded out.
			if (item.getOpacity() == 0)
			{
				page.append(' ').append(item.getItemId());
			}
		}
		String obtained = shown.size() + ":" + page;
		if (obtained.equals(lastLogPage))
		{
			return;
		}
		lastLogPage = obtained;
		log.debug("Collection log page shows {} items, obtained:{}", shown.size(), page);
		for (Widget item : shown)
		{
			Curiosity curiosity = byItem.get(item.getItemId());
			if (curiosity != null && item.getOpacity() == 0 && foundDate(curiosity) == null)
			{
				save(curiosity, TrawlingPlusPlugin.CURIOSITY_FROM_LOG, null);
			}
		}
	}

	/** Every widget showing an item under this one. */
	private static void collectItems(Widget widget, List<Widget> into)
	{
		for (Widget[] children : new Widget[][]{widget.getDynamicChildren(), widget.getStaticChildren(),
			widget.getNestedChildren()})
		{
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child.getItemId() > 0)
				{
					into.add(child);
				}
				collectItems(child, into);
			}
		}
	}

	/**
	 * Notes a curiosity found now, by a crewmate or (null) the player, unless it already has been. A crewmate's catch
	 * arriving after the collection log line for the same find gives it its finder.
	 */
	private void found(Curiosity curiosity, String crewmate)
	{
		String today = LocalDate.now().toString();
		String date = foundDate(curiosity);
		if (date == null)
		{
			save(curiosity, today, crewmate);
		}
		else if (date.equals(today) && crewmate != null && curiosity.getCrewEntry() != null
			&& configManager.getRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey()) == null)
		{
			configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey(), crewmate);
		}
	}

	private void save(Curiosity curiosity, String date, String crewmate)
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		log.debug("Curiosity found: {} on {} by {}", curiosity.getName(), date, crewmate);
		configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.foundKey(), date);
		if (crewmate != null && curiosity.getCrewEntry() != null)
		{
			configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey(), crewmate);
		}
	}

	private String foundDate(Curiosity curiosity)
	{
		return configManager.getRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.foundKey());
	}

	private int soupInInventory()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory == null ? 0 : inventory.count(ItemID.SKILLPETSAILING);
	}
}
