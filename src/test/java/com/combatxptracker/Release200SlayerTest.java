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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * 2.0.0, round 2: prep check, task forecast, is cannon worth it.
 */
public class Release200SlayerTest
{
	private static SlayerTaskRecord run(String task, int kills, int minutes, int xp, long loot, long supplies, int balls)
	{
		SlayerTaskRecord r = new SlayerTaskRecord();
		r.task = task;
		r.kills = kills;
		r.activeMillis = minutes * 60_000L;
		r.slayerXp = xp;
		r.lootGe = loot;
		r.lootHa = loot / 2;
		r.supplies = supplies;
		r.cannonballs = balls;
		return r;
	}

	@Test
	public void forecastScalesYourPastRuns()
	{
		List<SlayerTaskRecord> log = Arrays.asList(
			run("Abyssal demons", 100, 30, 15_000, 800_000, 100_000, 0),
			run("Gargoyles", 150, 45, 30_000, 3_000_000, 50_000, 0),
			run("Abyssal demons", 200, 60, 30_000, 1_600_000, 200_000, 0));
		TaskForecast f = TaskForecast.of(log, "abyssal demons", 180, LootPrice.GRAND_EXCHANGE);
		assertEquals(2, f.getRuns());
		// 300 kills in 90 minutes: 180 kills take 54 minutes.
		assertEquals(54 * 60_000L, f.getMillis());
		assertEquals(27_000, f.getSlayerXp());
		assertEquals(1_440_000, f.getLoot());
		assertEquals(180_000, f.getSupplies());
		assertEquals("Abyssal demons x180: about 54m, +27K Slayer XP, ~1.44M gp, 180K gp of supplies (your last 2).",
			f.message("Abyssal demons"));
		assertNull(TaskForecast.of(log, "Kraken", 100, LootPrice.GRAND_EXCHANGE));
		assertNull(TaskForecast.of(log, "Abyssal demons", 0, LootPrice.GRAND_EXCHANGE));
	}

	@Test
	public void onlyTheNewestRunsCount()
	{
		List<SlayerTaskRecord> log = new ArrayList<>();
		for (int i = 0; i < TaskForecast.MAX_RUNS; i++)
		{
			log.add(run("Gargoyles", 100, 30, 20_000, 0, 0, 0));
		}
		// An old, slow run past the limit.
		log.add(run("Gargoyles", 100, 300, 20_000, 0, 0, 0));
		TaskForecast f = TaskForecast.of(log, "Gargoyles", 100, LootPrice.GRAND_EXCHANGE);
		assertEquals(TaskForecast.MAX_RUNS, f.getRuns());
		assertEquals(30 * 60_000L, f.getMillis());
	}

	@Test
	public void cannonComparison()
	{
		List<SlayerTaskRecord> log = Arrays.asList(
			run("Dust devils", 300, 60, 38_000, 0, 0, 2_000),
			run("Dust devils", 200, 60, 24_000, 0, 0, 0));
		TaskForecast.Cannon c = TaskForecast.cannon(log, "Dust devils", 200);
		assertEquals(38_000, c.getXpPerHourWith(), 0.5);
		assertEquals(24_000, c.getXpPerHourWithout(), 0.5);
		assertEquals(400_000, c.getBallCostPerHour(), 0.5);
		assertEquals("+14K/hr", c.gain());
		assertEquals("400K gp/hr", c.cost());
		// Never without a cannon: nothing to compare.
		assertNull(TaskForecast.cannon(Collections.singletonList(log.get(0)), "Dust devils", 200));
		// Too little fighting on one side.
		assertNull(TaskForecast.cannon(Arrays.asList(log.get(0), run("Dust devils", 20, 5, 2_000, 0, 0, 0)), "Dust devils", 200));
	}

	@Test
	public void prepCheckNamesWhatsMissing()
	{
		SlayerGuide guide = new SlayerGuide("Basilisks", Collections.emptyList(), Arrays.asList(
			new SlayerGuide.NeedStatus("Mirror shield", false, false, true),
			new SlayerGuide.NeedStatus("Nose peg", false, false, false),
			new SlayerGuide.NeedStatus("Antifire", false, true),
			new SlayerGuide.NeedStatus("Slayer helmet", true, false)), true);
		assertEquals("Prep check for Basilisks: missing Mirror shield (in your bank), Nose peg.", PrepCheck.missing(guide));

		SlayerGuide ready = new SlayerGuide("Basilisks", Collections.emptyList(), Collections.singletonList(
			new SlayerGuide.NeedStatus("Mirror shield", true, false)), true);
		assertNull(PrepCheck.missing(ready));
		assertTrue(PrepCheck.missing(SlayerGuide.NONE) == null);
	}
}
