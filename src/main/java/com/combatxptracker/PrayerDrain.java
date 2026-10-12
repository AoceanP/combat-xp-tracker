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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How fast the player's prayer drains while fighting each monster, measured from their own
 * prayer points: every point lost while fighting a monster counts towards it, and points
 * restored (potions, altars) are ignored. It reflects the prayers they really use there.
 *
 * Saved per account as JSON, so field names are the save format. Synchronized: it's
 * measured on the client thread and read by the panel.
 */
final class PrayerDrain
{
	/**
	 * Kills only count towards the rate if prayer drained this recently (30s), so kills
	 * done without praying don't make it look cheaper.
	 */
	static final int RECENT_DRAIN_TICKS = 50;
	/**
	 * Enough fighting to trust the rate: two minutes and a few kills.
	 */
	static final int MIN_TICKS = 200;
	static final int MIN_KILLS = 3;
	private static final double TICK_MINUTES = 0.6 / 60;

	/**
	 * One monster's measured drain.
	 */
	static final class Rate
	{
		int points;
		int ticks;
		int kills;

		/**
		 * @return prayer points per kill, or -1 without enough data
		 */
		double pointsPerKill()
		{
			return isReliable() ? points / (double) kills : -1;
		}

		/**
		 * @return prayer points per minute of fighting, or -1 without enough data
		 */
		double pointsPerMinute()
		{
			return isReliable() ? points / (ticks * TICK_MINUTES) : -1;
		}

		boolean isReliable()
		{
			return ticks >= MIN_TICKS && kills >= MIN_KILLS && points > 0;
		}
	}

	private final Map<String, Rate> rates = new HashMap<>();
	private final Map<String, Integer> lastDrainTick = new HashMap<>();
	private int revision;
	// Changes only on kills, so the panel isn't rebuilt on every tick of drain.
	private int killRevision;

	/**
	 * One game tick of fighting a monster.
	 *
	 * @param drained prayer points lost this tick (restores are not drains: pass 0)
	 * @param praying whether any prayer is on
	 */
	synchronized void onTick(String monster, int drained, boolean praying, int tick)
	{
		if (monster == null || !praying)
		{
			return;
		}
		Rate rate = rates.computeIfAbsent(key(monster), k -> new Rate());
		rate.ticks++;
		if (drained > 0)
		{
			rate.points += drained;
			lastDrainTick.put(key(monster), tick);
			revision++;
		}
	}

	/**
	 * A monster died. It counts if prayer was draining while fighting it.
	 */
	synchronized void onKill(String monster, int tick)
	{
		if (monster == null)
		{
			return;
		}
		Integer last = lastDrainTick.get(key(monster));
		Rate rate = rates.get(key(monster));
		if (rate != null && last != null && tick - last <= RECENT_DRAIN_TICKS && tick >= last)
		{
			rate.kills++;
			revision++;
			killRevision++;
		}
	}

	/**
	 * @return the measured rate for a monster, or null if it isn't reliable yet
	 */
	synchronized Rate rate(String monster)
	{
		Rate rate = monster == null ? null : rates.get(key(monster));
		if (rate == null || !rate.isReliable())
		{
			return null;
		}
		Rate copy = new Rate();
		copy.points = rate.points;
		copy.ticks = rate.ticks;
		copy.kills = rate.kills;
		return copy;
	}

	/**
	 * The measured rate for a slayer task: the matching monster fought the most.
	 */
	synchronized Rate rateForTask(String task)
	{
		String best = null;
		int bestKills = -1;
		for (Map.Entry<String, Rate> e : rates.entrySet())
		{
			if (e.getValue().isReliable() && e.getValue().kills > bestKills && SlayerTaskMatcher.matches(task, e.getKey()))
			{
				best = e.getKey();
				bestKills = e.getValue().kills;
			}
		}
		return best == null ? null : rate(best);
	}

	/**
	 * Prayer points a prayer potion dose restores: 7 plus a quarter of the Prayer level.
	 */
	static int pointsPerDose(int prayerLevel)
	{
		return 7 + Math.max(1, prayerLevel) / 4;
	}

	/**
	 * Prayer potions (4 doses each) for a number of kills, rounded up.
	 */
	static int potionsFor(Rate rate, int kills, int prayerLevel)
	{
		double points = rate.pointsPerKill() * kills;
		int doses = (int) Math.ceil(points / pointsPerDose(prayerLevel));
		return (doses + 3) / 4;
	}

	/**
	 * Minutes of fighting one dose lasts.
	 */
	static double minutesPerDose(Rate rate, int prayerLevel)
	{
		double perMinute = rate.pointsPerMinute();
		return perMinute <= 0 ? -1 : pointsPerDose(prayerLevel) / perMinute;
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized int getKillRevision()
	{
		return killRevision;
	}

	synchronized Map<String, Rate> exportState()
	{
		Map<String, Rate> out = new HashMap<>();
		for (Map.Entry<String, Rate> e : rates.entrySet())
		{
			Rate r = e.getValue();
			if (r.points > 0)
			{
				Rate copy = new Rate();
				copy.points = r.points;
				copy.ticks = r.ticks;
				copy.kills = r.kills;
				out.put(e.getKey(), copy);
			}
		}
		return out;
	}

	synchronized void importState(Map<String, Rate> saved)
	{
		rates.clear();
		lastDrainTick.clear();
		if (saved != null)
		{
			for (Map.Entry<String, Rate> e : saved.entrySet())
			{
				Rate r = e.getValue();
				if (e.getKey() != null && r != null && r.points >= 0 && r.ticks >= 0 && r.kills >= 0)
				{
					rates.put(key(e.getKey()), r);
				}
			}
		}
		revision++;
		killRevision++;
	}

	synchronized void remove(String monster)
	{
		if (monster != null && rates.remove(key(monster)) != null)
		{
			revision++;
		}
	}

	synchronized boolean isEmpty()
	{
		return rates.isEmpty();
	}

	private static String key(String monster)
	{
		return monster.toLowerCase(Locale.ROOT);
	}
}
