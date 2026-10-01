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

import java.util.Locale;

/**
 * What happened during the current slayer task, for the summary posted when it's done:
 * kills, time spent fighting, loot and the biggest hit.
 *
 * Only covers what the plugin saw: if it was turned on halfway through a task, the
 * summary covers the part it tracked. Used on the client thread only.
 */
final class SlayerTaskSession
{
	private String task;
	private int kills;
	private long lootGe;
	private long lootHa;
	private int biggestHit = -1;
	private int superiors;
	private int slayerXp;
	private int combatXp;
	private int assigned;
	private String master;
	private String location;
	private final ActivityTimer activity = new ActivityTimer();

	void start(String taskName)
	{
		task = taskName;
		kills = 0;
		lootGe = 0;
		lootHa = 0;
		biggestHit = -1;
		superiors = 0;
		slayerXp = 0;
		combatXp = 0;
		assigned = 0;
		master = null;
		location = null;
		activity.reset();
	}

	/**
	 * Task details read from the game when it was assigned.
	 */
	void setDetails(int assignedAmount, String masterName, String taskLocation)
	{
		assigned = assignedAmount;
		master = masterName;
		location = taskLocation;
	}

	void recordSuperior()
	{
		superiors++;
	}

	void recordXp(boolean slayer, int delta)
	{
		if (slayer)
		{
			slayerXp += delta;
		}
		else
		{
			combatXp += delta;
		}
	}

	int getSuperiors()
	{
		return superiors;
	}

	/**
	 * The task log entry for this task, when it's finished.
	 */
	SlayerTaskRecord toRecord(long endMillis, int points)
	{
		SlayerTaskRecord r = new SlayerTaskRecord();
		r.task = task;
		r.master = master;
		r.location = location;
		r.assigned = assigned;
		r.kills = kills;
		r.endMillis = endMillis;
		r.activeMillis = activity.getActiveMillis();
		r.slayerXp = slayerXp;
		r.combatXp = combatXp;
		r.lootGe = lootGe;
		r.lootHa = lootHa;
		r.biggestHit = biggestHit;
		r.superiors = superiors;
		r.points = points;
		return r;
	}

	void clear()
	{
		task = null;
	}

	boolean isActive()
	{
		return task != null;
	}

	String getTask()
	{
		return task;
	}

	int getKills()
	{
		return kills;
	}

	/**
	 * @return whether this NPC is part of the task, by its name, its boss group's, or for a
	 * superior (e.g. Night beast) the monster it replaces (Dark beast)
	 */
	boolean isTaskMonster(String npcName)
	{
		if (task == null || npcName == null)
		{
			return false;
		}
		if (SlayerTaskMatcher.matches(task, npcName))
		{
			return true;
		}
		String normal = SuperiorMonsters.normalFor(npcName);
		if (normal != null && SlayerTaskMatcher.matches(task, normal))
		{
			return true;
		}
		BossGroups group = BossGroups.of(npcName);
		return group != null && SlayerTaskMatcher.matches(task, group.getDisplayName());
	}

	void recordHit(int damage, long nowMillis)
	{
		biggestHit = Math.max(biggestHit, damage);
		activity.mark(nowMillis);
	}

	void recordKill(long nowMillis)
	{
		kills++;
		activity.mark(nowMillis);
	}

	void recordLoot(long geValue, long haValue, long nowMillis)
	{
		lootGe += geValue;
		lootHa += haValue;
		activity.mark(nowMillis);
	}

	/**
	 * e.g. "Abyssal demons task complete: 150 kills in 1h 32m, 3.2M gp (2.1M gp/hr),
	 * biggest hit 47."
	 */
	String summary(LootPrice price)
	{
		long loot = price == LootPrice.HIGH_ALCHEMY ? lootHa : lootGe;
		StringBuilder sb = new StringBuilder(task).append(" task complete: ")
			.append(kills).append(kills == 1 ? " kill" : " kills");

		long active = activity.getActiveMillis();
		if (active > 0)
		{
			sb.append(" in ").append(Formatting.duration(active / 3_600_000.0));
		}
		if (loot > 0)
		{
			sb.append(", ").append(Formatting.compactXp(loot)).append(" gp");
			double gpPerHour = ActivityTimer.perHour(loot, active);
			if (gpPerHour > 0)
			{
				sb.append(" (").append(Formatting.compactXp(Math.round(gpPerHour))).append(" gp/hr)");
			}
		}
		if (biggestHit >= 0)
		{
			sb.append(String.format(Locale.US, ", biggest hit %d", biggestHit));
		}
		if (superiors > 0)
		{
			sb.append(", ").append(superiors).append(superiors == 1 ? " superior" : " superiors");
		}
		return sb.append('.').toString();
	}
}
