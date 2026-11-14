# MC Survival Scoreboard plugin

Per-group scoreboards for Paper, like Multiverse-Inventories but for scoreboards.

Vanilla scoreboards are server-wide: a sidebar, tab-list or below-name objective is shown to everyone, and
criteria like `deathCount` count everywhere. This plugin gives each **group of worlds** its own scoreboard
(a real Bukkit scoreboard, set per player), so an objective is only shown, and only counted, in its group.
No teams, no datapack, no per-tick commands.

Built for Paper 26.3 (Java 25).

## How it works

- **Groups** come from Multiverse-Inventories' `groups.yml` (default), and/or from `config.yml`.
  Players in a world with no group keep the main scoreboard.
- Each group has its own Bukkit `Scoreboard`. On join and on `PlayerChangedWorldEvent` (and respawn), the player
  is given the scoreboard of their current group.
- **Criteria are tracked by the plugin**, not by vanilla (vanilla criteria update every scoreboard, so they would
  count outside the group):
  - counters, incremented by events in the group's worlds and saved: `deathCount`, `playerKillCount`, `totalKillCount`
  - live values, sampled while the player is in the group and removed when they leave:
    `health` (half-hearts, like vanilla), `food`, `level`, `xp`, `armor`, `air`
  - `dummy`: manual scores, saved
  - any other vanilla criteria (statistics, `trigger`, team kills, ...) is refused
- A Bukkit objective can only show in one slot, so an objective shown in several slots (like `HP` in `list`
  and `below_name`) gets one hidden mirror objective per slot (`HP.player_list`, ...), kept in sync.
- State (objectives, display slots, saved scores) is in `plugins/MCSurvivalScoreboard/state.yml`, written
  atomically on change (autosave) and on shutdown. Scores are keyed by player name.

## Commands (`/mvsb`, permission `mvsb.admin`, works from console and RCON)

Same syntax as vanilla `/scoreboard`, with the group first:

```
/mvsb groups | reload | save
/mvsb <group> objectives list
/mvsb <group> objectives add <name> <criteria> [displayName]
/mvsb <group> objectives ensure <name> <criteria> [displayName]   # idempotent: create or update the display name
/mvsb <group> objectives remove <name>
/mvsb <group> objectives modify <name> displayname <text>
/mvsb <group> objectives modify <name> rendertype <integer|hearts>
/mvsb <group> objectives setdisplay <list|sidebar|below_name> [objective]   # no objective: clear the slot
/mvsb <group> players list [entry] | get <entry> <obj> | set|add|remove <entry> <obj> <n> | reset <entry> [obj]
```

Display names are a JSON text component (like vanilla, starts with `{`, `[` or `"`) or MiniMessage text.

Example, for the 2gether survival group:

```
/mvsb survival objectives ensure HP health {"text":"❤","color":"red"}
/mvsb survival objectives modify HP rendertype hearts
/mvsb survival objectives setdisplay list HP
/mvsb survival objectives setdisplay below_name HP
/mvsb survival objectives ensure DeathCount deathCount {"text":"Deaths","color":"gray"}
/mvsb survival objectives setdisplay sidebar DeathCount
```

## Config

See [config.yml](src/main/resources/config.yml): Multiverse-Inventories import, extra groups,
refresh interval of live values (2 ticks), autosave interval.

## Build

```
./build.sh   # Gradle in Docker, no local JDK needed -> dist/MC-Survival-Scoreboard-<version>.jar (gitignored)
```

## Limitations

- Commands only affect the group named in the command; vanilla `/scoreboard`, datapacks and command blocks keep
  using the main scoreboard (that is what players outside any group see).
- Scores are keyed by player name: a renamed player starts from zero.
- Other plugins that set player scoreboards (e.g. TAB) conflict with this one.
- Deaths from before the objective was created are not counted.
