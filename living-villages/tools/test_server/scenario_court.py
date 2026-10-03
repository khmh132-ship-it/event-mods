"""A child is born the vanilla way: two villagers go to each other, hearts, then a baby between them."""
import re, time
from harness import TestServer
CFG = {'founding': {'naturalFounding': False}, 'economy': {'birthInterval': 200}}
with TestServer(CFG, log_name='test_court.log') as s:
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('gamerule doMobSpawning false'); s.cmd('gamerule doDaylightCycle false'); s.cmd('time set 1000')
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    time.sleep(10)
    print(s.cmd('execute positioned 0 64 0 run village storage add minecraft:bread 64', check=False))
    for i in range(12):
        time.sleep(5)
        w = s.cmd('execute positioned 0 64 0 run village workers', check=False).replace('\n', ' | ')
        print(i, w[:300])
    print(s.cmd('execute positioned 0 64 0 run village info', check=False).split('\n')[0])
