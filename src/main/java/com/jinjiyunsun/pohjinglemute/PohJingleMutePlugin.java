package com.jinjiyunsun.pohjinglemute;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.Collections;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Music & Jingle Muter",
	description = "Mutes the music tracks and jingles you choose",
	tags = {"music", "track", "jingle", "mute", "sound", "annoyance", "annoying"}
)
public class PohJingleMutePlugin extends Plugin
{
	// The red the game uses in its own chat messages
	private static final Color PREFIX_COLOR = new Color(0xEF1020);
	// Readable on both the opaque and the transparent chatbox
	private static final Color ID_COLOR = new Color(0xFF9900);
	private static final Color NAME_COLOR = new Color(0x4FA8FF);

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
	// Listed requests already dealt with, as "jingle 299" or "track 496", so one that stays listed is never muted twice
	private Set<String> handledTargets = new HashSet<>();

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
		handledTargets = new HashSet<>();
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
			mutedJingles = parseIds(config.mutedJingles(), null);
			mutedTracks = parseIds(config.mutedTracks(), idsByName);
		}

		List<MidiRequest> active = client.getActiveMidiRequests();
		// Before muting, so a request stopped this tick is still posted
		postNewRequests(active);

		Set<String> targets = muteTargets(active);
		// Forget targets that have stopped, so they're muted again if they come back
		handledTargets.retainAll(targets);

		// Restore a tick after muting: the request is gone by then and the next track not yet requested.
		// A target that appears on this tick is left unhandled and muted next tick
		if (restoreVolume >= 0)
		{
			restore();
		}
		else if (!handledTargets.containsAll(targets))
		{
			mute();
			handledTargets.addAll(targets);
		}
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

	// Listed requests now playing; a shared empty set when none, as is usual, to save making one each tick
	private Set<String> muteTargets(List<MidiRequest> active)
	{
		Set<String> targets = null;
		if (active != null)
		{
			for (MidiRequest request : active)
			{
				int id = request.getArchiveId();
				if (request.isJingle() ? mutedJingles.contains(id) : mutedTracks.contains(id))
				{
					if (targets == null)
					{
						targets = new HashSet<>();
					}
					targets.add(key(request));
				}
			}
		}
		return targets == null ? Collections.emptySet() : targets;
	}

	private static String key(MidiRequest request)
	{
		return (request.isJingle() ? "jingle " : "track ") + request.getArchiveId();
	}

	private void postNewRequests(List<MidiRequest> active)
	{
		// Forgetting what was seen means turning the option on posts what's already playing
		if (!config.showTrackIds())
		{
			lastRequests.clear();
			return;
		}

		Set<String> current = new HashSet<>();
		if (active != null)
		{
			for (MidiRequest request : active)
			{
				String key = key(request);
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
			message = "Jingle " + ColorUtil.wrapWithColorTag(String.valueOf(id), ID_COLOR);
		}
		else
		{
			String name = trackNames.get(id);
			message = "Track " + ColorUtil.wrapWithColorTag(String.valueOf(id), ID_COLOR)
				+ (name == null ? "" : ": " + ColorUtil.wrapWithColorTag(name, NAME_COLOR));
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			// Names the plugin, for players who forget the option is on
			.runeLiteFormattedMessage("[" + ColorUtil.wrapWithColorTag("Music & Jingle Muter", PREFIX_COLOR) + "] " + message)
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
