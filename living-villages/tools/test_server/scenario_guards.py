"""Guards: armed from the warehouse, they kill monsters in the village; the others hide in bed."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def count(s, sel):
    m = re.search(r'count: (\d+)', s.cmd(f'execute if entity {sel}', check=False))
    return int(m.group(1)) if m else 0


with TestServer(CFG, log_name='test_guards.log', difficulty='easy') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('gamerule doMobSpawning false')
    s.cmd('time set noon')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(7):
        s.cmd(f'summon villager {i - 3} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    at(s, 'create')
    for h in ['small_house_1', 'small_house_2', 'small_house_3', 'small_house_4']:
        at(s, f'build {h} true')
    at(s, 'finish')
    at(s, 'storage add minecraft:iron_sword 2')
    at(s, 'storage add minecraft:iron_chestplate 2')
    time.sleep(25)
    print(at(s, 'workers'))
    s.cmd('time set midnight')
    for i in range(5):
        s.cmd(f'summon zombie {20 + i} 64 {-20 + i * 3} {{PersistenceRequired:1b}}')
    for _ in range(6):
        time.sleep(10)
        print('zombies', count(s, '@e[type=zombie]'), '| guards',
              [w for w in at(s, 'workers').split('\n') if w.startswith('guard')])
    print(at(s, 'workers'))
