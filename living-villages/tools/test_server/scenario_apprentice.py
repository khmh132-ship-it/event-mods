"""Apprentice alone: order glass panes and a few wooden things, watch him set up a table and a furnace."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_apprentice.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 1 64 3 {NoAI:1b,PersistenceRequired:1b}')
    at(s, 'create')
    at(s, 'storage add minecraft:oak_log 300')  # no lumberjack needed
    at(s, 'storage add minecraft:cobblestone 200')  # no miner needed
    print(at(s, 'request minecraft:glass_pane 6'))
    print(at(s, 'request minecraft:oak_door 2'))
    for _ in range(14):
        time.sleep(15)
        inv = s.cmd('data get entity @e[type=livingvillages:worker,limit=1] Inventory', check=False)
        pos = s.cmd('data get entity @e[type=livingvillages:worker,limit=1] Pos', check=False)
        print(re.findall(r'id: "minecraft:(\w+)"', inv), re.findall(r'(-?\d+)\.\d+d', pos), '|',
              at(s, 'requests').replace('\n', ' ; '))
    print(at(s, 'info'))
    for x in range(-9, 10):
        for z in range(-9, 10):
            for y in (64,):
                r = s.cmd(f'execute if block {x} {y} {z} #minecraft:mineable/pickaxe', check=False)
    print('furnace:', [p for p in [(x, z) for x in range(-8, 9) for z in range(-8, 9)]
                       if 'passed' in s.cmd(f'execute if block {p[0]} 64 {p[1]} furnace', check=False)])
    print('table:', [p for p in [(x, z) for x in range(-8, 9) for z in range(-8, 9)]
                     if 'passed' in s.cmd(f'execute if block {p[0]} 64 {p[1]} crafting_table', check=False)])
