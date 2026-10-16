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
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.junit.Test;

/**
 * 1.9.9: deaths, and tidier settings.
 */
public class Release199Test
{
	@Test
	public void deathsAreCountedPerMonster()
	{
		DeathLedger deaths = new DeathLedger();
		assertEquals(1, deaths.record("Vorkath", 12_400_000, 1_000));
		assertEquals(2, deaths.record("vorkath", 10_000_000, 2_000));
		deaths.record("Zulrah", 5_000_000, 3_000);
		DeathLedger.Deaths v = deaths.get("Vorkath");
		assertEquals(2, v.count);
		assertEquals(22_400_000, v.carried);
		assertEquals(2_000, v.lastMillis);
		assertEquals(3, deaths.totalDeaths());
		assertEquals("Deaths: 2", DeathLedger.label(v));
		assertNull(DeathLedger.label(deaths.get("Kraken")));
		deaths.remove("Vorkath");
		assertNull(deaths.get("Vorkath"));
	}

	@Test
	public void deathMessages()
	{
		assertEquals("Died to Vorkath. You were carrying 12.4M gp.", DeathLedger.message("Vorkath", 1, 12_400_000));
		assertEquals("Died to Zulrah (2nd time).", DeathLedger.message("Zulrah", 2, 0));
		assertEquals("1st", DeathLedger.ordinal(1));
		assertEquals("3rd", DeathLedger.ordinal(3));
		assertEquals("11th", DeathLedger.ordinal(11));
		assertEquals("12th", DeathLedger.ordinal(12));
		assertEquals("22nd", DeathLedger.ordinal(22));
		assertEquals("101st", DeathLedger.ordinal(101));
	}

	@Test
	public void deathsSaveAndLoad()
	{
		DeathLedger deaths = new DeathLedger();
		deaths.record("Vorkath", 12_400_000, 1_000);
		Gson gson = new Gson();
		DeathLedger loaded = new DeathLedger();
		loaded.importState(gson.fromJson(gson.toJson(deaths.exportState()), new TypeToken<Map<String, DeathLedger.Deaths>>()
		{
		}.getType()));
		assertEquals(1, loaded.get("vorkath").count);
		// Bad entries are skipped.
		loaded.importState(gson.fromJson("{\"kraken\":{\"count\":0},\"zulrah\":{\"count\":1,\"carried\":5}}",
			new TypeToken<Map<String, DeathLedger.Deaths>>()
			{
			}.getType()));
		assertNull(loaded.get("Kraken"));
		assertEquals(1, loaded.get("Zulrah").count);
		loaded.reset();
		assertTrue(loaded.isEmpty());
	}

	@Test
	public void taskRecordsDeaths()
	{
		SlayerTaskSession session = new SlayerTaskSession();
		session.start("Abyssal demons");
		session.recordKill(1_000);
		session.recordDeath();
		SlayerTaskRecord r = session.toRecord(2_000, -1);
		assertEquals(1, r.deaths);
		assertTrue(session.summary(LootPrice.GRAND_EXCHANGE), session.summary(LootPrice.GRAND_EXCHANGE).endsWith(", 1 death."));
	}

	@Test
	public void oneMonstersStats()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordHit("Vorkath", 40, CombatStyle.RANGED, 1_000);
		t.recordHit("Zulrah", 30, CombatStyle.MAGIC, 1_000);
		assertEquals(40, t.snapshotOf("vorkath").getTotalDamage());
		assertNull(t.snapshotOf("Kraken"));
		assertNull(t.snapshotOf(null));
	}
}
