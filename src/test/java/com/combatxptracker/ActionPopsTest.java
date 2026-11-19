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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 2.5.0: action pops, a reactive confirmation of your own actions. Off by default.
 */
public class ActionPopsTest
{
	private static final Map<Integer, String> NAMES = new HashMap<>();

	static
	{
		NAMES.put(1, "Prayer potion(4)");
		NAMES.put(2, "Prayer potion(3)");
		NAMES.put(3, "Super restore(1)");
		NAMES.put(4, "Vial");
		NAMES.put(5, "Prayer potion (unf)");
		NAMES.put(6, "Snape grass");
		NAMES.put(7, "Shark");
	}

	private static Map<Integer, Long> inv(long... idAndCount)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < idAndCount.length; i += 2)
		{
			m.put((int) idAndCount[i], idAndCount[i + 1]);
		}
		return m;
	}

	@Test
	public void newlyEquippedItemsPop()
	{
		assertEquals(Arrays.asList(30), ActionPops.newlyWorn(Arrays.asList(10, 20), Arrays.asList(10, 30)));
		assertTrue("Taking something off isn't a pop", ActionPops.newlyWorn(Arrays.asList(10, 20), Arrays.asList(10)).isEmpty());
		assertTrue("Nothing on the first look after login", ActionPops.newlyWorn(null, Arrays.asList(10)).isEmpty());
	}

	@Test
	public void aSipShowsTheDoseChange()
	{
		Object[] sip = ActionPops.sip(inv(1, 1), inv(2, 1), NAMES::get);
		assertEquals("Prayer potion (4 → 3)", sip[0]);
		assertEquals(1, sip[1]);
		assertEquals("Super restore (1 → empty)", ActionPops.sip(inv(3, 1), inv(4, 1), NAMES::get)[0]);
		assertNull("Making a potion isn't a sip", ActionPops.sip(inv(5, 1, 6, 1), inv(2, 1), NAMES::get));
		assertNull("Eating isn't a sip", ActionPops.sip(inv(7, 3), inv(7, 2), NAMES::get));
		assertNull(ActionPops.sip(null, inv(2, 1), NAMES::get));
	}

	@Test
	public void foodHealsButRegenerationDoesNot()
	{
		assertEquals("+18 HP", ActionPops.heal(60, 78));
		assertNull("One HP is natural regeneration", ActionPops.heal(60, 61));
		assertNull(ActionPops.heal(60, 50));
	}

	@Test
	public void popsShowBrieflyAndNeverPileUp()
	{
		ActionPops pops = new ActionPops();
		for (int i = 0; i < 5; i++)
		{
			pops.add("+ Item " + i, i, 1000);
		}
		assertEquals(ActionPops.MAX_POPS, pops.showing(1000).size());
		assertEquals("+ Item 4", pops.showing(1000).get(ActionPops.MAX_POPS - 1).text);
		assertTrue(pops.showing(1000 + ActionPops.SHOW_MILLIS).isEmpty());
	}

	@Test
	public void actionPopsAreOffByDefault()
	{
		CombatXpTrackerConfig config = new CombatXpTrackerConfig()
		{
		};
		assertFalse(config.actionPops());
	}
}
