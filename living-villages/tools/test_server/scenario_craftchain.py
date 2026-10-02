"""Charcoal torches and a hide bed from nothing but logs, cobblestone and leather."""
import time

from harness import TestServer

CFG = {'construction': {'autoGrowth': False}, 'founding': {'naturalFounding': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_craftchain.log') as s:
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(2):
        s.cmd(f'summon villager {i} 64 3 {{PersistenceRequired:1b}}')
    print(at(s, 'create'))
    for item, n in [('oak_log', 200), ('cobblestone', 64), ('leather', 3), ('stone_axe', 1), ('bread', 20)]:
        at(s, f'storage add minecraft:{item} {n}')
    print(at(s, 'request minecraft:torch 8'))
    print(at(s, 'request minecraft:white_bed 1'))
    for minute in range(6):
        time.sleep(60)
        print('---', minute + 1)
        print(at(s, 'requests'))
        print(at(s, 'workers'))
    info = at(s, 'info')
    print([l for l in info.split('\n') if 'torch' in l or 'bed' in l or 'charcoal' in l])
