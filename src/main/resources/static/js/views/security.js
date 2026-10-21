/** Owns views security. */
import { setState, getState } from "../state/store.js";
import { $, node } from "../ui/dom.js";
import { dateTime } from "../format/dates.js";
import { focusPayment } from "./payments.js";
import { notice } from "../ui/notice.js";

/** renderSecurity owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderSecurity(data) {
  setState({ security: data });
  $("security-events").replaceChildren(
    node("strong", "", `Security events · ${data.failedSignatureVerification}`),
    ...(data.items.length
      ? data.items.map((event) => {
          const row = node(
            "button",
            "history-item interactive-row",
            `${event.eventType} · ${event.deviceId || "Unknown device"}`,
          );
          row.type = "button";
          row.dataset.securityIndex = data.items.indexOf(event);
          row.title = `${event.message} · ${dateTime.format(new Date(event.occurredAt))}`;
          return row;
        })
      : [node("p", "empty-state", "No security failures recorded")]),
  );
}

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  $("security-events").addEventListener("click", (event) => {
    const row = event.target.closest("[data-security-index]");
    if (!row) return;
    const item = getState().security.items[Number(row.dataset.securityIndex)];
    if (item.paymentId) focusPayment(item.paymentId);
    else notice(row.title);
  });
  $("notifications-button").addEventListener("click", () => {
    location.hash = "#activity";
    $("activity-type").value = "SIGNATURE";
    $("activity-type").dispatchEvent(new Event("change"));
    notice(
      getState().security?.items.length
        ? `${getState().security.items.length} recent security event(s).`
        : "No security failures recorded.",
    );
  });
}
