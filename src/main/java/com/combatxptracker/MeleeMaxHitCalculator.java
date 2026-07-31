/*
 * Copyright (c) 2026, YourNameHere <https://github.com/YourNameHere>
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

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Computes melee max hit from the player's current equipment, boosted Strength level,
 * and active prayer, using the formula published on the OSRS Wiki's "Maximum melee hit"
 * page (fetched directly from the live page, last edited 27 April 2026):
 *
 *   Effective Strength = floor((floor(Strength + PotionBonus) x Prayer) + Style + 8) x Void
 *   Base Damage = 0.5 + Effective Strength x (Strength Bonus + 64) / 640
 *   Max Hit = floor(Base Damage)
 *
 * This is written independently from real RuneLite API calls -- it is NOT a port or
 * derivative of any existing max-hit plugin's source. The underlying formula is public
 * game mechanics documented by the wiki, not anyone's original expression; only the
 * *code* implementing it needs to be original, and it is.
 *
 * SCOPE (deliberately limited on this first pass, matching how even the most popular
 * existing max-hit plugin, at 116k+ installs, still lists unsupported cases on its own
 * page rather than claiming full coverage):
 *   - Covers: equipped weapon's melee strength bonus, boosted Strength level (which
 *     already reflects potions via getBoostedSkillLevel), the five strength-boosting
 *     prayers, aggressive/controlled/accurate style bonus, void melee.
 *   - NOT covered yet: weapon special attacks (AGS, DWH, dragon dagger, etc.), Dharok's
 *     HP-scaling passive, Salve amulet / Slayer helm conditionals (undead / task
 *     detection isn't attempted), Obsidian/Berserker necklace set bonuses, Inquisitor's
 *     armour, Osmumten's fang's asymmetric roll, Keris variants, or the 200 damage cap.
 *     A style bonus of 0 (accurate/defensive) is assumed by default since the current
 *     attack style in use isn't read here.
 */
public class MeleeMaxHitCalculator
{
	private final Client client;
	private final ItemManager itemManager;

	@Inject
	public MeleeMaxHitCalculator(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Style bonus added during effective strength calculation. Aggressive gives +3,
	 * controlled +1, accurate/defensive +0 (per the wiki's bonus table). Exposed so the
	 * caller can offer a style selector rather than this class guessing the active style.
	 */
	public enum StyleBonus
	{
		ACCURATE_OR_DEFENSIVE(0),
		CONTROLLED(1),
		AGGRESSIVE(3);

		private final int value;

		StyleBonus(int value)
		{
			this.value = value;
		}
	}

	/**
	 * @return the calculated max hit, or -1 if no weapon is equipped or its equipment
	 * stats couldn't be resolved (e.g. unarmed, or the item has no equipment data).
	 */
	public int calculate(StyleBonus style, boolean voidMeleeActive)
	{
		Integer strengthBonus = getEquippedStrengthBonus();
		if (strengthBonus == null)
		{
			return -1;
		}

		int boostedStrength = client.getBoostedSkillLevel(Skill.STRENGTH);

		double prayerMultiplier = getStrengthPrayerMultiplier();

		// Effective Strength = floor((floor(Strength + PotionBonus) x Prayer) + Style + 8) x Void
		// getBoostedSkillLevel already folds in potion bonuses, so the inner floor(Strength
		// + PotionBonus) step collapses to just the boosted level itself.
		double afterPrayer = boostedStrength * prayerMultiplier;
		double beforeVoid = Math.floor(afterPrayer) + style.value + 8;
		double voidMultiplier = voidMeleeActive ? 1.1 : 1.0;
		int effectiveStrength = (int) Math.floor(beforeVoid * voidMultiplier);

		// Base Damage = 0.5 + Effective Strength x (Strength Bonus + 64) / 640
		double baseDamage = 0.5 + effectiveStrength * (strengthBonus + 64) / 640.0;

		return (int) Math.floor(baseDamage);
	}

	/**
	 * @return the equipped weapon's melee strength bonus, or null if no weapon is
	 * equipped or its stats aren't resolvable. Only the weapon slot is read -- armour
	 * pieces also contribute strength bonus in the real game (e.g. Bandos, Fighter
	 * torso), but summing every slot is left for a later pass rather than guessed at
	 * here; treat this as weapon-only strength bonus for now, understating total bonus
	 * for players wearing strength-bonus armour.
	 */
	private Integer getEquippedStrengthBonus()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return null;
		}

		Item[] items = equipment.getItems();
		int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		if (weaponSlot < 0 || weaponSlot >= items.length)
		{
			return null;
		}

		Item weapon = items[weaponSlot];
		if (weapon == null || weapon.getId() <= 0)
		{
			return null;
		}

		ItemStats stats = itemManager.getItemStats(weapon.getId());
		if (stats == null || !stats.isEquipable())
		{
			return null;
		}

		ItemEquipmentStats equipmentStats = stats.getEquipment();
		if (equipmentStats == null)
		{
			return null;
		}

		return equipmentStats.getStr();
	}

	/**
	 * @return the strength-boosting prayer multiplier for whichever of the five relevant
	 * prayers is currently active, or 1.0 if none are. Values confirmed against the OSRS
	 * Wiki's bonus table (Burst of Strength 1.05, Superhuman Strength 1.1, Ultimate
	 * Strength 1.15, Chivalry 1.18, Piety 1.23). If multiple were somehow simultaneously
	 * active (shouldn't happen in practice, since these prayers occupy the same prayer
	 * book slot), the strongest is used.
	 */
	private double getStrengthPrayerMultiplier()
	{
		if (client.isPrayerActive(Prayer.PIETY))
		{
			return 1.23;
		}
		if (client.isPrayerActive(Prayer.CHIVALRY))
		{
			return 1.18;
		}
		if (client.isPrayerActive(Prayer.ULTIMATE_STRENGTH))
		{
			return 1.15;
		}
		if (client.isPrayerActive(Prayer.SUPERHUMAN_STRENGTH))
		{
			return 1.1;
		}
		if (client.isPrayerActive(Prayer.BURST_OF_STRENGTH))
		{
			return 1.05;
		}
		return 1.0;
	}
}
