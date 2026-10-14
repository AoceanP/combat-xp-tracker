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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
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
	private static final CombatStyle[] STYLES = {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC};
	private static final Dimension ITEM_SIZE = new Dimension(40, 36);

	private final ItemManager itemManager;
	private final String monsterName;

	private final JPanel header = new JPanel(new BorderLayout(6, 0));
	private final JLabel nameLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel killsLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel valueLabel = Theme.label("", Theme.GOLD);

	private final JPanel body = new JPanel(new BorderLayout(0, 6));
	private final JLabel hitsLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel avgLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel maxLabel = Theme.boldLabel("", Theme.TEXT);
	// Biggest hit per style, always Melee, Ranged, Magic so the columns line up across cards.
	private final JPanel styleRow = new JPanel(new GridLayout(1, STYLES.length, 3, 0));
	private final Map<CombatStyle, JLabel> styleValues = new java.util.EnumMap<>(CombatStyle.class);
	private final JLabel ratesLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel taskLabel = Theme.label("", Theme.SUCCESS);
	private final JLabel dryLabel = Theme.label("", Theme.MUTED);
	private final JLabel prayerLabel = Theme.label("", Theme.MUTED);
	private final JLabel collectionLabel = Theme.label("", Theme.GOLD);
	private final JLabel missingLabel = Theme.label("", Theme.MUTED);
	private final CombatXpTrackerPlugin plugin;
	private final JPanel lootGrid = new JPanel(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
	private final JLabel noLootLabel = Theme.label("No drops recorded yet", Theme.SUBTLE);

	private boolean collapsed;
	// null until the first update, so an empty loot list still shows its message.
	private String lootKey;

	MonsterCard(String monsterName, ItemManager itemManager, CombatXpTrackerPlugin plugin)
	{
		this.monsterName = monsterName;
		this.itemManager = itemManager;
		this.plugin = plugin;

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
		JPanel statsRow = new JPanel(new GridLayout(1, 3, 3, 0));
		statsRow.setOpaque(false);
		statsRow.add(tile(hitsLabel, "Hits"));
		statsRow.add(tile(avgLabel, "Avg hit"));
		statsRow.add(tile(maxLabel, "Max hit"));

		styleRow.setOpaque(false);
		for (CombatStyle style : STYLES)
		{
			JLabel value = Theme.boldLabel("-", Theme.SUBTLE);
			styleValues.put(style, value);
			JPanel tile = tile(value, style.getDisplayName());
			tile.setToolTipText("Biggest " + style.getDisplayName().toLowerCase() + " hit on " + monsterName);
			styleRow.add(tile);
		}

		JPanel stats = new JPanel(new BorderLayout(0, 3));
		stats.setOpaque(false);
		stats.add(statsRow, BorderLayout.NORTH);
		stats.add(styleRow, BorderLayout.CENTER);
		ratesLabel.setToolTipText("Per hour of fighting this monster. Breaks over 5 minutes aren't counted.");
		taskLabel.setToolTipText("Your current slayer task. Time left uses your kills/hr on this monster.");
		// BoxLayout skips hidden lines; a grid would leave blank rows for them.
		JPanel extra = new JPanel();
		extra.setLayout(new BoxLayout(extra, BoxLayout.Y_AXIS));
		extra.setOpaque(false);
		ratesLabel.setAlignmentX(LEFT_ALIGNMENT);
		taskLabel.setAlignmentX(LEFT_ALIGNMENT);
		dryLabel.setAlignmentX(LEFT_ALIGNMENT);
		extra.add(ratesLabel);
		extra.add(taskLabel);
		dryLabel.setToolTipText("Kills since this monster last dropped an item worth at least your \"Rare drop value\" setting.");
		extra.add(dryLabel);
		prayerLabel.setAlignmentX(LEFT_ALIGNMENT);
		prayerLabel.setToolTipText("<html>How long a prayer potion dose lasts while fighting this monster, measured from your own "
			+ "prayer drain.<br>Restores don't count, and kills without prayer on aren't included.</html>");
		extra.add(prayerLabel);
		collectionLabel.setAlignmentX(LEFT_ALIGNMENT);
		extra.add(collectionLabel);
		missingLabel.setAlignmentX(LEFT_ALIGNMENT);
		extra.add(missingLabel);
		stats.add(extra, BorderLayout.SOUTH);

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
		JMenuItem pin = new JMenuItem();
		pin.addActionListener(e -> plugin.setPinned(monsterName, !plugin.isPinned(monsterName)));
		menu.add(pin);
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				pin.setText(plugin.isPinned(monsterName) ? "Unpin" : "Pin to top");
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
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

		// Stays collapsed across restarts if it was collapsed before.
		collapsed = plugin.isCollapsed(monsterName);
		body.setVisible(!collapsed);
	}

	String getMonsterName()
	{
		return monsterName;
	}

	/**
	 * A small stat tile: the value above a caption, centred, like the tiles at the top of the
	 * panel.
	 */
	private static JPanel tile(JLabel value, String caption)
	{
		JPanel tile = new JPanel(new BorderLayout(0, 0));
		tile.setBackground(Theme.HEADER);
		tile.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		value.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		JLabel label = Theme.label(caption, Theme.MUTED);
		label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		tile.add(value, BorderLayout.NORTH);
		tile.add(label, BorderLayout.SOUTH);
		return tile;
	}

	/**
	 * e.g. "Collection log 3/6, 1,214 kc", or "Collection log complete (8/8)".
	 */
	static String collectionText(CollectionLog.Status log)
	{
		String kc = log.getKillCount() >= 0 ? ", " + Formatting.withCommas(log.getKillCount()) + " kc" : "";
		if (log.isComplete())
		{
			return "Collection log complete (" + log.getTotal() + "/" + log.getTotal() + ")";
		}
		return "Collection log " + log.getObtained() + "/" + log.getTotal() + kc;
	}

	/**
	 * e.g. "Missing Vorki, Vorkath's head +1". Hover for the full list.
	 */
	static String missingText(CollectionLog.Status log)
	{
		List<String> missing = log.getMissing();
		int shown = Math.min(2, missing.size());
		StringBuilder sb = new StringBuilder("Missing ").append(String.join(", ", missing.subList(0, shown)));
		if (missing.size() > shown)
		{
			sb.append(" +").append(missing.size() - shown);
		}
		return "<html><div style='width:150px'>" + sb + "</div></html>";
	}

	private static String collectionTooltip(CollectionLog.Status log)
	{
		StringBuilder sb = new StringBuilder("<html>Collection log: ").append(log.getObtained()).append(" of ")
			.append(log.getTotal()).append(" items");
		if (log.getKillCount() >= 0)
		{
			sb.append("<br>Kill count: ").append(Formatting.withCommas(log.getKillCount()));
		}
		if (!log.getMissing().isEmpty())
		{
			sb.append("<br><br>Still missing:");
			for (String item : log.getMissing())
			{
				sb.append("<br>").append(item);
			}
		}
		return sb.append("<br><br>Updated when you open this page in the Collection Log, and when a new item is added.</html>")
			.toString();
	}

	private void toggleCollapsed()
	{
		collapsed = !collapsed;
		body.setVisible(!collapsed);
		plugin.setCollapsed(monsterName, collapsed);
		revalidate();
		repaint();
	}

	/**
	 * @param taskRemaining kills left on the slayer task if this monster is the task,
	 *                      otherwise -1
	 */
	void update(MonsterTracker.Snapshot s, int taskRemaining, long rareValue, boolean pinned)
	{
		// A gold stripe marks pinned cards.
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, pinned ? 3 : 0, 0, 0, Theme.GOLD),
			BorderFactory.createEmptyBorder(6, pinned ? 5 : 8, 6, 8)));
		header.setToolTipText(pinned ? "Pinned to the top. Right-click to unpin." : null);

		if (s.getKills() > 0 && rareValue < Long.MAX_VALUE)
		{
			String value = QuantityFormatter.quantityToStackSize(rareValue) + "+";
			dryLabel.setText(s.hasEverDroppedRare()
				? kills(s.getKillsSinceRare()) + " since a " + value + " drop"
				: "No " + value + " drop in " + kills(s.getKills()));
			dryLabel.setVisible(true);
		}
		else
		{
			dryLabel.setVisible(false);
		}

		CollectionLog.Status log = plugin.getCollectionStatus(monsterName);
		collectionLabel.setVisible(log != null);
		missingLabel.setVisible(log != null && !log.isComplete());
		if (log != null)
		{
			String tooltip = collectionTooltip(log);
			collectionLabel.setText(collectionText(log));
			collectionLabel.setForeground(log.isComplete() ? Theme.SUCCESS : Theme.GOLD);
			collectionLabel.setToolTipText(tooltip);
			missingLabel.setText(missingText(log));
			missingLabel.setToolTipText(tooltip);
		}

		String prayer = plugin.getPrayerDrainText(monsterName);
		prayerLabel.setText(prayer == null ? "" : "Prayer: " + prayer);
		prayerLabel.setVisible(prayer != null);

		if (taskRemaining > 0)
		{
			String text = "Task: " + Formatting.withCommas(taskRemaining) + " left";
			double killsPerHour = s.getKillsPerHour();
			if (killsPerHour > 0)
			{
				text += " (~" + Formatting.duration(taskRemaining / killsPerHour) + ")";
			}
			taskLabel.setText(text);
			taskLabel.setVisible(true);
		}
		else
		{
			taskLabel.setVisible(false);
		}

		killsLabel.setText(s.getKills() > 0 ? "x " + s.getKills() : "");
		valueLabel.setText(s.getLootValue() > 0 ? QuantityFormatter.quantityToStackSize(s.getLootValue()) + " gp" : "");
		valueLabel.setToolTipText(s.getLootValue() > 0 ? Formatting.withCommas(s.getLootValue()) + " gp" : null);

		hitsLabel.setText(Formatting.withCommas(s.getHits()));
		avgLabel.setText(String.format(java.util.Locale.US, "%.1f", s.getAverageHit()));
		if (s.getBiggestHit() >= 0)
		{
			maxLabel.setText(String.valueOf(s.getBiggestHit()));
			CombatStyle style = s.getBiggestHitStyle();
			maxLabel.setForeground(style != null ? style.getColor() : Theme.TEXT);
			maxLabel.setToolTipText(style != null ? "Biggest hit, dealt with " + style.getDisplayName() : "Biggest hit");
		}
		else
		{
			maxLabel.setText("-");
			maxLabel.setForeground(Theme.SUBTLE);
		}

		for (CombatStyle style : STYLES)
		{
			Integer biggest = s.getBiggestByStyle().get(style);
			JLabel value = styleValues.get(style);
			value.setText(biggest == null ? "-" : String.valueOf(biggest));
			value.setForeground(biggest == null ? Theme.SUBTLE : style.getColor());
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
			key.append(line.getItemId()).append(':').append(line.getQuantity()).append(':').append(line.getUnitPrice())
				.append(':').append(line.isRare()).append(',');
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
		if (line.isRare())
		{
			// Rare drops get a gold border and are sorted first.
			slot.setBorder(BorderFactory.createLineBorder(Theme.GOLD, 1));
		}
		slot.setToolTipText("<html>" + line.getName() + " x " + Formatting.withCommas(line.getQuantity()) + value
			+ "<br><font color='#888888'>Right-click to ignore</font></html>");

		JPopupMenu menu = new JPopupMenu();
		JMenuItem ignore = new JMenuItem("Ignore " + line.getName());
		ignore.setToolTipText("Leave it out of loot and loot value everywhere. Undo in the plugin settings.");
		ignore.addActionListener(e -> plugin.ignoreItem(line.getName()));
		menu.add(ignore);
		slot.setComponentPopupMenu(menu);
		return slot;
	}

	private static JLabel emptySlot()
	{
		JLabel slot = new JLabel();
		slot.setOpaque(true);
		slot.setBackground(Theme.SLOT);
		slot.setPreferredSize(ITEM_SIZE);
		slot.setHorizontalAlignment(JLabel.CENTER);
		slot.setVerticalAlignment(JLabel.CENTER);
		return slot;
	}

	private static String kills(int count)
	{
		return Formatting.withCommas(count) + (count == 1 ? " kill" : " kills");
	}

	/**
	 * The header, which drags the card when it's pinned.
	 */
	JComponent getDragHandle()
	{
		return header;
	}
}
