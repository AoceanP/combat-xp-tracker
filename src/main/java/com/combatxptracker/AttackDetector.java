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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Spots the player starting an attack, once a tick, from their animation.
 *
 * Two things used to fool the attack timer:
 * <ul>
 * <li>Melee and magic attack animations last as long as the weapon's attack speed or longer,
 * so an animation still playing when the weapon was ready looked like a new attack. Only an
 * animation that starts (or restarts) this tick counts now.</li>
 * <li>Being hit plays a block animation, which looked like an attack when the weapon was
 * ready. Animations that came with a combat XP drop are learned as the weapon's attacks; an
 * unknown animation on a tick the player was hit is taken for a block.</li>
 * </ul>
 * Client thread only.
 */
final class AttackDetector
{
	/**
	 * What this tick's animation says.
	 */
	enum Result
	{
		NONE,
		// An animation started with a target, and it isn't known to be an attack.
		LIKELY,
		// An animation known to be this player's attack started.
		CERTAIN
	}

	// Plenty for every weapon and spell used in a session.
	private static final int MAX_LEARNED = 64;

	private final Set<Integer> nonAttacks;
	private final Set<Integer> attackAnimations = new LinkedHashSet<>();
	private int lastAnimation = -1;
	private int lastFrame = -1;
	private int startTick = -1;
	private boolean startedOnHit;
	private boolean animationSet;
	private boolean hitTaken;
	private boolean xpDrop;

	/**
	 * @param nonAttacks animations that are never an attack (eating, the common blocks)
	 */
	AttackDetector(Set<Integer> nonAttacks)
	{
		this.nonAttacks = nonAttacks;
	}

	/**
	 * The game set the player's animation (it may be the same one again).
	 */
	void onAnimationSet()
	{
		animationSet = true;
	}

	/**
	 * The player took a hit (a block animation may come with it).
	 */
	void onHitTaken()
	{
		hitTaken = true;
	}

	/**
	 * A combat XP drop from the player's own attack (not a cannon). The animation that just
	 * started is learned as an attack.
	 */
	void onXpDrop()
	{
		xpDrop = true;
	}

	boolean isKnownAttack(int animation)
	{
		return attackAnimations.contains(animation);
	}

	/**
	 * Called once each game tick.
	 *
	 * @param animation the player's animation, or -1
	 * @param frame     how far into it they are
	 * @param hasTarget whether they're targeting something that can be fought
	 */
	Result onTick(int tick, int animation, int frame, boolean hasTarget)
	{
		boolean started = animation != -1
			&& (animationSet || animation != lastAnimation || frame < lastFrame);
		boolean hit = hitTaken;
		if (started)
		{
			startTick = tick;
			startedOnHit = hit;
		}
		// The XP drop can come on the tick the animation starts or the one after. A block
		// that started as the player was hit is never learned.
		if (xpDrop && animation != -1 && !nonAttacks.contains(animation) && startTick >= 0
			&& tick - startTick <= 1 && tick >= startTick && !startedOnHit)
		{
			learn(animation);
		}
		animationSet = false;
		hitTaken = false;
		xpDrop = false;
		lastAnimation = animation;
		lastFrame = frame;

		if (!started || !hasTarget || nonAttacks.contains(animation))
		{
			return Result.NONE;
		}
		if (attackAnimations.contains(animation))
		{
			return Result.CERTAIN;
		}
		return hit ? Result.NONE : Result.LIKELY;
	}

	private void learn(int animation)
	{
		if (attackAnimations.contains(animation))
		{
			return;
		}
		if (attackAnimations.size() >= MAX_LEARNED)
		{
			attackAnimations.remove(attackAnimations.iterator().next());
		}
		attackAnimations.add(animation);
	}

	void reset()
	{
		lastAnimation = -1;
		lastFrame = -1;
		startTick = -1;
		startedOnHit = false;
		animationSet = false;
		hitTaken = false;
		xpDrop = false;
	}
}
