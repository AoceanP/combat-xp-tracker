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
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.Experience;
import org.junit.Test;

/**
 * 1.6.0: session XP fix, actions left, boss groups, loot prices, ignored items.
 */
public class Release160Test
{
	private static final int WINDOW = 300;

	// ---- Session XP bug -------------------------------------------------------

	/**
	 * The reported bug: "Session XP 80.71M". While logging in the client can report 0 XP,
	 * then the real value arrives and looked like a gain of the whole lifetime XP.
	 */
	@Test
	public void zeroWhileLoggingInIsNotCountedAsSessionXp()
	{
		SkillProgress p = new SkillProgress();
		assertTrue(p.recordXp(0, 0, WINDOW));
		// The real value arrives: an impossible jump, so it becomes the new start.
		assertFalse(p.recordXp(13_034_431, 600, WINDOW));
		assertEquals(0, p.getSessionXpGained());

		// Real training afterwards still counts.
		assertTrue(p.recordXp(13_035_431, 60_000, WINDOW));
		assertEquals(1_000, p.getSessionXpGained());
	}

	@Test
	public void zeroThenLoginBaselineIsNotCountedEither()
	{
		SkillProgress p = new SkillProgress();
		p.recordXp(0, 0, WINDOW);
		// The plugin's login baseline with the real XP.
		p.resetBaseline(40_000_000, 600);
		assertEquals(0, p.getSessionXpGained());
		assertEquals(40_000_000, p.getCurrentXp());
	}

	@Test
	public void relogKeepsTheSessionGoing()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(1_000_000, 0);
		p.recordXp(1_050_000, 60_000, WINDOW);
		p.reset();
		p.resetBaseline(1_050_000, 120_000);
		p.recordXp(1_060_000, 180_000, WINDOW);
		assertEquals(60_000, p.getSessionXpGained());
	}

	// ---- Actions left ---------------------------------------------------------

	@Test
	public void actionsLeftUsesAverageXpPerDrop()
	{
		SkillProgress p = new SkillProgress();
		int start = Experience.getXpForLevel(98);
		p.resetBaseline(start, 0);
		p.setGoal(Goal.ofLevel(99), start);
		assertEquals(-1, p.getActionsLeftToGoal());

		// Four drops of 500 xp each.
		for (int i = 1; i <= 4; i++)
		{
			p.recordXp(start + i * 500, i * 1_000, WINDOW);
		}
		int remaining = p.getXpRemainingToGoal();
		assertEquals((int) Math.ceil(remaining / 500.0), p.getActionsLeftToGoal());
	}

	@Test
	public void actionsLeftNeedsAFewDropsFirst()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(0, 0);
		p.setGoal(Goal.ofLevel(10), 0);
		p.recordXp(100, 1_000, WINDOW);
		p.recordXp(200, 2_000, WINDOW);
		assertEquals(-1, p.getActionsLeftToGoal());
	}

	// ---- Boss groups ----------------------------------------------------------

	@Test
	public void bossGroups()
	{
		assertEquals("Royal Titans", BossGroups.trackedName("Branda the Fire Queen"));
		assertEquals("Royal Titans", BossGroups.trackedName("Eldric the Ice King"));
		assertEquals("Grotesque Guardians", BossGroups.trackedName("Dusk"));
		assertEquals("Barrows brothers", BossGroups.trackedName("Dharok the Wretched"));
		assertEquals("Abyssal demon", BossGroups.trackedName("Abyssal demon"));
		assertNull(BossGroups.trackedName(null));

		// Nex's minions are part of her card but don't count as kills.
		assertEquals("Nex", BossGroups.trackedName("Fumus"));
		assertFalse(BossGroups.countsAsKill("Fumus"));
		assertTrue(BossGroups.countsAsKill("Nex"));
		assertTrue(BossGroups.countsAsKill("Abyssal demon"));

		// Both Titans die in one fight, so they're one kill.
		assertTrue(BossGroups.ROYAL_TITANS.getSameKillTicks() > 0);
	}

	@Test
	public void oldSeparateTitanCardsMergeIntoOne()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Branda the Fire Queen", 26, CombatStyle.RANGED, 0);
		t.recordKill("Branda the Fire Queen", 60_000);
		t.recordHit("Eldric the Ice King", 21, CombatStyle.MELEE, 30_000);
		t.recordKill("Eldric the Ice King", 60_000);
		t.recordHit("Cow", 3, CombatStyle.MELEE, 1);

		t.rename(BossGroups::trackedName);

		List<MonsterTracker.Snapshot> all = t.snapshot();
		assertEquals(2, all.size());
		MonsterTracker.Snapshot titans = all.stream().filter(s -> s.getName().equals("Royal Titans")).findFirst().get();
		assertEquals(2, titans.getHits());
		assertEquals(2, titans.getKills());
		assertEquals(26, titans.getBiggestHit());
		assertEquals(CombatStyle.RANGED, titans.getBiggestHitStyle());
		assertEquals(Integer.valueOf(21), titans.getBiggestByStyle().get(CombatStyle.MELEE));
	}

	// ---- Loot prices and ignored items ------------------------------------------

	private static MonsterTracker withLoot()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Black dragon", 0);
		t.recordLoot("Black dragon", Arrays.asList(
			new MonsterTracker.Drop(536, "Dragon bones", 1, 2_500, 0),
			new MonsterTracker.Drop(1747, "Black dragonhide", 1, 1_700, 36),
			new MonsterTracker.Drop(1319, "Rune 2h sword", 1, 38_000, 38_400)), 0);
		return t;
	}

	@Test
	public void lootCanBeValuedAtHighAlchemy()
	{
		MonsterTracker t = withLoot();
		Predicate<String> none = name -> false;
		assertEquals(2_500 + 1_700 + 38_000, t.snapshot(LootPrice.GRAND_EXCHANGE, none).get(0).getLootValue());
		assertEquals(36 + 38_400, t.snapshot(LootPrice.HIGH_ALCHEMY, none).get(0).getLootValue());
		// Sorted by the chosen price: at High Alchemy the 2h sword is still first.
		assertEquals("Rune 2h sword", t.snapshot(LootPrice.HIGH_ALCHEMY, none).get(0).getLoot().get(0).getName());
	}

	@Test
	public void ignoredItemsAreLeftOutOfLootAndValue()
	{
		MonsterTracker t = withLoot();
		MonsterTracker.Snapshot s = t.snapshot(LootPrice.GRAND_EXCHANGE, MonsterTracker.itemMatcher("dragon BONES")).get(0);
		assertEquals(2, s.getLoot().size());
		assertEquals(1_700 + 38_000, s.getLootValue());
	}

	@Test
	public void ignoreWildcards()
	{
		Predicate<String> m = MonsterTracker.itemMatcher("*bones, rune*, ash*s");
		assertTrue(m.test("Dragon bones"));
		assertTrue(m.test("Bones"));
		assertTrue(m.test("Rune 2h sword"));
		assertTrue(m.test("Ashes"));
		assertFalse(m.test("Black dragonhide"));
		assertFalse(m.test("Bones to peaches"));
		assertFalse(m.test(null));
		// Regex characters in names are taken literally.
		assertFalse(MonsterTracker.itemMatcher("a.c").test("abc"));
		assertTrue(MonsterTracker.itemMatcher("").test("anything") == false);
	}

	@Test
	public void highAlchValuesSurviveSaveAndAreFilledInForOldSaves()
	{
		Gson gson = new GsonBuilder().create();
		MonsterTracker t = withLoot();
		MonsterTracker loaded = new MonsterTracker();
		loaded.importState(Arrays.asList(gson.fromJson(gson.toJson(t.exportState()), MonsterTracker.Saved[].class)));
		assertEquals(36 + 38_400, loaded.snapshot(LootPrice.HIGH_ALCHEMY, n -> false).get(0).getLootValue());

		// A 1.5.x save had no HA values: they get filled in on load.
		MonsterTracker old = new MonsterTracker();
		old.recordLoot("Black dragon", Collections.singletonList(new MonsterTracker.Drop(1319, "Rune 2h sword", 1, 38_000)), 0);
		assertEquals(0, old.snapshot(LootPrice.HIGH_ALCHEMY, n -> false).get(0).getLootValue());
		old.fillMissingHaPrices(id -> id == 1319 ? 38_400 : 0);
		assertEquals(38_400, old.snapshot(LootPrice.HIGH_ALCHEMY, n -> false).get(0).getLootValue());
	}
}
