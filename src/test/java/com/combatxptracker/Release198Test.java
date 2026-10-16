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
import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * 1.9.8: attack timer for melee and magic, accuracy and DPS, target card, kills to the next
 * combat level.
 */
public class Release198Test
{
	private static final int WHIP = 1658;
	private static final int WHIP_BLOCK = 1659;
	private static final int CAST = 1162;
	private static final Set<Integer> NON_ATTACKS = Set.of(829, 424, 1156);

	private static AttackDetector.Result tick(AttackDetector d, int tick, int animation, int frame)
	{
		return d.onTick(tick, animation, frame, true);
	}

	// ---- Attack detection ----------------------------------------------------------------------

	@Test
	public void aStillPlayingAnimationIsNotANewAttack()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		assertEquals(AttackDetector.Result.LIKELY, tick(d, 100, CAST, 0));
		// A long cast animation still running on the next ticks: not new attacks.
		assertEquals(AttackDetector.Result.NONE, tick(d, 101, CAST, 8));
		assertEquals(AttackDetector.Result.NONE, tick(d, 102, CAST, 16));
		assertEquals(AttackDetector.Result.NONE, tick(d, 105, CAST, 40));
		// It restarted: the frame went back.
		assertEquals(AttackDetector.Result.LIKELY, tick(d, 106, CAST, 2));
	}

	@Test
	public void theGameSettingTheSameAnimationAgainIsAnAttack()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		tick(d, 100, WHIP, 0);
		d.onAnimationSet();
		assertEquals(AttackDetector.Result.LIKELY, tick(d, 104, WHIP, 30));
	}

	@Test
	public void aBlockWhenHitIsNotAnAttack()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		d.onHitTaken();
		assertEquals(AttackDetector.Result.NONE, tick(d, 100, WHIP_BLOCK, 0));
		// Known blocks never count, hit or not.
		assertEquals(AttackDetector.Result.NONE, tick(d, 101, 424, 0));
		// Eating with a target isn't an attack.
		assertEquals(AttackDetector.Result.NONE, tick(d, 102, 829, 0));
	}

	@Test
	public void learnedAttacksCountEvenWhenHit()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		// The first whip attack lands: the XP drop teaches its animation.
		d.onXpDrop();
		assertEquals(AttackDetector.Result.CERTAIN, tick(d, 100, WHIP, 0));
		// Next attack, the same tick the monster hits back.
		d.onAnimationSet();
		d.onHitTaken();
		assertEquals(AttackDetector.Result.CERTAIN, tick(d, 104, WHIP, 0));
		// A block on its own is still ignored.
		d.onHitTaken();
		assertEquals(AttackDetector.Result.NONE, tick(d, 106, WHIP_BLOCK, 0));
	}

	@Test
	public void xpOneTickLateStillTeaches()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		assertEquals(AttackDetector.Result.LIKELY, tick(d, 100, WHIP, 0));
		d.onXpDrop();
		tick(d, 101, WHIP, 10);
		d.onAnimationSet();
		assertEquals(AttackDetector.Result.CERTAIN, tick(d, 104, WHIP, 0));
	}

	@Test
	public void aBlockIsNeverLearned()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		// Hit (block starts) on the same tick as some combat XP, e.g. a thrall-free cannon miss.
		d.onHitTaken();
		d.onXpDrop();
		assertEquals(AttackDetector.Result.NONE, tick(d, 100, WHIP_BLOCK, 0));
		d.onHitTaken();
		d.onAnimationSet();
		assertEquals(AttackDetector.Result.NONE, tick(d, 104, WHIP_BLOCK, 0));
	}

	@Test
	public void noTargetNoAttack()
	{
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		assertEquals(AttackDetector.Result.NONE, d.onTick(100, WHIP, 0, false));
		assertEquals(AttackDetector.Result.NONE, d.onTick(101, -1, 0, true));
	}

	@Test
	public void meleeTimerWhileTakingHits()
	{
		// A whip (4 ticks) against a monster that hits back on its own 4-tick rhythm, offset by
		// two ticks so its hits land between the player's attacks.
		AttackDetector d = new AttackDetector(NON_ATTACKS);
		AttackTimer timer = new AttackTimer();
		for (int attack = 0; attack < 6; attack++)
		{
			int t = 1000 + attack * 4;
			d.onAnimationSet();
			if (attack > 0)
			{
				d.onXpDrop();
			}
			AttackDetector.Result r = tick(d, t, WHIP, 0);
			boolean counted = r == AttackDetector.Result.CERTAIN ? timer.onCertainAttack(t)
				: timer.onTick(t, r == AttackDetector.Result.LIKELY, 4);
			assertEquals("attack " + attack, true, counted);
			assertEquals(4, timer.ticksLeft(t, 4));
			tick(d, t + 1, WHIP, 10);
			// Hit by the monster: a block animation starts.
			d.onHitTaken();
			r = tick(d, t + 2, WHIP_BLOCK, 0);
			assertEquals(AttackDetector.Result.NONE, r);
			timer.onTick(t + 2, false, 4);
			assertEquals(2, timer.ticksLeft(t + 2, 4));
			tick(d, t + 3, WHIP_BLOCK, 12);
		}
	}

	@Test
	public void certainAttackReplacesAWrongGuess()
	{
		AttackTimer timer = new AttackTimer();
		timer.onTick(100, true, 5);
		// The game wouldn't allow an attack at 102 if 100 had been one.
		assertEquals(true, timer.onCertainAttack(102));
		assertEquals(5, timer.ticksLeft(102, 5));
		assertEquals(false, timer.onCertainAttack(102));
	}

	// ---- Accuracy and DPS ----------------------------------------------------------------------

	@Test
	public void accuracyCountsZeros()
	{
		MonsterTracker t = new MonsterTracker();
		for (int i = 0; i < 9; i++)
		{
			t.recordRoll("Gargoyle", i % 3 != 0);
		}
		assertEquals("too few", -1, t.snapshot().get(0).getAccuracy());
		t.recordRoll("Gargoyle", true);
		// 7 of 10 did damage.
		assertEquals(70, t.snapshot().get(0).getAccuracy());

		Gson gson = new Gson();
		MonsterTracker loaded = new MonsterTracker();
		List<MonsterTracker.Saved> saved = gson.fromJson(gson.toJson(t.exportState()),
			new com.google.gson.reflect.TypeToken<List<MonsterTracker.Saved>>()
			{
			}.getType());
		loaded.importState(saved);
		assertEquals(70, loaded.snapshot().get(0).getAccuracy());
	}

	@Test
	public void dpsIsDamageOverFightingTime()
	{
		AttackUptime uptime = new AttackUptime();
		// 20 attacks every 4 ticks, 12 damage each: 240 damage over 80 ticks (48s) = 5 a second.
		for (int i = 0; i < 20; i++)
		{
			int t = i * 4;
			uptime.onAttack("Gargoyle", t, 4);
			uptime.onDamage("Gargoyle", 12, t + 1);
		}
		uptime.endFight();
		assertEquals(5.0, uptime.dps("Gargoyle"), 0.01);
		assertEquals(100, uptime.percent("Gargoyle"));
		// Damage outside a fight doesn't count.
		uptime.onDamage("Gargoyle", 500, 300);
		assertEquals(5.0, uptime.totalDps(), 0.01);
	}

	// ---- Target card ---------------------------------------------------------------------------

	@Test
	public void hitpointsFromTheHealthBar()
	{
		assertEquals(750, TargetInfo.hitpoints(30, 30, 750));
		assertEquals(0, TargetInfo.hitpoints(0, 30, 750));
		assertEquals(-1, TargetInfo.hitpoints(-1, 30, 750));
		int half = TargetInfo.hitpoints(15, 30, 750);
		assertEquals(true, half >= 360 && half <= 390);
		TargetInfo t = new TargetInfo("Vorkath", 120, 750);
		assertEquals(5, t.hitsLeft(25));
		assertEquals(-1, t.hitsLeft(0));
		assertEquals(0.16, t.getHealthShare(), 0.001);
	}

	// ---- Kills to combat level -----------------------------------------------------------------

	@Test
	public void combatLevelGoesUp()
	{
		int xp70 = net.runelite.api.Experience.getXpForLevel(70);
		int[] xp = {xp70, xp70, xp70, xp70, 0, 0};
		int now = net.runelite.api.Experience.getCombatLevel(70, 70, 70, 70, 1, 1, 43);
		// Training Strength only, 400 xp a kill (plus Hitpoints).
		double[] perKill = {0, 400, 0, 133, 0, 0};
		LevelForecast.CombatRow row = LevelForecast.killsToCombatLevel(xp, perKill, 43);
		assertEquals(now + 1, row.getNextLevel());
		// One kill fewer isn't enough.
		assertEquals(true, row.getKills() > 1);
		assertNull(LevelForecast.killsToCombatLevel(xp, new double[6], 43));

		int[] maxed = new int[6];
		java.util.Arrays.fill(maxed, net.runelite.api.Experience.getXpForLevel(99));
		assertNull(LevelForecast.killsToCombatLevel(maxed, perKill, 99));
	}

	@Test
	public void combatLevelArgumentOrder()
	{
		// Experience.getCombatLevel(attack, strength, defence, hitpoints, magic, ranged, prayer).
		assertEquals(126, net.runelite.api.Experience.getCombatLevel(99, 99, 99, 99, 1, 1, 99));
		assertEquals(3, net.runelite.api.Experience.getCombatLevel(1, 1, 1, 10, 1, 1, 1));
	}

	@Test
	public void summaryHasDps()
	{
		String text = SessionSummaryText.build(new java.util.EnumMap<>(net.runelite.api.Skill.class), new HitStats(),
			Collections.emptyList(), XpRateMode.SESSION, null, -1, 3.94);
		assertEquals(true, text.contains("DPS: 3.9"));
	}
}
