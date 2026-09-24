package com.combatxptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MonsterTrackerTest
{
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
	public void lootStacksAcrossKillsAndSortsByValue()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Abyssal demon", Arrays.asList(
			new MonsterTracker.Drop(995, "Coins", 500, 1),
			new MonsterTracker.Drop(4151, "Abyssal whip", 1, 1_500_000)), 1);
		t.recordKill("Abyssal demon", Collections.singletonList(
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
		assertTrue(t.snapshot().isEmpty());
	}
}
