import re
import time
from collections import Counter

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def fields(s, x0, z0, x1, z1):
    c = Counter()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if 'passed' not in s.cmd(f'execute if block {x} 64 {z} farmland', check=False):
                continue
            st = 'empty'
            for age in (7, 0, 1, 2, 3, 4, 5, 6):
                if 'passed' in s.cmd(f'execute if block {x} 65 {z} wheat[age={age}]', check=False):
                    st = f'wheat{age}'
                    break
            else:
                for crop in ('carrots', 'potatoes', 'beetroots'):
                    if 'passed' in s.cmd(f'execute if block {x} 65 {z} {crop}', check=False):
                        st = crop
            c[st] += 1
    return dict(c)


with TestServer(CFG, log_name='test_farmer.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('time set noon')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 2 64 3 {NoAI:1b,PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",'
          'level:1,type:"minecraft:plains"},Inventory:[{id:"minecraft:wheat_seeds",Count:10b}]}')
    s.cmd('summon villager 3 64 3 {NoAI:1b,PersistenceRequired:1b}')
    at(s, 'create')
    print(at(s, 'build small_farm_1 true'))
    print(at(s, 'finish'))
    b = re.search(r'small_farm_1 at (-?\d+) (-?\d+) (-?\d+)', at(s, 'buildings'))
    x, z = int(b.group(1)), int(b.group(3))
    box = (x - 10, z - 10, x + 10, z + 10)
    s.cmd('gamerule randomTickSpeed 200')
    for i in range(6):
        if i % 2 == 0:  # no player around means no random ticks: ripen the field by hand
            s.cmd(f'fill {box[0]} 65 {box[1]} {box[2]} 65 {box[3]} wheat[age=7] replace wheat', check=False)
        time.sleep(15)
        pos = re.findall(r'(-?\d+)\.\d+d', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:4}] Pos', check=False))
        inv = re.findall(r'id: "minecraft:(\w+)", Count: (\d+)', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:4}] Inventory', check=False))
        print(fields(s, *box), 'farmer at', pos, inv, '| stock wheat:',
              (re.search(r'minecraft:wheat x(\d+)', at(s, 'info')) or [0, 0])[1])
