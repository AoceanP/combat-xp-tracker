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

import java.util.Collections;
import java.util.List;

/**
 * Where the current task can be done and what to bring, for the Slayer tab. Immutable.
 */
final class SlayerGuide
{
	static final SlayerGuide NONE = new SlayerGuide(null, Collections.emptyList(), Collections.emptyList());

	/**
	 * A place the task can be done, with the game's own directions hint.
	 */
	static final class Area
	{
		private final String name;
		private final String hint;
		private final boolean assigned;

		Area(String name, String hint, boolean assigned)
		{
			this.name = name;
			this.hint = hint;
			this.assigned = assigned;
		}

		String getName()
		{
			return name;
		}

		/**
		 * @return directions, or null
		 */
		String getHint()
		{
			return hint;
		}

		/**
		 * @return whether this is the area Konar picked
		 */
		boolean isAssigned()
		{
			return assigned;
		}

		@Override
		public String toString()
		{
			return name + (assigned ? "*" : "");
		}
	}

	/**
	 * Something to bring and whether the player has it.
	 */
	static final class NeedStatus
	{
		private final String label;
		private final boolean met;
		private final boolean infoOnly;

		NeedStatus(String label, boolean met, boolean infoOnly)
		{
			this.label = label;
			this.met = met;
			this.infoOnly = infoOnly;
		}

		String getLabel()
		{
			return label;
		}

		boolean isMet()
		{
			return met;
		}

		boolean isInfoOnly()
		{
			return infoOnly;
		}

		@Override
		public String toString()
		{
			return label + (met ? "+" : "-");
		}
	}

	private final String task;
	private final List<Area> areas;
	private final List<NeedStatus> needs;

	SlayerGuide(String task, List<Area> areas, List<NeedStatus> needs)
	{
		this.task = task;
		this.areas = areas;
		this.needs = needs;
	}

	String getTask()
	{
		return task;
	}

	List<Area> getAreas()
	{
		return areas;
	}

	List<NeedStatus> getNeeds()
	{
		return needs;
	}

	/**
	 * @return how many required items are missing
	 */
	int missingCount()
	{
		int missing = 0;
		for (NeedStatus n : needs)
		{
			if (!n.met && !n.infoOnly)
			{
				missing++;
			}
		}
		return missing;
	}

	@Override
	public String toString()
	{
		return task + "|" + areas + "|" + needs;
	}
}
