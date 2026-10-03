"""A big village (a score of people, a score of buildings): what it costs the server per tick."""
import time
from harness import TestServer
CFG = {'founding': {'naturalFounding': False}}
with TestServer(CFG, log_name='test_bigperf.log') as s:
    s.cmd('forceload add -96 -96 96 96')
    s.cmd('gamerule doMobSpawning false')
    for x in range(-90, 91, 10):
        for z in (-85, 85):
            s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
    time.sleep(20)
    print('empty', s.cmd('forge tps', check=False).splitlines()[0])
    print(s.cmd('execute positioned 0 64 0 run village found', check=False))
    for t in ['warehouse', 'lumberjack_hut', 'mine', 'sawmill', 'workshop', 'barracks', 'pen', 'watchtower'] + ['small_house_1'] * 6 + ['small_farm_1'] * 4:
        s.cmd(f'execute positioned 0 64 0 run village build {t} true', check=False)
        s.cmd('execute positioned 0 64 0 run village finish', check=False)
    for i in range(18):
        s.cmd(f'summon villager {i % 6 - 3} 65 {i // 6 - 1}', check=False)
    s.cmd('execute positioned 0 64 0 run village storage add minecraft:bread 200', check=False)
    time.sleep(90)
    print(s.cmd('execute positioned 0 64 0 run village info', check=False).split('\n')[0:5])
    for i in range(3):
        time.sleep(30)
        print('big', s.cmd('forge tps', check=False).splitlines()[0])
