package com.barbershop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;

/**
 * Heads made for body type B, mostly women. Pieces are indexes into the NPC's model list, picked from
 * the cache by their head bones.
 */
@Getter
@RequiredArgsConstructor
public enum BodyTypeBHead implements Head
{
	NONE("None", -1, new int[0]),
	// Varlamore citizen (woman 2)
	VARLAMORE_CITIZEN_NORMAL_F_3("Blunt bob", NpcID.VARLAMORE_CITIZEN_NORMAL_F_3, new int[]{0}),
	// Cook (servant)
	COOK("Bow tail", NpcID.POH_SERVANT_COOK_WOMAN, new int[]{0, 1, 8}),
	// Port master (Prifddinas)
	PORT_MASTER_PRIFDDINAS("Elven half-up", NpcID.PORT_TASK_MASTER_PRIFDDINAS, new int[]{0}),
	// Gwyna
	GWYNA("Elven high tail", NpcID.SAILING_BT_TRIAL_MASTER_3_1OP, new int[]{0}),
	// Auburn citizen (woman 1)
	AUBURN_CITIZEN_F_1("Feather fan", NpcID.AUBURN_CITIZEN_NORMAL_F_1, new int[]{4}),
	// Ros
	ROS("Flower curls", NpcID.SAILING_BT_TRIAL_MASTER_2_ASSISTANT_1OP, new int[]{0, 2, 6}),
	// Leela
	LEELA("Headband bob", NpcID.LEELA, new int[]{0, 6, 7}),
	// Auburn citizen (woman 3)
	AUBURN_CITIZEN_F_3("Leaf buns", NpcID.AUBURN_CITIZEN_NORMAL_F_3, new int[]{4}),
	// Aub
	AUB("Leaf hat", NpcID.FARMING_GARDENER_TREE_7, new int[]{0, 1}),
	// Sailor (Red Rock, woman 2)
	RED_ROCK_SAILOR_F_2("Long bandana", NpcID.RED_ROCK_SAILOR_F_2, new int[]{0, 1}),
	// Auburn citizen (rich woman)
	AUBURN_CITIZEN_RICH_F("Long feathered", NpcID.AUBURN_CITIZEN_RICH_F_1, new int[]{0, 1}),
	// Bosun Zarah
	BOSUN_ZARAH("Long half-up", NpcID.SAILING_CREW_FREMENNIK_WORLD, new int[]{0}),
	// Shipwright Sasha
	SHIPWRIGHT_SASHA("Long layered", NpcID.SAILING_SHIPWRIGHT_LANDS_END, new int[]{0, 1}),
	// Eleanor
	ELEANOR("Long parted", NpcID.PORT_ROBERTS_GEM_TRADER, new int[]{0, 4}),
	// Banker
	BANKER("Long parting", NpcID.TFA_LOBBY_BANKER, new int[]{0, 6}),
	// Adventurer Ada
	ADVENTURER_ADA("Long swept", NpcID.SAILING_CREW_GENERIC_2_WORLD, new int[]{0, 6}),
	// Captain Maclean
	CAPTAIN_MACLEAN("Long wavy", NpcID.RED_ROCK_CAPTAIN, new int[]{0, 1, 2}),
	// Varlamore citizen (woman 4)
	VARLAMORE_CITIZEN_NORMAL_F_5("Low buns", NpcID.VARLAMORE_CITIZEN_NORMAL_F_5, new int[]{0}),
	// Varlamore citizen (rich woman 1)
	VARLAMORE_CITIZEN_RICH_F_1("Medium bangs", NpcID.VARLAMORE_CITIZEN_RICH_F_1, new int[]{0}),
	// Receptionist (Red Rock)
	RED_ROCK_RECEPTIONIST("Messy bun", NpcID.TRR_RED_ROCK_RECEPTIONIST, new int[]{0, 1}),
	// Varlamore citizen (woman 3)
	VARLAMORE_CITIZEN_NORMAL_F_4("Short crop", NpcID.VARLAMORE_CITIZEN_NORMAL_F_4, new int[]{0}),
	// Auburn citizen (woman 2)
	AUBURN_CITIZEN_F_2("Short straight", NpcID.AUBURN_CITIZEN_NORMAL_F_2, new int[]{4}),
	// Varlamore citizen (rich woman 2)
	VARLAMORE_CITIZEN_RICH_F_3("Shoulder length", NpcID.VARLAMORE_CITIZEN_RICH_F_3, new int[]{0}),
	// Mercenary (Port Roberts)
	MERCENARY("Side swept", NpcID.PORT_ROBERTS_BAR_PATRON, new int[]{0}),
	// Litlihui, Teoki of Ralos
	LITLIHUI("Spiked tail", NpcID.AUBURN_TEOKI_RALOS, new int[]{0, 1}),
	// Crew Registrar
	CREW_REGISTRAR("Top bun", NpcID.SAILING_CREW_MANAGER_1OP, new int[]{0, 5}),
	// Sailor (Red Rock, woman 1)
	RED_ROCK_SAILOR_F_1("Top knot", NpcID.RED_ROCK_SAILOR_F_1, new int[]{0, 1}),
	// Rebecca
	REBECCA("Twin buns bangs", NpcID.BAS_REBECCA, new int[]{0}),
	// Oarswoman Olga
	OARSWOMAN_OLGA("Wavy bandana", NpcID.SAILING_CREW_WEREWOLF_WORLD, new int[]{0});

	private final String name;
	private final int npcId;
	private final int[] pieces;

	@Override
	public String toString()
	{
		return name;
	}
}
