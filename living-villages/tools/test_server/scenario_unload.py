"""Construction keeps going while the site is unloaded and catches up when it is loaded again."""
import re
import time

from harness import TestServer

SLOW = {'construction': {'buildInterval': 20, 'maxBuildStepsPerSecond': 64, 'autoGrowth': False}}


def progress(s):
    r = s.cmd('execute positioned 0 64 0 run village buildings', check=False)
    m = re.search(r'town_hall .*: (\w+) (\d+)', r)
    return m.group(1), int(m.group(2))


with TestServer(SLOW, log_name='test_unload.log') as s:
    s.cmd('setworldspawn 3000 64 3000')  # spawn chunks far away so the village can unload
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 2 64 2 {NoAI:1b,PersistenceRequired:1b}')
    print(s.cmd('execute positioned 0 64 0 run village create', check=False))
    print(s.cmd('execute positioned 0 64 0 run village build town_hall true', check=False))
    time.sleep(6)
    p1 = progress(s)
    s.cmd('forceload remove -48 -48 48 48')
    time.sleep(40)
    p2 = progress(s)
    s.cmd('forceload add -48 -48 48 48')
    time.sleep(4)
    p3 = progress(s)
    print('loaded 6s:', p1, '| after 40s unloaded:', p2, '| 4s after reload:', p3)
    ok = p2[1] <= p1[1] + 3 and (p3[0] != p1[0] or p3[1] >= p1[1] + 35)
    print('PASS' if ok else 'FAIL')
