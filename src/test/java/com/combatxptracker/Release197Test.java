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
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.junit.Test;

/**
 * 1.9.7: max hit flash, attack uptime, boss personal bests.
 */
public class Release197Test
{
	// ---- Max hit flash -------------------------------------------------------------------------

	@Test
	public void maxHitChangeFlashesForAFewTicks()
	{
		MaxHitFlash flash = new MaxHitFlash();
		flash.update(CombatStyle.MELEE, 34, 100);
		assertEquals("first value isn't a change", 0, flash.change(100));
		flash.update(CombatStyle.MELEE, 37, 101);
		assertEquals(3, flash.change(101));
		assertEquals(3, flash.change(105));
		assertEquals(0, flash.change(106));
		flash.update(CombatStyle.MELEE, 36, 120);
		assertEquals(-1, flash.change(120));
		assertEquals("(+3)", MaxHitFlash.label(3));
		assertEquals("(-1)", MaxHitFlash.label(-1));
	}

	@Test
	public void switchingStyleDoesNotFlash()
	{
		MaxHitFlash flash = new MaxHitFlash();
		flash.update(CombatStyle.MELEE, 34, 100);
		flash.update(CombatStyle.RANGED, 41, 101);
		assertEquals(0, flash.change(101));
		flash.update(CombatStyle.RANGED, 41, 102);
		assertEquals(0, flash.change(102));
		// Unknown max hits never flash.
		flash.update(CombatStyle.RANGED, -1, 103);
		flash.update(CombatStyle.RANGED, 41, 104);
		assertEquals(0, flash.change(104));
	}

	// ---- Attack uptime -------------------------------------------------------------------------

	@Test
	public void perfectAttacksAreFullUptime()
	{
		AttackUptime uptime = new AttackUptime();
		for (int tick = 0; tick <= 100; tick += 4)
		{
			uptime.onAttack("Abyssal demon", tick, 4);
		}
		assertEquals(100, uptime.percent("abyssal demon"));
		assertEquals(100, uptime.totalPercent());
	}

	@Test
	public void lostTicksLowerUptime()
	{
		AttackUptime uptime = new AttackUptime();
		// A 4-tick weapon used every 5 ticks: 80%.
		for (int tick = 0; tick <= 100; tick += 5)
		{
			uptime.onAttack("Gargoyle", tick, 4);
		}
		assertEquals(80, uptime.percent("Gargoyle"));
		assertEquals(-1, uptime.percent("Kraken"));
	}

	@Test
	public void longBreaksAndShortFightsAreLeftOut()
	{
		AttackUptime uptime = new AttackUptime();
		uptime.onAttack("Vorkath", 0, 5);
		uptime.onAttack("Vorkath", 5, 5);
		assertEquals("too little to say", -1, uptime.percent("Vorkath"));
		// Banking for a minute between fights isn't lost uptime.
		for (int tick = 200; tick <= 300; tick += 5)
		{
			uptime.onAttack("Vorkath", tick, 5);
		}
		assertEquals(100, uptime.percent("Vorkath"));
		uptime.endFight();
		uptime.onAttack("Vorkath", 302, 5);
		assertEquals(100, uptime.percent("Vorkath"));
	}

	@Test
	public void uptimeSavesLoadsAndRemoves()
	{
		AttackUptime uptime = new AttackUptime();
		for (int tick = 0; tick <= 100; tick += 5)
		{
			uptime.onAttack("Gargoyle", tick, 4);
		}
		Gson gson = new Gson();
		AttackUptime loaded = new AttackUptime();
		loaded.importState(gson.fromJson(gson.toJson(uptime.exportState()), new TypeToken<Map<String, AttackUptime.Ticks>>()
		{
		}.getType()));
		assertEquals(80, loaded.percent("gargoyle"));
		assertEquals(80, loaded.totalPercent());
		loaded.remove("Gargoyle");
		assertEquals(-1, loaded.percent("gargoyle"));
		assertEquals(-1, loaded.totalPercent());
		assertTrue(loaded.isEmpty());
	}

	@Test
	public void timerReportsNewAttacksOnly()
	{
		AttackTimer timer = new AttackTimer();
		assertTrue(timer.onTick(10, true, 4));
		assertFalse(timer.onTick(11, true, 4));
		assertFalse(timer.onAttackSignal(11, 4));
		assertTrue(timer.onTick(14, true, 4));
		assertFalse(timer.onTick(18, false, 4));
		assertTrue(timer.onAttackSignal(19, 4));
	}

	// ---- Boss personal bests -------------------------------------------------------------------

	@Test
	public void durationMessagesAreRead()
	{
		BossTimes.Duration pb = BossTimes.parse("Fight duration: 1:23.40 (new personal best)");
		assertEquals(83.4, pb.seconds, 0.001);
		assertTrue(pb.newBest);

		BossTimes.Duration slower = BossTimes.parse("Fight duration: 1:25.00. Personal best: 1:20.40");
		assertEquals(85.0, slower.seconds, 0.001);
		assertFalse(slower.newBest);
		assertEquals(80.4, slower.previousBest, 0.001);

		BossTimes.Duration raid = BossTimes.parse("Team size: 3 players Duration: 1:02:03 Personal best: 58:10");
		assertEquals(3723, raid.seconds, 0.001);
		assertEquals(3490, raid.previousBest, 0.001);

		assertEquals(330.0, BossTimes.parse("Challenge duration: 5:30 (new personal best).").seconds, 0.001);
		assertNull(BossTimes.parse("Wave 3 completed! Wave duration: 0:44.40"));
		assertNull(BossTimes.parse("Your Vorkath kill count is: 124."));
		assertNull(BossTimes.parse("Total completion time: 25:00"));
	}

	@Test
	public void timesFormatLikeTheGame()
	{
		assertEquals("1:23.40", BossTimes.format(83.4));
		assertEquals("1:23", BossTimes.format(83));
		assertEquals("1:02:03", BossTimes.format(3723));
		assertEquals("0:59.40", BossTimes.format(59.4));
		assertEquals("1:12.00", BossTimes.format(72, true));
	}

	@Test
	public void personalBestsAreKept()
	{
		BossTimes times = new BossTimes();
		// First kill seen, slower than the game's record from before the plugin.
		assertEquals(-1, times.record("Vorkath", BossTimes.parse("Fight duration: 1:25.00. Personal best: 1:20.40")), 0.001);
		assertEquals("PB 1:20.40, last 1:25.00", BossTimes.label(times.get("vorkath")));
		// A new best: says what it beat.
		assertEquals(80.4, times.record("Vorkath", BossTimes.parse("Fight duration: 1:12.00 (new personal best)")), 0.001);
		assertEquals("PB 1:12", BossTimes.label(times.get("Vorkath")));
		// The very first kill is a best with nothing to beat.
		assertEquals(0, times.record("Zulrah", BossTimes.parse("Fight duration: 1:40.20 (new personal best)")), 0.001);
		assertNull(BossTimes.label(times.get("Kraken")));

		Gson gson = new Gson();
		BossTimes loaded = new BossTimes();
		loaded.importState(gson.fromJson(gson.toJson(times.exportState()), new TypeToken<Map<String, BossTimes.Times>>()
		{
		}.getType()));
		assertEquals(72.0, loaded.get("Vorkath").best, 0.001);
		loaded.remove("Vorkath");
		assertNull(loaded.get("Vorkath"));
	}

	@Test
	public void killCountNamesTheBoss()
	{
		assertEquals("Vorkath", BossFight.parseKillCountName("Your Vorkath kill count is: 124."));
		assertEquals("Theatre of Blood", BossFight.parseKillCountName("Your completed Theatre of Blood count is: 12."));
		assertNull(BossFight.parseKillCountName("Your Barrows chest count is: 50."));
		assertNull(BossFight.parseKillCountName("Fight duration: 1:23.40"));
	}

	@Test
	public void summaryIncludesUptime()
	{
		String text = SessionSummaryText.build(new java.util.EnumMap<>(net.runelite.api.Skill.class), new HitStats(),
			java.util.Collections.emptyList(), XpRateMode.SESSION, null, 87);
		assertTrue(text, text.contains("Attack uptime: 87%"));
	}
}
