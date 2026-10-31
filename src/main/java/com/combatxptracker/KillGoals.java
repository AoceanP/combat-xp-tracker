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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WHAT: Kill count goals, like "500 Vorkath kills": saved per account as one line of text,
 * "Vorkath=500;Zulrah=1000", in the order they were added.
 * WHY: Goals aren't only skills. A plain format needs no JSON and survives monster names with
 * spaces and apostrophes.
 * PRE: Monster names never contain '=' or ';' (in-game names don't).
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class KillGoals
{
	/** The largest goal accepted. */
	static final int MAX_TARGET = 10_000_000;

	private KillGoals()
	{
	}

	/**
	 * WHAT: Reads the saved text into monster -> target, skipping anything malformed.
	 * WHY: A hand-edited or corrupt setting must never break the Goals tab.
	 * PRE: text may be null or empty.
	 * POST: Nothing changes.
	 * RETURN: the goals, in saved order (empty if none)
	 * THROWS: Nothing.
	 */
	static Map<String, Integer> parse(String text)
	{
		Map<String, Integer> goals = new LinkedHashMap<>();
		if (text == null || text.isEmpty())
		{
			return goals;
		}
		for (String part : text.split(";"))
		{
			int eq = part.lastIndexOf('=');
			if (eq <= 0)
			{
				continue;
			}
			String name = part.substring(0, eq).trim();
			try
			{
				int target = Integer.parseInt(part.substring(eq + 1).trim());
				if (!name.isEmpty() && target > 0 && target <= MAX_TARGET)
				{
					goals.put(name, target);
				}
			}
			catch (NumberFormatException e)
			{
				// Skip it; the rest still load.
			}
		}
		return goals;
	}

	/**
	 * WHAT: Writes goals back to the saved text form.
	 * WHY: The opposite of parse.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: e.g. "Vorkath=500;Zulrah=1000"
	 * THROWS: Nothing.
	 */
	static String serialize(Map<String, Integer> goals)
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : goals.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(e.getKey().replace(";", "").replace("=", "")).append('=').append(e.getValue());
		}
		return sb.toString();
	}

	/**
	 * WHAT: A sensible default goal above your current kills: the next of 100, 250, 500, 1,000,
	 * 2,500, 5,000 and so on.
	 * WHY: The add-goal dialog starts with a target worth aiming for.
	 * PRE: kills >= 0.
	 * POST: Nothing changes.
	 * RETURN: a round target above kills
	 * THROWS: Nothing.
	 */
	static int suggestTarget(int kills)
	{
		// 100, 250, 500, 1,000, 2,500, 5,000, 10,000, ...
		for (long step = 100; step <= MAX_TARGET; step *= 10)
		{
			for (long target : new long[]{step, step * 5 / 2, step * 5})
			{
				if (target > kills && target <= MAX_TARGET)
				{
					return (int) target;
				}
			}
		}
		return MAX_TARGET;
	}

	/**
	 * WHAT: Whether the kill just counted is the one that reaches the goal.
	 * WHY: Announce it once, on that kill; a goal set below your kills is already done and stays quiet.
	 * PRE: kills is the count after the kill.
	 * POST: Nothing changes.
	 * RETURN: true when kills equals the target
	 * THROWS: Nothing.
	 */
	static boolean justReached(int kills, int target)
	{
		return target > 0 && kills == target;
	}

	/**
	 * WHAT: Progress as the infobox shows it: "42%", or "Done".
	 * WHY: Matches the skill goal infobox. Rounds down so 99.9% never says 100%.
	 * PRE: target > 0.
	 * POST: Nothing changes.
	 * RETURN: the text
	 * THROWS: Nothing.
	 */
	static String percentText(int kills, int target)
	{
		if (kills >= target)
		{
			return "Done";
		}
		return (int) Math.floor(Math.max(0, kills) * 100.0 / target) + "%";
	}

	/**
	 * WHAT: The right side of the overlay line: "212/500 (42%)", or "Goal reached!".
	 * WHY: Kill count goals sit under skill goals on the overlay.
	 * PRE: target > 0.
	 * POST: Nothing changes.
	 * RETURN: the text
	 * THROWS: Nothing.
	 */
	static String overlayText(int kills, int target)
	{
		if (kills >= target)
		{
			return "Goal reached!";
		}
		return Formatting.withCommas(kills) + "/" + Formatting.withCommas(target) + " (" + percentText(kills, target) + ")";
	}

	/**
	 * WHAT: Hours left to reach a goal at a kill rate.
	 * WHY: "~7h 10m" on the goal card.
	 * PRE: killsPerHour may be zero or negative for unknown.
	 * POST: Nothing changes.
	 * RETURN: hours, or -1 if unknown or the goal is reached
	 * THROWS: Nothing.
	 */
	static double hoursLeft(int kills, int target, double killsPerHour)
	{
		if (kills >= target || killsPerHour <= 0)
		{
			return -1;
		}
		return (target - kills) / killsPerHour;
	}
}
