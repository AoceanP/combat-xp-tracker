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
import net.runelite.api.gameval.NpcID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Looting your own gravestone must never count as monster loot, and stray items can be
 * removed from one monster's loot.
 */
public class GravestoneLootTest
{
	@Test
	public void everyGravestoneStyleIsAGravestone()
	{
		assertTrue(CombatXpTrackerPlugin.isGravestone(NpcID.GRAVESTONE_DEFAULT, "Grave"));
		assertTrue(CombatXpTrackerPlugin.isGravestone(NpcID.GRAVESTONE_ANGEL, null));
		assertTrue(CombatXpTrackerPlugin.isGravestone(NpcID.GRAVESTONE_ANGEL_255, null));
		assertTrue(CombatXpTrackerPlugin.isGravestone(-1, "Gravestone"));
	}

	@Test
	public void bossesAreNotGravestones()
	{
		assertFalse(CombatXpTrackerPlugin.isGravestone(NpcID.GRAVESTONE_DEFAULT - 1, "Branda the Fire Queen"));
		assertFalse(CombatXpTrackerPlugin.isGravestone(NpcID.GRAVESTONE_ANGEL_255 + 1, "Eldric the Ice King"));
	}

	@Test
	public void lootOnlyCountsRightAfterAKill()
	{
		assertTrue(CombatXpTrackerPlugin.lootFollowsKill(100, 105));
		assertTrue(CombatXpTrackerPlugin.lootFollowsKill(100, 100 + CombatXpTrackerPlugin.LOOT_AFTER_KILL_TICKS));
		assertFalse(CombatXpTrackerPlugin.lootFollowsKill(100, 101 + CombatXpTrackerPlugin.LOOT_AFTER_KILL_TICKS));
		// Never killed this login, or a tick from before a relog.
		assertFalse(CombatXpTrackerPlugin.lootFollowsKill(null, 50));
		assertFalse(CombatXpTrackerPlugin.lootFollowsKill(500, 20));
	}

	@Test
	public void removesOneItemFromOneMonster()
	{
		MonsterTracker t = new MonsterTracker();
		t.recordKill("Royal Titans", 1000);
		t.recordLoot("Royal Titans", java.util.Arrays.asList(
			new MonsterTracker.Drop(560, "Death rune", 100, 160, 108),
			new MonsterTracker.Drop(28988, "Blue Moon spear", 1, 280_000, 60_000)), 1000);
		t.recordLoot("Cave horror", Collections.singletonList(new MonsterTracker.Drop(28988, "Blue Moon spear", 1, 280_000, 60_000)), 1000);

		assertTrue(t.removeLoot("Royal Titans", 28988));
		assertFalse(t.removeLoot("Royal Titans", 28988));
		assertEquals(1, t.snapshotOf("Royal Titans").getLoot().size());
		assertEquals(100 * 160, t.snapshotOf("Royal Titans").getLootValue());
		// Other monsters keep theirs.
		assertEquals(1, t.snapshotOf("Cave horror").getLoot().size());
	}
}
