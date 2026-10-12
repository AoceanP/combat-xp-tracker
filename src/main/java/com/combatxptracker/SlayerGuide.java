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
	static final SlayerGuide NONE = new SlayerGuide(null, Collections.emptyList(), Collections.emptyList(), false);

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
		// Not carried, but in the bank the last time it was opened.
		private final boolean inBank;

		NeedStatus(String label, boolean met, boolean infoOnly)
		{
			this(label, met, infoOnly, false);
		}

		NeedStatus(String label, boolean met, boolean infoOnly, boolean inBank)
		{
			this.label = label;
			this.met = met;
			this.infoOnly = infoOnly;
			this.inBank = !met && inBank;
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

		/**
		 * @return whether it isn't carried but is in the bank
		 */
		boolean isInBank()
		{
			return inBank;
		}

		@Override
		public String toString()
		{
			return label + (met ? "+" : inBank ? "b" : "-");
		}
	}

	private final String task;
	private final List<Area> areas;
	private final List<NeedStatus> needs;
	// Whether the bank has been seen, so "Missing" can mean "you don't own one".
	private final boolean bankKnown;

	SlayerGuide(String task, List<Area> areas, List<NeedStatus> needs)
	{
		this(task, areas, needs, false);
	}

	SlayerGuide(String task, List<Area> areas, List<NeedStatus> needs, boolean bankKnown)
	{
		this.task = task;
		this.areas = areas;
		this.needs = needs;
		this.bankKnown = bankKnown;
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

	boolean isBankKnown()
	{
		return bankKnown;
	}

	/**
	 * @return how many required items aren't carried (including ones in the bank)
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

	/**
	 * @return how many required items aren't carried but are in the bank
	 */
	int inBankCount()
	{
		int inBank = 0;
		for (NeedStatus n : needs)
		{
			if (n.inBank && !n.infoOnly)
			{
				inBank++;
			}
		}
		return inBank;
	}

	@Override
	public String toString()
	{
		return task + "|" + areas + "|" + needs + "|" + bankKnown;
	}
}
