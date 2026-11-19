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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * WHAT: Draws action pops over your character: each rises a little and fades out.
 * WHY: A quiet, cosmetic confirmation of what you just equipped, drank or ate.
 * PRE: Registered by the plugin; renders on the client thread.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
class ActionPopOverlay extends Overlay
{
	/** Pixels the pops start above your head, how far each rises, and the gap between rows. */
	private static final int HEIGHT_ABOVE_HEAD = 70;
	private static final int RISE = 18;
	private static final int ROW = 16;

	private final Client client;
	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;
	private final ItemManager itemManager;

	@Inject
	ActionPopOverlay(Client client, CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * WHAT: Draws the pops still showing, newest at the bottom.
	 * WHY: Stacked rows keep a quick switch of several items readable.
	 * PRE: Client thread.
	 * POST: The pops are drawn, or nothing when turned off.
	 * RETURN: null (a dynamic overlay)
	 * THROWS: Nothing.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.actionPops())
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		long now = System.currentTimeMillis();
		List<ActionPops.Pop> pops = plugin.getActionPops().showing(now);
		if (player == null || pops.isEmpty())
		{
			return null;
		}
		graphics.setFont(FontManager.getRunescapeBoldFont());
		Composite old = graphics.getComposite();
		for (int i = 0; i < pops.size(); i++)
		{
			ActionPops.Pop pop = pops.get(i);
			double age = Math.min(1, (now - pop.start) / (double) ActionPops.SHOW_MILLIS);
			int row = pops.size() - 1 - i;
			Point at = player.getCanvasTextLocation(graphics, pop.text, player.getLogicalHeight() + HEIGHT_ABOVE_HEAD);
			if (at == null)
			{
				continue;
			}
			int x = at.getX();
			int y = at.getY() - (int) (age * RISE) - row * ROW;
			float alpha = (float) (age < 0.7 ? 1 : 1 - (age - 0.7) / 0.3);
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
			BufferedImage icon = pop.itemId > 0 ? itemManager.getImage(pop.itemId) : null;
			if (icon != null)
			{
				int size = 18;
				graphics.drawImage(icon, x - size - 2, y - size + 3, size, size, null);
			}
			graphics.setColor(Color.BLACK);
			graphics.drawString(pop.text, x + 1, y + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(pop.text, x, y);
		}
		graphics.setComposite(old);
		return null;
	}
}
