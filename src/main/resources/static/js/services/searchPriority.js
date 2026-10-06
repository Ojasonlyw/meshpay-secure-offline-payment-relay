/** Pure search resolution: payment, account, device, then transaction. */
/** @param {string} input @param {object} state @returns {{type:string,item:object}|null} */
export function resolveSearch(input, state) {
  const query = input.trim().toLowerCase();
  if (!query) return null;
  const contains = (value) => String(value).toLowerCase().includes(query);
  const candidates = [
    ["payment", state.payments.find((item) => contains(item.paymentId))],
    [
      "account",
      state.accounts.find(
        (item) => contains(item.vpa) || contains(item.holderName),
      ),
    ],
    ["device", state.mesh?.devices.find((item) => contains(item.deviceId))],
    [
      "transaction",
      state.transactions.find((item) =>
        [item.id, item.senderVpa, item.receiverVpa].some(contains),
      ),
    ],
  ];
  const result = candidates.find(([, item]) => item);
  return result ? { type: result[0], item: result[1] } : null;
}
