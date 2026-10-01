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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chances of each possible next task from a slayer master, from the game's own task
 * weights. Tasks the player can't get (levels, unlocks) or has blocked are left out.
 * Quest requirements aren't in the game data, so it's an estimate.
 */
final class TaskOdds
{
	/**
	 * One task a master can assign.
	 */
	static final class Option
	{
		private final String task;
		private final int weight;
		private final int minAmount;
		private final int maxAmount;
		// Why it can't be assigned, or null if it can.
		private final String unavailable;
		private boolean blocked;
		private double chance;

		Option(String task, int weight, int minAmount, int maxAmount, String unavailable)
		{
			this.task = task;
			this.weight = weight;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			this.unavailable = unavailable;
		}

		String getTask()
		{
			return task;
		}

		int getWeight()
		{
			return weight;
		}

		int getMinAmount()
		{
			return minAmount;
		}

		int getMaxAmount()
		{
			return maxAmount;
		}

		/**
		 * @return why the master can't give this task, or null
		 */
		String getUnavailable()
		{
			return unavailable;
		}

		boolean isBlocked()
		{
			return blocked;
		}

		/**
		 * @return the chance of getting it next, 0 to 1 (0 if blocked or unavailable)
		 */
		double getChance()
		{
			return chance;
		}

		@Override
		public String toString()
		{
			return task + "=" + weight + (blocked ? "B" : "") + (unavailable != null ? "U" : "");
		}
	}

	private final String master;
	private final List<Option> options;
	private final boolean unlocksKnown;

	TaskOdds(String master, List<Option> options, boolean unlocksKnown)
	{
		this.master = master;
		this.options = options;
		this.unlocksKnown = unlocksKnown;
	}

	static final TaskOdds NONE = new TaskOdds(null, Collections.emptyList(), true);

	/**
	 * Works out every option's chance, leaving out unavailable and blocked tasks.
	 *
	 * @param blockedLowercase task names the player marked as blocked with this master
	 */
	static TaskOdds compute(String master, List<Option> all, Set<String> blockedLowercase, boolean unlocksKnown)
	{
		long total = 0;
		for (Option o : all)
		{
			o.blocked = blockedLowercase.contains(o.task.toLowerCase(Locale.ROOT));
			o.chance = 0;
			if (o.unavailable == null && !o.blocked && o.weight > 0)
			{
				total += o.weight;
			}
		}
		for (Option o : all)
		{
			if (total > 0 && o.unavailable == null && !o.blocked && o.weight > 0)
			{
				o.chance = o.weight / (double) total;
			}
		}
		List<Option> sorted = new ArrayList<>(all);
		sorted.sort(Comparator.comparingDouble(Option::getChance).reversed().thenComparing(Option::getTask));
		return new TaskOdds(master, sorted, unlocksKnown);
	}

	String getMaster()
	{
		return master;
	}

	/**
	 * Every option, likeliest first; blocked and unavailable ones last.
	 */
	List<Option> getOptions()
	{
		return options;
	}

	/**
	 * @return false if the player's Slayer reward unlocks couldn't be read, so tasks that
	 * need one may be missing or extra
	 */
	boolean isUnlocksKnown()
	{
		return unlocksKnown;
	}

	/**
	 * The task most worth blocking: the one with a real chance of coming up that's slowest
	 * for the player, by their own Slayer XP/hr in the log. Null without enough history.
	 */
	TaskStats blockAdvice(List<TaskStats> history, double minChance)
	{
		List<TaskStats> ranked = TaskStats.ranked(history, false);
		if (ranked.size() < 3)
		{
			return null;
		}
		// Slowest first.
		Collections.reverse(ranked);
		for (TaskStats s : ranked)
		{
			// The log stores the game's own task names, so they match exactly.
			Option o = find(s.getTask());
			if (o != null && o.chance >= minChance)
			{
				return s;
			}
		}
		return null;
	}

	/**
	 * @return the option for a task name, or null
	 */
	Option find(String task)
	{
		for (Option o : options)
		{
			if (o.task.equalsIgnoreCase(task))
			{
				return o;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return master + "|" + unlocksKnown + "|" + options;
	}
}
