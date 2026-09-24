package com.combatxptracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlayerTaskMatcherTest
{
	@Test
	public void pluralTaskMatchesSingularNpc()
	{
		assertTrue(SlayerTaskMatcher.matches("Abyssal demons", "Abyssal demon"));
		assertTrue(SlayerTaskMatcher.matches("Black dragons", "Black dragon"));
		assertTrue(SlayerTaskMatcher.matches("Wolves", "Wolf"));
		assertTrue(SlayerTaskMatcher.matches("Harpie bug swarms", "Harpie bug swarm"));
		assertTrue(SlayerTaskMatcher.matches("Hellhounds", "Hellhound"));
	}

	@Test
	public void variantNamesStillMatch()
	{
		assertTrue(SlayerTaskMatcher.matches("Abyssal demons", "Greater abyssal demon"));
		assertTrue(SlayerTaskMatcher.matches("Greater demons", "Greater demon"));
		assertTrue(SlayerTaskMatcher.matches("Kalphite", "Kalphite Soldier"));
		assertTrue(SlayerTaskMatcher.matches("Zulrah", "Zulrah"));
	}

	@Test
	public void ignoresCaseTagsAndNonBreakingSpaces()
	{
		assertTrue(SlayerTaskMatcher.matches("ABYSSAL DEMONS", "<col=ffff00>Abyssal demon</col>"));
	}

	@Test
	public void differentMonstersDontMatch()
	{
		assertFalse(SlayerTaskMatcher.matches("Black demons", "Greater demon"));
		assertFalse(SlayerTaskMatcher.matches("Black dragons", "Blue dragon"));
		// Whole words only: "Rat" is not part of "Pirate".
		assertFalse(SlayerTaskMatcher.matches("Rats", "Pirate"));
	}

	@Test
	public void noTaskOrTargetNeverMatches()
	{
		assertFalse(SlayerTaskMatcher.matches(null, "Abyssal demon"));
		assertFalse(SlayerTaskMatcher.matches("Abyssal demons", null));
		assertFalse(SlayerTaskMatcher.matches("  ", "Abyssal demon"));
	}
}
