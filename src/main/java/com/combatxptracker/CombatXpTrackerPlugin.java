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

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Combat & XP Tracker",
	description = "Tracks average damage, max hit, and XP/hr with per-skill goal levels",
	tags = {"combat", "damage", "dps", "xp", "experience", "tracker", "goals"}
)
public class CombatXpTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private CombatXpTrackerConfig config;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CombatXpTrackerOverlay overlay;

	private CombatXpTrackerPanel panel;
	private NavigationButton navButton;

	private final Map<Skill, SkillProgress> skillProgress = new EnumMap<>(Skill.class);
	private final HitStats hitStats = new HitStats();

	private static final String CONFIG_GROUP = "combatxptracker";
	private static final String GOAL_KEY_PREFIX = "goal.";
	private static final String SET_GOAL_MENU_OPTION = "Set goal level";

	@Provides
	CombatXpTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CombatXpTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			SkillProgress progress = new SkillProgress();
			progress.setGoalLevel(loadGoalLevel(skill));
			skillProgress.put(skill, progress);
		}

		panel = new CombatXpTrackerPanel(this, config, skillIconManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/combatxptracker/icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Combat & XP Tracker")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		overlayManager.add(overlay);

		clientThread.invokeLater(this::initializeCurrentSkillLevels);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		skillProgress.clear();
		hitStats.reset();
	}

	private void initializeCurrentSkillLevels()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			int xp = client.getSkillExperience(skill);
			int level = client.getRealSkillLevel(skill);
			SkillProgress progress = skillProgress.get(skill);
			if (progress != null)
			{
				progress.recordXp(xp, level, System.currentTimeMillis(), config.xpHrIntervalSeconds());
				// Goals were loaded and clamped in startUp() against the default
				// currentLevel=1 placeholder, since real levels aren't available until
				// this deferred client-thread call. Re-clamp now that recordXp() above
				// has set the real level, so a stale saved goal below the player's
				// actual level gets corrected instead of silently staying too low.
				progress.setGoalLevel(progress.getGoalLevel());
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN && config.resetHitsOnLogout())
		{
			hitStats.reset();
			if (panel != null)
			{
				SwingUtilities.invokeLater(() -> panel.refresh());
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == Skill.OVERALL)
		{
			return;
		}

		SkillProgress progress = skillProgress.computeIfAbsent(skill, s -> new SkillProgress());
		progress.recordXp(event.getXp(), event.getLevel(), System.currentTimeMillis(), config.xpHrIntervalSeconds());

		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refresh());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// Damage the local player DEALS lands on the target (an NPC in almost all PvM
		// cases, or another Player in PvP) and is tagged with a "_ME" hitsplat type.
		// Damage the local player TAKES lands directly on the player actor instead.
		// We only want damage dealt BY the player, and we want it whether the target
		// is an NPC or a Player, so we key off Hitsplat.isMine() rather than the actor's type.
		// isMine() correctly includes BLOCK_ME (a blocked/0-damage hit is still "my" hit)
		// alongside all DAMAGE_ME/DAMAGE_MAX_ME color variants.
		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine())
		{
			return;
		}

		int damage = hitsplat.getAmount();
		if (damage == 0 && !config.showZeroHits())
		{
			return;
		}

		hitStats.recordHit(damage);

		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refresh());
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Add a "Set goal level" right-click option on skill icons in the stats tab.
		//
		// I confirmed WidgetInfo.SKILLS_CONTAINER exists in current mainline RuneLite
		// (it wraps InterfaceID.Stats.UNIVERSE), but the whole WidgetInfo enum is marked
		// @Deprecated in favor of InterfaceID/gameval constants, so this uses the
		// underlying InterfaceID.Stats.UNIVERSE constant directly instead.
		//
		// UNVERIFIED: the child-index-to-skill ORDERING below (skillFromWidgetChildIndex)
		// is an assumption on my part, not something I confirmed against the actual Stats
		// interface's child layout — I could not compile or run this against a live client
		// jar in this environment (repo.runelite.net is outside my sandbox's network
		// allowlist). Before relying on this, open the in-game Widget Inspector
		// (Ctrl+Shift+I in RuneLite dev mode) on the skills tab, hover each skill icon, and
		// read off its actual child index to confirm or correct the ordering below. The
		// core xptracker/skillcalculator plugins are the best reference if this needs fixing.
		if (event.getParam1() != InterfaceID.Stats.UNIVERSE)
		{
			return;
		}

		String option = event.getOption();
		if (!"View guide".equalsIgnoreCase(option) && !"View hiscores".equalsIgnoreCase(option))
		{
			return;
		}

		Skill skill = skillFromWidgetChildIndex(event.getActionParam());
		if (skill == null)
		{
			return;
		}

		client.createMenuEntry(-1)
			.setOption(SET_GOAL_MENU_OPTION)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptSetGoalLevel(skill));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.RUNELITE)
		{
			return;
		}
		if (!SET_GOAL_MENU_OPTION.equals(event.getMenuOption()))
		{
			return;
		}
		// Handled via the onClick consumer set in onMenuEntryAdded; nothing further needed here.
	}

	private Skill skillFromWidgetChildIndex(int childIndex)
	{
		// This ordering follows the skills tab's conventional 3-column visual layout
		// (Attack/Hitpoints/Mining, Strength/Agility/Smithing, ...) as it's appeared in
		// OSRS for years. It is NOT confirmed against any RuneLite source constant — I
		// could not verify it against a live client in this environment. Use the Widget
		// Inspector to confirm before relying on this row-to-skill mapping.
		Skill[] orderedSkills = {
			Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
			Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
			Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
			Skill.RANGED, Skill.THIEVING, Skill.COOKING,
			Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
			Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
			Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
			Skill.CONSTRUCTION, Skill.HUNTER
		};

		if (childIndex < 0 || childIndex >= orderedSkills.length)
		{
			return null;
		}
		return orderedSkills[childIndex];
	}

	/**
	 * Opens a lightweight prompt for the user to type a goal level for the given skill.
	 * The actual input dialog lives in the panel/UI layer since it needs Swing access.
	 */
	private void promptSetGoalLevel(Skill skill)
	{
		SwingUtilities.invokeLater(() -> panel.promptGoalLevelDialog(skill));
	}

	public void setGoalLevel(Skill skill, int level)
	{
		SkillProgress progress = skillProgress.computeIfAbsent(skill, s -> new SkillProgress());
		progress.setGoalLevel(level);
		saveGoalLevel(skill, level);
		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refresh());
		}
	}

	private int loadGoalLevel(Skill skill)
	{
		String stored = configManager.getConfiguration(CONFIG_GROUP, GOAL_KEY_PREFIX + skill.getName());
		if (stored == null)
		{
			return config.defaultGoalLevel();
		}
		try
		{
			return Integer.parseInt(stored);
		}
		catch (NumberFormatException e)
		{
			return config.defaultGoalLevel();
		}
	}

	private void saveGoalLevel(Skill skill, int level)
	{
		configManager.setConfiguration(CONFIG_GROUP, GOAL_KEY_PREFIX + skill.getName(), String.valueOf(level));
	}

	public Map<Skill, SkillProgress> getSkillProgress()
	{
		return skillProgress;
	}

	public HitStats getHitStats()
	{
		return hitStats;
	}

	public void resetHitStats()
	{
		hitStats.reset();
	}
}
