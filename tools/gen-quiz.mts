// Deals Nu's questions with the phone app's own code (src/home/daily.ts, quizOf), for QuizTest to
// check the watch deals the same ones. Run from the phone repo's checkout:
//   npx tsx ../atomos-watch/tools/gen-quiz.mts <path to atomos> <out file> [from day] [to day]
// Writes { "<day>": [question, ...] } as JSON.

import { writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const phone = resolve(process.argv[2] ?? '.');
const out = resolve(process.argv[3] ?? 'quiz-phone.json');
const from = Number(process.argv[4] ?? 20700), to = Number(process.argv[5] ?? 20799);
const { quizOf } = await import(pathToFileURL(join(phone, 'src/home/daily.ts')).href);
const days: Record<string, unknown> = {};
for (let d = from; d <= to; d++) days[d] = quizOf(d);
writeFileSync(out, JSON.stringify(days));
console.log(`${to - from + 1} days to ${out}`);
