"""
Assets and data of the Redstone Realm: blocks, items, loot, recipes, tags, the dimension with its six biomes,
world generation features and all texts (English and German).

Run after the other generators (gen_textures.js, gen_data.js, gen_content.py):
    python tools/gen_realm.py
    python tools/gen_realm_mobs.py      (creature textures / model seeds)
    python tools/gen_realm_sounds.py    (creature sounds)
Textures are recoloured from the vanilla jar in the Gradle cache like gen_content.py does.
"""
import colorsys
import glob
import io
import json
import os
import random
import zipfile

from PIL import Image

NS = 'redstoneplus'
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)
MC_DATA = os.path.join(ROOT, 'data', 'minecraft')

jars = glob.glob(os.path.expanduser('~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/1.21.1/client.jar'))
if not jars:
    raise SystemExit('Minecraft 1.21.1 client.jar not found in the Gradle cache - run a Gradle build once first')
JAR = zipfile.ZipFile(jars[0])
rnd = random.Random(20260926)
en, de = {}, {}


# ------------------------------------------------------------------------------------------ helpers
def vanilla(path):
    return Image.open(io.BytesIO(JAR.read('assets/minecraft/textures/' + path + '.png'))).convert('RGBA')


def vanilla_json(path):
    return json.loads(JAR.read(path))


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def save(img, kind, name):
    path = os.path.join(ASSETS, 'textures', kind, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def recolor(img, color, sat_min=0.15):
    th, ts, tv = colorsys.rgb_to_hsv(*rgb(color))
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < sat_min:
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(th, min(1, ts * (0.55 + 0.6 * s)), min(1, v * (0.55 + 0.6 * tv)))
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def tint(img, color, strength=1.0, gain=1.3):
    tr, tg, tb = rgb(color)
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255 * gain
            c = (min(1, lum * tr), min(1, lum * tg), min(1, lum * tb))
            px[x, y] = tuple(round((o / 255 * (1 - strength) + n * strength) * 255) for o, n in zip((r, g, b), c)) + (a,)
    return out


def specks(img, color, chance, rows=None):
    out = img.copy()
    px = out.load()
    c = tuple(round(v * 255) for v in rgb(color))
    for y in range(out.height):
        if rows and y not in rows:
            continue
        for x in range(out.width):
            if px[x, y][3] and rnd.random() < chance:
                px[x, y] = c + (255,)
    return out


def first_frame(img):
    return img.crop((0, 0, img.width, img.width)) if img.height > img.width else img


def sprite(rows, palette):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = tuple(int(palette[ch][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    return img


def name(key, e, g, desc_e=None, desc_g=None):
    en[key] = e
    de[key] = g
    if desc_e:
        en[key + '.desc'] = desc_e
        de[key + '.desc'] = desc_g


def model(path, obj):
    write(os.path.join(ASSETS, 'models', path + '.json'), obj)


def blockstate(block, obj):
    write(os.path.join(ASSETS, 'blockstates', block + '.json'), obj)


def item_model(item, parent=None, texture=None):
    if parent:
        model(f'item/{item}', {'parent': parent})
    else:
        model(f'item/{item}', {'parent': 'minecraft:item/generated', 'textures': {'layer0': texture or f'{NS}:item/{item}'}})


def simple_block(block, texture=None):
    model(f'block/{block}', {'parent': 'minecraft:block/cube_all', 'textures': {'all': texture or f'{NS}:block/{block}'}})
    blockstate(block, {'variants': {'': {'model': f'{NS}:block/{block}'}}})
    item_model(block, f'{NS}:block/{block}')


def self_drop(block):
    write(os.path.join(DATA, 'loot_table', 'blocks', block + '.json'), {
        'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{block}',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{block}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def shaped(name_, pattern, key, result, count=1):
    write(os.path.join(DATA, 'recipe', name_ + '.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'redstone', 'pattern': pattern,
        'key': {k: ({'tag': v[1:]} if v.startswith('#') else {'item': v}) for k, v in key.items()},
        'result': {'id': result, 'count': count}})


def shapeless(name_, ingredients, result, count=1):
    write(os.path.join(DATA, 'recipe', name_ + '.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'redstone',
        'ingredients': [({'tag': i[1:]} if i.startswith('#') else {'item': i}) for i in ingredients],
        'result': {'id': result, 'count': count}})


FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
pickaxe, shovel, needs_iron = [], [], []

# ================================================================================================ terrain blocks
limestone = tint(vanilla('block/calcite'), '#e4d2ae', gain=1.05)
save(limestone, 'block', 'karst_limestone')
lichen_top = specks(specks(limestone, '#d8883a', 0.45), '#a85a20', 0.12)
save(lichen_top, 'block', 'lichen_karst_top')
save(specks(specks(limestone, '#d8883a', 0.6, rows=range(0, 5)), '#d8883a', 0.2, rows=range(5, 8)), 'block', 'lichen_karst_side')
save(tint(vanilla('block/stone_bricks'), '#dcc8a0', gain=1.2), 'block', 'karst_bricks')
save(tint(vanilla('block/coarse_dirt'), '#b0482c', gain=1.4), 'block', 'rusted_soil')
save(specks(tint(vanilla('block/blackstone'), '#6a4a40', gain=1.6), '#e07a30', 0.04), 'block', 'slag')
save(recolor(vanilla('block/amethyst_block'), '#40e0ff'), 'block', 'resonant_crystal')
save(specks(tint(vanilla('block/tuff'), '#f0d840', gain=1.7), '#fff4a0', 0.08), 'block', 'sulfur_crust')
for b in ('karst_limestone', 'karst_bricks', 'rusted_soil', 'slag', 'resonant_crystal', 'sulfur_crust'):
    simple_block(b)
model('block/lichen_karst', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
    'top': f'{NS}:block/lichen_karst_top', 'side': f'{NS}:block/lichen_karst_side', 'bottom': f'{NS}:block/karst_limestone'}})
blockstate('lichen_karst', {'variants': {'': {'model': f'{NS}:block/lichen_karst'}}})
item_model('lichen_karst', f'{NS}:block/lichen_karst')
pickaxe += [f'{NS}:{b}' for b in ('karst_limestone', 'lichen_karst', 'karst_bricks', 'slag', 'resonant_crystal', 'sulfur_crust')]
shovel.append(f'{NS}:rusted_soil')

briar = specks(tint(vanilla('block/dead_bush'), '#4a5a2a', gain=1.6), '#c02020', 0.08)
save(briar, 'block', 'briar_thorns')
model('block/briar_thorns', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{NS}:block/briar_thorns'}})
blockstate('briar_thorns', {'variants': {'': {'model': f'{NS}:block/briar_thorns'}}})
item_model('briar_thorns', texture=f'{NS}:block/briar_thorns')

name(f'block.{NS}.karst_limestone', 'Karst Limestone', 'Karstkalk', 'Pale rock of the Piston Karst.', 'Heller Fels aus dem Kolbenkarst.')
name(f'block.{NS}.lichen_karst', 'Lichen Karst', 'Flechtenkarst', 'Karst limestone grown over with orange lichen.', 'Karstkalk mit orangen Flechten.')
name(f'block.{NS}.karst_bricks', 'Karst Bricks', 'Karstziegel', 'Building block from Karst Limestone.', 'Baublock aus Karstkalk.')
name(f'block.{NS}.rusted_soil', 'Rusted Soil', 'Rosterde', 'Iron-red ground of the Switchyard Flats.', 'Eisenroter Boden der Weichenebene.')
name(f'block.{NS}.slag', 'Slag', 'Schlacke', 'Leftovers of the realm\'s old smelters.', 'Reste der alten Schmelzen des Reichs.')
name(f'block.{NS}.resonant_crystal', 'Resonant Crystal', 'Resonanzkristall', 'Glows and chimes deep in the Resonance Hollows.', 'Leuchtet und klingt tief in den Resonanzhöhlen.')
name(f'block.{NS}.sulfur_crust', 'Sulfur Crust', 'Schwefelkruste', 'Yellow crust of the Kiln Barrens.', 'Gelbe Kruste der Brennofen-Öde.')
name(f'block.{NS}.briar_thorns', 'Briar Thorns', 'Dornengestrüpp', 'Slows and scratches whoever walks through it.', 'Bremst und kratzt jeden, der hindurchgeht.')

# ================================================================================================ realm gate
gate_side = tint(vanilla('block/polished_blackstone_bricks'), '#5a3a3a', gain=1.3)
px = gate_side.load()
for i in range(16):
    for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
        px[x, y] = (200, 30, 20, 255)
save(gate_side, 'block', 'realm_gate_side')
save(recolor(vanilla('block/respawn_anchor_top_off'), '#c02020', 0.05), 'block', 'realm_gate_top')
save(recolor(first_frame(vanilla('block/respawn_anchor_top')), '#ff4020', 0.05), 'block', 'realm_gate_top_on')
for lit, top in ((False, 'realm_gate_top'), (True, 'realm_gate_top_on')):
    model(f'block/realm_gate{"_on" if lit else ""}', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': f'{NS}:block/{top}', 'side': f'{NS}:block/realm_gate_side', 'bottom': 'minecraft:block/polished_blackstone'}})
blockstate('realm_gate', {'variants': {'lit=false': {'model': f'{NS}:block/realm_gate'}, 'lit=true': {'model': f'{NS}:block/realm_gate_on'}}})
item_model('realm_gate', f'{NS}:block/realm_gate_on')
pickaxe.append(f'{NS}:realm_gate')
needs_iron.append(f'{NS}:realm_gate')
name(f'block.{NS}.realm_gate', 'Realm Gate', 'Reichstor',
     'Give it a redstone signal, then right click to travel to the Redstone Realm and back.',
     'Mit Redstone-Signal versorgen, dann Rechtsklick: Reise ins Redstone-Reich und zurück.')

# ================================================================================================ tripper rail
save(recolor(vanilla('block/detector_rail'), '#e0a020', 0.3), 'block', 'tripper_rail')
save(recolor(vanilla('block/detector_rail_on'), '#ffd040', 0.3), 'block', 'tripper_rail_on')
vanilla_state = vanilla_json('assets/minecraft/blockstates/detector_rail.json')
for variant in vanilla_state['variants'].values():
    variant['model'] = variant['model'].replace('minecraft:block/detector_rail', f'{NS}:block/tripper_rail')
blockstate('tripper_rail', vanilla_state)
for suffix in ('', '_raised_ne', '_raised_sw', '_on', '_on_raised_ne', '_on_raised_sw'):
    m = vanilla_json(f'assets/minecraft/models/block/detector_rail{suffix}.json')
    m['textures'] = {k: v.replace('minecraft:block/detector_rail', f'{NS}:block/tripper_rail') for k, v in m['textures'].items()}
    model(f'block/tripper_rail{suffix}', m)
item_model('tripper_rail', texture=f'{NS}:block/tripper_rail')
name(f'block.{NS}.tripper_rail', 'Tripper Rail', 'Auslöseschiene',
     'Detector rail that also reacts to anything walking over it, not only minecarts.',
     'Sensorschiene, die auch auf alles reagiert, was darüber läuft, nicht nur auf Loren.')

# ================================================================================================ traps
def orientable_trap(block, side, top, front, front_firing, front_disarmed, extra_firing=None):
    for suffix, fr in (('', front), ('_firing', front_firing), ('_disarmed', front_disarmed)):
        m = {'parent': 'minecraft:block/orientable', 'textures': {'side': side, 'top': top, 'front': fr}}
        if suffix == '_firing' and extra_firing:
            m = extra_firing
        model(f'block/{block}{suffix}', m)
    variants = {}
    for facing, y in FACING_Y.items():
        for firing in ('false', 'true'):
            for disarmed in ('false', 'true'):
                suffix = '_disarmed' if disarmed == 'true' else '_firing' if firing == 'true' else ''
                v = {'model': f'{NS}:block/{block}{suffix}'}
                if y:
                    v['y'] = y
                variants[f'facing={facing},firing={firing},disarmed={disarmed}'] = v
    blockstate(block, {'variants': variants})
    item_model(block, f'{NS}:block/{block}')
    pickaxe.append(f'{NS}:{block}')


# crusher: piston textures in karst colours; fired it shows the ram half a block out in front
save(tint(vanilla('block/piston_side'), '#c8b490', gain=1.2), 'block', 'crusher_side')
save(tint(vanilla('block/piston_bottom'), '#9a8a70', gain=1.2), 'block', 'crusher_front')
save(tint(vanilla('block/piston_top'), '#8a5a3a', gain=1.3), 'block', 'crusher_ram')
jam = tint(vanilla('block/piston_bottom'), '#9a8a70', gain=1.2)
jp = jam.load()
for i in range(16):
    for w in (0, 1):
        jp[min(15, i + w), i] = (200, 150, 60, 255)
        jp[min(15, 15 - i + w), i] = (200, 150, 60, 255)
save(jam, 'block', 'crusher_front_jammed')
crusher_fired = {'parent': 'minecraft:block/block', 'textures': {
    'particle': f'{NS}:block/crusher_side', 'side': f'{NS}:block/crusher_side', 'front': f'{NS}:block/crusher_front',
    'ram': f'{NS}:block/crusher_ram'}, 'elements': [
    {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
        'north': {'texture': '#front', 'cullface': 'north'}, 'south': {'texture': '#side', 'cullface': 'south'},
        'east': {'texture': '#side', 'cullface': 'east'}, 'west': {'texture': '#side', 'cullface': 'west'},
        'up': {'texture': '#side', 'cullface': 'up'}, 'down': {'texture': '#side', 'cullface': 'down'}}},
    {'from': [6, 6, -6], 'to': [10, 10, 0], 'faces': {
        'east': {'texture': '#side', 'uv': [0, 6, 6, 10]}, 'west': {'texture': '#side', 'uv': [0, 6, 6, 10]},
        'up': {'texture': '#side', 'uv': [6, 0, 10, 6]}, 'down': {'texture': '#side', 'uv': [6, 0, 10, 6]}}},
    {'from': [1, 1, -9], 'to': [15, 15, -6], 'faces': {
        'north': {'texture': '#ram'}, 'south': {'texture': '#ram'},
        'east': {'texture': '#ram', 'uv': [0, 1, 3, 15]}, 'west': {'texture': '#ram', 'uv': [0, 1, 3, 15]},
        'up': {'texture': '#ram', 'uv': [1, 0, 15, 3]}, 'down': {'texture': '#ram', 'uv': [1, 0, 15, 3]}}}]}
orientable_trap('crusher', f'{NS}:block/crusher_side', f'{NS}:block/crusher_side', f'{NS}:block/crusher_front',
                f'{NS}:block/crusher_front', f'{NS}:block/crusher_front_jammed', crusher_fired)

# hazard switch: observer look with hazard stripes
hz_side = tint(vanilla('block/observer_side'), '#8a4a2a', gain=1.3)
save(hz_side, 'block', 'hazard_switch_side')
front = Image.new('RGBA', (16, 16))
fp = front.load()
for y in range(16):
    for x in range(16):
        fp[x, y] = (230, 170, 30, 255) if ((x + y) // 3) % 2 == 0 else (30, 28, 26, 255)
lamp_on = front.copy()
lamp_off = front.copy()
for y in range(5, 11):
    for x in range(5, 11):
        lamp_on.load()[x, y] = (255, 60, 30, 255) if (x + y) % 3 else (255, 180, 120, 255)
        lamp_off.load()[x, y] = (60, 20, 18, 255)
save(lamp_off, 'block', 'hazard_switch_front')
save(lamp_on, 'block', 'hazard_switch_front_on')
save(tint(lamp_off, '#404040', gain=1.0), 'block', 'hazard_switch_front_off')
orientable_trap('hazard_switch', f'{NS}:block/hazard_switch_side', 'minecraft:block/observer_top', f'{NS}:block/hazard_switch_front',
                f'{NS}:block/hazard_switch_front_on', f'{NS}:block/hazard_switch_front_off')

# floodgate: copper grate; fired it pours water, disarmed it is chained shut
save(recolor(vanilla('block/copper_grate'), '#3a8ae0', 0.05), 'block', 'floodgate_front_open')
locked = vanilla('block/oxidized_copper_grate').copy()
lp = locked.load()
for i in range(16):
    lp[i, 7] = lp[i, 8] = (70, 60, 60, 255)
save(locked, 'block', 'floodgate_front_locked')
orientable_trap('floodgate', 'minecraft:block/oxidized_cut_copper', 'minecraft:block/oxidized_cut_copper', 'minecraft:block/oxidized_copper_grate',
                f'{NS}:block/floodgate_front_open', f'{NS}:block/floodgate_front_locked')

# kiln turret: blast furnace
orientable_trap('kiln_turret', 'minecraft:block/blast_furnace_side', 'minecraft:block/blast_furnace_top', 'minecraft:block/blast_furnace_front',
                'minecraft:block/blast_furnace_front_on', 'minecraft:block/furnace_front')

# volley launcher: mossy dispenser
orientable_trap('volley_launcher', 'minecraft:block/mossy_cobblestone', 'minecraft:block/mossy_stone_bricks', 'minecraft:block/dispenser_front',
                'minecraft:block/dispenser_front', 'minecraft:block/dropper_front')

# lockdown gate: a portcullis of brass bars. Open it hangs raised in the top of the block
save(tint(vanilla('block/iron_bars'), '#c89a50', gain=1.2), 'block', 'lockdown_bars')


def portcullis(y0):
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': {
        'particle': f'{NS}:block/lockdown_bars', 'bars': f'{NS}:block/lockdown_bars'}, 'elements': [
        {'from': [0, y0, 7], 'to': [16, 16, 9], 'faces': {
            'north': {'texture': '#bars', 'uv': [0, 0, 16, 16 - y0]}, 'south': {'texture': '#bars', 'uv': [0, 0, 16, 16 - y0]},
            'up': {'texture': '#bars', 'uv': [0, 7, 16, 9]}, 'down': {'texture': '#bars', 'uv': [0, 7, 16, 9]},
            'east': {'texture': '#bars', 'uv': [7, 0, 9, 16 - y0]}, 'west': {'texture': '#bars', 'uv': [7, 0, 9, 16 - y0]}}},
        {'from': [0, 14, 6], 'to': [16, 16, 10], 'faces': {f: {'texture': '#bars', 'uv': [0, 0, 16, 2]} for f in
                                                             ('north', 'south', 'up', 'down', 'east', 'west')}}]}


model('block/lockdown_gate', portcullis(12))
model('block/lockdown_gate_firing', portcullis(0))
model('block/lockdown_gate_disarmed', portcullis(12))
lg_variants = {}
for facing, y in FACING_Y.items():
    for firing in ('false', 'true'):
        for disarmed in ('false', 'true'):
            suffix = '_disarmed' if disarmed == 'true' else '_firing' if firing == 'true' else ''
            v = {'model': f'{NS}:block/lockdown_gate{suffix}'}
            if y:
                v['y'] = y
            lg_variants[f'facing={facing},firing={firing},disarmed={disarmed}'] = v
blockstate('lockdown_gate', {'variants': lg_variants})
item_model('lockdown_gate', f'{NS}:block/lockdown_gate_firing')
pickaxe.append(f'{NS}:lockdown_gate')

TRAPS = {
    'crusher': ('Crusher', 'Zermalmer', 'Redstone signal: rams the two blocks in front of it. Touching crushers fire as a ripple. Jam it with a Piston Brace.',
                'Redstone-Signal: rammt die zwei Blöcke davor. Angrenzende Zermalmer lösen nacheinander aus. Mit einer Kolbenstrebe blockieren.'),
    'hazard_switch': ('Hazard Switch', 'Gefahrenweiche', 'Redstone signal: throws everything on the rails beside it off the track, towards its front. Burn it out with a Pulse Injector.',
                      'Redstone-Signal: wirft alles auf den Schienen daneben in Richtung seiner Vorderseite vom Gleis. Mit einem Impulsinjektor durchbrennen.'),
    'lockdown_gate': ('Lockdown Gate', 'Sperrgitter', 'Open portcullis. Redstone signal: slams shut for 10 seconds and calls nearby Bell Stalkers. A Decoy Beacon keeps it open.',
                      'Offenes Fallgitter. Redstone-Signal: schließt sich 10 Sekunden und ruft Glockenpirscher. Ein Köderleuchtfeuer hält es offen.'),
    'floodgate': ('Floodgate', 'Flutschleuse', 'Redstone signal: a surge sweeps everything within 6 blocks towards its front. Disarm it with a Repeater Wrench.',
                  'Redstone-Signal: eine Flutwelle spült alles im Umkreis von 6 Blöcken in Richtung der Vorderseite. Mit einem Verstärkerschlüssel entschärfen.'),
    'kiln_turret': ('Kiln Turret', 'Brennofen-Geschütz', 'Redstone signal: spits a fire charge. Rows fire as a volley. A held Signal Jammer stops it.',
                    'Redstone-Signal: spuckt eine Feuerkugel. Reihen feuern als Salve. Ein gehaltener Signalstörer stoppt es.'),
    'volley_launcher': ('Volley Launcher', 'Salvenwerfer', 'Redstone signal: fires a fan of three arrows. Cut it loose with Insulated Cutters.',
                        'Redstone-Signal: schießt einen Fächer aus drei Pfeilen. Mit der Isolierten Zange entschärfen.'),
}
for block, (e, g, de_, dg) in TRAPS.items():
    name(f'block.{NS}.{block}', e, g, de_, dg)

# ================================================================================================ decoy beacon
dside = vanilla('block/cut_copper').copy()
dp = dside.load()
for y in range(4, 12):
    for x in range(5, 11):
        dp[x, y] = (64, 224, 255, 255) if (x + y) % 3 else (190, 250, 255, 255)
save(dside, 'block', 'decoy_beacon_side')
save(recolor(vanilla('block/amethyst_block'), '#40e0ff'), 'block', 'decoy_beacon_core')
model('block/decoy_beacon', {'parent': 'minecraft:block/block', 'textures': {
    'particle': f'{NS}:block/decoy_beacon_side', 'side': f'{NS}:block/decoy_beacon_side', 'core': f'{NS}:block/decoy_beacon_core',
    'metal': 'minecraft:block/cut_copper'}, 'elements': [
    {'from': [3, 0, 3], 'to': [13, 3, 13], 'faces': {f: {'texture': '#metal'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
    {'from': [4, 3, 4], 'to': [12, 11, 12], 'faces': {f: {'texture': '#side'} for f in ('north', 'south', 'east', 'west')}},
    {'from': [5, 11, 5], 'to': [11, 14, 11], 'faces': {f: {'texture': '#core'} for f in ('north', 'south', 'east', 'west', 'up')}}]})
blockstate('decoy_beacon', {'variants': {'': {'model': f'{NS}:block/decoy_beacon'}}})
item_model('decoy_beacon', f'{NS}:block/decoy_beacon')
pickaxe.append(f'{NS}:decoy_beacon')
name(f'block.{NS}.decoy_beacon', 'Decoy Beacon', 'Köderleuchtfeuer',
     'Chimes like footsteps: Bell Stalkers within 24 blocks go to it, Lockdown Gates within 16 blocks stay open.',
     'Klingt wie Schritte: Glockenpirscher im Umkreis von 24 Blöcken gehen hin, Sperrgitter im Umkreis von 16 bleiben offen.')

for block in ('karst_limestone', 'lichen_karst', 'karst_bricks', 'rusted_soil', 'slag', 'resonant_crystal', 'sulfur_crust',
              'briar_thorns', 'realm_gate', 'tripper_rail', 'decoy_beacon', *TRAPS):
    self_drop(block)

# ================================================================================================ counter items
P = {'a': '#3a2a1a', 'b': '#c8902a', 'c': '#e8c060', 'd': '#6a6a72', 'e': '#a0a0a8', 'f': '#d02020', 'g': '#ff7040',
     'h': '#202024', 'i': '#40e0ff', 'j': '#2a2a2a', 'k': '#e0e0e0', 'l': '#8a1a1a', 'm': '#50c0ff'}
ITEMS = {
    'realm_cog': ([
        '................', '......dd.dd.....', '.....deeeeed....', '...dd.eeeeee.dd.', '...deeeddeeeed..', '....eeddd.deee..',
        '...deed....deed.', '..ddee......eedd', '..ddee......eedd', '...deed....deed.', '....eeed.ddeee..', '...deeeeddeeeed.',
        '...dd.eeeeee.dd.', '.....deeeeed....', '......dd.dd.....', '................'], 'Realm Cog', 'Reichszahnrad',
        'Gear from a realm construct. Crafts the counter tools.', 'Zahnrad eines Reichs-Konstrukts. Für die Gegenwerkzeuge.'),
    'piston_brace': ([
        '................', '.bb..........bb.', '.bcb........bcb.', '..bcb......bcb..', '...bcb....bcb...', '....bcb..bcb....',
        '.....bcbbcb.....', '......bccb......', '......bccb......', '.....bcbbcb.....', '....bcb..bcb....', '...bcb....bcb...',
        '..bcb......bcb..', '.bcb........bcb.', '.bb..........bb.', '................'], 'Piston Brace', 'Kolbenstrebe',
        'Right click a Crusher to jam it for good.', 'Rechtsklick auf einen Zermalmer blockiert ihn für immer.'),
    'pulse_injector': ([
        '................', '..........ff....', '.........fgf....', '........fgf.....', '.......fgf......', '......dfd.......',
        '.....dede.......', '....deed........', '...deed.........', '..hded..........', '.hhhd...........', '.hhh............',
        '..h.............', '................', '................', '................'], 'Pulse Injector', 'Impulsinjektor',
        'Right click a Hazard Switch to burn it out. Right click a construct to short-circuit it for 5 seconds.',
        'Rechtsklick auf eine Gefahrenweiche brennt sie durch. Rechtsklick auf ein Konstrukt legt es 5 Sekunden lahm.'),
    'repeater_wrench': ([
        '................', '...ee.....ee....', '...e.e...e.e....', '....eeeeeee.....', '.....eefee......', '......eee.......',
        '......ded.......', '.....ded........', '....ded.........', '...ded..........', '..ded...........', '.fdd............',
        '.ff.............', '................', '................', '................'], 'Repeater Wrench', 'Verstärkerschlüssel',
        'Right click a Floodgate to disarm it. Sneak + right click any realm trap to switch it between armed and safe.',
        'Rechtsklick auf eine Flutschleuse entschärft sie. Schleichen + Rechtsklick schaltet jede Reichsfalle zwischen scharf und sicher.'),
    'signal_jammer': ([
        '................', '..k.........k...', '..d.........d...', '..d.........d...', '..d.........d...', '.hhhhhhhhhhhhh..',
        '.hddddddddddh...', '.hdiidffddddh...', '.hdiidffddddh...', '.hddddddddddh...', '.hdjdjdjdjddh...', '.hddddddddddh...',
        '.hhhhhhhhhhhhh..', '................', '................', '................'], 'Signal Jammer', 'Signalstörer',
        'While you hold it, realm traps within 10 blocks and Kiln Brute volleys do not fire.',
        'Solange du ihn hältst, lösen Reichsfallen im Umkreis von 10 Blöcken und Salven der Brennofen-Rohlinge nicht aus.'),
    'insulated_cutters': ([
        '................', '..e.........e...', '..ee.......ee...', '...ee.....ee....', '....ee...ee.....', '.....eedee......',
        '......dkd.......', '.....ll.ll......', '....ll...ll.....', '...ll.....ll....', '..ll.......ll...', '.ll.........ll..',
        '.l...........l..', '................', '................', '................'], 'Insulated Cutters', 'Isolierte Zange',
        'Shears that cut tripwire without setting it off, disarm Volley Launchers and hurt Spool Weavers badly.',
        'Schere, die Stolperdraht schneidet ohne auszulösen, Salvenwerfer entschärft und Spulenwebern schwer schadet.'),
}
for item, (rows, e, g, de_, dg) in ITEMS.items():
    save(sprite(rows, P), 'item', item)
    item_model(item)
    name(f'item.{NS}.{item}', e, g, de_, dg)

# ================================================================================================ creatures
CREATURES = {
    # name: (english, german, drops [(item, min, max, chance)])
    'karst_colossus': ('Karst Colossus', 'Karst-Koloss', [(f'{NS}:realm_cog', 2, 4, 1), ('minecraft:redstone', 2, 6, 1), (f'{NS}:karst_bricks', 0, 3, 1)]),
    'switchback_crawler': ('Switchback Crawler', 'Weichenkriecher', [(f'{NS}:realm_cog', 1, 2, 1), ('minecraft:rail', 1, 4, 1), ('minecraft:iron_nugget', 2, 6, 1)]),
    'bell_stalker': ('Bell Stalker', 'Glockenpirscher', [(f'{NS}:realm_cog', 1, 3, 1), ('minecraft:copper_ingot', 1, 3, 1), ('minecraft:amethyst_shard', 0, 3, 1)]),
    'sluice_chainjaw': ('Sluice Chainjaw', 'Schleusen-Kettenkiefer', [(f'{NS}:realm_cog', 1, 2, 1), ('minecraft:chain', 1, 2, 1), ('minecraft:copper_ingot', 1, 3, 1)]),
    'kiln_brute': ('Kiln Brute', 'Brennofen-Rohling', [(f'{NS}:realm_cog', 1, 3, 1), ('minecraft:coal', 2, 5, 1), ('minecraft:blaze_powder', 0, 1, 1)]),
    'spool_weaver': ('Spool Weaver', 'Spulenweber', [(f'{NS}:realm_cog', 1, 2, 1), ('minecraft:string', 2, 5, 1), ('minecraft:tripwire_hook', 0, 1, 1)]),
    'leaking_cell': ('The Leaking Cell', 'Die Leckende Zelle', [(f'{NS}:uranium_flesh', 0, 2, 1), (f'{NS}:uranium_shard', 0, 1, 0.3), (f'{NS}:realm_cog', 1, 1, 0.3)]),
    'detonator_husk': ('Detonator Husk', 'Zünderhülle', [('minecraft:gunpowder', 0, 2, 1), (f'{NS}:charged_gunpowder', 0, 1, 0.5), (f'{NS}:realm_cog', 1, 1, 0.3)]),
    'tripwire_brood': ('Tripwire Brood', 'Stolperdraht-Brut', [('minecraft:string', 0, 3, 1), (f'{NS}:crystal_silk', 0, 2, 0.6), (f'{NS}:realm_cog', 1, 1, 0.3)]),
    'kilnbound': ('Kilnbound', 'Ofengebundener', [('minecraft:bone', 0, 2, 1), ('minecraft:coal', 0, 2, 1), ('minecraft:arrow', 0, 2, 1), (f'{NS}:realm_cog', 1, 1, 0.3)]),
    'living_capacitor': ('Living Capacitor', 'Lebender Kondensator', [('minecraft:slime_ball', 0, 2, 1), ('minecraft:redstone', 1, 3, 1), (f'{NS}:ruby', 0, 1, 0.2)]),
    'relay_strider': ('Relay Strider', 'Relais-Schreiter', [('minecraft:ender_pearl', 0, 1, 1), ('minecraft:redstone', 1, 3, 1), (f'{NS}:realm_cog', 1, 1, 0.4)]),
    'bellows_hog': ('Bellows Hog', 'Blasebalg-Keiler', [(f'{NS}:ember_pork', 1, 3, 1), ('minecraft:leather', 0, 1, 1)]),
    'flesh_press': ('Flesh Press', 'Fleischpresse', [('minecraft:redstone', 4, 9, 1), ('minecraft:iron_ingot', 2, 4, 1), (f'{NS}:realm_cog', 2, 3, 1)]),
}
for mob, (e, g, drops) in CREATURES.items():
    name(f'entity.{NS}.{mob}', e, g)
    name(f'item.{NS}.{mob}_spawn_egg', f'{e} Spawn Egg', f'{g}-Spawn-Ei')
    item_model(f'{mob}_spawn_egg', 'minecraft:item/template_spawn_egg')
    pools = []
    for item, lo, hi, chance in drops:
        pool = {'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': item, 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}, 'add': False},
            {'function': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting',
             'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}]}]}
        if chance < 1:
            pool['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': chance}]
        pools.append(pool)
    write(os.path.join(DATA, 'loot_table', 'entities', mob + '.json'), {'type': 'minecraft:entity', 'pools': pools,
                                                                       'random_sequence': f'{NS}:entities/{mob}'})


# ================================================================================================ chest loot
def entry(item, lo=1, hi=1, weight=10):
    e = {'type': 'minecraft:item', 'name': item, 'weight': weight}
    if hi > 1 or lo != 1:
        e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}, 'add': False}]
    return e


COMMON = [entry('minecraft:redstone', 4, 12, 20), entry('minecraft:iron_ingot', 1, 4, 12), entry(f'{NS}:realm_cog', 1, 3, 14),
          entry('minecraft:repeater', 1, 3, 8), entry('minecraft:comparator', 1, 2, 6), entry(f'{NS}:redstone_circuit', 1, 2, 8),
          entry('minecraft:bread', 2, 5, 8), entry(f'{NS}:ruby', 1, 3, 4), entry('minecraft:diamond', 1, 1, 2)]
COUNTERS = {'realm_piston_karst': f'{NS}:piston_brace', 'realm_switchyard_flats': f'{NS}:pulse_injector',
            'realm_resonance_hollows': f'{NS}:decoy_beacon', 'realm_sluice_gardens': f'{NS}:repeater_wrench',
            'realm_kiln_barrens': f'{NS}:signal_jammer', 'realm_tripwire_briar': f'{NS}:insulated_cutters'}


def chest(name_, pools):
    write(os.path.join(DATA, 'loot_table', 'chests', name_ + '.json'), {'type': 'minecraft:chest', 'pools': pools,
                                                                        'random_sequence': f'{NS}:chests/{name_}'})


for chest_name, counter in COUNTERS.items():
    count = [2, 4] if counter.endswith('piston_brace') else [1, 1]
    chest(chest_name, [
        {'rolls': {'type': 'minecraft:uniform', 'min': 4, 'max': 7}, 'bonus_rolls': 0, 'entries': COMMON},
        {'rolls': 1, 'bonus_rolls': 0, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.75}],
         'entries': [entry(counter, count[0], count[1])]}])
LOGIC = ['and_gate', 'or_gate', 'xor_gate', 'not_gate', 't_flip_flop', 'rs_latch', 'counter', 'sequencer', 'clock', 'delay_block',
         'pulse_extender', 'edge_detector', 'instant_lamp', 'signal_display', 'player_detector', 'laser_sensor', 'wireless_transmitter',
         'wireless_receiver', 'multimeter']
chest('realm_workshop', [
    {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6}, 'bonus_rolls': 0, 'entries': [entry(f'{NS}:{b}', 1, 3) for b in LOGIC]},
    {'rolls': 2, 'bonus_rolls': 0, 'entries': COMMON}])
chest('realm_arrival', [
    {'rolls': 1, 'bonus_rolls': 0, 'entries': [entry(c, 2 if c.endswith('brace') else 1, 3 if c.endswith('brace') else 1)]} for c in COUNTERS.values()
] + [{'rolls': 1, 'bonus_rolls': 0, 'entries': [entry(f'{NS}:realm_cog', 4, 6)]},
     {'rolls': 1, 'bonus_rolls': 0, 'entries': [entry('minecraft:redstone', 16, 24)]},
     {'rolls': 1, 'bonus_rolls': 0, 'entries': [entry('minecraft:cooked_beef', 6, 8)]},
     {'rolls': 1, 'bonus_rolls': 0, 'entries': [entry(f'{NS}:multimeter')]}])

# ================================================================================================ recipes
shaped('realm_gate', ['IRI', 'RCR', 'IRI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone_block', 'C': f'{NS}:redstone_circuit'}, f'{NS}:realm_gate')
shaped('piston_brace', ['I I', 'ICI', 'I I'], {'I': 'minecraft:iron_ingot', 'C': f'{NS}:realm_cog'}, f'{NS}:piston_brace', 3)
shaped('pulse_injector', ['  R', ' C ', 'I  '], {'R': 'minecraft:redstone_block', 'C': f'{NS}:realm_cog', 'I': 'minecraft:iron_ingot'}, f'{NS}:pulse_injector')
shaped('decoy_beacon', ['AAA', 'ACA', 'KKK'], {'A': 'minecraft:amethyst_shard', 'C': f'{NS}:realm_cog', 'K': 'minecraft:copper_ingot'}, f'{NS}:decoy_beacon')
shaped('repeater_wrench', [' I ', ' RI', 'C  '], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:repeater', 'C': f'{NS}:realm_cog'}, f'{NS}:repeater_wrench')
shaped('signal_jammer', ['T T', 'ICI', 'IRI'], {'T': 'minecraft:redstone_torch', 'I': 'minecraft:iron_ingot', 'C': f'{NS}:realm_cog', 'R': 'minecraft:redstone'},
       f'{NS}:signal_jammer')
shapeless('insulated_cutters', ['minecraft:shears', f'{NS}:realm_cog', '#minecraft:wool'], f'{NS}:insulated_cutters')
shaped('karst_bricks', ['SS', 'SS'], {'S': f'{NS}:karst_limestone'}, f'{NS}:karst_bricks', 4)
shaped('tripper_rail', ['I I', 'IHI', 'IRI'], {'I': 'minecraft:iron_ingot', 'H': 'minecraft:tripwire_hook', 'R': 'minecraft:redstone'}, f'{NS}:tripper_rail', 6)
shaped('crusher', ['SSS', 'SPS', 'SRS'], {'S': f'{NS}:karst_bricks', 'P': 'minecraft:piston', 'R': 'minecraft:redstone'}, f'{NS}:crusher')
shaped('hazard_switch', ['III', 'ILI', 'IRI'], {'I': 'minecraft:iron_ingot', 'L': 'minecraft:lever', 'R': 'minecraft:redstone'}, f'{NS}:hazard_switch')
shaped('lockdown_gate', ['BBB', 'BCB', 'BRB'], {'B': 'minecraft:iron_bars', 'C': f'{NS}:realm_cog', 'R': 'minecraft:redstone'}, f'{NS}:lockdown_gate', 4)
shaped('floodgate', ['KKK', 'KWK', 'KRK'], {'K': 'minecraft:copper_ingot', 'W': 'minecraft:water_bucket', 'R': 'minecraft:redstone'}, f'{NS}:floodgate')
shaped('kiln_turret', ['BBB', 'BFB', 'BRB'], {'B': 'minecraft:polished_blackstone_bricks', 'F': 'minecraft:fire_charge', 'R': 'minecraft:redstone'},
       f'{NS}:kiln_turret')
shaped('volley_launcher', ['MMM', 'MDM', 'MRM'], {'M': 'minecraft:mossy_cobblestone', 'D': 'minecraft:dispenser', 'R': 'minecraft:redstone'},
       f'{NS}:volley_launcher')


# ================================================================================================ tags
def merge_tag(path, values):
    existing = {'replace': False, 'values': []}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            existing = json.load(f)
    for v in values:
        if v not in existing['values']:
            existing['values'].append(v)
    write(path, existing)


merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'mineable', 'pickaxe.json'), pickaxe)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'mineable', 'shovel.json'), shovel)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'needs_iron_tool.json'), needs_iron)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'rails.json'), [f'{NS}:tripper_rail'])
merge_tag(os.path.join(MC_DATA, 'tags', 'item', 'rails.json'), [f'{NS}:tripper_rail'])
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'sword_efficient.json'), [f'{NS}:briar_thorns'])

# ================================================================================================ world generation
def placed(name_, configured, placement):
    write(os.path.join(DATA, 'worldgen', 'configured_feature', name_ + '.json'), configured)
    write(os.path.join(DATA, 'worldgen', 'placed_feature', name_ + '.json'), {'feature': f'{NS}:{name_}', 'placement': placement})


def surface(count=None, rarity=None, heightmap='MOTION_BLOCKING_NO_LEAVES'):
    p = []
    if rarity:
        p.append({'type': 'minecraft:rarity_filter', 'chance': rarity})
    if count is not None:
        p.append({'type': 'minecraft:count', 'count': count})
    return p + [{'type': 'minecraft:in_square'}, {'type': 'minecraft:heightmap', 'heightmap': heightmap}, {'type': 'minecraft:biome'}]


def none_feature(kind):
    return {'type': f'{NS}:{kind}', 'config': {}}


def state(name_, props=None):
    s = {'Name': name_}
    if props:
        s['Properties'] = props
    return s


placed('karst_spire', {'type': f'{NS}:spire', 'config': {'body': state(f'{NS}:karst_limestone'), 'accent': state(f'{NS}:lichen_karst'),
                                                          'min_height': 12, 'max_height': 34, 'min_radius': 2, 'max_radius': 4}},
       surface({'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 2}))
placed('kiln_spire', {'type': f'{NS}:spire', 'config': {'body': state('minecraft:basalt', {'axis': 'y'}), 'accent': state('minecraft:magma_block'),
                                                         'min_height': 6, 'max_height': 18, 'min_radius': 1, 'max_radius': 3}},
       surface({'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 1}))
for kind, chance in (('crusher_passage', 10), ('switchyard_junction', 9), ('sluice_bridge', 10), ('kiln_bridge', 10), ('briar_ambush', 10),
                     ('circuit_workshop', 36), ('rail_line', 3)):
    placed(kind, none_feature(kind), surface(rarity=chance, heightmap='WORLD_SURFACE_WG'))
placed('resonance_gatehouse', none_feature('resonance_gatehouse'), [
    {'type': 'minecraft:rarity_filter', 'chance': 4}, {'type': 'minecraft:in_square'},
    {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -40}, 'max_inclusive': {'absolute': 30}}},
    {'type': 'minecraft:environment_scan', 'direction_of_search': 'down', 'max_steps': 16, 'target_condition': {'type': 'minecraft:solid'},
     'allowed_search_condition': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}},
    {'type': 'minecraft:random_offset', 'xz_spread': 0, 'y_spread': 1}, {'type': 'minecraft:biome'}])
placed('resonant_crystal_ore', {'type': 'minecraft:ore', 'config': {'size': 9, 'discard_chance_on_air_exposure': 0.0, 'targets': [
    {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:base_stone_overworld'}, 'state': state(f'{NS}:resonant_crystal')}]}},
       [{'type': 'minecraft:count', 'count': 14}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -60}, 'max_inclusive': {'absolute': 40}}},
        {'type': 'minecraft:biome'}])
placed('briar_patch', {'type': 'minecraft:random_patch', 'config': {'tries': 24, 'xz_spread': 5, 'y_spread': 2, 'feature': {
    'feature': {'type': 'minecraft:simple_block', 'config': {'to_place': {'type': 'minecraft:simple_state_provider', 'state': state(f'{NS}:briar_thorns')}}},
    'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
        {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'},
        {'type': 'minecraft:would_survive', 'state': state(f'{NS}:briar_thorns')}]}}]}}}, surface(3))
placed('slag_heap', {'type': 'minecraft:forest_rock', 'config': {'state': state(f'{NS}:slag')}}, surface(1))
placed('coal_heap', {'type': 'minecraft:forest_rock', 'config': {'state': state('minecraft:coal_block')}}, surface(rarity=3))
placed('sulfur_rock', {'type': 'minecraft:forest_rock', 'config': {'state': state(f'{NS}:sulfur_crust')}}, surface(rarity=2))

# every biome picks its features from this one ordered list per step, so the order is the same everywhere
plains = vanilla_json('data/minecraft/worldgen/biome/plains.json')
ORES = plains['features'][6]
STEPS = [
    [],
    ['minecraft:lake_lava_underground', 'minecraft:lake_lava_surface'],
    ['minecraft:amethyst_geode', f'{NS}:karst_spire', f'{NS}:kiln_spire', f'{NS}:slag_heap', f'{NS}:coal_heap', f'{NS}:sulfur_rock'],
    ['minecraft:monster_room', 'minecraft:monster_room_deep', f'{NS}:resonance_gatehouse'],
    [f'{NS}:crusher_passage', f'{NS}:switchyard_junction', f'{NS}:sluice_bridge', f'{NS}:kiln_bridge', f'{NS}:briar_ambush',
     f'{NS}:circuit_workshop', f'{NS}:rail_line'],
    [],
    ORES + [f'{NS}:resonant_crystal_ore'],
    ['minecraft:large_dripstone', 'minecraft:dripstone_cluster', 'minecraft:pointed_dripstone', 'minecraft:sculk_vein', 'minecraft:sculk_patch_deep_dark'],
    ['minecraft:spring_water', 'minecraft:spring_lava'],
    ['minecraft:glow_lichen', 'minecraft:flower_cherry', 'minecraft:patch_waterlily', 'minecraft:trees_swamp', 'minecraft:seagrass_swamp',
     'minecraft:patch_sugar_cane_swamp', 'minecraft:dark_forest_vegetation', 'minecraft:vines', f'{NS}:briar_patch',
     'minecraft:patch_tall_grass', 'minecraft:patch_grass_forest', 'minecraft:patch_dead_bush_badlands', 'minecraft:patch_dead_bush',
     'minecraft:patch_berry_common'],
    ['minecraft:freeze_top_layer'],
]
known = set(n.split('/')[-1][:-5] for n in JAR.namelist() if n.startswith('data/minecraft/worldgen/placed_feature/'))
for step in STEPS:
    for f in step:
        if f.startswith('minecraft:') and f.split(':')[1] not in known:
            raise SystemExit('unknown vanilla placed feature ' + f)


def features(*wanted):
    w = set(wanted) | set(ORES) | {'minecraft:lake_lava_underground', 'minecraft:monster_room', 'minecraft:monster_room_deep',
                                   'minecraft:spring_water', 'minecraft:spring_lava', 'minecraft:freeze_top_layer', f'{NS}:circuit_workshop'}
    return [[f for f in step if f in w] for step in STEPS]


def spawn(entity, weight, lo, hi):
    return {'type': entity if ':' in entity else f'{NS}:{entity}', 'weight': weight, 'minCount': lo, 'maxCount': hi}


def c(hexcolor):
    return int(hexcolor.lstrip('#'), 16)


BIOMES = {
    'piston_karst': dict(
        en='Piston Karst', de='Kolbenkarst', base='stony_peaks', temperature=0.9, downfall=0.3,
        effects={'sky_color': c('#78a8ff'), 'fog_color': c('#d8c8a8'), 'water_color': c('#3a9ad8'), 'water_fog_color': c('#10304a'),
                 'grass_color': c('#b8a060'), 'foliage_color': c('#a88a40')},
        features=features(f'{NS}:karst_spire', f'{NS}:crusher_passage'),
        monster=[spawn('karst_colossus', 25, 1, 1), spawn('relay_strider', 30, 1, 1), spawn('detonator_husk', 40, 1, 2)],
        creature=[spawn('flesh_press', 4, 1, 1)], costs=['karst_colossus']),
    'switchyard_flats': dict(
        en='Switchyard Flats', de='Weichenebene', base='badlands', temperature=1.2, downfall=0.1,
        effects={'sky_color': c('#c07aa0'), 'fog_color': c('#d08a70'), 'water_color': c('#8a4a3a'), 'water_fog_color': c('#3a1a10'),
                 'grass_color': c('#a8905a'), 'foliage_color': c('#8a7040'),
                 'particle': {'probability': 0.004, 'options': {'type': 'minecraft:dust', 'color': [1.0, 0.2, 0.1], 'scale': 1.0}}},
        features=features(f'{NS}:slag_heap', f'{NS}:coal_heap', f'{NS}:switchyard_junction', f'{NS}:rail_line', 'minecraft:patch_dead_bush_badlands'),
        monster=[spawn('switchback_crawler', 40, 1, 2), spawn('detonator_husk', 40, 1, 2), spawn('kilnbound', 20, 1, 1)],
        creature=[spawn('bellows_hog', 12, 2, 3)], costs=['switchback_crawler']),
    'resonance_hollows': dict(
        en='Resonance Hollows', de='Resonanzhöhlen', base='dripstone_caves', temperature=0.6, downfall=0.4,
        effects={'sky_color': c('#6a5aa8'), 'fog_color': c('#3a2a6a'), 'water_color': c('#40c8ff'), 'water_fog_color': c('#10205a'),
                 'particle': {'probability': 0.008, 'options': {'type': 'minecraft:glow'}}},
        features=features('minecraft:amethyst_geode', f'{NS}:resonance_gatehouse', f'{NS}:resonant_crystal_ore', 'minecraft:large_dripstone',
                          'minecraft:dripstone_cluster', 'minecraft:pointed_dripstone', 'minecraft:sculk_vein', 'minecraft:glow_lichen'),
        monster=[spawn('bell_stalker', 30, 1, 1), spawn('tripwire_brood', 40, 1, 2), spawn('living_capacitor', 30, 1, 2), spawn('relay_strider', 15, 1, 1)],
        creature=[], costs=['bell_stalker']),
    'sluice_gardens': dict(
        en='Sluice Gardens', de='Schleusengärten', base='swamp', temperature=0.7, downfall=0.9,
        effects={'sky_color': c('#8ab0a0'), 'fog_color': c('#a8c8b8'), 'water_color': c('#3ab8a0'), 'water_fog_color': c('#10403a'),
                 'grass_color': c('#4a9a70'), 'foliage_color': c('#3a8a60')},
        features=features('minecraft:flower_cherry', 'minecraft:patch_waterlily', 'minecraft:trees_swamp', 'minecraft:seagrass_swamp',
                          'minecraft:patch_sugar_cane_swamp', 'minecraft:patch_tall_grass', f'{NS}:sluice_bridge'),
        monster=[spawn('sluice_chainjaw', 40, 1, 2), spawn('leaking_cell', 40, 1, 3), spawn('living_capacitor', 30, 1, 3)],
        creature=[], costs=['sluice_chainjaw']),
    'kiln_barrens': dict(
        en='Kiln Barrens', de='Brennofen-Öde', base='desert', temperature=2.0, downfall=0.0,
        effects={'sky_color': c('#b0603a'), 'fog_color': c('#8a4a2a'), 'water_color': c('#c06a2a'), 'water_fog_color': c('#3a1a0a'),
                 'grass_color': c('#6a5a3a'), 'foliage_color': c('#5a4a2a'),
                 'particle': {'probability': 0.01, 'options': {'type': 'minecraft:white_ash'}}},
        features=features(f'{NS}:kiln_spire', f'{NS}:sulfur_rock', 'minecraft:lake_lava_surface', f'{NS}:kiln_bridge'),
        monster=[spawn('kiln_brute', 30, 1, 1), spawn('kilnbound', 50, 1, 2), spawn('detonator_husk', 20, 1, 1)],
        creature=[spawn('bellows_hog', 20, 2, 4)], costs=['kiln_brute']),
    'tripwire_briar': dict(
        en='Tripwire Briar', de='Stolperdraht-Dickicht', base='dark_forest', temperature=0.7, downfall=0.8,
        effects={'sky_color': c('#d8b060'), 'fog_color': c('#b8a060'), 'water_color': c('#4a7a4a'), 'water_fog_color': c('#1a2a1a'),
                 'grass_color': c('#5a6a2a'), 'foliage_color': c('#4a5a20'), 'grass_color_modifier': 'none'},
        features=features('minecraft:dark_forest_vegetation', 'minecraft:vines', f'{NS}:briar_patch', 'minecraft:patch_berry_common',
                          'minecraft:patch_grass_forest', f'{NS}:briar_ambush'),
        monster=[spawn('spool_weaver', 40, 1, 2), spawn('tripwire_brood', 40, 1, 2), spawn('leaking_cell', 20, 1, 2)],
        creature=[spawn('flesh_press', 3, 1, 1)], costs=['spool_weaver']),
}
for biome_name, b in BIOMES.items():
    base = vanilla_json(f'data/minecraft/worldgen/biome/{b["base"]}.json')
    effects = dict(base['effects'])
    for key in ('music', 'additions_sound', 'ambient_sound', 'grass_color_modifier'):
        effects.pop(key, None)
    effects.update(b['effects'])
    if effects.get('grass_color_modifier') == 'none':
        effects.pop('grass_color_modifier')
    biome = {
        'has_precipitation': b['downfall'] > 0.2, 'temperature': b['temperature'], 'downfall': b['downfall'], 'effects': effects,
        'spawners': {'monster': b['monster'], 'creature': b['creature'], 'ambient': [], 'axolotls': [], 'underground_water_creature': [],
                     'water_creature': [], 'water_ambient': [], 'misc': []},
        'spawn_costs': {f'{NS}:{m}': {'energy_budget': 0.12, 'charge': 0.7} for m in b['costs']},
        'carvers': {'air': ['minecraft:cave', 'minecraft:cave_extra_underground', 'minecraft:canyon']},
        'features': b['features'],
    }
    write(os.path.join(DATA, 'worldgen', 'biome', biome_name + '.json'), biome)
    name(f'biome.{NS}.{biome_name}', b['en'], b['de'])


# surface rules: each biome gets its own ground, cliffs and cave floors
def cond(condition, then):
    return {'type': 'minecraft:condition', 'if_true': condition, 'then_run': then}


def seq(*rules):
    return {'type': 'minecraft:sequence', 'sequence': list(rules)}


def blk(name_, props=None):
    return {'type': 'minecraft:block', 'result_state': state(name_, props)}


def biome_is(*names):
    return {'type': 'minecraft:biome', 'biome_is': [f'{NS}:{n}' for n in names]}


def depth(surface_type='floor', add=False, offset=0):
    return {'type': 'minecraft:stone_depth', 'offset': offset, 'add_surface_depth': add, 'secondary_depth_range': 0, 'surface_type': surface_type}


def noise_range(lo, hi, noise='minecraft:surface'):
    return {'type': 'minecraft:noise_threshold', 'noise': noise, 'min_threshold': lo, 'max_threshold': hi}


def y_above(y):
    return {'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': 0, 'add_stone_depth': False}


ABOVE_WATER = {'type': 'minecraft:water', 'offset': -1, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
ON_FLOOR, UNDER_FLOOR = depth('floor'), depth('floor', add=True)
PRELIM = {'type': 'minecraft:above_preliminary_surface'}
realm_rules = seq(
    # the cave biome: crystal-studded floors and pale ceilings wherever it is
    cond(biome_is('resonance_hollows'), seq(
        cond(ON_FLOOR, seq(cond(noise_range(0.35, 9), blk(f'{NS}:resonant_crystal')), cond(noise_range(-0.2, 9), blk('minecraft:calcite')),
                           blk('minecraft:smooth_basalt'))),
        cond(depth('ceiling'), blk('minecraft:calcite')))),
    cond(PRELIM, seq(
        cond(biome_is('piston_karst'), seq(
            cond(ON_FLOOR, seq(cond(noise_range(0.1, 9), blk(f'{NS}:lichen_karst')), blk(f'{NS}:karst_limestone'))),
            cond(UNDER_FLOOR, blk(f'{NS}:karst_limestone')))),
        cond(biome_is('switchyard_flats'), seq(
            cond(ON_FLOOR, seq(cond(noise_range(0.45, 9), blk(f'{NS}:slag')), cond(noise_range(-9, -0.5), blk('minecraft:gravel')),
                               blk(f'{NS}:rusted_soil'))),
            cond(UNDER_FLOOR, blk(f'{NS}:rusted_soil')))),
        cond(biome_is('sluice_gardens'), seq(
            cond(ON_FLOOR, seq(cond(ABOVE_WATER, seq(cond(noise_range(0.2, 9), blk('minecraft:moss_block')), blk('minecraft:grass_block', {'snowy': 'false'}))),
                               blk('minecraft:mud'))),
            cond(UNDER_FLOOR, blk('minecraft:dirt')))),
        cond(biome_is('kiln_barrens'), seq(
            cond(ON_FLOOR, seq(cond(noise_range(0.4, 9), blk(f'{NS}:sulfur_crust')), cond(noise_range(-9, -0.55), blk('minecraft:magma_block')),
                               blk('minecraft:blackstone'))),
            cond(UNDER_FLOOR, blk('minecraft:basalt', {'axis': 'y'})))),
        cond(biome_is('tripwire_briar'), seq(
            cond(ON_FLOOR, seq(cond(noise_range(0.3, 9), blk('minecraft:moss_block')), cond(noise_range(-9, -0.4), blk('minecraft:rooted_dirt')),
                               blk('minecraft:podzol', {'snowy': 'false'}))),
            cond(UNDER_FLOOR, blk('minecraft:dirt')))),
    )),
    # whole cliffs of the karst and the barrens are their own rock
    cond(biome_is('piston_karst'), cond(y_above(40), blk(f'{NS}:karst_limestone'))),
    cond(biome_is('kiln_barrens'), cond(y_above(36), blk('minecraft:blackstone'))),
)
settings = vanilla_json('data/minecraft/worldgen/noise_settings/overworld.json')
settings['surface_rule']['sequence'].insert(1, realm_rules)
write(os.path.join(DATA, 'worldgen', 'noise_settings', 'redstone_realm.json'), settings)

write(os.path.join(DATA, 'dimension_type', 'redstone_realm.json'), {
    'ultrawarm': False, 'natural': True, 'coordinate_scale': 1.0, 'has_skylight': True, 'has_ceiling': False, 'ambient_light': 0.0,
    'monster_spawn_light_level': {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 7}, 'monster_spawn_block_light_limit': 0,
    'piglin_safe': False, 'bed_works': True, 'respawn_anchor_works': False, 'has_raids': False,
    'logical_height': 384, 'min_y': -64, 'height': 384, 'infiniburn': '#minecraft:infiniburn_overworld', 'effects': 'minecraft:overworld'})


def point(biome, t=(-1, 1), h=(-1, 1), cont=(-1.2, 1.2), ero=(-1, 1), weird=(-1, 1), dep=0.0):
    return {'biome': f'{NS}:{biome}', 'parameters': {'temperature': list(t), 'humidity': list(h), 'continentalness': list(cont),
                                                     'erosion': list(ero), 'weirdness': list(weird), 'depth': dep, 'offset': 0.0}}


INLAND = (-0.11, 1.2)
write(os.path.join(DATA, 'dimension', 'redstone_realm.json'), {
    'type': f'{NS}:redstone_realm',
    'generator': {'type': 'minecraft:noise', 'settings': f'{NS}:redstone_realm', 'biome_source': {'type': 'minecraft:multi_noise', 'biomes': [
        point('sluice_gardens', cont=(-1.2, -0.11)),
        point('piston_karst', cont=INLAND, ero=(-1, -0.3)),
        point('kiln_barrens', t=(0.2, 1), h=(-1, 0.1), cont=INLAND, ero=(-0.3, 1)),
        point('switchyard_flats', t=(-1, 0.2), h=(-1, 0.1), cont=INLAND, ero=(-0.3, 1)),
        point('tripwire_briar', t=(-1, 0.2), h=(0.1, 1), cont=INLAND, ero=(-0.3, 1)),
        point('sluice_gardens', t=(0.2, 1), h=(0.1, 1), cont=INLAND, ero=(-0.3, 1)),
        point('resonance_hollows', dep=[0.2, 0.9]),
    ]}}})

# ================================================================================================ particles
def particle_frames(name, frames):
    keys = []
    for i, img in enumerate(frames):
        save(img, 'particle', f'{name}_{i}')
        keys.append(f'{NS}:{name}_{i}')
    write(os.path.join(ASSETS, 'particles', name + '.json'), {'textures': keys})


def radial(size, fn):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5 / (size / 2)
            col = fn(d, x, y)
            if col:
                px[x, y] = col
    return img


# spark: white-hot core with a red-orange cross that shrinks
particle_frames('realm_spark', [radial(8, lambda d, x, y, k=k: (
    (255, 250, 220, 255) if d < 0.25 * (1 - k * 0.2) else
    (255, 120, 40, 255) if (abs(x - 3.5) < 0.6 or abs(y - 3.5) < 0.6) and d < 0.95 - k * 0.2 else
    (220, 30, 20, 200) if d < 0.5 - k * 0.1 else None)) for k in range(4)])
# steam: soft round puffs with a little noise
particle_frames('realm_steam', [radial(16, lambda d, x, y, k=k: (
    (240, 240, 236, int(max(0, 1 - d) * (200 - k * 30) * (0.8 + 0.2 * rnd.random())))) if d < 1 else None) for k in range(4)])
# ember: flickering orange coal
particle_frames('realm_ember', [radial(8, lambda d, x, y, k=k: (
    (255, 240, 170, 255) if d < 0.3 else (255, 150 - k * 30, 30, 230) if d < 0.65 - k * 0.08 else None)) for k in range(3)])
# resonance: a thin cyan ring that the particle then grows
particle_frames('realm_resonance', [radial(16, lambda d, x, y, k=k: (
    (120, 240, 255, 230 - k * 40) if 0.72 - k * 0.04 < d < 0.92 else (200, 255, 255, 120) if 0.62 < d <= 0.72 - k * 0.04 else None)) for k in range(4)])
# drip: green glowing droplet
particle_frames('realm_drip', [radial(8, lambda d, x, y, k=k: (
    (220, 255, 160, 255) if d < 0.25 else (125, 255, 60, 230) if d < 0.6 - k * 0.1 else None)) for k in range(3)])

# ================================================================================================ texts
name(f'itemGroup.{NS}.redstone_realm', 'Redstone Realm', 'Redstone-Reich')
MESSAGES = {
    'gate_unpowered': ('The Realm Gate needs a redstone signal.', 'Das Reichstor braucht ein Redstone-Signal.'),
    'realm_enter': ('You enter the Redstone Realm.', 'Du betrittst das Redstone-Reich.'),
    'realm_first': ('Welcome to the Redstone Realm! Read the Field Guide, open the kit chest and look at the example circuits on this platform. The gate on the redstone block takes you home.',
                    'Willkommen im Redstone-Reich! Lies den Feldführer, öffne die Starterkiste und schau dir die Beispielschaltungen auf dieser Plattform an. Das Tor auf dem Redstone-Block bringt dich heim.'),
    'realm_leave': ('Back home.', 'Wieder zu Hause.'),
    'realm_missing': ('The Redstone Realm is missing from this world.', 'Das Redstone-Reich fehlt in dieser Welt.'),
    'wrench_sneak': ('Sneak + right click to switch this trap between armed and safe.', 'Schleichen + Rechtsklick schaltet diese Falle zwischen scharf und sicher.'),
    'trap_armed': ('Trap armed', 'Falle scharf'),
    'trap_disarmed': ('Trap disarmed', 'Falle entschärft'),
}
for key, (e, g) in MESSAGES.items():
    name(f'message.{NS}.{key}', e, g)

# signs: four lines of at most ~15 characters
SIGNS = {
    'crusher_passage': (('CRUSHING', 'PASSAGE', 'Plates fire the', 'crushers!'), ('QUETSCH-', 'GANG', 'Platten lösen', 'Zermalmer aus!')),
    'vault_and': (('THE VAULT', 'Both levers', '-> AND gate', '-> door opens'), ('DER TRESOR', 'Beide Hebel', '-> UND-Gatter', '-> Tür geht auf')),
    'switchyard': (('JUNCTION', 'Tripper rail', 'flips the Hazard', 'Switch. Mind pit'), ('WEICHE', 'Auslöseschiene', 'stellt die Weiche', 'Vorsicht Grube')),
    'gatehouse': (('LOCKDOWN', 'Sculk hears you,', 'gates slam shut.', 'Sneak or decoy!'), ('ABRIEGELUNG', 'Sculk hört dich,', 'Gitter fallen.', 'Schleich/Köder!')),
    'sluice': (('SLUICE BRIDGE', 'Tripwire opens', 'the floodgates.', 'Wrench = safe'), ('SCHLEUSEN-', 'BRÜCKE: Draht', 'öffnet Schleusen', 'Schlüssel=sicher')),
    'kiln': (('KILN BRIDGE', 'Plates fire the', 'turret walls.', 'Counter = score'), ('OFENBRÜCKE', 'Platten zünden', 'die Geschütze.', 'Zähler zählt mit')),
    'briar': (('AMBUSH PATH', 'Tripwire = arrow', 'volley. Laser', 'post = siren'), ('HINTERHALT', 'Draht = Pfeil-', 'salve. Laser-', 'posten = Sirene')),
    'station_gate': (('RETURN GATE', 'Sits on a', 'redstone block:', 'right click it'), ('RÜCKKEHR-TOR', 'Steht auf', 'Redstone-Block:', 'Rechtsklick')),
    'station_kit': (('STARTER KIT', 'One counter', 'tool for every', 'trap. Good luck'), ('STARTERKISTE', 'Ein Gegenmittel', 'für jede Falle.', 'Viel Glück!')),
    'ex_blinker': (('BLINKER', 'Clock -> lamps', 'Right click the', 'clock: speed'), ('BLINKER', 'Taktgeber->Lampe', 'Rechtsklick auf', 'Takt: Tempo')),
    'ex_and': (('AND GATE', 'Lever left and', 'lever right', 'both on = light'), ('UND-GATTER', 'Hebel links und', 'Hebel rechts:', 'beide = Licht')),
    'ex_toggle': (('TOGGLE', 'Button on block', '-> T flip-flop', 'on, off, on...'), ('UMSCHALTER', 'Knopf auf Block', '-> T-Flipflop', 'an, aus, an...')),
    'ex_counter': (('COUNTER', 'Top button +1', 'Floor button', 'resets to 0'), ('ZÄHLER', 'Oberer Knopf +1', 'Bodenknopf', 'setzt auf 0')),
    'ex_sequencer': (('RUNNING LIGHT', 'Clock ->', 'Sequencer: left', 'front, right..'), ('LAUFLICHT', 'Taktgeber ->', 'Sequenzer: li,', 'vorne, re, ...')),
    'ex_wireless': (('WIRELESS', 'Lever -> sender', 'Receiver on', 'channel 15'), ('FUNK', 'Hebel -> Sender', 'Empfänger auf', 'Kanal 15')),
    'ex_detector': (('DETECTOR', 'Player near:', 'lamp on, the', 'inverted off'), ('DETEKTOR', 'Spieler nah:', 'Lampe an, die', 'invertierte aus')),
    'ex_laser': (('LASER FENCE', 'Step into the', 'beam: siren +', 'lamp go off'), ('LASERZAUN', 'Tritt in den', 'Strahl: Sirene', '+ Lampe an')),
    'ex_trap': (('REALM TRAP', 'Lever fires', 'crushers, they', 'ripple along'), ('REICHSFALLE', 'Hebel zündet', 'Zermalmer, die', 'sich weitergeben')),
    'ex_rail': (('TRIPPER RAIL', 'Walk on it:', 'lamp + pulse', 'extender stays'), ('AUSLÖSESCHIENE', 'Drüberlaufen:', 'Lampe + Puls-', 'verlängerer')),
}
for key, (e_lines, g_lines) in SIGNS.items():
    for i in range(4):
        name(f'sign.{NS}.{key}.{i}', e_lines[i], g_lines[i])

BOOK_EN = [
    'REDSTONE REALM\nField Guide\n\nThis world runs on redstone. Every biome has a machine creature, a trap with a TRIGGER and a RESPONSE, and a COUNTER tool that beats it.\n\nThe arrival platform shows working example circuits.',
    'GETTING AROUND\n\nThe Realm Gate works only while it gets a redstone signal. Right click it to travel. Your return gate sits on a redstone block, so it is always on.\n\nCrafting the counters needs Realm Cogs, dropped by constructs.',
    'PISTON KARST\nPale limestone towers.\n\nTrigger: pressure plate\nResponse: crushing passage\nCounter: Piston Brace (right click a Crusher)\n\nThe vault at the end opens with two levers and an AND gate.',
    'SWITCHYARD FLATS\nRust, slag and rails.\n\nTrigger: Tripper Rail\nResponse: hazard diversion into a spike pit\nCounter: Pulse Injector (burns out a Hazard Switch, and stuns constructs).',
    'RESONANCE HOLLOWS\nCrystal caves deep below.\n\nTrigger: footstep vibration (sculk sensor)\nResponse: gate lockdown\nCounter: Decoy Beacon\n\nSneak: the Bell Stalker hunts by sound.',
    'SLUICE GARDENS\nCanals and copper.\n\nTrigger: bridge tripwire\nResponse: floodgate surge\nCounter: Repeater Wrench\n\nSneak + right click with the wrench arms or disarms any trap.',
    'KILN BARRENS\nBlack rock and fire.\n\nTrigger: pressure plate\nResponse: fire-charge volley\nCounter: Signal Jammer (hold it: traps within 10 blocks stay quiet).',
    'TRIPWIRE BRIAR\nThorns and rusted towers.\n\nTrigger: tripwire\nResponse: arrow volley\nCounter: Insulated Cutters (cut wire safely, disarm launchers).',
    'THE CONSTRUCTS\nKarst Colossus: slams the ground.\nSwitchback Crawler: charges, fast on rails.\nBell Stalker: blind, tolls.\nSluice Chainjaw: drags you in.\nKiln Brute: fire volleys.\nSpool Weaver: roots you with wire.',
    'THE MACHINE-BOUND\nLeaking Cell, Detonator Husk, Tripwire Brood, Kilnbound, Living Capacitor, Relay Strider, Bellows Hog and Flesh Press: creatures rebuilt by the realm\'s machines. Watch their tricks.',
    'BUILD YOUR OWN\nTraps are normal redstone parts: any signal fires them, and touching traps of the same kind fire one after another. Mix them with gates, sensors and wireless from RedstonePlus.',
    'EXAMPLE\nLaser Sensor across a door -> Delay -> row of Kiln Turrets.\nPlayer Detector -> Counter -> Signal Display shows visitors.\nClock -> Sequencer -> three Floodgates = a wave pool.',
    'CREATURE JOBS 1\nColossus: its slam sets off every trap nearby.\nCrawler: drags minecarts, fast on rails.\nBell Stalker: its toll trips sculk sensors.\nChainjaw: whirlpool in water.\nKiln Brute: smelts items dropped near it.\nSpool Weaver: shear it for string.',
    'CREATURE JOBS 2\nLeaking Cell: its spill makes crops grow.\nDetonator Husk: its blast fires traps.\nBrood: bursts into cave spiders.\nKilnbound: fires the Kiln Turrets.\nCapacitor: shocks traps.\nBellows Hog: feed coal, it smelts.\nFlesh Press: give a redstone block, it guards you.',
]
BOOK_DE = [
    'REDSTONE-REICH\nFeldführer\n\nDiese Welt läuft mit Redstone. Jedes Biom hat eine Maschinenkreatur, eine Falle mit AUSLÖSER und REAKTION und ein GEGENMITTEL.\n\nDie Ankunftsplattform zeigt funktionierende Beispielschaltungen.',
    'UNTERWEGS\n\nDas Reichstor funktioniert nur mit Redstone-Signal. Rechtsklick zum Reisen. Dein Rückkehr-Tor steht auf einem Redstone-Block und ist immer an.\n\nFür die Gegenmittel brauchst du Reichszahnräder von Konstrukten.',
    'KOLBENKARST\nHelle Kalktürme.\n\nAuslöser: Druckplatte\nReaktion: Quetschgang\nGegenmittel: Kolbenstrebe (Rechtsklick auf Zermalmer)\n\nDer Tresor am Ende öffnet mit zwei Hebeln und einem UND-Gatter.',
    'WEICHENEBENE\nRost, Schlacke und Schienen.\n\nAuslöser: Auslöseschiene\nReaktion: Umleitung in eine Stachelgrube\nGegenmittel: Impulsinjektor (brennt Weichen durch, lähmt Konstrukte).',
    'RESONANZHÖHLEN\nKristallhöhlen tief unten.\n\nAuslöser: Schrittvibration (Sculk-Sensor)\nReaktion: Abriegelung\nGegenmittel: Köderleuchtfeuer\n\nSchleichen: der Glockenpirscher jagt nach Gehör.',
    'SCHLEUSENGÄRTEN\nKanäle und Kupfer.\n\nAuslöser: Brückendraht\nReaktion: Flutwelle\nGegenmittel: Verstärkerschlüssel\n\nSchleichen + Rechtsklick mit dem Schlüssel macht jede Falle scharf oder sicher.',
    'BRENNOFEN-ÖDE\nSchwarzer Fels und Feuer.\n\nAuslöser: Druckplatte\nReaktion: Feuerkugel-Salve\nGegenmittel: Signalstörer (halten: Fallen im Umkreis von 10 Blöcken bleiben still).',
    'STOLPERDRAHT-DICKICHT\nDornen und rostige Türme.\n\nAuslöser: Stolperdraht\nReaktion: Pfeilsalve\nGegenmittel: Isolierte Zange (Draht sicher schneiden, Werfer entschärfen).',
    'DIE KONSTRUKTE\nKarst-Koloss: stampft.\nWeichenkriecher: stürmt, schnell auf Schienen.\nGlockenpirscher: blind, läutet.\nKettenkiefer: zieht dich heran.\nBrennofen-Rohling: Feuersalven.\nSpulenweber: fesselt mit Draht.',
    'DIE MASCHINENGEBUNDENEN\nLeckende Zelle, Zünderhülle, Stolperdraht-Brut, Ofengebundener, Lebender Kondensator, Relais-Schreiter, Blasebalg-Keiler und Fleischpresse: von den Maschinen umgebaute Kreaturen.',
    'SELBST BAUEN\nFallen sind normale Redstone-Teile: jedes Signal löst sie aus, und angrenzende Fallen derselben Art folgen nacheinander. Kombiniere sie mit Gattern, Sensoren und Funk aus RedstonePlus.',
    'BEISPIEL\nLasersensor vor einer Tür -> Verzögerer -> Reihe Brennofen-Geschütze.\nSpielerdetektor -> Zähler -> Signalanzeige zählt Besucher.\nTaktgeber -> Sequenzer -> drei Flutschleusen = Wellenbad.',
    'KREATUR-AUFGABEN 1\nKoloss: sein Stampfen löst alle Fallen aus.\nKriecher: zieht Loren, schnell auf Schienen.\nGlockenpirscher: sein Läuten weckt Sculk-Sensoren.\nKettenkiefer: Strudel im Wasser.\nBrennofen-Rohling: schmilzt Items neben sich.\nSpulenweber: scheren gibt Faden.',
    'KREATUR-AUFGABEN 2\nLeckende Zelle: macht Pflanzen wachsen.\nZünderhülle: Explosion löst Fallen aus.\nBrut: platzt zu Höhlenspinnen.\nOfengebundener: zündet Geschütze.\nKondensator: schockt Fallen.\nBlasebalg-Keiler: mit Kohle füttern, er schmilzt.\nFleischpresse: Redstone-Block geben, sie beschützt dich.',
]
name(f'book.{NS}.realm_guide.title', 'Redstone Realm Field Guide', 'Feldführer Redstone-Reich')
for i, (e, g) in enumerate(zip(BOOK_EN, BOOK_DE)):
    name(f'book.{NS}.realm_guide.{i}', e, g)

for lang, entries in (('en_us', en), ('de_de', de)):
    path = os.path.join(ASSETS, 'lang', lang + '.json')
    with open(path, encoding='utf-8') as f:
        current = json.load(f)
    current.update(entries)
    write(path, current)
print('realm generated:', len(en), 'lang entries')
