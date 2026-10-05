#!/usr/bin/env python3
"""Re-voices every spoken line with neural Russian voices (Piper), keeping the old effects and timings.

    pip install piper-tts
    voices (put next to this file or set PIPER_VOICES): ru_RU-{irina,denis,dmitri,ruslan}-medium.onnx(.json)
    from https://huggingface.co/rhasspy/piper-voices/tree/main/ru/ru_RU

Each file is fitted to the length of the line it replaces, so the subtitle timings in the mod stay right.
"""
import io
import os
import sys
import wave

import numpy as np
import soundfile as sf
from scipy import signal

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from piper import PiperVoice, SynthesisConfig  # noqa: E402

VOICE_DIR = os.environ.get('PIPER_VOICES', os.path.dirname(__file__))
_cache = {}

# who speaks with which voice
IRINA, DENIS, DMITRI, RUSLAN = 'irina', 'denis', 'dmitri', 'ruslan'


def _voice(name):
    if name not in _cache:
        _cache[name] = PiperVoice.load(os.path.join(VOICE_DIR, f'ru_RU-{name}-medium.onnx'))
    return _cache[name]


def say(text, voice, scale=1.0, noise=0.667, noise_w=0.8):
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as w:
        _voice(voice).synthesize_wav(text, w, SynthesisConfig(length_scale=scale, noise_scale=noise, noise_w_scale=noise_w))
    buf.seek(0)
    x, sr = sf.read(buf)
    if x.ndim > 1:
        x = x.mean(axis=1)
    return g.norm(signal.resample_poly(x, g.SR, sr), 0.9)


def radio_l(x, static=0.05):
    """A radio you can still understand: band-limited, softly clipped, a little static."""
    y = g.bp(x, 250, 4800)
    y = np.tanh(y * 1.4) / 1.1
    tt = np.arange(len(y)) / g.SR
    y *= 0.9 + 0.1 * np.sin(2 * np.pi * 0.5 * tt)
    st = g.bp(g.rng.uniform(-1, 1, len(y)), 1000, 6000) * static + g.crackle(len(y) / g.SR, 12, len(y)) * static * 0.6
    return y + st


def tape_l(x, hiss=0.025):
    """Old tape, but the words survive: gentle wow & flutter, warm band, light hiss."""
    tt = np.arange(len(x)) / g.SR
    d = (0.0015 * np.sin(2 * np.pi * 0.5 * tt) + 0.0003 * np.sin(2 * np.pi * 6 * tt)) * g.SR
    idx = np.clip(np.arange(len(x)) - d - 0.003 * g.SR, 0, len(x) - 1)
    y = np.interp(idx, np.arange(len(x)), x)
    y = g.bp(y, 120, 6500)
    y = np.tanh(y * 1.3) / 1.05
    return y + g.hp(g.rng.uniform(-1, 1, len(y)), 3000) * hiss + g.crackle(len(y) / g.SR, 2, len(y)) * 0.05


def old_len(rel):
    path = os.path.join(g.OUT, rel + '.ogg')
    if not os.path.exists(path):
        return None
    x, sr = sf.read(path)
    return len(x) / sr


def fitted(rel, text, voice, process, pitch=1.0, noise=0.667, noise_w=0.8, base_scale=1.0):
    """Synthesise, process, and fit the result to the length of the file it replaces."""
    target = old_len(rel)

    def render(scale):
        v = say(text, voice, scale, noise, noise_w)
        if pitch != 1.0:
            v = g.resample(v, pitch)
        return v, process(v)

    v, y = render(base_scale)
    if target:
        overhead = len(y) / g.SR - len(v) / g.SR
        want = max(0.5, target - overhead)
        scale = float(np.clip(base_scale * want / (len(v) / g.SR), 0.7, 1.6))
        v, y = render(scale)
        n = int(target * g.SR)
        if len(y) > n:
            y = y[:n]
            f = min(len(y), int(0.3 * g.SR))
            y[-f:] *= np.linspace(1, 0, f)
        else:
            y = np.pad(y, (0, n - len(y)))
    return y


def main():
    # ---- the woman on the radio who counts
    numbers = ['ноль', 'один', 'два', 'три', 'четыре', 'пять', 'шесть', 'семь', 'восемь', 'девять', 'десять',
               'одиннадцать', 'двенадцать']
    for i, n in enumerate(numbers):
        rel = f'radio/num{i}'
        g.save(rel, fitted(rel, n + '.', IRINA, lambda v: radio_l(g.pad_silence(v, 0.1, 0.25)), 0.97, 0.4, 0.5), 0.8)
    broadcasts = {
        'b_attention': ('Внимание. Внимание. Говорит ретранслятор эр семь. Считаю.', IRINA, 0.96),
        'b_end': ('Конец связи.', IRINA, 0.96),
        'b_prologue': ('Всем, кто слышит. Не отвечайте голосам из темноты. Повторяю. Не отвечайте голосам из темноты. '
                       'Не открывайте дверь, если стучат ночью.', IRINA, 0.96),
        'b_lis': ('Это Лисицын. Ретранслятор эр семь. Если вы слышите это, значит, я уже не я. Оно выучило мой голос. '
                  'Найдите колокол. Тихий Лог. Только колокол заставляет его замолчать.', DENIS, 1.0),
        'b_more': ('Вас стало больше.', IRINA, 0.9),
        'b_deeper': ('Глубже. Спускайтесь глубже. Мы ждём.', RUSLAN, 0.78),
        'b_counting': ('Я считаю вас каждую ночь. И каждую ночь вас на одного больше.', IRINA, 0.96),
    }
    for name, (text, voice, pitch) in broadcasts.items():
        rel = f'radio/{name}'
        g.save(rel, fitted(rel, text, voice, lambda v: radio_l(g.pad_silence(v, 0.2, 0.4)), pitch), 0.8)
    # «Мы услышали вас» — every voice at once
    layers = []
    for i, (vc, p) in enumerate([(IRINA, 1.0), (DENIS, 0.95), (DMITRI, 0.9), (RUSLAN, 0.8), (IRINA, 1.2), (DENIS, 0.7)]):
        layers.append((0.03 * i, g.resample(say('Мы услышали вас.', vc, 1.2), p), 0.7))
    heard = g.reverb(g.bp(g.mix(*layers), 120, 6000), 3.5, 0.55, 2500)
    g.save('radio/b_heard', radio_l(heard, 0.06), 0.85)

    # ---- voices from the dark
    calls = [('open1', 'Открой. Это я.', IRINA, 0.97), ('open2', 'Серёжа. Открой мне. Мне холодно.', IRINA, 0.94),
             ('help1', 'Помогите.', IRINA, 1.0), ('help2', 'Помоги мне. Пожалуйста.', DENIS, 0.95),
             ('mama', 'Мама? Мама, это ты?', IRINA, 1.3), ('play', 'Поиграй со мной.', IRINA, 1.32),
             ('here', 'Я здесь. Иди сюда.', DMITRI, 0.88), ('name', 'Эй. Ты меня слышишь?', RUSLAN, 0.95)]
    for name, text, voice, pitch in calls:
        rel = f'voice/{name}'
        g.save(rel, fitted(rel, text, voice, lambda v: g.reverb(g.lp(v, 5000), 2.0, 0.3, 3000), pitch, 0.8, 0.9), 0.7)

    # ---- Voronov's tapes
    tapes = {
        'tape1': 'Запись первая. Объект «Колокол». Полость под станцией издаёт звук ниже порога слуха. '
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
        rel = f'tape/{name}'
        g.save(rel, fitted(rel, text, DMITRI, lambda v: tape_l(g.pad_silence(v, 0.6, 0.8)), 0.93), 0.8, q=2)

    # Masha, 7 — and her "mother" half a second behind
    masha = ('Это Маша. Папа сказал записать, как я пою. Баю, баюшки, баю. Мама, перестань повторять. '
             'Мама, ты стоишь за дверью? Мама. Почему у тебя мой голос?')

    def masha_fx(v):
        x = tape_l(g.pad_silence(v, 0.6, 0.8))
        echo = np.concatenate([np.zeros(int(0.5 * g.SR)), g.resample(v, 0.8) * 0.35])
        return g.addp(x, tape_l(g.pad_silence(echo, 0.6, 0.3), hiss=0.0) * 0.6)
    g.save('tape/tape5', fitted('tape/tape5', masha, IRINA, masha_fx, 1.3, 0.8, 1.0), 0.8, q=2)
    # Lisitsyn, 3 a.m.
    g.save('tape/tape6', fitted('tape/tape6', 'Это Лисицын. Три часа ночи. Оно стучит. Три раза. Как Таня. Я не открою. '
                                'Я записываю, чтобы помнить свой голос. Если утром я заговорю иначе, сожгите эту плёнку. '
                                'Оно перестало стучать. Оно говорит. Оно говорит со мной моим голосом.', DENIS,
                                lambda v: tape_l(g.pad_silence(v, 0.6, 0.8)), 0.97, 0.75, 0.9), 0.8, q=2)
    # Tanya
    g.save('tape/tape7', fitted('tape/tape7', 'Серёжа, ты меня слышишь? Ты стоишь у окна уже шесть часов. Ты не моргаешь. '
                                'Я нашла твою плёнку. Ты просил её сжечь. Я не смогла. Это всё, что от тебя осталось. '
                                'Если кто-то найдёт это, не открывайте ему. Он очень хорошо говорит моим именем.', IRINA,
                                lambda v: tape_l(g.pad_silence(v, 0.6, 0.8)), 0.95, 0.75, 0.9), 0.8, q=2)
    # the counselor, and the children who say her words before she does
    counselor = ('Это Людмила Смирнова, вожатая второго отряда. Двенадцатое июля. Дети, тихо. Я записываю для отчёта. '
                 'Дети, перестаньте повторять. Перестаньте. Я не буду с вами разговаривать. Я буду молчать. '
                 'Если меня кто-нибудь слышит: не приезжайте за детьми. Это уже не дети.')
    kids = [g.resample(say(counselor, IRINA, 1.0, 0.8, 1.0), 1.28 + 0.05 * i) for i in range(4)]

    def counselor_fx(v):
        layers = [(0.0, v, 1.0)] + [(0.55 + i * 0.07, k, 0.2) for i, k in enumerate(kids)]
        return tape_l(g.pad_silence(g.mix(*layers), 0.6, 1.0))
    g.save('tape/tape8', fitted('tape/tape8', counselor, IRINA, counselor_fx, 0.95), 0.8, q=2)

    # ---- loudspeakers
    lineup = ('Внимание. Внимание. Всем отрядам — построиться на линейку. Повторяю. Всем отрядам — на линейку. '
              'Маша Воронова, подойди к вожатой. Маша. Мы тебя ждём.')

    def speaker_fx(v):
        late = g.resample(v, 0.94)  # the second loudspeaker, a little behind and lower
        y = g.addp(radio_l(v, 0.05), np.concatenate([np.zeros(int(0.3 * g.SR)), radio_l(late, 0.05) * 0.18]))
        return g.reverb(g.pad_silence(y, 0.3, 0.5), 2.5, 0.25, 3500)
    g.save('voice/camp_lineup', fitted('voice/camp_lineup', lineup, IRINA, speaker_fx, 0.97), 0.85, q=2)
    alarm = ('Внимание. Нарушение протокола «Тишина». Объект изолируется. Персоналу — соблюдать тишину. '
             'Не разговаривать. Не бежать. Повторяю. Не разговаривать. Оно слушает.')
    g.save('voice/bunker_alarm', fitted('voice/bunker_alarm', alarm, RUSLAN,
                                        lambda v: g.reverb(radio_l(g.pad_silence(v, 0.3, 0.6), 0.05), 2.5, 0.25, 3500), 0.95), 0.85, q=2)
    # children counting in unison
    layers = []
    for i in range(6):
        k = say('двадцать девять. тридцать. тридцать один. тридцать два.', IRINA, 1.15 + 0.04 * i, 0.8, 1.0)
        layers.append((g.rng.uniform(0, 0.15), g.resample(k, 1.3 + 0.05 * g.rng.normal()), 0.6 + 0.4 * g.rng.random()))
    kidsc = g.bp(g.mix(*layers), 250, 6000)
    g.save('voice/camp_children', g.reverb(g.pad_silence(kidsc, 0.1, 0.3), 2.8, 0.5, 3500), 0.8)

    # ---- the mimic's babble (a voice copying a voice)
    babble = say('ты где. иди сюда. всё нормально. иди сюда. ты где.', DENIS, 1.1, 0.9, 1.0)
    babble = g.resample(babble, 0.92)
    g.save('entity/mimic_idle', g.reverb(g.lp(babble, 3000), 1.5, 0.4), 0.7)
    print('done')


if __name__ == '__main__':
    main()
