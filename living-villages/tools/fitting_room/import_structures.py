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


def clean(src, dst, ground):
    t = nbtlib.load(src)
    palette = t['palette'] if 'palette' in t else t['palettes'][0]
    kept, dropped = [], 0
    for b in t['blocks']:
        name = str(palette[int(b['state'])]['Name'])
        y = int(b['pos'][1])
        if name in ANYWHERE_JUNK or (y == ground and name in GROUND_JUNK):
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
        name = fn[:-4]
        if not fn.endswith('.nbt') or name not in DRAFTS:
            continue
        dropped = clean(os.path.join(src_dir, fn), os.path.join(OUT, fn), GROUND_OFFSET.get(name, 0))
        print(f'imported {name} (dropped {dropped} ground/helper blocks)')
