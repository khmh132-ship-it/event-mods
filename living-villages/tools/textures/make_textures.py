"""Pixel art for Living Villages: outfits for the village's own professions (64x64 villager overlays, same
layout as vanilla profession textures) and textures for the job blocks (16x16). Deterministic: run again after
changes.  python3 make_textures.py"""
import io
import os
import random
import zipfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_ENTITY = os.path.join(ROOT, 'src/main/resources/assets/livingvillages/textures/entity/worker')
OUT_BLOCK = os.path.join(ROOT, 'src/main/resources/assets/livingvillages/textures/block')
JAR = zipfile.ZipFile(os.path.expanduser('~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar'))


def vanilla(path):
    return Image.open(io.BytesIO(JAR.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


class Canvas:
    def __init__(self, w, h, seed):
        self.img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        self.px[x, y] = c if len(c) == 4 else c + (255,)

    def fill(self, x0, y0, x1, y1, c, noise=0.08):
        """Fills [x0,x1) x [y0,y1) with c, each pixel slightly lighter or darker, as Minecraft cloth is."""
        for x in range(x0, x1):
            for y in range(y0, y1):
                self.put(x, y, shade(c, 1 + self.rnd.uniform(-noise, noise)))

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1):
            self.put(x, y, c)


# Villager model UVs (64x64): faces of the boxes as (x0, y0, x1, y1).
HAT = {'top': (40, 0, 48, 8), 'right': (32, 8, 40, 18), 'front': (40, 8, 48, 18), 'left': (48, 8, 56, 18),
       'back': (56, 8, 64, 18)}
JACKET = {'top': (6, 38, 14, 44), 'right': (0, 44, 6, 62), 'front': (6, 44, 14, 62), 'left': (14, 44, 20, 62),
          'back': (20, 44, 28, 62)}
BODY = {'right': (16, 26, 22, 38), 'front': (22, 26, 30, 38), 'left': (30, 26, 36, 38), 'back': (36, 26, 44, 38)}
ARMS = (44, 22, 60, 30)   # the crossed arms' sides
ARMS_MID = (40, 38, 56, 46)  # the middle bar of the crossed arms


def cap(c, colour, band=None, height=3, brim=None):
    """A close cap: the top and the upper rows of the headwear box (the face stays bare)."""
    c.fill(*HAT['top'], colour)
    for face in ('right', 'front', 'left', 'back'):
        x0, y0, x1, y1 = HAT[face]
        c.fill(x0, y0, x1, y0 + height, colour)
        if band:
            c.hline(x0, x1, y0 + height - 1, band)
    if brim:
        x0, y0, x1, y1 = HAT['front']
        c.hline(x0, x1, y0 + height, brim)


def jacket(c, colour, noise=0.08):
    for face in JACKET.values():
        c.fill(*face, colour, noise)


def belt(c, colour, buckle=None, row=8):
    for name in ('right', 'front', 'left', 'back'):
        x0, y0, x1, y1 = JACKET[name]
        c.hline(x0, x1, y0 + row, colour)
    if buckle:
        x0, y0, x1, y1 = JACKET['front']
        c.put(x0 + 3, y0 + row, buckle)
        c.put(x0 + 4, y0 + row, buckle)


def apron(c, colour, top=3, bottom=18, pocket=None):
    x0, y0, x1, y1 = JACKET['front']
    c.fill(x0 + 1, y0 + top, x1 - 1, y0 + bottom, colour)
    # straps over the shoulders
    c.vline(x0 + 1, y0, y0 + top, shade(colour, 0.85))
    c.vline(x1 - 2, y0, y0 + top, shade(colour, 0.85))
    if pocket:
        c.fill(x0 + 2, y0 + top + 6, x1 - 2, y0 + top + 9, pocket, 0.03)
    for y in range(y0 + top, y0 + bottom):  # side seams
        c.put(x0 + 1, y, shade(colour, 0.8))
        c.put(x1 - 2, y, shade(colour, 0.8))


def sleeves(c, colour):
    c.fill(*ARMS, colour)
    c.fill(*ARMS_MID, colour)


def builder(seed):
    c = Canvas(64, 64, seed)
    straw, straw_dark = (214, 178, 92), (160, 126, 58)
    cap(c, straw, band=(122, 74, 40), height=3, brim=straw_dark)       # straw hat with a leather band
    for x in range(36, 64):  # wider brim: a darker row under the crown all round
        if 8 + 3 < 18:
            c.put(x, 11, straw_dark) if c.px[x, 11][3] == 0 else None
    jacket(c, (110, 120, 136))                                        # blue-grey work smock
    apron(c, (176, 150, 112), top=4, bottom=17, pocket=(140, 116, 84)) # canvas apron
    belt(c, (92, 58, 32), buckle=(200, 190, 120), row=9)
    x0, y0, _, _ = JACKET['front']
    # a hammer hanging at the belt and a folding rule in the pocket
    c.vline(x0 + 6, y0 + 10, y0 + 15, (120, 82, 46))
    c.put(x0 + 5, y0 + 10, (150, 150, 158)); c.put(x0 + 7, y0 + 10, (150, 150, 158))
    c.hline(x0 + 2, x0 + 4, y0 + 10, (230, 210, 120))
    sleeves(c, (110, 120, 136))
    return c.img


def lumberjack(seed):
    c = Canvas(64, 64, seed)
    red, dark, light = (166, 40, 36), (52, 22, 22), (196, 64, 52)
    cap(c, (60, 44, 34), band=(90, 66, 50), height=3)                   # knitted brown cap
    for name, (x0, y0, x1, y1) in list(JACKET.items()) + [('arms', ARMS), ('mid', ARMS_MID)]:
        for x in range(x0, x1):
            for y in range(y0, y1):
                check = ((x - x0) // 2 + (y - y0) // 2) % 2
                line = (x - x0) % 4 == 1 or (y - y0) % 4 == 1
                col = dark if check and line else red if not line else light if not check else dark
                c.put(x, y, shade(col, 1 + c.rnd.uniform(-0.06, 0.06)))
    belt(c, (70, 44, 26), buckle=(170, 170, 170), row=10)
    x0, y0, _, _ = JACKET['back']
    # an axe slung on the back
    c.vline(x0 + 4, y0 + 2, y0 + 14, (130, 92, 52))
    for dy in range(3):
        c.hline(x0 + 5, x0 + 7, y0 + 2 + dy, (176, 176, 184))
    return c.img


def miner(seed):
    c = Canvas(64, 64, seed)
    helmet, rim = (88, 84, 76), (60, 56, 50)
    cap(c, helmet, band=rim, height=4, brim=rim)
    x0, y0, _, _ = HAT['front']
    for (dx, dy, col) in ((3, 1, (250, 214, 90)), (4, 1, (250, 214, 90)), (3, 2, (230, 160, 50)), (4, 2, (230, 160, 50)),
                          (3, 0, (255, 240, 170)), (4, 0, (255, 240, 170))):
        c.put(x0 + dx, y0 + dy, col)                                  # a candle lamp on the helmet
    jacket(c, (72, 70, 74))
    apron(c, (96, 72, 52), top=3, bottom=18, pocket=(78, 58, 42))       # leather apron
    for _ in range(26):                                               # coal dust
        x = c.rnd.randrange(0, 28); y = c.rnd.randrange(44, 62)
        if c.px[x, y][3]:
            c.put(x, y, shade(c.px[x, y], 0.6))
    belt(c, (40, 34, 30), buckle=(150, 150, 150), row=9)
    sleeves(c, (72, 70, 74))
    return c.img


def apprentice(seed):
    c = Canvas(64, 64, seed)
    cap(c, (56, 92, 150), band=(40, 66, 110), height=2)               # small blue cap
    jacket(c, (150, 120, 92))
    apron(c, (226, 222, 210), top=3, bottom=16, pocket=(200, 196, 182)) # white apron
    x0, y0, _, _ = JACKET['front']
    c.vline(x0 + 3, y0 + 9, y0 + 12, (120, 82, 46)); c.put(x0 + 3, y0 + 8, (40, 40, 40))  # a pencil
    belt(c, (110, 76, 46), buckle=(210, 180, 80), row=8)
    sleeves(c, (150, 120, 92))
    return c.img


def guard(seed):
    c = Canvas(64, 64, seed)
    iron, iron_d = (176, 178, 184), (120, 122, 130)
    cap(c, iron, band=iron_d, height=4, brim=iron_d)                  # kettle helmet
    x0, y0, _, _ = HAT['front']
    c.vline(x0 + 3, y0, y0 + 5, iron_d); c.vline(x0 + 4, y0, y0 + 5, iron_d)  # nose guard
    for name, (x0, y0, x1, y1) in list(JACKET.items()) + [('arms', ARMS), ('mid', ARMS_MID)]:
        for x in range(x0, x1):
            for y in range(y0, y1):
                ring = (x + (y // 2)) % 2 == 0                         # mail
                c.put(x, y, shade(iron if ring else iron_d, 1 + c.rnd.uniform(-0.05, 0.05)))
    x0, y0, x1, y1 = JACKET['front']
    c.fill(x0 + 2, y0, x1 - 2, y1, (150, 30, 34), 0.05)                # red tabard
    c.vline(x0 + 3, y0 + 3, y0 + 8, (230, 200, 90)); c.hline(x0 + 2, x0 + 5, y0 + 5, (230, 200, 90))  # gold cross
    x0, y0, x1, y1 = JACKET['back']
    c.fill(x0 + 2, y0, x1 - 2, y1, (150, 30, 34), 0.05)
    belt(c, (60, 40, 26), buckle=(220, 200, 110), row=9)
    return c.img


def carpenter(seed):
    c = Canvas(64, 64, seed)
    cap(c, (64, 104, 58), band=(44, 74, 40), height=2)                # green felt cap
    jacket(c, (176, 150, 116))
    apron(c, (124, 86, 52), top=2, bottom=17, pocket=(100, 68, 40))   # leather apron
    for _ in range(18):                                               # sawdust
        x = c.rnd.randrange(7, 13); y = c.rnd.randrange(46, 61)
        c.put(x, y, (214, 186, 130))
    x0, y0, _, _ = JACKET['front']
    c.hline(x0 + 2, x0 + 6, y0 + 9, (200, 200, 206)); c.put(x0 + 6, y0 + 9, (120, 82, 46))  # a saw in the pocket
    belt(c, (80, 52, 30), buckle=(180, 180, 180), row=10)
    sleeves(c, (176, 150, 116))
    return c.img


def storekeeper(seed):
    c = Canvas(64, 64, seed)
    x0, y0, _, _ = HAT['front']
    for dx in (0, 3, 4, 7):                                           # spectacle rims round the eyes
        c.put(x0 + dx, y0 + 4, (176, 150, 70))
    for dx in (1, 2, 5, 6):
        c.put(x0 + dx, y0 + 3, (176, 150, 70))
    jacket(c, (226, 220, 204))                                        # white shirt
    for name in ('front', 'right', 'left', 'back'):                   # green waistcoat
        x0, y0, x1, y1 = JACKET[name]
        c.fill(x0, y0, x1, y0 + 9, (52, 96, 66), 0.05)
    x0, y0, x1, y1 = JACKET['front']
    c.vline(x0 + 3, y0, y0 + 9, (226, 220, 204)); c.vline(x0 + 4, y0, y0 + 9, (226, 220, 204))
    for dy in (2, 5, 8):
        c.put(x0 + 2, y0 + dy, (220, 190, 70)); c.put(x0 + 5, y0 + dy, (220, 190, 70))  # brass buttons
    c.put(x0 + 6, y0 + 11, (220, 190, 70)); c.put(x0 + 6, y0 + 12, (190, 160, 60))      # keys on the belt
    belt(c, (60, 40, 26), buckle=(220, 200, 110), row=10)
    sleeves(c, (226, 220, 204))
    return c.img


def carrier(seed):
    c = Canvas(64, 64, seed)
    cap(c, (150, 120, 80), band=(120, 92, 60), height=2)
    jacket(c, (128, 112, 90))
    x0, y0, x1, y1 = JACKET['back']                                   # big pack on the back
    c.fill(x0, y0 + 1, x1, y0 + 15, (122, 84, 50), 0.07)
    c.hline(x0, x1, y0 + 4, (90, 60, 34)); c.hline(x0, x1, y0 + 10, (90, 60, 34))
    c.put(x0 + 3, y0 + 7, (200, 190, 120)); c.put(x0 + 4, y0 + 7, (200, 190, 120))
    x0, y0, x1, y1 = JACKET['front']                                  # its straps over the chest
    c.vline(x0 + 1, y0, y0 + 10, (90, 60, 34)); c.vline(x1 - 2, y0, y0 + 10, (90, 60, 34))
    belt(c, (90, 60, 34), buckle=(170, 170, 170), row=10)
    sleeves(c, (128, 112, 90))
    return c.img


OUTFITS = {'builder': builder, 'lumberjack': lumberjack, 'miner': miner, 'apprentice': apprentice,
           'guard': guard, 'carpenter': carpenter, 'storekeeper': storekeeper, 'carrier': carrier}


# ---------------------------------------------------------------- job blocks (16x16)

def tex16(seed):
    return Canvas(16, 16, seed)


def chopping_block_top():
    img = vanilla('block/oak_log_top')
    c = tex16(1); c.img.paste(img)
    for x0, y0, dx, dy, n in ((2, 5, 1, 0, 9), (4, 10, 1, -1, 6), (9, 3, 0, 1, 7)):   # axe cuts
        for i in range(n):
            x, y = x0 + dx * i, y0 + dy * i
            if 0 <= x < 16 and 0 <= y < 16:
                c.put(x, y, shade(c.px[x, y], 0.45))
                if 0 <= y + 1 < 16 and dx:
                    c.put(x, y + 1, shade(c.px[x, y + 1], 1.25))
    for _ in range(6):
        x, y = c.rnd.randrange(16), c.rnd.randrange(16)
        c.put(x, y, (196, 160, 104))                                   # chips
    return c.img


def miners_bench_top():
    base = vanilla('block/smooth_stone')
    c = tex16(2); c.img.paste(base)
    for x in range(16):                                                # a dark slate working surface
        for y in range(16):
            c.put(x, y, shade(c.px[x, y], 0.62))
    for x, y, col in ((3, 3, (216, 160, 120)), (4, 3, (190, 140, 100)), (11, 4, (40, 40, 40)), (12, 4, (60, 60, 60)),
                      (12, 11, (240, 210, 80)), (5, 12, (110, 220, 220)), (6, 12, (90, 190, 190))):
        c.put(x, y, col)                                               # bits of ore: iron, coal, gold, diamond
    for i in range(10):                                                # chisel scratches
        c.put(2 + i, 8 + (i % 2), shade(c.px[2 + i, 8 + (i % 2)], 1.35))
    for x in range(16):                                                # oak trim round the edge
        for y in (0, 15):
            c.put(x, y, (110, 84, 52)); c.put(y, x, (110, 84, 52))
    return c.img


def miners_bench_side():
    c = tex16(3); c.img.paste(vanilla('block/cobblestone'))
    for x in range(16):
        c.put(x, 0, (110, 84, 52)); c.put(x, 1, (88, 66, 40))
    for x in (2, 13):                                                  # hanging tools: a pick and a shovel
        c.vline(x, 4, 12, (120, 82, 46))
    c.hline(1, 4, 4, (170, 170, 178)); c.put(13, 12, (170, 170, 178)); c.put(13, 13, (170, 170, 178))
    return c.img


def builders_table_top():
    c = tex16(4); c.img.paste(vanilla('block/oak_planks'))
    for x in range(3, 14):                                             # a blueprint
        for y in range(2, 13):
            c.put(x, y, shade((54, 96, 168), 1 + c.rnd.uniform(-0.05, 0.05)))
    white = (224, 232, 245)
    c.hline(5, 12, 9, white); c.vline(5, 6, 10, white); c.vline(11, 6, 10, white)   # a little house
    for i in range(4):
        c.put(5 + i, 6 - i, white); c.put(11 - i, 6 - i, white)
    c.vline(8, 7, 10, white)
    for x in range(3, 14, 2):
        c.put(x, 12, white)                                            # a dimension line
    c.hline(1, 15, 14, (228, 196, 92)); c.put(14, 14, (40, 40, 40))  # a folding rule
    return c.img


def storekeeper_desk_top():
    c = tex16(5); c.img.paste(vanilla('block/dark_oak_planks'))
    for x in range(2, 10):                                             # an open ledger
        for y in range(3, 12):
            c.put(x, y, (232, 224, 200) if x != 6 else (190, 180, 160))
    for y in range(4, 11, 2):
        c.hline(3, 6, y, (120, 116, 110)); c.hline(7, 9, y, (120, 116, 110))
    c.fill(11, 3, 14, 6, (30, 30, 36), 0.02); c.put(12, 2, (240, 240, 240))  # an inkwell and quill
    for x, y in ((11, 10), (12, 11), (13, 10)):
        c.put(x, y, (232, 196, 70)); c.put(x, y + 1, (190, 150, 50))   # coins
    return c.img


def apprentice_workbench_top():
    c = tex16(6); c.img.paste(vanilla('block/crafting_table_top'))
    c.hline(10, 15, 2, (170, 170, 178)); c.vline(12, 3, 6, (120, 82, 46))      # a hammer
    c.hline(2, 6, 13, (200, 200, 206)); c.put(1, 13, (120, 82, 46))            # a file
    return c.img


BLOCKS = {'chopping_block_top': chopping_block_top, 'miners_bench_top': miners_bench_top,
          'miners_bench_side': miners_bench_side, 'builders_table_top': builders_table_top,
          'storekeeper_desk_top': storekeeper_desk_top, 'apprentice_workbench_top': apprentice_workbench_top}


def main():
    os.makedirs(OUT_ENTITY, exist_ok=True)
    os.makedirs(OUT_BLOCK, exist_ok=True)
    for i, (name, fn) in enumerate(OUTFITS.items()):
        fn(100 + i).save(os.path.join(OUT_ENTITY, f'{name}.png'))
    for name, fn in BLOCKS.items():
        fn().save(os.path.join(OUT_BLOCK, f'{name}.png'))
    print('wrote', len(OUTFITS), 'outfits and', len(BLOCKS), 'block textures')


if __name__ == '__main__':
    main()
