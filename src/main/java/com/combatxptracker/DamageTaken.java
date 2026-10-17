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
 * Damage the player took per boss kill, and their flawless kills (no damage taken). Only
 * bosses: with several monsters fighting at once, one kill's damage can't be told from the
 * next's.
 *
 * Saved per account as JSON, so field names are the save format. Synchronized: recorded on
 * the client thread and read by the panel.
 */
final class DamageTaken
{
	/**
	 * Fewer kills than this say too little.
	 */
	static final int MIN_KILLS = 3;

	static final class Stats
	{
		int kills;
		long damage;
		int flawless;

		/**
		 * @return damage taken per kill, or -1 with too few kills
		 */
		double perKill()
		{
			return kills < MIN_KILLS ? -1 : damage / (double) kills;
		}

		/**
		 * e.g. "12 (24%)".
		 */
		String flawlessLabel()
		{
			return Formatting.withCommas(flawless) + " (" + Math.round(flawless * 100.0 / Math.max(1, kills)) + "%)";
		}
	}

	private Map<String, Stats> byMonster = new HashMap<>();
	private int revision;

	private static String key(String name)
	{
		return name.toLowerCase(Locale.ROOT);
	}

	synchronized void record(String monster, int damage)
	{
		Stats s = byMonster.computeIfAbsent(key(monster), k -> new Stats());
		s.kills++;
		s.damage += Math.max(0, damage);
		if (damage <= 0)
		{
			s.flawless++;
		}
		revision++;
	}

	/**
	 * @return a copy of a monster's stats, or null if there are none
	 */
	synchronized Stats get(String monster)
	{
		Stats s = monster == null ? null : byMonster.get(key(monster));
		if (s == null)
		{
			return null;
		}
		Stats copy = new Stats();
		copy.kills = s.kills;
		copy.damage = s.damage;
		copy.flawless = s.flawless;
		return copy;
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

	synchronized Map<String, Stats> exportState()
	{
		return new HashMap<>(byMonster);
	}

	/**
	 * @param saved from {@link #exportState()}, or null to start empty
	 */
	synchronized void importState(Map<String, Stats> saved)
	{
		byMonster.clear();
		revision++;
		if (saved == null)
		{
			return;
		}
		for (Map.Entry<String, Stats> e : saved.entrySet())
		{
			Stats s = e.getValue();
			if (e.getKey() != null && s != null && s.kills > 0 && s.damage >= 0 && s.flawless >= 0 && s.flawless <= s.kills)
			{
				byMonster.put(key(e.getKey()), s);
			}
		}
	}
}
