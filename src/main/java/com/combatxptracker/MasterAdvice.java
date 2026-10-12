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
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which slayer master is best for this player: each master's task chances, combined with
 * the player's own XP, GP and kills per hour on each task from their task log. Over many
 * tasks the rate is total gained over total time, so each master's expected gain per task
 * is divided by their expected time per task.
 *
 * Tasks the player hasn't logged use their average rate and average task length, and the
 * share of the master's tasks that are known is shown alongside.
 */
final class MasterAdvice
{
	/**
	 * Masters with less of their tasks known than this aren't ranked.
	 */
	static final double MIN_COVERAGE = 0.3;
	/**
	 * Logged tasks needed before comparing masters at all.
	 */
	static final int MIN_KNOWN_TASKS = 3;

	enum Goal
	{
		XP("Slayer XP"),
		GP("GP"),
		POINTS("Points");

		private final String displayName;

		Goal(String displayName)
		{
			this.displayName = displayName;
		}

		String getDisplayName()
		{
			return displayName;
		}
	}

	/**
	 * One master's expected rates for the player.
	 */
	static final class Entry
	{
		private final SlayerPoints.Master master;
		private final double xpPerHour;
		private final double gpPerHour;
		private final int pointsPerTask;
		private final double hoursPerTask;
		private final double coverage;
		private final String unavailable;
		private final boolean ranked;

		Entry(SlayerPoints.Master master, double xpPerHour, double gpPerHour, int pointsPerTask, double hoursPerTask,
			double coverage, String unavailable, boolean ranked)
		{
			this.master = master;
			this.xpPerHour = xpPerHour;
			this.gpPerHour = gpPerHour;
			this.pointsPerTask = pointsPerTask;
			this.hoursPerTask = hoursPerTask;
			this.coverage = coverage;
			this.unavailable = unavailable;
			this.ranked = ranked;
		}

		SlayerPoints.Master getMaster()
		{
			return master;
		}

		double getXpPerHour()
		{
			return xpPerHour;
		}

		double getGpPerHour()
		{
			return gpPerHour;
		}

		/**
		 * @return points for an ordinary task, or -1 if they vary (Mortimer)
		 */
		int getPointsPerTask()
		{
			return pointsPerTask;
		}

		/**
		 * @return points per hour of fighting, or -1 if unknown
		 */
		double getPointsPerHour()
		{
			return pointsPerTask < 0 || hoursPerTask <= 0 ? -1 : pointsPerTask / hoursPerTask;
		}

		double getHoursPerTask()
		{
			return hoursPerTask;
		}

		/**
		 * @return the share of this master's tasks (by chance) the player has logged
		 */
		double getCoverage()
		{
			return coverage;
		}

		/**
		 * @return why the player can't use this master, or null
		 */
		String getUnavailable()
		{
			return unavailable;
		}

		/**
		 * @return whether there's enough data to rank it
		 */
		boolean isRanked()
		{
			return ranked;
		}

		double value(Goal goal)
		{
			switch (goal)
			{
				case GP:
					return gpPerHour;
				case POINTS:
					return getPointsPerHour();
				default:
					return xpPerHour;
			}
		}
	}

	private MasterAdvice()
	{
	}

	/**
	 * Combat level (and Slayer level) a master needs (OSRS Wiki). Quests aren't checked.
	 *
	 * @return why the player can't use the master yet, or null
	 */
	static String requirement(SlayerPoints.Master master, int combatLevel, int slayerLevel)
	{
		int combat;
		switch (master)
		{
			case MAZCHNA:
				combat = 20;
				break;
			case VANNAKA:
				combat = 40;
				break;
			case CHAELDAR:
				combat = 70;
				break;
			case KONAR:
				combat = 75;
				break;
			case NIEVE:
				combat = 85;
				break;
			case DURADEL:
				if (combatLevel >= 100 && slayerLevel < 50)
				{
					return "Needs 50 Slayer";
				}
				combat = 100;
				break;
			default:
				combat = 0;
		}
		return combatLevel < combat ? "Needs " + combat + " combat" : null;
	}

	/**
	 * @param odds    every master's task chances for this player
	 * @param history the task log grouped by task
	 * @param elite   whether the player has the diary that raises a master's points
	 * @return every master with tasks, best first for the goal; unranked and unavailable last
	 */
	static List<Entry> compute(Map<SlayerPoints.Master, TaskOdds> odds, List<TaskStats> history,
		Predicate<SlayerPoints.Master> elite, int combatLevel, int slayerLevel, Goal goal)
	{
		// The player's average rates, for tasks they haven't logged.
		List<TaskStats> known = new ArrayList<>();
		long xp = 0;
		long gp = 0;
		long millis = 0;
		int times = 0;
		for (TaskStats s : history)
		{
			if (s.getActiveMillis() >= TaskStats.MIN_ACTIVE_MILLIS && s.getKills() > 0)
			{
				known.add(s);
				xp += s.getSlayerXp();
				gp += s.getLoot();
				millis += s.getActiveMillis();
				times += s.getTimes();
			}
		}
		boolean enoughHistory = known.size() >= MIN_KNOWN_TASKS;
		double hours = millis / 3_600_000.0;
		double avgXpPerHour = hours > 0 ? xp / hours : 0;
		double avgGpPerHour = hours > 0 ? gp / hours : 0;
		double avgTaskHours = times > 0 ? hours / times : 0;

		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<SlayerPoints.Master, TaskOdds> e : odds.entrySet())
		{
			SlayerPoints.Master master = e.getKey();
			double expXp = 0;
			double expGp = 0;
			double expHours = 0;
			double coverage = 0;
			boolean any = false;
			for (TaskOdds.Option o : e.getValue().getOptions())
			{
				double p = o.getChance();
				if (p <= 0)
				{
					continue;
				}
				any = true;
				TaskStats s = TaskStats.find(known, o.getTask());
				double amount = o.getMaxAmount() > 0 ? (o.getMinAmount() + o.getMaxAmount()) / 2.0 : -1;
				double taskHours;
				double taskXp;
				double taskGp;
				if (s != null)
				{
					double sHours = s.getActiveMillis() / 3_600_000.0;
					if (amount > 0)
					{
						taskHours = amount * sHours / s.getKills();
						taskXp = amount * s.getSlayerXp() / s.getKills();
						taskGp = amount * s.getLoot() / s.getKills();
					}
					else
					{
						taskHours = sHours / s.getTimes();
						taskXp = s.getSlayerXp() / (double) s.getTimes();
						taskGp = s.getLoot() / (double) s.getTimes();
					}
					coverage += p;
				}
				else
				{
					taskHours = avgTaskHours;
					taskXp = avgXpPerHour * taskHours;
					taskGp = avgGpPerHour * taskHours;
				}
				expHours += p * taskHours;
				expXp += p * taskXp;
				expGp += p * taskGp;
			}
			if (!any)
			{
				continue;
			}
			String unavailable = requirement(master, combatLevel, slayerLevel);
			boolean ranked = enoughHistory && unavailable == null && coverage >= MIN_COVERAGE && expHours > 0;
			entries.add(new Entry(master, expHours > 0 ? expXp / expHours : 0, expHours > 0 ? expGp / expHours : 0,
				master.base(elite.test(master)), expHours, coverage, unavailable, ranked));
		}
		entries.sort(Comparator.comparing((Entry en) -> en.unavailable != null)
			.thenComparing(en -> !en.ranked)
			.thenComparing(Comparator.comparingDouble((Entry en) -> en.value(goal)).reversed()));
		return entries;
	}
}
