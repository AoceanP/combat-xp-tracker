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

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether an NPC is plausibly part of the current slayer task, by name.
 *
 * Task names are plural ("Abyssal demons", "Wolves", "Black dragons") and NPC names are
 * singular ("Abyssal demon", "Wolf", "Black dragon"), so the task is reduced to its
 * singular forms and matched as whole words inside the NPC name.
 *
 * This is best effort. A few tasks cover monsters whose names don't contain the task
 * name at all, and those won't match. The panel always shows the on-task max hit
 * separately, so a missed match only means that line isn't highlighted.
 */
public final class SlayerTaskMatcher
{
	private SlayerTaskMatcher()
	{
	}

	public static boolean matches(String taskName, String npcName)
	{
		if (taskName == null || npcName == null || taskName.trim().isEmpty())
		{
			return false;
		}

		String npc = normalize(npcName);
		for (String candidate : singularForms(normalize(taskName)))
		{
			if (candidate.isEmpty())
			{
				continue;
			}
			Pattern word = Pattern.compile("(^|\\s)" + Pattern.quote(candidate) + "($|\\s)");
			if (word.matcher(npc).find())
			{
				return true;
			}
		}
		return false;
	}

	static Set<String> singularForms(String plural)
	{
		Set<String> forms = new LinkedHashSet<>();
		forms.add(plural);
		if (plural.endsWith("ies"))
		{
			forms.add(plural.substring(0, plural.length() - 3) + "y");
		}
		if (plural.endsWith("ves"))
		{
			String stem = plural.substring(0, plural.length() - 3);
			forms.add(stem + "f");
			forms.add(stem + "fe");
		}
		if (plural.endsWith("es"))
		{
			forms.add(plural.substring(0, plural.length() - 2));
		}
		if (plural.endsWith("s"))
		{
			forms.add(plural.substring(0, plural.length() - 1));
		}
		if (plural.endsWith("men"))
		{
			forms.add(plural.substring(0, plural.length() - 3) + "man");
		}
		return forms;
	}

	private static String normalize(String s)
	{
		// NPC names can use non-breaking spaces and colour tags.
		return s.replace(' ', ' ')
			.replaceAll("<[^>]*>", "")
			.trim()
			.toLowerCase(Locale.ROOT);
	}
}
