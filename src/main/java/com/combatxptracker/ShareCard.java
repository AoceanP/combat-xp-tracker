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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.runelite.client.ui.FontManager;

/**
 * WHAT: Draws a monster's share card: a picture made for posting, with the boss's name and kill
 * count large, its PB, rates, loot, drop luck and best drops, and the plugin's name at the bottom.
 * WHY: "Copy as image" copies the panel card as it is, which is narrow and busy; this is laid out
 * to read well in Discord or on Reddit, and every post shows where it came from.
 * PRE: Swing thread. All numbers and icons are passed in; nothing here reads the game.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class ShareCard
{
	/** The card is laid out at this width and drawn twice as large, so the pixel fonts stay sharp. */
	static final int WIDTH = 360;
	static final int SCALE = 2;
	static final int MAX_DROPS = 6;
	private static final int PAD = 14;
	private static final Color BAD = new Color(232, 93, 74);

	private ShareCard()
	{
	}

	/**
	 * WHAT: Everything one card shows, worked out before drawing.
	 * WHY: Keeps drawing apart from where the numbers come from, so it can be tested.
	 * PRE: Unknown numbers are -1 (or NaN for luck); drops are best first.
	 * POST: Not applicable.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class Data
	{
		String name;
		int kills;
		int killsToday;
		double pbSeconds = -1;
		double killsPerHour = -1;
		long loot;
		double gpPerHour = -1;
		double luckPercent = Double.NaN;
		int killsSinceRare = -1;
		final List<Drop> drops = new ArrayList<>();
		String date;
	}

	/**
	 * WHAT: One drop on the card: its icon, how many, and whether it's a rare drop.
	 * WHY: The best drops row shows the items people care about.
	 * PRE: icon may be null if it hasn't loaded.
	 * POST: Not applicable.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class Drop
	{
		final Image icon;
		final long quantity;
		final boolean rare;

		Drop(Image icon, long quantity, boolean rare)
		{
			this.icon = icon;
			this.quantity = quantity;
			this.rare = rare;
		}
	}

	/**
	 * WHAT: The stats shown in the tile row, leaving out any that aren't known.
	 * WHY: A monster with no PB or rates yet still gets a tidy row.
	 * PRE: d is filled in.
	 * POST: Nothing changes.
	 * RETURN: pairs of value and caption
	 * THROWS: Nothing.
	 */
	static List<String[]> tiles(Data d)
	{
		List<String[]> tiles = new ArrayList<>();
		if (d.pbSeconds > 0)
		{
			tiles.add(new String[]{BossTimes.format(d.pbSeconds), "Personal best"});
		}
		if (d.killsPerHour > 0)
		{
			tiles.add(new String[]{String.format(Locale.US, "%.1f", d.killsPerHour), "Kills/hr"});
		}
		if (d.loot > 0)
		{
			tiles.add(new String[]{Formatting.compactXp(d.loot), "Loot"});
		}
		if (d.gpPerHour > 0)
		{
			tiles.add(new String[]{Formatting.compactXp(Math.round(d.gpPerHour)), "GP/hr"});
		}
		return tiles;
	}

	/**
	 * WHAT: "+18% vs average" or "-6% vs average".
	 * WHY: The luck line's wording, shared with tests.
	 * PRE: percent is a number.
	 * POST: Nothing changes.
	 * RETURN: the text
	 * THROWS: Nothing.
	 */
	static String luckText(double percent)
	{
		long rounded = Math.round(percent);
		return (rounded > 0 ? "+" : "") + rounded + "% vs average";
	}

	/**
	 * WHAT: Draws the card.
	 * WHY: Called when the player picks "Copy share card" on a monster card.
	 * PRE: Swing thread; d is filled in.
	 * POST: Nothing changes.
	 * RETURN: the picture, WIDTH x SCALE pixels wide
	 * THROWS: Nothing.
	 */
	static BufferedImage render(Data d)
	{
		Font title = FontManager.getRunescapeBoldFont().deriveFont(32f);
		Font bold = FontManager.getRunescapeBoldFont();
		Font plain = FontManager.getRunescapeFont();
		Font small = FontManager.getRunescapeSmallFont();
		List<String[]> tiles = tiles(d);
		boolean luck = !Double.isNaN(d.luckPercent);
		boolean drops = !d.drops.isEmpty();

		int height = PAD + 34 + 16;
		height += tiles.isEmpty() ? 0 : 40 + 10;
		height += luck || d.killsSinceRare >= 0 ? (luck ? 38 : 22) + 10 : 0;
		height += drops ? 16 + 36 + 10 : 0;
		height += 22 + PAD - 10;

		BufferedImage img = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.scale(SCALE, SCALE);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		g.setColor(Theme.CARD);
		g.fill(new RoundRectangle2D.Float(0, 0, WIDTH, height, 18, 18));
		g.setColor(Theme.highlight(Theme.GOLD, 0.6f));
		g.setStroke(new BasicStroke(1.5f));
		g.draw(new RoundRectangle2D.Float(0.75f, 0.75f, WIDTH - 1.5f, height - 1.5f, 18, 18));

		// Name on the left, kill count on the right.
		int y = PAD;
		String killText = Formatting.withCommas(d.kills);
		g.setFont(title);
		int killWidth = g.getFontMetrics().stringWidth(killText);
		Font nameFont = g.getFontMetrics().stringWidth(d.name) + killWidth + 70 > WIDTH - PAD * 2 ? bold.deriveFont(24f) : title;
		g.setFont(nameFont);
		g.setColor(Theme.GOLD);
		g.drawString(d.name, PAD, y + 26);
		g.setFont(title);
		g.setColor(Theme.TEXT);
		g.drawString(killText, WIDTH - PAD - killWidth, y + 26);
		g.setFont(small);
		g.setColor(Theme.MUTED);
		String killsWord = d.kills == 1 ? "kill" : "kills";
		g.drawString(killsWord, WIDTH - PAD - g.getFontMetrics().stringWidth(killsWord), y + 40);
		if (d.killsToday > 0)
		{
			g.setColor(Theme.SUCCESS);
			g.drawString("+" + Formatting.withCommas(d.killsToday) + " this session", PAD, y + 40);
		}
		y += 34 + 16;

		// Tiles: PB, kills/hr, loot, GP/hr.
		if (!tiles.isEmpty())
		{
			int gap = 6;
			float w = (WIDTH - PAD * 2 - gap * (tiles.size() - 1)) / (float) tiles.size();
			for (int i = 0; i < tiles.size(); i++)
			{
				float x = PAD + i * (w + gap);
				g.setColor(Theme.SLOT);
				g.fill(new RoundRectangle2D.Float(x, y, w, 40, 10, 10));
				String[] t = tiles.get(i);
				g.setFont(bold);
				g.setColor(t[1].equals("Loot") || t[1].equals("GP/hr") ? Theme.GOLD : Theme.TEXT);
				centre(g, t[0], x, w, y + 18);
				g.setFont(small);
				g.setColor(Theme.MUTED);
				centre(g, t[1], x, w, y + 33);
			}
			y += 40 + 10;
		}

		// Drop luck, with a bar that grows left (unlucky) or right (lucky) from the middle.
		if (luck || d.killsSinceRare >= 0)
		{
			int h = luck ? 38 : 22;
			g.setColor(Theme.SLOT);
			g.fill(new RoundRectangle2D.Float(PAD, y, WIDTH - PAD * 2, h, 10, 10));
			int line = y + 15;
			if (luck)
			{
				Color c = d.luckPercent >= 0 ? Theme.SUCCESS : BAD;
				g.setFont(plain);
				g.setColor(Theme.MUTED);
				g.drawString("Drop luck", PAD + 8, line);
				String text = luckText(d.luckPercent);
				g.setFont(bold);
				g.setColor(c);
				g.drawString(text, WIDTH - PAD - 8 - g.getFontMetrics().stringWidth(text), line);
				int barX = PAD + 8;
				int barW = WIDTH - PAD * 2 - 16;
				int mid = barX + barW / 2;
				g.setColor(Theme.TRACK);
				g.fill(new RoundRectangle2D.Float(barX, y + 24, barW, 5, 5, 5));
				int fill = (int) Math.round(Math.min(1, Math.abs(d.luckPercent) / 100.0) * barW / 2);
				g.setColor(c);
				g.fill(new RoundRectangle2D.Float(d.luckPercent >= 0 ? mid : mid - fill, y + 24, Math.max(3, fill), 5, 5, 5));
				g.setColor(Theme.MUTED);
				g.fillRect(mid, y + 22, 1, 9);
			}
			else
			{
				g.setFont(plain);
				g.setColor(Theme.MUTED);
				g.drawString("Rare drops", PAD + 8, line);
			}
			if (d.killsSinceRare >= 0 && !luck)
			{
				String dry = d.killsSinceRare == 0 ? "on the last kill" : Formatting.withCommas(d.killsSinceRare) + " kills dry";
				g.setFont(bold);
				g.setColor(Theme.TEXT);
				g.drawString(dry, WIDTH - PAD - 8 - g.getFontMetrics().stringWidth(dry), line);
			}
			y += h + 10;
		}

		// Best drops.
		if (drops)
		{
			g.setFont(small);
			g.setColor(Theme.MUTED);
			g.drawString("Best drops", PAD, y + 10);
			if (d.killsSinceRare > 0 && luck)
			{
				String dry = Formatting.withCommas(d.killsSinceRare) + " kills since a rare drop";
				g.drawString(dry, WIDTH - PAD - g.getFontMetrics().stringWidth(dry), y + 10);
			}
			y += 16;
			int slotW = 44;
			for (int i = 0; i < d.drops.size() && i < MAX_DROPS; i++)
			{
				Drop drop = d.drops.get(i);
				int x = PAD + i * (slotW + 6);
				g.setColor(Theme.SLOT);
				g.fill(new RoundRectangle2D.Float(x, y, slotW, 36, 10, 10));
				if (drop.rare)
				{
					g.setColor(Theme.GOLD);
					g.setStroke(new BasicStroke(1.2f));
					g.draw(new RoundRectangle2D.Float(x + 0.6f, y + 0.6f, slotW - 1.2f, 34.8f, 10, 10));
				}
				if (drop.icon != null)
				{
					int iw = drop.icon.getWidth(null);
					int ih = drop.icon.getHeight(null);
					g.drawImage(drop.icon, x + (slotW - iw) / 2, y + 36 - ih - 1, null);
				}
				if (drop.quantity > 1)
				{
					String q = drop.quantity >= 10_000_000 ? drop.quantity / 1_000_000 + "M"
						: drop.quantity >= 100_000 ? drop.quantity / 1000 + "K" : String.valueOf(drop.quantity);
					g.setFont(small);
					g.setColor(Color.BLACK);
					g.drawString(q, x + 4, y + 11);
					g.setColor(drop.quantity >= 100_000 ? Color.WHITE : new Color(255, 255, 0));
					g.drawString(q, x + 3, y + 10);
				}
			}
			y += 36 + 10;
		}

		// Footer: where it came from, and when.
		g.setColor(UiKit.line());
		g.fillRect(PAD, y, WIDTH - PAD * 2, 1);
		g.setFont(small);
		g.setColor(Theme.ACCENT);
		g.drawString("Combat & XP Tracker", PAD, y + 15);
		if (d.date != null)
		{
			g.setColor(Theme.SUBTLE);
			g.drawString(d.date, WIDTH - PAD - g.getFontMetrics().stringWidth(d.date), y + 15);
		}
		g.dispose();
		return img;
	}

	private static void centre(Graphics2D g, String text, float x, float w, int baseline)
	{
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, x + (w - fm.stringWidth(text)) / 2f, baseline);
	}
}
