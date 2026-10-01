"""Real terrain: a generated vanilla plains village is discovered and grows; construction catches up after its
chunks were unloaded."""
import os
import re
import subprocess
import sys
import time

from harness import TestServer

FAST = {
    'economy': {'productionInterval': 20, 'productionMultiplier': 4.0},
    'construction': {'buildInterval': 2, 'maxBuildStepsPerSecond': 600, 'planInterval': 40},
}


def main():
    grow_seconds = int(sys.argv[1]) if len(sys.argv) > 1 else 120
    with TestServer(FAST, flat=False, log_name='test_natural.log') as s:
        for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0', 'doPatrolSpawning false',
                     'doTraderSpawning false']:
            s.cmd('gamerule ' + rule)
        found = s.cmd('locate biome minecraft:plains')
        print(found)
        m = re.search(r'\[(-?\d+), (~|-?\d+), (-?\d+)\]', found)
        x, z = int(m.group(1)), int(m.group(3))
        s.cmd(f'forceload add {x - 80} {z - 80} {x + 80} {z + 80}')
        print(s.cmd(f'place structure minecraft:village_plains {x} 64 {z}', check=False))
        time.sleep(8)
        print(s.cmd(f'execute positioned {x} 70 {z} run village discover', check=False))
        lst = s.cmd('village list', check=False)
        print(lst)
        m = re.search(r'bell (-?\d+) (-?\d+) (-?\d+)', lst)
        if not m:
            print('NO VILLAGE FOUND')
            return
        bx, by, bz = map(int, m.groups())

        def at(c):
            return s.cmd(f'execute positioned {bx} {by} {bz} run village {c}', check=False)

        time.sleep(6)
        print(at('info'))
        time.sleep(grow_seconds)
        print(at('buildings'))

        # Unload while something is being built, then come back.
        before = at('buildings')
        print('--- unloading')
        s.cmd(f'forceload remove {x - 80} {z - 80} {x + 80} {z + 80}')
        time.sleep(25)
        s.cmd(f'forceload add {x - 80} {z - 80} {x + 80} {z + 80}')
        time.sleep(4)
        after = at('buildings')
        print('before unload:\n' + before)
        print('after reload:\n' + after)
        print(at('info'))
        print(at('growth false'))
        print('plan report:\n' + at('plan'))
        print('items:', s.cmd('kill @e[type=item]', check=False))
    out = os.path.join(os.path.dirname(s.log_path), 'test_natural.png')
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), 'render_map.py'), s.world_dir(), out,
                    f'{bx - 70},{bz - 70},{bx + 70},{bz + 70}', '4'])
    print('map:', out)


if __name__ == '__main__':
    main()
