package com.seteffects;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.runelite.api.gameval.ItemID;

/**
 * Enchanted bolts (regular and dragon): each gem has a chance to trigger its own effect. Chances
 * depend on whether the target is a player or a monster, and completing the Hard Kandarin Diary
 * raises every chance by 10% (5.0% becomes 5.5%). Not a plain {@link SingleItemEffect} because the
 * displayed chance has to reflect the diary state, which is only read (via the supplier) when an
 * enchanted bolt is actually being described.
 */
final class EnchantedBolts
{
	private static final Map<Integer, Bolt> BOLTS = new HashMap<>();

	static
	{
		add("Opal", ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_TIPPED_OPAL_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_OPAL,
			50, 50, "strike with lightning for extra damage equal to 10% of your Ranged level.");
		add("Jade", ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_JADE,
			60, 60, "root the target for 5 seconds.");
		add("Pearl", ItemID.XBOWS_CROSSBOW_BOLTS_IRON_TIPPED_PEARL_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_PEARL,
			60, 60, "deal extra water damage. Negated by water staves, increased against fiery targets.");
		// Topaz's effect doesn't work against monsters (no PvM chance)
		add("Topaz", ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_TOPAZ,
			40, -1, "drain the target's Magic level by 1.");
		add("Sapphire", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_SAPPHIRE,
			50, 250, "drain Prayer equal to 1/20 of your Ranged level, restoring about half of it to you.");
		add("Emerald", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_EMERALD,
			540, 550, "poison the target for 5 damage.");
		add("Ruby", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY,
			110, 60, "lose 10% of your current Hitpoints and deal 20% of the target's current Hitpoints, up to 100.");
		add("Diamond", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND,
			50, 100, "ignore the target's Defence and raise max hit by 15%.");
		add("Dragonstone", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_DRAGONSTONE,
			60, 60, "deal extra fire damage equal to 20% of your Ranged level. Negated by fiery monsters and dragonfire protection.");
		add("Onyx", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_ONYX,
			100, 110, "deal 20% extra damage that heals you for 25% of the damage dealt. Not effective against the undead.");
	}

	private EnchantedBolts()
	{
	}

	private static void add(String gem, int regularId, int dragonId, int pvpTenths, int pvmTenths, String effect)
	{
		BOLTS.put(regularId, new Bolt(gem + " bolts (e)", pvpTenths, pvmTenths, effect));
		BOLTS.put(dragonId, new Bolt(gem + " dragon bolts (e)", pvpTenths, pvmTenths, effect));
	}

	static boolean isEnchantedBolt(int itemId)
	{
		return BOLTS.containsKey(itemId);
	}

	/** @param hardKandarinDiary only asked for here, i.e. only when an enchanted bolt is being described */
	static EffectLine describe(int itemId, BooleanSupplier hardKandarinDiary)
	{
		Bolt bolt = BOLTS.get(itemId);
		boolean diary = hardKandarinDiary.getAsBoolean();
		String pvp = chance(bolt.pvpTenths, diary);
		String text;
		if (bolt.pvmTenths < 0)
		{
			text = pvp + " chance in PvP to " + bolt.effect;
		}
		else if (bolt.pvmTenths == bolt.pvpTenths)
		{
			text = pvp + " chance to " + bolt.effect;
		}
		else
		{
			text = chance(bolt.pvmTenths, diary) + " chance (" + pvp + " in PvP) to " + bolt.effect;
		}
		return new EffectLine(bolt.name, text);
	}

	// The diary adds 10% of the base chance (5.0% -> 5.5%), which stays a whole number of tenths
	private static String chance(int baseTenths, boolean diary)
	{
		return EffectLineFormat.formatTenths(diary ? baseTenths * 11 / 10 : baseTenths) + "%";
	}

	private static final class Bolt
	{
		final String name;
		final int pvpTenths;
		final int pvmTenths;
		final String effect;

		Bolt(String name, int pvpTenths, int pvmTenths, String effect)
		{
			this.name = name;
			this.pvpTenths = pvpTenths;
			this.pvmTenths = pvmTenths;
			this.effect = effect;
		}
	}
}
