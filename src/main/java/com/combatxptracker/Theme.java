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
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Colours and small shared widgets for the sidebar, built on RuneLite's own
 * {@link ColorScheme} so the panel sits naturally next to the core plugins.
 */
final class Theme
{
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	static final Color HEADER = new Color(24, 24, 24);
	static final Color TRACK = new Color(17, 17, 17);
	static final Color BORDER = new Color(52, 52, 52);

	static final Color TEXT = Color.WHITE;
	static final Color MUTED = new Color(168, 168, 168);
	static final Color SUBTLE = new Color(118, 118, 118);

	static final Color GOLD = new Color(255, 198, 64);
	static final Color SUCCESS = new Color(96, 220, 140);

	private Theme()
	{
	}

	static JLabel label(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	static JLabel boldLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(color);
		return label;
	}

	/**
	 * Mixes a colour toward white; amount 0 = unchanged, 1 = white.
	 */
	static Color lighten(Color c, float amount)
	{
		int r = Math.round(c.getRed() + (255 - c.getRed()) * amount);
		int g = Math.round(c.getGreen() + (255 - c.getGreen()) * amount);
		int b = Math.round(c.getBlue() + (255 - c.getBlue()) * amount);
		return new Color(r, g, b, c.getAlpha());
	}

	/**
	 * Mixes a colour toward black; amount 0 = unchanged, 1 = black.
	 */
	static Color darken(Color c, float amount)
	{
		return new Color(
			Math.round(c.getRed() * (1 - amount)),
			Math.round(c.getGreen() * (1 - amount)),
			Math.round(c.getBlue() * (1 - amount)),
			c.getAlpha());
	}

	/**
	 * A flat, hover-highlighted button. Swing's JButton picks up the client's look and
	 * feel inconsistently, so this is a clickable label instead.
	 */
	static JLabel flatButton(String text, String tooltip, Runnable onClick)
	{
		JLabel button = new JLabel(text, SwingConstants.CENTER);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(TEXT);
		button.setOpaque(true);
		button.setBackground(CARD);
		button.setToolTipText(tooltip);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(CARD);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (button.contains(e.getPoint()) && e.getButton() == MouseEvent.BUTTON1)
				{
					onClick.run();
				}
			}
		});
		return button;
	}
}
