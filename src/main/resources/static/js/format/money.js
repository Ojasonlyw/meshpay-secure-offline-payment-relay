/** Owns format money. */


export const currency = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
  });

/** money owns its existing dashboard behavior.
 * @param {*} value
 * @returns {*}
 */
export function money(value) {
    return Number.isFinite(Number(value))
      ? currency.format(Number(value))
      : "--";
  }

/** compactMoney owns its existing dashboard behavior.
 * @param {*} value
 * @returns {*}
 */
export function compactMoney(value) {
    if (!Number.isFinite(Number(value))) return "--";
    return currency.format(Number(value)).replace(".00", "");
  }
