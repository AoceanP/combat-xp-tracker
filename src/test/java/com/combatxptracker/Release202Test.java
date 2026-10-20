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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 2.0.2: saved stats are never written unless they were loaded first, and lost stats can be
 * rebuilt from the Loot Tracker.
 */
public class Release202Test
{
	@Test
	public void savesOnlyForTheLoadedAccount()
	{
		assertTrue(CombatXpTrackerPlugin.canSave("rsprofile.abc", "rsprofile.abc"));
	}

	@Test
	public void aStartThatNeverLoadedCantSave()
	{
		// 2.0 failed before loading, then shutting down saved empty stats over the real ones.
		assertFalse(CombatXpTrackerPlugin.canSave("rsprofile.abc", null));
	}

	@Test
	public void anotherAccountsDataIsNeverSavedOverThisOne()
	{
		assertFalse(CombatXpTrackerPlugin.canSave("rsprofile.def", "rsprofile.abc"));
	}

	@Test
	public void nothingIsSavedAtTheLoginScreen()
	{
		assertFalse(CombatXpTrackerPlugin.canSave(null, "rsprofile.abc"));
		assertFalse(CombatXpTrackerPlugin.canSave(null, null));
	}

	// ---- restore from the Loot Tracker ----

	private static final LootTrackerRestore.Items ITEMS = new LootTrackerRestore.Items()
	{
		@Override
		public String name(int itemId)
		{
			return "Item " + itemId;
		}

		@Override
		public long price(int itemId)
		{
			return 100;
		}

		@Override
		public long haPrice(int itemId)
		{
			return 60;
		}
	};

	private static String record(String npc, int kills, int... drops)
	{
		StringBuilder d = new StringBuilder();
		for (int i = 0; i < drops.length; i++)
		{
			d.append(i == 0 ? "" : ",").append(drops[i]);
		}
		return "{\"type\":\"NPC\",\"name\":\"" + npc + "\",\"kills\":" + kills
			+ ",\"first\":1,\"last\":2,\"drops\":[" + d + "]}";
	}

	private static LootTrackerRestore.Result restore(List<MonsterTracker.Saved> current, List<String> records,
		Map<String, Integer> killCounts)
	{
		return LootTrackerRestore.merge(current, records, killCounts, null, RuneLiteAPI.GSON, ITEMS);
	}

	private static MonsterTracker.Saved find(LootTrackerRestore.Result result, String name)
	{
		for (MonsterTracker.Saved s : result.monsters)
		{
			if (s.name.equals(name))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void rebuildsKillsAndDrops()
	{
		LootTrackerRestore.Result result = restore(new ArrayList<>(),
			Arrays.asList(record("Cave horror", 455, 995, 10000, 8901, 1)), Collections.emptyMap());
		MonsterTracker.Saved horror = find(result, "Cave horror");
		assertEquals(455, horror.kills);
		assertEquals(2, horror.loot.size());
		assertEquals(1, horror.loot.get(1).quantity);
		assertEquals(1, result.restored);
		assertEquals(455, result.kills);
	}

	@Test
	public void bothRoyalTitansAreOneBossWithTheGamesKillCount()
	{
		Map<String, Integer> kc = new HashMap<>();
		kc.put("royal titans", 419);
		LootTrackerRestore.Result result = restore(new ArrayList<>(),
			Arrays.asList(record("Branda the Fire Queen", 150, 995, 5), record("Eldric the Ice King", 140, 995, 7)), kc);
		MonsterTracker.Saved titans = find(result, "Royal Titans");
		assertEquals(419, titans.kills);
		assertEquals(12, titans.loot.get(0).quantity);
		assertEquals(1, result.monsters.size());
	}

	@Test
	public void combinesWithWhatsTrackedNowWithoutCountingTwice()
	{
		MonsterTracker tracker = new MonsterTracker();
		for (int i = 0; i < 20; i++)
		{
			tracker.recordKill("Cave horror", 1000L * i);
		}
		tracker.recordHit("Cave horror", 25, CombatStyle.MELEE, 5);
		LootTrackerRestore.Result result = restore(tracker.exportState(),
			Arrays.asList(record("Cave horror", 455)), Collections.emptyMap());
		MonsterTracker.Saved horror = find(result, "Cave horror");
		// The Loot Tracker also saw those 20 kills, so the larger count, not 475.
		assertEquals(455, horror.kills);
		assertEquals(435, result.kills);
		// What's been tracked since is kept.
		assertEquals(25, horror.biggestHit);
	}

	@Test
	public void keepsMoreKillsIfTheTrackerHasThem()
	{
		MonsterTracker tracker = new MonsterTracker();
		for (int i = 0; i < 30; i++)
		{
			tracker.recordKill("Cow", 1000L * i);
		}
		LootTrackerRestore.Result result = restore(tracker.exportState(),
			Arrays.asList(record("Cow", 10)), Collections.emptyMap());
		assertEquals(30, find(result, "Cow").kills);
		assertEquals(0, result.kills);
	}

	@Test
	public void bringsBackOnlyTheMonsterRemovedByAccident()
	{
		MonsterTracker tracker = new MonsterTracker();
		tracker.recordKill("Cow", 1);
		LootTrackerRestore.Result result = LootTrackerRestore.merge(tracker.exportState(),
			Arrays.asList(record("Cave horror", 455), record("Hill Giant", 186), record("Cow", 50)),
			Collections.emptyMap(), Collections.singleton("Cave horror"), RuneLiteAPI.GSON, ITEMS);
		assertEquals(455, find(result, "Cave horror").kills);
		// The others are left exactly as they are.
		assertEquals(null, find(result, "Hill Giant"));
		assertEquals(1, find(result, "Cow").kills);
		assertEquals(1, result.restored);
	}

	@Test
	public void restoresTheMonstersChosenFromTheList()
	{
		List<String> records = Arrays.asList(record("Cave horror", 455), record("Hill Giant", 186),
			record("Branda the Fire Queen", 151), record("Eldric the Ice King", 262));
		// The list shows one row per monster, Royal Titans as one.
		List<MonsterTracker.Saved> choices = LootTrackerRestore.read(records, Collections.emptyMap(), null, RuneLiteAPI.GSON, ITEMS);
		assertEquals(3, choices.size());

		LootTrackerRestore.Result result = LootTrackerRestore.merge(new ArrayList<>(), records, Collections.emptyMap(),
			new java.util.HashSet<>(Arrays.asList("Cave horror", "Royal Titans")), RuneLiteAPI.GSON, ITEMS);
		assertEquals(2, result.restored);
		assertEquals(413, find(result, "Royal Titans").kills);
		assertEquals(null, find(result, "Hill Giant"));
	}

	@Test
	public void aRestoredRareIsntCalledADryStreak()
	{
		// 413 loots with a rare among them: which kill it came on isn't known.
		LootTrackerRestore.Result result = restore(new ArrayList<>(),
			Arrays.asList(record("Branda the Fire Queen", 413, 995, 50_000, 30_000, 1)), Collections.emptyMap());
		MonsterTracker tracker = new MonsterTracker();
		tracker.importState(result.monsters);
		MonsterTracker.Snapshot titans = tracker.snapshot(LootPrice.GRAND_EXCHANGE, name -> false, 50).get(0);
		assertTrue(titans.isRareSinceRestore());
		assertEquals(0, titans.getKillsSinceRare());

		// It counts from the restore...
		for (int i = 0; i < 5; i++)
		{
			tracker.recordKill("Royal Titans", i);
		}
		titans = tracker.snapshot(LootPrice.GRAND_EXCHANGE, name -> false, 50).get(0);
		assertEquals(5, titans.getKillsSinceRare());

		// ...until a rare is seen again, which is back to normal.
		tracker.recordLoot("Royal Titans", Collections.singletonList(new MonsterTracker.Drop(30_000, "Rare", 1, 20_000)), 6);
		titans = tracker.snapshot(LootPrice.GRAND_EXCHANGE, name -> false, 50).get(0);
		assertFalse(titans.isRareSinceRestore());
		assertEquals(0, titans.getKillsSinceRare());
	}

	@Test
	public void noRareInTheRestoredLootStartsFromZero()
	{
		LootTrackerRestore.Result result = restore(new ArrayList<>(),
			Arrays.asList(record("Cow", 40, 526, 40)), Collections.emptyMap());
		MonsterTracker tracker = new MonsterTracker();
		tracker.importState(result.monsters);
		MonsterTracker.Snapshot cow = tracker.snapshot(LootPrice.GRAND_EXCHANGE, name -> false, 1_000).get(0);
		// 2.1.2: a streak whose start isn't known starts from 0, not the whole kill count.
		assertTrue(cow.isRareSinceRestore());
		assertEquals(0, cow.getKillsSinceRare());
	}

	@Test
	public void restoredStatsLoadBackIntoTheTracker()
	{
		LootTrackerRestore.Result result = restore(new ArrayList<>(),
			Arrays.asList(record("Hill Giant", 186, 532, 186), "not json"), Collections.emptyMap());
		MonsterTracker tracker = new MonsterTracker();
		// Saved and loaded the way the plugin does.
		String json = RuneLiteAPI.GSON.toJson(result.monsters);
		tracker.importState(Arrays.asList(RuneLiteAPI.GSON.fromJson(json, MonsterTracker.Saved[].class)));
		assertFalse(tracker.isEmpty());
		assertEquals(186, tracker.exportState().get(0).kills);
	}
}
