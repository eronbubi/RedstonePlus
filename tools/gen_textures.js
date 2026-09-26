// Generates every 16x16 texture of RedstonePlus as PNG files.
// Run: node tools/gen_textures.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'redstoneplus', 'textures');

// ---------- PNG writer ----------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function writePng(file, img) {
  const { w, h, px } = img;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const p = px[y * w + x];
      const o = y * (w * 4 + 1) + 1 + x * 4;
      raw[o] = p[0]; raw[o + 1] = p[1]; raw[o + 2] = p[2]; raw[o + 3] = p[3];
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, png);
}

// ---------- drawing helpers ----------
function hex(c, a = 255) {
  const n = parseInt(c.replace('#', ''), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a];
}
function img(w = 16, h = 16) {
  return { w, h, px: Array.from({ length: w * h }, () => [0, 0, 0, 0]) };
}
function seededRand(seed) {
  let s = 0;
  for (const ch of seed) s = (s * 31 + ch.charCodeAt(0)) >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}
function set(im, x, y, c) {
  if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
  im.px[y * im.w + x] = c.slice();
}
function shade(c, f) {
  return [Math.max(0, Math.min(255, Math.round(c[0] * f))), Math.max(0, Math.min(255, Math.round(c[1] * f))),
    Math.max(0, Math.min(255, Math.round(c[2] * f))), c[3]];
}
function noiseFill(im, base, seed, amount = 0.12) {
  const r = seededRand(seed);
  for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) set(im, x, y, shade(base, 1 - amount / 2 + r() * amount));
}
function border(im, c, inset = 0) {
  for (let i = inset; i < im.w - inset; i++) {
    set(im, i, inset, c); set(im, i, im.h - 1 - inset, c);
    set(im, inset, i, c); set(im, im.w - 1 - inset, i, c);
  }
}
function rect(im, x0, y0, x1, y1, c) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(im, x, y, c);
}

const FONT = {
  A: ['.#.', '#.#', '###', '#.#', '#.#'], B: ['##.', '#.#', '##.', '#.#', '##.'], C: ['.##', '#..', '#..', '#..', '.##'],
  D: ['##.', '#.#', '#.#', '#.#', '##.'], E: ['###', '#..', '##.', '#..', '###'], F: ['###', '#..', '##.', '#..', '#..'],
  G: ['.##', '#..', '#.#', '#.#', '.##'], H: ['#.#', '#.#', '###', '#.#', '#.#'], I: ['###', '.#.', '.#.', '.#.', '###'],
  J: ['..#', '..#', '..#', '#.#', '.#.'], K: ['#.#', '#.#', '##.', '#.#', '#.#'], L: ['#..', '#..', '#..', '#..', '###'],
  M: ['#.#', '###', '###', '#.#', '#.#'], N: ['##.', '#.#', '#.#', '#.#', '#.#'], O: ['.#.', '#.#', '#.#', '#.#', '.#.'],
  P: ['##.', '#.#', '##.', '#..', '#..'], Q: ['.#.', '#.#', '#.#', '##.', '.##'], R: ['##.', '#.#', '##.', '#.#', '#.#'],
  S: ['.##', '#..', '.#.', '..#', '##.'], T: ['###', '.#.', '.#.', '.#.', '.#.'], U: ['#.#', '#.#', '#.#', '#.#', '###'],
  V: ['#.#', '#.#', '#.#', '#.#', '.#.'], W: ['#.#', '#.#', '###', '###', '#.#'], X: ['#.#', '#.#', '.#.', '#.#', '#.#'],
  Y: ['#.#', '#.#', '.#.', '.#.', '.#.'], Z: ['###', '..#', '.#.', '#..', '###'],
  0: ['###', '#.#', '#.#', '#.#', '###'], 1: ['.#.', '##.', '.#.', '.#.', '###'], 2: ['##.', '..#', '.#.', '#..', '###'],
  3: ['##.', '..#', '.#.', '..#', '##.'], 4: ['#.#', '#.#', '###', '..#', '..#'], 5: ['###', '#..', '##.', '..#', '##.'],
  6: ['.##', '#..', '###', '#.#', '###'], 7: ['###', '..#', '.#.', '.#.', '.#.'], 8: ['###', '#.#', '###', '#.#', '###'],
  9: ['###', '#.#', '###', '..#', '##.'], '&': ['.#.', '#.#', '.#.', '#.#', '.##'], '!': ['.#.', '.#.', '.#.', '...', '.#.'],
  '+': ['...', '.#.', '###', '.#.', '...'], '=': ['...', '###', '...', '###', '...'], '-': ['...', '...', '###', '...', '...'],
};
function text(im, str, x, y, c) {
  for (const ch of str) {
    const g = FONT[ch];
    if (g) for (let r = 0; r < 5; r++) for (let k = 0; k < 3; k++) if (g[r][k] === '#') set(im, x + k, y + r, c);
    x += 4;
  }
}
function centeredText(im, str, y, c) {
  const width = str.length * 4 - 1;
  text(im, str, Math.floor((16 - width) / 2), y, c);
}
function arrowUp(im, cx, y, c) {
  set(im, cx, y, c); set(im, cx + 1, y, c);
  rect(im, cx - 1, y + 1, cx + 2, y + 1, c);
  rect(im, cx - 2, y + 2, cx + 3, y + 2, c);
  rect(im, cx, y + 3, cx + 1, y + 4, c);
}

// ---------- block textures: every block gets its own set ----------
const { BLOCKS } = require('./spec');
const RED_OFF = hex('#5e0a0a');
const RED_ON = hex('#ff2a1a');

function save(kind, name, im) {
  writePng(path.join(ROOT, kind, name + '.png'), im);
}
function mix(a, b, t) {
  return [0, 1, 2].map((i) => Math.round(a[i] * (1 - t) + b[i] * t)).concat([255]);
}

// Surface patterns. Each draws over an already noise-filled image.
const PATTERNS = {
  smooth: (im, b, a) => border(im, shade(b, 0.75)),
  grid: (im, b) => { for (let i = 0; i < 16; i += 4) for (let k = 0; k < 16; k++) { set(im, i, k, shade(b, 0.8)); set(im, k, i, shade(b, 0.8)); } },
  dots: (im, b, a) => { for (let y = 2; y < 16; y += 4) for (let x = 2; x < 16; x += 4) set(im, x, y, mix(b, a, 0.5)); border(im, shade(b, 0.7)); },
  cross: (im, b, a) => { border(im, shade(b, 0.7)); for (let i = 1; i < 15; i++) { set(im, i, i, shade(b, 0.85)); set(im, 15 - i, i, shade(b, 0.85)); } },
  bands: (im, b, a) => { rect(im, 0, 4, 15, 4, shade(b, 0.6)); rect(im, 0, 11, 15, 11, shade(b, 0.6)); for (let x = 0; x < 16; x += 3) rect(im, x, 5, x, 10, shade(b, 0.85)); },
  ice: (im, b, a) => { const r = seededRand('ice' + b); for (let i = 0; i < 18; i++) set(im, Math.floor(r() * 16), Math.floor(r() * 16), a); border(im, mix(b, a, 0.5)); },
  planks: (im, b) => { for (let y = 0; y < 16; y += 4) rect(im, 0, y, 15, y, shade(b, 0.65)); for (let y = 0; y < 16; y += 4) set(im, (y * 5) % 16, y + 2, shade(b, 0.6)); },
  hazard: (im, b, a) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (((x + y) >> 2) % 2 === 0 && (y < 3 || y > 12)) set(im, x, y, a); },
  swirl: (im, b, a) => { for (let t = 0; t < 40; t++) { const r = t / 6, ang = t * 0.5; set(im, Math.round(8 + Math.cos(ang) * r), Math.round(8 + Math.sin(ang) * r), mix(b, a, t / 40)); } },
  circuit: (im, b, a) => { const c = mix(b, a, 0.4); rect(im, 2, 3, 13, 3, c); rect(im, 13, 3, 13, 12, c); rect(im, 2, 12, 13, 12, c); rect(im, 2, 7, 8, 7, c); set(im, 8, 8, a); set(im, 2, 3, a); set(im, 2, 12, a); },
  plated: (im, b) => { border(im, shade(b, 0.55)); border(im, shade(b, 1.25), 1); for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) set(im, x, y, shade(b, 1.5)); },
  rivets: (im, b) => { border(im, shade(b, 0.6)); for (const [x, y] of [[1, 1], [14, 1], [1, 14], [14, 14], [7, 1], [7, 14]]) set(im, x, y, shade(b, 1.6)); },
  stars: (im, b, a) => { const r = seededRand('stars' + b); for (let i = 0; i < 10; i++) set(im, Math.floor(r() * 16), Math.floor(r() * 16), a); border(im, shade(b, 0.6)); },
  cracks: (im, b) => { const c = shade(b, 0.55); let x = 3, y = 0; for (let i = 0; i < 16; i++) { set(im, x, y, c); y++; x += i % 3 === 0 ? 1 : 0; } x = 12; y = 15; for (let i = 0; i < 10; i++) { set(im, x, y, c); y--; x -= i % 2; } },
  bricks: (im, b) => { const c = shade(b, 0.65); for (let y = 0; y < 16; y += 4) { rect(im, 0, y, 15, y, c); for (let x = (y / 4) % 2 ? 0 : 4; x < 16; x += 8) rect(im, x, y, x, y + 3, c); } },
  bolt: (im, b, a) => { const pts = [[9, 1], [8, 2], [7, 3], [6, 4], [7, 5], [8, 6], [9, 7], [8, 8], [7, 9], [6, 10], [7, 11], [6, 12], [5, 13]]; pts.forEach(([x, y]) => { set(im, x, y, a); set(im, x + 1, y, a); }); border(im, shade(b, 0.6)); },
  chevron: (im, b, a) => { for (const y0 of [3, 9]) for (let i = 0; i < 5; i++) { set(im, 7 - i, y0 + i, a); set(im, 8 + i, y0 + i, a); } border(im, shade(b, 0.6)); },
  vents: (im, b) => { for (let y = 2; y < 14; y += 3) rect(im, 2, y, 13, y, shade(b, 0.5)); border(im, shade(b, 0.6)); },
  flames: (im, b, a) => { const r = seededRand('fl' + b); for (let x = 0; x < 16; x++) { const h = 3 + Math.floor(r() * 6); for (let y = 15; y > 15 - h; y--) set(im, x, y, mix(b, a, (15 - y) / h)); } },
  wheat: (im, b, a) => { for (let x = 1; x < 16; x += 3) for (let y = 4; y < 15; y++) set(im, x, y, y < 7 ? a : shade(b, 1.3)); },
  stripes: (im, b, a) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (((x + y) >> 1) % 4 === 0) set(im, x, y, shade(b, 1.35)); },
  waves: (im, b, a) => { for (let x = 0; x < 16; x++) for (const y0 of [4, 10]) set(im, x, y0 + Math.round(Math.sin(x / 2) * 1.5), mix(b, a, 0.6)); },
  glow: (im, b, a) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d < 5) set(im, x, y, mix(im.px[y * 16 + x], a, (5 - d) / 6)); } border(im, shade(b, 0.6)); },
  belt: (im, b) => { for (let y = 1; y < 16; y += 3) rect(im, 0, y, 15, y, shade(b, 1.5)); },
  lamp: (im, b) => border(im, shade(b, 0.5)),
  display: (im, b) => border(im, hex('#555555')),
  net: () => {},
  ore: () => {},
};

function surface(spec, seed, baseShade = 1) {
  const [name, , , , baseHex, accentHex, , pattern] = spec;
  const b = shade(hex(baseHex), baseShade), a = hex(accentHex);
  const im = img();
  noiseFill(im, b, name + seed, 0.16);
  (PATTERNS[pattern] || PATTERNS.smooth)(im, b, a);
  return im;
}
// Status light: dark red when off, bright red (or the accent colour) when powered.
function statusLight(im, on, x, y, w = 4, h = 2) {
  rect(im, x, y, x + w - 1, y + h - 1, on ? RED_ON : RED_OFF);
}

for (const spec of BLOCKS) {
  const [name, shape, , , baseHex, accentHex, label] = spec;
  const a = hex(accentHex);
  if (shape === 'net') {
    const im = img();
    for (let i = 0; i < 16; i += 5) for (let k = 0; k < 16; k++) { set(im, i, k, hex(baseHex, 230)); set(im, k, i, hex(baseHex, 230)); }
    for (let i = 0; i < 16; i++) { set(im, i, i, hex(accentHex, 200)); set(im, 15 - i, i, hex(accentHex, 200)); }
    save('block', name, im);
    continue;
  }
  if (shape === 'display') {
    for (let p = 0; p <= 15; p++) {
      const im = surface(spec, 'd');
      const c = p === 0 ? hex('#5a1010') : hex(accentHex);
      centeredText(im, String(p), 4, c);
      for (let i = 0; i < p; i++) set(im, 1 + Math.floor(i * 14 / 15), 12, c);
      save('block', `${name}_${p}`, im);
    }
    save('block', name + '_side', surface(spec, 's', 0.8));
    continue;
  }
  if (shape === 'ore') {
    const im = surface(spec, 'o');
    const r = seededRand(name);
    for (let i = 0; i < 9; i++) {
      const x = 1 + Math.floor(r() * 13), y = 1 + Math.floor(r() * 13);
      set(im, x, y, a); set(im, x + 1, y, shade(a, 0.7)); set(im, x, y + 1, shade(a, 0.7));
    }
    save('block', name, im);
    continue;
  }
  if (shape === 'lamp') {
    for (const on of [false, true]) {
      const im = img();
      noiseFill(im, on ? hex(accentHex) : hex(baseHex), name + on, 0.12);
      border(im, on ? hex('#ffffff') : shade(hex(baseHex), 0.5));
      rect(im, 4, 4, 11, 11, on ? mix(hex(accentHex), hex('#ffffff'), 0.6) : shade(hex(baseHex), 0.7));
      if (name === 'inverted_lamp') { rect(im, 7, 2, 8, 13, on ? hex('#ffffff') : shade(hex(baseHex), 0.4)); }
      else { rect(im, 2, 7, 13, 8, on ? hex('#ffffff') : shade(hex(baseHex), 0.4)); }
      save('block', name + (on ? '_on' : ''), im);
    }
    continue;
  }
  // generic texture set: side, bottom, top(+on), front(+on), detail(+on)
  save('block', name + '_side', surface(spec, 'side'));
  save('block', name + '_bottom', surface(spec, 'bottom', 0.7));
  for (const on of [false, true]) {
    const sfx = on ? '_on' : '';
    const top = surface(spec, 'top', 1.1);
    if (shape.startsWith('gate')) {
      // arrow to the output (north) and the label
      const c = on ? mix(a, hex('#ffffff'), 0.2) : shade(a, 0.45);
      set(top, 7, 1, c); set(top, 8, 1, c); rect(top, 6, 2, 9, 2, c); rect(top, 5, 3, 10, 3, c);
      if (label) centeredText(top, label, 8, c);
    } else if (shape === 'conveyor') {
      const c = on ? shade(a, 0.4) : a;
      for (const y0 of [2, 8]) for (let i = 0; i < 4; i++) { set(top, 7 - i, y0 + i, c); set(top, 8 + i, y0 + i, c); }
    } else if (label && !['machine', 'cannon', 'rod'].includes(shape)) {
      centeredText(top, label.slice(0, 3), 8, on ? a : shade(a, 0.6));
      statusLight(top, on, 6, 3);
    } else {
      statusLight(top, on, 6, 7);
    }
    if (shape === 'phantom' && on) {
      const ghost = img();
      border(ghost, hex(accentHex, 200));
      for (let i = 2; i < 14; i += 3) { set(ghost, i, i, hex(accentHex, 120)); set(ghost, 15 - i, i, hex(accentHex, 120)); }
      save('block', name + '_top_on', ghost);
    } else {
      save('block', name + '_top' + sfx, top);
    }

    const front = surface(spec, 'front', 0.95);
    rect(front, 3, 2, 12, 5, shade(hex(baseHex), 0.45));
    statusLight(front, on, 5, 3, 6, 2);
    if (label) centeredText(front, label.slice(0, 3), 8, on ? mix(a, hex('#ffffff'), 0.3) : a);
    save('block', name + '_front' + sfx, front);

    const detail = img();
    noiseFill(detail, on ? a : shade(a, 0.5), name + 'detail', 0.2);
    border(detail, shade(on ? a : shade(a, 0.5), 0.7));
    save('block', name + '_detail' + sfx, detail);
  }
}

// ---------- items ----------
function item(name, draw) {
  const im = img();
  draw(im);
  save('item', name, im);
}
item('uranium_shard', (im) => {
  const g1 = hex('#3cff3c'), g2 = hex('#1fb81f'), g3 = hex('#0f6a0f');
  const rows = ['......##', '.....#12', '....#122', '...#1223', '..#12233', '.#122333', '#1223333', '.#223333', '..#2333.', '...#33..'];
  rows.forEach((r, y) => [...r].forEach((ch, x) => {
    const c = ch === '#' ? g3 : ch === '1' ? hex('#c8ffc8') : ch === '2' ? g1 : ch === '3' ? g2 : null;
    if (c) set(im, x + 4, y + 3, c);
  }));
});
item('enriched_uranium', (im) => {
  rect(im, 2, 5, 13, 11, hex('#1fb81f'));
  rect(im, 3, 6, 12, 10, hex('#3cff3c'));
  rect(im, 4, 6, 9, 7, hex('#c8ffc8'));
  for (let x = 2; x <= 13; x++) { set(im, x, 4, hex('#0f6a0f')); set(im, x, 12, hex('#0f6a0f')); }
  set(im, 1, 8, hex('#0f6a0f')); set(im, 14, 8, hex('#0f6a0f'));
});
item('redstone_circuit', (im) => {
  rect(im, 1, 3, 14, 12, hex('#1f6b2a'));
  for (let x = 1; x <= 14; x++) { set(im, x, 3, hex('#0f3a16')); set(im, x, 12, hex('#0f3a16')); }
  for (let x = 3; x <= 12; x++) set(im, x, 7, hex('#d0a030'));
  rect(im, 5, 5, 6, 6, hex('#ff2a1a')); rect(im, 9, 8, 10, 10, hex('#202020'));
  set(im, 3, 9, hex('#ff2a1a')); set(im, 12, 5, hex('#ff2a1a'));
});
item('remote_detonator', (im) => {
  rect(im, 4, 4, 11, 14, hex('#2a2a2a'));
  rect(im, 5, 5, 10, 13, hex('#3d3d3d'));
  rect(im, 6, 7, 9, 10, hex('#c01010'));
  rect(im, 7, 8, 8, 9, hex('#ff5050'));
  rect(im, 9, 0, 9, 3, hex('#8a8a8a')); set(im, 9, 0, hex('#ff3030'));
  rect(im, 6, 12, 9, 12, hex('#3cff3c'));
});
item('redstone_remote', (im) => {
  rect(im, 4, 2, 11, 14, hex('#e0e0e0'));
  rect(im, 5, 3, 10, 13, hex('#bcbcbc'));
  rect(im, 6, 4, 9, 6, hex('#303030'));
  set(im, 7, 5, hex('#ff3030')); set(im, 8, 5, hex('#ff3030'));
  rect(im, 6, 8, 7, 9, hex('#c01010')); rect(im, 8, 8, 9, 9, hex('#1060c0'));
  rect(im, 6, 11, 9, 12, hex('#808080'));
});
item('redstone_wrench', (im) => {
  const c = hex('#9a9aa6'), d = hex('#5a5a66'), r = hex('#c01010');
  for (let i = 0; i < 9; i++) { set(im, 3 + i, 12 - i, c); set(im, 4 + i, 12 - i, d); }
  rect(im, 11, 1, 14, 4, c); set(im, 12, 2, hex('#000000', 0)); set(im, 13, 1, hex('#000000', 0)); set(im, 13, 2, hex('#000000', 0));
  rect(im, 1, 12, 3, 14, r); rect(im, 2, 11, 4, 13, r);
});
item('multimeter', (im) => {
  rect(im, 3, 1, 12, 14, hex('#e0b020'));
  rect(im, 4, 2, 11, 6, hex('#1a2a1a'));
  text(im, '15', 4, 2, hex('#50ff50'));
  rect(im, 6, 9, 9, 12, hex('#303030')); set(im, 7, 10, hex('#ff3030'));
  rect(im, 13, 10, 14, 15, hex('#c01010')); rect(im, 1, 10, 2, 15, hex('#202020'));
});

item('tnt_activator', (im) => {
  rect(im, 5, 5, 10, 15, hex('#303030'));
  rect(im, 6, 6, 9, 14, hex('#5fb4e6'));
  rect(im, 6, 9, 9, 10, hex('#ffffff'));
  rect(im, 7, 1, 8, 4, hex('#8a8a8a'));
  rect(im, 6, 0, 9, 1, hex('#ff3030'));
  set(im, 4, 2, hex('#ffe040')); set(im, 11, 2, hex('#ffe040')); set(im, 3, 4, hex('#ffe040')); set(im, 12, 4, hex('#ffe040'));
});

console.log('textures written to', ROOT);

// ---------- drill vehicle ----------
{
  const body = img();
  noiseFill(body, hex('#e0b020'), 'drillbody', 0.12);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (((x + y) >> 2) % 2 === 0 && (y < 3 || y > 12)) set(body, x, y, hex('#202020'));
  border(body, hex('#8a6a10'));
  save('block', 'drill_body', body);

  const track = img();
  noiseFill(track, hex('#2a2a2a'), 'drilltrack', 0.1);
  for (let x = 0; x < 16; x += 3) rect(track, x, 0, x, 15, hex('#505050'));
  save('block', 'drill_track', track);

  const cabin = img();
  noiseFill(cabin, hex('#6a6e74'), 'drillcabin', 0.1);
  border(cabin, hex('#3a3c40'));
  rect(cabin, 3, 3, 12, 8, hex('#8fd0ff'));
  rect(cabin, 4, 4, 6, 5, hex('#e0f6ff'));
  save('block', 'drill_cabin', cabin);

  const front = img();
  noiseFill(front, hex('#44474c'), 'drillfront', 0.1);
  border(front, hex('#25272a'));
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) set(front, x, y, hex('#b0b0b0'));
  save('block', 'drill_front', front);

  const bit = img();
  noiseFill(bit, hex('#a8acb2'), 'drillbit', 0.12);
  for (let x = 0; x < 16; x++) for (let k = 0; k < 16; k += 4) set(bit, x, (x + k) % 16, hex('#5a5e64'));
  save('block', 'drill_bit', bit);
}
item('drill', (im) => {
  rect(im, 1, 11, 11, 14, hex('#2a2a2a'));
  rect(im, 2, 7, 10, 11, hex('#e0b020'));
  rect(im, 6, 4, 10, 7, hex('#6a6e74'));
  rect(im, 7, 5, 9, 6, hex('#8fd0ff'));
  rect(im, 11, 7, 12, 11, hex('#a8acb2'));
  rect(im, 13, 8, 13, 10, hex('#a8acb2'));
  set(im, 14, 9, hex('#e0e0e0'));
  for (const x of [2, 5, 8]) set(im, x, 14, hex('#505050'));
});
