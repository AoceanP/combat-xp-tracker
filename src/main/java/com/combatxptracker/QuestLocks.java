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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * WHAT: Slayer tasks a master won't give until you've done a quest (OSRS Wiki, "Slayer task").
 * WHY: The game's task data has levels and unlocks but no quests, so the odds counted tasks
 * you can't get yet, like elves before Regicide (2.5).
 * PRE: None.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class QuestLocks
{
	/**
	 * WHAT: One quest a task needs, and whether starting it is enough.
	 * WHY: The wiki marks some as "partially", e.g. Dragon Slayer I for the dragons.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class Need
	{
		private final Quest quest;
		private final boolean startedEnough;

		Need(Quest quest, boolean startedEnough)
		{
			this.quest = quest;
			this.startedEnough = startedEnough;
		}

		Quest getQuest()
		{
			return quest;
		}

		/**
		 * WHAT: Whether a quest state meets this need.
		 * WHY: Started counts for a partial need; only finished counts otherwise.
		 * PRE: None.
		 * POST: Nothing changes.
		 * RETURN: true if met
		 * THROWS: Nothing.
		 */
		boolean isMetBy(QuestState state)
		{
			return state == QuestState.FINISHED || startedEnough && state == QuestState.IN_PROGRESS;
		}
	}

	// Task (lower case) -> quests, any one of which unlocks it.
	private static final Map<String, List<Need>> BY_TASK = new HashMap<>();

	static
	{
		Need priest = new Need(Quest.PRIEST_IN_PERIL, false);
		for (String task : new String[]{"Aberrant spectres", "Araxytes", "Banshees", "Bandits", "Bloodvelds",
			"Crawling hands", "Ghouls", "Gargoyles", "Infernal mages", "Nechryael", "Vampyres", "Werewolves"})
		{
			add(task, priest);
		}
		add("Abyssal demons", priest, new Need(Quest.FAIRYTALE_II__CURE_A_QUEEN, true));

		Need dragonSlayer = new Need(Quest.DRAGON_SLAYER_I, true);
		for (String task : new String[]{"Black dragons", "Blue dragons", "Green dragons", "Red dragons", "Lava dragons",
			"Metal dragons", "Bronze dragons", "Iron dragons", "Steel dragons", "Mithril dragons", "Adamant dragons",
			"Rune dragons"})
		{
			add(task, dragonSlayer);
		}

		Need cabinFever = new Need(Quest.CABIN_FEVER, false);
		add("Cave horrors", cabinFever);
		add("Jungle horrors", cabinFever);
		Need lostCity = new Need(Quest.LOST_CITY, false);
		add("Otherworldly beings", lostCity);
		add("Zygomites", lostCity);
		add("Mutated zygomites", lostCity);

		add("Brine rats", new Need(Quest.OLAFS_QUEST, false));
		add("Custodian stalkers", new Need(Quest.SHADOWS_OF_CUSTODIA, false));
		add("Dark beasts", new Need(Quest.MOURNINGS_END_PART_II, true));
		add("Dust devils", new Need(Quest.DESERT_TREASURE_I, true));
		add("Elves", new Need(Quest.REGICIDE, false));
		add("Fever spiders", new Need(Quest.RUM_DEAL, true));
		add("Fossil Island wyverns", new Need(Quest.BONE_VOYAGE, false));
		add("Gryphons", new Need(Quest.TROUBLED_TORTUGANS, false));
		add("Killerwatts", new Need(Quest.ERNEST_THE_CHICKEN, false));
		add("Lesser nagua", new Need(Quest.PERILOUS_MOONS, false));
		add("Molanisks", new Need(Quest.DEATH_TO_THE_DORGESHUUN, false));
		add("Mogres", new Need(Quest.SKIPPY_AND_THE_MOGRES, false));
		add("Scabarites", new Need(Quest.CONTACT, true));
		add("Sea snakes", new Need(Quest.ROYAL_TROUBLE, false));
		add("Shadow warriors", new Need(Quest.LEGENDS_QUEST, false));
		add("Skeletal wyverns", new Need(Quest.ELEMENTAL_WORKSHOP_I, false));
		add("Sourhogs", new Need(Quest.A_PORCINE_OF_INTEREST, false));
		add("Spiritual creatures", new Need(Quest.DEATH_PLATEAU, false));
		add("Suqahs", new Need(Quest.LUNAR_DIPLOMACY, true));
		add("Terror dogs", new Need(Quest.HAUNTED_MINE, false));
		add("Venators", new Need(Quest.THE_BLOOD_MOON_RISES, false));
		add("Warped creatures", new Need(Quest.THE_PATH_OF_GLOUPHRIE, false));
	}

	private QuestLocks()
	{
	}

	private static void add(String task, Need... needs)
	{
		BY_TASK.put(task.toLowerCase(Locale.ROOT), Collections.unmodifiableList(new ArrayList<>(List.of(needs))));
	}

	/**
	 * WHAT: The quests that unlock a task; any one is enough.
	 * WHY: Exact names only: a loose match would lock "Spiders" behind Fever spiders' Rum Deal.
	 * PRE: task may be null.
	 * POST: Nothing changes.
	 * RETURN: the needs, or an empty list for tasks with no quest
	 * THROWS: Nothing.
	 */
	static List<Need> forTask(String task)
	{
		if (task == null)
		{
			return Collections.emptyList();
		}
		List<Need> needs = BY_TASK.get(task.trim().toLowerCase(Locale.ROOT));
		return needs == null ? Collections.emptyList() : needs;
	}

	/**
	 * WHAT: Why a task is quest-locked for you, e.g. "Needs Regicide".
	 * WHY: Shown next to the task in the odds, like a missing level or reward.
	 * PRE: states gives a quest's state; it's only asked about this task's quests.
	 * POST: Nothing changes.
	 * RETURN: the reason, or null if the task isn't locked
	 * THROWS: Nothing.
	 */
	static String lockedBy(String task, Function<Quest, QuestState> states)
	{
		List<Need> needs = forTask(task);
		if (needs.isEmpty())
		{
			return null;
		}
		for (Need need : needs)
		{
			if (need.isMetBy(states.apply(need.getQuest())))
			{
				return null;
			}
		}
		Need first = needs.get(0);
		return "Needs " + first.getQuest().getName() + (first.startedEnough ? " (started)" : "");
	}
}
