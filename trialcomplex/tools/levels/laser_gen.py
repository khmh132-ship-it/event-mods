#!/usr/bin/env python3
"""Генератор уровней «Лазер»: случайная сетка → перебор поворотов → берём уровни с единственным коротким решением.
Обозначения: . пусто, # стена, nesw — поворотный отражатель (начальное направление), NESW — фиксированный,
C — кристалл (луч должен пройти через все), G — стекло (луч проходит, игрок — нет).
Излучатели/приёмники — на краях: (сторона, индекс)."""
import itertools, random, sys

D = {'N': (0, -1), 'E': (1, 0), 'S': (0, 1), 'W': (-1, 0)}
ORDER = 'NESW'


def trace(grid, n, em_side, em_idx, rot):
    # стартовая клетка и направление
    if em_side == 'W': x, z, d = 0, em_idx, 'E'
    elif em_side == 'E': x, z, d = n - 1, em_idx, 'W'
    elif em_side == 'N': x, z, d = em_idx, 0, 'S'
    else: x, z, d = em_idx, n - 1, 'N'
    lit = set(); seen = set(); path = []
    while 0 <= x < n and 0 <= z < n:
        if (x, z, d) in seen: return None, lit, path
        seen.add((x, z, d)); path.append((x, z))
        c = grid[z][x]
        if c == '#': return None, lit, path
        if c in 'nesw': d = rot.get((x, z), c.upper())
        elif c in 'NESW': d = c
        elif c == 'C': lit.add((x, z))
        dx, dz = D[d]; x += dx; z += dz
    # вышли за край
    if x < 0: return ('W', z), lit, path
    if x >= n: return ('E', z), lit, path
    if z < 0: return ('N', x), lit, path
    return ('S', x), lit, path


def solve(grid, n, emitters, targets):
    rots = [(x, z) for z in range(n) for x in range(n) if grid[z][x] in 'nesw']
    crystals = {(x, z) for z in range(n) for x in range(n) if grid[z][x] == 'C'}
    sols = []
    for combo in itertools.product(ORDER, repeat=len(rots)):
        rot = dict(zip(rots, combo))
        lit = set(); ok = True
        for (es, ei), tgt in zip(emitters, targets):
            end, l, _ = trace(grid, n, es, ei, rot)
            lit |= l
            if end != tgt: ok = False; break
        if ok and lit >= crystals:
            sols.append(rot)
            if len(sols) > 40: break
    return rots, sols


def gen(n, n_rot, n_fixed, n_cry, n_wall, n_em, seed, min_changes):
    rnd = random.Random(seed)
    for attempt in range(20000):
        grid = [['.'] * n for _ in range(n)]
        cells = [(x, z) for z in range(n) for x in range(n)]
        rnd.shuffle(cells)
        it = iter(cells)
        for _ in range(n_wall): x, z = next(it); grid[z][x] = '#'
        for _ in range(n_rot): x, z = next(it); grid[z][x] = rnd.choice('nesw')
        for _ in range(n_fixed): x, z = next(it); grid[z][x] = rnd.choice('NESW')
        for _ in range(n_cry): x, z = next(it); grid[z][x] = 'C'
        sides = ['W', 'N', 'S', 'E']
        emitters = [('W', rnd.randrange(n))] + [('N', rnd.randrange(n))][: n_em - 1]
        targets = []
        for k in range(n_em):
            s = rnd.choice(['E', 'S', 'N'] if k == 0 else ['S', 'E'])
            targets.append((s, rnd.randrange(n)))
        g = [''.join(r) for r in grid]
        rots, sols = solve(g, n, emitters, targets)
        if not sols or len(sols) > 3: continue
        # начальное состояние не решено и требует много поворотов
        init = {(x, z): g[z][x].upper() for (x, z) in rots}
        changes = min(sum(1 for k in s if s[k] != init[k]) for s in sols)
        if changes < min_changes: continue
        # все повороты должны быть задействованы на пути (иначе лишние)
        used = set()
        for (es, ei) in emitters:
            _, _, path = trace(g, n, es, ei, sols[0]); used |= set(path)
        if sum(1 for r in rots if r in used) < len(rots) - 1: continue
        return g, emitters, targets, sols[0], changes
    return None


if __name__ == '__main__':
    specs = [  # n, rot, fixed, cry, wall, emitters, seed, min_changes
        (7, 4, 1, 0, 4, 1, 11, 3),
        (9, 5, 2, 2, 8, 1, 22, 4),
        (9, 6, 3, 3, 10, 1, 33, 5),
        (11, 7, 3, 3, 14, 1, 44, 5),
        (9, 6, 2, 2, 8, 2, 55, 5),
    ]
    for spec in specs:
        r = gen(*spec)
        if not r: print('FAILED', spec); continue
        g, em, tg, sol, ch = r
        print(f'// n={spec[0]} changes={ch} emitters={em} targets={tg}')
        print('new String[]{' + ', '.join(f'"{row}"' for row in g) + '},')
        print('  solution:', {f'{k[0]},{k[1]}': v for k, v in sol.items()})
