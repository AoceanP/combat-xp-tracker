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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * One monster in the Monsters tab, as a rounded card (2.2):
 *
 * <pre>
 * Vorkath  [task]                         6.10M
 * 212 kills  +12 today                ● fighting
 * Rates       Kills/hr, GP/hr, Prayer, Task
 * Luck        Since unique, Rare drop, collection log, Driest
 * Drops       every item
 * Hits        hits | avg | max, per style, acc | dps | uptime
 * Boss        pb | last | avg, Combat Achievements
 * Profit      Supplies, Net profit, Net GP/hr, Deaths
 * </pre>
 *
 * Click the header to fold or open it. Folded, it's one line: name, kills and when you last
 * fought it, loot and GP/hr. Right-click for options.
 */
class MonsterCard extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;
	private static final CombatStyle[] STYLES = {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC};
	private static final Dimension ITEM_SIZE = new Dimension(40, 34);
	private static final java.awt.Color TASK_TAG = new java.awt.Color(190, 160, 255);

	private final ItemManager itemManager;
	private final String monsterName;

	// The rounded card everything sits on; outlined green while fighting, gold when pinned.
	private final UiKit.RoundPanel shell = new UiKit.RoundPanel(new BorderLayout(), Theme.CARD, UiKit.CARD_RADIUS);
	private final JPanel header = new JPanel(new BorderLayout(6, 0));
	private final JLabel nameLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel taskTag = UiKit.pill("task", TASK_TAG);
	private final JLabel killsLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel todayLabel = Theme.label("", Theme.SUCCESS);
	private final JLabel valueLabel = Theme.boldLabel("", Theme.GOLD);
	private final JLabel statusLabel = Theme.label("", Theme.MUTED);
	private boolean fighting;
	private boolean pinned;
	private String sinceText = "";

	private static final java.awt.Color DEATHS = new java.awt.Color(235, 110, 100);
	// Bad news: dry streaks, deaths, running low.
	static final java.awt.Color BAD = DEATHS;
	private final JPanel body = new JPanel(new BorderLayout(0, 6));
	private final JLabel hitsLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel avgLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel maxLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel maxCaption = UiKit.caption("max");
	// Biggest hit per style, always Melee, Ranged, Magic so the columns line up across cards.
	// The three strips below sit in holders, which add the gap between strips and are what
	// gets shown or hidden.
	private final JPanel styleRow;
	private final Map<CombatStyle, JLabel> styleValues = new java.util.EnumMap<>(CombatStyle.class);
	private final JLabel accuracyValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel dpsValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel uptimeValue = Theme.boldLabel("-", Theme.TEXT);
	private final JPanel fightRow;
	// Kill times: personal best, last kill and the recent average.
	private final JLabel pbValue = Theme.boldLabel("-", Theme.GOLD);
	private final JLabel lastValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel avgValue = Theme.boldLabel("-", Theme.TEXT);
	private final JPanel timesRow;
	// Every collection log item from the boss: bright if obtained, faded if not.
	private final JPanel collectionGrid = new JPanel(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
	private String collectionKey;
	// Label and value rows, in sections.
	private final InfoRow killsRateRow = new InfoRow("Kills/hr");
	private final InfoRow gpRateRow = new InfoRow("GP/hr (trip)");
	private final InfoRow fightRateRow = new InfoRow("GP/hr (in fight)");
	private final InfoRow taskRow = new InfoRow("Task");
	private final InfoRow rareRow = new InfoRow("Rare drop");
	private final InfoRow prayerRow = new InfoRow("Prayer");
	private final InfoRow driestRow = new InfoRow("Driest");
	private final InfoRow sinceUniqueRow = new InfoRow("Since last unique");
	private final InfoRow dropLuckRow = new InfoRow("Luck vs average");
	private final InfoRow achievementsRow = new InfoRow("Combat Achievements");
	private final InfoRow suppliesRow = new InfoRow("Supplies");
	private final InfoRow deathsRow = new InfoRow("Deaths");
	private final InfoRow netRow = new InfoRow("Net profit");
	private final InfoRow netRateRow = new InfoRow("Net GP/hr");
	// From the last update, for net profit.
	private long lootValue;
	private long activeMillis;
	private final InfoRow damageTakenRow = new InfoRow("Damage taken");
	private final InfoRow flawlessRow = new InfoRow("Flawless kills");
	private final InfoRow idleRow = new InfoRow("Idle time");
	private final InfoRow.Section fightSection = new InfoRow.Section("Hits");
	private final InfoRow.Section overview = new InfoRow.Section("Rates");
	private final InfoRow.Section collectionSection = new InfoRow.Section("Luck");
	private final InfoRow.Section dropsSection = new InfoRow.Section("Drops");
	private final InfoRow.Section bossSection = new InfoRow.Section("Boss");
	private final InfoRow.Section costsSection = new InfoRow.Section("Profit");
	private final CombatXpTrackerPlugin plugin;
	private final JPanel lootGrid = new JPanel(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
	private final JLabel noLootLabel = Theme.label("No drops recorded yet", Theme.SUBTLE);
	// Drops show two rows (the most valuable) until "Show all" is clicked.
	private static final int FOLDED_LOOT = ITEMS_PER_ROW * 2;
	private final JLabel showAllLabel = Theme.label("", Theme.MUTED);
	private boolean showAllLoot;
	private List<MonsterTracker.LootLine> lastLoot;

	private boolean collapsed;
	// null until the first update, so an empty loot list still shows its message.
	private String lootKey;

	MonsterCard(String monsterName, ItemManager itemManager, CombatXpTrackerPlugin plugin)
	{
		this(monsterName, itemManager, plugin, null);
	}

	/**
	 * @param onKillGoal opens the kill count goal dialog for this monster, or null for no menu item
	 */
	MonsterCard(String monsterName, ItemManager itemManager, CombatXpTrackerPlugin plugin,
		java.util.function.Consumer<String> onKillGoal)
	{
		this.monsterName = monsterName;
		this.itemManager = itemManager;
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		// Header: name (and a task tag) over kills; loot over fighting or GP/hr. Folded, this is
		// the whole card.
		JPanel nameLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		nameLine.setOpaque(false);
		nameLabel.setText(monsterName);
		nameLine.add(nameLabel);
		JPanel tagGap = new JPanel();
		tagGap.setOpaque(false);
		tagGap.setPreferredSize(new Dimension(5, 1));
		nameLine.add(tagGap);
		nameLine.add(taskTag);
		taskTag.setVisible(false);
		taskTag.setToolTipText("Your current slayer task");
		JPanel killsLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		killsLine.setOpaque(false);
		killsLine.add(killsLabel);
		todayLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
		killsLine.add(todayLabel);
		JPanel left = new JPanel(new GridLayout(2, 1, 0, 1));
		left.setOpaque(false);
		left.add(nameLine);
		left.add(killsLine);
		JPanel right = new JPanel(new GridLayout(2, 1, 0, 1));
		right.setOpaque(false);
		valueLabel.setHorizontalAlignment(JLabel.RIGHT);
		statusLabel.setHorizontalAlignment(JLabel.RIGHT);
		right.add(valueLabel);
		right.add(statusLabel);

		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.add(left, BorderLayout.CENTER);
		header.add(right, BorderLayout.EAST);

		// Hits: three slim strips instead of rows of tiles.
		JPanel statsRow = UiKit.strip(new JLabel[]{hitsLabel, avgLabel, maxLabel},
			new JLabel[]{UiKit.caption("hits"), UiKit.caption("avg"), maxCaption});
		hitsLabel.setToolTipText("Hits on " + monsterName + ", 0s included");
		avgLabel.setToolTipText("Your average hit on " + monsterName + ", 0s included");

		JLabel[] styleCaptions = new JLabel[STYLES.length];
		JLabel[] styleLabels = new JLabel[STYLES.length];
		for (int i = 0; i < STYLES.length; i++)
		{
			CombatStyle style = STYLES[i];
			JLabel value = Theme.boldLabel("-", Theme.SUBTLE);
			value.setToolTipText("Biggest " + style.getDisplayName().toLowerCase() + " hit on " + monsterName);
			styleValues.put(style, value);
			styleLabels[i] = value;
			styleCaptions[i] = UiKit.caption(styleCaption(style));
		}
		styleRow = spaced(UiKit.strip(styleLabels, styleCaptions));

		accuracyValue.setToolTipText("<html>The share of your hits on " + monsterName + " that did damage.<br>"
			+ "A 0 counts as a miss, though a hit can also roll 0.</html>");
		dpsValue.setToolTipText("Your damage per second while fighting " + monsterName);
		uptimeValue.setToolTipText("<html>How much of each fight you spent attacking. Ticks past your weapon's attack speed "
			+ "before the next attack<br>(eating, moving, a late click) count against it. Breaks over 10 seconds aren't counted.</html>");
		fightRow = spaced(UiKit.strip(new JLabel[]{accuracyValue, dpsValue, uptimeValue},
			new JLabel[]{UiKit.caption("acc"), UiKit.caption("dps"), UiKit.caption("uptime")}));
		fightRow.setVisible(false);

		pbValue.setToolTipText("Your fastest kill, from the game's own fight duration message");
		lastValue.setToolTipText("Your most recent kill");
		avgValue.setToolTipText("Your average over your recent kills (up to " + BossTimes.RECENT_KILLS + ")");
		timesRow = spaced(UiKit.strip(new JLabel[]{pbValue, lastValue, avgValue},
			new JLabel[]{UiKit.caption("pb"), UiKit.caption("last"), UiKit.caption("avg")}));
		timesRow.setVisible(false);

		// BoxLayout skips hidden rows; a grid would leave blank space for them.
		JPanel extra = new JPanel();
		extra.setLayout(new BoxLayout(extra, BoxLayout.Y_AXIS));
		extra.setOpaque(false);
		collectionGrid.setOpaque(false);
		collectionGrid.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		collectionGrid.setVisible(false);
		lootGrid.setOpaque(false);
		lootGrid.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		overview.with(killsRateRow).with(gpRateRow).with(fightRateRow).with(prayerRow).with(taskRow);
		collectionSection.with(dropLuckRow).with(sinceUniqueRow).with(rareRow).with(driestRow).with(collectionGrid);
		showAllLabel.setHorizontalAlignment(JLabel.RIGHT);
		showAllLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		showAllLabel.setVisible(false);
		showAllLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleAllLoot();
			}
		});
		dropsSection.with(lootGrid).with(showAllLabel);
		fightSection.with(spaced(statsRow)).with(styleRow).with(fightRow).with(damageTakenRow).with(flawlessRow).with(idleRow);
		bossSection.with(timesRow).with(achievementsRow);
		costsSection.with(suppliesRow).with(netRow).with(netRateRow).with(deathsRow);
		for (InfoRow.Section section : new InfoRow.Section[]{overview, collectionSection, dropsSection, fightSection, bossSection,
			costsSection})
		{
			section.setVisible(false);
			extra.add(section);
		}

		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		body.add(extra, BorderLayout.NORTH);

		shell.add(header, BorderLayout.NORTH);
		shell.add(body, BorderLayout.CENTER);
		add(shell, BorderLayout.NORTH);

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
		if (onKillGoal != null)
		{
			JMenuItem goal = new JMenuItem("Set kill goal...");
			goal.setToolTipText("A kill count to aim for, like 500, shown in the Goals tab");
			goal.addActionListener(e -> onKillGoal.accept(monsterName));
			menu.add(goal);
		}
		JMenuItem hide = new JMenuItem("Hide " + monsterName);
		hide.setToolTipText("Keeps tracking it, but leaves it out of this tab. Unhide from the bottom of the tab or the plugin settings.");
		hide.addActionListener(e -> plugin.hideMonster(monsterName));
		menu.add(hide);
		JMenuItem copyImage = new JMenuItem("Copy as image");
		copyImage.setToolTipText("Copies this card as a picture, to paste into Discord");
		copyImage.addActionListener(e -> WrappedDialog.copyImage(snapshot()));
		menu.add(copyImage);
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
				shell.setBackground(Theme.CARD_HOVER);
				shell.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				shell.setBackground(Theme.CARD);
				shell.repaint();
			}
		});

		// Stays collapsed across restarts if it was collapsed before.
		collapsed = plugin.isCollapsed(monsterName);
		body.setVisible(!collapsed);
	}

	/**
	 * The card as it looks now, as a picture.
	 */
	java.awt.image.BufferedImage snapshot()
	{
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()),
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.setColor(Theme.BACKGROUND);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		paint(g);
		g.dispose();
		return image;
	}

	String getMonsterName()
	{
		return monsterName;
	}

	/**
	 * WHAT: Marks the card as the monster you're fighting now, and shows this session's kills.
	 * WHY: The card you're fighting gets a green outline and "fighting", so it's easy to find;
	 * "+12 today" shows progress even on the all time view.
	 * PRE: Swing thread. sessionKills is this session's kills, or 0.
	 * POST: The header and outline are updated.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void setStatus(boolean fightingNow, int sessionKills, boolean showToday)
	{
		fighting = fightingNow;
		todayKills = showToday ? sessionKills : 0;
		refreshHeader();
	}

	private int todayKills;

	/**
	 * WHAT: Redraws the header's second line, its right side and the outline from the latest values.
	 * WHY: Both update() and setStatus() change parts of it.
	 * PRE: Swing thread.
	 * POST: The header shows the current state.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void refreshHeader()
	{
		todayLabel.setText(todayKills > 0 ? "+" + Formatting.withCommas(todayKills) + " today" : "");
		todayLabel.setVisible(!todayLabel.getText().isEmpty());
		// The "2h ago" goes when there's a "+12 today" or the fight is now.
		killsLabel.setText(killsText + (todayLabel.isVisible() || fighting || sinceText.isEmpty() ? "" : "  ·  " + sinceText));
		if (fighting)
		{
			statusLabel.setText("● fighting");
			statusLabel.setForeground(Theme.SUCCESS);
		}
		else
		{
			statusLabel.setText(rateText);
			statusLabel.setForeground(Theme.GOLD);
		}
		shell.outline(fighting ? Theme.SUCCESS : pinned ? Theme.GOLD : null);
	}

	private String killsText = "";
	private String rateText = "";

	/**
	 * WHAT: Wraps a strip so strips stacked in a section have a small gap between them.
	 * WHY: A rounded strip paints its whole area, so the gap can't be its own border.
	 * PRE: strip is a component to show.
	 * POST: Returns a holder; show or hide the holder.
	 * RETURN: the holder
	 * THROWS: Nothing.
	 */
	private static JPanel spaced(JComponent strip)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(BorderFactory.createEmptyBorder(1, 0, 3, 0));
		holder.add(strip, BorderLayout.CENTER);
		return holder;
	}

	/**
	 * WHAT: The short caption for a style's biggest hit: "melee", "range" or "mage".
	 * WHY: Fits beside the number in a strip cell.
	 * PRE: style is not null.
	 * POST: Nothing changes.
	 * RETURN: the caption
	 * THROWS: Nothing.
	 */
	static String styleCaption(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return "range";
			case MAGIC:
				return "mage";
			default:
				return "melee";
		}
	}

	/**
	 * Supplies used fighting this monster, or null for none.
	 */
	void updateSupplies(SupplyLedger.Totals supplies)
	{
		boolean show = supplies != null && supplies.totalGp() > 0;
		suppliesRow.set(show ? Formatting.compactXp(supplies.totalGp()) + " gp" : null, Theme.TEXT,
			show ? SupplyLedger.tooltip(supplies) : null);
		// Loot minus supplies, only once there are supplies to take off.
		long net = show ? lootValue - supplies.totalGp() : 0;
		String netTip = "Loot minus supplies used. Deaths aren't taken off: most of what you carry comes back.";
		netRow.set(show ? (net < 0 ? "-" : "") + Formatting.compactXp(Math.abs(net)) + " gp" : null, net >= 0 ? Theme.GOLD : BAD, netTip);
		double netPerHour = show ? ActivityTimer.perHour(net, activeMillis) : -1;
		netRateRow.set(show && activeMillis > 0 && Math.abs(netPerHour) > 0 ? (netPerHour < 0 ? "-" : "")
			+ Formatting.compactXp(Math.round(Math.abs(netPerHour))) + " gp/hr" : null, netPerHour >= 0 ? Theme.GOLD : BAD, netTip);
		costsSection.setDetail(show ? (net < 0 ? "-" : "+") + Formatting.compactXp(Math.abs(net)) + " net" : "",
			net >= 0 ? Theme.GOLD : BAD, show ? netTip : null);
		costsSection.refresh();
	}

	/**
	 * Boss extras: kill times (or null), the driest missing collection log items, and
	 * Combat Achievements.
	 */
	void updateBoss(BossTimes.Times times, List<DryCalculator.Dry> dry, List<CombatAchievements.Task> achievements)
	{
		List<Double> recent = times == null ? java.util.Collections.emptyList() : times.recentOrEmpty();
		timesRow.setVisible(times != null && times.best > 0);
		bossSection.setDetail(times != null && times.best > 0 ? "PB " + KillTimerOverlay.clock(times.best) : "", Theme.GOLD, null);
		if (times != null && times.best > 0)
		{
			pbValue.setText(KillTimerOverlay.clock(times.best));
			lastValue.setText(times.last > 0 ? KillTimerOverlay.clock(times.last) : "-");
			double sum = 0;
			for (double t : recent)
			{
				sum += t;
			}
			avgValue.setText(recent.size() >= 2 ? KillTimerOverlay.clock(sum / recent.size()) : "-");
			// Green when the last kill was the best.
			lastValue.setForeground(times.last > 0 && Math.abs(times.last - times.best) < 0.001 ? Theme.SUCCESS : Theme.TEXT);
		}

		updateCollectionGrid(plugin.getCollectionItems(monsterName), dry);
		if (!dry.isEmpty())
		{
			// The icons show what's missing; this row says how dry the driest one is.
			StringBuilder tip = new StringBuilder("<html>How dry you are on each missing item, from your kill count:<br>");
			for (DryCalculator.Dry d : dry)
			{
				tip.append(DryCalculator.line(d)).append("<br>");
			}
			tip.append("<br>Drop rates from the OSRS Wiki.</html>");
			DryCalculator.Dry driest = dry.get(0);
			driestRow.set(String.format(java.util.Locale.US, "%s %.1fx", driest.getItem(), driest.getRatio()),
				driest.getRatio() >= 1 ? DEATHS : Theme.TEXT, tip.toString());
		}
		else
		{
			driestRow.set(null);
		}
		collectionSection.refresh();

		if (achievements.isEmpty())
		{
			achievementsRow.set(null);
		}
		else
		{
			int done = 0;
			for (CombatAchievements.Task t : achievements)
			{
				done += t.isDone() ? 1 : 0;
			}
			achievementsRow.set(done + "/" + achievements.size(), done == achievements.size() ? Theme.SUCCESS : Theme.TEXT,
				CombatAchievements.tooltip(achievements, times == null ? 0 : times.best));
		}
		bossSection.refresh();
	}

	/**
	 * The boss's collection log as item icons, obtained ones bright, missing ones faded, and
	 * the driest missing item outlined in red.
	 */
	private void updateCollectionGrid(List<CollectionLog.Item> items, List<DryCalculator.Dry> dry)
	{
		StringBuilder key = new StringBuilder();
		for (CollectionLog.Item item : items)
		{
			key.append(item.id).append(item.obtained ? '+' : '-');
		}
		for (DryCalculator.Dry d : dry)
		{
			key.append('|').append(d.getItem()).append(String.format(java.util.Locale.US, "%.2f", d.getRatio()));
		}
		collectionGrid.setVisible(!items.isEmpty());
		if (key.toString().equals(collectionKey))
		{
			return;
		}
		collectionKey = key.toString();

		Map<String, DryCalculator.Dry> dryByName = new java.util.HashMap<>();
		for (DryCalculator.Dry d : dry)
		{
			dryByName.put(d.getItem().toLowerCase(java.util.Locale.ROOT), d);
		}
		String driest = dry.isEmpty() ? null : dry.get(0).getItem();
		collectionGrid.removeAll();
		for (CollectionLog.Item item : items)
		{
			collectionGrid.add(collectionSlot(item, dryByName.get(item.name == null ? "" : item.name.toLowerCase(java.util.Locale.ROOT)),
				item.name != null && item.name.equalsIgnoreCase(driest)));
		}
		int remainder = items.size() % ITEMS_PER_ROW;
		for (int i = 0; remainder != 0 && i < ITEMS_PER_ROW - remainder; i++)
		{
			collectionGrid.add(emptySlot());
		}
		collectionGrid.revalidate();
		collectionGrid.repaint();
	}

	private JLabel collectionSlot(CollectionLog.Item item, DryCalculator.Dry dry, boolean driest)
	{
		Slot slot = emptySlot();
		if (itemManager != null && item.id > 0)
		{
			AsyncBufferedImage image = itemManager.getImage(item.id);
			if (item.obtained)
			{
				image.addTo(slot);
			}
			else
			{
				// Faded and grey, like a missing slot in the in-game log.
				image.onLoaded(() -> SwingUtilities.invokeLater(() -> slot.setIcon(
					new javax.swing.ImageIcon(ImageUtil.alphaOffset(ImageUtil.grayscaleImage(image), 0.35f)))));
			}
		}
		if (driest)
		{
			slot.setOutline(DEATHS);
		}
		String status = item.obtained ? "<font color='#60dc8c'>Obtained</font>"
			: dry != null ? DryCalculator.line(dry).substring(item.name.length() + 2) : "Not obtained yet";
		slot.setToolTipText("<html>" + item.name + "<br>" + status + "</html>");
		return slot;
	}

	/**
	 * Damage taken per kill and flawless kills (bosses, or null), and time lost between
	 * attacks in ticks (or -1).
	 */
	void updateFightExtras(DamageTaken.Stats taken, long idleTicks)
	{
		double perKill = taken == null ? -1 : taken.perKill();
		damageTakenRow.set(perKill < 0 ? null : String.format(java.util.Locale.US, "%.0f a kill", perKill), Theme.TEXT,
			perKill < 0 ? null : "Average damage you took per kill, over " + Formatting.withCommas(taken.kills) + " kills");
		flawlessRow.set(perKill < 0 ? null : taken.flawlessLabel(), taken != null && taken.flawless > 0 ? Theme.SUCCESS : Theme.TEXT,
			"Kills where you took no damage at all");
		idleRow.set(idleTicks < 0 ? null : idleTime(idleTicks), Theme.TEXT,
			"<html>Time lost between attacks: ticks past your weapon's attack speed before your next attack<br>"
				+ "(eating, moving, a late click). Breaks over 10 seconds aren't counted.</html>");
		fightSection.refresh();
	}

	/**
	 * Game ticks as time, e.g. 420 ticks -> "4m 12s".
	 */
	static String idleTime(long ticks)
	{
		long seconds = Math.round(ticks * 0.6);
		return seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
	}

	/**
	 * Deaths to this monster, or null for none.
	 */
	void updateDeaths(DeathLedger.Deaths deaths)
	{
		boolean show = deaths != null && deaths.count > 0;
		deathsRow.set(show ? Formatting.withCommas(deaths.count) : null, DEATHS, show
			? "<html>You were carrying " + Formatting.compactXp(deaths.carried) + " gp in all when you died to " + monsterName
				+ ".<br>That was at risk, not lost: most of it goes to a gravestone or Death's Office.</html>"
			: null);
		costsSection.refresh();
	}

	/**
	 * Accuracy and uptime (0 to 100, or -1 if unknown) and damage per second (or -1).
	 */
	void updateFight(int accuracy, double dps, int uptime)
	{
		fightRow.setVisible(accuracy >= 0 || dps >= 0 || uptime >= 0);
		fightSection.setDetail(uptime >= 0 ? uptime + "% uptime" : "", Theme.MUTED, null);
		accuracyValue.setText(accuracy >= 0 ? accuracy + "%" : "-");
		dpsValue.setText(dps >= 0 ? String.format(java.util.Locale.US, "%.1f", dps) : "-");
		uptimeValue.setText(uptime >= 0 ? uptime + "%" : "-");
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
		// A gold outline marks pinned cards (green wins while you're fighting it).
		this.pinned = pinned;
		header.setToolTipText(pinned ? "Pinned to the top. Right-click to unpin." : null);
		taskTag.setVisible(taskRemaining > 0);

		if (s.getKills() > 0 && rareValue < Long.MAX_VALUE)
		{
			String value = QuantityFormatter.quantityToStackSize(rareValue) + "+";
			rareRow.setLabel("Rare drop (" + value + ")");
			if (s.isRareSinceRestore())
			{
				rareRow.set(kills(s.getKillsSinceRare()) + " dry", Theme.TEXT,
					"Which kill your last rare came on wasn't saved, so this started from 0. It starts again from 0 at your next one.");
			}
			else
			{
				rareRow.set(kills(s.hasEverDroppedRare() ? s.getKillsSinceRare() : s.getKills()) + " dry", Theme.TEXT,
					s.hasEverDroppedRare() ? "Kills since this monster last dropped an item worth " + value
						: "This monster hasn't dropped an item worth " + value + " yet");
			}
		}
		else
		{
			rareRow.set(null);
		}

		updateSinceUnique(s);

		CollectionLog.Status log = plugin.getCollectionStatus(monsterName);
		if (log != null)
		{
			collectionSection.setDetail(log.getObtained() + "/" + log.getTotal() + " log",
				log.isComplete() ? Theme.SUCCESS : Theme.GOLD, collectionTooltip(log));
		}

		String prayer = plugin.getPrayerDrainText(monsterName);
		prayerRow.set(prayer, Theme.TEXT, "<html>How long a prayer potion dose lasts while fighting this monster, measured "
			+ "from your own prayer drain.<br>Restores don't count, and kills without prayer on aren't included.</html>");

		if (taskRemaining > 0)
		{
			String text = Formatting.withCommas(taskRemaining) + " left";
			double killsPerHour = s.getKillsPerHour();
			if (killsPerHour > 0)
			{
				text += " (~" + Formatting.duration(taskRemaining / killsPerHour) + ")";
			}
			taskRow.set(text, Theme.SUCCESS, "Your current slayer task. Time left uses your kills/hr on this monster.");
		}
		else
		{
			taskRow.set(null);
		}

		lootValue = s.getLootValue();
		activeMillis = s.getActiveMillis();
		killsText = s.getKills() > 0 ? Formatting.withCommas(s.getKills()) + (s.getKills() == 1 ? " kill" : " kills")
			: Formatting.withCommas(s.getHits()) + (s.getHits() == 1 ? " hit" : " hits");
		sinceText = UiKit.ago(s.getLastActivityMillis(), System.currentTimeMillis());
		valueLabel.setText(s.getLootValue() > 0 ? QuantityFormatter.quantityToStackSize(s.getLootValue()) : "");
		valueLabel.setToolTipText(s.getLootValue() > 0 ? Formatting.withCommas(s.getLootValue()) + " gp of loot" : null);
		double headerRate = s.getTripGpPerHour();
		rateText = headerRate > 0 ? QuantityFormatter.quantityToStackSize(Math.round(headerRate)) + "/hr" : "";
		statusLabel.setToolTipText(headerRate > 0 ? "Loot per hour of your last trip here" : null);

		hitsLabel.setText(Formatting.withCommas(s.getHits()));
		avgLabel.setText(String.format(java.util.Locale.US, "%.1f", s.getAverageHit()));
		if (s.getBiggestHit() >= 0)
		{
			maxLabel.setText(String.valueOf(s.getBiggestHit()));
			CombatStyle style = s.getBiggestHitStyle();
			maxLabel.setForeground(style != null ? style.getColor() : Theme.TEXT);
			maxLabel.setToolTipText(style != null ? "Biggest hit, dealt with " + style.getDisplayName() : "Biggest hit");
			maxCaption.setText(style != null ? "max " + shortName(style) : "max");
		}
		else
		{
			maxLabel.setText("-");
			maxLabel.setForeground(Theme.SUBTLE);
			maxCaption.setText("max");
		}

		for (CombatStyle style : STYLES)
		{
			Integer biggest = s.getBiggestByStyle().get(style);
			JLabel value = styleValues.get(style);
			value.setText(biggest == null ? "-" : String.valueOf(biggest));
			value.setForeground(biggest == null ? Theme.SUBTLE : style.getColor());
		}
		// One style used: the Max tile already says which.
		styleRow.setVisible(s.getBiggestByStyle().size() > 1);

		double killsPerHour = s.getKillsPerHour();
		String perHour = "Per hour of fighting this monster. Breaks over 5 minutes aren't counted.";
		killsRateRow.set(killsPerHour < 0 ? null : String.format(java.util.Locale.US, "%.1f", killsPerHour), Theme.TEXT, perHour);
		double tripPerHour = s.getTripGpPerHour();
		double fightPerHour = s.getFightGpPerHour();
		gpRateRow.set(tripPerHour <= 0 ? null : QuantityFormatter.quantityToStackSize(Math.round(tripPerHour))
				+ (s.isAway() ? " (away)" : ""),
			s.isAway() ? Theme.MUTED : Theme.GOLD,
			"<html>Loot per hour of the whole trip, banking and walking included. Updates every 5 ticks.<br>"
				+ "It keeps counting after you leave, so it falls while you're away and climbs again with new loot.<br>"
				+ "Logging out, a break over 20 minutes or moving on to another monster ends the trip.</html>");
		// Only worth a second row when banking and walking make a real difference.
		boolean showFight = fightPerHour > 0 && tripPerHour > 0 && fightPerHour > tripPerHour * 1.05;
		fightRateRow.set(showFight ? QuantityFormatter.quantityToStackSize(Math.round(fightPerHour)) : null, Theme.MUTED,
			"Loot per hour of fighting only: gaps over 30 seconds between kills, hits and drops are left out.");
		overview.refresh();
		collectionSection.refresh();

		updateLoot(s.getLoot());
		dropsSection.setDetail(s.getLoot().isEmpty() ? "" : s.getLoot().size() + (s.getLoot().size() == 1 ? " kind" : " kinds"),
			Theme.MUTED, "Right-click an item to ignore it, or to remove it from this monster's loot");
		dropsSection.refresh();
		refreshHeader();
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
		key.append(showAllLoot);
		if (key.toString().equals(lootKey))
		{
			return;
		}
		lootKey = key.toString();
		lastLoot = loot;

		lootGrid.removeAll();
		if (loot.isEmpty())
		{
			lootGrid.setLayout(new BorderLayout());
			lootGrid.add(noLootLabel, BorderLayout.CENTER);
		}
		else
		{
			lootGrid.setLayout(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
			// The most valuable two rows, unless "Show all" is open.
			int shown = showAllLoot ? loot.size() : Math.min(loot.size(), FOLDED_LOOT);
			for (MonsterTracker.LootLine line : loot.subList(0, shown))
			{
				lootGrid.add(itemSlot(line));
			}
			// Pad the last row so icons stay the same size.
			int remainder = shown % ITEMS_PER_ROW;
			for (int i = 0; remainder != 0 && i < ITEMS_PER_ROW - remainder; i++)
			{
				lootGrid.add(emptySlot());
			}
		}
		int more = loot.size() - FOLDED_LOOT;
		showAllLabel.setVisible(more > 0);
		showAllLabel.setText(showAllLoot ? "Show less  ▴" : "Show all " + loot.size() + "  ▾");
		lootGrid.revalidate();
		lootGrid.repaint();
	}

	/**
	 * WHAT: Opens or folds the full drops grid.
	 * WHY: A boss with 50+ kinds of drop would fill the whole sidebar; two rows show the best ones.
	 * PRE: Swing thread.
	 * POST: The grid is rebuilt with all drops or the top ones.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void toggleAllLoot()
	{
		showAllLoot = !showAllLoot;
		if (lastLoot != null)
		{
			updateLoot(lastLoot);
		}
		revalidate();
		repaint();
	}

	private JLabel itemSlot(MonsterTracker.LootLine line)
	{
		Slot slot = emptySlot();
		int quantity = (int) Math.min(Integer.MAX_VALUE, line.getQuantity());
		if (itemManager != null)
		{
			AsyncBufferedImage image = itemManager.getImage(line.getItemId(), quantity, quantity > 1);
			image.addTo(slot);
		}

		String value = line.getTotalValue() > 0
			? "<br><font color='#FFC640'>" + Formatting.withCommas(line.getTotalValue()) + " gp</font>"
			: "";
		if (line.isRare())
		{
			// Rare drops get a gold border and are sorted first.
			slot.setOutline(Theme.GOLD);
		}
		slot.setToolTipText("<html>" + line.getName() + " x " + Formatting.withCommas(line.getQuantity()) + value
			+ "<br><font color='#888888'>Right-click to ignore or remove</font></html>");

		JPopupMenu menu = new JPopupMenu();
		JMenuItem ignore = new JMenuItem("Ignore " + line.getName());
		ignore.setToolTipText("Leave it out of loot and loot value everywhere. Undo in the plugin settings.");
		ignore.addActionListener(e -> plugin.ignoreItem(line.getName()));
		menu.add(ignore);
		JMenuItem remove = new JMenuItem("Remove from loot");
		remove.setToolTipText("For items that were never a drop, like your own gear back from a gravestone");
		remove.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this,
				"Remove " + line.getName() + " x " + Formatting.withCommas(line.getQuantity()) + " from the " + monsterName
					+ " loot?\nThis can't be undone.", "Remove from loot", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				plugin.removeLoot(monsterName, line.getItemId());
			}
		});
		menu.add(remove);
		slot.setComponentPopupMenu(menu);
		return slot;
	}

	private static Slot emptySlot()
	{
		Slot slot = new Slot();
		slot.setPreferredSize(ITEM_SIZE);
		slot.setHorizontalAlignment(JLabel.CENTER);
		slot.setVerticalAlignment(JLabel.CENTER);
		return slot;
	}

	/**
	 * WHAT: An item slot: a rounded dark square with the item, and an optional coloured outline.
	 * WHY: Matches the rounded cards; gold marks rare drops and red the driest collection log item.
	 * PRE: Swing thread.
	 * POST: Paints its background and outline, then the item icon.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class Slot extends JLabel
	{
		private java.awt.Color outline;

		Slot()
		{
			setOpaque(false);
		}

		void setOutline(java.awt.Color colour)
		{
			outline = colour;
			repaint();
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(Theme.current().isLight() ? Theme.SLOT : new java.awt.Color(28, 28, 28));
			g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
			if (outline != null)
			{
				g2.setStroke(new java.awt.BasicStroke(1.5f));
				g2.setColor(outline);
				g2.draw(new java.awt.geom.RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 6, 6));
			}
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/**
	 * "mel", "rng" or "mag", to fit a tile caption.
	 */
	static String shortName(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return "rng";
			case MAGIC:
				return "mag";
			default:
				return "mel";
		}
	}

	/**
	 * Kills since the last collection log unique. Starts again from 0 each time one drops.
	 */
	/**
	 * WHAT: Shows your luck with this boss's uniques against the average, e.g. "+18%".
	 * WHY: One number that says whether you've been lucky, with the detail per item on hover.
	 * PRE: Swing thread. luck may be null (no known rates, or too few kills yet).
	 * POST: The row is shown or hidden.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void updateDropLuck(DryCalculator.Luck luck)
	{
		if (luck == null)
		{
			dropLuckRow.set(null);
			collectionSection.refresh();
			return;
		}
		double pct = luck.percent();
		String value = String.format(java.util.Locale.US, "%s%.0f%%", pct >= 0 ? "+" : "", pct);
		StringBuilder tip = new StringBuilder(String.format(java.util.Locale.US,
			"<html>%s uniques from %s kills, where the average player would have %.1f.<br>",
			Formatting.withCommas(Math.round(luck.getGot())), monsterName, luck.getExpected()));
		tip.append(pct >= 0 ? "That's luckier than average." : "That's less lucky than average.").append("<br><br>");
		for (String line : luck.getLines())
		{
			tip.append(line).append("<br>");
		}
		tip.append("<br>From the kills and drops tracked here, at the OSRS Wiki's drop rates.</html>");
		dropLuckRow.set(value, pct >= 0 ? Theme.SUCCESS : BAD, tip.toString());
		collectionSection.refresh();
	}

	private void updateSinceUnique(MonsterTracker.Snapshot s)
	{
		if (s.getKills() <= 0 || !plugin.hasUniques(monsterName))
		{
			sinceUniqueRow.set(null);
			return;
		}
		int since = s.getKillsSinceUnique();
		sinceUniqueRow.set(kills(since), Theme.TEXT, s.isUniqueSinceRestore()
			? "Which kill your last unique came on wasn't saved, so this started from 0. It starts again from 0 at your next one."
			: "Kills since your last unique from " + monsterName + ", new or a duplicate. Starts again from 0 each time one drops.");
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
