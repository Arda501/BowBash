BowBash
=======

Bash others to fall and win the game by getting the score of the others down to zero.

A standalone Paper plugin for Minecraft **26.2** (Java 25+ required). No external
dependencies — earlier versions of this plugin required a separate "MinigamesLib"
library; that dependency has been removed and its needed functionality reimplemented
directly in this plugin, since MinigamesLib and its Maven repository have been
unmaintained/unreachable since 2017.

Building
--------

```
mvn clean package
```

Produces `target/BowBash-<version>.jar`. Drop it into your server's `plugins/` folder.

Pushing a version tag (`git tag v2.0.1 && git push origin v2.0.1`) instead builds it on GitHub
and attaches the jar to a new [Release](https://github.com/Arda501/BowBash/releases) - no local
build needed.

Setting up an arena
--------------------

All setup commands require the `bowbash.admin` permission (granted to ops by default).

```
/bb create <arena>
/bb setlobby <arena>          # stand where players should wait before a game
/bb setspawn <arena> red      # stand at the red team's starting platform
/bb setspawn <arena> blue     # stand at the blue team's starting platform
/bb pos1 <arena>              # stand at one corner of the playable map area
/bb pos2 <arena>              # stand at the opposite corner
/bb savemap <arena>           # capture everything in that box as the reset baseline
```

That's the minimum to make an arena joinable — do `savemap` last, once the map
actually looks how you want it to reset to; the arena won't be joinable until
you do. Re-run it any time you deliberately change the built map and want that
to become the new baseline. Optional:

```
/bb setdefaultscore <arena> <n>
/bb remove <arena>
/bb stop <arena>              # force-stop and reset a running/stuck arena
```

Players join with `/bb join <arena> [red|blue]` (omit the team to auto-balance),
or by right-clicking a sign whose first line reads `[BowBash]` and second line
names the arena. There's no minimum or maximum player count.

Joining hands you a "Not Ready" item — right-click it to ready up. As soon as both
teams are the same size (at least 1 each) and everyone's ready, a short countdown
starts automatically; un-readying, or a team becoming uneven, cancels it instantly.
Joining puts you in Adventure mode; when the round actually starts every player is
switched to Survival, and back to Adventure once the round ends.

The `/bb savemap` baseline is restored block-for-block the instant a round ends —
covers everything that changed, broken or placed, not just what BowBash itself
touched, including chest/barrel/furnace/etc. contents (not just the block itself) —
and persists to disk, so it survives a server restart; nothing is re-captured
automatically, the saved baseline is what every round resets to until you
`savemap` again.

> Upgrading from an older version: `savemap`'s save format changed to also cover
> container contents. Run `/bb savemap <arena>` again for each of your arenas -
> any `.snapshot` file saved before this update won't load.

Placing **light blue stained glass** and **orange stained glass** anywhere in an
arena (typically near a team's spawn) creates an infinite block-farming resource:
mining light blue hands the blue team endless blue stained glass, mining orange
hands the red team endless red stained glass. These blocks never actually break,
can't be destroyed by arrows or egg/snowball explosions, and the opposing team
can't mine (or deplete) them at all.

Falling out of the arena scores a point immediately (decided the moment a player
first drops below Y=0), with a sound cue for the whole arena and the scoreboard
updated on the spot. The in-game scoreboard also shows an elapsed-time "Time:
M:SS" line, always on top. BowBash doesn't touch health or damage beyond
cancelling friendly fire, and never *causes or prevents* a death — that's left
entirely to whatever else is handling it (a command block, another plugin,
plain vanilla). Once a death does happen, though, BowBash takes over what comes
after: a short delay (`config.respawn_delay_seconds`, default 1s), then an
automatic respawn with no button to click, landing back at the player's own
team spawn with a fresh kit. Winning a round plays a victory sound for every
player on the server plus a handful of real firework rockets, in the winning
team's colour, launched over the arena's lobby.

**Blue/black/red/light blue glazed terracotta**, **polished sulfur slabs**, and
**dark prismarine slabs** can't be broken at all, by anyone, through any means
(mining, arrows, egg/snowball explosions) while a game is on — useful for map
borders, decoration, or anything else that should never come apart.

While a round is actually in progress, a non-admin player can't use any command
except `/help` and `/matrix` — everything else is silently blocked. Players with
`bowbash.admin` are never restricted.

Displaying live stats (signs, scoreboards, ...)
------------------------------------------------

If [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is
installed, BowBash registers its own placeholders on startup (nothing to
configure) - any PAPI-aware plugin can then show live stats for whichever arena
the *viewing* player is currently in, including on a sign via
[SignManager](https://modrinth.com/plugin/signmanager)
(`/sign edit line 1 %bowbash_map%`, refreshed automatically at whatever interval
you've set with `/sign admin interval`), a scoreboard plugin, or chat formatting.
Since BowBash is multi-arena, these are always relative to the requesting player,
not global — a player not currently in any arena sees `-` for all of them:

| Placeholder | Value |
|---|---|
| `%bowbash_time%` | "M:SS" elapsed since the viewer's round went in-game, or `-` |
| `%bowbash_red_score%` / `%bowbash_blue_score%` | the viewer's arena's current scores, or `-` |
| `%bowbash_map%` | the viewer's arena's name, or `-` |

No PlaceholderAPI installed? BowBash just skips registering them - everything
else works the same either way.

Links
-----

- [Original project](https://github.com/MysticCity/BowBash)
