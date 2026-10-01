# Living Villages

A Forge 1.20.1 mod that makes vanilla villages live on their own: they gather resources into a shared
stockpile, build new houses, make golems, defend themselves and grow. The player can help through quests.

## Building

Requires JDK 17 (Gradle downloads it automatically through the toolchain if it is missing).

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client with the mod loaded
```

## Roadmap

1. **Village data** (done): bell-anchored `Village` stored in `SavedData`, shared stockpile, reputation.
2. Resource gathering: real work when loaded, abstract production when chunks are unloaded.
3. Construction: NBT house templates, site selection, builders placing blocks from the stockpile.
4. Defense: guards, towers, golems built from stockpile iron, reaction to raids.
5. Quests: a board near the bell, generated from stockpile shortages.

## Debug commands (op level 2)

- `/village list`: all villages in the current dimension
- `/village info`: the village you are standing in, with its stockpile
- `/village storage add|take <item> <count>`: change the stockpile

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
