"""Workers: unemployed villagers get hired, the lumberjack fells trees into the stockpile, the builder speeds up
construction."""
import re
import time

from harness import TestServer

CFG = {
    'economy': {'productionInterval': 72000, 'productionMultiplier': 0.0},
    'construction': {'buildInterval': 10, 'maxBuildStepsPerSecond': 64, 'autoGrowth': False},
}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def logs(s):
    m = re.search(r'minecraft:oak_log x(\d+)', at(s, 'info'))
    return int(m.group(1)) if m else 0


def progress(s, t):
    m = re.search(t + r' .*: (\w+) (\d+)', at(s, 'buildings'))
    return (m.group(1), int(m.group(2))) if m else None


with TestServer(CFG, log_name='test_workers.log') as s:
    s.cmd('forceload add -64 -64 64 64')
    for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0']:
        s.cmd('gamerule ' + rule)
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(4):
        s.cmd(f'summon villager {i - 1} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    print(at(s, 'create'))
    print(at(s, 'build lumberjack_hut true'))
    print(at(s, 'build builder_workshop true'))
    print(at(s, 'finish'))
    print(at(s, 'buildings'))
    # Trees around the hut, found after the hut exists so none get felled by site levelling.
    hut = re.search(r'lumberjack_hut at (-?\d+) (-?\d+) (-?\d+)', at(s, 'buildings'))
    hx, hz = int(hut.group(1)), int(hut.group(3))
    planted = 0
    for dx, dz in [(12, 0), (-12, 4), (0, 13), (8, 10), (-10, -10), (14, -8)]:
        r = s.cmd(f'place feature minecraft:oak {hx + dx} 64 {hz + dz}', check=False)
        planted += 'Placed' in r
    print('trees planted:', planted)
    l0 = logs(s)
    time.sleep(8)
    print(at(s, 'info').split('\n')[1])
    print('workers:', s.cmd('execute store result score @p x run list', check=False) and
          s.cmd('kill @e[type=livingvillages:worker,limit=0]', check=False))
    n = s.cmd('execute if entity @e[type=livingvillages:worker]', check=False)
    print('worker entities:', n)
    time.sleep(90)
    l1 = logs(s)
    print(f'oak logs in stockpile: {l0} -> {l1}')
    print('lumberjack:', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:1}] Carried',
                               check=False))

    # Builder speed: start a building and measure progress per second with the builder walking over.
    print(at(s, 'build town_hall true'))
    p0 = progress(s, 'town_hall')
    time.sleep(40)
    p1 = progress(s, 'town_hall')
    print('town hall progress over 40s:', p0, '->', p1, '(no builder would be ~80 steps)')
    print(s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:0}] Pos', check=False))
