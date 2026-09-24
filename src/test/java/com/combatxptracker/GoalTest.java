package com.combatxptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.junit.Test;

public class GoalTest
{
	@Test
	public void wholeNumbersUpTo126AreLevels()
	{
		Goal g = Goal.parse("99");
		assertEquals(Goal.Type.LEVEL, g.getType());
		assertEquals(99, g.getTargetLevel());
		assertEquals(13_034_431, g.getTargetXp());

		assertEquals(188_884_740, Goal.parse("126").getTargetXp());
		assertEquals(90, Goal.parse("lvl 90").getTargetLevel());
		assertEquals(2, Goal.parse("level 2").getTargetLevel());
	}

	@Test
	public void largerNumbersAreXp()
	{
		Goal g = Goal.parse("13,034,431");
		assertEquals(Goal.Type.XP, g.getType());
		assertEquals(13_034_431, g.getTargetXp());
		assertEquals(99, g.getTargetLevel());

		assertEquals(500, Goal.parse("500").getTargetXp());
	}

	@Test
	public void suffixesAreXp()
	{
		assertEquals(200_000_000, Goal.parse("200m").getTargetXp());
		assertEquals(200_000_000, Goal.parse("200M xp").getTargetXp());
		assertEquals(13_030_000, Goal.parse("13.03m").getTargetXp());
		assertEquals(500_000, Goal.parse("500k").getTargetXp());
		assertEquals(Goal.Type.XP, Goal.parse("50k").getType());
		// 200M is past level 126's XP, so it's shown as level 126.
		assertEquals(126, Goal.parse("200m").getTargetLevel());
	}

	@Test
	public void rejectsInvalidInput()
	{
		for (String bad : new String[]{"", "   ", "abc", "1", "0", "-5", "99.5", "201m", "1b", "nan", "12x"})
		{
			try
			{
				Goal.parse(bad);
				fail("Expected '" + bad + "' to be rejected");
			}
			catch (IllegalArgumentException expected)
			{
				// ok
			}
		}
	}

	@Test
	public void serializeRoundTrips()
	{
		for (String input : new String[]{"99", "126", "2", "200m", "13034431", "500k"})
		{
			Goal g = Goal.parse(input);
			assertEquals(g, Goal.deserialize(g.serialize()));
		}
	}

	@Test
	public void levelGoalsKeepTheOldSavedFormat()
	{
		// 1.3.x saved a bare level number. It must still load after updating.
		assertEquals("99", Goal.ofLevel(99).serialize());
		assertEquals(Goal.ofLevel(80), Goal.deserialize("80"));
	}

	@Test
	public void corruptSavedValuesAreIgnored()
	{
		assertNull(Goal.deserialize(null));
		assertNull(Goal.deserialize(""));
		assertNull(Goal.deserialize("banana"));
		assertNull(Goal.deserialize("xp:notanumber"));
		assertNull(Goal.deserialize("xp:999999999"));
	}

	@Test
	public void shortLabels()
	{
		assertEquals("99", Goal.parse("99").getShortLabel());
		assertEquals("200M", Goal.parse("200m").getShortLabel());
		assertEquals("13.03M", Goal.parse("13.03m").getShortLabel());
	}
}
