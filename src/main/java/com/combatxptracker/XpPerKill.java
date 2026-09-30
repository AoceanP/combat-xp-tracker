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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * The XP each combat skill actually gets per kill of each monster, measured from the XP
 * drops between kills. That covers what the hitpoints formula in {@link KillXp} can't:
 * magic, monsters with a defence XP bonus, and multi-target spells.
 *
 * XP drops since the last kill are pooled and credited to the next kill. Recent kills
 * count more than old ones, so a new attack style or gear setup shows up quickly.
 *
 * Synchronized: written on the client thread, read by the panel.
 */
final class XpPerKill
{
	static final Set<Skill> COMBAT_SKILLS = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.SLAYER);

	/**
	 * How much each older kill still counts. 0.9 means the last ~10 kills matter most.
	 */
	private static final double DECAY = 0.9;

	private static final class Stats
	{
		final Map<Skill, Double> xp = new EnumMap<>(Skill.class);
		double weight;
		int kills;
	}

	private final Map<Skill, Integer> pending = new EnumMap<>(Skill.class);
	private final Map<String, Stats> byMonster = new HashMap<>();

	synchronized void recordXp(Skill skill, int delta)
	{
		if (delta > 0 && COMBAT_SKILLS.contains(skill))
		{
			pending.merge(skill, delta, Integer::sum);
		}
	}

	/**
	 * Drops pooled XP that isn't from a kill, e.g. after switching to another monster.
	 */
	synchronized void clearPending()
	{
		pending.clear();
	}

	synchronized void recordKill(String monster)
	{
		if (monster == null)
		{
			return;
		}
		Stats stats = byMonster.computeIfAbsent(key(monster), k -> new Stats());
		// Older kills only fade when XP comes in. Several kills in one tick (a burst spell)
		// share one XP batch, so they must weigh the same or the average skews low.
		double decay = pending.isEmpty() ? 1 : DECAY;
		for (Skill skill : COMBAT_SKILLS)
		{
			double old = stats.xp.getOrDefault(skill, 0.0);
			stats.xp.put(skill, old * decay + pending.getOrDefault(skill, 0));
		}
		stats.weight = stats.weight * decay + 1;
		stats.kills++;
		pending.clear();
	}

	/**
	 * @return average XP per kill, 0 if the skill got none, or -1 with no kills measured
	 */
	synchronized double average(String monster, Skill skill)
	{
		Stats stats = monster == null ? null : byMonster.get(key(monster));
		if (stats == null || stats.kills == 0)
		{
			return -1;
		}
		return stats.xp.getOrDefault(skill, 0.0) / stats.weight;
	}

	synchronized int kills(String monster)
	{
		Stats stats = monster == null ? null : byMonster.get(key(monster));
		return stats == null ? 0 : stats.kills;
	}

	synchronized void clear()
	{
		pending.clear();
		byMonster.clear();
	}

	private static String key(String monster)
	{
		return monster.toLowerCase(Locale.ROOT);
	}
}
