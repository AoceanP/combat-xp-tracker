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

import java.util.List;

/**
 * What a slayer task will take, from the player's own past runs of it in the task log: time,
 * Slayer XP, loot and supplies, scaled to the kills asked for. Also whether a cannon paid off
 * on that task, comparing runs with and without one.
 */
final class TaskForecast
{
	/**
	 * The newest runs used. Older ones may be from weaker gear.
	 */
	static final int MAX_RUNS = 5;
	/**
	 * Each side of the cannon comparison needs at least this much fighting (10 minutes).
	 */
	static final long MIN_CANNON_MILLIS = 10 * 60_000L;

	private final int runs;
	private final int kills;
	private final long millis;
	private final long slayerXp;
	private final long loot;
	private final long supplies;

	private TaskForecast(int runs, int kills, long millis, long slayerXp, long loot, long supplies)
	{
		this.runs = runs;
		this.kills = kills;
		this.millis = millis;
		this.slayerXp = slayerXp;
		this.loot = loot;
		this.supplies = supplies;
	}

	/**
	 * @param records the task log, newest first
	 * @param amount  how many kills to forecast
	 * @return the forecast, or null with no past runs of the task
	 */
	static TaskForecast of(List<SlayerTaskRecord> records, String task, int amount, LootPrice price)
	{
		if (task == null || amount <= 0)
		{
			return null;
		}
		int runs = 0;
		int kills = 0;
		long millis = 0;
		long xp = 0;
		long loot = 0;
		long supplies = 0;
		for (SlayerTaskRecord r : records)
		{
			if (runs >= MAX_RUNS)
			{
				break;
			}
			if (!task.equalsIgnoreCase(r.task) || r.kills <= 0 || r.activeMillis <= 0)
			{
				continue;
			}
			runs++;
			kills += r.kills;
			millis += r.activeMillis;
			xp += r.slayerXp;
			loot += r.loot(price);
			supplies += r.supplies;
		}
		if (runs == 0)
		{
			return null;
		}
		double scale = amount / (double) kills;
		return new TaskForecast(runs, amount, Math.round(millis * scale), Math.round(xp * scale), Math.round(loot * scale),
			Math.round(supplies * scale));
	}

	int getRuns()
	{
		return runs;
	}

	long getMillis()
	{
		return millis;
	}

	long getSlayerXp()
	{
		return slayerXp;
	}

	long getLoot()
	{
		return loot;
	}

	long getSupplies()
	{
		return supplies;
	}

	/**
	 * e.g. "Abyssal demons x180: about 52 min, +27K Slayer XP, ~1.4M gp, 210K gp of supplies
	 * (your last 3)."
	 */
	String message(String task)
	{
		StringBuilder sb = new StringBuilder(task).append(" x").append(Formatting.withCommas(kills))
			.append(": about ").append(Formatting.duration(millis / 3_600_000.0))
			.append(", +").append(Formatting.compactXp(slayerXp)).append(" Slayer XP");
		if (loot > 0)
		{
			sb.append(", ~").append(Formatting.compactXp(loot)).append(" gp");
		}
		if (supplies > 0)
		{
			sb.append(", ").append(Formatting.compactXp(supplies)).append(" gp of supplies");
		}
		return sb.append(" (").append(runs == 1 ? "your last run" : "your last " + runs).append(").").toString();
	}

	/**
	 * Slayer XP an hour on a task with a cannon against without one.
	 */
	static final class Cannon
	{
		private final double xpPerHourWith;
		private final double xpPerHourWithout;
		private final double ballCostPerHour;

		Cannon(double xpPerHourWith, double xpPerHourWithout, double ballCostPerHour)
		{
			this.xpPerHourWith = xpPerHourWith;
			this.xpPerHourWithout = xpPerHourWithout;
			this.ballCostPerHour = ballCostPerHour;
		}

		double getXpPerHourWith()
		{
			return xpPerHourWith;
		}

		double getXpPerHourWithout()
		{
			return xpPerHourWithout;
		}

		double getBallCostPerHour()
		{
			return ballCostPerHour;
		}

		/**
		 * The Slayer XP a cannon adds an hour, e.g. "+14K/hr" (or "-2K/hr").
		 */
		String gain()
		{
			double gain = xpPerHourWith - xpPerHourWithout;
			return (gain >= 0 ? "+" : "-") + Formatting.compactXp(Math.round(Math.abs(gain))) + "/hr";
		}

		/**
		 * What the cannonballs cost an hour, e.g. "420K gp/hr".
		 */
		String cost()
		{
			return Formatting.compactXp(Math.round(ballCostPerHour)) + " gp/hr";
		}

		String tooltip()
		{
			return "<html>Slayer XP an hour on this task from your task log:<br>with a cannon "
				+ Formatting.compactXp(Math.round(xpPerHourWith)) + "/hr, without " + Formatting.compactXp(Math.round(xpPerHourWithout))
				+ "/hr.<br>Cannonballs cost about " + Formatting.compactXp(Math.round(ballCostPerHour)) + " gp an hour at today's price.</html>";
		}
	}

	/**
	 * @param ballPrice a cannonball's price now
	 * @return the comparison, or null without enough fighting both with and without a cannon
	 */
	static Cannon cannon(List<SlayerTaskRecord> records, String task, long ballPrice)
	{
		if (task == null)
		{
			return null;
		}
		long withMillis = 0;
		long withXp = 0;
		long balls = 0;
		long withoutMillis = 0;
		long withoutXp = 0;
		for (SlayerTaskRecord r : records)
		{
			if (!task.equalsIgnoreCase(r.task) || r.activeMillis <= 0)
			{
				continue;
			}
			if (r.cannonballs > 0)
			{
				withMillis += r.activeMillis;
				withXp += r.slayerXp;
				balls += r.cannonballs;
			}
			else
			{
				withoutMillis += r.activeMillis;
				withoutXp += r.slayerXp;
			}
		}
		if (withMillis < MIN_CANNON_MILLIS || withoutMillis < MIN_CANNON_MILLIS)
		{
			return null;
		}
		return new Cannon(ActivityTimer.perHour(withXp, withMillis), ActivityTimer.perHour(withoutXp, withoutMillis),
			ActivityTimer.perHour(balls * Math.max(0, ballPrice), withMillis));
	}
}
