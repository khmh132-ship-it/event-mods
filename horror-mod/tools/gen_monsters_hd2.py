#!/usr/bin/env python3
"""Detailed (256x256) skins for the crawler, the silent one and the mimic's mask. Overwrites the 64x64 ones."""
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'echohorror', 'textures', 'entity')
R = random.Random(4013)
S = 4
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
    return {k: (x * S, y * S, a * S, b * S) for k, (x, y, a, b) in f.items()}


def new():
    return Image.new('RGBA', (64 * S, 64 * S), (0, 0, 0, 0))


def cl(v):
    return max(0, min(255, int(v)))


def skin_fill(img, part, col, noise=10, dark_edges=0.35, bottom=0.3, which=None):
    """Noisy fill, darker at the edges of each face and towards the bottom: reads as sunken, unhealthy flesh."""
    px = img.load()
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for i in range(x, x + w):
            for j in range(y, y + h):
                ex = min(i - x, x + w - 1 - i) / max(1, w / 2)
                k = (1 - dark_edges * (1 - min(1, ex * 1.6))) * (1 - bottom * (j - y) / max(1, h))
                n = R.randint(-noise, noise)
                px[i, j] = (cl(col[0] * k + n), cl(col[1] * k + n), cl(col[2] * k + n), 255)


def veins(img, part, col, count, which=None, alpha=140):
    d = ImageDraw.Draw(img, 'RGBA')
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for _ in range(count):
            cx, cy = R.randint(x, x + w - 1), R.randint(y, y + h - 1)
            for _ in range(R.randint(5, 12)):
                nx = min(x + w - 1, max(x, cx + R.randint(-2, 2)))
                ny = min(y + h - 1, max(y, cy + R.randint(0, 3)))
                d.line((cx, cy, nx, ny), fill=col + (alpha,))
                cx, cy = nx, ny


def blotch(img, part, col, count, rmin, rmax, alpha=150, which=None):
    d = ImageDraw.Draw(img, 'RGBA')
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for _ in range(count):
            r = R.randint(rmin, rmax)
            cx, cy = R.randint(x, x + w - 1), R.randint(y, y + h - 1)
            d.ellipse((max(x, cx - r), max(y, cy - r), min(x + w - 1, cx + r), min(y + h - 1, cy + r)), fill=col + (alpha,))


def drip(img, x, y, n, col=(95, 4, 6), alpha=255, step=1):
    px = img.load()
    for j in range(n):
        if 0 <= x < img.width and 0 <= y + j * step < img.height:
            a = cl(alpha * (1 - 0.6 * j / max(1, n)))
            px[x, y + j * step] = col + (a,)
            if j > n * 0.6 and R.random() < 0.4 and x + 1 < img.width:
                px[x + 1, y + j * step] = col + (a // 2,)


def socket(d, g, cx, cy, rx, ry, rim=(90, 20, 22), pupil=True, tear_dir=1):
    """A hollow eye: ragged black hole, raw rim, a pinprick of light deep inside, a black tear."""
    pts = []
    import math
    for k in range(14):
        a = k / 14 * 2 * math.pi
        r1 = 1 + R.uniform(-0.18, 0.18)
        pts.append((cx + math.cos(a) * rx * r1, cy + math.sin(a) * ry * r1))
    d.polygon([(x + (1 if x > cx else -1), y + (1 if y > cy else -1)) for x, y in pts], fill=rim + (255,))
    d.polygon(pts, fill=(4, 2, 2, 255))
    if pupil:
        d.rectangle((cx - 1, cy, cx, cy + 1), fill=(240, 240, 230, 255))
        if g is not None:
            g.rectangle((cx - 1, cy, cx, cy + 1), fill=(255, 255, 255, 255))
    # black tears
    for t in range(R.randint(1, 3)):
        sx = int(cx + R.randint(-rx + 1, rx - 1))
        sy = int(cy + ry * tear_dir)
        for j in range(R.randint(5, 13)):
            d.point((sx, sy + j * tear_dir), fill=(10, 4, 4, 230))
            if R.random() < 0.3:
                sx += R.choice([-1, 1])


def out(img, name):
    img.save(os.path.join(ROOT, name))
    print('  ', name)


# ============================================================================================ Ползун
def crawler():
    img = new()
    flesh = (118, 124, 118)
    for p in BASE:
        skin_fill(img, p, flesh, 12, 0.45, 0.35)
    for p in BASE:
        veins(img, p, (55, 40, 75), 6)
    blotch(img, 'body', (70, 80, 60), 10, 2, 6, 110)
    blotch(img, 'body', (90, 20, 20), 4, 1, 3, 170)
    d = ImageDraw.Draw(img, 'RGBA')
    # ribs pushing through
    bx, by, bw, bh = faces('body')['front']
    for k in range(7):
        y = by + 6 + k * 5
        d.arc((bx + 1, y - 5, bx + bw // 2 + 1, y + 6), 195, 345, fill=(60, 64, 58, 255), width=2)
        d.arc((bx + bw // 2 - 1, y - 5, bx + bw - 1, y + 6), 195, 345, fill=(60, 64, 58, 255), width=2)
        d.arc((bx + 2, y - 4, bx + bw // 2, y + 5), 200, 340, fill=(150, 155, 145, 200), width=1)
    # a spine like a saw
    sx, sy, sw, sh = faces('body')['back']
    for k in range(11):
        d.polygon([(sx + sw // 2 - 3, sy + 2 + k * 4), (sx + sw // 2 + 3, sy + 2 + k * 4), (sx + sw // 2, sy + 5 + k * 4)], fill=(150, 152, 140, 255))
    # rags for trousers, black rotten hands and feet
    for p in ['rleg', 'lleg']:
        for name, (x, y, w, h) in faces(p).items():
            for i in range(x, x + w):
                top = R.randint(int(h * 0.3), int(h * 0.55))
                for j in range(y, y + top):
                    if name != 'bottom':
                        img.putpixel((i, j), (34 + R.randint(-5, 5), 32, 30, 255))
    for p in ['rarm', 'larm', 'rleg', 'lleg']:
        for name, (x, y, w, h) in faces(p).items():
            if name == 'top':
                continue
            for j in range(y + h - 16, y + h):
                for i in range(x, x + w):
                    if R.random() < (j - (y + h - 16)) / 16 + 0.25:
                        img.putpixel((i, j), (18, 12, 12, 255))
    # the face (rendered upside down): two empty sockets and a mouth that opens far too wide
    g = new()
    gd = ImageDraw.Draw(g, 'RGBA')
    fx, fy, fw, fh = faces('head')['front']
    socket(d, gd, fx + 9, fy + 11, 5, 6)
    socket(d, gd, fx + 22, fy + 11, 5, 6)
    my = fy + 19
    d.polygon([(fx + 4, my), (fx + 28, my), (fx + 25, my + 11), (fx + 16, my + 13), (fx + 7, my + 11)], fill=(12, 0, 0, 255))
    d.polygon([(fx + 8, my + 3), (fx + 24, my + 3), (fx + 22, my + 9), (fx + 10, my + 9)], fill=(70, 6, 8, 255))
    x = fx + 5
    while x < fx + 27:  # upper and lower teeth, too many
        d.polygon([(x, my), (x + 2, my), (x + 1, my + R.randint(3, 5))], fill=(210, 200, 165, 255))
        gd.polygon([(x, my), (x + 2, my), (x + 1, my + 3)], fill=(60, 58, 50, 255))
        x += 3
    x = fx + 8
    while x < fx + 24:
        d.polygon([(x, my + 11), (x + 2, my + 11), (x + 1, my + 11 - R.randint(3, 5))], fill=(210, 200, 165, 255))
        x += 3
    for _ in range(6):
        drip(img, fx + R.randint(6, 26), my + 12, R.randint(3, 8))
    out(img, 'crawler.png')
    out(g, 'crawler_eyes.png')


# ============================================================================================ Немая
def silent():
    img = new()
    skin = (196, 200, 204)
    for p in BASE:
        skin_fill(img, p, skin, 7, 0.3, 0.15)
    for p in ['rarm', 'larm', 'rleg', 'lleg', 'head']:
        veins(img, p, (90, 100, 150), 4, alpha=120)
    blotch(img, 'rleg', (110, 80, 120), 4, 2, 5, 120)
    blotch(img, 'lleg', (110, 80, 120), 4, 2, 5, 120)
    blotch(img, 'rarm', (110, 80, 120), 3, 2, 4, 120)
    # long grey fingertips, dirty feet
    for p in ['rarm', 'larm']:
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y + h - 9, y + h):
                for i in range(x, x + w):
                    if name != 'top' and (i - x) % 4 != 3:
                        img.putpixel((i, j), (150, 152, 165, 255))
    for p in ['rleg', 'lleg']:
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y + h - 8, y + h):
                for i in range(x, x + w):
                    if name != 'top':
                        img.putpixel((i, j), (58, 50, 44, 255))
    # hospital gown, filthy
    gown = (186, 188, 176)
    skin_fill(img, 'body', gown, 7, 0.2, 0.2)
    skin_fill(img, 'jacket', gown, 7, 0.2, 0.25)
    for p in ['rarm', 'larm']:
        for name, (x, y, w, h) in faces(p).items():
            for j in range(y, y + 16):
                for i in range(x, x + w):
                    img.putpixel((i, j), (gown[0] + R.randint(-7, 7), gown[1] + R.randint(-7, 7), gown[2] + R.randint(-7, 7), 255))
    for p in ['rpants', 'lpants']:
        for name, (x, y, w, h) in faces(p).items():
            if name in ('top', 'bottom'):
                continue
            for j in range(y, y + 16):
                for i in range(x, x + w):
                    img.putpixel((i, j), (gown[0] - 12 + R.randint(-6, 6), gown[1] - 12, gown[2] - 14, 255))
    blotch(img, 'body', (110, 85, 55), 8, 2, 6, 140)
    blotch(img, 'body', (100, 10, 12), 5, 1, 4, 200)
    d = ImageDraw.Draw(img, 'RGBA')
    bx, by, bw, bh = faces('body')['front']
    for _ in range(5):
        drip(img, bx + R.randint(4, bw - 4), by + R.randint(0, 10), R.randint(8, 20), (100, 10, 12), 200)
    # head: hair everywhere except a slit in front
    hair = (12, 10, 12)
    skin_fill(img, 'head', hair, 4, 0.0, 0.0, which=['top', 'back', 'left', 'right'])
    g = new()
    gd = ImageDraw.Draw(g, 'RGBA')
    fx, fy, fw, fh = faces('head')['front']
    # one dead white eye, the other one only a glint under the hair
    d.ellipse((fx + 17, fy + 9, fx + 26, fy + 15), fill=(70, 40, 45, 255))
    d.ellipse((fx + 18, fy + 10, fx + 25, fy + 14), fill=(236, 238, 232, 255))
    gd.ellipse((fx + 18, fy + 10, fx + 25, fy + 14), fill=(220, 225, 220, 255))
    d.line((fx + 18, fy + 16, fx + 25, fy + 17), fill=(110, 80, 110, 255))
    socket(d, None, fx + 10, fy + 12, 4, 3, pupil=False)
    # the mouth, sewn shut with black thread; it bleeds where the needle went in
    my = fy + 23
    d.line((fx + 9, my, fx + 23, my), fill=(70, 30, 34, 255), width=2)
    for x in range(fx + 10, fx + 23, 3):
        d.line((x, my - 3, x + 1, my + 3), fill=(5, 5, 5, 255), width=1)
        d.point((x, my - 4), fill=(140, 20, 20, 255))
        drip(img, x + 1, my + 3, R.randint(2, 6), (110, 10, 12), 230)
    # strands of hair over the face
    for i in range(fx, fx + fw):
        if 5 <= i - fx <= 27:
            if R.random() < 0.18:  # a few strands across the face
                for j in range(fy, fy + R.randint(6, 20)):
                    img.putpixel((i, j), hair + (255,))
            elif i - fx <= 26:
                for j in range(fy, fy + R.randint(2, 5)):  # fringe
                    img.putpixel((i, j), hair + (255,))
            continue
        length = R.randint(fh - 4, fh)
        for j in range(fy, fy + length):
            img.putpixel((i, j), (hair[0] + R.randint(0, 12), hair[1] + R.randint(0, 9), hair[2] + R.randint(0, 9), 255))
    hx, hy, hw, hh = faces('hat')['front']
    for i in range(hx, hx + hw):
        if 6 <= i - hx <= 26:
            continue
        for j in range(hy, hy + R.randint(hh // 2, hh)):
            img.putpixel((i, j), hair + (255,))
    for name in ['back', 'left', 'right']:
        x, y, w, h = faces('hat')[name]
        for i in range(x, x + w):
            for j in range(y, y + R.randint(h - 8, h)):
                img.putpixel((i, j), (hair[0] + R.randint(0, 8), hair[1], hair[2], 255))
    out(img, 'silent.png')
    out(g, 'silent_eyes.png')


# ============================================================================================ Двойник: a mask over your own face
def mimic_mask():
    m = new()
    d = ImageDraw.Draw(m, 'RGBA')
    g = new()
    gd = ImageDraw.Draw(g, 'RGBA')
    fx, fy, fw, fh = faces('head')['front']
    # cracks across the borrowed skin
    for _ in range(7):
        x, y = fx + R.randint(2, 29), fy + R.randint(2, 29)
        for _ in range(R.randint(4, 9)):
            nx, ny = x + R.randint(-3, 3), y + R.randint(-3, 3)
            d.line((x, y, nx, ny), fill=(30, 8, 8, 200))
            x, y = nx, ny
    socket(d, gd, fx + 9, fy + 13, 4, 5)
    socket(d, gd, fx + 22, fy + 13, 4, 5)
    # the smile, cut from ear to ear
    my = fy + 23
    d.polygon([(fx + 1, my - 4), (fx + 6, my), (fx + 16, my + 2), (fx + 26, my), (fx + 31, my - 4), (fx + 27, my + 4),
               (fx + 16, my + 7), (fx + 5, my + 4)], fill=(10, 0, 0, 255))
    x = fx + 6
    while x < fx + 26:
        d.polygon([(x, my), (x + 2, my), (x + 1, my + 3)], fill=(220, 212, 180, 255))
        d.polygon([(x + 1, my + 5), (x + 3, my + 5), (x + 2, my + 2)], fill=(220, 212, 180, 255))
        x += 3
    for _ in range(5):
        drip(m, fx + R.randint(4, 28), my + 6, R.randint(3, 8))
    out(m, 'mimic_mask.png')
    out(g, 'mimic_eyes.png')


if __name__ == '__main__':
    crawler()
    silent()
    mimic_mask()
