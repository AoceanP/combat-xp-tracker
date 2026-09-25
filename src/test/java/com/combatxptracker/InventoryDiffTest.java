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
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class InventoryDiffTest
{
	private static Map<Integer, Long> inv(long... idQtyPairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < idQtyPairs.length; i += 2)
		{
			m.put((int) idQtyPairs[i], idQtyPairs[i + 1]);
		}
		return m;
	}

	@Test
	public void newItemsAndBiggerStacksCount()
	{
		// Before: 100 coins and a shark. After looting: 600 coins, the shark, 2 new items.
		Map<Integer, Long> gained = InventoryDiff.gained(
			inv(995, 100, 385, 1),
			inv(995, 600, 385, 1, 1319, 1, 560, 25));
		assertEquals(3, gained.size());
		assertEquals(Long.valueOf(500), gained.get(995));
		assertEquals(Long.valueOf(1), gained.get(1319));
		assertEquals(Long.valueOf(25), gained.get(560));
	}

	@Test
	public void usedOrDroppedItemsAreNotLoot()
	{
		// Ate the shark and nothing arrived: no gain.
		assertTrue(InventoryDiff.gained(inv(995, 100, 385, 1), inv(995, 100)).isEmpty());
		// Ate the shark while loot arrived: only the loot counts.
		Map<Integer, Long> gained = InventoryDiff.gained(inv(385, 1), inv(1319, 1));
		assertEquals(1, gained.size());
		assertEquals(Long.valueOf(1), gained.get(1319));
	}

	@Test
	public void emptyInventories()
	{
		assertTrue(InventoryDiff.gained(inv(), inv()).isEmpty());
		assertEquals(Long.valueOf(3), InventoryDiff.gained(inv(), inv(526, 3)).get(526));
	}
}
