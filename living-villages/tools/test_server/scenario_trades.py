"""Specialists take the orders of their trade; the apprentice takes the rest."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_trades.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for prof in ['mason', 'toolsmith', 'none', 'none', 'none']:
        s.cmd(f'summon villager 2 64 3 {{NoAI:1b,PersistenceRequired:1b,VillagerData:{{profession:"minecraft:{prof}",'
              f'level:1,type:"minecraft:plains"}}}}')
    at(s, 'create')
    at(s, 'storage add minecraft:oak_log 200')
    at(s, 'storage add minecraft:cobblestone 200')
    at(s, 'storage add minecraft:coal 20')
    for order in ['stone_bricks 8', 'stone_axe 1', 'oak_door 3', 'glass_pane 8']:
        print(at(s, 'request minecraft:' + order))
    for _ in range(8):
        time.sleep(15)
        print([w for w in at(s, 'workers').split('\n') if w.split(' ')[0] in ('mason', 'toolsmith', 'apprentice')],
              '|', at(s, 'requests').replace('\n', ' ; '))
    stock = dict(re.findall(r'minecraft:(\w+) x(\d+)', at(s, 'info')))
    print({k: stock.get(k) for k in ('stone_bricks', 'stone_axe', 'oak_door', 'glass_pane')})
