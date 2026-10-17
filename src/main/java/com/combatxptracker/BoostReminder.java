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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;

/**
 * A reminder at the start of a boss fight when the player has no potion boost, no offensive
 * prayer or (on the Arceuus spellbook) no thrall for the style they're attacking with. It only
 * says what's missing; it doesn't help with the fight itself.
 */
final class BoostReminder
{
	private static final Map<CombatStyle, Skill> BOOSTED_SKILL = new EnumMap<>(CombatStyle.class);
	private static final Map<CombatStyle, List<Prayer>> OFFENSIVE_PRAYERS = new EnumMap<>(CombatStyle.class);

	static
	{
		BOOSTED_SKILL.put(CombatStyle.MELEE, Skill.STRENGTH);
		BOOSTED_SKILL.put(CombatStyle.RANGED, Skill.RANGED);
		BOOSTED_SKILL.put(CombatStyle.MAGIC, Skill.MAGIC);
		OFFENSIVE_PRAYERS.put(CombatStyle.MELEE, Arrays.asList(Prayer.PIETY, Prayer.CHIVALRY, Prayer.ULTIMATE_STRENGTH,
			Prayer.SUPERHUMAN_STRENGTH, Prayer.BURST_OF_STRENGTH, Prayer.INCREDIBLE_REFLEXES, Prayer.IMPROVED_REFLEXES,
			Prayer.CLARITY_OF_THOUGHT));
		OFFENSIVE_PRAYERS.put(CombatStyle.RANGED, Arrays.asList(Prayer.RIGOUR, Prayer.DEADEYE, Prayer.EAGLE_EYE,
			Prayer.HAWK_EYE, Prayer.SHARP_EYE));
		OFFENSIVE_PRAYERS.put(CombatStyle.MAGIC, Arrays.asList(Prayer.AUGURY, Prayer.MYSTIC_VIGOUR, Prayer.MYSTIC_MIGHT,
			Prayer.MYSTIC_LORE, Prayer.MYSTIC_WILL));
	}

	private BoostReminder()
	{
	}

	static Skill boostedSkill(CombatStyle style)
	{
		return BOOSTED_SKILL.get(style);
	}

	static List<Prayer> offensivePrayers(CombatStyle style)
	{
		List<Prayer> prayers = OFFENSIVE_PRAYERS.get(style);
		return prayers == null ? Collections.emptyList() : prayers;
	}

	/**
	 * @param boosted     whether the style's skill is boosted above its level
	 * @param prayer      whether an offensive prayer for the style is on
	 * @param thrallWanted whether the player is on the Arceuus spellbook without a thrall out
	 * @return e.g. "Vorkath: no Ranged boost, no offensive prayer.", or null when nothing's missing
	 */
	static String message(String boss, CombatStyle style, boolean boosted, boolean prayer, boolean thrallWanted)
	{
		List<String> missing = new ArrayList<>();
		if (!boosted)
		{
			missing.add("no " + style.getDisplayName() + " boost");
		}
		if (!prayer)
		{
			missing.add("no offensive prayer");
		}
		if (thrallWanted)
		{
			missing.add("no thrall");
		}
		return missing.isEmpty() ? null : boss + ": " + String.join(", ", missing) + ".";
	}
}
