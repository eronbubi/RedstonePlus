"""
How the realm's creatures look once the realm is freed (see RealmStory): every creature texture in
assets/redstoneplus/textures/entity/realm/NAME.png gets a healed twin in .../realm/healed/NAME.png.

The healed look: shadows lifted and the colours warmed (rust and grey iron turn to warm copper and rose), and the
machine grown over like an old wall in spring: crimson ivy in patches and runners, with blossoms of gold and coral.
Still red: everything stays on the warm side of the hue wheel (see realm_palette.py).

    python tools/gen_realm_healed.py
"""
import colorsys
import glob
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'redstoneplus', 'textures', 'entity', 'realm')
OUT = os.path.join(TEX, 'healed')

LEAF = [(122, 26, 24), (168, 40, 34), (206, 66, 46), (232, 104, 64)]
BLOSSOM = [(255, 214, 96), (255, 176, 140), (255, 236, 170), (255, 132, 96)]


def smooth_noise(w, h, cell, rnd):
    """Value noise in 0..1 with cells of the given size (bilinear between random corners)."""
    gw, gh = w // cell + 2, h // cell + 2
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]

    def at(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx), int(gy)
        fx, fy = gx - x0, gy - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = grid[y0][x0] * (1 - fx) + grid[y0][x0 + 1] * fx
        b = grid[y0 + 1][x0] * (1 - fx) + grid[y0 + 1][x0 + 1] * fx
        return a * (1 - fy) + b * fy
    return at


def warm(r, g, b):
    """Lift the shadows, warm the greys, keep every hue between red and yellow."""
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    deg = h * 360
    if s < 0.18:
        deg, s = 18.0, max(s, 0.22)  # grey iron becomes warm copper
    elif not (deg >= 340 or deg <= 58):
        deg = 20.0
    v = min(1.0, v * 0.78 + 0.24)
    s = min(1.0, s * 0.9 + 0.06)
    nr, ng, nb = colorsys.hsv_to_rgb((deg % 360) / 360, s, v)
    return int(nr * 255), int(ng * 255), int(nb * 255)


def heal(img, seed):
    img = img.convert('RGBA')
    w, h = img.size
    rnd = random.Random(seed)
    out = img.copy()
    px = out.load()
    src = img.load()
    scale = max(1, w // 64)
    patches = smooth_noise(w, h, 10 * scale, rnd)
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            col = warm(r, g, b)
            n = patches(x, y)
            if n > 0.68:
                # a patch of ivy: leaves of four shades in little clumps
                k = int((n - 0.68) / 0.32 * 3.99)
                col = LEAF[min(3, k + (1 if rnd.random() < 0.3 else 0))]
            elif n > 0.62 and rnd.random() < 0.5:
                col = LEAF[0]
            px[x, y] = col + (a,)
    # runners of ivy wandering across the body
    for _ in range(w * h // (220 * scale * scale)):
        x, y = rnd.randrange(w), rnd.randrange(h)
        for step in range(rnd.randint(6, 18) * scale):
            if 0 <= x < w and 0 <= y < h and src[x, y][3]:
                px[x, y] = LEAF[1 if step % 3 else 2] + (src[x, y][3],)
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((-1, 1, 1))
    # blossoms: a bright centre with four petals, on the leaves
    for _ in range(w * h // (90 * scale * scale)):
        x, y = rnd.randrange(1, w - 1), rnd.randrange(1, h - 1)
        if patches(x, y) < 0.6 or not src[x, y][3]:
            continue
        petal = BLOSSOM[rnd.randrange(len(BLOSSOM))]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if src[x + dx, y + dy][3]:
                px[x + dx, y + dy] = petal + (src[x + dx, y + dy][3],)
        px[x, y] = (255, 246, 210, src[x, y][3])
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    count = 0
    for path in sorted(glob.glob(os.path.join(TEX, '*.png'))):
        name = os.path.basename(path)[:-4]
        if name.endswith('_glow') or name == 'light_cycle':
            continue
        seed = sum(ord(c) * 31 ** i for i, c in enumerate(name)) % 2 ** 31
        heal(Image.open(path), seed).save(os.path.join(OUT, name + '.png'))
        count += 1
    print(f'{count} healed creature textures')


if __name__ == '__main__':
    main()
