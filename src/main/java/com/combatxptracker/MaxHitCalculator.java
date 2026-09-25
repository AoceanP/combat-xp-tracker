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

import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Reads the player's gear, prayers, levels, attack style, autocast spell and slayer
 * task from the client, and works out the max hit for the style they're using with
 * {@link MeleeMaxHit}, {@link RangedMaxHit} or {@link MagicMaxHit}.
 *
 * Must be called on the client thread: item compositions, varbits, enums and DB tables
 * can only be read there.
 */
public class MaxHitCalculator
{
	// Slayer task id meaning "boss task"; the real target is in a sublist
	// (from [proc,helper_slayer_current_assignment], same as the core Slayer plugin).
	private static final int BOSS_TASK_ID = 98;

	// Autocast spell number -> spell object. There's no gameval constant for this enum;
	// the id comes from the game's own [clientscript,combat_interface_autocast].
	private static final int AUTOCAST_SPELLS_ENUM = 1986;

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	public MaxHitCalculator(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Everything the panel shows about the max hit.
	 */
	public static final class Result
	{
		private final CombatStyle style;
		private final int maxHit;
		private final int onTaskMaxHit;
		private final int vsUndeadMaxHit;
		private final String undeadSource;
		private final String setup;
		private final String note;
		private final String slayerTask;
		private final boolean targetIsOnTask;

		Result(CombatStyle style, int maxHit, int onTaskMaxHit, int vsUndeadMaxHit, String undeadSource,
			String setup, String note, String slayerTask, boolean targetIsOnTask)
		{
			this.style = style;
			this.maxHit = maxHit;
			this.onTaskMaxHit = onTaskMaxHit;
			this.vsUndeadMaxHit = vsUndeadMaxHit;
			this.undeadSource = undeadSource;
			this.setup = setup;
			this.note = note;
			this.slayerTask = slayerTask;
			this.targetIsOnTask = targetIsOnTask;
		}

		public CombatStyle getStyle()
		{
			return style;
		}

		/**
		 * @return the max hit, or -1 when it can't be worked out (e.g. magic with no
		 * autocast spell); {@link #getNote()} then says why
		 */
		public int getMaxHit()
		{
			return maxHit;
		}

		/**
		 * @return the max hit on slayer task monsters, or -1 without the right headgear
		 * or a task
		 */
		public int getOnTaskMaxHit()
		{
			return onTaskMaxHit;
		}

		/**
		 * @return the max hit against undead, or -1 without a Salve amulet that works
		 * for this style
		 */
		public int getVsUndeadMaxHit()
		{
			return vsUndeadMaxHit;
		}

		public String getUndeadSource()
		{
			return undeadSource;
		}

		/**
		 * Short description of what went into the number, e.g. "Aggressive, Piety, +150 str".
		 */
		public String getSetup()
		{
			return setup;
		}

		/**
		 * @return a hint shown instead of the number, or null
		 */
		public String getNote()
		{
			return note;
		}

		public String getSlayerTask()
		{
			return slayerTask;
		}

		public boolean isTargetOnTask()
		{
			return targetIsOnTask;
		}

		/**
		 * Every field, so two results that would display the same compare equal. The
		 * plugin uses this to only refresh the panel when something visible changed.
		 */
		@Override
		public String toString()
		{
			return style + "|" + maxHit + "|" + onTaskMaxHit + "|" + vsUndeadMaxHit + "|" + undeadSource
				+ "|" + setup + "|" + note + "|" + slayerTask + "|" + targetIsOnTask;
		}
	}

	/**
	 * What's worn, boiled down to what the formulas need.
	 */
	private static final class Gear
	{
		int meleeStrength;
		int rangedStrength;
		// Magic damage in per-mille, e.g. 50 = 5%.
		int magicDamage;
		String weaponName = "";
		boolean voidTop;
		boolean eliteTop;
		boolean voidRobe;
		boolean eliteRobe;
		boolean voidGloves;
		boolean meleeHelm;
		boolean rangerHelm;
		boolean mageHelm;
		boolean slayerHeadgear;
		boolean slayerHeadgearImbued;
		boolean salve;
		boolean salveImbued;
		boolean salveEnchanted;
	}

	/**
	 * @return the result, or null if the equipment isn't loaded yet
	 */
	public Result calculate(CombatStyle.AttackStyle attackStyle, String slayerTask)
	{
		Gear gear = readGear();
		if (gear == null)
		{
			return null;
		}

		CombatStyle style = attackStyle != null ? attackStyle.getStyle() : CombatStyle.MELEE;
		boolean targetOnTask = slayerTask != null && SlayerTaskMatcher.matches(slayerTask, currentTargetName());

		switch (style)
		{
			case RANGED:
				return ranged(gear, attackStyle, slayerTask, targetOnTask);
			case MAGIC:
				return magic(gear, attackStyle, slayerTask, targetOnTask);
			default:
				return melee(gear, attackStyle, slayerTask, targetOnTask);
		}
	}

	private Result melee(Gear gear, CombatStyle.AttackStyle attackStyle, String task, boolean targetOnTask)
	{
		boolean voidMelee = (gear.voidTop || gear.eliteTop) && (gear.voidRobe || gear.eliteRobe) && gear.voidGloves && gear.meleeHelm;
		MeleeMaxHit.StrengthPrayer prayer = meleePrayer();
		int styleBonus = attackStyle != null ? attackStyle.getMeleeStrengthBonus() : 0;

		int max = MeleeMaxHit.maxHit(client.getBoostedSkillLevel(Skill.STRENGTH), prayer, styleBonus, voidMelee, gear.meleeStrength);
		int onTask = gear.slayerHeadgear && task != null ? MeleeMaxHit.TargetBonus.SLAYER_HELM.apply(max) : -1;

		MeleeMaxHit.TargetBonus salve = null;
		if (gear.salve)
		{
			salve = gear.salveEnchanted ? MeleeMaxHit.TargetBonus.SALVE_E : MeleeMaxHit.TargetBonus.SALVE;
		}
		int vsUndead = salve != null ? salve.apply(max) : -1;

		String setup = join(attackStyle != null ? attackStyle.getName() : "No style",
			prayer == MeleeMaxHit.StrengthPrayer.NONE ? null : prayer.getDisplayName(),
			signed(gear.meleeStrength) + " str",
			voidMelee ? "Void" : null);
		return new Result(CombatStyle.MELEE, max, onTask, vsUndead, salve == null ? null : salve.getDisplayName(),
			setup, null, task, targetOnTask);
	}

	private Result ranged(Gear gear, CombatStyle.AttackStyle attackStyle, String task, boolean targetOnTask)
	{
		RangedMaxHit.VoidSet voidSet = RangedMaxHit.VoidSet.NONE;
		if (gear.voidGloves && gear.rangerHelm)
		{
			if (gear.eliteTop && gear.eliteRobe)
			{
				voidSet = RangedMaxHit.VoidSet.ELITE;
			}
			else if ((gear.voidTop || gear.eliteTop) && (gear.voidRobe || gear.eliteRobe))
			{
				voidSet = RangedMaxHit.VoidSet.VOID;
			}
		}
		RangedMaxHit.RangedPrayer prayer = rangedPrayer();

		int max = RangedMaxHit.maxHit(client.getBoostedSkillLevel(Skill.RANGED), prayer,
			attackStyle.getRangedStrengthBonus(), voidSet, gear.rangedStrength);
		// Only the imbued mask/helm and Salve work for ranged.
		int onTask = gear.slayerHeadgearImbued && task != null ? RangedMaxHit.TargetBonus.SLAYER_HELM_I.apply(max) : -1;

		RangedMaxHit.TargetBonus salve = null;
		if (gear.salve && gear.salveImbued)
		{
			salve = gear.salveEnchanted ? RangedMaxHit.TargetBonus.SALVE_EI : RangedMaxHit.TargetBonus.SALVE_I;
		}
		int vsUndead = salve != null ? salve.apply(max) : -1;

		String setup = join(attackStyle.getName(),
			prayer == RangedMaxHit.RangedPrayer.NONE ? null : prayer.getDisplayName(),
			signed(gear.rangedStrength) + " rstr",
			voidSet == RangedMaxHit.VoidSet.ELITE ? "Elite Void" : voidSet == RangedMaxHit.VoidSet.VOID ? "Void" : null);
		return new Result(CombatStyle.RANGED, max, onTask, vsUndead, salve == null ? null : salve.getDisplayName(),
			setup, null, task, targetOnTask);
	}

	private Result magic(Gear gear, CombatStyle.AttackStyle attackStyle, String task, boolean targetOnTask)
	{
		int magicLevel = client.getBoostedSkillLevel(Skill.MAGIC);
		MagicMaxHit.PoweredStaff staff = MagicMaxHit.PoweredStaff.fromWeaponName(gear.weaponName);

		int base;
		String source;
		if (staff != null)
		{
			base = staff.baseDamage(magicLevel);
			source = staff.getDisplayName();
		}
		else
		{
			MagicMaxHit.Spell spell = autocastSpell();
			if (spell == null)
			{
				return new Result(CombatStyle.MAGIC, -1, -1, -1, null, null,
					"Set an autocast spell to see your magic max hit", task, targetOnTask);
			}
			boolean slayersStaffE = gear.weaponName.toLowerCase(Locale.ROOT).startsWith("slayer's staff (e)");
			base = spell.baseDamage(magicLevel, slayersStaffE);
			source = spell.getDisplayName();
		}

		int shadow = staff == MagicMaxHit.PoweredStaff.TUMEKENS_SHADOW ? 3 : 1;
		boolean eliteVoid = gear.eliteTop && gear.eliteRobe && gear.voidGloves && gear.mageHelm;
		MagicMaxHit.MagicPrayer prayer = magicPrayer();

		int bonus = MagicMaxHit.bonusPermille(gear.magicDamage, shadow, eliteVoid, MagicMaxHit.Salve.NONE, prayer);
		int max = MagicMaxHit.primaryDamage(base, bonus);
		int onTask = gear.slayerHeadgearImbued && task != null ? MagicMaxHit.slayerHelm(max) : -1;

		MagicMaxHit.Salve salve = MagicMaxHit.Salve.NONE;
		if (gear.salve && gear.salveImbued)
		{
			salve = gear.salveEnchanted ? MagicMaxHit.Salve.SALVE_EI : MagicMaxHit.Salve.SALVE_I;
		}
		int vsUndead = salve != MagicMaxHit.Salve.NONE
			? MagicMaxHit.primaryDamage(base, MagicMaxHit.bonusPermille(gear.magicDamage, shadow, eliteVoid, salve, prayer))
			: -1;

		int shownBonus = Math.min(1000, gear.magicDamage * shadow) + (eliteVoid ? 50 : 0);
		String setup = join(source,
			prayer == MagicMaxHit.MagicPrayer.NONE ? null : prayer.getDisplayName(),
			"+" + formatPercent(shownBonus) + " dmg",
			eliteVoid ? "Elite Void" : null);
		return new Result(CombatStyle.MAGIC, max, onTask, vsUndead,
			salve == MagicMaxHit.Salve.NONE ? null : salve.getDisplayName(), setup, null, task, targetOnTask);
	}

	private Gear readGear()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return null;
		}

		Gear gear = new Gear();
		Item[] items = worn.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			int id = items[slot].getId();
			if (id <= 0)
			{
				continue;
			}

			ItemStats stats = itemManager.getItemStats(id);
			if (stats != null)
			{
				ItemEquipmentStats equipment = stats.getEquipment();
				if (equipment != null)
				{
					gear.meleeStrength += equipment.getStr();
					gear.rangedStrength += equipment.getRstr();
					gear.magicDamage += Math.round(equipment.getMdmg() * 10);
				}
			}

			// Matching on names covers every recolour, imbue, ornament and league
			// variant without a list of dozens of item ids.
			String name = itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT);
			if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				gear.weaponName = name;
			}
			readNamedGear(gear, name);
		}
		return gear;
	}

	private static void readNamedGear(Gear gear, String name)
	{
		if (name.contains("elite void top"))
		{
			gear.eliteTop = true;
		}
		else if (name.contains("void knight top"))
		{
			gear.voidTop = true;
		}
		else if (name.contains("elite void robe"))
		{
			gear.eliteRobe = true;
		}
		else if (name.contains("void knight robe"))
		{
			gear.voidRobe = true;
		}
		else if (name.contains("void knight gloves"))
		{
			gear.voidGloves = true;
		}
		else if (name.contains("void melee helm"))
		{
			gear.meleeHelm = true;
		}
		else if (name.contains("void ranger helm"))
		{
			gear.rangerHelm = true;
		}
		else if (name.contains("void mage helm"))
		{
			gear.mageHelm = true;
		}
		else if (name.startsWith("salve amulet"))
		{
			// "Salve amulet", "Salve amulet(i)", "Salve amulet (e)", "Salve amulet(ei)"
			String compact = name.replace(" ", "");
			gear.salve = true;
			gear.salveImbued = compact.contains("(i)") || compact.contains("(ei)");
			gear.salveEnchanted = compact.contains("(e)") || compact.contains("(ei)");
		}
		else if (name.contains("slayer helmet") || name.startsWith("black mask"))
		{
			gear.slayerHeadgear = true;
			gear.slayerHeadgearImbued = name.contains("(i)");
		}
	}

	private MagicMaxHit.Spell autocastSpell()
	{
		int autocast = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
		if (autocast <= 0)
		{
			return null;
		}
		int spellObject = client.getEnum(AUTOCAST_SPELLS_ENUM).getIntValue(autocast);
		return MagicMaxHit.Spell.fromObjectId(spellObject);
	}

	private boolean isActive(Prayer prayer)
	{
		return client.getVarbitValue(prayer.getVarbit()) == 1;
	}

	private MeleeMaxHit.StrengthPrayer meleePrayer()
	{
		if (isActive(Prayer.PIETY))
		{
			return MeleeMaxHit.StrengthPrayer.PIETY;
		}
		if (isActive(Prayer.CHIVALRY))
		{
			return MeleeMaxHit.StrengthPrayer.CHIVALRY;
		}
		if (isActive(Prayer.ULTIMATE_STRENGTH))
		{
			return MeleeMaxHit.StrengthPrayer.ULTIMATE_STRENGTH;
		}
		if (isActive(Prayer.SUPERHUMAN_STRENGTH))
		{
			return MeleeMaxHit.StrengthPrayer.SUPERHUMAN_STRENGTH;
		}
		if (isActive(Prayer.BURST_OF_STRENGTH))
		{
			return MeleeMaxHit.StrengthPrayer.BURST_OF_STRENGTH;
		}
		return MeleeMaxHit.StrengthPrayer.NONE;
	}

	private RangedMaxHit.RangedPrayer rangedPrayer()
	{
		if (isActive(Prayer.RIGOUR))
		{
			return RangedMaxHit.RangedPrayer.RIGOUR;
		}
		if (isActive(Prayer.DEADEYE))
		{
			return RangedMaxHit.RangedPrayer.DEADEYE;
		}
		if (isActive(Prayer.EAGLE_EYE))
		{
			return RangedMaxHit.RangedPrayer.EAGLE_EYE;
		}
		if (isActive(Prayer.HAWK_EYE))
		{
			return RangedMaxHit.RangedPrayer.HAWK_EYE;
		}
		if (isActive(Prayer.SHARP_EYE))
		{
			return RangedMaxHit.RangedPrayer.SHARP_EYE;
		}
		return RangedMaxHit.RangedPrayer.NONE;
	}

	private MagicMaxHit.MagicPrayer magicPrayer()
	{
		if (isActive(Prayer.AUGURY))
		{
			return MagicMaxHit.MagicPrayer.AUGURY;
		}
		if (isActive(Prayer.MYSTIC_VIGOUR))
		{
			return MagicMaxHit.MagicPrayer.MYSTIC_VIGOUR;
		}
		if (isActive(Prayer.MYSTIC_MIGHT))
		{
			return MagicMaxHit.MagicPrayer.MYSTIC_MIGHT;
		}
		if (isActive(Prayer.MYSTIC_LORE))
		{
			return MagicMaxHit.MagicPrayer.MYSTIC_LORE;
		}
		return MagicMaxHit.MagicPrayer.NONE;
	}

	private String currentTargetName()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}
		Actor target = local.getInteracting();
		return target instanceof NPC ? target.getName() : null;
	}

	/**
	 * The attack style selected in the combat tab, or null if it can't be read. Uses the
	 * same weapon-style enum and structs as the core Attack Styles plugin.
	 */
	public CombatStyle.AttackStyle readAttackStyle()
	{
		int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		int index = client.getVarpValue(VarPlayerID.COM_MODE);
		int rawIndex = index;
		// Staves only use indices 0-4; defensive casting is index 4 plus this varbit.
		if (index == 4)
		{
			index += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		}

		CombatStyle.AttackStyle style;
		int stylesEnum = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
		if (stylesEnum == -1)
		{
			style = CombatStyle.AttackStyle.fromName(fallbackStyleName(weaponType, index));
		}
		else
		{
			int[] styleStructs = client.getEnum(stylesEnum).getIntVals();
			if (index < 0 || index >= styleStructs.length)
			{
				return null;
			}
			StructComposition struct = client.getStructComposition(styleStructs[index]);
			String name = struct.getStringValue(ParamID.ATTACK_STYLE_NAME);
			// Index 5 reuses the "Defensive" struct for defensive casting.
			if (index == 5 && "Defensive".equalsIgnoreCase(name))
			{
				name = "Defensive casting";
			}
			style = CombatStyle.AttackStyle.fromName(name);
		}

		if (style != null && style.getStyle() == CombatStyle.RANGED && rawIndex == 0)
		{
			style = CombatStyle.AttackStyle.rangedAccurate();
		}

		// Powered staves report melee-looking style names but always attack with magic.
		String weapon = equippedWeaponName();
		if (MagicMaxHit.PoweredStaff.fromWeaponName(weapon) != null)
		{
			style = CombatStyle.AttackStyle.poweredStaff(style != null ? style.getName() : null);
		}
		return style;
	}

	private String equippedWeaponName()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return null;
		}
		Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null || weapon.getId() <= 0)
		{
			return null;
		}
		return itemManager.getItemComposition(weapon.getId()).getName();
	}

	/**
	 * Weapon types missing from the enum, hardcoded the same way the core Attack Styles
	 * plugin does.
	 */
	private static String fallbackStyleName(int weaponType, int index)
	{
		String[] names;
		if (weaponType == 22)
		{
			// Blue moon spear
			names = new String[]{"Accurate", "Aggressive", null, "Defensive", "Casting", "Defensive casting"};
		}
		else if (weaponType == 30)
		{
			// Partisan
			names = new String[]{"Accurate", "Aggressive", "Aggressive", "Defensive"};
		}
		else
		{
			return null;
		}
		return index >= 0 && index < names.length ? names[index] : null;
	}

	/**
	 * The current slayer task's name, e.g. "Abyssal demons", or null with no task.
	 * Read from the game's slayer task table, the same way the core Slayer plugin does.
	 */
	public String readSlayerTaskName()
	{
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) <= 0)
		{
			return null;
		}

		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int taskRow;
		if (taskId == BOSS_TASK_ID)
		{
			List<Integer> bossRows = client.getDBRowsByValue(
				DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0,
				client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (bossRows.isEmpty())
			{
				return null;
			}
			taskRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
		}
		else
		{
			List<Integer> taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (taskRows.isEmpty())
			{
				return null;
			}
			taskRow = taskRows.get(0);
		}

		Object[] name = client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
		return name.length > 0 && name[0] instanceof String ? (String) name[0] : null;
	}

	private static String join(String... parts)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : parts)
		{
			if (part == null || part.isEmpty())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(part);
		}
		return sb.toString();
	}

	private static String signed(int value)
	{
		return value >= 0 ? "+" + value : String.valueOf(value);
	}

	/**
	 * Per-mille as a percentage: 50 -> "5%", 25 -> "2.5%".
	 */
	static String formatPercent(int permille)
	{
		return permille % 10 == 0 ? permille / 10 + "%" : permille / 10 + "." + Math.abs(permille % 10) + "%";
	}
}
