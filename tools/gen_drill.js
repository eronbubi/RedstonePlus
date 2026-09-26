// Models, item model, recipe and texts for the drivable Drill. Called from gen_data.js.
module.exports = function genDrill({ NS, ASSETS, DATA, write, en, de }) {
  const path = require('path');
  const t = (n) => `${NS}:block/${n}`;
  const FULL = [0, 0, 16, 16];
  const box = (from, to, tex) => ({
    from, to,
    faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: FULL, texture: '#' + (tex[f] || tex.other) }])),
  });
  // the model faces north; the renderer turns it to the drill's direction
  write(path.join(ASSETS, 'models', 'block', 'drill_body_model.json'), {
    parent: 'minecraft:block/block',
    textures: { particle: t('drill_body'), body: t('drill_body'), track: t('drill_track'), cabin: t('drill_cabin'), front: t('drill_front') },
    elements: [
      box([0, 0, 1], [3, 4, 15], { other: 'track' }),
      box([13, 0, 1], [16, 4, 15], { other: 'track' }),
      box([2, 3, 2], [14, 8, 15], { other: 'body' }),
      box([3, 8, 11], [13, 14, 15], { north: 'cabin', other: 'front' }),
      box([4, 3, 0], [12, 11, 2], { other: 'front' }),
    ],
  });
  write(path.join(ASSETS, 'models', 'block', 'drill_bit_model.json'), {
    parent: 'minecraft:block/block',
    textures: { particle: t('drill_bit'), bit: t('drill_bit') },
    elements: [
      box([4, 3, -3], [12, 11, 0], { other: 'bit' }),
      box([5, 4, -6], [11, 10, -3], { other: 'bit' }),
      box([6, 5, -9], [10, 9, -6], { other: 'bit' }),
      box([7, 6, -12], [9, 8, -9], { other: 'bit' }),
    ],
  });
  for (const n of ['drill_body_model', 'drill_bit_model']) {
    write(path.join(ASSETS, 'blockstates', n + '.json'), { variants: { '': { model: t(n) } } });
  }
  write(path.join(ASSETS, 'models', 'item', 'drill.json'), { parent: 'minecraft:item/generated', textures: { layer0: `${NS}:item/drill` } });

  const ing = (id) => ({ item: id });
  write(path.join(DATA, 'recipe', 'drill.json'), {
    type: 'minecraft:crafting_shaped', category: 'redstone',
    pattern: ['IZ ', 'BPD', 'MMM'],
    key: {
      I: ing('minecraft:iron_block'), Z: ing(`${NS}:redstone_circuit`), B: ing('minecraft:minecart'),
      P: ing(`${NS}:enriched_uranium`), D: ing('minecraft:diamond_pickaxe'), M: ing('minecraft:iron_ingot'),
    },
    result: { id: `${NS}:drill`, count: 1 },
  });

  en[`item.${NS}.drill`] = 'Drill'; de[`item.${NS}.drill`] = 'Bohrer';
  en[`entity.${NS}.drill`] = 'Drill'; de[`entity.${NS}.drill`] = 'Bohrer';
  en[`block.${NS}.drill_body_model`] = 'Drill'; de[`block.${NS}.drill_body_model`] = 'Bohrer';
  en[`block.${NS}.drill_bit_model`] = 'Drill Bit'; de[`block.${NS}.drill_bit_model`] = 'Bohrkopf';
  en[`item.${NS}.drill.desc`] = 'Place it, right click to get in. W drives and bores a 3x3 tunnel, it turns where you look. Look down/up to dig down/up. Sneak to get out, hit it to pick it up.';
  de[`item.${NS}.drill.desc`] = 'Aufstellen, Rechtsklick zum Einsteigen. W fährt und bohrt einen 3x3-Tunnel, er fährt, wohin du schaust. Nach unten/oben schauen bohrt runter/hoch. Schleichen zum Aussteigen, schlagen zum Aufheben.';
};
