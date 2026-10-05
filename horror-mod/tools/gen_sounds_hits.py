#!/usr/bin/env python3
"""Encounter stingers: the sound when something sees you, and the jumpscare hit."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g
import numpy as np


def main():
    # spotted: a short torn shriek over a low thump
    thump = g.lp(g.noise(0.6), 120) * np.exp(-g.t(0.6) * 9) * 3
    spotted = g.addp(g.shriek(0.9, 1050) * 0.9, thump, g.glitch(0.35) * 0.4)
    g.save('scare/spotted', g.reverb(np.tanh(spotted * 2.2), 1.2, 0.25, 4000), 0.98)
    # jumpscare: everything at once
    hit = g.lp(g.noise(1.2), 90) * np.exp(-g.t(1.2) * 4) * 4
    scream = g.addp(g.shriek(1.4, 820), g.shriek(1.4, 1230) * 0.6, g.scream(1.4, 600, far=False) * 0.7)
    js = g.addp(np.tanh(g.addp(scream, g.bp(g.noise(1.4), 1500, 9000) * 0.5) * 2.5), hit)
    g.save('scare/jumpscare', js, 0.99)


if __name__ == '__main__':
    main()
