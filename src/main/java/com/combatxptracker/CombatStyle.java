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
import java.util.Locale;
import net.runelite.api.Skill;

/**
 * The three combat styles a hit can come from.
 */
public enum CombatStyle
{
	MELEE("Melee", new Color(232, 93, 74)),
	RANGED("Ranged", new Color(111, 196, 84)),
	MAGIC("Magic", new Color(96, 156, 245));

	private final String displayName;
	private final Color color;

	CombatStyle(String displayName, Color color)
	{
		this.displayName = displayName;
		this.color = color;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Color getColor()
	{
		return color;
	}

	/**
	 * Which style an XP drop in this skill implies, or null if it says nothing about it.
	 * Defence and Hitpoints are ignored because every style can train them.
	 */
	public static CombatStyle fromXpSkill(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
			case STRENGTH:
				return MELEE;
			case RANGED:
				return RANGED;
			case MAGIC:
				return MAGIC;
			default:
				return null;
		}
	}

	/**
	 * The player's selected attack style, as named by the game's attack style structs
	 * ("Accurate", "Aggressive", "Controlled", "Defensive", "Ranging", "Longrange",
	 * "Casting", "Defensive Casting").
	 */
	public static final class AttackStyle
	{
		private final String name;
		private final CombatStyle style;
		private final int meleeStrengthBonus;

		private AttackStyle(String name, CombatStyle style, int meleeStrengthBonus)
		{
			this.name = name;
			this.style = style;
			this.meleeStrengthBonus = meleeStrengthBonus;
		}

		/**
		 * @return the style, or null for "Other"/unknown names
		 */
		public static AttackStyle fromName(String rawName)
		{
			if (rawName == null)
			{
				return null;
			}
			String n = rawName.trim().toLowerCase(Locale.ROOT);
			switch (n)
			{
				case "aggressive":
					return new AttackStyle("Aggressive", MELEE, 3);
				case "controlled":
					return new AttackStyle("Controlled", MELEE, 1);
				case "accurate":
					return new AttackStyle("Accurate", MELEE, 0);
				case "defensive":
					return new AttackStyle("Defensive", MELEE, 0);
				case "ranging":
					return new AttackStyle("Ranging", RANGED, 0);
				case "longrange":
					return new AttackStyle("Longrange", RANGED, 0);
				case "casting":
					return new AttackStyle("Casting", MAGIC, 0);
				case "defensive casting":
				case "defensive_casting":
					return new AttackStyle("Defensive casting", MAGIC, 0);
				default:
					return null;
			}
		}

		public String getName()
		{
			return name;
		}

		public CombatStyle getStyle()
		{
			return style;
		}

		/**
		 * The style bonus added to effective Strength: Aggressive +3, Controlled +1,
		 * everything else +0 (OSRS Wiki, "Maximum melee hit").
		 */
		public int getMeleeStrengthBonus()
		{
			return meleeStrengthBonus;
		}
	}
}
