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
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;

/**
 * The big drop reveal at the top of the Monsters tab: a strip of the boss's items spins past
 * and slows to a stop on the drop, like opening a case, then says what it was. Click to
 * dismiss; it also goes by itself after a while.
 */
final class DropRevealCard extends JPanel
{
	private static final int SLOT = 36;
	private static final int STRIP_LENGTH = 30;
	// The drop's place in the strip, a few from the end so it slows down onto it.
	static final int LANDING = 25;
	static final int SPIN_MILLIS = 2_200;
	private static final int SHOW_MILLIS = 15_000;
	private static final int FRAME_MILLIS = 16;

	private final ItemManager itemManager;
	private final Strip strip = new Strip();
	private final JLabel headline = Theme.boldLabel("", Theme.GOLD);
	private final JLabel itemLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel detail = Theme.label("", Theme.MUTED);
	private final Timer spin;
	private final Timer hide;
	private final Map<Integer, Image> icons = new HashMap<>();
	private List<Integer> ids = new ArrayList<>();
	private DropReveal reveal;
	private long started;
	private int shownSequence = -1;

	/**
	 * @param itemManager for item icons, or null to draw without them
	 */
	DropRevealCard(ItemManager itemManager)
	{
		super(new BorderLayout(0, 4));
		this.itemManager = itemManager;
		setBackground(Theme.CARD);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, Theme.GOLD),
			BorderFactory.createEmptyBorder(6, 7, 7, 7)));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Click to dismiss");

		JPanel text = new JPanel(new BorderLayout(0, 1));
		text.setOpaque(false);
		text.add(itemLabel, BorderLayout.NORTH);
		text.add(detail, BorderLayout.SOUTH);
		add(headline, BorderLayout.NORTH);
		add(strip, BorderLayout.CENTER);
		add(text, BorderLayout.SOUTH);

		spin = new Timer(FRAME_MILLIS, e -> tick());
		hide = new Timer(SHOW_MILLIS, e -> setVisible(false));
		hide.setRepeats(false);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				spin.stop();
				hide.stop();
				setVisible(false);
			}
		});
		setVisible(false);
	}

	/**
	 * Plays a reveal, once per drop.
	 */
	void play(DropReveal drop)
	{
		if (drop == null || drop.getSequence() == shownSequence)
		{
			return;
		}
		shownSequence = drop.getSequence();
		reveal = drop;
		ids = stripFor(drop, new Random(drop.getSequence()));
		headline.setText("Opening...");
		headline.setForeground(Theme.MUTED);
		itemLabel.setText(" ");
		detail.setText(" ");
		started = System.currentTimeMillis();
		hide.stop();
		setVisible(true);
		spin.restart();
	}

	/**
	 * The strip of item ids: the boss's other items in a random order, with the drop at the
	 * landing place.
	 */
	static List<Integer> stripFor(DropReveal drop, Random random)
	{
		List<Integer> pool = new ArrayList<>(drop.getStrip());
		pool.remove(Integer.valueOf(drop.getItemId()));
		List<Integer> out = new ArrayList<>(STRIP_LENGTH);
		for (int i = 0; i < STRIP_LENGTH; i++)
		{
			out.add(i == LANDING || pool.isEmpty() ? drop.getItemId() : pool.get(random.nextInt(pool.size())));
		}
		return out;
	}

	/**
	 * Eases out: fast at first, slowing to a stop. 0 to 1 over the spin.
	 */
	static double progress(long elapsedMillis)
	{
		double t = Math.min(1, Math.max(0, elapsedMillis / (double) SPIN_MILLIS));
		return 1 - Math.pow(1 - t, 3);
	}

	private void tick()
	{
		long elapsed = System.currentTimeMillis() - started;
		if (elapsed >= SPIN_MILLIS)
		{
			spin.stop();
			land();
		}
		strip.repaint();
	}

	/**
	 * Stops on the drop and says what it was.
	 */
	void land()
	{
		started = System.currentTimeMillis() - SPIN_MILLIS;
		headline.setText(reveal.headline());
		headline.setForeground(Theme.GOLD);
		itemLabel.setText(reveal.getItem());
		detail.setText(reveal.getDetail() == null ? " " : reveal.getDetail());
		strip.repaint();
		hide.restart();
	}

	private Image icon(int id)
	{
		Image cached = icons.get(id);
		if (cached != null || itemManager == null || id <= 0)
		{
			return cached;
		}
		return icons.computeIfAbsent(id, k ->
		{
			net.runelite.client.util.AsyncBufferedImage image = itemManager.getImage(k);
			image.onLoaded(strip::repaint);
			return image;
		});
	}

	/**
	 * The spinning strip with a gold marker in the middle.
	 */
	private final class Strip extends JComponent
	{
		Strip()
		{
			setPreferredSize(new Dimension(0, SLOT + 8));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if (reveal == null)
			{
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			int centre = w / 2;
			g2.setColor(Theme.TRACK);
			g2.fillRect(0, 0, w, h);

			// Where the strip is: slot LANDING ends up centred under the marker.
			double travelled = progress(System.currentTimeMillis() - started) * LANDING * SLOT;
			int y = (h - SLOT) / 2;
			for (int i = 0; i < ids.size(); i++)
			{
				int x = (int) Math.round(centre - SLOT / 2.0 + i * SLOT - travelled);
				if (x + SLOT < 0 || x > w)
				{
					continue;
				}
				boolean landed = i == LANDING && !spin.isRunning();
				g2.setColor(landed ? Theme.highlight(Theme.GOLD, 0.7f) : Theme.SLOT);
				g2.fillRect(x + 1, y, SLOT - 2, SLOT);
				Image image = icon(ids.get(i));
				if (image != null)
				{
					g2.drawImage(image, x + (SLOT - image.getWidth(null)) / 2, y + (SLOT - image.getHeight(null)) / 2, null);
				}
				else
				{
					g2.setColor(Theme.MUTED);
					g2.drawString(i == LANDING ? "!" : "?", x + SLOT / 2 - 2, y + SLOT / 2 + 4);
				}
			}
			g2.setColor(Theme.GOLD);
			g2.setStroke(new BasicStroke(2));
			g2.drawLine(centre, 0, centre, 4);
			g2.drawLine(centre, h - 4, centre, h);
			g2.drawRect(centre - SLOT / 2, y - 1, SLOT, SLOT + 1);
			g2.dispose();
		}
	}
}
