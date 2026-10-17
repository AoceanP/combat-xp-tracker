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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.DynamicGridLayout;

/**
 * The Gielinor Wrapped window: the account's lifetime stats as a card in the panel theme,
 * which can be copied as an image or as text.
 */
final class WrappedDialog
{
	private static final int CARD_WIDTH = 260;

	private WrappedDialog()
	{
	}

	static void show(Component parent, WrappedStats stats, String accountName)
	{
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Gielinor Wrapped");
		JPanel card = card(stats, accountName);

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setBackground(Theme.BACKGROUND);
		buttons.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		JLabel[] image = new JLabel[1];
		image[0] = Theme.flatButton("Copy image", "Copy the card as a picture, to paste into Discord",
			() -> copy(image[0], "Copy image", new ImageSelection(render(card))));
		JLabel[] text = new JLabel[1];
		text[0] = Theme.flatButton("Copy text", "Copy the stats as text",
			() -> copy(text[0], "Copy text", new StringSelection(stats.text(accountName))));
		buttons.add(image[0]);
		buttons.add(text[0]);
		buttons.add(Theme.flatButton("Close", null, dialog::dispose));

		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(Theme.BACKGROUND);
		content.add(card, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);
		dialog.setContentPane(content);
		dialog.pack();
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	/**
	 * The card itself, also what's drawn for "Copy image".
	 */
	static JPanel card(WrappedStats stats, String accountName)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		card.setBackground(Theme.BACKGROUND);
		card.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(Theme.boldLabel("Gielinor Wrapped", Theme.ACCENT), BorderLayout.WEST);
		if (accountName != null)
		{
			head.add(Theme.boldLabel(accountName, Theme.TEXT), BorderLayout.EAST);
		}
		card.add(head);

		if (stats.isEmpty())
		{
			card.add(Theme.label("<html><div style='width:" + (CARD_WIDTH - 20) + "px'>Nothing tracked yet. Fight some "
				+ "monsters or finish a slayer task and your lifetime stats show up here.</div></html>", Theme.MUTED));
		}
		for (WrappedStats.Section section : stats.getSections())
		{
			JPanel box = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
			box.setBackground(Theme.CARD);
			box.setBorder(BorderFactory.createEmptyBorder(6, 8, 7, 8));
			JLabel title = Theme.boldLabel(section.getTitle(), Theme.GOLD);
			title.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
			box.add(title);
			for (WrappedStats.Line line : section.getLines())
			{
				JPanel row = new JPanel(new BorderLayout(6, 0));
				row.setBackground(Theme.HEADER);
				row.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
				JPanel left = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
				left.setOpaque(false);
				left.add(Theme.label(line.getLabel(), Theme.MUTED));
				if (line.getDetail() != null)
				{
					left.add(Theme.label(line.getDetail(), Theme.SUBTLE));
				}
				row.add(left, BorderLayout.CENTER);
				row.add(Theme.boldLabel(line.getValue(), Theme.TEXT), BorderLayout.EAST);
				box.add(row);
			}
			card.add(box);
		}
		card.add(Theme.label("Since you installed Combat & XP Tracker", Theme.SUBTLE));
		card.setPreferredSize(new Dimension(CARD_WIDTH, card.getPreferredSize().height));
		return card;
	}

	static BufferedImage render(JPanel card)
	{
		Dimension size = card.getPreferredSize();
		card.setSize(size);
		layout(card);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		card.paint(g);
		g.dispose();
		return image;
	}

	private static void layout(Container c)
	{
		c.doLayout();
		for (Component child : c.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}

	/**
	 * Puts an image on the clipboard.
	 *
	 * @return whether it worked (the clipboard can be busy)
	 */
	static boolean copyImage(Image image)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
			return true;
		}
		catch (IllegalStateException e)
		{
			return false;
		}
	}

	private static void copy(JLabel button, String label, Transferable contents)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);
			button.setText("Copied!");
		}
		catch (IllegalStateException e)
		{
			button.setText("Try again");
		}
		Timer restore = new Timer(1500, e -> button.setText(label));
		restore.setRepeats(false);
		restore.start();
	}

	/**
	 * An image on the clipboard.
	 */
	private static final class ImageSelection implements Transferable
	{
		private final Image image;

		ImageSelection(Image image)
		{
			this.image = image;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return new DataFlavor[]{DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
			{
				throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
			}
			return image;
		}
	}
}
