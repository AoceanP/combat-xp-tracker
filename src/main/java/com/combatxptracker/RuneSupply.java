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

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * How many casts of the autocast spell the player's runes allow (rune costs from the OSRS
 * Wiki spellbook pages). Elemental staves and tomes give unlimited runes of their element,
 * and a combination rune (e.g. a smoke rune) counts as one of each of its elements at once.
 */
final class RuneSupply
{
	enum Rune
	{
		AIR, WATER, EARTH, FIRE, MIND, CHAOS, DEATH, BLOOD, SOUL, WRATH;

		String getDisplayName()
		{
			return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * The casts left, and the rune that runs out first.
	 */
	static final class Result
	{
		/**
		 * Every rune the spell needs is unlimited.
		 */
		static final int UNLIMITED = Integer.MAX_VALUE;

		private final long casts;
		private final Rune limitedBy;

		Result(long casts, Rune limitedBy)
		{
			this.casts = casts;
			this.limitedBy = limitedBy;
		}

		long getCasts()
		{
			return casts;
		}

		/**
		 * @return the rune that runs out first, or null if none does
		 */
		Rune getLimitedBy()
		{
			return limitedBy;
		}

		boolean isUnlimited()
		{
			return casts >= UNLIMITED;
		}
	}

	private static final Map<MagicMaxHit.Spell, Map<Rune, Integer>> COSTS = new EnumMap<>(MagicMaxHit.Spell.class);
	private static final Map<Integer, Rune[]> RUNE_ITEMS = new HashMap<>();
	/**
	 * Staves and tomes that give unlimited runes, by a word in their name.
	 */
	private static final Map<String, Rune[]> UNLIMITED_BY_NAME = new HashMap<>();

	static
	{
		rune(ItemID.AIRRUNE, Rune.AIR);
		rune(ItemID.WATERRUNE, Rune.WATER);
		rune(ItemID.EARTHRUNE, Rune.EARTH);
		rune(ItemID.FIRERUNE, Rune.FIRE);
		rune(ItemID.SUNFIRERUNE, Rune.FIRE);
		rune(ItemID.MINDRUNE, Rune.MIND);
		rune(ItemID.CHAOSRUNE, Rune.CHAOS);
		rune(ItemID.DEATHRUNE, Rune.DEATH);
		rune(ItemID.BLOODRUNE, Rune.BLOOD);
		rune(ItemID.SOULRUNE, Rune.SOUL);
		rune(ItemID.AETHERRUNE, Rune.SOUL);
		rune(ItemID.WRATHRUNE, Rune.WRATH);
		rune(ItemID.MISTRUNE, Rune.AIR, Rune.WATER);
		rune(ItemID.DUSTRUNE, Rune.AIR, Rune.EARTH);
		rune(ItemID.SMOKERUNE, Rune.AIR, Rune.FIRE);
		rune(ItemID.MUDRUNE, Rune.WATER, Rune.EARTH);
		rune(ItemID.STEAMRUNE, Rune.WATER, Rune.FIRE);
		rune(ItemID.LAVARUNE, Rune.EARTH, Rune.FIRE);

		UNLIMITED_BY_NAME.put("air", new Rune[]{Rune.AIR});
		UNLIMITED_BY_NAME.put("water", new Rune[]{Rune.WATER});
		UNLIMITED_BY_NAME.put("earth", new Rune[]{Rune.EARTH});
		UNLIMITED_BY_NAME.put("fire", new Rune[]{Rune.FIRE});
		UNLIMITED_BY_NAME.put("mist", new Rune[]{Rune.AIR, Rune.WATER});
		UNLIMITED_BY_NAME.put("dust", new Rune[]{Rune.AIR, Rune.EARTH});
		UNLIMITED_BY_NAME.put("smoke", new Rune[]{Rune.AIR, Rune.FIRE});
		UNLIMITED_BY_NAME.put("mud", new Rune[]{Rune.WATER, Rune.EARTH});
		UNLIMITED_BY_NAME.put("steam", new Rune[]{Rune.WATER, Rune.FIRE});
		UNLIMITED_BY_NAME.put("lava", new Rune[]{Rune.EARTH, Rune.FIRE});
		UNLIMITED_BY_NAME.put("kodai", new Rune[]{Rune.WATER});
		UNLIMITED_BY_NAME.put("twinflame", new Rune[]{Rune.FIRE, Rune.WATER});

		cost(MagicMaxHit.Spell.WIND_STRIKE, Rune.AIR, 1, Rune.MIND, 1);
		cost(MagicMaxHit.Spell.WATER_STRIKE, Rune.AIR, 1, Rune.WATER, 1, Rune.MIND, 1);
		cost(MagicMaxHit.Spell.EARTH_STRIKE, Rune.AIR, 1, Rune.EARTH, 2, Rune.MIND, 1);
		cost(MagicMaxHit.Spell.FIRE_STRIKE, Rune.AIR, 2, Rune.FIRE, 3, Rune.MIND, 1);
		cost(MagicMaxHit.Spell.WIND_BOLT, Rune.AIR, 2, Rune.CHAOS, 1);
		cost(MagicMaxHit.Spell.WATER_BOLT, Rune.AIR, 2, Rune.WATER, 2, Rune.CHAOS, 1);
		cost(MagicMaxHit.Spell.EARTH_BOLT, Rune.AIR, 2, Rune.EARTH, 3, Rune.CHAOS, 1);
		cost(MagicMaxHit.Spell.FIRE_BOLT, Rune.AIR, 3, Rune.FIRE, 4, Rune.CHAOS, 1);
		cost(MagicMaxHit.Spell.WIND_BLAST, Rune.AIR, 3, Rune.DEATH, 1);
		cost(MagicMaxHit.Spell.WATER_BLAST, Rune.AIR, 3, Rune.WATER, 3, Rune.DEATH, 1);
		cost(MagicMaxHit.Spell.EARTH_BLAST, Rune.AIR, 3, Rune.EARTH, 4, Rune.DEATH, 1);
		cost(MagicMaxHit.Spell.FIRE_BLAST, Rune.AIR, 4, Rune.FIRE, 5, Rune.DEATH, 1);
		cost(MagicMaxHit.Spell.WIND_WAVE, Rune.AIR, 5, Rune.BLOOD, 1);
		cost(MagicMaxHit.Spell.WATER_WAVE, Rune.AIR, 5, Rune.WATER, 7, Rune.BLOOD, 1);
		cost(MagicMaxHit.Spell.EARTH_WAVE, Rune.AIR, 5, Rune.EARTH, 7, Rune.BLOOD, 1);
		cost(MagicMaxHit.Spell.FIRE_WAVE, Rune.AIR, 5, Rune.FIRE, 7, Rune.BLOOD, 1);
		cost(MagicMaxHit.Spell.WIND_SURGE, Rune.AIR, 7, Rune.WRATH, 1);
		cost(MagicMaxHit.Spell.WATER_SURGE, Rune.AIR, 7, Rune.WATER, 10, Rune.WRATH, 1);
		cost(MagicMaxHit.Spell.EARTH_SURGE, Rune.AIR, 7, Rune.EARTH, 10, Rune.WRATH, 1);
		cost(MagicMaxHit.Spell.FIRE_SURGE, Rune.AIR, 7, Rune.FIRE, 10, Rune.WRATH, 1);
		cost(MagicMaxHit.Spell.CRUMBLE_UNDEAD, Rune.AIR, 2, Rune.EARTH, 2, Rune.CHAOS, 1);
		cost(MagicMaxHit.Spell.IBAN_BLAST, Rune.FIRE, 5, Rune.DEATH, 1);
		cost(MagicMaxHit.Spell.MAGIC_DART, Rune.DEATH, 1, Rune.MIND, 4);
		cost(MagicMaxHit.Spell.SARADOMIN_STRIKE, Rune.AIR, 4, Rune.FIRE, 2, Rune.BLOOD, 2);
		cost(MagicMaxHit.Spell.CLAWS_OF_GUTHIX, Rune.AIR, 4, Rune.FIRE, 1, Rune.BLOOD, 2);
		cost(MagicMaxHit.Spell.FLAMES_OF_ZAMORAK, Rune.AIR, 1, Rune.FIRE, 4, Rune.BLOOD, 2);

		cost(MagicMaxHit.Spell.SMOKE_RUSH, Rune.AIR, 1, Rune.FIRE, 1, Rune.CHAOS, 2, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.SHADOW_RUSH, Rune.AIR, 1, Rune.CHAOS, 2, Rune.DEATH, 2, Rune.SOUL, 1);
		cost(MagicMaxHit.Spell.BLOOD_RUSH, Rune.BLOOD, 1, Rune.CHAOS, 2, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.ICE_RUSH, Rune.WATER, 2, Rune.CHAOS, 2, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.SMOKE_BURST, Rune.AIR, 2, Rune.FIRE, 2, Rune.CHAOS, 4, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.SHADOW_BURST, Rune.AIR, 1, Rune.CHAOS, 4, Rune.DEATH, 2, Rune.SOUL, 2);
		cost(MagicMaxHit.Spell.BLOOD_BURST, Rune.BLOOD, 2, Rune.CHAOS, 4, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.ICE_BURST, Rune.WATER, 4, Rune.CHAOS, 4, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.SMOKE_BLITZ, Rune.AIR, 2, Rune.FIRE, 2, Rune.BLOOD, 2, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.SHADOW_BLITZ, Rune.AIR, 2, Rune.BLOOD, 2, Rune.DEATH, 2, Rune.SOUL, 2);
		cost(MagicMaxHit.Spell.BLOOD_BLITZ, Rune.BLOOD, 4, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.ICE_BLITZ, Rune.WATER, 3, Rune.BLOOD, 2, Rune.DEATH, 2);
		cost(MagicMaxHit.Spell.SMOKE_BARRAGE, Rune.AIR, 4, Rune.FIRE, 4, Rune.BLOOD, 2, Rune.DEATH, 4);
		cost(MagicMaxHit.Spell.SHADOW_BARRAGE, Rune.AIR, 4, Rune.BLOOD, 2, Rune.DEATH, 4, Rune.SOUL, 3);
		cost(MagicMaxHit.Spell.BLOOD_BARRAGE, Rune.BLOOD, 4, Rune.DEATH, 4, Rune.SOUL, 1);
		cost(MagicMaxHit.Spell.ICE_BARRAGE, Rune.WATER, 6, Rune.BLOOD, 2, Rune.DEATH, 4);
	}

	private RuneSupply()
	{
	}

	private static void rune(int itemId, Rune... runes)
	{
		RUNE_ITEMS.put(itemId, runes);
	}

	private static void cost(MagicMaxHit.Spell spell, Object... pairs)
	{
		Map<Rune, Integer> cost = new EnumMap<>(Rune.class);
		for (int i = 0; i < pairs.length; i += 2)
		{
			cost.put((Rune) pairs[i], (Integer) pairs[i + 1]);
		}
		COSTS.put(spell, Collections.unmodifiableMap(cost));
	}

	/**
	 * The plain rune item for a rune, for its icon.
	 */
	static int itemFor(Rune rune)
	{
		switch (rune)
		{
			case AIR:
				return ItemID.AIRRUNE;
			case WATER:
				return ItemID.WATERRUNE;
			case EARTH:
				return ItemID.EARTHRUNE;
			case FIRE:
				return ItemID.FIRERUNE;
			case MIND:
				return ItemID.MINDRUNE;
			case CHAOS:
				return ItemID.CHAOSRUNE;
			case DEATH:
				return ItemID.DEATHRUNE;
			case BLOOD:
				return ItemID.BLOODRUNE;
			case SOUL:
				return ItemID.SOULRUNE;
			default:
				return ItemID.WRATHRUNE;
		}
	}

	/**
	 * @return the spell's rune cost, or an empty map if it isn't known
	 */
	static Map<Rune, Integer> costOf(MagicMaxHit.Spell spell)
	{
		Map<Rune, Integer> cost = spell == null ? null : COSTS.get(spell);
		return cost == null ? Collections.emptyMap() : cost;
	}

	/**
	 * Adds a stack of an item to the runes the player has, if it's a rune. A combination
	 * rune adds one of each of its elements.
	 */
	static void add(Map<Rune, Long> have, int itemId, long quantity)
	{
		Rune[] runes = RUNE_ITEMS.get(itemId);
		if (runes == null || quantity <= 0)
		{
			return;
		}
		for (Rune r : runes)
		{
			have.merge(r, quantity, Long::sum);
		}
	}

	/**
	 * Runes a worn weapon or shield gives without limit: elemental staves and battlestaves,
	 * combination staves, the Kodai wand, the Twinflame staff and the charged tomes.
	 *
	 * @param itemName lower-case item name
	 */
	static Set<Rune> unlimitedFrom(String itemName)
	{
		Set<Rune> out = EnumSet.noneOf(Rune.class);
		if (itemName == null)
		{
			return out;
		}
		boolean staff = itemName.contains("staff") || itemName.contains("kodai");
		boolean tome = itemName.startsWith("tome of ") && !itemName.contains("(empty)");
		if (!staff && !tome)
		{
			return out;
		}
		for (String word : itemName.split("[^a-z]+"))
		{
			Rune[] runes = UNLIMITED_BY_NAME.get(word);
			if (runes != null)
			{
				Collections.addAll(out, runes);
			}
		}
		return out;
	}

	/**
	 * How many casts the runes allow. Each combination rune counts towards both its
	 * elements at once, as it does in game.
	 */
	static Result castsLeft(MagicMaxHit.Spell spell, Map<Rune, Long> have, Set<Rune> unlimited)
	{
		Map<Rune, Integer> cost = costOf(spell);
		if (cost.isEmpty())
		{
			return null;
		}
		long best = Result.UNLIMITED;
		Rune limit = null;
		for (Map.Entry<Rune, Integer> e : cost.entrySet())
		{
			if (unlimited.contains(e.getKey()))
			{
				continue;
			}
			long casts = have.getOrDefault(e.getKey(), 0L) / e.getValue();
			if (casts < best)
			{
				best = casts;
				limit = e.getKey();
			}
		}
		return new Result(best, limit);
	}
}
