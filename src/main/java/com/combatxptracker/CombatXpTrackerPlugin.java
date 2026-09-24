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

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Combat & XP Tracker",
	description = "Skill goals with XP/hr, damage and max hit tracking, and per-monster hits and drops",
	tags = {"combat", "damage", "dps", "xp", "experience", "tracker", "goals", "loot", "max hit", "slayer"}
)
public class CombatXpTrackerPlugin extends Plugin
{
	private static final String GOAL_KEY_PREFIX = "goal.";
	private static final String GOAL_START_KEY_PREFIX = "goalstart.";
	private static final String COLOR_KEY_PREFIX = "color.";
	private static final String SET_GOAL_MENU_OPTION = "Set goal";

	/**
	 * The largest XP gain accepted from one StatChanged event. Anything bigger means the
	 * previous value was a bad baseline (e.g. a 0 from before login), not a real gain.
	 */
	private static final int MAX_PLAUSIBLE_XP_DELTA = 200_000;

	/**
	 * How many ticks an XP drop's combat style is trusted for when labelling hits. Long
	 * enough to cover a slow ranged or magic projectile landing after the XP drop.
	 */
	private static final int XP_STYLE_MEMORY_TICKS = 10;

	// Skill name embedded in the stats tab's own menu options, e.g.
	// "View <col=ff981f>Attack</col> guide". The core XP Tracker reads it the same way.
	private static final Pattern SKILL_MENU_OPTION_PATTERN =
		Pattern.compile("^View\\s+(.+?)\\s+(guide|hiscores)$", Pattern.CASE_INSENSITIVE);

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
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private CombatXpTrackerOverlay overlay;

	@Inject
	private MeleeMaxHitCalculator meleeMaxHitCalculator;

	private final Map<Skill, SkillProgress> skillProgress = new EnumMap<>(Skill.class);
	private final Map<Skill, GoalInfoBox> infoBoxes = new EnumMap<>(Skill.class);
	private final HitStats hitStats = new HitStats();
	private final CombinedDropTracker combinedDropTracker = new CombinedDropTracker();
	private final MonsterTracker monsterTracker = new MonsterTracker();

	private CombatXpTrackerPanel panel;
	private NavigationButton navButton;

	// Client-thread only.
	private boolean panelDirty;
	private CombatStyle.AttackStyle attackStyle;
	private CombatStyle lastXpStyle;
	private int lastXpStyleTick;
	private String maxHitKey;

	// Written on the client thread, read by the panel and overlay.
	private volatile MeleeMaxHitCalculator.Result maxHitResult;

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
			SkillProgress progress = new SkillProgress();
			Goal goal = Goal.deserialize(configManager.getConfiguration(CombatXpTrackerConfig.GROUP, GOAL_KEY_PREFIX + skill.getName()));
			if (goal != null)
			{
				progress.setGoal(goal, loadGoalStart(skill));
			}
			skillProgress.put(skill, progress);
		}

		panel = new CombatXpTrackerPanel(this, config, skillIconManager, itemManager);

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

		// Turned on while already logged in: GameStateChanged won't fire, so baseline now.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::onLoggedIn);
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
		monsterTracker.reset();
		maxHitResult = null;
		maxHitKey = null;
		attackStyle = null;
		lastXpStyle = null;
		panelDirty = false;
		panel = null;
		navButton = null;
	}

	// ---- Events ------------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// Deferred so the client's skill values are populated first.
			clientThread.invokeLater(this::onLoggedIn);
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			// Drop samples so offline time never counts as training time.
			for (SkillProgress progress : skillProgress.values())
			{
				progress.reset();
			}
			if (config.resetHitsOnLogout())
			{
				hitStats.reset();
				combinedDropTracker.reset();
				monsterTracker.reset();
			}
			refreshPanelNow();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CombatXpTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("showInfobox".equals(event.getKey()))
		{
			syncInfoBoxes();
		}
		refreshPanelNow();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		refreshCombatState();
		// Panel updates are batched to at most one per tick. Hitsplats and XP drops can
		// fire several times a tick in combat, and refreshing on each made the panel flicker.
		if (panelDirty)
		{
			panelDirty = false;
			refreshPanelNow();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		SkillProgress progress = skillProgress.get(skill);
		if (progress == null)
		{
			return;
		}

		boolean hadBaseline = progress.hasRecordedSample();
		int xp = event.getXp();
		int delta = xp - progress.getCurrentXp();

		if (hadBaseline && delta > MAX_PLAUSIBLE_XP_DELTA)
		{
			// Implausible jump: treat as a baseline correction, not a gain.
			progress.resetBaseline(xp, System.currentTimeMillis());
			panelDirty = true;
			return;
		}

		progress.recordXp(xp, System.currentTimeMillis(), config.xpHrIntervalSeconds());
		ensureGoalStart(skill, progress);

		if (hadBaseline && delta > 0)
		{
			CombatStyle style = CombatStyle.fromXpSkill(skill);
			if (style != null)
			{
				lastXpStyle = style;
				lastXpStyleTick = client.getTickCount();
			}
		}

		panelDirty = true;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// Only damage the local player dealt. isMine() covers every "_ME" hitsplat,
		// including blocked 0s, whether the target is an NPC or a player.
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

		long now = System.currentTimeMillis();
		hitStats.recordHit(damage);
		combinedDropTracker.recordHit(damage, now);

		Actor target = event.getActor();
		if (target instanceof NPC)
		{
			monsterTracker.recordHit(cleanName(target.getName()), damage, resolveHitStyle(), now);
		}

		panelDirty = true;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		String name = npc == null ? null : cleanName(npc.getName());
		if (name == null)
		{
			return;
		}

		List<MonsterTracker.Drop> drops = new ArrayList<>();
		if (config.trackMonsterLoot())
		{
			for (ItemStack stack : event.getItems())
			{
				// Canonicalize so noted drops are priced and stacked as the normal item.
				int id = itemManager.canonicalize(stack.getId());
				String itemName = itemManager.getItemComposition(id).getName();
				drops.add(new MonsterTracker.Drop(id, itemName, stack.getQuantity(), itemManager.getItemPrice(id)));
			}
		}
		monsterTracker.recordKill(name, drops, System.currentTimeMillis());
		panelDirty = true;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Adds "Set goal" to a skill's right-click menu in the stats tab. It only opens
		// this plugin's dialog; nothing is sent to the game server.
		String option = event.getOption();
		if (option == null)
		{
			return;
		}

		Matcher matcher = SKILL_MENU_OPTION_PATTERN.matcher(Text.removeTags(option).trim());
		if (!matcher.matches())
		{
			return;
		}

		Skill skill;
		try
		{
			skill = Skill.valueOf(matcher.group(1).trim().toUpperCase());
		}
		catch (IllegalArgumentException e)
		{
			return;
		}

		// "guide" and "hiscores" both match, so only add the entry once.
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (SET_GOAL_MENU_OPTION.equals(entry.getOption()))
			{
				return;
			}
		}

		client.getMenu().createMenuEntry(-1)
			.setOption(SET_GOAL_MENU_OPTION)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> SwingUtilities.invokeLater(() ->
			{
				CombatXpTrackerPanel p = panel;
				if (p != null)
				{
					p.promptGoalDialog(skill);
				}
			}));
	}

	// ---- Client-thread helpers -------------------------------------------------

	private void onLoggedIn()
	{
		long now = System.currentTimeMillis();
		for (Map.Entry<Skill, SkillProgress> entry : skillProgress.entrySet())
		{
			SkillProgress progress = entry.getValue();
			progress.resetBaseline(client.getSkillExperience(entry.getKey()), now);
			ensureGoalStart(entry.getKey(), progress);
		}
		refreshCombatState();
		refreshPanelNow();
	}

	/**
	 * Goals saved by 1.3.x have no start XP. Give them one the first time real XP is
	 * known: the start of the current level, so the bar isn't empty right after updating.
	 */
	private void ensureGoalStart(Skill skill, SkillProgress progress)
	{
		if (!progress.isGoalSet() || progress.getGoalStartXp() >= 0 || !progress.isXpKnown())
		{
			return;
		}
		int xp = progress.getCurrentXp();
		int start = Math.min(xp, Experience.getXpForLevel(Experience.getLevelForXp(xp)));
		progress.setGoalStartXp(start);
		saveGoalStart(skill, start);
	}

	private void refreshCombatState()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		attackStyle = meleeMaxHitCalculator.readAttackStyle();
		if (!config.showMeleeMaxHit())
		{
			return;
		}

		// Recalculated once per tick rather than per frame in the overlay. Gear, prayers,
		// boosts, style and task can all change between ticks, but not within one.
		MeleeMaxHitCalculator.Result result = meleeMaxHitCalculator.calculate(
			attackStyle, meleeMaxHitCalculator.readSlayerTaskName());
		String key = result == null ? null : result.toString();
		if (key != null && !key.equals(maxHitKey))
		{
			maxHitKey = key;
			maxHitResult = result;
			panelDirty = true;
		}
	}

	/**
	 * The style a hit came from: the latest combat XP drop if it was recent (this catches
	 * spells cast manually while holding a melee weapon), otherwise the selected style.
	 */
	private CombatStyle resolveHitStyle()
	{
		if (lastXpStyle != null && client.getTickCount() - lastXpStyleTick <= XP_STYLE_MEMORY_TICKS)
		{
			return lastXpStyle;
		}
		return attackStyle != null ? attackStyle.getStyle() : null;
	}

	private static String cleanName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String cleaned = Text.removeTags(name).replace(' ', ' ').trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

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
				GoalInfoBox box = new GoalInfoBox(skillIconManager.getSkillImage(skill, true), this, skill);
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

	private void refreshPanelNow()
	{
		CombatXpTrackerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	// ---- Called from the panel (Swing thread) -----------------------------------

	/**
	 * Sets a goal. Its progress bar starts from the player's XP right now.
	 */
	public void setGoal(Skill skill, Goal goal)
	{
		clientThread.invokeLater(() ->
		{
			SkillProgress progress = skillProgress.get(skill);
			if (progress == null)
			{
				return;
			}
			int start = progress.isXpKnown() ? progress.getCurrentXp() : -1;
			progress.setGoal(goal, start);
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, GOAL_KEY_PREFIX + skill.getName(), goal.serialize());
			if (start >= 0)
			{
				saveGoalStart(skill, start);
			}
			else
			{
				configManager.unsetConfiguration(CombatXpTrackerConfig.GROUP, GOAL_START_KEY_PREFIX + skill.getName());
			}
			syncInfoBoxes();
			refreshPanelNow();
		});
	}

	public void clearGoal(Skill skill)
	{
		clientThread.invokeLater(() ->
		{
			SkillProgress progress = skillProgress.get(skill);
			if (progress != null)
			{
				progress.clearGoal();
			}
			configManager.unsetConfiguration(CombatXpTrackerConfig.GROUP, GOAL_KEY_PREFIX + skill.getName());
			configManager.unsetConfiguration(CombatXpTrackerConfig.GROUP, GOAL_START_KEY_PREFIX + skill.getName());
			syncInfoBoxes();
			refreshPanelNow();
		});
	}

	/**
	 * Clears damage stats, monsters and XP rates. Goals and colours are kept.
	 */
	public void resetTracker()
	{
		hitStats.reset();
		combinedDropTracker.reset();
		monsterTracker.reset();
		for (SkillProgress progress : skillProgress.values())
		{
			progress.resetSession();
		}
		refreshPanelNow();
	}

	public void removeMonster(String name)
	{
		monsterTracker.remove(name);
		refreshPanelNow();
	}

	private int loadGoalStart(Skill skill)
	{
		String stored = configManager.getConfiguration(CombatXpTrackerConfig.GROUP, GOAL_START_KEY_PREFIX + skill.getName());
		if (stored == null)
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(stored);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	private void saveGoalStart(Skill skill, int startXp)
	{
		configManager.setConfiguration(CombatXpTrackerConfig.GROUP, GOAL_START_KEY_PREFIX + skill.getName(), String.valueOf(startXp));
	}

	/**
	 * @return the player's chosen bar colour for this skill, or null for the default
	 */
	public Color getSkillColor(Skill skill)
	{
		String stored = configManager.getConfiguration(CombatXpTrackerConfig.GROUP, COLOR_KEY_PREFIX + skill.getName());
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
			configManager.unsetConfiguration(CombatXpTrackerConfig.GROUP, COLOR_KEY_PREFIX + skill.getName());
		}
		else
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, COLOR_KEY_PREFIX + skill.getName(), String.valueOf(color.getRGB()));
		}
		refreshPanelNow();
	}

	// ---- Read access for the panel, overlay and infoboxes --------------------------

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

	public MonsterTracker getMonsterTracker()
	{
		return monsterTracker;
	}

	/**
	 * @return the latest max hit, or null before the first calculation
	 */
	public MeleeMaxHitCalculator.Result getMaxHitResult()
	{
		return maxHitResult;
	}
}
