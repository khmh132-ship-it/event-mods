"""Minimal structure-template (.nbt) builder used by the fitting-room tools."""
import nbtlib
from nbtlib.tag import Compound, Int, List, String

DATA_VERSION = 3465  # Minecraft 1.20.1


class Template:
    def __init__(self):
        self.blocks = {}  # (x, y, z) -> (name, props, nbt)

    def set(self, x, y, z, name, nbt=None, **props):
        if ':' not in name:
            name = 'minecraft:' + name
        self.blocks[(x, y, z)] = (name, {k: str(v).lower() for k, v in props.items()}, nbt)

    def fill(self, x1, y1, z1, x2, y2, z2, name, **props):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **props)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def bounds(self):
        xs, ys, zs = zip(*self.blocks)
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def size(self):
        (a, b, c), (x, y, z) = self.bounds()
        return x - a + 1, y - b + 1, z - c + 1

    def to_nbt(self):
        (ox, oy, oz), _ = self.bounds()
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda e: (e[0][1], e[0][2], e[0][0])):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = Compound({'Name': String(name)})
                if props:
                    entry['Properties'] = Compound({k: String(v) for k, v in props.items()})
                palette.append(entry)
            b = Compound({'pos': List[Int]([Int(x - ox), Int(y - oy), Int(z - oz)]), 'state': Int(index[key])})
            if nbt is not None:
                b['nbt'] = nbt
            blocks.append(b)
        return nbtlib.File({
            'DataVersion': Int(DATA_VERSION),
            'size': List[Int]([Int(v) for v in self.size()]),
            'palette': List[Compound](palette),
            'blocks': List[Compound](blocks),
            'entities': List[Compound]([]),
        }, gzipped=True)

    def save(self, path):
        self.to_nbt().save(path)
