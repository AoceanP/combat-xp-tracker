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
	private final InfoRow weaknessRow = new InfoRow("Weak to");
	private final InfoRow foodRow = new InfoRow("Food lasts");
	private final InfoRow prayerRow = new InfoRow("Prayer lasts");
	private final InfoRow.Section rows = new InfoRow.Section(null);

	TargetCard()
	{
		super(new BorderLayout(0, 5));
		// Painted as a rounded card with a green outline (see paintComponent).
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

		JPanel title = new JPanel(new BorderLayout(6, 0));
		title.setOpaque(false);
		title.add(nameLabel, BorderLayout.CENTER);
		title.add(Theme.label("● fighting", Theme.SUCCESS), BorderLayout.EAST);

		hitsLeftValue.setToolTipText("Hits to finish it at your average hit on it this session, 0s included");
		perKillValue.setToolTipText("Your average time per kill on it this session, including time between kills");
		dpsValue.setToolTipText("Your damage per second while fighting it this session");
		JPanel tiles = UiKit.stackedStrip(new JLabel[]{hitsLeftValue, perKillValue, dpsValue},
			new JLabel[]{UiKit.caption("Hits left"), UiKit.caption("Per kill"), UiKit.caption("DPS")});

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setOpaque(false);
		top.add(title, BorderLayout.NORTH);
		top.add(bar, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);
		add(tiles, BorderLayout.CENTER);
		rows.with(weaknessRow).with(foodRow).with(prayerRow);
		add(rows, BorderLayout.SOUTH);
	}

	/**
	 * @param averagePerHit your average damage per hit on it, 0s included, or 0 if unknown
	 * @param perKillMillis your time per kill, or -1 if unknown
	 * @param dps           your damage per second on it, or -1 if unknown
	 * @param foodKills     kills your food lasts, or -1 if unknown
	 * @param prayerMinutes minutes your prayer lasts, or -1 if unknown
	 */
	void update(TargetInfo target, double averagePerHit, long perKillMillis, double dps, int foodKills, double prayerMinutes)
	{
		foodRow.set(foodKills < 0 ? null : SupplyForecast.killsLabel(foodKills), foodKills <= 2 ? MonsterCard.BAD : Theme.TEXT,
			"From the food you've eaten per kill on this monster");
		prayerRow.set(prayerMinutes < 0 ? null : SupplyForecast.minutesLabel(prayerMinutes),
			prayerMinutes < 2 ? MonsterCard.BAD : Theme.TEXT,
			"Your prayer points and the restore doses you carry, at your measured prayer drain on this monster");
		nameLabel.setText(target.getName());
		bar.set(target.getHealthShare(), target.getHitpoints() >= 0 && target.getMaxHitpoints() > 0
			? Formatting.withCommas(target.getHitpoints()) + " / " + Formatting.withCommas(target.getMaxHitpoints())
			: "Health unknown");
		int hits = target.hitsLeft(averagePerHit);
		hitsLeftValue.setText(hits >= 0 ? "~" + Formatting.withCommas(hits) : "-");
		perKillValue.setText(perKillMillis > 0 ? BossTimes.format(Math.round(perKillMillis / 1000.0)) : "-");
		dpsValue.setText(dps >= 0 ? String.format(java.util.Locale.US, "%.1f", dps) : "-");

		ElementalWeakness.Weakness weakness = target.getWeakness();
		if (weakness == null)
		{
			weaknessRow.set(null);
		}
		else
		{
			// Green once the autocast spell is of that element.
			weaknessRow.setLabel(target.isSpellMatching() ? "Weak to (your spell)" : "Weak to");
			weaknessRow.set(weakness.label(), target.isSpellMatching() ? Theme.SUCCESS : weakness.getElement().getColour(),
				"<html>" + weakness.getElement().getDisplayName() + " spells get " + weakness.getPercent()
					+ "% more accuracy and damage against it.<br>Elemental weaknesses from the OSRS Wiki.</html>");
		}
		rows.refresh();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(Theme.CARD);
		g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), UiKit.CARD_RADIUS, UiKit.CARD_RADIUS));
		g2.setStroke(new java.awt.BasicStroke(1.5f));
		g2.setColor(Theme.SUCCESS);
		g2.draw(new java.awt.geom.RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f,
			UiKit.CARD_RADIUS, UiKit.CARD_RADIUS));
		g2.dispose();
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
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			g2.setColor(Theme.TRACK);
			g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, h, h));
			if (share > 0)
			{
				g2.setColor(share <= 0.25 ? LOW : HEALTHY);
				g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, Math.max(h, (float) (w * share)), h, h, h));
			}
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
