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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Reads the game's own slayer tables: where each master's tasks can be done, their
 * weights and sizes, level requirements, and the reward unlocks. Reading them live means
 * the plugin keeps up with game updates without a hand-made list.
 *
 * Client thread only. Every read is guarded: if the tables ever change shape, the Slayer
 * tab just shows less instead of breaking.
 */
@Singleton
class SlayerGameData
{
	// Slayer task id meaning "boss task"; the real target is in a sublist.
	private static final int BOSS_TASK_ID = 98;
	// Multi-value columns are read one tuple at a time; none has anywhere near this many.
	private static final int MAX_TUPLES = 40;
	/**
	 * The varps holding the reward unlock bits, 32 to a varp: unlock bit n is bit n % 32 of
	 * UNLOCK_VARPS[n / 32]. Checked at runtime against the Bigger and Badder varbit.
	 */
	private static final int[] UNLOCK_VARPS = {
		VarPlayerID.SLAYER_REWARDS_UNLOCKS, VarPlayerID.SLAYER_REWARDS_UNLOCKS1, VarPlayerID.SLAYER_REWARDS_UNLOCKS2,
	};
	private static final String SANITY_UNLOCK = "bigger and badder";

	private final Client client;
	private final ItemManager itemManager;
	// The current task's areas, read once per task rather than every tick.
	private String areasKey;
	private List<SlayerGuide.Area> areas = Collections.emptyList();

	@Inject
	SlayerGameData(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	// ---- Current task --------------------------------------------------------------------

	/**
	 * @return the SlayerTask table row of the current task, or -1 without one
	 */
	int currentTaskRow()
	{
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) <= 0)
		{
			return -1;
		}
		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		if (taskId == BOSS_TASK_ID)
		{
			List<Integer> rows = rowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			return rows.isEmpty() ? -1 : intField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, -1);
		}
		List<Integer> rows = rowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, taskId);
		return rows.isEmpty() ? -1 : rows.get(0);
	}

	/**
	 * Where the current task can be done with this master, and what to bring.
	 *
	 * @param assignedArea Konar's chosen area name, or null
	 */
	SlayerGuide guide(SlayerPoints.Master master, String taskName, String assignedArea)
	{
		if (taskName == null)
		{
			return SlayerGuide.NONE;
		}
		int taskRow = currentTaskRow();
		String key = master + "|" + taskRow + "|" + assignedArea;
		if (!key.equals(areasKey))
		{
			areasKey = key;
			areas = readAreas(master, taskRow, assignedArea);
		}

		Set<String> carried = carriedItemNames();
		List<SlayerGuide.NeedStatus> needs = new ArrayList<>();
		for (TaskRequirements.Need need : TaskRequirements.forTask(taskName))
		{
			boolean unlocked = need.getUnlockVarbit() >= 0 && client.getVarbitValue(need.getUnlockVarbit()) == 1;
			needs.add(new SlayerGuide.NeedStatus(need.getLabel(), unlocked || need.isMetBy(carried), need.isInfoOnly()));
		}
		return new SlayerGuide(taskName, areas, needs);
	}

	private List<SlayerGuide.Area> readAreas(SlayerPoints.Master master, int taskRow, String assignedArea)
	{
		List<SlayerGuide.Area> found = new ArrayList<>();
		if (master != null && taskRow >= 0)
		{
			int masterTask = masterTaskRow(masterId(master), taskRow);
			if (masterTask >= 0)
			{
				for (int area : intTuples(masterTask, DBTableID.SlayerMasterTask.COL_AREAS))
				{
					String name = stringField(area, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER);
					if (name == null)
					{
						name = stringField(area, DBTableID.SlayerArea.COL_AREA_TEXT);
					}
					if (name != null)
					{
						String hint = stringField(area, DBTableID.SlayerArea.COL_AREA_HINT);
						found.add(new SlayerGuide.Area(name, hint, name.equalsIgnoreCase(assignedArea)));
					}
				}
			}
		}
		return Collections.unmodifiableList(found);
	}

	/**
	 * Lower-case names of everything worn and in the inventory.
	 */
	private Set<String> carriedItemNames()
	{
		Set<String> names = new HashSet<>();
		for (int container : new int[]{InventoryID.WORN, InventoryID.INV})
		{
			ItemContainer items = client.getItemContainer(container);
			if (items == null)
			{
				continue;
			}
			for (Item item : items.getItems())
			{
				if (item.getId() > 0)
				{
					names.add(itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT));
				}
			}
		}
		return names;
	}

	// ---- Next task odds --------------------------------------------------------------------

	/**
	 * Every task a master can assign, with whether the player can get it right now.
	 */
	TaskOdds odds(SlayerPoints.Master master, Set<String> blockedLowercase)
	{
		if (master == null)
		{
			return TaskOdds.NONE;
		}
		Boolean unlocksReadable = unlocksReadable();
		boolean unlocksKnown = Boolean.TRUE.equals(unlocksReadable);
		Player player = client.getLocalPlayer();
		int combatLevel = player == null ? 0 : player.getCombatLevel();
		int id = masterId(master);

		List<TaskOdds.Option> options = new ArrayList<>();
		for (int row : tableRows(DBTableID.SlayerMasterTask.ID))
		{
			if (intField(row, DBTableID.SlayerMasterTask.COL_MASTER_ID, -1) != id)
			{
				continue;
			}
			int taskRow = intField(row, DBTableID.SlayerMasterTask.COL_TASK, -1);
			String name = taskRow < 0 ? null : stringField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE);
			if (name == null)
			{
				continue;
			}
			int weight = intField(row, DBTableID.SlayerMasterTask.COL_WEIGHT, 0);
			int min = intField(row, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT, 0);
			int max = intField(row, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT, 0);

			// Rewards that change the weight or extend the task.
			for (Object[] t : tuples(taskRow, DBTableID.SlayerTask.COL_UNLOCK_WEIGHTING))
			{
				if (t.length >= 2 && unlockOwned(asInt(t[0]), unlocksKnown))
				{
					weight = asInt(t[1]);
				}
			}
			for (Object[] t : tuples(taskRow, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX))
			{
				if (t.length >= 3 && unlockOwned(asInt(t[0]), unlocksKnown))
				{
					min = asInt(t[1]);
					max = asInt(t[2]);
				}
			}

			options.add(new TaskOdds.Option(name, weight, min, max,
				unavailable(row, taskRow, combatLevel, unlocksKnown)));
		}
		return TaskOdds.compute(master.getDisplayName(), options, blockedLowercase, unlocksKnown);
	}

	/**
	 * @return why the player can't get this task right now, or null if they can
	 */
	private String unavailable(int masterTaskRow, int taskRow, int combatLevel, boolean unlocksKnown)
	{
		int minCombat = intField(taskRow, DBTableID.SlayerTask.COL_MIN_COMLEVEL, 0);
		if (combatLevel < minCombat)
		{
			return "Needs combat level " + minCombat;
		}
		for (Object[] t : tuples(taskRow, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ALL))
		{
			Skill skill = t.length >= 2 ? skill(asInt(t[1])) : null;
			if (skill != null && client.getRealSkillLevel(skill) < asInt(t[0]))
			{
				return "Needs " + asInt(t[0]) + " " + Formatting.capitalize(skill.getName());
			}
		}
		List<Object[]> any = tuples(taskRow, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ANY);
		if (!any.isEmpty())
		{
			boolean met = false;
			for (Object[] t : any)
			{
				Skill skill = t.length >= 2 ? skill(asInt(t[1])) : null;
				met |= skill != null && client.getRealSkillLevel(skill) >= asInt(t[0]);
			}
			if (!met)
			{
				return "Needs a higher combat skill";
			}
		}
		int needed = intField(masterTaskRow, DBTableID.SlayerMasterTask.COL_TASK_UNLOCK, -1);
		if (needed >= 0 && !unlockOwned(needed, unlocksKnown))
		{
			return "Needs the " + unlockName(needed) + " reward";
		}
		int turnsOff = intField(taskRow, DBTableID.SlayerTask.COL_BLOCK_UNLOCK, -1);
		if (turnsOff >= 0 && unlockOwned(turnsOff, unlocksKnown))
		{
			return "Turned off by " + unlockName(turnsOff);
		}
		return null;
	}

	// ---- Reward unlocks ---------------------------------------------------------------------

	/**
	 * Whether the unlock bits can be trusted: Bigger and Badder's bit must agree with its own
	 * varbit. Null if that row can't be found.
	 */
	private Boolean unlocksReadable()
	{
		for (int row : tableRows(DBTableID.SlayerUnlock.ID))
		{
			String name = stringField(row, DBTableID.SlayerUnlock.COL_NAME);
			if (name != null && name.toLowerCase(Locale.ROOT).startsWith(SANITY_UNLOCK))
			{
				Boolean bit = bitSet(intField(row, DBTableID.SlayerUnlock.COL_BIT, -1));
				boolean varbit = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS) != 0;
				return bit != null && bit == varbit;
			}
		}
		return null;
	}

	private boolean unlockOwned(int unlockRow, boolean unlocksKnown)
	{
		if (!unlocksKnown || unlockRow < 0)
		{
			return false;
		}
		return Boolean.TRUE.equals(bitSet(intField(unlockRow, DBTableID.SlayerUnlock.COL_BIT, -1)));
	}

	private Boolean bitSet(int bit)
	{
		if (bit < 0 || bit >= UNLOCK_VARPS.length * 32)
		{
			return null;
		}
		return (client.getVarpValue(UNLOCK_VARPS[bit / 32]) >>> (bit % 32) & 1) != 0;
	}

	private String unlockName(int unlockRow)
	{
		String name = stringField(unlockRow, DBTableID.SlayerUnlock.COL_NAME);
		return name == null ? "right" : name;
	}

	// ---- Table helpers ------------------------------------------------------------------------

	private int masterTaskRow(int masterId, int taskRow)
	{
		for (int row : tableRows(DBTableID.SlayerMasterTask.ID))
		{
			if (intField(row, DBTableID.SlayerMasterTask.COL_MASTER_ID, -1) == masterId
				&& intField(row, DBTableID.SlayerMasterTask.COL_TASK, -1) == taskRow)
			{
				return row;
			}
		}
		return -1;
	}

	/**
	 * The master's id in the game's tables: the same as the slayer master varbit.
	 */
	private static int masterId(SlayerPoints.Master master)
	{
		return master.getId();
	}

	private static Skill skill(int statId)
	{
		Skill[] skills = Skill.values();
		return statId >= 0 && statId < skills.length ? skills[statId] : null;
	}

	private List<Integer> tableRows(int table)
	{
		try
		{
			List<Integer> rows = client.getDBTableRows(table);
			return rows == null ? Collections.emptyList() : rows;
		}
		catch (RuntimeException e)
		{
			return Collections.emptyList();
		}
	}

	private List<Integer> rowsByValue(int table, int column, Object value)
	{
		try
		{
			List<Integer> rows = client.getDBRowsByValue(table, column, 0, value);
			return rows == null ? Collections.emptyList() : rows;
		}
		catch (RuntimeException e)
		{
			return Collections.emptyList();
		}
	}

	private Object[] field(int row, int column, int tuple)
	{
		try
		{
			Object[] value = client.getDBTableField(row, column, tuple);
			return value == null ? new Object[0] : value;
		}
		catch (RuntimeException e)
		{
			return new Object[0];
		}
	}

	/**
	 * Every tuple of a multi-value column, until one comes back empty.
	 */
	private List<Object[]> tuples(int row, int column)
	{
		List<Object[]> out = new ArrayList<>();
		for (int i = 0; i < MAX_TUPLES; i++)
		{
			Object[] value = field(row, column, i);
			if (value.length == 0)
			{
				break;
			}
			out.add(value);
		}
		return out;
	}

	private Set<Integer> intTuples(int row, int column)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (Object[] t : tuples(row, column))
		{
			if (t[0] instanceof Integer && !out.add((Integer) t[0]))
			{
				// The same value again: past the end of the list.
				break;
			}
		}
		return out;
	}

	private int intField(int row, int column, int fallback)
	{
		Object[] value = field(row, column, 0);
		return value.length > 0 && value[0] instanceof Integer ? (Integer) value[0] : fallback;
	}

	private String stringField(int row, int column)
	{
		Object[] value = field(row, column, 0);
		return value.length > 0 && value[0] instanceof String && !((String) value[0]).isEmpty() ? (String) value[0] : null;
	}

	private static int asInt(Object o)
	{
		return o instanceof Integer ? (Integer) o : -1;
	}
}
