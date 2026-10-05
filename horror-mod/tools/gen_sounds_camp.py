#!/usr/bin/env python3
"""Pioneer camp "Zvyozdochka": bugle, loudspeaker announcement, children, the music box. Reuses gen_sounds.py."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g
import numpy as np


def bugle(notes, bpm=110, sag=0.0):
    """A tired brass call: harmonic-rich tones with breath, through an old loudspeaker."""
    beat = 60 / bpm
    parts, off = [], 0.3
    for i, (n, l) in enumerate(notes):
        dur = l * beat
        f = 440 * 2 ** ((n - 69) / 12) * (1 - sag * (i / len(notes)) ** 2)
        tt = g.t(dur * 0.95)
        vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * tt) * np.clip(tt * 3, 0, 1)
        ph = 2 * np.pi * f * np.cumsum(vib) / g.SR
        tone = sum((0.9 ** k) * np.sin(k * ph) / k ** 0.6 for k in range(1, 9))
        env = np.minimum(1, tt / 0.03) * np.exp(-tt * 0.6) * np.minimum(1, (tt[-1] - tt) / 0.04 + 0.01)
        breath = g.bp(g.noise(dur * 0.95), 900, 4000) * 0.05
        parts.append((off, (tone * env + breath * env), 0.6))
        off += dur
    y = g.mix(*parts, length=int((off + 0.5) * g.SR))
    return g.reverb(g.radio(y, static=0.08, warble=False), 3.0, 0.45, 3500)


def main():
    # "Сбор" — the call to the line-up; the last notes sag as if the bugler ran out of breath
    call = [(67, 0.5), (72, 0.5), (76, 0.5), (79, 1.0), (76, 0.5), (79, 1.0), (72, 0.5), (76, 0.5), (79, 0.5), (84, 2.0)]
    g.save('story/camp_horn', bugle(call, 120, 0.05), 0.8)

    ann = g.speak('Внимание. Внимание. Всем отрядам — построиться на линейку. Повторяю. Всем отрядам — на линейку. '
                  'Маша Воронова, подойди к вожатой. Маша. Мы тебя ждём.', 'ru+f3', 118, 45, gap=4)
    late = g.resample(ann, 0.9)  # the second loudspeaker, a little behind and lower — which loudspeaker?
    y = g.addp(g.radio(ann, 0.12), np.concatenate([np.zeros(int(0.45 * g.SR)), g.radio(late, 0.12) * 0.4]))
    g.save('voice/camp_lineup', g.reverb(g.pad_silence(y, 0.3, 0.5), 3.5, 0.5, 3000), 0.85, q=2)

    # children counting in unison, getting slower; a little too many voices
    layers = []
    for i in range(7):
        v = g.speak('двадцать девять. тридцать. тридцать один. тридцать два.', 'ru+f' + str(1 + i % 5), 92 + i * 3, 85 + i * 2)
        v = g.resample(v, 1.12 + 0.03 * g.rng.normal())
        layers.append((g.rng.uniform(0, 0.15), v, 0.6 + 0.4 * g.rng.random()))
    kids = g.bp(g.mix(*layers), 250, 6000)
    g.save('voice/camp_children', g.reverb(g.pad_silence(kids, 0.1, 0.3), 2.8, 0.5, 3500), 0.8)

    # the counselor's tape: every phrase repeated by the children half a beat later
    text = ('Это Людмила Смирнова, вожатая второго отряда. Двенадцатое июля. '
            'Дети, тихо. Я записываю для отчёта. Дети, перестаньте повторять. '
            'Перестаньте. Я не буду с вами разговаривать. Я буду молчать. '
            'Если меня кто-нибудь слышит: не приезжайте за детьми. Это уже не дети.')
    v = g.speak(text, 'ru+f4', 112, 55, gap=7)
    kids = []
    for i in range(5):
        k = g.resample(g.speak(text, 'ru+f' + str(1 + i), 108 + i * 3, 85 + i * 3, gap=7), 1.15 + 0.04 * i)
        kids.append((0.55 + i * 0.07, k, 0.22))
    y = g.mix((0.0, v, 1.0), *kids)
    x = g.tape(g.pad_silence(y, 0.6, 1.0))
    g.save('tape/tape8', x, 0.8, q=2)
    print('tape8', len(x) / g.SR)

    # Masha's music box, clean — the Echo can't copy it
    g.save('item/music_box_play', g.music_box(g.LULLABY[:18], 84, 0.0005, 0.0, 2.5), 0.75)


if __name__ == '__main__':
    main()
