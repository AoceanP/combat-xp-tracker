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
import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * 1.9.0: Slayer tab (points, log, superiors), drag to reorder, attack timer.
 */
public class Release190Test
{
	// ---- Slayer points (OSRS Wiki "Slayer reward point") ------------------------------------

	@Test
	public void pointsPerTaskAndMilestones()
	{
		SlayerPoints.Master duradel = SlayerPoints.Master.DURADEL;
		assertEquals(0, SlayerPoints.pointsFor(duradel, 4, false));
		assertEquals(15, SlayerPoints.pointsFor(duradel, 5, false));
		assertEquals(75, SlayerPoints.pointsFor(duradel, 10, false));
		assertEquals(225, SlayerPoints.pointsFor(duradel, 50, false));
		assertEquals(375, SlayerPoints.pointsFor(duradel, 100, false));
		assertEquals(525, SlayerPoints.pointsFor(duradel, 250, false));
		assertEquals(750, SlayerPoints.pointsFor(duradel, 1000, false));
		assertEquals(375, SlayerPoints.pointsFor(duradel, 300, false));
	}

	@Test
	public void diaryBonusesAndSpecialMasters()
	{
		assertEquals(12, SlayerPoints.pointsFor(SlayerPoints.Master.NIEVE, 7, false));
		assertEquals(15, SlayerPoints.pointsFor(SlayerPoints.Master.NIEVE, 7, true));
		assertEquals(900, SlayerPoints.pointsFor(SlayerPoints.Master.KONAR, 1000, false));
		assertEquals(1000, SlayerPoints.pointsFor(SlayerPoints.Master.KONAR, 1000, true));
		assertEquals(1250, SlayerPoints.pointsFor(SlayerPoints.Master.KRYSTILIA, 1000, false));
		assertEquals(0, SlayerPoints.pointsFor(SlayerPoints.Master.TURAEL, 50, false));
		assertEquals(-1, SlayerPoints.pointsFor(SlayerPoints.Master.MORTIMER, 50, false));
	}

	@Test
	public void mastersFromGameIds()
	{
		assertEquals(SlayerPoints.Master.TURAEL, SlayerPoints.Master.fromId(1));
		assertEquals(SlayerPoints.Master.KRYSTILIA, SlayerPoints.Master.fromId(7));
		assertEquals(SlayerPoints.Master.KONAR, SlayerPoints.Master.fromId(8));
		assertEquals(SlayerPoints.Master.MORTIMER, SlayerPoints.Master.fromId(10));
		assertNull(SlayerPoints.Master.fromId(0));
	}

	@Test
	public void nextBonusTasks()
	{
		assertEquals(10, SlayerPoints.nextBonusTask(1, 5));
		assertEquals(50, SlayerPoints.nextBonusTask(47, 5));
		assertEquals(50, SlayerPoints.nextBonusTask(50, 5));
		assertEquals(60, SlayerPoints.nextBonusTask(51, 5));
		assertEquals(50, SlayerPoints.nextBonusTask(3, 15));
		assertEquals(100, SlayerPoints.nextBonusTask(51, 15));
		assertEquals(250, SlayerPoints.nextBonusTask(201, 15));
		assertEquals(1000, SlayerPoints.nextBonusTask(951, 15));
		assertEquals(35, SlayerPoints.multiplier(250));
	}

	// ---- Superiors ----------------------------------------------------------------------------

	@Test
	public void superiorsCountForTheirTask()
	{
		assertTrue(SuperiorMonsters.isSuperior("Night beast"));
		assertEquals("Dark beast", SuperiorMonsters.normalFor("night beast"));
		assertNull(SuperiorMonsters.normalFor("Dark beast"));

		SlayerTaskSession task = new SlayerTaskSession();
		task.start("Dark beasts");
		assertTrue(task.isTaskMonster("Night beast"));
		task.start("Nechryael");
		assertTrue(task.isTaskMonster("Nechryarch"));
		task.start("Dust devils");
		assertTrue(task.isTaskMonster("Choke devil"));
		assertFalse(task.isTaskMonster("Nuclear smoke devil"));
	}

	@Test
	public void taskRecordAndSummaryIncludeSuperiors()
	{
		SlayerTaskSession task = new SlayerTaskSession();
		task.start("Abyssal demons");
		task.setDetails(180, "Duradel", null);
		task.recordKill(1_000);
		task.recordKill(61_000);
		task.recordSuperior();
		task.recordXp(true, 300);
		task.recordXp(false, 1200);
		task.recordLoot(5_000, 3_000, 61_000);
		task.recordHit(47, 61_000);

		SlayerTaskRecord r = task.toRecord(99L, 15);
		assertEquals("Abyssal demons", r.task);
		assertEquals("Duradel", r.master);
		assertEquals(180, r.assigned);
		assertEquals(2, r.kills);
		assertEquals(1, r.superiors);
		assertEquals(300, r.slayerXp);
		assertEquals(1200, r.combatXp);
		assertEquals(5_000, r.loot(LootPrice.GRAND_EXCHANGE));
		assertEquals(3_000, r.loot(LootPrice.HIGH_ALCHEMY));
		assertEquals(47, r.biggestHit);
		assertEquals(15, r.points);
		assertTrue(task.summary(LootPrice.GRAND_EXCHANGE).endsWith("1 superior."));
	}

	// ---- Task log -----------------------------------------------------------------------------

	private static SlayerTaskRecord record(String task, int kills, int superiors)
	{
		SlayerTaskRecord r = new SlayerTaskRecord();
		r.task = task;
		r.kills = kills;
		r.superiors = superiors;
		return r;
	}

	@Test
	public void logKeepsNewestFirstAndSaves()
	{
		SlayerLog log = new SlayerLog();
		log.add(record("Gargoyles", 100, 1));
		log.add(record("Abyssal demons", 150, 0));
		assertEquals("Abyssal demons", log.getTasks().get(0).task);
		assertEquals(250, log.totalKills());
		assertEquals(1, log.totalSuperiors());

		Gson gson = new Gson();
		SlayerLog loaded = new SlayerLog();
		loaded.importState(gson.fromJson(gson.toJson(log.exportState()), SlayerTaskRecord[].class));
		assertEquals(2, loaded.getTasks().size());
		assertEquals("Gargoyles", loaded.getTasks().get(1).task);

		SlayerTaskRecord first = loaded.getTasks().get(0);
		loaded.remove(first);
		assertEquals(1, loaded.getTasks().size());
	}

	@Test
	public void logDropsOldestPastTheLimit()
	{
		SlayerLog log = new SlayerLog();
		for (int i = 0; i < SlayerLog.MAX_TASKS + 5; i++)
		{
			log.add(record("Task " + i, 1, 0));
		}
		List<SlayerTaskRecord> tasks = log.getTasks();
		assertEquals(SlayerLog.MAX_TASKS, tasks.size());
		assertEquals("Task " + (SlayerLog.MAX_TASKS + 4), tasks.get(0).task);
	}

	@Test
	public void killsPerSuperior()
	{
		assertEquals(-1, SlayerLog.killsPerSuperior(100, 0), 0);
		assertEquals(50, SlayerLog.killsPerSuperior(100, 2), 0.001);
	}

	// ---- Pinned order -------------------------------------------------------------------------

	@Test
	public void pinnedMonstersKeepTheirDraggedOrder()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Cow", 30);
		t.recordKill("Goblin", 20);
		t.recordKill("Zulrah", 10);
		List<MonsterTracker.Snapshot> view = MonsterTracker.view(t.snapshot(), MonsterSort.RECENT,
			Collections.emptySet(), MonsterTracker.parseNames("zulrah, goblin"));
		assertEquals("Zulrah", view.get(0).getName());
		assertEquals("Goblin", view.get(1).getName());
		assertEquals("Cow", view.get(2).getName());
	}

	// ---- Attack timer (OSRS Wiki "Attack speed") ------------------------------------------------

	@Test
	public void attackSpeeds()
	{
		// The game names Rapid "Ranging".
		CombatStyle.AttackStyle rapid = CombatStyle.AttackStyle.fromName("Ranging");
		CombatStyle.AttackStyle accurate = CombatStyle.AttackStyle.rangedAccurate();
		CombatStyle.AttackStyle aggressive = CombatStyle.AttackStyle.fromName("Aggressive");
		CombatStyle.AttackStyle casting = CombatStyle.AttackStyle.fromName("Casting");

		assertEquals(4, AttackTimer.ticksBetweenAttacks(4, aggressive, false, false));
		assertEquals(4, AttackTimer.ticksBetweenAttacks(0, aggressive, false, false));
		assertEquals(5, AttackTimer.ticksBetweenAttacks(6, rapid, false, false));
		assertEquals(6, AttackTimer.ticksBetweenAttacks(6, accurate, false, false));
		assertEquals(2, AttackTimer.ticksBetweenAttacks(3, rapid, false, false));
		assertEquals(5, AttackTimer.ticksBetweenAttacks(4, casting, false, false));
		assertEquals(4, AttackTimer.ticksBetweenAttacks(4, casting, false, true));
		assertEquals(4, AttackTimer.ticksBetweenAttacks(4, casting, true, false));
	}

	@Test
	public void timerCountsDownAndIgnoresSignalsWhileCooling()
	{
		AttackTimer timer = new AttackTimer();
		assertEquals(-1, timer.ticksLeft(100, 4));
		timer.onAttackSignal(100, 4);
		assertEquals(4, timer.ticksLeft(100, 4));
		assertEquals(3, timer.ticksLeft(101, 4));
		// A block animation mid-cooldown doesn't restart it.
		timer.onAttackSignal(101, 4);
		assertEquals(2, timer.ticksLeft(102, 4));
		assertEquals(0, timer.ticksLeft(104, 4));
		timer.onAttackSignal(104, 4);
		assertEquals(4, timer.ticksLeft(104, 4));
		// Hidden after a while without attacking.
		assertEquals(-1, timer.ticksLeft(104 + AttackTimer.IDLE_TICKS + 1, 4));
	}

	@Test
	public void timerResetsWhenTheTickCounterRestarts()
	{
		AttackTimer timer = new AttackTimer();
		timer.onAttackSignal(5_000, 4);
		// Hopping worlds restarts the tick count.
		assertEquals(-1, timer.ticksLeft(3, 4));
		timer.onAttackSignal(3, 4);
		assertEquals(4, timer.ticksLeft(3, 4));
	}
}
