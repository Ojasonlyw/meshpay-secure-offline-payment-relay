/** Owns api validators. */


export const isText = (value) => typeof value === "string";

export const isNumber = (value) =>
    (typeof value === "number" ||
      (typeof value === "string" && value.trim() !== "")) &&
    Number.isFinite(Number(value));

export const isCount = (value) => Number.isInteger(value) && value >= 0;

/** validateMesh owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function validateMesh(data) {
    if (
      !data ||
      !Array.isArray(data.devices) ||
      !isCount(data.idempotencyCacheSize) ||
      !data.devices.every(
        (d) =>
          d &&
          isText(d.deviceId) &&
          typeof d.hasInternet === "boolean" &&
          isCount(d.packetCount) &&
          Array.isArray(d.packetIds) &&
          d.packetIds.every(isText),
      )
    )
      throw new Error("Invalid mesh data");
    return data;
  }

/** validateAccounts owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function validateAccounts(data) {
    if (
      !Array.isArray(data) ||
      !data.every(
        (a) =>
          a && isText(a.vpa) && isText(a.holderName) && isNumber(a.balance),
      )
    )
      throw new Error("Invalid accounts data");
    return data;
  }

/** validateTransactions owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function validateTransactions(data) {
    if (
      !Array.isArray(data) ||
      !data.every(
        (t) =>
          t &&
          isNumber(t.id) &&
          isText(t.senderVpa) &&
          isText(t.receiverVpa) &&
          isNumber(t.amount) &&
          isText(t.status) &&
          (t.paymentId == null || isText(t.paymentId)) &&
          (t.bridgeNodeId == null || isText(t.bridgeNodeId)) &&
          (t.settledAt == null || (isText(t.settledAt) && Number.isFinite(Date.parse(t.settledAt)))) &&
          (t.hopCount == null || isCount(t.hopCount)),
      )
    )
      throw new Error("Invalid transaction data");
    return data;
  }

/** validatePayments owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function validatePayments(data) {
    if (!Array.isArray(data) || !data.every(p => p && isText(p.paymentId) &&
        isText(p.senderVpa) && isText(p.receiverVpa) && isNumber(p.amount) &&
        isText(p.status) && (p.failureMessage == null || isText(p.failureMessage)) && isText(p.receivedAt) && Number.isFinite(Date.parse(p.receivedAt)))) {
      throw new Error("Invalid payment data");
    }
    return data;
  }

/** validatePaymentSummary owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function validatePaymentSummary(data) {
    const statuses = ["PENDING", "PROCESSING", "SETTLED", "REJECTED", "FAILED", "EXPIRED"];
    if (!data || !data.paymentStatusCounts ||
        typeof data.reconciliationBalanced !== "boolean" ||
        !isCount(data.reconciliationAccountsChecked) ||
        !isCount(data.reconciliationMismatchedAccounts) ||
        !statuses.every(status => isCount(data.paymentStatusCounts[status]))) {
      throw new Error("Invalid lifecycle counts");
    }
    return data;
  }
