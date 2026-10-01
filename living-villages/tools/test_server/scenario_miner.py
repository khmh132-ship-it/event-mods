"""Quarry miner alone: does he survive and bring stone?"""
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}

with TestServer(CFG, log_name='test_miner.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 1 64 3 {NoAI:1b,PersistenceRequired:1b}')
    s.cmd('summon villager 2 64 3 {NoAI:1b,PersistenceRequired:1b}')
    s.cmd('execute positioned 0 64 0 run village create', check=False)
    s.cmd('execute positioned 0 64 0 run village storage take minecraft:cobblestone 32', check=False)
    for _ in range(12):
        time.sleep(15)
        print(s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:2}] Pos', check=False).strip()[-60:],
              '| hp', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:2}] Health', check=False).strip()[-8:])
