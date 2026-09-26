# Combat & XP Tracker

A RuneLite plugin for skill goals, max hits for every combat style, and per-monster hits, kills and drops, all in one sidebar panel.

## Goals
- Set a goal from the panel (**+ Add goal**) or by right-clicking a skill in the stats tab and choosing **Set goal**
- Goals can be a level from 2 to 126 (virtual levels past 99 work) or an XP amount up to 200M: `99`, `126`, `13.03m`, `200m`, `500k`
- A light blue progress bar fills from the XP you had when you set the goal, so it never resets on level-up
- XP/hr, time left, time to your next level, XP left and XP gained this session for every goal
- Kills left for combat goals, worked out from the hitpoints of the monster you're fighting (a rough hits/actions estimate otherwise)
- XP/hr can be the last few seconds (reacts fast) or the whole session with breaks left out (steadier)
- A notification and a chat message when you reach a goal
- Right-click a goal to change it, give it its own bar colour, hide it from the overlay or remove it

## Max hit
- Your max hit for the style you're using: melee, ranged or magic
- Melee: all worn gear, boosted Strength, strength prayers, attack style and Void
- Ranged: ranged strength from all gear and ammo, boosted Ranged, ranged prayers (including Deadeye), Accurate's +3, Void and Elite Void
- Magic: your autocast spell or a powered staff (tridents, Sanguinesti, Tumeken's shadow, Warped sceptre), magic damage gear, magic prayers and Elite Void
- Separate max hits for your slayer task (Black mask / Slayer helmet) and against undead (Salve amulet), highlighted when your current target is on task
- Special attack max hit for your weapon: Armadyl, Bandos, Saradomin, Zamorak and Ancient godswords, Dragon dagger, warhammer, longsword and mace, halberds, Abyssal dagger and bludgeon, and more
- Special attacks and weapon passives aren't included

## Monsters
- Every monster you fight, like the Loot Tracker: kills, hits, average hit, biggest hit and which combat style dealt it
- Switch between **This session** and **All time** (remembered between sessions, separately for each account)
- Kills are counted when a monster you hit dies, even if it drops nothing
- Bosses fought as several NPCs share one card: Royal Titans, Grotesque Guardians, Nex (with her minions) and the Barrows brothers
- Loot from bosses you **Loot** after the kill (e.g. the Royal Titans) is recorded too
- Kills per hour and GP per hour, counting only time spent fighting
- Your slayer task on the matching card: kills left and roughly how long at your kill rate
- A chat summary when you finish a slayer task: kills, time, loot, GP/hr and biggest hit
- Rare drops (worth more than a value you choose) get a gold border, and each monster shows your dry streak: kills since its last rare drop
- Pin monsters to the top of the list
- Loot valued at Grand Exchange or High Alchemy prices (for ironmen)
- Search, sort, hide monsters, ignore items (right-click an item), and collapsed cards stay collapsed

## Session summary
- **Copy** puts a text summary of your session (XP, hits, kills, loot, top monsters and goals) on your clipboard, ready to paste into Discord

## Overlays
- Optional on-screen overlay: choose which lines it shows (damage, max hit and spec, slayer task, goals)
- One infobox per goal with its progress
