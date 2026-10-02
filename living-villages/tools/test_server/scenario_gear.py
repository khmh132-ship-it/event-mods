"""Gear: a cleric blesses stored gear, a miner swaps to a better pickaxe, guards wear the best armour."""
import os
import time

from harness import TestServer

SPEED = int(os.environ.get('LV_SPEED', '10'))
CFG = {'construction': {'autoGrowth': False}, 'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_gear.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doMobSpawning false')
    s.cmd('time set 1000')
    for x in range(-40, 41, 8):
        s.cmd(f'place feature minecraft:oak {x} 64 -36', check=False)
    print(at(s, 'found'))
    s.cmd('summon villager 2 64 2 {PersistenceRequired:1b,VillagerData:{profession:"minecraft:cleric",level:2,type:"minecraft:plains"}}')
    for i in range(4):
        s.cmd(f'summon villager {i} 64 -3 {{PersistenceRequired:1b}}')
    print(at(s, 'build mine true'))
    print(at(s, 'build small_house_1 true'))
    print(at(s, 'finish'))
    for item, n in [('diamond_pickaxe', 1), ('iron_chestplate', 1), ('diamond_sword', 1), ('diamond_axe', 1), ('iron_shovel', 1), ('iron_boots', 1), ('oak_log', 200),
                    ('bread', 64), ('cobblestone', 64), ('oak_fence', 32), ('torch', 32)]:
        at(s, f'storage add minecraft:{item} {n}')
    for minute in range(3):
        time.sleep(60)
        print('---', minute + 1)
        print(at(s, 'workers'))
    print(s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:2}] HandItems', check=False)[:300])
