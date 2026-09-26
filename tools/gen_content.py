"""
Assets and data for the 1.6 content: ore materials, tools, armor, decorative blocks, mob drops, mobs and biomes.

Textures are recoloured from the vanilla textures inside the Minecraft client jar (found in the Gradle cache),
so nothing from Mojang is stored in this repository. Run after gen_textures.js and gen_data.js:
    python tools/gen_content.py
"""
import colorsys
import glob
import io
import json
import os
import zipfile

from PIL import Image

NS = 'redstoneplus'
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)
MC_DATA = os.path.join(ROOT, 'data', 'minecraft')

jars = glob.glob(os.path.expanduser('~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/1.21.1/client.jar'))
if not jars:
    raise SystemExit('Minecraft 1.21.1 client.jar not found in the Gradle cache - run a Gradle build once first')
JAR = zipfile.ZipFile(jars[0])


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


def rgb(hexcolor):
    h = hexcolor.lstrip('#')
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def recolor(img, color, sat_min=0.2, hue_lo=None, hue_hi=None):
    """Moves the coloured pixels (e.g. the cyan of diamond) to the hue of `color`, keeping shading."""
    th, ts, tv = colorsys.rgb_to_hsv(*rgb(color))
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < sat_min or (hue_lo is not None and not (hue_lo <= h <= hue_hi)):
                continue
            ns = min(1.0, ts * (0.55 + 0.6 * s))
            nv = min(1.0, v * (0.55 + 0.6 * tv))
            nr, ng, nb = colorsys.hsv_to_rgb(th, ns, nv)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def tint(img, color, strength=1.0, gain=1.3):
    """Colours a grey texture (iron, stone, skeleton...) by multiplying its brightness with `color`."""
    tr, tg, tb = rgb(color)
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255 * gain
            cr, cg, cb = min(1, lum * tr), min(1, lum * tg), min(1, lum * tb)
            px[x, y] = (round((r / 255 * (1 - strength) + cr * strength) * 255),
                        round((g / 255 * (1 - strength) + cg * strength) * 255),
                        round((b / 255 * (1 - strength) + cb * strength) * 255), a)
    return out


def overlay_gems(base, source, color):
    """Puts the coloured gem specks of `source` (diamond ore) on top of another stone texture."""
    gems = recolor(source, color, sat_min=0.25)
    out = base.copy()
    bp, sp, gp = out.load(), source.load(), gems.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = sp[x, y]
            if colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] >= 0.25:
                bp[x, y] = gp[x, y]
    return out


# ---------------------------------------------------------------- materials
MATERIALS = {
    # id: (kind, colour, english, german)
    'ruby': ('gem', '#e0284a', 'Ruby', 'Rubin'),
    'sapphire': ('gem', '#2f5fe0', 'Sapphire', 'Saphir'),
    'titanium': ('metal', '#b8c8d8', 'Titanium', 'Titan'),
    'cobalt': ('metal', '#2f7fd0', 'Cobalt', 'Kobalt'),
    'mythril': ('metal', '#3ee0c8', 'Mythril', 'Mithril'),
    'voidium': ('end', '#8a3ae8', 'Voidium', 'Voidium'),
    'uranium': ('tools', '#3cff3c', 'Uranium', 'Uran'),
}
TOOLS = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe']
ARMOR = ['helmet', 'chestplate', 'leggings', 'boots']
TOOL_DE = {'sword': 'schwert', 'pickaxe': 'spitzhacke', 'axe': 'axt', 'shovel': 'schaufel', 'hoe': 'hacke',
           'helmet': 'helm', 'chestplate': 'brustpanzer', 'leggings': 'beinschutz', 'boots': 'stiefel'}
TOOL_EN = {k: k.capitalize() for k in TOOL_DE}

en, de = {}, {}
blocks_simple = []  # (name, texture name) for cube_all blocks
pickaxe_blocks, needs_iron, needs_diamond = [], [], []


def block_cube(name, texture=None):
    texture = texture or name
    write(os.path.join(ASSETS, 'models', 'block', name + '.json'),
          {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{texture}'}})
    write(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': {'': {'model': f'{NS}:block/{name}'}}})
    write(os.path.join(ASSETS, 'models', 'item', name + '.json'), {'parent': f'{NS}:block/{name}'})
    pickaxe_blocks.append(f'{NS}:{name}')


def item_model(name, handheld=False):
    write(os.path.join(ASSETS, 'models', 'item', name + '.json'),
          {'parent': 'minecraft:item/handheld' if handheld else 'minecraft:item/generated',
           'textures': {'layer0': f'{NS}:item/{name}'}})


def loot_self(name):
    write(os.path.join(DATA, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'{NS}:blocks/{name}'})


SILK = [{'condition': 'minecraft:match_tool', 'predicate': {'predicates': {
    'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]


def loot_ore(name, drop, count=(1, 1)):
    fns = []
    if count != (1, 1):
        fns.append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': count[0], 'max': count[1]}, 'add': False})
    fns += [{'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
            {'function': 'minecraft:explosion_decay'}]
    write(os.path.join(DATA, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': f'{NS}:{name}', 'conditions': SILK},
            {'type': 'minecraft:item', 'name': drop, 'functions': fns}]}]}],
        'random_sequence': f'{NS}:blocks/{name}'})


def ing(i):
    return {'tag': i[1:]} if i.startswith('#') else {'item': i}


def shaped(name, pattern, key, result=None, count=1):
    write(os.path.join(DATA, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: ing(v) for k, v in key.items()}, 'result': {'id': result or f'{NS}:{name}', 'count': count}})


def shapeless(name, items, result, count=1):
    write(os.path.join(DATA, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [ing(i) for i in items],
        'result': {'id': result, 'count': count}})


def cook(name, source, result, xp=0.7, blasting=True, smoking=False):
    write(os.path.join(DATA, 'recipe', name + '_from_smelting.json'), {
        'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': ing(source), 'result': {'id': result},
        'experience': xp, 'cookingtime': 200})
    if blasting:
        write(os.path.join(DATA, 'recipe', name + '_from_blasting.json'), {
            'type': 'minecraft:blasting', 'category': 'misc', 'ingredient': ing(source), 'result': {'id': result},
            'experience': xp, 'cookingtime': 100})
    if smoking:
        write(os.path.join(DATA, 'recipe', name + '_from_smoking.json'), {
            'type': 'minecraft:smoking', 'category': 'food', 'ingredient': ing(source), 'result': {'id': result},
            'experience': xp, 'cookingtime': 100})


diamond_ore = vanilla('block/diamond_ore')
deepslate_diamond_ore = vanilla('block/deepslate_diamond_ore')
item_tags = {t: [] for t in ['swords', 'pickaxes', 'axes', 'shovels', 'hoes', 'head_armor', 'chest_armor', 'leg_armor',
                             'foot_armor', 'trimmable_armor']}
tag_of = {'sword': 'swords', 'pickaxe': 'pickaxes', 'axe': 'axes', 'shovel': 'shovels', 'hoe': 'hoes',
          'helmet': 'head_armor', 'chestplate': 'chest_armor', 'leggings': 'leg_armor', 'boots': 'foot_armor'}

for mid, (kind, color, en_name, de_name) in MATERIALS.items():
    main = f'{NS}:{mid}' if kind == 'gem' else f'{NS}:{mid}_ingot'
    repair = f'{NS}:enriched_uranium' if kind == 'tools' else main
    hard = mid in ('mythril', 'voidium')

    if kind in ('gem', 'metal'):
        save(recolor(diamond_ore, color, 0.25), 'block', f'{mid}_ore')
        save(recolor(deepslate_diamond_ore, color, 0.25), 'block', f'deepslate_{mid}_ore')
        for ore in (f'{mid}_ore', f'deepslate_{mid}_ore'):
            block_cube(ore)
            (needs_diamond if hard else needs_iron).append(f'{NS}:{ore}')
        en[f'block.{NS}.{mid}_ore'] = f'{en_name} Ore'
        de[f'block.{NS}.{mid}_ore'] = f'{de_name}erz'
        en[f'block.{NS}.deepslate_{mid}_ore'] = f'Deepslate {en_name} Ore'
        de[f'block.{NS}.deepslate_{mid}_ore'] = f'Tiefenschiefer-{de_name}erz'
    if kind == 'end':
        save(overlay_gems(vanilla('block/end_stone'), diamond_ore, color), 'block', f'{mid}_ore')
        block_cube(f'{mid}_ore')
        needs_diamond.append(f'{NS}:{mid}_ore')
        en[f'block.{NS}.{mid}_ore'] = f'{en_name} Ore'
        de[f'block.{NS}.{mid}_ore'] = f'{de_name}erz'

    if kind == 'gem':
        save(recolor(vanilla('item/diamond'), color), 'item', mid)
        item_model(mid)
        en[f'item.{NS}.{mid}'] = en_name
        de[f'item.{NS}.{mid}'] = de_name
        save(recolor(vanilla('block/diamond_block'), color), 'block', f'{mid}_block')
        loot_ore(f'{mid}_ore', main)
        loot_ore(f'deepslate_{mid}_ore', main)
        cook(mid, f'#{NS}:{mid}_ores', main, 1.0)
    elif kind in ('metal', 'end'):
        save(tint(vanilla('item/raw_iron'), color), 'item', f'raw_{mid}')
        save(tint(vanilla('item/iron_ingot'), color), 'item', f'{mid}_ingot')
        save(tint(vanilla('block/iron_block'), color, gain=1.15), 'block', f'{mid}_block')
        save(tint(vanilla('block/raw_iron_block'), color), 'block', f'raw_{mid}_block')
        item_model(f'raw_{mid}')
        item_model(f'{mid}_ingot')
        block_cube(f'raw_{mid}_block')
        loot_self(f'raw_{mid}_block')
        (needs_diamond if hard else needs_iron).append(f'{NS}:raw_{mid}_block')
        en[f'item.{NS}.raw_{mid}'] = f'Raw {en_name}'
        de[f'item.{NS}.raw_{mid}'] = f'Roh-{de_name}'
        en[f'item.{NS}.{mid}_ingot'] = f'{en_name} Ingot'
        de[f'item.{NS}.{mid}_ingot'] = f'{de_name}barren'
        en[f'block.{NS}.raw_{mid}_block'] = f'Block of Raw {en_name}'
        de[f'block.{NS}.raw_{mid}_block'] = f'Roh-{de_name}block'
        loot_ore(f'{mid}_ore', f'{NS}:raw_{mid}')
        if kind == 'metal':
            loot_ore(f'deepslate_{mid}_ore', f'{NS}:raw_{mid}')
        cook(f'{mid}_ingot_from_ore', f'#{NS}:{mid}_ores', main, 0.9)
        cook(f'{mid}_ingot', f'{NS}:raw_{mid}', main, 0.9)
        shaped(f'raw_{mid}_block', ['XXX', 'XXX', 'XXX'], {'X': f'{NS}:raw_{mid}'})
        shapeless(f'raw_{mid}_from_block', [f'{NS}:raw_{mid}_block'], f'{NS}:raw_{mid}', 9)
    if kind != 'tools':
        block_cube(f'{mid}_block')
        loot_self(f'{mid}_block')
        (needs_diamond if hard else needs_iron).append(f'{NS}:{mid}_block')
        en[f'block.{NS}.{mid}_block'] = f'Block of {en_name}'
        de[f'block.{NS}.{mid}_block'] = f'{de_name}block'
        shaped(f'{mid}_block', ['XXX', 'XXX', 'XXX'], {'X': main})
        shapeless(f'{mid}_from_block', [f'{NS}:{mid}_block'], main, 9)
        ores = [f'{NS}:{mid}_ore'] + ([f'{NS}:deepslate_{mid}_ore'] if kind in ('gem', 'metal') else [])
        write(os.path.join(ROOT, 'data', NS, 'tags', 'item', f'{mid}_ores.json'), {'values': ores})

    for t in TOOLS:
        save(recolor(vanilla(f'item/diamond_{t}'), color, 0.2, 0.40, 0.62), 'item', f'{mid}_{t}')
        item_model(f'{mid}_{t}', handheld=True)
        item_tags[tag_of[t]].append(f'{NS}:{mid}_{t}')
        en[f'item.{NS}.{mid}_{t}'] = f'{en_name} {TOOL_EN[t]}'
        de[f'item.{NS}.{mid}_{t}'] = f'{de_name}{TOOL_DE[t]}'
    for a in ARMOR:
        save(recolor(vanilla(f'item/diamond_{a}'), color, 0.2, 0.40, 0.62), 'item', f'{mid}_{a}')
        item_model(f'{mid}_{a}')
        item_tags[tag_of[a]].append(f'{NS}:{mid}_{a}')
        item_tags['trimmable_armor'].append(f'{NS}:{mid}_{a}')
        en[f'item.{NS}.{mid}_{a}'] = f'{en_name} {TOOL_EN[a]}'
        de[f'item.{NS}.{mid}_{a}'] = f'{de_name}{TOOL_DE[a]}'
    for layer in ('1', '2'):
        save(recolor(vanilla(f'models/armor/diamond_layer_{layer}'), color, 0.2, 0.40, 0.62), 'models/armor', f'{mid}_layer_{layer}')

    s, st = 'minecraft:stick', repair
    shaped(f'{mid}_sword', [' X ', ' X ', ' S '], {'X': st, 'S': s})
    shaped(f'{mid}_pickaxe', ['XXX', ' S ', ' S '], {'X': st, 'S': s})
    shaped(f'{mid}_axe', ['XX ', 'XS ', ' S '], {'X': st, 'S': s})
    shaped(f'{mid}_shovel', [' X ', ' S ', ' S '], {'X': st, 'S': s})
    shaped(f'{mid}_hoe', ['XX ', ' S ', ' S '], {'X': st, 'S': s})
    shaped(f'{mid}_helmet', ['XXX', 'X X'], {'X': st})
    shaped(f'{mid}_chestplate', ['X X', 'XXX', 'XXX'], {'X': st})
    shaped(f'{mid}_leggings', ['XXX', 'X X', 'X X'], {'X': st})
    shaped(f'{mid}_boots', ['X X', 'X X'], {'X': st})

# ---------------------------------------------------------------- decorative blocks
DECOR = {
    # name: (texture, english, german)
    'void_stone': (lambda: tint(vanilla('block/end_stone'), '#8a5ac0', gain=1.0), 'Void Stone', 'Leerenstein'),
    'void_stone_bricks': (lambda: tint(vanilla('block/end_stone_bricks'), '#8a5ac0', gain=1.0), 'Void Stone Bricks', 'Leerenstein-Ziegel'),
    'polished_void_stone': (lambda: tint(vanilla('block/smooth_stone'), '#9a6ad0', gain=1.0), 'Polished Void Stone', 'Polierter Leerenstein'),
    'void_crystal': (lambda: recolor(vanilla('block/amethyst_block'), '#c040ff', 0.1), 'Void Crystal', 'Leerenkristall'),
    'ruby_bricks': (lambda: tint(vanilla('block/stone_bricks'), '#e0284a'), 'Ruby Bricks', 'Rubinziegel'),
    'sapphire_bricks': (lambda: tint(vanilla('block/stone_bricks'), '#2f5fe0'), 'Sapphire Bricks', 'Saphirziegel'),
    'titanium_plating': (lambda: tint(vanilla('block/iron_block'), '#c8d8e8', gain=1.1), 'Titanium Plating', 'Titanplatten'),
    'cobalt_bricks': (lambda: tint(vanilla('block/deepslate_bricks'), '#3f8fe0', gain=1.6), 'Cobalt Bricks', 'Kobaltziegel'),
}
for name, (tex, e, g) in DECOR.items():
    save(tex(), 'block', name)
    block_cube(name)
    en[f'block.{NS}.{name}'] = e
    de[f'block.{NS}.{name}'] = g
    if name != 'void_crystal':
        loot_self(name)
write(os.path.join(DATA, 'loot_table', 'blocks', 'void_crystal.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
        {'type': 'minecraft:item', 'name': f'{NS}:void_crystal', 'conditions': SILK},
        {'type': 'minecraft:item', 'name': f'{NS}:void_crystal_shard', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'add': False},
            {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'}]}]}]}],
    'random_sequence': f'{NS}:blocks/void_crystal'})
shaped('void_stone_bricks', ['XX', 'XX'], {'X': f'{NS}:void_stone'}, count=4)
shaped('polished_void_stone', ['XX', 'XX'], {'X': f'{NS}:void_stone_bricks'}, count=4)
shaped('void_crystal', ['XX', 'XX'], {'X': f'{NS}:void_crystal_shard'})
shaped('ruby_bricks', ['SSS', 'SXS', 'SSS'], {'S': 'minecraft:stone_bricks', 'X': f'{NS}:ruby'}, count=8)
shaped('sapphire_bricks', ['SSS', 'SXS', 'SSS'], {'S': 'minecraft:stone_bricks', 'X': f'{NS}:sapphire'}, count=8)
shaped('titanium_plating', ['XX', 'XX'], {'X': f'{NS}:titanium_ingot'}, count=4)
shaped('cobalt_bricks', ['SSS', 'SXS', 'SSS'], {'S': 'minecraft:deepslate_bricks', 'X': f'{NS}:cobalt_ingot'}, count=8)
needs_iron.extend([f'{NS}:ruby_bricks', f'{NS}:sapphire_bricks', f'{NS}:titanium_plating', f'{NS}:cobalt_bricks'])

# ---------------------------------------------------------------- drops and food
ITEMS = {
    'void_pearl': (lambda: recolor(vanilla('item/ender_pearl'), '#9a40ff', 0.1), 'Void Pearl', 'Leerenperle', 'Thrown like an Ender Pearl.', 'Wird wie eine Enderperle geworfen.'),
    'crystal_silk': (lambda: tint(vanilla('item/string'), '#80f0ff'), 'Crystal Silk', 'Kristallseide', 'Dropped by Crystal Spiders. Craft into string.', 'Von Kristallspinnen. Wird zu Faden verarbeitet.'),
    'uranium_flesh': (lambda: recolor(vanilla('item/rotten_flesh'), '#7dff3a', 0.15), 'Uranium Flesh', 'Uranfleisch', 'Radioactive. Poisons you.', 'Radioaktiv. Vergiftet dich.'),
    'magma_bone': (lambda: tint(vanilla('item/bone'), '#ff8a30'), 'Magma Bone', 'Magmaknochen', 'Dropped by Magma Skeletons. Craft into Blaze Powder.', 'Von Magmaskeletten. Wird zu Lohenstaub.'),
    'charged_gunpowder': (lambda: tint(vanilla('item/gunpowder'), '#ff3a3a', gain=1.6), 'Charged Gunpowder', 'Geladenes Schwarzpulver', 'Dropped by Redstone Creepers. Makes TNT with sand.', 'Von Redstone-Creepern. Macht mit Sand TNT.'),
    'ember_pork': (lambda: recolor(vanilla('item/porkchop'), '#ff8a30', 0.15), 'Ember Pork', 'Glutfleisch', 'Raw meat from Ember Pigs.', 'Rohes Fleisch vom Glutschwein.'),
    'cooked_ember_pork': (lambda: recolor(vanilla('item/cooked_porkchop'), '#ff7a20', 0.15), 'Cooked Ember Pork', 'Gebratenes Glutfleisch', 'Filling, gives Fire Resistance.', 'Macht satt und gibt Feuerresistenz.'),
    'ruby_apple': (lambda: recolor(vanilla('item/golden_apple'), '#ff2a4a', 0.2), 'Ruby Apple', 'Rubinapfel', 'Strength II and Regeneration II.', 'Stärke II und Regeneration II.'),
    'mythril_apple': (lambda: recolor(vanilla('item/golden_apple'), '#3ee0c8', 0.2), 'Mythril Apple', 'Mithrilapfel', 'Absorption IV and Resistance II.', 'Absorption IV und Resistenz II.'),
    'void_crystal_shard': (lambda: recolor(vanilla('item/amethyst_shard'), '#c040ff', 0.1), 'Void Crystal Shard', 'Leerenkristall-Splitter', 'Four make a Void Crystal.', 'Vier ergeben einen Leerenkristall.'),
}
for name, (tex, e, g, de_e, de_g) in ITEMS.items():
    save(tex(), 'item', name)
    item_model(name)
    en[f'item.{NS}.{name}'] = e
    de[f'item.{NS}.{name}'] = g
    en[f'item.{NS}.{name}.desc'] = de_e
    de[f'item.{NS}.{name}.desc'] = de_g
shapeless('string_from_crystal_silk', [f'{NS}:crystal_silk'], 'minecraft:string', 4)
shapeless('blaze_powder_from_magma_bone', [f'{NS}:magma_bone'], 'minecraft:blaze_powder', 3)
shapeless('tnt_from_charged_gunpowder', [f'{NS}:charged_gunpowder', f'{NS}:charged_gunpowder', '#minecraft:sand', '#minecraft:sand'], 'minecraft:tnt', 2)
shaped('ruby_apple', ['XXX', 'XAX', 'XXX'], {'X': f'{NS}:ruby', 'A': 'minecraft:apple'})
shaped('mythril_apple', ['XXX', 'XAX', 'XXX'], {'X': f'{NS}:mythril_ingot', 'A': 'minecraft:golden_apple'})
cook('cooked_ember_pork', f'{NS}:ember_pork', f'{NS}:cooked_ember_pork', 0.35, blasting=False, smoking=True)
for mid, (_, _, e, g) in MATERIALS.items():
    for kind_key in ([mid] if MATERIALS[mid][0] == 'gem' else [f'raw_{mid}', f'{mid}_ingot'] if MATERIALS[mid][0] != 'tools' else []):
        en[f'item.{NS}.{kind_key}.desc'] = f'{e} material for tools and armor.'
        de[f'item.{NS}.{kind_key}.desc'] = f'{g}-Material für Werkzeuge und Rüstung.'

# ---------------------------------------------------------------- mobs
MOBS = {
    # name: (vanilla texture, recolor function, drop, english, german)
    'uranium_zombie': ('entity/zombie/zombie', lambda i: recolor(i, '#7dff3a', 0.12), 'uranium_flesh', 'Uranium Zombie', 'Uran-Zombie'),
    'redstone_creeper': ('entity/creeper/creeper', lambda i: recolor(i, '#e02a1a', 0.1), 'charged_gunpowder', 'Redstone Creeper', 'Redstone-Creeper'),
    'crystal_spider': ('entity/spider/spider', lambda i: tint(i, '#60e8ff', 0.75, 1.9), 'crystal_silk', 'Crystal Spider', 'Kristallspinne'),
    'magma_skeleton': ('entity/skeleton/skeleton', lambda i: tint(i, '#ff8a30', 0.8), 'magma_bone', 'Magma Skeleton', 'Magmaskelett'),
    'ruby_slime': ('entity/slime/slime', lambda i: recolor(i, '#e0284a', 0.1), None, 'Ruby Slime', 'Rubinschleim'),
    'void_enderman': ('entity/enderman/enderman', lambda i: tint(i, '#b060ff', 0.9, 3.0), 'void_pearl', 'Void Enderman', 'Leeren-Enderman'),
    'ember_pig': ('entity/pig/pig', lambda i: recolor(i, '#ff7a20', 0.1), 'ember_pork', 'Ember Pig', 'Glutschwein'),
    'redstone_golem': ('entity/iron_golem/iron_golem', lambda i: tint(i, '#d84040', 0.75), None, 'Redstone Golem', 'Redstone-Golem'),
}
for name, (src, fn, drop, e, g) in MOBS.items():
    save(fn(vanilla(src)), 'entity', name)
    en[f'entity.{NS}.{name}'] = e
    de[f'entity.{NS}.{name}'] = g
    en[f'item.{NS}.{name}_spawn_egg'] = f'{e} Spawn Egg'
    de[f'item.{NS}.{name}_spawn_egg'] = f'{g}-Spawn-Ei'
    write(os.path.join(ASSETS, 'models', 'item', f'{name}_spawn_egg.json'), {'parent': 'minecraft:item/template_spawn_egg'})
    pools = []
    if drop:
        pools.append({'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{drop}', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 2}, 'add': False},
            {'function': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}]}]})
    if name == 'ruby_slime':
        pools.append({'rolls': 1, 'bonus_rolls': 0, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.35}],
                      'entries': [{'type': 'minecraft:item', 'name': f'{NS}:ruby'}]})
    if name == 'redstone_golem':
        pools.append({'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:redstone', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 4, 'max': 9}, 'add': False}]}]})
        pools.append({'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:ruby', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 3}, 'add': False}]}]})
    if name == 'magma_skeleton':
        pools.append({'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:arrow', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 2}, 'add': False}]}]})
    write(os.path.join(DATA, 'loot_table', 'entities', name + '.json'), {'type': 'minecraft:entity', 'pools': pools,
                                                                        'random_sequence': f'{NS}:entities/{name}'})

# ---------------------------------------------------------------- worldgen: ores
def ore_feature(name, targets, size, count, height, discard=0.0):
    write(os.path.join(DATA, 'worldgen', 'configured_feature', name + '.json'), {
        'type': 'minecraft:ore', 'config': {'size': size, 'discard_chance_on_air_exposure': discard, 'targets': targets}})
    write(os.path.join(DATA, 'worldgen', 'placed_feature', name + '.json'), {
        'feature': f'{NS}:{name}',
        'placement': [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:height_range', 'height': height}, {'type': 'minecraft:biome'}]})


def overworld_targets(mid):
    return [{'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'},
             'state': {'Name': f'{NS}:{mid}_ore'}},
            {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'},
             'state': {'Name': f'{NS}:deepslate_{mid}_ore'}}]


def trapezoid(lo, hi):
    return {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}


ore_feature('ore_ruby', overworld_targets('ruby'), 6, 5, trapezoid(-64, 64), 0.3)
ore_feature('ore_sapphire', overworld_targets('sapphire'), 6, 5, trapezoid(-64, 64), 0.3)
ore_feature('ore_titanium', overworld_targets('titanium'), 8, 8, uniform(-32, 96))
ore_feature('ore_cobalt', overworld_targets('cobalt'), 7, 6, uniform(-64, 32))
ore_feature('ore_mythril', overworld_targets('mythril'), 5, 3, trapezoid(-64, -16), 0.5)
ore_feature('ore_voidium', [{'target': {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:end_stone'},
                             'state': {'Name': f'{NS}:voidium_ore'}},
                            {'target': {'predicate_type': 'minecraft:block_match', 'block': f'{NS}:void_stone'},
                             'state': {'Name': f'{NS}:voidium_ore'}}], 6, 10, uniform(0, 100))
# extra ore in "its" biome
ore_feature('ore_ruby_rich', overworld_targets('ruby'), 8, 10, uniform(0, 100))
ore_feature('ore_sapphire_rich', overworld_targets('sapphire'), 8, 10, uniform(0, 100))
ore_feature('ore_titanium_rich', overworld_targets('titanium'), 10, 14, uniform(40, 200))


def biome_modifier(name, biomes, features, step):
    write(os.path.join(ROOT, 'data', NS, 'forge', 'biome_modifier', name + '.json'), {
        'type': 'forge:add_features', 'biomes': biomes, 'features': features, 'step': step})


biome_modifier('overworld_ores', '#minecraft:is_overworld',
               [f'{NS}:ore_ruby', f'{NS}:ore_sapphire', f'{NS}:ore_titanium', f'{NS}:ore_cobalt', f'{NS}:ore_mythril'], 'underground_ores')
biome_modifier('end_ores', '#minecraft:is_end', [f'{NS}:ore_voidium'], 'underground_ores')

# ---------------------------------------------------------------- worldgen: biome decorations
def placed(name, configured, placement):
    write(os.path.join(DATA, 'worldgen', 'configured_feature', name + '.json'), configured)
    write(os.path.join(DATA, 'worldgen', 'placed_feature', name + '.json'), {'feature': f'{NS}:{name}', 'placement': placement})


def surface_placement(count, below):
    return [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING_NO_LEAVES'},
            {'type': 'minecraft:block_predicate_filter', 'predicate': {
                'type': 'minecraft:matching_blocks', 'offset': [0, -1, 0], 'blocks': below}},
            {'type': 'minecraft:biome'}]


def rock(state):
    return {'type': 'minecraft:forest_rock', 'config': {'state': {'Name': state}}}


def simple(state, props=None):
    s = {'Name': state}
    if props:
        s['Properties'] = props
    return {'type': 'minecraft:simple_block', 'config': {'to_place': {'type': 'minecraft:simple_state_provider', 'state': s}}}


placed('redstone_fields_boulder', rock('minecraft:redstone_ore'), surface_placement(2, ['minecraft:grass_block', 'minecraft:dirt']))
placed('crystal_forest_crystals', simple('minecraft:amethyst_cluster', {'facing': 'up', 'waterlogged': 'false'}),
       surface_placement(8, ['minecraft:grass_block']))
placed('crystal_forest_rock', rock(f'{NS}:sapphire_bricks'), surface_placement(1, ['minecraft:grass_block', 'minecraft:dirt']))
placed('titan_highlands_rock', rock(f'{NS}:raw_titanium_block'), surface_placement(2, ['minecraft:grass_block', 'minecraft:stone', 'minecraft:dirt']))
placed('void_wastes_crystals', simple(f'{NS}:void_crystal'), surface_placement(5, [f'{NS}:void_stone', 'minecraft:end_stone']))
placed('crystal_spires_pillars', {'type': 'minecraft:block_column', 'config': {
    'direction': 'up', 'prioritize_tip': False,
    'allowed_placement': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'},
    'layers': [{'height': {'type': 'minecraft:uniform', 'min_inclusive': 3, 'max_inclusive': 10},
                'provider': {'type': 'minecraft:simple_state_provider', 'state': {'Name': f'{NS}:void_crystal'}}}]}},
    surface_placement(4, ['minecraft:end_stone']))

biome_modifier('redstone_fields_extras', f'{NS}:redstone_fields', [f'{NS}:redstone_fields_boulder'], 'local_modifications')
biome_modifier('redstone_fields_ores', f'{NS}:redstone_fields', [f'{NS}:ore_ruby_rich'], 'underground_decoration')
biome_modifier('crystal_forest_extras', f'{NS}:crystal_forest', [f'{NS}:crystal_forest_rock', f'{NS}:crystal_forest_crystals'], 'local_modifications')
biome_modifier('crystal_forest_ores', f'{NS}:crystal_forest', [f'{NS}:ore_sapphire_rich'], 'underground_decoration')
biome_modifier('titan_highlands_extras', f'{NS}:titan_highlands', [f'{NS}:titan_highlands_rock'], 'local_modifications')
biome_modifier('titan_highlands_ores', f'{NS}:titan_highlands', [f'{NS}:ore_titanium_rich'], 'underground_decoration')
biome_modifier('void_wastes_extras', f'{NS}:void_wastes', [f'{NS}:void_wastes_crystals'], 'surface_structures')
biome_modifier('crystal_spires_extras', f'{NS}:crystal_spires', [f'{NS}:crystal_spires_pillars'], 'surface_structures')

# ---------------------------------------------------------------- biomes
def spawn(entity, weight, lo, hi):
    return {'type': entity, 'weight': weight, 'minCount': lo, 'maxCount': hi}


def make_biome(name, base, effects, extra_spawns, en_name, de_name, temperature=None):
    biome = vanilla_json(f'data/minecraft/worldgen/biome/{base}.json')
    biome['effects'].update(effects)
    for category, entries in extra_spawns.items():
        biome['spawners'].setdefault(category, [])
        biome['spawners'][category] = biome['spawners'][category] + entries
    if temperature is not None:
        biome['temperature'] = temperature
    write(os.path.join(DATA, 'worldgen', 'biome', name + '.json'), biome)
    en[f'biome.{NS}.{name}'] = en_name
    de[f'biome.{NS}.{name}'] = de_name


def c(hexcolor):
    return int(hexcolor.lstrip('#'), 16)


make_biome('redstone_fields', 'plains',
           {'grass_color': c('#c8503a'), 'foliage_color': c('#b04030'), 'water_color': c('#d05050'), 'water_fog_color': c('#501010')},
           {'creature': [spawn(f'{NS}:ember_pig', 10, 2, 4), spawn(f'{NS}:redstone_golem', 2, 1, 1)],
            'monster': [spawn(f'{NS}:redstone_creeper', 60, 1, 2), spawn(f'{NS}:ruby_slime', 30, 1, 3), spawn(f'{NS}:uranium_zombie', 30, 1, 2)]},
           'Redstone Fields', 'Redstone-Felder')
make_biome('crystal_forest', 'forest',
           {'grass_color': c('#4ad0c8'), 'foliage_color': c('#3a9ae0'), 'water_color': c('#40e0ff'), 'water_fog_color': c('#10405a'),
            'sky_color': c('#9ad8ff')},
           {'monster': [spawn(f'{NS}:crystal_spider', 60, 1, 2)]},
           'Crystal Forest', 'Kristallwald')
make_biome('titan_highlands', 'windswept_hills',
           {'grass_color': c('#8a9a8a'), 'foliage_color': c('#7a8a7a'), 'sky_color': c('#b8c8d8')},
           {'monster': [spawn(f'{NS}:magma_skeleton', 60, 1, 2), spawn(f'{NS}:uranium_zombie', 30, 1, 2)]},
           'Titan Highlands', 'Titan-Hochland')
make_biome('void_wastes', 'end_midlands',
           {'fog_color': c('#1a0a2a'), 'sky_color': c('#000000'), 'water_color': c('#6a2ab0'), 'water_fog_color': c('#1a0a2a')},
           {'monster': [spawn(f'{NS}:void_enderman', 20, 1, 2), spawn(f'{NS}:crystal_spider', 10, 1, 2)]},
           'Void Wastes', 'Leerenwüste')
make_biome('crystal_spires', 'end_highlands',
           {'fog_color': c('#2a0a3a'), 'sky_color': c('#000000'), 'water_color': c('#c040ff'), 'water_fog_color': c('#2a0a3a')},
           {'monster': [spawn(f'{NS}:void_enderman', 15, 1, 2), spawn(f'{NS}:crystal_spider', 15, 1, 3)]},
           'Crystal Spires', 'Kristalltürme')

write(os.path.join(MC_DATA, 'tags', 'worldgen', 'biome', 'is_overworld.json'),
      {'replace': False, 'values': [f'{NS}:redstone_fields', f'{NS}:crystal_forest', f'{NS}:titan_highlands']})
write(os.path.join(MC_DATA, 'tags', 'worldgen', 'biome', 'is_forest.json'), {'replace': False, 'values': [f'{NS}:crystal_forest']})
write(os.path.join(MC_DATA, 'tags', 'worldgen', 'biome', 'is_hill.json'), {'replace': False, 'values': [f'{NS}:titan_highlands']})
write(os.path.join(MC_DATA, 'tags', 'worldgen', 'biome', 'is_end.json'),
      {'replace': False, 'values': [f'{NS}:void_wastes', f'{NS}:crystal_spires']})

# ---------------------------------------------------------------- tags
def merge_tag(path, values):
    existing = {'replace': False, 'values': []}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            existing = json.load(f)
    for v in values:
        if v not in existing['values']:
            existing['values'].append(v)
    write(path, existing)


merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'mineable', 'pickaxe.json'), pickaxe_blocks)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'needs_iron_tool.json'), needs_iron)
merge_tag(os.path.join(MC_DATA, 'tags', 'block', 'needs_diamond_tool.json'), needs_diamond)
for tag, values in item_tags.items():
    merge_tag(os.path.join(MC_DATA, 'tags', 'item', tag + '.json'), values)

# ---------------------------------------------------------------- drill texts (updated for the bigger drill)
en[f'item.{NS}.drill.desc'] = 'Place it, right click to get in. W drives and bores a 5x5 tunnel, it turns where you look. Look down/up to dig down/up. Sneak to get out, hit it to pick it up.'
de[f'item.{NS}.drill.desc'] = 'Aufstellen, Rechtsklick zum Einsteigen. W fährt und bohrt einen 5x5-Tunnel, er fährt, wohin du schaust. Nach unten/oben schauen bohrt runter/hoch. Schleichen zum Aussteigen, schlagen zum Aufheben.'

# ---------------------------------------------------------------- tooltips for the new blocks
WHERE = {'ruby': ('everywhere below Y 64, lots in Redstone Fields', 'überall unter Y 64, viel in den Redstone-Feldern'),
         'sapphire': ('everywhere below Y 64, lots in Crystal Forests', 'überall unter Y 64, viel im Kristallwald'),
         'titanium': ('between Y -32 and 96, lots in Titan Highlands', 'zwischen Y -32 und 96, viel im Titan-Hochland'),
         'cobalt': ('below Y 32', 'unter Y 32'),
         'mythril': ('deep down, below Y -16', 'tief unten, unter Y -16'),
         'voidium': ('in the End', 'im End')}
for full in pickaxe_blocks:
    name = full.split(':')[1]
    key = f'block.{NS}.{name}.desc'
    if key in en:
        continue
    mat = next((m for m in WHERE if m in name), None)
    if name.endswith('_ore') and mat:
        en[key] = f'Found {WHERE[mat][0]}.'
        de[key] = f'Kommt {WHERE[mat][1]} vor.'
    elif name.startswith('raw_') or name.endswith('_block'):
        en[key] = 'Storage block, 9 of the item.'
        de[key] = 'Speicherblock aus 9 Stück.'
    elif name == 'void_crystal':
        en[key] = 'Glowing crystal from the Void Wastes and Crystal Spires.'
        de[key] = 'Leuchtender Kristall aus der Leerenwüste und den Kristalltürmen.'
    else:
        en[key] = 'Decorative building block.'
        de[key] = 'Dekorativer Baublock.'

# ---------------------------------------------------------------- lang (merged into the files gen_data.js wrote)
for lang, entries in (('en_us', en), ('de_de', de)):
    path = os.path.join(ASSETS, 'lang', lang + '.json')
    with open(path, encoding='utf-8') as f:
        current = json.load(f)
    current.update(entries)
    write(path, current)

print('content generated:', len(en), 'lang entries')
