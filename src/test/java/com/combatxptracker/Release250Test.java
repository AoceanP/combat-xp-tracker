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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.config.ConfigItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 2.5: polish. A quiet chatbox for new players, a Chat & screenshots section, the same
 * "XP" and "GP/hr" everywhere, readable text on every theme, kill count goals that celebrate,
 * quest-aware task odds and drop tooltips with the drop rate.
 */
public class Release250Test
{
	private static final CombatXpTrackerConfig DEFAULTS = new CombatXpTrackerConfig()
	{
	};

	private static final List<String> CHAT = Arrays.asList("goalChatMessage", "taskSummary", "killSummary",
		"deathMessage", "milestoneMessages", "milestoneScreenshot", "uniqueScreenshot", "boostReminder");

	@Test
	public void chatMessagesAndScreenshotsAreOffForNewPlayers()
	{
		assertFalse(DEFAULTS.goalChatMessage());
		assertFalse(DEFAULTS.taskSummary());
		assertFalse(DEFAULTS.killSummary());
		assertFalse(DEFAULTS.deathMessage());
		assertFalse(DEFAULTS.milestoneMessages());
		assertFalse(DEFAULTS.milestoneScreenshot());
		assertFalse(DEFAULTS.uniqueScreenshot());
		assertFalse(DEFAULTS.boostReminder());
		assertFalse("the attack timer overlay is off like the main overlay", DEFAULTS.attackTimer());
	}

	@Test
	public void panelFeaturesStayOn()
	{
		// Only chat, screenshots and the attack timer changed; what the panel shows is untouched.
		assertTrue(DEFAULTS.dropReveal());
		assertTrue(DEFAULTS.combatAchievements());
		assertTrue(DEFAULTS.prepCheck());
		assertTrue(DEFAULTS.showInfobox());
	}

	@Test
	public void chatSettingsLiveInTheirOwnSectionWithTheSameKeys()
	{
		int found = 0;
		for (Method m : CombatXpTrackerConfig.class.getDeclaredMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null && CHAT.contains(item.keyName()))
			{
				found++;
				assertEquals(item.keyName(), CombatXpTrackerConfig.chatSection, item.section());
				// Keys are what players' saved settings are stored under, so they never change.
				assertEquals(item.keyName(), m.getName());
			}
		}
		assertEquals(CHAT.size(), found);
	}

	@Test
	public void shortCountsFitTheGoalCard()
	{
		// "~2,826 hits" ran into "+472K session"; counts from 1,000 up are now short.
		assertEquals("950", Formatting.compactCount(950));
		assertEquals("2.8K", Formatting.compactCount(2_826));
		assertEquals("10K", Formatting.compactCount(10_000));
		assertEquals("12.3K", Formatting.compactCount(12_345));
		assertEquals("1.25M", Formatting.compactCount(1_250_000));
	}

	@Test
	public void styleCaptionsAreFullWords()
	{
		assertEquals("Melee", MonsterCard.styleCaption(CombatStyle.MELEE));
		assertEquals("Ranged", MonsterCard.styleCaption(CombatStyle.RANGED));
		assertEquals("Magic", MonsterCard.styleCaption(CombatStyle.MAGIC));
	}

	@Test
	public void ratesUseCapitalXpAndGp()
	{
		assertEquals("Level 99", Goal.parse("99").toString());
		assertEquals("13M XP", Goal.parse("13m xp").toString());
		// Old saved goals written in lower case still load.
		assertEquals(13_000_000, Goal.parse("13M xp").getTargetXp());
	}

	private static double luminance(Color c)
	{
		double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
		for (int i = 0; i < 3; i++)
		{
			v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
	}

	private static double contrast(Color a, Color b)
	{
		double x = luminance(a);
		double y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	@Test
	public void everyTextColourIsReadableOnEveryTheme()
	{
		// 4.5:1 is the usual minimum for small text, on cards and the panel background.
		for (PanelTheme t : PanelTheme.values())
		{
			for (Color surface : new Color[]{t.card, t.background})
			{
				for (Color text : new Color[]{t.text, t.muted, t.subtle, t.accent, t.gold, t.success})
				{
					assertTrue(t + " " + text + " on " + surface + " is " + contrast(text, surface),
						contrast(text, surface) >= 4.5);
				}
			}
		}
	}
}
