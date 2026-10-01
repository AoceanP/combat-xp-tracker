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

/**
 * The sidebar's colour themes. Dark is the original look and the default, so nothing
 * changes until a player picks another theme.
 */
public enum PanelTheme
{
	DARK("Dark", 0x282828, 0x1E1E1E, 0x3C3C3C, 0x181818, 0x111111, 0x343434,
		0xFFFFFF, 0xA8A8A8, 0x767676, 0x4FC3F7, 0xFFC640, 0x60DC8C),
	CLASSIC("Classic", 0x3E3529, 0x4F4434, 0x5A4E3C, 0x2F281E, 0x1F1A13, 0x6B5B43,
		0xFF981F, 0xD8C9A5, 0xA8977A, 0xFFFF00, 0xFFFF00, 0x00FF00),
	MIDNIGHT("Midnight", 0x000000, 0x0B0B0B, 0x161616, 0x141414, 0x1A1A1A, 0x262626,
		0xEDEDED, 0x9A9A9A, 0x666666, 0xB388FF, 0xFFD166, 0x69F0AE),
	ABYSSAL("Abyssal", 0x1A0F1F, 0x251530, 0x301C3D, 0x140B18, 0x0D0710, 0x3A2447,
		0xF3E8FF, 0xC4A8D6, 0x8B6F9E, 0xD946EF, 0xFBBF24, 0x4ADE80),
	ZAMORAK("Zamorak", 0x160A0A, 0x220F0F, 0x2E1414, 0x100606, 0x0A0404, 0x3A1A1A,
		0xFFE5E5, 0xD4A3A3, 0x946666, 0xEF4444, 0xF59E0B, 0x84CC16),
	SARADOMIN("Saradomin", 0x0F1A2E, 0x16233D, 0x1E2E4D, 0x0B1324, 0x070D19, 0x263859,
		0xF8FAFC, 0xA9B8D4, 0x71819F, 0xFACC15, 0xFDE68A, 0x5EEAD4),
	GUTHIX("Guthix", 0x16201A, 0x1E2B22, 0x27382C, 0x111913, 0x0A110C, 0x2F4335,
		0xECFDF5, 0xA7C4B0, 0x6F8C78, 0x4ADE80, 0xEAB308, 0x86EFAC),
	ARMADYL("Armadyl", 0xE8EEF4, 0xFFFFFF, 0xF1F5F9, 0xDBE4EE, 0xC8D3DF, 0xB8C4D2,
		0x16202C, 0x4B5B6E, 0x7A8899, 0x0E7490, 0xA16207, 0x15803D),
	BANDOS("Bandos", 0x22201A, 0x2D2A20, 0x383428, 0x1A1813, 0x12100B, 0x45402F,
		0xF5F0DC, 0xBDB48F, 0x857D5C, 0xC2783A, 0xE9C46A, 0xA3C95A),
	DRAGONFIRE("Dragonfire", 0x1C1917, 0x262220, 0x302B28, 0x141210, 0x0C0A09, 0x3B3430,
		0xFFF7ED, 0xD6BFAE, 0x8F7A6B, 0xFB923C, 0xFCD34D, 0xA3E635),
	FROSTBITE("Frostbite", 0x111A22, 0x17232E, 0x1E2D3B, 0x0C141B, 0x071016, 0x26394A,
		0xEAF6FF, 0xA6C1D6, 0x6B879C, 0x7DD3FC, 0xFDE047, 0x6EE7B7),
	VENOM("Venom", 0x0C1714, 0x11211D, 0x172B26, 0x08110E, 0x050C0A, 0x1E3832,
		0xE6FFF6, 0x9CC9B8, 0x64907F, 0xA3E635, 0xFACC15, 0x2DD4BF);

	private final String displayName;
	final Color background;
	final Color card;
	final Color cardHover;
	final Color header;
	final Color track;
	final Color border;
	final Color text;
	final Color muted;
	final Color subtle;
	final Color accent;
	final Color gold;
	final Color success;

	PanelTheme(String displayName, int background, int card, int cardHover, int header, int track, int border,
		int text, int muted, int subtle, int accent, int gold, int success)
	{
		this.displayName = displayName;
		this.background = new Color(background);
		this.card = new Color(card);
		this.cardHover = new Color(cardHover);
		this.header = new Color(header);
		this.track = new Color(track);
		this.border = new Color(border);
		this.text = new Color(text);
		this.muted = new Color(muted);
		this.subtle = new Color(subtle);
		this.accent = new Color(accent);
		this.gold = new Color(gold);
		this.success = new Color(success);
	}

	/**
	 * @return whether this is a light theme (dark text on light cards)
	 */
	boolean isLight()
	{
		return background.getRed() + background.getGreen() + background.getBlue() > 3 * 128;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
