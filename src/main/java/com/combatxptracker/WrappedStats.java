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

/**
 * "Gielinor Wrapped": the account's lifetime stats, from everything the plugin has
 * remembered (the all-time monsters and the slayer task log). Immutable.
 */
final class WrappedStats
{
	/**
	 * Monsters with fewer kills than this don't count for the longest dry streak.
	 */
	static final int MIN_DRY_KILLS = 50;

	/**
	 * One line of the summary.
	 */
	static final class Line
	{
		private final String label;
		private final String value;
		private final String detail;

		Line(String label, String value, String detail)
		{
			this.label = label;
			this.value = value;
			this.detail = detail;
		}

		String getLabel()
		{
			return label;
		}

		String getValue()
		{
			return value;
		}

		/**
		 * @return a short note under the value, or null
		 */
		String getDetail()
		{
			return detail;
		}
	}

	/**
	 * A heading and its lines.
	 */
	static final class Section
	{
		private final String title;
		private final List<Line> lines = new ArrayList<>();

		Section(String title)
		{
			this.title = title;
		}

		String getTitle()
		{
			return title;
		}

		List<Line> getLines()
		{
			return lines;
		}
	}

	private final List<Section> sections = new ArrayList<>();
	private final boolean empty;

	private WrappedStats(boolean empty)
	{
		this.empty = empty;
	}

	static WrappedStats of(List<MonsterTracker.Snapshot> monsters, List<SlayerTaskRecord> tasks, LootPrice price)
	{
		long kills = 0;
		long hits = 0;
		long damage = 0;
		long loot = 0;
		long millis = 0;
		MonsterTracker.Snapshot mostKilled = null;
		MonsterTracker.Snapshot richest = null;
		MonsterTracker.Snapshot biggest = null;
		MonsterTracker.Snapshot dry = null;
		for (MonsterTracker.Snapshot s : monsters)
		{
			kills += s.getKills();
			hits += s.getHits();
			damage += Math.round(s.getAverageHit() * s.getHits());
			loot += s.getLootValue();
			millis += s.getActiveMillis();
			if (s.getKills() > 0 && (mostKilled == null || s.getKills() > mostKilled.getKills()))
			{
				mostKilled = s;
			}
			if (s.getLootValue() > 0 && (richest == null || s.getLootValue() > richest.getLootValue()))
			{
				richest = s;
			}
			if (s.getBiggestHit() > 0 && (biggest == null || s.getBiggestHit() > biggest.getBiggestHit()))
			{
				biggest = s;
			}
			if (s.getKills() >= MIN_DRY_KILLS && (dry == null || s.getKillsSinceRare() > dry.getKillsSinceRare()))
			{
				dry = s;
			}
		}

		WrappedStats w = new WrappedStats(monsters.isEmpty() && tasks.isEmpty());
		Section combat = new Section("Combat");
		combat.lines.add(new Line("Kills", Formatting.withCommas(kills), monsters.size() + (monsters.size() == 1 ? " monster" : " different monsters")));
		combat.lines.add(new Line("Hits landed", Formatting.withCommas(hits), null));
		combat.lines.add(new Line("Damage dealt", Formatting.withCommas(damage), null));
		if (millis > 0)
		{
			combat.lines.add(new Line("Time fighting", Formatting.duration(millis / 3_600_000.0), null));
		}
		w.sections.add(combat);

		Section records = new Section("Records");
		if (biggest != null)
		{
			CombatStyle style = biggest.getBiggestHitStyle();
			records.lines.add(new Line("Biggest hit", String.valueOf(biggest.getBiggestHit()),
				"on " + biggest.getName() + (style == null ? "" : ", " + style.getDisplayName().toLowerCase(Locale.ROOT))));
		}
		if (mostKilled != null)
		{
			records.lines.add(new Line("Most killed", mostKilled.getName(), Formatting.withCommas(mostKilled.getKills()) + " kills"));
		}
		if (richest != null)
		{
			records.lines.add(new Line("Most loot", richest.getName(), Formatting.compactXp(richest.getLootValue()) + " gp"));
		}
		if (dry != null && dry.getKillsSinceRare() > 0)
		{
			records.lines.add(new Line("Longest dry streak", Formatting.withCommas(dry.getKillsSinceRare()) + " kills",
				"on " + dry.getName()));
		}
		if (!records.lines.isEmpty())
		{
			w.sections.add(records);
		}

		Section loots = new Section("Loot");
		loots.lines.add(new Line("Total loot", Formatting.compactXp(loot) + " gp",
			price == LootPrice.HIGH_ALCHEMY ? "High Alchemy value" : "Grand Exchange value"));
		if (millis > 0 && loot > 0)
		{
			loots.lines.add(new Line("GP per hour", Formatting.compactXp(Math.round(ActivityTimer.perHour(loot, millis))), null));
		}
		w.sections.add(loots);

		if (!tasks.isEmpty())
		{
			long slayerXp = 0;
			int superiors = 0;
			int points = 0;
			for (SlayerTaskRecord r : tasks)
			{
				slayerXp += r.slayerXp;
				superiors += r.superiors;
				points += Math.max(0, r.points);
			}
			Section slayer = new Section("Slayer");
			slayer.lines.add(new Line("Tasks done", Formatting.withCommas(tasks.size()), null));
			slayer.lines.add(new Line("Slayer XP", Formatting.compactXp(slayerXp), null));
			if (points > 0)
			{
				slayer.lines.add(new Line("Points earned", Formatting.withCommas(points), null));
			}
			slayer.lines.add(new Line("Superiors", Formatting.withCommas(superiors), null));
			List<TaskStats> stats = TaskStats.group(tasks, price);
			List<TaskStats> byXp = TaskStats.ranked(stats, false);
			if (!byXp.isEmpty())
			{
				slayer.lines.add(new Line("Fastest task", byXp.get(0).getTask(),
					Formatting.compactXp(Math.round(byXp.get(0).getXpPerHour())) + " XP/hr"));
			}
			List<TaskStats> byGp = TaskStats.ranked(stats, true);
			if (!byGp.isEmpty() && byGp.get(0).getGpPerHour() > 0)
			{
				slayer.lines.add(new Line("Richest task", byGp.get(0).getTask(),
					Formatting.compactXp(Math.round(byGp.get(0).getGpPerHour())) + " GP/hr"));
			}
			w.sections.add(slayer);
		}
		return w;
	}

	List<Section> getSections()
	{
		return sections;
	}

	/**
	 * @return whether there's nothing to show yet
	 */
	boolean isEmpty()
	{
		return empty;
	}

	/**
	 * As plain text, for pasting.
	 */
	String text(String accountName)
	{
		StringBuilder sb = new StringBuilder("Gielinor Wrapped");
		if (accountName != null && !accountName.isEmpty())
		{
			sb.append(" - ").append(accountName);
		}
		sb.append('\n');
		for (Section section : sections)
		{
			sb.append('\n').append(section.title).append('\n');
			for (Line line : section.lines)
			{
				sb.append("  ").append(line.label).append(": ").append(line.value);
				if (line.detail != null)
				{
					sb.append(" (").append(line.detail).append(')');
				}
				sb.append('\n');
			}
		}
		return sb.append("\nTracked by Combat & XP Tracker").toString();
	}
}
