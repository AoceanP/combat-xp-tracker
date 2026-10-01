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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * The Slayer tab: master, points and streak, what the next tasks are worth, superiors,
 * and the log of finished tasks. Swing thread only.
 */
class SlayerView extends JPanel
{
	/**
	 * Only the newest tasks get a card; older ones still count in the totals.
	 */
	private static final int SHOWN_TASKS = 50;
	private static final int SHOWN_ODDS = 10;
	// Tasks below this chance aren't worth suggesting as a block.
	private static final double BLOCK_ADVICE_MIN_CHANCE = 0.03;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;

	private final JPanel statusCard = card();
	private final JPanel guideCard = card();
	private final JPanel oddsCard = card();
	private final JPanel rankCard = card();
	private final JPanel pointsCard = card();
	private final JPanel superiorCard = card();
	private final JLabel logTitle = Theme.boldLabel("", Theme.TEXT);
	private final JLabel logTotals = Theme.label("", Theme.SUBTLE);
	private final JPanel logList = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JLabel emptyLog = new JLabel();
	private final JLabel clearLog;
	private String viewKey;

	SlayerView(CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setOpaque(false);

		logList.setOpaque(false);
		emptyLog.setText("<html><div style='text-align:center;width:150px'><b>No finished tasks yet</b><br><br>"
			+ "Finish a slayer task and it shows up here with its kills, time, XP, loot and superiors.</div></html>");
		emptyLog.setFont(FontManager.getRunescapeSmallFont());
		emptyLog.setForeground(Theme.MUTED);
		emptyLog.setHorizontalAlignment(SwingConstants.CENTER);
		emptyLog.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

		clearLog = Theme.flatButton("Clear task log", "Delete every task in the log for this account", this::confirmClear);

		JPanel logHeader = new JPanel(new BorderLayout(0, 2));
		logHeader.setOpaque(false);
		logHeader.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
		logHeader.add(logTitle, BorderLayout.NORTH);
		logHeader.add(logTotals, BorderLayout.SOUTH);

		add(statusCard);
		add(holder(guideCard));
		add(pointsCard);
		add(holder(oddsCard));
		add(superiorCard);
		add(holder(rankCard));
		add(logHeader);
		add(holder(emptyLog));
		add(logList);
		add(holder(clearLog));
	}

	void update()
	{
		SlayerStatus status = plugin.getSlayerStatus();
		int taskSuperiors = plugin.getCurrentTaskSuperiors();
		int taskKills = plugin.getCurrentTaskKills();
		LootPrice price = config.lootPrice();
		SlayerGuide guide = plugin.getSlayerGuide();
		TaskOdds odds = plugin.getTaskOdds();
		String key = status + "|" + plugin.getSlayerLogRevision() + "|" + taskSuperiors + "|" + taskKills + "|" + price
			+ "|" + guide + "|" + odds;
		if (key.equals(viewKey))
		{
			return;
		}
		viewKey = key;

		List<SlayerTaskRecord> tasks = plugin.getSlayerTasks();
		List<TaskStats> history = TaskStats.group(tasks, price);
		updateStatus(status);
		updateGuide(guide);
		updatePoints(status);
		updateOdds(odds, history);
		updateSuperiors(tasks, taskSuperiors, taskKills, status.getTask() != null);
		updateRanking(history);
		updateLog(tasks, price);
		revalidate();
		repaint();
	}

	// ---- Sections ----------------------------------------------------------------------

	private void updateStatus(SlayerStatus status)
	{
		statusCard.removeAll();
		statusCard.add(title("Slayer"));
		SlayerPoints.Master master = status.getMaster();
		statusCard.add(row("Master", master == null ? "-" : master.getDisplayName(), Theme.TEXT, null));
		statusCard.add(row("Points", status.getPoints() < 0 ? "-" : Formatting.withCommas(status.getPoints()), Theme.GOLD, null));
		statusCard.add(row("Streak", status.getStreak() < 0 ? "-" : Formatting.withCommas(status.getStreak())
			+ (status.getStreak() == 1 ? " task" : " tasks"), Theme.TEXT,
			"Tasks completed in a row. Krystilia and Mortimer keep their own streaks."));
		if (status.getTask() != null)
		{
			String left = Formatting.withCommas(status.getRemaining())
				+ (status.getAssigned() > 0 ? " / " + Formatting.withCommas(status.getAssigned()) : "");
			statusCard.add(row("Task", status.getTask(), Theme.SUCCESS, "Your current task"));
			statusCard.add(row("Left", left, Theme.SUCCESS, "Kills left out of the number assigned"));
			if (status.getLocation() != null)
			{
				statusCard.add(row("Location", status.getLocation(), Theme.TEXT, "Konar's tasks must be done here"));
			}
		}
		else
		{
			statusCard.add(row("Task", "None", Theme.MUTED, null));
		}
	}

	/**
	 * Where the task can be done and what to bring.
	 */
	private void updateGuide(SlayerGuide guide)
	{
		guideCard.removeAll();
		if (guide.getTask() == null || (guide.getAreas().isEmpty() && guide.getNeeds().isEmpty()))
		{
			guideCard.setVisible(false);
			return;
		}
		guideCard.setVisible(true);
		guideCard.add(title("Task guide"));

		if (!guide.getAreas().isEmpty())
		{
			guideCard.add(Theme.label("Where to go", Theme.MUTED));
			for (SlayerGuide.Area area : guide.getAreas())
			{
				JPanel row = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
				row.setBackground(area.isAssigned() ? Theme.highlight(Theme.SUCCESS, 0.72f) : Theme.HEADER);
				row.setBorder(BorderFactory.createEmptyBorder(3, 7, 4, 7));
				row.add(Theme.boldLabel(area.getName() + (area.isAssigned() ? " (Konar's pick)" : ""),
					area.isAssigned() ? Theme.SUCCESS : Theme.TEXT));
				if (area.getHint() != null)
				{
					JLabel hint = Theme.label("<html><div style='width:130px'>" + area.getHint() + "</div></html>", Theme.SUBTLE);
					row.add(hint);
				}
				guideCard.add(row);
			}
		}

		if (!guide.getNeeds().isEmpty())
		{
			int missing = guide.missingCount();
			JLabel bring = Theme.label(missing > 0 ? "Bring (" + missing + " missing)" : "Bring", missing > 0 ? Theme.GOLD : Theme.MUTED);
			bring.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			guideCard.add(bring);
			for (SlayerGuide.NeedStatus need : guide.getNeeds())
			{
				String state = need.isMet() ? "Have it" : need.isInfoOnly() ? "Tip" : "Missing";
				Color colour = need.isMet() ? Theme.SUCCESS : need.isInfoOnly() ? Theme.MUTED : Theme.GOLD;
				JPanel row = new JPanel(new BorderLayout(6, 0));
				row.setBackground(Theme.HEADER);
				row.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
				row.add(Theme.label("<html><div style='width:95px'>" + need.getLabel() + "</div></html>", Theme.TEXT),
					BorderLayout.CENTER);
				row.add(Theme.boldLabel(state, colour), BorderLayout.EAST);
				row.setToolTipText(need.isMet() ? "Worn or in your inventory (or unlocked as a Slayer reward)"
					: need.isInfoOnly() ? "Recommended. There are too many alternatives to check, so this is just a reminder."
					: "Not worn or in your inventory");
				guideCard.add(row);
			}
		}
	}

	/**
	 * The likeliest next tasks from your master, with right-click to mark one as blocked.
	 */
	private void updateOdds(TaskOdds odds, List<TaskStats> history)
	{
		oddsCard.removeAll();
		if (odds.getMaster() == null || odds.getOptions().isEmpty())
		{
			oddsCard.setVisible(false);
			return;
		}
		oddsCard.setVisible(true);
		oddsCard.add(title("Next task (" + odds.getMaster() + ")"));

		int shown = 0;
		JPanel blockedList = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		blockedList.setOpaque(false);
		for (TaskOdds.Option o : odds.getOptions())
		{
			if (o.isBlocked())
			{
				JPanel row = row(o.getTask(), "Blocked", Theme.SUBTLE, "Right-click to unblock");
				row.setComponentPopupMenu(blockMenu(o.getTask(), false));
				blockedList.add(row);
				continue;
			}
			if (o.getChance() <= 0 || shown >= SHOWN_ODDS)
			{
				continue;
			}
			shown++;
			String size = o.getMaxAmount() > 0 ? o.getMinAmount() + "-" + o.getMaxAmount() : "";
			JPanel row = row(o.getTask(), String.format(Locale.US, "%.1f%%", o.getChance() * 100), Theme.TEXT,
				"<html>" + o.getTask() + ": " + String.format(Locale.US, "%.1f%%", o.getChance() * 100) + " chance"
					+ (size.isEmpty() ? "" : "<br>Usually " + size + " kills")
					+ "<br>Right-click to mark it as blocked</html>");
			row.setComponentPopupMenu(blockMenu(o.getTask(), true));
			oddsCard.add(row);
		}

		TaskStats advice = odds.blockAdvice(history, BLOCK_ADVICE_MIN_CHANCE);
		if (advice != null)
		{
			TaskOdds.Option o = odds.find(advice.getTask());
			oddsCard.add(wrapped("Block or skip idea: " + advice.getTask() + " (" + String.format(Locale.US, "%.1f%%", o.getChance() * 100)
				+ " chance) is your slowest task at " + Formatting.compactXp(Math.round(advice.getXpPerHour())) + " Slayer xp/hr.",
				Theme.GOLD));
		}
		if (blockedList.getComponentCount() > 0)
		{
			JLabel heading = Theme.label("Blocked by you", Theme.MUTED);
			heading.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			oddsCard.add(heading);
			oddsCard.add(blockedList);
		}
		oddsCard.add(wrapped("From your master's task weights, leaving out tasks you can't get yet. Quest requirements "
			+ "aren't in the game data, so it's an estimate. Right-click a task to mark it as blocked."
			+ (odds.isUnlocksKnown() ? "" : " Your Slayer reward unlocks couldn't be read, so tasks that need one are left out."),
			Theme.SUBTLE));
	}

	private JPopupMenu blockMenu(String task, boolean block)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem item = new JMenuItem(block ? "Mark " + task + " as blocked" : "Unblock " + task);
		item.addActionListener(e -> plugin.setTaskBlocked(task, block));
		menu.add(item);
		return menu;
	}

	/**
	 * Your tasks ranked by your own Slayer XP/hr and GP/hr.
	 */
	private void updateRanking(List<TaskStats> history)
	{
		rankCard.removeAll();
		List<TaskStats> byXp = TaskStats.ranked(history, false);
		if (byXp.size() < 2)
		{
			rankCard.setVisible(false);
			return;
		}
		rankCard.setVisible(true);
		rankCard.add(title("Your tasks"));
		rankCard.add(Theme.label("Fastest Slayer XP", Theme.MUTED));
		for (int i = 0; i < Math.min(3, byXp.size()); i++)
		{
			rankCard.add(rankRow(byXp.get(i), Formatting.compactXp(Math.round(byXp.get(i).getXpPerHour())) + " xp/hr", Theme.SUCCESS));
		}
		List<TaskStats> byGp = TaskStats.ranked(history, true);
		JLabel gp = Theme.label("Most GP", Theme.MUTED);
		gp.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		rankCard.add(gp);
		for (int i = 0; i < Math.min(3, byGp.size()); i++)
		{
			rankCard.add(rankRow(byGp.get(i), Formatting.compactXp(Math.round(byGp.get(i).getGpPerHour())) + " gp/hr", Theme.GOLD));
		}
		if (byXp.size() >= 4)
		{
			JLabel slow = Theme.label("Slowest (block or skip?)", Theme.MUTED);
			slow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			rankCard.add(slow);
			for (int i = byXp.size() - 1; i >= Math.max(3, byXp.size() - 3); i--)
			{
				rankCard.add(rankRow(byXp.get(i), Formatting.compactXp(Math.round(byXp.get(i).getXpPerHour())) + " xp/hr", Theme.MUTED));
			}
		}
		rankCard.add(wrapped("From your task log. Tasks need at least " + TaskStats.MIN_ACTIVE_MILLIS / 60_000
			+ " minutes of fighting to be ranked.", Theme.SUBTLE));
	}

	private static JPanel rankRow(TaskStats s, String value, Color colour)
	{
		return row(s.getTask(), value, colour, "<html>" + s.getTask() + ": done " + s.getTimes() + (s.getTimes() == 1 ? " time" : " times")
			+ "<br>Average " + s.getAverageKills() + " kills<br>"
			+ Formatting.compactXp(Math.round(s.getXpPerHour())) + " Slayer xp/hr, "
			+ Formatting.compactXp(Math.round(s.getGpPerHour())) + " gp/hr</html>");
	}

	private void updatePoints(SlayerStatus status)
	{
		pointsCard.removeAll();
		pointsCard.add(title("Points"));
		SlayerPoints.Master master = status.getMaster();
		int streak = status.getStreak();
		if (master == null || streak < 0)
		{
			pointsCard.add(wrapped("Log in and get a task to see what your next tasks are worth.", Theme.SUBTLE));
			return;
		}

		// The task you're on (or your next one) is streak + 1.
		int next = streak + 1;
		int nextPoints = SlayerPoints.pointsFor(master, next, status.hasEliteDiary(master));
		String nextText = nextPoints < 0 ? "Varies" : nextPoints == 0 ? "None" : Formatting.withCommas(nextPoints) + " pts";
		String nextTip = next < 5 ? "No points for the first four tasks of a streak"
			: nextPoints < 0 ? "Mortimer's points depend on the task" : "Points for finishing it with " + master.getDisplayName();
		pointsCard.add(row("Task #" + Formatting.withCommas(next) + " (" + master.getDisplayName() + ")", nextText,
			Theme.GOLD, nextTip));

		int tenth = SlayerPoints.nextBonusTask(next, 5);
		int big = SlayerPoints.nextBonusTask(next, 15);
		if (tenth < big)
		{
			pointsCard.add(row("Bonus x5", "#" + Formatting.withCommas(tenth) + away(tenth - next), Theme.TEXT,
				"Every 10th task gives 5x the points"));
		}
		pointsCard.add(row("Bonus x" + SlayerPoints.multiplier(big), "#" + Formatting.withCommas(big) + away(big - next),
			Theme.TEXT, "Every 50th, 100th, 250th and 1,000th task gives 15x to 50x the points. "
				+ "Below: what each master gives for it."));

		// What that big task is worth with each master, so it can be saved for the best one.
		JPanel masters = new JPanel(new DynamicGridLayout(0, 2, 4, 2));
		masters.setOpaque(false);
		for (SlayerPoints.Master m : SlayerPoints.BONUS_MASTERS)
		{
			int pts = SlayerPoints.pointsFor(m, big, status.hasEliteDiary(m));
			boolean current = m == master;
			JLabel label = Theme.label(m.getDisplayName() + " " + Formatting.withCommas(pts),
				current ? Theme.GOLD : Theme.MUTED);
			label.setToolTipText(m.getDisplayName() + " would give " + Formatting.withCommas(pts) + " points for task #"
				+ Formatting.withCommas(big) + (current ? " (your current master)" : ""));
			masters.add(label);
		}
		masters.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
		pointsCard.add(masters);
	}

	private static String away(int tasksAway)
	{
		return tasksAway <= 0 ? ", this one" : ", " + tasksAway + " away";
	}

	private void updateSuperiors(List<SlayerTaskRecord> tasks, int taskSuperiors, int taskKills, boolean onTask)
	{
		superiorCard.removeAll();
		superiorCard.add(title("Superiors"));
		StringBuilder rates = new StringBuilder();
		if (onTask)
		{
			superiorCard.add(row("This task", String.valueOf(taskSuperiors), taskSuperiors > 0 ? Theme.GOLD : Theme.TEXT,
				"Superiors that appeared during this task"));
			double rate = SlayerLog.killsPerSuperior(taskKills, taskSuperiors);
			if (rate > 0)
			{
				rates.append("This task: 1 per ").append(Math.round(rate)).append(" kills. ");
			}
		}
		int kills = 0;
		int superiors = 0;
		for (SlayerTaskRecord r : tasks)
		{
			kills += r.kills;
			superiors += r.superiors;
		}
		superiorCard.add(row("All logged tasks", Formatting.withCommas(superiors), Theme.TEXT, "Across every task in your log"));
		double rate = SlayerLog.killsPerSuperior(kills, superiors);
		if (rate > 0)
		{
			rates.append("Logged: 1 per ").append(Math.round(rate)).append(" kills. ");
		}
		superiorCard.add(wrapped(rates + "The chance is 1 in " + SuperiorMonsters.BASE_RATE
			+ " per task kill with Bigger and Badder (1 in 150 with the elite combat achievements).", Theme.SUBTLE));
	}

	private void updateLog(List<SlayerTaskRecord> tasks, LootPrice price)
	{
		logList.removeAll();
		logTitle.setText("Task log (" + tasks.size() + ")");
		long xp = 0;
		long loot = 0;
		int kills = 0;
		for (SlayerTaskRecord r : tasks)
		{
			xp += r.slayerXp;
			loot += r.loot(price);
			kills += r.kills;
		}
		logTotals.setText(tasks.isEmpty() ? "" : Formatting.withCommas(kills) + " kills, " + Formatting.compactXp(xp)
			+ " Slayer xp, " + Formatting.compactXp(loot) + " gp");

		int shown = 0;
		for (SlayerTaskRecord r : tasks)
		{
			if (shown++ >= SHOWN_TASKS)
			{
				break;
			}
			logList.add(taskCard(r, price));
		}
		emptyLog.setVisible(tasks.isEmpty());
		clearLog.setVisible(!tasks.isEmpty());
	}

	private JPanel taskCard(SlayerTaskRecord r, LootPrice price)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.SUCCESS),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Theme.boldLabel(r.task, Theme.TEXT), BorderLayout.CENTER);
		top.add(Theme.label(DATE.format(Instant.ofEpochMilli(r.endMillis).atZone(ZoneId.systemDefault())), Theme.SUBTLE),
			BorderLayout.EAST);
		card.add(top);

		String time = r.activeMillis > 0 ? ", " + Formatting.duration(r.activeMillis / 3_600_000.0) : "";
		card.add(Theme.label(Formatting.withCommas(r.kills) + (r.kills == 1 ? " kill" : " kills") + time
			+ (r.slayerXp > 0 ? ", +" + Formatting.compactXp(r.slayerXp) + " xp" : ""), Theme.MUTED));

		StringBuilder extra = new StringBuilder();
		long loot = r.loot(price);
		if (loot > 0)
		{
			extra.append(Formatting.compactXp(loot)).append(" gp");
		}
		if (r.superiors > 0)
		{
			append(extra, r.superiors + (r.superiors == 1 ? " superior" : " superiors"));
		}
		if (r.points > 0)
		{
			append(extra, "+" + r.points + " pts");
		}
		if (extra.length() > 0)
		{
			card.add(Theme.label(extra.toString(), loot > 0 ? Theme.GOLD : Theme.SUBTLE));
		}

		card.setToolTipText(tooltip(r, price));
		JPopupMenu menu = new JPopupMenu();
		JMenuItem remove = new JMenuItem("Remove from log");
		remove.addActionListener(e -> plugin.removeTaskRecord(r));
		menu.add(remove);
		card.setComponentPopupMenu(menu);
		return card;
	}

	private static String tooltip(SlayerTaskRecord r, LootPrice price)
	{
		StringBuilder sb = new StringBuilder("<html><b>").append(r.task).append("</b>");
		if (r.master != null)
		{
			sb.append(" from ").append(r.master);
		}
		if (r.location != null)
		{
			sb.append(" in ").append(r.location);
		}
		if (r.assigned > 0)
		{
			sb.append("<br>Assigned: ").append(Formatting.withCommas(r.assigned));
		}
		double xpHr = r.slayerXpPerHour();
		if (xpHr > 0)
		{
			sb.append("<br>Slayer XP/hr: ").append(Formatting.compactXp(Math.round(xpHr)));
		}
		if (r.combatXp > 0)
		{
			sb.append("<br>Combat XP: ").append(Formatting.withCommas(r.combatXp));
		}
		double gpHr = r.gpPerHour(price);
		if (gpHr > 0)
		{
			sb.append("<br>GP/hr: ").append(Formatting.compactXp(Math.round(gpHr)));
		}
		if (r.biggestHit >= 0)
		{
			sb.append("<br>Biggest hit: ").append(r.biggestHit);
		}
		return sb.append("<br><br>Right-click to remove</html>").toString();
	}

	private void confirmClear()
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete every task in the log for this account?\nThis can't be undone.",
			"Clear task log", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			plugin.clearSlayerLog();
		}
	}

	// ---- Building blocks -----------------------------------------------------------------

	private static void append(StringBuilder sb, String part)
	{
		if (sb.length() > 0)
		{
			sb.append(", ");
		}
		sb.append(part);
	}

	private static JPanel card()
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createEmptyBorder(7, 8, 8, 8));
		return card;
	}

	private static JLabel title(String text)
	{
		JLabel label = Theme.boldLabel(text, Theme.TEXT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		return label;
	}

	private static JPanel row(String label, String value, Color valueColour, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.HEADER);
		row.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
		row.add(Theme.label(label, Theme.MUTED), BorderLayout.CENTER);
		row.add(Theme.boldLabel(value, valueColour), BorderLayout.EAST);
		row.setToolTipText(tooltip);
		return row;
	}

	private static JLabel wrapped(String text, Color colour)
	{
		JLabel label = Theme.label("<html><div style='width:135px'>" + text + "</div></html>", colour);
		label.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
		return label;
	}

	private static JPanel holder(java.awt.Component child)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(child, BorderLayout.CENTER);
		return holder;
	}
}
