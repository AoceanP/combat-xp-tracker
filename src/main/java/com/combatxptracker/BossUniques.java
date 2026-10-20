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
import java.util.Locale;
import java.util.Map;

/**
 * Each boss's collection log uniques, bundled from the OSRS Wiki's collection log page (so it
 * works before the player opens the page in game). Items that drop more often than 1 in 40
 * kills, like the Royal Titans' desiccated pages, aren't counted as uniques.
 */
final class BossUniques
{
	private final Map<String, List<String>> pages;

	BossUniques(Map<String, List<String>> pages)
	{
		this.pages = pages == null ? Collections.emptyMap() : pages;
	}

	/**
	 * @return whether the item is one of the monster's collection log uniques
	 */
	boolean isUnique(String monster, String item)
	{
		if (monster == null || item == null)
		{
			return false;
		}
		String wanted = plain(item);
		for (Map.Entry<String, List<String>> page : pages.entrySet())
		{
			if (CollectionLog.matches(page.getKey(), monster))
			{
				for (String unique : page.getValue())
				{
					if (plain(unique).equals(wanted))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * @return whether the monster has a collection log page with uniques
	 */
	boolean hasUniques(String monster)
	{
		for (String title : pages.keySet())
		{
			if (CollectionLog.matches(title, monster))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Names compared without case or a charge suffix: "Giantsoul amulet (uncharged)" is the
	 * same unique as "Giantsoul amulet".
	 */
	static String plain(String name)
	{
		return name.replaceAll("\\s*\\((?:uncharged|empty|inactive|unf)\\)$", "").trim().toLowerCase(Locale.ROOT);
	}
}
