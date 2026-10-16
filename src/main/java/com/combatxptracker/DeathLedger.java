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
 * Deaths per monster: how many, what the player was carrying (Grand Exchange value of their
 * inventory and equipment) and when the last one was. What's carried is what was at risk,
 * not what was lost: most of it goes to a gravestone or Death's Office.
 *
 * Saved per account as JSON, so field names are the save format. Synchronized: recorded on
 * the client thread and read by the panel.
 */
final class DeathLedger
{
	static final class Deaths
	{
		int count;
		long carried;
		long lastMillis;
	}

	private Map<String, Deaths> byMonster = new HashMap<>();
	private int revision;

	private static String key(String name)
	{
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * @return how many times the player has now died to this monster
	 */
	synchronized int record(String monster, long carried, long nowMillis)
	{
		Deaths d = byMonster.computeIfAbsent(key(monster), k -> new Deaths());
		d.count++;
		d.carried += Math.max(0, carried);
		d.lastMillis = nowMillis;
		revision++;
		return d.count;
	}

	/**
	 * @return a copy of a monster's deaths, or null for none
	 */
	synchronized Deaths get(String monster)
	{
		Deaths d = monster == null ? null : byMonster.get(key(monster));
		if (d == null)
		{
			return null;
		}
		Deaths copy = new Deaths();
		copy.count = d.count;
		copy.carried = d.carried;
		copy.lastMillis = d.lastMillis;
		return copy;
	}

	synchronized int totalDeaths()
	{
		int total = 0;
		for (Deaths d : byMonster.values())
		{
			total += d.count;
		}
		return total;
	}

	/**
	 * e.g. "Deaths: 2", or null for none.
	 */
	static String label(Deaths d)
	{
		return d == null || d.count <= 0 ? null : "Deaths: " + Formatting.withCommas(d.count);
	}

	/**
	 * e.g. "Died to Vorkath (2nd time). You were carrying 12.4M gp."
	 */
	static String message(String monster, int count, long carried)
	{
		StringBuilder sb = new StringBuilder("Died to ").append(monster);
		if (count > 1)
		{
			sb.append(" (").append(ordinal(count)).append(" time)");
		}
		sb.append('.');
		if (carried > 0)
		{
			sb.append(" You were carrying ").append(Formatting.compactXp(carried)).append(" gp.");
		}
		return sb.toString();
	}

	static String ordinal(int n)
	{
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
			: n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
		return Formatting.withCommas(n) + suffix;
	}

	synchronized void remove(String monster)
	{
		if (byMonster.remove(key(monster)) != null)
		{
			revision++;
		}
	}

	synchronized void reset()
	{
		byMonster.clear();
		revision++;
	}

	synchronized boolean isEmpty()
	{
		return byMonster.isEmpty();
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized Map<String, Deaths> exportState()
	{
		return new HashMap<>(byMonster);
	}

	/**
	 * @param saved from {@link #exportState()}, or null to start empty
	 */
	synchronized void importState(Map<String, Deaths> saved)
	{
		byMonster.clear();
		revision++;
		if (saved == null)
		{
			return;
		}
		for (Map.Entry<String, Deaths> e : saved.entrySet())
		{
			Deaths d = e.getValue();
			if (e.getKey() != null && d != null && d.count > 0 && d.carried >= 0)
			{
				byMonster.put(key(e.getKey()), d);
			}
		}
	}
}
