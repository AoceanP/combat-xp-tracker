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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Builds the plain-text session summary the panel's "Copy" button puts on the
 * clipboard, formatted to paste cleanly into Discord or a forum post.
 */
final class SessionSummaryText
{
	/** Only the top few monsters are listed so the summary stays short. */
	static final int MAX_MONSTERS = 5;

	private SessionSummaryText()
	{
	}

	/**
	 * @param monsters the monsters to list, already filtered and sorted as in the panel
	 */
	static String build(Map<Skill, SkillProgress> skills, HitStats hits, List<MonsterTracker.Snapshot> monsters, XpRateMode rateMode)
	{
		StringBuilder sb = new StringBuilder("Combat & XP Tracker - session summary\n");

		// XP, biggest gains first
		List<Map.Entry<Skill, SkillProgress>> gained = new ArrayList<>();
		long totalXp = 0;
		for (Map.Entry<Skill, SkillProgress> e : skills.entrySet())
		{
			int xp = e.getValue().getSessionXpGained();
			if (xp > 0)
			{
				gained.add(e);
				totalXp += xp;
			}
		}
		gained.sort((a, b) -> Integer.compare(b.getValue().getSessionXpGained(), a.getValue().getSessionXpGained()));

		sb.append("\nXP gained: +").append(Formatting.withCommas(totalXp)).append('\n');
		for (Map.Entry<Skill, SkillProgress> e : gained)
		{
			sb.append("  ").append(Formatting.capitalize(e.getKey().getName()))
				.append(" +").append(Formatting.withCommas(e.getValue().getSessionXpGained()));
			int rate = e.getValue().getXpPerHour(rateMode);
			if (rate > 0)
			{
				sb.append(" (").append(Formatting.compactXp(rate)).append(" xp/hr)");
			}
			sb.append('\n');
		}

		// Combat
		if (hits.getHitCount() > 0)
		{
			sb.append(String.format(Locale.US, "\nHits: %s | Average: %.1f | Biggest: %d\n",
				Formatting.withCommas(hits.getHitCount()), hits.getAverageDamage(), hits.getMaxHit()));
		}

		// Monsters
		if (!monsters.isEmpty())
		{
			int kills = 0;
			long loot = 0;
			for (MonsterTracker.Snapshot s : monsters)
			{
				kills += s.getKills();
				loot += s.getLootValue();
			}
			sb.append("\nKills: ").append(Formatting.withCommas(kills))
				.append(" | Loot: ").append(Formatting.compactXp(loot)).append(" gp\n");
			for (int i = 0; i < monsters.size() && i < MAX_MONSTERS; i++)
			{
				sb.append("  ").append(describe(monsters.get(i))).append('\n');
			}
			if (monsters.size() > MAX_MONSTERS)
			{
				sb.append("  ...and ").append(monsters.size() - MAX_MONSTERS).append(" more\n");
			}
		}

		// Goals
		List<String> goals = new ArrayList<>();
		for (Map.Entry<Skill, SkillProgress> e : skills.entrySet())
		{
			SkillProgress p = e.getValue();
			Goal goal = p.getGoal();
			if (goal == null || !p.isXpKnown())
			{
				continue;
			}
			String name = Formatting.capitalize(e.getKey().getName());
			if (p.isGoalReached())
			{
				goals.add(name + ": " + goal + " reached");
			}
			else
			{
				goals.add(String.format(Locale.US, "%s: %.1f%% to %s", name,
					Math.floor(p.getProgressToGoal() * 1000) / 10.0, goal));
			}
		}
		if (!goals.isEmpty())
		{
			sb.append("\nGoals:\n");
			for (String g : goals)
			{
				sb.append("  ").append(g).append('\n');
			}
		}

		return sb.toString().trim();
	}

	static String describe(MonsterTracker.Snapshot s)
	{
		StringBuilder sb = new StringBuilder(s.getName()).append(" - ");
		sb.append(s.getKills()).append(s.getKills() == 1 ? " kill" : " kills");
		if (s.getLootValue() > 0)
		{
			sb.append(", ").append(Formatting.compactXp(s.getLootValue())).append(" gp");
		}
		if (s.getBiggestHit() >= 0)
		{
			sb.append(", biggest hit ").append(s.getBiggestHit());
			if (s.getBiggestHitStyle() != null)
			{
				sb.append(" (").append(s.getBiggestHitStyle().getDisplayName()).append(')');
			}
		}
		return sb.toString();
	}
}
