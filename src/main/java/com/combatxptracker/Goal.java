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

import java.util.Locale;
import java.util.Objects;
import net.runelite.api.Experience;

/**
 * An XP target for one skill: either a level (2-126, so virtual levels past 99 work)
 * or a raw XP amount (up to the 200M cap).
 *
 * Internally every goal is just a target XP; the type only controls how it's shown.
 */
public final class Goal
{
	public static final int MIN_LEVEL = 2;
	public static final int MAX_LEVEL = Experience.MAX_VIRT_LEVEL;
	public static final int MAX_XP = Experience.MAX_SKILL_XP;

	private static final String XP_PREFIX = "xp:";

	public enum Type
	{
		LEVEL,
		XP
	}

	private final Type type;
	private final int level;
	private final int targetXp;

	private Goal(Type type, int level, int targetXp)
	{
		this.type = type;
		this.level = level;
		this.targetXp = targetXp;
	}

	public static Goal ofLevel(int level)
	{
		if (level < MIN_LEVEL || level > MAX_LEVEL)
		{
			throw new IllegalArgumentException("Level must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
		}
		return new Goal(Type.LEVEL, level, Experience.getXpForLevel(level));
	}

	public static Goal ofXp(int xp)
	{
		if (xp < 1 || xp > MAX_XP)
		{
			throw new IllegalArgumentException("XP must be between 1 and 200M");
		}
		return new Goal(Type.XP, Experience.getLevelForXp(xp), xp);
	}

	/**
	 * Parses what a player types into the goal box.
	 *
	 * <ul>
	 *   <li>A whole number up to 126 is a level: {@code 99}, {@code 126}, {@code lvl 90}</li>
	 *   <li>A larger number is an XP amount: {@code 13034431}, {@code 13,034,431}</li>
	 *   <li>k/m suffixes are XP amounts: {@code 500k}, {@code 13.03m}, {@code 200m}</li>
	 * </ul>
	 *
	 * @throws IllegalArgumentException with a player-readable message if it isn't valid
	 */
	public static Goal parse(String input)
	{
		if (input == null)
		{
			throw new IllegalArgumentException("Enter a level or an XP amount.");
		}

		String s = input.trim().toLowerCase(Locale.ROOT)
			.replace(",", "")
			.replace("_", "")
			.replace(" ", "");
		if (s.startsWith("level"))
		{
			s = s.substring("level".length());
		}
		else if (s.startsWith("lvl"))
		{
			s = s.substring("lvl".length());
		}
		if (s.endsWith("xp"))
		{
			s = s.substring(0, s.length() - 2);
		}
		if (s.isEmpty())
		{
			throw new IllegalArgumentException("Enter a level or an XP amount.");
		}

		long multiplier = 1;
		char last = s.charAt(s.length() - 1);
		if (last == 'k' || last == 'm')
		{
			multiplier = last == 'k' ? 1_000L : 1_000_000L;
			s = s.substring(0, s.length() - 1);
		}

		double value;
		try
		{
			value = Double.parseDouble(s);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("'" + input.trim() + "' isn't a level or XP amount.");
		}

		if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0)
		{
			throw new IllegalArgumentException("The goal must be above zero.");
		}

		if (multiplier == 1 && value != Math.floor(value))
		{
			throw new IllegalArgumentException("Levels must be whole numbers. For XP, add k or m (e.g. 13.03m).");
		}

		double total = value * multiplier;
		if (multiplier == 1 && total <= MAX_LEVEL)
		{
			int level = (int) total;
			if (level < MIN_LEVEL)
			{
				throw new IllegalArgumentException("Level goals start at " + MIN_LEVEL + ".");
			}
			return ofLevel(level);
		}

		if (total > MAX_XP)
		{
			throw new IllegalArgumentException("XP can't go above 200M.");
		}
		return ofXp((int) Math.round(total));
	}

	/**
	 * Stored form. Level goals are saved as the bare level number, the same format
	 * earlier versions used, so goals saved by 1.3.x keep working after an update.
	 */
	public String serialize()
	{
		return type == Type.LEVEL ? String.valueOf(level) : XP_PREFIX + targetXp;
	}

	/**
	 * @return the goal, or null if the stored value is missing or corrupt
	 */
	public static Goal deserialize(String stored)
	{
		if (stored == null || stored.isEmpty())
		{
			return null;
		}
		try
		{
			if (stored.startsWith(XP_PREFIX))
			{
				return ofXp(Integer.parseInt(stored.substring(XP_PREFIX.length())));
			}
			int level = Integer.parseInt(stored);
			return ofLevel(Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level)));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	public Type getType()
	{
		return type;
	}

	public int getTargetXp()
	{
		return targetXp;
	}

	/**
	 * The (virtual) level this goal lands on. For XP goals that's the level the target
	 * XP falls inside, e.g. 200M is level 126.
	 */
	public int getTargetLevel()
	{
		return level;
	}

	/**
	 * Short label for the UI: "99" for level goals, "200M" for XP goals.
	 */
	public String getShortLabel()
	{
		return type == Type.LEVEL ? String.valueOf(level) : Formatting.compactXp(targetXp);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Goal))
		{
			return false;
		}
		Goal other = (Goal) o;
		return type == other.type && targetXp == other.targetXp;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(type, targetXp);
	}

	@Override
	public String toString()
	{
		return type == Type.LEVEL ? "Level " + level : Formatting.compactXp(targetXp) + " xp";
	}
}
