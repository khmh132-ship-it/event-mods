"""The new miner: a finished mine, supplies in stock, ore, a cave and lava around. Watch the corridors grow."""
import re
import sys
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False},
       'founding': {'naturalFounding': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(dict(CFG, debug={'testSpeed': 6}), log_name='test_mine3x3.log', difficulty='easy') as s:
    s.cmd('forceload add -96 -96 96 96')
    for rule in ['doMobSpawning false', 'doDaylightCycle false', 'time set noon']:
        s.cmd('gamerule ' + rule if 'time' not in rule else rule, check=False)
    s.cmd('time set 6000')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 1 64 3 {PersistenceRequired:1b}')
    s.cmd('summon villager 2 64 3 {PersistenceRequired:1b}')
    print(at(s, 'create'))
    for item, n in [('birch_fence', 64), ('birch_planks', 64), ('torch', 64), ('stone_pickaxe', 2), ('bread', 32), ('birch_log', 640)]:
        at(s, f'storage add minecraft:{item} {n}')
    print(at(s, 'build mine true'))
    print(at(s, 'finish'))
    b = at(s, 'buildings')
    print(b)
    m = re.search(r'mine at (-?\d+) (-?\d+) (-?\d+)', b)
    mx, my, mz = map(int, m.groups())
    # Ore veins and a cave around the mine's bottom, lava a bit further off.
    for dx, dz, ore in [(10, 3, 'iron_ore'), (-4, 12, 'coal_ore'), (3, -14, 'iron_ore'), (-12, -3, 'coal_ore')]:
        s.cmd(f'fill {mx + dx} {my - 1} {mz + dz} {mx + dx + 1} {my + 1} {mz + dz + 1} {ore} replace stone', check=False)
    s.cmd(f'fill {mx + 20} {my - 2} {mz - 8} {mx + 28} {my + 4} {mz + 8} air replace stone', check=False)
    s.cmd(f'fill {mx - 22} {my} {mz - 22} {mx - 20} {my + 2} {mz + 22} lava replace stone', check=False)
    minutes = int(sys.argv[1]) if len(sys.argv) > 1 else 8
    for minute in range(minutes):
        time.sleep(60)
        print(f'--- {minute + 1} min')
        if minute == 5:
            print(s.cmd('execute at @e[type=livingvillages:worker,nbt={Job:2},limit=1] run summon zombie ^ ^ ^4 {PersistenceRequired:1b}', check=False))
        print(at(s, 'workers'))
        print(s.cmd(f'execute positioned {mx} {my} {mz} run village count 40', check=False))
    print(at(s, 'info'))
