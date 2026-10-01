"""Imports buildings edited in the fitting room into the mod.

Usage: python3 tools/fitting_room/import_structures.py <path to .../generated/livingvillages/structures/plains>

Only Living Villages buildings are taken (vanilla copies are skipped). The ground layer of each structure
(y=0, or GROUND_OFFSET for buildings that reach underground) loses its grass and air, so a building placed in a
village replaces the ground only where it has a real foundation.
"""
import os
import sys

import nbtlib
from nbtlib.tag import Compound, List

sys.path.insert(0, os.path.dirname(__file__))
from drafts import DRAFTS, GROUND_OFFSET  # noqa: E402
from export_drafts import OUT  # noqa: E402

GROUND_JUNK = {'minecraft:air', 'minecraft:cave_air', 'minecraft:grass_block', 'minecraft:grass',
               'minecraft:tall_grass', 'minecraft:structure_block', 'minecraft:structure_void'}
ANYWHERE_JUNK = {'minecraft:structure_block', 'minecraft:structure_void', 'minecraft:jigsaw'}
# In buildings dug into the ground (the mine descent) natural ground shown around them is "whatever is there":
# it is never placed, the world keeps its own blocks.
NATURAL = {'minecraft:' + n for n in ('stone', 'dirt', 'grass_block', 'coarse_dirt', 'deepslate', 'granite', 'diorite',
                                       'andesite', 'tuff', 'gravel', 'sand', 'sandstone', 'clay', 'podzol', 'rooted_dirt',
                                       'calcite', 'cobbled_deepslate', 'mud')}
DUG_IN = {'mine'}
# Names saved in the fitting room under a different name than the building id.
RENAMED = {'kazarma': 'barracks', 'podmosterie': 'apprentice_workshop'}


def clean(src, dst, ground, dug_in=False):
    t = nbtlib.load(src)
    palette = t['palette'] if 'palette' in t else t['palettes'][0]
    kept, dropped = [], 0
    for b in t['blocks']:
        name = str(palette[int(b['state'])]['Name'])
        y = int(b['pos'][1])
        # Dug-in buildings keep their air at ground level: that is the way in, to be dug out.
        ground_junk = y == ground and name in GROUND_JUNK and not (dug_in and name == 'minecraft:air')
        if name in ANYWHERE_JUNK or ground_junk or (dug_in and name in NATURAL):
            dropped += 1
            continue
        kept.append(b)
    t['blocks'] = List[Compound](kept)
    t.save(dst, gzipped=True)
    return dropped


if __name__ == '__main__':
    src_dir = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for fn in sorted(os.listdir(src_dir)):
        name = RENAMED.get(fn[:-4], fn[:-4])
        if not fn.endswith('.nbt') or name not in DRAFTS:
            continue
        if fn[:-4] != name and os.path.exists(os.path.join(src_dir, name + '.nbt')) and fn[:-4] not in RENAMED:
            continue
        dropped = clean(os.path.join(src_dir, fn), os.path.join(OUT, name + '.nbt'), GROUND_OFFSET.get(name, 0),
                        name in DUG_IN)
        print(f'imported {fn[:-4]} as {name} (dropped {dropped} ground/helper blocks)')
