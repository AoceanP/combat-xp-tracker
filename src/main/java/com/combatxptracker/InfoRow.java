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
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * One line of a card: a grey label on the left and its value on the right, with a thin line
 * under it, so a card reads like a short table instead of a list of sentences.
 */
final class InfoRow extends JPanel
{
	private final JLabel label = new JLabel();
	private final JLabel value = new JLabel();

	InfoRow(String name)
	{
		super(new BorderLayout(8, 0));
		setOpaque(false);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(Theme.MUTED);
		label.setText(name);
		value.setFont(FontManager.getRunescapeFont());
		value.setHorizontalAlignment(JLabel.RIGHT);
		add(label, BorderLayout.WEST);
		add(value, BorderLayout.CENTER);
		setAlignmentX(LEFT_ALIGNMENT);
		setVisible(false);
		divider(true);
	}

	/**
	 * Shows the row with a value, or hides it when the value is null.
	 */
	void set(String text, Color colour, String tooltip)
	{
		setVisible(text != null);
		if (text != null)
		{
			value.setText(text);
			value.setForeground(colour);
			setToolTipText(tooltip);
		}
	}

	void set(String text)
	{
		set(text, Theme.TEXT, null);
	}

	void setLabel(String name)
	{
		label.setText(name);
	}

	/**
	 * WHAT: Spacing under the row. 2.2 drops the line between rows: sections already
	 * have a rule above them, and rows read cleaner without one each.
	 * WHY: Kept as a method so Section can still tighten the last row.
	 * PRE: None.
	 * POST: The row's border is set.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void divider(boolean show)
	{
		setBorder(BorderFactory.createEmptyBorder(1, 0, show ? 1 : 0, 0));
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	/**
	 * A group of rows under a small heading. It hides when none of its rows are showing, and
	 * the last showing row drops its line.
	 */
	static final class Section extends JPanel
	{
		private final JLabel title = Theme.boldLabel("", Theme.TEXT);
		private final JLabel detail = Theme.label("", Theme.MUTED);
		private final List<JComponent> parts = new ArrayList<>();

		/**
		 * @param heading the heading, or null for none
		 */
		Section(String heading)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
			if (heading != null)
			{
				// 2.2: a thin rule, then the title in bold with its summary on the right.
				JPanel top = new JPanel(new BorderLayout(6, 0));
				top.setOpaque(false);
				top.setAlignmentX(LEFT_ALIGNMENT);
				top.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(1, 0, 0, 0, UiKit.line()),
					BorderFactory.createEmptyBorder(5, 0, 2, 0)));
				title.setText(heading);
				detail.setHorizontalAlignment(JLabel.RIGHT);
				top.add(title, BorderLayout.WEST);
				top.add(detail, BorderLayout.CENTER);
				top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
				add(top);
			}
		}

		Section with(JComponent part)
		{
			part.setAlignmentX(LEFT_ALIGNMENT);
			parts.add(part);
			add(part);
			return this;
		}

		/**
		 * Text on the right of the heading, e.g. "3/6, 1,216 kc".
		 */
		void setDetail(String text, Color colour, String tooltip)
		{
			detail.setText(text == null ? "" : text);
			detail.setForeground(colour);
			detail.setToolTipText(tooltip);
		}

		/**
		 * Call after changing rows: hides an empty section and tidies the lines.
		 */
		void refresh()
		{
			InfoRow last = null;
			boolean any = false;
			for (Component c : parts)
			{
				if (c.isVisible())
				{
					any = true;
					if (c instanceof InfoRow)
					{
						((InfoRow) c).divider(true);
						last = (InfoRow) c;
					}
				}
			}
			if (last != null)
			{
				last.divider(false);
			}
			setVisible(any);
		}
	}
}
