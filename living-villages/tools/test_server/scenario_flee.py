"""A zombie walks up to the camp by day: workers run from it, a guard does not."""
import re, time
from harness import TestServer
CFG = {'founding': {'naturalFounding': False}}
with TestServer(CFG, log_name='test_flee.log', difficulty='normal') as s:
    s.cmd('forceload add -48 -48 48 48')
    for r in ['doMobSpawning false', 'doDaylightCycle false']:
        s.cmd('gamerule ' + r)
    s.cmd('time set 1000')
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    time.sleep(10)
    def pos():
        out = s.cmd('execute positioned 0 64 0 run village workers', check=False)
        return [(m.group(1), int(m.group(2)), int(m.group(4))) for m in re.finditer(r'(\w+) @(-?\d+), (-?\d+), (-?\d+)', out)]
    before = pos()
    print('before', before)
    job, x, z = before[0]
    s.cmd(f'summon zombie {x + 5} 64 {z} {{IsBaby:0b,CanPickUpLoot:0b}}', check=False)
    for i in range(6):
        time.sleep(1)
        now = pos()
        print(i, now, s.cmd(f'execute as @e[type=zombie,limit=1] run data get entity @s Pos', check=False)[-60:])
    s.cmd('kill @e[type=zombie]', check=False)
    time.sleep(15)
    print('after', pos(), s.cmd('execute positioned 0 64 0 run village workers', check=False)[-400:])
