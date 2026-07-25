/*
 * Copyright (c) 2026, YourNameHere <https://github.com/YourNameHere>
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

import net.runelite.api.Skill;

/**
 * RuneLite fires XP gain (StatChanged) and damage (HitsplatApplied) as two independent
 * events with no built-in link between them, even when they result from the same attack.
 * This class holds the most recent hit, and answers whether a given XP gain happened
 * "close enough" in time to that hit to be shown paired together on the overlay -- e.g.
 * "Slayer +52 xp (hit: 14)" instead of two disconnected numbers.
 *
 * This is a best-effort visual pairing based on timing proximity, not a guaranteed
 * causal link -- OSRS does not expose which specific hit caused which specific XP drop,
 * so on multi-hit ticks (e.g. dual wielding, multi-target spells) the pairing may show
 * a hit that wasn't actually the one that caused that particular skill's XP gain.
 */
public class CombinedDropTracker
{
	private long lastHitTimestampMillis = -1;
	private int lastHitDamage = -1;

	public void recordHit(int damage, long nowMillis)
	{
		lastHitTimestampMillis = nowMillis;
		lastHitDamage = damage;
	}

	/**
	 * Returns the paired hit damage if a hit was recorded within windowMillis of nowMillis,
	 * or -1 if no hit is close enough in time to pair with (i.e. this was a pure skilling
	 * XP drop, like Woodcutting, with no associated combat damage).
	 */
	public int getPairedDamage(long nowMillis, int windowMillis)
	{
		if (lastHitTimestampMillis < 0)
		{
			return -1;
		}
		long delta = Math.abs(nowMillis - lastHitTimestampMillis);
		if (delta > windowMillis)
		{
			return -1;
		}
		return lastHitDamage;
	}

	/**
	 * Skills that can plausibly be paired with combat damage. Pairing a hit with, say,
	 * Woodcutting XP would be misleading (chopping doesn't deal damage), so the overlay
	 * only attempts pairing for skills damage can actually be attributed to.
	 */
	public static boolean isCombatSkill(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
			case STRENGTH:
			case DEFENCE:
			case RANGED:
			case MAGIC:
			case HITPOINTS:
			case SLAYER:
				return true;
			default:
				return false;
		}
	}

	public void reset()
	{
		lastHitTimestampMillis = -1;
		lastHitDamage = -1;
	}
}
