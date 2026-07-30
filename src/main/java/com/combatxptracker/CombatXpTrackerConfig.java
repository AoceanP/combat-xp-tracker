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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("combatxptracker")
public interface CombatXpTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "xpHrInterval",
		name = "XP/hr averaging window",
		description = "How many seconds of recent activity to average XP/hr over. Lower = more reactive, higher = smoother.",
		position = 0
	)
	@Range(min = 5, max = 300)
	default int xpHrIntervalSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "resetHitsOnLogout",
		name = "Reset hit stats on logout",
		description = "Clears average/max damage stats when you log out.",
		position = 1
	)
	default boolean resetHitsOnLogout()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showZeroHits",
		name = "Include 0-damage hits in average",
		description = "Whether misses (0 damage, non-blocked) count toward your average hit calculation.",
		position = 2
	)
	default boolean showZeroHits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show on-screen overlay",
		description = "Displays average damage, biggest hit, and your goal-tracked skills' XP/hr on screen, in addition to the sidebar panel.",
		position = 3
	)
	default boolean showOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showInfobox",
		name = "Show goal infobox",
		description = "Shows an infobox near the minimap for the skill you're actively training, with its progress toward your goal.",
		position = 4
	)
	default boolean showInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugMenuLogging",
		name = "Debug: log right-click IDs",
		description = "TEMPORARY DIAGNOSTIC. When enabled, right-clicking in the stats tab prints the raw widget IDs to the client log, so the 'Set goal level' menu entry can be fixed. Leave off unless asked.",
		position = 90
	)
	default boolean debugMenuLogging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCombinedDrop",
		name = "Show damage on XP drops",
		description = "When an XP gain and a hit land close together, shows the hit damage alongside the XP drop overlay.",
		position = 5
	)
	default boolean showCombinedDrop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "combinedDropWindowMillis",
		name = "XP/damage pairing window (ms)",
		description = "How close together (in milliseconds) an XP gain and a hit need to land to be shown as paired.",
		position = 6
	)
	@Range(min = 100, max = 3000)
	default int combinedDropWindowMillis()
	{
		return 600;
	}
}
