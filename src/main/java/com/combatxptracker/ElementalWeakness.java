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

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monsters' elemental weaknesses (OSRS Wiki, "Elemental weakness"): an elemental spell of the
 * right element gets extra accuracy and damage, one percent for each percent of weakness.
 *
 * The table comes from the OSRS Wiki's monster infoboxes (CC BY-NC-SA 3.0), bundled with the
 * plugin as elemental_weakness.json and looked up by NPC id. Nothing is looked up online.
 */
final class ElementalWeakness
{
	enum Element
	{
		AIR("Air", new Color(220, 220, 220)),
		WATER("Water", new Color(90, 160, 255)),
		EARTH("Earth", new Color(160, 200, 90)),
		FIRE("Fire", new Color(255, 120, 70));

		private final String displayName;
		private final Color colour;

		Element(String displayName, Color colour)
		{
			this.displayName = displayName;
			this.colour = colour;
		}

		String getDisplayName()
		{
			return displayName;
		}

		Color getColour()
		{
			return colour;
		}

		static Element of(char letter)
		{
			switch (letter)
			{
				case 'A':
					return AIR;
				case 'W':
					return WATER;
				case 'E':
					return EARTH;
				case 'F':
					return FIRE;
				default:
					return null;
			}
		}

		/**
		 * @return the element of a standard spellbook elemental spell, or null for any other
		 */
		static Element ofSpell(MagicMaxHit.Spell spell)
		{
			if (spell == null)
			{
				return null;
			}
			String name = spell.name();
			if (name.startsWith("WIND_"))
			{
				return AIR;
			}
			if (name.startsWith("WATER_"))
			{
				return WATER;
			}
			if (name.startsWith("EARTH_"))
			{
				return EARTH;
			}
			return name.startsWith("FIRE_") ? FIRE : null;
		}
	}

	static final class Weakness
	{
		private final Element element;
		private final int percent;

		Weakness(Element element, int percent)
		{
			this.element = element;
			this.percent = percent;
		}

		Element getElement()
		{
			return element;
		}

		int getPercent()
		{
			return percent;
		}

		/**
		 * e.g. "Fire 40%".
		 */
		String label()
		{
			return element.getDisplayName() + " " + percent + "%";
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Weakness && ((Weakness) o).element == element && ((Weakness) o).percent == percent;
		}

		@Override
		public int hashCode()
		{
			return element.hashCode() * 31 + percent;
		}
	}

	private final Map<Integer, Weakness> byNpc = new HashMap<>();

	/**
	 * @param table e.g. {"F40": [npc ids]}, as in elemental_weakness.json
	 */
	ElementalWeakness(Map<String, List<Integer>> table)
	{
		if (table == null)
		{
			return;
		}
		for (Map.Entry<String, List<Integer>> e : table.entrySet())
		{
			String key = e.getKey();
			if (key == null || key.length() < 2 || e.getValue() == null)
			{
				continue;
			}
			Element element = Element.of(key.charAt(0));
			int percent;
			try
			{
				percent = Integer.parseInt(key.substring(1));
			}
			catch (NumberFormatException ex)
			{
				continue;
			}
			if (element == null || percent <= 0)
			{
				continue;
			}
			Weakness weakness = new Weakness(element, percent);
			for (Integer id : e.getValue())
			{
				if (id != null)
				{
					byNpc.put(id, weakness);
				}
			}
		}
	}

	/**
	 * @return the NPC's weakness, or null if it has none
	 */
	Weakness of(int npcId)
	{
		return byNpc.get(npcId);
	}
}
