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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finished slayer tasks, newest first. Synchronized: written on the client thread, read by
 * the panel.
 */
final class SlayerLog
{
	/**
	 * Oldest tasks are dropped past this, to keep the saved data small.
	 */
	static final int MAX_TASKS = 500;

	private final List<SlayerTaskRecord> tasks = new ArrayList<>();
	private int revision;

	synchronized void add(SlayerTaskRecord record)
	{
		tasks.add(0, record);
		while (tasks.size() > MAX_TASKS)
		{
			tasks.remove(tasks.size() - 1);
		}
		revision++;
	}

	synchronized void remove(SlayerTaskRecord record)
	{
		if (tasks.remove(record))
		{
			revision++;
		}
	}

	synchronized void clear()
	{
		tasks.clear();
		revision++;
	}

	synchronized List<SlayerTaskRecord> getTasks()
	{
		return new ArrayList<>(tasks);
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized boolean isEmpty()
	{
		return tasks.isEmpty();
	}

	synchronized SlayerTaskRecord[] exportState()
	{
		return tasks.toArray(new SlayerTaskRecord[0]);
	}

	synchronized void importState(SlayerTaskRecord[] saved)
	{
		tasks.clear();
		if (saved != null)
		{
			for (SlayerTaskRecord r : Arrays.asList(saved))
			{
				if (r != null && r.task != null)
				{
					tasks.add(r);
				}
			}
		}
		revision++;
	}

	/**
	 * @return total superiors across every logged task
	 */
	synchronized int totalSuperiors()
	{
		int total = 0;
		for (SlayerTaskRecord r : tasks)
		{
			total += r.superiors;
		}
		return total;
	}

	/**
	 * @return total kills across every logged task
	 */
	synchronized int totalKills()
	{
		int total = 0;
		for (SlayerTaskRecord r : tasks)
		{
			total += r.kills;
		}
		return total;
	}

	/**
	 * Kills per superior across tasks that had any, or -1 with none yet.
	 */
	static double killsPerSuperior(int kills, int superiors)
	{
		return superiors > 0 ? kills / (double) superiors : -1;
	}
}
