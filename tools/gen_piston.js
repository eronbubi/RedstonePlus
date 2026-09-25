// Assets and data for the Super Piston (normal and sticky). Called from gen_data.js.
// It looks exactly like the vanilla (sticky) piston, only the slime on the sticky one is blue.
module.exports = function genPiston({ NS, ASSETS, DATA, write, en, de }) {
  const path = require('path');
  const blockModel = (name, obj) => write(path.join(ASSETS, 'models', 'block', name + '.json'), obj);
  const blockstate = (name, obj) => write(path.join(ASSETS, 'blockstates', name + '.json'), obj);
  const BLUE = `${NS}:block/super_piston_top_sticky`;

  // same rotations as vanilla piston.json (models point north)
  const ROT = {
    down: { x: 90 }, east: { y: 90 }, north: {}, south: { y: 180 }, up: { x: 270 }, west: { y: 270 },
  };

  blockModel('sticky_super_piston', {
    parent: 'minecraft:block/template_piston',
    textures: { bottom: 'minecraft:block/piston_bottom', platform: BLUE, side: 'minecraft:block/piston_side' },
  });
  blockModel('super_piston_head_sticky', {
    parent: 'minecraft:block/template_piston_head',
    textures: { platform: BLUE, side: 'minecraft:block/piston_side', unsticky: 'minecraft:block/piston_top' },
  });
  // one arm segment: the piston rod through the whole block
  blockModel('super_piston_arm', {
    parent: 'minecraft:block/block',
    textures: { particle: 'minecraft:block/piston_side', side: 'minecraft:block/piston_side' },
    elements: [{
      from: [6, 6, 0], to: [10, 10, 16],
      faces: {
        down: { uv: [0, 0, 16, 4], texture: '#side', rotation: 90 },
        up: { uv: [0, 0, 16, 4], texture: '#side', rotation: 270 },
        west: { uv: [16, 4, 0, 0], texture: '#side' },
        east: { uv: [0, 0, 16, 4], texture: '#side' },
      },
    }],
  });

  for (const [name, closed] of [['super_piston', 'minecraft:block/piston'], ['sticky_super_piston', `${NS}:block/sticky_super_piston`]]) {
    const variants = {};
    for (const [f, rot] of Object.entries(ROT)) {
      variants[`extended=false,facing=${f}`] = { model: closed, ...rot };
      variants[`extended=true,facing=${f}`] = { model: 'minecraft:block/piston_base', ...rot };
    }
    blockstate(name, { variants });
    write(path.join(ASSETS, 'models', 'item', name + '.json'), { parent: closed });
    write(path.join(DATA, 'loot_table', 'blocks', name + '.json'), {
      type: 'minecraft:block',
      pools: [{ rolls: 1, bonus_rolls: 0, entries: [{ type: 'minecraft:item', name: `${NS}:${name}` }], conditions: [{ condition: 'minecraft:survives_explosion' }] }],
      random_sequence: `${NS}:blocks/${name}`,
    });
  }
  const head = {};
  const arm = {};
  for (const [f, rot] of Object.entries(ROT)) {
    head[`facing=${f},sticky=false`] = { model: 'minecraft:block/piston_head', ...rot };
    head[`facing=${f},sticky=true`] = { model: `${NS}:block/super_piston_head_sticky`, ...rot };
    arm[`facing=${f}`] = { model: `${NS}:block/super_piston_arm`, ...rot };
  }
  blockstate('super_piston_head', { variants: head });
  blockstate('super_piston_arm', { variants: arm });

  const ing = (id) => ({ item: id });
  write(path.join(DATA, 'recipe', 'super_piston.json'), {
    type: 'minecraft:crafting_shaped', category: 'redstone',
    pattern: ['PEP', 'PZP', 'PRP'],
    key: { P: ing('minecraft:piston'), E: ing(`${NS}:enriched_uranium`), Z: ing(`${NS}:redstone_circuit`), R: ing('minecraft:redstone_block') },
    result: { id: `${NS}:super_piston`, count: 1 },
  });
  write(path.join(DATA, 'recipe', 'sticky_super_piston.json'), {
    type: 'minecraft:crafting_shapeless', category: 'redstone',
    ingredients: [ing(`${NS}:super_piston`), ing('minecraft:slime_ball')],
    result: { id: `${NS}:sticky_super_piston`, count: 1 },
  });

  const L = [
    ['block', 'super_piston', 'Super Piston', 'SuperPiston'],
    ['block', 'sticky_super_piston', 'Sticky Super Piston', 'Klebriger SuperPiston'],
    ['block', 'super_piston_head', 'Super Piston Head', 'SuperPiston-Kopf'],
    ['block', 'super_piston_arm', 'Super Piston Arm', 'SuperPiston-Arm'],
  ];
  for (const [kind, name, e, g] of L) { en[`${kind}.${NS}.${name}`] = e; de[`${kind}.${NS}.${name}`] = g; }
  en[`block.${NS}.super_piston.desc`] = 'Pushes up to 50 blocks. Right click: set how far the arm goes (1-13 blocks).';
  de[`block.${NS}.super_piston.desc`] = 'Schiebt bis zu 50 Blöcke. Rechtsklick: Reichweite des Arms einstellen (1-13 Blöcke).';
  en[`block.${NS}.sticky_super_piston.desc`] = 'Sticky Super Piston: pushes up to 50 blocks and pulls them back. Right click: range 1-13.';
  de[`block.${NS}.sticky_super_piston.desc`] = 'Klebriger SuperPiston: schiebt bis zu 50 Blöcke und zieht sie zurück. Rechtsklick: Reichweite 1-13.';
  en[`gui.${NS}.range`] = 'Range: %s blocks'; de[`gui.${NS}.range`] = 'Reichweite: %s Blöcke';
  en[`gui.${NS}.push_limit`] = 'Pushes up to %s blocks'; de[`gui.${NS}.push_limit`] = 'Schiebt bis zu %s Blöcke';
};
