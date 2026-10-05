"""
The Redstone Realm's colour rule: everything in the realm's environment stays on the warm side of the hue wheel,
red through orange to yellow. No blue, cyan, green or violet skies, water, fog, plants or ground.

    python tools/realm_palette.py            checks every realm biome (sky, fog, water, water fog, grass, foliage,
                                             particle) and the realm's environment textures; exits 1 on a stray colour
    python tools/realm_palette.py --rehue    moves the environment textures listed in REHUE onto their warm target
                                             hue in place (keeps each pixel's saturation and brightness), for when the
                                             vanilla jar that gen_realm.py recolours from is not at hand

gen_realm.py writes the biomes; run this after it.
"""
import colorsys
import glob
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
BIOMES = os.path.join(RES, 'data', 'redstoneplus', 'worldgen', 'biome')
BLOCK_TEX = os.path.join(RES, 'assets', 'redstoneplus', 'textures', 'block')

# red (wrapping round from magenta-red) .. orange .. yellow
HUE_MIN = 340.0
HUE_MAX = 58.0

# environment textures and the warm colour gen_realm.py now gives them
REHUE = {
    'canal_moss': '#c8702a', 'grove_moss': '#8a2a2a', 'frost_realmstone': '#c8a090', 'tempest_basalt': '#ff7a30',
    'resonant_crystal': '#ffb030', 'decoy_beacon_core': '#ffb030', 'decoy_beacon_side': '#ffb030', 'floodgate_front_open': '#ff8a3a',
    'floodgate_front_locked': '#d87a3a', 'briar_thorns': '#5a3a22', 'frost_fern': '#f4dccf',
}
# environment textures checked by the rule (ground, rock, plants, crystals of the realm)
ENVIRONMENT = sorted(set(REHUE) | {
    'realmstone', 'deep_realmstone', 'realmstone_bricks', 'hematite', 'dark_hematite', 'cinder_rock', 'fossil_circuit', 'redstone_vein',
    'salt_crust', 'rust_plating', 'rust_sand', 'red_clay', 'fen_mud', 'briar_soil', 'heather_turf', 'root_soil', 'crimson_heather', 'fen_reed',
    'red_coral_shrub', 'pale_stalk', 'salt_brush', 'copper_reed', 'lichen_tuft', 'cinder_bloom', 'redstone_cluster', 'lightline', 'grid_beacon',
    'blight_crust', 'quarantine_plating', 'chain_link', 'dawn_lily', 'echo_seal_side'} | {f'echo_seal_{e}' for e in ('force', 'signal', 'resonance', 'heat', 'flow')}
    | {f'{l}_{p}' for l in ('molten_redstone', 'ember_oil', 'rust_brine', 'resonant_ichor', 'blight_sap', 'dawn_nectar') for p in ('still', 'flow')})


def hue_ok(h_deg):
    return h_deg >= HUE_MIN or h_deg <= HUE_MAX


def check_colour(rgb):
    """True if an RGB colour (0..255) is warm, or so dark/grey that it carries no hue at all."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255.0 for c in rgb))
    if v < 0.12 or s < 0.08:
        return True
    return hue_ok(h * 360.0)


def int_rgb(i):
    return (i >> 16) & 255, (i >> 8) & 255, i & 255


def realm_biomes():
    """The biomes the realm's dimension actually places (the mod's Overworld and End biomes are not the realm's)."""
    dim = json.load(open(os.path.join(RES, 'data', 'redstoneplus', 'dimension', 'redstone_realm.json'), encoding='utf-8'))
    return sorted({p['biome'].split(':')[1] for p in dim['generator']['biome_source']['biomes']})


def check_biomes():
    bad = []
    for name in realm_biomes():
        path = os.path.join(BIOMES, name + '.json')
        effects = json.load(open(path, encoding='utf-8'))['effects']
        for key in ('sky_color', 'fog_color', 'water_color', 'water_fog_color', 'grass_color', 'foliage_color'):
            if key in effects and not check_colour(int_rgb(effects[key])):
                bad.append(f'{name}: {key} #{effects[key]:06x}')
        particle = effects.get('particle', {}).get('options', {})
        cold = {'minecraft:electric_spark', 'minecraft:warped_spore', 'minecraft:glow', 'minecraft:soul_fire_flame', 'minecraft:soul',
                'minecraft:dripping_water', 'minecraft:nautilus', 'minecraft:glow_squid_ink', 'minecraft:snowflake'}
        if particle.get('type') in cold:
            bad.append(f'{name}: particle {particle["type"]}')
        if particle.get('type') == 'minecraft:dust' and not check_colour(tuple(round(c * 255) for c in particle['color'])):
            bad.append(f'{name}: dust particle {particle["color"]}')
    return bad


def texture_share_off(path):
    img = Image.open(path).convert('RGBA')
    px = img.load()
    total = off = 0
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a < 128:
                continue
            total += 1
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s > 0.2 and v > 0.15 and not hue_ok(h * 360.0):
                off += 1
    return off / max(1, total)


def check_textures():
    bad = []
    for name in ENVIRONMENT:
        path = os.path.join(BLOCK_TEX, name + '.png')
        if os.path.exists(path):
            share = texture_share_off(path)
            if share > 0.05:
                bad.append(f'texture {name}: {share:.0%} of its pixels are off the warm hues')
    return bad


def rehue(path, target):
    th, ts, tv = colorsys.rgb_to_hsv(*(int(target.lstrip('#')[i:i + 2], 16) / 255.0 for i in (0, 2, 4)))
    img = Image.open(path).convert('RGBA')
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s < 0.06 or hue_ok(h * 360.0):
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(th, min(1.0, max(s, ts * 0.6)), v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    img.save(path)


def main():
    if '--rehue' in sys.argv:
        for name, target in REHUE.items():
            path = os.path.join(BLOCK_TEX, name + '.png')
            if os.path.exists(path):
                rehue(path, target)
                print('rehued', name)
    bad = check_biomes() + check_textures()
    for line in bad:
        print('OFF PALETTE', line)
    print(f'realm palette: {len(bad)} problems')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
