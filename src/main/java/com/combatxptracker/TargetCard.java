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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The monster you're fighting right now, at the top of the Monsters tab: its health, hits
 * left to kill it at your average hit, your time per kill and your damage per second.
 */
final class TargetCard extends JPanel
{
	private final JLabel nameLabel = Theme.boldLabel("", Theme.TEXT);
	private final HealthBar bar = new HealthBar();
	private final JLabel hitsLeftValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel perKillValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel dpsValue = Theme.boldLabel("-", Theme.TEXT);

	TargetCard()
	{
		super(new BorderLayout(0, 5));
		setBackground(Theme.CARD);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT),
			BorderFactory.createEmptyBorder(6, 7, 7, 8)));

		JPanel title = new JPanel(new BorderLayout(6, 0));
		title.setOpaque(false);
		title.add(nameLabel, BorderLayout.CENTER);
		title.add(Theme.label("Fighting now", Theme.SUBTLE), BorderLayout.EAST);

		JPanel tiles = new JPanel(new GridLayout(1, 3, 3, 0));
		tiles.setOpaque(false);
		JPanel hitsTile = MonsterCard.tile(hitsLeftValue, "Hits left");
		hitsTile.setToolTipText("Hits to finish it at your average hit on it this session, 0s included");
		JPanel killTile = MonsterCard.tile(perKillValue, "Per kill");
		killTile.setToolTipText("Your average time per kill on it this session, including time between kills");
		JPanel dpsTile = MonsterCard.tile(dpsValue, "DPS");
		dpsTile.setToolTipText("Your damage per second while fighting it this session");
		tiles.add(hitsTile);
		tiles.add(killTile);
		tiles.add(dpsTile);

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setOpaque(false);
		top.add(title, BorderLayout.NORTH);
		top.add(bar, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);
		add(tiles, BorderLayout.CENTER);
	}

	/**
	 * @param averagePerHit your average damage per hit on it, 0s included, or 0 if unknown
	 * @param perKillMillis your time per kill, or -1 if unknown
	 * @param dps           your damage per second on it, or -1 if unknown
	 */
	void update(TargetInfo target, double averagePerHit, long perKillMillis, double dps)
	{
		nameLabel.setText(target.getName());
		bar.set(target.getHealthShare(), target.getHitpoints() >= 0 && target.getMaxHitpoints() > 0
			? Formatting.withCommas(target.getHitpoints()) + " / " + Formatting.withCommas(target.getMaxHitpoints())
			: "Health unknown");
		int hits = target.hitsLeft(averagePerHit);
		hitsLeftValue.setText(hits >= 0 ? "~" + Formatting.withCommas(hits) : "-");
		perKillValue.setText(perKillMillis > 0 ? BossTimes.format(Math.round(perKillMillis / 1000.0)) : "-");
		dpsValue.setText(dps >= 0 ? String.format(java.util.Locale.US, "%.1f", dps) : "-");
	}

	/**
	 * A thin health bar with the hitpoints written on it.
	 */
	private static final class HealthBar extends JComponent
	{
		private static final Color HEALTHY = new Color(48, 150, 72);
		private static final Color LOW = new Color(180, 52, 44);

		private double share = -1;
		private String text = "";

		HealthBar()
		{
			setPreferredSize(new Dimension(0, 16));
			setFont(Theme.label("", Theme.TEXT).getFont());
		}

		void set(double share, String text)
		{
			this.share = share;
			this.text = text;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			g2.setColor(Theme.TRACK);
			g2.fillRect(0, 0, w, h);
			if (share >= 0)
			{
				g2.setColor(share <= 0.25 ? LOW : HEALTHY);
				g2.fillRect(0, 0, (int) Math.round(w * share), h);
			}
			g2.setColor(Theme.BORDER);
			g2.drawRect(0, 0, w - 1, h - 1);
			FontMetrics fm = g2.getFontMetrics();
			int x = (w - fm.stringWidth(text)) / 2;
			int y = (h - fm.getHeight()) / 2 + fm.getAscent();
			g2.setColor(Color.BLACK);
			g2.drawString(text, x + 1, y + 1);
			g2.setColor(Color.WHITE);
			g2.drawString(text, x, y);
			g2.dispose();
		}
	}
}
