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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.QuantityFormatter;

/**
 * Picks which monsters to bring back from the Loot Tracker: a searchable list with each
 * monster's kills and loot, and how many kills are tracked for it now.
 */
final class RestoreChooser extends JDialog
{
	private final Map<String, JPanel> rows = new LinkedHashMap<>();
	private final JPanel list = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final Set<String> chosen = new LinkedHashSet<>();
	private final IconTextField search = new IconTextField();
	private final JLabel restoreButton;
	private final Consumer<Set<String>> onRestore;

	RestoreChooser(Component parent, List<MonsterTracker.Saved> choices, Map<String, Integer> trackedKills,
		Consumer<Set<String>> onRestore)
	{
		super(SwingUtilities.getWindowAncestor(parent), "Restore from Loot Tracker", ModalityType.APPLICATION_MODAL);
		this.onRestore = onRestore;

		JLabel title = new JLabel("Choose monsters to restore");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Theme.TEXT);
		JLabel note = Theme.label("Combined with what's tracked now, never doubled.", Theme.MUTED);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(0, 28));
		search.setBackground(Theme.CARD);
		search.setHoverBackgroundColor(Theme.CARD_HOVER);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				filter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				filter();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				filter();
			}
		});
		search.addClearListener(this::filter);

		JPanel selectRow = new JPanel(new GridLayout(1, 2, 4, 0));
		selectRow.setOpaque(false);
		selectRow.add(Theme.flatButton("Select all", "Select every monster shown", () -> selectShown(true)));
		selectRow.add(Theme.flatButton("Select none", "Clear the monsters shown", () -> selectShown(false)));

		JPanel top = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		top.setOpaque(false);
		top.add(title);
		top.add(note);
		top.add(search);
		top.add(selectRow);

		list.setBackground(Theme.BACKGROUND);
		choices.sort((a, b) -> Integer.compare(b.kills, a.kills));
		for (MonsterTracker.Saved s : choices)
		{
			JPanel row = row(s, trackedKills.getOrDefault(s.name, 0));
			rows.put(s.name, row);
			list.add(row);
		}
		JPanel listHolder = new JPanel(new BorderLayout());
		listHolder.setBackground(Theme.BACKGROUND);
		listHolder.add(list, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(listHolder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		restoreButton = Theme.flatButton("Restore", "Restore the selected monsters", this::restore);
		JPanel bottom = new JPanel(new GridLayout(1, 2, 4, 0));
		bottom.setOpaque(false);
		bottom.add(restoreButton);
		bottom.add(Theme.flatButton("Cancel", null, this::dispose));

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBackground(Theme.BACKGROUND);
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(top, BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		setContentPane(content);
		updateButton();
		setSize(300, 480);
		setLocationRelativeTo(parent);
	}

	/**
	 * Opens the chooser. {@code onRestore} gets the chosen monsters when Restore is clicked.
	 */
	static void open(Component parent, List<MonsterTracker.Saved> choices, Map<String, Integer> trackedKills,
		Consumer<Set<String>> onRestore)
	{
		new RestoreChooser(parent, new ArrayList<>(choices), trackedKills, onRestore).setVisible(true);
	}

	/**
	 * One monster: a tick box, its name and kills, and its loot underneath.
	 */
	private JPanel row(MonsterTracker.Saved s, int trackedNow)
	{
		JCheckBox box = new JCheckBox();
		box.setOpaque(false);
		box.setFocusable(false);
		box.addActionListener(e -> choose(s.name, box.isSelected()));

		JLabel name = new JLabel(s.name);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Theme.TEXT);
		JLabel kills = new JLabel(Formatting.withCommas(s.kills) + (s.kills == 1 ? " kill" : " kills"));
		kills.setFont(FontManager.getRunescapeSmallFont());
		kills.setForeground(Theme.TEXT);

		long value = 0;
		for (MonsterTracker.SavedLoot l : s.loot)
		{
			value += l.quantity * l.unitPrice;
		}
		String detail = QuantityFormatter.quantityToStackSize(value) + " gp loot"
			+ (trackedNow > 0 ? "  ·  " + Formatting.withCommas(trackedNow) + " tracked now" : "");
		JLabel sub = Theme.label(detail, value > 0 ? Theme.GOLD : Theme.MUTED);

		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		line.add(name, BorderLayout.CENTER);
		line.add(kills, BorderLayout.EAST);
		JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
		text.setOpaque(false);
		text.add(line);
		text.add(sub);

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 8));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.add(box, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.putClientProperty("box", box);
		// The whole row toggles, not just the tick box.
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(Theme.CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(Theme.CARD);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				box.setSelected(!box.isSelected());
				choose(s.name, box.isSelected());
			}
		});
		return row;
	}

	private void choose(String monster, boolean selected)
	{
		if (selected)
		{
			chosen.add(monster);
		}
		else
		{
			chosen.remove(monster);
		}
		updateButton();
	}

	private void selectShown(boolean selected)
	{
		for (Map.Entry<String, JPanel> e : rows.entrySet())
		{
			if (e.getValue().getParent() == list)
			{
				((JCheckBox) e.getValue().getClientProperty("box")).setSelected(selected);
				choose(e.getKey(), selected);
			}
		}
	}

	private void filter()
	{
		String text = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		// Rebuilt rather than hidden, so matches close up at the top of the list.
		list.removeAll();
		for (Map.Entry<String, JPanel> e : rows.entrySet())
		{
			if (text.isEmpty() || e.getKey().toLowerCase(Locale.ROOT).contains(text))
			{
				list.add(e.getValue());
			}
		}
		list.revalidate();
		list.repaint();
	}

	private void updateButton()
	{
		int n = chosen.size();
		restoreButton.setText(n == 0 ? "Restore" : n == 1 ? "Restore 1 monster" : "Restore " + n + " monsters");
		restoreButton.setForeground(n == 0 ? Theme.SUBTLE : Theme.TEXT);
	}

	private void restore()
	{
		if (chosen.isEmpty())
		{
			return;
		}
		dispose();
		onRestore.accept(new LinkedHashSet<>(chosen));
	}
}
