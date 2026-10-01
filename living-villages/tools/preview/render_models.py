"""Renders the mod's block models isometrically from their JSON and the vanilla textures, for a quick look
without starting the game.  Usage: python3 render_models.py <out.png>"""
import io
import json
import math
import os
import sys
import zipfile

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets')
CLIENT_JAR = os.path.expanduser('~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar')
SCALE = 14
jar = zipfile.ZipFile(CLIENT_JAR)
_tex = {}


def texture(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    key = f'{ns}:{path}'
    if key not in _tex:
        data = jar.read(f'assets/{ns}/textures/{path}.png')
        img = Image.open(io.BytesIO(data)).convert('RGBA')
        _tex[key] = img.crop((0, 0, img.width, img.width))  # first frame of animations
    return _tex[key]


def load_model(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    local = os.path.join(ASSETS, ns, 'models', path + '.json')
    m = json.load(open(local)) if os.path.exists(local) else json.loads(jar.read(f'assets/{ns}/models/{path}.json'))
    if 'parent' in m and not m['parent'].endswith('block/block'):
        parent = load_model(m['parent'])
        tex = dict(parent.get('textures', {}))
        tex.update(m.get('textures', {}))
        m = {**parent, **m, 'textures': tex}
        if 'elements' not in m:
            m['elements'] = parent.get('elements', [])
    return m


def resolve(tex, ref, depth=0):
    while ref.startswith('#') and depth < 10:
        ref = tex[ref[1:]]
        depth += 1
    return ref


def rotate(p, origin, axis, angle):
    a = math.radians(angle)
    x, y, z = (p[i] - origin[i] for i in range(3))
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def corners(face, f, t):
    (x1, y1, z1), (x2, y2, z2) = f, t
    return {
        'south': ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        'north': ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        'east': ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
        'west': ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        'up': ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        'down': ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
    }[face]


def default_uv(face, f, t):
    (x1, y1, z1), (x2, y2, z2) = f, t
    if face in ('north', 'south'):
        return [x1, 16 - y2, x2, 16 - y1]
    if face in ('east', 'west'):
        return [z1, 16 - y2, z2, 16 - y1]
    return [x1, z1, x2, z2]


SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.62, 'west': 0.62}


def render(model, yrot):
    tex = model.get('textures', {})
    quads = []
    for el in model.get('elements', []):
        f, t = el['from'], el['to']
        rot = el.get('rotation')
        for face, spec in el['faces'].items():
            tl, tr, bl = corners(face, f, t)
            pts = [tl, tr, bl]
            if rot:
                pts = [rotate(p, rot['origin'], rot['axis'], rot['angle']) for p in pts]
            pts = [rotate(p, (8, 8, 8), 'y', -yrot) for p in pts]
            tl, tr, bl = pts
            u = [tr[i] - tl[i] for i in range(3)]
            v = [bl[i] - tl[i] for i in range(3)]
            n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            br = tuple(tr[i] + v[i] for i in range(3))
            center = [(tl[i] + br[i]) / 2 for i in range(3)]
            flat = any(f[i] == t[i] for i in range(3))
            visible = True
            # Painter's order; flat decals (items lying on or hanging in front of a surface) go last.
            quads.append((sum(center) + (100 if flat else 0), face, tl, tr, bl, br, resolve(tex, spec['texture']),
                          spec.get('uv', default_uv(face, f, t)), visible))
    img = Image.new('RGBA', (32 * SCALE, 32 * SCALE), (0, 0, 0, 0))

    def proj(p):
        x, y, z = p
        c, s = math.cos(math.radians(30)), math.sin(math.radians(30))
        return (16 * SCALE + (x - z) * c * SCALE, 20 * SCALE + ((x + z) * s - y) * SCALE)

    for depth, face, tl, tr, bl, br, ref, uv, visible in sorted(quads, key=lambda q: q[0]):
        src = texture(ref)
        k = src.width / 16
        u0, v0, u1, v1 = [c * k for c in uv]
        flip_u, flip_v = u0 > u1, v0 > v1
        crop = src.crop((min(u0, u1), min(v0, v1), max(u0, u1), max(v0, v1)))
        if crop.width == 0 or crop.height == 0:
            continue
        if flip_u:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        if flip_v:
            crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
        shade = SHADE.get(face, 1.0)
        r, g, b, a = crop.split()
        crop = Image.merge('RGBA', [ch.point(lambda val, sh=shade: int(val * sh)) for ch in (r, g, b)] + [a])
        P0, P1, P2 = proj(tl), proj(tr), proj(bl)
        Ux, Uy = P1[0] - P0[0], P1[1] - P0[1]
        Vx, Vy = P2[0] - P0[0], P2[1] - P0[1]
        det = Ux * Vy - Vx * Uy
        if abs(det) < 1e-6:
            continue
        # output (X, Y) -> u, v in [0,1] -> texture pixel
        ia, ib = Vy / det, -Vx / det
        ic, id_ = -Uy / det, Ux / det
        w, h = crop.size
        coeffs = (w * ia, w * ib, -w * (ia * P0[0] + ib * P0[1]),
                  h * ic, h * id_, -h * (ic * P0[0] + id_ * P0[1]))
        layer = crop.transform(img.size, Image.AFFINE, coeffs, resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
        mask = Image.new('L', img.size, 0)
        ImageDraw.Draw(mask).polygon([P0, P1, proj(br), P2], fill=255)
        layer.putalpha(Image.composite(layer.split()[3], Image.new('L', img.size, 0), mask))
        img.alpha_composite(layer)
    return img


if __name__ == '__main__':
    out = sys.argv[1]
    names = sorted(os.listdir(os.path.join(ASSETS, 'livingvillages', 'models', 'block')))
    tiles = []
    for n in names:
        m = load_model('livingvillages:block/' + n[:-5])
        tiles.append((n[:-5], render(m, 180)))  # front turned towards the viewer
    W = len(tiles) * 32 * SCALE
    sheet = Image.new('RGBA', (W, 32 * SCALE + 30), (40, 44, 52, 255))
    d = ImageDraw.Draw(sheet)
    for i, (n, im) in enumerate(tiles):
        sheet.alpha_composite(im, (i * 32 * SCALE, 0))
        d.text((i * 32 * SCALE + 20, 32 * SCALE + 8), n, fill=(230, 230, 230))
    sheet.save(out)
    print('rendered', [n for n, _ in tiles])
