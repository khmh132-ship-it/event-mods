"""No player about: do the village's fields still ripen? (The game itself only grows plants near players.)"""
import os, time
from harness import TestServer
SPEED = int(os.environ.get('LV_SPEED', '8'))
CFG = {'founding': {'naturalFounding': False}, 'debug': {'testSpeed': SPEED}}
with TestServer(CFG, log_name='test_crops.log') as s:
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('gamerule doMobSpawning false')
    s.cmd('time set 1000')
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    print(s.cmd('execute positioned 0 64 0 run village build small_farm_1 true', check=False))
    for m in range(8):
        time.sleep(30)
        print(m, s.cmd('execute positioned 0 64 0 run village count 30', check=False))
    print(s.cmd('execute positioned 0 64 0 run village workers', check=False))
