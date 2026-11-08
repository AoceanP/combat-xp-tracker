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
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * 1.9.4: Gielinor Wrapped, milestones, spreadsheet export.
 */
public class Release194Test
{
	private static MonsterTracker tracker()
	{
		MonsterTracker t = new MonsterTracker();
		for (int i = 0; i < 120; i++)
		{
			t.recordHit("Gargoyle", 20, CombatStyle.MELEE, 1_000 + i * 6_000L);
			t.recordKill("Gargoyle", 1_000 + i * 6_000L);
		}
		t.recordHit("Vorkath", 71, CombatStyle.RANGED, 2_000);
		t.recordKill("Vorkath", 2_000);
		t.recordLoot("Vorkath", Collections.singletonList(new MonsterTracker.Drop(1, "Dragon bones", 2, 3_000, 0)), 2_000);
		return t;
	}

	// ---- N9: Wrapped ------------------------------------------------------------------------

	@Test
	public void wrappedSumsTheLifetime()
	{
		SlayerTaskRecord task = new SlayerTaskRecord();
		task.task = "Gargoyles";
		task.kills = 120;
		task.activeMillis = 30 * 60_000L;
		task.slayerXp = 12_000;
		task.superiors = 2;
		task.points = 15;
		WrappedStats w = WrappedStats.of(tracker().snapshot(), Collections.singletonList(task), LootPrice.GRAND_EXCHANGE);
		assertFalse(w.isEmpty());
		String text = w.text("Zezima");
		assertTrue(text, text.startsWith("Gielinor Wrapped - Zezima"));
		assertTrue(text, text.contains("Kills: 121 (2 different monsters)"));
		assertTrue(text, text.contains("Damage dealt: 2,471"));
		assertTrue(text, text.contains("Biggest hit: 71 (on Vorkath, ranged)"));
		assertTrue(text, text.contains("Most killed: Gargoyle (120 kills)"));
		assertTrue(text, text.contains("Most loot: Vorkath"));
		assertTrue(text, text.contains("Longest dry streak: 120 kills (on Gargoyle)"));
		assertTrue(text, text.contains("Tasks done: 1"));
		assertTrue(text, text.contains("Fastest task: Gargoyles (24K XP/hr)"));
		assertTrue(WrappedStats.of(Collections.emptyList(), Collections.emptyList(), LootPrice.GRAND_EXCHANGE).isEmpty());
	}

	// ---- M11: milestones ------------------------------------------------------------------------

	@Test
	public void killMilestones()
	{
		assertEquals("1,000 Gargoyle kills!", Milestones.onKill("Gargoyle", 1_000, 5_432));
		assertNull(Milestones.onKill("Gargoyle", 1_001, 5_433));
		assertEquals("10,000 kills in total!", Milestones.onKill("Cow", 37, 10_000));
		assertEquals("100th slayer task logged!", Milestones.onTask(100));
		assertNull(Milestones.onTask(99));
		assertEquals("21st", Milestones.ordinal(21));
		assertEquals("112th", Milestones.ordinal(112));
		assertEquals("1,000th", Milestones.ordinal(1_000));
	}

	@Test
	public void biggestHitNeedsARecordWorthBeating()
	{
		Milestones m = new Milestones();
		m.setRecord(60, 5_000);
		assertNull(m.onHit(60, "Vorkath", CombatStyle.RANGED));
		assertEquals("New biggest hit: 71 on Vorkath (ranged)!", m.onHit(71, "Vorkath", CombatStyle.RANGED));
		assertNull("71 is the record now", m.onHit(70, "Vorkath", CombatStyle.RANGED));

		// A new account: the first hits set the record quietly.
		Milestones fresh = new Milestones();
		fresh.setRecord(-1, 0);
		assertNull(fresh.onHit(30, "Goblin", CombatStyle.MELEE));
		fresh.setRecord(12, 20);
		assertNull("too few hits to trust the record", fresh.onHit(40, "Goblin", CombatStyle.MELEE));
	}

	@Test
	public void trackerTotals()
	{
		MonsterTracker t = tracker();
		assertEquals(120, t.getKills("Gargoyle"));
		assertEquals(0, t.getKills("Cow"));
		assertEquals(121, t.getTotalKills());
		assertEquals(71, t.getBiggestHit());
		assertEquals(121, t.getTotalHits());
	}

	// ---- Master tip (replaces the best master card) ---------------------------------------------

	private static MasterAdvice.Entry entry(SlayerPoints.Master m, double xpHr, boolean ranked)
	{
		return new MasterAdvice.Entry(m, xpHr, 0, 15, 0.5, 1, null, ranked);
	}

	@Test
	public void tipOnlyWhenAnotherMasterIsClearlyBetter()
	{
		List<MasterAdvice.Entry> entries = Arrays.asList(
			entry(SlayerPoints.Master.DURADEL, 40_000, true),
			entry(SlayerPoints.Master.NIEVE, 45_000, true),
			entry(SlayerPoints.Master.KONAR, 60_000, false));
		// Nieve is 12.5% better; Konar isn't ranked (too little data).
		assertEquals(SlayerPoints.Master.NIEVE,
			MasterAdvice.betterThan(entries, SlayerPoints.Master.DURADEL, MasterAdvice.MIN_TIP_GAIN).getMaster());
		// Already on the best one.
		assertNull(MasterAdvice.betterThan(entries, SlayerPoints.Master.NIEVE, MasterAdvice.MIN_TIP_GAIN));
		// Only 5% better: not worth a tip.
		List<MasterAdvice.Entry> close = Arrays.asList(
			entry(SlayerPoints.Master.DURADEL, 40_000, true),
			entry(SlayerPoints.Master.NIEVE, 42_000, true));
		assertNull(MasterAdvice.betterThan(close, SlayerPoints.Master.DURADEL, MasterAdvice.MIN_TIP_GAIN));
		// Your master without enough data: no tip.
		List<MasterAdvice.Entry> unknown = Arrays.asList(
			entry(SlayerPoints.Master.DURADEL, 40_000, false),
			entry(SlayerPoints.Master.NIEVE, 60_000, true));
		assertNull(MasterAdvice.betterThan(unknown, SlayerPoints.Master.DURADEL, MasterAdvice.MIN_TIP_GAIN));
		assertNull(MasterAdvice.betterThan(entries, null, MasterAdvice.MIN_TIP_GAIN));
	}
}
