# Combat & XP Tracker

A RuneLite plugin for skill goals, max hits for every combat style, and per-monster hits, kills and drops, all in one sidebar panel.

## Goals
- Set a goal from the panel (**+ Add goal**) or by right-clicking a skill in the stats tab and choosing **Set goal**
- Goals can be a level from 2 to 126 (virtual levels past 99 work) or an XP amount up to 200M: `99`, `126`, `13.03m`, `200m`, `500k`
- A light blue progress bar fills from the XP you had when you set the goal, so it never resets on level-up
- XP/hr, time left, time to your next level, XP left and XP gained this session for every goal
- Kills left for combat goals, at your real XP per kill of the monster you're fighting (a rough hits/actions estimate otherwise)
- XP/hr can be the last few seconds (reacts fast) or the whole session with breaks left out (steadier)
- A notification and a chat message when you reach a goal
- Right-click a goal to change it, give it its own bar colour, hide it from the overlay or remove it
- Drag goals up or down to put them in your own order

## Kills to level
- Under the max hit: how many more kills of the monster you're fighting each combat skill needs for its next level, and your next combat level
- Uses your real XP per kill, measured from your XP drops once you've killed one, so it works for magic and burst spells too. Before the first kill it's estimated from the monster's hitpoints
- On a slayer task, what the rest of the task will give: XP in each skill and the level you'll reach
- Optional overlay line for the skill you're training

## Max hit
- Your max hit for the style you're using: melee, ranged or magic
- Melee: all worn gear, boosted Strength, strength prayers, attack style and Void
- Ranged: ranged strength from all gear and ammo, boosted Ranged, ranged prayers (including Deadeye), Accurate's +3, Void and Elite Void
- Magic: your autocast spell or a powered staff (tridents, Sanguinesti, Tumeken's shadow, Warped sceptre), magic damage gear, magic prayers and Elite Void
- Separate max hits for your slayer task (Black mask / Slayer helmet) and against undead (Salve amulet), highlighted when your current target is on task
- Special attack max hit for your weapon: Armadyl, Bandos, Saradomin, Zamorak and Ancient godswords, Dragon dagger, warhammer, longsword and mace, halberds, Abyssal dagger and bludgeon, and more
- Luck: once you've landed enough hits, how lucky they've been this session against your max hit ("Top 18%"), with your max hits against how many were expected. Special attacks and weapons that don't roll evenly are left out. It's in the Copy summary too
- When your max hit changes (a potion, a prayer, a gear swap, a boost wearing off) it flashes green or red for a few seconds with the change, e.g. *34 (+3)*
- Effective levels: your Attack and Strength (or Ranged, or Magic) with prayer, style, the hidden +8 and Void, which accuracy and max hit are built on
- Gear bonuses: obsidian armour and Berserker necklace with obsidian weapons, Inquisitor's armour with crush weapons, crystal armour with the Bow of faerdhinen or crystal bow, and charged wilderness weapons in the Wilderness
- Extra rows for weapons strong against certain monsters, highlighted when you're fighting one: Dragon hunter lance and crossbow (dragons), Arclight, Emberlight, Silverlight and Darklight (demons), Keris (kalphites), and the Twisted bow against high-Magic monsters

## Monsters
- Every monster you fight, like the Loot Tracker: kills, hits, average hit, biggest hit and which combat style dealt it
- Switch between **This session** and **All time** (remembered between sessions, separately for each account)
- Kills are counted when a monster you hit dies, even if it drops nothing
- Bosses fought as several NPCs share one card: Royal Titans, Grotesque Guardians, Nex (with her minions) and the Barrows brothers
- Loot from bosses you **Loot** after the kill (e.g. the Royal Titans) is recorded too
- Kills per hour and GP per hour, counting only time spent fighting
- Your slayer task on the matching card: kills left and roughly how long at your kill rate
- A chat summary when you finish a slayer task: kills, time, loot, GP/hr and biggest hit
- A chat summary when you kill a boss: fight time, your damage and its share of the boss's hitpoints, hits and biggest hit, e.g. *Vorkath kill 124 in 1:32.4: you dealt 740 damage (99%) in 18 hits, biggest hit 60.*
- Accuracy, DPS and attack uptime tiles on each monster: the share of your hits that did damage, your damage per second while fighting, and how much of each fight you spent attacking (ticks past your weapon's attack speed before your next attack, from eating, moving or a late click, count against it)
- Target card: the monster you're fighting right now at the top of the tab, with its health bar, hits left to kill it at your average hit, your time per kill and your DPS
- Boss personal bests: your fastest kill and your last kill on each boss card, from the game's own fight duration message, with a milestone message when you beat it
- Supplies used: food eaten, potion doses, runes cast, ammo fired and cannonballs, with what they cost at Grand Exchange prices, for each monster (this session or all time) and each slayer task. Hover for the breakdown
- Collection log: open a boss's page in the in-game Collection Log and its monster card shows how many of its items you have, which ones you're still missing and your kill count. It stays up to date as new items are added. Bosses and raids only, since slayer monsters share one page
- Rare drops (worth more than a value you choose) get a gold border, and each monster shows your dry streak: kills since its last rare drop
- Pin monsters to the top of the list, and drag pinned monsters into your own order
- Loot valued at Grand Exchange or High Alchemy prices (for ironmen)
- Search, sort, hide monsters, ignore items (right-click an item), and collapsed cards stay collapsed
- Prayer drain: how long a prayer potion dose lasts while fighting each monster, measured from your own prayer points (restores and kills without prayer on aren't counted)

## Slayer
A **Slayer** tab with everything about your slayer progress:
- Your master, Slayer points, task streak, current task (left / assigned) and Konar's location
- Task guide: every place your task can be done, with the game's own directions, and what to bring (nose peg, earmuffs, rock hammer, mirror shield and so on), warning you if it isn't in your gear or inventory. Locations are read live from the game, so they stay up to date
- The guide checks your bank too: once you've opened it, an item you need shows **In bank** if you have one there, or **Missing** if you don't own one. It's saved on your computer only and can be turned off in the settings
- Your best area: right-click the area you're doing your task in. The plugin remembers the spot and picks it automatically next time (Konar's areas are picked for you). Each area then shows your own Slayer XP/hr and kills/hr there, your best one is marked, and areas you've found to be multi-combat or in the Wilderness are tagged
- Next task odds: the likeliest tasks from your master and their usual size, from the game's own task weights, leaving out tasks your levels or unlocks don't allow. Right-click a task to mark it as blocked. It's an estimate, since quest requirements aren't in the game data
- Where to get a task: pick any task (or right-click one anywhere in the tab) to see every master's chance of giving it to you, so you know who to go to for the task you want
- Master tip: if another slayer master would clearly give you more Slayer XP per hour (10% or more), a tip under the next task odds says so. It uses each master's task chances and your own rate on each task from your log
- Your tasks ranked by your own Slayer XP/hr and GP/hr, with your slowest ones as block or skip ideas
- Prayer potions: on a task, the guide says about how many prayer potions the kills left will take, at your own measured drain
- What your next task is worth, when the next x5 bonus task comes, and the next big bonus (50th, 100th, 250th, 1,000th task) with how many points each master would give for it, so you can save it for the best one
- Superiors: how many appeared this task and across your logged tasks, and how often. An optional notification when one spawns
- A task log of every finished task, saved per account: kills, time, Slayer XP, loot, superiors, points earned and biggest hit, with XP/hr and GP/hr on hover. Right-click a task to remove it

## Supplies & loot
- Loot despawn alert (off by default): a countdown to when your oldest monster drop on the ground disappears, and a notification shortly before, so you can AFK and pick everything up in one trip. Only your own drops from monsters count, and you can ignore cheap drops
- Casts left: while autocasting, an infobox shows how many casts your runes allow, counting the rune pouch, combination runes, elemental staves, battlestaves and tomes. It turns gold when you're running low, with an optional notification

## Themes
Twelve colour themes for the sidebar in the settings: Dark (the original, and the default), Classic, Midnight, Abyssal, Zamorak, Saradomin, Guthix, Armadyl (light), Bandos, Dragonfire, Frostbite and Venom. Each theme has its own accent colour, unless you've picked your own goal bar colour.

## Session summary and sharing
- **Copy** puts a text summary of your session (XP, hits, attack uptime and DPS, kills, loot, top monsters and goals) on your clipboard, ready to paste into Discord
- **Wrapped** opens Gielinor Wrapped: your lifetime stats (kills, damage, loot, biggest hit, most killed, longest dry streak, slayer tasks, fastest and richest task). Copy it as an image in your panel theme, or as text
- **Export** saves your monsters, slayer task log or goals as a CSV spreadsheet for Excel or Google Sheets, or copies it to paste straight in
- Milestones: a chat message for 100, 500, 1,000 (and more) kills of a monster, total kills, your 10th, 50th, 100th (and more) slayer task, a new biggest hit ever, and a new boss personal best. Optionally a screenshot of each one

## Overlays
- Optional on-screen overlay: choose which lines it shows (damage, max hit and spec, slayer task, kills to level, goals)
- One infobox per goal with its progress
- Attack timer: while fighting, the ticks until your next attack show above your character, turning green when you can attack. It uses your weapon's attack speed (Rapid, spells and powered staves included), works for melee, ranged and magic, and isn't thrown off by your block animation when you take a hit. It can be turned off
