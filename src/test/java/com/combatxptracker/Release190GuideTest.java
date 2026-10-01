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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * 1.9.0: task ranking, next task odds, task guide requirements, effective levels, themes.
 */
public class Release190GuideTest
{
	private static SlayerTaskRecord record(String task, int kills, long minutes, int xp, long gp)
	{
		SlayerTaskRecord r = new SlayerTaskRecord();
		r.task = task;
		r.kills = kills;
		r.activeMillis = minutes * 60_000L;
		r.slayerXp = xp;
		r.lootGe = gp;
		r.lootHa = gp / 2;
		return r;
	}

	// ---- S2: your tasks ranked -----------------------------------------------------------

	@Test
	public void groupsTasksAndRanksByRate()
	{
		List<SlayerTaskRecord> log = Arrays.asList(
			record("Gargoyles", 150, 60, 30_000, 3_000_000),
			record("Gargoyles", 170, 60, 34_000, 3_200_000),
			record("Spiritual creatures", 120, 60, 9_000, 400_000),
			record("Abyssal demons", 180, 60, 50_000, 1_500_000),
			record("Cows", 5, 2, 50, 0));
		List<TaskStats> stats = TaskStats.group(log, LootPrice.GRAND_EXCHANGE);
		assertEquals(4, stats.size());
		TaskStats garg = TaskStats.find(stats, "gargoyles");
		assertEquals(2, garg.getTimes());
		assertEquals(160, garg.getAverageKills());
		assertEquals(32_000, garg.getXpPerHour(), 1);

		List<TaskStats> byXp = TaskStats.ranked(stats, false);
		// Cows has only 2 minutes logged, too little to rank.
		assertEquals(3, byXp.size());
		assertEquals("Abyssal demons", byXp.get(0).getTask());
		assertEquals("Spiritual creatures", byXp.get(2).getTask());
		assertEquals("Gargoyles", TaskStats.ranked(stats, true).get(0).getTask());
	}

	// ---- R2: next task odds ---------------------------------------------------------------

	private static List<TaskOdds.Option> options()
	{
		return new ArrayList<>(Arrays.asList(
			new TaskOdds.Option("Abyssal demons", 12, 130, 200, null),
			new TaskOdds.Option("Gargoyles", 8, 130, 200, null),
			new TaskOdds.Option("Spiritual creatures", 10, 130, 200, null),
			new TaskOdds.Option("Hydras", 10, 125, 190, "Needs 95 Slayer")));
	}

	@Test
	public void oddsUseWeightsOfTasksYouCanGet()
	{
		TaskOdds odds = TaskOdds.compute("Duradel", options(), Collections.emptySet(), true);
		assertEquals(12 / 30.0, odds.find("Abyssal demons").getChance(), 1e-9);
		assertEquals(0, odds.find("Hydras").getChance(), 0);
		assertEquals("Abyssal demons", odds.getOptions().get(0).getTask());
	}

	@Test
	public void blockedTasksAreLeftOut()
	{
		TaskOdds odds = TaskOdds.compute("Duradel", options(), new HashSet<>(Collections.singletonList("abyssal demons")), true);
		assertTrue(odds.find("Abyssal demons").isBlocked());
		assertEquals(0, odds.find("Abyssal demons").getChance(), 0);
		assertEquals(8 / 18.0, odds.find("Gargoyles").getChance(), 1e-9);
	}

	@Test
	public void blockAdviceIsYourSlowestLikelyTask()
	{
		List<TaskStats> history = TaskStats.group(Arrays.asList(
			record("Gargoyles", 150, 60, 30_000, 3_000_000),
			record("Spiritual creatures", 120, 60, 9_000, 400_000),
			record("Abyssal demons", 180, 60, 50_000, 1_500_000)), LootPrice.GRAND_EXCHANGE);
		TaskOdds odds = TaskOdds.compute("Duradel", options(), Collections.emptySet(), true);
		assertEquals("Spiritual creatures", odds.blockAdvice(history, 0.03).getTask());

		// Not enough history.
		assertNull(odds.blockAdvice(history.subList(0, 2), 0.03));
	}

	// ---- R1: task guide --------------------------------------------------------------------

	@Test
	public void requirementsForTasks()
	{
		List<TaskRequirements.Need> spectres = TaskRequirements.forTask("Aberrant spectres");
		assertEquals(1, spectres.size());
		assertTrue(spectres.get(0).isMetBy(Collections.singletonList("nose peg")));
		assertTrue(spectres.get(0).isMetBy(Collections.singletonList("slayer helmet (i)")));
		assertFalse(spectres.get(0).isMetBy(Collections.singletonList("black mask")));

		TaskRequirements.Need hammer = TaskRequirements.forTask("Gargoyles").get(0);
		assertTrue(hammer.getUnlockVarbit() > 0);
		assertTrue(hammer.isMetBy(Collections.singletonList("granite hammer")));

		assertTrue(TaskRequirements.forTask("Rune dragons").get(0).isMetBy(Collections.singletonList("extended super antifire(4)")));
		assertTrue(TaskRequirements.forTask("Kurask").get(0).isInfoOnly());
		assertTrue(TaskRequirements.forTask("Abyssal demons").isEmpty());
		assertTrue(TaskRequirements.forTask(null).isEmpty());
	}

	@Test
	public void guideCountsMissingRequiredItems()
	{
		SlayerGuide guide = new SlayerGuide("Turoth", Collections.emptyList(), Arrays.asList(
			new SlayerGuide.NeedStatus("Leaf-bladed weapon", false, true),
			new SlayerGuide.NeedStatus("Something else", false, false),
			new SlayerGuide.NeedStatus("Had", true, false)));
		assertEquals(1, guide.missingCount());
	}

	// ---- R5: effective levels (OSRS Wiki) ------------------------------------------------------

	@Test
	public void effectiveLevels()
	{
		// 99 Attack, Piety (+20%), Accurate (+3): floor(99 x 1.2) = 118, + 3 + 8 = 129.
		assertEquals(129, EffectiveLevels.attack(99, EffectiveLevels.AccuracyPrayer.PIETY, 3, false));
		// Void: x1.1, floored.
		assertEquals(141, EffectiveLevels.attack(99, EffectiveLevels.AccuracyPrayer.PIETY, 3, true));
		// 99 Ranged with Rigour (+20%), Rapid: 118 + 8 = 126.
		assertEquals(126, EffectiveLevels.attack(99, EffectiveLevels.AccuracyPrayer.RIGOUR, 0, false));
		// 99 Magic with Augury (+25%): floor(123.75) = 123, + 9 = 132.
		assertEquals(132, EffectiveLevels.magic(99, EffectiveLevels.AccuracyPrayer.AUGURY, 0, false));
		assertEquals(191, EffectiveLevels.magic(99, EffectiveLevels.AccuracyPrayer.AUGURY, 0, true));
		assertEquals(107, EffectiveLevels.attack(99, EffectiveLevels.AccuracyPrayer.NONE, 0, false));
	}

	// ---- L1: themes -------------------------------------------------------------------------------

	@After
	public void resetTheme()
	{
		Theme.apply(PanelTheme.DARK);
	}

	@Test
	public void darkThemeIsTheOriginalLook()
	{
		Theme.apply(PanelTheme.DARK);
		assertEquals(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR, Theme.BACKGROUND);
		assertEquals(net.runelite.client.ui.ColorScheme.DARKER_GRAY_COLOR, Theme.CARD);
		assertEquals(net.runelite.client.ui.ColorScheme.DARKER_GRAY_HOVER_COLOR, Theme.CARD_HOVER);
		assertEquals(new Color(24, 24, 24), Theme.HEADER);
		assertEquals(new Color(17, 17, 17), Theme.TRACK);
		assertEquals(new Color(52, 52, 52), Theme.BORDER);
		assertEquals(Color.WHITE, Theme.TEXT);
		assertEquals(new Color(168, 168, 168), Theme.MUTED);
		assertEquals(new Color(118, 118, 118), Theme.SUBTLE);
		assertEquals(new Color(255, 198, 64), Theme.GOLD);
		assertEquals(new Color(96, 220, 140), Theme.SUCCESS);
		assertEquals(net.runelite.client.ui.ColorScheme.BRAND_ORANGE, Theme.TAB_UNDERLINE);
		assertEquals(new Color(36, 36, 36), Theme.SLOT);
	}

	@Test
	public void accentFollowsThemeUnlessThePlayerPickedAColour()
	{
		Color defaultBar = new Color(79, 195, 247);
		Theme.apply(PanelTheme.DARK);
		assertEquals(defaultBar, Theme.accent(defaultBar));

		Theme.apply(PanelTheme.MIDNIGHT);
		assertEquals(PanelTheme.MIDNIGHT.accent, Theme.accent(defaultBar));
		assertEquals(Color.PINK, Theme.accent(Color.PINK));
		assertSame(PanelTheme.MIDNIGHT, Theme.current());
		assertEquals(PanelTheme.MIDNIGHT.accent, Theme.TAB_UNDERLINE);
	}

	@Test
	public void twelveThemes()
	{
		assertEquals(12, PanelTheme.values().length);
		assertEquals("Dark", PanelTheme.DARK.toString());
	}
}
