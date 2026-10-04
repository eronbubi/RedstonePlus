"""
The creatures of the Redstone Realm: body parts, cubes, materials and animation clips.

Coordinates are Minecraft model pixels: y grows DOWNWARD, the ground is y = 24, the creature looks towards -z.
Pivots and cube corners are written in absolute model space; the tools turn them into the relative values
Minecraft and Blender need. Rotations and keyframes are in degrees around the Minecraft x / y / z axes and are
added on top of the rest pose. Clips: idle (loops on age), walk (follows the legs), attack (melee swing),
ability (the creature's special move, started from the server).

This file seeds tools/blender/<name>.blend (see tools/blender/build_mobs.py). Once a .blend exists it is the
source of the model and its animations: edit it in Blender and run tools/blender/export_mobs.py.
"""
import math

MOBS = {}


# ------------------------------------------------------------------------------------------------ helpers
def cube(frm, size, mat, faces=None, inflate=0.0, mirror=False):
    return {'from': list(frm), 'size': list(size), 'mat': mat, 'faces': faces or {}, 'inflate': inflate, 'mirror': mirror}


def part(name, pivot, cubes=(), parent='root', rot=(0, 0, 0)):
    return {'name': name, 'parent': parent, 'pivot': list(pivot), 'rot': list(rot), 'cubes': list(cubes)}


def wave(length, amp, axis='x', phase=0.0, cycles=1, steps=8, offset=0.0):
    """Keyframes of a sine wave: [[t, x, y, z], ...]."""
    keys = []
    n = steps * cycles
    for i in range(n + 1):
        t = length * i / n
        v = offset + amp * math.sin(2 * math.pi * cycles * i / n + phase)
        vec = [0.0, 0.0, 0.0]
        vec['xyz'.index(axis)] = round(v, 2)
        keys.append([round(t, 3)] + vec)
    return keys


def waves(length, amps, phases=(0, 0, 0), cycles=1, steps=8):
    """Sine on several axes at once. amps = (ax, ay, az)."""
    keys = []
    n = steps * cycles
    for i in range(n + 1):
        t = length * i / n
        a = 2 * math.pi * cycles * i / n
        keys.append([round(t, 3)] + [round(amps[k] * math.sin(a + phases[k]), 2) for k in range(3)])
    return keys


def spin(length, degrees, axis='x', steps=8):
    return [[round(length * i / steps, 3)] + [round(degrees * i / steps, 2) if 'xyz'[k] == axis else 0.0 for k in range(3)]
            for i in range(steps + 1)]


def keys(*frames, axis='x'):
    """keys((t, v), ...) on one axis."""
    out = []
    for t, v in frames:
        vec = [0.0, 0.0, 0.0]
        vec['xyz'.index(axis)] = v
        out.append([t] + vec)
    return out


def keys3(*frames):
    return [[t, x, y, z] for t, (x, y, z) in frames]


def clip(length, loop, bones):
    return {'length': length, 'loop': loop, 'bones': bones}


def rot(k):
    return {'rotation': k}


def pos(k):
    return {'position': k}


def scl(k):
    return {'scale': k}


def merge(*tracks):
    out = {}
    for t in tracks:
        out.update(t)
    return out


def mob(name, parts, anims, look=('head',), shadow=0.6, scale=1.0, texture_width=128):
    MOBS[name] = {'parts': parts, 'animations': anims, 'look': list(look), 'shadow': shadow, 'scale': scale,
                  'texture_width': texture_width}


def spider_legs(prefix_parent, y, zs, length, mat, x0=4):
    """Eight splayed legs; returns parts. Right legs point -x, left legs +x."""
    spread = [-32, -10, 10, 32]
    parts = []
    for i, z in enumerate(zs):
        parts.append(part(f'leg_r{i}', (-x0, y, z), [cube((-x0 - length, y - 1, z - 1), (length, 2, 2), mat)],
                          parent=prefix_parent, rot=(0, spread[i], -40)))
        parts.append(part(f'leg_l{i}', (x0, y, z), [cube((x0, y - 1, z - 1), (length, 2, 2), mat)],
                          parent=prefix_parent, rot=(0, -spread[i], 40)))
    return parts


def spider_walk(length, amp_y=22, amp_z=14):
    bones = {}
    for i in range(4):
        ph = (0 if i % 2 == 0 else math.pi)
        bones[f'leg_r{i}'] = rot(waves(length, (0, amp_y, amp_z), (0, ph, ph + math.pi / 2)))
        bones[f'leg_l{i}'] = rot(waves(length, (0, -amp_y, -amp_z), (0, ph + math.pi, ph + math.pi * 1.5)))
    return bones


# ================================================================================================ constructs
# ---- Karst Colossus: walking stone engine with piston limbs
mob('karst_colossus', [
    part('body', (0, 4, 0), [
        cube((-11, -18, -7), (22, 22, 14), 'karst', {'front': 'lichen'}),
        cube((-7, -14, -9), (14, 10, 2), 'rust', {'front': 'core'}),
        cube((-9, -16, 7), (6, 14, 5), 'iron', {'back': 'rivets'}),
        cube((3, -16, 7), (6, 14, 5), 'iron', {'back': 'rivets'}),
        cube((-6, -21, 3), (12, 3, 3), 'cable'),
    ]),
    part('head', (0, -18, -2), [
        cube((-5, -28, -8), (10, 10, 10), 'dark_stone', {'front': 'eyes3'}),
        cube((-6, -29, -9), (12, 3, 4), 'karst'),
    ], parent='body'),
    part('arm_r', (-13, -14, 0), [
        cube((-17, -17, -5), (8, 8, 10), 'rust', {'top': 'rivets'}),
        cube((-16, -10, -4), (6, 14, 8), 'karst'),
        cube((-15, 4, -2), (4, 6, 4), 'iron'),
        cube((-17, 9, -5), (8, 8, 10), 'dark_stone', {'front': 'rivets'}),
    ], parent='body'),
    part('arm_l', (13, -14, 0), [
        cube((9, -17, -5), (8, 8, 10), 'rust', {'top': 'rivets'}),
        cube((10, -10, -4), (6, 14, 8), 'karst'),
        cube((11, 4, -2), (4, 6, 4), 'iron'),
        cube((9, 9, -5), (8, 8, 10), 'dark_stone', {'front': 'rivets'}),
    ], parent='body'),
    part('leg_r', (-6, 4, 0), [
        cube((-10, 4, -4), (8, 10, 8), 'iron'),
        cube((-9, 14, -3), (6, 6, 6), 'dark_iron'),
        cube((-11, 20, -6), (10, 4, 11), 'karst'),
    ]),
    part('leg_l', (6, 4, 0), [
        cube((2, 4, -4), (8, 10, 8), 'iron'),
        cube((3, 14, -3), (6, 6, 6), 'dark_iron'),
        cube((1, 20, -6), (10, 4, 11), 'karst'),
    ]),
    part('cables', (0, -16, 11), [cube((-4, -16, 11), (2, 16, 2), 'cable'), cube((2, -14, 11), (2, 14, 2), 'cable')], parent='body'),
], {
    'walk': clip(1.2, True, merge(
        {'leg_r': rot(wave(1.2, 28)), 'leg_l': rot(wave(1.2, 28, phase=math.pi)),
         'arm_r': rot(wave(1.2, 18, phase=math.pi)), 'arm_l': rot(wave(1.2, 18)),
         'body': merge(rot(wave(1.2, 3, 'z')), pos(wave(1.2, 1.0, 'y', cycles=2, offset=0.5))),
         'cables': rot(wave(1.2, 10, phase=1.0))})),
    'idle': clip(3.0, True, {
        'body': rot(wave(3.0, 1.5)), 'head': rot(wave(3.0, 8, 'y')), 'cables': rot(waves(3.0, (6, 0, 5), (0, 0, 1))),
        'arm_r': rot(wave(3.0, 3, 'z')), 'arm_l': rot(wave(3.0, -3, 'z'))}),
    'attack': clip(0.6, False, {
        'arm_r': rot(keys((0, 0), (0.2, -110), (0.35, -20), (0.6, 0))),
        'body': rot(keys((0, 0), (0.2, -15), (0.6, 0), axis='y'))}),
    'ability': clip(1.0, False, {
        'arm_r': rot(keys((0, 0), (0.35, -165), (0.55, -15), (1.0, 0))),
        'arm_l': rot(keys((0, 0), (0.35, -165), (0.55, -15), (1.0, 0))),
        'body': merge(rot(keys((0, 0), (0.35, -12), (0.55, 16), (1.0, 0))), pos(keys3((0, (0, 0, 0)), (0.35, (0, -2, 0)), (0.55, (0, 3, 0)), (1.0, (0, 0, 0))))),
        'head': rot(keys((0, 0), (0.35, -15), (0.55, 20), (1.0, 0)))}),
}, shadow=1.2)

# ---- Switchback Crawler: rail centipede
_crawler = [
    part('body', (0, 15, -8)),
    part('head', (0, 15, -8), [
        cube((-6, 9, -18), (12, 10, 10), 'rust', {'front': 'eyes4'}),
        cube((-2, 8, -16), (4, 1, 4), 'ember'),
    ], parent='body'),
    part('mandible_l', (3, 17, -18), [cube((2, 16, -23), (3, 2, 5), 'iron')], parent='head'),
    part('mandible_r', (-3, 17, -18), [cube((-5, 16, -23), (3, 2, 5), 'iron')], parent='head'),
]
_prev = 'body'
_segs = [(-8, 12, 10, 9), (1, 12, 10, 9), (10, 10, 9, 9), (19, 8, 8, 8)]
for i, (z, w, h, d) in enumerate(_segs):
    name = f'seg{i}'
    _crawler.append(part(name, (0, 15, z), [
        cube((-w // 2, 19 - h, z), (w, h, d), 'rust', {'top': 'rivets'}),
        cube((-w // 2 + 1, 18 - h, z + 1), (w - 2, 1, d - 2), 'copper_ox'),
    ], parent=_prev))
    half = w // 2
    _crawler.append(part(f'{name}_leg_l', (half, 17, z + d // 2), [
        cube((half, 16, z + d // 2 - 1), (5, 2, 2), 'iron'), cube((half + 4, 16, z + d // 2 - 1), (2, 8, 2), 'dark_iron')], parent=name))
    _crawler.append(part(f'{name}_leg_r', (-half, 17, z + d // 2), [
        cube((-half - 5, 16, z + d // 2 - 1), (5, 2, 2), 'iron'), cube((-half - 6, 16, z + d // 2 - 1), (2, 8, 2), 'dark_iron')], parent=name))
    _prev = name
_crawler.append(part('tail', (0, 15, 27), [cube((-2, 12, 27), (4, 4, 6), 'rust'), cube((-1, 13, 33), (2, 2, 3), 'iron')], parent=_prev))
_walk = {}
for i in range(4):
    ph = i * math.pi / 2
    _walk[f'seg{i}_leg_l'] = rot(waves(0.8, (0, 25, 12), (0, ph, ph + 1.5)))
    _walk[f'seg{i}_leg_r'] = rot(waves(0.8, (0, 25, -12), (0, ph + math.pi, ph + math.pi + 1.5)))
    _walk[f'seg{i}'] = rot(wave(0.8, 6, 'y', phase=i * 0.9))
_walk['head'] = rot(wave(0.8, 5, 'y', phase=-0.9))
_walk['tail'] = rot(wave(0.8, 12, 'y', phase=4 * 0.9))
_dash = {f'seg{i}_leg_{s}': rot(waves(0.8, (0, 35, 15), (0, i + (0 if s == 'l' else math.pi), i), cycles=4)) for i in range(4) for s in 'lr'}
_dash['head'] = rot(keys((0, 0), (0.1, -12), (0.7, -12), (0.8, 0)))
_dash['body'] = pos(keys3((0, (0, 0, 0)), (0.1, (0, 1.5, 0)), (0.7, (0, 1.5, 0)), (0.8, (0, 0, 0))))
_dash['mandible_l'] = rot(keys((0, 0), (0.1, -30), (0.7, -30), (0.8, 0), axis='y'))
_dash['mandible_r'] = rot(keys((0, 0), (0.1, 30), (0.7, 30), (0.8, 0), axis='y'))
mob('switchback_crawler', _crawler, {
    'walk': clip(0.8, True, _walk),
    'idle': clip(2.0, True, {
        'mandible_l': rot(wave(2.0, -12, 'y', cycles=4)), 'mandible_r': rot(wave(2.0, 12, 'y', cycles=4)),
        'seg0': rot(wave(2.0, 3, 'y')), 'seg2': rot(wave(2.0, -3, 'y')), 'tail': rot(wave(2.0, 10, 'y', phase=1))}),
    'attack': clip(0.5, False, {
        'mandible_l': rot(keys((0, 0), (0.15, -40), (0.3, 12), (0.5, 0), axis='y')),
        'mandible_r': rot(keys((0, 0), (0.15, 40), (0.3, -12), (0.5, 0), axis='y')),
        'head': rot(keys((0, 0), (0.15, -18), (0.3, 10), (0.5, 0)))}),
    'ability': clip(0.8, False, _dash),
}, look=('head',), shadow=0.8)

# ---- Bell Stalker: blind stilt-walker with a bell for a head
_tendrils = []
for i, (x, z) in enumerate([(-6, -20), (5, -20), (-6, -9), (5, -9), (0, -21)]):
    _tendrils.append(part(f'tendril{i}', (x + 0.5, -27, z + 0.5), [
        cube((x, -27, z), (1, 12, 1), 'cable'), cube((x - 0.5, -15, z - 0.5), (2, 2, 2), 'teal_glow')], parent='bell'))
mob('bell_stalker', [
    part('hip', (0, -22, 0), [cube((-5, -26, -5), (10, 6, 10), 'brass', {'front': 'rivets'}), cube((-6, -24, -1), (12, 2, 2), 'iron')]),
    part('leg_l', (4, -22, 0), [cube((3, -22, -1), (3, 24, 3), 'wood'), cube((2.5, 0, -1.5), (4, 3, 4), 'brass')], parent='hip', rot=(0, 0, -6)),
    part('leg_l_low', (4.5, 2, 0.5), [cube((3, 2, -1), (3, 20, 3), 'wood'), cube((2, 22, -2), (5, 2, 5), 'brass')], parent='leg_l', rot=(0, 0, 6)),
    part('leg_r', (-4, -22, 0), [cube((-6, -22, -1), (3, 24, 3), 'wood'), cube((-6.5, 0, -1.5), (4, 3, 4), 'brass')], parent='hip', rot=(0, 0, 6)),
    part('leg_r_low', (-4.5, 2, 0.5), [cube((-6, 2, -1), (3, 20, 3), 'wood'), cube((-7, 22, -2), (5, 2, 5), 'brass')], parent='leg_r', rot=(0, 0, -6)),
    part('leg_b', (0, -22, 4), [cube((-1, -22, 3), (3, 24, 3), 'wood'), cube((-1.5, 0, 2.5), (4, 3, 4), 'brass')], parent='hip', rot=(-6, 0, 0)),
    part('leg_b_low', (0.5, 2, 4.5), [cube((-1, 2, 3), (3, 20, 3), 'wood'), cube((-2, 22, 2), (5, 2, 5), 'brass')], parent='leg_b', rot=(6, 0, 0)),
    part('neck', (0, -26, 0), [
        cube((-1.5, -44, -1.5), (3, 18, 3), 'brass'),
        cube((-2, -34, -2), (4, 3, 4), 'iron'),
        cube((-1.5, -46, -14), (3, 3, 15), 'brass', {'top': 'rivets'}),
        cube((-2, -47, -1.5), (4, 4, 4), 'iron'),
    ], parent='hip'),
    part('bell', (0, -43, -12), [
        cube((-3, -43, -15), (6, 3, 6), 'iron'),
        cube((-6, -40, -18), (12, 11, 12), 'bell', {'front': 'eye1', 'left': 'rivets', 'right': 'rivets'}),
        cube((-8, -29, -20), (16, 2, 16), 'bell'),
        cube((-2, -28, -14), (4, 3, 4), 'teal_glow'),
    ], parent='neck'),
] + _tendrils, {
    'walk': clip(1.6, True, merge(
        {'leg_l': rot(wave(1.6, 18)), 'leg_r': rot(wave(1.6, 18, phase=2.1)), 'leg_b': rot(wave(1.6, 18, phase=4.2)),
         'leg_l_low': rot(wave(1.6, 14, phase=1.5, offset=10)), 'leg_r_low': rot(wave(1.6, 14, phase=3.6, offset=10)),
         'leg_b_low': rot(wave(1.6, 14, phase=5.7, offset=10)),
         'hip': pos(wave(1.6, 1.2, 'y', cycles=3)), 'neck': rot(wave(1.6, 4)), 'bell': rot(wave(1.6, 12, 'z', phase=1.0))},
        {f'tendril{i}': rot(waves(1.6, (16, 0, 10), (i, 0, i + 1))) for i in range(5)})),
    'idle': clip(3.0, True, merge(
        {'bell': rot(wave(3.0, 10, 'z')), 'neck': rot(wave(3.0, 14, 'y'))},
        {f'tendril{i}': rot(waves(3.0, (12, 0, 8), (i * 1.3, 0, i))) for i in range(5)})),
    'attack': clip(0.7, False, {
        'neck': rot(keys((0, 0), (0.25, 25), (0.45, -10), (0.7, 0))),
        'bell': rot(keys((0, 0), (0.25, 30), (0.45, -15), (0.7, 0)))}),
    'ability': clip(1.2, False, merge(
        {'bell': merge(rot(keys((0, 0), (0.15, 35), (0.35, -35), (0.55, 25), (0.75, -15), (1.0, 5), (1.2, 0), axis='z')),
                       scl(keys3((0, (1, 1, 1)), (0.15, (1.2, 1.2, 1.2)), (0.4, (1, 1, 1)), (1.2, (1, 1, 1))))),
         'neck': rot(keys((0, 0), (0.15, -10), (0.6, 5), (1.2, 0)))},
        {f'tendril{i}': rot(keys((0, 0), (0.15, -50 if i % 2 == 0 else 50), (0.6, 10), (1.2, 0))) for i in range(5)})),
}, look=('neck',), shadow=0.7, scale=0.9)

# ---- Sluice Chainjaw: chained copper gator
mob('sluice_chainjaw', [
    part('body', (0, 14, 0), [
        cube((-7, 10, -10), (14, 9, 22), 'copper_ox', {'top': 'rivets'}),
        cube((-2, 8, -8), (4, 2, 18), 'copper'),
        cube((-7, 9, -4), (14, 11, 2), 'chain', inflate=0.3),
        cube((-7, 9, 5), (14, 11, 2), 'chain', inflate=0.3),
    ]),
    part('head', (0, 13, -10), [
        cube((-5, 9, -24), (10, 5, 14), 'copper_ox', {'top': 'eyes2', 'front': 'teeth'}),
        cube((-4, 8, -23), (8, 1, 5), 'iron'),
    ], parent='body'),
    part('jaw', (0, 14, -11), [cube((-4.5, 14, -23), (9, 3, 12), 'copper', {'top': 'teeth'})], parent='head'),
    part('tail1', (0, 13, 12), [cube((-4, 10, 12), (8, 6, 10), 'copper_ox'), cube((-4, 9.5, 15), (8, 7, 2), 'chain', inflate=0.2)], parent='body'),
    part('tail2', (0, 13, 22), [cube((-2.5, 11, 22), (5, 4, 10), 'copper_ox'), cube((-0.5, 8, 24), (1, 3, 7), 'iron')], parent='tail1'),
    part('leg_fl', (-6, 17, -7), [cube((-9, 16, -9), (4, 7, 4), 'copper_ox'), cube((-10, 22, -11), (5, 2, 5), 'iron')], parent='body'),
    part('leg_fr', (6, 17, -7), [cube((5, 16, -9), (4, 7, 4), 'copper_ox'), cube((5, 22, -11), (5, 2, 5), 'iron')], parent='body'),
    part('leg_bl', (-6, 17, 7), [cube((-9, 16, 5), (4, 7, 4), 'copper_ox'), cube((-10, 22, 3), (5, 2, 5), 'iron')], parent='body'),
    part('leg_br', (6, 17, 7), [cube((5, 16, 5), (4, 7, 4), 'copper_ox'), cube((5, 22, 3), (5, 2, 5), 'iron')], parent='body'),
], {
    'walk': clip(1.0, True, {
        'leg_fl': rot(wave(1.0, 30)), 'leg_br': rot(wave(1.0, 30)), 'leg_fr': rot(wave(1.0, 30, phase=math.pi)),
        'leg_bl': rot(wave(1.0, 30, phase=math.pi)), 'body': rot(wave(1.0, 4, 'y')),
        'tail1': rot(wave(1.0, 15, 'y', phase=1.5)), 'tail2': rot(wave(1.0, 20, 'y', phase=3.0)), 'head': rot(wave(1.0, 5, 'y', phase=-1))}),
    'idle': clip(3.0, True, {
        'jaw': rot(keys((0, 0), (1.2, 0), (1.5, 14), (1.8, 0), (3.0, 0))),
        'tail1': rot(wave(3.0, 10, 'y')), 'tail2': rot(wave(3.0, 14, 'y', phase=1)), 'body': pos(wave(3.0, 0.4, 'y'))}),
    'attack': clip(0.5, False, {
        'jaw': rot(keys((0, 0), (0.15, 45), (0.3, 0), (0.5, 0))),
        'head': rot(keys((0, 0), (0.15, -20), (0.3, 8), (0.5, 0)))}),
    'ability': clip(0.8, False, {
        'jaw': rot(keys((0, 0), (0.1, 50), (0.25, 0), (0.8, 0))),
        'head': rot(keys((0, 0), (0.2, -25), (0.4, 25), (0.6, -10), (0.8, 0), axis='y')),
        'body': rot(keys((0, 0), (0.2, 8), (0.4, -8), (0.8, 0), axis='y')),
        'tail1': rot(keys((0, 0), (0.2, -20), (0.4, 20), (0.8, 0), axis='y'))}),
}, shadow=0.9)

# ---- Kiln Brute: walking furnace
mob('kiln_brute', [
    part('body', (0, 6, 0), [
        cube((-11, -20, -8), (22, 26, 16), 'furnace', {'front': 'grill'}),
        cube((-7, -6, -9), (14, 8, 1), 'dark_iron', {'front': 'grill_small'}),
        cube((-11, -13, -8), (22, 2, 16), 'chain', inflate=0.4),
    ]),
    part('chimney', (4, -20, 3), [cube((2, -28, 1), (5, 8, 5), 'dark_iron', {'top': 'ember_top'})], parent='body'),
    part('arm_r', (-13, -14, 0), [
        cube((-19, -18, -6), (8, 8, 12), 'rust', {'top': 'rivets'}),
        cube((-18, -10, -5), (7, 18, 10), 'furnace', {'front': 'grill_small'}),
        cube((-19, 8, -6), (9, 7, 12), 'dark_iron', {'front': 'rivets'}),
    ], parent='body'),
    part('arm_l', (13, -14, 0), [
        cube((11, -18, -6), (8, 8, 12), 'rust', {'top': 'rivets'}),
        cube((11, -10, -5), (7, 18, 10), 'furnace', {'front': 'grill_small'}),
        cube((10, 8, -6), (9, 7, 12), 'dark_iron', {'front': 'rivets'}),
    ], parent='body'),
    part('leg_r', (-6, 6, 0), [cube((-10, 6, -5), (8, 14, 10), 'furnace'), cube((-11, 20, -6), (10, 4, 12), 'dark_iron')]),
    part('leg_l', (6, 6, 0), [cube((2, 6, -5), (8, 14, 10), 'furnace'), cube((1, 20, -6), (10, 4, 12), 'dark_iron')]),
], {
    'walk': clip(1.4, True, {
        'leg_r': rot(wave(1.4, 25)), 'leg_l': rot(wave(1.4, 25, phase=math.pi)),
        'arm_r': rot(wave(1.4, 20, phase=math.pi)), 'arm_l': rot(wave(1.4, 20)),
        'body': merge(rot(wave(1.4, 4, 'z')), pos(wave(1.4, 1.0, 'y', cycles=2, offset=0.5)))}),
    'idle': clip(3.0, True, {
        'body': rot(wave(3.0, 2)), 'chimney': rot(wave(3.0, 3, 'z')),
        'arm_r': rot(wave(3.0, 3, 'z')), 'arm_l': rot(wave(3.0, -3, 'z'))}),
    'attack': clip(0.7, False, {
        'arm_r': rot(keys((0, 0), (0.25, -100), (0.4, 10), (0.7, 0))),
        'body': rot(keys((0, 0), (0.25, -12), (0.7, 0), axis='y'))}),
    'ability': clip(1.0, False, {
        'body': rot(keys((0, 0), (0.2, -12), (0.5, -12), (0.7, 6), (1.0, 0))),
        'arm_r': rot(keys((0, 0), (0.2, 30), (0.7, 30), (1.0, 0), axis='z')),
        'arm_l': rot(keys((0, 0), (0.2, -30), (0.7, -30), (1.0, 0), axis='z')),
        'chimney': scl(keys3((0, (1, 1, 1)), (0.3, (1.2, 1.4, 1.2)), (0.5, (0.9, 0.8, 0.9)), (1.0, (1, 1, 1))))}),
}, shadow=1.1)

# ---- Spool Weaver: spider with a wire spool on its back
mob('spool_weaver', [
    part('body', (0, 15, 0), [cube((-4, 11, -4), (8, 7, 8), 'rust', {'top': 'rivets'})]),
    part('head', (0, 15, -4), [cube((-4, 10, -12), (8, 7, 8), 'dark_iron', {'front': 'eyes6'})], parent='body'),
    part('spool', (0, 12, 6), [cube((-7, 5, 2), (1, 10, 10), 'copper_ox'), cube((6, 5, 2), (1, 10, 10), 'copper_ox')], parent='body'),
    part('drum', (0, 10, 7), [cube((-6, 6, 3), (12, 8, 8), 'wire')], parent='spool'),
] + spider_legs('body', 15, (-3, -1, 1, 3), 15, 'iron'), {
    'walk': clip(0.6, True, merge(spider_walk(0.6), {'drum': rot(spin(0.6, 180)), 'body': pos(wave(0.6, 0.5, 'y', cycles=2))})),
    'idle': clip(4.0, True, {'drum': rot(spin(4.0, 360)), 'head': rot(wave(4.0, 6, 'y')), 'leg_r0': rot(wave(4.0, 6, 'z', cycles=2)),
                             'leg_l0': rot(wave(4.0, -6, 'z', cycles=2))}),
    'attack': clip(0.5, False, {'head': rot(keys((0, 0), (0.15, -20), (0.3, 10), (0.5, 0))),
                                'leg_r0': rot(keys((0, 0), (0.15, 30), (0.5, 0), axis='z')),
                                'leg_l0': rot(keys((0, 0), (0.15, -30), (0.5, 0), axis='z'))}),
    'ability': clip(0.8, False, {
        'body': rot(keys((0, 0), (0.2, -25), (0.5, -25), (0.8, 0))),
        'drum': rot(spin(0.8, 720)),
        'leg_r0': rot(keys((0, 0), (0.2, 45), (0.5, 45), (0.8, 0), axis='z')),
        'leg_l0': rot(keys((0, 0), (0.2, -45), (0.5, -45), (0.8, 0), axis='z'))}),
}, shadow=0.9)

# ================================================================================================ machine-bound
# ---- The Leaking Cell (Uranium Zombie)
mob('leaking_cell', [
    part('body', (0, 12, 0), [
        cube((-6, -4, -4), (12, 16, 8), 'zombie', {'front': 'veins'}),
        cube((-5, -2, -7), (10, 11, 3), 'iron', {'front': 'cage_glow'}),
        cube((-7, -6, 3), (14, 14, 4), 'rust', {'back': 'bars'}),
        cube((-6, -8, 2), (2, 2, 2), 'redstone'), cube((4, -8, 2), (2, 2, 2), 'redstone'),
        cube((-3, 9, -6), (1, 4, 1), 'uranium'), cube((2, 9, -6), (1, 3, 1), 'uranium'),
    ]),
    part('head', (0, -4, -2), [
        cube((-4, -12, -7), (8, 8, 8), 'zombie', {'front': 'face_zombie'}),
        cube((-4, -12, -7), (8, 8, 8), 'cage', inflate=0.6),
    ], parent='body'),
    part('arm_r', (-8, -2, 0), [cube((-11, -3, -3), (5, 16, 6), 'zombie'), cube((-11, 5, -3), (5, 4, 6), 'rust', inflate=0.3)],
         parent='body', rot=(-75, 0, 0)),
    part('arm_l', (8, -2, 0), [cube((6, -3, -3), (5, 16, 6), 'zombie'), cube((6, 5, -3), (5, 4, 6), 'rust', inflate=0.3)],
         parent='body', rot=(-75, 0, 0)),
    part('leg_r', (-3, 12, 0), [cube((-6, 12, -3), (5, 12, 6), 'zombie'), cube((-6, 19, -3), (5, 2, 6), 'iron', inflate=0.3)]),
    part('leg_l', (3, 12, 0), [cube((1, 12, -3), (5, 12, 6), 'zombie'), cube((1, 19, -3), (5, 2, 6), 'iron', inflate=0.3)]),
], {
    'walk': clip(1.2, True, {'leg_r': rot(wave(1.2, 30)), 'leg_l': rot(wave(1.2, 30, phase=math.pi)),
                             'arm_r': rot(wave(1.2, 8)), 'arm_l': rot(wave(1.2, 8, phase=math.pi)),
                             'body': rot(wave(1.2, 3, 'z'))}),
    'idle': clip(2.5, True, {'body': rot(waves(2.5, (3, 0, 2), (0, 0, 1))), 'head': rot(wave(2.5, 8, 'z', phase=1.5))}),
    'attack': clip(0.6, False, {'arm_r': rot(keys((0, 0), (0.2, -30), (0.4, 40), (0.6, 0))),
                                'arm_l': rot(keys((0, 0), (0.25, -30), (0.45, 40), (0.6, 0)))}),
    'ability': clip(0.8, False, {'body': merge(rot(keys((0, 0), (0.25, 25), (0.6, 25), (0.8, 0))),
                                               scl(keys3((0, (1, 1, 1)), (0.25, (1.1, 0.95, 1.1)), (0.8, (1, 1, 1))))),
                                 'head': rot(keys((0, 0), (0.25, 20), (0.8, 0)))}),
}, shadow=0.6)

# ---- Detonator Husk (Redstone Creeper)
mob('detonator_husk', [
    part('body', (0, 16, 0), [
        cube((-5, -6, -4), (10, 22, 8), 'flesh', {'front': 'veins'}),
        cube((-2, 3, -5), (4, 6, 1), 'redstone'),
        cube((-5, 0, -4), (10, 2, 8), 'rust', inflate=0.3), cube((-5, 11, -4), (10, 2, 8), 'rust', inflate=0.3),
        cube((5, -4, -1), (1, 18, 2), 'cable'),
    ]),
    part('head', (0, -6, 0), [
        cube((-5, -16, -5), (10, 10, 10), 'flesh', {'front': 'face_creeper'}),
        cube((-5, -16, -5), (10, 10, 10), 'cage', inflate=0.7),
        cube((-6, -17, -6), (2, 2, 2), 'redstone'), cube((4, -17, -6), (2, 2, 2), 'redstone'),
        cube((-6, -17, 4), (2, 2, 2), 'redstone'), cube((4, -17, 4), (2, 2, 2), 'redstone'),
    ], parent='body'),
    part('leg_fl', (-3, 16, -4), [cube((-6, 16, -8), (6, 8, 6), 'flesh'), cube((-6, 22, -8), (6, 2, 6), 'iron', inflate=0.3)], parent='body'),
    part('leg_fr', (3, 16, -4), [cube((0, 16, -8), (6, 8, 6), 'flesh'), cube((0, 22, -8), (6, 2, 6), 'iron', inflate=0.3)], parent='body'),
    part('leg_bl', (-3, 16, 4), [cube((-6, 16, 2), (6, 8, 6), 'flesh'), cube((-6, 22, 2), (6, 2, 6), 'iron', inflate=0.3)], parent='body'),
    part('leg_br', (3, 16, 4), [cube((0, 16, 2), (6, 8, 6), 'flesh'), cube((0, 22, 2), (6, 2, 6), 'iron', inflate=0.3)], parent='body'),
], {
    'walk': clip(0.8, True, {'leg_fl': rot(wave(0.8, 25)), 'leg_br': rot(wave(0.8, 25)),
                             'leg_fr': rot(wave(0.8, 25, phase=math.pi)), 'leg_bl': rot(wave(0.8, 25, phase=math.pi)),
                             'body': rot(wave(0.8, 3, 'z', cycles=2))}),
    'idle': clip(3.0, True, {'head': rot(wave(3.0, 6, 'y')), 'body': rot(wave(3.0, 1.5))}),
    'ability': clip(1.0, False, {
        'body': merge(rot(wave(1.0, 4, 'z', cycles=5)), scl(keys3((0, (1, 1, 1)), (1.0, (1.05, 1.0, 1.05))))),
        'head': merge(rot(wave(1.0, 6, 'x', cycles=6)), pos(keys3((0, (0, 0, 0)), (1.0, (0, -2, 0)))))}),
}, shadow=0.5, scale=0.8)

# ---- Tripwire Brood (Crystal Spider)
mob('tripwire_brood', [
    part('body', (0, 15, 0), [cube((-4, 11, -3), (8, 7, 7), 'iron', {'top': 'rivets'})]),
    part('head', (0, 15, -3), [cube((-4, 10, -10), (8, 8, 7), 'dark_iron', {'front': 'eyes6'})], parent='body'),
    part('fang_l', (2, 17, -10), [cube((1, 17, -12), (2, 4, 2), 'bone')], parent='head'),
    part('fang_r', (-2, 17, -10), [cube((-3, 17, -12), (2, 4, 2), 'bone')], parent='head'),
    part('strand', (0, 18, -12), [cube((-0.5, 18, -12.5), (1, 7, 1), 'wire')], parent='head'),
    part('sack', (0, 13, 4), [
        cube((-8, 4, 4), (16, 12, 14), 'egg', {'top': 'bumps', 'back': 'bumps'}),
        cube((-2, 1, 8), (3, 4, 3), 'crystal'), cube((3, 2, 11), (2, 3, 2), 'crystal'), cube((-5, 2, 13), (2, 3, 2), 'crystal'),
        cube((-8, 4, 4), (16, 12, 14), 'cage', inflate=0.6),
    ], parent='body'),
] + spider_legs('body', 15, (-2, 0, 2, 4), 17, 'rust'), {
    'walk': clip(0.6, True, merge(spider_walk(0.6), {'sack': rot(wave(0.6, 3, 'x', cycles=2))})),
    'idle': clip(3.0, True, {'sack': scl(keys3((0, (1, 1, 1)), (1.5, (1.05, 1.06, 1.04)), (3.0, (1, 1, 1)))),
                             'fang_l': rot(wave(3.0, -8, 'y', cycles=3)), 'fang_r': rot(wave(3.0, 8, 'y', cycles=3)),
                             'strand': rot(wave(3.0, 15, 'z'))}),
    'attack': clip(0.5, False, {'head': rot(keys((0, 0), (0.15, -20), (0.3, 10), (0.5, 0))),
                                'fang_l': rot(keys((0, 0), (0.15, -30), (0.3, 20), (0.5, 0), axis='y')),
                                'fang_r': rot(keys((0, 0), (0.15, 30), (0.3, -20), (0.5, 0), axis='y'))}),
    'ability': clip(0.6, False, {'body': rot(keys((0, 0), (0.15, -15), (0.3, 8), (0.6, 0))),
                                 'fang_l': rot(keys((0, 0), (0.1, -35), (0.25, 25), (0.6, 0), axis='y')),
                                 'fang_r': rot(keys((0, 0), (0.1, 35), (0.25, -25), (0.6, 0), axis='y'))}),
}, shadow=0.9)

# ---- Kilnbound (Magma Skeleton)
mob('kilnbound', [
    part('body', (0, 5, 0), [
        cube((-4, -15, -2), (8, 12, 4), 'bone', {'front': 'ribs'}),
        cube((-2, -12, -1), (4, 5, 2), 'ember'),
        cube((-5, -17, -3), (10, 2, 6), 'rust'),
        cube((-6, -17, -1), (1, 16, 2), 'wood'), cube((5, -17, -1), (1, 16, 2), 'wood'),
        cube((-1, -3, -1), (2, 5, 2), 'bone'),
        cube((-4, 2, -2), (8, 3, 4), 'bone'),
    ]),
    part('head', (0, -17, 0), [
        cube((-4, -26, -4), (8, 9, 8), 'furnace', {'front': 'grill'}),
        cube((-1, -28, -1), (2, 2, 2), 'ember'),
    ], parent='body'),
    part('arm_r', (-6, -15, 0), [cube((-7, -15, -1), (2, 20, 2), 'bone'), cube((-8, -9, -1), (1, 12, 2), 'wood')], parent='body',
         rot=(-10, 0, 0)),
    part('arm_l', (6, -15, 0), [cube((5, -15, -1), (2, 20, 2), 'bone'), cube((7, -9, -1), (1, 12, 2), 'wood')], parent='body',
         rot=(-10, 0, 0)),
    part('leg_r', (-2, 5, 0), [cube((-3, 5, -1), (2, 19, 2), 'bone'), cube((-4, 8, -1), (1, 16, 2), 'wood'), cube((-4, 22, -2), (4, 2, 4), 'rust')]),
    part('leg_l', (2, 5, 0), [cube((1, 5, -1), (2, 19, 2), 'bone'), cube((3, 8, -1), (1, 16, 2), 'wood'), cube((0, 22, -2), (4, 2, 4), 'rust')]),
], {
    'walk': clip(1.1, True, {'leg_r': rot(wave(1.1, 30)), 'leg_l': rot(wave(1.1, 30, phase=math.pi)),
                             'arm_r': rot(wave(1.1, 25, phase=math.pi)), 'arm_l': rot(wave(1.1, 25)),
                             'body': pos(wave(1.1, 0.6, 'y', cycles=2))}),
    'idle': clip(2.0, True, {'head': merge(rot(wave(2.0, 5, 'z')), pos(wave(2.0, 0.4, 'y', cycles=2))),
                             'arm_r': rot(wave(2.0, 4, 'z')), 'arm_l': rot(wave(2.0, -4, 'z'))}),
    'attack': clip(0.6, False, {'arm_r': rot(keys((0, 0), (0.2, -90), (0.4, 10), (0.6, 0)))}),
    'ability': clip(0.7, False, {'arm_r': rot(keys((0, 0), (0.1, -90), (0.45, -90), (0.7, 0))),
                                 'arm_l': rot(keys((0, 0), (0.1, -70), (0.45, -70), (0.7, 0))),
                                 'head': rot(keys((0, 0), (0.1, -8), (0.45, -8), (0.7, 0)))}),
}, shadow=0.5, scale=0.85)

# ---- Living Capacitor (Ruby Slime)
mob('living_capacitor', [
    part('frame', (0, 24, 0), [
        cube((-5, 15, -5), (1, 9, 1), 'iron'), cube((4, 15, -5), (1, 9, 1), 'iron'),
        cube((-5, 15, 4), (1, 9, 1), 'iron'), cube((4, 15, 4), (1, 9, 1), 'iron'),
        cube((-5, 15, -5), (10, 1, 1), 'rust'), cube((-5, 15, 4), (10, 1, 1), 'rust'),
        cube((-5, 23, -5), (10, 1, 10), 'dark_iron'),
    ]),
    part('slime', (0, 23, 0), [cube((-4, 15, -4), (8, 8, 8), 'slime', {'front': 'bubbles', 'top': 'bubbles'}),
                               cube((-2, 17, -2), (4, 4, 4), 'ruby_glow')], parent='frame'),
    part('electrode_l', (-2, 15, 0), [cube((-3, 11, -1), (2, 4, 2), 'copper'), cube((-3, 10, -1), (2, 1, 2), 'redstone')], parent='frame'),
    part('electrode_r', (2, 15, 0), [cube((1, 11, -1), (2, 4, 2), 'copper'), cube((1, 10, -1), (2, 1, 2), 'redstone')], parent='frame'),
], {
    'idle': clip(1.5, True, {'slime': scl(keys3((0, (1, 1, 1)), (0.75, (1.05, 0.92, 1.05)), (1.5, (1, 1, 1)))),
                             'electrode_l': rot(wave(1.5, 6, 'z')), 'electrode_r': rot(wave(1.5, -6, 'z'))}),
    'walk': clip(0.8, True, {'slime': scl(keys3((0, (1, 1, 1)), (0.2, (1.15, 0.8, 1.15)), (0.4, (0.9, 1.15, 0.9)), (0.8, (1, 1, 1)))),
                             'frame': pos(keys3((0, (0, 0, 0)), (0.4, (0, -3, 0)), (0.8, (0, 0, 0))))}),
    'ability': clip(0.5, False, {'electrode_l': scl(keys3((0, (1, 1, 1)), (0.1, (1.4, 1.4, 1.4)), (0.5, (1, 1, 1)))),
                                 'electrode_r': scl(keys3((0, (1, 1, 1)), (0.1, (1.4, 1.4, 1.4)), (0.5, (1, 1, 1)))),
                                 'slime': scl(keys3((0, (1, 1, 1)), (0.1, (1.15, 1.15, 1.15)), (0.3, (0.95, 0.95, 0.95)), (0.5, (1, 1, 1)))),
                                 'frame': rot(wave(0.5, 3, 'z', cycles=4))}),
}, look=(), shadow=0.25)

# ---- Relay Strider (Void Enderman)
mob('relay_strider', [
    part('body', (0, -6, 0), [
        cube((-4, -18, -2), (8, 12, 4), 'ender', {'front': 'circuit'}),
        cube((-4, -16, -3), (3, 3, 1), 'iron'), cube((1, -13, -3), (3, 3, 1), 'iron'),
    ]),
    part('head', (0, -18, 0), [cube((-4, -26, -4), (8, 8, 8), 'dark_iron', {'front': 'eyes_purple'})], parent='body'),
    part('arm_r', (-5, -16, 0), [cube((-6, -16, -1), (2, 30, 2), 'ender'), cube((-7, -14, 1), (1, 28, 1), 'cable'),
                                 cube((-7, 14, -2), (3, 3, 3), 'purple_glow')], parent='body'),
    part('arm_l', (5, -16, 0), [cube((4, -16, -1), (2, 30, 2), 'ender'), cube((6, -14, 1), (1, 28, 1), 'cable'),
                                cube((4, 14, -2), (3, 3, 3), 'purple_glow')], parent='body'),
    part('leg_r', (-2, -6, 0), [cube((-3, -6, -1), (2, 30, 2), 'ender'), cube((-4, 6, -2), (3, 4, 3), 'iron')]),
    part('leg_l', (2, -6, 0), [cube((1, -6, -1), (2, 30, 2), 'ender'), cube((1, 6, -2), (3, 4, 3), 'iron')]),
    part('cables', (0, -8, 2), [cube((-3, -8, 2), (1, 26, 1), 'cable'), cube((2, -8, 2), (1, 24, 1), 'cable')], parent='body'),
], {
    'walk': clip(1.4, True, {'leg_r': rot(wave(1.4, 25)), 'leg_l': rot(wave(1.4, 25, phase=math.pi)),
                             'arm_r': rot(wave(1.4, 15, phase=math.pi)), 'arm_l': rot(wave(1.4, 15)),
                             'cables': rot(wave(1.4, 10, phase=1))}),
    'idle': clip(3.0, True, {'arm_r': rot(wave(3.0, 4, 'z')), 'arm_l': rot(wave(3.0, -4, 'z')),
                             'cables': rot(waves(3.0, (8, 0, 5), (0, 0, 1))), 'head': rot(wave(3.0, 10, 'y'))}),
    'attack': clip(0.6, False, {'arm_r': rot(keys((0, 0), (0.2, -80), (0.4, 0), (0.6, 0))),
                                'arm_l': rot(keys((0, 0), (0.25, -80), (0.45, 0), (0.6, 0)))}),
    'ability': clip(0.6, False, {'head': rot(keys((0, 0), (0.15, -20), (0.6, 0))),
                                 'arm_r': rot(keys((0, 0), (0.15, 60), (0.45, 60), (0.6, 0), axis='z')),
                                 'arm_l': rot(keys((0, 0), (0.15, -60), (0.45, -60), (0.6, 0), axis='z')),
                                 'body': scl(keys3((0, (1, 1, 1)), (0.1, (1.1, 0.95, 1.1)), (0.2, (0.95, 1.05, 0.95)), (0.6, (1, 1, 1))))}),
}, shadow=0.5, scale=0.95)

# ---- Bellows Hog (Ember Pig)
mob('bellows_hog', [
    part('body', (0, 13, 0), [
        cube((-6, 6, -9), (12, 10, 18), 'pig', {'top': 'veins'}),
        cube((-6, 6, -4), (12, 10, 2), 'rust', inflate=0.3), cube((-6, 6, 4), (12, 10, 2), 'rust', inflate=0.3),
    ]),
    part('bellows_l', (6, 11, 0), [cube((6, 7, -6), (3, 8, 12), 'bellows')], parent='body'),
    part('bellows_r', (-6, 11, 0), [cube((-9, 7, -6), (3, 8, 12), 'bellows')], parent='body'),
    part('pipe', (2, 6, 6), [cube((1, 0, 5), (2, 6, 2), 'dark_iron', {'top': 'ember_top'})], parent='body'),
    part('head', (0, 11, -9), [
        cube((-5, 5, -17), (10, 9, 8), 'pig', {'front': 'face_pig'}),
        cube((-3, 9, -19), (6, 4, 3), 'iron', {'front': 'grill_small'}),
    ], parent='body'),
    part('leg_fl', (-3, 16, -6), [cube((-5, 16, -8), (4, 8, 4), 'pig'), cube((-5, 21, -8), (4, 1, 4), 'iron', inflate=0.2)]),
    part('leg_fr', (3, 16, -6), [cube((1, 16, -8), (4, 8, 4), 'pig'), cube((1, 21, -8), (4, 1, 4), 'iron', inflate=0.2)]),
    part('leg_bl', (-3, 16, 6), [cube((-5, 16, 4), (4, 8, 4), 'pig'), cube((-5, 21, 4), (4, 1, 4), 'iron', inflate=0.2)]),
    part('leg_br', (3, 16, 6), [cube((1, 16, 4), (4, 8, 4), 'pig'), cube((1, 21, 4), (4, 1, 4), 'iron', inflate=0.2)]),
], {
    'walk': clip(0.9, True, {'leg_fl': rot(wave(0.9, 30)), 'leg_br': rot(wave(0.9, 30)),
                             'leg_fr': rot(wave(0.9, 30, phase=math.pi)), 'leg_bl': rot(wave(0.9, 30, phase=math.pi)),
                             'head': rot(wave(0.9, 4, cycles=2))}),
    'idle': clip(1.5, True, {'bellows_l': scl(keys3((0, (1, 1, 1)), (0.75, (0.55, 1, 1)), (1.5, (1, 1, 1)))),
                             'bellows_r': scl(keys3((0, (1, 1, 1)), (0.75, (0.55, 1, 1)), (1.5, (1, 1, 1)))),
                             'body': scl(keys3((0, (1, 1, 1)), (0.75, (1.02, 1.03, 1)), (1.5, (1, 1, 1))))}),
    'ability': clip(0.6, False, {'bellows_l': scl(keys3((0, (1, 1, 1)), (0.15, (0.3, 1, 1)), (0.6, (1, 1, 1)))),
                                 'bellows_r': scl(keys3((0, (1, 1, 1)), (0.15, (0.3, 1, 1)), (0.6, (1, 1, 1)))),
                                 'head': rot(keys((0, 0), (0.15, -18), (0.6, 0))),
                                 'pipe': scl(keys3((0, (1, 1, 1)), (0.15, (1.3, 1.5, 1.3)), (0.6, (1, 1, 1))))}),
}, shadow=0.7)

# ---- Flesh Press (Redstone Golem)
mob('flesh_press', [
    part('body', (0, -2, 0), [
        cube((-10, -18, -7), (20, 16, 14), 'flesh', {'front': 'veins', 'back': 'veins'}),
        cube((-11, -17, -8), (22, 3, 2), 'iron', {'front': 'rivets'}), cube((-11, -5, -8), (22, 3, 2), 'iron', {'front': 'rivets'}),
        cube((-12, -19, -8), (2, 18, 2), 'rust'), cube((10, -19, -8), (2, 18, 2), 'rust'),
        cube((-2, -14, -9), (4, 7, 1), 'redstone'),
        cube((-6, -2, -5), (12, 6, 10), 'flesh'),
    ]),
    part('head', (0, -18, -3), [
        cube((-4, -26, -7), (8, 8, 8), 'flesh', {'front': 'face_cage'}),
        cube((-4, -26, -7), (8, 8, 8), 'cage', inflate=0.6),
        cube((-5, -27, -8), (2, 2, 2), 'redstone'), cube((3, -27, -8), (2, 2, 2), 'redstone'),
    ], parent='body'),
    part('arm_r', (-12, -15, 0), [cube((-17, -16, -4), (6, 30, 8), 'flesh', {'front': 'veins'}),
                                  cube((-18, -10, -5), (8, 4, 10), 'iron'), cube((-18, 4, -5), (8, 4, 10), 'iron')], parent='body'),
    part('arm_l', (12, -15, 0), [cube((11, -16, -4), (6, 30, 8), 'flesh', {'front': 'veins'}),
                                 cube((10, -10, -5), (8, 4, 10), 'iron'), cube((10, 4, -5), (8, 4, 10), 'iron')], parent='body'),
    part('leg_r', (-5, 4, 0), [cube((-9, 4, -4), (8, 20, 8), 'flesh'), cube((-10, 14, -5), (10, 4, 10), 'iron')]),
    part('leg_l', (5, 4, 0), [cube((1, 4, -4), (8, 20, 8), 'flesh'), cube((0, 14, -5), (10, 4, 10), 'iron')]),
], {
    'walk': clip(1.4, True, {'leg_r': rot(wave(1.4, 20)), 'leg_l': rot(wave(1.4, 20, phase=math.pi)),
                             'arm_r': rot(wave(1.4, 15, phase=math.pi)), 'arm_l': rot(wave(1.4, 15))}),
    'idle': clip(3.0, True, {'body': merge(rot(wave(3.0, 1.5)), scl(keys3((0, (1, 1, 1)), (1.5, (1.02, 1.02, 1.03)), (3.0, (1, 1, 1))))),
                             'head': rot(wave(3.0, 6, 'y'))}),
    'ability': clip(0.5, False, {'arm_r': rot(keys((0, 0), (0.15, -120), (0.3, -20), (0.5, 0))),
                                 'arm_l': rot(keys((0, 0), (0.15, -120), (0.3, -20), (0.5, 0))),
                                 'body': rot(keys((0, 0), (0.15, -8), (0.3, 6), (0.5, 0)))}),
}, shadow=1.1, scale=0.93)


# ================================================================================================ v2 details
# More parts, secondary motion and a "hurt" reaction for every creature, layered on the designs above.
def _part(mob_name, name):
    return next(p for p in MOBS[mob_name]['parts'] if p['name'] == name)


def add_parts(mob_name, *parts):
    MOBS[mob_name]['parts'].extend(parts)


def add_cubes(mob_name, part_name, *cubes):
    _part(mob_name, part_name)['cubes'].extend(cubes)


def add_clip(mob_name, clip_name, bones, length=None, loop=None):
    anims = MOBS[mob_name]['animations']
    if clip_name in anims:
        anims[clip_name]['bones'].update(bones)
    else:
        anims[clip_name] = clip(length, loop, bones)


def split_off(mob_name, part_name, cube_index, new_name, pivot):
    """Moves one cube of a part into its own child part, so it can move on its own (piston fists, jaws)."""
    part_ = _part(mob_name, part_name)
    c = part_['cubes'].pop(cube_index)
    add_parts(mob_name, part(new_name, pivot, [c], parent=part_name))


def hurt(root, head=None, amount=10):
    bones = {root: rot(keys((0, 0), (0.08, -amount), (0.22, amount * 0.4), (0.4, 0)))}
    if head:
        bones[head] = rot(keys((0, 0), (0.06, 12), (0.16, -10), (0.3, 4), (0.45, 0), axis='z'))
    return bones


# ---- Karst Colossus: exhaust stacks, piston fists that shoot out, lichen shoulders, knee plates
split_off('karst_colossus', 'arm_r', 3, 'fist_r', (-13, 9, 0))
split_off('karst_colossus', 'arm_l', 3, 'fist_l', (13, 9, 0))
add_parts('karst_colossus',
          part('stack_r', (-5, -18, 9), [cube((-7, -27, 8), (3, 9, 3), 'dark_iron', {'top': 'ember_top'}), cube((-7.5, -24, 7.5), (4, 1, 4), 'rust')], parent='body'),
          part('stack_l', (5, -18, 9), [cube((4, -27, 8), (3, 9, 3), 'dark_iron', {'top': 'ember_top'}), cube((3.5, -24, 7.5), (4, 1, 4), 'rust')], parent='body'))
add_cubes('karst_colossus', 'arm_r', cube((-17, -18, -5), (8, 1, 10), 'karst', {'top': 'lichen'}))
add_cubes('karst_colossus', 'arm_l', cube((9, -18, -5), (8, 1, 10), 'karst', {'top': 'lichen'}))
add_cubes('karst_colossus', 'leg_r', cube((-10.5, 12, -5), (9, 2, 2), 'rust'))
add_cubes('karst_colossus', 'leg_l', cube((1.5, 12, -5), (9, 2, 2), 'rust'))
add_cubes('karst_colossus', 'head', cube((-4, -20, -9), (8, 2, 2), 'iron', {'front': 'rivets'}))
add_clip('karst_colossus', 'attack', {'fist_r': pos(keys3((0, (0, 0, 0)), (0.18, (0, 0, 0)), (0.24, (0, 5, -3)), (0.45, (0, 0, 0))))})
add_clip('karst_colossus', 'ability', {'fist_r': pos(keys3((0, (0, 0, 0)), (0.45, (0, 0, 0)), (0.55, (0, 6, 0)), (0.8, (0, 0, 0)))),
                                       'fist_l': pos(keys3((0, (0, 0, 0)), (0.45, (0, 0, 0)), (0.55, (0, 6, 0)), (0.8, (0, 0, 0)))),
                                       'stack_r': scl(keys3((0, (1, 1, 1)), (0.55, (1.2, 1.3, 1.2)), (1.0, (1, 1, 1)))),
                                       'stack_l': scl(keys3((0, (1, 1, 1)), (0.55, (1.2, 1.3, 1.2)), (1.0, (1, 1, 1))))})
add_clip('karst_colossus', 'idle', {'stack_r': rot(wave(3.0, 3, 'z', cycles=2)), 'stack_l': rot(wave(3.0, -3, 'z', cycles=2))})
add_clip('karst_colossus', 'hurt', hurt('body', 'head', 8), 0.45, False)

# ---- Switchback Crawler: headlamp antennae, couplers between segments, vents
add_parts('switchback_crawler',
          part('antenna_l', (3, 9, -16), [cube((2.5, 1, -16.5), (1, 8, 1), 'iron'), cube((2, -1, -17), (2, 2, 2), 'ember')], parent='head', rot=(20, 0, 15)),
          part('antenna_r', (-3, 9, -16), [cube((-3.5, 1, -16.5), (1, 8, 1), 'iron'), cube((-4, -1, -17), (2, 2, 2), 'ember')], parent='head', rot=(20, 0, -15)))
for _i, _z in enumerate((1, 10, 19)):
    add_cubes('switchback_crawler', f'seg{_i}', cube((-1, 13, _z - 1), (2, 2, 2), 'dark_iron'))
add_cubes('switchback_crawler', 'seg1', cube((-3, 7, 3), (6, 2, 4), 'dark_iron', {'top': 'grill_small'}))
add_cubes('switchback_crawler', 'seg3', cube((-2, 10, 21), (4, 1, 3), 'dark_iron', {'top': 'grill_small'}))
add_clip('switchback_crawler', 'idle', {'antenna_l': rot(waves(2.0, (8, 0, 6), (0, 0, 1), cycles=2)),
                                        'antenna_r': rot(waves(2.0, (8, 0, -6), (1, 0, 0), cycles=2))})
add_clip('switchback_crawler', 'walk', {'antenna_l': rot(wave(0.8, 10, cycles=2)), 'antenna_r': rot(wave(0.8, 10, cycles=2, phase=1))})
add_clip('switchback_crawler', 'ability', {'antenna_l': rot(keys((0, 0), (0.1, 40), (0.7, 40), (0.8, 0))),
                                           'antenna_r': rot(keys((0, 0), (0.1, 40), (0.7, 40), (0.8, 0)))})
add_clip('switchback_crawler', 'hurt', merge(hurt('head', None, 15),
                                             {f'seg{i}': rot(keys((0, 0), (0.1, 12 * (1 if i % 2 else -1)), (0.4, 0), axis='y')) for i in range(4)}), 0.45, False)

# ---- Bell Stalker: swinging clapper, turning hip gear, counterweight
add_parts('bell_stalker',
          part('clapper', (0, -38, -12), [cube((-0.5, -38, -12.5), (1, 8, 1), 'iron'), cube((-1.5, -31, -13.5), (3, 3, 3), 'teal_glow')], parent='bell'),
          part('gear', (0, -23, -5.5), [cube((-3, -26, -6), (6, 6, 1), 'brass', {'front': 'rivets'})], parent='hip'),
          part('counterweight', (0, -45, 1.5), [cube((-2, -48, 1), (4, 6, 4), 'iron', {'back': 'rivets'})], parent='neck'))
add_clip('bell_stalker', 'idle', {'clapper': rot(wave(3.0, -14, 'z', phase=0.6)), 'gear': rot(spin(3.0, 180, 'z'))})
add_clip('bell_stalker', 'walk', {'clapper': rot(wave(1.6, -18, 'z', phase=1.6)), 'gear': rot(spin(1.6, 360, 'z'))})
add_clip('bell_stalker', 'ability', {'clapper': rot(keys((0, 0), (0.12, -50), (0.32, 50), (0.52, -35), (0.72, 20), (1.0, -5), (1.2, 0), axis='z'))})
add_clip('bell_stalker', 'hurt', merge(hurt('neck', None, 12), {'bell': rot(wave(0.45, 14, 'z', cycles=2))}), 0.45, False)

# ---- Sluice Chainjaw: red eye lamps, dorsal chain loops, a chain hanging from the jaw
add_cubes('sluice_chainjaw', 'head', cube((-4, 8, -22), (2, 1, 2), 'redstone'), cube((2, 8, -22), (2, 1, 2), 'redstone'))
add_cubes('sluice_chainjaw', 'body', cube((-1, 6, -7), (2, 3, 2), 'chain'), cube((-1, 6, -1), (2, 3, 2), 'chain'), cube((-1, 6, 5), (2, 3, 2), 'chain'))
add_parts('sluice_chainjaw', part('jawchain', (0, 17, -19), [cube((-0.5, 17, -19.5), (1, 6, 1), 'chain')], parent='jaw'))
add_clip('sluice_chainjaw', 'idle', {'jawchain': rot(wave(3.0, 18, 'x', cycles=2))})
add_clip('sluice_chainjaw', 'walk', {'jawchain': rot(wave(1.0, 25, 'x', phase=1))})
add_clip('sluice_chainjaw', 'hurt', merge(hurt('head', None, 14), {'body': rot(keys((0, 0), (0.1, 10), (0.25, -6), (0.45, 0), axis='z'))}), 0.45, False)

# ---- Kiln Brute: a furnace door on its belly that swings open for the volley, hoppers on the shoulders
_part('kiln_brute', 'body')['cubes'].pop(1)
add_parts('kiln_brute', part('door', (0, -6, -9), [cube((-7, -6, -10), (14, 8, 1), 'dark_iron', {'front': 'grill_small'})], parent='body'))
add_cubes('kiln_brute', 'body', cube((-6, -5, -8.5), (12, 6, 1), 'ember'))
add_cubes('kiln_brute', 'arm_r', cube((-19, -21, -5), (8, 3, 10), 'dark_iron', {'top': 'grill_small'}))
add_cubes('kiln_brute', 'arm_l', cube((11, -21, -5), (8, 3, 10), 'dark_iron', {'top': 'grill_small'}))
add_clip('kiln_brute', 'ability', {'door': rot(keys((0, 0), (0.15, -80), (0.75, -80), (1.0, 0)))})
add_clip('kiln_brute', 'idle', {'door': rot(keys((0, 0), (2.4, 0), (2.55, -12), (2.7, 0), (3.0, 0)))})
add_clip('kiln_brute', 'hurt', merge(hurt('body', None, 8), {'chimney': rot(wave(0.4, 10, 'z', cycles=2))}), 0.4, False)

# ---- Spool Weaver: fangs, head lamp, a loose wire from the drum
add_parts('spool_weaver',
          part('fang_l', (2, 16, -12), [cube((1, 16, -13), (2, 3, 2), 'bone')], parent='head'),
          part('fang_r', (-2, 16, -12), [cube((-3, 16, -13), (2, 3, 2), 'bone')], parent='head'),
          part('loose_wire', (0, 14, 11), [cube((-0.5, 14, 10.5), (1, 8, 1), 'wire')], parent='drum'))
add_cubes('spool_weaver', 'head', cube((-1, 9, -10), (2, 1, 2), 'ember'))
add_clip('spool_weaver', 'idle', {'fang_l': rot(wave(4.0, -10, 'y', cycles=4)), 'fang_r': rot(wave(4.0, 10, 'y', cycles=4)),
                                  'loose_wire': rot(wave(4.0, 20, 'x', cycles=2))})
add_clip('spool_weaver', 'attack', {'fang_l': rot(keys((0, 0), (0.15, -35), (0.3, 20), (0.5, 0), axis='y')),
                                    'fang_r': rot(keys((0, 0), (0.15, 35), (0.3, -20), (0.5, 0), axis='y'))})
add_clip('spool_weaver', 'hurt', hurt('body', 'head', 10), 0.45, False)

# ---- Leaking Cell: a glass canister of uranium on its back, a hanging jaw
add_parts('leaking_cell',
          part('canister', (0, -6, 8), [cube((-3, -9, 7), (6, 11, 4), 'uranium'), cube((-3, -9, 7), (6, 11, 4), 'cage', inflate=0.4),
                                        cube((-4, -10, 6.5), (8, 2, 5), 'rust')], parent='body'),
          part('jaw', (0, -5, -3), [cube((-3, -6, -7.5), (6, 2, 5), 'zombie')], parent='head'))
add_clip('leaking_cell', 'idle', {'jaw': rot(keys((0, 0), (1.0, 0), (1.3, 18), (1.8, 18), (2.1, 0), (2.5, 0))),
                                  'canister': scl(keys3((0, (1, 1, 1)), (1.25, (1.03, 1.0, 1.03)), (2.5, (1, 1, 1))))})
add_clip('leaking_cell', 'attack', {'jaw': rot(keys((0, 0), (0.2, 25), (0.4, 0), (0.6, 0)))})
add_clip('leaking_cell', 'ability', {'canister': scl(keys3((0, (1, 1, 1)), (0.25, (1.2, 1.1, 1.2)), (0.8, (1, 1, 1))))})
add_clip('leaking_cell', 'hurt', hurt('body', 'head', 12), 0.45, False)

# ---- Detonator Husk: antenna with a blinking lamp, fuse wires
add_parts('detonator_husk', part('antenna', (2, -16, 2), [cube((1.5, -24, 1.5), (1, 8, 1), 'iron'), cube((1, -26, 1), (2, 2, 2), 'redstone')], parent='head'))
add_cubes('detonator_husk', 'body', cube((-5.5, -2, -4.5), (1, 12, 1), 'cable'), cube((4.5, 2, -4.5), (1, 9, 1), 'cable'))
add_clip('detonator_husk', 'idle', {'antenna': rot(waves(3.0, (6, 0, 8), (0, 0, 1), cycles=2))})
add_clip('detonator_husk', 'ability', {'antenna': rot(wave(1.0, 20, 'z', cycles=8))})
add_clip('detonator_husk', 'hurt', hurt('body', 'head', 8), 0.4, False)

# ---- Tripwire Brood: more crystals on the sack
add_cubes('tripwire_brood', 'sack', cube((4, 0, 8), (2, 5, 2), 'crystal'), cube((-7, 2, 7), (2, 3, 2), 'crystal'), cube((0, 2, 15), (3, 3, 2), 'crystal'))
add_clip('tripwire_brood', 'hurt', merge(hurt('body', 'head', 10),
                                         {'sack': scl(keys3((0, (1, 1, 1)), (0.1, (1.1, 0.92, 1.1)), (0.4, (1, 1, 1))))}), 0.4, False)

# ---- Kilnbound: a flame on its furnace head, a shoulder frame, glowing pelvis
add_parts('kilnbound', part('flame', (0, -26, 0), [cube((-1.5, -31, -1.5), (3, 5, 3), 'ember')], parent='head'))
add_cubes('kilnbound', 'body', cube((-7, -18, -2), (14, 1, 4), 'wood'), cube((-2, 2.5, -2.5), (4, 2, 1), 'ember'))
add_clip('kilnbound', 'idle', {'flame': scl(keys3((0, (1, 1, 1)), (0.25, (0.8, 1.3, 0.8)), (0.5, (1.1, 0.9, 1.1)), (0.8, (0.9, 1.2, 0.9)),
                                                  (1.2, (1.05, 0.95, 1.05)), (1.6, (0.85, 1.25, 0.85)), (2.0, (1, 1, 1))))})
add_clip('kilnbound', 'ability', {'flame': scl(keys3((0, (1, 1, 1)), (0.1, (1.4, 1.8, 1.4)), (0.7, (1, 1, 1))))})
add_clip('kilnbound', 'hurt', merge(hurt('body', 'head', 8),
                                    {'arm_r': rot(wave(0.4, 15, 'z', cycles=2)), 'arm_l': rot(wave(0.4, -15, 'z', cycles=2))}), 0.4, False)

# ---- Living Capacitor: coils on the frame, a gauge
add_cubes('living_capacitor', 'frame', cube((-5.5, 17, -2), (1, 4, 4), 'copper'), cube((4.5, 17, -2), (1, 4, 4), 'copper'),
          cube((-2, 19, -5.5), (4, 3, 1), 'iron', {'front': 'eye1'}))
add_clip('living_capacitor', 'hurt', {'slime': scl(keys3((0, (1, 1, 1)), (0.08, (1.2, 0.8, 1.2)), (0.25, (0.95, 1.08, 0.95)), (0.4, (1, 1, 1))))},
         0.4, False)

# ---- Relay Strider: antenna, cable plugs, relay lamps on the arms
add_parts('relay_strider', part('antenna', (2, -26, 1), [cube((1.5, -33, 0.5), (1, 7, 1), 'iron'), cube((1, -35, 0), (2, 2, 2), 'purple_glow')], parent='head'))
add_cubes('relay_strider', 'cables', cube((-3.5, 17, 1.5), (2, 2, 2), 'copper'), cube((1.5, 15, 1.5), (2, 2, 2), 'copper'))
add_cubes('relay_strider', 'arm_r', cube((-6.5, -2, -1.5), (3, 3, 3), 'dark_iron', {'front': 'eye1'}))
add_cubes('relay_strider', 'arm_l', cube((3.5, -2, -1.5), (3, 3, 3), 'dark_iron', {'front': 'eye1'}))
add_clip('relay_strider', 'idle', {'antenna': rot(wave(3.0, 10, 'z', cycles=2))})
add_clip('relay_strider', 'hurt', merge(hurt('body', 'head', 10), {'cables': rot(wave(0.45, 25, 'x', cycles=2))}), 0.45, False)

# ---- Bellows Hog: ears, tusks, a curly tail, handles on the bellows
add_parts('bellows_hog',
          part('ear_l', (4, 5, -13), [cube((3, 3, -14), (3, 3, 1), 'pig')], parent='head', rot=(0, 0, 20)),
          part('ear_r', (-4, 5, -13), [cube((-6, 3, -14), (3, 3, 1), 'pig')], parent='head', rot=(0, 0, -20)),
          part('tail', (0, 8, 9), [cube((-0.5, 7, 9), (1, 1, 4), 'pig'), cube((-0.5, 5, 12), (1, 3, 1), 'pig')], parent='body'))
add_cubes('bellows_hog', 'head', cube((-4, 11, -19), (1, 3, 1), 'bone'), cube((3, 11, -19), (1, 3, 1), 'bone'))
add_cubes('bellows_hog', 'bellows_l', cube((8, 6, -2), (2, 1, 4), 'wood'))
add_cubes('bellows_hog', 'bellows_r', cube((-10, 6, -2), (2, 1, 4), 'wood'))
add_clip('bellows_hog', 'idle', {'ear_l': rot(keys((0, 0), (0.6, 0), (0.7, 15), (0.8, 0), (1.5, 0), axis='z')),
                                 'tail': rot(wave(1.5, 25, 'y', cycles=2))})
add_clip('bellows_hog', 'walk', {'ear_l': rot(wave(0.9, 12, 'z', cycles=2)), 'ear_r': rot(wave(0.9, -12, 'z', cycles=2)),
                                 'tail': rot(wave(0.9, 20, 'y', cycles=2))})
add_clip('bellows_hog', 'hurt', merge(hurt('head', None, 15), {'body': rot(wave(0.4, 6, 'z', cycles=2))}), 0.4, False)

# ---- Flesh Press: the press screw on top turns, shoulder pistons, bigger fists
add_parts('flesh_press', part('screw', (0, -19, 2), [cube((-1, -27, 1), (2, 8, 2), 'iron'),
                                                     cube((-4, -22, -2), (8, 1, 8), 'brass', {'top': 'rivets'})], parent='body'))
add_cubes('flesh_press', 'arm_r', cube((-18, -19, -3), (6, 3, 6), 'rust'), cube((-18, 12, -5), (8, 6, 10), 'flesh', {'bottom': 'veins'}))
add_cubes('flesh_press', 'arm_l', cube((12, -19, -3), (6, 3, 6), 'rust'), cube((10, 12, -5), (8, 6, 10), 'flesh', {'bottom': 'veins'}))
add_clip('flesh_press', 'idle', {'screw': rot(spin(3.0, 180, 'y'))})
add_clip('flesh_press', 'walk', {'screw': rot(spin(1.4, 360, 'y'))})
add_clip('flesh_press', 'ability', {'screw': merge(rot(spin(0.5, 360, 'y')), pos(keys3((0, (0, 0, 0)), (0.15, (0, 3, 0)), (0.5, (0, 0, 0)))))})
add_clip('flesh_press', 'hurt', hurt('body', 'head', 6), 0.4, False)


# ================================================================================================ v3 animation
# Every creature moves in its own way. Curves are written as functions of u (0..1 over the clip) and sampled.
TAU = 2 * math.pi


def track(length, fn, steps=16):
    """Samples fn(u) -> (x, y, z) over a looping clip."""
    return [[round(length * i / steps, 3)] + [round(v, 2) for v in fn(i / steps)] for i in range(steps + 1)]


def gait_value(u, amp, duty=0.6):
    """A leg: slow push back while the foot is planted, quick eased swing forward in the air."""
    u %= 1.0
    if u < duty:
        return amp - 2 * amp * (u / duty)
    w = (u - duty) / (1 - duty)
    return -amp + 2 * amp * (0.5 - 0.5 * math.cos(math.pi * w))


def swing_lift(u, amp, duty=0.6):
    """Bend/lift that only happens while the leg is in the air."""
    u %= 1.0
    return 0.0 if u < duty else amp * math.sin(math.pi * (u - duty) / (1 - duty))


def s(u, cycles=1.0, phase=0.0):
    return math.sin(TAU * (cycles * u + phase))


def c(u, cycles=1.0, phase=0.0):
    return math.cos(TAU * (cycles * u + phase))


def footfall(u, per_cycle=2, sharp=3):
    """1 at every footfall, 0 in between (sharp dip for heavy walkers)."""
    return max(0.0, c(u, per_cycle)) ** sharp


def snap(*frames):
    """Keys that jump: each (t, (x, y, z)) is held until the next, with a near-instant change."""
    out = []
    for i, (t, v) in enumerate(frames):
        if i > 0:
            out.append([round(t - 0.02, 3)] + list(frames[i - 1][1]))
        out.append([t] + list(v))
    return out


def R(fn, length, steps=16):
    return rot(track(length, fn, steps))


def P(fn, length, steps=16):
    return pos(track(length, fn, steps))


def S(fn, length, steps=16):
    return scl(track(length, fn, steps))


def replace_clip(mob_name, clip_name, length, loop, bones):
    """Replaces the clip, but keeps tracks of parts the new clip does not mention."""
    old = MOBS[mob_name]['animations'].get(clip_name, {'bones': {}})['bones']
    keep = {k: v for k, v in old.items() if k not in bones}
    if keep and old:
        # old tracks were timed for the old length: rescale them
        old_len = MOBS[mob_name]['animations'][clip_name]['length']
        for tr in keep.values():
            for ch in tr.values():
                for key in ch:
                    key[0] = round(key[0] * length / old_len, 3)
    bones = dict(bones)
    bones.update(keep)
    MOBS[mob_name]['animations'][clip_name] = clip(length, loop, bones)


# ---- Karst Colossus: a heavy lumber. Weight rolls from side to side, the whole body drops on every step
L = 1.6
replace_clip('karst_colossus', 'walk', L, True, {
    'leg_r': R(lambda u: (gait_value(u, 24, 0.6), 0, 0), L),
    'leg_l': R(lambda u: (gait_value(u + 0.5, 24, 0.6), 0, 0), L),
    'body': merge(R(lambda u: (2 + 1.5 * footfall(u), 4 * s(u), 5 * s(u)), L), P(lambda u: (0, 1.6 * footfall(u, 2, 2), 0), L)),
    'head': R(lambda u: (-3 * footfall(u, 2, 2) + 2 * s(u, 2, 0.1), -3 * s(u), -3 * s(u)), L),
    'arm_r': R(lambda u: (-18 * s(u, 1, 0.05) + 5, 0, 4 + 2 * footfall(u)), L),
    'arm_l': R(lambda u: (18 * s(u, 1, 0.05) + 5, 0, -4 - 2 * footfall(u)), L),
    'fist_r': R(lambda u: (8 * s(u, 1, -0.15), 0, 0), L),
    'fist_l': R(lambda u: (-8 * s(u, 1, -0.15), 0, 0), L),
    'cables': R(lambda u: (14 * s(u, 1, -0.2), 0, 6 * s(u, 2)), L),
    'stack_r': R(lambda u: (0, 0, 5 * s(u, 2, -0.15)), L), 'stack_l': R(lambda u: (0, 0, 5 * s(u, 2, -0.15)), L),
})
L = 4.0
replace_clip('karst_colossus', 'idle', L, True, {
    'body': merge(R(lambda u: (1.5 * s(u, 2), 0, 0), L), P(lambda u: (0, 0.4 * s(u, 2), 0), L)),
    'head': rot(keys3((0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.4, (4, 28, 0)), (2.4, (4, 28, 0)), (2.8, (-2, -18, 3)), (3.6, (-2, -18, 3)), (4.0, (0, 0, 0)))),
    'fist_r': rot(keys3((0, (0, 0, 0)), (2.0, (0, 0, 0)), (2.15, (-18, 0, 0)), (2.5, (0, 0, 0)), (4.0, (0, 0, 0)))),
    'stack_r': scl(keys3((0, (1, 1, 1)), (1.1, (1, 1, 1)), (1.25, (1.25, 1.3, 1.25)), (1.6, (1, 1, 1)), (4.0, (1, 1, 1)))),
    'stack_l': scl(keys3((0, (1, 1, 1)), (2.9, (1, 1, 1)), (3.05, (1.25, 1.3, 1.25)), (3.4, (1, 1, 1)), (4.0, (1, 1, 1)))),
    'cables': R(lambda u: (6 * s(u, 2), 0, 4 * s(u, 1, 0.3)), L),
})

# ---- Switchback Crawler: a ripple runs down its legs from head to tail, the body snakes behind the head
L = 0.6
bones = {}
for i in range(4):
    ph = i * 0.2
    bones[f'seg{i}_leg_l'] = R(lambda u, ph=ph: (0, gait_value(u + ph, 28, 0.55), swing_lift(u + ph, 20, 0.55)), L)
    bones[f'seg{i}_leg_r'] = R(lambda u, ph=ph: (0, -gait_value(u + ph + 0.5, 28, 0.55), -swing_lift(u + ph + 0.5, 20, 0.55)), L)
    bones[f'seg{i}'] = R(lambda u, i=i: (1.5 * s(u, 2, -i * 0.12), 8 * s(u, 1, -i * 0.16), 0), L)
bones['head'] = R(lambda u: (2 * s(u, 2), 6 * s(u, 1, 0.12), 0), L)
bones['tail'] = R(lambda u: (0, 16 * s(u, 1, -0.75), 0), L)
bones['antenna_l'] = R(lambda u: (10 * s(u, 2, -0.2), 0, 4 * s(u, 1)), L)
bones['antenna_r'] = R(lambda u: (10 * s(u, 2, -0.3), 0, -4 * s(u, 1)), L)
bones['body'] = P(lambda u: (0, 0.3 * s(u, 4), 0), L)
replace_clip('switchback_crawler', 'walk', L, True, bones)
L = 3.0
replace_clip('switchback_crawler', 'idle', L, True, {
    'head': rot(keys3((0, (0, 0, 0)), (0.5, (0, -28, 0)), (1.1, (0, -28, 0)), (1.5, (-6, 24, 0)), (2.3, (-6, 24, 0)), (3.0, (0, 0, 0)))),
    'mandible_l': rot(keys3((0, (0, 0, 0)), (1.9, (0, 0, 0)), (2.0, (0, -25, 0)), (2.1, (0, 0, 0)), (2.2, (0, -25, 0)), (2.3, (0, 0, 0)), (3.0, (0, 0, 0)))),
    'mandible_r': rot(keys3((0, (0, 0, 0)), (1.9, (0, 0, 0)), (2.0, (0, 25, 0)), (2.1, (0, 0, 0)), (2.2, (0, 25, 0)), (2.3, (0, 0, 0)), (3.0, (0, 0, 0)))),
    'seg0': R(lambda u: (0, 3 * s(u), 0), L), 'seg2': R(lambda u: (0, -3 * s(u), 0), L), 'tail': R(lambda u: (0, 12 * s(u, 1, 0.4), 0), L),
    'body': P(lambda u: (0, 0.3 * s(u, 2), 0), L),
})

# ---- Bell Stalker: a lurching tripod. The bell swings like a pendulum a beat behind the body
L = 2.0
bones = {
    'hip': merge(P(lambda u: (0, 1.5 * footfall(u, 3, 2), 0), L), R(lambda u: (0, 5 * s(u), 4 * s(u)), L)),
    'neck': R(lambda u: (6 * s(u, 1, -0.2), 0, -3 * s(u)), L),
    'bell': R(lambda u: (6 * s(u, 2, -0.3), 0, 18 * s(u, 1, -0.35)), L),
    'clapper': R(lambda u: (0, 0, -28 * s(u, 1, -0.5)), L),
    'gear': rot(spin(L, 360, 'z')),
}
for leg, ph in (('leg_l', 0.0), ('leg_r', 1 / 3), ('leg_b', 2 / 3)):
    bones[leg] = R(lambda u, ph=ph: (gait_value(u + ph, 20, 0.7), 0, 0), L)
    bones[leg + '_low'] = R(lambda u, ph=ph: (swing_lift(u + ph, 34, 0.7), 0, 0), L)
for i in range(5):
    bones[f'tendril{i}'] = R(lambda u, i=i: (22 * s(u, 1, -0.5 - i * 0.07), 0, 10 * c(u, 1, -i * 0.1)), L)
replace_clip('bell_stalker', 'walk', L, True, bones)
L = 5.0
bones = {
    'neck': rot(keys3((0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.3, (-6, -32, 8)), (2.5, (-6, -32, 8)), (3.0, (-4, 26, -6)), (4.2, (-4, 26, -6)), (5.0, (0, 0, 0)))),
    'bell': R(lambda u: (2 * s(u, 2), 0, 7 * s(u, 2, 0.2)), L),
    'clapper': R(lambda u: (0, 0, -10 * s(u, 2, 0.35)), L),
    'gear': rot(spin(L, 180, 'z')),
    'hip': P(lambda u: (0, 0.5 * s(u, 2), 0), L),
}
for i in range(5):
    bones[f'tendril{i}'] = R(lambda u, i=i: (12 * s(u, 2, i * 0.15), 0, 8 * s(u, 1, i * 0.2)), L)
replace_clip('bell_stalker', 'idle', L, True, bones)

# ---- Sluice Chainjaw: a sprawling crawl, the spine bends in an S that runs into the tail
L = 1.2
replace_clip('sluice_chainjaw', 'walk', L, True, {
    'body': merge(R(lambda u: (0, 7 * s(u), 2 * s(u, 2)), L), P(lambda u: (0, 0.5 * footfall(u, 2, 1), 0), L)),
    'head': R(lambda u: (2 * s(u, 2), -8 * s(u, 1, 0.1), 0), L),
    'tail1': R(lambda u: (0, 18 * s(u, 1, -0.2), 0), L),
    'tail2': R(lambda u: (0, 26 * s(u, 1, -0.4), 0), L),
    'leg_fl': R(lambda u: (gait_value(u, 34, 0.55), 0, -swing_lift(u, 14, 0.55)), L),
    'leg_br': R(lambda u: (gait_value(u, 34, 0.55), 0, swing_lift(u, 14, 0.55)), L),
    'leg_fr': R(lambda u: (gait_value(u + 0.5, 34, 0.55), 0, swing_lift(u + 0.5, 14, 0.55)), L),
    'leg_bl': R(lambda u: (gait_value(u + 0.5, 34, 0.55), 0, -swing_lift(u + 0.5, 14, 0.55)), L),
    'jawchain': R(lambda u: (22 * s(u, 1, -0.3), 0, 8 * s(u, 1)), L),
})
L = 4.0
replace_clip('sluice_chainjaw', 'idle', L, True, {
    'jaw': rot(keys((0, 0), (1.5, 0), (2.3, 24), (2.9, 24), (3.02, -2), (3.2, 0), (4.0, 0))),
    'head': rot(keys3((0, (0, 0, 0)), (1.5, (0, 0, 0)), (2.3, (-8, 0, 0)), (2.9, (-8, 0, 0)), (3.05, (6, 0, 0)), (3.4, (0, 0, 0)), (4.0, (0, 0, 0)))),
    'tail1': R(lambda u: (0, 12 * s(u), 0), L), 'tail2': R(lambda u: (0, 18 * s(u, 1, -0.15), 0), L),
    'body': P(lambda u: (0, 0.4 * s(u, 2), 0), L),
    'jawchain': R(lambda u: (14 * s(u, 2), 0, 0), L),
})

# ---- Kiln Brute: stomps. Everything shakes on the footfall; the furnace door rattles
L = 1.6
replace_clip('kiln_brute', 'walk', L, True, {
    'leg_r': R(lambda u: (gait_value(u, 22, 0.55), 0, 0), L),
    'leg_l': R(lambda u: (gait_value(u + 0.5, 22, 0.55), 0, 0), L),
    'body': merge(R(lambda u: (3 + 2 * footfall(u, 2, 4), 5 * s(u), 6 * s(u)), L), P(lambda u: (0, 2.0 * footfall(u, 2, 4), 0), L)),
    'arm_r': R(lambda u: (-26 * s(u, 1, 0.05), 0, 6 + 3 * footfall(u, 2, 4)), L),
    'arm_l': R(lambda u: (26 * s(u, 1, 0.05), 0, -6 - 3 * footfall(u, 2, 4)), L),
    'chimney': R(lambda u: (8 * s(u, 2, -0.2), 0, 4 * s(u, 1, -0.1)), L),
    'door': R(lambda u: (-9 * footfall(u, 2, 6), 0, 0), L),
})
L = 4.0
replace_clip('kiln_brute', 'idle', L, True, {
    'body': merge(S(lambda u: (1 + 0.015 * s(u, 2), 1 + 0.03 * s(u, 2), 1 + 0.02 * s(u, 2)), L), R(lambda u: (2 * s(u, 2), 0, 0), L)),
    'arm_r': rot(keys3((0, (0, 0, 4)), (2.8, (0, 0, 4)), (3.0, (-40, 0, 10)), (3.15, (-5, 0, 4)), (3.3, (-40, 0, 10)), (3.5, (0, 0, 4)), (4.0, (0, 0, 4)))),
    'arm_l': R(lambda u: (0, 0, -4 - 2 * s(u, 2)), L),
    'chimney': R(lambda u: (0, 0, 4 * s(u, 3)), L),
    'door': rot(keys((0, 0), (1.2, 0), (1.3, -10), (1.4, 0), (1.5, -6), (1.6, 0), (4.0, 0))),
})

# ---- Spool Weaver: skitters on two alternating groups of legs, the spool spins as it runs
L = 0.5
bones = {'drum': rot(spin(L, 360)), 'body': P(lambda u: (0, 0.4 * s(u, 4), 0), L), 'head': R(lambda u: (3 * s(u, 2), 4 * s(u, 1, 0.2), 0), L),
         'loose_wire': R(lambda u: (25 * s(u, 2, -0.3), 0, 10 * s(u, 1)), L)}
for i in range(4):
    grp = (i % 2) * 0.5
    bones[f'leg_r{i}'] = R(lambda u, g=grp: (0, gait_value(u + g, 24, 0.5), -swing_lift(u + g, 18, 0.5)), L)
    bones[f'leg_l{i}'] = R(lambda u, g=grp: (0, -gait_value(u + g + 0.5, 24, 0.5), swing_lift(u + g + 0.5, 18, 0.5)), L)
replace_clip('spool_weaver', 'walk', L, True, bones)
L = 4.0
replace_clip('spool_weaver', 'idle', L, True, {
    'drum': rot(spin(L, 90)),
    'leg_r0': rot(keys3((0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.08, (0, 0, 30)), (1.16, (0, 0, 0)), (1.24, (0, 0, 30)), (1.32, (0, 0, 0)), (4.0, (0, 0, 0)))),
    'leg_l1': rot(keys3((0, (0, 0, 0)), (2.6, (0, 0, 0)), (2.68, (0, 0, -30)), (2.76, (0, 0, 0)), (4.0, (0, 0, 0)))),
    'head': rot(snap((0, (0, 0, 0)), (0.7, (0, 18, 6)), (1.8, (0, -14, -4)), (3.1, (0, 0, 0)))),
    'fang_l': R(lambda u: (0, -10 * max(0, s(u, 6)), 0), L), 'fang_r': R(lambda u: (0, 10 * max(0, s(u, 6)), 0), L),
    'body': P(lambda u: (0, 0.3 * s(u, 2), 0), L),
})

# ---- Leaking Cell: a shambling limp. The left leg drags, the head lolls, the arms dangle
L = 1.8
replace_clip('leaking_cell', 'walk', L, True, {
    'leg_r': R(lambda u: (gait_value(u, 28, 0.55), 0, 0), L),
    'leg_l': R(lambda u: (gait_value(u + 0.5, 12, 0.8), 0, -3), L),
    'body': merge(R(lambda u: (4, 3 * s(u), 6 + 4 * s(u)), L), P(lambda u: (0, 1.6 * max(0, c(u)) ** 2 + 0.3 * footfall(u, 2, 2), 0), L)),
    'head': R(lambda u: (6 + 5 * s(u, 1, -0.3), 0, 14 * s(u, 1, -0.4)), L),
    'arm_r': R(lambda u: (10 * s(u, 1, -0.3), 0, 4 * s(u, 1, -0.2)), L),
    'arm_l': R(lambda u: (-8 * s(u, 1, -0.35), 0, -5 * s(u, 1, -0.25)), L),
    'jaw': R(lambda u: (10 + 6 * s(u, 2), 0, 0), L),
    'canister': S(lambda u: (1 + 0.04 * s(u, 2, -0.2), 1 - 0.03 * s(u, 2, -0.2), 1 + 0.04 * s(u, 2, -0.2)), L),
})
L = 3.5
replace_clip('leaking_cell', 'idle', L, True, {
    'head': R(lambda u: (8 + 6 * s(u, 1, 0.2), 10 * s(u, 1), 16 * s(u, 1, 0.1)), L),
    'body': rot(keys3((0, (2, 0, 0)), (2.4, (2, 0, 0)), (2.5, (8, 4, -6)), (2.6, (-2, -4, 6)), (2.7, (6, 2, -3)), (2.9, (2, 0, 0)), (3.5, (2, 0, 0)))),
    'arm_r': R(lambda u: (6 * s(u, 1, 0.3), 0, 4 * s(u)), L), 'arm_l': R(lambda u: (6 * s(u, 1, 0.8), 0, -4 * s(u, 1, 0.5)), L),
})

# ---- Detonator Husk: waddles on stumpy legs, its antenna whips about
L = 0.7
replace_clip('detonator_husk', 'walk', L, True, {
    'leg_fl': R(lambda u: (gait_value(u, 30, 0.5), 0, 0), L), 'leg_br': R(lambda u: (gait_value(u, 30, 0.5), 0, 0), L),
    'leg_fr': R(lambda u: (gait_value(u + 0.5, 30, 0.5), 0, 0), L), 'leg_bl': R(lambda u: (gait_value(u + 0.5, 30, 0.5), 0, 0), L),
    'body': merge(R(lambda u: (0, 0, 8 * s(u)), L), P(lambda u: (0, 1.0 * footfall(u, 2, 1), 0), L)),
    'head': R(lambda u: (3 * s(u, 2), 0, -6 * s(u)), L),
    'antenna': R(lambda u: (18 * s(u, 2, -0.25), 0, 14 * s(u, 1, -0.2)), L),
})
L = 3.0
replace_clip('detonator_husk', 'idle', L, True, {
    'head': rot(snap((0, (0, 0, 0)), (0.6, (0, 30, 0)), (1.3, (0, -25, 5)), (2.1, (-8, 10, 0)), (2.7, (0, 0, 0)))),
    'antenna': R(lambda u: (10 * s(u, 3), 0, 8 * s(u, 2)), L),
    'body': R(lambda u: (0, 0, 1.2 * s(u, 9)), L),
})

# ---- Tripwire Brood: a heavy scuttle, the egg sack sloshes behind
L = 0.7
bones = {'sack': merge(R(lambda u: (8 * s(u, 2, -0.3), 0, 5 * s(u, 1, -0.3)), L),
                       S(lambda u: (1 + 0.04 * s(u, 2, -0.35), 1 - 0.05 * s(u, 2, -0.35), 1 + 0.04 * s(u, 2, -0.35)), L)),
         'body': P(lambda u: (0, 0.5 * s(u, 4), 0), L), 'strand': R(lambda u: (0, 0, 25 * s(u, 1, -0.3)), L)}
for i in range(4):
    grp = (i % 2) * 0.5
    bones[f'leg_r{i}'] = R(lambda u, g=grp: (0, gait_value(u + g, 22, 0.55), -swing_lift(u + g, 16, 0.55)), L)
    bones[f'leg_l{i}'] = R(lambda u, g=grp: (0, -gait_value(u + g + 0.5, 22, 0.55), swing_lift(u + g + 0.5, 16, 0.55)), L)
replace_clip('tripwire_brood', 'walk', L, True, bones)

# ---- Kilnbound: stiff skeletal steps with a constant rattle
L = 1.1
replace_clip('kilnbound', 'walk', L, True, {
    'leg_r': R(lambda u: (gait_value(u, 32, 0.5), 0, 0), L), 'leg_l': R(lambda u: (gait_value(u + 0.5, 32, 0.5), 0, 0), L),
    'arm_r': R(lambda u: (-26 * s(u) + 3 * s(u, 8), 0, 3 * s(u, 6)), L), 'arm_l': R(lambda u: (26 * s(u) + 3 * s(u, 8, 0.3), 0, -3 * s(u, 6)), L),
    'body': merge(R(lambda u: (0, 6 * s(u), 0), L), P(lambda u: (0, 0.7 * footfall(u, 2, 1), 0), L)),
    'head': R(lambda u: (2 * s(u, 8), -5 * s(u), 3 * s(u, 6, 0.2)), L),
})
L = 2.5
replace_clip('kilnbound', 'idle', L, True, {
    'arm_r': rot(keys3((0, (0, 0, 0)), (1.2, (0, 0, 0)), (1.25, (0, 0, 10)), (1.3, (0, 0, -6)), (1.35, (0, 0, 8)), (1.4, (0, 0, 0)), (2.5, (0, 0, 0)))),
    'arm_l': rot(keys3((0, (0, 0, 0)), (1.3, (0, 0, 0)), (1.35, (0, 0, -10)), (1.4, (0, 0, 6)), (1.45, (0, 0, -8)), (1.5, (0, 0, 0)), (2.5, (0, 0, 0)))),
    'head': rot(snap((0, (0, 0, 0)), (0.8, (0, 0, 12)), (1.9, (-6, 0, -4)), (2.3, (0, 0, 0)))),
    'body': P(lambda u: (0, 0.3 * s(u, 2), 0), L),
})

# ---- Living Capacitor: hops, squashing on landing and stretching in the air; the electrodes whip
L = 0.9
replace_clip('living_capacitor', 'walk', L, True, {
    'frame': pos(keys3((0, (0, 0, 0)), (0.12, (0, 1, 0)), (0.42, (0, -5, 0)), (0.72, (0, 0, 0)), (0.8, (0, 1, 0)), (0.9, (0, 0, 0)))),
    'slime': scl(keys3((0, (1, 1, 1)), (0.12, (1.18, 0.8, 1.18)), (0.3, (0.9, 1.15, 0.9)), (0.6, (0.95, 1.08, 0.95)),
                       (0.74, (1.22, 0.78, 1.22)), (0.9, (1, 1, 1)))),
    'electrode_l': R(lambda u: (0, 0, 22 * s(u, 1, -0.2)), L), 'electrode_r': R(lambda u: (0, 0, -22 * s(u, 1, -0.2)), L),
})

# ---- Relay Strider: long gliding strides; standing still it glitches, snapping between poses
L = 1.8
replace_clip('relay_strider', 'walk', L, True, {
    'leg_r': R(lambda u: (gait_value(u, 30, 0.6), 0, 0), L), 'leg_l': R(lambda u: (gait_value(u + 0.5, 30, 0.6), 0, 0), L),
    'arm_r': R(lambda u: (-22 * s(u, 1, -0.1), 0, 4), L), 'arm_l': R(lambda u: (22 * s(u, 1, -0.1), 0, -4), L),
    'body': merge(R(lambda u: (0, 5 * s(u), 2 * s(u)), L), P(lambda u: (0, 0.8 * footfall(u, 2, 1), 0), L)),
    'head': R(lambda u: (0, -5 * s(u), 0), L),
    'cables': R(lambda u: (18 * s(u, 1, -0.3), 0, 8 * s(u, 2, -0.2)), L),
})
L = 4.0
replace_clip('relay_strider', 'idle', L, True, {
    'head': rot(snap((0, (0, 0, 0)), (1.0, (0, 38, 0)), (1.4, (-10, -12, 8)), (2.5, (0, 0, 0)), (3.2, (6, -30, 0)), (3.6, (0, 0, 0)))),
    'arm_r': rot(snap((0, (0, 0, 0)), (1.0, (-20, 0, 12)), (1.4, (0, 0, 0)), (3.2, (-12, 0, 20)), (3.6, (0, 0, 0)))),
    'arm_l': R(lambda u: (0, 0, -5 * s(u, 2)), L),
    'cables': R(lambda u: (8 * s(u, 2), 0, 6 * s(u, 1)), L),
    'antenna': R(lambda u: (0, 0, 12 * s(u, 3)), L),
})

# ---- Bellows Hog: a trot; the bellows pump with every step, the ears flop
L = 0.8
replace_clip('bellows_hog', 'walk', L, True, {
    'leg_fl': R(lambda u: (gait_value(u, 34, 0.5), 0, 0), L), 'leg_br': R(lambda u: (gait_value(u, 34, 0.5), 0, 0), L),
    'leg_fr': R(lambda u: (gait_value(u + 0.5, 34, 0.5), 0, 0), L), 'leg_bl': R(lambda u: (gait_value(u + 0.5, 34, 0.5), 0, 0), L),
    'body': P(lambda u: (0, 0.9 * footfall(u, 2, 1), 0), L),
    'bellows_l': S(lambda u: (0.8 + 0.2 * c(u, 2), 1, 1), L), 'bellows_r': S(lambda u: (0.8 + 0.2 * c(u, 2), 1, 1), L),
    'head': R(lambda u: (5 * s(u, 2, 0.1), 0, 0), L),
    'ear_l': R(lambda u: (0, 0, 14 * s(u, 2, -0.3)), L), 'ear_r': R(lambda u: (0, 0, -14 * s(u, 2, -0.3)), L),
    'tail': R(lambda u: (0, 25 * s(u, 2), 0), L),
})
L = 2.4
replace_clip('bellows_hog', 'idle', L, True, {
    'head': rot(keys((0, 0), (0.3, 18), (0.4, 12), (0.5, 20), (0.6, 12), (0.8, 0), (2.4, 0))),
    'bellows_l': S(lambda u: (0.7 + 0.3 * c(u, 1), 1, 1), L), 'bellows_r': S(lambda u: (0.7 + 0.3 * c(u, 1), 1, 1), L),
    'ear_l': rot(keys((0, 0), (1.6, 0), (1.7, 18), (1.8, 0), (2.4, 0), axis='z')),
    'tail': R(lambda u: (0, 20 * s(u, 3), 0), L),
})

# ---- Flesh Press: a hunched knuckle-walk; the press screw keeps turning
L = 1.8
replace_clip('flesh_press', 'walk', L, True, {
    'leg_r': R(lambda u: (gait_value(u, 20, 0.6), 0, 0), L), 'leg_l': R(lambda u: (gait_value(u + 0.5, 20, 0.6), 0, 0), L),
    'arm_r': R(lambda u: (-22 * s(u, 1, 0.1) - 6, 0, 5), L), 'arm_l': R(lambda u: (22 * s(u, 1, 0.1) - 6, 0, -5), L),
    'body': merge(R(lambda u: (8 + 2 * footfall(u, 2, 3), 4 * s(u), 4 * s(u)), L), P(lambda u: (0, 1.5 * footfall(u, 2, 3), 0), L)),
    'head': R(lambda u: (-8 - 2 * footfall(u, 2, 3), -4 * s(u), 0), L),
    'screw': rot(spin(L, 360, 'y')),
})
L = 4.0
replace_clip('flesh_press', 'idle', L, True, {
    'body': merge(R(lambda u: (5 + 1.5 * s(u, 2), 0, 0), L), S(lambda u: (1 + 0.02 * s(u, 2), 1 + 0.02 * s(u, 2), 1 + 0.03 * s(u, 2)), L)),
    'head': rot(keys3((0, (-5, 0, 0)), (1.2, (-5, 0, 0)), (1.8, (-5, 30, 0)), (2.8, (-5, 30, 0)), (3.4, (-5, 0, 0)), (4.0, (-5, 0, 0)))),
    'arm_r': R(lambda u: (5 * s(u, 2) - 4, 0, 3), L), 'arm_l': R(lambda u: (5 * s(u, 2, 0.5) - 4, 0, -3), L),
    'screw': rot(spin(L, 180, 'y')),
})


# ================================================================================================ wildlife
# The bottom of the realm's food chain: grazers on the veins, moths at the lamps, and the jackals that hunt them.

# ---- Spark Mite: a copper beetle with a glowing belly; scuttles on a tripod gait and grazes on redstone veins
_mite_legs = []
for _i, _z in enumerate((-3, 0, 3)):
    _spread = (-25, 0, 25)[_i]
    _mite_legs.append(part(f'leg_l{_i}', (4, 20, _z), [cube((4, 19.5, _z - 0.5), (4, 1, 1), 'dark_iron')], parent='body', rot=(0, -_spread, 40)))
    _mite_legs.append(part(f'leg_r{_i}', (-4, 20, _z), [cube((-8, 19.5, _z - 0.5), (4, 1, 1), 'dark_iron')], parent='body', rot=(0, _spread, -40)))
mob('spark_mite', [
    part('body', (0, 20, 0), [
        cube((-4, 16, -5), (8, 5, 10), 'copper', {'top': 'circuit'}),
        cube((-3, 20.5, -4), (6, 1, 8), 'redstone'),
    ]),
    part('head', (0, 19, -5), [cube((-2.5, 16.5, -8), (5, 4, 3), 'dark_iron', {'front': 'eyes2'})], parent='body'),
    part('mand_l', (1.5, 19.5, -8), [cube((1, 19, -10), (1, 1, 2), 'brass')], parent='head'),
    part('mand_r', (-1.5, 19.5, -8), [cube((-2, 19, -10), (1, 1, 2), 'brass')], parent='head'),
    part('ant_l', (1.5, 16.5, -7), [cube((1, 12.5, -8), (1, 4, 1), 'cable'), cube((1, 11.5, -8), (1, 1, 1), 'redstone')], parent='head', rot=(20, 0, 15)),
    part('ant_r', (-1.5, 16.5, -7), [cube((-2, 12.5, -8), (1, 4, 1), 'cable'), cube((-2, 11.5, -8), (1, 1, 1), 'redstone')], parent='head', rot=(20, 0, -15)),
    part('shell_l', (0.2, 15.8, -4), [cube((0.2, 15.2, -4.8), (4.2, 1, 10), 'copper_ox')], parent='body'),
    part('shell_r', (-0.2, 15.8, -4), [cube((-4.4, 15.2, -4.8), (4.2, 1, 10), 'copper_ox')], parent='body'),
    *_mite_legs,
], {}, shadow=0.35)
L = 0.5
_tri_a = ('leg_l0', 'leg_r1', 'leg_l2')
_walk = {'body': merge(P(lambda u: (0, -0.3 * abs(s(u, 2)), 0), L), R(lambda u: (0, 0, 3 * s(u)), L)),
         'head': R(lambda u: (0, 4 * s(u, 1, 0.25), 0), L),
         'ant_l': R(lambda u: (10 * s(u, 2), 0, 0), L), 'ant_r': R(lambda u: (10 * s(u, 2, 0.3), 0, 0), L)}
for _n in ('leg_l0', 'leg_l1', 'leg_l2', 'leg_r0', 'leg_r1', 'leg_r2'):
    _ph = 0.0 if _n in _tri_a else 0.5
    _sg = 1 if _n.startswith('leg_l') else -1
    _walk[_n] = R(lambda u, ph=_ph, sg=_sg: (0, sg * gait_value(u + ph, 22, 0.5), sg * swing_lift(u + ph, -25, 0.5)), L)
replace_clip('spark_mite', 'walk', L, True, _walk)
L = 3.0
replace_clip('spark_mite', 'idle', L, True, {
    'ant_l': rot(keys3((0, (0, 0, 0)), (0.4, (-20, 0, 10)), (0.6, (5, 0, 0)), (1.8, (0, 0, 0)), (2.0, (-15, 0, -5)), (3.0, (0, 0, 0)))),
    'ant_r': rot(keys3((0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.2, (-25, 0, -10)), (1.5, (5, 0, 0)), (3.0, (0, 0, 0)))),
    'mand_l': R(lambda u: (0, -12 * max(0, s(u, 3)), 0), L), 'mand_r': R(lambda u: (0, 12 * max(0, s(u, 3)), 0), L),
    'body': S(lambda u: (1 + 0.03 * s(u, 2), 1 + 0.05 * s(u, 2), 1), L),
})
L = 1.0
replace_clip('spark_mite', 'ability', L, False, {
    'head': rot(keys3((0, (0, 0, 0)), (0.15, (28, 0, 0)), (0.85, (28, 0, 0)), (1.0, (0, 0, 0)))),
    'mand_l': R(lambda u: (0, -20 * max(0, s(u, 5)), 0), L), 'mand_r': R(lambda u: (0, 20 * max(0, s(u, 5)), 0), L),
    'shell_l': rot(keys3((0, (0, 0, 0)), (0.2, (0, 0, -35)), (0.7, (0, 0, -30)), (1.0, (0, 0, 0)))),
    'shell_r': rot(keys3((0, (0, 0, 0)), (0.2, (0, 0, 35)), (0.7, (0, 0, 30)), (1.0, (0, 0, 0)))),
    'body': merge(R(lambda u: (6 * min(1, u * 6) * min(1, (1 - u) * 6), 0, 0), L), S(lambda u: (1 + 0.06 * s(u, 5), 1, 1 + 0.06 * s(u, 5)), L)),
})
add_clip('spark_mite', 'hurt', hurt('body', 'head', 14), 0.4, False)

# ---- Lamp Moth: a moth of rusted foil with a glowing abdomen, always fluttering
mob('lamp_moth', [
    part('body', (0, 16, 0), [cube((-1.5, 14, -3), (3, 3, 6), 'rust')]),
    part('abdomen', (0, 15.5, 3), [cube((-1.5, 14, 3), (3, 3, 5), 'bellows'), cube((-1, 14.5, 6), (2, 2, 2), 'ember')], parent='body'),
    part('head', (0, 15.5, -3), [cube((-1.5, 14, -5.5), (3, 3, 2.5), 'dark_iron', {'front': 'eyes2'})], parent='body'),
    part('ant_l', (0.8, 14, -5), [cube((0.5, 10, -6), (1, 4, 1), 'brass')], parent='head', rot=(-25, 0, 20)),
    part('ant_r', (-0.8, 14, -5), [cube((-1.5, 10, -6), (1, 4, 1), 'brass')], parent='head', rot=(-25, 0, -20)),
    part('wing_l', (1.5, 14.5, 0), [cube((1.5, 14, -4), (9, 1, 8), 'brass', {'top': 'veins'})], parent='body'),
    part('wing_r', (-1.5, 14.5, 0), [cube((-10.5, 14, -4), (9, 1, 8), 'brass', {'top': 'veins'})], parent='body'),
    part('hind_l', (1.5, 15, 2), [cube((1.5, 14.5, 2), (6, 1, 5), 'copper')], parent='body'),
    part('hind_r', (-1.5, 15, 2), [cube((-7.5, 14.5, 2), (6, 1, 5), 'copper')], parent='body'),
], {}, look=('head',), shadow=0.2)
for _clip, L, _amp in (('walk', 0.3, 55), ('idle', 0.45, 45)):
    replace_clip('lamp_moth', _clip, L, True, {
        'wing_l': R(lambda u, a=_amp: (0, 0, -a * s(u) - 10), L, 8), 'wing_r': R(lambda u, a=_amp: (0, 0, a * s(u) + 10), L, 8),
        'hind_l': R(lambda u, a=_amp: (0, 0, -a * 0.7 * s(u, 1, -0.1) - 5), L, 8),
        'hind_r': R(lambda u, a=_amp: (0, 0, a * 0.7 * s(u, 1, -0.1) + 5), L, 8),
        'body': P(lambda u: (0, 1.2 * s(u, 1, 0.25), 0), L, 8),
        'abdomen': R(lambda u: (6 * s(u, 1, 0.4), 0, 0), L, 8),
    })
L = 1.2
replace_clip('lamp_moth', 'ability', L, False, {
    'wing_l': R(lambda u: (0, 0, -60 * s(u, 6) - 10), L, 48), 'wing_r': R(lambda u: (0, 0, 60 * s(u, 6) + 10), L, 48),
    'body': R(lambda u: (-15 * s(u), 360 * u, 20 * s(u, 2)), L, 24),
    'ant_l': R(lambda u: (15 * s(u, 3), 0, 0), L), 'ant_r': R(lambda u: (15 * s(u, 3, 0.2), 0, 0), L),
})
add_clip('lamp_moth', 'hurt', hurt('body', 'head', 20), 0.4, False)

# ---- Scrap Jackal: a lean pack hunter of dark iron and rusted plates, a cable tail with a live tip
mob('scrap_jackal', [
    part('body', (0, 12, 0), [
        cube((-3, 8, -6), (6, 6, 12), 'rust', {'top': 'rivets'}),
        cube((-3.5, 9, -3), (7, 4, 5), 'dark_iron', {'right': 'ribs', 'left': 'ribs'}),
        cube((-0.5, 7, -5), (1, 1, 10), 'cable'),
    ]),
    part('head', (0, 10, -6), [
        cube((-3, 5, -11), (6, 5, 5), 'dark_iron', {'front': 'eyes2'}),
        cube((-1.5, 6.5, -15), (3, 2, 4), 'rust'),
    ], parent='body'),
    part('jaw', (0, 9.5, -11), [cube((-1.5, 8.5, -15), (3, 1.5, 4), 'iron', {'top': 'teeth'})], parent='head'),
    part('ear_l', (2, 5, -8), [cube((1, 2, -9), (2, 3, 1), 'rust')], parent='head', rot=(0, 0, 10)),
    part('ear_r', (-2, 5, -8), [cube((-3, 2, -9), (2, 3, 1), 'rust')], parent='head', rot=(0, 0, -10)),
    part('tail', (0, 9, 6), [cube((-0.5, 8.5, 6), (1, 1, 7), 'cable'), cube((-1, 8, 12), (2, 2, 2), 'redstone')], parent='body', rot=(25, 0, 0)),
    part('leg_fl', (2, 13, -4), [cube((1, 13, -5), (2, 9, 2), 'dark_iron'), cube((0.5, 22, -5.5), (3, 2, 3), 'rust')]),
    part('leg_fr', (-2, 13, -4), [cube((-3, 13, -5), (2, 9, 2), 'dark_iron'), cube((-3.5, 22, -5.5), (3, 2, 3), 'rust')]),
    part('leg_bl', (2, 13, 4), [cube((1, 13, 3), (2, 9, 2), 'dark_iron'), cube((0.5, 22, 2.5), (3, 2, 3), 'rust')]),
    part('leg_br', (-2, 13, 4), [cube((-3, 13, 3), (2, 9, 2), 'dark_iron'), cube((-3.5, 22, 2.5), (3, 2, 3), 'rust')]),
], {}, shadow=0.45)
L = 0.6
replace_clip('scrap_jackal', 'walk', L, True, {
    'leg_fl': R(lambda u: (gait_value(u, 30, 0.55), 0, 0), L), 'leg_br': R(lambda u: (gait_value(u + 0.05, 30, 0.55), 0, 0), L),
    'leg_fr': R(lambda u: (gait_value(u + 0.5, 30, 0.55), 0, 0), L), 'leg_bl': R(lambda u: (gait_value(u + 0.55, 30, 0.55), 0, 0), L),
    'body': merge(P(lambda u: (0, -0.8 * abs(s(u)), 0), L), R(lambda u: (2 * s(u, 2), 0, 2 * s(u)), L)),
    'head': R(lambda u: (4 * s(u, 2, 0.2), 3 * s(u), 0), L),
    'tail': R(lambda u: (5 * s(u, 2), 20 * s(u), 0), L),
    'ear_l': R(lambda u: (8 * s(u, 2, 0.3), 0, 0), L), 'ear_r': R(lambda u: (8 * s(u, 2, 0.35), 0, 0), L),
})
L = 3.5
replace_clip('scrap_jackal', 'idle', L, True, {
    'head': rot(keys3((0, (0, 0, 0)), (0.8, (25, 10, 0)), (1.1, (28, -5, 0)), (1.4, (25, 10, 0)), (1.8, (0, 0, 0)),
                      (2.6, (-8, -30, 0)), (3.2, (-8, -30, 0)), (3.5, (0, 0, 0)))),
    'ear_l': rot(keys3((0, (0, 0, 0)), (2.0, (0, 0, 0)), (2.1, (-20, 0, 0)), (2.3, (0, 0, 0)), (3.5, (0, 0, 0)))),
    'ear_r': rot(keys3((0, (0, 0, 0)), (2.6, (0, 0, 0)), (2.7, (-20, 0, 0)), (2.9, (0, 0, 0)), (3.5, (0, 0, 0)))),
    'tail': R(lambda u: (4 * s(u, 2), 12 * s(u, 3), 0), L),
    'jaw': rot(keys3((0, (0, 0, 0)), (0.9, (12, 0, 0)), (1.0, (0, 0, 0)), (1.15, (12, 0, 0)), (1.25, (0, 0, 0)), (3.5, (0, 0, 0)))),
    'body': S(lambda u: (1 + 0.03 * s(u, 3), 1 + 0.03 * s(u, 3), 1), L),
})
L = 0.5
replace_clip('scrap_jackal', 'attack', L, False, {
    'body': merge(P(lambda u: (0, 0, -3 * s(u * 0.5)), L), R(lambda u: (-10 * s(u * 0.5), 0, 0), L)),
    'head': rot(keys3((0, (0, 0, 0)), (0.12, (-20, 0, 0)), (0.25, (15, 0, 0)), (0.5, (0, 0, 0)))),
    'jaw': rot(keys3((0, (0, 0, 0)), (0.12, (40, 0, 0)), (0.22, (0, 0, 0)), (0.5, (0, 0, 0)))),
    'leg_fl': rot(keys3((0, (0, 0, 0)), (0.15, (-40, 0, 0)), (0.5, (0, 0, 0)))),
    'leg_fr': rot(keys3((0, (0, 0, 0)), (0.15, (-40, 0, 0)), (0.5, (0, 0, 0)))),
})
L = 1.0
replace_clip('scrap_jackal', 'ability', L, False, {
    'body': R(lambda u: (12 * min(1, u * 5) * min(1, (1 - u) * 5), 0, 0), L),
    'head': R(lambda u: (30 * min(1, u * 5) * min(1, (1 - u) * 5), 8 * s(u, 4), 0), L),
    'leg_fl': R(lambda u: (-45 * max(0, s(u, 5)), 0, 0), L, 40), 'leg_fr': R(lambda u: (-45 * max(0, s(u, 5, 0.5)), 0, 0), L, 40),
    'tail': R(lambda u: (10, 30 * s(u, 4), 0), L),
    'jaw': R(lambda u: (15 * max(0, s(u, 4)), 0, 0), L),
})
add_clip('scrap_jackal', 'hurt', merge(hurt('body', 'head', 12), {'tail': rot(keys((0, 0), (0.1, -30), (0.4, 0)))}), 0.4, False)
