package com.barbershop;

/** A head that can be worn: an NPC and which of its model pieces make up its head and hair. */
interface Head
{
	String getName();

	int getNpcId();

	int[] getPieces();

	/** Whether this is the "None" choice, wearing the player's own head. */
	default boolean isNone()
	{
		return getNpcId() == -1;
	}
}
