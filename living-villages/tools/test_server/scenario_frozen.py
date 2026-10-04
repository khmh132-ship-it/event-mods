"""Do workers keep ticking? Their tick counters should move between two looks."""
import re, time
from harness import TestServer
CFG = {'founding': {'naturalFounding': False}}
with TestServer(CFG, log_name='test_frozen.log', difficulty='normal') as s:
    s.cmd('forceload add -64 -64 64 64')
    for x in range(-60, 61, 12):
        for z in (-50, 50):
            s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    for i in range(16):
        time.sleep(30)
        ages = s.cmd('execute as @e[type=livingvillages:worker] run data get entity @s Age', check=False)
        pos = s.cmd('execute positioned 0 64 0 run village workers', check=False).replace('\n', ' | ')
        print(i, s.cmd('time query daytime', check=False).strip(), '|', pos[:300])
        print('   ticks:', s.cmd('execute as @e[type=livingvillages:worker] run data get entity @s Air', check=False)[:200],
              s.cmd('forge entity list livingvillages:worker', check=False)[:200])
