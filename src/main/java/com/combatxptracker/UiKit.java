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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * WHAT: The building blocks of the 2.2 sidebar: rounded cards, slim stat strips, section
 * headers, lined-up bars and small coloured tags.
 * WHY: One place for the look, so every tab reads the same and stays compact.
 * PRE: Swing thread only. Colours come from Theme, so every panel theme works.
 * POST: Nothing here touches the game client.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class UiKit
{
	/** Corner radius of cards, and of the small boxes inside them. */
	static final int CARD_RADIUS = 12;
	static final int BOX_RADIUS = 8;

	private UiKit()
	{
	}

	/**
	 * WHAT: A faint line colour that shows on both dark and light themes.
	 * WHY: Dividers inside cards should be barely there.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: a translucent white on dark themes, a translucent black on light ones
	 * THROWS: Nothing.
	 */
	static Color line()
	{
		return Theme.current().isLight() ? new Color(0, 0, 0, 34) : new Color(255, 255, 255, 22);
	}

	/**
	 * WHAT: A panel painted as a rounded rectangle in its background colour, with an optional outline.
	 * WHY: Rounded cards and boxes are the 2.2 look; Swing panels are square by default.
	 * PRE: Children should be non-opaque so the rounded corners show.
	 * POST: Paints only itself; children paint as usual.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class RoundPanel extends JPanel
	{
		private final int radius;
		private Color outline;

		RoundPanel(LayoutManager layout, Color background, int radius)
		{
			super(layout);
			this.radius = radius;
			setBackground(background);
			setOpaque(false);
		}

		/**
		 * WHAT: Sets or clears the outline, e.g. green for the monster you're fighting.
		 * WHY: Marks a card without stripes or icons.
		 * PRE: colour may be null for none.
		 * POST: The panel repaints if the outline changed.
		 * RETURN: this panel, for chaining
		 * THROWS: Nothing.
		 */
		RoundPanel outline(Color colour)
		{
			if (colour == null ? outline != null : !colour.equals(outline))
			{
				outline = colour;
				repaint();
			}
			return this;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
			if (outline != null)
			{
				g2.setStroke(new BasicStroke(1.5f));
				g2.setColor(outline);
				g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, radius, radius));
			}
			g2.dispose();
		}
	}

	/**
	 * WHAT: A label painted on a rounded background, for buttons, tabs and tags.
	 * WHY: Square labels looked heavy; this keeps clicks and hover the same.
	 * PRE: Swing thread.
	 * POST: Paints a rounded background behind its text.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class RoundLabel extends JLabel
	{
		private final int radius;

		RoundLabel(String text, int radius)
		{
			super(text, SwingConstants.CENTER);
			this.radius = radius;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/**
	 * WHAT: A rounded card with the standard padding.
	 * WHY: Every card in every tab starts the same way.
	 * PRE: layout is the card's layout.
	 * POST: Returns a new card.
	 * RETURN: the card
	 * THROWS: Nothing.
	 */
	static RoundPanel card(LayoutManager layout)
	{
		RoundPanel card = new RoundPanel(layout, Theme.CARD, CARD_RADIUS);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		return card;
	}

	/**
	 * WHAT: A small rounded box inside a card, e.g. an area or a task in the log.
	 * WHY: Groups a few lines without another full card.
	 * PRE: layout is the box's layout.
	 * POST: Returns a new box.
	 * RETURN: the box
	 * THROWS: Nothing.
	 */
	static RoundPanel box(LayoutManager layout)
	{
		RoundPanel box = new RoundPanel(layout, Theme.SLOT, BOX_RADIUS);
		box.setBorder(BorderFactory.createEmptyBorder(4, 6, 5, 6));
		return box;
	}

	/**
	 * WHAT: Like strip, but each cell has its value above its caption.
	 * WHY: For the stats at the top of the panel and on the target card, where captions are longer.
	 * PRE: values and captions have the same length.
	 * POST: Returns a new strip holding the given labels.
	 * RETURN: the strip
	 * THROWS: Nothing.
	 */
	static RoundPanel stackedStrip(JLabel[] values, JLabel[] captions)
	{
		RoundPanel strip = new RoundPanel(new GridLayout(1, values.length, 0, 0), Theme.SLOT, BOX_RADIUS);
		strip.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		for (int i = 0; i < values.length; i++)
		{
			JPanel cell = new JPanel(new GridLayout(2, 1, 0, 0));
			cell.setOpaque(false);
			if (i > 0)
			{
				cell.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, line()));
			}
			values[i].setHorizontalAlignment(SwingConstants.CENTER);
			captions[i].setHorizontalAlignment(SwingConstants.CENTER);
			cell.add(values[i]);
			cell.add(captions[i]);
			strip.add(cell);
		}
		return strip;
	}

	/**
	 * WHAT: A bold value label for a strip.
	 * WHY: Strips are built from labels the caller keeps and updates.
	 * PRE: None.
	 * POST: Returns a new label showing "-".
	 * RETURN: the label
	 * THROWS: Nothing.
	 */
	static JLabel value()
	{
		return Theme.boldLabel("-", Theme.TEXT);
	}

	/**
	 * WHAT: A small grey caption for a strip.
	 * WHY: Strips pair each value with a caption.
	 * PRE: text is the caption.
	 * POST: Returns a new label.
	 * RETURN: the label
	 * THROWS: Nothing.
	 */
	static JLabel caption(String text)
	{
		return Theme.label(text, Theme.MUTED);
	}

	/**
	 * WHAT: A section header: a thin line, a bold title, and a short summary on the right.
	 * WHY: Splits a long card into parts you can scan, like "Profit  +3.79M net".
	 * PRE: detail may be null for none; the caller keeps it to update it.
	 * POST: Returns a new header.
	 * RETURN: the header
	 * THROWS: Nothing.
	 */
	static JPanel sectionHeader(String title, JLabel detail)
	{
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.setBorder(BorderFactory.createEmptyBorder(4, 0, 1, 0));
		top.add(Theme.boldLabel(title, Theme.TEXT), BorderLayout.WEST);
		if (detail != null)
		{
			detail.setHorizontalAlignment(SwingConstants.RIGHT);
			top.add(detail, BorderLayout.CENTER);
		}
		JPanel wrap = new JPanel(new BorderLayout(0, 3));
		wrap.setOpaque(false);
		wrap.add(rule(), BorderLayout.NORTH);
		wrap.add(top, BorderLayout.CENTER);
		return wrap;
	}

	/**
	 * WHAT: A 1px divider line.
	 * WHY: Separates parts of a card without boxes.
	 * PRE: None.
	 * POST: Returns a new line.
	 * RETURN: the line
	 * THROWS: Nothing.
	 */
	static JPanel rule()
	{
		JPanel rule = new JPanel();
		rule.setBackground(line());
		rule.setPreferredSize(new Dimension(1, 1));
		return rule;
	}

	/**
	 * WHAT: A thin rounded progress bar.
	 * WHY: Task progress, chances and rankings read faster as bars.
	 * PRE: fill is 0 to 1 (clamped).
	 * POST: Returns a new bar.
	 * RETURN: the bar
	 * THROWS: Nothing.
	 */
	static Bar bar(double fill, Color colour, int height)
	{
		Bar bar = new Bar(height);
		bar.set(fill, colour);
		return bar;
	}

	/**
	 * WHAT: A thin rounded bar that can be updated.
	 * WHY: Used by bar rows and the task progress.
	 * PRE: Swing thread.
	 * POST: Paints a track and a fill.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class Bar extends JComponent
	{
		private final int height;
		private double fill;
		private Color colour = Theme.SUCCESS;

		Bar(int height)
		{
			this.height = height;
			setPreferredSize(new Dimension(0, height));
		}

		void set(double fill, Color colour)
		{
			this.fill = Math.max(0, Math.min(1, fill));
			this.colour = colour;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int y = (getHeight() - height) / 2;
			g2.setColor(Theme.TRACK);
			g2.fill(new RoundRectangle2D.Float(0, y, getWidth(), height, height, height));
			if (fill > 0)
			{
				g2.setColor(colour);
				g2.fill(new RoundRectangle2D.Float(0, y, Math.max(height, (float) (getWidth() * fill)), height, height, height));
			}
			g2.dispose();
		}
	}

	/**
	 * WHAT: A name, a bar and a value on one line, with the bars lined up down a list.
	 * WHY: Next-task chances, who gives a task and your task rankings compare at a glance.
	 * PRE: fill is 0 to 1; strong colours the name and value too.
	 * POST: Returns a new row.
	 * RETURN: the row
	 * THROWS: Nothing.
	 */
	static JPanel barRow(String name, double fill, String value, Color colour, boolean strong)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel n = Theme.label(name, strong ? colour : Theme.TEXT);
		n.setPreferredSize(new Dimension(96, n.getPreferredSize().height));
		row.add(n, BorderLayout.WEST);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		holder.add(bar(fill, colour, 4), BorderLayout.CENTER);
		row.add(holder, BorderLayout.CENTER);
		JLabel v = Theme.label(value, strong ? colour : Theme.TEXT);
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		v.setPreferredSize(new Dimension(44, v.getPreferredSize().height));
		row.add(v, BorderLayout.EAST);
		return row;
	}

	/**
	 * WHAT: A small rounded tag in a colour, e.g. "Worn", "In bank", "Missing" or "task".
	 * WHY: A status reads faster as a coloured tag than as plain text.
	 * PRE: None.
	 * POST: Returns a new tag.
	 * RETURN: the tag
	 * THROWS: Nothing.
	 */
	static RoundLabel pill(String text, Color colour)
	{
		RoundLabel pill = new RoundLabel(text, BOX_RADIUS);
		pill.setFont(Theme.label("", colour).getFont());
		pill.setForeground(Theme.readable(colour));
		pill.setBackground(Theme.highlight(colour, 0.72f));
		pill.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		return pill;
	}

	/**
	 * WHAT: A label row: grey name on the left, value on the right, no box.
	 * WHY: The plain table rows used across the Slayer tab and the top card.
	 * PRE: tooltip may be null.
	 * POST: Returns a new row.
	 * RETURN: the row
	 * THROWS: Nothing.
	 */
	static JPanel row(String label, String value, Color valueColour, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		row.add(Theme.label(label, Theme.MUTED), BorderLayout.WEST);
		JLabel v = Theme.label(value, valueColour);
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(v, BorderLayout.CENTER);
		row.setToolTipText(tooltip);
		return row;
	}

	/**
	 * WHAT: A label and a tag on the right, e.g. "Slayer helmet (i)  [Worn]".
	 * WHY: The Bring list shows each item's state as a coloured tag.
	 * PRE: tooltip may be null.
	 * POST: Returns a new row.
	 * RETURN: the row
	 * THROWS: Nothing.
	 */
	static JPanel tagRow(String label, String tag, Color colour, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		row.add(wrappedTo(label, Theme.MUTED, 120), BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		right.setOpaque(false);
		right.add(pill(tag, colour));
		row.add(right, BorderLayout.EAST);
		row.setToolTipText(tooltip);
		return row;
	}

	/**
	 * WHAT: A pill-shaped tab or toggle that shows when it's selected.
	 * WHY: The three main tabs and the All time / This session switch share one look.
	 * PRE: onClick runs on the Swing thread.
	 * POST: Returns a new tab; call select to style it.
	 * RETURN: the tab
	 * THROWS: Nothing.
	 */
	static RoundLabel tab(String text, String tooltip, Runnable onClick)
	{
		RoundLabel tab = new RoundLabel(text, 10);
		tab.setFont(Theme.label("", Theme.TEXT).getFont());
		tab.setToolTipText(tooltip);
		tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tab.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
		tab.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (tab.contains(e.getPoint()) && e.getButton() == MouseEvent.BUTTON1)
				{
					onClick.run();
				}
			}
		});
		select(tab, false);
		return tab;
	}

	/**
	 * WHAT: Styles a tab as selected (accent text on a soft accent background) or not.
	 * WHY: The selected tab must stand out on every theme.
	 * PRE: tab came from tab().
	 * POST: The tab repaints.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	static void select(JLabel tab, boolean selected)
	{
		tab.setBackground(selected ? Theme.highlight(Theme.TAB_UNDERLINE, 0.78f) : Theme.CARD);
		tab.setForeground(selected ? Theme.TAB_UNDERLINE : Theme.MUTED);
		tab.repaint();
	}

	/**
	 * WHAT: Text that wraps inside a card.
	 * WHY: HTML labels need a fixed width to wrap rather than run off the side.
	 * PRE: text may contain simple HTML.
	 * POST: Returns a new label.
	 * RETURN: the label
	 * THROWS: Nothing.
	 */
	static JLabel wrapped(String text, Color colour)
	{
		return wrappedTo(text, colour, 148);
	}

	/**
	 * WHAT: Text that wraps at a given width.
	 * WHY: Some rows share their line with a value or tag, so they wrap sooner.
	 * PRE: width in pixels.
	 * POST: Returns a new label.
	 * RETURN: the label
	 * THROWS: Nothing.
	 */
	static JLabel wrappedTo(String text, Color colour, int width)
	{
		return Theme.label("<html><div style='width:" + width + "px'>" + text + "</div></html>", colour);
	}

	/**
	 * WHAT: How long ago something happened, short: "now", "5m ago", "2h ago", "yesterday", "3 days ago".
	 * WHY: Folded monster cards say when you last fought them.
	 * PRE: millis is a past time in epoch milliseconds, or 0 or less for unknown.
	 * POST: Nothing changes.
	 * RETURN: the text, or "" when unknown
	 * THROWS: Nothing.
	 */
	static String ago(long millis, long now)
	{
		if (millis <= 0)
		{
			return "";
		}
		long minutes = Math.max(0, now - millis) / 60_000;
		if (minutes < 1)
		{
			return "now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		if (hours < 24)
		{
			return hours + "h ago";
		}
		long days = hours / 24;
		return days == 1 ? "yesterday" : days + " days ago";
	}
}
