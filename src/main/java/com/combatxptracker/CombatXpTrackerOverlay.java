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
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * On-screen overlay mirroring the sidebar panel's key numbers (average damage, max hit,
 * and each tracked skill's XP/hr + progress to goal) so the info stays visible without
 * needing the panel open, matching the pattern the core XP Tracker plugin uses for its
 * optional canvas overlays.
 */
public class CombatXpTrackerOverlay extends OverlayPanel
{
	private final Client client;
	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;

	@Inject
	private CombatXpTrackerOverlay(Client client, CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		panelComponent.setPreferredSize(new Dimension(200, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Combat & XP Tracker")
			.color(Color.ORANGE)
			.build());

		HitStats hitStats = plugin.getHitStats();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Avg damage:")
			.right(String.format("%.2f", hitStats.getAverageDamage()))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Biggest hit:")
			.right(String.valueOf(hitStats.getMaxHit()))
			.build());

		// Only show skills the player has actually gained XP in recently, so the overlay
		// doesn't list all 23 skills at all times and crowd the screen.
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			SkillProgress progress = plugin.getSkillProgress().get(skill);
			if (progress == null || progress.getXpPerHour() <= 0 || progress.isDismissedFromOverlay())
			{
				continue;
			}

			int level = progress.getCurrentLevel();
			int goal = progress.getGoalLevel();
			boolean goalReached = level >= goal;
			String rightText = goalReached
				? "Goal reached!"
				: String.format("%,d xp/hr (%d%%)", progress.getXpPerHour(), Math.round(progress.getProgressToGoal() * 100));

			// If this is a combat skill and a hit landed close enough in time to this
			// skill's most recent XP gain, append the paired damage. This is a best-effort
			// timing correlation, not a guaranteed causal link -- see CombinedDropTracker's
			// class doc for why (RuneLite doesn't expose which hit caused which XP drop).
			if (config.showCombinedDrop() && CombinedDropTracker.isCombatSkill(skill))
			{
				long lastUpdate = progress.getLastUpdateMillis();
				if (lastUpdate >= 0)
				{
					int pairedDamage = plugin.getCombinedDropTracker()
						.getPairedDamage(lastUpdate, config.combinedDropWindowMillis());
					if (pairedDamage >= 0)
					{
						rightText += " (hit: " + pairedDamage + ")";
					}
				}
			}

			panelComponent.getChildren().add(LineComponent.builder()
				.left(capitalize(skill.getName()) + ":")
				.right(rightText)
				.rightColor(goalReached ? Color.GREEN : Color.WHITE)
				.build());
		}

		return super.render(graphics);
	}

	private static String capitalize(String s)
	{
		if (s == null || s.isEmpty())
		{
			return s;
		}
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
}
