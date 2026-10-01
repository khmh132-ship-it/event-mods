# Living Villages

A Forge 1.20.1 mod that makes vanilla villages live on their own: they gather resources into a shared
stockpile, build new houses, make golems, defend themselves and grow. The player can help through quests.

## Building

Requires JDK 17 (Gradle downloads it automatically through the toolchain if it is missing).

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client with the mod loaded
```

## How a village lives

- **Discovery.** Any bell with villagers around it, near a player, becomes a village (radius 48, +16 per level).
- **Economy.** Every production cycle (1 min) villagers gather by profession (unemployed: wood, stone, a little
  sand, wool and iron; farmers: crops; masons: stone...) and finished buildings add their output. The village eats;
  hungry villages work at half speed. Missed cycles are made up for, so villages grow while nobody is around.
- **Growth.** The planner picks the next building by level and needs (lumberjack hut, houses when beds run short,
  warehouse, farms, builder's workshop, quest board; mine, sawmill, pens, watchtower, vanilla workshops at level 2;
  town hall, tavern, barracks, golem pad, stables at level 3), pays for it from the stockpile and finds a flat
  natural site facing the bell. Trees on the site are felled into the stockpile.
- **Construction.** Block by block: the site is levelled, then the building goes up bottom-up, then a dirt path is
  laid to the bell. Construction continues while unloaded and catches up when the area loads again.
- **Workers.** A finished builder's workshop or lumberjack hut hires an unemployed villager. The builder works on
  site and triples the pace; the lumberjack fells and replants trees and carries the logs to the warehouse.
- **Levels.** Level 2 at 6 villagers and 4 buildings, level 3 at 12 villagers, 10 buildings and a warehouse.

Settings are in `serverconfig/livingvillages-server.toml` of each world.

## Roadmap

1. ~~Village data~~, ~~economy~~, ~~construction and growth~~, ~~builder and lumberjack~~.
2. More workers: miner, farmer, guards.
3. Defense: guards, towers, golems built from stockpile iron, reaction to raids; walls (templates exist).
4. Quests: a board near the bell, generated from stockpile shortages; reputation rewards.
5. Other biome styles.

## Commands (op level 2)

- `/village list`, `/village info`, `/village buildings`, `/village types`
- `/village discover`: find villages around you; `/village create`: make the nearest bell a village
- `/village build <type> [free]`: build now at an automatically chosen site
- `/village plan`: run the planner now and show why candidates were skipped
- `/village finish`: complete everything under construction
- `/village produce <cycles>`, `/village growth <true|false>`
- `/village storage add|take <item> <count>`

## Tests

`tools/test_server/scenario_*.py` start the dev server on a throwaway world and drive it over RCON
(construction, real terrain, unload catch-up, workers, restart).

## Fitting room (house editing)

`tools/fitting_room/build_world.py` builds a void world where every vanilla plains village building and every
Living Villages draft (`tools/fitting_room/drafts.py`) stands on its own platform with a sign and a structure
block already set to SAVE as `livingvillages:plains/<name>`.

```
python3 tools/fitting_room/build_world.py   # -> build/fitting_room_plains.zip
```

Vanilla pieces are converted the way village generation places them: air is skipped and jigsaw blocks become
their final state. Edited buildings are saved to
`saves/<world>/generated/livingvillages/structures/plains/`.
