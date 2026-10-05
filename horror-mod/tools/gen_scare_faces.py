#!/usr/bin/env python3
"""Jumpscare faces in plain 8x8 pixel style, blown up to fill the screen. A Minecraft face, but wrong."""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'echohorror', 'textures', 'gui')
R = random.Random(7)
K, W, SK = (0, 0, 0), (255, 255, 255), (222, 214, 206)


def face(grid):
    """grid: 8 strings of 8 chars; legend below."""
    pal = {'.': SK, 'k': K, 'w': W, 'h': (18, 14, 14), 'r': (120, 10, 10), 'b': (150, 112, 86), 'd': (60, 40, 30),
           'g': (176, 170, 160), 'n': (8, 8, 10)}
    img = Image.new('RGB', (8, 8))
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            img.putpixel((x, y), pal[c])
    return img


def blow(img, name, glitch=True):
    big = img.resize((256, 256), Image.NEAREST)
    px = big.load()
    for x in range(256):  # film grain
        for y in range(256):
            r, g, b = px[x, y]
            n = R.randint(-18, 18)
            px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)))
    if glitch:  # a couple of torn scanlines
        for _ in range(4):
            y0, hgt, off = R.randint(0, 240), R.randint(3, 12), R.randint(-24, 24)
            band = big.crop((0, y0, 256, y0 + hgt))
            big.paste(band, (off, y0))
    big.save(os.path.join(OUT, name))
    print('  ', name)


# 1: the shade — nothing but white eyes
blow(face(['nnnnnnnn', 'nnnnnnnn', 'nnnnnnnn', 'nnwnnwnn', 'nnnnnnnn', 'nnnnnnnn', 'nnnnnnnn', 'nnnnnnnn']), 'face1.png')
# 2: the watcher / the crawler — skin with two holes, no mouth
blow(face(['dddddddd', 'gggggggg', 'gggggggg', 'gkkggkkg', 'gkkggkkg', 'gggggggg', 'gggggggg', 'gggggggg']), 'face2.png')
# 3: the mimic — your face, eyes gone, the smile too wide
blow(face(['dddddddd', 'dbbbbbbd', 'bbbbbbbb', 'bkkbbkkb', 'bkwbbwkb', 'bkbbbbkb', 'bkkkkkkb', 'bbbbbbbb']), 'face3.png')
# 4: the silent one — hair, white eyes, sewn mouth
blow(face(['hhhhhhhh', 'hhhhhhhh', 'h......h', 'h......h', 'h.w..w.h', 'h......h', 'h.rkrk.h', 'h......h']), 'face4.png')
