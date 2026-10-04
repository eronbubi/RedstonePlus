"""
Composes the music of the Redstone Realm and the toll of the Great Bell. Nothing is sampled: every note is synthesized.

    python tools/gen_realm_music.py            (needs numpy, scipy and soundfile; run after gen_realm_sounds.py)

Writes assets/redstoneplus/sounds/music/realm_N.ogg (three tracks, picked at random by the "music.realm" event that every
realm biome plays) and sounds/ambient/great_bell.ogg, and adds both events to sounds.json.

The tracks tell the realm's story:
  1  Concordance    the world as it was: the Bell keeping time, a music box, the patient clock of the Foundry City
  2  The Overtoll   the order that broke it: a choir of engines, a progression that never resolves, glass and thunder
  3  Engine Heart   what is left: a slow mechanical heartbeat, repeaters ticking an arpeggio nobody listens to
"""
import json
import os

import numpy as np
import soundfile as sf
from scipy import signal

SR = 32000
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'redstoneplus')
rng = np.random.default_rng(1913)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def t(dur):
    return np.arange(int(dur * SR)) / SR


def env(dur, a=0.01, r=0.5, hold=None):
    """Attack, sustain, release envelope."""
    n = int(dur * SR)
    e = np.ones(n)
    na, nr = max(1, int(a * SR)), max(1, int(r * SR))
    e[:na] = np.linspace(0, 1, na) ** 2
    e[-nr:] *= np.linspace(1, 0, nr) ** 2
    return e


def low(x, f, order=2):
    b, a = signal.butter(order, min(f / (SR / 2), 0.99), 'low')
    return signal.lfilter(b, a, x)


def high(x, f, order=2):
    b, a = signal.butter(order, min(f / (SR / 2), 0.99), 'high')
    return signal.lfilter(b, a, x)


def band(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi / (SR / 2), 0.99)], 'band')
    return signal.lfilter(b, a, x)


# ------------------------------------------------------------------------------------------ instruments
def bell(f, dur=6.0, bright=1.0):
    """Church-bell partials (hum, prime, minor third, fifth, nominal ...), each with its own decay and a slight beat."""
    tt = t(dur)
    out = np.zeros_like(tt)
    for ratio, amp, decay in ((0.5, 0.8, 2.2), (1.0, 1.0, 1.4), (1.2, 0.55, 1.0), (1.5, 0.35, 0.8), (2.0, 0.6 * bright, 0.7),
                              (2.51, 0.25 * bright, 0.45), (3.01, 0.2 * bright, 0.35), (4.07, 0.12 * bright, 0.25)):
        beat = 1 + 0.15 * np.sin(2 * np.pi * (0.7 + ratio * 0.3) * tt)
        out += amp * np.sin(2 * np.pi * f * ratio * tt + rng.random() * 6) * np.exp(-tt / (decay * dur / 4)) * beat
    strike = low(rng.standard_normal(len(tt)), 3000) * np.exp(-tt / 0.01) * 0.3
    return (out + strike) * env(dur, 0.002, 0.3)


def music_box(f, dur=2.5):
    tt = t(dur)
    tone = np.sin(2 * np.pi * f * tt) + 0.35 * np.sin(2 * np.pi * f * 2.0 * tt) + 0.12 * np.sin(2 * np.pi * f * 4.2 * tt)
    return tone * np.exp(-tt / 0.6) * env(dur, 0.002, 0.2)


def pad(freqs, dur, cutoff=900, a=3.0, r=4.0):
    """Slow detuned saw choir through a breathing low-pass."""
    tt = t(dur)
    out = np.zeros_like(tt)
    for f in freqs:
        for det in (-0.12, 0.0, 0.11):
            ph = rng.random()
            out += signal.sawtooth(2 * np.pi * f * (1 + det / 100) * tt + ph) * 0.25
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * tt / max(dur, 1) * 1.3)
    out = low(out, cutoff * (0.6 + 0.6 * lfo.mean()), 2)
    return out * env(dur, a, r) / max(1, len(freqs))


def choir(freqs, dur, a=2.5, r=3.5):
    """Vowel-ish voices: harmonics shaped by two formants, with vibrato."""
    tt = t(dur)
    out = np.zeros_like(tt)
    for f in freqs:
        vib = 1 + 0.004 * np.sin(2 * np.pi * (4.8 + rng.random()) * tt)
        phase = 2 * np.pi * np.cumsum(f * vib) / SR
        for h in range(1, 14):
            fh = f * h
            form = np.exp(-((fh - 600) / 220) ** 2) + 0.6 * np.exp(-((fh - 1100) / 300) ** 2) + 0.15
            out += np.sin(h * phase) * form / h ** 0.6
    return out * env(dur, a, r) / (len(freqs) * 3)


def drone(f, dur):
    tt = t(dur)
    x = np.sin(2 * np.pi * f * tt) + 0.5 * np.sin(2 * np.pi * f * 1.5 * tt + 1) + 0.3 * np.sin(2 * np.pi * f * 2.003 * tt)
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * tt / 23.0) * np.sin(2 * np.pi * tt / 37.0)
    return x * swell * env(dur, 6, 6)


def tick(dur=0.06, f=3500):
    tt = t(dur)
    return band(rng.standard_normal(len(tt)), f * 0.7, f * 1.4) * np.exp(-tt / 0.008)


def glass(f, dur=4.0):
    """FM chime, glassy."""
    tt = t(dur)
    mod = np.sin(2 * np.pi * f * 3.5 * tt) * 2.0 * np.exp(-tt / 0.4)
    return np.sin(2 * np.pi * f * tt + mod) * np.exp(-tt / 1.2) * env(dur, 0.003, 0.5)


def thud(f=50, dur=0.6, drop=0.5):
    tt = t(dur)
    freq = f * (1 + drop * np.exp(-tt / 0.05))
    return np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-tt / 0.18)


def wind(dur):
    n = rng.standard_normal(int(dur * SR))
    sweep = 0.5 + 0.5 * np.sin(2 * np.pi * t(dur) / 17.0)
    return band(n, 200, 900) * (0.3 + 0.7 * sweep) * env(dur, 4, 4)


# ------------------------------------------------------------------------------------------ mixing
class Track:
    def __init__(self, dur):
        self.n = int(dur * SR)
        self.L = np.zeros(self.n)
        self.R = np.zeros(self.n)

    def add(self, x, at, gain=1.0, pan=0.0):
        i = int(at * SR)
        if i >= self.n:
            return
        x = x[:self.n - i] * gain
        self.L[i:i + len(x)] += x * np.sqrt(0.5 * (1 - pan))
        self.R[i:i + len(x)] += x * np.sqrt(0.5 * (1 + pan))

    def render(self, wet=0.35, room=4.5, decay=1.3):
        out = []
        for ch, seed in ((self.L, 1), (self.R, 2)):
            r = np.random.default_rng(seed)
            tt = t(room)
            ir = r.standard_normal(len(tt)) * np.exp(-tt / decay)
            ir = low(ir, 5000)
            ir /= np.sqrt(np.sum(ir ** 2))
            rev = signal.fftconvolve(ch, ir)[:len(ch)]
            out.append(ch * (1 - wet) + rev * wet * 1.6)
        st = np.stack(out, axis=1)
        st = high(st.T, 30).T
        peak = np.max(np.abs(st)) or 1
        st = np.tanh(1.1 * st / peak) * 0.82
        fade = int(SR * 3)
        st[-fade:] *= np.linspace(1, 0, fade)[:, None]
        st[:int(SR * 0.5)] *= np.linspace(0, 1, int(SR * 0.5))[:, None]
        return st


D_MINOR = [62, 64, 65, 67, 69, 70, 72, 74]  # D E F G A Bb C D


def melody_walk(count, start=4, scale=D_MINOR):
    i = start
    out = []
    for _ in range(count):
        i = int(np.clip(i + rng.choice([-2, -1, -1, 0, 1, 1, 2, 3, -3]), 0, len(scale) - 1))
        out.append(scale[i])
    return out


# ------------------------------------------------------------------------------------------ the tracks
def concordance():
    dur = 150
    tr = Track(dur)
    tr.add(drone(hz(38), dur), 0, 0.35)
    tr.add(drone(hz(45), dur) * 0.6, 0, 0.25, pan=0.3)
    tr.add(wind(dur), 0, 0.1)
    # the Bell, far away, keeping time
    for k, at in enumerate(np.arange(4, dur - 8, 13.0)):
        tr.add(bell(hz(50 if k % 3 else 45), 9.0), at, 0.5, pan=-0.2)
    # the clock of the Foundry City
    for at in np.arange(20, 130, 1.0):
        fade = min(1, (at - 20) / 10, (130 - at) / 10)
        tr.add(tick(f=3200 if int(at) % 2 else 2600), at, 0.12 * fade, pan=0.5 if int(at) % 2 else -0.5)
    # a music box remembering a tune
    at = 30.0
    notes = melody_walk(48)
    for k, n in enumerate(notes):
        if at > 125:
            break
        tr.add(music_box(hz(n + 12)), at, 0.22, pan=np.sin(k * 0.7) * 0.4)
        at += rng.choice([0.6, 0.9, 0.9, 1.2, 1.8, 2.4])
    return tr.render(wet=0.4)


def overtoll():
    dur = 160
    tr = Track(dur)
    progression = [[50, 53, 57], [46, 50, 53], [43, 46, 50], [45, 49, 52], [50, 53, 57], [46, 50, 55], [43, 46, 50], [45, 49, 52, 55]]
    at = 2.0
    for k, chord in enumerate(progression * 2):
        if at > dur - 20:
            break
        tr.add(choir([hz(n) for n in chord], 20.0), at, 0.5, pan=-0.2)
        tr.add(pad([hz(chord[0] - 12)], 20.0, cutoff=500), at, 0.35, pan=0.2)
        at += 17.0
    # glass chimes, sparse and high
    for at in sorted(rng.uniform(15, dur - 15, 22)):
        tr.add(glass(hz(int(rng.choice([74, 77, 81, 82, 86])))), at, 0.12, pan=rng.uniform(-0.8, 0.8))
    # thunder of the cities answering
    for at in (48.0, 96.0, 130.0):
        tr.add(thud(38, 2.5, 0.8) + low(rng.standard_normal(int(2.5 * SR)), 180) * np.exp(-t(2.5) / 0.6) * 0.5, at, 0.6)
        tr.add(bell(hz(43), 10.0, bright=1.4), at + 0.1, 0.45)
    tr.add(wind(dur), 0, 0.12)
    return tr.render(wet=0.45, room=5.0, decay=1.6)


def engine_heart():
    dur = 140
    tr = Track(dur)
    tr.add(drone(hz(33), dur), 0, 0.3)
    beat = 60 / 52
    for k, at in enumerate(np.arange(3, dur - 5, beat)):
        fade = min(1, (at - 3) / 12, (dur - 5 - at) / 12)
        tr.add(thud(52, 0.5), at, 0.5 * fade)
        tr.add(thud(46, 0.45), at + 0.28, 0.35 * fade)
    # repeaters ticking an arpeggio
    arp = [62, 65, 69, 72, 69, 65, 62, 60, 58, 62, 65, 69, 70, 69, 65, 62]
    step = 60 / 104 / 2
    for k, at in enumerate(np.arange(16, dur - 16, step)):
        swell = 0.5 - 0.5 * np.cos(2 * np.pi * (at - 16) / 48)
        n = arp[k % len(arp)] + (12 if (k // 64) % 2 else 0)
        x = band(music_box(hz(n), 0.5), 300, 2500 + 2500 * swell)
        x[:int(0.03 * SR)] += tick(0.03, 4200) * 0.3
        tr.add(x, at, 0.16 * swell + 0.03, pan=0.5 * np.sin(k * 0.4))
    for at in (60.0, 118.0):
        tr.add(bell(hz(38), 12.0), at, 0.4)
    tr.add(pad([hz(50), hz(57)], 50.0, cutoff=700, a=8, r=10), 40, 0.25)
    return tr.render(wet=0.3)


def great_bell():
    """The toll of the Concordance, heard from below: an enormous low bell with a long, beating tail."""
    x = bell(hz(33), 9.0, bright=1.2) * 1.0 + bell(hz(45), 9.0, bright=0.8) * 0.35
    x += low(rng.standard_normal(len(x)), 120) * np.exp(-t(9.0) / 0.4) * 0.4
    tr = Track(10.0)
    tr.add(x, 0.0)
    st = tr.render(wet=0.5, room=4.0, decay=1.8)
    return st.mean(axis=1)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.ascontiguousarray(data, dtype=np.float32)
    channels = 1 if data.ndim == 1 else data.shape[1]
    # libsndfile's vorbis encoder overflows its stack on long buffers: feed it a second at a time
    with sf.SoundFile(path, 'w', SR, channels, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(data), SR):
            f.write(data[i:i + SR])


def main():
    music = []
    for i, fn in enumerate((concordance, overtoll, engine_heart), 1):
        rel = f'music/realm_{i}'
        write(os.path.join(ASSETS, 'sounds', rel + '.ogg'), fn())
        music.append({'name': f'redstoneplus:{rel}', 'stream': True})
        print('composed', fn.__name__)
    write(os.path.join(ASSETS, 'sounds', 'ambient', 'great_bell.ogg'), great_bell())
    path = os.path.join(ASSETS, 'sounds.json')
    with open(path, encoding='utf-8') as f:
        entries = json.load(f)
    entries['music.realm'] = {'sounds': music}
    entries['ambient.great_bell'] = {'sounds': [{'name': 'redstoneplus:ambient/great_bell', 'attenuation_distance': 64}],
                                     'subtitle': 'subtitles.redstoneplus.ambient.great_bell'}
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
        f.write('\n')
    for lang, text in (('en_us', 'The Great Bell tolls'), ('de_de', 'Die Große Glocke schlägt')):
        lp = os.path.join(ASSETS, 'lang', lang + '.json')
        with open(lp, encoding='utf-8') as f:
            current = json.load(f)
        current['subtitles.redstoneplus.ambient.great_bell'] = text
        with open(lp, 'w', encoding='utf-8') as f:
            json.dump(current, f, indent=2, ensure_ascii=False)
            f.write('\n')


if __name__ == '__main__':
    main()
