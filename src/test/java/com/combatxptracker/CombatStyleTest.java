package com.combatxptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import net.runelite.api.Skill;
import org.junit.Test;

public class CombatStyleTest
{
	@Test
	public void meleeStyleBonusesMatchTheWiki()
	{
		assertEquals(3, CombatStyle.AttackStyle.fromName("Aggressive").getMeleeStrengthBonus());
		assertEquals(1, CombatStyle.AttackStyle.fromName("Controlled").getMeleeStrengthBonus());
		assertEquals(0, CombatStyle.AttackStyle.fromName("Accurate").getMeleeStrengthBonus());
		assertEquals(0, CombatStyle.AttackStyle.fromName("Defensive").getMeleeStrengthBonus());
	}

	@Test
	public void styleNamesMapToCombatStyles()
	{
		assertEquals(CombatStyle.MELEE, CombatStyle.AttackStyle.fromName("aggressive").getStyle());
		assertEquals(CombatStyle.RANGED, CombatStyle.AttackStyle.fromName("Ranging").getStyle());
		assertEquals(CombatStyle.RANGED, CombatStyle.AttackStyle.fromName("Longrange").getStyle());
		assertEquals(CombatStyle.MAGIC, CombatStyle.AttackStyle.fromName("Casting").getStyle());
		assertEquals(CombatStyle.MAGIC, CombatStyle.AttackStyle.fromName("Defensive casting").getStyle());
		assertNull(CombatStyle.AttackStyle.fromName("Other"));
		assertNull(CombatStyle.AttackStyle.fromName(null));
	}

	@Test
	public void xpDropsImplyStyles()
	{
		assertEquals(CombatStyle.MELEE, CombatStyle.fromXpSkill(Skill.ATTACK));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromXpSkill(Skill.STRENGTH));
		assertEquals(CombatStyle.RANGED, CombatStyle.fromXpSkill(Skill.RANGED));
		assertEquals(CombatStyle.MAGIC, CombatStyle.fromXpSkill(Skill.MAGIC));
		// Every style can train these, so they say nothing.
		assertNull(CombatStyle.fromXpSkill(Skill.DEFENCE));
		assertNull(CombatStyle.fromXpSkill(Skill.HITPOINTS));
		assertNull(CombatStyle.fromXpSkill(Skill.WOODCUTTING));
	}
}
