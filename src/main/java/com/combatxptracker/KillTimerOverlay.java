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
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The live kill timer: the boss fight's time so far against your personal best, gold while
 * you're on pace for a new one. Only reads what the plugin works out on the client thread.
 */
class KillTimerOverlay extends OverlayPanel
{
	private static final Color OFF_PACE = new Color(240, 110, 100);

	private final Client client;
	private final CombatXpTrackerPlugin plugin;

	@Inject
	private KillTimerOverlay(Client client, CombatXpTrackerPlugin plugin)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		KillTimer timer = plugin.getKillTimer();
		if (timer == null || !timer.isShowing())
		{
			return null;
		}
		double elapsed = timer.elapsed(client.getTickCount());
		BossTimes.Times times = plugin.getBossTimes(timer.getBoss());
		double best = times == null ? 0 : times.best;
		Color colour = paceColour(elapsed, best, timer.isNewBest());

		panelComponent.setPreferredSize(new Dimension(150, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(timer.isNewBest() ? timer.getBoss() + ": new PB!" : timer.getBoss())
			.color(timer.isNewBest() ? Theme.GOLD : Color.WHITE)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left(timer.isFinished() ? "Kill time:" : "Time:")
			.right(clock(elapsed))
			.rightColor(colour)
			.build());
		if (best > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("PB:")
				.right(clock(best))
				.build());
		}
		return super.render(graphics);
	}

	/**
	 * Gold while on pace for a new personal best (or once one is set), red once it's out of
	 * reach, white with no best yet.
	 */
	static Color paceColour(double elapsed, double best, boolean newBest)
	{
		if (newBest || best > 0 && elapsed <= best)
		{
			return Theme.GOLD;
		}
		return best > 0 ? OFF_PACE : Color.WHITE;
	}

	/**
	 * 65.4 -> "1:05.4".
	 */
	static String clock(double seconds)
	{
		long tenths = Math.round(seconds * 10);
		return String.format(Locale.US, "%d:%02d.%d", tenths / 600, tenths / 10 % 60, tenths % 10);
	}
}
