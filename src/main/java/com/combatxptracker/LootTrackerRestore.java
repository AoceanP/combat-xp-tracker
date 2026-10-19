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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the Monsters tab from records RuneLite keeps itself: the Loot Tracker's kills and
 * drops per NPC, and boss kill counts. For stats the 2.0 startup bug erased, or a monster
 * removed by accident. Combined with what's tracked now by keeping the higher kill count and
 * the larger amount of each item, so nothing is counted twice and nothing goes down.
 */
final class LootTrackerRestore
{
	/**
	 * Item names and prices, read on the client thread.
	 */
	interface Items
	{
		String name(int itemId);

		long price(int itemId);

		long haPrice(int itemId);
	}

	static final class Result
	{
		final List<MonsterTracker.Saved> monsters;
		final int restored;
		final int kills;

		Result(List<MonsterTracker.Saved> monsters, int restored, int kills)
		{
			this.monsters = monsters;
			this.restored = restored;
			this.kills = kills;
		}
	}

	private LootTrackerRestore()
	{
	}

	/**
	 * @param current what the plugin has now
	 * @param lootRecords the Loot Tracker's saved NPC records, as it stores them
	 * @param killCounts boss kill counts, keyed by lower-case boss name
	 * @param only the monsters to bring back, or null for all of them
	 */
	static Result merge(List<MonsterTracker.Saved> current, Collection<String> lootRecords,
		Map<String, Integer> killCounts, Set<String> only, Gson gson, Items items)
	{
		Map<String, MonsterTracker.Saved> fromLoot = new LinkedHashMap<>();
		for (MonsterTracker.Saved s : read(lootRecords, killCounts, only, gson, items))
		{
			fromLoot.put(s.name, s);
		}
		return merge(current, fromLoot);
	}

	/**
	 * What the Loot Tracker has, one entry per monster as the plugin names them.
	 *
	 * @param only the monsters to read, or null for all of them
	 */
	static List<MonsterTracker.Saved> read(Collection<String> lootRecords, Map<String, Integer> killCounts,
		Set<String> only, Gson gson, Items items)
	{
		// Both Royal Titans are one boss here, as they are in the plugin.
		Map<String, MonsterTracker.Saved> fromLoot = new LinkedHashMap<>();
		for (String json : lootRecords)
		{
			JsonObject record;
			try
			{
				record = gson.fromJson(json, JsonObject.class);
			}
			catch (JsonParseException e)
			{
				continue;
			}
			if (record == null || !record.has("name"))
			{
				continue;
			}
			String name = BossGroups.trackedName(record.get("name").getAsString());
			if (only != null && !only.contains(name))
			{
				continue;
			}
			MonsterTracker.Saved s = fromLoot.computeIfAbsent(name, LootTrackerRestore::empty);
			s.kills += record.has("kills") ? record.get("kills").getAsInt() : 0;
			if (record.has("last"))
			{
				s.lastActivityMillis = Math.max(s.lastActivityMillis, record.get("last").getAsLong());
			}
			JsonArray drops = record.has("drops") ? record.getAsJsonArray("drops") : null;
			for (int i = 0; drops != null && i + 1 < drops.size(); i += 2)
			{
				addLoot(s, drops.get(i).getAsInt(), drops.get(i + 1).getAsLong(), items);
			}
		}

		// The game's own boss kill counts are the most complete, when it has them.
		for (MonsterTracker.Saved s : fromLoot.values())
		{
			Integer kc = killCounts.get(s.name.toLowerCase(Locale.ROOT));
			if (kc != null)
			{
				s.kills = Math.max(s.kills, kc);
			}
		}
		return new ArrayList<>(fromLoot.values());
	}

	private static Result merge(List<MonsterTracker.Saved> current, Map<String, MonsterTracker.Saved> fromLoot)
	{
		Map<String, MonsterTracker.Saved> merged = new LinkedHashMap<>();
		for (MonsterTracker.Saved s : current)
		{
			merged.put(s.name, s);
		}
		int kills = 0;
		for (MonsterTracker.Saved restored : fromLoot.values())
		{
			MonsterTracker.Saved now = merged.get(restored.name);
			if (now == null)
			{
				merged.put(restored.name, restored);
				kills += restored.kills;
				continue;
			}
			// Both counted the kills since the stats were lost, so the larger, never the sum.
			kills += Math.max(0, restored.kills - now.kills);
			now.kills = Math.max(now.kills, restored.kills);
			now.lastActivityMillis = Math.max(now.lastActivityMillis, restored.lastActivityMillis);
			Map<Integer, MonsterTracker.SavedLoot> lines = new LinkedHashMap<>();
			if (now.loot != null)
			{
				for (MonsterTracker.SavedLoot l : now.loot)
				{
					lines.put(l.id, l);
				}
			}
			for (MonsterTracker.SavedLoot l : restored.loot)
			{
				MonsterTracker.SavedLoot have = lines.get(l.id);
				if (have == null || have.quantity < l.quantity)
				{
					lines.put(l.id, l);
				}
			}
			now.loot = new ArrayList<>(lines.values());
		}
		for (MonsterTracker.Saved restored : fromLoot.values())
		{
			// The dry streak counts from here: the Loot Tracker doesn't say which kill a drop came on.
			MonsterTracker.Saved now = merged.get(restored.name);
			now.restoredAt = now.kills;
		}
		return new Result(new ArrayList<>(merged.values()), fromLoot.size(), kills);
	}

	private static MonsterTracker.Saved empty(String name)
	{
		MonsterTracker.Saved s = new MonsterTracker.Saved();
		s.name = name;
		s.biggestByStyle = new EnumMap<>(CombatStyle.class);
		s.loot = new ArrayList<>();
		s.rareDrops = new ArrayList<>();
		return s;
	}

	private static void addLoot(MonsterTracker.Saved s, int itemId, long quantity, Items items)
	{
		if (quantity <= 0)
		{
			return;
		}
		for (MonsterTracker.SavedLoot l : s.loot)
		{
			if (l.id == itemId)
			{
				l.quantity += quantity;
				return;
			}
		}
		MonsterTracker.SavedLoot l = new MonsterTracker.SavedLoot();
		l.id = itemId;
		l.name = items.name(itemId);
		l.quantity = quantity;
		l.unitPrice = items.price(itemId);
		l.haPrice = items.haPrice(itemId);
		s.loot.add(l);
	}
}
