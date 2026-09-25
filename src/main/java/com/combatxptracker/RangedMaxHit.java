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
 * The ranged max hit formula from the OSRS Wiki's "Maximum ranged hit" page:
 *
 * <pre>
 * Effective Ranged Strength = floor((floor(Visible Ranged x Prayer) + Style + 8) x Void)
 * Max Hit                   = floor(0.5 + Effective x (Ranged Strength + 64) / 640)
 * Gear bonus                = floor(Max Hit x Bonus)
 * </pre>
 *
 * Integer maths only, for the same reason as {@link MeleeMaxHit}.
 */
public final class RangedMaxHit
{
	private RangedMaxHit()
	{
	}

	public enum RangedPrayer
	{
		NONE("No prayer", 100),
		SHARP_EYE("Sharp Eye", 105),
		HAWK_EYE("Hawk Eye", 110),
		EAGLE_EYE("Eagle Eye", 115),
		DEADEYE("Deadeye", 118),
		RIGOUR("Rigour", 123);

		private final String displayName;
		private final int percent;

		RangedPrayer(String displayName, int percent)
		{
			this.displayName = displayName;
			this.percent = percent;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}

	public enum VoidSet
	{
		NONE(1, 1),
		/** Full Void ranged: x1.1 */
		VOID(11, 10),
		/** Full Elite Void ranged: x1.125 */
		ELITE(9, 8);

		private final int numerator;
		private final int denominator;

		VoidSet(int numerator, int denominator)
		{
			this.numerator = numerator;
			this.denominator = denominator;
		}
	}

	public enum TargetBonus
	{
		/** Black mask (i) / Slayer helmet (i) on task: x1.15. The non-imbued versions do nothing for ranged. */
		SLAYER_HELM_I("Slayer helm (i)", 23, 20),
		/** Salve amulet(i) against undead: x7/6. */
		SALVE_I("Salve amulet(i)", 7, 6),
		/** Salve amulet(ei) against undead: x1.2. */
		SALVE_EI("Salve amulet(ei)", 6, 5);

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
			return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * numerator / denominator);
		}
	}

	/**
	 * @param styleBonus +3 on accurate, 0 on rapid and longrange
	 */
	public static int effectiveRangedStrength(int visibleRanged, RangedPrayer prayer, int styleBonus, VoidSet voidSet)
	{
		int level = Math.max(0, visibleRanged) * prayer.percent / 100 + styleBonus + 8;
		return level * voidSet.numerator / voidSet.denominator;
	}

	public static int maxHit(int effectiveRangedStrength, int rangedStrengthBonus)
	{
		return MeleeMaxHit.maxHit(effectiveRangedStrength, rangedStrengthBonus);
	}

	public static int maxHit(int visibleRanged, RangedPrayer prayer, int styleBonus, VoidSet voidSet, int rangedStrengthBonus)
	{
		return maxHit(effectiveRangedStrength(visibleRanged, prayer, styleBonus, voidSet), rangedStrengthBonus);
	}
}
