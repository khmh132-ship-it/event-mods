#!/usr/bin/env python3
"""Проверка картинок «Рисунка» (японский кроссворд): подсказки и единственность решения.
Картинка — строки, '.' пусто, любая буква — закрашено (буква = цвет при открытии)."""
import sys, itertools
from functools import lru_cache

def clues(line):
    out, n = [], 0
    for c in line:
        if c: n += 1
        elif n: out.append(n); n = 0
    if n: out.append(n)
    return out or [0]

def fits(cl, n):
    """Все раскладки строки длины n под подсказку cl (битовые кортежи)."""
    cl = [c for c in cl if c]
    res = []
    def rec(i, pos, cur):
        if i == len(cl):
            res.append(tuple(cur + [0] * (n - len(cur)))); return
        rest = sum(cl[i + 1:]) + len(cl) - i - 1
        for s in range(pos, n - rest - cl[i] + 1):
            rec(i + 1, s + cl[i] + 1, cur + [0] * (s - len(cur)) + [1] * cl[i] + ([0] if i + 1 < len(cl) else []))
    rec(0, 0, [])
    return [r[:n] for r in res]

def count_solutions(rows, cols, limit=2):
    H, W = len(rows), len(cols)
    rcand = [fits(r, W) for r in rows]
    ccand = [fits(c, H) for c in cols]
    grid = [[None] * W for _ in range(H)]
    def propagate(rc, cc):
        changed = True
        while changed:
            changed = False
            for i in range(H):
                rc[i] = [r for r in rc[i] if all(grid[i][j] is None or grid[i][j] == r[j] for j in range(W))]
                if not rc[i]: return False
                for j in range(W):
                    vals = {r[j] for r in rc[i]}
                    if len(vals) == 1 and grid[i][j] is None: grid[i][j] = vals.pop(); changed = True
            for j in range(W):
                cc[j] = [c for c in cc[j] if all(grid[i][j] is None or grid[i][j] == c[i] for i in range(H))]
                if not cc[j]: return False
                for i in range(H):
                    vals = {c[i] for c in cc[j]}
                    if len(vals) == 1 and grid[i][j] is None: grid[i][j] = vals.pop(); changed = True
        return True
    found = [0]
    def search(rc, cc):
        nonlocal grid
        saved = [row[:] for row in grid]
        if not propagate(rc, cc):
            grid = saved; return
        unknown = [(i, j) for i in range(H) for j in range(W) if grid[i][j] is None]
        if not unknown:
            found[0] += 1
        else:
            i, j = unknown[0]
            for v in (1, 0):
                if found[0] >= limit: break
                g2 = [row[:] for row in grid]
                grid[i][j] = v
                search([list(x) for x in rc], [list(x) for x in cc])
                grid = g2
        grid = saved
    search(rcand, ccand)
    # чистая логика (без перебора)?
    grid = [[None] * W for _ in range(H)]
    propagate([list(x) for x in rcand], [list(x) for x in ccand])
    logic = all(v is not None for row in grid for v in row)
    return found[0], logic

def check(pic):
    bits = [[1 if c != '.' else 0 for c in row] for row in pic]
    rows = [clues(r) for r in bits]
    cols = [clues([bits[i][j] for i in range(len(bits))]) for j in range(len(bits[0]))]
    n, logic = count_solutions(rows, cols)
    return n, logic, rows, cols

if __name__ == '__main__':
    pic = [l.rstrip('\n') for l in open(sys.argv[1]) if l.strip()]
    n, logic, rows, cols = check(pic)
    print('solutions:', n, 'pure-logic:', logic)
    print('rows:', rows); print('cols:', cols)
