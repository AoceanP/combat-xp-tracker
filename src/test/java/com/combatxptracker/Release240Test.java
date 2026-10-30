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

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 2.4: the share card, a picture of a monster's stats made for posting.
 */
public class Release240Test
{
	private static ShareCard.Data vorkath()
	{
		ShareCard.Data d = new ShareCard.Data();
		d.name = "Vorkath";
		d.kills = 1247;
		d.pbSeconds = 98.4;
		d.killsPerHour = 33.6;
		d.loot = 186_400_000;
		d.gpPerHour = 4_980_000;
		d.luckPercent = 18;
		d.killsSinceRare = 212;
		d.drops.add(new ShareCard.Drop(null, 1, true));
		d.date = "30 Oct 2026";
		return d;
	}

	@Test
	public void tilesShowEveryKnownStatInOrder()
	{
		List<String[]> tiles = ShareCard.tiles(vorkath());
		assertEquals(4, tiles.size());
		assertEquals("1:38.40", tiles.get(0)[0]);
		assertEquals("Personal best", tiles.get(0)[1]);
		assertEquals("33.6", tiles.get(1)[0]);
		assertEquals("Loot", tiles.get(2)[1]);
		assertEquals("GP/hr", tiles.get(3)[1]);
	}

	@Test
	public void tilesLeaveOutUnknownStats()
	{
		ShareCard.Data d = new ShareCard.Data();
		d.name = "Cow";
		d.kills = 3;
		assertTrue(ShareCard.tiles(d).isEmpty());
		d.loot = 1_000;
		assertEquals(1, ShareCard.tiles(d).size());
	}

	@Test
	public void luckTextHasASign()
	{
		assertEquals("+18% vs average", ShareCard.luckText(17.6));
		assertEquals("-43% vs average", ShareCard.luckText(-43.2));
		assertEquals("0% vs average", ShareCard.luckText(0.2));
	}

	@Test
	public void renderIsSharpAndGrowsWithItsSections()
	{
		BufferedImage full = ShareCard.render(vorkath());
		assertEquals(ShareCard.WIDTH * ShareCard.SCALE, full.getWidth());

		ShareCard.Data bare = new ShareCard.Data();
		bare.name = "A monster with a really quite long name";
		bare.kills = 1;
		BufferedImage small = ShareCard.render(bare);
		assertEquals(ShareCard.WIDTH * ShareCard.SCALE, small.getWidth());
		assertTrue(small.getHeight() < full.getHeight());
	}

	@Test
	public void shareDataMatchesTheCard()
	{
		MonsterTracker tracker = new MonsterTracker();
		long now = 1_000_000;
		for (int i = 0; i < 10; i++)
		{
			tracker.recordKill("Vorkath", now + i * 60_000L);
		}
		tracker.recordLoot("Vorkath", Arrays.asList(new MonsterTracker.Drop(536, "Dragon bones", 20, 2_700),
			new MonsterTracker.Drop(22006, "Skeletal visage", 1, 2_600_000)), now + 600_000);
		MonsterTracker.Snapshot s = tracker.snapshot(LootPrice.GRAND_EXCHANGE, item -> false, 1_000_000).get(0);

		CombatXpTrackerPlugin plugin = new CombatXpTrackerPlugin()
		{
			@Override
			public boolean isCollapsed(String monster)
			{
				return false;
			}
		};
		MonsterCard card = new MonsterCard("Vorkath", null, plugin);
		assertNull("nothing to share before the card is filled in", card.shareData());
		card.update(s, -1, 1_000_000, false);
		card.setStatus(true, 4, true);
		Map<String, Map<String, Double>> rates = new HashMap<>();
		rates.put("Vorkath", Collections.singletonMap("Skeletal visage", 5000.0));
		Map<String, Long> loot = Collections.singletonMap("skeletal visage", 1L);
		card.updateDropLuck(new DryCalculator(rates).luck("Vorkath", loot, 5000));

		ShareCard.Data d = card.shareData();
		assertEquals("Vorkath", d.name);
		assertEquals(10, d.kills);
		assertEquals(4, d.killsToday);
		assertEquals(-1, d.pbSeconds, 0);
		assertEquals(s.getLootValue(), d.loot);
		assertEquals(0, d.luckPercent, 1e-9);
		assertEquals(0, d.killsSinceRare);
		// Best first, and the visage is a rare drop.
		assertEquals(2, d.drops.size());
		assertEquals(1, d.drops.get(0).quantity);
		assertTrue(d.drops.get(0).rare);
		assertFalse(d.drops.get(1).rare);
	}
}
