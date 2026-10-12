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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Where to get a task: the chance of each slayer master assigning it, from the same task
 * weights as the next task odds. Immutable.
 */
final class TaskSources
{
	static final TaskSources NONE = new TaskSources(null, Collections.emptyList());

	/**
	 * One master's chance of giving the task.
	 */
	static final class Source
	{
		private final SlayerPoints.Master master;
		private final double chance;
		private final int minAmount;
		private final int maxAmount;
		// Why the master can't give it right now (levels, unlocks, blocked), or null.
		private final String unavailable;

		Source(SlayerPoints.Master master, double chance, int minAmount, int maxAmount, String unavailable)
		{
			this.master = master;
			this.chance = chance;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			this.unavailable = unavailable;
		}

		SlayerPoints.Master getMaster()
		{
			return master;
		}

		double getChance()
		{
			return chance;
		}

		int getMinAmount()
		{
			return minAmount;
		}

		int getMaxAmount()
		{
			return maxAmount;
		}

		String getUnavailable()
		{
			return unavailable;
		}

		@Override
		public String toString()
		{
			return master + "=" + Math.round(chance * 10_000) + (unavailable != null ? "U" : "");
		}
	}

	private final String task;
	private final List<Source> sources;

	TaskSources(String task, List<Source> sources)
	{
		this.task = task;
		this.sources = sources;
	}

	/**
	 * Picks the task out of every master's odds. Masters that never assign it are left out;
	 * ones that can't give it to this player right now are kept, last, with the reason.
	 */
	static TaskSources of(String task, Map<SlayerPoints.Master, TaskOdds> oddsByMaster)
	{
		List<Source> sources = new ArrayList<>();
		for (Map.Entry<SlayerPoints.Master, TaskOdds> e : oddsByMaster.entrySet())
		{
			TaskOdds.Option o = e.getValue().find(task);
			if (o == null || o.getWeight() <= 0)
			{
				continue;
			}
			String why = o.getUnavailable() != null ? o.getUnavailable() : o.isBlocked() ? "Blocked by you" : null;
			sources.add(new Source(e.getKey(), o.getChance(), o.getMinAmount(), o.getMaxAmount(), why));
		}
		sources.sort(Comparator.comparingDouble(Source::getChance).reversed());
		return new TaskSources(task, Collections.unmodifiableList(sources));
	}

	String getTask()
	{
		return task;
	}

	/**
	 * Every master that can assign the task, likeliest first.
	 */
	List<Source> getSources()
	{
		return sources;
	}

	/**
	 * @return the master most likely to give it, or null if none can right now
	 */
	Source best()
	{
		return sources.isEmpty() || sources.get(0).chance <= 0 ? null : sources.get(0);
	}

	@Override
	public String toString()
	{
		return task + "|" + sources;
	}
}
