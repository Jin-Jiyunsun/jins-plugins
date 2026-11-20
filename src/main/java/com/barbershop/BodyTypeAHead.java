package com.barbershop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;

/**
 * Heads made for body type A, mostly men. Pieces are indexes into the NPC's model list, picked from
 * the cache by their head bones.
 */
@Getter
@RequiredArgsConstructor
public enum BodyTypeAHead implements Head
{
	NONE("None", -1, new int[0]),
	// Father Aereck
	FATHER_AERECK("Balding", NpcID.FATHER_AERECK, new int[]{0, 1}),
	// Sailor (Red Rock, man)
	RED_ROCK_SAILOR_M("Bandana", NpcID.RED_ROCK_SAILOR_M_2, new int[]{0, 1}),
	// Ribs
	RIBS("Beanie", NpcID.SAILING_INTRO_RIBS_VIS, new int[]{0}),
	// Ned
	NED("Blue cap", NpcID.NED, new int[]{0, 1, 7}),
	// Fortunato
	FORTUNATO("Bowler", NpcID.RAG_WINE_MERCHANT, new int[]{0, 1, 2}),
	// Oisin
	OISIN("Braided beard", NpcID.WYRMSCRAIG_SMITH, new int[]{0, 1}),
	// King Roald
	KING_ROALD("Crowned", NpcID.KING_ROALD_CUTSCENE, new int[]{0, 1, 2}),
	// Worker (Port Roberts, man)
	PORT_ROBERTS_WORKER_M("Eyepatch", NpcID.PORT_ROBERTS_WORKER_M1, new int[]{0, 1}),
	// Auburn citizen (man 1)
	AUBURN_CITIZEN_M_1("Feathered bob", NpcID.AUBURN_CITIZEN_NORMAL_M_1, new int[]{2, 5}),
	// Kastori citizen (man 2)
	KASTORI_CITIZEN_M_2("Fern crown", NpcID.KASTORI_CITIZEN_NORMAL_M_3, new int[]{4, 5}),
	// Geoff
	GEOFF("Full beard", NpcID.GOAT_PIT_HELPER, new int[]{0, 1}),
	// Wise Old Man
	WISE_OLD_MAN("Goggles", NpcID.WISE_OLD_MAN, new int[]{0, 1, 2}),
	// Kastori citizen (man 1)
	KASTORI_CITIZEN_M_1("Green feather", NpcID.KASTORI_CITIZEN_NORMAL_M_1, new int[]{2, 5}),
	// Sir Amik Varze
	SIR_AMIK_VARZE("Headband", NpcID.HUNDRED_VARZE, new int[]{0, 6, 7}),
	// Pirate Pete
	PIRATE_PETE("Hood", NpcID._100_PIRATE, new int[]{0, 1}),
	// Auburn citizen (man 3)
	AUBURN_CITIZEN_M_3("Leaf crown", NpcID.AUBURN_CITIZEN_NORMAL_M_3, new int[]{4, 5}),
	// Jobless Jim
	JOBLESS_JIM("Long bandana", NpcID.SAILING_CREW_GENERIC_1_WORLD, new int[]{0, 1}),
	// Auburn bartender
	AUBURN_BARTENDER("Long braids", NpcID.AUBURN_BARTENDER, new int[]{0, 4}),
	// Theodore Paxton
	THEODORE_PAXTON("Long fringe", NpcID.TRR_THEODORE_PAXTON_VIS, new int[]{0, 1}),
	// Party Pete
	PARTY_PETE("Party crown", NpcID.PARTYROOM_PETE, new int[]{0, 1, 2, 3}),
	// Jolly Jim
	JOLLY_JIM("Pirate braids", NpcID.SAILING_CREW_GENERIC_4_WORLD, new int[]{0, 1, 3}),
	// Redbeard Frank
	REDBEARD_FRANK("Pirate captain", NpcID.REDBEARD_FRANK, new int[]{0, 1, 2, 3}),
	// Auburn citizen (rich man)
	AUBURN_CITIZEN_RICH_M("Plumed band 1", NpcID.AUBURN_CITIZEN_RICH_M_1, new int[]{0, 1}),
	// Kastori citizen (rich man)
	KASTORI_CITIZEN_RICH_M("Plumed band 2", NpcID.KASTORI_CITIZEN_RICH_M_1, new int[]{0, 1}),
	// Tal Teklan citizen (rich man)
	TAL_TEKLAN_CITIZEN_RICH_M("Plumed band 3", NpcID.TAL_TEKLAN_CITIZEN_RICH_M_1, new int[]{0, 1}),
	// Tal Teklan shopkeeper
	TAL_TEKLAN_SHOPKEEPER("Plumed bob", NpcID.TAL_TEKLAN_GENERAL_STORE, new int[]{0, 4}),
	// Captain Campbell
	CAPTAIN_CAMPBELL("Plumed hat", NpcID.PORT_ROBERTS_CAMPBELL, new int[]{0, 1, 2, 3}),
	// Reldo
	RELDO("Ribbon tail", NpcID.RELDO_WITHBOOK, new int[]{0, 2, 7, 8}),
	// Ex-Captain Siad
	EX_CAPTAIN_SIAD("Sailor cap", NpcID.SAILING_CREW_CAPTAIN_SIAD_WORLD, new int[]{0, 1, 5}),
	// Record Keeper
	RECORD_KEEPER("Scholar", NpcID.TFA_RECORD_KEEPER, new int[]{0, 1, 2}),
	// General Montoya
	GENERAL_MONTOYA("Shaggy", NpcID.GENERAL_MONTOYA, new int[]{0, 6}),
	// Auburn citizen (man 2)
	AUBURN_CITIZEN_M_2("Short quiff", NpcID.AUBURN_CITIZEN_NORMAL_M_2, new int[]{4, 5}),
	// Antos
	ANTOS("Short swept", NpcID.SOC_ANTOS, new int[]{0, 1}),
	// Hairdresser
	HAIRDRESSER("Short tail", NpcID.HAIRDRESSER, new int[]{0, 2, 7}),
	// Angus
	ANGUS("Spiky handlebar", NpcID.WYRMSCRAIG_VILLAGER_2, new int[]{0, 1}),
	// Prince Itzla Arkan
	PRINCE_ITZLA_ARKAN("Swept back", NpcID.VMQ4_ITZLA_PRISONER_CS, new int[]{3}),
	// Romeo
	ROMEO("Swept fringe", NpcID.ROMEO, new int[]{0, 1, 2}),
	// Ali Morrisane
	ALI_MORRISANE("Turban", NpcID.FEUD_ALI_M, new int[]{0, 1});

	private final String name;
	private final int npcId;
	private final int[] pieces;

	@Override
	public String toString()
	{
		return name;
	}
}
