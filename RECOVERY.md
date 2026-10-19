# 2.0 data loss: FAQ

Version 2.0 had a bug that stopped the plugin from starting. For some players, it also erased the plugin's saved stats. Version 2.0.1 fixes the startup bug. I'm sorry this happened. This page explains who was affected and what you can get back.

## Was I affected?

Only if **you turned the plugin on while logged in to the game** while you had version 2.0. That usually happened when RuneLite switched the plugin off and you switched it back on.

If you only started RuneLite at the login screen, your data was not touched.

To check: update to 2.0.1 (restart RuneLite), switch the plugin on, log in and open the **Monsters** tab. If your monsters and kills are there, you're fine.

## What was erased?

The plugin's own saved data for the account you were logged in to:
- Monsters tab: kills, drops, hits, loot value, boss times
- Slayer task log
- Collection log progress, supplies, deaths, damage taken and prayer stats

Your goals and settings were **not** erased. Other accounts you weren't logged in to at the time were not affected. Nothing in the game itself was affected.

## First, before trying anything

1. Update to 2.0.1 by restarting RuneLite, then switch the plugin back on.
2. **Don't press Reset** in the panel.
3. Close RuneLite completely and make a copy of this file somewhere safe:
   - Windows: `%USERPROFILE%\.runelite\profiles2\$rsprofile--1.properties`
   - Mac / Linux: `~/.runelite/profiles2/$rsprofile--1.properties`

## Can I get it back?

### If your computer keeps file backups

Your stats live in the file above. If you have an older copy of it from **before you updated to 2.0**, it still has everything:
- **Windows:** right-click the `profiles2` folder, choose **Properties**, then the **Previous Versions** tab. This only shows something if File History or restore points were turned on.
- **Mac:** Time Machine.
- **Linux:** whatever backup tool you use.

**Don't replace the file yourself.** Restoring the whole file also rolls back every other plugin's data for that account, and RuneLite may replace it again from its cloud sync. [Open an issue](https://github.com/AoceanP/combat-xp-tracker/issues/new?labels=bug) that says you have a backup, and I'll help you restore only the Combat & XP Tracker data.

### If you don't have a backup

The plugin's detailed stats can't be rebuilt, because they weren't stored anywhere else. But RuneLite's own records weren't touched:
- **Loot Tracker** (RuneLite's built-in plugin) still has your kills and drops per monster, if you had it on.
- **Boss kill counts** are kept by the game. Type `!kc <boss>` in chat, or check the boss pages of your collection log.

From here, the plugin tracks everything again as normal.

## Will this happen again?

The startup bug is fixed in 2.0.1. The next update adds a safety check, so the plugin never saves unless your data was loaded first. Even if the plugin fails to start some other way, it won't overwrite your stats.

## Still stuck?

[Open an issue](https://github.com/AoceanP/combat-xp-tracker/issues/new?labels=bug) and include which account was affected (the in-game name is enough) and whether you have a backup.
