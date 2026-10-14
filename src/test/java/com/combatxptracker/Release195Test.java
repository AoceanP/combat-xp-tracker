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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * 1.9.5: collection log sync.
 */
public class Release195Test
{
	private static List<CollectionLog.Item> vorkathItems()
	{
		return Arrays.asList(
			new CollectionLog.Item(22106, "Vorki", false),
			new CollectionLog.Item(22111, "Vorkath's head", false),
			new CollectionLog.Item(11286, "Draconic visage", true),
			new CollectionLog.Item(22006, "Skeletal visage", true),
			new CollectionLog.Item(2425, "Jar of decay", false));
	}

	@Test
	public void pageSyncAndKillCount()
	{
		CollectionLog log = new CollectionLog();
		log.syncPage("Vorkath", vorkathItems(), Arrays.asList("Vorkath", "Obtained: 2/5", "Vorkath kills: 1,204"), 300);
		CollectionLog.Status s = log.status("Vorkath", 310);
		assertEquals(2, s.getObtained());
		assertEquals(5, s.getTotal());
		assertEquals(Arrays.asList("Vorki", "Vorkath's head", "Jar of decay"), s.getMissing());
		// 1,204 when opened, plus 10 kills since.
		assertEquals(1_214, s.getKillCount());
		assertNull(log.status("Zulrah", 0));
	}

	@Test
	public void newItemMessageUpdatesThePage()
	{
		CollectionLog log = new CollectionLog();
		log.syncPage("Vorkath", vorkathItems(), Collections.singletonList("Vorkath"), 0);
		assertTrue(log.onNewItem("New item added to your collection log: Jar of decay"));
		assertFalse("already had it", log.onNewItem("New item added to your collection log: Draconic visage"));
		assertFalse(log.onNewItem("You feel something weird sneaking into your backpack."));
		CollectionLog.Status s = log.status("Vorkath", 0);
		assertEquals(3, s.getObtained());
		assertEquals(-1, s.getKillCount());
	}

	@Test
	public void sharedAndAliasedPages()
	{
		assertTrue(CollectionLog.matches("Callisto and Artio", "Callisto"));
		assertTrue(CollectionLog.matches("Callisto and Artio", "artio"));
		assertTrue(CollectionLog.matches("Dagannoth Kings", "Dagannoth Rex"));
		assertTrue(CollectionLog.matches("Barrows Chests", "Barrows brothers"));
		assertTrue(CollectionLog.matches("Vorkath", "vorkath"));
		assertFalse(CollectionLog.matches("Vorkath", "Vorkath's head"));
		assertFalse(CollectionLog.matches("Slayer", "Abyssal demon"));
	}

	@Test
	public void killCountLines()
	{
		assertEquals(31, CollectionLog.parseKillCount(Arrays.asList("Chambers of Xeric", "Chambers of Xeric completions: 31")));
		assertEquals(250, CollectionLog.parseKillCount(Collections.singletonList("Barrows Chests opened: 250")));
		assertEquals(-1, CollectionLog.parseKillCount(Collections.singletonList("Obtained: 2/5")));
	}

	@Test
	public void otherPlayersLogIsIgnored()
	{
		assertTrue(CollectionLog.isOtherPlayersTitle(Collections.singletonList("Collection Log - Zezima"), "AoceanP"));
		assertFalse(CollectionLog.isOtherPlayersTitle(Collections.singletonList("Collection Log - AoceanP"), "AoceanP"));
		assertFalse(CollectionLog.isOtherPlayersTitle(Collections.singletonList("Collection Log"), "AoceanP"));
	}

	@Test
	public void savesAndLoads()
	{
		CollectionLog log = new CollectionLog();
		log.syncPage("Vorkath", vorkathItems(), Arrays.asList("Vorkath", "Vorkath kills: 12"), 5);
		Gson gson = new Gson();
		String json = gson.toJson(log.exportState());
		CollectionLog loaded = new CollectionLog();
		loaded.importState(Arrays.asList(gson.fromJson(json, CollectionLog.Page[].class)));
		assertEquals(2, loaded.status("Vorkath", 5).getObtained());
		assertEquals(12, loaded.status("Vorkath", 5).getKillCount());
		loaded.importState(null);
		assertTrue(loaded.isEmpty());
	}

	@Test
	public void cardText()
	{
		CollectionLog log = new CollectionLog();
		log.syncPage("Vorkath", vorkathItems(), Collections.singletonList("Vorkath"), 0);
		assertEquals("Collection log 2/5", MonsterCard.collectionText(log.status("Vorkath", 0)));
		String missing = MonsterCard.missingText(log.status("Vorkath", 0));
		assertTrue(missing, missing.contains("Missing Vorki, Vorkath's head +1"));
		log.syncPage("Vorkath", vorkathItems(), Arrays.asList("Vorkath", "Vorkath kills: 1,204"), 0);
		assertEquals("Collection log 2/5, 1,214 kc", MonsterCard.collectionText(log.status("Vorkath", 10)));
		for (String item : Arrays.asList("Vorki", "Vorkath's head", "Jar of decay"))
		{
			log.onNewItem(CollectionLog.NEW_ITEM_PREFIX + item);
		}
		assertEquals("Collection log complete (5/5)", MonsterCard.collectionText(log.status("Vorkath", 0)));
	}
}
