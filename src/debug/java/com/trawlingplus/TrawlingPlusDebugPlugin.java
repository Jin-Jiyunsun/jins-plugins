package com.trawlingplus;

import com.google.inject.Provides;
import java.time.LocalDate;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Dev-only tools for Trawling Plus, loaded by the dev client and never part of the released plugin: times its main
 * overlay through the package-private drawTimer hook, and fakes Curiosities finds.
 */
@PluginDescriptor(
	name = "Trawling Plus Debug",
	description = "Debug tools for Trawling Plus",
	developerPlugin = true
)
public class TrawlingPlusDebugPlugin extends Plugin
{
	// Crewmates' names, from the game's crew.
	private static final String[] CREW = {"Jolly Jim", "Jobless Jim", "Jittery Jim", "Sailor Jakob",
		"Adventurer Ada", "Bosun Zarah", "Oarswoman Olga", "Ex-Captain Siad", "Spotter Virginia",
		"Cabin Boy Jenkins"};

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TrawlingPlusDebugTimingOverlay timingOverlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	private TrawlingPlusDebugConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Provides
	TrawlingPlusDebugConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TrawlingPlusDebugConfig.class);
	}

	@Override
	protected void startUp()
	{
		TrawlingPlusOverlay.drawTimer = timingOverlay;
		overlayManager.add(timingOverlay);
	}

	@Override
	protected void shutDown()
	{
		TrawlingPlusOverlay.drawTimer = null;
		overlayManager.remove(timingOverlay);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TrawlingPlusDebugConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (TrawlingPlusDebugConfig.FAKE_MESSAGE_KEY.equals(event.getKey()))
		{
			fakeMessage();
			return;
		}
		if (!TrawlingPlusDebugConfig.FAKE_CURIOSITIES_KEY.equals(event.getKey()))
		{
			return;
		}
		boolean on = Boolean.parseBoolean(event.getNewValue());
		Random random = new Random();
		for (Curiosity curiosity : Curiosity.values())
		{
			configManager.unsetRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.foundKey());
			configManager.unsetRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey());
			if (!on || random.nextInt(3) == 0)
			{
				// Left not found.
				continue;
			}
			String date = random.nextInt(4) == 0 ? TrawlingPlusPlugin.CURIOSITY_FROM_LOG
				: LocalDate.now().minusDays(random.nextInt(365)).toString();
			configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.foundKey(), date);
			if (!TrawlingPlusPlugin.CURIOSITY_FROM_LOG.equals(date) && curiosity.getCrewEntry() != null
				&& random.nextBoolean())
			{
				configManager.setRSProfileConfiguration(TrawlingPlusConfig.GROUP, curiosity.finderKey(),
					CREW[random.nextInt(CREW.length)]);
			}
		}
	}

	/** Puts the picked game message in this client's chatbox only, then sets the pick back to none. */
	private void fakeMessage()
	{
		String message = config.fakeCuriosityMessage().message;
		if (message == null)
		{
			return;
		}
		clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
		configManager.setConfiguration(TrawlingPlusDebugConfig.GROUP, TrawlingPlusDebugConfig.FAKE_MESSAGE_KEY,
			TrawlingPlusDebugConfig.FakeMessage.NONE);
	}
}
