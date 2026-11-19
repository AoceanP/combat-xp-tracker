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
 * Superior slayer monsters and the normal monster each replaces (OSRS Wiki, "Superior
 * slayer monster"). They spawn from task kills once Bigger and Badder is unlocked.
 */
final class SuperiorMonsters
{
	/**
	 * The game's message when one spawns.
	 */
	static final String SPAWN_MESSAGE = "A superior foe has appeared...";

	/**
	 * Base chance per task kill with Bigger and Badder (1/150 with the elite combat
	 * achievements).
	 */
	static final int BASE_RATE = 200;

	private static final Map<String, String> NORMAL_BY_SUPERIOR = new HashMap<>();
	// Superior (lower case) -> the Slayer level its normal monster needs, which sets its drop chances.
	private static final Map<String, Integer> SLAYER_LEVEL = new HashMap<>();

	/** The two unique drops this plugin follows; each is 1/8 of its own unique table. */
	static final String IMBUED_HEART = "Imbued heart";
	static final String ETERNAL_GEM = "Eternal gem";

	static
	{
		add("Crushing hand", "Crawling hand", 5);
		add("Chasm Crawler", "Cave crawler", 10);
		add("Screaming banshee", "Banshee", 15);
		add("Screaming twisted banshee", "Twisted banshee", 15);
		add("Giant rockslug", "Rockslug", 20);
		add("Cockathrice", "Cockatrice", 25);
		add("Flaming pyrelord", "Pyrefiend", 30);
		add("Infernal pyrelord", "Pyrelord", 30);
		add("Monstrous basilisk", "Basilisk", 40);
		add("Malevolent Mage", "Infernal Mage", 45);
		add("Insatiable Bloodveld", "Bloodveld", 50);
		add("Insatiable mutated Bloodveld", "Mutated Bloodveld", 50);
		add("Dire gryphon", "Gryphon", 51);
		add("Vitreous Jelly", "Jelly", 52);
		add("Vitreous warped Jelly", "Warped Jelly", 52);
		add("Vitreous Chilled Jelly", "Chilled Jelly", 52);
		add("Spiked Turoth", "Turoth", 55);
		add("Mutated Terrorbird", "Warped terrorbird", 56);
		add("Mutated Tortoise", "Warped tortoise", 56);
		add("Cave abomination", "Cave horror", 58);
		add("Abhorrent spectre", "Aberrant spectre", 60);
		add("Repugnant spectre", "Deviant spectre", 60);
		add("Basilisk Sentinel", "Basilisk Knight", 60);
		add("Shadow Wyrm", "Wyrm", 62);
		add("Magma strykewyrm", "Lava strykewyrm", 62);
		add("Choke devil", "Dust devil", 65);
		add("King kurask", "Kurask", 70);
		add("Blood-starved venator", "Venator", 74);
		add("Marble gargoyle", "Gargoyle", 75);
		add("Ancient Custodian", "Elder Custodian Stalker", 76);
		add("Elder Aquanite", "Aquanite", 78);
		add("Nechryarch", "Nechryael", 80);
		add("Guardian Drake", "Drake", 84);
		add("Greater abyssal demon", "Abyssal demon", 85);
		add("Night beast", "Dark beast", 90);
		add("Nuclear smoke devil", "Smoke devil", 93);
		add("Dreadborn Araxyte", "Araxyte", 92);
		add("Colossal Hydra", "Hydra", 95);
	}

	private SuperiorMonsters()
	{
	}

	private static void add(String superior, String normal, int slayerLevel)
	{
		NORMAL_BY_SUPERIOR.put(superior.toLowerCase(Locale.ROOT), normal);
		SLAYER_LEVEL.put(superior.toLowerCase(Locale.ROOT), slayerLevel);
	}

	/**
	 * WHAT: Every superior's name, as written in the table above but lower case.
	 * WHY: The plugin looks up each one's kills to work out heart and gem luck.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: the names
	 * THROWS: Nothing.
	 */
	static java.util.Set<String> names()
	{
		return java.util.Collections.unmodifiableSet(SLAYER_LEVEL.keySet());
	}

	/**
	 * WHAT: The chance of an imbued heart (or, the same, an eternal gem) from one superior kill.
	 * WHY: OSRS Wiki, "Superior slayer monster": each unique table is rolled at
	 * 1 in (200 - (Slayer level + 55)^2 / 125), and the item is 1/8 of its table.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: N in "1 in N", or -1 for a name that isn't a superior
	 * THROWS: Nothing.
	 */
	static double uniqueOneIn(String superior)
	{
		Integer level = superior == null ? null : SLAYER_LEVEL.get(superior.toLowerCase(Locale.ROOT));
		if (level == null)
		{
			return -1;
		}
		double table = 200 - (level + 55) * (level + 55) / 125.0;
		return 8 * table;
	}

	/**
	 * WHAT: Your luck on one superior unique: how many you've had against how many your
	 * superior kills should give, and the chance of having one by now.
	 * WHY: Imbued hearts and eternal gems are the drops superiors are hunted for (2.5).
	 * PRE: killsBySuperior maps superior names to kills; got is how many of the item you've had.
	 * POST: Nothing changes.
	 * RETURN: the luck; its kills are 0 if you've killed no superiors
	 * THROWS: Nothing.
	 */
	static UniqueLuck luck(Map<String, Integer> killsBySuperior, long got)
	{
		int kills = 0;
		double expected = 0;
		double noneYet = 1;
		for (Map.Entry<String, Integer> e : killsBySuperior.entrySet())
		{
			double oneIn = uniqueOneIn(e.getKey());
			if (oneIn <= 0 || e.getValue() == null || e.getValue() <= 0)
			{
				continue;
			}
			kills += e.getValue();
			expected += e.getValue() / oneIn;
			noneYet *= Math.pow(1 - 1 / oneIn, e.getValue());
		}
		return new UniqueLuck(kills, expected, 1 - noneYet, got);
	}

	/**
	 * WHAT: One superior unique's luck, ready to show.
	 * WHY: The Slayer tab's superiors card (2.5).
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class UniqueLuck
	{
		final int kills;
		final double expected;
		final double chanceByNow;
		final long got;

		UniqueLuck(int kills, double expected, double chanceByNow, long got)
		{
			this.kills = kills;
			this.expected = expected;
			this.chanceByNow = chanceByNow;
			this.got = got;
		}

		@Override
		public String toString()
		{
			return kills + "/" + got + "/" + Math.round(expected * 1000);
		}
	}

	static boolean isSuperior(String npcName)
	{
		return npcName != null && NORMAL_BY_SUPERIOR.containsKey(npcName.toLowerCase(Locale.ROOT));
	}

	/**
	 * @return the normal monster a superior replaces, e.g. "Abyssal demon" for "Greater
	 * abyssal demon", or null if it isn't a superior
	 */
	static String normalFor(String npcName)
	{
		return npcName == null ? null : NORMAL_BY_SUPERIOR.get(npcName.toLowerCase(Locale.ROOT));
	}
}
