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
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.util.QuantityFormatter;

/**
 * How long until the player's oldest drop on the ground disappears.
 */
class LootDespawnOverlay extends OverlayPanel
{
	private static final Color WARN = new Color(255, 198, 64);

	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;

	@Inject
	LootDespawnOverlay(CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.lootDespawnAlert().isEnabled())
		{
			return null;
		}
		GroundLoot.Status status = plugin.getGroundLootStatus();
		if (status == null)
		{
			return null;
		}
		int seconds = (int) Math.round(status.getTicksLeft() * 0.6);
		boolean warn = seconds <= config.lootDespawnSeconds();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Loot despawns")
			.right(String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60))
			.rightColor(warn ? WARN : Color.WHITE)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(status.getItems() + (status.getItems() == 1 ? " drop" : " drops"))
			.right(QuantityFormatter.quantityToStackSize(status.getValue()) + " gp")
			.rightColor(WARN)
			.build());
		return super.render(graphics);
	}
}
