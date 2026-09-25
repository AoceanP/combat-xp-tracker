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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * Activity time, whole-session XP/hr, and the copied session summary.
 */
public class SessionFeaturesTest
{
	private static final long MINUTE = 60_000L;

	@Test
	public void activityTimerSkipsBreaks()
	{
		ActivityTimer timer = new ActivityTimer();
		timer.mark(0);
		timer.mark(2 * MINUTE);
		// A 10-minute gap is a break and isn't counted.
		timer.mark(12 * MINUTE);
		timer.mark(15 * MINUTE);
		assertEquals(5 * MINUTE, timer.getActiveMillis());
		// Exactly the 5-minute limit still counts.
		timer.mark(20 * MINUTE);
		assertEquals(10 * MINUTE, timer.getActiveMillis());
	}

	@Test
	public void perHourNeedsAMinute()
	{
		assertEquals(-1, ActivityTimer.perHour(10, 59_999), 0.0);
		assertEquals(600, ActivityTimer.perHour(10, MINUTE), 1e-9);
	}

	@Test
	public void wholeSessionXpPerHourIgnoresBreaks()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(0, 0);
		// 1,000 xp every minute for 10 minutes...
		for (int i = 1; i <= 10; i++)
		{
			p.recordXp(i * 1_000, i * MINUTE, 300);
		}
		// ...an hour's break, then 1,000 xp a minute after coming back.
		p.recordXp(11_000, 70 * MINUTE, 300);
		p.recordXp(12_000, 71 * MINUTE, 300);

		// 12,000 xp over 10 minutes of training (the first drop only starts the clock,
		// and the break isn't counted): 72,000 xp/hr
		assertEquals(10 * 1_000 + 2_000, p.getSessionXpGained());
		assertEquals(72_000, p.getSessionXpPerHour());
		assertEquals(72_000, p.getXpPerHour(XpRateMode.SESSION));
		// The recent-window rate only sees the last minute: 60,000 xp/hr
		assertEquals(60_000, p.getXpPerHour(XpRateMode.RECENT));
	}

	@Test
	public void timeToGoalUsesTheChosenRate()
	{
		SkillProgress p = new SkillProgress();
		int start = Experience.getXpForLevel(98);
		p.resetBaseline(start, 0);
		p.setGoal(Goal.ofLevel(99), start);
		for (int i = 1; i <= 60; i++)
		{
			p.recordXp(start + i * 1_000, i * MINUTE, 30);
		}
		int sessionRate = p.getXpPerHour(XpRateMode.SESSION);
		assertTrue(sessionRate > 0);
		assertEquals(p.getXpRemainingToGoal() / (double) sessionRate, p.getEstimatedHoursToGoal(XpRateMode.SESSION), 1e-9);
	}

	@Test
	public void summaryListsXpCombatMonstersAndGoals()
	{
		Map<Skill, SkillProgress> skills = new EnumMap<>(Skill.class);
		SkillProgress attack = new SkillProgress();
		attack.resetBaseline(Experience.getXpForLevel(80), 0);
		attack.setGoal(Goal.ofLevel(99), Experience.getXpForLevel(80));
		attack.recordXp(Experience.getXpForLevel(80) + 50_000, MINUTE, 30);
		skills.put(Skill.ATTACK, attack);
		skills.put(Skill.COOKING, new SkillProgress());

		HitStats hits = new HitStats();
		hits.recordHit(40);
		hits.recordHit(20);

		MonsterTracker monsters = new MonsterTracker();
		monsters.recordHit("Abyssal demon", 45, CombatStyle.MELEE, 0);
		monsters.recordKill("Abyssal demon", 0);
		monsters.recordLoot("Abyssal demon", Collections.singletonList(
			new MonsterTracker.Drop(4151, "Abyssal whip", 1, 1_500_000)), 0);

		String text = SessionSummaryText.build(skills, hits, monsters.snapshot(), XpRateMode.RECENT);
		assertTrue(text, text.startsWith("Combat & XP Tracker - session summary"));
		assertTrue(text, text.contains("XP gained: +50,000"));
		assertTrue(text, text.contains("Attack +50,000"));
		assertFalse(text, text.contains("Cooking"));
		assertTrue(text, text.contains("Hits: 2 | Average: 30.0 | Biggest: 40"));
		assertTrue(text, text.contains("Kills: 1 | Loot: 1.5M gp"));
		assertTrue(text, text.contains("Abyssal demon - 1 kill, 1.5M gp, biggest hit 45 (Melee)"));
		assertTrue(text, text.contains("Attack: "));
		assertTrue(text, text.contains("to Level 99"));
	}

	@Test
	public void summaryOnlyListsTheTopMonsters()
	{
		MonsterTracker monsters = new MonsterTracker();
		for (int i = 0; i < 8; i++)
		{
			monsters.recordKill("Monster " + i, i);
		}
		String text = SessionSummaryText.build(new EnumMap<>(Skill.class), new HitStats(), monsters.snapshot(), XpRateMode.RECENT);
		assertTrue(text, text.contains("...and 3 more"));
	}
}
