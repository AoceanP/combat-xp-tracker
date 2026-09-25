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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-monster stats: hits, damage, biggest hit per combat style, kills, loot, and how
 * long was spent fighting each monster (for kills/hr and GP/hr).
 *
 * Written on the client thread and read by the Swing panel, so every public method is
 * synchronized and reads return immutable snapshots rather than live objects.
 */
public class MonsterTracker
{
	/**
	 * One item stack from a drop, already resolved to a name and price on the client
	 * thread (item compositions can only be read there).
	 */
	public static final class Drop
	{
		private final int itemId;
		private final String name;
		private final int quantity;
		private final long unitPrice;

		public Drop(int itemId, String name, int quantity, long unitPrice)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}
	}

	/**
	 * An aggregated loot line for one item across every kill.
	 */
	public static final class LootLine
	{
		private final int itemId;
		private final String name;
		private final long quantity;
		private final long unitPrice;

		LootLine(int itemId, String name, long quantity, long unitPrice)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}

		public int getItemId()
		{
			return itemId;
		}

		public String getName()
		{
			return name;
		}

		public long getQuantity()
		{
			return quantity;
		}

		public long getUnitPrice()
		{
			return unitPrice;
		}

		public long getTotalValue()
		{
			return unitPrice * quantity;
		}
	}

	/**
	 * Read-only view of one monster, safe to hand to the Swing thread.
	 */
	public static final class Snapshot
	{
		private final String name;
		private final int kills;
		private final int hits;
		private final long totalDamage;
		private final int biggestHit;
		private final CombatStyle biggestHitStyle;
		private final Map<CombatStyle, Integer> biggestByStyle;
		private final List<LootLine> loot;
		private final long lootValue;
		private final long activeMillis;
		private final long lastActivityMillis;

		Snapshot(Entry e)
		{
			name = e.name;
			kills = e.kills;
			hits = e.hits;
			totalDamage = e.totalDamage;
			biggestHit = e.biggestHit;
			biggestHitStyle = e.biggestHitStyle;
			biggestByStyle = Collections.unmodifiableMap(new EnumMap<>(e.biggestByStyle));
			List<LootLine> lines = new ArrayList<>(e.loot.values());
			// Most valuable first, like the core Loot Tracker.
			lines.sort((a, b) -> Long.compare(b.getTotalValue(), a.getTotalValue()));
			loot = Collections.unmodifiableList(lines);
			long value = 0;
			for (LootLine line : lines)
			{
				value += line.getTotalValue();
			}
			lootValue = value;
			activeMillis = e.activity.getActiveMillis();
			lastActivityMillis = e.activity.getLastMillis();
		}

		public String getName()
		{
			return name;
		}

		public int getKills()
		{
			return kills;
		}

		public int getHits()
		{
			return hits;
		}

		public double getAverageHit()
		{
			return hits == 0 ? 0.0 : totalDamage / (double) hits;
		}

		public int getBiggestHit()
		{
			return biggestHit;
		}

		/**
		 * @return the style the biggest hit was dealt with, or null if unknown
		 */
		public CombatStyle getBiggestHitStyle()
		{
			return biggestHitStyle;
		}

		public Map<CombatStyle, Integer> getBiggestByStyle()
		{
			return biggestByStyle;
		}

		public List<LootLine> getLoot()
		{
			return loot;
		}

		public long getLootValue()
		{
			return lootValue;
		}

		/**
		 * Time spent fighting this monster, not counting breaks.
		 */
		public long getActiveMillis()
		{
			return activeMillis;
		}

		public long getLastActivityMillis()
		{
			return lastActivityMillis;
		}

		/**
		 * @return kills per hour of fighting, or -1 until there's a minute of activity
		 */
		public double getKillsPerHour()
		{
			return ActivityTimer.perHour(kills, activeMillis);
		}

		/**
		 * @return loot value per hour of fighting, or -1 until there's a minute of activity
		 */
		public double getGpPerHour()
		{
			return ActivityTimer.perHour(lootValue, activeMillis);
		}
	}

	/**
	 * Plain data for saving one monster to the RuneLite profile with Gson.
	 */
	static final class Saved
	{
		String name;
		int kills;
		int hits;
		long totalDamage;
		int biggestHit = -1;
		CombatStyle biggestHitStyle;
		Map<CombatStyle, Integer> biggestByStyle;
		List<SavedLoot> loot;
		long activeMillis;
		long lastActivityMillis = -1;
	}

	static final class SavedLoot
	{
		int id;
		String name;
		long quantity;
		long unitPrice;
	}

	private static final class Entry
	{
		final String name;
		int kills;
		int hits;
		long totalDamage;
		int biggestHit = -1;
		CombatStyle biggestHitStyle;
		final Map<CombatStyle, Integer> biggestByStyle = new EnumMap<>(CombatStyle.class);
		final Map<Integer, LootLine> loot = new LinkedHashMap<>();
		final ActivityTimer activity = new ActivityTimer();

		Entry(String name)
		{
			this.name = name;
		}
	}

	// Keyed by NPC name, so every "Abyssal demon" shares one entry.
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private int revision;

	/**
	 * @param style the style the hit came from, or null if it couldn't be determined
	 */
	public synchronized void recordHit(String monsterName, int damage, CombatStyle style, long nowMillis)
	{
		if (isBlank(monsterName))
		{
			return;
		}
		Entry e = entries.computeIfAbsent(monsterName, Entry::new);
		e.hits++;
		e.totalDamage += damage;
		if (damage > e.biggestHit)
		{
			e.biggestHit = damage;
			e.biggestHitStyle = style;
		}
		if (style != null)
		{
			e.biggestByStyle.merge(style, damage, Math::max);
		}
		e.activity.mark(nowMillis);
		revision++;
	}

	public synchronized void recordKill(String monsterName, long nowMillis)
	{
		if (isBlank(monsterName))
		{
			return;
		}
		Entry e = entries.computeIfAbsent(monsterName, Entry::new);
		e.kills++;
		e.activity.mark(nowMillis);
		revision++;
	}

	public synchronized void recordLoot(String monsterName, List<Drop> drops, long nowMillis)
	{
		if (isBlank(monsterName) || drops.isEmpty())
		{
			return;
		}
		Entry e = entries.computeIfAbsent(monsterName, Entry::new);
		for (Drop d : drops)
		{
			LootLine existing = e.loot.get(d.itemId);
			long qty = d.quantity + (existing == null ? 0 : existing.quantity);
			// Keep the latest price so the total tracks the current market.
			e.loot.put(d.itemId, new LootLine(d.itemId, d.name, qty, d.unitPrice));
		}
		e.activity.mark(nowMillis);
		revision++;
	}

	/**
	 * @return every monster, most recently fought first
	 */
	public synchronized List<Snapshot> snapshot()
	{
		List<Snapshot> out = new ArrayList<>(entries.size());
		for (Entry e : entries.values())
		{
			out.add(new Snapshot(e));
		}
		out.sort(MonsterSort.RECENT.comparator());
		return out;
	}

	/**
	 * Increases on every change, so the panel can skip rebuilding and the plugin can
	 * skip saving when nothing changed.
	 */
	public synchronized int getRevision()
	{
		return revision;
	}

	public synchronized void remove(String monsterName)
	{
		if (entries.remove(monsterName) != null)
		{
			revision++;
		}
	}

	public synchronized void reset()
	{
		entries.clear();
		revision++;
	}

	public synchronized boolean isEmpty()
	{
		return entries.isEmpty();
	}

	// ---- Saving and loading -----------------------------------------------------

	public synchronized List<Saved> exportState()
	{
		List<Saved> out = new ArrayList<>(entries.size());
		for (Entry e : entries.values())
		{
			Saved s = new Saved();
			s.name = e.name;
			s.kills = e.kills;
			s.hits = e.hits;
			s.totalDamage = e.totalDamage;
			s.biggestHit = e.biggestHit;
			s.biggestHitStyle = e.biggestHitStyle;
			s.biggestByStyle = new EnumMap<>(e.biggestByStyle);
			s.loot = new ArrayList<>();
			for (LootLine line : e.loot.values())
			{
				SavedLoot l = new SavedLoot();
				l.id = line.itemId;
				l.name = line.name;
				l.quantity = line.quantity;
				l.unitPrice = line.unitPrice;
				s.loot.add(l);
			}
			s.activeMillis = e.activity.getActiveMillis();
			s.lastActivityMillis = e.activity.getLastMillis();
			out.add(s);
		}
		return out;
	}

	/**
	 * Replaces everything with saved data. Bad entries are skipped rather than failing
	 * the whole load, so one corrupt monster can't wipe the rest.
	 */
	public synchronized void importState(Collection<Saved> saved)
	{
		entries.clear();
		if (saved != null)
		{
			for (Saved s : saved)
			{
				if (s == null || isBlank(s.name))
				{
					continue;
				}
				Entry e = new Entry(s.name);
				e.kills = Math.max(0, s.kills);
				e.hits = Math.max(0, s.hits);
				e.totalDamage = Math.max(0, s.totalDamage);
				e.biggestHit = s.biggestHit;
				e.biggestHitStyle = s.biggestHitStyle;
				if (s.biggestByStyle != null)
				{
					for (Map.Entry<CombatStyle, Integer> b : s.biggestByStyle.entrySet())
					{
						if (b.getKey() != null && b.getValue() != null)
						{
							e.biggestByStyle.put(b.getKey(), b.getValue());
						}
					}
				}
				if (s.loot != null)
				{
					for (SavedLoot l : s.loot)
					{
						if (l != null && l.quantity > 0)
						{
							e.loot.put(l.id, new LootLine(l.id, l.name == null ? "Unknown" : l.name, l.quantity, l.unitPrice));
						}
					}
				}
				e.activity.restore(s.activeMillis, s.lastActivityMillis);
				entries.put(e.name, e);
			}
		}
		revision++;
	}

	// ---- View helpers -----------------------------------------------------------

	/**
	 * Drops hidden monsters and sorts the rest for the Monsters tab.
	 */
	public static List<Snapshot> view(List<Snapshot> all, MonsterSort sort, Set<String> hiddenLowercase)
	{
		List<Snapshot> out = new ArrayList<>(all.size());
		for (Snapshot s : all)
		{
			if (!hiddenLowercase.contains(s.getName().toLowerCase(Locale.ROOT)))
			{
				out.add(s);
			}
		}
		out.sort((sort == null ? MonsterSort.RECENT : sort).comparator());
		return out;
	}

	/**
	 * Parses the "hidden monsters" setting: comma separated, case insensitive.
	 */
	public static Set<String> parseNames(String csv)
	{
		Set<String> names = new LinkedHashSet<>();
		if (csv == null)
		{
			return names;
		}
		for (String part : csv.split(","))
		{
			String name = part.trim().toLowerCase(Locale.ROOT);
			if (!name.isEmpty())
			{
				names.add(name);
			}
		}
		return names;
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.trim().isEmpty();
	}
}
