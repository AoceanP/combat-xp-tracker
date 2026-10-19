# 2.0 data loss: FAQ

Version 2.0 had a bug that stopped the plugin from starting. For some players, it also erased the plugin's saved stats. Version 2.0.1 fixed the startup bug. Version 2.0.2 makes sure it can't happen again and adds a button to rebuild what was lost. I'm sorry this happened.

## Was I affected?

Only if **you turned the plugin on while logged in to the game** while you had version 2.0. That usually happened when RuneLite switched the plugin off and you switched it back on.

If you only started RuneLite at the login screen, your data was not touched.

To check, log in and open the **Monsters** tab. If your monsters and kills are there, you're fine.

## What was erased?

The plugin's own saved data for the account you were logged in to:
- Monsters tab: kills, drops, hits, loot value, boss times
- Slayer task log
- Collection log progress, supplies, deaths, damage taken and prayer stats

Your goals and settings were **not** erased. Other accounts you weren't logged in to at the time were not affected. Nothing in the game itself was affected.

## How do I get it back?

It takes about two minutes.

### 1. Update to 2.0.2

Restart RuneLite and it updates by itself. If the plugin is switched off, switch **Combat & XP Tracker** back on.

### 2. Restore your Monsters tab

1. Log in to the account that lost its stats.
2. Open the plugin's sidebar panel, then the **Monsters** tab.
3. At the top is a card that says **Lost your stats in 2.0?** Click **Restore**, then pick:
   - **All monsters**, then **Restore** to confirm, or
   - **Choose monsters...** to pick from a list. Each row shows the kills and loot the Loot Tracker has, and there's a search box. Tick the ones you want and click **Restore**.
4. A chat message says how many monsters and kills came back.

This rebuilds your kills and drops from two places RuneLite keeps for you:
- **RuneLite's Loot Tracker:** kills and drops for every monster it recorded.
- **The game's boss kill counts:** these are used when they're higher, for example when the Loot Tracker missed some kills.

Anything you've tracked since the wipe is kept. For each monster the restore keeps the **higher** kill count and the **larger** amount of each item, so nothing is counted twice.

The card only shows if the account has Loot Tracker records, and it can only be used once per account. If you don't need it, click **Dismiss**.

### 3. Refill your collection log

Open the **Collection Log** in game and click through the boss pages you care about. The plugin reads each page as you open it, so the item grid and kill counts fill straight back in.

## What can't come back?

These were only ever recorded by this plugin, so there's nothing to rebuild them from:
- Hit stats (average hit, accuracy, biggest hit)
- Boss times and personal bests
- Supplies, deaths, damage taken and prayer stats
- Your slayer task log
- Which kill a rare drop came on

They all start tracking again from now.

## Didn't have the Loot Tracker on?

Then there's nothing for the restore to use, and the card won't show. You can still get your boss kill counts back by opening your collection log's boss pages (step 3).

If your computer keeps file backups (Windows **Previous Versions**, Mac **Time Machine**), an older copy of `.runelite/profiles2/$rsprofile--1.properties` from before you updated to 2.0 has everything. **Don't replace the file yourself.** It rolls back every other plugin too, and RuneLite's cloud sync may undo it. [Open an issue](https://github.com/AoceanP/combat-xp-tracker/issues/new?labels=bug) and I'll help you restore only this plugin's data.

## Removed a monster or reset by accident?

The same card comes back whenever you **Remove** a monster or choose **Reset → Everything**. Click **Restore** to bring that monster back, or after a reset, all monsters or the ones you choose. It works the same way: combined, never doubled, nothing goes down.

## Restoring later, any time

The card isn't the only way in. You can restore whenever you like, for all monsters or the ones you choose:
- **Sidebar:** click **Restore** at the top of the plugin's panel, next to **Reset**.
- **Settings:** open the plugin's settings, then **Monsters**, and tick **Restore from Loot Tracker**. The Restore card appears in the Monsters tab and the box unticks itself.

Log in to the account first, since each account has its own stats.

## Will this happen again?

No. Since 2.0.2, the plugin only saves an account's stats after it has loaded them. If it ever fails to start, it can't overwrite anything.

## Still stuck?

[Open an issue](https://github.com/AoceanP/combat-xp-tracker/issues/new?labels=bug) and include which account was affected (the in-game name is enough).
