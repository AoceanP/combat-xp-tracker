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
		description = "How many seconds of recent activity to average XP/hr over. Lower = more reactive, higher = smoother.",
		position = 0,
		section = goalsSection
	)
	@Range(min = 5, max = 300)
	default int xpHrIntervalSeconds()
	{
		return 30;
	}

	@Alpha
	@ConfigItem(
		keyName = "goalBarColor",
		name = "Goal bar colour",
		description = "Fill colour of the goal progress bars. Right-click a goal in the panel to give one skill its own colour.",
		position = 1,
		section = goalsSection
	)
	default Color goalBarColor()
	{
		return new Color(79, 195, 247);
	}

	// ---- Combat --------------------------------------------------------------

	@ConfigItem(
		keyName = "showMeleeMaxHit",
		name = "Show melee max hit",
		description = "Shows your melee max hit from worn gear, boosted Strength, prayer, attack style, Void, "
			+ "Salve amulet and Slayer helm. Special attacks and weapon passives aren't included.",
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
		description = "Clears damage stats and the Monsters tab when you log out.",
		position = 3,
		section = combatSection
	)
	default boolean resetHitsOnLogout()
	{
		return false;
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
