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
import java.util.Map;

/**
 * The real attack speed of each weapon and attack style, learned from the player's own
 * attacks. The game never lets you attack faster than your weapon allows, so the shortest gap
 * between two attacks in a row is its speed. That corrects the item stats for weapons and
 * styles they get wrong.
 *
 * A gap has to be seen a few times before it's trusted, and more times when it's slower than
 * the item stats say, since walking or eating also makes gaps longer.
 */
final class AttackSpeeds
{
	/** No weapon attacks faster than this. Shorter gaps are a mistake in spotting attacks. */
	static final int MIN_TICKS = 2;
	/** Longer gaps mean the player stopped attacking for a while. */
	static final int MAX_TICKS = 10;
	/** Times a speed at or faster than the item stats has to be seen before it's used. */
	static final int CONFIRM = 2;
	/** Times a speed slower than the item stats has to be seen, with nothing faster. */
	static final int CONFIRM_SLOWER = 5;

	/**
	 * The shortest gap seen for one weapon and style, and how often.
	 */
	static final class Learned
	{
		int ticks;
		int seen;

		Learned()
		{
		}

		Learned(int ticks, int seen)
		{
			this.ticks = ticks;
			this.seen = seen;
		}
	}

	private final Map<String, Learned> learned = new HashMap<>();

	/**
	 * @return the key for a weapon (item id, 0 unarmed) and attack style name
	 */
	static String key(int weaponId, String style)
	{
		return Math.max(0, weaponId) + "|" + (style == null ? "" : style);
	}

	/**
	 * Two attacks in a row with the same weapon and style, this many ticks apart.
	 *
	 * @return whether what was learned changed, so it's worth saving
	 */
	synchronized boolean observe(String key, int gap)
	{
		if (gap < MIN_TICKS || gap > MAX_TICKS)
		{
			return false;
		}
		Learned l = learned.get(key);
		if (l == null)
		{
			learned.put(key, new Learned(gap, 1));
			return true;
		}
		if (gap < l.ticks)
		{
			l.ticks = gap;
			l.seen = 1;
			return true;
		}
		if (gap == l.ticks)
		{
			l.seen++;
			return true;
		}
		return false;
	}

	/**
	 * @param stats the speed from the item stats and style
	 * @return the learned speed once it's been seen enough, otherwise the item stats
	 */
	synchronized int speed(String key, int stats)
	{
		Learned l = learned.get(key);
		if (l == null)
		{
			return stats;
		}
		int needed = l.ticks > stats ? CONFIRM_SLOWER : CONFIRM;
		return l.seen >= needed ? l.ticks : stats;
	}

	synchronized Map<String, Learned> exportState()
	{
		Map<String, Learned> out = new HashMap<>();
		learned.forEach((k, l) -> out.put(k, new Learned(l.ticks, l.seen)));
		return out;
	}

	synchronized void importState(Map<String, Learned> saved)
	{
		learned.clear();
		if (saved == null)
		{
			return;
		}
		saved.forEach((k, l) ->
		{
			if (k != null && l != null && l.ticks >= MIN_TICKS && l.ticks <= MAX_TICKS && l.seen > 0)
			{
				learned.put(k, new Learned(l.ticks, l.seen));
			}
		});
	}
}
