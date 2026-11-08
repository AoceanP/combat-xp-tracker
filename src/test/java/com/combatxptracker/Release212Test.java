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
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 2.1.2: gold per hour over the whole trip, banking and walking included, live every 5 ticks;
 * and an attack timer that learns each weapon and style and follows weapon swaps.
 */
public class Release212Test
{
	private static final long MIN = 60_000L;

	private long now;

	private MonsterTracker tracker()
	{
		MonsterTracker t = new MonsterTracker();
		t.setClock(() -> now);
		return t;
	}

	/**
	 * A kill every 30 seconds for 10 minutes, each worth 50K: 1M in 10 minutes of fighting.
	 */
	private void fight(MonsterTracker t, String monster)
	{
		for (int i = 0; i <= 20; i++)
		{
			t.recordKill(monster, now);
			if (i > 0)
			{
				t.recordLoot(monster, Collections.singletonList(new MonsterTracker.Drop(1, "Loot", 1, 50_000)), now);
			}
			now += 30_000;
		}
		now -= 30_000;
	}

	private static MonsterTracker.Snapshot get(MonsterTracker t, String monster)
	{
		for (MonsterTracker.Snapshot s : t.snapshot(LootPrice.GRAND_EXCHANGE, name -> false))
		{
			if (s.getName().equals(monster))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void whileFightingBothRatesAgree()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		assertEquals(6_000_000, s.getTripGpPerHour(), 1);
		assertEquals(6_000_000, s.getFightGpPerHour(), 1);
		assertFalse(s.isAway());
	}

	@Test
	public void theTripRateFallsWhileYouBank()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		// 10 minutes banking: the clock keeps running, so the trip rate halves.
		now += 10 * MIN;
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		assertTrue(s.isAway());
		assertEquals(3_000_000, s.getTripGpPerHour(), 1);
		// Fighting only stays where it was.
		assertEquals(6_000_000, s.getFightGpPerHour(), 1);
	}

	@Test
	public void andClimbsAgainWithNewLoot()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		now += 10 * MIN;
		fight(t, "Vorkath");
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		assertFalse(s.isAway());
		// 2M over 30 minutes, banking included.
		assertEquals(4_000_000, s.getTripGpPerHour(), 1);
		assertEquals(6_000_000, s.getFightGpPerHour(), 1);
	}

	@Test
	public void aLongBreakEndsTheTrip()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		now += 45 * MIN;
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		// AFK for 45 minutes doesn't sink the rate: past 20 minutes it stops counting.
		assertFalse(s.isAway());
		assertEquals(6_000_000, s.getTripGpPerHour(), 1);
	}

	@Test
	public void movingOnToAnotherMonsterEndsTheTrip()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		now += MIN;
		fight(t, "Zulrah");
		now += 5 * MIN;
		// Vorkath's clock stopped when Zulrah started; Zulrah's is the one running.
		assertFalse(get(t, "Vorkath").isAway());
		assertTrue(get(t, "Zulrah").isAway());
		assertEquals("Zulrah", t.getCurrentTrip());
	}

	@Test
	public void loggingOutEndsTheTrip()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		t.endTrip();
		now += 10 * MIN;
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		assertFalse(s.isAway());
		assertEquals(6_000_000, s.getTripGpPerHour(), 1);
		assertNull(t.getCurrentTrip());
	}

	@Test
	public void theTripIsSavedAndLoaded()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		now += 10 * MIN;
		fight(t, "Vorkath");
		String json = RuneLiteAPI.GSON.toJson(t.exportState());
		MonsterTracker loaded = tracker();
		loaded.importState(Arrays.asList(RuneLiteAPI.GSON.fromJson(json, MonsterTracker.Saved[].class)));
		MonsterTracker.Snapshot s = get(loaded, "Vorkath");
		assertEquals(4_000_000, s.getTripGpPerHour(), 1);
		assertEquals(6_000_000, s.getFightGpPerHour(), 1);
	}

	@Test
	public void olderSavesStartFromTheirFightingTime()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		List<MonsterTracker.Saved> saved = t.exportState();
		saved.get(0).tripMillis = -1;
		saved.get(0).fightMillis = -1;
		MonsterTracker loaded = tracker();
		loaded.importState(saved);
		MonsterTracker.Snapshot s = get(loaded, "Vorkath");
		assertEquals(s.getGpPerHour(), s.getTripGpPerHour(), 1);
		assertEquals(s.getGpPerHour(), s.getFightGpPerHour(), 1);
	}

	@Test
	public void theSlayerTaskRateFallsWhileYouBankToo()
	{
		SlayerTaskSession session = new SlayerTaskSession();
		session.start("Abyssal demons");
		long t = 0;
		for (int i = 0; i <= 20; i++)
		{
			session.recordKill(t);
			if (i > 0)
			{
				session.recordLoot(50_000, 30_000, t);
			}
			t += 30_000;
		}
		t -= 30_000;
		assertEquals(6_000_000, session.getTripGpPerHour(LootPrice.GRAND_EXCHANGE, t, true), 1);
		assertEquals(3_000_000, session.getTripGpPerHour(LootPrice.GRAND_EXCHANGE, t + 10 * MIN, true), 1);
		assertTrue(session.isAway(t + 10 * MIN, true));
		// Logged out: the time away isn't counted.
		session.endTrip();
		assertEquals(6_000_000, session.getTripGpPerHour(LootPrice.GRAND_EXCHANGE, t + 10 * MIN, true), 1);
	}

	@Test
	public void overlayLineIsOffByDefault()
	{
		CombatXpTrackerConfig config = new CombatXpTrackerConfig()
		{
		};
		assertFalse(config.overlayGpPerHour());
	}

	// ---- attack timer ----

	private static final String BLOWPIPE_RAPID = AttackSpeeds.key(12926, "Rapid");

	@Test
	public void learnsAWeaponsRealSpeed()
	{
		AttackSpeeds speeds = new AttackSpeeds();
		// The item stats say 3; the blowpipe on Rapid really attacks every 2.
		assertEquals(3, speeds.speed(BLOWPIPE_RAPID, 3));
		speeds.observe(BLOWPIPE_RAPID, 2);
		assertEquals(3, speeds.speed(BLOWPIPE_RAPID, 3));
		speeds.observe(BLOWPIPE_RAPID, 2);
		assertEquals(2, speeds.speed(BLOWPIPE_RAPID, 3));
	}

	@Test
	public void slowGapsFromWalkingDontSlowItDown()
	{
		AttackSpeeds speeds = new AttackSpeeds();
		String key = AttackSpeeds.key(4151, "Lash");
		// Walking between attacks: gaps of 6 with a 4-tick whip.
		speeds.observe(key, 6);
		speeds.observe(key, 6);
		assertEquals(4, speeds.speed(key, 4));
		// One real attack at full speed wins.
		speeds.observe(key, 4);
		speeds.observe(key, 4);
		assertEquals(4, speeds.speed(key, 4));
	}

	@Test
	public void aSlowerSpeedNeedsToBeSeenOften()
	{
		AttackSpeeds speeds = new AttackSpeeds();
		String key = AttackSpeeds.key(27275, "Accurate");
		for (int i = 0; i < AttackSpeeds.CONFIRM_SLOWER - 1; i++)
		{
			speeds.observe(key, 5);
		}
		assertEquals(4, speeds.speed(key, 4));
		speeds.observe(key, 5);
		assertEquals(5, speeds.speed(key, 4));
	}

	@Test
	public void eachWeaponAndStyleIsSeparate()
	{
		AttackSpeeds speeds = new AttackSpeeds();
		String rapid = AttackSpeeds.key(12926, "Rapid");
		String accurate = AttackSpeeds.key(12926, "Accurate");
		speeds.observe(rapid, 2);
		speeds.observe(rapid, 2);
		assertEquals(2, speeds.speed(rapid, 3));
		assertEquals(3, speeds.speed(accurate, 3));
	}

	@Test
	public void impossibleGapsAreIgnored()
	{
		AttackSpeeds speeds = new AttackSpeeds();
		assertFalse(speeds.observe(BLOWPIPE_RAPID, 1));
		assertFalse(speeds.observe(BLOWPIPE_RAPID, AttackSpeeds.MAX_TICKS + 1));
		assertEquals(3, speeds.speed(BLOWPIPE_RAPID, 3));
	}

	@Test
	public void learnedSpeedsAreSavedAndLoaded()
	{
		AttackSpeeds speeds = new AttackSpeeds();
		speeds.observe(BLOWPIPE_RAPID, 2);
		speeds.observe(BLOWPIPE_RAPID, 2);
		String json = RuneLiteAPI.GSON.toJson(speeds.exportState());
		AttackSpeeds loaded = new AttackSpeeds();
		loaded.importState(RuneLiteAPI.GSON.fromJson(json, new com.google.gson.reflect.TypeToken<java.util.Map<String, AttackSpeeds.Learned>>()
		{
		}.getType()));
		assertEquals(2, loaded.speed(BLOWPIPE_RAPID, 3));
	}

	@Test
	public void swappingWeaponsKeepsTheCooldownOfTheWeaponThatAttacked()
	{
		AttackTimer timer = new AttackTimer();
		// A 5-tick scythe attack, then a swap to 4-tick claws.
		timer.onCertainAttack(100, 5);
		assertEquals(5, timer.ticksLeft(100, 4));
		assertEquals(3, timer.ticksLeft(102, 4));
		// The claws can't attack before the scythe's cooldown is over.
		assertFalse(timer.onTick(104, true, 4));
		assertTrue(timer.onTick(105, true, 4));
		// From the claws' attack on, it's their 4 ticks.
		assertEquals(4, timer.ticksLeft(105, 5));
	}

	@Test
	public void timerIsColouredByStyleByDefault()
	{
		CombatXpTrackerConfig config = new CombatXpTrackerConfig()
		{
		};
		assertTrue(config.attackTimerStyleColours());
		// 2.5: the timer itself is off for new players, like the main overlay.
		assertFalse(config.attackTimer());
	}

	// ---- kills since last unique ----

	private static MonsterTracker vorkath()
	{
		MonsterTracker t = new MonsterTracker();
		t.setUniqueTest((monster, item) -> "Vorkath".equals(monster)
			&& ("Vorkath's head".equals(item) || "Draconic visage".equals(item)));
		return t;
	}

	private static void kills(MonsterTracker t, int n)
	{
		for (int i = 0; i < n; i++)
		{
			t.recordKill("Vorkath", i);
		}
	}

	private static void drop(MonsterTracker t, String item)
	{
		t.recordLoot("Vorkath", Collections.singletonList(new MonsterTracker.Drop(1, item, 1, 50_000)), 0);
	}

	private static MonsterTracker.Snapshot vork(MonsterTracker t)
	{
		return t.snapshot().get(0);
	}

	@Test
	public void countsFromTheLastUnique()
	{
		MonsterTracker t = vorkath();
		kills(t, 50);
		drop(t, "Vorkath's head");
		assertEquals(0, vork(t).getKillsSinceUnique());
		kills(t, 64);
		assertEquals(64, vork(t).getKillsSinceUnique());
	}

	@Test
	public void aDuplicateUniqueStartsItAgainToo()
	{
		MonsterTracker t = vorkath();
		kills(t, 50);
		drop(t, "Vorkath's head");
		kills(t, 30);
		drop(t, "Vorkath's head");
		kills(t, 2);
		assertEquals(2, vork(t).getKillsSinceUnique());
	}

	@Test
	public void ordinaryLootDoesntCount()
	{
		MonsterTracker t = vorkath();
		kills(t, 50);
		drop(t, "Vorkath's head");
		kills(t, 10);
		drop(t, "Superior dragon bones");
		assertEquals(10, vork(t).getKillsSinceUnique());
	}

	@Test
	public void trackedFromTheFirstKillCountsEveryKill()
	{
		// Every kill was seen and none dropped a unique, so all 50 count.
		MonsterTracker t = vorkath();
		kills(t, 50);
		assertEquals(50, vork(t).getKillsSinceUnique());
	}

	@Test
	public void olderSavesStartBothStreaksFromZero()
	{
		// Saved before 2.1.2 with no rare or unique on record: don't start at the whole kill count.
		MonsterTracker t = new MonsterTracker();
		kills(t, 419);
		List<MonsterTracker.Saved> saved = t.exportState();
		saved.get(0).tripMillis = -1;
		saved.get(0).fightMillis = -1;
		saved.get(0).lastUniqueKill = -1;
		saved.get(0).restoredAt = 0;
		MonsterTracker loaded = vorkath();
		loaded.importState(saved);
		MonsterTracker.Snapshot s = loaded.snapshot(LootPrice.GRAND_EXCHANGE, name -> false, 150_000).get(0);
		assertEquals(0, s.getKillsSinceUnique());
		assertEquals(0, s.getKillsSinceRare());
		kills(loaded, 3);
		s = loaded.snapshot(LootPrice.GRAND_EXCHANGE, name -> false, 150_000).get(0);
		assertEquals(3, s.getKillsSinceUnique());
		assertEquals(3, s.getKillsSinceRare());
		// The next unique starts it again from 0.
		drop(loaded, "Vorkath's head");
		assertEquals(0, vork(loaded).getKillsSinceUnique());
	}

	@Test
	public void olderSavesFindTheLastUniqueFromValuableDrops()
	{
		MonsterTracker t = new MonsterTracker();
		kills(t, 40);
		t.recordLoot("Vorkath", Collections.singletonList(new MonsterTracker.Drop(11286, "Draconic visage", 1, 2_000_000)), 0);
		kills(t, 25);
		List<MonsterTracker.Saved> saved = t.exportState();
		saved.get(0).lastUniqueKill = -1;
		MonsterTracker loaded = vorkath();
		loaded.importState(saved);
		assertEquals(25, vork(loaded).getKillsSinceUnique());
	}

	@Test
	public void restoredBossesCountFromTheRestore()
	{
		LootTrackerRestore.Result result = LootTrackerRestore.merge(new java.util.ArrayList<>(),
			Collections.singletonList("{\"type\":\"NPC\",\"name\":\"Vorkath\",\"kills\":300,\"drops\":[1,1]}"),
			Collections.emptyMap(), null, RuneLiteAPI.GSON, new LootTrackerRestore.Items()
			{
				public String name(int id)
				{
					return "Vorkath's head";
				}

				public long price(int id)
				{
					return 50_000;
				}

				public long haPrice(int id)
				{
					return 0;
				}
			});
		MonsterTracker t = vorkath();
		t.importState(result.monsters);
		assertTrue(vork(t).isUniqueSinceRestore());
		assertEquals(0, vork(t).getKillsSinceUnique());
		kills(t, 7);
		assertEquals(7, vork(t).getKillsSinceUnique());
		drop(t, "Draconic visage");
		assertFalse(vork(t).isUniqueSinceRestore());
		assertEquals(0, vork(t).getKillsSinceUnique());
	}

	// ---- rare drop value ----

	@Test
	public void rareDropsStartAt150k()
	{
		CombatXpTrackerConfig config = new CombatXpTrackerConfig()
		{
		};
		assertEquals(150_000, config.rareDropValue());
	}

	@Test
	public void playersOnTheOld1mDefaultMoveTo150k()
	{
		assertEquals(150_000, CombatXpTrackerPlugin.migratedRareDropValue(1_000_000));
		// A value the player picked is kept.
		assertEquals(500_000, CombatXpTrackerPlugin.migratedRareDropValue(500_000));
		assertEquals(5_000_000, CombatXpTrackerPlugin.migratedRareDropValue(5_000_000));
	}

	// ---- per-hour rates leave restored kills and loot out ----

	private static final LootTrackerRestore.Items PRICED = new LootTrackerRestore.Items()
	{
		public String name(int id)
		{
			return "Loot";
		}

		public long price(int id)
		{
			return 50_000;
		}

		public long haPrice(int id)
		{
			return 0;
		}
	};

	private static String lootRecord(int kills, int quantity)
	{
		return "{\"type\":\"NPC\",\"name\":\"Vorkath\",\"kills\":" + kills + ",\"drops\":[1," + quantity + "]}";
	}

	@Test
	public void restoredKillsAndLootDontInflateRates()
	{
		// 10 minutes tracked since the wipe: 21 kills, 1M of loot, so 6M/hr and 126 kills/hr.
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		// The Loot Tracker has 420 kills and 400 of that item, including those.
		LootTrackerRestore.Result result = LootTrackerRestore.merge(t.exportState(),
			Collections.singletonList(lootRecord(420, 400)), Collections.emptyMap(), null, RuneLiteAPI.GSON, PRICED);
		MonsterTracker restored = tracker();
		restored.importState(result.monsters);
		MonsterTracker.Snapshot s = get(restored, "Vorkath");
		assertEquals(420, s.getKills());
		assertEquals(20_000_000, s.getLootValue());
		// Rates still come from the 21 timed kills only.
		assertEquals(126, s.getKillsPerHour(), 0.5);
		assertEquals(6_000_000, s.getFightGpPerHour(), 1);
	}

	@Test
	public void aMonsterOnlyTheLootTrackerKnewHasNoRateYet()
	{
		LootTrackerRestore.Result result = LootTrackerRestore.merge(new java.util.ArrayList<>(),
			Collections.singletonList(lootRecord(300, 300)), Collections.emptyMap(), null, RuneLiteAPI.GSON, PRICED);
		MonsterTracker t = tracker();
		t.importState(result.monsters);
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		assertEquals(300, s.getKills());
		assertEquals(-1, s.getKillsPerHour(), 0);
		// The first minute of new kills gives it a rate from those alone.
		fight(t, "Vorkath");
		s = get(t, "Vorkath");
		assertEquals(126, s.getKillsPerHour(), 0.5);
	}

	@Test
	public void startingRatesAgainKeepsTheTotals()
	{
		MonsterTracker t = tracker();
		fight(t, "Vorkath");
		t.startRatesAgain();
		MonsterTracker.Snapshot s = get(t, "Vorkath");
		assertEquals(21, s.getKills());
		assertEquals(1_000_000, s.getLootValue());
		assertEquals(-1, s.getKillsPerHour(), 0);
	}

	@Test
	public void theUpgradeIsSavedSoItHappensOnce()
	{
		// An old save: no 2.1.2 fields.
		MonsterTracker t = vorkath();
		kills(t, 419);
		List<MonsterTracker.Saved> old = t.exportState();
		old.get(0).tripMillis = -1;
		old.get(0).untimedKills = -1;
		old.get(0).restoredAt = 0;
		MonsterTracker first = vorkath();
		first.importState(old);
		assertTrue(first.takeUpgraded());
		kills(first, 5);
		// What the plugin saves after upgrading, then loads at the next login.
		MonsterTracker second = vorkath();
		second.importState(first.exportState());
		assertFalse(second.takeUpgraded());
		// The streak kept counting instead of starting from 0 again.
		assertEquals(5, vork(second).getKillsSinceUnique());
	}

	// ---- bundled collection log uniques ----

	private static BossUniques bundledUniques()
	{
		return new BossUniques(RuneLiteAPI.GSON.fromJson(new java.io.InputStreamReader(
			CombatXpTrackerPlugin.class.getResourceAsStream("collection_uniques.json"), java.nio.charset.StandardCharsets.UTF_8),
			new com.google.gson.reflect.TypeToken<java.util.Map<String, List<String>>>()
			{
			}.getType()));
	}

	@Test
	public void royalTitansHaveUniquesWithoutOpeningTheCollectionLog()
	{
		BossUniques uniques = bundledUniques();
		assertTrue(uniques.hasUniques("Royal Titans"));
		assertTrue(uniques.isUnique("Royal Titans", "Fire element staff crown"));
		assertTrue(uniques.isUnique("Royal Titans", "Bran"));
		// The drop is called "Giantsoul amulet"; the collection log says "(uncharged)".
		assertTrue(uniques.isUnique("Royal Titans", "Giantsoul amulet"));
		// Desiccated pages drop about 1 in 14 kills: not a unique.
		assertFalse(uniques.isUnique("Royal Titans", "Desiccated page"));
	}

	@Test
	public void onlyCollectionLogItemsAreUniques()
	{
		BossUniques uniques = bundledUniques();
		assertTrue(uniques.isUnique("Vorkath", "Jar of decay"));
		assertTrue(uniques.isUnique("Vorkath", "Vorkath's head"));
		// A rare drop, but not on the collection log.
		assertFalse(uniques.isUnique("Vorkath", "Dragon battleaxe"));
		assertFalse(uniques.isUnique("Vorkath", "Superior dragon bones"));
	}

	@Test
	public void sharedPagesCountForEachBoss()
	{
		BossUniques uniques = bundledUniques();
		assertTrue(uniques.isUnique("Dagannoth Rex", "Berserker ring"));
		assertTrue(uniques.hasUniques("Callisto"));
		assertFalse(uniques.hasUniques("Cave horror"));
	}
}
