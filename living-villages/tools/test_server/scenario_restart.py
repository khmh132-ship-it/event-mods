"""Everything survives a server restart: villages, buildings, construction progress, stockpile, workers."""
import time

from harness import TestServer

CFG = {'economy': {'productionMultiplier': 0.0}, 'construction': {'buildInterval': 20, 'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_restart1.log') as s:
    s.cmd('forceload add -48 -48 48 48')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(3):
        s.cmd(f'summon villager {i} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    at(s, 'create')
    at(s, 'build builder_workshop true')
    at(s, 'finish')
    at(s, 'build tavern true')
    at(s, 'storage add minecraft:diamond 7')
    time.sleep(10)
    before = (at(s, 'buildings'), at(s, 'info'), s.cmd('execute if entity @e[type=livingvillages:worker]', check=False))

with TestServer(CFG, log_name='test_restart2.log', fresh=False) as s:
    time.sleep(3)
    after = (at(s, 'buildings'), at(s, 'info'), s.cmd('execute if entity @e[type=livingvillages:worker]', check=False))

for b, a in zip(before, after):
    print('BEFORE:', b.replace('\n', ' | '))
    print('AFTER: ', a.replace('\n', ' | '))
