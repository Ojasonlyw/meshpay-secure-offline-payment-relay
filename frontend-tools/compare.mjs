/** Exact visual/style comparison against the locked cleanup reference. */
import { readFile, writeFile } from "node:fs/promises";
import { PNG } from "pngjs";
import pixelmatch from "pixelmatch";
import assert from "node:assert/strict";
import { capture } from "./browser.mjs";
import { compareValidationDOM } from "./validation-dom.mjs";
await capture("actual");
const expected = JSON.parse(await readFile("cleanup/computed-styles.json", "utf8"));
const actual = JSON.parse(await readFile("actual/computed-styles.json", "utf8"));
let failures = 0;
const historicalNativeDifferences = [];
for (const name of Object.keys(expected)) {
  const before = PNG.sync.read(await readFile(`cleanup/${name}.png`));
  const after = PNG.sync.read(await readFile(`actual/${name}.png`));
  if (before.width !== after.width || before.height !== after.height) {
    console.error(name, "size changed");
    failures++;
    continue;
  }
  const diff = new PNG({ width: before.width, height: before.height });
  const pixels = pixelmatch(
    before.data,
    after.data,
    diff.data,
    before.width,
    before.height,
    { threshold: 0, includeAA: true },
  );
  if (pixels) {
    await writeFile(`actual/${name}-diff.png`, PNG.sync.write(diff));
    console.error(name, pixels, "different pixels");
    if (name.endsWith("-invalid")) {
      historicalNativeDifferences.push({ name, pixels });
      if (process.argv.includes("--historical-strict")) failures++;
    } else failures++;
  }
  try {
    assert.deepEqual(actual[name], expected[name]);
  } catch {
    console.error(name, "computed styles changed");
    failures++;
  }
}
const baselineAxe = JSON.parse(await readFile("baseline/accessibility.json", "utf8"));
const actualAxe = JSON.parse(await readFile("actual/accessibility.json", "utf8"));
for (const [state, violations] of Object.entries(actualAxe)) {
  for (const violation of violations) {
    const old = baselineAxe[state]?.find((v) => v.id === violation.id);
    if (!old || violation.count > old.count) {
      console.error(state, "new accessibility violation", violation);
      failures++;
    }
  }
}
const validation = await compareValidationDOM();
failures += validation.results.filter((result) => result.pixels !== 0).length;
await writeFile(
  "actual/result.json",
  JSON.stringify(
    {
      failures,
      states: Object.keys(expected).length,
      staticPixelTolerance: 0,
      historicalNativeDifferences,
      validation,
      policy:
        "26 locked screenshots + 6 settled invalid DOM comparisons; all 32 retained computed-style states. Historical native popover composites are reported, never replaced.",
    },
    null,
    2,
  ),
);
if (failures) process.exitCode = 1;
else
  console.log(
    "PASS: exact application pixels/styles; native historical differences reported separately; no new accessibility violations.",
  );
