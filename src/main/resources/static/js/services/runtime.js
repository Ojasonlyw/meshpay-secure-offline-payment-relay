/** Connect API resources to views and summarize connection freshness. */
import * as api from "../api/endpoints.js";
import * as validators from "../api/validators.js";
import * as dashboard from "../api/dashboardValidators.js";
import { POLL_INTERVALS } from "../config.js";
import { getState, setState } from "../state/store.js";
import { $, node, emptyRow, preserveFocus } from "../ui/dom.js";
import { renderAccounts } from "../views/accounts.js";
import { renderPayments } from "../views/payments.js";
import { renderSummary } from "../views/overview.js";
import { renderMesh, renderNetwork, renderRoutes } from "../views/mesh.js";
import { renderTransactions } from "../views/transactions.js";
import { renderCashflow, renderVolume } from "../views/analytics.js";
import { renderActivity } from "../views/activity.js";
import { renderSecurity } from "../views/security.js";
import { createPoller } from "./polling.js";
const definitions = [
  ["accounts", api.getAccounts, validators.validateAccounts, renderAccounts],
  ["payments", api.getPayments, validators.validatePayments, renderPayments],
  ["summary", api.getSummary, dashboard.validateSummary, renderSummary],
  ["mesh", api.getMesh, validators.validateMesh, renderMesh],
  [
    "transactions",
    api.getTransactions,
    validators.validateTransactions,
    (data) => {
      setState({ transactions: data, transactionsLoaded: true });
      renderTransactions();
    },
  ],
  [
    "cashflow",
    (signal) => api.getCashflow(getState().selectedAccount, signal),
    dashboard.validateCashflow,
    renderCashflow,
  ],
  ["activity", api.getActivity, dashboard.validateActivity, renderActivity],
  ["network", api.getNetwork, dashboard.validateNetwork, renderNetwork],
  ["volume", api.getVolume, dashboard.validateVolume, renderVolume],
  ["security", api.getSecurity, dashboard.validateSecurity, renderSecurity],
  ["routes", api.getRoutes, dashboard.validateRoutes, renderRoutes],
];
const signatures = new Map();
function sectionStatus(name, patch) {
  const sections = getState().sections;
  setState({
    sections: { ...sections, [name]: { ...sections[name], ...patch } },
  });
}
function connection() {
  const sections = Object.values(getState().sections);
  const failed = sections.filter((section) => section.error).length;
  const text =
    sections.length < definitions.length
      ? "Connecting"
      : failed === definitions.length
        ? "Disconnected"
        : failed
          ? "Partial update"
          : "Live connection";
  $("connection").classList.toggle("stale", failed > 0);
  $("connection-text").textContent = text;
  $("footer-connection").textContent = text;
  if (text === "Live connection")
    $("last-updated").textContent =
      `Updated ${new Date().toLocaleTimeString("en-IN")}`;
}
const resources = definitions.map(([name, read, validate, render]) => {
  const errorName = name === "summary" ? "payment-summary" : name;
  const element = () => $(errorName + "-error");
  return {
    name,
    interval: POLL_INTERVALS[name],
    key: () => (name === "cashflow" ? getState().selectedAccount : name),
    read: async (signal) => {
      element().closest("article, section")?.setAttribute("aria-busy", "true");
      return validate(await read(signal));
    },
    onData: (data) => {
      const signature = JSON.stringify(data);
      if (
        signatures.get(name) !== signature ||
        getState().sections[name]?.error ||
        name === "cashflow"
      ) {
        preserveFocus(() => render(data));
        signatures.set(name, signature);
      }
      if (name === "summary") setState({ summary: data });
      sectionStatus(name, { loaded: true, error: null, updatedAt: Date.now() });
      element().hidden = true;
      element().textContent = "";
    },
    onError: (error) => {
      const loaded = getState().sections[name]?.loaded;
      sectionStatus(name, { error });
      element().hidden = false;
      element().textContent =
        (loaded
          ? "Update unavailable. Previously loaded data may be out of date."
          : "Unable to load data. Retrying automatically.") +
        (error.traceId ? " Trace: " + error.traceId : "");
      if (name === "summary") {
        $("reconciliation-summary").textContent = "Reconciliation unavailable";
        $("reconciliation-summary").className = "badge neutral";
      }
      if (!loaded) {
        if (name === "mesh") {
          $("devices").replaceChildren(
            node("p", "empty-state", "Mesh devices unavailable"),
          );
          $("mesh-summary").textContent = "Unavailable";
        }
        if (name === "accounts") {
          document
            .querySelector("#accounts-table tbody")
            .replaceChildren(emptyRow(3, "Accounts unavailable"));
          $("account-count").textContent = "Unavailable";
          $("senderVpa").disabled = true;
          $("receiverVpa").disabled = true;
        }
        if (name === "payments")
          document
            .querySelector("#payments-table tbody")
            .replaceChildren(emptyRow(6, "Payments unavailable"));
        if (name === "transactions") renderTransactions();
      }
    },
    onSettled: () => {
      element().closest("article, section")?.setAttribute("aria-busy", "false");
      $("devices").setAttribute("aria-busy", "false");
      connection();
    },
  };
});
const poller = createPoller(resources);
/** @returns {void} Stop polling and cancel pending reads. */
export const pause = () => poller.pause();
/** @returns {void} Permit periodic scheduling. */
export const resume = () => poller.resume();
/** @returns {Promise<void>} Wait for reads to settle. */
export const drain = async () => {
  await poller.drain();
  await refreshPending;
};
/** @param {string} name @returns {Promise<void>} */
export const refreshSection = (name) => poller.refresh(name);
let refreshPending = null;
/** @returns {Promise<void>} Full refresh, ordered to resolve account-dependent cashflow. */
export async function refresh() {
  if (getState().mutationPending || document.hidden) return;
  if (refreshPending) return refreshPending;
  $("refresh").disabled = true;
  $("refresh").classList.add("spinning");
  refreshPending = (async () => {
    await poller.refresh("accounts");
    if (getState().mutationPending || document.hidden) return;
    await Promise.all(
      definitions
        .filter(([name]) => name !== "accounts")
        .map(([name]) => poller.refresh(name)),
    );
  })();
  try {
    await refreshPending;
  } finally {
    refreshPending = null;
    $("refresh").disabled = getState().mutationPending;
    $("refresh").classList.remove("spinning");
  }
}
/** @returns {Promise<void>} Attach visibility handling and start initial reads. */
export async function start() {
  $("refresh").addEventListener("click", refresh);
  document.addEventListener("visibilitychange", async () => {
    pause();
    if (!document.hidden && !getState().mutationPending) {
      await drain();
      if (document.hidden || getState().mutationPending) return;
      resume();
      await refresh();
    }
  });
  window.addEventListener("pagehide", pause);
  if (!document.hidden) {
    resume();
    await refresh();
  }
}
