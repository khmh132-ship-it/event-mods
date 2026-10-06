#!/usr/bin/env python3
"""Подбор задач «Рыцари и лжецы»: у каждой двери утверждение; рыцарь всегда прав, лжец всегда лжёт; выход ровно один.
Ищем наборы утверждений с единственным ответом (выход однозначен при всех согласованных расстановках)."""
import itertools, random

def stmts(n):
    out = []
    for k in range(n):
        out.append((f'ВЫХОД ЗА ДВЕРЬЮ {k+1}', lambda T, E, k=k: E == k))
        out.append((f'ВЫХОД НЕ ЗА ДВЕРЬЮ {k+1}', lambda T, E, k=k: E != k))
        out.append((f'ДВЕРЬ {k+1} — ЛЖЕЦ', lambda T, E, k=k: not T[k]))
        out.append((f'ДВЕРЬ {k+1} — РЫЦАРЬ', lambda T, E, k=k: T[k]))
    for j in range(n):
        for k in range(j + 1, n):
            out.append((f'ДВЕРИ {j+1} И {k+1} — ОДНОГО ТИПА', lambda T, E, j=j, k=k: T[j] == T[k]))
            out.append((f'ДВЕРИ {j+1} И {k+1} — РАЗНОГО ТИПА', lambda T, E, j=j, k=k: T[j] != T[k]))
    for c in range(0, n + 1):
        out.append((f'СРЕДИ НАС РОВНО {c} ЛЖЕЦ{"ОВ" if c != 1 else ""}', lambda T, E, c=c: sum(1 for t in T if not t) == c))
    out.append(('ВЫХОД ЗА ДВЕРЬЮ РЫЦАРЯ', lambda T, E: T[E]))
    out.append(('ВЫХОД ЗА ДВЕРЬЮ ЛЖЕЦА', lambda T, E: not T[E]))
    return out

def solve(n, chosen):
    sols = set(); assigns = 0
    for T in itertools.product([True, False], repeat=n):
        for E in range(n):
            if all(f(T, E) == T[i] for i, (_, f) in enumerate(chosen)):
                sols.add(E); assigns += 1
    return sols, assigns

def find(n, seed, avoid_self=True, want_assign=1):
    rnd = random.Random(seed)
    S = stmts(n)
    for _ in range(200000):
        chosen = []
        for i in range(n):
            while True:
                s = rnd.choice(S)
                if avoid_self and (f'ДВЕРЬ {i+1} ' in s[0] or f'ДВЕРЬЮ {i+1}' in s[0] and 'НЕ' not in s[0] and False): continue
                if s[0] not in [c[0] for c in chosen]: break
            chosen.append(s)
        sols, assigns = solve(n, chosen)
        if len(sols) == 1 and assigns == want_assign:
            return [c[0] for c in chosen], sols.pop()
    return None

for name, n, seed in (('L1', 3, 1), ('L2', 3, 7), ('L3', 4, 3), ('L4', 4, 11)):
    r = find(n, seed)
    print(name, r)
