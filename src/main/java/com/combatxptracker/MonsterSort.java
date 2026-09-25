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

import java.util.Comparator;

/**
 * Sort orders for the Monsters tab.
 */
public enum MonsterSort
{
	RECENT("Most recent", Comparator.comparingLong(MonsterTracker.Snapshot::getLastActivityMillis).reversed()),
	LOOT("Loot value", Comparator.comparingLong(MonsterTracker.Snapshot::getLootValue).reversed()),
	KILLS("Kills", Comparator.comparingInt(MonsterTracker.Snapshot::getKills).reversed()),
	BIGGEST_HIT("Biggest hit", Comparator.comparingInt(MonsterTracker.Snapshot::getBiggestHit).reversed()),
	NAME("Name", Comparator.comparing(s -> s.getName().toLowerCase()));

	private final String displayName;
	private final Comparator<MonsterTracker.Snapshot> comparator;

	MonsterSort(String displayName, Comparator<MonsterTracker.Snapshot> comparator)
	{
		this.displayName = displayName;
		// Ties fall back to most recent, so the order is stable between refreshes.
		this.comparator = comparator.thenComparing(
			Comparator.comparingLong(MonsterTracker.Snapshot::getLastActivityMillis).reversed());
	}

	public Comparator<MonsterTracker.Snapshot> comparator()
	{
		return comparator;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
