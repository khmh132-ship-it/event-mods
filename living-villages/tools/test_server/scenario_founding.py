"""A camp from nothing: bell, a house or two, two villagers, trees around. Watch what they make of it."""
import re
import time

from harness import TestServer

import os
SPEED = int(os.environ.get('LV_SPEED', '1'))
CFG = {'construction': {'buildInterval': 10}, 'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_founding.log') as s:
    s.cmd('forceload add -96 -96 96 96')
    for rule in ['doMobSpawning false', 'randomTickSpeed 3']:
        s.cmd('gamerule ' + rule)
    for x in range(-60, 61, 9):
        for z in (-50, -38, 38, 50):
            s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
            s.cmd(f'place feature minecraft:oak {z} 64 {x}', check=False)
    for i in range(6):
        s.cmd(f'summon cow {30 + i * 3} 64 {-20 + i}', check=False)
        s.cmd(f'summon pig {-30 - i * 3} 64 {20 - i}', check=False)
        s.cmd(f'summon chicken {20 - i * 3} 64 {30}', check=False)
    print(at(s, 'found'))
    for minute in range(int(__import__('sys').argv[1]) if len(__import__('sys').argv) > 1 else 12):
        time.sleep(60)
        s.cmd('fill -24 62 -24 24 67 24 wheat[age=7] replace wheat', check=False)  # no random ticks without players
        info = at(s, 'info')
        print(f'--- {minute + 1} min (game {(minute + 1) * SPEED} min), time', s.cmd('time query daytime', check=False).strip())
        print(info)
        print(at(s, 'buildings'))
        print(at(s, 'workers'))
        print(at(s, 'requests'))
