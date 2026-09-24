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
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.Timer;
import net.runelite.client.ui.FontManager;

/**
 * A rounded goal bar that fills with the goal colour (light blue by default) as XP comes
 * in, easing smoothly to each new value instead of jumping.
 *
 * The animation timer only runs while the fill is moving and stops when the bar is
 * removed from the panel, so an idle panel costs nothing.
 */
class GoalProgressBar extends JComponent
{
	private static final int HEIGHT = 16;
	private static final int FRAME_MILLIS = 16;
	// Fraction of the remaining distance covered per frame: quick start, gentle finish.
	private static final double EASING = 0.18;

	private final Timer animation = new Timer(FRAME_MILLIS, e -> step());

	private double target;
	private double shown;
	private Color fill = new Color(79, 195, 247);
	private String text = "";
	private boolean complete;

	GoalProgressBar()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(0, HEIGHT));
		setMinimumSize(new Dimension(0, HEIGHT));
		setFont(FontManager.getRunescapeSmallFont());
	}

	/**
	 * @param value   0.0-1.0
	 * @param animate false to snap straight to the value (e.g. when the card is first shown)
	 */
	void setValue(double value, boolean animate)
	{
		double clamped = Math.max(0.0, Math.min(1.0, value));
		if (clamped == target && (animate || clamped == shown))
		{
			return;
		}
		target = clamped;
		if (!animate || !isShowing())
		{
			shown = target;
			animation.stop();
			repaint();
		}
		else if (!animation.isRunning())
		{
			animation.start();
		}
	}

	void setFillColor(Color color)
	{
		if (!color.equals(fill))
		{
			fill = color;
			repaint();
		}
	}

	void setText(String text)
	{
		if (!text.equals(this.text))
		{
			this.text = text;
			repaint();
		}
	}

	void setComplete(boolean complete)
	{
		if (complete != this.complete)
		{
			this.complete = complete;
			repaint();
		}
	}

	private void step()
	{
		double diff = target - shown;
		if (Math.abs(diff) < 0.0015)
		{
			shown = target;
			animation.stop();
		}
		else
		{
			shown += diff * EASING;
		}
		repaint();
	}

	@Override
	public void removeNotify()
	{
		animation.stop();
		shown = target;
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth();
			int h = getHeight();
			float arc = h;

			// Track
			Shape track = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc);
			g2.setColor(Theme.TRACK);
			g2.fill(track);

			// Quarter markers, faint, so progress is readable at a glance.
			g2.setColor(new Color(255, 255, 255, 14));
			for (int i = 1; i < 4; i++)
			{
				int x = w * i / 4;
				g2.drawLine(x, 3, x, h - 4);
			}

			// Fill, clipped to the track's rounded shape.
			int fillWidth = (int) Math.round((w - 1) * shown);
			if (fillWidth > 0)
			{
				Color base = complete ? Theme.SUCCESS : fill;
				Graphics2D fg = (Graphics2D) g2.create();
				fg.clip(track);
				fg.setPaint(new GradientPaint(0, 0, Theme.lighten(base, 0.28f), 0, h, Theme.darken(base, 0.12f)));
				fg.fillRect(0, 0, fillWidth, h);
				// Soft gloss on the top half.
				fg.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 60), 0, h / 2f, new Color(255, 255, 255, 0)));
				fg.fillRect(0, 0, fillWidth, h / 2);
				// Bright leading edge while it's still filling.
				if (!complete && shown < 1.0)
				{
					fg.setColor(Theme.lighten(base, 0.6f));
					fg.fillRect(Math.max(0, fillWidth - 2), 0, 2, h);
				}
				fg.dispose();
			}

			// Outline
			g2.setColor(Theme.BORDER);
			g2.draw(track);

			// Label with a drop shadow so it reads on both the fill and the empty track.
			if (!text.isEmpty())
			{
				g2.setFont(getFont());
				FontMetrics fm = g2.getFontMetrics();
				int tx = (w - fm.stringWidth(text)) / 2;
				int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
				g2.setColor(new Color(0, 0, 0, 200));
				g2.drawString(text, tx + 1, ty + 1);
				g2.setColor(Color.WHITE);
				g2.drawString(text, tx, ty);
			}
		}
		finally
		{
			g2.dispose();
		}
	}
}
