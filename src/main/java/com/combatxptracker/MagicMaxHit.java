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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * The magic max hit formula from the OSRS Wiki's "Maximum magic hit" page, for the
 * parts that don't depend on the target:
 *
 * <pre>
 * Base         = spell's base damage, or the powered staff's formula
 * Primary      = floor(Base x (1 + min(1, Gear% x Shadow) + Void + Salve + Prayer))
 * Slayer (i)   = floor(Primary x 1.15)   (not together with the Salve)
 * </pre>
 *
 * Bonuses are handled as whole per-mille (thousandths) so the maths stays exact.
 * Not covered: tomes, Chaos gauntlets, Charge, smoke staves, elemental weaknesses,
 * Virtus, sceptres in the wilderness, and special attacks.
 */
public final class MagicMaxHit
{
	private MagicMaxHit()
	{
	}

	public enum MagicPrayer
	{
		NONE("No prayer", 0),
		MYSTIC_LORE("Mystic Lore", 10),
		MYSTIC_MIGHT("Mystic Might", 20),
		MYSTIC_VIGOUR("Mystic Vigour", 30),
		AUGURY("Augury", 40);

		private final String displayName;
		private final int permille;

		MagicPrayer(String displayName, int permille)
		{
			this.displayName = displayName;
			this.permille = permille;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public int getPermille()
		{
			return permille;
		}
	}

	public enum Salve
	{
		NONE("", 0),
		/** Salve amulet(i): +15% magic damage against undead. The plain Salve does nothing for magic. */
		SALVE_I("Salve amulet(i)", 150),
		/** Salve amulet(ei): +20% against undead. */
		SALVE_EI("Salve amulet(ei)", 200);

		private final String displayName;
		private final int permille;

		Salve(String displayName, int permille)
		{
			this.displayName = displayName;
			this.permille = permille;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public int getPermille()
		{
			return permille;
		}
	}

	/**
	 * Staves with a built-in spell whose damage grows with the (visible) Magic level.
	 */
	public enum PoweredStaff
	{
		TRIDENT_OF_THE_SEAS("Trident of the seas", "trident of the seas"),
		TRIDENT_OF_THE_SWAMP("Trident of the swamp", "trident of the swamp"),
		SANGUINESTI_STAFF("Sanguinesti staff", "sanguinesti staff"),
		TUMEKENS_SHADOW("Tumeken's shadow", "tumeken's shadow"),
		WARPED_SCEPTRE("Warped sceptre", "warped sceptre");

		private final String displayName;
		private final String namePrefix;

		PoweredStaff(String displayName, String namePrefix)
		{
			this.displayName = displayName;
			this.namePrefix = namePrefix;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		/**
		 * Wiki formulas for each staff's base max hit.
		 */
		public int baseDamage(int magicLevel)
		{
			int level = Math.max(0, magicLevel);
			switch (this)
			{
				case TRIDENT_OF_THE_SEAS:
					return Math.max(0, level / 3 - 5);
				case TRIDENT_OF_THE_SWAMP:
					return Math.max(0, level / 3 - 2);
				case SANGUINESTI_STAFF:
					return level / 3;
				case TUMEKENS_SHADOW:
					return level / 3 + 1;
				case WARPED_SCEPTRE:
					return (8 * level + 96) / 37;
				default:
					return 0;
			}
		}

		/**
		 * @return the staff for a weapon name, or null. Uncharged staves can't attack.
		 */
		public static PoweredStaff fromWeaponName(String weaponName)
		{
			if (weaponName == null)
			{
				return null;
			}
			String name = weaponName.toLowerCase(Locale.ROOT);
			if (name.contains("uncharged"))
			{
				return null;
			}
			// Holy sanguinesti and the (e) tridents count as the same staff.
			String normalized = name.replace("holy ", "");
			for (PoweredStaff staff : values())
			{
				if (normalized.startsWith(staff.namePrefix))
				{
					return staff;
				}
			}
			return null;
		}
	}

	/**
	 * Autocastable combat spells, keyed by the spell objects the game uses for autocast.
	 */
	public enum Spell
	{
		// Standard elemental spells. Within a tier, the base damage rises to the
		// strongest spell of that tier the player can cast (see baseDamage).
		WIND_STRIKE(ItemID._01_WIND_STRIKE, "Wind Strike", Tier.STRIKE, 2, 1),
		WATER_STRIKE(ItemID._05_WATER_STRIKE, "Water Strike", Tier.STRIKE, 4, 5),
		EARTH_STRIKE(ItemID._09_EARTH_STRIKE, "Earth Strike", Tier.STRIKE, 6, 9),
		FIRE_STRIKE(ItemID._13_FIRE_STRIKE, "Fire Strike", Tier.STRIKE, 8, 13),
		WIND_BOLT(ItemID._17_WIND_BOLT, "Wind Bolt", Tier.BOLT, 9, 17),
		WATER_BOLT(ItemID._23_WATER_BOLT, "Water Bolt", Tier.BOLT, 10, 23),
		EARTH_BOLT(ItemID._29_EARTH_BOLT, "Earth Bolt", Tier.BOLT, 11, 29),
		FIRE_BOLT(ItemID._35_FIRE_BOLT, "Fire Bolt", Tier.BOLT, 12, 35),
		WIND_BLAST(ItemID._41_WIND_BLAST, "Wind Blast", Tier.BLAST, 13, 41),
		WATER_BLAST(ItemID._47_WATER_BLAST, "Water Blast", Tier.BLAST, 14, 47),
		EARTH_BLAST(ItemID._53_EARTH_BLAST, "Earth Blast", Tier.BLAST, 15, 53),
		FIRE_BLAST(ItemID._59_FIRE_BLAST, "Fire Blast", Tier.BLAST, 16, 59),
		WIND_WAVE(ItemID._62_WIND_WAVE, "Wind Wave", Tier.WAVE, 17, 62),
		WATER_WAVE(ItemID._65_WATER_WAVE, "Water Wave", Tier.WAVE, 18, 65),
		EARTH_WAVE(ItemID._70_EARTH_WAVE, "Earth Wave", Tier.WAVE, 19, 70),
		FIRE_WAVE(ItemID._75_FIRE_WAVE, "Fire Wave", Tier.WAVE, 20, 75),
		WIND_SURGE(ItemID._81_WIND_SURGE, "Wind Surge", Tier.SURGE, 21, 81),
		WATER_SURGE(ItemID._85_WATER_SURGE, "Water Surge", Tier.SURGE, 22, 85),
		EARTH_SURGE(ItemID._90_EARTH_SURGE, "Earth Surge", Tier.SURGE, 23, 90),
		FIRE_SURGE(ItemID._95_FIRE_SURGE, "Fire Surge", Tier.SURGE, 24, 95),

		// Other standard spells
		CRUMBLE_UNDEAD(ItemID._39_CRUMBLE_UNDEAD, "Crumble Undead", Tier.NONE, 15, 39),
		IBAN_BLAST(ItemID._50_IBAN_BLAST, "Iban Blast", Tier.NONE, 25, 50),
		MAGIC_DART(ItemID._50_MAGIC_DART, "Magic Dart", Tier.NONE, -1, 50),
		SARADOMIN_STRIKE(ItemID._60_SARADOMIN_STRIKE, "Saradomin Strike", Tier.NONE, 20, 60),
		CLAWS_OF_GUTHIX(ItemID._60_CLAWS_OF_GUTHIX, "Claws of Guthix", Tier.NONE, 20, 60),
		FLAMES_OF_ZAMORAK(ItemID._60_FLAMES_OF_ZAMORAK, "Flames of Zamorak", Tier.NONE, 20, 60),

		// Ancient Magicks
		SMOKE_RUSH(ItemID._50_SMOKE_RUSH, "Smoke Rush", Tier.NONE, 13, 50),
		SHADOW_RUSH(ItemID._52_SHADOW_RUSH, "Shadow Rush", Tier.NONE, 14, 52),
		BLOOD_RUSH(ItemID._56_BLOOD_RUSH, "Blood Rush", Tier.NONE, 15, 56),
		ICE_RUSH(ItemID._58_ICE_RUSH, "Ice Rush", Tier.NONE, 16, 58),
		SMOKE_BURST(ItemID._62_SMOKE_BURST, "Smoke Burst", Tier.NONE, 17, 62),
		SHADOW_BURST(ItemID._64_SHADOW_BURST, "Shadow Burst", Tier.NONE, 18, 64),
		BLOOD_BURST(ItemID._68_BLOOD_BURST, "Blood Burst", Tier.NONE, 21, 68),
		ICE_BURST(ItemID._70_ICE_BURST, "Ice Burst", Tier.NONE, 22, 70),
		SMOKE_BLITZ(ItemID._74_SMOKE_BLITZ, "Smoke Blitz", Tier.NONE, 23, 74),
		SHADOW_BLITZ(ItemID._76_SHADOW_BLITZ, "Shadow Blitz", Tier.NONE, 24, 76),
		BLOOD_BLITZ(ItemID._80_BLOOD_BLITZ, "Blood Blitz", Tier.NONE, 25, 80),
		ICE_BLITZ(ItemID._82_ICE_BLITZ, "Ice Blitz", Tier.NONE, 26, 82),
		SMOKE_BARRAGE(ItemID._86_SMOKE_BARRAGE, "Smoke Barrage", Tier.NONE, 27, 86),
		SHADOW_BARRAGE(ItemID._88_SHADOW_BARRAGE, "Shadow Barrage", Tier.NONE, 28, 88),
		BLOOD_BARRAGE(ItemID._92_BLOOD_BARRAGE, "Blood Barrage", Tier.NONE, 29, 92),
		ICE_BARRAGE(ItemID._94_ICE_BARRAGE, "Ice Barrage", Tier.NONE, 30, 94);

		enum Tier
		{
			NONE, STRIKE, BOLT, BLAST, WAVE, SURGE
		}

		private static final Map<Integer, Spell> BY_OBJECT = new HashMap<>();

		static
		{
			for (Spell spell : values())
			{
				BY_OBJECT.put(spell.objectId, spell);
			}
		}

		private final int objectId;
		private final String displayName;
		private final Tier tier;
		private final int base;
		private final int levelRequired;

		Spell(int objectId, String displayName, Tier tier, int base, int levelRequired)
		{
			this.objectId = objectId;
			this.displayName = displayName;
			this.tier = tier;
			this.base = base;
			this.levelRequired = levelRequired;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		/**
		 * @return the spell for an autocast spell object, or null if it isn't a combat spell
		 */
		public static Spell fromObjectId(int objectId)
		{
			return BY_OBJECT.get(objectId);
		}

		/**
		 * Base max hit at a Magic level.
		 *
		 * Elemental spells hit as hard as the strongest spell of their tier the player
		 * can cast: at level 13+ every Strike hits up to 8 (OSRS Wiki). Magic Dart is
		 * floor(Magic / 10) + 10, or floor(Magic / 6) + 13 with a Slayer's staff (e).
		 */
		public int baseDamage(int magicLevel, boolean slayersStaffE)
		{
			if (this == MAGIC_DART)
			{
				return slayersStaffE ? magicLevel / 6 + 13 : magicLevel / 10 + 10;
			}
			if (tier == Tier.NONE)
			{
				return base;
			}
			int best = base;
			for (Spell other : values())
			{
				if (other.tier == tier && other.levelRequired <= magicLevel)
				{
					best = Math.max(best, other.base);
				}
			}
			return best;
		}
	}

	/**
	 * The total additive bonus in per-mille: gear magic damage (times the Tumeken's
	 * shadow multiplier, capped at +100%), plus Elite Void, Salve and prayer.
	 *
	 * @param gearPermille     the sum of worn items' magic damage bonuses, e.g. 50 for 5%
	 * @param shadowMultiplier 3 with Tumeken's shadow (outside Tombs of Amascut), else 1
	 */
	public static int bonusPermille(int gearPermille, int shadowMultiplier, boolean eliteVoid, Salve salve, MagicPrayer prayer)
	{
		int gear = Math.min(1000, Math.max(0, gearPermille) * shadowMultiplier);
		return gear + (eliteVoid ? 50 : 0) + salve.getPermille() + prayer.getPermille();
	}

	public static int primaryDamage(int baseDamage, int bonusPermille)
	{
		return Math.min(MeleeMaxHit.DAMAGE_CAP, Math.max(0, baseDamage) * (1000 + bonusPermille) / 1000);
	}

	/**
	 * Black mask (i) / Slayer helmet (i) on task: x1.15.
	 */
	public static int slayerHelm(int primaryDamage)
	{
		return Math.min(MeleeMaxHit.DAMAGE_CAP, primaryDamage * 23 / 20);
	}
}
