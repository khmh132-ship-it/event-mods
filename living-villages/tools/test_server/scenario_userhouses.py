"""All Living Villages buildings go up cleanly: nothing drops, the mine descent is dug out."""
import os
import re
import subprocess
import sys
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}
TYPES = ['lumberjack_hut', 'builder_workshop', 'warehouse', 'mine', 'sawmill', 'apprentice_workshop', 'barracks',
         'town_hall', 'tavern', 'watchtower', 'golem_pad', 'quest_board']


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


with TestServer(CFG, log_name='test_userhouses.log') as s:
    s.cmd('forceload add -80 -80 80 80')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    s.cmd('summon villager 2 64 3 {NoAI:1b,PersistenceRequired:1b}')
    at(s, 'create')
    for t in TYPES:
        print(at(s, f'build {t} true').strip())
    print(at(s, 'finish').strip())
    time.sleep(3)
    print('dropped items:', s.cmd('execute if entity @e[type=item]', check=False).strip())
    print(at(s, 'types').strip().split('\n')[:0])
    mine = re.search(r'Started mine at (-?\d+) (-?\d+) (-?\d+) facing (\w+)', '\n'.join(at(s, 'buildings').split('\n')))
    print(at(s, 'buildings'))
    stock = dict(re.findall(r'minecraft:(\w+) x(\d+)', at(s, 'info')))
    print('stock after digging:', {k: v for k, v in stock.items() if k in ('cobblestone', 'dirt', 'stone')})
out = os.path.join(os.path.dirname(s.log_path), 'userhouses_map.png')
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), 'render_map.py'), s.world_dir(), out,
                '-60,-60,60,60', '5'])
print('map', out)
