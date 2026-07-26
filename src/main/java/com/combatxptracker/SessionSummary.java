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

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Tracks session-wide totals for the "session summary" readout: total XP gained per
 * skill since the plugin started (or was last reset), total hits landed, and the single
 * biggest hit along with which monster it landed on, if determinable.
 *
 * "Since the plugin started" is a deliberate scope choice: this is NOT the same as total
 * account XP or lifetime stats, only what changed while this plugin has been actively
 * tracking, matching how a "session" is understood elsewhere in RuneLite (e.g. the core
 * XP Tracker's per-session gains).
 */
public class SessionSummary
{
	private final Map<Skill, Integer> xpGainedThisSession = new EnumMap<>(Skill.class);
	private int totalHitsLanded = 0;
	private int biggestHitDamage = -1;
	private String biggestHitMonsterName = null;

	public void recordXpGain(Skill skill, int xpDelta)
	{
		if (xpDelta <= 0)
		{
			return;
		}
		xpGainedThisSession.merge(skill, xpDelta, Integer::sum);
	}

	/**
	 * @param monsterName the NPC's name if the hit landed on an NPC and its name was
	 *                    available, or null if the target was a player, unnamed, or
	 *                    otherwise undeterminable. A null name is recorded as-is rather
	 *                    than guessed at, so the summary can honestly show "unknown
	 *                    target" instead of asserting a monster that wasn't confirmed.
	 */
	public void recordHit(int damage, String monsterName)
	{
		totalHitsLanded++;
		if (damage > biggestHitDamage)
		{
			biggestHitDamage = damage;
			biggestHitMonsterName = monsterName;
		}
	}

	public Map<Skill, Integer> getXpGainedThisSession()
	{
		return xpGainedThisSession;
	}

	public int getTotalXpGained()
	{
		return xpGainedThisSession.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int getTotalHitsLanded()
	{
		return totalHitsLanded;
	}

	public int getBiggestHitDamage()
	{
		return biggestHitDamage;
	}

	/**
	 * @return the monster name associated with the biggest hit, or null if the target
	 * wasn't a named NPC (e.g. was a player, or the hit predates this tracker resetting).
	 */
	public String getBiggestHitMonsterName()
	{
		return biggestHitMonsterName;
	}

	public void reset()
	{
		xpGainedThisSession.clear();
		totalHitsLanded = 0;
		biggestHitDamage = -1;
		biggestHitMonsterName = null;
	}
}
