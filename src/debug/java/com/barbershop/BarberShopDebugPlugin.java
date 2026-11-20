package com.barbershop;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Barber Shop Debug",
	description = "Developer checks for Barber Shop",
	developerPlugin = true
)
public class BarberShopDebugPlugin extends Plugin
{
	private static final int MAGENTA = JagexColor.rgbToHSL(0xFF00FF, 1.0);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BarberShopDebugConfig config;

	// What the player looked like when last painted, to repaint after any change.
	private int appearance;
	private Model painted;
	private HeadFaces.Ranges ranges;
	private int[] saved1;
	private int[] saved3;
	// The colour array the paint went into; the game shares it with the stored model.
	private int[] saved1Source;

	@Override
	protected void shutDown()
	{
		BarberShopPlugin.heightNudge = 0;
		BarberShopPlugin.neckCut = 10;
		BarberShopPlugin.neckWeldFull = 3;
		BarberShopPlugin.neckWeldBand = 3;
		clientThread.invoke(this::unpaint);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		BarberShopPlugin.heightNudge = config.heightNudge();
		BarberShopPlugin.neckCut = config.neckCut();
		BarberShopPlugin.neckWeldFull = config.neckWeldFull();
		BarberShopPlugin.neckWeldBand = Math.max(config.neckWeldBand(), config.neckWeldFull());
		showColours();

		Player player = client.getLocalPlayer();
		if (!config.paintHeadFaces() || player == null || player.getPlayerComposition() == null)
		{
			unpaint();
			return;
		}

		PlayerComposition composition = player.getPlayerComposition();
		int now = Arrays.hashCode(composition.getEquipmentIds()) * 31 + Arrays.hashCode(composition.getColors());
		Model model = player.getModel();
		if (now == appearance && model != null && ranges != null && model.getFaceColors1() == saved1Source)
		{
			return;
		}
		unpaint();
		appearance = now;

		ranges = HeadFaces.find(player);
		model = player.getModel();
		if (ranges == null || model == null || model.getFaceCount() != ranges.getFaceCount())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Barber Shop debug: head not found (" + HeadFaces.lastProblem
				+ (model == null ? ", model now missing" : ", model now " + model.getFaceCount() + " faces") + ")", null);
			ranges = null;
			return;
		}

		int[] c1 = model.getFaceColors1();
		int[] c3 = model.getFaceColors3();
		saved1 = c1.clone();
		saved3 = c3.clone();
		saved1Source = c1;
		painted = model;
		for (int i = 0; i < ranges.getFaceCount(); i++)
		{
			if (ranges.contains(i) && c3[i] != -2)
			{
				c1[i] = MAGENTA;
				c3[i] = -1;
			}
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"Barber Shop debug: painted (" + HeadFaces.lastProblem + "), priorities " + priorities(model, ranges), null);
	}

	/** How many found faces sit at each render priority; a clean find is almost all 6. */
	private static String priorities(Model model, HeadFaces.Ranges ranges)
	{
		byte[] pri = model.getFaceRenderPriorities();
		if (pri == null)
		{
			return "none";
		}
		int[] counts = new int[256];
		for (int i = 0; i < ranges.getFaceCount(); i++)
		{
			if (ranges.contains(i))
			{
				counts[pri[i] & 0xFF]++;
			}
		}
		StringBuilder out = new StringBuilder();
		for (int p = 0; p < counts.length; p++)
		{
			if (counts[p] > 0)
			{
				out.append(out.length() == 0 ? "" : " ").append(p).append(':').append(counts[p]);
			}
		}
		return out.toString();
	}

	private void unpaint()
	{
		if (painted != null && saved1Source != null && painted.getFaceColors1() == saved1Source)
		{
			System.arraycopy(saved1, 0, painted.getFaceColors1(), 0, saved1.length);
			System.arraycopy(saved3, 0, painted.getFaceColors3(), 0, saved3.length);
		}
		painted = null;
		saved1Source = null;
		ranges = null;
		appearance = 0;
	}

	private String lastColours = "";

	private void showColours()
	{
		Player player = client.getLocalPlayer();
		if (!config.showColours() || player == null || player.getPlayerComposition() == null)
		{
			lastColours = "";
			return;
		}
		PlayerComposition composition = player.getPlayerComposition();
		int skin = BarberShopPlugin.playerSkin(composition);
		String now = "body " + composition.getGender() + ", colours " + Arrays.toString(composition.getColors())
			+ ", skin picked " + skin + ", own head skin " + skinLightness(player.getModel(), BarberShopPlugin.debugRanges, skin, true)
			+ ", own body skin " + skinLightness(player.getModel(), BarberShopPlugin.debugRanges, skin, false)
			+ ", NPC head skin " + skinLightness(BarberShopPlugin.debugHeadModel, null, skin, true);
		if (!now.equals(lastColours))
		{
			lastColours = now;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Barber Shop debug: " + now, null);
		}
	}

	/**
	 * Average baked lightness (0-127) of a model's faces in the skin's hue and saturation, from the
	 * first two corner colours (the third may be the hidden mark); with ranges, only faces in them
	 * (head) or only faces outside them (body).
	 */
	private static String skinLightness(Model model, HeadFaces.Ranges ranges, int skin, boolean head)
	{
		if (model == null || skin < 0)
		{
			return "-";
		}
		int hueSat = skin >> 7;
		int[] c1 = model.getFaceColors1();
		int[] c2 = model.getFaceColors2();
		long total = 0;
		int count = 0;
		for (int face = 0; face < model.getFaceCount(); face++)
		{
			if (ranges != null && (ranges.getFaceCount() != model.getFaceCount() || ranges.contains(face) != head))
			{
				continue;
			}
			for (int c : new int[]{c1[face], c2[face]})
			{
				if (c >= 0 && (c >> 7) == hueSat)
				{
					total += c & 127;
					count++;
				}
			}
		}
		return count == 0 ? "none" : (total / count) + " (" + count + ")";
	}

	@Provides
	BarberShopDebugConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BarberShopDebugConfig.class);
	}
}
