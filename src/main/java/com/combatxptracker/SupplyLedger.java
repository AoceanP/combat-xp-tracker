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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Supplies used while fighting each monster: food, potion doses, runes, ammo and cannonballs,
 * with what they cost at Grand Exchange prices. Saved per account as JSON, so field names
 * are the save format. Synchronized: recorded on the client thread and read by the panel.
 */
final class SupplyLedger
{
	enum Category
	{
		FOOD("Food", "eaten"),
		POTIONS("Potions", "doses"),
		RUNES("Runes", "runes"),
		AMMO("Ammo", "used"),
		CANNONBALLS("Cannonballs", "fired");

		private final String displayName;
		private final String countWord;

		Category(String displayName, String countWord)
		{
			this.displayName = displayName;
			this.countWord = countWord;
		}

		String getDisplayName()
		{
			return displayName;
		}

		String getCountWord()
		{
			return countWord;
		}
	}

	/**
	 * One monster's supplies: gp and how many, per category.
	 */
	static final class Totals
	{
		Map<Category, Long> gp = new EnumMap<>(Category.class);
		Map<Category, Long> count = new EnumMap<>(Category.class);

		long totalGp()
		{
			long total = 0;
			for (long v : gp.values())
			{
				total += v;
			}
			return total;
		}

		boolean isEmpty()
		{
			return gp.isEmpty() && count.isEmpty();
		}

		Totals copy()
		{
			Totals t = new Totals();
			t.gp.putAll(gp);
			t.count.putAll(count);
			return t;
		}
	}

	private final Map<String, Totals> byMonster = new HashMap<>();
	private int revision;

	synchronized void record(String monster, Category category, long gp, long count)
	{
		if (monster == null || (gp <= 0 && count <= 0))
		{
			return;
		}
		Totals t = byMonster.computeIfAbsent(key(monster), k -> new Totals());
		t.gp.merge(category, Math.max(0, gp), Long::sum);
		t.count.merge(category, Math.max(0, count), Long::sum);
		revision++;
	}

	/**
	 * @return a copy of a monster's supplies, or null if none were recorded
	 */
	synchronized Totals get(String monster)
	{
		Totals t = monster == null ? null : byMonster.get(key(monster));
		return t == null || t.isEmpty() ? null : t.copy();
	}

	synchronized void remove(String monster)
	{
		if (monster != null && byMonster.remove(key(monster)) != null)
		{
			revision++;
		}
	}

	synchronized void reset()
	{
		byMonster.clear();
		revision++;
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized boolean isEmpty()
	{
		return byMonster.isEmpty();
	}

	synchronized Map<String, Totals> exportState()
	{
		Map<String, Totals> out = new HashMap<>();
		for (Map.Entry<String, Totals> e : byMonster.entrySet())
		{
			out.put(e.getKey(), e.getValue().copy());
		}
		return out;
	}

	synchronized void importState(Map<String, Totals> saved)
	{
		byMonster.clear();
		if (saved != null)
		{
			for (Map.Entry<String, Totals> e : saved.entrySet())
			{
				Totals t = e.getValue();
				if (e.getKey() != null && t != null && t.gp != null && t.count != null)
				{
					Totals clean = new Totals();
					// Old or hand-edited saves may hold categories this version doesn't know.
					t.gp.forEach((c, v) -> putIfKnown(clean.gp, c, v));
					t.count.forEach((c, v) -> putIfKnown(clean.count, c, v));
					byMonster.put(key(e.getKey()), clean);
				}
			}
		}
		revision++;
	}

	private static void putIfKnown(Map<Category, Long> map, Category c, Long v)
	{
		if (c != null && v != null && v >= 0)
		{
			map.put(c, v);
		}
	}

	/**
	 * The value given up when items changed: what went down, less what came back (a potion
	 * dose turns a (4) into a (3); the (3) is still worth something).
	 *
	 * @return the gp used up, never below 0
	 */
	static long consumedValue(Map<Integer, Long> before, Map<Integer, Long> after, ToLongFunction<Integer> price)
	{
		long value = 0;
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		for (int id : ids)
		{
			long diff = before.getOrDefault(id, 0L) - after.getOrDefault(id, 0L);
			value += diff * price.applyAsLong(id);
		}
		return Math.max(0, value);
	}

	/**
	 * Items that went down between two counts, by how many.
	 */
	static Map<Integer, Long> decreases(Map<Integer, Long> before, Map<Integer, Long> after)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (Map.Entry<Integer, Long> e : before.entrySet())
		{
			long diff = e.getValue() - after.getOrDefault(e.getKey(), 0L);
			if (diff > 0)
			{
				out.put(e.getKey(), diff);
			}
		}
		return out;
	}

	/**
	 * e.g. "412K", with each category on hover.
	 */
	static String tooltip(Totals t)
	{
		StringBuilder sb = new StringBuilder("<html>Supplies used fighting this monster, at Grand Exchange prices:");
		for (Category c : Category.values())
		{
			long gp = t.gp.getOrDefault(c, 0L);
			long count = t.count.getOrDefault(c, 0L);
			if (gp > 0 || count > 0)
			{
				sb.append("<br>").append(c.getDisplayName()).append(": ").append(Formatting.compactXp(gp)).append(" gp (")
					.append(Formatting.withCommas(count)).append(' ').append(c.getCountWord()).append(')');
			}
		}
		return sb.append("</html>").toString();
	}

	private static String key(String monster)
	{
		return monster.toLowerCase(Locale.ROOT);
	}
}
