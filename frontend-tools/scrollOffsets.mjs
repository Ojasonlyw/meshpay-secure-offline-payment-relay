/** Recover the reference scroll position from an opaque fixed control, without changing reference images. */
import { readFile } from "node:fs/promises";
import { PNG } from "pngjs";
export async function referenceScroll(page, width) {
  const normal = PNG.sync.read(await readFile(`cleanup/${width}-normal.png`));
  const invalid = PNG.sync.read(await readFile(`cleanup/${width}-invalid.png`));
  const box = await page
    .locator(width >= 768 ? ".brand-mark" : ".avatar")
    .boundingBox();
  const cx = Math.round(box.x + box.width / 2),
    cy = Math.round(box.y + box.height / 2);
  const samples = [];
  for (let dy = -8; dy <= 8; dy++)
    for (let dx = -8; dx <= 8; dx++) {
      if (dx * dx + dy * dy > 64) continue;
      const index = ((cy + dy) * normal.width + cx + dx) * 4;
      if (width >= 768 && (normal.data[index] < 220 || normal.data[index + 1] > 180))
        continue;
      samples.push({ dx, dy, rgb: [...normal.data.subarray(index, index + 3)] });
    }
  let best = { error: Infinity, offset: 0 };
  for (let offset = 0; offset < invalid.height - 1000; offset++) {
    let error = 0;
    for (const sample of samples) {
      const index = ((cy + offset + sample.dy) * invalid.width + cx + sample.dx) * 4;
      for (let channel = 0; channel < 3; channel++)
        error += Math.abs(invalid.data[index + channel] - sample.rgb[channel]);
    }
    if (error < best.error) best = { error, offset };
    if (error === 0) break;
  }
  return best.offset;
}
