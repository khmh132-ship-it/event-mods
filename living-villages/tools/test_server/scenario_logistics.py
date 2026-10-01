"""Storekeeper tidies the warehouse; a carrier empties other buildings' chests into it."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def chests(s, origin_line):
    x, y, z = map(int, origin_line)
    found = []
    for dx in range(-13, 14):
        for dz in range(-13, 14):
            for dy in range(0, 4):
                p = (x + dx, y + dy, z + dz)
                if 'passed' in s.cmd(f'execute if block {p[0]} {p[1]} {p[2]} #minecraft:guarded_by_piglins', check=False):
                    found.append(p)
    return found


with TestServer(CFG, log_name='test_logistics.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('time set noon')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for prof in ['nitwit', 'none', 'none', 'none']:
        s.cmd(f'summon villager 2 64 3 {{NoAI:1b,PersistenceRequired:1b,VillagerData:{{profession:"minecraft:{prof}",'
              f'level:1,type:"minecraft:plains"}}}}')
    at(s, 'create')
    at(s, 'build warehouse true')
    at(s, 'build lumberjack_hut true')
    at(s, 'finish')
    b = at(s, 'buildings')
    wh = re.search(r'warehouse at (-?\d+) (-?\d+) (-?\d+)', b).groups()
    hut = re.search(r'lumberjack_hut at (-?\d+) (-?\d+) (-?\d+)', b).groups()
    wchests = chests(s, wh)
    hchests = chests(s, hut)
    print('warehouse chests', len(wchests), 'hut chests', len(hchests))
    for p in wchests[:4]:
        s.cmd(f'data merge block {p[0]} {p[1]} {p[2]} {{Items:[{{Slot:5b,id:"minecraft:cobblestone",Count:10b}}]}}')
    for p in hchests:
        s.cmd(f'data merge block {p[0]} {p[1]} {p[2]} {{Items:[{{Slot:0b,id:"minecraft:emerald",Count:7b}}]}}')
    for _ in range(8):
        time.sleep(15)
        spread = sum(1 for p in wchests if 'cobblestone' in s.cmd(f'data get block {p[0]} {p[1]} {p[2]} Items', check=False))
        in_hut = sum(1 for p in hchests if 'emerald' in s.cmd(f'data get block {p[0]} {p[1]} {p[2]} Items', check=False))
        print('cobblestone in', spread, 'warehouse chests | hut chests with emeralds', in_hut, '|',
              [w for w in at(s, 'workers').split('\n') if w.split(' ')[0] in ('storekeeper', 'carrier')])
    print('emeralds in stock:', re.search(r'emerald x(\d+)', at(s, 'info')))
