package com.barbershop;

import lombok.Value;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;

/**
 * Finds which faces of the player's model are their own hair and jaw, by building the model
 * with and without those two kits and seeing which faces go missing.
 */
final class HeadFaces
{
	/** Face ranges [start, end) of the hair and the jaw in the player's model. */
	@Value
	static class Ranges
	{
		int faceCount;
		int hairStart;
		int hairEnd;
		int jawStart;
		int jawEnd;

		boolean contains(int face)
		{
			return face >= hairStart && face < hairEnd || face >= jawStart && face < jawEnd;
		}
	}

	private HeadFaces()
	{
	}

	/** Must run on the client thread. Returns null if the head can't be told apart. */
	static Ranges find(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return null;
		}
		int[] ids = composition.getEquipmentIds();
		int hairSlot = KitType.HAIR.getIndex();
		int jawSlot = KitType.JAW.getIndex();
		int hair = ids[hairSlot];
		int jaw = ids[jawSlot];
		if (hair == 0 && jaw == 0)
		{
			return null;
		}

		int[] with = faceKeys(player.getModel());
		int[] noHair = withSlotEmptied(player, composition, ids, hairSlot);
		int[] noJaw = withSlotEmptied(player, composition, ids, jawSlot);
		if (with == null || noHair == null || noJaw == null)
		{
			return null;
		}

		int hairLength = with.length - noHair.length;
		int jawLength = with.length - noJaw.length;
		int hairStart = hairLength > 0 ? bestBlock(with, noHair) : 0;
		int jawStart = jawLength > 0 ? bestBlock(with, noJaw) : 0;
		if (hairLength <= 0 && jawLength <= 0)
		{
			return null;
		}
		return new Ranges(with.length, hairStart, hairStart + Math.max(hairLength, 0), jawStart, jawStart + Math.max(jawLength, 0));
	}

	private static int[] withSlotEmptied(Player player, PlayerComposition composition, int[] ids, int slot)
	{
		int kept = ids[slot];
		ids[slot] = 0;
		composition.setHash();
		int[] keys = faceKeys(player.getModel());
		ids[slot] = kept;
		composition.setHash();
		return keys;
	}

	/**
	 * Where a block of faces was cut out of {@code with} to give {@code without}. Faces near the cut
	 * can shade differently once it's gone, so this takes the spot where the fewest others disagree.
	 */
	private static int bestBlock(int[] with, int[] without)
	{
		int n = without.length;
		int cut = with.length - n;
		// before[x]: faces disagreeing in front of a block at x; after[x]: behind it.
		int[] before = new int[n + 1];
		for (int i = 0; i < n; i++)
		{
			before[i + 1] = before[i] + (with[i] != without[i] ? 1 : 0);
		}
		int[] after = new int[n + 1];
		for (int i = n - 1; i >= 0; i--)
		{
			after[i] = after[i + 1] + (with[i + cut] != without[i] ? 1 : 0);
		}
		int best = 0;
		for (int x = 1; x <= n; x++)
		{
			if (before[x] + after[x] < before[best] + after[best])
			{
				best = x;
			}
		}
		return best;
	}

	/** Each face's own colour, unlit if the model still has them, else lit. */
	private static int[] faceKeys(Model model)
	{
		if (model == null)
		{
			return null;
		}
		int count = model.getFaceCount();
		int[] keys = new int[count];
		short[] unlit = model.getUnlitFaceColors();
		int[] lit = model.getFaceColors1();
		for (int i = 0; i < count; i++)
		{
			keys[i] = unlit != null ? unlit[i] : lit[i];
		}
		return keys;
	}
}
