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
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * 1.7.0: special attacks, kills left, dry streak, rare drops, pinning, task summary.
 */
public class Release170Test
{
	private static final long MINUTE = 60_000L;
	private static final Predicate<String> NONE = name -> false;

	// ---- Special attacks (OSRS Wiki "Maximum melee hit" multipliers) ----------------

	@Test
	public void specMultipliers()
	{
		// Base max hit 52
		assertEquals(71, SpecialAttack.ARMADYL_GODSWORD.maxHit(52, 0));   // 52 x 1.375 = 71.5
		assertEquals(62, SpecialAttack.BANDOS_GODSWORD.maxHit(52, 0));    // 52 x 1.21  = 62.92
		assertEquals(57, SpecialAttack.ZAMORAK_GODSWORD.maxHit(52, 0));   // 52 x 1.1   = 57.2
		assertEquals(78, SpecialAttack.DRAGON_WARHAMMER.maxHit(52, 0));   // 52 x 1.5
		assertEquals(65, SpecialAttack.DRAGON_LONGSWORD.maxHit(52, 0));   // 52 x 1.25
		assertEquals(59, SpecialAttack.DRAGON_DAGGER.maxHit(52, 0));      // 52 x 1.15 = 59.8
		assertEquals(44, SpecialAttack.ABYSSAL_DAGGER.maxHit(52, 0));     // 52 x 0.85 = 44.2
		assertEquals(2, SpecialAttack.DRAGON_DAGGER.getHits());
		assertEquals(2, SpecialAttack.ABYSSAL_DAGGER.getHits());
	}

	@Test
	public void bludgeonScalesWithMissingPrayer()
	{
		// 1 + 0.005 x missing: 0 missing = x1, 50 missing = x1.25, 99 missing = x1.495
		assertEquals(52, SpecialAttack.ABYSSAL_BLUDGEON.maxHit(52, 0));
		assertEquals(65, SpecialAttack.ABYSSAL_BLUDGEON.maxHit(52, 50));
		assertEquals(77, SpecialAttack.ABYSSAL_BLUDGEON.maxHit(52, 99));
	}

	@Test
	public void specWeaponNames()
	{
		assertEquals(SpecialAttack.DRAGON_DAGGER, SpecialAttack.fromWeaponName("Dragon dagger(p++)"));
		assertEquals(SpecialAttack.ARMADYL_GODSWORD, SpecialAttack.fromWeaponName("Armadyl godsword (or)"));
		assertEquals(SpecialAttack.SARADOMINS_BLESSED_SWORD, SpecialAttack.fromWeaponName("Saradomin's blessed sword"));
		assertEquals(SpecialAttack.SARADOMIN_GODSWORD, SpecialAttack.fromWeaponName("Saradomin godsword"));
		// Left out on purpose: its spec adds magic damage instead of a multiplier.
		assertNull(SpecialAttack.fromWeaponName("Saradomin sword"));
		assertNull(SpecialAttack.fromWeaponName("Abyssal whip"));
		assertNull(SpecialAttack.fromWeaponName(null));
	}

	@Test
	public void specIsCappedAt200()
	{
		assertEquals(200, SpecialAttack.DRAGON_WARHAMMER.maxHit(180, 0));
	}

	// ---- Kills left (OSRS Wiki "Combat" XP rates) --------------------------------

	private static CombatStyle.AttackStyle style(String name)
	{
		return CombatStyle.AttackStyle.fromName(name);
	}

	@Test
	public void xpPerKillByStyle()
	{
		int hp = 150; // Abyssal demon
		assertEquals(600, KillXp.perKill(Skill.STRENGTH, style("Aggressive"), hp), 1e-9);
		assertEquals(-1, KillXp.perKill(Skill.ATTACK, style("Aggressive"), hp), 1e-9);
		assertEquals(200, KillXp.perKill(Skill.ATTACK, style("Controlled"), hp), 1e-9);
		assertEquals(200, KillXp.perKill(Skill.DEFENCE, style("Controlled"), hp), 1e-9);
		assertEquals(600, KillXp.perKill(Skill.RANGED, style("Ranging"), hp), 1e-9);
		assertEquals(300, KillXp.perKill(Skill.RANGED, style("Longrange"), hp), 1e-9);
		assertEquals(300, KillXp.perKill(Skill.DEFENCE, style("Longrange"), hp), 1e-9);
		// Hitpoints and Slayer don't depend on the style.
		assertEquals(200, KillXp.perKill(Skill.HITPOINTS, null, hp), 1e-9);
		assertEquals(150, KillXp.perKill(Skill.SLAYER, null, hp), 1e-9);
		// Magic also earns XP per cast, which hitpoints can't predict.
		assertEquals(-1, KillXp.perKill(Skill.MAGIC, style("Casting"), hp), 1e-9);
		assertEquals(-1, KillXp.perKill(Skill.STRENGTH, style("Aggressive"), 0), 1e-9);
	}

	@Test
	public void killsLeftRoundsUp()
	{
		assertEquals(10, KillXp.killsLeft(6_000, 600));
		assertEquals(11, KillXp.killsLeft(6_001, 600));
		assertEquals(-1, KillXp.killsLeft(6_000, -1));
	}

	// ---- Dry streak and rare drops -----------------------------------------------------

	private static MonsterTracker fiveKillsWithWhipOnThird()
	{
		MonsterTracker t = new MonsterTracker();
		for (int kill = 1; kill <= 5; kill++)
		{
			t.recordKill("Abyssal demon", kill * MINUTE);
			if (kill == 3)
			{
				t.recordLoot("Abyssal demon", Collections.singletonList(
					new MonsterTracker.Drop(4151, "Abyssal whip", 1, 1_480_000, 72_000)), kill * MINUTE);
			}
			t.recordLoot("Abyssal demon", Collections.singletonList(
				new MonsterTracker.Drop(995, "Coins", 400_000, 1, 1)), kill * MINUTE);
		}
		return t;
	}

	@Test
	public void drystreakCountsKillsSinceTheLastRareDrop()
	{
		MonsterTracker t = fiveKillsWithWhipOnThird();
		MonsterTracker.Snapshot s = t.snapshot(LootPrice.GRAND_EXCHANGE, NONE, 1_000_000).get(0);
		assertTrue(s.hasEverDroppedRare());
		assertEquals(2, s.getKillsSinceRare());

		// With a 2M threshold nothing counts: dry for all 5 kills.
		MonsterTracker.Snapshot strict = t.snapshot(LootPrice.GRAND_EXCHANGE, NONE, 2_000_000).get(0);
		assertFalse(strict.hasEverDroppedRare());
		assertEquals(5, strict.getKillsSinceRare());

		// At High Alchemy the whip is only 72K, so it isn't rare at 1M either.
		assertFalse(t.snapshot(LootPrice.HIGH_ALCHEMY, NONE, 1_000_000).get(0).hasEverDroppedRare());

		// An ignored item doesn't end the dry streak.
		assertFalse(t.snapshot(LootPrice.GRAND_EXCHANGE, MonsterTracker.itemMatcher("abyssal whip"), 1_000_000)
			.get(0).hasEverDroppedRare());
	}

	@Test
	public void rareDropsAreFlaggedAndSortedFirst()
	{
		MonsterTracker t = fiveKillsWithWhipOnThird();
		List<MonsterTracker.LootLine> loot = t.snapshot(LootPrice.GRAND_EXCHANGE, NONE, 1_000_000).get(0).getLoot();
		// 2M in coins is worth more in total, but the whip is the rare one.
		assertEquals("Abyssal whip", loot.get(0).getName());
		assertTrue(loot.get(0).isRare());
		assertFalse(loot.get(1).isRare());
	}

	@Test
	public void rareDropsSurviveSaving()
	{
		Gson gson = new GsonBuilder().create();
		MonsterTracker loaded = new MonsterTracker();
		loaded.importState(Arrays.asList(gson.fromJson(gson.toJson(fiveKillsWithWhipOnThird().exportState()),
			MonsterTracker.Saved[].class)));
		assertEquals(2, loaded.snapshot(LootPrice.GRAND_EXCHANGE, NONE, 1_000_000).get(0).getKillsSinceRare());
	}

	@Test
	public void mergingShiftsKillNumbers()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Branda the Fire Queen", 0);
		t.recordKill("Branda the Fire Queen", 1);
		t.recordKill("Eldric the Ice King", 2);
		t.recordLoot("Eldric the Ice King", Collections.singletonList(
			new MonsterTracker.Drop(1, "Twinflame staff", 1, 5_000_000, 1)), 2);
		t.recordKill("Eldric the Ice King", 3);
		t.rename(BossGroups::trackedName);

		MonsterTracker.Snapshot s = t.snapshot(LootPrice.GRAND_EXCHANGE, NONE, 1_000_000).get(0);
		assertEquals(4, s.getKills());
		// The staff was Eldric's kill 1, which is kill 3 after Branda's 2: one kill since.
		assertEquals(1, s.getKillsSinceRare());
	}

	// ---- Pinning -------------------------------------------------------------------

	@Test
	public void pinnedMonstersComeFirst()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Cow", 30);
		t.recordKill("Goblin", 20);
		t.recordKill("Zulrah", 10);
		List<MonsterTracker.Snapshot> view = MonsterTracker.view(t.snapshot(), MonsterSort.RECENT,
			Collections.emptySet(), MonsterTracker.parseNames("zulrah"));
		assertEquals("Zulrah", view.get(0).getName());
		assertEquals("Cow", view.get(1).getName());
		assertEquals("Goblin", view.get(2).getName());
	}

	// ---- Slayer task summary -----------------------------------------------------------

	@Test
	public void taskSessionMatchesMonstersAndGroups()
	{
		SlayerTaskSession task = new SlayerTaskSession();
		assertFalse(task.isTaskMonster("Abyssal demon"));
		task.start("Abyssal demons");
		assertTrue(task.isTaskMonster("Abyssal demon"));
		assertTrue(task.isTaskMonster("Greater abyssal demon"));
		assertFalse(task.isTaskMonster("Cow"));

		task.start("Royal Titans");
		assertTrue(task.isTaskMonster("Branda the Fire Queen"));
	}

	@Test
	public void taskSummaryText()
	{
		SlayerTaskSession task = new SlayerTaskSession();
		task.start("Abyssal demons");
		task.recordHit(30, 0);
		task.recordKill(MINUTE);
		task.recordHit(47, MINUTE);
		task.recordKill(2 * MINUTE);
		task.recordLoot(1_500_000, 72_000, 2 * MINUTE);
		task.recordKill(2 * MINUTE);
		task.recordLoot(1_500_000, 72_000, 2 * MINUTE);

		assertEquals("Abyssal demons task complete: 3 kills in 2m, 3M gp (90M gp/hr), biggest hit 47.",
			task.summary(LootPrice.GRAND_EXCHANGE));
		assertTrue(task.summary(LootPrice.HIGH_ALCHEMY).contains("144K gp"));
	}

	@Test
	public void newTaskStartsFresh()
	{
		SlayerTaskSession task = new SlayerTaskSession();
		task.start("Abyssal demons");
		task.recordKill(0);
		task.start("Black dragons");
		assertEquals(0, task.getKills());
		assertEquals("Black dragons", task.getTask());
		task.clear();
		assertFalse(task.isActive());
	}
}
