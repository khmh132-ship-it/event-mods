"""Tools: workers start bare-handed, ask for tools, the apprentice makes them, they fetch and use them."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def hand(s, job):
    r = s.cmd(f'data get entity @e[type=livingvillages:worker,limit=1,nbt={{Job:{job}}}] HandItems[0]', check=False)
    m = re.search(r'id: "minecraft:(\w+)"', r)
    d = re.search(r'Damage: (\d+)', r)
    return (m.group(1) if m else '-') + (f' dmg {d.group(1)}' if d else '')


with TestServer(CFG, log_name='test_tools.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(3):
        s.cmd(f'summon villager {i} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    for x, z in [(20, 0), (22, 6), (-20, 4), (-18, -14), (6, 22)]:
        s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
    at(s, 'create')
    for _ in range(10):
        time.sleep(20)
        print('lumberjack:', hand(s, 1), '| miner:', hand(s, 2), '|', at(s, 'requests').replace('\n', ' ; '))
    print(at(s, 'info'))
