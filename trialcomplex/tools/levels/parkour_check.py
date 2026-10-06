#!/usr/bin/env python3
"""Проверка кооп-паркура: игрок 1 на дорожке A, игрок 2 на B. '#' платформа, ' ' пропасть (прыжок до 2),
'x' осыпается (проходима), строчная буква — мост, есть пока на плите той же заглавной буквы на ДРУГОЙ дорожке стоит напарник."""
from collections import deque

def check(A, B):
    L = len(A); assert len(B) == L
    lanes = [A, B]
    def ok(lane, x, other_pos):
        if x < 0: return False
        if x >= L: return True
        c = lanes[lane][x]
        if c in '#xS': return True
        if c.isupper(): return True
        if c.islower():
            o = lanes[1 - lane]
            return 0 <= other_pos < L and o[other_pos] == c.upper()
        return False
    start = (0, 0); q = deque([(start, 0)]); seen = {start}
    while q:
        (a, b), d = q.popleft()
        if a >= L and b >= L: return d
        for lane, p, o in ((0, a, b), (1, b, a)):
            if p >= L: continue
            for step in (1, -1, 2, 3):
                np = p + step
                if step in (2, 3):  # прыжок через пропасть
                    if not all(0 <= p + k < L and lanes[lane][p + k] == ' ' for k in range(1, step)): continue
                if not ok(lane, np, o): continue
                s = (np, b) if lane == 0 else (a, np)
                if s not in seen: seen.add(s); q.append((s, d + 1))
    return None

LV = {
 'L1': ("###A####bb###  ##C####dd####", "#aa####B##  ###cc###D##  ###"),
 'L2': ("##A###xxx##bb###  ##C##xx##dd###", "#aa##x##B###  ##cc###xxx#D#  ###"),
 'L3': ("##A##bb###C##xx##dd##E###  ##ff#####", "#aa###B##cc###D##ee##xx##F###  #####"),
}
for k, (a, b) in LV.items():
    print(k, len(a), len(b), check(a, b))
