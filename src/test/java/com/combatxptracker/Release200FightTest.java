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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 2.0.0, round 3: damage taken and flawless kills, idle time, boost reminder, elemental
 * weakness.
 */
public class Release200FightTest
{
	private static ElementalWeakness bundledWeaknesses()
	{
		return new ElementalWeakness(new Gson().fromJson(new InputStreamReader(
				CombatXpTrackerPlugin.class.getResourceAsStream("elemental_weakness.json"), StandardCharsets.UTF_8),
			new TypeToken<Map<String, List<Integer>>>()
			{
			}.getType()));
	}

	// ---- Elemental weakness --------------------------------------------------------------------

	@Test
	public void weaknessesByNpcId()
	{
		ElementalWeakness weaknesses = bundledWeaknesses();
		// Tz-Kih: water, 40%.
		ElementalWeakness.Weakness kih = weaknesses.of(2189);
		assertEquals(ElementalWeakness.Element.WATER, kih.getElement());
		assertEquals(40, kih.getPercent());
		assertEquals("Water 40%", kih.label());
		// Mithril dragon: earth, 50%.
		assertEquals("Earth 50%", weaknesses.of(2919).label());
		assertNull(weaknesses.of(-5));
	}

	@Test
	public void spellElements()
	{
		assertEquals(ElementalWeakness.Element.FIRE, ElementalWeakness.Element.ofSpell(MagicMaxHit.Spell.FIRE_SURGE));
		assertEquals(ElementalWeakness.Element.AIR, ElementalWeakness.Element.ofSpell(MagicMaxHit.Spell.WIND_SURGE));
		assertNull(ElementalWeakness.Element.ofSpell(null));
		assertNull(new ElementalWeakness(null).of(2189));
	}

	@Test
	public void targetChangesWhenTheSpellDoes()
	{
		ElementalWeakness.Weakness fire = new ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 40);
		TargetInfo a = new TargetInfo("Ent", 50, 100, fire, false);
		TargetInfo b = new TargetInfo("Ent", 50, 100, fire, true);
		assertFalse(a.equals(b));
		assertTrue(a.equals(new TargetInfo("Ent", 50, 100, new ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 40), false)));
	}

	// ---- Damage taken --------------------------------------------------------------------------

	@Test
	public void damageTakenAndFlawlessKills()
	{
		DamageTaken taken = new DamageTaken();
		taken.record("Vorkath", 40);
		taken.record("vorkath", 0);
		assertEquals("too few kills", -1, taken.get("Vorkath").perKill(), 0.001);
		taken.record("Vorkath", 20);
		taken.record("Vorkath", 0);
		DamageTaken.Stats s = taken.get("Vorkath");
		assertEquals(15, s.perKill(), 0.001);
		assertEquals("2 (50%)", s.flawlessLabel());

		Gson gson = new Gson();
		DamageTaken loaded = new DamageTaken();
		loaded.importState(gson.fromJson(gson.toJson(taken.exportState()), new TypeToken<Map<String, DamageTaken.Stats>>()
		{
		}.getType()));
		assertEquals(4, loaded.get("Vorkath").kills);
		// Impossible saves are dropped.
		loaded.importState(gson.fromJson("{\"zulrah\":{\"kills\":2,\"damage\":10,\"flawless\":5}}",
			new TypeToken<Map<String, DamageTaken.Stats>>()
			{
			}.getType()));
		assertNull(loaded.get("Zulrah"));
	}

	// ---- Idle time and the boost reminder ------------------------------------------------------

	@Test
	public void idleTimeReadsAsMinutes()
	{
		assertEquals("4m 12s", MonsterCard.idleTime(420));
		assertEquals("30s", MonsterCard.idleTime(50));
		AttackUptime uptime = new AttackUptime();
		for (int tick = 0; tick <= 100; tick += 5)
		{
			uptime.onAttack("Gargoyle", tick, 4);
		}
		// 20 gaps of 5 ticks with a 4-tick weapon: 20 ticks lost.
		assertEquals(20, uptime.idleTicks("Gargoyle"));
		assertEquals(-1, uptime.idleTicks("Kraken"));
	}

	@Test
	public void boostReminderSaysWhatsMissing()
	{
		assertEquals("Vorkath: no Ranged boost, no offensive prayer.",
			BoostReminder.message("Vorkath", CombatStyle.RANGED, false, false, false));
		assertEquals("Zulrah: no thrall.", BoostReminder.message("Zulrah", CombatStyle.MAGIC, true, true, true));
		assertNull(BoostReminder.message("Zulrah", CombatStyle.MAGIC, true, true, false));
		assertTrue(BoostReminder.offensivePrayers(CombatStyle.RANGED).contains(net.runelite.api.Prayer.RIGOUR));
		assertEquals(net.runelite.api.Skill.STRENGTH, BoostReminder.boostedSkill(CombatStyle.MELEE));
	}

	@Test
	public void killTimerSaysWhenAFightStarts()
	{
		KillTimer timer = new KillTimer();
		assertTrue(timer.onAttack("Vorkath", 10));
		assertFalse(timer.onAttack("Vorkath", 14));
		timer.onKill("Vorkath", 100);
		timer.onTick(100 + KillTimer.SHOW_RESULT_TICKS + 1);
		assertTrue(timer.onAttack("Vorkath", 120));
	}
}
