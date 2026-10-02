"""Front and back view of each outfit on a plains villager, and the job block textures, for a quick look."""
import os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from make_textures import vanilla, OUTFITS, OUT_ENTITY, OUT_BLOCK, BLOCKS

S = 8
base = vanilla('entity/villager/villager'); typ = vanilla('entity/villager/type/plains')


def layer(img, box):
    return img.crop(box)


def figure(outfit, back=False):
    """Head over jacket over legs, front (or back) faces, with the crossed arms in front."""
    W, H = 8, 10 + 18
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for img in (base, typ, outfit):
        head = (24, 8, 32, 18) if back else (8, 8, 16, 18)
        hat = (56, 8, 64, 18) if back else (40, 8, 48, 18)
        body = (36, 26, 44, 38) if back else (22, 26, 30, 38)
        jacket = (20, 44, 28, 62) if back else (6, 44, 14, 62)
        for box, pos in ((head, (0, 0)), (hat, (0, 0)), (body, (0, 10)), (jacket, (0, 10))):
            part = img.crop(box)
            out.alpha_composite(part, pos)
        if not back:
            arms = img.crop((44, 42, 52, 46))
            out.alpha_composite(arms, (0, 13))
    return out.resize((W * S, H * S), Image.NEAREST)


names = list(OUTFITS)
sheet = Image.new('RGBA', (len(names) * 20 * S, 32 * S + 20 * S), (58, 64, 72, 255))
d = ImageDraw.Draw(sheet)
for i, n in enumerate(names):
    o = Image.open(os.path.join(OUT_ENTITY, n + '.png'))
    sheet.alpha_composite(figure(o), (i * 20 * S + S, S))
    sheet.alpha_composite(figure(o, True), (i * 20 * S + 10 * S, S))
    d.text((i * 20 * S + S, 30 * S), n, fill=(255, 255, 255, 255))
for j, b in enumerate(BLOCKS):
    t = Image.open(os.path.join(OUT_BLOCK, b + '.png')).resize((16 * 6, 16 * 6), Image.NEAREST)
    sheet.alpha_composite(t, (j * 110 + S, 33 * S))
    d.text((j * 110 + S, 33 * S + 100), b[:16], fill=(255, 255, 255, 255))
sheet.save(sys.argv[1] if len(sys.argv) > 1 else '/tmp/outfits.png')
