#!/usr/bin/env python3
"""Monster skins in a plain vanilla pixel style (64x64): almost a person, but wrong. Overwrites the entity skins."""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'echohorror', 'textures', 'entity')
R = random.Random(13)

PARTS = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4),
    'larm': (32, 48, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}
BASE = ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']
FACE = (8, 8)  # head front, top-left


def faces(part):
    u, v, w, h, d = PARTS[part]
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}


def new():
    return Image.new('RGBA', (64, 64), (0, 0, 0, 0))


def fill(img, part, color, noise=5, rows=None):
    """Flat vanilla-like fill with a little pixel noise. rows=(from,to) limits to those rows of the side faces."""
    px = img.load()
    for name, (x, y, w, h) in faces(part).items():
        for i in range(x, x + w):
            for j in range(y, y + h):
                if rows and name not in ('top', 'bottom') and not (rows[0] <= j - y < rows[1]):
                    continue
                n = R.randint(-noise, noise)
                px[i, j] = tuple(max(0, min(255, c + n)) for c in color) + (255,)


def face(img, pixels):
    """pixels: {(x, y): color} on the 8x8 head front."""
    px = img.load()
    for (x, y), c in pixels.items():
        px[FACE[0] + x, FACE[1] + y] = c + (255,) if len(c) == 3 else c


def out(img, name):
    img.save(os.path.join(ROOT, name))
    print('  ', name)


BLACK = (0, 0, 0)
WHITE = (255, 255, 255)


def person(skin, shirt, pants, shoes=(40, 40, 40)):
    img = new()
    fill(img, 'head', skin)
    fill(img, 'body', shirt)
    for a in ('rarm', 'larm'):
        fill(img, a, skin)
        fill(img, a, shirt, rows=(0, 4))  # short sleeves
    for l in ('rleg', 'lleg'):
        fill(img, l, pants)
        fill(img, l, shoes, rows=(10, 12))
    return img


def hair(img, color, fringe=1):
    px = img.load()
    for name, (x, y, w, h) in faces('head').items():
        if name == 'top':
            rows = h
        elif name == 'front':
            rows = fringe
        elif name == 'back':
            rows = h
        else:
            rows = 3
        for i in range(x, x + w):
            for j in range(y, y + rows):
                px[i, j] = color + (255,)


# ============================================================================================ Ползун: no face, two holes
def crawler():
    img = person((176, 170, 160), (86, 92, 96), (44, 44, 58))
    hair(img, (40, 34, 30))
    skin = (176, 170, 160)
    f = {(x, y): skin for x in range(8) for y in range(1, 8)}
    f.update({(1, 4): BLACK, (2, 4): BLACK, (5, 4): BLACK, (6, 4): BLACK,
              (1, 5): BLACK, (2, 5): BLACK, (5, 5): BLACK, (6, 5): BLACK})
    face(img, f)
    out(img, 'crawler.png')
    g = new()  # nothing glows: you see it only when it's already close
    out(g, 'crawler_eyes.png')


# ============================================================================================ Немая: white eyes, nothing else
def silent():
    skin = (222, 214, 206)
    img = person(skin, (214, 214, 206), skin, skin)
    for a in ('rarm', 'larm'):
        fill(img, a, skin)
    hair(img, (18, 14, 14), fringe=2)
    # hair hangs down the sides of the face
    f = {(x, y): skin for x in range(1, 7) for y in range(2, 8)}
    f.update({(0, y): (18, 14, 14) for y in range(2, 8)})
    f.update({(7, y): (18, 14, 14) for y in range(2, 8)})
    f.update({(2, 4): WHITE, (5, 4): WHITE})
    face(img, f)
    out(img, 'silent.png')
    g = new()
    g.putpixel((FACE[0] + 2, FACE[1] + 4), WHITE + (255,))
    g.putpixel((FACE[0] + 5, FACE[1] + 4), WHITE + (255,))
    out(g, 'silent_eyes.png')


# ============================================================================================ Двойник: your face, but the eyes are gone
def mimic():
    # a mask drawn over the copied player's own skin when it gives itself away
    m = new()
    f = {}
    for (x, y) in [(1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)]:
        f[(x, y)] = BLACK
    for x in range(1, 7):
        f[(x, 6)] = BLACK
    f[(1, 5)] = BLACK
    f[(6, 5)] = BLACK
    face(m, f)
    out(m, 'mimic_mask.png')
    g = new()
    g.putpixel((FACE[0] + 2, FACE[1] + 4), WHITE + (255,))
    g.putpixel((FACE[0] + 5, FACE[1] + 4), WHITE + (255,))
    out(g, 'mimic_eyes.png')
    # fallback when there is no skin to wear
    img = person((176, 140, 110), (60, 70, 80), (40, 40, 50))
    hair(img, (50, 36, 26))
    face(img, {(x, y): (176, 140, 110) for x in range(8) for y in range(1, 8)})
    face(img, f)
    out(img, 'mimic_reveal.png')


# ============================================================================================ Наблюдатель: a shape with eyes
def watcher():
    img = new()
    for p in BASE:
        fill(img, p, (8, 8, 10), 2)
    face(img, {(2, 4): WHITE, (5, 4): WHITE})
    out(img, 'watcher.png')
    g = new()
    g.putpixel((FACE[0] + 2, FACE[1] + 4), WHITE + (255,))
    g.putpixel((FACE[0] + 5, FACE[1] + 4), WHITE + (255,))
    out(g, 'watcher_eyes.png')


# ============================================================================================ Отголосок: eyes everywhere
def boss():
    img = new()
    g = new()
    for p in BASE:
        fill(img, p, (10, 9, 12), 3)
    px, pg = img.load(), g.load()
    for p in BASE:
        for name, (x, y, w, h) in faces(p).items():
            if name in ('top', 'bottom') and p != 'head':
                continue
            for _ in range(max(1, w * h // 18)):
                i, j = R.randint(x, x + w - 2), R.randint(y, y + h - 1)
                if p == 'head' and name == 'front':
                    continue
                for k in (0, 1) if R.random() < 0.5 else (0,):
                    px[i + k, j] = (230, 230, 230, 255)
                    pg[i + k, j] = (255, 255, 255, 255)
    # the main face: six eyes, no mouth
    f = {(x, y): (10, 9, 12) for x in range(8) for y in range(8)}
    eyes = [(1, 2), (6, 2), (2, 4), (5, 4), (1, 6), (6, 6)]
    for e in eyes:
        f[e] = WHITE
    face(img, f)
    for (x, y) in eyes:
        pg[FACE[0] + x, FACE[1] + y] = (255, 255, 255, 255)
    out(img, 'boss.png')
    out(g, 'boss_eyes.png')


if __name__ == '__main__':
    crawler()
    silent()
    mimic()
    watcher()
    boss()
