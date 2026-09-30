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
 * Max hit bonuses from specific gear (OSRS Wiki, "Maximum melee hit", "Maximum ranged hit"
 * and each item's page). Integer maths, like {@link MeleeMaxHit}.
 *
 * Bonuses that always apply change the main max hit. Bonuses against one kind of monster
 * (dragons, demons, kalphites) are shown as their own "Vs ..." rows instead, since the
 * plugin can't know every monster's type.
 */
final class GearBonus
{
	private GearBonus()
	{
	}

	/**
	 * A weapon that hits harder against one kind of monster.
	 */
	enum Bane
	{
		DRAGON_HUNTER_LANCE("dragons", "Dragon hunter lance", 6, 5),
		DRAGON_HUNTER_CROSSBOW("dragons", "Dragon hunter crossbow", 5, 4),
		ARCLIGHT("demons", "Arclight", 17, 10),
		EMBERLIGHT("demons", "Emberlight", 17, 10),
		SILVERLIGHT("demons", "Silverlight", 8, 5),
		DARKLIGHT("demons", "Darklight", 8, 5),
		KERIS("kalphites", "Keris", 133, 100);

		private final String targets;
		private final String displayName;
		private final int numerator;
		private final int denominator;

		Bane(String targets, String displayName, int numerator, int denominator)
		{
			this.targets = targets;
			this.displayName = displayName;
			this.numerator = numerator;
			this.denominator = denominator;
		}

		String getTargets()
		{
			return targets;
		}

		String getDisplayName()
		{
			return displayName;
		}

		int apply(int maxHit)
		{
			return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * numerator / denominator);
		}

		/**
		 * @param weaponName lower case
		 * @return the weapon's bane, or null
		 */
		static Bane fromWeaponName(String weaponName)
		{
			if (weaponName == null)
			{
				return null;
			}
			if (weaponName.startsWith("dragon hunter lance"))
			{
				return DRAGON_HUNTER_LANCE;
			}
			if (weaponName.startsWith("dragon hunter crossbow"))
			{
				return DRAGON_HUNTER_CROSSBOW;
			}
			if (weaponName.startsWith("arclight"))
			{
				return ARCLIGHT;
			}
			if (weaponName.startsWith("emberlight"))
			{
				return EMBERLIGHT;
			}
			if (weaponName.startsWith("silverlight"))
			{
				return SILVERLIGHT;
			}
			if (weaponName.startsWith("darklight"))
			{
				return DARKLIGHT;
			}
			// The Amascut variant trades this bonus away.
			if (weaponName.contains("keris") && !weaponName.contains("amascut"))
			{
				return KERIS;
			}
			return null;
		}

		/**
		 * Whether a monster is one this bane works on. Used to highlight the row; names
		 * only, so a few unusual monsters may be missed.
		 */
		boolean matches(String npcName)
		{
			if (npcName == null)
			{
				return false;
			}
			String name = npcName.toLowerCase(Locale.ROOT);
			switch (targets)
			{
				case "dragons":
					return isDragon(name);
				case "demons":
					return isDemon(name);
				default:
					return name.contains("kalphite") || name.contains("scarab");
			}
		}
	}

	static boolean isDragon(String name)
	{
		// Elvarg and revenant dragons don't count (wiki).
		if (name.contains("revenant") || name.contains("elvarg"))
		{
			return false;
		}
		return name.contains("dragon") || name.contains("wyvern") || name.contains("wyrm") || name.contains("drake")
			|| name.contains("hydra") || name.equals("vorkath") || name.equals("galvek") || name.equals("great olm");
	}

	static boolean isDemon(String name)
	{
		return name.contains("demon") || name.contains("nechryael") || name.contains("nechryarch") || name.equals("imp")
			|| name.equals("k'ril tsutsaroth") || name.equals("skotizo") || name.equals("abyssal sire")
			|| name.equals("tstanon karlak") || name.equals("zakl'n gritch") || name.equals("balfrug kreeyath")
			|| name.equals("agrith-naar") || name.equals("delrith");
	}

	/**
	 * Obsidian armour (x1.1 for the full helm, body and legs) and the Berserker necklace
	 * (x1.2), with an obsidian weapon. They add together: both is x1.3.
	 */
	static int obsidian(int maxHit, boolean armourSet, boolean necklace)
	{
		int tenths = 10 + (armourSet ? 1 : 0) + (necklace ? 2 : 0);
		return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * tenths / 10);
	}

	/**
	 * Inquisitor's armour with crush attacks: +0.5% a piece, and the full set is +2.5%.
	 */
	static int inquisitor(int maxHit, int pieces)
	{
		if (pieces <= 0)
		{
			return maxHit;
		}
		int permille = pieces >= 3 ? 25 : pieces * 5;
		return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * (1000 + permille) / 1000);
	}

	/**
	 * Crystal armour with the Bow of faerdhinen or a crystal bow: helm +2.5%, legs +5%,
	 * body +7.5%.
	 */
	static int crystal(int maxHit, boolean helm, boolean body, boolean legs)
	{
		int fortieths = 40 + (helm ? 1 : 0) + (legs ? 2 : 0) + (body ? 3 : 0);
		return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * fortieths / 40);
	}

	/**
	 * Charged wilderness weapons against monsters in the Wilderness: x1.5.
	 */
	static int wilderness(int maxHit)
	{
		return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * 3 / 2);
	}

	/**
	 * The Twisted bow's damage multiplier as a percentage, from the target's Magic level
	 * (capped at 250 outside the Chambers of Xeric). 215% at 250 Magic.
	 */
	static int twistedBowPercent(int targetMagic)
	{
		int magic = Math.max(0, Math.min(250, targetMagic));
		int t2 = (3 * magic - 14) / 100;
		int t3 = (3 * magic / 10 - 140) * (3 * magic / 10 - 140) / 100;
		return Math.min(250, 250 + t2 - t3);
	}

	static int twistedBow(int maxHit, int targetMagic)
	{
		return Math.min(MeleeMaxHit.DAMAGE_CAP, maxHit * twistedBowPercent(targetMagic) / 100);
	}

	/**
	 * Obsidian melee weapons.
	 */
	static boolean isObsidianWeapon(String weaponName)
	{
		return weaponName.startsWith("toktz-xil-ak") || weaponName.startsWith("toktz-xil-ek")
			|| weaponName.startsWith("tzhaar-ket-em") || weaponName.startsWith("tzhaar-ket-om");
	}

	/**
	 * Weapons that only attack with crush, where Inquisitor's armour always applies.
	 */
	static boolean isCrushWeapon(String weaponName)
	{
		return weaponName.contains("mace") || weaponName.contains("warhammer") || weaponName.contains("maul")
			|| weaponName.contains("bludgeon") || weaponName.startsWith("tzhaar-ket");
	}

	static boolean isCrystalBow(String weaponName)
	{
		return !weaponName.contains("inactive")
			&& (weaponName.startsWith("bow of faerdhinen") || weaponName.startsWith("crystal bow"));
	}

	/**
	 * Charged wilderness weapons (the uncharged ones end in "(u)").
	 */
	static boolean isWildernessWeapon(String weaponName)
	{
		return weaponName.equals("craw's bow") || weaponName.equals("webweaver bow")
			|| weaponName.equals("viggora's chainmace") || weaponName.equals("ursine chainmace");
	}
}
