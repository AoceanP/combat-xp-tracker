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

import java.util.Objects;

/**
 * The monster the player is fighting right now and its health, read once a tick for the
 * target card. Immutable, so the panel can read it from the Swing thread.
 */
final class TargetInfo
{
	private final String name;
	private final int hitpoints;
	private final int maxHitpoints;
	private final ElementalWeakness.Weakness weakness;
	private final boolean spellMatches;

	/**
	 * @param hitpoints    its hitpoints left, or -1 if unknown
	 * @param maxHitpoints its full hitpoints, or -1 if unknown
	 */
	TargetInfo(String name, int hitpoints, int maxHitpoints)
	{
		this(name, hitpoints, maxHitpoints, null, false);
	}

	/**
	 * @param weakness     its elemental weakness, or null
	 * @param spellMatches whether the player's autocast spell is of that element
	 */
	TargetInfo(String name, int hitpoints, int maxHitpoints, ElementalWeakness.Weakness weakness, boolean spellMatches)
	{
		this.name = name;
		this.hitpoints = hitpoints;
		this.maxHitpoints = maxHitpoints;
		this.weakness = weakness;
		this.spellMatches = spellMatches;
	}

	ElementalWeakness.Weakness getWeakness()
	{
		return weakness;
	}

	boolean isSpellMatching()
	{
		return spellMatches;
	}

	/**
	 * Hitpoints from the health bar, which only shows a fraction of them (the same sums as
	 * RuneLite's Opponent Information).
	 *
	 * @param ratio the bar's filled part, or -1 when it isn't showing
	 * @param scale the bar's size
	 * @return hitpoints left, or -1 if unknown
	 */
	static int hitpoints(int ratio, int scale, int maxHitpoints)
	{
		if (ratio < 0 || scale <= 0 || maxHitpoints <= 0)
		{
			return -1;
		}
		if (ratio == 0)
		{
			return 0;
		}
		if (scale == 1)
		{
			return maxHitpoints;
		}
		int min = ratio > 1 ? (maxHitpoints * (ratio - 1) + scale - 2) / (scale - 1) : 1;
		int max = Math.min(maxHitpoints, (maxHitpoints * ratio - 1) / (scale - 1));
		return (min + max + 1) / 2;
	}

	String getName()
	{
		return name;
	}

	int getHitpoints()
	{
		return hitpoints;
	}

	int getMaxHitpoints()
	{
		return maxHitpoints;
	}

	/**
	 * @return how full its health bar is, 0 to 1, or -1 if unknown
	 */
	double getHealthShare()
	{
		return hitpoints < 0 || maxHitpoints <= 0 ? -1 : Math.min(1, hitpoints / (double) maxHitpoints);
	}

	/**
	 * Hits left to kill it at an average damage per hit (0s included).
	 *
	 * @return hits, or -1 if unknown
	 */
	int hitsLeft(double averagePerHit)
	{
		return hitpoints <= 0 || averagePerHit <= 0 ? -1 : (int) Math.ceil(hitpoints / averagePerHit);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof TargetInfo))
		{
			return false;
		}
		TargetInfo t = (TargetInfo) o;
		return hitpoints == t.hitpoints && maxHitpoints == t.maxHitpoints && Objects.equals(name, t.name)
			&& Objects.equals(weakness, t.weakness) && spellMatches == t.spellMatches;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, hitpoints, maxHitpoints, weakness, spellMatches);
	}
}
