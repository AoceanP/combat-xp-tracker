package com.combatxptracker;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Checks the formula against the OSRS Wiki's "Maximum melee hit" page:
 * https://oldschool.runescape.wiki/w/Maximum_melee_hit
 */
public class MeleeMaxHitTest
{
	/**
	 * Wiki: "For free-to-play the maximum hit is 31, using a rune 2h sword, an amulet of
	 * strength, decorative boots (gold), and a strength potion with Ultimate Strength."
	 * Bonuses from each item's wiki page: +70, +10, +1. The 2h is on aggressive.
	 * Strength potion at 99: floor(99 x 10%) + 3 = 12, so visible Strength is 111.
	 */
	@Test
	public void wikiFreeToPlayMaxHitIs31()
	{
		int visibleStrength = 99 + (99 * 10 / 100 + 3);
		assertEquals(111, visibleStrength);
		assertEquals(138, MeleeMaxHit.effectiveStrength(visibleStrength, MeleeMaxHit.StrengthPrayer.ULTIMATE_STRENGTH, 3, false));
		assertEquals(31, MeleeMaxHit.maxHit(visibleStrength, MeleeMaxHit.StrengthPrayer.ULTIMATE_STRENGTH, 3, false, 70 + 10 + 1));
	}

	@Test
	public void unarmed99StrengthAggressiveHits11()
	{
		// (99 + 3 + 8) = 110; floor(0.5 + 110 x 64 / 640) = floor(11.5) = 11
		assertEquals(11, MeleeMaxHit.maxHit(99, MeleeMaxHit.StrengthPrayer.NONE, 3, false, 0));
	}

	@Test
	public void styleBonuses()
	{
		assertEquals(107, MeleeMaxHit.effectiveStrength(99, MeleeMaxHit.StrengthPrayer.NONE, 0, false));
		assertEquals(108, MeleeMaxHit.effectiveStrength(99, MeleeMaxHit.StrengthPrayer.NONE, 1, false));
		assertEquals(110, MeleeMaxHit.effectiveStrength(99, MeleeMaxHit.StrengthPrayer.NONE, 3, false));
	}

	@Test
	public void prayerMultipliersFloorBeforeAddingStyle()
	{
		// 118 x 1.23 = 145.14 -> 145
		assertEquals(145 + 3 + 8, MeleeMaxHit.effectiveStrength(118, MeleeMaxHit.StrengthPrayer.PIETY, 3, false));
		// 118 x 1.18 = 139.24 -> 139
		assertEquals(139 + 8, MeleeMaxHit.effectiveStrength(118, MeleeMaxHit.StrengthPrayer.CHIVALRY, 0, false));
	}

	@Test
	public void integerMathAvoidsFloatingPointFloorError()
	{
		// 100 x 1.15 is 114.99999999999999 in doubles, which would floor to 114.
		assertEquals(115 + 8, MeleeMaxHit.effectiveStrength(100, MeleeMaxHit.StrengthPrayer.ULTIMATE_STRENGTH, 0, false));
	}

	@Test
	public void burstOfStrengthAlwaysAddsAtLeastOne()
	{
		// 10 x 1.05 = 10.5 -> 10, but the wiki says Burst always boosts by at least 1.
		assertEquals(11 + 8, MeleeMaxHit.effectiveStrength(10, MeleeMaxHit.StrengthPrayer.BURST_OF_STRENGTH, 0, false));
		// 40 x 1.05 = 42, already more than 1.
		assertEquals(42 + 8, MeleeMaxHit.effectiveStrength(40, MeleeMaxHit.StrengthPrayer.BURST_OF_STRENGTH, 0, false));
	}

	@Test
	public void voidMultipliesAfterStyleAndEight()
	{
		// floor((floor(99 x 1.23) + 3 + 8) x 1.1) = floor(132 x 1.1) = floor(145.2) = 145
		assertEquals(145, MeleeMaxHit.effectiveStrength(99, MeleeMaxHit.StrengthPrayer.PIETY, 3, true));
	}

	@Test
	public void maxedMeleeSetup()
	{
		// 99 Strength, super combat (+19 -> 118), Piety, aggressive, +150 strength bonus.
		// effStr = 145 + 3 + 8 = 156; floor(0.5 + 156 x 214 / 640) = floor(52.66) = 52
		assertEquals(52, MeleeMaxHit.maxHit(118, MeleeMaxHit.StrengthPrayer.PIETY, 3, false, 150));
	}

	@Test
	public void targetBonusesFloorAfterMultiplying()
	{
		// Slayer helm / Black mask and Salve: x 7/6. Salve (e)/(ei): x 1.2.
		assertEquals(60, MeleeMaxHit.TargetBonus.SLAYER_HELM.apply(52)); // 60.67
		assertEquals(60, MeleeMaxHit.TargetBonus.SALVE.apply(52));
		assertEquals(62, MeleeMaxHit.TargetBonus.SALVE_E.apply(52)); // 62.4
		assertEquals(7, MeleeMaxHit.TargetBonus.SLAYER_HELM.apply(6));
	}

	@Test
	public void damageIsCappedAt200()
	{
		assertEquals(200, MeleeMaxHit.TargetBonus.SLAYER_HELM.apply(190));
		assertEquals(200, MeleeMaxHit.maxHit(400, 500));
	}

	@Test
	public void negativeStrengthBonusNeverGoesBelowZero()
	{
		assertEquals(0, MeleeMaxHit.maxHit(10, -100));
	}
}
