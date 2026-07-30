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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
	name = "Combat & XP Tracker",
	description = "Tracks average damage, biggest hit, and XP/hr for the skills you set goals on",
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
	private InfoBoxManager infoBoxManager;

	@Inject
	private CombatXpTrackerOverlay overlay;

	// One infobox per goal-tracked skill, so they can be added/removed individually as
	// goals are set and cleared.
	private final Map<Skill, GoalInfoBox> infoBoxes = new EnumMap<>(Skill.class);

	// Panel refreshes are throttled: hitsplats and XP drops both fire many times per
	// second in combat, and rebuilding every skill row on each one made the whole panel
	// visibly flicker. We coalesce those into at most one refresh per interval.
	private static final long PANEL_REFRESH_INTERVAL_MS = 600;
	private long lastPanelRefreshMillis = 0;
	private boolean panelRefreshPending = false;

	private CombatXpTrackerPanel panel;
	private NavigationButton navButton;

	private final Map<Skill, SkillProgress> skillProgress = new EnumMap<>(Skill.class);
	private final HitStats hitStats = new HitStats();
	private final CombinedDropTracker combinedDropTracker = new CombinedDropTracker();
	private final SessionSummary sessionSummary = new SessionSummary();

	private static final String CONFIG_GROUP = "combatxptracker";
	private static final String GOAL_KEY_PREFIX = "goal.";
	private static final String COLOR_KEY_PREFIX = "color.";
	private static final String SET_GOAL_MENU_OPTION = "Set goal level";

	@Provides
	CombatXpTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CombatXpTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Create a SkillProgress for every skill, but only mark as goal-tracked the ones
		// with a previously saved goal. Deliberately NOT seeding XP here: at the login
		// screen client.getSkillExperience() returns 0 for everything, and storing that
		// zero as a baseline is what produced rates like "295,554,650 xp/hr". Real
		// baselines are established in onGameStateChanged when we reach LOGGED_IN.
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			SkillProgress progress = new SkillProgress();
			Integer savedGoal = loadSavedGoalLevel(skill);
			if (savedGoal != null)
			{
				progress.restoreSavedGoal(savedGoal);
			}
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

		syncInfoBoxes();

		// If the plugin is toggled on while already logged in, GameStateChanged won't
		// fire, so baseline immediately in that case.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::rebaselineAllSkills);
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		removeAllInfoBoxes();
		skillProgress.clear();
		hitStats.reset();
		combinedDropTracker.reset();
		sessionSummary.reset();
	}

	/**
	 * Throws away any accumulated XP samples and re-seeds every skill from the real,
	 * logged-in values. Called on login so a stale or zeroed baseline can never leak into
	 * the rate calculation.
	 */
	private void rebaselineAllSkills()
	{
		long now = System.currentTimeMillis();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			SkillProgress progress = skillProgress.get(skill);
			if (progress == null)
			{
				continue;
			}
			progress.resetBaseline(
				client.getSkillExperience(skill),
				client.getRealSkillLevel(skill),
				now);
		}
		requestPanelRefresh();
	}

	/**
	 * Adds an infobox for every goal-tracked skill and removes them for skills that are
	 * no longer tracked, so the set on screen always matches the set of active goals.
	 */
	private void syncInfoBoxes()
	{
		if (!config.showInfobox())
		{
			removeAllInfoBoxes();
			return;
		}

		for (Map.Entry<Skill, SkillProgress> entry : skillProgress.entrySet())
		{
			Skill skill = entry.getKey();
			boolean shouldShow = entry.getValue().isGoalSet();
			boolean isShowing = infoBoxes.containsKey(skill);

			if (shouldShow && !isShowing)
			{
				BufferedImage skillImage = skillIconManager.getSkillImage(skill, true);
				GoalInfoBox box = new GoalInfoBox(skillImage, this, skill);
				infoBoxes.put(skill, box);
				infoBoxManager.addInfoBox(box);
			}
			else if (!shouldShow && isShowing)
			{
				infoBoxManager.removeInfoBox(infoBoxes.remove(skill));
			}
		}
	}

	private void removeAllInfoBoxes()
	{
		for (GoalInfoBox box : infoBoxes.values())
		{
			infoBoxManager.removeInfoBox(box);
		}
		infoBoxes.clear();
	}

	/**
	 * Requests a panel refresh, coalescing rapid-fire requests into at most one per
	 * PANEL_REFRESH_INTERVAL_MS. In combat, hitsplat and XP events can each fire several
	 * times per second; refreshing on every one of them rebuilt all the skill rows
	 * constantly and made the panel visibly flicker.
	 */
	private void requestPanelRefresh()
	{
		if (panel == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastPanelRefreshMillis >= PANEL_REFRESH_INTERVAL_MS)
		{
			lastPanelRefreshMillis = now;
			panelRefreshPending = false;
			SwingUtilities.invokeLater(panel::refresh);
			return;
		}

		// Too soon: schedule one trailing refresh so the final state isn't lost, but
		// don't stack up more than one pending.
		if (!panelRefreshPending)
		{
			panelRefreshPending = true;
			long delay = PANEL_REFRESH_INTERVAL_MS - (now - lastPanelRefreshMillis);
			javax.swing.Timer trailing = new javax.swing.Timer((int) delay, e ->
			{
				lastPanelRefreshMillis = System.currentTimeMillis();
				panelRefreshPending = false;
				panel.refresh();
			});
			trailing.setRepeats(false);
			trailing.start();
		}
	}

	/**
	 * The largest XP gain we'll accept from a single StatChanged event. The biggest
	 * legitimate single drop in OSRS is far below this; anything larger means we were
	 * comparing against a bad baseline (typically a zero captured before login) rather
	 * than seeing a real gain, so it's discarded instead of poisoning the rate and the
	 * session total.
	 */
	private static final int MAX_PLAUSIBLE_XP_DELTA = 200_000;

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("showInfobox".equals(event.getKey()))
		{
			syncInfoBoxes();
		}
		requestPanelRefresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			// Re-seed every skill from real values. This is the fix for XP/hr showing
			// hundreds of millions: without it, the zero baseline captured before login
			// gets compared against the player's full lifetime XP.
			clientThread.invokeLater(this::rebaselineAllSkills);
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			// Drop stale samples so a logout->login cycle can't span the gap and count
			// the offline time as training time.
			for (SkillProgress progress : skillProgress.values())
			{
				progress.reset();
			}

			if (config.resetHitsOnLogout())
			{
				hitStats.reset();
				combinedDropTracker.reset();
			}
			requestPanelRefresh();
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

		// Ignore XP events that arrive before we're properly logged in -- their values
		// aren't trustworthy and they're what corrupted the baseline previously.
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		SkillProgress progress = skillProgress.computeIfAbsent(skill, s -> new SkillProgress());
		boolean hadBaseline = progress.hasRecordedSample();
		int previousXp = progress.getCurrentXp();
		int delta = event.getXp() - previousXp;

		if (hadBaseline && delta > MAX_PLAUSIBLE_XP_DELTA)
		{
			// Implausible jump: treat it as a baseline correction, not a gain. Reset the
			// baseline to this value so subsequent rates are computed from solid ground.
			progress.resetBaseline(event.getXp(), event.getLevel(), System.currentTimeMillis());
			requestPanelRefresh();
			return;
		}

		progress.recordXp(event.getXp(), event.getLevel(), System.currentTimeMillis(), config.xpHrIntervalSeconds());

		if (hadBaseline && delta > 0)
		{
			sessionSummary.recordXpGain(skill, delta);
		}

		requestPanelRefresh();
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
		combinedDropTracker.recordHit(damage, System.currentTimeMillis());

		// Determine the monster name for the session summary's "biggest hit" readout, if
		// the hit landed on a named NPC. Confirmed pattern (Actor instanceof NPC, then
		// getName()) matches real core plugins (CorpPlugin, IdleNotifierPlugin).
		// Actor.getName() is @Nullable per its own Javadoc, so this can legitimately be
		// null (unnamed NPC, or a player target) -- recorded as-is rather than guessed at.
		String monsterName = null;
		if (event.getActor() instanceof NPC)
		{
			monsterName = event.getActor().getName();
		}
		sessionSummary.recordHit(damage, monsterName);

		requestPanelRefresh();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Add a "Set goal level" right-click option on skill icons in the stats tab.
		//
		// CONFIRMED against the RuneLite CI build (plugin-hub PR #14262) and the official
		// API Javadoc: MenuEntryAdded exposes getActionParam0()/getActionParam1(), not
		// getParam0()/getParam1() (that was the old, since-removed method naming still
		// floating around in some outdated third-party examples) and not getActionParam()
		// (never existed as a no-arg method at all). Fixed to the confirmed-correct names.
		//
		// I also confirmed WidgetInfo.SKILLS_CONTAINER exists in current mainline RuneLite
		// (it wraps InterfaceID.Stats.UNIVERSE), but the whole WidgetInfo enum is marked
		// @Deprecated in favor of InterfaceID/gameval constants, so this uses the
		// underlying InterfaceID.Stats.UNIVERSE constant directly instead.
		//
		// CORRECTION: an earlier version of this comment claimed the child-index-to-skill
		// mapping was "confirmed working in practice" based on a user screenshot. That was
		// wrong -- the screenshot actually showed the menu WITHOUT this entry, meaning the
		// feature had never worked. Two attempted fixes (option-text matching, then a
		// contains() check) both failed. The diagnostic logging below exists to establish
		// the real widget IDs instead of guessing a third time.
		//
		// NOTE ON "Add to canvas": the core (built-in) XP Tracker plugin adds its own
		// separate "Add to canvas [Skill]" option to this same menu. That text belongs to
		// net.runelite.client.plugins.xptracker, a different plugin entirely -- this
		// plugin cannot rename or modify another plugin's menu text.
		final int actionParam0 = event.getActionParam0();
		final int actionParam1 = event.getActionParam1();

		if (config.debugMenuLogging())
		{
			log.info("[CombatXpTracker] menu option='{}' target='{}' actionParam0={} actionParam1={} "
					+ "group(param1>>16)={} child(param1&0xFFFF)={} InterfaceID.Stats.UNIVERSE={}",
				event.getOption(), event.getTarget(), actionParam0, actionParam1,
				actionParam1 >>> 16, actionParam1 & 0xFFFF, InterfaceID.Stats.UNIVERSE);
		}

		// Accept a match whether InterfaceID.Stats.UNIVERSE is a bare interface/group ID
		// or a packed component ID (group << 16 | child). Comparing only the raw values
		// fails silently if the two are in different forms, which is the most likely
		// reason this never fired.
		final int paramGroup = actionParam1 >>> 16;
		final int statsGroup = InterfaceID.Stats.UNIVERSE >>> 16;
		final boolean inStatsTab =
			actionParam1 == InterfaceID.Stats.UNIVERSE
				|| paramGroup == InterfaceID.Stats.UNIVERSE
				|| paramGroup == statsGroup;

		if (!inStatsTab)
		{
			return;
		}

		// Deliberately NOT matching on option text. Two previous attempts keyed off the
		// menu strings ("View guide", then contains("guide")) and both failed silently.
		// Being in the stats tab and resolving to a real skill is sufficient, and the
		// dedupe below stops us adding the entry once per native menu option.
		Skill skill = skillFromWidgetChildIndex(actionParam0);
		if (skill == null)
		{
			if (config.debugMenuLogging())
			{
				log.info("[CombatXpTracker] in stats tab but child index {} did not map to a skill", actionParam0);
			}
			return;
		}

		// MenuEntryAdded fires once per existing native option, so without this we'd add
		// "Set goal level" several times for a single right-click.
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (SET_GOAL_MENU_OPTION.equals(entry.getOption()))
			{
				return;
			}
		}

		if (config.debugMenuLogging())
		{
			log.info("[CombatXpTracker] adding 'Set goal level' for {}", skill.getName());
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

		// A newly goal-tracked skill starts from now, not from whatever stale samples
		// happen to be sitting in the deque.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> progress.resetBaseline(
				client.getSkillExperience(skill),
				client.getRealSkillLevel(skill),
				System.currentTimeMillis()));
		}

		syncInfoBoxes();
		requestPanelRefresh();
	}

	/**
	 * Stops tracking a skill: removes its goal, its saved config entry, and its infobox.
	 */
	public void clearGoal(Skill skill)
	{
		SkillProgress progress = skillProgress.get(skill);
		if (progress != null)
		{
			progress.clearGoal();
		}
		configManager.unsetConfiguration(CONFIG_GROUP, GOAL_KEY_PREFIX + skill.getName());
		syncInfoBoxes();
		requestPanelRefresh();
	}

	/**
	 * @return the saved goal level for this skill, or null if the player has never set
	 * one. Null is meaningful here: it's what keeps an untouched skill out of the panel
	 * and overlay entirely, rather than silently defaulting everything to 99.
	 */
	private Integer loadSavedGoalLevel(Skill skill)
	{
		String stored = configManager.getConfiguration(CONFIG_GROUP, GOAL_KEY_PREFIX + skill.getName());
		if (stored == null)
		{
			return null;
		}
		try
		{
			return Integer.parseInt(stored);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private void saveGoalLevel(Skill skill, int level)
	{
		configManager.setConfiguration(CONFIG_GROUP, GOAL_KEY_PREFIX + skill.getName(), String.valueOf(level));
	}

	/**
	 * Returns the user's saved color for a skill's row in the panel, or null if they
	 * haven't set one (in which case the panel falls back to its default styling).
	 */
	public Color getSkillColor(Skill skill)
	{
		String stored = configManager.getConfiguration(CONFIG_GROUP, COLOR_KEY_PREFIX + skill.getName());
		if (stored == null)
		{
			return null;
		}
		try
		{
			return new Color(Integer.parseInt(stored), true);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	public void setSkillColor(Skill skill, Color color)
	{
		if (color == null)
		{
			configManager.unsetConfiguration(CONFIG_GROUP, COLOR_KEY_PREFIX + skill.getName());
		}
		else
		{
			configManager.setConfiguration(CONFIG_GROUP, COLOR_KEY_PREFIX + skill.getName(), String.valueOf(color.getRGB()));
		}
		requestPanelRefresh();
	}

	public Map<Skill, SkillProgress> getSkillProgress()
	{
		return skillProgress;
	}

	public HitStats getHitStats()
	{
		return hitStats;
	}

	public CombinedDropTracker getCombinedDropTracker()
	{
		return combinedDropTracker;
	}

	public SessionSummary getSessionSummary()
	{
		return sessionSummary;
	}

	public void resetHitStats()
	{
		hitStats.reset();
		combinedDropTracker.reset();
	}
}
