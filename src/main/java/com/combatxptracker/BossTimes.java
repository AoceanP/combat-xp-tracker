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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boss kill times from the game's own chat messages: "Fight duration: 1:23.40 (new personal
 * best)" or "Fight duration: 1:25.00. Personal best: 1:20.40". The game sends one with the
 * kill count message, so the two are matched up to know which boss it was.
 *
 * Saved per account as JSON, so field names are the save format. Synchronized: recorded on
 * the client thread and read by the panel.
 */
final class BossTimes
{
	/**
	 * With colour tags removed. Raids and the Gauntlet word it differently from bosses.
	 */
	private static final Pattern DURATION = Pattern.compile(
		"(?i)(?:(?:fight |challenge |corrupted challenge )?duration:|subdued in|(?<!total )completion time:) "
			+ "([0-9]+(?::[0-9]{2}){1,2}(?:\\.[0-9]+)?)\\.?( \\(new personal best\\))?"
			+ "(?:\\.? ?personal best: ([0-9]+(?::[0-9]{2}){1,2}(?:\\.[0-9]+)?))?");

	/**
	 * One boss's times, in seconds. 0 = none yet.
	 */
	static final class Times
	{
		double best;
		double last;
		// The last few kill times, oldest first. Null in saves from before 2.0.
		List<Double> recent;

		List<Double> recentOrEmpty()
		{
			return recent == null ? Collections.emptyList() : recent;
		}
	}

	/**
	 * How many recent kill times are kept for each boss.
	 */
	static final int RECENT_KILLS = 20;

	/** Kills needed before a trend means anything: three against three. */
	static final int TREND_MIN_KILLS = 6;

	/**
	 * WHAT: How your recent kills compare with the ones before them: the newer half's average
	 * minus the older half's, in seconds.
	 * WHY: PB and Avg don't say whether you're improving; "8.0s faster" does (2.5).
	 * PRE: recent is oldest first.
	 * POST: Nothing changes.
	 * RETURN: seconds, negative when faster, or NaN with fewer than TREND_MIN_KILLS times
	 * THROWS: Nothing.
	 */
	static double trend(List<Double> recent)
	{
		if (recent == null || recent.size() < TREND_MIN_KILLS)
		{
			return Double.NaN;
		}
		int half = recent.size() / 2;
		double older = 0;
		double newer = 0;
		for (int i = 0; i < half; i++)
		{
			older += recent.get(i);
			newer += recent.get(recent.size() - 1 - i);
		}
		return (newer - older) / half;
	}

	/**
	 * WHAT: A trend as words: "8.0s faster", "1:02.0 slower" or "Steady".
	 * WHY: The boss card's Trend row.
	 * PRE: seconds is from trend() and not NaN.
	 * POST: Nothing changes.
	 * RETURN: the text
	 * THROWS: Nothing.
	 */
	static String trendText(double seconds)
	{
		double size = Math.abs(seconds);
		if (size < 0.5)
		{
			return "Steady";
		}
		String amount = size < 60 ? String.format(Locale.US, "%.1fs", size) : KillTimerOverlay.clock(size);
		return amount + (seconds < 0 ? " faster" : " slower");
	}

	/**
	 * A parsed duration message.
	 */
	static final class Duration
	{
		final double seconds;
		final boolean newBest;
		// The best before this kill, from the message, or 0.
		final double previousBest;

		Duration(double seconds, boolean newBest, double previousBest)
		{
			this.seconds = seconds;
			this.newBest = newBest;
			this.previousBest = previousBest;
		}
	}

	private Map<String, Times> byBoss = new HashMap<>();
	private int revision;

	private static String key(String name)
	{
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * @return the duration in a message, or null if it isn't one. Colosseum and Inferno wave
	 * times aren't a kill.
	 */
	static Duration parse(String message)
	{
		if (message == null || message.toLowerCase(Locale.ROOT).contains("wave"))
		{
			return null;
		}
		Matcher m = DURATION.matcher(message);
		if (!m.find())
		{
			return null;
		}
		double seconds = parseTime(m.group(1));
		if (seconds <= 0)
		{
			return null;
		}
		double previous = m.group(3) == null ? 0 : Math.max(0, parseTime(m.group(3)));
		return new Duration(seconds, m.group(2) != null, previous);
	}

	/**
	 * "1:23.40" -> 83.4, "1:02:03" -> 3723. -1 if it can't be read.
	 */
	static double parseTime(String text)
	{
		String[] parts = text.split(":");
		double seconds = 0;
		try
		{
			for (String part : parts)
			{
				seconds = seconds * 60 + Double.parseDouble(part);
			}
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
		return seconds;
	}

	/**
	 * 83.4 -> "1:23.40", 83 -> "1:23", 3723 -> "1:02:03". Hundredths only when the game gave
	 * them (precise timing is a game setting).
	 */
	static String format(double seconds)
	{
		return format(seconds, false);
	}

	/**
	 * @param hundredths always show hundredths, so times shown together line up: "1:12.00"
	 *                   next to "1:25.20"
	 */
	static String format(double seconds, boolean hundredths)
	{
		long total = Math.round(seconds * 100);
		long whole = total / 100;
		long h = whole / 3600;
		long m = whole / 60 % 60;
		long s = whole % 60;
		String text = h > 0
			? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
			: String.format(Locale.US, "%d:%02d", m, s);
		long fraction = total % 100;
		return fraction == 0 && !hundredths ? text : text + String.format(Locale.US, ".%02d", fraction);
	}

	/**
	 * Records a kill time.
	 *
	 * @return the best before this kill, if this kill beat it (0 when there wasn't one), or
	 * -1 if it isn't a new personal best
	 */
	synchronized double record(String boss, Duration d)
	{
		Times t = byBoss.computeIfAbsent(key(boss), k -> new Times());
		double before = d.previousBest > 0 ? d.previousBest : t.best;
		t.last = d.seconds;
		if (t.recent == null)
		{
			t.recent = new ArrayList<>();
		}
		t.recent.add(d.seconds);
		while (t.recent.size() > RECENT_KILLS)
		{
			t.recent.remove(0);
		}
		if (d.newBest)
		{
			t.best = d.seconds;
		}
		else if (d.previousBest > 0)
		{
			// The game's own record, which may be from before the plugin was installed.
			t.best = d.previousBest;
		}
		else if (t.best <= 0 || d.seconds < t.best)
		{
			t.best = d.seconds;
		}
		revision++;
		return d.newBest ? before : -1;
	}

	/**
	 * @return a boss's times, or null if none are recorded
	 */
	synchronized Times get(String boss)
	{
		Times t = boss == null ? null : byBoss.get(key(boss));
		if (t == null)
		{
			return null;
		}
		Times copy = new Times();
		copy.best = t.best;
		copy.last = t.last;
		copy.recent = new ArrayList<>(t.recentOrEmpty());
		return copy;
	}

	/**
	 * e.g. "PB 1:12.00, last 1:16.80", or null if there's nothing to show.
	 */
	static String label(Times t)
	{
		if (t == null || t.best <= 0)
		{
			return null;
		}
		boolean showLast = t.last > 0 && Math.abs(t.last - t.best) > 0.001;
		boolean precise = isPrecise(t.best) || showLast && isPrecise(t.last);
		String text = "PB " + format(t.best, precise);
		if (showLast)
		{
			text += ", last " + format(t.last, precise);
		}
		return text;
	}

	/**
	 * Whether a time has hundredths, from the game's precise timing setting.
	 */
	static boolean isPrecise(double seconds)
	{
		return Math.round(seconds * 100) % 100 != 0;
	}

	synchronized void remove(String boss)
	{
		if (byBoss.remove(key(boss)) != null)
		{
			revision++;
		}
	}

	synchronized void reset()
	{
		byBoss.clear();
		revision++;
	}

	synchronized boolean isEmpty()
	{
		return byBoss.isEmpty();
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized Map<String, Times> exportState()
	{
		return new HashMap<>(byBoss);
	}

	/**
	 * @param saved from {@link #exportState()}, or null to start empty
	 */
	synchronized void importState(Map<String, Times> saved)
	{
		byBoss.clear();
		revision++;
		if (saved == null)
		{
			return;
		}
		for (Map.Entry<String, Times> e : saved.entrySet())
		{
			if (e.getKey() != null && e.getValue() != null && e.getValue().best >= 0)
			{
				byBoss.put(key(e.getKey()), e.getValue());
			}
		}
	}
}
