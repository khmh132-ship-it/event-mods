"""A worker fallen into a deep pit (a quarry) with only granite in his pockets climbs back out."""
import re, time
from harness import TestServer
CFG = {'construction': {'autoGrowth': False}, 'founding': {'naturalFounding': False}}

def pos(s):
    r = s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] Pos', check=False)
    m = re.findall(r'(-?[\\d.]+)d', r)
    return tuple(round(float(v), 1) for v in m) if m else None

with TestServer(CFG, log_name='test_deeppit.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    for r in ['doMobSpawning false', 'doDaylightCycle false']:
        s.cmd('gamerule ' + r)
    s.cmd('time set 1000')
    for x in range(-40, 41, 8):
        s.cmd(f'place feature minecraft:oak {x} 64 -36', check=False)
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    s.cmd('execute positioned 0 64 0 run village storage add minecraft:bread 32', check=False)
    time.sleep(20)
    s.cmd('fill 20 49 20 22 63 22 air')
    s.cmd('tp @e[type=livingvillages:worker,limit=1,nbt={Job:1}] 21 49 21')
    s.cmd('data merge entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] {Inventory:[{id:"minecraft:granite",Count:24b}]}')
    print('in the pit:', pos(s))
    for i in range(8):
        time.sleep(15)
        print(i, pos(s), [l for l in s.cmd('execute positioned 0 64 0 run village workers', check=False).split('\n') if 'lumberjack' in l])
