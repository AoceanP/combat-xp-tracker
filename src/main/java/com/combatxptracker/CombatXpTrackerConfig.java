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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup(CombatXpTrackerConfig.GROUP)
public interface CombatXpTrackerConfig extends Config
{
	String GROUP = "combatxptracker";

	@ConfigSection(
		name = "Goals",
		description = "Skill goals and XP/hr",
		position = 0
	)
	String goalsSection = "goals";

	@ConfigSection(
		name = "Appearance",
		description = "How the sidebar looks",
		position = -1
	)
	String appearanceSection = "appearance";

	@ConfigSection(
		name = "Combat",
		description = "Damage, max hit, kills to level and milestones",
		position = 1
	)
	String combatSection = "combat";

	@ConfigSection(
		name = "Monsters",
		description = "The Monsters tab: what's remembered, loot values, the target card and kill messages",
		position = 2
	)
	String monstersSection = "monsters";

	@ConfigSection(
		name = "Slayer",
		description = "The Slayer tab, task summaries and superiors",
		position = 3
	)
	String slayerSection = "slayer";

	@ConfigSection(
		name = "Overlays",
		description = "On-screen overlay and infoboxes",
		position = 4
	)
	String overlaySection = "overlays";

	@ConfigSection(
		name = "Supplies & loot",
		description = "Loot despawn alert and casts left, for long trips",
		position = 5
	)
	String suppliesSection = "supplies";

	@ConfigItem(
		keyName = "lootDespawnAlert",
		name = "Loot despawn alert",
		description = "Shows a countdown to when your oldest monster drop on the ground disappears, and notifies you "
			+ "shortly before, so you can pick everything up in one trip. Only your own drops from monsters count.",
		position = 0,
		section = suppliesSection
	)
	default Notification lootDespawnAlert()
	{
		return Notification.OFF;
	}

	@Range(min = 5, max = 110)
	@ConfigItem(
		keyName = "lootDespawnSeconds",
		name = "Warn before despawn",
		description = "How many seconds before your oldest drop disappears to warn you.",
		position = 1,
		section = suppliesSection
	)
	@net.runelite.client.config.Units(net.runelite.client.config.Units.SECONDS)
	default int lootDespawnSeconds()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "lootDespawnMinValue",
		name = "Ignore drops under",
		description = "Drops worth less than this (gp, Grand Exchange price) don't count for the despawn alert.",
		position = 2,
		section = suppliesSection
	)
	default int lootDespawnMinValue()
	{
		return 1000;
	}

	@ConfigItem(
		keyName = "castsLeft",
		name = "Casts left",
		description = "While autocasting, an infobox shows how many casts your runes allow, counting the rune pouch, "
			+ "combination runes, elemental staves and tomes.",
		position = 3,
		section = suppliesSection
	)
	default boolean castsLeft()
	{
		return true;
	}

	@ConfigItem(
		keyName = "castsLowWarning",
		name = "Low on casts",
		description = "The casts left infobox turns gold below this many casts.",
		position = 4,
		section = suppliesSection
	)
	default int castsLowWarning()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "castsLowNotification",
		name = "Notify when low on casts",
		description = "Sends a notification when your casts left drop below the number above.",
		position = 5,
		section = suppliesSection
	)
	default Notification castsLowNotification()
	{
		return Notification.OFF;
	}

	// ---- Goals ---------------------------------------------------------------

	@ConfigItem(
		keyName = "xpHrInterval",
		name = "XP/hr averaging window",
		description = "With the 'Recent' XP/hr mode: how many seconds of recent activity to average over. "
			+ "Lower = more reactive, higher = smoother.",
		position = 0,
		section = goalsSection
	)
	@Range(min = 5, max = 300)
	default int xpHrIntervalSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "xpRateMode",
		name = "XP/hr mode",
		description = "Recent: the last few seconds of XP drops, reacts quickly. "
			+ "Whole session: all XP this session divided by time spent training (breaks over 5 minutes aren't counted), steadier.",
		position = 1,
		section = goalsSection
	)
	default XpRateMode xpRateMode()
	{
		return XpRateMode.RECENT;
	}

	@Alpha
	@ConfigItem(
		keyName = "goalBarColor",
		name = "Goal bar colour",
		description = "Fill colour of the goal progress bars. Left at the default, each theme uses its own accent colour. "
			+ "Right-click a goal in the panel to give one skill its own colour.",
		position = 2,
		section = goalsSection
	)
	default Color goalBarColor()
	{
		return new Color(79, 195, 247);
	}

	@ConfigItem(
		keyName = "goalNotification",
		name = "Notify when a goal is reached",
		description = "Sends a RuneLite notification when you reach a goal.",
		position = 3,
		section = goalsSection
	)
	default Notification goalNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "showNextLevelTime",
		name = "Show time to next level",
		description = "Adds how long until your next level to each goal, as well as the time to the goal itself.",
		position = 4,
		section = goalsSection
	)
	default boolean showNextLevelTime()
	{
		return true;
	}

	@ConfigItem(
		keyName = "goalChatMessage",
		name = "Chat message when a goal is reached",
		description = "Adds a message to your chatbox when you reach a goal. Only you can see it.",
		position = 5,
		section = goalsSection
	)
	default boolean goalChatMessage()
	{
		return true;
	}

	// ---- Combat --------------------------------------------------------------

	@ConfigItem(
		keyName = "showMeleeMaxHit",
		name = "Show max hit",
		description = "Shows your max hit for the style you're using (melee, ranged or magic) from your gear, levels, prayers, "
			+ "attack style, Void, Salve amulet and Slayer helm, plus obsidian, Inquisitor's, crystal armour, wilderness "
			+ "weapons, and separate rows for weapons strong against dragons, demons or kalphites.",
		position = 0,
		section = combatSection
	)
	default boolean showMeleeMaxHit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "flashMaxHitChange",
		name = "Flash max hit changes",
		description = "When your max hit goes up or down (a potion, a prayer, a gear swap, a boost wearing off), it "
			+ "flashes green or red for a few seconds with the change, e.g. 34 (+3).",
		position = 1,
		section = combatSection
	)
	default boolean flashMaxHitChange()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showZeroHits",
		name = "Include 0-damage hits in average",
		description = "Whether misses (0 damage) count toward your average hit.",
		position = 3,
		section = combatSection
	)
	default boolean showZeroHits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackMonsterLoot",
		name = "Track monster drops",
		description = "Records each monster's drops and their Grand Exchange value in the Monsters tab.",
		position = 0,
		section = monstersSection
	)
	default boolean trackMonsterLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resetHitsOnLogout",
		name = "Reset stats on logout",
		description = "Clears damage stats and this session's monsters when you log out. All-time monsters are kept.",
		position = 5,
		section = combatSection
	)
	default boolean resetHitsOnLogout()
	{
		return false;
	}

	@ConfigItem(
		keyName = "rememberMonsters",
		name = "Remember monsters",
		description = "Keeps the Monsters tab between sessions and client restarts, separately for each account.",
		position = 1,
		section = monstersSection
	)
	default boolean rememberMonsters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restoreFromLootTracker",
		name = "Restore from Loot Tracker",
		description = "Tick to bring back kills and drops from RuneLite's Loot Tracker, for all monsters or the ones you choose."
			+ " It shows the Restore card in the Monsters tab, then unticks itself. Combined with what's tracked now, never doubled.",
		position = 30,
		section = monstersSection
	)
	default boolean restoreFromLootTracker()
	{
		return false;
	}

	@ConfigItem(
		keyName = "monsterSort",
		name = "Sort monsters by",
		description = "Order of the Monsters tab. You can also change it at the top of the tab.",
		position = 3,
		section = monstersSection
	)
	default MonsterSort monsterSort()
	{
		return MonsterSort.RECENT;
	}

	@ConfigItem(
		keyName = "hiddenMonsters",
		name = "Hidden monsters",
		description = "Monsters left out of the Monsters tab, separated by commas. Right-click a monster in the panel to hide it.",
		position = 7,
		section = monstersSection
	)
	default String hiddenMonsters()
	{
		return "";
	}

	@ConfigItem(
		keyName = "monsterRange",
		name = "Monsters tab shows",
		description = "This session, or everything remembered for this account. You can also switch at the top of the tab.",
		position = 2,
		section = monstersSection
	)
	default MonsterRange monsterRange()
	{
		return MonsterRange.ALL_TIME;
	}

	@ConfigItem(
		keyName = "lootPrice",
		name = "Value loot at",
		description = "Grand Exchange price, or High Alchemy value (useful for ironmen, who can't use the GE).",
		position = 4,
		section = monstersSection
	)
	default LootPrice lootPrice()
	{
		return LootPrice.GRAND_EXCHANGE;
	}

	@ConfigItem(
		keyName = "ignoredItems",
		name = "Ignored items",
		description = "Items left out of loot and its value, separated by commas. Use * as a wildcard, e.g. 'bones, *ashes'. "
			+ "Right-click an item in the Monsters tab to ignore it.",
		position = 6,
		section = monstersSection
	)
	default String ignoredItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rareDropValue",
		name = "Rare drop value (gp)",
		description = "Items worth at least this much each get a gold border in the loot grid, "
			+ "and each monster shows how many kills since its last one (the dry streak).",
		position = 5,
		section = monstersSection
	)
	@Range(min = 1_000)
	default int rareDropValue()
	{
		return RARE_DROP_DEFAULT;
	}

	/** The rare drop value's default since 2.1.2. It was 1M. */
	int RARE_DROP_DEFAULT = 150_000;

	@ConfigItem(
		keyName = "taskSummary",
		name = "Slayer task summary",
		description = "When a slayer task is finished, adds a chat message with its kills, time, loot, GP/hr and biggest hit.",
		position = 1,
		section = slayerSection
	)
	default boolean taskSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "killSummary",
		name = "Boss kill summary",
		description = "When you kill a boss (anything the game gives a kill count for), adds a chat message with the fight time, "
			+ "your damage and its share of the boss's hitpoints, and your biggest hit.",
		position = 9,
		section = monstersSection
	)
	default boolean killSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "deathMessage",
		name = "Death message",
		description = "When you die to a monster, a chat message with how many times you've died to it and what you were "
			+ "carrying. Deaths are always counted on the monster's card.",
		position = 10,
		section = monstersSection
	)
	default boolean deathMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "combatAchievements",
		name = "Combat Achievements",
		description = "Shows each boss's Combat Achievements on its card, how many are done, and whether your best kill "
			+ "is fast enough for its speed tasks.",
		position = 11,
		section = monstersSection
	)
	default boolean combatAchievements()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropReveal",
		name = "Big drop reveal",
		description = "A short case-opening style reveal at the top of the Monsters tab for a new collection log item, or a "
			+ "drop worth more than your rare drop value (1M at least).",
		position = 12,
		section = monstersSection
	)
	default boolean dropReveal()
	{
		return true;
	}

	@ConfigItem(
		keyName = "uniqueScreenshot",
		name = "Screenshot big drops",
		description = "Saves a screenshot of each new collection log item and rare drop, in RuneLite's screenshots "
			+ "folder under Uniques.",
		position = 13,
		section = monstersSection
	)
	default boolean uniqueScreenshot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "killsToLevel",
		name = "Kills to next level",
		description = "Shows how many more kills of the monster you're fighting each combat skill needs for its next level, "
			+ "your next combat level, and what the rest of your slayer task will give. Uses your real XP per kill once "
			+ "you've killed one.",
		position = 4,
		section = combatSection
	)
	default boolean killsToLevel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTargetCard",
		name = "Target card",
		description = "A card at the top of the Monsters tab for the monster you're fighting: its health, hits left to kill it, "
			+ "your time per kill and your damage per second.",
		position = 8,
		section = monstersSection
	)
	default boolean showTargetCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "slayerLog",
		name = "Slayer task log",
		description = "Keeps a log of your finished slayer tasks (kills, time, XP, loot, superiors, points) in the Slayer tab, "
			+ "saved separately for each account.",
		position = 0,
		section = slayerSection
	)
	default boolean slayerLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "superiorNotification",
		name = "Notify on superior spawn",
		description = "Sends a RuneLite notification when a superior slayer monster appears. RuneLite's own Slayer plugin "
			+ "can do this too, so it's off by default.",
		position = 3,
		section = slayerSection
	)
	default Notification superiorNotification()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "prepCheck",
		name = "Prep check",
		description = "When you close your bank on a slayer task, a chat message if anything the task needs isn't with you, "
			+ "e.g. a mirror shield for basilisks.",
		position = 4,
		section = slayerSection
	)
	default boolean prepCheck()
	{
		return true;
	}

	@ConfigItem(
		keyName = "taskForecast",
		name = "Task forecast",
		description = "When you get a slayer task you've done before, a chat message with how long it took you, the XP, "
			+ "loot and supplies, from your last runs of it in the task log.",
		position = 5,
		section = slayerSection
	)
	default boolean taskForecast()
	{
		return true;
	}

	@ConfigItem(
		keyName = "boostReminder",
		name = "Boost reminder",
		description = "At the start of a boss fight, a chat message if you have no potion boost, no offensive prayer, or "
			+ "(on the Arceuus spellbook) no thrall for the style you're attacking with.",
		position = 8,
		section = combatSection
	)
	default boolean boostReminder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "milestoneMessages",
		name = "Milestone messages",
		description = "A chat message for milestones: 100, 500, 1,000 (and more) kills of a monster, total kills, "
			+ "your 10th, 50th, 100th (and more) slayer task, a new biggest hit ever, and a new boss personal best.",
		position = 6,
		section = combatSection
	)
	default boolean milestoneMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "milestoneScreenshot",
		name = "Screenshot milestones",
		description = "Also saves a screenshot for each milestone, in RuneLite's screenshots folder under Milestones.",
		position = 7,
		section = combatSection
	)
	default boolean milestoneScreenshot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "checkBank",
		name = "Check bank for task items",
		description = "Remembers what's in your bank when you open it, so the Slayer tab's task guide can say whether an "
			+ "item you need is in your bank or you don't own one. Saved on your computer only, per account.",
		position = 2,
		section = slayerSection
	)
	default boolean checkBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEffectiveLevels",
		name = "Show effective levels",
		description = "Shows your effective Attack and Strength (or Ranged, or Magic) under the max hit: your level with "
			+ "prayer, attack style, the hidden +8 and Void included. It's what accuracy and max hit are worked out from.",
		position = 2,
		section = combatSection
	)
	default boolean showEffectiveLevels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "panelTheme",
		name = "Theme",
		description = "Colours of the sidebar. Dark is the original look.",
		position = 0,
		section = appearanceSection
	)
	default PanelTheme panelTheme()
	{
		return PanelTheme.DARK;
	}

	@ConfigItem(
		keyName = "goalOrder",
		name = "Goal order",
		description = "The order of your goals. Set by dragging them in the panel.",
		hidden = true
	)
	default String goalOrder()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pinnedMonsters",
		name = "Pinned monsters",
		description = "Monsters pinned to the top of the Monsters tab. Set by right-clicking a monster.",
		hidden = true
	)
	default String pinnedMonsters()
	{
		return "";
	}

	@ConfigItem(
		keyName = "collapsedMonsters",
		name = "Collapsed monsters",
		description = "Monster cards collapsed in the panel. Set by clicking a card's header.",
		hidden = true
	)
	default String collapsedMonsters()
	{
		return "";
	}

	// ---- Overlays ------------------------------------------------------------

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show on-screen overlay",
		description = "Shows average damage, biggest hit, max hit and your goals' XP/hr on screen.",
		position = 0,
		section = overlaySection
	)
	default boolean showOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showInfobox",
		name = "Show goal infoboxes",
		description = "Shows an infobox per goal with its progress.",
		position = 1,
		section = overlaySection
	)
	default boolean showInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCombinedDrop",
		name = "Show damage on XP drops",
		description = "When an XP gain and a hit land close together, shows the hit next to that skill on the overlay.",
		position = 2,
		section = overlaySection
	)
	default boolean showCombinedDrop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "combinedDropWindowMillis",
		name = "XP/damage pairing window (ms)",
		description = "How close together (in milliseconds) an XP gain and a hit need to land to be paired.",
		position = 3,
		section = overlaySection
	)
	@Range(min = 100, max = 3000)
	default int combinedDropWindowMillis()
	{
		return 600;
	}

	@ConfigItem(
		keyName = "overlayShowDamage",
		name = "Overlay: average and biggest hit",
		description = "Show your average and biggest hit on the on-screen overlay.",
		position = 4,
		section = overlaySection
	)
	default boolean overlayShowDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayShowMaxHit",
		name = "Overlay: max hit",
		description = "Show your max hit (and special attack max hit) on the on-screen overlay.",
		position = 5,
		section = overlaySection
	)
	default boolean overlayShowMaxHit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayGpPerHour",
		name = "Overlay: GP/hr",
		description = "Show gold per hour for the monster you're doing on the on-screen overlay. It counts the whole trip,"
			+ " banking and walking included, and updates every 5 ticks.",
		position = 13,
		section = overlaySection
	)
	default boolean overlayGpPerHour()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayShowTask",
		name = "Overlay: slayer task",
		description = "Show your slayer task's kills left on the on-screen overlay.",
		position = 6,
		section = overlaySection
	)
	default boolean overlayShowTask()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayShowGoals",
		name = "Overlay: goals",
		description = "Show each goal's XP/hr and progress on the on-screen overlay.",
		position = 7,
		section = overlaySection
	)
	default boolean overlayShowGoals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayShowKillsToLevel",
		name = "Overlay: kills to level",
		description = "Show kills to the next level in the skill you're training on the on-screen overlay.",
		position = 8,
		section = overlaySection
	)
	default boolean overlayShowKillsToLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "liveKillTimer",
		name = "Live kill timer",
		description = "A timer during boss fights, from your first attack, next to your personal best. It's gold while "
			+ "you're on pace for a new best.",
		position = 11,
		section = overlaySection
	)
	default boolean liveKillTimer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropRevealOnScreen",
		name = "Drop reveal on screen",
		description = "Also shows the big drop reveal on the game screen for a few seconds, not just in the sidebar. "
			+ "Off by default.",
		position = 12,
		section = overlaySection
	)
	default boolean dropRevealOnScreen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "attackTimer",
		name = "Attack timer",
		description = "While fighting, shows the ticks until your next attack above your character. It learns the real speed of"
			+ " each weapon and attack style from your attacks, and follows weapon swaps. It turns green at 0 when you can attack.",
		position = 9,
		section = overlaySection
	)
	default boolean attackTimer()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "attackTimerColour",
		name = "Attack timer colour",
		description = "Colour of the attack timer while counting down.",
		position = 10,
		section = overlaySection
	)
	default Color attackTimerColour()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "attackTimerStyleColours",
		name = "Attack timer: colour by style",
		description = "Colour the attack timer by what you attacked with: red for melee, green for ranged, blue for magic."
			+ " Turn off to use the attack timer colour above.",
		position = 10,
		section = overlaySection
	)
	default boolean attackTimerStyleColours()
	{
		return true;
	}
}
