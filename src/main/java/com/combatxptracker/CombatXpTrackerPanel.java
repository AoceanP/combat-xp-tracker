/*
 * Copyright (c) 2026, YourNameHere <https://github.com/YourNameHere>
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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;

/**
 * Sidebar panel.
 *
 * The layout deliberately mirrors RuneLite's own XpInfoBox: each row is a JPanel using
 * BorderLayout with its content added to BorderLayout.NORTH. That matters -- an earlier
 * version used BoxLayout.Y_AXIS throughout, which distributes leftover vertical space
 * *into* its children, stretching every row and producing large empty gaps between the
 * level text and the progress bar. BorderLayout.NORTH pins content to its natural
 * height and lets the surplus fall into an empty CENTER instead.
 */
public class CombatXpTrackerPanel extends PluginPanel
{
	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;
	private final SkillIconManager skillIconManager;

	private final JLabel avgDamageLabel = new JLabel();
	private final JLabel biggestHitLabel = new JLabel();
	private final JLabel hitCountLabel = new JLabel();
	private final JLabel meleeMaxHitLabel = new JLabel();

	private final Map<Skill, SkillRow> skillRows = new EnumMap<>(Skill.class);
	private final JPanel skillsContainer = new JPanel();

	private final JLabel emptyStateLabel = new JLabel(
		"<html><div style='text-align:center;padding:6px;'>"
			+ "No skills tracked yet.<br><br>"
			+ "Use <b>Set skill goal</b> above, or right-click a skill "
			+ "in your in-game stats tab."
			+ "</div></html>");

	public CombatXpTrackerPanel(CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config, SkillIconManager skillIconManager)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		this.skillIconManager = skillIconManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		// Everything lives in NORTH of this wrapper so the content keeps its natural
		// height rather than being stretched to fill the sidebar.
		JPanel northWrapper = new JPanel(new BorderLayout());
		northWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(buildDamagePanel(), BorderLayout.NORTH);
		header.add(buildActionButtonsPanel(), BorderLayout.CENTER);

		skillsContainer.setLayout(new BoxLayout(skillsContainer, BoxLayout.Y_AXIS));
		skillsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		emptyStateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyStateLabel.setFont(FontManager.getRunescapeSmallFont());
		skillsContainer.add(emptyStateLabel);

		buildSkillRows();

		northWrapper.add(header, BorderLayout.NORTH);
		northWrapper.add(skillsContainer, BorderLayout.CENTER);

		JPanel scrollContent = new JPanel(new BorderLayout());
		scrollContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollContent.add(northWrapper, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(scrollContent);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

		add(scrollPane, BorderLayout.CENTER);

		refresh();
	}

	/**
	 * Damage stats block: average, biggest observed hit, hit count, and (optionally) the
	 * calculated melee max hit.
	 */
	private JPanel buildDamagePanel()
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(8, 10, 8, 10));

		int rows = config.showMeleeMaxHit() ? 4 : 3;
		JPanel stats = new JPanel(new GridLayout(rows, 1, 0, 3));
		stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		avgDamageLabel.setForeground(Color.WHITE);
		avgDamageLabel.setFont(FontManager.getRunescapeSmallFont());

		biggestHitLabel.setForeground(Color.WHITE);
		biggestHitLabel.setFont(FontManager.getRunescapeSmallFont());

		hitCountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hitCountLabel.setFont(FontManager.getRunescapeSmallFont());

		meleeMaxHitLabel.setForeground(ColorScheme.BRAND_ORANGE);
		meleeMaxHitLabel.setFont(FontManager.getRunescapeSmallFont());
		meleeMaxHitLabel.setToolTipText("Estimated from equipped weapon, boosted Strength, and active prayer. "
			+ "Doesn't account for special attacks, Dharok's, Salve amulet, Slayer helm, or armour strength bonus.");

		stats.add(avgDamageLabel);
		stats.add(biggestHitLabel);
		stats.add(hitCountLabel);
		if (config.showMeleeMaxHit())
		{
			stats.add(meleeMaxHitLabel);
		}

		wrapper.add(stats, BorderLayout.NORTH);
		updateDamageLabels();
		return wrapper;
	}

	private JPanel buildActionButtonsPanel()
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(8, 0, 8, 0));

		JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton setGoalButton = new JButton("Set skill goal");
		setGoalButton.setFont(FontManager.getRunescapeSmallFont());
		setGoalButton.setFocusPainted(false);
		setGoalButton.setToolTipText("Choose a skill and target level without right-clicking in-game.");
		setGoalButton.addActionListener(e -> promptSkillPickerThenGoalDialog());
		buttons.add(setGoalButton);

		JButton summaryButton = new JButton("Session summary");
		summaryButton.setFont(FontManager.getRunescapeSmallFont());
		summaryButton.setFocusPainted(false);
		summaryButton.setToolTipText("Total XP gained, hits landed, and biggest hit since the last reset.");
		summaryButton.addActionListener(e -> showSessionSummaryDialog());
		buttons.add(summaryButton);

		JButton resetButton = new JButton("Reset tracker");
		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.setFocusPainted(false);
		resetButton.setToolTipText("Clears damage stats and XP rates. Goals and colors are kept.");
		resetButton.addActionListener(e -> confirmAndReset());
		buttons.add(resetButton);

		wrapper.add(buttons, BorderLayout.NORTH);
		return wrapper;
	}

	private void confirmAndReset()
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Reset all tracked damage stats and XP rates? Goal levels and colors are kept.",
			"Reset tracker",
			JOptionPane.YES_NO_OPTION);

		if (choice == JOptionPane.YES_OPTION)
		{
			plugin.resetHitStats();
			plugin.getSessionSummary().reset();
			for (SkillProgress p : plugin.getSkillProgress().values())
			{
				p.reset();
			}
			refresh();
		}
	}

	private void buildSkillRows()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			skillRows.put(skill, new SkillRow(skill));
		}
	}

	/**
	 * Skill picker, so a goal can be set without needing the in-game stats tab.
	 */
	private void promptSkillPickerThenGoalDialog()
	{
		Skill[] pickable = Arrays.stream(Skill.values())
			.filter(s -> s != Skill.OVERALL)
			.toArray(Skill[]::new);

		JComboBox<Skill> skillCombo = new JComboBox<>(pickable);
		skillCombo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(
				JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof Skill)
				{
					Skill s = (Skill) value;
					SkillProgress progress = plugin.getSkillProgress().get(s);
					boolean tracked = progress != null && progress.isGoalSet();
					setText(capitalize(s.getName()) + (tracked ? "  (tracked)" : ""));
				}
				return this;
			}
		});

		int choice = JOptionPane.showConfirmDialog(
			this, skillCombo, "Choose a skill",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (choice == JOptionPane.OK_OPTION)
		{
			Skill selected = (Skill) skillCombo.getSelectedItem();
			if (selected != null)
			{
				promptGoalLevelDialog(selected);
			}
		}
	}

	public void promptGoalLevelDialog(Skill skill)
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		int currentGoal = (progress != null && progress.isGoalSet()) ? progress.getGoalLevel() : 99;

		String input = JOptionPane.showInputDialog(
			this,
			"Goal level for " + capitalize(skill.getName()) + " (2-99):",
			currentGoal);

		if (input == null || input.trim().isEmpty())
		{
			return;
		}

		try
		{
			int level = Integer.parseInt(input.trim());
			if (level < 2 || level > 99)
			{
				JOptionPane.showMessageDialog(this, "Level must be between 2 and 99.");
				return;
			}
			plugin.setGoalLevel(skill, level);
		}
		catch (NumberFormatException e)
		{
			JOptionPane.showMessageDialog(this, "Please enter a valid number.");
		}
	}

	public void promptSkillColorDialog(Skill skill)
	{
		Color current = plugin.getSkillColor(skill);
		Color chosen = JColorChooser.showDialog(
			this,
			"Color for " + capitalize(skill.getName()),
			current != null ? current : ColorScheme.BRAND_ORANGE);

		if (chosen != null)
		{
			plugin.setSkillColor(skill, chosen);
		}
	}

	private void showSessionSummaryDialog()
	{
		SessionSummary summary = plugin.getSessionSummary();

		StringBuilder sb = new StringBuilder();
		sb.append("=== Session Summary ===\n\n");

		if (summary.getXpGainedThisSession().isEmpty())
		{
			sb.append("No XP gained yet this session.\n\n");
		}
		else
		{
			sb.append(String.format("Total XP gained: %,d\n\n", summary.getTotalXpGained()));
			for (Map.Entry<Skill, Integer> entry : summary.getXpGainedThisSession().entrySet())
			{
				sb.append(String.format("  %s: %,d xp\n", capitalize(entry.getKey().getName()), entry.getValue()));
			}
			sb.append("\n");
		}

		sb.append(String.format("Total hits landed: %,d\n", summary.getTotalHitsLanded()));

		if (summary.getBiggestHitDamage() >= 0)
		{
			String monster = summary.getBiggestHitMonsterName();
			sb.append(String.format("Biggest hit: %d%s\n",
				summary.getBiggestHitDamage(),
				monster != null ? " (on " + monster + ")" : " (target unknown)"));
		}
		else
		{
			sb.append("Biggest hit: none recorded yet\n");
		}

		JTextArea textArea = new JTextArea(sb.toString());
		textArea.setEditable(false);
		textArea.setFont(FontManager.getRunescapeSmallFont());
		textArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textArea.setForeground(Color.WHITE);

		JScrollPane scroll = new JScrollPane(textArea);
		scroll.setPreferredSize(new Dimension(250, 280));

		JOptionPane.showMessageDialog(this, scroll, "Session Summary", JOptionPane.PLAIN_MESSAGE);
	}

	public void refresh()
	{
		updateDamageLabels();

		int visible = 0;
		int insertIndex = 1; // index 0 is the empty-state label

		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			SkillRow row = skillRows.get(skill);
			if (row == null)
			{
				continue;
			}

			SkillProgress progress = plugin.getSkillProgress().get(skill);
			boolean tracked = progress != null && progress.isGoalSet();
			boolean inContainer = row.getParent() == skillsContainer;

			// Rows are added/removed rather than shown/hidden: an invisible BoxLayout child
			// can still reserve its layout space, which left blank gaps behind.
			if (tracked)
			{
				if (!inContainer)
				{
					skillsContainer.add(row, insertIndex);
				}
				insertIndex++;
				visible++;
				row.update();
			}
			else if (inContainer)
			{
				skillsContainer.remove(row);
			}
		}

		emptyStateLabel.setVisible(visible == 0);

		skillsContainer.revalidate();
		skillsContainer.repaint();
	}

	private void updateDamageLabels()
	{
		HitStats stats = plugin.getHitStats();
		avgDamageLabel.setText(String.format("Average damage: %.2f", stats.getAverageDamage()));
		biggestHitLabel.setText("Biggest hit: " + stats.getMaxHit());
		hitCountLabel.setText("Hits recorded: " + stats.getHitCount());

		if (config.showMeleeMaxHit())
		{
			int maxHit = plugin.getMeleeMaxHit();
			meleeMaxHitLabel.setText(maxHit >= 0 ? "Max hit: " + maxHit : "Max hit: no weapon");
		}
	}

	private static String capitalize(String s)
	{
		if (s == null || s.isEmpty())
		{
			return s;
		}
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	/**
	 * A single tracked skill.
	 *
	 * Structure mirrors RuneLite's XpInfoBox:
	 *   this (BorderLayout)
	 *     └─ NORTH: container (BorderLayout)
	 *          ├─ WEST:   skill icon
	 *          ├─ CENTER: stats grid (name / rate / level→goal / eta)
	 *          └─ SOUTH:  progress bar
	 *
	 * Adding to NORTH is what stops the row being stretched vertically.
	 */
	private class SkillRow extends JPanel
	{
		private final Skill skill;

		private final JPanel container = new JPanel(new BorderLayout());
		private final JPanel statsPanel = new JPanel();
		private final ProgressBar progressBar = new ProgressBar();

		private final JLabel nameLabel = new JLabel();
		private final JLabel rateLabel = new JLabel();
		private final JLabel goalLabel = new JLabel();
		private final JLabel etaLabel = new JLabel();

		SkillRow(Skill skill)
		{
			this.skill = skill;

			setLayout(new BorderLayout());
			setBorder(new EmptyBorder(0, 0, 5, 0));
			setBackground(ColorScheme.DARK_GRAY_COLOR);

			container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			container.setBorder(new EmptyBorder(6, 6, 6, 6));

			JLabel iconLabel = new JLabel();
			java.awt.image.BufferedImage icon = skillIconManager.getSkillImage(skill, true);
			if (icon != null)
			{
				iconLabel.setIcon(new ImageIcon(icon));
			}
			iconLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
			iconLabel.setVerticalAlignment(JLabel.TOP);

			// 2x2 grid: name / level-goal on the first row, rate / eta on the second.
			// Plain GridLayout is safe here (rather than stretching cells) because the
			// whole row sits in BorderLayout.NORTH and keeps its natural height.
			statsPanel.setLayout(new GridLayout(2, 2, 0, 2));
			statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			nameLabel.setFont(FontManager.getRunescapeSmallFont());
			nameLabel.setForeground(Color.WHITE);

			goalLabel.setFont(FontManager.getRunescapeSmallFont());
			goalLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			goalLabel.setHorizontalAlignment(JLabel.RIGHT);

			rateLabel.setFont(FontManager.getRunescapeSmallFont());
			rateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			etaLabel.setFont(FontManager.getRunescapeSmallFont());
			etaLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			etaLabel.setHorizontalAlignment(JLabel.RIGHT);

			statsPanel.add(nameLabel);
			statsPanel.add(goalLabel);
			statsPanel.add(rateLabel);
			statsPanel.add(etaLabel);

			progressBar.setBackground(ColorScheme.PROGRESS_INPROGRESS_COLOR.darker());
			progressBar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			progressBar.setMaximumValue(100);
			progressBar.setPreferredSize(new Dimension(0, 16));

			container.add(iconLabel, BorderLayout.WEST);
			container.add(statsPanel, BorderLayout.CENTER);
			container.add(progressBar, BorderLayout.SOUTH);

			// Rebuilt on each open so the dismiss/show label matches current state.
			JPopupMenu popup = new JPopupMenu();
			popup.addPopupMenuListener(new PopupMenuListener()
			{
				@Override
				public void popupMenuWillBecomeVisible(PopupMenuEvent e)
				{
					popup.removeAll();
					for (JMenuItem item : buildMenuItems())
					{
						popup.add(item);
					}
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
			container.setComponentPopupMenu(popup);
			progressBar.setComponentPopupMenu(popup);

			add(container, BorderLayout.NORTH);
		}

		private List<JMenuItem> buildMenuItems()
		{
			List<JMenuItem> items = new ArrayList<>();

			JMenuItem setGoal = new JMenuItem("Set goal level");
			setGoal.addActionListener(e -> promptGoalLevelDialog(skill));
			items.add(setGoal);

			JMenuItem setColor = new JMenuItem("Set colour");
			setColor.addActionListener(e -> promptSkillColorDialog(skill));
			items.add(setColor);

			JMenuItem clearColor = new JMenuItem("Clear colour");
			clearColor.addActionListener(e -> plugin.setSkillColor(skill, null));
			items.add(clearColor);

			SkillProgress progress = plugin.getSkillProgress().get(skill);
			boolean dismissed = progress != null && progress.isDismissedFromOverlay();
			JMenuItem toggleOverlay = new JMenuItem(dismissed ? "Show on overlay" : "Hide from overlay");
			toggleOverlay.addActionListener(e ->
			{
				if (progress != null)
				{
					progress.setDismissedFromOverlay(!dismissed);
				}
			});
			items.add(toggleOverlay);

			JMenuItem stopTracking = new JMenuItem("Stop tracking");
			stopTracking.addActionListener(e -> plugin.clearGoal(skill));
			items.add(stopTracking);

			return items;
		}

		void update()
		{
			SkillProgress progress = plugin.getSkillProgress().get(skill);
			if (progress == null)
			{
				return;
			}

			Color custom = plugin.getSkillColor(skill);
			Color background = custom != null ? custom : ColorScheme.DARKER_GRAY_COLOR;
			container.setBackground(background);
			statsPanel.setBackground(background);

			int level = progress.getCurrentLevel();
			int goal = progress.getGoalLevel();
			boolean reached = level >= goal;

			nameLabel.setText(capitalize(skill.getName()));
			goalLabel.setText(level + " \u2192 " + goal);

			int rate = progress.getXpPerHour();
			rateLabel.setText(rate > 0 ? String.format("%,d xp/hr", rate) : "idle");

			if (reached)
			{
				etaLabel.setText("done");
				progressBar.setValue(100);
				progressBar.setCenterLabel("Goal reached");
				progressBar.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
				progressBar.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR.darker());
			}
			else
			{
				double hours = progress.getEstimatedHoursToGoal();
				etaLabel.setText(hours > 0 ? String.format("%.1fh left", hours) : "");

				int percent = (int) Math.round(progress.getProgressToGoal() * 100);
				progressBar.setValue(percent);
				progressBar.setCenterLabel(percent + "%");
				progressBar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
				progressBar.setBackground(ColorScheme.PROGRESS_INPROGRESS_COLOR.darker());
			}

			progressBar.setLeftLabel(String.valueOf(level));
			progressBar.setRightLabel(String.valueOf(goal));
		}
	}
}
