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
		name = "Combat",
		description = "Damage, max hit and per-monster tracking",
		position = 1
	)
	String combatSection = "combat";

	@ConfigSection(
		name = "Overlays",
		description = "On-screen overlay and infoboxes",
		position = 2
	)
	String overlaySection = "overlays";

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
		description = "Fill colour of the goal progress bars. Right-click a goal in the panel to give one skill its own colour.",
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
		keyName = "goalChatMessage",
		name = "Chat message when a goal is reached",
		description = "Adds a message to your chatbox when you reach a goal. Only you can see it.",
		position = 4,
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
			+ "attack style, Void, Salve amulet and Slayer helm. Special attacks and weapon passives aren't included.",
		position = 0,
		section = combatSection
	)
	default boolean showMeleeMaxHit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showZeroHits",
		name = "Include 0-damage hits in average",
		description = "Whether misses (0 damage) count toward your average hit.",
		position = 1,
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
		position = 2,
		section = combatSection
	)
	default boolean trackMonsterLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resetHitsOnLogout",
		name = "Reset stats on logout",
		description = "Clears damage stats and this session's monsters when you log out. All-time monsters are kept.",
		position = 3,
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
		position = 4,
		section = combatSection
	)
	default boolean rememberMonsters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "monsterSort",
		name = "Sort monsters by",
		description = "Order of the Monsters tab. You can also change it at the top of the tab.",
		position = 5,
		section = combatSection
	)
	default MonsterSort monsterSort()
	{
		return MonsterSort.RECENT;
	}

	@ConfigItem(
		keyName = "hiddenMonsters",
		name = "Hidden monsters",
		description = "Monsters left out of the Monsters tab, separated by commas. Right-click a monster in the panel to hide it.",
		position = 6,
		section = combatSection
	)
	default String hiddenMonsters()
	{
		return "";
	}

	@ConfigItem(
		keyName = "monsterRange",
		name = "Monsters tab shows",
		description = "This session, or everything remembered for this account. You can also switch at the top of the tab.",
		position = 7,
		section = combatSection
	)
	default MonsterRange monsterRange()
	{
		return MonsterRange.ALL_TIME;
	}

	@ConfigItem(
		keyName = "lootPrice",
		name = "Value loot at",
		description = "Grand Exchange price, or High Alchemy value (useful for ironmen, who can't use the GE).",
		position = 8,
		section = combatSection
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
		position = 9,
		section = combatSection
	)
	default String ignoredItems()
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
}
