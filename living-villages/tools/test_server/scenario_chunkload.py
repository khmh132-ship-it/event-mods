"""How long fresh terrain takes to come up, with the mod's founding and village loading on or off."""
import sys, time
from harness import TestServer
on = sys.argv[1] == 'on'
CFG = {'founding': {'naturalFounding': on}, 'loading': {'keepLoaded': on}}
with TestServer(CFG, log_name='test_chunkload.log', flat=False, structures=True) as s:
    s.cmd('gamerule doDaylightCycle false')
    total = 0
    for i in range(6):
        x = 2000 + i * 600
        t = time.time()
        s.cmd(f'forceload add {x} 0 {x + 240} 240', check=False)
        while time.time() - t < 300:
            r = s.cmd(f'execute if loaded {x + 240} 64 240', check=False)
            if i == 0 and time.time() - t < 1:
                print('probe:', r, flush=True)
            if 'passed' in r.lower():
                break
            time.sleep(0.5)
        dt = time.time() - t
        total += dt
        s.cmd(f'forceload remove {x} 0 {x + 240} 240', check=False)
        print(f'area {i}: {dt:.1f}s', flush=True)
        time.sleep(15)
    print('villages:', s.cmd('village list', check=False)[:300])
    print('tps', s.cmd('forge tps', check=False).splitlines()[0])
    print(f'TOTAL {total:.1f}s')
