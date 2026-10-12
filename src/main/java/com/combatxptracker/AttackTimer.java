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
 * Ticks until the player can attack again, from their weapon's attack speed (OSRS Wiki,
 * "Attack speed").
 *
 * Attacks are spotted from the player's attack animation and from combat XP drops, so it's
 * a close estimate rather than the game's own timer. Used on the client thread only.
 */
final class AttackTimer
{
	/**
	 * Spells cast from a normal staff or manually: 5 ticks.
	 */
	static final int SPELL_TICKS = 5;
	/**
	 * Standard spellbook spells with the Harmonised nightmare staff: 4 ticks.
	 */
	static final int HARMONISED_TICKS = 4;
	/**
	 * Unarmed attacks.
	 */
	static final int UNARMED_TICKS = 4;
	/**
	 * With no attack for this long, the player isn't fighting and the timer hides.
	 */
	static final int IDLE_TICKS = 16;

	private int lastAttackTick = -1;
	// An attack animation that started before the game said who the player is fighting.
	private int pendingAnimationTick = -1;

	/**
	 * @param weaponSpeed        the weapon's attack speed in ticks, or 0 when unarmed
	 * @param poweredStaff       a trident, Sanguinesti staff or other built-in spell weapon
	 * @param harmonisedStandard the Harmonised nightmare staff on the standard spellbook
	 */
	static int ticksBetweenAttacks(int weaponSpeed, CombatStyle.AttackStyle style, boolean poweredStaff,
		boolean harmonisedStandard)
	{
		int speed = weaponSpeed > 0 ? weaponSpeed : UNARMED_TICKS;
		if (style == null)
		{
			return speed;
		}
		switch (style.getStyle())
		{
			case MAGIC:
				if (poweredStaff)
				{
					return speed;
				}
				return harmonisedStandard ? HARMONISED_TICKS : SPELL_TICKS;
			case RANGED:
				return "Rapid".equals(style.getName()) ? Math.max(1, speed - 1) : speed;
			default:
				return speed;
		}
	}

	/**
	 * Something that looks like an attack happened this tick. Ignored while the weapon is
	 * still cooling down, so a block animation or a second signal in the same attack
	 * doesn't restart the count.
	 */
	void onAttackSignal(int tick, int speed)
	{
		if (lastAttackTick < 0 || tick - lastAttackTick >= speed - 1 || tick < lastAttackTick)
		{
			lastAttackTick = tick;
		}
	}

	/**
	 * The player started an animation that could be an attack. On the first attack of a fight
	 * the game can send the animation a moment before it says who the player is fighting, so
	 * without a target yet it's held until {@link #confirmPending} sees one.
	 */
	void onAttackAnimation(int tick, boolean hasTarget, int speed)
	{
		if (hasTarget)
		{
			onAttackSignal(tick, speed);
			pendingAnimationTick = -1;
		}
		else
		{
			pendingAnimationTick = tick;
		}
	}

	/**
	 * Checked every client frame: a held animation counts as the attack (on the tick it
	 * started) once the player has a target, if that happens within the next tick.
	 */
	void confirmPending(int tick, boolean hasTarget, boolean animating, int speed)
	{
		if (pendingAnimationTick < 0)
		{
			return;
		}
		if (tick > pendingAnimationTick + 1 || tick < pendingAnimationTick || !animating)
		{
			pendingAnimationTick = -1;
		}
		else if (hasTarget)
		{
			onAttackSignal(pendingAnimationTick, speed);
			pendingAnimationTick = -1;
		}
	}

	/**
	 * @return ticks until the next attack (0 = ready now), or -1 when not fighting
	 */
	int ticksLeft(int tick, int speed)
	{
		if (lastAttackTick < 0 || tick - lastAttackTick > IDLE_TICKS || tick < lastAttackTick)
		{
			return -1;
		}
		return Math.max(0, speed - (tick - lastAttackTick));
	}

	void reset()
	{
		lastAttackTick = -1;
		pendingAnimationTick = -1;
	}
}
