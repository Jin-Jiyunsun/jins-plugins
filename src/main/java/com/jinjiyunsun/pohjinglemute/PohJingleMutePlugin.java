package com.jinjiyunsun.pohjinglemute;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MidiRequest;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "POH Jingle Muter",
	description = "Mutes the jingle that plays when entering a player-owned house",
	tags = {"poh", "house", "jingle", "music", "mute", "sound", "annoyance", "annoying"}
)
public class PohJingleMutePlugin extends Plugin
{
	// Entrance jingle MIDI IDs, one per house style; there are no gamevals for these
	private static final Set<Integer> POH_JINGLES = ImmutableSet.of(
		16,  // Tropical Wood
		18,  // Whitewashed Stone
		19,  // Fremennik-Style Wood
		20,  // Basic Wood, Twisted theme
		21,  // Basic Stone
		22,  // Fancy Stone
		112, // Deathly Mansion, Wilderness theme
		279, // Hosidius
		286, // Festive (Cosy cabin theme)
		307, // Canifis
		308  // Civitas
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PohJingleMuteConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	// Volume to put back on the next client tick, or -1 when nothing is muted
	private int restoreVolume = -1;
	private boolean muteTargetWasActive;

	private boolean mutePohJingles;
	private Set<Integer> mutedJingles = new HashSet<>();
	private Set<Integer> mutedTracks = new HashSet<>();
	private Map<Integer, String> trackNames = new HashMap<>();
	// Requests seen last tick, as "jingle 299" or "track 496", for posting new ones
	private Set<String> lastRequests = new HashSet<>();
	// Set from the config thread; the lists are rebuilt on the client thread, where the music table can be read
	private volatile boolean listsChanged;

	@Provides
	PohJingleMuteConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohJingleMuteConfig.class);
	}

	@Override
	protected void startUp()
	{
		muteTargetWasActive = false;
		listsChanged = true;
		lastRequests = new HashSet<>();
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(this::restore);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(PohJingleMuteConfig.GROUP))
		{
			listsChanged = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			restore();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		// The music table can only be read once the cache has loaded
		if (listsChanged && client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState())
		{
			listsChanged = false;
			Map<String, Integer> idsByName = trackIdsByName();
			trackNames = new HashMap<>();
			idsByName.forEach((name, id) -> trackNames.put(id, name));
			mutePohJingles = config.mutePohJingles();
			mutedJingles = parseIds(config.mutedJingles(), null);
			mutedTracks = parseIds(config.mutedTracks(), idsByName);
		}

		// Read before muting, so a request stopped this tick is still posted
		postNewRequests();
		boolean active = isMuteTargetActive();

		// Restore a tick after muting: the request is gone by then and the next track not yet requested
		if (restoreVolume >= 0)
		{
			restore();
		}
		// Only on a target appearing, so one that survives never toggles the volume each tick
		else if (active && !muteTargetWasActive)
		{
			mute();
		}

		muteTargetWasActive = active;
	}

	private void mute()
	{
		// getMusicVolume reads the saved setting, which setMusicVolume leaves alone
		int volume = client.getMusicVolume();
		if (volume <= 0)
		{
			return;
		}

		// Volume 0 stops the request outright, and a stopped track stays stopped until a new one is sent
		restoreVolume = volume;
		client.setMusicVolume(0);
	}

	private boolean isMuteTargetActive()
	{
		List<MidiRequest> active = client.getActiveMidiRequests();
		if (active == null)
		{
			return false;
		}

		// Cutscenes reuse house jingles (Basic Wood in Daddy's Home), so leave those playing
		boolean inCutscene = client.getVarbitValue(VarbitID.CUTSCENE_STATUS) == 1;
		for (MidiRequest request : active)
		{
			int id = request.getArchiveId();
			if (request.isJingle()
				? mutedJingles.contains(id) || (mutePohJingles && POH_JINGLES.contains(id) && !inCutscene)
				: mutedTracks.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	private void postNewRequests()
	{
		// Forgetting what was seen means turning the option on posts what's already playing
		if (!config.showTrackIds())
		{
			lastRequests.clear();
			return;
		}

		Set<String> current = new HashSet<>();
		List<MidiRequest> active = client.getActiveMidiRequests();
		if (active != null)
		{
			for (MidiRequest request : active)
			{
				String key = (request.isJingle() ? "jingle " : "track ") + request.getArchiveId();
				current.add(key);
				// Only while logged in: the login screen has its own music, and chat isn't shown there
				if (!lastRequests.contains(key) && client.getGameState() == GameState.LOGGED_IN)
				{
					postRequest(request);
				}
			}
		}
		lastRequests = current;
	}

	private void postRequest(MidiRequest request)
	{
		int id = request.getArchiveId();
		String message;
		if (request.isJingle())
		{
			message = "Jingle " + id;
		}
		else
		{
			String name = trackNames.get(id);
			message = "Track " + id + (name == null ? "" : ": " + name);
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			// Names the plugin, for players who forget the option is on
			.runeLiteFormattedMessage("[POH Jingle Muter] " + message)
			.build());
	}

	private void restore()
	{
		if (restoreVolume < 0)
		{
			return;
		}
		client.setMusicVolume(restoreVolume);
		restoreVolume = -1;
	}

	// Numbers are taken as IDs; anything else is looked up by name when a name map is given
	private static Set<Integer> parseIds(String list, Map<String, Integer> idsByName)
	{
		Set<Integer> ids = new HashSet<>();
		for (String entry : Text.fromCSV(list))
		{
			try
			{
				ids.add(Integer.parseInt(entry));
			}
			catch (NumberFormatException e)
			{
				Integer id = idsByName == null ? null : idsByName.get(entry);
				if (id != null)
				{
					ids.add(id);
				}
			}
		}
		return ids;
	}

	// Track names as shown in the music tab, from the game's music table; lookups ignore case
	private Map<String, Integer> trackIdsByName()
	{
		Map<String, Integer> ids = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (int row : client.getDBTableRows(DBTableID.Music.ID))
		{
			Object[] name = client.getDBTableField(row, DBTableID.Music.COL_DISPLAYNAME, 0);
			Object[] midi = client.getDBTableField(row, DBTableID.Music.COL_MIDI, 0);
			if (name.length > 0 && midi.length > 0 && name[0] instanceof String && midi[0] instanceof Integer)
			{
				ids.put((String) name[0], (Integer) midi[0]);
			}
		}
		return ids;
	}
}
