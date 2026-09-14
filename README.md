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
```

That's the minimum to make an arena joinable. Optional:

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
Any blocks destroyed during the match are automatically restored when it ends.

Placing **light blue stained glass** and **orange stained glass** anywhere in an
arena (typically near a team's spawn) creates an infinite block-farming resource:
mining light blue hands the blue team endless blue stained glass, mining orange
hands the red team endless red stained glass. These blocks never actually break,
can't be destroyed by arrows or egg/snowball explosions, and the opposing team
can't mine (or deplete) them at all.

BowBash only *scores* a fall out of the arena (one team's point, decided the moment
a player first drops below Y=0) — it deliberately doesn't touch actual death or
respawning (no forced teleport, no health reset on death) beyond keeping ordinary
combat from killing anyone outright, so it expects something else on the server to
handle what happens to a player physically after that point.

Links
-----

- [Original project](https://github.com/MysticCity/BowBash)
