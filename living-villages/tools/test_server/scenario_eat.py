"""A starving worker with wheat in the chests should fetch it, bake and eat."""
import re, time
from harness import TestServer
CFG = {'founding': {'naturalFounding': False}}
with TestServer(CFG, log_name='test_eat.log') as s:
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('gamerule doMobSpawning false')
    s.cmd('time set 1000'); s.cmd('gamerule doDaylightCycle false')
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    time.sleep(8)
    print(s.cmd('execute positioned 0 64 0 run village storage add minecraft:wheat 64', check=False))
    print(s.cmd('execute as @e[type=livingvillages:worker] run data merge entity @s {Food:0}', check=False))
    for i in range(8):
        time.sleep(5)
        print(i, s.cmd('execute positioned 0 64 0 run village workers', check=False).replace('\n', ' | '))
