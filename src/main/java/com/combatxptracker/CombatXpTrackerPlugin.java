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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.NPCManager;
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
	 * How many ticks an XP drop's combat style is trusted for when labelling hits. Long
	 * enough to cover a slow ranged or magic projectile landing after the XP drop.
	 */
	private static final int XP_STYLE_MEMORY_TICKS = 10;

	/**
	 * An NPC counts as a kill if it dies within this many ticks (30s) of the player's
	 * last hit on it.
	 */
	private static final int KILL_CREDIT_TICKS = 50;

	/** How often (in ticks) changed monster stats are saved: every 30 seconds. */
	private static final int SAVE_INTERVAL_TICKS = 50;

	private static final String MONSTERS_KEY = "monsters";

	/**
	 * A task that disappears with at most this many kills left finished through kills.
	 * One that disappears with more was cancelled or skipped.
	 */
	private static final int TASK_FINISH_MAX_REMAINING = 10;

	/** The option some bosses use to hand out their reward, e.g. the Royal Titans. */
	private static final String LOOT_OPTION = "Loot";
	/** How long (3.6s) to wait after a "Loot" click for the reward to arrive. */
	private static final int LOOT_WINDOW_TICKS = 6;

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
	private MaxHitCalculator maxHitCalculator;

	@Inject
	private NPCManager npcManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	private final Map<Skill, SkillProgress> skillProgress = new EnumMap<>(Skill.class);
	private final Map<Skill, GoalInfoBox> infoBoxes = new EnumMap<>(Skill.class);
	private final HitStats hitStats = new HitStats();
	private final CombinedDropTracker combinedDropTracker = new CombinedDropTracker();
	// All-time stats (remembered per account) and this session's stats.
	private final MonsterTracker monsterTracker = new MonsterTracker();
	private final MonsterTracker sessionMonsters = new MonsterTracker();

	private CombatXpTrackerPanel panel;
	private NavigationButton navButton;

	// Client-thread only.
	private boolean panelDirty;
	// Written on the client thread, read by goal cards for "kills left".
	private volatile CombatStyle.AttackStyle attackStyle;
	// The last monster the player hit and its hitpoints, for "kills left" on goals.
	private volatile String lastTargetName;
	private volatile int lastTargetHitpoints;
	private final SlayerTaskSession taskSession = new SlayerTaskSession();
	private CombatStyle lastXpStyle;
	private int lastXpStyleTick;
	private String maxHitKey;
	// NPCs the player hit recently -> tick of the last hit, for counting kills.
	private final Map<NPC, Integer> engagedNpcs = new HashMap<>();
	// NPCs already counted as a kill on death -> tick, so their loot doesn't count again.
	private final Map<NPC, Integer> countedKills = new HashMap<>();
	private int savedMonsterRevision = -1;
	private int ticksSinceSave;
	// A "Loot" click on a boss (e.g. the Royal Titans) waiting for its reward to arrive
	// in the inventory: which NPC, when, and what the inventory held at the click.
	private String pendingLootNpc;
	private int pendingLootTick;
	private Map<Integer, Long> pendingLootBefore;
	// Multi-NPC bosses: last tick a member's death counted as a kill, so e.g. both Royal
	// Titans dying in one fight count once.
	private final Map<BossGroups, Integer> lastGroupKillTick = new EnumMap<>(BossGroups.class);

	// Current slayer task for the Monsters tab, read each tick. Null/0 without a task.
	private volatile String slayerTaskName;
	private volatile int slayerTaskRemaining;

	// Written on the client thread, read by the panel and overlay.
	private volatile MaxHitCalculator.Result maxHitResult;

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

		// Turned on while already logged in: GameStateChanged and RuneScapeProfileChanged
		// won't fire, so baseline and load now.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				loadMonsters();
				onLoggedIn();
			});
		}
	}

	@Override
	protected void shutDown()
	{
		saveMonsters();
		engagedNpcs.clear();
		countedKills.clear();
		pendingLootNpc = null;
		pendingLootBefore = null;
		savedMonsterRevision = -1;
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		removeAllInfoBoxes();
		skillProgress.clear();
		hitStats.reset();
		combinedDropTracker.reset();
		monsterTracker.reset();
		sessionMonsters.reset();
		lastGroupKillTick.clear();
		taskSession.clear();
		lastTargetName = null;
		lastTargetHitpoints = 0;
		slayerTaskName = null;
		slayerTaskRemaining = 0;
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
			engagedNpcs.clear();
			countedKills.clear();
			pendingLootNpc = null;
			pendingLootBefore = null;
			if (config.resetHitsOnLogout())
			{
				// Only this session's stats; what's remembered all-time is kept.
				hitStats.reset();
				combinedDropTracker.reset();
				sessionMonsters.reset();
			}
			saveMonsters();
			refreshPanelNow();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Each account keeps its own Monsters tab. The previous account's stats were
		// already saved on logout.
		loadMonsters();
		refreshPanelNow();
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
		if ("rememberMonsters".equals(event.getKey()))
		{
			// Turning it off forgets what was saved; turning it on saves right away.
			savedMonsterRevision = -1;
			clientThread.invokeLater(this::saveMonsters);
		}
		refreshPanelNow();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		refreshCombatState();
		pruneKillTracking();
		if (pendingLootNpc != null && client.getTickCount() - pendingLootTick > LOOT_WINDOW_TICKS)
		{
			// Nothing arrived (e.g. "Loot" gave nothing, or the inventory was full).
			pendingLootNpc = null;
			pendingLootBefore = null;
		}
		if (++ticksSinceSave >= SAVE_INTERVAL_TICKS)
		{
			ticksSinceSave = 0;
			saveMonsters();
		}
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

		boolean wasReached = progress.isGoalReached();
		if (!progress.recordXp(xp, System.currentTimeMillis(), config.xpHrIntervalSeconds()))
		{
			// An impossible jump (e.g. from a 0 reported while logging in): SkillProgress
			// started over from this value, session included. Not a real gain.
			ensureGoalStart(skill, progress);
			panelDirty = true;
			return;
		}
		ensureGoalStart(skill, progress);

		// Only announce a real gain crossing the goal, not the first value after login.
		if (hadBaseline && !wasReached && progress.isGoalReached())
		{
			announceGoalReached(skill, progress.getGoal());
		}

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
			NPC npc = (NPC) target;
			String rawName = cleanName(npc.getName());
			String name = BossGroups.trackedName(rawName);
			CombatStyle style = resolveHitStyle();
			monsterTracker.recordHit(name, damage, style, now);
			sessionMonsters.recordHit(name, damage, style, now);
			engagedNpcs.put(npc, client.getTickCount());

			Integer hitpoints = npcManager.getHealth(npc.getId());
			if (rawName != null && hitpoints != null && hitpoints > 0)
			{
				lastTargetName = rawName;
				lastTargetHitpoints = hitpoints;
			}
			if (taskSession.isTaskMonster(rawName))
			{
				taskSession.recordHit(damage, now);
			}
		}

		panelDirty = true;
	}

	/**
	 * Counts a kill when an NPC the player recently hit dies. Unlike counting drops, this
	 * also catches kills that drop nothing.
	 */
	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) event.getActor();
		if (engagedNpcs.remove(npc) == null)
		{
			return;
		}
		String name = cleanName(npc.getName());
		if (name != null)
		{
			countedKills.put(npc, client.getTickCount());
			countKill(name, System.currentTimeMillis());
		}
	}

	/**
	 * Adds a kill, handling multi-NPC bosses: minions (e.g. Nex's) don't count, and
	 * members dying together (e.g. both Royal Titans) count once.
	 */
	private void countKill(String npcName, long now)
	{
		if (!BossGroups.countsAsKill(npcName))
		{
			return;
		}
		BossGroups group = BossGroups.of(npcName);
		if (group != null && group.getSameKillTicks() > 0)
		{
			int tick = client.getTickCount();
			Integer last = lastGroupKillTick.get(group);
			if (last != null && tick - last <= group.getSameKillTicks())
			{
				return;
			}
			lastGroupKillTick.put(group, tick);
		}
		String tracked = BossGroups.trackedName(npcName);
		monsterTracker.recordKill(tracked, now);
		sessionMonsters.recordKill(tracked, now);
		if (taskSession.isTaskMonster(npcName))
		{
			taskSession.recordKill(now);
		}
		panelDirty = true;
	}

	/**
	 * Some bosses don't drop loot on death. The Royal Titans, for example, give their
	 * reward when you pick "Loot" on them afterwards, straight into the inventory, and
	 * RuneLite's NpcLootReceived never fires for it. So remember the click and what the
	 * inventory held, and record what gets added in the next few ticks.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc == null || !LOOT_OPTION.equals(Text.removeTags(entry.getOption())))
		{
			return;
		}
		String name = cleanName(npc.getName());
		if (name == null)
		{
			return;
		}
		pendingLootNpc = BossGroups.trackedName(name);
		pendingLootTick = client.getTickCount();
		pendingLootBefore = inventoryCounts(client.getItemContainer(InventoryID.INV));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || pendingLootNpc == null)
		{
			return;
		}

		Map<Integer, Long> after = inventoryCounts(event.getItemContainer());
		Map<Integer, Long> gained = InventoryDiff.gained(pendingLootBefore, after);
		if (gained.isEmpty())
		{
			// Something was used or dropped meanwhile; keep waiting from the new state.
			pendingLootBefore = after;
			return;
		}

		String name = pendingLootNpc;
		// Only the first change counts, so later pickups or withdrawals are never
		// mistaken for boss loot.
		pendingLootNpc = null;
		pendingLootBefore = null;

		if (config.trackMonsterLoot())
		{
			List<MonsterTracker.Drop> drops = new ArrayList<>();
			for (Map.Entry<Integer, Long> e : gained.entrySet())
			{
				drops.add(drop(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue())));
			}
			long now = System.currentTimeMillis();
			monsterTracker.recordLoot(name, drops, now);
			sessionMonsters.recordLoot(name, drops, now);
			recordTaskLoot(name, drops, now);
			panelDirty = true;
		}
	}

	/**
	 * Item id -> total quantity, with noted items counted as their normal item so they
	 * price and stack the same way as regular drops.
	 */
	private Map<Integer, Long> inventoryCounts(ItemContainer container)
	{
		Map<Integer, Long> counts = new HashMap<>();
		if (container == null)
		{
			return counts;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				counts.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
			}
		}
		return counts;
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		// Walked away or despawned without dying. Kills already counted are kept until
		// pruned, because the loot event can arrive after the despawn.
		engagedNpcs.remove(event.getNpc());
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

		long now = System.currentTimeMillis();
		// The death normally counted the kill already. If it didn't (e.g. the death
		// happened off-screen), the loot still proves one.
		if (countedKills.remove(npc) == null)
		{
			countKill(name, now);
		}

		if (config.trackMonsterLoot())
		{
			List<MonsterTracker.Drop> drops = new ArrayList<>();
			for (ItemStack stack : event.getItems())
			{
				// Canonicalize so noted drops are priced and stacked as the normal item.
				drops.add(drop(itemManager.canonicalize(stack.getId()), stack.getQuantity()));
			}
			String tracked = BossGroups.trackedName(name);
			monsterTracker.recordLoot(tracked, drops, now);
			sessionMonsters.recordLoot(tracked, drops, now);
			recordTaskLoot(name, drops, now);
		}
		panelDirty = true;
	}

	private void recordTaskLoot(String npcName, List<MonsterTracker.Drop> drops, long now)
	{
		if (!taskSession.isTaskMonster(npcName))
		{
			return;
		}
		long ge = 0;
		long ha = 0;
		for (MonsterTracker.Drop d : drops)
		{
			ge += d.totalValue(LootPrice.GRAND_EXCHANGE);
			ha += d.totalValue(LootPrice.HIGH_ALCHEMY);
		}
		taskSession.recordLoot(ge, ha, now);
	}

	/**
	 * A drop with both its GE price and High Alchemy value, so the price setting can be
	 * switched without re-recording anything. Must run on the client thread.
	 */
	private MonsterTracker.Drop drop(int itemId, int quantity)
	{
		String name = itemManager.getItemComposition(itemId).getName();
		long ha = itemId == ItemID.COINS ? 1 : itemManager.getItemComposition(itemId).getHaPrice();
		return new MonsterTracker.Drop(itemId, name, quantity, itemManager.getItemPrice(itemId), ha);
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

		attackStyle = maxHitCalculator.readAttackStyle();
		String task = maxHitCalculator.readSlayerTaskName();
		int remaining = task == null ? 0 : client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (!java.util.Objects.equals(task, slayerTaskName) || remaining != slayerTaskRemaining)
		{
			onSlayerTaskUpdate(slayerTaskName, slayerTaskRemaining, task, remaining);
			slayerTaskName = task;
			slayerTaskRemaining = remaining;
			panelDirty = true;
		}
		if (!config.showMeleeMaxHit())
		{
			return;
		}

		// Recalculated once per tick rather than per frame in the overlay. Gear, prayers,
		// boosts, style and task can all change between ticks, but not within one.
		MaxHitCalculator.Result result = maxHitCalculator.calculate(attackStyle, task);
		String key = result == null ? null : result.toString();
		if (key != null && !key.equals(maxHitKey))
		{
			maxHitKey = key;
			maxHitResult = result;
			panelDirty = true;
		}
	}

	/**
	 * Starts tracking a new task, and posts the summary when one finishes.
	 */
	private void onSlayerTaskUpdate(String oldTask, int oldRemaining, String newTask, int newRemaining)
	{
		boolean finished = oldTask != null && newTask == null
			// A task that ran out through kills, not one cancelled at a slayer master.
			&& oldRemaining <= TASK_FINISH_MAX_REMAINING && taskSession.isActive() && taskSession.getKills() > 0;
		if (finished && config.taskSummary())
		{
			String message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(taskSession.summary(config.lootPrice()))
				.build();
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(message)
				.build());
		}

		if (newTask == null)
		{
			taskSession.clear();
		}
		else if (!newTask.equals(oldTask) || newRemaining > oldRemaining || !taskSession.isActive())
		{
			// A new task (a different monster, or the count went up again).
			taskSession.start(newTask);
		}
	}

	/**
	 * Forgets NPCs that weren't hit for a while (no kill credit) and old counted kills
	 * whose loot never came.
	 */
	private void pruneKillTracking()
	{
		int tick = client.getTickCount();
		engagedNpcs.values().removeIf(lastHit -> tick - lastHit > KILL_CREDIT_TICKS);
		countedKills.values().removeIf(counted -> tick - counted > KILL_CREDIT_TICKS);
	}

	private void announceGoalReached(Skill skill, Goal goal)
	{
		String skillName = Formatting.capitalize(skill.getName());
		String what = goal.getType() == Goal.Type.LEVEL
			? "level " + goal.getTargetLevel() + " " + skillName
			: goal.getShortLabel() + " " + skillName + " xp";

		notifier.notify(config.goalNotification(), "Goal reached: " + what + "!");

		if (config.goalChatMessage())
		{
			String message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Goal reached: ")
				.append(ChatColorType.NORMAL)
				.append("you hit " + what + "!")
				.build();
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(message)
				.build());
		}
	}

	// ---- Remembering monsters between sessions ---------------------------------------

	/**
	 * Loads the current account's saved monsters, or starts empty. Called when the
	 * RuneLite profile switches to a (possibly different) account.
	 */
	private void loadMonsters()
	{
		List<MonsterTracker.Saved> saved = null;
		if (config.rememberMonsters() && configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, MONSTERS_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					MonsterTracker.Saved[] parsed = gson.fromJson(json, MonsterTracker.Saved[].class);
					saved = parsed == null ? null : Arrays.asList(parsed);
				}
				catch (JsonParseException e)
				{
					// Corrupt data: start fresh rather than break the plugin.
					saved = null;
				}
			}
		}
		monsterTracker.importState(saved);
		// 1.5.x saved each Royal Titan (etc.) separately and had no High Alchemy values.
		monsterTracker.rename(BossGroups::trackedName);
		monsterTracker.fillMissingHaPrices(id -> id == ItemID.COINS ? 1 : itemManager.getItemComposition(id).getHaPrice());
		engagedNpcs.clear();
		countedKills.clear();
		savedMonsterRevision = monsterTracker.getRevision();
	}

	/**
	 * Saves the monsters to the current account's profile if they changed since the last
	 * save. Cheap to call often.
	 */
	private void saveMonsters()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		int revision = monsterTracker.getRevision();
		if (revision == savedMonsterRevision)
		{
			return;
		}
		savedMonsterRevision = revision;

		if (!config.rememberMonsters() || monsterTracker.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, MONSTERS_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, MONSTERS_KEY,
			gson.toJson(monsterTracker.exportState()));
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
	 * Clears this session's damage stats, monsters and XP rates. Goals and colours are
	 * kept.
	 *
	 * @param allTimeToo also forget every remembered monster for this account
	 */
	public void resetTracker(boolean allTimeToo)
	{
		hitStats.reset();
		combinedDropTracker.reset();
		sessionMonsters.reset();
		if (allTimeToo)
		{
			monsterTracker.reset();
		}
		for (SkillProgress progress : skillProgress.values())
		{
			progress.resetSession();
		}
		refreshPanelNow();
	}

	public void removeMonster(String name)
	{
		monsterTracker.remove(name);
		sessionMonsters.remove(name);
		refreshPanelNow();
	}

	/**
	 * Leaves an item out of every loot grid and loot value, like the Loot Tracker's
	 * ignore list.
	 */
	public void ignoreItem(String itemName)
	{
		Set<String> ignored = MonsterTracker.parseNames(config.ignoredItems());
		if (ignored.add(itemName.toLowerCase()))
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "ignoredItems", String.join(", ", ignored));
		}
	}

	public void setMonsterRange(MonsterRange range)
	{
		configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "monsterRange", range);
	}

	public boolean isCollapsed(String monsterName)
	{
		return MonsterTracker.parseNames(config.collapsedMonsters()).contains(monsterName.toLowerCase());
	}

	/**
	 * Remembers whether a monster card is collapsed, so it stays that way after a
	 * restart.
	 */
	public boolean isPinned(String monsterName)
	{
		return MonsterTracker.parseNames(config.pinnedMonsters()).contains(monsterName.toLowerCase());
	}

	/**
	 * Pins a monster to the top of the Monsters tab, whatever the sort order.
	 */
	public void setPinned(String monsterName, boolean pinned)
	{
		Set<String> names = MonsterTracker.parseNames(config.pinnedMonsters());
		boolean changed = pinned ? names.add(monsterName.toLowerCase()) : names.remove(monsterName.toLowerCase());
		if (changed)
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "pinnedMonsters", String.join(", ", names));
		}
	}

	public void setCollapsed(String monsterName, boolean collapsed)
	{
		Set<String> names = MonsterTracker.parseNames(config.collapsedMonsters());
		boolean changed = collapsed ? names.add(monsterName.toLowerCase()) : names.remove(monsterName.toLowerCase());
		if (changed)
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "collapsedMonsters", String.join(", ", names));
		}
	}

	/**
	 * Hides a monster from the Monsters tab. Its stats are still tracked, so unhiding
	 * brings them back.
	 */
	public void hideMonster(String name)
	{
		Set<String> hidden = MonsterTracker.parseNames(config.hiddenMonsters());
		if (hidden.add(name.toLowerCase()))
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "hiddenMonsters", String.join(", ", hidden));
		}
	}

	public void unhideAllMonsters()
	{
		configManager.unsetConfiguration(CombatXpTrackerConfig.GROUP, "hiddenMonsters");
	}

	public void setMonsterSort(MonsterSort sort)
	{
		configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "monsterSort", sort);
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

	public MonsterTracker getMonsterTracker(MonsterRange range)
	{
		return range == MonsterRange.SESSION ? sessionMonsters : monsterTracker;
	}

	/**
	 * @return the current slayer task's name, or null without a task
	 */
	public String getSlayerTaskName()
	{
		return slayerTaskName;
	}

	/**
	 * @return the selected attack style, or null before it's known
	 */
	public CombatStyle.AttackStyle getAttackStyle()
	{
		return attackStyle;
	}

	/**
	 * @return the last monster the player hit, or null
	 */
	public String getLastTargetName()
	{
		return lastTargetName;
	}

	public int getLastTargetHitpoints()
	{
		return lastTargetHitpoints;
	}

	public int getSlayerTaskRemaining()
	{
		return slayerTaskRemaining;
	}

	public XpRateMode getXpRateMode()
	{
		return config.xpRateMode();
	}

	/**
	 * @return the latest max hit, or null before the first calculation
	 */
	public MaxHitCalculator.Result getMaxHitResult()
	{
		return maxHitResult;
	}
}
