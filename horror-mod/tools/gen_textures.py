#!/usr/bin/env python3
"""Procedural pixel-art textures for the ECHO horror mod (entities, GUI, items, blocks)."""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'echohorror', 'textures')
R = random.Random(1986)


def out(img, rel):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print('  ', rel)


def jitter(c, amt=12):
    r, g, b = c[:3]
    a = c[3] if len(c) > 3 else 255
    d = R.randint(-amt, amt)
    return (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)


# ---------------------------------------------------------------- skins
# (u, v, w, h, d) box definitions in the 64x64 player layout
PARTS = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4),
    'larm': (32, 48, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}


def faces(part):
    u, v, w, h, d = PARTS[part]
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }


def paint_part(img, part, color, amt=10, which=None):
    px = img.load()
    for name, (x, y, w, h) in faces(part).items():
        if which and name not in which:
            continue
        for i in range(x, x + w):
            for j in range(y, y + h):
                px[i, j] = jitter(color, amt) if callable(color) is False else color(i - x, j - y, name)


def front(part):
    return faces(part)['front']


def new_skin():
    return Image.new('RGBA', (64, 64), (0, 0, 0, 0))


def skin_watcher():
    img = new_skin()
    pale = (214, 212, 205)
    suit = (18, 18, 22)
    paint_part(img, 'head', pale, 6)
    for p in ['body', 'rarm', 'larm', 'rleg', 'lleg']:
        paint_part(img, p, suit, 5)
    px = img.load()
    # long pale fingers at arm bottoms
    for p in ['rarm', 'larm']:
        for name in ['front', 'back', 'left', 'right']:
            x, y, w, h = faces(p)[name]
            for i in range(x, x + w):
                for j in range(y + h - 3, y + h):
                    px[i, j] = jitter(pale, 6)
    # face: no features, two deep holes and a stitched mouth
    fx, fy, _, _ = front('head')
    for (ex, ey) in [(1, 3), (5, 3)]:
        for i in range(2):
            for j in range(2):
                px[fx + ex + i, fy + ey + j] = (6, 4, 4, 255)
        px[fx + ex, fy + ey + 2] = (60, 50, 50, 255)  # tear streak
        px[fx + ex, fy + ey + 3] = (90, 80, 80, 255)
    for i in range(2, 6):
        px[fx + i, fy + 6] = (120, 110, 105, 255)
    px[fx + 2, fy + 5] = px[fx + 4, fy + 5] = (140, 130, 125, 255)
    # white shirt line + red tie
    bx, by, _, _ = front('body')
    for j in range(0, 12):
        px[bx + 3, by + j] = (200, 200, 200, 255)
        px[bx + 4, by + j] = (110, 10, 12, 255) if j < 8 else (200, 200, 200, 255)
    out(img, 'entity/watcher.png')
    eyes = new_skin()
    pe = eyes.load()
    for (ex, ey) in [(1, 3), (5, 3)]:
        pe[fx + ex + (1 if ex == 1 else 0), fy + ey + 1] = (255, 255, 255, 255)
    out(eyes, 'entity/watcher_eyes.png')


def skin_shade():
    img = new_skin()
    for p in ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']:
        paint_part(img, p, (6, 6, 8), 4)
    for p in ['hat', 'jacket', 'rsleeve', 'lsleeve', 'rpants', 'lpants']:
        px = img.load()
        for name, (x, y, w, h) in faces(p).items():
            for i in range(x, x + w):
                for j in range(y, y + h):
                    if R.random() < 0.35:
                        px[i, j] = (0, 0, 0, R.randint(120, 220))
    out(img, 'entity/shade.png')
    eyes = new_skin()
    pe = eyes.load()
    fx, fy, _, _ = front('head')
    pe[fx + 2, fy + 4] = (255, 230, 230, 255)
    pe[fx + 5, fy + 4] = (255, 230, 230, 255)
    out(eyes, 'entity/shade_eyes.png')


def skin_crawler():
    img = new_skin()
    flesh = (168, 170, 160)
    for p in ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']:
        paint_part(img, p, flesh, 14)
    px = img.load()
    # ribs
    bx, by, _, _ = front('body')
    for j in range(2, 9, 2):
        for i in range(1, 7):
            px[bx + i, by + j] = (110, 108, 100, 255)
    for j in range(0, 12):
        px[bx + 3, by + j] = (125, 122, 115, 255)
    bx, by, _, _ = faces('body')['back']
    for j in range(0, 12):  # spine
        px[bx + 4, by + j] = (95, 90, 85, 255)
        if j % 2 == 0:
            px[bx + 3, by + j] = px[bx + 5, by + j] = (120, 115, 108, 255)
    # dirty bloody hands/feet
    for p in ['rarm', 'larm', 'rleg', 'lleg']:
        for name in ['front', 'back', 'left', 'right']:
            x, y, w, h = faces(p)[name]
            for i in range(x, x + w):
                for j in range(y + h - 4, y + h):
                    px[i, j] = jitter((70, 40, 35), 12)
    # face: big black eyes, huge mouth
    fx, fy, _, _ = front('head')
    for (ex, ey) in [(1, 2), (5, 2)]:
        for i in range(2):
            for j in range(2):
                px[fx + ex + i, fy + ey + j] = (0, 0, 0, 255)
    for i in range(1, 7):
        for j in range(5, 8):
            px[fx + i, fy + j] = (12, 2, 2, 255)
    for i in range(1, 7):
        px[fx + i, fy + 5] = (230, 225, 200, 255) if i % 2 else (12, 2, 2, 255)
        px[fx + i, fy + 7] = (230, 225, 200, 255) if not i % 2 else (12, 2, 2, 255)
    out(img, 'entity/crawler.png')


def skin_silent():
    img = new_skin()
    dress = (150, 150, 145)
    skin = (190, 188, 182)
    hair = (10, 9, 10)
    paint_part(img, 'head', hair, 4)
    paint_part(img, 'body', dress, 8)
    paint_part(img, 'rleg', dress, 8)
    paint_part(img, 'lleg', dress, 8)
    paint_part(img, 'rarm', skin, 8)
    paint_part(img, 'larm', skin, 8)
    px = img.load()
    # one eye peeking through the hair
    fx, fy, _, _ = front('head')
    for j in range(8):
        for i in range(8):
            if R.random() < 0.12:
                px[fx + i, fy + j] = jitter(skin, 10)
    px[fx + 5, fy + 4] = (240, 240, 240, 255)
    px[fx + 6, fy + 4] = (240, 240, 240, 255)
    px[fx + 5, fy + 5] = (40, 0, 0, 255)
    # hair layer long over shoulders
    for name, (x, y, w, h) in faces('hat').items():
        for i in range(x, x + w):
            for j in range(y, y + h):
                if name in ('top', 'back', 'left', 'right') or R.random() < 0.55:
                    px[i, j] = jitter(hair, 4)
    for name, (x, y, w, h) in faces('jacket').items():
        if name in ('back', 'left', 'right', 'top'):
            for i in range(x, x + w):
                for j in range(y, y + min(h, 9)):
                    px[i, j] = jitter(hair, 4)
    # dirt and stains on dress
    for p in ['body', 'rleg', 'lleg']:
        x, y, w, h = front(p)
        for _ in range(6):
            i, j = R.randint(x, x + w - 1), R.randint(y, y + h - 1)
            px[i, j] = jitter((90, 30, 28), 10)
    # black fingertips
    for p in ['rarm', 'larm']:
        for name in ['front', 'back', 'left', 'right', 'bottom']:
            x, y, w, h = faces(p)[name]
            for i in range(x, x + w):
                for j in range(y + h - 2 if name != 'bottom' else y, y + h):
                    px[i, j] = (20, 18, 18, 255)
    out(img, 'entity/silent.png')


def skin_mimic_reveal():
    img = new_skin()
    shirt = (0, 150, 150)
    jeans = (60, 60, 150)
    rot = (150, 120, 105)
    paint_part(img, 'head', rot, 18)
    paint_part(img, 'body', shirt, 15)
    paint_part(img, 'rarm', rot, 18)
    paint_part(img, 'larm', rot, 18)
    paint_part(img, 'rleg', jeans, 12)
    paint_part(img, 'lleg', jeans, 12)
    px = img.load()
    # torn shirt: flesh showing
    for p in ['body']:
        for name, (x, y, w, h) in faces(p).items():
            for i in range(x, x + w):
                for j in range(y, y + h):
                    if R.random() < 0.3:
                        px[i, j] = jitter((120, 20, 20), 25)
    fx, fy, _, _ = front('head')
    # eyes
    for (ex, ey) in [(1, 2), (5, 2)]:
        for i in range(2):
            for j in range(2):
                px[fx + ex + i, fy + ey + j] = (0, 0, 0, 255)
        for j in range(4, 8):
            px[fx + ex, fy + j] = (130 - j * 5, 0, 0, 255)
    # mouth stretched to the sides of the head
    for i in range(0, 8):
        px[fx + i, fy + 6] = (5, 0, 0, 255)
        px[fx + i, fy + 5] = (235, 230, 210, 255) if i % 2 else (5, 0, 0, 255)
        px[fx + i, fy + 7] = (235, 230, 210, 255) if not i % 2 else (5, 0, 0, 255)
    for side in ['left', 'right']:
        x, y, w, h = faces('head')[side]
        for i in range(x, x + w):
            px[i, y + 6] = (5, 0, 0, 255)
    out(img, 'entity/mimic_reveal.png')
    eyes = new_skin()
    pe = eyes.load()
    pe[fx + 2, fy + 3] = (255, 255, 255, 255)
    pe[fx + 5, fy + 3] = (255, 255, 255, 255)
    out(eyes, 'entity/mimic_eyes.png')


def skin_boss():
    img = new_skin()
    tar = (10, 8, 12)
    for p in ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']:
        paint_part(img, p, tar, 6)
    eyes = new_skin()
    pe = eyes.load()
    px = img.load()
    # eyes everywhere
    for p in ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']:
        for name, (x, y, w, h) in faces(p).items():
            n = max(1, (w * h) // 14)
            for _ in range(n):
                if w < 2 or h < 1:
                    continue
                i, j = R.randint(x, x + w - 2), R.randint(y, y + h - 1)
                px[i, j] = (230, 225, 215, 255)
                px[i + 1, j] = (230, 225, 215, 255)
                pe[i, j] = (255, 255, 255, 255)
                pe[i + 1, j] = (255, 255, 255, 255)
                pupil = i + R.randint(0, 1)
                px[pupil, j] = (0, 0, 0, 255)
                pe[pupil, j] = (0, 0, 0, 0)
    # face: vertical mouth
    fx, fy, _, _ = front('head')
    for j in range(1, 8):
        px[fx + 3, fy + j] = (90, 0, 0, 255)
        px[fx + 4, fy + j] = (150, 10, 10, 255)
        pe[fx + 3, fy + j] = (0, 0, 0, 0)
        pe[fx + 4, fy + j] = (255, 60, 40, 255) if j % 2 else (0, 0, 0, 0)
    out(img, 'entity/boss.png')
    out(eyes, 'entity/boss_eyes.png')


# ---------------------------------------------------------------- GUI
def scare_face(kind):
    S = 64
    img = Image.new('RGBA', (S, S), (0, 0, 0, 255))
    px = img.load()
    cx, cy = 32, 30
    for x in range(S):
        for y in range(S):
            dx, dy = (x - cx) / 26.0, (y - cy) / 31.0
            d = dx * dx + dy * dy
            if d < 1:
                shade = int(200 - 110 * d + R.randint(-14, 14))
                if kind == 2:
                    px[x, y] = (min(255, shade + 10), int(shade * 0.55), int(shade * 0.5), 255)
                else:
                    px[x, y] = (shade, shade, int(shade * 0.97), 255)
    dr = ImageDraw.Draw(img)
    if kind in (0, 2):
        # hollow eyes
        dr.ellipse((14, 18, 27, 33), fill=(0, 0, 0, 255))
        dr.ellipse((37, 18, 50, 33), fill=(0, 0, 0, 255))
        px[20, 25] = px[21, 25] = (255, 255, 255, 255)
        px[43, 25] = px[44, 25] = (255, 255, 255, 255)
        # mouth wide open
        dr.ellipse((18, 40, 46, 62), fill=(8, 0, 0, 255))
        for i in range(19, 46, 3):
            dr.polygon([(i, 41), (i + 3, 41), (i + 1.5, 46)], fill=(225, 220, 200, 255))
            dr.polygon([(i, 61), (i + 3, 61), (i + 1.5, 56)], fill=(225, 220, 200, 255))
        # tears of blood
        for x0 in (17, 24, 40, 47):
            L = R.randint(8, 22)
            for j in range(33, 33 + L):
                if 0 <= j < S:
                    px[x0, j] = (120 + R.randint(0, 40), 0, 0, 255)
    else:
        # faceless - only two tiny holes and cracks
        dr.rectangle((22, 26, 25, 29), fill=(0, 0, 0, 255))
        dr.rectangle((39, 26, 42, 29), fill=(0, 0, 0, 255))
        for _ in range(6):
            x, y = R.randint(12, 52), R.randint(6, 56)
            for k in range(R.randint(6, 16)):
                if 0 <= x < S and 0 <= y < S and px[x, y][0] > 40:
                    px[x, y] = (60, 55, 55, 255)
                x += R.choice([-1, 0, 1])
                y += 1
    # cracks
    for _ in range(5):
        x, y = R.randint(10, 54), R.randint(5, 20)
        for k in range(R.randint(5, 15)):
            if 0 <= x < S and 0 <= y < S and px[x, y][0] > 40:
                px[x, y] = (40, 35, 35, 255)
            x += R.choice([-1, 0, 1])
            y += 1
    big = img.resize((256, 256), Image.NEAREST)
    out(big, f'gui/face{kind + 1}.png')


def gui_misc():
    n = Image.new('RGBA', (128, 128))
    p = n.load()
    for x in range(128):
        for y in range(128):
            v = R.randint(0, 255)
            p[x, y] = (v, v, v, R.randint(0, 255) if R.random() < 0.5 else 0)
    out(n, 'gui/noise.png')
    v = Image.new('RGBA', (256, 256))
    p = v.load()
    for x in range(256):
        for y in range(256):
            d = math.hypot((x - 127.5) / 128, (y - 127.5) / 128)
            a = max(0.0, min(1.0, (d - 0.35) / 0.75))
            p[x, y] = (0, 0, 0, int(255 * a ** 1.6))
    out(v, 'gui/vignette.png')
    # journal paper
    w, h = 256, 180
    pap = Image.new('RGBA', (w, h))
    p = pap.load()
    for x in range(w):
        for y in range(h):
            base = 200 + R.randint(-10, 10)
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge < 6:
                base -= (6 - edge) * 14
            p[x, y] = (base, int(base * 0.92), int(base * 0.78), 255)
    # stains
    for _ in range(8):
        cx, cy, r = R.randint(0, w), R.randint(0, h), R.randint(6, 26)
        for x in range(max(0, cx - r), min(w, cx + r)):
            for y in range(max(0, cy - r), min(h, cy + r)):
                if (x - cx) ** 2 + (y - cy) ** 2 < r * r:
                    c = p[x, y]
                    p[x, y] = (int(c[0] * 0.9), int(c[1] * 0.86), int(c[2] * 0.8), 255)
    for _ in range(2):
        cx, cy = R.randint(10, w - 10), R.randint(10, h - 10)
        for k in range(R.randint(8, 25)):
            x, y = cx + R.randint(-12, 12), cy + R.randint(-12, 12)
            if 0 <= x < w and 0 <= y < h:
                p[x, y] = (110, 15, 15, 255)
    # center fold
    for y in range(h):
        c = p[w // 2, y]
        p[w // 2, y] = (int(c[0] * 0.7), int(c[1] * 0.7), int(c[2] * 0.7), 255)
        p[w // 2 - 1, y] = (int(c[0] * 0.85), int(c[1] * 0.85), int(c[2] * 0.85), 255)
    out(pap, 'gui/journal.png')
    note = pap.crop((0, 0, 150, 180)).resize((150, 180))
    np_ = note.load()
    for y in range(180):
        c = np_[0, y]
        np_[0, y] = np_[1, y] = (int(c[0] * 0.8), int(c[1] * 0.8), int(c[2] * 0.8), 255)
        np_[148, y] = np_[149, y] = np_[0, y]
    out(note, 'gui/note.png')


# ---------------------------------------------------------------- items & blocks
def icon(draw_fn, name, folder='item', size=16):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw_fn(img.load(), ImageDraw.Draw(img))
    out(img, f'{folder}/{name}.png')


def it_journal(p, d):
    d.rectangle((3, 1, 13, 14), fill=(74, 46, 30, 255))
    d.rectangle((4, 2, 12, 13), fill=(96, 60, 38, 255))
    d.line((3, 1, 3, 14), fill=(50, 30, 20, 255))
    d.rectangle((13, 2, 14, 13), fill=(215, 205, 180, 255))
    for y in range(3, 13, 2):
        p[14, y] = (180, 170, 150, 255)
    d.rectangle((6, 5, 10, 8), fill=(40, 25, 15, 255))
    p[7, 6] = p[9, 6] = (200, 190, 170, 255)  # eye symbol
    p[8, 7] = (120, 10, 10, 255)
    p[10, 11] = p[11, 12] = p[9, 12] = (110, 10, 10, 255)


def it_locator(p, d):
    d.rectangle((4, 5, 12, 15), fill=(60, 68, 52, 255))
    d.rectangle((5, 6, 11, 9), fill=(20, 40, 20, 255))
    for x in range(5, 12):
        p[x, 7 + (x % 3 == 0) - (x % 4 == 0)] = (90, 220, 90, 255)
    p[6, 11] = p[8, 11] = (200, 60, 40, 255)
    p[10, 11] = (40, 40, 40, 255)
    p[6, 13] = p[8, 13] = p[10, 13] = (30, 30, 30, 255)
    for y in range(0, 5):
        p[11, y] = (130, 130, 130, 255)
    p[11, 0] = (200, 40, 40, 255)
    d.line((4, 5, 4, 15), fill=(40, 46, 34, 255))


def it_flashlight(p, d):
    for i in range(10):
        x, y = 3 + i, 12 - i
        d.line((x, y, x + 1, y + 1), fill=(35, 35, 38, 255))
        p[x, y + 1] = (60, 60, 64, 255)
    d.rectangle((11, 2, 13, 4), fill=(80, 80, 85, 255))
    p[13, 2] = p[12, 2] = p[13, 3] = (255, 235, 150, 255)
    p[6, 9] = (190, 40, 40, 255)


def it_battery(p, d):
    d.rectangle((5, 3, 10, 14), fill=(30, 30, 30, 255))
    d.rectangle((5, 3, 10, 6), fill=(190, 120, 50, 255))
    d.rectangle((7, 1, 8, 2), fill=(170, 170, 170, 255))
    p[7, 9] = p[8, 9] = p[7, 10] = (200, 200, 200, 255)


def it_note(p, d):
    d.polygon([(3, 2), (12, 1), (13, 13), (4, 14)], fill=(215, 205, 175, 255))
    for y in range(4, 13, 2):
        for x in range(5, 12):
            if R.random() < 0.7:
                p[x, y] = (60, 50, 45, 255)
    p[11, 12] = p[10, 13] = (120, 15, 15, 255)
    p[3, 2] = (180, 170, 140, 255)


def it_page(p, d):
    d.polygon([(4, 1), (12, 3), (11, 14), (3, 13)], fill=(200, 185, 150, 255))
    d.line((5, 5, 10, 6), fill=(40, 30, 30, 255))
    d.line((5, 8, 9, 9), fill=(40, 30, 30, 255))
    p[7, 11] = p[8, 11] = (140, 20, 20, 255)


def it_tape(p, d):
    d.rectangle((1, 4, 14, 12), fill=(45, 40, 38, 255))
    d.ellipse((2, 5, 7, 10), fill=(150, 120, 80, 255))
    d.ellipse((8, 5, 13, 10), fill=(150, 120, 80, 255))
    p[4, 7] = p[10, 7] = (30, 25, 20, 255)
    d.line((2, 11, 13, 11), fill=(90, 80, 70, 255))
    p[7, 6] = (230, 230, 220, 255)


def it_pills(p, d):
    d.rectangle((5, 4, 10, 14), fill=(170, 120, 60, 200))
    d.rectangle((5, 2, 10, 4), fill=(235, 235, 235, 255))
    d.rectangle((6, 7, 9, 11), fill=(235, 230, 215, 255))
    p[7, 8] = p[8, 9] = (180, 30, 30, 255)


def it_clapper(p, d):
    d.line((8, 1, 8, 10), fill=(70, 70, 75, 255))
    d.line((9, 1, 9, 10), fill=(110, 110, 115, 255))
    d.ellipse((5, 9, 12, 15), fill=(120, 95, 50, 255))
    p[7, 11] = (200, 170, 100, 255)
    d.rectangle((7, 0, 10, 1), fill=(60, 60, 60, 255))


def it_shard(p, d):
    d.polygon([(8, 1), (12, 6), (10, 14), (5, 12), (4, 5)], fill=(160, 110, 50, 255))
    d.polygon([(8, 3), (10, 6), (9, 11), (6, 10)], fill=(210, 160, 80, 255))
    p[8, 6] = p[8, 7] = (255, 240, 200, 255)
    p[5, 12] = (40, 140, 140, 255)


def it_silence(p, d):
    d.ellipse((2, 2, 13, 13), fill=(30, 30, 40, 255))
    d.ellipse((4, 4, 11, 11), fill=(220, 220, 235, 255))
    d.ellipse((6, 6, 9, 9), fill=(255, 255, 255, 255))


def it_spawn_egg_base(p, d):
    pass


def block_tex(name, base, fn=None, noise=10):
    img = Image.new('RGBA', (16, 16))
    p = img.load()
    for x in range(16):
        for y in range(16):
            p[x, y] = jitter(base, noise)
    if fn:
        fn(p, ImageDraw.Draw(img))
    out(img, f'block/{name}.png')


def bt_console_front(p, d):
    d.rectangle((0, 0, 15, 15), outline=(40, 44, 40, 255))
    d.rectangle((2, 2, 13, 6), fill=(15, 30, 15, 255))
    for x in range(3, 13):
        p[x, 4 + int(1.5 * math.sin(x))] = (100, 230, 100, 255)
    for x in (3, 7, 11):
        d.ellipse((x - 1, 8, x + 1, 10), fill=(25, 25, 25, 255))
        p[x, 8] = (200, 200, 200, 255)
    p[3, 12] = (220, 40, 30, 255)
    p[5, 12] = (60, 60, 30, 255)
    d.rectangle((8, 12, 13, 13), fill=(30, 30, 30, 255))


def bt_lock_front(n):
    def f(p, d):
        d.rectangle((0, 0, 15, 15), outline=(60, 60, 64, 255))
        d.ellipse((4, 4, 11, 11), outline=(40, 30, 20, 255))
        sockets = [(7, 2), (3, 10), (11, 10)]
        for i, (x, y) in enumerate(sockets):
            d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=(15, 12, 10, 255))
            if i < n:
                d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=(200, 150, 70, 255))
                p[x, y] = (255, 240, 190, 255)
    return f


def bt_switch(on):
    def f(p, d):
        d.rectangle((2, 1, 13, 14), fill=(70, 75, 70, 255), outline=(35, 38, 35, 255))
        d.rectangle((6, 3, 9, 12), fill=(30, 30, 30, 255))
        if on:
            d.rectangle((5, 3, 10, 5), fill=(170, 170, 170, 255))
            p[11, 13] = p[12, 13] = (60, 230, 60, 255)
        else:
            d.rectangle((5, 10, 10, 12), fill=(170, 170, 170, 255))
            p[11, 13] = p[12, 13] = (230, 50, 40, 255)
        for x in range(3, 13, 3):
            p[x, 14] = (220, 190, 30, 255)
    return f


def main():
    print('Generating textures into', os.path.abspath(ROOT))
    skin_watcher()
    skin_shade()
    skin_crawler()
    skin_silent()
    skin_mimic_reveal()
    skin_boss()
    for k in range(3):
        scare_face(k)
    gui_misc()
    icon(it_journal, 'journal')
    icon(it_locator, 'locator')
    icon(it_flashlight, 'flashlight')
    icon(it_battery, 'battery')
    icon(it_note, 'note')
    icon(it_page, 'page')
    icon(it_tape, 'tape')
    icon(it_pills, 'pills')
    icon(it_clapper, 'bell_clapper')
    icon(it_shard, 'echo_shard')
    icon(it_silence, 'silence')
    block_tex('console_side', (70, 76, 68))
    block_tex('console_top', (60, 64, 58), lambda p, d: d.rectangle((0, 0, 15, 15), outline=(40, 44, 40, 255)))
    block_tex('console_front', (70, 76, 68), bt_console_front)
    block_tex('lock_side', (58, 58, 62), lambda p, d: d.rectangle((0, 0, 15, 15), outline=(40, 40, 44, 255)), 8)
    for n in range(4):
        block_tex(f'lock_front_{n}', (58, 58, 62), bt_lock_front(n), 8)
    block_tex('switch_off', (90, 92, 90), bt_switch(False), 6)
    block_tex('switch_on', (90, 92, 90), bt_switch(True), 6)


if __name__ == '__main__':
    main()
