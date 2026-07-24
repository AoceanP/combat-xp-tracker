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
		container.add(buildResetButtonPanel());
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

	private JPanel buildResetButtonPanel()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		javax.swing.JButton resetButton = new javax.swing.JButton("Reset hit stats");
		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.addActionListener(e ->
		{
			plugin.resetHitStats();
			refresh();
		});

		panel.add(resetButton);
		return panel;
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
		maxHitLabel.setText("Max hit: " + stats.getMaxHit());
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

			JPanel headerRow = new JPanel(new BorderLayout());
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

			JPanel nameAndIcon = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
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

			// Also allow right-click directly on the panel row as a convenience,
			// mirroring the in-game skill-tab right-click option.
			component.setComponentPopupMenu(buildRowPopupMenu());

			refresh();
		}

		private javax.swing.JPopupMenu buildRowPopupMenu()
		{
			javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
			javax.swing.JMenuItem setGoal = new javax.swing.JMenuItem("Set goal level");
			setGoal.addActionListener(e -> promptGoalLevelDialog(skill));
			menu.add(setGoal);
			return menu;
		}

		void refresh()
		{
			SkillProgress progress = plugin.getSkillProgress().get(skill);
			if (progress == null)
			{
				return;
			}

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
