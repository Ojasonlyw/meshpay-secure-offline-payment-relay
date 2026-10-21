/** Owns views activity. */
import { focusPayment } from "./payments.js";
import { setState, getState } from "../state/store.js";
import { $, node, icon, renderIcons } from "../ui/dom.js";
import { dateTime, activityTime } from "../format/dates.js";

/** focusReference owns its existing dashboard behavior.
 * @param {*} item
 * @returns {*}
 */
export function focusReference(item) {
  if (item.type === "ROUTE") {
    location.hash = "#mesh";
    const target = [...document.querySelectorAll("[data-route-packet]")].find(
      (el) => el.dataset.routePacket === item.referenceId,
    );
    target?.focus();
  } else if (item.referenceId) focusPayment(item.referenceId);
}

/** renderActivity owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderActivity(data) {
  setState({ activity: data.items });
  const filtered = data.items.filter(
    (item) => !getState().activityType || item.type === getState().activityType,
  );
  $("log").replaceChildren(
    ...(filtered.length
      ? filtered.map((item) => {
          const row = node("li", "interactive-row");
          row.tabIndex = 0;
          row.dataset.activityReference = item.referenceId;
          row.dataset.activityType = item.type;
          const time = node(
            "time",
            "",
            dateTime.format(new Date(item.occurredAt)),
          );
          time.dateTime = item.occurredAt;
          row.append(
            time,
            node("span", "log-message", `${item.title}: ${item.description}`),
          );
          return row;
        })
      : [
          node(
            "li",
            "empty-state",
            data.items.length ? "No matching activity" : "No activity yet",
          ),
        ]),
  );
  $("recent-activity").replaceChildren(
    ...(data.items.length
      ? data.items.slice(0, 4).map((item) => {
          const row = node("button", "recent-item interactive-row");
          row.type = "button";
          row.dataset.activityReference = item.referenceId;
          row.dataset.activityType = item.type;
          const symbol = node("span", "recent-icon");
          symbol.append(
            icon(
              item.type === "SIGNATURE"
                ? "shield"
                : item.type === "ROUTE"
                  ? "radio-tower"
                  : "badge-check",
            ),
          );
          const copy = node("span", "recent-copy");
          copy.append(
            node("strong", "", item.title),
            node("small", "", item.description),
          );
          const time = node(
            "time",
            "recent-time",
            activityTime.format(new Date(item.occurredAt)),
          );
          time.dateTime = item.occurredAt;
          time.title = dateTime.format(new Date(item.occurredAt));
          row.append(symbol, copy, time);
          return row;
        })
      : [node("p", "empty-state", "No recent activity yet")]),
  );
  renderIcons();
}

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  const activate = (event) => {
    const row = event.target.closest("[data-activity-reference]");
    if (!row) return;
    const item = getState().activity.find(
      (item) =>
        String(item.referenceId) === row.dataset.activityReference &&
        item.type === row.dataset.activityType,
    );
    if (item) focusReference(item);
  };
  ["log", "recent-activity"].forEach((id) =>
    $(id).addEventListener("click", activate),
  );
  $("log").addEventListener("keydown", (event) => {
    if (event.key === "Enter") activate(event);
  });
  $("activity-type").addEventListener("change", (event) => {
    setState({ activityType: event.target.value });
    renderActivity({ items: getState().activity });
  });
}
