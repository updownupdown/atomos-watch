// Bakes the watch's Atlas data from the phone app's own sources: each atom's numbers (mass,
// melting and boiling points, density...), its discovery and name, its stable and known isotopes,
// and its facts and stories. Run from the phone repo's checkout:
//   npx tsx ../atomos-watch/tools/gen-info.mts <path to atomos> <out dir>
// Writes atoms-info.json, one object per atom in order. Short keys keep the file small; the watch
// reads it in AtomInfo.kt.

import { writeFileSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const phone = resolve(process.argv[2] ?? '.');
const out = resolve(process.argv[3] ?? '.');
const read = (f: string) => JSON.parse(readFileSync(join(phone, 'src/data', f), 'utf8'));
const elements = read('elements.json') as Record<string, unknown>[];
const facts = read('element-facts.json') as { h: string; f: string[] }[];
const story = read('element-story.json') as { z: number; origins?: string[]; uses?: string[] }[];

const info = elements.map((e) => {
  const f = facts[(e.z as number) - 1];
  const st = story.find((s) => s.z === e.z);
  return {
    z: e.z,
    ser: e.ser, w: e.w, A: e.A,
    mp: e.mp ?? null, bp: e.bp ?? null, dens: e.dens ?? null, en: e.en ?? null,
    year: e.year ?? null, who: e.who ?? null, where: e.where ?? null, origin: e.origin ?? null,
    stab: e.stab ?? 0, nIso: e.nIso ?? 0, crust: e.crust ?? null, sea: e.sea ?? null,
    h: f?.h ?? '', f: f?.f ?? [],
    from: st?.origins ?? [], uses: st?.uses ?? [],
  };
});
writeFileSync(join(out, 'atoms-info.json'), JSON.stringify(info));
console.log(`${info.length} atoms`);
