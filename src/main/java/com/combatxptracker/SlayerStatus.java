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
 * The player's slayer state read from the game each tick, for the Slayer tab. Immutable,
 * so the panel can read it from the Swing thread.
 */
final class SlayerStatus
{
	static final SlayerStatus NONE = new SlayerStatus(null, -1, -1, null, 0, 0, null, false, false);

	private final SlayerPoints.Master master;
	private final int points;
	private final int streak;
	private final String task;
	private final int remaining;
	private final int assigned;
	private final String location;
	private final boolean eliteWestern;
	private final boolean eliteKourend;

	SlayerStatus(SlayerPoints.Master master, int points, int streak, String task, int remaining, int assigned,
		String location, boolean eliteWestern, boolean eliteKourend)
	{
		this.master = master;
		this.points = points;
		this.streak = streak;
		this.task = task;
		this.remaining = remaining;
		this.assigned = assigned;
		this.location = location;
		this.eliteWestern = eliteWestern;
		this.eliteKourend = eliteKourend;
	}

	/**
	 * @return the master of the current or last task, or null before one was assigned
	 */
	SlayerPoints.Master getMaster()
	{
		return master;
	}

	/**
	 * @return slayer points, or -1 before logging in
	 */
	int getPoints()
	{
		return points;
	}

	/**
	 * @return tasks completed in a row with the current master's streak
	 */
	int getStreak()
	{
		return streak;
	}

	String getTask()
	{
		return task;
	}

	int getRemaining()
	{
		return remaining;
	}

	int getAssigned()
	{
		return assigned;
	}

	/**
	 * @return where the task must be done (Konar), or null
	 */
	String getLocation()
	{
		return location;
	}

	/**
	 * @return whether the diary that raises a master's points is done: the Elite Western
	 * Provinces Diary for Nieve, the Elite Kourend &amp; Kebos Diary for Konar
	 */
	boolean hasEliteDiary(SlayerPoints.Master m)
	{
		return m == SlayerPoints.Master.NIEVE ? eliteWestern : m == SlayerPoints.Master.KONAR && eliteKourend;
	}

	@Override
	public String toString()
	{
		return master + "|" + points + "|" + streak + "|" + task + "|" + remaining + "|" + assigned + "|" + location
			+ "|" + eliteWestern + "|" + eliteKourend;
	}
}
