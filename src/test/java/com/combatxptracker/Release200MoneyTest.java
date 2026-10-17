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
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * 2.0.0, round 4: supply forecast, big drop reveal.
 */
public class Release200MoneyTest
{
	// ---- Supply forecast -----------------------------------------------------------------------

	@Test
	public void foodLastsFromFoodPerKill()
	{
		// 60 sharks over 40 kills: 1.5 a kill. 15 carried last 10 kills.
		assertEquals(10, SupplyForecast.foodKills(15, 60, 40));
		assertEquals("too few kills", -1, SupplyForecast.foodKills(15, 6, 4));
		assertEquals("never eats there", -1, SupplyForecast.foodKills(15, 0, 40));
		assertEquals(0, SupplyForecast.foodKills(0, 60, 40));
	}

	@Test
	public void prayerLastsFromDrainAndDoses()
	{
		// 70 Prayer: a dose restores 7 + 17 = 24. 50 points + 4 doses = 146 points at 10 a minute.
		assertEquals(14.6, SupplyForecast.prayerMinutes(50, 4, 70, 10), 0.001);
		assertEquals(-1, SupplyForecast.prayerMinutes(50, 4, 70, -1), 0.001);
		assertEquals(3, SupplyForecast.doses("Prayer potion(3)"));
		assertEquals(4, SupplyForecast.doses("Super restore(4)"));
		assertEquals(0, SupplyForecast.doses("Shark"));
		assertTrue(SupplyForecast.restoresPrayer("Super restore(2)"));
		assertTrue(SupplyForecast.restoresPrayer("Prayer potion(1)"));
		assertFalse(SupplyForecast.restoresPrayer("Super combat potion(4)"));
		assertEquals("~22 kills", SupplyForecast.killsLabel(22));
		assertEquals("~15 min", SupplyForecast.minutesLabel(14.6));
		assertEquals("~1h 05m", SupplyForecast.minutesLabel(65));
	}

	// ---- Big drop reveal -----------------------------------------------------------------------

	@Test
	public void revealTexts()
	{
		DropReveal unique = new DropReveal(1, "Vorkath's head", 21907, 80_000, "Vorkath", true,
			"Vorkath, kc 1,216, 24.3x the rate", Arrays.asList(1, 2, 3));
		assertEquals("New collection log item!", unique.headline());
		assertEquals("Unique Vorkaths head (Vorkath)", unique.screenshotName());
		DropReveal rare = new DropReveal(2, "Dragon warhammer", 13576, 38_000_000, "Lizardman shaman", false, null, null);
		assertEquals("Rare drop: 38M gp", rare.headline());
		assertTrue(rare.getStrip().isEmpty());
	}

	@Test
	public void stripLandsOnTheDrop()
	{
		DropReveal drop = new DropReveal(1, "Vorkath's head", 99, 0, "Vorkath", true, null, Arrays.asList(1, 2, 3, 99));
		List<Integer> strip = DropRevealCard.stripFor(drop, new Random(7));
		assertEquals(30, strip.size());
		assertEquals(99, (int) strip.get(25));
		// The other places never show the drop itself.
		assertFalse(strip.subList(0, 25).contains(99));
		// Nothing else to spin past: every place is the drop.
		DropReveal alone = new DropReveal(2, "Item", 5, 0, null, false, null, null);
		assertEquals(5, (int) DropRevealCard.stripFor(alone, new Random(1)).get(3));
	}

	@Test
	public void spinSlowsToAStop()
	{
		assertEquals(0, DropRevealCard.progress(0), 0.001);
		assertEquals(1, DropRevealCard.progress(10_000), 0.001);
		double early = DropRevealCard.progress(500) - DropRevealCard.progress(0);
		double late = DropRevealCard.progress(2_200) - DropRevealCard.progress(1_700);
		assertTrue("faster at the start", early > late);
	}

	@Test
	public void collectionLogFindsAnItemsPage()
	{
		CollectionLog log = new CollectionLog();
		log.syncPage("Vorkath", Arrays.asList(
			new CollectionLog.Item(22106, "Vorki", false),
			new CollectionLog.Item(21907, "Vorkath's head", true)),
			Arrays.asList("Vorkath", "Vorkath kills: 100"), 100);
		assertEquals("Vorkath", log.pageOf("vorki"));
		assertTrue(log.isMissing("Vorki"));
		assertFalse(log.isMissing("Vorkath's head"));
		assertNull(log.pageOf("Abyssal whip"));
	}

	// ---- Raids and chests ----------------------------------------------------------------------

	@Test
	public void rewardChestsByInterface()
	{
		assertEquals(RewardSources.CHAMBERS_OF_XERIC,
			RewardSources.ofInterface(net.runelite.api.gameval.InterfaceID.RAIDS_REWARDS));
		assertEquals("Barrows", RewardSources.ofInterface(net.runelite.api.gameval.InterfaceID.BARROWS_REWARD).getDisplayName());
		assertNull(RewardSources.ofInterface(-1));
	}

	@Test
	public void sameRewardHasTheSameSignature()
	{
		java.util.Map<Integer, Long> a = new java.util.HashMap<>();
		a.put(22324, 1L);
		a.put(995, 31_000L);
		java.util.Map<Integer, Long> b = new java.util.LinkedHashMap<>();
		b.put(995, 31_000L);
		b.put(22324, 1L);
		assertEquals("995x31000,22324x1", RewardSources.signature(a));
		assertEquals(RewardSources.signature(a), RewardSources.signature(b));
	}
}
