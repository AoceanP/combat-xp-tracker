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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Items some slayer tasks need (OSRS Wiki, "Slayer equipment" and each monster's page),
 * so the Slayer tab can say what to bring and warn when it's missing.
 */
final class TaskRequirements
{
	/**
	 * One thing to bring. It's met by any of the listed items (matched by name, so every
	 * variant counts), or by a Slayer reward that removes the need.
	 */
	static final class Need
	{
		private final String label;
		private final List<String> items;
		private final int unlockVarbit;
		/**
		 * Recommended rather than required, e.g. leaf-bladed weapons: there are too many
		 * alternatives (spells, ammo) to check, so it's shown without a warning.
		 */
		private final boolean infoOnly;

		Need(String label, int unlockVarbit, boolean infoOnly, String... items)
		{
			this.label = label;
			this.items = List.of(items);
			this.unlockVarbit = unlockVarbit;
			this.infoOnly = infoOnly;
		}

		String getLabel()
		{
			return label;
		}

		int getUnlockVarbit()
		{
			return unlockVarbit;
		}

		boolean isInfoOnly()
		{
			return infoOnly;
		}

		/**
		 * @param carried lower-case names of everything worn and in the inventory
		 */
		boolean isMetBy(Collection<String> carried)
		{
			for (String have : carried)
			{
				for (String item : items)
				{
					if (have.contains(item))
					{
						return true;
					}
				}
			}
			return false;
		}
	}

	private static final String HELM = "slayer helmet";
	private static final int NONE = -1;
	private static final Map<String, List<Need>> BY_TASK = new LinkedHashMap<>();

	static
	{
		Need nosePeg = new Need("Nose peg or Slayer helmet", NONE, false, "nose peg", HELM);
		Need earmuffs = new Need("Earmuffs or Slayer helmet", NONE, false, "earmuffs", HELM);
		Need mirror = new Need("Mirror shield or V's shield", NONE, false, "mirror shield", "v's shield");
		Need facemask = new Need("Facemask or Slayer helmet", NONE, false, "facemask", "gas mask", HELM);
		Need stoneBoots = new Need("Boots of stone, brimstone or granite boots (Karuulm dungeon)", NONE, false,
			"boots of stone", "boots of brimstone", "granite boots");
		Need wyvernShield = new Need("Elemental, mind or anti-wyvern shield, or a dragonfire shield/ward", NONE, false,
			"elemental shield", "mind shield", "ancient wyvern shield", "dragonfire shield", "dragonfire ward");
		Need dragonfire = new Need("Anti-dragon shield, dragonfire shield/ward or antifire potions", NONE, false,
			"anti-dragon shield", "dragonfire shield", "dragonfire ward", "antifire");
		Need leafBladed = new Need("Leaf-bladed weapon, broad ammo or Magic Dart", NONE, true,
			"leaf-bladed", "broad bolts", "broad arrows");

		add("Aberrant spectres", nosePeg);
		add("Deviant spectres", nosePeg);
		add("Banshees", earmuffs);
		add("Basilisks", mirror);
		add("Cockatrice", mirror);
		add("Cave horrors", new Need("Witchwood icon", NONE, false, "witchwood icon"));
		add("Dust devils", facemask);
		add("Smoke devils", facemask);
		add("Gargoyles", new Need("Rock hammer (or the Gargoyle smasher reward)", VarbitID.SLAYER_AUTOKILL_GARGOYLES, false,
			"rock hammer", "rock thrownhammer", "granite hammer"));
		add("Rockslugs", new Need("Bag of salt (or the Slug salter reward)", VarbitID.SLAYER_AUTOKILL_ROCKSLUGS, false,
			"bag of salt"));
		add("Lizards", new Need("Ice cooler (or the Reptile freezer reward)", VarbitID.SLAYER_AUTOKILL_DESERTLIZARDS, false,
			"ice cooler"));
		add("Mutated zygomites", new Need("Fungicide spray (or the Shroom sprayer reward)", VarbitID.SLAYER_AUTOKILL_ZYGOMITES,
			false, "fungicide spray"));
		add("Harpie bug swarms", new Need("Lit bug lantern", NONE, false, "lit bug lantern"));
		add("Killerwatts", new Need("Insulated boots", NONE, false, "insulated boots"));
		add("Wall beasts", new Need("Spiny helmet or Slayer helmet", NONE, false, "spiny helmet", HELM));
		add("Mogres", new Need("Fishing explosive to lure them", NONE, false, "fishing explosive"));
		add("Turoth", leafBladed);
		add("Kurask", leafBladed);
		add("Wyrms", stoneBoots);
		add("Drakes", stoneBoots);
		add("Hydras", stoneBoots);
		add("Fossil Island wyverns", wyvernShield);
		add("Skeletal wyverns", wyvernShield);
		for (String dragon : new String[]{"Blue dragons", "Black dragons", "Red dragons", "Green dragons", "Bronze dragons",
			"Iron dragons", "Steel dragons", "Mithril dragons", "Adamant dragons", "Rune dragons", "Lava dragons"})
		{
			add(dragon, dragonfire);
		}
	}

	private TaskRequirements()
	{
	}

	private static void add(String task, Need need)
	{
		BY_TASK.computeIfAbsent(task.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(need);
	}

	/**
	 * @return what the task needs, or an empty list
	 */
	static List<Need> forTask(String task)
	{
		if (task == null)
		{
			return Collections.emptyList();
		}
		List<Need> needs = BY_TASK.get(task.toLowerCase(Locale.ROOT));
		if (needs == null)
		{
			// Task names vary a little ("Zygomites", "Mutated Zygomites").
			for (Map.Entry<String, List<Need>> e : BY_TASK.entrySet())
			{
				if (SlayerTaskMatcher.matches(task, e.getKey()) || SlayerTaskMatcher.matches(e.getKey(), task))
				{
					return Collections.unmodifiableList(e.getValue());
				}
			}
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(needs);
	}
}
