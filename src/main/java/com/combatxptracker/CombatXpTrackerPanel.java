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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/**
 * The sidebar (2.2):
 *
 * <pre>
 * Combat &amp; XP Tracker                     [≡]
 * +412K xp this session
 * ( 69 Ranged max | 45 Biggest | 18.3 Avg | 245 Hits )
 *   Aggressive, Piety, +132 str
 *   On task (Abyssal demons)               60
 * ( Goals )( Monsters )( Slayer )
 * ...goal cards / monster cards / slayer cards...
 * </pre>
 *
 * The ≡ menu holds Copy, Wrapped, Export, Reset, Restore, Report a bug and Suggest a feature.
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

	// Header: the session line under the title, which also confirms Copy for a moment.
	private final JLabel sessionLine = Theme.label("", Theme.SUBTLE);
	private long copiedUntil;
	private String copiedText;

	// Combat card
	private final JLabel avgValue = tileValue();
	private final JLabel biggestValue = tileValue();
	private final JLabel hitsValue = tileValue();
	private final JLabel fourthValue = tileValue();
	private final JLabel fourthCaption = Theme.label("", Theme.MUTED);
	private final JPanel maxHitDetails = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	// "Kills to level" and "Rest of task" rows under the max hit.
	private final JPanel levelDetails = new JPanel(new DynamicGridLayout(0, 1, 0, 2));

	// Goals tab
	private final DragReorder.ListPanel goalsList = new DragReorder.ListPanel(new DynamicGridLayout(0, 1, 0, 0));
	private final JPanel goalsEmpty;
	// "Today" at the top of the Goals tab: goals, XP and levels gained this session.
	private final JLabel goalCountValue = tileValue();
	private final JLabel sessionXpValue = tileValue();
	private final JLabel levelsValue = tileValue();
	// Kill count goals, under the skill goals; rebuilt only when they or the kills change.
	private final JPanel killGoalsList = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private String killGoalsKey = "";
	private final Map<Skill, GoalCard> goalCards = new EnumMap<>(Skill.class);
	private List<Skill> shownGoals = new ArrayList<>();

	// Monsters tab
	private final TargetCard targetCard = new TargetCard();
	private DropRevealCard revealCard;
	private final DragReorder.ListPanel monstersList = new DragReorder.ListPanel(new DynamicGridLayout(0, 1, 0, 0));
	// How many cards at the top of the Monsters list are pinned, the only ones that can be dragged.
	private int pinnedShown;
	private List<String> shownMonsterNames = new ArrayList<>();
	private SlayerView slayerView;
	private final JPanel monstersEmpty;
	private final JLabel restoreText = new JLabel();
	// Set while the restore card is built, for its menu to open under.
	private JLabel restoreButton;
	private final JPanel restoreCard = restoreCard();
	private final JLabel killsValue = tileValue();
	private final JLabel lootValue = tileValue();
	private final JLabel monsterCountValue = tileValue();
	private final Map<String, MonsterCard> monsterCards = new HashMap<>();
	// The sort order, as a small pill next to the search box that opens a menu.
	private final JLabel sortPill = new UiKit.RoundLabel("", UiKit.BOX_RADIUS + 2);
	private final JLabel hiddenLabel = Theme.label("", Theme.SUBTLE);
	private final JLabel monstersCaption = UiKit.caption("monsters");
	private final Map<MonsterRange, JLabel> rangeTabs = new EnumMap<>(MonsterRange.class);
	private final IconTextField searchField = new IconTextField();
	private final JLabel noMatchLabel = Theme.label("No monsters match your search", Theme.SUBTLE);
	// Revision + sort + hidden list last shown, so the list is only rebuilt on changes.
	private String monsterViewKey = "";
	// The main tabs, and where the selected tab's content goes.
	private final List<JLabel> mainTabs = new ArrayList<>();
	private final JPanel tabContent = new JPanel(new BorderLayout());

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

	private static final String ISSUES = "https://github.com/AoceanP/combat-xp-tracker/issues/new";


	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setOpaque(false);

		JPanel titles = new JPanel(new GridLayout(2, 1, 0, 1));
		titles.setOpaque(false);
		titles.add(Theme.boldLabel("Combat & XP Tracker", Theme.TEXT));
		titles.add(sessionLine);
		header.add(titles, BorderLayout.NORTH);

		// Grouped by what they do: sharing, your data, feedback.
		JPanel buttons = new JPanel(new GridLayout(3, 2, 4, 4));
		buttons.setOpaque(false);
		buttons.add(Theme.flatButton("Copy", "Copy a session summary to paste into Discord or a forum post", this::copySummary));
		buttons.add(Theme.flatButton("Wrapped", "Gielinor Wrapped: your lifetime stats, to look back on or share",
			this::showWrapped));
		buttons.add(Theme.flatButton("Reset", "Clear damage, monsters and XP rates. Goals are kept.", this::confirmReset));
		JLabel[] restore = new JLabel[1];
		restore[0] = Theme.flatButton("Restore", "Bring back kills and drops from RuneLite's Loot Tracker",
			() -> restoreMenu(restore[0]));
		buttons.add(restore[0]);
		buttons.add(Theme.flatButton("Report bug", "Opens the plugin's GitHub page to report a bug",
			() -> LinkBrowser.browse(ISSUES + "?labels=bug")));
		buttons.add(Theme.flatButton("Suggest", "Opens the plugin's GitHub page to suggest a feature",
			() -> LinkBrowser.browse(ISSUES + "?labels=enhancement")));
		header.add(buttons, BorderLayout.CENTER);
		return header;
	}

	private JPanel buildCombatCard()
	{
		// One strip: your max hit first, in its style's colour, then the session's hits.
		JPanel tiles = UiKit.stackedStrip(new JLabel[]{fourthValue, biggestValue, avgValue, hitsValue},
			new JLabel[]{fourthCaption, UiKit.caption("Biggest"), UiKit.caption("Avg hit"), UiKit.caption("Hits")});
		biggestValue.setToolTipText("Your biggest hit this session");
		avgValue.setToolTipText("Your average hit this session, 0s included");
		hitsValue.setToolTipText("Hits this session");

		maxHitDetails.setOpaque(false);
		levelDetails.setOpaque(false);

		JPanel card = UiKit.card(new BorderLayout(0, 4));
		card.add(tiles, BorderLayout.NORTH);
		card.add(maxHitDetails, BorderLayout.CENTER);
		card.add(levelDetails, BorderLayout.SOUTH);
		return card;
	}

	private JPanel buildTabs()
	{
		JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		JPanel goals = buildGoalsView();
		JPanel monsters = buildMonstersView();
		slayerView = new SlayerView(plugin, config);
		String[] names = {"Goals", "Monsters", "Slayer"};
		JComponent[] views = {goals, monsters, slayerView};
		for (int i = 0; i < names.length; i++)
		{
			JComponent view = views[i];
			JLabel tab = UiKit.tab(names[i], null, () -> showTab(view));
			tab.putClientProperty(TAB_VIEW, view);
			mainTabs.add(tab);
			row.add(tab);
		}
		tabContent.setOpaque(false);
		showTab(goals);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(row, BorderLayout.NORTH);
		wrapper.add(tabContent, BorderLayout.CENTER);
		return wrapper;
	}

	private static final String TAB_VIEW = "cxt.tabView";

	/**
	 * WHAT: Shows one tab's content and marks its tab as selected.
	 * WHY: Only the open tab is laid out, so a long Slayer tab doesn't leave empty space under Goals.
	 * PRE: view is one of the three tab views.
	 * POST: The content area holds the view; the tabs are restyled.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void showTab(JComponent view)
	{
		tabContent.removeAll();
		tabContent.add(view, BorderLayout.CENTER);
		for (JLabel tab : mainTabs)
		{
			UiKit.select(tab, tab.getClientProperty(TAB_VIEW) == view);
		}
		tabContent.revalidate();
		tabContent.repaint();
	}

	private JPanel buildGoalsView()
	{
		JPanel view = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		view.setOpaque(false);

		// Today at a glance: how many goals, and what this session has brought.
		JPanel today = UiKit.card(new BorderLayout());
		today.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
		sessionXpValue.setForeground(Theme.SUCCESS);
		today.add(UiKit.stackedStrip(new JLabel[]{goalCountValue, sessionXpValue, levelsValue},
			new JLabel[]{UiKit.caption("Goals"), UiKit.caption("XP today"), UiKit.caption("Levels up")}), BorderLayout.CENTER);
		today.setToolTipText("This session: since the client started or your last Reset");
		view.add(today);

		JLabel[] add = new JLabel[1];
		add[0] = Theme.flatButton("+ Add goal", "A skill level or XP target, or a kill count for a monster",
			() -> addGoalMenu().show(add[0], 0, add[0].getHeight()));
		view.add(add[0]);

		goalsList.setOpaque(false);
		view.add(collapsible(goalsEmpty));
		view.add(goalsList);
		killGoalsList.setOpaque(false);
		view.add(killGoalsList);
		return view;
	}

	private JPanel buildMonstersView()
	{
		JPanel summary = UiKit.strip(new JLabel[]{killsValue, lootValue, monsterCountValue},
			new JLabel[]{UiKit.caption("kills"), UiKit.caption("loot"), monstersCaption});
		lootValue.setForeground(Theme.GOLD);

		sortPill.setFont(FontManager.getRunescapeSmallFont());
		sortPill.setForeground(Theme.TEXT);
		sortPill.setBackground(Theme.CARD);
		sortPill.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
		sortPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		sortPill.setToolTipText("Order of the monsters below");
		sortPill.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (sortPill.contains(e.getPoint()))
				{
					sortMenu().show(sortPill, 0, sortPill.getHeight());
				}
			}
		});

		monstersList.setOpaque(false);

		hiddenLabel.setHorizontalAlignment(SwingConstants.CENTER);
		hiddenLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hiddenLabel.setToolTipText("Click to show every hidden monster again");
		hiddenLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				plugin.unhideAllMonsters();
			}
		});

		JPanel view = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		view.setOpaque(false);
		JPanel rangeRow = new JPanel(new GridLayout(1, 2, 4, 0));
		rangeRow.setOpaque(false);
		for (MonsterRange range : MonsterRange.values())
		{
			JLabel tab = UiKit.tab(range.toString(), range == MonsterRange.SESSION
				? "Since the client started or your last Reset"
				: "Everything remembered for this account", () -> plugin.setMonsterRange(range));
			rangeTabs.put(range, tab);
			rangeRow.add(tab);
		}

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(0, 26));
		// Same look as the search boxes in RuneLite's own panels.
		searchField.setBackground(Theme.CARD);
		searchField.setHoverBackgroundColor(Theme.CARD_HOVER);
		searchField.setToolTipText("Search monsters by name");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateMonsters();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateMonsters();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateMonsters();
			}
		});
		searchField.addClearListener(this::updateMonsters);
		JPanel findRow = new JPanel(new BorderLayout(4, 0));
		findRow.setOpaque(false);
		findRow.add(searchField, BorderLayout.CENTER);
		findRow.add(sortPill, BorderLayout.EAST);

		targetCard.setVisible(false);
		view.add(rangeRow);
		revealCard = new DropRevealCard(itemManager);
		view.add(collapsible(revealCard));
		view.add(collapsible(targetCard));
		view.add(collapsible(restoreCard));
		view.add(summary);
		view.add(findRow);
		view.add(collapsible(monstersEmpty));
		noMatchLabel.setHorizontalAlignment(SwingConstants.CENTER);
		noMatchLabel.setVisible(false);
		JPanel noMatchHolder = new JPanel(new BorderLayout());
		noMatchHolder.setOpaque(false);
		noMatchHolder.add(noMatchLabel, BorderLayout.CENTER);
		view.add(noMatchHolder);
		view.add(monstersList);
		JPanel hiddenHolder = new JPanel(new BorderLayout());
		hiddenHolder.setOpaque(false);
		hiddenHolder.add(hiddenLabel, BorderLayout.CENTER);
		view.add(hiddenHolder);
		return view;
	}

	/**
	 * WHAT: The sort menu under the sort pill, with the current order ticked.
	 * WHY: Replaces the old drop-down box, which took its own row.
	 * PRE: Swing thread.
	 * POST: Returns a new menu; picking an order saves it.
	 * RETURN: the menu
	 * THROWS: Nothing.
	 */
	private javax.swing.JPopupMenu sortMenu()
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		MonsterSort current = config.monsterSort();
		for (MonsterSort sort : MonsterSort.values())
		{
			javax.swing.JRadioButtonMenuItem item = new javax.swing.JRadioButtonMenuItem(sort.toString(), sort == current);
			item.addActionListener(e -> plugin.setMonsterSort(sort));
			menu.add(item);
		}
		return menu;
	}

	private static JLabel tileValue()
	{
		JLabel value = new JLabel("-");
		value.setFont(FontManager.getRunescapeBoldFont());
		value.setForeground(Theme.TEXT);
		return value;
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

		JPanel panel = UiKit.card(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
		panel.add(label, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Offered to accounts with Loot Tracker records: for players whose stats 2.0 erased, and
	 * after removing a monster or resetting everything, in case it was by accident.
	 */
	private JPanel restoreCard()
	{
		JLabel text = restoreText;
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(Theme.MUTED);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		restoreButton = Theme.cardButton("Restore", "Rebuild your Monsters tab from the Loot Tracker", this::restoreClicked);
		buttons.add(restoreButton);
		buttons.add(Theme.cardButton("Dismiss", "Hide this for this account", () -> plugin.dismissRestore()));

		UiKit.RoundPanel card = UiKit.card(new BorderLayout(0, 8)).outline(Theme.GOLD);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		card.add(text, BorderLayout.CENTER);
		card.add(buttons, BorderLayout.SOUTH);
		card.setVisible(false);
		return card;
	}

	private void updateRestoreCard()
	{
		boolean offered = plugin.isRestoreOffered();
		restoreCard.setVisible(offered);
		if (!offered)
		{
			return;
		}
		String monster = plugin.getRestoreMonster();
		String title;
		String body = "Rebuild kills and drops from RuneLite's Loot Tracker.";
		if (monster != null)
		{
			title = "Removed " + monster + " by accident?";
			body = "Bring its kills and drops back from RuneLite's Loot Tracker.";
		}
		else if (plugin.getMonsterTracker().isEmpty())
		{
			title = "Lost your stats?";
		}
		else
		{
			title = "Lost your stats in 2.0?";
		}
		restoreText.setText("<html><div style='width:150px'><b>" + title + "</b><br>" + body + "</div></html>");
	}


	/**
	 * A monster removed by accident is restored straight away; otherwise all monsters, or the
	 * ones chosen from a list.
	 */
	private void restoreClicked()
	{
		String monster = plugin.getRestoreMonster();
		if (monster != null)
		{
			confirmRestore(java.util.Collections.singleton(monster));
			return;
		}
		restoreMenu(restoreButton);
	}

	/**
	 * All monsters, or the ones chosen from a list. From the restore card or the header.
	 */
	private void restoreMenu(JLabel under)
	{
		if (!plugin.canRestore())
		{
			JOptionPane.showMessageDialog(this, "Log in to the account you want to restore first.",
				"Restore from Loot Tracker", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem all = new javax.swing.JMenuItem("All monsters");
		all.addActionListener(e -> confirmRestore(null));
		javax.swing.JMenuItem choose = new javax.swing.JMenuItem("Choose monsters...");
		choose.addActionListener(e -> plugin.loadRestoreChoices(choices ->
		{
			if (choices.isEmpty())
			{
				JOptionPane.showMessageDialog(this, "RuneLite's Loot Tracker has nothing saved for this account.",
					"Restore from Loot Tracker", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			Map<String, Integer> tracked = new java.util.HashMap<>();
			for (MonsterTracker.Saved s : plugin.getMonsterTracker().exportState())
			{
				tracked.put(s.name, s.kills);
			}
			RestoreChooser.open(this, choices, tracked, plugin::restoreFromLootTracker);
		}));
		menu.add(all);
		menu.add(choose);
		menu.show(under, 0, under.getHeight());
	}

	private void confirmRestore(java.util.Set<String> only)
	{
		Object[] options = {"Restore", "Cancel"};
		String question = only != null && only.size() == 1
			? "Bring back " + only.iterator().next() + "'s kills and drops from RuneLite's Loot Tracker?\n\n"
			: "Rebuild your Monsters tab from RuneLite's Loot Tracker and boss kill counts?\n\n";
		int choice = JOptionPane.showOptionDialog(this,
			question
				+ "Kills and drops are combined with what's tracked now, keeping the\n"
				+ "higher number, so nothing is counted twice.\n\n"
				+ "Hit stats, boss times and supplies weren't recorded anywhere else,\n"
				+ "so they can't be restored. Open your in-game Collection Log pages\n"
				+ "to fill the collection log back in.",
			"Restore from Loot Tracker", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (choice == 0)
		{
			plugin.restoreFromLootTracker(only);
		}
	}

	// ---- Refresh --------------------------------------------------------------

	void refresh()
	{
		updateCombatCard();
		updateLevelDetails();
		updateGoals();
		updateTargetCard();
		if (revealCard != null)
		{
			revealCard.play(plugin.getDropReveal());
		}
		updateRestoreCard();
		updateMonsters();
		if (slayerView != null)
		{
			slayerView.update();
		}
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
		MaxHitCalculator.Result max = plugin.getMaxHitResult();
		if (!config.showMeleeMaxHit())
		{
			fourthCaption.setText("Session XP");
			fourthValue.setText(Formatting.compactXp(totalSessionXp()));
			fourthValue.setForeground(Theme.TEXT);
			fourthValue.setToolTipText(null);
			return;
		}

		if (max == null)
		{
			fourthCaption.setText("Max hit");
			fourthValue.setForeground(Theme.TEXT);
			fourthValue.setText("-");
			fourthValue.setToolTipText("Log in to calculate");
			return;
		}

		CombatStyle style = max.getStyle();
		int change = plugin.getMaxHitChange();
		fourthCaption.setText(style.getDisplayName() + " max" + (change == 0 ? "" : " " + MaxHitFlash.label(change)));
		fourthValue.setForeground(change > 0 ? MaxHitFlash.UP : change < 0 ? MaxHitFlash.DOWN : style.getColor());
		fourthValue.setText(max.getMaxHit() >= 0 ? String.valueOf(max.getMaxHit()) : "-");
		fourthValue.setToolTipText("<html>" + style.getDisplayName() + " max hit for your current attack style, gear, "
			+ "levels and prayers.<br>Special attacks and bonuses against certain monsters are shown below.</html>");

		String text = max.getNote() != null ? max.getNote() : max.getSetup();
		if (text != null && !text.isEmpty())
		{
			// Wraps onto a second line rather than being cut off.
			JLabel setupLabel = Theme.label("<html><div style='width:175px'>" + text + "</div></html>", Theme.SUBTLE);
			setupLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
			setupLabel.setToolTipText(text);
			maxHitDetails.add(setupLabel);
		}

		if (config.showEffectiveLevels() && max.getEffectiveLevels() != null)
		{
			maxHitDetails.add(detailRow("Effective", max.getEffectiveLevels(), false,
				"<html>Effective levels: your levels with prayer, attack style, the hidden +8 and Void included.<br>"
					+ "Accuracy and max hit are worked out from these. Atk = Attack, Str = Strength, Rng = Ranged, Mag = Magic.</html>"));
		}
		HitLuck.Result luck = plugin.getLuck();
		if (luck != null)
		{
			maxHitDetails.add(detailRow("Luck", luck.label(), false, String.format(java.util.Locale.US,
				"<html>Your hits this session against your max hit, compared with what's expected from %s hits.<br>"
					+ "Average hit %d%% of your max (about 50%% is normal), %d max %s (about %.1f expected).<br>"
					+ "Special attacks and weapons that don't roll evenly aren't counted.</html>",
				Formatting.withCommas(luck.getHits()), Math.round(luck.getAverageShare() * 100), luck.getMaxHits(),
				luck.getMaxHits() == 1 ? "hit" : "hits", luck.getExpectedMaxHits())));
		}
		if (max.getSpecMaxHit() >= 0)
		{
			SpecialAttack spec = max.getSpec();
			String label = "Spec (" + spec.getDisplayName() + ")";
			String value = spec.getHits() > 1 ? spec.getHits() + " x " + max.getSpecMaxHit() : String.valueOf(max.getSpecMaxHit());
			maxHitDetails.add(detailRow(label, value, false,
				"Special attack max hit, before Slayer helm or Salve bonuses"));
		}
		if (max.getOnTaskMaxHit() >= 0)
		{
			String task = "On task (" + max.getSlayerTask() + ")";
			maxHitDetails.add(detailRow(task, max.getOnTaskMaxHit(),
				max.isTargetOnTask(),
				max.isTargetOnTask() ? "Your current target is part of your task" : "Black mask / Slayer helm bonus on task monsters"));
		}
		if (max.getVsUndeadMaxHit() >= 0)
		{
			maxHitDetails.add(detailRow("Vs undead (" + max.getUndeadSource() + ")", max.getVsUndeadMaxHit(),
				false, "Salve amulet bonus against undead. It doesn't stack with the Slayer helm."));
		}
		for (MaxHitCalculator.Extra extra : max.getExtras())
		{
			maxHitDetails.add(detailRow(extra.getLabel(), extra.getMaxHit(), extra.isHighlight(), extra.getTooltip()));
		}
	}

	/**
	 * Kills of the monster you're fighting to each skill's next level, and what the rest
	 * of your slayer task gives.
	 */
	private void updateLevelDetails()
	{
		levelDetails.removeAll();
		String target = plugin.getLastTargetName();
		if (!config.killsToLevel() || target == null)
		{
			levelDetails.setVisible(false);
			return;
		}

		List<LevelForecast.Row> rows = plugin.getKillsToLevel();
		if (!rows.isEmpty())
		{
			JLabel header = Theme.label("<html><div style='width:175px'>Kills to level: " + target + "</div></html>", Theme.SUBTLE);
			header.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
			levelDetails.add(header);
			for (LevelForecast.Row row : rows)
			{
				String skillName = Formatting.capitalize(row.getSkill().getName());
				String source = row.getMeasuredKills() > 0
					? "measured over your last " + row.getMeasuredKills() + (row.getMeasuredKills() == 1 ? " kill" : " kills")
					: "estimated from its hitpoints until you kill one";
				levelDetails.add(detailRow(skillName + " " + row.getNextLevel(),
					Formatting.withCommas(row.getKills()) + (row.getKills() == 1 ? " kill" : " kills"), false,
					"<html>About " + Formatting.withCommas(Math.round(row.getXpPerKill())) + " " + skillName + " xp per kill,<br>"
						+ source + "</html>"));
			}
		}

		LevelForecast.CombatRow combat = plugin.getKillsToCombatLevel();
		if (combat != null)
		{
			if (rows.isEmpty())
			{
				JLabel header = Theme.label("<html><div style='width:175px'>Kills to level: " + target + "</div></html>", Theme.SUBTLE);
				header.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
				levelDetails.add(header);
			}
			levelDetails.add(detailRow("Combat " + combat.getNextLevel(),
				Formatting.withCommas(combat.getKills()) + (combat.getKills() == 1 ? " kill" : " kills"), false,
				"Kills until your combat level goes up, with every combat skill gaining its XP per kill"));
		}

		List<LevelForecast.TaskGain> gains = plugin.getTaskForecast();
		if (!gains.isEmpty())
		{
			int left = plugin.getSlayerTaskRemaining();
			StringBuilder text = new StringBuilder("<html><div style='width:175px'>Rest of task (")
				.append(Formatting.withCommas(left)).append(left == 1 ? " kill" : " kills").append("):");
			for (LevelForecast.TaskGain gain : gains)
			{
				text.append("<br>").append(Formatting.capitalize(gain.getSkill().getName()))
					.append(" +").append(Formatting.compactXp(gain.getXp())).append(" xp");
				if (gain.getLevelAfter() > gain.getLevelNow())
				{
					text.append(" &rarr; ").append(gain.getLevelAfter());
				}
			}
			JLabel label = Theme.label(text.append("</div></html>").toString(), Theme.MUTED);
			label.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
			label.setToolTipText("What finishing your slayer task at this monster will give, at your current XP per kill");
			levelDetails.add(label);
		}
		levelDetails.setVisible(levelDetails.getComponentCount() > 0);
	}

	private static JPanel detailRow(String label, int value, boolean highlight, String tooltip)
	{
		return detailRow(label, String.valueOf(value), highlight, tooltip);
	}

	private static JPanel detailRow(String label, String value, boolean highlight, String tooltip)
	{
		// Plain rows; a highlighted one (e.g. your target is on task) gets a soft green box.
		JPanel row = highlight
			? new UiKit.RoundPanel(new BorderLayout(6, 0), Theme.highlight(Theme.SUCCESS, 0.72f), UiKit.BOX_RADIUS)
			: new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(2, highlight ? 6 : 2, 2, highlight ? 6 : 2));
		row.setToolTipText(tooltip);
		row.add(Theme.label(label, highlight ? Theme.SUCCESS : Theme.MUTED), BorderLayout.CENTER);
		row.add(Theme.boldLabel(value, highlight ? Theme.SUCCESS : Theme.TEXT), BorderLayout.EAST);
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

		// The order you dragged them into; new goals go last.
		List<Skill> order = plugin.getGoalOrder();
		active.sort(java.util.Comparator.comparingInt(skill ->
		{
			int index = order.indexOf(skill);
			return index >= 0 ? index : order.size() + skill.ordinal();
		}));

		if (!active.equals(shownGoals))
		{
			goalsList.setLineColour(Theme.accent(config.goalBarColor()));
			goalsList.removeAll();
			for (Skill skill : active)
			{
				goalsList.add(goalCards.computeIfAbsent(skill, this::newGoalCard));
			}
			goalCards.keySet().retainAll(active);
			shownGoals = active;
		}

		for (Skill skill : active)
		{
			goalCards.get(skill).update();
		}

		int killGoals = updateKillGoals();
		goalsEmpty.setVisible(active.isEmpty() && killGoals == 0);
		int session = totalSessionXp();
		goalCountValue.setText(String.valueOf(active.size() + killGoals));
		sessionXpValue.setText(session > 0 ? "+" + Formatting.compactXp(session) : "0");
		sessionXpValue.setToolTipText(Formatting.withCommas(session) + " xp this session");
		levelsValue.setText(String.valueOf(levelsGained()));
		updateSessionLine(session);
	}

	/**
	 * WHAT: Levels gained this session, across every skill.
	 * WHY: Shown in the Goals tab's Today card.
	 * PRE: Swing thread.
	 * POST: Nothing changes.
	 * RETURN: the number of levels gained since the session started
	 * THROWS: Nothing.
	 */
	private int levelsGained()
	{
		int levels = 0;
		for (SkillProgress progress : plugin.getSkillProgress().values())
		{
			int gained = progress.getSessionXpGained();
			if (gained > 0 && progress.isXpKnown())
			{
				int now = progress.getCurrentXp();
				levels += Math.max(0, Experience.getLevelForXp(now) - Experience.getLevelForXp(Math.max(0, now - gained)));
			}
		}
		return levels;
	}

	/**
	 * WHAT: The line under the title: this session's XP, or "Copied!" for a moment after Copy.
	 * WHY: Copy lives in the menu now, so the confirmation shows here instead of on a button.
	 * PRE: Swing thread.
	 * POST: The session line is updated.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void updateSessionLine(int session)
	{
		if (System.currentTimeMillis() < copiedUntil)
		{
			sessionLine.setText(copiedText);
			sessionLine.setForeground(Theme.SUCCESS);
			return;
		}
		sessionLine.setForeground(Theme.SUBTLE);
		sessionLine.setText(session > 0 ? "+" + Formatting.withCommas(session) + " xp this session" : "No XP yet this session");
	}

	/**
	 * WHAT: The "+ Add goal" menu: a skill goal or a kill count goal.
	 * WHY: Goals can be levels and XP, or kills of a monster.
	 * PRE: Swing thread.
	 * POST: Returns a new menu.
	 * RETURN: the menu
	 * THROWS: Nothing.
	 */
	private javax.swing.JPopupMenu addGoalMenu()
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem skill = new javax.swing.JMenuItem("Skill goal...");
		skill.setToolTipText("A level up to 126 or an XP amount up to 200M");
		skill.addActionListener(e -> promptGoalDialog(null));
		menu.add(skill);
		javax.swing.JMenuItem kills = new javax.swing.JMenuItem("Kill count goal...");
		kills.setToolTipText("Kills of a monster, like 500 Vorkath");
		kills.addActionListener(e -> promptKillGoal(null));
		menu.add(kills);
		return menu;
	}

	/**
	 * WHAT: Rebuilds the kill count goal cards when the goals or the kills change.
	 * WHY: Shown under the skill goals, each with progress and time left at your kill rate.
	 * PRE: Swing thread.
	 * POST: The list shows one card per goal.
	 * RETURN: how many kill goals there are
	 * THROWS: Nothing.
	 */
	private int updateKillGoals()
	{
		Map<String, Integer> goals = plugin.getKillGoals();
		MonsterTracker tracker = plugin.getMonsterTracker(MonsterRange.ALL_TIME);
		String key = goals + "|" + tracker.getRevision() + "|" + plugin.getRateTick();
		if (key.equals(killGoalsKey))
		{
			return goals.size();
		}
		killGoalsKey = key;
		killGoalsList.removeAll();
		for (Map.Entry<String, Integer> goal : goals.entrySet())
		{
			killGoalsList.add(killGoalCard(goal.getKey(), goal.getValue(), tracker.snapshotOf(goal.getKey())));
		}
		killGoalsList.revalidate();
		killGoalsList.repaint();
		return goals.size();
	}

	private JPanel killGoalCard(String monster, int target, MonsterTracker.Snapshot s)
	{
		int kills = s == null ? 0 : s.getKills();
		boolean reached = kills >= target;
		JPanel card = UiKit.card(new DynamicGridLayout(0, 1, 0, 4));

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Theme.boldLabel(monster, Theme.TEXT), BorderLayout.CENTER);
		JLabel count = Theme.label(Formatting.withCommas(kills) + " / " + Formatting.withCommas(target), Theme.MUTED);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		top.add(count, BorderLayout.EAST);
		card.add(top);

		card.add(UiKit.bar(kills / (double) target, reached ? Theme.SUCCESS : Theme.GOLD, 8));

		JPanel bottom = new JPanel(new BorderLayout(6, 0));
		bottom.setOpaque(false);
		double perHour = s == null ? -1 : s.getKillsPerHour();
		double hours = KillGoals.hoursLeft(kills, target, perHour);
		JLabel left = Theme.label(reached ? "Goal reached!" : Formatting.withCommas(target - kills) + " kills left",
			reached ? Theme.SUCCESS : Theme.MUTED);
		bottom.add(left, BorderLayout.CENTER);
		if (hours > 0)
		{
			JLabel eta = Theme.label("~" + Formatting.duration(hours), Theme.SUCCESS);
			eta.setToolTipText(String.format(java.util.Locale.US, "At your %.1f kills/hr on %s, from your time fighting it",
				perHour, monster));
			bottom.add(eta, BorderLayout.EAST);
		}
		card.add(bottom);

		String tip = "<html>" + monster + ": " + Formatting.withCommas(kills) + " of " + Formatting.withCommas(target) + " kills"
			+ "<br>Kills tracked by this plugin. Right-click to change or remove.</html>";
		card.setToolTipText(tip);
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem change = new javax.swing.JMenuItem("Change goal");
		change.addActionListener(e -> promptKillGoal(monster));
		menu.add(change);
		javax.swing.JMenuItem remove = new javax.swing.JMenuItem("Remove goal");
		remove.addActionListener(e -> plugin.removeKillGoal(monster));
		menu.add(remove);
		card.setComponentPopupMenu(menu);
		return card;
	}

	/**
	 * WHAT: Asks for a kill count goal: which monster, and how many kills.
	 * WHY: From "+ Add goal" or a monster card's right-click menu.
	 * PRE: Swing thread. preset is the monster to start with, or null to pick one.
	 * POST: The goal is saved if the player confirms a valid number.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void promptKillGoal(String preset)
	{
		List<MonsterTracker.Snapshot> monsters = new ArrayList<>(allTimeMonsters());
		monsters.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));
		List<String> names = new ArrayList<>();
		for (MonsterTracker.Snapshot s : monsters)
		{
			if (s.getKills() > 0)
			{
				names.add(s.getName());
			}
		}
		if (preset != null && !names.contains(preset))
		{
			names.add(0, preset);
		}
		if (names.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Kill something first, then set a goal for it.", "Kill count goal",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JComboBox<String> monsterBox = new JComboBox<>(names.toArray(new String[0]));
		monsterBox.setEditable(true);
		monsterBox.setSelectedItem(preset != null ? preset : names.get(0));
		JTextField input = new JTextField(14);
		Runnable suggest = () ->
		{
			String name = String.valueOf(monsterBox.getSelectedItem());
			Integer existing = plugin.getKillGoals().get(name);
			MonsterTracker.Snapshot s = plugin.getMonsterTracker(MonsterRange.ALL_TIME).snapshotOf(name);
			input.setText(Formatting.withCommas(existing != null ? existing : KillGoals.suggestTarget(s == null ? 0 : s.getKills())));
		};
		suggest.run();
		monsterBox.addActionListener(e -> suggest.run());

		JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		for (int value : new int[]{100, 500, 1000, 5000})
		{
			presets.add(presetButton(Formatting.withCommas(value), () -> input.setText(Formatting.withCommas(value))));
		}
		JPanel form = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		form.add(new JLabel("Monster"));
		form.add(monsterBox);
		form.add(new JLabel("Kills"));
		form.add(input);
		form.add(presets);
		form.add(new JLabel("<html><small>Counted from the kills this plugin has tracked for this account.</small></html>"));

		while (true)
		{
			int choice = JOptionPane.showConfirmDialog(this, form, "Kill count goal", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
			{
				return;
			}
			String name = String.valueOf(monsterBox.getSelectedItem()).trim();
			int target;
			try
			{
				target = Integer.parseInt(input.getText().replace(",", "").trim());
			}
			catch (NumberFormatException e)
			{
				target = -1;
			}
			if (name.isEmpty() || target < 1 || target > KillGoals.MAX_TARGET)
			{
				JOptionPane.showMessageDialog(this, "Pick a monster and a number of kills from 1 to "
					+ Formatting.withCommas(KillGoals.MAX_TARGET) + ".", "Invalid goal", JOptionPane.WARNING_MESSAGE);
				continue;
			}
			if (!plugin.setKillGoal(name, target))
			{
				JOptionPane.showMessageDialog(this, "Log in to the account first, so the goal is saved for it.",
					"Kill count goal", JOptionPane.INFORMATION_MESSAGE);
			}
			return;
		}
	}

	private GoalCard newGoalCard(Skill skill)
	{
		GoalCard card = new GoalCard(skill, plugin, config, skillIconManager, this);
		DragReorder.attach(card, goalsList, () -> true, () -> shownGoals.size(), this::moveGoal, card.getDragHandles());
		return card;
	}

	private void moveGoal(int from, int to)
	{
		List<Skill> order = new ArrayList<>(shownGoals);
		if (from < 0 || from >= order.size() || to < 0 || to >= order.size())
		{
			return;
		}
		order.add(to, order.remove(from));
		plugin.setGoalOrder(order);
	}

	private MonsterCard newMonsterCard(String name)
	{
		MonsterCard card = new MonsterCard(name, itemManager, plugin, this::promptKillGoal);
		DragReorder.attach(card, monstersList, () -> plugin.isPinned(name), () -> pinnedShown, this::movePinned,
			card.getDragHandle());
		return card;
	}

	private void movePinned(int from, int to)
	{
		if (from < 0 || from >= pinnedShown || to < 0 || to >= pinnedShown)
		{
			return;
		}
		List<String> order = new ArrayList<>(shownMonsterNames.subList(0, pinnedShown));
		order.add(to, order.remove(from));
		plugin.setPinnedOrder(order);
	}

	/**
	 * The card for the monster being fought now, from this session's stats on it.
	 */
	private void updateTargetCard()
	{
		TargetInfo target = plugin.getTargetInfo();
		if (target == null)
		{
			targetCard.setVisible(false);
			return;
		}
		MonsterTracker.Snapshot stats = plugin.getMonsterTracker(MonsterRange.SESSION).snapshotOf(target.getName());
		double averagePerHit = stats == null || stats.getRolls() == 0 ? 0 : stats.getTotalDamage() / (double) stats.getRolls();
		long perKill = stats == null || stats.getKills() == 0 ? -1 : stats.getActiveMillis() / stats.getKills();
		targetCard.update(target, averagePerHit, perKill, plugin.getDps(target.getName(), MonsterRange.SESSION),
			plugin.getFoodKills(target.getName()), plugin.getPrayerMinutes(target.getName()));
		targetCard.setVisible(true);
	}

	private void updateMonsters()
	{
		MonsterRange range = config.monsterRange();
		MonsterTracker tracker = plugin.getMonsterTracker(range);
		MonsterSort sort = config.monsterSort();
		String hiddenSetting = config.hiddenMonsters();
		LootPrice price = config.lootPrice();
		String ignoredSetting = config.ignoredItems();
		String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
		String task = plugin.getSlayerTaskName();
		int taskRemaining = plugin.getSlayerTaskRemaining();
		String key = range + "|" + tracker.getRevision() + "|" + sort + "|" + hiddenSetting + "|" + price
			+ "|" + ignoredSetting + "|" + search + "|" + task + "|" + taskRemaining
			+ "|" + config.rareDropValue() + "|" + config.pinnedMonsters() + "|" + plugin.getPrayerDrainRevision()
			+ "|" + plugin.getCollectionRevision() + "|" + plugin.getSuppliesRevision() + "|" + plugin.getFightStatsRevision()
			+ "|" + plugin.getCombatAchievementsRevision() + "|" + plugin.getRateTick();
		// The card you're fighting gets a green outline; the session tracker gives "+12 today".
		TargetInfo target = plugin.getTargetInfo();
		String fightingName = target == null ? null : BossGroups.trackedName(target.getName());
		MonsterTracker session = plugin.getMonsterTracker(MonsterRange.SESSION);
		key += "|" + fightingName + "|" + session.getRevision();
		if (key.equals(monsterViewKey))
		{
			return;
		}
		monsterViewKey = key;

		for (Map.Entry<MonsterRange, JLabel> tab : rangeTabs.entrySet())
		{
			UiKit.select(tab.getValue(), tab.getKey() == range);
		}

		sortPill.setText(sort + "  ▾");

		long rareValue = config.rareDropValue();
		java.util.Set<String> pinned = MonsterTracker.parseNames(config.pinnedMonsters());
		List<MonsterTracker.Snapshot> all = tracker.snapshot(price, MonsterTracker.itemMatcher(ignoredSetting), rareValue);
		if (range == MonsterRange.SESSION)
		{
			// Pinned monsters keep their place in This session too, even before you fight them.
			all = MonsterTracker.withPinned(all, pinned, plugin.getMonsterTracker(MonsterRange.ALL_TIME));
		}
		List<MonsterTracker.Snapshot> visible = MonsterTracker.view(all, sort, MonsterTracker.parseNames(hiddenSetting), pinned);
		List<MonsterTracker.Snapshot> shown = new ArrayList<>();
		for (MonsterTracker.Snapshot s : visible)
		{
			if (search.isEmpty() || s.getName().toLowerCase().contains(search))
			{
				shown.add(s);
			}
		}
		Map<String, MonsterCard> keep = new HashMap<>();
		monstersList.removeAll();

		int kills = 0;
		long loot = 0;
		for (MonsterTracker.Snapshot s : shown)
		{
			MonsterCard card = monsterCards.get(s.getName());
			if (card == null)
			{
				card = newMonsterCard(s.getName());
			}
			card.update(s, isTaskMonster(task, s.getName()) ? taskRemaining : -1, rareValue,
				pinned.contains(s.getName().toLowerCase()));
			card.updateSupplies(plugin.getSupplies(s.getName(), range));
			card.updateFight(s.getAccuracy(), plugin.getDps(s.getName(), range), plugin.getUptime(s.getName(), range));
			card.updateDeaths(plugin.getDeaths(s.getName(), range));
			card.updateFightExtras(plugin.getDamageTaken(s.getName(), range), plugin.getIdleTicks(s.getName(), range));
			card.updateBoss(plugin.getBossTimes(s.getName()), plugin.getDryItems(s.getName()),
				plugin.getCombatAchievements(s.getName()));
			card.updateDropLuck(plugin.getDropLuck(s));
			MonsterTracker.Snapshot today = range == MonsterRange.SESSION ? null : session.snapshotOf(s.getName());
			card.setStatus(s.getName().equals(fightingName), today == null ? 0 : today.getKills(), range != MonsterRange.SESSION);
			keep.put(s.getName(), card);
			monstersList.add(card);
			kills += s.getKills();
			loot += s.getLootValue();
		}
		monsterCards.clear();
		monsterCards.putAll(keep);
		shownMonsterNames = new ArrayList<>();
		pinnedShown = 0;
		for (MonsterTracker.Snapshot s : shown)
		{
			shownMonsterNames.add(s.getName());
			if (pinned.contains(s.getName().toLowerCase()))
			{
				pinnedShown++;
			}
		}
		monstersList.setLineColour(Theme.GOLD);

		killsValue.setText(Formatting.withCommas(kills));
		lootValue.setText(QuantityFormatter.quantityToStackSize(loot));
		lootValue.setToolTipText(Formatting.withCommas(loot) + " gp");
		monsterCountValue.setText(String.valueOf(shown.size()));
		monstersEmpty.setVisible(shown.isEmpty() && search.isEmpty());
		noMatchLabel.setVisible(shown.isEmpty() && !search.isEmpty());

		int hidden = all.size() - visible.size();
		hiddenLabel.setText(hidden > 0 ? hidden + " hidden - click to show" : "");
		hiddenLabel.setVisible(hidden > 0);
	}

	/**
	 * Puts a text summary of the session on the clipboard and briefly confirms it on
	 * the button.
	 */
	private void copySummary()
	{
		// The summary is about this session, whichever range the tab is showing.
		MonsterTracker tracker = plugin.getMonsterTracker(MonsterRange.SESSION);
		List<MonsterTracker.Snapshot> monsters = MonsterTracker.view(
			tracker.snapshot(config.lootPrice(), MonsterTracker.itemMatcher(config.ignoredItems())),
			config.monsterSort(), MonsterTracker.parseNames(config.hiddenMonsters()));
		// Each monster's PB and drop luck, and kill count goals, as in the panel.
		java.util.function.Function<MonsterTracker.Snapshot, String> extra = s ->
		{
			List<String> parts = new ArrayList<>();
			BossTimes.Times times = plugin.getBossTimes(s.getName());
			if (times != null && times.best > 0)
			{
				parts.add("PB " + KillTimerOverlay.clock(times.best));
			}
			DryCalculator.Luck dropLuck = plugin.getDropLuck(plugin.getMonsterTracker(MonsterRange.ALL_TIME).snapshotOf(s.getName()));
			if (dropLuck != null)
			{
				parts.add(String.format(java.util.Locale.US, "drop luck %s%.0f%% vs average", dropLuck.percent() >= 0 ? "+" : "",
					dropLuck.percent()));
			}
			return String.join(", ", parts);
		};
		List<String> killGoals = new ArrayList<>();
		MonsterTracker allTime = plugin.getMonsterTracker(MonsterRange.ALL_TIME);
		for (Map.Entry<String, Integer> goal : plugin.getKillGoals().entrySet())
		{
			MonsterTracker.Snapshot s = allTime.snapshotOf(goal.getKey());
			int kills = s == null ? 0 : s.getKills();
			killGoals.add(goal.getKey() + ": " + Formatting.withCommas(kills) + " / " + Formatting.withCommas(goal.getValue())
				+ " kills" + (kills >= goal.getValue() ? " reached" : ""));
		}
		String text = SessionSummaryText.build(plugin.getSkillProgress(), plugin.getHitStats(), monsters, config.xpRateMode(),
			plugin.getLuck(), plugin.getSessionUptime(), plugin.getSessionDps(), extra, killGoals);

		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			copiedText = "Copied to the clipboard";
		}
		catch (IllegalStateException e)
		{
			// Another program is holding the clipboard.
			copiedText = "Clipboard busy, try again";
		}
		copiedUntil = System.currentTimeMillis() + 2000;
		updateSessionLine(totalSessionXp());
		Timer restore = new Timer(2100, e -> updateSessionLine(totalSessionXp()));
		restore.setRepeats(false);
		restore.start();
	}

	// ---- Wrapped and export ------------------------------------------------------

	private List<MonsterTracker.Snapshot> allTimeMonsters()
	{
		return plugin.getMonsterTracker(MonsterRange.ALL_TIME)
			.snapshot(config.lootPrice(), MonsterTracker.itemMatcher(config.ignoredItems()), config.rareDropValue());
	}

	private void showWrapped()
	{
		WrappedStats stats = WrappedStats.of(allTimeMonsters(), plugin.getSlayerTasks(), config.lootPrice());
		WrappedDialog.show(this, stats, plugin.getAccountName());
	}

	// ---- Dialogs ----------------------------------------------------------------

	private void confirmReset()
	{
		Object[] options = {"This session", "Everything", "Cancel"};
		int choice = JOptionPane.showOptionDialog(this,
			"What should be reset?\n\n"
				+ "This session: damage stats, XP rates and this session's monsters.\n"
				+ "Everything: also every monster remembered for this account.\n\n"
				+ "Your goals and colours are always kept.",
			"Reset tracker", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (choice == 0)
		{
			plugin.resetTracker(false);
		}
		else if (choice == 1)
		{
			plugin.resetTracker(true);
		}
	}


	/**
	 * Whether a card belongs to the current slayer task: by its own name, or for boss
	 * groups (e.g. Royal Titans) by any member's name.
	 */
	private static boolean isTaskMonster(String task, String cardName)
	{
		if (task == null)
		{
			return false;
		}
		if (SlayerTaskMatcher.matches(task, cardName))
		{
			return true;
		}
		for (BossGroups group : BossGroups.values())
		{
			if (group.getDisplayName().equals(cardName))
			{
				for (String member : group.getMembers())
				{
					if (SlayerTaskMatcher.matches(task, member))
					{
						return true;
					}
				}
			}
		}
		return false;
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
