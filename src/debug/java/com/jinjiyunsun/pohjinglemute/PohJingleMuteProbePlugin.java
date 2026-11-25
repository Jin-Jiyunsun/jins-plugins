package com.jinjiyunsun.pohjinglemute;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MidiRequest;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs active MIDI requests and music volume as they change.
 * With a test mode set, mutes one jingle using setMusicVolume only.
 */
@PluginDescriptor(
	name = "POH Jingle Mute Probe",
	developerPlugin = true
)
public class PohJingleMuteProbePlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(PohJingleMuteProbePlugin.class);

	// Restore anyway if the jingle is still listed this long after its length
	private static final long TIMEOUT_EXTRA_MS = 3000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PohJingleMuteProbeConfig config;

	private String lastRequests = "";
	private long lastChangeNanos;
	private final Map<String, Long> firstSeenNanos = new HashMap<>();
	private int lastClientVolume = -1;
	private int lastOptionMusic = -1;

	private boolean jingleWasActive;
	private boolean muting;
	private boolean tailSet;
	private long muteStartNanos;
	private int savedVolume;

	@Provides
	PohJingleMuteProbeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohJingleMuteProbeConfig.class);
	}

	@Override
	protected void startUp()
	{
		lastRequests = "";
		lastChangeNanos = System.nanoTime();
		firstSeenNanos.clear();
		lastClientVolume = -1;
		lastOptionMusic = -1;
		jingleWasActive = false;
		muting = false;
	}

	@Override
	protected void shutDown()
	{
		firstSeenNanos.clear();
		clientThread.invoke(() -> restore("probe stopped"));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		log.debug("[probe] game state {}", event.getGameState());
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			restore("logged out");
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		long now = System.nanoTime();
		List<MidiRequest> active = client.getActiveMidiRequests();

		runTimingTest(active, now);
		checkVolume();

		List<String> current = new ArrayList<>();
		if (active != null)
		{
			for (MidiRequest request : active)
			{
				current.add((request.isJingle() ? "jingle " : "track ") + request.getArchiveId());
			}
		}

		String requests = String.join(", ", current);
		if (requests.equals(lastRequests))
		{
			return;
		}

		long sinceLast = (now - lastChangeNanos) / 1_000_000;
		StringBuilder line = new StringBuilder();
		line.append("MIDI [").append(requests).append("] +").append(sinceLast).append("ms");

		// Report how long each request that just disappeared was active
		for (Map.Entry<String, Long> entry : new ArrayList<>(firstSeenNanos.entrySet()))
		{
			if (!current.contains(entry.getKey()))
			{
				long lasted = (now - entry.getValue()) / 1_000_000;
				line.append(" | ended ").append(entry.getKey()).append(" after ").append(lasted).append("ms");
				firstSeenNanos.remove(entry.getKey());
			}
		}
		for (String request : current)
		{
			firstSeenNanos.putIfAbsent(request, now);
		}

		report(line.toString());
		lastRequests = requests;
		lastChangeNanos = now;
	}

	private void runTimingTest(List<MidiRequest> active, long now)
	{
		PohJingleMuteProbeConfig.TestMode mode = config.testMode();
		boolean jingleActive = isJingleActive(active, config.jingleId());

		if (!muting)
		{
			// Only start on the jingle appearing, so a timeout doesn't re-mute it
			if (mode != PohJingleMuteProbeConfig.TestMode.OFF && jingleActive && !jingleWasActive)
			{
				startMute(now);
			}
		}
		else
		{
			long elapsed = (now - muteStartNanos) / 1_000_000;
			if (mode == PohJingleMuteProbeConfig.TestMode.OFF)
			{
				restore("mode set to off");
			}
			else
			{
				if (mode == PohJingleMuteProbeConfig.TestMode.ZERO_THEN_TAIL && !tailSet
					&& elapsed >= config.jingleLength() - config.tailLead())
				{
					client.setMusicVolume(config.tailVolume());
					tailSet = true;
					report("test: volume " + config.tailVolume() + " at +" + elapsed + "ms, now " + describeActive());
				}

				boolean tailDone = mode == PohJingleMuteProbeConfig.TestMode.ZERO_ONLY || tailSet;
				if (!jingleActive && tailDone)
				{
					restore("jingle gone");
				}
				else if (elapsed >= config.jingleLength() + TIMEOUT_EXTRA_MS)
				{
					restore("timeout");
				}
			}
		}

		jingleWasActive = jingleActive;
	}

	private void startMute(long now)
	{
		int volume = client.getMusicVolume();
		if (volume <= 0)
		{
			report("test: jingle " + config.jingleId() + " started, music already at 0, skipped");
			return;
		}
		savedVolume = volume;
		muting = true;
		tailSet = false;
		muteStartNanos = now;
		client.setMusicVolume(config.muteVolume());
		report("test: jingle " + config.jingleId() + " started, volume " + config.muteVolume()
			+ " (saved " + volume + "), now " + describeActive());
	}

	private void restore(String reason)
	{
		if (!muting)
		{
			return;
		}
		long elapsed = (System.nanoTime() - muteStartNanos) / 1_000_000;
		client.setMusicVolume(savedVolume);
		muting = false;
		report("test: restored " + savedVolume + " at +" + elapsed + "ms (" + reason + "), now " + describeActive());
	}

	// Active MIDI list read right after a volume change
	private String describeActive()
	{
		List<String> names = new ArrayList<>();
		List<MidiRequest> active = client.getActiveMidiRequests();
		if (active != null)
		{
			for (MidiRequest request : active)
			{
				names.add((request.isJingle() ? "jingle " : "track ") + request.getArchiveId());
			}
		}
		return "[" + String.join(", ", names) + "]";
	}

	private static boolean isJingleActive(List<MidiRequest> active, int jingleId)
	{
		if (active == null)
		{
			return false;
		}
		for (MidiRequest request : active)
		{
			if (request.isJingle() && request.getArchiveId() == jingleId)
			{
				return true;
			}
		}
		return false;
	}

	private void checkVolume()
	{
		int clientVolume = client.getMusicVolume();
		int optionMusic = client.getVarpValue(VarPlayerID.OPTION_MUSIC);
		if (clientVolume != lastClientVolume || optionMusic != lastOptionMusic)
		{
			report("volume: client " + clientVolume + ", slider " + optionMusic);
			lastClientVolume = clientVolume;
			lastOptionMusic = optionMusic;
		}
	}

	private void report(String message)
	{
		log.debug("[probe] {}", message);
		// Other plugins' chat handlers can fail before the local player exists
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[probe] " + message, null);
	}
}
