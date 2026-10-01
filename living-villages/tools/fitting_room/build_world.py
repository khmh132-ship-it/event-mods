"""Builds the plains "fitting room": a void world where every vanilla plains village building and every
Living Villages draft stands on its own labelled platform, next to a structure block set up to save it.

Usage (from living-villages/):  python3 tools/fitting_room/build_world.py
Needs: ./gradlew build done once, nbtlib, network access to the Forge/Mojang hosts.
"""
import json
import os
import shutil
import socket
import struct
import subprocess
import sys
import time
import zipfile

import nbtlib
from nbtlib.tag import Byte, Compound, List

sys.path.insert(0, os.path.dirname(__file__))
from drafts import DRAFTS, GROUND_OFFSET  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RUN = os.path.join(ROOT, 'run')
LEVEL = 'fitting_room_plains'
WORLD = os.path.join(RUN, LEVEL)
OUT_ZIP = os.path.join(ROOT, 'build', 'fitting_room_plains.zip')
STRUCTURES = os.path.join(WORLD, 'generated', 'livingvillages', 'structures', 'plains')
CLIENT_JAR = os.path.expanduser('~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar')
VANILLA_PREFIX = 'data/minecraft/structures/village/plains/'
GROUND_Y = 63
RCON_PORT, RCON_PASS = 25575, 'fittingroom'

# Rows of the fitting room: (title, [(template id under livingvillages:plains/, label)]).
VANILLA_HOUSES = 'houses/plains_'
ROWS = [
    ('Центр деревни (ваниль)', [
        ('vanilla/fountain_01', 'Фонтан'), ('vanilla/meeting_point_1', 'Площадь 1'),
        ('vanilla/meeting_point_2', 'Площадь 2'), ('vanilla/meeting_point_3', 'Площадь 3'),
        ('vanilla/meeting_point_4', 'Площадь 4'), ('vanilla/meeting_point_5', 'Площадь 5'),
        ('vanilla/lamp_1', 'Фонарь'), ('vanilla/accessory_1', 'Декор'),
    ]),
    ('Жильё (ваниль)', [(f'vanilla/small_house_{i}', f'Малый дом {i}') for i in range(1, 9)] + [
        ('vanilla/medium_house_1', 'Средний дом 1'), ('vanilla/medium_house_2', 'Средний дом 2'),
        ('vanilla/big_house_1', 'Большой дом'),
    ]),
    ('Профессии (ваниль)', [
        ('vanilla/armorer_house_1', 'Бронник'), ('vanilla/butcher_shop_1', 'Мясник 1'),
        ('vanilla/butcher_shop_2', 'Мясник 2'), ('vanilla/cartographer_1', 'Картограф'),
        ('vanilla/fisher_cottage_1', 'Рыбак'), ('vanilla/fletcher_house_1', 'Лучник'),
        ('vanilla/library_1', 'Библиотека 1'), ('vanilla/library_2', 'Библиотека 2'),
        ('vanilla/masons_house_1', 'Каменщик'), ('vanilla/shepherds_house_1', 'Пастух'),
        ('vanilla/tannery_1', 'Кожевник'), ('vanilla/temple_3', 'Храм 1'), ('vanilla/temple_4', 'Храм 2'),
        ('vanilla/tool_smith_1', 'Инструментальщик'), ('vanilla/weaponsmith_1', 'Оружейник'),
    ]),
    ('Фермы и скот (ваниль)', [
        ('vanilla/small_farm_1', 'Малая ферма'), ('vanilla/large_farm_1', 'Большая ферма'),
        ('vanilla/animal_pen_1', 'Загон 1'), ('vanilla/animal_pen_2', 'Загон 2'),
        ('vanilla/animal_pen_3', 'Загон 3'), ('vanilla/stable_1', 'Конюшня 1'), ('vanilla/stable_2', 'Конюшня 2'),
    ]),
    ('Новые: добыча и ремесло', [(n, DRAFTS[n][1]) for n in
                                 ('lumberjack_hut', 'mine', 'warehouse', 'builder_workshop', 'sawmill')]),
    ('Новые: оборона', [(n, DRAFTS[n][1]) for n in
                        ('barracks', 'watchtower', 'wall_tower', 'wall_straight', 'wall_gate', 'golem_pad')]),
    ('Новые: центр', [(n, DRAFTS[n][1]) for n in ('town_hall', 'tavern', 'quest_board')]),
]


def vanilla_source(name):
    short = name.split('/', 1)[1]
    if short.startswith(('fountain', 'meeting_point_1', 'meeting_point_2', 'meeting_point_3')):
        return f'{VANILLA_PREFIX}town_centers/plains_{short}.nbt'
    if short == 'lamp_1':
        return f'{VANILLA_PREFIX}plains_lamp_1.nbt'
    return f'{VANILLA_PREFIX}{VANILLA_HOUSES}{short}.nbt'


def parse_state(s):
    """'minecraft:oak_stairs[facing=east,half=bottom]' -> Compound palette entry."""
    name, _, props = s.partition('[')
    entry = Compound({'Name': nbtlib.String(name)})
    if props:
        entry['Properties'] = Compound({k: nbtlib.String(v) for k, v in
                                        (p.split('=') for p in props.rstrip(']').split(','))})
    return entry


def convert_vanilla(jar, src, dst):
    """Does what village generation does: air is not placed and jigsaws turn into their final state."""
    with jar.open(src) as f:
        data = f.read()
    tmp = dst + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(data)
    t = nbtlib.load(tmp)
    os.remove(tmp)
    palette = list(t['palette'])
    blocks = []
    for b in t['blocks']:
        entry = palette[int(b['state'])]
        name = str(entry['Name'])
        if name == 'minecraft:air':
            continue
        if name == 'minecraft:jigsaw':
            final = parse_state(str(b['nbt']['final_state']))
            if str(final['Name']) in ('minecraft:air', 'minecraft:structure_void'):
                continue
            palette.append(final)
            b = Compound({'pos': b['pos'], 'state': nbtlib.Int(len(palette) - 1)})
        blocks.append(b)
    t['palette'] = List[Compound](palette)
    t['blocks'] = List[Compound](blocks)
    t.save(dst, gzipped=True)
    return [int(v) for v in t['size']]


def write_templates():
    os.makedirs(os.path.join(STRUCTURES, 'vanilla'), exist_ok=True)
    sizes = {}
    with zipfile.ZipFile(CLIENT_JAR) as jar:
        for _, items in ROWS:
            for name, _ in items:
                dst = os.path.join(STRUCTURES, name + '.nbt')
                if name.startswith('vanilla/'):
                    sizes[name] = convert_vanilla(jar, vanilla_source(name), dst)
                else:
                    t = DRAFTS[name][0]()
                    t.save(dst)
                    sizes[name] = list(t.size())
    return sizes


def layout(sizes):
    """Returns [(name, label, x, z, size)] plus row headers, rows going south, items going east."""
    placed, headers, z = [], [], 0
    for title, items in ROWS:
        depth = max(sizes[n][2] for n, _ in items)
        headers.append((title, z))
        x = 0
        for name, label in items:
            placed.append((name, label, x, z, sizes[name]))
            x += sizes[name][0] + 7
        z += depth + 9
    return placed, headers


# --- RCON -------------------------------------------------------------------------------------------------

class Rcon:
    def __init__(self, port, password):
        self.sock = socket.create_connection(('127.0.0.1', port), timeout=60)
        self.req = 0
        self._send(3, password)

    def _send(self, kind, body):
        self.req += 1
        payload = struct.pack('<ii', self.req, kind) + body.encode('utf-8') + b'\x00\x00'
        self.sock.sendall(struct.pack('<i', len(payload)) + payload)
        length = struct.unpack('<i', self._read(4))[0]
        data = self._read(length)
        rid = struct.unpack('<i', data[:4])[0]
        if rid == -1:
            raise RuntimeError('RCON auth failed')
        return data[8:-2].decode('utf-8', 'replace')

    def _read(self, n):
        buf = b''
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError('RCON closed')
            buf += chunk
        return buf

    def cmd(self, command):
        out = self._send(2, command)
        if any(w in out for w in ('Unknown', 'Incorrect', 'Invalid', 'not loaded', 'Expected', 'Failed', 'Could not', 'not found')):
            raise RuntimeError(f'{command!r} -> {out}')
        return out


def snbt_text(s):
    # Non-ASCII goes through JSON \\u escapes so the command stays plain ASCII.
    return json.dumps({'text': s}).replace('\\', '\\\\').replace("'", "\\'")


def sign_cmd(x, y, z, lines, block='oak_sign[rotation=0]'):
    msgs = ','.join(f"'{snbt_text(l)}'" for l in (lines + [''] * 4)[:4])
    return f'setblock {x} {y} {z} {block}{{front_text:{{messages:[{msgs}]}}}} replace'


def build(rcon, placed, headers):
    xs = [p[2] for p in placed] + [p[2] + p[4][0] for p in placed]
    zs = [p[3] for p in placed] + [p[3] + p[4][2] for p in placed]
    min_x, max_x, min_z, max_z = min(xs) - 8, max(xs) + 8, min(zs) - 8, max(zs) + 8
    rcon.cmd(f'forceload add {min_x} {min_z} {max_x} {max_z}')
    for rule, value in [('doDaylightCycle', 'false'), ('doWeatherCycle', 'false'), ('doMobSpawning', 'false'),
                        ('randomTickSpeed', '0'), ('doFireTick', 'false'), ('mobGriefing', 'false'),
                        ('doPatrolSpawning', 'false'), ('doTraderSpawning', 'false'), ('doInsomnia', 'false'),
                        ('spawnRadius', '0')]:
        rcon.cmd(f'gamerule {rule} {value}')
    rcon.cmd('time set noon')
    rcon.cmd('weather clear')

    for name, label, x, z, (sx, sy, sz) in placed:
        rcon.cmd(f'fill {x - 2} {GROUND_Y} {z - 2} {x + sx + 1} {GROUND_Y} {z + sz + 2} grass_block')
        base_y = GROUND_Y - GROUND_OFFSET.get(name, 0)
        rcon.cmd(f'place template livingvillages:plains/{name} {x} {base_y} {z}')
        rcon.cmd(f'setblock {x - 1} {GROUND_Y} {z - 1} structure_block[mode=save]{{name:"livingvillages:plains/{name}",'
                 f'author:"living-villages",metadata:"",posX:1,posY:{base_y - GROUND_Y},posZ:1,'
                 f'sizeX:{sx},sizeY:{sy},sizeZ:{sz},rotation:"NONE",mirror:"NONE",mode:"SAVE",ignoreEntities:1b,'
                 f'powered:0b,showair:0b,showboundingbox:1b,integrity:1.0f,seed:0L}} replace')
        short = name.split('/')[-1]
        lines = [label, short[:15]] + ([short[15:30]] if len(short) > 15 else []) + \
                (['ванильный'] if name.startswith('vanilla/') else ['черновик'])
        rcon.cmd(sign_cmd(x, GROUND_Y + 1, z + sz + 1, lines))

    for title, z in headers:
        rcon.cmd(f'fill -6 {GROUND_Y} {z - 1} -4 {GROUND_Y} {z + 1} stone_bricks')
        rcon.cmd(f'setblock -5 {GROUND_Y + 1} {z} oak_log')
        words = title.split(' ', 1)
        rcon.cmd(sign_cmd(-5, GROUND_Y + 1, z + 1, words + ['->'], block='oak_wall_sign[facing=south]'))

    rcon.cmd(f'fill -8 {GROUND_Y} -8 -3 {GROUND_Y} -3 stone_bricks')
    rcon.cmd(f'setworldspawn -5 {GROUND_Y + 1} -5')
    rcon.cmd('save-all flush')


def main():
    sizes = write_templates()
    placed, headers = layout(sizes)
    os.makedirs(RUN, exist_ok=True)
    with open(os.path.join(RUN, 'eula.txt'), 'w') as f:
        f.write('eula=true\n')
    gen = json.dumps({'layers': [{'block': 'minecraft:air', 'height': 1}], 'biome': 'minecraft:plains',
                      'features': False, 'lakes': False, 'structure_overrides': []})
    with open(os.path.join(RUN, 'server.properties'), 'w') as f:
        f.write('\n'.join([
            f'level-name={LEVEL}', 'level-type=minecraft\\:flat', 'generator-settings=' + gen.replace(':', '\\:'),
            'gamemode=creative', 'difficulty=peaceful', 'spawn-monsters=false', 'spawn-animals=false',
            'spawn-npcs=false', 'generate-structures=false', 'online-mode=false', 'max-tick-time=-1',
            'enable-rcon=true', f'rcon.port={RCON_PORT}', f'rcon.password={RCON_PASS}', 'spawn-protection=0',
            'view-distance=4', 'simulation-distance=4', '']))

    log = open(os.path.join(ROOT, 'build', 'fitting_room_server.log'), 'w')
    server = subprocess.Popen(['./gradlew', 'runServer', '--console=plain'], cwd=ROOT, stdout=log,
                              stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    try:
        rcon = None
        for _ in range(600):
            if server.poll() is not None:
                raise RuntimeError('server exited, see build/fitting_room_server.log')
            try:
                rcon = Rcon(RCON_PORT, RCON_PASS)
                break
            except OSError:
                time.sleep(1)
        if rcon is None:
            raise RuntimeError('RCON never came up')
        build(rcon, placed, headers)
        rcon.cmd('stop')
        server.wait(timeout=180)
    finally:
        if server.poll() is None:
            server.kill()

    # Singleplayer needs cheats on for structure blocks and /place.
    level_dat = os.path.join(WORLD, 'level.dat')
    dat = nbtlib.load(level_dat)
    dat['Data']['allowCommands'] = Byte(1)
    dat['Data']['LevelName'] = nbtlib.String('Living Villages - plains fitting room')
    dat.save()

    os.makedirs(os.path.dirname(OUT_ZIP), exist_ok=True)
    with zipfile.ZipFile(OUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(WORLD):
            for fn in files:
                if fn == 'session.lock':
                    continue
                full = os.path.join(dirpath, fn)
                z.write(full, os.path.join(LEVEL, os.path.relpath(full, WORLD)))
    print(f'{len(placed)} buildings placed -> {OUT_ZIP}')


if __name__ == '__main__':
    if os.path.exists(WORLD):
        shutil.rmtree(WORLD)
    main()
