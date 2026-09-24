package com.combatxptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Experience;
import org.junit.Test;

public class SkillProgressTest
{
	private static final int WINDOW_SECONDS = 300;

	@Test
	public void xpPerHourFromTwoSamples()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(1_000_000, 0);
		p.recordXp(1_001_000, 60_000, WINDOW_SECONDS);
		// 1,000 xp in one minute = 60,000 xp/hr
		assertEquals(60_000, p.getXpPerHour());
	}

	@Test
	public void noRateWithoutTwoSamples()
	{
		SkillProgress p = new SkillProgress();
		assertEquals(0, p.getXpPerHour());
		p.resetBaseline(500, 0);
		assertEquals(0, p.getXpPerHour());
		assertEquals(-1, p.getEstimatedHoursToGoal(), 0.0);
	}

	@Test
	public void oldSamplesFallOutOfTheWindow()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(0, 0);
		p.recordXp(10_000, 10_000, 30);
		// A burst of 10k at the start; 60s later only this window's gains count.
		p.recordXp(10_100, 60_000, 30);
		p.recordXp(10_200, 70_000, 30);
		// Window is now [60s..70s]: 100 xp in 10 seconds = 36,000 xp/hr
		assertEquals(36_000, p.getXpPerHour());
	}

	/**
	 * Regression test for the 1.3.x bug: progress was measured from the start of the
	 * current level, so the bar dropped to ~0% on every level-up.
	 */
	@Test
	public void progressNeverGoesBackwardsAcrossLevelUps()
	{
		SkillProgress p = new SkillProgress();
		int start = Experience.getXpForLevel(70) + 1_000;
		p.resetBaseline(start, 0);
		p.setGoal(Goal.ofLevel(99), start);

		double previous = p.getProgressToGoal();
		assertEquals(0.0, previous, 1e-9);

		long t = 0;
		for (int xp = start; xp <= Experience.getXpForLevel(80); xp += 25_000)
		{
			t += 1_000;
			p.recordXp(xp, t, WINDOW_SECONDS);
			double now = p.getProgressToGoal();
			assertTrue("progress went backwards at " + xp + " xp", now >= previous);
			previous = now;
		}
		assertTrue(previous > 0.0);
	}

	@Test
	public void progressIsMeasuredFromTheGoalStart()
	{
		int target = Experience.getXpForLevel(99);
		// Halfway between the start and the goal.
		int start = 1_000_000;
		int halfway = start + (target - start) / 2;
		assertEquals(0.5, SkillProgress.progress(halfway, start, target), 1e-6);
		assertEquals(0.0, SkillProgress.progress(start, start, target), 1e-9);
		assertEquals(1.0, SkillProgress.progress(target, start, target), 1e-9);
		assertEquals(1.0, SkillProgress.progress(target + 5, start, target), 1e-9);
	}

	@Test
	public void goalsPast99WorkUpTo200m()
	{
		SkillProgress p = new SkillProgress();
		int xp = 100_000_000;
		p.resetBaseline(xp, 0);
		p.setGoal(Goal.ofXp(200_000_000), xp);
		assertEquals(0.0, p.getProgressToGoal(), 1e-9);
		assertEquals(100_000_000, p.getXpRemainingToGoal());
		// Virtual level, not capped at 99.
		assertEquals(Experience.getLevelForXp(xp), p.getCurrentLevel());
		assertTrue(p.getCurrentLevel() > 99);

		p.recordXp(150_000_000, 3_600_000, 7200);
		assertEquals(0.5, p.getProgressToGoal(), 1e-9);
		// 50M in an hour -> 1 more hour to go
		assertEquals(1.0, p.getEstimatedHoursToGoal(), 1e-9);
	}

	@Test
	public void goalReached()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(Experience.getXpForLevel(98), 0);
		p.setGoal(Goal.ofLevel(99), Experience.getXpForLevel(98));
		assertFalse(p.isGoalReached());
		p.recordXp(Experience.getXpForLevel(99), 1_000, WINDOW_SECONDS);
		assertTrue(p.isGoalReached());
		assertEquals(1.0, p.getProgressToGoal(), 1e-9);
		assertEquals(0, p.getXpRemainingToGoal());
	}

	@Test
	public void unknownStartFallsBackToCurrentLevel()
	{
		// Goals saved by 1.3.x have no start XP until the plugin fills one in.
		int levelStart = Experience.getXpForLevel(50);
		int target = Experience.getXpForLevel(51);
		assertEquals(0.0, SkillProgress.progress(levelStart, -1, target), 1e-9);
	}

	@Test
	public void sessionGainCountsFromFirstBaseline()
	{
		SkillProgress p = new SkillProgress();
		p.resetBaseline(1_000, 0);
		p.recordXp(1_500, 1_000, WINDOW_SECONDS);
		// A relog re-baselines but must not restart the session count.
		p.reset();
		p.resetBaseline(1_500, 5_000);
		p.recordXp(1_800, 6_000, WINDOW_SECONDS);
		assertEquals(800, p.getSessionXpGained());

		p.resetSession();
		assertEquals(0, p.getSessionXpGained());
	}
}
