"""Block models of the job blocks, built from cuboids only (no flat item pictures standing in for things):
tools, ore and the like are little 3D pieces in vanilla block textures.  python3 make_models.py"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'src/main/resources/assets/livingvillages/models/block')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def box(frm, to, tex, faces=None, rot=None, uv=None):
    """A cuboid; tex is one texture key for every face or a {face: key} map."""
    e = {'from': list(frm), 'to': list(to), 'faces': {}}
    for f in faces or FACES:
        t = tex[f] if isinstance(tex, dict) else tex
        face = {'texture': '#' + t}
        if uv and f in uv:
            face['uv'] = uv[f]
        e['faces'][f] = face
    if rot:
        axis, angle, origin = rot
        e['rotation'] = {'origin': list(origin), 'axis': axis, 'angle': angle}
    return e


def legs(x0, x1, z0, z1, h, w, tex):
    return [box((x, 0, z), (x + w, h, z + w), tex) for x in (x0, x1 - w) for z in (z0, z1 - w)]


def save(name, textures, elements):
    textures = dict(textures)
    textures.setdefault('particle', next(iter(textures.values())))
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump({'parent': 'block/block', 'textures': textures, 'elements': elements}, f, indent=1)


def hammer(x, y, z, length=7):
    """A hammer lying along x on a surface at height y."""
    return [box((x, y, z), (x + length, y + 1, z + 1), 'handle'),
            box((x + length - 1, y, z - 1.5), (x + length + 1, y + 1.5, z + 2.5), 'metal')]


def main():
    os.makedirs(OUT, exist_ok=True)
    # Chopping block: a stump with an axe bitten into it.
    save('chopping_block', {
        'bark': 'minecraft:block/oak_log', 'top': 'livingvillages:block/chopping_block_top',
        'rings': 'minecraft:block/oak_log_top', 'handle': 'minecraft:block/stripped_oak_log',
        'metal': 'minecraft:block/iron_block', 'particle': 'minecraft:block/oak_log'},
        [box((2, 0, 2), (14, 10, 14), {'north': 'bark', 'south': 'bark', 'east': 'bark', 'west': 'bark',
                                       'up': 'top', 'down': 'rings'}),
         # the blade sunk into the wood, the handle sticking up at a slant
         box((7.5, 8, 4), (8.5, 12, 9), 'metal'),             # the head: half in the wood, half out
         box((7.4, 11, 5.5), (8.6, 12.5, 7.5), 'metal'),       # its thick back, where the handle goes in
         box((7.5, 12, 6), (8.5, 22, 7), 'handle', rot=('x', 22.5, (8, 12, 6.5)))])

    # Miner's bench: a stone block with an oak rim, a pickaxe and a few lumps of ore on top.
    save('miners_bench', {
        'side': 'livingvillages:block/miners_bench_side', 'top': 'livingvillages:block/miners_bench_top',
        'stone': 'minecraft:block/cobblestone', 'handle': 'minecraft:block/stripped_oak_log',
        'metal': 'minecraft:block/iron_block', 'iron': 'minecraft:block/raw_iron_block',
        'coal': 'minecraft:block/coal_block', 'gold': 'minecraft:block/raw_gold_block',
        'diamond': 'minecraft:block/diamond_block', 'particle': 'minecraft:block/cobblestone'},
        [box((0, 0, 0), (16, 14, 16), {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side',
                                       'up': 'top', 'down': 'stone'}),
         # pickaxe: handle across, the curved head as three pieces
         box((3, 14, 9), (13, 15, 10), 'handle'),
         box((11, 14, 6), (12, 15, 13), 'metal'),
         box((12, 14, 5), (13, 15, 6), 'metal'), box((12, 14, 13), (13, 15, 14), 'metal'),
         box((3, 14, 3), (5, 16, 5), 'iron'), box((6, 14, 2), (7.5, 15.5, 3.5), 'coal'),
         box((3.5, 14, 12), (5, 15.5, 13.5), 'gold'), box((7, 14, 12.5), (8, 15, 13.5), 'diamond')])

    # Builder's table: blueprint top, a mallet and a roll of plans.
    save('builders_table', {
        'planks': 'minecraft:block/oak_planks', 'top': 'livingvillages:block/builders_table_top',
        'leg': 'minecraft:block/stripped_oak_log', 'handle': 'minecraft:block/stripped_oak_log',
        'metal': 'minecraft:block/oak_log', 'paper': 'minecraft:block/white_wool',
        'particle': 'minecraft:block/oak_planks'},
        [box((0, 12, 0), (16, 15, 16), {'north': 'planks', 'south': 'planks', 'east': 'planks', 'west': 'planks',
                                        'up': 'top', 'down': 'planks'}),
         *legs(1, 15, 1, 15, 12, 2, 'leg'),
         box((2, 4, 2), (14, 5, 14), 'planks'),
         box((1, 15, 13), (12, 16.5, 14.5), 'paper'),
         *hammer(9, 15, 2.5, 5)])

    # Apprentice's workbench: a crafting top, a small hammer and an ingot.
    save('apprentice_workbench', {
        'side': 'minecraft:block/spruce_planks', 'top': 'livingvillages:block/apprentice_workbench_top',
        'leg': 'minecraft:block/stripped_spruce_log', 'handle': 'minecraft:block/stripped_oak_log',
        'metal': 'minecraft:block/iron_block', 'ingot': 'minecraft:block/gold_block',
        'particle': 'minecraft:block/spruce_planks'},
        [box((0, 12, 0), (16, 15, 16), {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side',
                                        'up': 'top', 'down': 'side'}),
         *legs(1, 15, 1, 15, 12, 2, 'leg'),
         box((2, 4, 2), (14, 5, 14), 'side'),
         box((2, 15, 11), (6, 16, 13), 'ingot'),
         *hammer(8, 15, 3, 5)])

    # Carpenter's sawhorse: a log on two trestles, a saw stuck in a cut.
    save('carpenters_sawhorse', {
        'leg': 'minecraft:block/spruce_planks', 'bark': 'minecraft:block/oak_log',
        'rings': 'minecraft:block/oak_log_top', 'metal': 'minecraft:block/iron_block',
        'handle': 'minecraft:block/stripped_oak_log', 'particle': 'minecraft:block/oak_log'},
        [box((1, 0, 3), (3, 9, 5), 'leg'), box((1, 0, 11), (3, 9, 13), 'leg'), box((1, 8, 3), (3, 10, 13), 'leg'),
         box((13, 0, 3), (15, 9, 5), 'leg'), box((13, 0, 11), (15, 9, 13), 'leg'), box((13, 8, 3), (15, 10, 13), 'leg'),
         box((0, 10, 5), (16, 15, 11), {'north': 'bark', 'south': 'bark', 'up': 'bark', 'down': 'bark',
                                        'east': 'rings', 'west': 'rings'}),
         box((6, 12, 7.75), (12, 17, 8.25), 'metal'),          # the blade, half in the cut
         box((11, 16, 7.5), (14, 18, 8.5), 'handle')])

    # Storekeeper's desk: ledger, inkwell with a quill, a little stack of coins.
    save('storekeeper_desk', {
        'wood': 'minecraft:block/dark_oak_planks', 'top': 'livingvillages:block/storekeeper_desk_top',
        'leg': 'minecraft:block/spruce_log', 'ink': 'minecraft:block/black_concrete',
        'quill': 'minecraft:block/white_wool', 'gold': 'minecraft:block/gold_block',
        'particle': 'minecraft:block/dark_oak_planks'},
        [box((0, 10, 2), (16, 13, 16), {'north': 'wood', 'south': 'wood', 'east': 'wood', 'west': 'wood',
                                        'up': 'top', 'down': 'wood'}),
         box((0, 13, 13), (16, 16, 16), 'wood'),
         *legs(1, 15, 3, 15, 10, 2, 'leg'),
         box((11, 13, 5), (13, 15, 7), 'ink'),
         box((12, 14, 5.75), (12.5, 19, 6.25), 'quill', rot=('z', 22.5, (12, 14, 6))),
         box((11, 13, 9), (13, 13.5, 11), 'gold'), box((11.5, 13.5, 9.5), (12.5, 14, 10.5), 'gold')])

    # Weapon rack: a board on two posts with a sword and a spear hung on pegs.
    save('weapon_rack', {
        'planks': 'minecraft:block/spruce_planks', 'post': 'minecraft:block/spruce_log',
        'metal': 'minecraft:block/iron_block', 'handle': 'minecraft:block/stripped_oak_log',
        'leather': 'minecraft:block/brown_wool', 'particle': 'minecraft:block/spruce_planks'},
        [box((1, 0, 14), (15, 15, 16), 'planks'),
         box((0, 0, 13), (2, 16, 16), 'post'), box((14, 0, 13), (16, 16, 16), 'post'),
         box((2, 4, 13), (14, 5, 14), 'handle'), box((2, 11, 13), (14, 12, 14), 'handle'),   # pegs
         # sword, point down
         box((4.5, 2, 12), (5.5, 11, 12.5), 'metal'),
         box((3, 11, 12), (7, 12, 12.5), 'metal'),
         box((4.5, 12, 12), (5.5, 15, 12.5), 'leather'),
         # spear
         box((10.5, 1, 12), (11.5, 13, 12.5), 'handle'),
         box((10, 13, 12), (12, 15, 12.5), 'metal')])
    print('models written')


if __name__ == '__main__':
    main()
