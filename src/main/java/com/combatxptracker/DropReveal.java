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
import java.util.List;

/**
 * A big drop to celebrate: a new collection log item, or a drop worth more than the rare drop
 * value. The panel plays a short "case opening" reveal for it.
 */
final class DropReveal
{
	private final int sequence;
	private final String item;
	private final int itemId;
	private final long value;
	private final String monster;
	private final boolean collectionLog;
	private final String detail;
	// Items the reveal's strip spins past before landing on this one.
	private final List<Integer> strip;

	DropReveal(int sequence, String item, int itemId, long value, String monster, boolean collectionLog, String detail,
		List<Integer> strip)
	{
		this.sequence = sequence;
		this.item = item;
		this.itemId = itemId;
		this.value = value;
		this.monster = monster;
		this.collectionLog = collectionLog;
		this.detail = detail;
		this.strip = strip == null ? Collections.emptyList() : strip;
	}

	int getSequence()
	{
		return sequence;
	}

	String getItem()
	{
		return item;
	}

	int getItemId()
	{
		return itemId;
	}

	long getValue()
	{
		return value;
	}

	String getMonster()
	{
		return monster;
	}

	boolean isCollectionLog()
	{
		return collectionLog;
	}

	/**
	 * e.g. "Vorkath, kc 1,216, 24.3x the rate".
	 */
	String getDetail()
	{
		return detail;
	}

	List<Integer> getStrip()
	{
		return strip;
	}

	/**
	 * e.g. "New collection log item!" or "Rare drop: 4.2M".
	 */
	String headline()
	{
		if (collectionLog)
		{
			return "New collection log item!";
		}
		return value > 0 ? "Rare drop: " + Formatting.compactXp(value) + " gp" : "Rare drop!";
	}

	/**
	 * A file name for its screenshot, e.g. "Unique Vorkaths head (Vorkath)".
	 */
	String screenshotName()
	{
		String name = (collectionLog ? "Unique " : "Rare drop ") + item + (monster == null ? "" : " (" + monster + ")");
		return name.replaceAll("[^A-Za-z0-9 ,()-]", "").trim();
	}
}
