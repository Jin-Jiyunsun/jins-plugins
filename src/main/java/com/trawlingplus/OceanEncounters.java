package com.trawlingplus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;

/**
 * Finds the giant clams, lost crates, lost caskets and clue turtles that surface around a moving boat, keeps the
 * ones picked in the settings for the outline, and notifies of each once. It only reads; nothing is sent to the game.
 */
@Singleton
class OceanEncounters
{
	// Lowest tier first, in the order of the settings' tiers.
	private static final int[] CRATES = {
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_WOOD,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_OAK,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_TEAK,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_MAHOGANY,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CAMPHOR,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_IRONWOOD,
	};
	private static final int[] CASKETS = {
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CASKET_BEGINNER,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CASKET_EASY,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CASKET_MEDIUM,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CASKET_HARD,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CASKET_ELITE,
		NpcID.SAILING_CHANCE_ENCOUNTER_LOST_GOODS_CASKET_MASTER,
	};

	private static final TrawlingPlusConfig.CrateTier[] CRATE_TIERS = TrawlingPlusConfig.CrateTier.values();
	private static final TrawlingPlusConfig.CasketTier[] CASKET_TIERS = TrawlingPlusConfig.CasketTier.values();

	private final Client client;
	private final TrawlingPlusConfig config;
	private final Notifier notifier;

	// Every encounter in the scene.
	private final Set<NPC> seen = new HashSet<>();
	// Those already notified of, so each notifies once.
	private final Set<NPC> notified = new HashSet<>();
	// Those picked in the settings, worked out once a tick, for the outline; two lists reused in turn.
	private List<NPC> shown = new ArrayList<>();
	private List<NPC> spare = new ArrayList<>();

	@Inject
	OceanEncounters(Client client, TrawlingPlusConfig config, Notifier notifier)
	{
		this.client = client;
		this.config = config;
		this.notifier = notifier;
	}

	List<NPC> shown()
	{
		return shown;
	}

	/**
	 * Picks up encounters already in the scene, as when the plugin is switched on at sea.
	 */
	void findExisting()
	{
		WorldView top = client.getTopLevelWorldView();
		if (top != null)
		{
			for (NPC npc : top.npcs())
			{
				add(npc);
			}
		}
	}

	void reset()
	{
		seen.clear();
		notified.clear();
		shown.clear();
		spare.clear();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		add(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (seen.remove(npc))
		{
			notified.remove(npc);
			shown.remove(npc);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (seen.isEmpty())
		{
			shown.clear();
			return;
		}

		TrawlingPlusConfig.CrateTier lowestCrate = config.crateTier();
		TrawlingPlusConfig.CasketTier lowestCasket = config.casketTier();
		List<NPC> wanted = spare;
		wanted.clear();
		for (NPC npc : seen)
		{
			int id = baseId(npc);
			int crate = indexOf(CRATES, id);
			int casket = indexOf(CASKETS, id);
			int formId = -1;
			Notification notification;
			if (crate >= 0)
			{
				notification = CRATE_TIERS[crate].compareTo(lowestCrate) >= 0 ? config.notifyLostCrate() : Notification.OFF;
			}
			else if (casket >= 0)
			{
				notification = CASKET_TIERS[casket].compareTo(lowestCasket) >= 0 ? config.notifyLostCasket() : Notification.OFF;
			}
			else if (id == NpcID.SAILING_CHANCE_ENCOUNTER_CLUE_TURTLE)
			{
				notification = config.notifyClueTurtle();
			}
			else
			{
				// The clam shows the player's own clam: empty, shut on an item, or offering a pearl. A shut one has
				// nothing to do.
				NPCComposition form = npc.getTransformedComposition();
				formId = form == null ? -1 : form.getId();
				if (formId != NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_OPEN && formId != NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_PEARL)
				{
					continue;
				}
				notification = config.notifyGiantClam();
			}

			if (!notification.isEnabled())
			{
				continue;
			}
			wanted.add(npc);
			if (notified.add(npc))
			{
				notifier.notify(notification, message(crate, casket, formId));
			}
		}
		spare = shown;
		shown = wanted;
	}

	/**
	 * The notification for a crate or casket by its place among them, a clam by its form, or else the clue turtle.
	 */
	private String message(int crate, int casket, int clamForm)
	{
		boolean plain = config.plainNotifications();
		if (crate >= 0)
		{
			String wood = CRATE_TIERS[crate].toString().toLowerCase();
			return plain ? "Lost " + wood + " crate nearby." : "You spot a lost " + wood + " crate drifting nearby.";
		}
		if (casket >= 0)
		{
			String tier = CASKET_TIERS[casket].toString().toLowerCase();
			return plain ? "Lost casket (" + tier + ") nearby." : "You spot a lost casket (" + tier + ") floating nearby.";
		}
		if (clamForm == NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_OPEN)
		{
			return plain ? "Giant clam nearby." : "You spot a giant clam surfacing nearby.";
		}
		if (clamForm == NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_PEARL)
		{
			return plain ? "Giant clam with a pearl nearby." : "You spot a giant clam with a pearl surfacing nearby.";
		}
		return plain ? "Clue turtle nearby." : "You spot a clue turtle swimming nearby.";
	}

	private void add(NPC npc)
	{
		int id = baseId(npc);
		if (id == NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM
			|| id == NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_OPEN
			|| id == NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_CLOSED
			|| id == NpcID.SAILING_CHANCE_ENCOUNTERS_CLAM_PEARL
			|| id == NpcID.SAILING_CHANCE_ENCOUNTER_CLUE_TURTLE
			|| indexOf(CRATES, id) >= 0
			|| indexOf(CASKETS, id) >= 0)
		{
			seen.add(npc);
		}
	}

	/**
	 * The NPC's own id, before any change of form, so a clam is known whatever form it shows, or none.
	 */
	private static int baseId(NPC npc)
	{
		NPCComposition composition = npc.getComposition();
		return composition == null ? npc.getId() : composition.getId();
	}

	private static int indexOf(int[] ids, int id)
	{
		for (int i = 0; i < ids.length; i++)
		{
			if (ids[i] == id)
			{
				return i;
			}
		}
		return -1;
	}
}
