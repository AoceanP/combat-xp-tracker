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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * One monster in the Monsters tab, laid out like the core Loot Tracker:
 *
 * <pre>
 * Abyssal demon  x12                   184K gp
 * Hits 245    Avg 18.3    Max 45 Melee
 * Melee 45   Magic 38
 * [item][item][item][item][item]
 * </pre>
 *
 * Click the header to collapse or expand it. Right-click for options.
 */
class MonsterCard extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;
	private static final Dimension ITEM_SIZE = new Dimension(40, 36);

	private final ItemManager itemManager;
	private final String monsterName;

	private final JPanel header = new JPanel(new BorderLayout(6, 0));
	private final JLabel nameLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel killsLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel valueLabel = Theme.label("", Theme.GOLD);

	private final JPanel body = new JPanel(new BorderLayout(0, 6));
	private final JLabel hitsLabel = Theme.label("", Theme.MUTED);
	private final JLabel avgLabel = Theme.label("", Theme.MUTED);
	private final JLabel maxLabel = Theme.label("", Theme.TEXT);
	private final JPanel styleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
	private final JLabel ratesLabel = Theme.label("", Theme.SUBTLE);
	private final JPanel lootGrid = new JPanel(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
	private final JLabel noLootLabel = Theme.label("No drops recorded yet", Theme.SUBTLE);

	private boolean collapsed;
	// null until the first update, so an empty loot list still shows its message.
	private String lootKey;

	MonsterCard(String monsterName, ItemManager itemManager, CombatXpTrackerPlugin plugin)
	{
		this.monsterName = monsterName;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		// Header
		JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		title.setOpaque(false);
		nameLabel.setText(monsterName);
		title.add(nameLabel);
		killsLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
		title.add(killsLabel);

		header.setBackground(Theme.HEADER);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.add(title, BorderLayout.CENTER);
		header.add(valueLabel, BorderLayout.EAST);

		// Body
		JPanel statsRow = new JPanel(new GridLayout(1, 3, 6, 0));
		statsRow.setOpaque(false);
		statsRow.add(hitsLabel);
		statsRow.add(avgLabel);
		statsRow.add(maxLabel);

		styleRow.setOpaque(false);

		JPanel stats = new JPanel(new BorderLayout(0, 4));
		stats.setOpaque(false);
		stats.add(statsRow, BorderLayout.NORTH);
		stats.add(styleRow, BorderLayout.CENTER);
		ratesLabel.setToolTipText("Per hour of fighting this monster. Breaks over 5 minutes aren't counted.");
		stats.add(ratesLabel, BorderLayout.SOUTH);

		lootGrid.setOpaque(false);

		body.setBackground(Theme.CARD);
		body.setBorder(BorderFactory.createEmptyBorder(7, 8, 8, 8));
		body.add(stats, BorderLayout.NORTH);
		body.add(lootGrid, BorderLayout.CENTER);

		JPanel stack = new JPanel(new BorderLayout());
		stack.add(header, BorderLayout.NORTH);
		stack.add(body, BorderLayout.CENTER);
		add(stack, BorderLayout.NORTH);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem toggle = new JMenuItem("Collapse / expand");
		toggle.addActionListener(e -> toggleCollapsed());
		menu.add(toggle);
		JMenuItem hide = new JMenuItem("Hide " + monsterName);
		hide.setToolTipText("Keeps tracking it, but leaves it out of this tab. Unhide from the bottom of the tab or the plugin settings.");
		hide.addActionListener(e -> plugin.hideMonster(monsterName));
		menu.add(hide);
		JMenuItem remove = new JMenuItem("Remove " + monsterName);
		remove.addActionListener(e -> plugin.removeMonster(monsterName));
		menu.add(remove);
		header.setComponentPopupMenu(menu);
		body.setComponentPopupMenu(menu);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					toggleCollapsed();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				header.setBackground(Theme.CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setBackground(Theme.HEADER);
			}
		});
	}

	String getMonsterName()
	{
		return monsterName;
	}

	private void toggleCollapsed()
	{
		collapsed = !collapsed;
		body.setVisible(!collapsed);
		revalidate();
		repaint();
	}

	void update(MonsterTracker.Snapshot s)
	{
		killsLabel.setText(s.getKills() > 0 ? "x " + s.getKills() : "");
		valueLabel.setText(s.getLootValue() > 0 ? QuantityFormatter.quantityToStackSize(s.getLootValue()) + " gp" : "");
		valueLabel.setToolTipText(s.getLootValue() > 0 ? Formatting.withCommas(s.getLootValue()) + " gp" : null);

		hitsLabel.setText("Hits " + Formatting.withCommas(s.getHits()));
		avgLabel.setText(String.format(java.util.Locale.US, "Avg %.1f", s.getAverageHit()));
		if (s.getBiggestHit() >= 0)
		{
			maxLabel.setText("Max " + s.getBiggestHit());
			CombatStyle style = s.getBiggestHitStyle();
			maxLabel.setForeground(style != null ? style.getColor() : Theme.TEXT);
			maxLabel.setToolTipText(style != null ? "Biggest hit, dealt with " + style.getDisplayName() : "Biggest hit");
		}
		else
		{
			maxLabel.setText("Max -");
			maxLabel.setForeground(Theme.SUBTLE);
		}

		styleRow.removeAll();
		for (Map.Entry<CombatStyle, Integer> e : s.getBiggestByStyle().entrySet())
		{
			JLabel chip = Theme.label(e.getKey().getDisplayName() + " " + e.getValue(), e.getKey().getColor());
			chip.setToolTipText("Biggest " + e.getKey().getDisplayName().toLowerCase() + " hit on " + monsterName);
			styleRow.add(chip);
		}
		styleRow.setVisible(!s.getBiggestByStyle().isEmpty());

		double killsPerHour = s.getKillsPerHour();
		double gpPerHour = s.getGpPerHour();
		if (killsPerHour < 0)
		{
			ratesLabel.setVisible(false);
		}
		else
		{
			String rates = String.format(java.util.Locale.US, "%.1f kills/hr", killsPerHour);
			if (gpPerHour > 0)
			{
				rates += "   " + QuantityFormatter.quantityToStackSize(Math.round(gpPerHour)) + " gp/hr";
			}
			ratesLabel.setText(rates);
			ratesLabel.setVisible(true);
		}

		updateLoot(s.getLoot());
	}

	/**
	 * Rebuilds the item grid only when the loot actually changed, so icons don't flicker
	 * on every hit.
	 */
	private void updateLoot(List<MonsterTracker.LootLine> loot)
	{
		StringBuilder key = new StringBuilder();
		for (MonsterTracker.LootLine line : loot)
		{
			key.append(line.getItemId()).append(':').append(line.getQuantity()).append(':').append(line.getUnitPrice()).append(',');
		}
		if (key.toString().equals(lootKey))
		{
			return;
		}
		lootKey = key.toString();

		lootGrid.removeAll();
		if (loot.isEmpty())
		{
			lootGrid.setLayout(new BorderLayout());
			lootGrid.add(noLootLabel, BorderLayout.CENTER);
		}
		else
		{
			lootGrid.setLayout(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
			for (MonsterTracker.LootLine line : loot)
			{
				lootGrid.add(itemSlot(line));
			}
			// Pad the last row so icons stay the same size.
			int remainder = loot.size() % ITEMS_PER_ROW;
			for (int i = 0; remainder != 0 && i < ITEMS_PER_ROW - remainder; i++)
			{
				lootGrid.add(emptySlot());
			}
		}
		lootGrid.revalidate();
		lootGrid.repaint();
	}

	private JLabel itemSlot(MonsterTracker.LootLine line)
	{
		JLabel slot = emptySlot();
		int quantity = (int) Math.min(Integer.MAX_VALUE, line.getQuantity());
		AsyncBufferedImage image = itemManager.getImage(line.getItemId(), quantity, quantity > 1);
		image.addTo(slot);

		String value = line.getTotalValue() > 0
			? "<br><font color='#FFC640'>" + Formatting.withCommas(line.getTotalValue()) + " gp</font>"
			: "";
		slot.setToolTipText("<html>" + line.getName() + " x " + Formatting.withCommas(line.getQuantity()) + value + "</html>");
		return slot;
	}

	private static JLabel emptySlot()
	{
		JLabel slot = new JLabel();
		slot.setOpaque(true);
		slot.setBackground(new Color(36, 36, 36));
		slot.setPreferredSize(ITEM_SIZE);
		slot.setHorizontalAlignment(JLabel.CENTER);
		slot.setVerticalAlignment(JLabel.CENTER);
		return slot;
	}
}
