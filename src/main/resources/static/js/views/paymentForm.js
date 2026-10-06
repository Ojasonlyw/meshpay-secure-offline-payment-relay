/** Owns views paymentForm. */
import {$} from '../ui/dom.js';
import {getState} from '../state/store.js';
import {mutate} from '../services/mutations.js';
import {isText, isCount} from '../api/validators.js';
import {notice} from '../ui/notice.js';



/** Wire this module once after the document is ready. @returns {void} */
export function init() {
$("payment-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (getState().mutationPending) return;
    const amount = $("amount");
    const validAmount =
      /^\d+(\.\d{1,2})?$/.test(amount.value) &&
      Number(amount.value) > 0 &&
      Number.isFinite(Number(amount.value));
    amount.setCustomValidity(
      validAmount
        ? ""
        : "Enter a positive amount with up to two decimal places.",
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
