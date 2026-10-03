"""Normal difficulty, monsters about: does a camp come through the nights?"""
import os, re, time
from harness import TestServer
SPEED = int(os.environ.get('LV_SPEED', '4'))
CFG = {'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}
with TestServer(CFG, log_name='test_night.log', difficulty='normal') as s:
    s.cmd('forceload add -64 -64 64 64')
    for x in range(-60, 61, 12):
        for z in (-50, 50):
            s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    s.cmd('time set 11000')
    for m in range(10):
        time.sleep(30)
        info = s.cmd('execute positioned 0 64 0 run village info', check=False).split('\n')[0]
        mobs = s.cmd('execute if entity @e[type=#minecraft:raiders]', check=False)
        z = s.cmd('execute store result score @p dummy run list', check=False)
        print(m, s.cmd('time query daytime', check=False).strip(), '|', info, '|',
              s.cmd('execute positioned 0 64 0 run village workers', check=False).replace('\n', ' | ')[:500], flush=True)
    print('villagers:', s.cmd('execute positioned 0 64 0 run village info', check=False).split('\n')[0])
