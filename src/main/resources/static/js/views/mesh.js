/** Owns views mesh. */
import {setState, getState} from '../state/store.js';
import {node, icon, $, renderIcons} from '../ui/dom.js';
import {dateTime} from '../format/dates.js';
import {notice} from '../ui/notice.js';
import {focusPayment} from './payments.js';
import {mutate} from '../services/mutations.js';
import {isCount, isText} from '../api/validators.js';

/** renderMesh owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderMesh(data) {
    setState({mesh: data});
    const devices = [...data.devices].sort(
      (a, b) =>
        Number(a.hasInternet) - Number(b.hasInternet) ||
        a.deviceId.localeCompare(b.deviceId),
    );
    const fragment = document.createDocumentFragment();
    devices.forEach((device) => {
      const row = node("div", `device${device.hasInternet ? " bridge" : ""}`);
      row.classList.add("interactive-row");
      row.tabIndex = 0;
      row.setAttribute("role", "button");
      row.dataset.deviceId = device.deviceId;
      row.classList.toggle("selected-row", getState().selectedDevice === device.deviceId);
      const symbol = node("span", "device-icon");
      symbol.append(icon(device.hasInternet ? "radio-tower" : "smartphone"));
      const body = node("div", "device-body");
      const top = node("div", "device-top");
      top.append(
        node("span", "device-name", device.deviceId),
        node(
          "span",
          `badge ${device.hasInternet ? "success" : "neutral"}`,
          device.hasInternet ? "4G bridge" : "Offline",
        ),
      );
      const packets = node("div", "device-packets");
      packets.append(
        node(
          "span",
          "",
          `${device.packetCount} packet${device.packetCount === 1 ? "" : "s"}`,
        ),
      );
      device.packetIds.forEach((id) =>
        packets.append(node("span", "packet-id", id)),
      );
      body.append(top, packets);
      row.append(symbol, body);
      fragment.append(row);
    });
    if (!devices.length)
      fragment.append(node("p", "empty-state", "No mesh devices"));
    $("devices").replaceChildren(fragment);
    $("devices").setAttribute("aria-busy", "false");
    $("mesh-summary").textContent = `${devices.length} devices`;
    [$("route-source"), $("route-target")].forEach(select => {
      const current = select.value;
      select.replaceChildren(...devices.map(device => node("option", "", device.deviceId)));
      [...select.options].forEach(option => option.value = option.textContent);
      if (devices.some(device => device.deviceId === current)) select.value = current;
    });
    renderIcons();
  }

/** renderNetwork owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderNetwork(data) {
    setState({network: data});
    $("network-stats").textContent = `${data.activeConnections} active links · ${data.packetsRouted} routes · ${data.averageHopCount.toFixed(1)} average hops · ${data.trustedDevices} trusted · ${data.revokedDevices} revoked`;
    $("network-stats").title = data.latestRouteTimestamp
      ? `Latest route ${dateTime.format(new Date(data.latestRouteTimestamp))} · Most active bridge ${data.mostActiveBridgeDevice || "None"}`
      : "No packet routes yet";
    $("packet-count").textContent = data.packetsRouted;
  }

/** renderRoutes owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderRoutes(data) {
    setState({routes: data});
    const visible = data.filter(route => !getState().selectedDevice ||
      route.sourceDeviceId === getState().selectedDevice || route.destinationDeviceId === getState().selectedDevice);
    $("route-history").replaceChildren(node("strong", "", "Recent routes"),
      ...(visible.length ? visible.slice(0, 5).map(route => {
        const row = node("button", "history-item interactive-row", `${route.sourceDeviceId} → ${route.destinationDeviceId} · hop ${route.hopNumber}`);
        row.type = "button";
        row.dataset.routePacket = route.packetId;
        row.dataset.routeSource = route.sourceDeviceId;
        row.dataset.routeTarget = route.destinationDeviceId;
        row.title = `Packet ${route.packetId} · TTL ${route.ttlAfterHop} · ${dateTime.format(new Date(route.receivedAt))}`;
        return row;
      }) : [node("p", "empty-state", data.length ? "No routes for this device" : "No routed packets yet")]));
  }

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
  $("route-history").addEventListener("click",event=>{
    const row=event.target.closest("[data-route-packet]");if(!row)return;
    const route=getState().routes.find(route=>route.packetId===row.dataset.routePacket&&route.sourceDeviceId===row.dataset.routeSource&&route.destinationDeviceId===row.dataset.routeTarget);
    notice(row.title);if(route?.paymentId)focusPayment(route.paymentId);
  });
$("gossip").addEventListener("click", () =>
    mutate("/api/mesh/gossip", null, (data) => {
      if (
        !data ||
        !isCount(data.transfers) ||
        !data.deviceCounts ||
        typeof data.deviceCounts !== "object"
      )
        throw new Error("Invalid gossip result");
      notice(`Gossip complete. ${data.transfers} packet transfer(s).`);
    }),
  );
$("flush").addEventListener("click", () =>
    mutate("/api/mesh/flush", null, (data) => {
      if (
        !data ||
        !isCount(data.uploadsAttempted) ||
        !Array.isArray(data.results) ||
        !data.results.every(
          (result) =>
            result &&
            isText(result.bridgeNode) &&
            isText(result.packetId) &&
            isText(result.outcome),
        )
      )
        throw new Error("Invalid upload result");
      notice(
        `Bridge upload complete. ${data.uploadsAttempted} upload(s) attempted. ${[...new Set(data.results.map(result=>result.outcome))].join(", ")}`,
      );
    }),
  );
$("devices").addEventListener("click", event => {
    const row = event.target.closest("[data-device-id]");
    if (!row) return;
    setState({selectedDevice: getState().selectedDevice === row.dataset.deviceId ? null : row.dataset.deviceId});
    renderMesh(getState().mesh);
    renderRoutes(getState().routes);
  });
$("devices").addEventListener("keydown", event => {
    if (event.key === "Enter") event.target.closest("[data-device-id]")?.click();
  });
}
