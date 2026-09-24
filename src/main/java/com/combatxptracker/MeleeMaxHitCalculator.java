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
 * Reads the player's gear, prayers, Strength level, attack style and slayer task from
 * the client, and feeds them into {@link MeleeMaxHit}.
 *
 * Must be called on the client thread: item compositions, varbits and DB tables can
 * only be read there.
 *
 * Covered: strength bonus from every worn slot, boosted Strength (potions), the five
 * strength prayers, the selected attack style, Void melee, Salve amulet variants,
 * Black mask / Slayer helmet on task, and the 200 damage cap.
 * Not covered: special attacks, Dharok's, Obsidian / Berserker necklace, Inquisitor's,
 * Keris, Osmumten's fang and other weapon passives.
 */
public class MeleeMaxHitCalculator
{
	// Slayer task id meaning "boss task"; the real target is in a sublist
	// (from [proc,helper_slayer_current_assignment], same as the core Slayer plugin).
	private static final int BOSS_TASK_ID = 98;

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	public MeleeMaxHitCalculator(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Everything the panel shows about the max hit.
	 */
	public static final class Result
	{
		private final int maxHit;
		private final int onTaskMaxHit;
		private final int vsUndeadMaxHit;
		private final MeleeMaxHit.TargetBonus salve;
		private final String attackStyleName;
		private final boolean meleeStyle;
		private final MeleeMaxHit.StrengthPrayer prayer;
		private final int strengthBonus;
		private final boolean voidMelee;
		private final String slayerTask;
		private final boolean targetIsOnTask;

		Result(int maxHit, int onTaskMaxHit, int vsUndeadMaxHit, MeleeMaxHit.TargetBonus salve,
			String attackStyleName, boolean meleeStyle, MeleeMaxHit.StrengthPrayer prayer,
			int strengthBonus, boolean voidMelee, String slayerTask, boolean targetIsOnTask)
		{
			this.maxHit = maxHit;
			this.onTaskMaxHit = onTaskMaxHit;
			this.vsUndeadMaxHit = vsUndeadMaxHit;
			this.salve = salve;
			this.attackStyleName = attackStyleName;
			this.meleeStyle = meleeStyle;
			this.prayer = prayer;
			this.strengthBonus = strengthBonus;
			this.voidMelee = voidMelee;
			this.slayerTask = slayerTask;
			this.targetIsOnTask = targetIsOnTask;
		}

		public int getMaxHit()
		{
			return maxHit;
		}

		/**
		 * @return the max hit on slayer task targets, or -1 if no Black mask / Slayer
		 * helmet is worn or there's no task
		 */
		public int getOnTaskMaxHit()
		{
			return onTaskMaxHit;
		}

		/**
		 * @return the max hit against undead, or -1 if no Salve amulet is worn
		 */
		public int getVsUndeadMaxHit()
		{
			return vsUndeadMaxHit;
		}

		public MeleeMaxHit.TargetBonus getSalve()
		{
			return salve;
		}

		public String getAttackStyleName()
		{
			return attackStyleName;
		}

		/**
		 * False when the selected style is ranged or magic; the number is then what the
		 * player would hit on a melee style with +0 style bonus.
		 */
		public boolean isMeleeStyle()
		{
			return meleeStyle;
		}

		public MeleeMaxHit.StrengthPrayer getPrayer()
		{
			return prayer;
		}

		public int getStrengthBonus()
		{
			return strengthBonus;
		}

		public boolean isVoidMelee()
		{
			return voidMelee;
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
			return maxHit + "|" + onTaskMaxHit + "|" + vsUndeadMaxHit + "|" + salve + "|" + attackStyleName
				+ "|" + meleeStyle + "|" + prayer + "|" + strengthBonus + "|" + voidMelee + "|" + slayerTask
				+ "|" + targetIsOnTask;
		}
	}

	/**
	 * @return the result, or null if the equipment isn't loaded yet
	 */
	public Result calculate(CombatStyle.AttackStyle attackStyle, String slayerTask)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return null;
		}

		int strengthBonus = 0;
		boolean voidTop = false;
		boolean voidRobe = false;
		boolean voidGloves = false;
		boolean voidHelm = false;
		boolean slayerHeadgear = false;
		MeleeMaxHit.TargetBonus salve = null;

		for (Item item : worn.getItems())
		{
			int id = item.getId();
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
					strengthBonus += equipment.getStr();
				}
			}

			// Matching on names covers every recolour, imbue, ornament and league
			// variant without a list of dozens of item ids.
			String name = itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT);
			if (name.contains("void") && name.contains("top"))
			{
				voidTop = true;
			}
			else if (name.contains("void") && name.contains("robe"))
			{
				voidRobe = true;
			}
			else if (name.contains("void knight gloves"))
			{
				voidGloves = true;
			}
			else if (name.contains("void melee helm"))
			{
				voidHelm = true;
			}
			else if (name.startsWith("salve amulet"))
			{
				// "Salve amulet (e)" and "Salve amulet(ei)" are 20%; the rest 1/6.
				salve = name.contains("(e") ? MeleeMaxHit.TargetBonus.SALVE_E : MeleeMaxHit.TargetBonus.SALVE;
			}
			else if (name.contains("slayer helmet") || name.startsWith("black mask"))
			{
				slayerHeadgear = true;
			}
		}

		boolean voidMelee = voidTop && voidRobe && voidGloves && voidHelm;
		boolean meleeStyle = attackStyle != null && attackStyle.getStyle() == CombatStyle.MELEE;
		int styleBonus = meleeStyle ? attackStyle.getMeleeStrengthBonus() : 0;
		MeleeMaxHit.StrengthPrayer prayer = activeStrengthPrayer();

		int maxHit = MeleeMaxHit.maxHit(
			client.getBoostedSkillLevel(Skill.STRENGTH), prayer, styleBonus, voidMelee, strengthBonus);

		boolean hasTask = slayerTask != null;
		int onTask = slayerHeadgear && hasTask ? MeleeMaxHit.TargetBonus.SLAYER_HELM.apply(maxHit) : -1;
		// Salve and the slayer helm don't stack; against undead the Salve applies.
		int vsUndead = salve != null ? salve.apply(maxHit) : -1;

		boolean targetOnTask = hasTask && SlayerTaskMatcher.matches(slayerTask, currentTargetName());

		return new Result(maxHit, onTask, vsUndead, salve,
			attackStyle != null ? attackStyle.getName() : null, meleeStyle,
			prayer, strengthBonus, voidMelee, slayerTask, targetOnTask);
	}

	private MeleeMaxHit.StrengthPrayer activeStrengthPrayer()
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

	private boolean isActive(Prayer prayer)
	{
		return client.getVarbitValue(prayer.getVarbit()) == 1;
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
		// Staves only use indices 0-4; defensive casting is index 4 plus this varbit.
		if (index == 4)
		{
			index += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		}

		int stylesEnum = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
		if (stylesEnum == -1)
		{
			return CombatStyle.AttackStyle.fromName(fallbackStyleName(weaponType, index));
		}

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
		return CombatStyle.AttackStyle.fromName(name);
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
}
