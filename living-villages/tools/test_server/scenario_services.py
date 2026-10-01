"""Fisherman, cleric, librarian, cartographer at work."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_services.log') as s:
    s.cmd('forceload add -80 -80 80 80')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('time set noon')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('fill 14 63 -6 22 63 6 water')  # a pond
    for z in (-6, -4, -2):  # sugar cane by the pond
        s.cmd(f'setblock 13 63 {z} sand')
        s.cmd(f'fill 13 64 {z} 13 66 {z} sugar_cane')
    for x, z in [(40, 40), (-40, 30), (30, -40)]:
        s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
    for prof in ['fisherman', 'cleric', 'librarian', 'cartographer', 'none']:
        s.cmd(f'summon villager 2 64 3 {{NoAI:1b,PersistenceRequired:1b,VillagerData:{{profession:"minecraft:{prof}",'
              f'level:1,type:"minecraft:plains"}}}}')
    at(s, 'create')
    at(s, 'storage add minecraft:fishing_rod 1')
    at(s, 'storage add minecraft:oak_log 64')
    print(at(s, 'request minecraft:book 2'))
    time.sleep(10)
    # Hurt the lumberjack so the cleric has a patient.
    s.cmd('damage @e[type=livingvillages:worker,limit=1,nbt={Job:2}] 10 minecraft:generic', check=False)
    for _ in range(7):
        time.sleep(20)
        lines = [w for w in at(s, 'workers').split('\n') if w.split(' ')[0] in
                 ('fisherman', 'cleric', 'librarian', 'cartographer')]
        print(lines, '|', at(s, 'requests').replace('\n', ' ; '))
    stock = dict(re.findall(r'minecraft:(\w+) x(\d+)', at(s, 'info')))
    print({k: v for k, v in stock.items() if k not in ('oak_log', 'cobblestone', 'sand', 'iron_ingot', 'wheat')})
    print('miner health:', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:2}] Health', check=False).strip()[-8:])
