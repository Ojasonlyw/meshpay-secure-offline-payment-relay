/** Owns views payments. */
import { setState, getState } from "../state/store.js";
import { emptyRow, node, $ } from "../ui/dom.js";
import { statusStyles, statusLabels } from "../format/status.js";
import { money } from "../format/money.js";
import { dateTime } from "../format/dates.js";
import { notice } from "../ui/notice.js";

/** renderPayments owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderPayments(data) {
  setState({ payments: data });
  const body = document.querySelector("#payments-table tbody");
  body.replaceChildren();
  const filtered = data.filter(
    (p) => !getState().paymentStatus || p.status === getState().paymentStatus,
  );
  if (!filtered.length) {
    body.append(
      emptyRow(
        6,
        data.length ? "No matching payments" : "No payments received",
      ),
    );
    return;
  }
  filtered.forEach((payment) => {
    const row = node("tr");
    row.classList.add("interactive-row");
    row.tabIndex = 0;
    row.dataset.paymentId = payment.paymentId;
    const id = node("td", "payment-id", payment.paymentId.slice(0, 8));
    id.title = payment.paymentId;
    const parties = node("td");
    parties.append(
      node("div", "", payment.senderVpa),
      node("div", "", payment.receiverVpa),
    );
    const status = node("td");
    status.append(
      node(
        "span",
        `badge ${statusStyles[payment.status] || "neutral"}`,
        statusLabels[payment.status] || payment.status,
      ),
    );
    row.append(
      id,
      parties,
      node("td", "", money(payment.amount)),
      status,
      node("td", "", dateTime.format(new Date(payment.receivedAt))),
      node("td", "payment-failure", payment.failureMessage || "--"),
    );
    body.append(row);
  });
}

/** renderPaymentSummary owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderPaymentSummary(data) {
  const reconciliation = $("reconciliation-summary");
  reconciliation.textContent = data.reconciliationBalanced
    ? `Reconciled: ${data.reconciliationAccountsChecked} accounts, no mismatches`
    : `Balance mismatch: ${data.reconciliationMismatchedAccounts} of ${data.reconciliationAccountsChecked} accounts`;
  reconciliation.className = `badge ${data.reconciliationBalanced ? "success" : "error"}`;
  const counts = $("payment-counts");
  counts.replaceChildren();
  ["PENDING", "PROCESSING", "SETTLED", "REJECTED", "FAILED", "EXPIRED"].forEach(
    (status) => {
      const item = node("div", "payment-count");
      item.classList.add("clickable-metric");
      item.tabIndex = 0;
      item.setAttribute("role", "button");
      item.dataset.status = status;
      item.classList.toggle(
        "active-filter",
        getState().paymentStatus === status,
      );
      item.setAttribute("aria-label", `Show ${statusLabels[status]} payments`);
      item.append(
        node("span", "", statusLabels[status]),
        node("strong", "", data.paymentStatusCounts[status]),
      );
      counts.append(item);
    },
  );
}

/** focusPayment owns its existing dashboard behavior.
 * @param {*} id
 * @returns {*}
 */
export function focusPayment(id) {
  location.hash = "#payments-heading";
  const row = [
    ...document.querySelectorAll("#payments-table [data-payment-id]"),
  ].find((el) => el.dataset.paymentId === id);
  if (row) {
    row.focus();
    row.scrollIntoView({ block: "center", behavior: "smooth" });
  } else notice(`Payment ${id} is outside the recent list.`);
}

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  $("payment-counts").addEventListener("click", (event) => {
    const metric = event.target.closest("[data-status]");
    if (!metric) return;
    setState({
      paymentStatus:
        getState().paymentStatus === metric.dataset.status
          ? ""
          : metric.dataset.status,
    });
    $("payment-filter-clear").hidden = !getState().paymentStatus;
    renderPayments(getState().payments);
    document
      .querySelectorAll("#payment-counts [data-status]")
      .forEach((el) =>
        el.classList.toggle(
          "active-filter",
          el.dataset.status === getState().paymentStatus,
        ),
      );
    document
      .querySelector("#payments-table")
      .scrollIntoView({ block: "nearest", behavior: "smooth" });
  });
  $("payment-counts").addEventListener("keydown", (event) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      event.target.click();
    }
  });
  $("payment-filter-clear").addEventListener("click", () => {
    setState({ paymentStatus: "" });
    $("payment-filter-clear").hidden = true;
    renderPayments(getState().payments);
    document
      .querySelectorAll("#payment-counts [data-status]")
      .forEach((el) => el.classList.remove("active-filter"));
  });
  $("payments-table").addEventListener("click", (event) => {
    const row = event.target.closest("[data-payment-id]");
    if (row) {
      row.focus();
      notice(
        `Payment ${row.dataset.paymentId} · ${row.cells[3].textContent.trim()} · ${row.cells[5].textContent.trim()}`,
      );
    }
  });
  $("payments-table").addEventListener("keydown", (event) => {
    if (event.key === "Enter")
      event.target.closest("[data-payment-id]")?.click();
  });
}
