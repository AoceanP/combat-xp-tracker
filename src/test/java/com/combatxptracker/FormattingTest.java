package com.combatxptracker;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormattingTest
{
	@Test
	public void compactXp()
	{
		assertEquals("950", Formatting.compactXp(950));
		assertEquals("9,999", Formatting.compactXp(9_999));
		assertEquals("12.3K", Formatting.compactXp(12_345));
		assertEquals("500K", Formatting.compactXp(500_000));
		assertEquals("13.03M", Formatting.compactXp(13_034_431));
		assertEquals("1.5M", Formatting.compactXp(1_500_000));
		assertEquals("200M", Formatting.compactXp(200_000_000));
	}

	@Test
	public void duration()
	{
		assertEquals("-", Formatting.duration(-1));
		assertEquals("<1m", Formatting.duration(0.001));
		assertEquals("45m", Formatting.duration(0.75));
		assertEquals("4h 12m", Formatting.duration(4.2));
		assertEquals("3d 7h", Formatting.duration(79));
	}
}
