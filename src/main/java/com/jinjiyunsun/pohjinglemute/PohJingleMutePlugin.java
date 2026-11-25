package com.jinjiyunsun.pohjinglemute;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MidiRequest;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "POH Jingle Muter",
	description = "Mutes the jingle that plays when entering a player-owned house",
	tags = {"poh", "house", "jingle", "music", "mute", "sound"}
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

	// Volume to put back on the next client tick, or -1 when nothing is muted
	private int restoreVolume = -1;
	private boolean jingleWasActive;

	@Override
	protected void startUp()
	{
		jingleWasActive = false;
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(this::restore);
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
		boolean active = isPohJingleActive();

		// Restore a tick after muting: the jingle is gone by then and the house track not yet requested
		if (restoreVolume >= 0)
		{
			restore();
		}
		// Only on the jingle appearing, so a jingle that survives never toggles the volume each tick
		else if (active && !jingleWasActive)
		{
			mute();
		}

		jingleWasActive = active;
	}

	private void mute()
	{
		// Cutscenes reuse house jingles (Basic Wood in Daddy's Home), so leave those playing
		if (client.getVarbitValue(VarbitID.CUTSCENE_STATUS) == 1)
		{
			return;
		}

		// getMusicVolume reads the saved setting, which setMusicVolume leaves alone
		int volume = client.getMusicVolume();
		if (volume <= 0)
		{
			return;
		}

		// Volume 0 stops the jingle outright, so it never plays
		restoreVolume = volume;
		client.setMusicVolume(0);
	}

	private boolean isPohJingleActive()
	{
		List<MidiRequest> active = client.getActiveMidiRequests();
		return active != null && active.stream()
			.anyMatch(request -> request.isJingle() && POH_JINGLES.contains(request.getArchiveId()));
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
}
