"""Fast: a camp with a farm; is a farmer hired once crops ripen, and does wheat reach the stock?"""
import os
import time

from harness import TestServer

SPEED = int(os.environ.get('LV_SPEED', '10'))
CFG = {'construction': {'autoGrowth': False}, 'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_farmfast.log') as s:
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('gamerule doMobSpawning false')
    print(at(s, 'found'))
    for x in range(-40, 41, 10):
        s.cmd(f'place feature minecraft:oak {x} 64 -40', check=False)
    print(at(s, 'build small_farm_1 true'))
    print(at(s, 'finish'))
    for minute in range(5):
        time.sleep(60)
        s.cmd('fill -24 62 -24 24 67 24 wheat[age=7] replace wheat', check=False)  # no random ticks without players
        print('---', minute + 1)
        info = at(s, 'info')
        print(info.split('small_')[0])
        print(at(s, 'workers'))
