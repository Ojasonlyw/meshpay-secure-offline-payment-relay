/** Resolve API-backed choices without replacing a user's still-valid selection. */
function choose(accounts, previous, preferred, excluded = null) {
  if (accounts.some((account) => account.vpa === previous)) return previous;
  if (
    preferred !== excluded &&
    accounts.some((account) => account.vpa === preferred)
  )
    return preferred;
  return (
    accounts.find((account) => account.vpa !== excluded)?.vpa ||
    accounts[0]?.vpa ||
    ""
  );
}
/** @param {Array<object>} accounts @param {object} previous Existing selected VPAs. @returns {object} */
export function choosePaymentAccounts(accounts, previous) {
  const receiver = accounts.some(
    (account) => account.vpa === previous.receiverVpa,
  )
    ? previous.receiverVpa
    : "bob@demo";
  const senderVpa = choose(
    accounts,
    previous.senderVpa,
    "alice@demo",
    receiver,
  );
  return {
    senderVpa,
    receiverVpa: choose(accounts, previous.receiverVpa, "bob@demo", senderVpa),
  };
}
