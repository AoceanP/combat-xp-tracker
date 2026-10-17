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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

/**
 * 2.0.0, round 1: kill time history, live kill timer, dry calculator, Combat Achievements.
 */
public class Release200BossesTest
{
	private static Map<String, Map<String, Double>> bundledRates()
	{
		return new Gson().fromJson(new InputStreamReader(
				CombatXpTrackerPlugin.class.getResourceAsStream("unique_rates.json"), StandardCharsets.UTF_8),
			new TypeToken<Map<String, Map<String, Double>>>()
			{
			}.getType());
	}

	// ---- Dry calculator ------------------------------------------------------------------------

	@Test
	public void bundledRatesHaveTheBigUniques()
	{
		Map<String, Map<String, Double>> rates = bundledRates();
		assertEquals(50.0, rates.get("Vorkath").get("Vorkath's head"), 0.01);
		assertEquals(5000.0, rates.get("Vorkath").get("Draconic visage"), 0.01);
		// Two rolls a kill at 1/1,024 each.
		assertEquals(512.3, rates.get("Zulrah").get("Tanzanite fang"), 0.5);
		assertEquals(508.0, rates.get("General Graardor").get("Bandos hilt"), 0.01);
	}

	@Test
	public void driestFirstAndCaseDoesNotMatter()
	{
		DryCalculator calc = new DryCalculator(bundledRates());
		// The collection log writes "Pet snakeling", the wiki "Pet Snakeling".
		List<DryCalculator.Dry> dry = calc.dry("zulrah", Arrays.asList("Pet snakeling", "Tanzanite fang", "Not an item"), 1_024);
		assertEquals(2, dry.size());
		assertEquals("Tanzanite fang", dry.get(0).getItem());
		assertEquals(2.0, dry.get(0).getRatio(), 0.01);
		assertTrue(dry.get(0).getChanceByNow() > 0.86 && dry.get(0).getChanceByNow() < 0.87);
		assertEquals("Driest: Tanzanite fang, 2.0x the rate", DryCalculator.label(dry.get(0)));
		assertTrue(DryCalculator.line(dry.get(1)), DryCalculator.line(dry.get(1)).startsWith("Pet snakeling: 1/4,000, 0.3x"));
	}

	@Test
	public void noRatesNoDry()
	{
		DryCalculator calc = new DryCalculator(bundledRates());
		assertTrue(calc.dry("Goblin", Collections.singletonList("Bones"), 100).isEmpty());
		assertTrue(calc.dry("Vorkath", Collections.singletonList("Vorkath's head"), 0).isEmpty());
		assertTrue(new DryCalculator(null).dry("Vorkath", Collections.singletonList("Vorkath's head"), 100).isEmpty());
		assertTrue(calc.hasBoss("vorkath"));
		assertFalse(calc.hasBoss("Goblin"));
	}

	// ---- Kill time history ---------------------------------------------------------------------

	@Test
	public void recentKillTimesAreKept()
	{
		BossTimes times = new BossTimes();
		for (int i = 0; i < BossTimes.RECENT_KILLS + 5; i++)
		{
			times.record("Vorkath", new BossTimes.Duration(80 + i, false, 0));
		}
		List<Double> recent = times.get("Vorkath").recentOrEmpty();
		assertEquals(BossTimes.RECENT_KILLS, recent.size());
		assertEquals(85.0, recent.get(0), 0.001);
		assertEquals(104.0, recent.get(recent.size() - 1), 0.001);

		// Saves from 1.9.x have no list.
		BossTimes old = new BossTimes();
		old.importState(new Gson().fromJson("{\"vorkath\":{\"best\":72,\"last\":80}}",
			new TypeToken<Map<String, BossTimes.Times>>()
			{
			}.getType()));
		assertTrue(old.get("Vorkath").recentOrEmpty().isEmpty());
		old.record("Vorkath", new BossTimes.Duration(75, false, 0));
		assertEquals(1, old.get("Vorkath").recentOrEmpty().size());
	}

	// ---- Live kill timer -----------------------------------------------------------------------

	@Test
	public void timerRunsFromTheFirstAttack()
	{
		KillTimer timer = new KillTimer();
		timer.onAttack("Vorkath", 100);
		timer.onAttack("Vorkath", 105);
		assertTrue(timer.isRunning());
		assertEquals(6.0, timer.elapsed(110), 0.001);
		timer.onKill("Vorkath", 200);
		assertTrue(timer.isFinished());
		assertEquals(60.0, timer.elapsed(205), 0.001);
		// The game's exact time replaces the estimate.
		timer.onDuration("Vorkath", 59.4, true, 201);
		assertEquals(59.4, timer.elapsed(205), 0.001);
		assertTrue(timer.isNewBest());
		timer.onTick(200 + KillTimer.SHOW_RESULT_TICKS + 1);
		assertFalse(timer.isShowing());
	}

	@Test
	public void abandonedFightsHide()
	{
		KillTimer timer = new KillTimer();
		timer.onAttack("Zulrah", 100);
		timer.onTick(100 + KillTimer.GIVE_UP_TICKS);
		assertTrue(timer.isShowing());
		timer.onTick(101 + KillTimer.GIVE_UP_TICKS);
		assertFalse(timer.isShowing());
		// Another boss's kill doesn't stop it.
		timer.onAttack("Zulrah", 300);
		timer.onKill("Vorkath", 310);
		assertTrue(timer.isRunning());
	}

	@Test
	public void paceColours()
	{
		assertEquals(Theme.GOLD, KillTimerOverlay.paceColour(60, 72, false));
		assertEquals(java.awt.Color.WHITE, KillTimerOverlay.paceColour(60, 0, false));
		assertTrue(!Theme.GOLD.equals(KillTimerOverlay.paceColour(80, 72, false)));
		assertEquals(Theme.GOLD, KillTimerOverlay.paceColour(80, 72, true));
		assertEquals("1:05.4", KillTimerOverlay.clock(65.4));
		assertEquals("0:00.6", KillTimerOverlay.clock(0.6));
	}

	// ---- Combat Achievements -------------------------------------------------------------------

	@Test
	public void speedTaskTimes()
	{
		assertEquals(75, CombatAchievements.parseTime("Kill Vorkath in 1:15 or less."), 0.001);
		assertEquals(150.6, CombatAchievements.parseTime("Complete it in under 2:30.6"), 0.001);
		assertEquals(-1, CombatAchievements.parseTime("Kill Vorkath 50 times."), 0.001);
		assertTrue(CombatAchievements.isCompletionVarp(VarPlayerID.CA_TASK_COMPLETED_0));
		assertTrue(CombatAchievements.isCompletionVarp(VarPlayerID.CA_TASK_COMPLETED_20));
		assertFalse(CombatAchievements.isCompletionVarp(VarPlayerID.ROCKTHROWER));
	}

	@Test
	public void achievementLabels()
	{
		List<CombatAchievements.Task> tasks = Arrays.asList(
			new CombatAchievements.Task("Vorkath Veteran", "Kill Vorkath 50 times.", "Hard", false, true),
			new CombatAchievements.Task("Vorkath Speed-Runner", "Kill Vorkath in 1:15 or less.", "Elite", true, false));
		assertEquals("Combat Achievements: 1/2", CombatAchievements.label(tasks));
		String tip = CombatAchievements.tooltip(tasks, 72);
		assertTrue(tip, tip.contains("your PB 1:12 is fast enough"));
		assertTrue(tip, tip.contains("Done</font> [Hard] Vorkath Veteran"));
		assertEquals(null, CombatAchievements.label(Collections.emptyList()));
	}
}
