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

import java.util.Locale;

/**
 * Melee special attacks whose max hit is a fixed multiple of the normal one, from the
 * OSRS Wiki's "Maximum melee hit" table:
 *
 * <pre>
 * Spec max = floor(floor(Base Damage) x Special Bonus)
 * </pre>
 *
 * Specs whose damage depends on other things aren't listed: Dragon claws, the Dragon
 * hasta (special bar), and the Saradomin sword (extra magic damage).
 */
public enum SpecialAttack
{
	// Order matters: longer names first where one name starts with another.
	ARMADYL_GODSWORD("Armadyl godsword", "armadyl godsword", 1375, 1),
	BANDOS_GODSWORD("Bandos godsword", "bandos godsword", 1210, 1),
	SARADOMIN_GODSWORD("Saradomin godsword", "saradomin godsword", 1100, 1),
	ZAMORAK_GODSWORD("Zamorak godsword", "zamorak godsword", 1100, 1),
	ANCIENT_GODSWORD("Ancient godsword", "ancient godsword", 1100, 1),
	DRAGON_DAGGER("Dragon dagger", "dragon dagger", 1150, 2),
	DRAGON_HALBERD("Dragon halberd", "dragon halberd", 1100, 1),
	CRYSTAL_HALBERD("Crystal halberd", "crystal halberd", 1100, 1),
	DRAGON_LONGSWORD("Dragon longsword", "dragon longsword", 1250, 1),
	DRAGON_MACE("Dragon mace", "dragon mace", 1500, 1),
	DRAGON_WARHAMMER("Dragon warhammer", "dragon warhammer", 1500, 1),
	RUNE_CLAWS("Rune claws", "rune claws", 1100, 1),
	ABYSSAL_DAGGER("Abyssal dagger", "abyssal dagger", 850, 2),
	SARADOMINS_BLESSED_SWORD("Saradomin's blessed sword", "saradomin's blessed sword", 1250, 1),
	/** 1 + 0.5% per prayer point missing; see {@link #maxHit}. */
	ABYSSAL_BLUDGEON("Abyssal bludgeon", "abyssal bludgeon", 1000, 1);

	private final String displayName;
	private final String namePrefix;
	// Multiplier in thousandths, so 1.375 = 1375.
	private final int permille;
	private final int hits;

	SpecialAttack(String displayName, String namePrefix, int permille, int hits)
	{
		this.displayName = displayName;
		this.namePrefix = namePrefix;
		this.permille = permille;
		this.hits = hits;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/**
	 * How many hits the spec makes (the Dragon and Abyssal dagger hit twice).
	 */
	public int getHits()
	{
		return hits;
	}

	/**
	 * @return the weapon's spec, or null. Covers poisoned and ornament variants, e.g.
	 * "Dragon dagger(p++)" or "Armadyl godsword (or)".
	 */
	public static SpecialAttack fromWeaponName(String weaponName)
	{
		if (weaponName == null)
		{
			return null;
		}
		String name = weaponName.toLowerCase(Locale.ROOT);
		for (SpecialAttack spec : values())
		{
			if (name.startsWith(spec.namePrefix))
			{
				return spec;
			}
		}
		return null;
	}

	/**
	 * Max hit of one hit of the spec.
	 *
	 * @param baseMaxHit           the normal max hit (before Slayer helm / Salve)
	 * @param prayerPointsMissing  only used by the Abyssal bludgeon
	 */
	public int maxHit(int baseMaxHit, int prayerPointsMissing)
	{
		int multiplier = permille;
		if (this == ABYSSAL_BLUDGEON)
		{
			// 1 + 0.005 x missing = (1000 + 5 x missing) / 1000
			multiplier = 1000 + 5 * Math.max(0, prayerPointsMissing);
		}
		return Math.min(MeleeMaxHit.DAMAGE_CAP, baseMaxHit * multiplier / 1000);
	}
}
