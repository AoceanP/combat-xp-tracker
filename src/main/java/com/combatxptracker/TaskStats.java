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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The task log grouped by monster, so tasks can be ranked by the player's own Slayer XP/hr
 * and GP/hr: which are worth doing, and which to block or skip.
 */
final class TaskStats
{
	/**
	 * Tasks with less fighting time than this are left out of the rankings; a few minutes
	 * of kills says little about a whole task.
	 */
	static final long MIN_ACTIVE_MILLIS = 10 * 60_000L;

	private final String task;
	private int times;
	private int kills;
	private long activeMillis;
	private long slayerXp;
	private long loot;

	private TaskStats(String task)
	{
		this.task = task;
	}

	String getTask()
	{
		return task;
	}

	int getTimes()
	{
		return times;
	}

	int getAverageKills()
	{
		return times == 0 ? 0 : Math.round(kills / (float) times);
	}

	long getActiveMillis()
	{
		return activeMillis;
	}

	double getXpPerHour()
	{
		return ActivityTimer.perHour(slayerXp, activeMillis);
	}

	double getGpPerHour()
	{
		return ActivityTimer.perHour(loot, activeMillis);
	}

	/**
	 * One entry per task monster, in the log's order (newest first).
	 */
	static List<TaskStats> group(List<SlayerTaskRecord> records, LootPrice price)
	{
		Map<String, TaskStats> byTask = new LinkedHashMap<>();
		for (SlayerTaskRecord r : records)
		{
			if (r.task == null)
			{
				continue;
			}
			TaskStats s = byTask.computeIfAbsent(r.task.toLowerCase(Locale.ROOT), k -> new TaskStats(r.task));
			s.times++;
			s.kills += r.kills;
			s.activeMillis += r.activeMillis;
			s.slayerXp += r.slayerXp;
			s.loot += r.loot(price);
		}
		return new ArrayList<>(byTask.values());
	}

	/**
	 * Tasks with enough time logged to rank, best first by the given rate.
	 */
	static List<TaskStats> ranked(List<TaskStats> all, boolean byGp)
	{
		List<TaskStats> out = new ArrayList<>();
		for (TaskStats s : all)
		{
			if (s.activeMillis >= MIN_ACTIVE_MILLIS)
			{
				out.add(s);
			}
		}
		Comparator<TaskStats> rate = Comparator.comparingDouble(byGp ? TaskStats::getGpPerHour : TaskStats::getXpPerHour);
		out.sort(rate.reversed());
		return out;
	}

	/**
	 * @return the stats for a task name, or null if it isn't in the log
	 */
	static TaskStats find(List<TaskStats> all, String task)
	{
		if (task == null)
		{
			return null;
		}
		for (TaskStats s : all)
		{
			if (s.task.equalsIgnoreCase(task))
			{
				return s;
			}
		}
		return null;
	}
}
