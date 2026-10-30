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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 2.3: luck vs average from drop rates, kill count goals, pinned monsters kept in This session,
 * and PBs, drop luck and kill goals in the copied summary.
 */
public class Release230Test
{
	private static DryCalculator vorkath()
	{
		Map<String, Map<String, Double>> table = new HashMap<>();
		Map<String, Double> items = new HashMap<>();
		items.put("Vorkath's head", 50.0);
		items.put("Draconic visage", 5000.0);
		table.put("Vorkath", items);
		return new DryCalculator(table);
	}

	// ---- Luck vs average ------------------------------------------------------------------------

	@Test
	public void luckComparesUniquesWithTheExpectedNumber()
	{
		Map<String, Long> loot = new HashMap<>();
		loot.put("vorkath's head", 6L);
		DryCalculator.Luck luck = vorkath().luck("Vorkath", loot, 250);
		// 250/50 + 250/5000 = 5.05 expected, 6 had.
		assertEquals(5.05, luck.getExpected(), 1e-9);
		assertEquals(6, luck.getGot(), 1e-9);
		assertEquals((6 / 5.05 - 1) * 100, luck.percent(), 1e-9);
		assertTrue(luck.getLines().get(0).startsWith("Vorkath's head: 6"));
	}

	@Test
	public void unluckyIsNegative()
	{
		DryCalculator.Luck luck = vorkath().luck("Vorkath", new HashMap<>(), 250);
		assertEquals(-100, luck.percent(), 1e-9);
	}

	@Test
	public void noLuckUntilEnoughUniquesWereExpected()
	{
		// 20 kills: 0.4 expected, below the minimum.
		assertNull(vorkath().luck("Vorkath", new HashMap<>(), 20));
		assertNull(vorkath().luck("Zulrah", new HashMap<>(), 500));
		assertNull(vorkath().luck("Vorkath", new HashMap<>(), 0));
	}

	// ---- Kill count goals -----------------------------------------------------------------------

	@Test
	public void killGoalsRoundTrip()
	{
		Map<String, Integer> goals = new LinkedHashMap<>();
		goals.put("Vorkath", 500);
		goals.put("Kree'arra", 1000);
		String saved = KillGoals.serialize(goals);
		assertEquals("Vorkath=500;Kree'arra=1000", saved);
		assertEquals(goals, KillGoals.parse(saved));
	}

	@Test
	public void brokenGoalTextIsSkippedNotFatal()
	{
		Map<String, Integer> goals = KillGoals.parse("Vorkath=500;nonsense;Zulrah=abc;=7;Cerberus=-4;Giant Mole=50");
		assertEquals(2, goals.size());
		assertEquals(Integer.valueOf(500), goals.get("Vorkath"));
		assertEquals(Integer.valueOf(50), goals.get("Giant Mole"));
		assertTrue(KillGoals.parse(null).isEmpty());
	}

	@Test
	public void suggestedGoalsAreRoundAndAhead()
	{
		assertEquals(100, KillGoals.suggestTarget(0));
		assertEquals(250, KillGoals.suggestTarget(100));
		assertEquals(500, KillGoals.suggestTarget(400));
		assertEquals(1000, KillGoals.suggestTarget(500));
		assertEquals(2500, KillGoals.suggestTarget(1200));
		assertEquals(5000, KillGoals.suggestTarget(2500));
	}

	@Test
	public void timeLeftFromKillRate()
	{
		assertEquals(7.0, KillGoals.hoursLeft(220, 500, 40), 1e-9);
		assertEquals(-1, KillGoals.hoursLeft(500, 500, 40), 1e-9);
		assertEquals(-1, KillGoals.hoursLeft(10, 500, 0), 1e-9);
	}

	// ---- Pinned monsters in This session --------------------------------------------------------

	@Test
	public void pinnedMonstersKeepTheirPlaceInThisSession()
	{
		MonsterTracker allTime = new MonsterTracker();
		allTime.recordKill("Vorkath", 1000);
		allTime.recordKill("Zulrah", 1000);
		MonsterTracker session = new MonsterTracker();
		session.recordKill("Zulrah", 2000);
		Set<String> pinned = new LinkedHashSet<>(Arrays.asList("vorkath", "unknown boss"));
		List<MonsterTracker.Snapshot> list = MonsterTracker.withPinned(session.snapshot(), pinned, allTime);
		assertEquals("Vorkath added with 0 kills; a never-tracked name isn't", 2, list.size());
		List<MonsterTracker.Snapshot> view = MonsterTracker.view(list, MonsterSort.KILLS, Collections.emptySet(), pinned);
		assertEquals("Vorkath", view.get(0).getName());
		assertEquals(0, view.get(0).getKills());
		assertEquals(1, session.snapshot().size());
	}

	// ---- Copied summary -------------------------------------------------------------------------

	@Test
	public void summaryShowsPbLuckAndKillGoals()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Vorkath", 1000);
		Map<Skill, SkillProgress> skills = new EnumMap<>(Skill.class);
		String text = SessionSummaryText.build(skills, new HitStats(), t.snapshot(), XpRateMode.RECENT, null, -1, -1,
			s -> "PB 1:12, drop luck +18% vs average", Collections.singletonList("Vorkath: 212 / 500 kills"));
		assertTrue(text, text.contains("Vorkath - 1 kill, PB 1:12, drop luck +18% vs average"));
		assertTrue(text, text.contains("Goals:\n  Vorkath: 212 / 500 kills"));
	}
}
