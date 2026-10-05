#!/usr/bin/env python3
"""
Генерация голоса комплекса: lines.txt → .ogg + sounds.json + voice_lines.json.

Требуется: torch, soundfile, ffmpeg; модель Silero v4_ru (скачивается автоматически в tools/voice/.cache).
Запуск:  python3 tools/voice/build_voice.py [--only id1,id2] [--speaker aidar]
Готовые файлы кэшируются по хэшу текста — перегенерируются только изменённые реплики.
"""
import argparse, hashlib, json, os, re, subprocess, sys, tempfile, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'trialcomplex')
OUT_DIR = os.path.join(ASSETS, 'sounds', 'voice')
CACHE = os.path.join(HERE, '.cache')
MODEL_URL = 'https://models.silero.ai/models/tts/ru/v4_ru.pt'
SR = 48000

# Обработка: «голос системы» — срез низов/верхов, компрессия, короткое эхо помещения, лёгкий щелчок в начале.
FILTER = ("highpass=f=110,lowpass=f=8500,"
          "acompressor=threshold=-20dB:ratio=3:attack=5:release=90,"
          "aecho=0.85:0.45:28|57:0.22|0.12,"
          "asetrate={sr}*0.97,aresample={sr},atempo=1.03,"
          "loudnorm=I=-15:TP=-1.5:LRA=7")


def parse(path):
    lines = {}
    with open(path, encoding='utf-8') as f:
        for raw in f:
            raw = raw.strip()
            if not raw or raw.startswith('#'):
                continue
            ident, text = [s.strip() for s in raw.split('|', 1)]
            lines[ident] = text
    return lines


# Произношение: в субтитрах — как написано, в синтезатор — как надо читать.
SPOKEN = {'Кхмх': 'Кх+эмх', 'Итачи': 'Ит+ачи', 'аниме': 'ан+имэ', 'шорткат': 'шортк+ат', 'вайфая': 'вайф+ая',
          'Учиха': 'Уч+иха', 'слэш пазл хинт': 'слэш п+азл хинт'}
INLINE = re.compile(r'\[\[(.*?)\|\|(.*?)\]\]')


def subtitle(text):
    return INLINE.sub(lambda m: m.group(1), text).replace('+', '')


def tts_text(text):
    t = INLINE.sub(lambda m: m.group(2), text)
    for k, v in SPOKEN.items():
        t = t.replace(k, v)
    # Silero не любит кавычки-ёлочки и длинные тире
    t = t.replace('«', '').replace('»', '').replace('—', ',').replace('…', '...')
    t = re.sub(r'\s+,', ',', t)
    return t


def load_model():
    import torch
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, 'v4_ru.pt')
    if not os.path.exists(path):
        print('скачиваю модель Silero...')
        urllib.request.urlretrieve(MODEL_URL, path)
    model = torch.package.PackageImporter(path).load_pickle('tts_models', 'model')
    model.to('cpu')
    return model


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='')
    ap.add_argument('--speaker', default='aidar')
    args = ap.parse_args()

    lines = parse(os.path.join(HERE, 'lines.txt'))
    os.makedirs(OUT_DIR, exist_ok=True)
    state_path = os.path.join(HERE, 'voice_state.json')
    state = json.load(open(state_path, encoding='utf-8')) if os.path.exists(state_path) else {}
    only = set(filter(None, args.only.split(',')))

    model = None
    import soundfile as sf
    for ident, text in lines.items():
        fname = ident.replace('.', '_') + '.ogg'
        out = os.path.join(OUT_DIR, fname)
        key = hashlib.sha1((args.speaker + '|' + FILTER + '|' + text).encode()).hexdigest()
        if not only and state.get(ident, {}).get('key') == key and os.path.exists(out):
            continue
        if only and ident not in only:
            continue
        if model is None:
            model = load_model()
        audio = model.apply_tts(text=tts_text(text), speaker=args.speaker, sample_rate=SR, put_accent=True, put_yo=True)
        with tempfile.TemporaryDirectory() as td:
            wav = os.path.join(td, 'a.wav')
            sf.write(wav, audio.numpy(), SR)
            # 120 мс тишины в начале, щелчок «динамика», обработка, моно ogg
            cmd = ['ffmpeg', '-y', '-loglevel', 'error',
                   '-f', 'lavfi', '-i', f'sine=f=1800:d=0.035:sample_rate={SR}',
                   '-i', wav,
                   '-filter_complex',
                   f'[0]volume=0.12,afade=t=out:st=0.02:d=0.015,apad=pad_dur=0.09[c];'
                   f'[1]{FILTER.format(sr=SR)}[v];[c][v]concat=n=2:v=0:a=1,apad=pad_dur=0.25[o]',
                   '-map', '[o]', '-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', out]
            subprocess.run(cmd, check=True)
        dur = float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration',
                                             '-of', 'default=nw=1:nk=1', out]).decode().strip())
        state[ident] = {'key': key, 'ms': int(dur * 1000)}
        print(f'{ident}: {dur:.1f}s')

    # удалить файлы реплик, которых больше нет
    valid = {i.replace('.', '_') + '.ogg' for i in lines}
    for f in os.listdir(OUT_DIR):
        if f.endswith('.ogg') and f not in valid:
            os.remove(os.path.join(OUT_DIR, f))
    state = {k: v for k, v in state.items() if k in lines}
    json.dump(state, open(state_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=0, sort_keys=True)

    sounds = {f'voice.{i}': {'sounds': [{'name': f'trialcomplex:voice/{i.replace(".", "_")}', 'stream': True}],
                             'subtitle': f'trialcomplex.voice'} for i in lines}
    json.dump(sounds, open(os.path.join(ASSETS, 'sounds.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    index = {i: {'text': subtitle(t), 'ms': state[i]['ms']} for i, t in lines.items() if i in state}
    json.dump(index, open(os.path.join(ASSETS, 'voice_lines.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f'готово: {len(index)} реплик')


if __name__ == '__main__':
    main()
