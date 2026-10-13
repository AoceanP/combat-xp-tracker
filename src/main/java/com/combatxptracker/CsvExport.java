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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Spreadsheet (CSV) versions of the monsters, the slayer task log and the goals, for Excel
 * or Google Sheets. Plain numbers (no "k" or "M") so they can be sorted and summed.
 */
final class CsvExport
{
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

	private CsvExport()
	{
	}

	static String monsters(List<MonsterTracker.Snapshot> monsters)
	{
		StringBuilder sb = new StringBuilder("Monster,Kills,Hits,Average hit,Biggest hit,Biggest hit style,Loot (gp),"
			+ "Minutes fighting,Kills per hour,GP per hour,Kills since rare drop\n");
		for (MonsterTracker.Snapshot s : monsters)
		{
			row(sb, s.getName(), s.getKills(), s.getHits(), String.format(Locale.US, "%.2f", s.getAverageHit()),
				s.getBiggestHit() < 0 ? "" : s.getBiggestHit(),
				s.getBiggestHitStyle() == null ? "" : s.getBiggestHitStyle().getDisplayName(),
				s.getLootValue(), s.getActiveMillis() / 60_000, rate(s.getKillsPerHour()), rate(s.getGpPerHour()),
				s.getKillsSinceRare());
		}
		return sb.toString();
	}

	static String tasks(List<SlayerTaskRecord> tasks, LootPrice price, ZoneId zone)
	{
		StringBuilder sb = new StringBuilder("Finished,Task,Master,Area,Assigned,Kills,Minutes fighting,Slayer XP,"
			+ "Combat XP,Loot (gp),Slayer XP per hour,GP per hour,Superiors,Points,Biggest hit\n");
		for (SlayerTaskRecord r : tasks)
		{
			row(sb, r.endMillis > 0 ? DATE.format(Instant.ofEpochMilli(r.endMillis).atZone(zone)) : "",
				r.task, r.master, r.area != null ? r.area : r.location, r.assigned > 0 ? r.assigned : "", r.kills,
				r.activeMillis / 60_000, r.slayerXp, r.combatXp, r.loot(price), rate(r.slayerXpPerHour()),
				rate(r.gpPerHour(price)), r.superiors, r.points >= 0 ? r.points : "", r.biggestHit >= 0 ? r.biggestHit : "");
		}
		return sb.toString();
	}

	static String goals(Map<Skill, SkillProgress> skills, XpRateMode mode)
	{
		StringBuilder sb = new StringBuilder("Skill,Goal,Current XP,XP left,XP per hour,Hours left\n");
		for (Map.Entry<Skill, SkillProgress> e : skills.entrySet())
		{
			SkillProgress p = e.getValue();
			if (!p.isGoalSet())
			{
				continue;
			}
			double hours = p.getEstimatedHoursToGoal(mode);
			row(sb, Formatting.capitalize(e.getKey().getName()), p.getGoal().getShortLabel(), p.getCurrentXp(),
				p.getXpRemainingToGoal(), p.getXpPerHour(mode),
				hours > 0 && !Double.isInfinite(hours) ? String.format(Locale.US, "%.2f", hours) : "");
		}
		return sb.toString();
	}

	private static String rate(double perHour)
	{
		return perHour < 0 ? "" : String.valueOf(Math.round(perHour));
	}

	private static void row(StringBuilder sb, Object... cells)
	{
		for (int i = 0; i < cells.length; i++)
		{
			if (i > 0)
			{
				sb.append(',');
			}
			sb.append(cell(cells[i]));
		}
		sb.append('\n');
	}

	/**
	 * Quotes a cell when it needs it, and stops spreadsheets reading text as a formula.
	 */
	static String cell(Object value)
	{
		if (value == null)
		{
			return "";
		}
		String s = value.toString();
		if (!s.isEmpty() && !(value instanceof Number) && "=+-@".indexOf(s.charAt(0)) >= 0)
		{
			s = "'" + s;
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n"))
		{
			s = "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}
}
