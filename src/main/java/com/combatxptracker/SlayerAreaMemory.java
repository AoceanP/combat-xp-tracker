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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which slayer area the player is in. The game's tables name the areas but don't say where
 * they are (that's server side), so the plugin learns it: once the player marks the area
 * they're using, the map regions where they kill task monsters are remembered for that
 * area, and the next time they fight there it's picked automatically. It also remembers
 * whether an area turned out to be multi-combat or in the Wilderness.
 *
 * Saved per account as JSON, so field names are the save format. Synchronized: it's learned
 * on the client thread and read by the panel.
 */
final class SlayerAreaMemory
{
	// A cap so a corrupt save or a very long career can't grow without end.
	static final int MAX_REGIONS = 2000;

	/**
	 * The saved form.
	 */
	static final class Saved
	{
		Map<Integer, String> regions;
		Set<String> multi;
		Set<String> wilderness;
	}

	private final Map<Integer, String> regions = new LinkedHashMap<>();
	private final Set<String> multi = new LinkedHashSet<>();
	private final Set<String> wilderness = new LinkedHashSet<>();
	private int revision;

	/**
	 * Notes that the player killed a task monster in a map region while doing their task in
	 * an area. Later kills there pick the area automatically.
	 *
	 * @return whether anything new was learned
	 */
	synchronized boolean learn(int region, String area, boolean multiCombat, boolean inWilderness)
	{
		if (area == null || region <= 0)
		{
			return false;
		}
		boolean changed = false;
		if (!area.equals(regions.get(region)) && (regions.containsKey(region) || regions.size() < MAX_REGIONS))
		{
			regions.put(region, area);
			changed = true;
		}
		String key = key(area);
		if (multiCombat && multi.add(key))
		{
			changed = true;
		}
		if (inWilderness && wilderness.add(key))
		{
			changed = true;
		}
		if (changed)
		{
			revision++;
		}
		return changed;
	}

	/**
	 * @return the area learned for a map region, or null
	 */
	synchronized String areaFor(int region)
	{
		return regions.get(region);
	}

	/**
	 * @return the remembered area for a region, if it's one of the task's areas
	 */
	synchronized String areaFor(int region, Iterable<SlayerGuide.Area> taskAreas)
	{
		String area = regions.get(region);
		if (area == null)
		{
			return null;
		}
		for (SlayerGuide.Area a : taskAreas)
		{
			if (a.getName().equalsIgnoreCase(area))
			{
				return a.getName();
			}
		}
		return null;
	}

	synchronized boolean isMulti(String area)
	{
		return area != null && multi.contains(key(area));
	}

	synchronized boolean isWilderness(String area)
	{
		return area != null && wilderness.contains(key(area));
	}

	/**
	 * Forgets the regions learned for an area, e.g. after marking the wrong one.
	 */
	synchronized void forget(String area)
	{
		if (area != null && regions.values().removeIf(area::equalsIgnoreCase))
		{
			revision++;
		}
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized Saved exportState()
	{
		Saved saved = new Saved();
		saved.regions = new LinkedHashMap<>(regions);
		saved.multi = new LinkedHashSet<>(multi);
		saved.wilderness = new LinkedHashSet<>(wilderness);
		return saved;
	}

	synchronized void importState(Saved saved)
	{
		regions.clear();
		multi.clear();
		wilderness.clear();
		if (saved != null)
		{
			if (saved.regions != null)
			{
				for (Map.Entry<Integer, String> e : saved.regions.entrySet())
				{
					if (e.getKey() != null && e.getValue() != null && regions.size() < MAX_REGIONS)
					{
						regions.put(e.getKey(), e.getValue());
					}
				}
			}
			addAll(multi, saved.multi);
			addAll(wilderness, saved.wilderness);
		}
		revision++;
	}

	synchronized boolean isEmpty()
	{
		return regions.isEmpty() && multi.isEmpty() && wilderness.isEmpty();
	}

	private static void addAll(Set<String> to, Set<String> from)
	{
		for (String s : from == null ? Collections.<String>emptySet() : from)
		{
			if (s != null)
			{
				to.add(key(s));
			}
		}
	}

	private static String key(String area)
	{
		return area.toLowerCase(Locale.ROOT);
	}
}
