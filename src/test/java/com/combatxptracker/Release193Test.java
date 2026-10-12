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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

/**
 * 1.9.3: best master for you, luck meter.
 */
public class Release193Test
{
	// ---- I5: luck --------------------------------------------------------------------------

	@Test
	public void averageLuckIsAboutHalfway()
	{
		HitLuck luck = new HitLuck();
		// Every hit from 1 to 40, twice: exactly what's expected.
		for (int round = 0; round < 2; round++)
		{
			for (int hit = 1; hit <= 40; hit++)
			{
				luck.record(hit, 40);
			}
		}
		HitLuck.Result r = luck.result();
		assertEquals(80, r.getHits());
		assertEquals(0.5, r.getPercentile(), 1e-6);
		assertEquals(2, r.getMaxHits());
		assertEquals(2.0, r.getExpectedMaxHits(), 1e-9);
	}

	@Test
	public void bigHitsAreLucky()
	{
		HitLuck luck = new HitLuck();
		for (int i = 0; i < 30; i++)
		{
			luck.record(35, 40);
		}
		HitLuck.Result r = luck.result();
		assertTrue(r.getPercentile() > 0.99);
		assertEquals("Top 1%", r.label());
		assertEquals(35 / 40.0, r.getAverageShare(), 1e-9);

		HitLuck unlucky = new HitLuck();
		for (int i = 0; i < 30; i++)
		{
			unlucky.record(8, 40);
		}
		assertTrue(unlucky.result().label().startsWith("Bottom"));
	}

	@Test
	public void unknownHitsAreSkippedAndFewHitsSayNothing()
	{
		HitLuck luck = new HitLuck();
		assertFalse("over the max: a bonus wasn't known", luck.record(50, 40));
		assertFalse(luck.record(0, 40));
		assertFalse(luck.record(10, -1));
		for (int i = 0; i < HitLuck.MIN_HITS - 1; i++)
		{
			luck.record(20, 40);
		}
		assertNull(luck.result());
		luck.record(20, 40);
		assertEquals(HitLuck.MIN_HITS, luck.result().getHits());
		luck.reset();
		assertNull(luck.result());
	}

	@Test
	public void randomHitsLookAverage()
	{
		Random random = new Random(7);
		HitLuck luck = new HitLuck();
		for (int i = 0; i < 5000; i++)
		{
			int max = 30 + random.nextInt(30);
			luck.record(1 + random.nextInt(max), max);
		}
		double p = luck.result().getPercentile();
		assertTrue(String.valueOf(p), p > 0.01 && p < 0.99);
	}

	@Test
	public void normalCdf()
	{
		assertEquals(0.5, HitLuck.normalCdf(0), 1e-7);
		assertEquals(0.841345, HitLuck.normalCdf(1), 1e-5);
		assertEquals(0.022750, HitLuck.normalCdf(-2), 1e-5);
	}

	@Test
	public void luckLineForTheSummary()
	{
		HitLuck luck = new HitLuck();
		for (int i = 0; i < 30; i++)
		{
			luck.record(i % 2 == 0 ? 40 : 30, 40);
		}
		String line = SessionSummaryText.luckLine(luck.result());
		assertTrue(line, line.startsWith("Luck: top 1% (average hit 88% of max, 15 max hits, about 0.8 expected)"));
	}

	// ---- I1: best master -----------------------------------------------------------------------

	private static SlayerTaskRecord record(String task, int kills, int minutes, int xp, long gp)
	{
		SlayerTaskRecord r = new SlayerTaskRecord();
		r.task = task;
		r.kills = kills;
		r.activeMillis = minutes * 60_000L;
		r.slayerXp = xp;
		r.lootGe = gp;
		return r;
	}

	private static List<TaskStats> history()
	{
		return TaskStats.group(Arrays.asList(
			// Fast and rich: 150 kills in 30 min, 30k xp.
			record("Gargoyles", 150, 30, 30_000, 3_000_000),
			// Slow: 150 kills in 60 min, 15k xp.
			record("Spiritual creatures", 150, 60, 15_000, 300_000),
			record("Abyssal demons", 150, 45, 22_500, 1_500_000)), LootPrice.GRAND_EXCHANGE);
	}

	private static TaskOdds odds(String master, Object... taskWeights)
	{
		List<TaskOdds.Option> options = new java.util.ArrayList<>();
		for (int i = 0; i < taskWeights.length; i += 2)
		{
			options.add(new TaskOdds.Option((String) taskWeights[i], (Integer) taskWeights[i + 1], 140, 160, null));
		}
		return TaskOdds.compute(master, options, Collections.emptySet(), true);
	}

	@Test
	public void masterWithMoreFastTasksWins()
	{
		Map<SlayerPoints.Master, TaskOdds> all = new EnumMap<>(SlayerPoints.Master.class);
		all.put(SlayerPoints.Master.DURADEL, odds("Duradel", "Gargoyles", 3, "Spiritual creatures", 1));
		all.put(SlayerPoints.Master.NIEVE, odds("Nieve", "Gargoyles", 1, "Spiritual creatures", 3));
		List<MasterAdvice.Entry> ranked = MasterAdvice.compute(all, history(), m -> false, 126, 99, MasterAdvice.Goal.XP);
		assertSame(SlayerPoints.Master.DURADEL, ranked.get(0).getMaster());
		// Duradel: per task 0.75 x (30k in 0.5h) + 0.25 x (15k in 1h) = 26,250 xp in 0.625h.
		assertEquals(42_000, ranked.get(0).getXpPerHour(), 1);
		assertEquals(0.625, ranked.get(0).getHoursPerTask(), 1e-9);
		assertEquals(1.0, ranked.get(0).getCoverage(), 1e-9);
		assertTrue(ranked.get(0).isRanked());
		assertEquals(15, ranked.get(0).getPointsPerTask());
		assertEquals(24, ranked.get(0).getPointsPerHour(), 1e-9);
	}

	@Test
	public void unknownTasksUseYourAverage()
	{
		Map<SlayerPoints.Master, TaskOdds> all = new EnumMap<>(SlayerPoints.Master.class);
		all.put(SlayerPoints.Master.KONAR, odds("Konar", "Gargoyles", 1, "Hydras", 1));
		MasterAdvice.Entry konar = MasterAdvice.compute(all, history(), m -> m == SlayerPoints.Master.KONAR,
			126, 99, MasterAdvice.Goal.POINTS).get(0);
		assertEquals(0.5, konar.getCoverage(), 1e-9);
		// Elite Kourend diary: 20 points.
		assertEquals(20, konar.getPointsPerTask());
		// Hydras unknown: your average task is 45 minutes at 30k xp/hr.
		assertEquals((0.5 * 30_000 + 0.5 * 22_500) / (0.5 * 0.5 + 0.5 * 0.75), konar.getXpPerHour(), 1);
	}

	@Test
	public void tooLittleDataOrLevelsArentRanked()
	{
		Map<SlayerPoints.Master, TaskOdds> all = new EnumMap<>(SlayerPoints.Master.class);
		all.put(SlayerPoints.Master.DURADEL, odds("Duradel", "Gargoyles", 1, "Hydras", 9));
		all.put(SlayerPoints.Master.NIEVE, odds("Nieve", "Gargoyles", 3, "Spiritual creatures", 1));
		List<MasterAdvice.Entry> ranked = MasterAdvice.compute(all, history(), m -> false, 126, 99, MasterAdvice.Goal.XP);
		// Only 10% of Duradel's tasks are known: not ranked, listed after Nieve.
		assertSame(SlayerPoints.Master.NIEVE, ranked.get(0).getMaster());
		assertFalse(ranked.get(1).isRanked());

		List<MasterAdvice.Entry> low = MasterAdvice.compute(all, history(), m -> false, 90, 99, MasterAdvice.Goal.XP);
		MasterAdvice.Entry duradel = low.get(1);
		assertEquals("Needs 100 combat", duradel.getUnavailable());
		assertEquals("Needs 50 Slayer", MasterAdvice.requirement(SlayerPoints.Master.DURADEL, 110, 40));
		assertNull(MasterAdvice.requirement(SlayerPoints.Master.TURAEL, 3, 1));

		// Without enough logged tasks nothing is ranked.
		List<TaskStats> one = TaskStats.group(Collections.singletonList(record("Gargoyles", 150, 30, 30_000, 0)),
			LootPrice.GRAND_EXCHANGE);
		for (MasterAdvice.Entry e : MasterAdvice.compute(all, one, m -> false, 126, 99, MasterAdvice.Goal.XP))
		{
			assertFalse(e.isRanked());
		}
	}

	@Test
	public void gpGoalRanksByGp()
	{
		Map<SlayerPoints.Master, TaskOdds> all = new EnumMap<>(SlayerPoints.Master.class);
		all.put(SlayerPoints.Master.DURADEL, odds("Duradel", "Abyssal demons", 1, "Spiritual creatures", 1));
		all.put(SlayerPoints.Master.NIEVE, odds("Nieve", "Gargoyles", 1, "Spiritual creatures", 3));
		List<MasterAdvice.Entry> byGp = MasterAdvice.compute(all, history(), m -> false, 126, 99, MasterAdvice.Goal.GP);
		assertTrue(byGp.get(0).getGpPerHour() >= byGp.get(1).getGpPerHour());
	}
}
