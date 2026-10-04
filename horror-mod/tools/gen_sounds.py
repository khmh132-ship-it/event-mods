#!/usr/bin/env python3
"""Procedural sound generator for the ECHO horror mod.

Everything here is synthesised from scratch (numpy/scipy) or spoken by
espeak-ng and then mangled: radio filters, reverb, pitch shifting, static.
Output: mono OGG Vorbis files in src/main/resources/assets/echohorror/sounds/
"""
import os
import subprocess
import tempfile

import numpy as np
import soundfile as sf
from scipy import signal

SR = 32000
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'echohorror', 'sounds')
rng = np.random.default_rng(1986)


# ----------------------------------------------------------------- helpers
def t(sec):
    return np.arange(int(sec * SR)) / SR


def noise(sec):
    return rng.uniform(-1, 1, int(sec * SR))


def bp(x, lo, hi, order=4):
    sos = signal.butter(order, [lo, hi], btype='band', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def lp(x, f, order=4):
    sos = signal.butter(order, f, btype='low', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def hp(x, f, order=4):
    sos = signal.butter(order, f, btype='high', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def env_adsr(n, a, d, s, r):
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    a = max(a, 1)
    sus = max(n - a - d - r, 0)
    e = np.concatenate([np.linspace(0, 1, a), np.linspace(1, s, d), np.full(sus, s), np.linspace(s, 0, r)])
    if len(e) < n:
        e = np.pad(e, (0, n - len(e)))
    return e[:n]


def fade(x, fin=0.01, fout=0.05):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a > 0:
        x[:a] *= np.linspace(0, 1, a)
    if b > 0:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def reverb(x, sec=2.0, mix=0.4, damp=3000):
    n = int(sec * SR)
    ir = rng.normal(0, 1, n) * np.exp(-np.linspace(0, 7, n))
    ir = lp(ir, damp, 2)
    ir[0] = 0
    wet = signal.fftconvolve(x, ir)
    wet = np.pad(wet, (0, max(0, len(x) + n - len(wet))))[: len(x) + n]
    wet = norm(wet, 1.0) * np.max(np.abs(x))
    dry = np.pad(x, (0, n))
    return dry * (1 - mix) + wet * mix


def resample(x, factor):
    """factor >1 -> higher pitch & shorter"""
    n = int(len(x) / factor)
    return signal.resample(x, max(n, 1))


def mix(*parts, length=None):
    L = length or max(len(p[1]) + int(p[0] * SR) for p in parts)
    out = np.zeros(L)
    for off, sig, *g in parts:
        gain = g[0] if g else 1.0
        o = int(off * SR)
        end = min(L, o + len(sig))
        out[o:end] += sig[: end - o] * gain
    return out


def save(name, x, peak=0.9, q=3):
    x = norm(np.nan_to_num(x), peak)
    path = os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        sf.write(tmp.name, x.astype(np.float32), SR)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp.name, '-ac', '1', '-c:a', 'libvorbis',
                        '-q:a', str(q), path], check=True)
        os.unlink(tmp.name)
    print('  ', name, f'{len(x) / SR:.1f}s')


def speak(text, voice='ru', speed=120, pitch=40, amp=150, gap=0):
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        subprocess.run(['espeak-ng', '-v', voice, '-s', str(speed), '-p', str(pitch), '-a', str(amp),
                        '-g', str(gap), '-w', tmp.name, text], check=True)
        x, sr = sf.read(tmp.name)
        os.unlink(tmp.name)
    if x.ndim > 1:
        x = x.mean(axis=1)
    x = signal.resample_poly(x, SR, sr)
    return norm(x, 0.9)


def crackle(sec, density=30, n=None):
    x = np.zeros(n if n is not None else int(sec * SR))
    for _ in range(int(density * sec)):
        p = rng.integers(0, len(x) - 50)
        x[p:p + rng.integers(2, 40)] += rng.uniform(-1, 1)
    return x


def radio(x, static=0.18, warble=True):
    y = bp(x, 350, 3200)
    y = np.tanh(y * 3.0) / 1.2
    if warble:
        tt = np.arange(len(y)) / SR
        y *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.7 * tt + rng.uniform(0, 6))
        y *= 1 - 0.5 * (rng.uniform(0, 1, len(y) // 800 + 1).repeat(800)[: len(y)] < 0.04)
    st = bp(rng.uniform(-1, 1, len(y)), 800, 6000) * static + crackle(len(y) / SR, 25, len(y)) * static * 0.8
    return y + st


def tape(x, hiss=0.06):
    tt = np.arange(len(x)) / SR
    # wow & flutter through variable delay
    d = (0.004 * np.sin(2 * np.pi * 0.6 * tt) + 0.0008 * np.sin(2 * np.pi * 7 * tt)) * SR
    idx = np.clip(np.arange(len(x)) - d - 0.006 * SR, 0, len(x) - 1)
    y = np.interp(idx, np.arange(len(x)), x)
    y = bp(y, 200, 4500)
    y = np.tanh(y * 2)
    return y + hp(rng.uniform(-1, 1, len(y)), 3000) * hiss + crackle(len(y) / SR, 4, len(y)) * 0.1


def addp(*arrs):
    L = max(len(a) for a in arrs)
    return sum(np.pad(a, (0, L - len(a))) for a in arrs)


def pad_silence(x, before=0.0, after=0.0):
    return np.concatenate([np.zeros(int(before * SR)), x, np.zeros(int(after * SR))])


# ------------------------------------------------------------- synth parts
def drone(sec, base=43.65, seed=0):
    tt = t(sec)
    r = np.random.default_rng(seed)
    y = np.zeros_like(tt)
    for k, ratio in enumerate([1, 1.5, 2.0, 2.02, 3.01, 4.7, 1.06]):
        ph = r.uniform(0, 6)
        lfo = 1 + 0.004 * np.sin(2 * np.pi * r.uniform(0.03, 0.15) * tt + ph)
        amp = (0.6 / (k + 1)) * (0.5 + 0.5 * np.sin(2 * np.pi * r.uniform(0.02, 0.08) * tt + ph))
        y += np.sin(2 * np.pi * base * ratio * np.cumsum(lfo) / SR) * amp
    y += lp(noise(sec), 180) * 0.6
    y += bp(noise(sec), 900, 1400) * 0.05 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.05 * tt))
    y = reverb(y, 3.0, 0.5)[: len(tt)]
    return fade(y, 3, 4)


def knock(n=3, spacing=0.32, wood=True, frantic=False):
    parts = []
    off = 0.05
    for i in range(n):
        dur = 0.25
        tt = t(dur)
        thump = np.sin(2 * np.pi * (95 + 40 * np.exp(-tt * 30)) * tt) * np.exp(-tt * 28)
        click = bp(noise(dur), 600, 3500) * np.exp(-tt * 90) * 0.7
        body = np.sin(2 * np.pi * 210 * tt) * np.exp(-tt * 40) * 0.4
        k = (thump + click + body) * rng.uniform(0.7, 1.0)
        parts.append((off, k))
        off += spacing * (rng.uniform(0.5, 0.9) if frantic else rng.uniform(0.95, 1.08))
    y = mix(*parts, length=int((off + 0.6) * SR))
    return reverb(y, 0.9, 0.35, 2000)


def formant_voice(sec, f0_curve, vowel=(800, 1150, 2900), breath=0.15, vib=5.5, vib_depth=0.02):
    tt = t(sec)
    f0 = f0_curve(tt) * (1 + vib_depth * np.sin(2 * np.pi * vib * tt))
    ph = 2 * np.pi * np.cumsum(f0) / SR
    src = np.zeros_like(tt)
    for h in range(1, 40):
        src += np.sin(h * ph) / h * (h * f0 < SR / 2 - 500)
    src += noise(sec) * breath
    y = np.zeros_like(src)
    for i, f in enumerate(vowel):
        bw = 80 + 40 * i
        y += bp(src, max(f - bw, 50), f + bw, 2) * (1.0 / (i + 1))
    return y


def scream(sec=2.5, base=520, far=True):
    curve = lambda tt: base * (1 + 0.5 * np.minimum(tt / 0.25, 1)) * (1 - 0.25 * (tt / sec))
    y = formant_voice(sec, curve, (900, 1400, 3000), breath=0.35, vib=7, vib_depth=0.04)
    y = np.tanh(y * 4) * env_adsr(len(y), 0.08, 0.3, 0.8, 0.9)
    if far:
        y = lp(y, 2400)
        y = reverb(y, 3.5, 0.75, 1800)
    return y


def stinger(kind=0):
    sec = 3.2
    tt = t(sec)
    if kind == 0:  # orchestral-ish screech cluster
        y = np.zeros_like(tt)
        for f in [311, 329.6, 349.2, 370, 392, 622, 659]:
            y += signal.sawtooth(2 * np.pi * f * (1 + 0.01 * rng.normal()) * tt) * 0.3
        y = bp(y, 300, 7000)
        y += noise(sec) * 0.4 * np.exp(-tt * 3)
        y *= np.concatenate([np.linspace(0.2, 1, int(0.02 * SR)), np.exp(-np.linspace(0, 3.5, len(tt) - int(0.02 * SR)))])
    elif kind == 1:  # low boom + scrape
        boom = np.sin(2 * np.pi * (40 + 120 * np.exp(-tt * 8)) * tt) * np.exp(-tt * 1.5)
        scr = bp(noise(sec), 2000, 6000) * np.exp(-tt * 2.5) * (1 + np.sin(2 * np.pi * 37 * tt))
        y = boom * 1.2 + scr * 0.6
    elif kind == 2:  # rising shriek then cut
        f = 200 * np.exp(tt * 1.6)
        y = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR) * 0.5 + formant_voice(sec, lambda x: 380 * np.exp(x * 1.2), (850, 1300, 2700), 0.4) * 0.8
        y = np.tanh(y * 3)
        cut = int(2.2 * SR)
        y[cut:] *= np.exp(-np.linspace(0, 12, len(y) - cut))
        y *= np.linspace(0.3, 1, len(y))
    else:  # violin stab cluster with metallic hit
        y = np.zeros_like(tt)
        for f in [880, 932, 988, 1046, 1108]:
            y += signal.sawtooth(2 * np.pi * f * tt + rng.uniform(0, 6)) * 0.25
        y = bp(y, 600, 8000) * np.exp(-tt * 1.2)
        for f in [523, 1210, 1985, 2873]:
            y += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 2) * 0.3
        y[: int(0.01 * SR)] *= np.linspace(0, 1, int(0.01 * SR))
    return reverb(y, 2.0, 0.3)


def bell_toll(base=98.0, sec=9.0, decay=0.45):
    tt = t(sec)
    y = np.zeros_like(tt)
    partials = [(0.5, 1.0, 0.7), (1.0, 0.8, 1.0), (1.183, 0.6, 1.3), (1.506, 0.5, 1.6), (2.0, 0.45, 2.0),
                (2.514, 0.3, 2.6), (2.662, 0.25, 3.0), (3.011, 0.2, 3.5), (4.166, 0.12, 4.5)]
    for ratio, amp, dk in partials:
        f = base * 2 * ratio
        beat = 1 + 0.15 * np.sin(2 * np.pi * rng.uniform(0.5, 2) * tt)
        y += np.sin(2 * np.pi * f * tt + rng.uniform(0, 6)) * amp * np.exp(-tt * decay * dk) * beat
    hit = bp(noise(0.08), 1000, 5000) * np.exp(-t(0.08) * 60)
    y[: len(hit)] += hit * 0.5
    return reverb(y, 4.0, 0.35, 2500)


def music_box(notes, bpm=70, detune=0.0, slowdown=0.0, sec_tail=3.0):
    beat = 60 / bpm
    parts = []
    off = 0.2
    for i, (n, l) in enumerate(notes):
        if n is not None:
            f = 440 * 2 ** ((n - 69) / 12) * (1 + detune * rng.normal())
            d = 2.0
            tt = t(d)
            tone = (np.sin(2 * np.pi * f * tt) + 0.35 * np.sin(2 * np.pi * f * 2.0 * tt) * np.exp(-tt * 6)
                    + 0.15 * np.sin(2 * np.pi * f * 3.98 * tt) * np.exp(-tt * 9)) * np.exp(-tt * 3.2)
            tone[: 40] *= np.linspace(0, 1, 40)
            parts.append((off, tone, 0.5))
        off += l * beat * (1 + slowdown * (i / len(notes)) ** 2 * 3)
    y = mix(*parts, length=int((off + sec_tail) * SR))
    return reverb(y, 2.5, 0.35, 5000)


LULLABY = [(76, 1), (81, 1), (84, 1.5), (83, 0.5), (81, 1), (76, 1), (77, 1.5), (76, 0.5), (74, 2),
           (74, 1), (77, 1), (81, 1.5), (79, 0.5), (77, 1), (74, 1), (76, 1.5), (75, 0.5), (76, 2),
           (76, 1), (81, 1), (84, 1.5), (83, 0.5), (81, 1), (88, 1), (87, 1.5), (84, 0.5), (81, 2),
           (80, 1), (81, 1), (77, 1), (76, 1), (74, 1), (71, 1), (69, 3)]


def hum(sec=9.0):
    melody = [(69, 1.0), (72, 1.0), (76, 1.6), (74, 0.6), (72, 1.0), (69, 1.2), (71, 1.0), (68, 2.0)]
    tot = sum(l for _, l in melody)
    times = np.cumsum([0] + [l for _, l in melody])
    tt = t(tot * sec / tot)

    def f0(x):
        x = x * tot / sec
        idx = np.clip(np.searchsorted(times, x, side='right') - 1, 0, len(melody) - 1)
        notes = np.array([m[0] for m in melody])[idx]
        return 440 * 2 ** ((notes - 69) / 12) * 0.5 * 2
    f = f0(tt)
    f = signal.savgol_filter(f, 1201, 2)
    y = formant_voice(sec, lambda x: np.interp(x, tt, f), (300, 1000, 2600), breath=0.05, vib=5, vib_depth=0.015)
    y *= env_adsr(len(y), 0.4, 0.2, 0.9, 1.2)
    y = lp(y, 2500)
    return reverb(y, 3.0, 0.55, 2000)


def breathing(sec=6.0, rate=0.45, wet=True):
    tt = t(sec)
    ph = (tt * rate) % 1.0
    e_in = np.where(ph < 0.4, np.sin(np.pi * ph / 0.4), 0)
    e_out = np.where((ph >= 0.45) & (ph < 0.95), np.sin(np.pi * (ph - 0.45) / 0.5), 0)
    n = noise(sec)
    inh = bp(n, 1200, 4500) * e_in ** 1.5 * 0.7
    exh = bp(n, 400, 2200) * e_out ** 1.2 * 0.9
    rasp = signal.sawtooth(2 * np.pi * 55 * tt) * bp(n, 100, 600) * e_out * 0.4
    y = inh + exh + rasp
    return reverb(y, 1.0, 0.2) if wet else y


def clicks(sec=2.0, rate=18):
    x = np.zeros(int(sec * SR))
    p = 0
    while p < len(x) - 400:
        c = bp(noise(0.01), 1500, 7000) * np.exp(-t(0.01) * 400)
        x[p:p + len(c)] += c * rng.uniform(0.4, 1)
        p += int(SR / rate * rng.uniform(0.3, 1.6))
    x += bp(noise(sec), 300, 900) * 0.05 * np.abs(np.sin(2 * np.pi * 3 * t(sec)))
    return reverb(x, 0.8, 0.25)


def shriek(sec=1.8, base=700):
    y = formant_voice(sec, lambda tt: base * (1 + 0.6 * np.sin(np.pi * tt / sec)), (1000, 1600, 3200), 0.6, 11, 0.07)
    y += signal.sawtooth(2 * np.pi * base * 1.41 * t(sec)) * 0.3
    y = np.tanh(y * 5) * env_adsr(len(y), 0.02, 0.2, 0.8, 0.5)
    return reverb(bp(y, 300, 9000), 1.2, 0.3)


def glitch(sec=1.2):
    y = np.zeros(int(sec * SR))
    p = 0
    while p < len(y):
        L = int(rng.uniform(0.01, 0.08) * SR)
        kind = rng.integers(0, 4)
        tt = np.arange(L) / SR
        if kind == 0:
            seg = signal.square(2 * np.pi * rng.uniform(100, 3000) * tt) * 0.5
        elif kind == 1:
            seg = rng.uniform(-1, 1, L)
        elif kind == 2:
            seg = np.round(np.sin(2 * np.pi * rng.uniform(50, 400) * tt) * 3) / 3
        else:
            seg = np.zeros(L)
        y[p:p + L] = seg[: len(y[p:p + L])]
        p += L
    return y * 0.7


def scrape(sec=1.5):
    n = noise(sec)
    tt = t(sec)
    y = bp(n, 150, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * tt)) + bp(n, 2500, 5000) * 0.15
    return fade(y * env_adsr(len(y), 0.1, 0.2, 0.8, 0.4), 0.05, 0.2)


def swell_reverse(sec=2.5):
    y = bell_toll(220, sec, 1.0)[: int(sec * SR)][::-1] * 0.6
    y += bp(noise(sec), 500, 8000) * np.linspace(0, 1, int(sec * SR)) ** 3 * 0.8
    out = np.concatenate([y, np.zeros(int(0.3 * SR))])
    return out


def heartbeat(bpm=60, beats=2):
    period = 60 / bpm
    parts = []
    for i in range(beats):
        tt = t(0.3)
        lub = np.sin(2 * np.pi * (60 + 30 * np.exp(-tt * 25)) * tt) * np.exp(-tt * 18)
        parts.append((i * period, lub, 1.0))
        parts.append((i * period + 0.22, lub, 0.65))
    y = mix(*parts, length=int(beats * period * SR))
    return lp(y, 200)


def roar(sec=3.0, base=55):
    tt = t(sec)
    y = formant_voice(sec, lambda x: base * (1 + 0.3 * np.sin(np.pi * x / sec)), (400, 800, 2200), 0.8, 9, 0.1)
    y += signal.sawtooth(2 * np.pi * base * 0.5 * tt) * bp(noise(sec), 60, 400)
    y = np.tanh(y * 6) * env_adsr(len(y), 0.15, 0.4, 0.8, 0.8)
    return reverb(lp(y, 3000), 2.5, 0.4, 1500)


def chorus(text, voices=5, speed=95):
    layers = []
    for i in range(voices):
        v = speak(text, 'ru' + rng.choice(['', '+m3', '+f2', '+m7', '+f4', '+Demonic']), speed + rng.integers(-10, 10),
                  rng.integers(10, 70))
        v = resample(v, rng.uniform(0.62, 1.05))
        layers.append((rng.uniform(0, 0.12), v, rng.uniform(0.5, 1.0)))
    y = mix(*layers)
    y = bp(y, 120, 6000)
    return reverb(y, 3.5, 0.55, 2500)


def whisper(text, deep=False):
    v = speak(text, 'ru+whisper', 105, 35, 200)
    v = hp(v, 250)
    layers = [(0.0, v, 1.0), (0.08, resample(v, 0.93), 0.4)]
    if deep:
        layers.append((0.0, resample(speak(text, 'ru+Demonic', 90, 10), 0.7), 0.35))
    y = mix(*layers)
    return reverb(y, 1.6, 0.3, 4000)


def music_finale(sec=64):
    bpm = 96
    beat = 60 / bpm
    tt = t(sec)
    y = np.zeros_like(tt)
    # drone bed
    y += drone(sec, 36.7, 11)[: len(tt)] * 0.7
    # dissonant pad swelling every 8 beats
    for f, d in [(110, 0), (116.5, 0.3), (164.8, 0.1), (174.6, 0.5)]:
        y += lp(signal.sawtooth(2 * np.pi * f * (1 + 0.002 * np.sin(2 * np.pi * 0.2 * tt + d)) * tt), 900) * 0.12 * \
             (0.5 + 0.5 * np.sin(2 * np.pi * tt / (beat * 16) - np.pi / 2))
    # pounding drums
    nbeats = int(sec / beat)
    for i in range(nbeats):
        pattern = [1, 0, 0, 1, 1, 0, 1, 0]
        if pattern[i % 8]:
            d = t(0.6)
            kick = np.sin(2 * np.pi * (45 + 90 * np.exp(-d * 20)) * d) * np.exp(-d * 6)
            o = int(i * beat * SR)
            e = min(len(y), o + len(kick))
            y[o:e] += kick[: e - o] * 1.2
        if i % 16 == 0:
            b = bell_toll(73.4, 6, 0.6)
            o = int(i * beat * SR)
            e = min(len(y), o + len(b))
            y[o:e] += b[: e - o] * 0.5
        if i % 4 == 2:
            d = t(0.25)
            sn = bp(noise(0.25), 800, 5000) * np.exp(-d * 15) * 0.25
            o = int(i * beat * SR)
            e = min(len(y), o + len(sn))
            y[o:e] += sn[: e - o]
    # string ostinato
    for i in range(nbeats * 2):
        f = [220, 233.1, 220, 207.7][(i // 2) % 4]
        d = t(beat / 2)
        n = signal.sawtooth(2 * np.pi * f * d) * env_adsr(len(d), 0.01, 0.05, 0.6, 0.05)
        o = int(i * beat / 2 * SR)
        e = min(len(y), o + len(n))
        y[o:e] += bp(n, 200, 3500)[: e - o] * 0.12
    y = reverb(y, 2.0, 0.25)[: len(tt)]
    return fade(y, 0.05, 1.0)


# ------------------------------------------------------------- generation
def main():
    print('Generating sounds into', os.path.abspath(OUT))

    # --- ambience
    for i, (b, s) in enumerate([(43.65, 1), (36.7, 2), (49.0, 3)]):
        save(f'ambient/drone{i + 1}', drone(24, b, s), 0.7)
    deep = drone(28, 30.9, 7)
    for k in range(6):
        o = rng.uniform(1, 25)
        g = roar(2.5, rng.uniform(30, 45))
        deep[int(o * SR): int(o * SR) + len(g)] += lp(g, 600)[: len(deep) - int(o * SR)] * 0.25
    for k in range(10):
        o = int(rng.uniform(0.5, 27) * SR)
        drip = np.sin(2 * np.pi * rng.uniform(900, 1600) * t(0.15)) * np.exp(-t(0.15) * 40)
        deep[o:o + len(drip)] += reverb(drip, 1.5, 0.6)[: len(deep) - o][: len(deep[o:o + len(drip)])] * 0.25
    save('ambient/depths', deep, 0.7)

    # --- whispers
    phrases = ['обернись', 'я тебя вижу', 'не спи', 'оно рядом', 'ты здесь один', 'иди к нам', 'мы тебя слышим',
               'тише. тише.', 'он не настоящий', 'посмотри наверх', 'ты уже один из нас', 'не смотри на него',
               'открой дверь', 'почему ты не отвечаешь', 'мы были здесь всегда', 'сзади']
    for i, p in enumerate(phrases):
        save(f'whisper/w{i + 1}', whisper(p, deep=(i % 3 == 0)), 0.8)

    # --- voices calling from the dark
    calls = [('open1', 'Открой. Это я.', 'ru+f2', 0.95), ('open2', 'Серёжа. Открой мне. Мне холодно.', 'ru+f4', 0.9),
             ('help1', 'Помогите.', 'ru+f3', 1.0), ('help2', 'Помоги мне. Пожалуйста.', 'ru+m3', 0.9),
             ('mama', 'Мама? Мама, это ты?', 'ru+f5', 1.25), ('play', 'Поиграй со мной.', 'ru+f5', 1.3),
             ('here', 'Я здесь. Иди сюда.', 'ru+m7', 0.85), ('name', 'Эй. Ты меня слышишь?', 'ru+m1', 0.95)]
    for name, text, voice, pitch in calls:
        v = speak(text, voice, 105, 45)
        v = resample(v, pitch)
        v = lp(v, 3200)
        save(f'voice/{name}', reverb(v, 2.5, 0.55, 2000), 0.7)
    laugh = speak('ха. ха. ха. ха.', 'ru+Demonic', 80, 5)
    save('voice/laugh', reverb(resample(laugh, 0.7), 2.5, 0.5), 0.8)

    # --- scares
    save('scare/knock1', knock(3), 0.95)
    save('scare/knock2', knock(2, 0.5), 0.95)
    save('scare/knock3', knock(9, 0.18, frantic=True), 0.95)
    save('scare/scream1', scream(2.4, 520), 0.8)
    save('scare/scream2', scream(3.2, 430), 0.8)
    save('scare/scream_near', scream(1.8, 600, far=False), 0.95)
    save('scare/breath1', breathing(7, 0.4), 0.8)
    save('scare/breath2', breathing(5, 0.7), 0.8)
    for i in range(4):
        save(f'scare/stinger{i + 1}', stinger(i), 0.98)
    save('scare/hum', hum(10), 0.75)
    save('scare/music_box', music_box(LULLABY[:18], 78, 0.004, 0.6), 0.75)
    save('scare/heartbeat', heartbeat(62, 2), 0.95)
    save('scare/heartbeat_fast', heartbeat(130, 4), 0.95)
    save('scare/glitch', glitch(1.4), 0.8)
    save('scare/tinnitus', fade(np.sin(2 * np.pi * 7400 * t(5)) * np.exp(-t(5) * 0.6), 0.02, 1.0), 0.4)
    save('scare/vanish', swell_reverse(1.6), 0.8)
    save('scare/static', bp(noise(2.0), 500, 7000) * 0.5 + crackle(2.0, 60) * 0.5, 0.6)
    save('scare/scrape', scrape(1.6), 0.8)

    # --- radio broadcasts
    tune = []
    for i in range(3):
        tune += [(81, 0.5), (84, 0.5), (88, 0.5), (86, 1.0), (None, 0.5)]
    intro = music_box(tune, 120, 0.01, 0.0, 1.0)
    intro = radio(intro, 0.15)
    save('radio/intro', intro, 0.8)
    save('radio/tune', radio(np.sin(2 * np.pi * np.cumsum(np.linspace(300, 2400, int(1.5 * SR))) / SR) * 0.3, 0.6), 0.6)
    numbers = ['ноль', 'один', 'два', 'три', 'четыре', 'пять', 'шесть', 'семь', 'восемь', 'девять', 'десять',
               'одиннадцать', 'двенадцать']
    for i, n in enumerate(numbers):
        v = speak(n, 'ru+f1', 100, 30)
        save(f'radio/num{i}', radio(pad_silence(v, 0.1, 0.25)), 0.8)
    broadcasts = {
        'b_attention': ('Внимание. Внимание. Говорит ретранслятор эр семь. Считаю.', 'ru+f1', 105),
        'b_end': ('Конец связи.', 'ru+f1', 100),
        'b_prologue': ('Всем, кто слышит. Не отвечайте голосам из темноты. Повторяю. Не отвечайте голосам из темноты. '
                       'Не открывайте дверь, если стучат ночью.', 'ru+f1', 105),
        'b_lis': ('Это Лисицын. Ретранслятор эр семь. Если вы слышите это, значит, я уже не я. Оно выучило мой голос. '
                  'Найдите колокол. Тихий Лог. Только колокол заставляет его замолчать.', 'ru+m3', 110),
        'b_more': ('Вас стало больше.', 'ru+f1', 90),
        'b_deeper': ('Глубже. Спускайтесь глубже. Мы ждём.', 'ru+Demonic', 90),
        'b_counting': ('Я считаю вас каждую ночь. И каждую ночь вас на одного больше.', 'ru+f1', 100),
    }
    for name, (text, voice, sp) in broadcasts.items():
        v = speak(text, voice, sp, 30, gap=4)
        save(f'radio/{name}', radio(pad_silence(v, 0.2, 0.4)), 0.8)
    heard = chorus('Мы услышали вас.', 6, 80)
    save('radio/b_heard', radio(heard, 0.1), 0.85)

    # --- tapes (Voronov)
    tapes = {
        'tape1': 'Запись первая. Объект Колокол. Полость под станцией издаёт звук ниже порога слуха. '
                 'Мы назвали его Эхо. Сегодня оно впервые повторило слово. Слово было: помогите. '
                 'Никто из нас его не произносил.',
        'tape2': 'Запись седьмая. Лаборантка Зоя не разговаривает уже девять дней. Она не двигается, когда на неё смотрят. '
                 'Камеры показывают, что ночью она стоит у двери моего кабинета. Я приказал не отводить от неё взгляд.',
        'tape3': 'Запись двенадцатая. Деревня пуста. Люди вернулись из леса, но это не люди. Эхо копирует то, что слышит. '
                 'А тех, кого оно скопировало, больше нет. Колокол. Колокол — единственный звук, который оно не может повторить.',
        'tape4': 'Если вы слушаете это, проверьте себя. Вы помните, как пришли сюда? Вы помните лицо своей матери? '
                 'Эхо копирует не только тела. Оно копирует память. Возможно, вы тоже эхо. '
                 'Спуститесь в звонницу. Заставьте его замолчать. Даже если замолчите вместе с ним.',
    }
    for name, text in tapes.items():
        v = speak(text, 'ru+m7', 112, 25, gap=6)
        save(f'tape/{name}', tape(pad_silence(v, 0.6, 0.8)), 0.8, q=2)

    # --- story
    save('story/bell', bell_toll(98, 10, 0.35), 0.9)
    save('story/bell_far', lp(reverb(bell_toll(98, 7, 0.5), 4, 0.7, 1500), 1500), 0.7)
    boom = np.sin(2 * np.pi * (35 + 60 * np.exp(-t(5) * 4)) * t(5)) * np.exp(-t(5) * 0.8)
    save('story/chapter', np.concatenate([swell_reverse(2.2), reverb(boom, 3, 0.4)]), 0.95)
    save('story/door_open', lp(reverb(addp(scrape(2.5), roar(2.5, 30) * 0.3), 2.0, 0.4), 2500), 0.9)
    save('story/power', np.concatenate([glitch(0.4), bp(signal.sawtooth(2 * np.pi * np.cumsum(np.linspace(30, 120, int(3 * SR))) / SR), 40, 2000) * env_adsr(int(3 * SR), 0.1, 1, 0.6, 1.0)]), 0.8)

    # --- entities
    save('entity/watcher_idle', addp(breathing(4, 0.3) * 0.7, lp(drone(4, 55, 4), 300)), 0.6)
    save('entity/crawler_click1', clicks(1.6, 22), 0.8)
    save('entity/crawler_click2', clicks(1.2, 35), 0.8)
    save('entity/crawler_hurt', shriek(0.6, 900), 0.85)
    save('entity/crawler_death', shriek(1.4, 650), 0.85)
    save('entity/mimic_reveal', np.concatenate([glitch(0.25), shriek(1.6, 520)]), 0.95)
    babble_src = speak('ты где. иди сюда. всё нормально. иди сюда. ты где.', 'ru+m3', 140, 50)
    babble = np.concatenate([babble_src[i:i + 3000][::(-1 if (i // 3000) % 2 else 1)] for i in range(0, len(babble_src), 3000)])
    save('entity/mimic_idle', reverb(lp(babble, 3000), 1.5, 0.4), 0.7)
    save('entity/silent_move', scrape(0.9), 0.8)
    save('entity/silent_attack', shriek(1.2, 1100), 0.95)
    save('entity/boss_idle', roar(4, 41), 0.8)
    save('entity/boss_roar', addp(roar(3.5, 62), shriek(3.5, 400) * 0.3), 0.95)
    save('entity/boss_hurt', roar(1.0, 80), 0.85)
    save('entity/boss_death', np.concatenate([roar(4, 50), shriek(3, 300) * 0.6]), 0.95)
    for i, line in enumerate(['Я — это вы.', 'Мы — всё, что вы сказали.', 'Не звони.', 'Останься с нами.', 'Тише.']):
        save(f'entity/boss_voice{i + 1}', chorus(line, 5, 85), 0.85)
    save('entity/boss_stun', addp(bell_toll(196, 3.0, 1.2), glitch(3.0) * 0.2), 0.9)

    # --- music
    save('music/finale', music_finale(64), 0.85, q=2)
    save('music/ending', music_box(LULLABY, 52, 0.002, 0.15, 5.0), 0.7, q=2)

    # --- items
    save('item/flashlight_click', bp(noise(0.05), 2000, 8000) * np.exp(-t(0.05) * 120), 0.7)
    save('item/pills', bp(noise(0.5), 3000, 9000) * (np.sin(2 * np.pi * 30 * t(0.5)) > 0.6) * 0.5, 0.6)
    save('item/page', bp(noise(0.5), 1500, 7000) * env_adsr(int(0.5 * SR), 0.05, 0.2, 0.3, 0.2), 0.5)


if __name__ == '__main__':
    main()
