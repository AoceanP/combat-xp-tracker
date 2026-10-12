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

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * Casts of the autocast spell the player's runes allow, gold when running low.
 */
class CastsInfoBox extends InfoBox
{
	private static final Color LOW = new Color(255, 198, 64);

	private volatile long casts;
	private volatile boolean low;
	private volatile String tooltip;

	CastsInfoBox(BufferedImage image, CombatXpTrackerPlugin plugin)
	{
		super(image, plugin);
		setPriority(InfoBoxPriority.MED);
	}

	void update(long casts, boolean low, String tooltip)
	{
		this.casts = casts;
		this.low = low;
		this.tooltip = tooltip;
	}

	@Override
	public String getText()
	{
		return casts >= 10_000 ? casts / 1000 + "k" : String.valueOf(casts);
	}

	@Override
	public Color getTextColor()
	{
		return low ? LOW : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		return tooltip;
	}
}
