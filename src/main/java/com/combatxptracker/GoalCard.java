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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;

/**
 * One goal in the Goals tab:
 *
 * <pre>
 * [icon] Attack                      87 / 99
 *        54.2K xp/hr               4h 12m left
 * [=========== 73.4% ===========            ]
 * 1.23M xp left                 +56.2K session
 * </pre>
 *
 * Right-click for options; double-click to change the goal.
 */
class GoalCard extends JPanel
{
	private final Skill skill;
	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;
	private final CombatXpTrackerPanel owner;

	private final JPanel content = new JPanel(new BorderLayout(0, 4));
	private final JLabel nameLabel = Theme.boldLabel("", Theme.TEXT);
	private final JLabel levelLabel = Theme.label("", Theme.MUTED);
	private final JLabel rateLabel = Theme.label("", Theme.MUTED);
	private final JLabel etaLabel = Theme.label("", Theme.MUTED);
	private final JLabel remainingLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel sessionLabel = Theme.label("", Theme.SUBTLE);
	private final GoalProgressBar bar = new GoalProgressBar();
	private final JPanel[] tintedPanels;

	private boolean shownOnce;
	private Color accent;

	GoalCard(Skill skill, CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config,
		SkillIconManager skillIconManager, CombatXpTrackerPanel owner)
	{
		this.skill = skill;
		this.plugin = plugin;
		this.config = config;
		this.owner = owner;

		setLayout(new BorderLayout());
		setOpaque(false);
		// Gap under each card; the card body sits in NORTH so it keeps its natural height.
		setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		JLabel icon = new JLabel();
		BufferedImage image = skillIconManager.getSkillImage(skill, false);
		if (image != null)
		{
			icon.setIcon(new ImageIcon(image));
		}
		icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
		icon.setVerticalAlignment(JLabel.CENTER);

		JPanel titleRow = row(nameLabel, levelLabel);
		JPanel rateRow = row(rateLabel, etaLabel);
		JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
		text.add(titleRow);
		text.add(rateRow);

		JPanel top = new JPanel(new BorderLayout());
		top.add(icon, BorderLayout.WEST);
		top.add(text, BorderLayout.CENTER);

		JPanel footer = row(remainingLabel, sessionLabel);

		content.add(top, BorderLayout.NORTH);
		content.add(bar, BorderLayout.CENTER);
		content.add(footer, BorderLayout.SOUTH);

		tintedPanels = new JPanel[]{content, top, text, titleRow, rateRow, footer};
		setCardBackground(Theme.CARD);

		add(content, BorderLayout.NORTH);

		JPopupMenu menu = buildMenu();
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setCardBackground(Theme.CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setCardBackground(Theme.CARD);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2)
				{
					owner.promptGoalDialog(skill);
				}
			}
		};
		for (JPanel p : tintedPanels)
		{
			p.addMouseListener(mouse);
			p.setComponentPopupMenu(menu);
		}
		bar.addMouseListener(mouse);
		bar.setComponentPopupMenu(menu);
	}

	private static JPanel row(JLabel left, JLabel right)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.add(left, BorderLayout.WEST);
		right.setHorizontalAlignment(JLabel.RIGHT);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private void setCardBackground(Color color)
	{
		for (JPanel p : tintedPanels)
		{
			p.setBackground(color);
		}
		updateBorder();
	}

	private void updateBorder()
	{
		Color stripe = accent != null ? accent : config.goalBarColor();
		Border inner = BorderFactory.createEmptyBorder(7, 8, 7, 8);
		content.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, stripe), inner));
	}

	private JPopupMenu buildMenu()
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem edit = new JMenuItem("Change goal");
		edit.addActionListener(e -> owner.promptGoalDialog(skill));
		menu.add(edit);

		JMenuItem colour = new JMenuItem("Set bar colour");
		colour.addActionListener(e ->
		{
			Color current = plugin.getSkillColor(skill);
			Color chosen = JColorChooser.showDialog(owner, "Bar colour for " + Formatting.capitalize(skill.getName()),
				current != null ? current : config.goalBarColor());
			if (chosen != null)
			{
				plugin.setSkillColor(skill, chosen);
			}
		});
		menu.add(colour);

		JMenuItem resetColour = new JMenuItem("Use default colour");
		resetColour.addActionListener(e -> plugin.setSkillColor(skill, null));
		menu.add(resetColour);

		JMenuItem overlay = new JMenuItem();
		overlay.addActionListener(e ->
		{
			SkillProgress progress = plugin.getSkillProgress().get(skill);
			if (progress != null)
			{
				progress.setDismissedFromOverlay(!progress.isDismissedFromOverlay());
			}
		});
		menu.add(overlay);

		menu.addSeparator();

		JMenuItem remove = new JMenuItem("Remove goal");
		remove.addActionListener(e -> plugin.clearGoal(skill));
		menu.add(remove);

		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e)
			{
				SkillProgress progress = plugin.getSkillProgress().get(skill);
				boolean hidden = progress != null && progress.isDismissedFromOverlay();
				overlay.setText(hidden ? "Show on overlay" : "Hide from overlay");
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e)
			{
			}
		});
		return menu;
	}

	void update()
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		Goal goal = progress == null ? null : progress.getGoal();
		if (goal == null)
		{
			return;
		}

		accent = plugin.getSkillColor(skill);
		Color fill = accent != null ? accent : config.goalBarColor();
		bar.setFillColor(fill);
		updateBorder();

		nameLabel.setText(Formatting.capitalize(skill.getName()));

		boolean xpKnown = progress.isXpKnown();
		int xp = progress.getCurrentXp();
		boolean reached = progress.isGoalReached();

		if (goal.getType() == Goal.Type.LEVEL)
		{
			levelLabel.setText(xpKnown ? progress.getCurrentLevel() + " / " + goal.getTargetLevel() : "Goal " + goal.getTargetLevel());
		}
		else
		{
			levelLabel.setText(xpKnown ? Formatting.compactXp(xp) + " / " + goal.getShortLabel() : "Goal " + goal.getShortLabel());
		}

		int rate = progress.getXpPerHour();
		if (!xpKnown)
		{
			rateLabel.setText("Log in to track");
			rateLabel.setForeground(Theme.SUBTLE);
			etaLabel.setText("");
		}
		else if (reached)
		{
			rateLabel.setText("Goal reached!");
			rateLabel.setForeground(Theme.SUCCESS);
			etaLabel.setText("");
		}
		else if (rate > 0)
		{
			rateLabel.setText(Formatting.compactXp(rate) + " xp/hr");
			rateLabel.setForeground(fill);
			etaLabel.setText(Formatting.duration(progress.getEstimatedHoursToGoal()) + " left");
		}
		else
		{
			rateLabel.setText("Idle");
			rateLabel.setForeground(Theme.SUBTLE);
			etaLabel.setText("");
		}

		double fraction = progress.getProgressToGoal();
		bar.setComplete(reached);
		bar.setText(reached ? "Complete" : String.format(java.util.Locale.US, "%.1f%%", Math.floor(fraction * 1000) / 10.0));
		bar.setValue(fraction, shownOnce);
		shownOnce = true;

		remainingLabel.setText(xpKnown && !reached
			? Formatting.withCommas(progress.getXpRemainingToGoal()) + " xp left"
			: "");
		int gained = progress.getSessionXpGained();
		sessionLabel.setText(gained > 0 ? "+" + Formatting.compactXp(gained) + " session" : "");

		String tooltip = "<html>" + Formatting.capitalize(skill.getName()) + ": " + goal
			+ "<br>" + Formatting.withCommas(xp) + " xp now, " + Formatting.withCommas(goal.getTargetXp()) + " xp goal"
			+ "<br>Double-click to change, right-click for options</html>";
		for (JPanel p : tintedPanels)
		{
			p.setToolTipText(tooltip);
		}
		bar.setToolTipText(tooltip);
	}
}
