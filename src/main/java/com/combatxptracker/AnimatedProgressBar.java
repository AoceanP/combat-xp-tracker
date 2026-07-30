/*
 * Copyright (c) 2026, YourNameHere <https://github.com/YourNameHere>
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

import javax.swing.JProgressBar;
import javax.swing.Timer;

/**
 * A progress bar that eases toward its target value rather than snapping to it.
 *
 * Without this, a bar that jumps straight from 41% to 42% is easy to miss entirely,
 * and rapid XP gains make the whole panel look like it's twitching. Easing the fill
 * over a couple of hundred milliseconds makes progress feel continuous and readable.
 */
public class AnimatedProgressBar extends JProgressBar
{
	private static final int FRAME_DELAY_MS = 16;   // ~60fps
	private static final double EASING = 0.18;      // fraction of remaining distance per frame
	private static final double SNAP_THRESHOLD = 0.5;

	private double displayedValue = 0;
	private int targetValue = 0;
	private final Timer animationTimer;

	public AnimatedProgressBar()
	{
		super(0, 100);

		animationTimer = new Timer(FRAME_DELAY_MS, e ->
		{
			double distance = targetValue - displayedValue;

			if (Math.abs(distance) < SNAP_THRESHOLD)
			{
				displayedValue = targetValue;
				super.setValue(targetValue);
				animationTimer.stop();
				return;
			}

			displayedValue += distance * EASING;
			super.setValue((int) Math.round(displayedValue));
		});
		animationTimer.setRepeats(true);
	}

	/**
	 * Sets the value the bar should ease toward. Safe to call repeatedly with the same
	 * value -- the animation only restarts when the target actually changes.
	 */
	public void setAnimatedValue(int newTarget)
	{
		int clamped = Math.max(0, Math.min(100, newTarget));
		if (clamped == targetValue)
		{
			return;
		}
		targetValue = clamped;
		if (!animationTimer.isRunning())
		{
			animationTimer.start();
		}
	}

	/**
	 * Jumps straight to a value with no animation. Used on first display and after a
	 * reset, where easing up from zero would be misleading rather than pleasant.
	 */
	public void setValueImmediate(int value)
	{
		int clamped = Math.max(0, Math.min(100, value));
		animationTimer.stop();
		targetValue = clamped;
		displayedValue = clamped;
		super.setValue(clamped);
	}

	/**
	 * Stops the animation timer. Must be called when the bar is discarded, otherwise the
	 * Swing timer keeps a reference to it and it never gets garbage collected.
	 */
	public void dispose()
	{
		animationTimer.stop();
	}
}
