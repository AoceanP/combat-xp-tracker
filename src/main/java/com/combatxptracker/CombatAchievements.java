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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Combat Achievement tasks for each boss and which are done, read from the game's own data:
 * the same lists the in-game Combat Achievements interface shows. Loaded on the client
 * thread; read from any thread.
 */
final class CombatAchievements
{
	// The task lists for each tier, Easy to Grandmaster.
	private static final int[] TIER_ENUMS = {3981, 3982, 3983, 3984, 3985, 3986};
	private static final String[] TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};
	// Boss id -> boss name.
	private static final int BOSS_NAMES_ENUM = 3971;
	// Params on each task's struct.
	private static final int PARAM_TASK_ID = 1306;
	private static final int PARAM_NAME = 1308;
	private static final int PARAM_DESCRIPTION = 1309;
	private static final int PARAM_TYPE = 1311;
	private static final int PARAM_BOSS = 1312;
	private static final int TYPE_SPEED = 6;
	// One bit per task id, 32 to a varp.
	private static final int[] COMPLETED_VARPS = {
		VarPlayerID.CA_TASK_COMPLETED_0,
		VarPlayerID.CA_TASK_COMPLETED_1,
		VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3,
		VarPlayerID.CA_TASK_COMPLETED_4,
		VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6,
		VarPlayerID.CA_TASK_COMPLETED_7,
		VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9,
		VarPlayerID.CA_TASK_COMPLETED_10,
		VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12,
		VarPlayerID.CA_TASK_COMPLETED_13,
		VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15,
		VarPlayerID.CA_TASK_COMPLETED_16,
		VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18,
		VarPlayerID.CA_TASK_COMPLETED_19,
		VarPlayerID.CA_TASK_COMPLETED_20
	};
	// "in 1:15", "under 2:30.00" and the like.
	private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,2}))?");

	static final class Task
	{
		private final String name;
		private final String description;
		private final String tier;
		private final boolean speed;
		private final boolean done;

		Task(String name, String description, String tier, boolean speed, boolean done)
		{
			this.name = name;
			this.description = description;
			this.tier = tier;
			this.speed = speed;
			this.done = done;
		}

		String getName()
		{
			return name;
		}

		String getDescription()
		{
			return description;
		}

		String getTier()
		{
			return tier;
		}

		boolean isDone()
		{
			return done;
		}

		/**
		 * @return a speed task's time limit in seconds, or -1
		 */
		double getTimeLimit()
		{
			return speed ? parseTime(description) : -1;
		}
	}

	private volatile Map<String, List<Task>> byBoss = Collections.emptyMap();
	private volatile int revision;

	static boolean isCompletionVarp(int varpId)
	{
		for (int varp : COMPLETED_VARPS)
		{
			if (varp == varpId)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Reads every task and whether it's done. Client thread only.
	 */
	void load(Client client)
	{
		EnumComposition bosses = client.getEnum(BOSS_NAMES_ENUM);
		Map<String, List<Task>> loaded = new HashMap<>();
		for (int tier = 0; tier < TIER_ENUMS.length; tier++)
		{
			EnumComposition tasks = client.getEnum(TIER_ENUMS[tier]);
			if (tasks == null)
			{
				continue;
			}
			for (int structId : tasks.getIntVals())
			{
				StructComposition struct = client.getStructComposition(structId);
				if (struct == null)
				{
					continue;
				}
				String boss = bosses == null ? null : bosses.getStringValue(struct.getIntValue(PARAM_BOSS));
				if (boss == null || boss.isEmpty())
				{
					continue;
				}
				int id = struct.getIntValue(PARAM_TASK_ID);
				int varp = id >= 0 ? id / 32 : -1;
				boolean done = varp >= 0 && varp < COMPLETED_VARPS.length
					&& (client.getVarpValue(COMPLETED_VARPS[varp]) & (1 << (id % 32))) != 0;
				loaded.computeIfAbsent(key(boss), k -> new ArrayList<>()).add(new Task(struct.getStringValue(PARAM_NAME),
					struct.getStringValue(PARAM_DESCRIPTION), TIERS[tier], struct.getIntValue(PARAM_TYPE) == TYPE_SPEED, done));
			}
		}
		byBoss = loaded;
		revision++;
	}

	void clear()
	{
		byBoss = Collections.emptyMap();
		revision++;
	}

	int getRevision()
	{
		return revision;
	}

	private static String key(String boss)
	{
		return boss.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * @return a boss's tasks, easiest first, or an empty list
	 */
	List<Task> tasks(String boss)
	{
		List<Task> tasks = boss == null ? null : byBoss.get(key(boss));
		return tasks == null ? Collections.emptyList() : tasks;
	}

	/**
	 * e.g. "Combat Achievements: 5/9", or null with no tasks.
	 */
	static String label(List<Task> tasks)
	{
		if (tasks.isEmpty())
		{
			return null;
		}
		int done = 0;
		for (Task t : tasks)
		{
			done += t.isDone() ? 1 : 0;
		}
		return "Combat Achievements: " + done + "/" + tasks.size();
	}

	/**
	 * Each task on its own line, with how your best kill compares for speed tasks.
	 *
	 * @param personalBest your fastest kill in seconds, or 0 if none
	 */
	static String tooltip(List<Task> tasks, double personalBest)
	{
		StringBuilder sb = new StringBuilder("<html>");
		for (Task t : tasks)
		{
			sb.append(t.isDone() ? "<font color='#60dc8c'>Done</font> " : "To do ")
				.append('[').append(t.getTier()).append("] ").append(t.getName()).append(": ").append(t.getDescription());
			double limit = t.getTimeLimit();
			if (!t.isDone() && limit > 0 && personalBest > 0)
			{
				sb.append(personalBest <= limit ? " <font color='#ffc640'>(your PB " : " (your PB ")
					.append(BossTimes.format(personalBest)).append(personalBest <= limit ? " is fast enough)</font>" : ")");
			}
			sb.append("<br>");
		}
		return sb.append("</html>").toString();
	}

	/**
	 * "Kill Vorkath in 1:15 or less" -> 75. -1 without a time.
	 */
	static double parseTime(String text)
	{
		Matcher m = text == null ? null : TIME.matcher(text);
		if (m == null || !m.find())
		{
			return -1;
		}
		double seconds = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
		if (m.group(3) != null)
		{
			seconds += Double.parseDouble("0." + m.group(3));
		}
		return seconds;
	}
}
