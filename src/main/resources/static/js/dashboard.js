(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const currency = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
  });
  const dateTime = new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  });
  const chartDate = new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", timeZone: "UTC",
  });
  const activityTime = new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", hour: "numeric", minute: "2-digit",
  });
  const statusStyles = {
    PENDING: "neutral",
    PROCESSING: "warning",
    FAILED: "error",
    EXPIRED: "warning",
    SETTLED: "success",
    REJECTED: "error",
    DUPLICATE: "warning",
    INVALID: "error",
  };
  const statusLabels = {
    PENDING: "Pending",
    PROCESSING: "Processing",
    FAILED: "Failed",
    EXPIRED: "Expired",
    SETTLED: "Settled",
    REJECTED: "Rejected",
    DUPLICATE: "Duplicate",
    INVALID: "Invalid",
  };
  const state = { accounts: [], payments: [], transactions: [], activity: [], cashflow: null,
    volume: null, network: null, security: null, routes: [], selectedAccount: null,
    paymentStatus: "", activityType: "", range: "all", selectedDevice: null, mesh: null };
  let transactions = state.transactions;
  let transactionsLoaded = false;
  let mutationPending = false;
  let refreshPending = null;
  let pollTimer;
  const loadedSections = new Set();

  function node(tag, className, value) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (value !== undefined) element.textContent = String(value);
    return element;
  }

  function icon(name) {
    const element = node("i");
    element.dataset.lucide = name;
    element.setAttribute("aria-hidden", "true");
    return element;
  }

  function renderIcons() {
    window.lucide?.createIcons({ attrs: { "aria-hidden": "true" } });
  }

  function money(value) {
    return Number.isFinite(Number(value))
      ? currency.format(Number(value))
      : "--";
  }

  function compactMoney(value) {
    if (!Number.isFinite(Number(value))) return "--";
    return currency.format(Number(value)).replace(".00", "");
  }

  function notice(message, failed = false) {
    $("notice").textContent = message;
    $("notice").classList.toggle("failure", failed);
    $("notice").hidden = false;
  }

  async function request(path, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 12000);
    try {
      const response = await fetch(path, {
        ...options,
        signal: controller.signal,
        cache: "no-store",
      });
      if (!response.ok) throw new Error("Request failed");
      return await response.json();
    } finally {
      clearTimeout(timer);
    }
  }

  const isText = (value) => typeof value === "string";
  const isNumber = (value) =>
    (typeof value === "number" ||
      (typeof value === "string" && value.trim() !== "")) &&
    Number.isFinite(Number(value));
  const isCount = (value) => Number.isInteger(value) && value >= 0;

  function validateMesh(data) {
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

  function validateAccounts(data) {
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

  function validateTransactions(data) {
    if (
      !Array.isArray(data) ||
      !data.every(
        (t) =>
          t &&
          isNumber(t.id) &&
          isText(t.senderVpa) &&
          isText(t.receiverVpa) &&
          isNumber(t.amount) &&
          isText(t.status),
      )
    )
      throw new Error("Invalid transaction data");
    return data;
  }

  function validatePayments(data) {
    if (!Array.isArray(data) || !data.every(p => p && isText(p.paymentId) &&
        isText(p.senderVpa) && isText(p.receiverVpa) && isNumber(p.amount) &&
        isText(p.status) && Number.isFinite(Date.parse(p.receivedAt)))) {
      throw new Error("Invalid payment data");
    }
    return data;
  }

  function renderPayments(data) {
    state.payments = data;
    const body = document.querySelector("#payments-table tbody");
    body.replaceChildren();
    const filtered = data.filter(p => !state.paymentStatus || p.status === state.paymentStatus);
    if (!filtered.length) { body.append(emptyRow(6, data.length ? "No matching payments" : "No payments received")); return; }
    filtered.forEach(payment => {
      const row = node("tr");
      row.classList.add("interactive-row");
      row.tabIndex = 0;
      row.dataset.paymentId = payment.paymentId;
      const id = node("td", "payment-id", payment.paymentId.slice(0, 8));
      id.title = payment.paymentId;
      const parties = node("td");
      parties.append(node("div", "", payment.senderVpa), node("div", "", payment.receiverVpa));
      const status = node("td");
      status.append(node("span", `badge ${statusStyles[payment.status] || "neutral"}`,
        statusLabels[payment.status] || payment.status));
      row.append(id, parties, node("td", "", money(payment.amount)), status,
        node("td", "", dateTime.format(new Date(payment.receivedAt))),
        node("td", "payment-failure", payment.failureMessage || "--"));
      body.append(row);
    });
  }

  function validatePaymentSummary(data) {
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

  function renderPaymentSummary(data) {
    const reconciliation = $("reconciliation-summary");
    reconciliation.textContent = data.reconciliationBalanced
      ? `Reconciled: ${data.reconciliationAccountsChecked} accounts, no mismatches`
      : `Balance mismatch: ${data.reconciliationMismatchedAccounts} of ${data.reconciliationAccountsChecked} accounts`;
    reconciliation.className = `badge ${data.reconciliationBalanced ? "success" : "error"}`;
    const counts = $("payment-counts");
    counts.replaceChildren();
    ["PENDING", "PROCESSING", "SETTLED", "REJECTED", "FAILED", "EXPIRED"].forEach(status => {
      const item = node("div", "payment-count");
      item.classList.add("clickable-metric");
      item.tabIndex = 0;
      item.setAttribute("role", "button");
      item.dataset.status = status;
      item.classList.toggle("active-filter", state.paymentStatus === status);
      item.setAttribute("aria-label", `Show ${statusLabels[status]} payments`);
      item.append(node("span", "", statusLabels[status]),
        node("strong", "", data.paymentStatusCounts[status]));
      counts.append(item);
    });
  }

  function renderMesh(data) {
    state.mesh = data;
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
      row.classList.toggle("selected-row", state.selectedDevice === device.deviceId);
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

  function emptyRow(columns, message) {
    const row = node("tr");
    const cell = node("td", "empty-state", message);
    cell.colSpan = columns;
    row.append(cell);
    return row;
  }

  function renderAccounts(accounts) {
    const ordered = [...accounts].sort((a, b) => a.vpa.localeCompare(b.vpa));
    state.accounts = ordered;
    if (!state.selectedAccount || !ordered.some(a => a.vpa === state.selectedAccount))
      state.selectedAccount = ordered[0]?.vpa || null;
    const fragment = document.createDocumentFragment();
    ordered.forEach((account, index) => {
      const row = node("tr");
      row.classList.add("interactive-row");
      row.tabIndex = 0;
      row.dataset.accountVpa = account.vpa;
      row.classList.toggle("selected-row", account.vpa === state.selectedAccount);
      const nameCell = node("td");
      const holder = node("span", "holder");
      const initials = account.holderName
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((word) => word[0] || "")
        .join("")
        .toUpperCase();
      holder.append(
        node("span", `holder-avatar tone-${index % 4}`, initials),
        node("span", "", account.holderName),
      );
      nameCell.append(holder);
      row.append(
        nameCell,
        node("td", "muted", account.vpa),
        node("td", "money align-right", money(account.balance)),
      );
      fragment.append(row);
    });
    if (!accounts.length) fragment.append(emptyRow(3, "No accounts available"));
    document.querySelector("#accounts-table tbody").replaceChildren(fragment);
    $("account-count").textContent = `${accounts.length} accounts`;
    document.querySelectorAll("[data-flow-account]").forEach(el => {
      const account = ordered[Number(el.dataset.flowAccount)];
      el.textContent = account ? account.holderName.toUpperCase() : "--";
    });
  }

  function renderSummary(data) {
    renderPaymentSummary(data);
    $("total-balance").textContent = money(data.totalBalance);
    document.querySelectorAll("[data-assets-total]").forEach(el => el.textContent = compactMoney(data.totalBalance));
    $("device-count").textContent = data.activeDevices;
    $("bridge-count").textContent = data.bridgeDevices;
    $("cacheInfo").textContent = `Idempotency cache: ${data.idempotencyCacheSize}`;
    $("tx-count").textContent = data.totalTransactions;
    document.querySelector("[data-flow-bridge]").textContent = `BRIDGE ${data.bridgeDevices}`;
    document.querySelector("[data-flow-settled]").textContent = `SETTLED ${data.settledPayments}`;
    document.querySelector("[data-flow-cache]").textContent = `CACHE ${data.idempotencyCacheSize}`;
  }

  function chartPoint(points, index, key, max) {
    return {
      x: 25 + index * 669 / Math.max(1, points.length - 1),
      y: 224 - Number(points[index][key] || 0) * 185 / max,
    };
  }

  function linePath(points, key, max) {
    if (!points.length) return "";
    const coordinates = points.map((_, index) => chartPoint(points, index, key, max));
    if (coordinates.length === 1) return `M${coordinates[0].x.toFixed(1)} ${coordinates[0].y.toFixed(1)}`;
    const slopes = coordinates.slice(1).map((point, index) =>
      (point.y - coordinates[index].y) / (point.x - coordinates[index].x));
    const tangents = coordinates.map((_, index) => {
      if (index === 0) return slopes[0];
      if (index === coordinates.length - 1) return slopes.at(-1);
      const before = slopes[index - 1];
      const after = slopes[index];
      return before * after <= 0 ? 0 : 2 * before * after / (before + after);
    });
    return coordinates.slice(1).reduce((path, point, index) => {
      const previous = coordinates[index];
      const third = (point.x - previous.x) / 3;
      return `${path} C${(previous.x + third).toFixed(1)} ${(previous.y + tangents[index] * third).toFixed(1)} `
        + `${(point.x - third).toFixed(1)} ${(point.y - tangents[index + 1] * third).toFixed(1)} `
        + `${point.x.toFixed(1)} ${point.y.toFixed(1)}`;
    }, `M${coordinates[0].x.toFixed(1)} ${coordinates[0].y.toFixed(1)}`);
  }

  function renderCashflow(data) {
    state.cashflow = data;
    $("cashflow-heading").title = data.accountVpa || "All accounts";
    document.querySelectorAll("[data-cashflow]").forEach(el => el.textContent = money(data.netMovement));
    document.querySelectorAll("[data-cashflow-rate]").forEach(el => {
      el.textContent = `Credits ${compactMoney(data.totalCreditVolume)}`;
      el.classList.toggle("danger", Number(data.netMovement) < 0);
      el.classList.toggle("positive", Number(data.netMovement) >= 0);
    });
    const points = state.range === "recent" ? data.points.slice(-7) : data.points;
    const periodDebits = points.reduce((total, point) => total + Number(point.debits || 0), 0);
    const periodCredits = points.reduce((total, point) => total + Number(point.credits || 0), 0);
    document.querySelectorAll("[data-chart-total]").forEach(el => el.textContent = compactMoney(periodDebits));
    const max = Math.max(1, ...points.flatMap(p => [Number(p.credits), Number(p.debits)]));
    const hasDebits = points.some(point => Number(point.debits) > 0);
    const hasCredits = points.some(point => Number(point.credits) > 0);
    const hasMovement = hasDebits || hasCredits;
    const latestMovementIndex = hasMovement
      ? points.findLastIndex(point => Number(point.credits) > 0 || Number(point.debits) > 0) : -1;
    document.querySelector("[data-flow-red]").setAttribute("d", hasDebits ? linePath(points, "debits", max) : "");
    document.querySelector("[data-flow-white]").setAttribute("d", hasCredits ? linePath(points, "credits", max) : "");
    const chart = document.querySelector(".line-chart");
    chart.querySelector(".chart-empty").hidden = hasMovement;
    const dot = document.querySelector(".chart-dot");
    dot.hidden = !hasMovement;
    const tip = document.querySelector("[data-chart-tip]");
    tip.hidden = !hasMovement;
    if (hasMovement) {
      const point = points[latestMovementIndex];
      const coordinate = chartPoint(points, latestMovementIndex, Number(point.debits) > 0 ? "debits" : "credits", max);
      dot.setAttribute("cx", coordinate.x.toFixed(1));
      dot.setAttribute("cy", coordinate.y.toFixed(1));
      tip.style.setProperty("--tip-x", `${(coordinate.x / 720 * 100).toFixed(2)}%`);
      tip.style.setProperty("--tip-y", `${(coordinate.y / 260 * 100).toFixed(2)}%`);
      tip.classList.toggle("below", coordinate.y < 84);
      tip.querySelector("[data-chart-tip-date]").textContent = chartDate.format(new Date(`${point.bucket}T00:00:00Z`));
      tip.querySelector("[data-chart-tip-values]").textContent = `Debits ${compactMoney(point.debits)} · Credits ${compactMoney(point.credits)}`;
    }
    chart.setAttribute("aria-label", hasMovement
      ? `${state.range === "recent" ? "Recent seven days" : "All fifteen days"} balance spending: debits ${compactMoney(periodDebits)}, credits ${compactMoney(periodCredits)}`
      : `${state.range === "recent" ? "Recent seven days" : "All fifteen days"}: no spending or credits`);
    $("cashflow-map").replaceChildren(...data.points.flatMap(point => ["credits", "debits", "netMovement"].map(key => {
      const cell = node("span");
      cell.style.setProperty("--heat", String(Math.min(28, Math.round(Math.abs(Number(point[key])) * 28 / max))));
      cell.title = `${point.bucket}: ${key} ${money(point[key])}`;
      return cell;
    })));
    document.querySelector(".day-pills").style.gridTemplateColumns = `repeat(${points.length}, 1fr)`;
    document.querySelector(".day-pills").replaceChildren(...points.map((point, index) =>
      node("span", index === latestMovementIndex ? "active" : "", point.bucket.slice(-2))));
  }

  function renderVolume(data) {
    state.volume = data;
    document.querySelectorAll("[data-bar-total]").forEach(el => el.textContent = compactMoney(data.settledAmount));
    const max = Math.max(1, ...data.points.map(p => Number(p.settledAmount)));
    [...document.querySelectorAll(".bars span")].forEach((bar, index) => {
      const point = data.points[index];
      bar.style.setProperty("--h", `${Math.round(Number(point?.settledAmount || 0) * 100 / max)}%`);
      bar.title = `${point?.bucket || ""}: ${money(point?.settledAmount || 0)}`;
      bar.classList.toggle("active", index === data.points.length - 1);
    });
    [...document.querySelectorAll(".bar-labels span")].forEach((label, index) => {
      const point = data.points[index];
      label.textContent = point ? new Date(`${point.bucket}T00:00:00Z`).toLocaleDateString("en-IN", {weekday: "short", timeZone: "UTC"}) : "";
    });
  }

  function focusReference(item) {
    if (item.type === "ROUTE") {
      location.hash = "#mesh";
      const target = [...document.querySelectorAll("[data-route-packet]")].find(el => el.dataset.routePacket === item.referenceId);
      target?.focus();
    } else if (item.referenceId) focusPayment(item.referenceId);
  }

  function renderActivity(data) {
    state.activity = data.items;
    const filtered = data.items.filter(item => !state.activityType || item.type === state.activityType);
    $("log").replaceChildren(...(filtered.length ? filtered.map(item => {
      const row = node("li", "interactive-row");
      row.tabIndex = 0;
      row.dataset.activityReference = item.referenceId;
      const time = node("time", "", dateTime.format(new Date(item.occurredAt)));
      time.dateTime = item.occurredAt;
      row.append(time, node("span", "log-message", `${item.title}: ${item.description}`));
      row.addEventListener("click", () => focusReference(item));
      row.addEventListener("keydown", event => { if (event.key === "Enter") focusReference(item); });
      return row;
    }) : [node("li", "empty-state", data.items.length ? "No matching activity" : "No activity yet")]));
    $("recent-activity").replaceChildren(...(data.items.length ? data.items.slice(0, 4).map(item => {
      const row = node("button", "recent-item interactive-row");
      row.type = "button";
      const symbol = node("span", "recent-icon");
      symbol.append(icon(item.type === "SIGNATURE" ? "shield" : item.type === "ROUTE" ? "radio-tower" : "badge-check"));
      const copy = node("span", "recent-copy");
      copy.append(node("strong", "", item.title), node("small", "", item.description));
      const time = node("time", "recent-time", activityTime.format(new Date(item.occurredAt)));
      time.dateTime = item.occurredAt;
      time.title = dateTime.format(new Date(item.occurredAt));
      row.append(symbol, copy, time);
      row.addEventListener("click", () => focusReference(item));
      return row;
    }) : [node("p", "empty-state", "No recent activity yet")]));
    renderIcons();
  }

  function renderNetwork(data) {
    state.network = data;
    $("network-stats").textContent = `${data.activeConnections} active links · ${data.packetsRouted} routes · ${data.averageHopCount.toFixed(1)} average hops · ${data.trustedDevices} trusted · ${data.revokedDevices} revoked`;
    $("network-stats").title = data.latestRouteTimestamp
      ? `Latest route ${dateTime.format(new Date(data.latestRouteTimestamp))} · Most active bridge ${data.mostActiveBridgeDevice || "None"}`
      : "No packet routes yet";
    $("packet-count").textContent = data.packetsRouted;
  }

  function renderRoutes(data) {
    state.routes = data;
    const visible = data.filter(route => !state.selectedDevice ||
      route.sourceDeviceId === state.selectedDevice || route.destinationDeviceId === state.selectedDevice);
    $("route-history").replaceChildren(node("strong", "", "Recent routes"),
      ...(visible.length ? visible.slice(0, 5).map(route => {
        const row = node("button", "history-item interactive-row", `${route.sourceDeviceId} → ${route.destinationDeviceId} · hop ${route.hopNumber}`);
        row.type = "button";
        row.dataset.routePacket = route.packetId;
        row.title = `Packet ${route.packetId} · TTL ${route.ttlAfterHop} · ${dateTime.format(new Date(route.receivedAt))}`;
        row.addEventListener("click", () => {
          notice(row.title);
          if (route.paymentId) focusPayment(route.paymentId);
        });
        return row;
      }) : [node("p", "empty-state", data.length ? "No routes for this device" : "No routed packets yet")]));
  }

  function renderSecurity(data) {
    state.security = data;
    $("security-events").replaceChildren(node("strong", "", `Security events · ${data.failedSignatureVerification}`),
      ...(data.items.length ? data.items.map(event => {
        const row = node("button", "history-item interactive-row", `${event.eventType} · ${event.deviceId || "Unknown device"}`);
        row.type = "button";
        row.title = `${event.message} · ${dateTime.format(new Date(event.occurredAt))}`;
        row.addEventListener("click", () => event.paymentId ? focusPayment(event.paymentId) : notice(row.title));
        return row;
      }) : [node("p", "empty-state", "No security failures recorded")]));
  }

  function focusPayment(id) {
    location.hash = "#payments-heading";
    const row = [...document.querySelectorAll("[data-payment-id]")].find(el => el.dataset.paymentId === id);
    if (row) { row.focus(); row.scrollIntoView({block: "center", behavior: "smooth"}); }
    else notice(`Payment ${id} is outside the recent list.`);
  }

  function renderTransactions() {
    const query = $("tx-search").value.trim().toLowerCase();
    const status = $("tx-status").value;
    const filtered = transactions.filter(
      (t) =>
        (!status || t.status === status) &&
        [t.id, t.senderVpa, t.receiverVpa].some((value) =>
          String(value).toLowerCase().includes(query),
        ),
    );
    const fragment = document.createDocumentFragment();
    filtered.forEach((tx) => {
      const row = node("tr");
      row.classList.add("interactive-row");
      row.tabIndex = 0;
      if (tx.paymentId) row.dataset.paymentId = tx.paymentId;
      const parties = node("td");
      parties.append(
        node("span", "transfer-party", tx.senderVpa),
        node("span", "transfer-party", tx.receiverVpa),
      );
      const state = node("td");
      state.append(
        node(
          "span",
          `badge ${statusStyles[tx.status] || "neutral"}`,
          statusLabels[tx.status] || "Unknown",
        ),
      );
      const settled = tx.settledAt ? new Date(tx.settledAt) : null;
      row.append(
        node("td", "", `#${tx.id}`),
        parties,
        node("td", "money", money(tx.amount)),
        state,
        node("td", "muted", tx.bridgeNodeId || "--"),
        node("td", "muted", isCount(tx.hopCount) ? tx.hopCount : "--"),
        node(
          "td",
          "muted",
          settled && !Number.isNaN(settled.getTime())
            ? dateTime.format(settled)
            : "--",
        ),
      );
      fragment.append(row);
    });
    if (!filtered.length)
      fragment.append(
        emptyRow(
          7,
          !transactionsLoaded
            ? "Transactions unavailable"
            : transactions.length
              ? "No matching transactions"
              : "No transactions yet",
        ),
      );
    document.querySelector("#tx-table tbody").replaceChildren(fragment);
  }

  function scheduleRefresh() {
    clearTimeout(pollTimer);
    if (!document.hidden && !mutationPending)
      pollTimer = setTimeout(refresh, 3000);
  }

  async function refresh() {
    if (refreshPending) return refreshPending;
    if (mutationPending) return;
    clearTimeout(pollTimer);
    $("refresh").disabled = true;
    $("refresh").classList.add("spinning");
    const sections = [
      {
        name: "payments",
        path: "/api/payments",
        validate: validatePayments,
        render: renderPayments,
      },
      {
        name: "payment-summary",
        path: "/api/dashboard/summary",
        validate: validatePaymentSummary,
        render: renderSummary,
      },
      {
        name: "mesh",
        path: "/api/mesh/state",
        validate: validateMesh,
        render: renderMesh,
      },
      {
        name: "accounts",
        path: "/api/accounts",
        validate: validateAccounts,
        render: renderAccounts,
      },
      {
        name: "transactions",
        path: "/api/transactions",
        validate: validateTransactions,
        render: (data) => {
          transactions = data;
          state.transactions = data;
          transactionsLoaded = true;
          renderTransactions();
        },
      },
      { name: "cashflow", path: `/api/dashboard/cashflow${state.selectedAccount ? `?accountVpa=${encodeURIComponent(state.selectedAccount)}` : ""}`,
        validate: data => { if (!data || !Array.isArray(data.points) || !isNumber(data.netMovement)) throw new Error("Invalid cashflow"); return data; }, render: renderCashflow },
      { name: "activity", path: "/api/dashboard/activity",
        validate: data => { if (!data || !Array.isArray(data.items)) throw new Error("Invalid activity"); return data; }, render: renderActivity },
      { name: "network", path: "/api/dashboard/network-stats",
        validate: data => { if (!data || !isCount(data.totalDevices) || !isCount(data.packetsRouted)) throw new Error("Invalid network stats"); return data; }, render: renderNetwork },
      { name: "volume", path: "/api/dashboard/transaction-volume",
        validate: data => { if (!data || !Array.isArray(data.points) || !isNumber(data.settledAmount)) throw new Error("Invalid volume"); return data; }, render: renderVolume },
      { name: "security", path: "/api/dashboard/security-events",
        validate: data => { if (!data || !Array.isArray(data.items)) throw new Error("Invalid security events"); return data; }, render: renderSecurity },
      { name: "routes", path: "/api/mesh/routes",
        validate: data => { if (!Array.isArray(data)) throw new Error("Invalid routes"); return data; }, render: renderRoutes },
    ];
    sections.forEach(section => $(`${section.name}-error`)?.closest("article, section")?.setAttribute("aria-busy", "true"));
    refreshPending = Promise.allSettled(
      sections.map(async (section) => {
        const data = section.validate(await request(section.path));
        section.render(data);
        loadedSections.add(section.name);
      }),
    );
    try {
      const results = await refreshPending;
      if (state.selectedAccount && state.cashflow?.accountVpa !== state.selectedAccount) {
        try { renderCashflow(await request(`/api/dashboard/cashflow?accountVpa=${encodeURIComponent(state.selectedAccount)}`)); }
        catch { $("cashflow-error").hidden = false; $("cashflow-error").textContent = "Account cashflow unavailable."; }
      }
      results.forEach((result, index) => {
        const error = $(`${sections[index].name}-error`);
        error.closest("article, section")?.setAttribute("aria-busy", "false");
        error.hidden = result.status === "fulfilled";
        error.textContent =
          result.status === "rejected"
            ? "Update unavailable. Previously loaded data may be out of date."
            : "";
        const section = sections[index].name;
        if (result.status === "rejected" && section === "payment-summary") {
          $("reconciliation-summary").textContent = "Reconciliation unavailable";
          $("reconciliation-summary").className = "badge neutral";
        }
        if (result.status === "rejected" && !loadedSections.has(section)) {
          error.textContent = "Unable to load data. Retrying automatically.";
          if (section === "mesh") {
            $("devices").replaceChildren(
              node("p", "empty-state", "Mesh devices unavailable"),
            );
            $("mesh-summary").textContent = "Unavailable";
          } else if (section === "accounts") {
            document
              .querySelector("#accounts-table tbody")
              .replaceChildren(emptyRow(3, "Accounts unavailable"));
            $("account-count").textContent = "Unavailable";
          } else if (section === "payments") {
            document.querySelector("#payments-table tbody")
              .replaceChildren(emptyRow(6, "Payments unavailable"));
          } else if (section === "transactions") {
            renderTransactions();
          }
        }
      });
      const failed = results.filter(
        (result) => result.status === "rejected",
      ).length;
      $("connection").classList.toggle("stale", failed > 0);
      $("connection-text").textContent =
        failed === sections.length
          ? "Disconnected"
          : failed
            ? "Partial update"
            : "Live connection";
      if (!failed)
        $("last-updated").textContent =
          `Updated ${new Date().toLocaleTimeString("en-IN")}`;
      $("devices").setAttribute("aria-busy", "false");
    } finally {
      refreshPending = null;
      $("refresh").disabled = mutationPending;
      $("refresh").classList.remove("spinning");
      scheduleRefresh();
    }
  }

  async function mutate(path, body, onSuccess) {
    if (mutationPending) return;
    mutationPending = true;
    clearTimeout(pollTimer);
    document.querySelectorAll("[data-mutation]").forEach((button) => {
      button.disabled = true;
    });
    $("refresh").disabled = true;
    try {
      // Finish any earlier read before issuing a mutation, then fetch a fresh snapshot.
      if (refreshPending) await refreshPending;
      const data = await request(path, {
        method: "POST",
        ...(body
          ? {
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(body),
            }
          : {}),
      });
      onSuccess(data);
    } catch {
      notice(
        "Action could not be confirmed. Check the refreshed state before trying again.",
        true,
      );
    } finally {
      mutationPending = false;
      document.querySelectorAll("[data-mutation]").forEach((button) => {
        button.disabled = false;
      });
      await refresh();
    }
  }

  $("payment-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (mutationPending) return;
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
        `Bridge upload complete. ${data.uploadsAttempted} upload(s) attempted.`,
      );
    }),
  );
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
  $("refresh").addEventListener("click", refresh);
  $("tx-search").addEventListener("input", renderTransactions);
  $("tx-status").addEventListener("change", renderTransactions);
  $("payment-counts").addEventListener("click", event => {
    const metric = event.target.closest("[data-status]");
    if (!metric) return;
    state.paymentStatus = state.paymentStatus === metric.dataset.status ? "" : metric.dataset.status;
    $("payment-filter-clear").hidden = !state.paymentStatus;
    renderPayments(state.payments);
    document.querySelectorAll("#payment-counts [data-status]").forEach(el =>
      el.classList.toggle("active-filter", el.dataset.status === state.paymentStatus));
    document.querySelector("#payments-table").scrollIntoView({block: "nearest", behavior: "smooth"});
  });
  $("payment-counts").addEventListener("keydown", event => {
    if (event.key === "Enter" || event.key === " ") { event.preventDefault(); event.target.click(); }
  });
  $("payment-filter-clear").addEventListener("click", () => {
    state.paymentStatus = "";
    $("payment-filter-clear").hidden = true;
    renderPayments(state.payments);
    document.querySelectorAll("#payment-counts [data-status]").forEach(el => el.classList.remove("active-filter"));
  });
  $("activity-type").addEventListener("change", event => {
    state.activityType = event.target.value;
    renderActivity({items: state.activity});
  });
  document.querySelectorAll("[data-range]").forEach(button => button.addEventListener("click", () => {
    state.range = button.dataset.range;
    document.querySelectorAll("[data-range]").forEach(el => {
      el.classList.toggle("active", el === button);
      el.setAttribute("aria-pressed", String(el === button));
    });
    if (state.cashflow) renderCashflow(state.cashflow);
  }));
  document.querySelectorAll("[data-focus]").forEach(button => button.addEventListener("click", () => {
    location.hash = `#${button.dataset.focus}`;
  }));
  $("accounts-table").addEventListener("click", async event => {
    const row = event.target.closest("[data-account-vpa]");
    if (!row) return;
    state.selectedAccount = row.dataset.accountVpa;
    renderAccounts(state.accounts);
    $("cashflow-heading").textContent = "Loading...";
    try {
      renderCashflow(await request(`/api/dashboard/cashflow?accountVpa=${encodeURIComponent(state.selectedAccount)}`));
      $("cashflow-error").hidden = true;
      location.hash = "#analytics";
    } catch { $("cashflow-error").hidden = false; $("cashflow-error").textContent = "Account cashflow unavailable."; }
  });
  $("accounts-table").addEventListener("keydown", event => {
    if (event.key === "Enter") event.target.closest("[data-account-vpa]")?.click();
  });
  $("payments-table").addEventListener("click", event => {
    const row = event.target.closest("[data-payment-id]");
    if (row) { row.focus(); notice(`Payment ${row.dataset.paymentId} · ${row.cells[3].textContent.trim()} · ${row.cells[5].textContent.trim()}`); }
  });
  $("payments-table").addEventListener("keydown", event => {
    if (event.key === "Enter") event.target.closest("[data-payment-id]")?.click();
  });
  $("tx-table").addEventListener("click", event => {
    const row = event.target.closest("[data-payment-id]");
    if (row) focusPayment(row.dataset.paymentId);
  });
  $("devices").addEventListener("click", event => {
    const row = event.target.closest("[data-device-id]");
    if (!row) return;
    state.selectedDevice = state.selectedDevice === row.dataset.deviceId ? null : row.dataset.deviceId;
    renderMesh(state.mesh);
    renderRoutes(state.routes);
  });
  $("devices").addEventListener("keydown", event => {
    if (event.key === "Enter") event.target.closest("[data-device-id]")?.click();
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
  $("command-search").addEventListener("keydown", event => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    const query = event.target.value.trim().toLowerCase();
    if (!query) return;
    const account = state.accounts.find(item => [item.vpa, item.holderName].some(value => value.toLowerCase().includes(query)));
    const payment = state.payments.find(item => item.paymentId.toLowerCase().includes(query));
    const device = state.mesh?.devices.find(item => item.deviceId.toLowerCase().includes(query));
    const transaction = state.transactions.find(item => [item.id, item.senderVpa, item.receiverVpa].some(value => String(value).toLowerCase().includes(query)));
    if (payment) focusPayment(payment.paymentId);
    else if (account) { location.hash = "#accounts"; [...document.querySelectorAll("[data-account-vpa]")].find(el => el.dataset.accountVpa === account.vpa)?.focus(); }
    else if (device) { location.hash = "#mesh"; [...document.querySelectorAll("[data-device-id]")].find(el => el.dataset.deviceId === device.deviceId)?.focus(); }
    else if (transaction) { $("tx-search").value = query; renderTransactions(); location.hash = "#transactions"; }
    else notice(`No dashboard result for “${event.target.value.trim()}”.`);
  });
  document.addEventListener("keydown", event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      $("command-search").focus();
    }
  });
  $("applications-button").addEventListener("click", () => { location.hash = "#payment-panel"; $("senderVpa").focus(); });
  $("notifications-button").addEventListener("click", () => {
    location.hash = "#activity";
    $("activity-type").value = "SIGNATURE";
    $("activity-type").dispatchEvent(new Event("change"));
    notice(state.security?.items.length ? `${state.security.items.length} recent security event(s).` : "No security failures recorded.");
  });
  $("messages-button").addEventListener("click", () => {
    location.hash = "#activity";
    $("activity-type").value = "";
    $("activity-type").dispatchEvent(new Event("change"));
  });
  document.addEventListener("visibilitychange", () => {
    clearTimeout(pollTimer);
    if (!document.hidden) refresh();
  });

  function setDrawer(open) {
    $("sidebar").classList.toggle("open", open);
    $("drawer-backdrop").hidden = !open;
    $("menu-toggle").setAttribute("aria-expanded", String(open));
    $("menu-toggle").setAttribute(
      "aria-label",
      open ? "Close navigation" : "Open navigation",
    );
    document.body.classList.toggle("drawer-open", open);
    if (window.innerWidth < 768) $("sidebar").inert = !open;
    if (open) {
      $("sidebar").setAttribute("role", "dialog");
      $("sidebar").setAttribute("aria-modal", "true");
      $("sidebar").querySelector("a").focus();
    } else {
      $("sidebar").removeAttribute("role");
      $("sidebar").removeAttribute("aria-modal");
    }
  }
  $("menu-toggle").addEventListener("click", () =>
    setDrawer(!$("sidebar").classList.contains("open")),
  );
  $("drawer-backdrop").addEventListener("click", () => {
    setDrawer(false);
    $("menu-toggle").focus();
  });
  document.addEventListener("keydown", (event) => {
    if (!$("sidebar").classList.contains("open")) return;
    if (event.key === "Escape") {
      setDrawer(false);
      $("menu-toggle").focus();
    }
    if (event.key === "Tab") {
      const links = [...$("sidebar").querySelectorAll("a")];
      const first = links[0],
        last = links[links.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  });
  document.querySelectorAll(".sidebar a").forEach((link) => {
    link.addEventListener("click", () => {
      if ($("sidebar").classList.contains("open")) {
        setDrawer(false);
        const target = document.querySelector(link.getAttribute("href"));
        target.setAttribute("tabindex", "-1");
        target.focus({ preventScroll: true });
      }
    });
  });
  function updateNavigation() {
    const current = location.hash || "#overview";
    document.querySelectorAll(".nav-link").forEach((link) => {
      const active = link.getAttribute("href") === current;
      link.classList.toggle("active", active);
      if (active) link.setAttribute("aria-current", "location");
      else link.removeAttribute("aria-current");
    });
  }
  const mobile = window.matchMedia("(max-width: 767px)");
  function resizeNavigation() {
    setDrawer(false);
    $("sidebar").inert = mobile.matches;
  }
  mobile.addEventListener("change", resizeNavigation);
  window.addEventListener("hashchange", updateNavigation);
  document.querySelectorAll(".nav-link").forEach((link) => {
    link.title = link.textContent.trim();
    link.setAttribute("aria-label", link.textContent.trim());
  });
  resizeNavigation();
  updateNavigation();
  renderIcons();
  refresh();
})();
