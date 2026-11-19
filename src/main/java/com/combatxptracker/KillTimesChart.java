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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;

/**
 * WHAT: A small line of a boss's recent kill times, oldest on the left, with the PB as a gold line.
 * WHY: Shows at a glance whether kills are getting faster, next to the Trend row (2.5).
 * PRE: Swing thread.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
class KillTimesChart extends JComponent
{
	private static final int HEIGHT = 26;
	private static final int PAD = 3;

	private List<Double> times = Collections.emptyList();
	private double best;

	KillTimesChart()
	{
		setOpaque(false);
		setToolTipText("Your recent kill times, oldest on the left. Higher is faster; the gold line at the top is your PB.");
	}

	/**
	 * WHAT: Sets the times to draw.
	 * WHY: Called when the boss card updates.
	 * PRE: times is oldest first; best is the PB in seconds, or 0.
	 * POST: Repaints if anything changed.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void setTimes(List<Double> times, double best)
	{
		if (times.equals(this.times) && best == this.best)
		{
			return;
		}
		this.times = times;
		this.best = best;
		repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(0, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (times.size() < 2)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double lo = best > 0 ? best : Double.MAX_VALUE;
		double hi = 0;
		for (double t : times)
		{
			lo = Math.min(lo, t);
			hi = Math.max(hi, t);
		}
		double span = Math.max(1, hi - lo);
		int w = getWidth() - 2 * PAD;
		int h = getHeight() - 2 * PAD;

		if (best > 0)
		{
			int y = PAD + (int) Math.round((best - lo) / span * h);
			g2.setColor(Theme.readable(Theme.GOLD));
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f));
			g2.drawLine(PAD, y, PAD + w, y);
		}

		Path2D.Double line = new Path2D.Double();
		double x = PAD;
		double y = 0;
		for (int i = 0; i < times.size(); i++)
		{
			x = PAD + w * i / (double) (times.size() - 1);
			y = PAD + (times.get(i) - lo) / span * h;
			if (i == 0)
			{
				line.moveTo(x, y);
			}
			else
			{
				line.lineTo(x, y);
			}
		}
		g2.setColor(Theme.ACCENT);
		g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.draw(line);
		// The latest kill as a dot.
		g2.fillOval((int) Math.round(x) - 2, (int) Math.round(y) - 2, 5, 5);
		g2.dispose();
	}
}
