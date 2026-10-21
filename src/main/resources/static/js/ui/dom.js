/** Owns ui dom. */

/** @param {string} id Existing DOM hook. @returns {HTMLElement|null} */
export const $ = (id) => document.getElementById(id);

/** Preserve focus identity when a retained data row is reconstructed.
 * @param {Function} render DOM update. @returns {void}
 */
export function preserveFocus(render) {
  const active = document.activeElement;
  const key = [
    "accountVpa",
    "paymentId",
    "deviceId",
    "routePacket",
    "activityReference",
  ].find((key) => active?.dataset?.[key]);
  const value = key ? active.dataset[key] : null;
  const container = active?.closest("[id]");
  render();
  if (key && !active.isConnected && container) {
    const match = [...container.querySelectorAll("[tabindex], button")].find(
      (el) => el.dataset[key] === value,
    );
    match?.focus({ preventScroll: true });
  }
}

/** node owns its existing dashboard behavior.
 * @param {*} tag
 * @param {*} className
 * @param {*} value
 * @returns {*}
 */
export function node(tag, className, value) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (value !== undefined) element.textContent = String(value);
  return element;
}

/** icon owns its existing dashboard behavior.
 * @param {*} name
 * @returns {*}
 */
export function icon(name) {
  const element = node("i");
  element.dataset.lucide = name;
  element.setAttribute("aria-hidden", "true");
  return element;
}

/** renderIcons owns its existing dashboard behavior.

 * @returns {*}
 */
export function renderIcons() {
  window.lucide?.createIcons({ attrs: { "aria-hidden": "true" } });
}

/** emptyRow owns its existing dashboard behavior.
 * @param {*} columns
 * @param {*} message
 * @returns {*}
 */
export function emptyRow(columns, message) {
  const row = node("tr");
  const cell = node("td", "empty-state", message);
  cell.colSpan = columns;
  row.append(cell);
  return row;
}
