/** Owns views accounts. */
import { setState, getState } from "../state/store.js";
import { $, node, emptyRow } from "../ui/dom.js";
import { money } from "../format/money.js";
import { refreshSection } from "../services/runtime.js";
import { choosePaymentAccounts } from "./accountSelections.js";

/** renderAccounts owns its existing dashboard behavior.
 * @param {*} accounts
 * @returns {*}
 */
export function renderAccounts(accounts) {
  const ordered = [...accounts].sort((a, b) => a.vpa.localeCompare(b.vpa));
  setState({ accounts: ordered });
  const selections = choosePaymentAccounts(ordered, {
    senderVpa: $("senderVpa").value,
    receiverVpa: $("receiverVpa").value,
  });
  ["senderVpa", "receiverVpa"].forEach((id) => {
    const select = $(id);
    select.replaceChildren(
      ...ordered.map((account) => {
        const option = node("option", "", account.vpa);
        option.value = account.vpa;
        return option;
      }),
    );
    select.value = selections[id];
    select.disabled = !ordered.length;
  });
  if (
    !getState().selectedAccount ||
    !ordered.some((a) => a.vpa === getState().selectedAccount)
  )
    setState({ selectedAccount: ordered[0]?.vpa || null });
  const fragment = document.createDocumentFragment();
  ordered.forEach((account, index) => {
    const row = node("tr");
    row.classList.add("interactive-row");
    row.tabIndex = 0;
    row.dataset.accountVpa = account.vpa;
    row.classList.toggle(
      "selected-row",
      account.vpa === getState().selectedAccount,
    );
    const nameCell = node("td");
    const holder = node("span", "holder");
    const initials = account.holderName
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word[0] || "")
      .join("")
      .toUpperCase();
    holder.append(
      node("span", `holder-avatar tone-${index % 4}`, initials),
      node("span", "", account.holderName),
    );
    nameCell.append(holder);
    row.append(
      nameCell,
      node("td", "muted", account.vpa),
      node("td", "money align-right", money(account.balance)),
    );
    fragment.append(row);
  });
  if (!accounts.length) fragment.append(emptyRow(3, "No accounts available"));
  document.querySelector("#accounts-table tbody").replaceChildren(fragment);
  $("account-count").textContent = `${accounts.length} accounts`;
  document.querySelectorAll("[data-flow-account]").forEach((el) => {
    const account = ordered[Number(el.dataset.flowAccount)];
    el.textContent = account ? account.holderName.toUpperCase() : "--";
  });
}

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  $("accounts-table").addEventListener("click", async (event) => {
    const row = event.target.closest("[data-account-vpa]");
    if (!row) return;
    setState({ selectedAccount: row.dataset.accountVpa });
    renderAccounts(getState().accounts);
    $("cashflow-heading").textContent = "Loading...";
    await refreshSection("cashflow");
    location.hash = "#analytics";
  });
  $("accounts-table").addEventListener("keydown", (event) => {
    if (event.key === "Enter")
      event.target.closest("[data-account-vpa]")?.click();
  });
}
