"""
Synthesizes the original sounds of the realm creatures (no samples, everything is generated) and writes
assets/redstoneplus/sounds/entity/NAME/KIND[N].ogg plus the entries in assets/redstoneplus/sounds.json.

    python tools/gen_realm_sounds.py            (needs numpy, scipy and soundfile)
"""
import json
import os

import numpy as np
import soundfile as sf
from scipy import signal

SR = 32000
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'redstoneplus')
rng = np.random.default_rng(1337)


# ------------------------------------------------------------------------------------------ primitives
def t(dur):
    return np.arange(int(SR * dur)) / SR


def env(dur, attack=0.005, decay=None, curve=4.0):
    x = t(dur)
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    d = np.exp(-curve * x / (decay or dur))
    return a * d


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def band(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], btype='band')
    return signal.lfilter(b, a, x)


def low(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), btype='low')
    return signal.lfilter(b, a, x)


def high(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), btype='high')
    return signal.lfilter(b, a, x)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def at(x, offset):
    return np.concatenate([np.zeros(int(SR * offset)), x])


def metal(freq, dur, ratios=(1.0, 2.76, 5.4, 8.93, 13.34), bright=1.0):
    """Struck metal: inharmonic partials, higher ones die faster."""
    x = t(dur)
    out = np.zeros_like(x)
    for i, r in enumerate(ratios):
        out += np.sin(2 * np.pi * freq * r * x + rng.uniform(0, 6)) * np.exp(-x * (3 + i * 4) / dur) * (bright ** i) / (i + 1)
    return out * env(dur, 0.001, dur)


def bell(freq, dur):
    return metal(freq, dur, ratios=(0.5, 1.0, 1.19, 1.56, 2.0, 2.66, 3.01, 4.1), bright=0.8)


def thud(freq, dur, drop=0.5):
    x = t(dur)
    f = freq * (1 - drop * x / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) + 0.4 * low(noise(dur), 300)) * env(dur, 0.002, dur * 0.6)


def servo(f0, f1, dur, grit=0.3):
    x = t(dur)
    f = np.linspace(f0, f1, len(x)) * (1 + 0.02 * np.sin(2 * np.pi * 18 * x))
    ph = 2 * np.pi * np.cumsum(f) / SR
    saw = 2 * ((ph / (2 * np.pi)) % 1) - 1
    s = band(saw, f0 * 0.8, max(f0, f1) * 6) + grit * band(noise(dur), 1500, 6000)
    return s * env(dur, 0.03, dur, 1.5)


def hiss(dur, lo=2000, hi=9000, attack=0.02, curve=3.0):
    return band(noise(dur), lo, hi) * env(dur, attack, dur, curve)


def sparks(dur, count=14):
    out = np.zeros(int(SR * dur))
    for _ in range(count):
        p = int(rng.uniform(0, dur * 0.9) * SR)
        n = int(SR * rng.uniform(0.002, 0.01))
        burst = high(rng.uniform(-1, 1, n), 3000) * np.exp(-np.arange(n) / (n / 4))
        out[p:p + n] += burst[:len(out) - p]
    return out


def squelch(dur, f0=180, f1=90):
    x = t(dur)
    f = np.linspace(f0, f1, len(x)) + 30 * np.sin(2 * np.pi * 7 * x)
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) * 0.5
    wet = band(noise(dur), 200, 1200) * (0.6 + 0.4 * np.sin(2 * np.pi * 11 * x))
    return (tone + wet) * env(dur, 0.02, dur, 2.5)


def growl(freq, dur, rough=0.5):
    x = t(dur)
    f = freq * (1 + 0.08 * np.sin(2 * np.pi * 3 * x))
    ph = 2 * np.pi * np.cumsum(f) / SR
    saw = 2 * ((ph / (2 * np.pi)) % 1) - 1
    am = 1 + rough * np.sin(2 * np.pi * 27 * x)
    body = band(saw * am, freq, freq * 8)
    return body * env(dur, 0.05, dur, 1.8)


def chain(dur, hits=5, freq=1800):
    out = np.zeros(int(SR * dur))
    for i in range(hits):
        off = rng.uniform(0, dur * 0.75)
        m = metal(freq * rng.uniform(0.8, 1.2), 0.12, bright=0.9) * rng.uniform(0.4, 1)
        p = int(off * SR)
        out[p:p + len(m)] += m[:len(out) - p]
    return out


def whoosh(dur, lo=300, hi=2500):
    x = t(dur)
    shape = np.sin(np.pi * x / dur) ** 2
    return band(noise(dur), lo, hi) * shape


def zap(dur):
    x = t(dur)
    f = 60 * (1 + 3 * np.exp(-x * 8))
    ph = 2 * np.pi * np.cumsum(f) / SR
    buzz = np.sign(np.sin(ph)) * 0.4
    return (band(buzz, 80, 4000) + sparks(dur, 20)) * env(dur, 0.002, dur, 2.0)


def piston(dur=0.35, pitch=1.0):
    return mix(thud(90 * pitch, dur), hiss(dur * 0.6, 1500, 6000, 0.001, 6) * 0.5, metal(420 * pitch, dur * 0.8) * 0.4)


def crackle(dur, rate=40):
    out = np.zeros(int(SR * dur))
    for _ in range(int(rate * dur)):
        p = int(rng.uniform(0, dur) * SR)
        n = int(SR * 0.004)
        out[p:p + n] += rng.uniform(0.2, 1) * high(noise(0.004), 1500)[:len(out) - p]
    return out


# ------------------------------------------------------------------------------------------ recipes
def v(fn, n=2):
    """n variants of a recipe (the recipe draws random numbers, so each call differs)."""
    return [fn for _ in range(n)]


RECIPES = {
    'karst_colossus': {
        'ambient': v(lambda: mix(growl(55, 1.6, 0.6) * 0.8, at(piston(0.4, 0.7), 0.9) * 0.6)),
        'hurt': v(lambda: mix(metal(210, 0.5), thud(80, 0.4) * 0.6)),
        'death': [lambda: mix(growl(50, 2.2, 0.8), at(piston(0.6, 0.5), 0.3), at(thud(45, 1.2, 0.7), 1.0), at(chain(1.0, 8, 900), 0.6) * 0.5)],
        'step': v(lambda: mix(thud(60, 0.35, 0.3), metal(300, 0.2) * 0.2)),
        'ability': [lambda: mix(piston(0.5, 0.6), at(thud(40, 1.0, 0.6) * 1.2, 0.1), at(hiss(0.8, 800, 4000, 0.01, 2), 0.1) * 0.5)],
    },
    'switchback_crawler': {
        'ambient': v(lambda: mix(servo(300, 520, 0.7, 0.4) * 0.7, chain(0.7, 4, 2400) * 0.4)),
        'hurt': v(lambda: mix(metal(640, 0.3), servo(700, 300, 0.25) * 0.4)),
        'death': [lambda: mix(servo(500, 80, 1.4, 0.6), at(chain(1.0, 10, 2000), 0.2) * 0.6, at(thud(70, 0.6), 0.9))],
        'step': v(lambda: mix(metal(1400, 0.08, bright=0.7) * 0.5, crackle(0.08, 60) * 0.3), 3),
        'ability': [lambda: mix(servo(200, 900, 0.9, 0.5), hiss(0.9, 1000, 5000, 0.05, 1.2) * 0.5, at(metal(900, 0.3), 0.05) * 0.5)],
    },
    'bell_stalker': {
        'ambient': v(lambda: mix(bell(220 * rng.uniform(0.9, 1.1), 2.0) * 0.4, whoosh(2.0, 150, 700) * 0.2)),
        'hurt': v(lambda: mix(bell(330, 0.8) * 0.6, metal(900, 0.2) * 0.3)),
        'death': [lambda: mix(bell(110, 3.5), at(bell(147, 3.0), 0.4) * 0.7, at(chain(1.5, 8, 1200), 0.2) * 0.4)],
        'step': v(lambda: mix(metal(700, 0.12, bright=0.6) * 0.35, thud(120, 0.12) * 0.3)),
        'ability': [lambda: mix(bell(98, 3.5) * 1.2, at(bell(98, 3.0), 0.35) * 0.8, at(whoosh(2.5, 60, 300), 0.0) * 0.5)],
    },
    'sluice_chainjaw': {
        'ambient': v(lambda: mix(growl(80, 1.4, 0.7) * 0.6, chain(1.2, 5, 1300) * 0.4, band(noise(1.4), 200, 900) * env(1.4, 0.3, 1.4, 1) * 0.2)),
        'hurt': v(lambda: mix(chain(0.4, 4, 1500), growl(120, 0.4) * 0.5)),
        'death': [lambda: mix(growl(70, 2.0, 0.8), at(chain(1.4, 12, 1300), 0.2), at(squelch(1.0, 140, 60), 0.8) * 0.6)],
        'step': v(lambda: mix(chain(0.18, 2, 1600) * 0.5, thud(90, 0.15) * 0.3)),
        'ability': [lambda: mix(metal(500, 0.2) * 0.8, at(chain(0.6, 7, 1400), 0.05), at(growl(110, 0.6, 0.9), 0.1) * 0.6)],
    },
    'kiln_brute': {
        'ambient': v(lambda: mix(growl(45, 1.8, 0.4) * 0.5, crackle(1.8, 60) * 0.5, whoosh(1.8, 100, 600) * 0.4)),
        'hurt': v(lambda: mix(metal(260, 0.4), crackle(0.4, 120) * 0.5)),
        'death': [lambda: mix(growl(40, 2.5, 0.6), hiss(2.5, 1500, 8000, 0.2, 1.5) * 0.6, at(metal(180, 1.2), 0.3), crackle(2.5, 80) * 0.5)],
        'step': v(lambda: mix(thud(55, 0.3, 0.3), crackle(0.3, 50) * 0.3)),
        'ability': [lambda: mix(whoosh(0.8, 200, 3000), at(whoosh(0.5, 300, 4000), 0.25) * 0.8, crackle(1.0, 120) * 0.5, thud(70, 0.5) * 0.5)],
    },
    'spool_weaver': {
        'ambient': v(lambda: mix(servo(900, 1300, 0.6, 0.2) * 0.4, crackle(0.6, 70) * 0.3)),
        'hurt': v(lambda: mix(metal(1200, 0.25), hiss(0.2, 3000, 9000, 0.001, 5) * 0.4)),
        'death': [lambda: mix(servo(1200, 200, 1.4), at(metal(1600, 0.8), 0.1) * 0.5, at(chain(0.8, 6, 2600), 0.3) * 0.5)],
        'step': v(lambda: crackle(0.07, 90) * 0.5, 3),
        'ability': [lambda: mix(servo(400, 2400, 0.35, 0.1) * 0.7, hiss(0.4, 4000, 11000, 0.001, 4), at(metal(2200, 0.3), 0.3) * 0.6)],
    },
    'leaking_cell': {
        'ambient': v(lambda: mix(growl(90, 1.4, 0.3) * 0.5, squelch(1.0, 220, 120) * 0.5, at(sparks(0.5, 5), 0.6) * 0.3)),
        'hurt': v(lambda: mix(squelch(0.4, 260, 140), metal(420, 0.2) * 0.3)),
        'death': [lambda: mix(growl(70, 1.8, 0.5), at(squelch(1.4, 200, 50), 0.3), at(zap(0.6), 0.2) * 0.4)],
        'step': v(lambda: mix(squelch(0.18, 160, 120) * 0.4, metal(600, 0.1) * 0.15)),
        'ability': [lambda: mix(squelch(0.9, 300, 60), hiss(0.9, 2000, 7000, 0.05, 2) * 0.4, zap(0.4) * 0.3)],
    },
    'detonator_husk': {
        'ambient': v(lambda: mix(hiss(1.0, 3000, 9000, 0.2, 1) * 0.15, at(metal(1800, 0.15), 0.4) * 0.3, at(metal(1800, 0.15), 0.7) * 0.3)),
        'hurt': v(lambda: mix(squelch(0.35, 300, 180), metal(1500, 0.15) * 0.4)),
        'death': [lambda: mix(squelch(1.0, 220, 70), at(sparks(0.8, 25), 0.1))],
        'step': v(lambda: mix(squelch(0.15, 200, 150) * 0.3, metal(900, 0.08) * 0.2)),
        'ability': [lambda: mix(*[at(metal(2000, 0.08) * 0.6, i * 0.12) for i in range(8)], hiss(1.0, 4000, 10000, 0.3, 0.5) * 0.4)],
    },
    'tripwire_brood': {
        'ambient': v(lambda: mix(crackle(0.9, 50) * 0.4, servo(700, 900, 0.9, 0.3) * 0.25, at(bell(1800, 0.5), 0.3) * 0.15)),
        'hurt': v(lambda: mix(metal(1100, 0.25), hiss(0.2, 2500, 8000, 0.001, 5) * 0.3)),
        'death': [lambda: mix(servo(900, 150, 1.2), at(bell(1500, 1.0), 0.2) * 0.3, at(crackle(0.8, 80), 0.3) * 0.5)],
        'step': v(lambda: crackle(0.07, 80) * 0.45, 3),
        'ability': [lambda: mix(metal(1300, 0.3), hiss(0.3, 3000, 9000, 0.001, 4) * 0.5, at(bell(2000, 0.4), 0.05) * 0.3)],
    },
    'kilnbound': {
        'ambient': v(lambda: mix(crackle(1.2, 50) * 0.4, whoosh(1.2, 150, 800) * 0.3, at(metal(1300, 0.1), 0.5) * 0.3)),
        'hurt': v(lambda: mix(metal(1000, 0.25, bright=0.6), crackle(0.3, 100) * 0.4)),
        'death': [lambda: mix(*[at(metal(rng.uniform(900, 1600), 0.15) * 0.6, i * 0.09) for i in range(10)], hiss(1.2, 1000, 5000, 0.1, 2) * 0.5)],
        'step': v(lambda: mix(metal(1100, 0.07, bright=0.5) * 0.35, crackle(0.07, 50) * 0.2)),
        'ability': [lambda: mix(whoosh(0.5, 400, 4000), crackle(0.5, 150) * 0.5, metal(700, 0.2) * 0.3)],
    },
    'living_capacitor': {
        'ambient': v(lambda: mix(np.sin(2 * np.pi * 120 * t(1.2)) * 0.15 * env(1.2, 0.2, 1.2, 1), squelch(0.6, 250, 200) * 0.4, sparks(1.2, 4) * 0.3)),
        'hurt': v(lambda: mix(squelch(0.3, 320, 200), zap(0.25) * 0.5)),
        'death': [lambda: mix(squelch(0.8, 300, 80), zap(0.9), at(sparks(0.8, 30), 0.1) * 0.6)],
        'step': v(lambda: squelch(0.2, 260, 180) * 0.5),
        'ability': [lambda: mix(zap(0.6) * 1.2, at(sparks(0.5, 30), 0.02))],
    },
    'relay_strider': {
        'ambient': v(lambda: mix(np.sin(2 * np.pi * 70 * t(1.6)) * 0.25 * env(1.6, 0.4, 1.6, 1), servo(150, 110, 1.6, 0.2) * 0.3,
                                 at(metal(2400, 0.08), 0.5) * 0.3, at(metal(2400, 0.08), 0.62) * 0.3)),
        'hurt': v(lambda: mix(zap(0.3), metal(700, 0.2) * 0.3)),
        'death': [lambda: mix(servo(300, 30, 2.0), at(zap(1.0), 0.3), at(whoosh(1.2, 80, 600), 0.6) * 0.5)],
        'step': v(lambda: mix(metal(500, 0.12, bright=0.5) * 0.3, thud(100, 0.1) * 0.2)),
        'ability': [lambda: mix(whoosh(0.6, 200, 5000), zap(0.4) * 0.6, at(metal(3000, 0.2), 0.3) * 0.4)],
    },
    'bellows_hog': {
        'ambient': v(lambda: mix(whoosh(0.9, 150, 900) * 0.6, at(whoosh(0.7, 150, 900), 0.8) * 0.5, growl(160, 0.5, 0.8) * 0.4)),
        'hurt': v(lambda: mix(growl(220, 0.35, 1.0), whoosh(0.3, 300, 2000) * 0.5)),
        'death': [lambda: mix(growl(150, 1.4, 1.0), at(whoosh(1.2, 100, 700), 0.3), at(hiss(1.0, 800, 3000, 0.1, 2), 0.4) * 0.4)],
        'step': v(lambda: mix(thud(130, 0.12) * 0.4, metal(800, 0.08) * 0.15)),
        'ability': [lambda: mix(whoosh(0.5, 300, 5000) * 1.3, crackle(0.5, 150) * 0.6, thud(80, 0.3) * 0.5)],
    },
    'flesh_press': {
        'ambient': v(lambda: mix(growl(40, 2.0, 0.3) * 0.6, at(squelch(0.8, 120, 80), 0.6) * 0.4, at(metal(200, 0.8), 1.2) * 0.3)),
        'hurt': v(lambda: mix(squelch(0.4, 180, 90), metal(250, 0.4) * 0.6)),
        'death': [lambda: mix(growl(35, 2.5, 0.5), at(piston(0.8, 0.5), 0.2), at(squelch(1.5, 150, 40), 0.6), at(thud(40, 1.0), 1.2))],
        'step': v(lambda: mix(thud(50, 0.35, 0.3), squelch(0.2, 120, 90) * 0.2)),
        'ability': [lambda: mix(piston(0.5, 0.45) * 1.2, at(thud(40, 0.8, 0.5), 0.15), at(squelch(0.5, 150, 60), 0.15) * 0.5)],
    },
}

RECIPES.update({
    'spark_mite': {
        'ambient': v(lambda: mix(sparks(0.6, 10) * 0.4, metal(3200, 0.05) * 0.2, at(metal(3600, 0.04), 0.15) * 0.2)),
        'hurt': v(lambda: mix(zap(0.2) * 0.6, metal(2600, 0.1) * 0.4)),
        'death': [lambda: mix(sparks(0.7, 24), zap(0.5) * 0.6, at(metal(2000, 0.3), 0.1) * 0.3)],
        'step': v(lambda: metal(3800, 0.03, bright=0.6) * 0.15),
        'ability': [lambda: mix(crackle(0.9, 220) * 0.5, sparks(0.9, 16) * 0.4)],
    },
    'lamp_moth': {
        'ambient': v(lambda: mix(whoosh(0.3, 300, 1500) * 0.3, at(whoosh(0.3, 300, 1500), 0.28) * 0.3, at(metal(2800, 0.08), 0.1) * 0.1)),
        'hurt': v(lambda: mix(metal(2800, 0.1) * 0.5, whoosh(0.2, 500, 3000) * 0.4)),
        'death': [lambda: mix(whoosh(0.8, 200, 2000), at(metal(2000, 0.3), 0.2) * 0.4)],
        'step': v(lambda: whoosh(0.1, 500, 2000) * 0.1),
        'ability': [lambda: mix(whoosh(1.1, 300, 3000) * 0.6, sparks(1.1, 8) * 0.3)],
    },
    'scrap_jackal': {
        'ambient': v(lambda: mix(growl(180, 0.6, 0.8) * 0.5, at(metal(1200, 0.1), 0.2) * 0.2)),
        'hurt': v(lambda: mix(growl(320, 0.3, 1.0), metal(900, 0.15) * 0.4)),
        'death': [lambda: mix(growl(160, 1.2, 1.0), at(metal(600, 0.5), 0.4) * 0.5, at(thud(90, 0.3), 0.8) * 0.4)],
        'step': v(lambda: mix(metal(1500, 0.05, bright=0.5) * 0.2, thud(160, 0.05) * 0.2)),
        'ability': [lambda: mix(growl(260, 0.5, 1.0) * 0.6, crackle(0.7, 120) * 0.4)],
    },
})

# the Grid's builder and the things of the Sealed Reach
RECIPES.update({
    'trackwright': {
        'ambient': v(lambda: mix(servo(300, 520, 0.5, 0.2) * 0.4, at(metal(1800, 0.08), 0.35) * 0.3, at(metal(2100, 0.06), 0.45) * 0.2)),
        'hurt': v(lambda: mix(metal(900, 0.25) * 0.6, servo(500, 200, 0.25, 0.5) * 0.5)),
        'death': [lambda: mix(servo(500, 60, 1.2, 0.4), at(metal(700, 0.8), 0.3) * 0.6, at(sparks(0.8, 20), 0.5) * 0.5, at(thud(70, 0.5), 1.0) * 0.5)],
        'step': v(lambda: mix(metal(2400, 0.04, bright=0.5) * 0.18, thud(140, 0.05) * 0.2)),
        'ability': [lambda: mix(piston(0.3, 1.3) * 0.8, at(thud(90, 0.3), 0.12) * 0.7, at(sparks(0.5, 16), 0.12) * 0.5, at(zap(0.25), 0.15) * 0.4)],
    },
    'wirewraith': {
        'ambient': v(lambda: mix(chain(1.4, 7, 2600) * 0.25, at(bell(311, 1.6), 0.4) * 0.25, growl(70, 1.6, 0.9) * 0.35), 3),
        'hurt': v(lambda: mix(bell(415, 0.6) * 0.6, chain(0.4, 4, 3000) * 0.5, zap(0.3) * 0.3)),
        'death': [lambda: mix(bell(277, 3.0) * 0.7, at(bell(262, 3.0), 0.05) * 0.5, chain(2.0, 12, 2200) * 0.5, at(whoosh(1.5, 80, 1200), 0.5) * 0.6)],
        'step': v(lambda: mix(chain(0.15, 2, 2800) * 0.25, thud(220, 0.05) * 0.15)),
        'ability': [lambda: mix(growl(55, 2.2, 1.0) * 0.6, bell(233, 2.2) * 0.5, at(bell(247, 2.0), 0.03) * 0.5, whoosh(2.2, 120, 5000) * 0.7,
                                crackle(2.2, 90) * 0.3)],
    },
    'maw_engine': {
        'ambient': v(lambda: mix(growl(45, 2.0, 0.6) * 0.6, hiss(1.6, 400, 3000, 0.3) * 0.25, at(crackle(1.2, 30), 0.3) * 0.3)),
        'hurt': v(lambda: mix(growl(90, 0.5, 1.0) * 0.6, metal(300, 0.4) * 0.6)),
        'death': [lambda: mix(growl(40, 3.0, 1.0), at(piston(1.0, 0.35), 0.3) * 0.8, at(thud(35, 1.5), 1.0), at(hiss(2.0, 300, 4000), 0.8) * 0.5)],
        'step': v(lambda: mix(thud(45, 0.4, 0.4) * 0.8, metal(400, 0.2) * 0.2)),
        'ability': [lambda: mix(growl(60, 1.2, 1.0) * 0.7, at(piston(0.4, 0.4), 0.7) * 1.1, at(thud(50, 0.6), 0.8) * 0.8)],
    },
})

SPECIAL = {
    'block.realm_gate.travel': [lambda: mix(servo(80, 600, 1.5, 0.2) * 0.6, whoosh(1.6, 100, 3000), at(bell(196, 2.0), 0.9) * 0.5,
                                            at(sparks(1.0, 25), 0.4) * 0.3)],
    # the light cycle: a seamless engine tone (looped by the game, pitched with speed), materialising, and the lightline being laid
    'entity.light_cycle.engine': [lambda: mix(np.sin(2 * np.pi * 55 * t(2.0)) * 0.5, np.sin(2 * np.pi * 110 * t(2.0)) * 0.3,
                                              np.sign(np.sin(2 * np.pi * 82.5 * t(2.0))) * 0.08, band(noise(2.0), 200, 900) * 0.12)],
    'entity.light_cycle.rez': [lambda: mix(servo(120, 1600, 1.0, 0.1) * 0.6, whoosh(1.0, 300, 6000) * 0.6, at(sparks(0.6, 30), 0.4) * 0.4,
                                           at(bell(523, 1.2), 0.75) * 0.3)],
    'entity.light_cycle.derez': [lambda: mix(servo(1600, 100, 0.9, 0.1) * 0.6, crackle(0.9, 160) * 0.5, whoosh(0.9, 200, 4000) * 0.4)],
    'block.lightline.lay': [lambda: mix(thud(110, 0.2) * 0.6, zap(0.25) * 0.5, sparks(0.4, 10) * 0.4)],
}
SPECIAL_SUBTITLES = {
    'block.realm_gate.travel': ('Realm Gate hums', 'Reichstor summt'),
    'entity.light_cycle.engine': ('Light cycle hums', 'Lichtrad summt'),
    'entity.light_cycle.rez': ('Light cycle materialises', 'Lichtrad materialisiert'),
    'entity.light_cycle.derez': ('Light cycle dissolves', 'Lichtrad zerfällt'),
    'block.lightline.lay': ('Lightline laid', 'Lichtbahn verlegt'),
}

SUBTITLES = {
    'ambient': ('%s whirs', '%s surrt'), 'hurt': ('%s hurts', '%s wird verletzt'), 'death': ('%s breaks down', '%s zerfällt'),
    'step': ('Footsteps', 'Schritte'), 'ability': ('%s strikes', '%s schlägt zu'),
}
NAMES = {
    'karst_colossus': ('Karst Colossus', 'Karst-Koloss'), 'switchback_crawler': ('Switchback Crawler', 'Weichenkriecher'),
    'bell_stalker': ('Bell Stalker', 'Glockenpirscher'), 'sluice_chainjaw': ('Sluice Chainjaw', 'Schleusen-Kettenkiefer'),
    'kiln_brute': ('Kiln Brute', 'Brennofen-Rohling'), 'spool_weaver': ('Spool Weaver', 'Spulenweber'),
    'leaking_cell': ('The Leaking Cell', 'Die Leckende Zelle'), 'detonator_husk': ('Detonator Husk', 'Zünderhülle'),
    'tripwire_brood': ('Tripwire Brood', 'Stolperdraht-Brut'), 'kilnbound': ('Kilnbound', 'Ofengebundener'),
    'living_capacitor': ('Living Capacitor', 'Lebender Kondensator'), 'relay_strider': ('Relay Strider', 'Relais-Schreiter'),
    'bellows_hog': ('Bellows Hog', 'Blasebalg-Keiler'), 'flesh_press': ('Flesh Press', 'Fleischpresse'),
    'spark_mite': ('Spark Mite', 'Funkenmilbe'), 'lamp_moth': ('Lamp Moth', 'Lampenmotte'), 'scrap_jackal': ('Scrap Jackal', 'Schrottschakal'),
    'trackwright': ('Trackwright', 'Bahnwerker'), 'wirewraith': ('Wirewraith', 'Drahtgespenst'), 'maw_engine': ('Maw Engine', 'Schlundmaschine'),
}


def write(path, data):
    peak = np.max(np.abs(data)) or 1.0
    data = np.tanh(1.2 * data / peak) * 0.85
    fade = min(len(data), int(SR * 0.01))
    data[-fade:] *= np.linspace(1, 0, fade)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, data.astype(np.float32), SR, format="OGG", subtype="VORBIS", compression_level=0.75)


def main():
    sounds_json = os.path.join(ASSETS, 'sounds.json')
    entries = {}
    if os.path.exists(sounds_json):
        with open(sounds_json, encoding='utf-8') as f:
            entries = json.load(f)
    en, de = {}, {}
    count = 0
    for mob, kinds in RECIPES.items():
        for kind, recipes in kinds.items():
            files = []
            for i, fn in enumerate(recipes):
                rel = f'entity/{mob}/{kind}{i + 1}'
                write(os.path.join(ASSETS, 'sounds', rel + '.ogg'), fn())
                files.append(f'redstoneplus:{rel}')
                count += 1
            key = f'entity.{mob}.{kind}'
            entries[key] = {'sounds': files, 'subtitle': f'subtitles.redstoneplus.{key}'}
            e_sub, d_sub = SUBTITLES[kind]
            en[f'subtitles.redstoneplus.{key}'] = e_sub % NAMES[mob][0] if '%s' in e_sub else e_sub
            de[f'subtitles.redstoneplus.{key}'] = d_sub % NAMES[mob][1] if '%s' in d_sub else d_sub
    for key, recipes in SPECIAL.items():
        rel = key.replace('.', '/')
        write(os.path.join(ASSETS, 'sounds', rel + '.ogg'), recipes[0]())
        entries[key] = {'sounds': [f'redstoneplus:{rel}'], 'subtitle': f'subtitles.redstoneplus.{key}'}
        en[f'subtitles.redstoneplus.{key}'], de[f'subtitles.redstoneplus.{key}'] = SPECIAL_SUBTITLES[key]
        count += 1
    with open(sounds_json, 'w', encoding='utf-8') as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
        f.write('\n')
    for lang, e in (('en_us', en), ('de_de', de)):
        path = os.path.join(ASSETS, 'lang', lang + '.json')
        with open(path, encoding='utf-8') as f:
            current = json.load(f)
        current.update(e)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(current, f, indent=2, ensure_ascii=False)
            f.write('\n')
    print(f'{count} sound files, {len(entries)} sound events')


if __name__ == '__main__':
    main()
