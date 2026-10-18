// Bakes the watch's atom data from the phone app's own sources, so the clouds and facts match.
// Run from the phone repo's checkout:
//   npx tsx ../atomos-watch/tools/gen-atoms.mts <path to atomos> <out dir>
// Writes atoms.json (names, places, hues, two facts each) and atoms.bin (each atom's point cloud,
// sampled with the phone's own hydrogen-like orbitals: |R_nl|^2 |Y_lm|^2 with Slater's charges).

import { writeFileSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const phone = resolve(process.argv[2] ?? '.');
const out = resolve(process.argv[3] ?? '.');
const data = await import(join(phone, 'src/gallery/data.ts'));
const orbital = await import(join(phone, 'src/gallery/orbital.ts'));
const facts = JSON.parse(readFileSync(join(phone, 'src/data/element-facts.json'), 'utf8')) as { h: string; f: string[] }[];

const BUDGET = 1400;
const atoms: unknown[] = [];
const chunks: Buffer[] = [];
for (const e of data.ELEMENTS) {
  // Outer subshells get more points than the core, as on the phone (the count of each orbital
  // grows with the square root of its reach), so the valence shape shows and the core glows.
  const subs = e.subshells as { n: number; l: string; count: number; zeff: number; meanRadius: number }[];
  const rmax = Math.max(...subs.map((s) => s.meanRadius));
  const weight = subs.map((s) => Math.max(0.15, Math.sqrt(s.meanRadius / rmax)));
  const base = BUDGET / subs.reduce((a, s, i) => a + s.count * weight[i], 0);
  const pts: { x: number; y: number; z: number; sub: number; oi: number }[] = [];
  subs.forEach((s, si) => {
    const perSlot = Math.max(3, Math.round(base * weight[si]));
    const cloud = orbital.sampleSubshell(s.n, s.l, perSlot, 1.6, 0.85);
    for (let i = 0; i < cloud.slot.length; i++) {
      if (cloud.slot[i] >= s.count) continue;
      pts.push({ x: cloud.positions[i * 3] / s.zeff, y: cloud.positions[i * 3 + 1] / s.zeff, z: cloud.positions[i * 3 + 2] / s.zeff, sub: si, oi: cloud.orbital[i] });
    }
  });
  const rs = pts.map((p) => Math.hypot(p.x, p.y, p.z)).sort((a, b) => a - b);
  const frame = (rs[Math.floor(rs.length * 0.85)] ?? 1) * 1.25;
  const q = (v: number) => Math.max(-127, Math.min(127, Math.round((v / frame) * 127)));
  const buf = Buffer.alloc(2 + pts.length * 5);
  buf.writeUInt16LE(pts.length, 0);
  pts.forEach((p, i) => {
    const o = 2 + i * 5;
    buf.writeInt8(q(p.x), o); buf.writeInt8(q(p.y), o + 1); buf.writeInt8(q(p.z), o + 2);
    buf.writeUInt8(p.sub, o + 3); buf.writeUInt8(p.oi, o + 4);
  });
  chunks.push(buf);
  const f = facts[e.z - 1];
  atoms.push({
    z: e.z, s: e.symbol, n: e.name, p: e.period, g: e.group, b: e.block,
    hue: data.placeColour(e), subs: e.subshells.map((s: { key: string; count: number }) => `${s.key}${s.count}`),
    f1: f?.h ?? '', f2: f?.f?.[0] ?? '',
  });
}
writeFileSync(join(out, 'atoms.json'), JSON.stringify(atoms));
writeFileSync(join(out, 'atoms.bin'), Buffer.concat(chunks));
console.log(`${atoms.length} atoms, ${chunks.reduce((a, b) => a + b.length, 0)} bytes of points`);
