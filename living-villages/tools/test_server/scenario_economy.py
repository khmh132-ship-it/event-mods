"""The real economy chain: a builder builds the lumberjack hut from real materials, an apprentice crafts what
is missing with real recipes (setting up a crafting table and furnace by the bell), a lumberjack fells trees."""
import re
import sys
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'buildInterval': 10, 'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


minutes = float(sys.argv[1]) if len(sys.argv) > 1 else 6
with TestServer(CFG, log_name='test_economy.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0']:
        s.cmd('gamerule ' + rule)
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(6):
        s.cmd(f'summon villager {i - 2} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    for x, z in [(20, 0), (22, 6), (-20, 4), (-18, -14), (6, 22), (-6, -22), (14, 18), (-24, 12)]:
        s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
    print(at(s, 'create'))
    print(at(s, 'build lumberjack_hut'))
    end = time.time() + minutes * 60
    while time.time() < end:
        time.sleep(45)
        print('---', at(s, 'buildings').strip(), '|', at(s, 'info').split('\n')[1])
        print(at(s, 'requests').strip())
        inv = s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:3}] Inventory', check=False)
        pos = s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:3}] Pos', check=False)
        print('   apprentice', re.findall(r'id: "minecraft:(\w+)", Count: (\d+)', inv), re.findall(r'(-?\d+)\.\d+d', pos))
    print(at(s, 'info'))
    print('stations near bell:', s.cmd('execute if block -2 64 -2 crafting_table', check=False))
    print('dropped items:', s.cmd('execute if entity @e[type=item]', check=False))
