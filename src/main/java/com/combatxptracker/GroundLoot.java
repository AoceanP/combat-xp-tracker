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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The player's own monster drops still lying on the ground, for the loot despawn alert.
 *
 * Items owned by the player spawn first; a moment later RuneLite says which of them were a
 * monster's loot. Only those are kept, so items the player drops themselves never count.
 * Each item is gone when it's picked up or despawns. Used on the client thread only.
 */
final class GroundLoot
{
	/**
	 * How long a spawned item waits to be matched with a monster's loot.
	 */
	static final int MATCH_TICKS = 2;

	private static final class Entry
	{
		final int itemId;
		final int sceneX;
		final int sceneY;
		final int plane;
		final int spawnTick;
		final int despawnTick;
		long value;
		boolean loot;

		Entry(int itemId, int sceneX, int sceneY, int plane, int spawnTick, int despawnTick)
		{
			this.itemId = itemId;
			this.sceneX = sceneX;
			this.sceneY = sceneY;
			this.plane = plane;
			this.spawnTick = spawnTick;
			this.despawnTick = despawnTick;
		}
	}

	/**
	 * The drop that will disappear first, and everything still on the ground.
	 */
	static final class Status
	{
		private final int ticksLeft;
		private final int items;
		private final long value;

		Status(int ticksLeft, int items, long value)
		{
			this.ticksLeft = ticksLeft;
			this.items = items;
			this.value = value;
		}

		/**
		 * @return ticks until the oldest drop disappears
		 */
		int getTicksLeft()
		{
			return ticksLeft;
		}

		int getItems()
		{
			return items;
		}

		long getValue()
		{
			return value;
		}
	}

	// Keyed by the game's own ground item object, so the same item is never counted twice.
	private final Map<Object, Entry> items = new IdentityHashMap<>();
	// Warned about the current pile; reset once it's all picked up or gone.
	private boolean warned;

	/**
	 * An item owned by the player appeared on the ground.
	 */
	void onSpawn(Object key, int itemId, int sceneX, int sceneY, int plane, int tick, int despawnTick)
	{
		if (despawnTick > tick)
		{
			items.put(key, new Entry(itemId, sceneX, sceneY, plane, tick, despawnTick));
		}
	}

	/**
	 * A monster's loot: marks the matching items that just spawned on that tile as loot.
	 *
	 * @param values the stack value of each dropped item, by item id (for the minimum value)
	 */
	void onLoot(Map<Integer, Long> values, int sceneX, int sceneY, int plane, int tick)
	{
		for (Entry e : items.values())
		{
			if (!e.loot && e.sceneX == sceneX && e.sceneY == sceneY && e.plane == plane
				&& tick - e.spawnTick <= MATCH_TICKS && values.containsKey(e.itemId))
			{
				e.loot = true;
				e.value = values.get(e.itemId);
			}
		}
	}

	void onDespawn(Object key)
	{
		items.remove(key);
	}

	/**
	 * Forgets items that were never matched with loot, and ones that should have despawned.
	 */
	void prune(int tick)
	{
		Iterator<Entry> it = items.values().iterator();
		while (it.hasNext())
		{
			Entry e = it.next();
			if (tick >= e.despawnTick || (!e.loot && tick - e.spawnTick > MATCH_TICKS) || tick < e.spawnTick)
			{
				it.remove();
			}
		}
	}

	/**
	 * @param minValue drops worth less than this are left out
	 * @return the loot on the ground, or null if there's none worth tracking
	 */
	Status status(int tick, long minValue)
	{
		int first = Integer.MAX_VALUE;
		int count = 0;
		long value = 0;
		for (Entry e : tracked(minValue))
		{
			first = Math.min(first, e.despawnTick);
			count++;
			value += e.value;
		}
		return count == 0 ? null : new Status(Math.max(0, first - tick), count, value);
	}

	/**
	 * @return whether the oldest drop just came within the warning time. Warns once per pile
	 * of loot: not again until it's all been picked up (or has gone).
	 */
	boolean shouldWarn(int tick, int warnTicks, long minValue)
	{
		Status status = status(tick, minValue);
		if (status == null)
		{
			warned = false;
			return false;
		}
		if (!warned && status.getTicksLeft() <= warnTicks)
		{
			warned = true;
			return true;
		}
		return false;
	}

	void clear()
	{
		items.clear();
		warned = false;
	}

	private List<Entry> tracked(long minValue)
	{
		List<Entry> out = new ArrayList<>();
		for (Entry e : items.values())
		{
			if (e.loot && e.value >= minValue)
			{
				out.add(e);
			}
		}
		return out;
	}
}
