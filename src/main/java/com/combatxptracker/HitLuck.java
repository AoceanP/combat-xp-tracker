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

/**
 * How lucky the player's hits have been this session. A hit that lands rolls evenly from 0
 * to the max hit (OSRS Wiki, "Damage per second/Melee"), so a hit above 0 is even on 1 to
 * max: its average is (max + 1) / 2. The total of the player's hits is compared with that,
 * and turned into a percentile with the normal approximation (fine after a few dozen hits).
 *
 * Synchronized: hits come in on the client thread and the panel reads the result.
 */
final class HitLuck
{
	/**
	 * Fewer hits than this say little either way.
	 */
	static final int MIN_HITS = 20;

	/**
	 * The luck so far.
	 */
	static final class Result
	{
		private final int hits;
		private final double percentile;
		private final double averageShare;
		private final int maxHits;
		private final double expectedMaxHits;

		Result(int hits, double percentile, double averageShare, int maxHits, double expectedMaxHits)
		{
			this.hits = hits;
			this.percentile = percentile;
			this.averageShare = averageShare;
			this.maxHits = maxHits;
			this.expectedMaxHits = expectedMaxHits;
		}

		int getHits()
		{
			return hits;
		}

		/**
		 * @return the share of players who'd have hit lower than this, 0 to 1
		 */
		double getPercentile()
		{
			return percentile;
		}

		/**
		 * @return the average hit as a share of the max hit
		 */
		double getAverageShare()
		{
			return averageShare;
		}

		int getMaxHits()
		{
			return maxHits;
		}

		double getExpectedMaxHits()
		{
			return expectedMaxHits;
		}

		/**
		 * e.g. "Top 18%" or "Bottom 30%".
		 */
		String label()
		{
			if (percentile >= 0.5)
			{
				return "Top " + Math.max(1, Math.round((1 - percentile) * 100)) + "%";
			}
			return "Bottom " + Math.max(1, Math.round(percentile * 100)) + "%";
		}
	}

	private int hits;
	private long damage;
	private double expected;
	private double variance;
	private double share;
	private int maxHits;
	private double expectedMaxHits;

	/**
	 * A hit above 0. Hits over the max are left out: some bonus wasn't known.
	 *
	 * @return whether it counted
	 */
	synchronized boolean record(int hit, int maxHit)
	{
		if (hit <= 0 || maxHit <= 0 || hit > maxHit)
		{
			return false;
		}
		hits++;
		damage += hit;
		expected += (maxHit + 1) / 2.0;
		variance += (maxHit * (double) maxHit - 1) / 12.0;
		share += hit / (double) maxHit;
		if (hit == maxHit)
		{
			maxHits++;
		}
		expectedMaxHits += 1.0 / maxHit;
		return true;
	}

	/**
	 * @return the luck so far, or null with too few hits
	 */
	synchronized Result result()
	{
		if (hits < MIN_HITS)
		{
			return null;
		}
		double z = variance <= 0 ? 0 : (damage - expected) / Math.sqrt(variance);
		return new Result(hits, normalCdf(z), share / hits, maxHits, expectedMaxHits);
	}

	synchronized void reset()
	{
		hits = 0;
		damage = 0;
		expected = 0;
		variance = 0;
		share = 0;
		maxHits = 0;
		expectedMaxHits = 0;
	}

	/**
	 * The standard normal CDF (Abramowitz and Stegun 7.1.26, error under 1.5e-7).
	 */
	static double normalCdf(double z)
	{
		double x = Math.abs(z) / Math.sqrt(2);
		double t = 1 / (1 + 0.3275911 * x);
		double erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
			* t * Math.exp(-x * x);
		return z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
	}
}
