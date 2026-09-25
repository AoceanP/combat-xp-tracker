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
import java.awt.image.BufferedImage;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * Infobox showing one goal's progress as a percentage.
 */
public class GoalInfoBox extends InfoBox
{
	private static final Color GOAL_REACHED = new Color(96, 220, 140);

	private final Skill skill;
	private final CombatXpTrackerPlugin plugin;

	public GoalInfoBox(BufferedImage image, CombatXpTrackerPlugin plugin, Skill skill)
	{
		super(image, plugin);
		this.plugin = plugin;
		this.skill = skill;
		setPriority(InfoBoxPriority.LOW);
	}

	public Skill getSkill()
	{
		return skill;
	}

	private SkillProgress progress()
	{
		return plugin.getSkillProgress().get(skill);
	}

	@Override
	public String getText()
	{
		SkillProgress progress = progress();
		if (progress == null || !progress.isGoalSet())
		{
			return "";
		}
		if (progress.isGoalReached())
		{
			return "Done";
		}
		return (int) Math.floor(progress.getProgressToGoal() * 100) + "%";
	}

	@Override
	public Color getTextColor()
	{
		SkillProgress progress = progress();
		return progress != null && progress.isGoalReached() ? GOAL_REACHED : Color.WHITE;
	}

	@Override
	public boolean render()
	{
		SkillProgress progress = progress();
		return progress != null && progress.isGoalSet();
	}

	@Override
	public String getTooltip()
	{
		SkillProgress progress = progress();
		Goal goal = progress == null ? null : progress.getGoal();
		if (goal == null)
		{
			return "";
		}

		String name = Formatting.capitalize(skill.getName());
		if (progress.isGoalReached())
		{
			return name + ": goal of " + goal + " reached";
		}

		StringBuilder sb = new StringBuilder()
			.append(name).append(": ").append(goal)
			.append("</br>").append(Formatting.withCommas(progress.getXpRemainingToGoal())).append(" xp left");
		int rate = progress.getXpPerHour(plugin.getXpRateMode());
		if (rate > 0)
		{
			sb.append("</br>").append(Formatting.withCommas(rate)).append(" xp/hr, ")
				.append(Formatting.duration(progress.getEstimatedHoursToGoal(plugin.getXpRateMode()))).append(" to go");
		}
		return sb.toString();
	}
}
