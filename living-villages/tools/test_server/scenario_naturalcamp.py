"""Exploring a normal world: camps should turn up by themselves where vanilla villages would have been."""
import re, time
from harness import TestServer
CFG = {'founding': {'naturalFounding': True}}
with TestServer(CFG, log_name='test_naturalcamp.log', flat=False, structures=True, seed='4242') as s:
    s.cmd('gamerule doMobSpawning false')
    areas = [(ax, az) for ax in range(0, 3) for az in range(0, 3)]
    for ax, az in areas:
        x, z = ax * 512 - 512, az * 512 - 512
        t = time.time()
        s.cmd(f'forceload add {x} {z} {x + 240} {z + 240}', check=False)
        time.sleep(25)
        lst = s.cmd('village list', check=False)
        print(f'area {x},{z} ({time.time() - t:.0f}s):', lst.replace('\n', ' | ')[:400], flush=True)
        s.cmd(f'forceload remove {x} {z} {x + 240} {z + 240}', check=False)
    time.sleep(30)
    print('final:', s.cmd('village list', check=False))
