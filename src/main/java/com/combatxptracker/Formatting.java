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

import java.util.Locale;

/**
 * Number and duration formatting shared by the panel, overlay and infoboxes.
 */
public final class Formatting
{
	private Formatting()
	{
	}

	/**
	 * 950 -> "950", 12_345 -> "12.3K", 13_034_431 -> "13.03M", 200_000_000 -> "200M".
	 */
	public static String compactXp(long xp)
	{
		long abs = Math.abs(xp);
		if (abs < 10_000)
		{
			return String.format(Locale.US, "%,d", xp);
		}
		if (abs < 1_000_000)
		{
			return trimZeros(String.format(Locale.US, "%.1f", xp / 1_000.0)) + "K";
		}
		if (abs < 100_000_000)
		{
			return trimZeros(String.format(Locale.US, "%.2f", xp / 1_000_000.0)) + "M";
		}
		return trimZeros(String.format(Locale.US, "%.1f", xp / 1_000_000.0)) + "M";
	}

	public static String withCommas(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}

	/**
	 * Hours -> "45m", "4h 12m", "3d 7h". Negative or NaN means "unknown" -> "-".
	 */
	public static String duration(double hours)
	{
		if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0)
		{
			return "-";
		}
		long totalMinutes = Math.round(hours * 60);
		if (totalMinutes < 1)
		{
			return "<1m";
		}
		long days = totalMinutes / (60 * 24);
		long h = (totalMinutes / 60) % 24;
		long m = totalMinutes % 60;
		if (days > 0)
		{
			return days + "d " + h + "h";
		}
		if (h > 0)
		{
			return h + "h " + m + "m";
		}
		return m + "m";
	}

	public static String capitalize(String s)
	{
		if (s == null || s.isEmpty())
		{
			return s;
		}
		return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String trimZeros(String s)
	{
		if (!s.contains("."))
		{
			return s;
		}
		s = s.replaceAll("0+$", "");
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}
}
