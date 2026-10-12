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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * 1.9.2: loot despawn alert, prayer drain per monster, casts left.
 */
public class Release192Test
{
	// ---- N1: loot despawn alert ------------------------------------------------------------

	private static Map<Integer, Long> loot(int itemId, long value)
	{
		Map<Integer, Long> m = new HashMap<>();
		m.put(itemId, value);
		return m;
	}

	@Test
	public void onlyMonsterLootIsTracked()
	{
		GroundLoot ground = new GroundLoot();
		Object bones = new Object();
		Object rune = new Object();
		Object dropped = new Object();
		ground.onSpawn(bones, 526, 50, 50, 0, 100, 300);
		ground.onSpawn(rune, 1319, 50, 50, 0, 100, 300);
		// Something the player dropped themselves, elsewhere.
		ground.onSpawn(dropped, 995, 52, 50, 0, 100, 300);
		Map<Integer, Long> drops = loot(526, 100);
		drops.put(1319, 37_000L);
		ground.onLoot(drops, 50, 50, 0, 100);
		ground.prune(103);

		GroundLoot.Status status = ground.status(103, 0);
		assertEquals(2, status.getItems());
		assertEquals(37_100, status.getValue());
		assertEquals(197, status.getTicksLeft());
		// The minimum value leaves the bones out.
		assertEquals(1, ground.status(103, 1000).getItems());
	}

	@Test
	public void pickedUpOrDespawnedLootIsForgotten()
	{
		GroundLoot ground = new GroundLoot();
		Object a = new Object();
		Object b = new Object();
		ground.onSpawn(a, 1319, 10, 10, 0, 100, 300);
		ground.onSpawn(b, 1319, 11, 10, 0, 150, 350);
		ground.onLoot(loot(1319, 37_000), 10, 10, 0, 100);
		ground.onLoot(loot(1319, 37_000), 11, 10, 0, 151);
		ground.onDespawn(a);
		assertEquals(200, ground.status(150, 0).getTicksLeft());
		ground.prune(350);
		assertNull(ground.status(350, 0));
	}

	@Test
	public void warnsOncePerPile()
	{
		GroundLoot ground = new GroundLoot();
		Object a = new Object();
		Object b = new Object();
		ground.onSpawn(a, 1319, 10, 10, 0, 100, 300);
		ground.onLoot(loot(1319, 37_000), 10, 10, 0, 100);
		ground.onSpawn(b, 1319, 12, 10, 0, 200, 400);
		ground.onLoot(loot(1319, 37_000), 12, 10, 0, 200);
		assertFalse(ground.shouldWarn(200, 34, 0));
		assertTrue(ground.shouldWarn(266, 34, 0));
		assertFalse("not again for the same pile", ground.shouldWarn(267, 34, 0));
		ground.onDespawn(a);
		assertFalse(ground.shouldWarn(370, 34, 0));
		// All picked up: the next pile warns again.
		ground.onDespawn(b);
		assertFalse(ground.shouldWarn(371, 34, 0));
		Object c = new Object();
		ground.onSpawn(c, 1319, 10, 10, 0, 400, 600);
		ground.onLoot(loot(1319, 37_000), 10, 10, 0, 400);
		assertTrue(ground.shouldWarn(570, 34, 0));
	}

	@Test
	public void unmatchedSpawnsExpire()
	{
		GroundLoot ground = new GroundLoot();
		Object a = new Object();
		ground.onSpawn(a, 1319, 10, 10, 0, 100, 300);
		ground.prune(100 + GroundLoot.MATCH_TICKS + 1);
		// Too late to be matched.
		ground.onLoot(loot(1319, 37_000), 10, 10, 0, 100 + GroundLoot.MATCH_TICKS + 1);
		assertNull(ground.status(104, 0));
	}

	// ---- N2: prayer drain -------------------------------------------------------------------

	@Test
	public void prayerDrainPerMonster()
	{
		PrayerDrain drain = new PrayerDrain();
		int tick = 1000;
		// 4 minutes (400 ticks) of fighting, losing a point every 4 ticks, 8 kills.
		for (int i = 0; i < 400; i++)
		{
			drain.onTick("Abyssal demon", i % 4 == 0 ? 1 : 0, true, tick + i);
			if (i % 50 == 49)
			{
				drain.onKill("Abyssal demon", tick + i);
			}
		}
		PrayerDrain.Rate rate = drain.rate("abyssal demon");
		assertNotNull(rate);
		assertEquals(100, rate.points);
		assertEquals(8, rate.kills);
		assertEquals(12.5, rate.pointsPerKill(), 1e-9);
		assertEquals(25, rate.pointsPerMinute(), 1e-9);
		// 70 Prayer: 7 + 17 = 24 points a dose, so just under a minute each.
		assertEquals(24, PrayerDrain.pointsPerDose(70));
		assertEquals(24 / 25.0, PrayerDrain.minutesPerDose(rate, 70), 1e-9);
		// 64 kills: 800 points = 34 doses = 9 potions.
		assertEquals(9, PrayerDrain.potionsFor(rate, 64, 70));
		assertSame(null, drain.rate("Gargoyle"));
	}

	@Test
	public void prayerNeedsEnoughFightingAndPrayerOn()
	{
		PrayerDrain drain = new PrayerDrain();
		for (int i = 0; i < 100; i++)
		{
			drain.onTick("Gargoyle", 1, true, i);
		}
		drain.onKill("Gargoyle", 99);
		assertNull("too little to trust", drain.rate("Gargoyle"));

		// Ticks without prayer on don't count, and kills long after the last drain don't either.
		PrayerDrain other = new PrayerDrain();
		for (int i = 0; i < 300; i++)
		{
			other.onTick("Gargoyle", 0, false, i);
		}
		other.onKill("Gargoyle", 299);
		assertNull(other.rate("Gargoyle"));
	}

	@Test
	public void prayerRateForATaskAndSaving()
	{
		PrayerDrain drain = new PrayerDrain();
		for (int i = 0; i < 300; i++)
		{
			drain.onTick("Greater abyssal demon", 1, true, i);
			drain.onTick("Abyssal demon", i % 2, true, i);
			if (i % 60 == 59)
			{
				drain.onKill("Abyssal demon", i);
			}
			if (i % 100 == 99)
			{
				drain.onKill("Greater abyssal demon", i);
			}
		}
		// The demon fought most on the task.
		assertEquals(5, drain.rateForTask("Abyssal demons").kills);

		Gson gson = new Gson();
		String json = gson.toJson(drain.exportState());
		PrayerDrain loaded = new PrayerDrain();
		loaded.importState(gson.fromJson(json, new TypeToken<Map<String, PrayerDrain.Rate>>()
		{
		}.getType()));
		assertEquals(150, loaded.rate("Abyssal demon").points);
		loaded.remove("Abyssal demon");
		assertNull(loaded.rate("Abyssal demon"));
	}

	// ---- M1: casts left -----------------------------------------------------------------------

	@Test
	public void castsFromInventoryRunes()
	{
		Map<RuneSupply.Rune, Long> have = new EnumMap<>(RuneSupply.Rune.class);
		RuneSupply.add(have, ItemID.DEATHRUNE, 400);
		RuneSupply.add(have, ItemID.BLOODRUNE, 300);
		RuneSupply.add(have, ItemID.WATERRUNE, 1200);
		RuneSupply.Result ice = RuneSupply.castsLeft(MagicMaxHit.Spell.ICE_BARRAGE, have, EnumSet.noneOf(RuneSupply.Rune.class));
		// Death runes: 400 / 4 = 100; blood 150; water 200.
		assertEquals(100, ice.getCasts());
		assertSame(RuneSupply.Rune.DEATH, ice.getLimitedBy());
	}

	@Test
	public void staffAndCombinationRunes()
	{
		assertEquals(EnumSet.of(RuneSupply.Rune.AIR), RuneSupply.unlimitedFrom("staff of air"));
		assertEquals(EnumSet.of(RuneSupply.Rune.AIR, RuneSupply.Rune.FIRE), RuneSupply.unlimitedFrom("mystic smoke staff"));
		assertEquals(EnumSet.of(RuneSupply.Rune.FIRE), RuneSupply.unlimitedFrom("tome of fire"));
		assertTrue(RuneSupply.unlimitedFrom("tome of fire (empty)").isEmpty());
		assertEquals(EnumSet.of(RuneSupply.Rune.WATER), RuneSupply.unlimitedFrom("kodai wand"));
		assertTrue(RuneSupply.unlimitedFrom("staff of the dead").isEmpty());
		assertTrue(RuneSupply.unlimitedFrom("abyssal whip").isEmpty());

		// Fire Surge with a smoke battlestaff: only wrath runes are needed.
		Map<RuneSupply.Rune, Long> have = new EnumMap<>(RuneSupply.Rune.class);
		RuneSupply.add(have, ItemID.WRATHRUNE, 250);
		Set<RuneSupply.Rune> smoke = RuneSupply.unlimitedFrom("smoke battlestaff");
		assertEquals(250, RuneSupply.castsLeft(MagicMaxHit.Spell.FIRE_SURGE, have, smoke).getCasts());

		// Smoke runes count as air and fire at once: Fire Blast needs 4 air and 5 fire.
		Map<RuneSupply.Rune, Long> combo = new EnumMap<>(RuneSupply.Rune.class);
		RuneSupply.add(combo, ItemID.SMOKERUNE, 500);
		RuneSupply.add(combo, ItemID.DEATHRUNE, 1000);
		assertEquals(100, RuneSupply.castsLeft(MagicMaxHit.Spell.FIRE_BLAST, combo, Collections.emptySet()).getCasts());
	}

	@Test
	public void unlimitedWhenEveryRuneIsCovered()
	{
		RuneSupply.Result r = RuneSupply.castsLeft(MagicMaxHit.Spell.WIND_STRIKE, new EnumMap<>(RuneSupply.Rune.class),
			EnumSet.of(RuneSupply.Rune.AIR, RuneSupply.Rune.MIND));
		assertTrue(r.isUnlimited());
		assertNull(r.getLimitedBy());
	}

	@Test
	public void everyCombatSpellHasACost()
	{
		for (MagicMaxHit.Spell spell : MagicMaxHit.Spell.values())
		{
			assertFalse(spell.name(), RuneSupply.costOf(spell).isEmpty());
		}
		assertEquals(ItemID.DEATHRUNE, RuneSupply.itemFor(RuneSupply.Rune.DEATH));
	}
}
