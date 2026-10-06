import { test, expect } from "@playwright/test";
test("send, four gossip rounds, settle once, reconcile and duplicate replay", async ({
  page,
  request,
}) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const before = await (await request.get("/api/accounts")).json();
  await page.goto("/");
  await expect(page.locator("#connection-text")).toHaveText("Live connection");
  await expect(page.locator("#senderVpa option")).toHaveCount(before.length);
  await page.locator("#amount").fill("1");
  const sent = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/demo/send") &&
      response.request().method() === "POST",
  );
  await page.locator("#payment-form button[type=submit]").click();
  const sentResponse = await sent;
  expect(sentResponse.ok()).toBeTruthy();
  for (let round = 0; round < 4; round++) {
    await expect(page.locator("#gossip")).toBeEnabled();
    const response = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/mesh/gossip") &&
        response.request().method() === "POST",
    );
    await page.locator("#gossip").click();
    expect((await response).ok()).toBeTruthy();
  }
  await expect(page.locator("#flush")).toBeEnabled();
  let flushed = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/mesh/flush") &&
      response.request().method() === "POST",
  );
  await page.locator("#flush").click();
  const first = await (await flushed).json();
  expect(first.results.some((item) => item.outcome === "SETTLED")).toBeTruthy();
  await expect(page.locator("#payments-table tbody .success").first()).toHaveText(
    "Settled",
  );
  await expect(page.locator("#reconciliation-summary")).toContainText("no mismatches");
  const settled = await (await request.get("/api/accounts")).json();
  expect(Number(settled.find((a) => a.vpa === "alice@demo").balance)).toBe(
    Number(before.find((a) => a.vpa === "alice@demo").balance) - 1,
  );
  expect(Number(settled.find((a) => a.vpa === "bob@demo").balance)).toBe(
    Number(before.find((a) => a.vpa === "bob@demo").balance) + 1,
  );
  await expect(page.locator("#flush")).toBeEnabled();
  flushed = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/mesh/flush") &&
      response.request().method() === "POST",
  );
  await page.locator("#flush").click();
  const second = await (await flushed).json();
  expect(second.results.every((item) => item.outcome === "DUPLICATE")).toBeTruthy();
  expect(await (await request.get("/api/accounts")).json()).toEqual(settled);
  expect(
    (await (await request.get("/api/reconciliation")).json()).balanced,
  ).toBeTruthy();
  expect(errors).toEqual([]);
});
test("filters, search, account cashflow and keyboard row activation", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("#connection-text")).toHaveText("Live connection");
  await page.locator("#payment-counts [data-status=SETTLED]").click();
  await expect(page.locator("#payment-filter-clear")).toBeVisible();
  await page.locator("#payment-filter-clear").click();
  await page.locator("#tx-search").fill("impossible-query");
  await expect(page.locator("#tx-table")).toContainText("No matching transactions");
  await page.locator("#tx-search").fill("");
  await page.locator("#tx-status").selectOption("REJECTED");
  await expect(page.locator("#tx-table")).toContainText("No matching transactions");
  await page.locator("#tx-status").selectOption("");
  await page.locator("#activity-type").selectOption("SIGNATURE");
  await expect(page.locator("#log")).toContainText("Signature");
  await page.locator('#accounts-table [data-account-vpa="bob@demo"]').focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#cashflow-heading")).toHaveAttribute("title", "bob@demo");
  await page.locator('#devices [data-device-id="phone-alice"]').click();
  await expect(page.locator("#devices .selected-row")).toHaveAttribute(
    "data-device-id",
    "phone-alice",
  );
  await page.keyboard.press("Control+k");
  await expect(page.locator("#command-search")).toBeFocused();
  await page.locator("#command-search").fill("Alice");
  await page.keyboard.press("Enter");
  await expect(
    page.locator('#accounts-table [data-account-vpa="alice@demo"]'),
  ).toBeFocused();
  await page.locator("#tx-table tbody tr[data-payment-id]").first().focus();
  await page.keyboard.press("Enter");
  await expect(
    page.locator("#payments-table tbody tr[data-payment-id]").first(),
  ).toBeFocused();
});
test("dialogs validate, cancel, restore focus and save an existing route", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("#connection-text")).toHaveText("Live connection");
  await page.locator("#reset").click();
  await expect(page.locator("#reset-dialog")).toBeVisible();
  await expect(page.locator("#reset-cancel")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.locator("#reset")).toBeFocused();
  await page.locator("#add-route").click();
  await page.locator("#route-source").selectOption("phone-alice");
  await page.locator("#route-target").selectOption("phone-alice");
  await page.locator("#route-form button[type=submit]").click();
  expect(
    await page.locator("#route-target").evaluate((el) => !el.validity.valid),
  ).toBeTruthy();
  await page.locator("#route-target").selectOption("phone-stranger1");
  await page.locator("#route-form button[type=submit]").click();
  await expect(page.locator("#route-dialog")).not.toBeVisible();
  await expect(page.locator("#gossip")).toBeEnabled();
  await page.locator("#reset").click();
  await page.locator("#reset-confirm").click();
  await expect(page.locator("#devices")).toContainText("0 packets");
});
test("mobile drawer traps focus, closes with Escape and restores its opener", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 1000 });
  await page.goto("/");
  await expect(page.locator("#connection-text")).toHaveText("Live connection");
  await page.locator(".skip-link").focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#main")).toBeFocused();
  await page.locator("#menu-toggle").click();
  await expect(page.locator("#sidebar")).toHaveAttribute("aria-modal", "true");
  await expect(page.locator(".page")).toHaveAttribute("aria-hidden", "true");
  await page.locator("#sidebar a").last().focus();
  await page.keyboard.press("Tab");
  await expect(page.locator("#sidebar .brand")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.locator("#menu-toggle")).toBeFocused();
  await expect(page.locator("#drawer-backdrop")).not.toBeVisible();
});
test("validation prevents writes and failed writes expose a persistent safe trace", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator("#connection-text")).toHaveText("Live connection");
  let writes = 0;
  await page.route("**/api/demo/send", (route) => {
    writes++;
    return route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Request validation failed.",
        errorCode: "VALIDATION_FAILED",
        traceId: "browser-test-trace",
      }),
    });
  });
  await page.locator("#amount").fill("0");
  await page.locator("#payment-form button[type=submit]").click();
  expect(writes).toBe(0);
  await page.locator("#amount").fill("1");
  await page.locator("#payment-form button[type=submit]").click();
  await expect(page.locator("#notice")).toContainText("browser-test-trace");
  await expect(page.locator("#notice")).toContainText("Request validation failed.");
  await page.waitForTimeout(4200);
  await expect(page.locator("#notice")).toBeVisible();
  expect(writes).toBe(1);
  await page.getByRole("button", { name: "Dismiss", exact: true }).click();
  await expect(page.locator("#notice")).not.toBeVisible();
});
