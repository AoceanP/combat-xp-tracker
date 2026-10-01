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

	static
	{
		add("Crushing hand", "Crawling hand");
		add("Chasm Crawler", "Cave crawler");
		add("Screaming banshee", "Banshee");
		add("Screaming twisted banshee", "Twisted banshee");
		add("Giant rockslug", "Rockslug");
		add("Cockathrice", "Cockatrice");
		add("Flaming pyrelord", "Pyrefiend");
		add("Infernal pyrelord", "Pyrelord");
		add("Monstrous basilisk", "Basilisk");
		add("Malevolent Mage", "Infernal Mage");
		add("Insatiable Bloodveld", "Bloodveld");
		add("Insatiable mutated Bloodveld", "Mutated Bloodveld");
		add("Dire gryphon", "Gryphon");
		add("Vitreous Jelly", "Jelly");
		add("Vitreous warped Jelly", "Warped Jelly");
		add("Vitreous Chilled Jelly", "Chilled Jelly");
		add("Spiked Turoth", "Turoth");
		add("Mutated Terrorbird", "Warped terrorbird");
		add("Mutated Tortoise", "Warped tortoise");
		add("Cave abomination", "Cave horror");
		add("Abhorrent spectre", "Aberrant spectre");
		add("Repugnant spectre", "Deviant spectre");
		add("Basilisk Sentinel", "Basilisk Knight");
		add("Shadow Wyrm", "Wyrm");
		add("Magma strykewyrm", "Lava strykewyrm");
		add("Choke devil", "Dust devil");
		add("King kurask", "Kurask");
		add("Blood-starved venator", "Venator");
		add("Marble gargoyle", "Gargoyle");
		add("Ancient Custodian", "Elder Custodian Stalker");
		add("Elder Aquanite", "Aquanite");
		add("Nechryarch", "Nechryael");
		add("Guardian Drake", "Drake");
		add("Greater abyssal demon", "Abyssal demon");
		add("Night beast", "Dark beast");
		add("Nuclear smoke devil", "Smoke devil");
		add("Dreadborn Araxyte", "Araxyte");
		add("Colossal Hydra", "Hydra");
	}

	private SuperiorMonsters()
	{
	}

	private static void add(String superior, String normal)
	{
		NORMAL_BY_SUPERIOR.put(superior.toLowerCase(Locale.ROOT), normal);
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
