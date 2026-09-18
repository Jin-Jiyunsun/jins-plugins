package com.seteffects;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every {@link EffectFamily} needs a matching per-family toggle in the config (and vice versa,
 * apart from the master toggles and the warm clothing dropdown) - a mismatched key wouldn't fail at runtime, the toggle would
 * just silently do nothing. Reflection is fine here: this is test code, not shipped plugin code.
 */
public class EffectFamilyConfigTest
{
	private static final Set<String> MASTER_TOGGLES = Set.of("showSetEffectList", "showTooltips", "verbose", "warmClothing");

	@Test
	public void everyFamilyHasAMatchingConfigItemAndViceVersa()
	{
		Set<String> configKeys = new HashSet<>();
		for (Method method : SetEffectDisplayConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				assertEquals("method name should equal its keyName", method.getName(), item.keyName());
				configKeys.add(item.keyName());
			}
		}

		Set<String> familyKeys = new HashSet<>();
		for (EffectFamily family : EffectFamily.values())
		{
			assertTrue("duplicate config key " + family.configKey, familyKeys.add(family.configKey));
			assertTrue("no @ConfigItem for " + family + " (" + family.configKey + ")", configKeys.contains(family.configKey));
		}

		configKeys.removeAll(MASTER_TOGGLES);
		configKeys.removeAll(familyKeys);
		assertTrue("@ConfigItems with no EffectFamily: " + configKeys, configKeys.isEmpty());
	}

	@Test
	public void everyFamilyIsActuallyUsedByTheData()
	{
		Set<EffectFamily> used = new HashSet<>();
		SetEffectsData.ITEM_SETS.forEach(set -> used.add(set.getFamily()));
		SetEffectsData.SINGLE_ITEM_EFFECTS.values().forEach(single -> used.add(single.getFamily()));

		// Families produced by the dedicated resolvers instead of ItemSet/SingleItemEffect
		used.add(EffectFamily.VOID_KNIGHT);
		used.add(EffectFamily.SALVE_AMULET);
		used.add(EffectFamily.BLACK_MASK);
		used.add(EffectFamily.SLAYER_HELMET);
		used.add(EffectFamily.RADAS_BLESSING);
		used.add(EffectFamily.ABYSSAL_LANTERN);
		used.add(EffectFamily.GHOSTSPEAK);
		used.add(EffectFamily.GLOVES_OF_SILENCE);
		used.add(EffectFamily.RING_OF_THE_GODS);
		used.add(EffectFamily.ENCHANTED_BOLTS);
		used.add(EffectFamily.SHAYZIEN);
		used.add(EffectFamily.VANILLA_ONLY_SETS);
		used.add(EffectFamily.CAPES_OF_ACCOMPLISHMENT);
		used.add(EffectFamily.INQUISITORS);
		used.add(EffectFamily.VIRTUS);
		used.add(EffectFamily.CRYSTAL_ARMOUR);
		used.add(EffectFamily.SWAMPBARK);
		used.add(EffectFamily.BLOODBARK);
		used.add(EffectFamily.GRACEFUL);
		PerPieceSets.SkillingOutfit.ALL.forEach(outfit -> used.add(outfit.family));
		used.add(EffectFamily.AMULET_OF_THE_DAMNED);

		for (EffectFamily family : EffectFamily.values())
		{
			assertTrue(family + " is never used by any tracked item", used.contains(family));
		}
	}
}
