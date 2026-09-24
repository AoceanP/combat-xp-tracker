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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.QuantityFormatter;

/**
 * The sidebar:
 *
 * <pre>
 * Combat &amp; XP Tracker                 [Reset]
 * +-------------------------------------------+
 * | 18.3 Avg hit          |  45 Biggest hit   |
 * | 245 Hits              |  52 Melee max     |
 * | Aggressive, Piety, +132 str               |
 * | On task (Abyssal demons)             60   |
 * +-------------------------------------------+
 * [   Goals   ][  Monsters  ]
 * ...goal cards / monster cards...
 * </pre>
 *
 * Every method runs on the Swing thread. Data comes from the plugin's synchronized
 * trackers; nothing here touches the game client directly.
 */
class CombatXpTrackerPanel extends PluginPanel
{
	private final CombatXpTrackerPlugin plugin;
	private final CombatXpTrackerConfig config;
	private final SkillIconManager skillIconManager;
	private final ItemManager itemManager;

	// Combat card
	private final JLabel avgValue = tileValue();
	private final JLabel biggestValue = tileValue();
	private final JLabel hitsValue = tileValue();
	private final JLabel fourthValue = tileValue();
	private final JLabel fourthCaption = Theme.label("", Theme.MUTED);
	private final JPanel maxHitDetails = new JPanel(new DynamicGridLayout(0, 1, 0, 2));

	// Goals tab
	private final JPanel goalsList = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
	private final JPanel goalsEmpty;
	private final JLabel sessionLabel = Theme.label("", Theme.MUTED);
	private final Map<Skill, GoalCard> goalCards = new EnumMap<>(Skill.class);
	private List<Skill> shownGoals = new ArrayList<>();

	// Monsters tab
	private final JPanel monstersList = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
	private final JPanel monstersEmpty;
	private final JLabel killsValue = tileValue();
	private final JLabel lootValue = tileValue();
	private final JLabel monsterCountValue = tileValue();
	private final Map<String, MonsterCard> monsterCards = new HashMap<>();
	private int monsterRevision = -1;

	CombatXpTrackerPanel(CombatXpTrackerPlugin plugin, CombatXpTrackerConfig config,
		SkillIconManager skillIconManager, ItemManager itemManager)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		this.skillIconManager = skillIconManager;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);

		goalsEmpty = emptyState("No goals yet",
			"Click <b>Add goal</b>, or right-click a skill in your stats tab and pick <b>Set goal</b>. "
				+ "Goals can be a level up to 126 or an XP amount up to 200M.");
		monstersEmpty = emptyState("No monsters yet",
			"Attack something. Hits, your biggest hit per combat style, and every drop "
				+ "with its GE value show up here.");

		JPanel stack = new JPanel(new DynamicGridLayout(0, 1, 0, 8));
		stack.setBackground(Theme.BACKGROUND);
		stack.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		stack.add(buildHeader());
		stack.add(buildCombatCard());
		stack.add(buildTabs());

		JPanel north = new WidthTrackingPanel();
		north.setBackground(Theme.BACKGROUND);
		north.add(stack, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(north);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setBackground(Theme.BACKGROUND);
		scroll.getViewport().setBackground(Theme.BACKGROUND);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		add(scroll, BorderLayout.CENTER);

		refresh();
	}

	// ---- Construction ------------------------------------------------------------

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(false);

		JLabel title = Theme.boldLabel("Combat & XP Tracker", Theme.TEXT);
		header.add(title, BorderLayout.CENTER);

		JLabel reset = Theme.flatButton("Reset", "Clear damage, monsters and XP rates. Goals are kept.", this::confirmReset);
		header.add(reset, BorderLayout.EAST);
		return header;
	}

	private JPanel buildCombatCard()
	{
		JPanel tiles = new JPanel(new GridLayout(2, 2, 4, 4));
		tiles.setOpaque(false);
		tiles.add(tile(avgValue, Theme.label("Avg hit", Theme.MUTED)));
		tiles.add(tile(biggestValue, Theme.label("Biggest hit", Theme.MUTED)));
		tiles.add(tile(hitsValue, Theme.label("Hits", Theme.MUTED)));
		tiles.add(tile(fourthValue, fourthCaption));

		maxHitDetails.setOpaque(false);

		JPanel card = new JPanel(new BorderLayout(0, 6));
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		card.add(tiles, BorderLayout.NORTH);
		card.add(maxHitDetails, BorderLayout.CENTER);
		return card;
	}

	private JPanel buildTabs()
	{
		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setLayout(new GridLayout(1, 2, 4, 0));
		tabGroup.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		tabGroup.setOpaque(false);

		MaterialTab goalsTab = new MaterialTab("Goals", tabGroup, buildGoalsView());
		MaterialTab monstersTab = new MaterialTab("Monsters", tabGroup, buildMonstersView());
		tabGroup.addTab(goalsTab);
		tabGroup.addTab(monstersTab);
		tabGroup.select(goalsTab);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(tabGroup, BorderLayout.NORTH);
		wrapper.add(display, BorderLayout.CENTER);
		return wrapper;
	}

	private JPanel buildGoalsView()
	{
		JPanel view = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		view.setOpaque(false);

		view.add(Theme.flatButton("+ Add goal", "Pick a skill and a level or XP target",
			() -> promptGoalDialog(null)));

		goalsList.setOpaque(false);
		view.add(collapsible(goalsEmpty));
		view.add(goalsList);

		sessionLabel.setHorizontalAlignment(SwingConstants.CENTER);
		view.add(sessionLabel);
		return view;
	}

	private JPanel buildMonstersView()
	{
		JPanel summary = new JPanel(new GridLayout(1, 3, 4, 0));
		summary.setOpaque(false);
		summary.add(tile(killsValue, Theme.label("Kills", Theme.MUTED)));
		summary.add(tile(lootValue, Theme.label("Loot", Theme.MUTED)));
		summary.add(tile(monsterCountValue, Theme.label("Monsters", Theme.MUTED)));
		lootValue.setForeground(Theme.GOLD);

		monstersList.setOpaque(false);

		JPanel view = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		view.setOpaque(false);
		view.add(summary);
		view.add(collapsible(monstersEmpty));
		view.add(monstersList);
		return view;
	}

	private static JLabel tileValue()
	{
		JLabel value = new JLabel("-");
		value.setFont(FontManager.getRunescapeBoldFont());
		value.setForeground(Theme.TEXT);
		return value;
	}

	private static JPanel tile(JLabel value, JLabel caption)
	{
		JPanel tile = new JPanel(new BorderLayout());
		tile.setBackground(Theme.HEADER);
		tile.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		tile.add(value, BorderLayout.NORTH);
		tile.add(caption, BorderLayout.SOUTH);
		return tile;
	}

	/**
	 * Wraps a component so hiding it also removes its space. DynamicGridLayout keeps a
	 * row for invisible children; BorderLayout gives an empty holder zero height.
	 */
	private static JPanel collapsible(JPanel child)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(child, BorderLayout.CENTER);
		return holder;
	}

	/**
	 * The scroll view. Tracking the viewport width makes content wrap and truncate to
	 * the sidebar instead of growing sideways and being clipped.
	 */
	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		WidthTrackingPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static JPanel emptyState(String title, String body)
	{
		// Fixed wrap width: HTML labels otherwise report their whole text as one line.
		JLabel label = new JLabel("<html><div style='text-align:center;width:150px'><b>" + title + "</b><br><br>" + body + "</div></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Theme.MUTED);
		label.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(Theme.CARD);
		panel.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
		panel.add(label, BorderLayout.CENTER);
		return panel;
	}

	// ---- Refresh --------------------------------------------------------------

	void refresh()
	{
		updateCombatCard();
		updateGoals();
		updateMonsters();
		revalidate();
		repaint();
	}

	private void updateCombatCard()
	{
		HitStats hits = plugin.getHitStats();
		int count = hits.getHitCount();
		avgValue.setText(count > 0 ? String.format(java.util.Locale.US, "%.1f", hits.getAverageDamage()) : "-");
		biggestValue.setText(count > 0 ? String.valueOf(hits.getMaxHit()) : "-");
		hitsValue.setText(Formatting.withCommas(count));

		maxHitDetails.removeAll();
		MeleeMaxHitCalculator.Result max = plugin.getMaxHitResult();
		if (!config.showMeleeMaxHit())
		{
			fourthCaption.setText("Session XP");
			fourthValue.setText(Formatting.compactXp(totalSessionXp()));
			fourthValue.setForeground(Theme.TEXT);
			fourthValue.setToolTipText(null);
			return;
		}

		fourthCaption.setText("Melee max");
		fourthValue.setForeground(CombatStyle.MELEE.getColor());
		if (max == null)
		{
			fourthValue.setText("-");
			fourthValue.setToolTipText("Log in to calculate");
			return;
		}

		fourthValue.setText(String.valueOf(max.getMaxHit()));
		fourthValue.setToolTipText("<html>Melee max hit from worn gear, boosted Strength, prayer and attack style."
			+ "<br>Special attacks and weapon passives aren't included.</html>");

		StringBuilder setup = new StringBuilder();
		setup.append(max.isMeleeStyle() ? max.getAttackStyleName() : "Not on a melee style");
		if (max.getPrayer() != MeleeMaxHit.StrengthPrayer.NONE)
		{
			setup.append(", ").append(max.getPrayer().getDisplayName());
		}
		setup.append(", +").append(max.getStrengthBonus()).append(" str");
		if (max.isVoidMelee())
		{
			setup.append(", Void");
		}
		JLabel setupLabel = Theme.label(setup.toString(), Theme.SUBTLE);
		setupLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		maxHitDetails.add(setupLabel);

		if (max.getOnTaskMaxHit() >= 0)
		{
			String task = "On task (" + max.getSlayerTask() + ")";
			maxHitDetails.add(detailRow(task, max.getOnTaskMaxHit(),
				max.isTargetOnTask(),
				max.isTargetOnTask() ? "Your current target is part of your task" : "Black mask / Slayer helm bonus on task monsters"));
		}
		if (max.getVsUndeadMaxHit() >= 0)
		{
			maxHitDetails.add(detailRow("Vs undead (" + max.getSalve().getDisplayName() + ")", max.getVsUndeadMaxHit(),
				false, "Salve amulet bonus against undead. It doesn't stack with the Slayer helm."));
		}
	}

	private static JPanel detailRow(String label, int value, boolean highlight, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(highlight ? Theme.darken(Theme.SUCCESS, 0.72f) : Theme.HEADER);
		row.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
		row.setToolTipText(tooltip);
		row.add(Theme.label(label, highlight ? Theme.SUCCESS : Theme.MUTED), BorderLayout.CENTER);
		row.add(Theme.boldLabel(String.valueOf(value), highlight ? Theme.SUCCESS : Theme.TEXT), BorderLayout.EAST);
		return row;
	}

	private int totalSessionXp()
	{
		int total = 0;
		for (SkillProgress progress : plugin.getSkillProgress().values())
		{
			total += progress.getSessionXpGained();
		}
		return total;
	}

	private void updateGoals()
	{
		List<Skill> active = new ArrayList<>();
		for (Map.Entry<Skill, SkillProgress> entry : plugin.getSkillProgress().entrySet())
		{
			if (entry.getValue().isGoalSet())
			{
				active.add(entry.getKey());
			}
		}

		if (!active.equals(shownGoals))
		{
			goalsList.removeAll();
			for (Skill skill : active)
			{
				goalsList.add(goalCards.computeIfAbsent(skill,
					s -> new GoalCard(s, plugin, config, skillIconManager, this)));
			}
			goalCards.keySet().retainAll(active);
			shownGoals = active;
		}

		for (Skill skill : active)
		{
			goalCards.get(skill).update();
		}

		goalsEmpty.setVisible(active.isEmpty());
		int session = totalSessionXp();
		sessionLabel.setText(session > 0 ? "+" + Formatting.withCommas(session) + " xp this session" : "");
	}

	private void updateMonsters()
	{
		MonsterTracker tracker = plugin.getMonsterTracker();
		int revision = tracker.getRevision();
		if (revision == monsterRevision)
		{
			return;
		}
		monsterRevision = revision;

		List<MonsterTracker.Snapshot> snapshots = tracker.snapshot();
		Map<String, MonsterCard> keep = new HashMap<>();
		monstersList.removeAll();

		int kills = 0;
		long loot = 0;
		for (MonsterTracker.Snapshot s : snapshots)
		{
			MonsterCard card = monsterCards.get(s.getName());
			if (card == null)
			{
				card = new MonsterCard(s.getName(), itemManager, plugin);
			}
			card.update(s);
			keep.put(s.getName(), card);
			monstersList.add(card);
			kills += s.getKills();
			loot += s.getLootValue();
		}
		monsterCards.clear();
		monsterCards.putAll(keep);

		killsValue.setText(Formatting.withCommas(kills));
		lootValue.setText(QuantityFormatter.quantityToStackSize(loot));
		lootValue.setToolTipText(Formatting.withCommas(loot) + " gp");
		monsterCountValue.setText(String.valueOf(snapshots.size()));
		monstersEmpty.setVisible(snapshots.isEmpty());
	}

	// ---- Dialogs ----------------------------------------------------------------

	private void confirmReset()
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Clear damage stats, monsters and XP rates?\nYour goals and colours are kept.",
			"Reset tracker", JOptionPane.YES_NO_OPTION);
		if (choice == JOptionPane.YES_OPTION)
		{
			plugin.resetTracker();
		}
	}

	/**
	 * Asks for a goal. With a null skill, the dialog also asks which skill.
	 */
	void promptGoalDialog(Skill presetSkill)
	{
		Skill[] skills = Skill.values();
		JComboBox<Skill> skillBox = new JComboBox<>(skills);
		skillBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof Skill)
				{
					Skill s = (Skill) value;
					SkillProgress p = plugin.getSkillProgress().get(s);
					setText(Formatting.capitalize(s.getName()) + (p != null && p.isGoalSet() ? "  (goal: " + p.getGoal() + ")" : ""));
				}
				return this;
			}
		});
		if (presetSkill != null)
		{
			skillBox.setSelectedItem(presetSkill);
		}

		JTextField input = new JTextField(defaultGoalText(presetSkill != null ? presetSkill : skills[0]), 14);
		skillBox.addActionListener(e -> input.setText(defaultGoalText((Skill) skillBox.getSelectedItem())));

		JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		presets.add(presetButton("Next level", () ->
		{
			SkillProgress p = plugin.getSkillProgress().get((Skill) skillBox.getSelectedItem());
			int level = p == null ? 1 : p.getCurrentLevel();
			input.setText(String.valueOf(Math.min(Goal.MAX_LEVEL, level + 1)));
		}));
		presets.add(presetButton("99", () -> input.setText("99")));
		presets.add(presetButton("126", () -> input.setText("126")));
		presets.add(presetButton("200M", () -> input.setText("200m")));

		JPanel form = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		if (presetSkill == null)
		{
			form.add(new JLabel("Skill"));
			form.add(skillBox);
		}
		form.add(new JLabel("Goal"));
		form.add(input);
		form.add(presets);
		form.add(new JLabel("<html><small>A level from 2 to 126, or XP like 13,034,431, 500k or 13.03m.</small></html>"));

		String title = presetSkill != null ? "Goal for " + Formatting.capitalize(presetSkill.getName()) : "Add goal";
		while (true)
		{
			int choice = JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
			{
				return;
			}

			Skill skill = presetSkill != null ? presetSkill : (Skill) skillBox.getSelectedItem();
			try
			{
				Goal goal = Goal.parse(input.getText());
				SkillProgress progress = plugin.getSkillProgress().get(skill);
				if (progress != null && progress.isXpKnown() && goal.getTargetXp() <= progress.getCurrentXp())
				{
					throw new IllegalArgumentException("You already have " + Formatting.withCommas(progress.getCurrentXp())
						+ " " + Formatting.capitalize(skill.getName()) + " xp. Pick a higher goal.");
				}
				plugin.setGoal(skill, goal);
				return;
			}
			catch (IllegalArgumentException e)
			{
				JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid goal", JOptionPane.WARNING_MESSAGE);
			}
		}
	}

	private String defaultGoalText(Skill skill)
	{
		SkillProgress progress = plugin.getSkillProgress().get(skill);
		if (progress == null)
		{
			return "99";
		}
		Goal existing = progress.getGoal();
		if (existing != null)
		{
			return existing.getType() == Goal.Type.LEVEL
				? String.valueOf(existing.getTargetLevel())
				: Formatting.withCommas(existing.getTargetXp());
		}
		int level = progress.getCurrentLevel();
		if (level < 99)
		{
			return "99";
		}
		return level < Experience.MAX_VIRT_LEVEL ? String.valueOf(level + 1) : "200m";
	}

	private static JButton presetButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.addActionListener(e -> action.run());
		return button;
	}
}
