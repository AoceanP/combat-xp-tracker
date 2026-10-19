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
import net.runelite.client.util.ImageUtil;
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
	// Space between stat tiles.
	private static final int TILE_GAP = 2;

	private final ItemManager itemManager;
	private final String monsterName;

	private final JPanel header = new JPanel(new BorderLayout(6, 0));
	private final JLabel nameLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel killsLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel valueLabel = Theme.label("", Theme.GOLD);

	private static final java.awt.Color DEATHS = new java.awt.Color(235, 110, 100);
	// Bad news: dry streaks, deaths, running low.
	static final java.awt.Color BAD = DEATHS;
	private final JPanel body = new JPanel(new BorderLayout(0, 6));
	private final JLabel hitsLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel avgLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel maxLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel maxCaption = Theme.label("Max hit", Theme.MUTED);
	// Biggest hit per style, always Melee, Ranged, Magic so the columns line up across cards.
	private final JPanel styleRow = new JPanel(new GridLayout(1, STYLES.length, TILE_GAP, 0));
	private final Map<CombatStyle, JLabel> styleValues = new java.util.EnumMap<>(CombatStyle.class);
	private final JLabel accuracyValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel dpsValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel uptimeValue = Theme.boldLabel("-", Theme.TEXT);
	private final JPanel fightRow = new JPanel(new GridLayout(1, 3, TILE_GAP, 0));
	// Kill times: personal best, last kill and the recent average.
	private final JLabel pbValue = Theme.boldLabel("-", Theme.GOLD);
	private final JLabel lastValue = Theme.boldLabel("-", Theme.TEXT);
	private final JLabel avgValue = Theme.boldLabel("-", Theme.TEXT);
	private final JPanel timesRow = new JPanel(new GridLayout(1, 3, TILE_GAP, 0));
	// Every collection log item from the boss: bright if obtained, faded if not.
	private final JPanel collectionGrid = new JPanel(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
	private String collectionKey;
	// Label and value rows, in sections.
	private final InfoRow killsRateRow = new InfoRow("Kills/hr");
	private final InfoRow gpRateRow = new InfoRow("GP/hr");
	private final InfoRow taskRow = new InfoRow("Task");
	private final InfoRow rareRow = new InfoRow("Rare drop");
	private final InfoRow prayerRow = new InfoRow("Prayer");
	private final InfoRow driestRow = new InfoRow("Driest");
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
	private final InfoRow.Section fightSection = new InfoRow.Section("Fight");
	private final InfoRow.Section overview = new InfoRow.Section(null);
	private final InfoRow.Section collectionSection = new InfoRow.Section("Collection log");
	private final InfoRow.Section bossSection = new InfoRow.Section("Boss");
	private final InfoRow.Section costsSection = new InfoRow.Section("Profit");
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
		JPanel statsRow = new JPanel(new GridLayout(1, 3, TILE_GAP, 0));
		statsRow.setOpaque(false);
		statsRow.add(tile(hitsLabel, "Hits"));
		statsRow.add(tile(avgLabel, "Avg hit"));
		statsRow.add(tile(maxLabel, maxCaption));

		styleRow.setOpaque(false);
		for (CombatStyle style : STYLES)
		{
			JLabel value = Theme.boldLabel("-", Theme.SUBTLE);
			styleValues.put(style, value);
			JPanel tile = tile(value, style.getDisplayName());
			tile.setToolTipText("Biggest " + style.getDisplayName().toLowerCase() + " hit on " + monsterName);
			styleRow.add(tile);
		}

		fightRow.setOpaque(false);
		JPanel accuracyTile = tile(accuracyValue, "Accuracy");
		accuracyTile.setToolTipText("<html>The share of your hits on " + monsterName + " that did damage.<br>"
			+ "A 0 counts as a miss, though a hit can also roll 0.</html>");
		JPanel dpsTile = tile(dpsValue, "DPS");
		dpsTile.setToolTipText("Your damage per second while fighting " + monsterName);
		JPanel uptimeTile = tile(uptimeValue, "Uptime");
		uptimeTile.setToolTipText("<html>How much of each fight you spent attacking. Ticks past your weapon's attack speed "
			+ "before the next attack<br>(eating, moving, a late click) count against it. Breaks over 10 seconds aren't counted.</html>");
		fightRow.add(accuracyTile);
		fightRow.add(dpsTile);
		fightRow.add(uptimeTile);
		fightRow.setVisible(false);

		timesRow.setOpaque(false);
		JPanel pbTile = tile(pbValue, "PB");
		pbTile.setToolTipText("Your fastest kill, from the game's own fight duration message");
		JPanel lastTile = tile(lastValue, "Last");
		lastTile.setToolTipText("Your most recent kill");
		JPanel avgTile = tile(avgValue, "Avg");
		avgTile.setToolTipText("Your average over your recent kills (up to " + BossTimes.RECENT_KILLS + ")");
		timesRow.add(pbTile);
		timesRow.add(lastTile);
		timesRow.add(avgTile);
		timesRow.setVisible(false);
		JPanel tileRows = new JPanel(new BorderLayout(0, TILE_GAP));
		tileRows.setOpaque(false);
		tileRows.add(styleRow, BorderLayout.NORTH);
		tileRows.add(fightRow, BorderLayout.SOUTH);

		JPanel stats = new JPanel(new BorderLayout(0, TILE_GAP));
		stats.setOpaque(false);
		stats.add(statsRow, BorderLayout.NORTH);
		stats.add(tileRows, BorderLayout.CENTER);
		// BoxLayout skips hidden rows; a grid would leave blank space for them.
		JPanel extra = new JPanel();
		extra.setLayout(new BoxLayout(extra, BoxLayout.Y_AXIS));
		extra.setOpaque(false);
		extra.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		collectionGrid.setOpaque(false);
		collectionGrid.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		collectionGrid.setVisible(false);
		timesRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		overview.with(killsRateRow).with(gpRateRow).with(taskRow).with(rareRow).with(prayerRow);
		collectionSection.with(collectionGrid).with(driestRow);
		bossSection.with(timesRow).with(achievementsRow);
		costsSection.with(suppliesRow).with(netRow).with(netRateRow).with(deathsRow);
		fightSection.with(damageTakenRow).with(flawlessRow).with(idleRow);
		for (InfoRow.Section section : new InfoRow.Section[]{overview, fightSection, collectionSection, bossSection, costsSection})
		{
			section.setVisible(false);
			extra.add(section);
		}
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
		JLabel slot = emptySlot();
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
			slot.setBorder(BorderFactory.createLineBorder(DEATHS, 1));
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
		accuracyValue.setText(accuracy >= 0 ? accuracy + "%" : "-");
		dpsValue.setText(dps >= 0 ? String.format(java.util.Locale.US, "%.1f", dps) : "-");
		uptimeValue.setText(uptime >= 0 ? uptime + "%" : "-");
	}

	/**
	 * A small stat tile: the value above a caption, centred, like the tiles at the top of the
	 * panel.
	 */
	static JPanel tile(JLabel value, String caption)
	{
		return tile(value, Theme.label(caption, Theme.MUTED));
	}

	private static JPanel tile(JLabel value, JLabel label)
	{
		JPanel tile = new JPanel(new BorderLayout(0, 0));
		tile.setBackground(Theme.HEADER);
		tile.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
		value.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
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
			rareRow.setLabel("Rare drop (" + value + ")");
			if (s.isRareSinceRestore())
			{
				rareRow.set(Formatting.withCommas(s.getKillsSinceRare()) + " since restore", Theme.TEXT,
					"Your rare drops were restored, but not which kill they came on, so this counts from the restore");
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

		CollectionLog.Status log = plugin.getCollectionStatus(monsterName);
		if (log != null)
		{
			String kc = log.getKillCount() >= 0 ? ", " + Formatting.withCommas(log.getKillCount()) + " kc" : "";
			collectionSection.setDetail(log.getObtained() + "/" + log.getTotal() + kc,
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
			maxCaption.setText(style != null ? "Max (" + shortName(style) + ")" : "Max hit");
		}
		else
		{
			maxLabel.setText("-");
			maxLabel.setForeground(Theme.SUBTLE);
			maxCaption.setText("Max hit");
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
		double gpPerHour = s.getGpPerHour();
		String perHour = "Per hour of fighting this monster. Breaks over 5 minutes aren't counted.";
		killsRateRow.set(killsPerHour < 0 ? null : String.format(java.util.Locale.US, "%.1f", killsPerHour), Theme.TEXT, perHour);
		gpRateRow.set(killsPerHour < 0 || gpPerHour <= 0 ? null : QuantityFormatter.quantityToStackSize(Math.round(gpPerHour)),
			Theme.GOLD, perHour);
		overview.refresh();
		collectionSection.refresh();

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
