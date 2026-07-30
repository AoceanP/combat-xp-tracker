/*
 * Copyright (c) 2026, YourNameHere <https://github.com/YourNameHere>
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
 * An infobox (rendered near the minimap by RuneLite's InfoBoxOverlay) showing progress
 * toward the goal for a single skill.
 *
 * One of these exists per goal-tracked skill. The text is the percentage complete, so a
 * glance during combat tells you how close you are without opening the sidebar.
 */
public class GoalInfoBox extends InfoBox
{
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

	@Override
	public String getText()
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		if (progress == null)
		{
			return "";
		}
		if (progress.getCurrentLevel() >= progress.getGoalLevel())
		{
			return "done";
		}
		return Math.round(progress.getProgressToGoal() * 100) + "%";
	}

	@Override
	public Color getTextColor()
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		if (progress != null && progress.getCurrentLevel() >= progress.getGoalLevel())
		{
			return Color.GREEN;
		}
		return Color.WHITE;
	}

	@Override
	public boolean render()
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		return progress != null && progress.isGoalSet();
	}

	@Override
	public String getTooltip()
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		if (progress == null)
		{
			return "";
		}
		String name = skill.getName();
		if (progress.getCurrentLevel() >= progress.getGoalLevel())
		{
			return name + ": goal of " + progress.getGoalLevel() + " reached";
		}
		int rate = progress.getXpPerHour();
		String eta = "";
		double hours = progress.getEstimatedHoursToGoal();
		if (hours > 0)
		{
			eta = String.format(" (~%.1fh at %,d xp/hr)", hours, rate);
		}
		return String.format("%s: level %d \u2192 %d, %,d xp to go%s",
			name, progress.getCurrentLevel(), progress.getGoalLevel(),
			progress.getXpRemainingToGoal(), eta);
	}
}
