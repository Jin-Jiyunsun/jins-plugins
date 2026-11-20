package com.barbershop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;

/**
 * Glasses that can be added to any head: the NPC who wears them, which of its model pieces and chathead models
 * they are, and how far to move them down and back (model units) on each body type's heads, as they're shaped
 * for their NPC's face.
 */
@Getter
@RequiredArgsConstructor
public enum Glasses
{
	NONE("None", -1, -1, -1, 0, 0, 0, 0),
	// Ros's, made for a body type B face; on type A they sank in.
	ROUND("Round", NpcID.SAILING_BT_TRIAL_MASTER_2_ASSISTANT_1OP, 2, 1, -3, -5, 0, 0),
	// Reldo's, made for a body type A face; on type B they sat too far forward and up.
	SQUARE("Square", NpcID.RELDO_WITHBOOK, 7, 2, 0, 0, 3, 3),
	// The Crew Registrar's, on the right eye.
	MONOCLE("Monocle", NpcID.SAILING_CREW_MANAGER_1OP, 5, 1, 0, 0, 0, 0);

	private final String name;
	private final int npcId;
	private final int piece;
	private final int chatPiece;
	private final int downA;
	private final int backA;
	private final int downB;
	private final int backB;

	@Override
	public String toString()
	{
		return name;
	}
}
