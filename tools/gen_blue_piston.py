# Makes the Sticky Super Piston face: the vanilla sticky piston top with the green slime turned blue.
import colorsys, os
from PIL import Image

here = os.path.dirname(__file__)
src = Image.open(os.path.join(here, 'piston_top_sticky_vanilla.png')).convert('RGBA')
out = src.copy()
px = out.load()
for y in range(out.height):
    for x in range(out.width):
        r, g, b, a = px[x, y]
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        # only the green slime pixels, the wood/stone stays untouched
        if s > 0.15 and 0.17 < h < 0.5:
            h = (h + 1 / 3) % 1.0  # green (120 deg) -> blue (240 deg)
            nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
dst = os.path.join(here, '..', 'src', 'main', 'resources', 'assets', 'redstoneplus', 'textures', 'block', 'super_piston_top_sticky.png')
out.save(dst)
print('saved', dst)
