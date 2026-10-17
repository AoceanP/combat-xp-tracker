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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The big drop reveal on the game screen, for players who turn it on (it's off by default,
 * and the sidebar shows it either way). Spins, lands, then stays a few seconds and goes.
 */
class DropRevealOverlay extends Overlay
{
	/**
	 * How long it stays after landing: 4 seconds.
	 */
	static final int HOLD_MILLIS = 4_000;
	private static final int SLOT = 36;
	private static final int VISIBLE_SLOTS = 5;
	private static final int PAD = 8;
	private static final Color BACKGROUND = new Color(20, 20, 20, 225);

	private final CombatXpTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final Map<Integer, Image> icons = new HashMap<>();
	private int stripSequence = -1;
	private List<Integer> strip;

	@Inject
	private DropRevealOverlay(CombatXpTrackerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		DropReveal reveal = plugin.getScreenReveal();
		if (reveal == null)
		{
			return null;
		}
		long elapsed = System.currentTimeMillis() - plugin.getRevealStartMillis();
		if (elapsed > DropRevealCard.SPIN_MILLIS + HOLD_MILLIS)
		{
			return null;
		}
		if (reveal.getSequence() != stripSequence)
		{
			stripSequence = reveal.getSequence();
			strip = DropRevealCard.stripFor(reveal, new Random(reveal.getSequence()));
		}
		boolean landed = elapsed >= DropRevealCard.SPIN_MILLIS;

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		int width = VISIBLE_SLOTS * SLOT + PAD * 2;
		int stripTop = PAD + fm.getHeight() + 2;
		int height = stripTop + SLOT + 6 + fm.getHeight() + PAD;

		g.setColor(BACKGROUND);
		g.fillRect(0, 0, width, height);
		g.setColor(Theme.GOLD);
		g.drawRect(0, 0, width - 1, height - 1);

		String headline = landed ? reveal.headline() : "Opening...";
		g.setColor(landed ? Theme.GOLD : Theme.MUTED);
		g.drawString(headline, (width - fm.stringWidth(headline)) / 2, PAD + fm.getAscent());

		// The strip, clipped to the box, with slot DropRevealCard.LANDING ending under the marker.
		int centre = width / 2;
		Graphics2D clip = (Graphics2D) g.create(PAD, stripTop, width - PAD * 2, SLOT);
		double travelled = DropRevealCard.progress(elapsed) * DropRevealCard.LANDING * SLOT;
		for (int i = 0; i < strip.size(); i++)
		{
			int x = (int) Math.round(centre - PAD - SLOT / 2.0 + i * SLOT - travelled);
			if (x + SLOT < 0 || x > width)
			{
				continue;
			}
			clip.setColor(i == DropRevealCard.LANDING && landed ? Theme.highlight(Theme.GOLD, 0.7f) : Theme.SLOT);
			clip.fillRect(x + 1, 0, SLOT - 2, SLOT);
			Image image = icon(strip.get(i));
			if (image != null)
			{
				clip.drawImage(image, x + (SLOT - image.getWidth(null)) / 2, (SLOT - image.getHeight(null)) / 2, null);
			}
		}
		clip.dispose();
		g.setColor(Theme.GOLD);
		g.setStroke(new BasicStroke(2));
		g.drawRect(centre - SLOT / 2, stripTop - 1, SLOT, SLOT + 1);

		if (landed)
		{
			String item = reveal.getItem();
			g.setColor(Color.WHITE);
			g.drawString(item, (width - fm.stringWidth(item)) / 2, stripTop + SLOT + 6 + fm.getAscent());
		}
		return new Dimension(width, height);
	}

	private Image icon(int id)
	{
		Image cached = icons.get(id);
		if (cached != null || id <= 0 || itemManager == null)
		{
			return cached;
		}
		Image image = itemManager.getImage(id);
		icons.put(id, image);
		return image;
	}
}
