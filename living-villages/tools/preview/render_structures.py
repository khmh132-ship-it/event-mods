"""Isometric voxel previews of the mod's building templates (no game needed).
Usage: python3 render_structures.py <out.png> [names...]"""
import hashlib
import math
import os
import sys

import nbtlib
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'livingvillages', 'structures', 'plains')
S = 7  # half-width of a voxel's top diamond in pixels

COLORS = {
    'oak_planks': (172, 138, 86), 'oak_log': (110, 85, 52), 'stripped_oak_log': (176, 143, 88),
    'oak_stairs': (162, 128, 78), 'oak_slab': (180, 146, 92), 'oak_fence': (150, 120, 70),
    'spruce_planks': (115, 85, 50), 'spruce_slab': (115, 85, 50), 'cobblestone': (125, 125, 125),
    'stone_bricks': (120, 120, 120), 'stone_brick_stairs': (115, 115, 115), 'stone_brick_slab': (118, 118, 118),
    'stone_brick_wall': (112, 112, 112), 'cobblestone_wall': (120, 120, 120), 'glass_pane': (190, 225, 235),
    'oak_door': (140, 110, 60), 'oak_trapdoor': (140, 110, 60), 'chest': (160, 110, 40),
    'trapped_chest': (160, 110, 40), 'barrel': (120, 85, 45), 'red_bed': (180, 40, 40), 'white_bed': (230, 230, 230),
    'wall_torch': (255, 210, 80), 'torch': (255, 210, 80), 'lantern': (255, 200, 90), 'bookshelf': (130, 90, 50),
    'dirt_path': (148, 121, 65), 'dirt': (125, 90, 60), 'cobblestone_stairs': (125, 125, 125),
}


def color(name):
    n = name.split(':')[-1]
    if n in COLORS:
        return COLORS[n]
    if name.startswith('livingvillages:'):
        return (230, 80, 230)
    h = hashlib.md5(n.encode()).digest()
    return (h[0] // 2 + 70, h[1] // 2 + 70, h[2] // 2 + 70)


def render(path):
    t = nbtlib.load(path)
    pal = t['palette']
    sx, sy, sz = [int(v) for v in t['size']]
    vox = {}
    for b in t['blocks']:
        n = str(pal[int(b['state'])]['Name'])
        if n.endswith(':air') or n.endswith('structure_void'):
            continue
        vox[tuple(int(v) for v in b['pos'])] = n
    w = (sx + sz) * S + 4
    h = (sx + sz) * S // 2 + sy * S + 4
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ox, oy = sz * S + 2, sy * S + 2

    def iso(x, y, z):
        return ox + (x - z) * S, oy + (x + z) * S / 2 - y * S

    for (x, y, z) in sorted(vox, key=lambda p: (p[0] + p[2], p[1])):
        r, g, b = color(vox[(x, y, z)])
        top = [iso(x, y + 1, z), iso(x + 1, y + 1, z), iso(x + 1, y + 1, z + 1), iso(x, y + 1, z + 1)]
        left = [iso(x, y + 1, z + 1), iso(x + 1, y + 1, z + 1), iso(x + 1, y, z + 1), iso(x, y, z + 1)]
        right = [iso(x + 1, y + 1, z), iso(x + 1, y + 1, z + 1), iso(x + 1, y, z + 1), iso(x + 1, y, z)]
        d.polygon(top, fill=(r, g, b))
        d.polygon(left, fill=(int(r * .75), int(g * .75), int(b * .75)))
        d.polygon(right, fill=(int(r * .6), int(g * .6), int(b * .6)))
    return img


if __name__ == '__main__':
    out = sys.argv[1]
    names = sys.argv[2:] or sorted(f[:-4] for f in os.listdir(DIR) if f.endswith('.nbt'))
    tiles = [(n, render(os.path.join(DIR, n + '.nbt'))) for n in names]
    cols = 5
    cw = max(im.width for _, im in tiles) + 10
    ch = max(im.height for _, im in tiles) + 24
    rows = math.ceil(len(tiles) / cols)
    sheet = Image.new('RGBA', (cols * cw, rows * ch), (36, 40, 48, 255))
    dr = ImageDraw.Draw(sheet)
    for i, (n, im) in enumerate(tiles):
        x, y = (i % cols) * cw, (i // cols) * ch
        sheet.alpha_composite(im, (x + (cw - im.width) // 2, y + ch - 20 - im.height))
        dr.text((x + 6, y + ch - 16), n, fill=(230, 230, 230))
    sheet.save(out)
    print('rendered', len(tiles))
