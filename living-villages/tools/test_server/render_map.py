import glob, io, math, struct, zlib, hashlib, sys
import nbtlib
from PIL import Image, ImageDraw

world = sys.argv[1]; out = sys.argv[2]
BOUNDS = tuple(int(v) for v in sys.argv[3].split(',')) if len(sys.argv) > 3 else None
top = {}  # (x,z) -> (y, name)

def chunks(path):
    data = open(path, 'rb').read()
    for i in range(1024):
        off = struct.unpack('>I', data[i*4:i*4+4])[0]
        if not off: continue
        sector = (off >> 8) * 4096
        length, comp = struct.unpack('>IB', data[sector:sector+5])
        raw = zlib.decompress(data[sector+5:sector+4+length])
        yield nbtlib.File.parse(io.BytesIO(raw))

for path in glob.glob(f'{world}/region/*.mca'):
    for c in chunks(path):
        cx, cz = int(c['xPos']), int(c['zPos'])
        for s in sorted(c['sections'], key=lambda s: -int(s['Y'])):
            bs = s.get('block_states')
            if bs is None: continue
            pal = [str(p['Name']).replace('minecraft:', '') for p in bs['palette']]
            if pal == ['air']: continue
            sy = int(s['Y']) * 16
            if len(pal) == 1:
                idx = lambda i: 0
            else:
                bits = max(4, math.ceil(math.log2(len(pal)))); per = 64 // bits; longs = [int(v) & ((1<<64)-1) for v in bs['data']]
                idx = lambda i: (longs[i // per] >> ((i % per) * bits)) & ((1 << bits) - 1)
            for i in range(4096):
                name = pal[idx(i)]
                if name in ('air', 'cave_air', 'light'): continue
                x, z, y = i & 15, (i >> 4) & 15, i >> 8
                key = (cx*16+x, cz*16+z)
                if key not in top or top[key][0] < sy + y:
                    top[key] = (sy + y, name)

COL = {'grass_block': (95,159,53), 'dirt_path': (148,121,65), 'cobblestone': (122,122,122), 'stone_bricks': (120,120,120),
       'water': (52,92,220), 'farmland': (100,66,40), 'wheat': (200,180,60), 'hay_block': (190,160,30), 'glass_pane': (180,220,230),
       'structure_block': (200,60,200), 'oak_sign': (240,240,0), 'oak_wall_sign': (240,240,0), 'bell': (250,210,40), 'oak_leaves': (60,120,40), 'farmland': (100,66,40), 'stone': (128,128,128), 'bedrock': (40,40,40)}
def color(n):
    if n in COL: return COL[n]
    if 'stairs' in n or 'slab' in n or 'planks' in n:
        if 'spruce' in n: return (110,80,45)
        if 'cobble' in n or 'stone' in n: return (122,122,122)
        return (170,135,80)
    if 'log' in n or 'wood' in n: return (105,80,50)
    if 'bed' in n: return (200,40,40)
    if 'fence' in n: return (150,120,70)
    h = hashlib.md5(n.encode()).digest(); return (h[0]//2+60, h[1]//2+60, h[2]//2+60)

if BOUNDS:
    x0, z0, x1, z1 = BOUNDS
    top = {k: v for k, v in top.items() if x0 <= k[0] <= x1 and z0 <= k[1] <= z1}
else:
    xs = [k[0] for k in top]; zs = [k[1] for k in top]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
S = int(sys.argv[4]) if len(sys.argv) > 4 else 3
img = Image.new('RGB', ((x1-x0+1)*S, (z1-z0+1)*S), (24,24,30))
d = ImageDraw.Draw(img)
for (x, z), (y, n) in top.items():
    r, g, b = color(n); f = max(0.45, min(1.45, 1 + (y-63)*0.06))
    d.rectangle([(x-x0)*S, (z-z0)*S, (x-x0)*S+S-1, (z-z0)*S+S-1], fill=(min(255,int(r*f)), min(255,int(g*f)), min(255,int(b*f))))
img.save(out)
print(len(top), 'columns', (x0, z0, x1, z1), img.size)
from collections import Counter
print(Counter(n for _, n in top.values()).most_common(12))
