/** Owns views transactions. */
import {$, node, emptyRow} from '../ui/dom.js';
import {getState} from '../state/store.js';
import {statusStyles, statusLabels} from '../format/status.js';
import {money} from '../format/money.js';
import {isCount} from '../api/validators.js';
import {dateTime} from '../format/dates.js';
import {focusPayment} from './payments.js';

/** renderTransactions owns its existing dashboard behavior.

 * @returns {*}
 */
export function renderTransactions() {
    const query = $("tx-search").value.trim().toLowerCase();
    const status = $("tx-status").value;
    const filtered = getState().transactions.filter(
      (t) =>
        (!status || t.status === status) &&
        [t.id, t.senderVpa, t.receiverVpa].some((value) =>
          String(value).toLowerCase().includes(query),
        ),
    );
    const fragment = document.createDocumentFragment();
    filtered.forEach((tx) => {
      const row = node("tr");
      row.classList.add("interactive-row");
      row.tabIndex = 0;
      if (tx.paymentId) row.dataset.paymentId = tx.paymentId;
      const parties = node("td");
      parties.append(
        node("span", "transfer-party", tx.senderVpa),
        node("span", "transfer-party", tx.receiverVpa),
      );
      const state = node("td");
      state.append(
        node(
          "span",
          `badge ${statusStyles[tx.status] || "neutral"}`,
          statusLabels[tx.status] || "Unknown",
        ),
      );
      const settled = tx.settledAt ? new Date(tx.settledAt) : null;
      row.append(
        node("td", "", `#${tx.id}`),
        parties,
        node("td", "money", money(tx.amount)),
        state,
        node("td", "muted", tx.bridgeNodeId || "--"),
        node("td", "muted", isCount(tx.hopCount) ? tx.hopCount : "--"),
        node(
          "td",
          "muted",
          settled && !Number.isNaN(settled.getTime())
            ? dateTime.format(settled)
            : "--",
        ),
      );
      fragment.append(row);
    });
    if (!filtered.length)
      fragment.append(
        emptyRow(
          7,
          !getState().transactionsLoaded
            ? "Transactions unavailable"
            : getState().transactions.length
              ? "No matching transactions"
              : "No transactions yet",
        ),
      );
    document.querySelector("#tx-table tbody").replaceChildren(fragment);
  }

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
$("tx-search").addEventListener("input", renderTransactions);
$("tx-status").addEventListener("change", renderTransactions);
$("tx-table").addEventListener("click", event => {
    const row = event.target.closest("[data-payment-id]");
    if (row) focusPayment(row.dataset.paymentId);
  });
$("tx-table").addEventListener("keydown", event => {
    if(event.key==='Enter')event.target.closest('[data-payment-id]')?.click();
  });
}
