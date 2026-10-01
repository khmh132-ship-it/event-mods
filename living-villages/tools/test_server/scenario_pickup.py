"""Debug: does a worker pick up an item dropped right next to him?"""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}

with TestServer(CFG, log_name='test_pickup.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(2):
        s.cmd(f'summon villager {i - 1} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    s.cmd('execute positioned 0 64 0 run village create', check=False)
    s.cmd('execute positioned 0 64 0 run village build lumberjack_hut true', check=False)
    s.cmd('execute positioned 0 64 0 run village finish', check=False)
    time.sleep(8)
    for attempt in range(3):
        r = s.cmd('data get entity @e[type=livingvillages:worker,limit=1] Pos', check=False)
        x, y, z = [float(v) for v in re.findall(r'(-?[\d.]+)d', r)]
        s.cmd(f'summon item {x + 3:.1f} {y + 0.5:.1f} {z:.1f} {{Item:{{id:"minecraft:diamond",Count:1b}},PickupDelay:0s}}')
        time.sleep(6)
        print('attempt', attempt, 'worker at', round(x, 1), round(y, 1), round(z, 1), '| items:',
              s.cmd('execute if entity @e[type=item]', check=False).strip(), '| gamerule mobGriefing:',
              s.cmd('gamerule mobGriefing', check=False).strip())
    print(s.cmd('data get entity @e[type=livingvillages:worker,limit=1] Inventory', check=False))
    print(s.cmd('data get entity @e[type=livingvillages:worker,limit=1] CanPickUpLoot', check=False))
