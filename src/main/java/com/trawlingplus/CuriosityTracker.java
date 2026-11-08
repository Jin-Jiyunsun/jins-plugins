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
	// The curiosities caught in the nets.
	private static final Curiosity[] RARE_FISH = {Curiosity.GIANT_BLUE_KRILL, Curiosity.GOLDEN_HADDOCK,
		Curiosity.ORANGEFIN, Curiosity.HUGE_HALIBUT, Curiosity.PURPLEFIN, Curiosity.SWIFT_MARLIN};
	// How a catch line of each ends, " purplefin!", checked before the pattern runs.
	private static final String[] RARE_ENDINGS = new String[RARE_FISH.length];
	// "You catch a giant blue krill!", "Jolly Jim catches an orangefin!" (the game says "a orangefin" for the player),
	// naming only the rare fish.
	private static final Pattern RARE_CATCH;

	static
	{
		StringBuilder names = new StringBuilder();
		for (int i = 0; i < RARE_FISH.length; i++)
		{
			String name = RARE_FISH[i].getName().toLowerCase();
			RARE_ENDINGS[i] = " " + name + "!";
			names.append(i == 0 ? "" : "|").append(Pattern.quote(name));
		}
		RARE_CATCH = Pattern.compile("^(?:You catch|(.+) catches) an? (" + names + ")!$");
	}
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
	// How long after the collection log line saves a find the crewmate's catch line can still name its finder.
	private static final int FINDER_TICKS = 2;

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
	// How many Soup items were in the inventory when the pet message came.
	private int soupInInventory;
	// The find the collection log line just saved without a finder, and the tick it did, or null.
	private Curiosity awaitingFinder;
	private int awaitingFinderTick;

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
		awaitingFinder = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.MESBOX)
		{
			return;
		}
		String message = TrawlingPlusPlugin.chatText(event.getMessage());
		int tick = client.getTickCount();
		if (message.startsWith("You catch "))
		{
			playerCatchTick = tick;
		}

		// The pattern only runs on lines ending in a rare fish's name.
		Matcher rare = RARE_CATCH.matcher(message);
		if (endsWithRareFish(message) && rare.matches())
		{
			Curiosity curiosity = byName.get(rare.group(2));
			String crewmate = rare.group(1);
			// A crewmate's catch only counts on the player's own boat, as the crew's catches do.
			if (crewmate == null || !plugin.onOtherBoat())
			{
				found(curiosity, crewmate);
			}
		}
		else if (message.equals(PAINT) && TrawlingPlusPlugin.within(playerCatchTick, tick, PAINT_AFTER_CATCH_TICKS))
		{
			found(Curiosity.ANGLERS_PAINT, null);
		}
		else if (message.startsWith(COLLECTION_LOG))
		{
			Curiosity curiosity = byName.get(message.substring(COLLECTION_LOG.length()).trim().toLowerCase());
			if (curiosity != null && found(curiosity, curiosity == Curiosity.SOUP ? soupFinder() : null)
				&& curiosity.getCrewEntry() != null)
			{
				// The catch line naming who caught it can arrive just after.
				awaitingFinder = curiosity;
				awaitingFinderTick = tick;
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
		if (!TrawlingPlusPlugin.within(petTick, client.getTickCount(), PET_TICKS))
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
			found(Curiosity.SOUP, soupFinder());
		}
	}

	/**
	 * Soup comes from any Sailing activity, so one found with no trawling catch in the last 3 minutes is marked as
	 * found elsewhere, and its page doesn't tell of the nets.
	 */
	private String soupFinder()
	{
		return plugin.caughtRecently() ? null : Curiosity.FOUND_ELSEWHERE;
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
			return;
		}
		List<Widget> shown = new ArrayList<>();
		collectItems(items, shown);
		for (Widget item : shown)
		{
			Curiosity curiosity = byItem.get(item.getItemId());
			// The log shows items not yet obtained faded out.
			if (curiosity != null && item.getOpacity() == 0 && foundDate(curiosity) == null)
			{
				save(curiosity, Curiosity.FROM_LOG, null);
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
	 * Notes a curiosity found now, by a crewmate or (null) the player, unless it already has been; returns whether it
	 * was saved. A saved find is never changed, except that a crewmate's catch line arriving within a couple of ticks
	 * of the collection log line that saved it, for the same item, names its finder.
	 */
	private boolean found(Curiosity curiosity, String crewmate)
	{
		if (foundDate(curiosity) == null)
		{
			save(curiosity, LocalDate.now().toString(), crewmate);
			return true;
		}
		if (curiosity == awaitingFinder && TrawlingPlusPlugin.within(awaitingFinderTick, client.getTickCount(), FINDER_TICKS))
		{
			// Only the one catch line straight after the log line, whoever it names.
			awaitingFinder = null;
			if (crewmate != null)
			{
				log.debug("Curiosity finder: {} by {}", curiosity.getName(), crewmate);
				configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey(), crewmate);
			}
		}
		return false;
	}

	private void save(Curiosity curiosity, String date, String crewmate)
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		log.debug("Curiosity found: {} on {} by {}", curiosity.getName(), date, crewmate);
		configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.foundKey(), date);
		if (crewmate != null && (curiosity.getCrewEntry() != null || Curiosity.FOUND_ELSEWHERE.equals(crewmate)))
		{
			configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey(), crewmate);
		}
	}

	private static boolean endsWithRareFish(String message)
	{
		for (String ending : RARE_ENDINGS)
		{
			if (message.endsWith(ending))
			{
				return true;
			}
		}
		return false;
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
