import { test } from "node:test";
import { choosePaymentAccounts } from "../../src/main/resources/static/js/views/accountSelections.js";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  money,
  compactMoney,
} from "../../src/main/resources/static/js/format/money.js";
import { chartDate } from "../../src/main/resources/static/js/format/dates.js";
import {
  validateAccounts,
  validateMesh,
  validatePayments,
} from "../../src/main/resources/static/js/api/validators.js";
import * as validators from "../../src/main/resources/static/js/api/dashboardValidators.js";
import { createStore } from "../../src/main/resources/static/js/state/store.js";
import { resolveSearch } from "../../src/main/resources/static/js/services/searchPriority.js";
import {
  ApiError,
  responseError,
  request,
} from "../../src/main/resources/static/js/api/client.js";
import {
  backoff,
  createPoller,
} from "../../src/main/resources/static/js/services/polling.js";
const fixtures = JSON.parse(
  await readFile(new URL("../baseline/demo.json", import.meta.url), "utf8"),
);
test("account defaults and fallbacks stay distinct without discarding valid choices", () => {
  const accounts = [{ vpa: "bob@demo" }, { vpa: "carol@demo" }];
  assert.deepEqual(
    choosePaymentAccounts(accounts, { senderVpa: "", receiverVpa: "" }),
    { senderVpa: "carol@demo", receiverVpa: "bob@demo" },
  );
  assert.deepEqual(
    choosePaymentAccounts(accounts, { senderVpa: "removed", receiverVpa: "bob@demo" }),
    { senderVpa: "carol@demo", receiverVpa: "bob@demo" },
  );
  assert.deepEqual(
    choosePaymentAccounts(accounts, {
      senderVpa: "bob@demo",
      receiverVpa: "carol@demo",
    }),
    { senderVpa: "bob@demo", receiverVpa: "carol@demo" },
  );
  assert.deepEqual(
    choosePaymentAccounts([], { senderVpa: "removed", receiverVpa: "removed" }),
    { senderVpa: "", receiverVpa: "" },
  );
});
test("INR and chart dates preserve original formatting", () => {
  assert.equal(money(1234.5), "₹1,234.50");
  assert.equal(compactMoney(500), "₹500");
  assert.equal(money("invalid"), "--");
  assert.equal(chartDate.format(new Date("2026-10-06T23:59:00Z")), "6 Oct");
});
test("all captured API contracts validate; malformed consumed fields are rejected", () => {
  validateAccounts(fixtures["/api/accounts"]);
  validateMesh(fixtures["/api/mesh/state"]);
  validatePayments(fixtures["/api/payments"]);
  for (const [name, path] of [
    ["Summary", "summary"],
    ["Cashflow", "cashflow"],
    ["Volume", "transaction-volume"],
    ["Activity", "activity"],
    ["Network", "network-stats"],
    ["Security", "security-events"],
  ])
    validators["validate" + name](fixtures["/api/dashboard/" + path]);
  validators.validateRoutes(fixtures["/api/mesh/routes"]);
  assert.throws(() =>
    validateAccounts([{ vpa: "a", holderName: "A", balance: "bad" }]),
  );
  assert.throws(() =>
    validators.validateCashflow({
      ...fixtures["/api/dashboard/cashflow"],
      points: [{ bucket: "bad" }],
    }),
  );
  assert.throws(() =>
    validators.validateNetwork({
      ...fixtures["/api/dashboard/network-stats"],
      averageHopCount: "bad",
    }),
  );
  assert.throws(() =>
    validators.validateActivity({
      items: [{ type: "PAYMENT", title: "A", description: "B", occurredAt: "bad" }],
    }),
  );
});
test("store snapshots, notifications and unsubscribe stay synchronized", () => {
  const store = createStore({ value: 1 });
  const seen = [];
  const stop = store.subscribe((next, previous) =>
    seen.push([previous.value, next.value]),
  );
  store.set({ value: 2 });
  stop();
  store.set({ value: 3 });
  assert.deepEqual(seen, [[1, 2]]);
  assert.equal(store.get().value, 3);
  assert.throws(() => {
    store.get().value = 4;
  }, TypeError);
});
test("search chooses payment before account, device and transaction", () => {
  const state = {
    payments: [{ paymentId: "alice-payment" }],
    accounts: [{ vpa: "alice@demo", holderName: "Alice" }],
    mesh: { devices: [{ deviceId: "alice-phone" }] },
    transactions: [{ id: 1, senderVpa: "alice", receiverVpa: "bob" }],
  };
  assert.equal(resolveSearch(" Alice ", state).type, "payment");
  state.payments = [];
  assert.equal(resolveSearch("alice", state).type, "account");
  state.accounts = [];
  assert.equal(resolveSearch("alice", state).type, "device");
  state.mesh = null;
  assert.equal(resolveSearch("alice", state).type, "transaction");
  assert.equal(resolveSearch("", state), null);
  assert.equal(resolveSearch("missing", state), null);
});
test("safe API errors retain status/code/trace including header fallback", () => {
  const response = new Response("{}", {
    status: 409,
    headers: { "X-Trace-Id": "header-trace" },
  });
  const error = responseError(response, {
    message: "Duplicate payment.",
    errorCode: "DUPLICATE_PAYMENT",
  });
  assert.equal(error.status, 409);
  assert.equal(error.errorCode, "DUPLICATE_PAYMENT");
  assert.equal(error.traceId, "header-trace");
  assert.equal(
    responseError(response, { traceId: "body-trace" }).traceId,
    "body-trace",
  );
});
test("request handles backend errors, invalid JSON and network failures", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () =>
      new Response(
        JSON.stringify({
          message: "Safe message",
          errorCode: "VALIDATION_FAILED",
          traceId: "trace",
        }),
        { status: 400 },
      ),
  );
  await assert.rejects(
    request("/test"),
    (error) =>
      error instanceof ApiError && error.status === 400 && error.traceId === "trace",
  );
  globalThis.fetch = async () => new Response("broken");
  await assert.rejects(
    request("/test"),
    (error) => error.errorCode === "INVALID_RESPONSE",
  );
  globalThis.fetch = async () => {
    throw new TypeError("internal network detail");
  };
  await assert.rejects(
    request("/test"),
    (error) => error.message === "Unable to reach the demo server.",
  );
});
test("timeout aborts the request without retrying it", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  let calls = 0;
  t.mock.method(globalThis, "fetch", (_path, { signal }) => {
    calls++;
    return new Promise((_resolve, reject) =>
      signal.addEventListener("abort", () => reject(new Error("abort"))),
    );
  });
  const pending = request("/test");
  t.mock.timers.tick(12000);
  await assert.rejects(pending, (error) => error.errorCode === "TIMEOUT");
  assert.equal(calls, 1);
});
test("a timeout while reading the body remains a timeout, not invalid JSON", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  t.mock.method(globalThis, "fetch", async (_path, { signal }) => ({
    ok: true,
    status: 200,
    headers: new Headers(),
    json: () =>
      new Promise((_resolve, reject) =>
        signal.addEventListener("abort", () => reject(new Error("body abort"))),
      ),
  }));
  const pending = request("/body-timeout");
  await Promise.resolve();
  t.mock.timers.tick(12000);
  await assert.rejects(pending, (error) => error.errorCode === "TIMEOUT");
});

test("backoff is exponential and bounded", () => {
  assert.equal(backoff(3000, 0), 3000);
  assert.equal(backoff(3000, 3), 24000);
  assert.equal(backoff(15000, 5), 60000);
});
function clock() {
  const scheduled = [];
  return {
    scheduled,
    setTimeout(fn, ms) {
      const handle = { fn, ms };
      scheduled.push(handle);
      return handle;
    },
    clearTimeout(handle) {
      const index = scheduled.indexOf(handle);
      if (index >= 0) scheduled.splice(index, 1);
    },
  };
}
test("reads coalesce and pause cancels retries", async () => {
  const time = clock();
  let calls = 0,
    resolve;
  const poller = createPoller(
    [
      {
        name: "mesh",
        interval: 3000,
        read: () => {
          calls++;
          return new Promise((done) => (resolve = done));
        },
        onData() {},
        onError() {},
      },
    ],
    time,
  );
  poller.resume();
  const first = poller.refresh("mesh");
  const second = poller.refresh("mesh");
  assert.equal(calls, 1);
  resolve({});
  await Promise.all([first, second]);
  assert.equal(time.scheduled[0].ms, 3000);
  poller.pause();
  assert.equal(time.scheduled.length, 0);
});
test("failed reads back off and success restores the normal interval", async () => {
  const time = clock();
  let fail = true;
  const poller = createPoller(
    [
      {
        name: "mesh",
        interval: 3000,
        read: async () => {
          if (fail) throw new Error("offline");
          return {};
        },
        onData() {},
        onError() {},
      },
    ],
    time,
  );
  poller.resume();
  await poller.refresh("mesh");
  assert.equal(time.scheduled[0].ms, 6000);
  fail = false;
  await poller.refresh("mesh");
  assert.equal(time.scheduled[0].ms, 3000);
  poller.pause();
});
test("a previous account response cannot replace a new selection", async () => {
  let selected = "alice",
    resolveOld;
  const seen = [];
  const poller = createPoller(
    [
      {
        name: "cashflow",
        key: () => selected,
        interval: 15000,
        read: () =>
          selected === "alice"
            ? new Promise((resolve) => (resolveOld = resolve))
            : Promise.resolve("bob data"),
        onData: (data) => seen.push(data),
        onError() {},
      },
    ],
    clock(),
  );
  const old = poller.refresh("cashflow");
  selected = "bob";
  const fresh = poller.refresh("cashflow");
  resolveOld("alice data");
  await Promise.all([old, fresh]);
  assert.deepEqual(seen, ["bob data"]);
});

test("three rapid selection changes still serialize the resource", async () => {
  let selected = "alice",
    release,
    active = 0,
    peak = 0;
  const seen = [];
  const poller = createPoller(
    [
      {
        name: "cashflow",
        key: () => selected,
        interval: 15000,
        read: async () => {
          active++;
          peak = Math.max(peak, active);
          const result =
            selected === "alice"
              ? await new Promise((resolve) => (release = resolve))
              : selected;
          active--;
          return result;
        },
        onData: (data) => seen.push(data),
        onError() {},
      },
    ],
    clock(),
  );
  const first = poller.refresh("cashflow");
  selected = "bob";
  const second = poller.refresh("cashflow");
  selected = "carol";
  const third = poller.refresh("cashflow");
  release("alice");
  await Promise.all([first, second, third]);
  assert.equal(peak, 1);
  assert.deepEqual(seen, ["carol"]);
});
