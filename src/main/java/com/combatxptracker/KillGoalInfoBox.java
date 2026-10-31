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
import java.util.Map;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * WHAT: An infobox for one kill count goal: "42%", then "Done" once reached.
 * WHY: Skill goals have an infobox; a kill count goal deserves the same glance on screen (2.5).
 * PRE: Made and removed on the client thread.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
class KillGoalInfoBox extends InfoBox
{
	private static final Color GOAL_REACHED = new Color(96, 220, 140);

	private final CombatXpTrackerPlugin plugin;
	private final String monster;

	KillGoalInfoBox(BufferedImage image, CombatXpTrackerPlugin plugin, String monster)
	{
		super(image, plugin);
		this.plugin = plugin;
		this.monster = monster;
		setPriority(InfoBoxPriority.LOW);
	}

	/**
	 * WHAT: The monster this goal counts.
	 * WHY: The plugin matches infoboxes to goals by name when goals change.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: the monster's name
	 * THROWS: Nothing.
	 */
	String getMonster()
	{
		return monster;
	}

	private int target()
	{
		return plugin.getKillGoalTarget(monster);
	}

	@Override
	public String getText()
	{
		int target = target();
		return target <= 0 ? "" : KillGoals.percentText(plugin.getAllTimeKills(monster), target);
	}

	@Override
	public Color getTextColor()
	{
		int target = target();
		return target > 0 && plugin.getAllTimeKills(monster) >= target ? GOAL_REACHED : Color.WHITE;
	}

	@Override
	public boolean render()
	{
		return target() > 0;
	}

	@Override
	public String getTooltip()
	{
		int target = target();
		if (target <= 0)
		{
			return "";
		}
		int kills = plugin.getAllTimeKills(monster);
		if (kills >= target)
		{
			return monster + ": goal of " + Formatting.withCommas(target) + " kills reached";
		}
		return monster + ": " + Formatting.withCommas(kills) + " / " + Formatting.withCommas(target) + " kills"
			+ "</br>" + Formatting.withCommas(target - kills) + " kills left";
	}

	/**
	 * WHAT: Whether this infobox's goal is still in a set of goals.
	 * WHY: Removed goals take their infobox with them.
	 * PRE: goals is not null.
	 * POST: Nothing changes.
	 * RETURN: true if the goal still exists
	 * THROWS: Nothing.
	 */
	boolean stillIn(Map<String, Integer> goals)
	{
		return goals.containsKey(monster);
	}
}
