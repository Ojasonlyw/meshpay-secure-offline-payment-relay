/** Owns ui dialogs. */
import {$} from './dom.js';
import {mutate} from '../services/mutations.js';
import {isText} from '../api/validators.js';
import {notice} from './notice.js';



/** Wire this module once after the document is ready. @returns {void} */
export function init() {
$("reset").addEventListener("click", () => $("reset-dialog").showModal());
$("reset-cancel").addEventListener("click", () => $("reset-dialog").close());
$("reset-confirm").addEventListener("click", () => {
    $("reset-dialog").close();
    mutate("/api/mesh/reset", null, (data) => {
      if (!data || !isText(data.status))
        throw new Error("Invalid reset result");
      notice("Mesh packets and idempotency cache cleared.");
    });
  });
$("add-route").addEventListener("click", () => $("route-dialog").showModal());
$("route-cancel").addEventListener("click", () => $("route-dialog").close());
$("route-form").addEventListener("submit", event => {
    event.preventDefault();
    if ($("route-source").value === $("route-target").value) {
      $("route-target").setCustomValidity("Choose a different destination device.");
      $("route-form").reportValidity();
      return;
    }
    $("route-target").setCustomValidity("");
    $("route-dialog").close();
    mutate("/api/mesh/connections", {sourceDeviceId: $("route-source").value,
      targetDeviceId: $("route-target").value, status: "ACTIVE", linkType: "BLUETOOTH"},
    data => { notice(`Route ${data.sourceDeviceId} → ${data.targetDeviceId} active.`); });
  });
$("route-target").addEventListener("change", () => $("route-target").setCustomValidity(""));
$("route-source").addEventListener("change", () => $("route-target").setCustomValidity(""));
}
