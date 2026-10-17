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

/**
 * A running timer for the boss fight in progress, from the player's first attack. When the
 * kill's own fight duration message arrives it shows the game's exact time instead, for a few
 * seconds. Client thread only.
 */
final class KillTimer
{
	/**
	 * How long a finished kill's time stays up: 10 ticks, 6 seconds.
	 */
	static final int SHOW_RESULT_TICKS = 10;
	/**
	 * With no attack on the boss for this long (30 seconds) the fight was abandoned.
	 */
	static final int GIVE_UP_TICKS = 50;
	private static final double SECONDS_PER_TICK = 0.6;

	private String boss;
	private int startTick = -1;
	private int lastAttackTick = -1;
	private int endTick = -1;
	private double finalSeconds = -1;
	private boolean newBest;

	/**
	 * The player attacked a boss. Attacks on anything else mid-fight (a group boss's other
	 * half, a minion) keep the fight going.
	 *
	 * @return whether this started a new fight
	 */
	boolean onAttack(String name, int tick)
	{
		if (name == null)
		{
			return false;
		}
		if (isRunning())
		{
			lastAttackTick = tick;
			return false;
		}
		boss = name;
		startTick = tick;
		lastAttackTick = tick;
		endTick = -1;
		finalSeconds = -1;
		newBest = false;
		return true;
	}

	/**
	 * The boss died.
	 */
	void onKill(String name, int tick)
	{
		if (isRunning() && name != null && name.equalsIgnoreCase(boss))
		{
			endTick = tick;
		}
	}

	/**
	 * The game's fight duration for the kill.
	 */
	void onDuration(String name, double seconds, boolean best, int tick)
	{
		if (boss == null || name == null || !name.equalsIgnoreCase(boss))
		{
			return;
		}
		finalSeconds = seconds;
		newBest = best;
		if (endTick < 0)
		{
			endTick = tick;
		}
	}

	/**
	 * Called every tick: hides a finished or abandoned fight.
	 */
	void onTick(int tick)
	{
		if (boss == null)
		{
			return;
		}
		if (endTick >= 0 ? tick - endTick > SHOW_RESULT_TICKS : tick - lastAttackTick > GIVE_UP_TICKS)
		{
			reset();
		}
	}

	boolean isRunning()
	{
		return boss != null && endTick < 0;
	}

	boolean isShowing()
	{
		return boss != null;
	}

	boolean isFinished()
	{
		return boss != null && endTick >= 0;
	}

	String getBoss()
	{
		return boss;
	}

	boolean isNewBest()
	{
		return newBest;
	}

	/**
	 * @return seconds into the fight, or the kill's exact time once it's in
	 */
	double elapsed(int tick)
	{
		if (boss == null)
		{
			return 0;
		}
		if (finalSeconds >= 0)
		{
			return finalSeconds;
		}
		return Math.max(0, (endTick >= 0 ? endTick : tick) - startTick) * SECONDS_PER_TICK;
	}

	void reset()
	{
		boss = null;
		startTick = -1;
		lastAttackTick = -1;
		endTick = -1;
		finalSeconds = -1;
		newBest = false;
	}
}
