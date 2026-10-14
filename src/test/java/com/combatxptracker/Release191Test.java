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
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 1.9.1: where to get a task, bank-aware task guide, best area for you.
 */
public class Release191Test
{
	private static final List<SlayerGuide.Area> AREAS = Arrays.asList(
		new SlayerGuide.Area("Slayer Tower", null, false),
		new SlayerGuide.Area("Catacombs of Kourend", null, false));

	// ---- A1: learning areas --------------------------------------------------------------

	@Test
	public void learnsRegionsForTheMarkedArea()
	{
		SlayerAreaMemory memory = new SlayerAreaMemory();
		assertNull(memory.areaFor(13623, AREAS));
		assertTrue(memory.learn(13623, "Slayer Tower", false, false));
		assertFalse("nothing new", memory.learn(13623, "Slayer Tower", false, false));
		assertEquals("Slayer Tower", memory.areaFor(13623, AREAS));
		// A region learned for an area this task can't be done in isn't picked.
		assertNull(memory.areaFor(13623, Collections.singletonList(new SlayerGuide.Area("Smoke Dungeon", null, false))));
		assertFalse(memory.learn(-1, "Slayer Tower", false, false));
		assertFalse(memory.learn(1, null, false, false));
	}

	@Test
	public void remembersMultiAndWilderness()
	{
		SlayerAreaMemory memory = new SlayerAreaMemory();
		memory.learn(6557, "Catacombs of Kourend", true, false);
		memory.learn(12345, "Wilderness Slayer Cave", true, true);
		assertTrue(memory.isMulti("catacombs of kourend"));
		assertFalse(memory.isWilderness("Catacombs of Kourend"));
		assertTrue(memory.isWilderness("Wilderness Slayer Cave"));
		assertFalse(memory.isMulti("Slayer Tower"));
		assertFalse(memory.isMulti(null));
	}

	@Test
	public void forgettingAWrongArea()
	{
		SlayerAreaMemory memory = new SlayerAreaMemory();
		memory.learn(1, "Slayer Tower", false, false);
		memory.learn(2, "Slayer Tower", false, false);
		memory.learn(3, "Catacombs of Kourend", false, false);
		memory.forget("slayer tower");
		assertNull(memory.areaFor(1));
		assertNull(memory.areaFor(2));
		assertEquals("Catacombs of Kourend", memory.areaFor(3));
	}

	@Test
	public void areaMemorySavesAndLoads()
	{
		SlayerAreaMemory memory = new SlayerAreaMemory();
		memory.learn(13623, "Slayer Tower", true, false);
		Gson gson = new Gson();
		String json = gson.toJson(memory.exportState());

		SlayerAreaMemory loaded = new SlayerAreaMemory();
		loaded.importState(gson.fromJson(json, SlayerAreaMemory.Saved.class));
		assertEquals("Slayer Tower", loaded.areaFor(13623));
		assertTrue(loaded.isMulti("Slayer Tower"));

		loaded.importState(null);
		assertTrue(loaded.isEmpty());
		// Corrupt or old saves don't break it.
		loaded.importState(gson.fromJson("{\"regions\":null}", SlayerAreaMemory.Saved.class));
		assertTrue(loaded.isEmpty());
	}

	@Test
	public void areaMemoryIsCapped()
	{
		SlayerAreaMemory memory = new SlayerAreaMemory();
		for (int i = 1; i <= SlayerAreaMemory.MAX_REGIONS + 50; i++)
		{
			memory.learn(i, "Somewhere", false, false);
		}
		assertNull(memory.areaFor(SlayerAreaMemory.MAX_REGIONS + 1));
		assertEquals("Somewhere", memory.areaFor(SlayerAreaMemory.MAX_REGIONS));
	}

	private static SlayerTaskRecord record(String task, String area, int kills, long minutes, int xp)
	{
		SlayerTaskRecord r = new SlayerTaskRecord();
		r.task = task;
		r.area = area;
		r.kills = kills;
		r.activeMillis = minutes * 60_000L;
		r.slayerXp = xp;
		return r;
	}

	@Test
	public void ratesPerArea()
	{
		List<SlayerTaskRecord> log = Arrays.asList(
			record("Abyssal demons", "Slayer Tower", 120, 60, 18_000),
			record("Abyssal demons", "Catacombs of Kourend", 180, 60, 27_000),
			record("Abyssal demons", "Catacombs of Kourend", 160, 60, 24_000),
			record("Abyssal demons", null, 150, 60, 22_000),
			record("Gargoyles", "Slayer Tower", 150, 60, 30_000));
		Map<String, TaskStats> rates = TaskStats.byArea(log, "abyssal demons", LootPrice.GRAND_EXCHANGE);
		assertEquals(2, rates.size());
		TaskStats catacombs = rates.get("catacombs of kourend");
		assertEquals(2, catacombs.getTimes());
		assertEquals(170, catacombs.getKillsPerHour(), 0.01);
		assertEquals(25_500, catacombs.getXpPerHour(), 1);

		assertSame(catacombs, SlayerView.bestArea(rates));
		List<SlayerGuide.Area> sorted = SlayerView.sortAreas(AREAS, rates);
		assertEquals("Catacombs of Kourend", sorted.get(0).getName());

		// One area isn't a comparison.
		assertNull(SlayerView.bestArea(TaskStats.byArea(log, "Gargoyles", LootPrice.GRAND_EXCHANGE)));
	}

	@Test
	public void konarsPickStaysFirst()
	{
		List<SlayerGuide.Area> areas = Arrays.asList(
			new SlayerGuide.Area("Catacombs of Kourend", null, false),
			new SlayerGuide.Area("Slayer Tower", null, true));
		List<SlayerTaskRecord> log = Collections.singletonList(record("Abyssal demons", "Catacombs of Kourend", 180, 60, 27_000));
		List<SlayerGuide.Area> sorted = SlayerView.sortAreas(areas, TaskStats.byArea(log, "Abyssal demons", LootPrice.GRAND_EXCHANGE));
		assertEquals("Slayer Tower", sorted.get(0).getName());
	}

	@Test
	public void taskRecordKeepsItsArea()
	{
		SlayerTaskSession session = new SlayerTaskSession();
		session.start("Abyssal demons");
		session.setArea("Slayer Tower");
		session.recordKill(1_000);
		assertEquals("Slayer Tower", session.toRecord(2_000, -1).area);
		session.start("Gargoyles");
		assertNull("a new task starts without an area", session.getArea());
	}

	// ---- I2: where to get a task -----------------------------------------------------------

	@Test
	public void sourcesRankMastersForATask()
	{
		Map<SlayerPoints.Master, TaskOdds> all = new EnumMap<>(SlayerPoints.Master.class);
		all.put(SlayerPoints.Master.DURADEL, TaskOdds.compute("Duradel", Arrays.asList(
			new TaskOdds.Option("Gargoyles", 8, 130, 200, null),
			new TaskOdds.Option("Abyssal demons", 12, 130, 200, null)), Collections.emptySet(), true));
		all.put(SlayerPoints.Master.NIEVE, TaskOdds.compute("Nieve", Arrays.asList(
			new TaskOdds.Option("Gargoyles", 6, 120, 185, null),
			new TaskOdds.Option("Abyssal demons", 9, 120, 185, null),
			new TaskOdds.Option("Hydras", 10, 125, 190, null)), Collections.emptySet(), true));
		all.put(SlayerPoints.Master.KONAR, TaskOdds.compute("Konar", Arrays.asList(
			new TaskOdds.Option("Gargoyles", 6, 120, 170, "Needs 75 Slayer"),
			new TaskOdds.Option("Hydras", 10, 125, 190, null)), Collections.emptySet(), true));
		all.put(SlayerPoints.Master.TURAEL, TaskOdds.compute("Turael", Collections.singletonList(
			new TaskOdds.Option("Cows", 8, 15, 50, null)), Collections.emptySet(), true));

		TaskSources sources = TaskSources.of("gargoyles", all);
		assertEquals(3, sources.getSources().size());
		assertSame(SlayerPoints.Master.DURADEL, sources.best().getMaster());
		assertEquals(8 / 20.0, sources.best().getChance(), 1e-9);
		TaskSources.Source konar = sources.getSources().get(2);
		assertSame(SlayerPoints.Master.KONAR, konar.getMaster());
		assertEquals("Needs 75 Slayer", konar.getUnavailable());

		assertTrue(TaskSources.of("Unicorns", all).getSources().isEmpty());
		assertNull(TaskSources.of("Unicorns", all).best());
	}

	@Test
	public void blockedTaskShowsWhy()
	{
		Map<SlayerPoints.Master, TaskOdds> all = new EnumMap<>(SlayerPoints.Master.class);
		all.put(SlayerPoints.Master.DURADEL, TaskOdds.compute("Duradel", Arrays.asList(
			new TaskOdds.Option("Gargoyles", 8, 130, 200, null),
			new TaskOdds.Option("Abyssal demons", 12, 130, 200, null)),
			new HashSet<>(Collections.singletonList("gargoyles")), true));
		TaskSources.Source src = TaskSources.of("Gargoyles", all).getSources().get(0);
		assertEquals("Blocked by you", src.getUnavailable());
		assertEquals(0, src.getChance(), 0);
	}

	// ---- I3: bank-aware guide -----------------------------------------------------------

	@Test
	public void needsKnowWhatsInTheBank()
	{
		SlayerGuide guide = new SlayerGuide("Gargoyles", Collections.emptyList(), Arrays.asList(
			new SlayerGuide.NeedStatus("Rock hammer", false, false, true),
			new SlayerGuide.NeedStatus("Earmuffs", false, false, false),
			new SlayerGuide.NeedStatus("Carried", true, false, true),
			new SlayerGuide.NeedStatus("Tip", false, true, true)), true);
		assertEquals(2, guide.missingCount());
		assertEquals(1, guide.inBankCount());
		assertTrue(guide.isBankKnown());
		assertFalse("carried wins over bank", guide.getNeeds().get(2).isInBank());
		assertFalse(new SlayerGuide("X", Collections.emptyList(), Collections.emptyList()).isBankKnown());
	}

	// ---- Attack timer (reworked in 1.9.6: checked once a tick) ---------------------------------

	@Test
	public void attackCountsWhenReadyAndAttacking()
	{
		AttackTimer timer = new AttackTimer();
		timer.onTick(200, false, 4);
		assertEquals(-1, timer.ticksLeft(200, 4));
		timer.onTick(201, true, 4);
		assertEquals(4, timer.ticksLeft(201, 4));
		assertEquals(3, timer.ticksLeft(202, 4));
	}

	@Test
	public void blocksWhileCoolingDownAreIgnored()
	{
		AttackTimer timer = new AttackTimer();
		// Attack at 700. The monster hits back at 701 and 702: still animating with a target.
		timer.onTick(700, true, 4);
		timer.onTick(701, true, 4);
		timer.onTick(702, true, 4);
		assertEquals(2, timer.ticksLeft(702, 4));
		timer.onTick(703, true, 4);
		// Ready again at 704, and swinging: a new attack.
		timer.onTick(704, true, 4);
		assertEquals(4, timer.ticksLeft(704, 4));
	}

	@Test
	public void waitingForATargetKeepsShowingReady()
	{
		AttackTimer timer = new AttackTimer();
		timer.onTick(800, true, 5);
		for (int tick = 801; tick <= 808; tick++)
		{
			timer.onTick(tick, false, 5);
		}
		assertEquals(0, timer.ticksLeft(808, 5));
		// The next swing.
		timer.onTick(809, true, 5);
		assertEquals(5, timer.ticksLeft(809, 5));
	}

	@Test
	public void xpDropCorrectsTheTiming()
	{
		AttackTimer timer = new AttackTimer();
		// The animation was missed, but the XP drop from the hit landed on 900.
		timer.onAttackSignal(900, 4);
		timer.onTick(901, true, 4);
		assertEquals(3, timer.ticksLeft(901, 4));
	}

	@Test
	public void bankItemsSaveAsIds()
	{
		assertEquals(Arrays.asList(4155, 11865, 4162), CombatXpTrackerPlugin.parseIds("4155, 11865,x,4162"));
		assertTrue(CombatXpTrackerPlugin.parseIds("").isEmpty());
	}
}
