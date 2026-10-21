/** Owns views paymentForm. */
import { $ } from "../ui/dom.js";
import { getState } from "../state/store.js";
import { mutate } from "../services/mutations.js";
import { isText, isCount, isPaymentAmount } from "../api/validators.js";
import { notice } from "../ui/notice.js";

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  $("payment-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (getState().mutationPending) return;
    const amount = $("amount");
    const validAmount = isPaymentAmount(amount.value);
    amount.setCustomValidity(
      validAmount
        ? ""
        : "Enter 0.01 to 1000000.00 with up to two decimal places.",
    );
    if (!$("payment-form").reportValidity()) return;
    const body = {
      senderVpa: $("senderVpa").value,
      receiverVpa: $("receiverVpa").value,
      amount: Number(amount.value),
      pin: $("pin").value,
      ttl: 5,
    };
    $("pin").value = "";
    mutate("/api/demo/send", body, (data) => {
      if (
        !data ||
        !isText(data.packetId) ||
        !isText(data.injectedAt) ||
        !isCount(data.ttl)
      )
        throw new Error("Invalid send result");
      notice("Payment packet injected into the mesh.");
    });
  });
  $("amount").addEventListener("input", () =>
    $("amount").setCustomValidity(""),
  );
}
