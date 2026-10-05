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
save(recolor(vanilla('block/amethyst_block'), '#ffb030'), 'block', 'resonant_crystal')
save(specks(tint(vanilla('block/tuff'), '#f0d840', gain=1.7), '#fff4a0', 0.08), 'block', 'sulfur_crust')
for b in ('karst_limestone', 'karst_bricks', 'rusted_soil', 'slag', 'resonant_crystal', 'sulfur_crust'):
    simple_block(b)
model('block/lichen_karst', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
    'top': f'{NS}:block/lichen_karst_top', 'side': f'{NS}:block/lichen_karst_side', 'bottom': f'{NS}:block/karst_limestone'}})
blockstate('lichen_karst', {'variants': {'': {'model': f'{NS}:block/lichen_karst'}}})
item_model('lichen_karst', f'{NS}:block/lichen_karst')
pickaxe += [f'{NS}:{b}' for b in ('karst_limestone', 'lichen_karst', 'karst_bricks', 'slag', 'resonant_crystal', 'sulfur_crust')]
shovel.append(f'{NS}:rusted_soil')

briar = specks(tint(vanilla('block/dead_bush'), '#5a3a22', gain=1.6), '#c02020', 0.08)
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
    'tempest_basalt': (specks(tint(vanilla('block/polished_basalt_side'), '#3a3030', gain=1.5), '#ff7a30', 0.02), 'pickaxe', 'Tempest Basalt', 'Sturmbasalt',
                       'Glassy black stone of the Tempest Shoals.', 'Glasiger schwarzer Stein der Sturmbänke.'),
    'frost_realmstone': (specks(tint(vanilla('block/stone'), '#c8a090', gain=1.35), '#fff0e0', 0.08), 'pickaxe', 'Frost Realmstone', 'Frost-Reichsstein',
                         'Rime-crusted rock of the Frostwork Wastes. Slippery.', 'Bereifter Fels der Frostwerk-Öde. Rutschig.'),
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
    'canal_moss': (tint(vanilla('block/moss_block'), '#c8702a', gain=1.4), 'shovel', 'Canal Moss', 'Kanalmoos', 'Rust-orange moss of the Sluice Gardens.',
                   'Rostoranges Moos der Schleusengärten.'),
    'briar_soil': (tint(vanilla('block/rooted_dirt'), '#5a4a2c', gain=1.4), 'shovel', 'Briar Soil', 'Dornenerde', 'Root-tangled ground of the Tripwire Briar.',
                   'Wurzeldurchzogener Boden des Stolperdraht-Dickichts.'),
    'heather_turf': (specks(tint(vanilla('block/moss_block'), '#a8304a', gain=1.5), '#e05070', 0.1), 'shovel', 'Heather Turf', 'Heidetorf',
                     'Crimson heather of the Landmark Moors.', 'Karminrote Heide der Wegmarken-Moore.'),
    'root_soil': (specks(tint(vanilla('block/rooted_dirt'), '#3a2222', gain=1.4), '#ff3a2a', 0.05), 'shovel', 'Root Soil', 'Wurzelerde',
                  'Dark soil of the Vein Mire, threaded with glowing roots.', 'Dunkle Erde des Adermoors mit leuchtenden Wurzeln.'),
    'grove_moss': (tint(vanilla('block/moss_block'), '#8a2a2a', gain=1.3), 'shovel', 'Grove Moss', 'Hainmoos', 'Deep crimson moss of the Lamplit Grove.',
                   'Tiefrotes Moos des Lampenhains.'),
    # the old world's building materials, found in the Foundry Cities
    'cracked_realmstone_bricks': (tint(vanilla('block/cracked_stone_bricks'), '#b0564a', gain=1.3), 'pickaxe', 'Cracked Realmstone Bricks',
                                  'Rissige Reichssteinziegel', 'Bricks split by the Last Toll.', 'Vom Letzten Schlag gesprungene Ziegel.'),
    'chiseled_realmstone_bricks': (traces(tint(vanilla('block/chiseled_stone_bricks'), '#b0564a', gain=1.3), '#ff4a30', 5), 'pickaxe',
                                   'Chiseled Realmstone Bricks', 'Gemeißelte Reichssteinziegel',
                                   'Carved with the circuit sigils of the Wirewrights. The lines still glow.',
                                   'Mit den Schaltkreis-Siegeln der Drahtwerker verziert. Die Linien glühen noch.'),
    'bell_bronze': (specks(tint(vanilla('block/gold_block'), '#b8803c', gain=1.05), '#5a3418', 0.07), 'pickaxe', 'Bell Bronze', 'Glockenbronze',
                    'Metal of the Great Bell. Shards of it rained down across the realm when it tore free.',
                    'Metall der Großen Glocke. Splitter davon regneten über das Reich, als sie sich losriss.'),
    'wirewright_tiles': (specks(tint(vanilla('block/polished_blackstone_bricks'), '#4a3034', gain=1.5), '#ff3a2a', 0.02), 'pickaxe', 'Wirewright Tiles',
                         'Drahtwerker-Fliesen', 'Floor tiles of the Wirewrights\' halls.', 'Bodenfliesen aus den Hallen der Drahtwerker.'),
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
    'frost_fern': (tint(vanilla('block/fern'), '#f4dccf', gain=1.6), 'Frost Fern', 'Frostfarn', 'Fern of the frozen wastes.', 'Farn der gefrorenen Öde.'),
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
save(recolor(vanilla('block/copper_grate'), '#ff8a3a', 0.05), 'block', 'floodgate_front_open')
locked = vanilla('block/copper_grate').copy()
lp = locked.load()
for i in range(16):
    lp[i, 7] = lp[i, 8] = (70, 60, 60, 255)
save(locked, 'block', 'floodgate_front_locked')
orientable_trap('floodgate', 'minecraft:block/cut_copper', 'minecraft:block/cut_copper', 'minecraft:block/copper_grate',
                f'{NS}:block/floodgate_front_open', f'{NS}:block/floodgate_front_locked')

# kiln turret: blast furnace
orientable_trap('kiln_turret', 'minecraft:block/blast_furnace_side', 'minecraft:block/blast_furnace_top', 'minecraft:block/blast_furnace_front',
                'minecraft:block/blast_furnace_front_on', 'minecraft:block/furnace_front')

# volley launcher: mossy dispenser
orientable_trap('volley_launcher', f'{NS}:block/cracked_realmstone_bricks', f'{NS}:block/realmstone_bricks', 'minecraft:block/dispenser_front',
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
        dp[x, y] = (255, 176, 48, 255) if (x + y) % 3 else (255, 232, 170, 255)
save(dside, 'block', 'decoy_beacon_side')
save(recolor(vanilla('block/amethyst_block'), '#ffb030'), 'block', 'decoy_beacon_core')
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
    'spark_mite': ('Spark Mite', 'Funkenmilbe', [('minecraft:redstone', 1, 2, 1), ('minecraft:copper_ingot', 0, 1, 0.3)]),
    'lamp_moth': ('Lamp Moth', 'Lampenmotte', [('minecraft:glowstone_dust', 0, 2, 1)]),
    'scrap_jackal': ('Scrap Jackal', 'Schrottschakal', [('minecraft:iron_nugget', 1, 4, 1), (f'{NS}:realm_cog', 1, 1, 0.3)]),
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


LORE_IDS = range(6)


def chest(name_, pools):
    write(os.path.join(DATA, 'loot_table', 'chests', name_ + '.json'), {'type': 'minecraft:chest', 'pools': pools,
                                                                        'random_sequence': f'{NS}:chests/{name_}'})


for chest_name, counter in COUNTERS.items():
    count = [2, 4] if counter.endswith('piston_brace') else [1, 1]
    chest(chest_name, [
        {'rolls': {'type': 'minecraft:uniform', 'min': 4, 'max': 7}, 'bonus_rolls': 0, 'entries': COMMON},
        {'rolls': 1, 'bonus_rolls': 0, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.75}],
         'entries': [entry(counter, count[0], count[1])]},
        {'rolls': 1, 'bonus_rolls': 0, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.35}],
         'entries': [entry(f'{NS}:etched_plate_{i}') for i in range(1, len(LORE_IDS) + 1)]}])
chest('realm_relic', [
    {'rolls': {'type': 'minecraft:uniform', 'min': 5, 'max': 8}, 'bonus_rolls': 0, 'entries': COMMON + [entry(f'{NS}:bell_bronze', 1, 4, 10)]},
    {'rolls': 1, 'bonus_rolls': 0, 'entries': [entry(f'{NS}:etched_plate_{i}') for i in range(1, len(LORE_IDS) + 1)]}])
# ================================================================================================ the Grid and the Sealed Reach
# Everything here is drawn from scratch (no vanilla textures), so it can be regenerated without the Minecraft jar.
GRID_RND = random.Random(20261004)


def blank(color):
    return Image.new('RGBA', (16, 16), tuple(int(color[i:i + 2], 16) for i in (1, 3, 5)) + (255,))


def noisy(img, amount, rnd):
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            f = 1 + rnd.uniform(-amount, amount)
            px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)
    return img


def lightline_texture():
    """A Tron floor tile: dark glass over red light, a glowing rim, a dashed bright lane down the middle, nodes in the corners."""
    img = noisy(blank('#1c0a08'), 0.18, GRID_RND)
    px = img.load()
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = (255, 58, 28, 255)
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= i <= 14:
                px[x, y] = (150, 26, 14, 255)
    for i in range(2, 14):
        if i % 4 != 1:
            px[7, i] = px[8, i] = (255, 176, 112, 255)
            px[i, 7] = px[i, 8] = (255, 140, 80, 255) if i % 4 == 3 else px[i, 7]
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 1), (14, 1), (1, 14), (14, 14)):
        px[x, y] = (255, 210, 160, 255)
    return img


def grid_beacon_texture():
    img = blank('#2a0c08')
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 2:
                px[x, y] = (255, 240, 200, 255)
            elif d < 4:
                px[x, y] = (255, 160, 80, 255)
            elif d < 6:
                px[x, y] = (255, 70, 30, 255)
            elif d < 7:
                px[x, y] = (120, 24, 12, 255)
            else:
                px[x, y] = (255, 58, 28, 255) if (x + y) % 2 == 0 else (60, 16, 10, 255)
    return img


def blight_texture():
    """Black-red crust split by cracks that still glow."""
    img = noisy(blank('#2a0e0a'), 0.3, GRID_RND)
    px = img.load()
    for _ in range(3):
        x, y = GRID_RND.randrange(16), GRID_RND.randrange(16)
        for k in range(14):
            px[x % 16, y % 16] = (255, 110, 30, 255) if k % 3 else (255, 190, 90, 255)
            if GRID_RND.random() < 0.5:
                x += GRID_RND.choice((-1, 1))
            else:
                y += GRID_RND.choice((-1, 1))
    for _ in range(10):
        px[GRID_RND.randrange(16), GRID_RND.randrange(16)] = (70, 20, 14, 255)
    return img


def quarantine_texture():
    """Hazard plating: diagonal yellow and black stripes, rivets in the corners, rust bleeding through."""
    img = blank('#2a1a14')
    px = img.load()
    for y in range(16):
        for x in range(16):
            if ((x + y) // 4) % 2 == 0:
                px[x, y] = (232, 176, 32, 255)
            if GRID_RND.random() < 0.08:
                r, g, b, a = px[x, y]
                px[x, y] = (min(255, r // 2 + 90), g // 2 + 20, b // 2, 255)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[x, y] = (200, 190, 170, 255)
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = (60, 40, 30, 255)
    return noisy(img, 0.08, GRID_RND)


GRID_BLOCKS = {
    'lightline': (lightline_texture(), 'Lightline', 'Lichtbahn',
                  'A tile of the Grid. Light cycles lock onto rows of it and race along them; Trackwrights lay it. Breaking it breaks the Concordance.',
                  'Eine Kachel des Rasters. Lichträder rasten auf Reihen davon ein und rasen darauf entlang; Bahnwerker verlegen sie. Sie zu zerstören bricht die Eintracht.'),
    'grid_beacon': (grid_beacon_texture(), 'Grid Beacon', 'Rasterleuchtfeuer',
                    'The bright light at the Grid\'s nodes, pylons and waystops. Built by the Wirewrights: do not break it.',
                    'Das helle Licht an den Knoten, Masten und Haltestellen des Rasters. Von den Drahtwerkern gebaut: nicht zerstören.'),
    'blight_crust': (blight_texture(), 'Blight Crust', 'Fäulniskruste', 'The cracked, faintly glowing ground of the Sealed Reach.',
                     'Der rissige, schwach glühende Boden der Versiegelten Weite.'),
    'quarantine_plating': (quarantine_texture(), 'Quarantine Plating', 'Quarantäneplatten',
                           'The plating of the walls around the Sealed Reach. Nearly unbreakable, and breaking it breaks the Concordance.',
                           'Die Platten der Mauern um die Versiegelte Weite. Fast unzerstörbar, und sie zu zerstören bricht die Eintracht.'),
}
for bid, (img, e, g, de_, dg) in GRID_BLOCKS.items():
    save(img, 'block', bid)
    simple_block(bid)
    self_drop(bid)
    name(f'block.{NS}.{bid}', e, g, de_, dg)
    pickaxe.append(f'{NS}:{bid}')
needs_iron.append(f'{NS}:quarantine_plating')
# natural ground a Trackwright may pave: the realm's rock and soils and plain vanilla ground, never builds, ores, logs or valuables
write(os.path.join(DATA, 'tags', 'block', 'lightline_pavable.json'), {'replace': False, 'values': [
    f'{NS}:{b}' for b in ('realmstone', 'deep_realmstone', 'hematite', 'dark_hematite', 'cinder_rock', 'tempest_basalt', 'frost_realmstone',
                          'fossil_circuit', 'salt_crust', 'karst_limestone', 'lichen_karst', 'rust_sand', 'red_clay', 'fen_mud', 'canal_moss',
                          'briar_soil', 'heather_turf', 'root_soil', 'grove_moss', 'rusted_soil', 'slag', 'sulfur_crust', 'redstone_vein',
                          'blight_crust')] + [
    f'minecraft:{b}' for b in ('dirt', 'coarse_dirt', 'rooted_dirt', 'grass_block', 'podzol', 'mud', 'clay', 'gravel', 'sand', 'red_sand',
                               'stone', 'granite', 'diorite', 'andesite', 'tuff', 'calcite', 'deepslate', 'blackstone', 'basalt',
                               'terracotta', 'red_terracotta', 'orange_terracotta', 'snow_block', 'packed_ice', 'magma_block', 'netherrack')]})
shaped('lightline', ['BBB', 'RGR', 'BBB'], {'B': f'{NS}:realmstone_bricks', 'R': 'minecraft:redstone', 'G': 'minecraft:glowstone_dust'},
       f'{NS}:lightline', 6)
shaped('grid_beacon', ['CRC', 'RGR', 'CRC'], {'C': f'{NS}:realm_cog', 'R': 'minecraft:redstone', 'G': 'minecraft:glowstone'}, f'{NS}:grid_beacon')
shaped('quarantine_plating', ['IYI', 'YIY', 'IYI'], {'I': 'minecraft:iron_ingot', 'Y': 'minecraft:yellow_dye'}, f'{NS}:quarantine_plating', 4)

# the Cycle Key
P_KEY = {'a': '#2a0c08', 'b': '#ff3a1c', 'c': '#ffb070', 'd': '#6a6a72', 'e': '#a0a0a8', 'f': '#d8d8e0'}
save(sprite([
    '................', '..........aaa...', '.........abbba..', '........abcccba.', '........abc.cba.', '........abcccba.',
    '.........abbba..', '........daaaa...', '.......ded......', '......ded.......', '.....dedd.......', '....ded.d.......',
    '...ded..........', '..ded.d.........', '..dd............', '................'], P_KEY), 'item', 'cycle_key')
item_model('cycle_key')
name(f'item.{NS}.cycle_key', 'Cycle Key', 'Lichtrad-Schlüssel',
     'The key to your light cycle. Use it to call the cycle and ride it; sneak-use to switch its automatic return on or off.',
     'Der Schlüssel zu deinem Lichtrad. Benutzen ruft das Rad und setzt dich darauf; Schleichen + Benutzen schaltet seine automatische Rückkehr an oder aus.')
for i, (e, g) in enumerate((('Use: call your light cycle and ride it', 'Benutzen: Lichtrad rufen und aufsteigen'),
                            ('Sneak + use: automatic return on/off', 'Schleichen + Benutzen: automatische Rückkehr an/aus'),
                            ('Ride onto a Lightline to lock on. Jump: overdrive', 'Auf eine Lichtbahn fahren, um einzurasten. Springen: Schub'))):
    name(f'item.{NS}.cycle_key.tip.{i}', e, g)
shaped('cycle_key', [' C ', 'IRI', ' I '], {'C': f'{NS}:realm_cog', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone_block'}, f'{NS}:cycle_key')
name(f'entity.{NS}.light_cycle', 'Light Cycle', 'Lichtrad')

# the Grid's builder and the things of the Sealed Reach
GRID_CREATURES = {
    'trackwright': ('Trackwright', 'Bahnwerker', [(f'{NS}:lightline', 2, 5, 1), (f'{NS}:realm_cog', 1, 2, 1), ('minecraft:redstone', 1, 3, 1)]),
    'wirewraith': ('Wirewraith', 'Drahtgespenst', [('minecraft:chain', 1, 3, 1), ('minecraft:redstone', 2, 6, 1), (f'{NS}:bell_bronze', 0, 1, 0.4),
                                                   (f'{NS}:etched_plate_6', 1, 1, 0.08)]),
    'maw_engine': ('Maw Engine', 'Schlundmaschine', [('minecraft:coal', 2, 6, 1), ('minecraft:iron_ingot', 1, 3, 1), (f'{NS}:realm_cog', 1, 3, 1),
                                                     ('minecraft:magma_cream', 0, 2, 0.5)]),
}
for mob, (e, g, drops) in GRID_CREATURES.items():
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
chest('grid_depot', [
    {'rolls': {'type': 'minecraft:uniform', 'min': 4, 'max': 7}, 'bonus_rolls': 0,
     'entries': COMMON + [entry(f'{NS}:lightline', 6, 18, 18), entry(f'{NS}:grid_beacon', 1, 2, 6)]},
    {'rolls': 1, 'bonus_rolls': 0, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.3}],
     'entries': [entry(f'{NS}:etched_plate_{i}') for i in range(1, len(LORE_IDS) + 1)]}])

# texts: the cycle, its HUD and the map, the key binding, and the signs around the Sealed Reach
for key, e, g in (
        ('message.redstoneplus.cycle_welcome.0', 'A light cycle answers your arrival. It is yours, and the realm will keep it by your side.',
         'Ein Lichtrad antwortet auf deine Ankunft. Es gehört dir, und das Reich hält es an deiner Seite.'),
        ('message.redstoneplus.cycle_welcome.1', 'Right click it to ride. Roll onto a glowing Lightline and it locks on and races along it; tap A or D before a junction to turn.',
         'Rechtsklick zum Aufsteigen. Fahr auf eine leuchtende Lichtbahn: das Rad rastet ein und rast darauf entlang; tippe vor einer Kreuzung A oder D, um abzubiegen.'),
        ('message.redstoneplus.cycle_welcome.2', 'Hold jump for overdrive, S to brake. Press M for the Grid Map. Lost it? The Cycle Key calls it back.',
         'Springen halten für Schub, S bremst. M öffnet die Rasterkarte. Verloren? Der Lichtrad-Schlüssel ruft es zurück.'),
        ('message.redstoneplus.cycle_returned', 'Your light cycle rematerialises beside you.', 'Dein Lichtrad materialisiert neben dir.'),
        ('message.redstoneplus.cycle_parked', 'Light cycle put away. Use the Cycle Key to call it back.', 'Lichtrad weggestellt. Der Lichtrad-Schlüssel ruft es zurück.'),
        ('message.redstoneplus.cycle_auto_on', 'Automatic return on: your cycle will always come back to you.', 'Automatische Rückkehr an: dein Rad kommt immer zu dir zurück.'),
        ('message.redstoneplus.cycle_auto_off', 'Automatic return off: your cycle stays where you leave it.', 'Automatische Rückkehr aus: dein Rad bleibt, wo du es lässt.'),
        ('message.redstoneplus.cycle_not_yours', 'This light cycle is bound to another rider.', 'Dieses Lichtrad gehört einem anderen Fahrer.'),
        ('message.redstoneplus.cycle_busy', 'Someone is riding your light cycle right now.', 'Gerade fährt jemand anderes dein Lichtrad.'),
        ('hud.redstoneplus.cycle.speed', '%s blocks/s', '%s Blöcke/s'),
        ('hud.redstoneplus.cycle.locked', 'LIGHTLINE LOCK', 'AUF DER LICHTBAHN'),
        ('hud.redstoneplus.cycle.free', 'free ride', 'freie Fahrt'),
        ('hud.redstoneplus.cycle.junction', 'Junction in %s: %s', 'Kreuzung in %s: %s'),
        ('hud.redstoneplus.cycle.end', 'Line ends in %s', 'Bahn endet in %s'),
        ('hud.redstoneplus.cycle.turn_left', '◀ turn queued', '◀ Abbiegen vorgemerkt'),
        ('hud.redstoneplus.cycle.turn_right', 'turn queued ▶', 'Abbiegen vorgemerkt ▶'),
        ('hud.redstoneplus.cycle.bell', 'THE GREAT BELL TOLLS: HOLDING', 'DIE GROSSE GLOCKE SCHLÄGT: HALTEN'),
        ('hud.redstoneplus.cycle.keys', '[%s] Grid Map', '[%s] Rasterkarte'),
        ('key.redstoneplus.grid_map', 'Grid Map', 'Rasterkarte'),
        ('key.categories.redstoneplus', 'RedstonePlus', 'RedstonePlus'),
        ('screen.redstoneplus.grid_map', 'THE GRID', 'DAS RASTER'),
        ('screen.redstoneplus.grid_map.legend', 'red: lightlines   diamonds: nodes   amber: Trackwrights   arrows: light cycles   scroll: zoom, drag: look, right click: back to you',
         'rot: Lichtbahnen   Rauten: Knoten   bernstein: Bahnwerker   Pfeile: Lichträder   Mausrad: Zoom, Ziehen: umsehen, Rechtsklick: zurück zu dir'),
        ('screen.redstoneplus.grid_map.tile', 'lightline', 'Lichtbahn'),
        ('screen.redstoneplus.grid_map.empty', 'No loaded world to show here.', 'Hier ist keine geladene Welt zu zeigen.')):
    en[key] = e
    de[key] = g
QUARANTINE_SIGNS = [
    (('!! QUARANTINE !!', 'The Concordance', 'does not hold', 'beyond this wall'), ('!! QUARANTÄNE !!', 'Die Eintracht', 'gilt nicht', 'hinter der Mauer')),
    (('SEALED BY ORDER', 'OF THE COUNCIL', '- - -', 'TURN BACK'), ('VERSIEGELT AUF', 'BEFEHL DES RATES', '- - -', 'KEHR UM')),
    (('DO NOT OPEN', 'THE GATES', '', '(they listen)'), ('ÖFFNE NICHT', 'DIE TORE', '', '(sie lauschen)')),
    (('LINE CLOSED', 'no cycle', 'came back', 'from the Reach'), ('BAHN GESPERRT', 'kein Rad', 'kam je zurück', 'aus der Weite')),
]
for i, (lines_e, lines_g) in enumerate(QUARANTINE_SIGNS):
    for k in range(4):
        en[f'quarantine.{NS}.sign.{i}.{k}'] = lines_e[k]
        de[f'quarantine.{NS}.sign.{i}.{k}'] = lines_g[k]

# ================================================================================================ the Great Bell (sky)
def great_bell():
    """The Concordance as it hangs in the sky: a cracked bronze bell, its crack and sigils still glowing."""
    import math as _m
    size = 128
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    cx = size / 2

    def half_width(y):
        if y < 18:
            return 0
        if y < 30:  # crown dome
            return 18 * _m.sqrt(max(0.0, 1 - ((30 - y) / 12.0) ** 2))
        if y < 92:  # waist widening to the shoulder of the lip
            return 18 + (y - 30) * 0.42
        if y < 104:  # flared lip
            return 44 + (y - 92) * 0.9
        return 0

    for y in range(size):
        w = half_width(y)
        for x in range(size):
            dx = x + 0.5 - cx
            if w and abs(dx) <= w:
                u = dx / w
                shade = 0.45 + 0.55 * _m.cos(u * _m.pi / 2) ** 0.7 + (0.25 if -0.55 < u < -0.3 else 0)
                r, g, b = 186 * shade, 128 * shade, 62 * shade
                if y in (40, 41, 86, 87, 100):  # cast rings
                    r, g, b = r * 0.6, g * 0.6, b * 0.6
                if 74 <= y <= 80 and int((u + 1) * 14) % 3 == 0:  # glowing sigil band
                    r, g, b = 255, 80, 50
                px[x, y] = (int(min(255, r)), int(min(255, g)), int(min(255, b)), 255)
    # crown loop
    for a in range(0, 360, 2):
        for rr in (8, 9, 10):
            x = int(cx + rr * _m.cos(_m.radians(a)))
            y = int(14 + rr * 0.8 * _m.sin(_m.radians(a)))
            if y < 20 and 0 <= x < size:
                px[x, y] = (120, 80, 40, 255)
    # the crack that freed it, still burning
    _crack = random.Random(17)
    x = cx + 6
    for y in range(26, 100):
        x += _crack.choice((-2, -1, -1, 0, 1, 1, 2)) * 0.6 + (0.25 if y > 70 else -0.15)
        for d in (0, 1):
            xi = int(x) + d
            if px[xi, y][3]:
                px[xi, y] = (255, 70 + 40 * d, 40, 255)
    # clapper hanging below the lip
    for y in range(104, 116):
        for x in range(size):
            if (x + 0.5 - cx) ** 2 + ((y - 110) * 1.2) ** 2 < 30:
                px[x, y] = (90, 60, 34, 255)
    return img


def halo():
    import math as _m
    size = 64
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            d = _m.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2)
            a = max(0.0, 1 - d) ** 2.2
            px[x, y] = (255, 255, 255, int(255 * a))
    return img


for tex, img in (('great_bell', great_bell()), ('great_bell_halo', halo())):
    path = os.path.join(ASSETS, 'textures', 'environment', tex + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

# ================================================================================================ the liquids of the realm
# Drawn from scratch: animated still and flowing textures (frames loop seamlessly in space and time), and buckets.
import math as _m

LIQUIDS = {
    # id: english, german, description e/g, (dark, mid, bright) colours, alpha, look
    'molten_redstone': ('Molten Redstone', 'Geschmolzenes Redstone',
                        'Liquid power. It glows, burns and powers redstone it touches: the closer to its source, the stronger the signal. Meets water: Redstone Vein. Meets lava: realm redstone ore.',
                        'Flüssige Energie. Es leuchtet, brennt und versorgt Redstone, das es berührt: je näher an der Quelle, desto stärker das Signal. Trifft Wasser: Redstone-Ader. Trifft Lava: Reichs-Redstone-Erz.',
                        ('#7a0804', '#e8200c', '#ffb050'), 255, 'veins'),
    'ember_oil': ('Ember Oil', 'Glutöl',
                  'Thick, warm and flammable: fire spreads over it and it bursts into flame near lava or fire. Wading in it is slow, but it coats you: fire cannot touch you for a while. Meets water: slag.',
                  'Dick, warm und brennbar: Feuer breitet sich darauf aus, und neben Lava oder Feuer geht es in Flammen auf. Darin kommt man nur langsam voran, aber es überzieht dich: eine Weile kann Feuer dir nichts anhaben. Trifft Wasser: Schlacke.',
                  ('#3a1204', '#b04a10', '#ffb040'), 255, 'oil'),
    'rust_brine': ('Rust Brine', 'Rostlake',
                   'The realm\'s salt sea water, red with rust. Tools and armour corrode in it, and it weakens you; it slowly rusts the iron around it. It refills like water. Meets lava: hematite.',
                   'Das salzige Meerwasser des Reichs, rot vor Rost. Werkzeug und Rüstung korrodieren darin, und sie schwächt dich; Eisen daneben rostet langsam. Es füllt sich nach wie Wasser. Trifft Lava: Hämatit.',
                   ('#3a0c06', '#8a2a18', '#d8704a'), 190, 'ripple'),
    'resonant_ichor': ('Resonant Ichor', 'Resonanz-Ichor',
                       'Liquid sound from the deep crystal caves. Light and humming: you fall softly into it, jump high out of it, and its chime sets off the sculk sensors near it. Meets lava: resonant crystal.',
                       'Flüssiger Klang aus den tiefen Kristallhöhlen. Leicht und summend: man fällt weich hinein, springt hoch heraus, und sein Klingen weckt die Sculk-Sensoren in der Nähe. Trifft Lava: Resonanzkristall.',
                       ('#6a3008', '#ffa830', '#fff0b0'), 170, 'rings'),
    'blight_sap': ('Blight Sap', 'Fäulnissaft',
                   'The black ichor of the Sealed Reach. It saps your strength and withers; ground it spills over turns to blight crust. When the realm is freed it turns to Dawn Nectar.',
                   'Der schwarze Saft der Versiegelten Weite. Er raubt Kraft und lässt verdorren; Boden, über den er fließt, wird zu Fäulniskruste. Ist das Reich befreit, wird er zu Morgennektar.',
                   ('#140202', '#4a0a08', '#c8301a'), 255, 'bubbles'),
    'dawn_nectar': ('Dawn Nectar', 'Morgennektar',
                    'What the realm bleeds once it is freed: warm and golden. It heals and feeds you, puts out fire, and makes the plants around it grow. Meets blight sap: heather turf.',
                    'Was das Reich blutet, wenn es befreit ist: warm und golden. Er heilt und nährt dich, löscht Feuer und lässt Pflanzen um sich wachsen. Trifft Fäulnissaft: Heideboden.',
                    ('#8a4a08', '#ffc040', '#fff4c8'), 200, 'sparkle'),
}


def _hexc(h):
    return tuple(int(h.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4))


def _lerp(a, b, f):
    return tuple(int(round(a[i] + (b[i] - a[i]) * f)) for i in range(3))


def _ramp(cols, v):
    """v in 0..1 over dark -> mid -> bright."""
    dark, mid, bright = cols
    v = max(0.0, min(1.0, v))
    return _lerp(dark, mid, v / 0.6) if v < 0.6 else _lerp(mid, bright, (v - 0.6) / 0.4)


def _field(look, x, y, size, k, frames, seed):
    """A value 0..1 that tiles over size x size and loops over the frames."""
    r = random.Random(seed)
    T = 2 * _m.pi * k / frames
    u, w = 2 * _m.pi * x / size, 2 * _m.pi * y / size
    waves = [(r.randint(1, 3), r.randint(-3, 3), r.uniform(0, 6.3), r.choice((-1, 1)) * r.randint(1, 2)) for _ in range(5)]
    base = sum(_m.sin(a * u + b * w + ph + sp * T) for a, b, ph, sp in waves) / 5.0
    v = 0.5 + 0.45 * base
    if look == 'veins':
        # thin bright veins over a deep glow
        vein = 1.0 - abs(_m.sin(u * 2 + _m.sin(w * 1 + T) * 1.5 + T))
        v = 0.45 + 0.25 * base + (0.45 if vein > 0.93 else 0.0)
    elif look == 'oil':
        # slow sheen bands
        v = 0.35 + 0.3 * base + 0.35 * max(0.0, _m.sin(u + w * 2 + T)) ** 6
    elif look == 'ripple':
        v = 0.4 + 0.3 * base + 0.25 * max(0.0, _m.sin(w * 3 + T * 2 + _m.sin(u) * 0.8)) ** 4
    elif look == 'rings':
        d = _m.hypot(_m.sin(u / 2) * 1.0, _m.sin(w / 2) * 1.0)
        v = 0.45 + 0.2 * base + 0.35 * max(0.0, _m.cos(d * 9 - T * 2)) ** 3
    elif look == 'bubbles':
        v = 0.15 + 0.25 * base
        for i in range(3):
            bx, by = r.uniform(0, size), r.uniform(0, size)
            ph = (k / frames + i / 3.0) % 1.0
            rad = 0.6 + ph * 2.4
            dx = min(abs(x - bx), size - abs(x - bx))
            dy = min(abs(y - by), size - abs(y - by))
            if abs(_m.hypot(dx, dy) - rad) < 0.7 and ph < 0.85:
                v = 0.95
    elif look == 'sparkle':
        v = 0.45 + 0.3 * base
        if int(x * 7 + y * 13 + k * 5) % 61 == 0:
            v = 1.0
    return v


def liquid_frames(lid, cols, alpha, look, size, frames, flow):
    img = Image.new('RGBA', (size, size * frames))
    px = img.load()
    for k in range(frames):
        for y in range(size):
            for x in range(size):
                # a flowing texture scrolls down by one size per loop
                yy = (y - (size * k / frames if flow else 0)) % size
                v = _field(look, x, yy, size, k, frames, sum(map(ord, lid)) * 7919)
                if flow:
                    v = v * 0.85 + 0.15 * (0.5 + 0.5 * _m.sin(2 * _m.pi * (x / size) * 4))
                c = _ramp(cols, v)
                px[x, y + k * size] = c + (alpha,)
    return img


for lid, (e, g, de_, dg, cols_hex, alpha, look) in LIQUIDS.items():
    cols = tuple(_hexc(h) for h in cols_hex)
    for part, size, frames, frametime in (('still', 16, 32, 2), ('flow', 32, 16, 1)):
        save(liquid_frames(lid, cols, alpha, look, size, frames, part == 'flow'), 'block', f'{lid}_{part}')
        with open(os.path.join(ASSETS, 'textures', 'block', f'{lid}_{part}.png.mcmeta'), 'w', encoding='utf-8') as f:
            json.dump({'animation': {'frametime': frametime}}, f)
            f.write('\n')
    model(f'block/{lid}', {'textures': {'particle': f'{NS}:block/{lid}_still'}})
    blockstate(lid, {'variants': {'': {'model': f'{NS}:block/{lid}'}}})
    name(f'block.{NS}.{lid}', e, g, de_, dg)
    name(f'fluid_type.{NS}.{lid}', e, g)
    # the bucket: an iron pail full to the brim
    pal = {'a': '#2a2a2e', 'b': '#6a6a72', 'c': '#a8a8b0', 'd': '#d8d8e0', 'l': cols_hex[1], 'm': cols_hex[2], 'n': cols_hex[0]}
    save(sprite([
        '................', '.....aaaaaa.....', '....a......a....', '...a........a...', '...a........a...', '..abbbbbbbbbba..',
        '..anmmlllllllna.', '..bnlllllmllncb.', '..bcnlllllllncb.', '..bccnnnnnnnccb.', '...bcccdccccdb..', '...bccccdcccdb..',
        '...bbcccdccccb..', '....bbcccdccb...', '.....bbbbbbbb...', '................'], pal), 'item', f'{lid}_bucket')
    item_model(f'{lid}_bucket')
    name(f'item.{NS}.{lid}_bucket', f'{e} Bucket', f'Eimer mit {g}', f'A bucket of {e}. ' + de_.split('.')[0] + '.',
         f'Ein Eimer {g}. ' + dg.split('.')[0] + '.')

# ================================================================================================ the story: Echoes, seals, chains
STORY_RND = random.Random(20261005)
ECHOES = {
    # id: (title e, title g, power e, power g, colour, glyph)
    'force': ('The Pistonarch', 'Der Kolbenfürst', 'the Echo of Force', 'das Echo der Kraft', '#ff3a1a', 'force'),
    'signal': ('The Current', 'Der Strom', 'the Echo of Signal', 'das Echo des Signals', '#ff1a10', 'signal'),
    'resonance': ('The Choir', 'Der Chor', 'the Echo of Resonance', 'das Echo der Resonanz', '#ffb040', 'resonance'),
    'heat': ('The Kilnheart', 'Das Ofenherz', 'the Echo of Heat', 'das Echo der Hitze', '#ff7a10', 'heat'),
    'flow': ('The Sluicemother', 'Die Schleusenmutter', 'the Echo of Flow', 'das Echo des Flusses', '#ff4a20', 'flow'),
}
GLYPHS = {
    'force': ['......', '..##..', '.####.', '..##..', '..##..', '.####.'],
    'signal': ['...#..', '..#...', '.####.', '...#..', '..#...', '.#....'],
    'resonance': ['.####.', '#....#', '#.##.#', '#.##.#', '#....#', '.####.'],
    'heat': ['..#...', '..##..', '.###..', '.####.', '######', '.####.'],
    'flow': ['......', '.#..#.', '#.##.#', '......', '.#..#.', '#.##.#'],
}


def seal_top(echo):
    """The face of an Echo Seal: dark stone, a glowing ring, and the sigil of its Echo."""
    col = _hexc(ECHOES[echo][4])
    img = noisy(blank('#1a0808'), 0.2, STORY_RND)
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = _m.hypot(x - 7.5, y - 7.5)
            if 6.2 < d < 7.4:
                px[x, y] = col + (255,)
            elif 5.4 < d <= 6.2:
                px[x, y] = _lerp(col, (40, 10, 6), 0.6) + (255,)
    for gy, row in enumerate(GLYPHS[ECHOES[echo][5]]):
        for gx, ch in enumerate(row):
            if ch == '#':
                px[5 + gx, 5 + gy] = (255, 236, 190, 255)
    return img


def seal_side():
    img = noisy(blank('#2a0c08'), 0.15, STORY_RND)
    px = img.load()
    for x in range(16):
        px[x, 0] = px[x, 15] = (110, 70, 34, 255)
        px[x, 7] = px[x, 8] = (255, 70, 30, 255) if x % 3 else (255, 180, 90, 255)
    return img


for i, echo in enumerate(ECHOES):
    save(seal_top(echo), 'block', f'echo_seal_{echo}')
    model(f'block/echo_seal_{echo}', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': f'{NS}:block/echo_seal_{echo}', 'side': f'{NS}:block/echo_seal_side', 'bottom': f'{NS}:block/echo_seal_side'}})
save(seal_side(), 'block', 'echo_seal_side')
blockstate('echo_seal', {'variants': {f'echo={i}': {'model': f'{NS}:block/echo_seal_{echo}'} for i, echo in enumerate(ECHOES)}})
item_model('echo_seal', f'{NS}:block/echo_seal_force')
name(f'block.{NS}.echo_seal', 'Echo Seal', 'Echo-Siegel',
     'The seal at the heart of a sanctum. Use it to wake the Echo that keeps the sanctum. Once its Echo has fallen it stays silent. Unbreakable.',
     'Das Siegel im Herzen eines Heiligtums. Benutze es, um das Echo zu wecken, das das Heiligtum hütet. Ist das Echo gefallen, bleibt es still. Unzerstörbar.')


def chain_link_texture():
    """Black iron of a giant chain link, worn bright at the edges, with rivets that are still red hot."""
    img = noisy(blank('#2a1c18'), 0.18, STORY_RND)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = (120, 84, 60, 255)
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = (70, 48, 38, 255)
            elif 5 <= x <= 10 and 5 <= y <= 10:
                px[x, y] = (40, 26, 22, 255)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[x, y] = (255, 90, 30, 255)
        px[x + 1, y] = px[x, y + 1] = (180, 40, 16, 255)
    return img


save(chain_link_texture(), 'block', 'chain_link')
simple_block('chain_link')
name(f'block.{NS}.chain_link', 'Giant Chain Link', 'Riesenkettenglied',
     'A link of the giant chains that bind the realm to the Great Bell. Nothing breaks it: only the fall of the Echo that holds its chain brings it down. When the realm is freed, every chain falls.',
     'Ein Glied der Riesenketten, die das Reich an die Große Glocke binden. Nichts zerbricht es: nur der Fall des Echos, das seine Kette hält, bringt es herab. Ist das Reich befreit, fallen alle Ketten.')

def artery_wall_texture():
    """The wall of the vessel: dark crimson tissue, branching vessels in it, a few of them still glowing."""
    img = noisy(blank('#5a0e12'), 0.16, STORY_RND)
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 0.5 + 0.5 * _m.sin(x * 0.9 + _m.sin(y * 0.7) * 2.0)
            if v > 0.92:
                px[x, y] = (128, 22, 28, 255)
    for _ in range(3):
        x, y = STORY_RND.randrange(16), STORY_RND.randrange(16)
        glow = STORY_RND.random() < 0.6
        for k in range(12):
            px[x % 16, y % 16] = (255, 64, 40, 255) if glow and k % 4 else (150, 26, 30, 255)
            x += STORY_RND.choice((-1, 0, 1))
            y += STORY_RND.choice((0, 1, 1))
    return img


save(artery_wall_texture(), 'block', 'artery_wall')
simple_block('artery_wall')
self_drop('artery_wall')
pickaxe.append(f'{NS}:artery_wall')
name(f'block.{NS}.artery_wall', 'Artery Wall', 'Aderwand',
     'The living wall of the vessel the realm is. It lines the rims, the underside and the veins that hang down into the Blood Below.',
     'Die lebende Wand des Gefäßes, das das Reich ist. Sie säumt die Ränder, die Unterseite und die Adern, die in das Blut darunter hinabhängen.')

# the dawn lily: what grows on the freed realm
save(sprite([
    '................', '.....a....a.....', '....aba..aba....', '....abba.abb....', '.....abbabba....', '......abcba.....',
    '....aabcdcbaa...', '...abbcdddcbba..', '....aabcdcbaa...', '......abcba.....', '.......eae......', '.......e.......',
    '......fe........', '.....f.e........', '.......e........', '.......e........'],
    {'a': '#c83a14', 'b': '#ff7a2a', 'c': '#ffb84a', 'd': '#fff0a0', 'e': '#8a2a14', 'f': '#b84a1a'}), 'block', 'dawn_lily')
model('block/dawn_lily', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{NS}:block/dawn_lily'}})
blockstate('dawn_lily', {'variants': {'': {'model': f'{NS}:block/dawn_lily'}}})
item_model('dawn_lily', texture=f'{NS}:block/dawn_lily')
name(f'block.{NS}.dawn_lily', 'Dawn Lily', 'Morgenlilie',
     'A warm, glowing lily. It only grows once the realm is freed, wherever the land heals.',
     'Eine warme, leuchtende Lilie. Sie wächst erst, wenn das Reich befreit ist, überall dort, wo das Land heilt.')
self_drop('dawn_lily')


def core_sprite(echo):
    """An Echo's core: its light caught in a cage of bell bronze."""
    col = ECHOES[echo][4]
    bright = '#%02x%02x%02x' % _lerp(_hexc(col), (255, 240, 200), 0.6)
    return sprite([
        '................', '......aaaa......', '....aabbbbaa....', '...abbccccbba...', '...abccddccba...', '..abccdeedccba..',
        '..abcdeffedcba..', '..abcdeffedcba..', '..abccdeedccba..', '...abccddccba...', '...abbccccbba...', '....aabbbbaa....',
        '......aaaa......', '.......gg.......', '......g..g......', '................'],
        {'a': '#6a4a22', 'b': '#3a0a06', 'c': '#%02x%02x%02x' % _lerp(_hexc(col), (40, 8, 4), 0.45), 'd': col, 'e': bright,
         'f': '#fff8e0', 'g': '#b88a3a'})


for echo, (title_e, title_g, power_e, power_g, col, glyph) in ECHOES.items():
    save(core_sprite(echo), 'item', f'core_{echo}')
    item_model(f'core_{echo}')
    name(f'item.{NS}.core_{echo}', f'Core of {power_e.split(" of ")[1]}', f'Kern {power_g.split(" ", 1)[1].replace("Echo ", "")}',
         f'What is left when {title_e[0].lower() + title_e[1:]}, {power_e}, falls. Its chain is broken. Five cores make the Heart of the Five.',
         f'Was bleibt, wenn {title_g[0].lower() + title_g[1:]} fällt, {power_g}. Seine Kette ist zerbrochen. Fünf Kerne ergeben das Herz der Fünf.')
    name(f'story.{NS}.echo.{echo}', f'{title_e}, {power_e}', f'{title_g}, {power_g}')
    name(f'entity.{NS}.echo_{echo}', title_e, title_g)
save(sprite([
    '................', '.......a........', '......aba.......', '.....abcba......', '....abcdcba.....', '...abcdedcba....',
    '..abcdefedcba...', '..abcdefedcba...', '...abcdedcba....', '....abcdcba.....', '.....abcba......', '......aba.......',
    '.......a........', '................', '................', '................'],
    {'a': '#6a4a22', 'b': '#ff3a1a', 'c': '#ff7a10', 'd': '#ffb040', 'e': '#ffe08a', 'f': '#fffbe8'}), 'item', 'heart_of_the_five')
item_model('heart_of_the_five')
name(f'item.{NS}.heart_of_the_five', 'Heart of the Five', 'Herz der Fünf',
     'The five cores of the Echoes, forged into one. Use it on the bronze socket of the Great Cradle, in the Heart of the artery, to call the Great Bell down to fight.',
     'Die fünf Kerne der Echos, zu einem geschmiedet. Benutze es auf dem Bronzesockel der Großen Wiege im Herzen der Ader, um die Große Glocke zum Kampf herabzurufen.')
save(sprite([
    '................', '...a......a.....', '...ab.....ab....', '...ab.....ab....', '...ab.....ab....', '...ab.....ab....',
    '...ab.....ab....', '...abb...bab....', '....abbbbba.....', '.....aaaaa......', '.......ab.......', '.......ab.......',
    '.......ab.......', '.......cc.......', '.......cc.......', '................'],
    {'a': '#8a5a22', 'b': '#e0a850', 'c': '#ff3a1a'}), 'item', 'tuning_fork')
item_model('tuning_fork')
name(f'item.{NS}.tuning_fork', 'Tuning Fork', 'Stimmgabel',
     'Strike it (use) and it hums towards the nearest arena whose Echo still stands, and how far it is; once all five have fallen, towards the Great Cradle.',
     'Anschlagen (benutzen), und sie summt in Richtung der nächsten Arena, deren Echo noch steht, und sagt wie weit; sind alle fünf gefallen, zur Großen Wiege.')
shaped('tuning_fork', ['B B', 'BRB', ' C '], {'B': f'{NS}:bell_bronze', 'R': 'minecraft:redstone', 'C': f'{NS}:realm_cog'}, f'{NS}:tuning_fork')
shapeless('heart_of_the_five', [f'{NS}:core_{e}' for e in ECHOES] + [f'{NS}:bell_bronze'],
          f'{NS}:heart_of_the_five')
name(f'entity.{NS}.the_overtoll', 'The Overtoll', 'Der Übergeläut')

# boss spawn eggs and loot (the core itself is dropped by the boss)
BOSS_LOOT = {
    'echo_force': [('minecraft:redstone_block', 4, 8, 1), (f'{NS}:karst_bricks', 8, 16, 1), ('minecraft:piston', 2, 4, 1)],
    'echo_signal': [('minecraft:redstone_block', 4, 8, 1), ('minecraft:repeater', 3, 6, 1), ('minecraft:comparator', 2, 4, 1)],
    'echo_resonance': [('minecraft:redstone_block', 4, 8, 1), (f'{NS}:resonant_crystal', 6, 12, 1), ('minecraft:bell', 1, 1, 1)],
    'echo_heat': [('minecraft:redstone_block', 4, 8, 1), ('minecraft:blaze_rod', 3, 6, 1), ('minecraft:magma_block', 6, 12, 1)],
    'echo_flow': [('minecraft:redstone_block', 4, 8, 1), (f'{NS}:molten_redstone_bucket', 1, 1, 1), ('minecraft:copper_block', 3, 6, 1)],
    'the_overtoll': [(f'{NS}:bell_bronze', 16, 32, 1), ('minecraft:redstone_block', 16, 32, 1), ('minecraft:bell', 1, 1, 1),
                     ('minecraft:nether_star', 1, 1, 1)],
}
for mob, drops in BOSS_LOOT.items():
    e = en[f'entity.{NS}.{mob}']
    g = de[f'entity.{NS}.{mob}']
    name(f'item.{NS}.{mob}_spawn_egg', f'{e} Spawn Egg', f'{g}-Spawn-Ei')
    item_model(f'{mob}_spawn_egg', 'minecraft:item/template_spawn_egg')
    pools = []
    for item, lo, hi, chance in drops:
        pools.append({'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': item, 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}, 'add': False}]}]})
    write(os.path.join(DATA, 'loot_table', 'entities', mob + '.json'), {'type': 'minecraft:entity', 'pools': pools,
                                                                       'random_sequence': f'{NS}:entities/{mob}'})


# ================================================================================================ the sky after the Bell: chains and the red sun
def sky_chain():
    """A strip of giant chain as seen from far below: links face-on and edge-on in turn, dark iron lit red from beneath."""
    w, h = 16, 64
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        n, k = divmod(y, 16)
        for x in range(w):
            dx = x - 7.5
            if n % 2 == 0:  # face-on: an oval ring
                d = _m.hypot(dx / 7.0, (k - 7.5) / 7.8)
                if 0.45 < d < 1.0:
                    lit = 0.5 + 0.5 * (-dx / 6.5)
                    px[x, y] = _lerp((40, 20, 16), (200, 90, 50), max(0.0, lit)) + (255,)
            else:  # edge-on: a bar
                if abs(dx) < 2.6:
                    px[x, y] = _lerp((60, 30, 20), (230, 110, 60), 1 - abs(dx) / 2.6) + (255,)
    return img


def red_sun():
    """The new sun: a deep red disc with a hot gold heart and bright cells, a soft corona, and around it a thin ring of bronze
    light where the Bell's lip once was."""
    size = 128
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    c = size / 2
    r0 = 34.0
    rr = random.Random(9)
    cells = [(rr.uniform(-r0, r0), rr.uniform(-r0, r0), rr.uniform(3, 7)) for _ in range(26)]
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = _m.hypot(dx, dy)
            if d < r0:
                mu = _m.sqrt(1 - (d / r0) ** 2)
                v = 0.55 + 0.45 * mu
                for cx, cy, cr in cells:
                    if _m.hypot(dx - cx, dy - cy) < cr:
                        v += 0.08
                f = min(1.0, v * mu ** 0.5)
                col = _lerp((150, 12, 6), (255, 60, 24), f / 0.7) if f < 0.7 else _lerp((255, 60, 24), (255, 190, 110), (f - 0.7) / 0.3)
                px[x, y] = col + (255,)
            else:
                a = max(0.0, 1 - (d - r0) / (c - r0)) ** 2.4
                col = (255, 50, 20)
                if abs(d - (r0 + 9)) < 1.2:  # the ring of bronze light
                    a = max(a, 0.75 * (1 - abs(d - (r0 + 9)) / 1.2))
                    col = (255, 170, 80)
                px[x, y] = col + (int(255 * min(1.0, a * 0.95)),)
    return img


for tex, img in (('sky_chain', sky_chain()), ('red_sun', red_sun())):
    path = os.path.join(ASSETS, 'textures', 'environment', tex + '.png')
    img.save(path)

# ================================================================================================ the story's texts
for key, e, g in (
        ('awakens', '%s awakens!', '%s erwacht!'),
        ('seat_silent', 'The seat of %s is silent: it has fallen already. Its chain comes down here too.',
         'Der Sitz von %s ist still: es ist schon gefallen. Auch hier fällt seine Kette.'),
        ('chain_broken.title', 'A CHAIN BREAKS', 'EINE KETTE BRICHT'),
        ('chains_left', 'The Great Bell is held by %s chain(s) more. Somewhere, its other Echoes keep them.',
         'Die Große Glocke hängt noch an %s Kette(n). Irgendwo hüten ihre anderen Echos sie.'),
        ('all_broken', 'All five chains are broken. Forge the five cores into the Heart of the Five and lay it on the socket of the Great Cradle, in the Heart of the artery: the Bell will come down.',
         'Alle fünf Ketten sind gebrochen. Schmiede die fünf Kerne zum Herz der Fünf und lege es auf den Sockel der Großen Wiege im Herzen der Ader: die Glocke wird herabkommen.'),
        ('overtoll_warning', 'THE OVERTOLL GATHERS ITS TOLL · BE STILL', 'DER ÜBERGELÄUT HOLT ZUM SCHLAG AUS · HALTE STILL'),
        ('overtoll_descends', 'The Great Bell tears loose from the sky and comes down: the Overtoll!',
         'Die Große Glocke reißt sich vom Himmel los und stürzt herab: der Übergeläut!'),
        ('heart_where', 'The Heart answers only on the bronze socket of the Great Cradle, in the Heart of the artery.',
         'Das Herz antwortet nur auf dem Bronzesockel der Großen Wiege im Herzen der Ader.'),
        ('heart_done', 'The realm is free already. The Heart only glows warmly.', 'Das Reich ist schon frei. Das Herz glüht nur warm.'),
        ('freed.title', 'THE REALM IS FREE', 'DAS REICH IST FREI'),
        ('freed.subtitle', 'A red sun rises where the Bell hung', 'Eine rote Sonne geht auf, wo die Glocke hing'),
        ('freed.message', 'The Overtoll has fallen. The chains fall, the land heals around you, and the creatures of the realm bloom and leave you in peace.',
         'Der Übergeläut ist gefallen. Die Ketten fallen, das Land heilt um dich herum, und die Kreaturen des Reichs blühen auf und lassen dich in Frieden.'),
        ('fork_points', '%s: %s %s blocks', '%s: %s %s Blöcke'),
        ('fork_cradle', 'The Great Cradle', 'Die Große Wiege'),
        ('fork_silent', 'The fork is silent outside the realm.', 'Außerhalb des Reichs schweigt die Gabel.'),
        ('fork_healed', 'The fork rings clear: there is nothing left to find.', 'Die Gabel klingt rein: es gibt nichts mehr zu finden.'),
        ('fork_nothing', 'The fork hums, but finds nothing near.', 'Die Gabel summt, findet aber nichts in der Nähe.'),
        ('bar.healed', 'The realm is free · the Concordance rests', 'Das Reich ist frei · die Eintracht ruht')):
    name(f'story.{NS}.{key}', e, g)

# ================================================================================================ the Concordance (rules)
for key, e, g in (
        ('strike_first', 'You struck first. The Concordance remembers.', 'Du hast zuerst zugeschlagen. Die Eintracht vergisst das nicht.'),
        ('break_built', 'You broke what the Wirewrights built.', 'Du hast zerstört, was die Drahtwerker gebaut haben.'),
        ('toll_still', 'You moved while the Great Bell tolled.', 'Du hast dich bewegt, als die Große Glocke schlug.'),
        ('bar.harmony', 'The Concordance · in harmony', 'Die Eintracht · im Einklang'),
        ('bar.broken', 'The Concordance · rules broken: %s/%s', 'Die Eintracht · gebrochene Regeln: %s/%s'),
        ('bar.condemned', 'The Concordance · %s/%s · the realm hunts you', 'Die Eintracht · %s/%s · das Reich jagt dich'),
        ('bar.toll_soon', 'The Great Bell tolls in %s · be still', 'Die Große Glocke schlägt in %s · halte still'),
        ('bar.tolling', 'The Great Bell tolls · be still', 'Die Große Glocke schlägt · halte still'),
        ('condemned', 'Three rules broken. Every creature of the realm now hunts you. Atone to be left in peace again.',
         'Drei Regeln gebrochen. Jede Kreatur des Reichs jagt dich jetzt. Sühne, um wieder in Ruhe gelassen zu werden.'),
        ('forgiven', 'The Concordance forgives: %s/%s rules broken.', 'Die Eintracht vergibt: %s/%s Regeln gebrochen.'),
        ('forgiven_all', 'The Concordance forgives everything. You are in harmony again.', 'Die Eintracht vergibt alles. Du bist wieder im Einklang.'),
        ('lamp_known', 'You relit this lamp before. The Concordance has already counted it.', 'Diese Lampe hast du schon einmal entzündet. Die Eintracht hat sie schon gezählt.'),
        ('lamp_wait', 'The lamp burns, but the Concordance only counts one lamp a minute.', 'Die Lampe brennt, doch die Eintracht zählt nur eine Lampe pro Minute.'),
        ('welcome', 'The realm keeps the Concordance: do not strike first, do not break what the Wirewrights built, be still when the Great Bell tolls. The bar at the top keeps count.',
         'Das Reich hält die Eintracht: schlag nicht zuerst, zerstöre nicht, was die Drahtwerker gebaut haben, halte still, wenn die Große Glocke schlägt. Der Balken oben zählt mit.')):
    name(f'rules.{NS}.{key}', e, g)

# ================================================================================================ etched plates (lore)
LORE = [
    ('The Concordance', 'Die Eintracht',
     'We hung the Great Bell in its cradle at the heart of the Foundry City. Every clock in the realm kept time by its toll, and every machine moved as one.',
     'Wir hängten die Große Glocke in ihre Wiege im Herzen der Gießereistadt. Jede Uhr des Reichs ging nach ihrem Schlag, und jede Maschine bewegte sich im Gleichtakt.'),
    ('The Grid', 'Das Netz',
     'Power ran beneath every road and every wall. Where the veins glow in the ground, the old grid is still bleeding.',
     'Strom floss unter jeder Straße und jeder Mauer. Wo die Adern im Boden glühen, blutet das alte Netz noch immer.'),
    ('The Walkers', 'Die Wanderer',
     'We built the constructs to tend the machines while we slept. Nobody ever told them to stop.',
     'Wir bauten die Konstrukte, damit sie die Maschinen pflegen, während wir schliefen. Niemand hat ihnen je gesagt, dass sie aufhören sollen.'),
    ('The Overtoll', 'Der Überschlag',
     'The Council ordered one great toll to wake every engine at once. The cradle cracked on the first stroke. On the second the cities answered.',
     'Der Rat befahl einen einzigen großen Schlag, um alle Maschinen zugleich zu wecken. Beim ersten Schlag riss die Wiege. Beim zweiten antworteten die Städte.'),
    ('The Ascent', 'Der Aufstieg',
     'On the last stroke the Bell tore free and rose into the sky. It hangs there still in place of the sun, and every time it tolls the machines forget a little more.',
     'Beim letzten Schlag riss sich die Glocke los und stieg in den Himmel. Dort hängt sie noch heute statt der Sonne, und mit jedem Schlag vergessen die Maschinen ein wenig mehr.'),
    ('The Fused', 'Die Verschmolzenen',
     'Those who stayed too close to the leaking engines became part of them. We call them Machine-Bound now. Do not call them by their old names.',
     'Wer den leckenden Maschinen zu nahe blieb, wurde ein Teil von ihnen. Wir nennen sie jetzt Maschinengebundene. Nenn sie nicht bei ihren alten Namen.'),
]


def plate_image(seed):
    from PIL import ImageDraw
    import random as _r
    rnd = _r.Random(seed)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 3, 13, 12), fill=(176, 122, 58, 255), outline=(92, 54, 24, 255))
    d.line((3, 4, 12, 4), fill=(226, 172, 98, 255))
    for row in range(5, 12, 2):
        x = 4
        while x < 12:
            w = rnd.randint(1, 3)
            d.line((x, row, min(11, x + w - 1), row), fill=(110, 60, 28, 255) if rnd.random() < 0.8 else (255, 70, 40, 255))
            x += w + 1
    return img


for i, (e, g, te, tg) in enumerate(LORE, 1):
    pid = f'etched_plate_{i}'
    save(plate_image(i * 31), 'item', pid)
    item_model(pid)
    name(f'item.{NS}.{pid}', f'Etched Plate: {e}', f'Gravierte Platte: {g}', '"' + te + '"', '„' + tg + '“')
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


def spire(name_, body, accent, h, r, count, rarity=None):
    placed(name_, {'type': f'{NS}:spire', 'config': {'body': state(body), 'accent': state(accent), 'min_height': h[0], 'max_height': h[1],
                                                      'min_radius': r[0], 'max_radius': r[1]}}, surface(count, rarity))


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
spire('frost_spire', 'frost_realmstone', 'salt_crust', (10, 26), (1, 3), {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 1}, rarity=3)
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
for shape in ('dunes', 'mesas', 'ponds', 'crevasses', 'lava_channels', 'terrace_pools', 'tracks', 'veins', 'roads', 'cities', 'grid', 'sealed_wall', 'sanctums',
              'red_sea', 'rim', 'abyss'):
    placed(shape, none_feature(shape), [])

# trap sites, scenery and machines: rare and spread out
placed('resonance_gatehouse', none_feature('resonance_gatehouse'), underground(1, -40, 30, rarity=5))
for kind, rarity in (('crusher_passage', 28), ('switchyard_junction', 26), ('sluice_bridge', 26), ('kiln_bridge', 26), ('briar_ambush', 26),
                     ('rail_line', 40), ('tower', 40), ('ruin', 56), ('monolith', 36), ('crashed_shell', 36), ('crystal_dome', 36),
                     ('boardwalk', 10), ('kiln_hut', 36), ('aqueduct', 40), ('ice_rails', 40), ('scrap', 36),
                     ('lamp_pylon', 70), ('crusher_mill', 48), ('pump_station', 44), ('minecart_loop', 48), ('storm_spire', 30),
                     ('bell_tower', 44), ('beast_cage', 56), ('laser_post', 40)):
    site(kind, rarity)
# the great buildings of the old world: centred on their chunk so they have room, and very rare
for kind, rarity in (('generator_hall', 260), ('relay_spire', 220), ('circuit_temple', 300)):
    placed(kind, none_feature(kind), [{'type': 'minecraft:rarity_filter', 'chance': rarity},
                                      {'type': 'minecraft:random_offset', 'xz_spread': 8, 'y_spread': 0},
                                      {'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'}, {'type': 'minecraft:biome'}])
# the chains that bind the trapped realm to the Bell: rare, but they rise to the top of the sky and are seen from far away
site('chain_anchor', 120)


# the liquids: lakes on the surface, pools deep down, and springs in the cliffs
def lake(name_, liquid, barrier, rarity, deep=False):
    cfg = {'type': 'minecraft:lake', 'config': {'fluid': {'type': 'minecraft:simple_state_provider', 'state': state(liquid)},
                                                'barrier': {'type': 'minecraft:simple_state_provider', 'state': state(barrier)}}}
    if deep:
        placement = [{'type': 'minecraft:rarity_filter', 'chance': rarity}, {'type': 'minecraft:in_square'},
                     {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -50},
                                                                   'max_inclusive': {'absolute': 30}}},
                     {'type': 'minecraft:environment_scan', 'direction_of_search': 'down', 'max_steps': 32,
                      'target_condition': {'type': 'minecraft:solid'},
                      'allowed_search_condition': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:air', 'minecraft:cave_air']}},
                     {'type': 'minecraft:biome'}]
    else:
        placement = [{'type': 'minecraft:rarity_filter', 'chance': rarity}, {'type': 'minecraft:in_square'},
                     {'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'}, {'type': 'minecraft:biome'}]
    placed(name_, cfg, placement)


SPRING_ROCK = [f'{NS}:{b}' for b in ('realmstone', 'deep_realmstone', 'hematite', 'dark_hematite', 'cinder_rock', 'karst_limestone',
                                     'salt_crust', 'fossil_circuit')]


def spring(name_, liquid, count):
    placed(name_, {'type': 'minecraft:spring_feature', 'config': {'state': {'Name': f'{NS}:{liquid}', 'Properties': {'falling': 'true'}},
                                                                   'requires_block_below': True, 'rock_count': 4, 'hole_count': 1,
                                                                   'valid_blocks': SPRING_ROCK}},
           [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -40},
                                                          'max_inclusive': {'absolute': 180}}}, {'type': 'minecraft:biome'}])


lake('molten_redstone_lake', 'molten_redstone', 'cinder_rock', 16)
lake('molten_redstone_pool', 'molten_redstone', 'fossil_circuit', 4, deep=True)
lake('ember_oil_lake', 'ember_oil', 'slag', 22)
lake('rust_brine_lake', 'rust_brine', 'salt_crust', 12)
lake('resonant_ichor_pool', 'resonant_ichor', 'resonant_crystal', 3, deep=True)
lake('blight_sap_lake', 'blight_sap', 'blight_crust', 5)
lake('dawn_nectar_lake', 'dawn_nectar', 'red_clay', 60)
spring('molten_redstone_spring', 'molten_redstone', 6)
spring('ember_oil_spring', 'ember_oil', 5)
spring('resonant_ichor_spring', 'resonant_ichor', 8)
placed('giant_tree', none_feature('giant_tree'), surface(count={'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 1}, rarity=2))

# one ordered list per generation step, so every biome uses the same order
STEPS = [
    [],
    ['minecraft:lake_lava_surface', 'redstoneplus:molten_redstone_lake', 'redstoneplus:molten_redstone_pool', 'redstoneplus:ember_oil_lake',
     'redstoneplus:rust_brine_lake', 'redstoneplus:resonant_ichor_pool', 'redstoneplus:blight_sap_lake', 'redstoneplus:dawn_nectar_lake'],
    ['redstoneplus:dunes', 'redstoneplus:mesas', 'redstoneplus:ponds', 'redstoneplus:crevasses', 'redstoneplus:lava_channels',
     'redstoneplus:terrace_pools', 'redstoneplus:tracks', 'redstoneplus:veins', 'redstoneplus:roads', 'redstoneplus:cities', 'redstoneplus:grid',
     'redstoneplus:sealed_wall', 'redstoneplus:sanctums', 'redstoneplus:karst_spire', 'redstoneplus:kiln_spire', 'redstoneplus:hoodoo', 'redstoneplus:tempest_pillar', 'redstoneplus:rubedo_spire',
     'redstoneplus:frost_spire', 'redstoneplus:dune_rock', 'redstoneplus:slag_heap', 'redstoneplus:sulfur_rock', 'redstoneplus:salt_mound',
     'redstoneplus:scree', 'redstoneplus:red_sea', 'redstoneplus:rim', 'redstoneplus:abyss'],
    ['redstoneplus:resonance_gatehouse'],
    ['redstoneplus:crusher_passage', 'redstoneplus:switchyard_junction', 'redstoneplus:sluice_bridge', 'redstoneplus:kiln_bridge',
     'redstoneplus:briar_ambush', 'redstoneplus:rail_line', 'redstoneplus:tower', 'redstoneplus:ruin', 'redstoneplus:monolith',
     'redstoneplus:crashed_shell', 'redstoneplus:crystal_dome', 'redstoneplus:boardwalk', 'redstoneplus:kiln_hut', 'redstoneplus:aqueduct',
     'redstoneplus:ice_rails', 'redstoneplus:scrap', 'redstoneplus:lamp_pylon', 'redstoneplus:crusher_mill', 'redstoneplus:pump_station',
     'redstoneplus:minecart_loop', 'redstoneplus:storm_spire', 'redstoneplus:bell_tower', 'redstoneplus:beast_cage', 'redstoneplus:laser_post',
     'redstoneplus:generator_hall', 'redstoneplus:relay_spire', 'redstoneplus:circuit_temple', 'redstoneplus:chain_anchor'],
    [],
    ['redstoneplus:ore_realm_redstone', 'redstoneplus:ore_realm_iron', 'redstoneplus:ore_realm_copper', 'redstoneplus:ore_redstone_vein',
     'redstoneplus:resonant_crystal_ore'],
    ['redstoneplus:cave_clusters_floor', 'redstoneplus:cave_clusters_ceiling'],
    ['redstoneplus:molten_redstone_spring', 'redstoneplus:ember_oil_spring', 'redstoneplus:resonant_ichor_spring'],
    ['redstoneplus:giant_tree', 'redstoneplus:crimson_heather_patch', 'redstoneplus:fen_reed_patch', 'redstoneplus:red_coral_shrub_patch',
     'redstoneplus:pale_stalk_patch', 'redstoneplus:salt_brush_patch', 'redstoneplus:copper_reed_patch', 'redstoneplus:lichen_tuft_patch',
     'redstoneplus:cinder_bloom_patch', 'redstoneplus:frost_fern_patch', 'redstoneplus:briar_patch'],
    [],
]
EVERYWHERE = {'redstoneplus:veins', 'redstoneplus:roads', 'redstoneplus:cities', 'redstoneplus:grid', 'redstoneplus:sealed_wall', 'redstoneplus:sanctums', 'redstoneplus:chain_anchor',
              'redstoneplus:red_sea', 'redstoneplus:rim', 'redstoneplus:abyss', 'redstoneplus:generator_hall', 'redstoneplus:relay_spire', 'redstoneplus:circuit_temple', 'redstoneplus:ore_realm_redstone', 'redstoneplus:ore_realm_iron', 'redstoneplus:ore_realm_copper', 'redstoneplus:cave_clusters_floor',
              'redstoneplus:scree'}


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
    'piston_karst': ('Piston Karst', 'Kolbenkarst', 0.9, 0.3, ('#e0a070', '#e8c8a0', '#c86a30', '#3a1a0a', '#c0a060'), None,
                     features('karst_spire', 'crusher_passage', 'crusher_mill', 'lichen_tuft_patch', 'beast_cage', 'molten_redstone_spring'),
                     [('karst_colossus', 25, 1, 1), ('relay_strider', 20, 1, 1), ('detonator_husk', 30, 1, 2)], [('flesh_press', 3, 1, 1)], ['karst_colossus']),
    'switchyard_flats': ('Switchyard Flats', 'Weichenebene', 1.2, 0.1, ('#c8786a', '#d08a70', '#8a4a3a', '#3a1a10', '#a8905a'), fx(RED_DUST, 0.004),
                         features('tracks', 'slag_heap', 'switchyard_junction', 'tower', 'lamp_pylon', 'minecart_loop', 'salt_brush_patch', 'ember_oil_lake'),
                         [('switchback_crawler', 40, 1, 2), ('detonator_husk', 30, 1, 2), ('kilnbound', 20, 1, 1)], [('bellows_hog', 10, 2, 3)],
                         ['switchback_crawler']),
    'sluice_gardens': ('Sluice Gardens', 'Schleusengärten', 0.7, 0.9, ('#e8b088', '#e0b898', '#d0782a', '#40200a', '#c87a30'), None,
                       features('sluice_bridge', 'aqueduct', 'pump_station', 'copper_reed_patch', 'fen_reed_patch'),
                       [('sluice_chainjaw', 40, 1, 2), ('leaking_cell', 30, 1, 2), ('living_capacitor', 20, 1, 2)], [], ['sluice_chainjaw']),
    'kiln_barrens': ('Kiln Barrens', 'Brennofen-Öde', 2.0, 0.0, ('#b0603a', '#8a4a2a', '#c06a2a', '#3a1a0a', '#6a5a3a'), fx('minecraft:white_ash', 0.01),
                     features('lava_channels', 'kiln_spire', 'sulfur_rock', 'lamp_pylon', 'minecraft:lake_lava_surface', 'kiln_bridge', 'cinder_bloom_patch', 'molten_redstone_lake', 'ember_oil_spring'),
                     [('kiln_brute', 30, 1, 1), ('kilnbound', 40, 1, 2), ('detonator_husk', 15, 1, 1)], [('bellows_hog', 15, 2, 3)], ['kiln_brute']),
    'tripwire_briar': ('Tripwire Briar', 'Stolperdraht-Dickicht', 0.7, 0.8, ('#d8b060', '#b89a60', '#8a5a2a', '#2a1a0a', '#7a5a2a'), None,
                       features('briar_ambush', 'briar_patch', 'laser_post', 'tower', 'ruin', 'lichen_tuft_patch'),
                       [('spool_weaver', 40, 1, 2), ('tripwire_brood', 35, 1, 2), ('leaking_cell', 15, 1, 2)], [], ['spool_weaver']),
    'arsenal_dunes': ('Arsenal Dunes', 'Arsenal-Dünen', 1.6, 0.0, ('#e04a36', '#c83a2a', '#9a2020', '#3a0a0a', '#b04a30'), fx(RED_DUST, 0.006),
                      features('dunes', 'dune_rock', 'crashed_shell', 'tower', 'lamp_pylon', 'scrap', 'minecart_loop', 'salt_brush_patch', 'ember_oil_lake'),
                      [('switchback_crawler', 30, 1, 2), ('detonator_husk', 35, 1, 2), ('kilnbound', 25, 1, 2)], [('bellows_hog', 8, 1, 2)],
                      ['switchback_crawler']),
    'rubedo_gardens': ('Rubedo Gardens', 'Rubedo-Gärten', 0.9, 0.6, ('#e87070', '#d85a5a', '#e0202a', '#5a0a0a', '#c03a3a'), fx(RED_DUST, 0.008),
                       features('terrace_pools', 'rubedo_spire', 'crystal_dome', 'aqueduct', 'red_coral_shrub_patch', 'crimson_heather_patch', 'pump_station', 'molten_redstone_lake'),
                       [('living_capacitor', 40, 1, 3), ('relay_strider', 20, 1, 1), ('leaking_cell', 20, 1, 2)], [], []),
    'landmark_moors': ('Landmark Moors', 'Wegmarken-Moore', 0.6, 0.6, ('#c83030', '#a82424', '#7a1a1a', '#2a0808', '#a02a40'), None,
                       features('monolith', 'bell_tower', 'crimson_heather_patch', 'ruin'),
                       [('bell_stalker', 20, 1, 1), ('relay_strider', 25, 1, 1), ('detonator_husk', 20, 1, 2)], [('flesh_press', 3, 1, 1)], ['bell_stalker']),
    'red_clay_fen': ('Red Clay Fen', 'Rotton-Moor', 0.8, 0.9, ('#b83e32', '#98322a', '#a82418', '#3a0a08', '#8a3a2a'), None,
                     features('ponds', 'boardwalk', 'kiln_hut', 'pump_station', 'fen_reed_patch', 'rust_brine_lake'),
                     [('sluice_chainjaw', 35, 1, 2), ('leaking_cell', 30, 1, 3), ('living_capacitor', 25, 1, 2)], [], ['sluice_chainjaw']),
    'hematite_scarps': ('Hematite Scarps', 'Hämatit-Klippen', 1.1, 0.2, ('#d86040', '#c84e3a', '#7a2a2a', '#2a0a0a', '#a0402a'), fx(RED_DUST, 0.004),
                        features('mesas', 'hoodoo', 'beast_cage', 'ruin', 'red_coral_shrub_patch', 'crusher_mill', 'molten_redstone_spring'),
                        [('karst_colossus', 20, 1, 1), ('tripwire_brood', 30, 1, 2), ('detonator_husk', 25, 1, 2)], [('flesh_press', 4, 1, 1)],
                        ['karst_colossus']),
    'tempest_shoals': ('Tempest Shoals', 'Sturmbänke', 0.5, 0.9, ('#5a2a2a', '#3a2020', '#b0301a', '#2a0806', '#6a2a1a'), fx({'type': 'minecraft:dust', 'color': [1.0, 0.55, 0.15], 'scale': 0.8}, 0.004),
                       features('tempest_pillar', 'storm_spire'),
                       [('sluice_chainjaw', 35, 1, 2), ('relay_strider', 25, 1, 1)], [], ['sluice_chainjaw']),
    'frostwork_wastes': ('Frostwork Wastes', 'Frostwerk-Öde', 0.3, 0.0, ('#e8c0a8', '#f0d8c8', '#c87a5a', '#2a1410', '#d8b0a0'), fx('minecraft:white_ash', 0.02),
                         features('crevasses', 'frost_spire', 'ice_rails', 'tower', 'frost_fern_patch', 'rust_brine_lake'),
                         [('switchback_crawler', 25, 1, 2), ('spool_weaver', 25, 1, 2), ('karst_colossus', 15, 1, 1)], [], ['karst_colossus']),
    'vein_mire': ('Vein Mire', 'Adermoor', 0.8, 0.9, ('#4a3a30', '#4a3a32', '#3a1414', '#140404', '#4a3a2a'), fx('minecraft:crimson_spore', 0.01),
                  features('ponds', 'pale_stalk_patch', 'boardwalk', 'fen_reed_patch', 'kiln_hut', 'blight_sap_lake'),
                  [('leaking_cell', 35, 1, 3), ('tripwire_brood', 30, 1, 2), ('spool_weaver', 20, 1, 1)], [], []),
    'oxide_salt_flats': ('Oxide Salt Flats', 'Oxid-Salzebene', 1.3, 0.0, ('#eca858', '#e8b878', '#d8904a', '#3a200a', '#c09060'), fx(RED_DUST, 0.003),
                         features('salt_mound', 'scrap', 'rail_line', 'ruin', 'lamp_pylon', 'salt_brush_patch', 'tower', 'rust_brine_lake', 'ember_oil_lake'),
                         [('switchback_crawler', 30, 1, 2), ('kilnbound', 25, 1, 2), ('detonator_husk', 25, 1, 2)], [('bellows_hog', 10, 2, 3)],
                         ['switchback_crawler']),
    'lamplit_grove': ('Lamplit Grove', 'Lampenhain', 0.6, 0.8, ('#6a2a20', '#5a2418', '#7a2a1a', '#1a0604', '#8a3a1a'), fx('minecraft:crimson_spore', 0.01),
                      features('giant_tree', 'crimson_heather_patch', 'pale_stalk_patch', 'bell_tower', 'dawn_nectar_lake'),
                      [('spool_weaver', 30, 1, 2), ('relay_strider', 25, 1, 1), ('bell_stalker', 15, 1, 1)], [], []),
    'resonance_hollows': ('Resonance Hollows', 'Resonanzhöhlen', 0.6, 0.4, ('#a8603a', '#5a2a14', '#ffa040', '#4a200a', '#a8603a'), fx({'type': 'minecraft:dust', 'color': [1.0, 0.7, 0.25], 'scale': 0.7}, 0.008),
                          features('resonance_gatehouse', 'resonant_crystal_ore', 'cave_clusters_ceiling', 'resonant_ichor_pool', 'resonant_ichor_spring'),
                          [('bell_stalker', 30, 1, 1), ('tripwire_brood', 35, 1, 2), ('living_capacitor', 25, 1, 2)], [], ['bell_stalker']),
    'circuit_fossil_beds': ('Circuit Fossil Beds', 'Schaltungs-Fossilbetten', 0.9, 0.4, ('#8a1a1a', '#6a1010', '#a02020', '#300808', '#8a2020'), fx(RED_DUST, 0.01),
                            features('ore_redstone_vein', 'cave_clusters_ceiling', 'red_coral_shrub_patch', 'molten_redstone_pool'),
                            [('tripwire_brood', 30, 1, 2), ('living_capacitor', 30, 1, 2), ('relay_strider', 25, 1, 1)], [], []),
    # walled off: blood-red sky, fog that never lifts (see RealmSky), and the things that never kept the Concordance
    'sealed_reach': ('The Sealed Reach', 'Die Versiegelte Weite', 0.8, 0.0, ('#3a0606', '#2a0404', '#4a0a06', '#140202', '#3a0a08'),
                     fx({'type': 'minecraft:dust', 'color': [0.55, 0.05, 0.03], 'scale': 1.2}, 0.03),
                     features('monolith', 'ruin', 'beast_cage', 'briar_patch', 'cinder_bloom_patch', 'blight_sap_lake'),
                     [('wirewraith', 30, 1, 1), ('maw_engine', 20, 1, 1)], [], []),
    # the void around the artery: no ground, a deep red haze, embers drifting up from the Blood Below
    'the_abyss': ('The Abyss', 'Der Abgrund', 1.0, 0.0, ('#1c0404', '#3a0806', '#5a0a06', '#200404', '#3a0a08'),
                  fx({'type': 'minecraft:dust', 'color': [1.0, 0.3, 0.08], 'scale': 0.7}, 0.006), features(), [], [], []),
}
UNDERGROUND = {'resonance_hollows', 'circuit_fossil_beds', 'the_abyss'}
# no wildlife and no Trackwrights inside the walls of the Sealed Reach
WALLED = {'sealed_reach'}
CONSTRUCTS = {'karst_colossus', 'switchback_crawler', 'bell_stalker', 'sluice_chainjaw', 'kiln_brute', 'spool_weaver'}
# the realm's own wildlife lives everywhere on the surface: grazing mites in herds and the jackal packs that hunt them
WILDLIFE = [('spark_mite', 14, 3, 6), ('scrap_jackal', 5, 2, 3)]
for biome_name, (e, g, temp, rain, cols, particle, feats, monsters, creatures, costs) in BIOMES.items():
    sky, fog, water, water_fog, plant = cols
    effects = {'sky_color': c(sky), 'fog_color': c(fog), 'water_color': c(water), 'water_fog_color': c(water_fog),
               'grass_color': c(plant), 'foliage_color': c(plant),
               'mood_sound': {'sound': 'minecraft:ambient.cave', 'tick_delay': 6000, 'block_search_extent': 8, 'offset': 2.0}}
    if particle:
        effects['particle'] = particle
    effects['music'] = {'sound': f'{NS}:music.realm', 'min_delay': 1200, 'max_delay': 4800, 'replace_current_music': True}
    if biome_name not in UNDERGROUND and biome_name not in WALLED:
        # the realm's wildlife, and now and then a Trackwright out laying lightlines
        creatures = creatures + WILDLIFE + [('trackwright', 3, 1, 1)]
        ambient = [spawn('lamp_moth', 30, 3, 6)]
    else:
        ambient = []
    # balance: capacitors and striders otherwise fill the monster cap before the big constructs get a chance
    damp = {'living_capacitor': 0.25, 'relay_strider': 0.4}
    monsters = [(m, max(4, int(w * damp.get(m, 1.6 if m in CONSTRUCTS else 1.0))), lo, hi + (0 if m in damp else 1)) for m, w, lo, hi in monsters]
    biome = {
        'has_precipitation': rain > 0.2, 'temperature': temp, 'downfall': rain, 'effects': effects,
        'spawners': {'monster': [spawn(*m) for m in monsters], 'creature': [spawn(*cr) for cr in creatures], 'ambient': ambient, 'axolotls': [],
                     'underground_water_creature': [], 'water_creature': [], 'water_ambient': [], 'misc': []},
        'spawn_costs': {f'{NS}:{m}': {'energy_budget': 1.0, 'charge': 0.4} for m in costs},
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
    'frostwork_wastes': ground([(0.35, 9, 'frost_realmstone'), 'salt_crust'], 'frost_realmstone', 'frost_realmstone'),
    'vein_mire': ground([(0.55, 9, 'redstone_vein'), 'root_soil'], 'root_soil', 'root_soil'),
    'oxide_salt_flats': ground([(-0.04, 0.04, 'redstone_vein'), (0.5, 9, 'rusted_soil'), 'salt_crust'], 'salt_crust', 'salt_crust'),
    'lamplit_grove': ground('grove_moss', 'root_soil', 'root_soil'),
    'sealed_reach': ground([(0.4, 9, 'cinder_rock'), 'blight_crust'], 'blight_crust', 'dark_hematite'),
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
# no sea and no water table: outside the artery there is only the void (its floor, the Blood Below, is laid by a feature)
settings['default_fluid'] = {'Name': 'minecraft:air'}
settings['aquifers_enabled'] = False
settings['noise_router']['final_density'] = {'type': f'{NS}:artery', 'argument': settings['noise_router']['final_density']}
# biomes a third of the overworld's size, so all of them are within reach: only the biome noises are sampled faster,
# the terrain shape is untouched
for climate in ('temperature', 'vegetation'):
    settings['noise_router'][climate]['xz_scale'] = 0.75
write(os.path.join(DATA, 'worldgen', 'noise_settings', 'redstone_realm.json'), settings)

write(os.path.join(DATA, 'dimension_type', 'redstone_realm.json'), {
    'ultrawarm': False, 'natural': True, 'coordinate_scale': 1.0, 'has_skylight': True, 'has_ceiling': False, 'ambient_light': 0.0,
    'monster_spawn_light_level': {'type': 'minecraft:uniform', 'min_inclusive': 0, 'max_inclusive': 7}, 'monster_spawn_block_light_limit': 0,
    'piglin_safe': False, 'bed_works': True, 'respawn_anchor_works': False, 'has_raids': False,
    'logical_height': 384, 'min_y': -64, 'height': 384, 'infiniburn': '#minecraft:infiniburn_overworld', 'effects': f'{NS}:redstone_realm'})


# ---- the map: the realm is one giant artery of land over a void (see Artery.java). The biomes lie along it on purpose,
# placed by the realm's own biome source; the shape is cut by the realm's own density function around the overworld terrain.
write(os.path.join(DATA, 'dimension', 'redstone_realm.json'), {
    'type': f'{NS}:redstone_realm',
    'generator': {'type': 'minecraft:noise', 'settings': f'{NS}:redstone_realm', 'biome_source': {'type': f'{NS}:artery'}}})

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
# resonance: a thin amber ring that the particle then grows
particle_frames('realm_resonance', [radial(16, lambda d, x, y, k=k: (
    (255, 170, 60, 230 - k * 40) if 0.72 - k * 0.04 < d < 0.92 else (255, 230, 170, 120) if 0.62 < d <= 0.72 - k * 0.04 else None)) for k in range(4)])
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
    'THE HISTORY I\n\nThis was the Engine World of the Wirewrights. One Great Bell, the Concordance, hung in a cradle at the heart of their Foundry City; every clock and machine kept time by its toll.',
    'THE HISTORY II\n\nTo wake every engine at once the Council ordered the Overtoll. The cradle cracked, the cities answered, and on the last stroke the Bell tore free and rose into the sky.',
    'THE HISTORY III\n\nIt hangs there still in place of the sun. When it tolls, the machines below lose a little more of their purpose. Etched Plates found in old chests tell the rest.',
    'THE RULES\n\nThe realm still keeps the Concordance. Its creatures leave you alone until you:\n\n1. strike first,\n2. break what the Wirewrights built (their bricks, tiles, bronze, lamps, bells and traps),\n3. move while the Great Bell tolls.',
    'THE COUNT\n\nThe bar at the top counts the rules you have broken. At three, every creature of the realm hunts you on sight. Fight back freely when they come for you: defending is no crime. The bar also counts down to each toll. The realm forgets the dead.',
    'ATONEMENT\n\nRelight a dead lamp with redstone: one rule (each lamp once, one a minute).\nRing a bell while the Great Bell tolls: two rules.\nLay an Etched Plate on Bell Bronze (the Cradle plazas are full of it): all of them.\nAtoning calls off the hunt.',
    'FOUNDRY CITIES\n\nThe ruined cities of the Wirewrights spread hundreds of blocks wide, behind broken walls. At the centre of each stands the empty cradle where a bell once hung.',
    'THE GRID\n\nGlowing Lightlines cross the realm every 256 blocks, north-south and east-west. Where they meet stand nodes: cycle depots, junction spires, trackworks and bell gates. Many lines broke; the Trackwrights mend them.',
    'LIGHT CYCLES\n\nOn your first visit a light cycle is built for you. Ride it onto a Lightline: it locks on and races along it. Tap A or D before a junction to turn. Hold jump for overdrive, S to brake, S at a standstill to turn round.',
    'YOUR CYCLE\n\nThe realm keeps it by your side: if it is lost, it is rebuilt next to you. The Cycle Key calls it any time. It holds still by itself while the Great Bell tolls. Press M for the Grid Map; the bar on the left shows the lines around you.',
    'TRACKWRIGHTS\n\nCrab-legged paving engines. They walk to the broken ends of lines and lay tile after tile, and they start new branch lines that end at a waystop beacon: new ways to turn down. Their lightlines count as built: leave them be.',
    'THE SEALED REACH\n\nWalled lands with crimson signs all around. Inside, the Wirewraith and the Maw Engine never kept the Concordance: they hunt everyone, and fighting them breaks no rule. They never leave the walls. Neither should you.',
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
    'DIE GESCHICHTE I\n\nDies war die Maschinenwelt der Drahtwerker. Eine Große Glocke, die Eintracht, hing in einer Wiege im Herzen ihrer Gießereistadt; jede Uhr und jede Maschine ging nach ihrem Schlag.',
    'DIE GESCHICHTE II\n\nUm alle Maschinen zugleich zu wecken, befahl der Rat den Überschlag. Die Wiege riss, die Städte antworteten, und beim letzten Schlag riss sich die Glocke los und stieg in den Himmel.',
    'DIE GESCHICHTE III\n\nDort hängt sie noch heute statt der Sonne. Wenn sie schlägt, verlieren die Maschinen unten ein wenig mehr ihres Zwecks. Gravierte Platten in alten Truhen erzählen den Rest.',
    'DIE REGELN\n\nDas Reich hält noch die Eintracht. Seine Kreaturen lassen dich in Ruhe, bis du:\n\n1. zuerst zuschlägst,\n2. zerstörst, was die Drahtwerker gebaut haben (Ziegel, Fliesen, Bronze, Lampen, Glocken, Fallen),\n3. dich bewegst, während die Große Glocke schlägt.',
    'DIE ZÄHLUNG\n\nDer Balken oben zählt deine gebrochenen Regeln. Ab drei jagt dich jede Kreatur des Reichs. Wehr dich ruhig, wenn sie kommen: Verteidigung ist kein Vergehen. Der Balken zählt auch bis zu jedem Schlag herunter. Das Reich vergisst die Toten.',
    'SÜHNE\n\nEine tote Lampe mit Redstone neu entzünden: eine Regel (jede Lampe einmal, eine pro Minute).\nEine Glocke läuten, während die Große Glocke schlägt: zwei Regeln.\nEine Gravierte Platte auf Glockenbronze legen (die Wiegen-Plätze sind voll davon): alle.\nSühne beendet die Jagd.',
    'GIESSEREISTÄDTE\n\nDie zerfallenen Städte der Drahtwerker sind hunderte Blöcke breit, hinter zerbrochenen Mauern. In ihrer Mitte steht die leere Wiege, in der einst eine Glocke hing.',
    'DAS RASTER\n\nLeuchtende Lichtbahnen durchziehen das Reich alle 256 Blöcke, von Nord nach Süd und Ost nach West. Wo sie sich treffen, stehen Knoten: Raddepots, Kreuzungstürme, Bahnwerke und Glockentore. Viele Bahnen brachen; die Bahnwerker flicken sie.',
    'LICHTRÄDER\n\nBeim ersten Besuch wird dir ein Lichtrad gebaut. Fahr damit auf eine Lichtbahn: es rastet ein und rast darauf entlang. Tippe vor einer Kreuzung A oder D zum Abbiegen. Springen halten: Schub, S: bremsen, S im Stand: wenden.',
    'DEIN RAD\n\nDas Reich hält es an deiner Seite: geht es verloren, wird es neben dir neu gebaut. Der Lichtrad-Schlüssel ruft es jederzeit. Während die Große Glocke schlägt, hält es von selbst still. M öffnet die Rasterkarte; links siehst du die Bahnen um dich.',
    'BAHNWERKER\n\nPflastermaschinen auf Krabbenbeinen. Sie gehen zu den Bruchstellen der Bahnen und legen Kachel um Kachel, und sie beginnen neue Abzweige, die an einem Haltestellen-Leuchtfeuer enden: neue Wege zum Abbiegen. Ihre Bahnen gelten als gebaut: lass sie ganz.',
    'DIE VERSIEGELTE WEITE\n\nUmmauerte Lande, ringsum karminrote Schilder. Drinnen halten Drahtgespenst und Schlundmaschine die Eintracht nicht: sie jagen jeden, und sie zu bekämpfen bricht keine Regel. Sie verlassen die Mauern nie. Du solltest sie nicht betreten.',
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
BOOK_EN += [
    'THE LIQUIDS I\nMolten Redstone: burns, glows, powers redstone it touches.\nEmber Oil: catches fire; wading in it makes you fire proof.\nRust Brine: the salt sea; corrodes your gear, rusts iron.',
    'THE LIQUIDS II\nResonant Ichor: soft falls, high jumps, it sets off sculk sensors.\nBlight Sap: saps strength, withers, spreads blight.\nDawn Nectar: heals and feeds, grows plants. It only flows once the realm is free.',
    'WHERE THEY MEET\nMolten Redstone + water: Redstone Vein; + lava: realm redstone ore.\nEmber Oil + fire or lava: flames.\nRust Brine + lava: hematite.\nIchor + lava: resonant crystal.\nBlight Sap + Dawn Nectar: heather turf.',
    'THE ECHOES\n\nWhen the Bell tore free, its five voices broke away: the Echoes of Force, Signal, Resonance, Heat and Flow, beings of pure redstone light. Each holds one of the giant chains that keep the Bell tolling over the realm.',
    'THE ARENAS\n\nEach Echo keeps one arena on the artery: the Press Throne (Piston Karst), the Switchboard (Switchyard), the Belfry Hollow (Moors), the Sluice Basin (by the Red Sea) and the Furnace Crown (Kiln Barrens). A giant chain rises from each into the sky. The Tuning Fork hums the way.',
    'WAKING AN ECHO\n\nUse the Echo Seal at the heart of a sanctum. The Echo comes. It hits hard, and at half strength it calls the realm\'s creatures to its side. Fighting it breaks no rule. When it falls, its chain breaks: in the world, and in the sky.',
    'THE LAST FIGHT\n\nForge the five cores and a block of bell bronze into the Heart of the Five. Lay it on the socket of the Great Cradle, in the Heart, the widest chamber of the artery. The Bell comes down: the Overtoll. When it tolls, stand still.',
    'THE FREED REALM\n\nWhen the Overtoll falls, a red sun rises where it hung. The light grows warm, the creatures bloom and leave you in peace, and around you the land heals: the chains fall, blight turns to heather, dead lamps light, dawn lilies grow.',
]
BOOK_DE += [
    'DIE FLÜSSIGKEITEN I\nGeschmolzenes Redstone: brennt, leuchtet, versorgt Redstone.\nGlutöl: fängt Feuer; wer hindurchwatet, wird feuerfest.\nRostlake: das Salzmeer; zerfrisst deine Ausrüstung, lässt Eisen rosten.',
    'DIE FLÜSSIGKEITEN II\nResonanz-Ichor: weiche Stürze, hohe Sprünge, weckt Sculk-Sensoren.\nFäulnissaft: raubt Kraft, lässt verdorren, verbreitet Fäulnis.\nMorgennektar: heilt, nährt, lässt Pflanzen wachsen. Er fließt erst, wenn das Reich frei ist.',
    'WO SIE SICH TREFFEN\nRedstone + Wasser: Redstone-Ader; + Lava: Reichs-Redstone-Erz.\nGlutöl + Feuer oder Lava: Flammen.\nRostlake + Lava: Hämatit.\nIchor + Lava: Resonanzkristall.\nFäulnissaft + Morgennektar: Heideboden.',
    'DIE ECHOS\n\nAls die Glocke sich losriss, brachen ihre fünf Stimmen ab: die Echos der Kraft, des Signals, der Resonanz, der Hitze und des Flusses, Wesen aus reinem Redstone-Licht. Jedes hält eine der Riesenketten, die die Glocke über dem Reich schlagen lassen.',
    'DIE ARENEN\n\nJedes Echo hütet eine Arena auf der Ader: Pressenthron (Kolbenkarst), Schaltwarte (Weichenebene), Glockenmulde (Moore), Schleusenbecken (am Roten Meer) und Ofenkrone (Brennofen-Öde). Aus jeder steigt eine Riesenkette in den Himmel. Die Stimmgabel summt den Weg.',
    'EIN ECHO WECKEN\n\nBenutze das Echo-Siegel im Herzen eines Heiligtums. Das Echo kommt. Es schlägt hart zu, und bei halber Kraft ruft es die Kreaturen des Reichs zu Hilfe. Es zu bekämpfen bricht keine Regel. Fällt es, bricht seine Kette: in der Welt und am Himmel.',
    'DER LETZTE KAMPF\n\nSchmiede die fünf Kerne und einen Block Glockenbronze zum Herz der Fünf. Lege es auf den Sockel der Großen Wiege im Herzen, der weitesten Kammer der Ader. Die Glocke kommt herab: der Übergeläut. Wenn er schlägt, halte still.',
    'DAS BEFREITE REICH\n\nFällt der Übergeläut, geht eine rote Sonne auf, wo er hing. Das Licht wird warm, die Kreaturen blühen auf und lassen dich in Frieden, und um dich heilt das Land: die Ketten fallen, Fäulnis wird Heide, tote Lampen leuchten, Morgenlilien wachsen.',
]
for key, e, g in (
        ('blood', 'The Blood Below! The artery will not let you drown in it...', 'Das Blut darunter! Die Ader lässt dich nicht darin versinken...'),
        ('carried_back', 'A pulse of the artery carries you up out of the Blood Below and throws you onto the land.',
         'Ein Pulsschlag der Ader trägt dich aus dem Blut darunter herauf und wirft dich an Land.'),
        ('edge', 'The realm ends here: only the void lies beyond, and it pushes you back.', 'Hier endet das Reich: dahinter liegt nur die Leere, und sie drängt dich zurück.'),
        ('returned', 'The void will not hold you. The artery pulls you back to its land.', 'Die Leere hält dich nicht. Die Ader zieht dich zurück an ihr Land.')):
    name(f'artery.{NS}.{key}', e, g)
BOOK_EN += [
    'CREWS\n\nThe realm\'s creatures work in crews. A crew has a node, its leader, linked by lines of light to its members. Warbands march in a wedge, jackal packs in single file, mite herds in two columns, the hunters of the Reach in a ring.',
    'ORDERS\n\nThe node decides. Its orders run down the links as pulses of light; the farther a member, the later it hears. Each member nods and flashes as the order reaches it, and a fainter pulse runs back: the acknowledgement.',
    'CREWS AT WORK\n\nWhen the node takes on a job (a lamp, a bell, a wall), its crew rings the work, faces it and works in step. Every member in place makes the job go faster. Idle, a crew stands in formation and sweeps its gaze round together.',
    'CREWS IN A FIGHT\n\nAn engaged crew encircles its target. Only the members holding an attack token (a spike of light above them) close in; the tokens pass round the ring. The badly hurt drop back to repair. When the node falls, the crew elects another; a losing crew calls for help.',
]
BOOK_DE += [
    'TRUPPS\n\nDie Kreaturen des Reichs arbeiten in Trupps. Ein Trupp hat einen Knoten, seinen Anführer, mit Lichtlinien zu seinen Mitgliedern verbunden. Kriegshaufen marschieren im Keil, Schakalrudel im Gänsemarsch, Milbenherden in zwei Reihen, die Jäger der Weite im Ring.',
    'BEFEHLE\n\nDer Knoten entscheidet. Seine Befehle laufen als Lichtimpulse die Verbindungen entlang; je weiter weg ein Mitglied, desto später hört es sie. Jedes nickt und blitzt auf, wenn der Befehl ankommt, und ein schwächerer Impuls läuft zurück: die Bestätigung.',
    'TRUPPS BEI DER ARBEIT\n\nNimmt sich der Knoten eine Arbeit vor (eine Lampe, eine Glocke, eine Mauer), umringt sein Trupp sie, blickt darauf und arbeitet im Takt. Jedes Mitglied an seinem Platz macht die Arbeit schneller. Untätig steht ein Trupp in Formation und schaut gemeinsam reihum.',
    'TRUPPS IM KAMPF\n\nEin kämpfender Trupp umzingelt sein Ziel. Nur wer eine Angriffsmarke hält (ein Lichtdorn über ihm), rückt vor; die Marken wandern im Ring. Schwer Verletzte ziehen sich zur Reparatur zurück. Fällt der Knoten, wählt der Trupp einen neuen; ein unterlegener Trupp ruft Hilfe.',
]
BOOK_EN[1:1] = [
    'THE ARTERY\n\nThe realm does not go on forever. It is one giant artery of the world: a vessel of land eight thousand blocks long, winding over a red void. It swells into chambers and sends side vessels off its flanks.',
    'THE WAY ALONG IT\nWest Root, Piston Karst (Force), Switchyard (Signal), Moors and Fen (Resonance), the Heart with the Great Cradle, the Red Sea (Flow), the Grove, the Kiln Barrens (Heat), the Salt Flats, East Root. The Sealed Reach is a walled side vessel near the east.',
    'THE BLOOD BELOW\n\nAt the bottom of the void lies a sea of molten redstone. Veins hang down into it from the rims and the underside, and at both ends the artery dives into it: there the realm joins the world. Fall in, and the artery carries you back up.',
]
BOOK_DE[1:1] = [
    'DIE ADER\n\nDas Reich geht nicht ewig weiter. Es ist eine riesige Ader der Welt: ein Gefäß aus Land, achttausend Blöcke lang, das sich über eine rote Leere windet. Es weitet sich zu Kammern und treibt Seitengefäße aus seinen Flanken.',
    'DER WEG ENTLANG\nWestwurzel, Kolbenkarst (Kraft), Weichenebene (Signal), Moore und Moor (Resonanz), das Herz mit der Großen Wiege, das Rote Meer (Fluss), der Hain, die Brennofen-Öde (Hitze), die Salzebene, Ostwurzel. Die Versiegelte Weite ist ein ummauertes Seitengefäß im Osten.',
    'DAS BLUT DARUNTER\n\nAm Grund der Leere liegt ein Meer aus geschmolzenem Redstone. Von den Rändern und der Unterseite hängen Adern hinein, und an beiden Enden taucht die Ader darin ein: dort ist das Reich mit der Welt verbunden. Wer hineinfällt, wird zurückgetragen.',
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
