package com.tabkeybinds;

import java.awt.Color;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.ModifierlessKeybind;
import net.runelite.client.config.Range;

@ConfigGroup("tabkeybinds")
public interface TabKeybindsConfig extends Config
{
	// Shared "not set" default for every ModifierlessKeybind item below.
	ModifierlessKeybind NO_KEYBIND = new ModifierlessKeybind(KeyEvent.VK_UNDEFINED, 0);

	// Input range for the offset fields. The dynamic per-tab bounds-clamp
	// in the overlay is the real limit on where text can end up, but the
	// panel still needs a sane range so the field doesn't accept wildly
	// larger values than any tab could ever use.
	int OFFSET_INPUT_MIN = -20;
	int OFFSET_INPUT_MAX = 20;

	/**
	 * Where a tab's keybind text is anchored within its widget bounds.
	 * CUSTOM uses that tab's X/Y offset fields, measured from the centre.
	 */
	enum TabTextPosition
	{
		CENTER("Centre"),
		TOP_LEFT("Top left"),
		TOP_RIGHT("Top right"),
		BOTTOM_LEFT("Bottom left"),
		BOTTOM_RIGHT("Bottom right"),
		CUSTOM("Custom");

		private final String label;

		TabTextPosition(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/**
	 * How the keybind text's casing is displayed. Global only, not
	 * configurable per tab.
	 */
	enum TabTextCase
	{
		DEFAULT("Default"),
		UPPERCASE("Uppercase"),
		LOWERCASE("Lowercase");

		private final String label;

		TabTextCase(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	// ============================================================
	// Global
	// ============================================================

	@ConfigItem(
		keyName = "textColor",
		name = "Text color",
		description = "Global text color, used on every tab unless overridden per tab below",
		position = 0
	)
	default Color textColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "useGlobalColor",
		name = "Use global color",
		description = "If checked, the global text color above is used for every tab, overriding any per-tab colors set below",
		position = 1
	)
	default boolean useGlobalColor()
	{
		return true;
	}

	@ConfigItem(
		keyName = "textCase",
		name = "Text case",
		description = "Casing used for the keybind text on every tab (not configurable per tab)",
		position = 2
	)
	default TabTextCase textCase()
	{
		return TabTextCase.DEFAULT;
	}

	// ============================================================
	// Combat
	// ============================================================

	@ConfigSection(
		name = "Combat",
		description = "Settings for the Combat tab",
		position = 10,
		closedByDefault = true
	)
	String combatSection = "combatSection";

	@ConfigItem(keyName = "combat", name = "Keybind", description = "Keybind shown on the Combat tab", position = 0, section = combatSection)
	default ModifierlessKeybind combatKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "combatColor", name = "Color", description = "Text color for the Combat tab (used only if Use global color is off)", position = 1, section = combatSection)
	default Color combatColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "combatPosition", name = "Position", description = "Where the keybind text is anchored on the Combat tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = combatSection)
	default TabTextPosition combatPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "combatXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = combatSection)
	default int combatXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "combatYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = combatSection)
	default int combatYOffset()
	{
		return 0;
	}

	// ============================================================
	// Skills
	// ============================================================

	@ConfigSection(
		name = "Skills",
		description = "Settings for the Skills tab",
		position = 11,
		closedByDefault = true
	)
	String skillsSection = "skillsSection";

	@ConfigItem(keyName = "stats", name = "Keybind", description = "Keybind shown on the Skills tab", position = 0, section = skillsSection)
	default ModifierlessKeybind statsKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "statsColor", name = "Color", description = "Text color for the Skills tab (used only if Use global color is off)", position = 1, section = skillsSection)
	default Color statsColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "statsPosition", name = "Position", description = "Where the keybind text is anchored on the Skills tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = skillsSection)
	default TabTextPosition statsPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "statsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = skillsSection)
	default int statsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "statsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = skillsSection)
	default int statsYOffset()
	{
		return 0;
	}

	// ============================================================
	// Quests
	// ============================================================

	@ConfigSection(
		name = "Quests",
		description = "Settings for the Quests tab",
		position = 12,
		closedByDefault = true
	)
	String questsSection = "questsSection";

	@ConfigItem(keyName = "quests", name = "Keybind", description = "Keybind shown on the Quests tab", position = 0, section = questsSection)
	default ModifierlessKeybind questsKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "questsColor", name = "Color", description = "Text color for the Quests tab (used only if Use global color is off)", position = 1, section = questsSection)
	default Color questsColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "questsPosition", name = "Position", description = "Where the keybind text is anchored on the Quests tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = questsSection)
	default TabTextPosition questsPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "questsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = questsSection)
	default int questsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "questsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = questsSection)
	default int questsYOffset()
	{
		return 0;
	}

	// ============================================================
	// Inventory
	// ============================================================

	@ConfigSection(
		name = "Inventory",
		description = "Settings for the Inventory tab",
		position = 13,
		closedByDefault = true
	)
	String inventorySection = "inventorySection";

	@ConfigItem(keyName = "inventory", name = "Keybind", description = "Keybind shown on the Inventory tab", position = 0, section = inventorySection)
	default ModifierlessKeybind inventoryKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "inventoryColor", name = "Color", description = "Text color for the Inventory tab (used only if Use global color is off)", position = 1, section = inventorySection)
	default Color inventoryColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "inventoryPosition", name = "Position", description = "Where the keybind text is anchored on the Inventory tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = inventorySection)
	default TabTextPosition inventoryPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "inventoryXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = inventorySection)
	default int inventoryXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "inventoryYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = inventorySection)
	default int inventoryYOffset()
	{
		return 0;
	}

	// ============================================================
	// Equipment
	// ============================================================

	@ConfigSection(
		name = "Equipment",
		description = "Settings for the Equipment tab",
		position = 14,
		closedByDefault = true
	)
	String equipmentSection = "equipmentSection";

	@ConfigItem(keyName = "equipment", name = "Keybind", description = "Keybind shown on the Equipment tab", position = 0, section = equipmentSection)
	default ModifierlessKeybind equipmentKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "equipmentColor", name = "Color", description = "Text color for the Equipment tab (used only if Use global color is off)", position = 1, section = equipmentSection)
	default Color equipmentColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "equipmentPosition", name = "Position", description = "Where the keybind text is anchored on the Equipment tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = equipmentSection)
	default TabTextPosition equipmentPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "equipmentXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = equipmentSection)
	default int equipmentXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "equipmentYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = equipmentSection)
	default int equipmentYOffset()
	{
		return 0;
	}

	// ============================================================
	// Prayer
	// ============================================================

	@ConfigSection(
		name = "Prayer",
		description = "Settings for the Prayer tab",
		position = 15,
		closedByDefault = true
	)
	String prayerSection = "prayerSection";

	@ConfigItem(keyName = "prayer", name = "Keybind", description = "Keybind shown on the Prayer tab", position = 0, section = prayerSection)
	default ModifierlessKeybind prayerKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "prayerColor", name = "Color", description = "Text color for the Prayer tab (used only if Use global color is off)", position = 1, section = prayerSection)
	default Color prayerColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "prayerPosition", name = "Position", description = "Where the keybind text is anchored on the Prayer tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = prayerSection)
	default TabTextPosition prayerPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "prayerXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = prayerSection)
	default int prayerXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "prayerYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = prayerSection)
	default int prayerYOffset()
	{
		return 0;
	}

	// ============================================================
	// Spellbook
	// ============================================================

	@ConfigSection(
		name = "Spellbook",
		description = "Settings for the Spellbook tab",
		position = 16,
		closedByDefault = true
	)
	String spellbookSection = "spellbookSection";

	@ConfigItem(keyName = "spellbook", name = "Keybind", description = "Keybind shown on the Spellbook tab", position = 0, section = spellbookSection)
	default ModifierlessKeybind spellbookKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "spellbookColor", name = "Color", description = "Text color for the Spellbook tab (used only if Use global color is off)", position = 1, section = spellbookSection)
	default Color spellbookColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "spellbookPosition", name = "Position", description = "Where the keybind text is anchored on the Spellbook tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = spellbookSection)
	default TabTextPosition spellbookPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "spellbookXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = spellbookSection)
	default int spellbookXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "spellbookYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = spellbookSection)
	default int spellbookYOffset()
	{
		return 0;
	}

	// ============================================================
	// Clan
	// ============================================================

	@ConfigSection(
		name = "Clan",
		description = "Settings for the Clan tab",
		position = 17,
		closedByDefault = true
	)
	String clanSection = "clanSection";

	@ConfigItem(keyName = "clan", name = "Keybind", description = "Keybind shown on the Clan tab", position = 0, section = clanSection)
	default ModifierlessKeybind clanKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "clanColor", name = "Color", description = "Text color for the Clan tab (used only if Use global color is off)", position = 1, section = clanSection)
	default Color clanColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "clanPosition", name = "Position", description = "Where the keybind text is anchored on the Clan tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = clanSection)
	default TabTextPosition clanPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "clanXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = clanSection)
	default int clanXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "clanYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = clanSection)
	default int clanYOffset()
	{
		return 0;
	}

	// ============================================================
	// Friends
	// ============================================================

	@ConfigSection(
		name = "Friends",
		description = "Settings for the Friends tab",
		position = 18,
		closedByDefault = true
	)
	String friendsSection = "friendsSection";

	@ConfigItem(keyName = "friends", name = "Keybind", description = "Keybind shown on the Friends tab", position = 0, section = friendsSection)
	default ModifierlessKeybind friendsKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "friendsColor", name = "Color", description = "Text color for the Friends tab (used only if Use global color is off)", position = 1, section = friendsSection)
	default Color friendsColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "friendsPosition", name = "Position", description = "Where the keybind text is anchored on the Friends tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = friendsSection)
	default TabTextPosition friendsPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "friendsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = friendsSection)
	default int friendsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "friendsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = friendsSection)
	default int friendsYOffset()
	{
		return 0;
	}

	// ============================================================
	// Account Management
	// ============================================================

	@ConfigSection(
		name = "Account Management",
		description = "Settings for the Account Management tab",
		position = 19,
		closedByDefault = true
	)
	String accountManagementSection = "accountManagementSection";

	@ConfigItem(keyName = "accountManagement", name = "Keybind", description = "Keybind shown on the Account Management tab", position = 0, section = accountManagementSection)
	default ModifierlessKeybind accountManagementKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "accountManagementColor", name = "Color", description = "Text color for the Account Management tab (used only if Use global color is off)", position = 1, section = accountManagementSection)
	default Color accountManagementColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "accountManagementPosition", name = "Position", description = "Where the keybind text is anchored on the Account Management tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = accountManagementSection)
	default TabTextPosition accountManagementPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "accountManagementXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = accountManagementSection)
	default int accountManagementXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "accountManagementYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = accountManagementSection)
	default int accountManagementYOffset()
	{
		return 0;
	}

	// ============================================================
	// Logout
	// ============================================================

	@ConfigSection(
		name = "Logout",
		description = "Settings for the Logout tab",
		position = 20,
		closedByDefault = true
	)
	String logoutSection = "logoutSection";

	@ConfigItem(keyName = "logout", name = "Keybind", description = "Keybind shown on the Logout tab", position = 0, section = logoutSection)
	default ModifierlessKeybind logoutKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "logoutColor", name = "Color", description = "Text color for the Logout tab (used only if Use global color is off)", position = 1, section = logoutSection)
	default Color logoutColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "logoutPosition", name = "Position", description = "Where the keybind text is anchored on the Logout tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = logoutSection)
	default TabTextPosition logoutPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "logoutXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = logoutSection)
	default int logoutXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "logoutYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = logoutSection)
	default int logoutYOffset()
	{
		return 0;
	}

	// ============================================================
	// Options
	// ============================================================

	@ConfigSection(
		name = "Options",
		description = "Settings for the Options tab",
		position = 21,
		closedByDefault = true
	)
	String optionsSection = "optionsSection";

	@ConfigItem(keyName = "options", name = "Keybind", description = "Keybind shown on the Options tab", position = 0, section = optionsSection)
	default ModifierlessKeybind optionsKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "optionsColor", name = "Color", description = "Text color for the Options tab (used only if Use global color is off)", position = 1, section = optionsSection)
	default Color optionsColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "optionsPosition", name = "Position", description = "Where the keybind text is anchored on the Options tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = optionsSection)
	default TabTextPosition optionsPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "optionsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = optionsSection)
	default int optionsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "optionsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = optionsSection)
	default int optionsYOffset()
	{
		return 0;
	}

	// ============================================================
	// Emotes
	// ============================================================

	@ConfigSection(
		name = "Emotes",
		description = "Settings for the Emotes tab",
		position = 22,
		closedByDefault = true
	)
	String emotesSection = "emotesSection";

	@ConfigItem(keyName = "emotes", name = "Keybind", description = "Keybind shown on the Emotes tab", position = 0, section = emotesSection)
	default ModifierlessKeybind emotesKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "emotesColor", name = "Color", description = "Text color for the Emotes tab (used only if Use global color is off)", position = 1, section = emotesSection)
	default Color emotesColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "emotesPosition", name = "Position", description = "Where the keybind text is anchored on the Emotes tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = emotesSection)
	default TabTextPosition emotesPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "emotesXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = emotesSection)
	default int emotesXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "emotesYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = emotesSection)
	default int emotesYOffset()
	{
		return 0;
	}

	// ============================================================
	// Music
	// ============================================================

	@ConfigSection(
		name = "Music",
		description = "Settings for the Music tab",
		position = 23,
		closedByDefault = true
	)
	String musicSection = "musicSection";

	@ConfigItem(keyName = "music", name = "Keybind", description = "Keybind shown on the Music tab", position = 0, section = musicSection)
	default ModifierlessKeybind musicKeybind()
	{
		return NO_KEYBIND;
	}

	@ConfigItem(keyName = "musicColor", name = "Color", description = "Text color for the Music tab (used only if Use global color is off)", position = 1, section = musicSection)
	default Color musicColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "musicPosition", name = "Position", description = "Where the keybind text is anchored on the Music tab. Custom uses the X/Y offsets below, measured from centre.", position = 2, section = musicSection)
	default TabTextPosition musicPosition()
	{
		return TabTextPosition.CENTER;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "musicXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 3, section = musicSection)
	default int musicXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "musicYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 4, section = musicSection)
	default int musicYOffset()
	{
		return 0;
	}

}
