#!/usr/bin/env python3
"""Генератор уровней «Склада» (кооперативный сокобан).
'#' стена, '.' пол, 'x' цель, 'b' лёгкий ящик, 'H' тяжёлый ящик (толкают вдвоём: нужна свободная клетка
ещё и за спиной толкающего), 'B'/'h' — ящик на цели, '@' старт игроков.
Уровень строится обратными ходами (тянем ящики от целей), затем BFS проверяет решаемость и считает
минимальное число толчков."""
import random, sys
from collections import deque

DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1)]

EXTRA = set()  # клетки только для игроков (проход в западной стене)

def region(W, H, walls, crates, start):
    seen = {start}; q = [start]
    while q:
        x, z = q.pop()
        for dx, dz in DIRS:
            n = (x + dx, z + dz)
            ok = (0 <= n[0] < W and 0 <= n[1] < H and n not in walls) or n in EXTRA
            if ok and n not in crates and n not in seen:
                seen.add(n); q.append(n)
    return seen

def free(W, H, walls, crates, c):
    return 0 <= c[0] < W and 0 <= c[1] < H and c not in walls and c not in crates

def solve(W, H, walls, light, heavy, targets, agent, limit=400000):
    """BFS по толчкам. Возвращает (минимум толчков, список толчков [(x,z,dx,dz)]) или (None, None)."""
    def key(l, h, a):
        reg = region(W, H, walls, l | h, a)
        return (l, h, min(reg)), reg
    k0, reg0 = key(frozenset(light), frozenset(heavy), agent)
    prev = {k0: None}
    q = deque([(k0, reg0)])
    while q:
        (l, h, _), reg = k = q.popleft()
        if (l | h) == targets:
            path = []; cur = k[0]
            while prev[cur]: cur, mv = prev[cur]; path.append(mv)
            return len(path), path[::-1]
        crates = l | h
        for c in crates:
            for dx, dz in DIRS:
                behind = (c[0] - dx, c[1] - dz); dest = (c[0] + dx, c[1] + dz)
                if behind not in reg or not free(W, H, walls, crates, dest): continue
                if c in h and not free(W, H, walls, crates, (c[0] - 2 * dx, c[1] - 2 * dz)): continue
                if dead(W, H, walls, targets, dest): continue
                nl, nh = (l - {c}) | {dest} if c in l else l, (h - {c}) | {dest} if c in h else h
                nk, nreg = key(frozenset(nl), frozenset(nh), c)
                if nk in prev: continue
                prev[nk] = (k[0], (c[0], c[1], dx, dz))
                if len(prev) > limit: return None, None
                q.append((nk, nreg))
    return None, None

def dead(W, H, walls, targets, c):
    """Ящик в углу не на цели — тупик."""
    if c in targets: return False
    blocked = lambda p: not (0 <= p[0] < W and 0 <= p[1] < H) or p in walls
    hor = blocked((c[0] - 1, c[1])) or blocked((c[0] + 1, c[1]))
    ver = blocked((c[0], c[1] - 1)) or blocked((c[0], c[1] + 1))
    return hor and ver

def generate(W, H, n_light, n_heavy, wall_frac, pulls, rnd):
    while True:
        walls = set()
        for x in range(W):
            for z in range(H):
                if rnd.random() < wall_frac: walls.add((x, z))
        floor = [(x, z) for x in range(W) for z in range(H) if (x, z) not in walls]
        if len(floor) < W * H * 0.6: continue
        if len(region(W, H, walls, set(), floor[0])) != len(floor): continue
        cand = [c for c in floor if not dead(W, H, walls, set(), c)]
        if len(cand) < n_light + n_heavy + 4: continue
        tg = rnd.sample(cand, n_light + n_heavy)
        heavy = set(tg[:n_heavy]); light = set(tg[n_heavy:]); targets = frozenset(tg)
        agent = rnd.choice([c for c in floor if c not in targets])
        # обратные ходы: тянем ящик на себя
        for _ in range(pulls):
            crates = light | heavy
            reg = region(W, H, walls, crates, agent)
            moves = []
            for c in crates:
                for dx, dz in DIRS:
                    p = (c[0] + dx, c[1] + dz); p2 = (c[0] + 2 * dx, c[1] + 2 * dz)
                    if p in reg and free(W, H, walls, crates, p2):
                        if c in heavy and not free(W, H, walls, crates, (c[0] + 3 * dx, c[1] + 3 * dz)): continue
                        moves.append((c, dx, dz))
            if not moves: break
            c, dx, dz = rnd.choice(moves)
            nc = (c[0] + dx, c[1] + dz)
            if c in heavy: heavy = (heavy - {c}) | {nc}
            else: light = (light - {c}) | {nc}
            agent = (c[0] + 2 * dx, c[1] + 2 * dz)
        if (light | heavy) & targets: continue
        return walls, light, heavy, targets, agent

def render(W, H, walls, light, heavy, targets, agent):
    rows = []
    for z in range(H):
        r = ''
        for x in range(W):
            c = (x, z)
            r += '#' if c in walls else 'b' if c in light else 'H' if c in heavy else 'x' if c in targets else '@' if c == agent else '.'
        rows.append(r)
    return rows

if __name__ == '__main__':
    W, H, nl, nh, minp, seed = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
    rnd = random.Random(seed)
    best = None
    for attempt in range(400):
        walls, light, heavy, targets, agent = generate(W, H, nl, nh, 0.18, 300, rnd)
        rows = [z for z in range(H) if (0, z) not in walls and (0, z) not in light | heavy]
        if not rows: continue
        zg = rnd.choice(rows)
        EXTRA.clear(); EXTRA.add((-1, zg))
        if len(region(W, H, walls, set(), (-1, zg))) != W * H - len(walls) + 1: continue
        agent = (-1, zg)
        n, path = solve(W, H, walls, light, heavy, targets, agent)
        if n is None: continue
        # «линии ящиков»: сколько раз меняли ящик/направление — ближе к человеческой сложности
        lines = sum(1 for i, m in enumerate(path) if i == 0 or (m[0] - m[2], m[1] - m[3], m[2], m[3]) != (path[i - 1][0], path[i - 1][1], path[i - 1][2], path[i - 1][3]))
        if best is None or lines > best[0]:
            best = (lines, n, [('>' if z == zg else '#') + r for z, r in enumerate(render(W, H, walls, light, heavy, targets, None))], path)
            print(f'attempt {attempt}: pushes={n} lines={lines}', file=sys.stderr); print('\n'.join(best[2]) + '\nsol=' + ';'.join(f'{x},{z},{dx},{dz}' for x, z, dx, dz in path), file=sys.stderr, flush=True)
            if lines >= minp: break
    print('\n'.join(best[2])); print(f'pushes={best[1]} lines={best[0]}')
    print('sol=' + ';'.join(f'{x},{z},{dx},{dz}' for x, z, dx, dz in best[3]))
