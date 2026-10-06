import { test, expect } from "@playwright/test";
import { openPage, fixtures } from "../browser.mjs";
test("malformed refresh preserves loaded data and recovers on the next refresh", async ({
  browser,
}) => {
  const { page, context } = await openPage(browser, 1440, await fixtures("normal"));
  const previous = await page.locator("#accounts-table").innerText();
  let bad = true;
  await page.route("**/api/accounts", (route) =>
    bad
      ? route.fulfill({
          status: 200,
          contentType: "application/json",
          body: '[{"vpa":"bad","balance":null}]',
        })
      : route.fallback(),
  );
  await page.locator("#refresh").click();
  await expect(page.locator("#accounts-error")).toContainText("Previously loaded data");
  expect(await page.locator("#accounts-table").innerText()).toEqual(previous);
  bad = false;
  await expect(page.locator("#refresh")).toBeEnabled();
  await page.locator("#refresh").click();
  await expect(page.locator("#accounts-error")).toBeHidden();
  await expect(page.locator("#connection-text")).toHaveText("Live connection");
  await context.close();
});
test("visibility handling suspends reads and refreshes on return", async ({
  browser,
}) => {
  const { page, context } = await openPage(browser, 1440, await fixtures("normal"));
  let reads = 0;
  page.on("request", (request) => {
    if (
      request.method() === "GET" &&
      new URL(request.url()).pathname.startsWith("/api/")
    )
      reads++;
  });
  await page.evaluate(() => {
    window.testHidden = true;
    Object.defineProperty(document, "hidden", {
      configurable: true,
      get: () => window.testHidden,
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await page.waitForTimeout(3300);
  expect(reads).toBe(0);
  await page.evaluate(() => {
    window.testHidden = false;
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect.poll(() => reads).toBeGreaterThanOrEqual(11);
  await context.close();
});
test("rapid account changes cannot show a stale cashflow response", async ({
  browser,
}) => {
  const data = await fixtures("normal");
  const { page, context } = await openPage(browser, 1440, data);
  let release;
  const held = new Promise((resolve) => (release = resolve));
  await page.route("**/api/dashboard/cashflow*", async (route) => {
    const vpa = new URL(route.request().url()).searchParams.get("accountVpa");
    if (vpa === "bob@demo") await held;
    try {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ ...data["/api/dashboard/cashflow"], accountVpa: vpa }),
      });
    } catch {
      /* An obsolete request was deliberately aborted. */
    }
  });
  await page.locator('#accounts-table [data-account-vpa="bob@demo"]').click();
  await page.locator('#accounts-table [data-account-vpa="carol@demo"]').click();
  await expect(page.locator("#cashflow-heading")).toHaveAttribute(
    "title",
    "carol@demo",
  );
  release();
  await page.waitForTimeout(200);
  await expect(page.locator("#cashflow-heading")).toHaveAttribute(
    "title",
    "carol@demo",
  );
  await context.close();
});
test("success notices queue and expire while failures remain dismissible", async ({
  browser,
}) => {
  const { page, context } = await openPage(browser, 1440, await fixtures("normal"));
  await page.clock.install();
  await page.evaluate(async () => {
    const { notice } = await import("/js/ui/notice.js");
    notice("First success");
    notice("Second success");
    notice("Persistent failure", true, "queue-trace");
  });
  await expect(page.locator("#notice")).toContainText("First success");
  await page.clock.fastForward(4001);
  await expect(page.locator("#notice")).toContainText("Second success");
  await page.clock.fastForward(4001);
  await expect(page.locator("#notice")).toContainText("Persistent failure");
  await page.clock.fastForward(20000);
  await expect(page.locator("#notice")).toBeVisible();
  await page.getByRole("button", { name: "Dismiss", exact: true }).click();
  await expect(page.locator("#notice")).toBeHidden();
  await context.close();
});
