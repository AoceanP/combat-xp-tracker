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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the player has from each collection log page, so a boss's monster card can show
 * what's still missing. A page is saved when the player opens it in the in-game Collection
 * Log, and kept up to date by the "New item added to your collection log" chat message.
 *
 * Saved per account as JSON, so field names are the save format. Synchronized: it's
 * updated on the client thread and read by the panel.
 */
final class CollectionLog
{
	static final String NEW_ITEM_PREFIX = "New item added to your collection log: ";
	// A page's kill count line, e.g. "Vorkath kills: 1,204" or "Chambers of Xeric completions: 31".
	private static final Pattern COUNT = Pattern.compile("(?:kills|count|completions|opened|chests)\\s*:\\s*([\\d,]+)",
		Pattern.CASE_INSENSITIVE);
	// Boss cards whose collection log page has another name.
	private static final Map<String, String> PAGE_ALIASES = new LinkedHashMap<>();

	static
	{
		PAGE_ALIASES.put("dagannoth rex", "dagannoth kings");
		PAGE_ALIASES.put("dagannoth prime", "dagannoth kings");
		PAGE_ALIASES.put("dagannoth supreme", "dagannoth kings");
		PAGE_ALIASES.put("barrows brothers", "barrows chests");
		PAGE_ALIASES.put("phosani's nightmare", "the nightmare");
		PAGE_ALIASES.put("tztok-jad", "the fight caves");
		PAGE_ALIASES.put("tzkal-zuk", "the inferno");
	}

	/**
	 * One item on a page.
	 */
	static final class Item
	{
		int id;
		String name;
		boolean obtained;

		Item()
		{
		}

		Item(int id, String name, boolean obtained)
		{
			this.id = id;
			this.name = name;
			this.obtained = obtained;
		}
	}

	/**
	 * One page: its items, and the kill count when it was last opened.
	 */
	static final class Page
	{
		String title;
		List<Item> items = new ArrayList<>();
		// The page's own kill count when opened, or -1 if it doesn't show one.
		int killCount = -1;
		// The plugin's all-time kills of the monster then, so kills since can be added.
		long trackedKills;
	}

	/**
	 * What a monster card shows.
	 */
	static final class Status
	{
		private final int obtained;
		private final int total;
		private final List<String> missing;
		private final int killCount;

		Status(int obtained, int total, List<String> missing, int killCount)
		{
			this.obtained = obtained;
			this.total = total;
			this.missing = missing;
			this.killCount = killCount;
		}

		int getObtained()
		{
			return obtained;
		}

		int getTotal()
		{
			return total;
		}

		List<String> getMissing()
		{
			return missing;
		}

		/**
		 * @return the kill count now, or -1 if it isn't known
		 */
		int getKillCount()
		{
			return killCount;
		}

		boolean isComplete()
		{
			return obtained >= total;
		}
	}

	private final Map<String, Page> pages = new LinkedHashMap<>();
	private int revision;

	/**
	 * The player opened a page: replaces what was saved for it.
	 *
	 * @param headerLines the page header's text lines (title first)
	 * @param trackedKills the plugin's all-time kills of the page's monsters right now
	 */
	synchronized void syncPage(String title, List<Item> items, List<String> headerLines, long trackedKills)
	{
		if (title == null || title.trim().isEmpty() || items.isEmpty())
		{
			return;
		}
		Page page = new Page();
		page.title = title.trim();
		page.items = new ArrayList<>(items);
		page.killCount = parseKillCount(headerLines);
		page.trackedKills = trackedKills;
		pages.put(key(page.title), page);
		revision++;
	}

	/**
	 * The "New item added to your collection log" message: marks it obtained on every page.
	 *
	 * @return whether a saved page had it as missing
	 */
	synchronized boolean onNewItem(String message)
	{
		if (message == null || !message.startsWith(NEW_ITEM_PREFIX))
		{
			return false;
		}
		String name = message.substring(NEW_ITEM_PREFIX.length()).trim();
		boolean changed = false;
		for (Page page : pages.values())
		{
			for (Item item : page.items)
			{
				if (!item.obtained && item.name != null && item.name.equalsIgnoreCase(name))
				{
					item.obtained = true;
					changed = true;
				}
			}
		}
		if (changed)
		{
			revision++;
		}
		return changed;
	}

	/**
	 * @param trackedKills the plugin's all-time kills of the page's monsters now
	 * @return the monster's collection log page status, or null if it hasn't been opened
	 */
	synchronized Status status(String monster, long trackedKills)
	{
		Page page = pageFor(monster);
		if (page == null)
		{
			return null;
		}
		int obtained = 0;
		List<String> missing = new ArrayList<>();
		for (Item item : page.items)
		{
			if (item.obtained)
			{
				obtained++;
			}
			else
			{
				missing.add(item.name);
			}
		}
		int kc = page.killCount < 0 ? -1 : (int) (page.killCount + Math.max(0, trackedKills - page.trackedKills));
		return new Status(obtained, page.items.size(), missing, kc);
	}

	/**
	 * @return the title of the saved page an item is on, or null
	 */
	synchronized String pageOf(String itemName)
	{
		for (Page page : pages.values())
		{
			for (Item item : page.items)
			{
				if (item.name != null && item.name.equalsIgnoreCase(itemName))
				{
					return page.title;
				}
			}
		}
		return null;
	}

	/**
	 * @return whether an item is on a saved page and not obtained yet
	 */
	synchronized boolean isMissing(String itemName)
	{
		for (Page page : pages.values())
		{
			for (Item item : page.items)
			{
				if (!item.obtained && item.name != null && item.name.equalsIgnoreCase(itemName))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return copies of the items on a monster's saved page, in page order, or an empty list
	 */
	synchronized List<Item> items(String monster)
	{
		Page page = pageFor(monster);
		List<Item> out = new ArrayList<>();
		if (page != null)
		{
			for (Item item : page.items)
			{
				out.add(new Item(item.id, item.name, item.obtained));
			}
		}
		return out;
	}

	/**
	 * @return the saved page title for a monster card, or null
	 */
	synchronized String titleFor(String monster)
	{
		Page page = pageFor(monster);
		return page == null ? null : page.title;
	}

	private Page pageFor(String monster)
	{
		if (monster == null)
		{
			return null;
		}
		for (Page p : pages.values())
		{
			if (matches(p.title, monster))
			{
				return p;
			}
		}
		return null;
	}

	/**
	 * Whether a monster card belongs to a collection log page: the same name, an alias (the
	 * Dagannoth Kings), or one half of a shared page such as "Callisto and Artio".
	 */
	static boolean matches(String pageTitle, String monster)
	{
		if (pageTitle == null || monster == null)
		{
			return false;
		}
		String title = key(pageTitle);
		String name = key(monster);
		if (title.equals(name) || title.equals(PAGE_ALIASES.get(name)))
		{
			return true;
		}
		for (String part : title.split(" and "))
		{
			if (part.equals(name))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The kill count in a page's header lines, or -1.
	 */
	static int parseKillCount(List<String> headerLines)
	{
		for (String line : headerLines)
		{
			Matcher m = COUNT.matcher(line == null ? "" : line);
			if (m.find())
			{
				try
				{
					return Integer.parseInt(m.group(1).replace(",", ""));
				}
				catch (NumberFormatException e)
				{
					return -1;
				}
			}
		}
		return -1;
	}

	/**
	 * Whether the log's title lines name another player, e.g. "Collection Log - Zezima" seen
	 * through a house's Adventure Log. The player's own log doesn't name anyone else.
	 */
	static boolean isOtherPlayersTitle(List<String> titleLines, String playerName)
	{
		String me = key(playerName.replace(' ', ' '));
		for (String line : titleLines)
		{
			String text = key(line.replace(' ', ' '));
			int dash = text.indexOf("collection log - ");
			if (dash >= 0)
			{
				return !text.substring(dash + "collection log - ".length()).trim().equals(me);
			}
		}
		return false;
	}

	synchronized int getRevision()
	{
		return revision;
	}

	synchronized List<Page> exportState()
	{
		List<Page> out = new ArrayList<>();
		for (Page p : pages.values())
		{
			Page copy = new Page();
			copy.title = p.title;
			copy.killCount = p.killCount;
			copy.trackedKills = p.trackedKills;
			for (Item i : p.items)
			{
				copy.items.add(new Item(i.id, i.name, i.obtained));
			}
			out.add(copy);
		}
		return out;
	}

	synchronized void importState(List<Page> saved)
	{
		pages.clear();
		if (saved != null)
		{
			for (Page p : saved)
			{
				if (p != null && p.title != null && p.items != null)
				{
					pages.put(key(p.title), p);
				}
			}
		}
		revision++;
	}

	synchronized boolean isEmpty()
	{
		return pages.isEmpty();
	}

	private static String key(String s)
	{
		return s.toLowerCase(Locale.ROOT).trim();
	}
}
