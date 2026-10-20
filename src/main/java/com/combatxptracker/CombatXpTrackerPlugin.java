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
import java.util.function.Consumer;
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
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
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
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
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
import net.runelite.client.util.QuantityFormatter;
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
	// How often gold per hour redraws while a trip is going: 5 ticks, 3 seconds.
	private static final int RATE_TICKS = 5;
	private static final int OLD_RARE_DROP_DEFAULT = 1_000_000;
	private static final String LEGACY_RESTORE_KEY = "restoredFromLootTracker";
	private static final String RATES_RESTARTED_KEY = "ratesRestarted212";
	private static final String RARE_DROP_MIGRATED_KEY = "rareDropValue212";
	// Attack speeds learned from the player's attacks. Weapons are the same on every account.
	private static final String ATTACK_SPEEDS_KEY = "learnedAttackSpeeds";
	// Set once the Loot Tracker restore was used or dismissed for an account.
	private static final String RESTORE_KEY = "lootTrackerRestore";
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
	private static final String SUPPLIES_KEY = "supplies";
	private static final String UPTIME_KEY = "attackUptime";
	private static final String BOSS_TIMES_KEY = "bossTimes";
	private static final String DEATHS_KEY = "deaths";
	private static final String DAMAGE_TAKEN_KEY = "damageTaken";
	private static final int ARCEUUS_SPELLBOOK = 3;
	// A reward chest's items should be there within this many ticks of it opening.
	private static final int REWARD_WAIT_TICKS = 3;
	// Drops below this never get a reveal, whatever the rare drop value.
	private static final long MIN_REVEAL_VALUE = 1_000_000;
	// A boss's duration message and kill count message come within this many ticks.
	private static final int BOSS_TIME_WINDOW_TICKS = 3;
	// Supplies used within this many ticks (30s) of hitting a monster count towards it.
	private static final int SUPPLY_TICKS = 50;
	// The cannon's ball count drops by one a shot; bigger drops are it being picked up.
	private static final int MAX_CANNON_SHOT = 2;
	// Combat XP this soon after a cannon shot may be the cannon's.
	private static final int CANNON_XP_TICKS = 4;
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
	private KillTimerOverlay killTimerOverlay;

	@Inject
	private DropRevealOverlay dropRevealOverlay;

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
	// The item price can only be read on the client thread; the panel reads this copy.
	private volatile long cannonballPrice;

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
	// Supplies used per monster (all time and this session), and what was carried last.
	private final SupplyLedger allTimeSupplies = new SupplyLedger();
	private final SupplyLedger sessionSupplies = new SupplyLedger();
	private int savedSuppliesRevision = -1;
	// Attack uptime per monster (all time and this session).
	private final AttackUptime allTimeUptime = new AttackUptime();
	private final AttackUptime sessionUptime = new AttackUptime();
	private int savedUptimeRevision = -1;
	// Boss kill times from the game's messages, and the halves waiting to be matched up.
	private final BossTimes bossTimes = new BossTimes();
	private int savedBossTimesRevision = -1;
	private BossTimes.Duration pendingDuration;
	private int pendingDurationTick;
	private String lastKillCountBoss;
	private int lastKillCountBossTick;
	private final MaxHitFlash maxHitFlash = new MaxHitFlash();
	private final KillTimer killTimer = new KillTimer();
	// Damage taken per boss kill (all time and this session), and taken since the last kill.
	private final DamageTaken allTimeDamageTaken = new DamageTaken();
	private final DamageTaken sessionDamageTaken = new DamageTaken();
	private int savedDamageTakenRevision = -1;
	private int damageSinceKill;
	private ElementalWeakness elementalWeakness = new ElementalWeakness(null);
	// The latest big drop for the panel's reveal.
	private volatile DropReveal dropReveal;
	private volatile long revealStartMillis;
	// A reward chest's interface opened, waiting to read its items; and the last one read, so
	// looking at the same reward again isn't counted twice.
	private RewardSources pendingReward;
	private int pendingRewardTick;
	private String lastRewardSignature;
	private long lastRewardMillis;
	// With the reveal on screen, its screenshot waits until it has landed.
	private String pendingRevealScreenshot;
	private long revealScreenshotAt;
	private int revealSequence;
	// What the player carries, for the supply forecast.
	private volatile int carriedFood = -1;
	private volatile int carriedPrayerDoses;
	private volatile int prayerPointsNow = -1;
	// Prep check: the bank was open last tick, and the tick to check on after it closes.
	private boolean bankWasOpen;
	private int prepCheckTick = -1;
	// Drop rates for the dry calculator, bundled with the plugin.
	private DryCalculator dryCalculator = new DryCalculator(null);
	private final CombatAchievements combatAchievements = new CombatAchievements();
	private boolean combatAchievementsLoaded;
	// Deaths per monster (all time and this session).
	private final DeathLedger allTimeDeaths = new DeathLedger();
	private final DeathLedger sessionDeaths = new DeathLedger();
	private int savedDeathsRevision = -1;
	private final AttackDetector attackDetector = new AttackDetector(NON_ATTACK_ANIMATIONS);
	// The monster being fought now, for the target card.
	private volatile TargetInfo targetInfo;
	private int targetSeenTick = -1;
	// Cannon XP isn't the player attacking.
	private int lastCannonShotTick = -1;
	private volatile int maxHitChange;
	private Map<Integer, Long> lastInventory;
	private SupplyLedger.Category pendingConsume;
	private int pendingConsumeTick;
	private Map<Integer, Long> lastRunes;
	private int lastAmmoId = -1;
	private long lastAmmoQty;
	private int lastCannonBalls = -1;
	private volatile int attackSpeedTicks = AttackTimer.UNARMED_TICKS;
	// Real speeds learned from the player's attacks, per weapon and attack style.
	private final AttackSpeeds attackSpeeds = new AttackSpeeds();
	private String attackKey;
	// The last attack spotted for certain (its animation is known), to learn from the gap to the next.
	private String lastCertainKey;
	private int lastCertainTick = -1;
	// The combat style of the last attack, for the timer's colour.
	private volatile CombatStyle timerStyle;
	// Each boss's collection log uniques, bundled from the OSRS Wiki.
	private volatile BossUniques bossUniques;
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
	// The account whose saved data is loaded. Nothing is saved for any other.
	private volatile String loadedProfile;
	private volatile boolean restoreOffered;
	// Bumped every 5 ticks while a trip is going, so gold per hour redraws as time passes.
	private volatile int rateTick;
	// The monster being done, for the overlay's gold per hour. Updated every 5 ticks.
	private volatile MonsterTracker.Snapshot tripSnapshot;
	private int ticksSinceRate;
	// The one monster the restore offer is for (removed by accident), or null for all.
	private volatile String restoreMonster;
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
		overlayManager.add(killTimerOverlay);
		overlayManager.add(dropRevealOverlay);
		overlayManager.add(lootDespawnOverlay);
		dryCalculator = loadDryCalculator();
		bossUniques = new BossUniques(loadResource("collection_uniques.json", new TypeToken<Map<String, List<String>>>()
		{
		}.getType()));
		migrateRareDropValue();
		monsterTracker.setUniqueTest(this::isUnique);
		sessionMonsters.setUniqueTest(this::isUnique);
		loadAttackSpeeds();
		elementalWeakness = new ElementalWeakness(loadResource("elemental_weakness.json",
			new TypeToken<Map<String, List<Integer>>>()
			{
			}.getType()));
		syncInfoBoxes();

		// Turned on while already logged in: GameStateChanged and RuneScapeProfileChanged
		// won't fire, so baseline and load now.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				loadProfileData();
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
		overlayManager.remove(killTimerOverlay);
		overlayManager.remove(dropRevealOverlay);
		killTimer.reset();
		combatAchievements.clear();
		combatAchievementsLoaded = false;
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
		saveSupplies();
		allTimeSupplies.importState(null);
		sessionSupplies.reset();
		savedSuppliesRevision = -1;
		saveUptime();
		allTimeUptime.importState(null);
		sessionUptime.reset();
		savedUptimeRevision = -1;
		saveBossTimes();
		bossTimes.importState(null);
		savedBossTimesRevision = -1;
		saveDeaths();
		allTimeDeaths.importState(null);
		sessionDeaths.reset();
		savedDeathsRevision = -1;
		saveDamageTaken();
		allTimeDamageTaken.importState(null);
		sessionDamageTaken.reset();
		savedDamageTakenRevision = -1;
		damageSinceKill = 0;
		pendingDuration = null;
		lastKillCountBoss = null;
		maxHitFlash.reset();
		maxHitChange = 0;
		lastInventory = null;
		lastRunes = null;
		pendingConsume = null;
		lastCannonBalls = -1;
		attackTimer.reset();
		lastCertainTick = -1;
		attackDetector.reset();
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
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
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
		loadedProfile = null;
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
			// Time logged out isn't part of a trip.
			monsterTracker.endTrip();
			sessionMonsters.endTrip();
			taskSession.endTrip();
			clearFights();
			xpPerKill.clearPending();
			attackTimer.reset();
		lastCertainTick = -1;
			attackDetector.reset();
			killTimer.reset();
			combatAchievementsLoaded = false;
			saveSlayerLog();
			saveAreaMemory();
			savePrayerDrain();
			saveSupplies();
			saveUptime();
			saveBossTimes();
			saveDeaths();
			saveDamageTaken();
			allTimeUptime.endFight();
			sessionUptime.endFight();
			maxHitFlash.reset();
			maxHitChange = 0;
			lastInventory = null;
			lastRunes = null;
			lastCannonBalls = -1;
			groundLoot.clear();
			groundLootStatus = null;
			lastPrayerPoints = -1;
			if (config.resetHitsOnLogout())
			{
				// Only this session's stats; what's remembered all-time is kept.
				hitStats.reset();
				hitLuck.reset();
				sessionSupplies.reset();
				sessionUptime.reset();
				sessionDeaths.reset();
				sessionDamageTaken.reset();
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
		loadProfileData();
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
		if ("restoreFromLootTracker".equals(event.getKey()) && config.restoreFromLootTracker())
		{
			// A tick box standing in for a button: untick it and show the Restore card.
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "restoreFromLootTracker", false);
			offerRestore(null);
			SwingUtilities.invokeLater(() ->
			{
				if (navButton != null)
				{
					clientToolbar.openPanel(navButton);
				}
			});
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
		cannonballPrice = itemManager.getItemPrice(ItemID.MCANNONBALL);
		if (++ticksSinceRate >= RATE_TICKS)
		{
			ticksSinceRate = 0;
			if (monsterTracker.getCurrentTrip() != null || taskSession.isActive())
			{
				rateTick++;
				panelDirty = true;
			}
			tripSnapshot = config.overlayGpPerHour() ? currentTripSnapshot() : null;
		}
		refreshCombatState();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			trackAttacks();
		}
		pruneKillTracking();
		pruneFights();
		killTimer.onTick(client.getTickCount());
		readPendingReward();
		if (pendingRevealScreenshot != null && System.currentTimeMillis() >= revealScreenshotAt)
		{
			screenshot(pendingRevealScreenshot);
			pendingRevealScreenshot = null;
		}
		checkPrepAfterBank();
		if (!combatAchievementsLoaded && config.combatAchievements() && client.getGameState() == GameState.LOGGED_IN)
		{
			combatAchievementsLoaded = true;
			combatAchievements.load(client);
			panelDirty = true;
		}
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
			saveSupplies();
			saveUptime();
			saveBossTimes();
			saveDeaths();
			saveDamageTaken();
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			updateGroundLoot();
			trackPrayer();
			updateCasts();
			trackRunesAndAmmo();
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
				&& isFromCurrentStyle(skill) && !cannonFiredRecently())
			{
				attackDetector.onXpDrop();
				// Combat XP comes the moment you attack.
				if (attackTimer.onAttackSignal(client.getTickCount(), attackSpeedTicks))
				{
					recordAttack();
				}
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
			// Damage taken, not dealt. A block animation may come with it.
			attackDetector.onHitTaken();
			int type = hitsplat.getHitsplatType();
			if (type != HitsplatID.HEAL && type != HitsplatID.POISON && type != HitsplatID.VENOM && type != HitsplatID.DISEASE
				&& fightMonster != null && client.getTickCount() - lastFightTick <= AttackTimer.IDLE_TICKS)
			{
				damageSinceKill += hitsplat.getAmount();
			}
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
			// Accuracy counts 0s whatever the "Include 0-damage hits" setting.
			boolean landed = hitsplat.getAmount() > 0;
			monsterTracker.recordRoll(fightMonster, landed);
			sessionMonsters.recordRoll(fightMonster, landed);
			allTimeUptime.onDamage(fightMonster, hitsplat.getAmount(), lastFightTick);
			sessionUptime.onDamage(fightMonster, hitsplat.getAmount(), lastFightTick);
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
		if (event.getActor() == client.getLocalPlayer())
		{
			onPlayerDeath();
			return;
		}
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
				killTimer.onKill(BossGroups.trackedName(name), client.getTickCount());
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
	 * The player died. Counted against the monster that killed them, with what they were
	 * carrying. Deaths to other players aren't counted.
	 */
	private void onPlayerDeath()
	{
		String monster = deathCause();
		if (monster == null)
		{
			return;
		}
		long carried = carriedValue();
		long now = System.currentTimeMillis();
		int count = allTimeDeaths.record(monster, carried, now);
		sessionDeaths.record(monster, carried, now);
		if (taskSession.isActive() && taskSession.isTaskMonster(monster))
		{
			taskSession.recordDeath();
		}
		if (config.deathMessage())
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append(DeathLedger.message(monster, count, carried))
					.build())
				.build());
		}
		panelDirty = true;
	}

	/**
	 * The monster that killed the player: the one they were fighting, one attacking them, or
	 * the last one they hit in the past 30 seconds. Null for another player or nothing.
	 */
	private String deathCause()
	{
		Player local = client.getLocalPlayer();
		Actor target = local == null ? null : local.getInteracting();
		if (target instanceof Player)
		{
			return null;
		}
		if (target instanceof NPC)
		{
			return BossGroups.trackedName(cleanName(target.getName()));
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc != null && npc.getInteracting() == local)
			{
				return BossGroups.trackedName(cleanName(npc.getName()));
			}
		}
		return fightMonster != null && client.getTickCount() - lastFightTick <= SUPPLY_TICKS ? fightMonster : null;
	}

	/**
	 * Grand Exchange value of the player's inventory and equipment.
	 */
	private long carriedValue()
	{
		long total = 0;
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			for (Map.Entry<Integer, Long> e : inventoryCounts(client.getItemContainer(containerId)).entrySet())
			{
				total += e.getValue() * itemManager.getItemPrice(e.getKey());
			}
		}
		return total;
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
		if (text.startsWith(CollectionLog.NEW_ITEM_PREFIX))
		{
			revealUnique(text.substring(CollectionLog.NEW_ITEM_PREFIX.length()).trim());
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
			lastKillCountBoss = BossFight.parseKillCountName(text);
			lastKillCountBossTick = client.getTickCount();
			recordBossTimeIfReady();
			return;
		}
		BossTimes.Duration duration = BossTimes.parse(text);
		if (duration != null)
		{
			pendingDuration = duration;
			pendingDurationTick = client.getTickCount();
			recordBossTimeIfReady();
		}
	}

	/**
	 * Records a boss kill time once both its duration and kill count messages are in,
	 * whichever came first.
	 */
	private void recordBossTimeIfReady()
	{
		if (pendingDuration == null || lastKillCountBoss == null
			|| Math.abs(pendingDurationTick - lastKillCountBossTick) > BOSS_TIME_WINDOW_TICKS)
		{
			return;
		}
		String boss = BossGroups.trackedName(lastKillCountBoss);
		BossTimes.Duration duration = pendingDuration;
		pendingDuration = null;
		lastKillCountBoss = null;
		double before = bossTimes.record(boss, duration);
		killTimer.onDuration(boss, duration.seconds, duration.newBest, client.getTickCount());
		if (before >= 0)
		{
			boolean precise = BossTimes.isPrecise(duration.seconds) || BossTimes.isPrecise(before);
			announceMilestone("New " + boss + " personal best: " + BossTimes.format(duration.seconds, precise)
				+ (before > 0 ? " (was " + BossTimes.format(before, precise) + ")" : ""));
		}
		panelDirty = true;
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
		if (pendingDuration != null && tick - pendingDurationTick > BOSS_TIME_WINDOW_TICKS)
		{
			pendingDuration = null;
		}
		if (lastKillCountBoss != null && tick - lastKillCountBossTick > BOSS_TIME_WINDOW_TICKS)
		{
			lastKillCountBoss = null;
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
	 * Whether the player is attacking something right now: an attack-like animation playing
	 * while targeting an NPC that can be fought (or a player).
	 */
	/**
	 * An attack on the monster the player is targeting, for attack uptime.
	 */
	private void recordAttack()
	{
		CombatStyle.AttackStyle style = attackStyle;
		timerStyle = style == null ? null : style.getStyle();
		Player local = client.getLocalPlayer();
		Actor target = local == null ? null : local.getInteracting();
		if (!(target instanceof NPC))
		{
			return;
		}
		String name = BossGroups.trackedName(cleanName(target.getName()));
		if (name == null)
		{
			return;
		}
		int tick = client.getTickCount();
		allTimeUptime.onAttack(name, tick, attackSpeedTicks);
		sessionUptime.onAttack(name, tick, attackSpeedTicks);
		if (isBoss(name) && killTimer.onAttack(name, tick) && config.boostReminder())
		{
			remindBoosts(name);
		}
	}

	/**
	 * At the start of a boss fight: says if the style's skill isn't boosted, no offensive
	 * prayer is on, or there's no thrall out on the Arceuus spellbook.
	 */
	private void remindBoosts(String boss)
	{
		CombatStyle style = attackStyle == null ? null : attackStyle.getStyle();
		Skill skill = style == null ? null : BoostReminder.boostedSkill(style);
		if (skill == null)
		{
			return;
		}
		boolean boosted = client.getBoostedSkillLevel(skill) > client.getRealSkillLevel(skill);
		boolean prayer = false;
		for (Prayer p : BoostReminder.offensivePrayers(style))
		{
			prayer |= client.isPrayerActive(p);
		}
		NPC follower = client.getFollower();
		boolean thrallWanted = client.getVarbitValue(VarbitID.SPELLBOOK) == ARCEUUS_SPELLBOOK
			&& (follower == null || follower.getName() == null || !follower.getName().toLowerCase().contains("thrall"));
		String message = BoostReminder.message(boss, style, boosted, prayer, thrallWanted);
		if (message != null)
		{
			chat(message);
		}
	}

	/**
	 * A boss: one with a recorded kill time or known unique drops.
	 */
	private boolean isBoss(String name)
	{
		return bossTimes.get(name) != null || dryCalculator.hasBoss(name);
	}

	/**
	 * Once a tick: whether the player started an attack, for the attack timer and uptime.
	 */
	private void trackAttacks()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		int tick = client.getTickCount();
		AttackDetector.Result seen = attackDetector.onTick(tick, local.getAnimation(), local.getAnimationFrame(),
			hasCombatTarget(local));
		boolean attacked = seen == AttackDetector.Result.CERTAIN
			? attackTimer.onCertainAttack(tick, attackSpeedTicks)
			: attackTimer.onTick(tick, seen == AttackDetector.Result.LIKELY, attackSpeedTicks);
		if (attacked)
		{
			recordAttack();
		}
		if (seen == AttackDetector.Result.CERTAIN && attacked)
		{
			learnAttackSpeed(tick);
		}
		updateTarget(local, tick);
	}

	/**
	 * The monster the player is fighting and its health, for the target card. Kept for a
	 * few seconds after they stop, so it doesn't flicker between kills.
	 */
	private void updateTarget(Player local, int tick)
	{
		Actor target = local.getInteracting();
		if (target instanceof NPC && hasCombatTarget(local))
		{
			NPC npc = (NPC) target;
			String name = BossGroups.trackedName(cleanName(npc.getName()));
			if (name != null)
			{
				Integer max = npcManager.getHealth(npc.getId());
				int maxHp = max == null ? -1 : max;
				ElementalWeakness.Weakness weakness = elementalWeakness.of(npc.getId());
				boolean spellMatches = weakness != null && attackStyle != null && attackStyle.getStyle() == CombatStyle.MAGIC
					&& weakness.getElement() == ElementalWeakness.Element.ofSpell(maxHitCalculator.autocastSpell());
				TargetInfo info = new TargetInfo(name, TargetInfo.hitpoints(npc.getHealthRatio(), npc.getHealthScale(), maxHp),
					maxHp, weakness, spellMatches);
				targetSeenTick = tick;
				if (!info.equals(targetInfo))
				{
					targetInfo = info;
					panelDirty = true;
				}
				return;
			}
		}
		if (targetInfo != null && tick - targetSeenTick > AttackTimer.IDLE_TICKS)
		{
			targetInfo = null;
			panelDirty = true;
		}
	}

	/**
	 * @return the monster being fought now, or null
	 */
	public TargetInfo getTargetInfo()
	{
		return config.showTargetCard() ? targetInfo : null;
	}

	/**
	 * The player's animation was set. Attacks are worked out once a tick in trackAttacks.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			attackDetector.onAnimationSet();
		}
	}

	private boolean cannonFiredRecently()
	{
		// Cannon XP comes when the ball lands, a few ticks after the shot.
		return lastCannonShotTick >= 0 && client.getTickCount() - lastCannonShotTick <= CANNON_XP_TICKS;
	}

	/**
	 * Whether the player is targeting something that can be fought: an NPC with hitpoints or
	 * an Attack option, or another player.
	 */
	private boolean hasCombatTarget(Player local)
	{
		Actor target = local.getInteracting();
		if (target instanceof Player)
		{
			return true;
		}
		if (!(target instanceof NPC))
		{
			return false;
		}
		NPC npc = (NPC) target;
		Integer health = npcManager.getHealth(npc.getId());
		if (health != null && health > 0)
		{
			return true;
		}
		String[] actions = npc.getComposition() == null ? null : npc.getComposition().getActions();
		return actions != null && Arrays.asList(actions).contains("Attack");
	}

	/**
	 * Two certain attacks in a row with the same weapon and style: the gap teaches its real speed.
	 */
	private void learnAttackSpeed(int tick)
	{
		String key = attackKey;
		if (key != null && key.equals(lastCertainKey) && lastCertainTick >= 0
			&& attackSpeeds.observe(key, tick - lastCertainTick))
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, ATTACK_SPEEDS_KEY,
				gson.toJson(attackSpeeds.exportState()));
		}
		lastCertainKey = key;
		lastCertainTick = tick;
	}

	private void loadAttackSpeeds()
	{
		String json = configManager.getConfiguration(CombatXpTrackerConfig.GROUP, ATTACK_SPEEDS_KEY);
		Map<String, AttackSpeeds.Learned> saved = null;
		if (json != null && !json.isEmpty())
		{
			try
			{
				saved = gson.fromJson(json, new TypeToken<Map<String, AttackSpeeds.Learned>>()
				{
				}.getType());
			}
			catch (JsonParseException e)
			{
				saved = null;
			}
		}
		attackSpeeds.importState(saved);
	}

	/**
	 * @return the combat style of the last attack, for the timer's colour, or null
	 */
	CombatStyle getTimerStyle()
	{
		return timerStyle;
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
		if (isBoss(tracked))
		{
			allTimeDamageTaken.record(tracked, damageSinceKill);
			sessionDamageTaken.record(tracked, damageSinceKill);
		}
		damageSinceKill = 0;
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
		String option = Text.removeTags(event.getMenuOption());
		if (event.getItemId() > 0 && ("Eat".equalsIgnoreCase(option) || "Drink".equalsIgnoreCase(option)))
		{
			// The item changes in the inventory on the next tick.
			pendingConsume = "Eat".equalsIgnoreCase(option) ? SupplyLedger.Category.FOOD : SupplyLedger.Category.POTIONS;
			pendingConsumeTick = client.getTickCount();
		}
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
		if (event.getContainerId() == InventoryID.INV)
		{
			countCarriedSupplies(event.getItemContainer());
			Map<Integer, Long> now = inventoryCounts(event.getItemContainer());
			if (pendingConsume != null && lastInventory != null && client.getTickCount() - pendingConsumeTick <= 2)
			{
				long gp = SupplyLedger.consumedValue(lastInventory, now, itemManager::getItemPrice);
				if (!now.equals(lastInventory))
				{
					recordSupply(pendingConsume, gp, 1);
					pendingConsume = null;
				}
			}
			lastInventory = now;
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
			revealRareDrops(name, drops);
			panelDirty = true;
		}
	}

	/**
	 * A reward interface opened (a raid chest, the Barrows chest): its items are read on the
	 * next tick, once the game has filled them in.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		RewardSources source = RewardSources.ofInterface(event.getGroupId());
		if (source != null)
		{
			pendingReward = source;
			pendingRewardTick = client.getTickCount();
		}
	}

	/**
	 * Records a reward chest's items as a completion of it, unless it's the same reward
	 * looked at again.
	 */
	private void readPendingReward()
	{
		RewardSources source = pendingReward;
		if (source == null || client.getTickCount() <= pendingRewardTick)
		{
			return;
		}
		ItemContainer container = client.getItemContainer(source.getContainerId());
		Map<Integer, Long> items = container == null ? Collections.emptyMap() : inventoryCounts(container);
		if (items.isEmpty())
		{
			// Not filled in yet: give it a few ticks.
			if (client.getTickCount() - pendingRewardTick > REWARD_WAIT_TICKS)
			{
				pendingReward = null;
			}
			return;
		}
		pendingReward = null;
		long now = System.currentTimeMillis();
		String signature = source.name() + ":" + RewardSources.signature(items);
		if (signature.equals(lastRewardSignature) && now - lastRewardMillis < RewardSources.SAME_REWARD_MILLIS)
		{
			return;
		}
		lastRewardSignature = signature;
		lastRewardMillis = now;
		if (!config.trackMonsterLoot())
		{
			return;
		}
		String name = source.getDisplayName();
		List<MonsterTracker.Drop> drops = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			drops.add(drop(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue())));
		}
		monsterTracker.recordKill(name, now);
		sessionMonsters.recordKill(name, now);
		monsterTracker.recordLoot(name, drops, now);
		sessionMonsters.recordLoot(name, drops, now);
		revealRareDrops(name, drops);
		panelDirty = true;
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
			revealRareDrops(tracked, drops);
		}
		panelDirty = true;
	}

	// ---- Big drops ------------------------------------------------------------------------------

	/**
	 * A new collection log item: reveal it, with how dry the player was.
	 */
	private void revealUnique(String item)
	{
		String page = collectionLog.pageOf(item);
		String monster = page;
		int itemId = -1;
		List<Integer> strip = new ArrayList<>();
		String detail = null;
		if (page != null)
		{
			for (CollectionLog.Item i : collectionLog.items(page))
			{
				strip.add(i.id);
				if (i.name != null && i.name.equalsIgnoreCase(item))
				{
					itemId = i.id;
				}
			}
			CollectionLog.Status status = getCollectionStatus(page);
			int kc = status == null ? -1 : status.getKillCount();
			List<DryCalculator.Dry> dry = kc > 0 ? dryCalculator.dry(page, Collections.singletonList(item), kc)
				: Collections.emptyList();
			detail = page + (kc > 0 ? ", kc " + Formatting.withCommas(kc) : "")
				+ (dry.isEmpty() ? "" : String.format(java.util.Locale.US, ", %.1fx the rate", dry.get(0).getRatio()));
		}
		reveal(new DropReveal(++revealSequence, item, itemId, itemId > 0 ? itemManager.getItemPrice(itemId) : 0, monster,
			true, detail, strip));
	}

	/**
	 * Drops worth more than the rare drop value get a reveal too, unless they're a missing
	 * collection log item: that one's revealed when the game's message for it comes.
	 */
	private void revealRareDrops(String monster, List<MonsterTracker.Drop> drops)
	{
		// At least a million, so a low rare drop value doesn't reveal (and screenshot) every kill.
		long rareValue = Math.max(MIN_REVEAL_VALUE, config.rareDropValue());
		LootPrice price = config.lootPrice();
		MonsterTracker.Drop best = null;
		for (MonsterTracker.Drop d : drops)
		{
			if (d.totalValue(price) >= rareValue && !collectionLog.isMissing(d.getName())
				&& (best == null || d.totalValue(price) > best.totalValue(price)))
			{
				best = d;
			}
		}
		if (best == null)
		{
			return;
		}
		List<Integer> strip = new ArrayList<>();
		for (MonsterTracker.Drop d : drops)
		{
			strip.add(d.getItemId());
		}
		int kills = monsterTracker.getKills(monster);
		reveal(new DropReveal(++revealSequence, best.getName(), best.getItemId(), best.totalValue(price), monster, false,
			monster + (kills > 0 ? ", kill " + Formatting.withCommas(kills) : ""), strip));
	}

	private void reveal(DropReveal drop)
	{
		dropReveal = drop;
		revealStartMillis = System.currentTimeMillis();
		panelDirty = true;
		if (!config.uniqueScreenshot())
		{
			return;
		}
		if (config.dropRevealOnScreen())
		{
			// Taken once the reveal has landed, so the picture shows it.
			pendingRevealScreenshot = drop.screenshotName();
			revealScreenshotAt = revealStartMillis + DropRevealCard.SPIN_MILLIS + 300;
		}
		else
		{
			screenshot(drop.screenshotName());
		}
	}

	private void screenshot(String fileName)
	{
		drawManager.requestNextFrameListener(frame ->
		{
			BufferedImage image = ImageUtil.bufferedImageFromImage(frame);
			executor.submit(() -> imageCapture.saveScreenshot(image, fileName, "Uniques", false, false));
		});
	}

	/**
	 * @return the big drop to show on the game screen, or null when that's turned off
	 */
	public DropReveal getScreenReveal()
	{
		return config.dropReveal() && config.dropRevealOnScreen() ? dropReveal : null;
	}

	public long getRevealStartMillis()
	{
		return revealStartMillis;
	}

	/**
	 * @return the latest big drop, or null
	 */
	public DropReveal getDropReveal()
	{
		return config.dropReveal() ? dropReveal : null;
	}

	// ---- Supply forecast ----------------------------------------------------------------------

	/**
	 * Food pieces and prayer restoring doses in the inventory. Client thread.
	 */
	private void countCarriedSupplies(ItemContainer inventory)
	{
		int food = 0;
		int doses = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(item.getId());
			String[] actions = comp.getInventoryActions();
			if (actions != null && Arrays.asList(actions).contains("Eat"))
			{
				food += item.getQuantity();
			}
			else if (SupplyForecast.restoresPrayer(comp.getName()))
			{
				doses += SupplyForecast.doses(comp.getName()) * item.getQuantity();
			}
		}
		carriedFood = food;
		carriedPrayerDoses = doses;
	}

	/**
	 * @return kills the food carried lasts against a monster, or -1
	 */
	public int getFoodKills(String monster)
	{
		SupplyLedger.Totals totals = allTimeSupplies.get(monster);
		Long eaten = totals == null ? null : totals.count.get(SupplyLedger.Category.FOOD);
		return SupplyForecast.foodKills(carriedFood, eaten == null ? 0 : eaten, monsterTracker.getKills(monster));
	}

	/**
	 * @return minutes the prayer points and doses carried last against a monster, or -1
	 */
	public double getPrayerMinutes(String monster)
	{
		PrayerDrain.Rate rate = prayerDrain.rate(monster);
		return rate == null ? -1
			: SupplyForecast.prayerMinutes(prayerPointsNow, carriedPrayerDoses, prayerLevel, rate.pointsPerMinute());
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
		attackKey = AttackSpeeds.key(maxHitCalculator.weaponId(), attackStyle == null ? null : attackStyle.getName());
		attackSpeedTicks = attackSpeeds.speed(attackKey, maxHitCalculator.attackSpeed(attackStyle));
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
		int tick = client.getTickCount();
		if (result != null)
		{
			maxHitFlash.update(result.getStyle(), result.getMaxHit(), tick);
		}
		int change = config.flashMaxHitChange() ? maxHitFlash.change(tick) : 0;
		if (change != maxHitChange)
		{
			maxHitChange = change;
			panelDirty = true;
		}
	}

	/**
	 * @return how much the max hit just changed by, while it's flashing, or 0
	 */
	public int getMaxHitChange()
	{
		return maxHitChange;
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
			// Only for a task just given out, not one picked up again at login.
			if (config.taskForecast() && status.getAssigned() > 0 && newRemaining >= status.getAssigned())
			{
				TaskForecast forecast = TaskForecast.of(slayerLog.getTasks(), newTask, newRemaining, config.lootPrice());
				if (forecast != null)
				{
					chat(forecast.message(newTask));
				}
			}
		}
	}

	/**
	 * A highlighted chat message from the plugin.
	 */
	private void chat(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(text)
				.build())
			.build());
	}

	/**
	 * Once the bank closes on a slayer task, says what the task needs that isn't with you.
	 * Checked a tick later, once the task guide has seen the new inventory.
	 */
	private void checkPrepAfterBank()
	{
		boolean open = client.getGameState() == GameState.LOGGED_IN && isBankOpen();
		int tick = client.getTickCount();
		if (bankWasOpen && !open)
		{
			prepCheckTick = tick + 1;
		}
		bankWasOpen = open;
		if (prepCheckTick < 0 || tick < prepCheckTick)
		{
			return;
		}
		prepCheckTick = -1;
		if (!config.prepCheck() || slayerGuide.getTask() == null)
		{
			return;
		}
		String missing = PrepCheck.missing(slayerGuide);
		if (missing != null)
		{
			chat(missing);
		}
	}

	/**
	 * @return the forecast for the rest of the current task, or null
	 */
	public TaskForecast getTaskRunForecast()
	{
		SlayerStatus status = slayerStatus;
		return TaskForecast.of(slayerLog.getTasks(), status.getTask(), status.getRemaining(), config.lootPrice());
	}

	/**
	 * @return cannon against no cannon on the current task, or null
	 */
	public TaskForecast.Cannon getTaskCannon()
	{
		return TaskForecast.cannon(slayerLog.getTasks(), slayerStatus.getTask(), cannonballPrice);
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
	/**
	 * Loads everything saved for the current account. Only after this finishes can
	 * anything be saved again, so a start that failed before loading never writes empty
	 * stats over the saved ones.
	 */
	private void loadProfileData()
	{
		loadedProfile = null;
		loadMonsters();
		loadSlayerLog();
		loadSlayerExtras();
		loadedProfile = configManager.getRSProfileKey();
		upgradeSavedMonsters();
		restoreMonster = null;
		restoreOffered = loadedProfile != null
			&& configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, RESTORE_KEY) == null
			&& hasLootRecords(null);
	}

	/**
	 * @param monster a monster's name, or null for any
	 * @return whether the Loot Tracker has records to restore from
	 */
	private boolean hasLootRecords(String monster)
	{
		if (!config.rememberMonsters() || loadedProfile == null)
		{
			return false;
		}
		for (String key : configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, loadedProfile, LOOT_TRACKER_PREFIX))
		{
			if (monster == null || monster.equals(BossGroups.trackedName(key.substring(LOOT_TRACKER_PREFIX.length()))))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * After a monster (or everything) was removed: offers to bring it back, in case it was
	 * by accident.
	 */
	private void offerRestore(String monster)
	{
		clientThread.invokeLater(() ->
		{
			if (canSave(configManager.getRSProfileKey(), loadedProfile) && hasLootRecords(monster))
			{
				restoreMonster = monster;
				restoreOffered = true;
				refreshPanelNow();
			}
		});
	}

	private static final String LOOT_TRACKER_GROUP = "loottracker";
	private static final String LOOT_TRACKER_PREFIX = "drops_NPC_";
	private static final String KILL_COUNT_GROUP = "killcount";

	/**
	 * @return whether this account can rebuild its Monsters tab from the Loot Tracker
	 */
	public boolean isRestoreOffered()
	{
		return restoreOffered;
	}

	/**
	 * @return whether an account is logged in with its stats loaded, so it can be restored
	 */
	public boolean canRestore()
	{
		return loadedProfile != null;
	}

	/**
	 * @return the one monster the restore is for, or null for all of them
	 */
	public String getRestoreMonster()
	{
		return restoreMonster;
	}

	/**
	 * Rebuilds the Monsters tab (or the one monster offered) from RuneLite's Loot Tracker and
	 * boss kill counts, combined with what's tracked now. Read only: the Loot Tracker's data
	 * isn't changed.
	 */
	public void restoreFromLootTracker(Set<String> only)
	{
		clientThread.invokeLater(() ->
		{
			String profile = configManager.getRSProfileKey();
			if (!canSave(profile, loadedProfile))
			{
				return;
			}
			LootTrackerRestore.Result result = LootTrackerRestore.merge(monsterTracker.exportState(), lootRecords(profile),
				killCounts(profile), only, gson, restoreItems());
			monsterTracker.importState(result.monsters);
			milestones.setRecord(monsterTracker.getBiggestHit(), monsterTracker.getTotalHits());
			saveMonsters();
			configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, RESTORE_KEY, "restored");
			restoreOffered = false;
			restoreMonster = null;
			String what = only != null && only.size() == 1 ? only.iterator().next()
				: result.restored == 1 ? "1 monster" : result.restored + " monsters";
			chat(result.restored == 0 ? "Combat & XP Tracker: the Loot Tracker has nothing saved to restore."
				: "Combat & XP Tracker: restored " + what + " (" + QuantityFormatter.formatNumber(result.kills)
					+ " kills) from the Loot Tracker.");
			refreshPanelNow();
		});
	}

	/**
	 * Lists what the Loot Tracker can bring back, for choosing which monsters to restore.
	 * The list is handed to {@code then} on the Swing thread.
	 */
	public void loadRestoreChoices(Consumer<List<MonsterTracker.Saved>> then)
	{
		clientThread.invokeLater(() ->
		{
			String profile = configManager.getRSProfileKey();
			if (!canSave(profile, loadedProfile))
			{
				return;
			}
			List<MonsterTracker.Saved> choices = LootTrackerRestore.read(lootRecords(profile), killCounts(profile), null,
				gson, restoreItems());
			SwingUtilities.invokeLater(() -> then.accept(choices));
		});
	}

	private List<String> lootRecords(String profile)
	{
		List<String> records = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, profile, LOOT_TRACKER_PREFIX))
		{
			String json = configManager.getConfiguration(LOOT_TRACKER_GROUP, profile, key);
			if (json != null)
			{
				records.add(json);
			}
		}
		return records;
	}

	private Map<String, Integer> killCounts(String profile)
	{
		Map<String, Integer> killCounts = new HashMap<>();
		for (String boss : configManager.getRSProfileConfigurationKeys(KILL_COUNT_GROUP, profile, ""))
		{
			Integer kc = configManager.getConfiguration(KILL_COUNT_GROUP, profile, boss, int.class);
			if (kc != null)
			{
				killCounts.put(boss, kc);
			}
		}
		return killCounts;
	}

	/**
	 * Item names and prices for restored loot. Client thread only.
	 */
	private LootTrackerRestore.Items restoreItems()
	{
		return new LootTrackerRestore.Items()
		{
			@Override
			public String name(int itemId)
			{
				return itemManager.getItemComposition(itemId).getName();
			}

			@Override
			public long price(int itemId)
			{
				return itemId == ItemID.COINS ? 1 : itemManager.getItemPrice(itemId);
			}

			@Override
			public long haPrice(int itemId)
			{
				return itemId == ItemID.COINS ? 1 : itemManager.getItemComposition(itemId).getHaPrice();
			}
		};
	}

	/**
	 * Hides the restore offer for this account.
	 */
	public void dismissRestore()
	{
		restoreOffered = false;
		restoreMonster = null;
		refreshPanelNow();
		clientThread.invokeLater(() ->
		{
			if (canSave(configManager.getRSProfileKey(), loadedProfile))
			{
				configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, RESTORE_KEY, "dismissed");
			}
		});
	}

	/**
	 * Saves data from before 2.1.2 once it's been upgraded, so the upgrade (streaks starting
	 * from 0, rates kept apart from restored kills) happens once rather than every login.
	 */
	private void upgradeSavedMonsters()
	{
		boolean upgraded = monsterTracker.takeUpgraded();
		// Stats rebuilt by the one-time restore build made before 2.0.2 kept no record of what
		// was restored: rates start again from now.
		if (configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, LEGACY_RESTORE_KEY) != null
			&& configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, RATES_RESTARTED_KEY) == null)
		{
			monsterTracker.startRatesAgain();
			configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, RATES_RESTARTED_KEY, "true");
			upgraded = true;
		}
		if (upgraded)
		{
			savedMonsterRevision = -1;
			saveMonsters();
		}
	}

	/**
	 * @return whether it's safe to save: logged in, to the account whose data was loaded
	 */
	static boolean canSave(String currentProfile, String loadedProfile)
	{
		return currentProfile != null && currentProfile.equals(loadedProfile);
	}

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
		if (!canSave(configManager.getRSProfileKey(), loadedProfile))
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
		sessionSupplies.reset();
		sessionUptime.reset();
		sessionDeaths.reset();
		sessionDamageTaken.reset();
		combinedDropTracker.reset();
		sessionMonsters.reset();
		if (allTimeToo)
		{
			allTimeDeaths.reset();
			allTimeDamageTaken.reset();
			monsterTracker.reset();
			allTimeSupplies.reset();
			allTimeUptime.reset();
			bossTimes.reset();
			prayerDrain.importState(null);
			offerRestore(null);
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
		allTimeSupplies.remove(name);
		sessionSupplies.remove(name);
		allTimeUptime.remove(name);
		sessionUptime.remove(name);
		bossTimes.remove(name);
		allTimeDeaths.remove(name);
		sessionDeaths.remove(name);
		allTimeDamageTaken.remove(name);
		sessionDamageTaken.remove(name);
		offerRestore(name);
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
	/**
	 * @return kills of the monster being fought to the next combat level, or null
	 */
	public LevelForecast.CombatRow getKillsToCombatLevel()
	{
		if (lastTargetName == null || prayerLevel <= 0)
		{
			return null;
		}
		int[] xp = new int[LevelForecast.COMBAT_SKILLS.length];
		double[] perKill = new double[xp.length];
		for (int i = 0; i < xp.length; i++)
		{
			Skill skill = LevelForecast.COMBAT_SKILLS[i];
			SkillProgress progress = skillProgress.get(skill);
			if (progress == null || !progress.isXpKnown())
			{
				return null;
			}
			xp[i] = progress.getCurrentXp();
			perKill[i] = Math.max(0, getXpPerKill(skill));
		}
		return LevelForecast.killsToCombatLevel(xp, perKill, prayerLevel);
	}

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
		prayerPointsNow = points;
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
		if (!canSave(configManager.getRSProfileKey(), loadedProfile))
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

	// ---- Supplies used ---------------------------------------------------------------------------

	/**
	 * Records supplies used. They count towards the monster fought in the last 30 seconds,
	 * and its slayer task. Cannonballs count for the task even while AFK.
	 */
	private void recordSupply(SupplyLedger.Category category, long gp, long count)
	{
		int tick = client.getTickCount();
		boolean fighting = fightMonster != null && tick - lastFightTick <= SUPPLY_TICKS && tick >= lastFightTick;
		boolean cannon = category == SupplyLedger.Category.CANNONBALLS;
		if (!fighting && !cannon)
		{
			return;
		}
		if (fighting)
		{
			allTimeSupplies.record(fightMonster, category, gp, count);
			sessionSupplies.record(fightMonster, category, gp, count);
		}
		if (taskSession.isActive() && (fighting ? taskSession.isTaskMonster(fightMonster) : cannon))
		{
			taskSession.recordSupplies(gp, cannon ? (int) count : 0);
		}
		panelDirty = true;
	}

	/**
	 * Each tick: runes and ammo used up since the last tick, while fighting.
	 */
	private void trackRunesAndAmmo()
	{
		int tick = client.getTickCount();
		boolean fighting = fightMonster != null && tick - lastFightTick <= FIGHT_TICKS && tick >= lastFightTick
			&& !isBankOpen();

		Map<Integer, Long> runes = carriedRuneItems();
		if (lastRunes != null && fighting)
		{
			Map<Integer, Long> used = SupplyLedger.decreases(lastRunes, runes);
			long count = 0;
			long gp = 0;
			for (Map.Entry<Integer, Long> e : used.entrySet())
			{
				count += e.getValue();
				gp += e.getValue() * itemManager.getItemPrice(e.getKey());
			}
			recordSupply(SupplyLedger.Category.RUNES, gp, count);
		}
		lastRunes = runes;

		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item ammo = worn == null ? null : worn.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());
		int ammoId = ammo == null ? -1 : ammo.getId();
		long ammoQty = ammo == null ? 0 : ammo.getQuantity();
		if (fighting && ammoId > 0 && ammoId == lastAmmoId && ammoQty < lastAmmoQty)
		{
			long used = lastAmmoQty - ammoQty;
			recordSupply(SupplyLedger.Category.AMMO, used * itemManager.getItemPrice(ammoId), used);
		}
		lastAmmoId = ammoId;
		lastAmmoQty = ammoQty;
	}

	/**
	 * Rune items in the inventory and rune pouch, by item.
	 */
	private Map<Integer, Long> carriedRuneItems()
	{
		Map<Integer, Long> runes = new HashMap<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		boolean pouch = false;
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (RuneSupply.isRune(item.getId()))
				{
					runes.merge(item.getId(), (long) item.getQuantity(), Long::sum);
				}
				pouch |= RUNE_POUCHES.contains(item.getId());
			}
		}
		if (pouch)
		{
			EnumComposition types = client.getEnum(EnumID.RUNEPOUCH_RUNE);
			for (int i = 0; i < POUCH_TYPES.length; i++)
			{
				int type = client.getVarbitValue(POUCH_TYPES[i]);
				int amount = client.getVarbitValue(POUCH_AMOUNTS[i]);
				if (type > 0 && amount > 0)
				{
					runes.merge(types.getIntValue(type), (long) amount, Long::sum);
				}
			}
		}
		return runes;
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	/**
	 * The cannon's ball count went down: a shot each.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (CombatAchievements.isCompletionVarp(event.getVarpId()))
		{
			// A task was completed: read them again next tick.
			combatAchievementsLoaded = false;
			return;
		}
		if (event.getVarpId() != VarPlayerID.ROCKTHROWER)
		{
			return;
		}
		int balls = event.getValue();
		int fired = lastCannonBalls - balls;
		// Picking the cannon up empties it at once; only single shots are used up.
		if (lastCannonBalls >= 0 && fired > 0 && fired <= MAX_CANNON_SHOT)
		{
			lastCannonShotTick = client.getTickCount();
			recordSupply(SupplyLedger.Category.CANNONBALLS, fired * (long) itemManager.getItemPrice(ItemID.MCANNONBALL), fired);
		}
		lastCannonBalls = balls;
	}

	/**
	 * @return supplies used fighting a monster, for the range shown, or null
	 */
	public SupplyLedger.Totals getSupplies(String monster, MonsterRange range)
	{
		return (range == MonsterRange.SESSION ? sessionSupplies : allTimeSupplies).get(monster);
	}

	public int getSuppliesRevision()
	{
		return allTimeSupplies.getRevision() + sessionSupplies.getRevision();
	}

	private void saveSupplies()
	{
		if (!canSave(configManager.getRSProfileKey(), loadedProfile))
		{
			return;
		}
		int revision = allTimeSupplies.getRevision();
		if (revision == savedSuppliesRevision)
		{
			return;
		}
		savedSuppliesRevision = revision;
		if (!config.rememberMonsters() || allTimeSupplies.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, SUPPLIES_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, SUPPLIES_KEY, gson.toJson(allTimeSupplies.exportState()));
	}

	private void loadSupplies()
	{
		Map<String, SupplyLedger.Totals> saved = null;
		if (config.rememberMonsters() && configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, SUPPLIES_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					saved = gson.fromJson(json, new TypeToken<Map<String, SupplyLedger.Totals>>()
					{
					}.getType());
				}
				catch (JsonParseException e)
				{
					saved = null;
				}
			}
		}
		allTimeSupplies.importState(saved);
		savedSuppliesRevision = allTimeSupplies.getRevision();
	}

	// ---- Attack uptime and boss times ----------------------------------------------------------

	/**
	 * @return attack uptime fighting a monster from 0 to 100, for the range shown, or -1
	 */
	public int getUptime(String monster, MonsterRange range)
	{
		return (range == MonsterRange.SESSION ? sessionUptime : allTimeUptime).percent(monster);
	}

	/**
	 * @return damage per second fighting a monster, for the range shown, or -1
	 */
	public double getDps(String monster, MonsterRange range)
	{
		return (range == MonsterRange.SESSION ? sessionUptime : allTimeUptime).dps(monster);
	}

	/**
	 * @return this session's damage per second over every monster, or -1
	 */
	public double getSessionDps()
	{
		return sessionUptime.totalDps();
	}

	/**
	 * @return this session's attack uptime over every monster from 0 to 100, or -1
	 */
	public int getSessionUptime()
	{
		return sessionUptime.totalPercent();
	}

	/**
	 * @return a boss's kill times, or null
	 */
	public BossTimes.Times getBossTimes(String monster)
	{
		return bossTimes.get(monster);
	}

	public int getFightStatsRevision()
	{
		return allTimeUptime.getRevision() + sessionUptime.getRevision() + bossTimes.getRevision()
			+ allTimeDeaths.getRevision() + sessionDeaths.getRevision() + allTimeDamageTaken.getRevision()
			+ sessionDamageTaken.getRevision();
	}

	private void saveUptime()
	{
		if (!canSave(configManager.getRSProfileKey(), loadedProfile) || allTimeUptime.getRevision() == savedUptimeRevision)
		{
			return;
		}
		savedUptimeRevision = allTimeUptime.getRevision();
		if (!config.rememberMonsters() || allTimeUptime.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, UPTIME_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, UPTIME_KEY, gson.toJson(allTimeUptime.exportState()));
	}

	private void loadUptime()
	{
		allTimeUptime.importState(loadJson(UPTIME_KEY, new TypeToken<Map<String, AttackUptime.Ticks>>()
		{
		}.getType()));
		savedUptimeRevision = allTimeUptime.getRevision();
	}

	/**
	 * @return deaths to a monster, for the range shown, or null for none
	 */
	public DeathLedger.Deaths getDeaths(String monster, MonsterRange range)
	{
		return (range == MonsterRange.SESSION ? sessionDeaths : allTimeDeaths).get(monster);
	}

	private void saveDeaths()
	{
		if (!canSave(configManager.getRSProfileKey(), loadedProfile) || allTimeDeaths.getRevision() == savedDeathsRevision)
		{
			return;
		}
		savedDeathsRevision = allTimeDeaths.getRevision();
		if (!config.rememberMonsters() || allTimeDeaths.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, DEATHS_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, DEATHS_KEY, gson.toJson(allTimeDeaths.exportState()));
	}

	private void loadDeaths()
	{
		allTimeDeaths.importState(loadJson(DEATHS_KEY, new TypeToken<Map<String, DeathLedger.Deaths>>()
		{
		}.getType()));
		savedDeathsRevision = allTimeDeaths.getRevision();
	}

	/**
	 * @return damage taken per kill on a boss, for the range shown, or null
	 */
	public DamageTaken.Stats getDamageTaken(String monster, MonsterRange range)
	{
		return (range == MonsterRange.SESSION ? sessionDamageTaken : allTimeDamageTaken).get(monster);
	}

	/**
	 * @return ticks lost between attacks on a monster, for the range shown, or -1
	 */
	public long getIdleTicks(String monster, MonsterRange range)
	{
		return (range == MonsterRange.SESSION ? sessionUptime : allTimeUptime).idleTicks(monster);
	}

	private void saveDamageTaken()
	{
		if (!canSave(configManager.getRSProfileKey(), loadedProfile) || allTimeDamageTaken.getRevision() == savedDamageTakenRevision)
		{
			return;
		}
		savedDamageTakenRevision = allTimeDamageTaken.getRevision();
		if (!config.rememberMonsters() || allTimeDamageTaken.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, DAMAGE_TAKEN_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, DAMAGE_TAKEN_KEY,
			gson.toJson(allTimeDamageTaken.exportState()));
	}

	private void loadDamageTaken()
	{
		allTimeDamageTaken.importState(loadJson(DAMAGE_TAKEN_KEY, new TypeToken<Map<String, DamageTaken.Stats>>()
		{
		}.getType()));
		savedDamageTakenRevision = allTimeDamageTaken.getRevision();
	}

	/**
	 * A JSON file bundled with the plugin, or null if it can't be read.
	 */
	private <T> T loadResource(String name, java.lang.reflect.Type type)
	{
		try (java.io.InputStream in = CombatXpTrackerPlugin.class.getResourceAsStream(name))
		{
			return in == null ? null
				: gson.fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), type);
		}
		catch (java.io.IOException | JsonParseException e)
		{
			return null;
		}
	}

	private void saveBossTimes()
	{
		if (!canSave(configManager.getRSProfileKey(), loadedProfile) || bossTimes.getRevision() == savedBossTimesRevision)
		{
			return;
		}
		savedBossTimesRevision = bossTimes.getRevision();
		if (!config.rememberMonsters() || bossTimes.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(CombatXpTrackerConfig.GROUP, BOSS_TIMES_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(CombatXpTrackerConfig.GROUP, BOSS_TIMES_KEY, gson.toJson(bossTimes.exportState()));
	}

	private void loadBossTimes()
	{
		bossTimes.importState(loadJson(BOSS_TIMES_KEY, new TypeToken<Map<String, BossTimes.Times>>()
		{
		}.getType()));
		savedBossTimesRevision = bossTimes.getRevision();
	}

	/**
	 * @return the saved JSON for this account under a key, or null if there's none or it's
	 * unreadable
	 */
	private <T> T loadJson(String key, java.lang.reflect.Type type)
	{
		if (!config.rememberMonsters() || configManager.getRSProfileKey() == null)
		{
			return null;
		}
		String json = configManager.getRSProfileConfiguration(CombatXpTrackerConfig.GROUP, key);
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, type);
		}
		catch (JsonParseException e)
		{
			return null;
		}
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
	 * @return the live kill timer, or null when it's turned off
	 */
	public KillTimer getKillTimer()
	{
		return config.liveKillTimer() ? killTimer : null;
	}

	/**
	 * @return missing collection log items with a known drop rate, driest first
	 */
	public List<DryCalculator.Dry> getDryItems(String monster)
	{
		CollectionLog.Status log = getCollectionStatus(monster);
		if (log == null || log.isComplete())
		{
			return Collections.emptyList();
		}
		return dryCalculator.dry(monster, log.getMissing(), log.getKillCount());
	}

	/**
	 * 2.1.2 lowered the rare drop value's default from 1M to 150K. RuneLite saves defaults, so
	 * players still on the old one are moved once; a value they picked themselves is kept.
	 */
	private void migrateRareDropValue()
	{
		if (configManager.getConfiguration(CombatXpTrackerConfig.GROUP, RARE_DROP_MIGRATED_KEY) != null)
		{
			return;
		}
		int saved = config.rareDropValue();
		if (migratedRareDropValue(saved) != saved)
		{
			configManager.setConfiguration(CombatXpTrackerConfig.GROUP, "rareDropValue", migratedRareDropValue(saved));
		}
		configManager.setConfiguration(CombatXpTrackerConfig.GROUP, RARE_DROP_MIGRATED_KEY, true);
	}

	static int migratedRareDropValue(int saved)
	{
		return saved == OLD_RARE_DROP_DEFAULT ? CombatXpTrackerConfig.RARE_DROP_DEFAULT : saved;
	}

	/**
	 * @return whether an item is one of a monster's collection log uniques
	 */
	private boolean isUnique(String monster, String item)
	{
		if (item == null)
		{
			return false;
		}
		BossUniques bundled = bossUniques;
		if (bundled != null && bundled.isUnique(monster, item))
		{
			return true;
		}
		// A page opened in game also counts, for anything the bundled list doesn't have yet.
		for (CollectionLog.Item i : collectionLog.items(monster))
		{
			if (item.equalsIgnoreCase(i.name))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return whether the monster has collection log uniques to count kills since
	 */
	public boolean hasUniques(String monster)
	{
		BossUniques bundled = bossUniques;
		return bundled != null && bundled.hasUniques(monster) || !collectionLog.items(monster).isEmpty();
	}

	/**
	 * @return a boss's Combat Achievements, or an empty list
	 */
	public List<CombatAchievements.Task> getCombatAchievements(String monster)
	{
		return config.combatAchievements() ? combatAchievements.tasks(monster) : Collections.emptyList();
	}

	public int getCombatAchievementsRevision()
	{
		return combatAchievements.getRevision();
	}

	/**
	 * The bundled drop rates (from the OSRS Wiki). An empty calculator if they can't be read.
	 */
	private DryCalculator loadDryCalculator()
	{
		return new DryCalculator(loadResource("unique_rates.json", new TypeToken<Map<String, Map<String, Double>>>()
		{
		}.getType()));
	}

	/**
	 * @return the monster's collection log progress, or null if its page hasn't been opened
	 */
	public CollectionLog.Status getCollectionStatus(String monster)
	{
		String title = collectionLog.titleFor(monster);
		return title == null ? null : collectionLog.status(monster, pageKills(title));
	}

	/**
	 * @return the items on the monster's collection log page, or an empty list
	 */
	public List<CollectionLog.Item> getCollectionItems(String monster)
	{
		return collectionLog.items(monster);
	}

	public int getCollectionRevision()
	{
		return collectionLog.getRevision();
	}

	private void saveCollectionLog()
	{
		if (!canSave(configManager.getRSProfileKey(), loadedProfile))
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
		if (!canSave(configManager.getRSProfileKey(), loadedProfile))
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
		loadSupplies();
		loadUptime();
		loadBossTimes();
		loadDeaths();
		loadDamageTaken();
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
		if (!canSave(configManager.getRSProfileKey(), loadedProfile))
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

	/**
	 * @return the current task's gold per hour, banking and walking included, or -1
	 */
	public double getTaskGpPerHour()
	{
		return taskSession.isActive() ? taskSession.getTripGpPerHour(config.lootPrice(), System.currentTimeMillis(), true) : -1;
	}

	/**
	 * @return whether the player is away from the current task, so its gold per hour is falling
	 */
	public boolean isTaskAway()
	{
		return taskSession.isActive() && taskSession.isAway(System.currentTimeMillis(), true);
	}

	/**
	 * @return the monster being done, for the overlay's gold per hour, or null
	 */
	public MonsterTracker.Snapshot getTripSnapshot()
	{
		return tripSnapshot;
	}

	private MonsterTracker.Snapshot currentTripSnapshot()
	{
		String name = monsterTracker.getCurrentTrip();
		if (name == null)
		{
			return null;
		}
		for (MonsterTracker.Snapshot s : monsterTracker.snapshot(config.lootPrice(), MonsterTracker.itemMatcher(config.ignoredItems())))
		{
			if (s.getName().equals(name))
			{
				return s;
			}
		}
		return null;
	}

	/**
	 * @return increases every 5 ticks while gold per hour is changing, for the panel to redraw
	 */
	public int getRateTick()
	{
		return rateTick;
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
