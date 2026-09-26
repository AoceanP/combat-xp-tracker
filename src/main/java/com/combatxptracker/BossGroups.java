/*
 * Copyright (c) 2026, AoceanP <https://github.com/AoceanP>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.combatxptracker;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bosses fought as several NPCs, shown as one card in the Monsters tab.
 *
 * Only groups whose NPC names are certain are listed. Each group says which members'
 * deaths count as a kill, and whether deaths close together are one kill (both Royal
 * Titans die in the same fight).
 */
public enum BossGroups
{
	ROYAL_TITANS("Royal Titans", 50,
		names("Branda the Fire Queen", "Eldric the Ice King"),
		names("Branda the Fire Queen", "Eldric the Ice King")),
	GROTESQUE_GUARDIANS("Grotesque Guardians", 50,
		names("Dusk", "Dawn"),
		names("Dusk", "Dawn")),
	// Nex's minions are fought during her fight; only Nex's own death is a kill.
	NEX("Nex", 0,
		names("Nex", "Fumus", "Umbra", "Cruor", "Glacies"),
		names("Nex")),
	// Each brother is a separate kill.
	BARROWS("Barrows brothers", 0,
		names("Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"),
		names("Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"));

	private static final Map<String, BossGroups> BY_MEMBER = new HashMap<>();

	static
	{
		for (BossGroups group : values())
		{
			for (String member : group.members)
			{
				BY_MEMBER.put(member, group);
			}
		}
	}

	private final String displayName;
	private final int sameKillTicks;
	private final Set<String> members;
	private final Set<String> killMembers;

	BossGroups(String displayName, int sameKillTicks, Set<String> members, Set<String> killMembers)
	{
		this.displayName = displayName;
		this.sameKillTicks = sameKillTicks;
		this.members = members;
		this.killMembers = killMembers;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/**
	 * Deaths of members within this many ticks of each other are one kill (0 = every
	 * counted death is its own kill).
	 */
	public int getSameKillTicks()
	{
		return sameKillTicks;
	}

	public Set<String> getMembers()
	{
		return members;
	}

	/**
	 * @return the group an NPC belongs to, or null
	 */
	public static BossGroups of(String npcName)
	{
		return npcName == null ? null : BY_MEMBER.get(npcName.toLowerCase(Locale.ROOT));
	}

	/**
	 * The name a monster is tracked under: the group's name for group members, otherwise
	 * the NPC's own name.
	 */
	public static String trackedName(String npcName)
	{
		BossGroups group = of(npcName);
		return group != null ? group.displayName : npcName;
	}

	/**
	 * Whether this NPC dying counts toward the kill count (Nex's minions don't).
	 */
	public static boolean countsAsKill(String npcName)
	{
		BossGroups group = of(npcName);
		return group == null || group.killMembers.contains(npcName.toLowerCase(Locale.ROOT));
	}

	private static Set<String> names(String... names)
	{
		Set<String> set = new HashSet<>();
		for (String name : Arrays.asList(names))
		{
			set.add(name.toLowerCase(Locale.ROOT));
		}
		return Collections.unmodifiableSet(set);
	}
}
