"""Shepherd and butcher with real animals: shearing, leading into a pen, breeding, slaughter, smoking."""
import re
import time

from harness import TestServer

CFG = {'economy': {'productionInterval': 72000}, 'construction': {'autoGrowth': False}}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def count(s, sel):
    r = s.cmd(f'execute if entity {sel}', check=False)
    m = re.search(r'count: (\d+)', r)
    return int(m.group(1)) if m else 0


with TestServer(CFG, log_name='test_animals.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    s.cmd('gamerule doDaylightCycle false')
    s.cmd('time set noon')
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for prof in ['shepherd', 'butcher', 'none', 'none']:
        s.cmd(f'summon villager 2 64 3 {{NoAI:1b,PersistenceRequired:1b,VillagerData:{{profession:"minecraft:{prof}",'
              f'level:1,type:"minecraft:plains"}}}}')
    at(s, 'create')
    print(at(s, 'build animal_pen_1 true'))
    print(at(s, 'build butcher_shop_1 true'))
    print(at(s, 'finish'))
    at(s, 'storage add minecraft:wheat 40')
    at(s, 'storage add minecraft:shears 1')
    at(s, 'storage add minecraft:stone_axe 1')
    at(s, 'storage add minecraft:coal 16')
    for i in range(4):
        s.cmd(f'summon sheep {12 + i * 2} 64 -12')
    for i in range(3):
        s.cmd(f'summon cow {-14 - i * 2} 64 10')
    for i in range(2):
        s.cmd(f'summon pig {14 + i * 2} 64 14')
    pen = re.search(r'animal_pen_1 at (-?\d+) (-?\d+) (-?\d+)', at(s, 'buildings'))
    print('pen at', pen.groups())
    for _ in range(6):
        time.sleep(20)
        info = at(s, 'info')
        stock = dict(re.findall(r'minecraft:(\w+) x(\d+)', info))
        print(info.split('\n')[1], '| sheep', count(s, '@e[type=sheep]'), 'cows', count(s, '@e[type=cow]'),
              'pigs', count(s, '@e[type=pig]'), '| stock:', {k: stock[k] for k in stock if k not in
                                                             ('oak_log', 'cobblestone', 'sand', 'iron_ingot')})
        print('   ', at(s, 'workers').replace('\n', '\n    '))
        px, py, pz = map(int, pen.groups())
        print('    sheep in pen:', count(s, f'@e[type=sheep,x={px - 1},y={py},z={pz - 1},dx=10,dy=4,dz=10]'))
