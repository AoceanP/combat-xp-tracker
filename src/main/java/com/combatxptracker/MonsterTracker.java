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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

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
	 * Items worth at least this much each are remembered with the kill they dropped on,
	 * so the dry streak works for any "rare drop value" the player picks above it.
	 */
	static final long RARE_RECORD_FLOOR = 10_000;
	/** How many valuable drops are remembered per monster. */
	static final int MAX_RARE_RECORDS = 100;

	/**
	 * A valuable item and the kill number it dropped on.
	 */
	static final class RareDrop
	{
		int kill;
		int itemId;
		long unitPrice;
		long haPrice;

		RareDrop()
		{
		}

		RareDrop(int kill, int itemId, long unitPrice, long haPrice)
		{
			this.kill = kill;
			this.itemId = itemId;
			this.unitPrice = unitPrice;
			this.haPrice = haPrice;
		}

		long value(LootPrice mode)
		{
			return mode == LootPrice.HIGH_ALCHEMY ? haPrice : unitPrice;
		}
	}

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
		private final long haPrice;

		public Drop(int itemId, String name, int quantity, long unitPrice)
		{
			this(itemId, name, quantity, unitPrice, 0);
		}

		/**
		 * @param unitPrice Grand Exchange price of one item
		 * @param haPrice   High Alchemy value of one item
		 */
		public Drop(int itemId, String name, int quantity, long unitPrice, long haPrice)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.haPrice = haPrice;
		}

		public long totalValue(LootPrice mode)
		{
			return (mode == LootPrice.HIGH_ALCHEMY ? haPrice : unitPrice) * quantity;
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
		private final long haPrice;
		private final LootPrice priceMode;
		private final boolean rare;

		LootLine(int itemId, String name, long quantity, long unitPrice, long haPrice)
		{
			this(itemId, name, quantity, unitPrice, haPrice, LootPrice.GRAND_EXCHANGE, false);
		}

		private LootLine(int itemId, String name, long quantity, long unitPrice, long haPrice, LootPrice priceMode, boolean rare)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.haPrice = haPrice;
			this.priceMode = priceMode;
			this.rare = rare;
		}

		LootLine priced(LootPrice mode, long rareValue)
		{
			long each = mode == LootPrice.HIGH_ALCHEMY ? haPrice : unitPrice;
			return new LootLine(itemId, name, quantity, unitPrice, haPrice, mode, each >= rareValue);
		}

		/**
		 * Whether one of this item is worth at least the "rare drop value" setting.
		 */
		public boolean isRare()
		{
			return rare;
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

		/**
		 * Value of one item in the price mode this line was read with.
		 */
		public long getUnitPrice()
		{
			return priceMode == LootPrice.HIGH_ALCHEMY ? haPrice : unitPrice;
		}

		public long getTotalValue()
		{
			return getUnitPrice() * quantity;
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
		private final int killsSinceRare;
		private final boolean everHadRare;

		Snapshot(Entry e)
		{
			this(e, LootPrice.GRAND_EXCHANGE, name -> false, Long.MAX_VALUE);
		}

		Snapshot(Entry e, LootPrice price, Predicate<String> ignoredItem, long rareValue)
		{
			name = e.name;
			kills = e.kills;
			hits = e.hits;
			totalDamage = e.totalDamage;
			biggestHit = e.biggestHit;
			biggestHitStyle = e.biggestHitStyle;
			biggestByStyle = Collections.unmodifiableMap(new EnumMap<>(e.biggestByStyle));
			List<LootLine> lines = new ArrayList<>();
			for (LootLine line : e.loot.values())
			{
				if (!ignoredItem.test(line.getName()))
				{
					lines.add(line.priced(price, rareValue));
				}
			}
			// Rare drops first, then most valuable, like the core Loot Tracker.
			lines.sort((a, b) -> a.rare != b.rare
				? (a.rare ? -1 : 1)
				: Long.compare(b.getTotalValue(), a.getTotalValue()));
			loot = Collections.unmodifiableList(lines);
			long value = 0;
			for (LootLine line : lines)
			{
				value += line.getTotalValue();
			}
			lootValue = value;
			activeMillis = e.activity.getActiveMillis();
			lastActivityMillis = e.activity.getLastMillis();

			int lastRareKill = -1;
			for (RareDrop r : e.rareDrops)
			{
				if (r.value(price) >= rareValue && !ignoredItem.test(nameOf(e, r.itemId)))
				{
					lastRareKill = Math.max(lastRareKill, r.kill);
				}
			}
			everHadRare = lastRareKill >= 0;
			killsSinceRare = everHadRare ? e.kills - lastRareKill : e.kills;
		}

		private static String nameOf(Entry e, int itemId)
		{
			LootLine line = e.loot.get(itemId);
			return line == null ? null : line.getName();
		}

		/**
		 * Kills since the last drop worth at least the rare drop value, or all kills if
		 * there hasn't been one.
		 */
		public int getKillsSinceRare()
		{
			return killsSinceRare;
		}

		public boolean hasEverDroppedRare()
		{
			return everHadRare;
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
		List<RareDrop> rareDrops;
		long activeMillis;
		long lastActivityMillis = -1;
	}

	static final class SavedLoot
	{
		int id;
		String name;
		long quantity;
		long unitPrice;
		long haPrice;
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
		final List<RareDrop> rareDrops = new ArrayList<>();
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
			e.loot.put(d.itemId, new LootLine(d.itemId, d.name, qty, d.unitPrice, d.haPrice));
			if (d.unitPrice >= RARE_RECORD_FLOOR || d.haPrice >= RARE_RECORD_FLOOR)
			{
				e.rareDrops.add(new RareDrop(e.kills, d.itemId, d.unitPrice, d.haPrice));
				if (e.rareDrops.size() > MAX_RARE_RECORDS)
				{
					e.rareDrops.remove(0);
				}
			}
		}
		e.activity.mark(nowMillis);
		revision++;
	}

	/**
	 * @return every monster, most recently fought first
	 */
	public synchronized List<Snapshot> snapshot()
	{
		return snapshot(LootPrice.GRAND_EXCHANGE, name -> false);
	}

	/**
	 * @param price       how to value loot
	 * @param ignoredItem item names to leave out of the loot and its value
	 */
	public synchronized List<Snapshot> snapshot(LootPrice price, Predicate<String> ignoredItem)
	{
		return snapshot(price, ignoredItem, Long.MAX_VALUE);
	}

	/**
	 * @param rareValue items worth at least this much each count as rare drops
	 */
	public synchronized List<Snapshot> snapshot(LootPrice price, Predicate<String> ignoredItem, long rareValue)
	{
		List<Snapshot> out = new ArrayList<>(entries.size());
		for (Entry e : entries.values())
		{
			out.add(new Snapshot(e, price, ignoredItem, rareValue));
		}
		out.sort(MonsterSort.RECENT.comparator());
		return out;
	}

	/**
	 * Fills in High Alchemy values missing from data saved by 1.5.x, which only stored
	 * GE prices.
	 */
	public synchronized void fillMissingHaPrices(ToLongFunction<Integer> haPriceOfItem)
	{
		for (Entry e : entries.values())
		{
			for (Map.Entry<Integer, LootLine> l : e.loot.entrySet())
			{
				LootLine line = l.getValue();
				if (line.haPrice <= 0)
				{
					long ha = haPriceOfItem.applyAsLong(line.itemId);
					if (ha > 0)
					{
						l.setValue(new LootLine(line.itemId, line.name, line.quantity, line.unitPrice, ha));
					}
				}
			}
		}
	}

	/**
	 * Renames monsters, merging entries that end up with the same name. Used to fold
	 * cards saved separately by 1.5.x (e.g. each Royal Titan) into one boss card.
	 */
	public synchronized void rename(UnaryOperator<String> newName)
	{
		Map<String, Entry> merged = new LinkedHashMap<>();
		for (Entry e : entries.values())
		{
			String name = newName.apply(e.name);
			Entry target = merged.get(name);
			if (target == null)
			{
				target = new Entry(name);
				merged.put(name, target);
			}
			mergeInto(target, e);
		}
		if (!merged.keySet().equals(entries.keySet()))
		{
			entries.clear();
			entries.putAll(merged);
			revision++;
		}
	}

	private static void mergeInto(Entry target, Entry source)
	{
		for (RareDrop r : source.rareDrops)
		{
			target.rareDrops.add(new RareDrop(r.kill + target.kills, r.itemId, r.unitPrice, r.haPrice));
		}
		target.kills += source.kills;
		target.hits += source.hits;
		target.totalDamage += source.totalDamage;
		if (source.biggestHit > target.biggestHit)
		{
			target.biggestHit = source.biggestHit;
			target.biggestHitStyle = source.biggestHitStyle;
		}
		for (Map.Entry<CombatStyle, Integer> b : source.biggestByStyle.entrySet())
		{
			target.biggestByStyle.merge(b.getKey(), b.getValue(), Math::max);
		}
		for (LootLine line : source.loot.values())
		{
			LootLine existing = target.loot.get(line.itemId);
			long qty = line.quantity + (existing == null ? 0 : existing.quantity);
			target.loot.put(line.itemId, new LootLine(line.itemId, line.name, qty, line.unitPrice, line.haPrice));
		}
		target.activity.restore(
			target.activity.getActiveMillis() + source.activity.getActiveMillis(),
			Math.max(target.activity.getLastMillis(), source.activity.getLastMillis()));
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
				l.haPrice = line.haPrice;
				s.loot.add(l);
			}
			s.rareDrops = new ArrayList<>(e.rareDrops);
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
							e.loot.put(l.id, new LootLine(l.id, l.name == null ? "Unknown" : l.name, l.quantity, l.unitPrice, l.haPrice));
						}
					}
				}
				if (s.rareDrops != null)
				{
					for (RareDrop r : s.rareDrops)
					{
						if (r != null)
						{
							e.rareDrops.add(r);
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
		return view(all, sort, hiddenLowercase, Collections.emptySet());
	}

	/**
	 * Like {@link #view(List, MonsterSort, Set)}, with pinned monsters first (in the
	 * chosen order among themselves).
	 */
	public static List<Snapshot> view(List<Snapshot> all, MonsterSort sort, Set<String> hiddenLowercase, Set<String> pinnedLowercase)
	{
		List<Snapshot> out = new ArrayList<>(all.size());
		for (Snapshot s : all)
		{
			if (!hiddenLowercase.contains(s.getName().toLowerCase(Locale.ROOT)))
			{
				out.add(s);
			}
		}
		Comparator<Snapshot> order = (sort == null ? MonsterSort.RECENT : sort).comparator();
		Comparator<Snapshot> pinnedFirst = Comparator.comparing(
			(Snapshot s) -> !pinnedLowercase.contains(s.getName().toLowerCase(Locale.ROOT)));
		out.sort(pinnedFirst.thenComparing(order));
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

	/**
	 * Parses the "ignored items" setting: comma separated item names, case insensitive,
	 * with * as a wildcard (e.g. "bones, *ashes").
	 */
	public static Predicate<String> itemMatcher(String csv)
	{
		List<Pattern> patterns = new ArrayList<>();
		for (String name : parseNames(csv))
		{
			patterns.add(Pattern.compile(wildcardRegex(name)));
		}
		return itemName ->
		{
			if (itemName == null || patterns.isEmpty())
			{
				return false;
			}
			String lower = itemName.toLowerCase(Locale.ROOT);
			for (Pattern pattern : patterns)
			{
				if (pattern.matcher(lower).matches())
				{
					return true;
				}
			}
			return false;
		};
	}

	/**
	 * "*ashes" -> ".*\Qashes\E": everything literal except the * wildcards.
	 */
	static String wildcardRegex(String wildcard)
	{
		StringBuilder regex = new StringBuilder();
		String[] parts = wildcard.split("\\*", -1);
		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				regex.append(".*");
			}
			if (!parts[i].isEmpty())
			{
				regex.append(Pattern.quote(parts[i]));
			}
		}
		return regex.toString();
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.trim().isEmpty();
	}
}
