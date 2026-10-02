"""Normal terrain with structures on: no vanilla villages, and camps found ground of their own."""
import re
import time

from harness import TestServer

CFG = {'founding': {'naturalFounding': False}}

with TestServer(CFG, log_name='test_realworld.log', flat=False, structures=True, seed='4242') as s:
    print('locate:', s.cmd('locate structure #minecraft:village', check=False)[:200])
    s.cmd('forceload add -160 -160 160 160')
    time.sleep(20)
    for i in range(4):
        print(s.cmd(f'execute positioned {i * 400} 80 0 run forceload add {i * 400 - 120} -120 {i * 400 + 120} 120', check=False)[:80])
        time.sleep(15)
        print(i, s.cmd(f'execute positioned {i * 400} 80 0 run village autofound', check=False))
    print(s.cmd('village list', check=False))
    for minute in range(16):
      time.sleep(60)
      for m in re.finditer(r'bell (-?\d+) (-?\d+) (-?\d+)', s.cmd('village list', check=False)):
        x, y, z = m.groups()
        print('--', minute, x, y, z)
        print(s.cmd(f'execute positioned {x} {y} {z} run village info', check=False).split('Stock')[0])
        print(s.cmd(f'execute positioned {x} {y} {z} run village workers', check=False))
