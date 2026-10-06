/** Owns services search. */
import { resolveSearch } from "./searchPriority.js";
import { $ } from "../ui/dom.js";
import { getState } from "../state/store.js";
import { focusPayment } from "../views/payments.js";
import { renderTransactions } from "../views/transactions.js";
import { notice } from "../ui/notice.js";

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  $("command-search").addEventListener("keydown", (event) => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    const query = event.target.value.trim().toLowerCase();
    if (!query) return;
    const result = resolveSearch(query, getState());
    const payment = result?.type === "payment" ? result.item : null;
    const account = result?.type === "account" ? result.item : null;
    const device = result?.type === "device" ? result.item : null;
    const transaction = result?.type === "transaction" ? result.item : null;
    if (payment) focusPayment(payment.paymentId);
    else if (account) {
      location.hash = "#accounts";
      [...document.querySelectorAll("[data-account-vpa]")]
        .find((el) => el.dataset.accountVpa === account.vpa)
        ?.focus();
    } else if (device) {
      location.hash = "#mesh";
      [...document.querySelectorAll("[data-device-id]")]
        .find((el) => el.dataset.deviceId === device.deviceId)
        ?.focus();
    } else if (transaction) {
      $("tx-search").value = query;
      renderTransactions();
      location.hash = "#transactions";
    } else notice(`No dashboard result for “${event.target.value.trim()}”.`);
  });
  document.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      if (document.getElementById("sidebar").classList.contains("open")) return;
      event.preventDefault();
      $("command-search").focus();
    }
  });
}
