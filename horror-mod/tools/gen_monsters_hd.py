#!/usr/bin/env python3
"""HD (256x256, 4x the player layout) monster skins. Run after gen_textures.py; overwrites the entity skins."""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'echohorror', 'textures', 'entity')
R = random.Random(666)
S = 4  # scale over the 64x64 layout

PARTS = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4),
    'larm': (32, 48, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}
BASE = ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']


def faces(part):
    u, v, w, h, d = PARTS[part]
    f = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
         'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}
    return {k: (x * S, y * S, w_ * S, h_ * S) for k, (x, y, w_, h_) in f.items()}


def new():
    return Image.new('RGBA', (64 * S, 64 * S), (0, 0, 0, 0))


def clamp(v):
    return max(0, min(255, int(v)))


def shade_fill(img, part, color, noise=10, vgrad=0.0, which=None, alpha=255):
    """Fill a part's faces with a noisy colour; vgrad darkens towards the bottom of each face."""
    px = img.load()
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for i in range(x, x + w):
            for j in range(y, y + h):
                t = (j - y) / max(1, h - 1)
                k = 1 - vgrad * t
                n = R.randint(-noise, noise)
                px[i, j] = (clamp(color[0] * k + n), clamp(color[1] * k + n), clamp(color[2] * k + n), alpha)


def stain(img, part, color, count, rmin=1, rmax=4, which=None, alpha=200):
    d = ImageDraw.Draw(img, 'RGBA')
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for _ in range(count):
            r = R.randint(rmin, rmax)
            cx, cy = R.randint(x, x + w - 1), R.randint(y, y + h - 1)
            d.ellipse((max(x, cx - r), max(y, cy - r), min(x + w - 1, cx + r), min(y + h - 1, cy + r)), fill=color + (alpha,))


def drip(img, x, y, length, color=(110, 8, 8)):
    px = img.load()
    for j in range(length):
        a = 255 - j * 120 // max(1, length)
        px[x, y + j] = color + (a,)
        if j > length // 2 and R.random() < 0.3:
            px[x + 1, y + j] = color + (a // 2,)


def veins(img, part, color, count, which=None):
    d = ImageDraw.Draw(img, 'RGBA')
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for _ in range(count):
            cx, cy = R.randint(x, x + w - 1), R.randint(y, y + h - 1)
            for _ in range(R.randint(4, 9)):
                nx = min(x + w - 1, max(x, cx + R.randint(-3, 3)))
                ny = min(y + h - 1, max(y, cy + R.randint(-1, 4)))
                d.line((cx, cy, nx, ny), fill=color + (150,))
                cx, cy = nx, ny


def out(img, name):
    img.save(os.path.join(ROOT, name))
    print('  ', name)


def teeth_row(d, x0, x1, y, down=True, color=(225, 215, 185)):
    x = x0
    while x < x1:
        w = R.randint(2, 4)
        h = R.randint(3, 7)
        if down:
            d.polygon([(x, y), (x + w, y), (x + w // 2, y + h)], fill=color + (255,))
        else:
            d.polygon([(x, y), (x + w, y), (x + w // 2, y - h)], fill=color + (255,))
        x += w + R.randint(0, 1)


# ============================================================================================ Ползун
def crawler():
    img = new()
    flesh = (150, 152, 138)
    for p in BASE:
        shade_fill(img, p, flesh, 12, 0.35)
    veins(img, 'body', (70, 60, 90), 6)
    veins(img, 'head', (70, 60, 90), 4)
    stain(img, 'body', (90, 95, 70), 10, 2, 6)
    d = ImageDraw.Draw(img, 'RGBA')
    # ribs
    bx, by, bw, bh = faces('body')['front']
    for k in range(6):
        y = by + 8 + k * 5
        d.arc((bx + 2, y - 6, bx + bw // 2 + 2, y + 6), 200, 340, fill=(70, 72, 60, 255), width=2)
        d.arc((bx + bw // 2 - 2, y - 6, bx + bw - 2, y + 6), 200, 340, fill=(70, 72, 60, 255), width=2)
    # spine
    sx, sy, sw, sh = faces('body')['back']
    for k in range(10):
        d.ellipse((sx + sw // 2 - 3, sy + 2 + k * 4, sx + sw // 2 + 3, sy + 5 + k * 4), fill=(105, 108, 95, 255))
    # rotting black hands and feet
    for p in ['rarm', 'larm', 'rleg', 'lleg']:
        for name, (x, y, w, h) in faces(p).items():
            if name in ('top',):
                continue
            for j in range(y + h - 14, y + h):
                for i in range(x, x + w):
                    if R.random() < (j - (y + h - 14)) / 14 + 0.2:
                        img.putpixel((i, j), (25, 18, 18, 255))
    # the face is a mouth: a vertical split from brow to chin
    fx, fy, fw, fh = faces('head')['front']
    cx = fx + fw // 2
    d.polygon([(cx, fy + 2), (cx + 7, fy + 10), (cx + 6, fy + 24), (cx, fy + 31), (cx - 6, fy + 24), (cx - 7, fy + 10)], fill=(20, 0, 0, 255))
    d.polygon([(cx, fy + 5), (cx + 4, fy + 11), (cx + 3, fy + 23), (cx, fy + 28), (cx - 3, fy + 23), (cx - 4, fy + 11)], fill=(70, 4, 6, 255))
    for y in range(fy + 7, fy + 27, 3):  # teeth along both lips
        d.polygon([(cx - 7, y), (cx - 7, y + 3), (cx - 3, y + 1)], fill=(220, 210, 175, 255))
        d.polygon([(cx + 7, y), (cx + 7, y + 3), (cx + 3, y + 1)], fill=(220, 210, 175, 255))
    d.line((cx - 8, fy + 9, cx - 8, fy + 26), fill=(120, 30, 30, 255), width=1)
    d.line((cx + 8, fy + 9, cx + 8, fy + 26), fill=(120, 30, 30, 255), width=1)
    for _ in range(5):
        drip(img, cx + R.randint(-4, 4), fy + 28, R.randint(3, 6))
    bx, by, bw, bh = faces('body')['front']
    for _ in range(7):
        drip(img, bx + bw // 2 + R.randint(-8, 8), by, R.randint(8, 22))
    out(img, 'crawler.png')
    # what glows in the dark: the teeth, faintly
    g = new()
    gd = ImageDraw.Draw(g, 'RGBA')
    for y in range(fy + 7, fy + 27, 3):
        gd.polygon([(cx - 7, y), (cx - 7, y + 3), (cx - 3, y + 1)], fill=(90, 85, 75, 255))
        gd.polygon([(cx + 7, y), (cx + 7, y + 3), (cx + 3, y + 1)], fill=(90, 85, 75, 255))
    out(g, 'crawler_eyes.png')


# ============================================================================================ Немая
def silent():
    img = new()
    skin = (226, 222, 214)
    for p in BASE:
        shade_fill(img, p, skin, 6, 0.1)
    veins(img, 'rleg', (120, 120, 160), 3)
    veins(img, 'lleg', (120, 120, 160), 3)
    stain(img, 'rleg', (130, 100, 120), 3, 2, 4, alpha=120)  # bruises
    stain(img, 'lleg', (130, 100, 120), 3, 2, 4, alpha=120)
    # dirty bare feet
    for p in ['rleg', 'lleg']:
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y + h - 6, y + h):
                for i in range(x, x + w):
                    if name != 'top':
                        img.putpixel((i, j), (70, 60, 50, 255))
    # hospital gown on the body and upper arms, down to the knees
    gown = (196, 198, 186)
    shade_fill(img, 'body', gown, 7, 0.15)
    shade_fill(img, 'jacket', gown, 7, 0.2)
    for p in ['rarm', 'larm']:
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y, y + min(h, 14)):
                for i in range(x, x + w):
                    img.putpixel((i, j), (gown[0] + R.randint(-6, 6), gown[1] + R.randint(-6, 6), gown[2] + R.randint(-6, 6), 255))
    for p in ['rpants', 'lpants']:  # gown hem over the thighs
        for name, (x, y, w, h) in faces(p).items():
            if name in ('top', 'bottom'):
                continue
            for j in range(y, y + 14):
                for i in range(x, x + w):
                    img.putpixel((i, j), (gown[0] - 10 + R.randint(-6, 6), gown[1] - 10, gown[2] - 12, 255))
    stain(img, 'body', (120, 90, 60), 6, 2, 5, alpha=150)
    stain(img, 'body', (110, 20, 20), 3, 1, 3, alpha=200)
    d = ImageDraw.Draw(img, 'RGBA')
    bx, by, bw, bh = faces('body')['front']
    d.text((bx + 6, by + 6), 'Н-3', fill=(60, 60, 70, 255))
    # long fingers: grey-blue tips
    for p in ['rarm', 'larm']:
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y + h - 5, y + h):
                for i in range(x, x + w):
                    if (i - x) % 4 != 3:
                        img.putpixel((i, j), (170, 170, 190, 255))
    # black hair everywhere on the head except the lower front
    hair = (14, 11, 12)
    shade_fill(img, 'head', hair, 4, 0.0, which=['top', 'back', 'left', 'right'])
    fx, fy, fw, fh = faces('head')['front']
    for i in range(fx, fx + fw):
        for j in range(fy, fy + fh):
            img.putpixel((i, j), (226 + R.randint(-6, 4), 222 + R.randint(-6, 4), 214 + R.randint(-6, 4), 255))
    # one eye visible between the strands (her left), mouth sewn shut
    ex, ey = fx + 21, fy + 13
    d.ellipse((ex - 4, ey - 3, ex + 4, ey + 3), fill=(30, 25, 25, 255))
    d.ellipse((ex - 1, ey - 1, ex + 1, ey + 1), fill=(235, 235, 225, 255))
    d.line((ex - 4, ey + 4, ex + 3, ey + 6), fill=(150, 130, 140, 255))  # dark circles
    my = fy + 24
    d.line((fx + 9, my, fx + 23, my), fill=(80, 40, 45, 255), width=2)
    for x in range(fx + 10, fx + 23, 3):
        d.line((x, my - 3, x + 1, my + 3), fill=(30, 20, 20, 255))
        img.putpixel((x, my - 3), (110, 30, 30, 255))
    for name, (x, y, w, h) in faces('head').items():  # hair strands over the face
        if name != 'front':
            continue
        for i in range(x, x + w):
            if 17 <= i - x <= 25:
                continue  # the gap
            top = R.randint(h // 2 + 4, h + 4)
            for j in range(y, min(y + h, y + top)):
                img.putpixel((i, j), (hair[0] + R.randint(0, 10), hair[1] + R.randint(0, 8), hair[2] + R.randint(0, 8), 255))
    # hat layer: hair hanging further down past the chin on the sides
    hx, hy, hw, hh = faces('hat')['front']
    for i in range(hx, hx + hw):
        if 15 <= i - hx <= 27:
            continue
        for j in range(hy, hy + R.randint(hh // 2, hh)):
            img.putpixel((i, j), (hair[0], hair[1], hair[2], 255))
    for name in ['back', 'left', 'right']:
        x, y, w, h = faces('hat')[name]
        for i in range(x, x + w):
            for j in range(y, y + R.randint(h - 6, h)):
                img.putpixel((i, j), (hair[0] + R.randint(0, 8), hair[1], hair[2], 255))
    out(img, 'silent.png')
    g = new()
    g.putpixel((ex, ey), (255, 255, 250, 255))
    g.putpixel((ex + 1, ey), (200, 200, 190, 255))
    g.putpixel((ex, ey - 1), (200, 200, 190, 255))
    out(g, 'silent_eyes.png')


# ============================================================================================ Двойник (раскрытый)
def mimic():
    img = new()
    raw = (150, 40, 42)
    for p in BASE:
        shade_fill(img, p, raw, 18, 0.25)
    # a quilted worker's jacket and trousers, torn
    jacket = (48, 54, 62)
    for p in ['body', 'jacket', 'rarm', 'larm', 'rsleeve', 'lsleeve']:
        for name, (x, y, w, h) in faces(p).items():
            for i in range(x, x + w):
                for j in range(y, y + h):
                    if R.random() < 0.85 and not (p in ('rarm', 'larm') and j > y + h - 10):
                        q = 8 if (j - y) % 8 < 2 else 0
                        img.putpixel((i, j), (jacket[0] - q + R.randint(-6, 6), jacket[1] - q + R.randint(-6, 6), jacket[2] - q + R.randint(-6, 6), 255))
    for p in ['rleg', 'lleg']:
        shade_fill(img, p, (40, 40, 44), 6, 0.3)
    stain(img, 'body', (120, 15, 18), 8, 2, 6)
    stain(img, 'rarm', (120, 15, 18), 3, 2, 5)
    # the face: what's left of a borrowed one
    shade_fill(img, 'head', (130, 34, 36), 18, 0.2)
    fx, fy, fw, fh = faces('head')['front']
    d = ImageDraw.Draw(img, 'RGBA')
    # patches of someone's skin, peeling
    for (ax, ay, aw, ah) in [(2, 1, 12, 9), (20, 3, 10, 7), (3, 18, 6, 6)]:
        d.polygon([(fx + ax, fy + ay), (fx + ax + aw, fy + ay + 2), (fx + ax + aw - 2, fy + ay + ah), (fx + ax + 1, fy + ay + ah - 1)],
                  fill=(205, 170, 150, 255))
    eyes = [(9, 9), (22, 8), (15, 4), (26, 15)]  # too many, not where they should be
    for (x, y) in eyes:
        d.ellipse((fx + x - 3, fy + y - 2, fx + x + 3, fy + y + 2), fill=(235, 230, 210, 255))
        d.ellipse((fx + x - 1, fy + y - 1, fx + x + 1, fy + y + 1), fill=(10, 10, 10, 255))
    # the smile: ear to ear
    my = fy + 21
    d.chord((fx + 1, my - 6, fx + fw - 2, my + 8), 0, 180, fill=(15, 0, 0, 255))
    teeth_row(d, fx + 3, fx + fw - 4, my + 1, True)
    teeth_row(d, fx + 5, fx + fw - 6, my + 7, False)
    for _ in range(6):
        drip(img, fx + R.randint(6, fw - 6), my + 8, R.randint(3, 6))
    out(img, 'mimic_reveal.png')
    g = new()
    for (x, y) in eyes:
        g.putpixel((fx + x, fy + y), (255, 240, 200, 255))
        g.putpixel((fx + x - 1, fy + y), (160, 140, 110, 255))
    out(g, 'mimic_eyes.png')


# ============================================================================================ Отголосок
def boss():
    img = new()
    for p in BASE + ['hat', 'jacket', 'rsleeve', 'lsleeve', 'rpants', 'lpants']:
        shade_fill(img, p, (14, 12, 16), 6, 0.0, alpha=255 if p in BASE else 0)
    g = new()
    d = ImageDraw.Draw(img, 'RGBA')
    gd = ImageDraw.Draw(g, 'RGBA')
    # every face it has taken, pressing out from inside
    for p in BASE:
        for name, (x, y, w, h) in faces(p).items():
            if name in ('top', 'bottom') and p != 'head':
                continue
            n = max(1, (w * h) // 260)
            for _ in range(n):
                fw_, fh_ = R.randint(9, 14), R.randint(11, 16)
                if w < fw_ + 2 or h < fh_ + 2:
                    continue
                cx, cy = R.randint(x, x + w - fw_ - 1), R.randint(y, y + h - fh_ - 1)
                pale = R.randint(150, 200)
                d.ellipse((cx, cy, cx + fw_, cy + fh_), fill=(pale, pale - 8, pale - 14, 255))
                e1, e2 = (cx + fw_ // 3, cy + fh_ // 3), (cx + 2 * fw_ // 3, cy + fh_ // 3)
                for (ex, ey) in (e1, e2):
                    d.ellipse((ex - 1, ey - 1, ex + 1, ey + 1), fill=(5, 0, 0, 255))
                    gd.point((ex, ey), fill=(255, 60, 50, 255))
                mo = (cx + fw_ // 2, cy + 2 * fh_ // 3 + 1)
                d.ellipse((mo[0] - 2, mo[1] - 1, mo[0] + 2, mo[1] + 3), fill=(10, 0, 0, 255))
    # the main face: hollow, wide, crying black
    fx, fy, fw, fh = faces('head')['front']
    d.rectangle((fx, fy, fx + fw - 1, fy + fh - 1), fill=(190, 185, 178, 255))
    for (ex, ey) in [(fx + 9, fy + 12), (fx + 22, fy + 12)]:
        d.ellipse((ex - 4, ey - 4, ex + 4, ey + 4), fill=(0, 0, 0, 255))
        d.line((ex, ey + 4, ex - 1, ey + 18), fill=(0, 0, 0, 255), width=2)
        gd.ellipse((ex - 1, ey - 1, ex + 1, ey + 1), fill=(255, 40, 30, 255))
    d.ellipse((fx + 11, fy + 21, fx + 20, fy + 30), fill=(0, 0, 0, 255))
    out(img, 'boss.png')
    out(g, 'boss_eyes.png')


# ============================================================================================ Наблюдатель
def watcher():
    img = new()
    pale = (214, 212, 205)
    for p in BASE:
        shade_fill(img, p, (16, 16, 20), 5, 0.1)
    shade_fill(img, 'head', pale, 6, 0.1)
    d = ImageDraw.Draw(img, 'RGBA')
    bx, by, bw, bh = faces('body')['front']
    d.polygon([(bx + 10, by), (bx + 22, by), (bx + 16, by + 14)], fill=(200, 200, 200, 255))
    d.polygon([(bx + 15, by + 2), (bx + 17, by + 2), (bx + 18, by + 30), (bx + 16, by + 33), (bx + 14, by + 30)], fill=(110, 8, 10, 255))
    for p in ['rarm', 'larm']:  # long pale fingers
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y + h - 10, y + h):
                for i in range(x, x + w):
                    if name != 'top' and (i - x) % 4 != 3:
                        img.putpixel((i, j), (pale[0] - 10, pale[1] - 10, pale[2] - 10, 255))
    fx, fy, fw, fh = faces('head')['front']
    eyes = [(fx + 9, fy + 13), (fx + 22, fy + 13)]
    for (ex, ey) in eyes:
        d.ellipse((ex - 4, ey - 5, ex + 4, ey + 5), fill=(4, 2, 2, 255))
        d.line((ex - 1, ey + 5, ex - 2, ey + 13), fill=(60, 50, 50, 200), width=1)
    d.line((fx + 8, fy + 25, fx + 24, fy + 25), fill=(110, 100, 98, 255), width=1)  # a mouth that was stitched
    for x in range(fx + 9, fx + 24, 3):
        d.line((x, fy + 23, x, fy + 27), fill=(60, 50, 50, 255))
    out(img, 'watcher.png')
    g = new()
    for (ex, ey) in eyes:
        g.putpixel((ex, ey), (255, 255, 255, 255))
    out(g, 'watcher_eyes.png')


if __name__ == '__main__':
    crawler()
    silent()
    mimic()
    boss()
    watcher()
