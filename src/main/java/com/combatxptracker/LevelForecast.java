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

import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * "Kills to next level" and "what the rest of my slayer task gives", as pure maths.
 */
final class LevelForecast
{
	private LevelForecast()
	{
	}

	/**
	 * Kills left to the next level in one skill.
	 */
	static final class Row
	{
		private final Skill skill;
		private final int nextLevel;
		private final int kills;
		private final double xpPerKill;
		private final int measuredKills;

		Row(Skill skill, int nextLevel, int kills, double xpPerKill, int measuredKills)
		{
			this.skill = skill;
			this.nextLevel = nextLevel;
			this.kills = kills;
			this.xpPerKill = xpPerKill;
			this.measuredKills = measuredKills;
		}

		Skill getSkill()
		{
			return skill;
		}

		int getNextLevel()
		{
			return nextLevel;
		}

		int getKills()
		{
			return kills;
		}

		double getXpPerKill()
		{
			return xpPerKill;
		}

		/**
		 * @return how many kills the XP per kill was measured over, or 0 if it's the
		 * hitpoints estimate
		 */
		int getMeasuredKills()
		{
			return measuredKills;
		}
	}

	/**
	 * XP a skill gets from the rest of a task.
	 */
	static final class TaskGain
	{
		private final Skill skill;
		private final int xp;
		private final int levelNow;
		private final int levelAfter;

		TaskGain(Skill skill, int xp, int levelNow, int levelAfter)
		{
			this.skill = skill;
			this.xp = xp;
			this.levelNow = levelNow;
			this.levelAfter = levelAfter;
		}

		Skill getSkill()
		{
			return skill;
		}

		int getXp()
		{
			return xp;
		}

		int getLevelNow()
		{
			return levelNow;
		}

		int getLevelAfter()
		{
			return levelAfter;
		}
	}

	/**
	 * @return the row, or null if XP per kill is unknown or the skill is maxed
	 */
	static Row killsToLevel(Skill skill, int currentXp, double xpPerKill, int measuredKills)
	{
		if (xpPerKill <= 0 || currentXp < 0 || currentXp >= Experience.MAX_SKILL_XP)
		{
			return null;
		}
		int level = Experience.getLevelForXp(currentXp);
		if (level >= Experience.MAX_VIRT_LEVEL)
		{
			return null;
		}
		int kills = KillXp.killsLeft(Experience.getXpForLevel(level + 1) - currentXp, xpPerKill);
		return kills > 0 ? new Row(skill, level + 1, kills, xpPerKill, measuredKills) : null;
	}

	/**
	 * @return the gain, or null if XP per kill is unknown
	 */
	static TaskGain taskGain(Skill skill, int currentXp, double xpPerKill, int killsLeft)
	{
		if (xpPerKill <= 0 || killsLeft <= 0 || currentXp < 0)
		{
			return null;
		}
		int gain = (int) Math.min(Experience.MAX_SKILL_XP, Math.round(xpPerKill * killsLeft));
		int after = (int) Math.min(Experience.MAX_SKILL_XP, (long) currentXp + gain);
		return new TaskGain(skill, after - currentXp, Experience.getLevelForXp(currentXp), Experience.getLevelForXp(after));
	}
}
