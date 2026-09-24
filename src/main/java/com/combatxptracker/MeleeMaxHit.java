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

/**
 * The melee max hit formula from the OSRS Wiki's "Maximum melee hit" page, as pure
 * functions so it can be unit tested without a game client:
 *
 * <pre>
 * Effective Strength = floor((floor(Visible Strength x Prayer) + Style + 8) x Void)
 * Max Hit            = floor(0.5 + Effective Strength x (Strength Bonus + 64) / 640)
 * Target bonus       = floor(Max Hit x Multiplier)   (Salve, Slayer helm / Black mask)
 * </pre>
 *
 * All maths is done in integers. Doing it with doubles gets some floors wrong: for
 * example 100 x 1.15 is 114.99999999999999 in floating point, which floors to 114
 * instead of 115.
 */
public final class MeleeMaxHit
{
	/** Every hit in the game is capped at 200 (OSRS Wiki). */
	public static final int DAMAGE_CAP = 200;

	private MeleeMaxHit()
	{
	}

	/**
	 * Strength prayers, as percentages so the multiply stays in integers.
	 */
	public enum StrengthPrayer
	{
		NONE("No prayer", 100),
		BURST_OF_STRENGTH("Burst of Strength", 105),
		SUPERHUMAN_STRENGTH("Superhuman Strength", 110),
		ULTIMATE_STRENGTH("Ultimate Strength", 115),
		CHIVALRY("Chivalry", 118),
		PIETY("Piety", 123);

		private final String displayName;
		private final int percent;

		StrengthPrayer(String displayName, int percent)
		{
			this.displayName = displayName;
			this.percent = percent;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		int apply(int level)
		{
			int boosted = level * percent / 100;
			// Burst of Strength always raises Strength by at least 1 (wiki footnote).
			if (this == BURST_OF_STRENGTH && boosted == level)
			{
				boosted++;
			}
			return boosted;
		}
	}

	/**
	 * Target-specific bonuses, as exact fractions.
	 */
	public enum TargetBonus
	{
		/** Black mask / Slayer helmet, any variant, on a slayer task target. */
		SLAYER_HELM("Slayer helm", 7, 6),
		/** Salve amulet and Salve amulet(i) against undead. */
		SALVE("Salve amulet", 7, 6),
		/** Salve amulet (e) and Salve amulet(ei) against undead. */
		SALVE_E("Salve amulet (e)", 6, 5);

		private final String displayName;
		private final int numerator;
		private final int denominator;

		TargetBonus(String displayName, int numerator, int denominator)
		{
			this.displayName = displayName;
			this.numerator = numerator;
			this.denominator = denominator;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public int apply(int maxHit)
		{
			return Math.min(DAMAGE_CAP, maxHit * numerator / denominator);
		}
	}

	/**
	 * @param visibleStrength the boosted Strength level, which already includes potions
	 * @param styleBonus      +3 aggressive, +1 controlled, 0 otherwise
	 * @param voidMelee       full Void melee set (normal or elite)
	 */
	public static int effectiveStrength(int visibleStrength, StrengthPrayer prayer, int styleBonus, boolean voidMelee)
	{
		int level = prayer.apply(Math.max(0, visibleStrength)) + styleBonus + 8;
		return voidMelee ? level * 11 / 10 : level;
	}

	/**
	 * floor(0.5 + effStr x (bonus + 64) / 640), written as integer division:
	 * floor((effStr x (bonus + 64) + 320) / 640).
	 */
	public static int maxHit(int effectiveStrength, int strengthBonus)
	{
		long product = (long) effectiveStrength * (strengthBonus + 64);
		if (product <= 0)
		{
			return 0;
		}
		return (int) Math.min(DAMAGE_CAP, (product + 320) / 640);
	}

	public static int maxHit(int visibleStrength, StrengthPrayer prayer, int styleBonus, boolean voidMelee, int strengthBonus)
	{
		return maxHit(effectiveStrength(visibleStrength, prayer, styleBonus, voidMelee), strengthBonus);
	}
}
