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

# ================================================================================================ the realm's own blocks
def overlay_ore(base, ore_path, stone_path='block/stone', threshold=40):
    """Puts the ore specks of a vanilla ore texture on our own rock."""
    ore = vanilla(ore_path)
    stone = vanilla(stone_path)
    out = base.copy()
    op, sp, bp = ore.load(), stone.load(), out.load()
    for y in range(16):
        for x in range(16):
            o, s_ = op[x, y], sp[x, y]
            if sum(abs(o[i] - s_[i]) for i in range(3)) > threshold:
                bp[x, y] = o
    return out


def bands(img, dark, every=5, width=2):
    out = img.copy()
    px = out.load()
    d = tuple(round(v * 255) for v in rgb(dark))
    for y in range(16):
        if y % every < width:
            for x in range(16):
                r, g, b, a = px[x, y]
                px[x, y] = (round(r * 0.35 + d[0] * 0.65), round(g * 0.35 + d[1] * 0.65), round(b * 0.35 + d[2] * 0.65), a)
    return out


def traces(img, color, seed=7):
    """Circuit-like lines of light."""
    out = img.copy()
    px = out.load()
    c = tuple(round(v * 255) for v in rgb(color)) + (255,)
    r = random.Random(seed)
    for _ in range(4):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(9):
            px[x, y] = c
            if r.random() < 0.5:
                x = (x + r.choice((-1, 1))) % 16
            else:
                y = (y + r.choice((-1, 1))) % 16
    return out


realmstone = specks(tint(vanilla('block/stone'), '#a8524a', gain=1.3), '#d02a1a', 0.02)
TERRAIN = {
    # id: (texture, tool, english, german, english desc, german desc)
    'realmstone': (realmstone, 'pickaxe', 'Realmstone', 'Reichsstein', 'The rock the Redstone Realm is made of.', 'Der Fels, aus dem das Redstone-Reich besteht.'),
    'deep_realmstone': (specks(tint(vanilla('block/deepslate'), '#6a2e2e', gain=1.4), '#ff3020', 0.015), 'pickaxe', 'Deep Realmstone', 'Tiefer Reichsstein',
                        'Realmstone from deep below.', 'Reichsstein aus der Tiefe.'),
    'realmstone_bricks': (tint(vanilla('block/stone_bricks'), '#b0564a', gain=1.3), 'pickaxe', 'Realmstone Bricks', 'Reichssteinziegel',
                          'Building block of the realm\'s old builders.', 'Baublock der alten Erbauer des Reichs.'),
    'hematite': (bands(tint(vanilla('block/stone'), '#c04a36', gain=1.35), '#3a1a18'), 'pickaxe', 'Hematite', 'Hämatit',
                 'Banded red rock of the Hematite Scarps.', 'Gebänderter roter Fels der Hämatit-Klippen.'),
    'dark_hematite': (specks(tint(vanilla('block/stone'), '#4a2a28', gain=1.3), '#d03a2a', 0.03), 'pickaxe', 'Dark Hematite', 'Dunkler Hämatit',
                      'The dark bands of the scarps.', 'Die dunklen Bänder der Klippen.'),
    'cinder_rock': (specks(tint(vanilla('block/blackstone'), '#4a3e3a', gain=1.45), '#ff8a20', 0.03), 'pickaxe', 'Cinder Rock', 'Schlackenfels',
                    'Black rock of the Kiln Barrens.', 'Schwarzer Fels der Brennofen-Öde.'),
    'tempest_basalt': (specks(tint(vanilla('block/polished_basalt_side'), '#303844', gain=1.5), '#40e0c8', 0.02), 'pickaxe', 'Tempest Basalt', 'Sturmbasalt',
                       'Glassy black stone of the Tempest Shoals.', 'Glasiger schwarzer Stein der Sturmbänke.'),
    'frost_realmstone': (specks(tint(vanilla('block/stone'), '#7a8a9c', gain=1.35), '#ffffff', 0.08), 'pickaxe', 'Frost Realmstone', 'Frost-Reichsstein',
                         'Frozen rock of the Frostwork Wastes. Slippery.', 'Gefrorener Fels der Frostwerk-Öde. Rutschig.'),
    'fossil_circuit': (traces(tint(vanilla('block/stone'), '#7a3030', gain=1.3), '#ff4a30'), 'pickaxe', 'Fossil Circuit', 'Fossile Schaltung',
                       'Ancient circuits turned to stone. Glows faintly.', 'Uralte, versteinerte Schaltungen. Leuchtet schwach.'),
    'redstone_vein': (traces(tint(vanilla('block/redstone_block'), '#e02a1a', gain=1.1), '#ffb080', 3), 'pickaxe', 'Redstone Vein', 'Redstone-Ader',
                      'Glowing redstone grown through the rock.', 'Leuchtendes Redstone, durch den Fels gewachsen.'),
    'salt_crust': (specks(tint(vanilla('block/calcite'), '#f2ede4', gain=1.05), '#d8cfc0', 0.12), 'pickaxe', 'Salt Crust', 'Salzkruste',
                   'White crust of the Oxide Salt Flats.', 'Weiße Kruste der Oxid-Salzebene.'),
    'rust_plating': (specks(tint(vanilla('block/iron_block'), '#b0582e', gain=1.2), '#6a2a14', 0.1), 'pickaxe', 'Rust Plating', 'Rostplatten',
                     'Rusted plates from the realm\'s derelict machines.', 'Verrostete Platten der verlassenen Maschinen des Reichs.'),
    'shell_plating': (specks(tint(vanilla('block/iron_block'), '#8e9098', gain=1.1), '#a0382a', 0.05), 'pickaxe', 'Shell Plating', 'Hüllenplatten',
                      'Plating of the great shell casings in the dunes.', 'Beplankung der großen Geschosshülsen in den Dünen.'),
    'rust_sand': (recolor(vanilla('block/red_sand'), '#b8442e'), 'shovel', 'Rust Sand', 'Rostsand', 'Red sand of the Arsenal Dunes. Falls like sand.',
                  'Roter Sand der Arsenal-Dünen. Fällt wie Sand.'),
    'red_clay': (tint(vanilla('block/clay'), '#b8503e', gain=1.3), 'shovel', 'Red Clay', 'Roter Ton', 'Clay of the Red Clay Fen.', 'Ton des Rotton-Moors.'),
    'fen_mud': (tint(vanilla('block/mud'), '#7a3024', gain=1.5), 'shovel', 'Fen Mud', 'Moorschlamm', 'Sticky red mud.', 'Zäher roter Schlamm.'),
    'canal_moss': (tint(vanilla('block/moss_block'), '#46b89c', gain=1.4), 'shovel', 'Canal Moss', 'Kanalmoos', 'Teal moss of the Sluice Gardens.',
                   'Türkises Moos der Schleusengärten.'),
    'briar_soil': (tint(vanilla('block/rooted_dirt'), '#5a4a2c', gain=1.4), 'shovel', 'Briar Soil', 'Dornenerde', 'Root-tangled ground of the Tripwire Briar.',
                   'Wurzeldurchzogener Boden des Stolperdraht-Dickichts.'),
    'heather_turf': (specks(tint(vanilla('block/moss_block'), '#a8304a', gain=1.5), '#e05070', 0.1), 'shovel', 'Heather Turf', 'Heidetorf',
                     'Crimson heather of the Landmark Moors.', 'Karminrote Heide der Wegmarken-Moore.'),
    'root_soil': (specks(tint(vanilla('block/rooted_dirt'), '#3a2222', gain=1.4), '#ff3a2a', 0.05), 'shovel', 'Root Soil', 'Wurzelerde',
                  'Dark soil of the Vein Mire, threaded with glowing roots.', 'Dunkle Erde des Adermoors mit leuchtenden Wurzeln.'),
    'grove_moss': (tint(vanilla('block/moss_block'), '#2e6a6a', gain=1.3), 'shovel', 'Grove Moss', 'Hainmoos', 'Deep teal moss of the Lamplit Grove.',
                   'Tiefgrünes Moos des Lampenhains.'),
}
for bid, (img, tool, e, g, de_, dg) in TERRAIN.items():
    save(img, 'block', bid)
    simple_block(bid)
    self_drop(bid)
    name(f'block.{NS}.{bid}', e, g, de_, dg)
    (pickaxe if tool == 'pickaxe' else shovel).append(f'{NS}:{bid}')

# logs
pale_side = tint(vanilla('block/stripped_birch_log'), '#ece2d2', gain=1.1)
pale_top = tint(vanilla('block/stripped_birch_log_top'), '#e0d4c0', gain=1.1)
save(pale_side, 'block', 'pale_root_log')
save(pale_top, 'block', 'pale_root_log_top')
save(traces(pale_side, '#ff3020', 11), 'block', 'vein_log')
save(pale_top, 'block', 'vein_log_top')
axes = []
for log, e, g, de_, dg in (('pale_root_log', 'Pale Root', 'Bleichwurzel', 'Wood of the giant trees of the Lamplit Grove.', 'Holz der Riesenbäume des Lampenhains.'),
                           ('vein_log', 'Vein Root', 'Aderwurzel', 'Pale root with glowing red veins.', 'Bleichwurzel mit leuchtenden roten Adern.')):
    model(f'block/{log}', {'parent': 'minecraft:block/cube_column', 'textures': {'end': f'{NS}:block/{log}_top', 'side': f'{NS}:block/{log}'}})
    model(f'block/{log}_horizontal', {'parent': 'minecraft:block/cube_column_horizontal', 'textures': {'end': f'{NS}:block/{log}_top', 'side': f'{NS}:block/{log}'}})
    blockstate(log, {'variants': {'axis=y': {'model': f'{NS}:block/{log}'}, 'axis=z': {'model': f'{NS}:block/{log}_horizontal', 'x': 90},
                                  'axis=x': {'model': f'{NS}:block/{log}_horizontal', 'x': 90, 'y': 90}}})
    item_model(log, f'{NS}:block/{log}')
    self_drop(log)
    name(f'block.{NS}.{log}', e, g, de_, dg)
    axes.append(f'{NS}:{log}')

# plants
PLANTS = {
    'crimson_heather': (tint(vanilla('block/short_grass'), '#c83656', gain=1.9), 'Crimson Heather', 'Karminheide', 'Heather of the moors.', 'Heide der Moore.'),
    'fen_reed': (tint(vanilla('block/sugar_cane'), '#8e2c26', gain=1.5), 'Fen Reed', 'Moorschilf', 'Dark red reeds of the fens.', 'Dunkelrotes Schilf der Moore.'),
    'red_coral_shrub': (recolor(vanilla('block/fire_coral'), '#e8303e'), 'Red Coral Shrub', 'Rote Korallenstaude',
                        'Stone coral that grows on dry land in the realm. Glows a little.', 'Steinkoralle, die im Reich an Land wächst. Leuchtet etwas.'),
    'pale_stalk': (tint(vanilla('block/crimson_roots'), '#f2e6d8', gain=1.4), 'Pale Stalk', 'Bleichstängel', 'Glowing pale stalks of the Vein Mire.',
                   'Leuchtende bleiche Stängel des Adermoors.'),
    'salt_brush': (tint(vanilla('block/dead_bush'), '#dcd4c4', gain=1.6), 'Salt Brush', 'Salzbusch', 'Dry brush of the salt flats.', 'Trockener Busch der Salzebene.'),
    'copper_reed': (tint(vanilla('block/sugar_cane'), '#e89aac', gain=1.5), 'Copper Reed', 'Kupferschilf', 'Pink reeds along the canals.', 'Rosa Schilf an den Kanälen.'),
    'lichen_tuft': (tint(vanilla('block/short_grass'), '#eca04a', gain=1.9), 'Lichen Tuft', 'Flechtenbüschel', 'Orange lichen of the karst.', 'Orange Flechte des Karsts.'),
    'cinder_bloom': (recolor(vanilla('block/crimson_fungus'), '#ff8a24'), 'Cinder Bloom', 'Glutblüte', 'A flower that grows from hot cinders. Glows.',
                     'Eine Blume, die aus heißer Schlacke wächst. Leuchtet.'),
    'frost_fern': (tint(vanilla('block/fern'), '#cfe4f4', gain=1.6), 'Frost Fern', 'Frostfarn', 'Fern of the frozen wastes.', 'Farn der gefrorenen Öde.'),
}
for pid, (img, e, g, de_, dg) in PLANTS.items():
    save(img, 'block', pid)
    model(f'block/{pid}', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{NS}:block/{pid}'}})
    blockstate(pid, {'variants': {'': {'model': f'{NS}:block/{pid}'}}})
    item_model(pid, texture=f'{NS}:block/{pid}')
    self_drop(pid)
    name(f'block.{NS}.{pid}', e, g, de_, dg)

# redstone crystal cluster (placed like an amethyst cluster, on any face)
save(recolor(vanilla('block/amethyst_cluster'), '#ff2a1a', 0.05), 'block', 'redstone_cluster')
model('block/redstone_cluster', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{NS}:block/redstone_cluster'}})
cluster_state = vanilla_json('assets/minecraft/blockstates/amethyst_cluster.json')
for v in cluster_state['variants'].values():
    v['model'] = f'{NS}:block/redstone_cluster'
blockstate('redstone_cluster', cluster_state)
item_model('redstone_cluster', texture=f'{NS}:block/redstone_cluster')
write(os.path.join(DATA, 'loot_table', 'blocks', 'redstone_cluster.json'), {
    'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/redstone_cluster',
    'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:redstone', 'functions': [
        {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 5}, 'add': False},
        {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:uniform_bonus_count', 'parameters': {'bonusMultiplier': 1}}]}]}]})
pickaxe.append(f'{NS}:redstone_cluster')
name(f'block.{NS}.redstone_cluster', 'Redstone Crystal', 'Redstone-Kristall', 'Glowing crystal of pure redstone. Breaks into redstone dust.',
     'Leuchtender Kristall aus reinem Redstone. Zerfällt zu Redstone-Staub.')

# ores in realmstone: same drops as their vanilla ores
for oid, vanilla_ore, e, g in (('realm_redstone_ore', 'redstone_ore', 'Realm Redstone Ore', 'Reichs-Redstone-Erz'),
                               ('realm_iron_ore', 'iron_ore', 'Realm Iron Ore', 'Reichs-Eisenerz'),
                               ('realm_copper_ore', 'copper_ore', 'Realm Copper Ore', 'Reichs-Kupfererz')):
    save(overlay_ore(realmstone, f'block/{vanilla_ore}'), 'block', oid)
    simple_block(oid)
    loot = vanilla_json(f'data/minecraft/loot_table/blocks/{vanilla_ore}.json')
    write(os.path.join(DATA, 'loot_table', 'blocks', oid + '.json'),
          json.loads(json.dumps(loot).replace(f'minecraft:{vanilla_ore}', f'{NS}:{oid}').replace(f'minecraft:blocks/{vanilla_ore}', f'{NS}:blocks/{oid}')))
    pickaxe.append(f'{NS}:{oid}')
    needs_iron.append(f'{NS}:{oid}') if oid == 'realm_redstone_ore' else None
    name(f'block.{NS}.{oid}', e, g, f'Found in the realm\'s rock. Drops the same as its Overworld ore.',
         f'Kommt im Fels des Reichs vor. Lässt dasselbe fallen wie das Erz der Oberwelt.')
if os.path.exists(os.path.join(DATA, 'loot_table', 'blocks', 'realm_redstone_ore.json')):
    lit = vanilla_json('assets/minecraft/blockstates/redstone_ore.json')
    blockstate('realm_redstone_ore', {'variants': {'lit=false': {'model': f'{NS}:block/realm_redstone_ore'},
                                                   'lit=true': {'model': f'{NS}:block/realm_redstone_ore'}}})

# ================================================================================================ the realm portal
portal = recolor(vanilla('block/nether_portal'), '#ff2a1a', 0.05)
save(portal, 'block', 'realm_portal')
with open(os.path.join(ASSETS, 'textures', 'block', 'realm_portal.png.mcmeta'), 'w', encoding='utf-8') as f:
    f.write(JAR.read('assets/minecraft/textures/block/nether_portal.png.mcmeta').decode('utf-8'))
for suffix in ('ns', 'ew'):
    m = vanilla_json(f'assets/minecraft/models/block/nether_portal_{suffix}.json')
    m['textures'] = {'particle': f'{NS}:block/realm_portal', 'portal': f'{NS}:block/realm_portal'}
    m['render_type'] = 'minecraft:translucent'
    model(f'block/realm_portal_{suffix}', m)
blockstate('realm_portal', {'variants': {'axis=x': {'model': f'{NS}:block/realm_portal_ns'}, 'axis=z': {'model': f'{NS}:block/realm_portal_ew'}}})
name(f'block.{NS}.realm_portal', 'Realm Portal', 'Reichsportal')

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
              'briar_thorns', 'tripper_rail', 'decoy_beacon', *TRAPS):
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
# ================================================================================================ recipes
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
# The realm uses only its own blocks, plants and features. Vanilla feature *types* (ore, random_patch, block_column...)
# are fine; what they place is always the realm's own content.
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


def underground(count, lo, hi, direction='down', rarity=None):
    p = [{'type': 'minecraft:rarity_filter', 'chance': rarity}] if rarity else []
    return p + [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
                {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}},
                {'type': 'minecraft:environment_scan', 'direction_of_search': direction, 'max_steps': 16, 'target_condition': {'type': 'minecraft:solid'},
                 'allowed_search_condition': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:air', 'minecraft:cave_air']}},
                {'type': 'minecraft:random_offset', 'xz_spread': 0, 'y_spread': 1 if direction == 'down' else -1}, {'type': 'minecraft:biome'}]


def none_feature(kind):
    return {'type': f'{NS}:{kind}', 'config': {}}


def state(name_, props=None):
    s_ = {'Name': name_ if ':' in name_ else f'{NS}:{name_}'}
    if props:
        s_['Properties'] = props
    return s_


def spire(name_, body, accent, h, r, count):
    placed(name_, {'type': f'{NS}:spire', 'config': {'body': state(body), 'accent': state(accent), 'min_height': h[0], 'max_height': h[1],
                                                      'min_radius': r[0], 'max_radius': r[1]}}, surface(count))


def patch(name_, plant, tries, count, spread=6):
    placed(name_, {'type': 'minecraft:random_patch', 'config': {'tries': tries, 'xz_spread': spread, 'y_spread': 2, 'feature': {
        'feature': {'type': 'minecraft:simple_block', 'config': {'to_place': {'type': 'minecraft:simple_state_provider', 'state': state(plant)}}},
        'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
            {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}, {'type': 'minecraft:would_survive', 'state': state(plant)}]}}]}}},
          surface(count))


def rock(name_, block, count=None, rarity=None):
    placed(name_, {'type': 'minecraft:forest_rock', 'config': {'state': state(block)}}, surface(count, rarity))


def ore(name_, block, size, count, lo, hi):
    placed(name_, {'type': 'minecraft:ore', 'config': {'size': size, 'discard_chance_on_air_exposure': 0.0, 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': f'{NS}:realm_base_stone'}, 'state': state(block)}]}},
        [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
         {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}},
         {'type': 'minecraft:biome'}])


def cluster(name_, count, lo, hi, direction):
    facing = 'up' if direction == 'down' else 'down'
    placed(name_, {'type': 'minecraft:simple_block', 'config': {'to_place': {'type': 'minecraft:simple_state_provider',
                                                                            'state': state('redstone_cluster', {'facing': facing, 'waterlogged': 'false'})}}},
           underground(count, lo, hi, direction))


def site(kind, rarity, heightmap='WORLD_SURFACE_WG'):
    placed(kind, none_feature(kind), surface(rarity=rarity, heightmap=heightmap))


# rock shapes
spire('karst_spire', 'karst_limestone', 'lichen_karst', (12, 34), (2, 4), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 2})
spire('kiln_spire', 'cinder_rock', 'minecraft:magma_block', (8, 22), (1, 3), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 2})
spire('hoodoo', 'hematite', 'dark_hematite', (10, 28), (2, 4), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 2})
spire('tempest_pillar', 'tempest_basalt', 'redstone_vein', (8, 30), (1, 3), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 2})
spire('rubedo_spire', 'redstone_vein', 'realmstone_bricks', (6, 16), (1, 2), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 1})
spire('frost_spire', 'frost_realmstone', 'minecraft:packed_ice', (10, 26), (1, 3), 1)
spire('dune_rock', 'realmstone', 'rust_sand', (3, 8), (2, 4), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 1})
rock('slag_heap', 'slag', 1)
rock('sulfur_rock', 'sulfur_crust', rarity=2)
rock('salt_mound', 'salt_crust', 1)
rock('scree', 'realmstone', rarity=3)

# plants
for pid, tries, count in (('crimson_heather', 48, 6), ('fen_reed', 40, 5), ('red_coral_shrub', 24, 3), ('pale_stalk', 32, 4), ('salt_brush', 12, 2),
                          ('copper_reed', 32, 4), ('lichen_tuft', 24, 3), ('cinder_bloom', 16, 2), ('frost_fern', 16, 2)):
    patch(pid + '_patch', pid, tries, count)
patch('briar_patch', 'briar_thorns', 24, 3)

# ores and crystals
ore('ore_realm_redstone', 'realm_redstone_ore', 8, 10, -64, 64)
ore('ore_realm_iron', 'realm_iron_ore', 9, 12, -32, 96)
ore('ore_realm_copper', 'realm_copper_ore', 10, 8, 0, 96)
ore('ore_redstone_vein', 'redstone_vein', 12, 10, -64, 40)
ore('resonant_crystal_ore', 'resonant_crystal', 9, 14, -60, 40)
cluster('cave_clusters_floor', 18, -60, 50, 'down')
cluster('cave_clusters_ceiling', 12, -60, 50, 'up')

# landforms: once per chunk, over the columns of their own biome
for shape in ('dunes', 'mesas', 'ponds', 'crevasses', 'lava_channels', 'terrace_pools'):
    placed(shape, none_feature(shape), [])

# trap sites, scenery and machines: rare and spread out
placed('resonance_gatehouse', none_feature('resonance_gatehouse'), underground(1, -40, 30, rarity=5))
for kind, rarity in (('crusher_passage', 28), ('switchyard_junction', 26), ('sluice_bridge', 26), ('kiln_bridge', 26), ('briar_ambush', 26),
                     ('rail_line', 14), ('tower', 22), ('ruin', 20), ('monolith', 30), ('crashed_shell', 18), ('crystal_dome', 30),
                     ('boardwalk', 16), ('kiln_hut', 20), ('aqueduct', 16), ('ice_rails', 22), ('scrap', 18),
                     ('lamp_pylon', 70), ('crusher_mill', 48), ('pump_station', 44), ('minecart_loop', 48), ('storm_spire', 30),
                     ('bell_tower', 44), ('beast_cage', 56), ('laser_post', 40)):
    site(kind, rarity)
placed('giant_tree', none_feature('giant_tree'), surface(count={'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 1}, rarity=2))

# one ordered list per generation step, so every biome uses the same order
STEPS = [
    [],
    ['minecraft:lake_lava_surface'],
    ['redstoneplus:dunes', 'redstoneplus:mesas', 'redstoneplus:ponds', 'redstoneplus:crevasses', 'redstoneplus:lava_channels',
     'redstoneplus:terrace_pools', 'redstoneplus:karst_spire', 'redstoneplus:kiln_spire', 'redstoneplus:hoodoo', 'redstoneplus:tempest_pillar', 'redstoneplus:rubedo_spire',
     'redstoneplus:frost_spire', 'redstoneplus:dune_rock', 'redstoneplus:slag_heap', 'redstoneplus:sulfur_rock', 'redstoneplus:salt_mound',
     'redstoneplus:scree'],
    ['redstoneplus:resonance_gatehouse'],
    ['redstoneplus:crusher_passage', 'redstoneplus:switchyard_junction', 'redstoneplus:sluice_bridge', 'redstoneplus:kiln_bridge',
     'redstoneplus:briar_ambush', 'redstoneplus:rail_line', 'redstoneplus:tower', 'redstoneplus:ruin', 'redstoneplus:monolith',
     'redstoneplus:crashed_shell', 'redstoneplus:crystal_dome', 'redstoneplus:boardwalk', 'redstoneplus:kiln_hut', 'redstoneplus:aqueduct',
     'redstoneplus:ice_rails', 'redstoneplus:scrap', 'redstoneplus:lamp_pylon', 'redstoneplus:crusher_mill', 'redstoneplus:pump_station',
     'redstoneplus:minecart_loop', 'redstoneplus:storm_spire', 'redstoneplus:bell_tower', 'redstoneplus:beast_cage', 'redstoneplus:laser_post'],
    [],
    ['redstoneplus:ore_realm_redstone', 'redstoneplus:ore_realm_iron', 'redstoneplus:ore_realm_copper', 'redstoneplus:ore_redstone_vein',
     'redstoneplus:resonant_crystal_ore'],
    ['redstoneplus:cave_clusters_floor', 'redstoneplus:cave_clusters_ceiling'],
    [],
    ['redstoneplus:giant_tree', 'redstoneplus:crimson_heather_patch', 'redstoneplus:fen_reed_patch', 'redstoneplus:red_coral_shrub_patch',
     'redstoneplus:pale_stalk_patch', 'redstoneplus:salt_brush_patch', 'redstoneplus:copper_reed_patch', 'redstoneplus:lichen_tuft_patch',
     'redstoneplus:cinder_bloom_patch', 'redstoneplus:frost_fern_patch', 'redstoneplus:briar_patch'],
    ['minecraft:freeze_top_layer'],
]
EVERYWHERE = {'redstoneplus:ore_realm_redstone', 'redstoneplus:ore_realm_iron', 'redstoneplus:ore_realm_copper', 'redstoneplus:cave_clusters_floor',
              'redstoneplus:scree', 'minecraft:freeze_top_layer'}


def features(*wanted):
    w = {f if ':' in f else f'{NS}:{f}' for f in wanted} | EVERYWHERE
    return [[f for f in step if f in w] for step in STEPS]


def spawn(entity, weight, lo, hi):
    return {'type': entity if ':' in entity else f'{NS}:{entity}', 'weight': weight, 'minCount': lo, 'maxCount': hi}


def c(hexcolor):
    return int(hexcolor.lstrip('#'), 16)


def fx(particle, probability):
    return {'probability': probability, 'options': particle if isinstance(particle, dict) else {'type': particle}}


RED_DUST = {'type': 'minecraft:dust', 'color': [1.0, 0.15, 0.08], 'scale': 1.0}

# name: english, german, temperature, downfall, colours (sky, fog, water, water fog, grass/foliage), particle, features, monsters, creatures, costs
BIOMES = {
    'piston_karst': ('Piston Karst', 'Kolbenkarst', 0.9, 0.3, ('#78a8ff', '#d8c8a8', '#3a9ad8', '#10304a', '#c0a060'), None,
                     features('karst_spire', 'crusher_passage', 'crusher_mill', 'lichen_tuft_patch', 'beast_cage'),
                     [('karst_colossus', 25, 1, 1), ('relay_strider', 20, 1, 1), ('detonator_husk', 30, 1, 2)], [('flesh_press', 3, 1, 1)], ['karst_colossus']),
    'switchyard_flats': ('Switchyard Flats', 'Weichenebene', 1.2, 0.1, ('#c07aa0', '#d08a70', '#8a4a3a', '#3a1a10', '#a8905a'), fx(RED_DUST, 0.004),
                         features('slag_heap', 'switchyard_junction', 'rail_line', 'tower', 'lamp_pylon', 'minecart_loop', 'salt_brush_patch'),
                         [('switchback_crawler', 40, 1, 2), ('detonator_husk', 30, 1, 2), ('kilnbound', 20, 1, 1)], [('bellows_hog', 10, 2, 3)],
                         ['switchback_crawler']),
    'sluice_gardens': ('Sluice Gardens', 'Schleusengärten', 0.7, 0.9, ('#8ab0a0', '#a8c8b8', '#3ab8a0', '#10403a', '#4a9a70'), None,
                       features('sluice_bridge', 'aqueduct', 'pump_station', 'copper_reed_patch', 'fen_reed_patch'),
                       [('sluice_chainjaw', 40, 1, 2), ('leaking_cell', 30, 1, 2), ('living_capacitor', 20, 1, 2)], [], ['sluice_chainjaw']),
    'kiln_barrens': ('Kiln Barrens', 'Brennofen-Öde', 2.0, 0.0, ('#b0603a', '#8a4a2a', '#c06a2a', '#3a1a0a', '#6a5a3a'), fx('minecraft:white_ash', 0.01),
                     features('lava_channels', 'kiln_spire', 'sulfur_rock', 'lamp_pylon', 'minecraft:lake_lava_surface', 'kiln_bridge', 'cinder_bloom_patch'),
                     [('kiln_brute', 30, 1, 1), ('kilnbound', 40, 1, 2), ('detonator_husk', 15, 1, 1)], [('bellows_hog', 15, 2, 3)], ['kiln_brute']),
    'tripwire_briar': ('Tripwire Briar', 'Stolperdraht-Dickicht', 0.7, 0.8, ('#d8b060', '#b8a060', '#4a7a4a', '#1a2a1a', '#5a6a2a'), None,
                       features('briar_ambush', 'briar_patch', 'laser_post', 'tower', 'ruin', 'lichen_tuft_patch'),
                       [('spool_weaver', 40, 1, 2), ('tripwire_brood', 35, 1, 2), ('leaking_cell', 15, 1, 2)], [], ['spool_weaver']),
    'arsenal_dunes': ('Arsenal Dunes', 'Arsenal-Dünen', 1.6, 0.0, ('#e04a36', '#c83a2a', '#9a2020', '#3a0a0a', '#b04a30'), fx(RED_DUST, 0.006),
                      features('dunes', 'dune_rock', 'crashed_shell', 'tower', 'lamp_pylon', 'scrap', 'minecart_loop', 'salt_brush_patch'),
                      [('switchback_crawler', 30, 1, 2), ('detonator_husk', 35, 1, 2), ('kilnbound', 25, 1, 2)], [('bellows_hog', 8, 1, 2)],
                      ['switchback_crawler']),
    'rubedo_gardens': ('Rubedo Gardens', 'Rubedo-Gärten', 0.9, 0.6, ('#e87070', '#d85a5a', '#e0202a', '#5a0a0a', '#c03a3a'), fx(RED_DUST, 0.008),
                       features('terrace_pools', 'rubedo_spire', 'crystal_dome', 'aqueduct', 'red_coral_shrub_patch', 'crimson_heather_patch', 'pump_station'),
                       [('living_capacitor', 40, 1, 3), ('relay_strider', 20, 1, 1), ('leaking_cell', 20, 1, 2)], [], []),
    'landmark_moors': ('Landmark Moors', 'Wegmarken-Moore', 0.6, 0.6, ('#c83030', '#a82424', '#7a1a1a', '#2a0808', '#a02a40'), None,
                       features('monolith', 'bell_tower', 'crimson_heather_patch', 'ruin'),
                       [('bell_stalker', 20, 1, 1), ('relay_strider', 25, 1, 1), ('detonator_husk', 20, 1, 2)], [('flesh_press', 3, 1, 1)], ['bell_stalker']),
    'red_clay_fen': ('Red Clay Fen', 'Rotton-Moor', 0.8, 0.9, ('#b83e32', '#98322a', '#a82418', '#3a0a08', '#8a3a2a'), None,
                     features('ponds', 'boardwalk', 'kiln_hut', 'pump_station', 'fen_reed_patch'),
                     [('sluice_chainjaw', 35, 1, 2), ('leaking_cell', 30, 1, 3), ('living_capacitor', 25, 1, 2)], [], ['sluice_chainjaw']),
    'hematite_scarps': ('Hematite Scarps', 'Hämatit-Klippen', 1.1, 0.2, ('#d86040', '#c84e3a', '#7a2a2a', '#2a0a0a', '#a0402a'), fx(RED_DUST, 0.004),
                        features('mesas', 'hoodoo', 'beast_cage', 'ruin', 'red_coral_shrub_patch', 'crusher_mill'),
                        [('karst_colossus', 20, 1, 1), ('tripwire_brood', 30, 1, 2), ('detonator_husk', 25, 1, 2)], [('flesh_press', 4, 1, 1)],
                        ['karst_colossus']),
    'tempest_shoals': ('Tempest Shoals', 'Sturmbänke', 0.5, 0.9, ('#3e4260', '#2e3446', '#22b0a0', '#0a2a28', '#2a6a6a'), fx('minecraft:electric_spark', 0.004),
                       features('tempest_pillar', 'storm_spire', 'aqueduct'),
                       [('sluice_chainjaw', 35, 1, 2), ('relay_strider', 25, 1, 1)], [], ['sluice_chainjaw']),
    'frostwork_wastes': ('Frostwork Wastes', 'Frostwerk-Öde', -0.6, 0.5, ('#8a9ab0', '#a8b8c8', '#3a6a9a', '#0a1a2a', '#8aa0b0'), fx('minecraft:white_ash', 0.02),
                         features('crevasses', 'frost_spire', 'ice_rails', 'tower', 'frost_fern_patch'),
                         [('switchback_crawler', 25, 1, 2), ('spool_weaver', 25, 1, 2), ('karst_colossus', 15, 1, 1)], [], ['karst_colossus']),
    'vein_mire': ('Vein Mire', 'Adermoor', 0.8, 0.9, ('#40403a', '#4a4a40', '#3a1414', '#140404', '#4a3a2a'), fx('minecraft:crimson_spore', 0.01),
                  features('ponds', 'pale_stalk_patch', 'boardwalk', 'fen_reed_patch', 'kiln_hut'),
                  [('leaking_cell', 35, 1, 3), ('tripwire_brood', 30, 1, 2), ('spool_weaver', 20, 1, 1)], [], []),
    'oxide_salt_flats': ('Oxide Salt Flats', 'Oxid-Salzebene', 1.3, 0.0, ('#eca858', '#e8b878', '#60a080', '#1a3a2a', '#c09060'), fx(RED_DUST, 0.003),
                         features('salt_mound', 'scrap', 'rail_line', 'ruin', 'lamp_pylon', 'salt_brush_patch', 'tower'),
                         [('switchback_crawler', 30, 1, 2), ('kilnbound', 25, 1, 2), ('detonator_husk', 25, 1, 2)], [('bellows_hog', 10, 2, 3)],
                         ['switchback_crawler']),
    'lamplit_grove': ('Lamplit Grove', 'Lampenhain', 0.6, 0.8, ('#46306a', '#3e2c64', '#2a3a5a', '#0a0a1a', '#2e6a6a'), fx('minecraft:warped_spore', 0.01),
                      features('giant_tree', 'crimson_heather_patch', 'pale_stalk_patch', 'bell_tower'),
                      [('spool_weaver', 30, 1, 2), ('relay_strider', 25, 1, 1), ('bell_stalker', 15, 1, 1)], [], []),
    'resonance_hollows': ('Resonance Hollows', 'Resonanzhöhlen', 0.6, 0.4, ('#6a5aa8', '#3a2a6a', '#40c8ff', '#10205a', '#6a5aa8'), fx('minecraft:glow', 0.008),
                          features('resonance_gatehouse', 'resonant_crystal_ore', 'cave_clusters_ceiling'),
                          [('bell_stalker', 30, 1, 1), ('tripwire_brood', 35, 1, 2), ('living_capacitor', 25, 1, 2)], [], ['bell_stalker']),
    'circuit_fossil_beds': ('Circuit Fossil Beds', 'Schaltungs-Fossilbetten', 0.9, 0.4, ('#8a1a1a', '#6a1010', '#a02020', '#300808', '#8a2020'), fx(RED_DUST, 0.01),
                            features('ore_redstone_vein', 'cave_clusters_ceiling', 'red_coral_shrub_patch'),
                            [('tripwire_brood', 30, 1, 2), ('living_capacitor', 30, 1, 2), ('relay_strider', 25, 1, 1)], [], []),
}
for biome_name, (e, g, temp, rain, cols, particle, feats, monsters, creatures, costs) in BIOMES.items():
    sky, fog, water, water_fog, plant = cols
    effects = {'sky_color': c(sky), 'fog_color': c(fog), 'water_color': c(water), 'water_fog_color': c(water_fog),
               'grass_color': c(plant), 'foliage_color': c(plant),
               'mood_sound': {'sound': 'minecraft:ambient.cave', 'tick_delay': 6000, 'block_search_extent': 8, 'offset': 2.0}}
    if particle:
        effects['particle'] = particle
    biome = {
        'has_precipitation': rain > 0.2, 'temperature': temp, 'downfall': rain, 'effects': effects,
        'spawners': {'monster': [spawn(*m) for m in monsters], 'creature': [spawn(*cr) for cr in creatures], 'ambient': [], 'axolotls': [],
                     'underground_water_creature': [], 'water_creature': [], 'water_ambient': [], 'misc': []},
        'spawn_costs': {f'{NS}:{m}': {'energy_budget': 0.12, 'charge': 0.7} for m in costs},
        'carvers': {'air': ['minecraft:cave', 'minecraft:cave_extra_underground', 'minecraft:canyon']},
        'features': feats,
    }
    write(os.path.join(DATA, 'worldgen', 'biome', biome_name + '.json'), biome)
    name(f'biome.{NS}.{biome_name}', e, g)


# ---- surface rules: every realm biome paints its own ground; nothing falls through to vanilla grass or sand
def cond(condition, then):
    return {'type': 'minecraft:condition', 'if_true': condition, 'then_run': then}


def seq(*rules):
    return {'type': 'minecraft:sequence', 'sequence': list(rules)}


def blk(name_, props=None):
    return {'type': 'minecraft:block', 'result_state': state(name_, props)}


def biome_is(*names):
    return {'type': 'minecraft:biome', 'biome_is': [f'{NS}:{n}' for n in names]}


def depth(surface_type='floor', add=False, offset=0, secondary=0):
    return {'type': 'minecraft:stone_depth', 'offset': offset, 'add_surface_depth': add, 'secondary_depth_range': secondary, 'surface_type': surface_type}


def noise_range(lo, hi, noise='minecraft:surface'):
    return {'type': 'minecraft:noise_threshold', 'noise': noise, 'min_threshold': lo, 'max_threshold': hi}


def y_above(y):
    return {'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': 0, 'add_stone_depth': False}


def not_(condition):
    return {'type': 'minecraft:not', 'invert': condition}


ABOVE_WATER = {'type': 'minecraft:water', 'offset': -1, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
ON_FLOOR, UNDER_FLOOR = depth('floor'), depth('floor', add=True)
DEEP_FLOOR = depth('floor', add=True, secondary=6)
PRELIM = {'type': 'minecraft:above_preliminary_surface'}


def ground(top, underwater, under, deeper=None):
    """top: a block, or a list of (noise_lo, noise_hi, block) patches ending with the default block."""
    if isinstance(top, str):
        top_rule = blk(top)
    else:
        top_rule = seq(*[cond(noise_range(lo, hi), blk(b)) for lo, hi, b in top[:-1]], blk(top[-1]))
    rules = [cond(ON_FLOOR, seq(cond(ABOVE_WATER, top_rule), blk(underwater))), cond(UNDER_FLOOR, blk(under))]
    if deeper:
        rules.append(cond(DEEP_FLOOR, blk(deeper)))
    return seq(*rules)


GROUND = {
    'piston_karst': ground([(0.1, 9, 'lichen_karst'), 'karst_limestone'], 'karst_limestone', 'karst_limestone'),
    'switchyard_flats': ground([(0.45, 9, 'slag'), (-9, -0.5, 'realmstone'), 'rusted_soil'], 'slag', 'rusted_soil'),
    'sluice_gardens': ground([(0.35, 9, 'realmstone_bricks'), 'canal_moss'], 'fen_mud', 'red_clay'),
    'kiln_barrens': ground([(0.4, 9, 'sulfur_crust'), (-9, -0.55, 'minecraft:magma_block'), 'cinder_rock'], 'cinder_rock', 'cinder_rock'),
    'tripwire_briar': ground([(0.3, 9, 'grove_moss'), 'briar_soil'], 'fen_mud', 'briar_soil'),
    'arsenal_dunes': ground('rust_sand', 'rust_sand', 'rust_sand', 'realmstone'),
    'rubedo_gardens': ground([(0.5, 9, 'redstone_vein'), (-9, -0.4, 'red_clay'), 'heather_turf'], 'red_clay', 'realmstone'),
    'landmark_moors': ground([(0.55, 9, 'realmstone'), 'heather_turf'], 'fen_mud', 'red_clay'),
    'red_clay_fen': ground([(0.2, 9, 'fen_mud'), 'red_clay'], 'fen_mud', 'red_clay'),
    'hematite_scarps': ground([(0.3, 9, 'dark_hematite'), 'hematite'], 'hematite', 'hematite'),
    'tempest_shoals': ground('tempest_basalt', 'tempest_basalt', 'tempest_basalt'),
    'frostwork_wastes': ground([(0.35, 9, 'minecraft:packed_ice'), 'minecraft:snow_block'], 'frost_realmstone', 'frost_realmstone'),
    'vein_mire': ground([(0.55, 9, 'redstone_vein'), 'root_soil'], 'root_soil', 'root_soil'),
    'oxide_salt_flats': ground([(-0.04, 0.04, 'redstone_vein'), (0.5, 9, 'rusted_soil'), 'salt_crust'], 'salt_crust', 'salt_crust'),
    'lamplit_grove': ground('grove_moss', 'root_soil', 'root_soil'),
}
# whole cliffs in some biomes are their own rock; the scarps are banded
cliffs = [cond(biome_is('piston_karst'), cond(y_above(40), blk('karst_limestone'))),
          cond(biome_is('kiln_barrens'), cond(y_above(36), blk('cinder_rock'))),
          cond(biome_is('tempest_shoals'), cond(y_above(20), blk('tempest_basalt'))),
          cond(biome_is('frostwork_wastes'), cond(y_above(40), blk('frost_realmstone')))]
band_rules = []
for y in range(40, 260, 7):
    band_rules.append(cond(y_above(y), cond(not_(y_above(y + 3)), blk('dark_hematite'))))
cliffs.append(cond(biome_is('hematite_scarps'), cond(y_above(40), seq(*band_rules, blk('hematite')))))

realm_rules = seq(
    # underground biomes: crystal-lit floors and pale ceilings / fossil circuits and red veins
    cond(biome_is('resonance_hollows'), seq(
        cond(ON_FLOOR, seq(cond(noise_range(0.35, 9), blk('resonant_crystal')), cond(noise_range(-0.2, 9), blk('salt_crust')), blk('deep_realmstone'))),
        cond(depth('ceiling'), blk('salt_crust')))),
    cond(biome_is('circuit_fossil_beds'), seq(
        cond(ON_FLOOR, seq(cond(noise_range(0.4, 9), blk('redstone_vein')), blk('fossil_circuit'))),
        cond(depth('ceiling'), blk('dark_hematite')),
        cond(depth('floor', add=True, secondary=4), blk('fossil_circuit')))),
    cond(PRELIM, seq(*[cond(biome_is(b), rule) for b, rule in GROUND.items()],
                     # anything left: plain realmstone, never vanilla grass
                     cond(ON_FLOOR, blk('realmstone')), cond(UNDER_FLOOR, blk('realmstone')))),
    *cliffs,
    # the deep rock
    cond({'type': 'minecraft:vertical_gradient', 'random_name': 'redstoneplus:deep_realmstone',
          'true_at_and_below': {'absolute': 0}, 'false_at_and_above': {'absolute': 8}}, blk('deep_realmstone')),
)
settings = vanilla_json('data/minecraft/worldgen/noise_settings/overworld.json')
settings['default_block'] = state('realmstone')
settings['ore_veins_enabled'] = False
settings['surface_rule']['sequence'].insert(1, realm_rules)
write(os.path.join(DATA, 'worldgen', 'noise_settings', 'redstone_realm.json'), settings)

write(os.path.join(DATA, 'dimension_type', 'redstone_realm.json'), {
    'ultrawarm': False, 'natural': True, 'coordinate_scale': 1.0, 'has_skylight': True, 'has_ceiling': False, 'ambient_light': 0.0,
    'monster_spawn_light_level': {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 7}, 'monster_spawn_block_light_limit': 0,
    'piglin_safe': False, 'bed_works': True, 'respawn_anchor_works': False, 'has_raids': False,
    'logical_height': 384, 'min_y': -64, 'height': 384, 'infiniburn': '#minecraft:infiniburn_overworld', 'effects': 'minecraft:overworld'})


# ---- biome layout: seas and coasts, mountains by heat, and a temperature x humidity grid on the flatter land
def point(biome, t=(-1, 1), h=(-1, 1), cont=(-0.11, 1.2), ero=(-1, 1), weird=(-1, 1), dep=0.0):
    return {'biome': f'{NS}:{biome}', 'parameters': {'temperature': list(t), 'humidity': list(h), 'continentalness': list(cont),
                                                     'erosion': list(ero), 'weirdness': list(weird), 'depth': dep, 'offset': 0.0}}


T = [(-1.0, -0.45), (-0.45, -0.15), (-0.15, 0.2), (0.2, 0.55), (0.55, 1.0)]
H = [(-1.0, -0.2), (-0.2, 0.3), (0.3, 1.0)]
FLAT = [['frostwork_wastes', 'frostwork_wastes', 'frostwork_wastes'],
        ['oxide_salt_flats', 'landmark_moors', 'lamplit_grove'],
        ['switchyard_flats', 'tripwire_briar', 'vein_mire'],
        ['arsenal_dunes', 'rubedo_gardens', 'red_clay_fen'],
        ['kiln_barrens', 'arsenal_dunes', 'sluice_gardens']]
PEAKS = ['frostwork_wastes', 'hematite_scarps', 'piston_karst', 'hematite_scarps', 'kiln_barrens']
points = [point('tempest_shoals', cont=(-1.2, -0.35)),
          point('sluice_gardens', t=(0.0, 1.0), cont=(-0.35, -0.11)),
          point('red_clay_fen', t=(-1.0, 0.0), cont=(-0.35, -0.11))]
for ti, tr in enumerate(T):
    points.append(point(PEAKS[ti], t=tr, ero=(-1.0, -0.375)))
    for hi, hr in enumerate(H):
        points.append(point(FLAT[ti][hi], t=tr, h=hr, ero=(-0.375, 1.0)))
points.append(point('circuit_fossil_beds', h=(-1.0, 0.0), cont=(-1.2, 1.2), dep=[0.2, 0.9]))
points.append(point('resonance_hollows', h=(0.0, 1.0), cont=(-1.2, 1.2), dep=[0.2, 0.9]))
write(os.path.join(DATA, 'dimension', 'redstone_realm.json'), {
    'type': f'{NS}:redstone_realm',
    'generator': {'type': 'minecraft:noise', 'settings': f'{NS}:redstone_realm', 'biome_source': {'type': 'minecraft:multi_noise', 'biomes': points}}})

# ---- tags: the realm's rock is where its ores grow and where caves are carved
REALM_ROCK = [f'{NS}:{b}' for b in ('realmstone', 'deep_realmstone', 'hematite', 'dark_hematite', 'cinder_rock', 'tempest_basalt', 'frost_realmstone',
                                    'fossil_circuit', 'salt_crust', 'karst_limestone')]
write(os.path.join(DATA, 'tags', 'block', 'realm_base_stone.json'), {'replace': False, 'values': REALM_ROCK})
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'overworld_carver_replaceables.json'),
          REALM_ROCK + [f'{NS}:{b}' for b in ('rust_sand', 'red_clay', 'fen_mud', 'canal_moss', 'briar_soil', 'heather_turf', 'root_soil', 'grove_moss',
                                              'lichen_karst', 'rusted_soil', 'slag', 'sulfur_crust', 'redstone_vein')])
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'mineable', 'axe.json'), axes)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'logs.json'), axes)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'mineable', 'pickaxe.json'), pickaxe)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'mineable', 'shovel.json'), shovel)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'needs_iron_tool.json'), needs_iron)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'sword_efficient.json'), [f'{NS}:{p}' for p in PLANTS] + [f'{NS}:briar_thorns'])
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'replaceable_by_trees.json'), [f'{NS}:{p}' for p in PLANTS])

# ---- things the realm no longer has: the gate, the arrival station and its signs, the workshop
for path in ('models/block/realm_gate.json', 'models/block/realm_gate_on.json', 'models/item/realm_gate.json', 'blockstates/realm_gate.json',
             'textures/block/realm_gate_side.png', 'textures/block/realm_gate_top.png', 'textures/block/realm_gate_top_on.png'):
    p_ = os.path.join(ASSETS, *path.split('/'))
    if os.path.exists(p_):
        os.remove(p_)
for path in ('recipe/realm_gate.json', 'loot_table/blocks/realm_gate.json', 'loot_table/chests/realm_arrival.json', 'loot_table/chests/realm_workshop.json',
             'worldgen/configured_feature/circuit_workshop.json', 'worldgen/placed_feature/circuit_workshop.json',
             'worldgen/configured_feature/coal_heap.json', 'worldgen/placed_feature/coal_heap.json'):
    p_ = os.path.join(DATA, *path.split('/'))
    if os.path.exists(p_):
        os.remove(p_)
# removed blocks must leave every tag too: one unknown entry makes Minecraft drop the whole tag
REMOVED = {f'{NS}:realm_gate'}
for tag_path in glob.glob(os.path.join(ROOT, 'data', '**', 'tags', '**', '*.json'), recursive=True):
    with open(tag_path, encoding='utf-8') as f:
        tag_data = json.load(f)
    kept = [v for v in tag_data.get('values', []) if v not in REMOVED]
    if kept != tag_data.get('values', []):
        tag_data['values'] = kept
        write(tag_path, tag_data)
for lang in ('en_us', 'de_de'):
    path = os.path.join(ASSETS, 'lang', lang + '.json')
    with open(path, encoding='utf-8') as f:
        current = json.load(f)
    stale = [k for k in current if k.startswith(f'sign.{NS}.') or k.startswith(f'block.{NS}.realm_gate')
             or k in (f'message.{NS}.gate_unpowered', f'message.{NS}.realm_first', f'message.{NS}.realm_enter', f'message.{NS}.realm_leave',
                      f'message.{NS}.realm_missing')]
    for k in stale:
        del current[k]
    write(path, current)


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
    'wrench_sneak': ('Sneak + right click to switch this trap between armed and safe.', 'Schleichen + Rechtsklick schaltet diese Falle zwischen scharf und sicher.'),
    'trap_armed': ('Trap armed', 'Falle scharf'),
    'trap_disarmed': ('Trap disarmed', 'Falle entschärft'),
}
for key, (e, g) in MESSAGES.items():
    name(f'message.{NS}.{key}', e, g)

BOOK_EN = [
    'REDSTONE REALM\nField Guide\n\nA world that runs on redstone. Every land has its own machines, creatures and traps. Nothing explains itself: watch the machines to see how they work.',
    'GETTING THERE\n\nBuild a frame of redstone blocks like a Nether portal (inside at least 2 wide, 3 high) and light it with flint and steel. Step in. A portal waits on the other side to bring you back.',
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
    'REDSTONE-REICH\nFeldführer\n\nEine Welt, die mit Redstone läuft. Jedes Land hat eigene Maschinen, Kreaturen und Fallen. Nichts erklärt sich selbst: beobachte die Maschinen, dann siehst du, wie sie funktionieren.',
    'DER WEG HIN\n\nBaue einen Rahmen aus Redstone-Blöcken wie ein Netherportal (innen mindestens 2 breit, 3 hoch) und entzünde ihn mit Feuerzeug. Tritt hinein. Drüben wartet ein Portal für den Rückweg.',
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
