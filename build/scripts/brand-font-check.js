#!/usr/bin/env node
// Перевіряє, чи у шрифті справді є літери, які ми обіцяємо в UI.
//
// Навіщо: «шрифт підтримує кирилицю» — це твердження з опису сімейства, а не
// факт про файл. Ми ж роздаємо дистрибутив українською, тому вимога конкретна:
// у cmap мають бути коди Ґ ґ є і ї ї, інакше інтерфейс локально впаде в
// фолбек-шрифт і «Їжа» намалюється квадратиком.
//
// Використання: node brand-font-check.js <файл.ttf> [ще…]
// Вихід: рядки «OK»/«НЕМАЄ» на код-точку, вимкнутий вихід — 0 або 1.

const fs = require("fs");

// Українські специфічні + латинка базова (перевіряємо, що підмножина не лита)
const REQUIRED = [
  ["0404", "є"], ["0406", "і"], ["0407", "ї"],
  ["0490", "Ґ"], ["0491", "ґ"],
  ["0410", "А (кир.)"], ["0430", "а (кир.)"],
  ["0041", "A (lat)"], ["0061", "a (lat)"],
];

function readTables(buf) {
  const numTables = buf.readUInt16BE(4);
  const out = {};
  for (let i = 0; i < numTables; i++) {
    const off = 12 + i * 16;
    const tag = buf.toString("latin1", off, off + 4);
    out[tag] = { offset: buf.readUInt32BE(off + 8), length: buf.readUInt32BE(off + 12) };
  }
  return out;
}

function cmapSubtable(buf, cmap) {
  const base = cmap.offset;
  const n = buf.readUInt16BE(base + 2);
  const want = [[3, 1], [3, 10], [0, 3], [0, 4]];
  for (const [platform, encoding] of want) {
    for (let i = 0; i < n; i++) {
      const rec = base + 4 + i * 8;
      if (buf.readUInt16BE(rec) === platform && buf.readUInt16BE(rec + 2) === encoding) {
        return base + buf.readUInt32BE(rec + 4);
      }
    }
  }
  return null;
}

// Формат 4 (BMP) і формат 12 (повний Unicode) — покривають усе, що віддає
// Google Fonts для цих сімейств.
function hasCodepoint(buf, sub, cp) {
  const format = buf.readUInt16BE(sub);
  if (format === 4) {
    const segCountX2 = buf.readUInt16BE(sub + 6);
    const segCount = segCountX2 / 2;
    const ends = sub + 14, starts = ends + segCountX2 + 2;
    const deltas = starts + segCountX2, ranges = deltas + segCountX2;
    for (let i = 0; i < segCount; i++) {
      const end = buf.readUInt16BE(ends + i * 2);
      if (cp > end) continue;
      const start = buf.readUInt16BE(starts + i * 2);
      if (cp < start) return null;
      const delta = buf.readInt16BE(deltas + i * 2);
      const range = buf.readUInt16BE(ranges + i * 2);
      if (range === 0) return (cp + delta) & 0xffff;
      const giAddr = ranges + i * 2 + range + (cp - start) * 2;
      const gi = buf.readUInt16BE(giAddr);
      return gi === 0 ? null : (gi + delta) & 0xffff;
    }
    return null;
  }
  if (format === 12) {
    const groups = buf.readUInt32BE(sub + 12);
    for (let i = 0; i < groups; i++) {
      const g = sub + 16 + i * 12;
      if (cp >= buf.readUInt32BE(g) && cp <= buf.readUInt32BE(g + 4)) {
        return buf.readUInt32BE(g + 8) + (cp - buf.readUInt32BE(g));
      }
    }
    return null;
  }
  return null;
}

let failed = 0;
const files = process.argv.slice(2);
if (!files.length) {
  console.error("використання: node brand-font-check.js <файл.ttf> [ще…]");
  process.exit(2);
}

for (const file of files) {
  const buf = fs.readFileSync(file);
  const tables = readTables(buf);
  if (!tables.cmap) { console.error(`НЕМАЄ cmap у ${file}`); failed++; continue; }
  const sub = cmapSubtable(buf, tables.cmap);
  if (sub === null) { console.error(`НЕМАЄ зрозумілого cmap у ${file}`); failed++; continue; }
  const missing = [];
  for (const [hex, label] of REQUIRED) {
    const glyph = hasCodepoint(buf, sub, parseInt(hex, 16));
    console.log(`${glyph ? "OK    " : "НЕМАЄ"} U+${hex} ${label}`);
    if (!glyph) missing.push(`U+${hex}`);
  }
  if (missing.length) {
    console.error(`${file}: бракує ${missing.join(", ")}`);
    failed++;
  } else {
    console.log(`→ ${file}: покриття повне`);
  }
}
process.exit(failed ? 1 : 0);
