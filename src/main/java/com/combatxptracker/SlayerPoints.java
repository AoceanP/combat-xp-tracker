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
 * Slayer masters and the points their tasks give (OSRS Wiki, "Slayer reward point").
 *
 * No points for the first four tasks of a streak. After that each master gives a base
 * amount, and every 10th, 50th, 100th, 250th and 1,000th task gives 5x, 15x, 25x, 35x and
 * 50x the base.
 */
final class SlayerPoints
{
	private SlayerPoints()
	{
	}

	enum Master
	{
		// Ids are the game's slayer master varbit values.
		TURAEL(1, "Turael", 0, 0),
		MAZCHNA(2, "Mazchna", 6, 6),
		VANNAKA(3, "Vannaka", 8, 8),
		CHAELDAR(4, "Chaeldar", 10, 10),
		DURADEL(5, "Duradel", 15, 15),
		// 15 with the Elite Western Provinces Diary.
		NIEVE(6, "Nieve", 12, 15),
		KRYSTILIA(7, "Krystilia", 25, 25),
		// 20 with the Elite Kourend & Kebos Diary.
		KONAR(8, "Konar", 18, 20),
		SPRIA(9, "Spria", 0, 0),
		// Mortimer's points depend on the task's modifier, so they can't be predicted.
		MORTIMER(10, "Mortimer", -1, -1);

		private final int id;
		private final String displayName;
		private final int base;
		private final int baseWithDiary;

		Master(int id, String displayName, int base, int baseWithDiary)
		{
			this.id = id;
			this.displayName = displayName;
			this.base = base;
			this.baseWithDiary = baseWithDiary;
		}

		String getDisplayName()
		{
			return displayName;
		}

		/**
		 * The game's id for this master: the slayer master varbit value, which is also the
		 * master column in the game's task tables.
		 */
		int getId()
		{
			return id;
		}

		/**
		 * @return points per ordinary task, or -1 if it varies
		 */
		int base(boolean eliteDiary)
		{
			return eliteDiary ? baseWithDiary : base;
		}

		/**
		 * @return the master for a varbit value, or null
		 */
		static Master fromId(int id)
		{
			for (Master master : values())
			{
				if (master.id == id)
				{
					return master;
				}
			}
			return null;
		}
	}

	/**
	 * The masters worth comparing for a big bonus task, highest points last.
	 */
	static final Master[] BONUS_MASTERS = {Master.NIEVE, Master.DURADEL, Master.KONAR, Master.KRYSTILIA};

	/**
	 * The bonus multiplier for a task number: 50 for every 1,000th task, 35 for every
	 * 250th, and so on, 1 for an ordinary task.
	 */
	static int multiplier(int taskNumber)
	{
		if (taskNumber % 1000 == 0)
		{
			return 50;
		}
		if (taskNumber % 250 == 0)
		{
			return 35;
		}
		if (taskNumber % 100 == 0)
		{
			return 25;
		}
		if (taskNumber % 50 == 0)
		{
			return 15;
		}
		if (taskNumber % 10 == 0)
		{
			return 5;
		}
		return 1;
	}

	/**
	 * @param taskNumber the task's place in the streak, 1 for the first
	 * @return points for finishing it, or -1 if the master's points vary
	 */
	static int pointsFor(Master master, int taskNumber, boolean eliteDiary)
	{
		int base = master.base(eliteDiary);
		if (base < 0)
		{
			return -1;
		}
		if (taskNumber < 5)
		{
			return 0;
		}
		return base * multiplier(taskNumber);
	}

	/**
	 * @return the first task number from {@code from} on that gets at least {@code multiplier}
	 * times the points, e.g. the next 10th task for 5
	 */
	static int nextBonusTask(int from, int multiplier)
	{
		int step = multiplier >= 15 ? 50 : 10;
		int task = Math.max(from, 5);
		task = ((task + step - 1) / step) * step;
		while (multiplier(task) < multiplier)
		{
			task += step;
		}
		return task;
	}
}
