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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How dry the player is on each collection log item they're missing from a boss: their
 * kill count against the item's drop rate.
 *
 * Drop rates are from the Old School RuneScape Wiki's drop tables (CC BY-NC-SA 3.0),
 * bundled with the plugin as unique_rates.json, one "1 in N" chance per kill for each item.
 * Nothing is looked up online.
 */
final class DryCalculator
{
	/**
	 * One missing item.
	 */
	static final class Dry
	{
		private final String item;
		private final double oneIn;
		private final int killCount;

		Dry(String item, double oneIn, int killCount)
		{
			this.item = item;
			this.oneIn = oneIn;
			this.killCount = killCount;
		}

		String getItem()
		{
			return item;
		}

		double getOneIn()
		{
			return oneIn;
		}

		/**
		 * @return kill count over the drop rate: 2.0 means twice the rate
		 */
		double getRatio()
		{
			return killCount / oneIn;
		}

		/**
		 * @return the chance of having had at least one by now, 0 to 1
		 */
		double getChanceByNow()
		{
			return 1 - Math.pow(1 - 1 / oneIn, killCount);
		}
	}

	// boss (lower case) -> item (lower case) -> 1 in N per kill
	private final Map<String, Map<String, Double>> rates = new HashMap<>();
	// The item names as the wiki writes them, for display.
	private final Map<String, String> names = new HashMap<>();

	/**
	 * @param table boss -> item -> 1 in N, as in unique_rates.json
	 */
	DryCalculator(Map<String, Map<String, Double>> table)
	{
		if (table == null)
		{
			return;
		}
		for (Map.Entry<String, Map<String, Double>> boss : table.entrySet())
		{
			if (boss.getKey() == null || boss.getValue() == null)
			{
				continue;
			}
			Map<String, Double> items = new HashMap<>();
			for (Map.Entry<String, Double> item : boss.getValue().entrySet())
			{
				if (item.getKey() != null && item.getValue() != null && item.getValue() > 1)
				{
					items.put(key(item.getKey()), item.getValue());
				}
			}
			rates.put(key(boss.getKey()), items);
		}
	}

	private static String key(String s)
	{
		return s.trim().toLowerCase(Locale.ROOT);
	}

	boolean hasBoss(String boss)
	{
		return boss != null && rates.containsKey(key(boss));
	}

	/**
	 * @param missing   collection log items not yet obtained
	 * @param killCount the boss's kill count
	 * @return the missing items with a known drop rate, driest first
	 */
	List<Dry> dry(String boss, List<String> missing, int killCount)
	{
		Map<String, Double> items = boss == null ? null : rates.get(key(boss));
		if (items == null || missing == null || killCount <= 0)
		{
			return Collections.emptyList();
		}
		List<Dry> out = new ArrayList<>();
		for (String name : missing)
		{
			Double oneIn = name == null ? null : items.get(key(name));
			if (oneIn != null)
			{
				out.add(new Dry(name, oneIn, killCount));
			}
		}
		out.sort((a, b) -> Double.compare(b.getRatio(), a.getRatio()));
		return out;
	}

	/**
	 * e.g. "Driest: Vorkath's head, 2.4x the rate".
	 */
	static String label(Dry d)
	{
		return String.format(Locale.US, "Driest: %s, %.1fx the rate", d.getItem(), d.getRatio());
	}

	/**
	 * One tooltip line, e.g. "Vorkath's head: 1/50, 2.4x the rate (91% would have it by now)".
	 */
	static String line(Dry d)
	{
		return String.format(Locale.US, "%s: 1/%s, %.1fx the rate (%d%% would have it by now)", d.getItem(),
			Formatting.withCommas(Math.round(d.getOneIn())), d.getRatio(), Math.round(d.getChanceByNow() * 100));
	}
}
