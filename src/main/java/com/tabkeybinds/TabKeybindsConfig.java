package com.tabkeybinds;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("tabkeybinds")
public interface TabKeybindsConfig extends Config
{
	int OFFSET_INPUT_MIN = -20;
	int OFFSET_INPUT_MAX = 20;

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
		keyName = "globalPosition",
		name = "Position",
		description = "Global position, used on every tab unless overridden per tab below",
		position = 2
	)
	default TabTextPosition globalPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@ConfigItem(
		keyName = "useGlobalPosition",
		name = "Use global position",
		description = "If checked, the global position above is used for every tab, overriding any per-tab position set below",
		position = 3
	)
	default boolean useGlobalPosition()
	{
		return false;
	}

	@ConfigItem(
		keyName = "textCase",
		name = "Text case",
		description = "Casing used for the keybind text on every tab (not configurable per tab)",
		position = 4
	)
	default TabTextCase textCase()
	{
		return TabTextCase.DEFAULT;
	}

	@ConfigSection(
		name = "Combat",
		description = "Settings for the Combat tab",
		position = 10,
		closedByDefault = true
	)
	String combatSection = "combatSection";

	@ConfigItem(keyName = "combatColor", name = "Color", description = "Text color for the Combat tab (used only if Use global color is off)", position = 0, section = combatSection)
	default Color combatColor()
	{
		return new Color(0xFF4646);
	}

	@ConfigItem(keyName = "combatPosition", name = "Position", description = "Where the keybind text is anchored on the Combat tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = combatSection)
	default TabTextPosition combatPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "combatXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = combatSection)
	default int combatXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "combatYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = combatSection)
	default int combatYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Skills",
		description = "Settings for the Skills tab",
		position = 11,
		closedByDefault = true
	)
	String skillsSection = "skillsSection";

	@ConfigItem(keyName = "statsColor", name = "Color", description = "Text color for the Skills tab (used only if Use global color is off)", position = 0, section = skillsSection)
	default Color statsColor()
	{
		return new Color(0x42FF58);
	}

	@ConfigItem(keyName = "statsPosition", name = "Position", description = "Where the keybind text is anchored on the Skills tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = skillsSection)
	default TabTextPosition statsPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "statsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = skillsSection)
	default int statsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "statsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = skillsSection)
	default int statsYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Quests",
		description = "Settings for the Quests tab",
		position = 12,
		closedByDefault = true
	)
	String questsSection = "questsSection";

	@ConfigItem(keyName = "questsColor", name = "Color", description = "Text color for the Quests tab (used only if Use global color is off)", position = 0, section = questsSection)
	default Color questsColor()
	{
		return new Color(0x19C6FF);
	}

	@ConfigItem(keyName = "questsPosition", name = "Position", description = "Where the keybind text is anchored on the Quests tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = questsSection)
	default TabTextPosition questsPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "questsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = questsSection)
	default int questsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "questsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = questsSection)
	default int questsYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Inventory",
		description = "Settings for the Inventory tab",
		position = 13,
		closedByDefault = true
	)
	String inventorySection = "inventorySection";

	@ConfigItem(keyName = "inventoryColor", name = "Color", description = "Text color for the Inventory tab (used only if Use global color is off)", position = 0, section = inventorySection)
	default Color inventoryColor()
	{
		return new Color(0xAE6C17);
	}

	@ConfigItem(keyName = "inventoryPosition", name = "Position", description = "Where the keybind text is anchored on the Inventory tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = inventorySection)
	default TabTextPosition inventoryPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "inventoryXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = inventorySection)
	default int inventoryXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "inventoryYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = inventorySection)
	default int inventoryYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Equipment",
		description = "Settings for the Equipment tab",
		position = 14,
		closedByDefault = true
	)
	String equipmentSection = "equipmentSection";

	@ConfigItem(keyName = "equipmentColor", name = "Color", description = "Text color for the Equipment tab (used only if Use global color is off)", position = 0, section = equipmentSection)
	default Color equipmentColor()
	{
		return new Color(0xD5D5D5);
	}

	@ConfigItem(keyName = "equipmentPosition", name = "Position", description = "Where the keybind text is anchored on the Equipment tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = equipmentSection)
	default TabTextPosition equipmentPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "equipmentXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = equipmentSection)
	default int equipmentXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "equipmentYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = equipmentSection)
	default int equipmentYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Prayer",
		description = "Settings for the Prayer tab",
		position = 15,
		closedByDefault = true
	)
	String prayerSection = "prayerSection";

	@ConfigItem(keyName = "prayerColor", name = "Color", description = "Text color for the Prayer tab (used only if Use global color is off)", position = 0, section = prayerSection)
	default Color prayerColor()
	{
		return new Color(0x7DE9FF);
	}

	@ConfigItem(keyName = "prayerPosition", name = "Position", description = "Where the keybind text is anchored on the Prayer tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = prayerSection)
	default TabTextPosition prayerPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "prayerXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = prayerSection)
	default int prayerXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "prayerYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = prayerSection)
	default int prayerYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Spellbook",
		description = "Settings for the Spellbook tab",
		position = 16,
		closedByDefault = true
	)
	String spellbookSection = "spellbookSection";

	@ConfigItem(keyName = "spellbookColor", name = "Color", description = "Text color for the Spellbook tab (used only if Use global color is off)", position = 0, section = spellbookSection)
	default Color spellbookColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "spellbookPosition", name = "Position", description = "Where the keybind text is anchored on the Spellbook tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = spellbookSection)
	default TabTextPosition spellbookPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "spellbookXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = spellbookSection)
	default int spellbookXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "spellbookYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = spellbookSection)
	default int spellbookYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Clan",
		description = "Settings for the Clan tab",
		position = 17,
		closedByDefault = true
	)
	String clanSection = "clanSection";

	@ConfigItem(keyName = "clanColor", name = "Color", description = "Text color for the Clan tab (used only if Use global color is off)", position = 0, section = clanSection)
	default Color clanColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "clanPosition", name = "Position", description = "Where the keybind text is anchored on the Clan tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = clanSection)
	default TabTextPosition clanPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "clanXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = clanSection)
	default int clanXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "clanYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = clanSection)
	default int clanYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Friends",
		description = "Settings for the Friends tab",
		position = 18,
		closedByDefault = true
	)
	String friendsSection = "friendsSection";

	@ConfigItem(keyName = "friendsColor", name = "Color", description = "Text color for the Friends tab (used only if Use global color is off)", position = 0, section = friendsSection)
	default Color friendsColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "friendsPosition", name = "Position", description = "Where the keybind text is anchored on the Friends tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = friendsSection)
	default TabTextPosition friendsPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "friendsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = friendsSection)
	default int friendsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "friendsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = friendsSection)
	default int friendsYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Account Management",
		description = "Settings for the Account Management tab",
		position = 19,
		closedByDefault = true
	)
	String accountManagementSection = "accountManagementSection";

	@ConfigItem(keyName = "accountManagementColor", name = "Color", description = "Text color for the Account Management tab (used only if Use global color is off)", position = 0, section = accountManagementSection)
	default Color accountManagementColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "accountManagementPosition", name = "Position", description = "Where the keybind text is anchored on the Account Management tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = accountManagementSection)
	default TabTextPosition accountManagementPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "accountManagementXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = accountManagementSection)
	default int accountManagementXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "accountManagementYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = accountManagementSection)
	default int accountManagementYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Logout",
		description = "Settings for the Logout tab",
		position = 20,
		closedByDefault = true
	)
	String logoutSection = "logoutSection";

	@ConfigItem(keyName = "logoutColor", name = "Color", description = "Text color for the Logout tab (used only if Use global color is off)", position = 0, section = logoutSection)
	default Color logoutColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "logoutPosition", name = "Position", description = "Where the keybind text is anchored on the Logout tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = logoutSection)
	default TabTextPosition logoutPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "logoutXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = logoutSection)
	default int logoutXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "logoutYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = logoutSection)
	default int logoutYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Options",
		description = "Settings for the Options tab",
		position = 21,
		closedByDefault = true
	)
	String optionsSection = "optionsSection";

	@ConfigItem(keyName = "optionsColor", name = "Color", description = "Text color for the Options tab (used only if Use global color is off)", position = 0, section = optionsSection)
	default Color optionsColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "optionsPosition", name = "Position", description = "Where the keybind text is anchored on the Options tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = optionsSection)
	default TabTextPosition optionsPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "optionsXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = optionsSection)
	default int optionsXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "optionsYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = optionsSection)
	default int optionsYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Emotes",
		description = "Settings for the Emotes tab",
		position = 22,
		closedByDefault = true
	)
	String emotesSection = "emotesSection";

	@ConfigItem(keyName = "emotesColor", name = "Color", description = "Text color for the Emotes tab (used only if Use global color is off)", position = 0, section = emotesSection)
	default Color emotesColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "emotesPosition", name = "Position", description = "Where the keybind text is anchored on the Emotes tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = emotesSection)
	default TabTextPosition emotesPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "emotesXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = emotesSection)
	default int emotesXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "emotesYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = emotesSection)
	default int emotesYOffset()
	{
		return 0;
	}

	@ConfigSection(
		name = "Music",
		description = "Settings for the Music tab",
		position = 23,
		closedByDefault = true
	)
	String musicSection = "musicSection";

	@ConfigItem(keyName = "musicColor", name = "Color", description = "Text color for the Music tab (used only if Use global color is off)", position = 0, section = musicSection)
	default Color musicColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(keyName = "musicPosition", name = "Position", description = "Where the keybind text is anchored on the Music tab. Custom uses the X/Y offsets below, measured from centre.", position = 1, section = musicSection)
	default TabTextPosition musicPosition()
	{
		return TabTextPosition.BOTTOM_LEFT;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "musicXOffset", name = "X offset", description = "Horizontal pixel offset from centre. Only used when Position is Custom.", position = 2, section = musicSection)
	default int musicXOffset()
	{
		return 0;
	}

	@Range(min = OFFSET_INPUT_MIN, max = OFFSET_INPUT_MAX)
	@ConfigItem(keyName = "musicYOffset", name = "Y offset", description = "Vertical pixel offset from centre. Only used when Position is Custom.", position = 3, section = musicSection)
	default int musicYOffset()
	{
		return 0;
	}

}
