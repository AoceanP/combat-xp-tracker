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

import java.util.Locale;

/**
 * Milestones worth a chat message: kill counts of a monster, total kills, slayer tasks and
 * a new biggest hit ever. A count milestone fires when the count reaches it exactly, so it
 * fires once as the count passes it. Client thread only.
 */
final class Milestones
{
	static final int[] MONSTER_KILLS = {100, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000};
	static final int[] TOTAL_KILLS = {1_000, 10_000, 100_000, 1_000_000};
	static final int[] SLAYER_TASKS = {10, 50, 100, 250, 500, 1_000, 2_500};
	/**
	 * A new biggest hit is only news once there's a record worth beating.
	 */
	static final int MIN_RECORD_HITS = 100;

	private int biggestHit = -1;
	private boolean recordTrusted;

	/**
	 * The biggest hit remembered so far, from the all-time monsters.
	 *
	 * @param hits how many hits that record is out of
	 */
	void setRecord(int biggest, long hits)
	{
		biggestHit = biggest;
		recordTrusted = hits >= MIN_RECORD_HITS;
	}

	/**
	 * @return a message if this kill reached a milestone for the monster or in total, or null
	 */
	static String onKill(String monster, int monsterKills, long totalKills)
	{
		if (reached(MONSTER_KILLS, monsterKills))
		{
			return Formatting.withCommas(monsterKills) + " " + monster + " kills!";
		}
		if (totalKills > 0 && totalKills <= Integer.MAX_VALUE && reached(TOTAL_KILLS, (int) totalKills))
		{
			return Formatting.withCommas(totalKills) + " kills in total!";
		}
		return null;
	}

	/**
	 * @return a message if the task log just reached a milestone, or null
	 */
	static String onTask(int tasks)
	{
		return reached(SLAYER_TASKS, tasks) ? ordinal(tasks) + " slayer task logged!" : null;
	}

	/**
	 * @return a message if this hit beat the biggest ever, or null
	 */
	String onHit(int damage, String monster, CombatStyle style)
	{
		if (damage <= biggestHit || damage <= 0)
		{
			return null;
		}
		boolean announce = recordTrusted && biggestHit > 0;
		biggestHit = damage;
		if (!announce)
		{
			return null;
		}
		return "New biggest hit: " + damage + " on " + monster
			+ (style == null ? "" : " (" + style.getDisplayName().toLowerCase(Locale.ROOT) + ")") + "!";
	}

	private static boolean reached(int[] milestones, int count)
	{
		for (int m : milestones)
		{
			if (m == count)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * e.g. "100th", "1,000th", "21st".
	 */
	static String ordinal(int n)
	{
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
			: n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
		return Formatting.withCommas(n) + suffix;
	}
}
