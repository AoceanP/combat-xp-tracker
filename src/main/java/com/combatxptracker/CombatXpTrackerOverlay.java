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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Optional on-screen overlay with the panel's key numbers: average and biggest hit, max
 * hit, and each goal's XP/hr and progress.
 *
 * Runs every frame, so it only reads values the plugin has already worked out on the
 * client thread. The max hit is recalculated once per tick, not here.
 */
public class CombatXpTrackerOverlay extends OverlayPanel
{
	private static final Color GOAL_REACHED = new Color(96, 220, 140);

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

		panelComponent.setPreferredSize(new Dimension(190, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Combat & XP Tracker")
			.color(config.goalBarColor())
			.build());

		HitStats hitStats = plugin.getHitStats();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Avg hit:")
			.right(String.format(Locale.US, "%.1f", hitStats.getAverageDamage()))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Biggest hit:")
			.right(String.valueOf(hitStats.getMaxHit()))
			.build());

		MaxHitCalculator.Result maxHit = plugin.getMaxHitResult();
		if (config.showMeleeMaxHit() && maxHit != null && maxHit.getMaxHit() >= 0)
		{
			String right = String.valueOf(maxHit.getMaxHit());
			if (maxHit.isTargetOnTask() && maxHit.getOnTaskMaxHit() >= 0)
			{
				right = maxHit.getOnTaskMaxHit() + " (task)";
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(maxHit.getStyle().getDisplayName() + " max:")
				.right(right)
				.rightColor(maxHit.getStyle().getColor())
				.build());
		}

		for (Map.Entry<Skill, SkillProgress> entry : plugin.getSkillProgress().entrySet())
		{
			Skill skill = entry.getKey();
			SkillProgress progress = entry.getValue();
			if (!progress.isGoalSet() || progress.isDismissedFromOverlay())
			{
				continue;
			}

			boolean reached = progress.isGoalReached();
			String right = reached
				? "Goal reached!"
				: String.format(Locale.US, "%s/hr (%d%%)",
					Formatting.compactXp(progress.getXpPerHour(config.xpRateMode())),
					(int) Math.floor(progress.getProgressToGoal() * 100));

			// Best-effort pairing of this skill's latest XP drop with a hit that landed at
			// about the same time. The game doesn't say which hit caused which drop.
			if (!reached && config.showCombinedDrop() && CombinedDropTracker.isCombatSkill(skill))
			{
				long lastUpdate = progress.getLastUpdateMillis();
				if (lastUpdate >= 0)
				{
					int paired = plugin.getCombinedDropTracker().getPairedDamage(lastUpdate, config.combinedDropWindowMillis());
					if (paired >= 0)
					{
						right += " hit " + paired;
					}
				}
			}

			panelComponent.getChildren().add(LineComponent.builder()
				.left(Formatting.capitalize(skill.getName()) + ":")
				.right(right)
				.rightColor(reached ? GOAL_REACHED : Color.WHITE)
				.build());
		}

		return super.render(graphics);
	}
}
