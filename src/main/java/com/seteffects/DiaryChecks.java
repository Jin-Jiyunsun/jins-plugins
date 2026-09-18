package com.seteffects;

import java.util.function.BooleanSupplier;

/**
 * Achievement Diary completion, read lazily: each lookup reads a varbit on the client thread, so
 * it's only made when something being described actually depends on it (enchanted bolts, the
 * Slayer helmet standing in for a Shayzien helm, and the gloves of silence), never per frame just in case.
 */
final class DiaryChecks
{
	private final BooleanSupplier hardKandarin;
	private final BooleanSupplier hardKourend;
	private final BooleanSupplier hardArdougne;

	DiaryChecks(BooleanSupplier hardKandarin, BooleanSupplier hardKourend, BooleanSupplier hardArdougne)
	{
		this.hardArdougne = hardArdougne;
		this.hardKandarin = hardKandarin;
		this.hardKourend = hardKourend;
	}

	boolean hardKandarin()
	{
		return hardKandarin.getAsBoolean();
	}

	boolean hardKourend()
	{
		return hardKourend.getAsBoolean();
	}

	boolean hardArdougne()
	{
		return hardArdougne.getAsBoolean();
	}
}
