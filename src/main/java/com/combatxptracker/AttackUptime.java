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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Attack uptime: how much of a fight the player spent attacking. After each attack the
 * weapon is busy for its attack speed; any ticks past that before the next attack (eating,
 * walking, a late click) are lost. A gap longer than {@link AttackTimer#IDLE_TICKS} means
 * the fight ended, and isn't counted.
 *
 * Damage dealt during fights is kept too, for damage per second of fighting.
 *
 * Kept per monster, plus a total. Saved per account as JSON, so field names are the save
 * format. Synchronized: recorded on the client thread and read by the panel.
 */
final class AttackUptime
{
	/**
	 * Less fighting than this (30 seconds) is too little to say.
	 */
	static final int MIN_TICKS = 50;

	static final class Ticks
	{
		long busy;
		long idle;
		// Damage dealt while fighting, for DPS.
		long damage;

		/**
		 * @return uptime from 0 to 100, or -1 if there's too little fighting to say
		 */
		int percent()
		{
			long total = busy + idle;
			return total < MIN_TICKS ? -1 : (int) Math.round(busy * 100.0 / total);
		}

		/**
		 * @return damage per second of fighting, or -1 if there's too little fighting to say
		 */
		double dps()
		{
			long total = busy + idle;
			return total < MIN_TICKS ? -1 : damage / (total * SECONDS_PER_TICK);
		}
	}

	private static final double SECONDS_PER_TICK = 0.6;

	private Map<String, Ticks> byMonster = new HashMap<>();
	private final Ticks total = new Ticks();
	private String lastMonster;
	private int lastTick = -1;
	private int lastSpeed;
	private int revision;

	private static String key(String name)
	{
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * The player attacked a monster. The time since their last attack counts towards the
	 * monster that attack was on.
	 */
	synchronized void onAttack(String monster, int tick, int speed)
	{
		int gap = tick - lastTick;
		if (lastTick >= 0 && (gap > AttackTimer.IDLE_TICKS || gap < 0))
		{
			closeFight();
		}
		else if (lastTick >= 0 && lastMonster != null && gap > 0)
		{
			int busy = Math.min(gap, lastSpeed);
			Ticks t = byMonster.computeIfAbsent(key(lastMonster), k -> new Ticks());
			t.busy += busy;
			t.idle += gap - busy;
			total.busy += busy;
			total.idle += gap - busy;
			revision++;
		}
		lastMonster = monster;
		lastTick = tick;
		lastSpeed = speed;
	}

	/**
	 * Logging out or teleporting away: the next attack starts a new fight.
	 */
	synchronized void endFight()
	{
		closeFight();
		lastTick = -1;
		lastMonster = null;
	}

	/**
	 * The last attack of a fight keeps the weapon busy for its attack speed too.
	 */
	private void closeFight()
	{
		if (lastTick < 0 || lastMonster == null)
		{
			return;
		}
		byMonster.computeIfAbsent(key(lastMonster), k -> new Ticks()).busy += lastSpeed;
		total.busy += lastSpeed;
		lastMonster = null;
		revision++;
	}

	/**
	 * Damage the player dealt. Only counted during a fight, so it matches the time.
	 */
	synchronized void onDamage(String monster, int amount, int tick)
	{
		if (monster == null || amount <= 0 || lastTick < 0 || tick - lastTick > AttackTimer.IDLE_TICKS)
		{
			return;
		}
		byMonster.computeIfAbsent(key(monster), k -> new Ticks()).damage += amount;
		total.damage += amount;
		revision++;
	}

	/**
	 * @return damage per second fighting a monster, or -1 if there isn't enough to say
	 */
	synchronized double dps(String monster)
	{
		Ticks t = monster == null ? null : byMonster.get(key(monster));
		return t == null ? -1 : t.dps();
	}

	/**
	 * @return damage per second over every monster, or -1 if there isn't enough to say
	 */
	synchronized double totalDps()
	{
		return total.dps();
	}

	/**
	 * @return ticks lost between attacks fighting a monster, or -1 if there isn't enough to say
	 */
	synchronized long idleTicks(String monster)
	{
		Ticks t = monster == null ? null : byMonster.get(key(monster));
		return t == null || t.percent() < 0 ? -1 : t.idle;
	}

	/**
	 * @return uptime fighting a monster from 0 to 100, or -1 if there isn't enough to say
	 */
	synchronized int percent(String monster)
	{
		Ticks t = monster == null ? null : byMonster.get(key(monster));
		return t == null ? -1 : t.percent();
	}

	/**
	 * @return uptime over every monster from 0 to 100, or -1 if there isn't enough to say
	 */
	synchronized int totalPercent()
	{
		return total.percent();
	}

	synchronized void remove(String monster)
	{
		Ticks t = byMonster.remove(key(monster));
		if (t != null)
		{
			total.busy -= t.busy;
			total.idle -= t.idle;
			total.damage -= t.damage;
			revision++;
		}
	}

	synchronized void reset()
	{
		byMonster.clear();
		total.busy = 0;
		total.idle = 0;
		total.damage = 0;
		lastTick = -1;
		lastMonster = null;
		revision++;
	}

	synchronized boolean isEmpty()
	{
		return byMonster.isEmpty();
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized Map<String, Ticks> exportState()
	{
		return new HashMap<>(byMonster);
	}

	/**
	 * @param saved from {@link #exportState()}, or null to start empty
	 */
	synchronized void importState(Map<String, Ticks> saved)
	{
		reset();
		if (saved == null)
		{
			return;
		}
		for (Map.Entry<String, Ticks> e : saved.entrySet())
		{
			Ticks t = e.getValue();
			if (e.getKey() == null || t == null || t.busy < 0 || t.idle < 0 || t.damage < 0)
			{
				continue;
			}
			byMonster.put(key(e.getKey()), t);
			total.busy += t.busy;
			total.idle += t.idle;
			total.damage += t.damage;
		}
	}
}
