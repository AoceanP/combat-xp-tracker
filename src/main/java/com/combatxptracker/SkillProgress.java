/*
 * Copyright (c) 2026, YourNameHere <https://github.com/YourNameHere>
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

import java.util.ArrayDeque;
import java.util.Deque;
import net.runelite.api.Experience;

/**
 * Tracks XP-over-time for a single skill so we can compute a rolling XP/hr rate,
 * plus the player's chosen goal level and progress toward it.
 */
public class SkillProgress
{
	// Timestamped XP snapshots, oldest first. Pruned to the configured averaging window.
	private final Deque<XpSample> samples = new ArrayDeque<>();

	private int currentXp = 0;
	private int currentLevel = 1;
	private int goalLevel = 99;

	private static class XpSample
	{
		final long timestampMillis;
		final int xp;

		XpSample(long timestampMillis, int xp)
		{
			this.timestampMillis = timestampMillis;
			this.xp = xp;
		}
	}

	public void recordXp(int newXp, int newLevel, long nowMillis, int windowSeconds)
	{
		this.currentXp = newXp;
		this.currentLevel = newLevel;
		samples.addLast(new XpSample(nowMillis, newXp));
		pruneOlderThan(nowMillis - (windowSeconds * 1000L));
	}

	private void pruneOlderThan(long cutoffMillis)
	{
		while (!samples.isEmpty() && samples.peekFirst().timestampMillis < cutoffMillis)
		{
			// Keep at least one sample so we don't lose our baseline entirely
			if (samples.size() <= 1)
			{
				break;
			}
			samples.pollFirst();
		}
	}

	/**
	 * Rolling XP/hr based on samples within the current window.
	 * Returns 0 if we don't have at least two samples spanning a meaningful amount of time.
	 */
	public int getXpPerHour()
	{
		if (samples.size() < 2)
		{
			return 0;
		}

		XpSample first = samples.peekFirst();
		XpSample last = samples.peekLast();

		long elapsedMillis = last.timestampMillis - first.timestampMillis;
		if (elapsedMillis <= 0)
		{
			return 0;
		}

		int xpGained = last.xp - first.xp;
		double hoursElapsed = elapsedMillis / 3_600_000.0;
		return (int) Math.round(xpGained / hoursElapsed);
	}

	public int getCurrentXp()
	{
		return currentXp;
	}

	/**
	 * Timestamp of the most recent recorded XP sample, or -1 if no sample has been
	 * recorded yet. Used to check whether a recent hit landed close enough in time to
	 * pair with this skill's latest XP gain for the combined-drop overlay display.
	 */
	public long getLastUpdateMillis()
	{
		if (samples.isEmpty())
		{
			return -1;
		}
		return samples.peekLast().timestampMillis;
	}

	public int getCurrentLevel()
	{
		return currentLevel;
	}

	public int getGoalLevel()
	{
		return goalLevel;
	}

	public void setGoalLevel(int goalLevel)
	{
		this.goalLevel = Math.max(currentLevel, Math.min(99, goalLevel));
	}

	/**
	 * Progress from current level toward the goal level, as a fraction 0.0-1.0,
	 * measured in XP terms (not just level count) so it reflects real grind remaining.
	 */
	public double getProgressToGoal()
	{
		if (goalLevel <= currentLevel)
		{
			return 1.0;
		}

		int xpAtCurrentLevel = Experience.getXpForLevel(currentLevel);
		int xpAtGoalLevel = goalLevel >= 99 ? Experience.getXpForLevel(99) : Experience.getXpForLevel(goalLevel);

		int span = xpAtGoalLevel - xpAtCurrentLevel;
		if (span <= 0)
		{
			return 1.0;
		}

		double progressed = currentXp - xpAtCurrentLevel;
		return Math.max(0.0, Math.min(1.0, progressed / span));
	}

	public int getXpRemainingToGoal()
	{
		int xpAtGoal = goalLevel >= 99 ? Experience.getXpForLevel(99) : Experience.getXpForLevel(goalLevel);
		return Math.max(0, xpAtGoal - currentXp);
	}

	/**
	 * Estimated hours remaining to hit the goal level at the current XP/hr rate.
	 * Returns -1 if the rate is 0 (can't estimate).
	 */
	public double getEstimatedHoursToGoal()
	{
		int rate = getXpPerHour();
		if (rate <= 0)
		{
			return -1;
		}
		return getXpRemainingToGoal() / (double) rate;
	}

	public void reset()
	{
		samples.clear();
	}
}
