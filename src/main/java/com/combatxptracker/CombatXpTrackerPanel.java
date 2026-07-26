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
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class CombatXpTrackerPanel extends PluginPanel
{
	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;
	private final SkillIconManager skillIconManager;

	private final JLabel avgDamageLabel = new JLabel();
	private final JLabel maxHitLabel = new JLabel();
	private final JLabel hitCountLabel = new JLabel();

	private final Map<Skill, SkillRow> skillRows = new EnumMap<>(Skill.class);
	private final JPanel skillsContainer = new JPanel();

	public CombatXpTrackerPanel(CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config, SkillIconManager skillIconManager)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		this.skillIconManager = skillIconManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);

		container.add(buildDamagePanel());
		container.add(Box.createRigidArea(new Dimension(0, 8)));
		container.add(buildActionButtonsPanel());
		container.add(Box.createRigidArea(new Dimension(0, 10)));

		skillsContainer.setLayout(new BoxLayout(skillsContainer, BoxLayout.Y_AXIS));
		skillsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buildSkillRows();
		container.add(skillsContainer);

		JScrollPane scrollPane = new JScrollPane(container);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		add(scrollPane, BorderLayout.CENTER);
	}

	private JPanel buildDamagePanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(3, 1, 0, 4));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));

		avgDamageLabel.setForeground(Color.WHITE);
		avgDamageLabel.setFont(FontManager.getRunescapeSmallFont());
		maxHitLabel.setForeground(Color.WHITE);
		maxHitLabel.setFont(FontManager.getRunescapeSmallFont());
		hitCountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hitCountLabel.setFont(FontManager.getRunescapeSmallFont());

		panel.add(avgDamageLabel);
		panel.add(maxHitLabel);
		panel.add(hitCountLabel);

		updateDamageLabels();

		return panel;
	}

	private JPanel buildActionButtonsPanel()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		javax.swing.JButton summaryButton = new javax.swing.JButton("Session summary");
		summaryButton.setFont(FontManager.getRunescapeSmallFont());
		summaryButton.setToolTipText("Shows total XP gained, hits landed, and your biggest hit since the tracker was last reset.");
		summaryButton.addActionListener(e -> showSessionSummaryDialog());
		panel.add(summaryButton);

		javax.swing.JButton resetButton = new javax.swing.JButton("Reset tracker");
		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.setToolTipText("Clears damage stats and XP rates for all skills. Goal levels and colors are kept.");
		resetButton.addActionListener(e ->
		{
			int choice = JOptionPane.showConfirmDialog(
				this,
				"Reset all tracked damage stats and XP rates? Goal levels and colors are kept.",
				"Reset tracker",
				JOptionPane.YES_NO_OPTION
			);
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
		});
		panel.add(resetButton);

		return panel;
	}

	/**
	 * Builds and shows the session summary as a plain dialog: total XP gained per skill,
	 * total hits landed, and the biggest single hit with its monster name if one was
	 * determinable. Text is plain and easy to select/copy so the user can paste it
	 * somewhere (chat, Discord, etc.) as a shareable readout.
	 */
	private void showSessionSummaryDialog()
	{
		SessionSummary summary = plugin.getSessionSummary();

		StringBuilder sb = new StringBuilder();
		sb.append("=== Combat & XP Tracker: Session Summary ===\n\n");

		if (summary.getXpGainedThisSession().isEmpty())
		{
			sb.append("No XP gained yet this session.\n");
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
			if (monster != null)
			{
				sb.append(String.format("Biggest hit: %d (on %s)\n", summary.getBiggestHitDamage(), monster));
			}
			else
			{
				sb.append(String.format("Biggest hit: %d (target unknown)\n", summary.getBiggestHitDamage()));
			}
		}
		else
		{
			sb.append("Biggest hit: none recorded yet\n");
		}

		javax.swing.JTextArea textArea = new javax.swing.JTextArea(sb.toString());
		textArea.setEditable(false);
		textArea.setFont(FontManager.getRunescapeSmallFont());
		textArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textArea.setForeground(Color.WHITE);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(260, 300));

		JOptionPane.showMessageDialog(
			this,
			scrollPane,
			"Session Summary",
			JOptionPane.PLAIN_MESSAGE
		);
	}

	private void buildSkillRows()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			SkillRow row = new SkillRow(skill);
			skillRows.put(skill, row);
			skillsContainer.add(row.component);
			skillsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
		}
	}

	/**
	 * Prompts the user for a goal level via a simple input dialog, since the
	 * right-click menu can't render its own text field.
	 */
	public void promptGoalLevelDialog(Skill skill)
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		int currentGoal = progress != null ? progress.getGoalLevel() : config.defaultGoalLevel();

		String input = JOptionPane.showInputDialog(
			this,
			"Set goal level for " + capitalize(skill.getName()) + " (2-99):",
			currentGoal
		);

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

	/**
	 * Opens a color picker for the given skill's row background, persisted via the plugin.
	 */
	public void promptSkillColorDialog(Skill skill)
	{
		Color current = plugin.getSkillColor(skill);
		Color chosen = javax.swing.JColorChooser.showDialog(
			this,
			"Choose a color for " + capitalize(skill.getName()),
			current != null ? current : ColorScheme.DARKER_GRAY_COLOR
		);
		if (chosen != null)
		{
			plugin.setSkillColor(skill, chosen);
		}
	}

	public void refresh()
	{
		updateDamageLabels();
		for (Map.Entry<Skill, SkillRow> entry : skillRows.entrySet())
		{
			entry.getValue().refresh();
		}
	}

	private void updateDamageLabels()
	{
		HitStats stats = plugin.getHitStats();
		avgDamageLabel.setText(String.format("Average damage: %.2f", stats.getAverageDamage()));
		maxHitLabel.setText("Biggest hit: " + stats.getMaxHit());
		hitCountLabel.setText("Hits recorded: " + stats.getHitCount());
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
	 * A single skill's row in the panel: name, level, goal, XP/hr, and a progress bar.
	 */
	private class SkillRow
	{
		private final Skill skill;
		private final JPanel component;
		private final JPanel headerRow;
		private final JPanel nameAndIcon;
		private final JLabel nameLabel = new JLabel();
		private final JLabel xpHrLabel = new JLabel();
		private final JLabel goalLabel = new JLabel();
		private final JProgressBar progressBar = new JProgressBar(0, 100);

		SkillRow(Skill skill)
		{
			this.skill = skill;
			this.component = new JPanel();
			component.setLayout(new BoxLayout(component, BoxLayout.Y_AXIS));
			component.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			component.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)
			));

			headerRow = new JPanel(new BorderLayout());
			headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			JLabel iconLabel = new JLabel();
			java.awt.image.BufferedImage icon = skillIconManager.getSkillImage(skill, true);
			if (icon != null)
			{
				iconLabel.setIcon(new ImageIcon(icon));
			}
			iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

			nameLabel.setForeground(Color.WHITE);
			nameLabel.setFont(FontManager.getRunescapeBoldFont());
			nameLabel.setText(capitalize(skill.getName()));

			nameAndIcon = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			nameAndIcon.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			nameAndIcon.add(iconLabel);
			nameAndIcon.add(nameLabel);

			goalLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			goalLabel.setFont(FontManager.getRunescapeSmallFont());
			goalLabel.setHorizontalAlignment(SwingConstants.RIGHT);

			headerRow.add(nameAndIcon, BorderLayout.WEST);
			headerRow.add(goalLabel, BorderLayout.EAST);

			xpHrLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			xpHrLabel.setFont(FontManager.getRunescapeSmallFont());

			progressBar.setStringPainted(true);
			progressBar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			progressBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
			progressBar.setFont(FontManager.getRunescapeSmallFont());
			progressBar.setPreferredSize(new Dimension(0, 16));

			component.add(headerRow);
			component.add(Box.createRigidArea(new Dimension(0, 3)));
			component.add(xpHrLabel);
			component.add(Box.createRigidArea(new Dimension(0, 3)));
			component.add(progressBar);

			// Popup menu is rebuilt fresh each time it's about to show (via the listener
			// below), not once at construction, so the dismiss/show label always reflects
			// current state rather than a stale snapshot from row creation time.
			javax.swing.JPopupMenu popupMenu = new javax.swing.JPopupMenu();
			popupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
			{
				@Override
				public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
				{
					popupMenu.removeAll();
					for (java.awt.Component item : buildRowPopupMenuItems())
					{
						popupMenu.add(item);
					}
				}

				@Override
				public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}

				@Override
				public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
			});
			component.setComponentPopupMenu(popupMenu);

			refresh();
		}

		private java.util.List<java.awt.Component> buildRowPopupMenuItems()
		{
			java.util.List<java.awt.Component> items = new java.util.ArrayList<>();

			javax.swing.JMenuItem setGoal = new javax.swing.JMenuItem("Set goal level");
			setGoal.addActionListener(e -> promptGoalLevelDialog(skill));
			items.add(setGoal);

			javax.swing.JMenuItem setColor = new javax.swing.JMenuItem("Set skill color");
			setColor.addActionListener(e -> promptSkillColorDialog(skill));
			items.add(setColor);

			javax.swing.JMenuItem clearColor = new javax.swing.JMenuItem("Clear skill color");
			clearColor.addActionListener(e -> plugin.setSkillColor(skill, null));
			items.add(clearColor);

			items.add(new javax.swing.JPopupMenu.Separator());

			SkillProgress progress = plugin.getSkillProgress().get(skill);
			boolean currentlyDismissed = progress != null && progress.isDismissedFromOverlay();
			javax.swing.JMenuItem toggleDismiss = new javax.swing.JMenuItem(
				currentlyDismissed ? "Show on overlay" : "Dismiss from overlay"
			);
			toggleDismiss.addActionListener(e ->
			{
				if (progress != null)
				{
					progress.setDismissedFromOverlay(!currentlyDismissed);
				}
			});
			items.add(toggleDismiss);

			return items;
		}

		void refresh()
		{
			SkillProgress progress = plugin.getSkillProgress().get(skill);
			if (progress == null)
			{
				return;
			}

			Color customColor = plugin.getSkillColor(skill);
			Color rowBackground = customColor != null ? customColor : ColorScheme.DARKER_GRAY_COLOR;
			component.setBackground(rowBackground);
			headerRow.setBackground(rowBackground);
			nameAndIcon.setBackground(rowBackground);

			int level = progress.getCurrentLevel();
			int goal = progress.getGoalLevel();
			int xpPerHour = progress.getXpPerHour();
			double progressFraction = progress.getProgressToGoal();

			goalLabel.setText("Lvl " + level + " \u2192 " + goal);
			xpHrLabel.setText(xpPerHour > 0 ? String.format("%,d xp/hr", xpPerHour) : "No recent activity");

			int percent = (int) Math.round(progressFraction * 100);
			progressBar.setValue(percent);

			if (level >= goal)
			{
				progressBar.setString("Goal reached!");
				progressBar.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			}
			else
			{
				double etaHours = progress.getEstimatedHoursToGoal();
				String etaText = etaHours > 0 ? String.format(" (~%.1fh)", etaHours) : "";
				progressBar.setString(percent + "%" + etaText);
				progressBar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			}
		}
	}
}
