#!/usr/bin/env python3
"""Конструктивный генератор уровней «Лазер»: сначала путь луча, потом отражатели на поворотах,
кристаллы на прямых участках, ложные отражатели и стены вне пути. Проверка решателем."""
import random, itertools
from laser_gen import trace, D, ORDER

def turn_path(rnd, n, start, d0, turns, occupied):
    """Ломаная от start в направлении d0 с заданным числом поворотов до выхода за край."""
    for attempt in range(3000):
        x, z = start; d = d0; path = [(x, z)]; corners = {}; used = set(occupied) | {(x, z)}
        t = 0; ok = True
        while True:
            # идём прямо 1..4, потом поворачиваем (если ещё нужно)
            seg = rnd.randint(1, 4) if t < turns else 99
            for _ in range(seg):
                dx, dz = D[d]; nx, nz = x + dx, z + dz
                if not (0 <= nx < n and 0 <= nz < n):
                    if t < turns: ok = False
                    break
                if (nx, nz) in used: ok = False; break
                x, z = nx, nz; used.add((x, z)); path.append((x, z))
            else:
                if t < turns:
                    nd = rnd.choice([c for c in ORDER if c != d and D[c] != (-D[d][0], -D[d][1])])
                    corners[(x, z)] = nd; d = nd; t += 1
                    continue
            if not ok: break
            # вышли за край или упёрлись
            dx, dz = D[d]
            if not (0 <= x + dx < n and 0 <= z + dz < n) and t >= turns:
                side = {'E': 'E', 'W': 'W', 'N': 'N', 'S': 'S'}[d]
                idx = z if d in 'EW' else x
                return path, corners, (side, idx)
            break
    return None

def make(n, turns, n_fixed, n_cry, n_decoy, n_wall, seed, two=False):
    rnd = random.Random(seed)
    for attempt in range(500):
        grid = [['.'] * n for _ in range(n)]
        emitters, targets, allpath, sol = [], [], set(), {}
        starts = [(('W', rnd.randrange(1, n - 1)), 'E')] + ([(('N', rnd.randrange(1, n - 1)), 'S')] if two else [])
        okall = True
        for (side, idx), d0 in starts:
            st = (0, idx) if side == 'W' else (idx, 0)
            r = turn_path(rnd, n, st, d0, turns, allpath)
            if not r: okall = False; break
            path, corners, tgt = r
            if tgt in targets or tgt[0] == side and tgt[1] == idx: okall = False; break
            emitters.append((side, idx)); targets.append(tgt); allpath |= set(path)
            cs = list(corners.items()); rnd.shuffle(cs)
            for i, ((x, z), nd) in enumerate(cs):
                if grid[z][x] != '.': okall = False
                if i < n_fixed: grid[z][x] = nd
                else:
                    grid[z][x] = rnd.choice([c for c in 'nesw' if c.upper() != nd]); sol[(x, z)] = nd
            straight = [p for p in path if p not in corners and grid[p[1]][p[0]] == '.']
            for (x, z) in rnd.sample(straight, min(n_cry, len(straight))): grid[z][x] = 'C'
        if not okall: continue
        free = [(x, z) for z in range(n) for x in range(n) if (x, z) not in allpath]
        rnd.shuffle(free)
        for (x, z) in free[:n_decoy]: grid[z][x] = rnd.choice('nesw')
        for (x, z) in free[n_decoy:n_decoy + n_wall]: grid[z][x] = '#'
        g = [''.join(r) for r in grid]
        # проверка решения
        rot = {k: v for k, v in sol.items()}
        for z in range(n):
            for x in range(n):
                if g[z][x] in 'nesw' and (x, z) not in rot: rot[(x, z)] = g[z][x].upper()
        lit = set(); good = True
        for (es, ei), tg in zip(emitters, targets):
            end, l, _ = trace(g, n, es, ei, rot); lit |= l
            if end != tg: good = False
        cry = {(x, z) for z in range(n) for x in range(n) if g[z][x] == 'C'}
        if not good or not lit >= cry: continue
        # начальное состояние не должно быть решением
        init = {(x, z): g[z][x].upper() for z in range(n) for x in range(n) if g[z][x] in 'nesw'}
        e0 = [trace(g, n, es, ei, init)[0] for es, ei in emitters]
        if e0 == targets: continue
        return g, emitters, targets, sol
    return None

if __name__ == '__main__':
    specs = [  # n, turns, fixed, cry, decoy, wall, seed, two
        ('L1', 7, 3, 0, 0, 1, 5, 101, False),
        ('L2', 9, 4, 1, 2, 2, 8, 202, False),
        ('L3', 9, 6, 2, 3, 3, 12, 303, False),
        ('L4', 11, 7, 2, 3, 4, 16, 404, False),
        ('L5', 11, 4, 1, 2, 3, 14, 505, True),
    ]
    for name, *spec in specs:
        r = make(*spec)
        if not r: print('FAILED', name); continue
        g, em, tg, sol = r
        e = ', '.join(f'"{s}{i}"' for s, i in em); t = ', '.join(f'"{s}{i}"' for s, i in tg)
        print(f'// {name}: solution ' + ' '.join(f'{x},{z}={v}' for (x, z), v in sol.items()))
        print(f'new Level(new String[]{{{", ".join(chr(34)+row+chr(34) for row in g)}}}, new String[]{{{e}}}, new String[]{{{t}}}),')
