package com.barbershop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;

/**
 * Earrings that can be added to any head. They're part of their NPC's head model, so each names
 * that model piece and the colours only the earring's faces use; the rest of the piece is hidden.
 * Any head built from that model is worn without the earring, so this setting alone decides whether
 * there is one. A pair already has one on each ear; a single one is on the right and mirrored for the left.
 */
@Getter
@RequiredArgsConstructor
public enum Earring
{
	NONE("None", -1, -1, new short[0], false),
	// Gwyna's.
	FISH("Fish", NpcID.SAILING_BT_TRIAL_MASTER_3_1OP, 0, new short[]{-28476, -28460, -28452, -28018, -28010, -27766}, false),
	// Eleanor's, a pair.
	GEM("Gem", NpcID.PORT_ROBERTS_GEM_TRADER, 0, new short[]{23853}, true),
	// Adventurer Ada's.
	STUD("Stud", NpcID.SAILING_CREW_GENERIC_2_WORLD, 0, new short[]{8125}, false);

	private final String name;
	private final int npcId;
	private final int piece;
	private final short[] colours;
	private final boolean pair;

	@Override
	public String toString()
	{
		return name;
	}
}
