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
	private boolean dismissedFromOverlay = false;

	// Tracking is opt-in: a skill only appears in the panel/overlay once the player has
	// explicitly right-clicked it and set a goal. Without this, every skill the player
	// has ever trained shows up at once, which is unreadable (23 rows of noise).
	private boolean goalSet = false;

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
		// If the player resumes training a skill they'd previously dismissed from the
		// overlay (e.g. pushing past an earlier milestone toward 99), un-dismiss it
		// automatically rather than leaving it silently hidden from someone actively
		// training it again.
		if (dismissedFromOverlay && newXp > currentXp)
		{
			dismissedFromOverlay = false;
		}
		this.currentXp = newXp;
		this.currentLevel = newLevel;
		samples.addLast(new XpSample(nowMillis, newXp));
		pruneOlderThan(nowMillis - (windowSeconds * 1000L));
	}

	/**
	 * Discards all accumulated samples and re-seeds from a known-good XP value.
	 *
	 * This exists because of a real bug: at the login screen, client.getSkillExperience()
	 * returns 0 for every skill. If that zero got stored as a baseline, the first real
	 * StatChanged event after login would look like the player gained their entire
	 * lifetime XP in the few seconds since startup, producing nonsense rates like
	 * "295,554,650 xp/hr". Calling this on GameState.LOGGED_IN throws away any such
	 * bogus baseline and starts clean from the real value.
	 */
	public void resetBaseline(int realXp, int realLevel, long nowMillis)
	{
		samples.clear();
		this.currentXp = realXp;
		this.currentLevel = realLevel;
		samples.addLast(new XpSample(nowMillis, realXp));
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
	 * Whether at least one real XP sample has been recorded for this skill yet. Used to
	 * distinguish "this skill legitimately has 0 XP" from "no real baseline has been
	 * recorded yet" (the field's zero-default before the first recordXp() call), which
	 * matters for computing accurate session-XP-gain deltas -- without this check, a
	 * StatChanged event firing before the deferred initializeCurrentSkillLevels() seeds a
	 * real baseline would compute a massive fake "gain" equal to the player's entire
	 * pre-existing XP total in that skill.
	 */
	public boolean hasRecordedSample()
	{
		return !samples.isEmpty();
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
		this.goalSet = true;
	}

	/**
	 * Whether the player has explicitly set a goal for this skill. Only skills where this
	 * is true appear in the panel and overlay -- tracking is opt-in, so the display shows
	 * the two or three skills you actually care about rather than all 23 at once.
	 */
	public boolean isGoalSet()
	{
		return goalSet;
	}

	/**
	 * Restores a goal loaded from saved config without re-triggering the level clamp
	 * against a not-yet-known current level.
	 */
	public void restoreSavedGoal(int savedGoalLevel)
	{
		this.goalLevel = Math.max(2, Math.min(99, savedGoalLevel));
		this.goalSet = true;
	}

	public void clearGoal()
	{
		this.goalSet = false;
		this.goalLevel = 99;
	}

	public boolean isDismissedFromOverlay()
	{
		return dismissedFromOverlay;
	}

	public void setDismissedFromOverlay(boolean dismissed)
	{
		this.dismissedFromOverlay = dismissed;
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
