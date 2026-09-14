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
```

That's the minimum to make an arena joinable — `pos1`/`pos2` (opposite corners of
a box that should contain the whole map) are required so the arena knows what to
snapshot and reset each round. Optional:

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

The whole `pos1`/`pos2` box is snapshotted the instant a round starts and restored
block-for-block the instant it ends — covers everything that changed, broken or
placed, not just what BowBash itself touched, so the map is always back to exactly
its starting state for the next round.

Placing **light blue stained glass** and **orange stained glass** anywhere in an
arena (typically near a team's spawn) creates an infinite block-farming resource:
mining light blue hands the blue team endless blue stained glass, mining orange
hands the red team endless red stained glass. These blocks never actually break,
can't be destroyed by arrows or egg/snowball explosions, and the opposing team
can't mine (or deplete) them at all.

Falling out of the arena scores a point immediately (decided the moment a player
first drops below Y=0), with a sound cue for the whole arena and the scoreboard
updated on the spot. BowBash deliberately doesn't force a teleport or touch actual
death on a fall — it expects something else on the server to handle what happens
to a player physically after that point — but it does redirect any respawn that
does happen back to the player's own team spawn (instead of the world spawn) for
as long as their round is still live, and keeps ordinary combat from killing
anyone outright. Winning a round plays a victory sound for every player on the
server plus a firework burst at the arena's lobby.

Links
-----

- [Original project](https://github.com/MysticCity/BowBash)
