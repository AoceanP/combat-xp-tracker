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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Works out which items were added to the inventory between two snapshots. Used for
 * bosses whose reward goes straight into the inventory when you choose "Loot" on them
 * (e.g. the Royal Titans), rather than being dropped on death.
 */
final class InventoryDiff
{
	private InventoryDiff()
	{
	}

	/**
	 * @param before item id -> total quantity before
	 * @param after  item id -> total quantity after
	 * @return item id -> quantity gained, only for items that went up
	 */
	static Map<Integer, Long> gained(Map<Integer, Long> before, Map<Integer, Long> after)
	{
		Map<Integer, Long> gained = new LinkedHashMap<>();
		for (Map.Entry<Integer, Long> e : after.entrySet())
		{
			long delta = e.getValue() - before.getOrDefault(e.getKey(), 0L);
			if (delta > 0)
			{
				gained.put(e.getKey(), delta);
			}
		}
		return gained;
	}
}
