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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Drag a card up or down its list to reorder it. The list draws a line where the card will
 * land. Used on the Swing thread only.
 */
final class DragReorder
{
	/**
	 * How far the mouse must move before a press becomes a drag, so clicks still work.
	 */
	private static final int DRAG_THRESHOLD = 6;

	interface Mover
	{
		/**
		 * Moves the card at {@code from} to {@code to} (both list indices).
		 */
		void move(int from, int to);
	}

	/**
	 * A list of cards that shows where a dragged card will be dropped.
	 */
	static final class ListPanel extends JPanel
	{
		private int dropY = -1;
		private Color lineColour = Theme.TEXT;

		ListPanel(LayoutManager layout)
		{
			super(layout);
		}

		void setLineColour(Color colour)
		{
			lineColour = colour;
		}

		void setDropLine(int y)
		{
			dropY = y;
			repaint();
		}

		@Override
		protected void paintChildren(Graphics g)
		{
			super.paintChildren(g);
			if (dropY >= 0)
			{
				g.setColor(lineColour);
				g.fillRect(0, dropY - 1, getWidth(), 3);
			}
		}
	}

	private DragReorder()
	{
	}

	/**
	 * Makes a card draggable by pressing on any of {@code handles}.
	 *
	 * @param enabled whether the card can be dragged right now (e.g. only pinned monsters)
	 * @param limit   how many cards from the top of the list can be drop targets
	 */
	static void attach(Component card, ListPanel list, BooleanSupplier enabled, IntSupplier limit, Mover mover,
		JComponent... handles)
	{
		MouseAdapter drag = new MouseAdapter()
		{
			private Point start;
			private boolean dragging;

			@Override
			public void mousePressed(MouseEvent e)
			{
				start = SwingUtilities.isLeftMouseButton(e) && enabled.getAsBoolean() ? e.getLocationOnScreen() : null;
				dragging = false;
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (start == null)
				{
					return;
				}
				if (!dragging && start.distance(e.getLocationOnScreen()) < DRAG_THRESHOLD)
				{
					return;
				}
				dragging = true;
				list.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				list.setDropLine(lineY(list, slotAt(list, e, limit.getAsInt())));
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (dragging)
				{
					int from = indexOf(list, card);
					int slot = slotAt(list, e, limit.getAsInt());
					int to = slot > from ? slot - 1 : slot;
					if (from >= 0 && to != from)
					{
						mover.move(from, to);
					}
				}
				start = null;
				dragging = false;
				list.setDropLine(-1);
				list.setCursor(Cursor.getDefaultCursor());
			}
		};
		for (JComponent handle : handles)
		{
			handle.addMouseListener(drag);
			handle.addMouseMotionListener(drag);
		}
	}

	private static int indexOf(JPanel list, Component card)
	{
		for (int i = 0; i < list.getComponentCount(); i++)
		{
			if (list.getComponent(i) == card)
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The gap the mouse is nearest: 0 is above the first card, n below the last.
	 */
	private static int slotAt(JPanel list, MouseEvent e, int limit)
	{
		Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), list);
		int n = Math.min(limit, list.getComponentCount());
		for (int i = 0; i < n; i++)
		{
			Component c = list.getComponent(i);
			if (p.y < c.getY() + c.getHeight() / 2)
			{
				return i;
			}
		}
		return n;
	}

	/**
	 * Where to draw the drop line for a slot. Cards have a 6px gap below them, so the line
	 * sits in the gap above the card at that slot.
	 */
	private static int lineY(JPanel list, int slot)
	{
		int n = list.getComponentCount();
		if (n == 0)
		{
			return -1;
		}
		if (slot <= 0)
		{
			return 1;
		}
		if (slot < n)
		{
			return list.getComponent(slot).getY() - 3;
		}
		Component last = list.getComponent(n - 1);
		return last.getY() + last.getHeight() - 3;
	}
}
