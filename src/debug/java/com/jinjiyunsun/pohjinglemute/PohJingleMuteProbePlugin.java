package com.jinjiyunsun.pohjinglemute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MidiRequest;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only probe: logs active MIDI requests and music volume as they change.
 * Never changes any game or client state.
 */
@PluginDescriptor(
	name = "POH Jingle Mute Probe",
	developerPlugin = true
)
public class PohJingleMuteProbePlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(PohJingleMuteProbePlugin.class);

	@Inject
	private Client client;

	private String lastRequests = "";
	private long lastChangeNanos;
	private final Map<String, Long> firstSeenNanos = new HashMap<>();
	private int lastClientVolume = -1;
	private int lastOptionMusic = -1;

	@Override
	protected void startUp()
	{
		lastRequests = "";
		lastChangeNanos = System.nanoTime();
		firstSeenNanos.clear();
		lastClientVolume = -1;
		lastOptionMusic = -1;
	}

	@Override
	protected void shutDown()
	{
		firstSeenNanos.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		log.debug("[probe] game state {}", event.getGameState());
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		long now = System.nanoTime();
		checkVolume();

		List<String> current = new ArrayList<>();
		List<MidiRequest> active = client.getActiveMidiRequests();
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
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[probe] " + message, null);
	}
}
