package com.trawlingplus;

import java.awt.Color;
import java.util.Arrays;
import net.runelite.api.gameval.ItemID;

/**
 * What one boat's cargo hold holds of the catches from the fishing spots at sea, and how many of its slots the
 * rest of its cargo takes out of how many. Only known once its screen has been opened, the only time the game
 * sends it, and kept up to date after by what is deposited or withdrawn as it is shut. Every fish takes a slot of
 * its own, since fish don't stack, so the slots left for them are what the rest doesn't take.
 */
final class CargoHold
{
	// The catches of the six fishing spots at sea, which are only fished from a boat, and what the hold's display
	// calls each, in the same order.
	static final int[] SEA_FISH = {
		ItemID.RAW_SHRIMP, ItemID.RAW_ANCHOVIES, ItemID.RAW_SARDINE, ItemID.RAW_HERRING, ItemID.RAW_MACKEREL,
		ItemID.RAW_COD, ItemID.RAW_BASS, ItemID.RAW_SHARK, ItemID.RAW_LOBSTER, ItemID.RAW_TUNA, ItemID.RAW_SWORDFISH,
		ItemID.TBWT_RAW_KARAMBWAN, ItemID.RAW_ANGLERFISH, ItemID.RAW_MONKFISH
	};
	static final String[] SEA_FISH_NAMES = {
		"Shrimps", "Anchovies", "Sardine", "Herring", "Mackerel",
		"Cod", "Bass", "Shark", "Lobster", "Tuna", "Swordfish",
		"Karambwan", "Anglerfish", "Monkfish"
	};
	// The colour each is listed in: its item picture's own colour, averaged without its black outline and shadow
	// and with the most colourful parts counting most, then lifted to read on the dark display, tuned by eye and
	// made a fifth more saturated.
	static final Color[] SEA_FISH_COLOURS = {
		new Color(0xdc8d7c), new Color(0x8b8ebf), new Color(0x74d975), new Color(0xd9cbcb), new Color(0xd9d76b),
		new Color(0xa8a8d9), new Color(0xd97a6e), new Color(0xd9c3c3), new Color(0xd95c00), new Color(0xe6d8d5),
		new Color(0xcc86d9), new Color(0x72bf3f), new Color(0x6bbf52), new Color(0xd97b5b)
	};

	// How many of each, by SEA_FISH, and the slots taken by everything else, such as bait, planks, salvage and
	// trawled fish, and there are, or -1 before the screen has shown them.
	private final int[] fish = new int[SEA_FISH.length];
	private int others = -1;
	private int capacity = -1;
	// How many times any of those has changed, which the display goes by to only write its lines out again then.
	private int changes;
	// Which went in most recently: by SEA_FISH, the number of the deposit that last brought each, or 0 if none has
	// been seen; how many deposits there have been, those on the same tick counting as one; and the last one's tick.
	private final int[] lastDeposit = new int[SEA_FISH.length];
	private int deposits;
	private int depositTick = -1;
	// Whether the display at the helm warns that it is full: found without room when the nets were last emptied
	// into it, or showing no free slots when last opened, until it is known to have room again.
	private boolean fullWarning;

	int changes()
	{
		return changes;
	}

	/**
	 * The number of the deposit that last brought this kind, higher for more recent ones, or 0 if none has been seen.
	 */
	int lastDeposit(int kind)
	{
		return lastDeposit[kind];
	}

	boolean fullWarning()
	{
		return fullWarning;
	}

	void setFullWarning(boolean full)
	{
		fullWarning = full;
	}

	int fish(int kind)
	{
		return fish[kind];
	}

	/**
	 * How many catches from the fishing spots at sea it holds, all kinds together.
	 */
	int caught()
	{
		int caught = 0;
		for (int count : fish)
		{
			caught += count;
		}
		return caught;
	}

	/**
	 * How many of those catches it has slots for, counting the ones in it: all of its slots but those the rest of
	 * its cargo takes.
	 */
	int room()
	{
		return capacity - others;
	}

	/**
	 * Whether its slots are known, which they are once its screen has been opened.
	 */
	boolean known()
	{
		return others >= 0 && capacity > 0;
	}

	boolean full()
	{
		return known() && caught() >= room();
	}

	/**
	 * Whether at least a share of its slots, 0.9 being 90%, are taken, by anything, once known.
	 */
	boolean fullTo(double share)
	{
		return known() && capacity - room() + caught() >= capacity * share;
	}

	/**
	 * Takes how many of a kind its screen shows, on the tick it showed them. More than it was known to hold were just
	 * deposited; before its contents are known, there is nothing to say which went in last.
	 */
	void setFish(int kind, int count, int tick)
	{
		if (fish[kind] != count)
		{
			if (count > fish[kind] && known())
			{
				noteDeposit(kind, tick);
			}
			fish[kind] = count;
			changes++;
		}
	}

	/**
	 * Takes the slots taken and there are from its screen, once its catches are counted: those not taken by them
	 * are taken by the rest.
	 */
	void setSlots(int taken, int capacity)
	{
		int others = Math.max(0, taken - caught());
		if (others != this.others || capacity != this.capacity)
		{
			this.others = others;
			this.capacity = capacity;
			changes++;
		}
	}

	/**
	 * Adds fish put in without the screen sending them, as when deposited just as it is shut.
	 */
	void deposit(int kind, int count, int tick)
	{
		fish[kind] += count;
		noteDeposit(kind, tick);
		changes++;
	}

	private void noteDeposit(int kind, int tick)
	{
		if (tick != depositTick)
		{
			depositTick = tick;
			deposits++;
		}
		lastDeposit[kind] = deposits;
	}

	/**
	 * Takes away fish taken out without the screen sending it, as when withdrawn just as it is shut.
	 */
	void withdraw(int kind, int count)
	{
		fish[kind] = Math.max(0, fish[kind] - count);
		changes++;
	}

	/**
	 * Everything taken out at once, as when the crew move it all to the bank from the dock.
	 */
	void empty()
	{
		Arrays.fill(fish, 0);
		changes++;
		if (others >= 0)
		{
			others = 0;
		}
		fullWarning = false;
	}
}
