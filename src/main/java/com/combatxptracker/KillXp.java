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

import net.runelite.api.Skill;

/**
 * XP a skill gets from killing a monster, from its hitpoints and the attack style
 * (OSRS Wiki, "Combat"):
 *
 * <ul>
 *   <li>Melee and ranged: 4 XP per damage in the trained skill</li>
 *   <li>Controlled melee: 1.33 each to Attack, Strength and Defence</li>
 *   <li>Longrange: 2 to Ranged and 2 to Defence</li>
 *   <li>Hitpoints: 1.33 per damage with any style</li>
 *   <li>Slayer (on task): the monster's hitpoints</li>
 * </ul>
 *
 * Some high-defence monsters give more XP per damage, so this is an estimate. Magic
 * isn't covered: it also gets XP per cast, which hitpoints can't predict.
 */
final class KillXp
{
	private KillXp()
	{
	}

	/**
	 * @return XP per kill, or -1 when it can't be worked out for this skill and style
	 */
	static double perKill(Skill skill, CombatStyle.AttackStyle style, int monsterHitpoints)
	{
		if (monsterHitpoints <= 0)
		{
			return -1;
		}
		if (skill == Skill.HITPOINTS)
		{
			return monsterHitpoints * 4 / 3.0;
		}
		if (skill == Skill.SLAYER)
		{
			return monsterHitpoints;
		}
		if (style == null)
		{
			return -1;
		}

		String name = style.getName();
		switch (style.getStyle())
		{
			case MELEE:
				if ("Controlled".equals(name))
				{
					return isMeleeSkill(skill) ? monsterHitpoints * 4 / 3.0 : -1;
				}
				return skill == meleeSkillFor(name) ? monsterHitpoints * 4.0 : -1;
			case RANGED:
				if ("Longrange".equals(name))
				{
					return skill == Skill.RANGED || skill == Skill.DEFENCE ? monsterHitpoints * 2.0 : -1;
				}
				return skill == Skill.RANGED ? monsterHitpoints * 4.0 : -1;
			default:
				return -1;
		}
	}

	/**
	 * @return kills to go, or -1 if XP per kill can't be worked out
	 */
	static int killsLeft(int xpRemaining, double xpPerKill)
	{
		if (xpPerKill <= 0 || xpRemaining < 0)
		{
			return -1;
		}
		return (int) Math.ceil(xpRemaining / xpPerKill);
	}

	private static boolean isMeleeSkill(Skill skill)
	{
		return skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE;
	}

	private static Skill meleeSkillFor(String styleName)
	{
		switch (styleName)
		{
			case "Accurate":
				return Skill.ATTACK;
			case "Aggressive":
				return Skill.STRENGTH;
			case "Defensive":
				return Skill.DEFENCE;
			default:
				return null;
		}
	}
}
