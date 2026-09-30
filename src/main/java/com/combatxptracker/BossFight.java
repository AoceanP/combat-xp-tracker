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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your part in one boss fight, for the chat summary when it dies: time from your first
 * hit, your damage and its share of the boss's hitpoints.
 *
 * A fight only gets a summary when the game follows the kill with a kill count message
 * ("Your Vorkath kill count is: 124."). That's how bosses are told apart from ordinary
 * monsters without keeping a list. Used on the client thread only.
 */
final class BossFight
{
	/**
	 * "Your Vorkath kill count is: 124.", "Your Gauntlet completion count is: 3." and the
	 * like, with colour tags already removed. Chest counts (Barrows) aren't a boss kill.
	 */
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completed )?(.+?) (?:kill |completion |success )?count is: ?([\\d,]+)\\.?$");

	private static final double SECONDS_PER_TICK = 0.6;

	private final String name;
	private final int startTick;
	private int lastHitTick;
	private int damage;
	private int hits;
	private int biggest;
	private int hitpoints;
	private final Set<Integer> npcs = new HashSet<>();

	BossFight(String name, int startTick)
	{
		this.name = name;
		this.startTick = startTick;
		this.lastHitTick = startTick;
	}

	/**
	 * @param npcIndex  the NPC hit, so each one's hitpoints are added once
	 * @param npcHitpoints its hitpoints, or 0 if unknown
	 */
	void recordHit(int npcIndex, int npcHitpoints, int amount, int tick)
	{
		if (npcs.add(npcIndex) && npcHitpoints > 0)
		{
			hitpoints += npcHitpoints;
		}
		damage += amount;
		hits++;
		biggest = Math.max(biggest, amount);
		lastHitTick = tick;
	}

	int getLastHitTick()
	{
		return lastHitTick;
	}

	/**
	 * @return the kill count in a kill count message, or -1 if it isn't one
	 */
	static int parseKillCount(String message)
	{
		if (message == null)
		{
			return -1;
		}
		Matcher m = KILL_COUNT.matcher(message.trim());
		if (!m.matches() || m.group(1).toLowerCase(Locale.ROOT).contains("chest"))
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(m.group(2).replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	/**
	 * e.g. "Vorkath kill 124 in 1:32.4: you dealt 750 damage (100%) in 18 hits, biggest hit 62."
	 */
	String summary(int killCount, int endTick)
	{
		StringBuilder sb = new StringBuilder(name);
		if (killCount > 0)
		{
			sb.append(" kill ").append(Formatting.withCommas(killCount));
		}
		int ticks = Math.max(0, endTick - startTick);
		if (ticks > 0)
		{
			sb.append(" in ").append(fightTime(ticks));
		}
		sb.append(": you dealt ").append(Formatting.withCommas(damage)).append(" damage");
		if (hitpoints > 0 && damage <= hitpoints)
		{
			sb.append(" (").append(Math.round(damage * 100.0 / hitpoints)).append("%)");
		}
		sb.append(" in ").append(hits).append(hits == 1 ? " hit" : " hits");
		if (hits > 0)
		{
			sb.append(", biggest hit ").append(biggest);
		}
		return sb.append('.').toString();
	}

	/**
	 * Game ticks as m:ss.s, e.g. 154 ticks -> "1:32.4".
	 */
	static String fightTime(int ticks)
	{
		double seconds = ticks * SECONDS_PER_TICK;
		int minutes = (int) (seconds / 60);
		return String.format(Locale.US, "%d:%04.1f", minutes, seconds - minutes * 60);
	}
}
