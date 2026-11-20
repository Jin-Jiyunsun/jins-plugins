package com.barbershop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;

/**
 * Heads that can be worn: an NPC and which of its model pieces make up its head and hair.
 * Pieces are indexes into the NPC's model list, picked from the cache by their head bones.
 */
@Getter
@RequiredArgsConstructor
public enum Head
{
	NONE("None", -1, new int[0], BodyType.A),
	ADVENTURER_ADA("Adventurer Ada", NpcID.SAILING_CREW_GENERIC_2_WORLD, new int[]{0, 6}, BodyType.B),
	AGGIE("Aggie", NpcID.AGGIE_1OP, new int[]{0, 1}, BodyType.B),
	ALI_MORRISANE("Ali Morrisane", NpcID.FEUD_ALI_M, new int[]{0, 1}, BodyType.A),
	AUBURN_CITIZEN_M_1("Auburn citizen (man 1)", NpcID.AUBURN_CITIZEN_NORMAL_M_1, new int[]{2, 5}, BodyType.A),
	AUBURN_CITIZEN_M_2("Auburn citizen (man 2)", NpcID.AUBURN_CITIZEN_NORMAL_M_2, new int[]{4, 5}, BodyType.A),
	AUBURN_CITIZEN_M_3("Auburn citizen (man 3)", NpcID.AUBURN_CITIZEN_NORMAL_M_3, new int[]{4, 5}, BodyType.A),
	AUBURN_CITIZEN_F_1("Auburn citizen (woman 1)", NpcID.AUBURN_CITIZEN_NORMAL_F_1, new int[]{4}, BodyType.B),
	AUBURN_CITIZEN_F_2("Auburn citizen (woman 2)", NpcID.AUBURN_CITIZEN_NORMAL_F_2, new int[]{4}, BodyType.B),
	AUBURN_CITIZEN_F_3("Auburn citizen (woman 3)", NpcID.AUBURN_CITIZEN_NORMAL_F_3, new int[]{4}, BodyType.B),
	AUBURN_CITIZEN_RICH_M("Auburn citizen (rich man)", NpcID.AUBURN_CITIZEN_RICH_M_1, new int[]{0, 1}, BodyType.A),
	AUBURN_CITIZEN_RICH_F("Auburn citizen (rich woman)", NpcID.AUBURN_CITIZEN_RICH_F_1, new int[]{0, 1}, BodyType.B),
	BOSUN_ZARAH("Bosun Zarah", NpcID.SAILING_CREW_FREMENNIK_WORLD, new int[]{0}, BodyType.B),
	COOK("Cook (servant)", NpcID.POH_SERVANT_COOK_WOMAN, new int[]{0, 1, 8}, BodyType.B),
	DUKE_HORACIO("Duke Horacio", NpcID.DUKE_OF_LUMBRIDGE, new int[]{0, 1}, BodyType.A),
	FATHER_AERECK("Father Aereck", NpcID.FATHER_AERECK, new int[]{0, 1}, BodyType.A),
	FORTUNATO("Fortunato", NpcID.RAG_WINE_MERCHANT, new int[]{0, 1, 2}, BodyType.A),
	GERTRUDE("Gertrude", NpcID.GERTRUDE_QUEST, new int[]{0, 1, 2, 8}, BodyType.B),
	GWYNA("Gwyna", NpcID.SAILING_BT_TRIAL_MASTER_3_1OP, new int[]{0}, BodyType.B),
	HAIRDRESSER("Hairdresser", NpcID.HAIRDRESSER, new int[]{0, 2, 7}, BodyType.A),
	HANS("Hans", NpcID.HANS, new int[]{0, 1}, BodyType.A),
	HETTY("Hetty", NpcID.HETTY, new int[]{0, 6}, BodyType.B),
	JOLLY_JIM("Jolly Jim", NpcID.SAILING_CREW_GENERIC_4_WORLD, new int[]{0, 1, 3}, BodyType.A),
	JULIET("Juliet", NpcID.JULIET, new int[]{0}, BodyType.B),
	KING_ROALD("King Roald", NpcID.KING_ROALD_CUTSCENE, new int[]{0, 1, 2}, BodyType.A),
	LEELA("Leela", NpcID.LEELA, new int[]{0, 6, 7}, BodyType.B),
	LUMBRIDGE_GUIDE("Lumbridge Guide", NpcID.LUMBRIDGE_GUIDE, new int[]{0, 1}, BodyType.A),
	NED("Ned", NpcID.NED, new int[]{0, 1, 7}, BodyType.A),
	OZIACH("Oziach", NpcID.OZIACH, new int[]{0, 1}, BodyType.A),
	PARTY_PETE("Party Pete", NpcID.PARTYROOM_PETE, new int[]{0, 1, 2, 3}, BodyType.A),
	PIRATE_PETE("Pirate Pete", NpcID._100_PIRATE, new int[]{0, 1}, BodyType.A),
	REDBEARD_FRANK("Redbeard Frank", NpcID.REDBEARD_FRANK, new int[]{0, 1, 2, 3}, BodyType.A),
	RELDO("Reldo", NpcID.RELDO_WITHBOOK, new int[]{0, 2, 7, 8}, BodyType.A),
	ROMEO("Romeo", NpcID.ROMEO, new int[]{0, 1, 2}, BodyType.A),
	ROS("Ros", NpcID.SAILING_BT_TRIAL_MASTER_2_ASSISTANT_1OP, new int[]{0, 2, 6}, BodyType.B),
	SIR_AMIK_VARZE("Sir Amik Varze", NpcID.HUNDRED_VARZE, new int[]{0, 6, 7}, BodyType.A),
	SPOTTER_VIRGINIA("Spotter Virginia", NpcID.SAILING_CREW_SPIRIT_ANGLER_WORLD, new int[]{0, 1}, BodyType.B),
	THESSALIA("Thessalia", NpcID.THESSALIA_NORMAL, new int[]{0, 5}, BodyType.B),
	VARLAMORE_CITIZEN_POOR_F_1("Varlamore citizen (poor woman 1)", NpcID.VARLAMORE_CITIZEN_POOR_F_1, new int[]{4}, BodyType.B),
	VARLAMORE_CITIZEN_POOR_F_2("Varlamore citizen (poor woman 2)", NpcID.VARLAMORE_CITIZEN_POOR_F_2, new int[]{1}, BodyType.B),
	VARLAMORE_CITIZEN_POOR_F_3("Varlamore citizen (poor woman 3)", NpcID.VARLAMORE_CITIZEN_POOR_F_3, new int[]{1}, BodyType.B),
	VARLAMORE_CITIZEN_POOR_F_4("Varlamore citizen (poor woman 4)", NpcID.VARLAMORE_CITIZEN_POOR_F_4, new int[]{4}, BodyType.B),
	VARLAMORE_CITIZEN_POOR_F_5("Varlamore citizen (poor woman 5)", NpcID.VARLAMORE_CITIZEN_POOR_F_5, new int[]{1}, BodyType.B),
	VARLAMORE_CITIZEN_NORMAL_F_1("Varlamore citizen (woman 1)", NpcID.VARLAMORE_CITIZEN_NORMAL_F_1, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_NORMAL_F_2("Varlamore citizen (woman 2)", NpcID.VARLAMORE_CITIZEN_NORMAL_F_2, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_NORMAL_F_3("Varlamore citizen (woman 3)", NpcID.VARLAMORE_CITIZEN_NORMAL_F_3, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_NORMAL_F_4("Varlamore citizen (woman 4)", NpcID.VARLAMORE_CITIZEN_NORMAL_F_4, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_NORMAL_F_5("Varlamore citizen (woman 5)", NpcID.VARLAMORE_CITIZEN_NORMAL_F_5, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_RICH_F_1("Varlamore citizen (rich woman 1)", NpcID.VARLAMORE_CITIZEN_RICH_F_1, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_RICH_F_2("Varlamore citizen (rich woman 2)", NpcID.VARLAMORE_CITIZEN_RICH_F_2, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_RICH_F_3("Varlamore citizen (rich woman 3)", NpcID.VARLAMORE_CITIZEN_RICH_F_3, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_RICH_F_4("Varlamore citizen (rich woman 4)", NpcID.VARLAMORE_CITIZEN_RICH_F_4, new int[]{0}, BodyType.B),
	VARLAMORE_CITIZEN_RICH_F_5("Varlamore citizen (rich woman 5)", NpcID.VARLAMORE_CITIZEN_RICH_F_5, new int[]{0}, BodyType.B),
	WISE_OLD_MAN("Wise Old Man", NpcID.WISE_OLD_MAN, new int[]{0, 1, 2}, BodyType.A),
	ZAFF("Zaff", NpcID.ZAFF, new int[]{0, 1}, BodyType.A);

	private final String name;
	private final int npcId;
	private final int[] pieces;
	// The body the head was made for; the two body types' necks differ in height.
	private final BodyType body;

	enum BodyType
	{
		A,
		B
	}

	@Override
	public String toString()
	{
		return name;
	}
}
