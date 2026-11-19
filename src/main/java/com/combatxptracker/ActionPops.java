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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WHAT: Action pops: short popups over your character for what you just did. The item you
 * equipped, the potion dose you drank, the HP your food healed.
 * WHY: Players asked for a reactive, cosmetic confirmation of their own actions. It only ever
 * reflects your own equipment and inventory after the fact: no prayers, no timers, no counting,
 * nothing about NPCs or boss mechanics, and no suggestions, so it stays within Jagex's
 * third-party client rules.
 * PRE: Fed from the client thread; read by the overlay on the client thread.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class ActionPops
{
	/** How long one pop shows, in milliseconds. */
	static final long SHOW_MILLIS = 1_600;
	/** At most this many pops at once. */
	static final int MAX_POPS = 3;
	/** Healing of this much or more counts as food, not natural regeneration. */
	static final int MIN_HEAL = 2;

	private static final Pattern DOSE = Pattern.compile("(.+)\\((\\d)\\)");

	/**
	 * One pop: its text, the item icon to show (or -1), and when it started.
	 */
	static final class Pop
	{
		final String text;
		final int itemId;
		final long start;

		Pop(String text, int itemId, long start)
		{
			this.text = text;
			this.itemId = itemId;
			this.start = start;
		}
	}

	private final List<Pop> pops = new ArrayList<>();

	/**
	 * WHAT: Adds a pop, dropping the oldest past MAX_POPS.
	 * WHY: A quick switch of several items shouldn't stack a tower of text.
	 * PRE: Client thread.
	 * POST: The pop shows for SHOW_MILLIS.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void add(String text, int itemId, long now)
	{
		if (text == null)
		{
			return;
		}
		while (pops.size() >= MAX_POPS)
		{
			pops.remove(0);
		}
		pops.add(new Pop(text, itemId, now));
	}

	/**
	 * WHAT: The pops still showing, oldest first.
	 * WHY: The overlay draws them stacked over your head.
	 * PRE: Client thread.
	 * POST: Finished pops are removed.
	 * RETURN: a copy of the showing pops
	 * THROWS: Nothing.
	 */
	List<Pop> showing(long now)
	{
		pops.removeIf(p -> now - p.start >= SHOW_MILLIS);
		return new ArrayList<>(pops);
	}

	void clear()
	{
		pops.clear();
	}

	/**
	 * WHAT: The items that are worn now but weren't before.
	 * WHY: Each newly equipped item gets a "+ Scythe of Vitur" pop.
	 * PRE: Both are item ids; before may be null on the first look after login.
	 * POST: Nothing changes.
	 * RETURN: the newly worn ids, empty when before is null
	 * THROWS: Nothing.
	 */
	static List<Integer> newlyWorn(Collection<Integer> before, Collection<Integer> now)
	{
		List<Integer> out = new ArrayList<>();
		if (before == null || now == null)
		{
			return out;
		}
		for (int id : now)
		{
			if (!before.contains(id))
			{
				out.add(id);
			}
		}
		return out;
	}

	/**
	 * WHAT: The text for a potion sip, read from what left and arrived in one inventory change:
	 * "Prayer potion (4 → 3)", or "Prayer potion (1 → empty)" for the last dose.
	 * WHY: Drinking a dose is one of the actions players wanted confirmed.
	 * PRE: before and now map item id to quantity; name gives an item's name.
	 * POST: Nothing changes.
	 * RETURN: {text, item id of the potion drunk} or null when this wasn't a sip
	 * THROWS: Nothing.
	 */
	static Object[] sip(Map<Integer, Long> before, Map<Integer, Long> now, IntFunction<String> name)
	{
		if (before == null || now == null)
		{
			return null;
		}
		for (Map.Entry<Integer, Long> lost : before.entrySet())
		{
			if (now.getOrDefault(lost.getKey(), 0L) >= lost.getValue())
			{
				continue;
			}
			Matcher l = DOSE.matcher(String.valueOf(name.apply(lost.getKey())));
			if (!l.matches())
			{
				continue;
			}
			String base = l.group(1).trim();
			int from = Integer.parseInt(l.group(2));
			for (Map.Entry<Integer, Long> got : now.entrySet())
			{
				if (got.getValue() <= before.getOrDefault(got.getKey(), 0L))
				{
					continue;
				}
				String gotName = String.valueOf(name.apply(got.getKey()));
				Matcher g = DOSE.matcher(gotName);
				if (g.matches() && g.group(1).trim().equals(base) && Integer.parseInt(g.group(2)) == from - 1)
				{
					return new Object[]{base + " (" + from + " → " + (from - 1) + ")", lost.getKey()};
				}
				if (from == 1 && gotName.equals("Vial"))
				{
					return new Object[]{base + " (1 → empty)", lost.getKey()};
				}
			}
		}
		return null;
	}

	/**
	 * WHAT: The text for food healing you: "+18 HP".
	 * WHY: Eating is the third action players wanted confirmed.
	 * PRE: before and after are your current (boosted) Hitpoints.
	 * POST: Nothing changes.
	 * RETURN: the text, or null when it's under MIN_HEAL (regeneration) or not a heal
	 * THROWS: Nothing.
	 */
	static String heal(int before, int after)
	{
		int healed = after - before;
		return healed >= MIN_HEAL ? "+" + healed + " HP" : null;
	}
}
