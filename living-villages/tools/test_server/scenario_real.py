"""Real gathering in a loaded village (simulated production is switched off by a huge interval):
farmers' surplus is collected, the miner digs tunnels with real drops, the builder places blocks himself."""
import re
import time

from harness import TestServer

CFG = {
    'economy': {'productionInterval': 72000},
    'construction': {'buildInterval': 10, 'autoGrowth': False},
}


def at(s, c):
    return s.cmd(f'execute positioned 0 64 0 run village {c}', check=False)


def stock(s):
    return {m.group(1): int(m.group(2)) for m in re.finditer(r'minecraft:(\w+) x(\d+)', at(s, 'info'))}


def workers(s):
    out = []
    for job in range(3):
        r = s.cmd(f'data get entity @e[type=livingvillages:worker,limit=1,nbt={{Job:{job}}}] Pos', check=False)
        m = re.findall(r'(-?[\d.]+)d', r)
        out.append(tuple(round(float(v), 1) for v in m) if m else None)
    return out


with TestServer(CFG, log_name='test_real.log') as s:
    s.cmd('forceload add -80 -80 80 80')
    for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0']:
        s.cmd('gamerule ' + rule)
    s.cmd('setblock 0 64 0 bell[attachment=floor]')
    for i in range(4):
        s.cmd(f'summon villager {i - 1} 64 3 {{NoAI:1b,PersistenceRequired:1b}}')
    s.cmd('summon villager 3 64 -3 {NoAI:1b,PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",'
          'level:1,type:"minecraft:plains"},Inventory:[{id:"minecraft:wheat",Count:30b},'
          '{id:"minecraft:carrot",Count:20b}]}')
    print(at(s, 'create'))
    start = stock(s)
    for t in ['builder_workshop', 'mine', 'lumberjack_hut']:
        print(at(s, f'build {t} true'))
    print(at(s, 'finish'))
    time.sleep(8)
    after_scan = stock(s)
    print('farmer harvest: wheat', start.get('wheat'), '->', after_scan.get('wheat'),
          '| carrot', start.get('carrot', 0), '->', after_scan.get('carrot'))
    print(at(s, 'info').split('\n')[1])

    mine = re.search(r'mine at (-?\d+) (-?\d+) (-?\d+)', at(s, 'buildings'))
    mx, my, mz = map(int, mine.groups())
    y = my + 1
    for i, (dx, dz) in enumerate([(d * sx, d * sz) for d in (8, 14, 20) for sx, sz in
                                  [(1, 0), (-1, 0), (0, 1), (0, -1)]]):
        ore = 'iron_ore' if i % 3 == 0 else 'coal_ore'
        s.cmd(f'fill {mx + dx - 3} {y} {mz + dz - 3} {mx + dx + 3} {y + 1} {mz + dz + 3} {ore} replace stone',
              check=False)

    print(at(s, 'build small_house_1 true'))
    samples = []
    for _ in range(6):
        time.sleep(5)
        samples.append((re.search(r'small_house_1 .*: (\w+ \d+)', at(s, 'buildings')).group(1), workers(s)[0]))
    print('builder (progress, position):')
    for p in samples:
        print('  ', p)
    time.sleep(120)
    end = stock(s)
    print('stockpile change over the test:')
    for k in sorted(set(end) | set(after_scan)):
        if end.get(k, 0) != after_scan.get(k, 0):
            print(f'   {k}: {after_scan.get(k, 0)} -> {end.get(k, 0)}')
    print('miner:', s.cmd('data get entity @e[type=livingvillages:worker,limit=1,nbt={Job:2}] Carried', check=False))
    print('positions builder/lumberjack/miner:', workers(s))
    print(at(s, 'buildings'))
    for _ in range(10):
        r = s.cmd('data get entity @e[type=item,limit=1] {}', check=False)
        if 'No entity' in r:
            break
        print('dropped:', re.search(r'Item: \{[^}]*\}', r).group(0) if 'Item' in r else r[:200],
              re.search(r'Pos: \[[^\]]*\]', r).group(0) if 'Pos' in r else '')
        s.cmd('kill @e[type=item,limit=1]', check=False)
