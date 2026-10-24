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
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The Slayer tab: your task, the task guide, points, what the next tasks are likely to be,
 * who gives a task, superiors, your best and worst tasks, and the log of finished tasks.
 * 2.2 draws them as rounded cards with bars, tags and strips. Swing thread only.
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
	private static final Color BAR_GREY = new Color(130, 130, 130);

	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;

	private final UiKit.RoundPanel statusCard = card();
	private final UiKit.RoundPanel guideCard = card();
	private final UiKit.RoundPanel oddsCard = card();
	private final UiKit.RoundPanel sourcesCard = card();
	private final UiKit.RoundPanel rankCard = card();
	private final UiKit.RoundPanel pointsCard = card();
	private final UiKit.RoundPanel superiorCard = card();
	private final UiKit.RoundPanel logCard = card();
	private final JLabel logTitle = Theme.boldLabel("", Theme.TEXT);
	private final JLabel logTotals = Theme.label("", Theme.SUBTLE);
	private final JPanel logList = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
	private final JLabel emptyLog = new JLabel();
	private final JLabel clearLog;
	private String viewKey;
	// Which list "Your tasks" shows: 0 fastest XP, 1 most GP, 2 slowest.
	private int rankTab;
	private List<TaskStats> lastHistory = new ArrayList<>();

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

		clearLog = Theme.cardButton("Clear task log", "Delete every task in the log for this account", this::confirmClear);

		JPanel logHeader = new JPanel(new BorderLayout(0, 1));
		logHeader.setOpaque(false);
		logHeader.add(logTitle, BorderLayout.NORTH);
		logHeader.add(logTotals, BorderLayout.SOUTH);
		logCard.add(logHeader);
		logCard.add(holder(emptyLog));
		logCard.add(logList);
		logCard.add(holder(clearLog));

		add(statusCard);
		add(holder(guideCard));
		add(pointsCard);
		add(holder(oddsCard));
		add(holder(sourcesCard));
		add(superiorCard);
		add(holder(rankCard));
		add(logCard);
	}

	void update()
	{
		SlayerStatus status = plugin.getSlayerStatus();
		int taskSuperiors = plugin.getCurrentTaskSuperiors();
		int taskKills = plugin.getCurrentTaskKills();
		LootPrice price = config.lootPrice();
		SlayerGuide guide = plugin.getSlayerGuide();
		TaskOdds odds = plugin.getTaskOdds();
		TaskSources sources = plugin.getTaskSources();
		String taskArea = plugin.getTaskArea();
		int potions = plugin.getTaskPrayerPotions(status.getTask(), status.getRemaining());
		String key = status + "|" + plugin.getSlayerLogRevision() + "|" + taskSuperiors + "|" + taskKills + "|" + price
			+ "|" + guide + "|" + odds + "|" + sources + "|" + taskArea + "|" + plugin.getAreaRevision() + "|" + potions
			+ "|" + plugin.getMasterOddsKey() + "|" + plugin.getRateTick() + "|" + rankTab;
		if (key.equals(viewKey))
		{
			return;
		}
		viewKey = key;

		List<SlayerTaskRecord> tasks = plugin.getSlayerTasks();
		List<TaskStats> history = TaskStats.group(tasks, price);
		TaskForecast forecast = plugin.getTaskRunForecast();
		updateStatus(status, forecast, taskSuperiors);
		updateGuide(guide, tasks, price, taskArea, potions, status.getRemaining(), forecast);
		updatePoints(status);
		updateOdds(odds, history, status.getMaster(), status.getTask());
		updateSources(sources);
		updateSuperiors(tasks, taskSuperiors, taskKills, status.getTask() != null);
		updateRanking(history);
		updateLog(tasks, price);
		revalidate();
		repaint();
	}

	// ---- Sections ----------------------------------------------------------------------

	/**
	 * WHAT: The task card: the task, master and streak, a bar of kills done, and GP/hr, points
	 * and superiors. With no task: master, points and streak.
	 * WHY: The most-checked facts sit at the top, readable at a glance.
	 * PRE: Swing thread.
	 * POST: The card is rebuilt.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void updateStatus(SlayerStatus status, TaskForecast forecast, int taskSuperiors)
	{
		statusCard.removeAll();
		SlayerPoints.Master master = status.getMaster();
		String streakText = status.getStreak() < 0 ? "-" : Formatting.withCommas(status.getStreak())
			+ (status.getStreak() == 1 ? " task" : " tasks");
		String pointsText = status.getPoints() < 0 ? "-" : Formatting.withCommas(status.getPoints());
		if (status.getTask() == null)
		{
			statusCard.outline(null);
			statusCard.add(title("Slayer"));
			statusCard.add(row("Master", master == null ? "-" : master.getDisplayName(), Theme.TEXT, null));
			statusCard.add(row("Points", pointsText, Theme.GOLD, null));
			statusCard.add(row("Streak", streakText, Theme.TEXT,
				"Tasks completed in a row. Krystilia and Mortimer keep their own streaks."));
			statusCard.add(row("Task", "None", Theme.MUTED, null));
			return;
		}

		statusCard.outline(Theme.SUCCESS);
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Theme.boldLabel(status.getTask(), Theme.TEXT), BorderLayout.CENTER);
		head.add(Theme.label("● on task", Theme.SUCCESS), BorderLayout.EAST);
		head.setToolTipText("Your current task. Right-click to see where else to get it.");
		head.setComponentPopupMenu(findMenu(status.getTask()));
		statusCard.add(head);
		statusCard.add(row("Master", (master == null ? "-" : master.getDisplayName()) + "  ·  streak " + streakText,
			Theme.MUTED, "Tasks completed in a row. Krystilia and Mortimer keep their own streaks."));

		// Kills done out of the number assigned, with the time left from your own pace.
		int remaining = status.getRemaining();
		int assigned = status.getAssigned();
		JPanel progress = new JPanel(new BorderLayout(0, 2));
		progress.setOpaque(false);
		JPanel progressText = new JPanel(new BorderLayout(6, 0));
		progressText.setOpaque(false);
		JLabel left = Theme.label(Formatting.withCommas(remaining) + (assigned > 0 ? " of " + Formatting.withCommas(assigned) : "")
			+ " left", Theme.TEXT);
		left.setToolTipText("Kills left out of the number assigned");
		progressText.add(left, BorderLayout.WEST);
		if (forecast != null && forecast.getMillis() > 0)
		{
			JLabel eta = Theme.label("~" + Formatting.duration(forecast.getMillis() / 3_600_000.0) + " to go", Theme.SUCCESS);
			eta.setHorizontalAlignment(SwingConstants.RIGHT);
			eta.setToolTipText("At your own pace on this task, from your task log");
			progressText.add(eta, BorderLayout.CENTER);
		}
		progress.add(progressText, BorderLayout.NORTH);
		if (assigned > 0)
		{
			progress.add(UiKit.bar((assigned - remaining) / (double) assigned, Theme.SUCCESS, 6), BorderLayout.CENTER);
		}
		statusCard.add(progress);

		double gpPerHour = plugin.getTaskGpPerHour();
		boolean away = plugin.isTaskAway();
		JLabel gp = UiKit.value();
		gp.setText(gpPerHour > 0 ? QuantityFormatter.quantityToStackSize(Math.round(gpPerHour)) : "-");
		gp.setForeground(away ? Theme.MUTED : Theme.GOLD);
		gp.setToolTipText("Loot per hour of this task, banking and walking included. Updates every 5 ticks, and falls while you're away.");
		JLabel pts = UiKit.value();
		pts.setText(pointsText);
		pts.setForeground(Theme.GOLD);
		JLabel sup = UiKit.value();
		sup.setText(String.valueOf(taskSuperiors));
		sup.setForeground(taskSuperiors > 0 ? Theme.GOLD : Theme.TEXT);
		sup.setToolTipText("Superiors that appeared during this task");
		statusCard.add(UiKit.stackedStrip(new JLabel[]{gp, pts, sup},
			new JLabel[]{UiKit.caption(away ? "GP/hr (away)" : "GP/hr"), UiKit.caption("Points"), UiKit.caption("Superiors")}));
		if (status.getLocation() != null)
		{
			statusCard.add(row("Location", status.getLocation(), Theme.TEXT, "Konar's tasks must be done here"));
		}
	}

	/**
	 * Where the task can be done and what to bring.
	 */
	private void updateGuide(SlayerGuide guide, List<SlayerTaskRecord> tasks, LootPrice price, String taskArea,
		int potions, int killsLeft, TaskForecast forecast)
	{
		guideCard.removeAll();
		TaskForecast.Cannon cannon = plugin.getTaskCannon();
		if (guide.getTask() == null || (guide.getAreas().isEmpty() && guide.getNeeds().isEmpty() && potions < 0
			&& forecast == null && cannon == null))
		{
			guideCard.setVisible(false);
			return;
		}
		guideCard.setVisible(true);
		guideCard.add(title("Task guide"));

		if (!guide.getAreas().isEmpty())
		{
			guideCard.add(UiKit.sectionHeader("Where to go", null));
			Map<String, TaskStats> rates = TaskStats.byArea(tasks, guide.getTask(), price);
			List<SlayerGuide.Area> areas = sortAreas(guide.getAreas(), rates);
			TaskStats best = bestArea(rates);
			boolean konar = false;
			for (SlayerGuide.Area area : areas)
			{
				konar |= area.isAssigned();
				guideCard.add(areaRow(area, rates.get(area.getName().toLowerCase(Locale.ROOT)), best,
					area.getName().equalsIgnoreCase(taskArea), guide.getAreas().size() > 1));
			}
			if (!konar && taskArea == null && guide.getAreas().size() > 1)
			{
				guideCard.add(wrapped("Right-click the area you're using. The plugin learns the spot, picks it next time, "
					+ "and ranks areas by your own rates.", Theme.SUBTLE));
			}
		}

		if (!guide.getNeeds().isEmpty() || potions >= 0)
		{
			int inBank = guide.inBankCount();
			int missing = guide.missingCount() - inBank;
			List<String> parts = new ArrayList<>();
			if (missing > 0)
			{
				parts.add(missing + " missing");
			}
			if (inBank > 0)
			{
				parts.add(inBank + " in bank");
			}
			JLabel summary = Theme.label(String.join(", ", parts), missing > 0 ? MonsterCard.BAD : Theme.GOLD);
			guideCard.add(UiKit.sectionHeader("Bring", parts.isEmpty() ? null : summary));
			for (SlayerGuide.NeedStatus need : guide.getNeeds())
			{
				guideCard.add(needRow(need, guide.isBankKnown()));
			}
			if (!guide.isBankKnown() && guide.missingCount() > 0 && config.checkBank())
			{
				guideCard.add(wrapped("Open your bank once and the guide will also say what's in it.", Theme.SUBTLE));
			}
			if (potions >= 0)
			{
				guideCard.add(row("Prayer potions", potions == 0 ? "None" : "About " + potions, Theme.TEXT,
					"<html>For the " + Formatting.withCommas(killsLeft) + " kills left, at your own measured prayer drain "
						+ "on this monster.<br>Super restores restore a little more per dose.</html>"));
			}
		}
		if (forecast != null || cannon != null)
		{
			JLabel from = Theme.label(forecast == null ? "your task log"
				: "your last " + (forecast.getRuns() == 1 ? "run" : forecast.getRuns() + " runs"), Theme.SUBTLE);
			guideCard.add(UiKit.sectionHeader("Rest of task", from));
		}
		if (forecast != null)
		{
			String tip = "At your own pace on this task, scaled to the " + Formatting.withCommas(killsLeft) + " kills left";
			guideCard.add(row("Time", "~" + Formatting.duration(forecast.getMillis() / 3_600_000.0), Theme.TEXT, tip));
			guideCard.add(row("Slayer XP", "+" + Formatting.compactXp(forecast.getSlayerXp()), Theme.SUCCESS, tip));
			if (forecast.getLoot() > 0)
			{
				guideCard.add(row("Loot", "~" + Formatting.compactXp(forecast.getLoot()) + " gp", Theme.GOLD, tip));
			}
			if (forecast.getSupplies() > 0)
			{
				guideCard.add(row("Supplies", "~" + Formatting.compactXp(forecast.getSupplies()) + " gp", Theme.TEXT, tip));
			}
		}
		if (cannon != null)
		{
			guideCard.add(row("Cannon XP", cannon.gain(),
				cannon.getXpPerHourWith() > cannon.getXpPerHourWithout() ? Theme.SUCCESS : Theme.MUTED, cannon.tooltip()));
			guideCard.add(row("Cannon cost", cannon.cost(), Theme.TEXT, cannon.tooltip()));
		}
	}

	private static JPanel needRow(SlayerGuide.NeedStatus need, boolean bankKnown)
	{
		if (need.isMet())
		{
			return UiKit.tagRow(need.getLabel(), "Have it", Theme.SUCCESS,
				"Worn or in your inventory (or unlocked as a Slayer reward)");
		}
		if (need.isInfoOnly())
		{
			return UiKit.tagRow(need.getLabel(), "Tip", Theme.MUTED,
				"Recommended. There are too many alternatives to check, so this is just a reminder.");
		}
		if (need.isInBank())
		{
			return UiKit.tagRow(need.getLabel(), "In bank", Theme.GOLD,
				"Not worn or in your inventory, but it was in your bank the last time you opened it");
		}
		return UiKit.tagRow(need.getLabel(), "Missing", MonsterCard.BAD,
			bankKnown ? "Not worn, in your inventory or in your bank" : "Not worn or in your inventory");
	}

	/**
	 * Konar's pick first, then areas you have rates for (best first), then the rest in the
	 * game's order.
	 */
	static List<SlayerGuide.Area> sortAreas(List<SlayerGuide.Area> areas, Map<String, TaskStats> rates)
	{
		List<SlayerGuide.Area> sorted = new ArrayList<>(areas);
		sorted.sort(Comparator.comparing((SlayerGuide.Area a) -> !a.isAssigned())
			.thenComparing(a -> -areaXpPerHour(rates.get(a.getName().toLowerCase(Locale.ROOT)))));
		return sorted;
	}

	private static double areaXpPerHour(TaskStats s)
	{
		return s == null || s.getActiveMillis() <= 0 ? -1 : s.getXpPerHour();
	}

	/**
	 * The area with the best Slayer XP/hr, if at least two have enough time logged to compare.
	 */
	static TaskStats bestArea(Map<String, TaskStats> rates)
	{
		List<TaskStats> ranked = TaskStats.ranked(new ArrayList<>(rates.values()), false);
		return ranked.size() >= 2 ? ranked.get(0) : null;
	}

	private JPanel areaRow(SlayerGuide.Area area, TaskStats rate, TaskStats best, boolean here, boolean canPick)
	{
		boolean isBest = best != null && rate == best;
		boolean green = area.isAssigned() || here;
		UiKit.RoundPanel row = UiKit.box(new DynamicGridLayout(0, 1, 0, 1));
		if (green)
		{
			row.setBackground(Theme.highlight(Theme.SUCCESS, 0.72f));
		}

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Theme.label(area.getName(), green ? Theme.SUCCESS : Theme.TEXT), BorderLayout.CENTER);
		String badge = area.isAssigned() ? "Konar" : here ? "you're here" : isBest ? "your best" : null;
		if (badge != null)
		{
			top.add(Theme.label(badge, isBest && !here && !area.isAssigned() ? Theme.GOLD : Theme.SUCCESS), BorderLayout.EAST);
		}
		row.add(top);

		List<String> tags = new ArrayList<>();
		if (plugin.isMultiArea(area.getName()))
		{
			tags.add("Multi-combat");
		}
		if (plugin.isWildernessArea(area.getName()))
		{
			tags.add("Wilderness");
		}
		if (!tags.isEmpty())
		{
			row.add(Theme.label(String.join(", ", tags), Theme.GOLD));
		}
		if (rate != null && rate.getActiveMillis() > 0)
		{
			row.add(Theme.label("You: " + Formatting.compactXp(Math.round(rate.getXpPerHour())) + " xp/hr, "
				+ Math.round(rate.getKillsPerHour()) + " kills/hr", isBest ? Theme.GOLD : Theme.MUTED));
		}
		if (area.getHint() != null)
		{
			row.add(UiKit.wrappedTo(area.getHint(), Theme.SUBTLE, 150));
		}

		StringBuilder tip = new StringBuilder("<html><b>").append(area.getName()).append("</b>");
		if (rate != null && rate.getActiveMillis() > 0)
		{
			tip.append("<br>Your rates here, from ").append(rate.getTimes()).append(rate.getTimes() == 1 ? " task" : " tasks")
				.append(":<br>").append(Formatting.compactXp(Math.round(rate.getXpPerHour()))).append(" Slayer xp/hr, ")
				.append(Math.round(rate.getKillsPerHour())).append(" kills/hr, ")
				.append(Formatting.compactXp(Math.round(rate.getGpPerHour()))).append(" gp/hr");
		}
		if (canPick && !area.isAssigned())
		{
			tip.append("<br><br>").append(here ? "Right-click if this isn't where you are" : "Right-click if you're doing your task here");
			JPopupMenu menu = new JPopupMenu();
			JMenuItem item = new JMenuItem(here ? "Not doing my task here" : "I'm doing my task here");
			item.addActionListener(e -> plugin.setTaskArea(here ? null : area.getName()));
			menu.add(item);
			row.setComponentPopupMenu(menu);
		}
		row.setToolTipText(tip.append("</html>").toString());
		return row;
	}

	/**
	 * The likeliest next tasks from your master, as lined-up bars, with right-click to mark one
	 * as blocked.
	 */
	private void updateOdds(TaskOdds odds, List<TaskStats> history, SlayerPoints.Master current, String currentTask)
	{
		oddsCard.removeAll();
		if (odds.getMaster() == null || odds.getOptions().isEmpty())
		{
			oddsCard.setVisible(false);
			return;
		}
		oddsCard.setVisible(true);
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(title("Next task"), BorderLayout.WEST);
		JLabel master = Theme.label(odds.getMaster().toString(), Theme.MUTED);
		master.setHorizontalAlignment(SwingConstants.RIGHT);
		head.add(master, BorderLayout.CENTER);
		oddsCard.add(head);

		TaskStats advice = odds.blockAdvice(history, BLOCK_ADVICE_MIN_CHANCE);
		double top = 0;
		for (TaskOdds.Option o : odds.getOptions())
		{
			if (!o.isBlocked())
			{
				top = Math.max(top, o.getChance());
			}
		}

		int shown = 0;
		int more = 0;
		JPanel blockedList = new JPanel(new GridLayout(0, 2, 3, 3));
		blockedList.setOpaque(false);
		for (TaskOdds.Option o : odds.getOptions())
		{
			if (o.isBlocked())
			{
				UiKit.RoundPanel chip = UiKit.box(new BorderLayout());
				chip.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
				chip.add(Theme.label(o.getTask(), Theme.MUTED), BorderLayout.CENTER);
				chip.setToolTipText("Blocked. Right-click to unblock.");
				chip.setComponentPopupMenu(blockMenu(o.getTask(), false));
				blockedList.add(chip);
				continue;
			}
			if (o.getChance() <= 0)
			{
				continue;
			}
			if (shown >= SHOWN_ODDS)
			{
				more++;
				continue;
			}
			shown++;
			boolean onTask = o.getTask().equalsIgnoreCase(currentTask);
			boolean blockIdea = advice != null && o.getTask().equalsIgnoreCase(advice.getTask());
			Color colour = onTask ? Theme.SUCCESS : blockIdea ? MonsterCard.BAD : BAR_GREY;
			String size = o.getMaxAmount() > 0 ? o.getMinAmount() + "-" + o.getMaxAmount() : "";
			JPanel row = UiKit.barRow(o.getTask(), top <= 0 ? 0 : o.getChance() / top,
				String.format(Locale.US, "%.1f%%", o.getChance() * 100), colour, onTask || blockIdea);
			row.setToolTipText("<html>" + o.getTask() + ": " + String.format(Locale.US, "%.1f%%", o.getChance() * 100) + " chance"
				+ (size.isEmpty() ? "" : "<br>Usually " + size + " kills")
				+ "<br>Right-click to mark it as blocked</html>");
			row.setComponentPopupMenu(blockMenu(o.getTask(), true));
			oddsCard.add(row);
		}
		if (more > 0)
		{
			oddsCard.add(Theme.label("+ " + more + " less likely", Theme.SUBTLE));
		}

		if (advice != null)
		{
			TaskOdds.Option o = odds.find(advice.getTask());
			oddsCard.add(note("<font color='" + hex(Theme.GOLD) + "'>Block or skip idea:</font> " + advice.getTask()
				+ " (" + String.format(Locale.US, "%.1f%%", o.getChance() * 100) + " chance) is your slowest task at "
				+ Formatting.compactXp(Math.round(advice.getXpPerHour())) + " Slayer xp/hr.", Theme.GOLD, null));
		}
		MasterAdvice.Entry better = MasterAdvice.betterThan(plugin.getMasterAdvice(history, MasterAdvice.Goal.XP), current,
			MasterAdvice.MIN_TIP_GAIN);
		if (better != null)
		{
			MasterAdvice.Entry yours = MasterAdvice.find(plugin.getMasterAdvice(history, MasterAdvice.Goal.XP), current);
			long gain = Math.round((better.getXpPerHour() / yours.getXpPerHour() - 1) * 100);
			oddsCard.add(note("<font color='" + hex(Theme.SUCCESS) + "'>Tip:</font> " + better.getMaster().getDisplayName()
					+ " would be about " + gain + "% more Slayer XP/hr for you, from your task log.", Theme.SUCCESS,
				"<html>Your expected Slayer XP/hr, from each master's task chances and your own rate on each task:<br>"
					+ better.getMaster().getDisplayName() + ": " + Formatting.compactXp(Math.round(better.getXpPerHour())) + " xp/hr<br>"
					+ yours.getMaster().getDisplayName() + ": " + Formatting.compactXp(Math.round(yours.getXpPerHour())) + " xp/hr<br>"
					+ "Tasks you haven't logged count at your average rate.</html>"));
		}
		oddsCard.add(holder(Theme.cardButton("Where to get a task...", "See which master is likeliest to give you a task",
			this::pickTaskToFind)));
		if (blockedList.getComponentCount() > 0)
		{
			JLabel count = Theme.label(String.valueOf(blockedList.getComponentCount()), Theme.SUBTLE);
			oddsCard.add(UiKit.sectionHeader("Blocked by you", count));
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
		menu.add(findItem(task));
		return menu;
	}

	private JMenuItem findItem(String task)
	{
		JMenuItem find = new JMenuItem("Where to get " + task);
		find.addActionListener(e -> plugin.showTaskSources(task));
		return find;
	}

	private JPopupMenu findMenu(String task)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.add(findItem(task));
		return menu;
	}

	/**
	 * Asks which task to look up.
	 */
	private void pickTaskToFind()
	{
		List<String> names = plugin.getAllTaskNames();
		if (names.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Log in first, so the task list can be read from the game.",
				"Where to get a task", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Object choice = JOptionPane.showInputDialog(this, "Which task do you want?", "Where to get a task",
			JOptionPane.QUESTION_MESSAGE, null, names.toArray(), names.get(0));
		if (choice != null)
		{
			plugin.showTaskSources(choice.toString());
		}
	}

	/**
	 * Every master's chance of giving the task the player looked up, as bars.
	 */
	private void updateSources(TaskSources sources)
	{
		sourcesCard.removeAll();
		if (sources.getTask() == null)
		{
			sourcesCard.setVisible(false);
			return;
		}
		sourcesCard.setVisible(true);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(title("Where to get " + sources.getTask()), BorderLayout.CENTER);
		JLabel close = Theme.boldLabel("x", Theme.MUTED);
		close.setToolTipText("Close");
		close.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		close.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				plugin.showTaskSources(null);
			}
		});
		top.add(close, BorderLayout.EAST);
		sourcesCard.add(top);

		if (sources.getSources().isEmpty())
		{
			sourcesCard.add(wrapped("No slayer master gives this task.", Theme.MUTED));
			return;
		}
		TaskSources.Source best = sources.best();
		double topChance = best == null ? 0 : best.getChance();
		for (TaskSources.Source src : sources.getSources())
		{
			String name = src.getMaster().getDisplayName();
			String size = src.getMaxAmount() > 0 ? src.getMinAmount() + "-" + src.getMaxAmount() + " kills" : "";
			if (src.getUnavailable() != null || src.getChance() <= 0)
			{
				String why = src.getUnavailable() == null ? "Can't give it now" : src.getUnavailable();
				sourcesCard.add(row(name, "-", Theme.SUBTLE, why));
				continue;
			}
			String pct = String.format(Locale.US, "%.1f%%", src.getChance() * 100);
			JPanel row = UiKit.barRow(name + (src == best ? " (best)" : ""), topChance <= 0 ? 0 : src.getChance() / topChance,
				pct, src == best ? Theme.SUCCESS : BAR_GREY, src == best);
			row.setToolTipText("<html>" + name + ": " + pct + " chance per task" + (size.isEmpty() ? "" : "<br>Usually " + size)
				+ "<br>About 1 in " + Math.max(1, Math.round(1 / src.getChance())) + " tasks</html>");
			sourcesCard.add(row);
		}
		sourcesCard.add(wrapped("From each master's task weights with your levels, unlocks and blocked tasks. "
			+ "Quest requirements aren't in the game data, so it's an estimate.", Theme.SUBTLE));
	}

	/**
	 * Your tasks ranked by your own Slayer XP/hr and GP/hr, one list at a time behind small tabs.
	 */
	private void updateRanking(List<TaskStats> history)
	{
		lastHistory = history;
		rankCard.removeAll();
		List<TaskStats> byXp = TaskStats.ranked(history, false);
		if (byXp.size() < 2)
		{
			rankCard.setVisible(false);
			return;
		}
		rankCard.setVisible(true);
		rankCard.add(title("Your tasks"));

		boolean slowest = byXp.size() >= 4;
		if (!slowest && rankTab == 2)
		{
			rankTab = 0;
		}
		String[] names = slowest ? new String[]{"Fastest XP", "Most GP", "Slowest"} : new String[]{"Fastest XP", "Most GP"};
		JPanel tabs = new JPanel(new GridLayout(1, names.length, 3, 0));
		tabs.setOpaque(false);
		for (int i = 0; i < names.length; i++)
		{
			int index = i;
			JLabel tab = UiKit.tab(names[i], null, () ->
			{
				rankTab = index;
				viewKey = null;
				update();
			});
			tab.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
			UiKit.select(tab, i == rankTab);
			if (i != rankTab)
			{
				tab.setBackground(Theme.SLOT);
			}
			tabs.add(tab);
		}
		rankCard.add(tabs);

		if (rankTab == 1)
		{
			List<TaskStats> byGp = TaskStats.ranked(history, true);
			double top = byGp.isEmpty() ? 0 : byGp.get(0).getGpPerHour();
			for (int i = 0; i < Math.min(5, byGp.size()); i++)
			{
				TaskStats s = byGp.get(i);
				rankCard.add(rankRow(s, i + 1, top <= 0 ? 0 : s.getGpPerHour() / top,
					Formatting.compactXp(Math.round(s.getGpPerHour())), Theme.GOLD, i == 0));
			}
		}
		else if (rankTab == 2)
		{
			double top = byXp.get(0).getXpPerHour();
			for (int i = byXp.size() - 1, n = 1; i >= Math.max(3, byXp.size() - 5); i--, n++)
			{
				TaskStats s = byXp.get(i);
				rankCard.add(rankRow(s, n, top <= 0 ? 0 : s.getXpPerHour() / top,
					Formatting.compactXp(Math.round(s.getXpPerHour())), MonsterCard.BAD, n == 1));
			}
		}
		else
		{
			double top = byXp.get(0).getXpPerHour();
			for (int i = 0; i < Math.min(5, byXp.size()); i++)
			{
				TaskStats s = byXp.get(i);
				rankCard.add(rankRow(s, i + 1, top <= 0 ? 0 : s.getXpPerHour() / top,
					Formatting.compactXp(Math.round(s.getXpPerHour())), Theme.SUCCESS, i == 0));
			}
		}
		rankCard.add(wrapped((rankTab == 1 ? "GP/hr" : "Slayer XP/hr") + " from your task log. Tasks need at least "
			+ TaskStats.MIN_ACTIVE_MILLIS / 60_000 + " minutes of fighting to be ranked.", Theme.SUBTLE));
	}

	private JPanel rankRow(TaskStats s, int place, double fill, String value, Color colour, boolean first)
	{
		JPanel row = UiKit.barRow(place + ". " + s.getTask(), fill, value, first ? colour : BAR_GREY, first);
		row.setToolTipText("<html>" + s.getTask() + ": done " + s.getTimes() + (s.getTimes() == 1 ? " time" : " times")
			+ "<br>Average " + s.getAverageKills() + " kills<br>"
			+ Formatting.compactXp(Math.round(s.getXpPerHour())) + " Slayer xp/hr, "
			+ Formatting.compactXp(Math.round(s.getGpPerHour())) + " gp/hr</html>");
		row.setComponentPopupMenu(findMenu(s.getTask()));
		return row;
	}

	private void updatePoints(SlayerStatus status)
	{
		pointsCard.removeAll();
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(title("Points"), BorderLayout.WEST);
		JLabel total = Theme.boldLabel(status.getPoints() < 0 ? "" : Formatting.withCommas(status.getPoints()), Theme.GOLD);
		total.setHorizontalAlignment(SwingConstants.RIGHT);
		head.add(total, BorderLayout.CENTER);
		pointsCard.add(head);
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
		String nextText = nextPoints < 0 ? "Varies" : nextPoints == 0 ? "None" : "+" + Formatting.withCommas(nextPoints) + " pts";
		String nextTip = next < 5 ? "No points for the first four tasks of a streak"
			: nextPoints < 0 ? "Mortimer's points depend on the task" : "Points for finishing it with " + master.getDisplayName();
		pointsCard.add(row("Task #" + Formatting.withCommas(next) + " (" + master.getDisplayName() + ")", nextText,
			Theme.GOLD, nextTip));

		int tenth = SlayerPoints.nextBonusTask(next, 5);
		int big = SlayerPoints.nextBonusTask(next, 15);
		if (tenth < big)
		{
			pointsCard.add(row("Task #" + Formatting.withCommas(tenth) + " (x5)", away(tenth - next), Theme.TEXT,
				"Every 10th task gives 5x the points"));
		}
		pointsCard.add(row("Task #" + Formatting.withCommas(big) + " (x" + SlayerPoints.multiplier(big) + ")",
			away(big - next), Theme.GOLD, "Every 50th, 100th, 250th and 1,000th task gives 15x to 50x the points. "
				+ "Below: what each master gives for it."));
		// How far through the 50 tasks to that big task.
		pointsCard.add(UiKit.bar(1 - Math.min(50, big - next) / 50.0, Theme.GOLD, 4));

		// What that big task is worth with each master, so it can be saved for the best one.
		pointsCard.add(Theme.label("Task #" + Formatting.withCommas(big) + " with each master", Theme.MUTED));
		JPanel masters = new JPanel(new GridLayout(0, 2, 3, 3));
		masters.setOpaque(false);
		for (SlayerPoints.Master m : SlayerPoints.BONUS_MASTERS)
		{
			int pts = SlayerPoints.pointsFor(m, big, status.hasEliteDiary(m));
			boolean current = m == master;
			UiKit.RoundPanel cell = UiKit.box(new BorderLayout(4, 0));
			cell.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
			if (current)
			{
				cell.setBackground(Theme.highlight(Theme.GOLD, 0.78f));
			}
			cell.add(Theme.label(m.getDisplayName(), current ? Theme.GOLD : Theme.MUTED), BorderLayout.WEST);
			JLabel v = Theme.label(Formatting.withCommas(pts), current ? Theme.GOLD : Theme.TEXT);
			v.setHorizontalAlignment(SwingConstants.RIGHT);
			cell.add(v, BorderLayout.CENTER);
			cell.setToolTipText(m.getDisplayName() + " would give " + Formatting.withCommas(pts) + " points for task #"
				+ Formatting.withCommas(big) + (current ? " (your current master)" : ""));
			masters.add(cell);
		}
		pointsCard.add(masters);
	}

	private static String away(int tasksAway)
	{
		return tasksAway <= 0 ? "This one" : tasksAway == 1 ? "Next task" : "In " + tasksAway + " tasks";
	}

	private void updateSuperiors(List<SlayerTaskRecord> tasks, int taskSuperiors, int taskKills, boolean onTask)
	{
		superiorCard.removeAll();
		superiorCard.add(title("Superiors"));
		StringBuilder rates = new StringBuilder();
		int kills = 0;
		int superiors = 0;
		for (SlayerTaskRecord r : tasks)
		{
			kills += r.kills;
			superiors += r.superiors;
		}
		List<JLabel> values = new ArrayList<>();
		List<JLabel> captions = new ArrayList<>();
		if (onTask)
		{
			JLabel now = UiKit.value();
			now.setText(String.valueOf(taskSuperiors));
			now.setForeground(taskSuperiors > 0 ? Theme.GOLD : Theme.TEXT);
			now.setToolTipText("Superiors that appeared during this task");
			values.add(now);
			captions.add(UiKit.caption("This task"));
			double rate = SlayerLog.killsPerSuperior(taskKills, taskSuperiors);
			if (rate > 0)
			{
				rates.append("This task: 1 per ").append(Math.round(rate)).append(" kills. ");
			}
		}
		JLabel all = UiKit.value();
		all.setText(Formatting.withCommas(superiors));
		all.setToolTipText("Across every task in your log");
		values.add(all);
		captions.add(UiKit.caption("All tasks"));
		JLabel chance = UiKit.value();
		chance.setText("1/" + SuperiorMonsters.BASE_RATE);
		chance.setForeground(Theme.MUTED);
		chance.setToolTipText("Per task kill with Bigger and Badder");
		values.add(chance);
		captions.add(UiKit.caption("Chance"));
		superiorCard.add(UiKit.stackedStrip(values.toArray(new JLabel[0]), captions.toArray(new JLabel[0])));
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
		logTotals.setText(tasks.isEmpty() ? "" : "<html><div style='width:148px'>" + Formatting.withCommas(kills) + " kills, "
			+ Formatting.compactXp(xp) + " Slayer xp, " + Formatting.compactXp(loot) + " gp</div></html>");

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

	/**
	 * One finished task: name and loot, then kills, time and XP with the date, then extras.
	 */
	private JPanel taskCard(SlayerTaskRecord r, LootPrice price)
	{
		UiKit.RoundPanel card = UiKit.box(new DynamicGridLayout(0, 1, 0, 1));
		long loot = r.loot(price);

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Theme.label(r.task, Theme.TEXT), BorderLayout.CENTER);
		if (loot > 0)
		{
			top.add(Theme.label("+" + Formatting.compactXp(loot) + " gp", Theme.GOLD), BorderLayout.EAST);
		}
		card.add(top);

		String time = r.activeMillis > 0 ? "  ·  " + Formatting.duration(r.activeMillis / 3_600_000.0) : "";
		JPanel middle = new JPanel(new BorderLayout(6, 0));
		middle.setOpaque(false);
		middle.add(Theme.label(Formatting.withCommas(r.kills) + (r.kills == 1 ? " kill" : " kills") + time
			+ (r.slayerXp > 0 ? "  ·  +" + Formatting.compactXp(r.slayerXp) + " xp" : ""), Theme.MUTED), BorderLayout.CENTER);
		middle.add(Theme.label(DATE.format(Instant.ofEpochMilli(r.endMillis).atZone(ZoneId.systemDefault())), Theme.SUBTLE),
			BorderLayout.EAST);
		card.add(middle);

		StringBuilder extra = new StringBuilder();
		if (r.superiors > 0)
		{
			append(extra, r.superiors + (r.superiors == 1 ? " superior" : " superiors"));
		}
		if (r.points > 0)
		{
			append(extra, "+" + r.points + " pts");
		}
		if (r.supplies > 0)
		{
			append(extra, Formatting.compactXp(r.supplies) + " supplies");
		}
		if (r.deaths > 0)
		{
			append(extra, r.deaths + (r.deaths == 1 ? " death" : " deaths"));
		}
		if (extra.length() > 0)
		{
			card.add(Theme.label(extra.toString(), Theme.SUBTLE));
		}

		card.setToolTipText(tooltip(r, price));
		JPopupMenu menu = new JPopupMenu();
		JMenuItem remove = new JMenuItem("Remove from log");
		remove.addActionListener(e -> plugin.removeTaskRecord(r));
		menu.add(remove);
		if (r.task != null)
		{
			menu.add(findItem(r.task));
		}
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
		if (r.area != null)
		{
			sb.append(" in ").append(r.area);
		}
		else if (r.location != null)
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
		if (r.supplies > 0)
		{
			sb.append("<br>Supplies used: ").append(Formatting.compactXp(r.supplies)).append(" gp");
		}
		if (r.cannonballs > 0)
		{
			sb.append("<br>Cannonballs fired: ").append(Formatting.withCommas(r.cannonballs));
		}
		if (r.deaths > 0)
		{
			sb.append("<br>Deaths: ").append(r.deaths);
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

	private static UiKit.RoundPanel card()
	{
		return UiKit.card(new DynamicGridLayout(0, 1, 0, 3));
	}

	private static JLabel title(String text)
	{
		JLabel label = Theme.boldLabel(text, Theme.TEXT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		return label;
	}

	private static JPanel row(String label, String value, Color valueColour, String tooltip)
	{
		return UiKit.row(label, value, valueColour, tooltip);
	}

	private static JLabel wrapped(String text, Color colour)
	{
		return UiKit.wrapped(text, colour);
	}

	/**
	 * WHAT: A short note in a soft box of its colour, e.g. the block idea in gold or a tip in green.
	 * WHY: Advice stands apart from the list above it.
	 * PRE: text may contain simple HTML; tooltip may be null.
	 * POST: Returns a new box.
	 * RETURN: the box
	 * THROWS: Nothing.
	 */
	private static JPanel note(String text, Color colour, String tooltip)
	{
		UiKit.RoundPanel box = new UiKit.RoundPanel(new BorderLayout(), Theme.highlight(colour, 0.8f), UiKit.BOX_RADIUS);
		box.setBorder(BorderFactory.createEmptyBorder(4, 6, 5, 6));
		box.add(UiKit.wrappedTo(text, Theme.TEXT, 140), BorderLayout.CENTER);
		box.setToolTipText(tooltip);
		return box;
	}

	private static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static JPanel holder(java.awt.Component child)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(child, BorderLayout.CENTER);
		return holder;
	}
}
