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
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * 1.9.6: supplies used.
 */
public class Release196Test
{
	private static final int SHARK = 385;
	private static final int PRAYER_4 = 2434;
	private static final int PRAYER_3 = 139;
	private static final int VIAL = 229;

	private static Map<Integer, Long> items(Object... idsAndCounts)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < idsAndCounts.length; i += 2)
		{
			m.put((Integer) idsAndCounts[i], ((Number) idsAndCounts[i + 1]).longValue());
		}
		return m;
	}

	private static long price(int id)
	{
		switch (id)
		{
			case SHARK:
				return 900;
			case PRAYER_4:
				return 8_000;
			case PRAYER_3:
				return 6_100;
			case VIAL:
				return 5;
			default:
				return 0;
		}
	}

	@Test
	public void eatingAndDrinkingCostWhatWasUsed()
	{
		// Ate a shark.
		assertEquals(900, SupplyLedger.consumedValue(items(SHARK, 10), items(SHARK, 9), Release196Test::price));
		// Drank a dose: a (4) became a (3), which is still worth 6,100.
		assertEquals(1_900, SupplyLedger.consumedValue(items(PRAYER_4, 2, SHARK, 9), items(PRAYER_4, 1, PRAYER_3, 1, SHARK, 9),
			Release196Test::price));
		// Nothing lost: never negative.
		assertEquals(0, SupplyLedger.consumedValue(items(SHARK, 1), items(SHARK, 3), Release196Test::price));
	}

	@Test
	public void decreasesOnlyCountWhatWentDown()
	{
		Map<Integer, Long> used = SupplyLedger.decreases(items(ItemID.DEATHRUNE, 400, ItemID.BLOODRUNE, 200),
			items(ItemID.DEATHRUNE, 396, ItemID.BLOODRUNE, 210));
		assertEquals(1, used.size());
		assertEquals(4L, (long) used.get(ItemID.DEATHRUNE));
	}

	@Test
	public void ledgerPerMonster()
	{
		SupplyLedger ledger = new SupplyLedger();
		ledger.record("Abyssal demon", SupplyLedger.Category.FOOD, 900, 1);
		ledger.record("abyssal demon", SupplyLedger.Category.FOOD, 900, 1);
		ledger.record("Abyssal demon", SupplyLedger.Category.CANNONBALLS, 3_000, 15);
		ledger.record(null, SupplyLedger.Category.FOOD, 900, 1);
		SupplyLedger.Totals t = ledger.get("Abyssal Demon");
		assertEquals(4_800, t.totalGp());
		assertEquals(2L, (long) t.count.get(SupplyLedger.Category.FOOD));
		String tip = SupplyLedger.tooltip(t);
		assertTrue(tip, tip.contains("Food: 1,800 gp (2 eaten)"));
		assertTrue(tip, tip.contains("Cannonballs: 3,000 gp (15 fired)"));
		assertFalse(tip, tip.contains("Runes"));
		assertNull(ledger.get("Gargoyle"));
		ledger.remove("Abyssal demon");
		assertNull(ledger.get("Abyssal demon"));
	}

	@Test
	public void ledgerSavesAndLoads()
	{
		SupplyLedger ledger = new SupplyLedger();
		ledger.record("Vorkath", SupplyLedger.Category.POTIONS, 50_000, 12);
		Gson gson = new Gson();
		String json = gson.toJson(ledger.exportState());
		SupplyLedger loaded = new SupplyLedger();
		loaded.importState(gson.fromJson(json, new TypeToken<Map<String, SupplyLedger.Totals>>()
		{
		}.getType()));
		assertEquals(50_000, loaded.get("Vorkath").totalGp());

		// A category from a newer version is skipped, the rest kept.
		loaded.importState(gson.fromJson("{\"vorkath\":{\"gp\":{\"POTIONS\":10,\"TELEPORTS\":5},\"count\":{\"POTIONS\":1}}}",
			new TypeToken<Map<String, SupplyLedger.Totals>>()
			{
			}.getType()));
		assertEquals(10, loaded.get("Vorkath").totalGp());
		loaded.reset();
		assertTrue(loaded.isEmpty());
	}

	@Test
	public void taskRecordsSupplies()
	{
		SlayerTaskSession session = new SlayerTaskSession();
		session.start("Abyssal demons");
		session.recordKill(1_000);
		session.recordSupplies(150_000, 0);
		session.recordSupplies(60_000, 300);
		SlayerTaskRecord r = session.toRecord(2_000, -1);
		assertEquals(210_000, r.supplies);
		assertEquals(300, r.cannonballs);
		assertTrue(session.summary(LootPrice.GRAND_EXCHANGE), session.summary(LootPrice.GRAND_EXCHANGE).endsWith("210K gp of supplies."));

		session.start("Gargoyles");
		assertEquals(0, session.toRecord(3_000, -1).supplies);
	}

	@Test
	public void runesAreKnown()
	{
		assertTrue(RuneSupply.isRune(ItemID.DEATHRUNE));
		assertTrue(RuneSupply.isRune(ItemID.SMOKERUNE));
		assertFalse(RuneSupply.isRune(SHARK));
	}
}
