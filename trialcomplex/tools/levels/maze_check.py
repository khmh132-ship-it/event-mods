#!/usr/bin/env python3
"""Проверка кооп-лабиринта: BFS по (позиция A, позиция B, рычаги). Заглавная в лабиринте X управляет
строчной (и цифрой-инверсией) в ДРУГОМ лабиринте. Печатает длину кратчайшего решения (в действиях)."""
from collections import deque
import sys

def check(a, b):
    mazes = [a, b]
    H, W = len(a), len(a[0])
    def find(m, ch):
        return [(c, r) for r in range(H) for c in range(W) if mazes[m][r][c] == ch]
    letters = sorted({ch for m in mazes for row in m for ch in row if 'A' <= ch <= 'F'})
    owner = {}
    for m in (0, 1):
        for row in mazes[m]:
            for ch in row:
                if 'A' <= ch <= 'F':
                    assert ch not in owner or owner[ch] == m, f'рычаг {ch} в обоих лабиринтах'
                    owner[ch] = m
    def passable(m, c, r, lev):
        if not (0 <= c < W and 0 <= r < H): return False
        ch = mazes[m][r][c]
        if ch == '#': return False
        if 'a' <= ch <= 'f':
            k = ch.upper(); return k in lev
        if '1' <= ch <= '6':
            k = chr(ord('A') + int(ch) - 1); return k not in lev
        return True
    sa, sb = find(0, 'S')[0], find(1, 'S')[0]
    ea, eb = set(find(0, 'X')), set(find(1, 'X'))
    start = (sa, sb, frozenset())
    q = deque([(start, 0)]); seen = {start}
    while q:
        (pa, pb, lev), d = q.popleft()
        if pa in ea and pb in eb: return d
        nxt = []
        for m, p in ((0, pa), (1, pb)):
            c, r = p
            for dc, dr in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if passable(m, c + dc, r + dr, lev):
                    np = (c + dc, r + dr)
                    nxt.append((np, pb, lev) if m == 0 else (pa, np, lev))
            ch = mazes[m][r][c]
            if 'A' <= ch <= 'F':
                nl = lev ^ {ch}
                nxt.append((pa, pb, frozenset(nl)))
        for s in nxt:
            # двери, закрывшиеся «на игроке», не выталкивают — в игре так же; не важно для BFS
            if s not in seen: seen.add(s); q.append((s, d + 1))
    return None

if __name__ == '__main__':
    L2a = ["#############",
           "S.a..#..D...#",
           "####.#.####.#",
           "#E..b1.c....#",
           "#.#####.#####",
           "#..F..#....dE",
           "#############"]
    print('demo L2 =', 'skip')
