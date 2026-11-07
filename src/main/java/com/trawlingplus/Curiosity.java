package com.trawlingplus;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

/**
 * The rare finds written up at the back of the trawling log, a page each: the item, its name before it is found, its
 * journal entry for the player and for a crewmate (who names it first), and a hint while it is still to be found.
 */
@Getter
enum Curiosity
{
	GIANT_BLUE_KRILL(ItemID.POH_TROPHYDROP_GIANT_KRILL, "Giant blue krill", "Rare giant krill",
		"I caught a curious blue krill today. The crew almost started a mutiny over cooking and eating it!"
			+ "\nI didn't let them.",
		" caught a curious blue krill today. They almost started a mutiny over cooking and eating it!"
			+ "\nI didn't let them.",
		"It's rumoured that strangely coloured krill hide in shimmering shoals."),
	GOLDEN_HADDOCK(ItemID.POH_TROPHYDROP_HADDOCK, "Golden haddock", "Rare haddock",
		"I caught a haddock that glimmered like gold in the sunlight today. The crew wanted to sell it at the next"
			+ " port! I'm keeping it.",
		" caught a haddock that glimmered like gold in the sunlight today. They wanted to sell it at the next"
			+ " port! I'm keeping it.",
		"Merchants say there's a haddock worth its weight in gold hiding in shimmering shoals."),
	ORANGEFIN(ItemID.POH_TROPHYDROP_YELLOWFIN, "Orangefin", "Rare yellowfin",
		"I caught a yellowfin dyed like the setting sun today. The crew have argued over what to call it ever"
			+ " since!\nI settled on Orangefin.",
		" caught a yellowfin dyed like the setting sun. The crew argued over what to call it!"
			+ "\nI settled on Orangefin.",
		"Legends say the sun once sank into a glistening shoal, and a yellowfin swallowed its light."),
	HUGE_HALIBUT(ItemID.POH_TROPHYDROP_HALIBUT, "Huge halibut", "Rare halibut",
		"I caught a huge halibut today. It took the whole crew to haul it over the side!\nIt nearly sank the boat.",
		" caught a huge halibut today. They needed my help to haul it over the side!\nIt nearly sank the boat.",
		"Old fishermen's tales tell of a halibut that's truly a whopper, hiding in glistening shoals."),
	PURPLEFIN(ItemID.POH_TROPHYDROP_BLUEFIN, "Purplefin", "Rare bluefin",
		"I caught a purple bluefin today. The crew swore it was cursed and wouldn't go near it!\nI think it's lucky.",
		" caught a purple bluefin today. They swore it was cursed and tried to throw it back!\nI think it's lucky.",
		"Sailors whisper of a cursed bluefin, sometimes glimpsed in vibrant shoals."),
	SWIFT_MARLIN(ItemID.POH_TROPHYDROP_MARLIN, "Swift marlin", "Rare marlin",
		"I caught the swiftest marlin I've ever seen today. The crew think it would look good as a figurehead.",
		" caught the swiftest marlin I've ever seen today. They suggested it would look good as a figurehead.",
		"Lookouts swear a marlin faster than the wind itself cuts through vibrant shoals."),
	// Only the player's own net brings up paint, and Soup follows only the player.
	ANGLERS_PAINT(ItemID.SAILING_PAINT_ANGLERS, "Angler's paint", "Rare paint",
		"I hauled up a pot of paint in my nets today, still sealed!"
			+ "\nThe crew can't agree on which part of the boat to paint first.",
		null,
		"Sometimes the junk brought up in the nets hides something useful."),
	SOUP(ItemID.SKILLPETSAILING, "Soup", "A new friend?",
		"I found a sea turtle trapped in my nets, he seems to have taken a liking to me and has followed me ever"
			+ " since! I named him Soup.",
		null,
		"Even in the lonely sea, you can find a new friend.");

	// The saved date of a find known only from the collection log, and the date line its page shows instead.
	static final String FROM_LOG = "log";
	static final String BEFORE_THE_LOG = "Before this log was kept";

	private final int itemId;
	private final String name;
	private final String unfoundTitle;
	private final String entry;
	// The crew's entry, after the crewmate's name, or null for a find only the player makes.
	private final String crewEntry;
	private final String hint;

	Curiosity(int itemId, String name, String unfoundTitle, String entry, String crewEntry, String hint)
	{
		this.itemId = itemId;
		this.name = name;
		this.unfoundTitle = unfoundTitle;
		this.entry = entry;
		this.crewEntry = crewEntry;
		this.hint = hint;
	}

	/** The profile key holding the date it was first found ("2026-11-12"), or "log" when known only from the log. */
	String foundKey()
	{
		return "curiosityFound_" + itemId;
	}

	/** The profile key holding the name of the crewmate who found it, unset when the player did. */
	String finderKey()
	{
		return "curiosityFinder_" + itemId;
	}

	/**
	 * Its entry: the crewmate's, after their name, when one found it, or the player's; told as long ago ("I once
	 * caught...") when found before the log was kept.
	 */
	String entryFor(String finder, boolean beforeTheLog)
	{
		if (finder != null && crewEntry != null)
		{
			return finder + crewEntry;
		}
		return beforeTheLog ? entry.replace(" today", "").replaceFirst("^I ", "I once ") : entry;
	}
}
