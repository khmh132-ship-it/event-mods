#!/usr/bin/env python3
"""Object «Kolokol» blackout: the air-raid siren and the isolation announcement. Reuses gen_sounds.py."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g
import numpy as np
from scipy import signal


def siren(sec=14.0):
    """A hand-cranked Soviet siren: a slow rise and fall, rough harmonics, echoing down concrete corridors."""
    tt = g.t(sec)
    cycle = 7.0
    ph = (tt % cycle) / cycle
    f0 = 180 + 420 * np.where(ph < 0.55, np.sin(ph / 0.55 * np.pi / 2), np.cos((ph - 0.55) / 0.45 * np.pi / 2))
    f0 *= 1 + 0.01 * np.sin(2 * np.pi * 9 * tt)
    phase = 2 * np.pi * np.cumsum(f0) / g.SR
    y = sum(np.sin(k * phase) * (0.7 ** k) for k in range(1, 7))
    y = np.tanh(y * 1.6)
    y *= np.minimum(1, tt / 1.0) * np.minimum(1, (sec - tt) / 1.5)
    y = g.bp(y, 150, 5000)
    return g.reverb(y, 3.5, 0.55, 2500)


def main():
    g.save('story/siren', siren(14.0), 0.8, q=2)
    v = g.speak('Внимание. Нарушение протокола «Тишина». Объект изолируется. '
                'Персоналу — соблюдать тишину. Не разговаривать. Не бежать. '
                'Повторяю. Не разговаривать. Оно слушает.', 'ru+m1', 112, 28, gap=5)
    y = g.radio(g.pad_silence(v, 0.3, 0.6), 0.1)
    g.save('voice/bunker_alarm', g.reverb(y, 3.0, 0.5, 2500), 0.85, q=2)


if __name__ == '__main__':
    main()
