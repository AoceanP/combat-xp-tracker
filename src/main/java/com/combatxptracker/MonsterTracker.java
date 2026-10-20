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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
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

		public int getItemId()
		{
			return itemId;
		}

		public String getName()
		{
			return name;
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
		private final int rolls;
		private final int landed;
		private final int biggestHit;
		private final CombatStyle biggestHitStyle;
		private final Map<CombatStyle, Integer> biggestByStyle;
		private final List<LootLine> loot;
		private final long lootValue;
		private final long activeMillis;
		private final long lastActivityMillis;
		private final int killsSinceRare;
		private final boolean everHadRare;
		private final boolean rareSinceRestore;
		private final long tripMillis;
		private final long fightMillis;
		private final boolean away;
		private final boolean liveTrip;
		private final int killsSinceUnique;
		private final int timedKills;
		private final long timedLootValue;
		private final boolean uniqueSinceRestore;

		Snapshot(Entry e)
		{
			this(e, LootPrice.GRAND_EXCHANGE, name -> false, Long.MAX_VALUE, false, 0);
		}

		Snapshot(Entry e, LootPrice price, Predicate<String> ignoredItem, long rareValue, boolean liveTrip, long nowMillis)
		{
			// When the last unique's kill isn't known, count from where tracking started (0 for a
			// monster tracked from its first kill) rather than claiming the whole kill count.
			uniqueSinceRestore = e.lastUniqueKill < 0 && e.restoredAt > 0;
			killsSinceUnique = e.lastUniqueKill >= 0 ? e.kills - e.lastUniqueKill : e.kills - e.restoredAt;
			tripMillis = e.trip.getTripMillis(nowMillis, liveTrip);
			fightMillis = e.trip.getFightMillis();
			away = e.trip.isAway(nowMillis, liveTrip);
			this.liveTrip = liveTrip && !away;
			name = e.name;
			kills = e.kills;
			hits = e.hits;
			totalDamage = e.totalDamage;
			rolls = e.rolls;
			landed = e.landed;
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
			long untimed = 0;
			for (LootLine line : lines)
			{
				value += line.getTotalValue();
				Long n = e.untimedLoot.get(line.getItemId());
				if (n != null)
				{
					untimed += Math.min(n, line.getQuantity()) * line.getUnitPrice();
				}
			}
			lootValue = value;
			timedLootValue = Math.max(0, value - untimed);
			timedKills = Math.max(0, e.kills - e.untimedKills);
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
			// When the last rare's kill isn't known (restored, or saved before 2.1.2), the streak
			// counts from then instead of claiming the whole kill count.
			rareSinceRestore = lastRareKill < 0 && e.restoredAt > 0;
			everHadRare = lastRareKill >= 0;
			killsSinceRare = everHadRare ? e.kills - lastRareKill : e.kills - e.restoredAt;
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

		/**
		 * @return whether the dry streak counts from a Loot Tracker restore
		 */
		public boolean isRareSinceRestore()
		{
			return rareSinceRestore;
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

		/**
		 * @return the share of your hits that did damage, from 0 to 100, or -1 with fewer than
		 * {@link #MIN_ROLLS} hits. A 0 counts as a miss, though the game also shows a 0 for a hit
		 * that rolled no damage.
		 */
		public int getAccuracy()
		{
			return rolls < MIN_ROLLS ? -1 : (int) Math.round(landed * 100.0 / rolls);
		}

		/**
		 * @return every hit including 0s, whatever the "Include 0-damage hits" setting
		 */
		public int getRolls()
		{
			return rolls;
		}

		public long getTotalDamage()
		{
			return totalDamage;
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
			return ActivityTimer.perHour(timedKills, activeMillis);
		}

		/**
		 * @return loot value per hour of fighting, or -1 until there's a minute of activity
		 */
		public double getGpPerHour()
		{
			return ActivityTimer.perHour(timedLootValue, activeMillis);
		}

		/**
		 * @return loot per hour of the whole trip, banking and walking included, or -1 until
		 * there's a minute of it
		 */
		public double getTripGpPerHour()
		{
			return ActivityTimer.perHour(timedLootValue, tripMillis);
		}

		/**
		 * @return loot per hour of fighting only, or -1 until there's a minute of it
		 */
		public double getFightGpPerHour()
		{
			return ActivityTimer.perHour(timedLootValue, fightMillis);
		}

		/**
		 * @return whether this monster's trip is going and the player is away from it, so its
		 * trip rate is falling
		 */
		public boolean isAway()
		{
			return away;
		}

		/**
		 * @return kills since the last collection log unique, or -1 if none has been seen
		 */
		public int getKillsSinceUnique()
		{
			return killsSinceUnique;
		}

		/**
		 * @return whether {@link #getKillsSinceUnique} counts from a Loot Tracker restore
		 */
		public boolean isUniqueSinceRestore()
		{
			return uniqueSinceRestore;
		}

		/**
		 * @return whether the player is fighting this monster right now
		 */
		public boolean isLiveTrip()
		{
			return liveTrip;
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
		int rolls;
		int landed;
		int biggestHit = -1;
		CombatStyle biggestHitStyle;
		Map<CombatStyle, Integer> biggestByStyle;
		List<SavedLoot> loot;
		List<RareDrop> rareDrops;
		long activeMillis;
		long lastActivityMillis = -1;
		// Kills when restored from the Loot Tracker, which doesn't say which kill a drop came on.
		int restoredAt;
		// Gold per hour time: the whole trip, and fighting only. -1 in data saved before 2.1.2.
		long tripMillis = -1;
		long fightMillis = -1;
		int lastUniqueKill = -1;
		// -1 in data saved before 2.1.2.
		int untimedKills = -1;
		Map<Integer, Long> untimedLoot;
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
		int rolls;
		int landed;
		int biggestHit = -1;
		CombatStyle biggestHitStyle;
		final Map<CombatStyle, Integer> biggestByStyle = new EnumMap<>(CombatStyle.class);
		final Map<Integer, LootLine> loot = new LinkedHashMap<>();
		final List<RareDrop> rareDrops = new ArrayList<>();
		final ActivityTimer activity = new ActivityTimer();
		final TripTimer trip = new TripTimer();
		int restoredAt;
		// The kill a collection log unique last dropped on, or -1 if none has been seen.
		int lastUniqueKill = -1;
		// Kills and items that weren't timed (restored from the Loot Tracker): left out of per-hour rates.
		int untimedKills;
		final Map<Integer, Long> untimedLoot = new HashMap<>();

		Entry(String name)
		{
			this.name = name;
		}

		/**
		 * Everything so far counts as untimed, so per-hour rates only use what comes next.
		 */
		void startRatesAgain()
		{
			untimedKills = kills;
			untimedLoot.clear();
			loot.forEach((id, line) -> untimedLoot.put(id, line.getQuantity()));
			activity.restore(0, activity.getLastMillis());
			trip.restore(0, 0);
		}
	}

	// Keyed by NPC name, so every "Abyssal demon" shares one entry.
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private int revision;
	// The monster whose trip is going: its gold per hour keeps counting after the last kill.
	private String currentTrip;
	private LongSupplier clock = System::currentTimeMillis;
	// Whether an item is a collection log unique of a monster.
	private BiPredicate<String, String> uniqueTest = (monster, item) -> false;
	// Data saved before 2.1.2 was upgraded while loading, so it should be saved again.
	private boolean upgraded;

	/**
	 * Hits that count towards accuracy fewer than this are too few to say.
	 */
	static final int MIN_ROLLS = 10;

	/**
	 * Any hit, 0s included, for accuracy. Recorded even when 0s are left out of the average.
	 */
	public synchronized void recordRoll(String monsterName, boolean didDamage)
	{
		if (isBlank(monsterName))
		{
			return;
		}
		Entry e = entries.computeIfAbsent(monsterName, Entry::new);
		e.rolls++;
		if (didDamage)
		{
			e.landed++;
		}
		revision++;
	}

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
		mark(e, nowMillis);
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
		mark(e, nowMillis);
		revision++;
	}

	/**
	 * @return kills of one monster
	 */
	public synchronized int getKills(String monsterName)
	{
		Entry e = monsterName == null ? null : entries.get(monsterName);
		return e == null ? 0 : e.kills;
	}

	/**
	 * @return kills of every monster together
	 */
	public synchronized long getTotalKills()
	{
		long total = 0;
		for (Entry e : entries.values())
		{
			total += e.kills;
		}
		return total;
	}

	/**
	 * @return the biggest hit on any monster, or -1
	 */
	public synchronized int getBiggestHit()
	{
		int biggest = -1;
		for (Entry e : entries.values())
		{
			biggest = Math.max(biggest, e.biggestHit);
		}
		return biggest;
	}

	/**
	 * @return hits on every monster together
	 */
	public synchronized long getTotalHits()
	{
		long total = 0;
		for (Entry e : entries.values())
		{
			total += e.hits;
		}
		return total;
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
			if (uniqueTest.test(monsterName, d.name))
			{
				e.lastUniqueKill = e.kills;
			}
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
		mark(e, nowMillis);
		revision++;
	}

	/**
	 * @return every monster, most recently fought first
	 */
	/**
	 * @return one monster's stats (loot at Grand Exchange prices), or null if it isn't tracked
	 */
	public synchronized Snapshot snapshotOf(String monsterName)
	{
		if (monsterName == null)
		{
			return null;
		}
		for (Entry e : entries.values())
		{
			if (e.name.equalsIgnoreCase(monsterName))
			{
				return new Snapshot(e);
			}
		}
		return null;
	}

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
		long now = clock.getAsLong();
		for (Entry e : entries.values())
		{
			out.add(new Snapshot(e, price, ignoredItem, rareValue, e.name.equals(currentTrip), now));
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
			currentTrip = null;
			revision++;
		}
	}

	private static void mergeInto(Entry target, Entry source)
	{
		for (RareDrop r : source.rareDrops)
		{
			target.rareDrops.add(new RareDrop(r.kill + target.kills, r.itemId, r.unitPrice, r.haPrice));
		}
		if (source.restoredAt > 0)
		{
			target.restoredAt = Math.max(target.restoredAt, target.kills + source.restoredAt);
		}
		if (source.lastUniqueKill >= 0)
		{
			target.lastUniqueKill = Math.max(target.lastUniqueKill, target.kills + source.lastUniqueKill);
		}
		target.kills += source.kills;
		target.hits += source.hits;
		target.totalDamage += source.totalDamage;
		target.rolls += source.rolls;
		target.landed += source.landed;
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
		target.trip.add(source.trip);
		target.untimedKills += source.untimedKills;
		source.untimedLoot.forEach((id, n) -> target.untimedLoot.merge(id, n, Long::sum));
	}

	/**
	 * @return whether the last load upgraded data saved before 2.1.2, then forgets it
	 */
	public synchronized boolean takeUpgraded()
	{
		boolean was = upgraded;
		upgraded = false;
		return was;
	}

	/**
	 * Per-hour rates start again from now, for data whose timed part can't be told apart.
	 */
	public synchronized void startRatesAgain()
	{
		for (Entry e : entries.values())
		{
			e.startRatesAgain();
		}
		revision++;
	}

	/**
	 * A kill, hit or drop: counts towards the time it's fought, and starts or continues its trip.
	 * Moving on to another monster ends the last one's trip.
	 */
	private void mark(Entry e, long nowMillis)
	{
		e.activity.mark(nowMillis);
		if (currentTrip != null && !currentTrip.equals(e.name))
		{
			Entry last = entries.get(currentTrip);
			if (last != null)
			{
				last.trip.end();
			}
		}
		currentTrip = e.name;
		e.trip.mark(nowMillis);
	}

	/**
	 * Ends the current trip, e.g. on logout: time from now on isn't counted.
	 */
	public synchronized void endTrip()
	{
		Entry last = currentTrip == null ? null : entries.get(currentTrip);
		if (last != null)
		{
			last.trip.end();
		}
		currentTrip = null;
	}

	/**
	 * @return the monster whose trip is going, or null
	 */
	public synchronized String getCurrentTrip()
	{
		return currentTrip;
	}

	/**
	 * For tests: what "now" is.
	 */
	void setClock(LongSupplier clock)
	{
		this.clock = clock;
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
		if (monsterName.equals(currentTrip))
		{
			currentTrip = null;
		}
	}

	public synchronized void reset()
	{
		entries.clear();
		currentTrip = null;
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
			s.rolls = e.rolls;
			s.landed = e.landed;
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
			s.restoredAt = e.restoredAt;
			s.tripMillis = e.trip.getTripMillis(0, false);
			s.fightMillis = e.trip.getFightMillis();
			s.lastUniqueKill = e.lastUniqueKill;
			s.untimedKills = e.untimedKills;
			s.untimedLoot = new HashMap<>(e.untimedLoot);
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
		currentTrip = null;
		upgraded = false;
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
				e.rolls = Math.max(0, s.rolls);
				e.landed = Math.max(0, Math.min(e.rolls, s.landed));
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
				// Saved before 2.1.2: streaks whose last drop isn't known count from now.
				e.restoredAt = s.tripMillis < 0 ? e.kills : Math.max(0, Math.min(e.kills, s.restoredAt));
				// Saved before 2.1.2: start from the fighting time there was.
				e.trip.restore(s.tripMillis < 0 ? s.activeMillis : s.tripMillis,
					s.fightMillis < 0 ? s.activeMillis : s.fightMillis);
				e.lastUniqueKill = Math.min(e.kills, s.lastUniqueKill);
				upgraded |= s.untimedKills < 0 || s.tripMillis < 0;
				if (s.untimedKills >= 0)
				{
					e.untimedKills = Math.min(e.kills, s.untimedKills);
					if (s.untimedLoot != null)
					{
						s.untimedLoot.forEach((id, n) ->
						{
							if (id != null && n != null && n > 0)
							{
								e.untimedLoot.put(id, n);
							}
						});
					}
				}
				else if (s.restoredAt > 0)
				{
					// Restored before 2.1.2, when restored kills weren't kept apart: rates start again.
					e.startRatesAgain();
				}
				entries.put(e.name, e);
			}
		}
		backfillUniques();
		revision++;
	}

	/**
	 * Saved before 2.1.2: the last unique's kill isn't stored, but valuable drops were saved
	 * with their kill, so take it from those.
	 */
	private void backfillUniques()
	{
		for (Entry e : entries.values())
		{
			if (e.lastUniqueKill >= 0)
			{
				continue;
			}
			for (RareDrop r : e.rareDrops)
			{
				LootLine line = e.loot.get(r.itemId);
				String item = line == null ? null : line.getName();
				if (item != null && uniqueTest.test(e.name, item))
				{
					e.lastUniqueKill = Math.max(e.lastUniqueKill, r.kill);
				}
			}
		}
	}

	/**
	 * @param test whether an item is a collection log unique of a monster
	 */
	public synchronized void setUniqueTest(BiPredicate<String, String> test)
	{
		uniqueTest = test;
		backfillUniques();
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
	 * Like {@link #view(List, MonsterSort, Set)}, with pinned monsters first, in the order
	 * they're pinned in (the set's iteration order, which the player sets by dragging).
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
		Map<String, Integer> pinRank = new HashMap<>();
		for (String name : pinnedLowercase)
		{
			pinRank.putIfAbsent(name, pinRank.size());
		}
		out.sort((a, b) ->
		{
			Integer ra = pinRank.get(a.getName().toLowerCase(Locale.ROOT));
			Integer rb = pinRank.get(b.getName().toLowerCase(Locale.ROOT));
			if (ra != null && rb != null)
			{
				return Integer.compare(ra, rb);
			}
			if (ra != null || rb != null)
			{
				return ra != null ? -1 : 1;
			}
			return order.compare(a, b);
		});
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
