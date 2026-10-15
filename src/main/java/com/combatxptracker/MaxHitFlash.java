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

import java.awt.Color;

/**
 * Notices the max hit going up or down (a potion, a prayer, a gear swap, a boost wearing
 * off) so it can flash green or red for a few seconds. Changing combat style (melee to
 * ranged) isn't a change of max hit, so it doesn't flash. Client thread only.
 */
final class MaxHitFlash
{
	/**
	 * How long a change shows: 5 ticks, 3 seconds.
	 */
	static final int FLASH_TICKS = 5;
	static final Color UP = new Color(96, 220, 140);
	static final Color DOWN = new Color(240, 90, 90);

	private CombatStyle style;
	private int maxHit = -1;
	private int change;
	private int changeTick = -1;

	/**
	 * The max hit worked out this tick.
	 *
	 * @param maxHit the max hit, or -1 if it isn't known
	 */
	void update(CombatStyle newStyle, int newMaxHit, int tick)
	{
		if (newStyle == style && maxHit >= 0 && newMaxHit >= 0 && newMaxHit != maxHit)
		{
			change = newMaxHit - maxHit;
			changeTick = tick;
		}
		else if (newStyle != style)
		{
			changeTick = -1;
		}
		style = newStyle;
		maxHit = newMaxHit;
	}

	/**
	 * @return how much the max hit just changed by (+3, -1), or 0 when it isn't flashing
	 */
	int change(int tick)
	{
		if (changeTick < 0 || tick < changeTick || tick - changeTick >= FLASH_TICKS)
		{
			return 0;
		}
		return change;
	}

	/**
	 * e.g. "(+3)" or "(-1)".
	 */
	static String label(int change)
	{
		return "(" + (change > 0 ? "+" : "") + change + ")";
	}

	void reset()
	{
		style = null;
		maxHit = -1;
		changeTick = -1;
	}
}
