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
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MonsterTrackerTest
{
	private static final long MINUTE = 60_000L;

	@Test
	public void hitsAverageAndBiggestPerStyle()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Abyssal demon", 20, CombatStyle.MELEE, 1);
		t.recordHit("Abyssal demon", 45, CombatStyle.MELEE, 2);
		t.recordHit("Abyssal demon", 38, CombatStyle.MAGIC, 3);
		t.recordHit("Abyssal demon", 0, CombatStyle.RANGED, 4);

		MonsterTracker.Snapshot s = t.snapshot().get(0);
		assertEquals(4, s.getHits());
		assertEquals((20 + 45 + 38 + 0) / 4.0, s.getAverageHit(), 1e-9);
		assertEquals(45, s.getBiggestHit());
		assertEquals(CombatStyle.MELEE, s.getBiggestHitStyle());
		assertEquals(Integer.valueOf(45), s.getBiggestByStyle().get(CombatStyle.MELEE));
		assertEquals(Integer.valueOf(38), s.getBiggestByStyle().get(CombatStyle.MAGIC));
		assertEquals(Integer.valueOf(0), s.getBiggestByStyle().get(CombatStyle.RANGED));
	}

	@Test
	public void unknownStyleStillCountsTowardTotals()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Goblin", 5, null, 1);
		MonsterTracker.Snapshot s = t.snapshot().get(0);
		assertEquals(5, s.getBiggestHit());
		assertNull(s.getBiggestHitStyle());
		assertTrue(s.getBiggestByStyle().isEmpty());
	}

	@Test
	public void killsCountWithoutDrops()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Cow", 1);
		t.recordKill("Cow", 2);
		MonsterTracker.Snapshot s = t.snapshot().get(0);
		assertEquals(2, s.getKills());
		assertTrue(s.getLoot().isEmpty());
	}

	@Test
	public void lootStacksAcrossKillsAndSortsByValue()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Abyssal demon", 1);
		t.recordLoot("Abyssal demon", Arrays.asList(
			new MonsterTracker.Drop(995, "Coins", 500, 1),
			new MonsterTracker.Drop(4151, "Abyssal whip", 1, 1_500_000)), 1);
		t.recordKill("Abyssal demon", 2);
		t.recordLoot("Abyssal demon", Collections.singletonList(
			new MonsterTracker.Drop(995, "Coins", 250, 1)), 2);

		MonsterTracker.Snapshot s = t.snapshot().get(0);
		assertEquals(2, s.getKills());
		assertEquals(1_500_750, s.getLootValue());

		List<MonsterTracker.LootLine> loot = s.getLoot();
		assertEquals("Abyssal whip", loot.get(0).getName());
		assertEquals("Coins", loot.get(1).getName());
		assertEquals(750, loot.get(1).getQuantity());
	}

	@Test
	public void ratesUseFightingTimeAndSkipBreaks()
	{
		MonsterTracker t = new MonsterTracker();
		// 10 kills a minute apart = 9 minutes of fighting...
		for (int i = 0; i < 10; i++)
		{
			t.recordKill("Cow", i * MINUTE);
			t.recordLoot("Cow", Collections.singletonList(new MonsterTracker.Drop(1739, "Cowhide", 1, 100)), i * MINUTE);
		}
		// ...then a 2-hour break, then one more kill a minute after coming back.
		t.recordHit("Cow", 5, CombatStyle.MELEE, 129 * MINUTE);
		t.recordKill("Cow", 130 * MINUTE);

		MonsterTracker.Snapshot s = t.snapshot().get(0);
		assertEquals(10 * MINUTE, s.getActiveMillis());
		// 11 kills in 10 minutes of fighting
		assertEquals(66.0, s.getKillsPerHour(), 1e-9);
		// 1,000 gp in 10 minutes
		assertEquals(6_000.0, s.getGpPerHour(), 1e-9);
	}

	@Test
	public void noRatesUntilAMinuteOfFighting()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Cow", 0);
		t.recordKill("Cow", 30_000);
		assertEquals(-1, t.snapshot().get(0).getKillsPerHour(), 0.0);
	}

	@Test
	public void mostRecentMonsterFirst()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Goblin", 1, CombatStyle.MELEE, 10);
		t.recordHit("Cow", 1, CombatStyle.MELEE, 20);
		assertEquals("Cow", t.snapshot().get(0).getName());
		t.recordHit("Goblin", 1, CombatStyle.MELEE, 30);
		assertEquals("Goblin", t.snapshot().get(0).getName());
	}

	@Test
	public void sortOrders()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Cow", 3, CombatStyle.MELEE, 30);
		t.recordKill("Cow", 30);
		t.recordHit("Abyssal demon", 45, CombatStyle.MELEE, 10);
		t.recordLoot("Abyssal demon", Collections.singletonList(new MonsterTracker.Drop(4151, "Abyssal whip", 1, 1_500_000)), 10);
		t.recordHit("Goblin", 8, CombatStyle.MELEE, 20);
		t.recordKill("Goblin", 20);
		t.recordKill("Goblin", 21);

		List<MonsterTracker.Snapshot> all = t.snapshot();
		Set<String> none = Collections.emptySet();
		assertEquals("Cow", MonsterTracker.view(all, MonsterSort.RECENT, none).get(0).getName());
		assertEquals("Abyssal demon", MonsterTracker.view(all, MonsterSort.LOOT, none).get(0).getName());
		assertEquals("Goblin", MonsterTracker.view(all, MonsterSort.KILLS, none).get(0).getName());
		assertEquals("Abyssal demon", MonsterTracker.view(all, MonsterSort.BIGGEST_HIT, none).get(0).getName());
		assertEquals("Abyssal demon", MonsterTracker.view(all, MonsterSort.NAME, none).get(0).getName());
	}

	@Test
	public void hiddenMonstersAreLeftOutIgnoringCase()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Cow", 1, CombatStyle.MELEE, 1);
		t.recordHit("Goblin", 1, CombatStyle.MELEE, 2);

		Set<String> hidden = MonsterTracker.parseNames(" cow ,, GOBLIN ");
		assertEquals(2, hidden.size());
		assertTrue(MonsterTracker.view(t.snapshot(), MonsterSort.RECENT, hidden).isEmpty());
		assertEquals(1, MonsterTracker.view(t.snapshot(), MonsterSort.RECENT, MonsterTracker.parseNames("cow")).size());
		assertTrue(MonsterTracker.parseNames(null).isEmpty());
	}

	@Test
	public void saveAndLoadRoundTripsThroughGson()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Abyssal demon", 45, CombatStyle.MELEE, 0);
		t.recordHit("Abyssal demon", 38, CombatStyle.MAGIC, MINUTE);
		t.recordKill("Abyssal demon", 2 * MINUTE);
		t.recordLoot("Abyssal demon", Collections.singletonList(new MonsterTracker.Drop(4151, "Abyssal whip", 1, 1_500_000)), 2 * MINUTE);

		Gson gson = new com.google.gson.GsonBuilder().create();
		String json = gson.toJson(t.exportState());
		MonsterTracker loaded = new MonsterTracker();
		loaded.importState(Arrays.asList(gson.fromJson(json, MonsterTracker.Saved[].class)));

		MonsterTracker.Snapshot a = t.snapshot().get(0);
		MonsterTracker.Snapshot b = loaded.snapshot().get(0);
		assertEquals(a.getName(), b.getName());
		assertEquals(a.getKills(), b.getKills());
		assertEquals(a.getHits(), b.getHits());
		assertEquals(a.getAverageHit(), b.getAverageHit(), 1e-9);
		assertEquals(a.getBiggestHit(), b.getBiggestHit());
		assertEquals(a.getBiggestHitStyle(), b.getBiggestHitStyle());
		assertEquals(a.getBiggestByStyle(), b.getBiggestByStyle());
		assertEquals(a.getLootValue(), b.getLootValue());
		assertEquals(a.getActiveMillis(), b.getActiveMillis());
		assertEquals("Abyssal whip", b.getLoot().get(0).getName());
	}

	@Test
	public void loadingSkipsCorruptEntries()
	{
		MonsterTracker.Saved good = new MonsterTracker.Saved();
		good.name = "Cow";
		good.kills = 3;
		MonsterTracker.Saved noName = new MonsterTracker.Saved();

		MonsterTracker t = new MonsterTracker();
		t.importState(Arrays.asList(good, null, noName));
		assertEquals(1, t.snapshot().size());
		assertEquals(3, t.snapshot().get(0).getKills());

		t.importState(null);
		assertTrue(t.isEmpty());
	}

	@Test
	public void revisionChangesOnEveryUpdate()
	{
		MonsterTracker t = new MonsterTracker();
		int r0 = t.getRevision();
		t.recordHit("Cow", 1, CombatStyle.MELEE, 1);
		int r1 = t.getRevision();
		t.remove("Cow");
		int r2 = t.getRevision();
		assertTrue(r1 > r0);
		assertTrue(r2 > r1);
		assertTrue(t.snapshot().isEmpty());
	}

	@Test
	public void namelessTargetsAreIgnored()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit(null, 10, CombatStyle.MELEE, 1);
		t.recordHit("", 10, CombatStyle.MELEE, 1);
		t.recordKill(" ", 1);
		assertTrue(t.snapshot().isEmpty());
	}
}
