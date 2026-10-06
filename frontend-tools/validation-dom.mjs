/** Compare stable invalid-form DOM with the unchanged, committed cleanup source.
 * Native Chromium popovers/smooth-scroll composites are not deterministic pixels.
 * Never modifies either locked screenshot reference.
 */
import { execFileSync } from "node:child_process";
import { writeFile } from "node:fs/promises";
import { chromium } from "@playwright/test";
import { PNG } from "pngjs";
import pixelmatch from "pixelmatch";
import { openPage, fixtures, widths, baseURL } from "./browser.mjs";
export const sourceRevision = "89a459d";
export async function compareValidationDOM() {
  const frozen = new Map(
    [
      ["/", "src/main/resources/templates/dashboard.html"],
      ["/css/dashboard.css", "src/main/resources/static/css/dashboard.css"],
      ["/js/dashboard.js", "src/main/resources/static/js/dashboard.js"],
      ["/vendor/lucide.min.js", "src/main/resources/static/vendor/lucide.min.js"],
    ].map(([url, file]) => [
      url,
      execFileSync(
        "git",
        ["-c", "safe.directory=/workspace", "show", `${sourceRevision}:${file}`],
        { cwd: "..", encoding: "utf8" },
      ),
    ]),
  );
  for (const name of ["manrope-regular.ttf", "manrope-bold.ttf"])
    frozen.set(
      "/vendor/" + name,
      execFileSync(
        "git",
        [
          "-c",
          "safe.directory=/workspace",
          "show",
          `${sourceRevision}:src/main/resources/static/vendor/${name}`,
        ],
        { cwd: ".." },
      ),
    );
  const browser = await chromium.launch();
  const results = [];
  try {
    for (const width of widths) {
      const images = [];
      for (const original of [true, false]) {
        const { page, context } = await openPage(
          browser,
          width,
          await fixtures("normal"),
          original ? frozen : null,
        );
        await page.waitForFunction(() => typeof window.lucide === "object");
        if (original)
          await page.evaluate(() =>
            window.lucide.createIcons({ attrs: { "aria-hidden": "true" } }),
          );
        await page.evaluate(() =>
          Promise.all([
            document.fonts.load("13px Manrope"),
            document.fonts.load("800 13px Manrope"),
          ]),
        );
        await page.locator("#amount").fill("0");
        await page.locator("#payment-form button[type=submit]").click();
        if (await page.locator("#amount").evaluate((el) => el.validity.valid))
          throw new Error("Invalid form accepted");
        await page.waitForTimeout(650);
        await page.locator("#amount").evaluate((el) => {
          el.blur();
          el.focus({ preventScroll: true });
        });
        await page.evaluate(() => window.scrollTo({ top: 0, behavior: "instant" }));
        await page.waitForTimeout(100);
        await page.mouse.move(0, 0);
        const buffer = await page.screenshot({
          fullPage: true,
          animations: "disabled",
          caret: "hide",
        });
        await writeFile(
          `actual/${width}-validation-dom-${original ? "frozen" : "current"}.png`,
          buffer,
        );
        images.push(PNG.sync.read(buffer));
        await context.close();
      }
      const [a, b] = images;
      const diff = new PNG({ width: a.width, height: a.height });
      const pixels =
        a.width === b.width && a.height === b.height
          ? pixelmatch(a.data, b.data, diff.data, a.width, a.height, {
              threshold: 0,
              includeAA: true,
            })
          : -1;
      if (pixels > 0)
        await writeFile(
          `actual/${width}-validation-dom-diff.png`,
          PNG.sync.write(diff),
        );
      results.push({ width, pixels });
      console.log("Invalid DOM", width, pixels);
    }
  } finally {
    await browser.close();
  }
  return { sourceRevision, baseURL, results };
}
if (process.argv[1]?.endsWith("validation-dom.mjs"))
  console.log(await compareValidationDOM());
