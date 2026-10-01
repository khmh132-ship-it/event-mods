"""Construction check: a village with villagers builds by command, then grows on its own."""
import re
import sys
import time

from harness import TestServer

FAST = {
    'economy': {'productionInterval': 20, 'productionMultiplier': 4.0},
    'construction': {'buildInterval': 1, 'maxBuildStepsPerSecond': 600, 'planInterval': 40},
}


def at(s, x, y, z, c):
    return s.cmd(f'execute positioned {x} {y} {z} run {c}', check=False)


def main():
    grow_seconds = int(sys.argv[1]) if len(sys.argv) > 1 else 90
    with TestServer(FAST) as s:
        s.cmd('forceload add -64 -64 64 64')
        for rule in ['doDaylightCycle false', 'doMobSpawning false', 'randomTickSpeed 0']:
            s.cmd('gamerule ' + rule)
        # A couple of trees and a pond the site finder has to avoid.
        for x, z in [(14, 0), (16, 3), (-12, 10)]:
            s.cmd(f'place feature minecraft:oak {x} 64 {z}', check=False)
        s.cmd('fill -20 63 -20 -14 63 -14 water')
        s.cmd('setblock 0 64 0 bell[attachment=floor]')
        for i, prof in enumerate(['none', 'none', 'farmer', 'mason', 'none', 'shepherd']):
            s.cmd(f'summon villager {i - 2} 64 3 {{NoAI:1b,PersistenceRequired:1b,'
                  f'VillagerData:{{profession:"minecraft:{prof}",level:1,type:"minecraft:plains"}}}}')
        print(at(s, 0, 64, 0, 'village create'))
        time.sleep(6)  # let the 5-second scan observe the villagers
        print(at(s, 0, 64, 0, 'village growth false'))
        types = at(s, 0, 64, 0, 'village types')
        missing = [l for l in types.split('\n') if 'MISSING' in l]
        print('types loaded:', len(types.split('\n')), 'missing:', missing)
        for t in ['lumberjack_hut', 'small_house_1', 'mine', 'large_farm_1']:
            print(at(s, 0, 64, 0, f'village build {t} true'))
        print(at(s, 0, 64, 0, 'village finish'))
        print(at(s, 0, 64, 0, 'village buildings'))
        print(at(s, 0, 64, 0, 'village growth true'))
        time.sleep(grow_seconds)
        print(at(s, 0, 64, 0, 'village buildings'))
        print(at(s, 0, 64, 0, 'village info'))
        print('dropped items:', s.cmd('execute store result score #n lv run kill @e[type=item]', check=False))
        print(s.cmd('kill @e[type=item]', check=False))
    print('log:', s.log_path)
    import subprocess, os
    out = os.path.join(os.path.dirname(s.log_path), 'test_map.png')
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), 'render_map.py'), s.world_dir(), out,
                    '-56,-56,56,56', '5'])
    print('map:', out)


if __name__ == '__main__':
    main()
