package com.seteffects;

import java.util.function.BooleanSupplier;

/**
 * Achievement Diary completion, read lazily: each lookup reads a varbit on the client thread, so
 * it's only made when something being described actually depends on it (enchanted bolts and the
 * Slayer helmet standing in for a Shayzien helm), never per frame just in case.
 */
final class DiaryChecks
{
	private final BooleanSupplier hardKandarin;
	private final BooleanSupplier hardKourend;

	DiaryChecks(BooleanSupplier hardKandarin, BooleanSupplier hardKourend)
	{
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
}
