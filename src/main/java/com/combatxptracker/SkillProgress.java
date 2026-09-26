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

import java.util.ArrayDeque;
import java.util.Deque;
import net.runelite.api.Experience;

/**
 * XP-over-time for one skill (for a rolling XP/hr), plus the player's goal and how far
 * along it they are.
 *
 * Written on the client thread, read from the Swing thread by the sidebar, so every
 * method is synchronized. The old version wasn't, and the panel could read the sample
 * deque while the client thread was pruning it.
 *
 * Goal progress is measured from the XP the player had when the goal was set
 * ({@link #getGoalStartXp()}), not from the start of their current level. Measuring
 * from the current level made the bar jump back to ~0% on every level-up.
 */
public class SkillProgress
{
	private static final class XpSample
	{
		final long timestampMillis;
		final int xp;

		XpSample(long timestampMillis, int xp)
		{
			this.timestampMillis = timestampMillis;
			this.xp = xp;
		}
	}

	// Timestamped XP snapshots, oldest first, pruned to the configured averaging window.
	private final Deque<XpSample> samples = new ArrayDeque<>();
	private int currentXp;
	private boolean xpKnown;

	private Goal goal;
	// -1 until known. Goals saved by 1.3.x have no start and get one on first login.
	private int goalStartXp = -1;
	private boolean dismissedFromOverlay;

	// XP when this session began, -1 until the first real baseline after login.
	private int sessionStartXp = -1;
	// Time spent actually training this session, for the whole-session XP/hr.
	private final ActivityTimer sessionActivity = new ActivityTimer();
	// XP drops this session, for the average XP per action.
	private int sessionActions;

	/**
	 * The largest XP gain accepted in one step. Anything bigger means the previous value
	 * wasn't real: while logging in the client can briefly report 0 XP, and the real value
	 * then arrives as a "gain" of the player's whole lifetime XP.
	 */
	static final int MAX_PLAUSIBLE_XP_DELTA = 200_000;

	/**
	 * Records an XP value from the client.
	 *
	 * @return true for a normal update; false if the jump from the previous value was
	 * impossible, in which case the previous value is treated as bogus and this one
	 * becomes the new starting point (samples and session start included)
	 */
	public synchronized boolean recordXp(int newXp, long nowMillis, int windowSeconds)
	{
		if (isImpossibleJump(newXp))
		{
			rebase(newXp, nowMillis);
			return false;
		}

		boolean gained = xpKnown && newXp > currentXp;
		// Resuming a dismissed skill puts it back on the overlay.
		if (dismissedFromOverlay && newXp > currentXp)
		{
			dismissedFromOverlay = false;
		}
		if (gained)
		{
			sessionActivity.mark(nowMillis);
			sessionActions++;
		}
		currentXp = newXp;
		xpKnown = true;
		if (sessionStartXp < 0)
		{
			sessionStartXp = newXp;
		}
		samples.addLast(new XpSample(nowMillis, newXp));
		pruneOlderThan(nowMillis - windowSeconds * 1000L);
		return true;
	}

	/**
	 * Throws away samples and re-seeds from a known-good XP value. Called on login: at the
	 * login screen the client reports 0 XP for every skill, and using that as a baseline
	 * produced rates in the hundreds of millions.
	 */
	public synchronized void resetBaseline(int realXp, long nowMillis)
	{
		if (isImpossibleJump(realXp))
		{
			// The session start came from a bogus value; start the session here instead.
			rebase(realXp, nowMillis);
			return;
		}
		samples.clear();
		currentXp = realXp;
		xpKnown = true;
		if (sessionStartXp < 0)
		{
			sessionStartXp = realXp;
		}
		samples.addLast(new XpSample(nowMillis, realXp));
	}

	private boolean isImpossibleJump(int newXp)
	{
		return xpKnown && (long) newXp - currentXp > MAX_PLAUSIBLE_XP_DELTA;
	}

	private void rebase(int xp, long nowMillis)
	{
		samples.clear();
		currentXp = xp;
		xpKnown = true;
		sessionStartXp = xp;
		sessionActions = 0;
		sessionActivity.reset();
		samples.addLast(new XpSample(nowMillis, xp));
	}

	private void pruneOlderThan(long cutoffMillis)
	{
		// Always keep one sample as the baseline.
		while (samples.size() > 1 && samples.peekFirst().timestampMillis < cutoffMillis)
		{
			samples.pollFirst();
		}
	}

	/**
	 * Rolling XP/hr over the samples in the current window, or 0 without two samples
	 * spanning some time.
	 */
	public synchronized int getXpPerHour()
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
		double hours = elapsedMillis / 3_600_000.0;
		return (int) Math.round((last.xp - first.xp) / hours);
	}

	/**
	 * XP gained this session per hour of training, breaks over 5 minutes excluded.
	 * 0 until there's a minute of training.
	 */
	public synchronized int getSessionXpPerHour()
	{
		double rate = ActivityTimer.perHour(getSessionXpGained(), sessionActivity.getActiveMillis());
		return rate < 0 ? 0 : (int) Math.round(rate);
	}

	/**
	 * Roughly how many more XP drops (kills, hits, logs chopped...) reach the goal, from
	 * this session's average XP per drop.
	 *
	 * @return the estimate, or -1 without a goal or fewer than 3 drops to average over
	 */
	public synchronized int getActionsLeftToGoal()
	{
		if (goal == null || sessionActions < 3)
		{
			return -1;
		}
		int gained = getSessionXpGained();
		if (gained <= 0)
		{
			return -1;
		}
		double perAction = gained / (double) sessionActions;
		return (int) Math.ceil(getXpRemainingToGoal() / perAction);
	}

	public synchronized int getXpPerHour(XpRateMode mode)
	{
		return mode == XpRateMode.SESSION ? getSessionXpPerHour() : getXpPerHour();
	}

	/**
	 * Hours left at the given rate mode, or -1 when there's no rate to estimate from.
	 */
	public synchronized double getEstimatedHoursToGoal(XpRateMode mode)
	{
		int rate = getXpPerHour(mode);
		if (rate <= 0 || goal == null)
		{
			return -1;
		}
		return getXpRemainingToGoal() / (double) rate;
	}

	public synchronized int getCurrentXp()
	{
		return currentXp;
	}

	/**
	 * Whether the current XP is a real value from the client rather than the default 0.
	 */
	public synchronized boolean isXpKnown()
	{
		return xpKnown;
	}

	public synchronized boolean hasRecordedSample()
	{
		return !samples.isEmpty();
	}

	/**
	 * Timestamp of the newest sample, or -1. Used to pair XP drops with hits.
	 */
	public synchronized long getLastUpdateMillis()
	{
		return samples.isEmpty() ? -1 : samples.peekLast().timestampMillis;
	}

	/**
	 * Current level including virtual levels past 99 (up to 126), since goals can go
	 * that high.
	 */
	public synchronized int getCurrentLevel()
	{
		return Experience.getLevelForXp(currentXp);
	}

	// ---- Goal ------------------------------------------------------------------

	public synchronized Goal getGoal()
	{
		return goal;
	}

	public synchronized boolean isGoalSet()
	{
		return goal != null;
	}

	/**
	 * @param startXp the XP the progress bar starts from, normally the player's XP right
	 *                now; -1 if not known yet (it's filled in on login)
	 */
	public synchronized void setGoal(Goal goal, int startXp)
	{
		this.goal = goal;
		this.goalStartXp = startXp;
	}

	public synchronized void clearGoal()
	{
		goal = null;
		goalStartXp = -1;
	}

	public synchronized int getGoalStartXp()
	{
		return goalStartXp;
	}

	public synchronized void setGoalStartXp(int goalStartXp)
	{
		this.goalStartXp = goalStartXp;
	}

	public synchronized boolean isGoalReached()
	{
		return goal != null && xpKnown && currentXp >= goal.getTargetXp();
	}

	/**
	 * Fraction 0.0-1.0 of the way from the goal's start XP to its target XP.
	 */
	public synchronized double getProgressToGoal()
	{
		return progress(currentXp, goalStartXp, goal == null ? -1 : goal.getTargetXp());
	}

	/**
	 * The progress maths on its own so it can be unit tested directly.
	 *
	 * @param startXp the XP the goal was set at, or -1 to fall back to the start of the
	 *                current level
	 * @param targetXp the goal's XP, or -1 for no goal
	 */
	static double progress(int currentXp, int startXp, int targetXp)
	{
		if (targetXp < 0)
		{
			return 0.0;
		}
		if (currentXp >= targetXp)
		{
			return 1.0;
		}
		int start = startXp >= 0 ? startXp : Experience.getXpForLevel(Experience.getLevelForXp(currentXp));
		long span = (long) targetXp - start;
		if (span <= 0)
		{
			return 1.0;
		}
		double done = (double) currentXp - start;
		return Math.max(0.0, Math.min(1.0, done / span));
	}

	public synchronized int getXpRemainingToGoal()
	{
		return goal == null ? 0 : Math.max(0, goal.getTargetXp() - currentXp);
	}

	/**
	 * Hours left at the current XP/hr, or -1 when there's no rate to estimate from.
	 */
	public synchronized double getEstimatedHoursToGoal()
	{
		int rate = getXpPerHour();
		if (rate <= 0 || goal == null)
		{
			return -1;
		}
		return getXpRemainingToGoal() / (double) rate;
	}

	// ---- Overlay / session -----------------------------------------------------

	public synchronized boolean isDismissedFromOverlay()
	{
		return dismissedFromOverlay;
	}

	public synchronized void setDismissedFromOverlay(boolean dismissed)
	{
		dismissedFromOverlay = dismissed;
	}

	public synchronized int getSessionXpGained()
	{
		return sessionStartXp < 0 ? 0 : Math.max(0, currentXp - sessionStartXp);
	}

	/**
	 * Clears samples. Used on logout so offline time never counts as training time.
	 */
	public synchronized void reset()
	{
		samples.clear();
	}

	/**
	 * "Reset tracker": clears rates and starts the session count from now.
	 */
	public synchronized void resetSession()
	{
		samples.clear();
		sessionStartXp = xpKnown ? currentXp : -1;
		sessionActivity.reset();
		sessionActions = 0;
	}
}
