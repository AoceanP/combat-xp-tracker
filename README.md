# Combat & XP Tracker

A RuneLite plugin for skill goals, max hits for every combat style, and per-monster hits, kills and drops, all in one sidebar panel.

## Goals
- Set a goal from the panel (**+ Add goal**) or by right-clicking a skill in the stats tab and choosing **Set goal**
- Goals can be a level from 2 to 126 (virtual levels past 99 work) or an XP amount up to 200M: `99`, `126`, `13.03m`, `200m`, `500k`
- A light blue progress bar fills from the XP you had when you set the goal, so it never resets on level-up
- XP/hr, time left, XP left and XP gained this session for every goal
- XP/hr can be the last few seconds (reacts fast) or the whole session with breaks left out (steadier)
- A notification and a chat message when you reach a goal
- Right-click a goal to change it, give it its own bar colour, hide it from the overlay or remove it

## Max hit
- Your max hit for the style you're using: melee, ranged or magic
- Melee: all worn gear, boosted Strength, strength prayers, attack style and Void
- Ranged: ranged strength from all gear and ammo, boosted Ranged, ranged prayers (including Deadeye), Accurate's +3, Void and Elite Void
- Magic: your autocast spell or a powered staff (tridents, Sanguinesti, Tumeken's shadow, Warped sceptre), magic damage gear, magic prayers and Elite Void
- Separate max hits for your slayer task (Black mask / Slayer helmet) and against undead (Salve amulet), highlighted when your current target is on task
- Special attacks and weapon passives aren't included

## Monsters
- Every monster you fight, like the Loot Tracker: kills, hits, average hit, biggest hit and which combat style dealt it
- Kills are counted when a monster you hit dies, even if it drops nothing
- Kills per hour and GP per hour, counting only time spent fighting
- Every drop with its Grand Exchange value
- Sort by most recent, loot value, kills, biggest hit or name; right-click to hide a monster
- Remembered between sessions and client restarts, separately for each account

## Session summary
- **Copy** puts a text summary of your session (XP, hits, kills, loot, top monsters and goals) on your clipboard, ready to paste into Discord

## Overlays
- Optional on-screen overlay with the key numbers
- One infobox per goal with its progress
