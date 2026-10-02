"""Watch a builder put up houses from a full store: where does he freeze?"""
import os
import time

from harness import TestServer

SPEED = int(os.environ.get('LV_SPEED', '4'))
CFG = {'construction': {'autoGrowth': False}, 'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_buildwatch.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    for r in ['doMobSpawning false', 'doDaylightCycle false']:
        s.cmd('gamerule ' + r)
    s.cmd('time set 1000')
    for x in range(-30, 31, 6):  # trees on the sites, to be cleared
        s.cmd(f'place feature minecraft:oak {x} 64 12', check=False)
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 1 64 3 {PersistenceRequired:1b}')
    print(at(s, 'create'))
    for item, n in [('oak_log', 300), ('cobblestone', 300), ('oak_planks', 200), ('glass_pane', 40), ('bread', 64),
                    ('white_bed', 4), ('torch', 32), ('oak_door', 6), ('oak_fence', 32), ('oak_slab', 32), ('oak_stairs', 64), ('chest', 8), ('stone_pickaxe', 2), ('stone_axe', 2)]:
        at(s, f'storage add minecraft:{item} {n}')
    print(at(s, 'build mine'))
    
    last = None
    for i in range(30):
        time.sleep(10)
        w = [l for l in at(s, 'workers').split('\n') if l.strip()]
        b = at(s, 'buildings').replace('\n', ' | ')
        print(i, b[-120:], '||', w)
    import re
    m = re.search(r'mine at (-?\d+) (-?\d+) (-?\d+)', at(s, 'buildings'))
    if m:
        x, y, z = m.groups()
        print('around mine:', s.cmd(f'execute positioned {x} {int(y)+6} {z} run village count 8', check=False))
