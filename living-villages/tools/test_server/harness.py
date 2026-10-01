"""Starts the dev server on a throwaway flat world and drives it over RCON, for automated checks.

    with TestServer(config={...}) as s:
        print(s.cmd('village list'))
"""
import json
import os
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'fitting_room'))
from build_world import Rcon  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RUN = os.path.join(ROOT, 'run')
LEVEL = 'test_world'
PORT, PASSWORD = 25576, 'test'

# Flat ground with grass at y=63.
FLAT = {'layers': [{'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:stone', 'height': 123},
                   {'block': 'minecraft:dirt', 'height': 3}, {'block': 'minecraft:grass_block', 'height': 1}],
        'biome': 'minecraft:plains', 'features': False, 'lakes': False, 'structure_overrides': []}


class TestServer:
    def __init__(self, config=None, log_name='test_server.log', flat=True, seed='livingvillages', fresh=True):
        self.fresh = fresh
        self.config = config or {}
        self.flat = flat
        self.seed = seed
        self.log_path = os.path.join(ROOT, 'build', log_name)
        self.proc = None
        self.rcon = None

    def __enter__(self):
        world = os.path.join(RUN, LEVEL)
        if self.fresh and os.path.exists(world):
            shutil.rmtree(world)
        os.makedirs(os.path.join(RUN, 'defaultconfigs'), exist_ok=True)
        with open(os.path.join(RUN, 'eula.txt'), 'w') as f:
            f.write('eula=true\n')
        with open(os.path.join(RUN, 'defaultconfigs', 'livingvillages-server.toml'), 'w') as f:
            for section, values in self.config.items():
                f.write(f'[{section}]\n')
                for k, v in values.items():
                    f.write(f'{k} = {json.dumps(v)}\n')
        gen = json.dumps(FLAT).replace(':', '\\:')
        with open(os.path.join(RUN, 'server.properties'), 'w') as f:
            f.write('\n'.join([
                f'level-name={LEVEL}', f'level-seed={self.seed}',
                'level-type=minecraft\\:flat' if self.flat else 'level-type=minecraft\\:normal',
                'generator-settings=' + (gen if self.flat else '{}'),
                'gamemode=creative', 'difficulty=peaceful', 'spawn-monsters=false', 'spawn-animals=true',
                'spawn-npcs=true', 'generate-structures=false', 'online-mode=false', 'max-tick-time=-1',
                'enable-rcon=true', f'rcon.port={PORT}', f'rcon.password={PASSWORD}', 'spawn-protection=0',
                'view-distance=4', 'simulation-distance=4', '']))
        os.makedirs(os.path.dirname(self.log_path), exist_ok=True)
        self.log = open(self.log_path, 'w')
        self.proc = subprocess.Popen(['./gradlew', 'runServer', '--console=plain'], cwd=ROOT, stdout=self.log,
                                     stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        for _ in range(600):
            if self.proc.poll() is not None:
                raise RuntimeError('server exited, see ' + self.log_path)
            try:
                self.rcon = Rcon(PORT, PASSWORD)
                return self
            except OSError:
                time.sleep(1)
        raise RuntimeError('RCON never came up')

    def cmd(self, command, check=True):
        if check:
            return self.rcon.cmd(command)
        return self.rcon._send(2, command)

    def world_dir(self):
        return os.path.join(RUN, LEVEL)

    def __exit__(self, *exc):
        try:
            if self.rcon:
                self.rcon._send(2, 'save-all flush')
                self.rcon._send(2, 'stop')
            self.proc.wait(timeout=120)
        except Exception:
            pass
        finally:
            if self.proc and self.proc.poll() is None:
                self.proc.kill()
            self.log.close()
