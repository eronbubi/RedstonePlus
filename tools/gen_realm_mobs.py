"""
Textures and model files of the realm creatures, from tools/realm_mobs.py.

For every creature it writes
  - src/main/resources/assets/redstoneplus/textures/entity/realm/NAME.png       (painted box-UV texture)
  - src/main/resources/assets/redstoneplus/textures/entity/realm/NAME_glow.png  (only the glowing pixels)
  - tools/blender/specs/NAME.json   (seed for Blender: parts, cubes with UVs, materials, animations)
  - src/main/resources/assets/redstoneplus/realm_models/NAME.json  (what the game loads) - but only when
    there is no tools/blender/NAME.blend yet; once the .blend exists, export_mobs.py writes this file from it.

    python tools/gen_realm_mobs.py
"""
import json
import math
import os
import random
import sys
import zlib

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from realm_mobs import MOBS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'redstoneplus')
TEX = os.path.join(ROOT, 'textures', 'entity', 'realm')
MODELS = os.path.join(ROOT, 'realm_models')
SPECS = os.path.join(HERE, 'blender', 'specs')
BLENDS = os.path.join(HERE, 'blender')


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# material: base colour, second colour, pattern, glows
MATERIALS = {
    'karst': ('#d6c6a2', '#e8943e', 'lichen', False),
    'dark_stone': ('#7a746c', '#9a928a', 'stone', False),
    'rust': ('#a8582a', '#e08a3a', 'rust', False),
    'iron': ('#a2a4ae', '#d4d6e0', 'metal', False),
    'dark_iron': ('#5a5c68', '#8a8c9a', 'metal', False),
    'brass': ('#d09a44', '#ffd27a', 'metal', False),
    'bell': ('#c07a34', '#ffc070', 'metal', False),
    'copper': ('#d87a44', '#ffaa70', 'metal', False),
    'copper_ox': ('#48a890', '#d8804a', 'patina', False),
    'chain': ('#8a2626', '#d04040', 'chain', False),
    'cable': ('#3a2626', '#d02020', 'cable', False),
    'furnace': ('#5a5450', '#3c3836', 'bricks', False),
    'wood': ('#8a6238', '#604224', 'planks', False),
    'wire': ('#eeeee6', '#b0b0a4', 'stripes', False),
    'flesh': ('#d8928a', '#a24a4a', 'flesh', False),
    'zombie': ('#6e9446', '#4a6e2c', 'flesh', False),
    'pig': ('#f0b4aa', '#c87a70', 'flesh', False),
    'ender': ('#2e2438', '#46385a', 'stone', False),
    'bone': ('#ece4cc', '#bcb49a', 'stone', False),
    'egg': ('#a4bade', '#6a7ca4', 'flesh', False),
    'crystal': ('#b4e4ff', '#f0faff', 'crystal', False),
    'slime': ('#d83a5a', '#ff7a90', 'slime', False),
    'bellows': ('#8a6238', '#ffa030', 'bellows', False),
    'cage': ('#3a3a44', '#6a6a78', 'cage', False),
    'ember': ('#ff9a2a', '#ffe070', 'glow', True),
    'redstone': ('#ff2a1a', '#ff8060', 'glow', True),
    'teal_glow': ('#50eaff', '#d0ffff', 'glow', True),
    'purple_glow': ('#c060ff', '#f0c8ff', 'glow', True),
    'uranium': ('#8aff44', '#e0ffa0', 'glow', True),
    'ruby_glow': ('#ff3a5a', '#ffb0bc', 'glow', True),
}


class Painter:
    def __init__(self, w, h, seed):
        self.img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.glow = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.gx = self.glow.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, rgb, glow=False, alpha=255):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            c = tuple(max(0, min(255, int(v))) for v in rgb)
            self.px[x, y] = c + (alpha,)
            if glow:
                self.gx[x, y] = c + (255,)
            else:
                self.gx[x, y] = (0, 0, 0, 0)

    def face(self, x0, y0, w, h, mat, deco=None, light=1.0):
        base, second, pattern, glows = MATERIALS[mat]
        b, s = hexrgb(base), hexrgb(second)
        r = self.rnd
        for dy in range(h):
            for dx in range(w):
                n = r.uniform(-1, 1)
                c = [v * (1 + 0.09 * n) for v in b]
                alpha = 255
                edge = dx == 0 or dy == 0 or dx == w - 1 or dy == h - 1
                if pattern == 'lichen':
                    if r.random() < 0.10 + (0.25 if dy < 2 else 0):
                        c = [v * (1 + 0.1 * n) for v in s]
                elif pattern == 'rust':
                    if r.random() < 0.2:
                        c = [v * (1 + 0.1 * n) for v in s]
                    if (dx * 7 + dy * 3) % 11 == 0:
                        c = [v * 0.72 for v in c]
                    if dx % 4 == 1 and r.random() < 0.3:
                        c = [v * 0.8 for v in c]
                elif pattern == 'metal':
                    if (dx + dy) % 7 == 0:
                        c = [v * 0.88 for v in c]
                    if abs(dx - dy - w // 3) <= 0:
                        c = [v * 0.25 + s_ * 0.8 for v, s_ in zip(c, s)]
                    if r.random() < 0.04:
                        c = [v * 0.7 + 30 for v in c]
                elif pattern == 'patina':
                    if r.random() < 0.12:
                        c = list(s)
                elif pattern == 'chain':
                    if (dx + dy) % 3 == 0:
                        c = [v * 1.35 for v in c]
                    elif (dx - dy) % 3 == 0:
                        c = [v * 0.7 for v in c]
                elif pattern == 'cable':
                    if dx % 3 == 1:
                        c = list(s)
                elif pattern == 'bricks':
                    row = dy // 3
                    if dy % 3 == 0 or (dx + (row % 2) * 3) % 6 == 0:
                        c = [v * 0.65 for v in c]
                elif pattern == 'planks':
                    if dy % 4 == 0:
                        c = list(s)
                elif pattern == 'stripes':
                    if dy % 2 == 0:
                        c = list(s)
                elif pattern == 'flesh':
                    if r.random() < 0.12:
                        c = [v * (1 + 0.08 * n) for v in s]
                    elif r.random() < 0.06:
                        c = [min(255, v * 1.2 + 15) for v in c]
                    if (dx * 5 + dy * 7) % 17 == 0:
                        c = [v * 0.8 for v in c]
                elif pattern == 'crystal':
                    if (dx + dy) % 4 == 0:
                        c = list(s)
                elif pattern == 'slime':
                    cx, cy = (w - 1) / 2, (h - 1) / 2
                    d = math.hypot(dx - cx, dy - cy) / max(1, max(w, h) / 2)
                    c = [v * (1.25 - 0.4 * d) for v in b]
                elif pattern == 'bellows':
                    if dy % 2 == 0:
                        c = [v * 0.6 for v in b]
                    elif r.random() < 0.15:
                        self.put(x0 + dx, y0 + dy, s, glow=True)
                        continue
                elif pattern == 'cage':
                    if dx % 3 != 0 and dy % 3 != 0 and not edge:
                        self.put(x0 + dx, y0 + dy, (0, 0, 0), alpha=0)
                        self.gx[x0 + dx, y0 + dy] = (0, 0, 0, 0)
                        continue
                elif pattern == 'glow':
                    if r.random() < 0.25:
                        c = list(s)
                if pattern not in ('glow', 'cage'):
                    c = [v * light for v in c]
                    # bevel: light catches the top/left edge, the bottom/right edge falls into shadow
                    if dy == 0 or dx == 0:
                        c = [v * 1.18 + 8 for v in c]
                    elif dy == h - 1 or dx == w - 1:
                        c = [v * 0.7 for v in c]
                self.put(x0 + dx, y0 + dy, c, glow=glows, alpha=alpha)
        if deco:
            self.decorate(x0, y0, w, h, deco)

    # ---------------------------------------------------------------- decorations
    def decorate(self, x0, y0, w, h, deco):
        red, orange, white = (255, 40, 30), (255, 150, 40), (235, 230, 220)
        cx, cy = x0 + w // 2, y0 + h // 2
        r = self.rnd

        def dot(x, y, col, glow=True, size=1):
            for a in range(size):
                for b in range(size):
                    self.put(x + a, y + b, col, glow=glow)

        if deco in ('eyes2', 'eyes3', 'eyes4', 'eye1', 'eyes_purple'):
            col = (190, 90, 255) if deco == 'eyes_purple' else red if deco != 'eyes4' else orange
            n = {'eyes2': 2, 'eyes3': 3, 'eyes4': 4, 'eye1': 1, 'eyes_purple': 2}[deco]
            size = 2 if w >= 8 else 1
            ey = y0 + max(1, h // 3)
            span = w - 2
            for i in range(n):
                ex = x0 + 1 + int((i + 0.5) * span / n) - size // 2
                dot(ex, ey, col, size=size)
                if deco == 'eyes_purple':
                    dot(ex - 1, ey, (120, 40, 200))
                    dot(ex + size, ey, (120, 40, 200))
        elif deco == 'eyes6':
            for i, (ex, ey) in enumerate([(-3, -1), (2, -1), (-1, -2), (0, -2), (-4, 1), (3, 1)]):
                dot(cx + ex, cy + ey, red)
        elif deco in ('grill', 'grill_small'):
            step = 2
            for yy in range(y0 + 2, y0 + h - 1, step):
                for xx in range(x0 + 2, x0 + w - 2):
                    self.put(xx, yy, orange if r.random() > 0.2 else (255, 220, 120), glow=True)
        elif deco == 'core':
            for dy in range(-3, 4):
                for dx in range(-3, 4):
                    if abs(dx) + abs(dy) <= 3:
                        self.put(cx + dx, cy + dy, red if abs(dx) + abs(dy) < 3 else (140, 10, 10), glow=abs(dx) + abs(dy) < 3)
        elif deco == 'teeth':
            for xx in range(x0 + 1, x0 + w - 1, 2):
                self.put(xx, y0 + h - 1, white)
                self.put(xx, y0 + h - 2, white)
                self.put(xx + 1, y0 + h - 1, white)
        elif deco == 'rivets':
            for xx, yy in [(x0 + 1, y0 + 1), (x0 + w - 2, y0 + 1), (x0 + 1, y0 + h - 2), (x0 + w - 2, y0 + h - 2)]:
                self.put(xx, yy, (190, 190, 180))
        elif deco == 'lichen':
            for _ in range(w * h // 6):
                self.put(x0 + r.randrange(w), y0 + r.randrange(max(1, h // 2)), (216, 136, 58))
        elif deco == 'veins':
            for _ in range(3):
                xx, yy = x0 + r.randrange(w), y0 + r.randrange(h)
                for _ in range(max(w, h)):
                    self.put(xx, yy, (110, 20, 25))
                    xx += r.choice((-1, 0, 1))
                    yy += 1
                    if not (x0 <= xx < x0 + w and y0 <= yy < y0 + h):
                        break
        elif deco == 'cage_glow':
            for dy in range(1, h - 1):
                for dx in range(1, w - 1):
                    if dx % 3 == 0 or dy % 3 == 0:
                        self.put(x0 + dx, y0 + dy, (40, 40, 44))
                    else:
                        self.put(x0 + dx, y0 + dy, (125, 255, 58) if r.random() > 0.3 else (200, 255, 150), glow=True)
        elif deco == 'bars':
            for dx in range(1, w - 1, 2):
                for dy in range(h):
                    self.put(x0 + dx, y0 + dy, (40, 36, 34))
        elif deco == 'face_zombie':
            dot(x0 + 1, cy - 1, (20, 30, 10), glow=False, size=2)
            dot(x0 + w - 3, cy - 1, (20, 30, 10), glow=False, size=2)
            dot(x0 + 2, cy - 1, (125, 255, 58))
            dot(x0 + w - 2, cy - 1, (125, 255, 58))
            for xx in range(x0 + 2, x0 + w - 2):
                self.put(xx, y0 + h - 2, (40, 50, 20))
        elif deco == 'face_creeper':
            dot(x0 + 1, y0 + 2, red, size=2)
            dot(x0 + w - 3, y0 + 2, red, size=2)
            for dy, xs in enumerate([(cx - 1, cx), (cx - 2, cx + 1), (cx - 2, cx + 1)]):
                for xx in range(xs[0], xs[1] + 1):
                    self.put(xx, y0 + 5 + dy, (40, 10, 10))
        elif deco == 'face_pig':
            for dy in range(3):
                for dx in range(4):
                    self.put(cx - 2 + dx, y0 + h - 4 + dy, (240, 170, 160))
            self.put(cx - 1, y0 + h - 3, (90, 40, 40))
            self.put(cx, y0 + h - 3, (90, 40, 40))
            dot(x0 + 1, y0 + 2, (255, 140, 40))
            dot(x0 + w - 2, y0 + 2, (255, 140, 40))
        elif deco == 'face_cage':
            dot(x0 + 1, y0 + 3, red, size=2)
            dot(x0 + w - 3, y0 + 3, red, size=2)
        elif deco == 'bumps':
            for _ in range(w * h // 10):
                self.put(x0 + r.randrange(w), y0 + r.randrange(h), (170, 190, 220))
        elif deco == 'ribs':
            for yy in range(y0 + 1, y0 + h - 1):
                for xx in range(x0 + 1, x0 + w - 1):
                    if (yy - y0) % 2 == 1:
                        self.put(xx, yy, (230, 222, 200))
                    else:
                        self.put(xx, yy, orange if r.random() > 0.4 else (255, 90, 20), glow=True)
        elif deco == 'circuit':
            for _ in range(4):
                xx, yy = x0 + r.randrange(w), y0 + r.randrange(h)
                for _ in range(6):
                    self.put(xx, yy, (190, 90, 255), glow=True)
                    if r.random() < 0.5:
                        xx = min(x0 + w - 1, max(x0, xx + r.choice((-1, 1))))
                    else:
                        yy = min(y0 + h - 1, max(y0, yy + r.choice((-1, 1))))
        elif deco == 'ember_top':
            dot(cx - 1, cy - 1, orange, size=2)
        elif deco == 'bubbles':
            for _ in range(3):
                xx, yy = x0 + 1 + r.randrange(max(1, w - 3)), y0 + 1 + r.randrange(max(1, h - 3))
                self.put(xx, yy, (255, 150, 170))
                self.put(xx + 1, yy, (255, 190, 200))


def footprint(c):
    w, h, d = (int(math.ceil(v)) for v in c['size'])
    return 2 * d + 2 * w, d + h, (w, h, d)


def pack(mob):
    """Shelf packing of every cube's box UV. Returns texture size."""
    width = mob['texture_width']
    cubes = [c for p in mob['parts'] for c in p['cubes']]
    width = max(width, max((footprint(c)[0] for c in cubes), default=16))
    order = sorted(cubes, key=lambda c: -footprint(c)[1])
    x = y = shelf = 0
    for c in order:
        fw, fh, _ = footprint(c)
        if x + fw > width:
            x, y = 0, y + shelf
            shelf = 0
        c['uv'] = [x, y]
        x += fw
        shelf = max(shelf, fh)
    height = y + shelf
    height = max(16, int(math.ceil(height / 16.0)) * 16)
    return width, height


def paint(name, mob, tw, th):
    p = Painter(tw, th, zlib.crc32(name.encode()))
    for part in mob['parts']:
        for c in part['cubes']:
            u, v = c['uv']
            w, h, d = footprint(c)[2]
            faces = c['faces']
            mat = c['mat']
            p.face(u + d, v, w, d, mat, faces.get('top'), 1.15)
            p.face(u + d + w, v, w, d, mat, faces.get('bottom'), 0.72)
            p.face(u, v + d, d, h, mat, faces.get('right'), 0.92)
            p.face(u + d, v + d, w, h, mat, faces.get('front'), 1.0)
            p.face(u + d + w, v + d, d, h, mat, faces.get('left'), 0.92)
            p.face(u + 2 * d + w, v + d, w, h, mat, faces.get('back'), 0.85)
    os.makedirs(TEX, exist_ok=True)
    p.img.save(os.path.join(TEX, name + '.png'))
    p.glow.save(os.path.join(TEX, name + '_glow.png'))


def relative(mob):
    """Pivots relative to the parent, cube origins relative to the part pivot (what Minecraft wants)."""
    absolute = {'root': [0.0, 0.0, 0.0]}
    parts = []
    for p in mob['parts']:
        absolute[p['name']] = p['pivot']
        par = absolute[p['parent']] if p['parent'] in absolute else [0.0, 0.0, 0.0]
        parts.append({
            'name': p['name'], 'parent': p['parent'],
            'pivot': [round(p['pivot'][i] - par[i], 3) for i in range(3)],
            'rotation': p['rot'],
            'cubes': [{'uv': c['uv'], 'origin': [round(c['from'][i] - p['pivot'][i], 3) for i in range(3)],
                       'size': c['size'], 'inflate': c['inflate'], 'mirror': c['mirror'], 'mat': c['mat']} for c in p['cubes']],
        })
    return parts


def main():
    os.makedirs(SPECS, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for name, mob in MOBS.items():
        tw, th = pack(mob)
        paint(name, mob, tw, th)
        spec = {'name': name, 'texture_size': [tw, th], 'parts': relative(mob), 'animations': mob['animations'],
                'look': mob['look'], 'shadow': mob['shadow'], 'scale': mob['scale']}
        with open(os.path.join(SPECS, name + '.json'), 'w', encoding='utf-8') as f:
            json.dump(spec, f, indent=1)
        if not os.path.exists(os.path.join(BLENDS, name + '.blend')):
            game = dict(spec)
            game['parts'] = [{k: v for k, v in part.items()} for part in spec['parts']]
            for part in game['parts']:
                part['cubes'] = [{k: v for k, v in c.items() if k != 'mat'} for c in part['cubes']]
            with open(os.path.join(MODELS, name + '.json'), 'w', encoding='utf-8') as f:
                json.dump(game, f, separators=(',', ':'))
        print(f'{name}: {len(mob["parts"])} parts, texture {tw}x{th}, clips {sorted(mob["animations"])}')


if __name__ == '__main__':
    main()
