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

	@Test
	public void killGoalsAnnounceOnceOnTheKillThatReachesThem()
	{
		assertFalse(KillGoals.justReached(499, 500));
		assertTrue(KillGoals.justReached(500, 500));
		// Set below your kills: already done, so no announcement.
		assertFalse(KillGoals.justReached(612, 500));
	}

	@Test
	public void killGoalTextMatchesSkillGoals()
	{
		assertEquals("42%", KillGoals.percentText(212, 500));
		assertEquals("99%", KillGoals.percentText(499, 500));
		assertEquals("Done", KillGoals.percentText(500, 500));
		assertEquals("212/500 (42%)", KillGoals.overlayText(212, 500));
		assertEquals("1,000/2,500 (40%)", KillGoals.overlayText(1_000, 2_500));
		assertEquals("Goal reached!", KillGoals.overlayText(612, 500));
	}

	private static java.util.function.Function<Quest, QuestState> states(Map<Quest, QuestState> known)
	{
		return q -> known.getOrDefault(q, QuestState.NOT_STARTED);
	}

	@Test
	public void questLockedTasksNeedTheirQuest()
	{
		Map<Quest, QuestState> none = Collections.emptyMap();
		assertEquals("Needs Regicide", QuestLocks.lockedBy("Elves", states(none)));
		assertEquals("Needs Bone Voyage", QuestLocks.lockedBy("Fossil Island Wyverns", states(none)));
		assertNull("no quest needed", QuestLocks.lockedBy("Hellhounds", states(none)));

		Map<Quest, QuestState> regicide = new HashMap<>();
		regicide.put(Quest.REGICIDE, QuestState.FINISHED);
		assertNull(QuestLocks.lockedBy("Elves", states(regicide)));

		Map<Quest, QuestState> started = new HashMap<>();
		started.put(Quest.REGICIDE, QuestState.IN_PROGRESS);
		started.put(Quest.DRAGON_SLAYER_I, QuestState.IN_PROGRESS);
		assertEquals("only finishing Regicide counts", "Needs Regicide", QuestLocks.lockedBy("Elves", states(started)));
		assertNull("starting Dragon Slayer I is enough", QuestLocks.lockedBy("Blue dragons", states(started)));
		assertEquals("Needs Dragon Slayer I (started)", QuestLocks.lockedBy("Black dragons", states(none)));
	}

	@Test
	public void eitherQuestUnlocksAbyssalDemons()
	{
		Map<Quest, QuestState> fairy = new HashMap<>();
		fairy.put(Quest.FAIRYTALE_II__CURE_A_QUEEN, QuestState.IN_PROGRESS);
		assertNull(QuestLocks.lockedBy("Abyssal demons", states(fairy)));
		assertEquals("Needs Priest in Peril", QuestLocks.lockedBy("Abyssal demons", states(Collections.emptyMap())));
	}

	@Test
	public void questLocksMatchExactNamesOnly()
	{
		// "Spiders" isn't Fever spiders, so Rum Deal doesn't lock it.
		assertNull(QuestLocks.lockedBy("Spiders", states(Collections.emptyMap())));
		assertTrue(QuestLocks.forTask("Fever spiders").size() == 1);
		assertTrue(QuestLocks.forTask(null).isEmpty());
	}

	@Test
	public void dropTooltipsTellTheDropsStory()
	{
		assertEquals("1 in 5,000 drop", DryCalculator.dropStory(5_000, -1));
		assertEquals("1 in 5,000 drop<br>First at kill 212. Lucky: 4% of players get one that early.",
			DryCalculator.dropStory(5_000, 212));
		assertEquals("1 in 3,000 drop<br>First at kill 9,800. Dry: 96% of players get one sooner.",
			DryCalculator.dropStory(3_000, 9_800));
		assertEquals("1 in 5,000 drop<br>First at kill 1. Lucky: under 1% of players get one that early.",
			DryCalculator.dropStory(5_000, 1));
	}

	@Test
	public void dropRatesComeFromTheBundledTable()
	{
		Map<String, Map<String, Double>> table = new HashMap<>();
		Map<String, Double> vorkath = new HashMap<>();
		vorkath.put("Draconic visage", 5_000.0);
		table.put("Vorkath", vorkath);
		DryCalculator dry = new DryCalculator(table);
		assertEquals(5_000, dry.oneIn("vorkath", "draconic visage"), 0);
		assertEquals(-1, dry.oneIn("Vorkath", "Dragon bones"), 0);
		assertEquals(-1, dry.oneIn("Zulrah", "Tanzanite fang"), 0);
	}

	@Test
	public void superiorUniqueChancesFollowTheWikiFormula()
	{
		// 1 in (200 - (level + 55)^2 / 125) for the table, then 1/8 for the item.
		assertEquals(8 * 171.2, SuperiorMonsters.uniqueOneIn("Crushing hand"), 0.001);
		assertEquals(8 * 54.2, SuperiorMonsters.uniqueOneIn("Nechryarch"), 0.001);
		assertEquals(8 * 20.0, SuperiorMonsters.uniqueOneIn("colossal hydra"), 0.001);
		assertEquals(-1, SuperiorMonsters.uniqueOneIn("Vorkath"), 0);
		assertEquals(38, SuperiorMonsters.names().size());
	}

	@Test
	public void superiorLuckAddsUpEverySuperior()
	{
		Map<String, Integer> kills = new HashMap<>();
		kills.put("nechryarch", 200);
		kills.put("colossal hydra", 80);
		kills.put("vorkath", 500);
		SuperiorMonsters.UniqueLuck luck = SuperiorMonsters.luck(kills, 0);
		assertEquals("Vorkath isn't a superior", 280, luck.kills);
		assertEquals(200 / 433.6 + 80 / 160.0, luck.expected, 1e-9);
		double none = Math.pow(1 - 1 / 433.6, 200) * Math.pow(1 - 1 / 160.0, 80);
		assertEquals(1 - none, luck.chanceByNow, 1e-9);
		assertEquals(0, SuperiorMonsters.luck(Collections.emptyMap(), 0).kills);
	}

	@Test
	public void petsAreFoundByName()
	{
		assertTrue(Pets.isPet("Vorki"));
		assertTrue(Pets.isPet("pet zilyana"));
		assertFalse(Pets.isPet("Draconic visage"));
		assertFalse(Pets.isPet(null));

		List<DryCalculator.Dry> dry = Arrays.asList(new DryCalculator.Dry("Draconic visage", 5_000, 2_912),
			new DryCalculator.Dry("Vorki", 3_000, 2_912));
		assertEquals("Vorki", Pets.missing(dry).getItem());
		assertNull(Pets.missing(dry.subList(0, 1)));

		assertTrue(Pets.owned(Arrays.asList(new CollectionLog.Item(1, "Vorki", true))));
		assertFalse(Pets.owned(Arrays.asList(new CollectionLog.Item(1, "Vorki", false))));
		assertFalse(Pets.owned(null));
	}

	@Test
	public void bossTrendComparesNewerKillsWithOlder()
	{
		// Older three average 120s, newer three 112s: 8s faster.
		List<Double> faster = Arrays.asList(121.0, 120.0, 119.0, 113.0, 112.0, 111.0);
		assertEquals(-8, BossTimes.trend(faster), 1e-9);
		assertEquals("8.0s faster", BossTimes.trendText(BossTimes.trend(faster)));
		// An odd count leaves the middle kill out.
		assertEquals(10, BossTimes.trend(Arrays.asList(100.0, 100.0, 100.0, 999.0, 110.0, 110.0, 110.0)), 1e-9);
		assertEquals("Steady", BossTimes.trendText(0.3));
		assertEquals("1:05.0 slower", BossTimes.trendText(65));
		assertTrue(Double.isNaN(BossTimes.trend(Arrays.asList(100.0, 90.0, 80.0, 70.0, 60.0))));
	}
}
