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
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.StatChanged;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
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
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Combat & XP Tracker",
	description = "Skill goals with XP/hr, kills to level, damage and max hit tracking, per-monster hits and drops, boss kill summaries, a Slayer tab and an attack timer",
	tags = {"combat", "damage", "dps", "xp", "experience", "tracker", "goals", "loot", "max hit", "slayer", "kills to level", "boss", "slayer points", "superior", "attack timer"}
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
	private static final String SLAYER_LOG_KEY = "slayerLog";
	// The streak and points update a moment after the task count reaches 0.
	private static final int TASK_RECORD_DELAY_TICKS = 2;
	// Next-task odds are re-read this often, to pick up new levels and unlocks.
	private static final int ODDS_REFRESH_TICKS = 100;
	private static final String BLOCKED_KEY_PREFIX = "blockedTasks.";
	private static final String AREAS_KEY = "slayerAreas";
	private static final String BANK_KEY = "bankItems";
	private static final String PRAYER_KEY = "prayerDrain";
	private static final String COLLECTION_KEY = "collectionLog";
	// Prayer drained within this many ticks (6s) of hitting a monster counts towards it.
	private static final int FIGHT_TICKS = 10;
	// Hits this soon after a special attack (6 ticks, for projectiles) don't count for luck.
	private static final int SPEC_LUCK_TICKS = 6;
	// Weapons whose hits don't roll evenly from 0 to the max hit, or hit more than once.
	private static final String[] UNEVEN_WEAPONS = {"scythe of vitur", "dual macuahuitl", "venator bow",
		"osmumten's fang", "keris", "ahrim's", "dharok's", "karil's", "tonalztics", "gadderhammer"};
	private static final Set<Integer> RUNE_POUCHES = Set.of(ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH);
	private static final int[] POUCH_TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
	private static final int[] POUCH_AMOUNTS = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5,
		VarbitID.RUNE_POUCH_QUANTITY_6};
	// Eating/drinking and shield blocks: animations that aren't attacks.
	private static final Set<Integer> NON_ATTACK_ANIMATIONS = Set.of(829, 424, 1156, 388, 403, 404, 410, 420, 425);

	/**
	 * A task that disappears with at most this many kills left finished through kills.
	 * One that disappears with more was cancelled or skipped.
	 */
	private static final int TASK_FINISH_MAX_REMAINING = 10;

	/** The option some bosses use to hand out their reward, e.g. the Royal Titans. */
	private static final String LOOT_OPTION = "Loot";
	/** How long (3.6s) to wait after a "Loot" click for the reward to arrive. */
	private static final int LOOT_WINDOW_TICKS = 6;
	// A boss kill and its kill count message arrive close together. Multi-NPC bosses (the
	// Royal Titans) send the message when the last one dies, so allow a while after the first.
	private static final int KILL_COUNT_WINDOW_TICKS = 100;
	private static final int KILL_COUNT_EARLY_TICKS = 10;
	// Fights you haven't hit for this long are over.
	private static final int FIGHT_TIMEOUT_TICKS = 200;
	// Order of the "Kills to level" rows.
	private static final Skill[] FORECAST_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED,
		Skill.MAGIC, Skill.HITPOINTS, Skill.SLAYER};

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
	private AttackTimerOverlay attackTimerOverlay;

	@Inject
	private LootDespawnOverlay lootDespawnOverlay;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ImageCapture imageCapture;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private SlayerGameData slayerGameData;

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
	private final XpPerKill xpPerKill = new XpPerKill();
	private final SlayerLog slayerLog = new SlayerLog();
	private int savedSlayerLogRevision = -1;
	// Read from the game each tick, for the Slayer tab.
	private volatile SlayerStatus slayerStatus = SlayerStatus.NONE;
	// A finished task waiting a moment for the game to update the streak and points.
	private SlayerTaskRecord pendingTaskRecord;
	private SlayerPoints.Master pendingTaskMaster;
	private int pendingTaskRecordTicks;
	private final AttackTimer attackTimer = new AttackTimer();
	// Task guide and next-task odds for the Slayer tab, read from the game's tables.
	private volatile SlayerGuide slayerGuide = SlayerGuide.NONE;
	private volatile TaskOdds taskOdds = TaskOdds.NONE;
	private String oddsKey;
	private int oddsAgeTicks;
	// Where slayer areas are, learned from where the player fights (per account).
	private final SlayerAreaMemory areaMemory = new SlayerAreaMemory();
	private int savedAreaRevision = -1;
	// Lower-case names of what was in the bank when it was last open, or null if never seen.
	private volatile Set<String> bankNames;
	private String savedBank;
	// "Where to get a task", when the player asked for one.
	private volatile TaskSources taskSources = TaskSources.NONE;
	// Every task name, for picking one to look up. Read once.
	private volatile List<String> allTaskNames = Collections.emptyList();
	// The player's own monster drops on the ground, for the despawn alert.
	private final GroundLoot groundLoot = new GroundLoot();
	private volatile GroundLoot.Status groundLootStatus;
	// Prayer drain per monster, and what's being fought right now.
	private final PrayerDrain prayerDrain = new PrayerDrain();
	private int savedPrayerRevision = -1;
	private int lastPrayerPoints = -1;
	private String fightMonster;
	private int lastFightTick = Integer.MIN_VALUE / 2;
	private volatile int prayerLevel = 1;
	// The casts left infobox, while autocasting.
	private CastsInfoBox castsInfoBox;
	private RuneSupply.Rune castsRune;
	private boolean castsLowNotified;
	// Luck of this session's hits, and the last special attack (its hits don't count).
	private final HitLuck hitLuck = new HitLuck();
	private int lastSpecEnergy = -1;
	private int lastSpecTick = Integer.MIN_VALUE / 2;
	// Every master's task chances and the player's levels, for "best master for you".
	private volatile Map<SlayerPoints.Master, TaskOdds> allMasterOdds = Collections.emptyMap();
	private volatile int combatLevel;
	private volatile int slayerLevel;
	// Milestone messages, and the account's name for Gielinor Wrapped.
	private final Milestones milestones = new Milestones();
	private volatile String accountName;
	// What the player has from each collection log page they've opened.
	private final CollectionLog collectionLog = new CollectionLog();
	private int savedCollectionRevision = -1;
	private volatile int attackSpeedTicks = AttackTimer.UNARMED_TICKS;
	// The monster XP is currently being pooled for, so XP from another monster isn't mixed in.
	private String xpTarget;
	// Boss fight logs by fight key (a boss group, or one NPC), for the kill summary.
	private final Map<String, BossFight> fights = new HashMap<>();
	private BossFight pendingKillFight;
	private String pendingKillKey;
	private int pendingKillTick;
	private int lastKillCount = -1;
	private int lastKillCountTick;
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

		Theme.apply(config.panelTheme());
		buildPanel();
		overlayManager.add(overlay);
		overlayManager.add(attackTimerOverlay);
		overlayManager.add(lootDespawnOverlay);
		syncInfoBoxes();

		// Turned on while already logged in: GameStateChanged and RuneScapeProfileChanged
		// won't fire, so baseline and load now.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				loadMonsters();
				loadSlayerLog();
				loadSlayerExtras();
				onLoggedIn();
			});
		}
	}

	@Override
	protected void shutDown()
	{
		saveMonsters();
		saveSlayerLog();
		saveAreaMemory();
		savePrayerDrain();
		overlayManager.remove(attackTimerOverlay);
		overlayManager.remove(lootDespawnOverlay);
		groundLoot.clear();
		groundLootStatus = null;
		removeCastsBox();
		castsLowNotified = false;
		prayerDrain.importState(null);
		savedPrayerRevision = -1;
		lastPrayerPoints = -1;
		fightMonster = null;
		collectionLog.importState(null);
		savedCollectionRevision = -1;
		attackTimer.reset();
		slayerLog.importState(null);
		savedSlayerLogRevision = -1;
		slayerStatus = SlayerStatus.NONE;
		slayerGuide = SlayerGuide.NONE;
		taskOdds = TaskOdds.NONE;
		oddsKey = null;
		areaMemory.importState(null);
		savedAreaRevision = -1;
		bankNames = null;
		savedBank = null;
		taskSources = TaskSources.NONE;
		allTaskNames = Collections.emptyList();
		pendingTaskRecord = null;
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
		hitLuck.reset();
		allMasterOdds = Collections.emptyMap();
		combinedDropTracker.reset();
		monsterTracker.reset();
		sessionMonsters.reset();
		lastGroupKillTick.clear();
		taskSession.clear();
		xpPerKill.clear();
		xpTarget = null;
		clearFights();
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
		if (state == GameState.LOADING || state == GameState.HOPPING)
		{
			// The scene is rebuilt; ground items are seen again as they reappear, but can't
			// be matched to a kill any more.
			groundLoot.clear();
			groundLootStatus = null;
		}
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
			clearFights();
			xpPerKill.clearPending();
			attackTimer.reset();
			saveSlayerLog();
			saveAreaMemory();
			savePrayerDrain();
			groundLoot.clear();
			groundLootStatus = null;
			lastPrayerPoints = -1;
			if (config.resetHitsOnLogout())
			{
				// Only this session's stats; what's remembered all-time is kept.
				hitStats.reset();
				hitLuck.reset();
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
		loadSlayerLog();
		loadSlayerExtras();
		// Another account's blocked tasks.
		oddsKey = null;
		taskSources = TaskSources.NONE;
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
		if ("panelTheme".equals(event.getKey()))
		{
			SwingUtilities.invokeLater(this::rebuildPanelForTheme);
			return;
		}
		if ("castsLeft".equals(event.getKey()) && !config.castsLeft())
		{
			clientThread.invokeLater(this::removeCastsBox);
		}
		if ("checkBank".equals(event.getKey()) && !config.checkBank())
		{
			clientThread.invokeLater(this::forgetBank);
		}
		if ("rememberMonsters".equals(event.getKey()))
		{
			// Turning it off forgets what was saved; turning it on saves right away.
			savedMonsterRevision = -1;
			clientThread.invokeLater(this::saveMonsters);
		}
		refreshPanelNow();
	}

	/**
	 * Creates the sidebar panel and its toolbar button.
	 */
	private void buildPanel()
	{
		panel = new CombatXpTrackerPanel(this, config, skillIconManager, itemManager);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/combatxptracker/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Combat & XP Tracker")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	/**
	 * A new theme: components take their colours when they're created, so the panel is
	 * built again, and reopened if it was open.
	 */
	private void rebuildPanelForTheme()
	{
		if (panel == null)
		{
			return;
		}
		boolean wasOpen = panel.isShowing();
		clientToolbar.removeNavigation(navButton);
		Theme.apply(config.panelTheme());
		buildPanel();
		if (wasOpen)
		{
			clientToolbar.openPanel(navButton);
		}
		refreshPanelNow();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		refreshCombatState();
		pruneKillTracking();
		pruneFights();
		finishPendingTaskRecord();
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
			saveSlayerLog();
			saveAreaMemory();
			savePrayerDrain();
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			updateGroundLoot();
			trackPrayer();
			updateCasts();
			int energy = client.getVarpValue(VarPlayerID.SA_ENERGY);
			if (lastSpecEnergy >= 0 && energy < lastSpecEnergy)
			{
				lastSpecTick = client.getTickCount();
			}
			lastSpecEnergy = energy;
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
			recordKillXp(skill, delta);
			if (taskSession.isActive() && XpPerKill.COMBAT_SKILLS.contains(skill))
			{
				taskSession.recordXp(skill == Skill.SLAYER, delta);
			}
			if (skill != Skill.HITPOINTS && skill != Skill.SLAYER && XpPerKill.COMBAT_SKILLS.contains(skill)
				&& isFromCurrentStyle(skill))
			{
				// Combat XP comes the moment you attack.
				attackTimer.onAttackSignal(client.getTickCount(), attackSpeedTicks);
			}
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
		if (event.getActor() == client.getLocalPlayer())
		{
			// Damage taken, not dealt. The hit plays a block animation the attack timer
			// shouldn't take for an attack.
			attackTimer.onHitTaken(client.getTickCount());
			return;
		}
		if (!hitsplat.isMine())
		{
			return;
		}

		if (event.getActor() instanceof NPC)
		{
			fightMonster = BossGroups.trackedName(cleanName(((NPC) event.getActor()).getName()));
			lastFightTick = client.getTickCount();
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
			if (name != null && !name.equals(xpTarget))
			{
				// XP pooled so far was for another monster.
				xpPerKill.clearPending();
				xpTarget = name;
			}

			Integer hitpoints = npcManager.getHealth(npc.getId());
			if (name != null)
			{
				int tick = client.getTickCount();
				fights.computeIfAbsent(fightKey(npc, rawName), k -> new BossFight(name, tick))
					.recordHit(npc.getIndex(), hitpoints == null ? 0 : hitpoints, damage, tick);
			}
			if (rawName != null && hitpoints != null && hitpoints > 0)
			{
				lastTargetName = rawName;
				lastTargetHitpoints = hitpoints;
			}
			if (taskSession.isTaskMonster(rawName))
			{
				taskSession.recordHit(damage, now);
			}
			if (damage > 0)
			{
				hitLuck.record(damage, luckMaxHit(rawName, style));
				announceMilestone(milestones.onHit(damage, name, style));
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
			if (BossGroups.countsAsKill(name))
			{
				String key = fightKey(npc, name);
				BossFight fight = fights.get(key);
				if (fight != null && pendingKillFight == null)
				{
					pendingKillFight = fight;
					pendingKillKey = key;
					pendingKillTick = client.getTickCount();
					postKillSummaryIfReady();
				}
			}
		}
	}

	/**
	 * Kill count messages mark a boss kill: "Your Vorkath kill count is: 124."
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String text = Text.removeTags(event.getMessage());
		if (SuperiorMonsters.SPAWN_MESSAGE.equals(text))
		{
			if (taskSession.isActive())
			{
				taskSession.recordSuperior();
			}
			notifier.notify(config.superiorNotification(), "A superior foe has appeared!");
			panelDirty = true;
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		if (collectionLog.onNewItem(text))
		{
			saveCollectionLog();
			panelDirty = true;
		}
		int killCount = BossFight.parseKillCount(text);
		if (killCount > 0)
		{
			lastKillCount = killCount;
			lastKillCountTick = client.getTickCount();
			postKillSummaryIfReady();
		}
	}

	/**
	 * Posts the boss kill summary once both the kill and its kill count message are in,
	 * whichever came first.
	 */
	private void postKillSummaryIfReady()
	{
		if (pendingKillFight == null || lastKillCount <= 0)
		{
			return;
		}
		int gap = lastKillCountTick - pendingKillTick;
		if (gap < -KILL_COUNT_EARLY_TICKS || gap > KILL_COUNT_WINDOW_TICKS)
		{
			return;
		}
		if (config.killSummary())
		{
			String message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(pendingKillFight.summary(lastKillCount, Math.max(pendingKillTick, lastKillCountTick)))
				.build();
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(message)
				.build());
		}
		fights.remove(pendingKillKey);
		pendingKillFight = null;
		pendingKillKey = null;
		lastKillCount = -1;
	}

	/**
	 * Members of a boss group share one fight (both Royal Titans); anything else is one NPC.
	 */
	private static String fightKey(NPC npc, String npcName)
	{
		BossGroups group = BossGroups.of(npcName);
		return group != null ? "group:" + group.name() : "npc:" + npc.getIndex();
	}

	private void pruneFights()
	{
		int tick = client.getTickCount();
		fights.values().removeIf(fight -> tick - fight.getLastHitTick() > FIGHT_TIMEOUT_TICKS);
		if (pendingKillFight != null && tick - pendingKillTick > KILL_COUNT_WINDOW_TICKS)
		{
			// No kill count message came: not a boss. Forget it.
			fights.remove(pendingKillKey);
			pendingKillFight = null;
			pendingKillKey = null;
		}
		if (lastKillCount > 0 && tick - lastKillCountTick > KILL_COUNT_EARLY_TICKS && pendingKillFight == null)
		{
			lastKillCount = -1;
		}
	}

	private void clearFights()
	{
		fights.clear();
		pendingKillFight = null;
		pendingKillKey = null;
		lastKillCount = -1;
	}

	/**
	 * Pools a combat XP drop for the next kill. XP in a skill the current style doesn't
	 * train (e.g. Magic from High Alchemy while meleeing) isn't from the fight.
	 */
	private void recordKillXp(Skill skill, int delta)
	{
		if (isFromCurrentStyle(skill))
		{
			xpPerKill.recordXp(skill, delta);
		}
	}

	/**
	 * Whether XP in this skill can come from the selected attack style. Magic XP while
	 * meleeing is from something else, like High Alchemy.
	 */
	private boolean isFromCurrentStyle(Skill skill)
	{
		CombatStyle.AttackStyle style = attackStyle;
		CombatStyle trained = style == null ? null : style.getStyle();
		if (trained == null)
		{
			return true;
		}
		if (skill == Skill.ATTACK || skill == Skill.STRENGTH)
		{
			return trained == CombatStyle.MELEE;
		}
		if (skill == Skill.RANGED)
		{
			return trained == CombatStyle.RANGED;
		}
		if (skill == Skill.MAGIC)
		{
			return trained == CombatStyle.MAGIC;
		}
		return true;
	}

	/**
	 * The player starting an attack animation while fighting something.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local)
		{
			return;
		}
		int animation = local.getAnimation();
		if (animation == -1 || NON_ATTACK_ANIMATIONS.contains(animation))
		{
			return;
		}
		attackTimer.onAttackAnimation(client.getTickCount());
	}

	/**
	 * Confirms an attack animation that started before the player had a target.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		Player local = client.getLocalPlayer();
		if (local != null)
		{
			int animation = local.getAnimation();
			attackTimer.confirmPending(client.getTickCount(), local.getInteracting() != null,
				animation != -1 && !NON_ATTACK_ANIMATIONS.contains(animation), attackSpeedTicks);
		}
	}

	/**
	 * @return ticks until the player's next attack (0 = ready), or -1 when not fighting.
	 * Client thread only (the overlay).
	 */
	int getTicksUntilAttack()
	{
		return attackTimer.ticksLeft(client.getTickCount(), attackSpeedTicks);
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
		if (config.rememberMonsters())
		{
			// Without remembered monsters the counts restart every session.
			announceMilestone(Milestones.onKill(tracked, monsterTracker.getKills(tracked), monsterTracker.getTotalKills()));
		}
		xpPerKill.recordKill(tracked);
		prayerDrain.onKill(tracked, client.getTickCount());
		if (taskSession.isTaskMonster(npcName))
		{
			taskSession.recordKill(now);
			learnTaskArea();
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
		if (event.getContainerId() == InventoryID.BANK)
		{
			rememberBank();
			return;
		}
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

		matchGroundLoot(event);
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

		Player me = client.getLocalPlayer();
		if (me != null && me.getName() != null)
		{
			accountName = me.getName();
		}
		attackStyle = maxHitCalculator.readAttackStyle();
		attackSpeedTicks = maxHitCalculator.attackSpeed(attackStyle);
		String task = maxHitCalculator.readSlayerTaskName();
		int remaining = task == null ? 0 : client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		SlayerStatus status = maxHitCalculator.readSlayerStatus(task, remaining);
		if (!status.toString().equals(slayerStatus.toString()))
		{
			slayerStatus = status;
			panelDirty = true;
		}
		refreshSlayerGuide(status);
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
		if (finished && config.slayerLog())
		{
			pendingTaskRecord = taskSession.toRecord(System.currentTimeMillis(), -1);
			pendingTaskMaster = slayerStatus.getMaster();
			pendingTaskRecordTicks = TASK_RECORD_DELAY_TICKS;
		}
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
			SlayerStatus status = slayerStatus;
			SlayerPoints.Master master = status.getMaster();
			taskSession.setDetails(status.getAssigned(), master == null ? null : master.getDisplayName(), status.getLocation());
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
		milestones.setRecord(monsterTracker.getBiggestHit(), monsterTracker.getTotalHits());
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
		xpPerKill.clear();
		hitStats.reset();
		hitLuck.reset();
		combinedDropTracker.reset();
		sessionMonsters.reset();
		if (allTimeToo)
		{
			monsterTracker.reset();
			prayerDrain.importState(null);
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
		prayerDrain.remove(name);
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

	/**
	 * XP a skill gets per kill of the monster you last hit: measured from your XP drops once
	 * you've killed one, before that estimated from its hitpoints. -1 when unknown or the
	 * skill gets none (Slayer only counts on task).
	 */
	public double getXpPerKill(Skill skill)
	{
		String target = lastTargetName;
		if (target == null)
		{
			return -1;
		}
		String tracked = BossGroups.trackedName(target);
		double measured = xpPerKill.average(tracked, skill);
		if (measured >= 0)
		{
			return measured > 0 ? measured : -1;
		}
		if (skill == Skill.SLAYER && !isTargetOnTask())
		{
			return -1;
		}
		return KillXp.perKill(skill, attackStyle, lastTargetHitpoints);
	}

	/**
	 * @return how many kills of the last target the XP per kill is measured over
	 */
	public int getMeasuredKills()
	{
		String target = lastTargetName;
		return target == null ? 0 : xpPerKill.kills(BossGroups.trackedName(target));
	}

	public boolean isTargetOnTask()
	{
		String task = slayerTaskName;
		String target = lastTargetName;
		if (task == null || target == null)
		{
			return false;
		}
		if (SlayerTaskMatcher.matches(task, target))
		{
			return true;
		}
		BossGroups group = BossGroups.of(target);
		return group != null && SlayerTaskMatcher.matches(task, group.getDisplayName());
	}

	/**
	 * Kills of the last target to each combat skill's next level, in skill order.
	 */
	public List<LevelForecast.Row> getKillsToLevel()
	{
		if (lastTargetName == null)
		{
			return Collections.emptyList();
		}
		int measured = getMeasuredKills();
		List<LevelForecast.Row> rows = new ArrayList<>();
		for (Skill skill : FORECAST_SKILLS)
		{
			SkillProgress progress = skillProgress.get(skill);
			if (progress == null || !progress.isXpKnown())
			{
				continue;
			}
			LevelForecast.Row row = LevelForecast.killsToLevel(skill, progress.getCurrentXp(), getXpPerKill(skill),
				xpPerKill.average(BossGroups.trackedName(lastTargetName), skill) > 0 ? measured : 0);
			if (row != null)
			{
				rows.add(row);
			}
		}
		return rows;
	}

	/**
	 * What the rest of the slayer task gives each skill, when you're fighting the task's
	 * monster. Empty otherwise.
	 */
	public List<LevelForecast.TaskGain> getTaskForecast()
	{
		int remaining = slayerTaskRemaining;
		if (remaining <= 0 || !isTargetOnTask())
		{
			return Collections.emptyList();
		}
		List<LevelForecast.TaskGain> gains = new ArrayList<>();
		for (Skill skill : FORECAST_SKILLS)
		{
			SkillProgress progress = skillProgress.get(skill);
			if (progress == null || !progress.isXpKnown())
			{
				continue;
			}
			LevelForecast.TaskGain gain = LevelForecast.taskGain(skill, progress.getCurrentXp(), getXpPerKill(skill), remaining);
			if (gain != null && gain.getXp() > 0)
			{
				gains.add(gain);
			}
		}
		return gains;
	}

	// ---- Slayer tab -----------------------------------------------------------------------

	/**
	 * The task guide every tick (what you carry changes), the odds when the master changes
	 * or every so often.
	 */
	private void refreshSlayerGuide(SlayerStatus status)
	{
		SlayerGuide guide = slayerGameData.guide(status.getMaster(), status.getTask(), status.getLocation(),
			config.checkBank() ? bankNames : null);
		if (status.getLocation() != null && taskSession.isActive() && !status.getLocation().equals(taskSession.getArea()))
		{
			// Konar says where.
			taskSession.setArea(status.getLocation());
			panelDirty = true;
		}

		if (!guide.toString().equals(slayerGuide.toString()))
		{
			slayerGuide = guide;
			panelDirty = true;
		}

		String key = String.valueOf(status.getMaster());
		if (!key.equals(oddsKey) || ++oddsAgeTicks >= ODDS_REFRESH_TICKS)
		{
			oddsKey = key;
			oddsAgeTicks = 0;
			if (allTaskNames.isEmpty())
			{
				allTaskNames = Collections.unmodifiableList(slayerGameData.taskNames());
			}
			Player local = client.getLocalPlayer();
			combatLevel = local == null ? 0 : local.getCombatLevel();
			slayerLevel = client.getRealSkillLevel(Skill.SLAYER);
			if (status.getMaster() != null)
			{
				allMasterOdds = slayerGameData.allOdds(this::blockedTasks);
			}
			TaskOdds odds = slayerGameData.odds(status.getMaster(), blockedTasks(status.getMaster()));
			if (!odds.toString().equals(taskOdds.toString()))
			{
				taskOdds = odds;
				panelDirty = true;
			}
		}
	}

	/**
	 * Tasks the player marked as blocked with a master, saved per account.
	 */
	private Set<String> blockedTasks(SlayerPoints.Master master)
	{
		if (master == null || configManager.getRSProfileKey() == null)
		{
			return Collections.emptySet();
		}
		return MonsterTracker.parseNames(configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP,
			BLOCKED_KEY_PREFIX + master.name().toLowerCase(Locale.ROOT)));
	}

	/**
	 * Marks or unmarks a task as blocked with the current master, from the Slayer tab.
	 */
	public void setTaskBlocked(String task, boolean blocked)
	{
		clientThread.invokeLater(() ->
		{
			SlayerPoints.Master master = slayerStatus.getMaster();
			if (master == null || configManager.getRSProfileKey() == null)
			{
				return;
			}
			Set<String> tasks = new java.util.LinkedHashSet<>(blockedTasks(master));
			if (blocked)
			{
				tasks.add(task.toLowerCase(Locale.ROOT));
			}
			else
			{
				tasks.remove(task.toLowerCase(Locale.ROOT));
			}
			configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP,
				BLOCKED_KEY_PREFIX + master.name().toLowerCase(Locale.ROOT), String.join(", ", tasks));
			// Re-read the odds on the next tick.
			oddsKey = null;
		});
	}

	// ---- Loot despawn alert --------------------------------------------------------------------

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		int owner = item.getOwnership();
		if (owner != TileItem.OWNERSHIP_SELF && owner != TileItem.OWNERSHIP_GROUP)
		{
			return;
		}
		LocalPoint where = event.getTile().getLocalLocation();
		if (where == null)
		{
			return;
		}
		groundLoot.onSpawn(item, itemManager.canonicalize(item.getId()), where.getSceneX(), where.getSceneY(),
			event.getTile().getPlane(), client.getTickCount(), item.getDespawnTime());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundLoot.onDespawn(event.getItem());
	}

	/**
	 * Marks the items a monster just dropped for the player as loot to keep an eye on.
	 */
	private void matchGroundLoot(NpcLootReceived event)
	{
		Map<LocalPoint, Map<Integer, Long>> byTile = new HashMap<>();
		for (ItemStack stack : event.getItems())
		{
			if (stack.getLocation() == null)
			{
				continue;
			}
			int id = itemManager.canonicalize(stack.getId());
			long value = (long) itemManager.getItemPrice(id) * stack.getQuantity();
			byTile.computeIfAbsent(stack.getLocation(), k -> new HashMap<>()).merge(id, value, Long::sum);
		}
		int plane = event.getNpc() == null ? client.getTopLevelWorldView().getPlane() : event.getNpc().getWorldLocation().getPlane();
		for (Map.Entry<LocalPoint, Map<Integer, Long>> e : byTile.entrySet())
		{
			groundLoot.onLoot(e.getValue(), e.getKey().getSceneX(), e.getKey().getSceneY(), plane, client.getTickCount());
		}
	}

	/**
	 * Each tick: forgets loot that's gone, and warns when the oldest drop is about to go.
	 */
	private void updateGroundLoot()
	{
		int tick = client.getTickCount();
		groundLoot.prune(tick);
		Notification alert = config.lootDespawnAlert();
		if (!alert.isEnabled())
		{
			groundLootStatus = null;
			return;
		}
		long minValue = Math.max(0, config.lootDespawnMinValue());
		groundLootStatus = groundLoot.status(tick, minValue);
		int warnTicks = (int) Math.ceil(config.lootDespawnSeconds() / 0.6);
		if (groundLoot.shouldWarn(tick, warnTicks, minValue))
		{
			GroundLoot.Status status = groundLootStatus;
			int seconds = status == null ? config.lootDespawnSeconds() : (int) Math.round(status.getTicksLeft() * 0.6);
			notifier.notify(alert, "Your loot despawns in " + seconds + " seconds"
				+ (status == null ? "" : " (" + status.getItems() + (status.getItems() == 1 ? " drop)" : " drops)")));
		}
	}

	/**
	 * Read by the overlay.
	 */
	GroundLoot.Status getGroundLootStatus()
	{
		return groundLootStatus;
	}

	// ---- Prayer drain ---------------------------------------------------------------------------

	/**
	 * Each tick: prayer points lost while fighting count towards the monster being fought.
	 */
	private void trackPrayer()
	{
		int points = client.getBoostedSkillLevel(Skill.PRAYER);
		prayerLevel = client.getRealSkillLevel(Skill.PRAYER);
		int drained = lastPrayerPoints >= 0 ? Math.max(0, lastPrayerPoints - points) : 0;
		lastPrayerPoints = points;
		int tick = client.getTickCount();
		if (fightMonster != null && tick - lastFightTick <= FIGHT_TICKS && tick >= lastFightTick)
		{
			prayerDrain.onTick(fightMonster, drained, isPraying(), tick);
		}
	}

	private boolean isPraying()
	{
		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * e.g. "1 dose per 2:40", or null without enough fighting to tell.
	 */
	public String getPrayerDrainText(String monster)
	{
		PrayerDrain.Rate rate = prayerDrain.rate(monster);
		if (rate == null)
		{
			return null;
		}
		double minutes = PrayerDrain.minutesPerDose(rate, prayerLevel);
		if (minutes <= 0)
		{
			return null;
		}
		int seconds = (int) Math.round(minutes * 60);
		return String.format(Locale.US, "1 dose per %d:%02d", seconds / 60, seconds % 60);
	}

	/**
	 * @return prayer potions for the rest of the task at the player's measured drain, or -1
	 * if it isn't known yet
	 */
	public int getTaskPrayerPotions(String task, int killsLeft)
	{
		PrayerDrain.Rate rate = task == null || killsLeft <= 0 ? null : prayerDrain.rateForTask(task);
		return rate == null ? -1 : PrayerDrain.potionsFor(rate, killsLeft, prayerLevel);
	}

	/**
	 * Changes when a kill updates the prayer drain rates, for the panel.
	 */
	public int getPrayerDrainRevision()
	{
		return prayerDrain.getKillRevision();
	}

	private void savePrayerDrain()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		int revision = prayerDrain.getRevision();
		if (revision == savedPrayerRevision)
		{
			return;
		}
		savedPrayerRevision = revision;
		Map<String, PrayerDrain.Rate> state = prayerDrain.exportState();
		if (state.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, PRAYER_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, PRAYER_KEY, gson.toJson(state));
	}

	private void loadPrayerDrain()
	{
		Map<String, PrayerDrain.Rate> saved = null;
		if (configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, PRAYER_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					saved = gson.fromJson(json, new TypeToken<Map<String, PrayerDrain.Rate>>()
					{
					}.getType());
				}
				catch (JsonParseException e)
				{
					saved = null;
				}
			}
		}
		prayerDrain.importState(saved);
		savedPrayerRevision = prayerDrain.getRevision();
	}

	// ---- Casts left -------------------------------------------------------------------------------

	/**
	 * Each tick while autocasting: how many casts the runes allow.
	 */
	private void updateCasts()
	{
		CombatStyle.AttackStyle style = attackStyle;
		MagicMaxHit.Spell spell = config.castsLeft() && style != null && style.getStyle() == CombatStyle.MAGIC
			? maxHitCalculator.autocastSpell() : null;
		RuneSupply.Result result = spell == null ? null : RuneSupply.castsLeft(spell, carriedRunes(), unlimitedRunes());
		if (result == null || result.isUnlimited())
		{
			removeCastsBox();
			return;
		}

		boolean low = result.getCasts() < config.castsLowWarning();
		if (low && !castsLowNotified)
		{
			notifier.notify(config.castsLowNotification(), "Running low on runes: " + result.getCasts() + " "
				+ spell.getDisplayName() + " casts left");
		}
		castsLowNotified = low;

		RuneSupply.Rune limit = result.getLimitedBy();
		if (castsInfoBox == null || limit != castsRune)
		{
			removeCastsBox();
			castsRune = limit;
			castsInfoBox = new CastsInfoBox(itemManager.getImage(RuneSupply.itemFor(limit)), this);
			infoBoxManager.addInfoBox(castsInfoBox);
		}
		castsInfoBox.update(result.getCasts(), low, spell.getDisplayName() + ": " + Formatting.withCommas(result.getCasts())
			+ " casts left</br>" + limit.getDisplayName() + " runes run out first");
	}

	private void removeCastsBox()
	{
		if (castsInfoBox != null)
		{
			infoBoxManager.removeInfoBox(castsInfoBox);
			castsInfoBox = null;
			castsRune = null;
		}
	}

	/**
	 * Runes in the inventory and its rune pouch.
	 */
	private Map<RuneSupply.Rune, Long> carriedRunes()
	{
		Map<RuneSupply.Rune, Long> have = new EnumMap<>(RuneSupply.Rune.class);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		boolean pouch = false;
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() <= 0)
				{
					continue;
				}
				RuneSupply.add(have, item.getId(), item.getQuantity());
				pouch |= RUNE_POUCHES.contains(item.getId())
					|| itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT).contains("rune pouch");
			}
		}
		if (pouch)
		{
			EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
			for (int i = 0; i < POUCH_TYPES.length; i++)
			{
				int type = client.getVarbitValue(POUCH_TYPES[i]);
				int amount = client.getVarbitValue(POUCH_AMOUNTS[i]);
				if (type > 0 && amount > 0)
				{
					RuneSupply.add(have, runes.getIntValue(type), amount);
				}
			}
		}
		return have;
	}

	/**
	 * Runes the worn weapon and shield give without limit.
	 */
	private Set<RuneSupply.Rune> unlimitedRunes()
	{
		Set<RuneSupply.Rune> unlimited = java.util.EnumSet.noneOf(RuneSupply.Rune.class);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			for (EquipmentInventorySlot slot : new EquipmentInventorySlot[]{EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.SHIELD})
			{
				Item item = worn.getItem(slot.getSlotIdx());
				if (item != null && item.getId() > 0)
				{
					unlimited.addAll(RuneSupply.unlimitedFrom(
						itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT)));
				}
			}
		}
		return unlimited;
	}

	public SlayerGuide getSlayerGuide()
	{
		return slayerGuide;
	}

	// ---- Collection log -------------------------------------------------------------------------

	/**
	 * The Collection Log drew a page: save what the player has from it.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}
		Widget header = client.getWidget(InterfaceID.Collection.HEADER);
		Widget contents = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (header == null || contents == null || contents.getDynamicChildren() == null || isOtherPlayersLog())
		{
			return;
		}
		List<String> lines = new ArrayList<>();
		addTexts(lines, header.getDynamicChildren());
		addTexts(lines, header.getChildren());
		Widget headerText = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (headerText != null)
		{
			lines.add(Text.removeTags(headerText.getText() == null ? "" : headerText.getText()));
			addTexts(lines, headerText.getDynamicChildren());
		}
		String title = null;
		for (String line : lines)
		{
			if (!line.isEmpty())
			{
				title = line;
				break;
			}
		}
		List<CollectionLog.Item> items = new ArrayList<>();
		for (Widget w : contents.getDynamicChildren())
		{
			if (w.getItemId() > 0)
			{
				// Missing items are drawn faded; obtained ones fully opaque.
				items.add(new CollectionLog.Item(w.getItemId(), itemManager.getItemComposition(w.getItemId()).getName(),
					w.getOpacity() == 0));
			}
		}
		collectionLog.syncPage(title, items, lines, pageKills(title));
		saveCollectionLog();
		panelDirty = true;
	}

	/**
	 * Someone else's log, seen through a house's Adventure Log: its title names them.
	 */
	private boolean isOtherPlayersLog()
	{
		Widget frame = client.getWidget(InterfaceID.Collection.FRAME);
		Player me = client.getLocalPlayer();
		if (frame == null || me == null || me.getName() == null)
		{
			return false;
		}
		List<String> texts = new ArrayList<>();
		addTexts(texts, frame.getDynamicChildren());
		addTexts(texts, frame.getChildren());
		return CollectionLog.isOtherPlayersTitle(texts, me.getName());
	}

	private static void addTexts(List<String> lines, Widget[] widgets)
	{
		if (widgets == null)
		{
			return;
		}
		for (Widget w : widgets)
		{
			if (w != null && w.getText() != null && !w.getText().isEmpty())
			{
				lines.add(Text.removeTags(w.getText()).trim());
			}
		}
	}

	/**
	 * All-time kills of every monster that belongs to a collection log page.
	 */
	private long pageKills(String title)
	{
		long kills = 0;
		if (title == null)
		{
			return 0;
		}
		for (MonsterTracker.Snapshot s : monsterTracker.snapshot())
		{
			if (CollectionLog.matches(title, s.getName()))
			{
				kills += s.getKills();
			}
		}
		return kills;
	}

	/**
	 * @return the monster's collection log progress, or null if its page hasn't been opened
	 */
	public CollectionLog.Status getCollectionStatus(String monster)
	{
		String title = collectionLog.titleFor(monster);
		return title == null ? null : collectionLog.status(monster, pageKills(title));
	}

	public int getCollectionRevision()
	{
		return collectionLog.getRevision();
	}

	private void saveCollectionLog()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		int revision = collectionLog.getRevision();
		if (revision == savedCollectionRevision)
		{
			return;
		}
		savedCollectionRevision = revision;
		if (collectionLog.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, COLLECTION_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, COLLECTION_KEY, gson.toJson(collectionLog.exportState()));
	}

	private void loadCollectionLog()
	{
		List<CollectionLog.Page> saved = null;
		if (configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, COLLECTION_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					CollectionLog.Page[] pages = gson.fromJson(json, CollectionLog.Page[].class);
					saved = pages == null ? null : Arrays.asList(pages);
				}
				catch (JsonParseException e)
				{
					saved = null;
				}
			}
		}
		collectionLog.importState(saved);
		savedCollectionRevision = collectionLog.getRevision();
	}

	// ---- Milestones and Wrapped ------------------------------------------------------------------

	private void announceMilestone(String message)
	{
		if (message == null || !config.milestoneMessages())
		{
			return;
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Milestone: ")
				.append(ChatColorType.NORMAL)
				.append(message)
				.build())
			.build());
		if (config.milestoneScreenshot())
		{
			String fileName = "Milestone " + message.replaceAll("[^A-Za-z0-9 ,()]", "").trim();
			drawManager.requestNextFrameListener(frame ->
			{
				BufferedImage image = ImageUtil.bufferedImageFromImage(frame);
				executor.submit(() -> imageCapture.saveScreenshot(image, fileName, "Milestones", false, false));
			});
		}
	}

	/**
	 * The logged-in account's name, or null.
	 */
	public String getAccountName()
	{
		return accountName;
	}

	// ---- Luck ---------------------------------------------------------------------------------

	/**
	 * The max hit a hit on this monster rolled up to, or -1 if it isn't known for sure (a
	 * special attack, a weapon that doesn't roll evenly, enchanted bolts, Salve).
	 */
	private int luckMaxHit(String npcName, CombatStyle hitStyle)
	{
		MaxHitCalculator.Result max = maxHitResult;
		if (max == null || max.getMaxHit() <= 0 || hitStyle != max.getStyle() || max.getVsUndeadMaxHit() > 0
			|| client.getTickCount() - lastSpecTick <= SPEC_LUCK_TICKS)
		{
			return -1;
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		String weapon = wornName(worn, EquipmentInventorySlot.WEAPON);
		for (String uneven : UNEVEN_WEAPONS)
		{
			if (weapon.contains(uneven))
			{
				return -1;
			}
		}
		if (weapon.contains("crossbow") && wornName(worn, EquipmentInventorySlot.AMMO).contains("(e)"))
		{
			// Enchanted bolt effects.
			return -1;
		}
		int best = -1;
		for (MaxHitCalculator.Extra extra : max.getExtras())
		{
			if (extra.isHighlight())
			{
				best = Math.max(best, extra.getMaxHit());
			}
		}
		if (best > 0)
		{
			return best;
		}
		if (max.getOnTaskMaxHit() > 0 && taskSession.isTaskMonster(npcName))
		{
			return max.getOnTaskMaxHit();
		}
		return max.getMaxHit();
	}

	private String wornName(ItemContainer worn, EquipmentInventorySlot slot)
	{
		Item item = worn == null ? null : worn.getItem(slot.getSlotIdx());
		return item == null || item.getId() <= 0 ? ""
			: itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT);
	}

	/**
	 * @return how lucky this session's hits have been, or null with too few
	 */
	public HitLuck.Result getLuck()
	{
		return hitLuck.result();
	}

	// ---- Best master -----------------------------------------------------------------------------

	/**
	 * Every slayer master ranked for this player, from the task log.
	 */
	public List<MasterAdvice.Entry> getMasterAdvice(List<TaskStats> history, MasterAdvice.Goal goal)
	{
		Map<SlayerPoints.Master, TaskOdds> odds = allMasterOdds;
		if (odds.isEmpty())
		{
			return Collections.emptyList();
		}
		SlayerStatus status = slayerStatus;
		return MasterAdvice.compute(odds, history, status::hasEliteDiary, combatLevel, slayerLevel, goal);
	}

	/**
	 * Changes when the masters' odds are read again.
	 */
	public int getMasterOddsKey()
	{
		return System.identityHashCode(allMasterOdds);
	}

	// ---- Slayer areas ------------------------------------------------------------------------

	/**
	 * A task monster died: remember which area this map region belongs to, or pick the area
	 * if this region is already known.
	 */
	private void learnTaskArea()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null)
		{
			return;
		}
		WorldPoint where = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		int region = where == null ? -1 : where.getRegionID();
		if (taskSession.getArea() == null)
		{
			String known = areaMemory.areaFor(region, slayerGameData.currentAreas());
			if (known == null)
			{
				return;
			}
			taskSession.setArea(known);
		}
		areaMemory.learn(region, taskSession.getArea(),
			client.getVarbitValue(VarbitID.MULTIWAY_INDICATOR) != 0,
			client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0);
	}

	/**
	 * The player marked (or unmarked, with null) the area they're doing their task in.
	 */
	public void setTaskArea(String area)
	{
		clientThread.invokeLater(() ->
		{
			if (!taskSession.isActive())
			{
				return;
			}
			if (area == null)
			{
				// Wrong area: forget where it was learned, so it isn't picked again.
				areaMemory.forget(taskSession.getArea());
			}
			// Regions are learned from the next task kills, not from where the player is now
			// (they may be at the bank).
			taskSession.setArea(area);
			refreshPanelNow();
		});
	}

	/**
	 * @return the area the current task is being done in, or null
	 */
	public String getTaskArea()
	{
		return taskSession.isActive() ? taskSession.getArea() : null;
	}

	public boolean isMultiArea(String area)
	{
		return areaMemory.isMulti(area);
	}

	public boolean isWildernessArea(String area)
	{
		return areaMemory.isWilderness(area);
	}

	public int getAreaRevision()
	{
		return areaMemory.getRevision();
	}

	private void saveAreaMemory()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		int revision = areaMemory.getRevision();
		if (revision == savedAreaRevision)
		{
			return;
		}
		savedAreaRevision = revision;
		if (areaMemory.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, AREAS_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, AREAS_KEY, gson.toJson(areaMemory.exportState()));
	}

	/**
	 * The current account's learned areas and remembered bank.
	 */
	private void loadSlayerExtras()
	{
		SlayerAreaMemory.Saved saved = null;
		String bank = null;
		if (configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, AREAS_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					saved = gson.fromJson(json, SlayerAreaMemory.Saved.class);
				}
				catch (JsonParseException e)
				{
					saved = null;
				}
			}
			bank = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, BANK_KEY);
		}
		areaMemory.importState(saved);
		savedAreaRevision = areaMemory.getRevision();
		loadPrayerDrain();
		loadCollectionLog();
		savedBank = bank;
		bankNames = null;
		if (bank != null && config.checkBank())
		{
			String loaded = bank;
			// Item names are read on the client thread.
			clientThread.invokeLater(() ->
			{
				if (loaded.equals(savedBank))
				{
					bankNames = slayerGameData.itemNames(parseIds(loaded));
					panelDirty = true;
				}
			});
		}
	}

	// ---- Bank ---------------------------------------------------------------------------------

	/**
	 * The bank changed while open: remember what's in it, for the task guide's "In bank".
	 */
	private void rememberBank()
	{
		if (!config.checkBank())
		{
			return;
		}
		Set<Integer> ids = slayerGameData.bankItemIds();
		if (ids == null)
		{
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int id : ids)
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(id);
		}
		String value = sb.toString();
		if (value.equals(savedBank))
		{
			return;
		}
		savedBank = value;
		bankNames = slayerGameData.itemNames(ids);
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, BANK_KEY, value);
		}
		panelDirty = true;
	}

	/**
	 * Forgets the remembered bank, when the setting is turned off.
	 */
	private void forgetBank()
	{
		bankNames = null;
		savedBank = null;
		if (configManager.getRSProfileKey() != null)
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, BANK_KEY);
		}
	}

	static List<Integer> parseIds(String csv)
	{
		List<Integer> ids = new ArrayList<>();
		for (String part : csv.split(","))
		{
			try
			{
				ids.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException e)
			{
				// Skip anything unreadable.
			}
		}
		return ids;
	}

	// ---- Where to get a task -------------------------------------------------------------------

	/**
	 * Works out every master's chance of giving a task, for the Slayer tab. Null closes it.
	 */
	public void showTaskSources(String task)
	{
		clientThread.invokeLater(() ->
		{
			if (task == null || client.getGameState() != GameState.LOGGED_IN)
			{
				taskSources = TaskSources.NONE;
			}
			else
			{
				taskSources = TaskSources.of(task, slayerGameData.allOdds(this::blockedTasks));
			}
			refreshPanelNow();
		});
	}

	public TaskSources getTaskSources()
	{
		return taskSources;
	}

	/**
	 * Every task any master gives, empty until logged in with a slayer master.
	 */
	public List<String> getAllTaskNames()
	{
		return allTaskNames;
	}

	public TaskOdds getTaskOdds()
	{
		return taskOdds;
	}

	/**
	 * Adds the finished task to the log once the game has updated the streak, so its points
	 * can be worked out.
	 */
	private void finishPendingTaskRecord()
	{
		if (pendingTaskRecord == null || --pendingTaskRecordTicks > 0)
		{
			return;
		}
		SlayerStatus status = slayerStatus;
		SlayerPoints.Master master = pendingTaskMaster;
		if (master != null && status.getStreak() > 0)
		{
			pendingTaskRecord.points = SlayerPoints.pointsFor(master, status.getStreak(), status.hasEliteDiary(master));
		}
		slayerLog.add(pendingTaskRecord);
		pendingTaskRecord = null;
		announceMilestone(Milestones.onTask(slayerLog.getTasks().size()));
		saveSlayerLog();
		panelDirty = true;
	}

	private void loadSlayerLog()
	{
		SlayerTaskRecord[] saved = null;
		if (configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, SLAYER_LOG_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					saved = gson.fromJson(json, SlayerTaskRecord[].class);
				}
				catch (JsonParseException e)
				{
					saved = null;
				}
			}
		}
		slayerLog.importState(saved);
		savedSlayerLogRevision = slayerLog.getRevision();
	}

	private void saveSlayerLog()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		int revision = slayerLog.getRevision();
		if (revision == savedSlayerLogRevision)
		{
			return;
		}
		savedSlayerLogRevision = revision;
		if (slayerLog.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, SLAYER_LOG_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, SLAYER_LOG_KEY,
			gson.toJson(slayerLog.exportState()));
	}

	public SlayerStatus getSlayerStatus()
	{
		return slayerStatus;
	}

	public List<SlayerTaskRecord> getSlayerTasks()
	{
		return slayerLog.getTasks();
	}

	public int getSlayerLogRevision()
	{
		return slayerLog.getRevision();
	}

	/**
	 * @return superiors seen during the current task (0 without a task)
	 */
	public int getCurrentTaskSuperiors()
	{
		return taskSession.isActive() ? taskSession.getSuperiors() : 0;
	}

	public int getCurrentTaskKills()
	{
		return taskSession.isActive() ? taskSession.getKills() : 0;
	}

	public void removeTaskRecord(SlayerTaskRecord record)
	{
		clientThread.invokeLater(() ->
		{
			slayerLog.remove(record);
			saveSlayerLog();
			refreshPanelNow();
		});
	}

	public void clearSlayerLog()
	{
		clientThread.invokeLater(() ->
		{
			slayerLog.clear();
			saveSlayerLog();
			refreshPanelNow();
		});
	}

	// ---- Reordering -----------------------------------------------------------------------

	/**
	 * The order goals are shown in, from dragging them. Skills not listed go last.
	 */
	public List<Skill> getGoalOrder()
	{
		List<Skill> order = new ArrayList<>();
		for (String name : config.goalOrder().split(","))
		{
			for (Skill skill : Skill.values())
			{
				if (skill.getName().equalsIgnoreCase(name.trim()) && !order.contains(skill))
				{
					order.add(skill);
				}
			}
		}
		return order;
	}

	public void setGoalOrder(List<Skill> order)
	{
		List<String> names = new ArrayList<>();
		for (Skill skill : order)
		{
			names.add(skill.getName());
		}
		configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "goalOrder", String.join(",", names));
		refreshPanelNow();
	}

	/**
	 * Sets the order of pinned monsters, from dragging them. Pinned monsters not in the
	 * list (e.g. filtered out by a search) keep their place after these.
	 */
	public void setPinnedOrder(List<String> names)
	{
		Set<String> order = new java.util.LinkedHashSet<>();
		for (String name : names)
		{
			order.add(name.toLowerCase(Locale.ROOT));
		}
		order.addAll(MonsterTracker.parseNames(config.pinnedMonsters()));
		configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "pinnedMonsters", String.join(", ", order));
	}
}
