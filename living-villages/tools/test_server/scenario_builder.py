"""A village without a builder's workshop gets a builder as soon as it starts building; he walks to the site
and places the blocks himself."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'buildInterval': 10, 'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def builder_pos(s):
    r = s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:0}] Pos', check=False)
    m = re.findall(r'(-?[\d.]+)d', r)
    return tuple(round(float(v), 1) for v in m) if m else None


with TestServer(CFG, log_name='test_builder.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(3):
        s.cmd(f'summon villager {i - 1} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    print(at(s, 'create'))
    print(at(s, 'build small_house_5 true'))
    rows = []
    for _ in range(18):
        time.sleep(5)
        m = re.search(r'small_house_5 at (-?\d+) (-?\d+) (-?\d+) \w+: (\w+ \d+)', at(s, 'buildings'))
        rows.append((m.group(4), builder_pos(s)))
        if m.group(4).startswith('DONE'):
            break
    print('house at', m.group(1), m.group(2), m.group(3))
    for r in rows:
        print('  ', r)
    print(at(s, 'info').split('\n')[1])
    print('dropped items:', s.cmd('kill @e[type=item]', check=False))
