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
import static org.junit.Assert.assertTrue;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * 1.8.0: kills to level, task forecast, boss kill summary, gear bonuses.
 */
public class Release180Test
{
	// ---- XP per kill -----------------------------------------------------------------

	@Test
	public void measuresXpPerKill()
	{
		XpPerKill xp = new XpPerKill();
		assertEquals(-1, xp.average("Abyssal demon", Skill.STRENGTH), 0);
		for (int i = 0; i < 5; i++)
		{
			xp.recordXp(Skill.STRENGTH, 600);
			xp.recordXp(Skill.HITPOINTS, 200);
			xp.recordKill("Abyssal demon");
		}
		assertEquals(600, xp.average("abyssal demon", Skill.STRENGTH), 0.001);
		assertEquals(200, xp.average("Abyssal demon", Skill.HITPOINTS), 0.001);
		assertEquals(0, xp.average("Abyssal demon", Skill.ATTACK), 0.001);
		assertEquals(5, xp.kills("Abyssal demon"));
	}

	@Test
	public void burstKillsInOneTickAverageOut()
	{
		// Three kills in one tick: the first gets all the XP, but the average is right.
		XpPerKill xp = new XpPerKill();
		for (int round = 0; round < 30; round++)
		{
			xp.recordXp(Skill.MAGIC, 300);
			xp.recordKill("Dust devil");
			xp.recordKill("Dust devil");
			xp.recordKill("Dust devil");
		}
		assertEquals(100, xp.average("Dust devil", Skill.MAGIC), 10);
	}

	@Test
	public void recentKillsCountMore()
	{
		XpPerKill xp = new XpPerKill();
		for (int i = 0; i < 20; i++)
		{
			xp.recordXp(Skill.RANGED, 100);
			xp.recordKill("Goblin");
		}
		for (int i = 0; i < 20; i++)
		{
			xp.recordXp(Skill.RANGED, 200);
			xp.recordKill("Goblin");
		}
		// Much closer to the new 200 than the overall 150.
		assertTrue(xp.average("Goblin", Skill.RANGED) > 185);
	}

	@Test
	public void clearPendingDropsOtherMonstersXp()
	{
		XpPerKill xp = new XpPerKill();
		xp.recordXp(Skill.STRENGTH, 999);
		xp.clearPending();
		xp.recordXp(Skill.STRENGTH, 40);
		xp.recordKill("Cow");
		assertEquals(40, xp.average("Cow", Skill.STRENGTH), 0.001);
	}

	// ---- Kills to level and task forecast ----------------------------------------------

	@Test
	public void killsToNextLevel()
	{
		int xp = Experience.getXpForLevel(80);
		int toNext = Experience.getXpForLevel(81) - xp;
		LevelForecast.Row row = LevelForecast.killsToLevel(Skill.STRENGTH, xp, 600, 3);
		assertEquals(81, row.getNextLevel());
		assertEquals((int) Math.ceil(toNext / 600.0), row.getKills());
		assertEquals(3, row.getMeasuredKills());
	}

	@Test
	public void noKillsToLevelWhenUnknownOrMaxed()
	{
		assertNull(LevelForecast.killsToLevel(Skill.STRENGTH, 1000, -1, 0));
		assertNull(LevelForecast.killsToLevel(Skill.STRENGTH, Experience.MAX_SKILL_XP, 600, 0));
		assertNull(LevelForecast.killsToLevel(Skill.STRENGTH, Experience.getXpForLevel(126), 600, 0));
	}

	@Test
	public void restOfTaskGain()
	{
		int xp = Experience.getXpForLevel(86);
		LevelForecast.TaskGain gain = LevelForecast.taskGain(Skill.SLAYER, xp, 150, 120);
		assertEquals(18_000, gain.getXp());
		assertEquals(86, gain.getLevelNow());
		assertEquals(Experience.getLevelForXp(xp + 18_000), gain.getLevelAfter());
		assertNull(LevelForecast.taskGain(Skill.SLAYER, xp, 150, 0));
	}

	@Test
	public void taskGainStopsAt200m()
	{
		LevelForecast.TaskGain gain = LevelForecast.taskGain(Skill.SLAYER, Experience.MAX_SKILL_XP - 100, 500, 50);
		assertEquals(100, gain.getXp());
	}

	// ---- Boss kill summary ---------------------------------------------------------------

	@Test
	public void parsesKillCountMessages()
	{
		assertEquals(124, BossFight.parseKillCount("Your Vorkath kill count is: 124."));
		assertEquals(1234, BossFight.parseKillCount("Your Zulrah kill count is: 1,234."));
		assertEquals(3, BossFight.parseKillCount("Your Gauntlet completion count is: 3."));
		assertEquals(7, BossFight.parseKillCount("Your completed Theatre of Blood count is: 7."));
		assertEquals(-1, BossFight.parseKillCount("Your Barrows chest count is: 50."));
		assertEquals(-1, BossFight.parseKillCount("You have a funny feeling like you're being followed."));
		assertEquals(-1, BossFight.parseKillCount(null));
	}

	@Test
	public void killSummary()
	{
		BossFight fight = new BossFight("Vorkath", 1000);
		fight.recordHit(5, 750, 60, 1000);
		for (int i = 0; i < 17; i++)
		{
			fight.recordHit(5, 750, 40, 1010 + i);
		}
		assertEquals("Vorkath kill 124 in 1:32.4: you dealt 740 damage (99%) in 18 hits, biggest hit 60.",
			fight.summary(124, 1154));
	}

	@Test
	public void groupFightAddsEachBossHitpointsOnce()
	{
		BossFight fight = new BossFight("Royal Titans", 0);
		fight.recordHit(1, 500, 100, 1);
		fight.recordHit(1, 500, 100, 2);
		fight.recordHit(2, 500, 300, 3);
		// 500 of 1000 hitpoints.
		assertTrue(fight.summary(9, 50).contains("500 damage (50%)"));
	}

	@Test
	public void noPercentWhenBossHeals()
	{
		BossFight fight = new BossFight("Zulrah", 0);
		fight.recordHit(1, 500, 600, 10);
		assertTrue(fight.summary(1, 20).contains("600 damage in 1 hit,"));
	}

	@Test
	public void fightTime()
	{
		assertEquals("0:06.0", BossFight.fightTime(10));
		assertEquals("1:00.0", BossFight.fightTime(100));
		assertEquals("1:32.4", BossFight.fightTime(154));
	}

	// ---- Gear bonuses --------------------------------------------------------------------

	@Test
	public void obsidianAndBerserkerAdd()
	{
		assertEquals(55, GearBonus.obsidian(50, true, false));
		assertEquals(60, GearBonus.obsidian(50, false, true));
		assertEquals(65, GearBonus.obsidian(50, true, true));
	}

	@Test
	public void inquisitorPerPieceAndSet()
	{
		assertEquals(120, GearBonus.inquisitor(120, 0));
		assertEquals(101, GearBonus.inquisitor(100, 2));
		assertEquals(123, GearBonus.inquisitor(120, 3));
		assertEquals(200, GearBonus.inquisitor(200, 3));
		assertEquals(41, GearBonus.inquisitor(40, 3));
	}

	@Test
	public void crystalArmourPieces()
	{
		assertEquals(40, GearBonus.crystal(40, false, false, false));
		assertEquals(46, GearBonus.crystal(40, true, true, true));
		assertEquals(43, GearBonus.crystal(40, false, true, false));
	}

	@Test
	public void twistedBowMultiplier()
	{
		assertEquals(215, GearBonus.twistedBowPercent(250));
		assertEquals(215, GearBonus.twistedBowPercent(400));
		assertTrue(GearBonus.twistedBowPercent(50) < 100);
		assertEquals(107, GearBonus.twistedBow(50, 250));
	}

	@Test
	public void wildernessWeapons()
	{
		assertEquals(60, GearBonus.wilderness(40));
		assertTrue(GearBonus.isWildernessWeapon("craw's bow"));
		assertFalse(GearBonus.isWildernessWeapon("craw's bow (u)"));
		assertTrue(GearBonus.isWildernessWeapon("ursine chainmace"));
	}

	@Test
	public void banes()
	{
		assertEquals(GearBonus.Bane.DRAGON_HUNTER_LANCE, GearBonus.Bane.fromWeaponName("dragon hunter lance"));
		assertEquals(GearBonus.Bane.DRAGON_HUNTER_CROSSBOW, GearBonus.Bane.fromWeaponName("dragon hunter crossbow (t)"));
		assertEquals(GearBonus.Bane.ARCLIGHT, GearBonus.Bane.fromWeaponName("arclight"));
		assertEquals(GearBonus.Bane.KERIS, GearBonus.Bane.fromWeaponName("keris partisan of breaching"));
		assertNull(GearBonus.Bane.fromWeaponName("keris partisan of amascut"));
		assertNull(GearBonus.Bane.fromWeaponName("abyssal whip"));

		assertEquals(60, GearBonus.Bane.DRAGON_HUNTER_LANCE.apply(50));
		assertEquals(62, GearBonus.Bane.DRAGON_HUNTER_CROSSBOW.apply(50));
		assertEquals(85, GearBonus.Bane.ARCLIGHT.apply(50));
		assertEquals(80, GearBonus.Bane.SILVERLIGHT.apply(50));
		assertEquals(66, GearBonus.Bane.KERIS.apply(50));
		assertEquals(200, GearBonus.Bane.ARCLIGHT.apply(150));
	}

	@Test
	public void baneTargets()
	{
		assertTrue(GearBonus.Bane.DRAGON_HUNTER_LANCE.matches("Rune dragon"));
		assertTrue(GearBonus.Bane.DRAGON_HUNTER_LANCE.matches("Vorkath"));
		assertTrue(GearBonus.Bane.DRAGON_HUNTER_LANCE.matches("Alchemical Hydra"));
		assertFalse(GearBonus.Bane.DRAGON_HUNTER_LANCE.matches("Revenant dragon"));
		assertFalse(GearBonus.Bane.DRAGON_HUNTER_LANCE.matches("Elvarg"));
		assertTrue(GearBonus.Bane.ARCLIGHT.matches("Greater demon"));
		assertTrue(GearBonus.Bane.ARCLIGHT.matches("K'ril Tsutsaroth"));
		assertFalse(GearBonus.Bane.ARCLIGHT.matches("Hellhound"));
		assertTrue(GearBonus.Bane.KERIS.matches("Kalphite Queen"));
		assertTrue(GearBonus.Bane.KERIS.matches("Scarab swarm"));
	}

	@Test
	public void weaponGroups()
	{
		assertTrue(GearBonus.isObsidianWeapon("tzhaar-ket-om (t)"));
		assertTrue(GearBonus.isObsidianWeapon("toktz-xil-ak"));
		assertFalse(GearBonus.isObsidianWeapon("abyssal whip"));
		assertTrue(GearBonus.isCrushWeapon("inquisitor's mace"));
		assertTrue(GearBonus.isCrushWeapon("elder maul"));
		assertFalse(GearBonus.isCrushWeapon("abyssal tentacle"));
		assertTrue(GearBonus.isCrystalBow("bow of faerdhinen (c)"));
		assertFalse(GearBonus.isCrystalBow("crystal bow (inactive)"));
	}
}
