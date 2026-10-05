#!/usr/bin/env python3
"""Упаковка построенного мира (run/world) в архив для одиночной игры: dist/<имя>.zip."""
import os, shutil, sys, zipfile
import nbtlib
from nbtlib.tag import Byte, Int, String

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SRC = os.path.join(ROOT, 'run', 'world')
NAME = 'Испытательный комплекс'
DIST = os.path.join(ROOT, 'dist')
SKIP_DIRS = {'playerdata', 'stats', 'advancements', 'serverconfig'}
SKIP_FILES = {'session.lock', 'level.dat_old'}


def main():
    global SRC
    if len(sys.argv) > 1:
        SRC = os.path.abspath(sys.argv[1])
    if not os.path.exists(os.path.join(SRC, 'level.dat')):
        sys.exit('нет run/world — сначала постройте мир (runServer -Pautobuild=true)')
    tmp = os.path.join(ROOT, 'build', 'pkg', NAME)
    shutil.rmtree(os.path.dirname(tmp), ignore_errors=True)
    shutil.copytree(SRC, tmp, ignore=lambda d, names: [n for n in names if n in SKIP_DIRS or n in SKIP_FILES])
    lvl = nbtlib.load(os.path.join(tmp, 'level.dat'))
    data = lvl['Data']
    data['LevelName'] = String(NAME)
    data['GameType'] = Int(2)        # приключение
    data['Difficulty'] = Byte(0)     # мирный
    data['allowCommands'] = Byte(1)  # чтобы хост мог спасти ситуацию командами
    data['DifficultyLocked'] = Byte(1)
    if 'Player' in data:
        del data['Player']
    lvl.save()
    os.makedirs(DIST, exist_ok=True)
    out = os.path.join(DIST, NAME + '.zip')
    if os.path.exists(out):
        os.remove(out)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for d, _, files in os.walk(tmp):
            for f in files:
                full = os.path.join(d, f)
                z.write(full, os.path.relpath(full, os.path.dirname(tmp)))
    print('мир упакован:', out, os.path.getsize(out) // 1024, 'КБ')


if __name__ == '__main__':
    main()
