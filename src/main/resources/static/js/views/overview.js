/** Owns views overview. */
import { renderPaymentSummary } from "./payments.js";
import { $ } from "../ui/dom.js";
import { money, compactMoney } from "../format/money.js";

/** renderSummary owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderSummary(data) {
  renderPaymentSummary(data);
  $("mesh-readiness").textContent = `${data.activeConnections} active links`;
  $("footer-bridges").textContent =
    `${data.bridgeDevices} bridge${data.bridgeDevices === 1 ? "" : "s"}`;
  $("total-balance").textContent = money(data.totalBalance);
  document
    .querySelectorAll("[data-assets-total]")
    .forEach((el) => (el.textContent = compactMoney(data.totalBalance)));
  $("device-count").textContent = data.activeDevices;
  $("bridge-count").textContent = data.bridgeDevices;
  $("cacheInfo").textContent =
    `Idempotency cache: ${data.idempotencyCacheSize}`;
  $("tx-count").textContent = data.totalTransactions;
  document.querySelector("[data-flow-bridge]").textContent =
    `BRIDGE ${data.bridgeDevices}`;
  document.querySelector("[data-flow-settled]").textContent =
    `SETTLED ${data.settledPayments}`;
  document.querySelector("[data-flow-cache]").textContent =
    `CACHE ${data.idempotencyCacheSize}`;
}
