// Generates blockstates, models, item definitions, loot tables, recipes, tags and lang files.
// Run: node tools/gen_data.js
const fs = require('fs');
const path = require('path');

const NS = 'redstoneplus';
const RES = path.join(__dirname, '..', 'src', 'main', 'resources');
const ASSETS = path.join(RES, 'assets', NS);
const DATA = path.join(RES, 'data', NS);

function write(file, obj) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
}
const blockModel = (name, obj) => write(path.join(ASSETS, 'models', 'block', name + '.json'), obj);
const blockstate = (name, obj) => write(path.join(ASSETS, 'blockstates', name + '.json'), obj);
// 1.21.1: a block item model simply inherits the block model
const itemDef = (name, model) => write(path.join(ASSETS, 'models', 'item', name + '.json'), { parent: model });
const t = (n) => `${NS}:block/${n}`;

// name, kind, english, german
const { BLOCKS, ITEMS } = require('./spec');

const H4 = { north: 0, east: 90, south: 180, west: 270 };
const R6 = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 }, up: { x: 270 }, down: { x: 90 } };
const FULL = [0, 0, 16, 16];

// One cuboid. tex: a texture key for all faces, or { north, south, east, west, up, down, other }.
function box(from, to, tex) {
  const faces = {};
  for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
    const key = typeof tex === 'string' ? tex : (tex[f] || tex.other);
    faces[f] = { uv: FULL, texture: '#' + key };
  }
  return { from, to, faces };
}
const body = (from, to, front = 'side') => box(from, to, { north: front, up: 'top', down: 'bottom', other: 'side' });

// Model shape per block type. North is the front, up is the top.
const SHAPES = {
  machine: [body([0, 0, 0], [16, 16, 16], 'front'), box([4, 4, -1], [12, 12, 0], 'detail')],
  rod: [body([0, 0, 0], [16, 16, 16], 'front'), box([7, 7, -6], [9, 9, 0], 'detail'), box([6, 6, -8], [10, 10, -6], 'detail')],
  fan: [body([0, 0, 0], [16, 16, 16], 'front'), box([1, 7, -1], [15, 9, 0], 'detail'), box([7, 1, -1], [9, 15, 0], 'detail')],
  cannon: [
    box([0, 0, 0], [16, 5, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([3, 5, 3], [13, 8, 13], 'side'),
    box([4, 8, -3], [12, 15, 15], { north: 'front', other: 'detail' }),
  ],
  teleporter: [
    box([0, 0, 0], [16, 11, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([0, 11, 0], [3, 16, 3], 'detail'), box([13, 11, 0], [16, 16, 3], 'detail'),
    box([0, 11, 13], [3, 16, 16], 'detail'), box([13, 11, 13], [16, 16, 16], 'detail'),
    box([4, 11, 4], [12, 12, 12], 'detail'),
  ],
  gate: [
    box([0, 0, 0], [16, 2, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([2, 2, 12], [4, 5, 14], 'detail'), box([12, 2, 12], [14, 5, 14], 'detail'),
  ],
  gate_core: [
    box([0, 0, 0], [16, 2, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([5, 2, 9], [11, 6, 15], 'detail'),
  ],
  antenna: [
    box([1, 0, 1], [15, 9, 15], { up: 'top', down: 'bottom', other: 'side' }),
    box([7, 9, 7], [9, 14, 9], 'side'), box([6, 13, 6], [10, 16, 10], 'detail'),
  ],
  sensor: [
    box([0, 0, 0], [16, 10, 16], { down: 'bottom', other: 'side' }),
    box([3, 10, 3], [13, 14, 13], { up: 'top', other: 'side' }), box([6, 14, 6], [10, 15, 10], 'detail'),
  ],
  pad: [box([0, 0, 0], [16, 4, 16], { up: 'bottom', down: 'bottom', other: 'side' }), box([1, 4, 1], [15, 5, 15], { up: 'top', other: 'detail' })],
  magnet: [
    box([0, 0, 0], [16, 8, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([2, 8, 5], [6, 13, 11], 'side'), box([10, 8, 5], [14, 13, 11], 'side'),
    box([2, 13, 5], [6, 16, 11], 'detail'), box([10, 13, 5], [14, 16, 11], 'detail'),
  ],
  spikes: [
    box([0, 0, 0], [16, 12, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([2, 12, 2], [5, 16, 5], 'detail'), box([11, 12, 2], [14, 16, 5], 'detail'),
    box([2, 12, 11], [5, 16, 14], 'detail'), box([11, 12, 11], [14, 16, 14], 'detail'),
    box([6.5, 12, 6.5], [9.5, 16, 9.5], 'detail'),
  ],
  conveyor: [
    box([0, 0, 0], [16, 10, 16], { up: 'top', down: 'bottom', other: 'side' }),
    box([0, 10, 0], [1, 11, 16], 'detail'), box([15, 10, 0], [16, 11, 16], 'detail'),
  ],
  nuke: [
    box([2, 0, 2], [14, 13, 14], { up: 'top', down: 'bottom', other: 'side' }), box([4, 13, 4], [12, 16, 12], 'detail'),
    box([0, 0, 7], [2, 6, 9], 'detail'), box([14, 0, 7], [16, 6, 9], 'detail'),
    box([7, 0, 0], [9, 6, 2], 'detail'), box([7, 0, 14], [9, 6, 16], 'detail'),
  ],
  siren: [
    box([1, 0, 1], [15, 7, 15], { up: 'top', down: 'bottom', other: 'side' }),
    box([3, 7, 3], [13, 13, 13], 'detail'), box([5, 13, 5], [11, 15, 11], { up: 'top', other: 'side' }),
  ],
  ring: [
    box([0, 0, 0], [16, 6, 16], { up: 'top', down: 'bottom', other: 'side' }), box([6, 6, 6], [10, 9, 10], 'detail'),
    box([2, 12, 2], [14, 13, 4], 'detail'), box([2, 12, 12], [14, 13, 14], 'detail'),
    box([2, 12, 4], [4, 13, 12], 'detail'), box([12, 12, 4], [14, 13, 12], 'detail'),
  ],
  mine: [box([1, 0, 1], [15, 1, 15], { up: 'top', down: 'bottom', other: 'side' }), box([6, 1, 6], [10, 3, 10], 'detail')],
};

function shapedModel(name, shape, on) {
  const s = on ? '_on' : '';
  return {
    parent: 'minecraft:block/block',
    textures: {
      particle: t(name + '_side'), side: t(name + '_side'), bottom: t(name + '_bottom'),
      top: t(name + '_top' + s), front: t(name + '_front' + s), detail: t(name + '_detail' + s),
    },
    elements: SHAPES[shape],
  };
}

const FACING6 = ['machine', 'rod', 'fan', 'cannon'];
const FACING4 = ['gate', 'gate_core', 'conveyor'];
const POWERED_ONLY = ['teleporter', 'antenna', 'sensor', 'pad', 'magnet', 'spikes', 'siren', 'ring'];

for (const [name, shape] of BLOCKS) {
  let item = t(name);
  if (SHAPES[shape]) {
    const stateless = ['nuke', 'mine'].includes(shape);
    blockModel(name, shapedModel(name, shape, false));
    if (!stateless) blockModel(name + '_on', shapedModel(name, shape, true));
    const variants = {};
    if (FACING6.includes(shape)) {
      for (const [f, rot] of Object.entries(R6)) for (const on of [false, true])
        variants[`facing=${f},powered=${on}`] = { model: t(name + (on ? '_on' : '')), ...rot };
    } else if (FACING4.includes(shape)) {
      for (const [f, y] of Object.entries(H4)) for (const on of [false, true])
        variants[`facing=${f},powered=${on}`] = { model: t(name + (on ? '_on' : '')), ...(y ? { y } : {}) };
    } else if (POWERED_ONLY.includes(shape)) {
      variants['powered=false'] = { model: t(name) };
      variants['powered=true'] = { model: t(name + '_on') };
    } else {
      variants[''] = { model: t(name) };
    }
    blockstate(name, { variants });
  } else if (shape === 'cube') {
    blockModel(name, { parent: 'minecraft:block/cube_bottom_top', textures: { top: t(name + '_top'), side: t(name + '_side'), bottom: t(name + '_bottom') } });
    blockstate(name, { variants: { '': { model: t(name) } } });
  } else if (shape === 'ore') {
    blockModel(name, { parent: 'minecraft:block/cube_all', textures: { all: t(name) } });
    blockstate(name, { variants: { '': { model: t(name) } } });
  } else if (shape === 'lamp') {
    for (const on of [false, true]) blockModel(name + (on ? '_on' : ''), { parent: 'minecraft:block/cube_all', textures: { all: t(name + (on ? '_on' : '')) } });
    blockstate(name, { variants: { 'lit=false': { model: t(name) }, 'lit=true': { model: t(name + '_on') } } });
  } else if (shape === 'phantom') {
    blockModel(name, { parent: 'minecraft:block/cube_bottom_top', textures: { top: t(name + '_top'), side: t(name + '_side'), bottom: t(name + '_bottom') } });
    blockModel(name + '_on', { parent: 'minecraft:block/cube_all', render_type: 'minecraft:cutout', textures: { all: t(name + '_top_on') } });
    blockstate(name, { variants: { 'powered=false': { model: t(name) }, 'powered=true': { model: t(name + '_on') } } });
  } else if (shape === 'display') {
    const variants = {};
    for (let p = 0; p <= 15; p++) {
      blockModel(`${name}_${p}`, { parent: 'minecraft:block/cube_bottom_top', textures: { top: t(`${name}_${p}`), side: t(`${name}_${p}`), bottom: t(name + '_side') } });
      variants[`power=${p}`] = { model: t(`${name}_${p}`) };
    }
    blockstate(name, { variants });
    item = t(`${name}_0`);
  } else if (shape === 'net') {
    blockModel(name, { parent: 'minecraft:block/cross', render_type: 'minecraft:cutout', textures: { cross: t(name) } });
    blockstate(name, { variants: { '': { model: t(name) } } });
    write(path.join(ASSETS, 'models', 'item', name + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: t(name) } });
    item = null;
  } else {
    throw new Error('unknown shape ' + shape);
  }
  if (item) itemDef(name, item);
}

for (const [name] of ITEMS) {
  write(path.join(ASSETS, 'models', 'item', name + '.json'), {
    parent: name === 'redstone_wrench' ? 'minecraft:item/handheld' : 'minecraft:item/generated',
    textures: { layer0: `${NS}:item/${name}` },
  });
}

// ---------- loot tables ----------
for (const [name] of BLOCKS) {
  if (name === 'uranium_ore') continue;
  write(path.join(DATA, 'loot_table', 'blocks', name + '.json'), {
    type: 'minecraft:block',
    pools: [{
      rolls: 1, bonus_rolls: 0,
      entries: [{ type: 'minecraft:item', name: `${NS}:${name}` }],
      conditions: [{ condition: 'minecraft:survives_explosion' }],
    }],
    random_sequence: `${NS}:blocks/${name}`,
  });
}
write(path.join(DATA, 'loot_table', 'blocks', 'uranium_ore.json'), {
  type: 'minecraft:block',
  pools: [{
    rolls: 1, bonus_rolls: 0,
    entries: [{
      type: 'minecraft:alternatives',
      children: [
        {
          type: 'minecraft:item', name: `${NS}:uranium_ore`,
          conditions: [{
            condition: 'minecraft:match_tool',
            predicate: { predicates: { 'minecraft:enchantments': [{ enchantments: 'minecraft:silk_touch', levels: { min: 1 } }] } },
          }],
        },
        {
          type: 'minecraft:item', name: `${NS}:uranium_shard`,
          functions: [
            { function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: 1, max: 3 }, add: false },
            { function: 'minecraft:apply_bonus', enchantment: 'minecraft:fortune', formula: 'minecraft:ore_drops' },
            { function: 'minecraft:explosion_decay' },
          ],
        },
      ],
    }],
  }],
  random_sequence: `${NS}:blocks/uranium_ore`,
});

// ---------- tags ----------
const pickaxe = BLOCKS.filter(([n, k]) => k !== 'net' && !k.startsWith('gate')).map(([n]) => `${NS}:${n}`);
write(path.join(RES, 'data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), { replace: false, values: pickaxe });
write(path.join(RES, 'data', 'minecraft', 'tags', 'block', 'needs_iron_tool.json'), { replace: false, values: [`${NS}:uranium_ore`, `${NS}:uranium_block`] });
write(path.join(RES, 'data', 'minecraft', 'tags', 'block', 'needs_diamond_tool.json'), { replace: false, values: [`${NS}:reinforced_redstone_block`] });

// ---------- recipes ----------
const ing = (id) => ({ item: id });
function shaped(name, pattern, key, count = 1, result = name) {
  key = Object.fromEntries(Object.entries(key).map(([k, v]) => [k, ing(v)]));
  write(path.join(DATA, 'recipe', name + '.json'), {
    type: 'minecraft:crafting_shaped', category: 'redstone', pattern: pattern.map((row) => row.replaceAll('.', ' ')), key, result: { id: `${NS}:${result}`, count },
  });
}
function shapeless(name, ingredients, count = 1, result = name) {
  ingredients = ingredients.map(ing);
  write(path.join(DATA, 'recipe', name + '.json'), {
    type: 'minecraft:crafting_shapeless', category: 'redstone', ingredients, result: { id: `${NS}:${result}`, count },
  });
}
const R = 'minecraft:redstone', S = 'minecraft:stone', C = 'minecraft:cobblestone', I = 'minecraft:iron_ingot';
const U = `${NS}:uranium_shard`, E = `${NS}:enriched_uranium`, Z = `${NS}:redstone_circuit`, T = 'minecraft:redstone_torch';
const P = 'minecraft:repeater', D = 'minecraft:dispenser', O = 'minecraft:observer', Q = 'minecraft:quartz', G = 'minecraft:gold_ingot';
const SS = 'minecraft:smooth_stone_slab';

shaped('redstone_circuit', ['RQR', 'GIG', 'RQR'], { R, Q, G, I }, 2);
shapeless('enriched_uranium', [U, U, U, U, R]);
shaped('uranium_block', ['UUU', 'UUU', 'UUU'], { U });
shapeless('uranium_shard_from_block', [`${NS}:uranium_block`], 9, 'uranium_shard');
write(path.join(DATA, 'recipe', 'uranium_shard_from_smelting.json'), {
  type: 'minecraft:smelting', category: 'misc', ingredient: ing(`${NS}:uranium_ore`),
  result: { id: `${NS}:uranium_shard` }, experience: 1.0, cookingtime: 200,
});
write(path.join(DATA, 'recipe', 'uranium_shard_from_blasting.json'), {
  type: 'minecraft:blasting', category: 'misc', ingredient: ing(`${NS}:uranium_ore`),
  result: { id: `${NS}:uranium_shard` }, experience: 1.0, cookingtime: 100,
});

shaped('tnter', ['CTC', 'CDC', 'CZC'], { C, T: 'minecraft:tnt', D, Z });
shaped('freeze_tnter', ['PIP', 'PXP', 'PZP'], { P: 'minecraft:packed_ice', I: 'minecraft:blue_ice', X: `${NS}:tnter`, Z });
shaped('activator_net', ['S.S', '.A.', 'S.S'], { S: 'minecraft:string', A: 'minecraft:activator_rail' }, 4);
shaped('entity_teleporter', ['OEO', 'ZPZ', 'OUO'], { O: 'minecraft:obsidian', E: 'minecraft:ender_eye', Z, P: 'minecraft:ender_pearl', U: E });
shaped('remote_detonator', ['.T.', 'IZI', 'IUI'], { T, I, Z, U });
shaped('nuclear_repeater', ['EPE', 'SSS'], { E, P, S: SS });
shaped('arrow_shooter', ['CBC', 'CDC', 'CZC'], { C, B: 'minecraft:bow', D, Z });

const gate = (name, key) => shaped(name, ['.X.', 'RZR', 'SSS'], { X: key, R, Z, S: SS });
gate('and_gate', T);
gate('or_gate', R);
gate('xor_gate', Q);
gate('not_gate', 'minecraft:lever');
gate('nand_gate', `${NS}:and_gate`);
gate('nor_gate', `${NS}:or_gate`);
gate('xnor_gate', `${NS}:xor_gate`);
gate('amplifier', 'minecraft:glowstone_dust');
shaped('t_flip_flop', ['.X.', 'RZP', 'SSS'], { X: 'minecraft:lever', R, Z, P, S: SS });
shaped('rs_latch', ['X.Y', 'RZR', 'SSS'], { X: 'minecraft:stone_button', Y: 'minecraft:lever', R, Z, S: SS });
gate('pulse_limiter', O);
shaped('pulse_extender', ['.X.', 'PZP', 'SSS'], { X: 'minecraft:clock', P, Z, S: SS });
shaped('delay_block', ['PPP', 'RZR', 'SSS'], { P, R, Z, S: SS });
gate('randomizer', 'minecraft:ender_pearl');
gate('counter', 'minecraft:comparator');
shaped('sequencer', ['PXP', 'RZR', 'SSS'], { X: 'minecraft:comparator', P, R, Z, S: SS });
shaped('redstone_crossing', ['.R.', 'RZR', 'SSS'], { R, Z, S: SS }, 2);
gate('clock', 'minecraft:clock');
shaped('variable_source', ['RCR', 'RZR', 'RRR'], { R, C: 'minecraft:comparator', Z });
shaped('reinforced_redstone_block', ['OIO', 'IRI', 'OIO'], { O: 'minecraft:obsidian', I, R: 'minecraft:redstone_block' }, 2);
shaped('wireless_transmitter', ['.X.', 'IZI', 'IRI'], { X: 'minecraft:lightning_rod', I, Z, R });
shaped('wireless_receiver', ['.X.', 'IZI', 'ITI'], { X: 'minecraft:lightning_rod', I, Z, T });
shaped('redstone_remote', ['.X.', 'IZI', 'ITI'], { X: 'minecraft:lightning_rod', I, Z, T: 'minecraft:stone_button' });
shaped('player_detector', ['IXI', 'RZR', 'III'], { X: 'minecraft:player_head', I, R, Z });
shaped('player_detector_alt', ['IXI', 'RZR', 'III'], { X: 'minecraft:ender_eye', I, R, Z }, 1, 'player_detector');
shaped('mob_detector', ['IXI', 'RZR', 'III'], { X: 'minecraft:rotten_flesh', I, R, Z });
shaped('weather_sensor', ['GGG', 'RZR', 'III'], { G: 'minecraft:glass', R, Z, I });
shaped('night_sensor', ['GGG', 'RZR', 'III'], { G: 'minecraft:daylight_detector', R, Z, I });
shaped('laser_sensor', ['IXI', 'RZR', 'III'], { X: 'minecraft:tripwire_hook', I, R, Z });
shaped('block_breaker', ['CXC', 'CZC', 'CRC'], { X: 'minecraft:iron_pickaxe', C, Z, R });
shaped('block_placer', ['CXC', 'CZC', 'CRC'], { X: D, C, Z, R });
shaped('lightning_caller', ['IXI', 'IZI', 'IUI'], { X: 'minecraft:lightning_rod', I, Z, U: E });
shaped('launch_pad', ['XXX', 'IZI', 'IRI'], { X: 'minecraft:slime_block', I, Z, R });
shaped('fan', ['IXI', 'XZX', 'IRI'], { X: 'minecraft:iron_bars', I, Z, R });
shaped('item_magnet', ['IXI', 'RZR', 'III'], { X: 'minecraft:hopper', I, R, Z });
shaped('fire_starter', ['CXC', 'CZC', 'CRC'], { X: 'minecraft:flint_and_steel', C, Z, R });
shaped('spike_block', ['XXX', 'IZI', 'IRI'], { X: 'minecraft:pointed_dripstone', I, Z, R });
shaped('phantom_block', ['GXG', 'XZX', 'GXG'], { G: 'minecraft:glass', X: 'minecraft:phantom_membrane', Z }, 4);
shaped('conveyor_belt', ['LLL', 'IZI'], { L: 'minecraft:leather', I, Z }, 4);
shaped('tnt_cannon', ['III', 'TZT', 'IDI'], { I, T: 'minecraft:tnt', Z, D });
shaped('uranium_nuke', ['ETE', 'TUT', 'ETE'], { E, T: 'minecraft:tnt', U: `${NS}:uranium_block` });
shaped('fireball_launcher', ['CXC', 'CZC', 'CDC'], { X: 'minecraft:fire_charge', C, Z, D });
shaped('crop_harvester', ['CXC', 'CZC', 'CRC'], { X: 'minecraft:iron_hoe', C, Z, R });
shaped('alarm_siren', ['IXI', 'IZI', 'IRI'], { X: 'minecraft:note_block', I, Z, R });
shaped('instant_lamp', ['.R.', 'RLR', '.Z.'], { R, L: 'minecraft:redstone_lamp', Z }, 2);
shaped('inverted_lamp', ['.T.', 'TLT', '.Z.'], { T, L: 'minecraft:redstone_lamp', Z }, 2);
shaped('signal_display', ['GGG', 'RZR', 'III'], { G: 'minecraft:black_stained_glass', R, Z, I });
shaped('landmine', ['.P.', 'TZT'], { P: 'minecraft:stone_pressure_plate', T: 'minecraft:tnt', Z }, 2);
shaped('anti_gravity_field', ['EXE', 'IZI', 'IRI'], { E, X: 'minecraft:shulker_shell', I, Z, R });
shaped('redstone_wrench', ['I.I', '.Z.', '.I.'], { I, Z });
shaped('multimeter', ['GXG', 'RZR', 'GIG'], { G, X: 'minecraft:glass_pane', R, Z, I });

// ---------- the 20 newer blocks ----------
gate('edge_detector', 'minecraft:observer');
gate('analog_inverter', 'minecraft:redstone_torch');
shaped('signal_adder', ['.X.', 'RZR', 'SSS'], { X: 'minecraft:comparator', R, Z, S: 'minecraft:stone_slab' });
shaped('signal_subtractor', ['.X.', 'TZT', 'SSS'], { X: 'minecraft:comparator', T, Z, S: SS });
shaped('item_detector', ['IXI', 'RZR', 'III'], { X: 'minecraft:hopper', I: 'minecraft:gold_ingot', R, Z });
shaped('light_sensor', ['GXG', 'RZR', 'III'], { G: 'minecraft:glass', X: 'minecraft:glowstone_dust', I, R, Z });
shaped('block_detector', ['CXC', 'RZR', 'CCC'], { X: O, C, R, Z });
shaped('entity_counter', ['IXI', 'RZR', 'III'], { X: 'minecraft:compass', I, R, Z });
shaped('ice_maker', ['CXC', 'CZC', 'CRC'], { X: 'minecraft:packed_ice', C, Z, R });
shaped('water_pump', ['CXC', 'CZC', 'CRC'], { X: 'minecraft:sponge', C, Z, R });
shaped('firework_launcher', ['IXI', 'IZI', 'IDI'], { X: 'minecraft:firework_rocket', I, Z, D });
shaped('snowball_turret', ['SXS', 'SZS', 'SDS'], { X: 'minecraft:snowball', S: 'minecraft:snow_block', Z, D });
shaped('anvil_dropper', ['IXI', 'IZI', 'IRI'], { X: 'minecraft:anvil', I, Z, R });
shaped('cluster_tnter', ['TTT', 'TXT', 'TZT'], { T: 'minecraft:tnt', X: `${NS}:tnter`, Z });
shaped('tnt_rain', ['XTX', 'TZT', 'XTX'], { X: `${NS}:tnter`, T: 'minecraft:tnt', Z });
shaped('block_swapper', ['CXC', 'PZP', 'CRC'], { X: 'minecraft:ender_pearl', C, P: 'minecraft:piston', Z, R });
shaped('heal_pad', ['XXX', 'IZI', 'IRI'], { X: 'minecraft:golden_apple', I, Z, R });
shaped('speed_pad', ['XXX', 'IZI', 'IRI'], { X: 'minecraft:sugar', I, Z, R });
shaped('smoke_emitter', ['.X.', 'CZC', 'CRC'], { X: 'minecraft:campfire', C, Z, R });
shaped('tnt_activator', ['.X.', 'IZI', 'IUI'], { X: `${NS}:activator_net`, I, Z, U });

// ---------- worldgen: uranium ore in the End ----------
write(path.join(DATA, 'worldgen', 'configured_feature', 'uranium_ore.json'), {
  type: 'minecraft:ore',
  config: {
    size: 6, discard_chance_on_air_exposure: 0.0,
    targets: [{ target: { predicate_type: 'minecraft:block_match', block: 'minecraft:end_stone' }, state: { Name: `${NS}:uranium_ore` } }],
  },
});
write(path.join(DATA, 'worldgen', 'placed_feature', 'uranium_ore.json'), {
  feature: `${NS}:uranium_ore`,
  placement: [
    { type: 'minecraft:count', count: 14 },
    { type: 'minecraft:in_square' },
    { type: 'minecraft:height_range', height: { type: 'minecraft:uniform', min_inclusive: { absolute: 0 }, max_inclusive: { absolute: 110 } } },
    { type: 'minecraft:biome' },
  ],
});
write(path.join(DATA, 'forge', 'biome_modifier', 'uranium_ore.json'), {
  type: 'forge:add_features', biomes: '#minecraft:is_end', features: `${NS}:uranium_ore`, step: 'underground_ores',
});

// ---------- lang ----------
const en = { [`itemGroup.${NS}`]: 'RedstonePlus' };
const de = { [`itemGroup.${NS}`]: 'RedstonePlus' };
for (const [name, , e, g] of BLOCKS) { en[`block.${NS}.${name}`] = e; de[`block.${NS}.${name}`] = g; }
for (const [name, e, g] of ITEMS) { en[`item.${NS}.${name}`] = e; de[`item.${NS}.${name}`] = g; }
en[`entity.${NS}.frozen_tnt`] = 'Frozen TNT'; de[`entity.${NS}.frozen_tnt`] = 'Eingefrorenes TNT';
en[`entity.${NS}.nuke_tnt`] = 'Primed Uranium Nuke'; de[`entity.${NS}.nuke_tnt`] = 'Gezündete Uran-Atombombe';
const { DESC, MSG } = require('./lang_extra');
for (const [name] of BLOCKS) {
  if (!DESC[name]) throw new Error('no description for ' + name);
  en[`block.${NS}.${name}.desc`] = DESC[name][0]; de[`block.${NS}.${name}.desc`] = DESC[name][1];
}
for (const [name] of ITEMS) {
  if (!DESC[name]) throw new Error('no description for ' + name);
  en[`item.${NS}.${name}.desc`] = DESC[name][0]; de[`item.${NS}.${name}.desc`] = DESC[name][1];
}
for (const [k, [e, g]] of Object.entries(MSG)) { en[`message.${NS}.${k}`] = e; de[`message.${NS}.${k}`] = g; }
en[`tooltip.${NS}.linked_to`] = 'Linked to %s %s %s (%s)';
de[`tooltip.${NS}.linked_to`] = 'Verbunden mit %s %s %s (%s)';
require('./gen_piston')({ NS, ASSETS, DATA, write, en, de });
require('./gen_drill')({ NS, ASSETS, DATA, write, en, de });
write(path.join(ASSETS, 'lang', 'en_us.json'), en);
write(path.join(ASSETS, 'lang', 'de_de.json'), de);

console.log(`${BLOCKS.length} blocks, ${ITEMS.length} items`);
