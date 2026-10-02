"""A lumberjack dropped into a deep pit gets out by himself (no teleport); villagers craft job blocks."""
import os
import re
import time

from harness import TestServer

SPEED = int(os.environ.get('LV_SPEED', '4'))
CFG = {'construction': {'autoGrowth': False}, 'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def pos(s, job):
    r = s.cmd(f'data get entity @e[type=livingvillages:worker,limit=1,nbt={{Job:{job}}}] Pos', check=False)
    m = re.findall(r'(-?[\d.]+)d', r)
    return tuple(round(float(v), 1) for v in m) if m else None


with TestServer(CFG, log_name='test_unstuck.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doMobSpawning false')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('time set 1000')
    for x in range(-40, 41, 8):
        s.cmd(f'place feature minecraft:oak {x} 64 -36', check=False)
    print(at(s, 'found'))
    for item, n in [('oak_log', 64), ('cobblestone', 64), ('bread', 32), ('iron_ingot', 4)]:
        at(s, f'storage add minecraft:{item} {n}')
    print(at(s, 'request livingvillages:miners_bench 1'))
    print(at(s, 'request livingvillages:chopping_block 1'))
    time.sleep(20)
    print(at(s, 'workers'))
    # The pit: 4 deep, walls of stone, the lumberjack inside with some dirt in his pockets.
    s.cmd('fill 18 60 18 24 63 24 air')
    s.cmd('fill 18 60 18 24 61 24 water')
    s.cmd('tp @e[type=livingvillages:worker,limit=1,nbt={Job:1}] 21 61 21')
    s.cmd('data merge entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] {Inventory:[{id:"minecraft:dirt",Count:16b}]}')
    print('in the pit:', pos(s, 1))
    for i in range(2):
        time.sleep(20)
        print(i, 'lumberjack at', pos(s, 1), '|', [l for l in at(s, 'workers').split('\n') if 'lumberjack' in l])
    print(at(s, 'requests'))
    print([l for l in at(s, 'info').split('\n') if 'bench' in l or 'chopping' in l])
