#!/usr/bin/env python3
"""Extra voiced tapes (round 2). Reuses the DSP helpers of gen_sounds.py."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g
import numpy as np

TAPES = {
    # Masha, 7: a child's recording on her father's recorder
    'tape5': ('Это Маша. Папа сказал записать, как я пою. Баю, баюшки, баю. Мама, перестань повторять. '
              'Мама, ты стоишь за дверью? Мама. Почему у тебя мой голос?', 'ru+f5', 120, 85, 1.12),
    # Lisitsyn, last night at the relay, whispering into the microphone
    'tape6': ('Это Лисицын. Три часа ночи. Оно стучит. Три раза. Как Таня. Я не открою. '
              'Я записываю, чтобы помнить свой голос. Если утром я заговорю иначе, сожгите эту плёнку. '
              'Оно перестало стучать. Оно говорит. Оно говорит со мной моим голосом.', 'ru+m3', 105, 30, 0.95),
    # Tanya, recorded in the relay room after the door was opened
    'tape7': ('Серёжа, ты меня слышишь? Ты стоишь у окна уже шесть часов. Ты не моргаешь. '
              'Я нашла твою плёнку. Ты просил её сжечь. Я не смогла. Это всё, что от тебя осталось. '
              'Если кто-то найдёт это, не открывайте ему. Он очень хорошо говорит моим именем.', 'ru+f2', 108, 50, 1.0),
}


def main():
    out = {}
    for name, (text, voice, speed, pitch, rs) in TAPES.items():
        v = g.speak(text, voice, speed, pitch, gap=6)
        if rs != 1.0:
            v = g.resample(v, rs)
        x = g.tape(g.pad_silence(v, 0.6, 0.8))
        if name == 'tape5':  # the mother's voice repeating her, half a second late
            echo = np.concatenate([np.zeros(int(0.5 * g.SR)), g.resample(v, 0.82) * 0.35])
            x = g.addp(x, g.tape(g.pad_silence(echo, 0.6, 0.3), hiss=0.0))
        g.save(f'tape/{name}', x, 0.8, q=2)
        out[name] = len(x) / g.SR
    print(out)


if __name__ == '__main__':
    main()
