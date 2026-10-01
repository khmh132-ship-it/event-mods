"""Where do dropped items come from? Places a vanilla village with our growth off, then on."""
import re
import time
from collections import Counter

from harness import TestServer


def items(s):
    out = Counter()
    for i in range(400):
        r = s.cmd(f'data get entity @e[type=item,limit=1,sort=nearest] Item.id', check=False)
        m = re.search(r'"([a-z_:]+)"', r)
        if not m:
            break
        out[m.group(1)] += 1
        s.cmd('kill @e[type=item,limit=1,sort=nearest]', check=False)
    return out


with TestServer({'construction': {'autoGrowth': False}}, flat=False, log_name='test_items.log') as s:
    for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0']:
        s.cmd('gamerule ' + rule)
    m = re.search(r'\[(-?\d+), (~|-?\d+), (-?\d+)\]', s.cmd('locate biome minecraft:plains'))
    x, z = int(m.group(1)), int(m.group(3))
    s.cmd(f'forceload add {x - 80} {z - 80} {x + 80} {z + 80}')
    s.cmd(f'place structure minecraft:village_plains {x} 64 {z}', check=False)
    time.sleep(20)
    print('after vanilla generation only:', items(s))
