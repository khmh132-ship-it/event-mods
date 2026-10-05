#!/usr/bin/env python3
"""Story lines voiced with Piper: dreams (whispered narrator), Kuzmich, the false Lisitsyn. Needs gen_voices_neural.py setup."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g
import gen_voices_neural as n
import numpy as np

DREAMS = [
    'Тебе снится эфир. Женский голос считает. На каждом числе в лесу гаснет одно окно. На твоём числе гаснет твоё.',
    'Тебе снится Лисицын. Он стоит у окна ретранслятора и шевелит губами в такт твоему дыханию. Он не моргает. Он учится.',
    'Тебе снится девочка со шкатулкой. Не говори с ними, шепчет она твоим голосом. Они запоминают. Шкатулка играет. Всё вокруг замолкает и слушает.',
    'Тебе снится колокол. Он звонит без звука, и от этой тишины у тебя идёт кровь из ушей. Кто-то внизу пытается повторить звон. У него не получается.',
    'Тебе снится колодец. Ты спускаешься по лестнице, бесконечно. Снизу кто-то поднимается тебе навстречу. Так же медленно. Шаг в шаг.',
    'Тебе снится Воронов. Мы его не нашли, говорит он. Мы его разбудили. Это разные вещи. У него твоё лицо.',
    'Тебе снится, что ты проснулся. У кровати стоишь ты и смотришь, как ты спишь. Потом ложишься на твоё место.',
    'Тебе снятся все, кого ты встретил. Они стоят кругом и считают. Ты — следующее число.',
    'Тебе больше ничего не снится. Тебе только слышится.',
]
KUZMICH = [
    'Живой? Живой... Я — Пётр Кузьмич. Я тут один остался. Не ходите в церковь ночью: там колокол звонит сам.',
    'Язык колокола Никодим искал, да не нашёл. Он у меня в подполе, в доме у колодца. Только не зови меня по имени, слышишь? Оно запоминает.',
    'А ты ведь меня не узнал? А я тебя узнал. Голос у тебя хороший. Очень хороший голос.',
]


def main():
    for i, t in enumerate(DREAMS):
        # a soft narrator, half-whispered, far away; must fit inside the 13 s dream card
        v = n.say(t, n.IRINA, 1.0, 0.6, 0.8)
        if len(v) / g.SR > 11.5:
            v = n.say(t, n.IRINA, 11.5 / (len(v) / g.SR), 0.6, 0.8)
        w = n.whisperize(v)
        y = g.mix((0.0, v, 0.45), (0.0, w, 0.7))
        g.save(f'dream/d{i + 1}', g.reverb(g.lp(y, 5000), 3.0, 0.4, 3000), 0.75)
    for i, t in enumerate(KUZMICH):
        v = g.resample(n.say(t, n.DMITRI, 1.12, 0.7, 0.9), 0.93)  # an old man
        if i == 2:  # on the last line the voice slips into yours
            w = g.resample(n.say(t, n.DENIS, 1.12), 1.0)
            y = g.mix((0.0, v, 0.9), (0.0, w * np.linspace(0, 1, len(w)), 0.8))
        else:
            y = v
        g.save(f'voice/kuzmich{i + 1}', g.reverb(y, 1.2, 0.2, 4000), 0.8)
    # a mimic without a recording of you: almost a person
    lines = ['иди сюда', 'я тут', 'ты чего стоишь?', 'подойди', 'всё нормально, иди сюда']
    for i, t in enumerate(lines):
        vc = [n.DENIS, n.IRINA, n.DMITRI, n.RUSLAN, n.IRINA][i]
        v = n.say(t, vc, 1.05, 0.9, 1.1)
        r = g.resample(v, 0.97 + 0.02 * i)
        wrong = r * (1 + 0.15 * np.sin(2 * np.pi * 7 * np.arange(len(r)) / g.SR))  # a tremor that isn't quite human
        g.save(f'voice/mimic{i + 1}', g.reverb(g.lp(wrong, 4500), 1.6, 0.3, 3000), 0.8)
    # fragments of other people's transmissions the locator picks up at night
    snippets = [('...кто-нибудь слышит? Приём. Кто-нибудь...', n.DENIS), ('...нас осталось трое. Нет. Четверо. Нас стало четверо...', n.DMITRI),
                ('...не выходите к колодцу. Повторяю, не выходите...', n.IRINA), ('...он стоит у окна. Уже второй час...', n.RUSLAN),
                ('...это не я. Если услышите меня утром — это не я...', n.DENIS), ('...мама, забери меня. Мама...', n.IRINA)]
    for i, (t, vc) in enumerate(snippets):
        v = n.say(t, vc, 1.0, 0.8, 1.0)
        if vc == n.IRINA and i == 5:
            v = g.resample(v, 1.28)
        g.save(f'radio/snip{i + 1}', n.radio_l(g.pad_silence(v, 0.3, 0.4), 0.12), 0.75)
    # the lullaby ending, told in a whisper over the music box
    t = 'Сердце бьётся всё медленнее. Эхо пытается повторить колыбельную. У него почти получается.'
    v = n.say(t, n.IRINA, 1.15, 0.6, 0.8)
    y = g.mix((0.0, v, 0.45), (0.0, n.whisperize(v), 0.7))
    g.save('dream/lullaby', g.reverb(g.lp(y, 5000), 3.0, 0.4, 3000), 0.7)
    lis = n.say('Сюда! Я в церкви! Быстрее, пока оно не вернулось!', n.DENIS, 0.9, 0.8, 1.0)
    g.save('voice/lis_call', g.reverb(g.lp(lis, 5000), 2.2, 0.35, 3000), 0.8)
    why = n.say('Почему ты не идёшь ко мне? Почему ты не идёшь ко мне. Почему.', n.DENIS, 1.15, 0.9, 1.0)
    g.save('voice/lis_why', g.reverb(g.lp(g.resample(why, 0.9), 4000), 2.5, 0.45, 2500), 0.8)


if __name__ == '__main__':
    main()
