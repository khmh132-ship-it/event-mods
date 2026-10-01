"""Rough plains-style drafts of the Living Villages buildings that have no vanilla counterpart.

Coordinates: x = east, z = south, y = 0 is the ground layer (except where GROUND_OFFSET says otherwise).
Every entrance faces south so the drafts line up with the signs in the fitting room.
"""
import json

from nbtlib.tag import Byte, Compound, List, String

from templates import Template

# How many layers of a draft lie below the ground (the mine shaft).
GROUND_OFFSET = {'mine': 6}


def sign_nbt(*lines):
    msgs = [json.dumps({'text': l}) for l in lines] + ['{"text":""}'] * (4 - len(lines))
    text = Compound({'messages': List[String]([String(m) for m in msgs]), 'color': String('black'),
                     'has_glowing_text': Byte(0)})
    return Compound({'id': String('minecraft:sign'), 'front_text': text, 'back_text': text, 'is_waxed': Byte(0)})


def box_house(t, x0, z0, w, d, h, y0=0, lower='cobblestone', wall='oak_planks', floor='oak_planks', roof=True):
    """Walls with log corners on a cobblestone foundation, air inside, optional gabled roof (ridge along x)."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    t.fill(x0, y0, z0, x1, y0, z1, 'cobblestone')
    t.fill(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, floor)
    for y in range(y0 + 1, y0 + h + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    t.set(x, y, z, 'air')
                elif x in (x0, x1) and z in (z0, z1):
                    t.set(x, y, z, 'oak_log', axis='y')
                else:
                    t.set(x, y, z, lower if y == y0 + 1 else wall)
    if roof:
        gable_roof(t, x0, z0, x1, z1, y0 + h)


def gable_roof(t, x0, z0, x1, z1, top, stairs='oak_stairs', slab='oak_slab', gable='oak_planks'):
    k = 0
    while True:
        zn, zs, y = z0 - 1 + k, z1 + 1 - k, top + 1 + k
        if zn > zs:
            break
        if zn == zs:
            t.fill(x0 - 1, y, zn, x1 + 1, y, zn, slab, type='bottom', waterlogged='false')
            break
        t.fill(x0 - 1, y, zn, x1 + 1, y, zn, stairs, facing='south', half='bottom', shape='straight', waterlogged='false')
        t.fill(x0 - 1, y, zs, x1 + 1, y, zs, stairs, facing='north', half='bottom', shape='straight', waterlogged='false')
        for z in range(max(zn + 1, z0), min(zs - 1, z1) + 1):
            for x in (x0, x1):
                t.set(x, y, z, gable)
            for x in range(x0 + 1, x1):
                t.set(x, y, z, 'air')
        k += 1


def door(t, x, z, y=1, facing='north', wood='oak'):
    t.set(x, y, z, f'{wood}_door', facing=facing, half='lower', hinge='left', open='false', powered='false')
    t.set(x, y + 1, z, f'{wood}_door', facing=facing, half='upper', hinge='left', open='false', powered='false')


def window(t, x, y, z):
    t.set(x, y, z, 'glass_pane', north='false', south='false', east='false', west='false', waterlogged='false')


def wall_torch(t, x, y, z, facing):
    t.set(x, y, z, 'wall_torch', facing=facing)


def lumberjack():
    t = Template()
    box_house(t, 0, 0, 7, 7, 4)
    door(t, 3, 6)
    for z in (2, 4):
        window(t, 0, 2, z)
    window(t, 3, 2, 0)
    t.set(1, 1, 1, 'barrel', facing='up', open='false')
    t.set(5, 1, 1, 'crafting_table')
    t.set(1, 1, 5, 'chest', facing='east', type='single', waterlogged='false')
    t.set(5, 1, 4, 'oak_log', axis='y')  # chopping block
    wall_torch(t, 3, 3, 1, 'south')
    # Woodpile under a lean-to on the east side.
    t.fill(7, 0, 1, 8, 0, 5, 'cobblestone')
    t.fill(7, 1, 1, 8, 2, 5, 'oak_log', axis='z')
    t.fill(9, 1, 1, 9, 3, 1, 'oak_fence')
    t.fill(9, 1, 5, 9, 3, 5, 'oak_fence')
    t.fill(7, 4, 0, 9, 4, 6, 'spruce_slab', type='bottom', waterlogged='false')
    return t


def mine():
    g = GROUND_OFFSET['mine']
    t = Template()
    # Chunk of ground with ores, a ladder shaft and a small chamber at the bottom.
    t.fill(0, 0, 0, 6, g - 1, 6, 'stone')
    for pos in [(0, 1, 2), (6, 2, 4), (1, 0, 5), (5, 3, 0), (2, 4, 6)]:
        t.set(*pos, 'coal_ore')
    for pos in [(6, 1, 1), (0, 3, 4)]:
        t.set(*pos, 'iron_ore')
    t.fill(1, 1, 1, 5, 2, 5, 'air')
    t.fill(1, 0, 1, 5, 0, 5, 'cobblestone')
    for x, z in [(1, 1), (5, 1), (1, 5), (5, 5)]:
        t.fill(x, 1, z, x, 2, z, 'oak_fence')
    t.set(3, 1, 5, 'chest', facing='north', type='single', waterlogged='false')
    t.set(3, 2, 1, 'wall_torch', facing='south')
    # House on top.
    box_house(t, 0, 0, 7, 7, 4, y0=g, lower='cobblestone', wall='cobblestone')
    for y in range(3, g + 4):
        t.set(3, y, 3, 'ladder', facing='south', waterlogged='false')
        if y <= g:
            t.set(3, y, 2, 'stone' if y < g else 'cobblestone')
    for y in range(g + 1, g + 4):
        t.set(3, y, 2, 'oak_planks')
    t.set(3, g, 3, 'ladder', facing='south', waterlogged='false')
    door(t, 3, 6, y=g + 1)
    window(t, 0, g + 2, 3)
    window(t, 6, g + 2, 3)
    t.set(1, g + 1, 1, 'barrel', facing='up', open='false')
    t.set(5, g + 1, 1, 'blast_furnace', facing='south', lit='false')
    t.set(5, g + 1, 5, 'chest', facing='west', type='single', waterlogged='false')
    t.set(1, g + 1, 5, 'rail', shape='north_south', waterlogged='false')
    wall_torch(t, 1, g + 3, 3, 'east')
    return t


def warehouse():
    t = Template()
    box_house(t, 0, 0, 9, 9, 5)
    door(t, 4, 8)
    for x in (2, 6):
        window(t, x, 3, 0)
        window(t, x, 3, 8)
    for z in (1, 2, 3, 5, 6, 7):
        t.fill(1, 1, z, 1, 2, z, 'barrel', facing='east', open='false')
        t.fill(7, 1, z, 7, 2, z, 'barrel', facing='west', open='false')
    for x in (3, 5):
        t.set(x, 1, 1, 'chest', facing='south', type='single', waterlogged='false')
    t.set(4, 1, 1, 'hay_block', axis='y')
    t.set(4, 2, 1, 'hay_block', axis='y')
    t.set(4, 4, 4, 'lantern', hanging='false', waterlogged='false')
    t.set(4, 5, 4, 'oak_planks')
    return t


def builder_workshop():
    t = Template()
    box_house(t, 0, 0, 7, 9, 4)
    door(t, 3, 8)
    window(t, 0, 2, 4)
    window(t, 6, 2, 4)
    t.set(1, 1, 1, 'crafting_table')
    t.set(2, 1, 1, 'chest', facing='south', type='single', waterlogged='false')
    t.set(3, 1, 1, 'chest', facing='south', type='single', waterlogged='false')
    t.set(5, 1, 1, 'lectern', facing='south', has_book='false', powered='false')
    t.set(5, 1, 6, 'barrel', facing='up', open='false')
    wall_torch(t, 3, 3, 1, 'south')
    # Material yard and scaffolding on the west side.
    t.fill(-3, 0, 1, -1, 0, 7, 'gravel')
    t.fill(-3, 1, 1, -2, 2, 2, 'oak_planks')
    t.fill(-3, 1, 4, -2, 1, 5, 'cobblestone')
    t.fill(-3, 1, 7, -3, 4, 7, 'scaffolding', bottom='false', distance='0', waterlogged='false')
    return t


def sawmill():
    t = Template()
    t.fill(0, 0, 0, 6, 0, 8, 'oak_planks')
    for x in (0, 6):
        for z in (0, 4, 8):
            t.fill(x, 1, z, x, 4, z, 'oak_log', axis='y')
    t.fill(1, 1, 0, 5, 3, 0, 'oak_planks')  # back wall only, the rest is open
    gable_roof(t, 0, 0, 6, 8, 4)
    t.set(3, 1, 3, 'stonecutter', facing='south')
    t.set(3, 1, 5, 'stonecutter', facing='south')
    t.fill(1, 1, 2, 1, 1, 6, 'oak_log', axis='z')
    t.fill(5, 1, 2, 5, 2, 6, 'stripped_oak_log', axis='z')
    t.set(4, 1, 1, 'barrel', facing='up', open='false')
    t.set(3, 4, 4, 'lantern', hanging='true', waterlogged='false')
    return t


def barracks():
    t = Template()
    box_house(t, 0, 0, 9, 13, 4, wall='oak_planks')
    t.fill(1, 2, 0, 7, 2, 0, 'cobblestone')
    door(t, 4, 12)
    for z in (2, 5, 8):
        t.set(1, 1, z, 'red_bed', facing='west', part='head', occupied='false')
        t.set(2, 1, z, 'red_bed', facing='west', part='foot', occupied='false')
        t.set(7, 1, z, 'red_bed', facing='east', part='head', occupied='false')
        t.set(6, 1, z, 'red_bed', facing='east', part='foot', occupied='false')
        window(t, 0, 2, z)
        window(t, 8, 2, z)
    for z in (3, 6, 9):
        t.set(1, 1, z, 'chest', facing='east', type='single', waterlogged='false')
        t.set(7, 1, z, 'chest', facing='west', type='single', waterlogged='false')
    t.set(4, 1, 1, 'smithing_table')
    t.set(3, 1, 1, 'grindstone', face='floor', facing='south')
    for z in (3, 9):
        t.set(4, 3, z, 'lantern', hanging='false', waterlogged='false')
        t.set(4, 4, z, 'oak_planks')
    return t


def watchtower():
    t = Template()
    t.fill(0, 0, 0, 2, 0, 2, 'cobblestone')
    for x, z in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        t.fill(x, 1, z, x, 9, z, 'oak_log', axis='y')
    t.fill(1, 1, 0, 1, 9, 0, 'oak_planks')
    t.fill(1, 1, 1, 1, 10, 1, 'ladder', facing='south', waterlogged='false')
    t.fill(-1, 10, -1, 3, 10, 3, 'oak_planks')
    t.set(1, 10, 1, 'ladder', facing='south', waterlogged='false')
    t.set(1, 10, 0, 'oak_planks')
    for x in range(-1, 4):
        for z in range(-1, 4):
            if x in (-1, 3) or z in (-1, 3):
                t.set(x, 11, z, 'oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    for x, z in [(-1, -1), (3, -1), (-1, 3), (3, 3)]:
        t.fill(x, 11, z, x, 12, z, 'oak_log', axis='y')
    t.fill(-1, 13, -1, 3, 13, 3, 'oak_slab', type='bottom', waterlogged='false')
    t.set(1, 13, 1, 'oak_planks')
    t.set(1, 12, 1, 'lantern', hanging='true', waterlogged='false')
    t.set(1, 14, 1, 'bell', attachment='floor', facing='south', powered='false')
    return t


WALL_HEIGHT = 5  # walkway is on top of y=4


def wall_section(t, x0, length):
    for x in range(x0, x0 + length):
        t.fill(x, 0, 0, x, 5, 0, 'cobblestone')
        if x % 2 == 0:
            t.set(x, 6, 0, 'cobblestone')
        t.fill(x, 0, 1, x, 4, 2, 'cobblestone')
        t.set(x, 4, 1, 'oak_planks')
        t.set(x, 4, 2, 'oak_planks')


def wall_straight():
    t = Template()
    wall_section(t, 0, 7)
    return t


def wall_gate():
    t = Template()
    wall_section(t, 0, 7)
    t.fill(2, 1, 0, 4, 3, 2, 'air')
    for x in (1, 5):
        t.fill(x, 1, 0, x, 3, 2, 'oak_log', axis='y')
    t.fill(1, 4, 0, 5, 4, 0, 'oak_log', axis='x')
    for x in (2, 3, 4):
        t.set(x, 1, 0, 'oak_fence_gate', facing='south', in_wall='false', open='false', powered='false')
    return t


def wall_tower():
    t = Template()
    t.fill(0, 0, 0, 4, 9, 4, 'cobblestone')
    t.fill(1, 1, 1, 3, 8, 3, 'air')
    t.fill(1, 0, 1, 3, 0, 3, 'oak_planks')
    for y in range(1, 10):
        t.set(2, y, 1, 'ladder', facing='south', waterlogged='false')
    t.fill(1, 4, 1, 3, 4, 3, 'oak_planks')  # wall-walk level
    t.set(2, 4, 1, 'ladder', facing='south', waterlogged='false')
    t.fill(1, 9, 1, 3, 9, 3, 'oak_planks')
    t.set(2, 9, 1, 'ladder', facing='south', waterlogged='false')
    for x in range(5):
        for z in range(5):
            if (x in (0, 4) or z in (0, 4)) and (x + z) % 2 == 0:
                t.set(x, 10, z, 'cobblestone')
    door(t, 2, 4, facing='north')
    for x in (0, 4):
        t.fill(x, 5, 2, x, 6, 2, 'air')  # openings onto the wall walk
    for x, z in [(2, 0), (0, 2), (4, 2)]:
        t.set(x, 7, z, 'air')  # arrow slits
    t.set(2, 3, 3, 'wall_torch', facing='north')
    return t


def golem_pad():
    t = Template()
    t.fill(0, 0, 0, 4, 0, 4, 'cobblestone')
    t.fill(1, 0, 1, 3, 0, 3, 'stone_bricks')
    t.set(2, 1, 2, 'iron_block')
    t.fill(1, 2, 2, 3, 2, 2, 'iron_block')
    t.set(0, 1, 4, 'anvil', facing='east')
    for x, z in [(0, 0), (4, 0), (4, 4)]:
        t.set(x, 1, z, 'cobblestone_wall', up='true', north='none', south='none', east='none', west='none',
              waterlogged='false')
        t.set(x, 2, z, 'lantern', hanging='false', waterlogged='false')
    return t


def town_hall():
    t = Template()
    box_house(t, 0, 0, 11, 11, 5)
    for x in (0, 10):
        t.fill(x, 1, 5, x, 5, 5, 'oak_log', axis='y')
    for z in (0, 10):
        t.fill(5, 1, z, 5, 5, z, 'oak_log', axis='y')
    t.set(5, 1, 10, 'air')
    door(t, 5, 10)
    for x in (2, 3, 7, 8):
        window(t, x, 3, 0)
        window(t, x, 3, 10)
    for z in (2, 3, 7, 8):
        window(t, 0, 3, z)
        window(t, 10, 3, z)
    t.fill(1, 1, 1, 9, 2, 1, 'bookshelf')
    t.set(5, 1, 2, 'lectern', facing='south', has_book='false', powered='false')
    t.fill(3, 1, 5, 7, 1, 5, 'oak_fence')
    t.fill(3, 2, 5, 7, 2, 5, 'oak_slab', type='bottom', waterlogged='false')  # table
    for x in (3, 5, 7):
        t.set(x, 1, 4, 'oak_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
        t.set(x, 1, 6, 'oak_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
    for x, z in [(2, 8), (8, 8), (5, 5)]:
        t.set(x, 4, z, 'lantern', hanging='true', waterlogged='false')
        t.set(x, 5, z, 'oak_planks')
    # Bell on a post in front of the hall.
    t.fill(5, 0, 11, 5, 0, 12, 'dirt_path')
    t.fill(7, 1, 12, 7, 3, 12, 'oak_log', axis='y')
    t.fill(7, 4, 12, 7, 4, 13, 'oak_planks')
    t.set(7, 3, 13, 'bell', attachment='ceiling', facing='south', powered='false')
    return t


def tavern():
    t = Template()
    box_house(t, 0, 0, 9, 11, 8)
    t.fill(1, 4, 1, 7, 4, 9, 'oak_planks')  # second floor
    t.fill(1, 5, 1, 7, 8, 9, 'air')
    for y in range(1, 5):
        t.set(7, y, 1, 'ladder', facing='south', waterlogged='false')
    door(t, 4, 10)
    for z in (3, 7):
        for y in (2, 6):
            window(t, 0, y, z)
            window(t, 8, y, z)
    window(t, 2, 6, 10)
    window(t, 6, 6, 10)
    # Bar counter along the north wall.
    t.fill(1, 1, 3, 5, 1, 3, 'barrel', facing='up', open='false')
    t.set(2, 2, 3, 'brewing_stand', has_bottle_0='false', has_bottle_1='false', has_bottle_2='false')
    t.fill(1, 1, 1, 4, 1, 1, 'barrel', facing='south', open='false')
    t.fill(1, 2, 1, 4, 2, 1, 'barrel', facing='south', open='false')
    for x, z in [(2, 7), (6, 7)]:
        t.set(x, 1, z, 'oak_fence')
        t.set(x, 2, z, 'oak_pressure_plate', powered='false')
        t.set(x - 1, 1, z, 'oak_stairs', facing='west', half='bottom', shape='straight', waterlogged='false')
        t.set(x + 1, 1, z, 'oak_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
    t.set(4, 3, 6, 'lantern', hanging='true', waterlogged='false')
    # Guest rooms upstairs.
    for x in (1, 3, 5):
        t.set(x, 5, 8, 'white_bed', facing='north', part='head', occupied='false')
        t.set(x, 5, 9, 'white_bed', facing='north', part='foot', occupied='false')
    t.set(4, 7, 5, 'lantern', hanging='true', waterlogged='false')
    t.set(4, 8, 5, 'oak_planks')
    return t


def quest_board():
    t = Template()
    t.fill(0, 0, 0, 4, 0, 1, 'dirt_path')
    for x in (0, 4):
        t.fill(x, 1, 0, x, 3, 0, 'oak_log', axis='y')
    t.fill(1, 1, 0, 3, 3, 0, 'spruce_planks')
    t.fill(0, 4, 0, 4, 4, 0, 'spruce_slab', type='bottom', waterlogged='false')
    t.set(2, 3, 1, 'oak_wall_sign', facing='south', waterlogged='false',
          nbt=sign_nbt('Доска', 'заданий'))
    for x in (1, 3):
        t.set(x, 2, 1, 'oak_wall_sign', facing='south', waterlogged='false', nbt=sign_nbt('...'))
    return t


# name -> (builder, Russian label)
DRAFTS = {
    'lumberjack_hut': (lumberjack, 'Хижина дровосека'),
    'mine': (mine, 'Шахта'),
    'warehouse': (warehouse, 'Склад'),
    'builder_workshop': (builder_workshop, 'Мастерская строителя'),
    'sawmill': (sawmill, 'Лесопилка'),
    'barracks': (barracks, 'Казарма'),
    'watchtower': (watchtower, 'Сторожевая вышка'),
    'wall_tower': (wall_tower, 'Башня стены'),
    'wall_straight': (wall_straight, 'Стена'),
    'wall_gate': (wall_gate, 'Ворота'),
    'golem_pad': (golem_pad, 'Площадка голема'),
    'town_hall': (town_hall, 'Ратуша'),
    'tavern': (tavern, 'Таверна'),
    'quest_board': (quest_board, 'Доска заданий'),
}
