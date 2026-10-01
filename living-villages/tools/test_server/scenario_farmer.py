"""A vanilla farmer is taken over and really works the village fields."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


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
    print(at(s, 'build large_farm_1 true'))
    print(at(s, 'build warehouse true'))
    print(at(s, 'finish'))
    s.cmd('gamerule randomTickSpeed 200')  # let the crops grow quickly
    w0 = re.search(r'wheat x(\d+)', at(s, 'info'))
    for _ in range(8):
        time.sleep(20)
        info = at(s, 'info')
        print(info.split('\n')[1], '| wheat', re.search(r'wheat x(\d+)', info).group(1) if 'wheat x' in info else 0,
              '| seeds', re.search(r'wheat_seeds x(\d+)', info).group(1) if 'wheat_seeds x' in info else 0)
    print('farmer inventory:', re.findall(r'id: "minecraft:(\w+)", Count: (\d+)',
                                          s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:4}] Inventory', check=False)))
