"""Physical store: workers carry what they gather into the warehouse chests and pick up items lying around."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'buildInterval': 10, 'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_stock.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0']:
        s.cmd('gamerule ' + rule)
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(4):
        s.cmd(f'summon villager {i - 1} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    print(at(s, 'create'))
    print(at(s, 'build lumberjack_hut true'))
    print(at(s, 'build warehouse true'))
    print(at(s, 'finish'))
    b = at(s, 'buildings')
    hut = re.search(r'lumberjack_hut at (-?\d+) (-?\d+) (-?\d+)', b)
    hx, hz = int(hut.group(1)), int(hut.group(3))
    for dx, dz in [(12, 0), (-12, 4), (0, 13), (8, 10)]:
        s.cmd(f'place feature minecraft:oak {hx + dx} 64 {hz + dz}', check=False)
    time.sleep(8)
    print(at(s, 'info'))
    # Something lying on the ground near the lumberjack.
    s.cmd(f'summon item {hx + 3} 65 {hz + 3} {{Item:{{id:"minecraft:diamond",Count:5b}}}}')
    s.cmd(f'summon item {hx - 5} 65 {hz - 5} {{Item:{{id:"minecraft:emerald",Count:3b}}}}')
    time.sleep(90)
    print(at(s, 'info'))
    w = re.search(r'warehouse at (-?\d+) (-?\d+) (-?\d+)', b)
    print('warehouse at', w.groups())
    print('items left on the ground:', s.cmd('execute if entity @e[type=item]', check=False))
