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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * WHAT: The boss pets in the bundled drop rates, by name.
 * WHY: Pets have no common naming ("Vorki", "Pet Zilyana", "Hellpuppy"), and "pet chance by
 * now" is the stat players share most (2.5).
 * PRE: None.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class Pets
{
	private static final Set<String> NAMES = new HashSet<>();

	static
	{
		for (String pet : Arrays.asList("Baby Mole", "Baron", "Beef", "Butch", "Callisto cub", "Dom", "Gull (pet)", "Hellpuppy",
			"Huberte", "Ikkle Hydra", "Kalphite Princess", "Lil'viathan", "Little Nightmare", "Maggot marquess", "Moxi",
			"Muphin", "Nexling", "Nid", "Noon", "Pet chaos elemental", "Pet Dagannoth Prime", "Pet Dagannoth Rex",
			"Pet Dagannoth Supreme", "Pet dark core", "Pet General Graardor", "Pet K'ril Tsutsaroth", "Pet Kraken",
			"Pet Kree'arra", "Pet Smoke Devil", "Pet Snakeling", "Pet Zilyana", "Prince Black Dragon", "Scorpia's offspring",
			"Scurry", "Smolcano", "Sraracha", "Tangleroot", "Venenatis spiderling", "Vet'ion Jr.", "Vorki", "Wisp", "Yami"))
		{
			NAMES.add(pet.toLowerCase(Locale.ROOT));
		}
	}

	private Pets()
	{
	}

	/**
	 * WHAT: Whether an item is a boss pet.
	 * WHY: Picks the pet out of a boss's collection log and drop rates.
	 * PRE: name may be null.
	 * POST: Nothing changes.
	 * RETURN: true for a pet
	 * THROWS: Nothing.
	 */
	static boolean isPet(String name)
	{
		return name != null && NAMES.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * WHAT: The boss's pet among your missing collection log items, with its dry numbers.
	 * WHY: "63% by now" on the boss card's Luck section.
	 * PRE: dry is not null.
	 * POST: Nothing changes.
	 * RETURN: the pet's entry, or null if the pet isn't missing or has no bundled rate
	 * THROWS: Nothing.
	 */
	static DryCalculator.Dry missing(List<DryCalculator.Dry> dry)
	{
		for (DryCalculator.Dry d : dry)
		{
			if (isPet(d.getItem()))
			{
				return d;
			}
		}
		return null;
	}

	/**
	 * WHAT: Whether you have the boss's pet, from its collection log page.
	 * WHY: The Luck section says "Owned" instead of a chance.
	 * PRE: items may be null (page not seen yet).
	 * POST: Nothing changes.
	 * RETURN: true if the page shows its pet as obtained
	 * THROWS: Nothing.
	 */
	static boolean owned(List<CollectionLog.Item> items)
	{
		if (items == null)
		{
			return false;
		}
		for (CollectionLog.Item item : items)
		{
			if (item.obtained && isPet(item.name))
			{
				return true;
			}
		}
		return false;
	}
}
