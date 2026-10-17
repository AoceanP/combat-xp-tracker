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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How long what the player is carrying will last against the monster they're fighting:
 * food in kills, from the food they've eaten per kill on it, and prayer in minutes, from
 * their measured prayer drain on it.
 */
final class SupplyForecast
{
	/**
	 * Fewer kills than this say too little about food per kill.
	 */
	static final int MIN_KILLS = 5;
	// "Prayer potion(3)", "Super restore(4)": the dose count at the end of the name.
	private static final Pattern DOSES = Pattern.compile("\\((\\d)\\)$");

	private SupplyForecast()
	{
	}

	/**
	 * @param carried pieces of food carried
	 * @param eaten   food eaten fighting the monster, all time
	 * @param kills   kills of the monster, all time
	 * @return kills the food lasts, or -1 if unknown
	 */
	static int foodKills(int carried, long eaten, int kills)
	{
		if (carried < 0 || eaten <= 0 || kills < MIN_KILLS)
		{
			return -1;
		}
		return (int) Math.floor(carried / (eaten / (double) kills));
	}

	/**
	 * @param points       prayer points now
	 * @param doses        prayer restoring doses carried
	 * @param pointsPerMin prayer drained a minute fighting the monster, or below 0 if unknown
	 * @return minutes the prayer lasts, or -1 if unknown
	 */
	static double prayerMinutes(int points, int doses, int prayerLevel, double pointsPerMin)
	{
		if (pointsPerMin <= 0 || points < 0)
		{
			return -1;
		}
		return (points + Math.max(0, doses) * PrayerDrain.pointsPerDose(prayerLevel)) / pointsPerMin;
	}

	/**
	 * @return the doses in a potion's name ("Prayer potion(3)" -> 3), or 0 for anything else
	 */
	static int doses(String itemName)
	{
		Matcher m = itemName == null ? null : DOSES.matcher(itemName);
		return m != null && m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/**
	 * Potions that restore prayer points.
	 */
	static boolean restoresPrayer(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		String name = itemName.toLowerCase(java.util.Locale.ROOT);
		return name.startsWith("prayer potion") || name.startsWith("super restore") || name.startsWith("blighted super restore")
			|| name.startsWith("sanfew serum") || name.startsWith("moonlight potion");
	}

	/**
	 * e.g. "~22 kills".
	 */
	static String killsLabel(int kills)
	{
		return "~" + Formatting.withCommas(kills) + (kills == 1 ? " kill" : " kills");
	}

	/**
	 * e.g. "~14 min", "~1h 05m".
	 */
	static String minutesLabel(double minutes)
	{
		long whole = Math.round(minutes);
		return whole >= 60 ? String.format(java.util.Locale.US, "~%dh %02dm", whole / 60, whole % 60) : "~" + whole + " min";
	}
}
