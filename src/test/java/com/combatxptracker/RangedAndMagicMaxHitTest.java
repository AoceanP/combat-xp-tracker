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
import static org.junit.Assert.assertNull;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Checks against the OSRS Wiki's "Maximum ranged hit" and "Maximum magic hit" pages
 * and the powered staff item pages.
 */
public class RangedAndMagicMaxHitTest
{
	// ---- Ranged ---------------------------------------------------------------

	@Test
	public void rangedFormula()
	{
		// 99 Ranged, no boosts or prayer, accurate (+3): effective = 99 + 3 + 8 = 110.
		// With +100 ranged strength: floor(0.5 + 110 x 164 / 640) = floor(28.69) = 28
		assertEquals(110, RangedMaxHit.effectiveRangedStrength(99, RangedMaxHit.RangedPrayer.NONE, 3, RangedMaxHit.VoidSet.NONE));
		assertEquals(28, RangedMaxHit.maxHit(99, RangedMaxHit.RangedPrayer.NONE, 3, RangedMaxHit.VoidSet.NONE, 100));
		// Rapid has no style bonus: 107 x 164 / 640 = 27.42 + 0.5 -> 27
		assertEquals(27, RangedMaxHit.maxHit(99, RangedMaxHit.RangedPrayer.NONE, 0, RangedMaxHit.VoidSet.NONE, 100));
	}

	@Test
	public void rangedPrayersFloorBeforeStyle()
	{
		// 112 x 1.23 = 137.76 -> 137; + 3 + 8 = 148
		assertEquals(148, RangedMaxHit.effectiveRangedStrength(112, RangedMaxHit.RangedPrayer.RIGOUR, 3, RangedMaxHit.VoidSet.NONE));
		// Deadeye is 1.18: 112 x 1.18 = 132.16 -> 132
		assertEquals(140, RangedMaxHit.effectiveRangedStrength(112, RangedMaxHit.RangedPrayer.DEADEYE, 0, RangedMaxHit.VoidSet.NONE));
	}

	@Test
	public void voidAndEliteVoid()
	{
		// (99 + 0 + 8) x 1.1 = 117.7 -> 117; x 1.125 = 120.375 -> 120
		assertEquals(117, RangedMaxHit.effectiveRangedStrength(99, RangedMaxHit.RangedPrayer.NONE, 0, RangedMaxHit.VoidSet.VOID));
		assertEquals(120, RangedMaxHit.effectiveRangedStrength(99, RangedMaxHit.RangedPrayer.NONE, 0, RangedMaxHit.VoidSet.ELITE));
	}

	@Test
	public void rangedTargetBonuses()
	{
		// Slayer helm (i) is x1.15 for ranged, Salve (i) 7/6, Salve (ei) 1.2
		assertEquals(46, RangedMaxHit.TargetBonus.SLAYER_HELM_I.apply(40));
		assertEquals(46, RangedMaxHit.TargetBonus.SALVE_I.apply(40));
		assertEquals(48, RangedMaxHit.TargetBonus.SALVE_EI.apply(40));
	}

	// ---- Magic: base damage -----------------------------------------------------

	@Test
	public void poweredStaffsMatchTheirWikiPages()
	{
		// Sanguinesti: 27 at level 82. Trident of the seas: 20 at 75. Swamp: 24 at 78.
		// Tumeken's shadow: 29 at 85. Warped sceptre: 16 at 62 and 24 at 99.
		assertEquals(27, MagicMaxHit.PoweredStaff.SANGUINESTI_STAFF.baseDamage(82));
		assertEquals(20, MagicMaxHit.PoweredStaff.TRIDENT_OF_THE_SEAS.baseDamage(75));
		assertEquals(24, MagicMaxHit.PoweredStaff.TRIDENT_OF_THE_SWAMP.baseDamage(78));
		assertEquals(29, MagicMaxHit.PoweredStaff.TUMEKENS_SHADOW.baseDamage(85));
		assertEquals(16, MagicMaxHit.PoweredStaff.WARPED_SCEPTRE.baseDamage(62));
		assertEquals(24, MagicMaxHit.PoweredStaff.WARPED_SCEPTRE.baseDamage(99));
	}

	@Test
	public void poweredStaffNames()
	{
		assertEquals(MagicMaxHit.PoweredStaff.SANGUINESTI_STAFF, MagicMaxHit.PoweredStaff.fromWeaponName("Holy sanguinesti staff"));
		assertEquals(MagicMaxHit.PoweredStaff.TRIDENT_OF_THE_SEAS, MagicMaxHit.PoweredStaff.fromWeaponName("Trident of the seas (e)"));
		assertEquals(MagicMaxHit.PoweredStaff.TUMEKENS_SHADOW, MagicMaxHit.PoweredStaff.fromWeaponName("Tumeken's shadow"));
		assertNull(MagicMaxHit.PoweredStaff.fromWeaponName("Tumeken's shadow (uncharged)"));
		assertNull(MagicMaxHit.PoweredStaff.fromWeaponName("Staff of fire"));
		assertNull(MagicMaxHit.PoweredStaff.fromWeaponName(null));
	}

	@Test
	public void spellsAreFoundByTheirAutocastObject()
	{
		assertEquals(MagicMaxHit.Spell.FIRE_SURGE, MagicMaxHit.Spell.fromObjectId(ItemID._95_FIRE_SURGE));
		assertEquals(MagicMaxHit.Spell.ICE_BARRAGE, MagicMaxHit.Spell.fromObjectId(ItemID._94_ICE_BARRAGE));
		assertNull(MagicMaxHit.Spell.fromObjectId(-1));
	}

	@Test
	public void elementalSpellsHitAsHardAsTheBestSpellOfTheirTier()
	{
		// Wiki: at 13+ Magic every Strike hits up to 8; below that, the spell's own base.
		assertEquals(2, MagicMaxHit.Spell.WIND_STRIKE.baseDamage(1, false));
		assertEquals(8, MagicMaxHit.Spell.WIND_STRIKE.baseDamage(13, false));
		assertEquals(20, MagicMaxHit.Spell.WIND_WAVE.baseDamage(99, false));
		assertEquals(21, MagicMaxHit.Spell.WIND_SURGE.baseDamage(81, false));
		assertEquals(24, MagicMaxHit.Spell.WIND_SURGE.baseDamage(95, false));
		// Ancients don't scale: Ice Barrage is always 30.
		assertEquals(30, MagicMaxHit.Spell.ICE_BARRAGE.baseDamage(99, false));
	}

	@Test
	public void magicDart()
	{
		// floor(Magic / 10) + 10, or floor(Magic / 6) + 13 with a Slayer's staff (e)
		assertEquals(19, MagicMaxHit.Spell.MAGIC_DART.baseDamage(99, false));
		assertEquals(29, MagicMaxHit.Spell.MAGIC_DART.baseDamage(99, true));
	}

	// ---- Magic: bonuses ---------------------------------------------------------

	@Test
	public void magicDamageBonusesAddUpBeforeMultiplying()
	{
		// Fire Surge (24) with +20% gear and Augury (+4%): floor(24 x 1.24) = 29
		int bonus = MagicMaxHit.bonusPermille(200, 1, false, MagicMaxHit.Salve.NONE, MagicMaxHit.MagicPrayer.AUGURY);
		assertEquals(240, bonus);
		assertEquals(29, MagicMaxHit.primaryDamage(24, bonus));
	}

	@Test
	public void tumekensShadowTriplesGearDamageUpToDouble()
	{
		// +20% gear x 3 = +60%; Elite Void (+5%) and prayer aren't tripled.
		assertEquals(600 + 50 + 40,
			MagicMaxHit.bonusPermille(200, 3, true, MagicMaxHit.Salve.NONE, MagicMaxHit.MagicPrayer.AUGURY));
		// +40% x 3 would be +120%, but the shadow's effect caps at +100%.
		assertEquals(1000, MagicMaxHit.bonusPermille(400, 3, false, MagicMaxHit.Salve.NONE, MagicMaxHit.MagicPrayer.NONE));
	}

	@Test
	public void salveIsAdditiveButSlayerHelmMultiplies()
	{
		// Base 30, +20% gear. Salve (ei) adds 20% more: floor(30 x 1.4) = 42
		int withSalve = MagicMaxHit.bonusPermille(200, 1, false, MagicMaxHit.Salve.SALVE_EI, MagicMaxHit.MagicPrayer.NONE);
		assertEquals(42, MagicMaxHit.primaryDamage(30, withSalve));
		// Slayer helm (i) instead: floor(floor(30 x 1.2) x 1.15) = floor(41.4) = 41
		assertEquals(41, MagicMaxHit.slayerHelm(MagicMaxHit.primaryDamage(30, 200)));
	}

	@Test
	public void percentFormatting()
	{
		assertEquals("5%", MaxHitCalculator.formatPercent(50));
		assertEquals("2.5%", MaxHitCalculator.formatPercent(25));
		assertEquals("0%", MaxHitCalculator.formatPercent(0));
	}
}
