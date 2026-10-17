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

import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;

/**
 * Combat rewards that don't drop from a monster: raid chests, the Barrows chest and the like.
 * Each opens a reward interface; its items are in an item container the game keeps for it.
 * The plugin reads them itself when the interface opens.
 */
enum RewardSources
{
	CHAMBERS_OF_XERIC("Chambers of Xeric", InterfaceID.RAIDS_REWARDS, InventoryID.RAIDS_REWARDS),
	THEATRE_OF_BLOOD("Theatre of Blood", InterfaceID.TOB_CHESTS, InventoryID.TOB_CHESTS),
	TOMBS_OF_AMASCUT("Tombs of Amascut", InterfaceID.TOA_CHESTS, InventoryID.TOA_CHESTS),
	BARROWS("Barrows", InterfaceID.BARROWS_REWARD, InventoryID.TRAIL_REWARDINV),
	FORTIS_COLOSSEUM("Fortis Colosseum", InterfaceID.COLOSSEUM_REWARD_CHEST, InventoryID.COLOSSEUM_REWARDS),
	MOONS_OF_PERIL("Lunar Chest", InterfaceID.PMOON_REWARD, InventoryID.PMOON_REWARDINV);

	/**
	 * Opening the same chest again within this long (10 minutes) showing the same items is
	 * looking at the same reward, not a new one.
	 */
	static final long SAME_REWARD_MILLIS = 10 * 60_000L;

	private final String displayName;
	private final int interfaceId;
	private final int containerId;

	RewardSources(String displayName, int interfaceId, int containerId)
	{
		this.displayName = displayName;
		this.interfaceId = interfaceId;
		this.containerId = containerId;
	}

	/**
	 * The name of its card in the Monsters tab, the same as its collection log page.
	 */
	String getDisplayName()
	{
		return displayName;
	}

	int getContainerId()
	{
		return containerId;
	}

	/**
	 * @return the reward whose interface this is, or null
	 */
	static RewardSources ofInterface(int interfaceId)
	{
		for (RewardSources source : values())
		{
			if (source.interfaceId == interfaceId)
			{
				return source;
			}
		}
		return null;
	}

	/**
	 * The items as one comparable string, e.g. "995x31000,22324x1", to tell a reward seen
	 * again from a new one.
	 */
	static String signature(Map<Integer, Long> items)
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Long> e : new TreeMap<>(items).entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append('x').append(e.getValue());
		}
		return sb.toString();
	}
}
