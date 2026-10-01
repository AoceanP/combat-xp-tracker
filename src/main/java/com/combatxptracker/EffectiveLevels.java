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
 * Effective levels: a stat with prayer, attack style, the hidden +8 (+9 for Magic) and Void
 * included. They're what the game's accuracy and max hit rolls are built on (OSRS Wiki,
 * "Maximum melee hit", "Combat#Accuracy"). Integer maths like the max hit classes.
 */
final class EffectiveLevels
{
	private EffectiveLevels()
	{
	}

	/**
	 * Attack-boosting prayers, as percentages.
	 */
	enum AccuracyPrayer
	{
		NONE(0),
		// Melee
		CLARITY_OF_THOUGHT(5),
		IMPROVED_REFLEXES(10),
		INCREDIBLE_REFLEXES(15),
		CHIVALRY(15),
		PIETY(20),
		// Ranged
		SHARP_EYE(5),
		HAWK_EYE(10),
		EAGLE_EYE(15),
		DEADEYE(18),
		RIGOUR(20),
		// Magic
		MYSTIC_WILL(5),
		MYSTIC_LORE(10),
		MYSTIC_MIGHT(15),
		MYSTIC_VIGOUR(18),
		AUGURY(25);

		private final int percent;

		AccuracyPrayer(int percent)
		{
			this.percent = percent;
		}

		int apply(int level)
		{
			return level * (100 + percent) / 100;
		}
	}

	/**
	 * Effective Attack, or effective Ranged accuracy: floor((floor(level x prayer) + style + 8) x void).
	 *
	 * @param styleBonus +3 for Accurate, +1 for Controlled, 0 otherwise
	 */
	static int attack(int visibleLevel, AccuracyPrayer prayer, int styleBonus, boolean voidSet)
	{
		int level = prayer.apply(Math.max(0, visibleLevel)) + styleBonus + 8;
		return voidSet ? level * 11 / 10 : level;
	}

	/**
	 * Effective Magic accuracy: floor(floor(level x prayer) + style + 9), times 1.45 with a
	 * full Void mage set.
	 *
	 * @param styleBonus +2 for Accurate on a powered staff, 0 otherwise
	 */
	static int magic(int visibleLevel, AccuracyPrayer prayer, int styleBonus, boolean voidMage)
	{
		int level = prayer.apply(Math.max(0, visibleLevel)) + styleBonus + 9;
		return voidMage ? level * 145 / 100 : level;
	}
}
