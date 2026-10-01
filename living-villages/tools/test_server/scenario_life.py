"""Daily life: workers sleep in beds at night (a bed in their workplace is theirs), eat when hungry, and the
village has children when there are free beds and food."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000, 'birthInterval': 200}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_life.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('time set noon')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(3):
        s.cmd(f'summon villager {i} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    at(s, 'create')
    for h in ['lumberjack_hut', 'small_house_1', 'small_house_2', 'small_house_3', 'small_house_4']:
        print(at(s, f'build {h} true'))
    print(at(s, 'finish'))
    at(s, 'storage add minecraft:bread 40')
    time.sleep(12)
    print(at(s, 'info').split('\n')[0:2])
    s.cmd('time set midnight')
    time.sleep(25)
    for w in range(1, 4):
        print('  ', s.cmd(f'data get entity @e[type=livingvillages:worker,limit=1,nbt={{Job:{w}}}] Bed', check=False).strip()[-40:],
              s.cmd(f'data get entity @e[type=livingvillages:worker,limit=1,nbt={{Job:{w}}}] SleepingY', check=False).strip()[-20:])
    s.cmd('time set noon')
    time.sleep(10)
    print('after dawn, lumberjack SleepingY:', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] SleepingY', check=False).strip()[-30:])
    # Hunger: make the lumberjack hungry and see him eat.
    s.cmd('data merge entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] {Food:3}')
    time.sleep(25)
    print('lumberjack food after eating:', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] Food', check=False).strip()[-5:])
    time.sleep(20)
    print(at(s, 'info').split('\n')[0])
    print('children:', s.cmd('execute if entity @e[type=villager,nbt={Age:-20000}]', check=False).strip(), '(any baby:',
          s.cmd('execute if entity @e[type=villager,x=-100,dx=200,y=0,dy=200,z=-100,dz=200]', check=False).strip() + ')',
          s.cmd('execute if entity @e[type=villager]', check=False))
